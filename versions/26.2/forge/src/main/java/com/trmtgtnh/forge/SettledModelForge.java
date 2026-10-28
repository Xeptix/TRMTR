package com.trmtgtnh.forge;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

import com.trmtgtnh.client.render.SettledModel;

/**
 * A settling block's model under Forge - snow or carpet - drawn down with the ground worn away under it (0.9.228; see
 * {@link SettledModel}, which says why the model is the seat).
 *
 * <p>
 * Forge hands a model no position when it asks for its parts; it asks the model first, square by square, for its data
 * ({@code IForgeBlockStateModel.getModelData}, which Forge's {@code SectionCompiler.compile} calls for every block drawn
 * as a model, with the section's region, before {@code ModelBlockRenderer.tesselateBlock(..., ModelData)} asks
 * {@code collectParts(RandomSource, List, ModelData)} - read from Forge 66.0.13's, 65.1.5's and 64.1.5's patched jars).
 * The ghost's model is told its square the same way (GhostModelForge). So the drop is read from the view
 * {@code getModelData} was handed and kept in the data, beside whatever the wrapped model put there, and the parts are
 * moved down by it. Asked with no data, or data without a drop, the model answers as it was baked.
 */
public class SettledModelForge implements BlockStateModel {

    /** Where the drop read at the square is kept, beside the wrapped model's own data. */
    private static final ModelProperty<Double> DROP = new ModelProperty<Double>();

    private final BlockStateModel wrapped;

    public SettledModelForge(BlockStateModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    @Nonnull
    public ModelData getModelData(@Nonnull BlockAndTintGetter level, @Nonnull BlockPos pos, @Nonnull BlockState state,
        @Nonnull ModelData tileData) {
        ModelData own = wrapped.getModelData(level, pos, state, tileData);
        double drop = SettledModel.dropAt(state, level, pos);
        if (drop <= 0.0D) return own;
        return own.derive()
            .with(DROP, Double.valueOf(drop))
            .build();
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> output, ModelData data) {
        Double drop = data == null ? null : data.get(DROP);
        if (drop == null || drop.doubleValue() <= 0.0D) {
            wrapped.collectParts(random, output, data == null ? ModelData.EMPTY : data);
            return;
        }
        List<BlockStateModelPart> own = new ArrayList<BlockStateModelPart>(2);
        wrapped.collectParts(random, own, data);
        SettledModel.lowerParts(own, drop.doubleValue(), output, SettledModel.LoweredPart::new);
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
        wrapped.collectParts(random, output);
    }

    @Override
    @Nonnull
    public Material.Baked particleMaterial() {
        return wrapped.particleMaterial();
    }

    @Override
    @Nonnull
    public Material.Baked particleMaterial(@Nonnull ModelData data) {
        return wrapped.particleMaterial(data);
    }

    @Override
    public int materialFlags() {
        return wrapped.materialFlags();
    }
}
