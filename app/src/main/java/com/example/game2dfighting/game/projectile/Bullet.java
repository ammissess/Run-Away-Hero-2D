package com.example.game2dfighting.game.projectile;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.game2dfighting.game.entity.Enemy;

public class Bullet {
    public float x, y;          // tâm viên đạn
    public float vx, vy;        // vận tốc (px/s)
    public float radius = 10f;  // bán kính vẽ & va chạm
    public boolean alive = true;
    public int damage = 15;     // sát thương cơ bản

    // map bounds để remove khi bay ra ngoài
    private final int mapW, mapH;

    // style vẽ
    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    static { paint.setStrokeWidth(2f); }

    public Bullet(float cx, float cy, float vx, float vy, int mapW, int mapH){
        this.x = cx; this.y = cy;
        this.vx = vx; this.vy = vy;
        this.mapW = mapW; this.mapH = mapH;
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
        c.drawCircle(sx, sy, radius, paint);
    }

    /** Va chạm đơn giản: tròn (đạn) vs. hộp (enemy). */
    public boolean hit(Enemy e){
        // Lấy tâm enemy (dùng bbox GameObject)
        float ex = e.x + e.w/2f;
        float ey = e.y + e.h/2f;

        // Clamp tâm đạn vào bbox enemy
        float nx = Math.max(e.x, Math.min(x, e.x + e.w));
        float ny = Math.max(e.y, Math.min(y, e.y + e.h));

        float dx = x - nx, dy = y - ny;
        return dx*dx + dy*dy <= radius*radius;
    }

    /** AABB hỗ trợ debug (không bắt buộc) */
    public RectF getRect(){
        return new RectF(x - radius, y - radius, x + radius, y + radius);
    }
}
