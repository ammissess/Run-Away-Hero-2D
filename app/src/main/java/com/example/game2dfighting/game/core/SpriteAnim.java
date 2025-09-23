package com.example.game2dfighting.game.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

public class SpriteAnim {
    private final Bitmap[] frames;
    private final int frameCount;
    private final int frameDurationMs; // ms mỗi frame
    private final boolean loop;
    private long elapsedMs = 0;
    private int currentIdx = 0;

    // tuỳ chọn: lật ngang khi quay trái
    private boolean flipX = false;

    // scale đích (pixel in-game)
    private int destW, destH;

    // ----- Tuỳ chọn vẽ pixel-art sắc nét -----
    private boolean pixelArt = true; // tắt khử răng cưa & lọc

    // ===== CTORs =====
    public SpriteAnim(Bitmap[] frames, int frameDurationMs, boolean loop, int destW, int destH) {
        this.frames = frames;
        this.frameCount = frames.length;
        this.frameDurationMs = Math.max(16, frameDurationMs);
        this.loop = loop;
        this.destW = destW;
        this.destH = destH;
    }

    public void setFlipX(boolean flip) { this.flipX = flip; }
    public void setPixelArt(boolean pixelArt) { this.pixelArt = pixelArt; }

    public void reset() { elapsedMs = 0; currentIdx = 0; }

    public boolean isFinished() { return !loop && (currentIdx >= frameCount - 1); }

    public void update(long dtMs) {
        if (frameCount <= 1) return;
        elapsedMs += dtMs;
        while (elapsedMs >= frameDurationMs) {
            elapsedMs -= frameDurationMs;
            if (currentIdx < frameCount - 1) currentIdx++;
            else if (loop) currentIdx = 0;
        }
    }

    public void draw(Canvas c, float x, float y, Paint p) {
        if (frameCount == 0) return;
        Bitmap src = frames[Math.min(currentIdx, frameCount - 1)];
        if (src == null) return;

        // Tạo Paint riêng nếu cần để không ảnh hưởng Paint từ ngoài truyền vào
        Paint use = (p != null) ? p : new Paint();
        if (pixelArt) {
            use.setFilterBitmap(false);
            use.setAntiAlias(false);
            use.setDither(false);
        }

        Rect dst = new Rect((int)x, (int)y, (int)(x + destW), (int)(y + destH));

        if (!flipX) {
            c.drawBitmap(src, null, dst, use);
        } else {
            // lật quanh tâm đích
            c.save();
            c.scale(-1f, 1f, x + destW / 2f, y + destH / 2f);
            c.drawBitmap(src, null, dst, use);
            c.restore();
        }
    }

    // ======= Helpers: load frames (thường) & load frames đã TRIM =======
    public static Bitmap[] loadFrames(Context ctx, int[] resIds) {
        Bitmap[] arr = new Bitmap[resIds.length];
        for (int i = 0; i < resIds.length; i++) {
            arr[i] = BitmapFactory.decodeResource(ctx.getResources(), resIds[i]);
        }
        return arr;
    }

    public static Bitmap[] loadFramesTrimmed(Context ctx, int[] resIds) {
        Bitmap[] arr = new Bitmap[resIds.length];
        for (int i = 0; i < resIds.length; i++) {
            Bitmap raw = BitmapFactory.decodeResource(ctx.getResources(), resIds[i]);
            arr[i] = trimTransparent(raw);
        }
        return arr;
    }

    /** Cắt bỏ phần viền trong suốt (alpha=0) ở 4 phía. Làm 1 lần khi load. */
    public static Bitmap trimTransparent(Bitmap src) {
        if (src == null) return null;
        final int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);

        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int a = (px[row + x] >>> 24) & 0xFF; // alpha
                if (a != 0) {
                    if (x < left)   left = x;
                    if (x > right)  right = x;
                    if (y < top)    top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < left || bottom < top) return src; // toàn rỗng
        return Bitmap.createBitmap(src, left, top, right - left + 1, bottom - top + 1);
    }

    // ======= APIs tiện cho Enemy/Player (reflection trong code của bạn) =======
    public long getTotalDurationMs() { return (long) frameCount * frameDurationMs; }
    public int  getFrameCount()      { return frameCount; }
    public int  getFrameDurationMs() { return frameDurationMs; }
}
