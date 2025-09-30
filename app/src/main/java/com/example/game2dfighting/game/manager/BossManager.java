package com.example.game2dfighting.game.manager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.example.game2dfighting.game.entity.Boss;
import com.example.game2dfighting.game.entity.Player;

/**
 * Quản lý Boss riêng: spawn theo thời gian (mặc định 50s), update di chuyển, combat, hurt, vẽ HP bar.
 */
public class BossManager {
    private final Context ctx;
    private final int mapW, mapH;

    private Boss boss = null;
    private boolean spawned = false;

    // Thời điểm xuất hiện boss (ms sau khi tạo manager)
    private final long bossStartAtMs;
    private final long startTime = System.currentTimeMillis();

    // Combat timing
    private static final long BOSS_COOLDOWN_MS   = 1200L;   // cooldown đánh của boss
    private static final long PLAYER_COOLDOWN_MS = 600L;    // cooldown đánh của player
    // NEW: thời gian đứng yên để hiển thị attack rõ ràng (ms)
    private static final long BOSS_ATTACK_LOCK_MS = 1000L;
    private long nextBossAttackAtMs = 0L;
    private long nextPlayerAttackAtMs = 0L;
    // NEW: thời điểm hết “khóa tấn công”
    private long bossAttackLockUntilMs = 0L;

    // HP boss quản lý ở Manager
    private int bossHp = Boss.BASE_HP;

    // Vẽ HP bar boss
    private final Paint hpBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpFg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hpOutline = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Tính điểm
    public interface KillListener { void onBossKilled(); }
    private KillListener killListener;
    public void setKillListener(KillListener l) { this.killListener = l; }


    /** Dùng ctor này nếu muốn đổi thời gian xuất hiện (ms). */
    public BossManager(Context ctx, int mapW, int mapH, long appearAfterMs) {
        this.ctx = ctx;
        this.mapW = mapW;
        this.mapH = mapH;
        this.bossStartAtMs = appearAfterMs; // ví dụ: 50_000L (50s)

        // Màu thanh máu
        hpBg.setColor(0xFF333333);
        hpFg.setColor(0xFFFF0000); // ĐỎ
        hpOutline.setStyle(Paint.Style.STROKE);
        hpOutline.setStrokeWidth(2.5f);
        hpOutline.setColor(0xFF8A2BE2); // tím viền (BlueViolet)
    }

    /** Ctor mặc định: boss xuất hiện sau 10 giây. */
    public BossManager(Context ctx, int mapW, int mapH) {
        this(ctx, mapW, mapH, 10_000L);
    }

    // ====== Trạng thái / Getter ======
    public boolean isActive()    { return boss != null && boss.getState() != Boss.State.DIE; }
    public boolean isSpawned()   { return spawned; }
    public boolean isDefeated()  { return boss == null && spawned; }
    public Boss getBoss()        { return boss; }
    public int  getHp()          { return bossHp; }
    public int  getHpMax()       { return Boss.BASE_HP; }

