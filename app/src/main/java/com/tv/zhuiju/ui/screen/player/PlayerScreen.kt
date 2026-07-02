package com.tv.zhuiju.ui.screen.player

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import xyz.doikki.videoplayer.player.ProgressManager
import xyz.doikki.videoplayer.player.VideoView
import com.tv.zhuiju.utils.SettingsManager
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    onBack: () -> Unit = {},
    viewModel: PlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isFullScreen by remember { mutableStateOf(false) }
    val playerRef = remember { mutableStateOf<VideoView?>(null) }

    // 获取当前剧集信息
    val currentSource = uiState.sources.getOrNull(uiState.selectedSourceIndex)
    val currentEpisode = currentSource?.episodes?.getOrNull(uiState.selectedEpisodeIndex)
    val episodeTitle = currentEpisode?.title ?: ""
    val sourceName = currentSource?.name ?: ""
    val hasPrevEpisode = uiState.selectedEpisodeIndex > 0 ||
        uiState.selectedSourceIndex > 0
    val hasNextEpisode = uiState.selectedEpisodeIndex < (currentSource?.episodes?.size?.minus(1) ?: 0) ||
        uiState.selectedSourceIndex < uiState.sources.size - 1
    val episodeCount = currentSource?.episodes?.size ?: 0
    val selectedEpisodeIndex = uiState.selectedEpisodeIndex

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading && uiState.videoItem == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (uiState.error != null && uiState.videoItem == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = uiState.error ?: "加载失败", color = Color.White)
            }
        } else {
            // === 视频播放区（始终在组合树中，避免全屏时 VideoView 被销毁） ===
            PlayerArea(
                url = uiState.currentPlayUrl,
                title = uiState.videoItem?.name ?: "",
                episodeTitle = episodeTitle,
                sourceUrl = uiState.currentPlayUrl ?: "",
                hasPrevEpisode = hasPrevEpisode,
                hasNextEpisode = hasNextEpisode,
                episodeCount = episodeCount,
                selectedEpisodeIndex = selectedEpisodeIndex,
                isFullScreen = isFullScreen,
                playerRef = playerRef,
                savedPosition = uiState.savedPlaybackPosition,
                onFullScreenChanged = { fullScreen ->
                    isFullScreen = fullScreen
                    if (fullScreen) {
                        val activity = playerRef.value?.context as? Activity
                        activity?.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    }
                },
                onSavePosition = { viewModel.savePlaybackPosition(it) },
                onClearSavedPosition = { viewModel.clearSavedPlaybackPosition() },
                onBack = onBack,
                onPrevEpisode = {
                    if (uiState.selectedEpisodeIndex > 0) {
                        viewModel.selectEpisode(uiState.selectedEpisodeIndex - 1)
                    } else if (uiState.selectedSourceIndex > 0) {
                        viewModel.selectSource(uiState.selectedSourceIndex - 1)
                    }
                },
                onNextEpisode = {
                    val maxEpIdx = (currentSource?.episodes?.size?.minus(1) ?: 0)
                    if (uiState.selectedEpisodeIndex < maxEpIdx) {
                        viewModel.selectEpisode(uiState.selectedEpisodeIndex + 1)
                    } else if (uiState.selectedSourceIndex < uiState.sources.size - 1) {
                        viewModel.selectSource(uiState.selectedSourceIndex + 1)
                    }
                },
                onEpisodeSelected = { index ->
                    viewModel.selectEpisode(index)
                },
                modifier = if (isFullScreen)
                    Modifier.fillMaxSize()
                else
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
            )

            // 竖屏模式：显示 Tab + 内容
            if (!isFullScreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 错误提示条
                    if (uiState.error != null && uiState.videoItem != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    // === Tab切换栏 ===
                    val tabTitles = listOf("剧集", "详情")
                    TabRow(
                        selectedTabIndex = uiState.selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            if (uiState.selectedTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = uiState.selectedTab == index,
                                onClick = { viewModel.selectTab(index) },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    when (uiState.selectedTab) {
                        0 -> EpisodeTab(uiState, viewModel)
                        1 -> DetailTab(uiState)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerArea(
    url: String?,
    title: String,
    episodeTitle: String,
    sourceUrl: String,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    episodeCount: Int,
    selectedEpisodeIndex: Int,
    isFullScreen: Boolean,
    playerRef: androidx.compose.runtime.MutableState<VideoView?>,
    savedPosition: Long,
    onFullScreenChanged: (Boolean) -> Unit,
    onSavePosition: (Long) -> Unit,
    onClearSavedPosition: () -> Unit,
    onBack: () -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier.background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无播放源", color = Color.Gray)
        }
    } else {
        val lifecycleOwner = LocalLifecycleOwner.current
        val landscapeComponentRef = remember { mutableStateOf<LandscapeControlComponent?>(null) }
        val portraitComponentRef = remember { mutableStateOf<PortraitControlComponent?>(null) }

        // url 变化时直接切集播放（首次进入也会触发）
        LaunchedEffect(url) {
            playerRef.value?.let { player ->
                if (!url.isNullOrBlank()) {
                    player.release()
                    player.setUrl(url)
                    player.start()
                }
            }
        }

        // 跳过片头片尾：播放过程中持续检查位置
        LaunchedEffect(url) {
            if (url.isNullOrBlank()) return@LaunchedEffect
            val player = playerRef.value ?: return@LaunchedEffect
            val introSeconds = SettingsManager.getSkipIntroSeconds()
            val outroSeconds = SettingsManager.getSkipOutroSeconds()
            if (introSeconds <= 0 && outroSeconds <= 0) return@LaunchedEffect

            var introSkipped = savedPosition > 0 // 如果有续播位置，认为片头已跳过
            var outroSkipped = false

            while (true) {
                delay(500)
                val currentPos = player.currentPosition
                val duration = player.duration
                if (duration <= 0 || currentPos <= 0) continue

                // 跳过片头
                if (!introSkipped && introSeconds > 0 && currentPos < introSeconds * 1000L) {
                    player.seekTo(introSeconds * 1000L)
                    introSkipped = true
                }

                // 跳过片尾：提前跳过，避免看到片尾
                if (!outroSkipped && outroSeconds > 0 && duration > outroSeconds * 1000L
                    && currentPos >= duration - outroSeconds * 1000L) {
                    outroSkipped = true
                    onNextEpisode()
                }
            }
        }

        // 更新横屏控制组件的数据（landscapeComponentRef 为 null 时不执行，等它被设置后重新触发）
        LaunchedEffect(landscapeComponentRef.value, episodeTitle, sourceUrl, hasPrevEpisode, hasNextEpisode, episodeCount, selectedEpisodeIndex) {
            landscapeComponentRef.value?.updateData(
                title = title,
                episodeTitle = episodeTitle,
                sourceName = sourceUrl,
                hasPrevEpisode = hasPrevEpisode,
                hasNextEpisode = hasNextEpisode,
                episodeCount = episodeCount,
                selectedEpisodeIndex = selectedEpisodeIndex,
                onBack = { exitFullScreen(playerRef.value) },
                onPrevEpisode = onPrevEpisode,
                onNextEpisode = onNextEpisode,
                onEpisodeList = { },
                onEpisodeSelected = onEpisodeSelected
            )
        }

        // 更新竖屏控制组件的数据
        LaunchedEffect(portraitComponentRef.value, episodeTitle, hasPrevEpisode, hasNextEpisode) {
            portraitComponentRef.value?.updateData(
                title = title,
                episodeTitle = episodeTitle,
                hasPrevEpisode = hasPrevEpisode,
                hasNextEpisode = hasNextEpisode,
                onBack = onBack,
                onPrevEpisode = onPrevEpisode,
                onNextEpisode = onNextEpisode,
                onToggleFullScreen = {
                    val activity = playerRef.value?.context as? Activity
                    activity?.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    playerRef.value?.startFullScreen()
                }
            )
        }

        // 拦截返回键：全屏时先退出全屏
        BackHandler(enabled = playerRef.value?.isFullScreen == true) {
            exitFullScreen(playerRef.value)
        }

        // 生命周期监听：保存/恢复播放进度
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        playerRef.value?.let { player ->
                            val pos = player.currentPosition.toLong()
                            if (pos > 0) onSavePosition(pos)
                        }
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        playerRef.value?.let { player ->
                            if (savedPosition > 0 && player.currentPosition < savedPosition) {
                                player.seekTo(savedPosition)
                                onClearSavedPosition()
                            }
                        }
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                playerRef.value?.let { player ->
                    val pos = player.currentPosition.toLong()
                    if (pos > 0) onSavePosition(pos)
                    player.pause()
                    player.release()
                }
            }
        }

        Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        playerRef.value = this

                        // === 应用播放器设置 ===
                        SettingsManager.applyToVideoView(this)

                        val controller = LongPressController(ctx)
                        SettingsManager.applyGestureToController(controller)
                        controller.addDefaultControlComponent(title, false)
                        // 设置控制器自动隐藏时间为5秒，由 DKVideoPlayer 管理自动显隐
                        // Compose 覆盖层通过 onVisibilityChanged 同步显隐状态
                        controller.setDismissTimeout(5000)
                        controller.getSimpleTitleView()?.setOnBackClickListener {
                            onBack()
                        }

                        // 设置全屏监听
                        controller.setFullScreenListener { fullScreen ->
                            onFullScreenChanged(fullScreen)
                            if (fullScreen) {
                                controller.hideNativeControls()
                            } else {
                                // 竖屏模式也隐藏原生控件，由自定义 PortraitControlComponent 接管
                                controller.hideNativeControls()
                            }
                        }

                        // 创建横屏控制组件，作为控制器的子组件
                        val landscapeComponent = LandscapeControlComponent(ctx, this)
                        landscapeComponentRef.value = landscapeComponent
                        controller.addControlComponent(landscapeComponent)

                        // 创建竖屏自定义控制组件，统一横竖屏风格
                        val portraitComponent = PortraitControlComponent(ctx, this)
                        portraitComponentRef.value = portraitComponent
                        controller.addControlComponent(portraitComponent)
                        // 设置引用，供 onVisibilityChanged 手动通知
                        controller.setPortraitComponent(portraitComponent)

                        setVideoController(controller)

                        // 使用 ProgressManager 自动保存和恢复播放进度
                        var shouldRestore = savedPosition > 0
                        val targetPosition = savedPosition
                        setProgressManager(object : ProgressManager() {
                            override fun saveProgress(url: String, progress: Long) {
                                if (progress > 0) onSavePosition(progress)
                            }
                            override fun getSavedProgress(url: String): Long {
                                return if (shouldRestore) {
                                    shouldRestore = false
                                    onClearSavedPosition()
                                    targetPosition
                                } else 0L
                            }
                        })

                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { }
            )
        }
    }
}

