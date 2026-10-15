package com.trmtgtnh.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;

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
 * real particle texture and real transforms, and those are what the wrapper hands back for the
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
    private static final ResourceLocation GHOST = ResourceLocation.fromNamespaceAndPath(Trmt.MODID, "ghost");

    /** The worn stair's own block since 0.9.223 (BlockGhostStair), drawn with the same model. */
    private static final ResourceLocation GHOST_STAIR = ResourceLocation.fromNamespaceAndPath(Trmt.MODID, "ghost_stair");

    @Override
    public void onInitializeModelLoader(ModelLoadingPlugin.Context context) {
        context.modifyModelAfterBake()
            .register(ModelModifier.WRAP_PHASE, (baked, about) -> wrap(baked, about.topLevelId()));
    }

    /** Every variant of either ghost, wrapped once; every other model as it was. */
    // From 1.21 a block's model is named by a ModelResourceLocation of its own, no longer a kind of ResourceLocation:
    // the hook hands it as the model's top-level name, and none for a model that is only a part of another.
    static BakedModel wrap(BakedModel baked, ModelResourceLocation named) {
        if (baked == null || baked instanceof GhostModelFabric || named == null) return baked;
        ResourceLocation id = named.id();
        if (!Trmt.MODID.equals(id.getNamespace())) return baked;
        if (!GHOST.getPath()
            .equals(id.getPath())
            && !GHOST_STAIR.getPath()
                .equals(id.getPath()))
            return baked;
        return new GhostModelFabric(baked);
    }
}
