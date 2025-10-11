package com.github.beemerwt.mcrpg.callback;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerEvents {

    public interface EquipItem {
        void onEquipItem(ServerPlayerEntity player, EquipmentSlot slot, ItemStack oldStack, ItemStack newStack);
    }

    public static final Event<PlayerEvents.EquipItem> EQUIP_ITEM = EventFactory.createArrayBacked(
            PlayerEvents.EquipItem.class,
            (listeners) -> (player, slot, oldStack, newStack) -> {
                for (PlayerEvents.EquipItem event : listeners) {
                    event.onEquipItem(player, slot, oldStack, newStack);
                }
            }
    );
}
