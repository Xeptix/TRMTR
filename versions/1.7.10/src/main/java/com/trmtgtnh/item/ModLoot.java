package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.WayfarerCategories;

/**
 * The mod's things, findable while exploring.
 *
 * <p>
 * Additive and deliberately tiny. Forge's chest hooks add to a category's weighted pool and never
 * remove from it, so nothing that was already findable stops being findable. Adding to a weighted
 * pool does make every other entry a hair less likely - that is arithmetic, not a choice - but at
 * weights of one or two against pools totalling in the hundreds, each existing item's odds shift
 * by a fraction of a percent. That is the intent: a small bonus in the mix, not a different game.
 *
 * <p>
 * Where each thing turns up is meant to read. Tampers are tools, so they sit with tools - dungeons,
 * mineshafts, a smith's chest. The books belong in a library. The Wayfarer is the end of the ladder
 * and appears only in a stronghold, which is the structure that leads to the End, and only just -
 * unless a pack names somewhere else for it.
 *
 * <p>
 * Every weight is a setting, and a weight of zero simply leaves that thing out - so a packmaker can
 * keep the books and drop the tools, or turn the lot off with one switch.
 *
 * <p>
 * That one switch is lootFinds. The pack personality switch, gtnhEnhanced, is not read here, on
 * purpose: nothing a chest is given needs a GregTech pack, being this mod's own items or vanilla's
 * enchanted book carrying one of its enchantments, so a pack set to behave as plain Forge finds them as
 * well. Its comment once promised otherwise; the comment was wrong, not this. Everything is filed once
 * a session - from postInit, and the Wayfarer's fallback as the first server starts - and never again,
 * which is why no setting read here follows /trmt reload.
 */
public final class ModLoot {

    private ModLoot() {}

    public static void register() {
        if (!TrmtConfig.lootFinds) return;

        ItemStack tamper = starter(ModItems.gradedTamper());
        ItemStack chunk = starter(ModItems.chunkTamper());

        add(
            tamper,
            TrmtConfig.lootWeightTamper,
            ChestGenHooks.DUNGEON_CHEST,
            ChestGenHooks.MINESHAFT_CORRIDOR,
            ChestGenHooks.VILLAGE_BLACKSMITH);
        add(chunk, TrmtConfig.lootWeightChunkTamper, ChestGenHooks.DUNGEON_CHEST, ChestGenHooks.STRONGHOLD_CROSSING);

        if (ModItems.magicTamper() != null) {
            // The last rung. Somewhere pointing at the End would suit it best, but 1.7.10 has no
            // End structure with a loot category of its own - the stronghold library is the nearest
            // thing there is, being both the rarest vanilla pool and the room with the portal in it.
            // A pack that has an End structure worth naming can name its category in config. Forge
            // takes a name nothing generates from without a word, so settleWayfarer looks again once
            // every mod has loaded, and puts it in the library too when none of them turn out to be real.
            List<String> named = WayfarerCategories
                .named(TrmtConfig.lootWayfarerCategories, ChestGenHooks.STRONGHOLD_LIBRARY);
            int weight = TrmtConfig.lootWeightWayfarer;
            if (add(new ItemStack(ModItems.magicTamper()), weight, named.toArray(new String[named.size()]))) {
                wayfarerFiled = named;
                wayfarerWeight = weight;
            }
        }

        // The draughts, on the same terms as everything else here: weights of one and two
        // against pools that total in the hundreds. Lightness turns up where a traveller's kit
        // would - dungeons, mineshafts and a smith's chest - and its opposite only in the deep
        // places, being the stranger thing for anybody to have brewed on purpose.
        if (TrmtConfig.potionsEnabled) {
            ItemDraught light = ModItems.draught(Draughts.LIGHTNESS);
            ItemDraught heavy = ModItems.draught(Draughts.HEAVYFOOT);
            if (light != null && TrmtConfig.potionLightness) {
                add(
                    new ItemStack(light),
                    TrmtConfig.lootWeightDraughtLight,
                    ChestGenHooks.DUNGEON_CHEST,
                    ChestGenHooks.MINESHAFT_CORRIDOR,
                    ChestGenHooks.VILLAGE_BLACKSMITH);
            }
            if (heavy != null && TrmtConfig.potionHeavyFoot) {
                add(
                    new ItemStack(heavy),
                    TrmtConfig.lootWeightDraughtHeavy,
                    ChestGenHooks.DUNGEON_CHEST,
                    ChestGenHooks.STRONGHOLD_CROSSING);
            }
        }

        addBook(
            GuideBook.MK1,
            TrmtConfig.lootWeightGuide,
            ChestGenHooks.DUNGEON_CHEST,
            ChestGenHooks.MINESHAFT_CORRIDOR,
            ChestGenHooks.VILLAGE_BLACKSMITH,
            ChestGenHooks.STRONGHOLD_LIBRARY);
        addBook(
            GuideBook.MK2,
            TrmtConfig.lootWeightGuideRare,
            ChestGenHooks.STRONGHOLD_LIBRARY,
            ChestGenHooks.DUNGEON_CHEST);
        addBook(
            GuideBook.COMMANDS,
            TrmtConfig.lootWeightGuideRare,
            ChestGenHooks.STRONGHOLD_LIBRARY,
            ChestGenHooks.VILLAGE_BLACKSMITH);
        addBook(
            GuideBook.GOLEM,
            TrmtConfig.lootWeightGuideRare,
            ChestGenHooks.STRONGHOLD_LIBRARY,
            ChestGenHooks.DUNGEON_CHEST);

        addGolemFinds();
        addEnchantBook(EnchReinforce.INSTANCE, TrmtConfig.reinforceEnabled);
        addEnchantBook(EnchWard.INSTANCE, TrmtConfig.wardEnabled);
        addEnchantBook(EnchLight.INSTANCE, TrmtConfig.lightEnabled);

        Trmt.LOG.info("Added the mod's finds to the world's chests");
    }

