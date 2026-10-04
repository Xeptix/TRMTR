package com.trmtgtnh.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * What a ghost keeps of the block it is standing in for.
 *
 * <p>
 * Almost everything a ghost answers comes from its family, which is a coarse bucket, and for most
 * questions that is exactly right - a worn path is worn path however it started. A few answers are
 * not about wear at all, though, and those should still be the covered block's: whether it glows,
 * and what it scatters into the air. Losing them is how a lit block went dark the first time
 * somebody walked on it.
 *
 * <p>
 * The rule for what belongs here is narrow, and it is the same rule the rest of the mod follows:
 * anything the server also runs, the server already runs against the real block, so delegating on
 * the client would either double the effect or diverge from it. What is left is the handful of
 * things only the client asks, at a position, about a block that is not there.
 */
public final class GhostInherit {

    /**
     * Whether anything a ghost can cover glows at all.
     *
     * <p>
     * Worked out once when surfaces are resolved, because the alternative is a cache lookup on the
     * light path - and light propagates through every block a chunk holds, several times, whenever
     * anything changes. In a world where no erodable block emits anything, which is nearly all of
     * them, this turns the whole question into one volatile read. It is the same bargain
     * {@link GhostLight} already makes for its own glow.
     */
    private static volatile boolean anythingGlows;

    /**
     * Whether anything a ghost can cover states a footing of its own.
     *
     * <p>
     * The same bargain as the flag above and it matters rather more, because this one fronts the
     * collision path: in a pack where every erodable block takes the whole of its cell, which is
     * nearly all of them, the entire question is one volatile read.
     */
    private static volatile boolean anythingStatesItsOwnBox;

    /** By block id, true where the block's own class works out its collision box. */
    private static volatile boolean[] ownBox = new boolean[0];

    private GhostInherit() {}

    /**
     * Looks over what this pack's erodable blocks are like, once, after surfaces are resolved.
     *
     * <p>
     * Walked over the block registry rather than over the resolved table, because the resolved
     * table is keyed by state and this only needs to know whether such a block exists at all.
     */
    public static void survey() {
        int width = registryWidth();
        boolean[] ownFooting = new boolean[width];
        boolean glows = false;
        boolean anyOwnBox = false;

        for (Object candidate : Block.blockRegistry) {
            if (!(candidate instanceof Block)) continue;
            Block block = (Block) candidate;
            if (block instanceof GhostBlock) continue;
            if (!erodable(block)) continue;

            try {
                if (block.getLightValue() > 0) glows = true;
            } catch (RuntimeException awkwardBlock) {
                // Dropped from the glow question and still asked the next one: a block that cannot
                // say how brightly it burns may still know perfectly well what shape it is.
            }

            int id = Block.getIdFromBlock(block);
            if (id < 0 || id >= width) continue;
            ownFooting[id] = statesOwnBox(block);
            if (ownFooting[id]) anyOwnBox = true;
        }

        ownBox = ownFooting;
        anythingGlows = glows;
        anythingStatesItsOwnBox = anyOwnBox;
    }

    /** One past the highest block id there is, so a plain array can be indexed by one. */
    private static int registryWidth() {
        int width = 0;
        for (Object candidate : Block.blockRegistry) {
            if (!(candidate instanceof Block)) continue;
            int id = Block.getIdFromBlock((Block) candidate);
            if (id >= width) width = id + 1;
        }
        return width;
    }

    /** Whether any metadata value of this block wears, which is what makes it worth surveying. */
    private static boolean erodable(Block block) {
        for (int meta = 0; meta < 16; meta++) {
            if (SurfaceRegistry.familyOf(block, meta) != null) return true;
        }
        return false;
    }

