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
    private RecyclerView rvL1, rvL2, rvL3;

    // Clouds (parallax)
    private static class Band {
        ImageView a, b;
        float speedPxPerSec;
        boolean initialized = false;
    }
    private final List<Band> bands = new ArrayList<>();
    private boolean running = false, loopPosted = false;
    private long lastNs = 0L;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_high_score);

        rvL1 = findViewById(R.id.rvScoresL1);
        rvL2 = findViewById(R.id.rvScoresL2);
        rvL3 = findViewById(R.id.rvScoresL3);

        setupRecycler(rvL1);
        setupRecycler(rvL2);
        setupRecycler(rvL3);

        List<ScoreManager.ScoreEntry> all = ScoreManager.sorted(this);
        List<ScoreManager.ScoreEntry> l1 = new ArrayList<>();
        List<ScoreManager.ScoreEntry> l2 = new ArrayList<>();
        List<ScoreManager.ScoreEntry> l3 = new ArrayList<>();

        for (ScoreManager.ScoreEntry s : all) {
            if (s.levelName.equalsIgnoreCase("Level1") || s.levelName.equalsIgnoreCase("Easy")) {
                l1.add(s);
            } else if (s.levelName.equalsIgnoreCase("Level2") || s.levelName.equalsIgnoreCase("Normal")) {
                l2.add(s);
            } else if (s.levelName.equalsIgnoreCase("Level3") || s.levelName.equalsIgnoreCase("Hard")) {
                l3.add(s);
            }
        }

        rvL1.setAdapter(new ScoreAdapter(topN(l1, 6)));
        rvL2.setAdapter(new ScoreAdapter(topN(l2, 6)));
        rvL3.setAdapter(new ScoreAdapter(topN(l3, 6)));

        Button btnBack = findViewById(R.id.btnBackHome);
        btnBack.setOnClickListener(v -> {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        mediaPlayer = MediaPlayer.create(this, R.raw.bg_game_highscore);
        mediaPlayer.setLooping(true);
        mediaPlayer.setVolume(0f, 0f);

        addBand(R.id.clouds_far_1, R.id.clouds_far_2, dp(20));
        addBand(R.id.clouds_mid_1, R.id.clouds_mid_2, dp(60));
        addBand(R.id.clouds_near_1, R.id.clouds_near_2, dp(90));

        for (Band band : bands) {
            band.a.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
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

    private void setupRecycler(RecyclerView rv) {
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setHasFixedSize(true);
    }

    private static List<ScoreManager.ScoreEntry> topN(List<ScoreManager.ScoreEntry> src, int n) {
        if (src.size() <= n) return src;
        return new ArrayList<>(src.subList(0, n));
    }

    // Clouds helpers
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
            bands.get(0).a.postOnAnimation(() -> {});
            loopPosted = true;
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) mediaPlayer.start();
    }

    @Override protected void onPause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    // RecyclerView Adapter
    static class ScoreAdapter extends RecyclerView.Adapter<ScoreAdapter.VH> {
        private final List<ScoreManager.ScoreEntry> data;
        ScoreAdapter(List<ScoreManager.ScoreEntry> data) { this.data = data; }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvRank, tvScore, tvTime, tvDate;
            VH(View v) {
                super(v);
                tvRank = v.findViewById(R.id.tvRank);
                tvScore = v.findViewById(R.id.tvScore);
                tvTime = v.findViewById(R.id.tvTime);
                tvDate = v.findViewById(R.id.tvDate);
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

    private float dp(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
