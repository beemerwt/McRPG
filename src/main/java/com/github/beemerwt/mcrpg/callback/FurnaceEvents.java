package com.github.beemerwt.mcrpg.callback;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class FurnaceEvents {

    public interface FuelConsumed {
        void onFuelConsumed(BlockPos pos, AbstractFurnaceBlockEntity entity, ItemStack fuel);
    }

    public interface SmeltedItem {
        void onSmeltedItem(BlockPos pos, AbstractFurnaceBlockEntity entity, ItemStack input, ItemStack output);
    }

    /**
     * An event that is called whenever fuel is consumed by a furnace.
     * This is always called BEFORE the fuel is decremented, but AFTER the fuelTime has increased.
     */
    public static final Event<FurnaceEvents.FuelConsumed> FUEL_CONSUMED = EventFactory.createArrayBacked(
            FurnaceEvents.FuelConsumed.class,
            (listeners) -> (pos, entity, fuel) -> {
                for (FurnaceEvents.FuelConsumed event : listeners) {
                    event.onFuelConsumed(pos, entity, fuel);
                }
            }
    );

    /**
     * An event that is called whenever an item is smelted by a furnace.
     * This is always called after the item is smelted.
     */
    public static final Event<FurnaceEvents.SmeltedItem> ITEM_SMELTED = EventFactory.createArrayBacked(
            FurnaceEvents.SmeltedItem.class,
            (listeners) -> (pos, entity, input, output) -> {
                for (FurnaceEvents.SmeltedItem event : listeners) {
                    event.onSmeltedItem(pos, entity, input, output);
                }
            }
    );
}
