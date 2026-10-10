package com.trmtgtnh.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The parts of the covered block a ghost goes on letting through.
 *
 * <p>
 * A ghost mirrors the block it is covering wherever anything but rendering can tell. Two of those
 * ways are things the block does rather than things it is: scattering its own ambient particles, and
 * acting on whatever stands inside it. Both are plain {@code Block} calls that the ghost receives
 * instead of the block it stands for, so both have to be handed on.
 *
 * <p>
 * The other edition does this from a class of the same name and keeps a third thing in it that lives
 * apart here: the shader material. Both editions have that, and for a while this javadoc said 1.12.2
 * could not, on the strength of 1.12.2's shaders being a different mod with a different hook. The
 * first half was true and the second was the wrong conclusion from it - the hook is there, one step
 * further in than Angelica's - so it moved out to
 * {@link com.trmtgtnh.client.render.ShaderMaterial} rather than staying here as a thing not done.
 */
public final class GhostInherit {

    private GhostInherit() {}

    /**
     * One re-entry flag for everything here that calls into somebody else's block.
     *
     * <p>
     * Per thread, because the client and the server both reach this and neither waits on the other.
     * It is the last hop of the recursion guard rather than the first: the identity checks catch the
     * shapes anybody would write on purpose, and this catches the one nobody would.
     */
    private static final ThreadLocal<Boolean> INSIDE = new ThreadLocal<Boolean>();

    private static boolean enter() {
        if (Boolean.TRUE.equals(INSIDE.get())) return false;
        INSIDE.set(Boolean.TRUE);
        return true;
    }

    private static void leave() {
        INSIDE.remove();
    }

    /** A ten-thousandth of a block, finer than anything states its own edges to. */
    private static final double TOLERANCE = 1.0E-4D;

    /**
     * Stands for "this block takes whatever footing it is given", so that null can go on meaning to
     * this mod what it means to the game, which is that there is nothing here to walk into.
     */
    public static final AxisAlignedBB ORDINARY = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    /**
     * The footing the covered block insists on for itself, or null when it insists on none.
     *
     * <p>
     * Wear is a height taken off the top of a full cell, and that is the right shape for every kind
     * of ground there is - earth, rock, sand, a slab, a made path. It is the wrong shape for a block
     * that occupies less of its square than that. A cloud is a pad a little over half across sitting
     * at the bottom of its cell, and a stand-in handing back a full cell over one turns a block you
     * fall through into a block you stand on. So a block narrower than its own square keeps its own
     * answer, worn or not, and a rut is simply never cut into it.
     *
     * <p>
     * Only the footprint is measured and never the height, because a shorter block is exactly what
     * wear produces and is what the record already knows how to describe. A slab and a grass path
     * fill their square and stand short, and both go on wearing as they always have. A stair is not
     * caught here either - its boxes span the square between them, and it is answered before this.
     *
     * <p>
     * Both sides ask the same question of the same block: the server through
     * {@code PhysicalDecay}, the client through here. A footing derived from two rules is two
     * machines disagreeing about where somebody is standing.
     *
     * <p>
     * The three answers use the 1.7.10 edition's own convention, and they have to: {@code NULL_AABB}
     * is literally {@code null} at this version, so null cannot also mean "no opinion" the way it
     * does on 1.16.5 where a shape has a real empty value.
     *
     * @return the block's own box when it is narrower than its square, null when it insists on no
     *         footing at all, and {@link #ORDINARY} when it takes whatever footing it is given
     */
    public static AxisAlignedBB ownFootingAt(IBlockAccess world, BlockPos pos, int origin) {
        if (origin < 0 || world == null || pos == null) return ORDINARY;
        if (!enter()) return ORDINARY;
        try {
            IBlockState under = Block.getStateById(origin);
            if (under == null || under.getBlock() instanceof BlockGhost) return ORDINARY;

            AxisAlignedBB own = under.getCollisionBoundingBox(world, pos);
            if (own == null) return null;

            boolean narrow = own.minX > TOLERANCE || own.minZ > TOLERANCE
                || own.maxX < 1.0D - TOLERANCE
                || own.maxZ < 1.0D - TOLERANCE;
            return narrow ? own : ORDINARY;
        } catch (RuntimeException awkwardBlock) {
            // A block that will not say what shape it is still gets the usual footing rather than
            // taking a chunk's collision pass down with it.
            return ORDINARY;
        } finally {
            leave();
        }
    }

