package com.tv.zhuiju.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 缓存管理页。
 * 展示应用各类数据占用情况，并支持按类型清理（不影响设置项）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheManagerScreen(
    onBack: () -> Unit = {},
    viewModel: CacheManagerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "缓存管理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部总览卡片
            item {
                Spacer(modifier = Modifier.height(4.dp))
                CacheOverviewCard(
                    totalSize = uiState.totalSize,
                    isCalculating = uiState.isCalculating
                )
            }

            // 数据分类清理
            item {
                Text(
                    text = "按类别清理",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                CacheCategoryCard(
                    title = "图片缓存",
                    subtitle = "海报、封面等网络图片",
                    size = uiState.imageCacheSize,
                    onClear = { viewModel.showClearDialog(CacheType.IMAGE_CACHE) }
                )
            }

            item {
                CacheCategoryCard(
                    title = "视频缓存",
                    subtitle = "播放预加载、边下边播等临时文件",
                    size = uiState.videoCacheSize,
                    onClear = { viewModel.showClearDialog(CacheType.VIDEO_CACHE) }
                )
            }

            item {
                CacheCategoryCard(
                    title = "网络缓存",
                    subtitle = "接口响应、JSON 数据等",
                    size = uiState.networkCacheSize,
                    onClear = { viewModel.showClearDialog(CacheType.NETWORK_CACHE) }
                )
            }

            item {
                CacheCategoryCard(
                    title = "WebView 缓存",
                    subtitle = "内置浏览器产生的网页缓存",
                    size = uiState.webViewCacheSize,
                    onClear = { viewModel.showClearDialog(CacheType.WEBVIEW_CACHE) }
                )
            }

            // 用户数据清理
            item {
                Text(
                    text = "用户数据",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                CacheCategoryCard(
                    title = "观看历史",
                    subtitle = "视频和短剧的播放记录",
                    size = uiState.watchHistorySize,
                    onClear = { viewModel.showClearDialog(CacheType.WATCH_HISTORY) }
                )
            }

            item {
                CacheCategoryCard(
                    title = "搜索历史",
                    subtitle = "搜索过的关键词记录",
                    size = uiState.searchHistorySize,
                    onClear = { viewModel.showClearDialog(CacheType.SEARCH_HISTORY) }
                )
            }

            item {
                CacheCategoryCard(
                    title = "本地视频库",
                    subtitle = "首页、分类等同步到本地的视频数据",
                    size = uiState.videoDatabaseSize,
                    onClear = { viewModel.showClearDialog(CacheType.VIDEO_DATABASE) }
                )
            }

            item {
                CacheCategoryCard(
                    title = "分类映射缓存",
                    subtitle = "采集源分类映射的内存缓存",
                    size = "0 B",
                    onClear = { viewModel.showClearDialog(CacheType.CATEGORY_MAPPING) }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // 清理确认弹窗
    uiState.pendingClearType?.let { type ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearDialog() },
            title = { Text("确认清理") },
            text = {
                Column {
                    Text("确定要清理“${type.label}”吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmClear() }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 顶部总览卡片。
 */
@Composable
private fun CacheOverviewCard(
    totalSize: String,
    isCalculating: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "总占用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isCalculating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = totalSize,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * 单个缓存类别卡片。
 */
@Composable
private fun CacheCategoryCard(
    title: String,
    subtitle: String,
    size: String,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = size,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("清理")
                }
            }
        }
    }
}
