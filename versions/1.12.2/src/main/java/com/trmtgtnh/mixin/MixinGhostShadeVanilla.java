package com.trmtgtnh.mixin;

import java.util.BitSet;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.render.SunkLighting;
import com.trmtgtnh.client.texture.SunkLight;

/**
 * Vanilla's smooth lighting, which draws when Forge's light pipeline is off - and OptiFine switches it off and
 * draws through this, its own copy of it: a whole ghost shades the corners beside it, as the block it replaced
 * did - see {@link BlockGhost#wholeAt}.
 *
 * <p>
 * Each corner's shade is read as {@code world.getBlockState(pos).getAmbientOcclusionLightValue()}, the state
 * asked straight after it is fetched. So the fetch is remembered, and the reading answered from it where the
 * state is that same ghost - by identity, so a reading that does not follow its own fetch is left alone.
 * Wrapped rather than redirected, so another mod wrapping the same calls is chained rather than refused.
 */
@Mixin(targets = "net.minecraft.client.renderer.BlockModelRenderer$AmbientOcclusionFace")
public abstract class MixinGhostShadeVanilla {

    @Unique
    private IBlockAccess trmt$world;

    @Unique
    private IBlockState trmt$fetched;

    @Unique
    private int trmt$x;

    @Unique
    private int trmt$y;

    @Unique
    private int trmt$z;

    @WrapOperation(
        method = "updateVertexBrightness",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/IBlockAccess;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"))
    private IBlockState trmt$remember(IBlockAccess world, BlockPos pos, Operation<IBlockState> original) {
        IBlockState state = original.call(world, pos);
        trmt$world = world;
        trmt$fetched = state;
        trmt$x = pos.getX();
        trmt$y = pos.getY();
        trmt$z = pos.getZ();
        return state;
    }

    @WrapOperation(
        method = "updateVertexBrightness",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;getAmbientOcclusionLightValue()F"))
    private float trmt$shade(IBlockState state, Operation<Float> original) {
        if (state == trmt$fetched && state.getBlock() instanceof BlockGhost
            && BlockGhost.wholeAt(trmt$world, new BlockPos(trmt$x, trmt$y, trmt$z))) {
            BlockGhost.shadeSeen("vanilla's smooth lighting");
            return BlockGhost.WHOLE_SHADE;
        }
        return original.call(state);
    }

    @Shadow
    @Final
    private float[] vertexColorMultiplier;

    @Shadow
    @Final
    private int[] vertexBrightness;

    /**
     * A sunk top lit by its height, as Forge's light pipeline lights it (0.9.223; Xep, 2026-10-11: "On the bottom 3
     * images, I prefer how the middle one looks the most, honestly") - the 1.7.10 edition's {@code MixinSunkLight}.
     *
     * <p>
     * Vanilla's smooth lighting takes a face that is not flush with its block's top from inside the block -
     * {@code shapeState.get(0) ? centerPos.offset(direction) : centerPos} - so a sunk square's every corner was shaded
     * by
     * the ground beside it. Once this face's corners are worked out, a sunk ghost's top has them replaced by what
     * {@link SunkLight} answers for each, which is Forge's lighter's arithmetic. The corners are the quad's own, from
     * the
     * bounds the renderer measured it by - {@code faceShape}, west and east, north and south - in the order vanilla
     * fills an upward face's: (west, north), (west, south), (east, south), (east, north), which is the order the ghost
     * model builds its tops in. Here rather than round the quad loop that calls this, because OptiFine draws through
     * this same method from a loop of its own.
     */
    @Inject(method = "updateVertexBrightness", at = @At("TAIL"), require = 0)
    private void trmt$lightSunkTop(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing direction,
        float[] faceShape, BitSet shapeState, CallbackInfo callback) {
        if (direction != EnumFacing.UP || !(state.getBlock() instanceof BlockGhost)) return;
        float height = faceShape[EnumFacing.UP.getIndex()];
        if (height >= 0.999F || !BlockGhost.litAsSunk(world, pos)) return;
        float west = faceShape[EnumFacing.WEST.getIndex()], east = faceShape[EnumFacing.EAST.getIndex()];
        float north = faceShape[EnumFacing.NORTH.getIndex()], south = faceShape[EnumFacing.SOUTH.getIndex()];
        float[][] corners = SunkLighting.topCorners(
            world,
            state,
            pos,
            height,
            new float[][] { { west, north }, { west, south }, { east, south }, { east, north } });
        for (int i = 0; i < 4; i++) {
            vertexBrightness[i] = SunkLight.packed(corners[i][0], corners[i][1]);
            vertexColorMultiplier[i] = corners[i][2];
        }
        SunkLighting.noteLit();
    }
}
