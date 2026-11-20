package com.trmtgtnh.client;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.MapColor;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.network.TrmtNetwork;

/**
 * What a worn square is in Distant Horizons' far terrain, and the one door between that mod's plugins and the rest of
 * this one (0.9.229 W6 T16, the spike, and T17, the carry; docs/goals/0.9.229-w6-spec.md 3.13 in the 1.7.10 tree).
 *
 * <p>
 * <strong>Nothing here names Distant Horizons</strong>, so this class loads in every game. The plugin (one per loader,
 * {@code com.trmtgtnh.<loader>.dh.DhPlugin}, the only code that does) is registered only where that mod is loaded, asks
 * this class which blocks to opt in, which squares are worn and what color each is, and hands back the counts the
 * harness reads and the two things the harness may ask of that mod: what it holds at a square, and its settings pinned
 * for a photograph. {@code DistantHorizonsIsTouchedOnlyThroughThePluginTest} holds the line.
 *
 * <p>
 * <strong>Why the plugin tells that mod where the wear is.</strong> R11's first play opted in no ghost at all: that mod
 * reads a client chunk once, as it arrives, before this mod's painter has painted it, and in a world of the client's
 * own it also reads the server's chunks, which never hold a ghost (DH 3.3.3's FabricClientProxy, MixinClientPacketListener
 * and ServerApi, read). So a chunk is handed to it again each time the painter has painted it ({@link #painted}), and
 * as it builds its data from any chunk the plugin puts the ghost on every square this class says is worn
 * ({@link #wornAt}), so a server chunk saved later does not wash the path out.
 *
 * <p>
 * <strong>The color is the one a map is given.</strong> A worn square's {@link BlockGhost#mapColorAt}: the material the
 * square now is, darkened one palette step for the wear (spec CO25, CO26) - what the vanilla map item and Xaero's
 * minimap paint the same square. So far terrain draws a path in the color a map does, and an untouched lawn beside it
 * in that mod's own color for grass.
 *
 * <p>
 * <strong>And it is remembered, until this client learns otherwise.</strong> The family is read from this client's
 * record at the square, and the client holds records only for the chunks the server is sending it; far terrain is drawn
 * exactly where those are not. So the color each square was given while its record was here is kept, and given again
 * once the record has gone. Until T17 it was kept for ever, so a square that healed went on drawing worn far off. Now it
 * is dropped the moment this client learns the square is no longer worn, which is each of the ways the server tells it:
 * the square cleared while the chunk is held ({@link #cleared}), the chunk's whole statement no longer listing it
 * ({@link #restated}), the chunk arriving again with no record in it at all ({@link #arrivedBare}, since the server
 * sends nothing for a chunk with no wear), and everything cleared ({@link #forgetAll}); and, whenever that mod asks of
 * a square in a chunk this client holds, the record there is the whole answer. Each time a square is forgotten, or a
 * ghost the far terrain read is lifted, the chunk is handed to that mod again so its own data loses the ghost too. What
 * healed while the chunk was never sent again is not known to this client, and stays as it was last seen.
 *
 * <p>
 * <strong>Nothing is left to the order things happen in (G17).</strong> Played on ten copies, 26.2 NeoForge opted in no
 * ghost and held grass at the band near and far: that mod opts a state in only as it first wraps it, it had met no ghost,
 * and it had built nothing from the band's chunks since they were worn, though it answered every hand-over as taken. So
 * the plugin has every state of the ghosts wrapped as each of that mod's levels loads ({@link #levelLoaded}), and a chunk
 * handed over is owed until that mod is seen building from it ({@link #processing}), handed over again until then
 * ({@link #handAgain}). What the harness asks of that mod is asked on a thread of its own ({@link #ask}).
 *
 * <p>
 * <strong>And nothing is forgotten that did not heal (0.9.229, shift 28).</strong> Played again, 26.1.2 NeoForge said
 * "remembered 776, forgotten as healed 1048" and 26.3 NeoForge "remembered 256, forgotten as healed 1568" from 512 blocks
 * off, in a scene where nothing heals, and far terrain held grass where the line was. Whether this client holds a chunk
 * was asked of {@code LevelReader.hasChunk}, which the client's level answers true for every chunk, held or not
 * ({@code ClientLevel.hasChunk} is {@code iconst_1, ireturn} in every edition from 1.20.1 to 26.3, javap). So every
 * square that mod asked of once its record had gone was taken as healed in a chunk held here and forgotten, whenever
 * that mod happened to build from the band's chunks after this client had let their records go; and no color was ever
 * given from memory ("from memory 0" on all ten copies). Now a chunk is held only where the client hands it over
 * ({@link #heldChunk}). And a chunk arriving with no record is only forgotten as bare once its statement has had time to
 * follow it ({@link #BARE_AFTER_MS}): the server sends a chunk's wear after the chunk itself, so the client's chunk load
 * always came first and forgot everything remembered in a worn chunk coming back. Each way of forgetting is counted on
 * its own in the harness's line.
 *
 * <p>
 * <strong>And far terrain follows the overlay's switch (0.9.229; Xep, 2026-11-09: "Follow the switch").</strong> Until
 * then nothing that mod asked of this one read the switch, so with the overlay switched off and the ghosts lifted near,
 * far terrain went on drawing recorded wear. Now, switched off ({@link #overlayShown}), no square is given a color or
 * held as the ghost, and nothing remembered is forgotten: the server's answer to the hello that says so clears this
 * client's records and sends no more, which is no heal. That mod builds a square's picture once and keeps it, so a
 * switch either way, seen at its next frame, has it build its far terrain again from the data it holds
 * ({@link #followTheSwitch}), asking every opted-in square again; and each chunk whose ghosts were lifted is handed to it
 * again as before (ClientOverlay.handleClearAll), now read without the ghost.
 *
 * <p>
 * <strong>And only that clear is no heal (0.9.229, the review of the switch).</strong> Switched off, every clear-all
 * was taken for the server's answer to this client's own hello saying so, and kept everything remembered; but a server
 * also clears a client it stops sending to after its mod or rules changed, and one it refuses, and far wear kept across
 * those came back stale with the overlay switched on again. Now a clear keeps it only where one is owed to a hello
 * saying off (TrmtNetwork.takeClearOwed), one clear a hello; any other forgets everything, switched off or not
 * ({@link #forgetAll}).
 */
