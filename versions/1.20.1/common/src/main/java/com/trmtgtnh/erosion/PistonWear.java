package com.trmtgtnh.erosion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;

import com.trmtgtnh.api.Wear;
import com.trmtgtnh.apiload.ApiHooks;
import com.trmtgtnh.network.TrmtNetwork;

/**
 * Carries a record with the blocks a piston moves (Xep, 2026-11-08: "Wear travels"; {@link WearTravels}).
 *
 * <p>
 * Every move a piston makes, a push or a sticky piston's pull, goes through {@code PistonBaseBlock.moveBlocks}, which
 * works out what moves with a {@code PistonStructureResolver} and then moves it all, slime-held blocks to the side
 * included. Each loader's {@code MixinPistonCarriesWear} calls here just before vanilla asks its resolver, once the
 * head of a retracting piston is already gone, and the same question is asked of a resolver of this class's own: the
 * same level, the same piston, the same answer, so the squares carried are exactly the squares vanilla moves. Nothing
 * has moved yet, so every record is still on its block's square, and the shared move reads every square before it
 * writes any. A block the piston destroys lands nothing, and the block pushed onto its square replaces its record, as
 * it replaces the block; the head's own square is emptied, for the case where nothing stood in front of it but air or
 * a block it broke.
 *
 * <p>
 * The record is on its landing square while the block is still in motion, so the sweep passes over a moving square
 * rather than dropping the record as ground that has gone, and a world saved mid-move keeps it with the chunk. Both
 * chunks are marked to be written; the clients are told of every square, wear and glow, and the moving block's
 * arrival repaints the landing square on each client; the listeners are told of every square.
 */
public final class PistonWear {

    private PistonWear() {}

    /** The store, as the shared move asks it. */
    private static final class Store implements WearTravels.Squares<BlockPos> {

        private final Level level;

        Store(Level level) {
            this.level = level;
        }

        @Override
        public ErosionEntry get(BlockPos at) {
            return ErosionStore.get()
                .getEntry(level, at.getX(), at.getY(), at.getZ());
        }

        @Override
        public void set(BlockPos at, ErosionEntry entry) {
            if (!com.trmtgtnh.util.Heights.holds(level, at.getY())) return;
            int chunkX = at.getX() >> 4;
            int chunkZ = at.getZ() >> 4;
            int index = ErosionStore.get()
                .indexOf(level);
            int key = ErosionKey.packWorld(at.getX(), at.getY(), at.getZ());
            ChunkErosionData data = entry == null ? ErosionStore.get()
                .getChunk(index, chunkX, chunkZ)
                : ErosionStore.get()
                    .getOrCreateChunk(index, chunkX, chunkZ);
            if (data == null) return;
            if (entry == null) data.remove(key);
            else data.put(key, entry);
            data.markDirty();
            ErosionStore.markModified(level, chunkX, chunkZ);
        }
    }

    /**
     * A piston about to move, with vanilla's own arguments. Only the server's pistons are asked about: a client runs
     * the same piston on its own copy of the world, and its records are the server's to send.
     */
    public static void moving(Level level, BlockPos piston, Direction facing, boolean extending) {
        if (level == null || level.isClientSide()) return;
        PistonStructureResolver move = new PistonStructureResolver(level, piston, facing, extending);
        if (!move.resolve()) return;
        Direction way = extending ? facing : facing.getOpposite();
        List<BlockPos> from = move.getToPush();
        List<BlockPos> to = new ArrayList<BlockPos>(from.size());
        for (BlockPos each : from) to.add(each.relative(way));
        List<BlockPos> emptied = new ArrayList<BlockPos>(move.getToDestroy());
        if (extending) emptied.add(piston.relative(facing));
        carry(level, from, to, emptied);
    }

    /** The move made in the store, and everyone told. */
    private static void carry(Level level, List<BlockPos> from, List<BlockPos> to, List<BlockPos> emptied) {
        Store squares = new Store(level);
        // Each moved record as the listeners know it, read while its block is still on its square.
        boolean listening = ApiHooks.listening();
        Map<BlockPos, Wear> left = new HashMap<BlockPos, Wear>();
        Map<BlockPos, Wear> landed = new HashMap<BlockPos, Wear>();
        if (listening) {
            for (int i = 0; i < from.size(); i++) {
                BlockPos at = from.get(i);
                Wear wear = ApiHooks.wearOf(level, at.getX(), at.getY(), at.getZ(), squares.get(at));
                left.put(at, wear);
                landed.put(to.get(i), wear);
            }
        }
        List<WearTravels.Change<BlockPos>> changes = WearTravels.carry(squares, from, to, emptied);
        for (WearTravels.Change<BlockPos> change : changes) {
            BlockPos at = change.at;
            short before = change.before != null && change.before.isVisible() ? change.before.packState()
                : ErosionState.NONE;
            short after = change.after != null && change.after.isVisible() ? change.after.packState()
                : ErosionState.NONE;
            if (before != after) TrmtNetwork.sendDelta(level, at.getX(), at.getY(), at.getZ(), after);
            int litBefore = change.before == null ? 0 : change.before.getLight();
            int litAfter = change.after == null ? 0 : change.after.getLight();
            if (litBefore != litAfter) TrmtNetwork.sendLightDelta(level, at.getX(), at.getY(), at.getZ(), litAfter);
            if (!listening) continue;
            // A record a landing replaces is told of on its own family's chain, its block being gone.
            Wear was = left.containsKey(at) ? left.get(at) : ApiHooks.wearOf(change.before, null);
            ApiHooks.changed(level, at.getX(), at.getY(), at.getZ(), was, change.after == null ? null : landed.get(at));
        }
    }
}
