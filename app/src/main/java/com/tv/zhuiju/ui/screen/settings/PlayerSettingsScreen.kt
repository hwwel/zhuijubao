package com.tv.zhuiju.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.tv.zhuiju.utils.AspectRatio
import com.tv.zhuiju.utils.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: PlayerSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("播放器设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
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
            // ==================== 画质与音频 ====================
            item {
                SectionTitle("画质与音频")
            }
            item {
                SettingsCard(
                    items = listOf(
                        SwitchItem(
                            icon = Icons.Filled.MusicNote,
                            title = "低延迟音频",
                            subtitle = "减少音频延迟，音画更同步",
                            checked = uiState.lowLatencyAudio,
                            onCheckedChange = { viewModel.setLowLatencyAudio(it) }
                        )
                    )
                )
            }

            // ==================== 播放控制 ====================
            item {
                SectionTitle("播放控制")
            }
            item {
                SettingsCard(
                    items = listOf(
                        SelectItem(
                            icon = Icons.Filled.Speed,
                            title = "默认播放倍速",
                            subtitle = "${uiState.playbackSpeed}x",
                            onClick = { viewModel.showSpeedDialog() }
                        ),
                        SelectItem(
                            icon = Icons.Filled.AspectRatio,
                            title = "默认画面比例",
                            subtitle = uiState.aspectRatio.label,
                            onClick = { viewModel.showAspectRatioDialog() }
                        )
                    )
                )
            }

            // ==================== 功能开关 ====================
            item {
                SectionTitle("功能开关")
            }
            item {
                SettingsCard(
                    items = listOf(
                        SwitchItem(
                            icon = Icons.Filled.PlayArrow,
                            title = "自动续播",
                            subtitle = "自动从上次观看位置继续播放",
                            checked = uiState.autoResume,
                            onCheckedChange = { viewModel.setAutoResume(it) }
                        ),
                        SwitchItem(
                            icon = Icons.Filled.PictureInPicture,
                            title = "自动画中画",
                            subtitle = "返回桌面时自动进入小窗播放",
                            checked = uiState.autoPip,
                            onCheckedChange = { viewModel.setAutoPip(it) }
                        ),
                        SwitchItem(
                            icon = Icons.Filled.TouchApp,
                            title = "手势控制",
                            subtitle = "滑动调节亮度/音量/进度",
                            checked = uiState.gestureControls,
                            onCheckedChange = { viewModel.setGestureControls(it) }
                        ),
                        SwitchItem(
                            icon = Icons.Filled.SlowMotionVideo,
                            title = "跳过片头片尾",
                            subtitle = "自动跳过剧集片头片尾",
                            checked = uiState.skipIntro,
                            onCheckedChange = { viewModel.setSkipIntro(it) }
                        )
                    )
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ========== 倍速弹窗 ==========
    if (uiState.showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
        SingleChoiceDialog(
            title = "默认播放倍速",
            items = speeds.map { "${it}x" to "" },
            selectedIndex = speeds.indexOf(uiState.playbackSpeed).coerceAtLeast(0),
            onSelect = { viewModel.selectSpeed(speeds[it]) },
            onDismiss = { viewModel.dismissSpeedDialog() }
        )
    }

    // ========== 画面比例弹窗 ==========
    if (uiState.showAspectRatioDialog) {
        SingleChoiceDialog(
            title = "默认画面比例",
            items = AspectRatio.entries.map { it.label to it.desc },
            selectedIndex = AspectRatio.entries.indexOf(uiState.aspectRatio),
            onSelect = { viewModel.selectAspectRatio(AspectRatio.entries[it]) },
            onDismiss = { viewModel.dismissAspectRatioDialog() }
        )
    }
}

// ==================== 可复用组件 ====================

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

data class SelectItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

data class SwitchItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

@Composable
private fun SettingsCard(
    items: List<Any> // SelectItem or SwitchItem
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column {
            items.forEachIndexed { index, item ->
                when (item) {
                    is SelectItem -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = item.onClick)
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
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    is SwitchItem -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = item.checked,
                                onCheckedChange = item.onCheckedChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
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

@Composable
private fun SingleChoiceDialog(
    title: String,
    items: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                items.forEachIndexed { index, (label, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (desc.isNotBlank()) {
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index == selectedIndex) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (index < items.size - 1) {
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}