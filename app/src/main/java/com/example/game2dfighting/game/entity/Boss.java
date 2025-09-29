package com.example.game2dfighting.game.entity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.core.GameObject;
import com.example.game2dfighting.game.core.SpriteAnim;

import java.util.HashMap;
import java.util.Map;

/**
 * Boss: độc lập với Enemy. Có anim riêng, lock ATTACK/DIE, và HURT (sprite riêng hoặc flash).
 *
 * Không phụ thuộc State.HURT (tránh lỗi compile nếu enum State chưa có HURT).
 * Thay vào đó dùng cờ isHurting + hurtAnim để hiển thị HURT.
 */
public class Boss extends GameObject {
    private final Context ctx;

    // ===== Stats cơ bản của Boss =====
    public static final int BASE_HP     = 400;
    public static final int BASE_SPEED  = 2;
    public static final int BASE_DAMAGE = 15;

    // ===== Lock thời gian =====
    private static final long ATTACK_ANIM_MS = 1000L;
    private static final long DIE_ANIM_MS    = 1000L;

    private long animLockUntilMs = 0L;  // lock cho ATTACK
    private long dieEndAtMs      = 0L;  // để biết khi nào DIE xong

    // ===== HURT =====
    // Nếu có bộ sprite HURT, ta dùng hurtAnim; nếu không có, sẽ flash overlay đỏ.
    private static final long HURT_MS = 240L; // thời gian hiển thị hurt
    private long hurtUntilMs = 0L;
    private boolean hasHurtAnim = false;
    private SpriteAnim hurtAnim = null;

    // ===== State/render =====
    protected Map<State, SpriteAnim> anims = new HashMap<>();
    private float speedMultiplier = 1.0f;
    private long slowUntilMs = 0L;
    private int baseSpeed = BASE_SPEED;


    // Kích thước sprite gốc (nếu cần vẽ gì theo size gốc)
    private int spriteW, spriteH;

    public Boss(Context ctx, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.ctx = ctx;
        setState(State.IDLE);
        loadAnimations();
    }

    private void loadAnimations() {
        int[] idleIds = new int[] {
                R.drawable.b_idle_0, R.drawable.b_idle_1, R.drawable.b_idle_2, R.drawable.b_idle_3
        };
        int[] runIds  = new int[] {
                R.drawable.b_run_0, R.drawable.b_run_1, R.drawable.b_run_2, R.drawable.b_run_3,
                R.drawable.b_run_4, R.drawable.b_run_5, R.drawable.b_run_6, R.drawable.b_run_7
                , R.drawable.b_run_8, R.drawable.b_run_9
        };
        int[] atkIds  = new int[] {
                R.drawable.b_attack_0, R.drawable.b_attack_1, R.drawable.b_attack_2,
                R.drawable.b_attack_3, R.drawable.b_attack_4, R.drawable.b_attack_5,
                R.drawable.b_attack_6, R.drawable.b_attack_7, R.drawable.b_attack_8,
                R.drawable.b_attack_9
        };
        int[] dieIds  = new int[] {
                R.drawable.b_die_0, R.drawable.b_die_1, R.drawable.b_die_2, R.drawable.b_die_3,
                R.drawable.b_die_4, R.drawable.b_die_5, R.drawable.b_die_6, R.drawable.b_die_7,
                R.drawable.b_die_8, R.drawable.b_die_9
        };
        // ===== HURT (tuỳ chọn) =====
        int[] hurtIds = new int[] {
                R.drawable.b_hurt_0, R.drawable.b_hurt_1, R.drawable.b_hurt_2, R.drawable.b_hurt_3
                , R.drawable.b_hurt_4, R.drawable.b_hurt_5, R.drawable.b_hurt_6
        };

        Bitmap[] idleF = loadAndTrimFrames(ctx, idleIds);
        Bitmap[] runF  = loadAndTrimFrames(ctx, runIds);
        Bitmap[] atkF  = loadAndTrimFrames(ctx, atkIds);
        Bitmap[] dieF  = loadAndTrimFrames(ctx, dieIds);

        // HURT: cố gắng load, nếu lỗi / thiếu resource thì bỏ qua
        try {
            Bitmap[] hurtF = loadAndTrimFrames(ctx, hurtIds);
            if (hurtF != null && hurtF.length > 0 && hurtF[0] != null) {
                hasHurtAnim = true;
                hurtAnim = new SpriteAnim(hurtF, 100, false, w, h);
            }
        } catch (Throwable ignore) {
            hasHurtAnim = false;
            hurtAnim = null;
        }

        Bitmap sample = idleF[0];
        spriteW = (sample != null) ? sample.getWidth()  : w;
        spriteH = (sample != null) ? sample.getHeight() : h;

        final int DRAW_W = this.w; // giữ bằng kích thước truyền vào
        final int DRAW_H = this.h;

        SpriteAnim idle = new SpriteAnim(idleF, 120, true,  DRAW_W, DRAW_H);
        SpriteAnim run  = new SpriteAnim(runF,   80,  true,  DRAW_W, DRAW_H);
        SpriteAnim atk  = new SpriteAnim(atkF,  100, false, DRAW_W, DRAW_H);
        SpriteAnim die  = new SpriteAnim(dieF,  150, false, DRAW_W, DRAW_H);

        anims.put(State.IDLE,   idle);
        anims.put(State.RUN,    run);
        anims.put(State.ATTACK, atk);
        anims.put(State.DIE,    die);
    }

