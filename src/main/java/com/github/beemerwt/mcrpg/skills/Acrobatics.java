package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.AcrobaticsConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.xp.Leveling;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

public class Acrobatics {
    private Acrobatics() {}

    public static void onFallDamage(ServerPlayerEntity player, float damageTaken) {
        AcrobaticsConfig cfg = ConfigManager.getSkillConfig(SkillType.ACROBATICS);
        var xp = cfg.fallXp * damageTaken;

        Set<RegistryEntry<Enchantment>> enchantments = player.getEquippedStack(EquipmentSlot.FEET)
                .getEnchantments().getEnchantments();

        if (enchantments != null && enchantments.stream().anyMatch(e -> e.matches(
                    p -> p == Enchantments.FEATHER_FALLING)))
                xp *= (float) cfg.featherFallMultiplier;

        var gained = (int)(Math.floor(xp * cfg.xpModifier));
        McRPG.getLogger().debug("Awarding {} acrobatics XP to {} for {} fall damage.", gained,
                player.getName().getString(), damageTaken);
        Leveling.addXp(player, SkillType.ACROBATICS, gained);
    }
}
