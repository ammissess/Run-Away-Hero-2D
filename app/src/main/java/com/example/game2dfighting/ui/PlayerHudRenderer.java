package com.example.game2dfighting.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;

import com.example.game2dfighting.game.entity.Player;

public class PlayerHudRenderer {
    private final Context ctx;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint en = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PlayerHudRenderer(Context ctx) {
        this.ctx = ctx;

        bg.setColor(Color.argb(140, 20, 20, 20));
        hp.setColor(Color.RED);
        mp.setColor(Color.BLUE);
        en.setColor(Color.YELLOW);

        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(dp(2));
        outline.setColor(Color.WHITE);

        label.setColor(Color.WHITE);
        label.setTextSize(dp(14));
        label.setShadowLayer(4f, 0f, 0f, Color.BLACK);
    }

    public void draw(Canvas c, Player p, int screenW, int screenH) {
        if (p == null) return;

        float margin = dp(16);
        float barW = Math.max(dp(180), screenW * 0.35f);
        float barH = dp(14);
        float spacing = dp(8);

        float x = margin;
        float y = margin;

        // HP
        float hpRatio = clamp01(p.getHp() / (float) p.getMaxHp());
        drawBar(c, "HP", x, y, barW, barH, hpRatio, hp);

        // Mana
        y += barH + spacing;
        float mpRatio = clamp01(p.getMana() / (float) p.getMaxMana());
        drawBar(c, "MP", x, y, barW, barH, mpRatio, mp);

        // Energy
        y += barH + spacing;
        float enRatio = clamp01(p.getEnergy() / (float) p.getMaxEnergy());
        drawBar(c, "EN", x, y, barW, barH, enRatio, en);
    }

    private void drawBar(Canvas c, String name, float x, float y, float w, float h, float ratio, Paint fg) {
        // nền
        c.drawRect(x, y, x + w, y + h, bg);
        // fill
        c.drawRect(x, y, x + w * ratio, y + h, fg);
        // viền
        c.drawRect(x, y, x + w, y + h, outline);
        // nhãn
        c.drawText(name, x, y - dp(2), label);
    }

    private float dp(float v) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return v * dm.density;
    }
    private float clamp01(float v){ return Math.max(0f, Math.min(1f, v)); }
}

