package com.github.beemerwt.mcrpg.command;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.data.PlayerData;
import com.github.beemerwt.mcrpg.data.PlayerStore;
import com.github.beemerwt.mcrpg.permission.Permissions;
import com.github.beemerwt.mcrpg.managers.AbilityManager;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.ui.XpBossbarManager;
import com.github.beemerwt.mcrpg.util.Leveling;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AdminCommand {
    private AdminCommand() {}

    public static void register(PlayerStore store) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(literal("mcrpg")
                    .requires(Permissions.require("mcrpg.admin", 2))

                    // /mcrpg saveall
                    .then(literal("saveall").executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> Text.literal("McRPG player data saved."), false);
                        return 1;
                    }))

                    // /mcrpg reload
                    .then(literal("reload").executes(ctx -> {
                        long start = System.nanoTime();
                        try {
                            ConfigManager.reloadAll();
                            long ms = (System.nanoTime() - start) / 1_000_000L;
                            ctx.getSource().sendFeedback(() -> Text.literal("McRPG configs reloaded (" + ms + " ms)."), true);
                            return 1;
                        } catch (Exception ex) {
                            throw new SimpleCommandExceptionType(Text.literal("Reload failed: " + ex.getMessage())).create();
                        }
                    }))

                    // ========= setlevel =========
                    // New form: /mcrpg setlevel <player> <skill> <level>
                    .then(literal("setlevel")
                            .then(argument("player", StringArgumentType.word()).suggests(PLAYER_SUGGESTIONS)
                                    .then(argument("skill", StringArgumentType.word()).suggests(SKILL_SUGGESTIONS)
                                            .then(argument("level", IntegerArgumentType.integer(0))
                                                    .executes(ctx -> {
                                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                        return handleSetLevel(ctx, store, target);
                                                    })
                                            )
                                    )
                            )
                    )

                    // ========= addxp =========
                    // New form: /mcrpg addxp <player> <skill> <xp>
                    .then(literal("addxp")
                            .then(argument("player", StringArgumentType.word()).suggests(PLAYER_SUGGESTIONS)
                                    .then(argument("skill", StringArgumentType.word()).suggests(SKILL_SUGGESTIONS)
                                            .then(argument("xp", LongArgumentType.longArg(1))
                                                    .executes(ctx -> {
                                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                        return handleAddXp(ctx, store, target);
                                                    })
                                            )
                                    )
                            )
                    )

                    // ========= subxp =========
                    // New form: /mcrpg subxp <player> <skill> <xp>
                    .then(literal("subxp")
                            .then(argument("player", StringArgumentType.word()).suggests(PLAYER_SUGGESTIONS)
                                    .then(argument("skill", StringArgumentType.word()).suggests(SKILL_SUGGESTIONS)
                                            .then(argument("xp", LongArgumentType.longArg(1))
                                                    .executes(ctx -> {
                                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                        return handleSubXp(ctx, store, target);
                                                    })
                                            )
                                    )
                            )
                    )

                    // /mcrpg resetcd  (self)
                    .then(literal("resetcd").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        AbilityManager.clearAllFor(player.getUuid());
                        ctx.getSource().sendFeedback(() -> Text.literal("Reset all ability cooldowns."), false);
                        return 1;
                    }))
            );
        });
    }

    /* ----------------- handlers ----------------- */

    // Snap to start of level; clamp to [0, maxLevel]
    private static int handleSetLevel(CommandContext<ServerCommandSource> ctx, PlayerStore store, ServerPlayerEntity target) throws CommandSyntaxException {
        SkillType skill = parseSkill(ctx, "skill");
        int requested = IntegerArgumentType.getInteger(ctx, "level");

        int maxLevel = Math.max(0, ConfigManager.getGeneralConfig().maxLevel);
        int newLevel = Math.min(Math.max(0, requested), maxLevel);

        long newTotal = Leveling.cumulativeXpForLevel(newLevel); // no % preservation
        PlayerData data = store.get(target);
        data.xp.put(skill, newTotal);

        XpBossbarManager.showSkillXp(target, skill, 0, newTotal, false);

        ctx.getSource().sendFeedback(
                () -> Text.literal("Set " + target.getName().getString() + " " + skill.name().toLowerCase(Locale.ROOT)
                        + " to level " + newLevel + " (0% into level)"),
                false
        );
        return 1;
    }

    // Clamp to [0, maxTotalXp()]
    private static int handleAddXp(CommandContext<ServerCommandSource> ctx, PlayerStore store, ServerPlayerEntity target) throws CommandSyntaxException {
        SkillType skill = parseSkill(ctx, "skill");
        long add = LongArgumentType.getLong(ctx, "xp");

        PlayerData data = store.get(target);
        long cur = data.xp.getOrDefault(skill, 0L);
        long capped = Math.min(Long.MAX_VALUE, cur + add); // avoid overflow
        long next = Math.min(capped, Leveling.maxTotalXp());
        next = Math.max(0L, next);

        data.xp.put(skill, next);
        XpBossbarManager.showSkillXp(target, skill, add, next, false);

        final long nextFinal = next;

        ctx.getSource().sendFeedback(
                () -> Text.literal("Added " + add + " XP to " + target.getName().getString() + " " + skill.name().toLowerCase(Locale.ROOT)
                        + " (total " + nextFinal + ")"),
                false
        );
        return 1;
    }

    // Clamp to [0, maxTotalXp()] (bottoming out at 0)
    private static int handleSubXp(CommandContext<ServerCommandSource> ctx, PlayerStore store, ServerPlayerEntity target) throws CommandSyntaxException {
        SkillType skill = parseSkill(ctx, "skill");
        long sub = LongArgumentType.getLong(ctx, "xp");

        PlayerData data = store.get(target);
        long cur = data.xp.getOrDefault(skill, 0L);

        long next = cur - sub;
        if (next < 0L) next = 0L;
        if (next > Leveling.maxTotalXp()) next = Leveling.maxTotalXp();

        data.xp.put(skill, next);
        XpBossbarManager.showSkillXp(target, skill, 0, next, false);

        final long nextFinal = next;

        ctx.getSource().sendFeedback(
                () -> Text.literal("Subtracted " + sub + " XP from " + target.getName().getString() + " " + skill.name().toLowerCase(Locale.ROOT)
                        + " (total " + nextFinal + ")"),
                false
        );
        return 1;
    }

    /* ----------------- helpers ----------------- */

    private static final SuggestionProvider<ServerCommandSource> SKILL_SUGGESTIONS =
            (ctx, b) -> {
                for (SkillType st : SkillType.values()) {
                    b.suggest(st.name().toLowerCase(Locale.ROOT));
                }
                return b.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> PLAYER_SUGGESTIONS =
            (ctx, b) -> {
                List<String> suggested = new ArrayList<>();
                for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                    suggested.add(p.getStringifiedName());
                }

                for (var pd : McRPG.getStore().all()) {
                    if (!pd.name.isEmpty() && !suggested.contains(pd.name))
                        suggested.add(pd.name);
                }

                for (String s : suggested)
                    b.suggest(s);

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
}
