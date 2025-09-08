package com.example.game2dfighting;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private GameView gameView;
    private JoystickView joystickView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ẩn thanh trạng thái và thanh điều hướng
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout layout = new FrameLayout(this);
        gameView = new GameView(this);

        // Tạo joystick
        joystickView = new JoystickView(this, (xPercent, yPercent) -> {
            gameView.setMovingUp(yPercent < -0.2f);
            gameView.setMovingDown(yPercent > 0.2f);
            gameView.setMovingLeft(xPercent < -0.2f);
            gameView.setMovingRight(xPercent > 0.2f);
        });

        FrameLayout.LayoutParams jsParams = new FrameLayout.LayoutParams(400, 400);
        jsParams.leftMargin = 50;
        jsParams.topMargin = getResources().getDisplayMetrics().heightPixels - 500;
        joystickView.setLayoutParams(jsParams);

        layout.addView(gameView);
        layout.addView(joystickView);

        setContentView(layout);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        gameView.handleKeyDown(keyCode);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        gameView.handleKeyUp(keyCode);
        return true;
    }
}