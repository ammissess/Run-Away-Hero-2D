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
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.game.entity.Enemy;
import com.example.game2dfighting.game.manager.EnemyManager;
import com.example.game2dfighting.game.projectile.Bullet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "GameView";
    private SurfaceHolder holder;
    private Thread gameThread;
    private volatile boolean isRunning = false;

    // Map (ISLAND) & Sky & Camera (camera đang ở HỆ SKY)
    private int mapWidth, mapHeight;
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
    private int islandX, islandY;      // toạ độ đảo bên trong SKY (offset từ SKY → MAP)

    // Mây
    private final ArrayList<Cloud> clouds = new ArrayList<>();
    private static class Cloud {
        float x, y; float v; float scale; float alpha; float parallax; int w, h;
        Cloud(float x, float y, float v, float scale, float alpha, float parallax, int baseW, int baseH) {
            this.x = x; this.y = y; this.v = v;
            this.scale = scale; this.alpha = alpha; this.parallax = parallax;
            this.w = (int) (baseW * scale);
            this.h = (int) (baseH * scale);
        }
    }

    // --- TIMER HUD ---
    private long timerStartMs = 0L;
    private long pausedAccumulatedMs = 0L;
    private long pauseStartMs = 0L;
    private boolean timerPaused = false;
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // game over
    private volatile boolean gameOver = false;

    // ================== BẮN ĐẠN ==================
    private final List<Bullet> bullets = new ArrayList<>();
    private long nextShootAtMs = 0L;
    private static final long SHOOT_COOLDOWN_MS = 250L;

    // Nút bắn & nút pause (UI screen space)
    private Rect fireBtnRect;
    private float fireBtnRadiusPx;

    private Rect pauseBtnRect;
    private float pauseBtnRadiusPx;

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
        player.setMaxMana(0);

        // EnemyManager dùng kích thước map
        enemyMgr = new EnemyManager(getContext(), mapWidth, mapHeight);

        // === 1) SKY – load SAU khi biết mapWidth/Height ===
        Bitmap srcSky = BitmapFactory.decodeResource(getResources(), R.drawable.bg_map_level1_sky);
        int viewW = getWidth(), viewH = getHeight();
        int skyMargin = Math.max(viewW, viewH);
        skyW = Math.max(mapWidth  + skyMargin*2, (int)(viewW * 1.5f));
        skyH = Math.max(mapHeight + skyMargin*2, (int)(viewH * 1.5f));
        bmpSky = Bitmap.createScaledBitmap(srcSky, skyW, skyH, false);

        // Tọa độ đặt đảo GIỮA sky
        islandX = (skyW - mapWidth) / 2;
        islandY = (skyH - mapHeight) / 2;

        // === 3) CLOUDS ===
        bmpCloud = BitmapFactory.decodeResource(getResources(), R.drawable.bg_map_level1_clouds);
        clouds.clear();
        Random r = new Random();

        int totalClouds = 150;
        int rows = 8;
        int baseW = bmpCloud.getWidth();
        int baseH = bmpCloud.getHeight();

        for (int i = 0; i < totalClouds; i++) {
            float row = r.nextInt(rows);
            float bandTop = skyH * 0.14f;
            float bandStep = skyH * 0.09f;
            float y = bandTop + row * bandStep + r.nextFloat() * (bandStep * 0.6f);
            float x = r.nextInt(skyW);

            float depth = r.nextFloat(); // 0..1
            float parallax = 0.10f + depth * 0.28f;     // 0.10..0.38
            float scale    = 0.50f + depth * 1.10f;     // 0.50..1.60
            float alpha    = 0.30f + depth * 0.70f;     // 0.30..1.00
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

        // === CAMERA ở HỆ SKY ===
        cameraX = (int)((player.centerX() + islandX) - getWidth()/2f);
        cameraY = (int)((player.centerY() + islandY) - getHeight()/2f);
        clampCamera();

        // === Setup nút bắn + pause (UI screen space) ===
        setupButtons(getWidth(), getHeight());

        if (!isRunning) {
            isRunning = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        setupButtons(width, height);
    }

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

            long now  = System.currentTimeMillis();
            long dtMs = now - lastUpdateMs;
            lastUpdateMs = now;
            if (dtMs < 0)   dtMs = 0;
            if (dtMs > 100) dtMs = 100; // clamp

            if (!paused && !gameOver) {

                // ==== UPDATE CLOUDS ====
                for (Cloud c : clouds) {
                    c.x += c.v;
                    if (c.x > skyW + c.w) {
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

                // update player (dtMs)
                player.update(dtMs);
                clampPlayerToMap();

                // camera follow (HỆ SKY)
                cameraX = (int)((player.centerX() + islandX) - getWidth()/2f);
                cameraY = (int)((player.centerY() + islandY) - getHeight()/2f);
                clampCamera();

                // enemies
                enemyMgr.maybeSpawn();
                enemyMgr.updateTowardsPlayer(player, dtMs);

                // ====== UPDATE BULLETS & COLLISION ======
                float dtSec = dtMs / 1000f;
                for (int i = bullets.size() - 1; i >= 0; i--) {
                    Bullet b = bullets.get(i);
                    b.update(dtSec);
                    if (!b.alive) {
                        bullets.remove(i);
                        continue;
                    }

                    boolean hit = false;
                    for (Enemy en : enemyMgr.list()) {
                        try {
                            if (en.getState() == Enemy.State.DIE) continue;
                        } catch (Throwable ignore) {}
                        if (b.hit(en)) {
                            hit = true;
                            enemyMgr.applyBulletHit(en, b.damage);
                            break;
                        }
                    }
                    if (hit) {
                        b.alive = false;
                        bullets.remove(i);
                    }
                }

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

            // ~60fps
            long frameTime = System.currentTimeMillis() - now;
            long sleep = 16 - frameTime;
            if (sleep > 0) sleep(sleep);

            if (gameOver) isRunning = false;
        }
    }

    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignore) {} }

    // ===== Input API (phím/joystick) =====
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

    // ===== TOUCH: Nút Pause + Nút bắn =====
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        final int index = event.getActionIndex();
        final float tx = event.getX(index);
        final float ty = event.getY(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // 1) Pause (trên-phải)
                if (isInPauseButton(tx, ty)) {
                    setPaused(!isPaused());
                    return true;
                }
                // 2) Fire (dưới-phải)
                if (isInFireButton(tx, ty)) {
                    tryShootAtNearestEnemy();
                    return true;
                }
                // 3) Joystick / input khác của bạn để dưới đây nếu có
                break;
            }
            // case MOVE/UP: phần joystick hiện có của bạn
        }
        return true;
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
                player.addMana(1);
                int x = random.nextInt(mapWidth - 40) + 20;
                int y = random.nextInt(mapHeight - 40) + 20;
                points.add(new Point(x, y));
            }
        }
    }

    // ===== Render =====
    private void render(Canvas canvas) {
        // 1) SKY
        float skyParallax = 0.2f;
        float skyDrawX = -cameraX * skyParallax;
        float skyDrawY = -cameraY * skyParallax;
        canvas.drawBitmap(bmpSky, skyDrawX, skyDrawY, null);

        // 2) CLOUDS
        for (Cloud c : clouds) {
            float drawX = c.x - cameraX * c.parallax;
            float drawY = c.y - cameraY * c.parallax;
            int left = (int) drawX, top = (int) drawY, right = left + c.w, bottom = top + c.h;
            int a = (int) (c.alpha * 255f);
            paint.setAlpha(a);
            canvas.drawBitmap(bmpCloud, null, new Rect(left, top, right, bottom), paint);
        }
        paint.setAlpha(255);

        // 3) ISLAND (map)
        float islandDrawX = islandX - cameraX;
        float islandDrawY = islandY - cameraY;
        canvas.drawBitmap(bmpIsland, islandDrawX, islandDrawY, null);

        // 4) ENTITIES (MAP hệ → trừ (camera - island) khi vẽ)
        player.draw(canvas, cameraX - islandX, cameraY - islandY, paint);
        enemyMgr.draw(canvas, cameraX - islandX, cameraY - islandY);

        // 5) BULLETS (MAP hệ)
        for (Bullet b : bullets) {
            b.draw(canvas, cameraX - islandX, cameraY - islandY);
        }

        // 6) POINTS (MAP hệ)
        drawPointsOnIsland(canvas);

        // 7) HUD + Buttons
        drawHud(canvas);
        drawPauseButton(canvas);
        drawFireButton(canvas);

        if (paused) {
            Paint dim = new Paint();
            dim.setColor(Color.argb(120, 0, 0, 0));
            canvas.drawRect(0, 0, getWidth(), getHeight(), dim);

            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(Color.WHITE);
            t.setTextSize(128f);
            t.setTextAlign(Paint.Align.CENTER);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            canvas.drawText("PAUSE", centerX, centerY, t);
            //canvas.drawText("QUIT", centerX, centerY + 100, t);
        }
    }

    private void drawPointsOnIsland(Canvas canvas) {
        paint.setColor(Color.parseColor("#66FFFF"));
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
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

        // TIMER
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

        float paddingX = 24f, paddingY = 12f;
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

    // ===== Buttons layout =====
    private void setupButtons(int w, int h) {
        int margin = (int) (16 * getResources().getDisplayMetrics().density);

        // Fire bottom-right (đối diện joystick)
        int fireSize = (int) (80 * getResources().getDisplayMetrics().density);
        int fireLeft = w - margin - fireSize;
        int fireTop  = h - margin - fireSize;
        fireBtnRect = new Rect(fireLeft, fireTop, fireLeft + fireSize, fireTop + fireSize);
        fireBtnRadiusPx = fireSize / 2f;

        // Pause top-right
        int pauseSize = (int) (64 * getResources().getDisplayMetrics().density);
        int pauseLeft = w - margin - pauseSize;
        int pauseTop  = margin;
        pauseBtnRect = new Rect(pauseLeft, pauseTop, pauseLeft + pauseSize, pauseTop + pauseSize);
        pauseBtnRadiusPx = pauseSize / 2f;
    }

    // ===== Fire button (UI) =====
    private void drawFireButton(Canvas c) {
        if (fireBtnRect == null) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // nền mờ
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(160, 255, 255, 255));
        float cx = fireBtnRect.exactCenterX();
        float cy = fireBtnRect.exactCenterY();
        c.drawCircle(cx, cy, fireBtnRadiusPx, p);

        // viền + icon đường thẳng (viên đạn)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setColor(Color.BLACK);
        c.drawCircle(cx, cy, fireBtnRadiusPx, p);
        c.drawLine(cx - fireBtnRadiusPx * 0.4f, cy,
                cx + fireBtnRadiusPx * 0.4f, cy, p);
    }

    // ===== Pause button (UI) =====
    private void drawPauseButton(Canvas c) {
        if (pauseBtnRect == null) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // nền tối
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(180, 0, 0, 0));
        float cx = pauseBtnRect.exactCenterX();
        float cy = pauseBtnRect.exactCenterY();
        c.drawCircle(cx, cy, pauseBtnRadiusPx, p);

        // icon ||
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setColor(Color.WHITE);
        float barW = pauseBtnRadiusPx * 0.3f;
        float barH = pauseBtnRadiusPx * 0.9f;
        c.drawLine(cx - barW, cy - barH/2f, cx - barW, cy + barH/2f, p);
        c.drawLine(cx + barW, cy - barH/2f, cx + barW, cy + barH/2f, p);
    }

    private boolean isInFireButton(float x, float y) {
        return fireBtnRect != null && fireBtnRect.contains((int) x, (int) y);
    }
    private boolean isInPauseButton(float x, float y) {
        return pauseBtnRect != null && pauseBtnRect.contains((int) x, (int) y);
    }

    private void tryShootAtNearestEnemy() {
        long now = System.currentTimeMillis();
        if (now < nextShootAtMs) return;
        if (player == null || enemyMgr == null) return;

        Enemy target = null;
        float bestD2 = Float.MAX_VALUE;
        float px = player.centerX();
        float py = player.centerY();

        for (Enemy en : enemyMgr.list()) {
            try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
            float ex = en.x + en.w / 2f;
            float ey = en.y + en.h / 2f;
            float dx = ex - px, dy = ey - py;
            float d2 = dx*dx + dy*dy;
            if (d2 < bestD2) { bestD2 = d2; target = en; }
        }

        if (target != null) {
            Bullet b = player.spawnBulletToward(
                    target.x + target.w / 2f,
                    target.y + target.h / 2f,
                    mapWidth, mapHeight,
                    getContext() // context cho sprite cầu lửa
            );
            bullets.add(b);
            nextShootAtMs = now + SHOOT_COOLDOWN_MS;
        }
    }
}
