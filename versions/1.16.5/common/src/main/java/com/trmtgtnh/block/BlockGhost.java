package com.trmtgtnh.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.Client;
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

    /** A whole cube, which is what a ghost stands in for unless the block it covers says otherwise. */
    public static final int WHOLE_CUBE = 16 << 8;

    /**
     * How brightly this square glows, nought to fifteen, as a property of the state rather than an
     * answer to a question.
     *
     * <p>
     * <strong>This is the last thing this port had not carried, and it is carried differently from
     * both older editions.</strong> There a ghost answers {@code getLightValue(state, world, pos)} -
     * a per-position question Forge adds, which the other loader has nothing of. The light engine at
     * this version asks a state and nothing else, so what a square glows has to be in the state: the
     * painter works out the level when it paints and sets it here, and {@code Properties.lightLevel}
     * reads it back. Vanilla's own mechanism, the same on both loaders, and no fork at all.
     *
     * <p>
     * Sixteen states rather than one, which is the cost. This class argues at length against listed
     * properties and that argument still stands - sixteen layers at nine depths across sixteen
     * families is a set nobody should enumerate - but a light level is sixteen values, it is what a
     * blockstate is for, and the model is replaced for every variant on both loaders anyway.
     */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty LIGHT = net.minecraft.world.level.block.state.properties.IntegerProperty
        .create("light", 0, 15);

    public BlockGhost() {
        // Properties handed to super, rather than setters called on itself: that is how 1.13 onward
        // builds a block, and it is where the hardness and the not-occluding now live.
        //
        // Two of the older editions' four settings have nowhere to go. setLightOpacity(255) made a
        // ghost as opaque to light as the ground it covered, so painting one was no change at all
        // and the world did not recompute light at every square painted; useNeighborBrightness had
        // it lit from its neighbours, because its own cell is opaque and a top drawn below that
        // cell's top would come out black. Both are per-block answers this version takes from the
        // shape instead, and whether it gets them right is a thing to look at rather than assume.
        super(
            BlockBehaviour.Properties.of(Material.DIRT)
                .strength(0.6F)
                .noOcclusion()
                // No state cache, because every interesting answer this block gives is per square.
                //
                // <strong>Without this, none of the per-position overrides below ever run.</strong>
                // This version builds a {@code BlockBehaviour$BlockStateBase$Cache} for each state
                // the first time it is used, and fills it by calling the block once with
                // {@code EmptyBlockGetter.INSTANCE} and {@code BlockPos.ZERO} - a view with no world
                // in it, where a ghost cannot look up its own record and answers as though nothing
                // had worn. Every later question is answered from that frozen value, whatever square
                // is being asked about.
                //
                // Proved rather than reasoned: getLightBlock was made to log its caller, and across a
                // whole run - world generation, light, eighty legs of walking, every chunk rebuild -
                // it was called exactly once, by EmptyBlockGetter, before the first ghost existed.
                //
                // What that cost: a sunken square reported that it blocks all light when it blocks
                // none, and Fabric's Indigo renderer - which, unlike vanilla's, reads opacity to
                // decide ambient occlusion - drew the walls beside every sunken square two and a half
                // times too dark. 1.7.10, 1.12.2 and Forge were unaffected and the fault looked like a
                // Fabric rendering bug, which is where four runs were spent before this was found.
                //
                // The cost of the cure is that occlusion, light and shape are now computed per query
                // rather than once per state. That is what a block whose shape depends on the square
                // it is in has to pay, and it is the same answer vanilla gives its own such blocks.
                .dynamicShape()
                .sound(SoundType.GRAVEL)
                // What this square glows, read off the state the painter chose. See LIGHT.
                .lightLevel(state -> state.getValue(LIGHT))
                // Nothing drops, because nothing is really there. Two methods there -
                // getItemDropped answering null and quantityDropped answering nought - and one word
                // here, because what a block drops is a loot table at this version and this is how a
                // block says it has none. Said rather than left out: a block with no table named
                // looks for one under its own name, does not find it, and logs a missing-table
                // warning on every break.
                .noDrops());
        registerDefaultState(
            getStateDefinition().any()
                .setValue(LIGHT, Integer.valueOf(0)));
    }

    @Override
    protected void createBlockStateDefinition(
        net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIGHT);
    }

    /**
     * The state to paint at this square, carrying whatever it should glow.
     *
     * <p>
     * The body is the other editions' {@code getLightValue}: the brighter of what this mod has lit
     * here and what the covered block gives off on its own, so a worn patch over a glowstone block
     * is still as bright as glowstone. What differs is when it is asked - there on every light
     * query, here once as the square is painted, because the engine at this version asks a state
     * rather than a position. {@code OverlayPainter.relight} is what asks again when the answer
     * changes.
     */
    public static BlockState litState(BlockGhost ghost, BlockGetter access, int x, int y, int z, BlockState covered) {
        int lit = GhostLight.levelAt(access, x, y, z);
        int own = 0;
        if (covered != null) {
            try {
                own = covered.getLightEmission();
            } catch (RuntimeException awkwardBlock) {
                own = 0;
            }
        }
        int level = Math.max(lit, own);
        if (level < 0) level = 0;
        if (level > 15) level = 15;
        return ghost.defaultBlockState()
            .setValue(LIGHT, Integer.valueOf(level));
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
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        VoxelShape stair = stairShapeAt(world, pos, origin);
        if (stair != null) return stair;
        // A block narrower than its own square keeps its own footing, worn or not - see
        // GhostInherit.ownFootingAt. Wear is a height off the top of a full cell, which is the wrong
        // shape for a pad that only covers part of one: handing back a full cell over a cloud turns
        // a block you fall through into a block you stand on.
        VoxelShape own = GhostInherit.ownFootingAt(world, pos, origin);
        if (own != null) return own;
        return solidBox(Client.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ()), origin);
    }

    /**
     * The shape of the stair this ghost stands in for, or null when it stands in for anything else.
     *
     * <p>
     * A worn stair keeps the stair's own shape and does not sink. A stair fuses what it looks like
     * and what it collides as into one answer, so a dip in the picture would be a dip the server has
     * not got, and the server would spend every tick pushing whoever stood in it back out of ground
     * it believes is solid. The 1.7.10 edition reached that from the other direction, by extending
     * vanilla's stair block and finding the two could not be separated there either.
     *
     * <p>
     * One answer serves the collision box, the outline a player aims at and the boxes the model
     * draws, which is what keeps the three agreeing.
     */
    public static VoxelShape stairShapeAt(BlockGetter world, BlockPos pos, int origin) {
        if (origin < 0 || world == null) return null;
        try {
            BlockState under = Block.stateById(origin);
            if (com.trmtgtnh.surface.SurfaceShape.of(under) != com.trmtgtnh.surface.SurfaceShape.STAIR) return null;
            VoxelShape shape = under.getShape(world, pos);
            return shape == null || shape.isEmpty() ? null : shape;
        } catch (RuntimeException awkwardBlock) {
            return null;
        }
    }

    /** The boxes that shape is made of, for the model to draw, or null for every other shape. */
    public static java.util.List<net.minecraft.world.phys.AABB> stairBoxesAt(BlockGetter world, BlockPos pos,
        int origin) {
        VoxelShape stair = stairShapeAt(world, pos, origin);
        return stair == null ? null : stair.toAabbs();
    }

    /**
     * The outline, which follows the footing rather than the picture - the 1.7.10 edition's GhostLogic.outlineBox,
     * "Selection follows collision, not the visuals".
     *
     * <p>
     * In visual mode the two differ: the ground is drawn sunk and walked on at full height, and the box a player
     * aims at is the one they stand on. Until 0.9.220 this edition outlined the picture instead; Xep chose the
     * other edition's rule on 2026-10-08. Never nothing, which collision may be - a block you walk through says so
     * with no box at all, and an outline is drawn from whatever it is handed: then the covered block's own outline,
     * which is what is really there and what was being pointed at, and failing that the whole cell.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        VoxelShape stair = stairShapeAt(world, pos, origin);
        if (stair != null) return stair;
        VoxelShape own = GhostInherit.ownFootingAt(world, pos, origin);
        VoxelShape solid = own != null ? own
            : solidBox(Client.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ()), origin);
        if (solid != null && !solid.isEmpty()) return solid;
        BlockState covered = coveredState(origin);
        if (covered != null) {
            try {
                VoxelShape mine = covered.getShape(world, pos);
                if (mine != null && !mine.isEmpty()) return mine;
            } catch (RuntimeException awkwardBlock) {
                // An outline in the wrong place is a smaller fault than none; the whole cell below.
            }
        }
        return Shapes.block();
    }

    /**
     * The box a worn square is stood on - the 1.7.10 edition's GhostLogic.solidBox, rule for rule.
     *
     * <p>
     * A slab wears from its own top, by the collision depth its shape allows, and stops at its own floor. Anything
     * else is a full cell less the collision depth, measured from the top of the cell, not from the top of the
     * block - which is the same thing for a whole cube, and for a block already short, a grass path, means its
     * first pixel of wear goes into its own shortfall. Until 0.9.220 this edition took every depth off the block's
     * own top, a pixel deeper for a path; Xep chose the other edition's rule on 2026-10-08.
     */
    static VoxelShape solidBox(short record, int origin) {
        BlockState covered = coveredState(origin);
        SurfaceShape shape = SurfaceShape.of(covered);
        if (shape.isPartial()) {
            double floor = SurfaceShape.bottomOf(shape, covered);
            double top = SurfaceShape.topOf(shape, covered);
            return box(floor, Math.max(floor, top - collisionSink(record, shape) / 16.0D));
        }
        return box(0.0D, SinkProfile.heightFor(collisionSink(record, SurfaceShape.FULL)));
    }

    /**
     * The height a square stands at, drawn (footing false) or stood on (footing true) - the 1.7.10 edition's
     * GhostLogic.heightAt, rule for rule.
     *
     * <p>
     * A slab: its own top less the depth its shape allows - the drawn one, or the collision one for footing - and
     * never below its own floor. Anything else: the block's own top, or the cell's top less the drawn depth if that
     * is lower. <strong>The drawn depth for footing too</strong>, on anything but a slab, which is that edition's
     * code rather than its comment: the comment says snow resting on worn ground in visual mode stays at walking
     * height, the code draws it down into the rut, and Xep chose the code on 2026-10-08.
     */
    public static double heightAt(short record, int origin, int outline, boolean footing) {
        BlockState covered = coveredState(origin);
        SurfaceShape shape = SurfaceShape.of(covered);
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
        BlockState covered = coveredState(origin);
        SurfaceShape shape = SurfaceShape.of(covered);
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
    private static BlockState coveredState(int origin) {
        if (origin < 0) return null;
        try {
            return Block.stateById(origin);
        } catch (RuntimeException awkwardBlock) {
            return null;
        }
    }

    /**
     * A box from a floor to a top, never of no height.
     *
     * <p>
     * Which both older editions can hand back and this version cannot afford to: a box with no height there is a
     * thin block, and an empty VoxelShape here is an absence - a player would fall through the square rather than
     * stand low in it.
     */
    private static VoxelShape box(double floor, double top) {
        double held = Math.max(floor + (1.0D / 16.0D), top);
        return Shapes.box(0.0D, floor, 0.0D, 1.0D, Math.min(1.0D, held), 1.0D);
    }

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
    public static int outlineAt(BlockGetter world, BlockPos pos, int origin) {
        if (origin < 0) return WHOLE_CUBE;
        try {
            return outlineOf(Block.stateById(origin), world, pos);
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
    public static int outlineOf(BlockState state, BlockGetter world, BlockPos pos) {
        if (state == null) return WHOLE_CUBE;
        try {
            // The shape rather than a bounding box, and its bounds rather than the shape itself:
            // what is wanted is the one span a ghost can sink within, and a shape with holes has no
            // such span. An empty shape is a block with no outline at all - a plant, a torch - and
            // is taken for a whole cube, which is what every ghost was before shapes were let in.
            VoxelShape outline = state.getShape(world, pos);
            if (outline == null || outline.isEmpty()) return WHOLE_CUBE;
            AABB box = outline.bounds();
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
    public static boolean snowedAt(BlockGetter world, BlockPos pos) {
        if (world == null || pos.getY() >= 255) return false;
        try {
            net.minecraft.world.level.material.Material above = world.getBlockState(pos.above())
                .getMaterial();
            return above == net.minecraft.world.level.material.Material.TOP_SNOW
                || above == net.minecraft.world.level.material.Material.SNOW;
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
     *
     * <p>
     * It went unsaid for the whole of this port and the sentence above describes what that looked
     * like exactly: a grey rectangle over each face, with the real fringe as a mottled band along its
     * top edge. The comment claiming the client module did it was written before the client module
     * did, which is the same shape as the three other things this edition had and never called.
     */
    // getRenderLayer is not a block's answer here. Which pass a block draws in is registered on the
    // client at start-up and each loader does it in its own client setup: Forge through
    // ItemBlockRenderTypes.setRenderLayer, which is Forge's patch onto vanilla's class, and Fabric
    // through BlockRenderLayerMap. Cut-out mipped on both, for the reason above.

    /**
     * Not a full cube, because it is not one.
     *
     * <p>
     * Saying otherwise would have the renderer cull the faces of everything beside it, and would
     * have light stop at it. Both of those are wrong for a square with a dip in it, and the second
     * is the one a player notices: a road that darkened its own verges.
     */
    // isFullCube and isOpaqueCube are said once in Properties.noOcclusion now, rather than
    // answered per call. The reason is the older editions': a square with a dip in it must not have
    // the renderer cull its neighbours' faces, and must not stop light - a road that darkened its
    // own verges is the one a player notices.

    /**
     * How much light a neighbour's face keeps where it touches this one: a fifth where this square stands in
     * for a whole block, as that block did, and all of it where the square is hollowed.
     *
     * <p>
     * <strong>{@code noOcclusion()} does not cover this, and that is the trap.</strong> Vanilla
     * decides this number from the <em>collision</em> shape rather than from occlusion -
     * {@code isCollisionShapeFullBlock() ? 0.2F : 1.0F}. Left to that, a sunken square answered a fifth
     * too, and the full blocks round a sunken patch drew dark edges where the worn ground had exposed
     * their sides - which somebody spotted from a screenshot. So this answered all of it, everywhere.
     *
     * <p>
     * <strong>Everywhere was one clause too many.</strong> The 1.7.10 edition answers this through
     * {@code isBlockNormalCube}, which for a ghost is {@code renderAsNormalBlock}, which is {@code !sunken} -
     * and its sunken variant is the hollowed one, chosen once the ground has sunk or from the start when the
     * block covered is short ({@code OverlayPainter.wantedGhost}, {@code shortBase || sink > 0}). So there a
     * worn square that has not sunk shades the corners beside it as the grass it replaced did, and a rut's
     * edge is shaded toward the step. This said otherwise until 0.9.219, when the first yard's close-ups
     * showed sunk earth beside an unsunken square drawn evenly lit here and shaded on 1.7.10; and the comment
     * here said both older editions answered from opacity, "which a ghost already answers no to" - true of
     * the hollowed variant only. See {@link #wholeAt}.
     */
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return wholeAt(level, pos) ? WHOLE_SHADE : 1.0F;
    }

    /** The shade a whole ghost gives the corners beside it - vanilla's for a full block. See {@link #wholeAt}. */
    public static final float WHOLE_SHADE = 0.2F;

    /**
     * Whether this square stands in for a whole block: it has not sunk, and the block it covers is neither a
     * partial shape - a slab, a stair - nor short of its cell, as a path or farmland is. The 1.7.10 edition's
     * {@code !(shortBase || sink > 0)}, which picks its hollowed variant. Nothing known about the block
     * covered is 1.7.10's {@code isShort(null)}: not short.
     */
    public static boolean wholeAt(BlockGetter world, BlockPos pos) {
        if (sunkAt(world, pos)) return false;
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        BlockState covered = origin < 0 ? null : Block.stateById(origin);
        if (covered == null || covered.getBlock() instanceof BlockGhost) return true;
        if (SurfaceShape.of(covered)
            .isPartial()) return false;
        try {
            return covered.getShape(world, pos)
                .max(net.minecraft.core.Direction.Axis.Y) >= 0.999D;
        } catch (RuntimeException awkwardBlock) {
            return true;
        }
    }

    // isTopSolid is not answered here any more. It was a flag a block set; it is read off the
    // collision shape now, and a ghost's collision shape is the hollow a rut actually has - so the
    // same answer comes out of the shape the block was already returning.

    /**
     * Whether this square has dropped below the top of the block it stands in for.
     *
     * <p>
     * The question both light answers below turn on, and the one the 1.7.10 edition answers by having
     * a separate sunken variant of every ghost: there {@code isOpaqueCube} is
     * {@code !sunken && !clear && !window}, so a worn-but-unsunken square is a full opaque block and
     * behaves like the one it replaced. One ghost cannot answer that from its state, so it is asked
     * of the position instead.
     */
    private static boolean sunkAt(BlockGetter world, BlockPos pos) {
        return ErosionState.sinkOf(Client.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ())) > 0;
    }

    /**
     * Skylight passes through a square that has sunk, as it does a slab, and not through one that
     * has not.
     *
     * <p>
     * Decided from the collision shape by default, and wrong here for the same reason the shade is:
     * a worn square is open to the sky it has sunk away from. Measured before this was written - the
     * cell of a worn square sat one light level below the open air above it, where a slab's cell sits
     * level with it.
     *
     * <p>
     * <strong>This method and the one below it were documented and then not written.</strong> The
     * javadoc above sat in the file with no method under it, and being a block comment it swallowed
     * the javadoc of the next method as well. So the measurement was recorded, the behaviour was
     * described, and the file compiled without either - which is the same fault as every other
     * comment in this port that promised wiring nobody had done.
     */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        if (sunkAt(world, pos)) return true;
        // Otherwise as the block it covers - see coveredFor.
        BlockState covered = coveredFor(world, pos);
        return covered != null && covered.propagatesSkylightDown(world, pos);
    }

    /**
     * The block an unsunken square answers light as: the one it covers.
     *
     * <p>
     * "Stops light exactly as the block it stands in for did" is the rule {@link #getLightBlock} states, and
     * until 0.9.219 it was written as fifteen, which is that answer only for a whole block of earth. Two
     * kinds of square it was wrong for:
     * <ul>
     * <li><em>Ice</em>, which the 1.7.10 edition calls clear and gives ice's own figure - {@code clear ?
     * lightOpacityFor(appearance) : 255} - where fifteen made a path across a frozen lake darken the water
     * under it.</li>
     * <li><em>A stair, a slab, a path</em> - anything that stops short of filling its square - whose faces
     * inside the square are lit from the square itself. Vanilla's renderer does that for every face not on
     * the cell's edge, and a square answering fifteen holds no light, so on Forge every worn but unsunken
     * stair in the second yard drew its riser black. The older editions never meet it: 1.7.10's stair
     * stand-in, and every 1.12.2 ghost, take their light from the brightest neighbour, a flag this version
     * does not have. Answering as the stair does lets the light in, as it comes into a stair.</li>
     * </ul>
     *
     * <p>
     * Null when the covered block is not known, which is the whole block of earth - except where the square
     * is worn as ice, which is ice until told otherwise, as that edition's stand-in is.
     */
    static BlockState coveredFor(BlockGetter world, BlockPos pos) {
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        BlockState covered = origin < 0 ? null : Block.stateById(origin);
        if (covered != null && !(covered.getBlock() instanceof BlockGhost)) return covered;
        short record = Client.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ());
        return ErosionState.familyOf(record) == com.trmtgtnh.surface.SurfaceFamily.ICE
            ? net.minecraft.world.level.block.Blocks.ICE.defaultBlockState()
            : null;
    }

    /**
     * How much light this square takes out of what passes through it.
     *
     * <p>
     * All of it until the ground sinks, and none once it has. A worn-but-unsunken square is still a
     * whole block of earth and stops light exactly as the block it stands in for did; letting light
     * through it lit the cell below a road, and caves under one. The 1.7.10 edition gets this from
     * {@code isOpaqueCube}, which its unsunken variants answer yes to.
     *
     * <p>
     * Not left to the default, which reads {@code canOcclude} - said once for the block rather than
     * per square, and no for this one, so every ghost leaked light whatever had happened to it.
     */
    @Override
    public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        if (sunkAt(world, pos)) return 0;
        // Otherwise as the block it covers - see coveredFor.
        BlockState covered = coveredFor(world, pos);
        return covered == null ? 15 : covered.getLightBlock(world, pos);
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
    public static MaterialColor mapColorAt(BlockState state, BlockGetter world, BlockPos pos) {
        short record = Client.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ());
        int packed = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed >= 0) {
            BlockState origin = Block.stateById(packed);
            // Any ghost rather than this one, since there is no "this" to compare against: the
            // question is asked of the state at this version and a mixin asks this on its behalf.
            if (origin != null && !(origin.getBlock() instanceof BlockGhost)) {
                try {
                    MaterialColor own = origin.getMapColor(world, pos);
                    // Darkened, so there is a road on the map rather than only a change of material
                    // where one has worn through. One color rather than a shade per gradation,
                    // which is all sixty-four fixed palette entries can carry - see GhostMapColor,
                    // which says what that costs and what it keeps.
                    if (own != null) return shows(record) ? GhostMapColor.worn(own) : own;
                } catch (RuntimeException hostileBlock) {
                    // A block of somebody else's asked about a position it does not own. Its family's
                    // stand-in below is a better answer than taking the map down.
                }
            }
        }
        // No record, or a block that would not say: the family this square is drawn as.
        if (com.trmtgtnh.erosion.ErosionState.familyOf(record) == null) return null;
        MaterialColor earth = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()
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
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
        int packed = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed >= 0) {
            BlockState origin = Block.stateById(packed);
            if (origin != null && origin.getBlock() != this) {
                // One stack of the block itself. The metadata the other edition carries beside
                // it is gone - a block state is a state, and the item it answers to is the item of
                // that block - so damageDropped has nothing left to ask.
                Item item = origin.getBlock()
                    .asItem();
                if (item != null && item != net.minecraft.world.item.Items.AIR) return new ItemStack(item);
            }
        }
        return super.getCloneItemStack(world, pos, state);
    }

    // What a ghost drops is said once in Properties.noDrops now, rather than by the two methods
    // that said it there. The reason is in the constructor.

    /** Whether this world may hold ghosts at all. Only ever a client's own copy of one. */
    public static boolean paintable(Level world) {
        return world != null && world.isClientSide();
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
    public void animateTick(BlockState state, Level world, BlockPos pos, java.util.Random random) {
        GhostInherit.ambientTick(this, state, world, pos, random);
    }

    /** Whatever the covered block does to something standing inside it, still done. */
    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        GhostInherit.entityCollided(this, world, pos, state, entity);
    }
}
