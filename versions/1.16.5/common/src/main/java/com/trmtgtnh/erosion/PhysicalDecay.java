package com.trmtgtnh.erosion;

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
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.Client;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
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
        if (y < 0 || y > 255) return 0;

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

        // By what class the block is, as the ghost decides it on the other side and as the 1.7.10 edition
        // decides it: a slab class is a slab, anything else whole. Until 0.9.220 this judged by thickness;
        // Xep chose the other edition's rule on 2026-10-08.
        return SinkProfile.collides(
            entry.getFamily(),
            entry.getSink(),
            SurfaceShape.of(com.trmtgtnh.util.Worlds.stateAt(world, x, y, z)));
    }

    /**
     * How far the ground directly under a position has dropped, in block units.
     *
     * <p>
     * The one number both sides settle a block's footing from. The server has the record and reads
     * it; the client is asked through the Client seam, which hands back the very figure the renderer
     * moved the picture by. Derived from the collision depth rather than the drawn one on purpose: in
     * visual mode the ground has not really moved, so nothing resting on it should move either.
     */
    public static double groundDropUnder(Level world, int x, int y, int z) {
        if (world == null || y <= 0) return 0.0D;
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
        SurfaceShape shape = SurfaceShape.of(state);
        // A stair answers for its own collision and this must not take it over. Everything below
        // builds ONE box across the whole footprint, which is right for a slab and wrong for a stair
        // - a stair is three boxes. Refused rather than shaped: a stair states its own shape and
        // never has a height clamped onto it, so a worn stair was never meant to sink at all.
        if (shape == SurfaceShape.STAIR) return null;
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
     * Whether a block's own outline spans the whole of its square across.
     *
     * <p>
     * What the 1.7.10 edition asks of {@code GhostInherit.ownFootingAt}, which this edition does not
     * have yet. The question is the same one: a block that stops short of its square's edges has a
     * shape of its own, and a worn box built edge to edge would widen it.
     */
    private static boolean fillsItsFootprint(BlockState state, Level world, int x, int y, int z) {
        try {
            net.minecraft.world.phys.AABB own = state.getShape(world, new BlockPos(x, y, z))
                .bounds();
            return own != null && own.minX <= 0.0D && own.minZ <= 0.0D && own.maxX >= 1.0D && own.maxZ >= 1.0D;
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
        for (Block block : Registry.BLOCK) {
            if (restsOnTheGround(block)) found.add(block);
        }
        settling = found;
    }

    private static boolean restsOnTheGround(Block block) {
        try {
            BlockState state = block.defaultBlockState();
            Material material = state.getMaterial();
            // TOP_SNOW is the layer; CLOTH_DECORATION is what carpet is made of here. Both older
            // editions name them SNOW and CARPET, which are this version's names for other things -
            // see the note in the rename table about CRAFTED_SNOW.
            if (material != Material.TOP_SNOW && material != Material.CLOTH_DECORATION) return false;
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
