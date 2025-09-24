package com.example.game2dfighting.game.core;


import android.graphics.Canvas;
import android.graphics.Paint;
import com.example.game2dfighting.game.core.SpriteAnim;
import java.util.HashMap;
import android.graphics.Bitmap;

public abstract class GameObject {
    // vị trí trong MAP
    public int x, y, w, h;

    // hướng nhìn (để flipX anim), true = nhìn trái
    protected boolean facingLeft = false;

    // state máy trạng thái
    public enum State { IDLE, RUN, ATTACK, HURT, DIE }
    protected State state = State.IDLE;

    // animations cho từng state
    protected HashMap<State, SpriteAnim> anims = new HashMap<>();
    protected SpriteAnim currentAnim = null;

    public GameObject(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    protected void setState(State s) {
        if (state != s) {
            state = s;
            SpriteAnim next = anims.get(s);
            if (next != null) {
                currentAnim = next;
                currentAnim.reset();
                currentAnim.setFlipX(facingLeft);
            }
        }
    }

    protected void setFacingLeft(boolean left) {
        this.facingLeft = left;
        if (currentAnim != null) currentAnim.setFlipX(left);
    }

    public void update(long dtMs) {
        if (currentAnim != null) currentAnim.update(dtMs);
    }

    public void draw(Canvas c, int cameraX, int cameraY, Paint p) {
        if (currentAnim == null) return;

        float sx = x - cameraX;
        float sy = y - cameraY;
        currentAnim.draw(c, sx, sy, p);           // chỉ vẽ sprite
    }

}
