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

    public EnemyManager(int mapW, int mapH) { this.mapW = mapW; this.mapH = mapH; }

    public List<Enemy> list() { return enemies; }

    public void maybeSpawn() {
        long now = System.currentTimeMillis();
        if (enemies.size() >= maxEnemies) return;
        if (now - lastSpawn < spawnIntervalMs) return;
        lastSpawn = now;

        int edge = rnd.nextInt(4);
        float r = 12f;
        float speed = 2.2f + rnd.nextFloat() * 1.6f;
        float x=0, y=0;
        switch (edge) {
            case 0: x = rnd.nextInt(mapW); y = 10 + r; break;
            case 1: x = rnd.nextInt(mapW); y = mapH - 10 - r; break;
            case 2: x = 10 + r; y = rnd.nextInt(mapH); break;
            default: x = mapW - 10 - r; y = rnd.nextInt(mapH);
        }
        enemies.add(new Enemy(x, y, speed, r));
    }

    public void updateTowardsPlayer(Player p) {
        float px = p.centerX(), py = p.centerY();
        for (Enemy e : enemies) {
            float dx = px - e.x, dy = py - e.y;
            float len = (float) Math.sqrt(dx*dx + dy*dy);
            if (len > 1e-4f) {
                e.x += e.speed * dx / len;
                e.y += e.speed * dy / len;
            }
        }
    }

    /** Vẽ theo toạ độ màn hình, truyền cameraX/Y để trừ. */
    public void draw(Canvas c, int cameraX, int cameraY) {
        c.save();
        // Dịch canvas để Enemy.draw dùng toạ độ thế giới như cũ
        c.translate(-cameraX, -cameraY);
        for (Enemy e : enemies) e.draw(c);
        c.restore();
    }
}
