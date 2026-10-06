package com.trmtgtnh.client;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The overlay's own traffic: what arrives from the server, and what a ghost asks back.
 *
 * <p>
 * Every packet that carries wear ends here, and so does every question the ghost's model asks about
 * the square it is drawing. Keeping both in one class is deliberate - what arrives and what is asked
 * are two views of the same cache, and a change to one is almost always a change to the other.
 *
 * <p>
 * Carried from the 1.12.2 edition's client proxy, which has no counterpart here.
 */
public final class ClientOverlay {

    private static final ClientOverlay INSTANCE = new ClientOverlay();

    private ClientOverlay() {}

    public static ClientOverlay get() {
        return INSTANCE;
    }

    public void handleChunkErosion(int chunkX, int chunkZ, int[] keys, short[] states) {
        ClientErosionCache cache = ClientErosionCache.get();
        ClientErosionCache.ChunkOverlay previous = cache.overlay(chunkX, chunkZ);
        // Before the overlay is replaced, because it is the only record of what each ghost was
        // covering. See OverlayPainter.restoreVanished.
        OverlayPainter.get()
            .restoreVanished(Minecraft.getInstance().level, previous, keys);
        cache.put(chunkX, chunkZ, ClientErosionCache.build(chunkX, chunkZ, keys, states, previous));
        OverlayPainter.get()
            .queueChunk(chunkX, chunkZ);
        redrawColumn(chunkX, chunkZ, keys);
    }

