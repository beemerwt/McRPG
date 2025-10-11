package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.callback.FurnaceEvents;
import com.github.beemerwt.mcrpg.extension.FuelTimeExtension;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Inject where one item is crafted/smelted and moved from input to output.
 * The exact method name can vary; in 1.21.x yarn there is usually a
 * method that performs the "doSmelt/quickRecipeTransfer" step inside tick.
 * If needed, adjust the target to the line right after it decrements the input and increments output.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin implements FuelTimeExtension {
    @Shadow
    int litTimeRemaining;

    /**
     * tick(ServerWorld, BlockPos, BlockState, AbstractFurnaceBlockEntity)
     * We target the INVOKE of craftRecipe(...), and run AFTER it returns. If that call returned true,
     * vanilla immediately calls setLastRecipe(recipeEntry) — we’re positioned right after that site.
     * If you want the locals (recipeEntry, input, etc.), keep CAPTURE_FAILHARD. If mappings differ,
     * temporarily switch to NO_CAPTURE and recompute from the BE inventory.
     */
    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;craftRecipe(Lnet/minecraft/registry/DynamicRegistryManager;Lnet/minecraft/recipe/RecipeEntry;Lnet/minecraft/recipe/input/SingleStackRecipeInput;Lnet/minecraft/util/collection/DefaultedList;I)Z",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.NO_CAPTURE
    )
    private static void mcrpg$afterSingleCraft(
            ServerWorld world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity be,
            CallbackInfo ci
    ) {
        FurnaceEvents.ITEM_SMELTED.invoker().onSmeltedItem(pos, be, be.getStack(0), be.getStack(2));
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;decrement(I)V",
                    // This decrement(I) in tick() is the one that consumes FUEL when starting a burn.
                    shift = At.Shift.BEFORE
            ),
            locals = LocalCapture.NO_CAPTURE
    )
    private static void mcrpg$beforeFuelConsumed(
            ServerWorld world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity be,
            CallbackInfo ci
    ) {
        ItemStack fuelStack = be.getStack(1); // inventory[1]
        FurnaceEvents.FUEL_CONSUMED.invoker().onFuelConsumed(pos, be, fuelStack);
    }

    @Override
    public void addFuelTime(int extra) {
        if (extra <= 0) return;
        long sum = (long) this.litTimeRemaining + (long) extra;
        if (sum < 0) sum = 0;                 // just in case of overflow negative
        if (sum > Integer.MAX_VALUE) sum = Integer.MAX_VALUE;
        this.litTimeRemaining = (int) sum;
    }

    @Override
    public int getFuelTime() {
        return litTimeRemaining;
    }
}