public final class DistantColors {

    private DistantColors() {}

    /** The answer for "leave that mod's own color". */
    public static final int OWN = -1;

    /**
     * The most squares remembered. Far past any road network a player walks - each is one map entry - and a world that
     * reaches it starts again empty rather than growing without end.
     */
    static final int REMEMBERED_MOST = 1 << 18;

    /** The color each worn square was last given, filed by its chunk and then by its key in the chunk (ErosionKey). */
    private static final Map<Long, Map<Integer, Integer>> REMEMBERED = new ConcurrentHashMap<Long, Map<Integer, Integer>>();

    /** How many squares REMEMBERED holds, across every chunk. */
    private static final AtomicInteger REMEMBERED_COUNT = new AtomicInteger();

    /** The world those colors were given in: a different one forgets them all. */
    private static volatile WeakReference<Object> rememberedIn = new WeakReference<Object>(null);

    /** What the plugin did, for the harness's lines. */
    private static final AtomicInteger WRAPPED = new AtomicInteger();

    private static final AtomicInteger OPTED_IN = new AtomicInteger();

    private static final AtomicInteger FROM_RECORD = new AtomicInteger();

    private static final AtomicInteger FROM_MEMORY = new AtomicInteger();

    private static final AtomicInteger LEFT_OWN = new AtomicInteger();

    private static final AtomicInteger FRAMES = new AtomicInteger();

    private static final AtomicInteger PUSHED = new AtomicInteger();

    private static final AtomicInteger PUT_ON = new AtomicInteger();

    private static final AtomicInteger FORGOTTEN = new AtomicInteger();

    /**
     * What was forgotten, each way on its own (shift 28): the five ways this client learns of a heal - a square cleared,
     * a chunk stated again without it, a chunk back with no statement at all, everything cleared, a chunk held with no
     * record at the square - and the two that are no heal at all, another world and the most remembered reached.
     */
    private static final AtomicInteger BY_CLEARED = new AtomicInteger();

    private static final AtomicInteger BY_RESTATED = new AtomicInteger();

    private static final AtomicInteger BY_BARE = new AtomicInteger();

    private static final AtomicInteger BY_ALL = new AtomicInteger();

    private static final AtomicInteger BY_HELD = new AtomicInteger();

    private static final AtomicInteger BY_WORLD = new AtomicInteger();

    private static final AtomicInteger BY_MOST = new AtomicInteger();

    /** Every state of this mod's ghosts opted in so far, and those that mod had wrapped before the plugin bound. */
    private static final Set<Object> OPTED_STATES = ConcurrentHashMap.newKeySet();

    private static final Set<Object> WRAPPED_EARLY = ConcurrentHashMap.newKeySet();

    /** The first reason that mod would not wrap a ghost's state, said once in the harness's line. */
    private static final AtomicReference<String> WRAP_REFUSED = new AtomicReference<String>();

    /** The first reason a painted chunk could not be handed over, said once in the harness's line. */
    private static final AtomicReference<String> PUSH_REFUSED = new AtomicReference<String>();

    /** What the harness may ask of Distant Horizons, through the plugin; null until the plugin is registered. */
    private static volatile Plugin plugin;

    /**
     * The plugin's half of the door: what only code that names Distant Horizons can answer. Implemented by the plugin
     * alone, and handed here once it has registered its events.
     */
    public interface Plugin {

        /** The block Distant Horizons holds at a square, as its own serial string, or why it could not say. */
        String heldAt(int x, int y, int z);

        /**
         * Pins its settings for a far photograph - its far distance in chunks, no fog of its own, its finest detail -
         * or puts its own settings back with nought.
         */
        void pinForPhotograph(int chunks);

        /** Hands it a chunk of this client's world to read again; null where it took it, or why it did not. */
        String reread(Object world, Object chunk);

        /**
         * Has it wrap a block state now, in one of its own levels (as handed to {@link #levelLoaded}), as it would on
         * first meeting that state in a chunk: firing its wrapped event, which is where a ghost is opted in, if it has
         * not wrapped that state before. Null where it wrapped it, or why it did not.
         */
        String wrap(Object state, Object level);

        /**
         * Has it build its far terrain's picture again from the data it holds, asking every opted-in square's color
         * again, as it does when its own color settings change; null where it took it, or why it did not.
         */
        String redraw();
    }

    /** Called by the plugin once its events are bound. */
    public static void registered(Plugin which) {
        plugin = which;
    }

    /** Whether the plugin is registered in this game: Distant Horizons is loaded and its events took. */
    public static boolean pluginRegistered() {
        return plugin != null;
    }

    // ------------------------------------------------------------------
    // The overlay's switch (0.9.229; Xep, 2026-11-09: "Follow the switch")
    // ------------------------------------------------------------------

    /**
     * Whether this client draws worn ground: the mod on, and the overlay shown by the player's own switch or held on by a
     * server's real ruts - the painter's own question before it puts a ghost down. Switched off, far terrain draws no
     * recorded wear, the same as near terrain.
     */
    static boolean overlayShown() {
        return TrmtConfig.enabled && TrmtConfig.overlayVisible();
    }

