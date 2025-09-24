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
import android.media.AudioAttributes;
import android.media.SoundPool;
import com.example.game2dfighting.game.entity.Heart;

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

    // Map & camera ...
    private int mapWidth, mapHeight;
    private int cameraX = 0, cameraY = 0;

    // Entities
    private Player player;
    private EnemyManager enemyMgr;

    // Input
    private boolean movingUp, movingDown, movingLeft, movingRight;

    // Points
    private final ArrayList<Point> points = new ArrayList<>();

    // Paint & RNG
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
    private int skyW, skyH;
    private int islandX, islandY;

    // Clouds ...
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

    private volatile boolean gameOver = false;

    // ================== BULLETS ==================
    private final List<Bullet> bullets = new ArrayList<>();
    private long nextShootAtMs = 0L;
    private static final long SHOOT_COOLDOWN_MS = 250L;

    // UI buttons: fire & pause
    private Rect fireBtnRect;
    private float fireBtnRadiusPx;
    private Rect pauseBtnRect;
    private float pauseBtnRadiusPx;

    // ====== NEW: Audio toggles (UI) ======
    private Rect musicBtnRect, soundBtnRect;
    private Bitmap bmMusicOn, bmMusicOff, bmSoundOn, bmSoundOff;
    private boolean musicEnabled = true;
    private boolean soundEnabled = true;

    // Giao tiếp với Activity để điều khiển MediaPlayer nhạc nền
    public interface AudioControl { void onMusicToggle(boolean enabled); }
    private AudioControl audioControl;
    public void setAudioControl(AudioControl c) { this.audioControl = c; }

    // ===== Audio (SFX) =====
    private SoundPool soundPool;
    private int sfxFireId = 0;
    private boolean sfxLoaded = false;
    private float sfxVolume = 1.0f; // 0..1
    private int sfxPlayerHurtId = 0;
    private int sfxWallId = 0;
    private int sfxRunStepId = 0;
    private long nextRunStepAtMs = 0L;
    private int  runStepIntervalMs = 220;
    private int  lastPX = Integer.MIN_VALUE, lastPY = Integer.MIN_VALUE;

    private boolean atLeftEdge = false, atRightEdge = false, atTopEdge = false, atBottomEdge = false;

    // ===== Debug bounds (outline) =====
    private boolean showDebugBounds = true; // bật/tắt vẽ viền
    private final Paint debugPaintPlayer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint debugPaintEnemy  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ================== HEARTS (HP pickup) ==================
    private Bitmap bmpHeart;
    private final List<Heart> hearts = new ArrayList<>();
    private long lastHeartSpawnAtMs = 0L;
    private static final long HEART_SPAWN_INTERVAL_MS = 5000L; // 5s xuất hiện 1 trái tim
    private static final int HEART_HEAL_HP = 10;
    private static final int HEART_MAX_ON_MAP = 3; // tránh spam (tuỳ chỉnh)

    // SFX khi nhặt tim (tuỳ chọn)
    private int sfxPickupId = 0;


    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        // Debug paints
        debugPaintPlayer.setStyle(Paint.Style.STROKE);
        debugPaintPlayer.setColor(Color.MAGENTA); // nhân vật: hồng
        debugPaintPlayer.setStrokeWidth(dp(2));

        debugPaintEnemy.setStyle(Paint.Style.STROKE);
        debugPaintEnemy.setColor(Color.GREEN);    // quái: xanh lá
        debugPaintEnemy.setStrokeWidth(dp(2));


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

    // ===== Viền map (pixel tính theo bitmap island) =====
    private static final int MAP_BORDER_PX = 300;

    // Vùng chơi hợp lệ bên trong island (0..mapWidth/Height)
    private Rect getPlayableRect() {
        return new Rect(
                MAP_BORDER_PX,
                MAP_BORDER_PX,
                Math.max(MAP_BORDER_PX, mapWidth  - MAP_BORDER_PX),
                Math.max(MAP_BORDER_PX, mapHeight - MAP_BORDER_PX)
        );
    }

    private void initGame() { /* ... */ }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void initAudioToggleBitmaps() {
        if (bmMusicOn != null) return;
        Bitmap mOn  = BitmapFactory.decodeResource(getResources(), R.drawable.ic_music_on);
        Bitmap mOff = BitmapFactory.decodeResource(getResources(), R.drawable.ic_music_off);
        Bitmap sOn  = BitmapFactory.decodeResource(getResources(), R.drawable.ic_sound_on);
        Bitmap sOff = BitmapFactory.decodeResource(getResources(), R.drawable.ic_sound_off);

        int size = Math.round(dp(56)); // 56dp
        bmMusicOn  = Bitmap.createScaledBitmap(mOn,  size, size, true);
        bmMusicOff = Bitmap.createScaledBitmap(mOff, size, size, true);
        bmSoundOn  = Bitmap.createScaledBitmap(sOn,  size, size, true);
        bmSoundOff = Bitmap.createScaledBitmap(sOff, size, size, true);
    }

    // --- TIMER UTILS ---
    public void resetTimer() {
        timerStartMs = System.currentTimeMillis();
        pausedAccumulatedMs = 0L;
        timerPaused = false;
    }
    public void pauseTimer() { if (!timerPaused) { timerPaused = true; pauseStartMs = System.currentTimeMillis(); } }
    public void resumeTimer() { if (timerPaused) { pausedAccumulatedMs += System.currentTimeMillis() - pauseStartMs; timerPaused = false; } }
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
            movingUp = movingDown = movingLeft = movingRight = false;
            player.up = player.down = player.left = player.right = false;
            if (!timerPaused) { timerPaused = true; pauseStartMs = System.currentTimeMillis(); }
        } else {
            if (timerPaused) { pausedAccumulatedMs += System.currentTimeMillis() - pauseStartMs; timerPaused = false; }
        }
    }
    public boolean isPaused() { return paused; }

    // ====== NEW: expose music/sound state to Activity ======
    public boolean isMusicEnabled() { return musicEnabled; }
    public boolean isSoundEnabled() { return soundEnabled; }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        this.holder = holder;

        initSound();  // load SFX
        initAudioToggleBitmaps();

        // ISLAND
        Bitmap srcIsland = BitmapFactory.decodeResource(getResources(), R.drawable.bg_map_level1_island);
        bmpIsland = srcIsland;
        mapWidth  = bmpIsland.getWidth();
        mapHeight = bmpIsland.getHeight();

        // Player
        int playerW = 400, playerH = 400;
        int startX = mapWidth / 2 - playerW / 2;
        int startY = mapHeight / 2 - playerH / 2;
        player = new Player(getContext(), startX, startY, playerW, playerH);
        player.setMaxMana(0);

        // Enemies
        enemyMgr = new EnemyManager(getContext(), mapWidth, mapHeight);
        enemyMgr.setCombatListener(new EnemyManager.CombatListener() {
            @Override public void onPlayerHit() { playPlayerHurtSfx(); }
        });

        // SKY
        Bitmap srcSky = BitmapFactory.decodeResource(getResources(), R.drawable.bg_map_level1_sky);
        int viewW = getWidth(), viewH = getHeight();
        int skyMargin = Math.max(viewW, viewH);
        skyW = Math.max(mapWidth  + skyMargin*2, (int)(viewW * 1.5f));
        skyH = Math.max(mapHeight + skyMargin*2, (int)(viewH * 1.5f));
        bmpSky = Bitmap.createScaledBitmap(srcSky, skyW, skyH, false);

        islandX = (skyW - mapWidth) / 2;
        islandY = (skyH - mapHeight) / 2;

        // HEART BITMAP
        Bitmap heartSrc = BitmapFactory.decodeResource(getResources(), R.drawable.heart);
