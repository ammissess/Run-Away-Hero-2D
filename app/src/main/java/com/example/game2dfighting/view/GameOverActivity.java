package com.example.game2dfighting.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.example.game2dfighting.R;

public class GameOverActivity extends AppCompatActivity {
    private Button btnPlayAgain, btnQuit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over); // tạo layout riêng

        btnPlayAgain = findViewById(R.id.btn_play_again);
        btnQuit = findViewById(R.id.btn_quit);

        // Play Again -> quay lại Level1
        btnPlayAgain.setOnClickListener(v -> {
            Intent intent = new Intent(GameOverActivity.this, Level1Activity.class);
            startActivity(intent);
            finish();
        });

        // Quit -> về Home
        btnQuit.setOnClickListener(v -> {
            Intent intent = new Intent(GameOverActivity.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}
