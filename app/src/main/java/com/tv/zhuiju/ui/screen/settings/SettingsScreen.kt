package com.tv.zhuiju.ui.screen.settings

import android.widget.Toast
import com.tv.zhuiju.utils.PlayerKernel
import com.tv.zhuiju.utils.ThemeMode
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

data class SettingItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit = {},
    // 开关类型
    val isSwitch: Boolean = false,
    val switchChecked: Boolean = false,
    val onSwitchChange: (Boolean) -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPlayerSettings: () -> Unit = {},
    onNavigateToSourceManagement: () -> Unit = {},
    onNavigateToCacheManager: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Toast
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    val settingsGroups = listOf(
        "播放设置" to listOf(
            SettingItem(
                icon = Icons.Filled.PhoneAndroid,
                title = "播放器设置",
                subtitle = "解码器、渲染器、画质等",
                onClick = onNavigateToPlayerSettings
            ),
            SettingItem(
                icon = Icons.Filled.PhoneAndroid,
                title = "播放内核",
                subtitle = uiState.playerKernel.label,
                onClick = { viewModel.showKernelDialog() }
            ),
            SettingItem(
                icon = Icons.Filled.Security,
                title = "HLS广告过滤",
                subtitle = "过滤m3u8视频中的广告片段（默认关闭）",
                isSwitch = true,
                switchChecked = uiState.hlsFilterEnabled,
                onSwitchChange = { viewModel.toggleHlsFilter() }
            ),
            SettingItem(
                icon = Icons.Filled.PhoneAndroid,
                title = "DLNA 投屏",
                subtitle = "投屏到电视或盒子",
                isSwitch = true,
                switchChecked = uiState.dlnaEnabled,
                onSwitchChange = { viewModel.toggleDlna() }
            ),
            SettingItem(
                icon = Icons.Filled.Download,
                title = "下载管理",
                subtitle = "查看下载内容",
                onClick = {
                    try {
                        val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开下载管理", Toast.LENGTH_SHORT).show()
                    }
                }
            ),
            SettingItem(
                icon = Icons.Filled.Download,
                title = "仅WiFi下载",
                subtitle = "仅WiFi环境下允许下载",
                isSwitch = true,
                switchChecked = uiState.wifiOnlyDownload,
                onSwitchChange = { viewModel.toggleWifiOnlyDownload() }
            ),
        ),
        "通用设置" to listOf(
            SettingItem(
                icon = Icons.Filled.Palette,
                title = "主题模式",
                subtitle = uiState.themeMode.label,
                onClick = { viewModel.showThemeDialog() }
            ),
            SettingItem(
                icon = Icons.Filled.Storage,
                title = "采集管理",
                subtitle = "添加和管理 API 采集源",
                onClick = onNavigateToSourceManagement
            ),
            SettingItem(
                icon = Icons.Filled.Security,
                title = "青少年模式",
                subtitle = "开启后屏蔽伦理等不适合未成年人的内容",
                isSwitch = true,
                switchChecked = uiState.youthModeEnabled,
                onSwitchChange = { viewModel.toggleYouthMode() }
            ),
            SettingItem(
                icon = Icons.Filled.Storage,
                title = "缓存管理",
                subtitle = "管理缓存与数据占用",
                onClick = onNavigateToCacheManager
            ),
            SettingItem(
                icon = Icons.Filled.Security,
                title = "隐私政策",
                subtitle = null,
                onClick = { viewModel.showPrivacyDialog() }
            ),
        ),
        "其他" to listOf(
            SettingItem(
                icon = Icons.Filled.Delete,
                title = "清除历史记录",
                subtitle = null,
                onClick = { viewModel.showClearHistoryDialog() }
            ),
            SettingItem(
                icon = Icons.Filled.Info,
                title = "关于追剧宝",
                subtitle = "v${viewModel.getAppVersion()}",
                onClick = { viewModel.showAboutDialog() }
            ),
        )
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior
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
            settingsGroups.forEach { (groupTitle, items) ->
                item {
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column {
                            items.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled = !item.isSwitch,
                                            onClick = item.onClick
                                        )
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (item.subtitle != null) {
                                            Text(
                                                text = item.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (item.isSwitch) {
                                        Switch(
                                            checked = item.switchChecked,
                                            onCheckedChange = { item.onSwitchChange(it) }
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                if (index < items.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ========== 播放内核选择弹窗 ==========
    if (uiState.showKernelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissKernelDialog() },
            title = { Text("选择播放内核") },
            text = {
                Column {
                    PlayerKernel.entries.forEach { kernel ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPlayerKernel(kernel) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = kernel.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            if (kernel == uiState.playerKernel) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (kernel != PlayerKernel.entries.last()) {
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissKernelDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // ========== 主题模式选择弹窗 ==========
    if (uiState.showThemeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissThemeDialog() },
            title = { Text("选择主题模式") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectThemeMode(mode) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            if (mode == uiState.themeMode) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (mode != ThemeMode.entries.last()) {
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissThemeDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // ========== 清除缓存确认弹窗 ==========
    if (uiState.showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearCacheDialog() },
            title = { Text("清除缓存") },
            text = { Text("确定要清除应用缓存吗？当前缓存大小：${uiState.cacheSize}") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCache() }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearCacheDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // ========== 清除历史记录确认弹窗 ==========
    if (uiState.showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearHistoryDialog() },
            title = { Text("清除历史记录") },
            text = { Text("确定要清除所有观看历史记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory() }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearHistoryDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // ========== 关于弹窗 ==========
    if (uiState.showAboutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAboutDialog() },
            title = { Text("关于追剧宝") },
            text = {
                Column {
                    Text(
                        text = "追剧宝 v${viewModel.getAppVersion()}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "影视大全应用，聚合多源播放",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "支持多播放源切换、续播、投屏、下载等功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAboutDialog() }) {
                    Text("确定")
                }
            }
        )
    }

    // ========== 投屏设置弹窗 ==========
    if (uiState.showCastDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCastDialog() },
            title = { Text("投屏设置") },
            text = {
                Column {
                    Text(
                        text = "已启用 DLNA 投屏",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "在播放页点击投屏按钮，即可搜索局域网内的 DLNA 设备并将视频投屏到电视或盒子。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "支持设备：DLNA 电视、电视盒子、支持 DLNA 协议的播放器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCastDialog() }) {
                    Text("确定")
                }
            }
        )
    }

    // ========== 隐私政策弹窗 ==========
    if (uiState.showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPrivacyDialog() },
            title = { Text("隐私政策") },
            text = {
                Column {
                    Text(
                        text = "1. 信息收集",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "本应用仅收集必要的使用数据，包括观看历史记录和搜索记录，用于提供续播和历史记录功能。以上数据均存储在您的设备本地，不会上传至任何服务器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "2. 数据存储",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "所有数据均存储在设备本地 SQLite 数据库和 SharedPreferences 中，不会与第三方共享。您可以在设置中随时清除所有本地数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "3. 网络请求",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "本应用会向第三方 API 服务器请求视频列表和播放数据。我们不会将您的个人信息发送给这些服务器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPrivacyDialog() }) {
                    Text("确定")
                }
            }
        )
    }
}