    /**
     * The golem's upgrades and, rarest of all, a dormant golem.
     *
     * <p>
     * The egg is the one thing here that skips a whole progression rather than shortening it, so it
     * sits alone in a stronghold library at the lowest weight the mod uses - a find somebody tells
     * other people about.
     */
    private static void addGolemFinds() {
        if (!TrmtConfig.golemEnabled) return;

        if (TrmtConfig.golemUpgrades) {
            for (com.trmtgtnh.entity.GolemUpgrade upgrade : com.trmtgtnh.entity.GolemUpgrade.real()) {
                ItemGolemUpgrade item = ModItems.upgrade(upgrade);
                if (item == null) continue;
                // One find for the whole-set tier rather than two, and it is whichever one a bench
                // can currently make: a library holding the loose part in a pack that cannot craft
                // one is a dead end, and a library holding both is twice the odds of a tier that
                // was priced to be found once.
                if (upgrade.carriesTheSet()) {
                    boolean loose = upgrade == com.trmtgtnh.entity.GolemUpgrade.UNSTABLE;
                    if (loose != TrmtConfig.golemUnstableOmni) continue;
                    add(new ItemStack(item), TrmtConfig.lootWeightOmni, ChestGenHooks.STRONGHOLD_LIBRARY);
                } else {
                    int weight = TrmtConfig.lootWeightUpgrade;
                    add(new ItemStack(item), weight, ChestGenHooks.DUNGEON_CHEST, ChestGenHooks.MINESHAFT_CORRIDOR);
                }
            }
        }

        if (ModItems.golemEgg() != null) {
            add(new ItemStack(ModItems.golemEgg()), TrmtConfig.lootWeightGolemEgg, ChestGenHooks.STRONGHOLD_LIBRARY);
        }
    }

    /** A found tool is a starter one, not whatever the pack's best metal happens to be. */
    private static ItemStack starter(net.minecraft.item.Item item) {
        if (item == null) return null;
        ItemStack stack = new ItemStack(item);
        TamperGrade grade = TamperGrade.available()
            .get(0);
        if (grade != null) ItemChunkTamper.setGrade(stack, grade);
        return stack;
    }

