package com.github.beemerwt.mcrpg.skills;

public enum Ability {
    TREE_FELLER((short) 1, "Tree Feller"),
    GIGA_DRILL_BREAKER((short) 2, "Giga Drill Breaker"),
    SKULL_SPLITTER((short) 3, "Skull Splitter"),
    SERRATED_STRIKES((short) 4, "Serrated Strikes"),
    BERSERK((short) 5, "Berserk"),
    GREEN_TERRA((short) 6, "Green Terra"),
    SUPER_BREAKER((short) 7, "Super Breaker"),
    BLAST_MINING((short) 8, "Blast Mining");

    private final short id;
    private final String displayName;
    Ability(short id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static Ability fromId(short id) {
        for (var a : values()) {
            if (a.id == id) return a;
        }

        throw new IllegalArgumentException("No ability with id " + id);
    }

    public short id() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }
}
