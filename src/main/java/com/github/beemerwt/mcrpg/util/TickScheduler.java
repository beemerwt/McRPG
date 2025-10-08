package com.github.beemerwt.mcrpg.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TickScheduler {
    public static class Task {
        long ticksRemaining;
        Runnable action;

        Task(long ticks, Runnable action) {
            this.ticksRemaining = ticks;
            this.action = action;
        }

        public long getRemainingTicks() {
            return ticksRemaining;
        }

        public void cancel() {
            TickScheduler.cancel(this);
        }
    }

    private static final List<Task> tasks = new ArrayList<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(TickScheduler::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        Iterator<Task> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (--task.ticksRemaining <= 0) {
                try {
                    task.action.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                iterator.remove();
            }
        }
    }

    /** Schedule a Runnable to run after a number of ticks. */
    public static Task schedule(long ticks, Runnable action) {
        var task = new Task(ticks, action);
        tasks.add(task);
        return task;
    }

    public static void cancel(Task task) {
        tasks.remove(task);
    }
}

