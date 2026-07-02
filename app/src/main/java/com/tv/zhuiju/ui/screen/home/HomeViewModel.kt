package com.tv.zhuiju.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.local.SourceManager
import com.tv.zhuiju.data.local.YouthModeManager
import com.tv.zhuiju.data.model.VideoCategory
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class HomeUiState(
    val bannerItems: List<VideoItem> = emptyList(),
    val todayRecommend: List<VideoItem> = emptyList(),
    val tvSeries: List<VideoItem> = emptyList(),
    val movies: List<VideoItem> = emptyList(),
    val variety: List<VideoItem> = emptyList(),
    val anime: List<VideoItem> = emptyList(),
    val documentary: List<VideoItem> = emptyList(),
    val sports: List<VideoItem> = emptyList(),
    val drama: List<VideoItem> = emptyList(),
    val watchHistory: List<WatchHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentTab: Int = 0,
    val error: String? = null,
    /** 每个 tab 的当前页码，key = tabIndex */
    val currentPageMap: Map<Int, Int> = emptyMap()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository()
    private val sourceManager = SourceManager(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val tabs = listOf("推荐", "电视剧", "电影", "综艺", "动漫", "纪录片", "体育")

    init {
        // 集成自定义采集源
        repository.customBindings = sourceManager.getAllBindings()
        repository.customApiServices = sourceManager.buildApiServices()
        repository.customApiSources = sourceManager.buildApiSources()
        loadHomeData()

        // 监听青少年模式切换，自动刷新数据
        viewModelScope.launch {
            YouthModeManager.isEnabledFlow.collectLatest {
                loadHomeData()
            }
        }
    }

    /**
     * 加载首页数据：先从本地数据库读取（快速展示），再后台同步远程数据
     */
    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // 1. 先从数据库读取，快速展示
            val dbData = repository.getHomeDataFromDb()
            if (dbData.allItems.isNotEmpty()) {
                updateUiStateFromData(dbData)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }

            // 2. 后台同步远程数据（只插入数据库中没有的新数据）
            repository.syncRemoteToDb()
                .onSuccess { remoteData ->
                    // 同步完成后，重新从数据库读取最新的合并数据
                    val updatedData = repository.getHomeDataFromDb()
                    updateUiStateFromData(updatedData)
                    loadWatchHistory()
                    // 重置所有 tab 的分页状态
                    val newPageMap = (1..6).associateWith { 1 }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        currentPageMap = newPageMap
                    )
                }
                .onFailure { e ->
                    if (_uiState.value.bannerItems.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = e.message
                        )
                    }
                }
        }
    }

    private fun updateUiStateFromData(data: com.tv.zhuiju.data.repository.AggregatedData) {
        _uiState.value = _uiState.value.copy(
            bannerItems = data.allItems.shuffled().take(6),
            todayRecommend = data.allItems.shuffled().take(6),
            tvSeries = data.tvSeries,
            movies = data.movies,
            variety = data.variety,
            anime = data.anime,
            documentary = data.documentary,
            sports = data.sports,
            drama = data.drama
        )
    }

    private fun loadWatchHistory() {
        _uiState.value = _uiState.value.copy(
            watchHistory = repository.getVideoHistory()
        )
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            currentPageMap = emptyMap()
        )
        loadHomeData()
    }

    fun onTabSelected(index: Int) {
        _uiState.value = _uiState.value.copy(currentTab = index)
    }

    /**
     * 加载更多：滑动到底部自动触发，不再判断hasMore
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore) return

        val category = when (state.currentTab) {
            1 -> VideoCategory.TV_SERIES
            2 -> VideoCategory.MOVIE
            3 -> VideoCategory.VARIETY
            4 -> VideoCategory.ANIME
            5 -> VideoCategory.DOCUMENTARY
            6 -> VideoCategory.SPORTS
            else -> return
        }

        val currentPage = state.currentPageMap[state.currentTab] ?: 1
        val nextPage = currentPage + 1

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            repository.fetchCategoryPage(page = nextPage, category = category)
                .onSuccess { result ->
                    val latestState = _uiState.value
                    val latestItems = when (category) {
                        VideoCategory.TV_SERIES -> latestState.tvSeries
                        VideoCategory.MOVIE -> latestState.movies
                        VideoCategory.VARIETY -> latestState.variety
                        VideoCategory.ANIME -> latestState.anime
                        VideoCategory.DOCUMENTARY -> latestState.documentary
                        VideoCategory.SPORTS -> latestState.sports
                        else -> emptyList()
                    }
                    val existingIds = latestItems.map { it.id }.toSet()
                    val uniqueNew = result.items.filter { it.id !in existingIds }
                    val merged = latestItems + uniqueNew

                    val newPageMap = latestState.currentPageMap.toMutableMap().apply {
                        put(state.currentTab, nextPage)
                    }

                    _uiState.value = latestState.copy(
                        isLoadingMore = false,
                        currentPageMap = newPageMap,
                        tvSeries = if (category == VideoCategory.TV_SERIES) merged else latestState.tvSeries,
                        movies = if (category == VideoCategory.MOVIE) merged else latestState.movies,
                        variety = if (category == VideoCategory.VARIETY) merged else latestState.variety,
                        anime = if (category == VideoCategory.ANIME) merged else latestState.anime,
                        documentary = if (category == VideoCategory.DOCUMENTARY) merged else latestState.documentary,
                        sports = if (category == VideoCategory.SPORTS) merged else latestState.sports
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
            loadWatchHistory()
        }
    }
}