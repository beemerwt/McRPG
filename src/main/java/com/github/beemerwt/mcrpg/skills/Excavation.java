package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.ExcavationConfig;
import com.github.beemerwt.mcrpg.skills.ability.TreasureFinding;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.xp.Leveling;
import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.joml.Math;

public class Excavation {

    public static void onBlockDug(ServerPlayerEntity player, BlockPos pos, Block block)
    {
        ExcavationConfig cfg = ConfigManager.getSkillConfig(SkillType.EXCAVATION);
        var data = McRPG.getStore().get(player.getUuid());
        var blocks = cfg.getBlocks();

        long blockXp = Leveling.resolveBlockXp(blocks, block);
        if (blockXp <= 0) return;

        long currentXp = data.xp.get(SkillType.EXCAVATION);
        int level = Leveling.levelFromTotalXp(currentXp);

        // Only trigger skills if the player is using a pickaxe
        var tool = player.getMainHandStack().getItem();
        if (ItemClassifier.isShovel(tool)) {
            long extraXp = TreasureFinding.processTrigger(cfg.treasures, level, player.getEntityWorld(), pos, block);
            blockXp += extraXp;
        }

        // Apply per-skill modifier
        double mod = cfg.xpModifier;
        long awarded = Math.max(0L, Math.round(blockXp * mod));
        if (awarded <= 0) return;

        McRPG.getLogger().debug("{} Excavation XP awarded to {} for block broken {}",
                awarded, player.getName(), block);
        Leveling.addXp(player, SkillType.EXCAVATION, awarded);
    }
}
