package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import com.trmtgtnh.fabric.FabricEvents;

/**
 * A client has started watching a chunk, so it is sent that chunk's wear.
 *
 * <p>
 * Forge fires {@code ChunkWatchEvent.Watch} from here. Fabric has an event for a chunk loading on the
 * server, which is a different question entirely - a chunk can be loaded for an hour before anybody
 * comes near it, and three players can start watching one chunk that only ever loaded once.
 *
 * <p>
 * <strong>This is the exact moment that makes the client and the server agree.</strong> It is where
 * the game sends that player the chunk itself, and from here on the same player gets every block
 * change in it. Sending the wear from the same call means a client that has the chunk has the wear on
 * it, with no window in which it has one and not the other.
 */
@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    @Inject(method = "playerLoadedChunk", at = @At("TAIL"))
    private void trmt$watched(ServerPlayer player, Packet<?>[] packets, LevelChunk chunk, CallbackInfo callback) {
        if (player == null || chunk == null) return;
        FabricEvents.chunkWatched(
            player,
            chunk.getPos().x,
            chunk.getPos().z);
    }
}
