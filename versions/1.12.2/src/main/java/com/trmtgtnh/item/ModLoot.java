package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetNBT;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.WayfarerCategories;

/**
 * The mod's things, findable while exploring.
 *
 * <p>
 * Additive, and the other edition's weights exactly. Each find joins a pool a chest is already drawn
 * from and nothing is ever taken out of one, so nothing that was already findable stops being
 * findable.
 *
 * <p>
 * <strong>They are not as rare as the other edition's comment says, and nor are its own.</strong> That
 * comment has them at weights of one or two against pools totalling in the hundreds, each existing
 * item's odds shifting by a fraction of a percent. Measured, this mod's seventeen dungeon entries come
 * to 35 against vanilla's 127 - <strong>a fifth of what a dungeon chest gives out</strong> - and 28
 * against 71 in a mineshaft, which is more than a quarter. The figures for the other edition are the
 * same, because 1.7.10's pools are the same size: its dungeon totals 119 where this one totals 127,
 * its mineshaft 79 where this one is 71, its smith's chest about 90 against 94. So the port is
 * faithful and the sentence was wrong in both places.
 *
 * <p>
 * Nothing is changed about the weights here on the strength of that. They are the other edition's
 * numbers, every one of them is a setting, and making this edition's finds several times rarer than
 * the mod it is a port of would be a balance decision wearing a bug fix's clothes. What is recorded
 * instead is the real figure, so a packmaker who wants the small bonus the sentence described knows
 * roughly what to divide by.
 *
 * <p>
 * Where each thing turns up is meant to read, and the places are the other edition's. Tampers are
 * tools, so they sit with tools - dungeons, mineshafts, a smith's chest. The books belong in a library.
 * The Wayfarer is the end of the ladder and appears only in a stronghold, which is the structure that
 * leads to the End, and only just - unless a pack names somewhere else for it.
 *
 * <p>
 * Every weight is a setting, and a weight of zero simply leaves that thing out - so a packmaker can
 * keep the books and drop the tools, divide the lot by five, or turn them off with one switch. That
 * one switch is
 * {@code lootFinds}. The pack personality switch, {@code gtnhEnhanced}, is not read here, on purpose:
 * nothing a chest is given needs a GregTech pack, being this mod's own items or vanilla's enchanted
 * book carrying one of its enchantments, so a pack set to behave as plain Forge finds them as well.
 *
 * <h2>A different mechanism, and a simpler one</h2>
 *
 * <p>
 * The other edition adds weighted entries to Forge's chest categories, once, at post-init. 1.12.2 has
 * no such thing: a chest is filled from a loot table, tables are loaded per world, and the way in is
 * {@code LootTableLoadEvent}, which hands over each table as it is read. So where the other edition
 * files everything once a session, this answers the same question once per table per load - and every
 * weight is therefore read live rather than frozen at startup, which is a small gain nobody asked for.
 *
 * <p>
 * <strong>Entries, not a pool.</strong> Each find joins the table's existing {@code main} pool rather
 * than arriving in a pool of its own, because a new pool would be an extra roll - a chest that gives
 * out more than it used to - where an entry is a share of the roll a chest already makes. The second is
 * what the other edition does and what the paragraph above describes.
 *
 * <p>
 * <strong>Two things the other edition needed are gone.</strong> Its {@code PreparedBook} existed
 * because Forge rebuilds any chest entry whose item is the enchanted book, throwing away the
 * enchantment it was given and rolling a random one at level thirty - at which level none of this
 * mod's three can ever come up, so its unlock books were in every pool and came out of no chest. A
 * loot entry here carries a {@code SetNBT} function and hands over precisely the stack it was
 * described with. And its {@code settleWayfarer} has to infer whether a named category is real, by
 * reading the pool and counting what else is in it, because Forge invents an empty category for any
 * name rather than refusing it; {@link LootTableList#getAll()} names every registered table, so the
 * question is simply asked.
 */
public final class ModLoot {

    private static final ModLoot INSTANCE = new ModLoot();

    private ModLoot() {}

    public static ModLoot get() {
        return INSTANCE;
    }

    /**
     * The other edition's chest categories, each against the table a 1.12.2 chest of that kind is
     * filled from.
     *
     * <p>
     * Named by the other edition's names because the settings file is the other edition's: a pack
     * that carries {@code loot.wayfarerCategories} across from a 1.7.10 instance should find it still
     * means the stronghold library. A name that is not in here is taken as a table's own name, so a
     * 1.12.2 packmaker can point the Wayfarer at any table a mod registers.
     */
    private static final Map<String, ResourceLocation> CATEGORIES = new LinkedHashMap<String, ResourceLocation>();

