package com.tv.zhuiju.ui.screen.drama

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.model.VideoCategory
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DramaListUiState(
    val dramas: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val error: String? = null
)

class DramaListViewModel : ViewModel() {

    private val repository = VideoRepository()
    private val _uiState = MutableStateFlow(DramaListUiState())
    val uiState: StateFlow<DramaListUiState> = _uiState.asStateFlow()

    init {
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.fetchCategoryPage(page = 1, category = VideoCategory.DRAMA)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        dramas = result.items.distinctBy { it.id },
                        currentPage = 1,
                        hasMore = (result.pageCount ?: 1) > 1
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val nextPage = state.currentPage + 1
            repository.fetchCategoryPage(page = nextPage, category = VideoCategory.DRAMA)
                .onSuccess { result ->
                    val existingIds = state.dramas.map { it.id }.toSet()
                    val uniqueNew = result.items.filter { it.id !in existingIds }
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        dramas = state.dramas + uniqueNew,
                        currentPage = nextPage,
                        hasMore = (result.pageCount ?: nextPage) > nextPage
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            repository.fetchCategoryPage(page = 1, category = VideoCategory.DRAMA)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        dramas = result.items.distinctBy { it.id },
                        currentPage = 1,
                        hasMore = (result.pageCount ?: 1) > 1
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                }
        }
    }
}