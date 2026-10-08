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
        // Any square already painted whose record has just crossed into or out of sunk. See relightIfMoved.
        if (keys != null && states != null) {
            for (int i = 0; i < keys.length && i < states.length; i++) {
                short before = previous == null ? com.trmtgtnh.erosion.ErosionState.NONE : previous.stateAt(keys[i]);
                relightIfMoved(
                    before,
                    states[i],
                    (chunkX << 4) + com.trmtgtnh.erosion.ErosionKey.localX(keys[i]),
                    com.trmtgtnh.erosion.ErosionKey.y(keys[i]),
                    (chunkZ << 4) + com.trmtgtnh.erosion.ErosionKey.localZ(keys[i]));
            }
        }
        OverlayPainter.get()
            .queueChunk(chunkX, chunkZ);
        redrawColumn(chunkX, chunkZ, keys);
    }

    /**
     * Re-lights a painted square whose record has just crossed into or out of sunk.
     *
     * <p>
     * A ghost stops all light until its ground sinks and none once it has ({@code
     * BlockGhost.getLightBlock}), and that answer is read from the record rather than from the block.
     * The 1.7.10 edition gets the re-light for nothing: sinking swaps a ghost for its separate sunken
     * variant, and a block that changes is always re-lit. One ghost standing in for both never changes
     * block when it sinks, so the change has to be told - or the cell keeps the darkness it had while it
     * was a whole block. Vanilla's renderer never reads that cell for a sunken top; the Sodium family
     * does, and on 2026-10-07 the second demonstrate yard drew darker the deeper it was worn, under
     * Rubidium only.
     *
     * <p>
     * Only a square already painted is told. One about to be painted is re-lit by the paint itself,
     * because its answer differs from the block it replaces, and a chunk arriving whole would otherwise
     * re-light every square in it for nothing.
     */
    private static void relightIfMoved(short before, short after, int x, int y, int z) {
        if (!lightAnswerMoved(before, after)) return;
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos pos = new BlockPos(x, y, z);
        if (!(level.getBlockState(pos)
            .getBlock() instanceof com.trmtgtnh.block.BlockGhost)) return;
        // Told to the light engine rather than to the world: this version keeps lighting in an engine of
        // its own, and the engine marks the sections it relights for rebuilding itself.
        level.getLightEngine()
            .checkBlock(pos);
    }

    /** Whether a record moving from one state to the other changes the square's answer to the light engine. */
    static boolean lightAnswerMoved(short before, short after) {
        return (com.trmtgtnh.erosion.ErosionState.sinkOf(before) > 0) != (com.trmtgtnh.erosion.ErosionState
            .sinkOf(after) > 0);
    }

    /**
     * The server's own light for one chunk column has just been queued over this client's, and it is
     * wrong at every painted square that answers the light engine differently from a whole block.
     *
     * <p>
     * The server lights the real block standing there, which stops all light and glows with none. A
     * sunk ghost stops none and a lit one glows, and this client worked that out for itself when it
     * painted them. But this version sends light in packets of its own, apart from the blocks, and a
     * packet replaces each section's light whole - so the moment the server's light for a column
     * arrives, every sunk square in it goes back to the darkness of a whole block of earth, and
     * nothing lights it again, because the ghost standing there never changed. The 1.7.10 and 1.12.2
     * editions never meet this: their light arrives with their blocks, which puts the real block back
     * and has the square painted, and lit, again.
     *
     * <p>
     * Found on 2026-10-07 by the harness's yard census, after re-lighting a square as its record sank
     * had cured Rubidium in one run and not in the next. 302 of 328 sunk squares in the second yard
     * were dark in their own cell under Rubidium and Embeddium, all 328 under Canvas - which light a
     * sunk top from that cell, so the yard drew darker the deeper it was worn. Vanilla and OptiFine
     * light a top from the cell above it and drew it right, while anything standing in a sunk square
     * was still lit by the dark cell under every renderer.
     *
     * <p>
     * <strong>The server's light is let in first and the squares told after.</strong> The packet only
     * queues it, for the light engine's next pass; told before that pass, the engine weighs each square
     * against the light that is about to be replaced, finds nothing to do, and the server's darkness
     * lands on top of it. Only when a square here needs it, so a column with no painted path costs
     * nothing.
     */
    public static void serverLightArrived(int chunkX, int chunkZ) {
        relightPainted(chunkX, chunkZ);
    }

    /**
     * Re-lights every painted square in one column that answers the light engine its own way, after letting
     * in whatever light is queued for it - the work {@link #serverLightArrived} exists for, asked from both
     * ends.
     *
     * <p>
     * <strong>From the painter too, from 0.9.219.</strong> The server sends a chunk's light just ahead of the
     * chunk and its wear just after, so a column is often painted while the server's light for it is still
     * queued: the light packet arrived first and found nothing painted to re-light, and the paint's own
     * re-light was weighed against the light about to be replaced. Found by the yard census on a dev run of
     * the first yard alone - 142 of 213 sunk squares dark, own light 0 under full sky - where every full run
     * that day had come back clean, because the walk before the yard had given the light time to land.
     */
    static void relightPainted(int chunkX, int chunkZ) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(chunkX, chunkZ);
        if (overlay == null || overlay.isEmpty()) return;
        java.util.List<BlockPos> told = null;
        for (int i = 0; i < overlay.size(); i++) {
            int key = overlay.keyAt(i);
            BlockPos pos = new BlockPos(
                (chunkX << 4) + com.trmtgtnh.erosion.ErosionKey.localX(key),
                com.trmtgtnh.erosion.ErosionKey.y(key),
                (chunkZ << 4) + com.trmtgtnh.erosion.ErosionKey.localZ(key));
            BlockState standing = level.getBlockState(pos);
            if (!(standing.getBlock() instanceof com.trmtgtnh.block.BlockGhost)) continue;
            if (!answersLightOwnWay(overlay.stateAtIndex(i), standing.getValue(com.trmtgtnh.block.BlockGhost.LIGHT)
                .intValue())) continue;
            if (told == null) told = new java.util.ArrayList<>();
            told.add(pos);
        }
        if (told == null) return;
        net.minecraft.world.level.lighting.LevelLightEngine engine = level.getLightEngine();
        // The server's sections in now, as the next frame would let them in, so each square is weighed
        // against the light it actually has.
        engine.runUpdates(Integer.MAX_VALUE, true, true);
        for (BlockPos pos : told) {
            engine.checkBlock(pos);
        }
    }

    /**
     * Whether a painted square answers the light engine other than as the whole block the server lit:
     * sunk, so it stops no light, or glowing.
     */
    static boolean answersLightOwnWay(short record, int glow) {
        return com.trmtgtnh.erosion.ErosionState.sinkOf(record) > 0 || glow > 0;
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
        relightIfMoved(previous == null ? com.trmtgtnh.erosion.ErosionState.NONE : previous.stateAt(key), state, x, y, z);
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
