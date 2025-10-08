package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.MiningConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.skills.ability.DoubleDrops;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.xp.Leveling;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.joml.Math;

import java.util.List;

public class Mining {
    private Mining() {}

    /**
     * When a player mines a block, this should be called to handle mining XP and skills.
     * @param player The player who mined the block.
     * @param block The block that was mined.
     * @param vanillaDrops The drops that will be dropped by vanilla mechanics.
     *                     Will not change original behavior.
     */
    public static void onBlockMined(ServerPlayerEntity player, BlockPos pos,
                                    Block block, List<ItemStack> vanillaDrops)
    {
        MiningConfig cfg = ConfigManager.getSkillConfig(SkillType.MINING);
        var data = McRPG.getStore().get(player.getUuid());
        var blocks = cfg.getBlocks();

        long blockXp = Leveling.resolveBlockXp(blocks, block);
        if (blockXp <= 0) return;

        var currentXp = data.xp.get(SkillType.MINING);
        int level = Leveling.levelFromTotalXp(currentXp);

        // Only trigger skills if the player is using a pickaxe
        var tool = player.getMainHandStack().getItem();
        if (ItemClassifier.isPickaxe(tool) && BlockClassifier.isOre(block))
            // TODO: Implement whitelist/blacklist for blocks that can trigger double drops
            if (DoubleDrops.processTrigger(cfg.doubleDrops, level, player.getEntityWorld(), pos, block, vanillaDrops))
                blockXp *= 2; // Double the XP awarded

        // Apply per-skill modifier
        double mod = cfg.xpModifier;
        long awarded = Math.max(0, Math.round(blockXp * mod));
        if (awarded <= 0) return;

        McRPG.getLogger().debug("{} Mining XP awarded to {} for block broken {}",
                awarded, player.getName(), block);
        Leveling.addXp(player, SkillType.MINING, awarded);
    }
}
