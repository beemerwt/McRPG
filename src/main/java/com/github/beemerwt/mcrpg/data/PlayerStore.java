package com.github.beemerwt.mcrpg.data;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerStore {
    static PlayerStore create() { return new BinaryPlayerStore(); }
    PlayerData get(ServerPlayerEntity player);
    PlayerData get(UUID id);
    Optional<PlayerData> lookup(String name);
    List<PlayerData> all();

    void save(UUID id);
    void saveAll();
}


