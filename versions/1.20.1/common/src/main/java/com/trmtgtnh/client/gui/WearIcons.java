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
     * A surface's road block, and for a family with none - flesh and taint, which no vanilla block is - the first block
     * of it the running pack holds (0.9.229, W4 T17; the rig's settings-advanced-2 in FTB Infinity drew the Advanced
     * fold's two rows with no picture). Null where the pack holds none. The settings screen asks
     * {@link #familyPicture}, whose first two answers these are.
     */
    public static Block familyBlock(SurfaceFamily family) {
        Block road = com.trmtgtnh.command.CommandTrmt.roadBlock(family);
        return road != null ? road : heldBlock(family);
    }

    /** The first block of a family the running game holds - by detection, a data file or the player's list - or null. */
    private static Block heldBlock(SurfaceFamily family) {
        for (com.trmtgtnh.surface.SurfaceRegistry.SurfaceState state : com.trmtgtnh.surface.SurfaceRegistry
            .texturableStates()) {
            if (state.family == family) return state.block;
        }
        return null;
    }

    /**
     * The picture a family is shown by in the settings screen - its Surfaces sub-tab (spec M1.2), its Advanced fold row
     * (M16.2) and its chip in a list of families (C1) - and never nothing (0.9.229; Xep, from GT New Horizons: the
     * Flesh sub-tab was an empty square, and Leaves and Vegetation were dirt). The sub-tabs drew the road block, which
     * flesh, taint, cloud and, before 1.19, sculk have none of, and which for leaves and vegetation is dirt, the ground
     * their yard is laid on. So: the family's own vanilla block where it is one; else the first block of it the running
     * game holds; else its {@link #standIn}. A block or an item, as {@code SettingsCanvas.picture} takes either.
     */
    public static Object familyPicture(SurfaceFamily family) {
        // Leaves and vegetation have no road of their own: roadBlock's dirt for them is the ground their yard is on.
        Block road = family.staged ? com.trmtgtnh.command.CommandTrmt.roadBlock(family) : null;
        if (road != null) return road;
        Block held = heldBlock(family);
        return held != null ? held : standIn(family);
    }

    /**
     * What a family is pictured by where it is no vanilla block of the game and the game holds none of it, the same in
     * every edition (0.9.229). Oak leaves and a poppy, the commonest of each. Rotten flesh, which says flesh at a
     * glance; not soul sand, flesh's ghost twin, which was chosen for how it walks and looks like the Nether's ground.
     * Mycelium, taint's ghost twin, a purple crust spread over the earth. Obsidian, sculk's ghost twin, the nearest
     * vanilla face to it that every edition has (from 1.19 sculk is vanilla's own block, so this is never asked there).
     * White wool, the cloud's twin. Null for a family that is a vanilla block.
     */
    static Object standIn(SurfaceFamily family) {
        switch (family) {
            case LEAVES:
                return Blocks.OAK_LEAVES;
            case VEGETATION:
                return Blocks.POPPY;
            case FLESH:
                return net.minecraft.world.item.Items.ROTTEN_FLESH;
            case TAINT:
                return Blocks.MYCELIUM;
            case SCULK:
                return Blocks.OBSIDIAN;
            case CLOUD:
                return Blocks.WHITE_WOOL;
            default:
                return null;
        }
    }

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
            ItemStack picture = pictureOf(state.block, state.family);
            if (picture != null) list.add(picture);
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
     * The picture of a detected block (0.9.223). The older editions pick the first of a block's variants that wears as
     * its family, because one block there holds many: Chisel numbers its carved cobblestone from 1, and drawing every
     * block as its variant 0 put the missing-texture square in 1.7.10's Wear Table. A block here is one variant, so its
     * picture is the block; kept as one method of the same name so the three harnesses ask the same question.
     */
    public static ItemStack pictureOf(Block block, SurfaceFamily family) {
        Item item = heldForm(block);
        return item == null ? null : new ItemStack(item);
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
            case SCULK:
                // Vanilla's own from 1.19, so it has a road, a yard platform and a row to open on (0.9.229, W4).
                return Blocks.SCULK;
            case FLESH:
            case TAINT:
            case CLOUD:
                // No vanilla block is flesh, taint or cloud (0.9.229, W4 T17; cloud, W4): a row with none of its
                // own detected shows nothing rather than another family's block.
                return null;
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

    /** Whether a key's picture has died once and is no longer drawn: the settings screen's budget asks it (Part 6). */
    static boolean broken(String key) {
        return key != null && BROKEN.contains(key);
    }

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
    public static void drawIcon(net.minecraft.client.gui.GuiGraphics graphics, String key, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty() || (key != null && BROKEN.contains(key))) return;
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.getItemRenderer() == null) return;
        try {
            graphics.renderItem(stack, x, y);
        } catch (Throwable hostileRenderer) {
            // A name can reach any mod in the pack. Something in here throwing is a reason to draw
            // nothing, and to go on drawing nothing, never a reason to take the screen down.
            if (key != null) BROKEN.add(key);
        }
    }
}
