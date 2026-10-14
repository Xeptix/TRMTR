package com.trmtgtnh.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * What to do with a message once it has arrived.
 *
 * <p>
 * Both older editions use Forge's {@code IMessageHandler}, which hands the handler a
 * {@code MessageContext} alongside the message. Across this whole layer that context is used for
 * exactly one thing - {@code ctx.getServerHandler().player}, eleven times, to find out who sent a
 * server-bound packet. Nothing reads the side, nothing reads the connection. So the whole of it
 * collapses into one parameter.
 *
 * <p>
 * The {@code IMessage} return goes with it. Every handler in the layer already returns null; the
 * reply-by-returning-a-message idiom is Forge's, and neither loader offers it at this version.
 *
 * <p>
 * <strong>This is called on the main thread.</strong> Both loaders hand a packet over on the network
 * thread and expect the mod to move it; each loader module does that before calling here, so a
 * handler may touch the world directly the way its 1.12.2 twin does. The {@code MainThread} helper
 * the older editions wrap client work in is still used where it was used, which costs nothing when
 * the work is already on the right thread and keeps the two files readable side by side.
 */
public interface Receiver<T extends Message> {

    /**
     * @param message the message, already decoded
     * @param from    who sent it, or null when this arrived on a client - a client-bound packet has
     *                no sender to name, and the server is not a player
     */
    void onMessage(T message, ServerPlayer from);
}
