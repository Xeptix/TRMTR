package com.trmtgtnh.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The parts of the covered block a ghost goes on letting through.
 *
 * <p>
 * A ghost mirrors the block it is covering wherever anything but rendering can tell, and two of those
 * ways are things the block does rather than things it is: scattering its own ambient particles, and
 * acting on whatever stands inside it. Both are plain {@code Block} calls that the ghost receives
 * instead of the block it stands for, so both have to be handed on.
 *
 * <p>
 * The other edition does this from a class of the same name, and keeps a third thing here that this
 * edition cannot: the shader material. There it reaches into the shader pack's own block-material
 * lookup through a mixin; 1.12.2's shaders are a different mod with a different hook, and
 * {@code client.inheritShaderMaterial} therefore does nothing here. It is left in the settings file so
 * a pack can move between the two versions, and the mod names it in the log as it loads.
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
