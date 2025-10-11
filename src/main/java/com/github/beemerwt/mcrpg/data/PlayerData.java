package com.github.beemerwt.mcrpg.data;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerData {
    public final String name;
    public final UUID uuid;
    public final Map<SkillType, Long> xp = new EnumMap<>(SkillType.class);

    public PlayerData(String name, UUID id) {
        this.name = Objects.requireNonNull(name, "PlayerData name");
        this.uuid = Objects.requireNonNull(id, "PlayerData uuid");
        for (SkillType s : SkillType.values()) {
            xp.put(s, 0L);
        }
    }
}

