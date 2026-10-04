package com.trmtgtnh.compat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.entity.GolemUpgrade;
import com.trmtgtnh.entity.GolemWork;
import com.trmtgtnh.item.EnchLight;
import com.trmtgtnh.item.EnchReinforce;
import com.trmtgtnh.item.EnchWard;
import com.trmtgtnh.item.GuideBook;
import com.trmtgtnh.item.ModItems;

/**
 * A quest line for the mod, written into the pack's questbook when BetterQuesting is installed.
 *
 * <p>
 * Like the trophies, this touches nothing of BetterQuesting's code. Its quests live on disk as
 * JSON under {@code config/betterquesting/DefaultQuests/}, so the entire integration is writing
 * files in the shape that folder already uses - no API, no classpath entry, nothing to break when
 * their internals move.
 *
 * <p>
 * The write is strictly additive. Two new folders are created, one for the line and one for its
 * quests, and a single line is appended to {@code QuestLinesOrder.txt} if it is not already there.
 * No file belonging to another quest line is opened, let alone changed. Quest ids in this format
 * are random UUIDs, so ours take a high word of {@code 0x54524D54} - "TRMT" - which no
 * pack-generated id will ever hold.
 *
 * <p>
 * Everything is generated rather than shipped as a resource, which is the point: item registry
 * names, the wildcard damage on a tamper whose material is the player's choice, and above all the
 * three enchantment ids - which are config-assigned and differ between packs - are all read from the
 * game as it actually loaded. A shipped file would be wrong on any pack that moved an id.
 *
 * <p>
 * Nothing is loaded into a running world by this class. BetterQuesting reads the folder when a
 * world is created or when somebody runs {@code /bq_admin default load}, and choosing to do that
 * is the player's, so the mod says the line is ready and stops there.
 * 
 * <p>
 * <strong>The file shape here is the other edition's and is unverified on 1.12.2.</strong> Every
 * other part of this is: the folder is written the same way, the fingerprint still says what was
 * written, and nothing links the other mod's code - so a format that turns out to have changed is a
 * change to the writer below and to nothing else. What it is not safe to assume is that the mod on
 * the other side of it reads this. Until somebody can check it against the real thing, treat a file
 * written here as offered rather than accepted.
 */
public final class QuestbookCompat {

    private static final String BETTER_QUESTING = "betterquesting";

    /** "TRMT" as the high word of every id written here, so nothing can collide with the pack. */
    private static final long ID_HIGH = 0x54524D54L;

    private static final String LINE_NAME = "The Roads More Travelled";

    private static final String STAMP = ".trmt-version";

    /** The pack's own bags, by the meta its loot config gives each age. */
    private static final String LOOT_BAG = "enhancedlootbags:lootbag";

    private static final int BAG_STEAM = 2;

    private static final int BAG_LV = 4;

    /**
     * Whether the world now loaded is missing the chapter, or null before anybody has looked.
     *
     * <p>
     * A question about the world rather than about the launch, which is what it used to be and got
     * wrong in both directions. Writing the folder said nothing at all about whether the world had
     * taken it, so a player was told to run the import on a world that already had the chapter and
     * never told at all on the launch after the folder was written - which is every launch but one.
     */
    private static volatile Boolean chapterMissing;

    private QuestbookCompat() {}

    public static boolean active() {
        return TrmtConfig.questbook && TrmtConfig.gtnhEnhanced && Loader.isModLoaded(BETTER_QUESTING);
    }

    /** Who has already been told, so a relog does not repeat it. */
    private static final java.util.Set<String> told = java.util.Collections
        .synchronizedSet(new java.util.HashSet<String>());

    /** Whether this player should be told - at most once each, and only when it is actually true. */
    public static boolean shouldTell(String who) {
        return active() && TrmtConfig.questbookNotice
            && Boolean.TRUE.equals(chapterMissing)
            && who != null
            && told.add(who);
    }

    /**
     * Whether the world now loaded has the chapter, asked once as it starts.
     *
     * <p>
     * By reading the world's own quest database rather than by inferring anything, because the two
     * things that decide the answer both live outside this mod: BetterQuesting reads its defaults
     * folder only into a world that has no database yet, and a pack may or may not have told it to
     * re-import on every load. A world that has one and has not taken the chapter is the only case
     * worth saying anything about, and it cannot be worked out from this side any other way.
     *
     * <p>
     * On the server thread, deliberately, and not on a worker. The player logs in immediately
     * afterwards and a promise that has not resolved by then is a notice that never appears. It is
     * one buffered pass looking for a fixed decimal that occurs nowhere else, and it happens once
     * per world.
     */
    public static void examineWorld(MinecraftServer server) {
        told.clear();
        chapterMissing = Boolean.FALSE;
        if (!active() || server == null) return;
        try {
            // A pack that has asked BetterQuesting to re-import its defaults on every world load
            // will take the chapter by itself, and the file on disk at this moment is the one from
            // before that import - so anything read here would be answering the wrong question.
            if (loadsDefaultsOnStartup()) return;

            // The prefix mirrors BetterQuesting's own: a client's integrated server roots its
            // world folders under saves/ and a dedicated server does not.
            String prefix = FMLCommonHandler.instance()
                .getSide()
                .isClient() ? "saves/" : "";
            File database = server.getFile(prefix + server.getFolderName() + "/betterquesting/QuestDatabase.json");
            // No database at all means this world has just been made, and the defaults folder was
            // read into it as it was created. Nothing to say.
            if (!database.isFile()) return;
            chapterMissing = Boolean.valueOf(!mentionsUs(database));
        } catch (RuntimeException awkward) {
            Trmt.LOG.debug("Could not tell whether this world has the quest chapter", awkward);
        }
    }

