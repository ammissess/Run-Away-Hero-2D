package com.example.game2dfighting.game.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.BitmapFactory;

public class SpriteAnim {
    private final Bitmap[] frames;
    private final int frameCount;
    private final long frameDurationMs; // thời gian mỗi frame
    private final boolean loop;
    private long elapsedMs = 0;
    private int currentIdx = 0;

    // tuỳ chọn: lật ngang khi quay trái
    private boolean flipX = false;

    // scale đích (pixel in-game)
    private int destW, destH;

    public SpriteAnim(Bitmap[] frames, int frameDurationMs, boolean loop, int destW, int destH) {
        this.frames = frames;
        this.frameCount = frames.length;
        this.frameDurationMs = Math.max(16, frameDurationMs);
        this.loop = loop;
        this.destW = destW;
        this.destH = destH;
    }

    public void setFlipX(boolean flip) { this.flipX = flip; }

    public void reset() {
        elapsedMs = 0;
        currentIdx = 0;
    }

    public boolean isFinished() {
        return !loop && (currentIdx >= frameCount - 1);
    }

    public void update(long dtMs) {
        if (frameCount <= 1) return;
        elapsedMs += dtMs;
        while (elapsedMs >= frameDurationMs) {
            elapsedMs -= frameDurationMs;
            if (currentIdx < frameCount - 1) {
                currentIdx++;
            } else if (loop) {
                currentIdx = 0;
            }
        }
    }

    public void draw(Canvas c, float x, float y, Paint p) {
        Bitmap src = frames[Math.min(currentIdx, frameCount - 1)];
        if (src == null) return;

        // Vẽ theo đích destW/destH, neo (x,y) là góc trên-trái
        Rect dst = new Rect((int)x, (int)y, (int)(x + destW), (int)(y + destH));

        if (!flipX) {
            c.drawBitmap(src, null, dst, p);
        } else {
            // lật ngang bằng Matrix hoặc bằng canvas scale
            c.save();
            c.scale(-1f, 1f, x + destW / 2f, y + destH / 2f); // lật quanh tâm
            c.drawBitmap(src, null, dst, p);
            c.restore();
        }
    }

    // Helper để load nhanh từ resource ids
    public static Bitmap[] loadFrames(Context ctx, int[] resIds) {
        Bitmap[] arr = new Bitmap[resIds.length];
        for (int i = 0; i < resIds.length; i++) {
            arr[i] = BitmapFactory.decodeResource(ctx.getResources(), resIds[i]);
        }
        return arr;
    }


}
