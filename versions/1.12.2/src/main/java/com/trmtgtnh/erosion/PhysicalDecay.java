package com.trmtgtnh.erosion;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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
 * untouched and nothing outside rendering ever knows. Ruts you can walk down into cannot work that way.
 * The ground you stand on is decided by the server, from a block that is still ordinary full-height
 * grass, so something has to intervene in its collision.
 *
 * <p>
 * Written for this edition rather than carried, because the other edition's version is built on two
 * things 1.12.2 does not need. There, a mixin on {@code Block.addCollisionBoxesToList} did the
 * intervening, and two more mixins stamped a flag onto every block instance so the hook could dismiss
 * the overwhelming majority of blocks with one field read. Here Forge posts
 * {@link GetCollisionBoxesEvent} with the list of boxes about to be returned, so {@link CollisionHook}
 * does the intervening from outside the game's code; and the flag becomes an identity set, built when
 * surfaces are resolved and replaced whole rather than written in place. What carried is everything
 * else - the rules for which box a position gets, and every reason written beside them.
 *
 * <p>
 * The hot path is as it was: collision runs for every moving entity, every tick, across every block
 * its box sweeps. So a box costs a static read and an identity lookup unless its block could ever
 * sink, and only a block that could ever sink pays for a record lookup.
 */
public final class PhysicalDecay {

    /**
     * Whether collision follows the visuals at all. Read before anything else on the collision path,
     * so that turning this off costs one static field read per box tested.
     */
    private static volatile boolean active;

    /**
     * Blocks that belong to a family whose chain can ever reach a depth. Identity rather than equality,
     * because a block is its own identity and asking it for a hash would ask a mod's code; replaced
     * whole, never written in place, so a collision on another thread reads the old set or the new one
     * and never a half-built one.
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
            .getChunk(world.provider.getDimension(), x >> 4, z >> 4);
        if (data == null || data.isEmpty()) return 0;

        ErosionEntry entry = data.get(ErosionKey.packWorld(x, y, z));
        if (entry == null || !entry.isVisible()) return 0;
        // Something planted here holds the ground level whatever the record says it has taken.
        if (GroundCover.holdsAt(world, x, y, z)) return 0;

        // By the block's own thickness rather than by what class it is, which is the same rule the ghost
        // uses on the other side: half a block of stone cannot lose eight pixels and still be there,
        // whatever it was cut from.
        return SinkProfile.collides(
            entry.getFamily(),
            entry.getSink(),
            com.trmtgtnh.block.BlockGhost.shapeOf(
                com.trmtgtnh.block.BlockGhost
                    .outlineOf(com.trmtgtnh.util.Worlds.stateAt(world, x, y, z), world, new BlockPos(x, y, z))));
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
     * Snow and carpet are drawn down into the rut beneath them so they do not hang in the air over it,
     * and for a long time in the other edition that was all that happened: the picture came down and
     * the footing stayed where it was. A snow layer keeps a collision box whatever its depth, which is
     * enough to stop anything falling onto it. So a player crossing a snowed-over road stood at the
     * height the road had before it wore, looking down at snow drawn half a block below their feet.
     *
     * <p>
     * Moved rather than capped: shifting the box by the same figure the picture moves by keeps both
     * sides on one number and costs one field read on the collision path.
     *
     * @param own the box the block itself gave, in world coordinates
     */
    public static AxisAlignedBB settledBoxAt(World world, int x, int y, int z, AxisAlignedBB own) {
        if (own == null) return null;
        double drop = groundDropUnder(world, x, y, z);
        if (drop <= 0.0D) return null;
        return own.offset(0.0D, -drop, 0.0D);
    }

