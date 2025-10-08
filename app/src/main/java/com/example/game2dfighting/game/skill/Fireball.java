package com.example.game2dfighting.game.skill;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.entity.Enemy;

public class Fireball {
    public float x, y;          // tâm viên đạn (MAP)
    public float vx, vy;        // vận tốc (px/s)
    public float radius = 18f;  // bán kính va chạm ~ 1/2 cạnh sprite đã scale
    public boolean alive = true;
    public int damage = 15;

    private final int mapW, mapH;

    // Sprite & vẽ
    private static Bitmap fireballBmp;     // cache chung
    private static Bitmap fireballScaled;  // cache bitmap đã scale
    private static final float SPRITE_SIZE_PX = 360f; // kích thước hiển thị mong muốn (vuông)

    // ma trận xoay theo hướng bay
    private final Matrix matrix = new Matrix();

    //fix spam crassh

    public Fireball(float cx, float cy, float vx, float vy, int mapW, int mapH, Context ctx){
        this.x = cx; this.y = cy;
        this.vx = vx; this.vy = vy;
        this.mapW = mapW; this.mapH = mapH;

        synchronized (Fireball.class) {
            if (fireballBmp == null || fireballBmp.isRecycled()) {
                fireballBmp = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.fireball);
            }
            if (fireballScaled == null || fireballScaled.isRecycled()) {
                fireballScaled = Bitmap.createScaledBitmap(
                        fireballBmp,
                        (int) SPRITE_SIZE_PX,
                        (int) SPRITE_SIZE_PX,
                        true
                );
            }
        }

        this.radius = SPRITE_SIZE_PX * 0.45f;
    }


    public void update(float dtSec){
        if (!alive) return;
        x += vx * dtSec;
        y += vy * dtSec;

        if (x < -radius || y < -radius || x > mapW + radius || y > mapH + radius){
            alive = false;
        }
    }

    public void draw(Canvas c, int cameraX, int cameraY){
        if (!alive) return;

        float sx = x - cameraX;
        float sy = y - cameraY;

        if (fireballScaled != null && !fireballScaled.isRecycled()) {
            float angleDeg = (float) Math.toDegrees(Math.atan2(vy, vx));
            matrix.reset();
            matrix.postTranslate(-fireballScaled.getWidth()/2f, -fireballScaled.getHeight()/2f);
            matrix.postRotate(angleDeg);
            matrix.postTranslate(sx, sy);
            c.drawBitmap(fireballScaled, matrix, null);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.RED);
            c.drawCircle(sx, sy, radius, p); // fallback
        }
    }


    /** Va chạm: tròn (đạn) vs. hộp (enemy). */
    public boolean hit(Enemy e){
        float nx = Math.max(e.x, Math.min(x, e.x + e.w));
        float ny = Math.max(e.y, Math.min(y, e.y + e.h));
        float dx = x - nx, dy = y - ny;
        return dx*dx + dy*dy <= radius*radius;
    }

    public RectF getRect(){
        return new RectF(x - radius, y - radius, x + radius, y + radius);
    }
}
