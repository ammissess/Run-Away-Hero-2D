package com.example.game2dfighting.game.entity;


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import com.example.game2dfighting.game.core.GameObject;
import com.example.game2dfighting.game.core.SpriteAnim;

public class Player implements GameObject {
    public int x, y, w, h, speed = 5;
    public boolean up, down, left, right;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SpriteAnim anim; // null nếu chưa có sprite

    public Player(int startX, int startY, int width, int height) {
        this.x = startX; this.y = startY; this.w = width; this.h = height;
    }

    public void setAnim(SpriteAnim anim) { this.anim = anim; }

    @Override
    public void update() {
        if (up) y -= speed;
        if (down) y += speed;
        if (left) x -= speed;
        if (right) x += speed;
    }

    @Override
    public void draw(Canvas canvas) {
        if (anim != null) {
            anim.update(System.currentTimeMillis());
            anim.draw(canvas, x, y, w, h);
        } else {
            // fallback: vẽ hình chữ nhật đỏ (như code cũ)
            paint.setColor(Color.RED);
            canvas.drawRect(new Rect(x, y, x + w, y + h), paint);
        }
    }

    public float centerX() { return x + w / 2f; }
    public float centerY() { return y + h / 2f; }
}
