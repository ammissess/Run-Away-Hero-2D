package com.example.game2dfighting.game.entity;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.example.game2dfighting.game.core.GameObject;

public class Enemy implements GameObject {
    public float x, y;        // toạ độ thế giới (tâm)
    public float speed;       // px/frame
    public float radius;      // bán kính
    private int maxHp = 30;
    private int hp = 30;

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Enemy(float x, float y, float speed, float radius) {
        this.x = x; this.y = y; this.speed = speed; this.radius = radius;
        bodyPaint.setColor(Color.RED);
        barBg.setColor(0xFF333333);
        barFill.setColor(0xFFE53935); // đỏ tươi
    }

    @Override public void update() { /* move ở EnemyManager */ }

    @Override
    public void draw(Canvas c) {
        c.drawCircle(x, y, radius, bodyPaint);
        // HP bar nhỏ trên đầu
        int bw = 32, bh = 5;
        float left = x - bw/2f, top = y - radius - 10 - bh;
        c.drawRect(left, top, left + bw, top + bh, barBg);
        float ratio = Math.max(0f, Math.min(1f, hp / (float) maxHp));
        c.drawRect(left, top, left + bw * ratio, top + bh, barFill);
    }

    public boolean takeDamage(int dmg) {
        if (dmg <= 0) return false;
        hp -= dmg; if (hp < 0) hp = 0;
        return hp <= 0;
    }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int v) { maxHp = Math.max(1, v); hp = Math.min(hp, maxHp); }
}
