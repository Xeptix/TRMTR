package com.trmtgtnh.entity;

import java.util.List;
import java.util.Random;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.item.ReinforceGestures;
import com.trmtgtnh.server.ReinforceCost;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Feeding a golem the stuff roads are made of, so that it lays it for you.
 *
 * <p>
 * Reinforcing ground by hand is a block at a time with a tamper, and a road is a great many blocks.
 * A golem already holds a patch of ground at a wear level; this lets it hold that patch <em>hard</em>
 * as well, for the price of the same material a player would have paid. Throw a bucket of concrete
 * into the ground it keeps and it eats it as its round carries it past - it has no eye for a
 * dropped item and will not go and fetch one - and a square of that round comes back reinforced.
 *
 * <p>
 * It takes one mouthful at a time and lays what it has before taking the next, rather than hoarding.
 * A bucket goes in, is chewed for a moment, and the empty comes straight back out; the material it
 * leaves behind is laid on the next stroke of work. A player can still throw a stack down and walk
 * away - the golem paces itself through it - but it will not take a bite at all unless a square of
 * its round is waiting to be reinforced, because by the time the chew ends the container has gone
 * back and there would be nothing left to hand over.
 *
 * <p>
 * What counts as material is not decided here. It is whatever {@code reinforce.materials} names,
 * which is the same list a player's own tamper pays from - a bucket of GregTech concrete where a
 * pack has one, and obsidian where it has not. So this needed no new item, no new recipe and no
 * mention of any other mod: a pack that renames its concrete renames it once, in the place that
 * already decided what reinforcement costs.
 */
public final class GolemMasonry {

    /** Ticks a golem waits before looking again, after finding nothing it could reinforce. */
    private static final int SULK_TICKS = 200;

    /** How many mouthfuls one golem will hold at once, so a thrown stack cannot fill it for ever. */
    private static final int GULLET = 16;

    /**
     * How long one mouthful takes to chew through.
     *
     * <p>
     * One tick short of a work stroke, and the margin is the point. Eating happens on a stroke,
     * so a chew that outlasts one costs the golem the stroke after it as well - a bite every
     * forty ticks rather than every twenty, and a stroke in between with no charge in hand,
     * which is a stroke the target picker takes and walks the golem off the pile it was eating
     * from. Ending just inside the stroke means the charge is ready when that stroke arrives,
     * the lay claims it, and the golem stays where the food is until the food is gone.
     */
    private static final int CHEW_TICKS = 19;

    /** How many squares of the round are looked at before giving up on finding one. */
    private static final int LOOKS = 32;

    private static final Random RANDOM = new Random();

    private GolemMasonry() {}

    /** Whether this golem is willing to eat and lay at all. */
    public static boolean works(EntityGolemOfWays golem) {
        return TrmtConfig.golemReinforces && TrmtConfig.reinforceEnabled
            && golem != null
            && golem.world != null
            && !golem.world.isRemote;
    }

