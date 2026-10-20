package com.trmtgtnh.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.trmtgtnh.Trmt;

/**
 * The two client registrations that have to wait until the registries are full.
 *
 * <p>
 * Both name the golem's {@code EntityType} and its {@code MenuType}, and both were in
 * {@code TrmtForge}'s constructor first. That does not work and does not fail to compile: a mod's
 * constructor runs before any registry event has fired, so {@code ModEntities.golemType()} answered
 * null and the game died on the title screen with a {@code NullPointerException} out of a lambda.
 * Found by starting the game, which is the only thing that was ever going to find it.
 *
 * <p>
 * {@code FMLClientSetupEvent} is the right moment: every registry is full, and it only fires on a
 * client, so neither of these is ever reached on a server. Its own class is marked
 * {@code Dist.CLIENT} as well, for the reason the side scan exists - a class that names a renderer
 * and a screen should say out loud that it is a client's, rather than relying on nobody calling it.
 *
 * <p>
 * The counterparts on Fabric are two lines in {@code TrmtFabricClient}, which needs no equivalent of
 * this class because its client initialiser already runs after the common one.
 */
// No bus named from Forge 64 (EventBus 7): each event says which group it is posted on - the registry event the
// mod's, most others the game's - and a subscriber left to choose registers each listener where its event is.
@Mod.EventBusSubscriber(modid = Trmt.MODID, value = Dist.CLIENT)
public final class ForgeClientSetup {

    private ForgeClientSetup() {}

    @SubscribeEvent
    public static void client(FMLClientSetupEvent event) {
        // On the game's own thread, after the parallel setup (0.9.227): the loader runs every mod's setup handler at
        // once, and what this registers - the golem's screen, and at 1.21.1 each tamper's picture rule and the ghosts'
        // passes - goes into the game's plain maps, which another mod's handler may be writing at the same moment.
        event.enqueueWork(ForgeClientSetup::onTheGamesThread);
    }

    /** What client setup does, run by {@link #client} on the game's own thread. */
    private static void onTheGamesThread() {
        // No pass is declared for the ghosts any more, and none is looked up for the blocks they cover. 1.21.1 offered
        // each ghost to every pass a covered block might draw in (setRenderLayer, see GhostLayers.claims) and asked a
        // covered block's model which pass it drew in (its ChunkRenderTypeSet). From 26.1 a chunk has no per-block pass:
        // each quad carries its own layer, which the ghost's quads are given from the covered block (GhostLayers.of),
        // and Forge 64 keeps no pass of its own for a block - a model file's render type is set on its quads - so the
        // shared lookup, which reads the covered block's baked quads, is already the answer this module used to give.

        // What draws a Golem of Ways is in its own two events from Forge 37: see renderers and layers below.

        // And what screen its orders open on. Through the game's own class here, which Forge makes
        // public with an access transformer; Fabric's copy of this line goes through its API
        // because vanilla's method is private and Fabric does not patch vanilla.
        net.minecraft.client.gui.screens.MenuScreens
            .register(com.trmtgtnh.entity.GolemMenu.type(), com.trmtgtnh.client.gui.GuiGolem::new);

        // Which picture draws a tamper is the grade property its client item file names, registered as the mod is
        // built rather than here from 26.1 - see TrmtForgeClient.gradeProperty. And the game's item color handlers,
        // which 1.21.1 handed to ItemTints from here, are gone with 1.21.4's item models: the wear editor's previews
        // read their tint from the item's own model now, so there is nothing to hand over.

        Trmt.LOG.info("Registered the golem's renderer and screen, and the tampers' grade property, with Forge");
    }

    /**
     * What color a ghost is at a place.
     *
     * <p>
     * Its own event rather than a line in {@link #client}: the block colors are built after setup
     * and Forge hands them over here, which is the only moment there is a registry to put this in.
     * The rule itself is {@code GhostTint}'s and is shared - Fabric registers the same object with
     * its own registry - because what color worn grass is has nothing to do with which loader is
     * asking.
     */
    // From 1.21.4 a block's color is a list of tint sources, one per tint slot (GhostTint.handler), which the event
    // takes as it is.
    @SubscribeEvent
    public static void colors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register(com.trmtgtnh.client.GhostTint.handler(), com.trmtgtnh.block.ModBlocks.ghosts());
    }

    /**
     * What draws a Golem of Ways. Both older editions need a factory class for this - 1.12.2 because the render
     * manager is built after the proxy runs, 1.7.10 because it registers the renderer object itself - and that factory
     * is Forge's own, so it could never live in the shared module. Here it is a method reference, in the event Forge
     * keeps for it from 1.17 (1.16.5 called RenderingRegistry from setup).
     */
    @SubscribeEvent
    public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            com.trmtgtnh.entity.ModEntities.golemType(),
            com.trmtgtnh.client.render.RenderGolemOfWays::new);
    }

    /**
     * The golem's model, as the layer its renderer bakes it from. From 1.17 an entity model is a definition baked once
     * the game has every one; the definition itself is shared (ModelGolemOfWays.createBodyLayer), and only where it is
     * handed over is the loader's.
     */
    @SubscribeEvent
    public static void layers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
            com.trmtgtnh.client.render.ModelGolemOfWays.LAYER,
            com.trmtgtnh.client.render.ModelGolemOfWays::createBodyLayer);
    }
}
