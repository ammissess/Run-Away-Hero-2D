package com.example.game2dfighting;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "GameView";
    private SurfaceHolder holder;
    private Thread gameThread;
    private boolean isRunning = false;

    private int playerX = 100;
    private int playerY = 100;
    private int playerWidth = 100;
    private int playerHeight = 100;
    private int moveSpeed = 5;

    private boolean movingUp = false;
    private boolean movingDown = false;
    private boolean movingLeft = false;
    private boolean movingRight = false;

    private Paint paint;
    private float angle = 0f; // Góc quay cho cánh quạt và kiếm
    private int mana = 0;     // Điểm mana hiện tại
    private int maxMana = 10; // Giới hạn mana, tăng dần khi nâng cấp

    // Danh sách chấm xanh
    private ArrayList<Point> points = new ArrayList<>();
    private Random random = new Random();

    // Danh sách thanh kiếm
    private ArrayList<Sword> swords = new ArrayList<>();
    private static final int MAX_SWORDS = 14; // Tối đa 14 thanh kiếm

    // ==== Map & Camera ====
    private int mapWidth = 3000;   // Chiều rộng map
    private int mapHeight = 3000;  // Chiều cao map
    private int cameraX = 0;       // Góc trên bên trái của camera
    private int cameraY = 0;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        paint = new Paint();
        paint.setColor(Color.RED);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        // Khởi tạo 1 thanh kiếm ban đầu
        swords.add(new Sword(0)); // Góc ban đầu 0 độ
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "Surface created");
        this.holder = holder;

        if (isRunning && gameThread != null && gameThread.isAlive()) {
            return;
        }

        isRunning = true;
        if (gameThread == null || !gameThread.isAlive()) {
            gameThread = new Thread(this);
            gameThread.start();
        }

        if (points.isEmpty()) {
            int count = 200;
            for (int i = 0; i < count; i++) {
                int maxX = Math.max(1, mapWidth - 40);
                int maxY = Math.max(1, mapHeight - 40);
                int x = random.nextInt(maxX) + 20;
                int y = random.nextInt(maxY) + 20;
                points.add(new Point(x, y));
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "Surface destroyed");
        isRunning = false;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error stopping thread", e);
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            if (!holder.getSurface().isValid()) {
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
                continue;
            }

            long frameStart = System.currentTimeMillis();

            // ===== Update vị trí player =====
            if (movingUp) playerY -= moveSpeed;
            if (movingDown) playerY += moveSpeed;
            if (movingLeft) playerX -= moveSpeed;
            if (movingRight) playerX += moveSpeed;

            // Giới hạn nhân vật trong bản đồ
            if (playerX < 0) playerX = 0;
            if (playerY < 0) playerY = 0;
            if (playerX + playerWidth > mapWidth) playerX = mapWidth - playerWidth;
            if (playerY + playerHeight > mapHeight) playerY = mapHeight - playerHeight;

            // Cập nhật camera
            cameraX = playerX + playerWidth / 2 - getWidth() / 2;
            cameraY = playerY + playerHeight / 2 - getHeight() / 2;
            if (cameraX < 0) cameraX = 0;
            if (cameraY < 0) cameraY = 0;
            cameraX = Math.min(cameraX, Math.max(0, mapWidth - getWidth()));
            cameraY = Math.min(cameraY, Math.max(0, mapHeight - getHeight()));

            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    // Clear màn hình
                    canvas.drawColor(Color.WHITE);

                    // Vẽ nền map
                    Paint mapBg = new Paint();
                    mapBg.setStyle(Paint.Style.FILL);
                    mapBg.setColor(Color.rgb(240, 240, 240));
                    canvas.drawRect(0 - cameraX, 0 - cameraY, mapWidth - cameraX, mapHeight - cameraY, mapBg);

                    // Vẽ nhân vật
                    float drawPlayerX = playerX - cameraX;
                    float drawPlayerY = playerY - cameraY;
                    Rect playerRect = new Rect((int) drawPlayerX, (int) drawPlayerY,
                            (int) (drawPlayerX + playerWidth), (int) (drawPlayerY + playerHeight));
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(playerRect, paint);

                    // Vẽ thanh kiếm
                    drawSwords(canvas, drawPlayerX, drawPlayerY);

                    // Vẽ chấm xanh + xử lý va chạm
                    checkCollisionsAndDrawPoints(canvas);

                    // Vẽ thanh mana
                    drawManaBar(canvas);

                    // Vẽ viền bản đồ
                    float left = 0 - cameraX;
                    float top = 0 - cameraY;
                    float right = mapWidth - cameraX;
                    float bottom = mapHeight - cameraY;
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(8f);
                    paint.setColor(Color.BLACK);
                    canvas.drawRect(left, top, right, bottom, paint);
                    paint.setStyle(Paint.Style.FILL);

                    // Quay các thanh kiếm
                    angle += 3f;
                    if (angle >= 360f) angle -= 360f;
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }

            // Giữ ~60 FPS
            long frameTime = System.currentTimeMillis() - frameStart;
            long sleep = 16 - frameTime;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException e) { Log.e(TAG, "Thread sleep error", e); }
            }
        }
    }

    public void setMovingUp(boolean moving) { this.movingUp = moving; }
    public void setMovingDown(boolean moving) { this.movingDown = moving; }
    public void setMovingLeft(boolean moving) { this.movingLeft = moving; }
    public void setMovingRight(boolean moving) { this.movingRight = moving; }

    public void handleKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP: movingUp = true; break;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN: movingDown = true; break;
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_LEFT: movingLeft = true; break;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT: movingRight = true; break;
        }
    }

    public void handleKeyUp(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP: movingUp = false; break;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN: movingDown = false; break;
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_LEFT: movingLeft = false; break;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT: movingRight = false; break;
        }
    }

    // Lớp nội bộ cho thanh kiếm
    private class Sword {
        float baseAngle;

        Sword(float baseAngle) {
            this.baseAngle = baseAngle;
        }
    }

    private void drawSwords(Canvas canvas, float drawPlayerX, float drawPlayerY) {
        paint.setColor(Color.BLACK);
        float swordLength = playerWidth * 1.5f;
        float swordWidth = playerWidth / 10f;
        float centerX = drawPlayerX + playerWidth / 2f;
        float centerY = drawPlayerY + playerHeight / 2f;

        for (Sword sword : swords) {
            canvas.save();
            float currentAngle = angle + sword.baseAngle;
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

    private class Point {
        int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private void checkCollisionsAndDrawPoints(Canvas canvas) {
        paint.setColor(Color.BLUE);

        for (int i = points.size() - 1; i >= 0; i--) {
            Point p = points.get(i);

            // Vẽ điểm theo camera
            float drawX = p.x - cameraX;
            float drawY = p.y - cameraY;
            canvas.drawCircle(drawX, drawY, 10f, paint);

            // Kiểm tra va chạm
            Rect playerRect = new Rect(playerX, playerY, playerX + playerWidth, playerY + playerHeight);
            Rect pointRect = new Rect(p.x - 10, p.y - 10, p.x + 10, p.y + 10);

            if (Rect.intersects(playerRect, pointRect)) {
                points.remove(i);
                mana += 1;

                // Kiểm tra nâng cấp khi mana đạt giới hạn
                if (mana >= maxMana && swords.size() < MAX_SWORDS) {
                    mana = 0;

                    // Thêm thanh kiếm mới
                    swords.add(new Sword(0)); // tạm angle = 0, lát sẽ set lại

                    // Sắp xếp lại toàn bộ góc các thanh kiếm sao cho đều nhau
                    int n = swords.size();
                    for (int j = 0; j < n; j++) {
                        float angleStep = 360f / n;
                        swords.get(j).baseAngle = j * angleStep;
                    }

                    // Tăng giới hạn mana lên 10% của giá trị ban đầu (30), tối đa 300
                    maxMana = (int) Math.min(300, maxMana * 1.1);
                }

                // Sinh chấm mới ngẫu nhiên trong map
                int x = random.nextInt(mapWidth - 40) + 20;
                int y = random.nextInt(mapHeight - 40) + 20;
                points.add(new Point(x, y));
            }
        }
    }

    private void drawManaBar(Canvas canvas) {
        Paint manaPaint = new Paint();
        manaPaint.setColor(Color.BLUE);
        float manaBarWidth = getWidth() * 0.3f; // 30% chiều rộng màn hình
        float manaBarHeight = 30f;
        float manaBarX = 10f;
        float manaBarY = 10f;

        // Nền xám
        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.GRAY);
        canvas.drawRect(manaBarX, manaBarY, manaBarX + manaBarWidth, manaBarY + manaBarHeight, backgroundPaint);

        // Thanh xanh theo điểm
        float manaWidth = (mana / (float) maxMana) * manaBarWidth;
        canvas.drawRect(manaBarX, manaBarY, manaBarX + manaWidth, manaBarY + manaBarHeight, manaPaint);

        // Text
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20f);
        canvas.drawText("Mana: " + mana + "/" + maxMana, manaBarX, manaBarY + manaBarHeight + 25f, textPaint);
    }
}