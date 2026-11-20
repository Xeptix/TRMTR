package com.trmtgtnh.erosion;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.Client;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.DataShape;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * The one piece of this mod that answers for a block it did not place.
 *
 * <p>
 * Everywhere else, a worn position is a lie the client tells itself: the server's block array is
 * untouched and nothing outside rendering ever knows. Ruts you can walk down into cannot work that
 * way. The ground you stand on is decided by the server, from a block that is still ordinary
 * full-height grass, so something has to intervene in its collision.
 *
 * <h2>Where the intervening happens, and why it moved</h2>
 *
 * <p>
 * <strong>This is the one part of the port that is a redesign rather than a carry.</strong> The
 * 1.7.10 edition mixes into {@code Block.addCollisionBoxesToList}; the 1.12.2 edition does it from
 * outside the game's code entirely, through Forge's {@code GetCollisionBoxesEvent}, which hands over
 * the list of boxes about to be returned. <strong>Neither exists here.</strong> Collision became
 * {@code VoxelShape} at 1.13, and the event went with it.
 *
 * <p>
 * What replaced both is the state's own collision shape, which each loader mixes into and hands
 * here. That is a better place in one way and worse in another. Better, because it is the single
 * question everything asks - movement, the push-out that shoves an entity out of a block, path
 * finding, a piston checking what is in its way - where the 1.12.2 event sits on two of those paths
 * and the 1.7.10 mixin on one. The bug that cost that edition a second mixin at 0.9.216 - an item
 * thrown into a rut being flung out of it every tick, because the push-out asked by a road this hook
 * did not sit on - has nowhere to live here.
 *
 * <p>
 * Worse, because <em>everything</em> asks it. The 1.12.2 event fires once per collision query with a
 * list; this is asked once per block per query. So the fast path matters more here than there, and
 * it is the same fast path: a static read, then an identity lookup, and only a block that could ever
 * sink pays for a record lookup.
 *
 * <p>
 * What carried is everything else - the rules for which shape a position gets, and every reason
 * written beside them.
 */
public final class PhysicalDecay {

    /**
     * Whether collision follows the visuals at all. Read before anything else on the collision path,
     * so that turning this off costs one static field read per block tested.
     */
    private static volatile boolean active;

    /**
     * Blocks that belong to a family whose chain can ever reach a depth. Identity rather than
     * equality, because a block is its own identity and asking it for a hash would ask a mod's code;
     * replaced whole, never written in place, so a collision on another thread reads the old set or
     * the new one and never a half-built one.
     */
    private static volatile Set<Block> sinkable = Collections.emptySet();

    /** Blocks that rest on the ground rather than being it: snow layers and carpet. */
    private static volatile Set<Block> settling = Collections.emptySet();

    private PhysicalDecay() {}

