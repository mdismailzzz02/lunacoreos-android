package com.lunacoreos.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

public class SwipeToDismissLayout extends FrameLayout {

    private float startY;
    private float startX;
    private boolean isSwiping;
    private int touchSlop;
    private Activity activity;
    private View backgroundView;

    public SwipeToDismissLayout(Context context) {
        super(context);
        init(context);
    }

    public SwipeToDismissLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        if (context instanceof Activity) {
            activity = (Activity) context;
        }
    }

    public void setBackgroundView(View view) {
        this.backgroundView = view;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startY = ev.getRawY();
                startX = ev.getRawX();
                isSwiping = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = ev.getRawY() - startY;
                float dx = ev.getRawX() - startX;
                // Only intercept if dragging vertically more than horizontally
                if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                    isSwiping = true;
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isSwiping) return super.onTouchEvent(event);

        float dy = event.getRawY() - startY;

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                setTranslationY(dy);
                
                // Calculate scale (shrinks slightly as you drag)
                float fraction = Math.min(1f, Math.abs(dy) / getHeight());
                float scale = 1f - (0.2f * fraction);
                setScaleX(scale);
                setScaleY(scale);
                
                // Fade background
                if (backgroundView != null) {
                    float alpha = Math.max(0f, 1f - fraction * 1.5f);
                    backgroundView.setBackgroundColor(Color.argb((int)(alpha * 255), 0, 0, 0));
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isSwiping = false;
                if (Math.abs(dy) > getHeight() / 4f) {
                    // Dragged far enough, finish activity
                    if (activity != null) {
                        activity.finish();
                        activity.overridePendingTransition(0, android.R.anim.fade_out);
                    }
                } else {
                    // Spring back
                    animateBack();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void animateBack() {
        animate()
            .translationY(0)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator())
            .start();
            
        if (backgroundView != null) {
            ValueAnimator colorAnim = ValueAnimator.ofInt(
                Color.alpha(backgroundView.getSolidColor()), 255
            );
            colorAnim.setDuration(200);
            colorAnim.addUpdateListener(anim -> {
                int a = (int) anim.getAnimatedValue();
                backgroundView.setBackgroundColor(Color.argb(a, 0, 0, 0));
            });
            colorAnim.start();
        }
    }
}
