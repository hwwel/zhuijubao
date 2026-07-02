package com.tv.zhuiju.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val history: List<WatchHistoryItem> = emptyList(),
    val isLoading: Boolean = true
)

class HistoryViewModel : ViewModel() {
    private val repository = VideoRepository()

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val history = repository.getWatchHistory()
            _uiState.value = HistoryUiState(history = history, isLoading = false)
        }
    }

    fun delete(videoId: Long) {
        viewModelScope.launch {
            repository.deleteWatchHistory(videoId)
            _uiState.value = _uiState.value.copy(
                history = _uiState.value.history.filter { it.videoId != videoId }
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearWatchHistory()
            _uiState.value = _uiState.value.copy(history = emptyList())
        }
    }
}
