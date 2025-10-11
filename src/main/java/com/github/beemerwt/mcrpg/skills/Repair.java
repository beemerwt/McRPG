package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.skills.RepairConfig;
import com.github.beemerwt.mcrpg.data.PlayerData;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.text.Component;
import com.github.beemerwt.mcrpg.text.NamedTextColor;
import com.github.beemerwt.mcrpg.util.Leveling;
import com.github.beemerwt.mcrpg.util.Messenger;
import com.github.beemerwt.mcrpg.util.SoundUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Repair {

    private Repair() {}

    private static final int CONFIRM_TICKS = 100; // ~5s
    private static final ConcurrentHashMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(int slot, ItemStack snapshot, long expiresAtTick) {}

    /**
     * Called iff a player right-clicks an iron block.
     */
    public static ActionResult onPlayerUse(ServerPlayerEntity player, ServerWorld world) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
            Messenger.actionBar(player, Component.text("Hold a damaged item to repair.", NamedTextColor.GRAY));
            return ActionResult.PASS;
        }

        final Identifier itemId = Registries.ITEM.getId(stack.getItem());
        final String itemKey = itemId.toString();

        RepairConfig cfg = ConfigManager.getSkillConfig(SkillType.REPAIR);
        RepairConfig.Repairable rp = cfg.repairables.get(itemKey);

        // Fallback: generate a sane default if not configured.
        if (rp == null) {
            Messenger.actionBar(player, Component.text("This item cannot be repaired here.", NamedTextColor.RED));
            return ActionResult.PASS;
        }

        // Level gate
        int level = currentLevel(player);
        if (level < rp.minimumLevel) {
            Messenger.actionBar(player, Component.text("Requires Repair level " + rp.minimumLevel
                    + " to repair.", NamedTextColor.RED));
            return ActionResult.PASS;
        }

        // Optional maximumDurability gate (cap by item type's max damage)
        if (rp.maximumDurability != null && rp.maximumDurability > 0 && stack.getMaxDamage() > rp.maximumDurability) {
            Messenger.actionBar(player, Component.text("That item cannot be repaired here.", NamedTextColor.RED));
            return ActionResult.PASS;
        }

        final int damage = stack.getDamage(); // 0 = undamaged
        final int maxDamage = stack.getMaxDamage();
        if (damage <= 0) {
            Messenger.actionBar(player, Component.text("That item is already fully repaired.", NamedTextColor.GRAY));
            return ActionResult.PASS;
        }

        final int slot = player.getInventory().getSelectedSlot();
        long now = world.getTime();

        Pending pending = PENDING.get(player.getUuid());
        if (pending == null || pending.expiresAtTick() < now || pending.slot() != slot || !isSameItemForConfirm(stack, pending)) {
            // First tap: prompt confirm
            PENDING.put(player.getUuid(), new Pending(slot, stack.copy(), now + CONFIRM_TICKS));
            Messenger.actionBar(player, Component.text("Use again to confirm repair", NamedTextColor.YELLOW));
            SoundUtil.playSound(player, SoundEvents.BLOCK_ANVIL_STEP, 0.6f, 1.1f);
            return ActionResult.CONSUME;
        }

        // Resolve the repair material (explicit or inferred by category)
        String category = inferCategory(itemId);
        Item repairItem = resolveRepairItem(rp, category);
        if (repairItem == null) {
            Messenger.actionBar(player, Component.text("No repair material configured for this item.", NamedTextColor.RED));
            return ActionResult.PASS;
        }

        // Quantity requirement
        int minQty = (rp.minimumQuantity != null && rp.minimumQuantity > 0) ? rp.minimumQuantity : 1;

        int available = countInInventory(player, repairItem);
        if (available < minQty) {
            Messenger.actionBar(player, Component.text("Need " + minQty + "x " + displayName(repairItem) + " to repair.", NamedTextColor.RED));
            return ActionResult.PASS;
        }

        // Compute base restore percent (uniform between base..max)
        float pctBase = lerp(cfg.baseDurabilityPercent, cfg.maxDurabilityPercent, world.getRandom().nextFloat());

        // Level-scaled bonuses (expected to already be 0..1 fractions)
        float bonusPct = Leveling.getScaledPercentage(cfg.baseBonusPercentage, cfg.maxBonusPercentage, level);
        float superChance = Leveling.getScaledPercentage(cfg.baseSuperRepairChance, cfg.maxSuperRepairChance, level);

        // Durability restored by consuming minQty once
        float restore = (maxDamage * (pctBase / 100.0f));
        restore *= 1.0f + bonusPct;
        boolean superRepair = world.getRandom().nextDouble() < superChance;
        if (superRepair) restore *= 2.0f;

        int applied = Math.min(damage, Math.round(restore));
        if (applied <= 0) {
            Messenger.actionBar(player, Component.text("Could not repair that item.", NamedTextColor.RED));
            return ActionResult.PASS;
        }

        // Consume materials
        removeFromInventory(player, repairItem, minQty);

        // Apply repair (reduce damage)
        stack.setDamage(Math.max(0, damage - applied));

        // XP: Base × category × per-item × fraction restored
        float baseXp = cfg.xpModifiers.getOrDefault("Base", 1000.0f);
        float catMul = cfg.xpModifiers.getOrDefault(category, cfg.xpModifiers.getOrDefault("Other", 1.5f));
        float frac = applied / (float) maxDamage;
        long xp = Math.round(baseXp * catMul * rp.xpMultiplier * frac);
        Leveling.addXp(player, SkillType.REPAIR, xp);

        // Feedback
        String msg = "Repaired " + applied + " durability" + (superRepair ? " (Super Repair!)" : "") + " for +" + xp + " XP.";
        Messenger.actionBar(player, Component.text(msg, superRepair ? NamedTextColor.GOLD : NamedTextColor.GREEN));
        SoundUtil.playSound(player, SoundEvents.BLOCK_ANVIL_USE, 0.9f, superRepair ? 1.25f : 1.0f);

        // Clear pending
        PENDING.remove(player.getUuid());
        return ActionResult.CONSUME;
    }

    // ---------- helpers ----------

    private static int currentLevel(ServerPlayerEntity player) {
        PlayerData data = McRPG.getStore().get(player);
        long total = data.xp.get(SkillType.REPAIR);
        return Leveling.levelFromTotalXp(total);
        // Or Leveling.getLevel(player, SkillType.REPAIR) if you have it.
    }

    private static boolean isSameItemForConfirm(ItemStack now, Pending pending) {
        ItemStack snap = pending.snapshot;
        if (!now.isOf(snap.getItem())) return false;
        if (!Objects.equals(now.getComponents(), snap.getComponents())) return false;
        if (now.getDamage() != snap.getDamage()) return false;
        return now.getCount() == snap.getCount();
    }

    private static String displayName(Item item) {
        return Registries.ITEM.getId(item).toString();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static String inferCategory(Identifier itemId) {
        String path = itemId.getPath();
        if (path.startsWith("wooden_") || path.equals("shield")) return "Wood";
        if (path.startsWith("stone_")) return "Stone";
        if (path.startsWith("iron_") || path.equals("shears") || path.equals("flint_and_steel")) return "Iron";
        if (path.startsWith("golden_")) return "Gold";
        if (path.startsWith("diamond_")) return "Diamond";
        if (path.startsWith("netherite_")) return "Netherite";
        if (path.startsWith("leather_")) return "Leather";
        return switch (path) {
            case "bow", "crossbow", "fishing_rod", "carrot_on_a_stick", "warped_fungus_on_a_stick" -> "String";
            case "elytra", "trident", "mace" -> "Other"; // explicit entries usually provide material
            default -> "Other";
        };
    }

    private static Item resolveRepairItem(RepairConfig.Repairable rp, String category) {
        String idStr = (rp.repairMaterial != null && !rp.repairMaterial.isBlank())
                ? rp.repairMaterial
                : switch (category) {
            case "Wood" -> "minecraft:oak_planks";
            case "Stone" -> "minecraft:cobblestone";
            case "Iron" -> "minecraft:iron_ingot";
            case "Gold" -> "minecraft:gold_ingot";
            case "Diamond" -> "minecraft:diamond";
            case "Netherite" -> "minecraft:netherite_ingot";
            case "Leather" -> "minecraft:leather";
            case "String" -> "minecraft:string";
            default -> "";
        };
        if (idStr.isEmpty()) return null;
        return Registries.ITEM.get(Identifier.tryParse(idStr));
    }

    private static int countInInventory(ServerPlayerEntity player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == item) total += s.getCount();
        }
        return total;
    }

    private static void removeFromInventory(ServerPlayerEntity player, Item item, int toRemove) {
        for (int i = 0; i < player.getInventory().size() && toRemove > 0; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty() || s.getItem() != item) continue;
            int take = Math.min(s.getCount(), toRemove);
            s.decrement(take);
            toRemove -= take;
        }
    }
}
