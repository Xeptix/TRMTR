package com.trmtgtnh;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.trmtgtnh.erosion.ErosionStore;

/**
 * What both sides do.
 *
 * <p>
 * The store that persists wear, the event hub that drives it, and the server's half of a rut. Each is
 * registered on both sides because a single-player game runs a server of its own; each ignores the
 * client's world where that matters. The packet sinks below are the other half of the arrangement:
 * empty here, and filled in by the client proxy, which is the only side with anywhere to put them.
 */
public class CommonProxy {

    public void preInit() {
        // The store listens for chunks loading, saving and unloading, which is the whole of how wear
        // is persisted: it rides inside each chunk's own NBT rather than in a file of its own, so it
        // loads and unloads with the chunk that owns it and an uninstalled mod leaves an unread tag
        // behind rather than a broken world.
        // The config screen's Done button, which until now nothing listened for. Forge posts
        // OnConfigChangedEvent on this bus and delivers it to registered handlers only, so without
        // this line the screen wrote nothing, reloaded nothing and said nothing about it.
        MinecraftForge.EVENT_BUS.register(new ConfigScreenListener());
        MinecraftForge.EVENT_BUS.register(ErosionStore.get());
        // And what drives it: footfalls, the tick the sweep runs on, and blocks coming and going.
        MinecraftForge.EVENT_BUS.register(com.trmtgtnh.server.ServerEvents.get());
        // The tamper's left-click gestures, which no item hook carries. On both sides, because one
        // of its two handlers has to reach the client to stop a dig ever completing there.
        MinecraftForge.EVENT_BUS.register(com.trmtgtnh.item.TamperEvents.get());

        // The server's half of a rut: its real blocks answer with the hollow the client draws over
        // them. Registered on both sides because a single-player game runs a server of its own; the
        // hook ignores the client's world, where a ghost answers for its own shape.
        MinecraftForge.EVENT_BUS.register(new com.trmtgtnh.erosion.PhysicalDecay.CollisionHook());

        // The chest finds. An event handler here where the other edition files everything once at
        // post-init, because 1.12.2 loads its loot tables per world and hands each one over as it is
        // read - so this is registered now and answers later, every time a world loads.
        MinecraftForge.EVENT_BUS.register(com.trmtgtnh.item.ModLoot.get());
    }

    public void init() {
        // The golem's window, which is the one screen in this mod with a container behind it. Named
        // against the mod object, because that is how Forge routes a window request back to whoever
        // asked for it.
        net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE
            .registerGuiHandler(Trmt.instance, new com.trmtgtnh.entity.TrmtGuiHandler());
    }

    /**
     * The golem's screen, for the gui handler to answer a client with.
     *
     * <p>
     * Through the proxy because a class annotated client-only must not be named anywhere a dedicated
     * server will load it, and the handler is shared code.
     */
    public Object golemScreen(net.minecraft.entity.player.EntityPlayer player,
        com.trmtgtnh.entity.EntityGolemOfWays golem) {
        return null;
    }

    // ------------------------------------------------------------------
    // Packet sinks
    // ------------------------------------------------------------------

    /**
     * Where a packet's contents go once it is back on the right thread.
     *
     * <p>
     * Nothing here, on purpose. Every one of these is something only a client has anywhere to put:
     * an overlay cache, a surface table it did not build, a set of rules about a server it is
     * visiting. The client proxy overrides them; a dedicated server runs the empty versions and
     * costs nothing for packets it will never be sent. Carried from the 1.7.10 edition, where the
     * same arrangement lets the packet classes name one type and be written once.
     */
    public void handleChunkErosion(int chunkX, int chunkZ, int[] keys, short[] states) {}

    public void handleDelta(int x, int y, int z, short state) {}

    public void handleChunkLight(int chunkX, int chunkZ, int[] keys, byte[] values) {}

    public void handleLightDelta(int x, int y, int z, int packed) {}

    public void handleClearAll() {}

    /** Applies a server's session rules. Only a client has anything to do with them. */
    public void applyServerRules(boolean forceOverlay, String decayMode,
        com.trmtgtnh.config.ServerRules.Geometry geometry) {}

