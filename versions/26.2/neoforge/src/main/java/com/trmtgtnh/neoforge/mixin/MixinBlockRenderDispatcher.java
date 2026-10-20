package com.trmtgtnh.neoforge.mixin;

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

import com.trmtgtnh.neoforge.GhostSeat;

/**
 * Tells the ghost model which square it is drawing, on vanilla's path, for a model asked without being told.
 *
 * <p>
 * <strong>This was the single most expensive line in the whole port's rendering, and it was here
 * because the rendering spike went looking and found nothing cheaper.</strong> Fabric's renderer
 * hands a model the {@code BlockPos}; Forge up to 1.21.1 handed it {@code ModelData}, and nothing
 * supplies model data for a block that is not a block entity. A tile entity per worn square is not
 * an option - a road has thousands. So the position is taken from the renderer on its way past.
 *
 * <p>
 * <strong>On 1.16.5 it was on {@code ModelBlockRenderer.tesselateBlock}, and that was a door nothing
 * opened.</strong> That method was vanilla's convenience wrapper, and Forge replaced the entire block
 * lighting pipeline underneath it: the chunk mesher called
 * {@code BlockRenderDispatcher.renderModel}, which went straight to
 * {@code ModelBlockRenderer.renderModel} and on into {@code ForgeBlockModelRenderer}, so
 * {@code tesselateBlock} and both of its {@code tesselateWithAO} / {@code tesselateWithoutAO}
 * siblings were dead code on that loader.
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
 * From 1.19 to 1.21.1 the chunk mesher's call was {@code BlockRenderDispatcher.renderBatched}, which Forge and NeoForge
 * carried to the model with the model data and the pass being built. <strong>From 1.21.5 that door is
 * {@code ModelBlockRenderer.tesselateBlock} again</strong>, and this time it is the one every chunk comes through: 26.1's
 * {@code SectionCompiler.compile} calls it for every block, and NeoForge's patched copy of it calls the model's
 * five-argument {@code collectParts} with the level and the position (both read from 26.2.0.88's patched jar), so the
 * square normally reaches the model as an argument. The seat taken here is the second door, as 1.21.1's was: what a
 * renderer or mod asks through the plain two-argument {@code collectParts} on this path still finds the square. The
 * class keeps its name, which the rules and the tests know it by.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinBlockRenderDispatcher {

    /**
     * Every parameter, because that is what Mixin matches on. {@code tesselateBlock} is not patched by NeoForge in
     * 26.1.2.122 (its descriptor is vanilla's), so the descriptor is vanilla's, spelled out. The only way to be sure
     * which of the two a method is, is to try it and read what Mixin says it expected.
     */
    @Inject(method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFF"
        + "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V", at = @At("HEAD"))
    private void trmt$seatSquare(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
        BlockPos pos, BlockState state, BlockStateModel model, long seed, CallbackInfo callback) {
        GhostSeat.sit(level, pos);
    }

    @Inject(method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFF"
        + "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V", at = @At("RETURN"))
    private void trmt$leaveSquare(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
        BlockPos pos, BlockState state, BlockStateModel model, long seed, CallbackInfo callback) {
        GhostSeat.stand();
    }
}
