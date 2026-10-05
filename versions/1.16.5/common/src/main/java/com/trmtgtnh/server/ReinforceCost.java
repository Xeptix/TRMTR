package com.trmtgtnh.server;

import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.Fluids;

/**
 * What one reinforcement costs, and taking it.
 *
 * <p>
 * The list is {@code reinforce.materials}, read in order, and the first entry the player can pay
 * with is the one that is taken. An entry is either an item by name or, with a {@code fluid:}
 * prefix, a bucket or cell of that fluid - and the container's own rule decides what comes back.
 *
 * <p>
 * <strong>Written by hand rather than carried, as the 1.12.2 edition's was, and for the same reason
 * turned up a version later.</strong> There, the half that reads fluids was what 1.12.2 had replaced
 * outright: the fluid container registry, a static table of filled and empty pairs, had become a
 * capability a container answers for itself. Here that capability is Forge's and Fabric has nothing
 * of the kind at this version, so the reading moves behind {@link Fluids} - a seam each loader
 * fills, and the one place in this mod where the two loaders can answer differently in a way a
 * player would notice. {@code Fluids} says what the difference is.
 *
 * <p>
 * Everything else is the other editions' reasoning, kept: the order the list is read in, the second
 * rule that asks whether an item declares a remainder so a bucket named as an item still comes back,
 * and the single answer {@link #entryFor} gives so that what pays and what the report says it pays
 * with cannot drift apart.
 */
public final class ReinforceCost {

    /** Marks an entry as a fluid rather than an item. */
    private static final String FLUID_PREFIX = "fluid:";

    private ReinforceCost() {}

    /** Spends one reinforcement's worth of material, or false when the player has none of it. */
    public static boolean pay(Player player) {
        if (player == null) return false;
        if (player.abilities.instabuild) return true;

        for (String raw : TrmtConfig.reinforceMaterials) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (entry.regionMatches(true, 0, FLUID_PREFIX, 0, FLUID_PREFIX.length())) {
                if (payFluid(player, entry.substring(FLUID_PREFIX.length()))) return true;
            } else if (payItem(player, entry)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player is carrying anything that would pay, without taking it. */
    public static boolean affordable(Player player) {
        if (player == null) return false;
        if (player.abilities.instabuild) return true;
        for (String raw : TrmtConfig.reinforceMaterials) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (entry.regionMatches(true, 0, FLUID_PREFIX, 0, FLUID_PREFIX.length())) {
                if (findFluid(player, entry.substring(FLUID_PREFIX.length())) >= 0) return true;
            } else if (findItem(player, entry) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether one of these would pay for a reinforcement, without taking anything.
     *
     * <p>
     * The same list and the same order the player's own purse is checked against, so a golem is fed
     * exactly what a tamper is paid with and a pack that renames its concrete renames it once.
     *
     * <p>
     * Asked through {@link #entryFor}, which is the same question with the answer kept rather than
     * thrown away. Two copies of this matching would drift, and the copy that drifted would be the
     * one the report prints - so a golem would refuse a bucket while the thing built to explain the
     * refusal said it should have taken it.
     */
    public static boolean isMaterial(ItemStack stack) {
        return entryFor(stack) != null;
    }

    /**
     * The registered name of the fluid one of these is a full container of, or null for anything
     * that is not one.
     *
     * <p>
     * Through the loader seam, because there is no shared way to ask. A pack whose concrete is in a
     * container the loader cannot read answers null here, and that is the whole of the difference
     * between "the list names the wrong fluid" and "no fluid entry can pay on this loader".
     */
    public static String fluidNameOf(ItemStack stack) {
        return Fluids.nameOf(stack);
    }

    /** How much of that fluid it holds, for telling a full container from a partial one. */
    public static int fluidAmountOf(ItemStack stack) {
        return Fluids.amountOf(stack);
    }

    /** Which entry of {@code reinforce.materials} this would be paid by, or null for none. */
    public static String entryFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (String raw : TrmtConfig.reinforceMaterials) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (entry.regionMatches(true, 0, FLUID_PREFIX, 0, FLUID_PREFIX.length())) {
                String want = entry.substring(FLUID_PREFIX.length());
                String has = fluidNameOf(stack);
                if (Fluids.named(want, has) && fluidAmountOf(stack) >= Fluids.BUCKET) return entry;
            } else {
                Item want = resolve(entry);
                // Item identity and nothing else. Both older editions also compare a damage value,
                // which used to pick a variant; there is one item per thing now.
                if (want != null && want == stack.getItem()) return entry;
            }
        }
        return null;
    }

    /** Whether an entry of the list names anything this game has, so a typo can be told from a gap. */
    public static boolean entryExists(String entry) {
        if (entry == null) return false;
        String trimmed = entry.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.regionMatches(true, 0, FLUID_PREFIX, 0, FLUID_PREFIX.length())) {
            return fluidExists(trimmed.substring(FLUID_PREFIX.length()));
        }
        return resolve(trimmed) != null;
    }

    /**
     * What is left of one of these once its contents are used - an empty bucket, or null.
     *
     * <p>
     * The same question {@code payFluid} asks, and the reason a golem hands an iron bucket back and
     * keeps nothing from a clay one. Null rather than the empty stack for "nothing", because that is
     * the answer every caller in the other editions was written against.
     *
     * <p>
     * Two rules asked in turn, because a pack's concrete is not always a fluid. A container the
     * loader can read is drained by it and hands back whatever it says it has become. What it cannot
     * read - and a full bucket of something can perfectly well be a plain item no mod gave a handler
     * - may still declare a crafting remainder, which is the rule every crafting bench in the game
     * already honours when it leaves an empty bucket in the grid. Asking that second is how a bucket
     * named as an item comes back rather than being destroyed, and a single-use one, which declares
     * none, still does not.
     */
    public static ItemStack leftoverOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ItemStack one = stack.copy();
        one.setCount(1);
        ItemStack drained = Fluids.drained(one);
        if (drained != null) return drained.isEmpty() ? null : drained;
        Item remains = one.getItem()
            .getCraftingRemainingItem();
        return remains == null ? null : new ItemStack(remains);
    }

    // ------------------------------------------------------------------
    // Fluids
    // ------------------------------------------------------------------

    private static boolean fluidExists(String fluidName) {
        if (fluidName == null || fluidName.isEmpty()) return false;
        ResourceLocation named = ResourceLocation.tryParse(fluidName);
        if (named != null && Registry.FLUID.getOptional(named)
            .isPresent()) {
            return true;
        }
        // A bare name, which is how both older editions spell a fluid and how a travelling settings
        // file still does. Vanilla's are under minecraft; a pack's are not, and those have to be
        // written in full here - which the settings file's own comment says.
        ResourceLocation vanilla = ResourceLocation.tryParse("minecraft:" + fluidName);
        return vanilla != null && Registry.FLUID.getOptional(vanilla)
            .isPresent();
    }

    private static int findFluid(Player player, String fluidName) {
        List<ItemStack> inventory = player.inventory.items;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack held = inventory.get(i);
            if (held.isEmpty()) continue;
            String has = fluidNameOf(held);
            if (has == null) continue;
            if (fluidAmountOf(held) < Fluids.BUCKET) continue;
            if (Fluids.named(fluidName, has)) return i;
        }
        return -1;
    }

