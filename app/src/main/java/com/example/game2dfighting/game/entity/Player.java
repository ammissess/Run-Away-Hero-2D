package com.example.game2dfighting.game.entity;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.core.GameObject;
import com.example.game2dfighting.game.core.SpriteAnim;
import com.example.game2dfighting.game.skill.Fireball;
import com.example.game2dfighting.game.skill.IceSpike;

/**
 * Player: vẫn di chuyển khi đang ATTACK hoặc HURT.
 * Tách lock animation (attackUntilMs/hurtUntilMs) khỏi việc cập nhật vị trí.
 */
public class Player extends GameObject {

    // ==== Input flags (GameView sẽ set mỗi frame) ====
    public boolean up, down, left, right;

    // ==== Core ====
    private final Context ctx;
    private int speed = 6;

    // ==== HP ====
    private int maxHp = 200;
    private int hp    = 200;

    // ==== Mana & Energy (thêm để khớp GameView) ====
    private int maxMana   = 50;
    private int mana      = 0;

    private int maxEnergy = 100;
    private int energy    = 100;

    // ==== Sprite gốc (tham chiếu nếu cần) ====
    private int spriteW;
    private int spriteH;

    // ==== Timers cho animation (chỉ khóa đổi state, KHÔNG khóa di chuyển) ====
    private static final long ATTACK_MS = 400L;
    private static final long HURT_MS   = 150L;
    private long attackUntilMs   = 0L;
    private long hurtUntilMs     = 0L;

    // (Tùy chọn) stun cứng thật sự – nếu muốn chặn di chuyển
    private long hardStunUntilMs = 0L;

    // ==== DIE ====
    private static final long DIE_ANIM_MS = 600L;
    private long dieEndAtMs = 0L;

    // ==== Convenience ====
    private boolean inAttack() { return System.currentTimeMillis() < attackUntilMs; }
    private boolean inHurt()   { return System.currentTimeMillis() < hurtUntilMs;   }
    private boolean isDead()   { return state == State.DIE; }

    /** Cho phép di chuyển khi không DIE và không hard-stun. */
    private boolean canMove() {
        return !isDead() && System.currentTimeMillis() >= hardStunUntilMs;
    }

    // Trung tâm (để GameView dùng camera follow)
    public float centerX() { return x + w/2f; }
    public float centerY() { return y + h/2f; }

    public Player(Context ctx, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.ctx = ctx;
        loadAnimations();
        setState(State.IDLE);
    }

    private void loadAnimations() {
        int[] idleIds = new int[]{
                R.drawable.p_idle_0, R.drawable.p_idle_1, R.drawable.p_idle_2,
                R.drawable.p_idle_3, R.drawable.p_idle_4, R.drawable.p_idle_5
        };
        int[] runIds = new int[]{
                R.drawable.p_run_0, R.drawable.p_run_1, R.drawable.p_run_2,
                R.drawable.p_run_3, R.drawable.p_run_4, R.drawable.p_run_5,
                R.drawable.p_run_6, R.drawable.p_run_7
        };
        int[] atkIds = new int[]{
                R.drawable.p_attack_0, R.drawable.p_attack_1, R.drawable.p_attack_2,
                R.drawable.p_attack_3, R.drawable.p_attack_4, R.drawable.p_attack_5
        };
        int[] hurtIds = new int[]{
                R.drawable.p_hurt_0, R.drawable.p_hurt_1, R.drawable.p_hurt_2
        };
        int[] dieIds = new int[]{
                R.drawable.p_die_0, R.drawable.p_die_1, R.drawable.p_die_2,
                R.drawable.p_die_3,
        };

        // Load & TRIM các frame để bỏ viền rỗng
        Bitmap[] idleF = loadAndTrimFrames(ctx, idleIds);
        Bitmap[] runF  = loadAndTrimFrames(ctx, runIds);
        Bitmap[] atkF  = loadAndTrimFrames(ctx, atkIds);
        Bitmap[] hurtF = loadAndTrimFrames(ctx, hurtIds);
        Bitmap[] dieF  = loadAndTrimFrames(ctx, dieIds);

        // Kích thước sprite gốc sau khi TRIM (để tham chiếu)
        Bitmap sample = idleF[0];
        spriteW = (sample != null) ? sample.getWidth()  : w;
        spriteH = (sample != null) ? sample.getHeight() : h;

        this.w = 100;
        this.h = 100;

        // Có thể đặt 1 biến chung:
        final int DRAW_W = 100;
        final int DRAW_H = 100;

        // Tạo anim: SpriteAnim sẽ scale các frame đã TRIM về kích thước hiển thị w,h
        SpriteAnim idle = new SpriteAnim(idleF, 120, true,  DRAW_W, DRAW_H);
        SpriteAnim run  = new SpriteAnim(runF,   80,  true,  DRAW_W, DRAW_H);
        SpriteAnim atk  = new SpriteAnim(atkF,   90,  false, DRAW_W, DRAW_H);
        SpriteAnim hurt = new SpriteAnim(hurtF,  90,  false, DRAW_W, DRAW_H);
        SpriteAnim die  = new SpriteAnim(dieF,  120,  false, DRAW_W, DRAW_H);

        anims.put(State.IDLE,   idle);
        anims.put(State.RUN,    run);
        anims.put(State.ATTACK, atk);
        anims.put(State.HURT,   hurt);
        anims.put(State.DIE,    die);
    }


