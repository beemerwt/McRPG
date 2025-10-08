package com.github.beemerwt.mcrpg.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;

public final class CropMarkers extends PersistentState {
    private static final String KEY = "mcrpg_herbalism_markers";
    private final Long2FloatOpenHashMap posToMultiplier = new Long2FloatOpenHashMap();

    private static final Codec<CropMarkers> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    Codec.LONG.listOf().fieldOf("positions").forGetter(m -> {
                        long[] keys = m.posToMultiplier.keySet().toLongArray();
                        List<Long> out = new ArrayList<>(keys.length);
                        for (long k : keys) out.add(k);
                        return out;
                    }),
                    Codec.FLOAT.listOf().fieldOf("multipliers").forGetter(m -> {
                        long[] keys = m.posToMultiplier.keySet().toLongArray();
                        List<Float> out = new ArrayList<>(keys.length);
                        for (long k : keys) out.add(m.posToMultiplier.get(k));
                        return out;
                    })
            ).apply(inst, (positions, multipliers) -> {
                CropMarkers m = new CropMarkers();
                int len = Math.min(positions.size(), multipliers.size());
                for (int i = 0; i < len; i++) {
                    long pos = positions.get(i);
                    float f = clampMultiplier(multipliers.get(i));
                    if (f > 1.0f) {
                        m.posToMultiplier.put(pos, f);
                    }
                }
                return m;
            })
    );

    public static final PersistentStateType<CropMarkers> TYPE =
            new PersistentStateType<>(KEY, CropMarkers::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    public CropMarkers() {
        super();
        posToMultiplier.defaultReturnValue(1.0f);
    }

    public static CropMarkers get(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(TYPE);
    }

    public float getMultiplier(BlockPos pos) {
        return posToMultiplier.get(pos.asLong());
    }

    public boolean isMarked(BlockPos pos) {
        return getMultiplier(pos) > 1.0;
    }

    public void mark(ServerWorld world, BlockPos pos, float multiplier) {
        float clamped = clampMultiplier(multiplier);
        if (clamped <= 1.0f) {
            unmark(world, pos);
            return;
        }
        posToMultiplier.put(pos.asLong(), clamped);
        markDirty();
    }

    public void unmark(ServerWorld world, BlockPos pos) {
        if (posToMultiplier.remove(pos.asLong()) != 0) {
            markDirty();
        }
    }

    public void maybeCleanupIfAir(ServerWorld world, BlockPos pos) {
        if (world.isAir(pos)) {
            unmark(world, pos);
        }
    }

    private static float clampMultiplier(float f) {
        if (Float.isNaN(f)) return 1.0f;
        if (f < 1.0f) return 1.0f;
        if (f > 16.0f) return 16.0f; // hard cap, just in case
        return f;
    }
}
