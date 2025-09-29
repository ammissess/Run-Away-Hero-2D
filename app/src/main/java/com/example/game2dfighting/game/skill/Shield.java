package com.example.game2dfighting.game.skill;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.game2dfighting.game.entity.Player;

public class Shield {
    private final Player player;
    private int shieldHP;
    private long shieldUntilMs;

    private final Paint shieldPaint;

    public Shield(Player player, int hp, long durationMs) {
        this.player = player;
        this.shieldHP = hp;
        this.shieldUntilMs = System.currentTimeMillis() + durationMs;

        shieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shieldPaint.setStyle(Paint.Style.STROKE);
        shieldPaint.setStrokeWidth(6f);
        shieldPaint.setColor(Color.CYAN); // viền xanh quanh Player
    }

    public void update() {
        if (isActive() && System.currentTimeMillis() > shieldUntilMs) {
            shieldHP = 0;
        }
    }

    public boolean isActive() {
        return shieldHP > 0;
    }

    /** absorb damage, trả về phần damage còn lại */
    public int absorbDamage(int dmg) {
        if (!isActive()) return dmg;
        int absorbed = Math.min(dmg, shieldHP);
        shieldHP -= absorbed;
        return dmg - absorbed;
    }

    /** Vẽ vòng tròn quanh Player nếu còn shield */
    public void draw(Canvas c, int cameraX, int cameraY) {
        if (!isActive()) return;

        // Tính tâm player trên màn hình (đã trừ camera)
        float cx = player.x + player.w / 2f - cameraX;
        float cy = player.y + player.h / 2f - cameraY;

        // Bán kính: lớn hơn một chút so với nửa kích thước player
        float radius = Math.max(player.w, player.h) * 0.6f; // tuỳ chỉnh hệ số 0.6f cho đẹp

        c.drawCircle(cx, cy, radius, shieldPaint);
    }



    public int getShieldHP() {
        return shieldHP;
    }
}