    /**
     * Whether a block's own class works out its collision box, asked of the class and never of the
     * block.
     *
     * <p>
     * The safest question there is, because it runs none of the block's code. Asking the block
     * itself with a position of nought would be the obvious shortcut and is a trap: a great many of
     * these route through {@code setBlockBoundsBasedOnState}, which writes the shared bounds fields
     * on the block instance, and this walk runs on the server thread at a moment when the client is
     * reading those very fields.
     *
     * <p>
     * Both spellings, because a name means one thing in a development environment and another in a
     * shipped game and neither is available at the other's moment; the obfuscated one is asked first
     * because that is what a player is running. A miss answers "it might", since the cost of that
     * mistake is one guarded call at a worn position and the cost of the other is somebody standing
     * on air.
     */
    private static boolean statesOwnBox(Block block) {
        for (int i = 0; i < BOX_METHOD_NAMES.length; i++) {
            try {
                return block.getClass()
                    .getMethod(BOX_METHOD_NAMES[i], World.class, int.class, int.class, int.class)
                    .getDeclaringClass() != Block.class;
            } catch (NoSuchMethodException wrongSpelling) {
                // The other one, then.
            } catch (RuntimeException awkwardClass) {
                return true;
            } catch (LinkageError awkwardClass) {
                // A class whose hierarchy will not resolve. A class that cannot be inspected is not
                // one to be clever about; let the guarded call find out instead.
                return true;
            }
        }
        return true;
    }

    private static final String[] BOX_METHOD_NAMES = { "func_149668_a", "getCollisionBoundingBoxFromPool" };

