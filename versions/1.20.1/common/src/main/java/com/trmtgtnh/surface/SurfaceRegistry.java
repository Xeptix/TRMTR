package com.trmtgtnh.surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.data.DataTable;
import com.trmtgtnh.dataload.DataLoad;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.original.OriginalBlocks;

/**
 * Decides which blocks erode and what they erode as.
 *
 * <p>
 * The target pack has 235 mods and a great many grass-, dirt-, sand- and stone-like blocks
 * between Biomes O' Plenty, Natura, Twilight Forest, Et Futurum, Thaumcraft, GregTech and
 * the rest. Naming them all in a config would go stale the moment the pack updates, so the
 * registry walks the block registry once at post-init and classifies by what a block
 * <em>is</em> - superclass, material, and a name check to keep the material rule from
 * swallowing near-misses like farmland, sandstone and soul sand. Per-family config lists
 * then add or remove individual entries.
 *
 * <p>
 * Detection is deliberately identical on client and server. It touches no client-only API, so
 * two machines with the same config derive the same table. Two with different configs do not,
 * which is why a client connected to somebody else's server uses that server's table for the
 * visit whenever the two differ: the server sends a fingerprint with its rules, and the table
 * itself to a client that asks. See {@link #holdServerTable}.
 *
 * <p>
 * Lookups happen in the movement tick and, on the client, once per painted position, so the
 * resolved table is an immutable {@link HashMap} keyed by a packed block-id/meta int and
 * published behind a volatile reference. Readers never synchronise.
 */
public final class SurfaceRegistry {

    /**
     * The state a block is in at this metadata value, or its default when it cannot say.
     *
     * <p>
     * 1.12.2 asks a block's state rather than the block for nearly everything this class wants to
     * know, and metadata is how the 1.7.10 edition addresses a block's variants - so the two are
     * bridged here rather than at a dozen call sites. Defensively, because {@code getStateFromMeta}
     * is somebody else's method in a pack of this size and a block that cannot make sense of a
     * metadata value is one to treat as its plain self rather than one to let a sweep of the whole
     * registry die on.
     */
    // stateOf is gone with the metadata it made states from. The full-cube question it was
    // wrapped around lives in Worlds now, because SnowCover asks the same one about the same
    // nothing-in-particular and two copies of that reasoning is one too many.

    /** Meta wildcard in config entries: {@code modid:block} or {@code modid:block:*}. */
    private static final int ANY_META = -1;

    /** Resolved table, replaced wholesale on reload so concurrent readers see one state. */
    private static volatile Map<Integer, SurfaceFamily> table = Collections.emptyMap();

    /** Distinct block states that erode with stages, in a stable order, for texture generation. */
    private static volatile List<SurfaceState> texturable = Collections.emptyList();

    /** The families the table in force holds a block of: see {@link #familiesHeld}. */
    private static volatile java.util.Set<SurfaceFamily> familiesHeld = Collections.emptySet();

    /** The states a data file or a plugin brought into this side's own table ({@link SurfaceData#brought}). */
    private static volatile Set<Integer> brought = Collections.emptySet();

    /** What a data file says also pays for mending a block state ({@code repairWith}, W3 T5), by block and form. */
    private static volatile Map<Integer, String[]> repairWith = Collections.emptyMap();

    /**
     * The shapes data files give block states (W3 spec 2.4, T6), by block and form: the server's while its table is
     * held, because a shape decides where somebody stands and so must be the server's on both sides.
     */
    private static volatile Map<Integer, DataShape> shapes = Collections.emptyMap();

    /**
     * The chains data files give block states of their own (W3 T9), by block and form: the families each wears through
     * to, in order, and none for one that sinks as itself. The server's while its table is held, because a block's
     * chain decides which of its records the server keeps and what its ruts show.
     */
    private static volatile Map<Integer, SurfaceFamily[]> ownChains = Collections.emptyMap();

    /**
     * The block states data files set apart from their block ({@link StateTable}), each with its whole answer, asked
     * ahead of every table here: the server's while its table is held. Filed by the state object, because a state is the
     * one key Forge's renumbering leaves alone, and by identity, because a lookup is then one probe with the hash already
     * in the object's header: nothing boxed, nothing allocated, nothing read off the state. Built whole and never written
     * after, so any thread may read it; empty unless a file picks a block's states apart, and then each question costs
     * one volatile read and {@link Map#isEmpty}.
     */
    private static volatile Map<BlockState, StateTable.Row> stateRows = Collections.emptyMap();

    /** The blocks with a state set apart, by identity. */
    private static volatile Set<Block> setApartBlocks = Collections.emptySet();

    /** Each distinct block and family among the states set apart that wear with stages. */
    private static volatile List<SurfaceState> setApartSurfaces = Collections.emptyList();

    /**
     * Block states that wear more slowly, mapped to how much more traffic they take.
     *
     * <p>
     * Separate from the family table because resistance is a property of the particular block,
     * not of its family: a grass path wears as dirt, but takes far longer to do it than the
     * dirt beside it.
     */
    private static volatile Map<Integer, Float> resistance = Collections.emptyMap();

    /**
     * Block states that stand on a surface rather than being one, and go down when it wears.
     *
     * <p>
     * A set rather than a family, and the reason is not only that these blocks carry no wear. A
     * family is the shape of something that can be an erosion record: thresholds, a chain, a sink
     * depth, a healing rate, and an ordinal that is part of the save format. Ground cover has one
     * property, which is that it does not survive the ground under it giving way, and one property
     * is a set.
     *
     * <p>
     * The other reason is that every block in here is already in a family. Tall grass, dead bush,
     * flowers and Biomes O' Plenty's whole foliage block are VEGETATION, both by the rule above
     * and by the list {@link com.trmtgtnh.config.FamilySettings} seeds, and a block belongs to
     * exactly one family. Giving them a family of their own would take them out from under the
     * trampling switch that was written for them - and could not even do that cleanly, because
     * detection writes its findings into the family lists and never removes an entry, so the old
     * names would be read back off disk and re-added on every load.
     */
    private static volatile Set<Integer> groundCover = Collections.emptySet();

    /**
     * The ground cover that holds its square rather than coming off it.
     *
     * <p>
     * A subset of the cover set and never anything outside it, because holding and breaking are
     * the two answers to one question: what happens to this when the ground gives way. A sapling
     * is a tree somebody planted and a tuft of grass is weather, and there is no line in the block
     * registry between them - so the split is a default that can be read and edited rather than a
     * rule pretending to know.
     */
    private static volatile Set<Integer> groundCoverHolds = Collections.emptySet();

    /** The fingerprint of the table published above, and of the one this side's own config builds. */
    private static volatile long publishedFingerprint;

    private static volatile long ownFingerprint;

    /**
     * A server's table in use for the visit, or null when this side's own is.
     *
     * <p>
     * Held rather than written over the published fields and forgotten, for the reason a server's
     * geometry is held: every path that rebuilds the table - a reload, a family changing hands, the
     * config screen - builds this side's own out of its own file, and would put it straight back
     * mid-visit. While this is set, a rebuild publishes the held table instead and keeps its own only
     * as a fingerprint, so the client can still tell when the server has come round to matching it.
     * Only ever set on a client connected to somebody else's server; a single-player or LAN host
     * shares its server's table outright.
     */
    private static volatile Held held;

    /** The published table as the wire carries it, and the fingerprint it was built for. Server side. */
    private static volatile byte[] encoded;

    private static volatile long encodedFor;

    /** A server's table as it arrived, with the two facts about it that travel beside the sets. */
    private static final class Held {

        final Map<Integer, SurfaceFamily> table;

        final Set<Integer> cover;

        final Set<Integer> holds;

        final Map<Integer, DataShape> shapes;

        final Map<Integer, SurfaceFamily[]> chains;

        final Map<BlockState, StateTable.Row> rows;

        final boolean holdsSwitch;

        final long fingerprint;

        Held(Map<Integer, SurfaceFamily> table, Set<Integer> cover, Set<Integer> holds, Map<Integer, DataShape> shapes,
            Map<Integer, SurfaceFamily[]> chains, Map<BlockState, StateTable.Row> rows, boolean holdsSwitch,
            long fingerprint) {
            this.table = table;
            this.cover = cover;
            this.holds = holds;
            this.shapes = shapes;
            this.chains = chains;
            this.rows = rows;
            this.holdsSwitch = holdsSwitch;
            this.fingerprint = fingerprint;
        }
    }

    private SurfaceRegistry() {}

    /**
     * The table's key: the block's own registry id.
     *
     * <p>
     * Both older editions pack a metadata value into the low four bits, because there a block could
     * be several materials at once. 1.13 split those apart into separate blocks, so the block id is
     * the whole answer here and the shift is gone.
     */
    private static int key(Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(block);
    }

    /**
     * The family a block at this metadata belongs to, or null when it does not erode. Hot
     * path - called per movement sample and per painted position.
     */
    public static SurfaceFamily familyOf(BlockState state) {
        if (state == null) return null;
        StateTable.Row row = rowOf(state);
        if (row != null) return row.family;
        Map<Integer, SurfaceFamily> current = table;
        if (current.isEmpty()) return null;
        int id = key(state.getBlock());
        if (id < 0) return null;
        return current.get(Integer.valueOf(formed(id, formOf(state))));
    }

