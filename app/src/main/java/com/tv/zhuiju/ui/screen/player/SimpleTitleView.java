package com.tv.zhuiju.ui.screen.player;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.doikki.videoplayer.controller.ControlWrapper;
import xyz.doikki.videoplayer.controller.IControlComponent;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * 简化的标题栏，竖屏和横屏都显示
 */
public class SimpleTitleView extends FrameLayout implements IControlComponent {

    private ControlWrapper mControlWrapper;
    private final TextView mTitle;
    private OnBackClickListener mOnBackClickListener;

    public SimpleTitleView(@NonNull Context context) {
        this(context, null);
    }

    public SimpleTitleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SimpleTitleView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setVisibility(GONE);
        LayoutInflater.from(context).inflate(
                xyz.doikki.videocontroller.R.layout.dkplayer_layout_title_view, this, true);
        mTitle = findViewById(xyz.doikki.videocontroller.R.id.title);
        ImageView back = findViewById(xyz.doikki.videocontroller.R.id.back);
        back.setOnClickListener(v -> {
            Activity activity = PlayerUtils.scanForActivity(getContext());
            if (activity == null)
                return;
            if (mControlWrapper != null && mControlWrapper.isFullScreen()) {
                // 全屏时先恢复竖屏方向，再退出全屏
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                mControlWrapper.stopFullScreen();
            } else if (mOnBackClickListener != null) {
                // 竖屏时回调给外部处理（如退出页面）
                mOnBackClickListener.onBackClick();
            }
        });
    }

    public void setTitle(String title) {
        mTitle.setText(title);
    }

    public void setOnBackClickListener(OnBackClickListener listener) {
        mOnBackClickListener = listener;
    }

    public interface OnBackClickListener {
        void onBackClick();
    }

    @Override
    public void attach(@NonNull ControlWrapper controlWrapper) {
        mControlWrapper = controlWrapper;
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onVisibilityChanged(boolean isVisible, Animation anim) {
        if (isVisible) {
            if (getVisibility() == GONE) {
                setVisibility(VISIBLE);
                if (anim != null)
                    startAnimation(anim);
            }
        } else {
            if (getVisibility() == VISIBLE) {
                setVisibility(GONE);
                if (anim != null)
                    startAnimation(anim);
            }
        }
    }

    @Override
    public void onPlayStateChanged(int playState) {
        switch (playState) {
            case VideoView.STATE_IDLE:
            case VideoView.STATE_START_ABORT:
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_PREPARED:
            case VideoView.STATE_ERROR:
            case VideoView.STATE_PLAYBACK_COMPLETED:
                setVisibility(GONE);
                break;
        }
    }

    @Override
    public void onPlayerStateChanged(int playerState) {
        if (mControlWrapper != null && mControlWrapper.isShowing()) {
            setVisibility(VISIBLE);
        }
    }

    @Override
    public void setProgress(int duration, int position) {
    }

    @Override
    public void onLockStateChanged(boolean isLocked) {
        if (isLocked) {
            setVisibility(GONE);
        } else if (mControlWrapper != null && mControlWrapper.isShowing()) {
            setVisibility(VISIBLE);
        }
    }
}
