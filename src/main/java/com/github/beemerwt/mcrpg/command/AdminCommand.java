package com.github.beemerwt.mcrpg.command;

import com.github.beemerwt.mcrpg.config.ConfigManager;
import com.github.beemerwt.mcrpg.data.PlayerData;
import com.github.beemerwt.mcrpg.data.PlayerStore;
import com.github.beemerwt.mcrpg.permission.Permissions;
import com.github.beemerwt.mcrpg.skills.AbilityManager;
import com.github.beemerwt.mcrpg.skills.SkillType;
import com.github.beemerwt.mcrpg.ui.XpBossbarManager;
import com.github.beemerwt.mcrpg.xp.Leveling;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AdminCommand {
    private AdminCommand() {}

    // Call this from your mod init with your PlayerStore instance
    public static void register(PlayerStore store) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(literal("mcrpg")
                    .requires(Permissions.require("mcrpg.admin", 2))

                    // /mcrpg saveall
                    .then(literal("saveall").executes(ctx -> {
                        // If you have an explicit save path in PlayerStore, call it here (not shown).
                        ctx.getSource().sendFeedback(() -> Text.literal("McRPG player data saved."), false);
                        return 1;
                    }))

                    .then(literal("reload").executes(ctx -> {
                        long start = System.nanoTime();
                        try {
                            // If yours is instance-based, use: McRPG.getConfigManager().reloadAll();
                            ConfigManager.reloadAll();

                            long ms = (System.nanoTime() - start) / 1_000_000L;
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("McRPG configs reloaded (" + ms + " ms)."),
                                    true // echo to ops console
                            );
                            return 1;
                        } catch (Exception ex) {
                            // Surface a concise error to the caller; full stack in log if you log elsewhere
                            throw new SimpleCommandExceptionType(
                                    Text.literal("Reload failed: " + ex.getMessage())
                            ).create();
                        }
                    }))

                    // /mcrpg setlevel <skill> <level>
                    .then(literal("setlevel")
                            .then(argument("skill", StringArgumentType.word())
                                    .suggests(SKILL_SUGGESTIONS)
                                    .then(argument("level", IntegerArgumentType.integer(0))
                                            .executes(ctx -> {
                                                ServerPlayerEntity sp = ctx.getSource().getPlayerOrThrow();
                                                SkillType skill = parseSkill(ctx, "skill");
                                                int newLevel = IntegerArgumentType.getInteger(ctx, "level");

                                                PlayerData data = store.get(sp.getUuid());
                                                long currentTotal = data.xp.getOrDefault(skill, 0L);

                                                int curLvl = Leveling.levelFromTotalXp(currentTotal);
                                                long curStart = cumulativeXpForLevel(curLvl);
                                                long into = Math.max(0, currentTotal - curStart);
                                                long need = Math.max(1, Leveling.xpForLevel(curLvl + 1));
                                                double pct = Math.min(1.0, (double) into / (double) need);

                                                long newStart = cumulativeXpForLevel(newLevel);
                                                long newNeed = Math.max(1, Leveling.xpForLevel(newLevel + 1));
                                                long newInto = Math.round(pct * newNeed);
                                                long newTotal = Math.max(0, newStart + newInto);

                                                data.xp.put(skill, newTotal);

                                                // Optional UI ping to show the new progress
                                                XpBossbarManager.showSkillXp(sp, skill, 0, newTotal, false);

                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal("Set " + skill.name().toLowerCase(Locale.ROOT)
                                                                + " to level " + newLevel + " (" + (int) Math.round(pct * 100) + "% into level)"),
                                                        false
                                                );
                                                return 1;
                                            })
                                    )
                            )
                    )

                    // /mcrpg addxp <skill> <xp>
                    .then(literal("addxp")
                            .then(argument("skill", StringArgumentType.word())
                                    .suggests(SKILL_SUGGESTIONS)
                                    .then(argument("xp", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                ServerPlayerEntity sp = ctx.getSource().getPlayerOrThrow();
                                                SkillType skill = parseSkill(ctx, "skill");
                                                int add = IntegerArgumentType.getInteger(ctx, "xp");

                                                PlayerData data = store.get(sp.getUuid());
                                                long cur = data.xp.getOrDefault(skill, 0L);
                                                long next = Math.max(0, cur + add);
                                                data.xp.put(skill, next);

                                                XpBossbarManager.showSkillXp(sp, skill, add, next, false);

                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal("Added " + add + " XP to " + skill.name().toLowerCase(Locale.ROOT)
                                                                + " (total " + next + ")"),
                                                        false
                                                );
                                                return 1;
                                            })
                                    )
                            )
                    )

                    // /mcrpg subxp <skill> <xp>
                    .then(literal("subxp")
                            .then(argument("skill", StringArgumentType.word())
                                    .suggests(SKILL_SUGGESTIONS)
                                    .then(argument("xp", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                ServerPlayerEntity sp = ctx.getSource().getPlayerOrThrow();
                                                SkillType skill = parseSkill(ctx, "skill");
                                                long sub = LongArgumentType.getLong(ctx, "xp");

                                                PlayerData data = store.get(sp.getUuid());
                                                long cur = data.xp.getOrDefault(skill, 0L);
                                                long next = Math.max(0, cur - sub);
                                                data.xp.put(skill, next);

                                                // Show bar with 0 justAdded so it updates percent without implying a gain
                                                XpBossbarManager.showSkillXp(sp, skill, 0, next, false);

                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal("Subtracted " + sub + " XP from " + skill.name().toLowerCase(Locale.ROOT)
                                                                + " (total " + next + ")"),
                                                        false
                                                );
                                                return 1;
                                            })
                                    )
                            )
                    )

                    .then(literal("resetcd")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayerOrThrow();
                            if (player != null) {
                                AbilityManager.clearAllFor(player.getUuid());
                                ctx.getSource().sendFeedback(() -> Text.literal("Reset all ability cooldowns."), false);
                            } else {
                                ctx.getSource().sendError(Text.literal("Player not found."));
                            }
                            return 1;
                        })
                    )
            );
        });
    }

    /* ----------------- helpers ----------------- */

    private static final SuggestionProvider<ServerCommandSource> SKILL_SUGGESTIONS =
            (CommandContext<ServerCommandSource> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) -> {
                for (SkillType st : SkillType.values()) {
                    b.suggest(st.name().toLowerCase(Locale.ROOT));
                }
                return b.buildFuture();
            };

    private static SkillType parseSkill(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, name);
        try {
            return SkillType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new SimpleCommandExceptionType(Text.literal("Unknown skill: " + raw)).create();
        }
    }

    // Mirror of your bossbar helper’s cumulative function
    private static long cumulativeXpForLevel(int level) {
        long total = 0;
        for (int i = 1; i <= level; i++)
            total += Leveling.xpForLevel(i);
        return total;
    }
}
