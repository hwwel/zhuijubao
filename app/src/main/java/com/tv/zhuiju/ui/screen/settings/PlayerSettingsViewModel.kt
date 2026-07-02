package com.tv.zhuiju.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.tv.zhuiju.utils.AspectRatio
import com.tv.zhuiju.utils.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerSettingsUiState(
    val lowLatencyAudio: Boolean = true,
    val autoPip: Boolean = true,
    val gestureControls: Boolean = true,
    val skipIntro: Boolean = false,
    val autoResume: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val aspectRatio: AspectRatio = AspectRatio.ADAPT,
    val showSpeedDialog: Boolean = false,
    val showAspectRatioDialog: Boolean = false,
    val toastMessage: String? = null
)

class PlayerSettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerSettingsUiState())
    val uiState: StateFlow<PlayerSettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            lowLatencyAudio = SettingsManager.isLowLatencyAudioEnabled(),
            autoPip = SettingsManager.isAutoPipEnabled(),
            gestureControls = SettingsManager.isGestureControlsEnabled(),
            skipIntro = SettingsManager.isSkipIntroEnabled(),
            autoResume = SettingsManager.isAutoResumeEnabled(),
            playbackSpeed = SettingsManager.getDefaultPlaybackSpeed(),
            aspectRatio = SettingsManager.getAspectRatio()
        )
    }

    // ========== 倍速 ==========
    fun showSpeedDialog() { _uiState.value = _uiState.value.copy(showSpeedDialog = true) }
    fun dismissSpeedDialog() { _uiState.value = _uiState.value.copy(showSpeedDialog = false) }
    fun selectSpeed(speed: Float) {
        SettingsManager.setDefaultPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed, showSpeedDialog = false)
        showToast("默认倍速已设为 ${speed}x")
    }

    // ========== 画面比例 ==========
    fun showAspectRatioDialog() { _uiState.value = _uiState.value.copy(showAspectRatioDialog = true) }
    fun dismissAspectRatioDialog() { _uiState.value = _uiState.value.copy(showAspectRatioDialog = false) }
    fun selectAspectRatio(ratio: AspectRatio) {
        SettingsManager.setAspectRatio(ratio)
        _uiState.value = _uiState.value.copy(aspectRatio = ratio, showAspectRatioDialog = false)
        showToast("画面比例已设为 ${ratio.label}")
    }

    // ========== 开关项 ==========
    fun setLowLatencyAudio(enabled: Boolean) {
        SettingsManager.setLowLatencyAudio(enabled)
        _uiState.value = _uiState.value.copy(lowLatencyAudio = enabled)
    }
    fun setAutoPip(enabled: Boolean) {
        SettingsManager.setAutoPip(enabled)
        _uiState.value = _uiState.value.copy(autoPip = enabled)
    }
    fun setGestureControls(enabled: Boolean) {
        SettingsManager.setGestureControls(enabled)
        _uiState.value = _uiState.value.copy(gestureControls = enabled)
    }
    fun setSkipIntro(enabled: Boolean) {
        SettingsManager.setSkipIntro(enabled)
        _uiState.value = _uiState.value.copy(skipIntro = enabled)
    }
    fun setAutoResume(enabled: Boolean) {
        SettingsManager.setAutoResume(enabled)
        _uiState.value = _uiState.value.copy(autoResume = enabled)
    }

    // ========== Toast ==========
    fun clearToast() { _uiState.value = _uiState.value.copy(toastMessage = null) }
    private fun showToast(msg: String) { _uiState.value = _uiState.value.copy(toastMessage = msg) }
}