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
    public static final String EXTRA_CHARACTER = "character";

    private Spinner levelSpinner, characterSpinner;

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

        // Nhạc nền: KHÔNG start ở đây để tránh “rè” lúc mới vào
        mediaPlayer = MediaPlayer.create(this, R.raw.bg_game_home);
        mediaPlayer.setLooping(true);
        mediaPlayer.setVolume(0f, 0f); // sẽ fade-in trong onResume()

        // UI
        levelSpinner = findViewById(R.id.spinner_level);
        characterSpinner = findViewById(R.id.spinner_character);
        Button startBtn = findViewById(R.id.button_start);

        levelSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Easy", "Normal", "Hard"}));

        characterSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Warrior", "Mage", "Rogue"}));

        // Khi bấm Start: dừng nhạc để tránh trộn âm ở màn chơi
        startBtn.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
            }
            Intent i = new Intent(HomeActivity.this, Level1Activity.class);
            i.putExtra(EXTRA_LEVEL, levelSpinner.getSelectedItem().toString());
            i.putExtra(EXTRA_CHARACTER, characterSpinner.getSelectedItem().toString());
            startActivity(i);
            // finish(); // nếu không muốn quay lại Home khi back
        });

        // Tạo 3 band: FAR, MID, NEAR (gần nhanh nhất)
        addBand(R.id.clouds_far_1,  R.id.clouds_far_2,  dp(20));  // xa: chậm
        addBand(R.id.clouds_mid_1,  R.id.clouds_mid_2,  dp(60));  // vừa
        addBand(R.id.clouds_near_1, R.id.clouds_near_2, dp(90));  // gần: nhanh

        // Đợi layout xong -> đặt vị trí b = width, a = 0 cho từng band
        for (Band band : bands) {
            band.a.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            band.a.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            float w = band.a.getWidth();
                            band.a.setX(0f);
                            band.b.setX(w);
                            band.initialized = true;
                            startClouds(); // lần đầu vào -> tự chạy
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
        // chỉ start khi có ít nhất 1 band init xong
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

    private void stopClouds() {
        running = false;
    }

    private float dp(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override protected void onResume() {
        super.onResume();
        startClouds();

        // Start + fade-in để tránh nhiễu lúc mới phát
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            fadeIn(mediaPlayer, 600); // 300–800ms tuỳ bạn chỉnh
        }
    }

    @Override
    protected void onPause() {
        // Dừng mây + dừng nhạc trước khi vào nền
        stopClouds();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        // Gỡ loop animation
        for (Band b : bands) if (b.a != null) b.a.removeCallbacks(cloudLoop);
        loopPosted = false;

        // Giải phóng MediaPlayer
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    // Fade-in volume đơn giản, không cần thư viện
    private void fadeIn(MediaPlayer mp, int durationMs) {
        final int steps = 20; // tăng steps để mượt hơn
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