    /** A server named its surface table's fingerprint. Only a client has anything to do with it. */
    public void considerServerTable(boolean sent, long fingerprint) {}

    /**
     * Block ids moved under the surface table, so it is rebuilt under the ids now in force.
     *
     * <p>
     * The table, the resistance and ground-cover sets and the sinkable stamps are all keyed by a block's
     * number, and a number is only good for the registry it was read under: a save made with another
     * mod list, or a server with its own history, hands out different ones. Built once at start-up and
     * never again, the table went on looking up the wrong blocks - the wrong modded ground wore, real
     * modded paths did not, and the table sent to clients carried the same stale keys - until a reload.
     * The 1.7.10 edition's rebuild; this edition had no handler for the move until 0.9.219.
     */
    public void idsMoved() {
        Trmt.LOG.info("Block ids changed; rebuilding the surface table under them");
        com.trmtgtnh.surface.SurfaceRegistry.resolve();
        // The settling stamps with them: this edition lays both in the one call.
        com.trmtgtnh.erosion.PhysicalDecay.markSinkableBlocks();
    }

    /** A server's surface table arrived. Only a client has anything to do with it. */
    public void installServerTable(long fingerprint, boolean holdsSwitch, byte[] bytes) {}

    /**
     * Only ever used to decide whether to draw the controls. Every edit is checked again on the
     * server, because what a client believes about its own privileges is not evidence.
     */
    public void setMayEditFamilies(boolean allowed) {}

    /** The server's Wear-Table pricing, or null. Held only on the client; the base does nothing. */
    public void setServerPricing(com.trmtgtnh.erosion.WearMath.Pricing pricing) {}

    /**
     * How far the ground under a block has dropped, in block units, as this side's picture has it.
     *
     * <p>
     * Nothing on a side with no picture: a dedicated server reads its own records instead, through
     * {@code PhysicalDecay.groundDropUnder}, and only a client's world is ever asked this.
     */
    public double settledDropUnder(int x, int y, int z) {
        return 0.0D;
    }

    // ------------------------------------------------------------------
    // What a ghost asks about itself
    // ------------------------------------------------------------------

    /**
     * The record a ghost should draw and stand on, as the client holds it and as the world around it
     * now changes it.
     *
     * <p>
     * Asked by the ghost block, which is shared code and is loaded on a dedicated server with every
     * other block. A ghost never stands in a server's world, so the server's answer - nothing - is
     * never actually read; it exists so the block can ask without naming the client's cache.
     *
     * @param world whatever view of the world the asker was handed - on a mesher thread, the snapshot
     *              the game built for exactly that thread, which is the only view safe to read there
     */
    public short ghostRecordAt(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        return com.trmtgtnh.erosion.ErosionState.NONE;
    }

    /**
     * How brightly a square glows as this side's picture has it. Nothing on a side with no picture:
     * a server reads its own records, through {@code GhostLight}, and only a client is asked this.
     */
    public int clientLightLevel(int x, int y, int z) {
        return 0;
    }

    /** The same, with the colour in the high nibble. */
    public int clientLightPacked(int x, int y, int z) {
        return 0;
    }

    // ------------------------------------------------------------------
    // A change of settings
    // ------------------------------------------------------------------

    /** Called after the config screen or a command changes something. Only a client has pictures to rebuild. */
    public void onConfigChanged(com.trmtgtnh.config.ConfigReload.Delta delta) {}

    /** Says that a server owns some of what was just typed. Only a client has anyone to tell. */
    public void tellServerOwnsGeometry() {}

    /**
     * Copies edits made on the config screen into the real settings.
     *
     * <p>
     * Nothing on a server, which has no screen. On a client it reads back the throwaway Forge config
     * the screen was given, which is the one piece of the settings layer that is still Forge's - see
     * {@code ConfigAdapter}, named here only in this comment because it is a client class.
     */
    public void readConfigScreenEdits() {}

    // ------------------------------------------------------------------
    // What a tool asks about the person holding it
    // ------------------------------------------------------------------

