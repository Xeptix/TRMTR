package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatisticsFile;
import net.minecraftforge.common.AchievementPage;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The mod's own achievement page: the tools, the two unlock enchantments, and the four books.
 *
 * <p>
 * Pure vanilla, with no dependency of any kind - a page, some achievements, and a call to
 * {@code triggerAchievement} from wherever the thing actually happens. Built in init rather than
 * pre-init because every icon is one of the mod's own items and those must exist first.
 *
 * <p>
 * A tree rather than a row: the three tools run left to right as a ladder, the enchantments hang
 * off the chunk tamper that carries them, and the books sit apart because they are read rather
 * than earned. Nothing here grants anything; each is triggered where its event already happens,
 * so there is no scanning and no polling.
 */
public final class ModAchievements {

    private static Achievement tamper;
    private static Achievement chunkTamper;
    private static Achievement wayfarer;
    private static Achievement reinforcing;
    private static Achievement warding;
    private static Achievement lighting;
    private static Achievement bookReinforce;
    private static Achievement bookWard;

    private static Achievement bookLight;
    private static Achievement golemBuilt;
    private static Achievement omni;

    private static Achievement library;

    private static final Map<GuideBook, Achievement> BOOKS = new EnumMap<GuideBook, Achievement>(GuideBook.class);

    private static boolean registered;

    private ModAchievements() {}

    public static void register() {
        if (registered || !TrmtConfig.achievements) return;
        if (ModItems.gradedTamper() == null || ModItems.chunkTamper() == null) return;
        registered = true;

        List<Achievement> all = new ArrayList<Achievement>();

        tamper = make("trmtTamper", 0, 0, new ItemStack(ModItems.gradedTamper()), null, all);
        chunkTamper = make("trmtChunkTamper", 2, 0, new ItemStack(ModItems.chunkTamper()), tamper, all);
        if (ModItems.magicTamper() != null) {
            wayfarer = make("trmtWayfarer", 4, 0, new ItemStack(ModItems.magicTamper()), chunkTamper, all);
        }
        // Each mode and its book asked of the switch AND of the enchantment, because neither
        // answers the question on its own. The id is claimed whatever the switch says, so that a
        // tool already carrying the enchantment does not turn into a broken one when somebody
        // turns the feature off - which means a non-null instance proves only that a number was
        // reserved. And a switch that is on proves nothing either, since registration can still
        // have found no free id or found the one it wanted taken. Both together are what say a
        // player can actually reach the mode, and an achievement they cannot reach is one that
        // sits on the page for ever with no way to clear it. The same test the recipes use.
        boolean canReinforce = TrmtConfig.reinforceEnabled && EnchReinforce.INSTANCE != null;
        boolean canLight = TrmtConfig.lightEnabled && EnchLight.INSTANCE != null;
        boolean canWard = TrmtConfig.wardEnabled && EnchWard.INSTANCE != null;

        if (canReinforce) {
            reinforcing = make("trmtReinforcing", 2, 2, new ItemStack(net.minecraft.init.Items.book), chunkTamper, all);
            bookReinforce = make(
                "trmtBookReinforce",
                4,
                2,
                new ItemStack(net.minecraft.init.Items.enchanted_book),
                reinforcing,
                all);
        }
        if (canLight) {
            lighting = make("trmtLighting", 2, 4, new ItemStack(net.minecraft.init.Blocks.glowstone), chunkTamper, all);
            // The third of the three, and it was simply missing. Both of its neighbours had their
            // book beside them and the questbook has had a quest for this one since the path light
            // shipped, so the shelf was one short of a set nothing else treated as incomplete.
            bookLight = make(
                "trmtBookLight",
                4,
                4,
                new ItemStack(net.minecraft.init.Items.enchanted_book),
                lighting,
                all);
        }
        if (canWard) {
            warding = make("trmtWarding", 2, -2, new ItemStack(net.minecraft.init.Items.book), chunkTamper, all);
            bookWard = make(
                "trmtBookWard",
                4,
                -2,
                new ItemStack(net.minecraft.init.Items.enchanted_book),
                warding,
                all);
        }

        // Asked of the switches rather than of the items, because the items are registered
        // whatever the switches say - the id map is compared when a client connects, so
        // which items exist may not depend on a setting. What does depend on it is whether
        // anything can craft one or stand one up, and an achievement for a golem nobody can
        // build is an achievement on the page for ever with no way to earn it.
        if (TrmtConfig.golemEnabled && ModItems.golemEgg() != null) {
            golemBuilt = make("trmtGolem", -2, 0, new ItemStack(ModItems.golemEgg()), null, all);
            ItemGolemUpgrade omniItem = TrmtConfig.golemUpgrades
                ? ModItems.upgrade(com.trmtgtnh.entity.GolemUpgrade.OMNI)
                : null;
            if (omniItem != null) {
                omni = make("trmtOmni", -4, 0, new ItemStack(omniItem), golemBuilt, all);
            }
        }

        // Row six rather than row two, which is where these used to sit - directly on top of
        // the reinforcing achievement and the book beside it. The screen draws every entry
        // independently with no test for a collision and takes its tooltip from whichever was
        // added last, so the guides won both squares and two achievements were invisible on
        // the page even once they were earned. The shelf moved rather than the pair, because
        // the pair is a symmetry: reinforcing mirrors warding above it, and each has its book
        // out to the right.
        int column = -2;
        for (GuideBook book : GuideBook.values()) {
            ItemGuideBook item = ModItems.guide(book);
            if (item == null) continue;
            BOOKS.put(book, make("trmtRead_" + book.key, column, 6, new ItemStack(item), null, all));
            column += 2;
        }
        // The shelf, once every book on it has been opened. Worth its own achievement rather than
        // being inferred from the last one read: which book somebody finishes on is an accident of
        // the order they picked them up in, and hanging anything off "the last one" quietly means
        // "whichever the enum happens to end with" - so adding a book would move the reward.
        if (!BOOKS.isEmpty()) {
            library = make("trmtLibrary", column, 6, new ItemStack(net.minecraft.init.Blocks.bookshelf), null, all);
        }

        AchievementPage.registerAchievementPage(
            // This mod's name rather than the original's, and the distinction is not a
            // preference. LICENSE.md is careful to say that only the initialism is reused and
            // that nothing here is endorsed by milkucha; a page inside the game titled with
            // the original's full name says the opposite of that to the one audience that
            // cannot see LICENSE.md.
            new AchievementPage("TRMT Reimagined", all.toArray(new Achievement[all.size()])));
        Trmt.LOG.info("Registered {} achievements", Integer.valueOf(all.size()));
    }

