package com.trmtgtnh.item;

import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

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

    public static final ResourceLocation TAMPER = id("trmt_tamper");
    public static final ResourceLocation CHUNK_TAMPER = id("trmt_chunk_tamper");
    public static final ResourceLocation WAYFARER = id("trmt_wayfarer");
    public static final ResourceLocation REINFORCING = id("trmt_reinforcing");
    public static final ResourceLocation BOOK_REINFORCE = id("trmt_book_reinforce");
    public static final ResourceLocation LIGHTING = id("trmt_lighting");
    public static final ResourceLocation BOOK_LIGHT = id("trmt_book_light");
    public static final ResourceLocation WARDING = id("trmt_warding");
    public static final ResourceLocation BOOK_WARD = id("trmt_book_ward");
    public static final ResourceLocation GOLEM = id("trmt_golem");
    public static final ResourceLocation OMNI = id("trmt_omni");
    public static final ResourceLocation LIBRARY = id("trmt_library");

    private ModAchievements() {}

    private static ResourceLocation id(String name) {
        return new ResourceLocation(Trmt.MODID, name);
    }

    /** The file one guide book's reading is written into. */
    public static ResourceLocation forBook(GuideBook book) {
        return book == null ? null : id("trmt_read_" + book.key);
    }

    // ------------------------------------------------------------------
    // Granting
    // ------------------------------------------------------------------

    /**
     * Marks one advancement earned, if the player is a real one on a real server.
     *
     * <p>
     * Silent about an advancement it cannot find, because a pack may have removed the file and that
     * is a pack's business; and silent on the client, where there is no advancement manager and
     * nothing to write to.
     */
    private static void award(Player player, ResourceLocation which) {
        if (which == null || !(player instanceof ServerPlayer)) return;
        ServerPlayer real = (ServerPlayer) player;
        if (real.server == null) return;
        Advancement advancement = real.server.getAdvancements()
            .getAdvancement(which);
        if (advancement == null) return;
        real.getAdvancements()
            .award(advancement, CRITERION);
    }

    /** Whether this player already has it, which is how the shelf knows the set is complete. */
    private static boolean has(ServerPlayer player, ResourceLocation which) {
        if (which == null || player.server == null) return false;
        Advancement advancement = player.server.getAdvancements()
            .getAdvancement(which);
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
                if (TrmtConfig.reinforceEnabled && carries(stack, EnchReinforce.INSTANCE)) {
                    award(player, BOOK_REINFORCE);
                }
                if (TrmtConfig.wardEnabled && carries(stack, EnchWard.INSTANCE)) award(player, BOOK_WARD);
                if (TrmtConfig.lightEnabled && carries(stack, EnchLight.INSTANCE)) award(player, BOOK_LIGHT);
            }
    }

    /**
     * Whether an enchanted book carries this enchantment.
     *
     * <p>
     * Read straight off {@code StoredEnchantments} rather than through EnchantmentHelper, because a
     * book keeps what it teaches in a different tag from what an enchanted tool wears - the helper
     * reads {@code ench} and so answers zero for every book ever made. The id written there is a
     * registry number here rather than the hand-picked one the other edition hunts for, which is the
     * same simplification the enchantments themselves took.
     */
    private static boolean carries(ItemStack stack, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        if (enchantment == null || !stack.hasTag()) return false;
        // Through the game's own pair of helpers rather than by hand, and that is this version
        // rather than a tidy-up. What a book teaches is still kept in StoredEnchantments and the
        // helper that reads a tool's tag still answers nought for one - which is what the note above
        // is about - but the id written in there is a registry *name* now, not a number. Picking the
        // entries apart by hand would be comparing a string to an int, and would have compiled.
        return net.minecraft.world.item.enchantment.EnchantmentHelper
            .deserializeEnchantments(net.minecraft.world.item.EnchantedBookItem.getEnchantments(stack))
            .containsKey(enchantment);
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
