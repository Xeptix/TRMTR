package com.trmtgtnh.neoforge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.client.render.SettledModel;

/**
 * A settling block's model under NeoForge - snow or carpet - drawn down with the ground worn away under it (0.9.228; see
 * {@link SettledModel}, which says why the model is the seat).
 *
 * <p>
 * NeoForge asks a block's model for its parts with the level, the position and the state
 * ({@code BlockStateModelExtension.collectParts}, which the patched {@code ModelBlockRenderer.tesselateBlock} calls with
 * the section's region - read from 26.3.0.94's, 26.2's and 26.1.2's patched jars, bytecode 27), and so does any renderer
 * that honours NeoForge's model API. That form is answered here: the wrapped model's own parts, each moved down by the
 * drop read from the view it was handed. The plain form, which has no square, answers the model as it was baked.
 */
public class SettledModelNeoForge implements BlockStateModel {

    private final BlockStateModel wrapped;

    public SettledModelNeoForge(BlockStateModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
        List<BlockStateModelPart> parts) {
        double drop = SettledModel.dropAt(state, level, pos);
        if (drop <= 0.0D) {
            wrapped.collectParts(level, pos, state, random, parts);
            return;
        }
        List<BlockStateModelPart> own = new ArrayList<BlockStateModelPart>(2);
        wrapped.collectParts(level, pos, state, random, own);
        SettledModel.lowerParts(own, drop, parts, LoweredPart::new);
    }

    /** The wrapped model's key, with the drop beside it: two squares share geometry only at one height. */
    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return SettledModel.geometryKey(wrapped.createGeometryKey(level, pos, state, random),
            SettledModel.quietDropAt(state, level, pos));
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        wrapped.collectParts(random, parts);
    }

    @Override
    public Material.Baked particleMaterial() {
        return wrapped.particleMaterial();
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return wrapped.particleMaterial(level, pos, state);
    }

    @Override
    public int materialFlags() {
        return wrapped.materialFlags();
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return wrapped.materialFlags(level, pos, state);
    }

    /** NeoForge's quads carry normals and colors of their own, which a moved quad keeps. */
    static BakedQuad move(BakedQuad quad, float drop) {
        return new BakedQuad(SettledModel.down(quad.position0(), drop), SettledModel.down(quad.position1(), drop),
            SettledModel.down(quad.position2(), drop), SettledModel.down(quad.position3(), drop), quad.packedUV0(),
            quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(), quad.materialInfo(),
            quad.bakedNormals(), quad.bakedColors());
    }

    /** A lowered part that also answers NeoForge's own question of it, ambient occlusion as three states. */
    static final class LoweredPart extends SettledModel.LoweredPart {

        LoweredPart(BlockStateModelPart part, double drop) {
            super(part, drop);
        }

        @Override
        public TriState ambientOcclusion() {
            return part.ambientOcclusion();
        }
    }
}
