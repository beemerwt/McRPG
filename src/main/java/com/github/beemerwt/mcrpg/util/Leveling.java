package com.github.beemerwt.mcrpg.util;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.data.PlayerData;
import com.github.beemerwt.mcrpg.data.SkillLinks;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.GeneralConfig;
import com.github.beemerwt.mcrpg.ui.XpBossbarManager;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.joml.Math;

import java.util.Map;
import java.util.UUID;

public final class Leveling {
    private Leveling() {}

    // ---------- XP curve helpers ----------

    private static long xpForLevel(int level, GeneralConfig.XpCurve curve) {
        if ("linear".equalsIgnoreCase(curve.base)) {
            // per-level requirement at this level
            return curve.linearBase + (long) level * curve.multiplier;
        }
        // quadratic per-level requirement: a*L^2 + b*L + c
        return curve.quadA * (long) level * (long) level + curve.quadB * (long) level + curve.quadC;
    }

    public static long xpForLevel(int level) {
        return xpForLevel(level, ConfigManager.getGeneralConfig().xpCurve);
    }

    public static int levelFromTotalXp(long total) {
        var generalCfg = ConfigManager.getGeneralConfig();
        int lvl = 0;
        long need = xpForLevel(1, generalCfg.xpCurve);
        long remain = total;
        while (remain >= need) {
            remain -= need;
            lvl++;
            need = xpForLevel(lvl + 1, generalCfg.xpCurve);
            if (lvl > generalCfg.maxLevel) break; // safety
        }
        return lvl;
    }

    public static long totalXpFromLevel(int level) {
        if (level <= 0) return 0L;

        var curve = ConfigManager.getGeneralConfig().xpCurve;

        if ("linear".equalsIgnoreCase(curve.base)) {
            // Sum_{i=1..L} (base + i*multiplier) = L*base + multiplier * L*(L+1)/2
            return (long) level * curve.linearBase + curve.multiplier
                    * (long) level * ((long) level + 1L) / 2L;
        }

        // Sum_{i=1..L} (a*i^2 + b*i + c)
        long L = level;
        long S1 = L * (L + 1L) / 2L;                 // 1 + 2 + ... + L
        long S2 = L * (L + 1L) * (2L * L + 1L) / 6L; // 1^2 + 2^2 + ... + L^2

        return curve.quadA * S2 + curve.quadB * S1 + curve.quadC * L;
    }

    // ---------- XP resolution for blocks ----------

    public static long resolveBlockXp(Map<String, Integer> blocks, Block block) {
        var id = Registries.BLOCK.getId(block);

        Integer direct = blocks.get(id.toString());
        if (direct != null) return direct;

        if (!blocks.isEmpty()) {
            RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
            for (Map.Entry<String, Integer> e : blocks.entrySet()) {
                String key = e.getKey();
                if (!key.startsWith("#")) continue;
                Identifier tagId = Identifier.tryParse(key.substring(1));
                if (tagId == null) continue;
                TagKey<Block> tagKey = TagKey.of(RegistryKeys.BLOCK, tagId);
                if (entry.isIn(tagKey)) return e.getValue();
            }
        }

        return 0;
    }

    // ---------- Public API: UUID-first ----------

    public static void addXp(UUID playerId, SkillType skill, long amount) {
        McRPG.getLogger().debug("Adding " + amount + " XP to " + playerId + " in skill " + skill);
        addXpInternal(playerId, skill, amount);
    }

    public static long getTotalXp(UUID playerId, SkillType skill) {
        if (SkillLinks.isAlias(skill)) {
            return getTotalXp(playerId, SkillLinks.primaryOf(skill));
        }

        if (SkillLinks.isComposite(skill)) {
            var c = SkillLinks.compositeOf(skill);
            double sum = 0.0;
            for (int i = 0; i < c.parts().length; i++) {
                long xi = getTotalXp(playerId, c.parts()[i]);
                sum += xi * c.weights()[i];
            }
            return Math.round(sum);
        }

        PlayerData data = McRPG.getStore().get(playerId);
        return data.xp.getOrDefault(skill, 0L);
    }

    public static int getLevel(UUID playerId, SkillType skill) {
        if (playerId == null) {
            McRPG.getLogger().severe("Leveling.getLevel() called with null playerId for skill " + skill);
            return 0;
        }

        if (SkillLinks.isAlias(skill)) {
            return getLevel(playerId, SkillLinks.primaryOf(skill));
        }
        if (SkillLinks.isComposite(skill)) {
            var c = SkillLinks.compositeOf(skill);
            double sum = 0.0;
            for (int i = 0; i < c.parts().length; i++) {
                int li = getLevel(playerId, c.parts()[i]);
                sum += li * c.weights()[i];
            }
            return (int) Math.floor(sum);
        }
        PlayerData data = McRPG.getStore().get(playerId);
        return levelFromTotalXp(data.xp.getOrDefault(skill, 0L));
    }

    // ---------- Convenience wrappers: ServerPlayerEntity ----------

    public static void addXp(ServerPlayerEntity player, SkillType skill, long amount) {
        addXpInternal(player.getUuid(), skill, amount);
    }

