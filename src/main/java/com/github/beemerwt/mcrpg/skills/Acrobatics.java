package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.AcrobaticsConfig;
import com.github.beemerwt.mcrpg.xp.Leveling;
import net.minecraft.server.network.ServerPlayerEntity;

public class Acrobatics {
    private Acrobatics() {}

    public static void onFallDamage(ServerPlayerEntity player, float damageTaken) {
        AcrobaticsConfig cfg = ConfigManager.getSkillConfig(SkillType.ACROBATICS);

        // TODO: Get feather falling on player's boots and add the modifier

        var gained = (int)(Math.floor(cfg.fallXp * damageTaken * cfg.xpModifier));
        McRPG.getLogger().debug("Awarding {} acrobatics XP to {} for {} fall damage.", gained,
                player.getName().getString(), damageTaken);
        Leveling.addXp(player, SkillType.ACROBATICS, gained);
    }
}
