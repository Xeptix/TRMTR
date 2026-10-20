package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;

import com.trmtgtnh.fabric.FabricEvents;

/**
 * A client has been sent a chunk, so it is sent that chunk's wear.
 *
 * <p>
 * Forge fires {@code ChunkWatchEvent} for this. Fabric has an event for a chunk loading on the server, which is a
 * different question entirely - a chunk can be loaded for an hour before anybody comes near it, and three players can
 * start watching one chunk that only ever loaded once.
 *
 * <p>
 * <strong>This is the exact moment that makes the client and the server agree.</strong> It is where the game sends
 * that player the chunk itself, and from here on the same player gets every block change in it. Sending the wear from
 * the same call means a client that has the chunk has the wear on it, with no window in which it has one and not the
 * other.
 *
 * <p>
 * Named for the class it hooked until 1.20.1, {@code ChunkMap.playerLoadedChunk}, which sent the chunk there and then.
 * From 1.20.2 the chunk map only queues a chunk for a player, and {@code PlayerChunkSender} sends the queue a few at a
 * time as the client keeps up - so the moment the chunk goes is its private {@code sendChunk}, and the wear follows it
 * from there, as it followed the packet before.
 */
@Mixin(PlayerChunkSender.class)
public abstract class MixinChunkMap {

    @Inject(method = "sendChunk", at = @At("TAIL"))
    private static void trmt$watched(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk,
        CallbackInfo callback) {
        if (connection == null || connection.player == null || chunk == null) return;
        FabricEvents.chunkWatched(
            connection.player,
            chunk.getPos().x(),
            chunk.getPos().z());
    }
}
