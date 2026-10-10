package com.trmtgtnh.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.erosion.MendLedger;
import com.trmtgtnh.server.HealingCost;
import com.trmtgtnh.server.MendPurse;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.util.RefusedFetches;

/**
 * What a Golem of Ways actually does on its round.
 *
 * <p>
 * It works what is within arm's length and walks to the rest. That is two searches rather than one:
 * a small box around its feet, looked at in full every stroke because it is only a few dozen
 * squares; and, when that box holds nothing to do, a cursor that creeps over the whole radius a
 * few dozen squares at a time looking for somewhere worth walking to. A golem told to keep
 * sixty-four blocks of ground therefore costs the same per tick as one keeping four, and simply
 * takes longer to come round again.
 *
 * <p>
 * One square per stroke with a plain tamper, because a plain tamper is one square a click. With a
 * chunk or magic tamper it works that tool's whole area from the square it found, one layer deep.
 *
 * <p>
 * The economy is the point of it. With no tool it does nothing at all. With a tool but nothing to
 * mend with it can only wear ground down, never build it back - so a golem left unfed slowly turns
 * its whole round into a path, which is a fair thing for a neglected keeper to do. With both it
 * works in either direction, spending a tool's durability every stroke and, for its mending, blocks
 * the ground will take, at the price {@link #strokeLedger} sets.
 */
public final class GolemWork {

    /** How many positions the wandering cursor looks at before giving up for this stroke. */
    private static final int LOOKS_PER_STROKE = 48;

    /** Nothing to do here. */
    private static final int NOTHING = 0;

    /** This square is fresher than it was told to be. */
    private static final int WEAR = 1;

    /** This square is more worn than it was told to be. */
    private static final int MEND = 2;

    /**
     * Whether a golem's mending is priced from the chunk tamper's rate, or at one flat figure for
     * every gradation.
     *
     * <p>
     * True: golem.blockCost blocks for every chunkTamperGradationsPerBlock gradations of one kind of
     * ground, rounded up once a stroke. False: golem.blockCost blocks for every gradation whatever
     * tool it holds, which is exactly the price up to 0.9.205. A plain tamper mends one gradation a
     * stroke, so it pays the same either way. Only a golem carrying a chunk tamper or the Wayfarer's
     * feels the difference, and under the rate it pays somewhere between the flat price and a quarter
     * of it at the default four gradations a block, depending on how much of one kind of ground a
     * stroke finds.
     *
     * <p>
     * A constant so that the choice can be undone in one line. The guide's price page, the blockCost
     * and chunkTamperGradationsPerBlock config comments and the golem quest read it and follow it;
     * README does not, and lists what changes, so it has to be put right by hand.
     * Being a compile-time constant it is copied into every class that reads it, which lets the guide
     * ask it on a client without loading this class.
     */
    public static final boolean PRICED_FROM_THE_CHUNK_TAMPER = true;

    private GolemWork() {}

    /**
     * The ledger one stroke is priced by.
     *
     * <p>
     * A stroke is a golem's gesture. Credit bought during it lasts until it ends and is never carried
     * into the next, so a stroke that finds only a gradation or two of some ground still pays a whole
     * purchase for it. What a purchase costs is the golem's own figure, from
     * {@link EntityGolemOfWays#mendPrice}; how many gradations it pays for is the chunk tamper's
     * figure, or one when {@link #PRICED_FROM_THE_CHUNK_TAMPER} is off.
     *
     * <p>
     * Never free. The Wayfarer's Tamper, a server that has made the chunk tamper free, and
     * tamperMendCost all leave a golem's price exactly where it was, because a keeper that cost
     * nothing to run would never need looking after.
     */
    static MendLedger strokeLedger(EntityGolemOfWays golem) {
        return MendLedger
            .rate(golem.mendPrice(), PRICED_FROM_THE_CHUNK_TAMPER ? TrmtConfig.chunkTamperGradationsPerBlock : 1);
    }