    /** A state's own row, or null for every state no data file sets apart. See {@link #stateRows}. */
    private static StateTable.Row rowOf(BlockState state) {
        Map<BlockState, StateTable.Row> current = stateRows;
        return current.isEmpty() ? null : current.get(state);
    }

    /** Whether a data file sets any state of this block apart from the rest of it. */
    public static boolean hasStatesApart(Block block) {
        return setApartBlocks.contains(block);
    }

    /** How many of this block's states a data file sets apart. */
    public static int statesApart(Block block) {
        int count = 0;
        for (BlockState state : stateRows.keySet()) if (state.getBlock() == block) count++;
        return count;
    }

    /** Each distinct block and family among the states set apart that wear with stages. */
    public static List<SurfaceState> setApartSurfaces() {
        return setApartSurfaces;
    }

    /** The single form of a block, and every block that is not a slab. See {@link #formed}. */
    static final int SINGLE = 0;

    /** A slab's doubled form. See {@link #formed}. */
    static final int DOUBLED = 1;

    /**
     * A block's key in the published table with its form in the low four bits - the older editions' id and
     * metadata layout, its spare nibble holding whether a slab is doubled (0.9.223, spec SD20). The 1.7.10 edition's
     * doubled slab is a block of its own and wears with its family whatever the family's slabs switch says; here it
     * is a state of the slab block, so the table tells the two forms apart or it cannot say that.
     */
    static int formed(int id, int form) {
        return (id << 4) | form;
    }

    /** Which form of its block a state is: a doubled slab, or anything else. */
    static int formOf(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE)
            && state.getValue(SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE
                ? DOUBLED
                : SINGLE;
    }

    /** The same, for a caller that genuinely has only the block. */
    public static SurfaceFamily familyOf(Block block) {
        if (block == null) return null;
        Map<Integer, SurfaceFamily> current = table;
        if (current.isEmpty()) return null;
        int id = key(block);
        if (id < 0) return null;
        return current.get(Integer.valueOf(formed(id, SINGLE)));
    }

    /**
     * The table's family for one form of a block, {@link #SINGLE} or a slab's {@link #DOUBLED}, by block and form as
     * the table keeps it, the states a data file sets apart aside. What the census writes of each form beside the facts
     * that led there (W4 spec 3.1), as the older editions' census reads a block at each metadata value.
     */
    public static SurfaceFamily familyOf(Block block, int form) {
        if (block == null) return null;
        Map<Integer, SurfaceFamily> current = table;
        if (current.isEmpty()) return null;
        int id = key(block);
        if (id < 0) return null;
        return current.get(Integer.valueOf(formed(id, form)));
    }

    /** A state's key in the tables kept by block and form, or -1 for no state or an unregistered block. */
    private static int formedKey(BlockState state) {
        if (state == null) return -1;
        int id = key(state.getBlock());
        if (id < 0) return -1;
        return formed(id, formOf(state));
    }

    /**
     * Whether a data file or a plugin brought this block state into the family table rather than the mod's own
     * detection and the player's lists ({@link SurfaceData#brought}). The quick demos lay none of these.
     */
    public static boolean broughtByData(BlockState state) {
        Set<Integer> current = brought;
        if (current.isEmpty()) return false;
        int at = formedKey(state);
        return at >= 0 && current.contains(Integer.valueOf(at));
    }

    /**
     * The shape a data file gives this block state, or null where none does and its class decides
     * ({@code SurfaceShape.of}). On the collision path, so one map read and no more. Kept by block and form, with the
     * states a file sets apart ahead of them; what a data slab or stair needs of the state itself (its half, its facing)
     * is read from the live state by its caller.
     */
    public static DataShape shapeOf(BlockState state) {
        StateTable.Row row = rowOf(state);
        if (row != null) return row.shape;
        Map<Integer, DataShape> current = shapes;
        if (current.isEmpty()) return null;
        int at = formedKey(state);
        return at < 0 ? null : current.get(Integer.valueOf(at));
    }

    /**
     * The block's own list a data file gives it (W3 T9), the families it wears through to in order, or null where it
     * follows its family's chain. An empty list sinks as itself. Not to be written to.
     */
    public static SurfaceFamily[] throughOf(BlockState state) {
        StateTable.Row row = rowOf(state);
        if (row != null) return row.chain;
        Map<Integer, SurfaceFamily[]> current = ownChains;
        if (current.isEmpty()) return null;
        int at = formedKey(state);
        return at < 0 ? null : current.get(Integer.valueOf(at));
    }

    /**
     * The chain the ground of this block state follows: its family's, or the block's own where a data file gives it one
     * (W3 T9; {@link ErosionChain#of(SurfaceFamily, SurfaceFamily[])}). Every question about where a record sits on its
     * chain is asked of this, so a block with a list of its own is worn, healed and placed along it. A chain with no
     * steps where the block does not erode.
     */
    public static ErosionChain.Chain chainOf(BlockState state) {
        SurfaceFamily family = familyOf(state);
        if (family == null) return ErosionChain.of(null);
        return ErosionChain.of(family, throughOf(state));
    }

    /**
     * What else a data file says pays for mending this block state ({@code repairWith}), in the config lists'
     * spelling, besides what its family's repairBlocks names. Empty for most blocks. Server side; read by
     * {@code HealingCost}, which bone meal, the chunk tamper and the golem all pay through.
     */
    public static String[] repairWithOf(BlockState state) {
        StateTable.Row row = rowOf(state);
        if (row != null) return row.repairWith;
        Map<Integer, String[]> current = repairWith;
        if (current.isEmpty() || state == null) return new String[0];
        int id = key(state.getBlock());
        if (id < 0) return new String[0];
        return SurfaceData.repairWith(current, id, formOf(state));
    }

    /** True when this block state erodes into stages the client can paint. */
    public static boolean isStaged(BlockState state) {
        SurfaceFamily family = familyOf(state);
        return family != null && family.staged;
    }

    /** Distinct erodable block states, used to decide which wear textures to generate. */
    /**
     * A fingerprint of what currently erodes, folded to an int, for telling whether a reload moved
     * anything.
     *
     * <p>
     * Comparing this beats comparing the settings that produced it: switching auto-detection off
     * changes a boolean but often not a single entry, because the lists detection wrote on earlier
     * runs are still sitting in the file. Folded from the published fingerprint rather than summed
     * over the table as it used to be, because a sum could not see two blocks swapping families -
     * which is exactly the edit the family editor makes.
     */
    public static int tableSignature() {
        long fingerprint = publishedFingerprint;
        return (int) (fingerprint ^ (fingerprint >>> 32));
    }

    /**
     * The fingerprint of the table in use, which is what a server sends with its rules.
     *
     * <p>
     * Covers the family table, both ground-cover sets and the switch that decides whether planted
     * cover holds its square, because every one of them changes what a client draws and where it
     * stands. See {@link SurfaceTableCodec#fingerprint}.
     */
    public static long fingerprint() {
        return publishedFingerprint;
    }

    /** The fingerprint of the table this side's own config builds, whichever table is in use. */
    public static long ownFingerprint() {
        return ownFingerprint;
    }

    /** Whether a server's table is in use on this client. */
    public static boolean holdingServerTable() {
        return held != null;
    }

    /** The fingerprint of the server's table in use, or nought when none is. */
    public static long heldFingerprint() {
        Held visiting = held;
        return visiting == null ? 0L : visiting.fingerprint;
    }

    /**
     * Whether planted ground cover holds its square: the server's answer while its table is in use.
     *
     * <p>
     * It travels with the table because it decides the same thing the holder set does. A client that
     * answered it from its own file drew a square flat, or sunk, under a plant the server was treating
     * the other way, and collided with ground the server did not agree was there.
     */
    public static boolean groundCoverHoldsOn() {
        Held visiting = held;
        return visiting != null ? visiting.holdsSwitch : TrmtConfig.groundCoverHolds;
    }

    /**
     * The table in use, compressed for the wire. Built once for each fingerprint. Server thread.
     */
    public static byte[] encodedTable() {
        long wanted = publishedFingerprint;
        byte[] cached = encoded;
        if (cached != null && encodedFor == wanted) return cached;
        // The blocks' own chains beside the codec's table (W3 T9), so a client draws each block on the server's chain.
        byte[] made = ChainTable.wrap(
            SurfaceTableCodec.encode(ordinals(table), groundCover, groundCoverHolds, shapes),
            chainOrdinals(ownChains));
        // The states data files set apart after it, read off their objects now, in the numbering this server sends.
        made = StateTable.wrap(made, rowsByStateId(stateRows));
        encoded = made;
        encodedFor = wanted;
        return made;
    }

    /**
     * Puts a server's table in use for the visit. Client thread.
     *
     * @return false, holding nothing, when the table names a family this build does not have
     */
    public static boolean holdServerTable(SurfaceTableCodec.Table decoded, boolean holdsSwitch, long fingerprint,
        boolean keysCarryForm) {
        return holdServerTable(decoded, Collections.<Integer, int[]>emptyMap(), holdsSwitch, fingerprint, keysCarryForm);
    }

