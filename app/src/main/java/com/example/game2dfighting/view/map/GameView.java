package com.example.game2dfighting.view.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import java.util.Locale;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.game.manager.EnemyManager;

import java.util.ArrayList;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "GameView";
    private SurfaceHolder holder;
    private Thread gameThread;
    private volatile boolean isRunning = false;

    // Map & Camera
    private int mapWidth, mapHeight;

    // CAMERA ở HỆ SKY
    private int cameraX = 0, cameraY = 0;

    // Entities
    private Player player;
    private EnemyManager enemyMgr;

    // Input (từ joystick/phím)
    private boolean movingUp, movingDown, movingLeft, movingRight;

    // Điểm xanh (tăng mana)
    private final ArrayList<Point> points = new ArrayList<>();

    // Vẽ & RNG
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    // Pause
    private volatile boolean paused = false;

    // Game over callback
    public interface GameEventListener { void onGameOver(); }
    private GameEventListener listener;
    public void setGameEventListener(GameEventListener l) { this.listener = l; }

    // ===== Parallax background =====
    private Bitmap bmpSky, bmpIsland, bmpCloud;
    private int skyW, skyH;            // kích thước sky đã scale
    private int islandX, islandY;      // toạ độ đặt đảo ở GIỮA sky (tọa độ thế giới SKY)

    // Nhiều mây hơn + nhanh hơn
    private final ArrayList<Cloud> clouds = new ArrayList<>();
    private static class Cloud {
        float x, y;      // vị trí trong hệ SKY
        float v;         // px/frame (tốc độ trôi)
        float scale;     // 0.5..1.6 => kích thước mây
        float alpha;     // 0.30..1.0 => độ mờ (xa thì mờ)
        float parallax;  // 0.10..0.38 => lớp xa-gần
        int   w, h;      // kích thước vẽ sau scale (để wrap)

        Cloud(float x, float y, float v, float scale, float alpha, float parallax, int baseW, int baseH) {
            this.x = x; this.y = y; this.v = v;
            this.scale = scale; this.alpha = alpha; this.parallax = parallax;
            this.w = (int) (baseW * scale);
            this.h = (int) (baseH * scale);
        }
    }

    // --- TIMER HUD ---
    private long timerStartMs = 0L;        // thời điểm bắt đầu Level
    private long pausedAccumulatedMs = 0L; // tổng thời gian đã pause
    private long pauseStartMs = 0L;        // thời điểm bắt đầu pause
    private boolean timerPaused = false;   // cờ đang pause hay không

    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // game over
    private volatile boolean gameOver = false;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        // TIMER HUD setup...
        timerStartMs = System.currentTimeMillis();
        pausedAccumulatedMs = 0L;
        timerPaused = false;

        timePaint.setColor(Color.WHITE);
        timePaint.setTextSize(64f);
        timePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTypeface(Typeface.MONOSPACE);
        timePaint.setShadowLayer(6f, 0f, 0f, Color.BLACK);
        timeBgPaint.setColor(Color.argb(120, 0, 0, 0));

        resetTimer();
    }

    private void initGame() {
        // hiện đang khởi tạo trong surfaceCreated() sau khi biết kích thước map/sky
    }

    // --- TIMER UTILS ---
    public void resetTimer() {
        timerStartMs = System.currentTimeMillis();
        pausedAccumulatedMs = 0L;
        timerPaused = false;
    }

    public void pauseTimer() {
        if (!timerPaused) {
            timerPaused = true;
            pauseStartMs = System.currentTimeMillis();
        }
    }

    public void resumeTimer() {
        if (timerPaused) {
            pausedAccumulatedMs += System.currentTimeMillis() - pauseStartMs;
            timerPaused = false;
        }
    }

    public int getElapsedSeconds() {
        long now = System.currentTimeMillis();
        long elapsedMs = timerPaused
                ? (pauseStartMs - timerStartMs - pausedAccumulatedMs)
                : (now - timerStartMs - pausedAccumulatedMs);
        if (elapsedMs < 0) elapsedMs = 0;
        return (int)(elapsedMs / 1000);
    }

    // ===== Pause API =====
    public void setPaused(boolean paused) {
        this.paused = paused;

        if (paused) {
            // tắt input
            movingUp = movingDown = movingLeft = movingRight = false;
            player.up = player.down = player.left = player.right = false;

            // ----- PAUSE TIMER -----
            if (!timerPaused) {
                timerPaused = true;
                pauseStartMs = System.currentTimeMillis();
            }
        } else {
            // ----- RESUME TIMER -----
            if (timerPaused) {
                pausedAccumulatedMs += System.currentTimeMillis() - pauseStartMs;
                timerPaused = false;
            }
        }
    }

    public boolean isPaused() { return paused; }

    // ===== Surface =====
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        this.holder = holder;

        // === 2) ISLAND (map) – load TRƯỚC ===
        Bitmap srcIsland = BitmapFactory.decodeResource(getResources(), R.drawable.bg_map_level1_island);
        bmpIsland = srcIsland; // có thể scale nếu muốn
        mapWidth  = bmpIsland.getWidth();
        mapHeight = bmpIsland.getHeight();

        // Tạo player sau khi đã biết kích thước map — đặt GIỮA ĐẢO
        int playerW = 400, playerH = 400;
        int startX = mapWidth / 2 - playerW / 2;
        int startY = mapHeight / 2 - playerH / 2;
        player = new Player(getContext(), startX, startY, playerW, playerH);
        player.setMaxMana(10);

        // EnemyManager dùng kích thước map
        enemyMgr = new EnemyManager(getContext(), mapWidth, mapHeight);

        // === 1) SKY – load SAU khi biết mapWidth/Height ===
        Bitmap srcSky = BitmapFactory.decodeResource(getResources(), R.drawable.bg_map_level1_sky);
        int viewW = getWidth(), viewH = getHeight();
        int skyMargin = Math.max(viewW, viewH);
        skyW = Math.max(mapWidth  + skyMargin*2, (int)(viewW * 1.5f));
        skyH = Math.max(mapHeight + skyMargin*2, (int)(viewH * 1.5f));
        bmpSky = Bitmap.createScaledBitmap(srcSky, skyW, skyH, false);

        // Tọa độ đặt đảo GIỮA sky (đảo nằm trong không trung)
        islandX = (skyW - mapWidth) / 2;
        islandY = (skyH - mapHeight) / 2;

        // === 3) CLOUDS ===
        bmpCloud = BitmapFactory.decodeResource(getResources(), R.drawable.bg_map_level1_clouds);
        clouds.clear();
        Random r = new Random();

        // Tăng số lượng mây & nhiều lớp cao độ
        int totalClouds = 150;
        int rows = 8;         // nhiều dải cao độ

        int baseW = bmpCloud.getWidth();
        int baseH = bmpCloud.getHeight();

        for (int i = 0; i < totalClouds; i++) {
            // dải cao độ + nhiễu
            float row = r.nextInt(rows);
            float bandTop = skyH * 0.14f;
            float bandStep = skyH * 0.09f;
            float y = bandTop + row * bandStep + r.nextFloat() * (bandStep * 0.6f);

            // rải vị trí X toàn bầu trời
            float x = r.nextInt(skyW);

            // độ sâu: xa thì nhỏ, mờ, chậm — gần thì to, rõ, nhanh
            float depth = r.nextFloat(); // 0..1 (0 = xa nhất)

            float parallax = 0.10f + depth * 0.28f;           // 0.10..0.38
            float scale    = 0.50f + depth * 1.10f;           // 0.50..1.60
            float alpha    = 0.30f + depth * 0.70f;           // 0.30..1.00

            // TỐC ĐỘ NHANH HƠN: 1.2 .. ~6.2 px/frame (tùy depth) + chút random
            float v        = 1.2f + depth * 4.2f + r.nextFloat() * 0.8f;

            clouds.add(new Cloud(x, y, v, scale, alpha, parallax, baseW, baseH));
        }

        // Points theo toạ độ MAP
        if (points.isEmpty()) {
            int count = 200;
            for (int i = 0; i < count; i++) {
                int x = r.nextInt(Math.max(1, mapWidth - 40)) + 20;
                int y = r.nextInt(Math.max(1, mapHeight - 40)) + 20;
                points.add(new Point(x, y));
            }
        }

        // === CAMERA ở HỆ SKY: nhìn vào player (đang ở map) + offset đảo ===
        cameraX = (int)((player.centerX() + islandX) - getWidth()/2f);
        cameraY = (int)((player.centerY() + islandY) - getHeight()/2f);
        clampCamera();

        if (!isRunning) {
            isRunning = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        isRunning = false;
        try { if (gameThread != null) gameThread.join(); } catch (InterruptedException e) { Log.e(TAG, "stop", e); }
    }

    // ===== Loop =====
    @Override
    public void run() {
        long lastUpdateMs = System.currentTimeMillis();

        while (isRunning) {
            if (holder == null || !holder.getSurface().isValid()) { sleep(16); continue; }

            // Tính delta-time cho frame hiện tại
            long now  = System.currentTimeMillis();
            long dtMs = now - lastUpdateMs;
            lastUpdateMs = now;
            if (dtMs < 0)   dtMs = 0;
            if (dtMs > 100) dtMs = 100; // clamp để tránh giật khi app bị treo tạm

            if (!paused && !gameOver) {

                // ==== UPDATE CLOUDS ====
                for (Cloud c : clouds) {
                    c.x += c.v; // trôi sang phải nhanh hơn
                    if (c.x > skyW + c.w) {
                        // loop lại từ trái với một cao độ mới nhẹ để đỡ trùng lặp
                        c.x = -c.w;
                        c.y += (random.nextFloat() - 0.5f) * (skyH * 0.06f);
                        if (c.y < skyH * 0.12f) c.y = skyH * 0.12f;
                        if (c.y > skyH * 0.78f) c.y = skyH * 0.78f;
                    }
                }

                // input -> player
                player.up = movingUp;
                player.down = movingDown;
                player.left = movingLeft;
                player.right = movingRight;

                // update player (dùng dtMs)
                player.update(dtMs);
                clampPlayerToMap();

                // === camera follow ở HỆ SKY ===
                cameraX = (int)((player.centerX() + islandX) - getWidth()/2f);
                cameraY = (int)((player.centerY() + islandY) - getHeight()/2f);
                clampCamera();

                // enemies
                enemyMgr.maybeSpawn();
                enemyMgr.updateTowardsPlayer(player, dtMs);

                if (player.getHp() <= 0) {
                    gameOver = true;
                    timerPaused = true;
                    if (listener != null) listener.onGameOver();
                }

                updatePointsAndLevelUpIfNeeded();
            }

            // render
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    render(canvas);
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }

            // điều tiết tốc độ ~60fps
            long frameTime = System.currentTimeMillis() - now;
            long sleep = 16 - frameTime;
            if (sleep > 0) sleep(sleep);

            if (gameOver) isRunning = false;
        }
    }

    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignore) {} }

    // ===== Input API =====
    public void setMovingUp(boolean v)    { movingUp = v; }
    public void setMovingDown(boolean v)  { movingDown = v; }
    public void setMovingLeft(boolean v)  { movingLeft = v; }
    public void setMovingRight(boolean v) { movingRight = v; }

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

    // ===== Update helpers =====
    private void clampPlayerToMap() {
        if (player.x < 0) player.x = 0;
        if (player.y < 0) player.y = 0;
        if (player.x + player.w > mapWidth)  player.x = mapWidth - player.w;
        if (player.y + player.h > mapHeight) player.y = mapHeight - player.h;
    }

    // KẸP CAMERA THEO BIÊN BẦU TRỜI (SKY)
    private void clampCamera() {
        if (cameraX < 0) cameraX = 0;
        if (cameraY < 0) cameraY = 0;
        int maxX = Math.max(0, skyW - getWidth());
        int maxY = Math.max(0, skyH - getHeight());
        if (cameraX > maxX) cameraX = maxX;
        if (cameraY > maxY) cameraY = maxY;
    }

    private static class Point { int x,y; Point(int x,int y){this.x=x;this.y=y;} }

    private void updatePointsAndLevelUpIfNeeded() {
        Rect playerRect = new Rect(player.x, player.y, player.x + player.w, player.y + player.h);
        for (int i = points.size() - 1; i >= 0; i--) {
            Point p = points.get(i);
            Rect pointRect = new Rect(p.x - 10, p.y - 10, p.x + 10, p.y + 10);
            if (Rect.intersects(playerRect, pointRect)) {
                points.remove(i);

                // tăng mana trong Player
                player.addMana(1);

                // spawn lại 1 điểm mới
                int x = random.nextInt(mapWidth - 40) + 20;
                int y = random.nextInt(mapHeight - 40) + 20;
                points.add(new Point(x, y));
            }
        }
    }

    // ===== Render =====
    private void render(Canvas canvas) {
        // WORLD ORIGIN: (0,0) là góc trái trên của SKY

        // === 1) SKY (nằm sau cùng) ===
        float skyParallax = 0.2f; // parallax nhẹ cho nền trời lớn
        float skyDrawX = -cameraX * skyParallax;
        float skyDrawY = -cameraY * skyParallax;
        canvas.drawBitmap(bmpSky, skyDrawX, skyDrawY, null);

        // === 2) CLOUDS (giữa Sky và Island) ===
        // Vẽ theo parallax/alpha/scale riêng từng mây
        for (Cloud c : clouds) {
            float drawX = c.x - cameraX * c.parallax;
            float drawY = c.y - cameraY * c.parallax;

            int left = (int) drawX;
            int top  = (int) drawY;
            int right = left + c.w;
            int bottom = top + c.h;

            int a = (int) (c.alpha * 255f);
            paint.setAlpha(a);
            canvas.drawBitmap(bmpCloud, null, new Rect(left, top, right, bottom), paint);
        }
        // Khôi phục alpha cho các phần vẽ sau
        paint.setAlpha(255);

        // === 3) ISLAND (map, che một phần mây) ===
        float islandDrawX = islandX - cameraX;
        float islandDrawY = islandY - cameraY;
        canvas.drawBitmap(bmpIsland, islandDrawX, islandDrawY, null);

        // === 4) ENTITIES trên đảo (ở HỆ MAP, nên trừ camera - island offset) ===
        player.draw(canvas, cameraX - islandX, cameraY - islandY, paint);
        enemyMgr.draw(canvas, cameraX - islandX, cameraY - islandY);

        // === 5) POINTS (chấm xanh) cũng theo map ===
        drawPointsOnIsland(canvas);

        // === 6) HUD, viền, overlay ===
        drawHud(canvas);

        // Viền map theo biên đảo (tuỳ thích)
//        paint.setStyle(Paint.Style.STROKE);
//        paint.setStrokeWidth(8f);
//        paint.setColor(Color.BLACK);
//        canvas.drawRect(islandDrawX, islandDrawY,
//                islandDrawX + mapWidth, islandDrawY + mapHeight, paint);
//        paint.setStyle(Paint.Style.FILL);

        if (paused) {
            Paint dim = new Paint();
            dim.setColor(Color.argb(120, 0, 0, 0));
            canvas.drawRect(0, 0, getWidth(), getHeight(), dim);

            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(Color.WHITE);
            t.setTextSize(64f);
            t.setTextAlign(Paint.Align.CENTER);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            canvas.drawText("RESUME", centerX, centerY, t);
            canvas.drawText("QUIT", centerX, centerY + 100, t);
        }
    }

    private void drawPointsOnIsland(Canvas canvas) {
        paint.setColor(Color.parseColor("#66FFFF"));
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            // p.x, p.y ở MAP → cộng island offset rồi trừ camera (hệ SKY)
            float sx = (p.x + islandX) - cameraX;
            float sy = (p.y + islandY) - cameraY;
            canvas.drawCircle(sx, sy, 10f, paint);
        }
    }

    private void drawHud(Canvas canvas) {
        int w = (int) (getWidth() * 0.3f);
        drawBar(canvas, 10, 10,  w, 24, player.getMana(),  player.getMaxMana(), 0xFF1E88E5, "Mana");
        drawBar(canvas, 10, 44,  w, 24, player.getHp(),    player.getMaxHp(),   0xFFE53935, "HP");
        drawBar(canvas, 10, 78,  w, 24, player.getEnergy(), player.getMaxEnergy(), 0xFFFFFF99, "Energy");

        // --- DRAW TIMER (center-top) ---
        long now = System.currentTimeMillis();
        long elapsedMs = timerPaused
                ? (pauseStartMs - timerStartMs - pausedAccumulatedMs)
                : (now - timerStartMs - pausedAccumulatedMs);

        if (elapsedMs < 0) elapsedMs = 0;

        int totalSec = (int) (elapsedMs / 1000);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        String timeText = String.format(Locale.getDefault(), "%02d:%02d", min, sec);

        float cx = canvas.getWidth() / 2f;
        float topPadding = 24f;
        float textWidth = timePaint.measureText(timeText);
        Paint.FontMetrics fm = timePaint.getFontMetrics();
        float textHeight = fm.bottom - fm.top;

        float paddingX = 24f;
        float paddingY = 12f;
        float rectLeft   = cx - textWidth / 2f - paddingX;
        float rectTop    = topPadding;
        float rectRight  = cx + textWidth / 2f + paddingX;
        float rectBottom = rectTop + textHeight + paddingY * 2f;

        canvas.drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, 24f, 24f, timeBgPaint);

        float baselineY = rectTop + paddingY - fm.top;
        canvas.drawText(timeText, cx, baselineY, timePaint);
    }

    private void drawBar(Canvas c, int x, int y, int w, int h, int value, int max, int color, String label) {
        Paint bg = new Paint(); bg.setColor(0xFF333333);
        c.drawRect(x, y, x+w, y+h, bg);
        float ratio = Math.max(0f, Math.min(1f, max == 0 ? 0f : (value / (float) max)));
        Paint fill = new Paint(); fill.setColor(color);
        c.drawRect(x, y, x + (int)(w * ratio), y + h, fill);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE); text.setTextSize(18f);
        c.drawText(label + ": " + value + "/" + max, x, y + h + 20, text);
    }
}
