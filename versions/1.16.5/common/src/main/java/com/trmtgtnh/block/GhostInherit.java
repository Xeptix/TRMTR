package com.trmtgtnh.block;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.Client;
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
 * The third is a thing the block <em>is</em>, and it was missing from this edition until 2026-10-06:
 * the footing it insists on. This javadoc used to say "two of those ways" and mean it, which is how
 * the absence went unseen - a list that sounds complete is not a list anybody checks.
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
     * fill their square and stand short, and both go on wearing as they always have. A stair's shape
     * is not full either, but the union of its boxes is, so it is not caught here - and it is
     * answered before this anyway.
     *
     * <p>
     * Both sides ask the same question of the same block: the server through
     * {@code PhysicalDecay.fillsItsFootprint}, the client through here. A footing derived from two
     * rules is two machines disagreeing about where somebody is standing.
     *
     * @return the block's own shape when it is narrower than its square, an empty shape when it
     *         insists on no footing at all, and null when it takes whatever footing it is given
     */
    public static VoxelShape ownFootingAt(BlockGetter world, BlockPos pos, int origin) {
        if (origin < 0 || world == null || pos == null) return null;
        if (!enter()) return null;
        try {
            BlockState under = Block.stateById(origin);
            if (under == null || under.getBlock() instanceof BlockGhost) return null;

            VoxelShape own = under.getCollisionShape(world, pos);
            if (own == null) return null;
            if (own.isEmpty()) return own;

            net.minecraft.world.phys.AABB bounds = own.bounds();
            boolean narrow = bounds.minX > TOLERANCE || bounds.minZ > TOLERANCE
                || bounds.maxX < 1.0D - TOLERANCE
                || bounds.maxZ < 1.0D - TOLERANCE;
            return narrow ? own : null;
        } catch (RuntimeException awkwardBlock) {
            // A block that will not say what shape it is still gets the usual footing rather than
            // taking a chunk's collision pass down with it.
            return null;
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
     * <strong>Never entered on a server, and said rather than annotated.</strong> 1.12.2 marks this
     * {@code SideOnly(CLIENT)}; there is no such annotation available to a module both loaders share,
     * so the promise is kept by the shape of the code instead. The only caller is the ghost's own
     * override of the game's client-side animate tick, which a dedicated server never runs, and the
     * first thing below is a turn-back on any world that is not a client's. The one call in here
     * that exists only on a client is on somebody else's block and is reached after that check.
     */
    public static void ambientTick(Block ghost, BlockState state, Level world, BlockPos pos, Random random) {
        if (!TrmtConfig.inheritAmbientParticles) return;
        if (world == null || !world.isClientSide()) return;

        int packed = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed < 0) return;
        BlockState origin = Block.stateById(packed);
        if (origin == null || origin.getBlock() == ghost || origin.getBlock() instanceof BlockGhost) return;
        try {
            origin.getBlock()
                .animateTick(origin, world, pos, random);
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
    public static void entityCollided(Block ghost, Level world, BlockPos pos, BlockState state, Entity entity) {
        if (!TrmtConfig.inheritEntityCollision) return;
        if (world == null || !world.isClientSide() || entity == null) return;
        if (!predicted(entity)) return;

        int packed = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed < 0) return;
        BlockState origin = Block.stateById(packed);
        if (origin == null || origin.getBlock() == ghost || origin.getBlock() instanceof BlockGhost) return;
        // The identity checks above are the recursion guard's first two hops and this is its third.
        // Nothing in vanilla forwards this call, but a block that asked the world what was at its own
        // position and called this on the answer would come straight back in for ever - and the game
        // wraps whatever comes out of here in a crash report and rethrows it, so an overflow here is
        // not a dropped tick.
        if (!enter()) return;
        try {
            origin.getBlock()
                .entityInside(origin, world, pos, entity);
        } catch (RuntimeException awkwardBlock) {
            Trmt.LOG.debug("Block {} refused an entity collision through a ghost", origin.getBlock(), awkwardBlock);
        } finally {
            leave();
        }
    }

    /**
     * Whether this machine is the one predicting where this entity goes.
     *
     * <p>
     * Three names for one question. 1.7.10 asks {@code isClientWorld}, which is the name MCP gave it
     * before deciding it was backwards; 1.12.2 asks {@code isServerWorld}; and this version asks
     * {@code isEffectiveAi}, which is at last a name for what it answers - whether this machine is
     * the one deciding where this entity goes. A client's own player answers true to all three. A
     * passenger is asked as well, because a player riding something is the case where the thing
     * being moved is not the thing whose machine is moving it.
     */
    private static boolean predicted(Entity entity) {
        if (entity instanceof LivingEntity && ((LivingEntity) entity).isEffectiveAi()) return true;
        Entity rider = entity.getControllingPassenger();
        return rider instanceof LivingEntity && ((LivingEntity) rider).isEffectiveAi();
    }
}
