package com.example.game2dfighting.game.manager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.example.game2dfighting.game.entity.Enemy;
import com.example.game2dfighting.game.entity.Enemy2;
import com.example.game2dfighting.game.entity.Enemy3;
import com.example.game2dfighting.game.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class EnemyManager {
    // ====== Core ======
    private final Context ctx;

    // 3 danh sách quái tách riêng
    private final List<Enemy> enemies1 = new ArrayList<>();
    private final List<Enemy> enemies2 = new ArrayList<>();
    private final List<Enemy> enemies3 = new ArrayList<>();

    private final Random rnd = new Random();
    private final int mapW, mapH;

    // Mốc thời gian mở khóa loại quái
    private final long gameStartAtMs  = System.currentTimeMillis();
    private final long type2StartAtMs = gameStartAtMs + 10_000L; // Enemy2 sau 10s
    private final long type3StartAtMs = gameStartAtMs + 20_000L; // Enemy3 sau 20s

    // Spawn interval từng loại
    public long spawnIntervalMs1 = 800L;
    public long spawnIntervalMs2 = 800L;
    public long spawnIntervalMs3 = 800L;

    // last spawn từng loại
    private long lastSpawn1 = 0L;
    private long lastSpawn2 = 0L;
    private long lastSpawn3 = 0L;

    // Giới hạn tối đa độc lập (ban đầu 1, mỗi 10s +1)
    private static final int BASE_MAX_1 = 1;
    private static final int BASE_MAX_2 = 1;
    private static final int BASE_MAX_3 = 1;

    // Combat config chung
    private static final float ATTACK_RANGE_PADDING = 12f;
    private static final long ENEMY_COOLDOWN_MS  = 1200L; // cooldown quái
    private static final long PLAYER_COOLDOWN_MS = 400L;  // cooldown người
    private static final int  PLAYER_DAMAGE      = 12;

    // Vẽ
    private final Paint hpPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sharedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Trạng thái combat
    private final Map<Enemy, Long>    nextEnemyAttackAtMs = new HashMap<>();
    private final Map<Enemy, Integer> enemyHp             = new HashMap<>();
    private long nextPlayerAttackAtMs = 0L;

    // Scale enemy khi spawn
    private static final float ENEMY_SCALE = 4f;
    private final int enemyBaseW = 100, enemyBaseH = 100;
    private int enemyW = Math.round(enemyBaseW * ENEMY_SCALE);
    private int enemyH = Math.round(enemyBaseH * ENEMY_SCALE);

    // Listener (optional)
    public interface CombatListener { void onPlayerHit(); }
    private CombatListener combatListener = null;
    public void setCombatListener(CombatListener l) { this.combatListener = l; }

    //Tính điểm
    public interface KillListener { void onEnemyKilled(); }
    private KillListener killListener;
    public void setKillListener(KillListener l) { this.killListener = l; }

    // ===== Difficulty multipliers =====
    private float hpMul  = 1f;
    private float dmgMul = 1f;

    public void setStatMultipliers(float hpMul, float dmgMul) {
        this.hpMul  = Math.max(0.1f, hpMul);
        this.dmgMul = Math.max(0.1f, dmgMul);
    }

    // Lưu cả max HP theo từng enemy để vẽ thanh máu đúng tỷ lệ
    private final Map<Enemy, Integer> enemyMaxHp = new HashMap<>();

    // ====== Ctor ======
    public EnemyManager(Context ctx, int mapW, int mapH) {
        this.ctx = ctx; this.mapW = mapW; this.mapH = mapH;
        hpPaint.setColor(0xFFFF0000);    // đỏ
        hpPaint.setStyle(Paint.Style.FILL);

        hpBgPaint.setColor(0xFF555555);  // nền tối
        hpBgPaint.setStyle(Paint.Style.FILL);

        hpOutlinePaint.setStyle(Paint.Style.STROKE);
        hpOutlinePaint.setStrokeWidth(2f);
        hpOutlinePaint.setColor(0xFF00FF00); // xanh lá cây

    }
    public EnemyManager(int mapW, int mapH) { this(null, mapW, mapH); }

    // ====== API ======
    public List<Enemy> list() {
        List<Enemy> all = new ArrayList<>(enemies1.size() + enemies2.size() + enemies3.size());
        all.addAll(enemies1); all.addAll(enemies2); all.addAll(enemies3);
        return all;
    }

    // ==== Max theo thời gian ====
    private int computeMax1(long now) {
        long elapsed = Math.max(0L, now - gameStartAtMs);
        return BASE_MAX_1 + (int)(elapsed / 10_000L);
    }
    private int computeMax2(long now) {
        if (now < type2StartAtMs) return 0;
        long elapsed = now - type2StartAtMs;
        return BASE_MAX_2 + (int)(elapsed / 10_000L);
    }
    private int computeMax3(long now) {
        if (now < type3StartAtMs) return 0;
        long elapsed = now - type3StartAtMs;
        return BASE_MAX_3 + (int)(elapsed / 10_000L);
    }

    // ==== Spawn vị trí ngẫu nhiên ở mép map ====
    private int[] randomSpawnPos() {
        int x, y, edge = rnd.nextInt(4);
        switch (edge) {
            case 0: x = rnd.nextInt(Math.max(1, mapW - enemyW)); y = 0; break;
            case 1: x = rnd.nextInt(Math.max(1, mapW - enemyW)); y = mapH - enemyH; break;
            case 2: x = 0; y = rnd.nextInt(Math.max(1, mapH - enemyH)); break;
            default: x = mapW - enemyW; y = rnd.nextInt(Math.max(1, mapH - enemyH)); break;
        }
        return new int[]{x, y};
    }

    private void spawnEnemy1() {
        int[] pos = randomSpawnPos();
        Enemy e = new Enemy(ctx, pos[0], pos[1], enemyW, enemyH);
        enemies1.add(e);
        enemyHp.put(e, Math.round(Enemy.BASE_HP * hpMul));
        enemyMaxHp.put(e, Math.round(Enemy.BASE_HP * hpMul));
        nextEnemyAttackAtMs.put(e, 0L);
    }
    private void spawnEnemy2() {
        int[] pos = randomSpawnPos();
        Enemy e = new Enemy2(ctx, pos[0], pos[1], enemyW, enemyH);
        enemies2.add(e);
        enemyHp.put(e, Math.round(Enemy2.BASE_HP * hpMul));
        enemyMaxHp.put(e, Math.round(Enemy2.BASE_HP * hpMul));
        nextEnemyAttackAtMs.put(e, 0L);
    }
    private void spawnEnemy3() {
        int[] pos = randomSpawnPos();
        Enemy e = new Enemy3(ctx, pos[0], pos[1], enemyW, enemyH);
        enemies3.add(e);
        enemyHp.put(e, Math.round(Enemy3.BASE_HP * hpMul));
        enemyMaxHp.put(e, Math.round(Enemy3.BASE_HP * hpMul));
        nextEnemyAttackAtMs.put(e, 0L);
    }

    // ==== Maybe spawn theo từng loại (giới hạn độc lập) ====
    public void maybeSpawn() {
        long now = System.currentTimeMillis();
        if (ctx == null) throw new IllegalStateException("EnemyManager requires Context to spawn.");

        int max1 = computeMax1(now);
        if (enemies1.size() < max1 && now - lastSpawn1 >= spawnIntervalMs1) { spawnEnemy1(); lastSpawn1 = now; }

        int max2 = computeMax2(now);
        if (enemies2.size() < max2 && now - lastSpawn2 >= spawnIntervalMs2) { spawnEnemy2(); lastSpawn2 = now; }

        int max3 = computeMax3(now);
        if (enemies3.size() < max3 && now - lastSpawn3 >= spawnIntervalMs3) { spawnEnemy3(); lastSpawn3 = now; }
    }

    // ==== Update + Combat ====
    public void updateTowardsPlayer(Player p, long dtMs) {
        long now = System.currentTimeMillis();
        updateAndCombatForList(enemies1, p, dtMs, now, 1);
        updateAndCombatForList(enemies2, p, dtMs, now, 2);
        updateAndCombatForList(enemies3, p, dtMs, now, 3);

        List<Enemy> all = list();
        nextEnemyAttackAtMs.keySet().retainAll(all);
        enemyHp.keySet().retainAll(all);

        cleanupDead(enemies1);
        cleanupDead(enemies2);
        cleanupDead(enemies3);
    }

    /**
     * typeId: 1,2,3 tương ứng Enemy / Enemy2 / Enemy3
     */
    private void updateAndCombatForList(List<Enemy> list, Player p, long dtMs, long now, int typeId) {
        for (int i = list.size() - 1; i >= 0; i--) {
            Enemy e = list.get(i);

            // Init HP & cooldown
            if (!enemyHp.containsKey(e)) {
                int base = (typeId == 2) ? Enemy2.BASE_HP : (typeId == 3) ? Enemy3.BASE_HP : Enemy.BASE_HP;
                int scaled = Math.round(base * hpMul);
                enemyHp.put(e, scaled);
                enemyMaxHp.put(e, scaled);
            }
            if (!nextEnemyAttackAtMs.containsKey(e)) nextEnemyAttackAtMs.put(e, 0L);

            // Pursue + chống đè lên Player
            int oldX = e.x, oldY = e.y;        // 1) Lưu vị trí cũ
            try { e.pursue(p.x, p.y, dtMs); } catch (Throwable ignore) {}

            // 2) Nếu sau khi di chuyển bị chồng lên player -> trả về vị trí cũ
            if (isOverlapCircle(p, e, 0f)) {
                e.x = oldX;
                e.y = oldY;
                e.forceIdle();                 // tránh “đẩy” liên tục vào người chơi
            }

            // Trigger tấn công: Enemy2 & Enemy3 có extra range 50 (giống yêu cầu trước)
            float enemyRadius  = Math.min(e.w, e.h) / 2f;
            float playerRadius = Math.min(p.w, p.h) / 2f;
            float extraRange   = (typeId == 2 || typeId == 3) ? 50f : 0f;
            float trigger      = enemyRadius + playerRadius + ATTACK_RANGE_PADDING + extraRange;

            float ddx = (p.x + p.w/2f) - (e.x + e.w/2f);
            float ddy = (p.y + p.h/2f) - (e.y + e.h/2f);
            float dist2 = ddx*ddx + ddy*ddy;

            if (dist2 <= trigger*trigger) {
                // Tách riêng cách tấn công theo loại (hiện giống Enemy1)
                switch (typeId) {
                    case 1: handleCombatType1(e, p, now); break;
                    case 2: handleCombatType2(e, p, now); break; // giống 1
                    case 3: handleCombatType3(e, p, now); break; // giống 1
                }
            }
        }
    }

    // ==== CÁCH TẤN CÔNG THEO LOẠI (hiện tại giống hệt quái 1) ====
    private void handleCombatType1(Enemy e, Player p, long now) {
        if (isDying(e)) return;

        // Enemy -> Player
        long readyAt = nextEnemyAttackAtMs.getOrDefault(e, 0L);
        if (now >= readyAt) {
            try { e.startAttack(); } catch (Throwable ignore) {}
            int dmg = Math.round(Enemy.BASE_DAMAGE * dmgMul);
            boolean playerDead = safeTakeDamage(p, dmg);
            if (combatListener != null) combatListener.onPlayerHit();
            nextEnemyAttackAtMs.put(e, now + ENEMY_COOLDOWN_MS);
        }

        // Player -> Enemy
        if (now >= nextPlayerAttackAtMs) {
            try { p.startAttack(); } catch (Throwable ignore) {}
            int hp = enemyHp.getOrDefault(e, 1);
            hp = Math.max(0, hp - PLAYER_DAMAGE);
            enemyHp.put(e, hp);
            nextPlayerAttackAtMs = now + PLAYER_COOLDOWN_MS;
            if (hp == 0) {
                try { e.onDie(); } catch (Throwable ignore) {}
                if (killListener != null) killListener.onEnemyKilled();   // <-- THÊM
            }
        }
    }

    private void handleCombatType2(Enemy e, Player p, long now) {
        // hiện giống type1, chỉ khác damage theo Enemy2
        if (isDying(e)) return;

        long readyAt = nextEnemyAttackAtMs.getOrDefault(e, 0L);
        if (now >= readyAt) {
            try { e.startAttack(); } catch (Throwable ignore) {}
            int dmg = Math.round(Enemy2.BASE_DAMAGE * dmgMul);
            boolean playerDead = safeTakeDamage(p, dmg);
            if (combatListener != null) combatListener.onPlayerHit();
            nextEnemyAttackAtMs.put(e, now + ENEMY_COOLDOWN_MS);
        }

        if (now >= nextPlayerAttackAtMs) {
            try { p.startAttack(); } catch (Throwable ignore) {}
            int hp = enemyHp.getOrDefault(e, 1);
            hp = Math.max(0, hp - PLAYER_DAMAGE);
            enemyHp.put(e, hp);
            nextPlayerAttackAtMs = now + PLAYER_COOLDOWN_MS;
            if (hp == 0) {
                try { e.onDie(); } catch (Throwable ignore) {}
                if (killListener != null) killListener.onEnemyKilled();   // <-- THÊM
            }
        }
    }

    private void handleCombatType3(Enemy e, Player p, long now) {
        // hiện giống type1, chỉ khác damage theo Enemy3
        if (isDying(e)) return;

        long readyAt = nextEnemyAttackAtMs.getOrDefault(e, 0L);
        if (now >= readyAt) {
            try { e.startAttack(); } catch (Throwable ignore) {}
            int dmg = Math.round(Enemy3.BASE_DAMAGE * dmgMul);
            boolean playerDead = safeTakeDamage(p, dmg);
            if (combatListener != null) combatListener.onPlayerHit();
            nextEnemyAttackAtMs.put(e, now + ENEMY_COOLDOWN_MS);
        }

        if (now >= nextPlayerAttackAtMs) {
            try { p.startAttack(); } catch (Throwable ignore) {}
            int hp = enemyHp.getOrDefault(e, 1);
            hp = Math.max(0, hp - PLAYER_DAMAGE);
            enemyHp.put(e, hp);
            nextPlayerAttackAtMs = now + PLAYER_COOLDOWN_MS;
            if (hp == 0) {
                try { e.onDie(); } catch (Throwable ignore) {}
                if (killListener != null) killListener.onEnemyKilled();   // <-- THÊM
            }
        }
    }

    private boolean isDying(Enemy e) {
        try { return e.getState().toString().equals("DIE"); }
        catch (Throwable ignore) { return false; }
    }

    // ==== Cleanup dead ====
    private void cleanupDead(List<Enemy> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            Enemy e = list.get(i);
            boolean dieState = false;
            try { dieState = e.getState().toString().equals("DIE"); } catch (Throwable ignore) {}
            if (dieState && e.isDieAnimDone()) {
                list.remove(i);
                enemyHp.remove(e);
                enemyMaxHp.remove(e);
                nextEnemyAttackAtMs.remove(e);
            }
        }
    }

    // ==== Damage helpers ====
    private boolean safeTakeDamage(Player p, int dmg) {
        try { return p.takeDamage(dmg); } catch (Throwable ignore) { return false; }
    }

    public void applyBulletHit(Enemy e, int dmg) {
        Integer hp = enemyHp.get(e);
        if (hp == null) return;
        hp -= dmg;
        if (hp <= 0) {
            enemyHp.put(e, 0);
            e.onDie();
            if (killListener != null) killListener.onEnemyKilled();
        } else {
            enemyHp.put(e, hp);
        }
    }

    // --- NO-OVERLAP CHECK (circle vs circle) ---
    private boolean isOverlapCircle(Player p, Enemy e, float marginPx) {
        float pcx = p.x + p.w / 2f, pcy = p.y + p.h / 2f;
        float ecx = e.x + e.w / 2f, ecy = e.y + e.h / 2f;

        float pr = Math.min(p.w, p.h) / 2f;
        float er = Math.min(e.w, e.h) / 2f;

        float minDist = pr + er + marginPx; // margin=0: “sát rạt” vẫn OK, không đẩy
        float dx = pcx - ecx, dy = pcy - ecy;
        return (dx*dx + dy*dy) < (minDist * minDist);
    }

    // ==== Draw ====
    public void draw(Canvas c, int cameraX, int cameraY) {
        for (Enemy e : enemies1) drawEnemyWithHp(c, e, cameraX, cameraY);
        for (Enemy e : enemies2) drawEnemyWithHp(c, e, cameraX, cameraY);
        for (Enemy e : enemies3) drawEnemyWithHp(c, e, cameraX, cameraY);
    }

    private void drawEnemyWithHp(Canvas c, Enemy e, int cameraX, int cameraY) {
        e.draw(c, cameraX, cameraY, sharedPaint);

        // Lấy max HP đã scale (nếu chưa có, fallback = base * hpMul)
        int base = (e instanceof Enemy2) ? Enemy2.BASE_HP
                : (e instanceof Enemy3) ? Enemy3.BASE_HP
                : Enemy.BASE_HP;
        int maxHp = enemyMaxHp.getOrDefault(e, Math.round(base * hpMul));
        int hp    = enemyHp.getOrDefault(e, maxHp);
        float ratio = Math.max(0f, Math.min(1f, (float) hp / Math.max(1, maxHp)));

        // Kích thước & vị trí thanh máu
        final float barW = e.w;         // rộng bề ngang quái
        final float barH = 10f;         // cao
        final float gapY = 20f;         // cách đỉnh đầu quái
        final float screenX = e.x - cameraX, screenY = e.y - cameraY;
        final float barX = screenX + (e.w - barW) / 2f, barY = screenY - gapY - barH;

        // Nền
        c.drawRect(barX, barY, barX + barW, barY + barH, hpBgPaint);
        // Phần máu đỏ
        c.drawRect(barX, barY, barX + barW * ratio, barY + barH, hpPaint);
        // Viền xanh lá
        c.drawRect(barX, barY, barX + barW, barY + barH, hpOutlinePaint);
    }

}
