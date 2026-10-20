package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
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
 *
 * <p>
 * <strong>By 26.1 the door is {@code ModelBlockRenderer.tesselateBlock} again.</strong> The chunk mesher
 * ({@code SectionCompiler}) asks the model for its data and hands it to Forge's own {@code tesselateBlock}, the one
 * patched to take {@code ModelData}, which asks the model for its parts with that data (read from Forge 64's patches to
 * SectionCompiler and ModelBlockRenderer). {@code BlockRenderDispatcher} and {@code renderBatched} are gone. So the
 * seat is taken on that patched method, every parameter spelled out - vanilla's shorter one hands on to it - and the
 * class keeps its name so the mixin configs and the guards that name it still find it.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinBlockRenderDispatcher {

    @Inject(method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFF"
        + "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J"
        + "Lnet/minecraftforge/client/model/data/ModelData;)V", at = @At("HEAD"))
    private void trmt$seatSquare(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
        BlockPos pos, BlockState state, BlockStateModel model, long seed, ModelData data, CallbackInfo callback) {
        GhostSeat.sit(level, pos);
    }

    @Inject(method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFF"
        + "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J"
        + "Lnet/minecraftforge/client/model/data/ModelData;)V", at = @At("RETURN"))
    private void trmt$leaveSquare(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
        BlockPos pos, BlockState state, BlockStateModel model, long seed, ModelData data, CallbackInfo callback) {
        GhostSeat.stand();
    }
}
