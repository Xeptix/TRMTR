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
        net.minecraft.client.gui.screens.MenuScreens.register(
            com.trmtgtnh.entity.GolemMenu.type(),
            com.trmtgtnh.client.gui.GuiGolem::new);

        // Which picture draws a tamper, from the grade in the stack. The rule is TamperModels' and
        // is shared; only the door differs - Forge patches vanilla's register public, and Fabric is
        // handed one line of access widener. That class holds the whole of this mod's third answer
        // to the question.
        tamperGrades();

        // The game's own item colour handlers, which the wear editor's previews are tinted with.
        // Private at this version with no accessor; Forge patches one back on, and Fabric's copy of
        // this line reads the field through a line of access widener. See ItemTints.
        com.trmtgtnh.client.gui.ItemTints.use(
            net.minecraft.client.Minecraft.getInstance()
                .getItemColors());

        Trmt.LOG.info("Registered the golem's renderer and screen, and the tampers' grade property, with Forge");
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
