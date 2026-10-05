package com.trmtgtnh.forge;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.texture.WearTextures;

/**
 * Where this mod's wear sprites join the block atlas, under Forge.
 *
 * <p>
 * Forge fires an event on either side of a stitch, which is exactly the pair {@code WearTextures}
 * wants: one call to plan the sprite set and name it, one to resolve those names into the atlas's
 * own sprites once they exist. Fabric has no equivalent at this version and needs a mixin for the
 * same two moments, which is the only part of this pipeline either loader has to know about.
 *
 * <p>
 * Only the block atlas. The event fires for every atlas the game builds - the particle sheet, the
 * banner sheet, a mod's own - and none of those is this mod's business.
 */
@Mod.EventBusSubscriber(modid = Trmt.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeTextures {

    private ForgeTextures() {}

    /**
     * Plans the sprite set and names it, before the stitcher runs.
     *
     * <p>
     * {@code addSprite} is how a name reaches the atlas without a model referring to it, which is
     * the whole difficulty: nothing in any model mentions a wear texture, so without this the atlas
     * would never ask the pack for one.
     */
    @SubscribeEvent
    public static void beginStitch(TextureStitchEvent.Pre event) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(
            event.getMap()
                .location())) {
            return;
        }
        WearTextures.beginStitch(event::addSprite);
    }

    /**
     * Puts the ghost's own model in place of the one the loader baked from its blockstate file.
     *
     * <p>
     * Every variant, because a ghost has one state and the file that describes it is a placeholder:
     * what a worn square looks like cannot be written down as variants - sixty-nine depths across
     * sixteen families is a set nobody should enumerate - so the model is code, and this is where
     * the code is swapped in.
     */
    @SubscribeEvent
    public static void bakeModels(ModelBakeEvent event) {
        int replaced = 0;
        for (ResourceLocation each : new java.util.ArrayList<ResourceLocation>(event.getModelRegistry()
            .keySet())) {
            if (!(each instanceof ModelResourceLocation)) continue;
            if (!Trmt.MODID.equals(each.getNamespace())) continue;
            // "ghost", which is what ModBlocks registers it as - not the 1.12.2 edition's
            // "ghost_grass", which is a name this edition never had.
            if (!"ghost".equals(each.getPath())) continue;
            BakedModel baked = event.getModelRegistry()
                .get(each);
            if (baked == null || baked instanceof GhostModelForge) continue;
            event.getModelRegistry()
                .put(each, new GhostModelForge(baked));
            replaced++;
        }
        Trmt.LOG.info("Put the ghost model in place of {} baked variant(s)", Integer.valueOf(replaced));
    }

    /** Resolves the planned names into the atlas's own sprites, now that there are some. */
    @SubscribeEvent
    public static void endStitch(TextureStitchEvent.Post event) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(
            event.getMap()
                .location())) {
            return;
        }
        WearTextures.endStitch(event.getMap());
    }
}
