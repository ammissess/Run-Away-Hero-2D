package com.example.game2dfighting.game.core;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

public class SpriteAnim {
    private final Bitmap[] frames;
    private final int frameDurationMs; // thời gian mỗi frame
    private int frameIndex = 0;
    private long lastSwitch = 0;

    public SpriteAnim(Bitmap[] frames, int frameDurationMs) {
        this.frames = frames;
        this.frameDurationMs = frameDurationMs;
    }

    public void update(long now) {
        if (now - lastSwitch >= frameDurationMs) {
            frameIndex = (frameIndex + 1) % frames.length;
            lastSwitch = now;
        }
    }

    public void draw(Canvas c, float x, float y, int w, int h) {
        Bitmap bmp = frames[frameIndex];
        Rect dst = new Rect((int)x, (int)y, (int)(x + w), (int)(y + h));
        c.drawBitmap(bmp, null, dst, null);
    }
}