    /**
     * The same, with the blocks' own chains the server sent beside its table ({@link ChainTable}, W3 T9) and the states
     * its data files set apart ({@link StateTable}), by state id, the wire's own numbering. Client thread.
     *
     * @return false, holding nothing, when the table or a chain names a family this build does not have
     */
    public static boolean holdServerTable(SurfaceTableCodec.Table decoded, Map<Integer, int[]> chains,
        Map<Integer, StateTable.Row> rows, boolean holdsSwitch, long fingerprint, boolean keysCarryForm) {
        SurfaceFamily[] all = SurfaceFamily.values();
        Map<Integer, SurfaceFamily> families = new HashMap<Integer, SurfaceFamily>(decoded.families.size() * 2);
        for (Map.Entry<Integer, Integer> entry : decoded.families.entrySet()) {
            int ordinal = entry.getValue()
                .intValue();
            if (ordinal < 0 || ordinal >= all.length) return false;
            families.put(entry.getKey(), all[ordinal]);
        }
        // A table from a server older than 0.9.223 keys a block by its id alone and means both its forms; read
        // as that build meant it (spec SD20).
        if (!keysCarryForm) families = withForms(families, Collections.<Integer, SurfaceFamily>emptyMap());
        // The blocks' own chains (W3 T9), each list held by ChainTable to families this build has with stages.
        Map<Integer, SurfaceFamily[]> chained = new HashMap<Integer, SurfaceFamily[]>(chains.size() * 2);
        for (Map.Entry<Integer, int[]> entry : chains.entrySet()) {
            int[] ordinals = entry.getValue();
            SurfaceFamily[] list = new SurfaceFamily[ordinals.length];
            for (int i = 0; i < ordinals.length; i++) {
                if (ordinals[i] < 0 || ordinals[i] >= all.length) return false;
                list[i] = all[ordinals[i]];
            }
            chained.put(entry.getKey(), list);
        }
        Held visiting = new Held(
            Collections.unmodifiableMap(families),
            decoded.cover,
            decoded.holds,
            decoded.shapes,
            Collections.unmodifiableMap(chained),
            rowsByState(rows),
            holdsSwitch,
            fingerprint);
        held = visiting;
        publish(visiting.table, visiting.cover, visiting.holds, visiting.shapes, visiting.chains, visiting.rows);
        return true;
    }

    /** The same, with no states set apart. */
    public static boolean holdServerTable(SurfaceTableCodec.Table decoded, Map<Integer, int[]> chains,
        boolean holdsSwitch, long fingerprint, boolean keysCarryForm) {
        return holdServerTable(
            decoded,
            chains,
            Collections.<Integer, StateTable.Row>emptyMap(),
            holdsSwitch,
            fingerprint,
            keysCarryForm);
    }

    /**
     * Stops using a server's table. The caller rebuilds this side's own.
     *
     * @return whether one was in use
     */
    public static boolean releaseServerTable() {
        boolean was = held != null;
        held = null;
        return was;
    }

    /** Writes a table into the published fields, with the fingerprint that belongs to it. */
    private static void publish(Map<Integer, SurfaceFamily> families, Set<Integer> cover, Set<Integer> holds,
        Map<Integer, DataShape> shaped, Map<Integer, SurfaceFamily[]> chained, Map<BlockState, StateTable.Row> rows) {
        table = families;
        shapes = shaped;
        ownChains = chained;
        stateRows = rows;
        // The blocks' own lists, by the family each block wears as, so the chains know what this ground may show.
        Map<SurfaceFamily, List<SurfaceFamily[]>> byFamily = new EnumMap<SurfaceFamily, List<SurfaceFamily[]>>(
            SurfaceFamily.class);
        Set<String> seen = new HashSet<String>();
        for (Map.Entry<Integer, SurfaceFamily[]> each : chained.entrySet()) {
            SurfaceFamily family = families.get(each.getKey());
            if (family == null) continue;
            if (!seen.add(family.key() + java.util.Arrays.toString(each.getValue()))) continue;
            List<SurfaceFamily[]> into = byFamily.get(family);
            if (into == null) {
                into = new ArrayList<SurfaceFamily[]>();
                byFamily.put(family, into);
            }
            into.add(each.getValue());
        }
        // The states set apart: their chains with the rest, and each block and family among them, which the pictures
        // and the sinkable set are planned for as a block's own family is.
        Set<Block> apart = Collections.newSetFromMap(new java.util.IdentityHashMap<Block, Boolean>());
        List<SurfaceState> surfacesApart = new ArrayList<SurfaceState>();
        Set<String> surfacesSeen = new HashSet<String>();
        for (Map.Entry<BlockState, StateTable.Row> each : rows.entrySet()) {
            Block block = each.getKey()
                .getBlock();
            apart.add(block);
            StateTable.Row row = each.getValue();
            if (row.family == null || !row.family.staged) continue;
            String name = registryName(block);
            if (name != null && surfacesSeen.add(name + " " + row.family.key())) {
                surfacesApart.add(new SurfaceState(block, name, row.family));
            }
            if (row.chain == null || !seen.add(row.family.key() + java.util.Arrays.toString(row.chain))) continue;
            List<SurfaceFamily[]> into = byFamily.get(row.family);
            if (into == null) {
                into = new ArrayList<SurfaceFamily[]>();
                byFamily.put(row.family, into);
            }
            into.add(row.chain);
        }
        Collections.sort(surfacesApart);
        setApartBlocks = apart;
        setApartSurfaces = Collections.unmodifiableList(surfacesApart);
        ErosionChain.ownLists(byFamily);
        texturable = collectTexturableStates(families, surfacesApart);
        java.util.Set<SurfaceFamily> holding = java.util.EnumSet.noneOf(SurfaceFamily.class);
        holding.addAll(families.values());
        // And a family a file gives some states alone, which wears there as any other (Codex's review c1130).
        for (StateTable.Row row : rows.values()) {
            if (row.family != null) holding.add(row.family);
        }
        familiesHeld = java.util.Collections.unmodifiableSet(holding);
        groundCover = cover;
        groundCoverHolds = holds;
        Held visiting = held;
        publishedFingerprint = visiting != null ? visiting.fingerprint : ownFingerprint;
    }

    /** The rows by the state objects they answer for; an id this game has no state at is passed over. */
    private static Map<BlockState, StateTable.Row> rowsByState(Map<Integer, StateTable.Row> rows) {
        if (rows.isEmpty()) return Collections.emptyMap();
        Map<BlockState, StateTable.Row> out = new java.util.IdentityHashMap<BlockState, StateTable.Row>(rows.size() * 2);
        for (Map.Entry<Integer, StateTable.Row> each : rows.entrySet()) {
            int id = each.getKey()
                .intValue();
            BlockState state = Block.stateById(id);
            if (state != null && Block.getId(state) == id) out.put(state, each.getValue());
        }
        return out;
    }

    /** The rows by state id, in the numbering in force now. */
    private static Map<Integer, StateTable.Row> rowsByStateId(Map<BlockState, StateTable.Row> rows) {
        Map<Integer, StateTable.Row> out = new HashMap<Integer, StateTable.Row>(rows.size() * 2);
        for (Map.Entry<BlockState, StateTable.Row> each : rows.entrySet()) {
            out.put(Integer.valueOf(Block.getId(each.getKey())), each.getValue());
        }
        return out;
    }

    /** The blocks' own lists as the codec carries them: family ordinals in order. */
    private static Map<Integer, int[]> chainOrdinals(Map<Integer, SurfaceFamily[]> chained) {
        Map<Integer, int[]> out = new HashMap<Integer, int[]>(chained.size() * 2);
        for (Map.Entry<Integer, SurfaceFamily[]> entry : chained.entrySet()) {
            SurfaceFamily[] list = entry.getValue();
            int[] ordinals = new int[list.length];
            for (int i = 0; i < list.length; i++) ordinals[i] = list[i].ordinal();
            out.put(entry.getKey(), ordinals);
        }
        return out;
    }

    private static Map<Integer, Integer> ordinals(Map<Integer, SurfaceFamily> families) {
        Map<Integer, Integer> out = new HashMap<Integer, Integer>(families.size() * 2);
        for (Map.Entry<Integer, SurfaceFamily> entry : families.entrySet()) {
            out.put(
                entry.getKey(),
                Integer.valueOf(
                    entry.getValue()
                        .ordinal()));
        }
        return out;
    }

    public static List<SurfaceState> texturableStates() {
        return texturable;
    }

    /**
     * The families the table in force holds at least one block of. The settings screen's Advanced fold gives a family
     * rows only when it is here (0.9.229, W4 T17; Xep, 2026-11-09: "Rows only for held families"), so flesh and taint,
     * which only other mods' blocks wear as, never lengthen a vanilla player's fold.
     */
    public static java.util.Set<SurfaceFamily> familiesHeld() {
        return familiesHeld;
    }

