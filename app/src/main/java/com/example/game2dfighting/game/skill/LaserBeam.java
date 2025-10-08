package com.example.game2dfighting.game.skill;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.game2dfighting.game.entity.Player;

/**
 * LaserBeam: Tia laser của BossAngel.
 * - Bắn theo hướng cố định (vx, vy)
 * - Có thời gian tồn tại (2s)
 * - Khi trúng player thì không biến mất ngay, mà tồn tại thêm 1s để tạo hiệu ứng.
 * - Có hiệu ứng sáng dần khi mới bắn, và mờ dần khi sắp tắt.
 */
public class LaserBeam {
    public float x, y;           // điểm xuất phát
    public float vx, vy;         // vector hướng
    public float speed;
    public boolean alive = true;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long createdAtMs;
    private long lifetimeMs = 2000L; // tồn tại 2s
    private boolean hitOnce = false;

    public LaserBeam(float x, float y, float vx, float vy, float speed, int color) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.speed = speed;

        paint.setColor(color);
        paint.setStrokeWidth(12f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setShadowLayer(10f, 0, 0, color);

        createdAtMs = System.currentTimeMillis();
    }

    // Gọi khi laser trúng player — để nó không tắt ngay mà giữ thêm 1s
    public void markHit() {
        if (!hitOnce) {
            hitOnce = true;
            lifetimeMs = 1000L; // sau khi trúng, tồn tại thêm 1 giây
            createdAtMs = System.currentTimeMillis();
        }
    }

    // Cập nhật logic — tự hủy sau lifetime
    public void update(float dtSec) {
        if (!alive) return;

        long now = System.currentTimeMillis();
        if (now - createdAtMs > lifetimeMs) {
            alive = false;
            return;
        }

        // di chuyển chậm đầu laser (nếu cần)
        x += vx * speed * dtSec * 30f;
        y += vy * speed * dtSec * 30f;
    }

    // Vẽ tia laser
    public void draw(Canvas c, float camX, float camY) {
        if (!alive) return;

        long now = System.currentTimeMillis();
        float progress = (now - createdAtMs) / (float) lifetimeMs;
        progress = Math.min(1f, Math.max(0f, progress));

        // hiệu ứng sáng dần lúc mới xuất hiện, mờ dần khi gần biến mất
        float alphaFade = (progress < 0.3f)
                ? (progress / 0.3f)
                : (progress > 0.8f ? (1f - progress) / 0.2f : 1f);
        int alpha = (int) (255 * alphaFade);
        paint.setAlpha(alpha);

        float startX = x - camX;
        float startY = y - camY;
        float len = 4000f; // độ dài laser
        float endX = startX + vx * len;
        float endY = startY + vy * len;

        c.drawLine(startX, startY, endX, endY, paint);
    }

    // Kiểm tra va chạm với player (chỉ phần đầu laser)
    public boolean hit(Player p) {
        if (!alive) return false;
        RectF player = new RectF(p.x, p.y, p.x + p.w, p.y + p.h);
        RectF hit = new RectF(x - 24, y - 24, x + 24, y + 24);
        return RectF.intersects(hit, player);
    }
}
