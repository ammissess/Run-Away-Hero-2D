package com.example.game2dfighting.game.skill;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.game2dfighting.game.entity.Player;

public class BossBullet {
    public float x, y;
    public float vx, vy;
    public float speed;
    public boolean alive = true;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BossBullet(float x, float y, float vx, float vy, float speed, int color) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.speed = speed;
        paint.setColor(color);
    }

    public void update(float dtSec) {
        x += vx * speed;
        y += vy * speed;
        if (x < -200 || y < -200 || x > 8000 || y > 8000) alive = false;
    }

    public void draw(Canvas c, float camX, float camY) {
        if (!alive) return;
        c.drawCircle(x - camX, y - camY, 12f, paint);
    }

    public boolean hit(Player p) {
        RectF b = new RectF(x - 12, y - 12, x + 12, y + 12);
        RectF pr = new RectF(p.x, p.y, p.x + p.w, p.y + p.h);
        return RectF.intersects(b, pr);
    }
}
