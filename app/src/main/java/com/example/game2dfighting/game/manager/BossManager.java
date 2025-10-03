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
    private static final long BOSS_COOLDOWN_MS   = 1200L;   // cooldown đánh của boss
    private static final long PLAYER_COOLDOWN_MS = 600L;    // cooldown đánh của player
    private static final long BOSS_ATTACK_LOCK_MS = 1000L;
    private long nextBossAttackAtMs = 0L;
    private long nextPlayerAttackAtMs = 0L;
    private long bossAttackLockUntilMs = 0L;

    // HP boss
    private int bossHp = Boss.BASE_HP;

    // HP bar paints
    private final Paint hpBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpFg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpOutline = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Listener
    public interface KillListener { void onBossKilled(); }
    private KillListener killListener;
    public void setKillListener(KillListener l) { this.killListener = l; }

    public BossManager(Context ctx, int mapW, int mapH, long appearAfterMs) {
        this.ctx = ctx;
        this.mapW = mapW;
        this.mapH = mapH;
        this.bossStartAtMs = appearAfterMs;

        hpBg.setColor(0xFF333333);
        hpFg.setColor(0xFFFF0000);
        hpOutline.setStyle(Paint.Style.STROKE);
        hpOutline.setStrokeWidth(2.5f);
        hpOutline.setColor(0xFF8A2BE2);
    }

    public BossManager(Context ctx, int mapW, int mapH) {
        this(ctx, mapW, mapH, 10_000L);
    }

    // ===== Trạng thái =====
    public boolean isActive()    { return boss != null && boss.getState() != Boss.State.DIE; }
    public boolean isSpawned()   { return spawned; }
    public boolean isDefeated()  { return boss == null && spawned; }
    public Boss getBoss()        { return boss; }
    public int  getHp()          { return bossHp; }
    public int  getHpMax()       { return Boss.BASE_HP; }

    // ===== Spawn =====
    public void maybeSpawn() {
        if (spawned) return;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= bossStartAtMs) {
            spawned = true;
            int bw = 200, bh = 200;
            int bx = (mapW - bw) / 2;
            int by = (mapH - bh) / 3;

            boss = new Boss(ctx, bx, by, bw, bh);
            bossHp = Boss.BASE_HP;
            nextBossAttackAtMs = 0L;
            nextPlayerAttackAtMs = 0L;
        }
    }

    // ===== Update + Combat =====
    public void update(Player p, long dtMs) {
        if (boss == null) return;

        try { boss.update(dtMs); } catch (Throwable ignore) {}
        long now = System.currentTimeMillis();

        // Nếu đang DIE: chờ anim xong rồi mới remove + notify
        if (boss.getState() == Boss.State.DIE) {
            if (boss.isDieAnimDone()) {
                if (killListener != null) {
                    killListener.onBossKilled();
                    killListener = null; // gọi 1 lần duy nhất
                }
                boss = null;
            }
            return;
        }

        // Pursue trừ khi đang ATTACK
        if (boss.getState() != Boss.State.ATTACK) {
            int oldX = boss.x, oldY = boss.y;
            try { boss.pursue(p.x, p.y, dtMs); } catch (Throwable ignore) {}
            if (isOverlapCircle(p, boss, 0f)) {
                boss.x = oldX; boss.y = oldY;
                try { boss.forceIdle(); } catch (Throwable ignore) {}
            }
        } else {
            if (System.currentTimeMillis() >= bossAttackLockUntilMs) {
                try { boss.forceIdle(); } catch (Throwable ignore) {}
            }
        }

        // Combat
        float bossRadius   = Math.min(boss.w, boss.h) / 2f;
        float playerRadius = Math.min(p.w, p.h) / 2f;
        float trigger      = bossRadius + playerRadius + 18f;

        float ddx = (p.x + p.w/2f) - (boss.x + boss.w/2f);
        float ddy = (p.y + p.h/2f) - (boss.y + boss.h/2f);
        float dist2 = ddx*ddx + ddy*ddy;

        if (dist2 <= trigger*trigger) {
            if (now >= nextBossAttackAtMs && boss.getState() != Boss.State.ATTACK) {
                try { boss.startAttack(); } catch (Throwable ignore) {}
                bossAttackLockUntilMs = now + BOSS_ATTACK_LOCK_MS;
                try { p.takeDamage(Boss.BASE_DAMAGE); } catch (Throwable ignore) {}
                nextBossAttackAtMs = now + BOSS_COOLDOWN_MS;
            }

            if (now >= nextPlayerAttackAtMs) {
                try { p.startAttack(); } catch (Throwable ignore) {}
                int oldHp = bossHp;
                bossHp = Math.max(0, bossHp - 12);

                if (bossHp == 0) {
                    if (boss.getState() != Boss.State.DIE) {
                        try { boss.onDie(); } catch (Throwable ignore) {}
                        // ❌ không notify ở đây nữa
                    }
                } else if (bossHp < oldHp) {
                    try { boss.onHurt(); } catch (Throwable ignore) {}
                }
                nextPlayerAttackAtMs = now + PLAYER_COOLDOWN_MS;
            }
        }
    }

    // ===== Boss bị đạn bắn =====
    public void applyBulletHit(int dmg) {
        if (boss == null) return;
        if (boss.getState() == Boss.State.DIE) return;

        int oldHp = bossHp;
        bossHp = Math.max(0, bossHp - Math.max(0, dmg));

        if (bossHp == 0) {
            if (boss.getState() != Boss.State.DIE) {
                try { boss.onDie(); } catch (Throwable ignore) {}
            }
        } else if (bossHp < oldHp) {
            try { boss.onHurt(); } catch (Throwable ignore) {}
        }
    }

    // ===== Draw =====
    public void draw(Canvas c, int cameraX, int cameraY) {
        if (boss == null) return;
        boss.draw(c, cameraX, cameraY, new Paint(Paint.ANTI_ALIAS_FLAG));
        if (boss.getState() == Boss.State.DIE) return;

        float bossScreenX = boss.x - cameraX;
        float bossScreenY = boss.y - cameraY;
        float barW   = Math.max(180f, boss.w * 1.25f);
        float segH   = Math.max(6f,  boss.h * 0.065f);
        float spacing = segH * 1.0f;
        float gap    = 10f;

        float bx    = bossScreenX + (boss.w - barW) / 2f;
        float byTop = bossScreenY - gap - (3 * segH + 2 * spacing);

        float ratio = Math.max(0f, Math.min(1f, bossHp / (float) Boss.BASE_HP));
        int segments = 3;
        float totalUnits = ratio * segments;

        for (int i = 0; i < segments; i++) {
            float segTop = byTop + i * (segH + spacing);
            float segBottom = segTop + segH;
            float segLeft = bx;
            float segRight = bx + barW;
            c.drawRect(segLeft, segTop, segRight, segBottom, hpBg);

            float localFill = Math.max(0f, Math.min(1f, totalUnits - i));
            if (localFill > 0f) {
                float fillRight = segLeft + barW * localFill;
                c.drawRect(segLeft, segTop, fillRight, segBottom, hpFg);
            }
            c.drawRect(segLeft, segTop, segRight, segBottom, hpOutline);
        }
    }

    private boolean isOverlapCircle(Player p, Boss b, float marginPx) {
        float pcx = p.x + p.w / 2f, pcy = p.y + p.h / 2f;
        float bcx = b.x + b.w / 2f, bcy = b.y + b.h / 2f;
        float pr = Math.min(p.w, p.h) / 2f;
        float br = Math.min(b.w, b.h) / 2f;
        float minDist = pr + br + marginPx;
        float dx = pcx - bcx, dy = pcy - bcy;
        return (dx*dx + dy*dy) < (minDist * minDist);
    }
}
