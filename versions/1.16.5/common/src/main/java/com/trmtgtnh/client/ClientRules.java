package com.trmtgtnh.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import com.trmtgtnh.config.ServerRules;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * What a server has decided for the length of a visit, and the surface table it sends.
 *
 * <p>
 * A server may force the overlay on, name a decay mode, fix the geometry, price wear its own way and
 * hand over its whole surface table; all of it lasts until the client leaves. Held here rather than
 * written into the client's own settings, which are its own and are waiting unchanged when it gets
 * home.
 *
 * <p>
 * Carried from the 1.12.2 edition's client proxy, which has no counterpart here - and carried again in
 * 0.9.219, after that edition's was found to have kept only the first clause of each of the 1.7.10
 * edition's methods. This copy had the same holes: a visit drew by this client's own chains and decay
 * mode whatever the server said, a forced overlay was never taken down or explained, the server's table
 * was installed over ground painted under this client's own and fetched again with every announcement,
 * and ground already built went on showing geometry the server had since changed.
 */
public final class ClientRules {

    private static final ClientRules INSTANCE = new ClientRules();

    private ClientRules() {}

    public static ClientRules get() {
        return INSTANCE;
    }

    private long serverNamedTable;

    private long tableAskedFor;

    private boolean mayEditFamilies;

    private com.trmtgtnh.erosion.WearMath.Pricing serverPricing;

    /**
     * Holds a server's rules for the visit, and carries out what they change here. The 1.7.10 edition's
     * method line for line.
     */
    public void applyServerRules(boolean forceOverlay, String decayMode,
        com.trmtgtnh.config.ServerRules.Geometry geometry) {
        boolean wasForced = TrmtConfig.overlayForced;
        // Taken before the rules land, because this is the last moment the chains still describe this
        // client's own file. What has to be asked is which materials a run passes through, and nothing but
        // building the chains says that.
        int[] before = ServerRules.chainAppearances();
        ServerRules.hold(forceOverlay, decayMode, geometry);

        com.trmtgtnh.erosion.ErosionChain.rebuild();
        com.trmtgtnh.erosion.PhysicalDecay.refresh();

        if (forceOverlay && !wasForced && !TrmtConfig.showErosion) {
            OverlayPainter.get()
                .repaintAll();
            tell(ChatFormatting.YELLOW, "This server has worn ground you can walk down into, so path visuals stay on here.");
        }
        if (wasForced && !forceOverlay && !TrmtConfig.showErosion) {
            // Reloading a server's config can stop it needing real ruts, and a player who never wanted
            // overlays should not be left holding the ones it made them show.
            OverlayPainter.get()
                .restoreAll();
            tell(ChatFormatting.GRAY, "Path visuals are back under your own setting on this server.");
        }
        if (ServerRules.enabledMovedFrom(before)) {
            // Before the stitch below rather than after it: a family arriving is a family the atlas has
            // never planned pictures for, and the planner reads the table this rebuilds.
            resettleSurfaces();
        }
        if (ServerRules.appearancesMovedFrom(before)) {
            // A surface whose run now visits gravel on a client that never planned gravel would stop
            // showing any wear at the point it reached it.
            WearRestitch.get()
                .requestRestitch("This server's ground wears through to different materials than your config draws.");
        }
        if (ServerRules.overrode() > 0 && !WearRestitch.get()
            .pending()) {
            // What a worn block draws, and how deep, is worked out from the live settings as a chunk's mesh
            // is built, so geometry that moved under an already-built chunk goes on showing the old picture
            // until the meshes are built again. Skipped when a rebuild of the pictures is already waiting,
            // since that ends in the same place.
            Minecraft client = Minecraft.getInstance();
            if (client.levelRenderer != null) client.levelRenderer.allChanged();
        }
    }

    /**
     * A server named the surface table it is using. Asks for it when it is not the one in use here.
     *
     * <p>
     * Never on a host: a single-player or LAN host shares its server's table outright. A fingerprint that
     * matches what is in use costs nothing; one that matches this client's own file while a server's table
     * is held means the server has come round to it, and the visit's table is handed back. Anything else
     * is asked for once, and not again until a different fingerprint arrives.
     */
    public void considerServerTable(boolean sent, long fingerprint) {
        if (!sent || com.trmtgtnh.Trmt.runningServer() != null) return;
        serverNamedTable = fingerprint;
        boolean holding = SurfaceRegistry.holdingServerTable();
        if (holding && SurfaceRegistry.heldFingerprint() == fingerprint) return;
        if (fingerprint == SurfaceRegistry.ownFingerprint()) {
            if (holding) handBackServerTable();
            return;
        }
        if (tableAskedFor == fingerprint) return;
        tableAskedFor = fingerprint;
        com.trmtgtnh.network.TrmtNetwork.requestSurfaceTable(fingerprint);
    }