    /**
     * Rebuilds the table from the current block registry and config. Safe to call again at
     * runtime - {@code /trmt reload} does exactly that.
     */
    public static void resolve() {
        Map<Integer, SurfaceFamily> found = new HashMap<Integer, SurfaceFamily>();
        // Each slab block's doubled form, by block id, with the family it wears as whatever the slabs switch says.
        Map<Integer, SurfaceFamily> doubled = new HashMap<Integer, SurfaceFamily>();
        Map<SurfaceFamily, Set<String>> detected = new EnumMap<SurfaceFamily, Set<String>>(SurfaceFamily.class);
        final Set<String> excluded = parseNameSet(TrmtConfig.surfaceExclude);

        // The data files' families, cover, resistance and repair (W3 spec 2.2, T5), read once from the one table
        // the last data read published, so this rebuild sees one read and never half of the next.
        DataTable dataTable = DataLoad.table();
        SurfaceData.Read data = SurfaceData.read(dataTable, dataNames(excluded));
        // And the states a selector takes apart from the rest of their block (StateTable).
        SurfaceData.SetApart apart = SurfaceData.setApart(dataTable, dataNames(excluded));
        // Before any list is read: the lines an earlier title screen wrote for leaves-tag blocks, taken back once.
        mendLeavesTagLines(excluded, data.family);

        if (TrmtConfig.surfaceAutoDetect) {
            autoDetect(found, excluded, detected, doubled, data.family);
        }

        // The player's lists, in family order as they always were applied, kept apart from detection so the files
        // sit between the two: data beats detection, and the player beats data (SurfaceData.families). Merged by
        // block and form, since that is what a file decides.
        Map<Integer, SurfaceFamily> listed = new HashMap<Integer, SurfaceFamily>();
        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings settings = TrmtConfig.family(family);
            if (settings == null) continue;
            applyList(listed, excluded, settings.extra, family);
        }
        Map<Integer, SurfaceFamily> foundFormed = withForms(found, doubled);
        Map<Integer, SurfaceFamily> listedFormed = withForms(listed, Collections.<Integer, SurfaceFamily>emptyMap());

        Map<Integer, SurfaceFamily> formed = SurfaceData.families(foundFormed, listedFormed, data.family);

        dropDisabledFamilies(formed);
        // What the files brought, measured against this side's table with only the mod's own shipped files; kept apart
        // from what is published, so a server's table held for a visit changes nothing here.
        brought = Collections.unmodifiableSet(
            SurfaceData.brought(
                formed,
                foundFormed,
                listedFormed,
                data,
                blockId -> {
                    String name = registryName(net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(blockId));
                    return name != null && name.startsWith("minecraft:");
                }));
        // The table by block, for the per-block sets below, as it always was: each block's single form.
        Map<Integer, SurfaceFamily> built = new HashMap<Integer, SurfaceFamily>();
        for (Map.Entry<Integer, SurfaceFamily> each : formed.entrySet()) {
            int packed = each.getKey()
                .intValue();
            if ((packed & 0xF) == SINGLE) built.put(Integer.valueOf(packed >> 4), each.getValue());
        }

        Map<Integer, Float> listedResistance = new HashMap<Integer, Float>();
        resistance = collectResistance(excluded, SurfaceData.byBlock(data.resistance), listedResistance);
        repairWith = data.repairWith;

        Map<Integer, String> coverData = SurfaceData.byBlock(data.cover);
        Set<String> cover = new java.util.TreeSet<String>();
        Set<Integer> ownCover = new HashSet<Integer>();
        Set<Integer> builtCover = collectGroundCover(built, cover, coverData, ownCover);
        Set<String> holds = new java.util.TreeSet<String>();
        Set<Integer> planted = new HashSet<Integer>();
        Set<Integer> listedHolds = new HashSet<Integer>();
        Set<Integer> builtHolds = collectHolders(builtCover, holds, coverData, planted, listedHolds);
        // The shapes the files give (W3 spec 2.4, T6), resolved for the blocks this game has; sent in the table, so a
        // client that has other shape data, or none, stands where the server says.
        Map<Integer, DataShape> builtShapes = Collections
            .unmodifiableMap(SurfaceData.shapes(dataTable, dataNames(excluded)));
        // The blocks' own chains (W3 T9): a file's wearsThroughTo, on the states that wear as the family it was written
        // for. Sent in the table, so a client draws each block on the server's chain.
        Map<Integer, SurfaceFamily[]> builtChains = Collections
            .unmodifiableMap(SurfaceData.chains(formed, foundFormed, data));
        // The states set apart, each held to the rules just applied to its block: detection and the player's lists as
        // they stand for its form, the file's facts for the state itself.
        Map<Integer, SurfaceFamily> foundApart = apart.spread(foundFormed, true);
        Map<Integer, SurfaceFamily> familiesApart = SurfaceData
            .families(foundApart, apart.spread(listedFormed, true), apart.read.family);
        dropDisabledFamilies(familiesApart);
        Set<Integer> coverApart = SurfaceData.cover(
            apart.spread(ownCover),
            keptFromPlayer(apart.read.cover, parseNameSet(TrmtConfig.groundCoverExclude), apart));
        for (Iterator<Integer> it = coverApart.iterator(); it.hasNext();) {
            SurfaceFamily family = familiesApart.get(it.next());
            if (family != null && family.staged) it.remove();
        }
        Map<Integer, StateTable.Row> builtRows = SurfaceData.rows(
            apart,
            familiesApart,
            coverApart,
            SurfaceData.holds(
                coverApart,
                apart.spread(planted),
                apart.spread(listedHolds),
                keptFromPlayer(apart.read.cover, parseNameSet(TrmtConfig.groundCoverHoldsExclude), apart)),
            SurfaceData.resistance(apart.read.resistance, apart.spread(listedResistance, false)),
            SurfaceData.chains(familiesApart, foundApart, apart.read));
        // The server's data folded in (W3 spec 2.2): a client whose mods or packs give it other families, shapes or
        // wearers uses the server's table for the visit. No data leaves the fingerprint as it always was.
        ownFingerprint = SurfaceData.withData(
            StateTable.fingerprint(
                ChainTable.fingerprint(
                    SurfaceTableCodec
                        .fingerprint(ordinals(formed), builtCover, builtHolds, TrmtConfig.groundCoverHolds, builtShapes),
                    chainOrdinals(builtChains)),
                builtRows),
            dataTable.fingerprint());

        // A server's table in use for the visit is published again rather than this side's own, so no
        // rebuild made while connected - a reload, the config screen, a family changing hands - can
        // quietly put back a table the server has already said is not the one in use. Detection still
        // runs and is still written down below; only what is published waits for the visit to end.
        Held visiting = held;
        if (visiting != null) {
            publish(visiting.table, visiting.cover, visiting.holds, visiting.shapes, visiting.chains, visiting.rows);
        } else {
            publish(Collections.unmodifiableMap(formed), builtCover, builtHolds, builtShapes, builtChains, rowsByState(builtRows));
        }

        // Detection is only useful if you can see and override what it decided, so what it
        // found is written back into the per-family block lists. Turn autoDetect off and those
        // lists become the whole story, editable by hand. Detection's findings only: what the data
        // files say is never written here, so removing a mod leaves nothing of it in the config.
        // And only once the block tags are bound: see tagsBound.
        if (TrmtConfig.writeDetectedSurfaces && tagsBound()) {
            TrmtConfig.recordDetectedSurfaces(detected);
            TrmtConfig.recordDetectedGroundCover(cover);
            TrmtConfig.recordDetectedHolders(holds);
        }

        Trmt.LOG.info(
            "Resolved {} erodable block states, {} of them with generated wear textures, and {} that stand on them",
            Integer.valueOf(built.size()),
            Integer.valueOf(texturable.size()),
            Integer.valueOf(groundCover.size()));

