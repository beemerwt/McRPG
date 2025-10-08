package com.github.beemerwt.mcrpg.data;

import java.util.List;
import java.util.UUID;

public interface PlayerStore {
    static PlayerStore create() { return new BinaryPlayerStore(); }
    PlayerData get(UUID id);
    List<PlayerData> all();

    void save(UUID id);
    void saveAll();
}


