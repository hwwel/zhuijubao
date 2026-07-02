package com.tv.zhuiju.utils

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import com.tv.zhuiju.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.doikki.videoplayer.player.BaseVideoView
import xyz.doikki.videoplayer.player.VideoViewConfig
import xyz.doikki.videoplayer.player.VideoViewManager

/**
 * 统一设置管理器，为播放器、投屏、下载等模块提供设置接口。
 * 使用单例模式，通过 SharedPreferences 持久化，支持 Flow 观察变化。
 */
object SettingsManager {

    private lateinit var prefs: SharedPreferences

    /** 必须在 Application.onCreate() 中初始化 */
    fun init(context: Context) {
        prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        // 加载已保存的设置到 StateFlow
        _playerKernel.value = PlayerKernel.fromKey(prefs.getString(KEY_PLAYER_KERNEL, PlayerKernel.EXO.key)!!)
        _themeMode.value = ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.key)!!)
        _autoResume.value = prefs.getBoolean(KEY_AUTO_RESUME, true)
        _dlnaEnabled.value = prefs.getBoolean(KEY_DLNA_ENABLED, true)
        _wifiOnlyDownload.value = prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOAD, true)
        _skipIntro.value = prefs.getBoolean(KEY_SKIP_INTRO, false)
        _skipIntroSeconds.value = prefs.getInt(KEY_SKIP_INTRO_SECONDS, 0)
        _skipOutroSeconds.value = prefs.getInt(KEY_SKIP_OUTRO_SECONDS, 0)
        _defaultPlaybackSpeed.value = prefs.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, 1.0f)
        // 播放器高级设置
        _decoderMode.value = DecoderMode.fromKey(prefs.getString(KEY_DECODER_MODE, DecoderMode.HARDWARE.key)!!)
        _rendererType.value = RendererType.fromKey(prefs.getString(KEY_RENDERER_TYPE, RendererType.AUTO.key)!!)
        _lowLatencyAudio.value = prefs.getBoolean(KEY_LOW_LATENCY_AUDIO, true)
        _pictureQuality.value = PictureQuality.fromKey(prefs.getString(KEY_PICTURE_QUALITY, PictureQuality.AUTO.key)!!)
        _autoPip.value = prefs.getBoolean(KEY_AUTO_PIP, true)
        _gestureControls.value = prefs.getBoolean(KEY_GESTURE_CONTROLS, true)
        _aspectRatio.value = AspectRatio.fromKey(prefs.getString(KEY_ASPECT_RATIO, AspectRatio.ADAPT.key)!!)
        _hlsFilter.value = prefs.getBoolean(KEY_HLS_FILTER, false)

        // 初始化全局 VideoView 配置
        initVideoViewConfig(context)
    }

    /**
     * 初始化 DKVideoPlayer 的全局 VideoViewConfig，应用当前保存的设置。
     * 在 Application.onCreate() 中调用一次即可。
     */
    private fun initVideoViewConfig(context: Context) {
        val config = VideoViewConfig.newBuilder()
            .setScreenScaleType(getAspectRatio().toScreenScale())
            .setEnableAudioFocus(true)
            .setPlayOnMobileNetwork(true)
            .setLogEnabled(false)
            .build()
        VideoViewManager.setConfig(config)
    }

    /**
     * 将当前设置应用到已有的 VideoView 实例。
     * 在 PlayerScreen 创建 VideoView 后调用。
     */
    fun applyToVideoView(videoView: BaseVideoView<*>) {
        // 1. 画面比例
        videoView.setScreenScaleType(getAspectRatio().toScreenScale())

        // 2. 默认播放倍速
        videoView.setSpeed(getDefaultPlaybackSpeed())

        // 3. 音频焦点
        videoView.setEnableAudioFocus(true)

        // 4. 低延迟音频
        if (isLowLatencyAudioEnabled()) {
            applyLowLatencyAudio(videoView.context)
        }
    }

    /**
     * 将手势控制设置应用到 VideoController。
     */
    fun applyGestureToController(controller: Any) {
        try {
            // GestureVideoController 有 setGestureEnabled 和 setDoubleTapTogglePlayEnabled
            val setGestureEnabled = controller.javaClass.getMethod("setGestureEnabled", Boolean::class.javaPrimitiveType!!)
            setGestureEnabled.invoke(controller, isGestureControlsEnabled())
            val setDoubleTap = controller.javaClass.getMethod("setDoubleTapTogglePlayEnabled", Boolean::class.javaPrimitiveType!!)
            setDoubleTap.invoke(controller, true)
        } catch (_: Exception) {
            // 如果不是 GestureVideoController 或其子类，静默忽略
        }
    }

    /**
     * 低延迟音频：通过 AudioAttributes.FLAG_LOW_LATENCY 配置系统音频输出。
     */
    private fun applyLowLatencyAudio(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            // 请求音频焦点（使用低延迟媒体属性）
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // 设置低延迟参数
                audioManager.setParameters("low_latency=1")
            }
        } catch (_: Exception) {
            // 某些设备可能不支持，静默回退
        }
    }

    // ==================== 播放内核 ====================

    private val _playerKernel = MutableStateFlow(PlayerKernel.EXO)
    val playerKernel: StateFlow<PlayerKernel> = _playerKernel.asStateFlow()

    fun setPlayerKernel(kernel: PlayerKernel) {
        _playerKernel.value = kernel
        prefs.edit().putString(KEY_PLAYER_KERNEL, kernel.key).apply()
    }

    fun getPlayerKernel(): PlayerKernel = _playerKernel.value

    // ==================== 主题模式 ====================

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.key).apply()
    }

    fun getThemeMode(): ThemeMode = _themeMode.value

    // ==================== 自动续播 ====================

    private val _autoResume = MutableStateFlow(true)
    val autoResume: StateFlow<Boolean> = _autoResume.asStateFlow()

    fun setAutoResume(enabled: Boolean) {
        _autoResume.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_RESUME, enabled).apply()
    }

    fun isAutoResumeEnabled(): Boolean = _autoResume.value

    // ==================== DLNA 投屏 ====================

    private val _dlnaEnabled = MutableStateFlow(true)
    val dlnaEnabled: StateFlow<Boolean> = _dlnaEnabled.asStateFlow()

    fun setDlnaEnabled(enabled: Boolean) {
        _dlnaEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DLNA_ENABLED, enabled).apply()
    }

    fun isDlnaEnabled(): Boolean = _dlnaEnabled.value

    // ==================== 下载设置 ====================

    private val _wifiOnlyDownload = MutableStateFlow(true)
    val wifiOnlyDownload: StateFlow<Boolean> = _wifiOnlyDownload.asStateFlow()

    fun setWifiOnlyDownload(wifiOnly: Boolean) {
        _wifiOnlyDownload.value = wifiOnly
        prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOAD, wifiOnly).apply()
    }

    fun isWifiOnlyDownload(): Boolean = _wifiOnlyDownload.value

    // ==================== 跳过片头片尾 ====================

    private val _skipIntro = MutableStateFlow(false)
    val skipIntro: StateFlow<Boolean> = _skipIntro.asStateFlow()

    fun setSkipIntro(enabled: Boolean) {
        _skipIntro.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_INTRO, enabled).apply()
    }

    fun isSkipIntroEnabled(): Boolean = _skipIntro.value

    private val _skipIntroSeconds = MutableStateFlow(0)
    val skipIntroSeconds: StateFlow<Int> = _skipIntroSeconds.asStateFlow()

    fun setSkipIntroSeconds(seconds: Int) {
        _skipIntroSeconds.value = seconds.coerceIn(0, 300)
        prefs.edit().putInt(KEY_SKIP_INTRO_SECONDS, seconds.coerceIn(0, 300)).apply()
    }

    fun getSkipIntroSeconds(): Int = _skipIntroSeconds.value

    private val _skipOutroSeconds = MutableStateFlow(0)
    val skipOutroSeconds: StateFlow<Int> = _skipOutroSeconds.asStateFlow()

    fun setSkipOutroSeconds(seconds: Int) {
        _skipOutroSeconds.value = seconds.coerceIn(0, 300)
        prefs.edit().putInt(KEY_SKIP_OUTRO_SECONDS, seconds.coerceIn(0, 300)).apply()
    }

    fun getSkipOutroSeconds(): Int = _skipOutroSeconds.value

    // ==================== 默认播放倍速 ====================

    private val _defaultPlaybackSpeed = MutableStateFlow(1.0f)
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    fun setDefaultPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        _defaultPlaybackSpeed.value = clamped
        prefs.edit().putFloat(KEY_DEFAULT_PLAYBACK_SPEED, clamped).apply()
    }

    fun getDefaultPlaybackSpeed(): Float = _defaultPlaybackSpeed.value

    // ==================== 解码器模式 ====================

    private val _decoderMode = MutableStateFlow(DecoderMode.HARDWARE)
    val decoderMode: StateFlow<DecoderMode> = _decoderMode.asStateFlow()

    fun setDecoderMode(mode: DecoderMode) {
        _decoderMode.value = mode
        prefs.edit().putString(KEY_DECODER_MODE, mode.key).apply()
    }

    fun getDecoderMode(): DecoderMode = _decoderMode.value

    // ==================== 渲染器类型 ====================

    private val _rendererType = MutableStateFlow(RendererType.AUTO)
    val rendererType: StateFlow<RendererType> = _rendererType.asStateFlow()

    fun setRendererType(type: RendererType) {
        _rendererType.value = type
        prefs.edit().putString(KEY_RENDERER_TYPE, type.key).apply()
    }

    fun getRendererType(): RendererType = _rendererType.value

    // ==================== 低延迟音频 ====================

    private val _lowLatencyAudio = MutableStateFlow(true)
    val lowLatencyAudio: StateFlow<Boolean> = _lowLatencyAudio.asStateFlow()

    fun setLowLatencyAudio(enabled: Boolean) {
        _lowLatencyAudio.value = enabled
        prefs.edit().putBoolean(KEY_LOW_LATENCY_AUDIO, enabled).apply()
    }

    fun isLowLatencyAudioEnabled(): Boolean = _lowLatencyAudio.value

    // ==================== 画质偏好 ====================

    private val _pictureQuality = MutableStateFlow(PictureQuality.AUTO)
    val pictureQuality: StateFlow<PictureQuality> = _pictureQuality.asStateFlow()

    fun setPictureQuality(quality: PictureQuality) {
        _pictureQuality.value = quality
        prefs.edit().putString(KEY_PICTURE_QUALITY, quality.key).apply()
    }

    fun getPictureQuality(): PictureQuality = _pictureQuality.value

    // ==================== 自动画中画 ====================

    private val _autoPip = MutableStateFlow(true)
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()

    fun setAutoPip(enabled: Boolean) {
        _autoPip.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_PIP, enabled).apply()
    }

    fun isAutoPipEnabled(): Boolean = _autoPip.value

    // ==================== 手势控制 ====================

    private val _gestureControls = MutableStateFlow(true)
    val gestureControls: StateFlow<Boolean> = _gestureControls.asStateFlow()

    fun setGestureControls(enabled: Boolean) {
        _gestureControls.value = enabled
        prefs.edit().putBoolean(KEY_GESTURE_CONTROLS, enabled).apply()
    }

    fun isGestureControlsEnabled(): Boolean = _gestureControls.value

    // ==================== 画面比例 ====================

    private val _aspectRatio = MutableStateFlow(AspectRatio.ADAPT)
    val aspectRatio: StateFlow<AspectRatio> = _aspectRatio.asStateFlow()

    fun setAspectRatio(ratio: AspectRatio) {
        _aspectRatio.value = ratio
        prefs.edit().putString(KEY_ASPECT_RATIO, ratio.key).apply()
    }

    fun getAspectRatio(): AspectRatio = _aspectRatio.value

    // ==================== HLS广告过滤 ====================

    private val _hlsFilter = MutableStateFlow(false)
    val hlsFilter: StateFlow<Boolean> = _hlsFilter.asStateFlow()

    fun setHlsFilter(enabled: Boolean) {
        _hlsFilter.value = enabled
        prefs.edit().putBoolean(KEY_HLS_FILTER, enabled).apply()
    }

    fun isHlsFilterEnabled(): Boolean = _hlsFilter.value

    // ==================== 偏好键常量 ====================

    private const val KEY_PLAYER_KERNEL = "player_kernel"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AUTO_RESUME = "auto_resume"
    private const val KEY_DLNA_ENABLED = "dlna_enabled"
    private const val KEY_WIFI_ONLY_DOWNLOAD = "wifi_only_download"
    private const val KEY_SKIP_INTRO = "skip_intro"
    private const val KEY_SKIP_INTRO_SECONDS = "skip_intro_seconds"
    private const val KEY_SKIP_OUTRO_SECONDS = "skip_outro_seconds"
    private const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
    private const val KEY_DECODER_MODE = "decoder_mode"
    private const val KEY_RENDERER_TYPE = "renderer_type"
    private const val KEY_LOW_LATENCY_AUDIO = "low_latency_audio"
    private const val KEY_PICTURE_QUALITY = "picture_quality"
    private const val KEY_AUTO_PIP = "auto_pip"
    private const val KEY_GESTURE_CONTROLS = "gesture_controls"
    private const val KEY_ASPECT_RATIO = "aspect_ratio"
    private const val KEY_HLS_FILTER = "hls_filter"
}

