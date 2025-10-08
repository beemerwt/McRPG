package com.github.beemerwt.mcrpg.skills.ability;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.skills.SkillType;
import com.github.beemerwt.mcrpg.xp.Leveling;
import net.minecraft.block.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GreenTerra {

    private GreenTerra() {}

    private static final Map<UUID, ArrayDeque<ReplantCandidate>> QUEUE = new ConcurrentHashMap<>();

    private record ReplantCandidate(BlockPos pos, BlockState seedling) {}

    public static void activateFor(ServerPlayerEntity player, int level) {
        // Start a fresh queue for this activation
        QUEUE.put(player.getUuid(), new ArrayDeque<>());
    }

    public static void deactivateFor(ServerPlayerEntity player) {
        var q = QUEUE.remove(player.getUuid());
        if (q == null || q.isEmpty()) return;

        HerbalismConfig cfg = ConfigManager.getSkillConfig(SkillType.HERBALISM);

        // Compute chance from config and player level
        long total = McRPG.getStore().get(player.getUuid()).xp.get(SkillType.HERBALISM);
        int lvl = Leveling.levelFromTotalXp(total);

        // Example scaling (adjust to your GreenTerraConfig fields):
        // chance = lerp(baseChance, maxChance, lvl/maxLevel)
        double chance = Leveling.getScaledPercentage(cfg.greenTerra.baseReplantChance,
                cfg.greenTerra.maxReplantChance, lvl);

        var rng = player.getRandom();

        int attempts = q.size();
        int replanted = 0;

        var world = player.getEntityWorld();
        var cm = world.getChunkManager();

        while (!q.isEmpty()) {
            var c = q.pollFirst();
            if (!cm.isChunkLoaded(c.pos().getX(), c.pos().getZ())) continue;
            if (rng.nextDouble() > chance) continue;

            // Only replant if spot is empty or replaceable and placement rules still hold
            var cur = world.getBlockState(c.pos());
            if (!cur.isAir() && !cur.isReplaceable()) continue;

            // Final validation: seedling is placeable (substrate checks)
            if (!canPlaceSeedling(world, c.pos(), c.seedling())) continue;

            boolean ok = world.setBlockState(c.pos(), c.seedling(), Block.NOTIFY_ALL);
            if (ok) replanted++;
        }

        if (replanted > 0) {
            McRPG.getLogger().debug("Green Terra replanted {}/{} crops for {}", replanted, attempts,
                    player.getName().getString());
        }
    }

    /** Called from Herbalism.onCropBroken (only if fully grown). */
    public static void considerReplant(ServerPlayerEntity player, BlockPos pos, BlockState brokenState) {
        var q = QUEUE.get(player.getUuid());
        if (q == null) return; // not active

        var world = player.getEntityWorld();

        // Build a seedling state for eligible crop types
        Optional<BlockState> seedling = toSeedlingState(brokenState, world, pos);
        seedling.ifPresent(s -> {
            McRPG.getLogger().debug("Queuing replant candidate at {} for {}", pos, player.getName().getString());
            q.add(new ReplantCandidate(pos.toImmutable(), s));
        });
    }

    // --- helpers ---

    private static Optional<BlockState> toSeedlingState(BlockState state, ServerWorld world, BlockPos pos) {
        Block block = state.getBlock();

        // Standard farmland crops (wheat, carrots, potatoes, beetroot, torchflower crop, etc.)
        if (block instanceof CropBlock) {
            IntProperty age = getAgeProperty(state);
            if (age == null) return Optional.empty();
            // require farmland substrate
            if (!isFarmland(world, pos.down())) return Optional.empty();
            McRPG.getLogger().debug("Green Terra: CropBlock {}, resetting age", block);
            return Optional.of(state.with(age, age.getValues().stream().min(Integer::compare).orElse(0)));
        }

        // Nether wart on soul sand
        if (block instanceof NetherWartBlock) {
            if (!isSoulSand(world, pos.down())) return Optional.empty();
            McRPG.getLogger().debug("Green Terra: NetherWartBlock, resetting age");
            return Optional.of(state.with(NetherWartBlock.AGE, 0));
        }

        // Cocoa: keep the facing, reset age
        if (block instanceof CocoaBlock) {
            EnumProperty<Direction> facing = CocoaBlock.FACING;
            IntProperty age = CocoaBlock.AGE;
            if (!state.contains(facing) || !state.contains(age)) return Optional.empty();
            // require jungle log behind the face
            var face = state.get(facing);
            BlockPos support = pos.offset(face.getOpposite());
            if (!isJungleLog(world, support)) return Optional.empty();
            McRPG.getLogger().debug("Green Terra: CocoaBlock, resetting age");
            return Optional.of(state.with(age, 0)); // keep facing, reset age
        }

        // Sweet berry bush: reset age (placement okay on grass/dirt/etc.)
        if (block instanceof SweetBerryBushBlock) {
            IntProperty age = SweetBerryBushBlock.AGE;
            if (!state.contains(age)) return Optional.empty();
            McRPG.getLogger().debug("Green Terra: SweetBerryBushBlock, resetting age");
            return Optional.of(state.with(age, 0));
        }

        // (Intentionally skip PitcherCropBlock: complex/tall placement; add later if desired)

        // Generic AGE_* fallback (only if substrate is valid and block is plant-like)
        IntProperty age = getAgeProperty(state);
        if (age != null && looksPlantLike(block)) {
            McRPG.getLogger().debug("Green Terra: Generic AGE_* property for {}, resetting age", block);
            return Optional.of(state.with(age, age.getValues().stream().min(Integer::compare).orElse(0)));
        }

        return Optional.empty();
    }

    private static boolean canPlaceSeedling(ServerWorld world, BlockPos pos, BlockState seedling) {
        // Basic placement validity; many plant blocks only check below block
        Block block = seedling.getBlock();

        if (block instanceof CropBlock) {
            return isFarmland(world, pos.down());
        }
        if (block instanceof NetherWartBlock) {
            return isSoulSand(world, pos.down());
        }
        if (block instanceof CocoaBlock) {
            EnumProperty<Direction> facing = CocoaBlock.FACING;
            var face = seedling.get(facing);
            BlockPos support = pos.offset(face.getOpposite());
            return isJungleLog(world, support);
        }
        if (block instanceof SweetBerryBushBlock) {
            // vanilla placement rules require sturdy ground; do a minimal check
            return world.getBlockState(pos.down()).isSolidBlock(world, pos.down());
        }

        // Fallback: allow if block at pos is air/replaceable and below is sturdy
        var below = world.getBlockState(pos.down());
        return below.isSolidBlock(world, pos.down());
    }

    private static boolean isFarmland(ServerWorld w, BlockPos pos) {
        return w.getBlockState(pos).getBlock() instanceof FarmlandBlock;
    }

    private static boolean isSoulSand(ServerWorld w, BlockPos pos) {
        return w.getBlockState(pos).getBlock() instanceof SoulSandBlock;
    }

    private static boolean isJungleLog(ServerWorld w, BlockPos pos) {
        Block b = w.getBlockState(pos).getBlock();
        // Broad match: any jungle log variant; adjust if you want stricter tags
        return b == Blocks.JUNGLE_LOG || b == Blocks.STRIPPED_JUNGLE_LOG;
    }

    private static boolean looksPlantLike(Block b) {
        return b instanceof PlantBlock || b instanceof CocoaBlock;
    }

    private static IntProperty getAgeProperty(BlockState s) {
        if (s.contains(Properties.AGE_7)) return Properties.AGE_7;
        if (s.contains(Properties.AGE_3)) return Properties.AGE_3;
        if (s.contains(Properties.AGE_2)) return Properties.AGE_2;
        if (s.contains(Properties.AGE_15)) return Properties.AGE_15;
        if (s.contains(Properties.AGE_25)) return Properties.AGE_25;
        return null;
    }
}
