package com.example.game2dfighting.game.entity;


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.example.game2dfighting.game.core.GameObject;

public class Enemy implements GameObject {
    public float x, y;
    public float speed;
    public float radius;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Enemy(float x, float y, float speed, float radius) {
        this.x = x; this.y = y; this.speed = speed; this.radius = radius;
        paint.setColor(Color.RED); // chấm đỏ nhỏ
    }

    @Override
    public void update() {
        // di chuyển được làm trong EnemyManager (biết vị trí player)
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawCircle(x, y, radius, paint);
    }
}

