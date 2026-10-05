package com.trmtgtnh.client;



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
 * Carried from the 1.12.2 edition's client proxy, which has no counterpart here.
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

    public void applyServerRules(boolean forceOverlay, String decayMode,
        com.trmtgtnh.config.ServerRules.Geometry geometry) {
        // Taken before and compared after, because what matters is not that the rules differ but that they
        // send a chain somewhere this client has no pictures for: a surface whose run now visits gravel on a
        // client that never planned gravel would stop showing any wear at the point it reached it.
        int[] before = com.trmtgtnh.config.ServerRules.chainAppearances();
        com.trmtgtnh.config.ServerRules.hold(forceOverlay, decayMode, geometry);
        if (com.trmtgtnh.config.ServerRules.appearancesMovedFrom(before)) {
            WearRestitch.get()
                .requestRestitch("This server's ground wears through into surfaces yours does not.");
        }
    }

    /**
     * Asks for the server's surface table, once per table.
     *
     * <p>
     * A client asks only when the fingerprint it has been told about is one it has not already asked
     * for, because the rules are announced on every reload and an ask per announcement would fetch
     * the same table over and over. The server forgets what it last sent whenever it announces, so
     * an ask that crosses with a table already on its way costs nothing either way.
     */
    public void considerServerTable(boolean sent, long fingerprint) {
        serverNamedTable = sent ? fingerprint : 0L;
        if (!sent || fingerprint == 0L || fingerprint == tableAskedFor) return;
        tableAskedFor = fingerprint;
        com.trmtgtnh.network.TrmtNetwork.requestSurfaceTable(fingerprint);
    }

    /**
     * Takes a server's surface table into use for the visit.
     *
     * <p>
     * Refused for a table this client did not ask for, and for one it cannot read or cannot hold -
     * in every refusal the client keeps its own table, which is what every version before the table
     * existed did anyway. The fingerprint is forgotten again on each refusal, so a server that comes
     * back to the same table is asked for it afresh rather than being assumed to have been answered.
     */
    public void installServerTable(long fingerprint, boolean holdsSwitch, byte[] bytes) {
        if (com.trmtgtnh.Trmt.runningServer() != null) return;
        if (serverNamedTable == 0L || fingerprint != serverNamedTable) {
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
        if (!com.trmtgtnh.surface.SurfaceRegistry.holdServerTable(decoded, holdsSwitch, fingerprint)) {
            tableAskedFor = fingerprint;
            com.trmtgtnh.Trmt.LOG
                .warn("This server's surface table names a surface this build does not have; keeping your own");
            return;
        }
        tableAskedFor = 0L;
        com.trmtgtnh.Trmt.LOG.info(
            "Using this server's surface table for the visit: {} block states",
            Integer.valueOf(decoded.families.size()));
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
