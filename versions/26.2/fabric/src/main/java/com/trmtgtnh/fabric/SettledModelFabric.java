package com.trmtgtnh.fabric;

import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.client.render.SettledModel;

/**
 * A settling block's model under Fabric - snow or carpet - drawn down with the ground worn away under it (0.9.228; see
 * {@link SettledModel}, which says why the model is the seat).
 *
 * <p>
 * Fabric API's renderer asks a block's model for its quads with the view being meshed and the square
 * ({@code FabricBlockStateModel.emitQuads}, which Indigo and every FRAPI renderer call - read from renderer-api-v1
 * 17.0.16, 14.1.4 and 13.0.8), so the drop is read from that view, as Settling's rule says, and never from the game's own
 * level. The wrapped model emits as it always does, through a transform pushed on the emitter that moves every corner
 * down by the drop - so whatever the wrapped model is, a vanilla one or another mod's, every quad it hands the
 * renderer comes down. The plain {@code collectParts}, which has no square, answers the model as it was baked
 * ({@link WrapperBlockStateModel}). Until 0.9.228 this lowered {@code BlockStateBase.getOffset} and read the game's
 * level on the mesher threads.
 */
public class SettledModelFabric extends WrapperBlockStateModel {

    public SettledModelFabric(BlockStateModel wrapped) {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
        RandomSource random, Predicate<Direction> cullTest) {
        double drop = SettledModel.dropAt(state, level, pos);
        if (drop <= 0.0D) {
            super.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        final float down = (float) drop;
        emitter.pushTransform(quad -> {
            for (int corner = 0; corner < 4; corner++) {
                quad.pos(corner, quad.x(corner), quad.y(corner) - down, quad.z(corner));
            }
            return true;
        });
        try {
            super.emitQuads(emitter, level, pos, state, random, cullTest);
        } finally {
            emitter.popTransform();
        }
    }

    /** The wrapped model's key, with the drop beside it: two squares share geometry only at one height. */
    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return SettledModel.geometryKey(super.createGeometryKey(level, pos, state, random),
            SettledModel.quietDropAt(state, level, pos));
    }
}
