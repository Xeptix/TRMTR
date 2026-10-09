package com.trmtgtnh.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
@Mod.EventBusSubscriber(modid = Trmt.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClientSetup {

    private ForgeClientSetup() {}

    @SubscribeEvent
    public static void client(FMLClientSetupEvent event) {
        // Every pass a covered block might draw in, because one ghost stands in for all of them and
        // which pass a square wants is not known until the square is. The model hands back nothing
        // in the three that are not this square's; see GhostLayers, which both loaders share, and
        // TrmtFabricClient, which acts on the same answer the other way round.
        //
        // It began as a single declaration of cut-out mipped, which was already an improvement on
        // declaring nothing - in the solid pass a cut-out texture's holes are drawn as though they
        // were opaque, so grass's fringe came out as a grey rectangle over the whole face. But one
        // pass for every ghost is the wrong shape of answer: it left worn ice drawing over what was
        // behind it rather than through it.
        net.minecraft.client.renderer.ItemBlockRenderTypes
            .setRenderLayer(com.trmtgtnh.block.ModBlocks.ghost(), com.trmtgtnh.client.GhostLayers::claims);
        // And how a covered block's own pass is found, which on Forge is its predicate rather than the chunk table
        // vanilla keeps: a modded block registers through setRenderLayer, which that table never sees, so asking the
        // table answered solid for every one of them (0.9.220). The first pass the block draws in, in the game's own
        // order - solid, cut-out mipped, cut-out, translucent.
        com.trmtgtnh.client.GhostLayers.lookUpPassesWith(state -> {
            for (net.minecraft.client.renderer.RenderType pass : net.minecraft.client.renderer.RenderType.chunkBufferLayers()) {
                if (net.minecraft.client.renderer.ItemBlockRenderTypes.canRenderInLayer(state, pass)) return pass;
            }
            return null;
        });

        // What draws a Golem of Ways. Both older editions need a factory class for this - 1.12.2
        // because the render manager is built after the proxy runs, 1.7.10 because it registers the
        // renderer object itself - and that factory is Forge's own interface, so it could never
        // live in the shared module. Here it is a method reference.
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
            com.trmtgtnh.entity.ModEntities.golemType(),
            com.trmtgtnh.client.render.RenderGolemOfWays::new);

        // And what screen its orders open on. Through the game's own class here, which Forge makes
        // public with an access transformer; Fabric's copy of this line goes through its API
        // because vanilla's method is private and Fabric does not patch vanilla.
        net.minecraft.client.gui.screens.MenuScreens
            .register(com.trmtgtnh.entity.GolemMenu.type(), com.trmtgtnh.client.gui.GuiGolem::new);

        // Which picture draws a tamper, from the grade in the stack. The rule is TamperModels' and
        // is shared; only the door differs - Forge patches vanilla's register public, and Fabric is
        // handed one line of access widener. That class holds the whole of this mod's third answer
        // to the question.
        tamperGrades();

        // The game's own item color handlers, which the wear editor's previews are tinted with.
        // Private at this version with no accessor; Forge patches one back on, and Fabric's copy of
        // this line reads the field through a line of access widener. See ItemTints.
        com.trmtgtnh.client.gui.ItemTints.use(
            net.minecraft.client.Minecraft.getInstance()
                .getItemColors());

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
    @SubscribeEvent
    public static void colors(net.minecraftforge.client.event.ColorHandlerEvent.Block event) {
        event.getBlockColors()
            .register(com.trmtgtnh.client.GhostTint.handler(), com.trmtgtnh.block.ModBlocks.ghost());
    }

    /** The grade property, on both tools that are drawn by one. */
    private static void tamperGrades() {
        net.minecraft.world.item.Item[] drawnByGrade = { com.trmtgtnh.item.ModItems.gradedTamper(),
            com.trmtgtnh.item.ModItems.chunkTamper(), };
        for (net.minecraft.world.item.Item each : drawnByGrade) {
            if (each == null) continue;
            net.minecraft.client.renderer.item.ItemProperties.register(
                each,
                com.trmtgtnh.client.model.TamperModels.GRADE,
                com.trmtgtnh.client.model.TamperModels.grader());
        }
    }
}
