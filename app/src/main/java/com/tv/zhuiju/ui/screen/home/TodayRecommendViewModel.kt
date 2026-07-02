package com.tv.zhuiju.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.local.SourceManager
import com.tv.zhuiju.data.local.YouthModeManager
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.data.model.VideoCategory
import com.tv.zhuiju.data.model.ApiResponse
import com.tv.zhuiju.data.repository.VideoRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class TodayRecommendUiState(
    val items: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val error: String? = null
)

class TodayRecommendViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository()
    private val sourceManager = SourceManager(application)

    private val _uiState = MutableStateFlow(TodayRecommendUiState())
    val uiState: StateFlow<TodayRecommendUiState> = _uiState.asStateFlow()

    init {
        repository.customBindings = sourceManager.getAllBindings()
        repository.customApiServices = sourceManager.buildApiServices()
        repository.customApiSources = sourceManager.buildApiSources()
        loadData()

        // 监听青少年模式切换，自动刷新数据
        viewModelScope.launch {
            YouthModeManager.isEnabledFlow.collectLatest {
                loadData()
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            supervisorScope {
                val results = repository.queryAllApiServices().map { api ->
                    async {
                        runCatching { api.getVideoList(ac = "videolist", pg = 1, h = 24) }
                    }
                }.awaitAll()

                val allItems = results
                    .filter { it.isSuccess }
                    .flatMap {
                        it.getOrDefault(ApiResponse(0, null, null, null, null, null, null, null)).list ?: emptyList()
                    }
                    .distinctBy { it.id }

                val filteredItems = VideoCategory.filterYouthMode(allItems)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = filteredItems,
                    currentPage = 1
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore) return

        val nextPage = state.currentPage + 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            supervisorScope {
                val results = repository.queryAllApiServices().map { api ->
                    async {
                        runCatching { api.getVideoList(ac = "videolist", pg = nextPage, h = 24) }
                    }
                }.awaitAll()

                val newItems = results
                    .filter { it.isSuccess }
                    .flatMap {
                        it.getOrDefault(ApiResponse(0, null, null, null, null, null, null, null)).list ?: emptyList()
                    }
                    .distinctBy { it.id }

                // 青少年模式过滤
                val filteredNewItems = VideoCategory.filterYouthMode(newItems)

                val state = _uiState.value
                val existingIds = state.items.map { it.id }.toSet()
                val unique = filteredNewItems.filter { it.id !in existingIds }

                _uiState.value = state.copy(
                    isLoadingMore = false,
                    items = state.items + unique,
                    currentPage = nextPage
                )
            }
        }
    }
}