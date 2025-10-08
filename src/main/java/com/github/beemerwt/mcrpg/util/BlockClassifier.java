package com.github.beemerwt.mcrpg.util;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class BlockClassifier {
    private static final TagKey<Block> ORES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft:ores"));
    private static final TagKey<Block> LEAVES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft:leaves"));
    private static final TagKey<Block> WART_BLOCKS = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft:wart_blocks"));
    private static final TagKey<Block> LOGS = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft:logs"));

    public static boolean isOre(Block block) {
        RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
        return entry.isIn(ORES);
    }

    public static boolean isLeaf(Block block) {
        RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
        return entry.isIn(LEAVES);
    }

    public static boolean isWartBlock(Block block) {
        RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
        return entry.isIn(WART_BLOCKS);
    }

    public static boolean isRoots(Block block) {
        String id = Registries.BLOCK.getId(block).toString();
        return id.equalsIgnoreCase("minecraft:mangrove_roots") ||
                id.equalsIgnoreCase("minecraft:muddy_mangrove_roots");
    }

    public static boolean isLog(Block block) {
        RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
        return entry.isIn(LOGS);
    }
}
