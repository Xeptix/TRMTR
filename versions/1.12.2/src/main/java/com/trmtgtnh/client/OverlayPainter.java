package com.trmtgtnh.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Paints the overlay into the client's own copy of the world, and takes it back out again.
 *
 * <p>
 * This is the only place a ghost block is ever written, and it only ever writes into
 * {@link WorldClient}. The server's block array is untouched, which is what makes the whole thing
 * removable, per-client switchable and invisible to a player who does not have the mod.
 *
 * <p>
 * Writes go in with no neighbour notification and no per-block render update; each chunk gets a
 * single range invalidation at the end instead. Flying into a well-travelled area can queue thousands
 * of positions at once, so the queue is drained against a per-tick budget rather than in one go.
 *
 * <p>
 * Smaller than the 1.7.10 edition's, and the reason is worth knowing. There, a painted square's
 * appearance lived in which ghost block was written and what metadata it carried, so this class had to
 * choose both - and choose again every time the record moved, or a plant was set on the square, or a
 * wall built over it. Here the ghost's model reads the square's record at every rebuild, on the
 * mesher's own snapshot of the world, and decides all of that for itself. What is left to the painter
 * is the part only it can do: which squares are ghosts at all, and what each one was covering.
 *
 * <p>
 * What it covered is kept as the block state's id, which is the 1.12.2 form of the {@code id << 4 |
 * meta} the other edition packed, and is what puts the ground back when a ghost is lifted.
 */
@SideOnly(Side.CLIENT)
public final class OverlayPainter {

    private static final OverlayPainter INSTANCE = new OverlayPainter();

    /** How often to look for cached chunks that have come back into overlay range. */
    private static final int RESCAN_INTERVAL_TICKS = 40;

    /** Chunks waiting to be painted or repainted, most recent first. */
    private final Deque<Long> pending = new ArrayDeque<Long>();

    private int rescanCounter;

    private OverlayPainter() {}

    public static OverlayPainter get() {
        return INSTANCE;
    }

    public void queueChunk(int chunkX, int chunkZ) {
        Long key = Long.valueOf(ClientErosionCache.chunkKey(chunkX, chunkZ));
        if (!pending.contains(key)) pending.addLast(key);
    }

    public void clearQueue() {
        pending.clear();
    }

    /** Whether the arrival hooks have been seen to run, said once so their absence can be noticed. */
    private boolean arrivalsSaid;

    /**
     * A chunk, or some of one, has just been written over by the server, so worn ground in it may be gone.
     *
     * <p>
     * A server writes a whole section of a chunk back when enough changes in it at once - an explosion,
     * a machine, an edit tool - and a handful of blocks at a time otherwise. Each writes the server's
     * real blocks over whatever was painted there, and neither says so to anything but the world: no
     * chunk load is announced for a partial resend. The overlay still believed every position it had
     * painted was painted, so a road through a blast stayed plain grass until the chunk unloaded.
     * Queued rather than painted here, so a burst of changes costs one pass.
     */
    public void chunkArrived(int chunkX, int chunkZ) {
        if (!TrmtConfig.enabled || !TrmtConfig.overlayVisible()) return;
        if (ClientErosionCache.get()
            .overlay(chunkX, chunkZ) == null) return;
        sayArrivals();
        queueChunk(chunkX, chunkZ);
    }

    /**
     * One block written over by the server: that position repainted at once if the overlay has anything
     * there, and the ground under it looked at again.
     *
     * <p>
     * The ground below as well, because a block arriving on top of worn ground changes how that ground
     * is drawn without touching it: built on, it flattens or hides; cleared, its dip comes back; a plant
     * set down or taken away holds it flat or lets it drop. Another player placing or breaking that one
     * block reaches this client as a single change at the block itself, and the ground under it was
     * never looked at again - drawn sunk under a new wall, or drawn flat where client and server alike
     * now sink a player.
     */
    public void blockArrived(int x, int y, int z) {
        if (!TrmtConfig.enabled || !TrmtConfig.overlayVisible()) return;
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(x >> 4, z >> 4);
        if (overlay == null) return;
        boolean here = overlay.indexOf(ErosionKey.packWorld(x, y, z)) >= 0;
        boolean below = y > 0 && overlay.indexOf(ErosionKey.packWorld(x, y - 1, z)) >= 0;
        if (!here && !below) return;
        sayArrivals();
        WorldClient world = Minecraft.getMinecraft().world;
        if (here) verifyPosition(world, x, y, z);
        if (below) verifyPosition(world, x, y - 1, z);
    }