    /**
     * Update mỗi frame: LUÔN cập nhật vị trí nếu canMove() == true,
     * còn state hiển thị ưu tiên ATTACK > HURT > RUN > IDLE.
     */
    @Override
    public void update(long dtMs) {
        int vx = 0, vy = 0;

        // 1) DI CHUYỂN – KHÔNG bị ảnh hưởng bởi ATTACK/HURT (trừ khi hard-stun/DIE)
        if (canMove()) {
            if (left)  vx -= speed;
            if (right) vx += speed;
            if (up)    vy -= speed;
            if (down)  vy += speed;

            // Nếu dùng dtMs theo 60fps ~ 16ms, cho mượt:
            float mul = (dtMs <= 0) ? 1f : (dtMs / 16f);
            x += (int) (vx * mul);
            y += (int) (vy * mul);

            if (vx < 0) setFacingLeft(true);
            else if (vx > 0) setFacingLeft(false);
        }

        // (Tùy chọn) Cancel window: nhấn hướng để hủy đòn ở cuối frame attack
        if (inAttack() && (left || right || up || down)) {
            long remain = attackUntilMs - System.currentTimeMillis();
            if (remain < 220) { // ví dụ cho phép cancel trong 220ms cuối
                attackUntilMs = 0L;
            }
        }

        // 2) CHỌN STATE HIỂN THỊ – ƯU TIÊN
        if (isDead()) {
            // Khi DIE, không đổi state nữa, chỉ để anim DIE chạy
            super.update(dtMs);
            return;
        }

        if (inHurt()) {
            setState(State.HURT);
        } else if (inAttack()) {
            setState(State.ATTACK);
        } else if (vx != 0 || vy != 0) {
            setState(State.RUN);
        } else {
            setState(State.IDLE);
        }

        super.update(dtMs);
    }

    // ==== API combat tương thích với EnemyManager ====

    /** Gọi khi người chơi bấm tấn công / auto-attack. */
    public void startAttack() {
        if (isDead()) return;
        attackUntilMs = System.currentTimeMillis() + ATTACK_MS;
        // KHÔNG khóa di chuyển
    }

    public Fireball shootFireballToward(float targetX, float targetY, int mapW, int mapH, Context ctx){
        // Tâm người chơi
        float px = this.x + this.w/2f;
        float py = this.y + this.h/2f;

        // Hướng bay (chuẩn hoá)
        float dx = targetX - px;
        float dy = targetY - py;
        float len = (float)Math.sqrt(dx*dx + dy*dy);
        if (len < 1f) len = 1f;
        dx /= len; dy /= len;

        // Tốc độ & vị trí nòng súng
        float speed = 300f; // px/s — tuỳ bạn
        float vx = dx * speed;
        float vy = dy * speed;

        float muzzle = Math.min(this.w, this.h) * 0.4f;
        float sx = px + dx * muzzle;
        float sy = py + dy * muzzle;

        return new Fireball(sx, sy, vx, vy, mapW, mapH, ctx);
    }

