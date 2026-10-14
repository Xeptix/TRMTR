package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.data.ModelData;

import com.trmtgtnh.forge.GhostSeat;

/**
 * Tells the ghost model which square it is drawing, which Forge does not.
 *
 * <p>
 * <strong>This is the single most expensive line in the whole port's rendering, and it is here
 * because the rendering spike went looking and found nothing cheaper.</strong> Fabric's renderer
 * hands a model the {@code BlockPos}; Forge at this version hands it {@code ModelData}, and nothing
 * supplies model data for a block that is not a block entity. A tile entity per worn square is not
 * an option - a road has thousands. So the position is taken from the renderer on its way past.
 *
 * <p>
 * <strong>It used to be on {@code ModelBlockRenderer.tesselateBlock}, and that is a door nothing
 * opens.</strong> That method is vanilla's convenience wrapper, and Forge replaces the entire block
 * lighting pipeline underneath it: the chunk mesher calls
 * {@code BlockRenderDispatcher.renderModel}, which goes straight to
 * {@code ModelBlockRenderer.renderModel} and on into {@code ForgeBlockModelRenderer}, so
 * {@code tesselateBlock} and both of its {@code tesselateWithAO} / {@code tesselateWithoutAO}
 * siblings are dead code on this loader.
 *
 * <p>
 * Every one of those three injections applied perfectly happily. The mixin config sets
 * {@code defaultRequire: 1}, so an injector that matched nothing would have stopped the game, and a
 * handler given a deliberately wrong signature was refused outright - both of which say the method
 * was found and injected. Neither says it is ever <em>called</em>, and it was not: the ghost model
 * was asked for quads five hundred times a run while the seat was taken exactly nought times.
 *
 * <p>
 * What that cost is worth writing down, because it is the whole lesson. Every worn square in the
 * world drew nothing at all. The hole it left looked so much like the feature working - sunken, the
 * right shape, dark like packed earth - that the spike photographed it, measured the rut it could
 * stand in, and called it a success; and the thing somebody finally noticed was not that the ground
 * was missing but that it was <em>too dark</em>. The floor in those pictures is the top of the block
 * underneath, and the walls are the sides of the neighbours, both lit as the inside of a pit.
 *
 * <p>
 * Found by printing a stack trace from inside {@code getQuads} rather than by reading the renderer.
 * Four wrong explanations were measured and discarded first - the lighting, the ambient occlusion,
 * the composed pictures, the tint - and each of those measurements was cheap and none of them would
 * ever have found this. Ask who is calling you before asking why they are unhappy.
 *
 * <p>
 * From 1.19 the chunk mesher's call is {@code renderBatched}, which Forge carries to the model with the model data and
 * the pass being built; 1.16.5's was {@code renderModel}, the same door under the name it had then.
 *
 * <p>
 * It lives in this module rather than in common for the reason the other method-naming mixin does:
 * common's annotation processor writes a refmap in Fabric's intermediary names, which Forge refuses.
 * Here the processor writes Forge's own.
 */
@Mixin(BlockRenderDispatcher.class)
public abstract class MixinBlockRenderDispatcher {

    /**
     * Every parameter, because that is what Mixin matches on - and the last of them is Forge's.
     *
     * <p>
     * {@code renderBatched} is patched to carry {@code ModelData} and the pass, and kept beside vanilla's own
     * shorter one, which hands on to it - so the descriptor is spelled out, and the seat is taken once, at the one
     * every call comes through. That is the opposite of the trap next door: {@code
     * tesselateBlock} is <em>not</em> patched, so a handler written with the extra argument was
     * refused there. The only way to be sure which of the two a method is, is to try it and read
     * what Mixin says it expected.
     */
    @Inject(method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;"
            + "Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"))
    private void trmt$seatSquare(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose,
        VertexConsumer into, boolean checkSides, RandomSource random, ModelData data, RenderType pass,
        CallbackInfo callback) {
        GhostSeat.sit(level, pos);
    }

    @Inject(method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;"
            + "Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("RETURN"))
    private void trmt$leaveSquare(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose,
        VertexConsumer into, boolean checkSides, RandomSource random, ModelData data, RenderType pass,
        CallbackInfo callback) {
        GhostSeat.stand();
    }
}
