package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;


/**
 * The example blocks each family gets shown as, resolved once and cycled through.
 *
 * <p>
 * The Wear Table and its editor both want a picture of a family that changes every second, so the
 * lists live here rather than being built twice. A family with nothing detected falls back to its
 * vanilla stand-in, so a row is never iconless.
 */
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
            if (item != null) list.add(new ItemStack(item));
        }
        for (SurfaceFamily family : built.keySet()) {
            List<ItemStack> list = built.get(family);
            if (list.isEmpty()) {
                Block fallback = vanillaFor(family);
                Item item = heldForm(fallback);
                if (item != null) list.add(new ItemStack(item));
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
        ItemStack made = item == null ? null : new ItemStack(item);
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
                return Blocks.GRASS_BLOCK;
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
                return Blocks.SNOW_BLOCK;
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
     * The other editions ask {@code Item.getItemFromBlock}: 1.7.10 gets null for a block nobody can
     * hold and 1.12.2 gets {@code Items.AIR}, which is why that class tests for both - a ghost block,
     * or anything else registered without an item, would otherwise be offered to a picture as an
     * empty stack and drawn as a gap. A block answers for itself at this version, which is the first
     * time the question has had one shape, and the test for air stays because that is still the
     * answer a block with no item form gives. Named here so that the six places which ask have one
     * answer between them.
     */
    private static Item heldForm(Block block) {
        if (block == null) return null;
        Item item = block.asItem();
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    /** Keys whose picture died on the way out, so it is never asked for twice. */
    private static final java.util.Set<String> BROKEN =
        java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * Draws one sixteen-pixel icon of a block, and gives up on a block that will not be drawn.
     *
     * <p>
     * <strong>This is {@code BlockIconArrayEntry.drawIcon} with the ceremony taken off, and the
     * ceremony is worth describing because losing it is the whole change.</strong> Both older
     * editions wrap this one call in a push of the GL attribute stack, a record of the matrix stack's
     * depth, an unwind loop that pops whatever the block's own renderer forgot to, a tessellator
     * flush for a renderer that died part way through a quad, and a pair of state-manager calls to
     * tell the manager what popping the attribute stack did behind its back. Every line of that is
     * fixed-function OpenGL, and a block icon there is a live isometric render that hands control to
     * whatever renderer the block's mod registered - so in a pack of any size it was a lot of
     * third-party code running inside a screen.
     *
     * <p>
     * None of those calls exist at this version and none of what they guarded against can happen:
     * there is no attribute stack, no shared matrix stack to leave unbalanced, and an item is drawn
     * through a buffer source that is flushed by whoever owns it. {@code renderAndDecorateItem}
     * already catches a model that will not build and reports it itself. What is left worth keeping
     * is the last of it - a block that throws is remembered, so a screen that cycles once a second
     * does not throw once a second.
     *
     * <p>
     * Here rather than in the config screen it came from, because it was always shared: the wear
     * table, its editor and both config pickers all want exactly this, and the config pickers are
     * the part of that list this edition does not keep.
     */
    public static void drawIcon(String key, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty() || (key != null && BROKEN.contains(key))) return;
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.getItemRenderer() == null) return;
        try {
            client.getItemRenderer()
                .renderAndDecorateItem(stack, x, y);
        } catch (Throwable hostileRenderer) {
            // A name can reach any mod in the pack. Something in here throwing is a reason to draw
            // nothing, and to go on drawing nothing, never a reason to take the screen down.
            if (key != null) BROKEN.add(key);
        }
    }
}