    /** Whether the overlay was shown at the last frame that mod drew; null before its first. */
    private static volatile Boolean shownAtLastFrame;

    private static final AtomicInteger SWITCHED = new AtomicInteger();

    private static final AtomicInteger REDRAWN = new AtomicInteger();

    private static final AtomicInteger WITHHELD = new AtomicInteger();

    /** The first reason that mod would not build its far terrain again, said once in the harness's line. */
    private static final AtomicReference<String> REDRAW_REFUSED = new AtomicReference<String>();

    /**
     * Called at each frame that mod draws: where the overlay has been switched either way since the last, that mod is
     * asked to build its far terrain again from the data it holds ({@link Plugin#redraw}). It works out a square's color
     * once, as it builds that part of its picture, and keeps it; built again, it asks every opted-in square again, and
     * {@link #colorAt} answers by the switch - its own color switched off, the wear switched on - for terrain it built
     * long before, whose chunks this client will never be sent again. Distant Horizons 3.3.3 and 3.3.4 (javap):
     * {@code DhApiRenderProxy.clearRenderDataCache} has every client level's {@code ClientLevelModule.clearRenderCache}
     * clear its block color cache and close every render section of its quadtree, built again from its data.
     */
    static void followTheSwitch() {
        boolean now = overlayShown();
        Boolean before = shownAtLastFrame;
        shownAtLastFrame = Boolean.valueOf(now);
        if (before == null || before.booleanValue() == now) return;
        SWITCHED.incrementAndGet();
        Plugin asked = plugin;
        if (asked == null) return;
        String refused;
        try {
            refused = asked.redraw();
        } catch (RuntimeException | LinkageError awkward) {
            refused = awkward.toString();
        }
        if (refused == null) {
            REDRAWN.incrementAndGet();
        } else {
            REDRAW_REFUSED.compareAndSet(null, refused);
        }
    }

    // ------------------------------------------------------------------
    // The ghost opted in whatever the order (G17, the Far cell on ten copies)
    // ------------------------------------------------------------------

    /**
     * Called by the plugin as each of Distant Horizons' levels loads: every state of this mod's ghosts is wrapped there
     * and then, while the plugin is bound, so each is opted in by the plugin's own call (see {@link #optInEveryGhost}).
     */
    public static void levelLoaded(Object level) {
        optInEveryGhost(level, ModBlocks.ghosts());
    }