    /**
     * Whether this client's settings modifier key is down.
     *
     * <p>
     * Only a client has a keyboard. A server is told about the key by packet and remembers it in
     * {@code TamperModifiers}, which is why this is asked through the proxy rather than of the game.
     */
    public boolean modifierHeld() {
        return false;
    }

    /** Opens the tamper's settings screen on this stack. A server has no screens. */
    public void openTamperScreen(net.minecraft.item.ItemStack stack) {}

    /**
     * Whether this server has said this player may refile blocks and mobs from the magic tamper.
     *
     * <p>
     * The client's copy of an answer the server worked out when it sent its rules, so the screen can
     * hide what it would only be refused for. The refusal itself still lives on the server, because
     * a hidden button is a courtesy and not a permission check.
     */
    public boolean mayEditFamilies() {
        return false;
    }

    /**
     * The last pricing this client was told the server uses, or null when there is none.
     *
     * <p>
     * For the Wear Table's diff, which draws what a crossing costs here beside what it costs where
     * you are standing. Null on a server, and on a client that has not been told.
     */
    public com.trmtgtnh.erosion.WearMath.Pricing serverPricing() {
        return null;
    }

    /**
     * Changes the look a family wears in, and keeps it.
     *
     * <p>
     * A client's own setting, from the wear editor: nothing on a server reads it, and a look is a
     * question about pictures, which only a client has.
     */
    public void setWearLook(com.trmtgtnh.surface.SurfaceFamily family, String pattern) {}

    /**
     * The exact numbers a server keeps for one square, come back.
     *
     * <p>
     * Only a client asked, and only a client has a tooltip waiting on the answer.
     */
    public void acceptInspection(int x, int y, int z, float wear, float threshold, int untouchedSeconds,
        int recoverySeconds, int chainIndex, int chainLength, int reinforce, int ward) {}

    /** Opens a guide book. Only a client has anywhere to read it. */
    public void openGuideScreen(int bookOrdinal) {}

    /** Opens, or refreshes in place, the snapshot screen. Only a client has one. */
    public void openSnapshotScreen(int flags) {}

    /** Asks the server what the snapshot slots hold, so the screen can open. Client only. */
    public void requestSnapshotScreen() {}

    /** Shows a wear-pattern preview. Only a client has a picture to change. */
    public void applyDevPreview(int mode) {}

    /**
     * Whether this swing was this client's own player swinging at nothing.
     *
     * <p>
     * Only a client knows where a crosshair is pointing. A server is told by packet, which is the
     * whole reason that packet exists: a left-click on a block arrives on its own, and a left-click
     * on sky arrives nowhere at all.
     */
    public boolean swungAtNothing(net.minecraft.entity.player.EntityPlayer swinger) {
        return false;
    }

    /**
     * Stamps the blocks whose footing comes down with the ground they rest on.
     *
     * <p>
     * Through the proxy because the other edition's client keeps a stamp of its own beside the server's; here
     * one set serves both sides, and this is the call that keeps the reload's sequence the same in both
     * editions.
     */
    public void markSettlingBlocks() {
        com.trmtgtnh.erosion.PhysicalDecay.markSettlingBlocks();
    }

    /** What a ghost is standing over, as a block state id, or -1. Only a client has ghosts. */
    public int ghostOriginAt(int x, int y, int z) {
        return -1;
    }

    /**
     * Picks up edits made through Forge's in-game config screen.
     *
     * <p>
     * Here rather than beside the settings it reloads, because it is the one part of the settings
     * layer that has to name a loader: the event is Forge's, the annotation is Forge's, and the bus
     * it arrives on is Forge's. Everything it calls is not.
     *
     * <p>
     * Two steps, in this order. The screen edits a throwaway Forge config built from the real
     * settings, and this event is posted after those edits have been written into it and before
     * anything reloads - so the copy is read back first and the reload second. Reversing them would
     * reload the settings as they were before the screen was opened.
     */
    public static final class ConfigScreenListener {

        @SubscribeEvent
        public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (!Trmt.MODID.equals(event.getModID())) return;
            Trmt.proxy.readConfigScreenEdits();
            com.trmtgtnh.config.ConfigReload.fromGuiDeferred();
        }
    }

}
