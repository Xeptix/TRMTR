package com.trmtgtnh.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;

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
 * that name standing between a player and all of it. That is {@link FabricSpikeRun} now, which runs
 * only when {@code -Dtrmt.spike} is set, and which this hands the tick to after the real work and
 * never before it.
 */
@Environment(EnvType.CLIENT)
public final class TrmtFabricClient implements ClientModInitializer {

    /** Whether there is a world to leave, so leaving happens once rather than every idle tick. */
    private boolean left;

    @Override
    public void onInitializeClient() {
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

        // Forge swaps the ghost's model after baking; Fabric has no such event, so the swap has to be
        // declared before anything is baked and happens by answering for the variant. One provider,
        // asked about every variant the game loads - see GhostModelProvider for what it answers.
        ModelLoadingRegistry.INSTANCE.registerVariantProvider(manager -> new GhostModelProvider());

        // Which pass the ghost draws in, and what color it is at a place. Both are things a block
        // tells the client rather than the game, both are one line per loader, and neither was said
        // on this edition: the ghost drew in the solid pass, where a cut-out texture's holes are
        // filled in, and asked for tints nothing answered. Worn grass came out as grey slabs for
        // both reasons at once. See BlockGhost for the pass and GhostTint for the color; the Forge
        // module says the same two things in ForgeClientSetup.
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE
            .putBlock(com.trmtgtnh.block.ModBlocks.ghost(), net.minecraft.client.renderer.RenderType.cutoutMipped());
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK
            .register(com.trmtgtnh.client.GhostTint.handler(), com.trmtgtnh.block.ModBlocks.ghost());

        // What draws a Golem of Ways, and what screen its orders open on. One line each, in the
        // only place that already knows it is a client; see TrmtForge for the other loader's pair.
        //
        // Both go through Fabric's API rather than the game's, and the screen one is the reason
        // worth recording: vanilla's `MenuScreens.register` is private, and Forge makes it public
        // with an access transformer of its own. So the Forge side can call the game directly and
        // this side cannot - which looks like an oversight and is the loaders disagreeing about
        // whether patching vanilla is a thing a loader does.
        net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry.INSTANCE.register(
            com.trmtgtnh.entity.ModEntities.golemType(),
            (dispatcher, context) -> new com.trmtgtnh.client.render.RenderGolemOfWays(dispatcher));
        // The game's own item color handlers, which the wear editor's previews are tinted with.
        // Private at this version with no accessor; Forge patches one back on and this side opens
        // the field with a line of access widener. See ItemTints.
        com.trmtgtnh.client.gui.ItemTints.use(net.minecraft.client.Minecraft.getInstance().itemColors);

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
                com.trmtgtnh.client.model.TamperModels.grader());
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
        });

        Trmt.LOG.info("{} client ready on Fabric", Trmt.NAME);
    }
}
