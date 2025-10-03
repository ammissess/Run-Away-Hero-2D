package com.example.game2dfighting.view;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.game2dfighting.R;
import com.example.game2dfighting.ui.JoystickView;
import com.example.game2dfighting.view.map.GameView;

public class Level3Activity extends AppCompatActivity {

    private GameView gameView;
    private JoystickView joystickView;

    private View pauseOverlay;
    private ImageButton btnPause;
    private Button btnResume, btnQuit, btnHome;

    private MediaPlayer bgMusic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen immersive
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // Dùng lại layout của Level1
        setContentView(R.layout.activity_level1);
        FrameLayout root = findViewById(R.id.level1_root);

        // --- GameView (đặt dưới cùng) ---
        gameView = new GameView(this);
        FrameLayout.LayoutParams gvParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        gameView.setLayoutParams(gvParams);
        root.addView(gameView, 0);

        // Giao tiếp nhạc nền từ GameView
        gameView.setAudioControl(enabled -> {
            if (bgMusic == null) return;
            if (enabled) {
                if (!gameView.isPaused() && !bgMusic.isPlaying()) {
                    bgMusic.start();
                    fadeIn(bgMusic, 300);
                }
            } else {
                if (bgMusic.isPlaying()) {
                    bgMusic.pause();
                }
            }
        });

        // Gắn tên level để lưu điểm vào cột Level3
        gameView.setLevelName("Level3"); // hoặc "Hard"

        // Khi player chết -> GameOver
        gameView.setGameEventListener(() -> runOnUiThread(() -> {
            stopAndRewindMusic();
            if (pauseOverlay != null) pauseOverlay.setVisibility(View.GONE);
            startActivity(new Intent(Level3Activity.this, GameOverActivity.class));
            finish();
        }));

        // --- Joystick ở trên GameView ---
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
        FrameLayout.LayoutParams jsParams = new FrameLayout.LayoutParams(400, 400);
        jsParams.leftMargin = 50;
        jsParams.topMargin = getResources().getDisplayMetrics().heightPixels - 500;
        joystickView.setLayoutParams(jsParams);
        root.addView(joystickView);

        // --- Tham chiếu overlay & nút từ layout ---
        pauseOverlay = findViewById(R.id.pause_overlay);
        btnPause = findViewById(R.id.btn_pause);
        btnResume = findViewById(R.id.btn_resume);
        btnQuit = findViewById(R.id.btn_quit);
        btnHome = findViewById(R.id.btn_home);

        pauseOverlay.setVisibility(View.GONE);

        // Pause
        btnPause.setOnClickListener(v -> {
            if (!gameView.isPaused()) {
                gameView.setPaused(true);
                pauseOverlay.setVisibility(View.VISIBLE);
                if (bgMusic != null && bgMusic.isPlaying()) bgMusic.pause();
            }
        });

        // Resume
        btnResume.setOnClickListener(v -> {
            pauseOverlay.setVisibility(View.GONE);
            gameView.setPaused(false);
            if (bgMusic != null && !bgMusic.isPlaying() && gameView.isMusicEnabled()) {
                bgMusic.start();
                fadeIn(bgMusic, 400);
            }
        });

        // Quit & Home (về Home)
        View.OnClickListener backHome = v -> {
            stopAndRewindMusic();
            startActivity(new Intent(Level3Activity.this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        };
        btnQuit.setOnClickListener(backHome);
        btnHome.setOnClickListener(backHome);

        // Back gesture
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!gameView.isPaused()) {
                    gameView.setPaused(true);
                    pauseOverlay.setVisibility(View.VISIBLE);
                    if (bgMusic != null && bgMusic.isPlaying()) bgMusic.pause();
                } else {
                    stopAndRewindMusic();
                    startActivity(new Intent(Level3Activity.this, HomeActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                }
            }
        });

        // Nhạc nền (tạm dùng chung track level1 để chắc chắn compile)
        bgMusic = MediaPlayer.create(this, R.raw.bg_game_level1 /* đổi sang bg_game_level3 nếu có */);
        bgMusic.setLooping(true);
        bgMusic.setVolume(0f, 0f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!gameView.isPaused() && bgMusic != null && !bgMusic.isPlaying() && gameView.isMusicEnabled()) {
            bgMusic.start();
            fadeIn(bgMusic, 600);
        }
    }

    @Override
    protected void onPause() {
        if (gameView != null && !gameView.isGameOver() && !gameView.isPaused()) {
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

    private void stopAndRewindMusic() {
        if (bgMusic != null && bgMusic.isPlaying()) bgMusic.pause();
        if (bgMusic != null) bgMusic.seekTo(0);
    }

    private void fadeIn(MediaPlayer mp, int durationMs) {
        if (mp == null) return;
        final int steps = 20;
        final float delta = 1.0f / steps;
        final int stepDelay = Math.max(10, durationMs / steps);
        mp.setVolume(0f, 0f);
        Handler h = new Handler();
        for (int i = 1; i <= steps; i++) {
            final float vol = delta * i;
            h.postDelayed(() -> {
                if (mp != null && mp.isPlaying()) mp.setVolume(vol, vol);
            }, (long) i * stepDelay);
        }
    }
}