    private static Achievement make(String name, int column, int row, ItemStack icon, Achievement parent,
        List<Achievement> into) {
        Achievement achievement = new Achievement("achievement." + name, name, column, row, icon, parent)
            .registerStat();
        into.add(achievement);
        return achievement;
    }

    // ------------------------------------------------------------------
    // Triggers
    // ------------------------------------------------------------------

    /** Something was crafted; award the tool ladder if it was one of ours. */
    public static void onCrafted(EntityPlayer player, ItemStack stack) {
        if (!ready(player) || stack == null) return;
        Object item = stack.getItem();
        if (item == ModItems.magicTamper()) {
            award(player, wayfarer);
        } else if (item == ModItems.chunkTamper()) {
            award(player, chunkTamper);
        } else if (item == ModItems.gradedTamper() || item instanceof ItemTamper) {
            award(player, tamper);
        } else if (item instanceof ItemGolemUpgrade
            && ((ItemGolemUpgrade) item).upgrade() == com.trmtgtnh.entity.GolemUpgrade.OMNI) {
                award(player, omni);
            } else if (item == net.minecraft.init.Items.enchanted_book) {
                // Which unlock it carries decides which of the two it is.
                if (carries(stack, EnchReinforce.INSTANCE)) award(player, bookReinforce);
                if (carries(stack, EnchWard.INSTANCE)) award(player, bookWard);
                if (carries(stack, EnchLight.INSTANCE)) award(player, bookLight);
            }
    }

    /**
     * Whether an enchanted book carries this enchantment.
     *
     * <p>
     * Read straight off {@code StoredEnchantments} rather than through EnchantmentHelper, because a
     * book keeps what it teaches in a different tag from what an enchanted tool wears - the helper
     * reads {@code ench} and so answers zero for every book ever made.
     */
    private static boolean carries(ItemStack stack, net.minecraft.enchantment.Enchantment enchantment) {
        if (enchantment == null || !stack.hasTagCompound()) return false;
        net.minecraft.nbt.NBTTagList stored = stack.getTagCompound()
            .getTagList("StoredEnchantments", 10);
        for (int index = 0; index < stored.tagCount(); index++) {
            if (stored.getCompoundTagAt(index)
                .getShort("id") == enchantment.effectId) {
                return true;
            }
        }
        return false;
    }