/**
 * 退出全屏：先恢复竖屏方向，再调用 stopFullScreen
 */
private fun exitFullScreen(player: VideoView?) {
    if (player == null) return
    val activity = player.context as? Activity
    activity?.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    player.stopFullScreen()
}

@Composable
private fun EpisodeTab(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel
) {
    val item = uiState.videoItem ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        val metaInfo = buildString {
            if (!item.year.isNullOrBlank()) append(item.year)
            if (!item.area.isNullOrBlank()) append(" · ${item.area}")
            if (!item.typeName.isNullOrBlank()) append(" · ${item.typeName}")
        }
        Text(
            text = metaInfo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (!item.content.isNullOrBlank()) {
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        val context = LocalContext.current
        var showCastDialog by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                icon = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = "追剧",
                tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { viewModel.toggleFavorite() }
            )
            ActionButton(
                icon = Icons.Outlined.Download,
                label = "下载",
                onClick = { viewModel.downloadCurrent() }
            )
            ActionButton(
                icon = Icons.Outlined.Cast,
                label = "投屏",
                onClick = {
                    showCastDialog = true
                    viewModel.discoverCastDevices()
                }
            )
            ActionButton(
                icon = Icons.Outlined.PictureInPictureAlt,
                label = "小窗",
                onClick = {
                    val activity = context as? Activity
                    if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build()
                        activity.enterPictureInPictureMode(params)
                    } else {
                        Toast.makeText(context, "当前设备不支持画中画", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // 投屏设备选择对话框
        if (showCastDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCastDialog = false
                    viewModel.dismissCastDialog()
                },
                title = { Text("选择投屏设备") },
                text = {
                    Column {
                        if (uiState.isDiscoveringCast) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在搜索设备...", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (uiState.castMessage != null) {
                            Text(uiState.castMessage, style = MaterialTheme.typography.bodyMedium)
                        } else if (uiState.castDevices.isEmpty()) {
                            Text("未找到设备，请确认电视/盒子与手机在同一WiFi下", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            uiState.castDevices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.castTo(device)
                                            showCastDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Cast,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCastDialog = false
                        viewModel.dismissCastDialog()
                    }) {
                        Text("关闭")
                    }
                },
                dismissButton = if (!uiState.isDiscoveringCast && uiState.castDevices.isEmpty()) {
                    {
                        TextButton(onClick = {
                            viewModel.discoverCastDevices()
                        }) {
                            Text("重新搜索")
                        }
                    }
                } else null
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.sources.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "播放源：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.sources.getOrNull(uiState.selectedSourceIndex)?.name ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "来源：${uiState.sources.getOrNull(uiState.selectedSourceIndex)?.name ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.sources.forEachIndexed { index, source ->
                    val selected = uiState.selectedSourceIndex == index
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { viewModel.selectSource(index) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // === 选集 / 正片 ===
        val source = uiState.sources.getOrNull(uiState.selectedSourceIndex)
        if (source != null && source.episodes.isNotEmpty()) {
            // 电影：只显示"正片"按钮，不显示分页
            if (uiState.isMovie) {
                val ep = source.episodes.first()
                val isSelected = uiState.selectedEpisodeIndex == 0
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { viewModel.selectEpisode(0) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ep.title.ifBlank { "正片" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 剧集：显示选集 + 分页
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选集",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "共${source.episodes.size}集",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                val totalEpisodes = source.episodes.size
                val perPage = uiState.episodesPerPage
                val totalPages = (totalEpisodes + perPage - 1) / perPage
                val currentPage = uiState.currentPage.coerceIn(0, maxOf(0, totalPages - 1))
                val startIndex = currentPage * perPage
                val endIndex = minOf(startIndex + perPage, totalEpisodes)
                val pageEpisodes = source.episodes.subList(startIndex, endIndex)

                val rows = pageEpisodes.chunked(4)
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { ep ->
                            val globalIndex = source.episodes.indexOf(ep)
                            val isSelected = globalIndex == uiState.selectedEpisodeIndex
                            val displayText = ep.title.ifBlank { (globalIndex + 1).toString() }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { viewModel.selectEpisode(globalIndex) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        repeat(4 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (totalPages > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (currentPage > 0) {
                            PageButton(
                                text = "<",
                                onClick = { viewModel.goToPage(currentPage - 1) }
                            )
                        }
                        for (p in 0 until totalPages) {
                            PageButton(
                                text = "${p + 1}",
                                selected = p == currentPage,
                                onClick = { viewModel.goToPage(p) }
                            )
                        }
                        if (currentPage < totalPages - 1) {
                            PageButton(
                                text = ">",
                                onClick = { viewModel.goToPage(currentPage + 1) }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PageButton(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailTab(uiState: PlayerUiState) {
    val item = uiState.videoItem ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!item.score.isNullOrBlank()) {
                    Text(
                        text = item.score,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (!item.year.isNullOrBlank()) {
                    Text(
                        text = "年份：${item.year}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!item.area.isNullOrBlank()) {
                    Text(
                        text = "地区：${item.area}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!item.typeName.isNullOrBlank()) {
                    Text(
                        text = "类型：${item.typeName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!item.director.isNullOrBlank()) {
                    Text(
                        text = "导演：${item.director}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!item.actor.isNullOrBlank()) {
                    Text(
                        text = "演员：${item.actor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!item.duration.isNullOrBlank()) {
                    Text(
                        text = "时长：${item.duration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!item.remarks.isNullOrBlank()) {
                    Text(
                        text = "备注：${item.remarks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (!item.pic.isNullOrBlank()) {
                AsyncImage(
                    model = item.pic,
                    contentDescription = item.name,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!item.content.isNullOrBlank()) {
            Text(
                text = "简介",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}