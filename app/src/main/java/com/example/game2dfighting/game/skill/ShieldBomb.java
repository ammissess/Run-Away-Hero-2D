package com.example.game2dfighting.game.skill;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;

import com.example.game2dfighting.game.entity.Boss;
import com.example.game2dfighting.game.entity.Enemy;
import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.game.manager.BossManager;
import com.example.game2dfighting.game.manager.EnemyManager;

public class ShieldBomb extends Shield {  // Kế thừa từ Shield để giữ tính năng giáp cơ bản

    // === Reference riêng để tránh private access ===
    private final Player myPlayer;

    // === Bomb-specific ===
    private boolean exploded = false;
    private long explosionStartTime = 0L;
    private static final long EXPLOSION_DURATION_MS = 800L;  // Thời gian hiệu ứng sóng lan tỏa
    private static final float MAX_EXPLOSION_RADIUS = 400f;  // Bán kính lan tỏa max
    private static final float PUSH_BACK_DISTANCE = 150f;    // Khoảng cách đẩy lùi
    private static final float DAMAGE_PERCENT = 0.5f;        // Giảm 50% HP

    // Paint cho viền giáp và hiệu ứng sóng
    private final Paint borderPaint;
    private final Paint wavePaint;


    //tính thời điểm hủy shield bom

    private final long shieldDuration; // thời gian tồn tại của shield
    private final long shieldCreatedAt; // thời điểm tạo shield

    public ShieldBomb(Player player, int absorbHp, long durationMs) {
        super(player, absorbHp, durationMs);  // Gọi super với 3 args
        this.myPlayer = player;  // Lưu reference riêng

        this.shieldDuration = durationMs;
        this.shieldCreatedAt = System.currentTimeMillis();

        this.borderPaint = new Paint();
        borderPaint.setStyle(Style.STROKE);
        borderPaint.setColor(Color.parseColor("#FFFF99"));  // Viền vàng nhạt
        borderPaint.setStrokeWidth(6f);
        borderPaint.setAntiAlias(true);

        this.wavePaint = new Paint();
        wavePaint.setStyle(Style.STROKE);
        wavePaint.setColor(Color.argb(150, 255, 255, 100));  // Sóng vàng nhạt trong suốt
        wavePaint.setStrokeWidth(4f);
        wavePaint.setAntiAlias(true);
    }