    /** A Golem of Ways was stood up, however it was stood up. */
    public static void onGolemBuilt(EntityPlayer player) {
        award(player, golemBuilt);
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
    public static void onReinforced(EntityPlayer player) {
        award(player, reinforcing);
    }

    /** A light gesture actually landed. */
    public static void onLit(EntityPlayer player) {
        award(player, lighting);
    }

    /** A ward gesture actually landed. */
    public static void onWarded(EntityPlayer player) {
        award(player, warding);
    }

    /** A guide book was opened. */
    public static void onRead(EntityPlayer player, GuideBook book) {
        if (book == null) return;
        award(player, BOOKS.get(book));
        awardLibraryIfComplete(player);
    }

    /**
     * The shelf achievement, if this was the last book outstanding.
     *
     * <p>
     * Asked of the player's own statistics rather than of a counter kept here, because the answer
     * has to survive logging out, and because a book read on one world and a book read on the next
     * are both read as far as the player is concerned. Checked after the award above rather than
     * before it, since by then the book just opened is already written into that file.
     */
    private static void awardLibraryIfComplete(EntityPlayer player) {
        if (library == null || !ready(player) || !(player instanceof EntityPlayerMP)) return;
        StatisticsFile stats = ((EntityPlayerMP) player).func_147099_x();
        if (stats == null) return;
        for (Achievement each : BOOKS.values()) {
            if (each != null && !stats.hasAchievementUnlocked(each)) return;
        }
        award(player, library);
    }

    // ------------------------------------------------------------------
    // What the rest of the mod, and anything outside it, may ask for
    // ------------------------------------------------------------------

    public static Achievement wayfarer() {
        return wayfarer;
    }

    public static Achievement bookReinforce() {
        return bookReinforce;
    }

    public static Achievement bookWard() {
        return bookWard;
    }

    public static Achievement bookLight() {
        return bookLight;
    }

    public static Achievement golemBuilt() {
        return golemBuilt;
    }

    public static Achievement omni() {
        return omni;
    }

    public static Achievement library() {
        return library;
    }

    /**
     * The name anything outside this mod has to use to react to an achievement.
     *
     * <p>
     * Not the one it is displayed under. An achievement carries two strings: the first constructor
     * argument is the id Forge files it under and the only one that appears in an event, and the
     * second is a stem for the lang keys. This mod passes "achievement.trmtGolem" and "trmtGolem"
     * respectively, so a companion mod matching on the short one matches nothing at all - which is
     * exactly what the trophies did until it was noticed.
     */
    public static String statId(Achievement achievement) {
        return achievement == null ? null : achievement.statId;
    }

    private static boolean ready(EntityPlayer player) {
        return TrmtConfig.achievements && registered
            && player != null
            && player.worldObj != null
            && !player.worldObj.isRemote;
    }

    /**
     * Hands over an achievement, and everything it hangs from, in order.
     *
     * <p>
     * The ancestors first, and that is the whole of the fix rather than tidiness. The game refuses
     * an achievement whose parent is still locked and drops it without a word, and nothing ever
     * tries again - so any award that can arrive before its parent is an award that can be lost
     * for the life of the world. Two on this page could: the enchanted book achievements fire the
     * moment a book is crafted and hang from gestures that cannot be made until that book exists,
     * so the prerequisite was written backwards and the drop was certain rather than unlucky.
     *
     * <p>
     * Walking the chain fixes it without moving the tree, which matters because the tree is also a
     * picture: the book sits beside the gesture it unlocks, and re-parenting to satisfy the engine
     * would have moved the drop onto whoever finds one in a chest instead. Every root here has no
     * parent, so the walk ends inside this page.
     */
    private static void award(EntityPlayer player, Achievement achievement) {
        if (achievement == null || !ready(player)) return;
        List<Achievement> chain = new ArrayList<Achievement>();
        for (Achievement each = achievement; each != null; each = each.parentAchievement) {
            chain.add(0, each);
        }
        for (int i = 0; i < chain.size(); i++) {
            player.triggerAchievement(chain.get(i));
        }
    }
}
