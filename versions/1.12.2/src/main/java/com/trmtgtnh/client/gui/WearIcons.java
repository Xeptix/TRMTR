package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The example blocks each family gets shown as, resolved once and cycled through.
 *
 * <p>
 * The Wear Table and its editor both want a picture of a family that changes every second, so the
 * lists live here rather than being built twice. A family with nothing detected falls back to its
 * vanilla stand-in, so a row is never iconless.
 */
@SideOnly(Side.CLIENT)
public final class WearIcons {

    /**
     * How long each block in a family's cycle is shown for, in ticks.
     *
     * <p>
     * Here rather than in either screen, because both of them cycle the same lists and the two
     * drifting apart would be two screens disagreeing about what a family looks like. A second and
     * a quarter: long enough to read the block it is standing on, short enough that a family of
     * eight comes round inside ten seconds.
     */
    static final int CYCLE_TICKS = 25;

    private static Map<SurfaceFamily, List<ItemStack>> cache;

    private static Map<SurfaceFamily, ItemStack> stocks;

    private WearIcons() {}

    /** The blocks to cycle for this family; never empty for a staged one. */
    static List<ItemStack> forFamily(SurfaceFamily family) {
        if (cache == null) build();
        List<ItemStack> list = cache.get(family);
        return list == null ? Collections.<ItemStack>emptyList() : list;
    }

    /** Forgets the cache, so a detection change is picked up next time the table opens. */
    public static void reset() {
        cache = null;
        stocks = null;
    }

    private static void build() {
        Map<SurfaceFamily, List<ItemStack>> built = new EnumMap<SurfaceFamily, List<ItemStack>>(SurfaceFamily.class);
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (family.staged) built.put(family, new ArrayList<ItemStack>());
        }
        for (SurfaceRegistry.SurfaceState state : SurfaceRegistry.texturableStates()) {
            List<ItemStack> list = built.get(state.family);
            if (list == null) continue;
            Item item = heldForm(state.block);
            if (item != null) list.add(new ItemStack(item, 1, 0));
        }
        for (SurfaceFamily family : built.keySet()) {
            List<ItemStack> list = built.get(family);
            if (list.isEmpty()) {
                Block fallback = vanillaFor(family);
                Item item = heldForm(fallback);
                if (item != null) list.add(new ItemStack(item, 1, 0));
            }
        }
        cache = built;
    }

    /**
     * The block everybody already knows this family by.
     *
     * <p>
     * The same answer the fallback below has always given, said out loud so a screen can ask for it
     * on purpose rather than only receiving it when detection came back with nothing. A pack's own
     * blocks are the honest thing to cycle - they are what the ground is actually made of - but
     * they are no use at all for telling one look from another, because nobody knows what a
     * chiselled limestone is supposed to look like unworn. Vanilla is the shared reference.
     *
     * <p>
     * Not taken from the cycled list, because it may not be in it: a pack that excludes plain stone
     * from detection has a stone family with no stone in it, and the answer to "show me the
     * ordinary one" is still ordinary stone.
     */
    static ItemStack stock(SurfaceFamily family) {
        if (stocks == null) stocks = new EnumMap<SurfaceFamily, ItemStack>(SurfaceFamily.class);
        if (stocks.containsKey(family)) return stocks.get(family);
        Block block = vanillaFor(family);
        Item item = heldForm(block);
        ItemStack made = item == null ? null : new ItemStack(item, 1, 0);
        // Kept rather than made fresh, because this is asked once per family per frame while the
        // reset is in force, and the cycled list beside it has always been cached for the same
        // reason. A null answer is cached too - a family whose stand-in has no item form will not
        // grow one, and asking again every frame would be asking a settled question.
        stocks.put(family, made);
        return made;
    }

    private static Block vanillaFor(SurfaceFamily family) {
        switch (family) {
            case GRASS:
                return Blocks.GRASS;
            case DIRT:
                return Blocks.DIRT;
            case SAND:
                return Blocks.SAND;
            case GRAVEL:
                return Blocks.GRAVEL;
            case STONE:
                return Blocks.STONE;
            case COBBLE:
                return Blocks.COBBLESTONE;
            case NETHER:
                return Blocks.NETHERRACK;
            case END:
                return Blocks.END_STONE;
            case SNOW:
                return Blocks.SNOW;
            case ICE:
                return Blocks.ICE;
            default:
                return Blocks.DIRT;
        }
    }

    /**
     * The item form of a block, or null where it has none.
     *
     * <p>
     * The other edition asks {@link Item#getItemFromBlock} and tests the answer for null. 1.12.2
     * answers {@code Items.AIR} for a block nobody can hold and never null, so that test stops
     * firing: a ghost block, or anything else registered without an item, would be offered to a
     * picture as an empty stack and drawn as a gap. Named here so that the six places which ask have
     * one answer between them.
     */
    private static Item heldForm(Block block) {
        if (block == null) return null;
        Item item = Item.getItemFromBlock(block);
        return item == null || item == net.minecraft.init.Items.AIR ? null : item;
    }
}
