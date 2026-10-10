package com.trmtgtnh.util;

import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Reading and writing somebody else's inventory, by the only rules everybody already agrees on.
 *
 * <p>
 * A hopper has been moving items into and out of arbitrary blocks since 2013, and every storage mod
 * worth the name is written so that a hopper works on it. So this is a transcription of what a
 * hopper does rather than a fresh idea about how inventories ought to behave: the same per-slot
 * gates, the same stack-limit clamp, the same take-then-put-back-on-failure discipline. Anywhere it
 * departs from the hopper it is because the hopper is missing a check rather than because a better
 * rule was available.
 *
 * <p>
 * Three of those departures, all of them things vanilla gets away with only because it is vanilla:
 * a foreign {@code getAccessibleSlotsFromSide} may return null or indices outside the inventory,
 * and vanilla indexes it unchecked; a side of -1 reaches {@code canPlaceItemThroughFace} in the fall-through
 * path and a mod that indexes a per-side array by it throws; and vanilla does not bound the number
 * of slots it will walk. Each is a crash in somebody else's code with this mod's name on the
 * report.
 *
 * <p>
 * No mod is named here and none is linked against. Better Barrels, the drawers, the iron chests and
 * whatever a pack ships next are all {@link Container} or {@link WorldlyContainer}, and that is the
 * whole of what this needs to know about any of them.
 */
public final class InventoryAccess {

    private InventoryAccess() {}

    /**
     * The stack limit for one slot.
     *
     * <p>
     * The smaller of what the item allows and what the inventory allows, and the clamp is not
     * politeness. A stack size lives in memory as an int and is written to disk as a signed byte,
     * so a barrel cheerfully reporting a limit of four thousand will produce a stack that comes
     * back from the save as a negative number.
     */
    public static int roomInSlot(Container inventory, ItemStack stack) {
        return Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
    }

    /**
     * Whether two stacks are the same thing, which is the question "does it already hold this".
     *
     * <p>
     * Item, damage and tag - the hopper's own test. Not {@code areItemStacksEqual}, which also
     * compares how many there are, and not {@code isItemEqual}, which ignores the tag and would
     * therefore call a tamper carrying a grade the same thing as a blank one.
     */
    public static boolean sameKind(ItemStack left, ItemStack right) {
        if (left == null || right == null) return false;
        if (left.getItem() != right.getItem()) return false;
        if (left.getDamageValue() != right.getDamageValue()) return false;
        return ItemStack.tagMatches(left, right);
    }

    /**
     * The slots reachable from a side, bounded and checked.
     *
     * @return the slot indices to try, never null, and every one of them a real slot
     */
    public static int[] slotsOn(Container inventory, net.minecraft.core.Direction side) {
        int size = inventory.getContainerSize();
        if (inventory instanceof WorldlyContainer && side != null) {
            int[] offered = ((WorldlyContainer) inventory).getSlotsForFace(side);
            if (offered == null) return new int[0];
            int kept = 0;
            for (int slot : offered) {
                if (slot >= 0 && slot < size) kept++;
            }
            if (kept == offered.length) return offered;
            int[] safe = new int[kept];
            int at = 0;
            for (int slot : offered) {
                if (slot >= 0 && slot < size) safe[at++] = slot;
            }
            return safe;
        }
        int[] every = new int[size];
        for (int slot = 0; slot < size; slot++) {
            every[slot] = slot;
        }
        return every;
    }

    /** Whether a slot will take this, by the inventory's own account. Advisory, like the hopper's. */
    public static boolean canPut(Container inventory, int slot, ItemStack stack, net.minecraft.core.Direction side) {
        if (!inventory.canPlaceItem(slot, stack)) return false;
        if (!(inventory instanceof WorldlyContainer)) return true;
        return ((WorldlyContainer) inventory).canPlaceItemThroughFace(slot, stack, side);
    }

    /** Whether a slot will give this up. Deliberately no validity check; the hopper has none either. */
    public static boolean canTake(Container inventory, int slot, ItemStack stack, net.minecraft.core.Direction side) {
        if (!(inventory instanceof WorldlyContainer)) return true;
        return ((WorldlyContainer) inventory).canTakeItemThroughFace(slot, stack, side);
    }

    /** Whether this inventory is already holding something of the same kind. */
    public static boolean alreadyHolds(Container inventory, ItemStack wanted, net.minecraft.core.Direction side) {
        if (inventory == null || wanted == null) return false;
        for (int slot : slotsOn(inventory, side)) {
            if (sameKind(inventory.getItem(slot), wanted)) return true;
        }
        return false;
    }

    /**
     * Puts as much of a stack into an inventory as it will take.
     *
     * @return what is left over, or null when the whole of it went in
     */
    public static ItemStack put(Container inventory, ItemStack stack, net.minecraft.core.Direction side) {
        if (inventory == null || stack == null || stack.isEmpty()) return stack;
        ItemStack left = stack;
        boolean moved = false;

        for (int slot : slotsOn(inventory, side)) {
            if (left == null || left.isEmpty()) break;
            if (!canPut(inventory, slot, left, side)) continue;

            ItemStack there = inventory.getItem(slot);
            int room = roomInSlot(inventory, left);
            // A container says EMPTY for a free slot at this version, never null, so null alone found no room in an
            // empty chest (0.9.222, spec GO42).
            if (there == null || there.isEmpty()) {
                if (room >= left.getCount()) {
                    inventory.setItem(slot, left);
                    left = null;
                } else {
                    inventory.setItem(slot, left.split(room));
                }
                moved = true;
                continue;
            }

            if (!sameKind(there, left)) continue;
            if (there.getCount() >= room) continue;
            int take = Math.min(left.getCount(), room - there.getCount());
            if (take <= 0) continue;
            there.grow(take);
            left.shrink(take);
            if (left.isEmpty()) left = ItemStack.EMPTY;
            moved = true;
        }

        if (moved) inventory.setChanged();
        return left == null || left.isEmpty() ? ItemStack.EMPTY : left;
    }

    /**
     * Takes one kind of thing out of an inventory, as much of it as the asker will have.
     *
     * <p>
     * It comes out before anywhere has been found to put it, which is the hopper's arrangement too,
     * and it puts a duty on the caller: a stack that could not be given a home has to be given back.
     * The alternative - working out in advance whether the destination would take it - is a
     * simulation of an insert, and a simulation of somebody else's insert is a guess.
     *
     * <p>
     * How many is asked of the candidate rather than fixed by the caller, because the caller cannot
     * know what it is about to find. A number decided in advance is either too small, which leaves
     * a fetch that had to be repeated, or too large, which takes a stack out of somebody's chest
     * only to hand it straight back.
     *
     * @param wanted how much of a given stack is worth taking, if any
     * @return what came out, or null when there was none of it
     */
    public static ItemStack take(Container inventory, Wanted wanted, net.minecraft.core.Direction side) {
        if (inventory == null || wanted == null) return null;
        for (int slot : slotsOn(inventory, side)) {
            ItemStack there = inventory.getItem(slot);
            if (there == null || there.isEmpty()) continue;
            int most = wanted.howMany(there);
            if (most <= 0) continue;
            if (!canTake(inventory, slot, there, side)) continue;

            ItemStack got = inventory.removeItem(slot, Math.min(most, there.getCount()));
            if (got == null || got.isEmpty()) continue;
            inventory.setChanged();
            return got;
        }
        return null;
    }

    /**
     * What a search is looking for, so the caller keeps its own rule rather than passing a stack.
     */
    public interface Wanted {

        /** How many of this to take, or zero for none of it at all. */
        int howMany(ItemStack stack);
    }
}
