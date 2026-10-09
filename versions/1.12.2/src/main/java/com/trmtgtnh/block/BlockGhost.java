package com.trmtgtnh.block;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.SinkProfile;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * A cosmetic block that stands in for worn ground, drawn but never stored.
 *
 * <p>
 * The same idea as the 1.7.10 edition's ghost: the server never places one and never hears about
 * one. The client paints it over ground the server has told it is worn, remembers what was
 * underneath, and draws a generated wear sprite in its place. No block is ever written into the
 * world, which is what lets the whole mod be switched off for one player, or removed outright,
 * without a square of terrain having changed.
 *
 * <p>
 * What is different here, and it is the discovery that made the spike worth running early: 1.12.2
 * carries the position into the two questions that decide a block's appearance and its footing.
 * {@link #getExtendedState} is handed the world and the position, so the model can be told this
 * square's whole record without anything having to be smuggled through metadata; and
 * {@link #getCollisionBoundingBox} is handed them too, so the hollow a rut actually has is returned by
 * the block itself.
 *
 * <p>
 * The record is asked for through the proxy rather than read from the client's cache directly. This
 * class is loaded on a dedicated server - it is registered there, like every block - and a server must
 * never load a class that reaches into the client's half of the mod. The other edition keeps the same
 * line by putting its rendering in a separate class; here the proxy is the separate class.
 *
 * <p>
 * The record is unlisted rather than a listed property, and that is not a detail. Sixteen layers at
 * nine depths across sixteen families is a blockstate set nobody should enumerate, and listed
 * properties have to be: they are part of the state's identity, they are saved, and the model system
 * wants a variant for each. Unlisted properties are worked out at render time from the position and
 * never stored, which is exactly what wear is.
 */
public class BlockGhost extends Block {

    /** This square's whole record, as the client holds it, worked out at render time and never stored. */
    public static final IUnlistedProperty<Integer> RECORD = number("record");

    /**
     * What the ghost is standing over, as the id of that block state, or -1 where nothing is remembered.
     *
     * <p>
     * The model needs it to find this surface's own wear set - a granite road wears in granite's pixels, not
     * stone's - and it is handed a state, never a place, so the place's answer has to travel inside the state.
     */
    public static final IUnlistedProperty<Integer> ORIGIN = number("origin");

    /**
     * Which way round this square's wear pattern is turned, 0 to 3, worked out from its position and its depth.
     * A path laid in a line would otherwise repeat one picture square after square.
     */
    public static final IUnlistedProperty<Integer> ROTATION = number("rotation");

    /**
     * The turn the receding grass fringe takes, which is the position's own without the depth folded in.
     * The other edition asks for it that way, and a fringe that changed as the ground sank would crawl.
     */
    public static final IUnlistedProperty<Integer> FRINGE_TURN = number("fringe_turn");

    /** Whether snow lies on this square, which decides what a lawn shows on its flanks. */
    public static final IUnlistedProperty<Integer> SNOWED = number("snowed");

    /**
     * The outline of the block this ghost stands in for, in sixteenths: its floor in the low byte and its
     * top in the high one.
     *
     * <p>
     * A ghost is not always a cube. A slab is half of one, resting on the floor or hung from the ceiling; a
     * grass path is a whole one a pixel short. Drawing every ghost as a cube put a path a pixel above the
     * ground it covers and made a slab a block. The other edition keeps a separate ghost block for each
     * shape; here one ghost carries the shape it is standing in for, read off the block itself.
     */
    public static final IUnlistedProperty<Integer> OUTLINE = number("outline");

    /** A whole cube, which is what a ghost stands in for unless the block it covers says otherwise. */
    public static final int WHOLE_CUBE = 16 << 8;

    /**
     * The shape of the stair this ghost stands in for, packed, or -1 for every other block.
     *
     * <p>
     * A shape rather than a height, which is why it is a property of its own and not part of
     * {@link #OUTLINE}: every other ghost is a box over the whole cell and is described by where its
     * floor and its top are, and a stair is neither.
     *
     * <p>
     * <strong>Packed by this class rather than carried as a state id, because a state id would lose
     * exactly the part that matters.</strong> A stair's metadata holds its facing and its half; which
     * of the five shapes it is - straight, or one of the four corners - is not stored at all but
     * worked out from its neighbours every time it is asked, by {@code getActualState}. Round-tripping
     * through {@code Block.getStateId} would therefore hand the model a state whose shape had reverted
     * to straight, and every corner stair in a staircase would draw as though it were not one. See
     * {@link #stairCodeAt}, which asks the block for its actual state and packs what that answers.
     */
    public static final IUnlistedProperty<Integer> STAIR = number("stair");

    private static IUnlistedProperty<Integer> number(final String name) {
        return new IUnlistedProperty<Integer>() {

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isValid(Integer value) {
                return value != null;
            }

            @Override
            public Class<Integer> getType() {
                return Integer.class;
            }

            @Override
            public String valueToString(Integer value) {
                return String.valueOf(value);
            }
        };
    }

    public BlockGhost() {
        super(Material.GROUND);
        setTranslationKey("trmtgtnh.ghost");
        setRegistryName("trmtgtnh", "ghost_grass");
        setHardness(0.6F);
        // As opaque to light as the ground it covers, which is what the other edition does and for the
        // same two reasons. Painting a ghost is then no change at all as far as lighting is concerned,
        // so the world does not recompute light at every square painted - a light update per block
        // is what flying into a well-worn area would otherwise cost. And sunlight does not start
        // leaking down through a road into the ground beneath it.
        setLightOpacity(255);
        // And lit from its neighbours, because its own cell is opaque and therefore dark: a top drawn
        // below the top of that cell would take the cell's own light and come out black. Vanilla
        // does exactly this for its two blocks of the same shape, farmland and the grass path - but in
        // a loop that runs over its own blocks before any mod has registered one, so a block added
        // later has to say so itself.
        this.useNeighborBrightness = true;
        // No creative tab and no item form. A ghost is not a thing anybody may hold: it exists for
        // the length of one client's opinion about one square, and an item that placed one would be
        // placing a lie into a real world.
        setCreativeTab(null);
    }

    /**
     * How brightly this square glows: whatever it has been lit with, or whatever the block it covers
     * gives off, whichever is brighter.
     *
     * <p>
     * The only block method here that both sides genuinely call: light propagation asks it, and a
     * chunk mesh asks it again for the brightness it bakes. {@link GhostLight} settles which side is
     * asking.
     *
     * <p>
     * The covered block's own glow is asked of its state rather than of the world. Through the world it
     * would be asked of whatever stands at this position - which is this ghost, asking itself. The other
     * edition has to take the same care for the same reason; here the state is simply to hand, turned
     * back from the id the painter kept.
     */
    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        int lit = GhostLight.levelAt(world, pos.getX(), pos.getY(), pos.getZ());
        int origin = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        int covered = 0;
        if (origin >= 0) {
            try {
                covered = Block.getStateById(origin)
                    .getLightValue();
            } catch (RuntimeException awkwardBlock) {
                covered = 0;
            }
        }
        return lit > covered ? lit : covered;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(
            this,
            new net.minecraft.block.properties.IProperty[0],
            new IUnlistedProperty[] { RECORD, ORIGIN, ROTATION, FRINGE_TURN, SNOWED, OUTLINE, STAIR });
    }

    /**
     * Tells the model this square's record.
     *
     * <p>
     * Called by the chunk mesher, on its own thread, once per block per rebuild. The client's cache is
     * built for exactly this - lock-free reads from mesher threads, each chunk's overlay replaced whole
     * and never written in place - so a rebuild that races a packet draws the old picture or the new
     * one, never half of each.
     */
    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) return state;
        short record = Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ());
        int origin = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        // Salted by depth, as the other edition's top face is, so a rut does not replay its first run's turns at
        // every pixel it sinks.
        int rotation = com.trmtgtnh.erosion.Rotations.forPosition(pos.getX(), pos.getZ(), ErosionState.sinkOf(record));
        return ((IExtendedBlockState) state).withProperty(RECORD, Integer.valueOf(record))
            .withProperty(ORIGIN, Integer.valueOf(origin))
            .withProperty(ROTATION, Integer.valueOf(rotation))
            .withProperty(
                FRINGE_TURN,
                Integer.valueOf(com.trmtgtnh.erosion.Rotations.forPosition(pos.getX(), pos.getZ())))
            .withProperty(SNOWED, Integer.valueOf(snowedAt(world, pos) ? 1 : 0))
            .withProperty(OUTLINE, Integer.valueOf(outlineAt(world, pos, origin)))
            // Here rather than in the model, because this is the last place with a world to ask: a
            // stair's shape is decided by its neighbours and the model is handed a state and nothing
            // else. See STAIR.
            .withProperty(STAIR, Integer.valueOf(stairCodeAt(world, pos, origin)));
    }

    /**
     * The hollow a rut actually has, which is the whole of "physical decay" on this side.
     *
     * <p>
     * Position-aware and virtual in 1.12.2, so the block answers for itself - on the client, where it
     * is the only place a ghost ever stands. The server answers for the real block under the same
     * square from its own records, by the same rule, in {@code PhysicalDecay}; the two meeting on one
     * number is what keeps a player standing in a rut from being shoved back out of it.
     */
    @Override
    @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        // A stair is not one box, so the single-box question has vanilla's own answer for a stair -
        // the whole cell - and the shape is given in addCollisionBoxToList, which is where vanilla
        // gives it too.
        int origin = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (stairCodeAt(world, pos, origin) >= 0) {
            return FULL_BLOCK_AABB;
        }
        // A block narrower than its own square keeps its own footing, worn or not - see
        // GhostInherit.ownFootingAt. Wear is a height off the top of a full cell, which is the wrong
        // shape for a pad that only covers part of one: handing back a full cell over a cloud turns
        // a block you fall through into a block you stand on.
        AxisAlignedBB own = GhostInherit.ownFootingAt(world, pos, origin);
        if (own != GhostInherit.ORDINARY) return own;
        return solidBox(Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ()), origin);
    }

    /**
     * The shape a stair is actually walked on, box by box.
     *
     * <p>
     * The one question whose answer cannot be a single box, and the reason a stair needed anything
     * beyond a height at all. Vanilla's stair gives its shape here and nowhere else; a ghost standing
     * in for one has to do the same, or a player would walk up an invisible ramp and stand inside the
     * step they can see.
     */
    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
        java.util.List<AxisAlignedBB> colliding, @Nullable net.minecraft.entity.Entity entity, boolean actual) {
        int stair = stairCodeAt(world, pos, Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ()));
        if (stair < 0) {
            super.addCollisionBoxToList(state, world, pos, entityBox, colliding, entity, actual);
            return;
        }
        for (AxisAlignedBB box : stairBoxes(stair)) {
            addCollisionBoxToList(pos, entityBox, colliding, box);
        }
    }

    /**
     * The outline, which follows the footing rather than the picture - the 1.7.10 edition's GhostLogic.outlineBox,
     * "Selection follows collision, not the visuals".
     *
     * <p>
     * In visual mode the two differ: the ground is drawn sunk and walked on at full height, and the box a player
     * aims at is the one they stand on. Until 0.9.220 this edition outlined the picture instead; Xep chose the
     * other edition's rule on 2026-10-08. Never nothing, which collision may be - a block you walk through says so
     * with no box at all, and an outline is drawn from whatever it is handed: then the covered block's own box,
     * which is what is really there and what was being pointed at, and failing that the whole cell.
     */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        // As vanilla's own stair does: the box a player aims at is the whole cell, however the steps
        // are cut. Fine-grained outlines on stairs are a later version's idea and copying one here
        // would make a worn stair the only stair in the world that aims differently.
        int origin = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (stairCodeAt(world, pos, origin) >= 0) {
            return FULL_BLOCK_AABB;
        }
        AxisAlignedBB own = GhostInherit.ownFootingAt(world, pos, origin);
        AxisAlignedBB solid = own != GhostInherit.ORDINARY ? own
            : solidBox(Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ()), origin);
        if (solid != null) return solid;
        IBlockState covered = coveredState(origin);
        if (covered != null) {
            try {
                AxisAlignedBB mine = covered.getBoundingBox(world, pos);
                if (mine != null) return mine;
            } catch (RuntimeException awkwardBlock) {
                // An outline in the wrong place is a smaller fault than none; the whole cell below.
            }
        }
        return FULL_BLOCK_AABB;
    }

    /**
     * The box a worn square is stood on - the 1.7.10 edition's GhostLogic.solidBox, rule for rule.
     *
     * <p>
     * A slab wears from its own top, by the collision depth its shape allows, and stops at its own floor. Anything
     * else is a full cell less the collision depth, measured from the top of the cell, not from the top of the
     * block - which is the same thing for a whole cube, and for a block already short means its first pixel of
     * wear goes into its own shortfall. Until 0.9.220 this edition took every depth off the block's own top, a
     * pixel deeper for a short block; Xep chose the other edition's rule on 2026-10-08.
     */
    static AxisAlignedBB solidBox(short record, int origin) {
        IBlockState covered = coveredState(origin);
        SurfaceShape shape = SurfaceShape.of(covered == null ? null : covered.getBlock());
        if (shape.isPartial()) {
            double floor = SurfaceShape.bottomOf(shape, covered);
            double top = SurfaceShape.topOf(shape, covered);
            return new AxisAlignedBB(
                0.0D,
                floor,
                0.0D,
                1.0D,
                Math.max(floor, top - collisionSink(record, shape) / 16.0D),
                1.0D);
        }
        return new AxisAlignedBB(
            0.0D,
            0.0D,
            0.0D,
            1.0D,
            SinkProfile.heightFor(collisionSink(record, SurfaceShape.FULL)),
            1.0D);
    }

    /**
     * The height a square stands at, drawn (footing false) or stood on (footing true) - the 1.7.10 edition's
     * GhostLogic.heightAt, rule for rule.
     *
     * <p>
     * A slab: its own top less the depth its shape allows - the drawn one, or the collision one for footing - and
     * never below its own floor. Anything else: the block's own top, or the cell's top less the drawn depth if that
     * is lower. <strong>The drawn depth for footing too</strong>, on anything but a slab, which is that edition's
     * code rather than its comment: snow resting on worn ground in visual mode comes down into the drawn rut, and
     * Xep chose the code on 2026-10-08.
     */
    public static double heightAt(short record, int origin, int outline, boolean footing) {
        IBlockState covered = coveredState(origin);
        SurfaceShape shape = SurfaceShape.of(covered == null ? null : covered.getBlock());
        if (shape.isPartial()) {
            double top = SurfaceShape.topOf(shape, covered);
            double floor = SurfaceShape.bottomOf(shape, covered);
            int sunk = footing ? collisionSink(record, shape) : drawnSink(record, shape);
            return Math.max(floor, top - sunk / 16.0D);
        }
        return Math.min(topOf(outline), SinkProfile.heightFor(drawnSink(record, SurfaceShape.FULL)));
    }

    /** Where the space a square stands in begins - only ever above nought for an upper slab. 1.7.10's bottomAt. */
    public static double bottomAt(int origin) {
        IBlockState covered = coveredState(origin);
        SurfaceShape shape = SurfaceShape.of(covered == null ? null : covered.getBlock());
        return shape.isPartial() ? SurfaceShape.bottomOf(shape, covered) : 0.0D;
    }

    /**
     * The block a square stands in for, or null.
     *
     * <p>
     * Its shape is decided by what class it is, as the 1.7.10 edition decides it (SurfaceShape.of): a slab class is
     * a slab and anything else whole, whatever its thickness. Until 0.9.220 this edition judged by thickness, which
     * wore a half-height block that is not a slab as a slab; Xep chose the other edition's rule on 2026-10-08.
     */
    private static IBlockState coveredState(int origin) {
        if (origin < 0) return null;
        try {
            return Block.getStateById(origin);
        } catch (RuntimeException awkwardBlock) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Stairs
    // ------------------------------------------------------------------

    /**
     * The shape of the stair this ghost stands in for, packed, or -1 for every other block.
     *
     * <p>
     * Two bits of facing, one of half, three of shape - the whole of what decides a stair's geometry,
     * and all of it read from the block's <em>actual</em> state rather than its stored one, because
     * which of the five shapes a stair is comes from its neighbours and is not in its metadata at all.
     *
     * <p>
     * A worn stair keeps the stair's own shape and does not sink. A stair fuses what it looks like and
     * what it collides as into one answer, so a dip in the picture would be a dip the server has not
     * got, and the server would spend every tick pushing whoever stood in it back out of ground it
     * believes is solid. The 1.7.10 edition reached that from the other direction, by extending
     * vanilla's stair block and finding the two could not be separated there either.
     */
    public static int stairCodeAt(IBlockAccess world, BlockPos pos, int origin) {
        if (origin < 0 || world == null) return -1;
        try {
            IBlockState stored = Block.getStateById(origin);
            if (SurfaceShape.of(stored.getBlock()) != SurfaceShape.STAIR) return -1;
            IBlockState actual = stored.getBlock()
                .getActualState(stored, world, pos);
            int facing = ((net.minecraft.util.EnumFacing) actual.getValue(net.minecraft.block.BlockStairs.FACING))
                .getHorizontalIndex();
            boolean top = actual.getValue(net.minecraft.block.BlockStairs.HALF)
                == net.minecraft.block.BlockStairs.EnumHalf.TOP;
            int shape = ((net.minecraft.block.BlockStairs.EnumShape) actual
                .getValue(net.minecraft.block.BlockStairs.SHAPE)).ordinal();
            return (facing & 3) | (top ? 4 : 0) | (shape << 3);
        } catch (RuntimeException awkwardBlock) {
            // A block that answers to being a stair without carrying a stair's properties. It gets
            // drawn as a cube rather than crashing a chunk rebuild.
            return -1;
        }
    }

    /**
     * The boxes a stair's shape is made of, in the cell's own frame.
     *
     * <p>
     * <strong>A port of vanilla's own {@code BlockStairs.getCollisionBoxList}, which is private and
     * takes a state this cannot hand it.</strong> The model is given a state and no world, and a
     * stair's shape has to be worked out from its neighbours, so the shape is settled once where there
     * is a world - {@link #stairCodeAt} - and turned back into boxes here. The eighteen boxes below
     * are vanilla's, with vanilla's names, so the two can be read side by side; the apparent
     * inversions are vanilla's too, and they are not mistakes - a stair whose half is TOP has its slab
     * across the top of the cell and its step in the <em>bottom</em> half, which is why the top half
     * asks for the boxes named BOT.
     *
     * <p>
     * One answer serves the collision box, the outline a player aims at and the boxes the model draws,
     * which is what keeps the three agreeing. The 1.16.5 edition gets the same list from
     * {@code VoxelShape.toAabbs}, which is the same geometry by a shorter road.
     */
    public static java.util.List<AxisAlignedBB> stairBoxes(int code) {
        java.util.List<AxisAlignedBB> boxes = new java.util.ArrayList<AxisAlignedBB>(3);
        if (code < 0) return boxes;

        boolean top = (code & 4) != 0;
        net.minecraft.util.EnumFacing facing = net.minecraft.util.EnumFacing.byHorizontalIndex(code & 3);
        net.minecraft.block.BlockStairs.EnumShape[] shapes = net.minecraft.block.BlockStairs.EnumShape.values();
        int which = code >> 3;
        net.minecraft.block.BlockStairs.EnumShape shape = which >= 0 && which < shapes.length ? shapes[which]
            : net.minecraft.block.BlockStairs.EnumShape.STRAIGHT;

        boxes.add(top ? AABB_SLAB_TOP : AABB_SLAB_BOTTOM);
        if (shape == net.minecraft.block.BlockStairs.EnumShape.STRAIGHT
            || shape == net.minecraft.block.BlockStairs.EnumShape.INNER_LEFT
            || shape == net.minecraft.block.BlockStairs.EnumShape.INNER_RIGHT) {
            boxes.add(stairQuarter(top, facing));
        }
        if (shape != net.minecraft.block.BlockStairs.EnumShape.STRAIGHT) {
            boxes.add(stairEighth(top, facing, shape));
        }
        return boxes;
    }

    /** A quarter of a block - two eighth-size cubes back to back. In every shape but the outer ones. */
    private static AxisAlignedBB stairQuarter(boolean top, net.minecraft.util.EnumFacing facing) {
        switch (facing) {
            case SOUTH:
                return top ? AABB_QTR_BOT_SOUTH : AABB_QTR_TOP_SOUTH;
            case WEST:
                return top ? AABB_QTR_BOT_WEST : AABB_QTR_TOP_WEST;
            case EAST:
                return top ? AABB_QTR_BOT_EAST : AABB_QTR_TOP_EAST;
            case NORTH:
            default:
                return top ? AABB_QTR_BOT_NORTH : AABB_QTR_TOP_NORTH;
        }
    }

    /** An eighth of a block - all three dimensions halved. In every shape but straight. */
    private static AxisAlignedBB stairEighth(boolean top, net.minecraft.util.EnumFacing facing,
        net.minecraft.block.BlockStairs.EnumShape shape) {
        net.minecraft.util.EnumFacing corner;
        switch (shape) {
            case OUTER_RIGHT:
                corner = facing.rotateY();
                break;
            case INNER_RIGHT:
                corner = facing.getOpposite();
                break;
            case INNER_LEFT:
                corner = facing.rotateYCCW();
                break;
            case OUTER_LEFT:
            default:
                corner = facing;
                break;
        }
        switch (corner) {
            case SOUTH:
                return top ? AABB_OCT_BOT_SE : AABB_OCT_TOP_SE;
            case WEST:
                return top ? AABB_OCT_BOT_SW : AABB_OCT_TOP_SW;
            case EAST:
                return top ? AABB_OCT_BOT_NE : AABB_OCT_TOP_NE;
            case NORTH:
            default:
                return top ? AABB_OCT_BOT_NW : AABB_OCT_TOP_NW;
        }
    }

    // ------------------------------------------------------------------
    // Which pass a ghost draws in
    // ------------------------------------------------------------------

    /**
     * The pass the block this ghost stands in for draws in, which is the pass the ghost draws in.
     *
     * <p>
     * <strong>One ghost standing in for everything has to answer this per position, and a block's
     * pass is asked of the block rather than of the position.</strong> The 1.7.10 edition has no such
     * problem: it registers a ghost block per family and variant - a clear one, a window one - and
     * each simply answers for itself. Here the pass is declared once for the single ghost, and the
     * only thing that varies per square is which quads are handed over, so the ghost offers itself to
     * every pass a covered block might use and the model hands back nothing in the passes that are
     * not this square's.
     *
     * <p>
     * Without it every ghost drew in the cut-out pass: worn ice drew over what was behind it instead
     * of through it, and a wear picture with holes in it had those holes punched through the ground
     * rather than filled, which is what "you can see through the top of a worn path" was.
     *
     * <p>
     * <strong>Except a window, which blends whatever pass its block draws in</strong> - the other
     * edition's window twin answers {@code getRenderBlockPass() { return clear || window ? 1 : 0; }},
     * and the window half of that was not carried until 2026-10-08. Chisel's waterstone draws in the
     * solid pass, which ignores alpha, so its picture's holes - the water, about two thirds opaque -
     * were written as solid blue stone. See {@code client.model.GhostWindows}. Client only: the model
     * is the one caller, and both questions here are the client's.
     */
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public static net.minecraft.util.BlockRenderLayer layerOf(int origin) {
        if (origin < 0) return net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED;
        if (com.trmtgtnh.client.model.GhostWindows.windowOf(origin)) {
            return net.minecraft.util.BlockRenderLayer.TRANSLUCENT;
        }
        try {
            net.minecraft.util.BlockRenderLayer layer = Block.getStateById(origin)
                .getBlock()
                .getRenderLayer();
            return layer == null ? net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED : layer;
        } catch (RuntimeException awkwardBlock) {
            return net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED;
        }
    }

    /**
     * Offered to every pass, because which one a square wants is not known until the square is known.
     *
     * <p>
     * Costs a visit to the model in each pass for every ghost; the model answers with an empty list
     * in all but one, which is the same work vanilla does for any block that declines a pass.
     */
    @Override
    public boolean canRenderInLayer(IBlockState state, net.minecraft.util.BlockRenderLayer layer) {
        return layer == net.minecraft.util.BlockRenderLayer.SOLID
            || layer == net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED
            || layer == net.minecraft.util.BlockRenderLayer.CUTOUT
            || layer == net.minecraft.util.BlockRenderLayer.TRANSLUCENT;
    }

    private static final AxisAlignedBB AABB_SLAB_TOP = new AxisAlignedBB(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB AABB_SLAB_BOTTOM = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);
    private static final AxisAlignedBB AABB_QTR_TOP_WEST = new AxisAlignedBB(0.0D, 0.5D, 0.0D, 0.5D, 1.0D, 1.0D);
    private static final AxisAlignedBB AABB_QTR_TOP_EAST = new AxisAlignedBB(0.5D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB AABB_QTR_TOP_NORTH = new AxisAlignedBB(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 0.5D);
    private static final AxisAlignedBB AABB_QTR_TOP_SOUTH = new AxisAlignedBB(0.0D, 0.5D, 0.5D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB AABB_QTR_BOT_WEST = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.5D, 0.5D, 1.0D);
    private static final AxisAlignedBB AABB_QTR_BOT_EAST = new AxisAlignedBB(0.5D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);
    private static final AxisAlignedBB AABB_QTR_BOT_NORTH = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 0.5D);
    private static final AxisAlignedBB AABB_QTR_BOT_SOUTH = new AxisAlignedBB(0.0D, 0.0D, 0.5D, 1.0D, 0.5D, 1.0D);
    private static final AxisAlignedBB AABB_OCT_TOP_NW = new AxisAlignedBB(0.0D, 0.5D, 0.0D, 0.5D, 1.0D, 0.5D);
    private static final AxisAlignedBB AABB_OCT_TOP_NE = new AxisAlignedBB(0.5D, 0.5D, 0.0D, 1.0D, 1.0D, 0.5D);
    private static final AxisAlignedBB AABB_OCT_TOP_SW = new AxisAlignedBB(0.0D, 0.5D, 0.5D, 0.5D, 1.0D, 1.0D);
    private static final AxisAlignedBB AABB_OCT_TOP_SE = new AxisAlignedBB(0.5D, 0.5D, 0.5D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB AABB_OCT_BOT_NW = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.5D, 0.5D, 0.5D);
    private static final AxisAlignedBB AABB_OCT_BOT_NE = new AxisAlignedBB(0.5D, 0.0D, 0.0D, 1.0D, 0.5D, 0.5D);
    private static final AxisAlignedBB AABB_OCT_BOT_SW = new AxisAlignedBB(0.0D, 0.0D, 0.5D, 0.5D, 0.5D, 1.0D);
    private static final AxisAlignedBB AABB_OCT_BOT_SE = new AxisAlignedBB(0.5D, 0.0D, 0.5D, 1.0D, 0.5D, 1.0D);

    /**
     * How far a record's picture has sunk, in sixteenths.
     *
     * <p>
     * One of the two rules that decide a ghost's height, kept here beside the other so the picture,
     * the outline and the footing are all worked out from one record by one piece of arithmetic. The
     * model asks this and so does the outline; they must never disagree, or a player would aim at a
     * box floating above the ground they can see.
     */
    public static int drawnSink(short record) {
        return drawnSink(record, SurfaceShape.FULL);
    }

    public static int drawnSink(short record, SurfaceShape shape) {
        SurfaceFamily appearance = ErosionState.familyOf(record);
        if (appearance == null || !shows(record)) return 0;
        return SinkProfile.shown(appearance, ErosionState.sinkOf(record), shape);
    }

    /**
     * How far a record's footing has sunk, in sixteenths - nothing at all in visual mode, where the
     * ground is drawn sunk and walked on at full height, because the server says it is.
     *
     * <p>
     * The same rule the server applies to the real block under the same square, in
     * {@code PhysicalDecay.sinkAt}, which is the whole reason it is {@link SinkProfile#collides} on
     * both sides and not something worked out here.
     */
    public static int collisionSink(short record) {
        return collisionSink(record, SurfaceShape.FULL);
    }

    public static int collisionSink(short record, SurfaceShape shape) {
        SurfaceFamily appearance = ErosionState.familyOf(record);
        if (appearance == null || !shows(record)) return 0;
        return SinkProfile.collides(appearance, ErosionState.sinkOf(record), shape);
    }

    /**
     * The outline of the block this ghost covers, in sixteenths, packed floor and top.
     *
     * <p>
     * Asked of that block's own state, through the view the asker was handed, which is how the game asks
     * every block where its edges are. A block that will not answer is taken for a whole cube, which is what
     * every ghost was before shapes were let in.
     */
    public static int outlineAt(IBlockAccess world, BlockPos pos, int origin) {
        if (origin < 0) return WHOLE_CUBE;
        try {
            return outlineOf(Block.getStateById(origin), world, pos);
        } catch (RuntimeException awkwardBlock) {
            return WHOLE_CUBE;
        }
    }

    /**
     * The outline of a block that is really there, packed the same way.
     *
     * <p>
     * The one rule both sides reach for. A client asks it of the block its ghost is standing in for and a
     * server asks it of the block itself, and because it is one method they cannot come to different answers
     * - which matters more than it sounds: a client that believes the ground is a pixel lower than the server
     * does is a client walking into what the server calls solid, and the server puts it back without a word.
     */
    public static int outlineOf(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state == null) return WHOLE_CUBE;
        try {
            AxisAlignedBB box = state.getBoundingBox(world, pos);
            if (box == null) return WHOLE_CUBE;
            int floor = clampSixteenths(box.minY, 0);
            int top = clampSixteenths(box.maxY, 16);
            if (top <= floor) return WHOLE_CUBE;
            return (top << 8) | floor;
        } catch (RuntimeException awkwardBlock) {
            return WHOLE_CUBE;
        }
    }

    private static int clampSixteenths(double value, int fallback) {
        if (Double.isNaN(value)) return fallback;
        long rounded = Math.round(value * 16.0D);
        if (rounded < 0L) return 0;
        return rounded > 16L ? 16 : (int) rounded;
    }

    /** The floor of a packed outline, as a fraction of a block. */
    public static float floorOf(int outline) {
        return (outline & 0xFF) / 16F;
    }

    /** The top of a packed outline, as a fraction of a block, before anything has sunk. */
    public static float topOf(int outline) {
        return ((outline >> 8) & 0xFF) / 16F;
    }

    /**
     * Whether snow lies on this square.
     *
     * <p>
     * Asked of the block above through the view the asker was handed, which on a mesher thread is that
     * thread's own snapshot. By material, as the other edition asks it: every snow layer in a pack is made of
     * snow, and a lawn under one shows its snowed flanks rather than its green ones.
     */
    private static boolean snowedAt(IBlockAccess world, BlockPos pos) {
        if (world == null || pos.getY() >= 255) return false;
        try {
            net.minecraft.block.material.Material above = world.getBlockState(pos.up())
                .getMaterial();
            return above == net.minecraft.block.material.Material.SNOW
                || above == net.minecraft.block.material.Material.CRAFTED_SNOW;
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    /**
     * Drawn in the cut-out pass, because grass's fringe is a cut-out texture.
     *
     * <p>
     * Vanilla's own grass block does the same, and for the same reason: in the solid pass a texture's holes
     * are drawn as though they were opaque, so the receding fringe would be a grey rectangle rather than a
     * fringe. Every ghost draws in this pass, as every vanilla grass block does, rather than the block
     * choosing a pass per square - which it cannot, since the pass is asked of the state and a ghost's state
     * is the same everywhere.
     */
    @Override
    public net.minecraft.util.BlockRenderLayer getRenderLayer() {
        return net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED;
    }

    /**
     * Not a full cube, because it is not one.
     *
     * <p>
     * Saying otherwise would have the renderer cull the faces of everything beside it, and would
     * have light stop at it. Both of those are wrong for a square with a dip in it, and the second
     * is the one a player notices: a road that darkened its own verges.
     */
    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    /**
     * How much light this square takes out of what passes through it.
     *
     * <p>
     * All of it until the ground sinks, and none once it has. A worn-but-unsunken square is still a
     * whole block of earth and stops light exactly as the block it stands in for did; letting light
     * through it lit the cell below a road, and caves under one.
     *
     * <p>
     * The 1.7.10 edition gets this from {@code isOpaqueCube}, which it can answer because it keeps a
     * separate sunken variant of every ghost - {@code !sunken && !clear && !window}. One ghost
     * standing in for everything cannot answer that from its state, and {@code isOpaqueCube} is
     * asked of the state alone, so it stays false and the question is answered per position here
     * instead. This is the position-aware opacity Forge adds for exactly this kind of block.
     */
    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (sunkAt(world, pos)) return 0;
        // Worn ice stops the light ice stops - the 1.7.10 edition's {@code clear ? lightOpacityFor :
        // 255}, missing here until 0.9.219, when a path across a frozen lake darkened the water under it.
        IBlockState covered = clearCovers(world, pos);
        return covered == null ? 255 : covered.getLightOpacity();
    }

    /**
     * Whether a neighbour may leave off the face it has against this square.
     *
     * <p>
     * It may, where this square is still a whole block. The 1.7.10 edition answers this through
     * {@code isOpaqueCube}, which it can make {@code !sunken && !clear && !window} because it keeps a
     * separate sunken variant of every ghost; one ghost standing in for all of them cannot, because
     * that question is asked of the state alone. So the state-level answer stays no - which is what
     * keeps a sunken square from hiding the sides its hollow has just exposed - and the position-level
     * one is given here, where Forge does hand the position in.
     *
     * <p>
     * Without it the neighbours of every worn square drew faces nobody can see, and the ambient
     * shading in the corners around a worn square came out lighter than around the block it replaced,
     * because that shading is worked out from what occludes.
     *
     * <p>
     * <strong>What this version cannot carry:</strong> {@code isOpaqueCube} itself stays false, so
     * anything that reads opaqueness straight from the state rather than asking per position still
     * sees a ghost as not opaque. Light is answered separately and per position in
     * {@link #getLightOpacity}; this is the other half.
     */
    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos,
        net.minecraft.util.EnumFacing side) {
        if (sunkAt(world, pos)) return false;
        // Nor where it is clear: worn ice is still seen through, so the face beside it is still seen - the
        // 1.7.10 edition's {@code !clear}. Dropped until 0.9.219, which the close-ups of the first yard
        // found: the real ice under a worn top and the worn squares beside it left off every face they
        // shared with it, and the yard's ice read as a pane with the next platform showing through.
        if (clearCovers(world, pos) != null) return false;
        int origin = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        // Nor where it is a window: its holes show the cell behind them, so the faces in that cell are
        // drawn - the third clause of the 1.7.10 edition's {@code !sunken && !clear && !window}, which
        // this lacked until 2026-10-08. With the pass alone put right, the block below and the squares
        // beside a worn waterstone went on leaving off the faces it shares with them, and its water
        // showed an empty cell.
        if (Trmt.proxy.ghostWindowOf(origin)) return false;
        int outline = outlineAt(world, pos, origin);
        // A whole cube only. A slab or a path stands short, and a neighbour that left off its face
        // against one would show a hole where the square stops.
        return floorOf(outline) <= 0F && topOf(outline) >= 1F;
    }

    /**
     * Two windows side by side hide the face between them, the way two panes of glass do - the
     * 1.7.10 edition's {@code BlockGhost.shouldSideBeRendered}, carried on 2026-10-08.
     *
     * <p>
     * Without it a road of worn waterstone draws every face inside it, each a blue sheet seen through
     * the one in front, now that a window no longer hides the faces beside it. Only squares that share a
     * pane take the shortcut: whole ones, because two hollows of different depths leave part of the
     * taller one's side open onto its own rut, and wearing as the same family, because the other
     * edition's rule asks for the same window twin. See {@code GhostWindows.paneAt}.
     */
    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos,
        net.minecraft.util.EnumFacing side) {
        int pane = com.trmtgtnh.client.model.GhostWindows.paneAt(world, pos);
        if (pane != 0 && pane == com.trmtgtnh.client.model.GhostWindows.paneAt(world, pos.offset(side))) return false;
        return super.shouldSideBeRendered(state, world, pos, side);
    }

    /**
     * Whether this square has dropped below the top of the block it stands in for.
     *
     * <p>
     * The question the opacity above turns on, and the one the other edition answers by having a
     * sunken variant of each ghost rather than by asking the position.
     */
    private static boolean sunkAt(IBlockAccess world, BlockPos pos) {
        return ErosionState.sinkOf(Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ())) > 0;
    }

    /**
     * Whether this square stands in for a whole block, and so shades the corners beside it as that block did.
     *
     * <p>
     * The 1.7.10 edition's {@code renderAsNormalBlock}, which is {@code !sunken} there - and its sunken variant is
     * the hollowed one, chosen once the ground has sunk or from the start when the block covered is short: a
     * slab, a stair, a path ({@code OverlayPainter.wantedGhost}, {@code shortBase || sink > 0}). Vanilla's
     * ambient occlusion reads that through {@code isBlockNormalCube}, so there a whole ghost darkens the corners
     * of the faces beside it, as the grass or stone it replaced did, and a hollowed one does not.
     *
     * <p>
     * Missing here until 0.9.219, when the first yard's close-ups showed it: sunk earth beside a worn square
     * that had not sunk was shaded toward it on 1.7.10 and evenly lit here, so a rut's edge drew flat. This
     * version asks it of the state alone ({@code getAmbientOcclusionLightValue}), which cannot say - one ghost
     * stands in for everything - so the renderers' own readings are corrected per position instead: Forge's
     * light pipeline in {@link com.trmtgtnh.mixin.MixinGhostShadeForge}, and vanilla's, which OptiFine draws
     * through, in {@link com.trmtgtnh.mixin.MixinGhostShadeVanilla}.
     */
    public static boolean wholeAt(IBlockAccess world, BlockPos pos) {
        if (sunkAt(world, pos)) return false;
        int origin = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        // Nothing known about what it covers: 1.7.10's isShort(null), which is no.
        if (origin < 0) return true;
        IBlockState covered = Block.getStateById(origin);
        if (com.trmtgtnh.surface.SurfaceShape.of(covered.getBlock())
            .isPartial()) return false;
        try {
            return covered.getBoundingBox(world, pos).maxY >= 0.999D;
        } catch (RuntimeException awkwardBlock) {
            return true;
        }
    }

    /** The shade a whole ghost gives the corners beside it - vanilla's for a normal cube. See {@link #wholeAt}. */
    public static final float WHOLE_SHADE = 0.2F;

    /**
     * The renderers seen reading a whole ghost's shade, each said once - proof the correction runs, not only applies.
     */
    private static final java.util.Set<String> SHADE_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Says, once per renderer, that a whole ghost has shaded its neighbours through it. */
    public static void shadeSeen(String renderer) {
        if (SHADE_SEEN.add(renderer))
            Trmt.LOG.info("Ghost shade: a whole ghost shades the corners beside it through {}", renderer);
    }

    /**
     * The block a clear square covers, or null when the square is not clear.
     *
     * <p>
     * Clear is the 1.7.10 edition's word, and its rule: the square is worn as ice, and the block under it
     * does not fill its square to look at - ice, and not packed ice, which that edition gives a solid twin
     * because a pack's decorative frost is ice by material and as solid to look at as stone. A square whose
     * covered block is not known is clear, as that edition's ice stand-in is until told otherwise.
     *
     * <p>
     * Public for {@code GhostWindows.paneAt}: clear ice has no window twin in that edition, so it shares
     * no pane.
     */
    @Nullable
    public static IBlockState clearCovers(IBlockAccess world, BlockPos pos) {
        short record = Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ());
        if (ErosionState.familyOf(record) != SurfaceFamily.ICE) return null;
        int origin = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        IBlockState covered = origin < 0 ? null : Block.getStateById(origin);
        if (covered == null) covered = net.minecraft.init.Blocks.ICE.getDefaultState();
        return covered.isOpaqueCube() ? null : covered;
    }

    @Override
    public boolean isTopSolid(IBlockState state) {
        return true;
    }

    /**
     * What a map paints this square, which is what the ground underneath would be painted.
     *
     * <p>
     * Everything that draws a map reads this - the vanilla map item, every minimap, and anything
     * rendering the world at a distance - so answering with the origin's own color makes a worn path
     * read as the ground it is rather than as an unknown block, once and for all of them.
     *
     * <p>
     * <strong>This is where the other edition needs per-mod code and this one does not.</strong>
     * There the question is only {@code getMapColor(int metadata)}: a ghost is asked what color it
     * is with no way to know which square is being asked about, so it can answer only from the family
     * its own class stands for - and a minimap that ignored the answer, as JourneyMap did for
     * anything descending from the grass block, had to be reached into by reflection and corrected.
     * 1.12.2 hands the position in. A ghost can look up its own origin and answer truthfully, and
     * there is nothing left for either minimap integration to fix.
     */
    @Override
    public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
        short record = Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ());
        int packed = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed >= 0) {
            IBlockState origin = Block.getStateById(packed);
            if (origin != null && origin.getBlock() != this) {
                try {
                    MapColor own = origin.getBlock()
                        .getMapColor(origin, world, pos);
                    // Darkened, so there is a road on the map rather than only a change of material
                    // where one has worn through. One color rather than a shade per gradation,
                    // which is all sixty-four fixed palette entries can carry - see GhostMapColor.
                    if (own != null) return shows(record) ? GhostMapColor.worn(own) : own;
                } catch (RuntimeException hostileBlock) {
                    // A block of somebody else's asked about a position it does not own. Its family's
                    // stand-in below is a better answer than taking the map down.
                }
            }
        }
        // No record, or a block that would not say: the family this square is drawn as.
        if (com.trmtgtnh.erosion.ErosionState.familyOf(record) == null) {
            return super.getMapColor(state, world, pos);
        }
        MapColor earth = net.minecraft.init.Blocks.DIRT.getDefaultState()
            .getMapColor(world, pos);
        return shows(record) ? GhostMapColor.worn(earth) : earth;
    }

    /**
     * What a middle-click picks up, which is the block this ghost is standing over.
     *
     * <p>
     * A ghost has no item form of its own and should not: it is a cosmetic block nobody is meant to
     * hold. What it can do is answer with the real ground underneath, which is what a player means by
     * picking a worn square - and it is also what a block-tooltip mod reads to name the thing under
     * the crosshair, so without this a worn square gets no tooltip at all rather than a wrong one.
     *
     * <p>
     * Built from the recorded original rather than from the world, because the state here is the
     * ghost's own. What the record holds is a whole block state id - which is the other edition's
     * one real difference here: there the origin is an id with four bits of metadata beside it, and
     * carrying that arithmetic across produced a pick of air and a worn square with no tooltip.
     */
    @Override
    public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult target, World world,
        BlockPos pos, net.minecraft.entity.player.EntityPlayer player) {
        int packed = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed >= 0) {
            IBlockState origin = Block.getStateById(packed);
            if (origin != null && origin.getBlock() != this) {
                Item item = Item.getItemFromBlock(origin.getBlock());
                if (item != null && item != net.minecraft.init.Items.AIR) {
                    return new ItemStack(
                        item,
                        1,
                        origin.getBlock()
                            .damageDropped(origin));
                }
            }
        }
        return super.getPickBlock(state, target, world, pos, player);
    }

    /** Nothing drops, because nothing is really there. */
    @Override
    @Nullable
    public Item getItemDropped(IBlockState state, java.util.Random rand, int fortune) {
        return null;
    }

    @Override
    public int quantityDropped(java.util.Random random) {
        return 0;
    }

    /** Whether this world may hold ghosts at all. Only ever a client's own copy of one. */
    public static boolean paintable(World world) {
        return world != null && world.isRemote;
    }

    /** Whether a record says anything is worn here at all. */
    public static boolean shows(short record) {
        return record != ErosionState.NONE && ErosionState.layerOf(record) >= 0;
    }

    // ------------------------------------------------------------------
    // What the covered block goes on doing
    // ------------------------------------------------------------------

    /**
     * The covered block's own ambient particles, still scattered.
     *
     * <p>
     * Handed straight on. See {@link GhostInherit}, which holds the reasoning and the recursion
     * guard for this and for the collision below.
     */
    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, java.util.Random random) {
        GhostInherit.ambientTick(this, state, world, pos, random);
    }

    /** Whatever the covered block does to something standing inside it, still done. */
    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, net.minecraft.entity.Entity entity) {
        GhostInherit.entityCollided(this, world, pos, state, entity);
    }
}