    static {
        CATEGORIES.put("dungeonChest", LootTableList.CHESTS_SIMPLE_DUNGEON);
        CATEGORIES.put("mineshaftCorridor", LootTableList.CHESTS_ABANDONED_MINESHAFT);
        CATEGORIES.put("villageBlacksmith", LootTableList.CHESTS_VILLAGE_BLACKSMITH);
        CATEGORIES.put("strongholdCorridor", LootTableList.CHESTS_STRONGHOLD_CORRIDOR);
        CATEGORIES.put("strongholdLibrary", LootTableList.CHESTS_STRONGHOLD_LIBRARY);
        CATEGORIES.put("strongholdCrossing", LootTableList.CHESTS_STRONGHOLD_CROSSING);
        CATEGORIES.put("pyramidDesertyChest", LootTableList.CHESTS_DESERT_PYRAMID);
        CATEGORIES.put("pyramidJungleChest", LootTableList.CHESTS_JUNGLE_TEMPLE);
        CATEGORIES.put("pyramidJungleDispenser", LootTableList.CHESTS_JUNGLE_TEMPLE_DISPENSER);
        CATEGORIES.put("bonusChest", LootTableList.CHESTS_SPAWN_BONUS_CHEST);
    }

    private static final ResourceLocation DUNGEON = LootTableList.CHESTS_SIMPLE_DUNGEON;
    private static final ResourceLocation MINESHAFT = LootTableList.CHESTS_ABANDONED_MINESHAFT;
    private static final ResourceLocation SMITH = LootTableList.CHESTS_VILLAGE_BLACKSMITH;
    private static final ResourceLocation LIBRARY = LootTableList.CHESTS_STRONGHOLD_LIBRARY;
    private static final ResourceLocation CROSSING = LootTableList.CHESTS_STRONGHOLD_CROSSING;

    private static final LootCondition[] NO_CONDITIONS = new LootCondition[0];

    /** Where the Wayfarer is to be found, worked out once every mod has registered its tables. */
    private static volatile List<ResourceLocation> wayfarerTables;

    /** Set by the first table load, because the tables a name could mean are not all known before one. */
    private static final AtomicBoolean wayfarerSettled = new AtomicBoolean();

    /** How many entries this has added, for the one line it logs. */
    private static int added;

    /** Whether that line has been said; a world reload files everything again and need not say so twice. */
    private static boolean announced;

    // ------------------------------------------------------------------
    // The one hook
    // ------------------------------------------------------------------

