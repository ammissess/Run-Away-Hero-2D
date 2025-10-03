package com.example.game2dfighting.view;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.media.MediaPlayer;

import com.example.game2dfighting.R;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends Activity {
    public static final String EXTRA_LEVEL = "level";
    // public static final String EXTRA_CHARACTER = "character"; // <-- BỎ nếu không dùng nữa

    private Spinner levelSpinner; // <-- chỉ còn level

    // ====== CLOUD BANDS (parallax) ======
    private static class Band {
        ImageView a, b;
        float speedPxPerSec;
        boolean initialized = false;
    }
    private final List<Band> bands = new ArrayList<>();
    private boolean running = false, loopPosted = false;
    private long lastNs = 0L;

    private MediaPlayer mediaPlayer;

    private final Runnable cloudLoop = new Runnable() {
        @Override public void run() {
            if (!running) { loopPosted = false; return; }

            long now = System.nanoTime();
            float dt = (now - lastNs) / 1_000_000_000f;
            lastNs = now;

            for (Band band : bands) {
                if (!band.initialized) continue;

                float dx = band.speedPxPerSec * dt; // trái -> phải
                band.a.setX(band.a.getX() + dx);
                band.b.setX(band.b.getX() + dx);

                float w = band.a.getWidth();
                if (band.a.getX() >= w) band.a.setX(band.b.getX() - w);
                if (band.b.getX() >= w) band.b.setX(band.a.getX() - w);
            }
            // ~60fps
            bands.get(0).a.postOnAnimation(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fullscreen + immersive
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_home);

        // Nhạc nền
        mediaPlayer = MediaPlayer.create(this, R.raw.bg_game_home);
        mediaPlayer.setLooping(true);
        mediaPlayer.setVolume(0f, 0f);

        // UI
        levelSpinner = findViewById(R.id.spinner_level);
        Button startBtn = findViewById(R.id.button_start);

        levelSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Easy", "Normal", "Hard"}));

        // KHÔNG còn characterSpinner / character
        startBtn.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
            }

            String level = levelSpinner.getSelectedItem().toString();

            // Chọn Activity theo độ khó
            Class<?> activityClass;
            if ("Easy".equalsIgnoreCase(level)) {
                activityClass = Level1Activity.class;
            } else if ("Normal".equalsIgnoreCase(level)) {
                activityClass = Level2Activity.class;
            } else if ("Hard".equalsIgnoreCase(level)) {
                activityClass = Level3Activity.class;
            } else {
                activityClass = Level1Activity.class;
            }

            Intent i = new Intent(HomeActivity.this, activityClass);
            i.putExtra(EXTRA_LEVEL, level);
            // i.putExtra(EXTRA_CHARACTER, character); // <-- BỎ
            startActivity(i);
        });

        Button btnRanking = findViewById(R.id.button_ranking);
        btnRanking.setOnClickListener(v -> {
            Intent i = new Intent(HomeActivity.this, HighScoreActivity.class);
            startActivity(i);
        });

        // Parallax clouds
        addBand(R.id.clouds_far_1,  R.id.clouds_far_2,  dp(20));
        addBand(R.id.clouds_mid_1,  R.id.clouds_mid_2,  dp(60));
        addBand(R.id.clouds_near_1, R.id.clouds_near_2, dp(90));

        for (Band band : bands) {
            band.a.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            band.a.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            float w = band.a.getWidth();
                            band.a.setX(0f);
                            band.b.setX(w);
                            band.initialized = true;
                            startClouds();
                        }
                    });
        }
    }

    private void addBand(int idA, int idB, float speed) {
        Band band = new Band();
        band.a = findViewById(idA);
        band.b = findViewById(idB);
        band.speedPxPerSec = speed;
        bands.add(band);
    }

    private void startClouds() {
        boolean anyReady = false;
        for (Band b : bands) if (b.initialized) { anyReady = true; break; }
        if (!anyReady) return;

        running = true;
        if (!loopPosted) {
            lastNs = System.nanoTime();
            bands.get(0).a.postOnAnimation(cloudLoop);
            loopPosted = true;
        }
    }

    private void stopClouds() { running = false; }

    private float dp(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override protected void onResume() {
        super.onResume();
        startClouds();

        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            fadeIn(mediaPlayer, 600);
        }
    }

    @Override protected void onPause() {
        stopClouds();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        for (Band b : bands) if (b.a != null) b.a.removeCallbacks(cloudLoop);
        loopPosted = false;

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    private void fadeIn(MediaPlayer mp, int durationMs) {
        final int steps = 20;
        final float delta = 1.0f / steps;
        final int stepDelay = Math.max(10, durationMs / steps);

        mp.setVolume(0f, 0f);
        final android.os.Handler h = new android.os.Handler();
        for (int i = 1; i <= steps; i++) {
            final float vol = delta * i; // 0 -> 1
            h.postDelayed(() -> {
                if (mp != null && mp.isPlaying()) mp.setVolume(vol, vol);
            }, (long) i * stepDelay);
        }
    }
}
