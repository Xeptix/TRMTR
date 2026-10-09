package com.trmtgtnh.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.trmtgtnh.CommonProxy;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.GhostRendering;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.config.ConfigReload;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.ServerRules;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.erosion.PhysicalDecay;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.util.MainThread;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * The client half: overlay cache, painting, generated textures, and the handshake that tells
 * the server whether this player wants any of it.
 */
public class ClientProxy extends CommonProxy {

    /** Reset on disconnect so the next server gets a fresh announcement. */
    private boolean announced;

    /** Last known value of the client toggle, so a change can be acted on the tick it happens. */
    private boolean lastShowErosion = true;

    /**
     * Whether anything on this client reads an inspection reply. Waila's provider is the only reader,
     * so without Waila the crosshair asks the server nothing.
     */
    private boolean inspectionRead;

    /** Why the block atlas is waiting to be rebuilt, or null when nothing is waiting on it. */
    private String restitchWanted;

    /** Client ticks left of the warning, once one has actually been given. */
    private int restitchIn;

    /**
     * Two seconds before a rebuild starts, and it is not politeness.
     *
     * <p>
     * Nothing is drawn between a chat message being added and the end of the tick it was added in,
     * so a warning printed immediately before a half-minute freeze is read after the freeze, which
     * is no warning at all. The same wait collects a burst: publishing several families from the
     * wear table arrives as several reloads a second or two apart, and each rebuild costs the better
     * part of half a minute.
     */
    private static final int RESTITCH_DELAY = 40;

    @Override
    public void preInit() {
        super.preInit();
        MinecraftForge.EVENT_BUS.register(this);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(this);
        lastShowErosion = TrmtConfig.showErosion;
        // The mod list is settled before any mod's pre-init, so this answer never changes. Read here
        // rather than in init, which only the common proxy has.
        inspectionRead = cpw.mods.fml.common.Loader.isModLoaded("Waila");
    }

    // ------------------------------------------------------------------
    // Packet sinks
    // ------------------------------------------------------------------

    @Override
    public void handleChunkErosion(int chunkX, int chunkZ, int[] keys, short[] states) {
        ClientErosionCache cache = ClientErosionCache.get();
        ClientErosionCache.ChunkOverlay previous = cache.overlay(chunkX, chunkZ);
        restoreVanished(previous, chunkX, chunkZ, keys);
        cache.put(chunkX, chunkZ, ClientErosionCache.build(chunkX, chunkZ, keys, states, previous));
        OverlayPainter.get()
            .queueChunk(chunkX, chunkZ);
    }

    /**
     * Lifts the ghosts this packet has stopped listing.
     *
     * <p>
     * A full chunk packet is a complete statement about that chunk, not an addition to what the
     * client already had, and this is the step that makes it one. Anything the server no longer
     * lists has been healed away, and its block has to be put back <em>before</em> the overlay
     * is replaced - because the overlay is the only record of what the ghost was covering, and
     * the replacement drops that record along with the position.
     *
     * <p>
     * Nothing downstream can do this instead. Every path in the painter that could restore a
     * position walks the overlay to find it, so a position that has left the overlay is
     * unreachable from all of them: the ghost stands there until the chunk unloads, and because
     * its origin is gone the renderer stops tinting it - which is why an area mend used to leave
     * a square of grey grass behind rather than the ground it started from.
     *
     * <p>
     * The single-position path has always done this; see {@link #handleDelta}. Only the chunk
     * path was missing it, which is exactly why the plain tamper was unaffected and the two area
     * tools were not.
     */
    private void restoreVanished(ClientErosionCache.ChunkOverlay previous, int chunkX, int chunkZ, int[] keys) {
        if (previous == null || previous.isEmpty()) return;
        int[] listed = keys == null ? new int[0] : keys.clone();
        java.util.Arrays.sort(listed);

        for (int i = 0; i < previous.size(); i++) {
            int key = previous.keyAt(i);
            if (java.util.Arrays.binarySearch(listed, key) >= 0) continue;
            restoreSingle(
                (chunkX << 4) + ErosionKey.localX(key),
                ErosionKey.y(key),
                (chunkZ << 4) + ErosionKey.localZ(key),
                previous);
        }
    }