    /**
     * Eats one piece of material lying about, if there is anywhere to put it.
     *
     * <p>
     * One a mouthful and only while its mouth is empty, so a stack thrown down is eaten steadily
     * with the chewing between - and each bucket's empty comes back the moment that bucket is
     * finished rather than at the far end of the job.
     *
     * <p>
     * It refuses to eat at all when nothing in its round wants reinforcing, and that is the rule the
     * whole arrangement rests on rather than a nicety. The container goes back when a mouthful ends,
     * so what the golem holds after that is material with nothing to carry it in: there is no stack
     * it could ever hand back. Taking in what it has no use for would be taking it for good. So the
     * question is asked before the first bite instead of discovered after it, and a bucket simply
     * lies there until the ground gives it something to do.
     *
     * <p>
     * The refusal sets the same wait a fruitless look sets, because the alternative is sampling the
     * round every tick for as long as a bucket lies within reach of a golem with nothing to harden.
     *
     * <p>
     * Called from the same sweep that vacuums wear drops, so it reaches exactly as far as the rest
     * of the golem's tidying does and needs no second idea of what is nearby.
     */
    public static void swallowNearby(EntityGolemOfWays golem, List<EntityItem> loose) {
        if (!works(golem) || loose == null) return;
        if (golem.masonryHeld() >= GULLET) return;
        // Its mouth is full: one at a time is the whole point, and the chew is the delay.
        if (golem.masonryInMouth() != null) return;
        if (golem.masonrySulk() > 0) return;
        // Everything laying asks, asked before eating. Laying happens inside a stroke of work,
        // and a stroke needs orders and a tamper - so a golem without either can eat all day
        // and lay nothing, and what it ate is material it can no longer hand back. That is the
        // one hole in "only eat when it has somewhere to put it" that a square in the round
        // does not close, because the square is not what is missing.
        if (!golem.hasOrders() || golem.findTool() < 0) return;

        for (int i = 0; i < loose.size(); i++) {
            EntityItem dropped = loose.get(i);
            if (dropped == null || dropped.isDead || dropped.cannotPickup()) continue;
            ItemStack stack = dropped.getItem();
            if (stack == null || stack.isEmpty()) continue;
            if (!ReinforceCost.isMaterial(stack)) continue;

            // Asked only once something worth eating is actually in front of it, so the cost of
            // looking is paid where there is a reason to look.
            if (findSquare(golem) == null) {
                Trmt.LOG.debug(
                    "Golem left {} where it lies: nothing in its round wants reinforcing",
                    stack.getDisplayName());
                // The wait is set only when its hands are empty. Thirty-two looks coming up
                // short is not proof the round is finished, and laying takes its own looks
                // every stroke - so a golem holding material must not be talked out of trying
                // by a question that was asked about eating.
                if (golem.masonryReady() <= 0) golem.setMasonrySulk(SULK_TICKS);
                return;
            }

            ItemStack one = stack.copy();
            one.setCount(1);
            if (!golem.swallowMasonry(one)) return;

            stack.shrink(1);
            if (stack.isEmpty()) dropped.setDead();
            else dropped.setItem(stack);

            // Its own pose: arms up to the mouth and a head that dips to meet them, worked in a
            // small cycle for as long as the mouthful lasts. It borrowed the mending pose at first
            // and that was wrong in the way a near-miss is wrong - a one-armed tamping swing is
            // what this creature does to ground, and eating is not that.
            golem.startChewing(CHEW_TICKS);
            golem.world.playSound(
                null,
                golem.posX,
                golem.posY,
                golem.posZ,
                SoundEvents.ENTITY_GENERIC_EAT,
                SoundCategory.NEUTRAL,
                0.6F,
                0.6F + RANDOM.nextFloat() * 0.2F);
            return;
        }
    }

    /** How many squares one look at the round tries before giving up, for anything reporting on it. */
    public static int looks() {
        return LOOKS;
    }

    /**
     * A square of the round that wants reinforcing, for a report rather than for work.
     *
     * <p>
     * The same question the golem asks itself before it takes a bite, asked by the command that
     * explains why it did not. It is the real one rather than a second copy, because a report that
     * answered differently from the golem would be worse than no report at all - and it carries the
     * same caveat: thirty-two looks coming up short is not proof the round is finished.
     */
    public static int[] somewhereToLay(EntityGolemOfWays golem) {
        return golem == null || golem.world == null || golem.world.isRemote ? null : findSquare(golem);
    }

