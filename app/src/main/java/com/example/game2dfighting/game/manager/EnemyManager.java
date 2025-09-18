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
    public int  maxEnemies       = 5;
    public long spawnIntervalMs  = 800L;
    private static final float STOP_GAP_PX = 20f; // quái dừng cách mép player 20 px

    // ====== Combat config ======
    private static final int   ENEMY_DAMAGE           = 3;      // quái đánh người
    private static final long  ENEMY_COOLDOWN_MS      = 1200L;  // hồi đòn quái
    private static final int   PLAYER_DAMAGE          = 12;     // người đánh quái (auto khi áp sát)
    private static final long  PLAYER_COOLDOWN_MS     = 400L;   // hồi đòn người
    private static final float ATTACK_RANGE_PADDING   = 12f;    // cộng thêm ngoài bán kính tiếp xúc

    // HP quái (quản lý ở Manager để giữ nguyên Enemy.java)
    private static final int ENEMY_MAX_HP = 30;

    private final Paint hpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Trạng thái combat
    private final Map<Enemy, Long>    nextEnemyAttackAtMs = new HashMap<>();
    private final Map<Enemy, Integer> enemyHp             = new HashMap<>();
    private long nextPlayerAttackAtMs = 0L;

    // Vẽ (truyền cho GameObject.draw)
    private final Paint sharedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Scale enemy khi spawn
    private static final float ENEMY_SCALE = 4f;

    // Kích thước sprite/quái khi spawn
    private final int enemyBaseW = 100;
    private final int enemyBaseH = 100;
    private int enemyW = Math.round(enemyBaseW * ENEMY_SCALE);
    private int enemyH = Math.round(enemyBaseH * ENEMY_SCALE);

    // ===== Combat listener =====
    public interface CombatListener {
        void onPlayerHit();  // quái đánh trúng người
    }
    private CombatListener combatListener = null;
    public void setCombatListener(CombatListener l) { this.combatListener = l; }


    // ====== Ctor ======
    public EnemyManager(Context ctx, int mapW, int mapH) {
        this.ctx = ctx;
        this.mapW = mapW;
        this.mapH = mapH;

        // --- HP bar paints ---
        hpPaint.setColor(0xFFFF0000);     // Đỏ
        hpPaint.setStyle(Paint.Style.FILL);

        hpBgPaint.setColor(0xFF555555);   // Xám nền
        hpBgPaint.setStyle(Paint.Style.FILL);
    }

    // Tuỳ chọn: để không vỡ compile nếu nơi khác vẫn gọi ctor cũ
    public EnemyManager(int mapW, int mapH) {
        this(null, mapW, mapH);
    }

    // ====== API ======
    public List<Enemy> list() { return enemies; }

