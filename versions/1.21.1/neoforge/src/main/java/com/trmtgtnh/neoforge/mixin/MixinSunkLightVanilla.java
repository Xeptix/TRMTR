package com.trmtgtnh.neoforge.mixin;

import java.util.BitSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.render.SunkLighting;
import com.trmtgtnh.client.texture.SunkLight;

/**
 * A sunk top lit by its height, as Forge's light pipeline lights it (0.9.223; Xep, 2026-10-11: "On the bottom 3 images,
 * I prefer how the middle one looks the most, honestly") - the 1.7.10 edition's {@code MixinSunkLight}.
 *
 * <p>
 * Vanilla's smooth lighting, which Forge draws through unless its experimental light pipeline is on, takes a face
 * that is not flush with its block's top from inside the block - {@code flags.get(0) ? pos.relative(direction) : pos}
 * - so a sunk square's every corner was shaded by the ground beside it, and the turf part's sunk grass drew at
 * (55,39,27) where Rubidium and Embeddium draw it at (85,60,41). Once this face's corners are worked out, a sunk
 * ghost's top has them replaced by what {@link SunkLight} answers for each, which is Forge's lighter's arithmetic,
 * times the direction's shade as vanilla multiplies its own. The corners are the quad's own, from the bounds the
 * renderer measured it by - {@code shape}, west and east, north and south - in the order vanilla fills an upward
 * face's (AmbientVertexRemap.UP): (west, north), (west, south), (east, south), (east, north), which is the order the
 * ghost model builds its tops in.
 */
@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public abstract class MixinSunkLightVanilla {

    @Shadow
    @Final
    private float[] brightness;

    @Shadow
    @Final
    private int[] lightmap;

    @Inject(method = "calculate", at = @At("TAIL"), require = 0)
    private void trmt$lightSunkTop(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction direction,
        float[] shape, BitSet flags, boolean shade, CallbackInfo callback) {
        if (direction != Direction.UP || !(state.getBlock() instanceof BlockGhost)) return;
        float height = shape[Direction.UP.get3DDataValue()];
        if (height >= 0.999F || !BlockGhost.litAsSunk(level, pos)) return;
        float west = shape[Direction.WEST.get3DDataValue()], east = shape[Direction.EAST.get3DDataValue()];
        float north = shape[Direction.NORTH.get3DDataValue()], south = shape[Direction.SOUTH.get3DDataValue()];
        float[][] corners = SunkLighting.topCorners(
            level,
            state,
            pos,
            height,
            new float[][] { { west, north }, { west, south }, { east, south }, { east, north } });
        float directional = level.getShade(direction, shade);
        for (int i = 0; i < 4; i++) {
            lightmap[i] = SunkLight.packed(corners[i][0], corners[i][1]);
            brightness[i] = corners[i][2] * directional;
        }
        SunkLighting.noteLit();
    }
}
