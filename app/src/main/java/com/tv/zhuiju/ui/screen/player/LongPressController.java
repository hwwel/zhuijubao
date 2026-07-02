package com.tv.zhuiju.ui.screen.player;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tv.zhuiju.R;

import xyz.doikki.videocontroller.component.CompleteView;
import xyz.doikki.videocontroller.component.ErrorView;
import xyz.doikki.videocontroller.component.GestureView;
import xyz.doikki.videocontroller.component.PrepareView;
import xyz.doikki.videocontroller.component.VodControlView;
import xyz.doikki.videocontroller.StandardVideoController;
import xyz.doikki.videoplayer.controller.IControlComponent;
import xyz.doikki.videoplayer.player.VideoView;

public class LongPressController extends StandardVideoController {

    private boolean mIsLongPressed = false;
    private float mOriginalSpeed = 1f;
    private SimpleTitleView mSimpleTitleView;
    private VodControlView mVodControlView;
    private PrepareView mPrepareView;
    private FullScreenListener mFullScreenListener;
    /** 竖屏自定义控制器引用，用于点击切换显隐 */
    private PortraitControlComponent mPortraitComponent;

    public LongPressController(@NonNull Context context) {
        super(context);
    }

    public LongPressController(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LongPressController(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * 全屏状态监听器，用于通知 Compose 层切换自定义横屏控制器
     */
    public interface FullScreenListener {
        void onFullScreenChanged(boolean isFullScreen);
    }

    public void setFullScreenListener(FullScreenListener listener) {
        mFullScreenListener = listener;
    }

    /**
     * 设置竖屏自定义控制器，用于点击时切换显隐
     */
    public void setPortraitComponent(PortraitControlComponent component) {
        mPortraitComponent = component;
    }

    /**
     * 重写以使用 SimpleTitleView 替代 TitleView，竖屏和横屏都显示标题
     */
    @Override
    public void addDefaultControlComponent(String title, boolean isLive) {
        CompleteView completeView = new CompleteView(getContext());
        ErrorView errorView = new ErrorView(getContext());
        mSimpleTitleView = new SimpleTitleView(getContext());
        mSimpleTitleView.setTitle(title);
        mPrepareView = new PrepareView(getContext());
        mPrepareView.setClickStart();
        addControlComponent(completeView, errorView, mPrepareView, mSimpleTitleView);
        if (isLive) {
            addControlComponent(new xyz.doikki.videocontroller.component.LiveControlView(getContext()));
        } else {
            mVodControlView = new VodControlView(getContext());
            addControlComponent(mVodControlView);
            // 应用 MD3 风格进度条
            applyMd3SeekBarStyle(mVodControlView);
        }
        addControlComponent(new GestureView(getContext()));
        setCanChangePosition(!isLive);
    }

    public SimpleTitleView getSimpleTitleView() {
        return mSimpleTitleView;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (!mIsLocked && mControlWrapper != null && mControlWrapper.isPlaying()) {
            mIsLongPressed = true;
            mOriginalSpeed = mControlWrapper.getSpeed();
            mControlWrapper.setSpeed(2f);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (mIsLongPressed && mControlWrapper != null) {
                mControlWrapper.setSpeed(mOriginalSpeed);
                mIsLongPressed = false;
            }
        }
        return result;
    }

    /**
     * 隐藏原生控制组件（横屏时由 Compose 覆盖层接管）
     */
    public void hideNativeControls() {
        if (mSimpleTitleView != null) {
            mSimpleTitleView.setVisibility(GONE);
        }
        if (mVodControlView != null) {
            mVodControlView.setVisibility(GONE);
        }
        if (mPrepareView != null) {
            mPrepareView.setVisibility(GONE);
        }
        if (mLockButton != null) {
            mLockButton.setVisibility(GONE);
        }
    }

    /**
     * 显示原生控制组件（退出横屏时恢复）
     */
    public void showNativeControls() {
        if (mSimpleTitleView != null && mControlWrapper != null && mControlWrapper.isShowing()) {
            mSimpleTitleView.setVisibility(VISIBLE);
        }
        if (mVodControlView != null) {
            mVodControlView.setVisibility(VISIBLE);
        }
        if (mPrepareView != null) {
            mPrepareView.setVisibility(VISIBLE);
        }
        if (mLockButton != null) {
            mLockButton.setVisibility(mControlWrapper != null && mControlWrapper.isFullScreen() ? VISIBLE : GONE);
        }
    }

    @Override
    protected void onPlayerStateChanged(int playerState) {
        super.onPlayerStateChanged(playerState);
        switch (playerState) {
            case VideoView.PLAYER_FULL_SCREEN:
                if (mFullScreenListener != null) {
                    mFullScreenListener.onFullScreenChanged(true);
                }
                break;
            case VideoView.PLAYER_NORMAL:
                if (mFullScreenListener != null) {
                    mFullScreenListener.onFullScreenChanged(false);
                }
                break;
        }
    }

    @Override
    public void show() {
        // 先隐藏默认控件，再调用 super.show()，避免默认控件闪烁
        hideNativeControls();
        super.show();
        // 竖屏时手动通知 PortraitControlComponent 显示
        if (mPortraitComponent != null && (mControlWrapper == null || !mControlWrapper.isFullScreen())) {
            mPortraitComponent.onVisibilityChanged(true, null);
        }
    }

    @Override
    protected void onVisibilityChanged(boolean isVisible, Animation anim) {
        // 全屏时不显示原生控件，由 LandscapeControlComponent 接管
        if (mControlWrapper != null && mControlWrapper.isFullScreen()) {
            return;
        }
        // 不调 super，避免 StandardVideoController 触发默认控件显示
        // 手动通知 PortraitControlComponent，使 Compose 覆盖层同步显隐
        if (mPortraitComponent != null) {
            mPortraitComponent.onVisibilityChanged(isVisible, anim);
        }
        // 隐藏原生控件（VodControlView、TitleView 等），避免覆盖自定义控制器
        hideNativeControls();
    }

    /**
     * 全屏时禁用控制器层的手势检测，让 LandscapeControlComponent 的 ComposeView 处理触摸
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mControlWrapper != null && mControlWrapper.isFullScreen()) {
            return false;
        }
        return super.onTouch(v, event);
    }

    /**
     * 对 VodControlView 中的 SeekBar 应用 MD3 风格
     */
    private void applyMd3SeekBarStyle(VodControlView vodControlView) {
        // 通过资源名称动态查找 SeekBar 和 ProgressBar
        int seekBarId = getResources().getIdentifier("seekBar", "id", "xyz.doikki.videocontroller");
        int bottomProgressId = getResources().getIdentifier("bottom_progress", "id", "xyz.doikki.videocontroller");

        if (seekBarId != 0) {
            SeekBar seekBar = vodControlView.findViewById(seekBarId);
            if (seekBar != null) {
                seekBar.setProgressDrawable(ContextCompat.getDrawable(getContext(), R.drawable.md3_seekbar_progress));
                seekBar.setThumb(ContextCompat.getDrawable(getContext(), R.drawable.md3_seekbar_thumb));
                seekBar.setThumbOffset(0);
                seekBar.setSplitTrack(false);
            }
        }

        if (bottomProgressId != 0) {
            ProgressBar bottomProgress = vodControlView.findViewById(bottomProgressId);
            if (bottomProgress != null) {
                bottomProgress
                        .setProgressDrawable(ContextCompat.getDrawable(getContext(), R.drawable.md3_seekbar_progress));
            }
        }
    }

}