    private static boolean payFluid(Player player, String fluidName) {
        int slot = findFluid(player, fluidName);
        if (slot < 0) return false;
        List<ItemStack> inventory = player.inventory.items;
        ItemStack held = inventory.get(slot);

        // Drain one container's worth. What it hands back - if anything - is what tells iron from
        // clay: a returnable container becomes its empty, a single-use one becomes nothing.
        ItemStack one = held.copy();
        one.setCount(1);
        ItemStack empty = Fluids.drained(one);

        held.shrink(1);
        if (held.isEmpty()) inventory.set(slot, ItemStack.EMPTY);
        if (empty != null && !empty.isEmpty()) giveBack(player, empty);
        return true;
    }

    private static void giveBack(Player player, ItemStack stack) {
        if (!player.inventory.add(stack)) {
            player.drop(stack, false);
        }
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    private static int findItem(Player player, String entry) {
        Item want = resolve(entry);
        if (want == null) return -1;
        List<ItemStack> inventory = player.inventory.items;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack held = inventory.get(i);
            if (held.isEmpty()) continue;
            if (held.getItem() == want) return i;
        }
        return -1;
    }

    private static boolean payItem(Player player, String entry) {
        int slot = findItem(player, entry);
        if (slot < 0) return false;
        List<ItemStack> inventory = player.inventory.items;
        ItemStack held = inventory.get(slot);

        // An entry that happens to name a full container is still a container, and its empty is
        // still owed. A pack whose concrete comes in a bucket the loader cannot read can only be
        // named as an item, and a player paying that way was having the bucket destroyed while a
        // golem fed the same bucket handed it straight back. Whether an empty comes back is the
        // container's own rule either way, so both ask it.
        ItemStack one = held.copy();
        one.setCount(1);
        ItemStack empty = leftoverOf(one);

        held.shrink(1);
        if (held.isEmpty()) inventory.set(slot, ItemStack.EMPTY);
        if (empty != null) giveBack(player, empty);
        return true;
    }

    /**
     * A {@code modid:name} entry as the item it names, or null.
     *
     * <p>
     * A trailing {@code :0} is still parsed and still dropped: it meant a metadata, which this
     * version does not have, and a settings file that has travelled from an older edition is full of
     * them. Refusing one would quietly take a material off the list.
     */
    private static Item resolve(String entry) {
        String name = entry;
        int lastColon = entry.lastIndexOf(':');
        if (lastColon > 0 && entry.indexOf(':') != lastColon) {
            String tail = entry.substring(lastColon + 1);
            boolean allDigits = !tail.isEmpty();
            for (int at = 0; at < tail.length(); at++) {
                if (!Character.isDigit(tail.charAt(at)) && tail.charAt(at) != '*') {
                    allDigits = false;
                    break;
                }
            }
            if (allDigits) name = entry.substring(0, lastColon);
        }
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) return null;
        return Registry.ITEM.getOptional(id)
            .orElse(null);
    }
}
