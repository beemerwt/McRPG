package com.github.beemerwt.mcrpg;

import com.github.beemerwt.mcrpg.command.AdminCommand;
import com.github.beemerwt.mcrpg.command.SkillCommand;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.data.BinaryPlayerStore;
import com.github.beemerwt.mcrpg.data.PlayerStore;
import com.github.beemerwt.mcrpg.events.AbilityEvents;
import com.github.beemerwt.mcrpg.events.BlockEvents;
import com.github.beemerwt.mcrpg.events.CombatEvents;
import com.github.beemerwt.mcrpg.events.MovementEvents;
import com.github.beemerwt.mcrpg.managers.AbilityManager;
import com.github.beemerwt.mcrpg.ui.HealthbarHover;
import com.github.beemerwt.mcrpg.ui.XpBossbarManager;
import com.github.beemerwt.mcrpg.util.FabricLogger;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class McRPG implements DedicatedServerModInitializer {
    private static final FabricLogger LOG = FabricLogger.getLogger("mcMMO-Fabric");
    public static FabricLogger getLogger() { return LOG; }

    private static MinecraftServer server;
    private static PlayerStore store;

    private long lastSave = 0;

    @Override
    public void onInitializeServer() {
        LOG.info("Initializing");
        ConfigManager.init();            // loads defaults + overrides
        AbilityManager.init();

        store = new BinaryPlayerStore();
        BlockEvents.register();
        AbilityEvents.register();
        CombatEvents.register();
        MovementEvents.register();

        SkillCommand.register(store);
        AdminCommand.register(store);

        XpBossbarManager.init(); // <--- add this line
        HealthbarHover.init();

        lastSave = System.currentTimeMillis();
        ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> {
            long now = System.currentTimeMillis();
            if (now - lastSave > ConfigManager.getGeneralConfig().autoSaveEverySeconds * 1000L) {
                try {
                    LOG.info("Auto-saving players...");
                    store.saveAll();
                } catch (Exception e) {
                    LOG.error(e, "Error during autosave");
                }
                lastSave = now;
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> {
            try {
                LOG.info("Shutting down; saving players...");
                store.saveAll();
                if (store instanceof AutoCloseable ac) ac.close();
            } catch (Exception e) {
                LOG.error(e, "Error during shutdown save");
            }
        });

        LOG.info("Initialized");
    }

    public static PlayerStore getStore() { return store; }
    public static MinecraftServer getServer() { return server; }
}
