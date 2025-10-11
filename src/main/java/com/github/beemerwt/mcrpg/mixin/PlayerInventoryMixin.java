package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.callback.PlayerEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Shadow
    public int selectedSlot;

    @Shadow @Final
    public PlayerEntity player;

    @Inject(method = "setSelectedSlot", at = @At("TAIL"))
    private void mcrpg$onSelectSlot(int slot, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        McRPG.getLogger().debug("Player {} changed selected hotbar slot to {}",
                sp.getName().getString(), slot);

        PlayerEvents.EQUIP_ITEM.invoker().onEquipItem(
                sp, EquipmentSlot.MAINHAND,
                ItemStack.EMPTY, // old unknown here; your listener can ignore
                player.getMainHandStack()
        );
    }

    @Inject(method = "setSelectedStack", at = @At("TAIL"))
    private void mcrpg$onSetSelectedStack(ItemStack stack, CallbackInfoReturnable<ItemStack> ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        McRPG.getLogger().debug("Player {} set selected hotbar stack to {}",
                sp.getName().getString(), stack);

        PlayerEvents.EQUIP_ITEM.invoker().onEquipItem(
                sp, EquipmentSlot.MAINHAND,
                ItemStack.EMPTY, // old unknown here; your listener can ignore
                player.getMainHandStack()
        );
    }
}
