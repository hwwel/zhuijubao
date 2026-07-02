package com.tv.zhuiju.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.local.YouthModeManager
import com.tv.zhuiju.data.repository.VideoRepository
import com.tv.zhuiju.utils.PlayerKernel
import com.tv.zhuiju.utils.SettingsManager
import com.tv.zhuiju.utils.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val playerKernel: PlayerKernel = PlayerKernel.EXO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cacheSize: String = "计算中...",
    // 开关设置
    val autoResume: Boolean = true,
    val wifiOnlyDownload: Boolean = true,
    val dlnaEnabled: Boolean = true,
    val skipIntro: Boolean = false,
    // 青少年模式
    val youthModeEnabled: Boolean = false,
    // HLS广告过滤
    val hlsFilterEnabled: Boolean = false,
    // 弹窗状态
    val showClearHistoryDialog: Boolean = false,
    val showClearCacheDialog: Boolean = false,
    val showKernelDialog: Boolean = false,
    val showThemeDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val showCastDialog: Boolean = false,
    val showPrivacyDialog: Boolean = false,
    val toastMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            playerKernel = SettingsManager.getPlayerKernel(),
            themeMode = SettingsManager.getThemeMode(),
            autoResume = SettingsManager.isAutoResumeEnabled(),
            wifiOnlyDownload = SettingsManager.isWifiOnlyDownload(),
            dlnaEnabled = SettingsManager.isDlnaEnabled(),
            skipIntro = SettingsManager.isSkipIntroEnabled(),
            youthModeEnabled = YouthModeManager.isEnabled,
            hlsFilterEnabled = SettingsManager.isHlsFilterEnabled()
        )
        calculateCacheSize()
    }

    // ========== 青少年模式 ==========

    fun toggleYouthMode() {
        val newValue = !_uiState.value.youthModeEnabled
        YouthModeManager.isEnabled = newValue
        _uiState.value = _uiState.value.copy(youthModeEnabled = newValue)
        showToast(if (newValue) "青少年模式已开启" else "青少年模式已关闭")
    }

    // ========== 播放内核 ==========

    fun selectPlayerKernel(kernel: PlayerKernel) {
        SettingsManager.setPlayerKernel(kernel)
        _uiState.value = _uiState.value.copy(
            playerKernel = kernel,
            showKernelDialog = false
        )
        showToast("已切换至 ${kernel.label}")
    }

    fun showKernelDialog() {
        _uiState.value = _uiState.value.copy(showKernelDialog = true)
    }

    fun dismissKernelDialog() {
        _uiState.value = _uiState.value.copy(showKernelDialog = false)
    }

    // ========== 主题模式 ==========

    fun selectThemeMode(mode: ThemeMode) {
        SettingsManager.setThemeMode(mode)
        _uiState.value = _uiState.value.copy(
            themeMode = mode,
            showThemeDialog = false
        )
        showToast("已切换至 ${mode.label}")
    }

    fun showThemeDialog() {
        _uiState.value = _uiState.value.copy(showThemeDialog = true)
    }

    fun dismissThemeDialog() {
        _uiState.value = _uiState.value.copy(showThemeDialog = false)
    }

    // ========== 缓存管理 ==========

    private fun calculateCacheSize() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val cacheDir = context.cacheDir
                val size = getDirSize(cacheDir)
                val sizeStr = formatSize(size)
                _uiState.value = _uiState.value.copy(cacheSize = sizeStr)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(cacheSize = "未知")
            }
        }
    }

    fun showClearCacheDialog() {
        _uiState.value = _uiState.value.copy(showClearCacheDialog = true)
    }

    fun dismissClearCacheDialog() {
        _uiState.value = _uiState.value.copy(showClearCacheDialog = false)
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                context.cacheDir.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()
                val videoCacheDir = java.io.File(context.cacheDir, "video_cache")
                videoCacheDir.deleteRecursively()
                val imageCache = java.io.File(context.cacheDir, "image_manager_disk_cache")
                imageCache.deleteRecursively()

                _uiState.value = _uiState.value.copy(
                    showClearCacheDialog = false,
                    cacheSize = "0 B"
                )
                showToast("缓存已清除")
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(showClearCacheDialog = false)
                showToast("清除缓存失败")
            }
        }
    }

    // ========== 清除历史记录 ==========

    fun showClearHistoryDialog() {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = true)
    }

    fun dismissClearHistoryDialog() {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = false)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
            repository.clearDramaHistory()
            _uiState.value = _uiState.value.copy(showClearHistoryDialog = false)
            showToast("历史记录已清除")
        }
    }

    // ========== 开关设置 ==========

    fun toggleAutoResume() {
        val newValue = !_uiState.value.autoResume
        SettingsManager.setAutoResume(newValue)
        _uiState.value = _uiState.value.copy(autoResume = newValue)
        showToast(if (newValue) "自动续播已开启" else "自动续播已关闭")
    }

    fun toggleWifiOnlyDownload() {
        val newValue = !_uiState.value.wifiOnlyDownload
        SettingsManager.setWifiOnlyDownload(newValue)
        _uiState.value = _uiState.value.copy(wifiOnlyDownload = newValue)
        showToast(if (newValue) "仅WiFi下载已开启" else "仅WiFi下载已关闭")
    }

    fun toggleDlna() {
        val newValue = !_uiState.value.dlnaEnabled
        SettingsManager.setDlnaEnabled(newValue)
        _uiState.value = _uiState.value.copy(dlnaEnabled = newValue)
        showToast(if (newValue) "DLNA投屏已开启" else "DLNA投屏已关闭")
    }

    fun toggleSkipIntro() {
        val newValue = !_uiState.value.skipIntro
        SettingsManager.setSkipIntro(newValue)
        _uiState.value = _uiState.value.copy(skipIntro = newValue)
        showToast(if (newValue) "跳过片头片尾已开启" else "跳过片头片尾已关闭")
    }

    // ========== HLS广告过滤 ==========

    fun toggleHlsFilter() {
        val newValue = !_uiState.value.hlsFilterEnabled
        SettingsManager.setHlsFilter(newValue)
        _uiState.value = _uiState.value.copy(hlsFilterEnabled = newValue)
        showToast(if (newValue) "HLS广告过滤已开启" else "HLS广告过滤已关闭")
    }

    // ========== 投屏设置 ==========

    fun showCastDialog() {
        _uiState.value = _uiState.value.copy(showCastDialog = true)
    }

    fun dismissCastDialog() {
        _uiState.value = _uiState.value.copy(showCastDialog = false)
    }

    // ========== 隐私政策 ==========

    fun showPrivacyDialog() {
        _uiState.value = _uiState.value.copy(showPrivacyDialog = true)
    }

    fun dismissPrivacyDialog() {
        _uiState.value = _uiState.value.copy(showPrivacyDialog = false)
    }

    // ========== 关于 ==========

    fun showAboutDialog() {
        _uiState.value = _uiState.value.copy(showAboutDialog = true)
    }

    fun dismissAboutDialog() {
        _uiState.value = _uiState.value.copy(showAboutDialog = false)
    }

    fun getAppVersion(): String {
        return try {
            val context = getApplication<Application>()
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    // ========== Toast ==========

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    private fun showToast(msg: String) {
        _uiState.value = _uiState.value.copy(toastMessage = msg)
    }

    // ========== 工具方法 ==========

    private fun getDirSize(dir: java.io.File): Long {
        if (!dir.exists()) return 0
        return if (dir.isDirectory) {
            dir.listFiles()?.sumOf { getDirSize(it) } ?: 0
        } else {
            dir.length()
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }
}