package com.trmtgtnh.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.trmtgtnh.Trmt;

/**
 * Hands work to the thread that is allowed to touch the world, to run at the end of that thread's tick,
 * or of a later one when more is waiting than a tick takes.
 *
 * <p>
 * Packet handlers do not need it to reach that thread in 1.7.10: they are already on it. A mod's play
 * packets are queued as they arrive and handled from the connection's own tick, on the server thread on
 * a server and on the client thread on a client, over a single-player world's memory connection as over
 * a remote one. What does run on the network thread is Forge's handshake, which is where a server's block
 * ids are installed, the disconnection events Forge fires as a connection closes, and the few vanilla
 * packets marked to skip the queue, such as keep-alives.
 *
 * <p>
 * The handlers hand their work over all the same, for three reasons. It then runs after every packet
 * handled that tick, in one line with everything else that reaches this queue: on the server, a command
 * from a chat bridge's own thread or from RCon where a mod has mended vanilla's listener, a single-player
 * config screen's reload from the client thread, and a loaded chunk's catch-up, which the server thread
 * puts off to the end of its own tick because only then is it known who is watching; on the client,
 * what a single-player world's server thread hands back to it, and a disconnect and a remote server's
 * block ids from the network thread. A task that throws an exception costs the warning below, where a
 * handler that throws makes Forge close the connection its packet came in on. And from 1.8 to 1.12.2
 * handlers do run on the network thread, where reading a chunk or writing a block is a race, and the
 * kind that shows up as a corrupted save weeks later rather than as an exception now. Later versions
 * differ by loader, so a port checks rather than trusting this in either direction.
 *
 * <p>
 * The client has a queue of its own in {@code Minecraft.func_152344_a}, the method later versions name
 * {@code addScheduledTask}, though it runs a task at once when asked from the client thread, ahead of
 * anything already waiting; the server has nothing like it in 1.7.10. So both sides go through the same
 * small queue here, drained at the end of each side's tick.
 */
public final class MainThread {

    private static final Queue<Runnable> SERVER = new ConcurrentLinkedQueue<Runnable>();
    private static final Queue<Runnable> CLIENT = new ConcurrentLinkedQueue<Runnable>();

    /** Bounds a single tick's catch-up so a burst of packets cannot stall the game. */
    private static final int MAX_PER_TICK = 256;

    private MainThread() {}

    public static void onServer(Runnable task) {
        if (task != null) SERVER.add(task);
    }

    public static void onClient(Runnable task) {
        if (task != null) CLIENT.add(task);
    }

    public static void drainServer() {
        drain(SERVER);
    }

    public static void drainClient() {
        drain(CLIENT);
    }

    /** Throws away the server's pending work, for when that server has stopped. */
    public static void clearServer() {
        SERVER.clear();
    }

    public static void clear() {
        SERVER.clear();
        CLIENT.clear();
    }

    private static void drain(Queue<Runnable> queue) {
        for (int i = 0; i < MAX_PER_TICK; i++) {
            Runnable task = queue.poll();
            if (task == null) return;
            try {
                task.run();
            } catch (RuntimeException failure) {
                // Whatever the task was - an overlay update, a config push, a handed-over command, a table
                // send - one that throws is logged and the rest of the queue still runs, so no one task
                // takes the tick with it. An error, as opposed to an exception, is not caught here.
                Trmt.LOG.warn("A task queued for the end of the tick failed; the tick goes on", failure);
            }
        }
    }
}
