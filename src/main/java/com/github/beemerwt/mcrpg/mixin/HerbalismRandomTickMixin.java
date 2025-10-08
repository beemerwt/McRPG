package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.data.CropMarkers;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerWorld.class)
public abstract class HerbalismRandomTickMixin {

    @Redirect(
            method = "tickChunk(Lnet/minecraft/world/chunk/WorldChunk;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockState;randomTick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V"
            )
    )
    private void mcrpg$applyGreenThumbMultiplier(BlockState state,
                                                 ServerWorld world,
                                                 BlockPos pos,
                                                 Random random) {
        if (!(world instanceof ServerWorld sw)) return;
        state.randomTick(sw, pos, random);

        float multiplier = CropMarkers.get(sw).getMultiplier(pos);
        if (multiplier <= 1.0f) return;

        // Base number of extra ticks
        int extra = (int)Math.floor(multiplier - 1.0f);
        float fractional = (multiplier - 1.0f) - extra;

        if (world.getBlockState(pos) != state) return;
        McRPG.getLogger().debug("Applying Green Thumb multiplier {} at {},{}: running {} extra ticks + 1 with chance {}",
                multiplier, pos.getX(), pos.getY(), extra, fractional);

        // Run floor(mult - 1) extra ticks
        for (int i = 0; i < extra; i++) {
            BlockState cur = world.getBlockState(pos);
            if (cur != state) break;
            state.randomTick(sw, pos, random);
        }

        // With chance equal to fractional part, run one more tick
        if (random.nextFloat() < fractional) {
            BlockState cur = world.getBlockState(pos);
            if (cur == state)
                state.randomTick(sw, pos, random);
        }
    }
}
