package com.example.game2dfighting.view;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.game2dfighting.R;
import com.example.game2dfighting.ui.JoystickView;
import com.example.game2dfighting.view.map.GameView;

public class Level1Activity extends AppCompatActivity {
    private GameView gameView;
    private JoystickView joystickView;

    private View pauseOverlay;
    private ImageButton btnPause;
    private Button btnResume, btnQuit;

    // ===== Music =====
    private MediaPlayer bgMusic;

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

        setContentView(R.layout.activity_level1);

        FrameLayout root = findViewById(R.id.level1_root);

        // --- GameView ---
        gameView = new GameView(this);

        // Khi player chết trong GameView -> sang GameOver
        gameView.setGameEventListener(() -> runOnUiThread(() -> {
            // dừng nhạc trước khi chuyển màn
            stopAndRewindMusic();
            Intent i = new Intent(Level1Activity.this, GameOverActivity.class);
            startActivity(i);
            finish();
        }));

        // ===== KẾT NỐI TOGGLE ÂM NHẠC TỪ GAMEVIEW → ACTIVITY =====
        gameView.setAudioControl(enabled -> {
            if (bgMusic == null) return;
            if (enabled) {
                if (!bgMusic.isPlaying() && !gameView.isPaused()) {
                    bgMusic.start();
                }
            } else {
                if (bgMusic.isPlaying()) bgMusic.pause();
            }
        });

        // --- Joystick ---
        joystickView = new JoystickView(this, (x, y) -> {
            if (!gameView.isPaused()) {
                gameView.setMovingUp(y < -0.2f);
                gameView.setMovingDown(y > 0.2f);
                gameView.setMovingLeft(x < -0.2f);
                gameView.setMovingRight(x > 0.2f);
            } else {
                gameView.setMovingUp(false);
                gameView.setMovingDown(false);
                gameView.setMovingLeft(false);
                gameView.setMovingRight(false);
            }
        });

        // Đặt joystick góc dưới trái
        FrameLayout.LayoutParams jsParams = new FrameLayout.LayoutParams(400, 400);
        jsParams.leftMargin = 50;
        jsParams.topMargin = getResources().getDisplayMetrics().heightPixels - 500;
        joystickView.setLayoutParams(jsParams);

        // Thêm view game + joystick vào layout (GameView nằm dưới cùng)
        root.addView(gameView);
        root.addView(joystickView);

        // --- Overlay Pause ---
        pauseOverlay = findViewById(R.id.pause_overlay);
        btnPause = findViewById(R.id.btn_pause);
        btnResume = findViewById(R.id.btn_resume);
        btnQuit = findViewById(R.id.btn_quit);

        // Pause -> hiện overlay (Resume/Quit)
        btnPause.setOnClickListener(v -> {
            if (!gameView.isPaused()) {
                gameView.setPaused(true);
                pauseOverlay.setVisibility(View.VISIBLE);
                if (bgMusic != null && bgMusic.isPlaying()) bgMusic.pause();
            }
        });

        // Resume -> ẩn overlay
        btnResume.setOnClickListener(v -> {
            pauseOverlay.setVisibility(View.GONE);
            gameView.setPaused(false);
            // chỉ phát lại nếu người chơi chưa tắt Music
            if (bgMusic != null && gameView.isMusicEnabled() && !bgMusic.isPlaying()) {
                bgMusic.start();
                fadeIn(bgMusic, 400);
            }
        });

        // Quit -> về Home
        btnQuit.setOnClickListener(v -> {
            stopAndRewindMusic();
            Intent i = new Intent(Level1Activity.this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        // Back gesture: đang chơi -> pause; đang pause -> thoát về Home
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!gameView.isPaused()) {
                    gameView.setPaused(true);
                    pauseOverlay.setVisibility(View.VISIBLE);
                    if (bgMusic != null && bgMusic.isPlaying()) bgMusic.pause();
                } else {
                    stopAndRewindMusic();
                    Intent i = new Intent(Level1Activity.this, HomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(i);
                    finish();
                }
            }
        });

        // ===== Init music but DON'T start here =====
        bgMusic = MediaPlayer.create(this, R.raw.bg_game_level1);
        bgMusic.setLooping(true);
        bgMusic.setVolume(0f, 0f); // sẽ fade-in trong onResume()
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Nếu không paus e và người chơi bật Music -> phát
        if (!gameView.isPaused()) {
            if (bgMusic != null && gameView.isMusicEnabled() && !bgMusic.isPlaying()) {
                bgMusic.start();
                fadeIn(bgMusic, 600);
            }
        }
    }

    @Override
    protected void onPause() {
        // Đi background thì tự pause + mở overlay
        if (!gameView.isPaused()) {
            gameView.setPaused(true);
            if (pauseOverlay != null) pauseOverlay.setVisibility(View.VISIBLE);
        }
        if (bgMusic != null && bgMusic.isPlaying()) {
            bgMusic.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (bgMusic != null) {
            bgMusic.release();
            bgMusic = null;
        }
        super.onDestroy();
    }

    /* ================== Helpers ================== */

    private void stopAndRewindMusic() {
        if (bgMusic != null && bgMusic.isPlaying()) {
            bgMusic.pause();
        }
        if (bgMusic != null) bgMusic.seekTo(0);
    }

    // Fade-in volume đơn giản
    private void fadeIn(MediaPlayer mp, int durationMs) {
        if (mp == null) return;
        final int steps = 20;
        final float delta = 1.0f / steps;
        final int stepDelay = Math.max(10, durationMs / steps);

        mp.setVolume(0f, 0f);
        Handler h = new Handler();
        for (int i = 1; i <= steps; i++) {
            final float vol = delta * i; // 0 -> 1
            h.postDelayed(() -> {
                if (mp != null && mp.isPlaying()) mp.setVolume(vol, vol);
            }, (long) i * stepDelay);
        }
    }

    @SuppressWarnings("unused")
    private void fadeOut(MediaPlayer mp, int durationMs) {
        if (mp == null) return;
        final int steps = 20;
        final float delta = 1.0f / steps;
        final int stepDelay = Math.max(10, durationMs / steps);

        Handler h = new Handler();
        for (int i = 1; i <= steps; i++) {
            final float vol = 1.0f - delta * i; // 1 -> 0
            h.postDelayed(() -> {
                if (mp != null) mp.setVolume(Math.max(0f, vol), Math.max(0f, vol));
            }, (long) i * stepDelay);
        }
        h.postDelayed(() -> {
            if (mp != null && mp.isPlaying()) mp.pause();
            if (mp != null) mp.setVolume(1f, 1f);
        }, (long) (steps + 1) * stepDelay);
    }
}
