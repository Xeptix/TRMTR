package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.trmtgtnh.Tags;
import com.trmtgtnh.Trmt;

/**
 * The mod's items, registered beside its blocks and on the same terms.
 *
 * <p>
 * Every item the other edition registers here is here.
 *
 * <p>
 * Registered on both sides for the reason the ghost blocks are: what a save records is what a world
 * expects to find, and a client and a server that disagree about the set do not connect. Nothing here
 * is client-only - which models an item can be drawn as is decided in the client proxy, from the same
 * list, and a dedicated server never asks the question.
 *
 * <p>
 * Unlike the ghosts these have a creative tab and can be held, because somebody is meant to.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModItems {

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

    /** One item per golem upgrade, and the egg that puts a golem down. */
    private static final java.util.Map<com.trmtgtnh.entity.GolemUpgrade, ItemGolemUpgrade> UPGRADES = new java.util.EnumMap<com.trmtgtnh.entity.GolemUpgrade, ItemGolemUpgrade>(
        com.trmtgtnh.entity.GolemUpgrade.class);

    private static ItemGolemEgg golemEgg;

    /** One book per guide, keyed so loot and recipes can ask for one by name. */
    private static final java.util.Map<GuideBook, ItemGuideBook> GUIDES = new java.util.EnumMap<GuideBook, ItemGuideBook>(
        GuideBook.class);

    /** The two tools that exist for whoever is tuning the mod rather than for whoever is playing it. */
    private static ItemDevTool devTool;

    private static ItemSnapshotTool snapshotTool;

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

    /** The bottle that pours this draught, or null before items have been registered. */
    public static ItemDraught draught(Draughts draught) {
        return DRAUGHTS.get(draught);
    }

    /** The fitting for this upgrade, or null for the empty one and before registration. */
    public static ItemGolemUpgrade upgrade(com.trmtgtnh.entity.GolemUpgrade upgrade) {
        return UPGRADES.get(upgrade);
    }

    public static ItemGolemEgg golemEgg() {
        return golemEgg;
    }

    /** The book for this guide, or null before items have been registered. */
    public static ItemGuideBook guide(GuideBook book) {
        return GUIDES.get(book);
    }

    public static ItemDevTool devTool() {
        return devTool;
    }

    public static ItemSnapshotTool snapshotTool() {
        return snapshotTool;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        gradedTamper = new ItemGradedTamper();
        add(event, gradedTamper, "tamper");

        chunkTamper = new ItemChunkTamper();
        add(event, chunkTamper, "chunk_tamper");

        magicTamper = new ItemMagicTamper();
        add(event, magicTamper, "magic_tamper");

        for (Draughts draught : Draughts.values()) {
            ItemDraught bottle = new ItemDraught(draught);
            add(event, bottle, draught.itemName());
            DRAUGHTS.put(draught, bottle);
        }

        for (GuideBook book : GuideBook.values()) {
            ItemGuideBook guide = new ItemGuideBook(book);
            add(event, guide, book.itemName());
            GUIDES.put(book, guide);
        }

        devTool = new ItemDevTool();
        add(event, devTool, "dev_tool");

        snapshotTool = new ItemSnapshotTool();
        add(event, snapshotTool, "snapshot_tool");

        golemEgg = new ItemGolemEgg();
        add(event, golemEgg, "golem_egg");

        // The real ones, which is the enum without its empty first value: nothing fitted is a state
        // a golem can be in, not a thing anybody can hold.
        for (com.trmtgtnh.entity.GolemUpgrade upgrade : com.trmtgtnh.entity.GolemUpgrade.real()) {
            ItemGolemUpgrade fitting = new ItemGolemUpgrade(upgrade);
            add(event, fitting, upgrade.itemName());
            UPGRADES.put(upgrade, fitting);
        }
    }

    /**
     * One item, named the two ways 1.12.2 asks for.
     *
     * <p>
     * The registry name is the save's own record and the same string the other edition passed to
     * {@code GameRegistry.registerItem}; the translation key - what 1.7.10 called the unlocalized
     * name - is only what the language file keys on, and keeps the dot the other edition used so
     * that one set of translations serves both.
     */
    private static void add(RegistryEvent.Register<Item> event, Item item, String name) {
        item.setRegistryName(new ResourceLocation(Trmt.MODID, name));
        item.setTranslationKey(Trmt.MODID + "." + name);
        event.getRegistry()
            .register(item);
        ALL.add(item);
    }
}
