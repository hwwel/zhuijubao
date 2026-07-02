package com.tv.zhuiju.ui.screen.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import xyz.doikki.videoplayer.controller.ControlWrapper
import xyz.doikki.videoplayer.controller.IControlComponent
import xyz.doikki.videoplayer.player.VideoView

/**
 * DKVideoPlayer 的横屏全屏控制组件。
 * 内部使用 ComposeView 渲染 [LandscapeControllerOverlay]。
 *
 * 作为 IControlComponent 添加到控制器中，跟随 VideoView 全屏进入 decorView，
 * 确保层级在视频之上且触摸事件正确传递。
 */
class LandscapeControlComponent(
    context: Context,
    private val videoView: VideoView?,
) : FrameLayout(context), IControlComponent {

    private var controlWrapper: ControlWrapper? = null
    private val composeView: ComposeView

    // 由外部更新的数据，Compose 会响应式重组
    private val titleState = mutableStateOf("")
    private val episodeTitleState = mutableStateOf("")
    private val sourceNameState = mutableStateOf("")
    private val hasPrevEpisodeState = mutableStateOf(false)
    private val hasNextEpisodeState = mutableStateOf(false)
    private val episodeCountState = mutableStateOf(0)
    private val selectedEpisodeIndexState = mutableStateOf(0)

    // 回调
    private var onBackCallback: (() -> Unit)? = null
    private var onPrevEpisodeCallback: (() -> Unit)? = null
    private var onNextEpisodeCallback: (() -> Unit)? = null
    private var onEpisodeListCallback: (() -> Unit)? = null
    private var onEpisodeSelectedCallback: ((Int) -> Unit)? = null

    init {
        visibility = View.GONE
        composeView = ComposeView(context)
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        composeView.setContent {
            LandscapeControllerOverlay(
                videoView = videoView,
                title = titleState.value,
                episodeTitle = episodeTitleState.value,
                sourceName = sourceNameState.value,
                hasPrevEpisode = hasPrevEpisodeState.value,
                hasNextEpisode = hasNextEpisodeState.value,
                episodeCount = episodeCountState.value,
                selectedEpisodeIndex = selectedEpisodeIndexState.value,
                onBack = { onBackCallback?.invoke() },
                onPrevEpisode = { onPrevEpisodeCallback?.invoke() },
                onNextEpisode = { onNextEpisodeCallback?.invoke() },
                onEpisodeList = { onEpisodeListCallback?.invoke() },
                onEpisodeSelected = { onEpisodeSelectedCallback?.invoke(it) },
                onToggleFullScreen = {
                    val activity = videoView?.context as? Activity
                    activity?.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    videoView?.stopFullScreen()
                }
            )
        }
    }

    fun updateData(
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
    ) {
        titleState.value = title
        episodeTitleState.value = episodeTitle
        sourceNameState.value = sourceName
        hasPrevEpisodeState.value = hasPrevEpisode
        hasNextEpisodeState.value = hasNextEpisode
        episodeCountState.value = episodeCount
        selectedEpisodeIndexState.value = selectedEpisodeIndex
        onBackCallback = onBack
        onPrevEpisodeCallback = onPrevEpisode
        onNextEpisodeCallback = onNextEpisode
        onEpisodeListCallback = onEpisodeList
        onEpisodeSelectedCallback = onEpisodeSelected
    }

    // ==================== IControlComponent 实现 ====================

    override fun attach(controlWrapper: ControlWrapper) {
        this.controlWrapper = controlWrapper
    }

    override fun getView(): View = this

    override fun onVisibilityChanged(isVisible: Boolean, anim: Animation?) {
        // 不响应控制器的全局显隐，由 onPlayerStateChanged 管理
    }

    override fun onPlayStateChanged(playState: Int) {}

    override fun onPlayerStateChanged(playerState: Int) {
        when (playerState) {
            VideoView.PLAYER_FULL_SCREEN -> visibility = View.VISIBLE
            VideoView.PLAYER_NORMAL -> visibility = View.GONE
        }
    }

    override fun setProgress(duration: Int, position: Int) {}

    override fun onLockStateChanged(isLocked: Boolean) {}
}