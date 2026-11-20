package com.trmtgtnh.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.util.concurrent.ListenableFuture;
import com.trmtgtnh.client.render.TranslucentOrder;

/**
 * The order a chunk's blending pass is handed to the card in (0.9.229, G17): see {@link TranslucentOrder}. Every
 * build and every re-sort reaches the card through {@code uploadChunk}, called again on the client thread when it was
 * first asked from a worker, and an upload that was cancelled never gets there; so what is noted here, on the client
 * thread, is what the card draws, whichever task sent it and in whatever order they landed.
 */
@Mixin(ChunkRenderDispatcher.class)
public abstract class MixinOrderUploaded {

    @Inject(method = "uploadChunk", at = @At("HEAD"), require = 0)
    private void trmt$noteOrder(BlockRenderLayer layer, BufferBuilder buffer, RenderChunk chunk,
        CompiledChunk compiled, double distance, CallbackInfoReturnable<ListenableFuture<Object>> callback) {
        if (layer != BlockRenderLayer.TRANSLUCENT || !Minecraft.getMinecraft()
            .isCallingFromMinecraftThread()) return;
        TranslucentOrder.uploadSeen();
        TranslucentOrder.get()
            .uploaded(chunk, ((TranslucentOrder.SortedFrom) buffer).trmt$sortedFrom());
    }
}
