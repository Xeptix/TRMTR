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
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockFrostedIce;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockGravel;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockSand;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.IPlantable;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;

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

        final boolean holdsSwitch;

        final long fingerprint;

        Held(Map<Integer, SurfaceFamily> table, Set<Integer> cover, Set<Integer> holds, boolean holdsSwitch,
            long fingerprint) {
            this.table = table;
            this.cover = cover;
            this.holds = holds;
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
        byte[] made = SurfaceTableCodec.encode(ordinals(table), groundCover, groundCoverHolds);
        encoded = made;
        encodedFor = wanted;
        return made;
    }

    /**
     * Puts a server's table in use for the visit. Client thread.
     *
     * @return false, holding nothing, when the table names a family this build does not have
     */
    public static boolean holdServerTable(SurfaceTableCodec.Table decoded, boolean holdsSwitch, long fingerprint) {
        SurfaceFamily[] all = SurfaceFamily.values();
        Map<Integer, SurfaceFamily> families = new HashMap<Integer, SurfaceFamily>(decoded.families.size() * 2);
        for (Map.Entry<Integer, Integer> entry : decoded.families.entrySet()) {
            int ordinal = entry.getValue()
                .intValue();
            if (ordinal < 0 || ordinal >= all.length) return false;
            families.put(entry.getKey(), all[ordinal]);
        }
        Held visiting = new Held(
            Collections.unmodifiableMap(families),
            decoded.cover,
            decoded.holds,
            holdsSwitch,
            fingerprint);
        held = visiting;
        publish(visiting.table, visiting.cover, visiting.holds);
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
    private static void publish(Map<Integer, SurfaceFamily> families, Set<Integer> cover, Set<Integer> holds) {
        table = families;
        texturable = collectTexturableStates(families);
        groundCover = cover;
        groundCoverHolds = holds;
        Held visiting = held;
        publishedFingerprint = visiting != null ? visiting.fingerprint : ownFingerprint;
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
     * Rebuilds the table from the current block registry and config. Safe to call again at
     * runtime - {@code /trmt reload} does exactly that.
     */
    public static void resolve() {
        Map<Integer, SurfaceFamily> built = new HashMap<Integer, SurfaceFamily>();
        Map<SurfaceFamily, Set<String>> detected = new EnumMap<SurfaceFamily, Set<String>>(SurfaceFamily.class);
        Set<String> excluded = parseNameSet(TrmtConfig.surfaceExclude);

        if (TrmtConfig.surfaceAutoDetect) {
            autoDetect(built, excluded, detected);
        }

        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings settings = TrmtConfig.family(family);
            if (settings == null) continue;
            applyList(built, excluded, settings.extra, family);
        }

        dropDisabledFamilies(built);

        resistance = collectResistance(excluded);

        Set<String> cover = new java.util.TreeSet<String>();
        Set<Integer> builtCover = collectGroundCover(built, cover);
        Set<String> holds = new java.util.TreeSet<String>();
        Set<Integer> builtHolds = collectHolders(builtCover, holds);
        ownFingerprint = SurfaceTableCodec
            .fingerprint(ordinals(built), builtCover, builtHolds, TrmtConfig.groundCoverHolds);

        // A server's table in use for the visit is published again rather than this side's own, so no
        // rebuild made while connected - a reload, the config screen, a family changing hands - can
        // quietly put back a table the server has already said is not the one in use. Detection still
        // runs and is still written down below; only what is published waits for the visit to end.
        Held visiting = held;
        if (visiting != null) {
            publish(visiting.table, visiting.cover, visiting.holds);
        } else {
            publish(Collections.unmodifiableMap(built), builtCover, builtHolds);
        }

        // Detection is only useful if you can see and override what it decided, so what it
        // found is written back into the per-family block lists. Turn autoDetect off and those
        // lists become the whole story, editable by hand.
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
        Map<SurfaceFamily, Set<String>> detected) {
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

            SurfaceFamily whole = classifyMaterial(block, name);
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
                byMeta[meta] = gateShape(block, refineSlab(block, name, whole, meta));
                if (byMeta[meta] != byMeta[0]) split = true;
            }

            for (int meta = 0; meta < 16; meta++) {
                SurfaceFamily family = byMeta[meta];
                if (family == null) continue;

                FamilySettings settings = TrmtConfig.family(family);
                if (settings == null || !settings.autoDetect) continue;

                out.put(Integer.valueOf(key(id, meta)), family);

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
     * Classifies one block.
     *
     * <p>
     * Material leads, name only excludes. An earlier version required the name to say "grass"
     * or "dirt" as well, and that quietly missed things: Biomes O' Plenty's mud registers as
     * {@code mud} on the <em>sand</em> material, so it matched neither the dirt rule nor the
     * sand one. Asking what a block is made of and then ruling out the near-misses by name
     * catches modded terrain whatever its author decided to call it.
     *
     * <p>
     * Leading with material is only safe because detection now writes what it decided into the
     * config. Anything it over-reaches on is visible in a list and one edit away, which is a far
     * better failure than terrain that silently never wears.
     */
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
     * Asked of every metadata because a block may carry one for some subtypes and not others,
     * and asked defensively because it reaches arbitrary code in a 233-mod pack.
     */
    private static boolean keepsItsOwnState(Block block) {
        if (block instanceof ITileEntityProvider) return true;
        for (int meta = 0; meta < 16; meta++) {
            try {
                if (block.hasTileEntity(stateOf(block, meta))) return true;
            } catch (RuntimeException awkwardBlock) {
                // A block that cannot answer is a block to leave alone.
                return true;
            }
        }
        return false;
    }

    /**
     * Which family this block belongs to, or null for one that does not wear.
     *
     * <p>
     * The shape is asked last rather than first. What a slab is made of is the same question as
     * what a whole block of it is made of, and it is answered by the same rules; whether a family
     * lets its slabs wear is a separate decision that cannot be taken until the family is known.
     */
    private static SurfaceFamily classify(Block block, String registryName) {
        return gateShape(block, classifyMaterial(block, registryName));
    }

    /**
     * Which family one metadata value of a slab belongs to.
     *
     * <p>
     * A slab block is several materials wearing one name. Vanilla packs stone, sandstone, wood,
     * cobble, brick, stone brick, nether brick and quartz into a single id, and the doubled form -
     * two slabs stacked, which is a different block again and a full cube - packs the same eight.
     * Classifying by the block's registry name therefore answers for all of them at once and gets
     * seven of the eight wrong: a cobblestone double slab came out as stone, because "stone" is in
     * the name and cobble is not.
     *
     * <p>
     * {@code BlockSlab} already knows the answer and will say so per metadata value. What it
     * returns is an unlocalized name - "tile.stoneSlab.cobble" - so the last segment is the word
     * that says what the thing is made of, and the shape words come off it for the same reason
     * they come off a registry name. That word then goes through the ordinary rules, so a slab is
     * classified by exactly what a whole block of the same stuff would be.
     *
     * <p>
     * The upper-half bit is masked off first: it says which half of the cell a slab occupies and
     * has nothing to do with what it is made of, and a doubled slab does not use it at all.
     */
    private static SurfaceFamily refineSlab(Block block, String registryName, SurfaceFamily whole, int meta) {
        if (!(block instanceof BlockSlab)) return whole;

        String subtype;
        try {
            subtype = ((BlockSlab) block).getTranslationKey(meta & 7);
        } catch (RuntimeException awkwardSlab) {
            // A slab that cannot say what it is is left as whatever its name suggested, which is
            // what every slab got before this existed.
            return whole;
        }
        if (subtype == null || subtype.isEmpty()) return whole;

        String word = withoutShapeWords(
            subtype.substring(subtype.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT));
        if (word.isEmpty()) return whole;

        // Whatever the word says, including nothing. A slab of stone brick is stone brick, and a
        // whole block of stone brick is decorative stonework this mod leaves alone, so a slab of it
        // is left alone too - falling back on the block's name here would have wandered off in the
        // other direction and worn it as plain stone.
        return classifyMaterial(block, "minecraft:" + word);
    }

    /**
     * Whether a family lets this block's shape wear, once the family is known.
     *
     * <p>
     * Separate from the classification because the two questions are separate: what a block is
     * made of does not depend on what shape it was cut into, and whether that shape wears is the
     * family's decision rather than the block's.
     */
    private static SurfaceFamily gateShape(Block block, SurfaceFamily family) {
        if (family == null) return null;
        SurfaceShape shape = SurfaceShape.of(block);
        if (!shape.isPartial()) return family;

        FamilySettings settings = TrmtConfig.family(family);
        if (settings == null) return null;
        if (shape == SurfaceShape.SLAB && !settings.slabs) return null;
        if (shape == SurfaceShape.STAIR && !settings.stairs) return null;
        return family;
    }

    private static SurfaceFamily classifyMaterial(Block block, String registryName) {
        String lower = registryName.toLowerCase(Locale.ROOT);
        String simple = lower.substring(lower.indexOf(':') + 1);
        Material material = block.getDefaultState()
            .getMaterial();
        SurfaceShape shape = SurfaceShape.of(block);
        // A slab is named for the block it was cut from with the shape tacked on, and every rule
        // below that reads a name wants the material rather than the shape: "stone_slab" must
        // reach the same answer "stone" does, and "sandstone_slab" must be refused for the same
        // reason sandstone is. Stripping the word is what makes one set of rules serve both.
        //
        // Asked of the class rather than of the shape, because a doubled slab is a full cube and
        // still a slab by name - and "slab" is on the decorative-stonework list, so leaving the
        // word on refused every doubled slab in the game before its metadata was ever consulted.
        if (shape.isPartial() || block instanceof BlockSlab || block instanceof BlockStairs) {
            simple = withoutShapeWords(simple);
        }

        if (block instanceof BlockLeaves || material == Material.LEAVES) return SurfaceFamily.LEAVES;

        if ((material == Material.PLANTS || material == Material.VINE) && !block.getDefaultState()
            .isFullCube()) {
            return SurfaceFamily.VEGETATION;
        }

        // Anything that is not a full opaque cube cannot carry a path overlay: a slab or a
        // fence would show the ghost block's geometry rather than its own. This is also what
        // keeps farmland and grass paths out, both of which stand a pixel short.
        // Ice is the exception to opacity, not to shape: it fills its space exactly, it is
        // simply clear. Asking for a full cube by sight would refuse every frozen surface in
        // the game, so this asks whether the block fills its space instead.
        boolean clearButSolid = material == Material.ICE || material == Material.PACKED_ICE;
        // And a block that says outright it fills its whole cell while saying it is no opaque cube
        // is the same exception by its own word. Chisel's waterstone is the one this is for: GTNH's
        // Chisel on 1.7.10 makes it a plain opaque cube, so that edition wears it, while Chisel for
        // 1.12.2 builds it opaque(false) - its water shows through a carved shell drawn in the pass
        // that blends - so it answers no to both questions below and yes only to isFullBlock, and was
        // never detected at all until 2026-10-08. Chisel's ice is built the same way and was missed
        // for the same reason, ice by material or not, where the other edition wears chisel:ice and
        // chisel:ice_pillar. Vanilla's isFullBlock is its opacity, set from the same answer when a
        // block is made, so no vanilla block moves.
        boolean wholeButSeenThrough = block.getDefaultState()
            .isFullBlock() && !block.isOpaqueCube(block.getDefaultState());
        // A slab is the one thing that is not a full cube and is still plainly ground: you walk on
        // it, and the top of it is the same sixteen by sixteen picture the block it was cut from
        // has. Whether it counts is the family's own decision, taken once the family is known, so
        // the shape is let past here and refused further down.
        if (!shape.isPartial() && !wholeButSeenThrough
            && (!block.getDefaultState()
                .isFullCube() || (!block.isOpaqueCube(block.getDefaultState()) && !clearButSolid))) {
            return null;
        }

        if (keepsItsOwnState(block)) return null;

        // Extending the vanilla class is the strongest signal there is, and costs one check.
        if (block instanceof BlockGrass) return SurfaceFamily.GRASS;
        if (block instanceof BlockDirt) return SurfaceFamily.DIRT;
        if (block instanceof BlockGravel) return SurfaceFamily.GRAVEL;
        if (block instanceof BlockSand) return SurfaceFamily.SAND;

        if (isWorkedGround(simple)) return null;

        if (material == Material.GRASS) return SurfaceFamily.GRASS;
        if (material == Material.GROUND) return SurfaceFamily.DIRT;

        // Snow and ice are led by material rather than by name, and for once that is the safe
        // way round: these materials name exactly the blocks meant, where "ice" as a word turns
        // up inside pumice, lattice and half the pack's chemistry.
        if (material == Material.CRAFTED_SNOW || material == Material.SNOW) return SurfaceFamily.SNOW;
        // Except frosted ice, which is ice by material and not ground at all: Frost Walker lays it and
        // the game melts it again within seconds. Counted, it put a platform of it in every
        // demonstrate yard, which melted and poured off the edge - found by the 0.9.219 photograph
        // pass, as a waterfall the 1.7.10 yard does not have. That edition has no such block, so
        // leaving it out is what its rule, the ground of frozen water, means here.
        if (neverGround(block)) return null;
        if (material == Material.ICE || material == Material.PACKED_ICE) return SurfaceFamily.ICE;

        if (material == Material.SAND) {
            if (contains(simple, "soul")) return null;
            if (contains(simple, "gravel")) return SurfaceFamily.GRAVEL;
            // Mud, silt and clay are earth that happens to be built on sand's material; they
            // should wear like ground rather than like a beach.
            if (contains(simple, "mud", "loam", "silt", "clay", "earth")) return SurfaceFamily.DIRT;
            if (contains(simple, "stone")) return null; // sandstone and its many cousins
            return SurfaceFamily.SAND;
        }

        // The nether and the end are checked before the decorative-stonework filter rather
        // than after, because their brick and tile forms are simply the ground of those places
        // rather than something a builder laid, and that filter would throw them out on the
        // word "brick" alone. Ores are still excluded: a road worn across an ore seam should
        // wear the seam's surroundings, not the seam.
        if (material == Material.ROCK && !contains(simple, "ore")) {
            if (contains(simple, "nether") && !contains(simple, "quartz")) return SurfaceFamily.NETHER;
            if (contains(simple, "endstone", "end_stone", "purpur", "endbrick", "end_brick")) {
                return SurfaceFamily.END;
            }
        }

        // Rock stays name-led: the material covers ores, casings and half the decorative blocks
        // in the pack, so leading with it here would sweep up far more than ground.
        if (material == Material.ROCK && !isDecorativeStonework(simple)) {
            if (contains(simple, "cobble")) return SurfaceFamily.COBBLE;
            if (contains(simple, "stone", "granite", "diorite", "andesite", "marble", "basalt", "limestone", "slate")) {
                return SurfaceFamily.STONE;
            }
        }

        return null;
    }

    /**
     * Ground somebody has already made something of, which should stay as they left it.
     *
     * <p>
     * The bale entries are not tilled ground at all - a hay bale is built on the grass material,
     * so leading with material sweeps it in as turf. It is a stored crop sitting in a barn, not
     * a field anyone walks a path across.
     */
    /**
     * A shaped block's name with the shape taken off it.
     *
     * <p>
     * Only ever applied to a block whose class already said what shape it is, so this is not
     * guessing from a name - it is removing a word the name is known to contain. That matters,
     * because "step" and "slab" turn up inside plenty of blocks that are neither.
     */
    private static String withoutShapeWords(String simple) {
        String out = simple;
        for (String word : SHAPE_WORDS) {
            if (out.endsWith(word)) {
                out = out.substring(0, out.length() - word.length());
                break;
            }
        }
        while (out.endsWith("_") || out.endsWith(".")) out = out.substring(0, out.length() - 1);
        // And the front, because two slabs stacked are a block of their own and vanilla names it
        // by saying so: double_stone_slab has to reach the same answer stone does.
        if (out.startsWith("double_")) out = out.substring("double_".length());
        else if (out.startsWith("double")) out = out.substring("double".length());
        while (out.startsWith("_") || out.startsWith(".")) out = out.substring(1);
        return out.isEmpty() ? simple : out;
    }

    /** Longest first, so "_slabs" is not left with a trailing s by the shorter match. */
    private static final String[] SHAPE_WORDS = { "_halfslab", "halfslab", "_slabs", "_slab", "slabs", "slab",
        "_stairs", "_stair", "stairs", "stair", "_steps", "_step", "steps" };

    private static boolean isWorkedGround(String simple) {
        return contains(
            simple,
            "path",
            "farmland",
            "tilled",
            "soil",
            "crop",
            "garden",
            "planter",
            "hay",
            "bale",
            "thatch",
            "straw");
    }

    /**
     * Filters out worked stone and everything wearing a stone material for other reasons.
     * GregTech alone contributes a great many rock-material blocks that are machinery,
     * casings or ores rather than ground you could wear a track into.
     */
    private static boolean isDecorativeStonework(String simple) {
        return contains(
            simple,
            "ore",
            "brick",
            "casing",
            "machine",
            "wall",
            "stair",
            "slab",
            "chiseled",
            "carved",
            "pillar",
            "sandstone",
            "redstone",
            "lodestone",
            "grindstone",
            "gemstone",
            "furnace",
            "block_of",
            "polished",
            "tile",
            "pressure",
            "button",
            "monster",
            "spawner",
            "obsidian",
            "netherrack",
            "endstone",
            "end_stone");
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

            if (meta == ANY_META) {
                for (int m = 0; m < 16; m++) {
                    out.put(Integer.valueOf(key(id, m)), family);
                }
            } else {
                out.put(Integer.valueOf(key(id, meta & 0xF)), family);
            }
        }
    }

    private static Set<String> parseNameSet(String[] entries) {
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

    private static Map<Integer, Float> collectResistance(Set<String> excluded) {
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
        return Collections.unmodifiableMap(built);
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
        return contains(simple, "sapling", "seedling", "sprout_tree");
    }

    /** Builds the holding set out of the cover set, by detection and by hand. */
    private static Set<Integer> collectHolders(Set<Integer> cover, Set<String> detected) {
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
                    any = true;
                }
                if (any) detected.add(name);
            }
        }

        Map<Integer, SurfaceFamily> listed = new HashMap<Integer, SurfaceFamily>();
        applyList(listed, excluded, TrmtConfig.groundCoverHoldsBlocks, SurfaceFamily.VEGETATION);
        for (Integer at : listed.keySet()) {
            if (!cover.contains(at)) continue;
            holds.add(at);
            Integer ready = growthTwin(at.intValue());
            if (ready != null) holds.add(ready);
        }

        return Collections.unmodifiableSet(holds);
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
        if (!(block instanceof IPlantable)) return false;
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
    private static Set<Integer> collectGroundCover(Map<Integer, SurfaceFamily> built, Set<String> detected) {
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
                // metadata values a block actually uses is not a question the registry answers.
                for (int meta = 0; meta < 16; meta++) {
                    cover.add(Integer.valueOf(key(id, meta)));
                }
                detected.add(name);
            }
        }

        // Hand-written additions, resolved by the same parser the family lists use. The family
        // handed to it is thrown away with the map: all that is wanted is the name-to-state
        // arithmetic and the tile-entity guard, which is what the resistance list does with it too.
        Map<Integer, SurfaceFamily> listed = new HashMap<Integer, SurfaceFamily>();
        applyList(listed, excluded, TrmtConfig.groundCoverBlocks, SurfaceFamily.VEGETATION);
        cover.addAll(listed.keySet());

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
