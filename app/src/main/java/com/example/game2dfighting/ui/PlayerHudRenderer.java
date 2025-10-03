package com.example.game2dfighting.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import androidx.annotation.DrawableRes;

import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.game.skill.Shield;
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

    // === NEW: LV & EXP Paints ===
    private final Paint levelText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    // === Fire button ===
    private Rect   fireBtnRect;
    private float  fireBtnRadiusPx;
    private Bitmap bmFireBtn;

    // === Ice button (NEW) ===
    private Rect   iceBtnRect;
    private float  iceBtnRadiusPx;
    private Bitmap bmIceBtn;

    // === Shield button (NEW) ===
    private Bitmap shieldBtn;
    private RectF shieldBtnRect = new RectF();

    // Cooldown UI (0f = sẵn sàng, 1f = vừa bấm xong)
    private float fireCdRatio   = 0f;
    private float iceCdRatio    = 0f;
    private float shieldCdRatio = 0f;

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

        // NEW: LV & EXP style
        levelText.setColor(Color.WHITE);
        levelText.setTextSize(dp(18));
        levelText.setFakeBoldText(true);
        levelText.setShadowLayer(4f, 0f, 0f, Color.BLACK);

        expBg.setColor(Color.argb(140, 60, 60, 60));
        expFill.setColor(Color.rgb(80, 200, 255)); // xanh EXP
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

        // NEW: hiển thị số HP gồm cả shield ở tử số
        int displayHp = p.getHp();
        Shield sh = p.getShield();
        if (sh != null && sh.isActive()) {
            displayHp = p.getHp() + sh.getShieldHP(); // ví dụ 250/200
        }

        // Giữ nguyên phần lấp đầy thanh dựa trên HP thật (không vượt quá 100%)
        drawBar(c, "HP", x, y, barW, barH, hpRatio, hp, displayHp, p.getMaxHp());

        // EN
        y += barH + spacing;
        float enRatio = clamp01(p.getEnergy() / (float) p.getMaxEnergy());
        drawBar(c, "MP", x, y, barW, barH, enRatio, en, p.getEnergy(), p.getMaxEnergy());

        // ===== NEW: LV + EXP =====
        y += barH + spacing; // khoảng cách dưới cùng 3 thanh
        // LV text
        c.drawText("LV " + p.getLevel(), x, y + dp(16), levelText);

        // Thanh EXP (mỏng)
        float expProgress = p.getExpProgress(); // 0..1
        float expTop = y + dp(22);
        float expH = dp(8);
        // nền
        c.drawRect(x, expTop, x + barW, expTop + expH, expBg);
        // fill
        c.drawRect(x, expTop, x + barW * clamp01(expProgress), expTop + expH, expFill);
        // viền
        c.drawRect(x, expTop, x + barW, expTop + expH, outline);
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
        drawCooldownOverlay(c, fireBtnRect, fireCdRatio);
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
        drawCooldownOverlay(c, iceBtnRect, iceCdRatio);
    }

    public boolean isInIceButton(float x, float y) {
        return iceBtnRect != null && iceBtnRect.contains((int)x, (int)y);
    }
    public void setShieldButtonImage(@DrawableRes int resId, int sizePx) {
        Bitmap raw = BitmapFactory.decodeResource(ctx.getResources(), resId);
        if (raw != null) {
            shieldBtn = Bitmap.createScaledBitmap(raw, sizePx, sizePx, true);
            raw.recycle();
        }
    }

    public void setShieldButtonBounds(float left, float top, float right, float bottom) {
        shieldBtnRect.set(left, top, right, bottom);
    }

    public boolean isInShieldButton(float x, float y) {
        return shieldBtnRect.contains(x, y);
    }

    public void drawShieldButton(Canvas c) {
        if (shieldBtn != null && shieldBtnRect != null) {
            float cx = shieldBtnRect.centerX();
            float cy = shieldBtnRect.centerY();
            float radius = Math.min(shieldBtnRect.width(), shieldBtnRect.height()) / 2f;

            // Vẽ bitmap icon khiên trong hình tròn
            Rect dst = new Rect(
                    (int)(cx - radius),
                    (int)(cy - radius),
                    (int)(cx + radius),
                    (int)(cy + radius)
            );
            c.drawBitmap(shieldBtn, null, dst, null);

            // Vẽ viền tròn (giống Fire/Ice)
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6f);
            p.setColor(Color.CYAN);
            c.drawCircle(cx, cy, radius, p);
            drawCooldownOverlay(c, new Rect(
                    (int)(cx - radius),
                    (int)(cy - radius),
                    (int)(cx + radius),
                    (int)(cy + radius)
            ), shieldCdRatio);

        }
    }

    // --- NEW: auto layout 3 nút kỹ năng ở góc dưới-phải ---
    public void layoutActionButtons(int screenW, int screenH,
                                    @DrawableRes int fireRes,
                                    @DrawableRes int iceRes,
                                    @DrawableRes int shieldRes) {
        int margin = dpI(16);
        int size   = dpI(80);
        int gap    = dpI(12);

        // Fire: dưới-phải
        int fireLeft = screenW - margin - size;
        int fireTop  = screenH - margin - size;
        Rect fire = new Rect(fireLeft, fireTop, fireLeft + size, fireTop + size);
        setFireButtonBounds(fire, size / 2f);
        setFireballButtonImage(fireRes, size);

        // Ice: bên trái Fire
        int iceLeft = fireLeft - gap - size;
        int iceTop  = fireTop;
        Rect ice = new Rect(iceLeft, iceTop, iceLeft + size, iceTop + size);
        setIceButtonBounds(ice, size / 2f);
        setIceButtonImage(iceRes, size);

        // Shield: bên trái Ice
        int shieldLeft = iceLeft - gap - size;
        int shieldTop  = iceTop;
        setShieldButtonBounds(shieldLeft, shieldTop, shieldLeft + size, shieldTop + size);
        setShieldButtonImage(shieldRes, size);
    }

    // helper dp -> int
    private int dpI(float v) {
        return Math.round(dp(v));
    }

    // Setter — GameView gọi mỗi frame trước khi vẽ
    public void setFireCooldownRatio(float r){ fireCdRatio = clamp01(r); }
    public void setIceCooldownRatio(float r){ iceCdRatio  = clamp01(r); }
    public void setShieldCooldownRatio(float r){ shieldCdRatio = clamp01(r); }

    private void drawCooldownOverlay(Canvas c, Rect dstRect, float ratio) {
        if (dstRect == null) return;
        ratio = clamp01(ratio);
        if (ratio <= 0f) return;

        // Tính hình tròn khớp với icon
        float cx = dstRect.exactCenterX();
        float cy = dstRect.exactCenterY();
        float radius = Math.min(dstRect.width(), dstRect.height()) / 2f;

        // 1) Lớp phủ mờ dạng TRÒN
        Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        dim.setColor(Color.BLACK);
        dim.setAlpha(140);
        c.drawCircle(cx, cy, radius, dim);

        // 2) “Pie” cooldown TRÒN (quạt từ đỉnh -90°)
        float inner = radius - dp(2); // chừa mép viền đẹp hơn
        RectF oval = new RectF(cx - inner, cy - inner, cx + inner, cy + inner);

        Paint pie = new Paint(Paint.ANTI_ALIAS_FLAG);
        pie.setColor(Color.WHITE);
        pie.setAlpha(90);
        pie.setStyle(Paint.Style.FILL);
        c.drawArc(oval, -90f, 360f * ratio, true, pie);

        // 3) Viền mảnh tròn (tùy chọn, giúp rõ hình)
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        border.setColor(Color.WHITE);
        border.setAlpha(160);
        c.drawCircle(cx, cy, inner, border);
    }

}