    /** Recomputed whenever config is read, so the collision path never consults config itself. */
    public static void refresh() {
        active = TrmtConfig.physicalDecayCollides();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isSinkable(Block block) {
        return sinkable.contains(block);
    }

    public static boolean isSettling(Block block) {
        return settling.contains(block);
    }

    // ------------------------------------------------------------------
    // What each loader's mixin asks
    // ------------------------------------------------------------------

    /**
     * The shape this position really has, or null to leave the block's own alone.
     *
     * <p>
     * The one door both loaders come through, so neither can answer differently from the other. Null
     * rather than the block's own shape handed back, because a mixin that has nothing to say should
     * not cancel: there is a difference between "this is the shape" and "I have no opinion", and only
     * the second leaves every other mod's hook on this method working.
     *
     * <p>
     * Ordered by how often each test fails. The switch first, because it is a field read and turns
     * the whole thing off; then whether the thing asking is a level at all, because a block asked
     * about in a recipe preview or a structure template has no ground under it; then the two identity
     * lookups, which between them dismiss every block in the game but a handful.
     *
     * @param own what the block itself said, which the settling half moves rather than replaces
     */
    public static VoxelShape shapeAt(BlockGetter access, BlockPos pos, BlockState state, VoxelShape own) {
        if (!active || pos == null || state == null) return null;
        Level level = levelOf(access);
        if (level == null) return null;
        reached();

        Block block = state.getBlock();
        if (isSinkable(block)) {
            asked();
            VoxelShape worn = shapeOfWorn(level, pos.getX(), pos.getY(), pos.getZ(), state);
            if (worn != null) {
                answered();
                return worn;
            }
        }
        if (isSettling(block) && own != null && !own.isEmpty()) {
            double drop = groundDropUnder(level, pos.getX(), pos.getY(), pos.getZ());
            if (drop > 0.0D) return own.move(0.0D, -drop, 0.0D);
        }
        return null;
    }

    /**
     * What the block under a snow layer offers it to lie on: the block's own shape where the ground is worn, and
     * otherwise whatever the shape read answered, another mod's hook on it included.
     *
     * <p>
     * The 1.7.10 edition lets snow lie on any opaque block, worn or not, and draws it settled into the rut. This
     * version's snow asks instead whether the top of the shape below is whole, and the worn shape {@link #shapeAt}
     * hands every collision read says no on any square that has sunk - so until 0.9.220 a worn road could hold no
     * snow: snow never fell on it, and snow already lying there was taken away at the next update beside it. Found
     * by the second yard's snow photograph, where 1.7.10 and 1.12.2 laid a layer on every square and this edition
     * kept one. Asked by each loader's MixinSnowRestsOnWornGround in SnowLayerBlock.canSurvive.
     */
    public static VoxelShape restingShape(BlockState below, BlockGetter access, BlockPos pos, VoxelShape asked) {
        if (!active || below == null || pos == null || !isSinkable(below.getBlock())) return asked;
        Level level = levelOf(access);
        if (level == null || shapeOfWorn(level, pos.getX(), pos.getY(), pos.getZ(), below) == null) return asked;
        return below.getBlock()
            .getCollisionShape(below, access, pos, CollisionContext.empty());
    }

    /**
     * The snow layer's reads of the block below it, newest first, for the loaders that answer them without MixinExtras
     * (0.9.229): {@code MixinSnowRestsOnWornGround} notes the square straight after the read is made, then answers the
     * shape the read gave as the game hands it on. A stack, per thread: each note pushes and each answer takes the
     * newest, so a check run again inside another - by another mod's code at the same place - answers its own square and
     * leaves the outer one's where it was; the server and the client ask at once. Nothing of the game's runs between a
     * note and its answer, so a frame is left behind only if another mod's code there throws out of the method; every
     * later check notes before it answers and so takes its own frame, never that one, and the stack keeps at most
     * {@value #RESTING_READS_KEPT} frames.
     */
    private static final ThreadLocal<ArrayDeque<Object[]>> RESTING_READS = ThreadLocal.withInitial(ArrayDeque::new);

    static final int RESTING_READS_KEPT = 16;

    /** Notes the level and the square a snow layer's read below was made at, for the answer that follows it. */
    public static void noteRestingRead(BlockGetter access, BlockPos pos) {
        ArrayDeque<Object[]> reads = RESTING_READS.get();
        if (reads.size() >= RESTING_READS_KEPT) reads.removeLast();
        reads.push(new Object[] { access, pos });
    }

    /** The newest note on this thread, taken off the stack, or null where there is none. */
    static Object[] takeRestingRead() {
        return RESTING_READS.get().poll();
    }

    /**
     * {@link #restingShape} at the newest read noted on this thread, given the shape the read answered - another mod's,
     * where one redirects the read - taking the note off the stack. The block is asked of the noted square again, the
     * same square in the same tick, as the friction is (see {@code GhostFamily.ghostAt}). With no note, the shape as
     * given.
     */
    public static VoxelShape restingShapeOfNoted(VoxelShape asked) {
        Object[] read = takeRestingRead();
        if (!active || read == null) return asked;
        BlockGetter access = (BlockGetter) read[0];
        BlockPos pos = (BlockPos) read[1];
        return restingShape(access.getBlockState(pos), access, pos, asked);
    }

    /**
     * The level behind whatever the collision system happened to be holding.
     *
     * <p>
     * <strong>It is very often not the level.</strong> Movement at this version walks chunks rather
     * than the world - a collision sweep fetches the chunk once and asks every block in it from
     * there - so what a block's shape is asked through is a {@code LevelChunk}, and the first version
     * of this refused everything that was not a {@code Level} outright. That is a refusal of the
     * entire collision path, and the only sign of it was a rut you could see and could not stand in.
     *
     * <p>
     * A chunk knows its level, so both are answered. Anything else - a structure template, a recipe
     * preview, a mod's own scratch view of blocks - has no ground under it and gets nothing.
     */
    private static Level levelOf(BlockGetter access) {
        if (access instanceof Level) return (Level) access;
        if (access instanceof net.minecraft.world.level.chunk.LevelChunk) {
            return ((net.minecraft.world.level.chunk.LevelChunk) access).getLevel();
        }
        return null;
    }

    private static volatile boolean reachedSaid;

    private static volatile boolean askedSaid;

    private static volatile boolean answeredSaid;

    /**
     * Said once when the hook first reaches a block that could sink, and once when it first changes
     * one.
     *
     * <p>
     * Two lines rather than one, because the two failures they separate look identical from outside
     * and have nothing to do with each other. Neither line means the hook never attached - and a
     * mixin that does not attach is silent by design here, because an optional injection that cannot
     * find its method writes nothing. The first line without the second means the hook is in place
     * and no square has worn deep enough to have a hollow yet, which is the ordinary state of a new
     * world.
     */
    private static void reached() {
        if (reachedSaid) return;
        reachedSaid = true;
        Trmt.LOG.info(
            "The worn-collision hook is attached and calling through: collision={}, blocks that can "
                + "sink={}",
            Boolean.valueOf(active),
            Integer.valueOf(sinkable.size()));
    }

    private static volatile boolean deepSaid;

    private static void deep() {
        if (deepSaid) return;
        deepSaid = true;
        Trmt.LOG.info("A square has worn deep enough to have a hollow; working out its shape");
    }

    private static void asked() {
        if (askedSaid) return;
        askedSaid = true;
        Trmt.LOG.info("The worn-collision hook is in place: ground that can sink is being asked about");
    }

    private static void answered() {
        if (answeredSaid) return;
        answeredSaid = true;
        Trmt.LOG.info("Worn ground is now hollow to walk in as well as to look at");
    }

    // ------------------------------------------------------------------
    // How deep, and what shape
    // ------------------------------------------------------------------

    /**
     * How far the surface at this position has sunk, in sixteenths, or 0 for untouched ground.
     *
     * <p>
     * Reads the erosion store rather than the block, because the block is exactly what has not
     * changed. Returns 0 for anything unloaded, untracked or not yet visibly worn.
     */
    public static int sinkAt(Level world, int x, int y, int z) {
        // The master switch as well as the collision one. Switching the mod off clears every client's
        // ruts and sends no more, and ground that nobody can see is hollow must not be hollow: before
        // this, cows, dropped items and minecarts on a disabled server's roads sat half a block down
        // inside what every player saw as solid turf.
        if (!active || !TrmtConfig.enabled || world == null || world.isClientSide()) return 0;
        if (!com.trmtgtnh.util.Heights.holds(world, y)) return 0;

        ChunkErosionData data = ErosionStore.get()
            .getChunk(
                ErosionStore.get()
                    .indexOf(world),
                x >> 4,
                z >> 4);
        if (data == null || data.isEmpty()) return 0;

        ErosionEntry entry = data.get(ErosionKey.packWorld(x, y, z));
        if (entry == null || !entry.isVisible()) return 0;
        // Something planted here holds the ground level whatever the record says it has taken.
        if (GroundCover.holdsAt(world, x, y, z)) return 0;
        // A solid block standing on it holds it full for as long as it stands there (0.9.229): see GroundCover.roofedAt.
        if (GroundCover.roofedAt(world, x, y, z)) return 0;

        // By what class the block is, as the ghost decides it on the other side and as the 1.7.10 edition
        // decides it: a slab class is a slab, anything else whole. Until 0.9.220 this judged by thickness;
        // Xep chose the other edition's rule on 2026-10-08.
        // Through the block state's data shape too (W3 T6), so the server's depth is the one the client's ghost takes.
        return SurfaceShape.sunk(
            entry.getFamily(),
            entry.getSink(),
            com.trmtgtnh.util.Worlds.stateAt(world, x, y, z),
            true);
    }

    /**
     * How far the ground directly under a position has dropped, in block units.
     *
     * <p>
     * The one number both sides settle a block's footing from. The server has the record and reads
     * it; the client is asked through the Client seam, which hands back the very figure the renderer
     * moved the picture by - on a whole block in the visual mode that is the drawn depth, as the 1.7.10
     * edition's code has it (Xep, 2026-10-08). Footing does not follow it there: no collision moves in
     * the visual mode, so only the picture comes down.
     */
    public static double groundDropUnder(Level world, int x, int y, int z) {
        if (world == null || y <= com.trmtgtnh.util.Heights.bottom(world)) return 0.0D;
        // A covered stair never settles (0.9.228; Xep: "Covered stair never settles"). A worn stair is drawn and
        // stood on as the stair it covers and is never hollowed, so snow or a carpet on one has nothing to come down
        // into - and the two sides must give the same nought: the client draws it whole (Settling.dropFor asks the
        // same of the stair the ghost stands in for), and the server's collision depth for a stair would otherwise
        // lower the carpet's box into a step nobody sees sunk. Asked of the real block, which on the server is the
        // stair itself and on a client the stair stand-in, both of them stairs.
        if (SurfaceShape.of(com.trmtgtnh.util.Worlds.stateAt(world, x, y - 1, z)) == SurfaceShape.STAIR) return 0.0D;
        if (world.isClientSide()) return Client.settledDropUnder(x, y - 1, z);
        return sinkAt(world, x, y - 1, z) / 16.0D;
    }

    /**
     * The shape the server believes a worn position has, or null when it has none.
     *
     * <p>
     * A slab is the reason this cannot simply be a height: the half of the block it occupies decides
     * where the shape starts as well as where it ends, and a rut worn into one has to stop at the
     * bottom of the slab rather than at the bottom of the cell.
     */
    public static VoxelShape shapeOfWorn(Level world, int x, int y, int z, BlockState state) {
        int sink = sinkAt(world, x, y, z);
        if (sink <= 0) return null;

        deep();
        // A data file's boxes (W3 spec 2.4, T6), one by one with the walked ones sunk: before the stair and footing
        // refusals below, because the file says what shape the block is, and the client's ghost asks them first as
        // well. The game's own shapes are unions of boxes, so the shape carries every box at once.
        DataShape boxes = boxesOf(state);
        if (boxes != null) {
            VoxelShape all = Shapes.empty();
            for (double[] box : boxes.boxesSunk(sink)) {
                all = Shapes.or(all, Shapes.box(box[0], box[1], box[2], box[3], box[4], box[5]));
            }
            return all;
        }
        SurfaceShape shape = SurfaceShape.of(state);
        // A stair answers for its own collision and this must not take it over. Everything below
        // builds ONE box across the whole footprint, which is right for a slab and wrong for a stair
        // - a stair is three boxes. Refused rather than shaped: a stair states its own shape and
        // never has a height clamped onto it, so a worn stair was never meant to sink at all.
        if (shape == SurfaceShape.STAIR) {
            // A data file's stair over a block that is not one (W3 T6) is stood on as the straight stair the client's
            // ghost draws and collides as (SurfaceShape.stairStandIn), never sunk; a real stair keeps its own.
            BlockState standIn = SurfaceShape.stairStandIn(state);
            if (standIn == null) return null;
            try {
                return standIn.getShape(world, new BlockPos(x, y, z));
            } catch (RuntimeException awkwardBlock) {
                return null;
            }
        }
        // And the same refusal for anything narrower than the square it stands in: everything below
        // builds one box across the whole footprint, which a block occupying part of its square has
        // already declined to have. Handing back nothing puts it back in charge of its own collision,
        // which is where it was before this mod arrived.
        if (!fillsItsFootprint(state, world, x, y, z)) return null;

        // The 1.7.10 edition's boxAt, the same rule the ghost's solidBox gives the client: anything but a slab is a
        // full cell less the depth, from the top of the cell; a slab wears from its own top and stops at its floor.
        if (!shape.isPartial()) return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, SinkProfile.heightFor(sink), 1.0D);
        double floor = SurfaceShape.bottomOf(shape, state);
        double worn = Math.max(floor, SurfaceShape.topOf(shape, state) - sink / 16.0D);
        if (worn <= floor) return Shapes.empty();
        // In the block's own space rather than the world's, which is what a shape is at this version:
        // the game offsets one by the position when it uses it. Both older editions build the box in
        // world coordinates because theirs are, and that is the whole of the difference.
        return Shapes.box(0.0D, floor, 0.0D, 1.0D, worn, 1.0D);
    }

