package com.trmtgtnh.neoforge;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.texture.WearTextures;

/**
 * Where this mod's wear sprites join the block atlas, under NeoForge. The Forge module's ForgeTextures carried across.
 *
 * <p>
 * The plan of the sprite set is made by MixinSpriteLoader as the atlas begins to load; resolving those names into the
 * atlas's own sprites once they exist is the loader's event, and is here - Forge's {@code TextureStitchEvent.Post},
 * which NeoForge calls {@code TextureAtlasStitchedEvent}.
 *
 * <p>
 * Only the block atlas. The event fires for every atlas the game builds - the particle sheet, the banner sheet, a mod's
 * own - and none of those is this mod's business.
 */
// No bus named: FML 4 ignores it and sends each listener to the mod's bus or the game's by its event's type
// (IModBusEvent or not), where Forge took the bus from this annotation.
@EventBusSubscriber(modid = Trmt.MODID, value = Dist.CLIENT)
public final class NeoForgeTextures {

    private NeoForgeTextures() {}

    /**
     * Puts the ghost's own model in place of the one the loader baked from its blockstate file.
     *
     * <p>
     * Every variant, because a ghost has one state and the file that describes it is a placeholder: what a worn square
     * looks like cannot be written down as variants - sixty-nine depths across sixteen families is a set nobody should
     * enumerate - so the model is code, and this is where the code is swapped in.
     */
    @SubscribeEvent
    public static void bakeModels(ModelEvent.ModifyBakingResult event) {
        int replaced = 0;
        // From 1.21 a block's model is named by a ModelResourceLocation of its own, no longer a kind of ResourceLocation.
        for (ModelResourceLocation each : new java.util.ArrayList<ModelResourceLocation>(event.getModels()
            .keySet())) {
            if (!Trmt.MODID.equals(each.id()
                .getNamespace())) continue;
            // "ghost", which is what ModBlocks registers it as, and "ghost_stair", the worn stair's own block since
            // 0.9.223 (BlockGhostStair), which draws with the same model.
            if (!"ghost".equals(each.id()
                .getPath())
                && !"ghost_stair".equals(
                    each.id()
                        .getPath()))
                continue;
            BakedModel baked = event.getModels()
                .get(each);
            if (baked == null || baked instanceof GhostModelNeoForge) continue;
            event.getModels()
                .put(each, new GhostModelNeoForge(baked));
            replaced++;
        }
        Trmt.LOG.info("Put the ghost model in place of {} baked variant(s)", Integer.valueOf(replaced));
    }

    /** Resolves the planned names into the atlas's own sprites, now that there are some. */
    @SubscribeEvent
    public static void endStitch(TextureAtlasStitchedEvent event) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(
            event.getAtlas()
                .location())) {
            return;
        }
        WearTextures.endStitch(event.getAtlas());
    }
}
