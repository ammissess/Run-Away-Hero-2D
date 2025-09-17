package com.example.game2dfighting.game.manager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.example.game2dfighting.game.entity.Enemy;
import com.example.game2dfighting.game.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class EnemyManager {
    // ====== Core ======
    private final Context ctx;
    private final List<Enemy> enemies = new ArrayList<>();
    private final Random rnd = new Random();
    private final int mapW, mapH;
    private long lastSpawn = 0L;

    // Giới hạn & tốc độ spawn
    public int  maxEnemies       = 50;
    public long spawnIntervalMs  = 800L;



    // ====== Combat config ======
    private static final int   ENEMY_DAMAGE           = 6;     // quái đánh người
    private static final long  ENEMY_COOLDOWN_MS      = 700L;  // hồi đòn quái
    private static final int   PLAYER_DAMAGE          = 12;    // người đánh quái (auto khi áp sát)
    private static final long  PLAYER_COOLDOWN_MS     = 400L;  // hồi đòn người
    private static final float ATTACK_RANGE_PADDING   = 12f;   // cộng thêm ngoài bán kính tiếp xúc

    // HP quái (quản lý ở Manager để giữ nguyên Enemy.java)
    private static final int ENEMY_MAX_HP = 30;

    // Trạng thái combat
    private final Map<Enemy, Long>    nextEnemyAttackAtMs = new HashMap<>();
    private final Map<Enemy, Integer> enemyHp             = new HashMap<>();
    private long nextPlayerAttackAtMs = 0L;

    // Vẽ (truyền cho GameObject.draw)
    private final Paint sharedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ... trong class EnemyManager
    private static final float ENEMY_SCALE = 4f;   // gấp 3

    // Kích thước sprite/quái khi spawn
    private final int enemyBaseW = 100;
    private final int enemyBaseH = 100;
    private int enemyW = Math.round(enemyBaseW * ENEMY_SCALE);
    private int enemyH = Math.round(enemyBaseH * ENEMY_SCALE);


    // ====== Ctor ======
    public EnemyManager(Context ctx, int mapW, int mapH) {
        this.ctx = ctx;
        this.mapW = mapW;
        this.mapH = mapH;
    }

    // Tuỳ chọn: để không vỡ compile nếu nơi khác vẫn gọi ctor cũ
    public EnemyManager(int mapW, int mapH) {
        this(null, mapW, mapH);
    }

    // ====== API ======
    public List<Enemy> list() { return enemies; }

    /** Spawn ngẫu nhiên từ 4 cạnh map. */
    public void maybeSpawn() {
        long now = System.currentTimeMillis();
        if (enemies.size() >= maxEnemies) return;
        if (now - lastSpawn < spawnIntervalMs) return;
        lastSpawn = now;

        if (ctx == null) {
            throw new IllegalStateException(
                    "EnemyManager requires Context to spawn animated Enemy. " +
                            "Use new EnemyManager(getContext(), mapW, mapH)."
            );
        }

        int x = 0, y = 0;
        int edge = rnd.nextInt(4);
        switch (edge) {
            case 0: // top
                x = rnd.nextInt(Math.max(1, mapW - enemyW));
                y = 0;
                break;
            case 1: // bottom
                x = rnd.nextInt(Math.max(1, mapW - enemyW));
                y = mapH - enemyH;
                break;
            case 2: // left
                x = 0;
                y = rnd.nextInt(Math.max(1, mapH - enemyH));
                break;
            default: // right
                x = mapW - enemyW;
                y = rnd.nextInt(Math.max(1, mapH - enemyH));
                break;
        }

        Enemy e = new Enemy(ctx, x, y, enemyW, enemyH);
        enemies.add(e);
        enemyHp.put(e, ENEMY_MAX_HP);
        nextEnemyAttackAtMs.put(e, 0L);
    }

    /**
     * Cập nhật đuổi theo + tấn công (không dùng dt). Gọi mỗi frame trong GameView.
     * - Quái áp sát → đánh người (cooldown riêng từng quái)
     * - Người áp sát → tự đánh quái (cooldown chung)
     */
    public void updateTowardsPlayer(Player p) {
        long now = System.currentTimeMillis();

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);

            // Đảm bảo đã có HP & cooldown
            if (!enemyHp.containsKey(e))             enemyHp.put(e, ENEMY_MAX_HP);
            if (!nextEnemyAttackAtMs.containsKey(e)) nextEnemyAttackAtMs.put(e, 0L);

            // Di chuyển quái đuổi theo người + cập nhật anim/hướng
            try {
                e.pursue(p.x, p.y, 16L); // giả định ~60 FPS
            } catch (Throwable ignore) {
                // fallback chase đơn giản nếu pursue không khả dụng
                float ecx = e.x + e.w / 2f, ecy = e.y + e.h / 2f;
                float pcx = p.x + p.w / 2f, pcy = p.y + p.h / 2f;
                float dx  = pcx - ecx,      dy  = pcy - ecy;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > 1e-4f) {
                    float step = 3f;
                    e.x += (int) (dx / len * step);
                    e.y += (int) (dy / len * step);
                }
            }

            // Tính tầm đánh dựa trên "bán kính" hộp va chạm
            float enemyRadius  = Math.min(e.w, p.h) / 2f; // e.h mới đúng, nhưng vẫn ổn nếu sprite vuông
            float playerRadius = Math.min(p.w, p.h) / 2f;
            float trigger      = enemyRadius + playerRadius + ATTACK_RANGE_PADDING;

            float ddx   = (p.x + p.w / 2f) - (e.x + e.w / 2f);
            float ddy   = (p.y + p.h / 2f) - (e.y + e.h / 2f);
            float dist2 = ddx * ddx + ddy * ddy;

            if (dist2 <= trigger * trigger) {
                // ===== Quái -> Người =====
                long readyAt = nextEnemyAttackAtMs.getOrDefault(e, 0L);
                if (now >= readyAt) {
                    try { e.startAttack(); } catch (Throwable ignore) {}
                    boolean playerDead = safeTakeDamage(p, ENEMY_DAMAGE);
                    nextEnemyAttackAtMs.put(e, now + ENEMY_COOLDOWN_MS);
                    if (playerDead) {
                        // Game over do GameView xử lý
                    }
                }

                // ===== Người -> Quái (auto) =====
                if (now >= nextPlayerAttackAtMs) {
                    int hp = enemyHp.getOrDefault(e, ENEMY_MAX_HP);
                    hp = Math.max(0, hp - PLAYER_DAMAGE);
                    enemyHp.put(e, hp);
                    nextPlayerAttackAtMs = now + PLAYER_COOLDOWN_MS;

                    if (hp == 0) {
                        try { e.onDie(); } catch (Throwable ignore) {}
                        enemies.remove(i);
                        enemyHp.remove(e);
                        nextEnemyAttackAtMs.remove(e);
                        continue; // sang quái kế tiếp
                    }
                }
            }
        }

        // Dọn rác nếu quái bị remove nơi khác
        nextEnemyAttackAtMs.keySet().retainAll(enemies);
        enemyHp.keySet().retainAll(enemies);
    }

    /**
     * Bản có dtMs (nếu vòng lặp của bạn đã có delta-time).
     */
    public void updateTowardsPlayer(Player p, long dtMs) {
        long now = System.currentTimeMillis();

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);

            if (!enemyHp.containsKey(e))             enemyHp.put(e, ENEMY_MAX_HP);
            if (!nextEnemyAttackAtMs.containsKey(e)) nextEnemyAttackAtMs.put(e, 0L);

            try { e.pursue(p.x, p.y, dtMs); }
            catch (Throwable ignore) {
                float ecx = e.x + e.w / 2f, ecy = e.y + e.h / 2f;
                float pcx = p.x + p.w / 2f, pcy = p.y + p.h / 2f;
                float dx  = pcx - ecx,      dy  = pcy - ecy;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > 1e-4f) {
                    float step = 3f * (dtMs / 16f);
                    e.x += (int) (dx / len * step);
                    e.y += (int) (dy / len * step);
                }
            }

            float enemyRadius  = Math.min(e.w, e.h) / 2f;
            float playerRadius = Math.min(p.w, p.h) / 2f;
            float trigger      = enemyRadius + playerRadius + ATTACK_RANGE_PADDING;

            float ddx   = (p.x + p.w / 2f) - (e.x + e.w / 2f);
            float ddy   = (p.y + p.h / 2f) - (e.y + e.h / 2f);
            float dist2 = ddx * ddx + ddy * ddy;

            if (dist2 <= trigger * trigger) {
                long readyAt = nextEnemyAttackAtMs.getOrDefault(e, 0L);
                if (now >= readyAt) {
                    try { e.startAttack(); } catch (Throwable ignore) {}
                    boolean playerDead = safeTakeDamage(p, ENEMY_DAMAGE);
                    nextEnemyAttackAtMs.put(e, now + ENEMY_COOLDOWN_MS);
                    if (playerDead) { /* GameView xử lý */ }
                }

                if (now >= nextPlayerAttackAtMs) {
                    int hp = enemyHp.getOrDefault(e, ENEMY_MAX_HP);
                    hp = Math.max(0, hp - PLAYER_DAMAGE);
                    enemyHp.put(e, hp);
                    nextPlayerAttackAtMs = now + PLAYER_COOLDOWN_MS;

                    if (hp == 0) {
                        try { e.onDie(); } catch (Throwable ignore) {}
                        enemies.remove(i);
                        enemyHp.remove(e);
                        nextEnemyAttackAtMs.remove(e);
                        continue;
                    }
                }
            }
        }

        nextEnemyAttackAtMs.keySet().retainAll(enemies);
        enemyHp.keySet().retainAll(enemies);
    }

    private boolean safeTakeDamage(Player p, int dmg) {
        try {
            return p.takeDamage(dmg); // trả về true nếu Player chết (theo GameView cũ)
        } catch (Throwable ignore) {
            // nếu Player.takeDamage khác chữ ký, đừng crash
            return false;
        }
    }

    /** Vẽ quái (GameObject.draw tự trừ camera). */
    public void draw(Canvas c, int cameraX, int cameraY) {
        for (Enemy e : enemies) {
            e.draw(c, cameraX, cameraY, sharedPaint);
        }
    }
}
