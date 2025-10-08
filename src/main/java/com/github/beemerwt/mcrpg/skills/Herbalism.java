package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.skills.ability.DoubleDrops;
import com.github.beemerwt.mcrpg.skills.ability.GreenTerra;
import com.github.beemerwt.mcrpg.util.Growth;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.xp.Leveling;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.joml.Math;

import java.util.List;

public class Herbalism {

    // TODO: Two problems:
    // 1. It's not showing that the skill is readied when it is
    // 2. It's not triggering when I break a crop because it gets stopped by "skipping block"

    public static void onCropBroken(ServerPlayerEntity player, BlockPos pos,
                                    Block block, List<ItemStack> drops)
    {
        HerbalismConfig cfg = ConfigManager.getSkillConfig(SkillType.HERBALISM);
        var data = McRPG.getStore().get(player.getUuid());
        var blocks = cfg.getBlocks();

        long blockXp = Leveling.resolveBlockXp(blocks, block);
        if (blockXp <= 0) return;

        var state = player.getEntityWorld().getBlockState(pos);
        if (!Growth.isMature(state)) return; // Only award XP for fully grown crops

        // GREEN TERRA: queue a replant candidate while active
        GreenTerra.considerReplant(player, pos, state);

        var currentXp = data.xp.get(SkillType.HERBALISM);
        int level = Leveling.levelFromTotalXp(currentXp);

        var id = Registries.BLOCK.getId(block);

        // Only trigger skills if the player is using a hoe and the crop supports double drops
        var tool = player.getMainHandStack().getItem();
        if (ItemClassifier.isHoe(tool) && cfg.doubleDropCrops.get(id.toString()) != null)
            if (DoubleDrops.processTrigger(cfg.doubleDrops, level, player.getEntityWorld(), pos, block, drops))
                blockXp *= 2; // Double the XP awarded

        // Apply per-skill modifier
        double mod = cfg.xpModifier;
        long awarded = org.joml.Math.max(0, Math.round(blockXp * mod));
        if (awarded <= 0) return;

        McRPG.getLogger().debug("{} Herbalism XP awarded to {} for crop broken {}",
                awarded, player.getName(), block);
        Leveling.addXp(player, SkillType.HERBALISM, awarded);
    }
}
