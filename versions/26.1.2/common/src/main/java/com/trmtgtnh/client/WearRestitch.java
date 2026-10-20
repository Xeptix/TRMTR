package com.trmtgtnh.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
     * Builds the wear pictures again: the model manager, the item renderer and the block renderer, then every chunk.
     *
     * <p>
     * Both older editions compose their wear pictures into the block atlas while it is being stitched, so asking for
     * the atlas to be stitched again - and, at 1.12.2, for every model to be baked against it - rebuilds the pictures
     * and touches nothing else. Here the pictures are composed into a resource pack of this mod's own, and until 0.9.223
     * this was the whole reload - packs, languages, sounds, models, atlas - on the reading that a pack is looked at only
     * when packs are read. The pack is not where the narrowing happens, though (WP67, the worker's report in the 1.7.10
     * tree's docs/goals/0.9.223-WP30-WP67.md): it stays open in the resource manager and draws every picture fresh
     * whenever the block atlas is stitched, and the model manager's prepare stitches it. So the model manager runs
     * alone, through the same prepare-then-apply barrier a full reload uses and under the same loading screen; then the
     * item renderer, whose items would otherwise keep models baked against the atlas just closed, and the block
     * renderer, whose water and lava would keep its sprites; then every chunk is built again, as a full reload ends.
     *
     * <p>
     * Under OptiFine it is still the whole reload: OptiFine rebuilds its better grass, custom colors and shader layers
     * from a listener of its own, which is not one of the three. And a narrow run that fails falls back to the whole
     * reload, as 1.7.10 falls back. Either way the line said before it warns that the game will stop responding, and
     * how long it took is logged, so the two can be compared on one machine.
     */
    private void restitchPictures() {
        Minecraft mc = Minecraft.getInstance();
        if (optiFinePresent() || !(mc.getResourceManager() instanceof net.minecraft.server.packs.resources.ReloadableResourceManager)) {
            com.trmtgtnh.Trmt.LOG.info("Rebuilding the wear pictures with a full resource reload{}", optiFinePresent() ? ", OptiFine being present" : "");
            mc.reloadResourcePacks();
            return;
        }
        final long began = System.nanoTime();
        // 26.1's three, in the order the game registers them. The atlases left the model manager for an atlas
        // manager of their own (by 26.1), which stitches them in its prepare and hands the stitch to the model
        // manager and the particles through the reload's shared state - so it runs first, and the model manager
        // cannot run without it. The item and block renderers are no longer listeners at all: items and blocks,
        // water and lava among them, are drawn from the model manager's own sets, which is what rebuilding those
        // two did in 1.21.1. The particles are new to the list because their atlas now stitches with the rest; left
        // out, they would keep sprites from the atlas just closed. The game keeps them in a private field with no
        // getter, so they are read by name, and a game where that fails takes the whole reload, as a narrow run
        // that fails does.
        net.minecraft.server.packs.resources.PreparableReloadListener particles = particleResources(mc);
        if (particles == null) {
            com.trmtgtnh.Trmt.LOG.info("Rebuilding the wear pictures with a full resource reload{}", ", the particles being out of reach");
            mc.reloadResourcePacks();
            return;
        }
        java.util.List<net.minecraft.server.packs.resources.PreparableReloadListener> narrow = narrowListeners(mc, particles);
        if (narrow == null) {
            com.trmtgtnh.Trmt.LOG.info("Rebuilding the wear pictures with a full resource reload{}", ", the game's own listeners being out of reach");
            mc.reloadResourcePacks();
            return;
        }
        net.minecraft.server.packs.resources.ReloadInstance run = net.minecraft.server.packs.resources.SimpleReloadInstance
            .of(
                mc.getResourceManager(),
                narrow,
                // 26.1 moved Util into net.minecraft.util.
                net.minecraft.util.Util.backgroundExecutor(),
                mc,
                java.util.concurrent.CompletableFuture.completedFuture(net.minecraft.util.Unit.INSTANCE));
        mc.setOverlay(new net.minecraft.client.gui.screens.LoadingOverlay(mc, run, failed -> {
            if (failed.isPresent()) {
                com.trmtgtnh.Trmt.error("Rebuilding the wear pictures by the model manager alone failed, so the whole reload runs instead", failed.get());
                mc.reloadResourcePacks();
                return;
            }
            mc.levelRenderer.allChanged();
            com.trmtgtnh.Trmt.LOG.info(
                "Rebuilt the wear pictures by the model manager alone in {} ms",
                Long.valueOf((System.nanoTime() - began) / 1000000L));
        }, true));
    }

    /** Whether OptiFine is loaded, asked of its own class without starting it. */
    /** The client's particle resources, a reload listener the game keeps in a private field from 26.1. */
    /**
     * The listeners a narrow rebuild runs, in the order the game registered them: the model manager, the particles, and
     * every listener that hands the reload a piece of shared state - the atlas manager's stitch in vanilla 26.1, and
     * whatever a loader adds. Found in the first 26.1.2 game (0.9.227): NeoForge's model manager also reads its
     * animation loader's state, which only that loader's own {@code prepareSharedState} sets, and a rebuild of the three
     * vanilla needs stopped the game on a NullPointerException. Asking which listeners prepare shared state, rather than
     * naming them, covers a loader's next one too. Null where the game's list cannot be read, and the caller then takes
     * the whole reload.
     */
    static java.util.List<net.minecraft.server.packs.resources.PreparableReloadListener> narrowListeners(Minecraft mc,
        net.minecraft.server.packs.resources.PreparableReloadListener particles) {
        java.util.List<net.minecraft.server.packs.resources.PreparableReloadListener> registered = registeredListeners(mc);
        if (registered == null) return null;
        java.util.List<net.minecraft.server.packs.resources.PreparableReloadListener> out = new java.util.ArrayList<net.minecraft.server.packs.resources.PreparableReloadListener>();
        for (net.minecraft.server.packs.resources.PreparableReloadListener each : registered) {
            if (each == mc.getModelManager() || each == particles || preparesSharedState(each)) out.add(each);
        }
        if (!out.contains(mc.getModelManager()) || !out.contains(particles)) return null;
        return out;
    }

    /** Whether a listener's own class, or one it extends, hands the reload a piece of shared state. */
    static boolean preparesSharedState(Object listener) {
        for (Class<?> at = listener.getClass(); at != null && at != Object.class; at = at.getSuperclass()) {
            try {
                at.getDeclaredMethod(
                    "prepareSharedState",
                    net.minecraft.server.packs.resources.PreparableReloadListener.SharedState.class);
                return true;
            } catch (NoSuchMethodException notHere) {
                // Asked of the class it extends next.
            }
        }
        return false;
    }

    /** The game's own reload listeners, in registration order, read by name; null where they cannot be read. */
    @SuppressWarnings("unchecked")
    private static java.util.List<net.minecraft.server.packs.resources.PreparableReloadListener> registeredListeners(Minecraft mc) {
        try {
            if (!(mc.getResourceManager() instanceof net.minecraft.server.packs.resources.ReloadableResourceManager)) return null;
            java.lang.reflect.Field field = net.minecraft.server.packs.resources.ReloadableResourceManager.class
                .getDeclaredField("listeners");
            field.setAccessible(true);
            Object held = field.get(mc.getResourceManager());
            return held instanceof java.util.List
                ? new java.util.ArrayList<net.minecraft.server.packs.resources.PreparableReloadListener>(
                    (java.util.List<net.minecraft.server.packs.resources.PreparableReloadListener>) held)
                : null;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static net.minecraft.server.packs.resources.PreparableReloadListener particleResources(Minecraft mc) {
        try {
            java.lang.reflect.Field field = Minecraft.class.getDeclaredField("particleResources");
            field.setAccessible(true);
            Object held = field.get(mc);
            return held instanceof net.minecraft.server.packs.resources.PreparableReloadListener
                ? (net.minecraft.server.packs.resources.PreparableReloadListener) held
                : null;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static boolean optiFinePresent() {
        try {
            Class.forName("net.optifine.Config", false, WearRestitch.class.getClassLoader());
            return true;
        } catch (Throwable absent) {
            return false;
        }
    }

    private static void tell(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // The second argument is who said it, which this version wants and 1.12.2 does not. Nobody
        // said it: it is the mod talking, and NIL_UUID is how the game spells that.
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
    }
}
