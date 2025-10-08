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
import android.content.Intent;
import com.example.game2dfighting.R;
import com.example.game2dfighting.game.core.GameObject;
import com.example.game2dfighting.game.entity.Heart;
import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.game.entity.Enemy;
import com.example.game2dfighting.game.entity.Boss;
import com.example.game2dfighting.game.manager.BossManager;
import com.example.game2dfighting.game.manager.EnemyManager;
import com.example.game2dfighting.game.manager.PlayerManager;
import com.example.game2dfighting.game.manager.ScoreManager;
import com.example.game2dfighting.game.skill.Fireball;
import com.example.game2dfighting.game.skill.IceSpike;
import com.example.game2dfighting.ui.PlayerHudRenderer;
import com.example.game2dfighting.game.entity.ShieldHeart;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.example.game2dfighting.game.entity.BossAngel;
import com.example.game2dfighting.game.manager.BossAngelManager;


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
    private BossManager bossMgr;
    private BossAngel bossAngel;  // ✨ Boss thiên thần riêng cho Level 3
    private BossAngelManager bossAngelMgr;

    // ====== LEVEL 3 PHASE CONTROL ======
    private enum Level3Phase {
        START,          // BossAngel chỉ bay
        ENEMY_FIGHT,    // Đánh quái thường để tích điểm
        ROCK_BOSSES,    // 5 Boss người đá xuất hiện
        ANGEL_ATTACK,   // BossAngel bắt đầu tấn công
        FINISHED        // BossAngel chết => thắng
    }
    private Level3Phase lv3Phase = Level3Phase.START;
    private boolean enemiesStopped = false;
    private int rockBossSpawned = 0;
    private int rockBossAlive = 0;



    // NEW: HUD & PlayerManager
    private PlayerHudRenderer playerHud;
    private PlayerManager playerMgr;

    // Input
    private boolean movingUp, movingDown, movingLeft, movingRight;

    // Points
    private final ArrayList<Point> points = new ArrayList<>();

    // Paint & RNG
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    // Pause
    private volatile boolean paused = false;

    // Trong GameView, thêm public method isGameOver() (sau fields)
    public boolean isGameOver() {
        return gameOver;
    }

    // Game over callback
    public interface GameEventListener { void onGameOver(); }
    private GameEventListener listener;
    public void setGameEventListener(GameEventListener l) { this.listener = l; }

    // ✅ WIN callback (NEW)
    public interface OnWinListener { void onWin(); }
    private OnWinListener onWinListener;
    public void setOnWinListener(OnWinListener l) { this.onWinListener = l; }

    // ===== Parallax background =====
    private Bitmap bmpSky, bmpIsland, bmpCloud;
    private int skyW, skyH;
    private int islandX, islandY;

    // Hiệu ứng nháy màn hình khi chạm boss

    // Thêm fields vào GameView (sau các fields khác)
    private long playerHurtUntilMs = 0L;
    private static final long HURT_LOCK_MS = 300L;  // Lock 300ms sau va chạm để tránh spam attack

    private boolean screenFlash = false;
    private long flashStartMs = 0L;
    private static final long FLASH_DURATION_MS = 150L;  // Thời gian nháy
    private final Paint flashPaint = new Paint();  // Paint overlay đỏ nhạt

    // === Death sequence overlay ===
    private boolean deathSequence = false;      // đang chạy hiệu ứng chết
    private long deathStartMs = 0L;             // mốc thời gian bắt đầu hiệu ứng
    private boolean deathTransitioned = false;  // đã chuyển sang GameOverActivity chưa
    private final Paint dimPaint = new Paint(); // sơn phủ mờ
    private Bitmap bmpGameOver;                 // ảnh "gameover"

    // === Level name để lưu điểm đúng cột High Score ===
    private String levelName = "Level1"; // mặc định
    public void setLevelName(String name) {
        this.levelName = (name == null || name.isEmpty()) ? "Level1" : name;
    }

    // Khoảng cách đẩy lùi
    private static final float PUSH_BACK_DISTANCE = 100f;  // Pixel đẩy lùi

    // ================== SHIELD HEARTS (pickup bật giáp) ==================
    private Bitmap bmpShieldHeart;
    private final List<ShieldHeart> shieldHearts = new ArrayList<>();
    private long lastShieldHeartSpawnAtMs = 0L;
    private static final long SHIELD_HEART_SPAWN_INTERVAL_MS = 7000L; // mỗi 7s
    private static final int  SHIELD_HEART_MAX_ON_MAP         = 2;    // tối đa 2 cái
    private static final int  SHIELD_HEART_AMOUNT             = 50;   // +50 giáp
    private static final int  SHIELD_HEART_DURATION_MS        = 5000; // 5 giây

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
    private android.graphics.RectF timePillRect = null;

    private volatile boolean gameOver = false;

    // ================== PROJECTILES (Fireball) ==================
    private final List<Fireball> fireballs = new ArrayList<>();

    // ================== PROJECTILES (IceSpike) ==================
    private final List<IceSpike> icespikes = new ArrayList<>();
    private Bitmap bmpIceSpike; // sprite đạn băng

    // UI buttons: fire & pause
    private Rect fireBtnRect;
    private float fireBtnRadiusPx;
    private Rect iceBtnRect;          // NEW
    private float iceBtnRadiusPx;     // NEW
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

    private int sfxIceId = 0;
    private int sfxShieldId = 0;
    private boolean atLeftEdge = false, atRightEdge = false, atTopEdge = false, atBottomEdge = false;

    // ===== Debug bounds (outline) =====
    private boolean showDebugBounds = false; // bật/tắt vẽ viền
    private final Paint debugPaintPlayer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint debugPaintEnemy  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ================== HEARTS (HP pickup) ==================
    private Bitmap bmpHeart;
    private final List<Heart> hearts = new ArrayList<>();
    private long lastHeartSpawnAtMs = 0L;
    private static final long HEART_SPAWN_INTERVAL_MS = 5000L; // 5s
    private static final int HEART_HEAL_HP = 10;
    private static final int HEART_MAX_ON_MAP = 3;

    // ===== Difficulty (HP & Damage only) =====
    private float enemyHpMul = 1f, enemyDmgMul = 1f;
    private float bossHpMul  = 1f, bossDmgMul  = 1f;

    /** Gọi từ LevelXActivity: L1=1, L2=2, L3=3 */
    public void setStatMultipliersForLevel(int levelIndex) {
        switch (levelIndex) {
            case 2:
                enemyHpMul = 2f; enemyDmgMul = 2f;
                bossHpMul  = 2f; bossDmgMul  = 2f;
                break;
            case 3:
                enemyHpMul = 3f; enemyDmgMul = 3f;
                bossHpMul  = 3f; bossDmgMul  = 3f;
                break;
            default:
                enemyHpMul = 1f; enemyDmgMul = 1f;
                bossHpMul  = 1f; bossDmgMul  = 1f;
        }
    }

    private int score = 0;
    private long gameStartMs = System.currentTimeMillis();

    // Score
    public void resetScore() { score = 0; }
    public void addScore(int delta) { score = Math.max(0, score + delta); }
    public int getScore() { return score; }

    // === Boss defeated overlay ===
    private boolean bossDefeated = false;
    private long bossDefeatAtMs = 0L;
    private boolean bossTransitioned = false;
    private float bossFadeAlpha = 0f; // 0..1
    private static final long BOSS_CONGRATS_DURATION_MS = 2000L;
    private Bitmap bmpCongrats; // R.drawable.congratulations

    //ADD level nhân vật
    private Integer startingPlayerLevel = null;

    public void setStartingPlayerLevel(int lvl) {
        startingPlayerLevel = Math.max(1, lvl);
        if (playerMgr != null) {
            playerMgr.setLevel(startingPlayerLevel);
        }
    }

    public int getCurrentPlayerLevel() {
        if (playerMgr != null) return playerMgr.getLevel();
        return 1;
    }

    // Cho phép Activity cấu hình respawn của BossManager
    public void setBossRespawnDelayMs(long ms) {
        if (bossMgr != null) {
            bossMgr.setRespawnDelayMs(ms);
        } else {
            // bossMgr chưa có (chưa surfaceCreated) -> lưu lại để apply sau
            pendingBossRespawnDelayMs = ms;
        }
    }


    // Lưu cấu hình respawn nếu Activity gọi trước khi bossMgr được tạo
    private long pendingBossRespawnDelayMs = -1L;


    // Bật/tắt việc thắng ngay khi giết boss (Level1: true, Level2/3: false)
    private boolean bossKillGrantsWin = true;
    public void setBossKillGrantsWin(boolean enabled) { this.bossKillGrantsWin = enabled; }


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
        debugPaintPlayer.setColor(Color.MAGENTA);
        debugPaintPlayer.setStrokeWidth(dp(2));

        debugPaintEnemy.setStyle(Paint.Style.STROKE);
        debugPaintEnemy.setColor(Color.GREEN);
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
        // Setup flash paint
        flashPaint.setColor(Color.argb(100, 255, 100, 100));  // Đỏ nhạt, alpha 100
        flashPaint.setStyle(Paint.Style.FILL);  // Thêm để fill rect
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

    private void initGame() { /* optional if you keep in surfaceCreated */ }

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

    private long getElapsedMs() {
        long now = System.currentTimeMillis();
        long elapsedMs = timerPaused
                ? (pauseStartMs - timerStartMs - pausedAccumulatedMs)
                : (now - timerStartMs - pausedAccumulatedMs);
        if (elapsedMs < 0) elapsedMs = 0;
        return elapsedMs;
    }

    // Tính elapsed thật sự kể cả đang pause hay không
    private long getElapsedMsAccurate() {
        long now = System.currentTimeMillis();
        long extraPaused = timerPaused ? (now - pauseStartMs) : 0L;
        long elapsed = now - timerStartMs - (pausedAccumulatedMs + extraPaused);
        return Math.max(0L, elapsed);
    }


    // ===== Pause API =====
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            movingUp = movingDown = movingLeft = movingRight = false;
            if (player != null) {
                player.up = player.down = player.left = player.right = false;
            }
            if (!timerPaused) { timerPaused = true; pauseStartMs = System.currentTimeMillis(); }
        } else {
            if (timerPaused) { pausedAccumulatedMs += System.currentTimeMillis() - pauseStartMs; timerPaused = false; }
        }
    }
    public boolean isPaused() { return paused; }

    // ====== NEW: expose music/sound state to Activity ======
    public boolean isMusicEnabled() { return musicEnabled; }
    public boolean isSoundEnabled() { return soundEnabled; }

    // Default: dùng island của level 1 (đổi tên này theo resource bạn đang có)
    private int islandResId = R.drawable.bg_map_island;
    public void setIslandResId(int resId) { this.islandResId = resId; }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        this.holder = holder;

        initSound();  // load SFX
        initAudioToggleBitmaps();

        // ISLAND
        Bitmap srcIsland = BitmapFactory.decodeResource(getResources(), islandResId);
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
        enemyMgr.setStatMultipliers(enemyHpMul, enemyDmgMul);