    /**
     * The boxes the server gives a worn position, in world space, or null when it gives none and the block answers for
     * itself: a data file's boxes one by one, the walked ones sunk (W3 spec 2.4, T6), and otherwise the boxes of
     * {@link #shapeOfWorn}'s one shape. The client's ghost builds the same through {@code BlockGhost.boxesAt};
     * {@link #shapeAt} hands every collision read the same boxes as one shape.
     */
    public static net.minecraft.world.phys.AABB[] boxesAt(Level world, int x, int y, int z) {
        int sink = sinkAt(world, x, y, z);
        if (sink <= 0) return null;
        BlockState state = com.trmtgtnh.util.Worlds.stateAt(world, x, y, z);
        DataShape boxes = boxesOf(state);
        if (boxes == null) {
            VoxelShape one = shapeOfWorn(world, x, y, z, state);
            if (one == null || one.isEmpty()) return null;
            java.util.List<net.minecraft.world.phys.AABB> parts = one.toAabbs();
            net.minecraft.world.phys.AABB[] out = new net.minecraft.world.phys.AABB[parts.size()];
            for (int i = 0; i < out.length; i++) out[i] = parts.get(i)
                .move(x, y, z);
            return out;
        }
        double[][] sunk = boxes.boxesSunk(sink);
        net.minecraft.world.phys.AABB[] out = new net.minecraft.world.phys.AABB[sunk.length];
        for (int i = 0; i < sunk.length; i++) {
            double[] box = sunk[i];
            out[i] = new net.minecraft.world.phys.AABB(
                x + box[0],
                y + box[1],
                z + box[2],
                x + box[3],
                y + box[4],
                z + box[5]);
        }
        return out;
    }