// (tuỳ) scale nhỏ lại nếu ảnh gốc to quá:
        int heartW = Math.round(dp(20));
        int heartH = Math.round(dp(20));
        bmpHeart = Bitmap.createScaledBitmap(heartSrc, heartW, heartH, true);

// Để xuất hiện trái tim ngay từ đầu sau 5s
        lastHeartSpawnAtMs = System.currentTimeMillis();


        // CLOUDS ...
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
            float depth = r.nextFloat();
            float parallax = 0.10f + depth * 0.28f;
            float scale    = 0.50f + depth * 1.10f;
            float alpha    = 0.30f + depth * 0.70f;
            float v        = 1.2f + depth * 4.2f + r.nextFloat() * 0.8f;
            clouds.add(new Cloud(x, y, v, scale, alpha, parallax, baseW, baseH));
        }

        // Points
        if (points.isEmpty()) {
            Rect pr = getPlayableRect();
            int count = 200;
            for (int i = 0; i < count; i++) {
                int x = pr.left + random.nextInt(Math.max(1, pr.width()  - 40)) + 20;
                int y = pr.top  + random.nextInt(Math.max(1, pr.height() - 40)) + 20;
                points.add(new Point(x, y));
            }
        }

        // CAMERA
        cameraX = (int)((player.centerX() + islandX) - getWidth()/2f);
        cameraY = (int)((player.centerY() + islandY) - getHeight()/2f);
        clampCamera();

        // Buttons
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
        try {
            if (soundPool != null) {
                soundPool.release();
                soundPool = null;
                sfxLoaded = false;
            }
        } catch (Throwable ignore) {}
        try { if (gameThread != null) gameThread.join(); } catch (InterruptedException e) { Log.e(TAG, "stop", e); }
    }

    @Override
    public void run() {
        long lastUpdateMs = System.currentTimeMillis();

        while (isRunning) {
            if (holder == null || !holder.getSurface().isValid()) { sleep(16); continue; }

            long now  = System.currentTimeMillis();
            long dtMs = now - lastUpdateMs;
            lastUpdateMs = now;
            if (dtMs < 0)   dtMs = 0;
            if (dtMs > 100) dtMs = 100;

            if (!paused && !gameOver) {
                // UPDATE CLOUDS ...
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

                player.update(dtMs);
                clampPlayerToMap();

                // RUN STEP SFX
                boolean movedPixel = (player.x != lastPX) || (player.y != lastPY);
                long nowMs = System.currentTimeMillis();
                if (movedPixel) {
                    if (nowMs >= nextRunStepAtMs) {
                        playRunStepSfx();
                        nextRunStepAtMs = nowMs + runStepIntervalMs;
                    }
                } else {
                    nextRunStepAtMs = 0L;
                }
                lastPX = player.x; lastPY = player.y;

                // camera follow
                cameraX = (int)((player.centerX() + islandX) - getWidth()/2f);
                cameraY = (int)((player.centerY() + islandY) - getHeight()/2f);
                clampCamera();

                // enemies
                enemyMgr.maybeSpawn();
                enemyMgr.updateTowardsPlayer(player, dtMs);

                // NEW: đảm bảo enemy không lọt ra viền map
                clampEnemiesToPlayable();

                // bullets
                float dtSec = dtMs / 1000f;
                for (int i = bullets.size() - 1; i >= 0; i--) {
                    Bullet b = bullets.get(i);
                    b.update(dtSec);
                    if (!b.alive) { bullets.remove(i); continue; }

                    boolean hit = false;
                    for (Enemy en : enemyMgr.list()) {
                        try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
                        if (b.hit(en)) {
                            hit = true;
                            enemyMgr.applyBulletHit(en, b.damage);
                            break;
                        }
                    }
                    if (hit) { b.alive = false; bullets.remove(i); }
                }

                // === HEARTS ===
                maybeSpawnHeart();
                updateHeartsAndPickup();

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

            long frameTime = System.currentTimeMillis() - now;
            long sleep = 16 - frameTime;
            if (sleep > 0) sleep(sleep);

            if (gameOver) isRunning = false;
        }
    }

    private void initSound() {
        try {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setAudioAttributes(aa)
                    .setMaxStreams(4)
                    .build();
            sfxFireId = soundPool.load(getContext(), R.raw.fireball_shoot, 1);
            sfxWallId = soundPool.load(getContext(), R.raw.wall_bump, 1);
            sfxPlayerHurtId = soundPool.load(getContext(), R.raw.player_hurt, 1);
            sfxRunStepId = soundPool.load(getContext(), R.raw.run_step1, 1);
            sfxPickupId = soundPool.load(getContext(), R.raw.pickup, 1);
            soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
                if (status == 0 && sampleId == sfxFireId) sfxLoaded = true;
            });
        } catch (Throwable t) {
            Log.e(TAG, "initSound error", t);
            soundPool = null;
            sfxLoaded = false;
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

    // ===== TOUCH: Pause + Fire + NEW Music/Sound =====
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        final int index = event.getActionIndex();
        final float tx = event.getX(index);
        final float ty = event.getY(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // Music/Sound buttons (ưu tiên bắt trước)
                if (musicBtnRect != null && musicBtnRect.contains((int)tx, (int)ty)) {
                    musicEnabled = !musicEnabled;
                    if (audioControl != null) audioControl.onMusicToggle(musicEnabled);
                    invalidate();
                    return true;
                }
                if (soundBtnRect != null && soundBtnRect.contains((int)tx, (int)ty)) {
                    soundEnabled = !soundEnabled;
                    invalidate();
                    return true;
                }

                // Pause (trên-phải)
                if (isInPauseButton(tx, ty)) {
                    setPaused(!isPaused());
                    // Khi resume từ pause: chỉ phát lại nhạc nếu musicEnabled → Activity đã xử lý
                    return true;
                }
                // Fire (dưới-phải)
                if (isInFireButton(tx, ty)) {
                    tryShootAtNearestEnemy();
                    return true;
                }
                break;
            }
        }
        return true;
    }

    private void clampPlayerToMap() {
        Rect pr = getPlayableRect();

        boolean hitNowLeft   = (player.x <= pr.left);
        boolean hitNowTop    = (player.y <= pr.top);
        boolean hitNowRight  = (player.x + player.w >= pr.right);
        boolean hitNowBottom = (player.y + player.h >= pr.bottom);

        if (player.x < pr.left) player.x = pr.left;
        if (player.y < pr.top)  player.y = pr.top;
        if (player.x + player.w > pr.right)  player.x = pr.right  - player.w;
        if (player.y + player.h > pr.bottom) player.y = pr.bottom - player.h;

        if (hitNowLeft   && !atLeftEdge)   playWallSfx();
        if (hitNowTop    && !atTopEdge)    playWallSfx();
        if (hitNowRight  && !atRightEdge)  playWallSfx();
        if (hitNowBottom && !atBottomEdge) playWallSfx();

        atLeftEdge   = hitNowLeft;
        atTopEdge    = hitNowTop;
        atRightEdge  = hitNowRight;
        atBottomEdge = hitNowBottom;
    }

    // === NEW: clamp toàn bộ enemy vào playable rect (gọi mỗi frame) ===
    private void clampEnemiesToPlayable() {
        if (enemyMgr == null) return;
        Rect pr = getPlayableRect();
        for (Enemy en : enemyMgr.list()) {
            try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
            if (en.x < pr.left) en.x = pr.left;
            if (en.y < pr.top)  en.y = pr.top;
            if (en.x + en.w > pr.right)  en.x = pr.right  - en.w;
            if (en.y + en.h > pr.bottom) en.y = pr.bottom - en.h;
        }
    }



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

                // NEW: respawn trong playable rect (thụt 100px)
                Rect pr = getPlayableRect();
                int x = pr.left + random.nextInt(Math.max(1, pr.width()  - 40)) + 20;
                int y = pr.top  + random.nextInt(Math.max(1, pr.height() - 40)) + 20;
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

        // 4) ENTITIES
        player.draw(canvas, cameraX - islandX, cameraY - islandY, paint);
        enemyMgr.draw(canvas, cameraX - islandX, cameraY - islandY);

        // 5) BULLETS
        for (Bullet b : bullets) {
            b.draw(canvas, cameraX - islandX, cameraY - islandY);
        }

        drawEntityBounds(canvas);

        // 5.5) HEARTS
        drawHearts(canvas);


        // 6) POINTS
        drawPointsOnIsland(canvas);

        // 7) HUD + Buttons
        drawHud(canvas);
        drawPauseButton(canvas);
        drawFireButton(canvas);

        // Overlay pause
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
        }

        // === NEW: luôn vẽ 2 nút Audio SAU CÙNG để không bị phủ che ===
        drawAudioToggles(canvas);
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

        // Fire bottom-right
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

        // ===== NEW: Music/Sound bottom-center (HORIZONTAL) =====
        int audioSize = (int) dp(56);
        int spacing   = (int) dp(12);

        int totalW = audioSize * 2 + spacing;   // tổng bề rộng 2 nút + khoảng cách
        int leftX  = (w - totalW) / 2;          // canh giữa theo ngang
        int topY   = h - margin - audioSize;    // cùng một hàng sát đáy, dùng margin đã có ở trên