    /**
     * Puts a server's surface table in use for the visit.
     *
     * <p>
     * Its drawing first taken off the world, then the table swapped, then everything derived from the
     * table re-stamped and the ground painted again under the new one - or a square painted under this
     * client's own table stays painted under a table that has never heard of it. Nothing is stitched: a
     * block this client never planned wear pictures for wears with its family's generic art for the visit.
     *
     * <p>
     * Refused rather than held when it is not the table the rules last named, and a table that cannot be
     * used stays remembered as asked for, so it is not sent again with every announcement to fail again.
     */
    public void installServerTable(long fingerprint, boolean holdsSwitch, byte[] bytes) {
        if (com.trmtgtnh.Trmt.runningServer() != null) return;
        if (serverNamedTable == 0L || fingerprint != serverNamedTable) {
            // Not remembered as asked for, so a server that comes back to this table is asked for it again.
            if (tableAskedFor == fingerprint) tableAskedFor = 0L;
            return;
        }
        com.trmtgtnh.surface.SurfaceTableCodec.Table decoded;
        try {
            decoded = com.trmtgtnh.surface.SurfaceTableCodec.decode(bytes);
        } catch (IllegalArgumentException malformed) {
            tableAskedFor = fingerprint;
            com.trmtgtnh.Trmt.LOG.warn("This server's surface table could not be read; keeping your own", malformed);
            return;
        }
        OverlayPainter painter = OverlayPainter.get();
        painter.restoreAll();
        if (!SurfaceRegistry.holdServerTable(decoded, holdsSwitch, fingerprint)) {
            // Nothing was held, so the table in use is still this client's own; the ground is only put back.
            tableAskedFor = fingerprint;
            com.trmtgtnh.Trmt.LOG
                .warn("This server's surface table names a surface this build does not have; keeping your own");
            surfacesMoved();
            return;
        }
        // Forgotten only once the table is in use, so a server that leaves it and comes back is asked again.
        tableAskedFor = 0L;
        surfacesMoved();
        com.trmtgtnh.Trmt.LOG.info(
            "Using this server's surface table for the visit: {} block states",
            Integer.valueOf(decoded.families.size()));
    }

    /**
     * Compares this client's own table with the server's again, after something here rebuilt it - an edit
     * made on the config screen mid-visit publishes a table the server may not be using.
     */
    public void recheckServerTable() {
        if (serverNamedTable != 0L) considerServerTable(true, serverNamedTable);
    }

    /** Stops using a server's table and goes back to this client's own. */
    private void handBackServerTable() {
        if (!SurfaceRegistry.releaseServerTable()) return;
        OverlayPainter.get()
            .restoreAll();
        SurfaceRegistry.resolve();
        surfacesMoved();
    }

    /**
     * Everything derived from the published table, re-stamped after it moved, and the ground repainted.
     *
     * <p>
     * The 1.7.10 edition's list less its survey of what every ghost class stands in for: this edition's one
     * ghost asks what it covers at every question. The settling stamps come with the sinkable ones.
     */
    static void surfacesMoved() {
        com.trmtgtnh.erosion.PhysicalDecay.markSinkableBlocks();
        com.trmtgtnh.client.gui.WearIcons.reset();
        if (TrmtConfig.enabled && TrmtConfig.overlayVisible()) {
            OverlayPainter.get()
                .repaintAll();
        }
    }

    /**
     * Rebuilds the surface table and what is derived from it, for a family that changed hands.
     *
     * <p>
     * Whether a family wears at all is decided when the table is built, and the table drops every block of
     * a family whose switch is off. {@link SurfaceRegistry#resolve} re-stamps the sinkable and settling
     * blocks as it goes, which the 1.7.10 edition does in three calls.
     */
    static void resettleSurfaces() {
        SurfaceRegistry.resolve();
    }

    /** Forgets everything this visit's server set, for leaving it. */
    void leave() {
        serverNamedTable = 0L;
        tableAskedFor = 0L;
        mayEditFamilies = false;
        serverPricing = null;
    }

    private static void tell(ChatFormatting color, String text) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        com.trmtgtnh.server.Notices.say(client.player, com.trmtgtnh.server.Notices.line(text, color, true));
    }

    public void setMayEditFamilies(boolean allowed) {
        mayEditFamilies = allowed;
    }

    public boolean mayEditFamilies() {
        return mayEditFamilies;
    }

    public void setServerPricing(com.trmtgtnh.erosion.WearMath.Pricing pricing) {
        serverPricing = pricing;
    }

    public com.trmtgtnh.erosion.WearMath.Pricing serverPricing() {
        return serverPricing;
    }
}
