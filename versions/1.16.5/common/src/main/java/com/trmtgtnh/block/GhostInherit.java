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

    // ------------------------------------------------------------------
    // Breaking it, and growing on it: the covered block's answers (0.9.222)
    // ------------------------------------------------------------------

    /**
     * The block a square's break speed is asked of: the one it covers, and where nothing is recorded under it, its
     * family's own block - see {@link GhostFamily#standIn}. Null with no record at all, where the ghost answers for
     * itself.
     *
     * <p>
     * The 1.7.10 edition gets the second half from its stand-in per family, whose hardness, material and harvest tool
     * are that family's block's; one ghost here has one of each, so the family's block is asked instead.
     */
    static BlockState answeringFor(Block ghost, BlockGetter world, BlockPos pos) {
        BlockState covered = coveredAt(ghost, pos);
        if (covered != null) return covered;
        com.trmtgtnh.surface.SurfaceFamily family = GhostFamily.familyAt(world, pos);
        return family == null ? null : GhostFamily.standIn(family);
    }

    /**
     * The real block under a ghost at a square, or null when nothing trustworthy is recorded - the 1.7.10 edition's
     * {@code GhostLogic.coveredAt}: never the ghost itself, never another ghost.
     */
    public static BlockState coveredAt(Block ghost, BlockPos pos) {
        if (pos == null) return null;
        int packed = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (packed < 0) return null;
        BlockState origin = Block.stateById(packed);
        if (origin == null || origin.getBlock() == ghost || origin.getBlock() instanceof BlockGhost) return null;
        return origin;
    }

    /**
     * How fast this player breaks the covered block, tool and all, or NaN to let the ghost answer - the 1.7.10
     * edition's {@code GhostLogic.blockHardness} and {@code breakSpeed} in one, because this version asks them as one
     * (spec GF11, GF12).
     *
     * <p>
     * A break is the player's speed against the block, over its hardness, over thirty where the tool can harvest it and
     * a hundred where it cannot; asked of the ghost, every square was 0.6 of ground that needs no tool, so worn ground a
     * pack gates behind a better pick broke three times too fast on the client and was put back by the server. That
     * edition works the sum out itself rather than hand it to the covered block, because the covered block would read
     * the world's metadata at its square and find the wear stage there. This version's answer is worked out from the
     * state it is handed - its hardness, and whether the tool suits it, are the state's own, on both loaders - and the
     * world it is handed is {@link OriginView}, which holds the covered block at its square. So the whole question can
     * go to the covered block, its own override, Forge's harvest levels and all, without coming back here; and an
     * unbreakable block answers nought, as it does for itself. Guarded besides, as everything here is.
     *
     * <p>
     * That edition turns away a world that is not a client's first; here {@code Client} does, answering nothing
     * recorded where there is no client, so the square is never asked about on a server.
     */
    public static float destroyProgress(Block ghost, net.minecraft.world.entity.player.Player player,
        BlockGetter world, BlockPos pos) {
        if (player == null) return Float.NaN;
        BlockState covered = answeringFor(ghost, world, pos);
        if (covered == null) return Float.NaN;
        if (!enter()) return Float.NaN;
        try {
            return covered.getDestroyProgress(player, new OriginView(world, pos, covered), pos);
        } catch (RuntimeException awkwardBlock) {
            return Float.NaN;
        } finally {
            leave();
        }
    }

    /**
     * Whether the covered block would hold a plant, or null to let the ghost answer - the 1.7.10 edition's
     * {@code GhostLogic.sustainsPlant} (spec GF15), for Forge, which asks a block this by identity.
     *
     * <p>
     * The question itself is Forge's ({@code canSustainPlant}, with a plant type this module cannot name), so the Forge
     * module asks it and this does everything around it: finds the covered block, hands it a view that holds it at its
     * own square, and guards against the question coming back here, as that edition's does.
     */
    public static Boolean sustainsPlant(Block ghost, BlockGetter world, BlockPos pos,
        java.util.function.BiPredicate<BlockState, BlockGetter> ask) {
        BlockState covered = coveredAt(ghost, pos);
        if (covered == null) return null;
        if (!enter()) return null;
        try {
            return Boolean.valueOf(ask.test(covered, new OriginView(world, pos, covered)));
        } catch (RuntimeException awkwardBlock) {
            return null;
        } finally {
            leave();
        }
    }

    /**
     * The ground a plant is asked to stand on: the block a ghost covers, where the ground is a ghost, and otherwise the
     * ground itself - for Fabric, whose plants decide this themselves rather than asking the block (spec GF15).
     *
     * <p>
     * Vanilla's plants check the block below them by identity - grass, dirt, sand - and a ghost is none of those
     * whatever it is drawn as, so the client refused a sapling on worn ground that the server would have taken. Forge
     * puts the question to the block ({@code canSustainPlant}, answered by {@link #sustainsPlant}); Fabric's mixins put
     * the plant's own question to the covered block instead, at the same four places Forge asks it: a bush, a mushroom,
     * a cactus and a reed. Nothing here calls into the covered block, so nothing can come back round.
     */
    public static BlockState soilFor(BlockState ground, BlockPos pos) {
        if (ground == null || !(ground.getBlock() instanceof BlockGhost)) return ground;
        BlockState covered = coveredAt(ground.getBlock(), pos);
        return covered == null ? ground : covered;
    }

    /**
     * The world a plant's question about a ghost's square is asked through: one holding the covered block there, as the
     * Forge question is asked, so a plant that looks at the square it was told about finds what it was told.
     */
    public static BlockGetter soilView(BlockGetter world, BlockPos pos) {
        if (world == null || pos == null) return world;
        BlockState ground = world.getBlockState(pos);
        if (!(ground.getBlock() instanceof BlockGhost)) return world;
        BlockState covered = coveredAt(ground.getBlock(), pos);
        return covered == null ? world : new OriginView(world, pos, covered);
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