    /** The boxes a data file gives a block state, or null where its shape is anything but boxes. */
    private static DataShape boxesOf(BlockState state) {
        DataShape data = SurfaceShape.dataOf(state);
        return data != null && data.kind == DataShape.BOXES ? data : null;
    }

    /**
     * Whether a block's own collision spans the whole of its square across - only the footprint, never the height, a
     * shorter block being what wear produces.
     *
     * <p>
     * What the 1.7.10 edition asks of {@code GhostInherit.ownFootingAt}. A block that stops short of its square's edges
     * has a shape of its own, and a worn box built edge to edge would widen it; one with no collision at all keeps none,
     * because a whole cell over a cloud turned a block you fall through into one you stand on. Its collision, not its
     * outline, and asked of the block rather than the state, so this mod's own hook on the state does not answer for
     * it: until 0.9.222 the outline was asked, so a sunk block whose outline filled its square and whose collision did
     * not was stood on by the server and fallen through by the client, which asks the collision (spec CS20).
     */
    static boolean fillsItsFootprint(BlockState state, BlockGetter world, int x, int y, int z) {
        try {
            VoxelShape own = state.getBlock()
                .getCollisionShape(state, world, new BlockPos(x, y, z), CollisionContext.empty());
            if (own == null || own.isEmpty()) return false;
            net.minecraft.world.phys.AABB bounds = own.bounds();
            return bounds.minX <= 0.0D && bounds.minZ <= 0.0D && bounds.maxX >= 1.0D && bounds.maxZ >= 1.0D;
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Which blocks are worth asking about
    // ------------------------------------------------------------------

    /**
     * Stamps every block with whether it belongs to a family that can sink, so the collision path can
     * dismiss the overwhelming majority of blocks with one lookup instead of a registry search.
     * Called after surfaces are resolved and after any config reload.
     */
    public static void markSinkableBlocks() {
        refresh();
        markSettlingBlocks();
        if (!TrmtConfig.physicalDecayShows()) {
            sinkable = Collections.emptySet();
            return;
        }
        Set<Block> found = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        for (SurfaceRegistry.SurfaceState state : SurfaceRegistry.texturableStates()) {
            if (canEverSink(state.family)) found.add(state.block);
        }
        // A block whose own family never sinks may have states a data file sets apart that do.
        for (SurfaceRegistry.SurfaceState state : SurfaceRegistry.setApartSurfaces()) {
            if (canEverSink(state.family)) found.add(state.block);
        }
        sinkable = found;
        Trmt.LOG.info("{} block(s) can sink under wear", Integer.valueOf(found.size()));
    }

    /**
     * Stamps the blocks that rest on the ground rather than being it.
     *
     * <p>
     * On both sides, which is the whole reason it lives here rather than beside the drawing. The set
     * decides whether a block's footing comes down with its picture, and a set built on one side only
     * is two machines disagreeing about where the floor is - the client predicting a step down onto
     * settled snow and the server shoving it back up, every tick, on every snowfield.
     *
     * <p>
     * By material, which for these two names exactly the blocks meant: every snow layer in the pack
     * is on snow's material and every carpet is on carpet's, because both are vanilla materials that
     * a mod adding one of these will reuse rather than invent. A block that fills its own space is
     * not lying on anything and is refused whatever it is made of.
     */
    public static void markSettlingBlocks() {
        if (!TrmtConfig.enabled || !TrmtConfig.settleOnWornGround) {
            settling = Collections.emptySet();
            return;
        }
        Set<Block> found = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (restsOnTheGround(block)) found.add(block);
        }
        settling = found;
    }

    private static boolean restsOnTheGround(Block block) {
        try {
            BlockState state = block.defaultBlockState();
            com.trmtgtnh.surface.MaterialKind material = com.trmtgtnh.surface.MaterialKind.of(state);
            // TOP_SNOW is the layer; CLOTH_DECORATION is what carpet is made of here. Both older
            // editions name them SNOW and CARPET, which are this version's names for other things -
            // see the note in the rename table about CRAFTED_SNOW.
            if (material != com.trmtgtnh.surface.MaterialKind.TOP_SNOW && material != com.trmtgtnh.surface.MaterialKind.CLOTH_DECORATION) return false;
            // Asked of nothing rather than of a world, because this is a sweep of the registry and
            // there is no position to ask about. A block whose shape depends on where it is answers
            // for its default here, which is what both older editions get from the default state too.
            return !Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    /**
     * Whether a surface of this family can ever end up sunk, counting where its wear chain leads.
     * Grass never sinks as grass, but a grass path that has worn through to earth is standing on the
     * dirt family's depth.
     */
    private static boolean canEverSink(SurfaceFamily base) {
        int length = ErosionChain.length(base);
        for (int i = 0; i < length; i++) {
            SurfaceFamily appearance = ErosionChain.familyAt(base, i);
            if (appearance != null && ErosionChain.sinkAt(base, i) > 0) {
                return true;
            }
        }
        return false;
    }
}
