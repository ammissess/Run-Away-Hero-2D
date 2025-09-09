package com.example.game2dfighting.view;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import com.example.game2dfighting.R;

public class HomeActivity extends Activity {
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_CHARACTER = "character";

    private Spinner levelSpinner, characterSpinner;

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

        levelSpinner = findViewById(R.id.spinner_level);
        characterSpinner = findViewById(R.id.spinner_character);
        Button startBtn = findViewById(R.id.button_start);

        levelSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Easy", "Normal", "Hard"}));

        characterSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Warrior", "Mage", "Rogue"}));

        startBtn.setOnClickListener(v -> {
            Intent i = new Intent(HomeActivity.this, Level1Activity.class);
            i.putExtra(EXTRA_LEVEL, levelSpinner.getSelectedItem().toString());
            i.putExtra(EXTRA_CHARACTER, characterSpinner.getSelectedItem().toString());
            startActivity(i);
        });
    }
}
