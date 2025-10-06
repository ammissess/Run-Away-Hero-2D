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

public class Level1Activity extends AppCompatActivity {

    private GameView gameView;
    private JoystickView joystickView;

    private View pauseOverlay;
    private ImageButton btnPause;
    private Button btnResume, btnQuit, btnHome;
    private MediaPlayer bgMusic;

    // NEW: giữ thông tin để chuyển màn
    private int difficulty = 1;            // lấy từ Home
    private int playerLevelCurrent = 1;    // cấp hiện tại của nhân vật
    private int playerLevelAtEntry = 1;    // snapshot khi vào level

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen immersive
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(R.layout.activity_level1);
        FrameLayout root = findViewById(R.id.level1_root);

        // NEW: nhận extras ngay sau setContentView
        Intent in = getIntent();
        difficulty = in.getIntExtra(LevelClearActivity.EXTRA_DIFFICULTY, 1);
        playerLevelCurrent = in.getIntExtra(LevelClearActivity.EXTRA_PLAYER_LEVEL_CURRENT, 1);
        playerLevelAtEntry = in.getIntExtra(LevelClearActivity.EXTRA_PLAYER_LEVEL_AT_ENTRY, playerLevelCurrent);

        // --- GameView (thêm VÀO index 0 để ở dưới cùng) ---
        gameView = new GameView(this);
        gameView.setIslandResId(R.drawable.bg_map_island);
        gameView.setStatMultipliersForLevel(1);
        FrameLayout.LayoutParams gvParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        gameView.setLayoutParams(gvParams);
        gameView.setStartingPlayerLevel(playerLevelCurrent);

        // QUAN TRỌNG: Add vào index 0 để các view từ XML vẫn ở trên
        root.addView(gameView, 0);

        // Sau root.addView(gameView, 0);
        gameView.setAudioControl(enabled -> {
            if (bgMusic == null) return;

            if (enabled) {
                // Chỉ phát khi game không pause và user bật music
                if (!gameView.isPaused() && !bgMusic.isPlaying()) {
                    bgMusic.start();
                    fadeIn(bgMusic, 300);
                }
            } else {
                // Tắt ngay khi user tắt music
                if (bgMusic.isPlaying()) {
                    bgMusic.pause();
                }
                // Có thể tua về 0 nếu muốn:
                // bgMusic.seekTo(0);
            }
        });

        // Gắn tên level cho lưu điểm (Level1/Easy)
        gameView.setLevelName("Level1"); // hoặc "Easy"

        // NEW: đăng ký onWin để mở LevelClearActivity
        gameView.setOnWinListener(() -> runOnUiThread(() -> {
            stopAndRewindMusic();
            if (pauseOverlay != null) pauseOverlay.setVisibility(View.GONE);

            // >>> LẤY CẤP HIỆN TẠI TỪ GAMEVIEW (sau khi đã cộng EXP/level up)
            playerLevelCurrent = gameView.getCurrentPlayerLevel();

            Intent it = new Intent(Level1Activity.this, LevelClearActivity.class);
            it.putExtra(LevelClearActivity.EXTRA_FROM_LEVEL, 1);
            it.putExtra(LevelClearActivity.EXTRA_DIFFICULTY, difficulty);
            it.putExtra(LevelClearActivity.EXTRA_PLAYER_LEVEL_AT_ENTRY, playerLevelAtEntry);
            it.putExtra(LevelClearActivity.EXTRA_PLAYER_LEVEL_CURRENT, playerLevelCurrent);
            it.putExtra(LevelClearActivity.EXTRA_IS_LAST_LEVEL, false);
            // tránh back về nhầm
            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(it);
            finish();
        }));

        // Khi player chết -> GameOver
    // Trong Level1Activity, sửa listener onGameOver (thêm hide overlay trước finish)
        gameView.setGameEventListener(() -> runOnUiThread(() -> {
            stopAndRewindMusic();
            if (pauseOverlay != null) {
                pauseOverlay.setVisibility(View.GONE);  // Ẩn overlay pause trước khi chuyển màn
            }
            // chỗ gọi GameOverActivity
            Intent i = new Intent(Level1Activity.this, GameOverActivity.class);
            i.putExtra("restart_activity", Level1Activity.class.getName());
            startActivity(i);
            finish();
        }));

        // --- Joystick (thêm SAU GameView để ở trên) ---
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

        // --- Lấy references từ XML (đã có sẵn trong layout) ---
        pauseOverlay = findViewById(R.id.pause_overlay);
        btnPause = findViewById(R.id.btn_pause);
        btnResume = findViewById(R.id.btn_resume);
        btnQuit = findViewById(R.id.btn_quit);
        btnHome = findViewById(R.id.btn_home);

        // Đảm bảo overlay ẩn ban đầu
        pauseOverlay.setVisibility(View.GONE);

        // --- Setup listeners ---
        btnPause.setOnClickListener(v -> {
            if (!gameView.isPaused()) {
                gameView.setPaused(true);
                pauseOverlay.setVisibility(View.VISIBLE);
                if (bgMusic != null && bgMusic.isPlaying()) bgMusic.pause();
            }
        });

        btnResume.setOnClickListener(v -> {
            pauseOverlay.setVisibility(View.GONE);
            gameView.setPaused(false);
            if (bgMusic != null && !bgMusic.isPlaying() && gameView.isMusicEnabled()) {
                bgMusic.start();
                fadeIn(bgMusic, 400);
            }
        });

        btnQuit.setOnClickListener(v -> {
            stopAndRewindMusic();
            startActivity(new Intent(Level1Activity.this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        btnHome.setOnClickListener(v -> {
            stopAndRewindMusic();
            startActivity(new Intent(Level1Activity.this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        // --- Back gesture ---
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!gameView.isPaused()) {
                    gameView.setPaused(true);
                    pauseOverlay.setVisibility(View.VISIBLE);
                    if (bgMusic != null && bgMusic.isPlaying()) bgMusic.pause();
                } else {
                    stopAndRewindMusic();
                    startActivity(new Intent(Level1Activity.this, HomeActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                }
            }
        });

        // --- Init music ---
        bgMusic = MediaPlayer.create(this, R.raw.bg_game_level1);
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

    // Trong Level1Activity.onPause(), thêm check để tránh show overlay khi game over
    @Override
    protected void onPause() {
        if (gameView != null && !gameView.isGameOver() && !gameView.isPaused()) {  // Thêm !gameView.isGameOver()
            gameView.setPaused(true);
            if (pauseOverlay != null) {
                pauseOverlay.setVisibility(View.VISIBLE);
            }
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