    // ====== Behavior ======
    private boolean isAnimLocked() {
        return System.currentTimeMillis() < animLockUntilMs || state == State.DIE;
    }
    private boolean isHurting() {
        return System.currentTimeMillis() < hurtUntilMs && state != State.DIE;
    }

    public void pursue(int targetX, int targetY, long dtMs) {
        if (state == State.DIE) { update(dtMs); return; }
        if (isAnimLocked())     { update(dtMs); return; } // ATTACK lock

        // reset slow khi hết hạn
        if (slowUntilMs > 0 && System.currentTimeMillis() > slowUntilMs) {
            speedMultiplier = 1.0f;
            slowUntilMs = 0L;
        }

        float realSpeed = baseSpeed * speedMultiplier;

        int dx = 0, dy = 0;
        if (targetX < x) dx = (int)-realSpeed;
        else if (targetX > x) dx = (int)realSpeed;
        if (targetY < y) dy = (int)-realSpeed;
        else if (targetY > y) dy = (int)realSpeed;


        if (dx < 0) setFacingLeft(true);
        else if (dx > 0) setFacingLeft(false);

        x += dx; y += dy;
        setState((dx != 0 || dy != 0) ? State.RUN : State.IDLE);

        update(dtMs);
    }

    public void startAttack() {
        if (state == State.DIE) return;
        setState(State.ATTACK);
        anims.get(State.ATTACK).reset(); // phát lại từ frame 0
        animLockUntilMs = System.currentTimeMillis() + ATTACK_ANIM_MS;
    }

    public void onDie() {
        setState(State.DIE);
        anims.get(State.DIE).reset();
        dieEndAtMs = System.currentTimeMillis() + DIE_ANIM_MS;
    }

    /** Gọi khi boss dính đòn để kích HURT */
    public void onHurt() {
        if (state == State.DIE) return;
        hurtUntilMs = System.currentTimeMillis() + HURT_MS;
        if (hasHurtAnim && hurtAnim != null) hurtAnim.reset();
    }

    public boolean isDieAnimDone() {
        return state == State.DIE && System.currentTimeMillis() >= dieEndAtMs;
    }

    // ====== Render/Update ======
    @Override
    public void update(long dtMs) {
        // reset slow khi hết hạn
        if (slowUntilMs > 0 && System.currentTimeMillis() > slowUntilMs) {
            speedMultiplier = 1.0f;
            slowUntilMs = 0L;
        }

        // Update anim đang active
        if (isHurting() && hasHurtAnim && hurtAnim != null) {
            hurtAnim.update(dtMs);
        } else {
            SpriteAnim sa = anims.get(state);
            if (sa != null) sa.update(dtMs);
        }
    }

    @Override
    public void draw(Canvas c, int cameraX, int cameraY, Paint p) {
        int drawX = x - cameraX;
        int drawY = y - cameraY;

        if (isHurting()) {
            if (hasHurtAnim && hurtAnim != null) {
                // Vẽ anim HURT riêng
                hurtAnim.draw(c, drawX, drawY, p);
            } else {
                // Flash đỏ nếu không có sprite HURT
                Paint flash = new Paint(p);
                flash.setColorFilter(new PorterDuffColorFilter(0x88FF4444, PorterDuff.Mode.SRC_ATOP));
                SpriteAnim sa = anims.get(state);
                if (sa != null) sa.draw(c, drawX, drawY, flash);
                return;
            }
        } else {
            SpriteAnim sa = anims.get(state);
            if (sa != null) sa.draw(c, drawX, drawY, p);
        }
    }


    // ====== Setters/Getters ======
    @Override
    public void setFacingLeft(boolean v) {
        super.setFacingLeft(v);
    }
    public int  getSpriteW() { return spriteW; }
    public int  getSpriteH() { return spriteH; }

    public State getState() {   // <<< thêm dòng này
        return state;
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
