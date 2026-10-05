package com.trmtgtnh;

import net.minecraft.world.level.BlockGetter;

import com.trmtgtnh.config.ServerRules;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.WearMath;

/**
 * Everything a packet arriving on a client asks the client to do.
 *
 * <p>
 * Both older editions reach the client through a Forge proxy: a {@code CommonProxy} of empty methods
 * that a {@code ClientProxy} overrides, with Forge choosing between them at load time. There is no
 * proxy here. Forge at this version chooses by {@code DistExecutor} and Fabric by having a separate
 * entry point altogether, and neither will hand us one object both can produce.
 *
 * <p>
 * So it becomes a seam of the shape the rest of this port already uses - {@code Plants},
 * {@code ModsPresent}, {@code TrmtNetwork}: an interface here, {@code use()} called by whichever
 * module is running on a client, and nothing happening when nothing has.
 *
 * <p>
 * <strong>Nothing happening is the correct behaviour, not a degraded one.</strong> That is what
 * {@code CommonProxy}'s empty bodies did on a dedicated server, which is where most of these packets
 * never arrive in the first place. The warning exists for the other case - a client that has a
 * network but no overlay, which is what this port looks like until the client layer lands - and it
 * is said once rather than per packet, because {@code handleDelta} fires as ground wears under
 * somebody walking.
 *
 * <p>
 * This is deliberately only the thirteen methods the networking layer asks for. The 1.12.2 proxy has
 * about thirty, and the rest are questions the client asks itself - what the overlay should draw,
 * whether a modifier key is held - which belong with the client layer that asks them rather than
 * here. They arrive as that layer does.
 */
public final class Client {

    /** What a client module supplies: somewhere for an arriving packet to put its news. */
    public interface Side {

        // ---- the overlay's own traffic ----

        void handleChunkErosion(int chunkX, int chunkZ, int[] keys, short[] states);

        void handleDelta(int x, int y, int z, short state);

        void handleChunkLight(int chunkX, int chunkZ, int[] keys, byte[] values);

        void handleLightDelta(int x, int y, int z, int packed);

        void handleClearAll();

        // ---- what the server has decided ----

        void applyServerRules(boolean forceOverlay, String decayMode, ServerRules.Geometry geometry);

        void setMayEditFamilies(boolean allowed);

        void setServerPricing(WearMath.Pricing pricing);

        // ---- the surface table, which arrives in two steps ----

        void considerServerTable(boolean sent, long fingerprint);

        void installServerTable(long fingerprint, boolean holdsSwitch, byte[] bytes);

        // ---- screens and readouts ----

        void acceptInspection(int x, int y, int z, float wear, float threshold, int untouchedSeconds,
            int recoverySeconds, int chainIndex, int chainLength, int reinforce, int ward);

        void openSnapshotScreen(int flags);

        /**
         * Opens one of the guide books, by its place in {@code GuideBook}.
         *
         * <p>
         * By ordinal rather than by the enum, because the item that asks lives in the shared module
         * and the screen that answers is a client's - and an ordinal is the one thing both ends
         * already agree about. No packet is involved: the screen opens on whichever side the
         * right-click happened on, and every word it shows is in the language file the client
         * already has. The server's half of that same right-click is {@code BookReading}, which is
         * what records the book as read.
         */
        void openGuideScreen(int bookOrdinal);

        void applyDevPreview(int mode);

        /**
         * Whether the settings modifier is held down on this client's own keyboard.
         *
         * <p>
         * The first of the questions this seam's javadoc said would arrive with the client layer
         * rather than with the network. It is here because only a client can answer it, and only from
         * the keyboard it is actually sitting at: what the server knows is what it was last told.
         */
        boolean modifierHeld();

        /**
         * Whether this server lets the Wayfarer's holder file a block under a family.
         *
         * <p>
         * The reader beside {@link #setMayEditFamilies}, which nothing wanted until the screens
         * arrived: a tamper's screen offers its two editing buttons only where the server has said
         * it may, and what the server said is held on this client.
         */
        boolean mayEditFamilies();

