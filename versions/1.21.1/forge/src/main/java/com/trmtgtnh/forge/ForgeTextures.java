package com.trmtgtnh.forge;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.texture.WearTextures;

/**
 * Where this mod's wear sprites join the block atlas, under Forge.
 *
 * <p>
 * On 1.16.5 Forge fired an event on either side of a stitch, which was exactly the pair {@code WearTextures} wants:
 * one call to plan the sprite set and name it, one to resolve those names into the atlas's own sprites once they
 * exist. From 1.19.3 the first is gone with the old atlas, and the plan is made by MixinSpriteLoader as the atlas
 * begins to load; the second is still Forge's event, and is here.
 *
 * <p>
 * Only the block atlas. The event fires for every atlas the game builds - the particle sheet, the
 * banner sheet, a mod's own - and none of those is this mod's business.
 */
@Mod.EventBusSubscriber(modid = Trmt.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeTextures {

    private ForgeTextures() {}

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
    public static void bakeModels(ModelEvent.ModifyBakingResult event) {
        int replaced = 0;
        // From 1.21 a block's model is named by a ModelResourceLocation of its own, no longer a kind of ResourceLocation.
        for (ModelResourceLocation each : new java.util.ArrayList<ModelResourceLocation>(event.getModels()
            .keySet())) {
            if (!Trmt.MODID.equals(each.id()
                .getNamespace())) continue;
            // "ghost", which is what ModBlocks registers it as - not the 1.12.2 edition's
            // "ghost_grass", which is a name this edition never had - and "ghost_stair", the worn
            // stair's own block since 0.9.223 (BlockGhostStair), which draws with the same model.
            if (!"ghost".equals(each.id()
                .getPath())
                && !"ghost_stair".equals(
                    each.id()
                        .getPath()))
                continue;
            BakedModel baked = event.getModels()
                .get(each);
            if (baked == null || baked instanceof GhostModelForge) continue;
            event.getModels()
                .put(each, new GhostModelForge(baked));
            replaced++;
        }
        Trmt.LOG.info("Put the ghost model in place of {} baked variant(s)", Integer.valueOf(replaced));
    }

    /** Resolves the planned names into the atlas's own sprites, now that there are some. */
    @SubscribeEvent
    public static void endStitch(TextureStitchEvent.Post event) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(
            event.getAtlas()
                .location())) {
            return;
        }
        WearTextures.endStitch(event.getAtlas());
    }
}
