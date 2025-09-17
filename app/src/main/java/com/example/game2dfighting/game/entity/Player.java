package com.example.game2dfighting.game.entity;

import android.content.Context;
import com.example.game2dfighting.R;
import com.example.game2dfighting.game.core.GameObject;
import com.example.game2dfighting.game.core.SpriteAnim;

public class Player extends GameObject {
    // ví dụ các biến sẵn có của bạn
    public boolean up, down, left, right;
    private int speed = 6;

    // (gợi ý) đang tấn công?
    private boolean attacking = false;
    private long attackTimeMs = 0;
    private final long attackDurationMs = 250; // 0.25s

    private final Context ctx;
    // --- STATS ---
    private int maxHp = 100;
    private int hp    = 100;

    private int maxMana   = 10;
    private int mana      = 0;

    private int maxEnergy = 100;
    private int energy    = 100;


    public Player(Context ctx, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.ctx = ctx;
        loadAnimations();
        setState(State.IDLE);
        if (currentAnim != null) currentAnim.update(1);   // ép chọn frame 0 ngay lập tức
    }

    private void loadAnimations() {
        // Khai báo resource id mảng frame theo state (đổi theo file PNG bạn có)
        int[] idleIds   = new int[]{ R.drawable.p_idle_0, R.drawable.p_idle_1, R.drawable.p_idle_2,
                R.drawable.p_idle_3, R.drawable.p_idle_4, R.drawable.p_idle_5 };
        int[] runIds    = new int[]{ R.drawable.p_run_0,  R.drawable.p_run_1,  R.drawable.p_run_2,
                R.drawable.p_run_3,  R.drawable.p_run_4,  R.drawable.p_run_5,  R.drawable.p_run_6,
                R.drawable.p_run_7  };
        int[] atkIds    = new int[]{ R.drawable.p_attack_0, R.drawable.p_attack_1, R.drawable.p_attack_2,
                R.drawable.p_attack_3, R.drawable.p_attack_4, R.drawable.p_attack_5 };
        int[] hurtIds   = new int[]{ R.drawable.p_hurt_0, R.drawable.p_hurt_1, R.drawable.p_hurt_2,
                R.drawable.p_hurt_3  };
        int[] dieIds    = new int[]{ R.drawable.p_die_0,  R.drawable.p_die_1,  R.drawable.p_die_2,
                R.drawable.p_die_3  };

        SpriteAnim idle = new SpriteAnim(SpriteAnim.loadFrames(ctx, idleIds), 140, true,  w, h);
        SpriteAnim run  = new SpriteAnim(SpriteAnim.loadFrames(ctx, runIds),  90,  true,  w, h);
        SpriteAnim atk  = new SpriteAnim(SpriteAnim.loadFrames(ctx, atkIds),  80,  false, w, h);
        SpriteAnim hurt = new SpriteAnim(SpriteAnim.loadFrames(ctx, hurtIds), 120, false, w, h);
        SpriteAnim die  = new SpriteAnim(SpriteAnim.loadFrames(ctx, dieIds),  120, false, w, h);

        anims.put(State.IDLE, idle);
        anims.put(State.RUN,  run);
        anims.put(State.ATTACK, atk);
        anims.put(State.HURT,  hurt);
        anims.put(State.DIE,   die);
    }

    public void startAttack() {
        if (state == State.DIE) return;
        attacking = true;
        attackTimeMs = 0;
        setState(State.ATTACK);
    }

    @Override
    public void update(long dtMs) {
        // di chuyển cơ bản
        int dx = 0, dy = 0;
        if (up) dy -= speed;
        if (down) dy += speed;
        if (left) dx -= speed;
        if (right) dx += speed;

        // hướng nhìn
        if (dx < 0) setFacingLeft(true);
        else if (dx > 0) setFacingLeft(false);

        // cập nhật vị trí
        x += dx; y += dy;

        // chuyển state theo hành vi
        if (state != State.DIE) {
            if (attacking) {
                attackTimeMs += dtMs;
                if (currentAnim != null && currentAnim.isFinished() || attackTimeMs >= attackDurationMs) {
                    attacking = false;
                    // quay về IDLE/RUN theo vận tốc
                    if (dx != 0 || dy != 0) setState(State.RUN);
                    else setState(State.IDLE);
                }
            } else {
                if (dx != 0 || dy != 0) setState(State.RUN);
                else setState(State.IDLE);
            }
        }

        super.update(dtMs);
    }

    // --- CENTER HELPERS ---
    public float centerX() { return x + w / 2f; }
    public float centerY() { return y + h / 2f; }

    // --- MANA ---
    public int  getMana()    { return mana; }
    public int  getMaxMana() { return maxMana; }

    public void setMaxMana(int v) {
        maxMana = Math.max(1, v);
        if (mana > maxMana) mana = maxMana;
    }

    public void setMana(int v) {
        mana = Math.max(0, Math.min(maxMana, v));
    }

    public void addMana(int delta) {
        setMana(mana + delta);
    }

    // --- HP / ENERGY (nếu GameView đang vẽ HUD cần các getter này) ---
    public int  getHp()        { return hp; }
    public int  getMaxHp()     { return maxHp; }
    public int  getEnergy()    { return energy; }
    public int  getMaxEnergy() { return maxEnergy; }

    public void setMaxHp(int v) {
        maxHp = Math.max(1, v);
        if (hp > maxHp) hp = maxHp;
    }

    /** Gây sát thương. Trả về true nếu Player chết. */
    public boolean takeDamage(int dmg) {
        if (state == State.DIE) return true;              // đã chết rồi
        if (dmg < 0) dmg = 0;
        hp -= dmg;
        if (hp <= 0) {
            hp = 0;
            onDie();                                       // chuyển state DIE (nếu có anim sẽ phát)
            return true;
        } else {
            onHurt();                                      // chớp HURT một nhịp
            return false;
        }
    }

    // Ví dụ khi nhận sát thương:
    public void onHurt() {
        if (state == State.DIE) return;
        setState(State.HURT);
    }

    public void onDie() {
        setState(State.DIE);
    }
}