    /** Forgotten between worlds, so somebody told in one is told again in the next. */
    public static void forgetWorld() {
        chapterMissing = null;
        told.clear();
    }

    /**
     * Whether the world's questbook already names one of this mod's quests.
     *
     * <p>
     * Searched for as a bare decimal rather than as a key and a value, because the exact spacing of
     * that file belongs to whoever wrote it and is not this mod's to predict. The number is the high
     * word every id here is built from, it appears nowhere in the pack's own ten megabytes, and the
     * file is written one value to a line, so no match can straddle a buffer. A false positive
     * would only make the mod hold its tongue, which is the right direction to fail in.
     */
    private static boolean mentionsUs(File database) {
        String token = Long.toString(ID_HIGH);
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(database), "UTF-8"),
                1 << 16);
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(token)) return true;
                }
            } finally {
                reader.close();
            }
        } catch (IOException unreadable) {
            // Unreadable is not missing. Saying nothing is the safe answer.
            return true;
        }
        return false;
    }

    /** Whether the pack has told BetterQuesting to re-import its defaults every time. */
    private static boolean loadsDefaultsOnStartup() {
        return flagInBqConfig("Load the default quest DB on world startup.", false);
    }

    /** Whether the pack has opened the questbook's admin commands to everybody. */
    public static boolean bqAdminUnrestricted() {
        return flagInBqConfig("Unrestrict Admin Commands", false);
    }

    /**
     * One boolean out of BetterQuesting's own config file, read as a data file.
     *
     * <p>
     * Read rather than asked, for the same reason everything else here is written rather than
     * called: their settings class is not on this mod's classpath and is never going to be. Both
     * flags decide whether something this mod is about to say would be wrong, so getting them from
     * the file that actually holds them is worth twenty lines.
     */
    private static boolean flagInBqConfig(String key, boolean fallback) {
        File configDir = Loader.instance()
            .getConfigDir();
        if (configDir == null) return fallback;
        File file = new File(configDir, BETTER_QUESTING + ".cfg");
        if (!file.isFile()) return fallback;
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("B:")) continue;
                    if (trimmed.indexOf(key) < 0) continue;
                    return trimmed.endsWith("true");
                }
            } finally {
                reader.close();
            }
        } catch (IOException unreadable) {
            return fallback;
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    public static void writeQuests() {
        if (!active()) return;
        try {
            File defaults = defaultsFolder();
            if (defaults == null || !defaults.isDirectory()) {
                Trmt.LOG.debug("No BetterQuesting DefaultQuests folder, so no quest line was written");
                return;
            }
            File lineFolder = new File(new File(defaults, "QuestLines"), folderName());
            File questFolder = new File(new File(defaults, "Quests"), folderName());
            File order = new File(defaults, "QuestLinesOrder.txt");
            if (isCurrent(defaults, lineFolder)) {
                // Still offered to the order file, which is the pack's and can be replaced under a
                // folder that is perfectly current. Appending is free when the entry is already
                // there, and a chapter missing from the order is a chapter with no tab.
                appendToOrder(order);
                Trmt.LOG.debug("The quest line is already current");
                return;
            }

            List<Quest> quests = build();
            if (quests.isEmpty()) return;

            wipe(lineFolder);
            wipe(questFolder);
            if (!ensure(lineFolder) || !ensure(questFolder)) return;

            write(new File(lineFolder, "QuestLine.json"), lineProperties().toString());
            for (Quest quest : quests) {
                write(new File(questFolder, quest.fileName()), quest.definition());
                write(new File(lineFolder, quest.fileName()), quest.placement());
            }
            appendToOrder(order);
            write(new File(defaults, STAMP), "generatedFor=" + stamp() + "\n");

            Trmt.LOG.info(
                "Wrote the '{}' quest line ({} quests). Run /bq_admin default load in game to pick it up.",
                LINE_NAME,
                Integer.valueOf(quests.size()));
        } catch (RuntimeException problem) {
            Trmt.LOG.warn("Could not write the quest line", problem);
        }
    }

    /**
     * What the folder was generated for.
     *
     * <p>
     * The three enchantment ids are part of it, not just the mod version, because they are config
     * assigned: a pack that moves one would otherwise keep a quest asking for a book that no longer
     * exists, and the stamp would insist everything was fine.
     *
     * <p>
     * The two golem switches are part of it for the same reason. They decide whether three quests
     * have anything to ask for at all, and a chapter written with them on, then left standing after
     * one was turned off, asked for upgrades no recipe made and hung the capstone on a quest
     * nobody could finish - while the stamp, which knew nothing of either, called it current.
     *
     * <p>
     * Each id is written as the id its lesson is taught at, which folds the lesson's own switch into
     * the same number: a lesson switched off is written as -1 whatever id it holds. The stamp used to
     * carry the ids alone, so a chapter written with path light on was still called current after it
     * went off, and kept a quest for a book nothing in the pack makes.
     */
    private static String stamp() {
        return com.trmtgtnh.Tags.VERSION + lessons()
            .stamp() + "/golems=" + TrmtConfig.golemEnabled + "/upgrades=" + TrmtConfig.golemUpgrades;
    }

    private static int effectId(net.minecraft.enchantment.Enchantment enchantment) {
        return enchantment == null ? -1 : net.minecraft.enchantment.Enchantment.getEnchantmentID(enchantment);
    }

    /** The lessons this pack can actually teach: each switch and each id together, as the recipes ask. */
    private static QuestLessons lessons() {
        return new QuestLessons(
            QuestLessons.taught(TrmtConfig.reinforceEnabled, effectId(EnchReinforce.INSTANCE)),
            QuestLessons.taught(TrmtConfig.wardEnabled, effectId(EnchWard.INSTANCE)),
            QuestLessons.taught(TrmtConfig.lightEnabled, effectId(EnchLight.INSTANCE)));
    }

    private static File defaultsFolder() {
        File own = TrmtConfig.raw() == null ? null
            : TrmtConfig.raw()
                .getConfigFile();
        File configDir = own == null ? null : own.getParentFile();
        if (configDir == null) return null;
        return new File(new File(configDir, BETTER_QUESTING), "DefaultQuests");
    }

    /** {@code TheRoadsMoreTra-<base64 uuid>}, which is how this folder names things. */
    private static String folderName() {
        return shorten(LINE_NAME) + "-" + uuid(1L);
    }

    /**
     * Whether the folder on disk is the one this build would write.
     *
     * <p>
     * The stamp lives at the root of the defaults folder and never inside the quest line, and that
     * is not tidiness. Everything under QuestLines and Quests is parsed file by file with no test
     * of the extension: a stray file in the quest folder is written over a real quest as a blank
     * one, and a stray file directly in QuestLines aborts the entire import - the pack's chapters
     * along with this one. The stamp used to sit inside the line folder, where it was read as a
     * quest, failed to parse, was copied aside as malformed_.trmt-version.json and left an empty
     * entry under the all-zero id - which in this pack is a real quest of its own, so the chapter
     * ended up holding a duplicate of somebody else's.
     *
     * <p>
     * The line folder is proved as well as the stamp, because the two can part company: the usual
     * way to update a pack's questbook is to delete the defaults folder and copy the new one in,
     * which takes the chapter away and would leave a stamp insisting all was well.
     */
    private static boolean isCurrent(File defaults, File lineFolder) {
        if (!new File(lineFolder, "QuestLine.json").isFile()) return false;
        File stamped = new File(defaults, STAMP);
        if (!stamped.isFile()) return false;
        try {
            byte[] bytes = new byte[(int) Math.min(stamped.length(), 4096L)];
            java.io.FileInputStream in = new java.io.FileInputStream(stamped);
            try {
                int read = in.read(bytes);
                return read > 0 && new String(bytes, 0, read, "UTF-8").contains(stamp());
            } finally {
                in.close();
            }
        } catch (IOException unreadable) {
            return false;
        }
    }

    /** Clears our own folder only, so a quest dropped in a later version really goes. */
    private static void wipe(File folder) {
        File[] existing = folder.listFiles();
        if (existing == null) return;
        for (File file : existing) {
            if (file.isFile() && !file.delete()) {
                Trmt.LOG.debug("Could not replace {}", file.getName());
            }
        }
    }

    private static boolean ensure(File folder) {
        if (folder.isDirectory() || folder.mkdirs()) return true;
        Trmt.LOG.warn("Could not create {}", folder);
        return false;
    }

    /**
     * Adds the line to the book's tab order, once.
     *
     * <p>
     * The only pack-owned file this class touches, and only ever by appending one line to the end
     * of it. If the entry is already present nothing is written at all, so a pack update that
     * replaces the file is repaired on the next launch and a pack update that keeps it is left
     * alone.
     */
    private static void appendToOrder(File order) {
        String entry = uuid(1L) + ": " + LINE_NAME;
        try {
            StringBuilder existing = new StringBuilder();
            if (order.isFile()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(order), "UTF-8"));
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(uuid(1L))) return; // already listed
                        existing.append(line)
                            .append('\n');
                    }
                } finally {
                    reader.close();
                }
            }
            existing.append(entry)
                .append('\n');
            write(order, existing.toString());
        } catch (IOException problem) {
            Trmt.LOG.warn("Could not add the quest line to the book's tab order", problem);
        }
    }

    private static void write(File file, String contents) {
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            try {
                writer.write(contents);
            } finally {
                writer.close();
            }
        } catch (IOException problem) {
            Trmt.LOG.warn("Could not write {}", file.getName(), problem);
        }
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /** The 16-byte id as base64, which is what this format uses for both names and folders. */
    private static String uuid(long low) {
        byte[] bytes = new byte[16];
        for (int index = 0; index < 8; index++) {
            bytes[index] = (byte) (ID_HIGH >>> (56 - index * 8));
            bytes[8 + index] = (byte) (low >>> (56 - index * 8));
        }
        return base64(bytes);
    }

    /**
     * The URL-safe base64 of exactly sixteen bytes, written out rather than borrowed.
     *
     * <p>
     * This used to call {@code javax.xml.bind.DatatypeConverter}, which is standard base64 and is
     * not in the runtime at all past Java 8 - it resolved only because this pack happens to ship a
     * copy. The names being built here are read back by the questbook with a URL-safe decoder, and
     * the two alphabets agree on every byte this mod has ever produced and part company on the two
     * highest. Twenty lines that cannot drift are worth more than a dependency that can vanish.
     */
    private static String base64(byte[] bytes) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < bytes.length; index += 3) {
            int left = bytes.length - index;
            int word = (bytes[index] & 0xFF) << 16;
            if (left > 1) word |= (bytes[index + 1] & 0xFF) << 8;
            if (left > 2) word |= bytes[index + 2] & 0xFF;
            out.append(alphabet.charAt((word >>> 18) & 0x3F));
            out.append(alphabet.charAt((word >>> 12) & 0x3F));
            out.append(left > 1 ? alphabet.charAt((word >>> 6) & 0x3F) : '=');
            out.append(left > 2 ? alphabet.charAt(word & 0x3F) : '=');
        }
        return out.toString();
    }

    /** The leading sixteen letters of a name, which is how the pack shortens its own. */
    private static String shorten(String name) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < name.length() && out.length() < 16; index++) {
            char letter = name.charAt(index);
            if (Character.isLetterOrDigit(letter)) out.append(letter);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // The line itself
    // ------------------------------------------------------------------

    private static Json lineProperties() {
        Json inner = Json.obj()
            .putText("bg_image:8", "")
            .put("bg_size:3", 256)
            .putText(
                "desc:8",
                "Ground remembers being walked on. This chapter covers noticing that, undoing it, and eventually handing the job to something that never gets bored. Mods covered: The Roads More Travelled.")
            .put("icon:10", stack(registryName(ModItems.magicTamper(), "minecraft:stone_shovel"), 1, 0))
            .putText("name:8", LINE_NAME)
            .putText("visibility:8", "NORMAL");
        return Json.obj()
            .put(
                "properties:10",
                Json.obj()
                    .put("betterquesting:10", inner))
            .put("questLineIDHigh:4", ID_HIGH)
            .put("questLineIDLow:4", 1L);
    }

    // ------------------------------------------------------------------
    // The quests
    // ------------------------------------------------------------------

    /**
     * The tree, laid out left to right.
     *
     * <p>
     * A spine along the middle - notice the wear, make a tamper, mend with it, make the chunk
     * tamper - with the three enchantments in the row above it, the books above those, and the golem
     * below, everything converging on one quest at the right hand end.
     *
     * <p>
     * Two things in this mod cannot be checked by any task BetterQuesting has: that you wore ground
     * down, and that you put it back. Those are checkboxes, which is what the pack itself uses for
     * every other objective that is a thing you did rather than a thing you hold.
     */
    private static List<Quest> build() {
        List<Quest> quests = new ArrayList<Quest>();

        String tamper = registryName(ModItems.gradedTamper(), null);
        String chunk = registryName(ModItems.chunkTamper(), null);
        String wayfarer = registryName(ModItems.magicTamper(), null);
        if (tamper == null || chunk == null) return quests;
        QuestLessons lessons = lessons();

        // --- the spine -------------------------------------------------
        Quest wear = quest(1, "The Ground Remembers", 0, 192).icon(stack("minecraft:grass", 1, 0))
            .main()
            .describe(
                "Walk the same line often enough and it starts to show. Grass gives way to bare earth, earth packs down into a path, and the ground sinks a little where your feet keep landing.\n\nNothing to craft here. Pick a route you actually use - between a door and a farm is ideal - and use it until you can see the difference.")
            .checkbox()
            .rewardBags(BAG_STEAM, 1)
            .rewardChoice("harvestcraft:toastItem", "harvestcraft:friedeggItem", "harvestcraft:butterItem");
        quests.add(wear);

        Quest heal = quest(2, "And Forgets", 48, 96).icon(stack("minecraft:tallgrass", 1, 1))
            .after(wear)
            .describe(
                "Stop walking somewhere and it comes back on its own. Slowly - a stage takes tens of in-game days - but it does.\n\n[note]Point Waila at a worn block to see how far along it is and how long it has left. That readout is the fastest way to understand the whole mod.[/note]")
            .checkbox()
            .rewardBags(BAG_STEAM, 1)
            .rewardChoice("harvestcraft:heartybreakfastItem", "harvestcraft:potatocakesItem");
        quests.add(heal);

        Quest makeTamper = quest(3, "A Tamper of Your Own", 96, 192).icon(stack(tamper, 1, 0))
            .main()
            .after(wear)
            .describe(
                "A tamper packs ground back the way it was. Any of them will do here - they differ in how much they mend per swing and how long they last, not in what they do.\n\nBetter materials are strictly better, so make the best one you can be bothered to make.")
            .retrieve(tamper, 1, 32767)
            .rewardBags(BAG_STEAM, 2);
        quests.add(makeTamper);

        Quest mend = quest(4, "Pack It Down", 144, 192).icon(stack("minecraft:dirt", 1, 0))
            .after(makeTamper)
            .describe(
                "Right-click worn ground with the tamper. It costs durability and the material to fill it back in - a couple of blocks of whatever the ground drops, for each kind of ground the patch mends - so carry some of whatever you are standing on.\n\n[warn]A tamper mends a small patch per swing. The chunk tamper in the next quest is the answer to that, not more swings.[/warn]")
            .checkbox()
            .rewardBags(BAG_STEAM, 1)
            .rewardChoice("harvestcraft:supremepizzaItem", "harvestcraft:rainbowcurryItem");
        quests.add(mend);

        Quest chunkTamper = quest(5, "By the Cubic Yard", 192, 192).icon(stack(chunk, 1, 0))
            .main()
            .after(makeTamper)
            .describe(
                "The same job over an area instead of a block. It must be built from a tamper of its own material - a diamond chunk tamper wants a diamond tamper, not whichever one you had spare."
                    + lessons.chunkTamperCloses())
            .retrieve(chunk, 1, 32767)
            .rewardBags(BAG_STEAM, 2)
            .rewardChoice("harvestcraft:meatfeastpizzaItem", "harvestcraft:epicbaconItem");
        quests.add(chunkTamper);

        // --- the books -------------------------------------------------
        Quest mk1 = book(6, GuideBook.MK1, "The Wayfarer's Guide", 192, 0).after(makeTamper)
            .describe(
                "Everything the mod does, in order, in a book you can carry. Worth reading before the rest of this chapter rather than after it.")
            .rewardBags(BAG_STEAM, 1);
        addIfReal(quests, mk1);

        Quest mk2 = book(7, GuideBook.MK2, "Down to the Numbers", 240, 0).after(mk1)
            .describe(
                "The second volume: what each family costs to mend, how the stages are counted, and what every config option actually changes.")
            .rewardBags(BAG_STEAM, 1);
        addIfReal(quests, mk2);

        Quest commands = book(8, GuideBook.COMMANDS, "Chapter and Verse", 288, 0).after(mk1)
            .describe(
                "The commands, including the demonstration ones - which build a labelled row of every wear stage in front of you and are by far the quickest way to see what the mod is doing.")
            .rewardBags(BAG_STEAM, 1);
        addIfReal(quests, commands);

        // --- the enchantments ------------------------------------------
        //
        // Asked of the switches as well as the ids, through QuestLessons. The id is claimed whatever the switch
        // says, so a quest asking only for the id went on asking for a book no recipe and no loot supplies once its
        // feature was off. An untaught lesson leaves its quest without a task, so addIfReal drops it and after()
        // re-parents whatever named it, as the golem's quests already do.
        Quest reinforce = quest(9, "Set in Stone", 240, 96).icon(bookIcon(lessons.reinforce()))
            .after(chunkTamper)
            .describe(
                "Reinforcing makes a block harder to wear and, at full level, proof against a blast. The lesson comes as a book; put it on a chunk tamper and the tool gains a mode.\n\n[note]Left-click the same block twice to take a level of reinforcement back off. It costs nothing.[/note]")
            .retrieveEnchantedBook(lessons.reinforce())
            .rewardBags(BAG_STEAM, 2);
        addIfReal(quests, reinforce);

        Quest ward = quest(10, "Nothing Spawns Here", 288, 96).icon(bookIcon(lessons.ward()))
            .after(chunkTamper)
            .describe(
                "Warding stops things spawning on a block - hostile, passive, or both, depending on which hand you use.\n\nA road nothing crawls out of at night is worth rather more than it sounds.")
            .retrieveEnchantedBook(lessons.ward())
            .rewardBags(BAG_STEAM, 2);
        addIfReal(quests, ward);

        // The note used to promise that a road lit above light level eight is a road nothing spawns on. Spawns are
        // decided in the server's world, which never holds a ghost, so the glow a ghost gives changes no light
        // there - which the pathlight category comment has always said.
        Quest pathLight = quest(17, "Lit From Below", 192, 96).icon(bookIcon(lessons.light()))
            .after(chunkTamper)
            .describe(
                "Wayfinding makes ground glow, in any of sixteen colours. Click a lit square again to step the colour on, left-click to step it back, sneak and click to put it out.\n\n[note]Only worn ground can be lit - the glow comes from the ghost this mod paints over it, so wear the path before you light it. It is light to see by and nothing more: things still spawn on a lit road.[/note]")
            .retrieveEnchantedBook(lessons.light())
            .rewardBags(BAG_STEAM, 2);
        addIfReal(quests, pathLight);

        // The words give the rule rather than naming a metal. The chapter is written before any recipe
        // is registered - writeQuests runs in Trmt.postInit ahead of proxy.postInit, where ModRecipes
        // decides - so which grade the Wayfarer was built from is not yet known, and a sentence naming
        // one would be wrong on exactly the packs that most need telling. The enhancements are left out
        // of the rule because the chapter is only written with them on. How many lessons it takes is
        // QuestLessons' to say, from the switches.
        Quest theWayfarer = quest(11, "The Wayfarer", 336, 144).icon(stack(wayfarer == null ? tamper : wayfarer, 1, 0))
            .main()
            .after(reinforce)
            .after(ward)
            .after(pathLight)
            .describe(
                lessons.wayfarerOpens()
                    + "\n\nIt is built around a chunk tamper with a nether star set above it: a netherite chunk tamper where the pack can make one, a diamond one where it cannot, and where neither can be made, the longest-lasting chunk tamper the pack can make. Look up its recipe to see which.")
            .retrieve(wayfarer, 1, 0)
            .rewardBags(BAG_LV, 1);
        // With no lesson taught after() has skipped all three, and a quest with no prerequisite is open from the
        // first minute with no line to anything, as quest 1 is. The Wayfarer is built from a chunk tamper, so that
        // is what it hangs off instead. Asked of the result rather than of the count, so the two cannot disagree.
        if (!theWayfarer.hasPrerequisites()) theWayfarer.after(chunkTamper);
        if (wayfarer != null) quests.add(theWayfarer);

        // --- the golem -------------------------------------------------
        //
        // Asked of the switches rather than of the items. Every golem item is registered whatever
        // those switches say, because which items exist is compared when a client connects and may
        // not depend on a setting - so a non-null item proves nothing about whether anybody can
        // obtain one. Both switches, because the recipes need both: with either off nothing makes
        // an upgrade and nothing stands a golem up.
        //
        // The quests below are then left WITHOUT tasks rather than left out of the list. That is
        // the difference between fixing this and moving it: a quest missing from the list is still
        // named by whatever depended on it, so the chapter would ship a prerequisite pointing at a
        // quest that was never written. Task-less, the machinery already here drops them and
        // re-parents the capstone onto whatever is left, for free.
        boolean golems = TrmtConfig.golemEnabled;
        boolean upgrades = golems && TrmtConfig.golemUpgrades;

        // The price sentence follows GolemWork.PRICED_FROM_THE_CHUNK_TAMPER, as the guide's price page and the
        // config comments do, so turning the price back to the flat one cannot leave the quest describing the other.
        // A questbook that has already imported the chapter keeps the words it was given until it is imported again.
        Quest golem = quest(12, "It Lives", 240, 288).icon(stack("minecraft:skull", 1, 3))
            .after(chunkTamper)
            .describe(
                "Build it the way you would an iron golem: packed earth under packed stone, arms either side, and any player's head placed last.\n\nIt is cheap to stand up and expensive to run. It needs a tamper in its inventory to mend with and the blocks to mend from, and "
                    + (GolemWork.PRICED_FROM_THE_CHUNK_TAMPER
                        ? "it spends the blocks twice as fast as you would, though the tamper no faster."
                        : "it spends two blocks for every step it puts back, whatever tamper it holds, though it wears the tamper no faster."))
            .rewardBags(BAG_LV, 1);
        if (golems) golem.checkbox();
        addIfReal(quests, golem);

        Quest golemBook = book(13, GuideBook.GOLEM, "Keeper of Paths", 288, 288).after(golem)
            .describe(
                "How to build it, what to put in it, and what each upgrade changes - the loose All Ways included.")
            .rewardBags(BAG_STEAM, 1);
        addIfReal(quests, golemBook);

        Quest eightWays = quest(14, "Nine Ways to Work", 336, 288)
            .icon(upgradeIcon(GolemUpgrade.RANGE, "minecraft:redstone"))
            .after(golem)
            .describe(
                "One upgrade at a time, nine in all: further, faster, gentler on tools, cheaper on material, deeper pockets, one whose ground breathes instead of holding still, one that doubles its health, one that doubles its blow, and one that lets it work out of the chests around it.\n\nOnly one can be fitted at once, which is the whole point of the quest after this one.");
        if (upgrades) {
            for (GolemUpgrade upgrade : GolemUpgrade.real()) {
                if (upgrade.carriesTheSet()) continue;
                String name = registryName(ModItems.upgrade(upgrade), null);
                if (name != null) eightWays.retrieve(name, 1, 0);
            }
        }
        eightWays.rewardBags(BAG_LV, 2)
            .rewardChoice("harvestcraft:wovencottonItem", "harvestcraft:grainbaitItem");
        // A checkbox only where there is a golem to do the work and merely no upgrades to fit;
        // with the golem itself switched off this stays task-less and drops out altogether.
        if (golems && !eightWays.hasTasks()) eightWays.checkbox();
        addIfReal(quests, eightWays);

        String omniName = registryName(ModItems.upgrade(GolemUpgrade.OMNI), null);
        Quest omni = quest(15, "All Ways at Once", 384, 240)
            .icon(stack(omniName == null ? "minecraft:nether_star" : omniName, 1, 0))
            .main()
            .after(eightWays)
            .describe(
                "Every upgrade in one part, and it costs all nine of them - which is a full crafting grid with nowhere left for the nether star. So the nine bind into an unstable version first, which carries the whole set and holds on to only some of it, and the star settles that into the real thing.\n\nNothing else in this mod costs as much, and nothing else makes the golem worth leaving alone for a week.")
            .rewardBags(BAG_LV, 3);
        if (upgrades && omniName != null) omni.retrieve(omniName, 1, 0);
        addIfReal(quests, omni);

        // --- the end ---------------------------------------------------
        Quest done = quest(16, "The Roads More Travelled", 432, 192)
            .icon(stack(wayfarer == null ? chunk : wayfarer, 1, 0))
            .main()
            // Whichever of the two actually got written. after() already refuses a quest with
            // no tasks, so naming both is how the capstone re-parents itself when the golem
            // half of the chapter is not there at all.
            .after(omni)
            .after(eightWays)
            .after(theWayfarer)
            .after(mk2)
            .after(commands)
            .after(golemBook)
            .describe(
                "Every road in this chapter walked, worn, mended and handed over.\n\nTake the tamper if you want the tool, the bags if you want the contents, or the eggs if you would rather never lay another path yourself.")
            .checkbox()
            .rewardBags(BAG_STEAM, 8);
        Json finale = Json.obj();
        int choice = 0;
        if (wayfarer != null) finale.add(choice++, maxedWayfarer(wayfarer, lessons));
        finale.add(choice++, stack(LOOT_BAG, 4, BAG_LV));
        String egg = registryName(ModItems.golemEgg(), null);
        if (egg != null) finale.add(choice++, stack(egg, 4, 0));
        done.rewardChoices(finale);
        quests.add(done);

        return quests;
    }

    /** A quest whose item may not exist in this pack is simply not written. */
    private static void addIfReal(List<Quest> into, Quest quest) {
        if (quest.hasTasks()) into.add(quest);
    }

    private static Quest book(int id, GuideBook which, String title, int x, int y) {
        String name = registryName(ModItems.guide(which), null);
        Quest quest = quest(id, title, x, y);
        if (name != null) {
            quest.icon(stack(name, 1, 0))
                .retrieve(name, 1, 0);
        }
        return quest;
    }

    /** An enchanted book carrying exactly one of ours, which is what our recipe makes. */
    private static Json bookIcon(int effectId) {
        Json icon = stack("minecraft:enchanted_book", 1, 0);
        if (effectId >= 0) icon.put("tag:10", storedEnchantment(effectId));
        return icon;
    }

    private static Json storedEnchantment(int effectId) {
        return Json.obj()
            .put(
                "StoredEnchantments:9",
                Json.obj()
                    .add(
                        0,
                        Json.obj()
                            .put("id:2", effectId)
                            .put("lvl:2", 1L)));
    }

    /**
     * The prize: a Wayfarer already carrying every lesson this pack teaches, and only those.
     *
     * <p>
     * A lesson switched off used to go on it all the same, and path light, the third, never did. Each is at level
     * one, the only level any of them has: reinforcing used to be written at reinforce.maxLevel, which is how many
     * times a block may be reinforced rather than a level of the enchantment, and the tooltip showed it as a third
     * level of an enchantment that has one.
     */
    private static Json maxedWayfarer(String wayfarer, QuestLessons lessons) {
        Json enchantments = Json.obj();
        int index = 0;
        for (int id : lessons.prize()) {
            enchantments.add(
                index++,
                Json.obj()
                    .put("id:2", id)
                    .put("lvl:2", 1L));
        }
        Json prize = stack(wayfarer, 1, 0);
        if (index > 0) {
            prize.put(
                "tag:10",
                Json.obj()
                    .put("ench:9", enchantments));
        }
        return prize;
    }

    private static Json upgradeIcon(GolemUpgrade upgrade, String fallback) {
        String name = registryName(ModItems.upgrade(upgrade), null);
        return stack(name == null ? fallback : name, 1, 0);
    }

    private static Json stack(String id, int count, int damage) {
        return Json.obj()
            .put("Count:3", count)
            .put("Damage:2", damage)
            .putText("OreDict:8", "")
            .putText("id:8", id);
    }

    private static String registryName(Item item, String fallback) {
        if (item == null) return fallback;
        net.minecraft.util.ResourceLocation name = item.getRegistryName();
        return name == null ? fallback : name.toString();
    }

    private static Quest quest(int id, String title, int x, int y) {
        return new Quest(id, title, x, y);
    }

    // ------------------------------------------------------------------
    // One quest, built up a piece at a time
    // ------------------------------------------------------------------

    private static final class Quest {

        private final long id;

        private final String title;

        private final int x;

        private final int y;

        private final Json tasks = Json.obj();

        private final Json rewards = Json.obj();

        private final Json prerequisites = Json.obj();

        private Json icon = stack("minecraft:paper", 1, 0);

        private String description = "";

        private boolean main;

        private int taskIndex;

        private int rewardIndex;

        private int prerequisiteIndex;

        Quest(int id, String title, int x, int y) {
            this.id = id;
            this.title = title;
            this.x = x;
            this.y = y;
        }

        Quest icon(Json what) {
            icon = what;
            return this;
        }

        Quest main() {
            main = true;
            return this;
        }

        Quest describe(String text) {
            description = text;
            return this;
        }

        boolean hasTasks() {
            return !tasks.isEmpty();
        }

        boolean hasPrerequisites() {
            return !prerequisites.isEmpty();
        }

        /** Depends on another quest - skipped silently when that one was not written. */
        Quest after(Quest other) {
            if (other == null || !other.hasTasks()) return this;
            prerequisites.add(
                prerequisiteIndex++,
                Json.obj()
                    .put("questIDHigh:4", ID_HIGH)
                    .put("questIDLow:4", other.id));
            return this;
        }

        Quest checkbox() {
            tasks.add(
                taskIndex,
                Json.obj()
                    .put("index:3", taskIndex)
                    .putText("taskID:8", "bq_standard:checkbox"));
            taskIndex++;
            return this;
        }

        Quest retrieve(String item, int count, int damage) {
            if (item == null) return this;
            return retrieve(stack(item, count, damage), true);
        }

        private Quest retrieve(Json what, boolean ignoreNBT) {
            tasks.add(
                taskIndex,
                Json.obj()
                    .put("autoConsume:1", 0L)
                    .put("consume:1", 0L)
                    .put("groupDetect:1", 0L)
                    .put("ignoreNBT:1", ignoreNBT ? 1L : 0L)
                    .put("index:3", taskIndex)
                    .put("partialMatch:1", 1L)
                    .put(
                        "requiredItems:9",
                        Json.obj()
                            .add(0, what))
                    .putText("taskID:8", "bq_standard:retrieval"));
            taskIndex++;
            return this;
        }

        /**
         * A book carrying one of our lessons.
         *
         * <p>
         * NBT is matched rather than ignored, because every enchanted book in the game is the same
         * item and only the tag tells them apart. Partial matching keeps it forgiving about
         * anything else the book happens to be carrying. An id below nought is a lesson this pack does
         * not teach; no task is written, which is what drops the quest.
         */
        Quest retrieveEnchantedBook(int effectId) {
            if (effectId < 0) return this;
            Json book = stack("minecraft:enchanted_book", 1, 0);
            book.put("tag:10", storedEnchantment(effectId));
            return retrieve(book, false);
        }

        Quest rewardBags(int meta, int count) {
            return rewardItems(stack(LOOT_BAG, count, meta));
        }

        private Quest rewardItems(Json what) {
            rewards.add(
                rewardIndex,
                Json.obj()
                    .put("ignoreDisabled:1", 0L)
                    .put("index:3", rewardIndex)
                    .putText("rewardID:8", "bq_standard:item")
                    .put(
                        "rewards:9",
                        Json.obj()
                            .add(0, what)));
            rewardIndex++;
            return this;
        }

        Quest rewardChoice(String... items) {
            Json choices = Json.obj();
            int index = 0;
            for (String item : items) {
                choices.add(index++, stack(item, 2, 0));
            }
            return rewardChoices(choices);
        }

        Quest rewardChoices(Json choices) {
            if (choices.isEmpty()) return this;
            rewards.add(
                rewardIndex,
                Json.obj()
                    .put("choices:9", choices)
                    .put("ignoreDisabled:1", 0L)
                    .put("index:3", rewardIndex)
                    .putText("rewardID:8", "bq_standard:choice"));
            rewardIndex++;
            return this;
        }

        String fileName() {
            return shorten(title) + "-" + uuid(id) + ".json";
        }

        String definition() {
            Json properties = Json.obj()
                .put("autoClaim:1", 0L)
                .putText("desc:8", description)
                .put("globalShare:1", 0L)
                .put("icon:10", icon)
                .put("isGlobal:1", 0L)
                .put("isMain:1", main ? 1L : 0L)
                .put("isSilent:1", 0L)
                .put("lockedProgress:1", 0L)
                .putText("name:8", title)
                .put("partySingleReward:1", 0L)
                .putText("questLogic:8", "AND")
                .put("repeatTime:3", -1L)
                .put("repeat_relative:1", 1L)
                .put("simultaneous:1", 0L)
                .putText("snd_complete:8", "random.levelup")
                .putText("snd_update:8", "random.levelup")
                .putText("taskLogic:8", "AND")
                .putText("visibility:8", "NORMAL");

            Json quest = Json.obj();
            if (!prerequisites.isEmpty()) quest.put("preRequisites:9", prerequisites);
            quest.put(
                "properties:10",
                Json.obj()
                    .put("betterquesting:10", properties))
                .put("questIDHigh:4", ID_HIGH)
                .put("questIDLow:4", id);
            if (!rewards.isEmpty()) quest.put("rewards:9", rewards);
            quest.put("tasks:9", tasks);
            return quest.toString();
        }

        String placement() {
            return Json.obj()
                .put("questIDHigh:4", ID_HIGH)
                .put("questIDLow:4", id)
                .put("sizeX:3", 24L)
                .put("sizeY:3", 24L)
                .put("x:3", x)
                .put("y:3", y)
                .toString();
        }
    }
}
