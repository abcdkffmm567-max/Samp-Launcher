package com.samp.mobile.launcher.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Lightweight launcher-only snow effect; it does not run inside the game. */
public class SnowfallView extends View {
    private static final int FLAKE_COUNT = 42;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Flake[] flakes = new Flake[FLAKE_COUNT];
    private long previousFrame;

    public SnowfallView(Context context) { super(context); init(); }
    public SnowfallView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public SnowfallView(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style); init();
    }

    private void init() {
        setWillNotDraw(false);
        paint.setColor(Color.WHITE);
        for (int i = 0; i < flakes.length; i++) flakes[i] = new Flake();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        for (Flake flake : flakes) reset(flake, width, height, true);
        previousFrame = System.currentTimeMillis();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();
        float delta = Math.min(0.05f, Math.max(0.001f, (now - previousFrame) / 1000f));
        previousFrame = now;

        for (Flake flake : flakes) {
            flake.y += flake.speed * delta;
            flake.x += flake.drift * delta;
            if (flake.y > getHeight() + flake.radius || flake.x < -20 || flake.x > getWidth() + 20) {
                reset(flake, getWidth(), getHeight(), false);
            }
            paint.setAlpha(flake.alpha);
            canvas.drawCircle(flake.x, flake.y, flake.radius, paint);
        }
        postInvalidateOnAnimation();
    }

    private void reset(Flake flake, int width, int height, boolean randomY) {
        flake.x = random.nextFloat() * Math.max(1, width);
        flake.y = randomY ? random.nextFloat() * Math.max(1, height) : -10f;
        flake.radius = 1.5f + random.nextFloat() * 3.5f;
        flake.speed = 28f + random.nextFloat() * 58f;
        flake.drift = -12f + random.nextFloat() * 24f;
        flake.alpha = 90 + random.nextInt(150);
    }

    private static final class Flake {
        float x, y, radius, speed, drift;
        int alpha;
    }
}
