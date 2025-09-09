package com.example.game2dfighting.game.entity;

import android.graphics.Canvas;
import com.example.game2dfighting.game.core.GameObject;

public class Player implements GameObject {
    public int x, y, w, h;
    public int speed = 5;

    // input
    public boolean up, down, left, right;

    // ===== Stats =====
    private int maxHp = 100, hp = 100;
    private int maxMana = 10, mana = 0;
    private int maxEnergy = 100, energy = 100;

    public Player(int startX, int startY, int width, int height) {
        this.x = startX; this.y = startY; this.w = width; this.h = height;
    }

    @Override public void update() {
        if (up) y -= speed;
        if (down) y += speed;
        if (left) x -= speed;
        if (right) x += speed;
    }

    @Override public void draw(Canvas c) {
        // Không vẽ HUD ở đây; GameView sẽ vẽ HUD.
    }

    public float centerX() { return x + w / 2f; }
    public float centerY() { return y + h / 2f; }

    // ===== API chỉ số =====
    public boolean takeDamage(int amount) {
        if (amount <= 0) return false;
        setHp(hp - amount);
        return hp <= 0;
    }
    public void heal(int amount) { setHp(hp + Math.max(0, amount)); }

    public boolean spendMana(int amount) {
        if (amount <= 0) return true;
        if (mana < amount) return false;
        setMana(mana - amount); return true;
    }
    public void addMana(int amount) { setMana(mana + Math.max(0, amount)); }

    public boolean spendEnergy(int amount) {
        if (amount <= 0) return true;
        if (energy < amount) return false;
        setEnergy(energy - amount); return true;
    }
    public void addEnergy(int amount) { setEnergy(energy + Math.max(0, amount)); }

    // ===== Getter/Setter với clamp =====
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public void setHp(int v) { hp = Math.max(0, Math.min(maxHp, v)); }

    public int getMana() { return mana; }
    public int getMaxMana() { return maxMana; }
    public void setMana(int v) { mana = Math.max(0, Math.min(maxMana, v)); }

    public int getEnergy() { return energy; }
    public int getMaxEnergy() { return maxEnergy; }
    public void setEnergy(int v) { energy = Math.max(0, Math.min(maxEnergy, v)); }

    public void setMaxHp(int v) { maxHp = Math.max(1, v); hp = Math.min(hp, maxHp); }
    public void setMaxMana(int v) { maxMana = Math.max(1, v); mana = Math.min(mana, maxMana); }
    public void setMaxEnergy(int v) { maxEnergy = Math.max(1, v); energy = Math.min(energy, maxEnergy); }
}
