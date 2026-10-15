package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The server's light for one chunk column has just been laid over this client's own, so the squares
 * painted there are lit again - see {@code ClientOverlay.serverLightArrived}, which says why the server's
 * light is wrong for them and why it cannot simply be left to the next frame.
 *
 * <p>
 * <strong>Where the light lands, not where its packet arrives (0.9.225).</strong> 1.16.5 laid a light
 * packet's sections in its handler, and this hook sat at that handler's tail. From 1.18 both packets that
 * carry light - the light update and the chunk that brings its own - hand it to
 * {@code ClientLevel.queueLightUpdate}, and the queue runs later, on a tick of its own. At the handler's tail
 * the squares were re-lit against the light about to be replaced, and then the server's light, which lights
 * the whole block of earth it sees there, landed over every sunk cell: the 1.20.1 yard census found all 327
 * sunk squares of the second yard, and all 243 worn stairs, a level darker than the air above them. So the
 * hook is in the two steps the queued work runs, read from the game jar: {@code applyLightData}, which both
 * run, and {@code enableChunkLight}, which a chunk's own runs after it and before which its sections are not
 * yet lit at all.
 *
 * <p>
 * The body only calls out, for the reason {@link com.trmtgtnh.mixin.MixinPackRepository} gives.
 *
 * <p>
 * <strong>In each loader's module, not in common, from 0.9.219.</strong> It names a game method, so the
 * name goes into a refmap, and common's processor writes that refmap in Fabric's intermediary names: a
 * production Forge jar has it remapped on the way out, and a Forge development run reads it as it is and
 * refuses the mixin - which, on a class as early as {@code Block}, is a game that never starts. Every
 * mixin left in common injects with {@code remap = false} for that reason; see
 * {@code CommonMixinsNeedNoRefmapTest}. The Forge module has the same class.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinServerLightArrives {

    @Inject(method = "applyLightData", at = @At("TAIL"))
    private void trmt$relightPainted(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data, CallbackInfo callback) {
        com.trmtgtnh.client.ClientOverlay.serverLightArrived(chunkX, chunkZ);
    }

    @Inject(method = "enableChunkLight", at = @At("TAIL"))
    private void trmt$relightEnabled(LevelChunk chunk, int chunkX, int chunkZ, CallbackInfo callback) {
        com.trmtgtnh.client.ClientOverlay.serverLightArrived(chunkX, chunkZ);
    }
}