    /**
     * Each table as it is loaded, given whatever of this mod's things belongs in it.
     *
     * <p>
     * Nothing thrown out of here would be caught, and a mod that breaks loot breaks every chest in
     * the world, so the body is guarded: a table this cannot add to is left exactly as it was.
     */
    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        if (!TrmtConfig.lootFinds) return;
        settleWayfarer();
        try {
            fill(event.getName(), event.getTable());
        } catch (RuntimeException awkwardTable) {
            Trmt.LOG.warn("Could not add this mod's finds to the loot table {}", event.getName(), awkwardTable);
        }
        if (!announced && added > 0) {
            announced = true;
            Trmt.LOG.info("Added {} of the mod's finds to the world's chests", added);
        }
    }

    private static void fill(ResourceLocation name, LootTable table) {
        LootPool pool = table == null ? null : table.getPool("main");
        if (pool == null) return;

        tool(pool, name, ModItems.gradedTamper(), TrmtConfig.lootWeightTamper, DUNGEON, MINESHAFT, SMITH);
        tool(pool, name, ModItems.chunkTamper(), TrmtConfig.lootWeightChunkTamper, DUNGEON, CROSSING);
        wayfarer(pool, name);
        draughts(pool, name);

        book(pool, name, GuideBook.MK1, TrmtConfig.lootWeightGuide, DUNGEON, MINESHAFT, SMITH, LIBRARY);
        book(pool, name, GuideBook.MK2, TrmtConfig.lootWeightGuideRare, LIBRARY, DUNGEON);
        book(pool, name, GuideBook.COMMANDS, TrmtConfig.lootWeightGuideRare, LIBRARY, SMITH);
        book(pool, name, GuideBook.GOLEM, TrmtConfig.lootWeightGuideRare, LIBRARY, DUNGEON);

        golemFinds(pool, name);
        enchantBook(pool, name, EnchReinforce.INSTANCE, TrmtConfig.reinforceEnabled);
        enchantBook(pool, name, EnchWard.INSTANCE, TrmtConfig.wardEnabled);
        enchantBook(pool, name, EnchLight.INSTANCE, TrmtConfig.lightEnabled);
    }

    // ------------------------------------------------------------------
    // What goes where
    // ------------------------------------------------------------------

    /** A found tool is a starter one, not whatever the pack's best metal happens to be. */
    private static void tool(LootPool pool, ResourceLocation name, Item item, int weight, ResourceLocation... tables) {
        if (item == null) return;
        ItemStack stack = new ItemStack(item);
        TamperGrade grade = TamperGrade.available()
            .isEmpty() ? null
                : TamperGrade.available()
                    .get(0);
        if (grade != null) ItemChunkTamper.setGrade(stack, grade);
        add(pool, name, stack, weight, tables);
    }

    /**
     * The last rung, wherever the settings put it.
     *
     * <p>
     * Somewhere pointing at the End would suit it best, and the stronghold library is the nearest
     * thing vanilla has - both the rarest pool and the room with the portal in it. A pack that has an
     * End structure worth naming can name its table instead.
     */
    private static void wayfarer(LootPool pool, ResourceLocation name) {
        Item wayfarer = ModItems.magicTamper();
        List<ResourceLocation> where = wayfarerTables;
        if (wayfarer == null || where == null) return;
        add(
            pool,
            name,
            new ItemStack(wayfarer),
            TrmtConfig.lootWeightWayfarer,
            where.toArray(new ResourceLocation[where.size()]));
    }

    /**
     * The draughts, on the same terms as everything else here.
     *
     * <p>
     * Lightness turns up where a traveller's kit would - dungeons, mineshafts and a smith's chest -
     * and its opposite only in the deep places, being the stranger thing for anybody to have brewed
     * on purpose.
     */
    private static void draughts(LootPool pool, ResourceLocation name) {
        if (!TrmtConfig.potionsEnabled) return;
        ItemDraught light = ModItems.draught(Draughts.LIGHTNESS);
        ItemDraught heavy = ModItems.draught(Draughts.HEAVYFOOT);
        if (light != null && TrmtConfig.potionLightness) {
            add(pool, name, new ItemStack(light), TrmtConfig.lootWeightDraughtLight, DUNGEON, MINESHAFT, SMITH);
        }
        if (heavy != null && TrmtConfig.potionHeavyFoot) {
            add(pool, name, new ItemStack(heavy), TrmtConfig.lootWeightDraughtHeavy, DUNGEON, CROSSING);
        }
    }

    private static void book(LootPool pool, ResourceLocation name, GuideBook book, int weight,
        ResourceLocation... tables) {
        ItemGuideBook item = ModItems.guide(book);
        if (item != null) add(pool, name, new ItemStack(item), weight, tables);
    }

    /**
     * The golem's upgrades and, rarest of all, a dormant golem.
     *
     * <p>
     * The egg is the one thing here that skips a whole progression rather than shortening it, so it
     * sits alone in a stronghold library at the lowest weight the mod uses - a find somebody tells
     * other people about.
     */
    private static void golemFinds(LootPool pool, ResourceLocation name) {
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
                    add(pool, name, new ItemStack(item), TrmtConfig.lootWeightOmni, LIBRARY);
                } else {
                    add(pool, name, new ItemStack(item), TrmtConfig.lootWeightUpgrade, DUNGEON, MINESHAFT);
                }
            }
        }

        if (ModItems.golemEgg() != null) {
            add(pool, name, new ItemStack(ModItems.golemEgg()), TrmtConfig.lootWeightGolemEgg, LIBRARY);
        }
    }

    /**
     * One of the unlocks, on a book, where an ordinary entry would do.
     *
     * <p>
     * Which it does here, and is the whole of what the other edition's {@code PreparedBook} was for:
     * there Forge rebuilds any entry whose item is the enchanted book and rolls a fresh enchantment at
     * level thirty, past anything this mod's three can reach, so a book it filed came out of no chest.
     * A {@code SetNBT} function hands over exactly the stack described and nothing looks at it again.
     */
    private static void enchantBook(LootPool pool, ResourceLocation name,
        net.minecraft.enchantment.Enchantment enchantment, boolean enabled) {
        if (!enabled || enchantment == null) return;
        ItemStack stack = new ItemStack(net.minecraft.init.Items.ENCHANTED_BOOK);
        net.minecraft.item.ItemEnchantedBook
            .addEnchantment(stack, new net.minecraft.enchantment.EnchantmentData(enchantment, 1));
        add(pool, name, stack, TrmtConfig.lootWeightEnchantBook, LIBRARY, DUNGEON);
    }

    // ------------------------------------------------------------------
    // Filing one thing
    // ------------------------------------------------------------------

    /**
     * Puts one stack in this pool if this is one of the tables it belongs in.
     *
     * <p>
     * The entry's name has to be unique within the pool and is the stack's registry name with the
     * mod's own prefix, so a second load of the same table replaces this mod's entry rather than
     * stacking another beside it. Vanilla's own pools reject a duplicate name, which is the behaviour
     * being relied on rather than worked around.
     */
    private static void add(LootPool pool, ResourceLocation table, ItemStack stack, int weight,
        ResourceLocation... tables) {
        if (stack == null || stack.isEmpty() || weight <= 0) return;
        boolean wanted = false;
        for (ResourceLocation each : tables) {
            if (table.equals(each)) {
                wanted = true;
                break;
            }
        }
        if (!wanted) return;

        String entryName = Trmt.MODID + ":"
            + stack.getItem()
                .getRegistryName()
                .getPath()
            + (stack.hasTagCompound() ? "_prepared" : "");
        if (pool.getEntry(entryName) != null) return;

        // The stack's own NBT, where it has any: a tamper's grade, or the enchantment on a book.
        LootFunction[] functions = stack.hasTagCompound()
            ? new LootFunction[] { new SetNBT(NO_CONDITIONS, stack.getTagCompound()) }
            : new LootFunction[0];
        LootEntry entry = new LootEntryItem(stack.getItem(), weight, 0, functions, NO_CONDITIONS, entryName);
        pool.addEntry(entry);
        added++;
    }

    // ------------------------------------------------------------------
    // Where the Wayfarer actually goes
    // ------------------------------------------------------------------

    /**
     * Makes sure the Wayfarer is in a table that exists, and says where it is not.
     *
     * <p>
     * The other edition cannot ask this: Forge invents an empty chest category for any name rather
     * than refusing it, so a pack whose every name was misspelt had no Wayfarer anywhere and nothing
     * could tell, and its {@code settleWayfarer} has to read each pool and count what else is in it to
     * guess whether anybody fills it. Here {@link LootTableList#getAll()} names every registered table,
     * so a name is either one of those or it is nothing.
     *
     * <p>
     * At the first table load rather than at startup, because a mod registers its tables during
     * loading and the set is only complete afterwards. Once a session, so a second world neither looks
     * again nor says it twice.
     */
    private static void settleWayfarer() {
        if (!wayfarerSettled.compareAndSet(false, true)) return;

        List<String> configured = WayfarerCategories.named(TrmtConfig.lootWayfarerCategories, "strongholdLibrary");
        java.util.Set<ResourceLocation> known = LootTableList.getAll();
        List<ResourceLocation> real = new ArrayList<ResourceLocation>();
        List<String> unused = new ArrayList<String>();
        for (String name : new LinkedHashSet<String>(configured)) {
            ResourceLocation table = table(name);
            if (table != null && known.contains(table)) {
                real.add(table);
            } else {
                unused.add(name);
            }
        }

        for (String name : unused) {
            String meant = WayfarerCategories.nearMiss(
                name,
                CATEGORIES.keySet()
                    .toArray(new String[CATEGORIES.size()]));
            Trmt.LOG.warn(
                "loot.wayfarerCategories names '{}', and no loot table of that name is registered, so no chest can be filled from it. {}",
                name,
                meant == null
                    ? "Names are matched exactly, case included: use one of this mod's category names, or the full name of a table a mod registers, such as 'minecraft:chests/stronghold_library'"
                    : "Names are matched exactly, case included - did you mean '" + meant + "'?");
        }

        Collection<String> missing = unused;
        if (WayfarerCategories.fallsBack(configured, missing, "strongholdLibrary")) {
            real.add(LIBRARY);
            Trmt.LOG.warn(
                "None of the loot tables loot.wayfarerCategories names is one that exists, so the Wayfarer's Tamper is filed in the stronghold library too, where it is found by default. A loot.wayfarer weight of 0 is the way to have it found nowhere");
        }
        wayfarerTables = real;
    }

    /**
     * The table one configured name means, or null for a name that is neither.
     *
     * <p>
     * One of this mod's category names, which are the other edition's so a carried settings file still
     * says what it meant; or a table's own name, for a pack naming something a mod registered. A name
     * with no colon in it that is not a category is not a table name either - every registered table
     * has a namespace - so it is the misspelling the warning above is about.
     */
    private static ResourceLocation table(String name) {
        ResourceLocation known = CATEGORIES.get(name);
        if (known != null) return known;
        if (name.indexOf(':') < 0) return null;
        try {
            return new ResourceLocation(name);
        } catch (RuntimeException notAName) {
            return null;
        }
    }
}
