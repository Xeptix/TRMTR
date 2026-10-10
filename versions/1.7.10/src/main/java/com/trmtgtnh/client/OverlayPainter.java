package com.trmtgtnh.client;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.chunk.Chunk;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.GroundCover;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * Paints the overlay into the client's own copy of the world, and takes it back out again.
 *
 * <p>
 * This is the only place a ghost block is ever written, and it only ever writes into
 * {@link WorldClient}. The server's block array is untouched, which is what makes the whole
 * thing removable, per-client switchable and invisible to a player who does not have the mod.
 *
 * <p>
 * Writes go in with no neighbour notification and no per-block render update; each chunk gets
 * a single range invalidation at the end instead. Flying into a well-travelled area can queue
 * thousands of positions at once, so the queue is drained against a per-tick budget rather
 * than in one go.
 */
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
        WorldClient world = Minecraft.getMinecraft().theWorld;
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
        WorldClient world = Minecraft.getMinecraft().theWorld;
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
            budget -= paintChunk(world, chunkX, chunkZ);
        }
    }

    /**
     * Picks up chunks that were cached while out of overlay range and have since come back
     * into it.
     *
     * <p>
     * Overlay range is usually shorter than render distance, so a chunk can sit loaded and
     * cached but unpainted for as long as the player stays away from it. Nothing else would
     * ever queue it again: the chunk never unloads, and the server has no new packet to send.
     * A slow rescan closes that hole without watching player movement.
     */
    private void rescanPeriodically() {
        if (++rescanCounter < RESCAN_INTERVAL_TICKS) return;
        rescanCounter = 0;
        for (ClientErosionCache.ChunkOverlay overlay : ClientErosionCache.get()
            .overlays()) {
            if (withinRange(overlay.chunkX, overlay.chunkZ) && hasUnpainted(overlay)) {
                queueChunk(overlay.chunkX, overlay.chunkZ);
            }
        }
    }

    /** True when any position in this overlay has not been painted yet. */
    private static boolean hasUnpainted(ClientErosionCache.ChunkOverlay overlay) {
        for (int i = 0; i < overlay.size(); i++) {
            if (overlay.originAtIndex(i) < 0) return true;
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
        if (!isChunkLoaded(world, chunkX, chunkZ)) {
            // The chunk has not arrived yet; it will be queued again on chunk load.
            return 0;
        }
        if (!withinRange(chunkX, chunkZ)) return 0;

        int[] origins = overlay.originsCopy();
        int touched = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int i = 0; i < overlay.size(); i++) {
            int key = overlay.keyAt(i);
            short flags = overlay.stateAtIndex(i);
            SurfaceFamily appearance = ErosionEntry.familyOf(flags);
            int stage = ErosionEntry.stageOf(flags);
            int sink = ErosionState.sinkOf(flags);
            if (appearance == null || stage < 0) continue;

            int x = (chunkX << 4) + ErosionKey.localX(key);
            int z = (chunkZ << 4) + ErosionKey.localZ(key);
            int y = ErosionKey.y(key);

            if (paint(world, x, y, z, appearance, stage, sink, origins, i)) {
                touched++;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }

        ClientErosionCache.get()
            .put(chunkX, chunkZ, overlay.copyWithOrigins(origins));

        // Told outside the test below, deliberately. A position can move along its chain and want
        // the very same ghost at the very same metadata, so nothing is touched and the color a map
        // works out from the record still moves - which is exactly the sub-step this exists to show.
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
     * The server resyncs blocks to the client more often than you would think: starting or
     * cancelling a dig sends the dug block back, and placing a block sends back both the block
     * clicked and the one it was placed against. Each of those overwrites a ghost with the
     * plain block the server believes is there, which reads in game as a path healing the
     * instant you swing at it. There is no client-side event for a block arriving, so the
     * positions the player is actually interacting with are checked directly instead.
     */
    public boolean verifyPosition(WorldClient world, int x, int y, int z) {
        if (world == null || !TrmtConfig.overlayVisible()) return false;
        if (y < 0 || y > 255) return false;
        // A painted position is normally settled and needs no second look. The exceptions are one that
        // has just been built on, one whose cover has just come off, and one a plant has just been set on.
        // The second is invisible from here, so a ghost currently drawn as a full cube is always
        // re-examined, which is enough to let the dip come back; the third is asked outright, since a
        // plant is no cover and a sunk ghost is no full cube.
        if (world.getBlock(x, y, z) instanceof GhostBlock && !covered(world, x, y, z)
            && !drawnFlat(world, x, y, z)
            && !GroundCover.holdsAt(world, x, y, z)) return false;

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(chunkX, chunkZ);
        if (overlay == null) return false;

        int index = overlay.indexOf(ErosionKey.packWorld(x, y, z));
        if (index < 0) return false;

        short flags = overlay.stateAtIndex(index);
        SurfaceFamily appearance = ErosionEntry.familyOf(flags);
        int stage = ErosionEntry.stageOf(flags);
        int sink = ErosionState.sinkOf(flags);
        if (appearance == null || stage < 0) return false;

        int[] origins = overlay.originsCopy();
        boolean painted = paint(world, x, y, z, appearance, stage, sink, origins, index);
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
    private boolean paint(WorldClient world, int x, int y, int z, SurfaceFamily appearance, int stage, int sink,
        int[] origins, int index) {
        Block current = world.getBlock(x, y, z);
        int currentMeta = world.getBlockMetadata(x, y, z);
        boolean hidden = covered(world, x, y, z);
        // Built on, but the wear still shown: the ground keeps the texture of the wear it has
        // and simply fills its own cube again. What a block on top actually spoils is the dip,
        // not the path - a rut with a block sitting in mid-air over it looks broken, while a
        // worn flagstone under a wall looks like a worn flagstone under a wall.
        boolean flatten = hidden && TrmtConfig.flattenWearUnderBlocks;

        // Something planted here holds the ground at the end of the run it takes on the face it
        // still has: as worn as this ground can look while still being this ground. What it has
        // actually taken goes on being counted and is simply not shown - break the plant and the
        // square catches up at once, which is the whole difference between holding wear back and
        // pretending it never happened.
        if (GroundCover.holdsAt(world, x, y, z)) {
            Block under = current instanceof GhostBlock ? originBlockOf(origins[index]) : current;
            int underMeta = current instanceof GhostBlock ? (origins[index] & 0xF) : currentMeta;
            SurfaceFamily base = SurfaceRegistry.familyOf(under, underMeta);
            int flat = ErosionChain.lastFlatIndex(base);
            if (flat >= 0) {
                int at = ErosionChain.indexOf(base, appearance, stage, sink);
                if (at < 0 || at > flat) {
                    SurfaceFamily held = ErosionChain.familyAt(base, flat);
                    int heldStage = ErosionChain.stageAt(base, flat);
                    if (held != null && heldStage >= 0) {
                        appearance = held;
                        stage = heldStage;
                        sink = 0;
                    }
                }
            }
        }

        int drawnSink = flatten ? 0 : sink;

        if (current instanceof GhostBlock) {
            // Something has been built on it since it was painted. Put the block back and
            // forget only that it was drawn - the wear itself belongs to the server and is
            // untouched, so lifting the cover off brings the same path back.
            if (hidden && !flatten) return unpaint(world, x, y, z, origins, index);
            // A ghost with no recorded origin cannot be tinted, cannot be restored and cannot be
            // picked - every one of those keys off this value. It should now be unreachable, so
            // rather than guess at a vanilla counterpart and be wrong for every modded grass,
            // say so once and carry on drawing what is there.
            if (origins[index] < 0) warnOrphan(x, y, z);
            // Already painted. Only the stage or the appearance can have moved on.
            Block wanted = wantedGhost(
                appearance,
                stage,
                drawnSink,
                origins[index],
                isShort(originBlockOf(origins[index])));
            if (current == wanted && currentMeta == stage) return false;
            if (wanted == null) return false;
            world.setBlock(x, y, z, wanted, stage, 0);
            return true;
        }

        // No ghost stands here, whatever was remembered: the server has written its own block over one
        // painted earlier, or none was ever painted. Forgotten as painted before anything below can
        // decline to paint it, or a declined position kept its old mark and the rescan, which looks for
        // unpainted positions, never tried it again.
        origins[index] = -1;

        // Nothing is drawn under a block, and nothing is remembered about not drawing it: the
        // position keeps its unpainted marker, so the periodic rescan picks it up the moment
        // the cover comes off even if the player never looks at it directly.
        if (hidden && !flatten) return false;

        SurfaceFamily family = SurfaceRegistry.familyOf(current, currentMeta);
        if (family == null || !family.staged) {
            // Whatever the server thinks, this client is looking at something that cannot
            // carry a path. Leave it alone; the server drops the entry on its next sweep.
            return false;
        }
        if (!canWear(family, appearance)) {
            // The block here is erodable but not into this appearance - someone has replaced
            // the grass with sand since the server last looked. Painting it would show sand
            // wearing like turf.
            return false;
        }

        int packedOrigin = (Block.getIdFromBlock(current) << 4) | (currentMeta & 0xF);
        origins[index] = packedOrigin;

        Block ghost = wantedGhost(appearance, stage, drawnSink, packedOrigin, isShort(current));
        if (ghost == null) return false;

        world.setBlock(x, y, z, ghost, stage, 0);
        return true;
    }

    /**
     * Whether something is standing on this position, hiding the face the wear is drawn on.
     *
     * <p>
     * Opaque cubes only. A slab or a fence leaves the ground around it in plain view, and a
     * path that vanished under a fence post would be a stranger sight than one that did not.
     * A sunken ghost is not an opaque cube either, so worn ground under worn ground still shows
     * the dip it has been given.
     */
    private static boolean covered(WorldClient world, int x, int y, int z) {
        if (!TrmtConfig.hideWearUnderBlocks || y >= 255) return false;
        Block above = world.getBlock(x, y + 1, z);
        return above != null && above.isOpaqueCube();
    }

    /** Said once a session, because the interesting thing is that it happened at all. */
    private static boolean orphanReported;

    private static void warnOrphan(int x, int y, int z) {
        if (orphanReported) return;
        orphanReported = true;
        com.trmtgtnh.Trmt.LOG.warn(
            "A ghost at {},{},{} has no recorded origin. It will draw untinted and cannot be lifted; "
                + "this means an overlay lost a position while its block stayed painted.",
            Integer.valueOf(x),
            Integer.valueOf(y),
            Integer.valueOf(z));
    }

    /**
     * Whether the ghost here is currently drawn as a full cube.
     *
     * <p>
     * Asked so that the moment a cover is broken the position gets one more look. Without it the
     * usual early-out refuses to re-examine anything that is already a ghost, and a flattened
     * path would keep its filled-in cube until something else happened to queue the chunk.
     */
    private static boolean drawnFlat(WorldClient world, int x, int y, int z) {
        Block here = world.getBlock(x, y, z);
        // A window is a full cube that declines to say so, which is the whole of what it is for -
        // but this asks the question for a different reason, and answering it honestly here would
        // stop the rescan ever looking at the position again.
        if (here instanceof GhostBlock && ((GhostBlock) here).isWindow()) return true;
        return here != null && here.isOpaqueCube();
    }

    /** Puts one painted position back to what it was covering, and marks it undrawn. */
    private static boolean unpaint(WorldClient world, int x, int y, int z, int[] origins, int index) {
        int packedOrigin = origins[index];
        if (packedOrigin < 0) return false;
        Block origin = Block.getBlockById(packedOrigin >> 4);
        if (origin == null) return false;
        world.setBlock(x, y, z, origin, packedOrigin & 0xF, 0);
        origins[index] = -1;
        return true;
    }

    /**
     * Whether a surface of this family can wear into this appearance.
     *
     * <p>
     * Checked by shape rather than by looking the appearance up in the chain, because the
     * chain is built from config that a client is not obliged to have matching the server's.
     * A stage count that differs would otherwise stop the overlay painting at all, which is a
     * far worse failure than tolerating an appearance one step off.
     */
    private static boolean canWear(SurfaceFamily base, SurfaceFamily appearance) {
        // Asked of the chain rather than written out here. This used to name grass and dirt
        // because grass and dirt were the only pair there was, and it went on naming them after
        // stone was given cobble, gravel and earth to wear through into - so the client refused
        // to paint any of the three, and a stone road stopped showing anything at all past its
        // sixteenth gradation while its textures sat in the atlas unused.
        //
        // canEverShow reads the configured list as it is WRITTEN rather than as it is currently
        // switched on, which is what this question wants: a record made while a run was enabled
        // is still that surface's own record after somebody turns the run off, and must not be
        // mistaken for ground that has been dug up and replaced with something else.
        return ErosionChain.canEverShow(base, appearance);
    }

    /** A block that does not fill its own space, such as a grass path. */
    private static boolean isShort(Block block) {
        if (block == null) return false;
        // A shape says so outright, and says so reliably. The bounds fields below are shared
        // mutable state that only mean anything immediately after setBlockBoundsBasedOnState, and
        // a slab's depend on its metadata - so asking a slab this way would answer whatever the
        // last slab anybody looked at happened to be.
        if (SurfaceShape.of(block)
            .isPartial()) return true;
        return block.getBlockBoundsMaxY() < 0.999D;
    }

    private static Block originBlockOf(int packedOrigin) {
        return packedOrigin < 0 ? null : Block.getBlockById(packedOrigin >> 4);
    }

    private static Block wantedGhost(SurfaceFamily appearance, int stage, int sink, int packedOrigin,
        boolean shortBase) {
        // Turned back into its block once, under the ids in force, and every question below asked of
        // that block. The see-through record is filed by block rather than by number, so the number
        // alone could not answer it, and each question no longer decodes the record for itself.
        Block origin = originBlockOf(packedOrigin);
        int originMeta = packedOrigin & 0xF;
        boolean vanillaGrassTop = appearance == SurfaceFamily.GRASS && usesVanillaGrassTop(origin, originMeta);
        // Every grass stage takes the full biome tint now, so the untinted variant is never
        // chosen. It stays registered so the set of block names in a save does not change.
        //
        // A block that was already short needs the hollowed variant even before it has worn
        // down, because that is the only one whose geometry is not a full cube.
        boolean hollow = shortBase || sink > 0;
        // The shape of the block being covered, not of the record: a stair has to be stood in for
        // by something that can draw a stair, and nothing about how worn it is changes that.
        SurfaceShape shape = SurfaceShape.of(origin);
        // Whether this covered block has anything see-through behind its own cut-away texture,
        // which is worked out from the pixels while the sprites are built and is a fact about the
        // block rather than about the setting - so the setting is asked here, where changing it can
        // take effect on the next repaint rather than only on the next stitch.
        boolean window = TrmtConfig.seeThroughInnerLayers
            && com.trmtgtnh.client.texture.InnerLayers.isWindow(origin, originMeta);
        return ModBlocks.forAppearance(
            appearance,
            vanillaGrassTop,
            false,
            hollow,
            shape,
            fillsItsSquare(appearance, origin),
            window);
    }

    /**
     * Whether the block being covered is solid to look at, asked only where the answer can differ.
     *
     * <p>
     * Every family but ice is settled by the family: nothing else here is ever anything but opaque,
     * so nothing else pays for the question. Ice is decided by its material and that material is
     * worn by packed ice and by half a pack's frost, which fill their square exactly as stone does.
     * A block that says it is an opaque cube gets the stand-in that says so too.
     */
    private static boolean fillsItsSquare(SurfaceFamily appearance, Block origin) {
        if (appearance != SurfaceFamily.ICE) return false;
        if (origin == null) return false;
        try {
            return origin.isOpaqueCube();
        } catch (RuntimeException awkwardBlock) {
            // The clear stand-in is the one that draws everything correctly and merely draws some
            // of it needlessly, so it is the right way to be wrong.
            return false;
        }
    }

    /**
     * Whether a block's top texture is vanilla's {@code grass_top}, which is what
     * {@code RenderBlocks} keys grass's untinted-sides-plus-overlay behaviour off.
     */
    private static boolean usesVanillaGrassTop(Block block, int meta) {
        if (block == null) return false;
        if (block == Blocks.grass) return true;
        try {
            IIcon icon = block.getIcon(1, meta);
            return icon != null && "grass_top".equals(icon.getIconName());
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
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

            if (!(world.getBlock(x, y, z) instanceof GhostBlock)) continue;

            Block origin = Block.getBlockById(packedOrigin >> 4);
            if (origin == null) continue;
            world.setBlock(x, y, z, origin, packedOrigin & 0xF, 0);
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

    /** Puts everything back, for the client toggle and for leaving a world. */
    public void restoreAll() {
        WorldClient world = Minecraft.getMinecraft().theWorld;
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
        Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
        return chunk != null && !chunk.isEmpty();
    }

    private static boolean withinRange(int chunkX, int chunkZ) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;
        int playerX = ((int) Math.floor(mc.thePlayer.posX)) >> 4;
        int playerZ = ((int) Math.floor(mc.thePlayer.posZ)) >> 4;
        int range = TrmtConfig.overlayDistanceChunks;
        return Math.abs(playerX - chunkX) <= range && Math.abs(playerZ - chunkZ) <= range;
    }
}
