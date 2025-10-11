package com.github.beemerwt.mcrpg.persistent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.*;

/**
 * Persistent ownership for furnace slots at a given BlockPos.
 * Fields:
 *  - input owner (slot 0)
 *  - fuel owner  (slot 1)
 * UUIDs are saved as strings via Codec.STRING.optionalFieldOf(...).
 */
public class FurnaceSlotOwners extends PersistentState {
    public static final class Owners {
        public UUID input;   // slot 0
        public UUID fuel;    // slot 1

        public Owners() {}

        public Owners(UUID all) {
            this.input = all;
            this.fuel = all;
        }

        public Owners(UUID input, UUID fuel) {
            this.input = input;
            this.fuel = fuel;
        }

        public boolean isAllNull() {
            return input == null && fuel == null;
        }
    }

    private static final String KEY = "mcrpg_furnace_slot_owners";

    private record Entry(long pos, String input, String fuel) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.optionalFieldOf("input").xmap(opt -> opt.orElse(null), Optional::ofNullable).forGetter(Entry::input),
                Codec.STRING.optionalFieldOf("fuel").xmap(opt -> opt.orElse(null), Optional::ofNullable).forGetter(Entry::fuel)
        ).apply(inst, Entry::new));
    }

    private static final Codec<FurnaceSlotOwners> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    Entry.CODEC.listOf().fieldOf("entries").forGetter(m -> {
                        List<Entry> out = new ArrayList<>(m.byPos.size());
                        for (Long2ObjectMap.Entry<Owners> e : m.byPos.long2ObjectEntrySet()) {
                            long pos = e.getLongKey();
                            Owners o = e.getValue();
                            out.add(new Entry(
                                    pos,
                                    o.input == null ? null : o.input.toString(),
                                    o.fuel  == null ? null : o.fuel.toString()
                            ));
                        }
                        return out;
                    })
            ).apply(inst, entries -> {
                FurnaceSlotOwners m = new FurnaceSlotOwners();
                for (Entry e : entries) {
                    UUID in  = parseUuidOrNull(e.input());
                    UUID fu  = parseUuidOrNull(e.fuel());
                    Owners o = new Owners(in, fu);
                    if (!o.isAllNull()) {
                        m.byPos.put(e.pos(), o);
                    }
                }
                return m;
            })
    );

    private final Long2ObjectOpenHashMap<Owners> byPos = new Long2ObjectOpenHashMap<>();
    public static final PersistentStateType<FurnaceSlotOwners> TYPE =
            new PersistentStateType<>(KEY, FurnaceSlotOwners::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    // ---------------------------
    // PersistentState lifecycle
    // ---------------------------

    public FurnaceSlotOwners() {
        super();
    }

    public static FurnaceSlotOwners get(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(TYPE);
    }

    // ---------------------------
    // Convenience API
    // ---------------------------

    public Owners get(BlockPos pos) {
        return byPos.get(pos.asLong());
    }

    public Owners getOrCreate(BlockPos pos) {
        Owners val = byPos.get(pos.asLong());
        if (val == null) {
            val = new Owners();
            byPos.put(pos.asLong(), val);
            markDirty();
        }
        return val;
    }

    public void setAll(BlockPos pos, UUID owner) {
        Objects.requireNonNull(owner, "owner");
        byPos.put(pos.asLong(), new Owners(owner));
        markDirty();
    }

    public void setInput(BlockPos pos, UUID owner) {
        Objects.requireNonNull(owner, "owner");
        getOrCreate(pos).input = owner;
        markDirty();
    }

    public void setFuel(BlockPos pos, UUID owner) {
        Objects.requireNonNull(owner, "owner");
        getOrCreate(pos).fuel = owner;
        markDirty();
    }

    public UUID getInputOwner(BlockPos pos) {
        Owners o = byPos.get(pos.asLong());
        return o == null ? null : o.input;
    }

    public UUID getFuelOwner(BlockPos pos) {
        Owners o = byPos.get(pos.asLong());
        return o == null ? null : o.fuel;
    }

    public void remove(BlockPos pos) {
        if (byPos.remove(pos.asLong()) != null) {
            markDirty();
        }
    }

    public boolean isEmpty() {
        return byPos.isEmpty();
    }

    // Helpers
    private static UUID parseUuidOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
