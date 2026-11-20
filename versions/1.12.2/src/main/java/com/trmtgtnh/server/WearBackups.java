package com.trmtgtnh.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.RegionFileCache;

import com.trmtgtnh.Tags;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.erosion.WearArchive;
import com.trmtgtnh.erosion.WearBackupSet;
import com.trmtgtnh.erosion.WearLoss;
import com.trmtgtnh.erosion.WearMirror;
import com.trmtgtnh.erosion.WearRestore;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.util.MainThread;

/**
 * The world's wear backups, in 1.12.2 (0.9.229; docs/goals/0.9.229-wear-backup-spec.md): the mirror kept as chunks
 * save, the backups taken on their schedule and before an update or a bulk clear, lost wear noticed as chunks load and
 * as the world opens, and the restore and its undo. What is the same in every edition - the files, which backups go,
 * the merge, the verdict on a chunk - is in the portable {@link WearMirror}, {@link WearArchive},
 * {@link WearBackupSet}, {@link WearRestore} and {@link WearLoss}; this is the part that knows the game.
 *
 * <p>
 * <b>Nothing here blocks a tick on a disk.</b> Every write and every backup runs on one background thread of this
 * mod's own, in the order asked, so a backup always sees the mirror as the flush before it left it. A save hands the
 * mirror its chunk's blob in memory; the main thread takes only a copy of this mod's own world records (a few hundred
 * bytes) when a backup starts. The world's opening reads the backups' small manifests and the state file, before any
 * tick runs. A restore never loads a chunk for itself: chunks that are loaded are filled at once, the rest as they
 * load.
 */
public final class WearBackups {

    private static final WearBackups INSTANCE = new WearBackups();

    public static WearBackups get() {
        return INSTANCE;
    }

    private WearBackups() {}

    /** The state file and the restore's record, in the mod's own folder in the world. */
    static final String STATE = "state.properties";

    static final String RUN = "restore.dat";

    /** This mod's own world data: every file in {@code data/} its records are saved under. */
    static final String DATA_PREFIX = "trmtgtnh";

    /** How often the mirror is written, when anything is waiting, in ticks: every thirty seconds. */
    static final int FLUSH_TICKS = 600;

    /** How long after the first quiet restore its one line is written, so it counts the spawn area's chunks. */
    static final int QUIET_LINE_TICKS = 600;

    /**
     * How long after the first loss is noticed the single-player screen is offered, so it counts what loads with it.
     */
    static final int ASK_TICKS = 100;

    // ------------------------------------------------------------------
    // The world open now
    // ------------------------------------------------------------------

    private File worldDir;

    private File home;

    private WearMirror.Store mirror;

    private ExecutorService io;

    private WearBackupSet.State state = new WearBackupSet.State();

    private volatile List<WearBackupSet.Backup> listing = Collections.emptyList();

    /** Names given to backups that are queued and not yet on disk, so two never share one. */
    private final Set<String> queued = new HashSet<String>();

    private boolean dedicated;

    /** The mirror is missing as a whole while a backup holds wear: the newest backup stands in for it. */
    private boolean mirrorGone;

    /** The world was last opened by a newer version of this mod. */
    private boolean newerWorld;

    /**
     * The world's wear or its world data went as a whole (the mirror missing while a backup holds wear, or this mod's
     * world data missing at load): nothing goes back quietly then, and the player is asked or the operators told.
     */
    private boolean wholeLoss;

    /** The world data put back at load, from this backup, or null: said in the question and the operators' notice. */
    private String dataFrom;

    /** Past the quiet threshold this session: what the mirror holds from the same save is a bigger loss too. */
    private boolean big;

    /** The quiet threshold in force: Xep's sixteen, or a test run's lower one. */
    private int quietMost = com.trmtgtnh.erosion.WearLoss.QUIET_MOST;

    /** Whether this session's look through the region files has been started. */
    private boolean scanStarted;

    /** The mirror being built from the region files (a world from before wear backups); cleared to stop it. */
    private volatile boolean seeding;

    private WearArchive.Opened newest;

    private WearArchive.Opened runSource;

    private final Set<WearMirror.Spot> lost = new LinkedHashSet<WearMirror.Spot>();

    /** Chunks read this session, so the opening's scan leaves alone what their own load has answered. */
    private final Set<WearMirror.Spot> seen = new HashSet<WearMirror.Spot>();

    /** Chunks whose wear read back damaged this session, which a restore of one backup may fill. */
    private final Set<WearMirror.Spot> damaged = new HashSet<WearMirror.Spot>();

    private WearRestore.Run run;

    /** A backup's restore asked for and still being read; and an Undo restore asked for meanwhile, with who asked. */
    private boolean restoring;

    private boolean undoAfter;

    private ICommandSender undoAfterWho;

    private int quiet;

    private long quietLineAt = -1L;

    private long askAt = -1L;

    private boolean asked;

    /** The backup the last restore took first, for its words. */
    private String lastUndo;

    /** How many chunks a server has put back by itself this session, and when its operators are next told. */
    private int autoRestored;

    /** The chunks a server has put back by itself this session, each once, for its operators' notice. */
    private final Set<WearMirror.Spot> autoSpots = new HashSet<WearMirror.Spot>();

    private long noticeAt = -1L;

    // The schedule, in ticks of this session's play.
    private long ticks;

    private long sinceLast;

    private boolean startChecked;

    private long interval;

    /** Whether the backups run in this world: the setting, as the world opened. */
    public boolean running() {
        return home != null;
    }

    // ------------------------------------------------------------------
    // Opening and closing
    // ------------------------------------------------------------------

