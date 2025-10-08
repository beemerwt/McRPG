package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.config.ConfigManager;
import com.github.beemerwt.mcrpg.data.PlacedBlockTracker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockOnPlacedMixin {
    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void onBlockPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (!(world instanceof ServerWorld sw)) return;
        // Only track placements that are relevant to a skill (reduces save size)
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        if (ConfigManager.whichSkillHasBlock(id).isPresent())
            PlacedBlockTracker.get(sw).mark(sw, pos);
    }
}