// Music bên trái
        musicBtnRect = new Rect(
                leftX,
                topY,
                leftX + audioSize,
                topY + audioSize
        );

// Sound bên phải
        soundBtnRect = new Rect(
                leftX + audioSize + spacing,
                topY,
                leftX + audioSize + spacing + audioSize,
                topY + audioSize
        );
    }

    // ===== Fire button (UI) =====
    private void drawFireButton(Canvas c) {
        if (fireBtnRect == null) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(160, 255, 255, 255));
        float cx = fireBtnRect.exactCenterX();
        float cy = fireBtnRect.exactCenterY();
        c.drawCircle(cx, cy, fireBtnRadiusPx, p);

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

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(180, 0, 0, 0));
        float cx = pauseBtnRect.exactCenterX();
        float cy = pauseBtnRect.exactCenterY();
        c.drawCircle(cx, cy, pauseBtnRadiusPx, p);

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

    // ===== NEW: vẽ 2 nút Audio =====
    private void drawAudioToggles(Canvas c) {
        if (bmMusicOn == null) initAudioToggleBitmaps();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Music
        Bitmap mbmp = musicEnabled ? bmMusicOn : bmMusicOff;
        if (musicBtnRect != null && mbmp != null) {
            c.drawBitmap(mbmp, musicBtnRect.left, musicBtnRect.top, p);
        }

        // Sound
        Bitmap sbmp = soundEnabled ? bmSoundOn : bmSoundOff;
        if (soundBtnRect != null && sbmp != null) {
            c.drawBitmap(sbmp, soundBtnRect.left, soundBtnRect.top, p);
        }
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
                    getContext()
            );
            bullets.add(b);
            nextShootAtMs = now + SHOOT_COOLDOWN_MS;
            playFireSfx();
        }
    }

    // ===== SFX (đã tôn trọng soundEnabled) =====
    private void playFireSfx() {
        if (paused) return;
        if (!soundEnabled) return;
        if (soundPool == null) return;
        if (!sfxLoaded) return;
        soundPool.play(sfxFireId, sfxVolume, sfxVolume, 1, 0, 1.0f);
    }

    private void playWallSfx() {
        if (paused) return;
        if (!soundEnabled) return;
        if (soundPool == null || !sfxLoaded) return;
        float vol = 0.9f;
        soundPool.play(sfxWallId, vol, vol, 1, 0, 1.0f);
    }

    private void playPlayerHurtSfx() {
        if (paused) return;
        if (!soundEnabled) return;
        if (soundPool == null || !sfxLoaded) return;
        soundPool.play(sfxPlayerHurtId, sfxVolume, sfxVolume, 1, 0, 1.0f);
    }

    private void playRunStepSfx() {
        if (paused) return;
        if (!soundEnabled) return;
        if (soundPool == null || !sfxLoaded) return;
        float rate = 0.92f + (float)Math.random() * 0.16f;
        soundPool.play(sfxRunStepId, sfxVolume, sfxVolume, 1, 0, rate);
    }

    private void drawEntityBounds(Canvas c) {
        if (!showDebugBounds || player == null || enemyMgr == null) return;

        // offset màn hình (giống cách bạn vẽ entity: player.draw(canvas, cameraX - islandX, cameraY - islandY))
        float offX = cameraX - islandX;
        float offY = cameraY - islandY;

        // ---- Player ----
        float pl = player.x - offX;
        float pt = player.y - offY;
        float pr = pl + player.w;
        float pb = pt + player.h;
        c.drawRect(pl, pt, pr, pb, debugPaintPlayer);

        // chấm tâm (tuỳ chọn)
        // c.drawCircle(pl + player.w/2f, pt + player.h/2f, dp(2), debugPaintPlayer);

        // ---- Enemies ----
        for (Enemy en : enemyMgr.list()) {
            try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
            float el = en.x - offX;
            float et = en.y - offY;
            float er = el + en.w;
            float eb = et + en.h;
            c.drawRect(el, et, er, eb, debugPaintEnemy);
            // c.drawCircle(el + en.w/2f, et + en.h/2f, dp(2), debugPaintEnemy); // chấm tâm (tuỳ chọn)
        }
    }

    private void maybeSpawnHeart() {
        long now = System.currentTimeMillis();
        if (now - lastHeartSpawnAtMs < HEART_SPAWN_INTERVAL_MS) return;
        if (bmpHeart == null) return;
        if (hearts.size() >= HEART_MAX_ON_MAP) { // giới hạn số lượng đang tồn tại
            lastHeartSpawnAtMs = now;
            return;
        }

        Rect pr = getPlayableRect();
        int margin = 40;
        int maxX = Math.max(1, pr.width()  - bmpHeart.getWidth()  - margin * 2);
        int maxY = Math.max(1, pr.height() - bmpHeart.getHeight() - margin * 2);
        if (maxX <= 0 || maxY <= 0) return;

        int hx = pr.left + random.nextInt(maxX) + margin;
        int hy = pr.top  + random.nextInt(maxY) + margin;

        hearts.add(new Heart(hx, hy, bmpHeart));
        lastHeartSpawnAtMs = now;
    }

    private void updateHeartsAndPickup() {
        if (player == null) return;
        Rect pRect = new Rect(player.x, player.y, player.x + player.w, player.y + player.h);

        for (int i = hearts.size() - 1; i >= 0; i--) {
            Heart h = hearts.get(i);
            if (h.isConsumed()) { hearts.remove(i); continue; }
            if (Rect.intersects(pRect, h.getHitbox())) {
                player.setHp(Math.min(player.getMaxHp(), player.getHp() + HEART_HEAL_HP));
                h.consume();
                hearts.remove(i);
                playPickupSfx();
            }
        }
    }

    private void playPickupSfx() {
        if (paused) return;
        if (!soundEnabled) return;
        if (soundPool == null || !sfxLoaded) return;
        if (sfxPickupId == 0) return;
        soundPool.play(sfxPickupId, sfxVolume, sfxVolume, 1, 0, 1.0f);
    }

    private void drawHearts(Canvas canvas) {
        if (hearts.isEmpty()) return;
        float offX = cameraX - islandX;
        float offY = cameraY - islandY;
        for (Heart h : hearts) {
            h.draw(canvas, offX, offY);
        }
    }



}
