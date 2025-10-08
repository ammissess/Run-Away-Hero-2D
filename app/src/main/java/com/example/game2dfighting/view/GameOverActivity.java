package com.example.game2dfighting.view;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.game2dfighting.R;

import java.util.ArrayList;
import java.util.List;

public class GameOverActivity extends AppCompatActivity {

    private Button btnPlayAgain, btnQuit;

    // Cloud layers
    private ImageView cloudsFar1, cloudsFar2;
    private ImageView cloudsMid1, cloudsMid2;
    private ImageView cloudsNear1, cloudsNear2;

    // Keep animators to pause/resume
    private final List<ValueAnimator> runningAnimators = new ArrayList<>();

    // Music
    private MediaPlayer mediaPlayer;

    // >>> NEW: tên activity để restart
    private Class<?> restartActivityClass = Level1Activity.class; // fallback


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        // >>> LẤY THÊM CÁC EXTRAS VỀ CẤP/ĐỘ KHÓ
        Intent in = getIntent();
        final int replayDifficulty       = in.getIntExtra(LevelClearActivity.EXTRA_DIFFICULTY, 1);
        final int replayLevelAtEntry     = in.getIntExtra(LevelClearActivity.EXTRA_PLAYER_LEVEL_AT_ENTRY, 1);
        final int replayLevelCurrent     = in.getIntExtra(LevelClearActivity.EXTRA_PLAYER_LEVEL_CURRENT, replayLevelAtEntry);

        // Lấy tên Activity màn vừa thua (nếu có)
        String restartClassName = getIntent().getStringExtra("restart_activity");
        if (restartClassName != null && !restartClassName.isEmpty()) {
            try {
                restartActivityClass = Class.forName(restartClassName);
            } catch (ClassNotFoundException ignored) {
                restartActivityClass = Level1Activity.class;
            }
        }

        // Buttons
        btnPlayAgain = findViewById(R.id.btn_play_again);
        btnQuit = findViewById(R.id.btn_quit);

        btnPlayAgain.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
            }
            Intent intent = new Intent(GameOverActivity.this, restartActivityClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            // >>> FORWARD ĐẦY ĐỦ EXTRAS <<<
            intent.putExtra(LevelClearActivity.EXTRA_DIFFICULTY, replayDifficulty);
            intent.putExtra(LevelClearActivity.EXTRA_PLAYER_LEVEL_AT_ENTRY, replayLevelAtEntry);
            intent.putExtra(LevelClearActivity.EXTRA_PLAYER_LEVEL_CURRENT, replayLevelCurrent);

            startActivity(intent);
            finish();
        });

        Button btnRanking = findViewById(R.id.btn_rank);
        btnRanking.setOnClickListener(v -> {
            Intent i = new Intent(GameOverActivity.this, HighScoreActivity.class);
            i.putExtra(HighScoreActivity.EXTRA_FROM, "gameover");
            startActivity(i);
        });

        btnQuit.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
            }
            Intent intent = new Intent(GameOverActivity.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Clouds
        cloudsFar1  = findViewById(R.id.clouds_far_1);
        cloudsFar2  = findViewById(R.id.clouds_far_2);
        cloudsMid1  = findViewById(R.id.clouds_mid_1);
        cloudsMid2  = findViewById(R.id.clouds_mid_2);
        cloudsNear1 = findViewById(R.id.clouds_near_1);
        cloudsNear2 = findViewById(R.id.clouds_near_2);

        if (cloudsFar1 != null) {
            cloudsFar1.post(this::startCloudAnimationsIfReady);
        }

        // Nhạc nền
        mediaPlayer = MediaPlayer.create(this, R.raw.bg_game_over);
        mediaPlayer.setLooping(true);
        mediaPlayer.setVolume(0f, 0f); // fade-in trong onResume
    }

    private void startCloudAnimationsIfReady() {
        float farSpeedPxPerSec  = 20f;
        float midSpeedPxPerSec  = 100f;
        float nearSpeedPxPerSec = 50f;

        runningAnimators.clear();

        if (cloudsFar1 != null && cloudsFar2 != null) {
            runningAnimators.add(startParallax(cloudsFar1, cloudsFar2, farSpeedPxPerSec));
        }
        if (cloudsMid1 != null && cloudsMid2 != null) {
            runningAnimators.add(startParallax(cloudsMid1, cloudsMid2, midSpeedPxPerSec));
        }
        if (cloudsNear1 != null && cloudsNear2 != null) {
            runningAnimators.add(startParallax(cloudsNear1, cloudsNear2, nearSpeedPxPerSec));
        }
    }

    private ValueAnimator startParallax(ImageView i1, ImageView i2, float pxPerSec) {
        int w = i1.getWidth();
        if (w == 0 && i1.getDrawable() != null) {
            w = i1.getDrawable().getIntrinsicWidth();
        }
        if (w <= 0) {
            i1.post(() -> startParallax(i1, i2, pxPerSec));
            return null;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setInterpolator(new LinearInterpolator());
        long durationMs = (long) Math.max((w / pxPerSec) * 1000L, 1000L);
        animator.setDuration(durationMs);
        animator.setRepeatCount(ValueAnimator.INFINITE);

        final int width = w;

        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float dx = (t * width) % width;
            i1.setTranslationX(-dx);
            i2.setTranslationX(-dx + width);
        });

        animator.start();
        return animator;
    }

    @Override
    protected void onResume() {
        super.onResume();

        for (ValueAnimator va : runningAnimators) {
            if (va != null && !va.isRunning()) va.start();
        }

        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            fadeIn(mediaPlayer, 600);
        }
    }

    @Override
    protected void onPause() {
        for (ValueAnimator va : runningAnimators) {
            if (va != null && va.isRunning()) va.pause();
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (ValueAnimator va : runningAnimators) {
            if (va != null) va.cancel();
        }
        runningAnimators.clear();

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void fadeIn(MediaPlayer mp, int durationMs) {
        final int steps = 20;
        final float delta = 1.0f / steps;
        final int stepDelay = Math.max(10, durationMs / steps);
        mp.setVolume(0f, 0f);
        final android.os.Handler h = new android.os.Handler();
        for (int i = 1; i <= steps; i++) {
            final float vol = delta * i;
            h.postDelayed(() -> {
                if (mp.isPlaying()) mp.setVolume(vol, vol);
            }, (long) i * stepDelay);
        }
    }
}
