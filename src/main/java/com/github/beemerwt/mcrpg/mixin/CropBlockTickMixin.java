package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.persistent.CropMarkers;
import com.github.beemerwt.mcrpg.mixin.access.CropBlockInvoker;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CropBlock.class)
public abstract class CropBlockTickMixin {

    @Unique
    private static final ThreadLocal<Boolean> MCRPG_IN_EXTRA = ThreadLocal.withInitial(() -> false);

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void mcrpg$boostGrowth(BlockState state,
                                   ServerWorld world,
                                   BlockPos pos,
                                   Random random,
                                   CallbackInfo ci) {
        // Skip if this invocation was triggered by our own extra ticks
        if (MCRPG_IN_EXTRA.get()) return;

        float m = CropMarkers.get(world).getMultiplier(pos);
        if (m <= 1.0f) return;

        int extra = (int) Math.floor(m - 1.0f);
        float frac = (m - 1.0f) - extra;

        MCRPG_IN_EXTRA.set(true);
        try {
            var inv = (CropBlockInvoker) (Object) this;
            for (int i = 0; i < extra; i++)
                inv.mcrpg$callRandomTick(state, world, pos, random);

            if (random.nextFloat() < frac)
                inv.mcrpg$callRandomTick(state, world, pos, random);

        } finally {
            MCRPG_IN_EXTRA.set(false);
        }
    }
}
