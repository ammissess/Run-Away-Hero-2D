package com.example.game2dfighting.game.entity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import com.example.game2dfighting.R;
import com.example.game2dfighting.game.core.GameObject;
import com.example.game2dfighting.game.core.SpriteAnim;
import com.example.game2dfighting.game.skill.BossBullet;
import com.example.game2dfighting.game.skill.LaserBeam;

import java.util.ArrayList;
import java.util.List;

/**
 * BossAngel có 2 hình thái:
 * - Form1: Bình thường, bắn đạn xanh dương tỏa ra nhiều hướng.
 * - Form2: Cuồng nộ khi còn nửa máu, bắn laser đỏ tập trung.
 */
public class BossAngel extends GameObject {
    private final Context ctx;

    // ==== TRẠNG THÁI ====
    private boolean furyMode = false;
    private boolean chargingLaser = false;
    private long chargeStartMs = 0L;
    private static final long LASER_CHARGE_TIME = 3000L;

    private long nextShootAtMs = 0L;
    private static final long FORM1_SHOOT_INTERVAL = 2000L;
    private static final long FORM2_LASER_INTERVAL = 5000L;

    // ==== THUỘC TÍNH ====
    private float baseSpeed = 2.0f;
    private float furySpeed = 4.0f;

    private final List<BossBullet> bullets = new ArrayList<>();
    private final List<LaserBeam> lasers = new ArrayList<>();

    private SpriteAnim idleForm1, idleForm2;
    private Paint laserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean attackEnabled = false;

    public void enableAttack() {
        attackEnabled = true;
    }

    public boolean isAttackEnabled() {
        return attackEnabled;
    }

    public BossAngel(Context ctx, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.ctx = ctx;
        setState(State.IDLE);
        loadAnimations(w, h);
        setupPaint();
    }

    private void setupPaint() {
        laserPaint.setColor(0xFFFF4444);
        laserPaint.setStrokeWidth(12f);
        laserPaint.setStyle(Paint.Style.STROKE);
        laserPaint.setShadowLayer(10f, 0, 0, 0xFFFF2222);
    }

    private void loadAnimations(int w, int h) {
        Bitmap[] idle1 = SpriteAnim.loadFrames(ctx, new int[]{
                R.drawable.angel_boss_f1_01, R.drawable.angel_boss_f1_02,
                R.drawable.angel_boss_f1_03, R.drawable.angel_boss_f1_04
        });
        Bitmap[] idle2 = SpriteAnim.loadFrames(ctx, new int[]{
                R.drawable.angel_boss_f2_01, R.drawable.angel_boss_f2_02,
                R.drawable.angel_boss_f2_03, R.drawable.angel_boss_f2_04
        });

        idleForm1 = new SpriteAnim(idle1, 120, true, w, h);
        idleForm2 = new SpriteAnim(idle2, 80, true, w, h); // form2 nhanh hơn
    }

    /** Kích hoạt hình thái cuồng nộ */
    public void activateFury() {
        if (!furyMode) {
            furyMode = true;
            if (idleForm2 != null) idleForm2.reset(); // reset anim form 2 khi đổi hình thái
        }
    }

    public boolean isFuryMode() {
        return furyMode;
    }

    public void update(long dtMs, Player player, int hp, int hpMax) {
//        // Kích hoạt cuồng nộ khi còn nửa máu
//        if (!furyMode && hp <= hpMax / 2) {
//            furyMode = true;
//            if (idleForm2 != null) idleForm2.reset();
//        }

        // Kích hoạt cuồng nộ khi còn 30% máu
        if (!furyMode && hp <= hpMax * 0.3f) {
            furyMode = true;
            if (idleForm2 != null) idleForm2.reset();
        }


        float dtSec = dtMs / 1000f;
        long now = System.currentTimeMillis();

        // Di chuyển qua lại trừ khi đang tụ lực
        float speed = furyMode ? furySpeed : baseSpeed;
        if (!chargingLaser) {
            x += (facingLeft ? -speed : speed);
            if (x < 200) facingLeft = false;
            else if (x > 1800) facingLeft = true;
        }

// Form 1: bắn đạn tròn xanh
        if (attackEnabled && !furyMode && now >= nextShootAtMs) {
            shootCircleBullets();
            nextShootAtMs = now + FORM1_SHOOT_INTERVAL;
        }

// Form 2: tụ lực laser
        if (attackEnabled && furyMode && !chargingLaser && now >= nextShootAtMs) {
            chargingLaser = true;
            chargeStartMs = now;
            nextShootAtMs = now + FORM2_LASER_INTERVAL;
        }

        // Sau 3s tụ lực thì bắn
        if (chargingLaser && now - chargeStartMs >= LASER_CHARGE_TIME) {
            shootLaser(player);
            chargingLaser = false;
        }

        // Update bullet và laser
        for (int i = bullets.size() - 1; i >= 0; i--) {
            BossBullet b = bullets.get(i);
            b.update(dtSec);
            if (!b.alive) bullets.remove(i);
        }

        for (int i = lasers.size() - 1; i >= 0; i--) {
            LaserBeam l = lasers.get(i);
            l.update(dtSec);
            if (!l.alive) lasers.remove(i);
        }

        // Animation update
        if (furyMode) idleForm2.update(dtMs);
        else idleForm1.update(dtMs);
    }

    public void draw(Canvas c, int camX, int camY, Paint p) {
        SpriteAnim current = furyMode ? idleForm2 : idleForm1;
        if (current != null) current.draw(c, x - camX, y - camY, p);

        // Đạn & laser
        for (BossBullet b : bullets) b.draw(c, camX, camY);
        for (LaserBeam l : lasers) l.draw(c, camX, camY);

        // Hiệu ứng tụ lực
        if (chargingLaser) {
            float progress = Math.min(1f, (System.currentTimeMillis() - chargeStartMs) / (float) LASER_CHARGE_TIME);
            int alpha = (int) (80 + 175 * progress);
            Paint glow = new Paint(p);
            glow.setColorFilter(new PorterDuffColorFilter(0xFFFF0000 | (alpha << 24), PorterDuff.Mode.ADD));
            c.drawCircle(x + w / 2f - camX, y + h / 2f - camY, 100 + 30 * progress, glow);
        }
    }

    private void shootCircleBullets() {
        int count = 8; // bắn 8 hướng
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            bullets.add(new BossBullet(x + w / 2f, y + h / 2f, dx, dy, 7f, 0xFF00BFFF)); // xanh dương
        }
    }

    private void shootLaser(Player player) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float dx = (player.x + player.w / 2f) - cx;
        float dy = (player.y + player.h / 2f) - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        dx /= len;
        dy /= len;
        lasers.add(new LaserBeam(cx, cy, dx, dy, 14f, 0xFFFF0000)); // đỏ tươi
    }

    public List<BossBullet> getBullets() { return bullets; }
    public List<LaserBeam> getLasers() { return lasers; }

    public void pursue(int targetX, int targetY, long dtMs) {
        if (state == State.DIE) return;
        if (chargingLaser) return; // không đuổi khi tụ lực

        float speed = furyMode ? furySpeed : baseSpeed;
        int dx = 0, dy = 0;

        if (targetX < x) dx = (int) -speed;
        else if (targetX > x) dx = (int) speed;

        if (targetY < y) dy = (int) -speed;
        else if (targetY > y) dy = (int) speed;

        if (dx < 0) setFacingLeft(true);
        else if (dx > 0) setFacingLeft(false);

        x += dx;
        y += dy;
    }
}
