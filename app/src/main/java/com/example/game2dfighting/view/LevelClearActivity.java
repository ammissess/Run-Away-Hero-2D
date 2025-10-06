package com.example.game2dfighting.view;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.game2dfighting.R;

import java.util.ArrayList;
import java.util.List;

public class LevelClearActivity extends AppCompatActivity {

    public static final String EXTRA_FROM_LEVEL = "from_level";          // 1/2/3
    public static final String EXTRA_DIFFICULTY = "difficulty";           // cấp độ chọn ở Home
    public static final String EXTRA_PLAYER_LEVEL_AT_ENTRY = "player_level_at_entry";
    public static final String EXTRA_PLAYER_LEVEL_CURRENT = "player_level_current";
    public static final String EXTRA_IS_LAST_LEVEL = "is_last_level";     // true nếu đang ở level 3

    private int fromLevel;
    private int difficulty;
    private int entryPlayerLevel;
    private int currentPlayerLevel;
    private boolean isLastLevel;

    // ====== PARALLAX CLOUDS (clone từ Home) ======
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

    // ====== MUSIC ======
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_level_clear);

        Intent i = getIntent();
        fromLevel = i.getIntExtra(EXTRA_FROM_LEVEL, 1);
        difficulty = i.getIntExtra(EXTRA_DIFFICULTY, 1);
        entryPlayerLevel = i.getIntExtra(EXTRA_PLAYER_LEVEL_AT_ENTRY, 1);
        currentPlayerLevel = i.getIntExtra(EXTRA_PLAYER_LEVEL_CURRENT, entryPlayerLevel);
        isLastLevel = i.getBooleanExtra(EXTRA_IS_LAST_LEVEL, false);

        TextView tv = findViewById(R.id.tvTitle);
        Button btnNext = findViewById(R.id.btnNextLevel);
        Button btnReplay = findViewById(R.id.btnReplay);
        Button btnRanking = findViewById(R.id.btnRanking);

        // ====== Nhạc nền (có thể dùng riêng file bg_level_clear.mp3 nếu muốn) ======
        mediaPlayer = MediaPlayer.create(this, R.raw.bg_game_levelclear);
        mediaPlayer.setLooping(true);
        mediaPlayer.setVolume(0f, 0f);

        // ====== Parallax clouds: map id + speed giống Home ======
        addBand(R.id.clouds_far_1,  R.id.clouds_far_2,  dp(20));
        addBand(R.id.clouds_mid_1,  R.id.clouds_mid_2,  dp(60));
        addBand(R.id.clouds_near_1, R.id.clouds_near_2, dp(90));

        // Khởi tạo vị trí hai tấm mây liền nhau
        for (Band band : bands) {
            if (band.a == null || band.b == null) continue;
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

        // ====== UI logic sẵn có ======
        if (isLastLevel) {
            tv.setText("GAME COMPLETED!");
            btnNext.setVisibility(View.GONE);
        } else {
            btnNext.setVisibility(View.VISIBLE);
        }

        btnReplay.setOnClickListener(v -> {
            Intent replay = openLevel(fromLevel, difficulty, entryPlayerLevel, entryPlayerLevel);
            replay.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(replay);
            finish();
        });

        btnNext.setOnClickListener(v -> {
            int next = Math.min(3, fromLevel + 1);
            Intent nextLevel = openLevel(next, difficulty, currentPlayerLevel, currentPlayerLevel);
            nextLevel.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(nextLevel);
            finish();
        });

        btnRanking.setOnClickListener(v -> {
            Intent rank = new Intent(this, HighScoreActivity.class);
            rank.putExtra(HighScoreActivity.EXTRA_FROM, isLastLevel ? "finalclear" : "levelclear");
            startActivity(rank);
        });
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

    private Intent openLevel(int level, int difficulty, int playerLevel, int entrySnapshotLevel) {
        Class<?> dest = Level1Activity.class;
        if (level == 2) dest = Level2Activity.class;
        else if (level == 3) dest = Level3Activity.class;

        Intent it = new Intent(this, dest);
        it.putExtra(EXTRA_DIFFICULTY, difficulty);
        it.putExtra(EXTRA_PLAYER_LEVEL_CURRENT, playerLevel);
        it.putExtra(EXTRA_PLAYER_LEVEL_AT_ENTRY, entrySnapshotLevel);
        return it;
    }
}