    /**
     * The shape the server believes a worn position has, in world coordinates, or null when it has
     * none.
     *
     * <p>
     * Built here rather than in the collision hook so that both sides derive the same box from the
     * same rule. A slab is the reason it cannot simply be a height: the half of the block it occupies
     * decides where the box starts as well as where it ends, and a rut worn into one has to stop at
     * the bottom of the slab rather than at the bottom of the cell.
     */
    public static AxisAlignedBB boxAt(World world, int x, int y, int z) {
        int sink = sinkAt(world, x, y, z);
        if (sink <= 0) return null;

        IBlockState state = com.trmtgtnh.util.Worlds.stateAt(world, x, y, z);
        SurfaceShape shape = SurfaceShape.of(state.getBlock());
        // A stair answers for its own collision and this must not take it over. Everything below
        // builds ONE box across the whole footprint, which is right for a slab and wrong for a stair -
        // a stair is three boxes. Refused rather than shaped: a stair states its own shape and never
        // has a height clamped onto it, so a worn stair was never meant to sink at all.
        if (shape == SurfaceShape.STAIR) return null;
        // And the same refusal for anything narrower than the square it stands in: everything below
        // builds one box across the whole footprint, which a block occupying part of its square has
        // already declined to have. Handing back nothing puts it back in charge of its own collision,
        // which is where it was before this mod arrived.
        if (!fillsItsFootprint(state, world, x, y, z)) return null;

        // The block's own outline, less what it has sunk from its own top - the same arithmetic the ghost
        // does on the other side, through the same method, so the two cannot disagree about where the
        // ground is. The other edition works the outline out from the shape instead, because 1.7.10 has no
        // way to ask a block where its edges are without a bounding box shared between every block of its
        // kind; here the state answers for itself.
        int outline = com.trmtgtnh.block.BlockGhost.outlineOf(state, world, new BlockPos(x, y, z));
        double floor = com.trmtgtnh.block.BlockGhost.floorOf(outline);
        double worn = Math.max(floor, com.trmtgtnh.block.BlockGhost.topOf(outline) - sink / 16.0D);
        return new AxisAlignedBB(x, y + floor, z, x + 1.0D, y + worn, z + 1.0D);
    }

