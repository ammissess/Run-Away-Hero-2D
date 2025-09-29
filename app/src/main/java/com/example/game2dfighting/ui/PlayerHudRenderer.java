package com.example.game2dfighting.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import androidx.annotation.DrawableRes;

import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.R;

public class PlayerHudRenderer {
    private final Context ctx;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint en = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);

    // === Fire button ===
    private Rect   fireBtnRect;
    private float  fireBtnRadiusPx;
    private Bitmap bmFireBtn;

    // === Ice button (NEW) ===
    private Rect   iceBtnRect;
    private float  iceBtnRadiusPx;
    private Bitmap bmIceBtn;

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

        number.setColor(Color.WHITE);
        number.setTextSize(dp(14));
        number.setShadowLayer(4f, 0f, 0f, Color.BLACK);
        number.setTextAlign(Paint.Align.LEFT);
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
        drawBar(c, "HP", x, y, barW, barH, hpRatio, hp, p.getHp(), p.getMaxHp());

        // EN
        y += barH + spacing;
        float enRatio = clamp01(p.getEnergy() / (float) p.getMaxEnergy());
        drawBar(c, "EN", x, y, barW, barH, enRatio, en, p.getEnergy(), p.getMaxEnergy());

        // MP
        y += barH + spacing;
        float mpRatio = clamp01(p.getMana() / (float) p.getMaxMana());
        drawBar(c, "MP", x, y, barW, barH, mpRatio, mp, p.getMana(), p.getMaxMana());
    }

    private void drawBar(Canvas c, String name, float x, float y, float w, float h,
                         float ratio, Paint fg, int cur, int max) {
        c.drawRect(x, y, x + w, y + h, bg);
        c.drawRect(x, y, x + w * ratio, y + h, fg);
        c.drawRect(x, y, x + w, y + h, outline);
        c.drawText(name, x, y - dp(2), label);
        c.drawText(cur + "/" + max, x + w + dp(8), y + h - dp(2), number);
    }

    private float dp(float v) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return v * dm.density;
    }
    private float clamp01(float v){ return Math.max(0f, Math.min(1f, v)); }

    // === Fire API ===
    public void setFireballButtonImage(@DrawableRes int resId, int sizePx) {
        Bitmap raw = BitmapFactory.decodeResource(ctx.getResources(), resId);
        if (raw != null) {
            bmFireBtn = Bitmap.createScaledBitmap(raw, sizePx, sizePx, true);
            raw.recycle();
        }
    }
    public void setFireButtonBounds(Rect r, float radiusPx) {
        this.fireBtnRect = (r == null) ? null : new Rect(r);
        this.fireBtnRadiusPx = radiusPx;
    }
    public void drawFireButton(Canvas c) {
        if (fireBtnRect == null || bmFireBtn == null) return;
        c.drawBitmap(bmFireBtn, null, fireBtnRect, null);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setColor(Color.BLACK);
        c.drawCircle(fireBtnRect.exactCenterX(), fireBtnRect.exactCenterY(), fireBtnRadiusPx, p);
    }
    public boolean isInFireButton(float x, float y) {
        return fireBtnRect != null && fireBtnRect.contains((int)x, (int)y);
    }

    // === Ice API (NEW) ===
    public void setIceButtonImage(@DrawableRes int resId, int sizePx) {
        Bitmap raw = BitmapFactory.decodeResource(ctx.getResources(), resId);
        if (raw != null) {
            bmIceBtn = Bitmap.createScaledBitmap(raw, sizePx, sizePx, true);
            raw.recycle();
        }
    }
    public void setIceButtonBounds(Rect r, float radiusPx) {
        this.iceBtnRect = (r == null) ? null : new Rect(r);
        this.iceBtnRadiusPx = radiusPx;
    }
    public void drawIceButton(Canvas c) {
        if (iceBtnRect == null || bmIceBtn == null) return;
        c.drawBitmap(bmIceBtn, null, iceBtnRect, null);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setColor(Color.BLACK);
        c.drawCircle(iceBtnRect.exactCenterX(), iceBtnRect.exactCenterY(), iceBtnRadiusPx, p);
    }
    public boolean isInIceButton(float x, float y) {
        return iceBtnRect != null && iceBtnRect.contains((int)x, (int)y);
    }
}
