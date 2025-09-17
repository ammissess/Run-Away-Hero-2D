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

        // UI cũ
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

        // Tạo 3 band: FAR, MID, NEAR (tốc độ tăng dần)
        addBand(R.id.clouds_far_1, R.id.clouds_far_2, dp(20));  // xa: chậm
        addBand(R.id.clouds_mid_1, R.id.clouds_mid_2, dp(80));  // vừa
        addBand(R.id.clouds_near_1, R.id.clouds_near_2, dp(50)); // gần: nhanh

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
    }

    @Override protected void onPause() {
        stopClouds();
        super.onPause();
    }

    @Override protected void onDestroy() {
        // cleanup
        for (Band b : bands) if (b.a != null) b.a.removeCallbacks(cloudLoop);
        loopPosted = false;
        super.onDestroy();
    }
}