/** 播放内核选项 */
enum class PlayerKernel(val label: String, val key: String) {
    EXO("ExoPlayer", "exo"),
    IJK("IJKPlayer", "ijk"),
    MEDIA("MediaPlayer", "media");

    companion object {
        fun fromKey(key: String): PlayerKernel =
            entries.find { it.key == key } ?: EXO
    }
}

/** 主题模式 */
enum class ThemeMode(val label: String, val key: String) {
    SYSTEM("跟随系统", "system"),
    LIGHT("浅色模式", "light"),
    DARK("深色模式", "dark");

    companion object {
        fun fromKey(key: String): ThemeMode =
            entries.find { it.key == key } ?: SYSTEM
    }
}

/** 解码器模式 */
enum class DecoderMode(val label: String, val desc: String, val key: String) {
    HARDWARE("硬件解码", "使用GPU解码，功耗低，兼容性好", "hardware"),
    SOFTWARE("软件解码", "使用CPU解码，兼容性更广，部分格式更稳定", "software");

    companion object {
        fun fromKey(key: String): DecoderMode =
            entries.find { it.key == key } ?: HARDWARE
    }
}

/** 渲染器类型 */
enum class RendererType(val label: String, val desc: String, val key: String) {
    AUTO("自动", "由系统自动选择最佳渲染器", "auto"),
    OPENGL("OpenGL", "使用OpenGL ES渲染，画质更优", "opengl"),
    MEDIACODEC("MediaCodec", "使用MediaCodec渲染，兼容性更好", "mediacodec");

