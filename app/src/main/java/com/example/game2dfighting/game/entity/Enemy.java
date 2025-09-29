package com.example.game2dfighting.game.entity;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.core.GameObject;
import com.example.game2dfighting.game.core.SpriteAnim;

/**
 * Enemy: quái có 4 state cơ bản (IDLE/RUN/ATTACK/DIE),
 * di chuyển đuổi mục tiêu, khóa anim khi tấn công, và tự báo kết thúc anim DIE.
 *
 * Giờ có thêm các chỉ số riêng: BASE_HP, BASE_SPEED, BASE_DAMAGE.
 */
public class Enemy extends GameObject {
    private final Context ctx;

    // === Tham số stats cơ bản cho Enemy loại 1 ===
    public static final int BASE_HP     = 10;
    public static final int BASE_SPEED  = 1;
    public static final int BASE_DAMAGE = 3;

    private float speedMultiplier = 1.0f;
    private long slowUntilMs = 0L;

    // speed thực tế khi di chuyển
    protected int speed = BASE_SPEED;

    // Kích thước sprite gốc (để tham chiếu, vẽ HP bar chính xác theo sprite)
    private int spriteW;
    private int spriteH;

    // ==== ANIM LOCK ====
    private static final long ATTACK_ANIM_MS = 450L;
    private long animLockUntilMs = 0L;

    private static final long DIE_ANIM_MS = 480L;
    private long dieEndAtMs = 0L;

    private boolean isAnimLocked() {
        return System.currentTimeMillis() < animLockUntilMs || state == State.DIE;
    }

    public float xCenter() { return x + w / 2f; }
    public float yCenter() { return y + h / 2f; }

    public Enemy(Context ctx, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.ctx = ctx;
        loadAnimations();
        setState(State.IDLE);
    }

    private void loadAnimations() {
        int[] idleIds = new int[]{
                R.drawable.e_idle_0, R.drawable.e_idle_1, R.drawable.e_idle_2,
                R.drawable.e_idle_3, R.drawable.e_idle_4, R.drawable.e_idle_5
        };
        int[] runIds  = new int[]{
                R.drawable.e_run_0, R.drawable.e_run_1, R.drawable.e_run_2,
                R.drawable.e_run_3, R.drawable.e_run_4, R.drawable.e_run_5,
                R.drawable.e_run_6, R.drawable.e_run_7
        };
        int[] atkIds  = new int[]{
                R.drawable.e_attack_0, R.drawable.e_attack_1, R.drawable.e_attack_2,
                R.drawable.e_attack_3, R.drawable.e_attack_4, R.drawable.e_attack_5
        };
        int[] dieIds  = new int[]{
                R.drawable.e_die_0, R.drawable.e_die_1, R.drawable.e_die_2,
                R.drawable.e_die_3
        };

        Bitmap[] idleF = loadAndTrimFrames(ctx, idleIds);
        Bitmap[] runF  = loadAndTrimFrames(ctx, runIds);
        Bitmap[] atkF  = loadAndTrimFrames(ctx, atkIds);
        Bitmap[] dieF  = loadAndTrimFrames(ctx, dieIds);

        Bitmap sample = idleF[0];
        spriteW = (sample != null) ? sample.getWidth()  : w;
        spriteH = (sample != null) ? sample.getHeight() : h;

        this.w = 100;
        this.h = 100;

        final int DRAW_W = 100;
        final int DRAW_H = 100;

        SpriteAnim idle = new SpriteAnim(idleF, 140, true,  DRAW_W, DRAW_H);
        SpriteAnim run  = new SpriteAnim(runF,   90,  true,  DRAW_W, DRAW_H);
        SpriteAnim atk  = new SpriteAnim(atkF,  100, false, DRAW_W, DRAW_H);
        SpriteAnim die  = new SpriteAnim(dieF,  120, false, DRAW_W, DRAW_H);

        anims.put(State.IDLE,   idle);
        anims.put(State.RUN,    run);
        anims.put(State.ATTACK, atk);
        anims.put(State.DIE,    die);
    }

    public void pursue(int targetX, int targetY, long dtMs) {
        if (state == State.DIE) {
            super.update(dtMs);
            return;
        }
        if (isAnimLocked()) {
            super.update(dtMs);
            return;
        }

        int dx = 0, dy = 0;
        // reset slow khi hết hạn
        if (slowUntilMs > 0 && System.currentTimeMillis() > slowUntilMs) {
            speedMultiplier = 1.0f;
            slowUntilMs = 0L;
        }

        float realSpeed = speed * speedMultiplier;

        if (targetX < x) dx = (int)-realSpeed;
        else if (targetX > x) dx = (int)realSpeed;

        if (targetY < y) dy = (int)-realSpeed;
        else if (targetY > y) dy = (int)realSpeed;


        if (dx < 0) setFacingLeft(true);
        else if (dx > 0) setFacingLeft(false);

        x += dx;
        y += dy;

        if (dx != 0 || dy != 0) setState(State.RUN);
        else setState(State.IDLE);

        super.update(dtMs);
    }

    public void startAttack() {
        if (state == State.DIE) return;
        setState(State.ATTACK);
        animLockUntilMs = System.currentTimeMillis() + ATTACK_ANIM_MS;
    }

    public void onDie() {
        setState(State.DIE);
        long dur = DIE_ANIM_MS;
        try {
            SpriteAnim dieAnim = anims.get(State.DIE);
            if (dieAnim != null) {
                try {
                    dur = (long) SpriteAnim.class.getMethod("getTotalDurationMs").invoke(dieAnim);
                } catch (NoSuchMethodException nsme) {
                    try {
                        int frameCount = (int) SpriteAnim.class.getMethod("getFrameCount").invoke(dieAnim);
                        int frameMs    = (int) SpriteAnim.class.getMethod("getFrameDurationMs").invoke(dieAnim);
                        dur = Math.max(dur, (long) frameCount * frameMs);
                    } catch (Throwable ignore2) {}
                }
            }
        } catch (Throwable ignore) {}
        dieEndAtMs = System.currentTimeMillis() + dur;
    }

    public boolean isDieAnimDone() {
        return state == State.DIE && System.currentTimeMillis() >= dieEndAtMs;
    }

    public long getDieAnimMs() { return DIE_ANIM_MS; }

    public State getState() { return state; }

    public int getSpriteW() { return spriteW; }
    public int getSpriteH() { return spriteH; }

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
        for (int i = 0; i < frames.length; i++) {
            frames[i] = trimTransparent(frames[i]);
        }
        return frames;
    }

    public void forceIdle() {
        try {
            setState(State.IDLE);
        } catch (Exception ignore) {}
    }

    public void applySlow(float multiplier, long durationMs) {
        this.speedMultiplier = multiplier;
        this.slowUntilMs = System.currentTimeMillis() + durationMs;
    }

}
