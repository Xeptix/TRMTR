package com.trmtgtnh.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.render.SettledModel;

/**
 * Puts the ghost's own model in place of the one baked from its blockstate file, under Fabric.
 *
 * <p>
 * On 1.16.5 Forge fired an event after baking and Fabric had none, so this answered for the variant before anything
 * was baked, with an unbaked model that baked the real one and wrapped it. From Fabric API's model loading v1 (1.20)
 * Fabric has the same moment Forge has - a model modified after it is baked - so this is now what ForgeTextures'
 * {@code bakeModels} is: the baked model of each ghost variant, wrapped.
 *
 * <p>
 * The wrapping matters: the ghost's picture is code, but the thing it wraps is a real model with a
 * real particle texture, and that is what the wrapper hands back for the
 * questions it has no opinion about. Baked by the game from the placeholder blockstate rather than built here,
 * which is also what keeps its textures on the atlas.
 *
 * <p>
 * Why the model is code at all: what a worn square looks like cannot be written down as variants -
 * sixty-nine depths across sixteen families is a set nobody should enumerate - so the blockstate
 * file names one placeholder and this replaces it.
 */
public final class GhostModelProvider implements ModelLoadingPlugin {

    /** The block whose model is replaced, as the blockstate file names it. */
    private static final Identifier GHOST = Identifier.fromNamespaceAndPath(Trmt.MODID, "ghost");

    /** The worn stair's own block since 0.9.223 (BlockGhostStair), drawn with the same model. */
    private static final Identifier GHOST_STAIR = Identifier.fromNamespaceAndPath(Trmt.MODID, "ghost_stair");

    @Override
    public void initialize(ModelLoadingPlugin.Context context) {
        // A new bake makes new models, so the settled quads moved for the old ones are let go.
        SettledModel.forget();
        // From Fabric API 0.155 (26.1; the split came with 1.21.4's model loading) a block's models are modified
        // after bake on an event of their own, handed the state the model is for rather than a model name.
        context.modifyBlockModelAfterBake()
            .register(ModelModifier.WRAP_PHASE, (baked, about) -> wrap(baked, about.state()));
    }

    /** Every variant of either ghost, wrapped once; every other model as it was. */
    // From 1.21.4 a block's model is baked for each of its states and the hook names the state, so the ghost is
    // recognised by the block's own registry name - the name its blockstate file is under, as 1.21.1 read it from
    // the model's.
    //
    // And every block that rests on the ground - snow and carpet - wrapped so it is drawn down with the ground worn away
    // under it, asked with the view it is meshed in (0.9.228; see SettledModel): every state of such a block, whatever
    // the settling switch says now, because the models stay baked while a server's rules change it.
    static BlockStateModel wrap(BlockStateModel baked, BlockState state) {
        if (baked == null || baked instanceof GhostModelFabric || baked instanceof SettledModelFabric || state == null)
            return baked;
        if (SettledModel.wraps(state)) return new SettledModelFabric(baked);
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!Trmt.MODID.equals(id.getNamespace())) return baked;
        if (!GHOST.getPath()
            .equals(id.getPath())
            && !GHOST_STAIR.getPath()
                .equals(id.getPath()))
            return baked;
        return new GhostModelFabric(baked);
    }
}
