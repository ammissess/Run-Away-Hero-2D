package com.example.game2dfighting.game.entity;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.core.SpriteAnim;

/**
 * Enemy3: quái loại 3, stats riêng, sprite riêng e3_*,
 * hành vi di chuyển & attack giống Enemy1/Enemy2.
 */
public class Enemy3 extends Enemy {

    // === Stats riêng cho Enemy3 ===
    public static final int BASE_HP     = 80;
    public static final int BASE_SPEED  = 10;
    public static final int BASE_DAMAGE = 10;

    public Enemy3(Context ctx, int x, int y, int w, int h) {
        super(ctx, x, y, w, h);

        // --- Ghi đè sprite bộ riêng cho Enemy3 ---
        int[] idleIds = new int[]{
                R.drawable.e3_idle_0, R.drawable.e3_idle_1, R.drawable.e3_idle_2
        };
        int[] runIds = new int[]{
                R.drawable.e3_run_0, R.drawable.e3_run_1, R.drawable.e3_run_2,
                R.drawable.e3_run_3, R.drawable.e3_run_4, R.drawable.e3_run_5,
                R.drawable.e3_run_6
        };
        int[] atkIds = new int[]{
                R.drawable.e3_attack_0, R.drawable.e3_attack_1, R.drawable.e3_attack_2
        };
        int[] dieIds = new int[]{
                R.drawable.e3_die_0, R.drawable.e3_die_1, R.drawable.e3_die_2
        };

        Bitmap[] idleF = loadAndTrimFrames(ctx, idleIds);
        Bitmap[] runF  = loadAndTrimFrames(ctx, runIds);
        Bitmap[] atkF  = loadAndTrimFrames(ctx, atkIds);
        Bitmap[] dieF  = loadAndTrimFrames(ctx, dieIds);

        final int DRAW_W = this.w;
        final int DRAW_H = this.h;

        SpriteAnim idle = new SpriteAnim(idleF, 140, true,  DRAW_W, DRAW_H);
        SpriteAnim run  = new SpriteAnim(runF,   90,  true,  DRAW_W, DRAW_H);
        SpriteAnim atk  = new SpriteAnim(atkF,  100, false, DRAW_W, DRAW_H);
        SpriteAnim die  = new SpriteAnim(dieF,  120, false, DRAW_W, DRAW_H);

        anims.clear();
        anims.put(State.IDLE,   idle);
        anims.put(State.RUN,    run);
        anims.put(State.ATTACK, atk);
        anims.put(State.DIE,    die);

        setState(State.IDLE);

        // đặt speed riêng cho Enemy3
        this.speed = BASE_SPEED;
    }

    // ===== helpers (reuse từ Enemy) =====
    private static Bitmap trimTransparent(Bitmap src) {
        if (src == null) return null;
        final int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int a = (px[row + x] >>> 24) & 0xFF;
                if (a != 0) {
                    if (x < left)   left = x;
                    if (x > right)  right = x;
                    if (y < top)    top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < left || bottom < top) return src;
        return Bitmap.createBitmap(src, left, top, right - left + 1, bottom - top + 1);
    }

    protected static Bitmap[] loadAndTrimFrames(Context ctx, int[] ids) {
        Bitmap[] frames = SpriteAnim.loadFrames(ctx, ids);
        for (int i = 0; i < frames.length; i++) frames[i] = trimTransparent(frames[i]);
        return frames;
    }
}
