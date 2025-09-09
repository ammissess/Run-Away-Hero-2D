package com.example.game2dfighting.view.map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.game.manager.EnemyManager;

import java.util.ArrayList;
import java.util.Random;

/**
 * GameView: SurfaceView điều khiển vòng lặp game.
 * Đã refactor để dùng Player + EnemyManager nhưng giữ nguyên cảm giác chơi cũ.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "GameView";

    // Thread & Surface
    private SurfaceHolder holder;
    private Thread gameThread;
    private volatile boolean isRunning = false;

    // Map & Camera
    private final int mapWidth = 3000;
    private final int mapHeight = 3000;
    private int cameraX = 0; // góc trên trái của camera (tọa độ thế giới)
    private int cameraY = 0;

    // Player (thay cho playerX/Y/Width/Height/Speed)
    private Player player;

    // Điều khiển (để tương thích joystick/phím cũ)
    private boolean movingUp = false, movingDown = false, movingLeft = false, movingRight = false;

    // Kiếm quay quanh player
    private final ArrayList<Sword> swords = new ArrayList<>();
    private static final int MAX_SWORDS = 14;
    private float angle = 0f; // góc quay (độ)

    // Điểm xanh (ăn để tăng mana)
    private final ArrayList<Point> points = new ArrayList<>();
    private int mana = 0;
    private int maxMana = 10;

    // Enemy
    private EnemyManager enemyMgr;

    // Pause
    private volatile boolean paused = false;

    // Vẽ
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        initGame();
    }

    private void initGame() {
        // Player: giữ kích thước & tốc độ giống code cũ
        player = new Player(100, 100, 100, 100);
        player.speed = 5;

        // 1 kiếm ban đầu
        swords.clear();
        swords.add(new Sword(0));

        // Enemy Manager
        enemyMgr = new EnemyManager(mapWidth, mapHeight);

        // Điểm xanh khởi tạo sẽ sinh ở surfaceCreated (vì khi đó đã có surface size)
    }

    // ===== Pause API =====
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            // ngắt input ngay khi pause
            movingUp = movingDown = movingLeft = movingRight = false;
            if (player != null) {
                player.up = player.down = player.left = player.right = false;
            }
        }
    }

    public boolean isPaused() { return paused; }

    // ===== Surface callbacks =====
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated");
        this.holder = holder;

        if (points.isEmpty()) {
            // sinh trước 200 điểm xanh trên map
            final int count = 200;
            for (int i = 0; i < count; i++) {
                int maxX = Math.max(1, mapWidth - 40);
                int maxY = Math.max(1, mapHeight - 40);
                int x = random.nextInt(maxX) + 20;
                int y = random.nextInt(maxY) + 20;
                points.add(new Point(x, y));
            }
        }

        if (!isRunning) {
            isRunning = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed");
        isRunning = false;
        try {
            if (gameThread != null) gameThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error stopping thread", e);
        }
    }

    // ===== Game loop =====
    @Override
    public void run() {
        while (isRunning) {
            if (holder == null || !holder.getSurface().isValid()) {
                sleep(16);
                continue;
            }

            long frameStart = System.currentTimeMillis();

            // -------- Update (logic) --------
            if (!paused) {
                // 1) Cập nhật input -> Player
                player.up = movingUp;
                player.down = movingDown;
                player.left = movingLeft;
                player.right = movingRight;

                // 2) Player.update()
                player.update();

                // 3) Giới hạn Player trong bản đồ
                if (player.x < 0) player.x = 0;
                if (player.y < 0) player.y = 0;
                if (player.x + player.w > mapWidth) player.x = mapWidth - player.w;
                if (player.y + player.h > mapHeight) player.y = mapHeight - player.h;

                // 4) Camera bám Player
                cameraX = (int) (player.centerX() - getWidth() / 2f);
                cameraY = (int) (player.centerY() - getHeight() / 2f);
                if (cameraX < 0) cameraX = 0;
                if (cameraY < 0) cameraY = 0;
                cameraX = Math.min(cameraX, Math.max(0, mapWidth - getWidth()));
                cameraY = Math.min(cameraY, Math.max(0, mapHeight - getHeight()));

                // 5) Quay kiếm
                angle += 3f;
                if (angle >= 360f) angle -= 360f;

                // 6) Enemy spawn & move
                enemyMgr.maybeSpawn();
                enemyMgr.updateTowardsPlayer(player);

                // 7) Điểm xanh (ăn để tăng mana, có thể nâng cấp kiếm)
                updatePointsAndLevelUpIfNeeded();
            }

            // -------- Render (vẽ) --------
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    // Nền trắng
                    canvas.drawColor(Color.WHITE);

                    // Vẽ map nền (xám nhạt)
                    Paint mapBg = new Paint();
                    mapBg.setStyle(Paint.Style.FILL);
                    mapBg.setColor(Color.rgb(240, 240, 240));
                    canvas.drawRect(0 - cameraX, 0 - cameraY, mapWidth - cameraX, mapHeight - cameraY, mapBg);

                    // Vẽ Player (hình chữ nhật đỏ như cũ)
                    float drawPlayerX = player.x - cameraX;
                    float drawPlayerY = player.y - cameraY;
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.RED);
                    canvas.drawRect(drawPlayerX, drawPlayerY, drawPlayerX + player.w, drawPlayerY + player.h, paint);

                    // Vẽ Enemy (chấm đỏ nhỏ)
                    enemyMgr.draw(canvas, cameraX, cameraY);

                    // Vẽ kiếm quay quanh Player
                    drawSwords(canvas, drawPlayerX, drawPlayerY);

                    // Kiểm tra va chạm kiếm - quái (và remove nếu trúng)
                    checkEnemiesHitBySwords();

                    // Vẽ điểm xanh (khi pause chỉ vẽ; khi chạy, logic ăn điểm đã xử lý ở update)
                    drawPoints(canvas);

                    // Thanh mana
                    drawManaBar(canvas);

                    // Viền bản đồ
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(8f);
                    paint.setColor(Color.BLACK);
                    float left = 0 - cameraX, top = 0 - cameraY, right = mapWidth - cameraX, bottom = mapHeight - cameraY;
                    canvas.drawRect(left, top, right, bottom, paint);
                    paint.setStyle(Paint.Style.FILL);

                    // Overlay "PAUSED" (nếu bạn dùng overlay XML thì có thể bỏ khối này)
                    if (paused) {
                        Paint dim = new Paint();
                        dim.setColor(Color.argb(120, 0, 0, 0));
                        canvas.drawRect(0, 0, getWidth(), getHeight(), dim);

                        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                        t.setColor(Color.WHITE);
                        t.setTextSize(64f);
                        t.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("PAUSED", getWidth() / 2f, getHeight() / 2f, t);
                    }

                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }

            // 60 FPS (xấp xỉ)
            long dt = System.currentTimeMillis() - frameStart;
            long sleep = 16 - dt;
            if (sleep > 0) sleep(sleep);
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignore) {}
    }

    // ===== Điều khiển (giữ tương thích với Joystick/Key) =====
    public void setMovingUp(boolean v)    { this.movingUp = v; }
    public void setMovingDown(boolean v)  { this.movingDown = v; }
    public void setMovingLeft(boolean v)  { this.movingLeft = v; }
    public void setMovingRight(boolean v) { this.movingRight = v; }

    public void handleKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:    movingUp = true; break;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:  movingDown = true; break;
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_LEFT:  movingLeft = true; break;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT: movingRight = true; break;
        }
    }

    public void handleKeyUp(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:    movingUp = false; break;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:  movingDown = false; break;
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_LEFT:  movingLeft = false; break;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT: movingRight = false; break;
        }
    }

    // ===== Kiếm quay quanh nhân vật =====
    private static class Sword {
        float baseAngle; // độ
        Sword(float baseAngle) { this.baseAngle = baseAngle; }
    }

    private void drawSwords(Canvas canvas, float drawPlayerX, float drawPlayerY) {
        // vẽ theo toạ độ màn hình (đã trừ camera)
        paint.setColor(Color.BLACK);
        float swordLength = player.w * 1.5f;
        float swordWidth  = player.w / 10f;
        float centerX = drawPlayerX + player.w / 2f;
        float centerY = drawPlayerY + player.h / 2f;

        for (Sword sword : swords) {
            canvas.save();
            float currentAngle = angle + sword.baseAngle; // độ
            canvas.rotate(currentAngle, centerX, centerY);
            canvas.drawRect(
                    centerX - swordWidth / 2f,
                    centerY,
                    centerX + swordWidth / 2f,
                    centerY + swordLength,
                    paint
            );
            canvas.restore();
        }
    }

    private void checkEnemiesHitBySwords() {
        // dùng toạ độ thế giới cho va chạm
        float cx = player.centerX();
        float cy = player.centerY();
        float swordLen = player.w * 1.5f;
        float halfThickness = player.w / 20f; // nửa bề dày hitbox
        float[] baseAngles = new float[swords.size()];
        for (int i = 0; i < swords.size(); i++) baseAngles[i] = swords.get(i).baseAngle;

        enemyMgr.removeIfHitBySwords(cx, cy, swordLen, halfThickness, angle, baseAngles);
    }

    // ===== Điểm xanh (mana) =====
    private static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    private void updatePointsAndLevelUpIfNeeded() {
        // Dùng player rect ở toạ độ thế giới
        Rect playerRect = new Rect(player.x, player.y, player.x + player.w, player.y + player.h);

        for (int i = points.size() - 1; i >= 0; i--) {
            Point p = points.get(i);

            Rect pointRect = new Rect(p.x - 10, p.y - 10, p.x + 10, p.y + 10);
            if (Rect.intersects(playerRect, pointRect)) {
                points.remove(i);
                mana += 1;

                // Nâng cấp kiếm khi đủ mana
                if (mana >= maxMana && swords.size() < MAX_SWORDS) {
                    mana = 0;
                    swords.add(new Sword(0));
                    // sắp đều góc
                    int n = swords.size();
                    for (int j = 0; j < n; j++) {
                        float step = 360f / n;
                        swords.get(j).baseAngle = j * step;
                    }
                    // tăng max mana 10% tới trần 300
                    maxMana = (int) Math.min(300, maxMana * 1.1);
                }

                // Sinh lại 1 điểm mới ngẫu nhiên
                int x = random.nextInt(mapWidth - 40) + 20;
                int y = random.nextInt(mapHeight - 40) + 20;
                points.add(new Point(x, y));
            }
        }
    }

    private void drawPoints(Canvas canvas) {
        paint.setColor(Color.BLUE);
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            float sx = p.x - cameraX;
            float sy = p.y - cameraY;
            canvas.drawCircle(sx, sy, 10f, paint);
        }
    }

    private void drawManaBar(Canvas canvas) {
        Paint manaPaint = new Paint();
        manaPaint.setColor(Color.BLUE);

        float w = getWidth() * 0.3f;
        float h = 30f;
        float x = 10f, y = 10f;

        // nền
        Paint bg = new Paint();
        bg.setColor(Color.GRAY);
        canvas.drawRect(x, y, x + w, y + h, bg);

        // fill theo mana
        float fill = (mana / (float) maxMana) * w;
        canvas.drawRect(x, y, x + fill, y + h, manaPaint);

        // text
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Color.WHITE);
        t.setTextSize(20f);
        canvas.drawText("Mana: " + mana + "/" + maxMana, x, y + h + 25f, t);
    }
}
