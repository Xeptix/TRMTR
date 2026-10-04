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
            new IUnlistedProperty[] { RECORD, ORIGIN, ROTATION, FRINGE_TURN, SNOWED, OUTLINE });
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
            .withProperty(OUTLINE, Integer.valueOf(outlineAt(world, pos, origin)));
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
        int outline = outlineAt(world, pos, Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ()));
        int sink = collisionSink(Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ()), shapeOf(outline));
        return box(outline, sink);
    }

    /**
     * The outline, which follows the picture rather than the footing.
     *
     * <p>
     * In visual mode the two differ: the ground is drawn sunk and walked on at full height. The box a
     * player aims at should be the one they can see.
     */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        int outline = outlineAt(world, pos, Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ()));
        int sink = drawnSink(Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ()), shapeOf(outline));
        return box(outline, sink);
    }

    /** The outline, less however far it has sunk from its own top. */
    private static AxisAlignedBB box(int outline, int sink) {
        double floor = floorOf(outline);
        double top = Math.max(floor, topOf(outline) - sink / 16.0D);
        return new AxisAlignedBB(0.0D, floor, 0.0D, 1.0D, top, 1.0D);
    }

    /**
     * Which of the mod's shapes an outline is, for the rule that halves how far a shape may sink.
     *
     * <p>
     * By its own thickness rather than by asking the block again: half a block of stone cannot lose eight
     * pixels and still be there, and that is true of anything half a block thick whatever class it is.
     */
    public static SurfaceShape shapeOf(int outline) {
        return topOf(outline) - floorOf(outline) <= 0.5F ? SurfaceShape.SLAB : SurfaceShape.FULL;
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

    @Override
    public boolean isTopSolid(IBlockState state) {
        return true;
    }

    /**
     * What a map paints this square, which is what the ground underneath would be painted.
     *
     * <p>
     * Everything that draws a map reads this - the vanilla map item, every minimap, and anything
     * rendering the world at a distance - so answering with the origin's own colour makes a worn path
     * read as the ground it is rather than as an unknown block, once and for all of them.
     *
     * <p>
     * <strong>This is where the other edition needs per-mod code and this one does not.</strong>
     * There the question is only {@code getMapColor(int metadata)}: a ghost is asked what colour it
     * is with no way to know which square is being asked about, so it can answer only from the family
     * its own class stands for - and a minimap that ignored the answer, as JourneyMap did for
     * anything descending from the grass block, had to be reached into by reflection and corrected.
     * 1.12.2 hands the position in. A ghost can look up its own origin and answer truthfully, and
     * there is nothing left for either minimap integration to fix.
     */
    @Override
    public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
        int packed = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed >= 0) {
            IBlockState origin = Block.getStateById(packed);
            if (origin != null && origin.getBlock() != this) {
                try {
                    MapColor own = origin.getBlock()
                        .getMapColor(origin, world, pos);
                    if (own != null) return own;
                } catch (RuntimeException hostileBlock) {
                    // A block of somebody else's asked about a position it does not own. Its family's
                    // stand-in below is a better answer than taking the map down.
                }
            }
        }
        // No record, or a block that would not say: the family this square is drawn as.
        return com.trmtgtnh.erosion.ErosionState
            .familyOf(Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ())) == null
                ? super.getMapColor(state, world, pos)
                : net.minecraft.init.Blocks.DIRT.getDefaultState()
                    .getMapColor(world, pos);
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
