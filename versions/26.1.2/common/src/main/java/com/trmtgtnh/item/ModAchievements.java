package com.trmtgtnh.item;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The sixteen things worth doing, and what grants them.
 *
 * <p>
 * This is the one place the port plan said 1:1 could not hold, and it holds less than it looks.
 * 1.12 removed the achievement system outright, so the other edition's sixteen are sixteen
 * advancement files under {@code assets/trmtgtnh/advancements} instead - the same set, the same
 * English, read from the same lang keys the other edition writes. What is gone is the grid: there
 * each achievement is placed at a column and a row by hand and a line is drawn only where there is a
 * parent, so six of the sixteen sit on the page with no line to anything. 1.12.2 lays a tree out for
 * itself and everything on a tab descends from that tab's root, so those six hang off the first tool
 * here and the lines between them are new.
 *
 * <p>
 * What is left in code is this: which gesture earns which one. Every advancement's only criterion is
 * {@code minecraft:impossible}, which is how 1.12.2 says "nothing but code grants this" - and what
 * earns one of these is this mod's own doing, a square mended or a block warded or a book read, none
 * of which is a thing vanilla watches for.
 *
 * <p>
 * The other edition refuses to register an achievement for a feature the config has switched off, so
 * that nothing unreachable sits on the page. A JSON file cannot be registered conditionally, so all
 * sixteen are always there and the refusal moved here instead: a switch that is off means the grant
 * never happens. The page shows one more unearned line than it used to; nothing can be earned that
 * the switches have closed.
 */
public final class ModAchievements {

    /** The one criterion every one of these files declares, and the only way any of them is met. */
    private static final String CRITERION = "trmt";

    public static final Identifier TAMPER = id("trmt_tamper");
    public static final Identifier CHUNK_TAMPER = id("trmt_chunk_tamper");
    public static final Identifier WAYFARER = id("trmt_wayfarer");
    public static final Identifier REINFORCING = id("trmt_reinforcing");
    public static final Identifier BOOK_REINFORCE = id("trmt_book_reinforce");
    public static final Identifier LIGHTING = id("trmt_lighting");
    public static final Identifier BOOK_LIGHT = id("trmt_book_light");
    public static final Identifier WARDING = id("trmt_warding");
    public static final Identifier BOOK_WARD = id("trmt_book_ward");
    public static final Identifier GOLEM = id("trmt_golem");
    public static final Identifier OMNI = id("trmt_omni");
    public static final Identifier LIBRARY = id("trmt_library");

