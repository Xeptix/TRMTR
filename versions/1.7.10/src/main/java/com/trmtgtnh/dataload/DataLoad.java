package com.trmtgtnh.dataload;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.apihost.PluginHost;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.data.DataKind;
import com.trmtgtnh.data.DataProblems;
import com.trmtgtnh.data.DataTable;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * The 1.7.10 edition's data read (W3 spec 2.2 and 2.3, task T4): when the data files are read, the table they make,
 * and the one summary line each read ends with.
 *
 * <p>
 * <strong>When.</strong> At pre-init, straight after the config is loaded and before the first
 * {@code SurfaceRegistry.resolve} (spec 2.2: "1.7.10 and 1.12.2 at pre-init after the config"); again on
 * {@code /trmt reload} ({@code ConfigReload.fromDisk}); and on a client, whenever a resource reload finds that the
 * enabled packs give other data files than before ({@code client.PackData}). Each read builds a new table and
 * publishes it whole, as {@code resolve} publishes its own, so a reader on another thread sees one read or the next and
 * never half of one.
 *
 * <p>
 * <strong>What is held.</strong> The mod files' and the config folder's files from the last full read, and the
 * client's resource packs' from the last resource reload, so a pack change re-merges without opening every jar again
 * and a {@code /trmt reload} on the integrated server's thread never touches the client's pack list.
 *
 * <p>
 * <strong>What it changes in play</strong> (T5): {@code SurfaceRegistry.resolve} reads its families, cover,
 * resistance and repair ({@code SurfaceData}) and folds its fingerprint into the table's; {@code multiplierFor} reads
 * its wearers ({@code DataWearers}); the texture build reads each block's look ({@code BlockLooks}). It is shown by
 * {@code /trmt data}.
 *
 * <p>
 * <strong>Where its lines go.</strong> Every problem is said once a session at WARN on the mod's logger, never through
 * {@code Trmt.error}, whose first line sends the player to TRMTR's tracker: a bad file is its mod's, its pack's or the
 * player's to mend ({@code DataErrorsBlameTheirSourceTest}). The summary is said at INFO; at pre-init it waits for
 * post-init, because which names are not installed can only be asked once every mod has registered its blocks.
 */
public final class DataLoad {

    private DataLoad() {}

    /** The mod's logger, at the level each line asks for. */
    static final DataProblems.Sink LOG = new DataProblems.Sink() {

        @Override
        public void warn(String line) {
            Trmt.LOG.warn(line);
        }

        @Override
        public void info(String line) {
            Trmt.LOG.info(line);
        }

        @Override
        public void debug(String line) {
            Trmt.LOG.debug(line);
        }
    };

    /** The session's said-once log, kept across reads so a broken file is said once however often it is read. */
    private static final DataProblems PROBLEMS = new DataProblems(LOG);

    private static volatile DataFiles.Result current = DataFiles.Result.NONE;

    /** The mod files' and the config folder's files, from the last full read. */
    private static List<DataFiles.Found> held = Collections.emptyList();

    /** The client's resource packs' files, from the last resource reload; none on a server. */
    private static List<DataFiles.Found> packs = Collections.emptyList();

    /** The plugins' files from code, given once at pre-init and kept for the session. */
    private static List<DataFiles.Found> fromPlugins = Collections.emptyList();

    private static boolean readOnce;

    /** Whether every mod has registered its blocks, so the names the files give can be looked up. */
    private static boolean registered;

    /** The session's problems log, for the client's pack reader. */
    public static DataProblems problems() {
        return PROBLEMS;
    }

    /** The last read: its table and the sources it read. Any thread. */
    public static DataFiles.Result current() {
        return current;
    }

    /** The merged table of the last read. Any thread. */
    public static DataTable table() {
        return current.table;
    }

    /**
     * Reads every mod file and the config folder again, with the packs last seen, and publishes the new table. Called
     * at
     * pre-init and by {@code /trmt reload}. Never throws.
     */
    public static synchronized void read() {
        try {
            List<DataFiles.Found> files = new ArrayList<DataFiles.Found>();
            for (DataFiles.ModFile mod : modFiles()) files.addAll(DataFiles.fromModFile(mod, PROBLEMS));
            files.addAll(DataFiles.fromConfig(TrmtConfig.folder(), PROBLEMS));
            held = files;
            publish();
        } catch (RuntimeException | LinkageError unexpected) {
            Trmt.LOG.warn(
                DataProblems.PREFIX + "the data files could not be read this time; the last read stays in use",
                unexpected);
        }
    }

