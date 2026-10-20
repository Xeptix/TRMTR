package com.trmtgtnh.neoforge.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.ao.EnhancedBlockModelLighter;

import org.spongepowered.asm.mixin.Mixin;
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
 *
 * <p>
 * <strong>By 26.1 vanilla's smooth lighting is {@code BlockModelLighter}</strong> (client/renderer/block): a quad's
 * corners are worked out by {@code prepareQuadAmbientOcclusion} into the quad's {@code QuadInstance}, vertex by vertex,
 * and the face's bounds kept in {@code faceShape} under the same six indices 1.21.1's {@code shape} used. So this sits
 * at that method's tail and writes each vertex's light and shade there, the shade times the direction's as vanilla
 * scales its own ({@code CardinalLighting}, the quad's shade flag choosing the face's or the top's, as 1.21.1's
 * {@code getShade(direction, shade)} chose). The Forge module's copy is the same.
 *
 * <p>
 * <strong>And NeoForge's own lighter.</strong> NeoForge 26.1.2.122 draws blocks with {@code EnhancedBlockModelLighter}
 * unless its {@code enhancedLighting} setting is turned off, and it is on by default; it overrides
 * {@code prepareQuadAmbientOcclusion} without calling vanilla's, and samples an axis-aligned face inside its block the
 * way vanilla does, so a hook on vanilla's lighter alone would leave the sunk tops of a default NeoForge game drawn as
 * vanilla's path drew them before 0.9.223. 1.21.1's NeoForge drew through vanilla's lighter by default (its own
 * pipeline was off), so this hook lit every default game's sunk tops; it is on both lighters so that stays true. Both
 * fill {@code faceShape} first, through {@code prepareQuadShape}.
 */
@Mixin({ BlockModelLighter.class, EnhancedBlockModelLighter.class })
public abstract class MixinSunkLightVanilla {

    @Inject(method = "prepareQuadAmbientOcclusion", at = @At("TAIL"), require = 0)
    private void trmt$lightSunkTop(BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad,
        QuadInstance output, CallbackInfo callback) {
        if (quad == null || quad.direction() != Direction.UP || !(state.getBlock() instanceof BlockGhost)) return;
        float[] shape = ((LighterFaceShape) (Object) this).trmt$faceShape();
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
        CardinalLighting cardinal = level.cardinalLighting();
        float directional = quad.materialInfo()
            .shade() ? cardinal.byFace(Direction.UP) : cardinal.up();
        for (int i = 0; i < 4; i++) {
            output.setLightCoords(i, SunkLight.packed(corners[i][0], corners[i][1]));
            output.setColor(i, ARGB.gray(corners[i][2] * directional));
        }
        SunkLighting.noteLit();
    }
}
