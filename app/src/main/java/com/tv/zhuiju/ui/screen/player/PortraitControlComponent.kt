package com.tv.zhuiju.ui.screen.player

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.view.View
import android.view.animation.Animation
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.doikki.videoplayer.controller.ControlWrapper
import xyz.doikki.videoplayer.controller.IControlComponent
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.util.PlayerUtils

/**
 * DKVideoPlayer 的竖屏自定义控制组件。
 * 内部使用 ComposeView 渲染 [PortraitControllerOverlay]。
 *
 * 实现 IControlComponent，在竖屏状态下显示，替代默认的 VodControlView。
 * 触摸事件透传给 ComposeView 处理按钮点击，未消费的触摸（如滑动）穿透到底层 GestureView 处理手势。
 * 显隐与 DKVideoPlayer 控制器同步：通过 onVisibilityChanged 回调同步 Compose 覆盖层。
 *
 * 布局：
 *  [返回] 剧集名+集数                     [电量]
 *  [暂停] 00:00 ==========进度========== 00:00 [全屏]
 */
class PortraitControlComponent(
    context: Context,
    private val videoView: VideoView?,
) : FrameLayout(context), IControlComponent {

    private var controlWrapper: ControlWrapper? = null
    // 供 Compose 层在自动隐藏时同步隐藏控制器
    internal val wrapper: ControlWrapper? get() = controlWrapper
    private val composeView: ComposeView

    // 由外部更新的数据
    private val titleState = mutableStateOf("")
    private val episodeTitleState = mutableStateOf("")
    private val hasPrevEpisodeState = mutableStateOf(false)
    private val hasNextEpisodeState = mutableStateOf(false)

    // 回调
    private var onBackCallback: (() -> Unit)? = null
    private var onPrevEpisodeCallback: (() -> Unit)? = null
    private var onNextEpisodeCallback: (() -> Unit)? = null
    private var onToggleFullScreenCallback: (() -> Unit)? = null

    // ==================== 进度状态（由 setProgress 回调 + Compose 轮询共同更新） ====================
    /** 当前播放位置（毫秒），由 setProgress 和 Compose 轮询更新 */
    var progressPosition by mutableLongStateOf(0L)
    /** 视频总时长（毫秒） */
    var progressDuration by mutableLongStateOf(0L)
    /** 是否正在播放 */
    var isPlaying by mutableStateOf(true)

    /** 控制器覆盖层是否可见（与 DKVideoPlayer 控制器显隐同步，供 Compose 层读取） */
    var isOverlayVisible by mutableStateOf(true)

    init {
        // 默认可见（竖屏场景），onPlayerStateChanged 切换全屏时隐藏
        visibility = View.VISIBLE
        // 不拦截触摸事件，让 Compose 按钮和底层手势都能正常工作
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false

        composeView = ComposeView(context)
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 把自身引用传给 Compose 层，用于状态同步
        val self = this
        composeView.setContent {
            PortraitControllerOverlay(
                videoView = videoView,
                portraitComponent = self,
                title = titleState.value,
                episodeTitle = episodeTitleState.value,
                hasPrevEpisode = hasPrevEpisodeState.value,
                hasNextEpisode = hasNextEpisodeState.value,
                onBack = { onBackCallback?.invoke() },
                onPrevEpisode = { onPrevEpisodeCallback?.invoke() },
                onNextEpisode = { onNextEpisodeCallback?.invoke() },
                onToggleFullScreen = { onToggleFullScreenCallback?.invoke() }
            )
        }
    }

    fun updateData(
        title: String,
        episodeTitle: String,
        hasPrevEpisode: Boolean,
        hasNextEpisode: Boolean,
        onBack: () -> Unit,
        onPrevEpisode: () -> Unit,
        onNextEpisode: () -> Unit,
        onToggleFullScreen: () -> Unit
    ) {
        titleState.value = title
        episodeTitleState.value = episodeTitle
        hasPrevEpisodeState.value = hasPrevEpisode
        hasNextEpisodeState.value = hasNextEpisode
        onBackCallback = onBack
        onPrevEpisodeCallback = onPrevEpisode
        onNextEpisodeCallback = onNextEpisode
        onToggleFullScreenCallback = onToggleFullScreen
    }

    // ==================== IControlComponent 实现 ====================

    override fun attach(controlWrapper: ControlWrapper) {
        this.controlWrapper = controlWrapper
    }

    override fun getView(): View = this

    /**
     * 与 DKVideoPlayer 控制器显隐同步。
     * 控制器显示时 → Compose 覆盖层显示
     * 控制器隐藏时 → Compose 覆盖层隐藏
     */
    override fun onVisibilityChanged(isVisible: Boolean, anim: Animation?) {
        isOverlayVisible = isVisible
    }

    override fun onPlayStateChanged(playState: Int) {}

    override fun onPlayerStateChanged(playerState: Int) {
        when (playerState) {
            VideoView.PLAYER_NORMAL -> visibility = View.VISIBLE
            VideoView.PLAYER_FULL_SCREEN -> visibility = View.GONE
        }
    }

    /**
     * 接收控制器下发的进度回调（约每秒一次），更新 Compose 状态。
     */
    override fun setProgress(duration: Int, position: Int) {
        progressDuration = duration.toLong()
        progressPosition = position.toLong()
    }

    override fun onLockStateChanged(isLocked: Boolean) {}
}