    /**
     * Has that mod wrap every state of these ghosts in this level, so the event that opts a ghost in fires now, while the
     * plugin is bound, and not whenever that mod first meets a ghost in a chunk - which on 26.2 NeoForge and on 26.1.2
     * Fabric's first run was never, though the band was worn under it. That mod wraps a state once and keeps it, and its
     * API has no way to wrap one again; a ghost state it had wrapped before the plugin bound is counted and said, since
     * far terrain then draws it in that mod's own color.
     */
    static void optInEveryGhost(Object level, Block... ghosts) {
        Plugin asked = plugin;
        if (asked == null || level == null || ghosts == null) return;
        for (Block ghost : ghosts) {
            if (ghost == null) continue;
            for (BlockState state : ghost.getStateDefinition()
                .getPossibleStates()) {
                String refused;
                try {
                    refused = asked.wrap(state, level);
                } catch (RuntimeException | LinkageError awkward) {
                    refused = awkward.toString();
                }
                if (refused != null) {
                    WRAP_REFUSED.compareAndSet(null, refused);
                } else if (!OPTED_STATES.contains(state) && WRAPPED_EARLY.add(state)) {
                    Trmt.LOG.warn(
                        "Distant Horizons wrapped this mod's ghost ({}) before the plugin bound its events, and its API cannot wrap a state again: far terrain draws that state in its own color",
                        state);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // What the harness asks, never on the game's own thread (G17)
    // ------------------------------------------------------------------

    /** What Distant Horizons holds at a square, as {@link #ask} reads it: null until that mod has answered. */
    public static final class Reading {

        private volatile String answer;

        /** That mod's answer, as {@link #heldAt} words it, or null while it has not given one. */
        public String answer() {
            return answer;
        }
    }

    /**
     * Asks Distant Horizons what it holds at a square on a thread of its own, and returns at once. That mod reads its own
     * data to answer and waits for the read without a bound; asked on the game's own thread, 1.21.1 NeoForge's answer
     * never came and the game sat 645 seconds after the far set's band was laid. So the harness waits for the answer a
     * second at a time, to a bound of its own (ModsFixture.farReadingWaits). A reading that never answers leaves a daemon
     * thread waiting, which ends with the game.
     */
    public static Reading ask(final int x, final int y, final int z) {
        final Reading reading = new Reading();
        Thread asker = new Thread(() -> reading.answer = heldAt(x, y, z), "TRMT Distant Horizons reading");
        asker.setDaemon(true);
        asker.start();
        return reading;
    }

    /**
     * What Distant Horizons holds at a square, or a line saying it is not there to ask. It waits on that mod's own read
     * of its data, so the harness asks through {@link #ask}, never on the game's own thread.
     */
    public static String heldAt(int x, int y, int z) {
        Plugin asked = plugin;
        if (asked == null) return "no Distant Horizons plugin registered";
        try {
            return asked.heldAt(x, y, z);
        } catch (RuntimeException | LinkageError awkward) {
            return "Distant Horizons could not say: " + awkward;
        }
    }

    /** Pins Distant Horizons' settings for a far photograph, or puts them back with nought; nothing where it is not loaded. */
    public static void pinForPhotograph(int chunks) {
        Plugin asked = plugin;
        if (asked != null) asked.pinForPhotograph(chunks);
    }

    /**
     * Called by the painter after it has painted a chunk of this client's world: where Distant Horizons is loaded, the
     * chunk is handed to it to read again, now that it holds the ghosts, and is owed until that mod is seen building
     * from it ({@link #processing}). Nothing at all where it is not.
     */
    public static void painted(Object world, Object chunk, int chunkX, int chunkZ) {
        if (plugin == null || world == null || chunk == null) return;
        handOver(world, chunk);
        OWED.put(Long.valueOf(chunkKey(chunkX, chunkZ)), new Owed(world, chunk, chunkX, chunkZ, clock.getAsLong()));
    }

    /** Hands a chunk to that mod to read again, counted, or its first refusal kept. */
    private static void handOver(Object world, Object chunk) {
        Plugin asked = plugin;
        if (asked == null) return;
        String refused;
        try {
            refused = asked.reread(world, chunk);
        } catch (RuntimeException | LinkageError awkward) {
            refused = awkward.toString();
        }
        if (refused == null) {
            PUSHED.incrementAndGet();
        } else {
            PUSH_REFUSED.compareAndSet(null, refused);
        }
    }

    // ------------------------------------------------------------------
    // A hand-over that mod never built from, handed over again (G17)
    // ------------------------------------------------------------------

    /**
     * How long a chunk handed over may go without that mod building from it before it is handed over again, how often
     * the owed chunks are looked at, and how many times one is handed over again before it is given up. That mod answers
     * every hand-over as taken, and drops one without a word when it already has that chunk waiting in its queue, or is
     * generating it, or has no queue for the level yet (DH 3.3.3's SharedApi.applyChunkUpdate, read): on 26.2 NeoForge it
     * built nothing from the band's chunks after they were worn, and far terrain held plain grass there.
     */
    static final long HAND_AGAIN_AFTER_MS = 3000L;

    static final long HAND_AGAIN_EVERY_MS = 1000L;

    static final int HAND_AGAIN_MOST = 10;

    /** The time, in milliseconds; a test's own clock in a test. */
    static volatile LongSupplier clock = System::currentTimeMillis;

    /** When the owed chunks were last looked at. */
    private static volatile long lastHandAgain;

    /** A chunk handed to that mod and not yet seen built from since, by its chunk key. */
    private static final Map<Long, Owed> OWED = new ConcurrentHashMap<Long, Owed>();

    private static final AtomicInteger HANDED_AGAIN = new AtomicInteger();

    private static final AtomicInteger GIVEN_UP = new AtomicInteger();

    /** One owed chunk: the world and chunk as this client held them, held weakly so a chunk let go is let go here too. */
    private static final class Owed {

        final WeakReference<Object> world;

        final WeakReference<Object> chunk;

        final int chunkX;

        final int chunkZ;

        volatile long handedAt;

        volatile int tries;

        Owed(Object world, Object chunk, int chunkX, int chunkZ, long handedAt) {
            this.world = new WeakReference<Object>(world);
            this.chunk = new WeakReference<Object>(chunk);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.handedAt = handedAt;
        }
    }

    /**
     * Called by the plugin as that mod builds its data from a chunk, on that mod's own threads: whatever chunk of this
     * client's it was handed there is taken, since the plugin puts the ghost on every worn square as that mod builds.
     */
    public static void processing(int chunkX, int chunkZ) {
        if (OWED.isEmpty()) return;
        OWED.remove(Long.valueOf(chunkKey(chunkX, chunkZ)));
    }

    /**
     * Every owed chunk handed over long enough ago is handed over again, while this client still holds it, to
     * {@link #HAND_AGAIN_MOST} times; then it is given up and counted.
     */
    static void handAgain(long now) {
        if (plugin == null || OWED.isEmpty()) return;
        for (Iterator<Owed> each = OWED.values()
            .iterator(); each.hasNext();) {
            Owed owed = each.next();
            if (now - owed.handedAt < HAND_AGAIN_AFTER_MS) continue;
            Object world = owed.world.get();
            Object chunk = owed.chunk.get();
            if (!(world instanceof BlockGetter) || chunk == null || !holds((BlockGetter) world, owed.chunkX, owed.chunkZ)) {
                each.remove();
                continue;
            }
            if (owed.tries >= HAND_AGAIN_MOST) {
                each.remove();
                GIVEN_UP.incrementAndGet();
                continue;
            }
            owed.tries++;
            owed.handedAt = now;
            HANDED_AGAIN.incrementAndGet();
            handOver(world, chunk);
        }
    }

    // ------------------------------------------------------------------
    // What this client learns of a heal (T17)
    // ------------------------------------------------------------------

    /**
     * The server has cleared a square while this client holds its chunk (ClientOverlay.handleDelta): its remembered color
     * is dropped, and where it was recorded worn - so its ghost has just been lifted - the chunk is handed to that mod
     * again.
     *
     * @param chunk the chunk as this client holds it, or null where it holds none (then nothing is handed over)
     */
    public static void cleared(Object world, Object chunk, int x, int y, int z, boolean wasRecorded) {
        if (plugin == null) return;
        boolean forgot = forget(chunkKey(x >> 4, z >> 4), ErosionKey.packWorld(x, y, z), BY_CLEARED);
        if (forgot || wasRecorded) painted(world, chunk, x >> 4, z >> 4);
    }

    /**
     * The server has stated a chunk's wear again, whole (ClientOverlay.handleChunkErosion): every remembered square it no
     * longer lists is dropped, and where one was dropped, or the last statement listed a square this one does not, the
     * chunk is handed to that mod again.
     *
     * @param chunk the chunk as this client holds it, or null where its blocks have not arrived (nothing handed over)
     * @param previous the chunk's last statement, or null
     */
    public static void restated(Object world, Object chunk, int chunkX, int chunkZ, int[] keys,
        ClientErosionCache.ChunkOverlay previous) {
        if (plugin == null) return;
        Set<Integer> listed = new HashSet<Integer>();
        if (keys != null) {
            for (int key : keys) listed.add(Integer.valueOf(key));
        }
        // A statement came: whether the chunk is bare is answered (see arrivedBare).
        BARE.remove(Long.valueOf(chunkKey(chunkX, chunkZ)));
        boolean changed = forgetAllBut(chunkKey(chunkX, chunkZ), listed, BY_RESTATED) > 0;
        if (!changed && previous != null) {
            for (int i = 0; i < previous.size() && !changed; i++) {
                if (!listed.contains(Integer.valueOf(previous.keyAt(i)))) changed = true;
            }
        }
        if (changed) painted(world, chunk, chunkX, chunkZ);
    }

    /**
     * How long a chunk that arrived with no record waits for its statement before what it remembers is forgotten as
     * healed. The server sends a chunk's wear just after the chunk itself (Fabric's hook at the tail of
     * {@code PlayerChunkSender.sendChunk}, NeoForge's {@code ChunkWatchEvent.Sent}), so this client's chunk load always
     * comes first and finds no record, worn or not; until shift 28 everything remembered in a worn chunk coming back was
     * forgotten there and then. Looked at with the owed chunks, once a second ({@link #drawn}).
     */
    static final long BARE_AFTER_MS = 3000L;

    /** A chunk that arrived with no record and has had no statement since, by its chunk key. */
    private static final Map<Long, Bare> BARE = new ConcurrentHashMap<Long, Bare>();

    /** One such chunk: the world it arrived in, held weakly, and when. */
    private static final class Bare {

        final WeakReference<Object> world;

        final int chunkX;

        final int chunkZ;

        final long arrivedAt;

        Bare(Object world, int chunkX, int chunkZ, long arrivedAt) {
            this.world = new WeakReference<Object>(world);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.arrivedAt = arrivedAt;
        }
    }

    /**
     * A chunk has arrived in this client's world with no record held for it (ClientSide.chunkLoaded). The server sends
     * no statement for a chunk with no wear, so where none follows by {@link #BARE_AFTER_MS}, every square remembered in
     * it healed while it was away: each is dropped then, and where any was, the chunk is handed to that mod again
     * ({@link #settleBare}). A statement that follows decides instead ({@link #restated}).
     */
    public static void arrivedBare(Object world, Object chunk, int chunkX, int chunkZ) {
        // Switched off, the server sends this client no wear at all: a chunk with none says nothing of a heal.
        if (plugin == null || !overlayShown()) return;
        Long key = Long.valueOf(chunkKey(chunkX, chunkZ));
        if (!REMEMBERED.containsKey(key)) return;
        BARE.put(key, new Bare(world, chunkX, chunkZ, clock.getAsLong()));
    }

    /**
     * Every chunk that arrived with no record long enough ago, and has had no statement since: where this client still
     * holds it and still has no record in it, what it remembers there is forgotten as healed and the chunk handed over.
     */
    static void settleBare(long now) {
        if (BARE.isEmpty()) return;
        if (!overlayShown()) {
            // Switched off before its statement came: none will now, and that is no heal (see arrivedBare).
            BARE.clear();
            return;
        }
        for (Iterator<Bare> each = BARE.values()
            .iterator(); each.hasNext();) {
            Bare bare = each.next();
            if (now - bare.arrivedAt < BARE_AFTER_MS) continue;
            each.remove();
            if (ClientErosionCache.get()
                .overlay(bare.chunkX, bare.chunkZ) != null) continue;
            Object world = bare.world.get();
            Object chunk = world instanceof BlockGetter ? heldChunk((BlockGetter) world, bare.chunkX, bare.chunkZ) : null;
            if (chunk == null) continue;
            if (forgetAllBut(chunkKey(bare.chunkX, bare.chunkZ), null, BY_BARE) > 0) {
                painted(world, chunk, bare.chunkX, bare.chunkZ);
            }
        }
    }

    /**
     * The server has cleared every record (ClientOverlay.handleClearAll): nothing remembered stands. Unless the overlay
     * is switched off and the clear is the server's answer to this client's own hello saying so
     * (TrmtNetwork.answerHello), owed to it and taken here (TrmtNetwork.takeClearOwed): that is no heal, and everything
     * remembered is kept for the overlay switched on again. Any other clear, a server unsubscribing this client after
     * its mod or rules changed or refusing it, forgets everything, switched off or not; and one owed is taken whether
     * or not it keeps anything.
     */
    public static void forgetAll() {
        if (TrmtNetwork.takeClearOwed() && !overlayShown()) {
            KEPT_AS_ANSWER.incrementAndGet();
            return;
        }
        forgetAll(BY_ALL);
    }

    /** Clears kept as the answer to this client's own hello saying the overlay is off, for the harness's line. */
    private static final AtomicInteger KEPT_AS_ANSWER = new AtomicInteger();

    /** Everything remembered forgotten, counted the way it went. */
    private static void forgetAll(AtomicInteger why) {
        int had = REMEMBERED_COUNT.getAndSet(0);
        REMEMBERED.clear();
        BARE.clear();
        if (had > 0) {
            FORGOTTEN.addAndGet(had);
            why.addAndGet(had);
        }
    }

    // ------------------------------------------------------------------
    // What the plugin asks
    // ------------------------------------------------------------------

    /**
     * Whether a block state is opted in to have its color asked: this mod's ghosts, and nothing else. Every other block
     * keeps that mod's own color and is never asked about, which is what keeps the cost to the worn squares. Every
     * state asked about is counted, so the harness can say whether that mod wrapped any state at all.
     */
    public static boolean optsIn(Object state) {
        WRAPPED.incrementAndGet();
        if (!(state instanceof BlockState) || !ModBlocks.isGhost(((BlockState) state).getBlock())) return false;
        OPTED_IN.incrementAndGet();
        OPTED_STATES.add(state);
        return true;
    }

    /**
     * Whether a square is worn, for the far terrain to hold this mod's ghost there whatever chunk it was read from:
     * worn in this client's record (and its color remembered), or remembered from one. A square in a chunk this client
     * holds, with no record there, is not worn, and anything remembered of it is forgotten.
     */
    public static boolean wornAt(BlockGetter world, int x, int y, int z) {
        // Switched off, no square is the ghost, so a chunk handed over as its ghosts are lifted is read without them.
        if (!overlayShown()) return false;
        forgetIfAnotherWorld(world);
        int given = colorOf(BlockGhost.mapColorAt(null, world, new BlockPos(x, y, z)));
        if (given != OWN) {
            remember(x, y, z, given);
            PUT_ON.incrementAndGet();
            return true;
        }
        if (holds(world, x >> 4, z >> 4)) {
            forget(chunkKey(x >> 4, z >> 4), ErosionKey.packWorld(x, y, z), BY_HELD);
            return false;
        }
        if (remembered(x, y, z) == null) return false;
        PUT_ON.incrementAndGet();
        return true;
    }

    /**
     * The color of a run of that mod's data, asked at its foot, as 0xRRGGBB, or {@link #OWN} to leave that mod's own.
     *
     * <p>
     * <strong>Asked at the foot, answered from the top (G17, the Far cell played on 1.12.2, shift 28).</strong> That mod
     * keeps a column as runs of one block, each a bottom and a height, and asks a run's color at its bottom:
     * {@code FullDataToRenderDataTransformer.setRenderColumnView} sets the position's y to the run's bottom plus the
     * level's floor before {@code ClientBlockStateColorCache.getColor} fires the color event (Distant Horizons 3.3.3,
     * read). The ghost's run at a worn square reaches down through the ground under it - "y -63 to 4" at the band on 1.20.1
     * and on 26.3, whose record is at y 3 - so asked at that y alone the band found no record and was drawn in
     * that mod's own color for the ghost, its average of earth's texture (138,100,70), never this mod's map color; only
     * some of the line's columns showed it. So the square a run stands for is the nearest at or above its foot that this
     * client's record says is worn, read up the run while this client holds the chunk (to the first air, where the ground
     * ends) and up the column to the level's build height where it does not; and the color remembered for it likewise,
     * remembered and forgotten at the square itself. {@link #wornAt} stays exact: that mod asks it at the square's own y.
     *
     * @param world the world the square is in, as this client holds it, or null where it holds none (another
     *        dimension's level), where only the square asked at is read
     */
    public static int colorAt(BlockGetter world, BlockState state, int x, int y, int z) {
        // Switched off, that mod's own color, and nothing remembered or forgotten (see followTheSwitch).
        if (!overlayShown()) {
            WITHHELD.incrementAndGet();
            return OWN;
        }
        forgetIfAnotherWorld(world);
        boolean held = holds(world, x >> 4, z >> 4);
        int top = world == null ? y : held ? runTop(world, x, y, z) : columnTop(world);
        for (int at = y; at <= top; at++) {
            int given = colorOf(BlockGhost.mapColorAt(state, world, new BlockPos(x, at, z)));
            if (given != OWN) {
                remember(x, at, z, given);
                FROM_RECORD.incrementAndGet();
                return given;
            }
        }
        if (held) {
            forgetRun(x, y, top, z, BY_HELD);
            LEFT_OWN.incrementAndGet();
            return OWN;
        }
        Integer kept = rememberedInRun(x, y, top, z);
        if (kept != null) {
            FROM_MEMORY.incrementAndGet();
            return kept.intValue();
        }
        LEFT_OWN.incrementAndGet();
        return OWN;
    }

    /** The highest y a block can stand at in this world: the end of any column read upward. */
    private static int columnTop(BlockGetter world) {
        return world.getMaxBuildHeight() - 1;
    }

    /** The highest y of the ground this client holds that rises unbroken from y: the top of the run asked at its foot. */
    private static int runTop(BlockGetter world, int x, int y, int z) {
        int highest = columnTop(world);
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        int top = y;
        while (top < highest && !world.getBlockState(above.set(x, top + 1, z))
            .isAir()) top++;
        return top;
    }

    /**
     * The color remembered for the lowest square of this column from y to top, or null where none is remembered. Each
     * key's y is read through {@link ErosionKey#y}, sign and all, never by the order of the keys: below nought a key
     * sorts after every square above it.
     */
    private static Integer rememberedInRun(int x, int from, int to, int z) {
        Map<Integer, Integer> chunk = REMEMBERED.get(Long.valueOf(chunkKey(x >> 4, z >> 4)));
        if (chunk == null) return null;
        Integer found = null;
        int lowest = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Integer> each : chunk.entrySet()) {
            int key = each.getKey()
                .intValue();
            int at = ErosionKey.y(key);
            if (!inColumn(key, x, z) || at < from || at > to || at >= lowest) continue;
            lowest = at;
            found = each.getValue();
        }
        return found;
    }

    /** Forgets every square remembered in this column from y to top, counted the way they went; how many were. */
    private static int forgetRun(int x, int from, int to, int z, AtomicInteger why) {
        long chunkKey = chunkKey(x >> 4, z >> 4);
        Map<Integer, Integer> chunk = REMEMBERED.get(Long.valueOf(chunkKey));
        if (chunk == null) return 0;
        int forgot = 0;
        for (Integer key : new java.util.ArrayList<Integer>(chunk.keySet())) {
            int at = ErosionKey.y(key.intValue());
            if (inColumn(key.intValue(), x, z) && at >= from && at <= to && forget(chunkKey, key.intValue(), why)) forgot++;
        }
        return forgot;
    }

    /** Whether a key of a chunk's squares is in the column at x, z. */
    private static boolean inColumn(int key, int x, int z) {
        return ErosionKey.localX(key) == (x & 0xF) && ErosionKey.localZ(key) == (z & 0xF);
    }

    /**
     * The color a map gives the square from this client's record, as 0xRRGGBB, or {@link #OWN} where nothing is recorded:
     * what a run standing for the square is drawn in. Read for the harness, which holds the far photograph to it, and
     * counted nowhere.
     */
    public static int wornColorAt(BlockGetter world, int x, int y, int z) {
        return colorOf(BlockGhost.mapColorAt(null, world, new BlockPos(x, y, z)));
    }

    /**
     * Whether this client holds a chunk, so its record there is the whole answer: it holds the chunk's wear, or the
     * chunk's blocks (a chunk with no wear has no record to hold), as {@link #heldChunk} hands them over.
     */
    static boolean holds(BlockGetter world, int chunkX, int chunkZ) {
        if (ClientErosionCache.get()
            .overlay(chunkX, chunkZ) != null) return true;
        return heldChunk(world, chunkX, chunkZ) != null;
    }

    /**
     * A chunk of this client's world as it holds it, or null where it holds none: the client's own chunk cache asked not
     * to load, so a chunk it has not got is null rather than the empty stand-in it gives for one, which is never handed
     * over either. Never {@code hasChunk}: the client's level answers true for every chunk, held or not
     * ({@code ClientLevel.hasChunk} is {@code iconst_1, ireturn} in every edition from 1.20.1 to 26.3, javap), so with it
     * every remembered square was forgotten as that mod asked of it from far away (shift 28). Asked of this client's
     * world alone, from any thread: its chunk cache is read without a lock.
     */
    public static Object heldChunk(BlockGetter world, int chunkX, int chunkZ) {
        if (!(world instanceof LevelReader)) return null;
        ChunkAccess chunk = ((LevelReader) world).getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        return chunk == null || chunk instanceof EmptyLevelChunk ? null : chunk;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ClientErosionCache.chunkKey(chunkX, chunkZ);
    }

    private static Integer remembered(int x, int y, int z) {
        Map<Integer, Integer> chunk = REMEMBERED.get(Long.valueOf(chunkKey(x >> 4, z >> 4)));
        return chunk == null ? null : chunk.get(Integer.valueOf(ErosionKey.packWorld(x, y, z)));
    }

    private static void remember(int x, int y, int z, int color) {
        Integer key = Integer.valueOf(ErosionKey.packWorld(x, y, z));
        Long chunkKey = Long.valueOf(chunkKey(x >> 4, z >> 4));
        if (REMEMBERED_COUNT.get() >= REMEMBERED_MOST && remembered(x, y, z) == null) forgetAll(BY_MOST);
        Map<Integer, Integer> chunk = REMEMBERED.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<Integer, Integer>());
        if (chunk.put(key, Integer.valueOf(color)) == null) REMEMBERED_COUNT.incrementAndGet();
    }

    /** Forgets one square, counted the way it went; whether it was remembered. */
    private static boolean forget(long chunkKey, int key, AtomicInteger why) {
        Map<Integer, Integer> chunk = REMEMBERED.get(Long.valueOf(chunkKey));
        if (chunk == null || chunk.remove(Integer.valueOf(key)) == null) return false;
        REMEMBERED_COUNT.decrementAndGet();
        FORGOTTEN.incrementAndGet();
        why.incrementAndGet();
        if (chunk.isEmpty()) REMEMBERED.remove(Long.valueOf(chunkKey), chunk);
        return true;
    }

    /** Forgets every square of a chunk but those kept (null keeps none), counted the way they went; how many. */
    private static int forgetAllBut(long chunkKey, Set<Integer> kept, AtomicInteger why) {
        Map<Integer, Integer> chunk = REMEMBERED.get(Long.valueOf(chunkKey));
        if (chunk == null) return 0;
        int forgot = 0;
        for (Iterator<Integer> each = chunk.keySet()
            .iterator(); each.hasNext();) {
            Integer key = each.next();
            if (kept != null && kept.contains(key)) continue;
            each.remove();
            forgot++;
        }
        if (forgot > 0) {
            REMEMBERED_COUNT.addAndGet(-forgot);
            FORGOTTEN.addAndGet(forgot);
            why.addAndGet(forgot);
        }
        if (chunk.isEmpty()) REMEMBERED.remove(Long.valueOf(chunkKey), chunk);
        return forgot;
    }

    /** A map color as 0xRRGGBB, or {@link #OWN} where there is none to give. */
    static int colorOf(MapColor color) {
        if (color == null || color == MapColor.NONE) return OWN;
        return color.col & 0xFFFFFF;
    }

    /** Every remembered color forgotten when the world asked about is not the one they were given in. */
    private static void forgetIfAnotherWorld(BlockGetter world) {
        if (world == null || rememberedIn.get() == world) return;
        forgetAll(BY_WORLD);
        // Nothing of the last world is left to keep, so a clear owed to a hello there is owed no more.
        TrmtNetwork.owesNoClear();
        rememberedIn = new WeakReference<Object>(world);
    }

    /**
     * Called by the plugin after each frame that mod draws, on the game's own thread: counted, the overlay's switch
     * followed ({@link #followTheSwitch}), and once a second the owed chunks are looked at ({@link #handAgain}) and the
     * chunks that arrived bare ({@link #settleBare}).
     */
    public static void drawn() {
        FRAMES.incrementAndGet();
        followTheSwitch();
        long now = clock.getAsLong();
        if (now - lastHandAgain < HAND_AGAIN_EVERY_MS) return;
        lastHandAgain = now;
        handAgain(now);
        settleBare(now);
    }

    /** Every square forgotten since this game started, every way, for the harness's far verdict. */
    public static int forgotten() {
        return FORGOTTEN.get();
    }

    /** Frames Distant Horizons has drawn since this game started. */
    public static int frames() {
        return FRAMES.get();
    }

    /** The plugin's counts, for one harness line. */
    public static String counts() {
        String refused = PUSH_REFUSED.get();
        return "states wrapped " + WRAPPED.get() + " (ghosts opted in " + OPTED_IN.get() + "), painted chunks handed over "
            + PUSHED.get() + (refused == null ? "" : " (first refusal: " + refused + ")") + ", ghosts put on squares "
            + PUT_ON.get() + ", squares colored from a record " + FROM_RECORD.get() + ", from memory " + FROM_MEMORY.get()
            + ", left to its own color " + LEFT_OWN.get() + ", remembered " + REMEMBERED_COUNT.get() + ", forgotten as healed "
            + (BY_CLEARED.get() + BY_RESTATED.get() + BY_BARE.get() + BY_ALL.get() + BY_HELD.get()) + " (cleared "
            + BY_CLEARED.get() + ", restated " + BY_RESTATED.get() + ", arrived bare " + BY_BARE.get() + ", all cleared "
            + BY_ALL.get() + ", held with no record " + BY_HELD.get() + "), forgotten otherwise " + (BY_WORLD.get() + BY_MOST.get())
            + " (another world " + BY_WORLD.get() + ", past the most " + BY_MOST.get() + "), handed again " + HANDED_AGAIN.get() + " (owed " + OWED.size() + ", given up " + GIVEN_UP.get()
            + "), ghost states wrapped before the plugin bound " + WRAPPED_EARLY.size()
            + (WRAP_REFUSED.get() == null ? "" : " (first wrap refused: " + WRAP_REFUSED.get() + ")") + ", overlay switched "
            + SWITCHED.get() + " (far terrain drawn again " + REDRAWN.get()
            + (REDRAW_REFUSED.get() == null ? "" : ", first refusal: " + REDRAW_REFUSED.get()) + "), colors withheld while switched off "
            + WITHHELD.get() + ", clears kept as the switch's answer " + KEPT_AS_ANSWER.get() + " (still owed "
            + TrmtNetwork.clearsOwed() + ")";
    }

    /**
     * What the overlay's switch has done, as the harness's line says it, for the far set played switched off and on again
     * (0.9.229 G17 t6): {overlay switched, far terrain drawn again, clears kept as the switch's answer, forgotten as healed}.
     */
    public static int[] switchCounts() {
        return new int[] { SWITCHED.get(), REDRAWN.get(), KEPT_AS_ANSWER.get(),
            BY_CLEARED.get() + BY_RESTATED.get() + BY_BARE.get() + BY_ALL.get() + BY_HELD.get() };
    }

    /**
     * Why no ghost has been opted in, where none has, from the counts: a ghost that mod had wrapped before the plugin
     * bound its events; no state wrapped at all, the event never firing; states wrapped and no ghost among them, that
     * mod never reading a chunk that holds one. Null where a ghost has been opted in.
     */
    public static String whyNoGhost() {
        if (OPTED_IN.get() > 0) return null;
        if (!WRAPPED_EARLY.isEmpty()) {
            return "Distant Horizons wrapped " + WRAPPED_EARLY.size() + " state(s) of this mod's ghost before the plugin bound its"
                + " events, and its API cannot wrap a state again";
        }
        if (WRAPPED.get() == 0) return "Distant Horizons fired no block-state-wrapped event at all, so the plugin's binding never ran";
        return "Distant Horizons wrapped " + WRAPPED.get() + " block state(s) and no ghost among them, so it never read a chunk"
            + " holding one";
    }

    /** Colors given from a record or from memory since this game started. */
    public static int colored() {
        return FROM_RECORD.get() + FROM_MEMORY.get();
    }

    /** Everything remembered and counted forgotten, and the plugin let go, for a test. */
    static void reset() {
        REMEMBERED.clear();
        REMEMBERED_COUNT.set(0);
        rememberedIn = new WeakReference<Object>(null);
        WRAPPED.set(0);
        OPTED_IN.set(0);
        FROM_RECORD.set(0);
        FROM_MEMORY.set(0);
        LEFT_OWN.set(0);
        FRAMES.set(0);
        PUSHED.set(0);
        PUT_ON.set(0);
        FORGOTTEN.set(0);
        BY_CLEARED.set(0);
        BY_RESTATED.set(0);
        BY_BARE.set(0);
        BY_ALL.set(0);
        BY_HELD.set(0);
        BY_WORLD.set(0);
        BY_MOST.set(0);
        BARE.clear();
        PUSH_REFUSED.set(null);
        OPTED_STATES.clear();
        WRAPPED_EARLY.clear();
        WRAP_REFUSED.set(null);
        OWED.clear();
        HANDED_AGAIN.set(0);
        GIVEN_UP.set(0);
        shownAtLastFrame = null;
        SWITCHED.set(0);
        REDRAWN.set(0);
        WITHHELD.set(0);
        REDRAW_REFUSED.set(null);
        KEPT_AS_ANSWER.set(0);
        TrmtNetwork.owesNoClear();
        lastHandAgain = 0L;
        clock = System::currentTimeMillis;
        plugin = null;
    }
}
