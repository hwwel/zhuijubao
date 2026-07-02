package com.tv.zhuiju.ui.screen.drama

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.data.remote.ApiSources
import com.tv.zhuiju.data.repository.VideoRepository
import com.tv.zhuiju.ui.screen.player.Episode
import com.tv.zhuiju.ui.screen.player.PlayerSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class ShortDramaUiState(
    /** 当前短剧的详情 */
    val currentDramaDetail: VideoItem? = null,
    /** 所有播放源 */
    val sources: List<PlayerSource> = emptyList(),
    /** 当前选中的播放源索引 */
    val selectedSourceIndex: Int = 0,
    /** 当前播放地址 */
    val currentPlayUrl: String? = null,
    val currentEpisodeIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /** 当前选中源的剧集列表 */
    val episodes: List<Episode>
        get() = sources.getOrNull(selectedSourceIndex)?.episodes ?: emptyList()

    /** 当前源名称 */
    val currentSourceName: String
        get() = sources.getOrNull(selectedSourceIndex)?.name ?: ""

    /** 总集数 */
    val totalEpisodeCount: Int
        get() = episodes.size
}

class ShortDramaViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val videoId: Long = savedStateHandle.get<String>("videoId")?.toLongOrNull() ?: 0L
    private val videoJson: String? = savedStateHandle.get<String>("videoJson")

    private val _uiState = MutableStateFlow(ShortDramaUiState())
    val uiState: StateFlow<ShortDramaUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private val repository = VideoRepository()

    init {
        val drama = videoJson?.let {
            try { gson.fromJson(it, VideoItem::class.java) } catch (_: Exception) { null }
        }
        if (drama != null) {
            _uiState.value = _uiState.value.copy(currentDramaDetail = drama)
            tryLoadEpisodes(drama)
            saveDramaHistory(drama)
        }
    }

    // ========== 剧集 / 源加载 ==========

    private fun tryLoadEpisodes(drama: VideoItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val results = withTimeout(10_000L) {
                    supervisorScope {
                        ApiSources.allSources.map { source ->
                            async {
                                runCatching {
                                    val response = source.api.getVideoDetail(ids = drama.id)
                                    source to response
                                }
                            }
                        }.awaitAll()
                    }
                }

                val dramaName = drama.name.trim()
                val matchedDetails = mutableListOf<VideoItem>()
                results.filter { it.isSuccess }.forEach { result ->
                    val (_, response) = result.getOrNull() ?: return@forEach
                    val list = response.list ?: return@forEach
                    val matched = list.firstOrNull { item ->
                        item.id == drama.id && (
                            dramaName.isBlank() ||
                            item.name.trim().contains(dramaName, ignoreCase = true) ||
                            dramaName.contains(item.name.trim(), ignoreCase = true)
                        )
                    }
                    if (matched != null) matchedDetails.add(matched)
                }

                if (drama.playFrom != null && drama.playUrl != null) {
                    val alreadyHas = matchedDetails.any {
                        it.playFrom == drama.playFrom && it.playUrl == drama.playUrl
                    }
                    if (!alreadyHas) matchedDetails.add(0, drama)
                }

                if (matchedDetails.isNotEmpty()) {
                    val bestDetail = matchedDetails.maxByOrNull {
                        listOfNotNull(it.content, it.actor, it.director, it.year).size
                    } ?: matchedDetails.first()
                    val allSources = parseSourcesFromItems(matchedDetails)
                    if (allSources.isNotEmpty()) {
                        val firstEp = allSources.firstOrNull()?.episodes?.firstOrNull()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            currentDramaDetail = bestDetail,
                            sources = allSources,
                            selectedSourceIndex = 0,
                            currentEpisodeIndex = 0,
                            currentPlayUrl = firstEp?.url
                        )
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
            } catch (e: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "请求超时，请检查网络后重试"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun parseSourcesFromItems(items: List<VideoItem>): List<PlayerSource> {
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

    // ========== 源 / 剧集切换 ==========

    fun selectSource(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.sources.size) return
        val newSource = state.sources[index]
        _uiState.value = _uiState.value.copy(
            selectedSourceIndex = index,
            currentEpisodeIndex = 0,
            currentPlayUrl = newSource.episodes.firstOrNull()?.url
        )
    }

    fun selectEpisode(index: Int) {
        val state = _uiState.value
        val ep = state.episodes.getOrNull(index) ?: return
        _uiState.value = _uiState.value.copy(
            currentEpisodeIndex = index,
            currentPlayUrl = ep.url
        )
    }

    // ========== 短剧观看历史 ==========

    private fun saveDramaHistory(drama: VideoItem) {
        val state = _uiState.value
        repository.addWatchHistory(
            videoItem = drama,
            episodeTitle = state.episodes.getOrNull(state.currentEpisodeIndex)?.title,
            sourceIndex = state.selectedSourceIndex,
            episodeIndex = state.currentEpisodeIndex,
            dramaType = "drama"
        )
    }

    fun getDramaHistory(): List<com.tv.zhuiju.ui.screen.home.WatchHistoryItem> {
        return repository.getDramaHistory()
    }

    fun retry() {
        val drama = _uiState.value.currentDramaDetail ?: return
        tryLoadEpisodes(drama)
    }
}