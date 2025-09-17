package com.example.game2dfighting.view;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.game2dfighting.R;

import java.util.ArrayList;
import java.util.List;

public class GameOverActivity extends AppCompatActivity {

    private Button btnPlayAgain, btnQuit;

    // Cloud layers
    private ImageView cloudsFar1, cloudsFar2;
    private ImageView cloudsMid1, cloudsMid2;
    private ImageView cloudsNear1, cloudsNear2;

    // Keep animators to pause/resume
    private final List<ValueAnimator> runningAnimators = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        // Buttons
        btnPlayAgain = findViewById(R.id.btn_play_again);
        btnQuit = findViewById(R.id.btn_quit);

        btnPlayAgain.setOnClickListener(v -> {
            Intent intent = new Intent(GameOverActivity.this, Level1Activity.class);
            startActivity(intent);
            finish();
        });

        btnQuit.setOnClickListener(v -> {
            Intent intent = new Intent(GameOverActivity.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Clouds (đảm bảo activity_game_over.xml có các id này)
        cloudsFar1  = findViewById(R.id.clouds_far_1);
        cloudsFar2  = findViewById(R.id.clouds_far_2);
        cloudsMid1  = findViewById(R.id.clouds_mid_1);
        cloudsMid2  = findViewById(R.id.clouds_mid_2);
        cloudsNear1 = findViewById(R.id.clouds_near_1);
        cloudsNear2 = findViewById(R.id.clouds_near_2);

        // Khởi tạo parallax sau khi view đã layout xong để lấy được width
        // Dùng post() để đợi view đo xong
        if (cloudsFar1 != null) {
            cloudsFar1.post(this::startCloudAnimationsIfReady);
        }
    }

    private void startCloudAnimationsIfReady() {
        // Tốc độ (px/giây) cho từng lớp: xa chậm, gần nhanh
        // Bạn có thể tinh chỉnh số này cho mượt hơn
        float farSpeedPxPerSec  = 20f;
        float midSpeedPxPerSec  = 100f;
        float nearSpeedPxPerSec = 50f;

        runningAnimators.clear();

        if (cloudsFar1 != null && cloudsFar2 != null) {
            runningAnimators.add(startParallax(cloudsFar1, cloudsFar2, farSpeedPxPerSec));
        }
        if (cloudsMid1 != null && cloudsMid2 != null) {
            runningAnimators.add(startParallax(cloudsMid1, cloudsMid2, midSpeedPxPerSec));
        }
        if (cloudsNear1 != null && cloudsNear2 != null) {
            runningAnimators.add(startParallax(cloudsNear1, cloudsNear2, nearSpeedPxPerSec));
        }
    }

    /**
     * Tạo chuyển động lặp vô hạn: 2 ảnh song song trượt sang trái.
     * i1 và i2 đặt cạnh nhau ảo bằng cách dùng translationX.
     */
    private ValueAnimator startParallax(ImageView i1, ImageView i2, float pxPerSec) {
        // Nếu width chưa sẵn sàng, gọi lại sau
        int w = i1.getWidth();
        if (w == 0 && i1.getDrawable() != null) {
            w = i1.getDrawable().getIntrinsicWidth();
        }
        if (w <= 0) {
            // fallback: đợi thêm một frame
            i1.post(() -> startParallax(i1, i2, pxPerSec));
            return null;
        }

        // Animator chạy từ 0 -> 1, lặp vô hạn, dùng để tính offset
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setInterpolator(new LinearInterpolator());

        // Thời gian để trôi hết 1 chiều rộng ảnh
        long durationMs = (long) Math.max((w / pxPerSec) * 1000L, 1000L);
        animator.setDuration(durationMs);
        animator.setRepeatCount(ValueAnimator.INFINITE);

        final int width = w;

        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();   // 0..1
            float dx = (t * width) % width;           // 0..width
            // Ảnh 1 dịch trái từ 0 -> -width
            i1.setTranslationX(-dx);
            // Ảnh 2 nằm tiếp nối phía sau ảnh 1
            i2.setTranslationX(-dx + width);
        });

        animator.start();
        return animator;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume lại animators nếu cần (ValueAnimator tự tiếp tục nếu activity không bị destroy)
        for (ValueAnimator va : runningAnimators) {
            if (va != null && !va.isRunning()) va.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Tạm dừng để tiết kiệm pin/cpu khi không hiển thị
        for (ValueAnimator va : runningAnimators) {
            if (va != null && va.isRunning()) va.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Hủy để tránh memory leak
        for (ValueAnimator va : runningAnimators) {
            if (va != null) va.cancel();
        }
        runningAnimators.clear();
    }
}