    /**
     * The client's resource packs' files, as a resource reload found them. Re-merged only when they differ from the
     * last, so a reload that changed no pack's data says nothing. Never throws.
     */
    public static synchronized void packsRead(List<DataFiles.Found> fromPacks) {
        try {
            if (same(packs, fromPacks)) return;
            packs = new ArrayList<DataFiles.Found>(fromPacks);
            if (readOnce) publish();
        } catch (RuntimeException | LinkageError unexpected) {
            Trmt.LOG.warn(
                DataProblems.PREFIX
                    + "the resource packs' data files could not be read this time; the last read stays in use",
                unexpected);
        }
    }

    /** At post-init: every block is registered now, so the summary of the pre-init read can be said. */
    public static synchronized void registered() {
        registered = true;
        if (readOnce) saySummary();
    }

    private static void publish() {
        List<DataFiles.Found> all = new ArrayList<DataFiles.Found>(held);
        all.addAll(fromPlugins);
        all.addAll(packs);
        current = DataFiles.read(all, PROBLEMS);
        readOnce = true;
        if (registered) saySummary();
    }

    private static void saySummary() {
        LOG.info(summary());
    }

    /** The last read's summary line, with the names not installed counted against this game. */
    public static String summary() {
        DataFiles.Result read = current;
        return read.table.summary(notInstalled(read.table).size());
    }

    /** The {@code /trmt data} answer: the overview, or with a name, where each of that block's values came from. */
    public static List<DataReport.Line> report(String name) {
        DataFiles.Result read = current;
        if (name != null && !name.trim()
            .isEmpty()) return DataReport.block(read, name);
        return DataReport.overview(read, notInstalled(read.table));
    }

    /** Every block and entity id the last read names, for the command's completion. */
    public static List<String> names() {
        TreeSet<String> out = new TreeSet<String>();
        DataTable table = current.table;
        for (DataKind kind : DataKind.values()) {
            if (kind != DataKind.PLUGIN) out.addAll(table.namedIds(kind));
        }
        return new ArrayList<String>(out);
    }

    /**
     * The block and entity names the files give that this game does not have, sorted: a block by
     * {@code Block.getBlockFromName}, as {@code SurfaceRegistry.applyList} asks it, an entity by the game's entity
     * list. Not an error (FORMAT.md: a pack written for several editions names each one's spelling), only counted.
     */
    static List<String> notInstalled(DataTable table) {
        TreeSet<String> out = new TreeSet<String>();
        try {
            for (DataKind kind : new DataKind[] { DataKind.FAMILIES, DataKind.LOOKS, DataKind.SHAPES }) {
                for (String id : table.namedIds(kind)) if (Block.getBlockFromName(id) == null) out.add(id);
            }
            for (String id : table.namedIds(DataKind.WEARERS)) {
                if (!EntityList.stringToClassMapping.containsKey(id)) out.add(id);
            }
        } catch (RuntimeException | LinkageError unasked) {
            Trmt.LOG.debug("TRMTR data: the names not installed could not be counted", unasked);
        }
        return new ArrayList<String>(out);
    }

    /**
     * The data files the plugins gave from code ({@code Registrar.data}, W3 T7), handed over once their plugins are
     * loaded: each counts as its own mod's jar in the load order, is kept for every later read, and the table is
     * published again with them. Never throws.
     */
    public static synchronized void pluginData(List<PluginHost.Data> given) {
        try {
            List<DataFiles.Found> files = new ArrayList<DataFiles.Found>();
            for (PluginHost.Data each : given) files.add(DataFiles.fromPlugin(each.modId, each.path, each.text));
            fromPlugins = files;
            if (readOnce) publish();
        } catch (RuntimeException | LinkageError unexpected) {
            Trmt.LOG.warn(
                DataProblems.PREFIX + "the plugins' data files could not be read; the last read stays in use",
                unexpected);
        }
    }

    /**
     * Every file FML loaded a mod from, once each, with the mods it holds. A container with no file (the game itself
     * in some launchers) or a file that is gone is passed over. Also read by trmtr-api's shading check.
     */
    public static List<DataFiles.ModFile> modFiles() {
        Map<File, List<String>> byFile = new LinkedHashMap<File, List<String>>();
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            File source = mod.getSource();
            if (source == null || !source.exists()) continue;
            File key = source.getAbsoluteFile();
            List<String> ids = byFile.get(key);
            if (ids == null) {
                ids = new ArrayList<String>();
                byFile.put(key, ids);
            }
            ids.add(mod.getModId());
        }
        List<DataFiles.ModFile> out = new ArrayList<DataFiles.ModFile>();
        for (Map.Entry<File, List<String>> each : byFile.entrySet()) {
            out.add(new DataFiles.ModFile(each.getKey(), each.getValue()));
        }
        return out;
    }

    private static boolean same(List<DataFiles.Found> before, List<DataFiles.Found> now) {
        if (before.size() != now.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i)
                .same(now.get(i))) return false;
        }
        return true;
    }
}