//    public List<Enemy> getEnemies() {
//        return enemies;
//    }

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
     * Cập nhật đuổi theo + tấn công (bản có dtMs).
     * - Quái áp sát → đánh người (cooldown riêng từng quái)
     * - Người áp sát → tự đánh quái (cooldown chung)
     */
    public void updateTowardsPlayer(Player p, long dtMs) {
        long now = System.currentTimeMillis();

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);

            if (!enemyHp.containsKey(e))             enemyHp.put(e, ENEMY_MAX_HP);
            if (!nextEnemyAttackAtMs.containsKey(e)) nextEnemyAttackAtMs.put(e, 0L);

            // Pursue (Enemy.pursue đã tự chặn nếu đang DIE)
            try { e.pursue(p.x, p.y, dtMs); }
            catch (Throwable ignore) {
                // Fallback di chuyển đơn giản
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

            // --- Combat trigger ---
            float enemyRadius  = Math.min(e.w, e.h) / 2f;
            float playerRadius = Math.min(p.w, p.h) / 2f;
            float trigger      = enemyRadius + playerRadius + ATTACK_RANGE_PADDING;

            float ddx   = (p.x + p.w / 2f) - (e.x + e.w / 2f);
            float ddy   = (p.y + p.h / 2f) - (e.y + e.h / 2f);
            float dist2 = ddx * ddx + ddy * ddy;

            if (dist2 <= trigger * trigger) {

                // NEW: bỏ qua toàn bộ combat với quái đã DIE
                boolean enemyIsDying = false;
                try {
                    enemyIsDying = (e.getState().toString().equals("DIE"));
                } catch (Throwable ignore) { /* nếu không có API state thì coi như chưa chết */ }

                if (!enemyIsDying) {
                    // Enemy -> Player
                    long readyAt = nextEnemyAttackAtMs.getOrDefault(e, 0L);
                    if (now >= readyAt) {
                        try { e.startAttack(); } catch (Throwable ignore) {}
                        boolean playerDead = safeTakeDamage(p, ENEMY_DAMAGE);
                        if (combatListener != null) combatListener.onPlayerHit();
                        nextEnemyAttackAtMs.put(e, now + ENEMY_COOLDOWN_MS);
                        if (playerDead) { /* GameView xử lý */ }
                    }

                    // Player -> Enemy
                    if (now >= nextPlayerAttackAtMs) {
                        try { p.startAttack(); } catch (Throwable ignore) {}

                        int hp = enemyHp.getOrDefault(e, ENEMY_MAX_HP);
                        hp = Math.max(0, hp - PLAYER_DAMAGE);
                        enemyHp.put(e, hp);

                        // Cooldown đánh của Player (có thể thay bằng p.getAttackDurationMs())
                        nextPlayerAttackAtMs = now + PLAYER_COOLDOWN_MS;

                        if (hp == 0) {
                            try { e.onDie(); } catch (Throwable ignore) {}
                            // KHÔNG remove ngay – sẽ quét dọn ở dưới sau khi anim DIE xong
                        }
                    }
                }
            }
        }

        // Giữ map đồng bộ với danh sách hiện hành
        nextEnemyAttackAtMs.keySet().retainAll(enemies);
        enemyHp.keySet().retainAll(enemies);

        // NEW: Quét dọn quái đã kết thúc animation DIE
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            boolean dieState = false;
            try { dieState = e.getState().toString().equals("DIE"); } catch (Throwable ignore) {}
            if (dieState && e.isDieAnimDone()) {
                enemies.remove(i);
                enemyHp.remove(e);
                nextEnemyAttackAtMs.remove(e);
            }
        }
    }

    private boolean safeTakeDamage(Player p, int dmg) {
        try {
            return p.takeDamage(dmg); // trả về true nếu Player chết (theo GameView cũ)
        } catch (Throwable ignore) {
            // nếu Player.takeDamage khác chữ ký, đừng crash
            return false;
        }
    }

    public void applyBulletHit(Enemy e, int dmg){
        Integer hp = enemyHp.get(e);
        if (hp == null) return;
        hp -= dmg;
        if (hp <= 0){
            enemyHp.put(e, 0);
            e.onDie(); // đã có trong Enemy
        } else {
            enemyHp.put(e, hp);
            // nếu có trạng thái HURT trong Enemy, bạn có thể gọi e.hurt() (nếu đã định nghĩa)
            //try { e.hurt(); } catch (Throwable ignore) {}
        }
    }


    /** Vẽ quái + thanh máu dựa trên kích thước sprite thật */
    public void draw(Canvas c, int cameraX, int cameraY) {
        for (Enemy e : enemies) {
            // Vẽ sprite quái
            e.draw(c, cameraX, cameraY, sharedPaint);

            // --- Tính kích thước/tham số thanh máu theo sprite thật ---
            final int hp = enemyHp.getOrDefault(e, ENEMY_MAX_HP);
            final float ratio = Math.max(0f, Math.min(1f, (float) hp / ENEMY_MAX_HP));

            // Kích thước sprite gốc (không scale)
            final float sprW = e.getSpriteW();
            final float sprH = e.getSpriteH();

            // NOTE: Nếu anim đang scale hiển thị khác, bạn có thể thay bằng kích thước hiển thị thực tế
            final float barW = sprW - 150f; // chỉnh theo nhu cầu
            final float barH = 8f;
            final float gapY = -150f;

            // Toạ độ đã trừ camera
            final float screenX = e.x - cameraX;
            final float screenY = e.y - cameraY;

            // Canh giữa thanh máu với sprite bên trong bounding box
            final float barX = screenX + (e.w - barW) / 2f;
            final float barY = screenY - gapY - barH;

            // Nền xám
            c.drawRect(barX, barY, barX + barW, barY + barH, hpBgPaint);
            // Máu đỏ còn lại
            c.drawRect(barX, barY, barX + barW * ratio, barY + barH, hpPaint);
        }
    }
}