    /**
     * A square of the round that wants reinforcing, as {x, y, z}, or null after {@link #LOOKS} tries.
     *
     * <p>
     * Chosen at random rather than nearest, so a golem fed a stack spreads the reinforcement over
     * its whole round instead of stacking it three deep at its feet and then declaring there is
     * nothing left to do.
     *
     * <p>
     * One method rather than two, and that is a correction. Deciding whether to eat and deciding
     * where to lay are the same question asked at different moments, and the moment they were two
     * pieces of code they would drift - which is precisely what happened to the column finder this
     * calls, when it was a second copy of the work sweep's own and quietly grew stricter until a
     * golem indoors refused every square it had.
     */
    private static int[] findSquare(EntityGolemOfWays golem) {
        World world = golem.world;
        int[] anchor = golem.anchor();
        int radius = golem.workRadius();
        int cap = Math.min(3, TrmtConfig.reinforceMaxLevel);
        if (cap <= 0) return null;

        for (int attempt = 0; attempt < LOOKS; attempt++) {
            int x = anchor[0] + RANDOM.nextInt(radius * 2 + 1) - radius;
            int z = anchor[2] + RANDOM.nextInt(radius * 2 + 1) - radius;
            // The work sweep's own column finder rather than one of this file's own, which is the
            // whole of what was wrong here. Ground the golem walks over and mends is ground it can
            // lay a mouthful into, and a square is passed over rather than aborting the column.
            int y = GolemWork.surfaceNear(world, x, anchor[1], z);
            if (y < 0) continue;
            SurfaceFamily family = SurfaceRegistry.familyOf(
                com.trmtgtnh.util.Worlds.blockAt(world, x, y, z),
                com.trmtgtnh.util.Worlds.metaAt(world, x, y, z));
            if (family == null || !family.staged) continue;
            if (ReinforceGestures.levelAt(world, x, y, z) >= cap) continue;
            return new int[] { x, y, z };
        }
        return null;
    }

    /**
     * Lays one mouthful into a square of its round.
     *
     * <p>
     * Nothing is brought back up any more, and nothing can be: the container went back when the
     * mouthful was chewed, so a golem with nowhere to lay is holding material it cannot hand over.
     * It waits instead - and does not take any more in until it has somewhere to put it, which is
     * what keeps that from being a way to lose somebody's concrete.
     *
     * @return true when a stroke of work was spent on this rather than on wear
     */
    public static boolean layOne(EntityGolemOfWays golem) {
        if (!works(golem) || golem.masonryReady() <= 0) return false;
        if (golem.masonrySulk() > 0) return false;

        World world = golem.world;
        int[] at = findSquare(golem);
        if (at == null) {
            // Said out loud, because for two versions it was not, and the difference between this
            // and a golem working perfectly well was one item on the floor. A player watching
            // cannot tell them apart; a log can.
            Trmt.LOG.debug(
                "Golem found nowhere to reinforce in {} looks; holding {} mouthful(s) and waiting",
                Integer.valueOf(LOOKS),
                Integer.valueOf(golem.masonryReady()));
            golem.setMasonrySulk(SULK_TICKS);
            return false;
        }

        if (!golem.spendMasonry()) return false;
        int x = at[0];
        int y = at[1];
        int z = at[2];
        ReinforceGestures.raiseBy(world, x, y, z, 1);
        golem.markBusy(GolemCombat.BUSY_MEND, 20);
        world.playSound(
            null,
            x + 0.5D,
            y + 0.5D,
            z + 0.5D,
            SoundEvents.BLOCK_STONE_BREAK,
            SoundCategory.BLOCKS,
            0.8F,
            1.4F);
        // The number this carries is a block id with its metadata above it, which is
        // exactly what 1.12.2 calls a state id, so the sum below is left as it was.
        world.playEvent(
            2001,
            new BlockPos(x, y, z),
            net.minecraft.block.Block.getIdFromBlock(com.trmtgtnh.util.Worlds.blockAt(world, x, y, z))
                + (com.trmtgtnh.util.Worlds.metaAt(world, x, y, z) << 12));
        Trmt.LOG.debug(
            "Golem laid reinforcement at {},{},{}; {} mouthful(s) left",
            new Object[] { Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z),
                Integer.valueOf(golem.masonryReady()) });
        return true;
    }
}