/**
 * 竖屏自定义控制器 Compose 覆盖层
 *
 * 布局：
 *  手势层（始终存在）：双击暂停、滑动调节音量/亮度/进度、长按加速
 *  控制器 UI（可见时显示）：
 *    [返回] 剧集名+集数                     [电量]
 *    [暂停] 00:00 ==========进度========== 00:00 [全屏]
 *
 * 显隐由 DKVideoPlayer 控制器通过 onVisibilityChanged 管理，
 * 按钮点击时重置自动隐藏计时器。
 * 手势层覆盖整个区域，按钮在 Z 轴上层优先接收事件。
 */
@Composable
fun PortraitControllerOverlay(
    videoView: VideoView?,
    portraitComponent: PortraitControlComponent,
    title: String,
    episodeTitle: String,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    onBack: () -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    // ==================== 手势状态 ====================
    var isDragging by mutableStateOf(false)
    var dragPosition by mutableLongStateOf(0L)

    // 手势指示器
    var brightnessPercent by mutableIntStateOf(-1)
    var currentBrightness by mutableIntStateOf(getScreenBrightness(context))
    var volumePercent by mutableIntStateOf(-1)
    var showSeekIndicator by mutableStateOf(false)
    var seekText by mutableStateOf("")
    var showSpeedIndicator by mutableStateOf(false)

    // 长按加速
    var isLongPressing by mutableStateOf(false)
    var originalSpeed by mutableFloatStateOf(1f)

    // 电量
    var batteryLevel by mutableIntStateOf(getBatteryLevel(context))

    // ==================== 定时刷新播放进度（每300ms） ====================
    LaunchedEffect(Unit) {
        while (true) {
            videoView?.let { player ->
                if (!isDragging) {
                    portraitComponent.progressPosition = player.currentPosition.toLong()
                    portraitComponent.progressDuration = player.duration.toLong()
                    portraitComponent.isPlaying = player.isPlaying
                }
            }
            delay(300)
        }
    }

    // ==================== 长按加速逻辑 ====================
    LaunchedEffect(isLongPressing) {
        if (isLongPressing) {
            originalSpeed = videoView?.speed ?: 1f
            videoView?.setSpeed(2f)
            showSpeedIndicator = true
            try {
                while (isLongPressing) {
                    delay(100)
                }
            } finally {
                videoView?.setSpeed(originalSpeed)
                showSpeedIndicator = false
            }
        }
    }

    // 从 portraitComponent 读取最新进度和显隐状态
    val isVisible = portraitComponent.isOverlayVisible
    val currentPosition = portraitComponent.progressPosition
    val duration = portraitComponent.progressDuration
    val isPlaying = portraitComponent.isPlaying

    // ==================== 根布局 ====================
    Box(modifier = modifier.fillMaxSize()) {

        // ==================== Layer 1: 手势检测层（始终存在，按钮未消费的事件会穿透到此层） ====================
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 点击/双击/长按手势
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // 单击：切换控制器显隐
                            if (portraitComponent.isOverlayVisible) {
                                portraitComponent.wrapper?.hide()
                            } else {
                                portraitComponent.wrapper?.show()
                            }
                        },
                        onDoubleTap = {
                            // 双击：暂停/播放
                            videoView?.let { player ->
                                if (player.isPlaying) player.pause() else player.start()
                            }
                        },
                        onLongPress = {
                            // 长按：加速到2倍
                            isLongPressing = true
                        },
                        onPress = {
                            // 长按松手时恢复速度
                            tryAwaitRelease()
                            if (isLongPressing) {
                                isLongPressing = false
                            }
                        }
                    )
                }
                // 水平拖动：调节进度
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragPosition = currentPosition
                        },
                        onDragEnd = {
                            isDragging = false
                            val finalPos = dragPosition
                            val dur = duration
                            dragPosition = 0L
                            showSeekIndicator = false
                            if (dur > 0 && finalPos in 0..dur) {
                                videoView?.seekTo(finalPos)
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            dragPosition = 0L
                            showSeekIndicator = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val dur = duration
                            if (dur <= 0) return@detectHorizontalDragGestures
                            val ratio = dur.toFloat() / size.width.toFloat().coerceAtLeast(1f)
                            val delta = (dragAmount * ratio).toLong()
                            dragPosition = (dragPosition + delta).coerceIn(0, dur)
                            showSeekIndicator = true
                            seekText = "${PlayerUtils.stringForTime(dragPosition.toInt())}/${PlayerUtils.stringForTime(dur.toInt())}"
                        }
                    )
                }
                // 垂直拖动：左半屏亮度、右半屏音量
                .pointerInput(maxVolume) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            brightnessPercent = -1
                            volumePercent = -1
                        },
                        onDragCancel = {
                            isDragging = false
                            brightnessPercent = -1
                            volumePercent = -1
                        },
                        onVerticalDrag = { change, dragAmount ->
                            val screenWidth = size.width.toFloat()
                            if (change.position.x < screenWidth / 2) {
                                // 左半屏：调节亮度
                                val newBrightness = (
                                    currentBrightness - dragAmount.toFloat() / size.height * 255
                                ).toInt().coerceIn(0, 255)
                                setScreenBrightness(context, newBrightness)
                                currentBrightness = newBrightness
                                brightnessPercent = (newBrightness * 100 / 255)
                            } else {
                                // 右半屏：调节音量
                                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val newVolume = (
                                    current - (dragAmount.toFloat() / size.height * maxVolume).toInt()
                                ).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                volumePercent = (newVolume * 100 / maxVolume)
                            }
                        }
                    )
                }
        )

        // ==================== Layer 2: 手势指示器 ====================

        // 亮度指示器（左半屏）
        if (brightnessPercent >= 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .width(40.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction = brightnessPercent / 100f)
                        .background(Color.White.copy(alpha = 0.8f))
                        .align(Alignment.BottomCenter)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.BrightnessMedium,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${brightnessPercent}%",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 音量指示器（右半屏）
        if (volumePercent >= 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .width(40.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction = volumePercent / 100f)
                        .background(Color.White.copy(alpha = 0.8f))
                        .align(Alignment.BottomCenter)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${volumePercent}%",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 进度指示器（居中）
        if (showSeekIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = seekText,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        // 倍速指示器（居中偏下）
        if (showSpeedIndicator && isLongPressing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "2x 加速播放",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ==================== Layer 3: 控制器 UI（仅可见时显示） ====================
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .align(Alignment.TopCenter)
                )

                // 底部渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .align(Alignment.BottomCenter)
                )

                // ==================== 顶部栏：返回 + 剧集名+集数 + 电量 ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (episodeTitle.isNotBlank()) {
                            Text(
                                text = episodeTitle,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = "${batteryLevel}%",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // ==================== 底部控制栏：暂停 + 时间 + 进度 + 时间 + 全屏 ====================
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                videoView?.let { player ->
                                    if (player.isPlaying) player.pause() else player.start()
                                }
                                portraitComponent.wrapper?.show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = PlayerUtils.stringForTime(
                                if (isDragging && dragPosition > 0) dragPosition.toInt() else currentPosition.toInt()
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        Slider(
                            value = if (isDragging && dragPosition > 0) dragPosition.toFloat()
                            else currentPosition.toFloat(),
                            onValueChange = { value ->
                                isDragging = true
                                dragPosition = value.toLong()
                                portraitComponent.wrapper?.show()
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                val finalPos = dragPosition
                                dragPosition = 0L
                                val dur = duration
                                if (dur > 0 && finalPos in 0..dur) {
                                    videoView?.seekTo(finalPos)
                                }
                                portraitComponent.wrapper?.show()
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .padding(horizontal = 4.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            enabled = duration > 0
                        )

                        Text(
                            text = PlayerUtils.stringForTime(duration.toInt()),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        IconButton(
                            onClick = {
                                onToggleFullScreen()
                                portraitComponent.wrapper?.show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = "全屏",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 获取当前屏幕亮度（0-255）
 */
private fun getScreenBrightness(context: Context): Int {
    return try {
        val activity = context as? android.app.Activity
        val brightness = activity?.window?.attributes?.screenBrightness ?: -1f
        if (brightness >= 0) (brightness * 255).toInt() else 127
    } catch (_: Exception) { 127 }
}

/**
 * 设置屏幕亮度（0-255）
 */
private fun setScreenBrightness(context: Context, brightness: Int) {
    try {
        val activity = context as? android.app.Activity
        if (activity != null) {
            val lp = activity.window.attributes
            lp.screenBrightness = brightness.coerceIn(0, 255) / 255f
            activity.window.attributes = lp
        }
    } catch (_: Exception) { }
}

/**
 * 获取当前电量百分比
 */
private fun getBatteryLevel(context: Context): Int {
    return try {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            100
        }
    } catch (_: Exception) {
        100
    }
}