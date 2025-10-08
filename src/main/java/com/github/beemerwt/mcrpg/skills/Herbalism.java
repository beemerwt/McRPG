package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.skills.ability.DoubleDrops;
import com.github.beemerwt.mcrpg.skills.ability.GreenTerra;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.xp.Leveling;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import org.joml.Math;

import java.util.List;

public class Herbalism {
    public static void onCropBroken(ServerPlayerEntity player, BlockPos pos,
                                    Block block, List<ItemStack> drops)
    {
        HerbalismConfig cfg = ConfigManager.getSkillConfig(SkillType.HERBALISM);
        var data = McRPG.getStore().get(player.getUuid());
        var blocks = cfg.getBlocks();

        long blockXp = Leveling.resolveBlockXp(blocks, block);
        if (blockXp <= 0) return;

        var state = player.getEntityWorld().getBlockState(pos);
        if (!isGrown(state)) return; // Only award XP for fully grown crops

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

    // Important: We can only grant double drops if the crop is fully grown
    public static boolean isGrown(BlockState state) {
        Block block = state.getBlock();

        // Sugarcane has an age property but no growth stages
        // if (block instanceof SugarCaneBlock) {
//            return true;
//        }

        // 1. Vanilla CropBlocks (wheat, carrots, potatoes, beetroot, etc.)
        if (block instanceof CropBlock crop) {
            return crop.isMature(state);
        }

        // 2. Nether Wart
        if (block instanceof NetherWartBlock) {
            // Nether wart uses "AGE_3" with max value 3
            return state.get(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }

        // 3. Cocoa
        if (block instanceof CocoaBlock) {
            // Cocoa AGE max = 2
            return state.get(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }

        // 4. Sweet Berry Bush / Cave Vines (with berries)
        if (block instanceof SweetBerryBushBlock) {
            return state.get(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE;
        }
        if (block instanceof CaveVinesBodyBlock || block instanceof CaveVinesHeadBlock) {
            return state.getOrEmpty(CaveVines.BERRIES).orElse(false);
        }

        // 5. Pitcher Plant / Torchflower (1.20+ crops)
        if (block instanceof PitcherCropBlock) {
            IntProperty age = PitcherCropBlock.AGE; // has a defined AGE property
            int max = age.getValues().stream().max(Integer::compare).orElse(0);
            return state.get(age) >= max;
        }

        // 6. Generic fallback for anything with an "age" property
        IntProperty age = getAgeProperty(state);
        if (age != null) {
            int max = age.getValues().stream().max(Integer::compare).orElse(0);
            return state.get(age) >= max;
        }

        // 7. For plants with no growth stages (like mushrooms, vines, grass, etc.)
        return true; // treat as always "grown"
    }

    /** Safely retrieves a generic age-like property if present. */
    private static IntProperty getAgeProperty(BlockState state) {
        if (state.contains(Properties.AGE_7)) return Properties.AGE_7;
        if (state.contains(Properties.AGE_3)) return Properties.AGE_3;
        if (state.contains(Properties.AGE_2)) return Properties.AGE_2;
        if (state.contains(Properties.AGE_15)) return Properties.AGE_15;
        if (state.contains(Properties.AGE_25)) return Properties.AGE_25;
        return null;
    }
}
