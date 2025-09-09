package com.example.game2dfighting.game.manager;


import android.graphics.Canvas;

import com.example.game2dfighting.game.entity.Enemy;
import com.example.game2dfighting.game.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EnemyManager {
    private final List<Enemy> enemies = new ArrayList<>();
    private final Random rnd = new Random();
    private final int mapW, mapH;
    private long lastSpawn = 0L;

    public int maxEnemies = 30;
    public long spawnIntervalMs = 800;

    public EnemyManager(int mapW, int mapH) {
        this.mapW = mapW; this.mapH = mapH;
    }

    public List<Enemy> list() { return enemies; }

    public void maybeSpawn() {
        long now = System.currentTimeMillis();
        if (enemies.size() >= maxEnemies) return;
        if (now - lastSpawn < spawnIntervalMs) return;
        lastSpawn = now;

        int edge = rnd.nextInt(4);
        float r = 12f;
        float speed = 2.4f + rnd.nextFloat() * 1.6f;
        float x=0,y=0;
        switch (edge) {
            case 0: x = rnd.nextInt(mapW); y =  10 + r; break;
            case 1: x = rnd.nextInt(mapW); y =  mapH - 10 - r; break;
            case 2: x = 10 + r;           y =  rnd.nextInt(mapH); break;
            default:x = mapW - 10 - r;    y =  rnd.nextInt(mapH);
        }
        enemies.add(new Enemy(x, y, speed, r));
    }

    public void updateTowardsPlayer(Player p) {
        float px = p.centerX();
        float py = p.centerY();
        for (Enemy e : enemies) {
            float dx = px - e.x, dy = py - e.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 1e-4) {
                e.x += e.speed * dx / len;
                e.y += e.speed * dy / len;
            }
        }
    }

    public void draw(Canvas c, int camX, int camY) {
        for (Enemy e : enemies) {
            float sx = e.x - camX;
            float sy = e.y - camY;
            c.save();
            c.translate(sx - e.x, sy - e.y);
            e.draw(c);
            c.restore();
        }
    }

    // Xóa quái khi bị kiếm quét (đoạn kiểm tra va chạm thanh kiếm bạn có thể tái sử dụng)
    public void removeIfHitBySwords(float cx, float cy, float swordLength, float swordHalfWidthDeg,
                                    float globalAngleDeg, float[] baseAnglesDeg) {
        // swordHalfWidthDeg: nửa bề dày (đổi ra “bán kính” để nới hitbox)
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            boolean hit = false;
            for (float base : baseAnglesDeg) {
                float rad = (float) Math.toRadians(globalAngleDeg + base);
                float sx = (float) (cx + Math.sin(rad) * swordLength);
                float sy = (float) (cy + Math.cos(rad) * swordLength);
                if (circleVsSegment(e.x, e.y, e.radius, cx, cy, sx, sy, swordHalfWidthDeg)) {
                    hit = true; break;
                }
            }
            if (hit) enemies.remove(i);
        }
    }

    private boolean circleVsSegment(float ex, float ey, float r,
                                    float ax, float ay, float bx, float by,
                                    float halfThickness) {
        float abx = bx - ax, aby = by - ay;
        float ab2 = abx*abx + aby*aby;
        if (ab2 < 1e-6f) {
            float dx = ex - ax, dy = ey - ay;
            float rr = r + halfThickness;
            return dx*dx + dy*dy <= rr*rr;
        }
        float t = ((ex - ax)*abx + (ey - ay)*aby) / ab2;
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
        float px = ax + t*abx, py = ay + t*aby;
        float dx = ex - px, dy = ey - py;
        float rr = r + halfThickness;
        return dx*dx + dy*dy <= rr*rr;
    }
}

