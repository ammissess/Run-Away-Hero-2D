package com.example.game2dfighting.game.skill;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.entity.Enemy;
import com.example.game2dfighting.game.entity.Boss;

public class IceSpike {
    public float x, y;          // tâm viên đạn (MAP)
    public float vx, vy;        // vận tốc (px/s)
    public float radius = 18f;  // bán kính va chạm
    public boolean alive = true;
    public int damage = 10;     // băng yếu hơn lửa

    private final int mapW, mapH;

    // Sprite
    private static Bitmap iceBmp;
    private static Bitmap iceScaled;
    private static final float SPRITE_SIZE_PX = 360f;

    // Slow effect
    private final float slowMultiplier = 0.5f;   // giảm 50%
    private final long slowDurationMs = 2000L;   // trong 2 giây

    // Matrix xoay sprite
    private final Matrix matrix = new Matrix();

    public IceSpike(float cx, float cy, float vx, float vy, int mapW, int mapH, Context ctx) {
        this.x = cx; this.y = cy;
        this.vx = vx; this.vy = vy;
        this.mapW = mapW; this.mapH = mapH;

        if (iceBmp == null) {
            iceBmp = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.icespike);
        }
        if (iceBmp != null && iceScaled == null) {
            iceScaled = Bitmap.createScaledBitmap(
                    iceBmp,
                    (int) SPRITE_SIZE_PX,
                    (int) SPRITE_SIZE_PX,
                    true
            );
        }
        this.radius = SPRITE_SIZE_PX * 0.45f;
    }

    public void update(float dtSec) {
        if (!alive) return;
        x += vx * dtSec;
        y += vy * dtSec;

        if (x < -radius || y < -radius || x > mapW + radius || y > mapH + radius) {
            alive = false;
        }
    }

    public void draw(Canvas c, int cameraX, int cameraY) {
        if (!alive) return;
        float sx = x - cameraX;
        float sy = y - cameraY;

        if (iceScaled != null) {
            float angleDeg = (float) Math.toDegrees(Math.atan2(vy, vx));

            matrix.reset();
            matrix.postTranslate(-iceScaled.getWidth()/2f, -iceScaled.getHeight()/2f);
            matrix.postRotate(angleDeg);
            matrix.postTranslate(sx, sy);

            c.drawBitmap(iceScaled, matrix, null);
        }
    }

    /** Va chạm với Enemy */
    public boolean hit(Enemy e) {
        float nx = Math.max(e.x, Math.min(x, e.x + e.w));
        float ny = Math.max(e.y, Math.min(y, e.y + e.h));
        float dx = x - nx, dy = y - ny;
        boolean collided = dx*dx + dy*dy <= radius*radius;
        if (collided) {
            applySlow(e);
        }
        return collided;
    }

    /** Va chạm với Boss */
    public boolean hit(Boss b) {
        float nx = Math.max(b.x, Math.min(x, b.x + b.w));
        float ny = Math.max(b.y, Math.min(y, b.y + b.h));
        float dx = x - nx, dy = y - ny;
        boolean collided = dx*dx + dy*dy <= radius*radius;
        if (collided) {
            applySlow(b);
        }
        return collided;
    }

    public RectF getRect() {
        return new RectF(x - radius, y - radius, x + radius, y + radius);
    }

    // === Slow effect ===
    private void applySlow(Object target) {
        if (target == null) return;
        try {
            // Ưu tiên method applySlow(float,long)
            target.getClass()
                    .getMethod("applySlow", float.class, long.class)
                    .invoke(target, slowMultiplier, slowDurationMs);
        } catch (Throwable ignore) {
            // Nếu không có method, thử set field trực tiếp
            try {
                target.getClass().getField("speedMul").setFloat(target, slowMultiplier);
                long until = System.currentTimeMillis() + slowDurationMs;
                target.getClass().getField("slowUntilMs").setLong(target, until);
            } catch (Throwable ignore2) {
                // nếu không có field -> thôi, vẫn gây damage nhưng không slow
            }
        }
    }
}