    /** One stroke of work: find a square that is not where it should be, and move it one step. */
    public static void workOnce(EntityGolemOfWays golem) {
        World world = golem.worldObj;
        if (world == null || world.isRemote) return;
        if (!golem.hasOrders()) return;

        // No tool, no work of any kind. This is the first thing checked because it is the thing
        // that most often explains a golem standing still.
        int toolSlot = golem.findTool();
        // Out of tool is where a golem's day used to end. A settled one asks the stores around it
        // first, and takeStock inside the fetch is what makes the slot it just filled visible here.
        if (toolSlot < 0 && GolemStores.reaches(golem) && GolemStores.fetchTool(golem)) {
            toolSlot = golem.findTool();
        }
        if (toolSlot < 0) return;

        // Before the wear stroke, and taking the stroke when it happens. A golem that has been fed
        // lays what it was fed first, so a player who throws down a stack sees it being used rather
        // than watching the round go by twice before anything comes of it.
        if (GolemMasonry.layOne(golem)) return;

        // One purse for the whole stroke, shared by every square the box below looks at. That
        // cannot change a price, because a purchase always ends the stroke: it follows a gradation
        // that landed, which makes the square count as work, and the stroke returns straight after.
        // What sharing buys is the quotes. The eighty-one squares a stroke may look at ask what pays
        // for the same few kinds of ground, and each kind is worked out once rather than once a square.
        final StrokePurse purse = new StrokePurse(golem);

        int[] anchor = golem.anchor();
        int radius = golem.workRadius();
        int anchorY = anchor[1];

        // ---- what is within reach -----------------------------------------------------
        int reach = Math.max(1, TrmtConfig.golemWorkReach);
        int atX = MathHelper.floor_double(golem.posX);
        int atZ = MathHelper.floor_double(golem.posZ);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = atX + dx;
                int z = atZ + dz;
                if (Math.abs(x - anchor[0]) > radius || Math.abs(z - anchor[2]) > radius) continue;
                if (directionAt(golem, world, x, anchorY, z) == NOTHING) continue;
                if (!act(golem, world, x, anchorY, z, toolSlot, purse)) {
                    // Wanted doing and would not move, which for a golem means it could not pay.
                    // Struck off rather than left in the list: one square nobody can afford would
                    // otherwise keep the count above zero for ever, which for a farming golem means
                    // a round that never finishes and a field that stops breathing.
                    // The field rather than the watched copy: the watched one is published for a
                    // tooltip once a tick and is therefore a tick behind, so it would answer for
                    // the square before this one.
                    // Striking it off does not stop it being tried: the box reads the ground, not the
                    // list, so the same square is found again next stroke. What keeps that cheap is
                    // elsewhere. A kind of ground nothing in reach pays for is asked of the stores
                    // once, until they are looked at again, the anchor moves or something is put away -
                    // or, refused for want of room, until what the golem carries changes - and the
                    // golem's own storage is counted once a stroke for each kind rather than once for
                    // every square of it.
                    if (golem.shortOfBlocks()) {
                        golem.workList()
                            .spend(
                                golem.workList()
                                    .locate(x, z, 0));
                    }
                    continue;
                }
                golem.clearWorkTarget();
                golem.noteMoved();
                // Finished squares come off the list here rather than waiting for a sweep to
                // notice, which is what lets the list wear down instead of going stale.
                if (directionAt(golem, world, x, anchorY, z) == NOTHING) {
                    golem.workList()
                        .spend(
                            golem.workList()
                                .locate(x, z, 0));
                }
                return;
            }
        }

        // ---- and where to go next -------------------------------------------------------
        //
        // Only asked when there is nothing left to do here, so a golem standing on work never
        // stops to plan a journey. What it consults is a list made once and worn down as the work
        // is done, because the making of it is the expensive part: sixteen thousand columns for a
        // golem keeping sixty-four blocks, which is not a question to ask every stroke.
        GolemTargets known = golem.workList();
        long now = world.getTotalWorldTime();
        if (known.stale(now, anchor[0], anchorY, anchor[2], TrmtConfig.golemTargetMemory * 20) && known.maySweep(now)) {
            int unseen = sweep(golem, world, anchor, radius, known, now);
            // A round that has stopped moving is a round finished, and for a farming golem that is
            // the moment to turn. Progress rather than an empty list, because the list is rebuilt
            // from scratch by the very sweep above: a square the golem has tried and cannot afford
            // is counted again on every pass, so a count of nought is only ever reached by a round
            // that finished perfectly - and a round that ran out of dirt would hold its breath for
            // ever. Asking whether anything moved answers both endings at once.
            //
            // Not while part of the ground is out of memory, though. An unloaded column looks
            // exactly like a finished one from here, and a golem at the edge of what is loaded
            // would turn every ten seconds and get nowhere.
            boolean moved = golem.movedSinceLook();
            if (golem.farms() && !moved && unseen == 0) {
                golem.turnFarm();
                sweep(golem, world, anchor, radius, known, now);
            }
            known.holdOff(now, TrmtConfig.golemScanCooldown * 20);
        }

        // A square remembered is not a square still wanting doing - somebody may have paved it,
        // dug it up or mended it by hand since the sweep. Anything that has stopped needing work
        // is struck off on the way past rather than by another sweep.
        for (int tries = 0; tries < 8; tries++) {
            int square = known.pick(golem.getRNG(), TrmtConfig.golemTargetLayers);
            if (square == 0) break;
            int x = known.xOf(square);
            int z = known.zOf(square);
            int y = surfaceNear(world, x, anchorY, z);
            if (y >= 0 && directionAtY(golem, world, x, y, z) != NOTHING) {
                golem.setWorkTarget(x, y, z);
                return;
            }
            known.spend(square);
        }
        golem.clearWorkTarget();
    }

    /**
     * Looks at every square of the golem's ground and writes down the ones that want doing.
     *
     * <p>
     * The whole radius in one pass, which for the widest golem is sixteen thousand columns. That
     * is affordable exactly once in a while and not otherwise, which is what the memory and the
     * cooldown either side of this call are for: the list stands for a quarter of an hour by
     * default, or until the work in it runs out, and a fresh sweep cannot follow a failed one
     * immediately.
     *
     * <p>
     * Distance from home goes in with each square, because that is what the list is sorted by and
     * working it out here costs one square root where working it out later would cost one per
     * comparison.
     */
    private static int sweep(EntityGolemOfWays golem, World world, int[] anchor, int radius, GolemTargets known,
        long now) {
        int span = radius * 2 + 1;
        int[] found = new int[span * span];
        int count = 0;
        int unseen = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = anchor[0] + dx;
                int z = anchor[2] + dz;
                // Counted rather than skipped, because a column nobody has loaded looks exactly
                // like a column with nothing wrong with it, and one of those two means the sweep
                // does not know what it is talking about.
                if (!world.blockExists(x, anchor[1], z)) {
                    unseen++;
                    continue;
                }
                int y = surfaceNear(world, x, anchor[1], z);
                if (y < 0) continue;
                if (directionAtY(golem, world, x, y, z) == NOTHING) continue;
                found[count++] = GolemTargets
                    .pack((int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz)), dx, dz, y);
            }
        }
        known.accept(found, count, now, anchor[0], anchor[1], anchor[2]);
        return unseen;
    }

    /**
     * Which way this square wants to move, without moving it.
     *
     * <p>
     * Kept apart from the doing so that looking is cheap. A golem sweeps eighty or so squares
     * around its feet every stroke and works at most one of them, so the question "does this need
     * anything" is asked about a hundred times for each time the answer is acted on - and with a
     * chunk tamper, acting means sweeping two hundred more.
     */
    private static int directionAt(EntityGolemOfWays golem, World world, int x, int anchorY, int z) {
        int y = surfaceNear(world, x, anchorY, z);
        return y < 0 ? NOTHING : directionAtY(golem, world, x, y, z);
    }

    /** As above, told the height, so a caller that already found one does not look twice. */
    private static int directionAtY(EntityGolemOfWays golem, World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        SurfaceFamily family = SurfaceRegistry.familyOf(block, meta);

        int wanted = wantedIndex(golem, world, x, y, z, block, meta, family);
        if (wanted < 0) return NOTHING;
        int current = currentIndex(world, x, y, z, family);
        if (current == wanted) return NOTHING;
        return current < wanted ? WEAR : MEND;
    }

    /**
     * Where this square should be sitting, or -1 when it is not the golem's to move.
     *
     * <p>
     * The one place the target arithmetic lives, and it has to be one place. The looking half and
     * the doing half must refuse exactly the same squares: a farming golem turns its round when a
     * sweep finds nothing, so a square one half will not work and the other still counts is a
     * round that never finishes and a field that quietly stops breathing, with nothing anywhere
     * saying why.
     *
     * <p>
     * Three refusals beyond "no order here". A family with no run cannot be moved along one. A run
     * capped below its full length stops where the cap does, which is where traffic and the hand
     * tool already stop. And a pinned square is pinned against the golem too - it was not before,
     * because the write it uses sets the pin rather than reading it, and a golem returning to the
     * same pinned square every round for ever would make a pin worth nothing near one.
     *
     * <p>
     * For a farming golem the order is the middle of a band rather than a place to stop, and the
     * answer is whichever edge the round is currently driving toward. The middle being the order
     * is what makes the part safe to fit and safe to pull: an order means the same thing either
     * way, on average, which matters most on an unstable golem that gains and loses this every
     * minute.
     */
    private static int wantedIndex(EntityGolemOfWays golem, World world, int x, int y, int z, Block block, int meta,
        SurfaceFamily family) {
        if (family == null || !family.staged) return -1;
        // The ground it keeps, asked here rather than at one of the two call sites. An area tamper
        // is centred on a square inside the round and reaches past it, so without this a golem was
        // working ground it was never told about - which is the one thing the radius is for.
        if (!golem.withinRound(x, z)) return -1;

        int target = golem.targetForBlock(block, meta, family);
        if (target < 0) return -1;
        int length = ErosionChain.length(family);
        if (length <= 1) return -1;

        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        if (entry != null && entry.isFrozen()) return -1;

        int top = Math.max(0, ErosionChain.cappedLength(family) - 1);
        int middle = Math.min(top, Math.round(target / 100f * (length - 1)));
        if (!golem.farms()) return middle;

        // Narrowed rather than clipped when the order sits near an end. Clipping one edge and
        // leaving the other moves the middle, and the middle being the order is the whole reason
        // fitting or pulling this part does not change what an order means.
        int half = Math.max(1, TrmtConfig.golemGreenBand);
        half = Math.min(half, Math.min(middle, top - middle));
        int low = middle - half;
        int high = middle + half;
        // A band with nowhere to go is the order it was made from, which is the same answer this
        // mod gives everywhere else a range collapses.
        if (high <= low) return middle;
        return golem.farmWearing() ? high : low;
    }

    /**
     * Moves one square - or one flat square of them - one step toward what it should be.
     *
     * <p>
     * What the tool in its hand can do is exactly what it could do in yours. A plain tamper is one
     * square a stroke, because a plain tamper is one square a click. A chunk or magic tamper works
     * the area it is set to, at the reach it is set to, and costs the tool one use for the gesture
     * rather than one per square - which is the bargain that tool already offers a player. The one
     * thing added is a ceiling: the golem's sweep is a single layer however deep the tool is set,
     * because it keeps a surface and not a volume, and a golem quietly hollowing out a cube of
     * ground it was never told about is not a keeper of roads.
     *
     * <p>
     * Whichever half it turns out to be says so on the way out, and that is what raises the tool
     * into the golem's hand. Only a stroke that actually changed something counts as work: a golem
     * walking over ground that is already right has nothing to hold up and keeps its hands where
     * they were.
     *
     * <p>
     * The blocks a mend spends are paid for by the stroke's ledger, each kind of ground on its own.
     */
    private static boolean act(final EntityGolemOfWays golem, World world, int x, final int anchorY, int z,
        int toolSlot, final StrokePurse purse) {
        ItemStack tool = golem.inventory()[toolSlot];
        int reach = areaReach(tool);
        int did;

        if (reach <= 0) {
            did = stepColumn(golem, world, x, anchorY, z, 1, true, purse);
        } else {
            final int steps = Math.max(1, com.trmtgtnh.item.ItemChunkTamper.stepsOf(tool));
            // The first square that moves says what the stroke was. A sweep can wear one square
            // and mend another, and the golem can only hold its tool one way at a time.
            final int[] first = { NOTHING };
            int changed = ErosionEngine.get()
                .sweepArea(world, x, surfaceNear(world, x, anchorY, z), z, reach, 0, new ErosionEngine.AreaWork() {

                    @Override
                    public boolean apply(World at, int px, int py, int pz) {
                        int kind = stepColumn(golem, at, px, anchorY, pz, steps, false, purse);
                        if (kind != NOTHING && first[0] == NOTHING) first[0] = kind;
                        return kind != NOTHING;
                    }
                }, Integer.MAX_VALUE);
            did = changed > 0 ? first[0] : NOTHING;
        }
        if (did == NOTHING) return false;

        // One use for the stroke, whether it moved one square or a hundred - the same accounting
        // the chunk tamper does for a player, who pays for the gesture and not for its footprint.
        // The blocks a mend spends are not the tool's business: the stroke's purse took them as each
        // gradation landed, by the kind of ground.
        golem.wearTool(toolSlot);
        golem.markBusy(
            did == MEND ? GolemCombat.BUSY_MEND : GolemCombat.BUSY_WEAR,
            golem.workPeriod() + GolemCombat.WORK_HOLD_SLACK);
        return true;
    }

    /** How far a tool lets the golem work around itself, which is nothing at all for a plain one. */
    private static int areaReach(ItemStack tool) {
        if (tool == null || !(tool.getItem() instanceof com.trmtgtnh.item.ItemChunkTamper)) return 0;
        return com.trmtgtnh.item.ItemChunkTamper.reachOf(tool);
    }

    /**
     * One column, moved up to {@code steps} toward whatever that column was told to be.
     *
     * <p>
     * Every column answers for itself. Inside one sweep the ground can change family, and each
     * kind carries its own order, so a square of grass and gravel is worked to two different
     * targets in the same stroke rather than to the one the middle happened to have.
     *
     * <p>
     * It never steps past the target. The tool's own step setting says how fast it may move; the
     * order says where it stops, and the order wins - which is the difference between a golem
     * using a chunk tamper and a chunk tamper using a golem.
     *
     * @param notify whether each square announces itself to the clients watching. True for a single
     *               square, and false for the area gesture, which sends one packet for the whole
     *               chunk when it is done - so a golem with a chunk tamper was telling everybody
     *               about eighty squares one at a time and then telling them about all eighty again
     * @param purse  the stroke's purse, which prices every gradation this mends and takes its blocks
     */
    private static int stepColumn(EntityGolemOfWays golem, World world, int x, int anchorY, int z, int steps,
        boolean notify, StrokePurse purse) {
        int y = surfaceNear(world, x, anchorY, z);
        if (y < 0) return NOTHING;

        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        SurfaceFamily family = SurfaceRegistry.familyOf(block, meta);

        // Read once, before the loop. A tool that moves several steps must not have the ground
        // move under it half way through, and reading it again per step is how that happens.
        int wanted = wantedIndex(golem, world, x, y, z, block, meta, family);
        if (wanted < 0) return NOTHING;
        int current = currentIndex(world, x, y, z, family);
        int sankFrom = sinkAt(world, x, y, z);
        int did = NOTHING;
        for (int step = 0; step < steps && current != wanted; step++) {
            if (current < wanted) {
                // Wearing asks the dimension list and mending does not, as for the tamper: a golem in a dimension
                // the list rules out still keeps its road, and wears nothing in (0.9.222, spec SD47).
                if (!TrmtConfig.dimensionAllowed(world.provider.dimensionId)) break;
                if (!ErosionEngine.get()
                    .forceStage(world, x, y, z, family, current + 1, false, notify)) break;
                current++;
                did = WEAR;
            } else {
                // Mending needs a block the ground will take. Without one the golem simply cannot,
                // which is what makes an unfed one a wearer rather than a keeper - and it says so
                // rather than standing there looking like it has decided not to bother.
                if (purse.halted) break;
                GolemQuote quote = purse.quote(block, meta);
                if (quote == null) {
                    golem.noteShortOfBlocks(true);
                    break;
                }
                int owed = purse.ledger.owedBefore(family, quote);
                if (owed > 0 && !quote.has(owed)) {
                    purse.ledger.shortOf(family, did != MEND);
                    golem.noteShortOfBlocks(true);
                    break;
                }
                // The gradation goes back before its price is taken, so ground the engine refuses
                // costs nothing. The price used to be taken first, and a refusal lost the blocks.
                if (!ErosionEngine.get()
                    .forceStage(world, x, y, z, family, current - 1, false, notify)) break;
                if (owed > 0) {
                    List<Object> kinds = quote.take(owed);
                    if (kinds == null || kinds.isEmpty()) {
                        unpaid(purse, family, x, y, z);
                        current--;
                        did = MEND;
                        break;
                    }
                    purse.ledger.bought(family, kinds);
                    // Never let the ledger throw inside a ticking entity: that is a server crash on
                    // every load of the chunk. A purchase this ground will not draw on is recorded
                    // as an unpaid gradation instead, and the stroke mends nothing more.
                    if (purse.ledger.owedBefore(family, quote) > 0) {
                        unpaid(purse, family, x, y, z);
                        current--;
                        did = MEND;
                        break;
                    }
                }
                purse.ledger.mended(family, quote);
                current--;
                did = MEND;
            }
        }
        // Once, after the strokes, and only for wearing. A golem putting ground back is not walking
        // through anything, and it should not be able to mend a road and clear it in one stroke.
        if (did == WEAR) {
            // The cover breaks once however far the square fell - it is one gesture, and its own
            // comment in the erosion engine asks for exactly that. What comes loose is counted per
            // level, because a tool set to sixteen steps takes two levels off in one stroke and
            // half a stroke's worth of earth is not what fell out of it.
            int levels = sinkAt(world, x, y, z) - sankFrom;
            com.trmtgtnh.erosion.GroundCover.breakAbove(world, x, y, z, true, levels > 0);
            for (int level = 0; level < levels; level++) {
                shed(golem, world, x, y, z, family);
            }
        }
        return did;
    }

    /**
     * Records a gradation that landed although nothing was taken to pay for it, and stops the
     * stroke's mending.
     *
     * <p>
     * It cannot happen on the server thread, where nothing touches the golem's storage between the
     * count and the take, and where every kind taken is one this ground accepts. If it does, the
     * gradation is counted as unpaid so that nothing earns from it, the purse mends nothing more this
     * stroke, and one warning a run says where. The golem is not marked short of blocks: what failed
     * was the take, not the stock, and a tooltip saying it needs the right block would send somebody
     * off to fetch something it already has.
     */
    private static void unpaid(StrokePurse purse, SurfaceFamily family, int x, int y, int z) {
        purse.ledger.mendedUnpaid(family);
        purse.halted = true;
        if (!StrokePurse.warnedUnpaid) {
            StrokePurse.warnedUnpaid = true;
            Trmt.LOG.warn(
                "A golem's mend at {},{},{} landed without its payment being taken; that stroke mended nothing more",
                Integer.valueOf(x),
                Integer.valueOf(y),
                Integer.valueOf(z));
        }
    }

    /**
     * What one stroke has bought, and what each kind of ground it meets would take, for as long as
     * the stroke lasts.
     *
     * <p>
     * One a stroke rather than one a square, for the reason {@code workOnce} gives where it makes
     * one: a purchase always ends the stroke, so sharing changes no price, and the quotes are worth
     * sharing. They are kept per kind of worn block - the same key the memory of refused fetches
     * uses - because what pays for a square depends on its block and metadata and on nothing else
     * but the config, and a config reload cannot land in the middle of a stroke.
     */
    private static final class StrokePurse {

        /** Whether the one warning about a take that failed has been written this run. */
        static boolean warnedUnpaid;

        final EntityGolemOfWays golem;

        final MendLedger ledger;

        /** Each kind of worn block met this stroke, with its quote, or null where nothing could pay. */
        final Map<Long, GolemQuote> quotes = new HashMap<Long, GolemQuote>();

        /** Set once a take has failed, after which the stroke mends nothing more. */
        boolean halted;

        StrokePurse(EntityGolemOfWays golem) {
            this.golem = golem;
            this.ledger = strokeLedger(golem);
        }

        /**
         * The price of this kind of ground, worked out once a stroke, or null when nothing could ever
         * pay for it - a block with no item form and nothing listed in its place, say.
         */
        GolemQuote quote(Block block, int meta) {
            Long key = Long.valueOf(RefusedFetches.kindOf(Block.getIdFromBlock(block), meta));
            if (quotes.containsKey(key)) return quotes.get(key);
            HealingCost cost = HealingCost.forBlock(block, meta);
            GolemQuote quote = cost == null ? null : new GolemQuote(golem, cost, key.longValue());
            quotes.put(key, quote);
            return quote;
        }
    }

    /**
     * What pays for one kind of ground, looked for in the golem's own storage and then, with Settled
     * Ways, in the stores around its anchor.
     *
     * <p>
     * The rule is bone meal's and the chunk tamper's, asked of {@link HealingCost}. The storage is
     * counted first, and the stores are asked only when that falls short. Asking the stores goes back
     * as many times as {@code GolemStores.FETCHES} allows, because one fetch brings back whatever the
     * first container holding any of it held, which can be a single block; but a kind the stores
     * were already found unable to pay for is not asked again until they, or what the golem carries,
     * have changed.
     *
     * <p>
     * It also remembers being short: how many it was short of, and at what count of changes to the
     * golem's storage. A chunk tamper's stroke can ask this for every column of a fifteen-square
     * area, and while nothing the golem carries has changed, going through its sixty-four slots again
     * for each column would only find the same answer.
     */
    private static final class GolemQuote implements MendPurse.Quote {

        private final EntityGolemOfWays golem;

        private final HealingCost cost;

        /** The kind of worn block this prices, as the memory of refused fetches knows it. */
        private final long blockKind;

        /** Whether each kind of item met so far would pay here, so a pool is judged once, not per gradation. */
        private final Map<Object, Boolean> answers = new HashMap<Object, Boolean>(4);

        /** The golem's count of stock changes when this was last found short; the lowest int before then. */
        private int shortAtStock = Integer.MIN_VALUE;

        /** The smallest count it was found short of at that count of changes. */
        private int shortOf;

        GolemQuote(EntityGolemOfWays golem, HealingCost cost, long blockKind) {
            this.golem = golem;
            this.cost = cost;
            this.blockKind = blockKind;
        }

        @Override
        public boolean accepts(Object kind) {
            Boolean known = answers.get(kind);
            if (known == null) {
                ItemStack stack = MendPurse.stackOf(kind);
                known = Boolean.valueOf(stack != null && cost.paidBy(stack));
                answers.put(kind, known);
            }
            return known.booleanValue();
        }

        @Override
        public boolean has(int count) {
            boolean knownShort = golem.stockChanges() == shortAtStock && count >= shortOf;
            if (!knownShort && golem.countPaying(cost, count) >= count) return true;
            if (GolemStores.reaches(golem) && !GolemStores.refusedBefore(golem, blockKind)) {
                for (int tries = 0; tries < GolemStores.FETCHES; tries++) {
                    if (!GolemStores.fetchPayment(golem, cost, blockKind)) break;
                    if (golem.countPaying(cost, count) >= count) return true;
                }
            }
            int stock = golem.stockChanges();
            shortOf = stock == shortAtStock ? Math.min(shortOf, count) : count;
            shortAtStock = stock;
            return false;
        }

        @Override
        public List<Object> take(int count) {
            return golem.takePayment(cost, count);
        }

        @Override
        public String wanted() {
            // Nobody is told what a golem wanted by name; its tooltip says it needs the right block.
            return null;
        }
    }

    /**
     * What a square sheds when the golem takes it down a level.
     *
     * <p>
     * The guide has always said a golem's own working turns up seeds, flint and snowballs and that
     * it keeps them in its store, and it never did: the write the golem uses is a bare assignment
     * that skips everything the traffic path does on the way past, the drop roll included. So a
     * golem has been wearing ground down and producing nothing since it was built.
     *
     * <p>
     * Rolled per physical level rather than per gradation, which is the unit a player can see and
     * the same one the ground cover already breaks on. A gradation is a shade; a level is the
     * ground actually dropping, and a handful of earth coming loose when it does is the reading
     * anybody would expect.
     *
     * <p>
     * Into the store rather than onto the floor. That is what the guide says, and it is also the
     * only version that survives a farm: an area tamper takes a level off eighty squares at a time,
     * and eighty items a stroke on the ground is a server's afternoon. Full storage is then the one
     * visible failure, which is the one a player can do something about.
     */
    private static void shed(EntityGolemOfWays golem, World world, int x, int y, int z, SurfaceFamily family) {
        net.minecraft.item.ItemStack drop = com.trmtgtnh.erosion.WearDrops
            .rollFor(world, x, y, z, family, TrmtConfig.golemWearDropChance);
        if (drop == null) return;
        if (!golem.store(drop)) com.trmtgtnh.erosion.WearDrops.spawn(world, x, y, z, drop);
    }

    /** How far this position has physically sunk, or 0 when nothing is recorded yet. */
    private static int sinkAt(World world, int x, int y, int z) {
        com.trmtgtnh.erosion.ErosionEntry entry = com.trmtgtnh.erosion.ErosionStore.get()
            .getEntry(world, x, y, z);
        return entry == null ? 0 : entry.getSink();
    }

    /** Where this position sits on its family's whole run, or 0 when nothing is recorded yet. */
    private static int currentIndex(World world, int x, int y, int z, SurfaceFamily family) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        if (entry == null) return 0;
        int index = ErosionChain.indexOf(family, entry.getFamily(), entry.getStage(), entry.getSink());
        return index < 0 ? 0 : index;
    }

    /**
     * The exposed ground in this column, near the height the golem was set at.
     *
     * <p>
     * Package-visible rather than private because {@link GolemMasonry} needs the same answer and
     * had its own version of it. Two answers to one question is how they came apart: the copy
     * searched five rows where this searches nine, gave up on a whole column at the first thing
     * it met rather than stepping past it, and asked for bare air above where this asks only
     * that nothing stands on the face - so a golem would happily mend a square it then refused
     * to reinforce, and under a roof it refused every square it had.
     */
    static int surfaceNear(World world, int x, int centreY, int z) {
        for (int offset = 0; offset <= 4; offset++) {
            for (int sign = 0; sign < (offset == 0 ? 1 : 2); sign++) {
                int y = centreY + (sign == 0 ? offset : -offset);
                if (y < 1 || y > 254) continue;
                if (!world.blockExists(x, y, z)) continue;
                Block block = world.getBlock(x, y, z);
                if (block == null || block.isAir(world, x, y, z)) continue;
                if (coveredOver(world, x, y, z)) continue;
                return y;
            }
        }
        return -1;
    }

    /**
     * Whether something resting on this square hides the wear that would go into it.
     *
     * <p>
     * Wear is drawn on the top face, so anything standing on that face makes the golem's whole job
     * invisible - and a golem spending its tool and its stock on ground nobody can see is spending
     * them on nothing. The rest of the mod asks only whether the block above is an opaque cube,
     * which is true of earth and stone and false of a slab, a stair, a carpet, a chest and most of
     * what anybody actually builds a floor out of.
     *
     * <p>
     * Movement is the better question and gets almost everything right on its own: a slab, a stair,
     * a chest and a block of snow all stand on the face, while grass, flowers, vines, rails,
     * shallow water and a dusting of snow do not - and ground under those is still perfectly
     * legible, which is why they are left workable. Carpet is the one case it gets wrong. It stops
     * nothing and covers everything, so it is named.
     *
     * <p>
     * Only the golem asks this. Natural wear and a player's own tamper go on using the narrower
     * test they always have, because widening that would quietly stop ground eroding under every
     * carpet and slab in the world.
     */
    private static boolean coveredOver(World world, int x, int y, int z) {
        Block above = world.getBlock(x, y + 1, z);
        if (above == null || above.isAir(world, x, y + 1, z)) return false;
        if (above.isOpaqueCube()) return true;
        Material material = above.getMaterial();
        if (material == null) return false;
        return material == Material.carpet || material.blocksMovement();
    }

    /**
     * Walks to the next square that needs doing, when none is within arm's length.
     *
     * <p>
     * The work itself sets the destination and clears it the moment something falls within reach,
     * so this task carries no search of its own - it is only the legs. It sits between fighting and
     * wandering: a golem drops its errand to defend its road or to run from something, and takes
     * precedence over idle wandering, which would otherwise walk it away from the very square it
     * had just decided to go and mend.
     */
    public static final class WalkToWork extends EntityAIBase {

        /** Unhurried. It is going to do groundwork, not answering an alarm. */
        private static final double PACE = 0.9D;

        private final EntityGolemOfWays golem;

        private int repathIn;

        public WalkToWork(EntityGolemOfWays golem) {
            this.golem = golem;
            setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            if (!TrmtConfig.golemEnabled || !golem.hasWorkTarget()) return false;
            return golem.getAttackTarget() == null && !golem.isFleeing();
        }

        @Override
        public boolean continueExecuting() {
            return shouldExecute() && !golem.getNavigator()
                .noPath();
        }

        @Override
        public void startExecuting() {
            repathIn = 0;
            walk();
        }

        @Override
        public void resetTask() {
            golem.getNavigator()
                .clearPathEntity();
        }

        @Override
        public void updateTask() {
            int[] to = golem.workTarget();
            if (to == null) return;
            golem.getLookHelper()
                .setLookPosition(to[0] + 0.5D, to[1] + 1.0D, to[2] + 0.5D, 30F, 30F);
            if (--repathIn <= 0) walk();
        }

        /** Asks again every second, because ground it is walking over is ground that changes. */
        private void walk() {
            repathIn = 20;
            int[] to = golem.workTarget();
            if (to == null) return;
            golem.getNavigator()
                .tryMoveToXYZ(to[0] + 0.5D, to[1] + 1.0D, to[2] + 0.5D, PACE);
        }
    }

    /**
     * Picks up what its own work shakes loose.
     *
     * <p>
     * A wider set than what may be handed to it, deliberately: seeds, flint and snowballs are what
     * wearing ground turns up, and they go into the same store for somebody to take out.
     */
    public static void gather(EntityGolemOfWays golem) {
        World world = golem.worldObj;
        if (world == null || world.isRemote) return;

        AxisAlignedBB reach = golem.boundingBox.expand(2.5D, 1.5D, 2.5D);
        @SuppressWarnings("unchecked")
        List<EntityItem> loose = world.getEntitiesWithinAABB(EntityItem.class, reach);
        for (EntityItem dropped : loose) {
            if (dropped.isDead || dropped.delayBeforeCanPickup > 0) continue;
            ItemStack stack = dropped.getEntityItem();
            if (stack == null) continue;
            if (!EntityGolemOfWays.gathersFromGround(stack)) continue;
            if (golem.store(stack)) dropped.setDead();
        }

        // Reinforcing material is not storage: it goes into the golem's mouth rather than into the
        // slots a player filled on purpose, so it is looked for separately and over the same reach.
        GolemMasonry.swallowNearby(golem, loose);
    }
}