    /**
     * A chunk's lit positions, replacing whatever this client believed about that chunk.
     *
     * <p>
     * Every one of them has to be propagated individually, because the client keeps its own
     * block-light map and vanilla's copy of it knows nothing about a glow this mod invented. There
     * are only ever a handful of lit positions in a chunk, so walking them costs nothing; skipping
     * the step would leave a lit road looking exactly like an unlit one until something else
     * happened to dirty the lighting.
     */
    @Override
    public void handleChunkLight(int chunkX, int chunkZ, int[] keys, byte[] values) {
        ClientLightCache.get()
            .put(chunkX, chunkZ, keys, values);

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || keys == null) return;
        for (int key : keys) {
            mc.theWorld.func_147451_t(
                (chunkX << 4) + ErosionKey.localX(key),
                ErosionKey.y(key),
                (chunkZ << 4) + ErosionKey.localZ(key));
        }
        mc.theWorld
            .markBlockRangeForRenderUpdate(chunkX << 4, 0, chunkZ << 4, (chunkX << 4) + 15, 255, (chunkZ << 4) + 15);
    }

    @Override
    public void handleLightDelta(int x, int y, int z, int packed) {
        ClientLightCache.get()
            .set(x, y, z, packed);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return;
        // Propagate first, then redraw - the other order rebuilds the mesh against the old light.
        mc.theWorld.func_147451_t(x, y, z);
        // Only as far as the glow can actually reach, rather than the whole column.
        int reach = 16;
        mc.theWorld.markBlockRangeForRenderUpdate(
            x - reach,
            Math.max(0, y - reach),
            z - reach,
            x + reach,
            Math.min(255, y + reach),
            z + reach);
    }

    /**
     * Whether the local player's crosshair is on nothing at all.
     *
     * <p>
     * {@code objectMouseOver} is the very field {@code Minecraft.func_147116_af} switches on three
     * lines after it calls {@code swingItem}, so an answer of MISS here is the same answer vanilla
     * is about to give itself - which is what guarantees that a click producing an air gesture is
     * exactly a click that will never produce a {@code LEFT_CLICK_BLOCK}.
     *
     * <p>
     * The identity check matters. Every other player's swing is replayed on this client from an
     * animation packet, and without it a stranger swinging a comparison tool across the street
     * would fire the gesture on this keyboard's player.
     */
    @Override
    public boolean swungAtNothing(net.minecraft.entity.player.EntityPlayer swinger) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || swinger != mc.thePlayer) return false;
        net.minecraft.util.MovingObjectPosition target = mc.objectMouseOver;
        return target != null && target.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.MISS;
    }

    @Override
    public int clientLightLevel(int x, int y, int z) {
        return ClientLightCache.get()
            .levelAt(x, y, z);
    }

    @Override
    public int clientLightPacked(int x, int y, int z) {
        return ClientLightCache.get()
            .at(x, y, z);
    }

    private void relight(int x, int y, int z) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return;
        // The client keeps its own block-light map, so it has to propagate the change itself
        // before the mesh is rebuilt - otherwise the mesh is rebuilt against the old lighting.
        if (y >= 0) mc.theWorld.func_147451_t(x, y, z);
        mc.theWorld.markBlockRangeForRenderUpdate(x - 16, 0, z - 16, x + 16, 255, z + 16);
    }

    @Override
    public void handleDelta(int x, int y, int z, short state) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ClientErosionCache cache = ClientErosionCache.get();
        ClientErosionCache.ChunkOverlay previous = cache.overlay(chunkX, chunkZ);

        if (state == com.trmtgtnh.erosion.ErosionState.NONE && previous != null) {
            // A cleared position has to be put back before its record is dropped, or the
            // ghost would be stranded with nothing left to say what it was covering.
            restoreSingle(x, y, z, previous);
        }

        cache.put(
            chunkX,
            chunkZ,
            ClientErosionCache.withSingle(previous, chunkX, chunkZ, ErosionKey.packWorld(x, y, z), state));
        OverlayPainter.get()
            .queueChunk(chunkX, chunkZ);
        // And ask for the mesh directly, because the painter cannot always tell that anything
        // happened. What a ghost draws is derived from the record rather than stored in the block:
        // the depth, the turn of the wear pattern that goes with it, the pin and the glow are all
        // read live at draw time, and only the appearance and the gradation are in the block and
        // its metadata. So a position can move to a different step of its chain and still want the
        // very same ghost at the very same metadata - a rut one pixel deeper is exactly that - at
        // which point the painter finds nothing to change, reports nothing touched, and no section
        // is ever rebuilt. This costs one rebuild for a position that has demonstrably just moved.
        if (Minecraft.getMinecraft().theWorld != null) {
            Minecraft.getMinecraft().theWorld.markBlockRangeForRenderUpdate(x, y, z, x, y, z);
            // And a minimap that reads the world, which is told by nothing else: this mod
            // never sends a block packet, so a map keeping what it drew keeps it for ever.
            com.trmtgtnh.client.xaero.XaeroMinimap.chunkChanged(Minecraft.getMinecraft().theWorld, x, z);
        }
    }

    private void restoreSingle(int x, int y, int z, ClientErosionCache.ChunkOverlay overlay) {
        if (Minecraft.getMinecraft().theWorld == null) return;
        int packedOrigin = overlay.originAt(ErosionKey.packWorld(x, y, z));
        if (packedOrigin < 0) return;
        if (!(Minecraft.getMinecraft().theWorld.getBlock(x, y, z) instanceof com.trmtgtnh.block.GhostBlock)) return;

        Block origin = Block.getBlockById(packedOrigin >> 4);
        if (origin == null) return;
        Minecraft.getMinecraft().theWorld.setBlock(x, y, z, origin, packedOrigin & 0xF, 3);
    }

    /**
     * Adopts a server's rules for the session.
     *
     * <p>
     * Applied to the in-memory settings and never written to disk, so leaving the server puts
     * this client's own preferences straight back. The geometry has to come across because a client
     * decides for itself what to do with the depth it is sent - clamping a rut to its own ceiling,
     * and declining to fall into one at all where its own file says the ruts are only pictures - so
     * if the two sides disagreed on those numbers a player would predict a floor at one height
     * while the server insisted on another.
     *
     * <p>
     * Handed to {@link ServerRules} rather than merely written here, because written here is
     * precisely where it did not survive: any config read while connected rebuilds the family map
     * from this client's own file, and took the whole lot straight back.
     */
    @Override
    public void applyServerRules(boolean forceOverlay, String decayMode, ServerRules.Geometry geometry) {
        boolean wasForced = TrmtConfig.overlayForced;
        // Taken before the rules land, because this is the last moment the chains still describe
        // this client's own file. What has to be asked is which materials a run passes through,
        // and nothing but building the chains says that.
        int[] appearancesBefore = ServerRules.chainAppearances();
        ServerRules.hold(forceOverlay, decayMode, geometry);

        ErosionChain.rebuild();
        PhysicalDecay.refresh();

        if (forceOverlay && !wasForced && !TrmtConfig.showErosion) {
            OverlayPainter.get()
                .repaintAll();
            tell(
                EnumChatFormatting.YELLOW
                    + "[TRMT] This server has worn ground you can walk down into, so path visuals stay on here.");
        }
        if (wasForced && !forceOverlay && !TrmtConfig.showErosion) {
            // Reloading a server's config can stop it needing real ruts, and a player who never
            // wanted overlays should not be left holding the ones it made them show.
            OverlayPainter.get()
                .restoreAll();
            tell(EnumChatFormatting.GRAY + "[TRMT] Path visuals are back under your own setting on this server.");
        }
        if (ServerRules.enabledMovedFrom(appearancesBefore)) {
            // Before the stitch below rather than after it, for the one case where both fire:
            // a family arriving is a family the atlas has never planned pictures for, and the
            // planner reads the table this rebuilds.
            resettleSurfaces();
        }
        if (ServerRules.appearancesMovedFrom(appearancesBefore)) {
            // Wear pictures are drawn when the atlas is stitched, and a full run of them exists
            // per appearance a chain visits, so an appearance this server's geometry reaches
            // that this client's own config never did has nothing drawn for it until it is
            // stitched again. The gradation count is not the question and never was: a longer
            // run through the same materials wants the sprites already in the atlas, so a server
            // one stage away used to buy half a minute of rebuilding for a picture that came
            // back identical to the pixel.
            requestRestitch("This server's ground wears through to different materials than your config draws.");
        }
        if (ServerRules.overrode() > 0 && restitchWanted == null) {
            // What a worn block draws is worked out from the live settings at the moment a chunk's
            // mesh is built, so geometry that moved under an already-built chunk goes on showing
            // the old picture until the meshes are built again. The stitch above is about the
            // atlas and deliberately says nothing about this - which the old stage-count stitch
            // hid, because it ended in this same call and fired on very nearly everything.
            // Cheap beside a stitch: no atlas is touched, and it is skipped outright when a
            // stitch has just been queued, since that ends here anyway a few ticks later.
            net.minecraft.client.renderer.RenderGlobal renderGlobal = Minecraft.getMinecraft().renderGlobal;
            if (renderGlobal != null) renderGlobal.loadRenderers();
        }
    }

    /**
     * A server named the surface table it is using. Asks for it when it is not the one in use here.
     *
     * <p>
     * Never on a host: a single-player or LAN host shares its server's table outright. A fingerprint
     * that matches what is in use costs nothing; one that matches this client's own file while a
     * server's table is held means the server has come round to it, and the visit's table is handed
     * back. Anything else is asked for once, and not again until a different fingerprint arrives.
     */
    @Override
    public void considerServerTable(boolean sent, long fingerprint) {
        if (!sent || Trmt.runningServer() != null) return;
        serverNamedTable = true;
        serverTable = fingerprint;
        boolean holding = SurfaceRegistry.holdingServerTable();
        if (holding && SurfaceRegistry.heldFingerprint() == fingerprint) return;
        if (fingerprint == SurfaceRegistry.ownFingerprint()) {
            if (holding) handBackServerTable();
            return;
        }
        if (tableAskedFor == fingerprint) return;
        tableAskedFor = fingerprint;
        TrmtNetwork.requestSurfaceTable(fingerprint);
    }

    /**
     * Puts a server's surface table in use for the visit.
     *
     * <p>
     * Its drawing first taken off the world, then the table swapped, then everything derived from the
     * table re-stamped and the ground painted again under the new one. Nothing is stitched: a block
     * this client never planned wear pictures for wears with its family's generic art for the visit,
     * which was decided rather than defaulted - joining a server should never stall for half a minute
     * to make a few blocks look a little more like themselves.
     *
     * <p>
     * Refused rather than held when it is not the table the rules last named. It is then the answer to an
     * older announcement that crossed with a newer one, and the newer one has already been dealt with -
     * holding it would put back a table the server has since left. And a table that cannot be used, from
     * a build that lays tables out differently or knows a surface this one does not, stays remembered as
     * asked for. Forgetting it had the whole table sent again with every rules the server sent, to fail
     * again each time.
     */
    @Override
    public void installServerTable(long fingerprint, boolean holdsSwitch, byte[] bytes) {
        if (Trmt.runningServer() != null) return;
        if (!serverNamedTable || fingerprint != serverTable) {
            // Not remembered as asked for, so a server that comes back to this table is asked for it again.
            if (tableAskedFor == fingerprint) tableAskedFor = 0L;
            return;
        }
        com.trmtgtnh.surface.SurfaceTableCodec.Table decoded;
        try {
            decoded = com.trmtgtnh.surface.SurfaceTableCodec.decode(bytes);
        } catch (IllegalArgumentException malformed) {
            tableAskedFor = fingerprint;
            Trmt.LOG.warn("This server's surface table could not be read; keeping your own", malformed);
            return;
        }
        OverlayPainter painter = OverlayPainter.get();
        painter.restoreAll();
        if (!SurfaceRegistry.holdServerTable(decoded, holdsSwitch, fingerprint)) {
            // Nothing was held, so the table in use is still this client's own; the ground is only put
            // back as it was.
            tableAskedFor = fingerprint;
            Trmt.LOG.warn("This server's surface table names a surface this build does not have; keeping your own");
            surfacesMoved();
            return;
        }
        // Forgotten only once the table is in use, so a server that leaves it and comes back is asked again.
        tableAskedFor = 0L;
        surfacesMoved();
        Trmt.LOG.info(
            "Using this server's surface table for the visit: {} block states",
            Integer.valueOf(decoded.families.size()));
    }

    /**
     * Compares this client's own table with the server's again, after something here rebuilt it.
     *
     * <p>
     * The comparison otherwise runs only when rules arrive, and a player whose table matched at join
     * holds nothing. So an edit made on the config screen mid-visit - a block added to a family, one
     * excluded, the plant-hold switch - published a table the server was not using, and nothing ever
     * looked again: the ground was drawn and collided with by the client's own table until the server
     * happened to send its rules for some other reason. A client already holding the server's table
     * is untouched by this, because its rebuilds publish the held table in any case.
     */
    private void recheckServerTable() {
        if (serverNamedTable) considerServerTable(true, serverTable);
    }

    /** Stops using a server's table and goes back to this client's own. */
    private void handBackServerTable() {
        if (!SurfaceRegistry.releaseServerTable()) return;
        OverlayPainter.get()
            .restoreAll();
        SurfaceRegistry.resolve();
        surfacesMoved();
    }

    /** Everything derived from the published table, re-stamped after it moved, and the ground repainted. */
    private void surfacesMoved() {
        PhysicalDecay.markSinkableBlocks();
        markSettlingBlocks();
        com.trmtgtnh.block.GhostInherit.survey();
        com.trmtgtnh.client.gui.WearIcons.reset();
        if (TrmtConfig.enabled && TrmtConfig.overlayVisible()) {
            OverlayPainter.get()
                .repaintAll();
        }
    }

    /**
     * Rebuilds the surface table and everything derived from it, for a family that changed hands.
     *
     * <p>
     * The head of what a config reload does, and it is needed here for one reason: whether a family
     * wears at all is decided when the table is built, and the table drops every block of a family
     * whose switch is off. So a client that had turned a family off, joining a server that has not,
     * gained a chain for that family and still could not place a single one of its records - the
     * painter asks the table what the ground is before it asks the chain anything, and the table had
     * never heard of it. Sending the switch without this was sending a fact nothing could act on.
     *
     * <p>
     * All four calls rather than the first, because the three after it are derived from the table
     * the first one replaces, and each has its own way of going quietly wrong. Without the sinkable
     * stamps the collision path dismisses the newly-known blocks on a single field read, so the
     * client walks over the top of a rut the server keeps dropping it into - which is the exact
     * desync this whole class exists to prevent. Without the settling and inheritance surveys the
     * ghosts covering those blocks lose the behaviour they are supposed to borrow from them.
     *
     * <p>
     * Hung on {@link ServerRules#enabledMovedFrom} rather than on the atlas question beside it,
     * which is a correction rather than a nicety. The two were briefly the same test, on the
     * reasoning that a family switched off has an empty chain and so moves the appearance set - and
     * that is true only of a staged family. Leaves and vegetation are never staged, so their chains
     * are empty either way and their switch moved nothing the atlas test could see, while the table
     * is pruned by that switch regardless. That table entry is the only gate on whether a route
     * breaks the plants standing in it, so the miss ran the wrong way: a visit could hand a player
     * back a table that tramples ground their own file had said to leave alone.
     */
    private void resettleSurfaces() {
        SurfaceRegistry.resolve();
        PhysicalDecay.markSinkableBlocks();
        markSettlingBlocks();
        com.trmtgtnh.block.GhostInherit.survey();
    }

    /**
     * The same rebuild as a server's, run where it is safe to run it.
     *
     * <p>
     * A client sees the ids move on three threads. A world opened in single player takes its ids and
     * gives them back on its own server thread, and both are rebuilt where they stand, as a reload in
     * single player already is. A visit to somebody else's server takes them on the network thread,
     * where rebuilding would race the painter, so that one is queued - ahead of the rules and the
     * ground the same join is about to send, which are queued behind it - and gives them back on this
     * one as the world closes, rebuilt where it stands. Nothing is repainted, because no world is
     * painted at any of those moments.
     *
     * <p>
     * The wear atlas needs nothing here: it is filed by block object, which no move renumbers, and the
     * line queued after the rebuild only says how many blocks with wear of their own now carry an id other
     * than the one they had when the atlas was stitched, and whether each still reads back as itself; a
     * move back to the ids the atlas was stitched under therefore reports none.
     */
    @Override
    public void idsMoved() {
        boolean inPlace = Minecraft.getMinecraft()
            .func_152345_ab()
            || cpw.mods.fml.common.FMLCommonHandler.instance()
                .getEffectiveSide() == cpw.mods.fml.relauncher.Side.SERVER;
        Runnable rebuild = new Runnable() {

            @Override
            public void run() {
                Trmt.LOG.info("Block ids changed; rebuilding the surface table under them");
                resettleSurfaces();
            }
        };
        try {
            if (inPlace) {
                rebuild.run();
            } else {
                MainThread.onClient(rebuild);
            }
        } finally {
            // Queued whatever the rebuild did. A rebuild run where it stands can throw, and the throw
            // still goes on to Forge as it always has; it must not also take the icons' reset and the
            // atlas's audit with it.
            //
            // The icons are a screen's, and are only ever let go of on the thread that draws them.
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    com.trmtgtnh.client.gui.WearIcons.reset();
                }
            });
            // Behind a queued rebuild in the same queue, so its line follows the one saying the ids
            // changed, and on this client's own thread, the one that stitches, because the ids it
            // compares against were written down by the stitch and are read nowhere else.
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    WearTextures.auditAfterIdMove();
                }
            });
        }
    }

    /**
     * Says once a visit that part of what was just typed belongs to the server today.
     *
     * <p>
     * Worth saying, because the edit looks as though it did nothing. It did not: Forge has already
     * written it into the file, and it takes effect the moment this player leaves. Only the geometry
     * is borrowed, and only while connected.
     */
    @Override
    public void tellServerOwnsGeometry() {
        tell(
            EnumChatFormatting.GRAY
                + "[TRMT] How deep and how finely ground wears here is this server's to set, so those stay as it sent them. Your change is saved and takes effect as soon as you leave.");
    }

    private static void tell(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText(message));
    }

    /** What the server last said about this client's privileges. Display only. */
    private boolean mayEditFamilies;

    @Override
    public void setMayEditFamilies(boolean allowed) {
        mayEditFamilies = allowed;
    }

    @Override
    public boolean mayEditFamilies() {
        return mayEditFamilies;
    }

    /** The last pricing this client was told the server uses, for the Wear Table's diff. */
    private com.trmtgtnh.erosion.WearMath.Pricing serverPricing;

    @Override
    public void setServerPricing(com.trmtgtnh.erosion.WearMath.Pricing pricing) {
        serverPricing = pricing;
    }

    @Override
    public com.trmtgtnh.erosion.WearMath.Pricing serverPricing() {
        return serverPricing;
    }

    /** What the modifier was last tick, so a change can be spotted and only a change is sent. */
    private boolean modifierWasDown;

    /**
     * The surface table last asked of the server, by fingerprint, so one is never asked for twice. Kept
     * after a table that arrived and could not be used, and forgotten once a table is in use.
     */
    private long tableAskedFor;

    /**
     * The surface table the server last named, by fingerprint, kept so a rebuild made here mid-visit
     * can be compared with it again. Meaningless until {@link #serverNamedTable} says a server has named
     * one this visit, which an older server never does.
     */
    private long serverTable;

    /** Whether the server has named its surface table this visit. */
    private boolean serverNamedTable;

    @Override
    public boolean modifierHeld() {
        return net.minecraft.client.gui.GuiScreen.isCtrlKeyDown();
    }

    @Override
    public void openTamperScreen(net.minecraft.item.ItemStack stack) {
        if (stack == null) return;
        net.minecraft.client.Minecraft.getMinecraft()
            .displayGuiScreen(new com.trmtgtnh.client.gui.GuiTamper(stack));
    }

    @Override
    public void handleClearAll() {
        OverlayPainter.get()
            .restoreAll();
        ClientErosionCache.get()
            .clear();
        ClientLightCache.get()
            .clear();
    }

    @Override
    public boolean overlayActive() {
        return TrmtConfig.overlayVisible();
    }

    @Override
    public Block originBlockAt(int x, int y, int z) {
        return GhostRendering.originBlock(
            ClientErosionCache.get()
                .originAt(x, y, z));
    }

    @Override
    public short erosionStateAt(int x, int y, int z) {
        ClientErosionCache.ChunkOverlay overlay = ClientErosionCache.get()
            .overlay(x >> 4, z >> 4);
        if (overlay == null) return com.trmtgtnh.erosion.ErosionState.NONE;
        return overlay.stateAt(ErosionKey.packWorld(x, y, z));
    }

    @Override
    public double settledDropUnder(int x, int y, int z) {
        net.minecraft.client.multiplayer.WorldClient world = Minecraft.getMinecraft().theWorld;
        return world == null ? 0.0D : com.trmtgtnh.client.render.Settling.dropFor(world, x, y + 1, z);
    }

    @Override
    public void markSettlingBlocks() {
        com.trmtgtnh.client.render.Settling.markSettlingBlocks();
    }

    @Override
    public int originPackedAt(int x, int y, int z) {
        return ClientErosionCache.get()
            .originAt(x, y, z);
    }

    @Override
    public void onConfigChanged(final ConfigReload.Delta delta) {
        if (delta == null || !delta.any()) return;
        // A reload runs wherever it was asked for, and in single player that is the server
        // thread. Everything below belongs to the client: its world, its painter, its chat.
        MainThread.onClient(new Runnable() {

            @Override
            public void run() {
                applyLocally(delta);
            }
        });
    }

    private void applyLocally(ConfigReload.Delta delta) {
        lastShowErosion = TrmtConfig.showErosion;

        if (delta.textures) {
            requestRestitch("Wear textures changed.");
        }

        if (delta.showErosionMoved && !TrmtConfig.showErosion && TrmtConfig.overlayForced) {
            tell(
                EnumChatFormatting.YELLOW
                    + "[TRMT] This server has worn ground you can walk down into, so path visuals cannot be switched off here. Your setting will apply everywhere else.");
        }

        if (delta.needsRepaint()) {
            // Always off before on. Painting is skipped where a ghost already stands, so a block
            // that has just stopped being tracked would otherwise keep the one it has forever.
            OverlayPainter painter = OverlayPainter.get();
            painter.restoreAll();
            if (TrmtConfig.enabled && TrmtConfig.overlayVisible()) painter.repaintAll();
        }

        // The server stops spending bandwidth on a player who cannot see the result.
        // The player's own choice, as the first hello sends, rather than what is visible. Visibility
        // includes the server's own forcing, and a server that remembered that as the player's wish
        // kept sending wear to somebody who had switched it off, long after its ruts stopped being real.
        // The server applies its own forcing when it answers.
        if (delta.showErosionMoved && announced) TrmtNetwork.sendHello(TrmtConfig.showErosion);

        // Last, because the table this asks for is installed from the queue and repaints on its own.
        recheckServerTable();
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != 0) return; // blocks atlas only
        WearTextures.registerAll(event.map);
    }

    /**
     * Reports whether the hooks the wear textures depend on ran. Said from TextureStitchEvent.Post, which is a Forge
     * event rather than an injection and so cannot go missing the way the injections can; what the sprite pass built,
     * guessed at or could not read, it says itself as it ends.
     */
    @SubscribeEvent
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() != 0) return;
        WearTextures.reportStitchHooks();
    }

    /**
     * Drops an effect this client has no potion for, before the game trips over it.
     *
     * <p>
     * Registered here rather than beside the wear hooks because it is a client concern entirely -
     * the machine that chose the number is the other one. See ModPotions for what it prevents,
     * which is a crash inside the world tick rather than anything cosmetic. Cheap on the hot path:
     * an entity with no effects at all costs one map read and a test for empty, which is what the
     * overwhelming majority of them are.
     */
    @SubscribeEvent
    public void onLivingUpdateClient(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        com.trmtgtnh.item.ModPotions.guardAgainstUnknownEffects(event.entityLiving);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MainThread.drainClient();
        // The hold a ghost takes while its breaking dust is spawned, let go of here. A tick is the
        // smallest window that covers the spawn, because the particles are built after the block
        // has finished answering rather than during it.
        com.trmtgtnh.block.GhostRendering.releaseShadeForDust();
        com.trmtgtnh.client.texture.InnerLayers.newTick();
        // Above the world check on purpose: a look changed at the main menu has to take there too,
        // since going into a world stitches nothing.
        serviceRestitch();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        if (!announced) {
            announced = true;
            TrmtNetwork.sendHello(TrmtConfig.showErosion);
        }

        // Only while a tamper is in hand, and only when it changes, so a player who never holds
        // one never sends a byte of this.
        net.minecraft.item.ItemStack held = mc.thePlayer.getHeldItem();
        boolean relevant = held != null && held.getItem() instanceof com.trmtgtnh.item.ItemChunkTamper;
        boolean down = relevant && modifierHeld();
        if (down != modifierWasDown) {
            modifierWasDown = down;
            TrmtNetwork.sendModifier(down);
        }

        OverlayPainter.get()
            .tick();
        // Keeps JourneyMap drawing each worn block in the color of the ground beneath it. A
        // no-op unless JourneyMap is present, and self-healing if it drops the handler.
        com.trmtgtnh.client.journeymap.JourneyMapColors.tick();
        verifyInteraction(mc);
    }

    /**
     * Repaints around whatever the player is aiming at.
     *
     * <p>
     * Digging and placing both make the server send the affected blocks back to the client,
     * which wipes any ghost standing on them. Those are precisely the blocks under the
     * crosshair and its immediate neighbours, so checking a handful of positions per tick
     * covers every case a player will actually notice, for the cost of seven lookups.
     */
    private void verifyInteraction(Minecraft mc) {
        MovingObjectPosition target = mc.objectMouseOver;
        if (target == null || target.typeOfHit != MovingObjectType.BLOCK || mc.theWorld == null) {
            // Nothing under the crosshair to ask about, so nothing is held about the last thing that was.
            InspectionCache.clear();
            return;
        }

        OverlayPainter painter = OverlayPainter.get();
        int x = target.blockX;
        int y = target.blockY;
        int z = target.blockZ;

        painter.verifyPosition(mc.theWorld, x, y, z);
        // The crosshair is also the only place a tooltip can be asking about, so this is where the
        // server's numbers get fetched - and only while Waila, the one reader, is installed. Worn ground
        // is asked about the moment its ghost is in view. A reinforcement or a spawn ward can sit on a
        // block that shows nothing, so while either feature is on the crosshair block is asked about
        // whatever it is; the server only answers when it has a record, so a plain block costs a request
        // and no reply. Asking only while reinforcement was on left a client with wards alone never
        // asking about an unworn floor.
        if (com.trmtgtnh.util.InspectionReach.asks(
            inspectionRead,
            mc.theWorld.getBlock(x, y, z) instanceof com.trmtgtnh.block.GhostBlock,
            TrmtConfig.reinforceEnabled,
            TrmtConfig.wardEnabled)) {
            InspectionCache.poll(x, y, z);
        } else {
            InspectionCache.clear();
        }
        painter.verifyPosition(mc.theWorld, x, y - 1, z);
        painter.verifyPosition(mc.theWorld, x, y + 1, z);
        painter.verifyPosition(mc.theWorld, x - 1, y, z);
        painter.verifyPosition(mc.theWorld, x + 1, y, z);
        painter.verifyPosition(mc.theWorld, x, y, z - 1);
        painter.verifyPosition(mc.theWorld, x, y, z + 1);
    }

    @Override
    public void acceptInspection(int x, int y, int z, float wear, float threshold, int untouchedSeconds,
        int recoverySeconds, int chainIndex, int chainLength, int reinforce, int ward) {
        InspectionCache.accept(
            x,
            y,
            z,
            wear,
            threshold,
            untouchedSeconds,
            recoverySeconds,
            chainIndex,
            chainLength,
            reinforce,
            ward);
    }

    @Override
    public Object golemScreen(net.minecraft.entity.player.EntityPlayer player,
        com.trmtgtnh.entity.EntityGolemOfWays golem) {
        return new com.trmtgtnh.client.gui.GuiGolem(player.inventory, golem);
    }

    /** The entity on both sides, and on this one its renderer too. */
    @Override
    public void registerEntities() {
        super.registerEntities();
        cpw.mods.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
            com.trmtgtnh.entity.EntityGolemOfWays.class,
            new com.trmtgtnh.client.render.RenderGolemOfWays());
    }

    @Override
    public void openGuideScreen(int bookOrdinal) {
        Minecraft.getMinecraft()
            .displayGuiScreen(
                new com.trmtgtnh.client.gui.GuiGuideBook(com.trmtgtnh.item.GuideBook.byOrdinal(bookOrdinal)));
    }

    @Override
    public void openSnapshotScreen(int flags) {
        Minecraft mc = Minecraft.getMinecraft();
        // Already open: refresh it in place, so pressing a button does not blink the screen.
        if (mc.currentScreen instanceof com.trmtgtnh.client.gui.GuiSnapshot) {
            ((com.trmtgtnh.client.gui.GuiSnapshot) mc.currentScreen).update(flags);
            return;
        }
        mc.displayGuiScreen(new com.trmtgtnh.client.gui.GuiSnapshot(flags));
    }

    @Override
    public void requestSnapshotScreen() {
        TrmtNetwork.sendSnapshotAction(com.trmtgtnh.network.PacketSnapshotAction.REQUEST);
    }

    /**
     * Swaps the hard surfaces' wear patterns and reloads, so the difference can be looked at.
     *
     * <p>
     * In memory only. The sprites are baked while the atlas is stitched, so the swap needs a
     * resource reload to show - and that same reload is what puts the configured values back the
     * next time one happens for any other reason. Nothing here is written to disk, which is what
     * keeps a preview a preview.
     */
    @Override
    public void applyDevPreview(int mode) {
        String pattern = mode == com.trmtgtnh.item.ItemDevTool.PREVIEW_OLD ? FamilySettings.PATTERN_SMOOTH
            : FamilySettings.PATTERN_CRACK;
        SurfaceFamily[] hard = { SurfaceFamily.COBBLE, SurfaceFamily.STONE, SurfaceFamily.NETHER, SurfaceFamily.END };
        for (SurfaceFamily family : hard) {
            FamilySettings settings = TrmtConfig.family(family);
            if (settings != null) settings.wearPattern = pattern;
        }
        restitchBlocks();
    }

    /**
     * Sets one family's wear look for this client, and remembers it.
     *
     * <p>
     * Written into this client's own config file rather than only into the settings in memory,
     * because that map is rebuilt wholesale from the file on disconnect and again whenever the Forge
     * config screen is closed - and a preference that vanished on either would read as the game
     * forgetting it.
     *
     * <p>
     * Deliberately not routed through the config reload. On a client attached to somebody else's
     * server that runs inline and re-reads the file, which throws away the stage and sink figures
     * the server installed for the session, so the client would go on predicting rut floors at the
     * wrong height until it relogged. Only one value is being changed and only the picture depends
     * on it, so it is set and saved directly.
     *
     * <p>
     * The property that is already declared, rather than a fresh one: re-declaring it here would
     * lose the comment and the list of valid looks that the settings screen reads off it.
     */
    @Override
    public void setWearLook(SurfaceFamily family, String pattern) {
        FamilySettings settings = TrmtConfig.family(family);
        if (settings == null || pattern == null || pattern.equals(settings.wearPattern)) return;
        settings.wearPattern = pattern;

        Configuration config = TrmtConfig.raw();
        if (config != null) {
            Property property = config
                .getCategory(TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER + family.key())
                .get("wearPattern");
            if (property != null) {
                property.set(pattern);
                TrmtConfig.save();
            }
        }
        if (TrmtConfig.isPoisoned()) {
            // Saving refuses to write a file assembled from a bad read, so without this the look
            // would apply now and be gone at the next start with nothing said about it.
            tell(
                EnumChatFormatting.YELLOW
                    + "[TRMT] Your look changed for now, but the config file failed to read, so it will not be kept. Fix it and use /trmt reload.");
        }
        requestRestitch("Your wear look changed.");
    }

    /**
     * Asks for the block atlas to be rebuilt, shortly rather than now.
     *
     * <p>
     * Everything a wear texture is made of is read while the atlas is being stitched, and nothing
     * outside a stitch can change one - so a setting that decides what a sprite looks like does
     * nothing whatever until the next one. For a long time this mod answered that by asking the
     * player to press F3+T, which is a debug keybinding, reloads every pack, language, sound and
     * atlas in the game rather than the one that matters, and is not a thing anybody should have to
     * know to make a setting they just changed take effect.
     */
    private void requestRestitch(String reason) {
        if (restitchWanted == null) Trmt.LOG.info("Rebuilding the block atlas: {}", reason);
        restitchWanted = reason;
        restitchIn = 0;
    }

    /**
     * Runs a pending rebuild once the countdown is out and the moment is right.
     *
     * <p>
     * Held while one of this mod's own screens is open, and that is the case worth guarding for.
     * Neither of them pauses the game, so unlike every other way into this the work would land with
     * the player still looking at the editor and part way through a set of changes. Closing it is
     * the moment to spend the time, and holding also collapses a whole session of edits into one
     * rebuild rather than one apiece.
     *
     * <p>
     * Held as well while the block ids may still be moving on another thread with no world loaded
     * here, for the reason given where that hold is taken.
     *
     * <p>
     * The warning is given here rather than where the rebuild is asked for, so that it is given at
     * a moment somebody can read it. Any of those holds can last long past the moment the rebuild
     * was asked for, and a notice that the game is about to stop responding is worth nothing if it
     * was printed a minute earlier into a screen that was covering it - or, on the join path, before
     * there was a player to print to.
     */
    private void serviceRestitch() {
        if (restitchWanted == null) return;
        Minecraft mc = Minecraft.getMinecraft();

        net.minecraft.client.gui.GuiScreen screen = mc.currentScreen;
        if (screen instanceof com.trmtgtnh.client.gui.GuiWearEditor
            || screen instanceof com.trmtgtnh.client.gui.GuiWearTable) {
            restitchIn = 0;
            return;
        }
        // A world part way through loading has nobody to warn yet, and the settings a server sends
        // on join are one of the ways in here - so that arrival would otherwise be a silent
        // half-minute freeze in the middle of joining, which reads as a crash.
        if (mc.theWorld != null && mc.thePlayer == null) {
            restitchIn = 0;
            return;
        }
        // Nor, with no world loaded, while the block ids may still be moving on another thread. A
        // server's ids arrive on the network thread while the connecting screen is up, and a
        // single-player world's are put back on its server thread after this client has already
        // been let go of it. The atlas is filed by block object and minds neither, but the planner
        // still classifies every block through the surface table and asks each for its item, both
        // by id, so a stitch straddling either move plans part of the pack under one numbering and
        // the rest under the other: family art where a block's own was due, until the next stitch.
        // The same test holds a single-player world between its server starting and the world
        // arriving here, which spares that join the freeze the hold above spares a server's.
        if (mc.theWorld == null
            && (screen instanceof net.minecraft.client.multiplayer.GuiConnecting || Trmt.serverThreadAlive())) {
            restitchIn = 0;
            return;
        }

        if (restitchIn <= 0) {
            tell(
                EnumChatFormatting.GRAY + "[TRMT] "
                    + restitchWanted
                    + " Rebuilding them now - the game will stop responding for up to half a minute.");
            restitchIn = RESTITCH_DELAY;
            return;
        }
        if (--restitchIn > 0) return;
        restitchWanted = null;
        restitchBlocks();
    }

    /**
     * Re-stitches the block atlas and rebuilds the chunk meshes, and nothing else.
     *
     * <p>
     * {@code refreshResources} was the obvious way to make a texture change show, and it is far
     * more than is wanted: it reloads every resource pack from disk and rebuilds the languages,
     * the sounds, the models and every other atlas, which is why flipping the comparison took
     * seconds. The wear sprites live on the block atlas alone, so only that is asked to reload,
     * and then the renderer is asked to build its chunks again so the new sprites are actually
     * drawn.
     */
    private void restitchBlocks() {
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.renderer.texture.ITextureObject blocks = mc.getTextureManager()
            .getTexture(net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture);
        if (blocks == null) {
            mc.refreshResources();
            return;
        }
        try {
            blocks.loadTexture(mc.getResourceManager());
        } catch (java.io.IOException failed) {
            Trmt.LOG.debug("Could not re-stitch the block atlas; reloading everything instead", failed);
            mc.refreshResources();
            return;
        } catch (RuntimeException failed) {
            // Not cosmetic, unlike almost everything else this queue runs, which is why it is caught
            // here rather than left to the queue's own warning. Loading a texture deletes the
            // atlas's GL name before it starts building the replacement, so a stitch that throws
            // half way leaves every block in the game untextured with one line in the log. At
            // startup the same throw becomes a crash report; this path bypasses that.
            Trmt.LOG.error("Re-stitching the block atlas failed; reloading every resource instead", failed);
            tell(
                EnumChatFormatting.RED
                    + "[TRMT] Wear textures could not be rebuilt. Reloading resources - if the world still looks wrong, restart the game.");
            mc.refreshResources();
            return;
        }
        if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
    }

    /** A chunk arriving is the moment its cached overlay can finally be applied. */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        Chunk chunk = event.getChunk();
        if (chunk == null || chunk.worldObj == null || !chunk.worldObj.isRemote) return;
        if (ClientErosionCache.get()
            .overlay(chunk.xPosition, chunk.zPosition) == null) return;
        OverlayPainter.get()
            .queueChunk(chunk.xPosition, chunk.zPosition);
    }

    /**
     * A chunk leaving takes its overlay with it. Nothing needs restoring: the client is
     * discarding that copy of the world wholesale, and the server resends on the way back.
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        Chunk chunk = event.getChunk();
        if (chunk == null || chunk.worldObj == null || !chunk.worldObj.isRemote) return;
        ClientErosionCache.get()
            .remove(chunk.xPosition, chunk.zPosition);
        // Dropped alongside the wear, or a chunk that lost its lights while unloaded would come
        // back still glowing: a chunk with nothing lit in it sends no light packet to say so.
        ClientLightCache.get()
            .remove(chunk.xPosition, chunk.zPosition);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world == null || !event.world.isRemote) return;
        ClientErosionCache.get()
            .clear();
        ClientLightCache.get()
            .clear();
        OverlayPainter.get()
            .clearQueue();
    }

    /**
     * Hands this client back its own settings when it leaves a server, on the client thread.
     *
     * <p>
     * Forge announces a disconnect from the network thread, and everything the hand-back does belongs
     * to the thread that ticks and draws the world: it rebuilds the wear chains and the surface table,
     * re-stamps block fields that collision reads, and empties caches the painter is walking. Done
     * there directly, a kick or a timeout rebuilt all of that under a frame still being drawn from
     * it. Queued instead - and the queue is drained every client tick whether or not a world is loaded
     * - so it runs a tick later, on the right thread, and after anything the old server had already
     * sent, which is the order a hand-back wants. Forge can announce the same disconnect twice; the
     * second run finds nothing left to move.
     */
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        MainThread.onClient(new Runnable() {

            @Override
            public void run() {
                handBack();
            }
        });
    }

    private void handBack() {
        announced = false;
        // The server keeps the modifier per player entity, and a new connection is a new entity
        // that believes it is up. Forgetting it here makes the next tick send it again if the key
        // is still held, rather than a settings click arriving at the server as a mend.
        modifierWasDown = false;
        serverPricing = null;
        com.trmtgtnh.client.journeymap.JourneyMapColors.reset();
        com.trmtgtnh.client.xaero.XaeroMinimap.reset();
        InspectionCache.clear();
        // Hand this client back its own settings: the server's only applied while connected.
        // Released before the read rather than after, because this read is the designed hand-back
        // and the one read in the mod that must not have a server's numbers put back into it.
        // Taking the appearance sets first is the only honest way to learn whether they are about
        // to move: the held bytes say what the server wanted, not what this client's own file
        // says, and only rebuilding the chains out of that file answers it.
        int[] appearancesUnderServer = ServerRules.chainAppearances();
        ServerRules.release();
        // Before the read and the rebuild below, so the rebuild publishes this client's own table
        // rather than publishing the visit's again.
        boolean tableHandedBack = SurfaceRegistry.releaseServerTable();
        tableAskedFor = 0L;
        serverNamedTable = false;
        serverTable = 0L;
        TrmtConfig.read();
        boolean appearancesHandedBack = ServerRules.appearancesMovedFrom(appearancesUnderServer);
        boolean enabledHandedBack = ServerRules.enabledMovedFrom(appearancesUnderServer);
        ClientErosionCache.get()
            .clear();
        ClientLightCache.get()
            .clear();
        OverlayPainter.get()
            .clearQueue();
        // The queue itself is left alone: this is running from it, and what is still in it arrived
        // after the disconnect was announced. The server's half is cleared when that server stops.
        // The mirror of the stitch asked for on the way in, and corrected the same way. The
        // direction that matters here is an appearance this client's own file reaches that the
        // server's chain never did: nothing was planned for it, and nothing will be found for it
        // either, because the family fallback every lookup falls through to is laid out from the
        // chain as well - so that ground would quietly stop showing any wear at the point its run
        // reached the missing material. Safe after the clear: asking for a stitch only sets a flag
        // and a reason for the tick loop to find.
        // The mirror of the join, and it has to be here rather than left to the next reload: a
        // family this client had switched off can have been resolved into its table for the
        // visit, and leaving it there afterwards would go on trampling ground the player's own
        // file had said to leave alone. Asked of the switches alone, like the join.
        if (enabledHandedBack || tableHandedBack) resettleSurfaces();
        if (tableHandedBack) com.trmtgtnh.client.gui.WearIcons.reset();
        if (appearancesHandedBack) requestRestitch("Back on the materials your own config draws.");
        Trmt.LOG.debug("Disconnected; overlay cache cleared");
    }

}