        /**
         * Whether this client's crosshair was on nothing when the swing happened.
         *
         * <p>
         * Only a client knows: what the server has is where the player was looking when it was last
         * told, which is not the same moment. Carried from the other editions' proxies, where it
         * answers the same question off the same field.
         */
        boolean swungAtNothing(net.minecraft.world.entity.player.Player swinger);

        /**
         * The wear numbers this server told this client it uses, or null for nobody's but our own.
         *
         * <p>
         * The wear table draws every figure twice where this is not null - the local number and the
         * server's, with the difference between them in green or red - which is the whole of what
         * that screen is for.
         */
        com.trmtgtnh.erosion.WearMath.Pricing serverPricing();

        /**
         * Sets one family's wear look, writes it to the config and rebuilds the pictures.
         *
         * <p>
         * Only the editor asks, and it asks on the way out with everything it staged. On the seam
         * rather than in the screen because what it does is a config write and a restitch, which are
         * the client layer's and not a screen's.
         */
        void setWearLook(com.trmtgtnh.surface.SurfaceFamily family, String pattern);

        /** Opens the chunk tamper's settings screen on this client, for the stack being held. */
        void openTamperScreen(net.minecraft.world.item.ItemStack stack);

        /** Tells the player that the server is deciding the geometry and their own choice is held. */
        void tellServerOwnsGeometry();

        /** The settings changed; what this client holds derived from them has to be rebuilt. */
        void onConfigChanged(com.trmtgtnh.config.ConfigReload.Delta delta);

        // ---- what the ghost block asks back ----

        short ghostRecordAt(BlockGetter level, int x, int y, int z);

        int ghostOriginAt(int x, int y, int z);

        int clientLightLevel(int x, int y, int z);

        double settledDropUnder(int x, int y, int z);
    }

    private static volatile Side side;

    private static volatile boolean complained;

    private Client() {}

    /** Tells this where the client is. Called by the client module as the mod starts. */
    public static void use(Side loaders) {
        side = loaders;
    }

    /** Forgets it again. For tests, which must not leak a client into the next one. */
    public static void forget() {
        side = null;
        complained = false;
    }

    /** Whether a client has told this where to put things yet. */
    public static boolean wired() {
        return side != null;
    }

    /**
     * The client, or null having said so once.
     *
     * <p>
     * Every method below goes through here rather than testing the field, so that a packet handler
     * reads as a single call and stays comparable with its 1.12.2 twin, which calls through
     * {@code Trmt.proxy} the same way.
     */
    private static Side reaching() {
        Side asking = side;
        if (asking == null && !complained) {
            complained = true;
            Trmt.LOG.warn(
                "Packets are arriving for a client overlay that is not there yet, so they are being "
                    + "read and dropped. On a dedicated server this is simply what happens and "
                    + "nothing is wrong. This is said once.");
        }
        return asking;
    }

    public static void handleChunkErosion(int chunkX, int chunkZ, int[] keys, short[] states) {
        Side at = reaching();
        if (at != null) at.handleChunkErosion(chunkX, chunkZ, keys, states);
    }

    public static void handleDelta(int x, int y, int z, short state) {
        Side at = reaching();
        if (at != null) at.handleDelta(x, y, z, state);
    }

    public static void handleChunkLight(int chunkX, int chunkZ, int[] keys, byte[] values) {
        Side at = reaching();
        if (at != null) at.handleChunkLight(chunkX, chunkZ, keys, values);
    }

    public static void handleLightDelta(int x, int y, int z, int packed) {
        Side at = reaching();
        if (at != null) at.handleLightDelta(x, y, z, packed);
    }

    public static void handleClearAll() {
        Side at = reaching();
        if (at != null) at.handleClearAll();
    }

    public static void applyServerRules(boolean forceOverlay, String decayMode, ServerRules.Geometry geometry) {
        Side at = reaching();
        if (at != null) at.applyServerRules(forceOverlay, decayMode, geometry);
    }

    public static void setMayEditFamilies(boolean allowed) {
        Side at = reaching();
        if (at != null) at.setMayEditFamilies(allowed);
    }

    public static void setServerPricing(WearMath.Pricing pricing) {
        Side at = reaching();
        if (at != null) at.setServerPricing(pricing);
    }

    public static void considerServerTable(boolean sent, long fingerprint) {
        Side at = reaching();
        if (at != null) at.considerServerTable(sent, fingerprint);
    }

