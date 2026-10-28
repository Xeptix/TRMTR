package com.trmtgtnh.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.ClientSide;

/**
 * The client tick, on Forge. The Fabric one's opposite number and deliberately the same shape.
 *
 * <p>
 * Two things happen here and they are the whole of it: while there is a world, the client layer gets
 * its tick - the overlay drains the queue of arrived records, work other threads left for this one is
 * run, and a pending rebuild of the wear pictures counts down - and when the world goes away, it is
 * told once so everything it was holding goes back where it came from. Leaving is the half that must
 * happen whatever else does.
 *
 * <p>
 * <strong>This class used to be five hundred lines, and all but these two were a harness.</strong> It
 * opened a world named {@code spike} from the title screen, walked the player across it for two
 * minutes, put the world on peaceful, closed the pause screen under them, moved the camera and took
 * photographs - ungated, in the shipped jar, with only the absence of a save by that name standing
 * between a player and all of it. That is now the test harness, which runs only when
 * {@code -Dtrmt.spike} is set, and which this hands the tick to after the real work and never before
 * it.
 *
 * <p>
 * {@code value = Dist.CLIENT} rather than a side annotation on the class, because this subscribes to
 * the Forge event bus and a server that loaded it would subscribe to a client tick that never comes -
 * which is not an error and is exactly the kind of quiet nothing this project keeps finding.
 *
 * <p>
 * From Forge 64 it also holds what the mod's constructor does on a client, which 1.21.1 handed to
 * {@code DistExecutor}: that class is gone, and a method of a client-only class, called only behind the
 * side check, is how a server is kept from loading any of it.
 */
@Mod.EventBusSubscriber(modid = Trmt.MODID, value = Dist.CLIENT)
public final class TrmtForgeClient {

    /** Whether there is a world to leave, so leaving happens once rather than every idle tick. */
    private static boolean left;

    private TrmtForgeClient() {}

    // ------------------------------------------------------------------
    // What the constructor does on a client (TrmtForge)
    // ------------------------------------------------------------------

    /**
     * On a client, and as early as there is one. The pack repository takes its sources when it
     * is built and the resource manager is assembled from whatever was offered then, so a pack
     * wired any later is a pack the manager never heard of - and every wear sprite the atlas asks
     * for comes back as a missing texture. Which is what happened when this was done at stitch
     * time, on Fabric, visibly, before it was moved here on both.
     */
    static void offerWearPack() {
        com.trmtgtnh.client.texture.WearPackSource.use(com.trmtgtnh.client.texture.WearTextures.SHEETS);
    }

    /**
     * Where an arriving packet puts its news, and where the ghost's model asks what to draw.
     * Both are the same object, so it is wired once and here: a packet can arrive before anything
     * on a screen has happened, and a model asked for quads with no seam behind it draws plain
     * earth rather than a path - which is exactly how the rendering spike looked until this line.
     */
    static void wireClient() {
        com.trmtgtnh.Client.use(new ClientSide());
    }

    /**
     * Where the mod list's Config button goes. Forge's own GuiConfig is gone at this version and
     * this is what replaced the factory that used to point at it - one line rather than
     * TrmtGuiFactory's forty-four, because the screen itself is shared and says the rest.
     *
     * <p>
     * Through ConfigScreens from 26.1 rather than straight to the Cloth screen: Cloth Config has no Forge build at
     * 26.1.2 and is optional on every 26.x jar (decided), so the shared module chooses which screen there is.
     */
    static void configButton() {
        net.minecraftforge.fml.ModLoadingContext.get()
            .registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                    (client, parent) -> com.trmtgtnh.client.gui.ConfigScreens.open(parent)));
    }

    /**
     * Which picture draws a tamper, from the grade in the stack. The rule is TamperModels' and is shared; only the door
     * differs. From 1.21.4 an item's picture is chosen by its client item file (assets/trmtgtnh/items), which names
     * the property by id, so the property's codec is what is registered - in vanilla's own table of numeric item
     * properties, which is private and which Forge 64 offers no event for, so through an accessor (see
     * RangeSelectPropertiesAccess). 1.21.1 registered the property per item from client setup.
     *
     * <p>
     * Here, at construction, rather than in client setup: from Forge 64 client setup runs inside the first resource
     * reload, beside the model manager reading those files, and a file read before its property is known fails to
     * load and draws the missing model.
     */
    static void gradeProperty() {
        com.trmtgtnh.forge.mixin.RangeSelectPropertiesAccess.trmt$idMapper()
            .put(com.trmtgtnh.client.model.TamperModels.GRADE, com.trmtgtnh.client.model.TamperModels.Grade.MAP_CODEC);
    }

    // ------------------------------------------------------------------
    // Ticks and chunks
    // ------------------------------------------------------------------

    /** The end of each client tick: Forge 64's Post event is the END phase of 1.21.1's one event. */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
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
    }

    /** The settings key, unbound until a player binds it (0.9.228). Each event names its own bus from Forge 64. */
    @SubscribeEvent
    public static void keys(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.trmtgtnh.client.gui.SettingsAccess.key());
    }

    /** {@code /trmt config}, on the client: never sent, and the server's own /trmt answers everything else (0.9.228). */
    @SubscribeEvent
    public static void commands(net.minecraftforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher()
            .register(
                net.minecraft.commands.Commands.literal(com.trmtgtnh.client.gui.SettingsAccess.ROOT)
                    .then(
                        net.minecraft.commands.Commands.literal(com.trmtgtnh.client.gui.SettingsAccess.CONFIG)
                            .executes(context -> com.trmtgtnh.client.gui.SettingsAccess.askedByCommand())));
    }

    /**
     * What this mod adds to a screen once it has laid itself out (0.9.228, ConfigScreens.afterInit): the small settings
     * button on the game's Options screen, and the way back to the own screen on Cloth Config's.
     */
    @SubscribeEvent
    public static void screens(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
        com.trmtgtnh.client.gui.ConfigScreens.afterInit(event.getScreen(), event.getListenersList(), event::addListener);
    }

    /** A chunk arriving in the client's world, which Forge announces once its blocks are in (spec PT18). */
    @SubscribeEvent
    public static void chunkLoaded(net.minecraftforge.event.level.ChunkEvent.Load event) {
        if (event.getLevel() == null || !event.getLevel()
            .isClientSide()) return;
        // ChunkPos is a record by 26.1 (world/level/ChunkPos.java): its x and z are accessors now.
        ClientSide.chunkLoaded(
            event.getChunk()
                .getPos()
                .x(),
            event.getChunk()
                .getPos()
                .z());
    }
}
