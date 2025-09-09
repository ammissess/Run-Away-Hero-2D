package com.example.game2dfighting.view;

// Level1Activity.java
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Button;
import com.example.game2dfighting.R;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.game2dfighting.ui.JoystickView;
import com.example.game2dfighting.view.map.GameView;

public class Level1Activity extends AppCompatActivity {
    private GameView gameView;
    private JoystickView joystickView;

    private View pauseOverlay;
    private ImageButton btnPause;
    private Button btnResume, btnQuit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_level1);

        FrameLayout root = findViewById(R.id.level1_root);

        gameView = new GameView(this);
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

        // Thêm view game + joystick
        root.addView(gameView);
        root.addView(joystickView);

        // Lấy UI overlay
        pauseOverlay = findViewById(R.id.pause_overlay);
        btnPause = findViewById(R.id.btn_pause);
        btnResume = findViewById(R.id.btn_resume);
        btnQuit = findViewById(R.id.btn_quit);

        // Pause -> hiện overlay (Resume/Quit)
        btnPause.setOnClickListener(v -> {
            if (!gameView.isPaused()) {
                gameView.setPaused(true);
                pauseOverlay.setVisibility(View.VISIBLE);
            }
        });

        // Resume -> ẩn overlay
        btnResume.setOnClickListener(v -> {
            pauseOverlay.setVisibility(View.GONE);
            gameView.setPaused(false);
        });

        // Quit -> về Home (finish Level1)
        btnQuit.setOnClickListener(v -> finish());

        // Back gesture: nếu đang chơi -> mở overlay; nếu đang pause -> thoát
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!gameView.isPaused()) {
                    gameView.setPaused(true);
                    pauseOverlay.setVisibility(View.VISIBLE);
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Đi background thì pause + mở overlay
        if (!gameView.isPaused()) {
            gameView.setPaused(true);
            if (pauseOverlay != null) pauseOverlay.setVisibility(View.VISIBLE);
        }
    }
}