    private static void addBook(GuideBook book, int weight, String... categories) {
        ItemGuideBook item = ModItems.guide(book);
        if (item != null) add(new ItemStack(item), weight, categories);
    }

    private static void addEnchantBook(net.minecraft.enchantment.Enchantment enchantment, boolean enabled) {
        if (!enabled || enchantment == null) return;
        ItemStack stack = new ItemStack(net.minecraft.init.Items.enchanted_book);
        net.minecraft.init.Items.enchanted_book
            .addEnchantment(stack, new net.minecraft.enchantment.EnchantmentData(enchantment, 1));
        int weight = TrmtConfig.lootWeightEnchantBook;
        if (weight <= 0) return;
        // Not through add, which files an ordinary entry. See PreparedBook for why that one never
        // produced the book it was given.
        for (String category : new String[] { ChestGenHooks.STRONGHOLD_LIBRARY, ChestGenHooks.DUNGEON_CHEST }) {
            ChestGenHooks info = ChestGenHooks.getInfo(category);
            if (info != null) info.addItem(new PreparedBook(stack, weight));
        }
    }

    /** Files a thing under each category, and answers whether it filed it anywhere. */
    private static boolean add(ItemStack stack, int weight, String... categories) {
        if (stack == null || stack.getItem() == null || weight <= 0) return false;
        for (String category : categories) {
            // Never null: Forge makes an empty pool for a name nobody has used and hands that back,
            // which is why settleWayfarer looks at the Wayfarer's names again.
            ChestGenHooks.getInfo(category)
                .addItem(new WeightedRandomChestContent(stack.copy(), 1, 1, weight));
        }
        return categories.length > 0;
    }

    /** The categories the Wayfarer was filed under as the game started, repeats kept, or null when it was not filed. */
    private static volatile List<String> wayfarerFiled;

    /** The weight it was filed at, so a fallback matches that rather than a later reload. */
    private static volatile int wayfarerWeight;

    /** Set by the first look, so a second world in one session neither looks again nor files twice. */
    private static final AtomicBoolean wayfarerSettled = new AtomicBoolean();

    /** Forge's own categories, for naming the one a name in config was probably meant to be. */
    private static final String[] FORGE_CATEGORIES = { ChestGenHooks.MINESHAFT_CORRIDOR,
        ChestGenHooks.PYRAMID_DESERT_CHEST, ChestGenHooks.PYRAMID_JUNGLE_CHEST, ChestGenHooks.PYRAMID_JUNGLE_DISPENSER,
        ChestGenHooks.STRONGHOLD_CORRIDOR, ChestGenHooks.STRONGHOLD_LIBRARY, ChestGenHooks.STRONGHOLD_CROSSING,
        ChestGenHooks.VILLAGE_BLACKSMITH, ChestGenHooks.BONUS_CHEST, ChestGenHooks.DUNGEON_CHEST };

