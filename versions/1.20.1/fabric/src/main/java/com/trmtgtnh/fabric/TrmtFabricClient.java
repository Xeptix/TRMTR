package com.trmtgtnh.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

import com.trmtgtnh.Client;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.ClientSide;
import com.trmtgtnh.client.texture.WearPackSource;
import com.trmtgtnh.client.texture.WearTextures;

/**
 * The client half of the Fabric entry point. The Forge one's opposite number and deliberately the
 * same shape.
 *
 * <p>
 * Everything a client needs told once is told here, in the order the game needs it told, and then
 * one tick handler: while there is a world the client layer gets its tick - the overlay drains the
 * queue of arrived records, work other threads left for this one is run, and a pending rebuild of
 * the wear pictures counts down - and when the world goes away it is told once, so everything it was
 * holding goes back where it came from. Leaving is the half that must happen whatever else does.
 *
 * <p>
 * <strong>This class used to be five hundred and eighty lines, and all but these were a
 * harness.</strong> It opened a world named {@code spike} from the title screen, walked the player
 * across it for two minutes, put the world on peaceful, closed the pause screen under them, moved
 * the camera and took photographs - ungated, in the shipped jar, with only the absence of a save by
 * that name standing between a player and all of it. That is the test harness now, which runs
 * only when {@code -Dtrmt.spike} is set, and which this hands the tick to after the real work and
 * never before it.
 */
@Environment(EnvType.CLIENT)
public final class TrmtFabricClient implements ClientModInitializer {

    /** Whether there is a world to leave, so leaving happens once rather than every idle tick. */
    private boolean left;

