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
// No bus named: FML 4 ignores it and sends each listener to the mod's bus or the game's by its event's type
// (IModBusEvent or not), where Forge took the bus from this annotation.
@EventBusSubscriber(modid = Trmt.MODID, value = Dist.CLIENT)
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {}

    @SubscribeEvent
    public static void client(FMLClientSetupEvent event) {
        // On the game's own thread, after the parallel setup (0.9.227): the loader runs every mod's setup handler at
        // once, and what this registers - the golem's screen, each tamper's picture rule, the ghosts' passes - goes into
        // the game's plain maps, which another mod's handler may be writing at the same moment.
        event.enqueueWork(NeoForgeClientSetup::onTheGamesThread);
    }

    /** What client setup does, run by {@link #client} on the game's own thread. */
    private static void onTheGamesThread() {
        // 1.21.1 offered every ghost to every chunk pass here (ItemBlockRenderTypes.setRenderLayer with
        // GhostLayers::claims) and handed GhostLayers a lookup reading the covered block's ChunkRenderTypeSet. From
        // 1.21.5 neither exists: there is no per-block pass to claim, and every quad carries its own chunk layer, which
        // the ghost's quads take from the covered block's (GhostQuads, GhostLayers). NeoForge 26.1 keeps no pass of its
        // own for a block, so GhostLayers' own lookup - the covered block's baked quads, as the game decides them -
        // answers here as on every loader, and a square still draws in the one pass 1.21.1 gave it.

        // What draws a Golem of Ways is in its own two events: see renderers and layers below. And what screen its
        // orders open on is in screens, below, where NeoForge asks for it.

        // Which picture draws a tamper, from the grade in the stack, is its own event from 1.21.4: see grades, below.
        // The game's item color handlers, which 1.21.1 handed ItemTints here, are gone from 1.21.4 - an item's tint is
        // part of its model definition, and ItemTints reads it from the stack's resolved model itself.
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

            // From NeoForge 26.3 the hitting dust is asked with the square and the face, where it was asked with the
            // hit (IClientBlockExtensions, read from 26.3.0.94); the ghost is handed the square.
            @Override
            public boolean addHitEffects(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face,
                net.minecraft.client.particle.ParticleEngine manager) {
                return state.getBlock() instanceof com.trmtgtnh.block.BlockGhost
                    && ((com.trmtgtnh.block.BlockGhost) state.getBlock()).addHitEffects(state, level, pos, manager);
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
    public static void colors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.BlockTintSources event) {
        // From 1.21.5 a block is tinted by a list of tint sources, one per tint slot, where 1.21.1 registered one
        // BlockColor answering every slot; GhostTint.handler hands the list, each source asking the same rule.
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

    /**
     * Which picture draws a tamper, from the grade in the stack. The rule is TamperModels' and is shared; only the door
     * differs.
     *
     * <p>
     * 1.21.1 registered the grade property on both tools drawn by one (ItemProperties.register, item by item). From
     * 1.21.4 a picture is chosen by the item's client file, whose {@code range_dispatch} names a numeric property by id,
     * so the property is registered once, by its id {@code trmtgtnh:grade} and its codec, in the event NeoForge posts
     * for vanilla's numeric property table - and the two tools' item files name it (assets/trmtgtnh/items). The number
     * it answers, and so the picture each grade gets, is 1.21.1's.
     */
    @SubscribeEvent
    public static void grades(net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(com.trmtgtnh.client.model.TamperModels.GRADE, com.trmtgtnh.client.model.TamperModels.Grade.MAP_CODEC);
        Trmt.LOG.info("Registered the tampers' grade property with NeoForge");
    }
}
