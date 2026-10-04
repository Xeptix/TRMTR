package com.trmtgtnh.server;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * What a tamper gesture costs in materials, and how that cost is taken.
 *
 * <p>
 * The same ordered-list idea as {@link ReinforceCost}, but paid in a count rather than one at a
 * time. Each caller brings its own list and its own count, so barring hostiles, barring passives
 * and lighting a block can all be priced in different things - and the Wayfarer pays half of
 * whichever it is. Undoing any of them costs nothing here; that is a durability hit on the tool,
 * applied by the caller.
 */
public final class MaterialCost {

    private MaterialCost() {}

    /** Spends {@code count} of the first material the player has enough of, or false if none. */
    public static boolean pay(EntityPlayer player, String[] materials, int count) {
        if (player == null) return false;
        if (player.capabilities.isCreativeMode) return true;
        if (materials == null || count <= 0) return count <= 0;
        for (String raw : materials) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (spend(player, entry, count)) return true;
        }
        return false;
    }

    /** Whether the player is carrying enough of any listed material, without taking it. */
    public static boolean affordable(EntityPlayer player, String[] materials, int count) {
        if (player == null) return false;
        if (player.capabilities.isCreativeMode) return true;
        if (materials == null || count <= 0) return count <= 0;
        for (String raw : materials) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (held(player, resolve(entry)) >= count) return true;
        }
        return false;
    }

    private static boolean spend(EntityPlayer player, String entry, int count) {
        ItemStack want = resolve(entry);
        if (want == null) return false;
        if (held(player, want) < count) return false;

        int remaining = count;
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int i = 0; i < inventory.length && remaining > 0; i++) {
            if (!matches(inventory[i], want)) continue;
            int take = Math.min(remaining, inventory[i].stackSize);
            inventory[i].stackSize -= take;
            remaining -= take;
            if (inventory[i].stackSize <= 0) inventory[i] = null;
        }
        return remaining <= 0;
    }

    private static int held(EntityPlayer player, ItemStack want) {
        if (want == null) return 0;
        int total = 0;
        for (ItemStack held : player.inventory.mainInventory) {
            if (matches(held, want)) total += held.stackSize;
        }
        return total;
    }

    private static boolean matches(ItemStack held, ItemStack want) {
        if (held == null || held.stackSize <= 0 || want == null) return false;
        if (held.getItem() != want.getItem()) return false;
        return want.getItemDamage() == OreDictionary.WILDCARD_VALUE || held.getItemDamage() == want.getItemDamage();
    }

    /** A {@code modid:name} or {@code modid:name:meta} entry as a matcher stack, or null. */
    private static ItemStack resolve(String entry) {
        int meta = OreDictionary.WILDCARD_VALUE;
        String name = entry;
        int lastColon = entry.lastIndexOf(':');
        if (lastColon > 0 && entry.indexOf(':') != lastColon) {
            String tail = entry.substring(lastColon + 1);
            name = entry.substring(0, lastColon);
            try {
                meta = Integer.parseInt(tail);
            } catch (NumberFormatException notMeta) {
                name = entry;
            }
        }
        Block block = Block.getBlockFromName(name);
        if (block != null && block != net.minecraft.init.Blocks.air) {
            Item item = Item.getItemFromBlock(block);
            if (item != null) return new ItemStack(item, 1, meta);
        }
        Item item = (Item) Item.itemRegistry.getObject(name);
        return item == null ? null : new ItemStack(item, 1, meta);
    }
}
