package com.github.beemerwt.mcrpg.ui;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HealthbarHover {
    // How long to keep the bar visible after last hover (ticks)
    private static final int SHOW_TICKS = 12;
    private static final double MAX_DISTANCE = 8.0;

    // Track when we last showed a bar per-entity so we can hide it later
    private static final Map<UUID, Integer> entityExpiry = new HashMap<>();
    // Cache original names so we can restore
    private static final Map<UUID, Text> originalNames = new HashMap<>();

    // Heart glyphs via escapes to keep code ASCII-only
    private static final String HEART_FULL  = "♥";
    private static final String HEART_EMPTY = "♡";

    public static void init() {
        ServerTickEvents.START_SERVER_TICK.register(HealthbarHover::onTick);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof LivingEntity le) {
                HealthbarHover.clearBarNow(le); // restore original name & hide
            }

            return true; // do not cancel death
        });
    }

    private static void onTick(MinecraftServer server) {
        // 1) Update for players looking at something
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            var target = getLookEntity(p, MAX_DISTANCE);
            if (target instanceof LivingEntity le) {
                showBar(le);
            }
        }

        // 2) Hide expired bars
        var it = entityExpiry.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int remaining = e.getValue() - 1;
            if (remaining <= 0) {
                var id = e.getKey();
                var entity = server.getOverworld().getEntity(id); // good enough if most are in OW; for multi-dim, track Ref<ServerWorld>
                if (entity instanceof LivingEntity le) {
                    hideBar(le);
                }
                it.remove();
            } else {
                e.setValue(remaining);
            }
        }
    }

    private static void showBar(LivingEntity le) {
        UUID id = le.getUuid();

        float hp  = Math.max(0f, le.getHealth());
        float max = Math.max(1f, le.getMaxHealth());

        // 10-slot heart bar (no halves): ceil(hp/max * 10) filled hearts
        int slots = (int)Math.ceil(max / 2.0);
        int filled = (int)Math.ceil(hp * slots / max);
        if (filled < 0) filled = 0;
        if (filled > slots) filled = slots;

        StringBuilder sb = new StringBuilder(slots + 10);
        for (int i = 0; i < slots; i++) sb.append(i < filled ? HEART_FULL : HEART_EMPTY);

        // Optional numeric tail: " 9/20 (45%)" — comment out if you want icons only
        //int pct = Math.round(hp * 100f / max);
        //sb.append(" ").append((int)hp).append("/").append((int)max).append(" (").append(pct).append("%)");

        // Cache original once
        originalNames.putIfAbsent(id, le.getCustomName());

        le.setCustomName(Text.literal(sb.toString()));
        le.setCustomNameVisible(true);

        // refresh TTL
        entityExpiry.put(id, SHOW_TICKS);
    }

    private static void hideBar(LivingEntity le) {
        UUID id = le.getUuid();
        Text orig = originalNames.remove(id);
        le.setCustomName(orig); // may be null
        le.setCustomNameVisible(false);
    }

    // Used for lethal or forced cleanup; also clears caches
    private static void clearBarNow(LivingEntity le) {
        UUID id = le.getUuid();
        if (originalNames.containsKey(id)) {
            le.setCustomName(originalNames.get(id));
            le.setCustomNameVisible(false);
        }
        originalNames.remove(id);
        entityExpiry.remove(id);
    }

    private static LivingEntity getLookEntity(ServerPlayerEntity p, double maxDist) {
        // Raycast blocks first
        var start = p.getCameraPosVec(1.0f);
        var end = start.add(p.getRotationVec(1.0f).multiply(maxDist));
        var blockHit = p.getEntityWorld().raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));

        double limit = maxDist;
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            limit = blockHit.getPos().distanceTo(start);
        }

        // Search entities along the ray
        var dir = p.getRotationVec(1.0f);
        var box = p.getBoundingBox().stretch(dir.multiply(limit)).expand(1.0, 1.0, 1.0);

        LivingEntity best = null;
        double bestDist = limit + 1.0;

        for (var e : p.getEntityWorld().getOtherEntities(p, box, ent -> ent instanceof LivingEntity && ent.isAlive() && ent.isAttackable())) {
            var aabb = e.getBoundingBox().expand(0.3);
            var res = aabb.raycast(start, end);
            if (res.isPresent()) {
                double d = res.get().distanceTo(start);
                if (d < bestDist) {
                    best = (LivingEntity) e;
                    bestDist = d;
                }
            }
        }

        return best;
    }
}

