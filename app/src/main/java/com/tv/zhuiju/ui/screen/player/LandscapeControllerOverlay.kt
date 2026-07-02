package com.tv.zhuiju.ui.screen.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.doikki.videoplayer.player.BaseVideoView
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.util.PlayerUtils
import com.tv.zhuiju.utils.SettingsManager
import com.tv.zhuiju.utils.AspectRatio as AppAspectRatio
import java.io.File
import java.io.FileOutputStream

/**
 * 横屏全屏状态下的自定义控制器覆盖层
 * 所有 UI 用 Compose 绘制，风格与竖屏默认控制器统一
 */
@Composable
fun LandscapeControllerOverlay(
    videoView: VideoView?,
    title: String,
    episodeTitle: String,
    sourceName: String,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    episodeCount: Int = 0,
    selectedEpisodeIndex: Int = 0,
    onBack: () -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onEpisodeList: () -> Unit,
    onEpisodeSelected: (Int) -> Unit = {},
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    // ==================== 核心状态 ====================
    var isVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPercent by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }

    // 更多菜单
    var showMoreMenu by remember { mutableStateOf(false) }
    // 跳过片头片尾弹窗
    var showSkipIntroDialog by remember { mutableStateOf(false) }
    // 定时关闭
    var showTimerMenu by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableLongStateOf(0L) }
    var timerRemaining by remember { mutableLongStateOf(0L) }
    // 倍速菜单
    var showSpeedMenu by remember { mutableStateOf(false) }
    // 比例菜单
    var showAspectMenu by remember { mutableStateOf(false) }
    // 剧集列表
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var episodePage by remember { mutableIntStateOf(0) }
    val episodesPerPage = 30 // 6列 x 5行

    // 听音频模式
    var isAudioOnly by remember { mutableStateOf(false) }

    // 手势指示器
    var brightnessPercent by remember { mutableIntStateOf(-1) }
    var currentBrightness by remember { mutableIntStateOf(getScreenBrightness(context)) }
    var volumePercent by remember { mutableIntStateOf(-1) }
    var showSpeedIndicator by remember { mutableStateOf(false) }
    var showSeekIndicator by remember { mutableStateOf(false) }
    var seekText by remember { mutableStateOf("") }

    // 长按加速
    var isLongPressing by remember { mutableStateOf(false) }
    var originalSpeed by remember { mutableFloatStateOf(1f) }

    // 自动隐藏
    val hideHandler = remember { Handler(Looper.getMainLooper()) }
    val hideRunnable = remember {
        Runnable {
            if (!isLocked && !isDragging && !showMoreMenu && !showSpeedMenu && !showAspectMenu) {
                isVisible = false
            }
        }
    }

    fun resetAutoHide() {
        hideHandler.removeCallbacks(hideRunnable)
        if (isVisible) {
            hideHandler.postDelayed(hideRunnable, 5000)
        }
    }

    fun toggleVisibility() {
        isVisible = !isVisible
        if (isVisible) resetAutoHide()
    }

    // ==================== 定时刷新播放进度 ====================
    LaunchedEffect(isVisible) {
        while (true) {
            videoView?.let { player ->
                if (!isDragging) {
                    currentPosition = player.currentPosition.toLong()
                    duration = player.duration.toLong()
                    bufferedPercent = player.bufferedPercentage
                    isPlaying = player.isPlaying
                    speed = player.speed
                }
            }
            delay(300)
        }
    }

    // 定时关闭倒计时
    LaunchedEffect(timerSeconds) {
        if (timerSeconds > 0) {
            timerRemaining = timerSeconds
            while (timerRemaining > 0) {
                delay(1000)
                timerRemaining -= 1
            }
            // 时间到，暂停播放
            videoView?.pause()
            timerSeconds = 0
            timerRemaining = 0
            Toast.makeText(context, "定时关闭时间到，已暂停播放", Toast.LENGTH_SHORT).show()
        }
    }

    // 长按加速逻辑
    LaunchedEffect(isLongPressing) {
        if (isLongPressing) {
            originalSpeed = speed
            videoView?.setSpeed(2f)
            showSpeedIndicator = true
            try {
                while (isLongPressing) {
                    delay(100)
                }
            } finally {
                // 确保松手后恢复原速
                videoView?.setSpeed(originalSpeed)
                showSpeedIndicator = false
            }
        }
    }

    // ==================== 手势处理 ====================
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // 拖拽 + 点击手势层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            toggleVisibility()
                        },
                        onDoubleTap = {
                            if (!isLocked) {
                                videoView?.let { player ->
                                    if (player.isPlaying) player.pause() else player.start()
                                }
                            }
                        },
                        onLongPress = {
                            if (!isLocked) {
                                isLongPressing = true
                            }
                        },
                        onPress = {
                            // 长按松手时重置
                            tryAwaitRelease()
                            if (isLongPressing) {
                                isLongPressing = false
                                showSpeedIndicator = false
                                videoView?.setSpeed(originalSpeed)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (isLocked) return@detectHorizontalDragGestures
                            isDragging = true
                            dragPosition = currentPosition
                            hideHandler.removeCallbacks(hideRunnable)
                        },
                        onDragEnd = {
                            if (isLocked) return@detectHorizontalDragGestures
                            isDragging = false
                            val finalPos = dragPosition
                            val dur = duration
                            dragPosition = 0L
                            showSeekIndicator = false
                            if (dur > 0 && finalPos in 0..dur) {
                                videoView?.seekTo(finalPos)
                            }
                            resetAutoHide()
                        },
                        onDragCancel = {
                            if (isLocked) return@detectHorizontalDragGestures
                            isDragging = false
                            dragPosition = 0L
                            showSeekIndicator = false
                            resetAutoHide()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (isLocked) return@detectHorizontalDragGestures
                            val dur = duration
                            if (dur <= 0) return@detectHorizontalDragGestures
                            // 拖拽距离映射到视频时长，使用屏幕宽度比例
                            val ratio = dur.toFloat() / size.width.toFloat().coerceAtLeast(1f)
                            val delta = (dragAmount * ratio).toLong()
                            dragPosition = (dragPosition + delta).coerceIn(0, dur)
                            showSeekIndicator = true
                            seekText = "${PlayerUtils.stringForTime(dragPosition.toInt())}/${PlayerUtils.stringForTime(dur.toInt())}"
                        }
                    )
                }
                .pointerInput(maxVolume) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            if (isLocked) return@detectVerticalDragGestures
                            // 根据起始位置判断是调节亮度还是音量
                            isDragging = true
                            hideHandler.removeCallbacks(hideRunnable)
                        },
                        onDragEnd = {
                            if (isLocked) return@detectVerticalDragGestures
                            isDragging = false
                            brightnessPercent = -1
                            volumePercent = -1
                            resetAutoHide()
                        },
                        onDragCancel = {
                            if (isLocked) return@detectVerticalDragGestures
                            isDragging = false
                            brightnessPercent = -1
                            volumePercent = -1
                            resetAutoHide()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (isLocked) return@detectVerticalDragGestures
                            val screenWidth = size.width.toFloat()
                            if (change.position.x < screenWidth / 2) {
                                // 左半屏：调节亮度
                                val newBrightness = (currentBrightness - dragAmount.toFloat() / size.height * 255).toInt().coerceIn(0, 255)
                                setScreenBrightness(context, newBrightness)
                                currentBrightness = newBrightness
                                brightnessPercent = (newBrightness * 100 / 255)
                            } else {
                                // 右半屏：调节音量
                                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val newVolume = (current - (dragAmount.toFloat() / size.height * maxVolume).toInt()).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                volumePercent = (newVolume * 100 / maxVolume)
                            }
                        }
                    )
                }
        )

        // ==================== 控制器 UI（可见时显示） ====================
        AnimatedVisibility(
            visible = isVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .align(Alignment.TopCenter)
                )

                // 底部渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .align(Alignment.BottomCenter)
                )

                // ==================== 顶部栏 ====================
                TopBar(
                    title = title,
                    episodeTitle = episodeTitle,
                    sourceName = sourceName,
                    isAudioOnly = isAudioOnly,
                    onBack = {
                        if (isLocked) {
                            Toast.makeText(context, "已锁定，请先解锁", Toast.LENGTH_SHORT).show()
                        } else {
                            onToggleFullScreen()
                        }
                    },
                    onCast = {
                        // 投屏由外部处理
                        onBack() // 退出全屏后再投屏
                    },
                    onDownload = {
                        Toast.makeText(context, "下载功能：请退出全屏后使用", Toast.LENGTH_SHORT).show()
                    },
                    onSeekBack = {
                        val pos = videoView?.currentPosition ?: 0
                        videoView?.seekTo(pos - 30000)
                        Toast.makeText(context, "后退30秒", Toast.LENGTH_SHORT).show()
                    },
                    onSeekForward = {
                        val pos = videoView?.currentPosition ?: 0
                        val dur = videoView?.duration ?: 0
                        videoView?.seekTo((pos + 30000).coerceAtMost(dur.toLong()))
                        Toast.makeText(context, "前进30秒", Toast.LENGTH_SHORT).show()
                    },
                    showMoreMenu = showMoreMenu,
                    onToggleMoreMenu = { showMoreMenu = !showMoreMenu },
                    onDismissMoreMenu = { showMoreMenu = false },
                    timerRemaining = timerRemaining,
                    onShowSkipIntro = {
                        showMoreMenu = false
                        showSkipIntroDialog = true
                    },
                    onShowTimer = {
                        showMoreMenu = false
                        showTimerMenu = true
                    },
                    onSetTimer = { seconds ->
                        timerSeconds = seconds
                        showTimerMenu = false
                        if (seconds > 0) {
                            Toast.makeText(context, "将在 ${seconds / 60} 分钟后暂停播放", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "已取消定时关闭", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleAudioOnly = {
                        isAudioOnly = !isAudioOnly
                        if (isAudioOnly) {
                            Toast.makeText(context, "听音频模式已开启", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "听音频模式已关闭", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // ==================== 听音频模式遮罩 ====================
                if (isAudioOnly) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = "听音频",
                                tint = Color(0xFF6750A4),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "听音频模式",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "视频已隐藏，仅播放音频",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // ==================== 底部控制栏 ====================
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // 时间文本
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = PlayerUtils.stringForTime(
                                if (isDragging && dragPosition > 0) dragPosition.toInt() else currentPosition.toInt()
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = PlayerUtils.stringForTime(duration.toInt()),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 进度条（MD3 Slider）
                    Slider(
                        value = if (isDragging && dragPosition > 0) dragPosition.toFloat()
                        else currentPosition.toFloat(),
                        onValueChange = { value ->
                            isDragging = true
                            dragPosition = value.toLong()
                            hideHandler.removeCallbacks(hideRunnable)
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            val finalPos = dragPosition
                            dragPosition = 0L
                            val dur = duration
                            if (dur > 0 && finalPos in 0..dur) {
                                videoView?.seekTo(finalPos)
                            }
                            resetAutoHide()
                        },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        enabled = duration > 0
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 底部按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左边：上一集、暂停/播放、下一集
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 上一集
                            IconButton(
                                onClick = onPrevEpisode,
                                enabled = hasPrevEpisode,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "上一集",
                                    tint = if (hasPrevEpisode) Color.White else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // 暂停/播放
                            IconButton(
                                onClick = {
                                    videoView?.let { player ->
                                        if (player.isPlaying) player.pause() else player.start()
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // 下一集
                            IconButton(
                                onClick = onNextEpisode,
                                enabled = hasNextEpisode,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "下一集",
                                    tint = if (hasNextEpisode) Color.White else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // 右边：倍速、比例、选集、缩小
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 倍速
                            Box {
                                IconButton(
                                    onClick = { showSpeedMenu = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Speed,
                                        contentDescription = "倍速",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false },
                                    offset = DpOffset((-100).dp, 0.dp)
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { s ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${s}x",
                                                    fontWeight = if (speed == s) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (speed == s) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                speed = s
                                                videoView?.setSpeed(s)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 比例
                            Box {
                                IconButton(
                                    onClick = { showAspectMenu = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AspectRatio,
                                        contentDescription = "画面比例",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAspectMenu,
                                    onDismissRequest = { showAspectMenu = false },
                                    offset = DpOffset((-100).dp, 0.dp)
                                ) {
                                    listOf(
                                        AppAspectRatio.ADAPT to "自适应",
                                        AppAspectRatio.FILL to "填充",
                                        AppAspectRatio.ZOOM to "裁剪"
                                    ).forEach { (ratio, label) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (SettingsManager.getAspectRatio() == ratio) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                videoView?.setScreenScaleType(ratio.toScreenScale())
                                                SettingsManager.setAspectRatio(ratio)
                                                showAspectMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 选集
                            IconButton(
                                onClick = {
                                    showEpisodeSheet = true
                                    onEpisodeList()
                                },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PlaylistPlay,
                                    contentDescription = "选集",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            // 缩小（退出全屏）
                            IconButton(
                                onClick = {
                                    if (isLocked) {
                                        Toast.makeText(context, "已锁定，请先解锁", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onToggleFullScreen()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FullscreenExit,
                                    contentDescription = "退出全屏",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== 锁定按钮（跟随控制器可见性） ====================
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            IconButton(
                onClick = {
                    isLocked = !isLocked
                    if (isLocked) {
                        isVisible = false
                    }
                },
                modifier = Modifier
                    .padding(start = 24.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (isLocked) "解锁" else "锁定",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ==================== 右侧：截图 + 小窗（跟随控制器可见性） ====================
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier.padding(end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 截图按钮
                IconButton(
                    onClick = {
                        takeScreenshot(context, videoView)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "截图",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // 小窗按钮
                IconButton(
                    onClick = {
                        enterPipMode(context)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PictureInPictureAlt,
                        contentDescription = "小窗",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ==================== 指示器覆盖层 ====================

        // 亮度指示器（竖进度条 + MD3 图标）
        if (brightnessPercent >= 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BrightnessMedium,
                        contentDescription = "亮度",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // 竖进度条
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction = brightnessPercent / 100f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF6750A4))
                                .align(Alignment.BottomCenter)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${brightnessPercent}%", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        // 音量指示器（竖进度条 + MD3 图标）
        if (volumePercent >= 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (volumePercent == 0) Icons.Outlined.VolumeOff
                        else Icons.Outlined.VolumeUp,
                        contentDescription = "音量",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // 竖进度条
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction = volumePercent / 100f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF6750A4))
                                .align(Alignment.BottomCenter)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${volumePercent}%", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        // 快进快退指示器
        if (showSeekIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = seekText, color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "滑动快进/快退",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 长按加速指示器
        AnimatedVisibility(
            visible = showSpeedIndicator && isLongPressing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加速播放中 2x",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ==================== 选集右侧滑出面板 ====================
    AnimatedVisibility(
        visible = showEpisodeSheet,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 半透明背景遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showEpisodeSheet = false }
            )
            // 右侧面板
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(Color(0xFF1C1B1F))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "选集",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showEpisodeSheet = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // 剧集列表
                    if (episodeCount > 0) {
                        val totalPages = (episodeCount + episodesPerPage - 1) / episodesPerPage
                        val startIdx = episodePage * episodesPerPage
                        val endIdx = minOf(startIdx + episodesPerPage, episodeCount)
                        val pageEpisodes = endIdx - startIdx

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(pageEpisodes) { offset ->
                                val index = startIdx + offset
                                val epNum = index + 1
                                val isSelected = index == selectedEpisodeIndex
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1.2f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) Color(0xFF6750A4)
                                            else Color.White.copy(alpha = 0.1f)
                                        )
                                        .clickable {
                                            onEpisodeSelected(index)
                                            showEpisodeSheet = false
                                            episodePage = 0
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = epNum.toString(),
                                        color = if (isSelected) Color.White
                                        else Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // 分页控制
                        if (totalPages > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        if (episodePage > 0) episodePage--
                                    },
                                    enabled = episodePage > 0
                                ) {
                                    Text(
                                        "上一页",
                                        color = if (episodePage > 0) Color(0xFF6750A4) else Color.White.copy(alpha = 0.3f),
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    text = (episodePage + 1).toString() + " / " + totalPages.toString(),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                TextButton(
                                    onClick = {
                                        if (episodePage < totalPages - 1) episodePage++
                                    },
                                    enabled = episodePage < totalPages - 1
                                ) {
                                    Text(
                                        "下一页",
                                        color = if (episodePage < totalPages - 1) Color(0xFF6750A4) else Color.White.copy(alpha = 0.3f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无剧集数据",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ==================== 跳过片头片尾弹窗 ====================
    if (showSkipIntroDialog) {
        SkipIntroDialog(
            onDismiss = { showSkipIntroDialog = false },
            onConfirm = { introSec, outroSec ->
                SettingsManager.setSkipIntroSeconds(introSec)
                SettingsManager.setSkipOutroSeconds(outroSec)
                SettingsManager.setSkipIntro(true)
                showSkipIntroDialog = false
                Toast.makeText(context, "已设置跳过片头${introSec}秒，片尾${outroSec}秒", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ==================== 定时关闭弹窗 ====================
    if (showTimerMenu) {
        TimerDialog(
            currentTimer = timerSeconds,
            onDismiss = { showTimerMenu = false },
            onSelect = { seconds ->
                showTimerMenu = false
                timerSeconds = seconds
                timerRemaining = seconds
                if (seconds > 0) {
                    Toast.makeText(context, "将在 ${seconds / 60} 分钟后暂停播放", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "已取消定时关闭", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// ==================== 顶部栏 ====================
@Composable
private fun TopBar(
    title: String,
    episodeTitle: String,
    sourceName: String,
    isAudioOnly: Boolean,
    onBack: () -> Unit,
    onCast: () -> Unit,
    onDownload: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    showMoreMenu: Boolean,
    onToggleMoreMenu: () -> Unit,
    onDismissMoreMenu: () -> Unit,
    timerRemaining: Long,
    onShowSkipIntro: () -> Unit,
    onShowTimer: () -> Unit,
    onSetTimer: (Long) -> Unit,
    onToggleAudioOnly: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ===== 左侧：返回 + 标题 + 剧集 + 来源 =====
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.padding(start = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (episodeTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = episodeTitle,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (sourceName.isNotBlank()) {
                    Text(
                        text = "来源：$sourceName",
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ===== 右侧：投屏、下载、前进后退、更多 =====
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 投屏
            IconButton(onClick = onCast, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Cast,
                    contentDescription = "投屏",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 下载
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "下载",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 后退30秒
            IconButton(
                onClick = onSeekBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FastRewind,
                    contentDescription = "后退30秒",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 前进30秒
            IconButton(
                onClick = onSeekForward,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = "前进30秒",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 更多
            Box {
                IconButton(
                    onClick = onToggleMoreMenu,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = onDismissMoreMenu,
                    offset = DpOffset(0.dp, 0.dp)
                ) {
                    // 跳过片头片尾
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "跳过片头片尾"
                            )
                        },
                        onClick = onShowSkipIntro
                    )
                    // 听音频（开关）
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "听音频",
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = isAudioOnly,
                                    onCheckedChange = { onToggleAudioOnly() },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        },
                        onClick = { onToggleAudioOnly() }
                    )
                    // 定时关闭
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (timerRemaining > 0) "定时关闭 (${timerRemaining / 60}:${String.format("%02d", timerRemaining % 60)})" else "定时关闭"
                            )
                        },
                        onClick = onShowTimer
                    )
                    // 取消定时（如果已设置）
                    if (timerRemaining > 0) {
                        DropdownMenuItem(
                            text = { Text("取消定时关闭", color = MaterialTheme.colorScheme.error) },
                            onClick = { onSetTimer(0L) }
                        )
                    }
                }
            }
        }
    }
}

// ==================== 跳过片头片尾弹窗 ====================
@Composable
private fun SkipIntroDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var introSeconds by remember {
        mutableIntStateOf(SettingsManager.getSkipIntroSeconds().coerceAtLeast(0))
    }
    var outroSeconds by remember {
        mutableIntStateOf(SettingsManager.getSkipOutroSeconds().coerceAtLeast(0))
    }
    var introText by remember { mutableStateOf(introSeconds.toString()) }
    var outroText by remember { mutableStateOf(outroSeconds.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳过片头片尾") },
        text = {
            Column {
                Text(
                    text = "片头跳过时间",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = introSeconds.toFloat(),
                        onValueChange = {
                            introSeconds = it.toInt()
                            introText = it.toInt().toString()
                        },
                        valueRange = 0f..300f,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = introText,
                        onValueChange = {
                            introText = it
                            it.toIntOrNull()?.let { v -> introSeconds = v.coerceIn(0, 300) }
                        },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        suffix = { Text("秒") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "片尾跳过时间",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = outroSeconds.toFloat(),
                        onValueChange = {
                            outroSeconds = it.toInt()
                            outroText = it.toInt().toString()
                        },
                        valueRange = 0f..300f,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = outroText,
                        onValueChange = {
                            outroText = it
                            it.toIntOrNull()?.let { v -> outroSeconds = v.coerceIn(0, 300) }
                        },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        suffix = { Text("秒") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(introSeconds, outroSeconds) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ==================== 定时关闭弹窗 ====================
@Composable
private fun TimerDialog(
    currentTimer: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val options = listOf(
        0L to "关闭",
        15L * 60 to "15 分钟",
        30L * 60 to "30 分钟",
        45L * 60 to "45 分钟",
        60L * 60 to "60 分钟",
        90L * 60 to "90 分钟"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时关闭") },
        text = {
            Column {
                options.forEach { (seconds, label) ->
                    val isSelected = currentTimer == seconds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

// ==================== 工具函数 ====================

/**
 * 获取当前屏幕亮度
 */
private fun getScreenBrightness(context: Context): Int {
    return try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS
        )
    } catch (_: Exception) {
        128
    }
}

/**
 * 设置屏幕亮度（优先修改系统亮度，其次修改窗口亮度）
 */
private fun setScreenBrightness(context: Context, brightness: Int) {
    val activity = context as? Activity ?: return
    // 尝试修改系统亮度（需要 WRITE_SETTINGS 权限）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
        try {
            // 关闭自动亮度
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness
            )
        } catch (_: Exception) {
            // 回退到窗口亮度
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = brightness / 255f
            activity.window.attributes = layoutParams
        }
    } else {
        // 无系统设置权限，修改窗口亮度
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = brightness / 255f
        activity.window.attributes = layoutParams
    }
}

/**
 * 截图（使用 PixelCopy 捕获窗口内容，包括 SurfaceView 视频）
 */
private fun takeScreenshot(context: Context, videoView: View?) {
    try {
        val activity = context as? Activity ?: return
        val window = activity.window
        // 获取窗口尺寸
        val decorView = window.decorView
        val width = decorView.width
        val height = decorView.height
        if (width <= 0 || height <= 0) {
            Toast.makeText(context, "截图失败：窗口尺寸无效", Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 使用 PixelCopy 捕获窗口（包括 SurfaceView）
            android.view.PixelCopy.request(
                window,
                bitmap,
                { copyResult ->
                    if (copyResult == android.view.PixelCopy.SUCCESS) {
                        saveScreenshot(context, bitmap)
                    } else {
                        bitmap.recycle()
                        Toast.makeText(context, "截图失败", Toast.LENGTH_SHORT).show()
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } else {
            // 低版本回退到 drawingCache
            decorView.isDrawingCacheEnabled = true
            decorView.buildDrawingCache()
            val cacheBitmap = decorView.drawingCache
            if (cacheBitmap != null) {
                val copy = cacheBitmap.copy(Bitmap.Config.ARGB_8888, false)
                saveScreenshot(context, copy)
                decorView.isDrawingCacheEnabled = false
            } else {
                bitmap.recycle()
                Toast.makeText(context, "截图失败", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "截图失败：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun saveScreenshot(context: Context, bitmap: Bitmap) {
    try {
        val dir = File(context.getExternalFilesDir(null), "Screenshots")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        Toast.makeText(context, "截图已保存到 ${file.absolutePath}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        bitmap.recycle()
        Toast.makeText(context, "截图保存失败：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 进入画中画模式
 */
private fun enterPipMode(context: Context) {
    val activity = context as? Activity ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Toast.makeText(context, "当前设备不支持画中画", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "当前设备不支持画中画", Toast.LENGTH_SHORT).show()
    }
}