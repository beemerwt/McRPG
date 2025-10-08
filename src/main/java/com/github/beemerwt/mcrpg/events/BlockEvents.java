package com.github.beemerwt.mcrpg.events;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ConfigManager;
import com.github.beemerwt.mcrpg.data.CropMarkers;
import com.github.beemerwt.mcrpg.data.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.skills.Excavation;
import com.github.beemerwt.mcrpg.skills.Herbalism;
import com.github.beemerwt.mcrpg.skills.Mining;
import com.github.beemerwt.mcrpg.skills.Woodcutting;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.Random;

public final class BlockEvents {
    private static final Random RNG = new Random();

    private BlockEvents() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (world.isClient()) return;
            if (!(world instanceof ServerWorld sw)) return;
            if (!(player instanceof ServerPlayerEntity sp)) return;

            McRPG.getLogger().debug("Processing block break event at {} by player {}", pos, player.getName().getString());

            var block = state.getBlock();
            var blockName = Registries.BLOCK.getId(block).toString();

            var skillCfg = ConfigManager.whichSkillHasBlock(blockName).orElse(null);
            if (skillCfg == null) {
                McRPG.getLogger().debug("No skill associated with block " + blockName);
                return;
            }

            ItemStack tool = player.getMainHandStack();
            List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, entity, player, tool);

            // Handle crop markers before placed block check, since crops can be both player-placed and naturally spawned
            var marker = CropMarkers.get(sw);
            if (marker.isMarked(pos)) {
                marker.unmark(sw, pos);
                McRPG.getLogger().debug("Removing marked crop {}", blockName);
                Herbalism.onCropBroken(sp, pos, block, drops);
                return;
            }

            var tracker = PlacedBlockTracker.get(sw);
            if (tracker.isMarked(pos)) {
                tracker.unmark(sw, pos);
                McRPG.getLogger().debug("Skipping block {} because it was player-placed", blockName);
                return;
            }

            switch (skillCfg.getSkillType()) {
                case MINING -> Mining.onBlockMined(sp, pos, block, drops);
                case WOODCUTTING -> Woodcutting.onLogChopped(sp, pos, block, drops);
                case EXCAVATION -> Excavation.onBlockDug(sp, pos, block);
                case HERBALISM -> Herbalism.onCropBroken(sp, pos, block, drops);
            }
        });
    }
}
