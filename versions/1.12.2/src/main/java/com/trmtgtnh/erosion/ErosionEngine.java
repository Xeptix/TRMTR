package com.trmtgtnh.erosion;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Where wear is accumulated, advanced and healed. Server side only.
 *
 * <p>
 * Two things happen here that upstream does not do. First, no block is ever written: wear
 * lives in {@link ErosionStore} and the client is told to paint over what is already there.
 * Second, healing is <em>lazy</em>. Every entry records the absolute world time of its last
 * step, so recovery is a function of elapsed time rather than of ticks spent loaded. A chunk
 * nobody has visited for two in-game months catches up in a single pass the moment it loads,
 * and costs nothing at all in the meantime. That is what makes healing affordable on a world
 * where the great majority of explored chunks are unloaded at any given moment.
 */
public final class ErosionEngine {

    /** 24000 ticks per Minecraft day at 20 ticks per second. The bank's own day, so the two cannot drift apart. */
    private static final int SECONDS_PER_DAY = HealBank.SECONDS_PER_DAY;

    private static final ErosionEngine INSTANCE = new ErosionEngine();

    /**
     * Last ground block each mover stood on. Weak so a despawned mob or a disconnected
     * player drops out without any bookkeeping; the server tick is single-threaded, so an
     * unsynchronised map is correct here.
     */
    private final WeakHashMap<Entity, long[]> lastGround = new WeakHashMap<Entity, long[]>();

    /**
     * Blocks each mover has worn lately, and when. A mover cannot wear a block already in its
     * own set - see {@link #onMoverTick}. Weak on the entity for the same reason lastGround is:
     * a despawned mob drops out with no bookkeeping. Entries inside leave by distance or by age,
     * so the set stays about as large as the ground within reach of where the mover has been.
     */
    private final WeakHashMap<Entity, java.util.HashMap<Long, Integer>> recentlyWorn = new WeakHashMap<Entity, java.util.HashMap<Long, Integer>>();

    private final Random random = new Random();

    private int tickCounter;

    private ErosionEngine() {}

    public static ErosionEngine get() {
        return INSTANCE;
    }

    public void reset() {
        lastGround.clear();
        tickCounter = 0;
        healPause = 0;
    }

    /**
     * In-game seconds the clock has been held back by, because nobody was connected for them.
     *
     * <p>
     * Cached here rather than looked up, because this is read once per entry per sweep and the
     * lookup is a map hit through the world's map storage. Written once a second by the server
     * tick from {@link com.trmtgtnh.server.HealClockData}, which is where it is worked out and
     * where it is saved.
     */
    private static volatile int healPause;

    /** Told by the server tick. Zero on a client, which heals nothing and needs no clock. */
    public static void setHealPause(int seconds) {
        healPause = Math.max(0, seconds);
    }

    public static int healPause() {
        return healPause;
    }

    /**
     * Absolute world clock in seconds. In-game time, so a stopped server heals nothing.
     *
     * <p>
     * Less whatever time went by with nobody connected, which is the other half of the same idea:
     * a server sitting empty is not a server whose roads should be growing back. Taking it off the
     * clock rather than skipping the sweep is what makes that true for chunks nobody visits -
     * a skipped sweep is a sweep that catches up the moment somebody walks past.
     */
    public static int nowSeconds(World world) {
        return (int) (world.getTotalWorldTime() / 20L) - healPause;
    }

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    /**
     * Called once per sampled tick for anything that can wear the ground down.
     *
     * @param walker     the entity being tracked; its vehicle, if any, is what actually
     *                   touches the ground
     * @param multiplier family-independent scaling for what kind of mover this is
     */
    public void onMoverTick(EntityLivingBase walker, float multiplier) {
        World world = walker.world;
        if (world == null || world.isRemote) return;
        if (!TrmtConfig.enabled) return;
        if (!TrmtConfig.dimensionAllowed(world.provider.getDimension())) return;

        Entity vehicle = walker.getRidingEntity();
        boolean mounted = vehicle != null;
        Entity mover = mounted ? vehicle : walker;

        if (!mover.onGround) {
            // Airborne: forget where they were so the next landing registers as a fresh step.
            lastGround.remove(walker);
            return;
        }

        int x = MathHelper.floor(mover.posX);
        // The bottom of the collision box, not the entity's position. They differ exactly when
        // it matters: once ground has sunk, its collision top is below the block boundary, so
        // "position minus one" names the block underneath instead — and that one is covered, so
        // it is rejected. Wear stopped accumulating the moment a rut started to form, which is
        // the opposite of what should happen.
        int y = MathHelper.floor(mover.getEntityBoundingBox().minY - 0.001D);
        int z = MathHelper.floor(mover.posZ);

        long packed = ((long) x << 40) ^ ((long) (y & 0xFF) << 32) ^ (z & 0xFFFFFFFFL);
        long[] previous = lastGround.get(walker);
        if (previous != null && previous[0] == packed) return; // still on the same block
        if (previous == null) {
            lastGround.put(walker, new long[] { packed });
        } else {
            previous[0] = packed;
        }

        if (TrmtConfig.sneakSuppresses && walker.isSneaking()) return;
        // The same class of suppression as sneaking, so it sits in the same place - after the
        // bookkeeping above rather than before it. That ordering is load-bearing: leaving the
        // record of which block was last stood on intact is what makes the first step after a
        // draught runs out register as a fresh one instead of being swallowed as "still here".
        if (com.trmtgtnh.item.ModPotions.treadsLightly(walker, vehicle)) return;

        float amount = TrmtConfig.multiplierPlayer * multiplier;
        if (mounted) amount *= TrmtConfig.multiplierMounted;
        // Before the guard below rather than after it, so that a factor somebody has set to nought
        // is caught by a check that already exists instead of producing wear of the wrong sign.
        // Applied to this figure rather than inside step, which means it reaches the blocks a pass
        // bleeds onto and the crop overhead as well: a heavy walker widens a track, not only sinks
        // it. That is intended, and it is written down because the setting's name does not say it.
        amount *= com.trmtgtnh.item.ModPotions.wearFactor(walker, vehicle);
        if (amount <= 0f) return;

        // The cooldown holds back everything this mover would count - the ground, a plant in the
        // way and a leaf underfoot alike. Plants used to be let through, when their tally was wiped
        // every ten seconds and nothing could come of it; a tally that lasts would let a creature
        // milling about in one place mow every plant it stands among, which is exactly what the
        // cooldown exists to stop.
        if (cooldownApplies(walker) && recentlyWorn(walker, world, mover, x, y, z)) return;

        step(world, x, y, z, amount);
        // Headed by the mount where the mount is a creature, and by the rider otherwise. The
        // position above comes from the mover because that is the thing in contact with the
        // ground - but a boat and a minecart keep no look yaw at all, only an atan2 of their own
        // displacement, a quarter turn out of phase with the decoding below and flipped a half
        // turn when they run in reverse. Neither can reach here in vanilla anyway, water and
        // rails being nothing this mod wears, so this is a guard against the modded case rather
        // than a fix for a visible one.
        bleedToNeighbours(world, mover instanceof EntityLivingBase ? mover : walker, x, y, z, amount);
        tryTrample(world, x, y + 1, z, amount, SurfaceFamily.VEGETATION);
        // Last, because it can take the floor out from under the walker.
        int leafY = leafUnderfoot(world, x, y, z);
        if (leafY >= 0) tryTrample(world, x, leafY, z, amount, SurfaceFamily.LEAVES);
    }

    /** Whether the re-trigger cooldown is imposed on this mover at all. */
    private static boolean cooldownApplies(EntityLivingBase walker) {
        if (!TrmtConfig.retriggerCooldown) return false;
        if (TrmtConfig.retriggerSeconds <= 0 && TrmtConfig.retriggerBlocks <= 0f) return false;
        boolean player = walker instanceof net.minecraft.entity.player.EntityPlayer;
        return player ? TrmtConfig.retriggerCooldownPlayers : true;
    }

    /**
     * Whether this block is still locked for this mover, and books it if it is not.
     *
     * <p>
     * Returning true means "leave it alone" - the mover wore this block lately and has neither
     * walked far enough from it nor waited long enough. Returning false books the block as worn
     * now, so the next pass over it is the one that is throttled.
     *
     * <p>
     * Stale bookings are released first, on every call, so the set never holds a block the mover
     * has since walked away from. The release is what keeps this cheap: a mover crossing open
     * ground carries only the handful of blocks within {@code retriggerBlocks} of where it is.
     */
    private boolean recentlyWorn(EntityLivingBase walker, World world, Entity mover, int x, int y, int z) {
        java.util.HashMap<Long, Integer> recent = recentlyWorn.get(walker);
        if (recent == null) {
            recent = new java.util.HashMap<Long, Integer>();
            recentlyWorn.put(walker, recent);
        }
        int now = nowSeconds(world);
        releaseWorn(recent, now, mover.posX, mover.posZ);

        long here = packPosition(x, y, z);
        if (recent.containsKey(here)) return true;

        // A ceiling far above what the release ever leaves in here, tripped only by something
        // pathological. Clearing then rather than evicting one at a time costs at worst a single
        // unthrottled pass, which is harmless.
        if (recent.size() > 1024) recent.clear();
        recent.put(here, Integer.valueOf(now));
        return false;
    }