    public IceSpike shootIceSpikeToward(float tx, float ty, int mapW, int mapH, Context ctx) {
        float px = centerX();
        float py = centerY();
        float dx = tx - px;
        float dy = ty - py;
        float len = (float)Math.sqrt(dx*dx + dy*dy);
        if (len == 0) return null;

        float speed = 800f; // tốc độ bay
        float vx = dx / len * speed;
        float vy = dy / len * speed;

        return new IceSpike(px, py, vx, vy, mapW, mapH, ctx);
    }


    /**
     * Player nhận sát thương. Trả về true nếu chết (để GameView xử lý).
     */
    public boolean takeDamage(int dmg) {
        if (isDead()) return true;

        hp = Math.max(0, hp - Math.max(0, dmg));
        if (hp == 0) {
            onDie();
            return true;
        } else {
            // Bị thương: chỉ khóa anim HURT, không chặn move
            hurtUntilMs = System.currentTimeMillis() + HURT_MS;

            // Nếu bạn muốn stun cứng 1 chút, mở dòng dưới:
            // hardStunUntilMs = System.currentTimeMillis() + 120L;
            return false;
        }
    }

    private void onDie() {
        setState(State.DIE);

        long dur = DIE_ANIM_MS; // fallback
        try {
            SpriteAnim dieAnim = anims.get(State.DIE);
            if (dieAnim != null) {
                try {
                    // Nếu SpriteAnim có tổng thời gian
                    dur = (long) SpriteAnim.class.getMethod("getTotalDurationMs").invoke(dieAnim);
                } catch (NoSuchMethodException nsme) {
                    try {
                        int frameCount = (int) SpriteAnim.class.getMethod("getFrameCount").invoke(dieAnim);
                        int frameMs    = (int) SpriteAnim.class.getMethod("getFrameDurationMs").invoke(dieAnim);
                        dur = Math.max(dur, (long) frameCount * frameMs);
                    } catch (Throwable ignore) {}
                }
            }
        } catch (Throwable ignore) {}

        dieEndAtMs = System.currentTimeMillis() + dur;
    }

    /** Nếu bạn cần biết khi nào DIE anim xong. */
    public boolean isDieAnimDone() {
        return state == State.DIE && System.currentTimeMillis() >= dieEndAtMs;
    }

    // ==== Getter/Setter phụ trợ ====

    // HP
    public int  getHp()     { return hp; }
    public int  getMaxHp()  { return maxHp; }
    public void setHp(int v){ hp = Math.max(0, Math.min(maxHp, v)); }
    public void setMaxHp(int v){ maxHp = Math.max(1, v); hp = Math.min(hp, maxHp); }

    // Speed
    public int  getSpeed()  { return speed; }
    public void setSpeed(int v){ speed = Math.max(1, v); }

    // Sprite gốc (tham chiếu)
    public int getSpriteW() { return spriteW; }
    public int getSpriteH() { return spriteH; }

    public long getAttackDurationMs() { return ATTACK_MS; }

    // === Mana (khớp GameView HUD & logic addMana) ===
    public int  getMana()         { return mana; }
    public int  getMaxMana()      { return maxMana; }
    public void setMana(int v)    { mana = Math.max(0, Math.min(maxMana, v)); }
    public void setMaxMana(int v) { maxMana = Math.max(0, v); mana = Math.min(mana, maxMana); }
    public void addMana(int a)    { setMana(mana + Math.max(0, a)); }

    // === Energy (khớp GameView HUD) ===
    public int  getEnergy()         { return energy; }
    public int  getMaxEnergy()      { return maxEnergy; }
    public void setEnergy(int v)    { energy = Math.max(0, Math.min(maxEnergy, v)); }
    public void setMaxEnergy(int v) { maxEnergy = Math.max(0, v); energy = Math.min(energy, maxEnergy); }

    // --- Utils: cắt khung trong suốt và load+trim một mảng frames ---
    private static Bitmap trimTransparent(Bitmap src) {
        if (src == null) return null;
        final int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);

        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int a = (px[row + x] >>> 24) & 0xFF;  // alpha
                if (a != 0) {
                    if (x < left)   left = x;
                    if (x > right)  right = x;
                    if (y < top)    top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < left || bottom < top) return src; // toàn bộ rỗng
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
