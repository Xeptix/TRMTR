package com.trmtgtnh.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import com.trmtgtnh.Trmt;

/**
 * The client registrations that have to wait until the registries are full. The Forge module's ForgeClientSetup
 * carried across.
 *
 * <p>
 * They name the golem's {@code EntityType} and its {@code MenuType}, and a mod's constructor runs before any registry
 * event has fired - so {@code ModEntities.golemType()} would answer null there, which on Forge killed the game on the
 * title screen with a {@code NullPointerException} out of a lambda.
 *
 * <p>
 * {@code FMLClientSetupEvent} is the right moment: every registry is full, and it only fires on a client. Its own class
 * is marked {@code Dist.CLIENT} as well, for the reason the side scan exists - a class that names a renderer and a
 * screen should say out loud that it is a client's, rather than relying on nobody calling it.
 *
 * <p>
 * Two things NeoForge moved out of setup into events of their own, each here beside the Forge module's other events:
 * the golem's screen ({@link #screens}), because NeoForge keeps vanilla's {@code MenuScreens.register} private, and the
 * ghost's dust ({@link #extensions}), because NeoForge asks a block's client extensions for it rather than the block.
 */
// The mod's bus named (0.9.228, step 9; Xep: "Fix the code too"): FML 4.0.40 (NeoForge 21.1.181) and later ignore
// it and send each listener to the mod's bus or the game's by its event's type, but every FML before reads it and
// takes the game's bus when none is named - where a mod-bus event is refused, the mod's subscribers are not
// registered, and NeoForge 21.1.68 to 21.1.180 never got as far as a world. Every listener here is the mod's bus's,
// so naming it is right on both; a class with listeners for both buses has to be two classes (see #keys). The
// attribute is deprecated for removal in the later FML 4.0 builds, hence the suppression. NeoForgeBusesTest.
@SuppressWarnings("removal")
@EventBusSubscriber(modid = Trmt.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {}

    /**
     * The settings key, unbound until a player binds it (0.9.228). Here among the mod's bus's listeners rather than in
     * TrmtNeoForgeClient, whose others are the game's bus's: an FML before 4.0.40 registers a class on the one bus its
     * annotation names, and refused this listener there with "IModBusEvent events are not allowed on the common
     * NeoForge bus", which left the whole mod unregistered on NeoForge 21.1.68 to 21.1.180.
     */
    @SubscribeEvent
    public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.trmtgtnh.client.gui.SettingsAccess.key());
    }

    @SubscribeEvent
    public static void client(FMLClientSetupEvent event) {
        // On the game's own thread, after the parallel setup (0.9.227): the loader runs every mod's setup handler at
        // once, and what this registers - the golem's screen, each tamper's picture rule, the ghosts' passes - goes into
        // the game's plain maps, which another mod's handler may be writing at the same moment.
        event.enqueueWork(NeoForgeClientSetup::onTheGamesThread);
    }

    /** What client setup does, run by {@link #client} on the game's own thread. */
    private static void onTheGamesThread() {
        // Every pass a covered block might draw in, because one ghost stands in for all of them and which pass a
        // square wants is not known until the square is. The model hands back nothing in the three that are not this
        // square's; see GhostLayers, which every loader shares, and TrmtFabricClient, which acts on the same answer the
        // other way round. NeoForge keeps Forge's setRenderLayer taking a test of the pass.
        for (net.minecraft.world.level.block.Block ghost : com.trmtgtnh.block.ModBlocks.ghosts()) {
            net.minecraft.client.renderer.ItemBlockRenderTypes
                .setRenderLayer(ghost, com.trmtgtnh.client.GhostLayers::claims);
        }
        // And how a covered block's own pass is found, which on NeoForge, as on Forge, is what the block's model says
        // rather than the chunk table vanilla keeps: a modded block registers through setRenderLayer, which that table
        // never sees, and a block may name its pass in its model's own file, which only its model knows. The first
        // pass the block draws in, in the game's own order - solid, cut-out mipped, cut-out, translucent.
        com.trmtgtnh.client.GhostLayers.lookUpPassesWith(state -> {
            net.neoforged.neoforge.client.ChunkRenderTypeSet passes = net.minecraft.client.Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModel(state)
                .getRenderTypes(
                    state,
                    net.minecraft.util.RandomSource.create(42L),
                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
            for (net.minecraft.client.renderer.RenderType pass : net.minecraft.client.renderer.RenderType.chunkBufferLayers()) {
                if (passes.contains(pass)) return pass;
            }
            return null;
        });

        // What draws a Golem of Ways is in its own two events: see renderers and layers below. And what screen its
        // orders open on is in screens, below, where NeoForge asks for it.

        // Which picture draws a tamper, from the grade in the stack. The rule is TamperModels' and is shared; only the
        // door differs - NeoForge, like Forge, makes vanilla's register public, and Fabric is handed one line of
        // access widener.
        tamperGrades();

        // The game's own item color handlers, which the wear editor's previews are tinted with. Private at this
        // version with no accessor; NeoForge, like Forge, patches one back on. See ItemTints.
        com.trmtgtnh.client.gui.ItemTints.use(
            net.minecraft.client.Minecraft.getInstance()
                .getItemColors());

        Trmt.LOG.info("Registered the tampers' grade property with NeoForge");
    }

    /**
     * What screen the golem's orders open on. Its own event on NeoForge, which keeps vanilla's
     * {@code MenuScreens.register} private and hands a registration over here instead; Forge made the method public
     * and it was a line of {@link #client}.
     */
    @SubscribeEvent
    public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.trmtgtnh.entity.GolemMenu.type(), com.trmtgtnh.client.gui.GuiGolem::new);
        Trmt.LOG.info("Registered the golem's screen with NeoForge");
    }

    /**
     * The ghost's hitting and breaking dust, held for the square's family as the Forge jar holds it.
     *
     * <p>
     * The shared ghost declares {@code addHitEffects} and {@code addDestroyEffects} under Forge's own names, which
     * Forge's block calls. NeoForge moved both onto a block's client extensions, which the shared module cannot name,
     * so they are handed over here, each passed straight to the ghost's own method - the answer, and the reason for
     * it, stay the ghost's.
     */
    @SubscribeEvent
    public static void extensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerBlock(new net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions() {

            @Override
            public boolean addHitEffects(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.Level level, net.minecraft.world.phys.HitResult target,
                net.minecraft.client.particle.ParticleEngine manager) {
                return state.getBlock() instanceof com.trmtgtnh.block.BlockGhost
                    && ((com.trmtgtnh.block.BlockGhost) state.getBlock()).addHitEffects(state, level, target, manager);
            }

            @Override
            public boolean addDestroyEffects(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                net.minecraft.client.particle.ParticleEngine manager) {
                return state.getBlock() instanceof com.trmtgtnh.block.BlockGhost
                    && ((com.trmtgtnh.block.BlockGhost) state.getBlock()).addDestroyEffects(state, level, pos, manager);
            }
        }, com.trmtgtnh.block.ModBlocks.ghosts());
    }

    /**
     * What color a ghost is at a place.
     *
     * <p>
     * Its own event rather than a line in {@link #client}: the block colors are built after setup and NeoForge hands
     * them over here, which is the only moment there is a registry to put this in. The rule itself is
     * {@code GhostTint}'s and is shared.
     */
    @SubscribeEvent
    public static void colors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register(com.trmtgtnh.client.GhostTint.handler(), com.trmtgtnh.block.ModBlocks.ghosts());
    }

    /**
     * What draws a Golem of Ways, in the event NeoForge keeps for it, as Forge does from 1.17. The renderer itself is
     * shared.
     */
    @SubscribeEvent
    public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            com.trmtgtnh.entity.ModEntities.golemType(),
            com.trmtgtnh.client.render.RenderGolemOfWays::new);
    }

    /**
     * The golem's model, as the layer its renderer bakes it from. The definition itself is shared
     * (ModelGolemOfWays.createBodyLayer), and only where it is handed over is the loader's.
     */
    @SubscribeEvent
    public static void layers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
            com.trmtgtnh.client.render.ModelGolemOfWays.LAYER,
            com.trmtgtnh.client.render.ModelGolemOfWays::createBodyLayer);
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