    /**
     * Whether a block's own outline spans the whole of its square across.
     *
     * <p>
     * What the other edition asks of {@code GhostInherit.ownFootingAt}, which is a class this edition
     * does not have yet. The question is the same one: a block that stops short of its square's edges
     * has a shape of its own, and a worn box built edge to edge would widen it.
     */
    private static boolean fillsItsFootprint(IBlockState state, World world, int x, int y, int z) {
        try {
            AxisAlignedBB own = state.getBoundingBox(world, new BlockPos(x, y, z));
            return own != null && own.minX <= 0.0D && own.minZ <= 0.0D && own.maxX >= 1.0D && own.maxZ >= 1.0D;
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    /**
     * Stamps every block with whether it belongs to a family that can sink, so the collision path can
     * dismiss the overwhelming majority of blocks with one lookup instead of a registry search. Called
     * after surfaces are resolved and after any config reload.
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
     * By material, which for these two names exactly the blocks meant: every snow layer in the pack is
     * on snow's material and every carpet is on carpet's, because both are vanilla materials that a mod
     * adding one of these will reuse rather than invent. A block that fills its own space is not lying
     * on anything and is refused whatever it is made of.
     */
    public static void markSettlingBlocks() {
        if (!TrmtConfig.enabled || !TrmtConfig.settleOnWornGround) {
            settling = Collections.emptySet();
            return;
        }
        Set<Block> found = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        for (Block block : Block.REGISTRY) {
            if (restsOnTheGround(block)) found.add(block);
        }
        settling = found;
    }

    private static boolean restsOnTheGround(Block block) {
        try {
            IBlockState state = block.getDefaultState();
            Material material = state.getMaterial();
            if (material != Material.SNOW && material != Material.CARPET) return false;
            return !state.isFullCube();
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

    /**
     * Gives the server's real blocks the shape the client draws over them.
     *
     * <p>
     * Stands where the other edition's collision mixin stood. Forge posts this event at the end of
     * {@code World.getCollisionBoxes}, carrying the list about to be returned - and, which is the part
     * worth knowing, it also posts it after every block inside {@code collidesWithAnyBlock}, the check
     * {@code Entity.pushOutOfBlocks} uses to decide whether to shove something out of the ground. The
     * other edition's hook sat on one of those two paths and not the other, and the difference was an
     * item thrown onto a rut being flung out of it again every tick (0.9.216 closed it with a second
     * mixin). Here one handler sits on both, and that bug has nowhere to live - provided a worn box that
     * no longer touches the question being asked is taken out of the list rather than left in, because
     * an empty list is what the push-out reads as "not stuck".
     *
     * <p>
     * Walks the boxes rather than the space. The event does not say which block a box came from, but a
     * box that spans a whole square across can only have come from the block in that square, and every
     * box this mod would ever replace is one of those - a block narrower than its square keeps its own
     * collision, by the rule {@link #boxAt} applies. So a box costs an identity lookup, and only a box
     * whose block could ever sink or settle costs more.
     *
     * <p>
     * Ignores the client's world, where a ghost stands over worn ground and answers for its own shape.
     */
    public static final class CollisionHook {

        @SubscribeEvent
        public void onCollisionBoxes(GetCollisionBoxesEvent event) {
            if (!active) return;
            World world = event.getWorld();
            if (world == null || world.isRemote) return;
            List<AxisAlignedBB> boxes = event.getCollisionBoxesList();
            if (boxes.isEmpty()) return;
            AxisAlignedBB query = event.getAabb();

            for (ListIterator<AxisAlignedBB> it = boxes.listIterator(); it.hasNext();) {
                AxisAlignedBB box = it.next();
                // Already ours. The check that has to come first, because inside collidesWithAnyBlock
                // this event is posted again after every block, over the same list: a snow box already
                // moved down into a rut sits in the square below its own, and read afresh it would be
                // taken for the ground there and swapped for the ground's box.
                if (box instanceof WornBox) continue;
                // Across a whole square, and nothing else can be ours. A tiny tolerance, because a
                // box that arrives already offset by a whole number of blocks can carry a rounding
                // error in the last place.
                double spanX = box.maxX - box.minX;
                double spanZ = box.maxZ - box.minZ;
                if (Math.abs(spanX - 1.0D) > 1.0E-7D || Math.abs(spanZ - 1.0D) > 1.0E-7D) continue;
                int x = MathHelper.floor(box.minX + 1.0E-7D);
                int z = MathHelper.floor(box.minZ + 1.0E-7D);
                if (Math.abs(box.minX - x) > 1.0E-7D || Math.abs(box.minZ - z) > 1.0E-7D) continue;
                int y = MathHelper.floor(box.minY + 1.0E-7D);

                Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
                AxisAlignedBB worn;
                if (sinkable.contains(block)) {
                    worn = boxAt(world, x, y, z);
                } else if (settling.contains(block)) {
                    worn = settledBoxAt(world, x, y, z, box);
                } else {
                    continue;
                }
                if (worn == null) continue;
                if (worn.intersects(query)) {
                    it.set(new WornBox(worn));
                } else {
                    it.remove();
                }
                sayInPlace(x, y, z, worn);
            }
        }

        /**
         * Said once, the first time this hook gives a real block a worn shape.
         *
         * <p>
         * Because its absence is otherwise invisible. The server's movement check puts a player back
         * without a word when it believes they have walked into solid ground - its "moved wrongly"
         * line guards only the horizontal, and the vertical test beside it can never fire - so a hook
         * that had silently stopped acting would show up only as a client that cannot step down into
         * any rut, with nothing in either log to say why. This line is the difference between that
         * and a known cause.
         */
        private static volatile boolean inPlaceSaid;

        private static void sayInPlace(int x, int y, int z, AxisAlignedBB worn) {
            if (inPlaceSaid) return;
            inPlaceSaid = true;
            Trmt.LOG.info(
                "Worn ground is solid at its worn height on this server; first seen at {},{},{} with its top at {}",
                Integer.valueOf(x),
                Integer.valueOf(y),
                Integer.valueOf(z),
                String.format(java.util.Locale.ROOT, "%.4f", Double.valueOf(worn.maxY)));
        }
    }

    /**
     * A box this mod put into a collision list, told apart from the game's by its class.
     *
     * <p>
     * Nothing else about it differs, and vanilla reads it exactly as it reads any box - the methods it
     * calls on one return plain boxes, so the mark never travels further than the list it was put in.
     */
    private static final class WornBox extends AxisAlignedBB {

        WornBox(AxisAlignedBB of) {
            super(of.minX, of.minY, of.minZ, of.maxX, of.maxY, of.maxZ);
        }
    }
}