    public static void installServerTable(long fingerprint, boolean holdsSwitch, byte[] bytes) {
        Side at = reaching();
        if (at != null) at.installServerTable(fingerprint, holdsSwitch, bytes);
    }

    public static void acceptInspection(int x, int y, int z, float wear, float threshold, int untouchedSeconds,
        int recoverySeconds, int chainIndex, int chainLength, int reinforce, int ward) {
        Side at = reaching();
        if (at != null) {
            at.acceptInspection(
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
    }

    public static void openSnapshotScreen(int flags) {
        Side at = reaching();
        if (at != null) at.openSnapshotScreen(flags);
    }

    public static void openGuideScreen(int bookOrdinal) {
        Side at = reaching();
        if (at != null) at.openGuideScreen(bookOrdinal);
    }

    public static void applyDevPreview(int mode) {
        Side at = reaching();
        if (at != null) at.applyDevPreview(mode);
    }

    // ------------------------------------------------------------------
    // What the ghost block asks back
    // ------------------------------------------------------------------
    //
    // These four answer rather than act, which is why they are set apart. Unwired, each gives what
    // both older editions' proxy gives on a server: a record of nothing, no origin, no light and no
    // drop. A ghost block asking any of them on a dedicated server is a ghost block nothing put
    // there, so the honest answer is that there is nothing to see.

    /** The wear record painted at this position, or {@link ErosionState#NONE}. */
    public static short ghostRecordAt(BlockGetter level, int x, int y, int z) {
        Side at = reaching();
        return at == null ? ErosionState.NONE : at.ghostRecordAt(level, x, y, z);
    }

    /** Tells the player the server owns the geometry, or does nothing on a server. */
    public static void tellServerOwnsGeometry() {
        Side at = reaching();
        if (at != null) at.tellServerOwnsGeometry();
    }

    /** Hands a settings change to the client layer, or does nothing on a server. */
    public static void onConfigChanged(com.trmtgtnh.config.ConfigReload.Delta delta) {
        Side at = reaching();
        if (at != null) at.onConfigChanged(delta);
    }

    /** Opens the chunk tamper's settings screen, or does nothing on a server. */
    public static void openTamperScreen(net.minecraft.world.item.ItemStack stack) {
        Side at = reaching();
        if (at != null) at.openTamperScreen(stack);
    }

    /** Whether the settings modifier is held on this client's keyboard, or false on a server. */
    public static boolean modifierHeld() {
        Side at = reaching();
        return at != null && at.modifierHeld();
    }

    /** Whether this client's crosshair was on nothing, or false on a server. */
    public static boolean swungAtNothing(net.minecraft.world.entity.player.Player swinger) {
        Side at = reaching();
        return at != null && at.swungAtNothing(swinger);
    }

    /** Whether this server lets the Wayfarer's holder file a block under a family. */
    public static boolean mayEditFamilies() {
        Side at = reaching();
        return at != null && at.mayEditFamilies();
    }

    /** The wear numbers this server says it uses, or null for nobody's but our own. */
    public static com.trmtgtnh.erosion.WearMath.Pricing serverPricing() {
        Side at = reaching();
        return at == null ? null : at.serverPricing();
    }

    /** Sets one family's wear look, writes it to the config and rebuilds the pictures. */
    public static void setWearLook(com.trmtgtnh.surface.SurfaceFamily family, String pattern) {
        Side at = reaching();
        if (at != null) at.setWearLook(family, pattern);
    }

    /** Which block this ghost stands in for, as the painter remembered it, or -1. */
    public static int ghostOriginAt(int x, int y, int z) {
        Side at = reaching();
        return at == null ? -1 : at.ghostOriginAt(x, y, z);
    }

    /** The light this square should be drawn at, or 0 where the client is not holding one. */
    public static int clientLightLevel(int x, int y, int z) {
        Side at = reaching();
        return at == null ? 0 : at.clientLightLevel(x, y, z);
    }

    /** How far whatever stands on this square has settled into it, in blocks. */
    public static double settledDropUnder(int x, int y, int z) {
        Side at = reaching();
        return at == null ? 0.0D : at.settledDropUnder(x, y, z);
    }
}
