package com.trmtgtnh.server;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.trmtgtnh.config.TrmtConfig;

/**
 * What one reinforcement costs, and how it is taken.
 *
 * <p>
 * The materials are a list tried in order and paid with the first the player is carrying, so the
 * same tool works on a pack that has GregTech concrete and on plain vanilla that does not. A
 * {@code fluid:} entry is a bucket or cell of that fluid, and the container's own rule decides what
 * is left behind: draining an iron bucket hands back an empty bucket, a cell hands back an empty
 * cell, and a single-use ceramic bucket leaves nothing - which is exactly the iron-returns,
 * clay-consumes behaviour asked for, without a line of code about which is which. Everything else
 * is a plain item, spent one at a time.
 *
 * <p>
 * The tamper is in the hand, so the material is looked for everywhere else in the inventory, the
 * way {@link HealingCost} looks for bone meal. Creative pays nothing.
 */
public final class ReinforceCost {

    private static final String FLUID_PREFIX = "fluid:";

    private ReinforceCost() {}

    /** Spends one reinforcement's worth of material, or false when the player has none of it. */
    public static boolean pay(EntityPlayer player) {
        if (player == null) return false;
        if (player.capabilities.isCreativeMode) return true;

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
    public static boolean affordable(EntityPlayer player) {
        if (player == null) return false;
        if (player.capabilities.isCreativeMode) return true;
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
     * that is not.
     *
     * <p>
     * Asked by the report that explains why a material is not being taken, which is the one place
     * that needs to say what an item actually is rather than merely that it did not match. A pack
     * whose concrete is in a container nothing has registered as a fluid container answers null
     * here, and that is the whole of the difference between "the list names the wrong fluid" and
     * "no fluid entry can ever name this".
     */
    public static String fluidNameOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
        if (fluid == null || fluid.getFluid() == null) return null;
        return fluid.getFluid()
            .getName();
    }

    /** How much of that fluid it holds, for telling a full container from a partial one. */
    public static int fluidAmountOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0;
        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
        return fluid == null ? 0 : fluid.amount;
    }

    /** Which entry of {@code reinforce.materials} this would be paid by, or null for none. */
    public static String entryFor(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return null;
        for (String raw : TrmtConfig.reinforceMaterials) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (entry.regionMatches(true, 0, FLUID_PREFIX, 0, FLUID_PREFIX.length())) {
                String want = entry.substring(FLUID_PREFIX.length());
                String has = fluidNameOf(stack);
                if (has != null && want.equalsIgnoreCase(has)
                    && fluidAmountOf(stack) >= FluidContainerRegistry.BUCKET_VOLUME) {
                    return entry;
                }
            } else {
                ItemStack want = resolve(entry);
                if (want == null || want.getItem() != stack.getItem()) continue;
                if (want.getItemDamage() != net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                    && stack.getItemDamage() != want.getItemDamage()) {
                    continue;
                }
                return entry;
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
            return net.minecraftforge.fluids.FluidRegistry.getFluid(trimmed.substring(FLUID_PREFIX.length())) != null;
        }
        return resolve(trimmed) != null;
    }

    /**
     * What is left of one of these once its contents are used - an empty bucket, or null.
     *
     * <p>
     * The same question {@code payFluid} asks, and the reason a golem hands an iron bucket back and
     * keeps nothing from a clay one.
     *
     * <p>
     * Two rules asked in turn, because a pack's concrete is not always a fluid. The fluid registry
     * knows about anything registered as a container, and hands back whatever was registered as its
     * empty. What it does not know about - and a full bucket of something can perfectly well be a
     * plain item that no mod ever registered as a fluid container - may still declare a container
     * item of its own, which is the rule every crafting bench in the game already honours when it
     * leaves an empty bucket in the grid. Asking that second is how a bucket named as an item comes
     * back rather than being destroyed, and a single-use one, which declares none, still does not.
     */
    public static ItemStack leftoverOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        ItemStack one = stack.copy();
        one.stackSize = 1;
        ItemStack drained = FluidContainerRegistry.drainFluidContainer(one);
        if (drained != null) return drained;
        return one.getItem()
            .hasContainerItem(one)
                ? one.getItem()
                    .getContainerItem(one)
                : null;
    }

    // ------------------------------------------------------------------
    // Fluids
    // ------------------------------------------------------------------

    private static int findFluid(EntityPlayer player, String fluidName) {
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int i = 0; i < inventory.length; i++) {
            ItemStack held = inventory[i];
            if (held == null || held.stackSize <= 0) continue;
            FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(held);
            if (fluid == null || fluid.getFluid() == null) continue;
            if (fluid.amount < FluidContainerRegistry.BUCKET_VOLUME) continue;
            if (fluidName.equalsIgnoreCase(
                fluid.getFluid()
                    .getName())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean payFluid(EntityPlayer player, String fluidName) {
        int slot = findFluid(player, fluidName);
        if (slot < 0) return false;
        ItemStack[] inventory = player.inventory.mainInventory;
        ItemStack held = inventory[slot];

        // Drain one container's worth. The empty it hands back - if any - is what tells iron from
        // clay: a returnable container gives its empty, a single-use one gives null.
        ItemStack one = held.copy();
        one.stackSize = 1;
        ItemStack empty = FluidContainerRegistry.drainFluidContainer(one);

        held.stackSize--;
        if (held.stackSize <= 0) inventory[slot] = null;
        if (empty != null) giveBack(player, empty);
        return true;
    }

    private static void giveBack(EntityPlayer player, ItemStack stack) {
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropPlayerItemWithRandomChoice(stack, false);
        }
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    private static int findItem(EntityPlayer player, String entry) {
        ItemStack want = resolve(entry);
        if (want == null) return -1;
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int i = 0; i < inventory.length; i++) {
            ItemStack held = inventory[i];
            if (held == null || held.stackSize <= 0) continue;
            if (held.getItem() != want.getItem()) continue;
            if (want.getItemDamage() != net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                && held.getItemDamage() != want.getItemDamage()) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static boolean payItem(EntityPlayer player, String entry) {
        int slot = findItem(player, entry);
        if (slot < 0) return false;
        ItemStack[] inventory = player.inventory.mainInventory;
        ItemStack held = inventory[slot];

        // An entry that happens to name a full container is still a container, and its empty is
        // still owed. A pack whose concrete comes in a bucket that nothing registered as a fluid
        // container can only be named as an item, and a player paying that way was having the
        // bucket destroyed while a golem fed the same bucket handed it straight back. Whether an
        // empty comes back is the container's own rule either way, so both now ask it.
        ItemStack one = held.copy();
        one.stackSize = 1;
        ItemStack empty = leftoverOf(one);

        held.stackSize--;
        if (held.stackSize <= 0) inventory[slot] = null;
        if (empty != null) giveBack(player, empty);
        return true;
    }

    /** A {@code modid:name} or {@code modid:name:meta} entry as a matcher stack, or null. */
    private static ItemStack resolve(String entry) {
        int meta = net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE;
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
