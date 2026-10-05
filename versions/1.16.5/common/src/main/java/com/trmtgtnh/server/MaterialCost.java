package com.trmtgtnh.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
    public static boolean pay(Player player, String[] materials, int count) {
        if (player == null) return false;
        if (player.abilities.instabuild) return true;
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
    public static boolean affordable(Player player, String[] materials, int count) {
        if (player == null) return false;
        if (player.abilities.instabuild) return true;
        if (materials == null || count <= 0) return count <= 0;
        for (String raw : materials) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (held(player, resolve(entry)) >= count) return true;
        }
        return false;
    }

    private static boolean spend(Player player, String entry, int count) {
        ItemStack want = resolve(entry);
        if (want == null) return false;
        if (held(player, want) < count) return false;

        int remaining = count;
        java.util.List<ItemStack> inventory = player.inventory.items;
        for (int i = 0; i < inventory.size() && remaining > 0; i++) {
            ItemStack held = inventory.get(i);
            if (!matches(held, want)) continue;
            int take = Math.min(remaining, held.getCount());
            held.shrink(take);
            remaining -= take;
            if (held.isEmpty()) inventory.set(i, ItemStack.EMPTY);
        }
        return remaining <= 0;
    }

    private static int held(Player player, ItemStack want) {
        if (want == null) return 0;
        int total = 0;
        for (ItemStack held : player.inventory.items) {
            if (matches(held, want)) total += held.getCount();
        }
        return total;
    }

    private static boolean matches(ItemStack held, ItemStack want) {
        if (held == null || held.isEmpty() || want == null) return false;
        // Item identity and nothing else. Both older editions compare a damage value too, because a
        // stack's damage used to pick a variant and a wildcard meant any of them; here there is one
        // stack per item, so "any variant of this" and "this" ask the same thing.
        return held.getItem() == want.getItem();
    }

    /** A {@code modid:name} or {@code modid:name:meta} entry as a matcher stack, or null. */
    private static ItemStack resolve(String entry) {
        String name = entry;
        int lastColon = entry.lastIndexOf(':');
        if (lastColon > 0 && entry.indexOf(':') != lastColon) {
            // A trailing number is a metadata value from an older edition's settings file, and
            // metadata is gone. Dropped rather than refused: these files are meant to travel between
            // the editions, and refusing one entry would quietly take a material off the list.
            String tail = entry.substring(lastColon + 1);
            boolean allDigits = !tail.isEmpty();
            for (int at = 0; at < tail.length(); at++) {
                if (!Character.isDigit(tail.charAt(at))) {
                    allDigits = false;
                    break;
                }
            }
            if (allDigits) name = entry.substring(0, lastColon);
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(name);
        if (id == null) return null;
        // One registry. That edition asks the block registry and then the item registry, because a
        // block and its item were separate things under separate names; here a block's item is in the
        // item registry under the block's own name.
        Item item = net.minecraft.core.Registry.ITEM.getOptional(id)
            .orElse(null);
        return item == null ? null : new ItemStack(item, 1);
    }
}
