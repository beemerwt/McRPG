package com.github.beemerwt.mcrpg.command;

import com.github.beemerwt.mcrpg.xp.Leveling;
import com.github.beemerwt.mcrpg.data.PlayerStore;
import com.github.beemerwt.mcrpg.permission.Permissions;
import com.github.beemerwt.mcrpg.data.SkillType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.command.argument.EntityArgumentType.getPlayer;
import static net.minecraft.command.argument.EntityArgumentType.player;

public final class SkillCommand {
    private SkillCommand() {}

    /**
     * Registers the "/skills" command.
     * Usage:
     *   /skills                  -> view your skills
     *   /skills <player>         -> view another player's skills (perm: mcrpg.command.skills.others, OP 2)
     */
    public static void register(PlayerStore store) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(literal("skills")
                    .requires(Permissions.require("mcrpg.command.skills", 0)) // anyone by default
                    .executes(ctx -> {
                        ServerPlayerEntity self = ctx.getSource().getPlayer();
                        if (self == null) return 0;
                        showSkills(ctx.getSource(), store, self.getUuid(), self.getGameProfile().name());
                        return 1;
                    })
                    .then(argument("player", player())
                            .requires(Permissions.require("mcrpg.command.skills.others", 2)) // OP2 if no perms mod
                            .executes(ctx -> {
                                ServerPlayerEntity target = getPlayer(ctx, "player");
                                showSkills(ctx.getSource(), store, target.getUuid(), target.getGameProfile().name());
                                return 1;
                            })
                    )
            );
        });
    }

    // ------------------------- Implementation -------------------------

    private static void showSkills(ServerCommandSource src, PlayerStore store, UUID uuid, String name) {
        var data = store.get(uuid);

        // Header
        src.sendFeedback(() -> Text.literal("McRPG Skills for " + name)
                .formatted(Formatting.GOLD, Formatting.BOLD), false);

        // Column header
        src.sendFeedback(() -> Text.literal(String.format("%-12s %-6s %-14s %-10s",
                        "Skill", "Level", "XP in Level", "To Next"))
                .formatted(Formatting.GRAY), false);

        // Separator
        src.sendFeedback(() -> Text.literal("--------------------------------------------------")
                .formatted(Formatting.DARK_GRAY), false);

        // Sort skills alphabetically for readability
        Arrays.stream(SkillType.values())
                .sorted(Comparator.comparing(Enum::name))
                .forEach(skill -> {
                    long totalXp = data.xp.getOrDefault(skill, 0L);
                    Progress p = progress(totalXp);

                    // Line per skill
                    MutableText line = Text.literal(String.format("%-12s ", title(skill)))
                            .formatted(Formatting.YELLOW)
                            .append(Text.literal(String.format("%-6d ", p.level)).formatted(Formatting.AQUA))
                            .append(Text.literal(String.format("%-14s ", p.inLevel + "/" + p.needForNext)).formatted(Formatting.WHITE))
                            .append(Text.literal(String.format("%-10d", p.needForNext - p.inLevel)).formatted(Formatting.GREEN));

                    src.sendFeedback(() -> line, false);
                });
    }

    private static String title(SkillType s) {
        // Pretty names
        String n = s.name().toLowerCase();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /**
     * Compute level and in-level progress from a total XP using the global curve.
     */
    private static Progress progress(long totalXp) {
        int lvl = Leveling.levelFromTotalXp(totalXp);
        long needThis = Leveling.xpForLevel(lvl + 1);
        long spentBefore = sumNeededUpTo(lvl);
        long inLevel = Math.max(0, totalXp - spentBefore);
        // guard against odd curves
        if (inLevel > needThis) inLevel = needThis;
        return new Progress(lvl, inLevel, needThis);
    }

    private static long sumNeededUpTo(int level) {
        long sum = 0;
        for (int i = 1; i <= level; i++)
            sum += Leveling.xpForLevel(i);
        return sum;
    }

    private record Progress(int level, long inLevel, long needForNext) {}
}
