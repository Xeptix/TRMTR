package com.trmtgtnh.erosion;

import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import com.trmtgtnh.api.Wear;
import com.trmtgtnh.apiload.ApiHooks;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Carries a record with the block 1.7.10's piston moves (Xep, 2026-11-08: "Wear travels"; {@link WearTravels}).
 *
 * <p>
 * The piston sets every moving block down the same way, pushed or pulled: the square it lands on becomes a moving
 * block, and {@code World.setTileEntity} is handed a {@code TileEntityPiston} that holds the block and knows which way
 * it moves. {@code MixinPistonCarriesWear} passes each one here, before the world takes it. A push of a row comes the
 * far end first, so each landing square has already given up its own record when the block behind lands on it; the
 * shared move reads before it writes, so even a different order could not lose one. A block the piston destroys lands
 * nothing, and the block pushed onto its square replaces its record, as it replaces the block; the head's own square
 * is emptied, for the case where nothing stood in front of it but air or a block it broke.
 *
 * <p>
 * The record is on its landing square while the block is still in motion, so the sweep passes over a moving square
 * rather than dropping the record as ground that has gone, and a world saved mid-move keeps it with the chunk. Both
 * chunks are marked to be written; the clients are told of both squares, wear and glow, and the moving block's
 * arrival repaints the landing square on each client; the listeners are told of both.
 */
public final class PistonWear {

    private PistonWear() {}

    /** One square of the world, for the shared move. */
    private static final class Square {

        final int x, y, z;

        Square(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Square)) return false;
            Square that = (Square) other;
            return that.x == x && that.y == y && that.z == z;
        }

        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }

        @Override
        public String toString() {
            return x + " " + y + " " + z;
        }
    }

    private static Square square(int x, int y, int z) {
        return new Square(x, y, z);
    }

    /** The store, as the shared move asks it. */
    private static final class Store implements WearTravels.Squares<Square> {

        private final World world;

        Store(World world) {
            this.world = world;
        }

        @Override
        public ErosionEntry get(Square at) {
            return ErosionStore.get()
                .getEntry(world, at.x, at.y, at.z);
        }

        @Override
        public void set(Square at, ErosionEntry entry) {
            if (at.y < 0 || at.y > 255) return;
            int chunkX = at.x >> 4;
            int chunkZ = at.z >> 4;
            int dimension = world.provider.dimensionId;
            int key = ErosionKey.packWorld(at.x, at.y, at.z);
            ChunkErosionData data = entry == null ? ErosionStore.get()
                .getChunk(dimension, chunkX, chunkZ)
                : ErosionStore.get()
                    .getOrCreateChunk(dimension, chunkX, chunkZ);
            if (data == null) return;
            if (entry == null) data.remove(key);
            else data.put(key, entry);
            data.markDirty();
            ErosionStore.get()
                .markModified(world, chunkX, chunkZ);
        }
    }

    /**
     * A moving block about to be set down at x, y, z. Only the server's pistons are asked about: a client runs the same
     * piston on its own copy of the world, and its records are the server's to send.
     */
    public static void moved(World world, int x, int y, int z, TileEntity tileEntity) {
        if (world == null || world.isRemote || !(tileEntity instanceof TileEntityPiston) || tileEntity.isInvalid()) {
            return;
        }
        TileEntityPiston moving = (TileEntityPiston) tileEntity;
        if (moving.func_145867_d()) {
            // The head moving out, or the piston's own base drawing back in. The base stays where it is; the head lands
            // on the square in front, which a pushed block has already left, so a record still there was on air or on
            // a block the piston broke.
            if (moving.isExtending()) carry(world, moving, null, square(x, y, z));
            return;
        }
        int[] from = leftFrom(x, y, z, moving.getPistonOrientation(), moving.isExtending());
        carry(world, moving, square(from[0], from[1], from[2]), square(x, y, z));
    }

    /**
     * The square a moving block left, from where it lands: one back along its way. Pushed, it moves toward the side the
     * piston faces; pulled, away from it. Package-visible so it is asked of the class without a world.
     */
    static int[] leftFrom(int x, int y, int z, int side, boolean extending) {
        int way = extending ? 1 : -1;
        return new int[] { x - way * Facing.offsetsXForSide[side], y - way * Facing.offsetsYForSide[side],
            z - way * Facing.offsetsZForSide[side] };
    }

    private static final List<Square> NONE = Collections.emptyList();

    /** One block's move, or with nothing moved the head's square emptied, and everyone told. */
    private static void carry(World world, TileEntityPiston moving, Square from, Square to) {
        Store squares = new Store(world);
        List<WearTravels.Change<Square>> changes = from == null
            ? WearTravels.carry(squares, NONE, NONE, Collections.singletonList(to))
            : WearTravels.carry(squares, Collections.singletonList(from), Collections.singletonList(to), NONE);
        if (changes.isEmpty()) return;
        // The moved block's own chain, for its record on either square: the landing square holds the moving block
        // until it arrives. A record it replaces is told of on its own family's chain, its block being gone.
        ErosionChain.Chain chain = SurfaceRegistry.chainOf(moving.getStoredBlockID(), moving.getBlockMetadata());
        boolean listening = ApiHooks.listening();
        for (WearTravels.Change<Square> change : changes) {
            Square at = change.at;
            short before = change.before != null && change.before.isVisible() ? change.before.packState()
                : ErosionState.NONE;
            short after = change.after != null && change.after.isVisible() ? change.after.packState()
                : ErosionState.NONE;
            if (before != after) TrmtNetwork.sendDelta(world, at.x, at.y, at.z, after);
            int litBefore = change.before == null ? 0 : change.before.getLight();
            int litAfter = change.after == null ? 0 : change.after.getLight();
            if (litBefore != litAfter) TrmtNetwork.sendLightDelta(world, at.x, at.y, at.z, litAfter);
            if (!listening) continue;
            Wear was = ApiHooks.wearOf(change.before, at.equals(from) ? chain : null);
            ApiHooks.changed(world, at.x, at.y, at.z, was, ApiHooks.wearOf(change.after, chain));
        }
    }
}
