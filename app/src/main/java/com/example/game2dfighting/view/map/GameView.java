package com.example.game2dfighting.view.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.game.entity.Enemy;
import com.example.game2dfighting.game.manager.EnemyManager;

import java.util.ArrayList;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "GameView";
    private SurfaceHolder holder;
    private Thread gameThread;
    private volatile boolean isRunning = false;

    // Map & Camera
    private final int mapWidth = 3000, mapHeight = 3000;
    private int cameraX = 0, cameraY = 0;

    // Entities
    private Player player;
    private EnemyManager enemyMgr;

    // Input (từ joystick/phím)
    private boolean movingUp, movingDown, movingLeft, movingRight;

    // Kiếm quay
    private final ArrayList<Sword> swords = new ArrayList<>();
    private static final int MAX_SWORDS = 14;
    private float angle = 0f; // độ

    // Điểm xanh (tăng mana & nâng cấp kiếm)
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

    // Damage config
    private final int SWORD_DAMAGE = 10;   // mỗi lần kiếm quét trúng quái
    private final int ENEMY_TOUCH_DAMAGE = 5; // mỗi frame chạm player (đơn giản)


    //Hinh nen
    private Bitmap background;

    //game over
    // Thêm biến mới
    private volatile boolean gameOver = false;

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
        player = new Player(100, 100, 100, 100);
        player.setMaxMana(10);  // như cũ

        swords.clear();
        swords.add(new Sword(0));

        enemyMgr = new EnemyManager(mapWidth, mapHeight);

        // Tải hình nền và làm mờ
        Bitmap originalBackground = BitmapFactory.decodeResource(getResources(), R.drawable.glass2_background);
        background = Bitmap.createBitmap(mapWidth, mapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(background);
        Paint paint = new Paint();
        //paint.setAlpha(128); // Giảm độ trong suốt (0 - hoàn toàn trong suốt, 255 - không trong suốt), 128 là 50% mờ
        canvas.drawBitmap(Bitmap.createScaledBitmap(originalBackground, mapWidth, mapHeight, true), 0, 0, paint);
    }

    // ===== Pause API =====
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            movingUp = movingDown = movingLeft = movingRight = false;
            player.up = player.down = player.left = player.right = false;
        }
    }
    public boolean isPaused() { return paused; }

    // ===== Surface =====
    @Override public void surfaceCreated(SurfaceHolder holder) {
        this.holder = holder;

        if (points.isEmpty()) {
            int count = 200;
            for (int i = 0; i < count; i++) {
                int x = random.nextInt(Math.max(1, mapWidth - 40)) + 20;
                int y = random.nextInt(Math.max(1, mapHeight - 40)) + 20;
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
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        isRunning = false;
        try { if (gameThread != null) gameThread.join(); } catch (InterruptedException e) { Log.e(TAG, "stop", e); }
    }

    // ===== Loop =====
    @Override
    public void run() {
        while (isRunning) {
            if (holder == null || !holder.getSurface().isValid()) { sleep(16); continue; }

            long frameStart = System.currentTimeMillis();

            if (!paused && !gameOver) { // Chỉ cập nhật khi chưa paused và chưa game over
                // input -> player
                player.up = movingUp; player.down = movingDown; player.left = movingLeft; player.right = movingRight;

                // update player
                player.update();
                clampPlayerToMap();

                // camera follow
                cameraX = (int) (player.centerX() - getWidth()/2f);
                cameraY = (int) (player.centerY() - getHeight()/2f);
                clampCamera();

                // rotate swords
                angle += 3f; if (angle >= 360f) angle -= 360f;

                // enemies
                enemyMgr.maybeSpawn();
                enemyMgr.updateTowardsPlayer(player);

                // collisions
                handleSwordHitsEnemies();
                handleEnemiesHitPlayer();

                // points/mana/level-up swords
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

            long dt = System.currentTimeMillis() - frameStart;
            long sleep = 16 - dt; if (sleep > 0) sleep(sleep);

            // Nếu game over, dừng vòng lặp
            if (gameOver) {
                isRunning = false;
            }
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
    private void clampCamera() {
        if (cameraX < 0) cameraX = 0; if (cameraY < 0) cameraY = 0;
        cameraX = Math.min(cameraX, Math.max(0, mapWidth - getWidth()));
        cameraY = Math.min(cameraY, Math.max(0, mapHeight - getHeight()));
    }

    private static class Sword { float baseAngle; Sword(float a){ baseAngle=a; } }
    private static class Point { int x,y; Point(int x,int y){this.x=x;this.y=y;} }

    private void handleSwordHitsEnemies() {
        float cx = player.centerX(), cy = player.centerY();
        float swordLen = player.w * 1.5f;
        float halfThickness = player.w / 20f;

        // Duyệt danh sách theo index từ cuối để remove an toàn
        for (int i = enemyMgr.list().size() - 1; i >= 0; i--) {
            Enemy e = enemyMgr.list().get(i);
            boolean hit = false;
            for (int s = 0; s < swords.size(); s++) {
                float base = swords.get(s).baseAngle;
                float rad = (float) Math.toRadians(angle + base);
                float sx = (float) (cx + Math.sin(rad) * swordLen);
                float sy = (float) (cy + Math.cos(rad) * swordLen);
                if (circleVsSegment(e.x, e.y, e.radius, cx, cy, sx, sy, halfThickness)) {
                    hit = true; break;
                }
            }
            if (hit) {
                boolean dead = e.takeDamage(SWORD_DAMAGE);
                if (dead) enemyMgr.list().remove(i);
            }
        }
    }

    private void handleEnemiesHitPlayer() {
        float pr = Math.min(player.w, player.h) / 2f;
        for (int i = 0; i < enemyMgr.list().size(); i++) {
            Enemy e = enemyMgr.list().get(i);
            float dx = e.x - player.centerX();
            float dy = e.y - player.centerY();
            float rr = e.radius + pr;
            if (dx * dx + dy * dy <= rr * rr) {
                boolean dead = player.takeDamage(ENEMY_TOUCH_DAMAGE);
                if (dead) {
                    gameOver = true; // Đánh dấu game over
                    if (listener != null) {
                        listener.onGameOver(); // Gọi event game over
                    }
                    return; // Thoát khỏi phương thức để dừng vòng lặp
                }
            }
        }
    }



    private boolean circleVsSegment(float ex, float ey, float r,
                                    float ax, float ay, float bx, float by,
                                    float halfThickness) {
        float abx = bx - ax, aby = by - ay;
        float ab2 = abx*abx + aby*aby;
        if (ab2 < 1e-6f) {
            float dx = ex - ax, dy = ey - ay;
            float rr = r + halfThickness;
            return dx*dx + dy*dy <= rr*rr;
        }
        float t = ((ex - ax)*abx + (ey - ay)*aby) / ab2;
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
        float px = ax + t*abx, py = ay + t*aby;
        float dx = ex - px, dy = ey - py;
        float rr = r + halfThickness;
        return dx*dx + dy*dy <= rr*rr;
    }

    private void updatePointsAndLevelUpIfNeeded() {
        Rect playerRect = new Rect(player.x, player.y, player.x + player.w, player.y + player.h);
        for (int i = points.size() - 1; i >= 0; i--) {
            Point p = points.get(i);
            Rect pointRect = new Rect(p.x - 10, p.y - 10, p.x + 10, p.y + 10);
            if (Rect.intersects(playerRect, pointRect)) {
                points.remove(i);

                // tăng mana trong Player
                player.addMana(1);

                // Nâng cấp kiếm khi đủ mana
                if (player.getMana() >= player.getMaxMana() && swords.size() < MAX_SWORDS) {
                    player.setMana(0);
                    swords.add(new Sword(0));
                    int n = swords.size();
                    for (int j = 0; j < n; j++) {
                        float step = 360f / n;
                        swords.get(j).baseAngle = j * step;
                    }
                    player.setMaxMana(Math.min(300, Math.round(player.getMaxMana() * 1.1f)));
                }

                // spawn lại 1 điểm mới
                int x = random.nextInt(mapWidth - 40) + 20;
                int y = random.nextInt(mapHeight - 40) + 20;
                points.add(new Point(x, y));
            }
        }
    }

    // ===== Render =====
    private void render(Canvas canvas) {
        // Vẽ hình nền
        canvas.drawBitmap(background, -cameraX, -cameraY, null);

        // map nền (bỏ dòng canvas.drawColor(Color.WHITE) và mapBg)
        // Paint mapBg = new Paint();
        // mapBg.setStyle(Paint.Style.FILL);
        // mapBg.setColor(Color.rgb(240, 240, 240));
        // canvas.drawRect(0 - cameraX, 0 - cameraY, mapWidth - cameraX, mapHeight - cameraY, mapBg);

        // player (hình chữ nhật đỏ như trước)
        float drawPlayerX = player.x - cameraX, drawPlayerY = player.y - cameraY;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.RED);
        canvas.drawRect(drawPlayerX, drawPlayerY, drawPlayerX + player.w, drawPlayerY + player.h, paint);

        // enemies + máu overhead
        enemyMgr.draw(canvas, cameraX, cameraY);

        // swords
        drawSwords(canvas, drawPlayerX, drawPlayerY);

        // points (chấm xanh)
        drawPoints(canvas);

        // HUD: Mana (trên), dưới là HP, dưới nữa là Energy
        drawHud(canvas);

        // viền map
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setColor(Color.BLACK);
        canvas.drawRect(0 - cameraX, 0 - cameraY, mapWidth - cameraX, mapHeight - cameraY, paint);
        paint.setStyle(Paint.Style.FILL);

        // (tuỳ chọn) overlay PAUSED nếu bạn không dùng overlay XML
        if (paused) {
            Paint dim = new Paint();
            dim.setColor(Color.argb(120, 0, 0, 0));
            canvas.drawRect(0, 0, getWidth(), getHeight(), dim);

            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(Color.WHITE);
            t.setTextSize(64f);
            t.setTextAlign(Paint.Align.CENTER);
            // Vẽ chữ PAUSED ở giữa màn hình
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            canvas.drawText("RESUME", centerX, centerY, t);

            // Vẽ chữ QUIT bên dưới
            canvas.drawText("QUIT", centerX, centerY + 100, t);
        }
    }

    private void drawSwords(Canvas canvas, float drawPlayerX, float drawPlayerY) {
        paint.setColor(Color.BLACK);
        float swordLength = player.w * 1.5f;
        float swordWidth  = player.w / 10f;
        float centerX = drawPlayerX + player.w / 2f;
        float centerY = drawPlayerY + player.h / 2f;

        for (Sword sword : swords) {
            canvas.save();
            float currentAngle = angle + sword.baseAngle;
            canvas.rotate(currentAngle, centerX, centerY);
            canvas.drawRect(centerX - swordWidth/2f, centerY, centerX + swordWidth/2f, centerY + swordLength, paint);
            canvas.restore();
        }
    }

    private void drawPoints(Canvas canvas) {
        paint.setColor(Color.parseColor("#66FFFF"));
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            float sx = p.x - cameraX, sy = p.y - cameraY;
            canvas.drawCircle(sx, sy, 10f, paint);
        }
    }

    private void drawHud(Canvas canvas) {
        int w = (int) (getWidth() * 0.3f);
        drawBar(canvas, 10, 10,  w, 24, player.getMana(),  player.getMaxMana(), 0xFF1E88E5, "Mana");
        drawBar(canvas, 10, 44,  w, 24, player.getHp(),    player.getMaxHp(),   0xFFE53935, "HP");
        drawBar(canvas, 10, 78, w, 24, player.getEnergy(), player.getMaxEnergy(), 0xFFFFFF99, "Energy");


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
