package com.github.beemerwt.mcrpg.events;

import com.github.beemerwt.mcrpg.skills.Acrobatics;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;

public class MovementEvents {
    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((livingEntity, damageSource,
                                                        amount, v1, b) ->
        {
            if (!(livingEntity instanceof ServerPlayerEntity player)) return;
            if (!damageSource.isOf(DamageTypes.FALL)) return;
            Acrobatics.onFallDamage(player, amount);
        });
    }
}