    public void handleDelta(int x, int y, int z, short state) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ClientErosionCache cache = ClientErosionCache.get();
        ClientErosionCache.ChunkOverlay previous = cache.overlay(chunkX, chunkZ);
        int key = com.trmtgtnh.erosion.ErosionKey.packWorld(x, y, z);
        if (state == com.trmtgtnh.erosion.ErosionState.NONE && previous != null) {
            // A cleared position has to be put back before its record is dropped, or the ghost
            // would be stranded with nothing left to say what it was covering.
            OverlayPainter.get()
                .restoreSingle(Minecraft.getInstance().level, chunkX, chunkZ, key, previous.originAt(key));
        }
        cache.put(chunkX, chunkZ, ClientErosionCache.withSingle(previous, chunkX, chunkZ, key, state));
        OverlayPainter.get()
            .queueChunk(chunkX, chunkZ);
        // And the mesh asked for directly, because the painter cannot always tell that anything
        // happened. A square already painted stays painted - a ghost is a ghost - and what it draws
        // is worked out from the record at every rebuild, so a record moving along its chain changes
        // the picture without the painter touching a block. Nothing else would ask for the rebuild.
        redrawAt(x, y, z);
    }

    public void handleClearAll() {
        // Put back before the caches go, since the caches are the only record of what to put back.
        OverlayPainter.get()
            .restoreAll();
        ClientErosionCache.get()
            .clear();
        ClientLightCache.get()
            .clear();
    }

    public void handleChunkLight(int chunkX, int chunkZ, int[] keys, byte[] values) {
        ClientLightCache.get()
            .put(chunkX, chunkZ, keys, values);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && keys != null) {
            // The client keeps a block-light map of its own and works it out itself, so a glow that has
            // just arrived is propagated here before anything is redrawn - or the mesh is rebuilt against
            // the old light and the square stays dark until something else disturbs it.
            for (int key : keys) {
                int x = (chunkX << 4) + com.trmtgtnh.erosion.ErosionKey.localX(key);
                int y = com.trmtgtnh.erosion.ErosionKey.y(key);
                int z = (chunkZ << 4) + com.trmtgtnh.erosion.ErosionKey.localZ(key);
                // The ghost's own state is repainted before the engine is told, because the engine
                // reads the state: what a square glows is a property of it at this version rather
                // than an answer to a per-position question. See BlockGhost.LIGHT.
                OverlayPainter.get()
                    .relight(x, y, z);
                // Told to the light engine rather than to the world: this version keeps lighting in an
                // engine of its own and the world has no opinion about it. Same job, same moment.
                mc.level.getLightEngine()
                    .checkBlock(new net.minecraft.core.BlockPos(x, y, z));
            }
        }
        redrawColumn(chunkX, chunkZ, keys);
    }

    public void handleLightDelta(int x, int y, int z, int packed) {
        ClientLightCache.get()
            .set(x, y, z, packed);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // Propagate first, then redraw - the other order rebuilds the mesh against the old light. And
        // as far as the glow can reach rather than one square, because a light's whole point is the
        // ground around it.
        // Repainted first, for the reason in handleChunkLight: the engine reads the state.
        OverlayPainter.get()
            .relight(x, y, z);
        mc.level.getLightEngine()
            .checkBlock(new net.minecraft.core.BlockPos(x, y, z));
        int reach = 16;
        redraw(x - reach, Math.max(0, y - reach), z - reach, x + reach, Math.min(255, y + reach), z + reach);
    }

    /**
     * A square's glow as this client holds it.
     *
     * <p>
     * Asked from mesher threads as well as this one, through the ghost's light value and its tint. The
     * cache is written as immutable snapshots so those reads need no lock; a read that races a chunk
     * going away is still possible, and dark is the least-wrong answer to it.
     */
    public int clientLightLevel(int x, int y, int z) {
        try {
            return ClientLightCache.get()
                .levelAt(x, y, z);
        } catch (RuntimeException racingAnUnload) {
            return 0;
        }
    }

    /**
     * The whole packed byte, and nothing here asks for it.
     *
     * <p>
     * 1.12.2 reaches the packed light through its proxy, because its {@code GhostLight} is handed a
     * world it cannot ask which side it is on. This version's is handed a {@code BlockGetter}, asks it,
     * and reads the store on the server and the cache on the client itself - so the packed byte never
     * comes through here. Kept because the pair reads as a pair, and named in {@code tools/unwired.py}
     * sweeps as deliberate rather than missed.
     */
    public int clientLightPacked(int x, int y, int z) {
        try {
            return ClientLightCache.get()
                .at(x, y, z);
        } catch (RuntimeException racingAnUnload) {
            return 0;
        }
    }

    /**
     * Asks for the meshes a chunk packet's squares sit in to be rebuilt, and no more.
     *
     * <p>
     * Over the heights the packet names rather than the whole column, because a chunk is sixteen
     * sections tall and a road occupies one or two of them: marking all sixteen would rebuild
     * fourteen meshes of sky and bedrock for every chunk that arrived.
     */
    private static void redrawColumn(int chunkX, int chunkZ, int[] keys) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || keys == null || keys.length == 0) return;
        int minY = 255;
        int maxY = 0;
        for (int key : keys) {
            int y = com.trmtgtnh.erosion.ErosionKey.y(key);
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        redraw(chunkX << 4, minY, chunkZ << 4, (chunkX << 4) + 15, maxY, (chunkZ << 4) + 15);
    }

    /**
     * Asks the renderer to build the chunks holding this box again.
     *
     * <p>
     * Said to the renderer rather than to the world, which has no such method here: a level knows what
     * blocks it holds and nothing about who is drawing them.
     */
    private static void redraw(int x0, int y0, int z0, int x1, int y1, int z1) {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null) return;
        client.levelRenderer.setBlocksDirty(x0, y0, z0, x1, y1, z1);
    }

    /** Asks for the mesh around one square to be rebuilt. The game widens it by a block itself. */
    private static void redrawAt(int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        redraw(x, y, z, x, y, z);
    }

    // -- what a ghost asks about itself --

    /**
     * The record a ghost should draw and stand on: what the server sent, as the world around the
     * square now changes it.
     *
     * <p>
     * Two things change it, and both are about the block above. Something planted there holds the
     * ground at the end of the run it takes on the face it still has - as worn as this ground can
     * look while still being this ground - and what it has actually taken goes on being counted and
     * is simply not shown: break the plant and the square catches up at once. And a block built on
     * top, where the setting says so, keeps the wear drawn but fills the square's cube again: what a
     * block on top spoils is the dip, not the path.
     *
     * <p>
     * The other edition works both out in the painter and bakes the answer into which ghost it
     * writes. Here they are worked out wherever a ghost is asked, from whichever view of the world
     * the asker was handed - on a mesher thread, the snapshot the game built for that thread - so a
     * plant set down re-meshes the square beneath it through vanilla's own neighbour update and
     * the picture follows without the painter being told.
     */
    public short ghostRecordAt(net.minecraft.world.level.BlockGetter world, int x, int y, int z) {
        short record;
        int packedOrigin;
        try {
            ClientErosionCache cache = ClientErosionCache.get();
            record = cache.stateAt(x, y, z);
            packedOrigin = cache.originAt(x, y, z);
        } catch (RuntimeException racingAnUnload) {
            // A read that raced a chunk going away. Untouched is the least-wrong answer.
            return com.trmtgtnh.erosion.ErosionState.NONE;
        }
        if (!com.trmtgtnh.block.BlockGhost.shows(record) || world == null) return record;

        com.trmtgtnh.surface.SurfaceFamily appearance = com.trmtgtnh.erosion.ErosionState.familyOf(record);
        int layer = com.trmtgtnh.erosion.ErosionState.layerOf(record);
        int sink = com.trmtgtnh.erosion.ErosionState.sinkOf(record);
        boolean changed = false;

        if (packedOrigin >= 0 && com.trmtgtnh.erosion.GroundCover.holdsAt(world, x, y, z)) {
            BlockState under = net.minecraft.world.level.block.Block.stateById(packedOrigin);
            com.trmtgtnh.surface.SurfaceFamily base = com.trmtgtnh.surface.SurfaceRegistry.familyOf(under);
            int flat = base == null ? -1 : com.trmtgtnh.erosion.ErosionChain.lastFlatIndex(base);
            if (flat >= 0) {
                int at = com.trmtgtnh.erosion.ErosionChain.indexOf(base, appearance, layer, sink);
                if (at < 0 || at > flat) {
                    com.trmtgtnh.surface.SurfaceFamily held = com.trmtgtnh.erosion.ErosionChain.familyAt(base, flat);
                    int heldLayer = com.trmtgtnh.erosion.ErosionChain.stageAt(base, flat);
                    if (held != null && heldLayer >= 0) {
                        appearance = held;
                        layer = heldLayer;
                        sink = 0;
                        changed = true;
                    }
                }
            }
        }

        if (sink > 0 && com.trmtgtnh.config.TrmtConfig.flattenWearUnderBlocks
            && OverlayPainter.covered(world, x, y, z)) {
            sink = 0;
            changed = true;
        }

        if (!changed) return record;
        return com.trmtgtnh.erosion.ErosionState.pack(
            appearance,
            layer,
            sink,
            com.trmtgtnh.erosion.ErosionState.frozenOf(record),
            com.trmtgtnh.erosion.ErosionState.reinforceOf(record));
    }

    public int ghostOriginAt(int x, int y, int z) {
        try {
            return ClientErosionCache.get()
                .originAt(x, y, z);
        } catch (RuntimeException racingAnUnload) {
            return -1;
        }
    }

    /**
     * How far the ground under a block has dropped, as this client's ghost there stands.
     *
     * <p>
     * The footing, not the picture: in visual mode the ground is drawn sunk and walked on at full
     * height, and nothing resting on it should move either.
     */
    public double settledDropUnder(int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0D;
        if (!(mc.level.getBlockState(new BlockPos(x, y, z))
            .getBlock() instanceof com.trmtgtnh.block.BlockGhost)) return 0.0D;
        return com.trmtgtnh.block.BlockGhost.collisionSink(ghostRecordAt(mc.level, x, y, z)) / 16.0D;
    }
}