    public static long getTotalXp(ServerPlayerEntity player, SkillType skill) {
        return getTotalXp(player.getUuid(), skill);
    }

    public static int getLevel(ServerPlayerEntity player, SkillType skill) {
        return getLevel(player.getUuid(), skill);
    }

    // ---------- Core write paths ----------

    private static void addXpInternal(UUID playerId, SkillType skill, long amount) {
        if (amount == 0) return;
        ServerPlayerEntity player = McRPG.getServer().getPlayerManager().getPlayer(playerId);

        // Alias: write to primary but display the alias skill on the bossbar
        if (SkillLinks.isAlias(skill)) {
            SkillType primary = SkillLinks.primaryOf(skill);

            addXpBase(playerId, primary, amount); // no bossbar here
            long after  = getRawTotalXp(playerId, primary);

            if (player != null)
                XpBossbarManager.showSkillXp(player, skill, amount, after);
            return;
        }

        // Composite: show one bar for the composite skill, not its parts
        if (SkillLinks.isComposite(skill)) {
            var comp = SkillLinks.compositeOf(skill);

            long beforeVirtual = virtualCompositeTotalXp(playerId, comp);

            double remaining = amount;
            for (int i = 0; i < comp.parts().length; i++) {
                long share = Math.round(amount * comp.weights()[i]);
                if (i == comp.parts().length - 1) {
                    share = Math.round(remaining);
                }
                addXpBase(playerId, comp.parts()[i], share);
                remaining -= share;
            }

            long afterVirtual = virtualCompositeTotalXp(playerId, comp);
            long virtualAmount = Math.max(0L, afterVirtual - beforeVirtual);

            if (player != null)
                XpBossbarManager.showSkillXp(player, skill, virtualAmount, afterVirtual);
            return;
        }

        // Base skill
        addXpBase(playerId, skill, amount);
        long after  = getRawTotalXp(playerId, skill);

        // Show bar if player is online
        if (player != null)
            XpBossbarManager.showSkillXp(player, skill, amount, after);
    }

    // Writes XP into the canonical bucket (UUID) without emitting a bossbar.
    private static void addXpBase(UUID playerId, SkillType bucket, long amount) {
        if (amount == 0) return;
        PlayerData data = McRPG.getStore().get(playerId);
        long before = data.xp.getOrDefault(bucket, 0L);
        long after  = Math.max(0L, before + amount);
        data.xp.put(bucket, after);
    }

    private static long getRawTotalXp(UUID playerId, SkillType bucket) {
        PlayerData data = McRPG.getStore().get(playerId);
        return data.xp.getOrDefault(bucket, 0L);
    }

    private static long virtualCompositeTotalXp(UUID playerId, SkillLinks.Composite comp) {
        double weightedLevel = 0.0;
        double weightedFrac  = 0.0;

        for (int i = 0; i < comp.parts().length; i++) {
            SkillType part = comp.parts()[i];
            double w = comp.weights()[i];

            long tot = getRawTotalXp(playerId, part);
            int L = levelFromTotalXp(tot);

            long start = totalXpFromLevel(L);
            long next  = totalXpFromLevel(L + 1);
            double frac = (next > start) ? (tot - start) / (double) (next - start) : 0.0;

            weightedLevel += w * L;
            weightedFrac  += w * frac;
        }

        int Lfloor = (int) Math.floor(weightedLevel);
        long start = totalXpFromLevel(Lfloor);
        long next  = totalXpFromLevel(Lfloor + 1);
        long within = Math.round(weightedFrac * Math.max(0L, next - start));
        return start + within;
    }

    public static long cumulativeXpForLevel(int level) {
        long total = 0;

        for (int i = 1; i <= level; i++) {
            total += Leveling.xpForLevel(i);
        }
        return total;
    }

    public static long maxTotalXp() {
        int max = java.lang.Math.max(0, ConfigManager.getGeneralConfig().maxLevel);
        return cumulativeXpForLevel(max);
    }

    // ---------- Scaling helpers ----------

    public static long getScaled(int min, int max, int level) {
        int maxLevel = ConfigManager.getGeneralConfig().maxLevel;
        double t = Math.max(0.0, Math.min(1.0, (double) level / (double) maxLevel));
        return Math.round(Math.lerp(min, max, t));
    }

    public static double getScaled(double min, double max, int level) {
        int maxLevel = ConfigManager.getGeneralConfig().maxLevel;
        double t = Math.max(0.0, Math.min(1.0, (double) level / (double) maxLevel));
        return Math.lerp(min, max, t);
    }

    public static float getScaled(float min, float max, int level) {
        int maxLevel = ConfigManager.getGeneralConfig().maxLevel;
        float t = Math.max(0.0f, Math.min(1.0f, (float) level / (float) maxLevel));
        return Math.lerp(min, max, t);
    }

    public static float getScaledPercentage(float min, float max, int level) {
        return getScaled(min, max, level) / 100.0f;
    }

    public static long getScaledTicks(int min, int max, int level) {
        return getScaled(min, max, level) * 20L;
    }
}
