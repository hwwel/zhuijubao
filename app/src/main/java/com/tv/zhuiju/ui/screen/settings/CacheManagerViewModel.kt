package com.tv.zhuiju.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.local.db.DatabaseProvider
import com.tv.zhuiju.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 缓存管理页数据类型。
 */
enum class CacheType(
    val label: String,
    val description: String
) {
    IMAGE_CACHE("图片缓存", "清理 Coil 加载的海报和封面图片，下次打开会重新下载。"),
    VIDEO_CACHE("视频缓存", "清理播放预加载和边下边播的临时文件。"),
    NETWORK_CACHE("网络缓存", "清理接口响应和 JSON 数据缓存，下次请求会重新拉取。"),
    WEBVIEW_CACHE("WebView 缓存", "清理内置网页浏览产生的缓存文件。"),
    WATCH_HISTORY("观看历史", "清除所有视频和短剧的播放记录，此操作不可恢复。"),
    SEARCH_HISTORY("搜索历史", "清除所有搜索过的关键词，此操作不可恢复。"),
    VIDEO_DATABASE("本地视频库", "清除首页、分类等同步到本地的视频数据，下次进入会重新同步。"),
    CATEGORY_MAPPING("分类映射缓存", "清除采集源分类映射的内存缓存，下次请求会重新生成。"),
}

/**
 * 缓存管理页 UI 状态。
 */
data class CacheManagerUiState(
    val isCalculating: Boolean = true,
    val totalSize: String = "计算中...",
    val imageCacheSize: String = "计算中...",
    val videoCacheSize: String = "计算中...",
    val networkCacheSize: String = "计算中...",
    val webViewCacheSize: String = "计算中...",
    val watchHistorySize: String = "计算中...",
    val searchHistorySize: String = "计算中...",
    val videoDatabaseSize: String = "计算中...",
    val pendingClearType: CacheType? = null,
    val toastMessage: String? = null
)

/**
 * 缓存管理页 ViewModel。
 */
class CacheManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository()

    private val _uiState = MutableStateFlow(CacheManagerUiState())
    val uiState: StateFlow<CacheManagerUiState> = _uiState.asStateFlow()

    init {
        calculateAllSizes()
    }

    /**
     * 重新计算所有缓存大小。
     */
    fun calculateAllSizes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCalculating = true)
            val context = getApplication<Application>()

            val imageCache = getDirSize(File(context.cacheDir, "image_manager_disk_cache"))
            val videoCache = getDirSize(File(context.cacheDir, "video_cache"))
            val networkCache = getDirSize(File(context.cacheDir, "okhttp"))
                + getDirSize(File(context.cacheDir, "volley"))
            val webViewCache = getDirSize(File(context.cacheDir, "webview"))
                + getDirSize(File(context.cacheDir, "WebView"))

            val dbFile = context.getDatabasePath("zhuijubao.db")
            val dbSize = if (dbFile.exists()) dbFile.length() else 0L
            val dbJournal = File(dbFile.parent, "zhuijubao.db-journal")
            val dbJournalSize = if (dbJournal.exists()) dbJournal.length() else 0L
            val totalDbSize = dbSize + dbJournalSize

            val watchHistorySize = estimateTableSize("watch_history")
            val searchHistorySize = estimateTableSize("search_history")
            val videoTableSize = estimateTableSize("videos")
            val categoryMappingSize = 0L // 内存缓存，不计入磁盘

            val total = imageCache + videoCache + networkCache + webViewCache + totalDbSize

            _uiState.value = _uiState.value.copy(
                isCalculating = false,
                totalSize = formatSize(total),
                imageCacheSize = formatSize(imageCache),
                videoCacheSize = formatSize(videoCache),
                networkCacheSize = formatSize(networkCache),
                webViewCacheSize = formatSize(webViewCache),
                watchHistorySize = formatSize(watchHistorySize),
                searchHistorySize = formatSize(searchHistorySize),
                videoDatabaseSize = formatSize(videoTableSize),
                pendingClearType = null
            )
        }
    }

    /**
     * 显示清理确认弹窗。
     */
    fun showClearDialog(type: CacheType) {
        _uiState.value = _uiState.value.copy(pendingClearType = type)
    }

    /**
     * 取消清理确认弹窗。
     */
    fun dismissClearDialog() {
        _uiState.value = _uiState.value.copy(pendingClearType = null)
    }

    /**
     * 确认清理选中的类型。
     */
    fun confirmClear() {
        val type = _uiState.value.pendingClearType ?: return
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                when (type) {
                    CacheType.IMAGE_CACHE -> {
                        File(context.cacheDir, "image_manager_disk_cache").deleteRecursively()
                    }
                    CacheType.VIDEO_CACHE -> {
                        File(context.cacheDir, "video_cache").deleteRecursively()
                    }
                    CacheType.NETWORK_CACHE -> {
                        File(context.cacheDir, "okhttp").deleteRecursively()
                        File(context.cacheDir, "volley").deleteRecursively()
                    }
                    CacheType.WEBVIEW_CACHE -> {
                        File(context.cacheDir, "webview").deleteRecursively()
                        File(context.cacheDir, "WebView").deleteRecursively()
                    }
                    CacheType.WATCH_HISTORY -> {
                        repository.clearWatchHistory()
                        repository.clearDramaHistory()
                    }
                    CacheType.SEARCH_HISTORY -> {
                        repository.clearSearchHistory()
                    }
                    CacheType.VIDEO_DATABASE -> {
                        DatabaseProvider.videoDao().deleteAll()
                    }
                    CacheType.CATEGORY_MAPPING -> {
                        VideoRepository.clearCategoryMappingCache()
                    }
                }
                showToast("${type.label}已清理")
            } catch (e: Exception) {
                showToast("清理失败: ${e.message}")
            } finally {
                // 清理完成后重新计算大小
                calculateAllSizes()
            }
        }
    }

    /**
     * 清除 Toast 消息。
     */
    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    private fun showToast(msg: String) {
        _uiState.value = _uiState.value.copy(toastMessage = msg)
    }

    /**
     * 估算数据库表大小：通过统计行数粗略估算（每行约 2KB）。
     * SQLite 不直接暴露单表大小，此方式足够用户感知占用。
     */
    private fun estimateTableSize(tableName: String): Long {
        return try {
            val db = DatabaseProvider.readableDb()
            db.rawQuery("SELECT COUNT(*) FROM $tableName", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0) * 2048L
                } else {
                    0L
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 递归计算目录大小。
     */
    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return if (dir.isDirectory) {
            dir.listFiles()?.sumOf { getDirSize(it) } ?: 0
        } else {
            dir.length()
        }
    }

    /**
     * 格式化字节大小。
     */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }
}
