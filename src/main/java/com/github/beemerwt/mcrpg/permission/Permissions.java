package com.github.beemerwt.mcrpg.permission;

import net.minecraft.server.command.ServerCommandSource;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Optional permissions hook for McRPG.
 * <p>
 * - If a permissions mod (like LuckPerms-Fabric) is installed,
 *   this will delegate to its API automatically.
 * - Otherwise, falls back to vanilla OP level checks.
 */
public final class Permissions {
    private static final boolean HAS_FABRIC_PERMS;
    private static final Method CHECK_METHOD; // boolean Permissions.check(ServerCommandSource, String, int)

    static {
        Method m = null;
        boolean present = false;
        try {
            Class<?> clazz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            m = clazz.getMethod("check", ServerCommandSource.class, String.class, int.class);
            present = true;
        } catch (Throwable ignored) {
            // No permissions mod detected; fallback only.
        }
        CHECK_METHOD = m;
        HAS_FABRIC_PERMS = present;
    }

    private Permissions() {}

    /**
     * Checks if the given command source has a permission node.
     *
     * @param src             command source (usually player or console)
     * @param node            permission string (e.g. "mcrpg.admin")
     * @param opLevelDefault  fallback OP level if no perms mod
     * @return true if allowed
     */
    public static boolean has(ServerCommandSource src, String node, int opLevelDefault) {
        if (src == null) return false;
        if (HAS_FABRIC_PERMS) {
            try {
                return (Boolean) CHECK_METHOD.invoke(null, src, node, opLevelDefault);
            } catch (Throwable ignored) {
                // fallback below
            }
        }
        return src.hasPermissionLevel(opLevelDefault);
    }

    /**
     * Brigadier-friendly predicate for use in `.requires(...)`.
     */
    public static Predicate<ServerCommandSource> require(String node, int opLevelDefault) {
        return src -> has(src, node, opLevelDefault);
    }
}
