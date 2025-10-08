package com.github.beemerwt.mcrpg.data;

public enum SkillType {
    // General skills
    MINING((short) 0),
    WOODCUTTING((short) 1),
    EXCAVATION((short) 2),
    ACROBATICS((short) 3),
    HERBALISM((short) 8),

    // Combat-specific
    SWORDS((short) 4),
    ARCHERY((short) 5),
    AXES((short) 6),
    UNARMED((short) 7);

    private final short id;
    SkillType(short id) {
        this.id = id;
    }

    public static SkillType fromId(short id) {
        for (SkillType st : values()) {
            if (st.id == id) return st;
        }

        throw new IllegalArgumentException("No SkillType with id " + id);
    }

    public short id() { return id; }
}
