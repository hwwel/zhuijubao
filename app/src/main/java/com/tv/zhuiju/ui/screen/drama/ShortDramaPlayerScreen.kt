package com.tv.zhuiju.ui.screen.drama

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.ui.screen.player.Episode
import com.tv.zhuiju.ui.screen.player.LongPressController
import com.tv.zhuiju.ui.screen.player.PlayerSource
import xyz.doikki.videoplayer.player.VideoView
import com.tv.zhuiju.utils.SettingsManager

// ==================== 主 Composable ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortDramaPlayerScreen(
    onBack: () -> Unit = {},
    viewModel: ShortDramaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // 底部弹窗状态
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ========== 播放器 ==========
        if (uiState.currentPlayUrl != null) {
            DramaVideoPlayer(
                url = uiState.currentPlayUrl!!,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ========== 顶部返回按钮 ==========
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }

        // ========== 底部信息区 ==========
        BottomInfoArea(
            uiState = uiState,
            onDetailsClick = { showDetailsSheet = true },
            onSourceClick = { showSourceSheet = true },
            onEpisodeCardClick = { showEpisodesSheet = true },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ========== 加载中 ==========
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }

        // ========== 错误信息 ==========
        if (uiState.error != null && !uiState.isLoading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = uiState.error!!,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                TextButton(onClick = { viewModel.retry() }) {
                    Text("重试", color = Color.White)
                }
            }
        }
    }

    // ========== 底部弹窗：详情 ==========
    if (showDetailsSheet) {
        DetailsBottomSheet(
            detail = uiState.currentDramaDetail,
            onDismiss = { showDetailsSheet = false }
        )
    }

    // ========== 底部弹窗：选集 ==========
    if (showEpisodesSheet) {
        EpisodesBottomSheet(
            sources = uiState.sources,
            selectedSourceIndex = uiState.selectedSourceIndex,
            currentEpisodeIndex = uiState.currentEpisodeIndex,
            onSelectEpisode = { index ->
                viewModel.selectEpisode(index)
                showEpisodesSheet = false
            },
            onDismiss = { showEpisodesSheet = false }
        )
    }

    // ========== 底部弹窗：播放源选择 ==========
    if (showSourceSheet) {
        SourceBottomSheet(
            sources = uiState.sources,
            selectedSourceIndex = uiState.selectedSourceIndex,
            onSelectSource = { index ->
                viewModel.selectSource(index)
                showSourceSheet = false
            },
            onDismiss = { showSourceSheet = false }
        )
    }
}

// ==================== 播放器 ====================

@Composable
private fun DramaVideoPlayer(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPaused by remember { mutableStateOf(false) }
    val videoViewRef = remember { mutableStateOf<VideoView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            videoViewRef.value?.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    videoViewRef.value = this
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // === 应用播放器设置 ===
                    SettingsManager.applyToVideoView(this)

                    val controller = LongPressController(ctx)
                    // 手势控制
                    SettingsManager.applyGestureToController(controller)
                    setVideoController(controller)
                    setUrl(url)
                    start()
                }
            },
            update = { videoView ->
                videoView.setUrl(url)
                videoView.start()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 点击暂停/播放
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    val vv = videoViewRef.value ?: return@clickable
                    if (isPaused) {
                        vv.resume()
                        isPaused = false
                    } else {
                        vv.pause()
                        isPaused = true
                    }
                }
        )

        // 暂停图标
        if (isPaused) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

// ==================== 底部信息区 ====================

@Composable
private fun BottomInfoArea(
    uiState: ShortDramaUiState,
    onDetailsClick: () -> Unit,
    onSourceClick: () -> Unit,
    onEpisodeCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drama = uiState.currentDramaDetail

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 0f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 标题 + 右箭头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDetailsClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = drama?.name ?: "",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "详情",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        // 简介
        val desc = drama?.content
        if (!desc.isNullOrBlank()) {
            Text(
                text = desc,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 播放源选择 + 来源
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onSourceClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "播放源：",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                Text(
                    text = uiState.currentSourceName.ifBlank { "默认" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "来源",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 集数卡片
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onEpisodeCardClick() },
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.totalEpisodeCount > 0) "更新至${uiState.totalEpisodeCount}集" else "暂无剧集",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "选集",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 播放进度条
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
        )
    }
}

// ==================== 底部弹窗：详情 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsBottomSheet(
    detail: VideoItem?,
    onDismiss: () -> Unit
) {
    val item = detail ?: return
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(
                text = item.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            val metaList = listOfNotNull(
                item.year, item.area, item.duration,
                item.actor?.takeIf { it.isNotBlank() }?.let { "主演：$it" },
                item.director?.takeIf { it.isNotBlank() }?.let { "导演：$it" }
            )
            if (metaList.isNotEmpty()) {
                Text(
                    text = metaList.joinToString(" | "),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val content = item.content
            if (!content.isNullOrBlank()) {
                Text(text = content, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
        }
    }
}

// ==================== 底部弹窗：选集 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EpisodesBottomSheet(
    sources: List<PlayerSource>,
    selectedSourceIndex: Int,
    currentEpisodeIndex: Int,
    onSelectEpisode: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val episodes = sources.getOrNull(selectedSourceIndex)?.episodes ?: emptyList()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(
                text = "选集",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (episodes.isEmpty()) {
                Text("暂无剧集数据", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    episodes.forEachIndexed { index, ep ->
                        val isCurrent = index == currentEpisodeIndex
                        Button(
                            onClick = { onSelectEpisode(index) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCurrent) Color(0xFFE53935) else Color.White.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp, vertical = 8.dp
                            )
                        ) {
                            Text(
                                text = ep.title.ifBlank { "${index + 1}" },
                                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 底部弹窗：播放源选择 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceBottomSheet(
    sources: List<PlayerSource>,
    selectedSourceIndex: Int,
    onSelectSource: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(
                text = "播放源",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (sources.isEmpty()) {
                Text("暂无播放源", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            } else {
                sources.forEachIndexed { index, source ->
                    val isSelected = index == selectedSourceIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSource(index) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = source.name.ifBlank { "播放源 ${index + 1}" },
                            color = if (isSelected) Color(0xFFE53935) else Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${source.episodes.size}集",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}