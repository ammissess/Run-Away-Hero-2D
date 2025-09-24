package com.example.game2dfighting.game.manager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.example.game2dfighting.game.entity.Boss;
import com.example.game2dfighting.game.entity.Player;

/**
 * Quản lý Boss riêng: spawn theo thời gian (mặc định 50s), update di chuyển, combat, hurt, vẽ HP bar.
 */
public class BossManager {
    private final Context ctx;
    private final int mapW, mapH;

    private Boss boss = null;
    private boolean spawned = false;

    // Thời điểm xuất hiện boss (ms sau khi tạo manager)
    private final long bossStartAtMs;
    private final long startTime = System.currentTimeMillis();

    // Combat timing
    private static final long BOSS_COOLDOWN_MS   = 2000L;   // cooldown đánh của boss
    private static final long PLAYER_COOLDOWN_MS = 400L;   // cooldown đánh của player

    private long nextBossAttackAtMs = 0L;
    private long nextPlayerAttackAtMs = 0L;

    // HP boss quản lý ở Manager
    private int bossHp = Boss.BASE_HP;

    // Vẽ HP bar boss
    private final Paint hpBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpFg = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Dùng ctor này nếu muốn đổi thời gian xuất hiện (ms). */
    public BossManager(Context ctx, int mapW, int mapH, long appearAfterMs) {
        this.ctx = ctx;
        this.mapW = mapW;
        this.mapH = mapH;
        this.bossStartAtMs = appearAfterMs; // ví dụ: 50_000L (50s)
        hpBg.setColor(0xFF333333);
        hpFg.setColor(0xFFFFCC00);
    }

    /** Ctor mặc định: boss xuất hiện sau 50 giây. */
    public BossManager(Context ctx, int mapW, int mapH) {
        this(ctx, mapW, mapH, 10_000L);
    }

    // ====== Trạng thái / Getter ======
    public boolean isActive()    { return boss != null && boss.getState() != Boss.State.DIE; }
    public boolean isSpawned()   { return spawned; }
    public boolean isDefeated()  { return boss == null && spawned; }
    public Boss getBoss()        { return boss; }
    public int  getHp()          { return bossHp; }
    public int  getHpMax()       { return Boss.BASE_HP; }

    // ====== Spawn theo thời gian ======
    public void maybeSpawn() {
        if (spawned) return;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= bossStartAtMs) {
            spawned = true;

            // Spawn giữa map (tuỳ chỉnh)
            int bw = 200;
            int bh = 200;
            int bx = (mapW - bw) / 2;
            int by = (mapH - bh) / 3;

            boss = new Boss(ctx, bx, by, bw, bh);
            bossHp = Boss.BASE_HP;
            nextBossAttackAtMs = 0L;
            nextPlayerAttackAtMs = 0L;
        }
    }

    // ====== Update + Combat cơ bản ======
    public void update(Player p, long dtMs) {
        if (boss == null) return;

        // Di chuyển đuổi kiểu cơ bản & tôn trọng lock anim
        boss.pursue(p.x, p.y, dtMs);

        // Combat: vào tầm là đánh (giống Enemy cơ bản)
        float bossRadius   = Math.min(boss.w, boss.h) / 2f;
        float playerRadius = Math.min(p.w, p.h) / 2f;
        float trigger      = bossRadius + playerRadius + 18f; // boss rộng hơn một chút

        float ddx   = (p.x + p.w / 2f) - (boss.x + boss.w / 2f);
        float ddy   = (p.y + p.h / 2f) - (boss.y + boss.h / 2f);
        float dist2 = ddx * ddx + ddy * ddy;

        long now = System.currentTimeMillis();
        if (dist2 <= trigger * trigger) {
            // Boss -> Player
            if (now >= nextBossAttackAtMs && boss.getState() != Boss.State.DIE) {
                boss.startAttack();
                try { p.takeDamage(Boss.BASE_DAMAGE); } catch (Throwable ignore) {}
                nextBossAttackAtMs = now + BOSS_COOLDOWN_MS;
            }

            // Player -> Boss (auto-attack)
            if (now >= nextPlayerAttackAtMs) {
                try { p.startAttack(); } catch (Throwable ignore) {}

                int oldHp = bossHp;
                bossHp = Math.max(0, bossHp - 12); // TODO: thay bằng damage thực từ Player nếu có
                if (bossHp < oldHp) {
                    boss.onHurt(); // bật HURT anim/flash
                }

                nextPlayerAttackAtMs = now + PLAYER_COOLDOWN_MS;
                if (bossHp == 0) {
                    boss.onDie();
                }
            }
        }

        // Remove khi DIE anim xong
        if (boss.getState() == Boss.State.DIE && boss.isDieAnimDone()) {
            boss = null;
        }
    }

    // ====== Nhận sát thương từ đạn (gọi từ GameView khi bullet trúng Boss) ======
    public void applyBulletHit(int dmg) {
        if (boss == null || boss.getState() == Boss.State.DIE) return;
        int oldHp = bossHp;
        bossHp = Math.max(0, bossHp - Math.max(0, dmg));
        if (bossHp < oldHp) boss.onHurt();
        if (bossHp == 0) boss.onDie();
    }

    // ====== Vẽ Boss + thanh máu ======
    public void draw(Canvas c, int cameraX, int cameraY) {
        if (boss == null) return;

        // Vẽ boss
        boss.draw(c, cameraX, cameraY, new Paint(Paint.ANTI_ALIAS_FLAG));

        // Vẽ HP bar boss trên đầu màn hình (theo camera)
        final float marginTop = 24f;
        final float barW = Math.max(220f, mapW * 0.6f);
        final float barH = 18f;

        float x = (mapW - barW) / 2f - cameraX;
        float y = marginTop - cameraY;

        float ratio = Math.max(0f, Math.min(1f, bossHp / (float) Boss.BASE_HP));

        c.drawRect(x, y, x + barW, y + barH, hpBg);
        c.drawRect(x, y, x + barW * ratio, y + barH, hpFg);
    }
}