    /**
     * Stands for "this block takes whatever footing it is given", so that null can go on meaning to
     * this mod what it means to the game, which is that there is nothing here to walk into.
     */
    public static final AxisAlignedBB ORDINARY = AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1);

    /** A ten-thousandth of a block, finer than anything states its own edges to. */
    private static final double TOLERANCE = 1.0E-4D;

    /**
     * The footing a block insists on for itself here: {@link #ORDINARY} when it insists on none,
     * null when it insists on having none at all, and its own box when that box is narrower than the
     * square it stands in.
     *
     * <p>
     * Wear is a height taken off the top of a full cell, and that is the right shape for every kind
     * of ground there is - earth, rock, sand, a slab, a made path. It is the wrong shape for a block
     * that occupies less of its square than that. A cloud is a pad a little over half across sitting
     * at the bottom of its cell, and a stand-in handing back a full cell over one turned a block you
     * fall through into a block you stand on. So a block narrower than its own square keeps its own
     * answer, worn or not, and a rut is simply never cut into it.
     *
     * <p>
     * Only the footprint is measured and never the height, because a shorter block is exactly what
     * wear produces and is what the record already knows how to describe. A slab and a grass path
     * fill their square and stand short, and both go on wearing as they always have.
     *
     * <p>
     * Both sides ask this, of the same block, and get one answer: the server through
     * {@code PhysicalDecay.boxAt} and the client through {@code GhostLogic.solidBox}. That is why it
     * lives here rather than beside either of them - a footing derived from two rules is two
     * machines disagreeing about where somebody is standing.
     */
    public static AxisAlignedBB ownFootingAt(Block block, World world, int x, int y, int z) {
        if (!anythingStatesItsOwnBox || block == null || world == null) return ORDINARY;
        if (block instanceof GhostBlock) return ORDINARY;

        int id = Block.getIdFromBlock(block);
        boolean[] states = ownBox;
        if (id < 0 || id >= states.length || !states[id]) return ORDINARY;
        if (!enter()) return ORDINARY;

        try {
            AxisAlignedBB own = block.getCollisionBoundingBoxFromPool(world, x, y, z);
            if (own == null) return null;
            boolean narrow = own.minX > x + TOLERANCE || own.minZ > z + TOLERANCE
                || own.maxX < x + 1.0D - TOLERANCE
                || own.maxZ < z + 1.0D - TOLERANCE;
            return narrow ? own : ORDINARY;
        } catch (RuntimeException awkwardBlock) {
            Trmt.LOG.debug("Block {} refused to state its own footing; giving it the usual one", block, awkwardBlock);
            return ORDINARY;
        } finally {
            leave();
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
    public static void entityCollided(GhostBlock ghost, World world, int x, int y, int z, Entity entity) {
        if (!TrmtConfig.inheritEntityCollision) return;
        if (world == null || !world.isRemote || entity == null) return;
        if (!predicted(entity)) return;

        Block origin = Trmt.proxy.originBlockAt(x, y, z);
        // The identity checks are the recursion guard's first two hops and the flag below is its
        // third. Nothing in vanilla forwards this call the way Forge's position-aware light value
        // does, but a block that asked the world what was at its own position and called this on the
        // answer would come straight back in for ever - and the game wraps whatever comes out of
        // here in a crash report and rethrows it, so an overflow here is not a dropped tick.
        if (origin == null || origin == ghost.asBlock() || origin instanceof GhostBlock) return;
        if (!enter()) return;
        try {
            origin.onEntityCollidedWithBlock(world, x, y, z, entity);
        } catch (RuntimeException awkwardBlock) {
            Trmt.LOG.debug("Block {} refused an entity collision through a ghost", origin, awkwardBlock);
        } finally {
            leave();
        }
    }

    /**
     * Whether this client is the one working out where this entity is going.
     *
     * <p>
     * The player at the controls and whatever they are riding, and nothing else. Every other entity
     * is moved by the server and arrives here as positions to interpolate towards, so nudging one
     * would buy jitter rather than fidelity - and a block that harms or ignites what stands in it
     * should not be handed a second thing to do it to.
     *
     * <p>
     * {@code isClientWorld} is the game's own name for this distinction rather than a test invented
     * here: it is what {@code EntityLivingBase} asks before it runs its own movement, and the local
     * player is the one entity on a client that answers yes.
     */
    private static boolean predicted(Entity entity) {
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).isClientWorld()) return true;
        Entity rider = entity.riddenByEntity;
        return rider instanceof EntityLivingBase && ((EntityLivingBase) rider).isClientWorld();
    }

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
     * How brightly the block under this ghost glows, or zero.
     *
     * <p>
     * Read through the no-argument form deliberately. Forge's position-aware
     * {@code getLightValue(IBlockAccess, x, y, z)} begins by looking up the block at the position
     * and, finding it is not itself, forwarding to whatever is there - which from inside a ghost's
     * own answer is the ghost, for ever.
     */
    public static int glowOf(IBlockAccess world, int x, int y, int z) {
        if (!anythingGlows) return 0;
        Block origin = Trmt.proxy.originBlockAt(x, y, z);
        if (origin == null) return 0;
        try {
            return origin.getLightValue();
        } catch (RuntimeException awkwardBlock) {
            return 0;
        }
    }

    /**
     * Lets the covered block scatter whatever it scatters.
     *
     * <p>
     * {@code WorldClient.doVoidFogParticles} picks a thousand positions in a cube around the player
     * every tick and calls this on every non-air block it lands on, without consulting whether the
     * block asked to be ticked - so a ghost is asked exactly as often as the turf it replaced and,
     * until now, answered with silence. Mycelium's spores are the vanilla casualty; in a pack this
     * size anything with weather of its own is one.
     *
     * <p>
     * The covered block is handed the real world rather than a view with itself put back at this
     * position, because what it is given has to be a {@code World} and the view is not one. Almost
     * nothing here reads its own metadata - these are decorations, not state machines - and the
     * try is for the one that does.
     */
    public static void ambientTick(GhostBlock ghost, World world, int x, int y, int z, Random random) {
        if (!TrmtConfig.inheritAmbientParticles) return;
        if (world == null || !world.isRemote) return;

        Block origin = Trmt.proxy.originBlockAt(x, y, z);
        if (origin == null || origin == ghost.asBlock()) return;
        try {
            origin.randomDisplayTick(world, x, y, z, random);
        } catch (RuntimeException awkwardBlock) {
            // Third-party code on the client tick. A decoration that cannot draw itself is not a
            // reason to drop the frame.
            Trmt.LOG.debug("Block {} refused an ambient tick through a ghost", origin, awkwardBlock);
        }
    }
}