// Boss thường
        bossMgr = new BossManager(getContext(), mapWidth, mapHeight);
        bossMgr.setStatMultipliers(bossHpMul, bossDmgMul);

// Nếu là Level3 thì dùng BossAngelManager thay cho Boss thường
//        if ("Level3".equalsIgnoreCase(levelName)) {
//            bossAngelMgr = new BossAngelManager(getContext(), mapWidth, mapHeight);
//            bossAngelMgr.setStatMultipliers(bossHpMul, bossDmgMul);
//            bossAngelMgr.setKillListener(() -> addScore(200)); // +200 điểm
//
//            // ✨ Boss Angel sẽ xuất hiện sau 5 giây
//            new android.os.Handler().postDelayed(() -> {
//                bossAngelMgr.maybeSpawn();
//
//                // Sau khi spawn 10s → bắt đầu cho phép tấn công
//                new android.os.Handler().postDelayed(() -> {
//                    if (bossAngelMgr.getBoss() != null) {
//                        bossAngelMgr.getBoss().enableAttack(); // sẽ tạo hàm này
//                    }
//                }, 10_000);
//            }, 5_000);
//        }
        //===thay mới cơ chế
        if ("Level3".equalsIgnoreCase(levelName)) {
            bossKillGrantsWin = false;
            bossAngelMgr = new BossAngelManager(getContext(), mapWidth, mapHeight);
            bossAngelMgr.setStatMultipliers(bossHpMul, bossDmgMul);
            bossAngelMgr.setKillListener(() -> {
                addScore(500); // BossAngel bị diệt => thắng
                lv3Phase = Level3Phase.FINISHED;
                triggerWinByCondition();
            });

            // Spawn bossAngel sớm nhưng không tấn công
            new android.os.Handler().postDelayed(() -> {
                bossAngelMgr.maybeSpawn();
                lv3Phase = Level3Phase.ENEMY_FIGHT;
            }, 2000);
        }


        else {
            bossMgr = new BossManager(getContext(), mapWidth, mapHeight);
            bossMgr.setStatMultipliers(bossHpMul, bossDmgMul);
        }




        // Nếu Activity đã cấu hình trước -> apply lại
        if (pendingBossRespawnDelayMs >= 0L) {
            bossMgr.setRespawnDelayMs(pendingBossRespawnDelayMs);
            // (giữ nguyên giá trị để Level2/3 có thể gọi lại lần nữa nếu muốn)
        }

        // Tính điểm quái vs boss
        enemyMgr.setKillListener(() -> addScore(10));    // quái: +10
        bossMgr.setKillListener(() -> {
            addScore(100);

            // ❌ Nếu đang ở Level3 thì không được thắng khi boss người đá chết
            if ("Level3".equalsIgnoreCase(levelName)) {
                bossKillGrantsWin = false;
                return; // chỉ cộng điểm, không trigger thắng
            }

            // ✅ Còn lại các màn khác thì giữ logic cũ
            if (!bossKillGrantsWin) return;

            if (!bossDefeated && !deathSequence) {
                long elapsed = getElapsedMsAccurate();
                ScoreManager.saveRun(getContext(), getScore(), elapsed, System.currentTimeMillis(), levelName);

                timerPaused = true;
                bossDefeated = true;
                bossDefeatAtMs = System.currentTimeMillis();
                bossTransitioned = false;
                bossFadeAlpha = 0f;
            }
        });


        // Load ảnh chúc mừng
        bmpCongrats = BitmapFactory.decodeResource(getResources(), R.drawable.congratulations);

        // NEW: HUD + PlayerManager
        playerHud = new PlayerHudRenderer(getContext());
        playerMgr = new PlayerManager(getContext(), player, mapWidth, mapHeight);
        if (startingPlayerLevel != null) {
            playerMgr.setLevel(startingPlayerLevel);
        }
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
        int heartW = Math.round(dp(20));
        int heartH = Math.round(dp(20));
        bmpHeart = Bitmap.createScaledBitmap(heartSrc, heartW, heartH, true);
        lastHeartSpawnAtMs = System.currentTimeMillis();

        // SHIELD HEART BITMAP (dùng tạm heart nếu chưa có icon riêng)
        Bitmap shieldSrc = BitmapFactory.decodeResource(getResources(), R.drawable.shield_item);
        int shW = Math.round(dp(20));
        int shH = Math.round(dp(20));
        bmpShieldHeart = Bitmap.createScaledBitmap(shieldSrc, shW, shH, true);
        lastShieldHeartSpawnAtMs = System.currentTimeMillis();

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

        // Death overlay init
        dimPaint.setColor(Color.BLACK);
        dimPaint.setAlpha(0);

        // Ảnh "game over": res/drawable/gameover.png
        bmpGameOver = BitmapFactory.decodeResource(getResources(), R.drawable.gameover);

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
// Sửa phần input -> player update trong run() (thêm check lock hurt trước player.update)
                if (player != null) {
                    player.up = movingUp;
                    player.down = movingDown;
                    player.left = movingLeft;
                    player.right = movingRight;

                    // ===== NEW: Lock attack nếu đang hurt (tránh spam chém khi va chạm) =====
                    long hurtNow = System.currentTimeMillis();
                    if (hurtNow < playerHurtUntilMs) {
                        // Force input = false để không trigger RUN/ATTACK
                        player.up = player.down = player.left = player.right = false;
                        // Nếu có HURT anim, giữ state HURT; nếu không, set IDLE
                        try {
                            if (player.getState() != GameObject.State.HURT) {
                                player.getClass().getMethod("setState", GameObject.State.class).invoke(player, GameObject.State.HURT);
                            }
                        } catch (Throwable ignore) {
                            // Fallback: set IDLE để dừng attack
                            try {
                                player.getClass().getMethod("setState", GameObject.State.class).invoke(player, GameObject.State.IDLE);
                            } catch (Throwable ignore2) {}
                        }
                    }

                    player.update(dtMs);
                    clampPlayerToMap();
                }

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

                //Quản lý logic trong map3


                //===========logic map3 ===============

                // ====== LEVEL 3 PHASE MANAGEMENT ======
                if ("Level3".equalsIgnoreCase(levelName) && bossAngelMgr != null) {
                    switch (lv3Phase) {
                        case START:
                            // chờ BossAngel spawn
                            break;

                        case ENEMY_FIGHT:
                            // Khi đủ 100 điểm -> dừng spawn quái, xuất hiện 5 Boss người đá
                            if (getScore() >= 100 && !enemiesStopped) {
                                enemiesStopped = true;
                                try {
                                    java.lang.reflect.Method stop = enemyMgr.getClass().getMethod("stopSpawning");
                                    stop.invoke(enemyMgr);
                                } catch (Exception ignore) {}
                                rockBossSpawned = 5;
                                rockBossAlive = 5;
                                for (int i = 0; i < rockBossSpawned; i++) bossMgr.maybeSpawn();
                                lv3Phase = Level3Phase.ROCK_BOSSES;
                            }
                            break;

                        case ROCK_BOSSES:
                            // Đếm số boss người đá còn sống
                            if (!bossMgr.isActive()) {
                                // Khi 5 boss người đá chết -> kích hoạt BossAngel
                                if (bossAngelMgr.getBoss() != null) {
                                    bossAngelMgr.getBoss().enableAttack();
                                }
                                // Gọi lại 5 boss người đá tấn công cùng
                                for (int i = 0; i < 5; i++) bossMgr.maybeSpawn();
                                lv3Phase = Level3Phase.ANGEL_ATTACK;
                            }
                            break;

                        case ANGEL_ATTACK:
                            // Nếu BossAngel chết thì thắng
                            if (!bossAngelMgr.isActive() || lv3Phase == Level3Phase.FINISHED) {
                                lv3Phase = Level3Phase.FINISHED;
                                triggerWinByCondition();
                            }
                            break;

                        case FINISHED:
                            // Màn kết thúc
                            break;
                    }
                }





                //===logicmap3 =====================

                // boss
