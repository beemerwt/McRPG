package com.github.beemerwt.mcrpg.util;

import net.minecraft.screen.ScreenHandler;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class HandlerOwnerLedger {
    private static final Map<ScreenHandler, UUID> MAP = new WeakHashMap<>();

    private HandlerOwnerLedger() {}

    public static void setOwner(ScreenHandler handler, UUID playerId) {
        if (handler != null && playerId != null) MAP.put(handler, playerId);
    }

    public static UUID getOwner(ScreenHandler handler) {
        return MAP.get(handler);
    }

    public static void clear(ScreenHandler handler) {
        MAP.remove(handler);
    }
}

