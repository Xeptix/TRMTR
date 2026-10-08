package com.trmtgtnh.client;

import net.minecraft.client.Minecraft;

/**
 * Asks for the wear pictures to be built again, and keeps a run of edits to one rebuild.
 *
 * <p>
 * Everything a wear picture is made of is read when the pictures are composed, and nothing outside
 * that composition can change one - so a setting that decides what a picture looks like does nothing
 * whatever until the next one. The 1.7.10 edition answered that for a long time by asking the player
 * to press F3+T, which is a debug keybinding, reloads every pack, language, sound and atlas in the
 * game rather than the one that matters, and is not a thing anybody should have to know to make a
 * setting they just changed take effect.
 *
 * <p>
 * Carried from the 1.12.2 edition's client proxy, which has no counterpart here.
 */
public final class WearRestitch {

    private static final WearRestitch INSTANCE = new WearRestitch();

    private WearRestitch() {}

    public static WearRestitch get() {
        return INSTANCE;
    }

    /** Why a rebuild is waiting, or null when none is. */
    private String restitchWanted;

    /** Ticks left before it runs, so a run of edits costs one rebuild rather than one apiece. */
    private int restitchIn;

    private static final int RESTITCH_DELAY = 40;

    /**
     * Asks for the wear pictures to be built again.
     *
     * <p>
     * Everything a wear texture is made of is read while the atlas is being stitched, and nothing outside a
     * stitch can change one - so a setting that decides what a picture looks like does nothing whatever until
     * the next one. The other edition answered that for a long time by asking the player to press F3+T, which
     * is a debug keybinding, reloads every pack, language, sound and atlas in the game rather than the one
     * that matters, and is not a thing anybody should have to know to make a setting they just changed take
     * effect.
     */
    public void requestRestitch(String reason) {
        if (restitchWanted == null) com.trmtgtnh.Trmt.LOG.info("Rebuilding the wear pictures: {}", reason);
        restitchWanted = reason;
        restitchIn = 0;
    }

    /** Whether a rebuild is waiting, which re-meshes everything when it runs. */
    public boolean pending() {
        return restitchWanted != null;
    }

    /**
     * Runs a pending rebuild once the countdown is out and the moment is right.
     *
     * <p>
     * Held while a world is part way through loading, which has nobody to warn yet: the rules a server sends on
     * joining are one of the ways in here, and that arrival would otherwise be a silent freeze in the middle of
     * joining, which reads as a crash. The warning is given here rather than where the rebuild was asked for,
     * so that it is given at a moment somebody can read it.
     */
    public void serviceRestitch() {
        if (restitchWanted == null) return;
        Minecraft mc = Minecraft.getInstance();
        // Held while one of this mod's own screens is open. Neither pauses the game, so the work would land
        // with the player still looking at the editor part way through a set of changes; closing it is the
        // moment to spend the time, and holding collapses a session of edits into one rebuild. The 1.7.10
        // edition's first hold, missing here until 0.9.219.
        if (mc.screen instanceof com.trmtgtnh.client.gui.GuiWearEditor
            || mc.screen instanceof com.trmtgtnh.client.gui.GuiWearTable) {
            restitchIn = 0;
            return;
        }
        if (mc.level != null && mc.player == null) {
            restitchIn = 0;
            return;
        }
        // Nor, with no world loaded, while the block ids may still be moving on another thread: a server's
        // arrive on the network thread under the connecting screen, and a single-player world's are put back
        // on its server thread after this client has let go of it - which a running server has stopped being
        // by then, so the thread is asked, as the older editions ask it.
        if (mc.level == null && (mc.screen instanceof net.minecraft.client.gui.screens.ConnectScreen
            || com.trmtgtnh.Trmt.serverThreadAlive())) {
            restitchIn = 0;
            return;
        }
        if (restitchIn <= 0) {
            tell(
                net.minecraft.ChatFormatting.GRAY + "[TRMT] "
                    + restitchWanted
                    + " Rebuilding them now - the game will stop responding for a moment.");
            restitchIn = RESTITCH_DELAY;
            return;
        }
        if (--restitchIn > 0) return;
        restitchWanted = null;
        restitchPictures();
    }

    /**
     * Reads every resource again, which is how a wear picture changes here.
     *
     * <p>
     * <strong>This costs more than it does in either older edition, and there is no narrower door.</strong>
     * Both of those compose their wear pictures into the block atlas while it is being stitched, so
     * asking for the atlas to be stitched again - and, at 1.12.2, for every model to be baked against
     * it - rebuilds the pictures and touches nothing else. Here the pictures are composed into a
     * resource pack of this mod's own and handed to the game as files, and a pack is read when packs
     * are read: there is no point between "reload the packs" and "do nothing" at which a pack's
     * contents are looked at again.
     *
     * <p>
     * So this is the whole reload - packs, languages, sounds, models, atlas - and it is why the line
     * said before it warns that the game will stop responding. It is asked for rarely and only by
     * something the player has just done.
     */
    private void restitchPictures() {
        Minecraft.getInstance()
            .reloadResourcePacks();
    }

    private static void tell(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // The second argument is who said it, which this version wants and 1.12.2 does not. Nobody
        // said it: it is the mod talking, and NIL_UUID is how the game spells that.
        mc.player.sendMessage(
            new net.minecraft.network.chat.TextComponent(message),
            net.minecraft.Util.NIL_UUID);
    }
}