    // ====== Spawn theo thời gian ======
    public void maybeSpawn() {
        if (spawned) return;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= bossStartAtMs) {
            spawned = true;

            // Spawn giữa map (tuỳ chỉnh)
            int bw = 200;
            int bh = 200;
            int bx = (mapW - bw) / 2;
            int by = (mapH - bh) / 3;

            boss = new Boss(ctx, bx, by, bw, bh);
            bossHp = Boss.BASE_HP;
            nextBossAttackAtMs = 0L;
            nextPlayerAttackAtMs = 0L;
        }
    }

    // ====== Update + Combat ======
    public void update(Player p, long dtMs) {
        if (boss == null) return;

        // 0) Luôn advance animation mỗi frame (kể cả DIE/HURT/ATTACK/IDLE)
        try { boss.update(dtMs); } catch (Throwable ignore) {}

        long now = System.currentTimeMillis();

        // 1) Nếu đang DIE: chờ anim xong rồi remove, không di chuyển/combat
        if (boss.getState() == Boss.State.DIE) {
            if (boss.isDieAnimDone()) {
                boss = null; // biến mất ngay sau khi die anim hoàn tất
            }
            return; // giữ boss để draw anim trong frame này
        }

        // 2) Di chuyển đuổi, nhưng KHÔNG cho chồng lên nhân vật
        //    Đồng thời, nếu boss đang ATTACK thì không pursue để tránh "giật" animation tấn công.
        if (boss.getState() != Boss.State.ATTACK) {
            int oldX = boss.x, oldY = boss.y;
            try { boss.pursue(p.x, p.y, dtMs); } catch (Throwable ignore) {}
            if (isOverlapCircle(p, boss, 0f)) {
                boss.x = oldX;
                boss.y = oldY;
                // Khi bị chồng lên nhau thì buộc về IDLE để reset anim di chuyển
                try { boss.forceIdle(); } catch (Throwable ignore) {}
            }
        } else {
            // Đang ATTACK:
            if (System.currentTimeMillis() < bossAttackLockUntilMs) {
                // Còn trong thời gian khoá -> đứng yên, KHÔNG forceIdle để không cắt anim
                // (Không pursue gì cả)
            } else {
                // Hết khoá rồi -> cho phép quay về IDLE để tiếp tục logic di chuyển/chiến đấu
                try { boss.forceIdle(); } catch (Throwable ignore) {}
            }
        }

        // 3) Combat (chỉ khi không DIE)
        float bossRadius   = Math.min(boss.w, boss.h) / 2f;
        float playerRadius = Math.min(p.w, p.h) / 2f;
        float trigger      = bossRadius + playerRadius + 18f;

        float ddx = (p.x + p.w/2f) - (boss.x + boss.w/2f);
        float ddy = (p.y + p.h/2f) - (boss.y + boss.h/2f);
        float dist2 = ddx*ddx + ddy*ddy;

        if (dist2 <= trigger*trigger) {
            // Boss -> Player: chỉ bắt đầu attack khi đến hạn và không đang DIE/ATTACK
            if (now >= nextBossAttackAtMs && boss.getState() != Boss.State.ATTACK) {
                try { boss.startAttack(); } catch (Throwable ignore) {}

                // Khoá tấn công: giữ state ATTACK đủ lâu để anim hiển thị rõ
                bossAttackLockUntilMs = now + BOSS_ATTACK_LOCK_MS;

                // (Tuỳ bạn) Gây damage ngay, hoặc có thể dời về giữa animation nếu Boss hỗ trợ callback/frame
                try { p.takeDamage(Boss.BASE_DAMAGE); } catch (Throwable ignore) {}

                nextBossAttackAtMs = now + BOSS_COOLDOWN_MS;
            }

            // Player -> Boss (auto-attack)
            if (now >= nextPlayerAttackAtMs) {
                try { p.startAttack(); } catch (Throwable ignore) {}

                int oldHp = bossHp;
                bossHp = Math.max(0, bossHp - 12); // TODO: thay bằng damage thực từ Player

                if (bossHp == 0) {
                    // Kết liễu: vào DIE ngay, KHÔNG gọi onHurt trước để không cắt die anim
                    if (boss.getState() != Boss.State.DIE) {
                        try { boss.onDie(); } catch (Throwable ignore) {}
                        if (killListener != null) killListener.onBossKilled();
                    }
                } else if (bossHp < oldHp) {
                    try { boss.onHurt(); } catch (Throwable ignore) {}
                }

                nextPlayerAttackAtMs = now + PLAYER_COOLDOWN_MS;
            }
        }
        // 4) Cleanup DIE đã xử lý ở đầu (khi getState()==DIE)
    }

    // ====== Nhận sát thương từ đạn (gọi từ GameView khi bullet trúng Boss) ======
    public void applyBulletHit(int dmg) {
        if (boss == null) return;
        if (boss.getState() == Boss.State.DIE) return;

        int oldHp = bossHp;
        bossHp = Math.max(0, bossHp - Math.max(0, dmg));

        if (bossHp == 0) {
            if (boss.getState() != Boss.State.DIE) {
                try { boss.onDie(); } catch (Throwable ignore) {}
                if (killListener != null) killListener.onBossKilled();
            }
        } else if (bossHp < oldHp) {
            try { boss.onHurt(); } catch (Throwable ignore) {}
        }
    }

    // ====== Vẽ Boss + thanh máu ======
    public void draw(Canvas c, int cameraX, int cameraY) {
        if (boss == null) return;

        // Vẽ boss (bao gồm cả anim DIE/ATTACK/HURT/IDLE do update(dtMs) đã advance)
        boss.draw(c, cameraX, cameraY, new Paint(Paint.ANTI_ALIAS_FLAG));

        // Khi đang DIE, thường không cần vẽ HP bar nữa
        if (boss.getState() == Boss.State.DIE) return;

        // === 3 thanh HP ngang màu đỏ, mỗi thanh có viền tím riêng, xếp dọc trên đỉnh đầu boss ===
        float bossScreenX = boss.x - cameraX;
        float bossScreenY = boss.y - cameraY;

        // Rộng hơn, cao rõ hơn, khoảng cách thoáng hơn
        float barW   = Math.max(180f, boss.w * 1.25f);   // to hơn
        float segH   = Math.max(6f,  boss.h * 0.065f);   // cao mỗi thanh
        float spacing = segH * 1.0f;                     // cách nhau rõ ràng
        float gap    = 10f;                              // cách đỉnh đầu boss

        // gốc trái-trên của cụm 3 thanh (căn giữa theo bề ngang boss)
        float bx    = bossScreenX + (boss.w - barW) / 2f;
        float byTop = bossScreenY - gap - (3 * segH + 2 * spacing);

        // Tỷ lệ HP còn lại 0..1
        float ratio = Math.max(0f, Math.min(1f, bossHp / (float) Boss.BASE_HP));

        // Phân bổ HP vào 3 thanh: đầy thanh trên trước rồi tới dưới
        int segments = 3;
        float totalUnits = ratio * segments; // 0..3

        for (int i = 0; i < segments; i++) {
            float segTop = byTop + i * (segH + spacing);
            float segBottom = segTop + segH;
            float segLeft = bx;
            float segRight = bx + barW;

            // Nền
            c.drawRect(segLeft, segTop, segRight, segBottom, hpBg);

            // Mức fill riêng từng thanh (0..1)
            float localFill = Math.max(0f, Math.min(1f, totalUnits - i));
            if (localFill > 0f) {
                float fillRight = segLeft + barW * localFill;
                c.drawRect(segLeft, segTop, fillRight, segBottom, hpFg); // ĐỎ
            }

            // Viền tím cho từng thanh
            c.drawRect(segLeft, segTop, segRight, segBottom, hpOutline);
        }
    }

    // --- NO-OVERLAP CHECK (circle vs circle) ---
    private boolean isOverlapCircle(Player p, Boss b, float marginPx) {
        float pcx = p.x + p.w / 2f, pcy = p.y + p.h / 2f;
        float bcx = b.x + b.w / 2f, bcy = b.y + b.h / 2f;

        float pr = Math.min(p.w, p.h) / 2f;
        float br = Math.min(b.w, b.h) / 2f;

        float minDist = pr + br + marginPx;
        float dx = pcx - bcx, dy = pcy - bcy;
        return (dx*dx + dy*dy) < (minDist * minDist);
    }
}