    private void sayArrivals() {
        if (arrivalsSaid) return;
        arrivalsSaid = true;
        Trmt.LOG.info("Repainting worn ground the server wrote over; the packet hooks are in place");
    }

    public int queueDepth() {
        return pending.size();
    }

    /** Drains the queue against this tick's budget. Called from the client tick. */
    public void tick() {
        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) {
            pending.clear();
            return;
        }
        if (!TrmtConfig.overlayVisible()) return;

        rescanPeriodically();

        int budget = TrmtConfig.overlayApplyBudget;
        while (budget > 0 && !pending.isEmpty()) {
            long key = pending.pollFirst()
                .longValue();
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            // At least one per chunk looked at, so a chunk with nothing left to paint still costs its
            // place in the budget and a queue of those cannot hold the tick for ever.
            budget -= Math.max(1, paintChunk(world, chunkX, chunkZ));
        }
        reportDeclined();
    }

    /**
     * Picks up chunks that were cached while out of overlay range and have since come back into it.
     *
     * <p>
     * Overlay range is usually shorter than render distance, so a chunk can sit loaded and cached but
     * unpainted for as long as the player stays away from it. Nothing else would ever queue it again:
     * the chunk never unloads, and the server has no new packet to send. A slow rescan closes that hole
     * without watching player movement.
     */
    private void rescanPeriodically() {
        if (++rescanCounter < RESCAN_INTERVAL_TICKS) return;
        rescanCounter = 0;
        WorldClient world = Minecraft.getMinecraft().world;
        for (ClientErosionCache.ChunkOverlay overlay : ClientErosionCache.get()
            .overlays()) {
            if (withinRange(overlay.chunkX, overlay.chunkZ) && needsPainting(world, overlay)) {
                queueChunk(overlay.chunkX, overlay.chunkZ);
            }
        }
    }

    /**
     * True when any position in this overlay is not painted, whatever the overlay remembers.
     *
     * <p>
     * Two ways, and the second is the one this edition has to watch for itself. A position may never
     * have been painted - out of range, or covered - and the overlay says so. Or it was painted and the
     * server has since written its own block over the ghost: it does that whenever a dig is started or
     * given up, and to the block clicked whenever something is placed against it, which reads as a path
     * healing the instant somebody swings at it. Both editions hear those writes arrive through three
     * hooks on the client's packet handler and repaint at once - see {@code MixinBlockArrivals}, which
     * this edition gained after the rescan did.
     *
     * <p>
     * The looking stays, because the hooks are not the whole of it and are not required to load: a
     * chunk cached while out of overlay range and since come back into it is announced by no packet at
     * all, and a pack whose renderer has moved those methods gets the rescan rather than a game that
     * refuses to start. What the hooks buy is the two seconds.
     */
    private static boolean needsPainting(WorldClient world, ClientErosionCache.ChunkOverlay overlay) {
        for (int i = 0; i < overlay.size(); i++) {
            if (overlay.originAtIndex(i) < 0) return true;
            if (world == null) continue;
            int key = overlay.keyAt(i);
            BlockPos pos = new BlockPos(
                (overlay.chunkX << 4) + ErosionKey.localX(key),
                ErosionKey.y(key),
                (overlay.chunkZ << 4) + ErosionKey.localZ(key));
            if (!(world.getBlockState(pos)
                .getBlock() instanceof BlockGhost)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------

    /** Paints one chunk. Returns how many positions were touched, for the tick budget. */
    public int paintChunk(WorldClient world, int chunkX, int chunkZ) {
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(chunkX, chunkZ);
        if (overlay == null || overlay.isEmpty()) return 0;
        // The chunk has not arrived yet; it will be queued again when it does.
        if (!isChunkLoaded(world, chunkX, chunkZ)) return 0;
        if (!withinRange(chunkX, chunkZ)) return 0;

        int[] origins = overlay.originsCopy();
        int touched = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int i = 0; i < overlay.size(); i++) {
            int key = overlay.keyAt(i);
            short record = overlay.stateAtIndex(i);
            SurfaceFamily appearance = ErosionEntry.familyOf(record);
            if (appearance == null || ErosionEntry.stageOf(record) < 0) continue;

            int x = (chunkX << 4) + ErosionKey.localX(key);
            int z = (chunkZ << 4) + ErosionKey.localZ(key);
            int y = ErosionKey.y(key);

            if (paint(world, x, y, z, appearance, origins, i)) {
                touched++;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }

        ClientErosionCache.get()
            .put(chunkX, chunkZ, overlay.copyWithOrigins(origins));

        // Told outside any "did anything change" test, deliberately. A position can move along its
        // chain and want the very same ghost at the very same state, so nothing is touched and the
        // colour a map works out from the record still moves - which is exactly the sub-step this
        // exists to show. See XaeroMinimap for why a map needs telling at all.
        com.trmtgtnh.client.xaero.XaeroMinimap.chunkChangedAt(world, chunkX, chunkZ);

        if (touched > 0) {
            world.markBlockRangeForRenderUpdate(
                (chunkX << 4),
                minY,
                (chunkZ << 4),
                (chunkX << 4) + 15,
                maxY,
                (chunkZ << 4) + 15);
        }
        return touched;
    }

    /**
     * Repaints one position if the overlay says it should be worn and it currently is not.
     *
     * <p>
     * The server resyncs blocks to the client more often than you would think: starting or cancelling
     * a dig sends the dug block back, and placing a block sends back both the block clicked and the one
     * it was placed against. Each of those overwrites a ghost with the plain block the server believes
     * is there, which reads in game as a path healing the instant you swing at it.
     */
    public boolean verifyPosition(WorldClient world, int x, int y, int z) {
        if (world == null || !TrmtConfig.overlayVisible()) return false;
        if (y < 0 || y > 255) return false;
        // A ghost already standing here needs no second look unless something has since covered it;
        // what it draws, it works out for itself.
        if (world.getBlockState(new BlockPos(x, y, z))
            .getBlock() instanceof BlockGhost && !covered(world, x, y, z)) return false;

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(chunkX, chunkZ);
        if (overlay == null) return false;

        int index = overlay.indexOf(ErosionKey.packWorld(x, y, z));
        if (index < 0) return false;

        short record = overlay.stateAtIndex(index);
        SurfaceFamily appearance = ErosionEntry.familyOf(record);
        if (appearance == null || ErosionEntry.stageOf(record) < 0) return false;

        int[] origins = overlay.originsCopy();
        boolean painted = paint(world, x, y, z, appearance, origins, index);
        // Kept even when nothing was drawn, if what is remembered about the position moved: a ghost the
        // server wrote over that cannot be drawn again is forgotten as painted, and a copy thrown away
        // here would leave it remembered as painted and never tried again.
        if (!painted && origins[index] == overlay.originAtIndex(index)) return false;

        ClientErosionCache.get()
            .put(chunkX, chunkZ, overlay.copyWithOrigins(origins));
        if (!painted) return false;
        world.markBlockRangeForRenderUpdate(x, y, z, x, y, z);
        com.trmtgtnh.client.xaero.XaeroMinimap.chunkChanged(world, x, z);
        return true;
    }

    /** Paints one position. Returns true when the world actually changed. */
    private boolean paint(WorldClient world, int x, int y, int z, SurfaceFamily appearance, int[] origins, int index) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState current = world.getBlockState(pos);
        boolean hidden = covered(world, x, y, z);
        // Built on, but the wear still shown: the ground keeps the texture of the wear it has and
        // simply fills its own cube again - which the ghost's model does for itself when it sees the
        // block above. What a block on top actually spoils is the dip, not the path.
        boolean flatten = hidden && TrmtConfig.flattenWearUnderBlocks;

        if (current.getBlock() instanceof BlockGhost) {
            // Something has been built on it since it was painted and the setting says to hide wear
            // under blocks. Put the block back and forget only that it was drawn - the wear itself
            // belongs to the server and is untouched, so lifting the cover off brings the same path back.
            if (hidden && !flatten) return unpaint(world, pos, origins, index);
            // Already painted. Everything about how it looks is the model's to decide.
            return false;
        }

        // No ghost stands here, whatever was remembered: the server has written its own block over one
        // painted earlier, or none was ever painted. Forgotten as painted before anything below can
        // decline to paint it, or a declined position kept its old mark and the rescan, which looks for
        // unpainted positions, never tried it again.
        origins[index] = -1;

        // Nothing is drawn under a block, and nothing is remembered about not drawing it: the position
        // keeps its unpainted marker, so the periodic rescan picks it up the moment the cover comes off.
        if (hidden && !flatten) return false;

        Block block = current.getBlock();
        SurfaceFamily family = SurfaceRegistry.familyOf(block, block.getMetaFromState(current));
        if (family == null || !family.staged) {
            // Whatever the server thinks, this client is looking at something that cannot carry a
            // path. Leave it alone; the server drops the entry on its next sweep.
            return false;
        }
        if (!ErosionChain.canEverShow(family, appearance)) {
            // The block here is erodable but not into this appearance - someone has replaced the grass
            // with sand since the server last looked. Painting it would show sand wearing like turf.
            return false;
        }
        // A stair used to be declined here, as the one shape a ghost could not stand in for. It can:
        // the shape is settled where there is a world to ask - BlockGhost.stairCodeAt - and the boxes
        // it is made of serve the collision, the outline and the model alike. Every other shape is a
        // box, and the ghost takes the outline of whatever it covers, so a slab stays a slab and a
        // path stays a pixel short of its cell.
        //
        // Pictures only for a family that has any: one that is not staged is never worn, and has none.
        if (!appearance.staged) return decline(appearance);

        BlockGhost ghost = ModBlocks.ghostGrass();
        if (ghost == null) return false;

        origins[index] = Block.getStateId(current);
        world.setBlockState(pos, ghost.getDefaultState(), 0);
        return true;
    }

    /**
     * Whether something is standing on this position, hiding the face the wear is drawn on.
     *
     * <p>
     * Opaque cubes only. A slab or a fence leaves the ground around it in plain view, and a path that
     * vanished under a fence post would be a stranger sight than one that did not. A sunken ghost is
     * not an opaque cube either, so worn ground under worn ground still shows the dip it has been given.
     */
    static boolean covered(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        if (!TrmtConfig.hideWearUnderBlocks || y >= 255) return false;
        IBlockState above = world.getBlockState(new BlockPos(x, y + 1, z));
        return above.isOpaqueCube();
    }

    /** Puts one painted position back to what it was covering, and marks it undrawn. */
    private static boolean unpaint(WorldClient world, BlockPos pos, int[] origins, int index) {
        int packedOrigin = origins[index];
        if (packedOrigin < 0) return false;
        world.setBlockState(pos, Block.getStateById(packedOrigin), 0);
        origins[index] = -1;
        return true;
    }

    // ------------------------------------------------------------------
    // What was not painted, and why
    // ------------------------------------------------------------------

    /**
     * Squares left undrawn because this build has no picture or no shape for them, by appearance.
     *
     * <p>
     * Counted and said, rather than skipped quietly. The other edition twice shipped a feature that
     * never ran and said so only on a debug line nobody read, and the lesson taken from that is that
     * a thing deliberately not done should be visible as not done. So a world with stone roads in it
     * says that its stone roads are waiting for their pictures.
     */
    private final Map<SurfaceFamily, Integer> declined = new EnumMap<SurfaceFamily, Integer>(SurfaceFamily.class);

    private boolean declinedSaid;

    private boolean decline(SurfaceFamily appearance) {
        Integer held = declined.get(appearance);
        declined.put(appearance, Integer.valueOf(held == null ? 1 : held.intValue() + 1));
        return false;
    }

    private void reportDeclined() {
        if (declinedSaid || declined.isEmpty()) return;
        declinedSaid = true;
        // "First pass", said outright: the rescan tries the same squares again every two seconds, so a
        // running total would grow for as long as the game was open and mean nothing.
        Trmt.LOG.info(
            "Some worn ground is not drawn in this build, because its pictures or its shape are still to come (squares, first pass): {}",
            com.trmtgtnh.util.LogSample.of(declined));
    }

    // ------------------------------------------------------------------
    // Removing
    // ------------------------------------------------------------------

    /** Puts every painted position in a chunk back to what it was covering. */
    public void restoreChunk(WorldClient world, int chunkX, int chunkZ) {
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(chunkX, chunkZ);
        if (overlay == null || overlay.isEmpty()) return;
        if (!isChunkLoaded(world, chunkX, chunkZ)) return;

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean changed = false;

        for (int i = 0; i < overlay.size(); i++) {
            int packedOrigin = overlay.originAtIndex(i);
            if (packedOrigin < 0) continue;

            int key = overlay.keyAt(i);
            int x = (chunkX << 4) + ErosionKey.localX(key);
            int z = (chunkZ << 4) + ErosionKey.localZ(key);
            int y = ErosionKey.y(key);
            BlockPos pos = new BlockPos(x, y, z);

            if (!(world.getBlockState(pos)
                .getBlock() instanceof BlockGhost)) continue;

            world.setBlockState(pos, Block.getStateById(packedOrigin), 0);
            changed = true;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        ClientErosionCache.get()
            .put(chunkX, chunkZ, overlay.withoutOrigins());

        // A lifted path leaves a dark tile behind on a map that keeps what it drew, so the same
        // telling is owed on the way out as on the way in.
        com.trmtgtnh.client.xaero.XaeroMinimap.chunkChangedAt(world, chunkX, chunkZ);

        if (changed) {
            world.markBlockRangeForRenderUpdate(
                (chunkX << 4),
                minY,
                (chunkZ << 4),
                (chunkX << 4) + 15,
                maxY,
                (chunkZ << 4) + 15);
        }
    }

    /**
     * Lifts the ghosts a full chunk packet has stopped listing.
     *
     * <p>
     * A full chunk packet is a complete statement about that chunk, not an addition to what the client
     * already had, and this is the step that makes it one. Anything the server no longer lists has been
     * healed away, and its block has to be put back <em>before</em> the overlay is replaced - because
     * the overlay is the only record of what the ghost was covering, and the replacement drops that
     * record along with the position.
     */
    public void restoreVanished(WorldClient world, ClientErosionCache.ChunkOverlay previous, int[] keptKeys) {
        if (world == null || previous == null || previous.isEmpty()) return;
        java.util.Set<Integer> kept = new java.util.HashSet<Integer>();
        if (keptKeys != null) {
            for (int key : keptKeys) kept.add(Integer.valueOf(key));
        }
        for (int i = 0; i < previous.size(); i++) {
            int key = previous.keyAt(i);
            if (kept.contains(Integer.valueOf(key))) continue;
            restoreSingle(world, previous.chunkX, previous.chunkZ, key, previous.originAtIndex(i));
        }
    }

    /** Puts one position back, if a ghost of ours is still standing there. */
    public void restoreSingle(WorldClient world, int chunkX, int chunkZ, int key, int packedOrigin) {
        if (world == null || packedOrigin < 0) return;
        BlockPos pos = new BlockPos(
            (chunkX << 4) + ErosionKey.localX(key),
            ErosionKey.y(key),
            (chunkZ << 4) + ErosionKey.localZ(key));
        if (!(world.getBlockState(pos)
            .getBlock() instanceof BlockGhost)) return;
        world.setBlockState(pos, Block.getStateById(packedOrigin), 0);
        world.markBlockRangeForRenderUpdate(pos, pos);
    }

    /** Puts everything back, for the client toggle and for leaving a world. */
    public void restoreAll() {
        WorldClient world = Minecraft.getMinecraft().world;
        pending.clear();
        if (world == null) return;
        for (ClientErosionCache.ChunkOverlay overlay : ClientErosionCache.get()
            .overlays()) {
            try {
                restoreChunk(world, overlay.chunkX, overlay.chunkZ);
            } catch (RuntimeException failure) {
                Trmt.LOG.warn(
                    "Failed to restore overlay in chunk {},{}",
                    Integer.valueOf(overlay.chunkX),
                    Integer.valueOf(overlay.chunkZ),
                    failure);
            }
        }
    }

    /** Repaints everything, for switching the overlay back on. */
    public void repaintAll() {
        pending.clear();
        for (ClientErosionCache.ChunkOverlay overlay : ClientErosionCache.get()
            .overlays()) {
            queueChunk(overlay.chunkX, overlay.chunkZ);
        }
    }

    // ------------------------------------------------------------------

    private static boolean isChunkLoaded(WorldClient world, int chunkX, int chunkZ) {
        Chunk chunk = world.getChunkProvider()
            .getLoadedChunk(chunkX, chunkZ);
        return chunk != null && !chunk.isEmpty();
    }

    private static boolean withinRange(int chunkX, int chunkZ) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return false;
        int playerX = ((int) Math.floor(mc.player.posX)) >> 4;
        int playerZ = ((int) Math.floor(mc.player.posZ)) >> 4;
        int range = TrmtConfig.overlayDistanceChunks;
        return Math.abs(playerX - chunkX) <= range && Math.abs(playerZ - chunkZ) <= range;
    }
}
