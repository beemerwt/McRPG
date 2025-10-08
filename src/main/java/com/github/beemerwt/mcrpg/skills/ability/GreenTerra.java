package com.github.beemerwt.mcrpg.skills.ability;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.util.Growth;
import com.github.beemerwt.mcrpg.xp.Leveling;
import net.minecraft.block.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GreenTerra {
    private record ReplantCandidate(BlockPos pos, BlockState seedling) { }

    private static final Map<UUID, ArrayDeque<ReplantCandidate>> QUEUE = new ConcurrentHashMap<>();

    private GreenTerra() { }

    public static void activateFor(ServerPlayerEntity player, int level) {
        QUEUE.put(player.getUuid(), new ArrayDeque<>());
    }

    public static void deactivateFor(ServerPlayerEntity player) {
        var q = QUEUE.remove(player.getUuid());
        if (q == null || q.isEmpty()) return;

        HerbalismConfig cfg = ConfigManager.getSkillConfig(SkillType.HERBALISM);
        long totalXp = McRPG.getStore().get(player.getUuid()).xp.get(SkillType.HERBALISM);
        int lvl = Leveling.levelFromTotalXp(totalXp);

        double chance = Leveling.getScaledPercentage(
                cfg.greenTerra.baseReplantChance,
                cfg.greenTerra.maxReplantChance,
                lvl
        );

        var rng = player.getRandom();
        var world = player.getEntityWorld();
        var cm = world.getChunkManager();

        int attempts = q.size();
        int replanted = 0;

        while (!q.isEmpty()) {
            var c = q.pollFirst();
            var pos = c.pos();

            if (!cm.isChunkLoaded(pos.getX(), pos.getZ())) continue;
            if (rng.nextDouble() > chance) continue;

            var cur = world.getBlockState(pos);
            if (!cur.isAir() && !cur.isReplaceable()) continue;

            if (!canPlaceSeedling(world, pos, c.seedling())) continue;

            if (world.setBlockState(pos, c.seedling(), Block.NOTIFY_ALL)) {
                replanted++;
            }
        }

        if (replanted > 0) {
            McRPG.getLogger().debug(
                    "Green Terra replanted {}/{} crops for {}",
                    replanted, attempts, player.getName().getString()
            );
        }
    }

    /**
     * Called from Herbalism.onCropBroken (only if fully grown).
     */
    public static void considerReplant(ServerPlayerEntity player, BlockPos pos, BlockState brokenState) {
        var q = QUEUE.get(player.getUuid());
        if (q == null) return; // not active

        var world = player.getEntityWorld();

        // Try each replanter in order; first match wins.
        for (var r : REPLANTERS) {
            var seed = r.seedling(world, pos, brokenState);
            if (seed.isPresent()) {
                q.add(new ReplantCandidate(pos.toImmutable(), seed.get()));
                return;
            }
        }
    }

    // ------------------------------------------------------------------------
    // Replanter pipeline
    // ------------------------------------------------------------------------

    private interface Replanter {
        Optional<BlockState> seedling(ServerWorld world, BlockPos pos, BlockState brokenState);
    }

    private static final List<Replanter> REPLANTERS = List.of(
            // 1) Vanilla farmland crops: wheat, carrots, potatoes, beetroot, torchflower crop, etc.
            (world, pos, state) -> {
                if (!(state.getBlock() instanceof CropBlock)) return Optional.empty();
                if (!isFarmland(world, pos.down())) return Optional.empty();

                IntProperty age = Growth.getAgeProperty(state);
                if (age == null) return Optional.empty();

                int minAge = age.getValues().stream().min(Integer::compare).orElse(0);
                return Optional.of(state.with(age, minAge));
            },

            // 2) Nether wart on soul sand
            (world, pos, state) -> {
                if (!(state.getBlock() instanceof NetherWartBlock)) return Optional.empty();
                if (!isSoulSand(world, pos.down())) return Optional.empty();
                return Optional.of(state.with(NetherWartBlock.AGE, 0));
            },

            // 3) Cocoa: keep facing, reset age; requires jungle log behind the pod
            (world, pos, state) -> {
                if (!(state.getBlock() instanceof CocoaBlock)) return Optional.empty();

                EnumProperty<Direction> facing = CocoaBlock.FACING;
                IntProperty age = CocoaBlock.AGE;
                if (!state.contains(facing) || !state.contains(age)) return Optional.empty();

                Direction face = state.get(facing);
                BlockPos support = pos.offset(face.getOpposite());
                if (!isJungleLog(world, support)) return Optional.empty();

                return Optional.of(state.with(age, 0));
            },

            // 4) Sweet berry bush: reset age; requires sturdy ground below
            (world, pos, state) -> {
                if (!(state.getBlock() instanceof SweetBerryBushBlock)) return Optional.empty();
                IntProperty age = SweetBerryBushBlock.AGE;
                if (!state.contains(age)) return Optional.empty();
                if (!isSolidBelow(world, pos)) return Optional.empty();

                return Optional.of(state.with(age, 0));
            },

            // 5) Generic AGE fallback for other plant-like blocks (mods, etc.)
            (world, pos, state) -> {
                if (!looksPlantLike(state.getBlock())) return Optional.empty();

                IntProperty age = Growth.getAgeProperty(state);
                if (age == null) return Optional.empty();

                // Only replant if below block is at least sturdy; customize as needed.
                if (!isSolidBelow(world, pos)) return Optional.empty();

                int minAge = age.getValues().stream().min(Integer::compare).orElse(0);
                return Optional.of(state.with(age, minAge));
            }
    );

    // ------------------------------------------------------------------------
    // Placement sanity checks
    // ------------------------------------------------------------------------
    private static boolean canPlaceSeedling(ServerWorld world, BlockPos pos, BlockState seedling) {
        Block b = seedling.getBlock();

        if (b instanceof CropBlock)
            return isFarmland(world, pos.down());

        if (b instanceof NetherWartBlock)
            return isSoulSand(world, pos.down());

        if (b instanceof CocoaBlock) {
            EnumProperty<Direction> facing = CocoaBlock.FACING;
            if (!seedling.contains(facing)) return false;
            Direction face = seedling.get(facing);
            BlockPos support = pos.offset(face.getOpposite());
            return isJungleLog(world, support);
        }

        // Fallback: require sturdy block below
        return isSolidBelow(world, pos);
    }

    // ------------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------------

    private static boolean isFarmland(ServerWorld w, BlockPos pos) {
        return w.getBlockState(pos).getBlock() instanceof FarmlandBlock;
    }

    private static boolean isSoulSand(ServerWorld w, BlockPos pos) {
        return w.getBlockState(pos).getBlock() instanceof SoulSandBlock;
    }

    private static boolean isJungleLog(ServerWorld w, BlockPos pos) {
        Block b = w.getBlockState(pos).getBlock();
        return b == Blocks.JUNGLE_LOG || b == Blocks.STRIPPED_JUNGLE_LOG;
    }

    private static boolean isSolidBelow(ServerWorld w, BlockPos pos) {
        BlockPos below = pos.down();
        return w.getBlockState(below).isSolidBlock(w, below);
    }

    private static boolean looksPlantLike(Block b) {
        return b instanceof PlantBlock || b instanceof CocoaBlock;
    }
}
