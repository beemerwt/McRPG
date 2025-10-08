package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.data.CropMarkers;
import com.github.beemerwt.mcrpg.data.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.xp.Leveling;
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
        var cfg = ConfigManager.whichSkillHasBlock(id).orElse(null);
        if (cfg == null) return;

        if (cfg instanceof HerbalismConfig herbCfg) {
            var data = McRPG.getStore().get(placer.getUuid());
            if (data == null) {
                McRPG.getLogger().warning("Could not find McRPG data for player {} when placing block {}",
                        placer.getName().getString(), id);
                return;
            }

            var level = Leveling.levelFromTotalXp(data.xp.get(SkillType.HERBALISM));
            var growthModifier = Leveling.getScaled(herbCfg.greenThumb.baseGrowthMultiplier,
                    herbCfg.greenThumb.maxGrowthMultiplier, level);
            CropMarkers.get(sw).mark(sw, pos, growthModifier);
            return;
        }

        PlacedBlockTracker.get(sw).mark(sw, pos);
    }
}
