package com.eis.emomusic;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public class ScanCountdownView extends View {

    private Paint ringPaint;
    private Paint textPaint;
    private Paint subTextPaint;
    private RectF ringBounds;
    private float progress = 0f;
    private int currentNumber = 5;
    private float numberScale = 1.0f;
    private String subText = "SCANNING...";

    public interface OnFinishListener {
        void onFinish();
    }

    public ScanCountdownView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(Color.parseColor("#00BCD4")); // accent_cyan
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(12f);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(120f);
        textPaint.setFakeBoldText(true);

        subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subTextPaint.setColor(Color.GRAY);
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTextSize(32f);

        ringBounds = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float padding = 20f;
        ringBounds.set(padding, padding, w - padding, h - padding);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        // Draw progress ring
        canvas.drawArc(ringBounds, -90f, 360f * progress, false, ringPaint);

        // Draw number with scale animation
        canvas.save();
        canvas.scale(numberScale, numberScale, centerX, centerY);
        canvas.drawText(String.valueOf(currentNumber), centerX, centerY + 40f, textPaint);
        canvas.restore();

        // Draw subtext
        canvas.drawText(subText, centerX, centerY + 100f, subTextPaint);
    }

    public void start(int seconds, final OnFinishListener listener) {
        this.currentNumber = seconds;
        this.progress = 0f;
        setVisibility(VISIBLE);

        ValueAnimator progressAnim = ValueAnimator.ofFloat(0f, 1f);
        progressAnim.setDuration(seconds * 1000L);
        progressAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        progressAnim.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });

        ValueAnimator numberAnim = ValueAnimator.ofInt(seconds, 0);
        numberAnim.setDuration(seconds * 1000L);
        numberAnim.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            if (val != currentNumber && val > 0) {
                currentNumber = val;
                animateNumber();
            }
        });
        
        progressAnim.start();
        numberAnim.start();

        postDelayed(() -> {
            if (listener != null) listener.onFinish();
        }, seconds * 1000L);
    }

    private void animateNumber() {
        ValueAnimator scale = ValueAnimator.ofFloat(0.5f, 1.2f, 1.0f);
        scale.setDuration(400);
        scale.setInterpolator(new OvershootInterpolator());
        scale.addUpdateListener(animation -> {
            numberScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        scale.start();
    }
}
