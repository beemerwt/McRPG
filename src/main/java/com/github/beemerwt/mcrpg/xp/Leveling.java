package com.github.beemerwt.mcrpg.xp;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.GeneralConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
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

public final class Leveling {
    private Leveling() {}

    private static long xpForLevel(int level, GeneralConfig.XpCurve curve) {
        if ("linear".equalsIgnoreCase(curve.base)) return curve.linearPerLevel * level;
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

    // Resolve XP using ids and tags from the skill config.
    public static long resolveBlockXp(Map<String, Integer> blocks, Block block) {
        // 1) Direct block id
        var id = Registries.BLOCK.getId(block);

        Integer direct = blocks.get(id.toString());
        if (direct != null) return direct;

        // 2) Tags defined as keys starting with "#"
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

        // 3) No match
        return 0;
    }

    public static void addXp(ServerPlayerEntity player, SkillType skill, long amount) {
        // Lookup player
        var data = McRPG.getStore().get(player.getUuid());
        if (data == null) {
            McRPG.getLogger().warning("Tried to add XP to player with no data: {}", player.getName().getString());
            return;
        }

        long cur = data.xp.getOrDefault(skill, 0L);
        long newTotal = cur + amount;
        data.xp.put(skill, newTotal);
        XpBossbarManager.showSkillXp(player, skill, amount, newTotal);
    }

    /**
     * Scales a value linearly between {@code min} and {@code max} based on player level.
     *
     * @param min   minimum value (at level 0)
     * @param max   maximum value (at max level)
     * @param level current level
     * @return scaled long value
     */
    public static long getScaled(int min, int max, int level) {
        int maxLevel = ConfigManager.getGeneralConfig().maxLevel;
        double t = Math.max(0.0, Math.min(1.0, (double) level / (double) maxLevel));
        return Math.round(Math.lerp(min, max, t));
    }

    /**
     * Scales a value linearly between {@code min} and {@code max} based on player level.
     *
     * @param min   minimum value (at level 0)
     * @param max   maximum value (at max level)
     * @param level current level
     * @return scaled double value
     */
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

    /**
     * Scales a percentage linearly between {@code min} and {@code max} based on player level.
     * @param min A minimum value percentage (0 - 100) at level 0
     * @param max A maximum value percentage (0 - 100) at max level
     * @param level current level
     * @return scaled percentage (0.0 to 1.0)
     */
    public static double getScaledPercentage(double min, double max, int level) {
        return getScaled(min, max, level) / 100.0;
    }

    public static long getScaledTicks(int min, int max, int level) {
        return getScaled(min, max, level) * 20L;
    }
}