    /**
     * Kích hoạt vụ nổ: Áp dụng damage và push back lên tất cả enemies/boss.
     * Gọi khi player ấn nút Shield (trong PlayerManager.tryUseShieldBomb hoặc tương tự).
     * Sau đó bắt đầu hiệu ứng sóng lan tỏa.
     */
    public void triggerExplosion(EnemyManager enemyMgr, BossManager bossMgr, com.example.game2dfighting.game.manager.BossAngelManager bossAngelMgr) {
        if (exploded) return;

        long now = System.currentTimeMillis();
        exploded = true;
        explosionStartTime = now;

        float px = myPlayer.centerX();
        float py = myPlayer.centerY();

        // === Áp dụng cho Enemies ===
        if (enemyMgr != null) {
            for (Enemy enemy : enemyMgr.list()) {
                try {
                    if (enemy.getState() == com.example.game2dfighting.game.core.GameObject.State.DIE)
                        continue;
                } catch (Exception e) { continue; }

                // Damage: 50% HP thông qua EnemyManager
                int currentHp = getEnemyCurrentHp(enemy, enemyMgr);
                int dmg = (int) (currentHp * DAMAGE_PERCENT);

                // Apply damage qua manager (sử dụng method có sẵn)
                enemyMgr.applyBulletHit(enemy, dmg);

                // Push back
                float ex = enemy.x + enemy.w / 2f;
                float ey = enemy.y + enemy.h / 2f;
                pushBackEntity(enemy, px, py, ex, ey);
            }
        }

        // === Áp dụng cho Boss ===
// === Áp dụng cho Boss ===
        if (bossMgr != null && bossMgr.isActive()) {

            // ✅ Chỉ xử lý BossAngel nếu có
            if (bossAngelMgr != null) {
                com.example.game2dfighting.game.entity.BossAngel angel = bossAngelMgr.getBoss();
                if (angel != null) {
                    try {
                        // Lấy HP hiện tại từ BossAngelManager
                        java.lang.reflect.Field hpField = bossAngelMgr.getClass().getDeclaredField("bossHp");
                        hpField.setAccessible(true);
                        int currentHp = hpField.getInt(bossAngelMgr);

                        int dmg = (int) (currentHp * DAMAGE_PERCENT);

                        // Gọi applyBulletHit để trừ máu boss
                        java.lang.reflect.Method hitMethod =
                                bossAngelMgr.getClass().getMethod("applyBulletHit", int.class);
                        hitMethod.invoke(bossAngelMgr, dmg);

                        // Đẩy boss angel lùi
                        float bx = angel.x + angel.w / 2f;
                        float by = angel.y + angel.h / 2f;
                        pushBackEntity(angel, px, py, bx, by);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            // Boss thường
            Boss boss = bossMgr.getBoss();
            if (boss != null) {
                try {
                    if (boss.getState() != com.example.game2dfighting.game.core.GameObject.State.DIE) {
                        int currentHp = bossMgr.getHp();
                        int dmg = (int) (currentHp * DAMAGE_PERCENT);

                        bossMgr.applyBulletHit(dmg);

                        float bx = boss.x + boss.w / 2f;
                        float by = boss.y + boss.h / 2f;
                        pushBackEntity(boss, px, py, bx, by);
                    }
                } catch (Exception ignore) {}
            }
        }

        //Hư thức - tử

// === Hư Thức Tử: tuyệt kỹ thanh tẩy toàn map ===
// === Hư Thức Tử: tuyệt kỹ thanh tẩy toàn map ===
        try {
            int playerLevel = myPlayer.getLevel();
            boolean highLevel = (playerLevel >= 9);
            boolean bossFury = bossAngelMgr != null
                    && bossAngelMgr.getBoss() != null
                    && bossAngelMgr.getBoss().isFuryMode();

            if (highLevel && bossFury) {
                // ===== 1. Diệt sạch quái thường =====
                if (enemyMgr != null) {
                    try {
                        java.lang.reflect.Method clearEnemies =
                                enemyMgr.getClass().getMethod("clearAll");
                        clearEnemies.invoke(enemyMgr);
                    } catch (Exception e1) {
                        for (Enemy e : enemyMgr.list()) {
                            try {
                                java.lang.reflect.Method setState =
                                        com.example.game2dfighting.game.core.GameObject.class
                                                .getDeclaredMethod("setState",
                                                        com.example.game2dfighting.game.core.GameObject.State.class);
                                setState.setAccessible(true);
                                setState.invoke(e, com.example.game2dfighting.game.core.GameObject.State.DIE);
                            } catch (Exception ignore) {}
                        }
                        enemyMgr.list().clear();
                    }
                }

                // ===== 2. Diệt Boss đá =====
                if (bossMgr != null && bossMgr.isActive()) {
                    Boss boss = bossMgr.getBoss();
                    if (boss != null) {
                        try {
                            java.lang.reflect.Method setState =
                                    com.example.game2dfighting.game.core.GameObject.class
                                            .getDeclaredMethod("setState",
                                                    com.example.game2dfighting.game.core.GameObject.State.class);
                            setState.setAccessible(true);
                            setState.invoke(boss, com.example.game2dfighting.game.core.GameObject.State.DIE);
                        } catch (Exception ignore) {}
                    }
                    bossMgr.clearAll();
                }

                // ===== 3. Diệt BossAngel =====

                if (bossAngelMgr != null && bossAngelMgr.getBoss() != null) {
                    com.example.game2dfighting.game.entity.BossAngel angel = bossAngelMgr.getBoss();
                    try {
                        // Đặt state DIE
                        java.lang.reflect.Method setState =
                                com.example.game2dfighting.game.core.GameObject.class
                                        .getDeclaredMethod("setState",
                                                com.example.game2dfighting.game.core.GameObject.State.class);
                        setState.setAccessible(true);
                        setState.invoke(angel, com.example.game2dfighting.game.core.GameObject.State.DIE);

                        // HP về 0 để BossAngelManager.update() xử lý thắng
                        java.lang.reflect.Field hpField =
                                bossAngelMgr.getClass().getDeclaredField("bossHp");
                        hpField.setAccessible(true);
                        hpField.setInt(bossAngelMgr, 0);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }


                // ===== 4. Hiệu ứng flash trắng màn hình =====
                try {
                    java.lang.reflect.Method flash =
                            myPlayer.getClass().getMethod("triggerScreenFlash", int.class);
                    flash.invoke(myPlayer, Color.WHITE);
                } catch (Exception ignore) {}

                return; // Kết thúc, không làm phần nổ 50% HP nữa
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }



    }

    private void pushBackEntity(Object entity, float px, float py, float ex, float ey) {
        float dx = ex - px;
        float dy = ey - py;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist > 0) {
            dx /= dist;
            dy /= dist;

            try {
                java.lang.reflect.Field xField = entity.getClass().getField("x");
                java.lang.reflect.Field yField = entity.getClass().getField("y");

                int currentX = xField.getInt(entity);
                int currentY = yField.getInt(entity);

                xField.setInt(entity, currentX + (int)(dx * PUSH_BACK_DISTANCE));
                yField.setInt(entity, currentY + (int)(dy * PUSH_BACK_DISTANCE));
            } catch (Exception ignore) {}
        }
    }

    private int getEnemyCurrentHp(Enemy enemy, EnemyManager mgr) {
        // Dùng reflection để lấy HP từ enemyHp map trong EnemyManager
        try {
            java.lang.reflect.Field mapField = EnemyManager.class.getDeclaredField("enemyHp");
            mapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Enemy, Integer> hpMap =
                    (java.util.Map<Enemy, Integer>) mapField.get(mgr);
            return hpMap.getOrDefault(enemy, Enemy.BASE_HP);
        } catch (Exception e) {
            return Enemy.BASE_HP;
        }
    }

    @Override
    public void update() {
        super.update();  // Update Shield base

        long now = System.currentTimeMillis();

// Kiểm tra thời gian tồn tại của shield
        if (now - shieldCreatedAt > shieldDuration) {
            // Hết thời gian -> deactivate shield
            try {
                // Set shieldHP về 0 để isActive() trả về false
                java.lang.reflect.Field hpField = Shield.class.getDeclaredField("shieldHP");
                hpField.setAccessible(true);
                hpField.setInt(this, 0);
            } catch (Exception e) {
                // Fallback: không làm gì
            }
        }

        // Cập nhật hiệu ứng explosion
        if (exploded) {
            if (now - explosionStartTime > EXPLOSION_DURATION_MS) {
                // Hết hiệu ứng explosion -> deactivate luôn
                try {
                    java.lang.reflect.Field hpField = Shield.class.getDeclaredField("shieldHP");
                    hpField.setAccessible(true);
                    hpField.setInt(this, 0);
                } catch (Exception ignore) {}
            }
        }
    }


    @Override
    public void draw(Canvas c, int cameraX, int cameraY) {
        if (!isActive() && !exploded) return; // Không vẽ nếu không active và không đang explode

        long now = System.currentTimeMillis();

        // Vẽ viền giáp vàng nhạt quanh player (chỉ khi còn active)
        if (isActive()) {
            float px = myPlayer.x + myPlayer.w / 2f - cameraX;
            float py = myPlayer.y + myPlayer.h / 2f - cameraY;
            float shieldRadius = Math.min(myPlayer.w, myPlayer.h) * 0.8f;

            // Hiệu ứng nhấp nháy khi sắp hết (2 giây cuối)
            long timeLeft = shieldDuration - (now - shieldCreatedAt);
            if (timeLeft < 2000) {
                // Nhấp nháy
                if ((now / 200) % 2 == 0) { // Nhấp nháy mỗi 200ms
                    c.drawCircle(px, py, shieldRadius, borderPaint);
                }
            } else {
                c.drawCircle(px, py, shieldRadius, borderPaint);
            }
        }

        // Vẽ hiệu ứng sóng radio lan tỏa nếu đang explode
        if (exploded && now - explosionStartTime <= EXPLOSION_DURATION_MS) {
            float progress = Math.min(1f, (now - explosionStartTime) / (float) EXPLOSION_DURATION_MS);
            float currentRadius = progress * MAX_EXPLOSION_RADIUS;

            float px = myPlayer.x + myPlayer.w / 2f - cameraX;
            float py = myPlayer.y + myPlayer.h / 2f - cameraY;

            // Vẽ nhiều vòng sóng để hiệu ứng radio
            for (int i = 0; i < 4; i++) {
                float waveProgress = progress - (i * 0.2f);
                if (waveProgress > 0) {
                    float waveRadius = waveProgress * MAX_EXPLOSION_RADIUS;
                    int alpha = (int) (150 * (1f - waveProgress));
                    wavePaint.setAlpha(Math.max(0, alpha));
                    c.drawCircle(px, py, waveRadius, wavePaint);
                }
            }
            wavePaint.setAlpha(150);
        }
    }

    // === Helpers (giả sử, cần adjust theo code thực tế) ===
    private int getEnemyHp(Enemy enemy) {
        // Lấy HP từ EnemyManager thông qua reflection hoặc public method
        try {
            java.lang.reflect.Field hpField = Enemy.class.getDeclaredField("hp");
            hpField.setAccessible(true);
            return (int) hpField.get(enemy);
        } catch (Exception e) {
            // Fallback: return BASE_HP tương ứng
            if (enemy instanceof com.example.game2dfighting.game.entity.Enemy2) {
                return com.example.game2dfighting.game.entity.Enemy2.BASE_HP;
            } else if (enemy instanceof com.example.game2dfighting.game.entity.Enemy3) {
                return com.example.game2dfighting.game.entity.Enemy3.BASE_HP;
            }
            return Enemy.BASE_HP;
        }
    }

    private void takeDamageOnEnemy(Enemy enemy, int dmg) {
        try {
            java.lang.reflect.Method method = Enemy.class.getMethod("takeDamage", int.class);
            method.invoke(enemy, dmg);
        } catch (Exception e) {
            // Fallback: set hp directly via reflection
            try {
                java.lang.reflect.Field hpField = Enemy.class.getDeclaredField("hp");
                hpField.setAccessible(true);
                int currentHp = (int) hpField.get(enemy);
                hpField.set(enemy, Math.max(0, currentHp - dmg));
            } catch (Exception ex) {
                // Không làm gì nếu không access được
            }
        }
    }

    private int getBossHp(Boss boss) {
        // Boss HP được quản lý trong BossManager, cần truyền vào
        return Boss.BASE_HP; // Fallback
    }

    private void takeDamageOnBoss(Boss boss, int dmg) {
        // Tương tự enemy, cần access qua Manager
    }

    public boolean isExploded() {
        return exploded;
    }

    // Override isActive nếu cần kết hợp với exploded
    @Override
    public boolean isActive() {
        long now = System.currentTimeMillis();

        // Kiểm tra thời gian trước
        if (now - shieldCreatedAt > shieldDuration) {
            return false;
        }

        // Sau khi explode thì không active nữa
        if (exploded && now - explosionStartTime > EXPLOSION_DURATION_MS) {
            return false;
        }

        return super.isActive();
    }
}