package com.github.beemerwt.mcrpg.ui;

import com.github.beemerwt.mcrpg.config.ConfigManager;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.skills.SkillType;
import com.github.beemerwt.mcrpg.text.NamedTextColor;
import com.github.beemerwt.mcrpg.xp.Leveling;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.*;

public final class XpBossbarManager {
    private static final int DISPLAY_TICKS = 60; // ~3 seconds at 20 tps

    // Per-player -> per-skill bossbar
    private static final Map<UUID, EnumMap<SkillType, ServerBossBar>> bars = new HashMap<>();
    // Time-to-live for each bar instance
    private static final Map<ServerBossBar, Integer> ttl = new HashMap<>();

    private XpBossbarManager() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(XpBossbarManager::onEndTick);

        // Ensure we clean up bars when a player leaves (sends REMOVE packets automatically)
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity sp = handler.player;
            EnumMap<SkillType, ServerBossBar> map = bars.remove(sp.getUuid());
            if (map != null) {
                for (ServerBossBar bar : map.values()) {
                    bar.clearPlayers(); // sends BossBar remove packets
                    ttl.remove(bar);
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((disp, reg, env) -> {
            disp.register(CommandManager.literal("mcrpg_testbar")
                    .executes(ctx -> {
                        ServerPlayerEntity sp = ctx.getSource().getPlayer();
                        XpBossbarManager.showSkillXp(sp, SkillType.MINING, 25, 500);
                        return 1;
                    }));
        });
    }

    private static void onEndTick(MinecraftServer server) {
        if (ttl.isEmpty()) return;

        List<ServerBossBar> toRemove = new ArrayList<>();
        ttl.replaceAll((bar, t) -> t - 1);
        for (var e : ttl.entrySet()) if (e.getValue() <= 0) toRemove.add(e.getKey());
        if (toRemove.isEmpty()) return;

        for (ServerBossBar bar : toRemove) {
            bar.clearPlayers(); // sends remove packets to any attached players
            ttl.remove(bar);
            bars.values().forEach(m -> m.values().removeIf(b -> b == bar));
        }
    }

    public static void showSkillXp(ServerPlayerEntity sp, SkillType skill, long justAdded, long newTotalXp, boolean playSound) {
        SkillConfig skillCfg = ConfigManager.getSkillConfig(skill);

        // Compute level, progress within level, and next requirement
        int beforeLevel = Leveling.levelFromTotalXp(Math.max(0, newTotalXp - justAdded));
        int level = Leveling.levelFromTotalXp(newTotalXp);
        long xpAtLevelStart = cumulativeXpForLevel(level);
        long intoLevel = Math.max(0, newTotalXp - xpAtLevelStart);
        long nextNeed = Leveling.xpForLevel(Math.max(1, level + 1));
        float progress = nextNeed == 0 ? 0f : Math.min(1f, (float) intoLevel / (float) nextNeed);

        if (beforeLevel < level && playSound) {
            // Milestone: every 100 levels, also play the toast "challenge complete" sound
            if (level % 100 == 0)
                sp.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            else
                sp.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
        }

        // Title: "Mining Lv.5"
        String niceName = skill.name().charAt(0) + skill.name().substring(1).toLowerCase(Locale.ROOT);
        Text title = Text.literal(niceName + " Lv.").append(
                Text.literal(String.valueOf(level)).withColor(NamedTextColor.GOLD.value()));

        ServerBossBar bar = bars
                .computeIfAbsent(sp.getUuid(), id -> new EnumMap<>(SkillType.class))
                .computeIfAbsent(skill, s -> new ServerBossBar(title, parseColor(skillCfg.bossbarColor), BossBar.Style.PROGRESS));

        // Ensure the client is actually "watching" this bar (sends ADD if needed)
        bar.addPlayer(sp);

        // Refresh properties (each setter sends its respective UPDATE packet)
        bar.setName(title);
        bar.setColor(parseColor(skillCfg.bossbarColor));
        bar.setPercent(progress);

        // Keep it alive while XP is flowing
        ttl.put(bar, DISPLAY_TICKS);
    }

    public static void showSkillXp(ServerPlayerEntity sp, SkillType skill, long justAdded, long newTotalXp) {
        showSkillXp(sp, skill, justAdded, newTotalXp, true);
    }

    private static BossBar.Color parseColor(String s) {
        if (s == null) return BossBar.Color.BLUE;
        try { return BossBar.Color.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { return BossBar.Color.BLUE; }
    }

    private static long cumulativeXpForLevel(int level) {
        // Total XP required to have *reached* this level (i.e., start of the level).
        // Level 0 -> 0; Level 1 -> xpForLevel(1); Level 2 -> xpForLevel(1)+xpForLevel(2); etc.
        long total = 0;
        for (int i = 1; i <= level; i++)
            total += Leveling.xpForLevel(i);
        return total;
    }
}