    companion object {
        fun fromKey(key: String): RendererType =
            entries.find { it.key == key } ?: AUTO
    }
}

/** 画质偏好 */
enum class PictureQuality(val label: String, val desc: String, val key: String) {
    AUTO("自动", "根据网络自动选择最佳画质", "auto"),
    HD("1080P", "优先选择1080P高清画质", "1080p"),
    QHD("2K", "优先选择2K超清画质", "2k"),
    UHD("4K", "优先选择4K超高清画质", "4k");

    companion object {
        fun fromKey(key: String): PictureQuality =
            entries.find { it.key == key } ?: AUTO
    }
}

/** 画面比例 */
enum class AspectRatio(val label: String, val desc: String, val key: String) {
    ADAPT("自适应", "自动适配视频原始比例", "adapt"),
    FILL("填充", "拉伸填充整个屏幕", "fill"),
    ZOOM("裁剪", "居中裁剪以填充屏幕", "zoom");

    companion object {
        fun fromKey(key: String): AspectRatio =
            entries.find { it.key == key } ?: ADAPT
    }

    /** 转换为 DKVideoPlayer 的屏幕缩放常量 */
    fun toScreenScale(): Int = when (this) {
        ADAPT -> BaseVideoView.SCREEN_SCALE_DEFAULT
        FILL -> BaseVideoView.SCREEN_SCALE_MATCH_PARENT
        ZOOM -> BaseVideoView.SCREEN_SCALE_CENTER_CROP
    }
}