    /**
     * Lets the block underneath go on scattering its own ambient particles.
     *
     * <p>
     * {@code WorldClient.showAmbientParticles} picks a thousand positions in a cube around the player
     * every tick and calls this on every non-air block it lands on, without consulting whether the
     * block asked to be ticked - so a ghost is asked exactly as often as the turf it replaced and,
     * until now, answered with silence. Mycelium's spores are the vanilla casualty; in a pack of any
     * size anything with weather of its own is one.
     *
     * <p>
     * The covered block is handed the real world and its own state, which is what 1.12.2's signature
     * asks for and is strictly better than the other edition's: there the block is handed the world
     * and a position whose block is the ghost, and almost nothing reads its own metadata because these
     * are decorations rather than state machines. Here it is handed the state it would have had.
     *
     * <p>
     * Marked for the client, like the vanilla method it hands on to and like the ghost's own override
     * that calls it. Nothing on a server can reach this - that override does not exist there - and
     * saying so is what lets the client-only reference scan tell this apart from a real crossing.
     */
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public static void ambientTick(Block ghost, IBlockState state, World world, BlockPos pos, Random random) {
        if (!TrmtConfig.inheritAmbientParticles) return;
        if (world == null || !world.isRemote) return;

        int packed = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed < 0) return;
        IBlockState origin = Block.getStateById(packed);
        if (origin == null || origin.getBlock() == ghost || origin.getBlock() instanceof BlockGhost) return;
        try {
            origin.getBlock()
                .randomDisplayTick(origin, world, pos, random);
        } catch (RuntimeException awkwardBlock) {
            // Third-party code on the client tick. A decoration that cannot draw itself is not a
            // reason to drop the frame.
            Trmt.LOG.debug("Block {} refused an ambient tick through a ghost", origin.getBlock(), awkwardBlock);
        }
    }

    /**
     * Lets the block underneath go on doing what it does to whatever stands inside it.
     *
     * <p>
     * A cloud is what this exists for: it takes an entity's downward motion almost entirely away,
     * and a client that had stopped asking fell through one at the speed of anything else. The
     * server runs the same call against the real block, re-simulating the move it was sent, and then
     * takes the position out of the packet anyway - so a player's fall is their own machine's
     * arithmetic and nobody else's, which is why losing this lost the effect outright rather than
     * halving it.
     *
     * <p>
     * Nothing reaches here unless the footing above has already let something inside the block. The
     * game only asks this of the cells an entity's box actually overlaps, so a stand-in that filled
     * its cell was never asked at all. That is the order the two halves go in, and it is why
     * forwarding this without the footing would have been a call nobody ever makes.
     */
    public static void entityCollided(Block ghost, World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!TrmtConfig.inheritEntityCollision) return;
        if (world == null || !world.isRemote || entity == null) return;
        if (!predicted(entity)) return;

        int packed = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed < 0) return;
        IBlockState origin = Block.getStateById(packed);
        if (origin == null || origin.getBlock() == ghost || origin.getBlock() instanceof BlockGhost) return;
        // The identity checks above are the recursion guard's first two hops and this is its third.
        // Nothing in vanilla forwards this call, but a block that asked the world what was at its own
        // position and called this on the answer would come straight back in for ever - and the game
        // wraps whatever comes out of here in a crash report and rethrows it, so an overflow here is
        // not a dropped tick.
        if (!enter()) return;
        try {
            origin.getBlock()
                .onEntityCollision(world, pos, origin, entity);
        } catch (RuntimeException awkwardBlock) {
            Trmt.LOG.debug("Block {} refused an entity collision through a ghost", origin.getBlock(), awkwardBlock);
        } finally {
            leave();
        }
    }

    // ------------------------------------------------------------------
    // Breaking it, and growing on it: the covered block's answers (0.9.222)
    // ------------------------------------------------------------------

    /**
     * The block a square's hardness and break speed are asked of: the one it covers, and where nothing is recorded
     * under it, its family's own block - see {@link GhostFamily#standIn}. Null with no record at all, where the ghost
     * answers for itself.
     *
     * <p>
     * The 1.7.10 edition gets the second half from its stand-in per family, whose hardness, material and harvest tool
     * are that family's block's; one ghost here has one of each, so the family's block is asked instead.
     */
    @javax.annotation.Nullable
    static IBlockState answeringFor(Block ghost, IBlockAccess world, BlockPos pos) {
        IBlockState covered = coveredAt(ghost, pos);
        if (covered != null) return covered;
        com.trmtgtnh.surface.SurfaceFamily family = GhostFamily.familyAt(world, pos);
        return family == null ? null : GhostFamily.standIn(family);
    }

    /**
     * The real block under a ghost at a square, or null when nothing trustworthy is recorded - the 1.7.10 edition's
     * {@code GhostLogic.coveredAt}: never the ghost itself, never another ghost.
     */
    @javax.annotation.Nullable
    static IBlockState coveredAt(Block ghost, BlockPos pos) {
        if (pos == null) return null;
        int packed = Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed < 0) return null;
        IBlockState origin = Block.getStateById(packed);
        if (origin == null || origin.getBlock() == ghost || origin.getBlock() instanceof BlockGhost) return null;
        return origin;
    }

    /**
     * The covered block's hardness at this square, or NaN to let the ghost answer - the 1.7.10 edition's
     * {@code GhostLogic.blockHardness} (spec GF11). An unbreakable block stays unbreakable, because its figure is
     * handed straight back.
     *
     * <p>
     * That edition turns away a world that is not a client's first; here the proxy does, answering nothing recorded on
     * a server, so the square is never asked about there. And guarded, which that edition is not: a block that asked
     * the world for its own square's hardness would find this ghost and come straight back in.
     */
    public static float blockHardness(Block ghost, World world, BlockPos pos) {
        IBlockState covered = answeringFor(ghost, world, pos);
        if (covered == null) return Float.NaN;
        if (!enter()) return Float.NaN;
        try {
            return covered.getBlockHardness(world, pos);
        } catch (RuntimeException awkwardBlock) {
            return Float.NaN;
        } finally {
            leave();
        }
    }

    /**
     * How fast this player breaks the covered block, tool and all, or NaN to let the ghost answer - the 1.7.10
     * edition's {@code GhostLogic.breakSpeed} (spec GF12).
     *
     * <p>
     * Forge works a break out as the player's speed against the block, over its hardness, over thirty where the tool
     * can harvest it and a hundred where it cannot - and asked of the ghost, that last question was answered by a block
     * of ground that needs no tool, so worn ground a pack gates behind a better pick broke three times too fast on the
     * client and was put back by the server. Worked out here, as that edition does, rather than by handing the whole
     * question to the covered block: Forge's {@code blockStrength} asks the harvest check of the world, which at this
     * square holds the ghost. So the check is asked through {@link OriginView}, which holds the covered block there,
     * and the speed with the covered state.
     */
    public static float breakSpeed(Block ghost, net.minecraft.entity.player.EntityPlayer player, World world,
        BlockPos pos) {
        if (player == null) return Float.NaN;
        IBlockState covered = answeringFor(ghost, world, pos);
        if (covered == null) return Float.NaN;
        if (!enter()) return Float.NaN;
        try {
            float hardness = covered.getBlockHardness(world, pos);
            // Unbreakable underneath means unbreakable here. Nought rather than a negative, because this is a rate.
            if (hardness < 0F) return 0F;
            boolean canHarvest = net.minecraftforge.common.ForgeHooks
                .canHarvestBlock(covered.getBlock(), player, new OriginView(world, pos, covered), pos);
            return player.getDigSpeed(covered, pos) / hardness / (canHarvest ? 30F : 100F);
        } catch (RuntimeException awkwardBlock) {
            return Float.NaN;
        } finally {
            leave();
        }
    }

    /**
     * Whether the covered block would hold this plant, or null to let the ghost answer - the 1.7.10 edition's
     * {@code GhostLogic.sustainsPlant} (spec GF15).
     *
     * <p>
     * Forge decides what a plant may stand on by asking the block beneath whether it is grass, or dirt, or sand - by
     * identity - and a ghost is none of those whatever it is drawn as. The client asks before it sends a placement and
     * sends nothing on a no, so nothing could be planted on worn ground although the server, which holds the real
     * ground, would have taken it; and a plant already there asks again when a neighbour changes, and on a no the
     * client takes it away. Asked of the covered block's own state, through a view that holds it at its square, and
     * guarded against the question coming back here, as that edition's is.
     */
    @javax.annotation.Nullable
    public static Boolean sustainsPlant(Block ghost, IBlockAccess world, BlockPos pos,
        net.minecraft.util.EnumFacing direction, net.minecraftforge.common.IPlantable plantable) {
        IBlockState covered = coveredAt(ghost, pos);
        if (covered == null) return null;
        if (!enter()) return null;
        try {
            return Boolean.valueOf(
                covered.getBlock()
                    .canSustainPlant(covered, new OriginView(world, pos, covered), pos, direction, plantable));
        } catch (RuntimeException awkwardBlock) {
            return null;
        } finally {
            leave();
        }
    }

    /**
     * Whether this machine is the one predicting where this entity goes.
     *
     * <p>
     * The other edition asks {@code isClientWorld}, which is the same method under the name MCP gave
     * it before deciding the name was backwards; 1.12.2 calls it {@code isServerWorld} and a client's
     * own player answers true to it. A passenger is asked as well, because a player riding something
     * is the case where the thing being moved is not the thing whose machine is moving it.
     */
    private static boolean predicted(Entity entity) {
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).isServerWorld()) return true;
        Entity rider = entity.getControllingPassenger();
        return rider instanceof EntityLivingBase && ((EntityLivingBase) rider).isServerWorld();
    }
}
