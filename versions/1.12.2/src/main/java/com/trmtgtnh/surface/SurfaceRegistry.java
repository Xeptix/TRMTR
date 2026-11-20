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

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockFrostedIce;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.IPlantable;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.data.DataTable;
import com.trmtgtnh.dataload.DataLoad;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.config.TrmtConfig;
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
    private static net.minecraft.block.state.IBlockState stateOf(Block block, int meta) {
        try {
            return block.getStateFromMeta(meta);
        } catch (RuntimeException awkwardBlock) {
            return block.getDefaultState();
        }
    }

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

    /**
     * Block states that wear more slowly, mapped to how much more traffic they take.
     *
     * <p>
     * Separate from the family table because resistance is a property of the particular block,
     * not of its family: a grass path wears as dirt, but takes far longer to do it than the
     * dirt beside it.
     */
    private static volatile Map<Integer, Float> resistance = Collections.emptyMap();

    /** What a data file says also pays for mending a block state ({@code repairWith}, W3 T5), by state. */
    private static volatile Map<Integer, String[]> repairWith = Collections.emptyMap();

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

    /**
     * The shapes data files give block states (W3 spec 2.4, T6), by packed key: the server's while its table is held,
     * because a shape decides where somebody stands and so must be the server's on both sides.
     */
    private static volatile Map<Integer, DataShape> shapes = Collections.emptyMap();

    /**
     * The chains data files give block states of their own (W3 T9), by packed key: the families each wears through to,
     * in order, and none for one that sinks as itself. The server's while its table is held, because a block's chain
     * decides which of its records the server keeps and what its ruts show.
     */
    private static volatile Map<Integer, SurfaceFamily[]> ownChains = Collections.emptyMap();

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

        final boolean holdsSwitch;

        final long fingerprint;

        Held(Map<Integer, SurfaceFamily> table, Set<Integer> cover, Set<Integer> holds, Map<Integer, DataShape> shapes,
            Map<Integer, SurfaceFamily[]> chains, boolean holdsSwitch, long fingerprint) {
            this.table = table;
            this.cover = cover;
            this.holds = holds;
            this.shapes = shapes;
            this.chains = chains;
            this.holdsSwitch = holdsSwitch;
            this.fingerprint = fingerprint;
        }
    }

    private SurfaceRegistry() {}

    private static int key(int blockId, int meta) {
        return (blockId << 4) | (meta & 0xF);
    }

    /**
     * The family a block at this metadata belongs to, or null when it does not erode. Hot
     * path - called per movement sample and per painted position.
     */
    public static SurfaceFamily familyOf(Block block, int meta) {
        if (block == null) return null;
        Map<Integer, SurfaceFamily> current = table;
        if (current.isEmpty()) return null;
        int id = Block.getIdFromBlock(block);
        if (id < 0) return null;
        return current.get(Integer.valueOf(key(id, meta)));
    }

    /**
     * Whether a data file or a plugin brought this block state into the family table rather than the mod's own
     * detection and the player's lists ({@link SurfaceData#brought}). The quick demos lay none of these.
     */
    public static boolean broughtByData(Block block, int meta) {
        if (block == null || meta < 0 || meta > 15) return false;
        Set<Integer> current = brought;
        if (current.isEmpty()) return false;
        int id = Block.getIdFromBlock(block);
        if (id < 0) return false;
        return current.contains(Integer.valueOf(key(id, meta)));
    }

    /**
     * The shape a data file gives this block state, or null where none does and its class decides
     * ({@link SurfaceShape#of(Block, int)}). On the collision path, so one map read and no more.
     */
    public static DataShape shapeOf(Block block, int meta) {
        if (block == null) return null;
        Map<Integer, DataShape> current = shapes;
        if (current.isEmpty()) return null;
        int id = Block.getIdFromBlock(block);
        if (id < 0) return null;
        return current.get(Integer.valueOf(key(id, meta)));
    }

    /**
     * The block's own list a data file gives it (W3 T9), the families it wears through to in order, or null where it
     * follows its family's chain. An empty list sinks as itself. Not to be written to.
     */
    public static SurfaceFamily[] throughOf(Block block, int meta) {
        if (block == null) return null;
        Map<Integer, SurfaceFamily[]> current = ownChains;
        if (current.isEmpty()) return null;
        int id = Block.getIdFromBlock(block);
        if (id < 0) return null;
        return current.get(Integer.valueOf(key(id, meta)));
    }

    /**
     * The chain the ground of this block state follows: its family's, or the block's own where a data file gives it one
     * (W3 T9; {@link ErosionChain#of(SurfaceFamily, SurfaceFamily[])}). Every question about where a record sits on its
     * chain is asked of this, so a block with a list of its own is worn, healed and placed along it. A chain with no
     * steps where the block does not erode.
     */
    public static ErosionChain.Chain chainOf(Block block, int meta) {
        SurfaceFamily family = familyOf(block, meta);
        if (family == null) return ErosionChain.of(null);
        return ErosionChain.of(family, throughOf(block, meta));
    }

    /** True when this block state erodes into stages the client can paint. */
    public static boolean isStaged(Block block, int meta) {
        SurfaceFamily family = familyOf(block, meta);
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
        encoded = made;
        encodedFor = wanted;
        return made;
    }

    /**
     * Puts a server's table in use for the visit. Client thread.
     *
     * @return false, holding nothing, when the table names a family this build does not have
     */
    public static boolean holdServerTable(SurfaceTableCodec.Table decoded, Map<Integer, int[]> chains,
        boolean holdsSwitch, long fingerprint) {
        SurfaceFamily[] all = SurfaceFamily.values();
        Map<Integer, SurfaceFamily> families = new HashMap<Integer, SurfaceFamily>(decoded.families.size() * 2);
        for (Map.Entry<Integer, Integer> entry : decoded.families.entrySet()) {
            int ordinal = entry.getValue()
                .intValue();
            if (ordinal < 0 || ordinal >= all.length) return false;
            families.put(entry.getKey(), all[ordinal]);
        }
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
            holdsSwitch,
            fingerprint);
        held = visiting;
        publish(visiting.table, visiting.cover, visiting.holds, visiting.shapes, visiting.chains);
        return true;
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
        Map<Integer, DataShape> shaped, Map<Integer, SurfaceFamily[]> chained) {
        table = families;
        shapes = shaped;
        ownChains = chained;
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
        ErosionChain.ownLists(byFamily);
        texturable = collectTexturableStates(families);
        java.util.Set<SurfaceFamily> holding = java.util.EnumSet.noneOf(SurfaceFamily.class);
        holding.addAll(families.values());
        familiesHeld = java.util.Collections.unmodifiableSet(holding);
        groundCover = cover;
        groundCoverHolds = holds;
        Held visiting = held;
        publishedFingerprint = visiting != null ? visiting.fingerprint : ownFingerprint;
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
        Map<SurfaceFamily, Set<String>> detected = new EnumMap<SurfaceFamily, Set<String>>(SurfaceFamily.class);
        final Set<String> excluded = parseNameSet(TrmtConfig.surfaceExclude);

        // The data files' families, cover, resistance and repair (W3 spec 2.2, T5), read once from the one table
        // the last data read published, so this rebuild sees one read and never half of the next.
        DataTable dataTable = DataLoad.table();
        SurfaceData.Read data = SurfaceData.read(dataTable, dataNames(excluded));

        if (TrmtConfig.surfaceAutoDetect) {
            autoDetect(found, excluded, detected, data.family);
        }

        // The player's lists, in family order as they always were applied, kept apart from detection so the files
        // sit between the two: data beats detection, and the player beats data (SurfaceData.families).
        Map<Integer, SurfaceFamily> listed = new HashMap<Integer, SurfaceFamily>();
        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings settings = TrmtConfig.family(family);
            if (settings == null) continue;
            applyList(listed, excluded, settings.extra, family);
        }

        Map<Integer, SurfaceFamily> built = SurfaceData.families(found, listed, data.family);

        dropDisabledFamilies(built);
        // What the files brought, measured against this side's table with only the mod's own shipped files; kept apart
        // from what is published, so a server's table held for a visit changes nothing here.
        brought = Collections.unmodifiableSet(
            SurfaceData.brought(
                built,
                found,
                listed,
                data,
                new SurfaceData.Vanilla() {

                    @Override
                    public boolean isVanilla(int blockId) {
                        String name = registryName(Block.getBlockById(blockId));
                        return name != null && name.startsWith("minecraft:");
                    }
                }));

        resistance = collectResistance(excluded, data.resistance);
        repairWith = data.repairWith;

        Set<String> cover = new java.util.TreeSet<String>();
        Set<Integer> builtCover = collectGroundCover(built, cover, data.cover);
        Set<String> holds = new java.util.TreeSet<String>();
        Set<Integer> builtHolds = collectHolders(builtCover, holds, data.cover);
        // The shapes the files give (W3 spec 2.4, T6), resolved for the blocks this game has; sent in the table, so a
        // client that has other shape data, or none, stands where the server says.
        Map<Integer, DataShape> builtShapes = Collections
            .unmodifiableMap(SurfaceData.shapes(dataTable, dataNames(excluded)));
        // The blocks' own chains (W3 T9): a file's wearsThroughTo, on the states that wear as the family it was written
        // for. Sent in the table, so a client draws each block on the server's chain.
        Map<Integer, SurfaceFamily[]> builtChains = Collections.unmodifiableMap(SurfaceData.chains(built, found, data));
        // The server's data folded in (W3 spec 2.2): a client whose mods or packs give it other families, shapes or
        // wearers uses the server's table for the visit. No data leaves the fingerprint as it always was.
        ownFingerprint = SurfaceData.withData(
            ChainTable.fingerprint(
                SurfaceTableCodec
                    .fingerprint(ordinals(built), builtCover, builtHolds, TrmtConfig.groundCoverHolds, builtShapes),
                chainOrdinals(builtChains)),
            dataTable.fingerprint());

        // A server's table in use for the visit is published again rather than this side's own, so no
        // rebuild made while connected - a reload, the config screen, a family changing hands - can
        // quietly put back a table the server has already said is not the one in use. Detection still
        // runs and is still written down below; only what is published waits for the visit to end.
        Held visiting = held;
        if (visiting != null) {
            publish(visiting.table, visiting.cover, visiting.holds, visiting.shapes, visiting.chains);
        } else {
            publish(Collections.unmodifiableMap(built), builtCover, builtHolds, builtShapes, builtChains);
        }

        // Detection is only useful if you can see and override what it decided, so what it
        // found is written back into the per-family block lists. Turn autoDetect off and those
        // lists become the whole story, editable by hand. Detection's findings only: what the data
        // files say is never written here, so removing a mod leaves nothing of it in the config.
        if (TrmtConfig.writeDetectedSurfaces) {
            TrmtConfig.recordDetectedSurfaces(detected);
            TrmtConfig.recordDetectedGroundCover(cover);
            TrmtConfig.recordDetectedHolders(holds);
        }

        Trmt.LOG.info(
            "Resolved {} erodable block states, {} of them with generated wear textures, and {} that stand on them",
            Integer.valueOf(built.size()),
            Integer.valueOf(texturable.size()),
            Integer.valueOf(groundCover.size()));
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    private static void autoDetect(Map<Integer, SurfaceFamily> out, Set<String> excluded,
        Map<SurfaceFamily, Set<String>> detected, Map<Integer, String> decidedByData) {
        // Iterated as Object because Forge's registry is generic while vanilla's is raw;
        // this compiles against either without a cast warning at the call site.
        for (Object candidate : Block.REGISTRY) {
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

            int id = Block.getIdFromBlock(block);
            if (id < 0) continue;

            // Detection cannot tell which metadata values of an ordinary block are the erodable
            // variant, so it claims all sixteen and the exclude list carves out specifics. A slab
            // is the exception, because a slab block genuinely is several materials wearing one
            // name and will say per value which is which.
            SurfaceFamily[] byMeta = new SurfaceFamily[16];
            boolean split = false;
            for (int meta = 0; meta < 16; meta++) {
                byMeta[meta] = gateShape(facts, Detection.atValue(facts, whole, meta));
                if (byMeta[meta] != byMeta[0]) split = true;
            }

            for (int meta = 0; meta < 16; meta++) {
                SurfaceFamily family = byMeta[meta];
                if (family == null) continue;

                FamilySettings settings = TrmtConfig.family(family);
                if (settings == null || !settings.autoDetect) continue;

                out.put(Integer.valueOf(key(id, meta)), family);

                // A state a data file decides is not written into the lists: it would read back as the player's
                // line, and detection's own record would overrule the file that beat it.
                if (decidedByData.containsKey(Integer.valueOf(key(id, meta)))) continue;

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
        return block instanceof BlockFrostedIce;
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
     * Asked of each metadata value on its own, as the game asks it: {@code Chunk} holds a tile entity
     * only where {@code hasTileEntity} says so for the state at that position, whether it makes one
     * itself or is handed one, and refuses one anywhere else. Forge's own answer for an
     * {@code ITileEntityProvider} is yes for every state, so a provider is asked no differently: one
     * that says no for a value holds nothing there and makes nothing there, and that value wears.
     * Galacticraft's moon and Mars rock on 1.7.10 are the ones that found this: one block for the
     * ground, the rock under it and the boss spawner, with the spawner's tile entity at one value
     * alone, and asked of the block as a whole that one value kept all sixteen out (Xep, 2026-11-09:
     * "Fix it, per-value rule").
     *
     * <p>
     * A value that says yes is kept out even where {@code createTileEntity} makes nothing: the game
     * still lets one be set there, which is how the moving piston gets its own (its block makes none,
     * and the piston hands it one).
     *
     * <p>
     * Asked defensively because it reaches arbitrary code in a 233-mod pack: a value that cannot
     * answer is a value to leave alone.
     */
    static boolean keepsItsOwnState(Block block, int meta) {
        try {
            return block.hasTileEntity(stateOf(block, meta));
        } catch (RuntimeException awkwardBlock) {
            // A value that cannot answer is a value to leave alone.
            return true;
        }
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
        SurfaceShape shape = facts.shape();
        if (!shape.isPartial()) return family;

        FamilySettings settings = TrmtConfig.family(family);
        if (settings == null) return null;
        return Detection.letIn(family, shape, settings.slabs, settings.stairs);
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

            Block block = Block.getBlockFromName(name);
            if (block == null) {
                // Not an error: pack composition changes, and a stale entry should not shout.
                Trmt.LOG.debug("Surface entry '{}' names a block that is not installed, skipping", entry);
                continue;
            }
            int id = Block.getIdFromBlock(block);
            if (id < 0) continue;
            // A list is taken at its word about which family a block belongs to, but not about
            // whether it can carry an overlay at all. Detection writes what it finds back into
            // these lists and never removes an entry, so one bad name recorded on an older
            // build would otherwise be re-added on every load for ever. Value by value, as detection
            // asks it, where the values are taken below.
            // Nor about whether it is ground at all, for the block that never is. Detection on 0.9.218
            // and earlier recorded frosted ice in the ice list of every config it ran in, and the list
            // keeps it, so refusing it in detection alone left every world opened before the fix still
            // laying it - which is how the 0.9.219 pass found the waterfall again in every instance.
            if (neverGround(block)) {
                Trmt.LOG.debug("Surface entry '{}' is never ground, skipping", entry);
                continue;
            }

            if (meta == ANY_META) {
                for (int m = 0; m < 16; m++) {
                    if (keepsItsOwnState(block, m)) continue;
                    out.put(Integer.valueOf(key(id, m)), family);
                }
            } else if (keepsItsOwnState(block, meta & 0xF)) {
                Trmt.LOG.debug("Surface entry '{}' keeps a tile entity, skipping", entry);
            } else {
                out.put(Integer.valueOf(key(id, meta & 0xF)), family);
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
     * The distinct block states worth generating color-matched wear textures for.
     *
     * <p>
     * Detection claims all sixteen metadata values per block, but most blocks only use one
     * or two, and generating sprites for the other fourteen would waste atlas space on
     * states that never appear. The client narrows this to the metadata a block actually
     * declares; this list is only the raw candidate set, ordered deterministically so both
     * the sprite names and the fallbacks are stable across restarts.
     */
    private static List<SurfaceState> collectTexturableStates(Map<Integer, SurfaceFamily> built) {
        Map<Integer, SurfaceFamily> byBlock = new HashMap<Integer, SurfaceFamily>();
        for (Map.Entry<Integer, SurfaceFamily> entry : built.entrySet()) {
            if (!entry.getValue().staged) continue;
            byBlock.put(
                Integer.valueOf(
                    entry.getKey()
                        .intValue() >> 4),
                entry.getValue());
        }

        List<SurfaceState> states = new ArrayList<SurfaceState>(byBlock.size());
        for (Map.Entry<Integer, SurfaceFamily> entry : byBlock.entrySet()) {
            Block block = Block.getBlockById(
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
    public static float resistanceOf(Block block, int meta) {
        if (block == null) return 1f;
        Map<Integer, Float> current = resistance;
        if (current.isEmpty()) return 1f;
        int id = Block.getIdFromBlock(block);
        if (id < 0) return 1f;
        Float found = current.get(Integer.valueOf(key(id, meta)));
        return found == null ? 1f : found.floatValue();
    }

    private static Map<Integer, Float> collectResistance(Set<String> excluded, Map<Integer, Float> fromData) {
        Map<Integer, Float> built = new HashMap<Integer, Float>();
        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings settings = TrmtConfig.family(family);
            if (settings == null || settings.resistance <= 1f) continue;

            Map<Integer, SurfaceFamily> resolved = new HashMap<Integer, SurfaceFamily>();
            applyList(resolved, excluded, settings.resistantBlocks, family);
            for (Integer state : resolved.keySet()) {
                built.put(state, Float.valueOf(settings.resistance));
            }
        }
        // A file's resistance under the player's resistant lists, which win where both name a state.
        return Collections.unmodifiableMap(SurfaceData.resistance(fromData, built));
    }

    /**
     * What else a data file says pays for mending this block state ({@code repairWith}), in the config lists'
     * spelling, besides what its family's repairBlocks names. Empty for most blocks. Server side; read by
     * {@code HealingCost}, which bone meal, the chunk tamper and the golem all pay through.
     */
    public static String[] repairWithOf(Block block, int meta) {
        if (block == null) return new String[0];
        Map<Integer, String[]> current = repairWith;
        if (current.isEmpty()) return new String[0];
        int id = Block.getIdFromBlock(block);
        if (id < 0) return new String[0];
        return SurfaceData.repairWith(current, id, meta);
    }

    /**
     * The id of a block a data file names, or -1 where it has none here: not installed, excluded by the player, this
     * mod's own, or another wear mod's (D60) - the same guards detection and the lists keep, for the same reasons. A
     * value keeping a tile entity is the fourth, asked value by value ({@link #dataValueKeepsState}).
     */
    private static int dataBlockId(String name, Set<String> excluded) {
        if (name == null || excluded.contains(name.toLowerCase(Locale.ROOT))) return -1;
        if (name.startsWith(Trmt.MODID + ":")) return -1;
        if (OriginalBlocks.standsAside(name)) return -1;
        Block block = Block.getBlockFromName(name);
        if (block == null || block == Blocks.AIR) return -1;
        return Block.getIdFromBlock(block);
    }

    /** Whether one value of a block a data file names keeps a tile entity, and so does not wear, as detection asks. */
    private static boolean dataValueKeepsState(String name, int meta) {
        Block block = Block.getBlockFromName(name);
        if (block == null || !keepsItsOwnState(block, meta)) return false;
        Trmt.LOG.debug(
            "A data file names {}:{}, which keeps a tile entity, so it does not wear",
            name,
            Integer.valueOf(meta));
        return true;
    }

    /**
     * How a data file's block names are found in this game: each name's id past {@link #dataBlockId}'s guards, and each
     * state's properties, so a selector naming them ({@code ns:name[half=bottom]}, read from 1.12.2 on) matches the
     * metadata values whose state has them.
     */
    private static SurfaceData.Names dataNames(final Set<String> excluded) {
        return new SurfaceData.Names() {

            @Override
            public int idOf(String blockName) {
                return dataBlockId(blockName, excluded);
            }

            @Override
            public boolean keepsStateAt(String blockName, int meta) {
                return dataValueKeepsState(blockName, meta);
            }

            @Override
            public Map<String, String> properties(int blockId, int meta) {
                return propertiesOf(Block.getBlockById(blockId), meta);
            }
        };
    }

    /**
     * The properties of the state a block takes at this metadata value, names and values as the game spells them in a
     * block-state string ({@code half=bottom}), sorted by name; null for no block. What a data file's
     * {@code [prop=value]} selector is matched against, here and by the looks a client reads.
     */
    public static Map<String, String> propertiesOf(Block block, int meta) {
        if (block == null) return null;
        net.minecraft.block.state.IBlockState state = stateOf(block, meta);
        Map<String, String> out = new java.util.TreeMap<String, String>();
        for (Map.Entry<net.minecraft.block.properties.IProperty<?>, Comparable<?>> each : state.getProperties()
            .entrySet()) {
            out.put(
                each.getKey()
                    .getName(),
                valueName(each.getKey(), each.getValue()));
        }
        return out;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String valueName(net.minecraft.block.properties.IProperty property, Comparable value) {
        return property.getName(value);
    }

    /** The block a packed state key belongs to, by its registry name lower-cased, or null. */
    private static String lowerNameOf(int packed) {
        String name = registryName(Block.getBlockById(packed >> 4));
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
    public static boolean isGroundCover(Block block, int meta) {
        if (block == null) return false;
        Set<Integer> current = groundCover;
        if (current.isEmpty()) return false;
        int id = Block.getIdFromBlock(block);
        if (id < 0) return false;
        return current.contains(Integer.valueOf(key(id, meta)));
    }

    /**
     * True when the cover at this state holds its ground instead of coming off it.
     *
     * <p>
     * Asked wherever a square's wear is about to be shown or stood on, which is often, so it is the
     * same one-lookup shape as {@link #isGroundCover} and returns on an empty-set check when
     * nothing in the pack holds anything.
     */
    public static boolean isGroundCoverHolder(Block block, int meta) {
        if (block == null) return false;
        Set<Integer> current = groundCoverHolds;
        if (current.isEmpty()) return false;
        int id = Block.getIdFromBlock(block);
        if (id < 0) return false;
        return current.contains(Integer.valueOf(key(id, meta)));
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
        if (block instanceof BlockSapling || block instanceof BlockFlower) return true;
        return Detection.contains(simple, "sapling", "seedling", "sprout_tree");
    }

    /** Builds the holding set out of the cover set, by detection and by hand. */
    private static Set<Integer> collectHolders(Set<Integer> cover, Set<String> detected,
        Map<Integer, String> fromData) {
        Set<String> excluded = parseNameSet(TrmtConfig.groundCoverHoldsExclude);
        Set<Integer> holds = new HashSet<Integer>();

        if (TrmtConfig.groundCoverAutoDetect) {
            for (Object candidate : Block.REGISTRY) {
                if (!(candidate instanceof Block)) continue;
                Block block = (Block) candidate;
                if (block == Blocks.AIR) continue;

                String name = registryName(block);
                if (name == null) continue;
                String lower = name.toLowerCase(Locale.ROOT);
                if (excluded.contains(lower)) continue;
                if (name.startsWith(Trmt.MODID + ":")) continue;
                if (!looksPlanted(block, lower.substring(lower.indexOf(':') + 1))) continue;

                int id = Block.getIdFromBlock(block);
                if (id < 0) continue;
                boolean any = false;
                for (int meta = 0; meta < 16; meta++) {
                    Integer at = Integer.valueOf(key(id, meta));
                    if (!cover.contains(at)) continue;
                    holds.add(at);
                    // Recorded only where no data file says whether it holds: the file's answer is never written in.
                    if (!fromData.containsKey(at)) any = true;
                }
                if (any) detected.add(name);
            }
        }

        Map<Integer, SurfaceFamily> listed = new HashMap<Integer, SurfaceFamily>();
        applyList(listed, excluded, TrmtConfig.groundCoverHoldsBlocks, SurfaceFamily.VEGETATION);
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
        int meta = packed & 0xF;
        if ((meta & 8) != 0) return null;
        int id = packed >> 4;
        Block block = Block.getBlockById(id);
        if (!(block instanceof BlockSapling)) return null;
        int marked = meta | 8;
        try {
            if (block.damageDropped(stateOf(block, marked)) != block.damageDropped(stateOf(block, meta))) return null;
        } catch (RuntimeException awkwardBlock) {
            // A block that cannot answer for a value it was never placed at is not one to guess about.
            return null;
        }
        return Integer.valueOf(key(id, marked));
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
     * {@link IPlantable} does the real work. It is Forge's own word for "this grows out of whatever
     * is underneath it", which is exactly the relationship being modelled, and it is what keeps
     * vines out: a vine is on the vine material and walks through, but it hangs off a wall and has
     * no opinion about the ground, so a path worn under one should not bring it down. A modded
     * tuft that never implemented it is missed, and that is the right way round to fail - a missed
     * one is a decoration that survives a path, where an over-reach is somebody's block destroyed.
     * The list under surfaces is there for anything detection turns out to be too shy about.
     */
    private static boolean looksLikeGroundCover(Block block) {
        Material material = block.getDefaultState()
            .getMaterial();
        if (material != Material.PLANTS && material != Material.VINE && material != Material.CACTUS) {
            return false;
        }
        if (block.getDefaultState()
            .isFullCube()) return false;
        // A value keeping a tile entity is left out where the values are taken, one by one, as detection leaves it.
        return block instanceof IPlantable;
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
        Map<Integer, String> fromData) {
        Set<String> excluded = parseNameSet(TrmtConfig.groundCoverExclude);
        Set<Integer> cover = new HashSet<Integer>();

        if (TrmtConfig.groundCoverAutoDetect) {
            for (Object candidate : Block.REGISTRY) {
                if (!(candidate instanceof Block)) continue;
                Block block = (Block) candidate;
                if (block == Blocks.AIR) continue;

                String name = registryName(block);
                if (name == null || excluded.contains(name.toLowerCase(Locale.ROOT))) continue;
                if (name.startsWith(Trmt.MODID + ":")) continue;
                if (!looksLikeGroundCover(block)) continue;

                int id = Block.getIdFromBlock(block);
                if (id < 0) continue;
                // All sixteen, for the reason detection claims all sixteen everywhere else: which
                // metadata values a block actually uses is not a question the registry answers. All
                // but a value keeping a tile entity, which detection leaves alone value by value.
                boolean any = false;
                for (int meta = 0; meta < 16; meta++) {
                    if (keepsItsOwnState(block, meta)) continue;
                    cover.add(Integer.valueOf(key(id, meta)));
                    any = true;
                }
                if (!any) continue;
                // Not written into the list where a data file already says it is cover (W3 T5).
                if (!SurfaceData.decidedAnywhere(fromData, id)) detected.add(name);
            }
        }

        // Hand-written additions, resolved by the same parser the family lists use. The family
        // handed to it is thrown away with the map: all that is wanted is the name-to-state
        // arithmetic and the tile-entity guard, which is what the resistance list does with it too.
        Map<Integer, SurfaceFamily> listed = new HashMap<Integer, SurfaceFamily>();
        applyList(listed, excluded, TrmtConfig.groundCoverBlocks, SurfaceFamily.VEGETATION);
        cover.addAll(listed.keySet());

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
        net.minecraft.util.ResourceLocation name = Block.REGISTRY.getNameForObject(block);
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
