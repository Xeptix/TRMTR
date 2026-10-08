package com.trmtgtnh;

import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * What the mod is called and where it writes, and nothing else.
 *
 * <p>
 * In both older editions this class is the mod: it carries the loader's entry-point annotation, holds
 * the sided proxy, and runs the lifecycle. It cannot be that here, because here there are two loaders
 * and a class that named either of them could not be shared. So the name has been kept and the job
 * has not: the entry points live in the loader modules, and what is left here is the part every other
 * class actually asked of it.
 *
 * <p>
 * Which, measured rather than guessed, is the logger. The settings layer is the largest thing carried
 * into this module and it reaches for exactly one member of the old class, {@code Trmt.LOG}, twelve
 * times. Keeping that name and that type is why three thousand lines of settings came across without
 * a single edit - and an edit there would have been the kind nobody reviews line by line.
 *
 * <p>
 * Log4j rather than either loader's logging, because Minecraft itself ships it on both and it is not
 * a loader package. The fence around the shared module would refuse the alternatives.
 */
public final class Trmt {

    /**
     * The mod id, which is also the settings file's name and the namespace every resource is under.
     *
     * <p>
     * Written out rather than generated. The older editions take it from a {@code Tags} class their
     * buildscripts write at compile time, and reproducing that here would mean a source-generating
     * task in a build that already has two loaders to keep happy. The cost of writing it out is that
     * it could disagree with {@code gradle.properties}, so a test reads both and fails if they ever do.
     */
    public static final String MODID = "trmtgtnh";

    /** The display name, under the same arrangement as {@link #MODID}. */
    public static final String NAME = "TRMT Reimagined";

    public static final Logger LOG = LogManager.getLogger(MODID);

    /**
     * What this build calls itself, as the loader that started it says.
     *
     * <p>
     * The 1.12.2 edition reads a generated {@code Tags} class that its build plugin writes; there is
     * no such plugin here and writing one would be a build task for one string. Each loader knows
     * its own version and says so as it starts, which is the same arrangement {@link #useHost} is
     * under and needs nothing generated.
     *
     * <p>
     * What it is for is stamping: the two compat layers write another mod's data files and put this
     * in them, so a later launch can tell a folder written by an older build from one written by
     * this one. A wrong answer there costs a rewrite nobody needed, which is why "unknown" is a safe
     * default rather than a crash.
     */
    private static volatile String version = "unknown";

    /** Tells this what this build is called. Called once by each loader module as the mod starts. */
    public static void useVersion(String said) {
        if (said != null && !said.isEmpty()) version = said;
    }

    /** What this build calls itself, or "unknown" before a loader has said. */
    public static String version() {
        return version;
    }

    /** How this edition finds the running server, supplied by whichever loader started the mod. */
    public interface Host {

        MinecraftServer runningServer();
    }

    private static volatile Host host;

    private Trmt() {}

    /** Tells this where the server is. Called once by each loader module as the mod starts. */
    public static void useHost(Host loaders) {
        host = loaders;
    }

    /** Forgets it again. For tests, which must not leak a server into the next one. */
    public static void forgetHost() {
        host = null;
    }

    /**
     * The server only while it is actually running, which is not the same question.
     *
     * <p>
     * A client that has opened a single-player world keeps a server instance around after that world
     * is closed, so anything deciding "is there a world here that I own" has to ask whether it is
     * still running, or it ends up handing work to a dead thread. That distinction is the whole reason
     * this method exists rather than callers reaching for the server themselves, and it is why the
     * loader modules answer it rather than merely handing over an instance.
     *
     * <p>
     * Null before a loader has said anything, which is the honest answer: nothing is running yet. The
     * settings layer asks this to decide whether there is anybody to tell about a change, and "nobody"
     * is correct during startup.
     */
    /**
     * Whether the caller is on the running server's own thread.
     *
     * <p>
     * Both older editions record the thread as the server starts and compare against it, because
     * neither has anything better to ask. This version does: a {@code MinecraftServer} is an event
     * loop and knows whether you are on it. Same question, one fewer piece of state, and nothing to
     * forget to clear when a single-player world closes.
     *
     * <p>
     * The reasoning for asking at all is carried from the 1.7.10 edition: a command from a chat
     * bridge's own thread and a disconnection event do not arrive on the server thread, packet
     * handlers arrive on the network thread, and most of what this mod does on a server assumes
     * otherwise. False with no server running, which sends a caller to queue its work rather than do
     * it.
     */
    public static boolean onServerThread() {
        MinecraftServer server = runningServer();
        return server != null && server.isSameThread();
    }

    public static MinecraftServer runningServer() {
        Host asking = host;
        return asking == null ? null : asking.runningServer();
    }

    /** The server thread, recorded as a server starts and forgotten as it stops. See serverThreadAlive. */
    private static volatile Thread serverThread;

    /** Records the thread a server is starting on, or forgets it with null as one stops. */
    public static void serverThreadIs(Thread thread) {
        serverThread = thread;
    }

    /**
     * Whether a server's thread is still alive, from its start to its stopped event.
     *
     * <p>
     * Not the same question as whether one is running, which is no from the moment it begins to stop: a
     * single-player world goes on saving, and its block ids go on moving, after that. The older editions
     * record the thread and ask this; a rebuild of the wear pictures is held while it is true with no world
     * loaded, so it cannot plan half a pack under one numbering and the rest under another.
     */
    public static boolean serverThreadAlive() {
        Thread recorded = serverThread;
        return recorded != null && recorded.isAlive();
    }
}