    @Override
    public void onInitializeClient() {
        // No relay of a tamper's left-click here (0.9.227, spec RL19): Fabric API at this version sends the dig itself when
        // the attack callback's answer accepts it - read from its interaction module's jar - so the relay 1.16.5 needs
        // (FabricEvents.sendClickToServer, for Fabric API 0.42, which sends nothing) sent every click twice. A stray
        // single click on reinforced ground then armed the take-off and took a level at once.
        FabricClientChannel.open();

        // Before anything reloads resources, and that ordering is the whole of it. The pack
        // repository takes its sources when it is built and the resource manager is assembled from
        // whatever was offered then - so a pack wired later is a pack the manager never heard of, and
        // every wear sprite the atlas asks for comes back as a missing texture. Which is exactly what
        // happened when this was done at stitch time.
        WearPackSource.use(WearTextures.SHEETS);

        // Where an arriving packet puts its news, and where the ghost's model asks what to draw.
        // Both are the same object, so it is wired once and here: a packet can arrive before anything
        // on a screen has happened, and a model asked for quads with no seam behind it draws plain
        // earth rather than a path - which is exactly how the rendering spike looked until this line.
        Client.use(new ClientSide());

        // The ghost's model, swapped in after baking, as on Forge - see GhostModelProvider.
        ModelLoadingPlugin.register(new GhostModelProvider());

        // Which pass the ghost draws in, and what color it is at a place. Both are things a block
        // tells the client rather than the game, both are one line per loader, and neither was said
        // on this edition: the ghost drew in the solid pass, where a cut-out texture's holes are
        // filled in, and asked for tints nothing answered. Worn grass came out as grey slabs for
        // both reasons at once. See BlockGhost for the pass and GhostTint for the color; the Forge
        // module says the same two things in ForgeClientSetup.
        // Both ghosts: the worn stair's is a block of its own since 0.9.223 (BlockGhostStair).
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE
            .putBlocks(net.minecraft.client.renderer.RenderType.cutoutMipped(), com.trmtgtnh.block.ModBlocks.ghosts());
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK
            .register(com.trmtgtnh.client.GhostTint.handler(), com.trmtgtnh.block.ModBlocks.ghosts());

        // What draws a Golem of Ways, and what screen its orders open on. One line each, in the
        // only place that already knows it is a client; see TrmtForge for the other loader's pair.
        //
        // Both go through Fabric's API rather than the game's, and the screen one is the reason
        // worth recording: vanilla's `MenuScreens.register` is private, and Forge makes it public
        // with an access transformer of its own. So the Forge side can call the game directly and
        // this side cannot - which looks like an oversight and is the loaders disagreeing about
        // whether patching vanilla is a thing a loader does.
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
            com.trmtgtnh.entity.ModEntities.golemType(),
            com.trmtgtnh.client.render.RenderGolemOfWays::new);
        // And its model, as the layer its renderer bakes it from: from 1.17 an entity model is a definition baked once
        // the game has every one. The definition is shared (ModelGolemOfWays.createBodyLayer); this hands it over.
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
            com.trmtgtnh.client.render.ModelGolemOfWays.LAYER,
            com.trmtgtnh.client.render.ModelGolemOfWays::createBodyLayer);
        // The game's own item color handlers, which the wear editor's previews are tinted with.
        // Private at this version with no accessor; Forge patches one back on and this side opens
        // the field with a line of access widener. See ItemTints.
        // Once the client has started, and not here: while mods start the game has not made its item colors yet, and
        // the field is still null (0.9.228, found photographing the settings screen's Surfaces tab, whose look pictures
        // are tinted through ItemTints - its stand-in for a null set was refused by Fabric API, which allows one set).
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(started -> com.trmtgtnh.client.gui.ItemTints.use(started.itemColors));

        // A fluid's still texture, for the settings screen's picture of a fluid with no bucket (0.9.228, spec 3.3):
        // Fabric API's fluid render handler names its sprites, the still one first. Asked with no world and no square,
        // which a handler that reads them answers by throwing, and the screen then draws nothing for that fluid.
        com.trmtgtnh.client.gui.SettingsPictures.useStillTextures(fluid -> {
            net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler handler =
                net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.get(fluid);
            if (handler == null) return null;
            net.minecraft.client.renderer.texture.TextureAtlasSprite[] sprites =
                handler.getFluidSprites(null, null, fluid.defaultFluidState());
            return sprites == null || sprites.length == 0 || sprites[0] == null ? null
                : sprites[0].contents()
                    .name();
        });
        // And its color, which the game's renderer multiplies that texture by (0.9.228): the same handler's, asked the
        // same way; white where there is no handler, and a handler that throws draws the texture as it is.
        com.trmtgtnh.client.gui.SettingsPictures.useStillTints(fluid -> {
            net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler tinting =
                net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.get(fluid);
            return tinting == null ? 0xFFFFFF : tinting.getFluidColor(null, null, fluid.defaultFluidState());
        });

        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry
            .register(com.trmtgtnh.entity.GolemMenu.type(), com.trmtgtnh.client.gui.GuiGolem::new);

        // Which picture draws a tamper, from the grade in the stack. The same disagreement as the
        // screen above and this time with the other answer: vanilla's register is private here too,
        // and rather than a second API this side opens that one method with a line of access
        // widener - so both loaders call the same vanilla method and TamperModels, which holds the
        // rule, knows about neither route. See trmtgtnh.accesswidener, which says so in full.
        for (net.minecraft.world.item.Item each : new net.minecraft.world.item.Item[] {
            com.trmtgtnh.item.ModItems.gradedTamper(), com.trmtgtnh.item.ModItems.chunkTamper(), }) {
            if (each == null) continue;
            net.minecraft.client.renderer.item.ItemProperties.register(
                each,
                com.trmtgtnh.client.model.TamperModels.GRADE,
                com.trmtgtnh.client.model.TamperModels.unclampedGrader());
        }

        // A chunk arriving in the client's world with a record already held is queued, as the 1.7.10 edition's
        // chunk load does (spec PT18).
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD
            .register((level, chunk) -> ClientSide.chunkLoaded(chunk.getPos().x, chunk.getPos().z));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) {
                if (left) {
                    left = false;
                    ClientSide.leaveWorld();
                }
                // What a tick with no world still owes - see ClientSide.idleTick.
                ClientSide.idleTick();
            } else {
                left = true;
                ClientSide.tick();
            }

            // The settings key and /trmt config, asked for since the last tick (0.9.228, SettingsAccess).
            com.trmtgtnh.client.gui.SettingsAccess.tick(client);
        });

        // The ways to the settings besides Mod Menu, which is optional (0.9.228): a key, unbound until a player binds it;
        // /trmt config, caught as it is sent and never sent on (SettingsAccess.sentCommand says why it is not a client
        // command on Fabric), every other /trmt going to the server; and a small button on the game's Options screen -
        // with the way back to the own screen on Cloth Config's.
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
            .registerKeyBinding(com.trmtgtnh.client.gui.SettingsAccess.key());
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND
            .register(command -> !com.trmtgtnh.client.gui.SettingsAccess.sentCommand(command));
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(
            (client, screen, width, height) -> com.trmtgtnh.client.gui.ConfigScreens.afterInit(
                screen,
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen),
                widget -> net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen)
                    .add(widget)));

        Trmt.LOG.info("{} client ready on Fabric", Trmt.NAME);
    }
}