// Sửa collision code trong run() (thay thế phần collision cũ)
//                if (bossMgr != null) {
//                    bossMgr.maybeSpawn();
//                    bossMgr.update(player, dtMs);
//                }

                if (bossMgr != null) {
                    bossMgr.maybeSpawn();
                    bossMgr.update(player, dtMs);

                    // 🔒 Nếu đang ở Level3, không cho bossMgr kích hoạt Win
                    if ("Level3".equalsIgnoreCase(levelName)) {
                        bossDefeated = false; // reset để không bị trigger overlay Win
                    }
                }


                //boss Angel
                // BossAngel logic riêng cho Level3
                if (bossAngel != null) {
                    bossAngel.update(dtMs);
                }

                if (bossAngelMgr != null) {
                    bossAngelMgr.update(player, dtMs);
                }

// ===== NEW: Collision player-enemies/boss + push back + flash =====
// 1. Collision với Enemies thường (Enemy, Enemy2, Enemy3) - đẩy enemy lùi (không lock player)
                if (enemyMgr != null) {
                    for (Enemy en : enemyMgr.list()) {
                        try {
                            if (en.getState() == Enemy.State.DIE) continue;
                        } catch (Throwable ignore) {}
                        Rect playerRect = new Rect(player.x, player.y, player.x + player.w, player.y + player.h);
                        Rect enRect = new Rect(en.x, en.y, en.x + en.w, en.y + en.h);
                        if (Rect.intersects(playerRect, enRect)) {
                            // Đẩy enemy lùi (từ player ra enemy), không lock hurt cho player
                            pushBackEntity(player.centerX(), player.centerY(), en, PUSH_BACK_DISTANCE, false);
                            // Có thể thêm SFX hoặc hurt player nhẹ nếu cần
                        }
                    }
                }

