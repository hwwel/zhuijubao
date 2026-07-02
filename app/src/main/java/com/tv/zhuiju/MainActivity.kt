package com.tv.zhuiju

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tv.zhuiju.ui.navigation.AppNavigation
import com.tv.zhuiju.ui.theme.追剧宝Theme
import com.tv.zhuiju.utils.SettingsManager

class MainActivity : ComponentActivity() {

    /** 当前是否在播放页（用于判断是否需要进入画中画） */
    var isPlayerActive: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            追剧宝Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        onPlayerActiveChange = { active ->
                            isPlayerActive = active
                        }
                    )
                }
            }
        }
    }

    /**
     * 用户离开应用时（按 Home 键或切换到其他应用），
     * 如果开启了自动画中画且当前在播放页，则自动进入画中画模式。
     */
    override fun onUserLeaveHint() {
        if (SettingsManager.isAutoPipEnabled() && isPlayerActive) {
            enterPipMode()
        }
        super.onUserLeaveHint()
    }

    /**
     * 进入画中画模式。
     */
    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                // 某些设备可能不支持画中画
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            // 从画中画恢复时，标记播放器仍活跃
            isPlayerActive = true
        }
    }
}