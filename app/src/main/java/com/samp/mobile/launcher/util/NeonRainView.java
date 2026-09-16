package com.samp.mobile.launcher.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class NeonRainView extends View {
    private static final int DROP_COUNT = 48;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float[] x = new float[DROP_COUNT];
    private final float[] y = new float[DROP_COUNT];
    private final float[] speed = new float[DROP_COUNT];
    private final float[] length = new float[DROP_COUNT];
    private boolean initialized;

    public NeonRainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.2f);
        paint.setColor(Color.rgb(49, 218, 255));
    }

    private void initializeDrops(int width, int height) {
        for (int i = 0; i < DROP_COUNT; i++) {
            x[i] = random.nextFloat() * width;
            y[i] = random.nextFloat() * height;
            speed[i] = 5f + random.nextFloat() * 8f;
            length[i] = 10f + random.nextFloat() * 26f;
        }
        initialized = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!initialized && getWidth() > 0 && getHeight() > 0) {
            initializeDrops(getWidth(), getHeight());
        }
        for (int i = 0; i < DROP_COUNT; i++) {
            paint.setAlpha(55 + (i % 4) * 35);
            canvas.drawLine(x[i], y[i], x[i] - 2f, y[i] + length[i], paint);
            y[i] += speed[i];
            if (y[i] > getHeight()) {
                y[i] = -length[i];
                x[i] = random.nextFloat() * getWidth();
            }
        }
        postInvalidateOnAnimation();
    }
}