// 2. Collision với Boss - đẩy player lùi + nháy màn hình + lock hurt
                if (bossMgr != null && bossMgr.isActive()) {
                    Boss boss = bossMgr.getBoss();
                    if (boss != null && boss.getState() != Boss.State.DIE) {
                        Rect playerRect = new Rect(player.x, player.y, player.x + player.w, player.y + player.h);
                        Rect bossRect = new Rect(boss.x, boss.y, boss.x + boss.w, boss.y + boss.h);
                        if (Rect.intersects(playerRect, bossRect)) {
                            // Đẩy player lùi (từ boss ra player) + lock hurt
                            pushBackEntity(boss.x + boss.w / 2f, boss.y + boss.h / 2f, player, PUSH_BACK_DISTANCE, true);
                            // Kích hoạt nháy màn hình
                            screenFlash = true;
                            flashStartMs = System.currentTimeMillis();
                            // Có thể thêm damage cho player hoặc SFX
                            playPlayerHurtSfx();  // Reuse SFX hurt
                        }
                    }
                }

                // đảm bảo enemy/boss không lọt ra viền map
                clampEnemiesToPlayable();

                // ===== NEW: PlayerManager regen/cooldown =====
                if (playerMgr != null) playerMgr.update(dtMs);

                // ===== Update & cleanup FIREBALLS =====
                float dtSec = dtMs / 1000f;
                for (int i = fireballs.size() - 1; i >= 0; i--) {
                    Fireball b = fireballs.get(i);
                    try { b.update(dtSec); } catch (Throwable ignore) {}
                    if (!b.alive) { fireballs.remove(i); continue; }

                    boolean hit = false;

                    // --- Enemy collision ---
                    for (Enemy en : enemyMgr.list()) {
                        try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
                        if (b.hit(en)) {
                            hit = true;
                            enemyMgr.applyBulletHit(en, b.damage);
                            break;
                        }
                    }

                    // --- Boss collision (nếu chưa trúng enemy) ---
                    if (!hit && bossMgr != null && bossMgr.isActive()) {
                        Boss boss = bossMgr.getBoss();
                        if (boss != null && boss.getState() != Boss.State.DIE) {
                            boolean bossHit = false;
                            try {
                                bossHit = (boolean) b.getClass().getMethod("hit", Boss.class).invoke(b, boss);
                            } catch (Throwable ignore) {
                                try {
                                    float bx = (float) b.getClass().getField("x").get(b);
                                    float by = (float) b.getClass().getField("y").get(b);
                                    int   bw = (int)   b.getClass().getField("w").get(b);
                                    int   bh = (int)   b.getClass().getField("h").get(b);
                                    Rect br = new Rect((int)bx, (int)by, (int)(bx + bw), (int)(by + bh));
                                    Rect bossR = new Rect(boss.x, boss.y, boss.x + boss.w, boss.y + boss.h);
                                    bossHit = Rect.intersects(br, bossR);
                                } catch (Throwable ignore2) {
                                    try {
                                        float bx = (float) b.getClass().getField("x").get(b);
                                        float by = (float) b.getClass().getField("y").get(b);
                                        float cx = boss.x + boss.w / 2f;
                                        float cy = boss.y + boss.h / 2f;
                                        float dx = bx - cx, dy = by - cy;
                                        float rad = Math.min(boss.w, boss.h) / 2f;
                                        bossHit = (dx*dx + dy*dy) <= (rad * rad);
                                    } catch (Throwable ignore3) { /* bỏ qua nếu không có dữ liệu */ }
                                }
                            }

                            if (bossHit) {
                                hit = true;
                                bossMgr.applyBulletHit(b.damage);
                            }
                        }
                    }

                    if (hit) { b.alive = false; fireballs.remove(i); }
                }

                // ===== Update & cleanup ICESPIKES =====
                for (int i = icespikes.size() - 1; i >= 0; i--) {
                    IceSpike spike = icespikes.get(i);
                    try { spike.update(dtSec); } catch (Throwable ignore) {}
                    if (!spike.alive) { icespikes.remove(i); continue; }

                    boolean hit = false;

                    // Enemy collision
                    for (Enemy en : enemyMgr.list()) {
                        try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
                        if (spike.hit(en)) {
                            hit = true;
                            enemyMgr.applyBulletHit(en, spike.damage);
                            break;
                        }
                    }

                    // Boss collision (nếu chưa trúng enemy)
                    if (!hit && bossMgr != null && bossMgr.isActive()) {
                        Boss boss = bossMgr.getBoss();
                        if (boss != null && boss.getState() != Boss.State.DIE) {
                            if (spike.hit(boss)) {
                                hit = true;
                                bossMgr.applyBulletHit(spike.damage);
                            }
                        }
                    }

                    if (hit) {
                        spike.alive = false;
                        icespikes.remove(i);
                    }
                }



                // === HEARTS ===
                maybeSpawnHeart();
                updateHeartsAndPickup();

                // === SHIELD HEARTS ===
                maybeSpawnShieldHeart();
                updateShieldHeartsAndPickup();

                if (player.getHp() <= 0) {
                    // KHÔNG gọi onGameOver ngay
                    timerPaused = true;   // dừng đếm thời gian
                    paused = true;        // dừng update gameplay (player/enemy/boss)
                    if (!deathSequence) {
                        deathSequence = true;
                        deathStartMs = System.currentTimeMillis();
                    }
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

    /**
     * Đẩy entity lùi theo hướng từ source ra entity (tránh chồng chéo).
     * @param sourceX, sourceY: Tâm source (boss/enemy)
     * @param entity: Entity bị đẩy (player hoặc enemy)
     * @param distance: Khoảng cách đẩy
     */
// Sửa method pushBackEntity (thêm param để hỗ trợ lock hurt nếu cần)
    private void pushBackEntity(float sourceX, float sourceY, GameObject entity, float distance, boolean lockHurt) {
        if (entity == null) return;
        float ex = entity.x + entity.w / 2f;
        float ey = entity.y + entity.h / 2f;
        float dx = ex - sourceX;
        float dy = ey - sourceY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 0 && dist < 100f) {  // Chỉ đẩy nếu quá gần (<100px)
            dx /= dist;
            dy /= dist;
            entity.x += dx * distance;
            entity.y += dy * distance;
            clampPlayerToMap();  // Clamp player nếu cần
            if (lockHurt && entity == player) {
                playerHurtUntilMs = System.currentTimeMillis() + HURT_LOCK_MS;
                // Force player state to HURT nếu có (giả sử Player có method setState)
                try {
                    entity.getClass().getMethod("setState", GameObject.State.class).invoke(entity, GameObject.State.HURT);
                } catch (Throwable ignore) {
                    // Fallback: không set state, chỉ lock time
                }
            }
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
            sfxIceId        = soundPool.load(getContext(), R.raw.icespike_shoot, 1);
            sfxShieldId     = soundPool.load(getContext(), R.raw.shield_cast, 1);

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
/* xóa pause đi không xung đột với layout
                // Pause (trên-phải)
                if (isInPauseButton(tx, ty)) {
                    setPaused(!isPaused());
                    return true;
                }
                */

                // Fire (dưới-phải)
                if (isInFireButton(tx, ty)) {
                    tryShootAtNearestEnemy();
                    return true;
                }
                // Ice
                if (playerHud != null && playerHud.isInIceButton(tx, ty)) {
                    tryShootIceAtNearestEnemy();
                    return true;
                }
                // Trong onTouchEvent, khi bấm nút Shield:
                if (playerHud != null && playerHud.isInShieldButton(tx, ty)) {
                    if (playerMgr != null) {
                        boolean willCast = playerMgr.getRemainingCooldownMs(PlayerManager.SkillType.SHIELD) <= 0;
                        playerMgr.tryUseShield(enemyMgr, bossMgr,bossAngelMgr);
                        if (willCast) {
                            playShieldSfx();
                        }
                    }
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

    // === clamp toàn bộ enemy + boss vào playable rect ===
    private void clampEnemiesToPlayable() {
        Rect pr = getPlayableRect();

        // Enemies
        if (enemyMgr != null) {
            for (Enemy en : enemyMgr.list()) {
                try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
                if (en.x < pr.left) en.x = pr.left;
                if (en.y < pr.top)  en.y = pr.top;
                if (en.x + en.w > pr.right)  en.x = pr.right  - en.w;
                if (en.y + en.h > pr.bottom) en.y = pr.bottom - en.h;
            }
        }

        // Boss
        if (bossMgr != null && bossMgr.isActive()) {
            Boss b = bossMgr.getBoss();
            if (b != null) {
                if (b.x < pr.left) b.x = pr.left;
                if (b.y < pr.top)  b.y = pr.top;
                if (b.x + b.w > pr.right)  b.x = pr.right  - b.w;
                if (b.y + b.h > pr.bottom) b.y = pr.bottom - b.h;
            }
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

                if (playerMgr != null) playerMgr.addExp(10);

                // respawn trong playable rect
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

        // ===== NEW: Vẽ flash overlay nếu đang nháy ===== //
        if (screenFlash) {
            long now = System.currentTimeMillis();
            if (now - flashStartMs > FLASH_DURATION_MS) {
                screenFlash = false;  // Tắt flash
            } else {
                // Fade alpha theo thời gian (nháy nhanh)
                float progress = (now - flashStartMs) / (float) FLASH_DURATION_MS;
                int alpha = (int) (100 * (1f - progress));  // Giảm alpha dần
                flashPaint.setAlpha(alpha);
                canvas.drawRect(0, 0, getWidth(), getHeight(), flashPaint);
            }
        }
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
        if (bossMgr != null) bossMgr.draw(canvas, cameraX - islandX, cameraY - islandY);

        //vẽ boss angel
        if (bossAngel != null) {
            bossAngel.draw(canvas, cameraX - islandX, cameraY - islandY, paint);
        }
        if (bossAngelMgr != null) {
            bossAngelMgr.draw(canvas, cameraX - islandX, cameraY - islandY);
        }


        // 5) FIREBALLS
        for (Fireball b : fireballs) {
            b.draw(canvas, cameraX - islandX, cameraY - islandY);
        }
        // 5b) ICESPIKES
        for (IceSpike s : icespikes) {
            s.draw(canvas, cameraX - islandX, cameraY - islandY);
        }
        // 5c) SHIELDs
        if (playerHud != null) {
            playerHud.drawShieldButton(canvas); // NEW: vẽ nút Shield
        }

        // Debug
        drawEntityBounds(canvas);

        // 5.5) HEARTS
        drawHearts(canvas);
        drawShieldHearts(canvas);

        // 6) POINTS
        drawPointsOnIsland(canvas);

        // === Boss defeated overlay (mờ + ảnh chúc mừng + auto transition) ===
        if (bossDefeated && !deathSequence) {
            // tăng alpha mờ
            bossFadeAlpha = Math.min(1f, bossFadeAlpha + 0.04f);
            int a = (int) (bossFadeAlpha * 180); // 0..180/255
            Paint dim = new Paint();
            dim.setColor(Color.BLACK);
            dim.setAlpha(a);
            canvas.drawRect(0, 0, getWidth(), getHeight(), dim);

            // Vẽ ảnh congratulations ở giữa
            if (bmpCongrats != null) {
                float maxW = getWidth() * 0.6f;
                float maxH = getHeight() * 0.3f;
                float scale = Math.min(maxW / bmpCongrats.getWidth(), maxH / bmpCongrats.getHeight());
                int w = (int) (bmpCongrats.getWidth() * scale);
                int h = (int) (bmpCongrats.getHeight() * scale);
                int left = (getWidth() - w) / 2;
                int top  = (getHeight() - h) / 2;
                Rect dst = new Rect(left, top, left + w, top + h);
                canvas.drawBitmap(bmpCongrats, null, dst, null);
            }

            // Sau 2s thì chuyển High Score
            // Sau 2s thì báo WIN ra Activity (thay vì tự mở HighScore)
            long t = System.currentTimeMillis() - bossDefeatAtMs;
            if (t >= BOSS_CONGRATS_DURATION_MS && !bossTransitioned) {
                bossTransitioned = true;
                // ✅ Gọi callback onWin để LevelXActivity mở LevelClearActivity
                if (onWinListener != null) onWinListener.onWin();
            }
        }

        // === Death overlay (tối mờ + ảnh gameover trong ~2s) ===
        if (deathSequence) {
            long t = System.currentTimeMillis() - deathStartMs;

            // Alpha tăng dần 0 -> 180 trong 2 giây
            int alpha = (int) Math.min(180, (t * 180) / 2000);
            dimPaint.setAlpha(alpha);

            // Phủ mờ toàn màn
            canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

            // Vẽ ảnh "game over" ở giữa, scale vừa khung
            if (bmpGameOver != null) {
                float maxW = getWidth() * 0.6f;   // tối đa 60% chiều rộng màn
                float maxH = getHeight() * 0.3f;  // tối đa 30% chiều cao màn
                float scale = Math.min(maxW / bmpGameOver.getWidth(), maxH / bmpGameOver.getHeight());

                float drawW = bmpGameOver.getWidth() * scale;
                float drawH = bmpGameOver.getHeight() * scale;
                float x = (getWidth() - drawW) / 2f;
                float y = (getHeight() - drawH) / 2f;

                Rect dst = new Rect((int) x, (int) y, (int) (x + drawW), (int) (y + drawH));
                canvas.drawBitmap(bmpGameOver, null, dst, null);
            }

            // Sau 2 giây mới chuyển màn
            if (t >= 2000 && !deathTransitioned) {
                deathTransitioned = true;
                gameOver = true;  // cho game loop dừng
                if (listener != null) listener.onGameOver(); // Level1Activity sẽ start GameOverActivity
            }
        }

        // 7) HUD + Buttons
        if (playerHud != null && playerMgr != null) {
            long rf = playerMgr.getRemainingCooldownMs(PlayerManager.SkillType.FIREBALL);
            long tf = playerMgr.getCooldownMs(PlayerManager.SkillType.FIREBALL);
            float fireRatio = (tf > 0) ? (rf / (float) tf) : 0f;

            long ri = playerMgr.getRemainingCooldownMs(PlayerManager.SkillType.ICESPIKE);
            long ti = playerMgr.getCooldownMs(PlayerManager.SkillType.ICESPIKE);
            float iceRatio = (ti > 0) ? (ri / (float) ti) : 0f;

            long rs = playerMgr.getRemainingCooldownMs(PlayerManager.SkillType.SHIELD);
            long ts = playerMgr.getCooldownMs(PlayerManager.SkillType.SHIELD);
            float shieldRatio = (ts > 0) ? (rs / (float) ts) : 0f;

            playerHud.setFireCooldownRatio(fireRatio);
            playerHud.setIceCooldownRatio(iceRatio);
            playerHud.setShieldCooldownRatio(shieldRatio);
        }
        if (playerHud != null) {
            playerHud.draw(canvas, player, getWidth(), getHeight()); // nếu bạn đã có hàm draw tổng
            playerHud.drawFireButton(canvas); // NEW: vẽ nút bắn bằng PNG
            playerHud.drawIceButton(canvas);
            playerHud.drawShieldButton(canvas);
        }

        //Vẽ Time vs Score
        drawTimer(canvas);
        drawScore(canvas);

        // 8) Audio toggles (vẽ sau cùng)
        drawAudioToggles(canvas);
    }

    private void drawTimer(Canvas canvas) {
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

        // LƯU lại khung TIME để vẽ SCORE dựa vào
        if (timePillRect == null) timePillRect = new android.graphics.RectF();
        timePillRect.set(rectLeft, rectTop, rectRight, rectBottom);
    }

    // Vẽ SCORE ở góc phải trên, pill bo tròn giống style TIME
    private void drawScore(Canvas canvas) {
        // Text style: tái dùng timePaint cho đồng bộ
        Paint text = new Paint(timePaint);
        text.setTextAlign(Paint.Align.LEFT);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.argb(120, 0, 0, 0));
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(4f);
        stroke.setColor(Color.WHITE);

        String scoreText = "SCORE " + score;

        // Đo kích thước pill SCORE
        float padX = 24f, padY = 12f;
        Paint.FontMetrics fm = text.getFontMetrics();
        float textW = text.measureText(scoreText);
        float textH = fm.bottom - fm.top;
        float pillW = textW + padX * 2f;
        float pillH = textH + padY * 2f;

        // Hàng trên cùng (cùng Top với TIME)
        float top = (timePillRect != null) ? timePillRect.top : 24f;
        float bottom = top + pillH;

        // Vị trí mong muốn: ngay bên phải TIME, cách 12dp
        float gap = dp(12);
        float desiredLeft = (timePillRect != null) ? (timePillRect.right + gap) : (canvas.getWidth() * 0.55f);
        float desiredRight = desiredLeft + pillW;

        // Biên an toàn bên phải: nếu có PauseBtnRect thì né nó, còn không thì dùng lề 24px
        float rightSafe = canvas.getWidth() - 24f;
        if (pauseBtnRect != null) {
            rightSafe = Math.min(rightSafe, pauseBtnRect.left - dp(12)); // cách Pause thêm 12dp
        }

        // Nếu SCORE tràn vào vùng Pause/biên phải thì lùi sang trái để vừa khít
        float shift = Math.max(0f, desiredRight - rightSafe);
        float left = desiredLeft - shift;
        float right = left + pillW;

        // Nếu vẫn còn chạm TIME (màn hình nhỏ, chữ dài), đảm bảo tối thiểu cách TIME 8dp
        if (timePillRect != null) {
            float minLeft = timePillRect.right + dp(8);
            if (left < minLeft) {
                left = minLeft;
                right = left + pillW;
            }
        }

        // Vẽ
        float radius = 24f;
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, bg);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, stroke);

        float baseline = top + padY - fm.top;
        canvas.drawText(scoreText, left + padX, baseline, text);
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

    // ===== Buttons layout =====
    private void setupButtons(int w, int h) {
        int margin = (int) (16 * getResources().getDisplayMetrics().density);

        // Layout 3 nút kỹ năng qua HUD (góc dưới-phải)
        if (playerHud != null) {
            playerHud.layoutActionButtons(
                    w, h,
                    R.drawable.fireball_button,
                    R.drawable.icespike_button,
                    R.drawable.shield_button1
            );
        }

        // Music/Sound bottom-center (horizontal)
        int audioSize = (int) dp(56);
        int spacing   = (int) dp(12);

        int totalW = audioSize * 2 + spacing;
        int leftX  = (w - totalW) / 2;
        int topY   = h - margin - audioSize;

        musicBtnRect = new Rect(leftX, topY, leftX + audioSize, topY + audioSize);
        soundBtnRect = new Rect(leftX + audioSize + spacing, topY,
                leftX + audioSize + spacing + audioSize, topY + audioSize);
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

    private boolean isInFireButton(float x, float y) {
        return playerHud != null && playerHud.isInFireButton(x, y);
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
        if (player == null || playerMgr == null) return;

        float px = player.centerX();
        float py = player.centerY();

        // Tìm mục tiêu gần nhất: trong enemies + boss
        float bestD2 = Float.MAX_VALUE;
        float tx = Float.NaN, ty = Float.NaN;

        // 1) enemies
        if (enemyMgr != null) {
            for (Enemy en : enemyMgr.list()) {
                try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
                float ex = en.x + en.w / 2f;
                float ey = en.y + en.h / 2f;
                float dx = ex - px, dy = ey - py;
                float d2 = dx*dx + dy*dy;
                if (d2 < bestD2) {
                    bestD2 = d2; tx = ex; ty = ey;
                }
            }
        }

        // 2) boss
        if (bossMgr != null && bossMgr.isActive()) {
            Boss b = bossMgr.getBoss();
            if (b != null && b.getState() != Boss.State.DIE) {
                float bx = b.x + b.w / 2f;
                float by = b.y + b.h / 2f;
                float dx = bx - px, dy = by - py;
                float d2 = dx*dx + dy*dy;
                if (d2 < bestD2) {
                    bestD2 = d2; tx = bx; ty = by;
                }
            }
        }

        if (!Float.isNaN(tx)) {
            // NEW: xin skill từ PlayerManager (tự check cooldown + trừ energy/mana)
            Fireball fb = playerMgr.tryUseFireball(tx, ty);
            if (fb != null) {
                fireballs.add(fb);
                playFireSfx();
            } else {
                // Không bắn được: cooldown/thiếu tài nguyên
                // long cd = playerMgr.getRemainingCooldownMs(SkillType.FIREBALL);
            }
        }
    }

    private void tryShootIceAtNearestEnemy() {
        if (player == null || playerMgr == null) return;

        float px = player.centerX();
        float py = player.centerY();

        float bestD2 = Float.MAX_VALUE;
        float tx = Float.NaN, ty = Float.NaN;

        // 1) Enemy
        if (enemyMgr != null) {
            for (Enemy en : enemyMgr.list()) {
                try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
                float ex = en.x + en.w / 2f;
                float ey = en.y + en.h / 2f;
                float dx = ex - px, dy = ey - py;
                float d2 = dx*dx + dy*dy;
                if (d2 < bestD2) { bestD2 = d2; tx = ex; ty = ey; }
            }
        }

        // 2) Boss
        if (bossMgr != null && bossMgr.isActive()) {
            Boss b = bossMgr.getBoss();
            if (b != null && b.getState() != Boss.State.DIE) {
                float bx = b.x + b.w / 2f;
                float by = b.y + b.h / 2f;
                float dx = bx - px, dy = by - py;
                float d2 = dx*dx + dy*dy;
                if (d2 < bestD2) { bestD2 = d2; tx = bx; ty = by; }
            }
        }

        if (!Float.isNaN(tx)) {
            IceSpike spike = playerMgr.tryUseIceSpike(tx, ty);
            if (spike != null) {
                icespikes.add(spike);
                playIceSfx();
            }
        }
    }

    // ===== SFX =====
    private void playFireSfx() {
        if (paused) return;
        if (!soundEnabled) return;
        if (soundPool == null) return;
        if (!sfxLoaded) return;
        soundPool.play(sfxFireId, sfxVolume, sfxVolume, 1, 0, 1.0f);
    }

    private void playIceSfx() {
        if (paused || !soundEnabled || soundPool == null || !sfxLoaded || sfxIceId == 0) return;
        soundPool.play(sfxIceId, sfxVolume, sfxVolume, 1, 0, 1.0f);
    }

    private void playShieldSfx() {
        if (paused || !soundEnabled || soundPool == null || !sfxLoaded || sfxShieldId == 0) return;
        soundPool.play(sfxShieldId, sfxVolume, sfxVolume, 1, 0, 1.0f);
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

        float offX = cameraX - islandX;
        float offY = cameraY - islandY;

        // Player
        float pl = player.x - offX;
        float pt = player.y - offY;
        float pr = pl + player.w;
        float pb = pt + player.h;
        c.drawRect(pl, pt, pr, pb, debugPaintPlayer);

        // Enemies
        for (Enemy en : enemyMgr.list()) {
            try { if (en.getState() == Enemy.State.DIE) continue; } catch (Throwable ignore) {}
            float el = en.x - offX;
            float et = en.y - offY;
            float er = el + en.w;
            float eb = et + en.h;
            c.drawRect(el, et, er, eb, debugPaintEnemy);
        }
    }

    private void maybeSpawnHeart() {
        long now = System.currentTimeMillis();
        if (now - lastHeartSpawnAtMs < HEART_SPAWN_INTERVAL_MS) return;
        if (bmpHeart == null) return;
        if (hearts.size() >= HEART_MAX_ON_MAP) {
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

    private void maybeSpawnShieldHeart() {
        long now = System.currentTimeMillis();
        if (now - lastShieldHeartSpawnAtMs < SHIELD_HEART_SPAWN_INTERVAL_MS) return;
        if (bmpShieldHeart == null) return;
        if (shieldHearts.size() >= SHIELD_HEART_MAX_ON_MAP) {
            lastShieldHeartSpawnAtMs = now;
            return;
        }

        Rect pr = getPlayableRect();
        int margin = 40;
        int maxX = Math.max(1, pr.width()  - bmpShieldHeart.getWidth()  - margin * 2);
        int maxY = Math.max(1, pr.height() - bmpShieldHeart.getHeight() - margin * 2);
        if (maxX <= 0 || maxY <= 0) return;

        int hx = pr.left + random.nextInt(maxX) + margin;
        int hy = pr.top  + random.nextInt(maxY) + margin;

        shieldHearts.add(new ShieldHeart(hx, hy, bmpShieldHeart));
        lastShieldHeartSpawnAtMs = now;
    }

    // Trong method updateShieldHeartsAndPickup():
    private void updateShieldHeartsAndPickup() {
        if (player == null) return;
        Rect pRect = new Rect(player.x, player.y, player.x + player.w, player.y + player.h);

        for (int i = shieldHearts.size() - 1; i >= 0; i--) {
            ShieldHeart h = shieldHearts.get(i);
            if (h.isConsumed()) { shieldHearts.remove(i); continue; }

            if (Rect.intersects(pRect, h.getHitbox())) {
                // Ăn item → bật ShieldBomb thay vì Shield thường
                player.addShield(SHIELD_HEART_AMOUNT, SHIELD_HEART_DURATION_MS,
                        Player.ShieldType.BOMB);
                h.consume();
                shieldHearts.remove(i);
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

    private void drawShieldHearts(Canvas canvas) {
        if (shieldHearts.isEmpty()) return;
        float offX = cameraX - islandX;
        float offY = cameraY - islandY;
        for (ShieldHeart h : shieldHearts) {
            h.draw(canvas, offX, offY);
        }
    }

    private boolean highScoreLaunched = false;
    private void openHighScoreScreen() {
        if (highScoreLaunched) return;
        highScoreLaunched = true;
        Context ctx = getContext();
        Intent i = new Intent(ctx, com.example.game2dfighting.view.HighScoreActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    // Kích hoạt trạng thái thắng: lưu điểm/thời gian + bật overlay "Congratulations" rồi tự chuyển HighScore
    public void triggerWinByCondition() {
        if (!bossDefeated && !deathSequence) {
            long elapsed = getElapsedMsAccurate();
            ScoreManager.saveRun(getContext(), getScore(), elapsed, System.currentTimeMillis(), levelName);

            timerPaused = true;                // dừng thời gian
            bossDefeated = true;               // bật overlay chúc mừng
            bossDefeatAtMs = System.currentTimeMillis();
            bossTransitioned = false;
            bossFadeAlpha = 0f;
        }
    }

    private void applyStartingLevelIfAny() {
        if (startingPlayerLevel == null) return;

        // Ưu tiên gọi API đúng nếu PlayerManager/Player có:
        try {
            // 1) Nếu PlayerManager có setLevel(int)
            playerMgr.getClass().getMethod("setLevel", int.class)
                    .invoke(playerMgr, startingPlayerLevel);
            return;
        } catch (Throwable ignore) {}

        try {
            // 2) Nếu Player có setLevel(int)
            player.getClass().getMethod("setLevel", int.class)
                    .invoke(player, startingPlayerLevel);
            return;
        } catch (Throwable ignore) {}

        // 3) Fallback (không có API đặt thẳng level): gọi "force/recalc" nếu có
        try {
            playerMgr.getClass().getMethod("applyStartingLevel", int.class)
                    .invoke(playerMgr, startingPlayerLevel);
            return;
        } catch (Throwable ignore) {}

        // 4) Cuối cùng: tăng thủ công (nếu có getLevel/levelUp)
        try {
            int cur = (int) playerMgr.getClass().getMethod("getLevel").invoke(playerMgr);
            while (cur < startingPlayerLevel) {
                playerMgr.getClass().getMethod("levelUp").invoke(playerMgr);
                cur++;
            }
        } catch (Throwable ignore) {}
    }

}