        // And which of them can ever sink, which the collision path reads as an identity lookup and
        // cannot work out for itself. Here rather than in the settings layer because this is the
        // moment the answer changes: the set is built from the surfaces that were just resolved, and
        // a reload resolves them again.
        com.trmtgtnh.erosion.PhysicalDecay.markSinkableBlocks();
    }

    /**
     * Whether the block tags are bound: whether any block in the registry holds any tag, which one does once a world's
     * data has been read and none does before.
     *
     * <p>
     * Asked of the registry's own holders because no loader has one signal for it that every edition and loader here
     * hears at the right moment, and the holders are what the tag reads themselves ask. A data load binds every tag in
     * one go, {@code minecraft:leaves} with the rest, so a block in any tag means the leaves tag is bound too; and no one
     * block is asked, so a pack that takes any block (oak leaves, say) out of every tag still reads as bound. The game's
     * own blocks are in dozens of tags once its data is read, so the walk stops at the first block or two; with nothing
     * bound it walks the whole registry once per resolve. From 26.1 a holder whose tags are not bound throws rather than
     * holding none ({@code MaterialKind.inLeavesTag}), which says the same of that holder ({@code TagsBoundTest}).
     * The assumption is the game's own bind, all tags at once: a mod that bound some tag of its own to a block before
     * the world's data was read would make this say bound early (Codex review, round 2). None is known to.
     *
     * <p>
     * Detection reads one tag, {@code minecraft:leaves} ({@code MaterialKind}), and a data file may name a block by a
     * tag. The block atlas is stitched at the title screen, before any world's data has bound a tag, and the table is
     * first resolved there: every block a mod puts in the leaves tag without extending {@code LeavesBlock} reads as what
     * it is built as, most of them the plant they hang as. Written into the lists then, that answer is read back at the
     * server's start as the player's line, which beats detection (spec 1.5), so the table kept it for good while the
     * census, which reads the same block with the tags bound, replayed it as leaves: Prominence's seven, Better End's
     * furs and outer leaves and Regions Unexplored's Joshua leaves and Spanish moss (G17 W4, 2026-11-11). A dedicated
     * server, which has no title screen, said leaves all along. So a table resolved before then is used as it stands and
     * written nowhere; the next one, at the server's start or a reload, is the one detection records.
     */
    static boolean tagsBound() {
        for (Object candidate : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (!(candidate instanceof Block)) continue;
            try {
                if (((Block) candidate).builtInRegistryHolder()
                    .tags()
                    .findAny()
                    .isPresent()) return true;
            } catch (IllegalStateException notBound) {
                // This holder's tags are not bound yet; another's may be.
            }
        }
        return false;
    }

    /**
     * Takes back, once a config, the lines builds before this one wrote at the title screen for blocks a mod puts in the
     * leaves tag (Codex review of G17 W4, 2026-11-11). Stopping the writes ({@link #tagsBound}) leaves every config that
     * already ran holding them: Better End's furs and Regions Unexplored's Spanish moss in the vegetation list, and in
     * ground cover where they grew on the ground, each read back as the player's line and beating detection for good.
     *
     * <p>
     * <strong>A config cannot tell a line detection wrote from one a player wrote</strong>: both are names in one list,
     * and the merge tells them apart only by whether they agree with detection now (spec 1.5). So the narrowest rule that
     * reaches the fault's lines: a block that is not a {@code LeavesBlock} and that detection, with the tags bound, makes
     * leaves, which it can only do by the leaves tag ({@code MaterialKind}), and which a fresh title screen therefore
     * made something else, loses its bare-name line in the vegetation list and in the ground-cover list, the two the
     * title screen's plant answer writes into. A seeded name is never taken, nor a line in any other list, nor a block
     * detection never reaches (excluded, this mod's own, the original's). Run once, with the tags bound, and the config
     * says so ({@code surfaces.leavesTagLinesMended}), so a player who puts a line back after it keeps it; each name taken
     * is in the log. Before the leaves family's own line is written, so the same resolve records it there. Only while
     * detection runs, writes what it finds and detects the leaves family, so a line taken is a line detection writes
     * back as leaves; otherwise nothing is taken and the mend is not spent. The ground-cover line only while cover
     * detection runs, for the same reason.
     */
    static void mendLeavesTagLines(Set<String> excluded, Map<Integer, String> decidedByData) {
        if (TrmtConfig.leavesTagLinesMended) return;
        // Only where detection would put its own line back (Codex review, round 2): detection on, writing what it
        // finds, and the leaves family switched on and detected. Otherwise the lists are kept by hand, nothing
        // taken would come back, and the mend is not spent, so it runs once detection is on again.
        if (!TrmtConfig.surfaceAutoDetect) return;
        if (!TrmtConfig.writeDetectedSurfaces) return;
        FamilySettings leaves = TrmtConfig.family(SurfaceFamily.LEAVES);
        if (leaves == null || !leaves.autoDetect) return;
        if (!leaves.enabled) return;
        if (!tagsBound()) return;
        Set<String> leavesByTag = new java.util.TreeSet<String>();
        for (Object candidate : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (!(candidate instanceof Block)) continue;
            Block block = (Block) candidate;
            if (block == Blocks.AIR || block instanceof net.minecraft.world.level.block.LeavesBlock) continue;
            String name = registryName(block);
            if (name == null || excluded.contains(name.toLowerCase(Locale.ROOT))) continue;
            if (name.startsWith(Trmt.MODID + ":") || OriginalBlocks.standsAside(name)) continue;
            // Not a leaves block in code, so leaves by its material alone, which is the leaves tag (MaterialKind), read
            // as detection reads it: on 26.x a holder whose tags are not bound is in no tag rather than a throw.
            BlockFacts facts = BlockFacts.of(block, name);
            SurfaceFamily whole = Detection.classify(facts);
            if (whole != SurfaceFamily.LEAVES) continue;
            // And leaves as autoDetect takes it, its shape let in, and not a block a data file decides, which detection
            // never writes down and whose line is no fault's.
            if (gateShape(facts, Detection.refineSlab(facts, whole, 0)) != SurfaceFamily.LEAVES) continue;
            int id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(block);
            if (id < 0) continue;
            if (SurfaceData.decidedAnywhere(decidedByData, id)) continue;
            leavesByTag.add(name);
        }
        TrmtConfig.mendLeavesTagLines(leavesByTag, TrmtConfig.groundCoverAutoDetect);
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    private static void autoDetect(Map<Integer, SurfaceFamily> out, Set<String> excluded,
        Map<SurfaceFamily, Set<String>> detected, Map<Integer, SurfaceFamily> doubled,
        Map<Integer, String> decidedByData) {
        // Iterated as Object because Forge's registry is generic while vanilla's is raw;
        // this compiles against either without a cast warning at the call site.
        for (Object candidate : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (!(candidate instanceof Block)) continue;
            Block block = (Block) candidate;
            if (block == Blocks.AIR) continue;

            String name = registryName(block);
            if (name == null || excluded.contains(name.toLowerCase(Locale.ROOT))) continue;
            // Our own ghost blocks are grass-material opaque cubes with "grass" in the name, so
            // detection would happily classify them as terrain and start generating wear
            // textures for the things that draw wear textures.
            if (name.startsWith(Trmt.MODID + ":")) continue;
            // The original TRMT's eroded blocks copy grass, dirt and sand, so detection would claim them and paint
            // over that mod's wear; its namespace is never a surface here, loaded or not (D60, W5 spec 2.11).
            if (OriginalBlocks.standsAside(name)) continue;

            BlockFacts facts = BlockFacts.of(block, name);
            SurfaceFamily whole = Detection.classify(facts);
            if (whole == null) continue;

            int id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(block);
            if (id < 0) continue;

            // A doubled slab is a whole block of its family whatever the slabs switch says, as the 1.7.10
            // edition's doubled slab block is (0.9.223, spec SD20): its family, ungated by shape.
            if (block instanceof SlabBlock) {
                SurfaceFamily asDoubled = Detection.refineSlab(facts, whole, 0);
                FamilySettings doubledSettings = asDoubled == null ? null : TrmtConfig.family(asDoubled);
                if (doubledSettings != null && doubledSettings.autoDetect) doubled.put(Integer.valueOf(id), asDoubled);
            }

            // Detection cannot tell which metadata values of an ordinary block are the erodable
            // variant, so it claims all sixteen and the exclude list carves out specifics. A slab
            // is the exception, because a slab block genuinely is several materials wearing one
            // name and will say per value which is which.
            SurfaceFamily[] byMeta = new SurfaceFamily[16];
            boolean split = false;
            for (int meta = 0; meta < 16; meta++) {
                byMeta[meta] = gateShape(facts, Detection.refineSlab(facts, whole, meta));
                if (byMeta[meta] != byMeta[0]) split = true;
            }

            for (int meta = 0; meta < 16; meta++) {
                SurfaceFamily family = byMeta[meta];
                if (family == null) continue;

                FamilySettings settings = TrmtConfig.family(family);
                if (settings == null || !settings.autoDetect) continue;

                out.put(Integer.valueOf(id), family);

                // A block a data file decides is not written into the lists: it would read back as the player's
                // line, and detection's own record would overrule the file that beat it.
                if (SurfaceData.decidedAnywhere(decidedByData, id)) continue;

                Set<String> names = detected.get(family);
                if (names == null) {
                    names = new java.util.TreeSet<String>();
                    detected.put(family, names);
                }
                // A block whose values disagree is written back one value at a time. Recorded as a
                // bare name it would be read back as all sixteen of one family on the next load,
                // which would undo the split the moment detection wrote what it found.
                names.add(split ? name + ":" + meta : name);
            }
        }
    }

    /**
     * True for the one block that is never ground, however it is named or listed: frosted ice, which is
     * ice by material, laid by Frost Walker and melted again by the game within seconds. Counted, it put
     * a platform of it in every demonstrate yard, which melted and poured off the edge - a waterfall the
     * 1.7.10 yard does not have, because that edition has no such block. Asked by detection and by the
     * family lists alike, since the lists keep whatever detection once recorded in them.
     */
    static boolean neverGround(Block block) {
        return block instanceof FrostedIceBlock;
    }

    /**
     * True for anything that keeps state of its own at the position.
     *
     * <p>
     * A ghost stands in for the block in the client's copy of the world, and a tile entity does
     * not come with it: right-click a worn furnace and the screen that opens is talking to a
     * block the client no longer believes is there, which crashes on the spot. Natura's nether
     * furnace is the one that found this - a full opaque cube, on rock, with "nether" in its
     * name, which is precisely the shape the nether rule was written to catch. No amount of
     * name-matching would have been enough; the question was never what a block is called.
     *
     * <p>
     * Asked as the game asks it, of each value on its own (Xep, 2026-11-09, on Galacticraft's moon and
     * Mars rock on 1.7.10: "Fix it, per-value rule"; that edition asks {@code hasTileEntity} of each
     * metadata value, as its chunk does, where it once kept a whole block out for one value's spawner).
     * A value here is a block, and the game's own gate on a block entity is the state's,
     * {@code BlockState.hasBlockEntity()}, which {@code LevelChunk} asks before it makes or keeps one:
     * at this version, read from its own jars on both loaders, that is {@code EntityBlock} and the same
     * for every state, so the per-value rule is this one. {@code newBlockEntity} is not asked: the moving
     * piston's block makes none there and is handed one, which the chunk keeps.
     */
    static boolean keepsItsOwnState(Block block) {
        // EntityBlock is the question the shared module can ask. 1.12.2 asks it of every metadata value as well,
        // because a block there may carry a tile entity for some subtypes and not others; 1.13 split
        // those subtypes into separate blocks, so a block either is one or is not.
        //
        // And it is the whole answer at this version, on both loaders. 1.16.5 also asks the loader (0.9.222, spec SD6
        // and SD30), because a Forge 1.16 mod kept a tile entity by overriding IForgeBlock.hasTileEntity(state) and
        // need never be an EntityBlock. Forge took that hook away in 1.17: 1.20.1's own BlockStateBase.hasBlockEntity()
        // is getBlock() instanceof EntityBlock and nothing else, and Forge 47's IForgeBlock has no block-entity method
        // at all, so a block that is not an EntityBlock cannot hold one. The seam 1.16.5 carries for it had no caller
        // here and was taken out in 0.9.225, which unwired.py found.
        return block instanceof EntityBlock;
    }

    /**
     * Whether a family lets this block's shape wear, once the family is known.
     *
     * <p>
     * Separate from the classification because the two questions are separate: what a block is
     * made of does not depend on what shape it was cut into, and whether that shape wears is the
     * family's decision rather than the block's.
     */
    static SurfaceFamily gateShape(BlockFacts facts, SurfaceFamily family) {
        if (family == null) return null;
        // By class alone: detection classifies before any data is published (W3 T6, as 1.12.2 and 1.7.10).
        SurfaceShape shape = facts.shape();
        if (!shape.isPartial()) return family;

        FamilySettings settings = TrmtConfig.family(family);
        if (settings == null) return null;
        return Detection.letIn(family, shape, settings.slabs, settings.stairs);
    }

    private static boolean contains(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Config lists
    // ------------------------------------------------------------------

    private static void applyList(Map<Integer, SurfaceFamily> out, Set<String> excluded, String[] entries,
        SurfaceFamily family) {
        if (entries == null) return;
        for (String raw : entries) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;

            int meta = ANY_META;
            String name = entry;
            int lastColon = entry.lastIndexOf(':');
            // "modid:block:meta" - the metadata suffix is optional and may be "*".
            if (lastColon > 0 && entry.indexOf(':') != lastColon) {
                String tail = entry.substring(lastColon + 1);
                name = entry.substring(0, lastColon);
                if (!"*".equals(tail)) {
                    try {
                        meta = Integer.parseInt(tail);
                    } catch (NumberFormatException bad) {
                        Trmt.LOG.warn("Bad metadata '{}' in surface entry '{}', treating as wildcard", tail, entry);
                    }
                }
            }

            if (excluded.contains(name.toLowerCase(Locale.ROOT))) continue;
            // Not even by the player's own line: the other mod's blocks are its own (D60, W5 spec 2.11).
            if (OriginalBlocks.standsAside(name)) continue;

            // A name 1.13 retired stands for each block it became, so a list carried from 1.7.10 - and this edition's
            // own vegetation list, which shipped the old spellings - still names its plants (0.9.222, spec SD32).
            for (String current : com.trmtgtnh.util.OldNames.orSelf(name, meta)) {
                // getOptional rather than get: the registry answers a missing name with air rather than
                // with null, and a stale entry would otherwise quietly claim air as a surface.
                Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getOptional(net.minecraft.resources.ResourceLocation.tryParse(current))
                    .orElse(null);
                if (block == null) {
                    // Not an error: pack composition changes, and a stale entry should not shout.
                    Trmt.LOG.debug("Surface entry '{}' names a block that is not installed, skipping", entry);
                    continue;
                }
                int id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(block);
                if (id < 0) continue;
                // A list is taken at its word about which family a block belongs to, but not about
                // whether it can carry an overlay at all. Detection writes what it finds back into
                // these lists and never removes an entry, so one bad name recorded on an older
                // build would otherwise be re-added on every load for ever.
                if (keepsItsOwnState(block)) {
                    Trmt.LOG.debug("Surface entry '{}' keeps a tile entity, skipping", entry);
                    continue;
                }
                // Nor about whether it is ground at all, for the block that never is. Detection on 0.9.218
                // and earlier recorded frosted ice in the ice list of every config it ran in, and the list
                // keeps it, so refusing it in detection alone left every world opened before the fix still
                // laying it - which is how the 0.9.219 pass found the waterfall again in every instance.
                if (neverGround(block)) {
                    Trmt.LOG.debug("Surface entry '{}' is never ground, skipping", entry);
                    continue;
                }

                // One entry per block, whether or not the entry carried a :value suffix. A suffix is
                // accepted and ignored rather than refused, so a settings file written for 1.7.10 or
                // 1.12.2 still loads here and still means the block it names - which is the promise the
                // settings file makes about moving between editions. What it cannot mean any more is one
                // variant of that block, because those are separate blocks now and have their own names.
                out.put(Integer.valueOf(id), family);
            }
        }
    }

    public static Set<String> parseNameSet(String[] entries) {
        Set<String> set = new HashSet<String>();
        if (entries != null) {
            for (String entry : entries) {
                if (entry != null && !entry.trim()
                    .isEmpty()) {
                    set.add(
                        entry.trim()
                            .toLowerCase(Locale.ROOT));
                }
            }
        }
        return set;
    }

    /**
     * The published table from the one keyed by block id: each block's single form, and a slab's doubled form
     * wherever its single form wears or detection found the doubled one wearing on its own (spec SD20). See
     * {@link #formed}.
     */
    static Map<Integer, SurfaceFamily> withForms(Map<Integer, SurfaceFamily> byBlock,
        Map<Integer, SurfaceFamily> doubled) {
        Map<Integer, SurfaceFamily> out = new HashMap<Integer, SurfaceFamily>(byBlock.size() * 3);
        for (Map.Entry<Integer, SurfaceFamily> entry : byBlock.entrySet()) {
            int id = entry.getKey()
                .intValue();
            out.put(Integer.valueOf(formed(id, SINGLE)), entry.getValue());
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(id) instanceof SlabBlock) {
                out.put(Integer.valueOf(formed(id, DOUBLED)), entry.getValue());
            }
        }
        for (Map.Entry<Integer, SurfaceFamily> entry : doubled.entrySet()) {
            Integer key = Integer.valueOf(formed(entry.getKey()
                .intValue(), DOUBLED));
            if (!out.containsKey(key)) out.put(key, entry.getValue());
        }
        return out;
    }

    /** Strips whole families out when their toggle is off. */
    private static void dropDisabledFamilies(Map<Integer, SurfaceFamily> out) {
        Iterator<Map.Entry<Integer, SurfaceFamily>> it = out.entrySet()
            .iterator();
        while (it.hasNext()) {
            FamilySettings settings = TrmtConfig.family(
                it.next()
                    .getValue());
            if (settings == null || !settings.enabled) it.remove();
        }
    }

    // ------------------------------------------------------------------
    // Texture planning
    // ------------------------------------------------------------------

    /**
     * The distinct blocks worth generating color-matched wear textures for.
     *
     * <p>
     * The older editions claim all sixteen metadata values per block and narrow the list on the
     * client, because there one block id wears sixteen faces. 1.13 split those into blocks of their
     * own, so a key here is a block and the list is simply the staged part of the table, ordered
     * deterministically - sprite names and fallbacks have to be the same on every launch, because
     * they end up baked into the ghost's icon tables.
     *
     * <p>
     * <strong>This shifted the key right by four until 2026-10-05, which was the metadata era's
     * shift left behind.</strong> Dividing every registry id by sixteen named a different block,
     * nearly always one that does not erode: the set collapsed from a hundred and thirty-three
     * states to twenty-three, the wear generator composed pictures for the wrong blocks - eight
     * textured faces where the 1.12.2 edition has fifty - what could sink was marked from the wrong
     * set, and {@code /trmt demonstrate} answered "Nothing is detected as erodable" because every
     * candidate failed the family check it is asked for. Ten callers read this list; all of them
     * were wrong together, which is why nothing stood out as broken.
     */
    private static List<SurfaceState> collectTexturableStates(Map<Integer, SurfaceFamily> built,
        List<SurfaceState> apart) {
        Map<Integer, SurfaceFamily> byBlock = new HashMap<Integer, SurfaceFamily>();
        for (Map.Entry<Integer, SurfaceFamily> entry : built.entrySet()) {
            if (!entry.getValue().staged) continue;
            // One per block, whichever of its forms wears: the key carries the form in its low four bits (SD20).
            byBlock.put(Integer.valueOf(entry.getKey()
                .intValue() >> 4), entry.getValue());
        }
        // A block that wears only in the states a data file sets apart is a surface too.
        for (SurfaceState state : apart) {
            Integer id = Integer.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(state.block));
            if (!byBlock.containsKey(id)) byBlock.put(id, state.family);
        }

        List<SurfaceState> states = new ArrayList<SurfaceState>(byBlock.size());
        for (Map.Entry<Integer, SurfaceFamily> entry : byBlock.entrySet()) {
            Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(
                entry.getKey()
                    .intValue());
            String name = registryName(block);
            if (block == null || name == null) continue;
            states.add(new SurfaceState(block, name, entry.getValue()));
        }
        // Sorted by registry name so the sprite set is identical on every launch, which
        // matters because sprite names end up baked into the ghost blocks' icon tables.
        Collections.sort(states);
        return Collections.unmodifiableList(states);
    }

    /**
     * How much extra traffic this block state takes before it advances, 1 for ordinary ground.
     *
     * <p>
     * Applied when a threshold is drawn, so it is paid once per position rather than per step,
     * and healing is untouched - a path recovers at the same rate as anything else, it just
     * takes much longer to wear in the first place.
     */
    public static float resistanceOf(BlockState state) {
        if (state == null) return 1f;
        StateTable.Row row = rowOf(state);
        if (row != null) return row.resistance;
        Map<Integer, Float> current = resistance;
        if (current.isEmpty()) return 1f;
        int id = key(state.getBlock());
        if (id < 0) return 1f;
        Float found = current.get(Integer.valueOf(id));
        return found == null ? 1f : found.floatValue();
    }

    private static Map<Integer, Float> collectResistance(Set<String> excluded, Map<Integer, Float> fromData,
        Map<Integer, Float> built) {
        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings settings = TrmtConfig.family(family);
            if (settings == null || settings.resistance <= 1f) continue;

            Map<Integer, SurfaceFamily> resolved = new HashMap<Integer, SurfaceFamily>();
            applyList(resolved, excluded, settings.resistantBlocks, family);
            for (Integer state : resolved.keySet()) {
                built.put(state, Float.valueOf(settings.resistance));
            }
        }
        // A file's resistance under the player's resistant lists, which win where both name a block.
        return Collections.unmodifiableMap(SurfaceData.resistance(fromData, built));
    }

    /**
     * The id of a block a data file names, or -1 where it has none here: not installed, excluded by the player, this
     * mod's own, another wear mod's (D60), never ground, or keeping a tile entity - the same guards detection and the
     * lists keep, for the same reasons.
     */
    private static int dataBlockId(String name, Set<String> excluded) {
        if (name == null || excluded.contains(name.toLowerCase(Locale.ROOT))) return -1;
        if (name.startsWith(Trmt.MODID + ":")) return -1;
        if (OriginalBlocks.standsAside(name)) return -1;
        net.minecraft.resources.ResourceLocation at = net.minecraft.resources.ResourceLocation.tryParse(name);
        if (at == null) return -1;
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(at)
            .orElse(null);
        if (block == null || block == Blocks.AIR) return -1;
        if (neverGround(block)) return -1;
        if (keepsItsOwnState(block)) {
            Trmt.LOG.debug("A data file names {}, which keeps a tile entity, so it does not wear", name);
            return -1;
        }
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(block);
    }

    /**
     * How a data file's block names are found in this game: each name's id past {@link #dataBlockId}'s guards, the
     * properties of the state each form stands for, so a selector naming them ({@code ns:name[type=bottom]}) matches,
     * and each block's tags and each tag's blocks, so {@code #ns:tag} reaches them (block tags, 1.16.5 on).
     */
    private static SurfaceData.Names dataNames(final Set<String> excluded) {
        return new SurfaceData.Names() {

            @Override
            public int idOf(String blockName) {
                return dataBlockId(blockName, excluded);
            }

            @Override
            public String nameOf(int blockId) {
                return registryName(net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(blockId));
            }

            @Override
            public Map<String, String> properties(int blockId, int form) {
                return formProperties(net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(blockId), form);
            }

            @Override
            public java.util.Collection<String> tags(int blockId) {
                return tagsOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(blockId));
            }

            @Override
            public List<SurfaceData.State> states(int blockId) {
                Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(blockId);
                List<SurfaceData.State> out = new ArrayList<SurfaceData.State>();
                for (BlockState state : block.getStateDefinition()
                    .getPossibleStates()) {
                    out.add(new SurfaceData.State(Block.getId(state), formOf(state), propertiesOf(state)));
                }
                return out;
            }

            @Override
            public java.util.Collection<Integer> idsInTag(String tag) {
                net.minecraft.resources.ResourceLocation at = net.minecraft.resources.ResourceLocation.tryParse(tag);
                if (at == null) return Collections.emptyList();
                java.util.Optional<net.minecraft.core.HolderSet.Named<Block>> found = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, at));
                if (!found.isPresent()) return Collections.emptyList();
                List<Integer> out = new ArrayList<Integer>();
                for (net.minecraft.core.Holder<Block> holder : found.get()) {
                    int id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(holder.value());
                    if (id >= 0) out.add(Integer.valueOf(id));
                }
                return out;
            }
        };
    }

    /**
     * The state a form of a block stands for, as its properties: the block's default state for its single form, and a
     * slab's {@code type=double} state for its doubled one; null for no block, or a doubled form of anything but a slab.
     * 1.16.5's tables are kept by block and form, so this is what a data file's {@code [prop=value]} selector is matched
     * against for those tables; a state with other facts is set apart ({@link StateTable}).
     */
    public static Map<String, String> formProperties(Block block, int form) {
        if (block == null) return null;
        BlockState state = block.defaultBlockState();
        if (form == DOUBLED) {
            if (!(block instanceof SlabBlock) || !state.hasProperty(SlabBlock.TYPE)) return null;
            state = state.setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE);
        }
        return propertiesOf(state);
    }

    /**
     * A state's properties, names and values as the game spells them in a block-state string ({@code type=bottom}),
     * sorted by name; null for no state.
     */
    public static Map<String, String> propertiesOf(BlockState state) {
        if (state == null) return null;
        Map<String, String> out = new java.util.TreeMap<String, String>();
        for (Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> each : state
            .getValues()
            .entrySet()) {
            out.put(
                each.getKey()
                    .getName(),
                valueName(each.getKey(), each.getValue()));
        }
        return out;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String valueName(net.minecraft.world.level.block.state.properties.Property property,
        Comparable value) {
        return property.getName(value);
    }

    /**
     * The block tags a block is in, as {@code ns:tag} without the mark, sorted; empty when tags are not bound yet. Read
     * off the block's own registry holder, which is what a tag bound on either side fills in.
     */
    public static java.util.Collection<String> tagsOf(Block block) {
        if (block == null) return null;
        List<String> out = new ArrayList<String>();
        try {
            block.builtInRegistryHolder()
                .tags()
                .forEach(
                    tag -> out.add(
                        tag.location()
                            .toString()));
        } catch (RuntimeException unbound) {
            // No tags bound yet (the title screen, before any world): the block answers by name alone.
        }
        Collections.sort(out);
        return out;
    }

    /** The file's answers for the states set apart, less those of the blocks a player's exclude list names. */
    private static Map<Integer, String> keptFromPlayer(Map<Integer, String> fromData, Set<String> excluded,
        SurfaceData.SetApart apart) {
        Map<Integer, String> out = new HashMap<Integer, String>();
        for (Map.Entry<Integer, String> each : fromData.entrySet()) {
            String name = lowerNameOf(
                apart.forms.get(each.getKey())
                    .intValue() >> 4);
            if (name != null && !excluded.contains(name)) out.put(each.getKey(), each.getValue());
        }
        return out;
    }

    /** The block a key kept by block id belongs to, by its registry name lower-cased, or null. */
    private static String lowerNameOf(int blockId) {
        String name = registryName(net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(blockId));
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Ground cover
    // ------------------------------------------------------------------

    /**
     * True when this block state is a decoration standing on the ground rather than ground.
     *
     * <p>
     * Asked once per gradation that actually lands rather than once per movement sample, so this
     * is nowhere near as hot as {@link #familyOf} - but it is the same lookup and costs the same,
     * and there is no reason to make it cheaper than the one that is.
     */
    public static boolean isGroundCover(BlockState state) {
        if (state == null) return false;
        StateTable.Row row = rowOf(state);
        return row != null ? row.cover : isGroundCover(state.getBlock());
    }

    /** The same, for a caller that genuinely has only the block. */
    public static boolean isGroundCover(Block block) {
        if (block == null) return false;
        Set<Integer> current = groundCover;
        if (current.isEmpty()) return false;
        int id = key(block);
        return id >= 0 && current.contains(Integer.valueOf(id));
    }

    /**
     * True when the cover at this state holds its ground instead of coming off it.
     *
     * <p>
     * Asked wherever a square's wear is about to be shown or stood on, which is often, so it is the
     * same one-lookup shape as {@link #isGroundCover} and returns on an empty-set check when
     * nothing in the pack holds anything.
     */
    public static boolean isGroundCoverHolder(BlockState state) {
        if (state == null) return false;
        StateTable.Row row = rowOf(state);
        return row != null ? row.holds : isGroundCoverHolder(state.getBlock());
    }

    /** The same, for a caller that genuinely has only the block. */
    public static boolean isGroundCoverHolder(Block block) {
        if (block == null) return false;
        Set<Integer> current = groundCoverHolds;
        if (current.isEmpty()) return false;
        int id = key(block);
        return id >= 0 && current.contains(Integer.valueOf(id));
    }

    /**
     * Whether this is the sort of plant somebody put there on purpose.
     *
     * <p>
     * A sapling is a tree in waiting and a flower is a thing somebody picked and placed, where a
     * tuft of grass is weather. Nothing in the registry draws that line, so this draws it the way
     * the rest of the file draws lines it cannot derive: by what a block extends first, and by what
     * it is called only where the ancestry runs out. Everything it misses is one entry in a list
     * that detection writes out for exactly this purpose.
     */
    private static boolean looksPlanted(Block block, String simple) {
        if (block instanceof SaplingBlock || block instanceof FlowerBlock) return true;
        return contains(simple, "sapling", "seedling", "sprout_tree");
    }

    /** Builds the holding set out of the cover set, by detection and by hand. */
    private static Set<Integer> collectHolders(Set<Integer> cover, Set<String> detected, Map<Integer, String> fromData,
        Set<Integer> planted, Set<Integer> listedAtAll) {
        Set<String> excluded = parseNameSet(TrmtConfig.groundCoverHoldsExclude);
        Set<Integer> holds = new HashSet<Integer>();

        if (TrmtConfig.groundCoverAutoDetect) {
            for (Object candidate : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (!(candidate instanceof Block)) continue;
                Block block = (Block) candidate;
                if (block == Blocks.AIR) continue;

                String name = registryName(block);
                if (name == null) continue;
                String lower = name.toLowerCase(Locale.ROOT);
                if (excluded.contains(lower)) continue;
                if (name.startsWith(Trmt.MODID + ":")) continue;
                if (!looksPlanted(block, lower.substring(lower.indexOf(':') + 1))) continue;

                int id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(block);
                if (id < 0) continue;
                Integer at = Integer.valueOf(id);
                planted.add(at);
                if (cover.contains(at)) {
                    holds.add(at);
                    // Recorded only where no data file says whether it holds: the file's answer is never written in.
                    if (!fromData.containsKey(at)) detected.add(name);
                }
            }
        }

        Map<Integer, SurfaceFamily> listed = new HashMap<Integer, SurfaceFamily>();
        applyList(listed, excluded, TrmtConfig.groundCoverHoldsBlocks, SurfaceFamily.VEGETATION);
        listedAtAll.addAll(listed.keySet());
        Set<Integer> listedHolds = new HashSet<Integer>();
        for (Integer at : listed.keySet()) {
            if (!cover.contains(at)) continue;
            listedHolds.add(at);
            Integer ready = growthTwin(at.intValue());
            if (ready != null) listedHolds.add(ready);
        }

        // A data file's holds or breaks between detection and the player's list (W3 T5), and never for a block the
        // player keeps from holding.
        Map<Integer, String> data = new HashMap<Integer, String>();
        for (Map.Entry<Integer, String> each : fromData.entrySet()) {
            String name = lowerNameOf(
                each.getKey()
                    .intValue());
            if (name != null && !excluded.contains(name)) data.put(each.getKey(), each.getValue());
        }
        return Collections.unmodifiableSet(SurfaceData.holds(cover, holds, listedHolds, data));
    }

    /**
     * The same sapling marked ready to grow, for a holder listed by hand at one metadata, or null.
     *
     * <p>
     * A vanilla sapling marks itself ready to grow in the top bit of its metadata, and the server sets that
     * bit without telling any client, so the two worlds disagree about it for the rest of the plant's
     * life. A holder listed at the plain value then held on one side only: a player stood at full height
     * on the client and was dropped into the rut by the server. The marked value is held alongside it, so
     * both sides answer alike whichever of the two they see.
     *
     * <p>
     * Only where the block drops the same thing for both values, which is how a growth mark shows itself
     * from outside, and asked here, once, rather than at every lookup. Biomes O' Plenty's saplings use the
     * same bit for eight more trees, each dropping its own sapling, and masking it off at lookup had a
     * listed apple tree quietly holding an orange autumn one on the server as well. Detected holders list
     * all sixteen values and never need this.
     */
    private static Integer growthTwin(int packed) {
        // Nothing to twin, and the reason is the one that simplified the rest of this class. 1.12.2
        // marks a sapling ready to grow in metadata bit three, so a holder listed by hand at one
        // value needs its grown twin listed too or it stops being a holder the moment it is ready.
        // Here growth is a blockstate property and this table is keyed by block, so a sapling is the
        // same entry at every stage and there is no second key to add.
        return null;
    }

    /**
     * Whether a block is the sort of thing that stands on ground and goes flat with it.
     *
     * <p>
     * The material says what it is made of, and plants and vine between them are what every thin
     * ground decoration in the pack is built on - Biomes O' Plenty's foliage block, which is dead
     * leaf pile and wheat grass and fourteen other things at sixteen metadata values, is one
     * plants-material block. Cactus is the third, on a material of its own and standing in its own
     * right rather than lying on the ground, and it is here for the same reason cane is: a stalk
     * has even less to stand on than a flower once the ground drops away. Not rendering as a normal
     * block then rules out the modded full cube that happens to have been built on one of these.
     *
     * <p>
     * {@link Plants} does the real work: "this grows out of whatever is underneath it", which is
     * exactly the relationship being modelled, and it is what keeps vines out - a vine is on the vine
     * material and walks through, but it hangs off a wall and has no opinion about the ground, so a
     * path worn under one should not bring it down. On Forge that question is {@code IPlantable},
     * Forge's own word for it, which is what the older editions ask directly; Fabric has no
     * equivalent and answers with vanilla's bush class instead, so the two agree about vanilla and
     * differ about a modded plant that extends neither. They differ in the safe direction: a plant
     * neither recognises is missed, and a missed one is a decoration that survives a path, where an
     * over-reach is somebody's block destroyed. The list under surfaces is there for anything
     * detection turns out to be too shy about.
     */
    private static boolean looksLikeGroundCover(Block block) {
        MaterialKind material = MaterialKind.of(block);
        if (!material.plant() && material != MaterialKind.CACTUS) {
            return false;
        }
        if (com.trmtgtnh.util.Worlds.fullCube(block.defaultBlockState())) return false;
        if (!Plants.growsOnGround(block.defaultBlockState())) return false;
        return !keepsItsOwnState(block);
    }

    /**
     * Builds the cover set from detection and from the hand-written list.
     *
     * <p>
     * A second walk of the block registry rather than a branch inside the first. The two rules
     * answer different questions off different exclude lists, and folding them together would tie
     * the ground-cover toggle to the surface one for nothing: this is five thousand blocks looked
     * at twice, once, at post-init.
     *
     * <p>
     * Built whether or not the feature is switched on, because the list is how somebody decides
     * whether to switch it on. That is the same bargain the surface detection already makes, and
     * it costs a walk that has just happened anyway.
     */
    private static Set<Integer> collectGroundCover(Map<Integer, SurfaceFamily> built, Set<String> detected,
        Map<Integer, String> fromData, Set<Integer> own) {
        Set<String> excluded = parseNameSet(TrmtConfig.groundCoverExclude);
        Set<Integer> cover = new HashSet<Integer>();

        if (TrmtConfig.groundCoverAutoDetect) {
            for (Object candidate : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (!(candidate instanceof Block)) continue;
                Block block = (Block) candidate;
                if (block == Blocks.AIR) continue;

                String name = registryName(block);
                if (name == null || excluded.contains(name.toLowerCase(Locale.ROOT))) continue;
                if (name.startsWith(Trmt.MODID + ":")) continue;
                if (!looksLikeGroundCover(block)) continue;

                int id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(block);
                if (id < 0) continue;
                // All sixteen, for the reason detection claims all sixteen everywhere else: which
                // metadata values a block actually uses is not a question the registry answers.
                cover.add(Integer.valueOf(id));
                // Not written into the list where a data file already says it is cover (W3 T5).
                if (!fromData.containsKey(Integer.valueOf(id))) detected.add(name);
            }
        }

        // Hand-written additions, resolved by the same parser the family lists use. The family
        // handed to it is thrown away with the map: all that is wanted is the name-to-state
        // arithmetic and the tile-entity guard, which is what the resistance list does with it too.
        Map<Integer, SurfaceFamily> listed = new HashMap<Integer, SurfaceFamily>();
        applyList(listed, excluded, TrmtConfig.groundCoverBlocks, SurfaceFamily.VEGETATION);
        cover.addAll(listed.keySet());
        own.addAll(cover);

        // Every block a data file gives a cover (W3 T5), unless the player excluded it from the cover.
        Map<Integer, String> data = new HashMap<Integer, String>();
        for (Map.Entry<Integer, String> each : fromData.entrySet()) {
            String name = lowerNameOf(
                each.getKey()
                    .intValue());
            if (name != null && !excluded.contains(name)) data.put(each.getKey(), each.getValue());
        }
        cover = SurfaceData.cover(cover, data);

        // Nothing that wears may also be knocked over. The material rule makes this impossible on
        // its own, but a hand-written entry can put any name in any list, and a config that
        // shattered its own grass blocks would be a miserable thing to work out from the symptom.
        Iterator<Integer> it = cover.iterator();
        while (it.hasNext()) {
            SurfaceFamily family = built.get(it.next());
            if (family != null && family.staged) it.remove();
        }

        return Collections.unmodifiableSet(cover);
    }

    /** Registry name such as {@code minecraft:grass}, or null if the block is unregistered. */
    public static String registryName(Block block) {
        if (block == null) return null;
        net.minecraft.resources.ResourceLocation name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return name == null ? null : name.toString();
    }

    /** One erodable block and the family it wears, paired for texture generation. */
    public static final class SurfaceState implements Comparable<SurfaceState> {

        public final Block block;
        public final String registryName;
        public final SurfaceFamily family;

        SurfaceState(Block block, String registryName, SurfaceFamily family) {
            this.block = block;
            this.registryName = registryName;
            this.family = family;
        }

        @Override
        public int compareTo(SurfaceState other) {
            return registryName.compareTo(other.registryName);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SurfaceState && registryName.equals(((SurfaceState) other).registryName);
        }

        @Override
        public int hashCode() {
            return registryName.hashCode();
        }

        @Override
        public String toString() {
            return family.key() + " " + registryName;
        }
    }
}
