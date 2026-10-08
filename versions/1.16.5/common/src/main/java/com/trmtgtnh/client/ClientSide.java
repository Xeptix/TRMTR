package com.trmtgtnh.client;

import net.minecraft.world.level.BlockGetter;

import com.trmtgtnh.Client;
import com.trmtgtnh.config.ServerRules;
import com.trmtgtnh.erosion.WearMath;

/**
 * Where a packet's news reaches the client, and where the ghost's model asks its questions back.
 *
 * <p>
 * The seam itself is thirteen methods and no behaviour: each one is handed straight to whichever of
 * the three carried classes owns it. The 1.12.2 edition keeps all of them on one proxy object, and
 * there is no proxy here, so this is the one piece of the client layer that is this edition's own
 * shape rather than carried text.
 *
 * <p>
 * It is written as a delegate and not as the work itself on purpose. What the three classes hold -
 * the overlay's traffic, the rules a server sets for a visit, and the rebuild of the pictures - is
 * carried line for line from the other edition and is checked against it; folding any of it in here
 * would put drift-checked text behind a shape that only exists in this edition.
 *
 * <h2>What is not here yet</h2>
 *
 * <p>
 * All thirteen are answered. Three were not when this class first landed and each waited on
 * something else: {@code openSnapshotScreen} and {@code openTamperScreen} on their screens, and
 * {@code applyDevPreview} on the dev tool that names which number means which preview. All three
 * have arrived.
 *
 */
public final class ClientSide implements Client.Side {

    // ------------------------------------------------------------------
    // The overlay's own traffic
    // ------------------------------------------------------------------

    @Override
    public void handleChunkErosion(int chunkX, int chunkZ, int[] keys, short[] states) {
        ClientOverlay.get()
            .handleChunkErosion(chunkX, chunkZ, keys, states);
    }

    @Override
    public void handleDelta(int x, int y, int z, short state) {
        ClientOverlay.get()
            .handleDelta(x, y, z, state);
    }

    @Override
    public void handleChunkLight(int chunkX, int chunkZ, int[] keys, byte[] values) {
        ClientOverlay.get()
            .handleChunkLight(chunkX, chunkZ, keys, values);
    }

    @Override
    public void handleLightDelta(int x, int y, int z, int packed) {
        ClientOverlay.get()
            .handleLightDelta(x, y, z, packed);
    }

    @Override
    public void handleClearAll() {
        ClientOverlay.get()
            .handleClearAll();
    }

    // ------------------------------------------------------------------
    // What the server has decided
    // ------------------------------------------------------------------

    @Override
    public void applyServerRules(boolean forceOverlay, String decayMode, ServerRules.Geometry geometry) {
        ClientRules.get()
            .applyServerRules(forceOverlay, decayMode, geometry);
    }

    @Override
    public void setMayEditFamilies(boolean allowed) {
        ClientRules.get()
            .setMayEditFamilies(allowed);
    }

