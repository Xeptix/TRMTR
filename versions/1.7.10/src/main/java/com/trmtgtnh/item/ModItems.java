package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;

import com.trmtgtnh.Trmt;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The mod's items, registered beside its blocks and on the same terms.
 *
 * <p>
 * Registered on both sides for the reason the ghost blocks are: the id map is compared between
 * client and server on connect and has to agree. Nothing here is client-only — the texture name
 * is a plain field that exists on a dedicated server, and the sprite it names is only ever
 * looked up while an atlas is stitched, which no server does.
 *
 * <p>
 * Unlike the ghosts these have a creative tab and can be held, because somebody is meant to.
 */
public final class ModItems {

    /** Every item registered, in registration order, for reporting. */
    private static final List<Item> ALL = new ArrayList<Item>();

    /**
     * One registration, every grade.
     *
     * <p>
     * See {@link ItemChunkTamper} for why the grade lives in the stack instead of in a name.
     */
    private static ItemChunkTamper chunkTamper;

    private static ItemMagicTamper magicTamper;

    /** The hand tamper, in every material the pack has. See {@link ItemGradedTamper}. */
    private static ItemGradedTamper gradedTamper;

    /** The creative-only before-and-after tool. */
    private static ItemDevTool devTool;

    /** The two-slot config snapshot tool. */
    private static ItemSnapshotTool snapshotTool;

    /** One item per golem upgrade, and the egg that puts a golem down. */
    private static final java.util.Map<com.trmtgtnh.entity.GolemUpgrade, ItemGolemUpgrade> UPGRADES = new java.util.EnumMap<com.trmtgtnh.entity.GolemUpgrade, ItemGolemUpgrade>(
        com.trmtgtnh.entity.GolemUpgrade.class);

    private static ItemGolemEgg golemEgg;

    /** One item per guide book, so each can look like itself. */
    private static final java.util.Map<GuideBook, ItemGuideBook> GUIDES = new java.util.EnumMap<GuideBook, ItemGuideBook>(
        GuideBook.class);

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

    public static ItemDevTool devTool() {
        return devTool;
    }

    public static ItemSnapshotTool snapshotTool() {
        return snapshotTool;
    }

    public static ItemGuideBook guide(GuideBook book) {
        return GUIDES.get(book);
    }

    public static ItemGolemUpgrade upgrade(com.trmtgtnh.entity.GolemUpgrade upgrade) {
        return UPGRADES.get(upgrade);
    }

    public static ItemGolemEgg golemEgg() {
        return golemEgg;
    }

    /** One drinkable per draught, keyed so recipes and loot can ask for either by name. */
    private static final java.util.Map<Draughts, ItemDraught> DRAUGHTS = new java.util.EnumMap<Draughts, ItemDraught>(
        Draughts.class);

    /** The bottle that pours this draught, or null before items have been registered. */
    public static ItemDraught draught(Draughts draught) {
        return DRAUGHTS.get(draught);
    }

    public static void register() {
        gradedTamper = new ItemGradedTamper();
        gradedTamper.setUnlocalizedName(Trmt.MODID + ".tamper");
        gradedTamper.setTextureName(Trmt.MODID + ":tamper");
        GameRegistry.registerItem(gradedTamper, "tamper");
        ALL.add(gradedTamper);

        chunkTamper = new ItemChunkTamper();
        chunkTamper.setUnlocalizedName(Trmt.MODID + ".chunk_tamper");
        chunkTamper.setTextureName(Trmt.MODID + ":chunk_tamper");
        GameRegistry.registerItem(chunkTamper, "chunk_tamper");
        ALL.add(chunkTamper);

        magicTamper = new ItemMagicTamper();
        magicTamper.setUnlocalizedName(Trmt.MODID + ".magic_tamper");
        magicTamper.setTextureName(Trmt.MODID + ":magic_tamper");
        GameRegistry.registerItem(magicTamper, "magic_tamper");
        ALL.add(magicTamper);

        devTool = new ItemDevTool();
        devTool.setUnlocalizedName(Trmt.MODID + ".dev_tool");
        GameRegistry.registerItem(devTool, "dev_tool");
        ALL.add(devTool);

        snapshotTool = new ItemSnapshotTool();
        snapshotTool.setUnlocalizedName(Trmt.MODID + ".snapshot_tool");
        GameRegistry.registerItem(snapshotTool, "snapshot_tool");
        ALL.add(snapshotTool);

        golemEgg = new ItemGolemEgg();
        golemEgg.setUnlocalizedName(Trmt.MODID + ".golem_egg");
        GameRegistry.registerItem(golemEgg, "golem_egg");
        ALL.add(golemEgg);

        for (com.trmtgtnh.entity.GolemUpgrade upgrade : com.trmtgtnh.entity.GolemUpgrade.real()) {
            ItemGolemUpgrade item = new ItemGolemUpgrade(upgrade);
            item.setUnlocalizedName(Trmt.MODID + "." + upgrade.itemName());
            GameRegistry.registerItem(item, upgrade.itemName());
            UPGRADES.put(upgrade, item);
            ALL.add(item);
        }

        for (Draughts draught : Draughts.values()) {
            ItemDraught item = new ItemDraught(draught);
            item.setUnlocalizedName(Trmt.MODID + "." + draught.itemName());
            GameRegistry.registerItem(item, draught.itemName());
            DRAUGHTS.put(draught, item);
            ALL.add(item);
        }

        for (GuideBook book : GuideBook.values()) {
            ItemGuideBook item = new ItemGuideBook(book);
            item.setUnlocalizedName(Trmt.MODID + "." + book.itemName());
            GameRegistry.registerItem(item, book.itemName());
            GUIDES.put(book, item);
            ALL.add(item);
        }
    }

}
