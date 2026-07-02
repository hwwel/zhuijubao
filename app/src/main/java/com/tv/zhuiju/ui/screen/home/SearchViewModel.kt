package com.tv.zhuiju.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.local.SourceManager
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<VideoItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository()
    private val sourceManager = SourceManager(application)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // 集成自定义采集源
        repository.customBindings = sourceManager.getAllBindings()
        repository.customApiServices = sourceManager.buildApiServices()
        repository.customApiSources = sourceManager.buildApiSources()
    }

    fun loadSearchHistory() {
        viewModelScope.launch {
            val history = repository.getSearchHistory()
            _uiState.value = _uiState.value.copy(searchHistory = history)
        }
    }

    fun search(keyword: String) {
        if (keyword.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, query = keyword, error = null)

            // 保存搜索历史
            repository.addSearchHistory(keyword)
            val updatedHistory = repository.getSearchHistory()

            repository.searchAggregated(keyword)
                .onSuccess { items ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        results = items,
                        searchHistory = updatedHistory
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message,
                        searchHistory = updatedHistory
                    )
                }
        }
    }

    fun clearSearchHistory() {
        repository.clearSearchHistory()
        _uiState.value = _uiState.value.copy(searchHistory = emptyList())
    }

    fun deleteSearchHistory(keyword: String) {
        repository.deleteSearchHistory(keyword)
        val updatedHistory = repository.getSearchHistory()
        _uiState.value = _uiState.value.copy(searchHistory = updatedHistory)
    }
}
