package com.example.game2dfighting.game.entity; // hoặc package của GameView

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

public class Heart {
    public float x, y;            // toạ độ thế giới (world)
    public int w, h;              // kích thước vẽ
    private final Bitmap sprite;
    private boolean consumed = false;

    public Heart(float x, float y, Bitmap sprite) {
        this.x = x;
        this.y = y;
        this.sprite = sprite;
        this.w = sprite.getWidth();
        this.h = sprite.getHeight();
    }

    public void draw(Canvas canvas, float cameraX, float cameraY) {
        if (consumed) return;
        canvas.drawBitmap(sprite, x - cameraX, y - cameraY, null);
    }

    public Rect getHitbox() {
        return new Rect((int)x, (int)y, (int)(x + w), (int)(y + h));
    }

    public void consume() { consumed = true; }
    public boolean isConsumed() { return consumed; }
}
