package com.trmtgtnh.compat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.stats.Achievement;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.item.GuideBook;
import com.trmtgtnh.item.ModAchievements;
import com.trmtgtnh.item.ModItems;

import cpw.mods.fml.common.Loader;

/**
 * Trophies for the things worth being smug about, when Amazing Trophies is installed.
 *
 * <p>
 * Not a single line of Amazing Trophies is imported, called, or reflected into. That mod reads
 * trophy definitions out of {@code config/amazingtrophies/trophies/} as JSON, so the whole of this
 * integration is writing files into a folder it already watches - which means it cannot break when
 * their API changes, cannot crash when the mod is absent, and needs nothing on the compile
 * classpath.
 *
 * <p>
 * Inside their tree rather than beside it, which was the first of three things this got wrong and
 * shipped. {@code config/trophies} was an assumption and nothing has ever read it; that mod roots
 * everything at its own modid and looks in exactly two places under it. A subfolder of this mod's
 * own inside theirs is both legal and safe: their reader walks the whole tree and takes anything
 * ending in .json, which is why the pack's own definitions already sit in folders of their own, and
 * an id prefix nobody else uses means a collision cannot happen. The stamp is deliberately not
 * named .json for the same reason - a file they tried to parse would put a failure in the log every
 * launch.
 *
 * <p>
 * Every trophy hangs off an achievement the mod already awards, so the earning conditions live in
 * exactly one place. It hangs off the achievement <em>object</em> rather than off a copy of its
 * name, which was the second thing this got wrong: their condition matches on the id Forge files an
 * achievement under, this mod files its achievements under "achievement." and a name, and the
 * definitions written here carried the name alone. Nothing could ever match. Asking the achievement
 * for its own id means the two cannot drift apart again.
 *
 * <p>
 * The folder carries a fingerprint of what was written into it, not merely the version that wrote
 * it. A stale list of trophies for things that no longer exist is the obvious failure, and a
 * version stamp catches that; a list whose conditions are silently wrong is the failure that
 * actually happened, and only a fingerprint catches that within one version. A player who has
 * hand-edited the files still gets left alone until this mod's own definitions change, which is the
 * same bargain every config in the game makes.
 *
 * <p>
 * The tree belongs to the pack, so a pack update that replaces {@code config/amazingtrophies} takes
 * this folder with it. That is self-healing rather than a problem: the fingerprint goes too, and the
 * next launch writes everything again.
 */
public final class TrophyCompat {

    /** The mod that reads these files. Named, never loaded. */
    private static final String AMAZING_TROPHIES = "amazingtrophies";

    /** Our own subfolder inside theirs, so nothing we write can collide with anyone else's. */
    private static final String FOLDER = "trmtgtnh";

    /** Written beside the trophies; holds the version the folder was generated for. */
    private static final String STAMP = ".trmt-version";

    private TrophyCompat() {}

    /**
     * Whether trophies should exist at all in this pack.
     *
     * <p>
     * Deliberately blind to {@code integration.gtnhEnhanced}. A trophy is earned through an
     * achievement that exists with or without the enhancements and changes nothing a pack costs, so
     * it has no plainer version to fall back to. Taken deliberately, after an earlier pass had
     * recorded the opposite as a defect; the enhancements' comment says so, and
     * IntegrationWordsTest fails if this starts asking it.
     *
     * <p>
     * False stops {@link #writeDefinitions} before it reaches the folder, so whatever an earlier launch
     * wrote stays there. Clearing it waits until it is known what Amazing Trophies does with an
     * earned trophy whose definition has gone.
     */
    public static boolean active() {
        return TrmtConfig.trophies && TrmtConfig.achievements && Loader.isModLoaded(AMAZING_TROPHIES);
    }

    /**
     * Write the trophy definitions, if they are wanted and not already current.
     *
     * <p>
     * Called in init, after the items exist and before Amazing Trophies reads its folder. Anything
     * that goes wrong is logged and swallowed: a missing trophy is a cosmetic disappointment, and
     * nothing here is worth taking a world down for.
     */
    public static void writeDefinitions() {
        try {
            // Before the switch, so that turning trophies off, or taking the other mod out, tidies
            // up after the version that wrote to the wrong place.
            sweepStray();
            if (!active()) return;

            File folder = folder();
            if (folder == null) return;

            // Defined before the currency test rather than after it, because what is defined is
            // what the test now compares against.
            List<Trophy> trophies = define();
            if (trophies.isEmpty()) return;
            String fingerprint = fingerprint(trophies);
            if (isCurrent(folder, fingerprint)) {
                Trmt.LOG.debug("Trophy definitions are already current");
                return;
            }

            // mkdirs rather than mkdir: at this point in the launch the trophies folder itself may
            // not exist yet, since the mod that owns it creates it in a phase this one precedes.
            if (!folder.isDirectory() && !folder.mkdirs()) {
                Trmt.LOG.warn("Could not create the trophy folder at {}", folder);
                return;
            }
            clearOurs(folder);
            boolean whole = true;
            for (Trophy trophy : trophies) {
                whole &= write(new File(folder, trophy.id + ".json"), trophy.json());
            }
            // Only over a folder that actually holds what the fingerprint describes. Stamping one
            // where every write failed would have it report itself current for ever afterwards.
            if (!whole) {
                Trmt.LOG.warn("Some trophy definitions could not be written; leaving the folder unstamped");
                return;
            }
            write(
                new File(folder, STAMP),
                "This folder is generated by " + Trmt.NAME
                    + ". Edits are kept until the definitions below change, at which point the folder is rewritten.\n"
                    + stampBody()
                    + "content="
                    + fingerprint
                    + "\n");
            Trmt.LOG.info("Wrote {} trophy definitions to {}", Integer.valueOf(trophies.size()), folder);
        } catch (RuntimeException problem) {
            Trmt.LOG.warn("Could not write the trophy definitions", problem);
        }
    }