    /**
     * The overworld has loaded, before any of its chunks have and before anything has read this mod's world records:
     * the state is read, world records that are missing are put back from the newest backup, the pre-update backup is
     * queued first of all when this mod's version changed, and a scan of the world is started where there is reason to
     * think wear has gone.
     */
    public synchronized void worldLoading(World overworld) {
        stop();
        if (!TrmtConfig.wearBackups || overworld == null || overworld.isRemote) return;
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        worldDir = overworld.getSaveHandler()
            .getWorldDirectory();
        home = new File(worldDir, WearMirror.HOME);
        mirror = new WearMirror.Store(new File(home, WearMirror.FOLDER));
        io = Executors.newSingleThreadExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "TRMT: Reimagined wear backups");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        });
        dedicated = server != null && server.isDedicatedServer();
        interval = WearBackupSet.intervalSeconds(TrmtConfig.wearBackupHours, System.getProperties(), development());
        quietMost = WearBackupSet.quietMost(System.getProperties(), development());
        if (quietMost != WearLoss.QUIET_MOST) {
            Trmt.LOG.info(
                "Wear backups: a test threshold of {} chunks put back quietly is in force.",
                Integer.valueOf(quietMost));
        }
        if (interval < 3600L) {
            Trmt.LOG.info("Wear backups: a test interval of {} seconds is in force.", Long.valueOf(interval));
        }

        File stateFile = new File(home, STATE);
        boolean hadState = stateFile.isFile();
        state = WearBackupSet.State.from(hadState ? text(stateFile) : null);
        listing = list();
        boolean hadWorldData = new File(dataDir(), HealClockData.NAME + ".dat").isFile();
        boolean dataPutBack = restoreMissingWorldData();

        int change = hadState ? WearBackupSet.versionChange(state.version, Tags.VERSION) : hadWorldData ? 1 : 0;
        boolean preFeature = !hadState && hadWorldData;
        if (change != 0) {
            // First of everything this world's opening queues, so it is the mirror and the records as the version
            // before this one left them.
            String name = backup(WearBackupSet.UPDATE, diskWorldData());
            if (preFeature) {
                Trmt.LOG.info(
                    "Wear backups: this world was last opened by a version of this mod from before wear backups; {} holds its world data alone while the mirror is built from the region files, and a second pre-update backup with the wear follows.",
                    name);
            } else {
                Trmt.LOG.info(
                    "Wear backups: this world was last opened by TRMT: Reimagined {}{}; {} is being taken before anything touches its wear.",
                    state.version,
                    change < 0 ? ", which is newer than this one (" + Tags.VERSION + ")" : "",
                    name);
            }
        }
        // A world from before wear backups has no mirror: it is built from the region files in the background, and
        // the pre-update backup taken again from it (Xep, after phase 1). Picked up again at the next load if the
        // server stops first.
        if (preFeature) state.seedPending = true;
        if (state.seedPending) seedMirror();
        newerWorld = change < 0;
        boolean playedWithout = hadState && state.saved >= 0L && overworld.getTotalWorldTime() > state.saved;
        if (playedWithout) {
            Trmt.LOG.info(
                "Wear backups: the world's clock stands at {} and this mod last saved it at {}, so it has been played without this mod since; looking for wear that was lost.",
                Long.valueOf(overworld.getTotalWorldTime()),
                Long.valueOf(state.saved));
        }
        state.version = Tags.VERSION;
        writeState();

        WearBackupSet.Backup newestBackup = WearBackupSet.newest(listing);
        mirrorGone = WearMirror.files(mirror.root())
            .isEmpty() && newestBackup != null
            && newestBackup.chunks > 0;
        if (mirrorGone) {
            Trmt.LOG.info(
                "Wear backups: the mirror is missing and {} holds the wear of {} chunks; it stands in for the mirror until chunks save again.",
                newestBackup.name,
                Integer.valueOf(newestBackup.chunks));
        }

        byte[] runBytes = bytes(new File(home, RUN));
        run = WearRestore.Run.read(runBytes);
        if (run != null) {
            for (WearMirror.Spot spot : run.waiting) mirror.hold(spot);
        }
        // The world's wear or its world data gone as a whole: nothing goes back quietly (Xep, after phase 1).
        wholeLoss = mirrorGone || dataPutBack;
        if (mirrorGone || playedWithout || newerWorld || dataPutBack) scan();
    }

    /** The server has stopped: the mirror and the state are written, and the background thread is let finish. */
    public void serverStopped() {
        ExecutorService closing;
        synchronized (this) {
            closing = io;
            if (closing == null) {
                stop();
                return;
            }
            // A mirror still being built stops where it is; the next load goes on with it.
            seeding = false;
            flushLater();
            writeState();
            if (run != null) writeRun();
            closing.shutdown();
        }
        // Waited for without the lock, which the background thread's last steps take. The game has stopped ticking, so
        // waiting here holds up nothing but the quit.
        try {
            if (!closing.awaitTermination(60, TimeUnit.SECONDS)) {
                Trmt.LOG.warn("Wear backups: the last writes were still going after a minute; left to finish.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
        }
        synchronized (this) {
            if (io == closing) stop();
        }
    }

    private void stop() {
        seeding = false;
        if (io != null && !io.isShutdown()) io.shutdown();
        io = null;
        close(newest);
        close(runSource);
        newest = null;
        runSource = null;
        worldDir = null;
        home = null;
        mirror = null;
        listing = Collections.emptyList();
        queued.clear();
        lost.clear();
        seen.clear();
        damaged.clear();
        run = null;
        restoring = false;
        undoAfter = false;
        undoAfterWho = null;
        quiet = 0;
        quietLineAt = -1L;
        askAt = -1L;
        asked = false;
        lastUndo = null;
        autoRestored = 0;
        autoSpots.clear();
        noticeAt = -1L;
        ticks = 0L;
        sinceLast = 0L;
        startChecked = false;
        mirrorGone = false;
        newerWorld = false;
        wholeLoss = false;
        dataFrom = null;
        big = false;
        scanStarted = false;
    }

    // ------------------------------------------------------------------
    // The game's chunks
    // ------------------------------------------------------------------

    /**
     * A chunk has been read from its save (on whatever thread the game reads chunks): a restore waiting for it is done,
     * and wear it lost is noticed - put back quietly when the mirror holds it from this same save, set aside for the
     * player or put back for a server's operators when it is a real loss.
     *
     * @param root the chunk's save, as read, whose {@code Level.LastUpdate} is the save's own stamp
     * @param blob the chunk's wear tag, or null when it has none
     * @param data what the blob read as, or null with none
     * @return the wear the chunk is to load with, which may be {@code data}, filled, or new
     */
    public synchronized ChunkErosionData chunkRead(World world, int chunkX, int chunkZ, NBTTagCompound root,
        byte[] blob, ChunkErosionData data) {
        if (home == null || world == null || world.isRemote || root == null) return data;
        WearMirror.Spot spot = spotOf(world, chunkX, chunkZ);
        seen.add(spot);
        boolean tagged = blob != null;
        boolean broken = tagged && WearLoss.damaged(blob, data == null ? 0 : data.size());
        if (broken) damaged.add(spot);
        if (run != null && run.waiting.contains(spot)) return applyRun(spot, data, !tagged || broken);
        if (tagged && !broken) return data;

        long savedAt = root.getCompoundTag("Level")
            .getLong("LastUpdate");
        WearMirror.Entry held = mirror.get(spot);
        boolean backupHolds = held == null && mirrorGone && newestEntry(spot) != null;
        WearLoss.Kind kind = WearLoss.of(tagged, broken, held, savedAt, backupHolds);
        if (kind == WearLoss.Kind.FINE) return data;
        if (kind == WearLoss.Kind.QUIET && (big || !WearLoss.staysQuiet(quiet, wholeLoss, quietMost))) {
            // Past the quiet threshold, or with the world's wear or data gone as a whole: a bigger loss, which the
            // player is asked about and a server's operators are told of, and the rest of it looked for now.
            if (!big) {
                big = true;
                Trmt.LOG.info(
                    "Wear backups: more than {} chunks have lost wear the mirror holds, so this is no longer put back quietly; looking for the rest.",
                    Integer.valueOf(quietMost));
                if (!scanStarted) scan();
            }
            kind = WearLoss.Kind.LOST;
        }
        if (kind == WearLoss.Kind.QUIET) {
            ChunkErosionData from = ChunkErosionData.read(held.blob());
            if (data == null || data.isEmpty()) {
                data = from;
            } else {
                WearRestore.fill(data, from);
            }
            data.markDirty();
            if (quiet++ == 0) quietLineAt = ticks + QUIET_LINE_TICKS;
            return data;
        }
        // A real loss: the copy is kept, whatever the chunk saves now, until it is put back or let go.
        mirror.hold(spot);
        lost.add(spot);
        if (dedicated) {
            // A server puts missing wear back by itself, here as the chunk is read: into the restore already putting
            // back what was lost, or a new one, which takes the backup its undo puts back first.
            boolean begun = !automatic();
            if (begun) begin(WearRestore.LATEST);
            run.waiting.add(spot);
            data = applyRun(spot, data, true);
            autoRestored++;
            autoSpots.add(spot);
            if (noticeAt < 0L) noticeAt = ticks + ASK_TICKS;
            return data;
        }
        if (askAt < 0L && !asked) askAt = ticks + ASK_TICKS;
        return data;
    }

    /**
     * A chunk is being saved, with the wear it wrote (null for none): the mirror's entry for it, unless it is held.
     * On the saving thread, and costs a map entry: the disk is the background thread's.
     */
    public void chunkSaved(World world, int chunkX, int chunkZ, NBTTagCompound root, byte[] blob) {
        WearMirror.Store store;
        synchronized (this) {
            store = mirror;
        }
        if (store == null || world == null || world.isRemote || root == null) return;
        store.put(
            spotOf(world, chunkX, chunkZ),
            root.getCompoundTag("Level")
                .getLong("LastUpdate"),
            blob);
    }

    /** The overworld has saved: its clock is this mod's last save, and the mirror goes to disk. */
    public synchronized void worldSaved(World world) {
        if (home == null || world == null || world.isRemote || world.provider.getDimension() != 0) return;
        state.saved = world.getTotalWorldTime();
        flushLater();
        writeState();
    }

    // ------------------------------------------------------------------
    // The schedule
    // ------------------------------------------------------------------

    /** Once a server tick, on its thread. */
    public synchronized void tick() {
        if (home == null) return;
        ticks++;
        state.played++;
        sinceLast++;
        if (!startChecked) {
            if (ticks >= WearBackupSet.warmUpTicks(interval)) {
                startChecked = true;
                if (WearBackupSet.dueAtStart(WearBackupSet.newest(listing), System.currentTimeMillis(), interval)) {
                    backup(WearBackupSet.SCHEDULED, liveWorldData());
                }
            }
        } else if (WearBackupSet.dueInPlay(sinceLast, interval)) {
            backup(WearBackupSet.SCHEDULED, liveWorldData());
        }
        if (ticks % FLUSH_TICKS == 0L && mirror.dirty()) flushLater();
        if (quietLineAt >= 0L && ticks >= quietLineAt) {
            quietLineAt = Long.MAX_VALUE;
            Trmt.LOG.info(WearLoss.quietLine(quiet));
        }
        if (askAt >= 0L && ticks >= askAt) {
            askAt = -1L;
            offer();
        }
        if (noticeAt >= 0L && ticks >= noticeAt) {
            noticeAt = -1L;
            noticeAutomatic();
        }
    }

    // ------------------------------------------------------------------
    // Backups
    // ------------------------------------------------------------------

    /**
     * Takes a backup now, on the background thread, after the mirror's waiting writes; returns its name at once. Called
     * on the server's thread, and before a bulk clear by the command that clears.
     */
    public synchronized String backupNow(String reason) {
        if (home == null) return null;
        return backup(reason, liveWorldData());
    }

    private String backup(final String reason, final Map<String, byte[]> data) {
        final long made = System.currentTimeMillis();
        Set<String> taken = new HashSet<String>(queued);
        for (WearBackupSet.Backup each : listing) taken.add(each.name);
        final String name = WearBackupSet.nameFor(made, reason, taken);
        queued.add(name);
        sinceLast = 0L;
        final long clock = overworldTicks();
        final long played = state.played;
        final WearMirror.Store store = mirror;
        final File folder = new File(home, WearBackupSet.FOLDER);
        // The mirror as it stands now, whatever saves before the background thread reaches it (a purge's, say).
        store.freeze();
        boolean queuedIt = submit(new Runnable() {

            @Override
            public void run() {
                try {
                    take();
                } finally {
                    store.thaw();
                }
            }

            private void take() {
                flushNow(store);
                try {
                    WearBackupSet.Backup made2 = WearArchive
                        .write(new File(folder, name), store.root(), data, made, reason, Tags.VERSION, clock, played);
                    Trmt.LOG.info(
                        "Wear backups: took {} ({}, the wear of {} chunks, {}).",
                        name,
                        reasonWords(reason),
                        Integer.valueOf(made2.chunks),
                        size(made2.bytes));
                } catch (IOException failed) {
                    Trmt.error("Wear backups: could not take " + name, failed);
                }
                prune(folder, played);
                List<WearBackupSet.Backup> now = listIn(folder);
                synchronized (WearBackups.this) {
                    queued.remove(name);
                    if (mirror == store) listing = now;
                }
            }
        });
        if (!queuedIt) {
            store.thaw();
            queued.remove(name);
        }
        return name;
    }

    /**
     * Deletes the backups past the count and the disk limit, never the newest, the newest pre-update one, or the one a
     * restore took first while its undo is still offered (see WearBackupSet.kept).
     */
    private void prune(File folder, long played) {
        List<WearBackupSet.Backup> all = listIn(folder);
        long limit = Math.max(1L, (long) TrmtConfig.wearBackupDiskMb) * 1024L * 1024L;
        for (WearBackupSet.Backup each : WearBackupSet.toPrune(all, TrmtConfig.wearBackupsKept, limit, played)) {
            if (new File(folder, each.name).delete()) {
                Trmt.LOG.info(
                    "Wear backups: removed {}, past the {} kept or the {} MB limit.",
                    each.name,
                    Integer.valueOf(TrmtConfig.wearBackupsKept),
                    Integer.valueOf(TrmtConfig.wearBackupDiskMb));
            }
        }
    }

    /** The backups on disk, newest first, as their manifests say; the state of this world's folder. */
    public List<WearBackupSet.Backup> backups() {
        return listing;
    }

    private List<WearBackupSet.Backup> list() {
        return listIn(new File(home, WearBackupSet.FOLDER));
    }

    private static List<WearBackupSet.Backup> listIn(File folder) {
        List<WearBackupSet.Backup> out = new ArrayList<WearBackupSet.Backup>();
        String[] names = folder.list();
        if (names == null) return out;
        for (String name : names) {
            if (!WearBackupSet.isBackupName(name)) continue;
            WearBackupSet.Backup read = WearArchive.describe(new File(folder, name));
            if (read != null) out.add(read);
        }
        return WearBackupSet.newestFirst(out);
    }

    // ------------------------------------------------------------------
    // Restoring
    // ------------------------------------------------------------------

    /**
     * Restores from a backup by its name, or {@link WearRestore#LATEST}: a backup is taken first for the undo, the
     * loaded chunks whose wear is missing or damaged are filled at once and the rest as they load. On the server's
     * thread.
     */
    public synchronized void restore(final String source, final ICommandSender who) {
        if (home == null) {
            say(who, TextFormatting.RED, "Wear backups are off in this world.");
            return;
        }
        if (WearRestore.LATEST.equals(source)) {
            restoreLost(who);
            return;
        }
        final WearBackupSet.Backup chosen = WearBackupSet.named(listing, source);
        if (chosen == null) {
            say(who, TextFormatting.RED, "There is no backup called " + source + ". /trmt wear list names them.");
            return;
        }
        final File file = new File(new File(home, WearBackupSet.FOLDER), chosen.name);
        restoring = true;
        submit(new Runnable() {

            @Override
            public void run() {
                final List<WearMirror.Spot> chunks;
                try {
                    WearArchive.Opened opened = WearArchive.open(file);
                    try {
                        chunks = opened.chunks();
                    } finally {
                        opened.close();
                    }
                } catch (IOException failed) {
                    Trmt.error("Wear backups: could not read " + chosen.name, failed);
                    MainThread.onServer(new Runnable() {

                        @Override
                        public void run() {
                            synchronized (WearBackups.this) {
                                restoring = false;
                                undoAfter = false;
                            }
                            say(who, TextFormatting.RED, "Could not read " + chosen.name + "; see the log.");
                        }
                    });
                    return;
                }
                MainThread.onServer(new Runnable() {

                    @Override
                    public void run() {
                        start(chosen.name, chunks, who);
                    }
                });
            }
        });
    }

    /** Puts back the wear of every chunk noticed lost, from the mirror where it holds it, then the newest backup. */
    private synchronized void restoreLost(ICommandSender who) {
        if (home == null) return;
        start(WearRestore.LATEST, new ArrayList<WearMirror.Spot>(lost), who);
    }

    private synchronized void start(String source, List<WearMirror.Spot> chunks, ICommandSender who) {
        if (home == null) return;
        restoring = false;
        // A server's own restore of what was lost goes on in the one already doing it, so its undo is one undo.
        boolean joining = who == null && dedicated && WearRestore.LATEST.equals(source) && automatic();
        if (!joining) begin(source);
        for (WearMirror.Spot spot : chunks) {
            run.waiting.add(spot);
            mirror.hold(spot);
        }
        int now = fillLoaded();
        if (run != null) writeRun();
        int waiting = run == null ? 0 : run.waiting.size();
        String from = WearRestore.LATEST.equals(source) ? "the mirror and the newest backup" : source;
        String line = "Restoring wear from " + from
            + ": "
            + now
            + (now == 1 ? " loaded chunk filled now, " : " loaded chunks filled now, ")
            + waiting
            + (waiting == 1 ? " chunk waits until it loads. " : " chunks wait until they load. ")
            + lastUndo
            + " was taken first; /trmt wear undo puts it back.";
        say(who, TextFormatting.GREEN, line);
        Trmt.LOG.info("Wear backups: " + line);
        if (dedicated && WearRestore.LATEST.equals(source) && who == null) {
            autoRestored += chunks.size();
            autoSpots.addAll(chunks);
            if (noticeAt < 0L) noticeAt = ticks + ASK_TICKS;
        }
        lost.clear();
        // An Undo restore asked for while this restore was being read, run now it has started (0.9.229).
        if (undoAfter) {
            undoAfter = false;
            ICommandSender asked = undoAfterWho;
            undoAfterWho = null;
            undo(asked);
        }
    }

    /** A new restore, after the backup its undo puts back. */
    private void begin(String source) {
        lastUndo = backup(WearBackupSet.RESTORE, Trmt.onServerThread() ? liveWorldData() : diskWorldData());
        close(runSource);
        runSource = null;
        run = new WearRestore.Run(source, lastUndo, System.currentTimeMillis());
    }

    /** Whether the restore under way is a server's own of what was lost, which more lost wear joins. */
    private boolean automatic() {
        return run != null && !run.undoing && WearRestore.LATEST.equals(run.source) && autoRestored > 0;
    }

    /** The operators' notice of what a server put back by itself, said to those online and kept for the rest. */
    private synchronized void noticeAutomatic() {
        if (home == null || autoSpots.isEmpty() && dataFrom == null) return;
        String data = dataFrom == null ? ""
            : "This world's own records (its heal clock and spawn grants) were missing and were put back from "
                + dataFrom
                + ".";
        state.notice = autoSpots.isEmpty() ? data
            : "Wear was missing from " + autoSpots.size()
                + (autoSpots.size() == 1 ? " chunk" : " chunks")
                + " of this world, and was put back from the mirror and the newest backup ("
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(run == null ? System.currentTimeMillis() : run.made))
                + ")."
                + (data.isEmpty() ? "" : " " + data);
        // The server's own log says it too: the chunks a load put back by itself say nothing as they load. The undo is
        // named only where a restore took a backup first; world data alone put back has none (0.9.229, 1.16.5's server
        // phase: "null was taken first").
        Trmt.LOG.info(
            "Wear backups: " + state.notice
                + (lastUndo == null ? "" : " " + lastUndo + " was taken first; /trmt wear undo puts it back."));
        state.told.clear();
        writeState();
        tellOperators();
    }

    /**
     * Waits, up to a cap, for everything queued for the background thread so far to be written: for the test harness,
     * which copies a world straight after saving it. Never called by the game itself, which never waits on a disk.
     */
    public void awaitWrites(long millis) {
        java.util.concurrent.Future<?> done;
        synchronized (this) {
            if (io == null || io.isShutdown()) return;
            done = io.submit(new Runnable() {

                @Override
                public void run() {}
            });
        }
        try {
            done.get(millis, TimeUnit.MILLISECONDS);
        } catch (Exception stillGoing) {
            Trmt.LOG.warn("Wear backups: the writes were still going after {} ms", Long.valueOf(millis));
        }
    }

    /**
     * Builds the mirror from the world's region files, on the background thread (a world from before wear backups; Xep,
     * after phase 1): of each chunk only this mod's tag and the save's stamp are read (WearSeed), and an entry goes in
     * only where the mirror holds nothing, so a save made meanwhile stands. Then the pre-update backup is taken again,
     * with the wear. Stopped where it is when the server stops, and taken up again at the next load.
     */
    private void seedMirror() {
        final WearMirror.Store store = mirror;
        final File world = worldDir;
        seeding = true;
        Trmt.LOG.info("Wear backups: building the mirror from this world's region files, in the background.");
        submit(new Runnable() {

            @Override
            public void run() {
                int chunks = 0;
                int files = 0;
                for (String dimension : dimensionFolders(world)) {
                    File folder = new File(dimension.isEmpty() ? world : new File(world, dimension), "region");
                    String[] names = folder.list();
                    if (names == null) continue;
                    java.util.Arrays.sort(names);
                    for (String name : names) {
                        if (!seeding) {
                            Trmt.LOG.info(
                                "Wear backups: the server stopped while the mirror was being built ({} chunks so far); the next load goes on with it.",
                                Integer.valueOf(chunks));
                            return;
                        }
                        java.util.regex.Matcher at = REGION_FILE.matcher(name);
                        if (!at.matches()) continue;
                        int regionX = Integer.parseInt(at.group(1));
                        int regionZ = Integer.parseInt(at.group(2));
                        byte[] bytes = bytes(new File(folder, name));
                        files++;
                        for (com.trmtgtnh.erosion.WearSeed.Found found : com.trmtgtnh.erosion.WearSeed
                            .read(bytes, ErosionStore.NBT_KEY)) {
                            WearMirror.Spot spot = new WearMirror.Spot(
                                dimension,
                                regionX * WearMirror.SIDE + (found.index & (WearMirror.SIDE - 1)),
                                regionZ * WearMirror.SIDE + (found.index >> 5));
                            if (store.seed(spot, found.savedAt, found.blob)) chunks++;
                        }
                    }
                }
                flushNow(store);
                synchronized (WearBackups.this) {
                    if (mirror != store) return;
                    seeding = false;
                    state.seedPending = false;
                    writeState();
                    Trmt.LOG.info(
                        "Wear backups: built the mirror from {} region files: the wear of {} chunks. Taking the pre-update backup again, with it.",
                        Integer.valueOf(files),
                        Integer.valueOf(chunks));
                    backup(WearBackupSet.UPDATE, diskWorldData());
                }
            }
        });
    }

    /** {@code r.<x>.<z>.mca}, a region file of the game's. */
    private static final java.util.regex.Pattern REGION_FILE = java.util.regex.Pattern
        .compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    /** The world's dimension folders as the game saves them: the overworld's own, and each DIM folder beside it. */
    private static List<String> dimensionFolders(File world) {
        List<String> out = new ArrayList<String>();
        out.add("");
        String[] names = world.list();
        if (names == null) return out;
        java.util.Arrays.sort(names);
        for (String name : names) {
            if (name.startsWith("DIM") && new File(new File(world, name), "region").isDirectory()) out.add(name);
        }
        return out;
    }

    /** Whether there is a restore to undo, now or as soon as the one being read has started: the screen's button. */
    public synchronized boolean canUndo() {
        return home != null && WearRestore.undoAsked(restoring, restoreToUndo()) != WearRestore.UndoAsked.NOTHING;
    }

    private boolean restoreToUndo() {
        return run != null && !run.undoing && !(run.filled.isEmpty() && run.waiting.isEmpty());
    }

    /** Undoes the last restore: what it put in is taken out, at once where loaded, the rest as they load. */
    public synchronized void undo(ICommandSender who) {
        WearRestore.UndoAsked asked = home == null ? WearRestore.UndoAsked.NOTHING
            : WearRestore.undoAsked(restoring, restoreToUndo());
        if (asked == WearRestore.UndoAsked.AFTER_RESTORE) {
            // The restore is still being read: undone as soon as it has started, never dropped (0.9.229).
            undoAfter = true;
            undoAfterWho = who;
            say(who, TextFormatting.YELLOW, "The restore is still being read; it is undone as soon as it starts.");
            return;
        }
        if (asked == WearRestore.UndoAsked.NOTHING) {
            say(who, TextFormatting.YELLOW, "There is no restore to undo.");
            return;
        }
        for (WearMirror.Spot spot : run.waiting) mirror.release(spot);
        run.undo();
        String undoBackup = run.undoBackup;
        int now = fillLoaded();
        writeRun();
        // fillLoaded lets a finished run go, so what the line says of it was read before (0.9.229: the line read the
        // run after, and an undo of loaded chunks alone threw once it had done its work).
        int left = run == null ? 0 : run.waiting.size();
        String line = "Restore undone: " + now
            + (now == 1 ? " loaded chunk put back" : " loaded chunks put back")
            + " as "
            + undoBackup
            + " has it, "
            + left
            + (left == 1 ? " chunk waits" : " chunks wait")
            + " until it loads.";
        say(who, TextFormatting.GREEN, line);
        Trmt.LOG.info("Wear backups: " + line);
        autoRestored = 0;
        autoSpots.clear();
        if (!state.notice.isEmpty()) {
            state.notice = "";
            state.told.clear();
            writeState();
        }
    }

    /** Every chunk of the run that is loaded now, done now. @return how many */
    private int fillLoaded() {
        int done = 0;
        for (WearMirror.Spot spot : new ArrayList<WearMirror.Spot>(run.waiting)) {
            World world = worldOf(spot.dimension);
            if (!(world instanceof WorldServer)) continue;
            if (((WorldServer) world).getChunkProvider().getLoadedChunk(spot.chunkX, spot.chunkZ) == null) continue;
            int dimension = world.provider.getDimension();
            ChunkErosionData live = ErosionStore.get()
                .getChunk(dimension, spot.chunkX, spot.chunkZ);
            boolean missing = live == null || live.isEmpty() || damaged.contains(spot) || lost.contains(spot);
            if (run.undoing) {
                WearRestore.unfill(live, run.filled.get(spot));
                run.undoneIn(spot);
            } else {
                if (!missing) {
                    run.filledInto(spot, null);
                    mirror.release(spot);
                    continue;
                }
                ChunkErosionData from = sourceFor(run.source, spot);
                if (from == null || from.isEmpty()) {
                    run.filledInto(spot, null);
                    mirror.release(spot);
                    continue;
                }
                if (live == null) {
                    live = ErosionStore.get()
                        .getOrCreateChunk(dimension, spot.chunkX, spot.chunkZ);
                }
                if (live.isEmpty()) live.setPlacedOn(from.placedOn());
                run.filledInto(spot, WearRestore.fill(live, from));
                mirror.release(spot);
                // The healing owed for the time since the backup, by each record's own clock.
                ErosionEngine.get()
                    .healChunk(world, spot.chunkX, spot.chunkZ, live, false);
            }
            ErosionStore.get()
                .markModified(world, spot.chunkX, spot.chunkZ);
            if (live != null) TrmtNetwork.sendChunkToWatchers(world, spot.chunkX, spot.chunkZ, live, true);
            done++;
        }
        if (run.spent()) {
            run = null;
            new File(home, RUN).delete();
        }
        return done;
    }

    /** A chunk the run is waiting for, as it is read. */
    private ChunkErosionData applyRun(WearMirror.Spot spot, ChunkErosionData data, boolean missing) {
        if (run.undoing) {
            WearRestore.unfill(data, run.filled.get(spot));
            run.undoneIn(spot);
        } else {
            ChunkErosionData from = missing ? sourceFor(run.source, spot) : null;
            if (from != null && !from.isEmpty()) {
                if (data == null) {
                    data = new ChunkErosionData();
                    data.setPlacedOn(from.placedOn());
                }
                run.filledInto(spot, WearRestore.fill(data, from));
                data.markDirty();
            } else {
                run.filledInto(spot, null);
            }
            mirror.release(spot);
            lost.remove(spot);
        }
        if (run.spent()) {
            run = null;
            new File(home, RUN).delete();
        } else {
            writeRun();
        }
        return data;
    }

    /** What a restore from this source puts into this chunk, or null. */
    private ChunkErosionData sourceFor(String source, WearMirror.Spot spot) {
        try {
            WearMirror.Entry entry;
            if (WearRestore.LATEST.equals(source)) {
                entry = mirror.get(spot);
                if (entry == null || !entry.hasWear()) entry = newestEntry(spot);
            } else {
                if (runSource == null || !runSource.backup.name.equals(source)) {
                    close(runSource);
                    runSource = WearArchive.open(new File(new File(home, WearBackupSet.FOLDER), source));
                }
                entry = runSource.entry(spot);
            }
            return entry == null || !entry.hasWear() ? null : ChunkErosionData.read(entry.blob());
        } catch (IOException unreadable) {
            Trmt.error("Wear backups: could not read " + source + " for chunk " + spot, unreadable);
            return null;
        }
    }

    /** What the newest backup holding wear has for this chunk, or null. */
    private WearMirror.Entry newestEntry(WearMirror.Spot spot) {
        try {
            WearArchive.Opened opened = newestOpened();
            return opened == null ? null : opened.entry(spot);
        } catch (IOException unreadable) {
            return null;
        }
    }

    /** The newest backup that holds any wear, opened once a session, or null. */
    private WearArchive.Opened newestOpened() {
        if (newest != null) return newest;
        for (WearBackupSet.Backup each : listing) {
            if (each.chunks <= 0) continue;
            try {
                newest = WearArchive.open(new File(new File(home, WearBackupSet.FOLDER), each.name));
            } catch (IOException unreadable) {
                Trmt.error("Wear backups: could not read " + each.name, unreadable);
            }
            break;
        }
        return newest;
    }

    // ------------------------------------------------------------------
    // Looking for what was lost, as the world opens
    // ------------------------------------------------------------------

    /**
     * Reads every chunk the mirror (or, with the mirror gone, the newest backup) holds wear for straight from the
     * world's region files, on the background thread, and counts those that have lost it. Chunks loaded meanwhile are
     * answered by their own load and left alone.
     */
    private void scan() {
        scanStarted = true;
        final WearMirror.Store store = mirror;
        final File world = worldDir;
        final boolean fromBackup = mirrorGone;
        submit(new Runnable() {

            @Override
            public void run() {
                List<WearMirror.Spot> spots = new ArrayList<WearMirror.Spot>();
                try {
                    if (fromBackup) {
                        synchronized (WearBackups.this) {
                            WearArchive.Opened opened = newestOpened();
                            if (opened != null) spots.addAll(opened.chunks());
                        }
                    } else {
                        for (String path : WearMirror.files(store.root())) {
                            byte[] bytes = Files.readAllBytes(new File(store.root(), path).toPath());
                            for (Map.Entry<Integer, WearMirror.Entry> each : WearMirror.read(bytes)
                                .entrySet()) {
                                if (!each.getValue()
                                    .hasWear()) continue;
                                WearMirror.Spot spot = WearMirror.spotOf(
                                    path,
                                    each.getKey()
                                        .intValue());
                                if (spot != null) spots.add(spot);
                            }
                        }
                    }
                } catch (IOException failed) {
                    Trmt.error("Wear backups: could not read the mirror to look for lost wear", failed);
                }
                int looked = 0;
                final List<WearMirror.Spot> found = new ArrayList<WearMirror.Spot>();
                final List<WearMirror.Spot> sameSave = new ArrayList<WearMirror.Spot>();
                for (WearMirror.Spot spot : spots) {
                    synchronized (WearBackups.this) {
                        if (mirror != store) return;
                        if (seen.contains(spot)) continue;
                    }
                    NBTTagCompound root = readChunk(world, spot);
                    if (root == null) continue;
                    looked++;
                    // The wear tag sits at the chunk's root, beside Level, which holds the save's stamp.
                    NBTTagCompound level = root.getCompoundTag("Level");
                    boolean tagged = root.hasKey(ErosionStore.NBT_KEY);
                    byte[] blob = tagged ? root.getByteArray(ErosionStore.NBT_KEY) : null;
                    boolean broken = tagged && WearLoss.damaged(
                        blob,
                        ChunkErosionData.read(blob)
                            .size());
                    WearMirror.Entry held = store.get(spot);
                    WearLoss.Kind kind = WearLoss
                        .of(tagged, broken, held, level.getLong("LastUpdate"), fromBackup && held == null);
                    // Same-save losses are counted apart: a few go back quietly as their chunks load, more are a loss.
                    if (kind == WearLoss.Kind.LOST) found.add(spot);
                    if (kind == WearLoss.Kind.QUIET) sameSave.add(spot);
                }
                final int read = looked;
                MainThread.onServer(new Runnable() {

                    @Override
                    public void run() {
                        scanned(store, read, found, sameSave);
                    }
                });
            }
        });
    }

    private synchronized void scanned(WearMirror.Store store, int read, List<WearMirror.Spot> found,
        List<WearMirror.Spot> sameSave) {
        if (mirror != store) return;
        // More than the quiet few lost from the same save, or the whole gone: every one of them is a loss.
        if (!big && (wholeLoss || quiet + sameSave.size() > quietMost)) big = true;
        if (big) found.addAll(sameSave);
        for (WearMirror.Spot spot : found) {
            // Read from its region file before its chunk loaded: the load has answered it since, and put it back or
            // set it aside itself.
            if (seen.contains(spot)) continue;
            mirror.hold(spot);
            lost.add(spot);
        }
        Trmt.LOG.info(
            "Wear backups: looked at {} chunks the {} holds wear for; {} have lost it.",
            Integer.valueOf(read),
            mirrorGone ? "newest backup" : "mirror",
            Integer.valueOf(lost.size()));
        if (lost.isEmpty() && dataFrom == null) return;
        if (dedicated) {
            if (!lost.isEmpty()) restoreLost(null);
            else if (noticeAt < 0L) noticeAt = ticks;
        } else if (!asked) {
            askAt = ticks;
        }
    }

    /** The single-player screen, offered once a session unless the player said not to ask. */
    private void offer() {
        if (dedicated || asked || lost.isEmpty() && dataFrom == null) return;
        asked = true;
        if (state.dontAsk) {
            Trmt.LOG.info(
                "Wear backups: {} chunks have lost wear; not asking, as this world was told not to.",
                Integer.valueOf(lost.size()));
            return;
        }
        WearBackupSet.Backup newestBackup = WearBackupSet.newest(listing);
        Trmt.LOG.info(
            "Wear backups: {} chunks have lost wear; asking whether to restore (newest backup {}).",
            Integer.valueOf(lost.size()),
            newestBackup == null ? "none" : newestBackup.name);
        Trmt.proxy.offerWearRestore(
            lost.size(),
            newestBackup == null ? -1L : newestBackup.made,
            newerWorld,
            !mirrorGone,
            dataFrom != null);
    }

    /** The single-player screen's answers, run on the server's thread. */
    public synchronized void answer(String choice) {
        if (home == null) return;
        if ("restore".equals(choice)) {
            restoreLost(null);
        } else if ("never".equals(choice)) {
            state.dontAsk = true;
            writeState();
            for (WearMirror.Spot spot : lost) mirror.release(spot);
            lost.clear();
            Trmt.LOG.info("Wear backups: told not to ask again in this world.");
        } else {
            Trmt.LOG.info("Wear backups: the lost wear was left as it is for this session.");
        }
    }

    // ------------------------------------------------------------------
    // Operators
    // ------------------------------------------------------------------

    /** A player has joined: an operator is told of the last automatic restore, once. */
    public synchronized void playerJoined(EntityPlayerMP player) {
        if (home == null || player == null || state.notice.isEmpty()) return;
        if (!player.canUseCommand(2, "trmt")) return;
        if (!state.told.add(player.getName())) return;
        writeState();
        tell(player);
    }

    private void tellOperators() {
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getPlayerList() == null) return;
        for (Object each : server.getPlayerList().getPlayers()) {
            if (!(each instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP) each;
            if (!player.canUseCommand(2, "trmt")) continue;
            if (state.told.add(player.getName())) tell(player);
        }
        writeState();
    }

    private void tell(EntityPlayerMP player) {
        player.sendMessage(
            new TextComponentString(
                TextFormatting.GRAY + "[" + Trmt.NAME + "] " + TextFormatting.WHITE + state.notice));
        player.sendMessage(
            new TextComponentString(
                TextFormatting.GRAY + "  "
                    + TextFormatting.AQUA
                    + "/trmt wear undo"
                    + TextFormatting.WHITE
                    + " takes it back out; "
                    + TextFormatting.AQUA
                    + "/trmt wear list"
                    + TextFormatting.WHITE
                    + " names the backups, and "
                    + TextFormatting.AQUA
                    + "/trmt wear restore <backup>"
                    + TextFormatting.WHITE
                    + " restores another."));
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    /** {@code /trmt wear}'s lines. */
    public synchronized List<String> status() {
        List<String> out = new ArrayList<String>();
        if (home == null) {
            out.add("Wear backups are off in this world (general.wearBackups).");
            return out;
        }
        WearBackupSet.Backup newestBackup = WearBackupSet.newest(listing);
        out.add(
            listing.size() + (listing.size() == 1 ? " backup" : " backups")
                + ", the newest "
                + (newestBackup == null ? "not yet taken" : newestBackup.name)
                + "; one every "
                + hours(interval)
                + " of play, "
                + TrmtConfig.wearBackupsKept
                + " kept, at most "
                + TrmtConfig.wearBackupDiskMb
                + " MB.");
        out.add(
            "This session: " + quiet
                + (quiet == 1 ? " chunk" : " chunks")
                + " put back quietly from the mirror, "
                + lost.size()
                + " lost and not yet put back.");
        if (run != null) {
            out.add(
                (run.undoing ? "Undoing the restore from " : "Restoring from ") + run.source
                    + ": "
                    + run.filled.size()
                    + " chunks filled, "
                    + run.waiting.size()
                    + " waiting until they load.");
        }
        return out;
    }

    /**
     * What restoring a backup would change, as one line: the chunks it holds that have no wear now and would be
     * filled, as far as the mirror and the loaded chunks can say without reading the world.
     */
    /** Whether the mirror holds wear, for the restore screen's first row. */
    public synchronized boolean mirrorHolds() {
        return home != null && !mirrorGone
            && !WearMirror.files(mirror.root())
                .isEmpty();
    }

    public synchronized String wouldChange(String name) {
        if (WearRestore.LATEST.equals(name)) {
            if (home == null) return "";
            Set<WearMirror.Spot> fills = new HashSet<WearMirror.Spot>(lost);
            fills.addAll(damaged);
            return Integer.toString(fills.size());
        }
        WearBackupSet.Backup chosen = WearBackupSet.named(listing, name);
        if (home == null || chosen == null) return "";
        int fills = 0;
        try {
            WearArchive.Opened opened = WearArchive.open(new File(new File(home, WearBackupSet.FOLDER), name));
            try {
                for (WearMirror.Spot spot : opened.chunks()) {
                    WearMirror.Entry now = mirror.get(spot);
                    if (lost.contains(spot) || damaged.contains(spot) || now == null || !now.hasWear()) fills++;
                }
            } finally {
                opened.close();
            }
        } catch (IOException unreadable) {
            return "";
        }
        return Integer.toString(fills);
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    /** Queues work for the background thread; false when it has stopped and nothing will run. */
    private boolean submit(Runnable task) {
        if (io == null || io.isShutdown()) return false;
        io.submit(task);
        return true;
    }

    private void flushLater() {
        final WearMirror.Store store = mirror;
        submit(new Runnable() {

            @Override
            public void run() {
                flushNow(store);
            }
        });
    }

    private static void flushNow(WearMirror.Store store) {
        if (store == null) return;
        try {
            store.flush();
        } catch (IOException failed) {
            Trmt.error("Wear backups: could not write the mirror", failed);
        }
    }

    private void writeState() {
        final File file = new File(home, STATE);
        final String text = state.text();
        submit(new Runnable() {

            @Override
            public void run() {
                try {
                    WearMirror.writeWhole(file, text.getBytes("UTF-8"));
                } catch (IOException failed) {
                    Trmt.error("Wear backups: could not write " + file, failed);
                }
            }
        });
    }

    private void writeRun() {
        final File file = new File(home, RUN);
        final byte[] bytes = run == null ? null : run.write();
        submit(new Runnable() {

            @Override
            public void run() {
                try {
                    if (bytes == null) file.delete();
                    else WearMirror.writeWhole(file, bytes);
                } catch (IOException failed) {
                    Trmt.error("Wear backups: could not write " + file, failed);
                }
            }
        });
    }

    private File dataDir() {
        return new File(worldDir, "data");
    }

    /**
     * This mod's world records as they stand in memory now (the heal clock and the spawn grants), and any other file of
     * this mod's in {@code data/} as it is on disk. On the server's thread: a few hundred bytes.
     */
    private Map<String, byte[]> liveWorldData() {
        Map<String, byte[]> out = diskWorldData();
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        World overworld = server == null || server.worlds == null || server.worlds.length == 0 ? null
            : server.worlds[0];
        if (overworld != null && overworld.getMapStorage() != null) {
            put(out, HealClockData.NAME, overworld.getMapStorage().getOrLoadData(HealClockData.class, HealClockData.NAME));
            put(out, SpawnGrantData.NAME, overworld.getMapStorage().getOrLoadData(SpawnGrantData.class, SpawnGrantData.NAME));
        }
        return out;
    }

    private static void put(Map<String, byte[]> out, String name, WorldSavedData record) {
        if (record == null) return;
        try {
            NBTTagCompound inner = new NBTTagCompound();
            record.writeToNBT(inner);
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("data", inner);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(root, bytes);
            out.put(name + ".dat", bytes.toByteArray());
        } catch (IOException never) {
            // Written to memory.
        }
    }

    /** This mod's files in {@code data/}, as they are on disk. */
    private Map<String, byte[]> diskWorldData() {
        Map<String, byte[]> out = new TreeMap<String, byte[]>();
        String[] names = dataDir().list();
        if (names == null) return out;
        for (String name : names) {
            if (!name.startsWith(DATA_PREFIX) || !name.endsWith(".dat")) continue;
            byte[] bytes = bytes(new File(dataDir(), name));
            if (bytes != null) out.put(name, bytes);
        }
        return out;
    }

    /**
     * Puts back, from the newest backup that has them, those of this mod's world records that are missing from
     * {@code data/}, before the world reads them. The heal clock comes back with its last reading forgotten, so the
     * time since the backup is time the ground heals over rather than time held still.
     *
     * @return whether anything was put back
     */
    private boolean restoreMissingWorldData() {
        boolean any = false;
        for (WearBackupSet.Backup each : listing) {
            WearArchive.Opened opened = null;
            try {
                opened = WearArchive.open(new File(new File(home, WearBackupSet.FOLDER), each.name));
                for (Map.Entry<String, byte[]> file : opened.data()
                    .entrySet()) {
                    File target = new File(dataDir(), file.getKey());
                    if (target.exists() || !file.getKey()
                        .startsWith(DATA_PREFIX)) continue;
                    byte[] bytes = file.getValue();
                    if (file.getKey()
                        .equals(HealClockData.NAME + ".dat")) bytes = forgetLastReading(bytes);
                    WearMirror.writeWhole(target, bytes);
                    // The heal clock is the record every world that has run this mod has on disk (it is written from
                    // its first second); the others are written only once something is granted, so their absence is
                    // no loss and is put right without counting as the world's data gone as a whole.
                    if (file.getKey()
                        .equals(HealClockData.NAME + ".dat")) {
                        any = true;
                        dataFrom = each.name;
                    }
                    Trmt.LOG.info("Wear backups: {} was missing and was put back from {}.", file.getKey(), each.name);
                }
            } catch (IOException failed) {
                Trmt.error("Wear backups: could not put this mod's world data back from " + each.name, failed);
            } finally {
                close(opened);
            }
            // The newest backup's records, and no older ones: an older backup's would be a step back.
            break;
        }
        return any;
    }

    private static byte[] forgetLastReading(byte[] bytes) throws IOException {
        NBTTagCompound root = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes));
        NBTTagCompound inner = root.getCompoundTag("data");
        inner.setLong("seen", -1L);
        root.setTag("data", inner);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, out);
        return out.toByteArray();
    }

    /** A chunk's save, straight from the world's region file, or null with none. On the background thread. */
    private static NBTTagCompound readChunk(File world, WearMirror.Spot spot) {
        File folder = spot.dimension.isEmpty() ? world : new File(world, spot.dimension);
        try {
            DataInputStream in = RegionFileCache.getChunkInputStream(folder, spot.chunkX, spot.chunkZ);
            if (in == null) return null;
            try {
                return CompressedStreamTools.read(in);
            } finally {
                in.close();
            }
        } catch (IOException unreadable) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Small things
    // ------------------------------------------------------------------

    static WearMirror.Spot spotOf(World world, int chunkX, int chunkZ) {
        String folder = world.provider.getSaveFolder();
        return new WearMirror.Spot(folder == null ? "" : folder, chunkX, chunkZ);
    }

    private static World worldOf(String dimension) {
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.worlds == null) return null;
        for (WorldServer world : server.worlds) {
            if (world == null) continue;
            String folder = world.provider.getSaveFolder();
            if ((folder == null ? "" : folder).equals(dimension)) return world;
        }
        return null;
    }

    private static long overworldTicks() {
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.worlds == null || server.worlds.length == 0) return 0L;
        return server.worlds[0].getTotalWorldTime();
    }

    private void say(ICommandSender who, TextFormatting color, String line) {
        if (who != null) {
            who.sendMessage(new TextComponentString(color + line));
            return;
        }
        // From the single-player screen: the player at the keyboard.
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (dedicated || server == null || server.getPlayerList() == null) return;
        for (Object each : server.getPlayerList().getPlayers()) {
            if (each instanceof EntityPlayerMP)
                ((EntityPlayerMP) each).sendMessage(new TextComponentString(color + line));
        }
    }

    private static String reasonWords(String reason) {
        if (WearBackupSet.UPDATE.equals(reason)) return "before a mod update";
        if (WearBackupSet.COMMAND.equals(reason)) return "before a command that clears wear";
        if (WearBackupSet.RESTORE.equals(reason)) return "before a restore";
        return "on the schedule";
    }

    private static String hours(long seconds) {
        if (seconds % 3600L == 0L) return seconds / 3600L + (seconds == 3600L ? " hour" : " hours");
        return seconds + " seconds";
    }

    /** A size as the restore screen and the list say it. */
    public static String size(long bytes) {
        if (bytes < 1024L) return bytes + " bytes";
        if (bytes < 1024L * 1024L) return (bytes + 512L) / 1024L + " KB";
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String text(File file) {
        byte[] bytes = bytes(file);
        try {
            return bytes == null ? null : new String(bytes, "UTF-8");
        } catch (java.io.UnsupportedEncodingException never) {
            return null;
        }
    }

    private static byte[] bytes(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static void close(WearArchive.Opened opened) {
        if (opened != null) opened.close();
    }

    private static boolean development() {
        Object said = net.minecraft.launchwrapper.Launch.blackboard == null ? null
            : net.minecraft.launchwrapper.Launch.blackboard.get("fml.deobfuscatedEnvironment");
        return Boolean.TRUE.equals(said);
    }
}
