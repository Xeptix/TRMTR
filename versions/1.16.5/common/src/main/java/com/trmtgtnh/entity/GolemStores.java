package com.trmtgtnh.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.server.HealingCost;
import com.trmtgtnh.util.InventoryAccess;

/**
 * The stores a settled golem works out of.
 *
 * <p>
 * Every other upgrade makes the golem better at something it already did. This one changes what it
 * is: a golem that carries its whole working life in sixteen slots has to be fed by hand for ever,
 * and one that can reach the chests around its anchor is a thing you set down beside a store and
 * leave. That is the difference between a machine and a piece of furniture.
 *
 * <p>
 * Two directions, and they are not symmetrical, on purpose. Taking is open: anything in reach that
 * holds a tamper or a block the mend will accept is fair game, because the golem is doing the work
 * it was asked for and the material is there for it. Putting back is narrow: only into a container
 * that is <em>already</em> holding that same thing. A golem allowed to put anything anywhere would
 * quietly redistribute a base into a mess, and "it goes where its own kind already lives" is the
 * one rule that needs no configuring and surprises nobody.
 *
 * <p>
 * What is remembered between scans is a <em>position</em> and never an inventory. That distinction
 * is the whole safety of this file. A remembered inventory is an object the world has stopped
 * believing in the moment somebody breaks the block: a broken chest leaves a live Java object whose
 * slots still answer questions, and anything put into it is gone with no drop and no log line - and
 * a chunk that unloads and comes back leaves two of them, one of which is a copy that duplicates
 * whatever is taken out of it. A position is asked again every time it is used and cannot go stale.
 *
 * <p>
 * All of it goes through {@link InventoryAccess}, which is the hopper's rules written down, so no
 * storage mod is named or linked against and anything a hopper can work, this can work.
 */
final class GolemStores {

    /**
     * How long a scan is trusted for, in ticks.
     *
     * <p>
     * Only the list of <em>places</em> is kept this long; what is at each place is asked again on
     * every use. Ten seconds is a long time for a base to change and no time at all for the set of
     * chests in it to change, which is the asymmetry this is trading on.
     */
    private static final int SCAN_TICKS = 200;

    /**
     * The most containers one golem will keep a note of.
     *
     * <p>
     * A number rather than no number, because "the containers near the anchor" is a phrase that
     * means four in a hut and four hundred in a warehouse - and every one of them is walked, slot
     * by slot, each time the golem runs out of something. The nearest are kept, which is the ones
     * anybody would have meant.
     */
    private static final int MAX_STORES = 48;

    /** How much of one thing a restock takes at a time, before the golem's own room clamps it. */
    private static final int RESTOCK = 64;

    /**
     * How many times one mend will go back to the stores.
     *
     * <p>
     * A fetch brings back whatever the first container that had any of it was holding, which can be
     * a single block. So one refusal is not proof that nothing in reach will pay - but a golem that
     * kept asking would walk the whole list every stroke. Four is enough for a scattering of part
     * stacks and short of anything anybody would notice.
     */
    static final int FETCHES = 4;

    private static final int[][] BESIDE = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private GolemStores() {}

    /**
     * One container, remembered by where it is rather than by what it was.
     *
     * <p>
     * The side is fixed at the scan because it is a fact about the geometry, which does not change
     * while the block is still there; the inventory is not, because it is a fact about this instant.
     */
    static final class Store {

        final int x;

        final int y;

        final int z;

        final Direction side;

        /** Whether this is somewhere things pass through rather than somewhere they live. */
        final boolean transit;

        Store(int x, int y, int z, Direction side, boolean transit) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.transit = transit;
        }

