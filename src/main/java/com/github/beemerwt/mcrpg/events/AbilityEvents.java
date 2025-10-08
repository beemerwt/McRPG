package com.github.beemerwt.mcrpg.events;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ConfigManager;
import com.github.beemerwt.mcrpg.skills.Ability;
import com.github.beemerwt.mcrpg.skills.AbilityManager;
import com.github.beemerwt.mcrpg.skills.Woodcutting;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class AbilityEvents {

    public static void register() {
        UseBlockCallback.EVENT.register(AbilityEvents::onUseBlock);
        UseItemCallback.EVENT.register(AbilityEvents::onUseItem);
        AttackBlockCallback.EVENT.register(AbilityEvents::onStartBreak);
    }

    private static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (world.isClient()) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        if (!player.isSneaking()) return ActionResult.PASS;

        var handItem = player.getMainHandStack().getItem();
        if (ItemClassifier.isShovel(handItem) && AbilityManager.canActivate(sp, Ability.GIGA_DRILL_BREAKER))
        {
            AbilityManager.readyAbility(sp, Ability.GIGA_DRILL_BREAKER);
            return ActionResult.SUCCESS;
        }

        if (ItemClassifier.isPickaxe(handItem) && AbilityManager.canActivate(sp, Ability.SUPER_BREAKER))
        {
            AbilityManager.readyAbility(sp, Ability.SUPER_BREAKER);
            return ActionResult.SUCCESS;
        }

        if (ItemClassifier.isAxe(handItem) && AbilityManager.canActivate(sp, Ability.TREE_FELLER))
        {
            AbilityManager.readyAbility(sp, Ability.TREE_FELLER);
            return ActionResult.SUCCESS;
        }

        if (ItemClassifier.isHoe(handItem) && AbilityManager.canActivate(sp, Ability.GREEN_TERRA))
        {
            AbilityManager.readyAbility(sp, Ability.GREEN_TERRA);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (world.isClient()) return ActionResult.PASS;
        if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        if (!player.isSneaking()) return ActionResult.PASS;

        var handItem = player.getMainHandStack().getItem();

        if (ItemClassifier.isShovel(handItem) && AbilityManager.canActivate(sp, Ability.GIGA_DRILL_BREAKER))
        {
            AbilityManager.readyAbility(sp, Ability.GIGA_DRILL_BREAKER);
            return ActionResult.SUCCESS;
        }

        if (ItemClassifier.isPickaxe(handItem) && AbilityManager.canActivate(sp, Ability.SUPER_BREAKER))
        {
            AbilityManager.readyAbility(sp, Ability.SUPER_BREAKER);
            return ActionResult.SUCCESS;
        }

        if (ItemClassifier.isAxe(handItem) && AbilityManager.canActivate(sp, Ability.TREE_FELLER))
        {
            AbilityManager.readyAbility(sp, Ability.TREE_FELLER);
            return ActionResult.SUCCESS;
        }

        if (ItemClassifier.isHoe(handItem) && AbilityManager.canActivate(sp, Ability.GREEN_TERRA))
        {
            AbilityManager.readyAbility(sp, Ability.GREEN_TERRA);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    public static ActionResult onStartBreak(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
        if (player == null || player.isSpectator() || player.isCreative()) return ActionResult.PASS;
        if (world.isClient()) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

        handleReadiedAbility(player, sp, sw, pos);
        Woodcutting.onStartBreak(sp, sw, pos);
        return ActionResult.PASS;
    }

    private static void handleReadiedAbility(PlayerEntity player, ServerPlayerEntity sp,
                                                     ServerWorld sw, BlockPos pos)
    {
        var readied = AbilityManager.getReadiedAbility(sp);
        if (readied.isEmpty()) return;
        McRPG.getLogger().debug("AbilityEvents: onStartBreak: Player {} has readied ability {}",
                player.getName().getString(), readied.get().getDisplayName());

        var state = sw.getBlockState(pos);
        if (state == null) return;

        var block = state.getBlock();
        if (block == null) return;

        var blockName = Registries.BLOCK.getId(block).toString();
        var skillCfg = ConfigManager.whichSkillHasBlock(blockName).orElse(null);
        if (skillCfg == null) {
            AbilityManager.clearReadiedAbility(sp);
            McRPG.getLogger().debug("AbilityEvents: onStartBreak: No skill config for block {}", blockName);
            return;
        }

        Item tool = player.getMainHandStack().getItem();

        switch (readied.get()) {
            case Ability.SUPER_BREAKER -> {
                if (ItemClassifier.isPickaxe(tool) && AbilityManager.canActivate(sp, Ability.SUPER_BREAKER))
                    AbilityManager.activate(sp, Ability.SUPER_BREAKER);
            }
            case Ability.GIGA_DRILL_BREAKER -> {
                if (ItemClassifier.isShovel(tool) && AbilityManager.canActivate(sp, Ability.GIGA_DRILL_BREAKER))
                    AbilityManager.activate(sp, Ability.GIGA_DRILL_BREAKER);
            }
            case Ability.TREE_FELLER -> {
                if (ItemClassifier.isAxe(tool) && AbilityManager.canActivate(sp, Ability.TREE_FELLER))
                    AbilityManager.activate(sp, Ability.TREE_FELLER);
            }
            case Ability.GREEN_TERRA -> {
                if (ItemClassifier.isHoe(tool) && AbilityManager.canActivate(sp, Ability.GREEN_TERRA))
                    AbilityManager.activate(sp, Ability.GREEN_TERRA);
            }
        }
    }
}
