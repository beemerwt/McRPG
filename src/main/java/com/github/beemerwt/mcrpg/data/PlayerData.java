package com.github.beemerwt.mcrpg.data;

import com.github.beemerwt.mcrpg.skills.SkillType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {
    public final UUID uuid;
    public final Map<SkillType, Long> xp = new EnumMap<>(SkillType.class);

    public PlayerData(UUID id) {
        uuid = id;
        for (SkillType s : SkillType.values()) {
            xp.put(s, 0L);
        }
    }
}

