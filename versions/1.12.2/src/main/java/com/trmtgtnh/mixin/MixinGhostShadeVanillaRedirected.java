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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.render.SunkLighting;
import com.trmtgtnh.client.texture.SunkLight;

/**
 * {@link MixinGhostShadeVanilla} in a game without MixinExtras (G17 R5, 2026-11-11): the same fetch remembered and the
 * same shade answered, redirected rather than wrapped because MixinExtras comes with MixinBooter and a pack that boots
 * its own Mixin, such as RLCraft, has neither. {@link OculusGate} applies this or that, never both, so this carries the
 * sunk top's light as well, word for word - that one needs no MixinExtras, but it lives in the mixin this stands in
 * for, and OlderMixinCopesTest holds the two copies the same. The one difference is a redirect's: another mod
 * redirecting the same call is refused by Mixin rather than chained.
 */
@Mixin(targets = "net.minecraft.client.renderer.BlockModelRenderer$AmbientOcclusionFace")
public abstract class MixinGhostShadeVanillaRedirected {

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

    @Redirect(
        method = "updateVertexBrightness",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/IBlockAccess;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"))
    private IBlockState trmt$remember(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        trmt$world = world;
        trmt$fetched = state;
        trmt$x = pos.getX();
        trmt$y = pos.getY();
        trmt$z = pos.getZ();
        return state;
    }

    @Redirect(
        method = "updateVertexBrightness",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;getAmbientOcclusionLightValue()F"))
    private float trmt$shade(IBlockState state) {
        if (state == trmt$fetched && state.getBlock() instanceof BlockGhost
            && BlockGhost.wholeAt(trmt$world, new BlockPos(trmt$x, trmt$y, trmt$z))) {
            BlockGhost.shadeSeen("vanilla's smooth lighting");
            return BlockGhost.WHOLE_SHADE;
        }
        return state.getAmbientOcclusionLightValue();
    }

    @Shadow
    @Final
    private float[] vertexColorMultiplier;

    @Shadow
    @Final
    private int[] vertexBrightness;

    /** The sunk top lit by its height: {@link MixinGhostShadeVanilla}'s, word for word. */
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
