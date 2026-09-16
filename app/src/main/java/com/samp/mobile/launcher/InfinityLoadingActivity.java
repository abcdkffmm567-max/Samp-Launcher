package com.samp.mobile.launcher;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.samp.mobile.R;

public class InfinityLoadingActivity extends AppCompatActivity {
    private ValueAnimator progressAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        setContentView(R.layout.activity_infinity_loading);

        ImageView logo = findViewById(R.id.loadingLogo);
        TextView title = findViewById(R.id.loadingTitle);
        TextView status = findViewById(R.id.loadingStatus);
        ProgressBar progress = findViewById(R.id.loadingProgress);

        title.setAlpha(0f);
        logo.setAlpha(0f);
        ObjectAnimator titleFade = ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f);
        ObjectAnimator logoFade = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.82f, 1f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.82f, 1f);
        AnimatorSet entrance = new AnimatorSet();
        entrance.playTogether(titleFade, logoFade, logoScaleX, logoScaleY);
        entrance.setDuration(700);
        entrance.setInterpolator(new AccelerateDecelerateInterpolator());
        entrance.start();

        progressAnimator = ValueAnimator.ofInt(0, 100);
        progressAnimator.setDuration(3200);
        progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        progressAnimator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            progress.setProgress(value);
            if (value < 38) {
                status.setText(R.string.loading_preparing);
            } else if (value < 78) {
                status.setText(R.string.loading_checking);
            } else {
                status.setText(R.string.loading_starting);
            }
        });
        progressAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!isFinishing()) {
                    startActivity(new Intent(InfinityLoadingActivity.this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                }
            }
        });
        progressAnimator.start();
    }

    @Override
    protected void onDestroy() {
        if (progressAnimator != null) progressAnimator.cancel();
        super.onDestroy();
    }
}
