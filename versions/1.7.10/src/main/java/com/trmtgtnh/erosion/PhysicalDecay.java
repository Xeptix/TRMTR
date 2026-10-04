package com.trmtgtnh.erosion;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.GhostInherit;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * The one piece of this mod that answers for a block it did not place.
 *
 * <p>
 * Everywhere else, a worn position is a lie the client tells itself: the server's block array
 * is untouched and nothing outside rendering ever knows. Ruts you can walk down into cannot
 * work that way. The ground you stand on is decided by the server, from a block that is still
 * ordinary full-height grass, so something has to intervene in its collision — see
 * {@code MixinBlockCollision}.
 *
 * <p>
 * That intervention sits on the hottest path in the game: collision runs for every moving
 * entity, every tick, across every block its bounding box sweeps. So the question this class
 * answers has to be almost free to ask, and it is — a static boolean, then a flag stamped onto
 * the block instance itself when surfaces were resolved. Only a block that could ever sink
 * pays for a position lookup, and only when something is standing on it.
 */
public final class PhysicalDecay {

    /**
     * Whether collision follows the visuals at all. Read before anything else on the collision
     * path, so that turning this off costs one static field read per block tested.
     */
    private static volatile boolean active;

    private PhysicalDecay() {}

    /** Recomputed whenever config is read, so the collision path never consults config itself. */
    public static void refresh() {
        active = TrmtConfig.physicalDecayCollides();
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * How far the surface at this position has sunk, in sixteenths, or 0 for untouched ground.
     *
     * <p>
     * Reads the erosion store rather than the block, because the block is exactly what has not
     * changed. Returns 0 for anything unloaded, untracked or not yet visibly worn.
     */
    public static int sinkAt(World world, int x, int y, int z) {
        // The master switch as well as the collision one. Switching the mod off clears every
        // client's ruts and sends no more, and ground that nobody can see is hollow must not be
        // hollow: before this, cows, dropped items and minecarts on a disabled server's roads sat
        // half a block down inside what every player saw as solid turf.
        if (!active || !TrmtConfig.enabled || world == null || world.isRemote) return 0;
        if (y < 0 || y > 255) return 0;

        ChunkErosionData data = ErosionStore.get()
            .getChunk(world.provider.dimensionId, x >> 4, z >> 4);
        if (data == null || data.isEmpty()) return 0;

        ErosionEntry entry = data.get(ErosionKey.packWorld(x, y, z));
        if (entry == null || !entry.isVisible()) return 0;
        // Something planted here holds the ground level whatever the record says it has taken.
        if (GroundCover.holdsAt(world, x, y, z)) return 0;

        return SinkProfile.collides(entry.getFamily(), entry.getSink(), SurfaceShape.of(world.getBlock(x, y, z)));
    }

    /**
     * How far the ground directly under a position has dropped, in block units.
     *
     * <p>
     * The one number both sides settle a block's footing from. The server has the record and reads
     * it; the client is asked through the proxy, which hands back the very figure the renderer moved
     * the picture by. Derived from the collision depth rather than the drawn one on purpose: in
     * visual mode the ground has not really moved, so nothing resting on it should move either.
     */
    public static double groundDropUnder(World world, int x, int y, int z) {
        if (world == null || y <= 0) return 0.0D;
        if (world.isRemote) return Trmt.proxy.settledDropUnder(x, y - 1, z);
        return sinkAt(world, x, y - 1, z) / 16.0D;
    }

    /**
     * The box a block resting on worn ground should be held up by, or null when the ground under it
     * has not moved.
     *
     * <p>
     * Snow and carpet are drawn down into the rut beneath them so they do not hang in the air over
     * it, and for a long time that was all that happened: the picture came down and the footing
     * stayed where it was. A snow layer keeps a collision box whatever its depth - vanilla builds
     * one by hand, and even at a single layer that is a box of no height sitting on the cell floor,
     * which is enough to stop anything falling onto it. So a player crossing a snowed-over road
     * stood at the height the road had before it wore, looking down at snow drawn half a block
     * below their feet.
     *
     * <p>
     * Moved rather than capped. The honest cap - the gap between what a course of snow draws and
     * what it collides with - is two pixels at every depth, and nothing at all for carpet, which
     * would leave the feature in name only. Shifting the box by the same figure the picture moves by
     * keeps both sides on one number and costs one field read on the collision path.
     */
    public static AxisAlignedBB settledBoxAt(World world, int x, int y, int z) {
        double drop = groundDropUnder(world, x, y, z);
        if (drop <= 0.0D) return null;

        Block block = world.getBlock(x, y, z);
        AxisAlignedBB own = block.getCollisionBoundingBoxFromPool(world, x, y, z);
        if (own == null) return null;
        return AxisAlignedBB.getBoundingBox(own.minX, own.minY - drop, own.minZ, own.maxX, own.maxY - drop, own.maxZ);
    }

    /**
     * The shape this mod has given a position, whichever of the two ways it gave it, or null where
     * it has given none.
     *
     * <p>
     * The same pair of questions the collision hook asks, reached from the world rather than from
     * the block, for a caller that is handed three coordinates and nothing else. The hook keeps its
     * own path rather than calling this: it runs for every moving entity over every block its box
     * sweeps, and the two flags it reads off the block it is already standing in are free where
     * this costs a lookup. What must not drift is which of the two boxes a position gets, so both
     * of them are built here and this is the one place that chooses between them.
     */
    public static AxisAlignedBB shapeAt(World world, int x, int y, int z) {
        if (!isActive()) return null;
        Block block = world.getBlock(x, y, z);
        if (block instanceof ISettlingBlock && ((ISettlingBlock) block).trmt$isSettling()) {
            return settledBoxAt(world, x, y, z);
        }
        if (block instanceof ISinkableBlock && ((ISinkableBlock) block).trmt$isSinkable()) {
            return boxAt(world, x, y, z);
        }
        return null;
    }

    /**
     * The shape the server believes a worn position has, or null when it has none.
     *
     * <p>
     * Built here rather than in the collision hook so that both sides derive the same box from the
     * same rule. A slab is the reason it cannot simply be a height: the half of the block it
     * occupies decides where the box starts as well as where it ends, and a rut worn into one has
     * to stop at the bottom of the slab rather than at the bottom of the cell.
     */
    public static AxisAlignedBB boxAt(World world, int x, int y, int z) {
        int sink = sinkAt(world, x, y, z);
        if (sink <= 0) return null;

        Block block = world.getBlock(x, y, z);
        SurfaceShape shape = SurfaceShape.of(block);
        // A stair answers for its own collision and this must not take it over. Everything below
        // builds ONE box across the whole footprint, which is right for a slab and wrong for a
        // stair - a stair is three boxes, and BlockStairs asks for them by calling the method this
        // is injected into three times over, so what a worn stair got was a nearly full cube
        // installed three times. The client meanwhile kept a real stair, because a remote world has
        // no record to sink from, so the two sides disagreed about a step somebody was standing on.
        //
        // Refused rather than shaped, and the reason is already written on BlockGhostStairs: a
        // stair states its own shape and never has a height clamped onto it, so a worn stair was
        // never meant to sink at all. Nothing visible is lost - no ghost stair has ever drawn a
        // rut, because that class reports itself unsunken - and handing back nothing puts the real
        // stair back in charge of the three boxes it built for itself.
        if (shape == SurfaceShape.STAIR) return null;
        // And the same refusal for anything narrower than the square it stands in, for the reason
        // written one paragraph up: everything below builds one box across the whole footprint,
        // which a block occupying part of its square has already declined to have. Handing back
        // nothing puts it back in charge of its own collision, which is where it was before this mod
        // arrived, and it is the same answer the client reaches through GhostLogic.solidBox.
        if (GhostInherit.ownFootingAt(block, world, x, y, z) != GhostInherit.ORDINARY) return null;
        if (!shape.isPartial()) {
            return AxisAlignedBB.getBoundingBox(x, y, z, x + 1.0D, y + SinkProfile.heightFor(sink), z + 1.0D);
        }

        int meta = world.getBlockMetadata(x, y, z);
        double floor = SurfaceShape.bottomOf(shape, meta);
        double top = SurfaceShape.topOf(shape, meta);
        double worn = Math.max(floor, top - sink / 16.0D);
        return AxisAlignedBB.getBoundingBox(x, y + floor, z, x + 1.0D, y + worn, z + 1.0D);
    }

    /**
     * Stamps every block with whether it belongs to a family that can sink, so the collision
     * path can dismiss the overwhelming majority of blocks with a single field read instead of
     * a registry lookup. Called after surfaces are resolved and after any config reload.
     */
    public static void markSinkableBlocks() {
        verifyCollisionHook();
        refresh();
        markSettlingBlocks();
        for (Object candidate : Block.blockRegistry) {
            if (!(candidate instanceof ISinkableBlock)) continue;
            ((ISinkableBlock) candidate).trmt$setSinkable(false);
        }
        if (!TrmtConfig.physicalDecayShows()) return;

        for (SurfaceRegistry.SurfaceState state : SurfaceRegistry.texturableStates()) {
            if (!canEverSink(state.family)) continue;
            if (state.block instanceof ISinkableBlock) {
                ((ISinkableBlock) state.block).trmt$setSinkable(true);
            }
        }
    }

    /**
     * Stamps the blocks that rest on the ground rather than being it.
     *
     * <p>
     * On both sides, which is the whole reason it lives here rather than beside the drawing. The
     * flag decides whether a block's footing comes down with its picture, and a flag stamped on one
     * side only is two machines disagreeing about where the floor is - the client predicting a step
     * down onto settled snow and the server shoving it back up, every tick, on every snowfield. The
     * client's own {@code Settling} reads what this writes rather than working it out again.
     *
     * <p>
     * By material, which for these two names exactly the blocks meant: every snow layer in the pack
     * is on snow's material and every carpet is on carpet's, because both are vanilla materials that
     * a mod adding one of these will reuse rather than invent. A block that fills its own space is
     * not lying on anything and is refused whatever it is made of.
     */
    public static void markSettlingBlocks() {
        boolean wanted = TrmtConfig.enabled && TrmtConfig.settleOnWornGround;
        for (Object candidate : Block.blockRegistry) {
            if (!(candidate instanceof ISettlingBlock)) continue;
            ((ISettlingBlock) candidate).trmt$setSettling(wanted && restsOnTheGround((Block) candidate));
        }
    }

    private static boolean restsOnTheGround(Block block) {
        try {
            net.minecraft.block.material.Material material = block.getMaterial();
            if (material != net.minecraft.block.material.Material.snow
                && material != net.minecraft.block.material.Material.carpet) {
                return false;
            }
            return !block.renderAsNormalBlock();
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    /**
     * Falls back to visual-only ruts if the collision hook is not actually in place.
     *
     * <p>
     * Worth checking rather than assuming, because the failure is silent and asymmetric. The
     * client predicts the ground from its own ghost block, which needs no injection at all; the
     * server answers from the mixin. If the mixin were missing, the client would stand in a rut
     * the server insists is solid ground and get shoved back out of it every tick. Dropping to
     * visual mode means the two sides agree again, at the cost of the feature rather than the
     * player's footing.
     */
    private static void verifyCollisionHook() {
        if (!TrmtConfig.physicalDecayCollides()) return;
        if (net.minecraft.init.Blocks.stone instanceof ISinkableBlock) return;

        com.trmtgtnh.Trmt.LOG.error(
            "The collision hook did not apply, so worn ground cannot actually be walked down into. "
                + "Falling back to visual-only ruts; set physicalDecay=visual to silence this.");
        // Not this client's decision to take while it is a guest. A client predicts its footing from
        // the ghost it painted, which needs no injection at all - the paragraph above says as much -
        // and the hook being looked for here is the server's half of the arrangement. Dropping the
        // mode would leave this client walking the flat while the server went on hollowing the
        // ground out, which is the very disagreement this fallback exists to avoid, arrived at from
        // the other side. The line above still stands, because a hook that did not apply is worth
        // knowing about either way.
        if (com.trmtgtnh.config.ServerRules.held()) return;
        TrmtConfig.physicalDecay = TrmtConfig.DECAY_VISUAL;
    }

    /**
     * Whether a surface of this family can ever end up sunk, counting where its wear chain
     * leads. Grass never sinks as grass, but a grass path that has worn through to earth is
     * standing on the dirt family's depth.
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
