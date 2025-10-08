package com.github.beemerwt.mcrpg.config.ability;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.AbilityConfig;

@JanksonObject
public class GreenThumbConfig extends AbilityConfig {
    @JankComment("Growth speed multiplier, scaling with skill level")
    public double baseGrowthMultiplier = 1.0;
    public double maxGrowthMultiplier = 4.0;

}