    /** The version string this build stamps its folder with. */
    private static String version() {
        return com.trmtgtnh.Tags.VERSION;
    }

    /** Included in the stamp so the file reads as something rather than a bare number. */
    private static String stampBody() {
        return "generatedFor=" + version() + "\n";
    }

    /**
     * {@code config/amazingtrophies/trophies/trmtgtnh}, resolved the way the reader resolves it.
     *
     * <p>
     * Off Forge's config directory rather than off this mod's own config file, because the point is
     * to land in the same place the other mod is looking and the other mod asks Forge. Deriving it
     * from somewhere else is how the two came to disagree in the first place.
     */
    private static File folder() {
        File configDir = Loader.instance()
            .getConfigDir();
        if (configDir == null) {
            Trmt.LOG.warn("Could not locate the config directory, so no trophies were written");
            return null;
        }
        return new File(new File(new File(configDir, AMAZING_TROPHIES), "trophies"), FOLDER);
    }

    /** Where the first version of this wrote, and where nothing has ever read. */
    private static File strayFolder() {
        File configDir = Loader.instance()
            .getConfigDir();
        return configDir == null ? null : new File(configDir, "trophies");
    }

    /**
     * Whether the folder already holds exactly these definitions.
     *
     * <p>
     * Compared against a fingerprint of what would be written rather than against the version that
     * wrote it, and the difference is not academic: the trophies shipped for several versions with
     * a condition that could never match, and a version stamp would have gone on reporting each of
     * those folders current. It is also an exact line match rather than a substring of the whole
     * file, which is how 0.9.12 would have been read as current by 0.9.1.
     */
    private static boolean isCurrent(File folder, String fingerprint) {
        File stamp = new File(folder, STAMP);
        if (!stamp.isFile()) return false;
        try {
            byte[] bytes = new byte[(int) Math.min(stamp.length(), 8192L)];
            java.io.FileInputStream in = new java.io.FileInputStream(stamp);
            try {
                int read = in.read(bytes);
                if (read <= 0) return false;
                String[] lines = new String(bytes, 0, read, "UTF-8").split("\\r?\\n");
                for (String line : lines) {
                    if (("content=" + fingerprint).equals(line.trim())) return true;
                }
                return false;
            } finally {
                in.close();
            }
        } catch (IOException unreadable) {
            return false;
        }
    }