    /** Drops every block this mover has waited out or walked far enough from. */
    private static void releaseWorn(java.util.HashMap<Long, Integer> recent, int now, double px, double pz) {
        if (recent.isEmpty()) return;
        boolean byTime = TrmtConfig.retriggerSeconds > 0;
        double maxSq = TrmtConfig.retriggerBlocks * TrmtConfig.retriggerBlocks;
        boolean byDist = TrmtConfig.retriggerBlocks > 0f;
        java.util.Iterator<java.util.Map.Entry<Long, Integer>> it = recent.entrySet()
            .iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Long, Integer> entry = it.next();
            if (byTime && now - entry.getValue()
                .intValue() >= TrmtConfig.retriggerSeconds) {
                it.remove();
                continue;
            }
            if (byDist) {
                long packed = entry.getKey()
                    .longValue();
                double dx = unpackX(packed) + 0.5D - px;
                double dz = unpackZ(packed) + 0.5D - pz;
                // Flat distance: jumping straight up is not walking away from the block.
                if (dx * dx + dz * dz >= maxSq) it.remove();
            }
        }
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    /**
     * Wear spreads sideways more than forwards, which is what turns a line of footsteps into
     * something with width. Upstream's ratios: a fifth ahead, half to each side, nothing
     * behind.
     *
     * <p>
     * Headed by whatever is actually going somewhere, which is the mount where there is one and
     * the mount is a creature. The position this is handed comes from the mover for the same
     * reason - the thing in contact with the ground is what drags across it - and a rider
     * glancing over their shoulder has not changed which way that is happening. It matters more
     * than a blurred shape would: the two bleeds are perpendicular, so the wrong yaw widens a
     * path along the line of travel and softens its flanks, which is the right shape turned
     * ninety degrees. Nothing changes for a vanilla mount, since horse and pig copy their
     * rider's yaw; a modded mount that steers itself is the case this was drawn for.
     */
    private void bleedToNeighbours(World world, Entity mover, int x, int y, int z, float amount) {
        int facing = MathHelper.floor(mover.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int dx = facing == 3 ? 1 : facing == 1 ? -1 : 0;
        int dz = facing == 0 ? 1 : facing == 2 ? -1 : 0;

        if (TrmtConfig.bleedFront > 0f) {
            step(world, x + dx, y, z + dz, amount * TrmtConfig.bleedFront);
        }
        if (TrmtConfig.bleedSide > 0f) {
            // Perpendicular to travel, both ways.
            step(world, x + dz, y, z - dx, amount * TrmtConfig.bleedSide);
            step(world, x - dz, y, z + dx, amount * TrmtConfig.bleedSide);
        }
    }

    // ------------------------------------------------------------------
    // Wear
    // ------------------------------------------------------------------

    /**
     * Scuffs the ground around a blast, hardest at the middle.
     *
     * <p>
     * Everything an explosion did not destroy outright it still scoured, and that is the part
     * worth showing: a crater with clean grass to its lip looks like a hole somebody cut rather
     * than something that went off. Wear falls away with distance so the ground reads as having
     * been pushed outward from a point.
     *
     * <p>
     * Every position goes through the ordinary step, which is what makes this cheap to trust:
     * pinned ground is still pinned, ground under a solid block is still sheltered, the
     * per-chunk entry cap still holds and an unloaded chunk is still left alone. This method
     * only decides where to knock and how hard.
     */
    public void blast(World world, double centreX, double centreY, double centreZ, float radius) {
        blast(world, centreX, centreY, centreZ, radius, null);
    }

    /**
     * As above, told which positions the blast is about to destroy.
     *
     * <p>
     * Forge fires the detonate event while every affected block is still standing, so without
     * this the scouring is spent on ground that is air a moment later. That is not merely
     * wasteful: entries are capped per chunk, and a large charge could fill a chunk's budget
     * with positions that no longer exist and leave nothing for the surviving ring - which is
     * the only part anybody was ever going to see.
     */
    public void blast(World world, double centreX, double centreY, double centreZ, float radius,
        java.util.Set<Long> doomed) {
        if (world == null || world.isRemote || radius <= 0f) return;
        if (!TrmtConfig.explosionWear || TrmtConfig.explosionStrength <= 0f) return;

        // The ground a charge scours is wider than the hole it digs, so the reach is the
        // blast's own radius scaled up rather than the radius itself.
        float reachF = Math.min(radius * TrmtConfig.explosionReach, TrmtConfig.explosionMaxRadius);

        // Bounded by work rather than only by distance. A sphere grows as the cube of its
        // radius and all of this happens inside the tick that set the charge off, so a big
        // enough number would stall the server outright however sensible the distance looked.
        // Pulling the reach in is the right way to lose it: what goes is the outermost ring,
        // where the wear had already faded to nothing.
        double affordable = Math.cbrt(TrmtConfig.explosionMaxPositions * 3.0D / (4.0D * Math.PI));
        if (reachF > affordable) reachF = (float) affordable;

        int reach = (int) Math.ceil(reachF);
        if (reach <= 0) return;

        // Strength scales with the charge, quoted against vanilla TNT: a creeper scuffs less
        // than a stick of dynamite and a mining charge leaves a mark you can find later.
        float force = radius <= 0f ? 0f : radius / TrmtConfig.REFERENCE_BLAST;
        float centre = TrmtConfig.explosionStrength * force;
        if (centre <= 0f) return;

        scour(world, centreX, centreY, centreZ, reachF, centre, doomed);
    }

    /**
     * Scuffs the ground where something landed hard, hardest where it struck.
     *
     * <p>
     * The same shape as a blast and a much smaller one. Something coming down from a height
     * puts its whole momentum through a patch of ground the size of itself, which marks that
     * patch and very little around it - so the reach grows slowly with the drop where a
     * charge's grows with its whole radius.
     *
     * @param drop       how far it fell, in blocks
     * @param multiplier what this particular thing is worth, from {@link #multiplierFor}
     */
    public void impact(World world, double centreX, double centreY, double centreZ, float drop, float multiplier) {
        if (world == null || world.isRemote || multiplier <= 0f) return;
        if (!TrmtConfig.fallWear || TrmtConfig.fallStrength <= 0f) return;
        // Below this a landing is one somebody walked into rather than fell, and it is already
        // counted as a step. It is also where vanilla stops doing damage.
        if (drop < TrmtConfig.fallMinDistance) return;

        // Quoted against a fall that hurts: at the reference drop a landing is worth the
        // configured strength, and everything else is in proportion to how far it came. Speed
        // is what does the marking and speed goes as the square root of the drop, so the wear
        // follows that rather than the distance itself - twice the height is not twice the blow.
        float reference = Math.max(0.1f, TrmtConfig.fallReferenceDistance);
        float force = (float) Math.sqrt(drop / reference);
        float centre = TrmtConfig.fallStrength * force * multiplier;
        if (centre <= 0f) return;

        float reachF = Math.min(TrmtConfig.fallMaxRadius, 1f + drop / reference);
        scour(world, centreX, centreY, centreZ, reachF, centre, null);
    }

    /**
     * Lays wear over a sphere, hardest in the middle and fading to nothing at the edge.
     *
     * <p>
     * Every position goes through the ordinary step, which is what makes this cheap to trust
     * rather than a second path to keep honest: pinned ground stays pinned, ground under a
     * solid block stays sheltered, the per-chunk entry cap holds and an unloaded chunk is left
     * alone. Callers decide only where to knock and how hard.
     */
    private void scour(World world, double centreX, double centreY, double centreZ, float reachF, float centre,
        java.util.Set<Long> doomed) {
        int reach = (int) Math.ceil(reachF);
        if (reach <= 0 || centre <= 0f) return;

        int baseX = MathHelper.floor(centreX);
        int baseY = MathHelper.floor(centreY);
        int baseZ = MathHelper.floor(centreZ);

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
                    if (distance > reachF) continue;
                    // Linear rather than square-law. The real thing falls off faster, but a
                    // scuff that is invisible two blocks out is a scuff nobody sees at all, and
                    // what this is for is the ring of scoured ground around the middle.
                    float share = 1f - (float) (distance / reachF);
                    float amount = centre * share;
                    if (amount <= 0f) continue;
                    int x = baseX + dx;
                    int y = baseY + dy;
                    int z = baseZ + dz;
                    if (doomed != null && doomed.contains(Long.valueOf(packPosition(x, y, z)))) continue;
                    step(world, x, y, z, amount);
                }
            }
        }
    }

    /** One world position in a single long, for the set of blocks a blast is about to take. */
    public static long packPosition(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Records wear at one position and advances its appearance if the threshold is met. */
    public void step(World world, int x, int y, int z, float amount) {
        if (y < TrmtConfig.minY || y > TrmtConfig.maxY) return;
        if (!com.trmtgtnh.util.Worlds.loaded(world, x, y, z)) return;

        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        int meta = com.trmtgtnh.util.Worlds.metaAt(world, x, y, z);

        // Snow more than one layer deep holds the feet up inside its own cell, so the step arrives
        // addressed to the snow rather than to the ground beneath - and a step addressed to snow is
        // dropped two lines below, because snow layers are not a wearing surface. That is why deep
        // snow has always been shelter, and why it was permanent and free. Step down one and let
        // the ground answer for itself. A single layer needs none of this: its collision box is of
        // no height at all, so the feet were already standing on the ground.
        if (TrmtConfig.snowCovers && y - 1 >= TrmtConfig.minY && SnowCover.isSnowLayer(block)) {
            y--;
            block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
            meta = com.trmtgtnh.util.Worlds.metaAt(world, x, y, z);
        }

        SurfaceFamily base = SurfaceRegistry.familyOf(block, meta);
        if (base == null || !base.staged) return;

        // Something solid resting on top means this is not exposed ground any more.
        if (com.trmtgtnh.util.Worlds.isOpaque(world, x, y + 1, z)) return;

        // And snow lying on it takes the step in the ground's place, until it has been trodden
        // through. Above the store entirely, so a square under snow spends no record at all and a
        // snowfield cannot eat the budget a road across it will want.
        if (SnowCover.covers(world, x, y, z) && SnowCover.takes(world, x, y, z, amount)) return;

        ChunkErosionData data = ErosionStore.get()
            .getOrCreateChunk(world.provider.getDimension(), x >> 4, z >> 4);
        int key = ErosionKey.packWorld(x, y, z);
        int now = nowSeconds(world);

        float resistance = SurfaceRegistry.resistanceOf(block, meta);

        ErosionEntry entry = data.get(key);
        // Pinned ground takes no wear, however much is walked over it. Checked before the wear
        // is banked rather than before it is spent, so a pin does not quietly accumulate a debt
        // that lands the moment it is lifted.
        if (entry != null && entry.isFrozen()) return;
        if (entry == null) {
            if (data.size() >= TrmtConfig.maxEntriesPerChunk) return;
            entry = new ErosionEntry(base, drawThreshold(base, 0) * resistance, now);
            data.put(key, entry);
        } else {
            short before = entry.isVisible() ? entry.packState() : ErosionState.NONE;
            if (reseat(entry, base)) {
                // Only moved because the chain moved. Tell the clients where it landed, or they
                // go on drawing a step this world no longer has.
                if (entry.packState() != before) TrmtNetwork.sendDelta(world, x, y, z, entry.packState());
                // A record made by a reinforcement, a ward or a demonstration holds a stand-in threshold,
                // because none of them can draw one, and one the heal sweep kept only for its protection
                // holds nought. Drawn here, the first time the square is walked on and before this step is
                // counted, with any wear it carries kept - and a record an earlier version saved that way is
                // put right the same way.
                if (entry.awaitsDraw()) {
                    float carried = entry.getWear();
                    entry.setAppearance(base, -1, drawThreshold(base, 0) * resistance);
                    entry.setWear(carried);
                }
            } else {
                // The block underneath changed into something from a different family. Whatever
                // wear it had described a surface that is no longer there.
                entry.retarget(base, drawThreshold(base, 0) * resistance, now);
                if (before != ErosionState.NONE) TrmtNetwork.sendDelta(world, x, y, z, ErosionState.NONE);
            }
        }

        entry.recordStep(amount, now);
        data.markDirty();
        ErosionStore.get()
            .markModified(world, x >> 4, z >> 4);

        // Carried rather than discarded, and looped rather than taken one gradation at a time.
        //
        // A footstep barely clears its threshold, so for traffic this is the same behaviour with
        // the rounding error taken out. For a single hard blow it is the difference between the
        // strength figure meaning something and meaning nothing: a charge worth twenty crossings
        // used to advance exactly one gradation out of sixteen and throw the other nineteen
        // crossings away, which is why a stick of dynamite left ground that looked untouched.
        boolean moved = false;
        // Kept so the plants standing here can be told a change of shade from the ground going out
        // from under them. The stored depth, not the drawn one: what is drawn depends on settings
        // this side of the question has no business reading.
        int sankFrom = entry.getSink();
        int guard = ErosionChain.length(base) + 1;
        while (entry.thresholdReached() && guard-- > 0) {
            float surplus = entry.getWear() - entry.effectiveThreshold();
            // False means the chain is finished; advance has already pinned the wear at the
            // threshold, and looping again would spin on a position that cannot move.
            if (!advance(base, entry, resistance)) break;
            entry.setWear(surplus);
            moved = true;
        }
        if (moved) {
            TrmtNetwork.sendDelta(world, x, y, z, entry.packState());
            WearDrops.roll(world, x, y, z, base);
            // Last, and after the packet has gone. This is one of the few lines in the engine that edit
            // the world - ground wearing away and trampling are the others, and each is likewise done
            // last - and breaking a block runs neighbour updates through arbitrary mod code, so
            // everything this position needed of the engine is already done before that starts.
            // Outside the loop above rather than inside it, so a charge worth twenty crossings
            // knocks the plant down once rather than asking twenty times.
            GroundCover.breakAbove(world, x, y, z, true, entry.getSink() > sankFrom);
        } else if (GroundGivesWay.dueToGo(world, x, y, z, base, entry)) {
            // Only on a tick that moved nothing. A single charge can carry a whole chain, and a
            // rule that fired in the same call would turn ground the blast deliberately spared
            // into a hole with no worn stage in between. Because the refusal above pins the wear
            // just past the end, the next call finds the same position with nothing left to
            // advance to and gives way then - one tick later, and visibly worn out first.
            GroundGivesWay.take(world, x, y, z, block, meta, data, key);
        }
    }

    /**
     * Puts an entry back on its chain when the chain has moved under it.
     *
     * <p>
     * Two things can make a stored appearance stop being a step on the ground's chain, and they
     * want opposite treatment. The ground may genuinely have become something else - vanilla
     * spreading grass over dirt - in which case the wear described a surface that is not there and
     * has to go. Or the chain may have been rebuilt from changed settings, in which case the wear
     * is perfectly real and only its address has gone stale. Telling them apart is what
     * {@link ErosionChain#nearestIndex} is for: it will only place a record among steps drawn as
     * the same appearance, so the first case still finds nothing.
     *
     * <p>
     * Progress toward the next step is carried across rather than reset. It was earned under the
     * old numbers and the new threshold is the only thing that has changed about what it buys.
     *
     * @return false when this entry does not belong to this surface at all and should be dropped
     */
    private static boolean reseat(ErosionEntry entry, SurfaceFamily base) {
        if (!entry.isVisible()) return entry.getFamily() == base;
        if (ErosionChain.indexOf(base, entry.getFamily(), entry.getStage(), entry.getSink()) >= 0) return true;

        // Asked in this order deliberately. A record showing a surface this one is configured to
        // wear through into is answered at its own depth first, because that is the case where the
        // chain has been reshaped under ground that is otherwise exactly what it says it is -
        // switching the successor runs on or off, or changing what a family becomes. Only when
        // there is no step at that depth at all does the coarser search run, and only when the
        // appearance has never belonged to this base is the record given up as somebody else's.
        int near = ErosionChain.reseatIndex(base, entry.getFamily(), entry.getStage(), entry.getSink());
        if (near < 0) near = ErosionChain.nearestIndex(base, entry.getFamily(), entry.getStage(), entry.getSink());
        if (near < 0) return false;
        // Held to what this world currently lets ground reach, so a lowered ceiling brings deep
        // ground up to the new limit instead of leaving it past a line nothing can cross.
        int capped = ErosionChain.cappedLength(base);
        if (capped > 0 && near >= capped) near = capped - 1;

        SurfaceFamily appearance = ErosionChain.familyAt(base, near);
        int stage = ErosionChain.stageAt(base, near);
        if (appearance == null || stage < 0) return false;

        float carried = entry.getWear();
        entry.setAppearance(appearance, stage, entry.getThreshold());
        entry.setSink(ErosionChain.sinkAt(base, near));
        entry.setWear(carried);
        return true;
    }

    /** True when this entry's appearance is still a position on the base surface's chain. */
    private static boolean belongsTo(ErosionEntry entry, SurfaceFamily base) {
        if (!entry.isVisible()) return entry.getFamily() == base;
        return ErosionChain.indexOf(base, entry.getFamily(), entry.getStage(), entry.getSink()) >= 0;
    }

    /**
     * Moves one step along the chain. Returns true when the visible appearance changed and
     * clients need telling.
     */
    private boolean advance(SurfaceFamily base, ErosionEntry entry, float resistance) {
        int index = entry.isVisible() ? ErosionChain.indexOf(base, entry.getFamily(), entry.getStage(), entry.getSink())
            : -1;
        int next = index + 1;
        if (next >= ErosionChain.cappedLength(base)) {
            // As worn as this world lets it get. Hold wear at the threshold rather than letting
            // it run away, so the moment traffic stops the healing clock starts from a known
            // point - and so lowering the ceiling later does not hand back a banked debt.
            //
            // One threshold further when ground is allowed to wear away entirely, because that is
            // the margin the rule is built on: a square has to be worn a whole phase past the end
            // of its run before it goes, which is both the grace an older save needs and what
            // stops one hard blow taking untouched ground to a hole inside a single tick. Still
            // bounded, so the debt cannot run away either way.
            float ceiling = TrmtConfig.groundWearsAway ? entry.effectiveThreshold() * 2f : entry.effectiveThreshold();
            if (entry.getWear() > ceiling) entry.setWear(ceiling);
            return false;
        }
        SurfaceFamily appearance = ErosionChain.familyAt(base, next);
        int stage = ErosionChain.stageAt(base, next);
        if (appearance == null) return false;
        entry.setAppearance(appearance, stage, drawThreshold(ErosionChain.pace(base, appearance), next) * resistance);
        entry.setSink(ErosionChain.sinkAt(base, next));
        return true;
    }

    /**
     * Draws a fresh threshold, unscaled, for a gradation at this point along its run.
     *
     * <p>
     * A per-position random draw, which is what keeps path edges ragged rather than geometric,
     * times whatever the family's curve asks for at that point. The speed multiplier is applied
     * where the threshold is compared against rather than here, so that changing the speed moves
     * ground that already exists; the curve is applied here for the opposite reason, because it
     * belongs to the gradation being priced and not to the moment somebody walks on it.
     *
     * <p>
     * Measured against the whole chain rather than against what a wear ceiling allows. A ceiling
     * shortens what ground can reach and must not reshape what the reachable part costs, or
     * lowering it would silently make the early gradations dearer.
     *
     * @param index which gradation this threshold is for, nought being ground nobody has touched
     */
    private float drawThreshold(SurfaceFamily family, int index) {
        FamilySettings settings = TrmtConfig.family(family);
        if (settings == null) return 1f;
        float min = settings.scaledMin();
        float max = settings.scaledMax();
        float drawn = max <= min ? Math.max(min, 0.01f) : min + random.nextFloat() * (max - min);
        float shaped = drawn * settings.costCurve.at(index, ErosionChain.length(family));
        return shaped < 0.01f ? 0.01f : shaped;
    }

    // ------------------------------------------------------------------
    // Healing
    // ------------------------------------------------------------------

    /**
     * Applies everything a chunk missed while it was unloaded, then tells any watching clients what the
     * chunk looks like now - at the end of the tick.
     *
     * <p>
     * At the end of the tick rather than here, because here is too early to know who is watching. A
     * chunk loaded from disk in the background is announced while the players waiting for it already
     * count as watching it and have not been sent it; later in the same tick they are queued for it,
     * and stop counting until it goes out. Sent from here, the wear reached a client with no chunk to
     * put it on, and every change made before the chunk itself arrived - a square healing, a block
     * broken, somebody mending - was refused for that player as not watched. The client painted the
     * wear it had been handed first, a rut the server no longer had, for as long as it kept the chunk.
     * By the end of the tick those players are queued, so they are left to the watch event, which sends
     * the chunk as it stands when it actually arrives.
     */
    public void catchUpChunk(final World world, final int chunkX, final int chunkZ, ChunkErosionData data) {
        // No per-position deltas here: the whole chunk goes out afterwards, and for a chunk that has been
        // away for months that is one packet instead of hundreds.
        healChunk(world, chunkX, chunkZ, data, false);
        if (world == null || world.isRemote) return;
        final int dimension = world.provider.getDimension();
        com.trmtgtnh.util.MainThread.onServer(new Runnable() {

            @Override
            public void run() {
                // Looked up again rather than carried, so what goes out is the chunk as it stands then.
                ChunkErosionData now = ErosionStore.get()
                    .getChunk(dimension, chunkX, chunkZ);
                if (now != null) TrmtNetwork.sendChunkToWatchers(world, chunkX, chunkZ, now);
            }
        });
    }

    /**
     * Walks a chunk's entries and heals each by the elapsed world time.
     *
     * <p>
     * Reads block state from the {@link Chunk} directly rather than through the world,
     * because this runs during chunk load, when the world may not yet answer for these
     * coordinates.
     */
    public void healChunk(World world, int chunkX, int chunkZ, ChunkErosionData data, boolean notifyClients) {
        if (!TrmtConfig.healingEnabled || data.isEmpty()) return;

        Chunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk == null) return;

        int now = nowSeconds(world);
        // Read once for the whole chunk, because rain falls on all of it at once. A position the
        // rain cannot actually reach is refused separately, below.
        int wetAllowance = Weather
            .allowance(world, ErosionStore.chunkKey(world.provider.getDimension(), chunkX, chunkZ));
        // Asked once for the whole chunk as well, because a discount nobody in it can earn
        // is not worth asking about per position: the per-position test costs a sky lookup
        // and a biome lookup, and a chunk can hold three thousand records.
        boolean maybeRaining = world.isRaining() && world.provider.hasSkyLight();
        int[] keys = data.keys();
        for (int key : keys) {
            ErosionEntry entry = data.get(key);
            if (entry == null) continue;

            int elapsed = now - entry.getLastTouchedSeconds();
            if (elapsed <= 0) continue;

            int localX = ErosionKey.localX(key);
            int localZ = ErosionKey.localZ(key);
            int y = ErosionKey.y(key);

            int x = (chunkX << 4) + localX;
            int z = (chunkZ << 4) + localZ;

            Block block = com.trmtgtnh.util.Worlds.blockAt(chunk, localX, y, localZ);
            int meta = com.trmtgtnh.util.Worlds.metaAt(chunk, localX, y, localZ);
            SurfaceFamily base = SurfaceRegistry.familyOf(block, meta);

            // A record whose step the chain no longer has is put back on the nearest one it does
            // have before any of this, because a rebuilt chain is not a changed surface and used
            // to be read as one - which made every edit to the wear settings a silent deletion of
            // every worn path in the world at the next chunk load.
            if (base != null && base.staged && entry.isVisible()) {
                short seated = entry.packState();
                if (reseat(entry, base) && entry.packState() != seated) {
                    data.markDirty();
                    if (notifyClients) TrmtNetwork.sendDelta(world, x, y, z, entry.packState());
                }
            }

            // A plant's or a leaf's trample tally, still on the block it was counted against. Taken
            // out ahead of the test below, which reads every family without gradations as a surface
            // that has gone: that test used to delete each tally at the first sweep after it was
            // counted, so nothing short of a stampede inside ten seconds ever broke anything.
            if (isTally(entry, base)) {
                // Faded without marking the chunk for saving: the fade is a straight line from the last
                // crossing, so one that is never written is worked out again, to the same figure, next
                // time. Marking it would rewrite every chunk holding a tally on every autosave for as
                // long as the tally lasted, meadows and forests with no worn ground in them included.
                if (TrampleTally.fade(entry, now, TrmtConfig.wearDecayPerDay)) data.markDirty();
                if (entry.isPrunable()) data.remove(key);
                // No packet: invisible before and after. And no weather lookups, which a tally never reads.
                continue;
            }

            if (base == null || !base.staged || !belongsTo(entry, base)) {
                // The ground is no longer the surface this entry described. Drop it, and tell
                // anyone still showing it so the overlay comes off now rather than on reload.
                boolean wasVisible = entry.isVisible();
                if (entry.getReinforce() > 0 || entry.getWard() != 0) {
                    // Except what it carries for the position rather than for the surface: a
                    // reinforcement or a spawn ward. Strip the stale wear so no ghost is drawn, and
                    // keep the rest. A ward was missing from this test, and a ward is exactly what
                    // somebody puts on a floor that is not a wearing surface at all - brick, planks,
                    // a stone slab - so every ward of that kind was deleted by the first sweep after
                    // it was paid for. Marked dirty only when something was actually stripped, or a
                    // record with nothing left to strip is rewritten on every sweep for ever.
                    boolean stale = wasVisible || entry.getWear() > 0f || entry.getSink() != 0;
                    entry.setAppearance(entry.getFamily(), -1, 0f);
                    entry.setSink(0);
                    entry.setWear(0f);
                    if (stale) data.markDirty();
                    if (wasVisible && notifyClients) TrmtNetwork.sendDelta(world, x, y, z, ErosionState.NONE);
                    continue;
                }
                data.remove(key);
                if (wasVisible && notifyClients) TrmtNetwork.sendDelta(world, x, y, z, ErosionState.NONE);
                continue;
            }

            // Deliberately below the mismatch drop above: a pin holds a position against time,
            // not against its own surface being replaced by something the chain no longer has a
            // step for, which nothing else would ever clean up.
            if (entry.isFrozen()) continue;

            // Minus one is "as many as the days can afford", which is what every family that
            // is not waiting for weather has always had.
            int cap = -1;
            if (Weather.waitsForWeather(base, world)) {
                cap = Weather.fallingOn(world, x, y, z, base) ? wetAllowance : 0;
            }

            // A discount on the price, for a family that is not already being paid by the rain
            // through the meter above. Cheapening a gated family as well would be paying for one
            // storm twice. Note that with the whole weather mechanism switched off nothing is
            // gated, so everything becomes eligible - which is right, and looks enough like an
            // oversight to be worth saying.
            boolean wet = maybeRaining && cap < 0 && Weather.fallingOn(world, x, y, z, base);

            short before = entry.isVisible() ? entry.packState() : ErosionState.NONE;
            if (!heal(entry, base, elapsed, now, cap, wet, SurfaceRegistry.resistanceOf(block, meta))) continue;

            data.markDirty();
            ErosionStore.get()
                .markModified(world, x >> 4, z >> 4);
            short after = entry.isVisible() ? entry.packState() : ErosionState.NONE;
            if (entry.isPrunable()) data.remove(key);
            // Healing that only bled off partial wear changes nothing anyone can see, so the
            // packet is sent on a change of appearance rather than on any change at all.
            if (notifyClients && after != before) TrmtNetwork.sendDelta(world, x, y, z, after);
        }
    }

    /**
     * Rolls one entry back by however much time has passed.
     *
     * <p>
     * Partial wear bleeds off first, then whole stages, and whatever time is left over is
     * banked into the entry's clock so healing is continuous rather than resetting on every
     * check. Returns true when anything changed.
     */
    /**
     * @param stageCap   how many gradations this entry may be given back on this pass, or -1 for as
     *                   many as its banked days can afford. Nought means nothing at all is paid and,
     *                   crucially, nothing is spent either - the clock is left exactly where it was,
     *                   so a dry spell delays recovery instead of consuming it.
     * @param wet        whether rain is falling on this square, which buys a discount on the price of
     *                   a gradation and on the rate partial wear bleeds away. Applied to the price
     *                   and never to the clock: the leftover days are banked back into the record in
     *                   the units they were counted in, so cheapening those instead would write
     *                   in-game time that never elapsed into the position every time it rained.
     * @param resistance the square's block resistance, which every threshold written here is multiplied
     *                   by exactly as wearing multiplies its own. Left out, a square healed back to
     *                   pristine and kept for a reinforcement or a ward held an unscaled draw that was
     *                   never drawn again, so on a resistant block - a grass path at eight - the paid-for
     *                   protection wore into its first gradation about eight times sooner than the
     *                   unprotected square beside it; and a partial heal left every resistant square
     *                   wearing back up at the unscaled price.
     */
    private boolean heal(ErosionEntry entry, SurfaceFamily base, int elapsedSeconds, int now, int stageCap, boolean wet,
        float resistance) {
        if (stageCap == 0) return false;

        double daysLeft = elapsedSeconds / (double) SECONDS_PER_DAY;
        if (daysLeft <= 0) return false;

        boolean changed = false;

        // 1. Bleed off progress toward the next stage.
        double decayPerDay = TrmtConfig.wearDecayPerDay * entry.effectiveThreshold();
        // The partial bleed as well as the whole gradations, or rain would visibly do
        // nothing to a lightly used path, which is most of what anybody is looking at.
        if (wet) decayPerDay *= wetFactor(base, entry.getFamily());
        if (decayPerDay > 0 && entry.getWear() > 0f) {
            double daysToZero = entry.getWear() / decayPerDay;
            if (daysLeft < daysToZero) {
                entry.setWear((float) (entry.getWear() - decayPerDay * daysLeft));
                entry.setLastTouchedSeconds(now);
                return true;
            }
            entry.setWear(0f);
            daysLeft -= daysToZero;
            changed = true;
        }

        // 2. Step whole stages back down the chain.
        int given = 0;
        while (entry.isVisible()) {
            if (stageCap >= 0 && given >= stageCap) break;
            // Priced by the ground rather than by the picture, the same way wearing is. A stone
            // road showing the grit it shed would otherwise recover at grit's rate, which turns
            // the hundred and forty-four in-game days an abandoned one takes into about sixty.
            FamilySettings settings = TrmtConfig.family(ErosionChain.pace(base, entry.getFamily()));
            double cost = settings == null ? Double.MAX_VALUE : settings.scaledHealDays();
            if (wet && settings != null) cost /= atLeastOne(settings.wetRecoverySpeed);
            // Asked of HealBank rather than compared here, so a remainder the arithmetic has left a hair
            // short of the price pays for it. Compared exactly, a catch-up owing four gradations of snow was
            // refused the fourth, and ground waiting on the weather then waited for the next metered one.
            if (!HealBank.affords(daysLeft, cost)) break;

            daysLeft -= cost;
            int index = ErosionChain.indexOf(base, entry.getFamily(), entry.getStage(), entry.getSink());
            if (index <= 0) {
                // Back to pristine. Leaving the entry empty lets the store prune it, which is
                // how a healed chunk sheds its NBT tag entirely. Drawn with the resistance, because a
                // record kept for its protection holds this draw as real and is never drawn for again.
                entry.setAppearance(base, -1, drawThreshold(base, 0) * resistance);
                entry.setWear(0f);
                entry.setSink(0);
            } else {
                SurfaceFamily appearance = ErosionChain.familyAt(base, index - 1);
                int stage = ErosionChain.stageAt(base, index - 1);
                if (appearance == null) break;
                entry.setAppearance(
                    appearance,
                    stage,
                    drawThreshold(ErosionChain.pace(base, appearance), index - 1) * resistance);
                // Recovering fills a rut back in as well as growing its cover back.
                entry.setSink(ErosionChain.sinkAt(base, index - 1));
            }
            given++;
            changed = true;
        }

        // 3. Bank the remainder so no partial progress is lost between checks. Whatever the meter
        // would not let this pass hand over stays here, which is what makes the debt survive a
        // drought rather than evaporate during one. Rounded in HealBank rather than cast here: the
        // division and subtractions above leave a remainder that should be whole a hair short of it,
        // and a cast dropped the second, most often over ground that had paid for nothing at all.
        entry.setLastTouchedSeconds(now - HealBank.bankedSeconds(daysLeft, elapsedSeconds));
        return changed;
    }

    /**
     * A recovery multiplier that can only ever help.
     *
     * <p>
     * Its own guard rather than the config's package-private one, and the floor is one rather than
     * anything above nought: a discount that could slow recovery would be a gate wearing a
     * discount's name, which is the whole distinction this feature turns on.
     */
    private static double atLeastOne(float rate) {
        return rate < 1f ? 1d : rate;
    }

    /**
     * How much faster rain mends this position, priced by the ground rather than by the picture.
     *
     * <p>
     * The same rule the cost beside it follows: a stone road showing the grit it shed recovers at
     * stone's rate, so it takes stone's view of the weather too.
     */
    private static double wetFactor(SurfaceFamily base, SurfaceFamily appearance) {
        FamilySettings paced = TrmtConfig.family(ErosionChain.pace(base, appearance));
        return paced == null ? 1.0d : atLeastOne(paced.wetRecoverySpeed);
    }

    /**
     * Periodic healing pass over chunks that have stayed loaded long enough to need one.
     *
     * <p>
     * One slice for the whole server, each chunk healed in its own world. The slice used to be
     * taken once per world and filtered down to that world, which moved one shared cursor on as
     * many times as there are dimensions and handed each world a window mostly made of other
     * worlds' chunks. Once more chunks were loaded than one pass takes, some of them fell into
     * the wrong world's window every single time and never healed for as long as they stayed
     * loaded - which is precisely the ground around a base.
     */
    public void sweep() {
        List<Long> slice = ErosionStore.get()
            .nextSweepSlice(TrmtConfig.sweepChunksPerPass);
        for (Long boxed : slice) {
            long chunkKey = boxed.longValue();
            World world = net.minecraftforge.common.DimensionManager.getWorld(ErosionStore.dimensionOf(chunkKey));
            if (world == null) continue;
            ChunkErosionData data = ErosionStore.get()
                .getChunk(chunkKey);
            if (data == null || data.isEmpty()) continue;

            int chunkX = ErosionStore.chunkXOf(chunkKey);
            int chunkZ = ErosionStore.chunkZOf(chunkKey);
            if (!com.trmtgtnh.util.Worlds.chunkLoaded(world, chunkX, chunkZ)) continue;
            healChunk(world, chunkX, chunkZ, data, true);
        }
    }

    public void onServerTick() {
        if (!TrmtConfig.enabled || !TrmtConfig.healingEnabled) return;

        // Counted every tick rather than every sweep, because it is a clock: a sweep that runs once
        // in two hundred ticks cannot tell how much of that was weather, and the whole point of the
        // meter is that it knows.
        if (TrmtConfig.wetHealingEnabled) {
            net.minecraft.server.MinecraftServer counting = com.trmtgtnh.Trmt.server();
            if (counting != null && counting.worlds != null) {
                for (World world : counting.worlds) Weather.tick(world);
            }
        }

        if (++tickCounter < TrmtConfig.sweepIntervalTicks) return;
        tickCounter = 0;

        net.minecraft.server.MinecraftServer server = com.trmtgtnh.Trmt.server();
        if (server == null || server.worlds == null) return;
        sweep();
    }

    // ------------------------------------------------------------------
    // Trampling
    // ------------------------------------------------------------------

    /**
     * Counts one crossing against a plant in the way or a leaf underfoot, and breaks the block when
     * its tally runs out, when the matching switch is on. The tally is an ordinary invisible record at
     * the block's own position, faded at the ground's partial decay before each crossing and on every
     * sweep. Snow on a leaf takes the crossing first, as it does on ground. Reinforcement, a ward, a
     * glow or a pin then refuses it outright, the rule groundWearsAway follows, and a plant in tilled
     * soil is spared unless groundCoverOnTilled says otherwise. The drop is asked for while the block
     * still stands, because a tile-entity block can only say what it drops while its tile entity
     * exists, and removing the block is the last thing done.
     *
     * @param target the family being looked for at this position: a plant in the cell the feet are in,
     *               or the leaf {@link #leafUnderfoot} found. Whatever else stands there is left alone,
     *               so the call made for plants can never break a leaf, nor the one made for leaves a
     *               plant.
     */
    private void tryTrample(World world, int x, int y, int z, float amount, SurfaceFamily target) {
        if (!tramples(target)) return;
        if (y < Math.max(0, TrmtConfig.minY) || y > Math.min(255, TrmtConfig.maxY)) return;
        if (!com.trmtgtnh.util.Worlds.loaded(world, x, y, z)) return;

        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        int meta = com.trmtgtnh.util.Worlds.metaAt(world, x, y, z);
        if (SurfaceRegistry.familyOf(block, meta) != target) return;

        // A farm. GroundCover's own test, asked the same way, because tilled soil is the one honest
        // signal that somebody planted this. Without it, tallies that last would bring down a village's
        // fields and a player's own crops at the pace of the harvesting.
        if (target == SurfaceFamily.VEGETATION && !TrmtConfig.groundCoverOnTilled
            && y > 0
            && com.trmtgtnh.util.Worlds.blockAt(world, x, y - 1, z) == Blocks.FARMLAND) return;

        // Snow lying on a leaf takes the crossing in the leaf's place until it has been trodden through,
        // as it does on ground. Above the store, so a snowed-over canopy spends no record, and above the
        // protections, so snow on a warded leaf is trodden away exactly as snow on warded ground is.
        if (target == SurfaceFamily.LEAVES && SnowCover.covers(world, x, y, z)
            && SnowCover.takes(world, x, y, z, amount)) return;

        // Refused outright rather than made dearer, which is how groundWearsAway treats a spent tamper
        // charge. Asked before anything is counted or retargeted: a reinforcement or a ward put on a leaf
        // or a plant is kept in a record of the ground's families, and this used to retarget that record
        // and then delete it with the block, taking the protection somebody paid for along with it. A
        // glow and a pin can only sit on a visible record, so those two are asked for completeness.
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        if (entry != null && (entry.getReinforce() > 0 || entry.getWard() != 0 || entry.isLit() || entry.isFrozen()))
            return;

        ChunkErosionData data = ErosionStore.get()
            .getOrCreateChunk(world.provider.getDimension(), x >> 4, z >> 4);
        int key = ErosionKey.packWorld(x, y, z);
        FamilySettings settings = TrmtConfig.family(target);
        // At face value. The speeds are applied where the tally is compared, in
        // ErosionEntry.effectiveThreshold, and applying them here as well used to divide the draw by
        // the square of the speed, so at an erosionSpeed of 8 a single crossing broke a leaf.
        float min = settings == null ? 16f : settings.thresholdMin;
        float max = settings == null ? 24f : settings.thresholdMax;
        int now = nowSeconds(world);

        if (entry == null) {
            if (data.size() >= TrmtConfig.maxEntriesPerChunk) return;
            entry = TrampleTally.start(target, min, max, random.nextFloat(), now);
            data.put(key, entry);
        } else if (entry.getFamily() != target || entry.isVisible()) {
            // Something else was tracked here, most often worn ground a plant or a leaf has since taken
            // the place of. Its wear described a surface that is no longer there. The depth goes too,
            // because an invisible record still packs it and a tally has no business carrying a rut.
            boolean wasVisible = entry.isVisible();
            entry.retarget(target, TrampleTally.draw(min, max, random.nextFloat()), now);
            entry.setSink(0);
            if (wasVisible) TrmtNetwork.sendDelta(world, x, y, z, ErosionState.NONE);
        } else if (!TrampleTally.isFaceValue(entry.getThreshold(), min, max)) {
            // Saved by an older version: drawn already divided by the speeds, or zeroed by the sweep that
            // stripped a protected record. Redrawn with its wear kept, and ahead of the fade below so the
            // fade is priced by the threshold the tally will actually be held to.
            float carried = entry.getWear();
            entry.setAppearance(target, -1, TrampleTally.draw(min, max, random.nextFloat()));
            entry.setWear(carried);
        }

        // Faded first, then counted. The crossing restamps the clock, so without the fade whatever decay
        // the sweep had not reached yet would be thrown away with the old stamp, and a plant crossed a
        // little more often than the sweep comes round would never recover at all. With healing off
        // nothing fades here, as nothing fades on the sweep.
        boolean due = TrampleTally.cross(entry, amount, now, TrmtConfig.wearDecayPerDay, TrmtConfig.healingEnabled);
        data.markDirty();
        ErosionStore.get()
            .markModified(world, x >> 4, z >> 4);
        if (!due) return;

        // The record first and the world last. Breaking the block runs neighbour updates through
        // arbitrary mod code - a broken leaf sets every leaf around it checking whether to decay - so
        // everything this position needed of the engine is finished before any of that starts.
        data.remove(key);
        data.markDirty();
        ErosionStore.get()
            .markModified(world, x >> 4, z >> 4);

        // Sound and particles as though it had been broken. Spelled out rather than calling the world's
        // own destroy helper, which is unmapped here.
        com.trmtgtnh.util.Worlds.playBreakEffect(world, x, y, z, block, meta);

        // Asked for while the block still stands, and that order is load-bearing: a leaf or a plant kept
        // in a tile entity - Forestry's leaves are one - can only say what it drops while its tile entity
        // exists, so removing the block first would quietly turn every such drop into nothing.
        float dropChance = target == SurfaceFamily.LEAVES ? TrmtConfig.leavesDropChance
            : TrmtConfig.vegetationDropChance;
        if (dropChance >= 1.0f || (dropChance > 0.0f && random.nextFloat() < dropChance)) {
            com.trmtgtnh.util.Worlds.dropAsItem(world, block, meta, x, y, z, 0);
        }

        // Checked again rather than trusted, as GroundGivesWay.take checks, because the drop ran mod code
        // too. Whatever stands here now, if it is not the block this tally was counted against, it is not
        // this rule's to remove.
        if (com.trmtgtnh.util.Worlds.blockAt(world, x, y, z) == block)
            com.trmtgtnh.util.Worlds.setToAir(world, x, y, z);
    }

    /**
     * The height of the leaf a walker's crossing counts against, or -1 when there is none.
     *
     * <p>
     * A leaf has a collision box of its own, so for anyone walking across it the leaf is the cell
     * underfoot and the cell the feet are in is the air above. Plants are looked for in that upper
     * cell, which is why the leaves switch, looked for in the same place, could never fire. Snow two
     * layers deep or more holds the feet up inside its own cell; with weather.snowCovers on, the leaf
     * beneath is found by stepping down one, the same test step makes for ground. With it off, that
     * snow shelters the leaf for as long as it lies.
     */
    private static int leafUnderfoot(World world, int x, int y, int z) {
        if (!TrmtConfig.trampleLeaves) return -1;
        if (y < 0 || y > 255) return -1;
        if (!com.trmtgtnh.util.Worlds.loaded(world, x, y, z)) return -1;

        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        int meta = com.trmtgtnh.util.Worlds.metaAt(world, x, y, z);
        if (TrmtConfig.snowCovers && y - 1 >= Math.max(0, TrmtConfig.minY) && SnowCover.isSnowLayer(block)) {
            y--;
            block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
            meta = com.trmtgtnh.util.Worlds.metaAt(world, x, y, z);
        }
        if (SurfaceRegistry.familyOf(block, meta) != SurfaceFamily.LEAVES) return -1;
        return covered(world, x, y, z) ? -1 : y;
    }

    /**
     * Whether something laid on this leaf takes the traffic in its place, so the leaf keeps no tally.
     *
     * <p>
     * Anything at all but air, a snow layer or a plant. A carpet is the case this exists for: its
     * collision box has no height, so the walker's feet still come down on the leaf's own cell and
     * nothing else would ever notice the carpet was there. Snow is left to SnowCover, which treads it
     * away first, and a vine hanging into the space above shelters nothing.
     *
     * <p>
     * Never asks isOpaqueCube. For a leaf its answer is a client's graphics setting rather than
     * anything about the leaf, so a dedicated server and a single-player world would disagree.
     */
    private static boolean covered(World world, int x, int y, int z) {
        if (y >= 255) return false;
        Block above = com.trmtgtnh.util.Worlds.blockAt(world, x, y + 1, z);
        if (above == null || com.trmtgtnh.util.Worlds.isAir(world, x, y + 1, z)) return false;
        if (SnowCover.isSnowLayer(above)) return false;
        return SurfaceRegistry.familyOf(above, com.trmtgtnh.util.Worlds.metaAt(world, x, y + 1, z))
            != SurfaceFamily.VEGETATION;
    }

    /** Whether the trampling switch for this family is on. Only plants and leaves have one. */
    private static boolean tramples(SurfaceFamily family) {
        if (family == SurfaceFamily.VEGETATION) return TrmtConfig.trampleVegetation;
        if (family == SurfaceFamily.LEAVES) return TrmtConfig.trampleLeaves;
        return false;
    }

    /**
     * Whether this record is a trample tally still standing on its own block.
     *
     * <p>
     * Deliberately does not ask whether the family's switch is on. A tally is progress that was earned,
     * and switching trampling off, or a typo caught by a reload, should leave it to fade like any other
     * rather than delete it, so that turning the switch back on inside the window carries on where it
     * left off. Asking the switch would also send a switched-off family's tallies into the test that
     * follows in the sweep, which deletes them outright, or zeroes the threshold of one that carries a
     * reinforcement or a ward.
     */
    private static boolean isTally(ErosionEntry entry, SurfaceFamily base) {
        return base != null && !base.staged && !entry.isVisible() && entry.getFamily() == base;
    }

    // ------------------------------------------------------------------
    // External edits
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // The grace period
    // ------------------------------------------------------------------

    /**
     * A record set aside when its block was broken, waiting to see whether that block comes back.
     *
     * <p>
     * It holds the whole entry - wear, reinforcement, ward and all - together with which block was
     * standing there and when it went. Kept in memory rather than written down: the window is
     * fifteen real minutes, which a chunk almost always outlives, and a record that survived a
     * restart to reattach itself to a block somebody replaced hours later would be a surprise
     * rather than a kindness.
     */
    private static final class Orphan {

        final ErosionEntry entry;
        final int blockId;
        final int meta;
        final long brokenAtMs;

        Orphan(ErosionEntry entry, int blockId, int meta, long brokenAtMs) {
            this.entry = entry;
            this.blockId = blockId;
            this.meta = meta;
            this.brokenAtMs = brokenAtMs;
        }
    }

    private final java.util.Map<String, Orphan> orphans = new java.util.HashMap<String, Orphan>();

    private static String orphanKey(int dimension, int x, int y, int z) {
        return dimension + ":" + x + ":" + y + ":" + z;
    }

    private static long graceMillis() {
        return (long) Math.max(0, TrmtConfig.graceSeconds) * 1000L;
    }

    /**
     * A block was broken: set its record aside instead of dropping it.
     *
     * <p>
     * The ghost goes at once - the block it was covering is gone - but what the position carried
     * is kept, so putting the same block back inside the window brings all of it home. With the
     * grace period switched off this is exactly {@link #forget}.
     */
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (world == null || world.isRemote) return;
        if (graceMillis() <= 0 || block == null) {
            forget(world, x, y, z);
            return;
        }

        ChunkErosionData data = ErosionStore.get()
            .getChunk(world.provider.getDimension(), x >> 4, z >> 4);
        if (data == null) return;
        int key = ErosionKey.packWorld(x, y, z);
        ErosionEntry entry = data.get(key);
        if (entry == null) return;

        boolean wasVisible = entry.isVisible();
        data.remove(key);
        data.markDirty();
        ErosionStore.get()
            .markModified(world, x >> 4, z >> 4);
        if (wasVisible) TrmtNetwork.sendDelta(world, x, y, z, ErosionState.NONE);

        orphans.put(
            orphanKey(world.provider.getDimension(), x, y, z),
            new Orphan(entry, Block.getIdFromBlock(block), meta, System.currentTimeMillis()));
    }

    /**
     * A block was placed: put the record back if this is the same block returning in time.
     *
     * <p>
     * The exact block, not merely one of its family - a dirt path is not the grass that was
     * there. A different block put down meanwhile neither takes the record nor cancels it: it
     * simply waits, so pulling a block out, standing something else there, and swapping the
     * original back still works. Anything else falls through to forgetting whatever the position
     * held, which is what keeps a paved-over path from lingering.
     */
    public void placeBlock(World world, int x, int y, int z, Block block, int meta) {
        if (world == null || world.isRemote) return;

        String key = orphanKey(world.provider.getDimension(), x, y, z);
        Orphan orphan = orphans.get(key);
        if (orphan != null) {
            long age = System.currentTimeMillis() - orphan.brokenAtMs;
            if (age > graceMillis()) {
                orphans.remove(key);
            } else if (block != null && Block.getIdFromBlock(block) == orphan.blockId && meta == orphan.meta) {
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                ChunkErosionData data = ErosionStore.get()
                    .getOrCreateChunk(world.provider.getDimension(), chunkX, chunkZ);
                data.put(ErosionKey.packWorld(x, y, z), orphan.entry);
                data.markDirty();
                ErosionStore.get()
                    .markModified(world, chunkX, chunkZ);
                orphans.remove(key);
                if (orphan.entry.isVisible()) TrmtNetwork.sendDelta(world, x, y, z, orphan.entry.packState());
                return;
            }
            // A different block inside the window: the record keeps waiting for the original.
        }
        forget(world, x, y, z);
    }

    /** Drops the records whose block never came back. Cheap, and usually over an empty map. */
    public void sweepOrphans() {
        if (orphans.isEmpty()) return;
        long window = graceMillis();
        long now = System.currentTimeMillis();
        java.util.Iterator<java.util.Map.Entry<String, Orphan>> each = orphans.entrySet()
            .iterator();
        while (each.hasNext()) {
            if (now - each.next()
                .getValue().brokenAtMs > window) {
                each.remove();
            }
        }
    }

    /** Forgets every held record, for a disconnect or a world change. */
    public void clearOrphans() {
        orphans.clear();
    }

    /**
     * Forgets a position outright. Called when a block is broken, placed or otherwise
     * replaced, so a path does not linger on ground that no longer exists.
     */
    public void forget(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return;
        ChunkErosionData data = ErosionStore.get()
            .getChunk(world.provider.getDimension(), x >> 4, z >> 4);
        if (data == null) return;
        int key = ErosionKey.packWorld(x, y, z);
        ErosionEntry entry = data.get(key);
        if (entry == null) return;
        boolean wasVisible = entry.isVisible();
        data.remove(key);
        if (wasVisible) TrmtNetwork.sendDelta(world, x, y, z, ErosionState.NONE);
    }

    /**
     * Whether {@link #restoreOneStage} would put a gradation back here now, asked without changing
     * anything.
     *
     * <p>
     * A caller that pays for mending needs to know a square is worth pricing before it prices it,
     * so that a pinned square, an unworn one or one whose block is no longer a surface is never
     * charged for, counted as short or named in chat. Both methods answer from the same lookup, so
     * the question and the work cannot disagree about a square. Like the work, it asks the world
     * for the block, so a caller that may reach into an unloaded chunk should ask
     * {@code blockExists} first.
     */
    public boolean restorable(World world, int x, int y, int z) {
        return restoreTarget(world, x, y, z) != null;
    }

    /**
     * Steps a position back one stage, as bone meal does. Returns true if anything changed,
     * so the caller knows whether to consume the item.
     */
    public boolean restoreOneStage(World world, int x, int y, int z) {
        return restoreOneStage(world, x, y, z, true);
    }

    /**
     * As above, but able to hold its tongue.
     *
     * <p>
     * An area gesture touches thousands of positions, and a delta each would have the client
     * rebuild its whole overlay for every one of them - the cost {@code catchUpChunk} already
     * exists to avoid. The caller sends one packet per chunk when it is done.
     */
    public boolean restoreOneStage(World world, int x, int y, int z, boolean notifyClients) {
        RestoreTarget target = restoreTarget(world, x, y, z);
        if (target == null) return false;

        ChunkErosionData data = target.data;
        int key = target.key;
        ErosionEntry entry = target.entry;
        SurfaceFamily base = target.base;
        int index = target.index;
        int now = nowSeconds(world);
        if (index <= 0) {
            // Back to pristine, the way healing does it: the wear goes and the record stays for
            // whatever else it carries. Removing it outright took a reinforcement, a spawn ward and
            // a glow with it - things somebody paid for, deleted by tidying the first gradation with
            // bone meal or a tamper - and a record with nothing left in it is pruned here anyway. Both
            // draws below carry the block's resistance for the reason healing's do: a kept record never
            // draws again, and a resistant floor mended without it wore back at the unscaled price.
            entry.setAppearance(base, -1, drawThreshold(base, 0) * target.resistance);
            entry.setWear(0f);
            entry.setSink(0);
            entry.setLastTouchedSeconds(now);
            if (entry.isPrunable()) data.remove(key);
            if (notifyClients) TrmtNetwork.sendDelta(world, x, y, z, ErosionState.NONE);
        } else {
            SurfaceFamily appearance = target.appearance;
            entry.setAppearance(
                appearance,
                target.stage,
                drawThreshold(ErosionChain.pace(base, appearance), index - 1) * target.resistance);
            entry.setSink(ErosionChain.sinkAt(base, index - 1));
            entry.setLastTouchedSeconds(now);
            if (notifyClients) TrmtNetwork.sendDelta(world, x, y, z, entry.packState());
        }
        data.markDirty();
        ErosionStore.get()
            .markModified(world, x >> 4, z >> 4);
        return true;
    }

    /**
     * Everything {@link #restoreOneStage} needs to put a gradation back here, or null when it would
     * refuse.
     *
     * <p>
     * The one place the refusals live - a remote world, no record, nothing showing, a pin, ground
     * that is no longer a surface, a chain position with no appearance behind it - so that
     * {@link #restorable} can ask exactly what the work would, without doing it.
     */
    private RestoreTarget restoreTarget(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return null;
        ChunkErosionData data = ErosionStore.get()
            .getChunk(world.provider.getDimension(), x >> 4, z >> 4);
        if (data == null) return null;

        int key = ErosionKey.packWorld(x, y, z);
        ErosionEntry entry = data.get(key);
        if (entry == null || !entry.isVisible()) return null;
        // Pinned ground does not recover either. Refusing leaves the bone meal in hand, unless
        // something else in the same patch did repair - that decision is the caller's.
        if (entry.isFrozen()) return null;

        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        int meta = com.trmtgtnh.util.Worlds.metaAt(world, x, y, z);
        SurfaceFamily base = SurfaceRegistry.familyOf(block, meta);
        if (base == null) return null;

        int index = ErosionChain.indexOf(base, entry.getFamily(), entry.getStage(), entry.getSink());
        SurfaceFamily appearance = null;
        int stage = 0;
        if (index > 0) {
            appearance = ErosionChain.familyAt(base, index - 1);
            stage = ErosionChain.stageAt(base, index - 1);
            if (appearance == null) return null;
        }
        return new RestoreTarget(
            data,
            key,
            entry,
            base,
            index,
            appearance,
            stage,
            SurfaceRegistry.resistanceOf(block, meta));
    }

    /** What {@link #restoreTarget} found, handed over in one piece. */
    private static final class RestoreTarget {

        final ChunkErosionData data;
        final int key;
        final ErosionEntry entry;
        final SurfaceFamily base;
        final int index;

        /** What the position will show one gradation back; null when that is pristine ground. */
        final SurfaceFamily appearance;

        final int stage;

        /**
         * The block's resistance, read with the block rather than asked of the world a second time, so
         * the draws the work writes are priced against the same block the refusals were decided on.
         */
        final float resistance;

        RestoreTarget(ChunkErosionData data, int key, ErosionEntry entry, SurfaceFamily base, int index,
            SurfaceFamily appearance, int stage, float resistance) {
            this.data = data;
            this.key = key;
            this.entry = entry;
            this.base = base;
            this.index = index;
            this.appearance = appearance;
            this.stage = stage;
            this.resistance = resistance;
        }
    }

    /**
     * One square's worth of a mending patch.
     *
     * <p>
     * The engine hands over a position and how deep the patch reaches there, and the work puts back
     * what it can pay for. That split is the point: the patch's shape and depth are the engine's,
     * and what a square costs is not.
     */
    public interface PatchWork {

        /**
         * Puts back up to {@code steps} gradations at one position.
         *
         * @return how many went back
         */
        int apply(World world, int x, int y, int z, int steps);
    }

    /**
     * Mends a scattered patch around one position. Returns how many gradations were actually
     * put back, as the work reports them, so the caller knows whether anything happened and what
     * it was worth.
     *
     * <p>
     * Deliberately uneven. A fixed one-block, one-step repair meant filling in a track was a
     * chore of standing on each square in turn, and the result was as square as the effort. A
     * patch of random size, healed by a random amount, mends a path the way grass actually comes
     * back - in blotches, some of them further along than others - and a few handfuls tidy a
     * junction without anyone counting blocks.
     *
     * <p>
     * The reach is the caller's because the two things that use this disagree about it: a
     * handful of bone meal reaches as far as the config says, and a tamper as far as its tier
     * does. Everything else about the patch is the same, which is the whole reason this is one
     * method rather than two that drift apart.
     *
     * <p>
     * The engine decides the shape of the patch and how deep each square of it is mended; what a
     * square costs, and whether it can be paid for, is the work's business. The engine has never
     * looked at a player or an inventory, and this keeps it that way while letting every square of
     * a patch be paid for by what that square would take.
     */
    public int mendPatch(World world, int x, int y, int z, int radius, PatchWork work) {
        if (world == null || world.isRemote || work == null) return 0;
        if (radius < 0) radius = 0;
        int reach = radius <= 0 ? 0 : world.rand.nextInt(radius + 1);

        int repaired = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                // A square patch would be obvious. Dropping the corners as the patch grows keeps
                // the mended area roughly round, which is what a scattered handful looks like.
                if (reach > 1 && Math.abs(dx) == reach && Math.abs(dz) == reach) continue;

                int steps = 1 + world.rand.nextInt(Math.max(1, TrmtConfig.bonemealMaxSteps));
                // Rolled before the chunk is asked about, so a square at an unloaded edge draws the
                // same numbers it always did and the rest of the patch keeps the shape it had.
                // Asking the world for ground it does not have loaded would generate the chunk.
                if (!com.trmtgtnh.util.Worlds.loaded(world, x + dx, y, z + dz)) continue;
                repaired += work.apply(world, x + dx, y, z + dz, steps);
            }
        }
        return repaired;
    }

    /**
     * Sets a position straight to a given point on its wear chain.
     *
     * <p>
     * {@code /trmt showcase} and {@code /trmt demonstrate} use this to lay out every gradation
     * side by side for inspection. It writes an ordinary entry through the ordinary path, so
     * what it produces is exactly what traffic produces, and it heals and purges like anything
     * else.
     *
     * @param chainIndex how far along the base surface's chain to jump, clamped to its length
     * @return true when an entry was written
     */
    public boolean forceStage(World world, int x, int y, int z, SurfaceFamily base, int chainIndex) {
        return forceStage(world, x, y, z, base, chainIndex, false, true);
    }

    /**
     * As above, but says outright whether the position should be pinned and whether to tell
     * clients now.
     *
     * <p>
     * The pin is written rather than inherited. Left to carry over, a position frozen by one
     * demonstration would stay frozen under the next one that did not ask for it, and under
     * {@code /trmt showcase} too - producing ground that never wears and never heals, with a
     * tooltip line as the only clue.
     *
     * <p>
     * Passing {@code notifyClients} false leaves the caller to send one packet for the whole
     * chunk afterwards. Laying out thousands of positions one delta at a time makes the client
     * rebuild its overlay arrays once per position, which is the cost {@code catchUpChunk}
     * already exists to avoid.
     */
    public boolean forceStage(World world, int x, int y, int z, SurfaceFamily base, int chainIndex, boolean frozen,
        boolean notifyClients) {
        if (world == null || world.isRemote || base == null || chainIndex < 0) return false;
        int length = ErosionChain.length(base);
        if (length == 0) return false;
        if (chainIndex >= length) chainIndex = length - 1;

        SurfaceFamily appearance = ErosionChain.familyAt(base, chainIndex);
        int stage = ErosionChain.stageAt(base, chainIndex);
        if (appearance == null || stage < 0) return false;

        ChunkErosionData data = ErosionStore.get()
            .getOrCreateChunk(world.provider.getDimension(), x >> 4, z >> 4);
        int key = ErosionKey.packWorld(x, y, z);
        int now = nowSeconds(world);

        // The block's resistance is paid on every draw here as step pays it, so a square a showcase or
        // demonstration lays out and leaves unfrozen wears on at the price its settings give it.
        float resistance = SurfaceRegistry.resistanceOf(
            com.trmtgtnh.util.Worlds.blockAt(world, x, y, z),
            com.trmtgtnh.util.Worlds.metaAt(world, x, y, z));
        ErosionEntry entry = data.get(key);
        if (entry == null) {
            entry = new ErosionEntry(base, drawThreshold(base, 0) * resistance, now);
            data.put(key, entry);
        }
        entry.setAppearance(
            appearance,
            stage,
            drawThreshold(ErosionChain.pace(base, appearance), chainIndex) * resistance);
        entry.setSink(ErosionChain.sinkAt(base, chainIndex));
        entry.setLastTouchedSeconds(now);
        entry.setFrozen(frozen);
        data.markDirty();
        ErosionStore.get()
            .markModified(world, x >> 4, z >> 4);

        if (notifyClients) TrmtNetwork.sendDelta(world, x, y, z, entry.packState());
        return true;
    }

    /**
     * Walks a cube and hands every position in it to one operation, then tells the clients once
     * per chunk rather than once per position.
     *
     * <p>
     * The cube is walked in full but nothing is generated: a position in a chunk that is not
     * loaded is skipped exactly as the wear path skips it, because a gesture should not decide
     * to load half the map.
     *
     * @param budget the most positions this gesture may change. Every caller in the mod now passes
     *               {@link Integer#MAX_VALUE} and pays for each square inside its work as the sweep
     *               reaches it, so this is only a ceiling, for a caller that wants one
     * @return how many positions actually changed
     */
    public int sweepArea(World world, int x, int y, int z, int reach, AreaWork work, int budget) {
        return sweepArea(world, x, y, z, reach, reach, work, budget);
    }

    /**
     * The same sweep, over a box that need not be a cube.
     *
     * <p>
     * A separate vertical reach, because the golem is the one caller that wants a flat one. It keeps a
     * surface rather than a volume, and every column it touches finds its own exposed ground - so
     * sweeping a cube would only ask again about columns it had already answered for, once per layer.
     */
    public int sweepArea(World world, int x, int y, int z, int reach, int reachY, AreaWork work, int budget) {
        if (world == null || world.isRemote || work == null || reach < 0 || reachY < 0 || budget <= 0) return 0;

        int changed = 0;
        Set<Long> touched = new HashSet<Long>();
        for (int dx = -reach; dx <= reach && changed < budget; dx++) {
            for (int dy = -reachY; dy <= reachY && changed < budget; dy++) {
                for (int dz = -reach; dz <= reach && changed < budget; dz++) {
                    int at = x + dx;
                    int up = y + dy;
                    int over = z + dz;
                    if (up < 0 || up > 255) continue;
                    if (!com.trmtgtnh.util.Worlds.loaded(world, at, up, over)) continue;
                    if (!work.apply(world, at, up, over)) continue;
                    changed++;
                    touched.add(Long.valueOf(((long) (at >> 4) << 32) | ((over >> 4) & 0xFFFFFFFFL)));
                }
            }
        }

        for (Long key : touched) {
            int chunkX = (int) (key.longValue() >> 32);
            int chunkZ = (int) key.longValue();
            ChunkErosionData data = ErosionStore.get()
                .getChunk(world.provider.getDimension(), chunkX, chunkZ);
            // Willing to send an empty one, because an area mend is the one gesture that can
            // take the last of a chunk's wear away, and silence would leave the client holding
            // ghosts with nothing left to tell it otherwise.
            if (data != null) TrmtNetwork.sendChunkToWatchers(world, chunkX, chunkZ, data, true);
        }
        return changed;
    }

    /** One position's worth of an area gesture. Returns true when it changed something. */
    public interface AreaWork {

        boolean apply(World world, int x, int y, int z);
    }

    /**
     * Pins or releases one position, and returns true when that changed anything.
     *
     * <p>
     * The one way a pin should ever be lifted. A frozen position's inactivity clock stops
     * advancing, because every path that would move it is skipped - so a demo pinned for a
     * fortnight and then released hands healing a fortnight of arrears and collapses to bare
     * ground in a single sweep. Restamping the clock on release is what makes a pin hand back
     * the position it was given.
     */
    public boolean setFrozen(World world, int x, int y, int z, boolean frozen) {
        return setFrozen(world, x, y, z, frozen, true);
    }

    /** As above, without telling the clients; an area gesture reports per chunk instead. */
    public boolean setFrozen(World world, int x, int y, int z, boolean frozen, boolean notifyClients) {
        if (world == null || world.isRemote) return false;
        ChunkErosionData data = ErosionStore.get()
            .getChunk(world.provider.getDimension(), x >> 4, z >> 4);
        if (data == null) return false;

        int key = ErosionKey.packWorld(x, y, z);
        ErosionEntry entry = data.get(key);
        if (entry == null || !entry.isVisible()) return false;
        if (entry.isFrozen() == frozen) return false;

        entry.setFrozen(frozen);
        if (!frozen) entry.setLastTouchedSeconds(nowSeconds(world));
        data.markDirty();
        ErosionStore.get()
            .markModified(world, x >> 4, z >> 4);
        if (notifyClients) TrmtNetwork.sendDelta(world, x, y, z, entry.packState());
        return true;
    }

    /** How hard this particular mover wears the ground, or 0 when it should not be tracked. */
    public static float multiplierFor(EntityLivingBase entity) {
        if (entity instanceof EntityPlayerMP) {
            return TrmtConfig.erodeFromPlayers ? 1.0f : 0f;
        }
        if (entity instanceof EntityPlayer) return 0f;
        if (entity instanceof EntityLiving && TrmtConfig.erodeFromLeashedMobs && ((EntityLiving) entity).getLeashed()) {
            // Being led counts whatever the mob is: somebody is walking it somewhere.
            return TrmtConfig.multiplierLeashed;
        }
        // Otherwise only the mobs named in the config, which by default is villagers. Looking
        // the name up costs one map lookup, and this already only runs on a sampled tick.
        return TrmtConfig.mobMultiplier(net.minecraft.entity.EntityList.getEntityString(entity));
    }
}
