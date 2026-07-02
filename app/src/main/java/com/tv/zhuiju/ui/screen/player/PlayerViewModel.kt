package com.tv.zhuiju.ui.screen.player

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.tv.zhuiju.data.local.FavoritesManager
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.data.remote.ApiSources
import com.tv.zhuiju.data.repository.VideoRepository
import com.tv.zhuiju.ui.screen.home.WatchHistoryItem
import com.tv.zhuiju.utils.DlnaCastHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class Episode(
    val title: String,
    val url: String
)

data class PlayerSource(
    val name: String,
    val episodes: List<Episode>
)

data class PlayerUiState(
    val videoItem: VideoItem? = null,
    val isLoading: Boolean = true,
    val error: String? = null,

    // Tab
    val selectedTab: Int = 0, // 0=剧集, 1=详情

    // Sources and episodes
    val sources: List<PlayerSource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    val selectedEpisodeIndex: Int = 0,
    val currentPlayUrl: String? = null,

    // Episode pagination
    val episodesPerPage: Int = 20,
    val currentPage: Int = 0,

    // 追剧
    val isFavorite: Boolean = false,

    // 投屏
    val castDevices: List<DlnaCastHelper.DlnaDevice> = emptyList(),
    val isDiscoveringCast: Boolean = false,
    val castMessage: String? = null,

    // 播放进度保存（用于恢复）
    val savedPlaybackPosition: Long = 0L,

    // 是否为电影（单集）
    val isMovie: Boolean = false,

    // 是否从数据库恢复了播放状态
    val isRestoredFromDb: Boolean = false
)

class PlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val videoId: Long = savedStateHandle.get<String>("videoId")?.toLongOrNull() ?: 0L
    private val videoJson: String? = savedStateHandle.get<String>("videoJson")

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private val favoritesManager = FavoritesManager.getInstance(application)
    private val videoRepository = VideoRepository()

    init {
        val cachedItem = videoJson?.let {
            try { gson.fromJson(it, VideoItem::class.java) } catch (_: Exception) { null }
        }

        val isFav = cachedItem?.let { favoritesManager.isFavorite(it.id) } ?: false

        if (cachedItem != null) {
            _uiState.value = _uiState.value.copy(videoItem = cachedItem, isFavorite = isFav)
        }

        val id = when {
            videoId > 0 -> videoId
            cachedItem != null -> cachedItem.id
            else -> 0L
        }

        if (id > 0) {
            // 优先尝试从数据库恢复播放状态
            val restored = tryRestoreFromDb(id, cachedItem)
            if (!restored) {
                // 数据库没有记录，走正常加载流程
                loadPlaySources(id, cachedItem)
            }
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "未获取到视频信息")
        }
    }

    /**
     * 从数据库恢复上次播放状态。如果成功，立即开始播放，不再等待 API。
     */
    private fun tryRestoreFromDb(id: Long, cachedItem: VideoItem?): Boolean {
        val history = videoRepository.getWatchHistoryByVideoId(id) ?: return false
        val videoItem = try {
            gson.fromJson(history.videoJson, VideoItem::class.java)
        } catch (_: Exception) { null } ?: cachedItem ?: return false

        // 必须有播放源信息才能恢复
        if (videoItem.playFrom.isNullOrBlank() || videoItem.playUrl.isNullOrBlank()) return false

        val sources = parseEpisodesFromItem(videoItem)
        if (sources.isEmpty()) return false

        // 恢复播放源和剧集
        val sourceIdx = history.sourceIndex.coerceIn(0, sources.size - 1)
        val source = sources[sourceIdx]
        val episodeIdx = history.episodeIndex.coerceIn(0, source.episodes.size - 1)
        val episode = source.episodes[episodeIdx]

        val isMovie = sources.firstOrNull()?.episodes?.let { eps ->
            eps.size == 1 && !eps[0].title.contains("集")
        } ?: false

        _uiState.value = _uiState.value.copy(
            videoItem = videoItem,
            sources = sources,
            selectedSourceIndex = sourceIdx,
            selectedEpisodeIndex = episodeIdx,
            currentPlayUrl = episode.url,
            savedPlaybackPosition = history.lastPosition,
            isMovie = isMovie,
            isLoading = false,
            isRestoredFromDb = true
        )

        // 后台静默刷新 API 数据（不中断当前播放）
        refreshPlaySourcesInBackground(id, videoItem)
        return true
    }

    /**
     * 后台静默刷新播放源。如果获取到新数据，合并到当前播放源列表中。
     */
    private fun refreshPlaySourcesInBackground(id: Long, currentItem: VideoItem) {
        viewModelScope.launch {
            try {
                val results = supervisorScope {
                    ApiSources.allSources.map { source ->
                        async {
                            runCatching {
                                val response = source.api.getVideoDetail(ids = id)
                                source to response
                            }
                        }
                    }.awaitAll()
                }

                val matchedDetails = mutableListOf<VideoItem>()
                val cachedName = currentItem.name.trim()
                results.filter { it.isSuccess }.forEach { result ->
                    val (_, response) = result.getOrNull() ?: return@forEach
                    val list = response.list ?: return@forEach
                    val matched = list.firstOrNull { item ->
                        item.id == id && (
                            cachedName.isBlank() ||
                            item.name.trim().contains(cachedName, ignoreCase = true) ||
                            cachedName.contains(item.name.trim(), ignoreCase = true)
                        )
                    }
                    if (matched != null) matchedDetails.add(matched)
                }

                if (matchedDetails.isNotEmpty()) {
                    val newSources = parseEpisodesFromItems(matchedDetails)
                    if (newSources.isNotEmpty()) {
                        // 合并新源到现有源列表，保留当前播放状态
                        mergeNewSources(newSources)
                    }
                }
            } catch (_: Exception) {
                // 静默失败，不影响当前播放
            }
        }
    }

    private fun mergeNewSources(newSources: List<PlayerSource>) {
        val state = _uiState.value
        val existing = state.sources.toMutableList()

        newSources.forEach { newSource ->
            val idx = existing.indexOfFirst { it.name == newSource.name }
            if (idx >= 0) {
                val merged = (existing[idx].episodes + newSource.episodes)
                    .distinctBy { it.url }
                existing[idx] = existing[idx].copy(episodes = merged)
            } else {
                existing.add(newSource)
            }
        }

        // 确保当前选中的源和集仍然有效
        val safeSourceIdx = state.selectedSourceIndex.coerceIn(0, existing.size - 1)
        val safeEpisodeIdx = state.selectedEpisodeIndex.coerceIn(
            0,
            existing.getOrNull(safeSourceIdx)?.episodes?.size?.minus(1) ?: 0
        )

        _uiState.value = state.copy(
            sources = existing,
            selectedSourceIndex = safeSourceIdx,
            selectedEpisodeIndex = safeEpisodeIdx,
            currentPlayUrl = existing.getOrNull(safeSourceIdx)?.episodes?.getOrNull(safeEpisodeIdx)?.url
                ?: state.currentPlayUrl
        )
    }

    /**
     * 正常加载流程（数据库无记录时走这里）
     */
    private fun loadPlaySources(id: Long, cachedItem: VideoItem?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val results = supervisorScope {
                    ApiSources.allSources.map { source ->
                        async {
                            runCatching {
                                val response = source.api.getVideoDetail(ids = id)
                                source to response
                            }
                        }
                    }.awaitAll()
                }

                val cachedName = cachedItem?.name?.trim()
                val matchedDetails = mutableListOf<VideoItem>()
                results.filter { it.isSuccess }.forEach { result ->
                    val (_, response) = result.getOrNull() ?: return@forEach
                    val list = response.list ?: return@forEach
                    val matched = list.firstOrNull { item ->
                        item.id == id && (
                            cachedName.isNullOrBlank() ||
                            item.name.trim().contains(cachedName, ignoreCase = true) ||
                            cachedName.contains(item.name.trim(), ignoreCase = true)
                        )
                    }
                    if (matched != null) matchedDetails.add(matched)
                }

                if (cachedItem != null && !cachedItem.playFrom.isNullOrBlank() && !cachedItem.playUrl.isNullOrBlank()) {
                    val alreadyHas = matchedDetails.any {
                        it.playFrom == cachedItem.playFrom && it.playUrl == cachedItem.playUrl
                    }
                    if (!alreadyHas) matchedDetails.add(0, cachedItem)
                }

                if (matchedDetails.isNotEmpty()) {
                    val bestDetail = matchedDetails.maxByOrNull {
                        listOfNotNull(it.content, it.actor, it.director, it.year).size
                    } ?: matchedDetails.first()

                    val currentItem = _uiState.value.videoItem
                    if (currentItem == null || currentItem.content.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(videoItem = bestDetail)
                    }

                    val sources = parseEpisodesFromItems(matchedDetails)
                    if (sources.isNotEmpty()) {
                        val firstUrl = sources.firstOrNull()?.episodes?.firstOrNull()?.url
                        val isMovie = sources.firstOrNull()?.episodes?.let { eps ->
                            eps.size == 1 && !eps[0].title.contains("集")
                        } ?: false

                        _uiState.value = _uiState.value.copy(
                            sources = sources,
                            selectedSourceIndex = 0,
                            selectedEpisodeIndex = 0,
                            currentPlayUrl = firstUrl,
                            isMovie = isMovie,
                            isLoading = false
                        )

                        // 保存观看历史
                        saveWatchHistory(sources, 0, 0)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "暂无可用播放源（仅支持m3u8格式）"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "未找到播放源，请检查网络"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载失败：${e.message}"
                )
            }
        }
    }

    private fun parseEpisodesFromItem(item: VideoItem): List<PlayerSource> {
        return parseEpisodesFromItems(listOf(item))
    }

    /** 解析播放源：每个 API 来源独立保留，不合并同名源，方便用户切换不同数据源 */
    private fun parseEpisodesFromItems(items: List<VideoItem>): List<PlayerSource> {
        val allSources = mutableListOf<PlayerSource>()

        items.forEach { item ->
            val playFrom = item.playFrom
            val playUrl = item.playUrl
            if (playFrom.isNullOrBlank() || playUrl.isNullOrBlank()) return@forEach

            val sourceNames = playFrom.split("$$$").filter { it.isNotBlank() }
            val sourceUrls = playUrl.split("$$$").filter { it.isNotBlank() }
            if (sourceNames.isEmpty() || sourceUrls.isEmpty()) return@forEach

            sourceNames.forEachIndexed { index, name ->
                val urlStr = sourceUrls.getOrElse(index) { "" }
                val episodes = urlStr.split("#")
                    .filter { it.isNotBlank() }
                    .map { ep ->
                        val parts = ep.split("$")
                        Episode(
                            title = parts.getOrElse(0) { "" },
                            url = parts.getOrElse(1) { "" }
                        )
                    }
                    .filter { ep -> ep.url.isNotBlank() && isM3u8Url(ep.url) }

                if (episodes.isNotEmpty()) {
                    val sourceName = name.trim()
                    // 同名源：合并剧集但保留每个源的数据（URL去重）
                    val existing = allSources.find { it.name == sourceName }
                    if (existing != null) {
                        val merged = (existing.episodes + episodes).distinctBy { it.url }
                        val idx = allSources.indexOf(existing)
                        allSources[idx] = existing.copy(episodes = merged)
                    } else {
                        allSources.add(PlayerSource(name = sourceName, episodes = episodes))
                    }
                }
            }
        }
        return allSources
    }

    private fun isM3u8Url(url: String): Boolean {
        return url.endsWith(".m3u8", ignoreCase = true) ||
               url.contains(".m3u8", ignoreCase = true)
    }

    private fun saveWatchHistory(sources: List<PlayerSource>, sourceIndex: Int, episodeIndex: Int) {
        val item = _uiState.value.videoItem ?: return
        val episode = sources.getOrNull(sourceIndex)?.episodes?.getOrNull(episodeIndex)
        videoRepository.addWatchHistory(
            videoItem = item,
            episodeTitle = episode?.title,
            sourceIndex = sourceIndex,
            episodeIndex = episodeIndex
        )
    }

    // ==================== UI 交互 ====================

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun selectSource(index: Int) {
        val state = _uiState.value
        if (index >= state.sources.size) return
        val source = state.sources[index]
        val firstUrl = source.episodes.firstOrNull()?.url
        _uiState.value = state.copy(
            selectedSourceIndex = index,
            selectedEpisodeIndex = 0,
            currentPlayUrl = firstUrl,
            currentPage = 0
        )
        saveWatchHistory(state.sources, index, 0)
    }

    fun selectEpisode(index: Int) {
        val state = _uiState.value
        val source = state.sources.getOrNull(state.selectedSourceIndex) ?: return
        val episode = source.episodes.getOrNull(index) ?: return
        _uiState.value = state.copy(
            selectedEpisodeIndex = index,
            currentPlayUrl = episode.url
        )
        saveWatchHistory(state.sources, state.selectedSourceIndex, index)
    }

    fun goToPage(page: Int) {
        _uiState.value = _uiState.value.copy(currentPage = page)
    }

    // ==================== 追剧 ====================

    fun toggleFavorite() {
        val item = _uiState.value.videoItem ?: return
        val isNowFavorite = favoritesManager.toggleFavorite(item)
        _uiState.value = _uiState.value.copy(isFavorite = isNowFavorite)
        val msg = if (isNowFavorite) "已添加到追剧" else "已取消追剧"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    // ==================== 下载 ====================

    fun downloadCurrent() {
        val app = getApplication<Application>()
        val url = _uiState.value.currentPlayUrl
        val title = _uiState.value.videoItem?.name ?: "video"

        if (url.isNullOrBlank()) {
            Toast.makeText(app, "暂无播放地址", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(title)
                setDescription("下载中...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$title.m3u8")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(app, "已开始下载：$title", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(app, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 投屏 ====================

    fun discoverCastDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDiscoveringCast = true, castDevices = emptyList(), castMessage = null)
            val devices = DlnaCastHelper.discoverDevices(timeoutMs = 6000)
            _uiState.value = _uiState.value.copy(
                isDiscoveringCast = false,
                castDevices = devices,
                castMessage = if (devices.isEmpty()) "未找到设备，请确认电视/盒子与手机在同一WiFi下" else null
            )
        }
    }

    fun castTo(device: DlnaCastHelper.DlnaDevice) {
        val app = getApplication<Application>()
        val url = _uiState.value.currentPlayUrl ?: return
        val title = _uiState.value.videoItem?.name ?: ""

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(castMessage = "正在投屏到 ${device.name}...")
            val success = DlnaCastHelper.castToDevice(device, url, title)
            if (success) {
                Toast.makeText(app, "已投屏到 ${device.name}", Toast.LENGTH_SHORT).show()
                _uiState.value = _uiState.value.copy(castMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(castMessage = "投屏失败，请重试")
            }
        }
    }

    fun clearCastMessage() {
        _uiState.value = _uiState.value.copy(castMessage = null)
    }

    fun dismissCastDialog() {
        _uiState.value = _uiState.value.copy(castDevices = emptyList(), castMessage = null)
    }

    // ==================== 播放进度 ====================

    fun savePlaybackPosition(position: Long) {
        if (position > 0) {
            _uiState.value = _uiState.value.copy(savedPlaybackPosition = position)
            val vid = _uiState.value.videoItem?.id ?: videoId
            val state = _uiState.value
            val episode = state.sources.getOrNull(state.selectedSourceIndex)
                ?.episodes?.getOrNull(state.selectedEpisodeIndex)
            if (vid > 0) {
                videoRepository.updatePlaybackState(
                    videoId = vid,
                    position = position,
                    episodeTitle = episode?.title,
                    sourceIndex = state.selectedSourceIndex,
                    episodeIndex = state.selectedEpisodeIndex
                )
            }
        }
    }

    fun clearSavedPlaybackPosition() {
        _uiState.value = _uiState.value.copy(savedPlaybackPosition = 0L)
    }
}
