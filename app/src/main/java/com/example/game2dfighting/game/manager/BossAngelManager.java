package com.example.game2dfighting.game.manager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.example.game2dfighting.game.entity.BossAngel;
import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.game.skill.BossBullet;
import com.example.game2dfighting.game.skill.LaserBeam;

/**
 * Quản lý Boss Angel trong Level 3.
 * Có 2 hình thái: Bình thường & Cuồng nộ (gọi activateFury()).
 */
public class BossAngelManager {
    private final Context ctx;
    private final int mapW, mapH;

    private BossAngel bossAngel = null;
    private boolean spawned = false;

    private int bossHp = 400;
    private static final int BASE_HP = 400;
    private static final int BASE_DMG = 20;

    private final Paint hpBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpFg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpOutline = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long respawnDelayMs = 0L;
    private long nextAttackAtMs = 0L;
    private static final long ATTACK_INTERVAL_MS = 1400L;

    public interface KillListener { void onBossKilled(); }
    private KillListener killListener;
    public void setKillListener(KillListener l) { this.killListener = l; }

    private float hpMul = 1f;
    private float dmgMul = 1f;
    public void setStatMultipliers(float hpMul, float dmgMul) {
        this.hpMul = Math.max(0.1f, hpMul);
        this.dmgMul = Math.max(0.1f, dmgMul);
    }

    public BossAngelManager(Context ctx, int mapW, int mapH) {
        this.ctx = ctx;
        this.mapW = mapW;
        this.mapH = mapH;

        hpBg.setColor(0xFF333333);
        hpFg.setColor(0xFF00BFFF);
        hpOutline.setStyle(Paint.Style.STROKE);
        hpOutline.setStrokeWidth(2.5f);
        hpOutline.setColor(0xFFFFFFFF);
    }

    public boolean isActive() { return bossAngel != null; }
    public BossAngel getBoss() { return bossAngel; }

    public void maybeSpawn() {
        if (spawned) return;
        int bw = 380, bh = 380;
        int bx = (mapW - bw) / 2;
        int by = (mapH - bh) / 2 - 200;
        bossAngel = new BossAngel(ctx, bx, by, bw, bh);
        bossHp = Math.round(BASE_HP * hpMul);
        spawned = true;

        // (Demo) sau 5 giây thì chuyển fury mode
//        new android.os.Handler().postDelayed(() -> {
//            if (bossAngel != null) bossAngel.activateFury();
//        }, 5000);
    }

    public void update(Player p, long dtMs) {
        if (bossAngel == null) return;
        bossAngel.update(dtMs, p, bossHp, Math.round(BASE_HP * hpMul));

        // 👉 Nếu boss đang invulnerable thì chỉ bay, không gây sát thương hoặc bị trúng đạn
        if (bossAngel.isInvulnerable()) {
            bossAngel.pursue(p.x, p.y, dtMs);
            return;
        }

        bossAngel.pursue(p.x, p.y, dtMs);

        long now = System.currentTimeMillis();
        if (distanceBetween(p, bossAngel) < 200f && now >= nextAttackAtMs) {
            p.takeDamage(Math.round(BASE_DMG * dmgMul));
            nextAttackAtMs = now + ATTACK_INTERVAL_MS;
        }

        if (bossHp <= 0) {
            if (killListener != null) killListener.onBossKilled();
            bossAngel = null;
            spawned = false;
        }

        // Đạn
        for (int i = bossAngel.getBullets().size() - 1; i >= 0; i--) {
            BossBullet b = bossAngel.getBullets().get(i);
            if (b.hit(p)) {
                p.takeDamage(10);
                b.alive = false;
            }
        }

        // Laser
        for (int i = bossAngel.getLasers().size() - 1; i >= 0; i--) {
            LaserBeam l = bossAngel.getLasers().get(i);
            if (l.hit(p)) {
                p.takeDamage(20);
                l.markHit();
            }
        }
    }

    public void applyBulletHit(int dmg) {
        if (bossAngel == null) return;
        if (bossAngel.isInvulnerable()) return; // ⚡ chặn damage khi chưa kích hoạt

        bossHp = Math.max(0, bossHp - Math.max(0, dmg));
        if (bossHp == 0 && killListener != null) {
            killListener.onBossKilled();
            bossAngel = null;
            spawned = false;
        }
    }



    public void draw(Canvas c, int offsetX, int offsetY) {
        if (bossAngel == null) return;
        bossAngel.draw(c, offsetX, offsetY, new Paint(Paint.ANTI_ALIAS_FLAG));

        float screenX = bossAngel.x - offsetX;
        float screenY = bossAngel.y - offsetY;

        float barW = bossAngel.w * 1.4f;
        float barH = 12f;
        float bx = screenX + (bossAngel.w - barW) / 2f;
        float by = screenY - 20f;

        // Đổi màu thanh máu khi fury
        hpFg.setColor(bossAngel.isFuryMode() ? 0xFFFF5555 : 0xFF00BFFF);

        float ratio = Math.max(0f, Math.min(1f, bossHp / (float) (BASE_HP * hpMul)));

        c.drawRect(bx, by, bx + barW, by + barH, hpBg);
        c.drawRect(bx, by, bx + barW * ratio, by + barH, hpFg);
        c.drawRect(bx, by, bx + barW, by + barH, hpOutline);
    }

    private float distanceBetween(Player p, BossAngel b) {
        float px = p.x + p.w / 2f;
        float py = p.y + p.h / 2f;
        float bx = b.x + b.w / 2f;
        float by = b.y + b.h / 2f;
        float dx = px - bx, dy = py - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
