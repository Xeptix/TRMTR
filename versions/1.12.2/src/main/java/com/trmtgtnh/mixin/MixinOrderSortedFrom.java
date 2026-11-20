package com.trmtgtnh.mixin;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.client.render.TranslucentOrder;

/**
 * Where a buffer's faces were last sorted from (0.9.229, G17): see {@link TranslucentOrder}. The game's chunk builder
 * and its re-sort both end a chunk's blending pass in {@code sortVertexData}, handed the camera as it stood; that is
 * noted on the buffer, and forgotten when the buffer is begun again, so a buffer that was not sorted this time says
 * nothing rather than what the last chunk it held said.
 */
@Mixin(BufferBuilder.class)
public abstract class MixinOrderSortedFrom implements TranslucentOrder.SortedFrom {

    @Unique
    private float[] trmt$sortedFrom;

    @Inject(method = "sortVertexData", at = @At("HEAD"), require = 0)
    private void trmt$noteSort(float x, float y, float z, CallbackInfo callback) {
        trmt$sortedFrom = new float[] { x, y, z };
    }

    @Inject(method = "begin", at = @At("HEAD"), require = 0)
    private void trmt$forgetSort(int mode, VertexFormat format, CallbackInfo callback) {
        trmt$sortedFrom = null;
    }

    @Override
    public float[] trmt$sortedFrom() {
        return trmt$sortedFrom;
    }
}