    /**
     * Makes sure the Wayfarer is somewhere a chest is actually filled from, and says where it is not.
     *
     * <p>
     * Register cannot tell a misspelt category from a real one, because Forge makes an empty pool for any
     * name rather than refusing it, and a pack whose every name was wrong had no Wayfarer anywhere. A real
     * pool is told from a made one by what other mods did to it, which is known only once they have all
     * loaded: see WayfarerCategories.
     *
     * <p>
     * Called as a server is about to start: after every mod's loading, and before any world exists. The
     * starting event is too late, and so is a world's load event, since a new world places its bonus chest
     * while it is being built and generates its spawn area before the server starts. Once a session.
     * Nothing is taken out of any pool, and a pool that cannot be read is taken as real, because anything
     * thrown out of this event stops the server starting. Does nothing when the Wayfarer was never filed -
     * loot switched off, or a weight of nought.
     */
    public static void settleWayfarer() {
        List<String> filed = wayfarerFiled;
        if (filed == null || !wayfarerSettled.compareAndSet(false, true)) return;
        Item wayfarer = ModItems.magicTamper();

        List<String> unused = new ArrayList<String>();
        for (String category : new LinkedHashSet<String>(filed)) {
            ChestGenHooks info = ChestGenHooks.getInfo(category);
            int others = entriesOtherThan(info, wayfarer, category);
            if (WayfarerCategories.unused(info.getMin(), info.getMax(), others)) unused.add(category);
        }
        for (String category : unused) {
            String meant = WayfarerCategories.nearMiss(category, FORGE_CATEGORIES);
            Trmt.LOG.warn(
                "loot.wayfarerCategories names '{}', and as the server started that chest pool held nothing but the Wayfarer's Tamper and had no chest size, so no chest is likely to be filled from it. {}",
                category,
                meant == null
                    ? "Check the spelling, which is matched exactly, case included, and that the mod whose structures use it is installed"
                    : "Names are matched exactly, case included - did you mean '" + meant + "'?");
        }
        if (WayfarerCategories.fallsBack(filed, unused, ChestGenHooks.STRONGHOLD_LIBRARY)) {
            ChestGenHooks.getInfo(ChestGenHooks.STRONGHOLD_LIBRARY)
                .addItem(new WeightedRandomChestContent(new ItemStack(wayfarer), 1, 1, wayfarerWeight));
            Trmt.LOG.warn(
                "None of the chest pools loot.wayfarerCategories names is one anything fills, so the Wayfarer's Tamper is filed in the stronghold library too, where it is found by default. A loot.wayfarer weight of 0 is the way to have it found nowhere");
        }
    }

    /**
     * How many of a pool's entries are anything but the Wayfarer, or one when they cannot be read.
     *
     * <p>
     * getItems is the only public reading of a pool, and it runs each entry's own chest hook - for an
     * enchanted book, a fresh book rolled from the throwaway random here, and nothing of that is kept. A
     * modded hook can throw this early, with no world yet, so a pool that cannot be read is taken as one
     * somebody else fills, which it must be: the Wayfarer's own hook is vanilla's and does not throw.
     */
    private static int entriesOtherThan(ChestGenHooks info, Item wayfarer, String category) {
        WeightedRandomChestContent[] entries;
        try {
            entries = info.getItems(new java.util.Random(0L));
        } catch (RuntimeException e) {
            Trmt.LOG.warn(
                "Could not read the chest pool '{}' named in loot.wayfarerCategories ({}); taking it as one another mod fills",
                category,
                e.toString());
            return 1;
        }
        int others = 0;
        for (WeightedRandomChestContent entry : entries) {
            if (entry == null || entry.theItemId == null || entry.theItemId.getItem() != wayfarer) others++;
        }
        return others;
    }

    /**
     * A chest entry that hands over one particular enchanted book.
     *
     * <p>
     * An ordinary entry cannot. Forge rebuilds every entry whose item is the enchanted book each time
     * a chest's pool is drawn from, throwing away whatever enchantment it carried and rolling a
     * random one at level thirty in its place - and at that level a random roll can never land on
     * any of this mod's three, whose enchantability tops out far below it. So the unlock books this
     * mod added to the dungeon and library pools were in every one of them and never came out of a
     * single chest.
     *
     * <p>
     * Filed under the plain book instead, which Forge leaves exactly as it found it, and the prepared
     * book is produced only when a chest is actually being filled. A mod that lists a pool's
     * contents by item will show a plain book here, which is the cost of it and a small one.
     */
    private static final class PreparedBook extends WeightedRandomChestContent {

        private final ItemStack book;

        PreparedBook(ItemStack book, int weight) {
            super(new ItemStack(net.minecraft.init.Items.book), 1, 1, weight);
            this.book = book.copy();
        }

        @Override
        protected ItemStack[] generateChestContent(java.util.Random random,
            net.minecraft.inventory.IInventory inventory) {
            return new ItemStack[] { book.copy() };
        }
    }

}
