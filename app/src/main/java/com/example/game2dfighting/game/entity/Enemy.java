package com.example.game2dfighting.game.entity;

import android.content.Context;
import com.example.game2dfighting.R;
import com.example.game2dfighting.game.core.GameObject;
import com.example.game2dfighting.game.core.SpriteAnim;

public class Enemy extends GameObject {
    private final Context ctx;
    private int speed = 4;

    public float xCenter() { return x + w/2f; }
    public float yCenter() { return y + h/2f; }

    public Enemy(Context ctx, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.ctx = ctx;
        loadAnimations();
        setState(State.IDLE);
    }

    private void loadAnimations() {
        int[] idleIds = new int[]{ R.drawable.e_idle_0, R.drawable.e_idle_1, R.drawable.e_idle_2,
                R.drawable.e_idle_3, R.drawable.e_idle_4, R.drawable.e_idle_5 };
        int[] runIds  = new int[]{ R.drawable.e_run_0,  R.drawable.e_run_1, R.drawable.e_run_2,
                R.drawable.e_run_3, R.drawable.e_run_4, R.drawable.e_run_5, R.drawable.e_run_6,
                R.drawable.e_run_7 };
        int[] atkIds  = new int[]{ R.drawable.e_attack_0, R.drawable.e_attack_1, R.drawable.e_attack_2,
                R.drawable.e_attack_3, R.drawable.e_attack_4, R.drawable.e_attack_5 };
        int[] dieIds  = new int[]{ R.drawable.e_die_0, R.drawable.e_die_1, R.drawable.e_die_2,
                R.drawable.e_die_3 };

        SpriteAnim idle = new SpriteAnim(SpriteAnim.loadFrames(ctx, idleIds), 140, true,  w, h);
        SpriteAnim run  = new SpriteAnim(SpriteAnim.loadFrames(ctx, runIds),   90, true,  w, h);
        SpriteAnim atk  = new SpriteAnim(SpriteAnim.loadFrames(ctx, atkIds),  100, false, w, h);
        SpriteAnim die  = new SpriteAnim(SpriteAnim.loadFrames(ctx, dieIds),  120, false, w, h);

        anims.put(State.IDLE, idle);
        anims.put(State.RUN,  run);
        anims.put(State.ATTACK, atk);
        anims.put(State.DIE,   die);
    }

    public void pursue(int targetX, int targetY, long dtMs) {
        if (state == State.DIE) { super.update(dtMs); return; }

        int dx = 0, dy = 0;
        if (targetX < x) dx = -speed;
        else if (targetX > x) dx = speed;
        if (targetY < y) dy = -speed;
        else if (targetY > y) dy = speed;

        if (dx < 0) setFacingLeft(true);
        else if (dx > 0) setFacingLeft(false);

        x += dx; y += dy;

        if (dx != 0 || dy != 0) setState(State.RUN);
        else setState(State.IDLE);

        super.update(dtMs);
    }

    public void startAttack() {
        if (state == State.DIE) return;
        setState(State.ATTACK);
    }

    public void onDie() {
        setState(State.DIE);
    }
}