    @Override
    public boolean swungAtNothing(net.minecraft.world.entity.player.Player swinger) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || swinger != client.player) return false;
        net.minecraft.world.phys.HitResult target = client.hitResult;
        return target != null && target.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    @Override
    public boolean mayEditFamilies() {
        return ClientRules.get()
            .mayEditFamilies();
    }

    @Override
    public WearMath.Pricing serverPricing() {
        return ClientRules.get()
            .serverPricing();
    }

    /**
     * Sets one family's wear look, writes it to the config and rebuilds the pictures.
     *
     * <p>
     * Carried from the other edition's proxy, where it has always lived: the editor stages a look
     * and hands every staged one over on the way out, and what happens then is a config write and a
     * restitch rather than anything a screen does.
     *
     * <p>
     * The warning about a poisoned config is the point of the second half. Saving refuses to write a
     * file assembled from a bad read, so without it the look would apply now and be gone at the next
     * start with nothing said about it.
     */
    @Override
    public void setWearLook(com.trmtgtnh.surface.SurfaceFamily family, String pattern) {
        com.trmtgtnh.config.FamilySettings settings = com.trmtgtnh.config.TrmtConfig.family(family);
        if (settings == null || pattern == null || pattern.equals(settings.wearPattern)) return;
        settings.wearPattern = pattern;

        com.trmtgtnh.config.ConfigFile config = com.trmtgtnh.config.TrmtConfig.raw();
        if (config != null) {
            com.trmtgtnh.config.ConfigFile.Setting property = config
                .getCategory(
                    com.trmtgtnh.config.TrmtConfig.CATEGORY_FAMILIES + com.trmtgtnh.config.ConfigFile.CATEGORY_SPLITTER
                        + family.key())
                .get("wearPattern");
            if (property != null) {
                property.set(pattern);
                com.trmtgtnh.config.TrmtConfig.save();
            }
        }
        if (com.trmtgtnh.config.TrmtConfig.isPoisoned()) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null && client.player != null) {
                com.trmtgtnh.server.Notices.say(
                    client.player,
                    com.trmtgtnh.server.Notices.line(
                        // The other edition's wording, written out rather than keyed, because that
                        // is how it is written there and one of these two files is the drift check's
                        // reference for the other.
                        "Your look changed for now, but the config file failed to read, so it will "
                            + "not be kept. Fix it and use /trmt reload.",
                        net.minecraft.ChatFormatting.YELLOW,
                        true));
            }
        }
        WearRestitch.get()
            .requestRestitch("Your wear look changed.");
    }

    @Override
    public void setServerPricing(WearMath.Pricing pricing) {
        ClientRules.get()
            .setServerPricing(pricing);
    }

    @Override
    public void considerServerTable(boolean sent, long fingerprint) {
        ClientRules.get()
            .considerServerTable(sent, fingerprint);
    }

    @Override
    public void installServerTable(long fingerprint, boolean holdsSwitch, byte[] bytes) {
        ClientRules.get()
            .installServerTable(fingerprint, holdsSwitch, bytes);
    }

    // ------------------------------------------------------------------
    // Screens and readouts
    // ------------------------------------------------------------------

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
    public void openSnapshotScreen(int flags) {
        net.minecraft.client.Minecraft.getInstance()
            .setScreen(new com.trmtgtnh.client.gui.GuiSnapshot(flags));
    }

    /**
     * Opens the guide book at this ordinal.
     *
     * <p>
     * An ordinal rather than the enum, because the item that asks lives in the shared module; see
     * the seam for why. Out of range is ignored rather than clamped - a number nobody sent is better
     * answered with nothing than with the wrong book.
     */
    @Override
    public void openGuideScreen(int bookOrdinal) {
        com.trmtgtnh.item.GuideBook[] books = com.trmtgtnh.item.GuideBook.values();
        if (bookOrdinal < 0 || bookOrdinal >= books.length) return;
        net.minecraft.client.Minecraft.getInstance()
            .setScreen(new com.trmtgtnh.client.gui.GuiGuideBook(books[bookOrdinal]));
    }

    @Override
    public void applyDevPreview(int mode) {
        String pattern = mode == com.trmtgtnh.item.ItemDevTool.PREVIEW_OLD
            ? com.trmtgtnh.config.FamilySettings.PATTERN_SMOOTH
            : com.trmtgtnh.config.FamilySettings.PATTERN_CRACK;
        com.trmtgtnh.surface.SurfaceFamily[] hard = { com.trmtgtnh.surface.SurfaceFamily.COBBLE,
            com.trmtgtnh.surface.SurfaceFamily.STONE, com.trmtgtnh.surface.SurfaceFamily.NETHER,
            com.trmtgtnh.surface.SurfaceFamily.END };
        for (com.trmtgtnh.surface.SurfaceFamily family : hard) {
            com.trmtgtnh.config.FamilySettings settings = com.trmtgtnh.config.TrmtConfig.family(family);
            if (settings != null) settings.wearPattern = pattern;
        }
        WearRestitch.get()
            .requestRestitch("A wear preview was asked for.");
    }

    /**
     * Ctrl, which is what both older editions ask for, asked of the screen's own helper.
     *
     * <p>
     * The helper rather than the keyboard directly, because it is the same question every screen in
     * the game asks and it already knows about the platforms where Ctrl is not Ctrl.
     */
    @Override
    public boolean modifierHeld() {
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }

    /**
     * The server is deciding the geometry, so this client's own choice is held.
     *
     * <p>
     * Said in chat, through the same notice every other line of this mod uses, and said each time
     * rather than once: it is the answer to a button the player has just pressed, and an answer that
     * only arrives the first time looks like the button stopped working.
     */
    @Override
    public void tellServerOwnsGeometry() {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) return;
        com.trmtgtnh.server.Notices.say(
            client.player,
            com.trmtgtnh.server.Notices.line(
                com.trmtgtnh.util.Translate.get("trmtgtnh.rules.serverOwnsGeometry"),
                net.minecraft.ChatFormatting.YELLOW,
                true));
    }

    /**
     * The settings changed, so what this client holds derived from them is rebuilt.
     *
     * <p>
     * Handed to the client thread rather than done here: this is called from wherever the reload
     * was asked for, which on a dedicated server's command is a server thread and on a screen's
     * Done button is the client's, and taking every ghost in the world off and putting it back is
     * not something to do from whichever of those happened to arrive.
     */
    @Override
    public void onConfigChanged(com.trmtgtnh.config.ConfigReload.Delta delta) {
        if (delta == null || !delta.any()) return;
        com.trmtgtnh.util.MainThread.onClient(() -> applyLocally(delta));
    }

    private void applyLocally(com.trmtgtnh.config.ConfigReload.Delta delta) {
        if (delta.textures) {
            WearRestitch.get()
                .requestRestitch("Wear textures changed.");
        }
        if (delta.needsRepaint()) {
            // Always off before on. Painting is skipped where a ghost already stands, so a block that
            // has just stopped being tracked would otherwise keep the one it has for ever.
            OverlayPainter painter = OverlayPainter.get();
            painter.restoreAll();
            if (com.trmtgtnh.config.TrmtConfig.enabled && com.trmtgtnh.config.TrmtConfig.overlayVisible()) {
                painter.repaintAll();
            }
        }
        // The server stops spending bandwidth on a player who cannot see the result. The player's own
        // choice, as the first hello sends, rather than what is visible: a server that remembered
        // visibility as the player's wish kept sending wear to somebody who had switched it off.
        if (delta.showErosionMoved && announced) {
            com.trmtgtnh.network.TrmtNetwork.sendHello(com.trmtgtnh.config.TrmtConfig.showErosion);
        }
        if (delta.showErosionMoved && !com.trmtgtnh.config.TrmtConfig.showErosion
            && com.trmtgtnh.config.TrmtConfig.overlayForced) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null && client.player != null) {
                com.trmtgtnh.server.Notices.say(
                    client.player,
                    com.trmtgtnh.server.Notices.line(
                        "This server has worn ground you can walk down into, so path visuals cannot be switched off here. Your setting will apply everywhere else.",
                        net.minecraft.ChatFormatting.YELLOW,
                        true));
            }
        }
        // Last, because the table this asks for is installed from the queue and repaints on its own. An edit
        // made on the config screen mid-visit - a block added to a family, one excluded - publishes a table the
        // server may not be using, and until 0.9.219 nothing here ever looked again.
        ClientRules.get()
            .recheckServerTable();
    }

    /**
     * The same rebuild as a server's, run where it is safe to run it.
     *
     * <p>
     * A client sees the ids move on more than one thread: a single-player world takes its ids and gives them
     * back on its own server thread, and is rebuilt where it stands; a visit's arrive on the network thread,
     * where rebuilding would race the painter, so that one is queued. The wear atlas needs nothing - it is
     * filed by block object, which no move renumbers - and the line queued after the rebuild says how many
     * blocks with wear of their own now carry another id. The 1.7.10 edition's method.
     */
    @Override
    public void idsMoved() {
        boolean inPlace = net.minecraft.client.Minecraft.getInstance()
            .isSameThread() || com.trmtgtnh.Trmt.onServerThread();
        Runnable rebuild = () -> {
            com.trmtgtnh.Trmt.LOG.info("Block ids changed; rebuilding the surface table under them");
            ClientRules.resettleSurfaces();
        };
        try {
            if (inPlace) {
                rebuild.run();
            } else {
                com.trmtgtnh.util.MainThread.onClient(rebuild);
            }
        } finally {
            // Queued whatever the rebuild did: one run where it stands can throw, and the throw goes on to the
            // loader as it always has without taking the icons' reset and the atlas's audit with it.
            com.trmtgtnh.util.MainThread.onClient(com.trmtgtnh.client.gui.WearIcons::reset);
            com.trmtgtnh.util.MainThread.onClient(com.trmtgtnh.client.texture.WearTextures::auditAfterIdMove);
        }
    }

    /**
     * What a tick with no world in it still has to do: run what was handed to this thread, and a rebuild of
     * the wear pictures that was asked for.
     *
     * <p>
     * The 1.7.10 edition does both above its world check. So a look changed at the main menu takes there,
     * since going into a world builds nothing, and a rebuild asked for on the way out of a world runs before
     * the next one rather than in the middle of joining it. Until 0.9.219 both waited for a world here.
     */
    public static void idleTick() {
        com.trmtgtnh.util.MainThread.drainClient();
        WearRestitch.get()
            .serviceRestitch();
    }

    @Override
    public void openTamperScreen(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        net.minecraft.client.Minecraft.getInstance()
            .setScreen(new com.trmtgtnh.client.gui.GuiTamper(stack));
    }

    // ------------------------------------------------------------------
    // What the ghost block asks back
    // ------------------------------------------------------------------

    @Override
    public short ghostRecordAt(BlockGetter level, int x, int y, int z) {
        return ClientOverlay.get()
            .ghostRecordAt(level, x, y, z);
    }

    @Override
    public int ghostOriginAt(int x, int y, int z) {
        return ClientOverlay.get()
            .ghostOriginAt(x, y, z);
    }

    @Override
    public int clientLightLevel(int x, int y, int z) {
        return ClientOverlay.get()
            .clientLightLevel(x, y, z);
    }

    @Override
    public double settledDropUnder(int x, int y, int z) {
        return ClientOverlay.get()
            .settledDropUnder(x, y, z);
    }

    // ------------------------------------------------------------------
    // What a loader's client tick has to drive
    // ------------------------------------------------------------------

    /**
     * One client tick's worth of work, called by each loader's own tick hook.
     *
     * <p>
     * Three things, and the order is the order the 1.12.2 proxy does them in. Whatever other threads
     * have left for the client thread is run first, because a packet handled off-thread may have
     * queued a repaint. Then the painter's queue is drained against its own per-tick budget. Then a
     * pending rebuild of the pictures is counted down, which is last because it stops the game for a
     * moment when it fires and everything above it should have happened first.
     */
    public static void tick() {
        sayHello();
        tellAboutTheModifier();
        // Put in place once, from here rather than from start-up: JourneyMap builds its own tables
        // as a world loads, and a BlockMD asked for before then is one it will replace.
        com.trmtgtnh.client.journeymap.JourneyMapColors.tick();
        askAboutCrosshair();
        com.trmtgtnh.util.MainThread.drainClient();
        OverlayPainter.get()
            .tick();
        WearRestitch.get()
            .serviceRestitch();
    }

    /**
     * Asks the server about the block under the crosshair, where anything is going to read the answer.
     *
     * <p>
     * <strong>Nothing called this, so nothing ever asked.</strong> {@code InspectionCache} holds the
     * reply and {@code WailaCompat} reads it, and between them was a poll no tick performed - so
     * {@code has(x, y, z)} was false for every position and a tooltip showed a square's wear but
     * never its reinforcement, its ward or how far along its run it was. Carried from the 1.12.2
     * edition's {@code askAboutCrosshair}, which is where the gate below comes from.
     */
    private static void askAboutCrosshair() {
        net.minecraft.client.Minecraft game = net.minecraft.client.Minecraft.getInstance();
        if (game == null || game.level == null) return;

        net.minecraft.world.phys.HitResult target = game.hitResult;
        if (target == null || target.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            InspectionCache.clear();
            return;
        }
        net.minecraft.core.BlockPos at = ((net.minecraft.world.phys.BlockHitResult) target).getBlockPos();
        if (at == null) {
            InspectionCache.clear();
            return;
        }

        boolean ghost = game.level.getBlockState(at)
            .getBlock() instanceof com.trmtgtnh.block.BlockGhost;
        if (com.trmtgtnh.util.InspectionReach.asks(
            InspectionCache.hasReader(),
            ghost,
            com.trmtgtnh.config.TrmtConfig.reinforceEnabled,
            com.trmtgtnh.config.TrmtConfig.wardEnabled)) {
            InspectionCache.poll(at.getX(), at.getY(), at.getZ());
        } else {
            InspectionCache.clear();
        }
    }

    /** Whether the modifier was held last tick, so only the changes are sent. */
    private static boolean modifierWasDown;

    /**
     * Tells the server whether the modifier key is held, when that changes.
     *
     * <p>
     * A key a server cannot see. The client knows it is held and the server is the side that acts on
     * a click, so unless this is sent the server never hears a byte of it and a chunk tamper's
     * modified click behaves as an ordinary one. The packet and its handler were both here and
     * registered; nothing sent one, which is the same shape as the rest of this edition's gaps.
     *
     * <p>
     * Sent on the transition rather than with the click, which is the whole reason it works: a message
     * sent a tick before a click arrives before that click, whereas a modifier sent alongside one
     * would be racing the click it is meant to qualify.
     *
     * <p>
     * Only while a chunk tamper is held, so a player holding control for any other reason is not
     * sending packets about it.
     */
    private static void tellAboutTheModifier() {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) return;

        net.minecraft.world.item.ItemStack held = client.player.getMainHandItem();
        boolean relevant = held != null && held.getItem() instanceof com.trmtgtnh.item.ItemChunkTamper;
        boolean down = relevant && net.minecraft.client.gui.screens.Screen.hasControlDown();
        if (down == modifierWasDown) return;

        modifierWasDown = down;
        com.trmtgtnh.network.TrmtNetwork.sendModifier(down);
    }

    /** Whether this client has told the server it is here, which is said once a visit. */
    private static boolean announced;

    /**
     * Tells the server this client has the mod and whether it wants worn ground drawn.
     *
     * <p>
     * <strong>Nothing arrives until this is said.</strong> The server sends a chunk's wear only to a
     * player it has heard from - that is what {@code TrmtNetwork.isSubscribed} asks - so a client that
     * never says hello gets a world whose roads are all unworn, with no error anywhere and the whole
     * server half working perfectly. Which is exactly what happened when this was left out: the store
     * held twenty-six worn positions and the client's overlay held none.
     *
     * <p>
     * From the tick rather than from a join hook, which is what both older editions do and for the
     * same reason: it is the plainest test of the thing that matters, that there is a world and a
     * player in it to be answered.
     */
    private static void sayHello() {
        if (announced) return;
        announced = true;
        com.trmtgtnh.network.TrmtNetwork.sendHello(com.trmtgtnh.config.TrmtConfig.showErosion);
    }

    /**
     * Puts everything this client is holding back, for leaving a world.
     *
     * <p>
     * The ghosts first, because the caches are the only record of what each one was covering and
     * lifting a ghost needs that record. Then the queue, then the caches, then the two things the
     * server set for the visit - its rules and its surface table - which are the server's and have no
     * business outliving the connection.
     *
     * <p>
     * <strong>And this client's own settings read back, which until 0.9.219 nothing did.</strong>
     * Releasing a server's rules only stops holding them; what they wrote into the live settings stays
     * until something reads the file again - so the decay mode, the wear-through switches and every
     * family's stages, depth and successors went on being the last server's, into the next world opened,
     * until the config screen's Done, {@code /trmt reload} or a restart. The 1.7.10 edition's hand-back:
     * the chains taken under the server, the rules and the table released, the file read, and only then
     * asked whether the chains or the switches moved - asked before the read they compare the server's
     * chains with themselves.
     */
    public static void leaveWorld() {
        // Said again on the next visit: a server has no memory of this client between connections.
        announced = false;
        // The server forgot the modifier the moment this connection went; if this did not forget it
        // too, a client that left with the key down would never send the transition again, and the
        // next server would be told the key was held only when it was finally let go.
        modifierWasDown = false;
        OverlayPainter.get()
            .restoreAll();
        // What is left queued names chunks of a world that is going away. Restoring first and
        // emptying second is the order that matters: a queue drained into the next world would paint
        // this one's roads onto it, at the coordinates they had here.
        OverlayPainter.get()
            .clearQueue();
        ClientErosionCache.get()
            .clear();
        ClientLightCache.get()
            .clear();
        InspectionCache.clear();
        int[] underServer = ServerRules.chainAppearances();
        ServerRules.release();
        // The table a server named is the server's answer to what erodes, and a client that kept it
        // would meet the next world holding the last one's list. Released before the read, so the
        // rebuild below publishes this client's own table rather than the visit's again.
        boolean tableHandedBack = com.trmtgtnh.surface.SurfaceRegistry.releaseServerTable();
        ClientRules.get()
            .leave();
        com.trmtgtnh.config.TrmtConfig.read();
        boolean appearancesHandedBack = ServerRules.appearancesMovedFrom(underServer);
        boolean enabledHandedBack = ServerRules.enabledMovedFrom(underServer);
        // The mirror of the join: a family this client had switched off can have been resolved into its
        // table for the visit, and leaving it there would go on trampling ground the player's own file had
        // said to leave alone.
        if (enabledHandedBack || tableHandedBack) ClientRules.resettleSurfaces();
        if (tableHandedBack) com.trmtgtnh.client.gui.WearIcons.reset();
        // A client that wore a server's chains needs its own pictures back - serviced with no world loaded,
        // see idleTick, so before the next world rather than in the middle of joining it.
        if (appearancesHandedBack) {
            WearRestitch.get()
                .requestRestitch("Back on the materials your own config draws.");
        }
        // And the map's coalescing, so the first square painted in the next world is told about
        // whatever the last one in this world happened to be.
        com.trmtgtnh.client.xaero.XaeroMinimap.reset();
        com.trmtgtnh.client.journeymap.JourneyMapColors.reset();
    }
}
