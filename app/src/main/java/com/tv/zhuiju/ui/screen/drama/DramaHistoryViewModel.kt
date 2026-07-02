package com.tv.zhuiju.ui.screen.drama

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.repository.VideoRepository
import com.tv.zhuiju.ui.screen.home.WatchHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DramaHistoryUiState(
    val history: List<WatchHistoryItem> = emptyList(),
    val isLoading: Boolean = true
)

class DramaHistoryViewModel : ViewModel() {
    private val repository = VideoRepository()

    private val _uiState = MutableStateFlow(DramaHistoryUiState())
    val uiState: StateFlow<DramaHistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val history = repository.getDramaHistory()
            _uiState.value = DramaHistoryUiState(history = history, isLoading = false)
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
            repository.clearDramaHistory()
            _uiState.value = _uiState.value.copy(history = emptyList())
        }
    }
}