        /** What is standing here now, or nothing when it has been broken, changed or unloaded. */
        Container live(Level world) {
            if (world == null || world.isClientSide()) return null;
            if (!com.trmtgtnh.util.Worlds.loaded(world, x, y, z)) return null;
            BlockEntity tile = world.getBlockEntity(new net.minecraft.core.BlockPos(x, y, z));
            if (!(tile instanceof Container) || tile.isRemoved()) return null;
            Container inventory = resolve(world, tile);
            return inventory == null || inventory.getContainerSize() <= 0 ? null : inventory;
        }
    }

    /** Whether this golem may reach into the world's containers at all. */
    static boolean reaches(EntityGolemOfWays golem) {
        return TrmtConfig.golemAreaStorage && golem.usesStores();
    }

    /**
     * How far a settled golem reaches, which is its round plus a margin.
     *
     * <p>
     * The same shape as {@code guardReach}: the ground it keeps, and then a little further, because
     * a store sitting at the edge of a round is a store you would expect it to use. Its own number
     * rather than the guard's, because how far a golem will walk to hit something and how far it
     * will reach for a block are not the same question.
     */
    static int reach(EntityGolemOfWays golem) {
        return Math.max(1, golem.workRadius() + Math.max(0, TrmtConfig.golemStoreReach));
    }

    // ------------------------------------------------------------------
    // Finding them
    // ------------------------------------------------------------------

    /**
     * Where every container within reach of the anchor is.
     *
     * <p>
     * Walked chunk by chunk rather than block by block, because a chunk keeps a small map of the
     * tile entities in it. At the default reach that is a handful of map reads in place of upwards
     * of a hundred thousand block lookups, and it stays a handful however far the reach is turned
     * up, because what grows is the number of chunks rather than the work in each.
     *
     * <p>
     * Two things are load-bearing and neither is obvious. A chunk is checked for being loaded
     * before it is asked for, because asking the world for a block in a chunk that is not there
     * loads it, and generates it if it has never existed - so a golem near the edge of a base would
     * quietly become a terrain generator. And the chunk's map is copied before anything in it is
     * touched, because it is a plain map that several innocent-looking calls write to: resolving a
     * tile entity can create one, reading an invalidated one removes it, and marking one dirty
     * tells six neighbours, which is somebody else's code.
     */
    static List<Store> around(Level world, final int[] anchor, int radius) {
        List<Store> found = new ArrayList<Store>();
        if (world == null || world.isClientSide() || anchor == null) return found;

        int fromChunkX = (anchor[0] - radius) >> 4;
        int toChunkX = (anchor[0] + radius) >> 4;
        int fromChunkZ = (anchor[2] - radius) >> 4;
        int toChunkZ = (anchor[2] + radius) >> 4;

        List<BlockEntity> candidates = new ArrayList<BlockEntity>();
        for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
            for (int chunkZ = fromChunkZ; chunkZ <= toChunkZ; chunkZ++) {
                // Asked of the chunk provider rather than through a block position, because a block
                // position carries a height and an anchor can sit outside the world's - which would
                // answer "not loaded" for every chunk in the box and leave a golem on the Nether
                // roof, or one that has fallen out of the world, with no stores at all.
                if (!com.trmtgtnh.util.Worlds.chunkLoaded(world, chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (Object held : chunk.getBlockEntities()
                    .values()) {
                    if (held instanceof BlockEntity) candidates.add((BlockEntity) held);
                }
            }
        }

        // Nearest first, so the cap keeps the containers anybody would have meant rather than
        // whichever chunk happened to be walked first.
        Collections.sort(candidates, new Comparator<BlockEntity>() {

            @Override
            public int compare(BlockEntity left, BlockEntity right) {
                return Long.signum(away(left, anchor) - away(right, anchor));
            }
        });

        Set<Long> taken = new HashSet<Long>();
        for (BlockEntity tile : candidates) {
            if (found.size() >= MAX_STORES) break;
            if (!(tile instanceof Container) || tile.isRemoved()) continue;
            if (Math.abs(
                tile.getBlockPos()
                    .getX() - anchor[0])
                > radius) continue;
            if (Math.abs(
                tile.getBlockPos()
                    .getZ() - anchor[2])
                > radius) continue;
            if (Math.abs(
                tile.getBlockPos()
                    .getY() - anchor[1])
                > radius) continue;
            if (taken.contains(
                at(
                    tile.getBlockPos()
                        .getX(),
                    tile.getBlockPos()
                        .getY(),
                    tile.getBlockPos()
                        .getZ())))
                continue;

            Container inventory = resolve(world, tile);
            if (inventory == null || inventory.getContainerSize() <= 0) continue;

            taken.add(
                at(
                    tile.getBlockPos()
                        .getX(),
                    tile.getBlockPos()
                        .getY(),
                    tile.getBlockPos()
                        .getZ()));
            claimPartner(world, tile, taken);
            found.add(
                new Store(
                    tile.getBlockPos()
                        .getX(),
                    tile.getBlockPos()
                        .getY(),
                    tile.getBlockPos()
                        .getZ(),
                    faceToward(tile, anchor),
                    isTransit(tile)));
        }
        return found;
    }

    /** How far a tile entity is from the anchor, squared, which is all a sort needs. */
    private static long away(BlockEntity tile, int[] anchor) {
        long dx = tile.getBlockPos()
            .getX() - anchor[0];
        long dy = tile.getBlockPos()
            .getY() - anchor[1];
        long dz = tile.getBlockPos()
            .getZ() - anchor[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Whether a container is somewhere things pass through rather than somewhere they live.
     *
     * <p>
     * A hopper holding cobblestone is a hopper with cobblestone in it on its way somewhere else, so
     * "it already keeps this" is exactly the wrong reading of it - a golem that filled the hoppers
     * around it would be feeding whatever they lead to. The same goes for a dropper, a furnace and
     * a brewing stand: each holds a thing in order to do something to it.
     *
     * <p>
     * Vanilla classes only, so nothing is linked against and no mod's machine is named. A modded
     * machine falls to the ordinary rules, which are three deep: it must already hold that exact
     * thing, it must say the slot is valid for it, and if it is sided it must say that face is
     * open. Only ever a refusal to <em>put</em>; a golem may take a spare tamper out of anything.
     */
    private static boolean isTransit(BlockEntity tile) {
        return tile instanceof Hopper || tile instanceof DispenserBlockEntity
            || tile instanceof FurnaceBlockEntity
            || tile instanceof BrewingStandBlockEntity;
    }

    /**
     * The inventory a tile entity really stands for.
     *
     * <p>
     * Only vanilla's chest needs this, and it needs it badly: two chests side by side are one
     * inventory of fifty-four and the tile entity knows nothing about it. The same call refuses -
     * and returns nothing - when a chest is blocked from opening at all, which includes a cat
     * asleep on the lid. That is not worth working around: a chest a player cannot open is a chest
     * a golem should leave alone.
     *
     * <p>
     * It is only asked when all four horizontal neighbours are in loaded chunks, because the call
     * reads every one of them looking for the other half - and reading a block in an absent chunk
     * is what pulls terrain into memory. Losing the far half of a chest that straddles into
     * unloaded ground costs twenty-seven slots; generating that ground costs the tick.
     */
    private static Container resolve(Level world, BlockEntity tile) {
        if (!(tile instanceof ChestBlockEntity)) return (Container) tile;
        if (!com.trmtgtnh.util.Worlds.loaded(
            world,
            tile.getBlockPos()
                .getX(),
            tile.getBlockPos()
                .getY(),
            tile.getBlockPos()
                .getZ()))
            return (Container) tile;
        Block block = com.trmtgtnh.util.Worlds.blockAt(
            world,
            tile.getBlockPos()
                .getX(),
            tile.getBlockPos()
                .getY(),
            tile.getBlockPos()
                .getZ());
        if (!(block instanceof ChestBlock)) return (Container) tile;
        for (int[] step : BESIDE) {
            if (!com.trmtgtnh.util.Worlds.loaded(
                world,
                tile.getBlockPos()
                    .getX() + step[0],
                tile.getBlockPos()
                    .getY(),
                tile.getBlockPos()
                    .getZ() + step[1])) {
                return (Container) tile;
            }
        }
        net.minecraft.core.BlockPos at = tile.getBlockPos();
        return ChestBlock.getContainer((ChestBlock) block, world.getBlockState(at), world, at, false);
    }

    /** A position as one number, so a set of them costs nothing to carry. */
    private static Long at(int x, int y, int z) {
        return Long.valueOf(((long) x << 38) ^ ((long) y << 26) ^ (z & 0x3FFFFFFL));
    }

    /**
     * Marks the other half of a double chest as already accounted for.
     *
     * <p>
     * Two chests side by side are two tile entities and one inventory, so without this the pair is
     * found twice and every one of its fifty-four slots is offered to the golem twice over. Done by
     * position rather than by comparing the two inventories, because each half resolves to a fresh
     * wrapper and two empty double chests compare identical slot for slot - which is exactly how a
     * comparison of contents quietly merges two chests that have nothing to do with each other.
     */
    private static void claimPartner(Level world, BlockEntity tile, Set<Long> taken) {
        if (!(tile instanceof ChestBlockEntity)) return;
        if (!com.trmtgtnh.util.Worlds.loaded(
            world,
            tile.getBlockPos()
                .getX(),
            tile.getBlockPos()
                .getY(),
            tile.getBlockPos()
                .getZ()))
            return;
        // The chest says which side its other half is on. At this version two single chests can stand side by side,
        // so a chest of the same block beside this one is not always its partner, and claiming every one skipped a
        // separate chest (0.9.222, spec GO36).
        net.minecraft.world.level.block.state.BlockState state = world.getBlockState(tile.getBlockPos());
        // A modded chest may be a ChestBlock with a state of its own, no TYPE in it (0.9.222).
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)) return;
        if (state.getValue(ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) return;
        net.minecraft.core.BlockPos partner = tile.getBlockPos()
            .relative(ChestBlock.getConnectedDirection(state));
        if (!com.trmtgtnh.util.Worlds.loaded(world, partner.getX(), partner.getY(), partner.getZ())) return;
        taken.add(at(partner.getX(), partner.getY(), partner.getZ()));
    }

    /**
     * Which face of a container the golem reaches in through.
     *
     * <p>
     * Measured from the anchor rather than from wherever the golem is standing this instant,
     * because the side has to be the same on the tick something comes out as on the tick it goes
     * back - a golem that walked round a machine between the two would be told the face had
     * changed, and a sided machine is entitled to answer differently for each of them.
     *
     * <p>
     * Always a real side and never a wildcard. Several sided inventories answer the question by
     * indexing an array of six, so handing one a minus one is a crash in their code with this mod's
     * name on the report.
     */
    private static Direction faceToward(BlockEntity tile, int[] anchor) {
        int dx = anchor[0] - tile.getBlockPos()
            .getX();
        int dy = anchor[1] - tile.getBlockPos()
            .getY();
        int dz = anchor[2] - tile.getBlockPos()
            .getZ();
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int az = Math.abs(dz);
        if (ay >= ax && ay >= az) return dy >= 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ------------------------------------------------------------------
    // Using them
    // ------------------------------------------------------------------

    /** Where this golem last found containers, walked again when that list has gone stale. */
    static List<Store> current(EntityGolemOfWays golem) {
        if (!reaches(golem)) return null;
        int now = golem.tickCount;
        if (golem.storesFoundAt <= 0 || now - golem.storesFoundAt >= SCAN_TICKS || golem.stores == null) {
            golem.stores = around(golem.level, golem.anchor(), reach(golem));
            golem.storesFoundAt = Math.max(1, now);
            // A fresh look is how a container restocked by hand or by a pipe, or a config reload,
            // comes to be noticed, so nothing the last look could not pay for is taken on trust.
            golem.refusedFetches.forget();
        }
        return golem.stores;
    }

    /**
     * Whether the stores were already found unable to pay for this kind of ground, and the refusal
     * still stands: until the stores are looked at again, the anchor moves or something is put away,
     * or, for a refusal made for want of room, until what the golem carries changes.
     *
     * <p>
     * Looks at the stores again first when a look is due, and a fresh look forgets every refusal,
     * so the answer is never older than one scan - at most ten seconds. A golem that cannot reach
     * any stores has not been refused by them, and is told so.
     *
     * @param kind the kind of worn block, from {@link com.trmtgtnh.util.RefusedFetches#kindOf}
     */
    static boolean refusedBefore(EntityGolemOfWays golem, long kind) {
        if (current(golem) == null) return false;
        return golem.refusedFetches.stillRefused(kind, golem.storesFoundAt, golem.stockChanges());
    }

    /**
     * Looks for a tamper in the stores and brings one back.
     *
     * <p>
     * Called when the golem has none, which is the moment it stops doing anything at all - it
     * cannot work, it cannot fight, and it runs from what it cannot fight. A crate of spare tampers
     * beside it turns that from the end of the golem's day into a pause.
     */
    static boolean fetchTool(EntityGolemOfWays golem) {
        List<Store> stores = current(golem);
        if (stores == null || stores.isEmpty()) return false;

        InventoryAccess.Wanted tamper = new InventoryAccess.Wanted() {

            @Override
            public int howMany(ItemStack stack) {
                return GolemCombat.isTamper(stack) ? 1 : 0;
            }
        };

        for (Store store : stores) {
            Container inventory = store.live(golem.level);
            if (inventory == null) continue;
            ItemStack got = InventoryAccess.take(inventory, tamper, store.side);
            if (got == null) continue;
            if (golem.store(got)) return true;
            // Nowhere to put it after all, so it goes back where it was found. Anything else is a
            // tamper deleted out of somebody's chest by a golem that could not use it.
            giveBack(golem, stores, store, got);
            return false;
        }
        return false;
    }

    /**
     * Looks for something that will pay for a mend and brings back as much as the golem can hold.
     *
     * <p>
     * What counts as payment is not decided here. It is the rule bone meal and the chunk tamper go
     * by, asked of the very class that answers it for them, so a golem drawing on a chest can never
     * accept something bone meal or the chunk tamper would refuse.
     *
     * <p>
     * How much it brings back is the golem's own room rather than a round number, because the golem
     * merges a fetched stack into one slot or not at all: asking for sixty-four when there is room
     * for twelve takes sixty-four out of a chest, fails to store any of it, and puts sixty-four
     * back - every stroke, for as long as the ground needs mending.
     *
     * <p>
     * A fetch that brings nothing back is remembered against this kind of ground, and a caller asks
     * {@link #refusedBefore} before walking the stores for it again. Without that, a golem facing
     * ground nothing in reach pays for walked every container for every square of it it tried, on
     * every stroke, and left the ground as it was each time. Why it failed is kept as well, because
     * the two answers last differently: nothing in reach that pays stands until the stores are next
     * looked at, while something that pays with no room to put it also ends as soon as anything the
     * golem carries changes, since that change may be the room.
     *
     * @param pays what would pay for the ground
     * @param kind the kind of worn block, from {@link com.trmtgtnh.util.RefusedFetches#kindOf}, that a
     *             refusal is remembered against
     */
    static boolean fetchPayment(final EntityGolemOfWays golem, final HealingCost pays, long kind) {
        if (pays == null) return false;
        List<Store> stores = current(golem);
        if (stores == null) return false;
        if (stores.isEmpty()) {
            golem.refusedFetches.nothingToBeHad(kind, golem.storesFoundAt);
            return false;
        }

        // Whether something that would have paid was passed over for want of room. Taking skips a
        // stack it is asked for none of, so without this a full golem beside a chest of the right
        // block would be remembered as having nothing in reach, and go on believing it until the
        // next look at the stores however much room it made in the meantime.
        final boolean[] roomless = { false };
        InventoryAccess.Wanted material = new InventoryAccess.Wanted() {

            @Override
            public int howMany(ItemStack stack) {
                if (!pays.paidBy(stack)) return 0;
                int room = Math.min(RESTOCK, golem.roomFor(stack));
                if (room <= 0) roomless[0] = true;
                return room;
            }
        };

        for (Store store : stores) {
            Container inventory = store.live(golem.level);
            if (inventory == null) continue;
            ItemStack got = InventoryAccess.take(inventory, material, store.side);
            if (got == null) continue;
            if (golem.store(got)) return true;
            giveBack(golem, stores, store, got);
            golem.refusedFetches.noRoomFor(kind, golem.storesFoundAt, golem.stockChanges());
            return false;
        }
        if (roomless[0]) {
            golem.refusedFetches.noRoomFor(kind, golem.storesFoundAt, golem.stockChanges());
        } else {
            golem.refusedFetches.nothingToBeHad(kind, golem.storesFoundAt);
        }
        return false;
    }

    /**
     * Puts back something the golem turned out not to be able to hold.
     *
     * <p>
     * The container it came out of first, then any other in reach, and the ground only when nothing
     * will have it - which can happen, because taking asks one question of a slot and putting asks
     * three. Dropped rather than vanished: a stack on the floor costs somebody a walk, and a stack
     * that stopped existing is a bug report about a mod eating chests.
     */
    private static void giveBack(EntityGolemOfWays golem, List<Store> stores, Store from, ItemStack stack) {
        Level world = golem.level;
        Container home = from.live(world);
        ItemStack left = home == null ? stack : InventoryAccess.put(home, stack, from.side);
        for (int i = 0; i < stores.size() && left != null; i++) {
            Store store = stores.get(i);
            if (store == from) continue;
            Container inventory = store.live(world);
            if (inventory == null) continue;
            left = InventoryAccess.put(inventory, left, store.side);
        }
        if (left != null && !left.isEmpty()) golem.spawnAtLocation(left, 0.5f);
    }

    /**
     * Puts the surplus back into the stores that already hold it.
     *
     * <p>
     * The surplus, not the stock. A tamper never goes, and neither does the fullest stack of
     * anything - so a golem carrying four stacks of cobble puts three away and keeps the biggest,
     * and comes back to a full inventory rather than an empty one. Without that it would tidy away
     * the very material it is about to spend and then fetch it back one stack at a time, for ever.
     *
     * <p>
     * That rule has to give way in one case, and it is the case this is called in. A golem whose
     * every slot holds something different has no surplus by the rule above, so it would put
     * nothing away, stay full, and never pick anything up again - so when the first pass frees
     * nothing at all, a second pass puts one stack away regardless of the rule. One, because the
     * point is to have somewhere to put the next thing rather than to tidy.
     *
     * <p>
     * Where it goes is the narrow half of the bargain: only into a container already holding that
     * same thing, and never into one things merely pass through. It is not a sorting system and
     * must not become one. Anything nothing in reach already keeps stays with the golem, which is
     * the honest answer - the alternative is a golem deciding where somebody's cobblestone lives.
     *
     * @return how many stacks it managed to put away
     */
    static int unload(EntityGolemOfWays golem) {
        List<Store> stores = current(golem);
        if (stores == null || stores.isEmpty()) return 0;

        int put = passOver(golem, stores, keepers(golem), Integer.MAX_VALUE);
        if (put == 0 && golem.isFull()) {
            put = passOver(golem, stores, new boolean[golem.inventory().length], 1);
        }
        if (put > 0) {
            // What went into a container may be the very thing some kind of ground was refused for,
            // and what left the golem may be the room another was refused for want of.
            golem.refusedFetches.forget();
            golem.restock();
        }
        return put;
    }

    /**
     * Which slots the golem is keeping: the fullest stack of each kind it is carrying.
     *
     * <p>
     * The fullest rather than the first, because the golem spends out of the low slots first - so
     * keeping the low one and putting the high one away would keep the stack about to be emptied
     * and give away the one that would have lasted.
     */
    private static boolean[] keepers(EntityGolemOfWays golem) {
        ItemStack[] carried = golem.inventory();
        boolean[] keep = new boolean[carried.length];
        int[] best = new int[carried.length];
        int kinds = 0;

        for (int slot = 0; slot < golem.slotCount() && slot < carried.length; slot++) {
            ItemStack held = carried[slot];
            if (held == null || held.isEmpty() || GolemCombat.isTamper(held)) continue;

            int seen = -1;
            for (int kind = 0; kind < kinds; kind++) {
                if (InventoryAccess.sameKind(carried[best[kind]], held)) {
                    seen = kind;
                    break;
                }
            }
            if (seen < 0) {
                best[kinds++] = slot;
                continue;
            }
            if (held.getCount() > carried[best[seen]].getCount()) best[seen] = slot;
        }

        for (int kind = 0; kind < kinds; kind++) {
            keep[best[kind]] = true;
        }
        return keep;
    }

    /** Walks the golem's slots once, putting away what is not marked as kept, up to a limit. */
    private static int passOver(EntityGolemOfWays golem, List<Store> stores, boolean[] keep, int most) {
        Level world = golem.level;
        ItemStack[] carried = golem.inventory();
        int put = 0;

        for (int slot = 0; slot < golem.slotCount() && slot < carried.length && put < most; slot++) {
            ItemStack held = carried[slot];
            if (held == null || held.isEmpty()) continue;
            if (GolemCombat.isTamper(held)) continue;
            if (slot < keep.length && keep[slot]) continue;

            for (Store store : stores) {
                if (store.transit) continue;
                Container inventory = store.live(world);
                if (inventory == null) continue;
                if (!InventoryAccess.alreadyHolds(inventory, held, store.side)) continue;

                int before = held.getCount();
                ItemStack left = InventoryAccess.put(inventory, held, store.side);
                // Null when all of it went: put answers EMPTY then, and an EMPTY kept here read as a slot still full,
                // so a golem that had unloaded everything went on being full (0.9.222, spec GO46).
                carried[slot] = left == null || left.isEmpty() ? null : left;
                if (left == null) {
                    put++;
                    break;
                }
                if (left.getCount() < before) put++;
                held = left;
            }
        }
        return put;
    }
}
