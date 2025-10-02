package com.example.game2dfighting.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.media.MediaPlayer;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.manager.ScoreManager;

import java.util.ArrayList;
import java.util.List;

public class HighScoreActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;

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
            bands.get(0).a.postOnAnimation(this);
        }
    };

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_high_score);

        // ====== RecyclerView top 6 scores ======
        RecyclerView rv = findViewById(R.id.rvScores);
        rv.setLayoutManager(new LinearLayoutManager(this));
        List<ScoreManager.ScoreEntry> list = ScoreManager.sorted(this);
        if (list.size() > 6) {
            list = list.subList(0, 6);  // chỉ lấy 6 phần tử đầu tiên
        }
        rv.setAdapter(new ScoreAdapter(list));

        // ====== Nút Back ======
        Button btnBack = findViewById(R.id.btnBackHome);
        btnBack.setOnClickListener(v -> {
            Intent i = new Intent(this, com.example.game2dfighting.view.HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        // ===== Nhạc nền =====
        mediaPlayer = MediaPlayer.create(this, R.raw.bg_game_highscore);
        mediaPlayer.setLooping(true);
        mediaPlayer.setVolume(0f, 0f); // sẽ fade-in trong onResume()


        // ====== Cloud bands setup (giống HomeActivity) ======
        addBand(R.id.clouds_far_1,  R.id.clouds_far_2,  dp(20));  // xa: chậm
        addBand(R.id.clouds_mid_1,  R.id.clouds_mid_2,  dp(60));  // vừa
        addBand(R.id.clouds_near_1, R.id.clouds_near_2, dp(90));  // gần: nhanh

        for (Band band : bands) {
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
    }

    // ====== Helper methods ======
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
            fadeIn(mediaPlayer, 600); // 0.6s fade-in, chỉnh tùy ý
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


    // ====== RecyclerView Adapter ======
    static class ScoreAdapter extends RecyclerView.Adapter<ScoreAdapter.VH> {
        private final List<ScoreManager.ScoreEntry> data;

        ScoreAdapter(List<ScoreManager.ScoreEntry> data) { this.data = data; }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvRank, tvScore, tvTime, tvDate;
            VH(View v) {
                super(v);
                tvRank = v.findViewById(R.id.tvRank);
                tvScore = v.findViewById(R.id.tvScore);
                tvTime  = v.findViewById(R.id.tvTime);
                tvDate  = v.findViewById(R.id.tvDate);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_score, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            ScoreManager.ScoreEntry s = data.get(pos);
            h.tvRank.setText(String.valueOf(pos + 1));
            h.tvScore.setText("Score: " + s.score);
            h.tvTime.setText("Time: " + ScoreManager.formatDuration(s.durationMs));
            h.tvDate.setText(ScoreManager.formatDate(s.ts));
        }

        @Override public int getItemCount() { return data.size(); }
    }

    // Fade-in volume
    private void fadeIn(MediaPlayer mp, int durationMs) {
        final int steps = 20;
        final float delta = 1.0f / steps;
        final int stepDelay = Math.max(10, durationMs / steps);

        mp.setVolume(0f, 0f);
        final android.os.Handler h = new android.os.Handler();
        for (int i = 1; i <= steps; i++) {
            final float vol = delta * i;
            h.postDelayed(() -> {
                if (mp != null && mp.isPlaying()) mp.setVolume(vol, vol);
            }, (long) i * stepDelay);
        }
    }

}
