package com.trmtgtnh.forge;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
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
// No bus named from Forge 64 (EventBus 7): each event says which group it is posted on - the registry event the
// mod's, most others the game's - and a subscriber left to choose registers each listener where its event is.
@Mod.EventBusSubscriber(modid = Trmt.MODID, value = Dist.CLIENT)
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
        // By 26.1 a block's baked models are kept by the state they draw (ModelBakery.BakingResult.blockStateModels)
        // rather than by a ModelResourceLocation, so the ghosts' own states are the ones swapped - every state of
        // "ghost" and of "ghost_stair", which is what the 1.21.1 edition matched by name. The map is the one the
        // manager builds its dispatch from once this event has run (client/resources/model/ModelManager.java).
        java.util.Map<BlockState, BlockStateModel> models = event.getResults()
            .blockStateModels();
        for (net.minecraft.world.level.block.Block ghost : com.trmtgtnh.block.ModBlocks.ghosts()) {
            for (BlockState each : ghost.getStateDefinition()
                .getPossibleStates()) {
                BlockStateModel baked = models.get(each);
                if (baked == null || baked instanceof GhostModelForge) continue;
                models.put(each, new GhostModelForge(baked));
                replaced++;
            }
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
