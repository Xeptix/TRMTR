package com.trmtgtnh.dataload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.apihost.PluginHost;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.data.DataKind;
import com.trmtgtnh.data.DataProblems;
import com.trmtgtnh.data.DataTable;

/**
 * The 26.1.2 edition's data read (W3 spec 2.2 and 2.3, task T4, carried in T19): when the data files are read, the
 * table they make, and the one summary line each read ends with.
 *
 * <p>
 * <strong>When.</strong> Once every mod is constructed and has registered its blocks (each loader's common setup, or
 * Fabric's main entry point, which runs after every mod's registration), and again on {@code /trmt reload}
 * ({@code ConfigReload.fromDisk}); the server's data packs on every data-pack reload, {@code /reload} included, through
 * each loader's server-data reload listener ({@link #dataPacksRead}), before the surface table is resolved again; and
 * on a client, the resource packs on every resource reload ({@code client.PackData}, {@link #packsRead}). Each read
 * builds a new table and publishes it whole, as {@code resolve} publishes its own, so a reader on another thread sees
 * one read or the next and never half of one.
 *
 * <p>
 * <strong>What is held.</strong> The mod files' and the config folder's files from the last full read, the server's
 * data packs' from its last data reload and the client's resource packs' from its last resource reload, so a pack
 * change re-merges without opening every jar again. On an integrated server both pack layers live in the one table:
 * the data packs give families, shapes and wearers and the resource packs looks, which the data package keeps apart.
 *
 * <p>
 * <strong>Where its lines go.</strong> Every problem is said once a session at WARN on the mod's logger, never through
 * {@code Trmt.error}, whose first line sends the player to TRMTR's tracker: a bad file is its mod's, its pack's or the
 * player's to mend ({@code DataErrorsBlameTheirSourceTest}). The summary is said at INFO once the blocks are
 * registered, which they are before the first read here.
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

    /** The client's resource packs' files, from the last resource reload; none on a dedicated server. */
    private static List<DataFiles.Found> packs = Collections.emptyList();

    /** The server's data packs' files, from its last data reload; none on a client of another's server. */
    private static List<DataFiles.Found> dataPacks = Collections.emptyList();

    /** The plugins' files from code, given once and kept for the session. */
    private static List<DataFiles.Found> fromPlugins = Collections.emptyList();

    private static boolean readOnce;

    /** Whether every mod has registered its blocks, so the names the files give can be looked up. */
    private static boolean registered;

    /** Each loader's list of the mods it loaded, with the files and folders it loaded them from. */
    private static volatile Supplier<List<DataFiles.ModFile>> mods = Collections::emptyList;

    /** Tells the read where the mods are. Called once by each loader module as the mod starts. */
    public static void useMods(Supplier<List<DataFiles.ModFile>> loaders) {
        if (loaders != null) mods = loaders;
    }

    /** The session's problems log, for the pack readers. */
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
     * once the mods are registered and by {@code /trmt reload}. Never throws.
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
     * The first read, taken now where nothing has read yet, and nothing where something has. Asked as a block-atlas
     * stitch begins ({@code client.PackData.beforeStitch}): on Forge and NeoForge the game's first resource reload
     * plans that stitch before common setup reads the files, so the plan's sets were keyed without the looks files and
     * a block whose model names the same faces as another's shared its pictures - on 1.21.1 TerraFirmaCraft's grass wore
     * the leaf litter of the duff of its soil (0.9.229 W6 R9). The read at common setup still runs, and reads the same
     * files again. On Fabric the entrypoint has read them already, and this does nothing. Never throws.
     */
    public static synchronized void readIfUnread() {
        if (!readOnce) read();
    }

    /**
     * The client's resource packs' files, as a resource reload found them. Re-merged only when they differ from the
     * last, so a reload that changed no pack's data says nothing. Never throws.
     */
    public static synchronized void packsRead(List<DataFiles.Found> fromResourcePacks) {
        try {
            if (same(packs, fromResourcePacks)) return;
            packs = new ArrayList<DataFiles.Found>(fromResourcePacks);
            if (readOnce) publish();
        } catch (RuntimeException | LinkageError unexpected) {
            Trmt.LOG.warn(
                DataProblems.PREFIX
                    + "the resource packs' data files could not be read this time; the last read stays in use",
                unexpected);
        }
    }

    /**
     * The server's data packs' files, as a data reload found them ({@code /reload} included). Always published again,
     * even when the files are the same: a data reload is also what changes the block and entity tags a file's
     * {@code #ns:tag} names, and a new table is what tells every reader keyed on the table to ask again. Never throws.
     */
    public static synchronized void dataPacksRead(List<DataFiles.Found> fromDataPacks) {
        try {
            dataPacks = new ArrayList<DataFiles.Found>(fromDataPacks);
            if (readOnce) publish();
        } catch (RuntimeException | LinkageError unexpected) {
            Trmt.LOG.warn(
                DataProblems.PREFIX + "the data packs' data files could not be read this time; the last read stays in use",
                unexpected);
        }
    }

    /** Every block is registered now, so the summary of a read can be said. */
    public static synchronized void registered() {
        if (registered) return;
        registered = true;
        if (readOnce) saySummary();
    }

    private static void publish() {
        List<DataFiles.Found> all = new ArrayList<DataFiles.Found>(held);
        all.addAll(fromPlugins);
        all.addAll(packs);
        all.addAll(dataPacks);
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
            .isEmpty()) {
            List<DataReport.Line> lines = DataReport.block(read, name);
            net.minecraft.resources.Identifier at = net.minecraft.resources.Identifier
                .tryParse(DataReport.idOf(name.trim()));
            net.minecraft.world.level.block.Block block = at == null ? null
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(at)
                    .orElse(null);
            int apart = block == null ? 0 : com.trmtgtnh.surface.SurfaceRegistry.statesApart(block);
            if (apart > 0) lines.add(
                DataReport.statesApart(
                    apart,
                    block.getStateDefinition()
                        .getPossibleStates()
                        .size()));
            return lines;
        }
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
     * The block and entity names the files give that this game does not have, sorted: a block by the game's block
     * registry, an entity by its entity type registry, both spelt {@code ns:name}. Tags are not counted: an empty tag
     * names nothing missing. Not an error (FORMAT.md: a pack written for several editions names each one's spelling),
     * only counted.
     */
    static List<String> notInstalled(DataTable table) {
        TreeSet<String> out = new TreeSet<String>();
        try {
            for (DataKind kind : new DataKind[] { DataKind.FAMILIES, DataKind.LOOKS, DataKind.SHAPES }) {
                for (String id : table.namedIds(kind)) {
                    Identifier at = Identifier.tryParse(id);
                    if (at == null || !BuiltInRegistries.BLOCK.getOptional(at)
                        .isPresent()) out.add(id);
                }
            }
            for (String id : table.namedIds(DataKind.WEARERS)) {
                Identifier at = id.indexOf(':') > 0 ? Identifier.tryParse(id) : null;
                if (at == null || !BuiltInRegistries.ENTITY_TYPE.getOptional(at)
                    .isPresent()) out.add(id);
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
     * Every file the loader loaded a mod from, once each, with the mods it holds, as the loader module that started
     * the mod says. Also read by trmtr-api's shading check. Never throws; none where the loader could not say.
     */
    public static List<DataFiles.ModFile> modFiles() {
        try {
            List<DataFiles.ModFile> listed = mods.get();
            return listed == null ? Collections.<DataFiles.ModFile>emptyList() : listed;
        } catch (RuntimeException | LinkageError unlisted) {
            Trmt.LOG.warn(
                DataProblems.PREFIX + "the loader's mod list could not be read, so no mod's data files were read",
                unlisted);
            return Collections.emptyList();
        }
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