    /**
     * A number that changes whenever any part of what would be written changes.
     *
     * <p>
     * Every id, every condition and every model, in list order, so a trophy added, removed,
     * renamed or repointed all move it. That is what makes a fix to a condition reach a player who
     * is already on this version, which the version stamp alone could not do.
     */
    private static String fingerprint(List<Trophy> trophies) {
        StringBuilder everything = new StringBuilder();
        for (Trophy trophy : trophies) {
            everything.append(trophy.json());
        }
        java.util.zip.CRC32 sum = new java.util.zip.CRC32();
        try {
            sum.update(
                everything.toString()
                    .getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException impossible) {
            return version();
        }
        return Long.toHexString(sum.getValue());
    }

    /**
     * Removes the folder the first version of this wrote to, which nothing has ever read.
     *
     * <p>
     * Run before the switch that decides whether trophies are wanted at all, so turning them off or
     * taking Amazing Trophies out clears it up as well. Only the files this mod put there go, and
     * only if nothing else has appeared beside them: a folder holding anything unexpected is
     * somebody else's now and is left exactly as it stands.
     */
    private static void sweepStray() {
        File stray = strayFolder();
        if (stray == null) return;
        File ours = new File(stray, FOLDER);
        if (!ours.isDirectory()) return;
        File[] inside = ours.listFiles();
        if (inside == null) return;
        for (File file : inside) {
            if (!file.isFile()) return;
            String name = file.getName();
            if (!name.endsWith(".json") && !STAMP.equals(name)) return;
        }
        for (File file : inside) {
            if (!file.delete()) return;
        }
        if (!ours.delete()) return;
        File[] rest = stray.listFiles();
        if (rest != null && rest.length == 0) stray.delete();
        Trmt.LOG.info("Removed the old trophy folder at {}, which nothing ever read", ours);
    }

    /**
     * Remove the JSON we wrote last time, so a trophy dropped from a later version really goes.
     *
     * <p>
     * Only our own folder and only {@code .json} files in it; nothing outside is ever touched.
     */
    private static void clearOurs(File folder) {
        File[] existing = folder.listFiles();
        if (existing == null) return;
        for (File file : existing) {
            if (file.isFile() && file.getName()
                .endsWith(".json") && !file.delete()) {
                Trmt.LOG.debug("Could not replace the trophy file {}", file.getName());
            }
        }
    }

    /**
     * @return false when nothing landed, so the stamp is not written over a folder that failed
     */
    private static boolean write(File file, String contents) {
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            try {
                writer.write(contents);
            } finally {
                writer.close();
            }
            return true;
        } catch (IOException problem) {
            Trmt.LOG.warn("Could not write {}", file.getName(), problem);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // What gets a trophy
    // ------------------------------------------------------------------

    /**
     * The list, in the order somebody would earn them.
     *
     * <p>
     * Deliberately not one per achievement. A trophy is a thing you put on a shelf, so it is for the
     * ends of the ladders - the Wayfarer, each of the three lessons bound into a book, the golem
     * stood up, and the
     * upgrade that costs a nether star - plus one for having read everything, which is the only way
     * to collect the books.
     */
    private static List<Trophy> define() {
        List<Trophy> trophies = new ArrayList<Trophy>();

        if (ModItems.magicTamper() != null) {
            add(trophies, "trmt_wayfarer", ModAchievements.wayfarer(), name(ModItems.magicTamper()));
        }
        add(trophies, "trmt_reinforcing", ModAchievements.bookReinforce(), "minecraft:enchanted_book");
        add(trophies, "trmt_warding", ModAchievements.bookWard(), "minecraft:enchanted_book");
        add(trophies, "trmt_lighting", ModAchievements.bookLight(), "minecraft:enchanted_book");

        if (ModItems.golemEgg() != null) {
            add(trophies, "trmt_golem", ModAchievements.golemBuilt(), name(ModItems.golemEgg()));
        }
        com.trmtgtnh.item.ItemGolemUpgrade omni = ModItems.upgrade(com.trmtgtnh.entity.GolemUpgrade.OMNI);
        if (omni != null) {
            add(trophies, "trmt_omni", ModAchievements.omni(), name(omni));
        }

        // The whole shelf, which is now an achievement in its own right rather than whichever book
        // the enum happens to end with.
        GuideBook last = null;
        for (GuideBook book : GuideBook.values()) {
            if (ModItems.guide(book) != null) last = book;
        }
        if (last != null) {
            add(trophies, "trmt_library", ModAchievements.library(), name(ModItems.guide(last)));
        }
        return trophies;
    }

    /**
     * Adds one, unless there is nothing for it to hang off.
     *
     * <p>
     * A missing achievement means no trophy at all rather than a trophy with an empty condition:
     * their reader falls back to the trophy's own id when the condition names nothing, and a
     * condition that quietly matches nothing is worse than an absence, because it looks installed.
     * Achievements can be missing for ordinary reasons - the whole feature switched off, or a base
     * item another mod prevented from registering.
     */
    private static void add(List<Trophy> into, String id, Achievement achievement, String model) {
        String stat = ModAchievements.statId(achievement);
        if (stat == null || model == null) return;
        into.add(new Trophy(id, stat, model));
    }

    /** An item's registry name, which is what the trophy model asks for. */
    private static String name(net.minecraft.item.Item item) {
        String registered = net.minecraft.item.Item.itemRegistry.getNameForObject(item);
        return registered == null ? "minecraft:stone" : registered;
    }

    /**
     * One trophy definition.
     *
     * <p>
     * Hand-built JSON rather than a serialiser, because the whole schema is three keys and pulling
     * in a dependency to write nine lines would be the tail wagging the dog.
     *
     * <p>
     * The condition holds a stat id, which is the string an achievement is filed under and the only
     * one that reaches anybody outside this mod. It arrives here already resolved from the
     * achievement itself, so there is no second copy of a name to fall out of step with the first.
     */
    private static final class Trophy {

        final String id;

        /** The stat id of the achievement that earns it. */
        private final String achievement;

        private final String model;

        Trophy(String id, String achievement, String model) {
            this.id = id;
            this.achievement = achievement;
            this.model = model;
        }

        String json() {
            return "{\n" + "  \"id\": \""
                + id
                + "\",\n"
                + "  \"condition\": {\n"
                + "    \"type\": \"achievement\",\n"
                + "    \"id\": \""
                + achievement
                + "\"\n"
                + "  },\n"
                + "  \"model\": {\n"
                + "    \"type\": \"item\",\n"
                + "    \"registryName\": \""
                + model
                + "\"\n"
                + "  }\n"
                + "}\n";
        }
    }
}