    private ModAchievements() {}

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(Trmt.MODID, name);
    }

    /** The file one guide book's reading is written into. */
    public static Identifier forBook(GuideBook book) {
        return book == null ? null : id("trmt_read_" + book.key);
    }

    // ------------------------------------------------------------------
    // Granting
    // ------------------------------------------------------------------

    /**
     * The advancements that hang from nothing in 1.7.10 - Ground Underfoot, It Lives, the four books read and The Whole
     * Shelf - where the award walk stops (0.9.227, spec BP34). The tab here hangs the golem and the books from the tamper
     * and the book-read ones from Mk I, a picture decided for this version (BP32); walked through that picture, opening
     * any guide also granted Ground Underfoot and Well Read, building a golem granted Ground Underfoot, and The Whole
     * Shelf came after three books. 1.7.10's chain gives each of these alone.
     */
    private static final java.util.Set<String> ROOTS_IN_THE_REFERENCE = new java.util.HashSet<String>(
        java.util.Arrays.asList(
            "trmt_tamper",
            "trmt_golem",
            "trmt_read_mk1",
            "trmt_read_mk2",
            "trmt_read_commands",
            "trmt_read_golem",
            "trmt_library"));

    /**
     * Marks one advancement earned, if the player is a real one on a real server.
     *
     * <p>
     * Silent about an advancement it cannot find, because a pack may have removed the file and that
     * is a pack's business; and silent on the client, where there is no advancement manager and
     * nothing to write to.
     */
    private static void award(Player player, Identifier which) {
        if (which == null || !(player instanceof ServerPlayer)) return;
        // The switch the 1.7.10 edition asks before it registers anything: off, nothing is awarded (0.9.222, spec
        // BP30). The tab itself still loads at this version, as a data file does.
        if (!com.trmtgtnh.config.TrmtConfig.achievements) return;
        ServerPlayer real = (ServerPlayer) player;
        // The player's server field is private from 1.21.6; its level's server is the same one.
        net.minecraft.server.MinecraftServer server = real.level() == null ? null : real.level().getServer();
        if (server == null) return;
        // From 1.20.2 the manager hands out an advancement with its id beside it, and an advancement names its parent
        // by id rather than holding it, so the chain is walked through the manager.
        net.minecraft.server.ServerAdvancementManager known = server.getAdvancements();
        AdvancementHolder advancement = known.get(which);
        if (advancement == null) return;
        // Everything it hangs from first, root down, as the 1.7.10 edition hands its achievements over - there the game
        // dropped one whose parent was still locked, so the chain was walked, and crafting a Reinforcing book gave Set in
        // Stone, By the Cubic Yard and Ground Underfoot with it. An advancement here is granted alone, so until 0.9.222
        // the book gave one and no other (spec BP34). Only this mod's own.
        java.util.List<AdvancementHolder> chain = new java.util.ArrayList<AdvancementHolder>();
        for (AdvancementHolder at = advancement; at != null; at = at.value()
            .parent()
            .map(known::get)
            .orElse(null)) {
            if (!Trmt.MODID.equals(at.id()
                .getNamespace())) break;
            chain.add(0, at);
            if (ROOTS_IN_THE_REFERENCE.contains(at.id()
                .getPath())) break;
        }
        for (AdvancementHolder each : chain) {
            real.getAdvancements()
                .award(each, CRITERION);
        }
    }

    /** Whether this player already has it, which is how the shelf knows the set is complete. */
    private static boolean has(ServerPlayer player, Identifier which) {
        // The level's server, as in award.
        net.minecraft.server.MinecraftServer server = player.level() == null ? null : player.level().getServer();
        if (which == null || server == null) return false;
        AdvancementHolder advancement = server.getAdvancements()
            .get(which);
        if (advancement == null) return false;
        PlayerAdvancements progress = player.getAdvancements();
        return progress.getOrStartProgress(advancement)
            .isDone();
    }

    // ------------------------------------------------------------------
    // Triggers
    // ------------------------------------------------------------------

    /** Something was crafted; award the tool ladder if it was one of ours. */
    public static void onCrafted(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        Object item = stack.getItem();
        if (item == ModItems.magicTamper()) {
            award(player, WAYFARER);
        } else if (item == ModItems.chunkTamper()) {
            award(player, CHUNK_TAMPER);
        } else if (item == ModItems.gradedTamper() || item instanceof ItemTamper) {
            award(player, TAMPER);
        } else if (item instanceof ItemGolemUpgrade
            && ((ItemGolemUpgrade) item).upgrade() == com.trmtgtnh.entity.GolemUpgrade.OMNI) {
                if (TrmtConfig.golemEnabled && TrmtConfig.golemUpgrades) award(player, OMNI);
            } else if (item == net.minecraft.world.item.Items.ENCHANTED_BOOK) {
                // Which unlock it carries decides which of the three it is.
                if (TrmtConfig.reinforceEnabled && carries(stack, EnchReinforce.UNLOCK)) {
                    award(player, BOOK_REINFORCE);
                }
                if (TrmtConfig.wardEnabled && carries(stack, EnchWard.UNLOCK)) award(player, BOOK_WARD);
                if (TrmtConfig.lightEnabled && carries(stack, EnchLight.UNLOCK)) award(player, BOOK_LIGHT);
            }
    }

    /**
     * Whether an enchanted book carries this enchantment.
     *
     * <p>
     * Read off the book's stored enchantments rather than its own, because a book keeps what it teaches apart from
     * what an enchanted tool wears - the helper that reads a tool answers nought for every book ever made. At this
     * version both are components naming each enchantment by its registry entry; see
     * {@link TamperEnchantment#taughtBy}.
     */
    private static boolean carries(ItemStack stack, TamperEnchantment enchantment) {
        return enchantment != null && enchantment.taughtBy(stack);
    }

    /** A Golem of Ways was stood up, however it was stood up. */
    public static void onGolemBuilt(Player player) {
        if (TrmtConfig.golemEnabled) award(player, GOLEM);
    }

    /**
     * A player's own tamper took a block up at least one level, which means the enchantment was
     * obtained and used.
     *
     * <p>
     * Either tamper: the chunk tamper's one-level rise, or the Wayfarer's set to a higher level.
     * Setting a block lower or clearing it earns nothing, and neither does a golem laying material,
     * which has no player to give it to.
     */
    public static void onReinforced(Player player) {
        if (TrmtConfig.reinforceEnabled) award(player, REINFORCING);
    }

    /** A light gesture actually landed. */
    public static void onLit(Player player) {
        if (TrmtConfig.lightEnabled) award(player, LIGHTING);
    }

    /** A ward gesture actually landed. */
    public static void onWarded(Player player) {
        if (TrmtConfig.wardEnabled) award(player, WARDING);
    }

    /** A guide book was opened. */
    public static void onRead(Player player, GuideBook book) {
        if (book == null) return;
        award(player, forBook(book));
        awardLibraryIfComplete(player);
    }

    /**
     * The shelf advancement, if this was the last book outstanding.
     *
     * <p>
     * Asked of the player's own progress rather than of a counter kept here, because the answer has
     * to survive logging out. Checked after the award above rather than before it, since by then the
     * book just opened is already written into that file.
     */
    private static void awardLibraryIfComplete(Player player) {
        if (!(player instanceof ServerPlayer)) return;
        ServerPlayer real = (ServerPlayer) player;
        for (GuideBook book : GuideBook.values()) {
            if (!has(real, forBook(book))) return;
        }
        award(player, LIBRARY);
    }
}
