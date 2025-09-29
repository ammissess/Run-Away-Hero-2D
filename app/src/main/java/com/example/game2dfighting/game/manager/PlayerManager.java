package com.example.game2dfighting.game.manager;

import android.content.Context;

import com.example.game2dfighting.game.entity.Player;
import com.example.game2dfighting.game.skill.Fireball;
import com.example.game2dfighting.game.skill.IceSpike;
import java.util.EnumMap;
import java.util.Map;

public class PlayerManager {

    public enum SkillType {
        FIREBALL, // cầu lửa
        ICESPIKE,  // mũi băng
        SHIELD   // khiên
    }

    public static class SkillConfig {
        public final long cooldownMs;
        public final int energyCost;
        public final int manaCost;

        public SkillConfig(long cooldownMs, int energyCost, int manaCost) {
            this.cooldownMs = cooldownMs;
            this.energyCost = energyCost;
            this.manaCost = manaCost;
        }
    }

    private final Context ctx;
    private final Player player;
    private final int mapW, mapH;

    private final Map<SkillType, SkillConfig> configs = new EnumMap<>(SkillType.class);
    private final Map<SkillType, Long> readyAt = new EnumMap<>(SkillType.class);

    private int energyRegenPerSec = 2;
    private int manaRegenPerSec   = 1;
    private long energyAccuMs = 0;
    private long manaAccuMs   = 0;

    public PlayerManager(Context ctx, Player player, int mapW, int mapH) {
        this.ctx = ctx;
        this.player = player;
        this.mapW = mapW;
        this.mapH = mapH;

        // cấu hình mặc định: cầu lửa
        configs.put(SkillType.FIREBALL, new SkillConfig(
                250L,   // cooldown 0.25s
                6,      // tốn 6 energy
                0       // không tốn mana
        ));

        // cấu hình mặc định: mũi băng
        configs.put(SkillType.ICESPIKE, new SkillConfig(
                500L,   // cooldown 0.5s
                4,      // tốn 4 energy
                0       // không tốn mana
        ));

        configs.put(SkillType.SHIELD, new SkillConfig(
                2000L,  // cooldown 2 giây
                10,      // không tốn energy
                0      // tốn mana
        ));


        long now = System.currentTimeMillis();
        for (SkillType t : SkillType.values()) {
            readyAt.put(t, now);
        }
    }

    public void update(long dtMs) {
        if (energyRegenPerSec > 0) {
            energyAccuMs += dtMs;
            while (energyAccuMs >= 1000) {
                energyAccuMs -= 1000;
                player.setEnergy(player.getEnergy() + energyRegenPerSec);
            }
        }
        if (manaRegenPerSec > 0) {
            manaAccuMs += dtMs;
            while (manaAccuMs >= 1000) {
                manaAccuMs -= 1000;
                player.setMana(player.getMana() + manaRegenPerSec);
            }
        }
    }

    /** Thử dùng kỹ năng. Trả về Fireball nếu cast thành công, null nếu không. */
    /** Thử dùng kỹ năng. Trả về projectile (Fireball/IceSpike) nếu cast thành công, null nếu không. */
    public Object tryUseSkill(SkillType type, float targetWorldX, float targetWorldY) {
        if (player == null) return null;
        SkillConfig cfg = configs.get(type);
        if (cfg == null) return null;

        long now = System.currentTimeMillis();
        long ra = readyAt.getOrDefault(type, 0L);
        if (now < ra) return null; // chưa hết cooldown

        if (player.getEnergy() < cfg.energyCost || player.getMana() < cfg.manaCost) {
            return null;
        }

        player.setEnergy(player.getEnergy() - cfg.energyCost);
        player.setMana(player.getMana() - cfg.manaCost);
        readyAt.put(type, now + cfg.cooldownMs);

        switch (type) {
            case FIREBALL:
                return player.shootFireballToward(targetWorldX, targetWorldY, mapW, mapH, ctx);
            case ICESPIKE:
                return player.shootIceSpikeToward(targetWorldX, targetWorldY, mapW, mapH, ctx);
            case SHIELD:
                player.addShield(50, 5000); // 50 shieldHP, tồn tại 5 giây
                return null;
        }
        return null;
    }

    // === Overload cho từng skill ===
    public Fireball tryUseFireball(float tx, float ty) {
        return (Fireball) tryUseSkill(SkillType.FIREBALL, tx, ty);
    }

    public IceSpike tryUseIceSpike(float tx, float ty) {
        return (IceSpike) tryUseSkill(SkillType.ICESPIKE, tx, ty);
    }

    public void tryUseShield() {
        tryUseSkill(SkillType.SHIELD, 0, 0);
    }

    public long getRemainingCooldownMs(SkillType type) {
        long now = System.currentTimeMillis();
        long ra = readyAt.getOrDefault(type, 0L);
        return Math.max(0, ra - now);
    }

    public void setEnergyRegenPerSec(int v){ energyRegenPerSec = Math.max(0, v); }
    public void setManaRegenPerSec(int v){ manaRegenPerSec = Math.max(0, v); }
}
