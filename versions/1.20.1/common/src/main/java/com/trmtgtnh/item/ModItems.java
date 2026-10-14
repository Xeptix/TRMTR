package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.trmtgtnh.Trmt;

/**
 * Where the items are made, and the seam that lets two loaders register them.
 *
 * <p>
 * The 1.12.2 edition keeps this as a Forge registry event. There is no such event in a module two
 * loaders share, so this is the arrangement {@code ModBlocks}, {@code ModPotions} and {@code
 * ModEnchantments} are already under: the items are made here and handed to whatever the loader
 * supplies for putting them away.
 *
 * <p>
 * Each is made once however often this is called, and it is called more than once - Forge fires a
 * separate event per registry and each comes back through here. A second instance would leave the
 * registry holding one item while every {@code instanceof} test in the mod asked about another.
 *
 * <h2>What is not here yet</h2>
 *
 * <p>
 * The other edition registers ten kinds of item; three are here. The seven that are not are the
 * draughts, the golem's egg and its upgrades, the guide books, and the two developer tools - each
 * waiting on a milestone of its own rather than on a decision, and each named in
 * {@code PortProgressTest}. The accessors they are asked through are not stubbed out: a caller that
 * wants one is a compile error until the item arrives, which is the only way this stays honest.
 */
public final class ModItems {

    /** What a loader module supplies: somewhere to put an item under a name. */
    public interface Registrar {

        void item(ResourceLocation name, Item item);

        /**
         * The three tampers this loader wants registered.
         *
         * <p>
         * Almost always the common ones, which is why those are the defaults. Forge is the exception
         * and the reason this exists: three of the things a tamper has to answer are Forge's own -
         * how much damage <em>this stack</em> may take, whether the grid may repair it, and whether a
         * left-click is a dig - and none of those can be declared in a module both loaders share. The
         * Forge side returns subclasses that add them and nothing else. {@code ModBlocks.Registrar}
         * carried a factory of the same shape for a while and no longer does, which says when the
         * arrangement is worth making: these three are overridden, and that one never was.
         */
        default ItemGradedTamper makeGradedTamper() {
            return new ItemGradedTamper();
        }

        default ItemChunkTamper makeChunkTamper() {
            return new ItemChunkTamper();
        }

        default ItemMagicTamper makeMagicTamper() {
            return new ItemMagicTamper();
        }
    }

    /** Every item registered, in registration order, for reporting. */
    private static final List<Item> ALL = new ArrayList<Item>();

    /** The hand tamper, in every material the pack has. See {@link ItemGradedTamper}. */
    private static ItemGradedTamper gradedTamper;

    /**
     * One registration, every grade.
     *
     * <p>
     * See {@link ItemChunkTamper} for why the grade lives in the stack instead of in a name.
     */
    private static ItemChunkTamper chunkTamper;

    /** The end of the tamper line, and the only one of the three with a single picture. */
    private static ItemMagicTamper magicTamper;

    /** One drinkable per draught, keyed so recipes and loot can ask for either by name. */
    private static final java.util.Map<Draughts, ItemDraught> DRAUGHTS = new java.util.EnumMap<Draughts, ItemDraught>(
        Draughts.class);

    /** One item per golem upgrade. */
    private static final java.util.Map<com.trmtgtnh.entity.GolemUpgrade, ItemGolemUpgrade> UPGRADES =
        new java.util.EnumMap<com.trmtgtnh.entity.GolemUpgrade, ItemGolemUpgrade>(
            com.trmtgtnh.entity.GolemUpgrade.class);

    /** The two tools that exist for whoever is tuning the mod rather than for whoever is playing it. */
    private static ItemDevTool devTool;

    private static ItemSnapshotTool snapshotTool;

    /**
     * A Golem of Ways in the hand, waiting to be put down.
     *
     * <p>
     * This mod's own item rather than a vanilla spawn egg, which is the same choice all three
     * editions make and for the same reason: a vanilla egg wants a global entity id, and those are
     * worth more than this. It also means the rarest find in the mod gets to look like the thing it
     * makes.
     */
    private static ItemGolemEgg golemEgg;

    /** One item per guide book, keyed so the loot tables and the first-join grant can ask by name. */
    private static final java.util.Map<GuideBook, ItemGuideBook> BOOKS =
        new java.util.EnumMap<GuideBook, ItemGuideBook>(GuideBook.class);

    private ModItems() {}

    public static List<Item> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static ItemGradedTamper gradedTamper() {
        return gradedTamper;
    }

    public static ItemChunkTamper chunkTamper() {
        return chunkTamper;
    }

    public static ItemMagicTamper magicTamper() {
        return magicTamper;
    }

    /** The drinkable for this draught, or null before registration. */
    public static ItemDraught draught(Draughts which) {
        return which == null ? null : DRAUGHTS.get(which);
    }

    /** The item for this upgrade, or null for NONE and before registration. */
    public static ItemGolemUpgrade upgrade(com.trmtgtnh.entity.GolemUpgrade which) {
        return which == null ? null : UPGRADES.get(which);
    }

    public static ItemDevTool devTool() {
        return devTool;
    }

    public static ItemSnapshotTool snapshotTool() {
        return snapshotTool;
    }

    public static ItemGolemEgg golemEgg() {
        return golemEgg;
    }

    /** The item for this guide book, or null before registration. */
    public static ItemGuideBook guide(GuideBook which) {
        return which == null ? null : BOOKS.get(which);
    }

    /** Makes the three and hands them over. */
    public static void register(Registrar into) {
        ALL.clear();
        if (gradedTamper == null) gradedTamper = into.makeGradedTamper();
        if (chunkTamper == null) chunkTamper = into.makeChunkTamper();
        if (magicTamper == null) magicTamper = into.makeMagicTamper();
        if (devTool == null) devTool = new ItemDevTool();
        if (snapshotTool == null) snapshotTool = new ItemSnapshotTool();
        if (golemEgg == null) golemEgg = new ItemGolemEgg();

        put(into, "tamper", gradedTamper);
        put(into, "chunk_tamper", chunkTamper);
        put(into, "magic_tamper", magicTamper);
        put(into, "dev_tool", devTool);
        put(into, "snapshot_tool", snapshotTool);
        put(into, "golem_egg", golemEgg);

        for (Draughts which : Draughts.values()) {
            if (!DRAUGHTS.containsKey(which)) DRAUGHTS.put(which, new ItemDraught(which));
            put(into, which.itemName(), DRAUGHTS.get(which));
        }

        for (GuideBook which : GuideBook.values()) {
            if (!BOOKS.containsKey(which)) BOOKS.put(which, new ItemGuideBook(which));
            put(into, which.itemName(), BOOKS.get(which));
        }

        // Every upgrade but NONE, which is the absence of one and has no item.
        for (com.trmtgtnh.entity.GolemUpgrade which : com.trmtgtnh.entity.GolemUpgrade.values()) {
            if (which == com.trmtgtnh.entity.GolemUpgrade.NONE) continue;
            if (!UPGRADES.containsKey(which)) UPGRADES.put(which, new ItemGolemUpgrade(which));
            put(into, which.itemName(), UPGRADES.get(which));
        }

        Trmt.LOG.info("Registered {} item(s)", Integer.valueOf(ALL.size()));
    }

    private static void put(Registrar into, String name, Item item) {
        into.item(new ResourceLocation(Trmt.MODID, name), item);
        ALL.add(item);
    }
}
