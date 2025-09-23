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
 * Yêu cầu tối thiểu ở GameObject:
 * - Map<State, SpriteAnim> anims
 * - protected State state;  // hoặc getter/setter tương đương
 * - void setState(State s), void setFacingLeft(boolean b), void update(long dtMs)
 * - void draw(...)
 * - Field w,h,x,y (int)
 *
 * Nếu enum State định nghĩa ở nơi khác, giữ nguyên tham chiếu State.
 */
public class Enemy extends GameObject {
    private final Context ctx;
    private int speed = 4;

    // Kích thước sprite gốc (để tham chiếu, vẽ HP bar chính xác theo sprite)
    private int spriteW;
    private int spriteH;

    // ==== ANIM LOCK ====
    // thời lượng anim ATTACK (ms) – chỉnh theo bộ sprite của bạn
    private static final long ATTACK_ANIM_MS = 450L;
    private long animLockUntilMs = 0L;

    // Thời lượng DIE mặc định (fallback). Bộ die hiện có 4 frame x 120ms ≈ 480ms.
    // Ta sẽ cố đọc thời lượng thực từ SpriteAnim, còn không thì fallback này sẽ chạy.
    private static final long DIE_ANIM_MS = 480L;
    private long dieEndAtMs = 0L;

    private boolean isAnimLocked() {
        // Khóa khi đang trong thời gian ATTACK, hoặc khi đã DIE thì không cho đổi state
        return System.currentTimeMillis() < animLockUntilMs || state == State.DIE;
    }

    // Trung tâm để tính khoảng cách/điểm bám
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

        // Load & TRIM để bỏ padding rỗng
        Bitmap[] idleF = loadAndTrimFrames(ctx, idleIds);
        Bitmap[] runF  = loadAndTrimFrames(ctx, runIds);
        Bitmap[] atkF  = loadAndTrimFrames(ctx, atkIds);
        Bitmap[] dieF  = loadAndTrimFrames(ctx, dieIds);

        // Kích thước sprite gốc sau khi TRIM
        Bitmap sample = idleF[0];
        spriteW = (sample != null) ? sample.getWidth()  : w;
        spriteH = (sample != null) ? sample.getHeight() : h;

        this.w = 100;
        this.h = 100;

        // Có thể đặt 1 biến chung:
        final int DRAW_W = 100;
        final int DRAW_H = 100;

        // Tạo anim đã TRIM, scale về w,h hiển thị
        SpriteAnim idle = new SpriteAnim(idleF, 140, true,  DRAW_W, DRAW_H);
        SpriteAnim run  = new SpriteAnim(runF,   90,  true,  DRAW_W, DRAW_H);
        SpriteAnim atk  = new SpriteAnim(atkF,  100, false, DRAW_W, DRAW_H);
        SpriteAnim die  = new SpriteAnim(dieF,  120, false, DRAW_W, DRAW_H);

        anims.put(State.IDLE,   idle);
        anims.put(State.RUN,    run);
        anims.put(State.ATTACK, atk);
        anims.put(State.DIE,    die);
    }


    /**
     * Đuổi theo mục tiêu – tôn trọng anim lock để không ghi đè ATTACK/DIE.
     * @param targetX target world X
     * @param targetY target world Y
     * @param dtMs    delta time (ms) của vòng lặp game
     */
    public void pursue(int targetX, int targetY, long dtMs) {
        if (state == State.DIE) {
            // Khi đã chết: vẫn update để anim DIE chạy; không di chuyển
            super.update(dtMs);
            return;
        }

        // Nếu đang khóa anim (attack/hurt…), không đổi state/không di chuyển
        if (isAnimLocked()) {
            super.update(dtMs);
            return;
        }

        int dx = 0, dy = 0;
        if (targetX < x) dx = -speed; else if (targetX > x) dx = speed;
        if (targetY < y) dy = -speed; else if (targetY > y) dy = speed;

        if (dx < 0) setFacingLeft(true);
        else if (dx > 0) setFacingLeft(false);

        x += dx;
        y += dy;

        if (dx != 0 || dy != 0) setState(State.RUN);
        else setState(State.IDLE);

        super.update(dtMs);
    }

    /** Bắt đầu ATTACK: đặt state và khóa anim theo ATTACK_ANIM_MS. */
    public void startAttack() {
        if (state == State.DIE) return; // đã chết thì không tấn công
        setState(State.ATTACK);
        animLockUntilMs = System.currentTimeMillis() + ATTACK_ANIM_MS;
    }

    /**
     * Kích hoạt DIE:
     * - Đổi state sang DIE
     * - Cố gắng lấy thời lượng thật của anim DIE từ SpriteAnim (nếu có API), fallback DIE_ANIM_MS.
     * - Ghi nhận mốc thời gian kết thúc anim DIE để EnemyManager có thể xóa đúng lúc.
     */
    public void onDie() {
        setState(State.DIE);

        long dur = DIE_ANIM_MS; // fallback
        try {
            SpriteAnim dieAnim = anims.get(State.DIE);
            if (dieAnim != null) {
                // Nếu SpriteAnim có API tổng thời gian, sử dụng; nếu không, cố gắng tự suy ra.
                // GỢI Ý: thêm hàm trong SpriteAnim:
                //   public long getTotalDurationMs() { return (long) frames.length * frameDurationMs; }
                try {
                    dur = (long) SpriteAnim.class.getMethod("getTotalDurationMs").invoke(dieAnim);
                } catch (NoSuchMethodException nsme) {
                    // Tự suy đoán qua các API thông dụng khác nếu có (không bắt buộc)
                    try {
                        int frameCount = (int) SpriteAnim.class.getMethod("getFrameCount").invoke(dieAnim);
                        int frameMs    = (int) SpriteAnim.class.getMethod("getFrameDurationMs").invoke(dieAnim);
                        dur = Math.max(dur, (long) frameCount * frameMs);
                    } catch (Throwable ignore2) {
                        // giữ nguyên fallback nếu SpriteAnim không có các API trên
                    }
                }
            }
        } catch (Throwable ignore) {
            // giữ nguyên fallback
        }

        dieEndAtMs = System.currentTimeMillis() + dur;
    }

    /** Đã kết thúc anim DIE chưa (để EnemyManager remove). */
    public boolean isDieAnimDone() {
        return state == State.DIE && System.currentTimeMillis() >= dieEndAtMs;
    }

    /** Thời lượng DIE fallback (có thể khác thời lượng thực nếu SpriteAnim không cung cấp). */
    public long getDieAnimMs() { return DIE_ANIM_MS; }

    /** Trả về state hiện tại (tiện cho EnemyManager). */
    public State getState() { return state; }

    // Getter kích thước sprite gốc (nếu bạn muốn vẽ HP bar bằng kích thước sprite thật)
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

    private static Bitmap[] loadAndTrimFrames(Context ctx, int[] ids) {
        Bitmap[] frames = SpriteAnim.loadFrames(ctx, ids);
        for (int i = 0; i < frames.length; i++) {
            frames[i] = trimTransparent(frames[i]);
        }
        return frames;
    }

}
