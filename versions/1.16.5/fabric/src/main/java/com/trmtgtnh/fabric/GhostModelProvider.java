package com.trmtgtnh.fabric;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import com.mojang.datafixers.util.Pair;

import net.fabricmc.fabric.api.client.model.ModelProviderContext;
import net.fabricmc.fabric.api.client.model.ModelVariantProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;

/**
 * Puts the ghost's own model in place of the one baked from its blockstate file, under Fabric.
 *
 * <p>
 * Forge fires an event after baking and the model can simply be swapped in the registry. Fabric has
 * no such event; what it has is this, which is asked about a variant before anything is baked and
 * may answer with a model of its own. So the answer is an unbaked model that, when it is baked,
 * bakes the real one and wraps it.
 *
 * <p>
 * The wrapping matters: the ghost's picture is code, but the thing it wraps is a real model with a
 * real particle texture and real transforms, and those are what the wrapper hands back for the
 * questions it has no opinion about. Baking it through the bakery rather than building one here is
 * also what keeps its textures on the atlas.
 *
 * <p>
 * Why the model is code at all: what a worn square looks like cannot be written down as variants -
 * sixty-nine depths across sixteen families is a set nobody should enumerate - so the blockstate
 * file names one placeholder and this replaces it.
 */
public class GhostModelProvider implements ModelVariantProvider {

    /** The block whose model is replaced, as the blockstate file names it. */
    private static final ResourceLocation GHOST = new ResourceLocation(Trmt.MODID, "ghost");

    /** What the placeholder blockstate points at, and what the wrapper is wrapped around. */
    private static final ResourceLocation PLACEHOLDER = new ResourceLocation(Trmt.MODID, "block/ghost");

    @Override
    public UnbakedModel loadModelVariant(ModelResourceLocation id, ModelProviderContext context) {
        if (!GHOST.getNamespace()
            .equals(id.getNamespace()) || !GHOST.getPath()
                .equals(id.getPath())) {
            return null;
        }
        return new Wrapper();
    }

    /** An unbaked model that bakes the placeholder and puts the ghost's own model round it. */
    private static final class Wrapper implements UnbakedModel {

        @Override
        public Collection<ResourceLocation> getDependencies() {
            // The placeholder, so the loader loads it before this is baked.
            return Collections.singletonList(PLACEHOLDER);
        }

        @Override
        public Collection<Material> getMaterials(Function<ResourceLocation, UnbakedModel> models,
            Set<Pair<String, String>> missing) {
            UnbakedModel placeholder = models.apply(PLACEHOLDER);
            return placeholder == null ? Collections.<Material>emptyList()
                : placeholder.getMaterials(models, missing);
        }

        @Override
        public BakedModel bake(ModelBakery bakery, Function<Material, TextureAtlasSprite> sprites,
            ModelState state, ResourceLocation location) {
            BakedModel placeholder = bakery.bake(PLACEHOLDER, state);
            if (placeholder == null) {
                Trmt.LOG.warn(
                    "The ghost's placeholder model would not bake, so worn ground will not be drawn on this loader.");
                return null;
            }
            return new GhostModelFabric(placeholder);
        }
    }
}
