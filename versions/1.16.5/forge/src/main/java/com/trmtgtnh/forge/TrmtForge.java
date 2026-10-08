package com.trmtgtnh.forge;

import java.io.File;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import com.trmtgtnh.Client;
import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.ClientSide;
import com.trmtgtnh.client.texture.WearPackSource;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.Plants;

/**
 * Where the mod starts under Forge. The Fabric module's opposite number, answering the same two
 * questions in the same order and for the same reasons.
 *
 * <p>
 * Shorter than its Fabric counterpart by exactly one thing: Forge keeps the running server somewhere
 * a static call can reach, so there is no lifecycle to subscribe to in order to know. It is still
 * asked whether it is running rather than merely whether it exists, because a client that has closed
 * a single-player world still has one.
 */
@Mod(Trmt.MODID)
public final class TrmtForge {

    public TrmtForge() {
        ModsPresent.use(
            modId -> ModList.get()
                .isLoaded(modId));
        // What this build calls itself, which the loader knows and the shared module does not. The
        // 1.12.2 edition reads a class its build plugin generates; there is none here and the two
        // compat layers only want it to stamp a folder. See Trmt.useVersion.
        Trmt.useVersion(
            ModList.get()
                .getModContainerById(Trmt.MODID)
                .map(
                    container -> container.getModInfo()
                        .getVersion()
                        .toString())
                .orElse("unknown"));
        // This jar's line in the update check's version file, and whether this is a development game -
        // which only the loader can say. See UpdateNotice.
        com.trmtgtnh.server.UpdateNotice
            .edition("1.16.5-forge", !net.minecraftforge.fml.loading.FMLEnvironment.production);
        Trmt.useHost(() -> {
            net.minecraft.server.MinecraftServer candidate = ServerLifecycleHooks.getCurrentServer();
            return candidate != null && candidate.isRunning() ? candidate : null;
        });

        // A tag that resolves whenever it is asked rather than when it is made, which is what a
        // recipe's ingredient needs: Forge's own optional tag. See OreNames.LazyTags for what asking
        // the collection of the moment cost this edition.
        com.trmtgtnh.util.OreNames.use(name -> net.minecraft.tags.ItemTags.createOptional(name));

        // Forge's own word for "this grows out of whatever is underneath it", which is exactly the
        // question Plants asks and the reason that seam exists: Fabric has no equivalent and
        // answers with vanilla's bush class instead.
        Plants.use(state -> state.getBlock() instanceof IPlantable);

        TrmtConfig.load(
            new File(
                FMLPaths.CONFIGDIR.get()
                    .toFile(),
                Trmt.MODID + ".cfg"));

        ForgeChannel.open();

        // How to ask a container what is inside it, which is a capability here and is the one place
        // this loader can answer something the other cannot. See Fluids.
        ForgeFluids.use();

        // On a client, and as early as there is one. The pack repository takes its sources when it
        // is built and the resource manager is assembled from whatever was offered then, so a pack
        // wired any later is a pack the manager never heard of - and every wear sprite the atlas asks
        // for comes back as a missing texture. Which is what happened when this was done at stitch
        // time, on Fabric, visibly, before it was moved here on both.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WearPackSource.use(WearTextures.SHEETS));

        // Where an arriving packet puts its news, and where the ghost's model asks what to draw.
        // Both are the same object, so it is wired once and here: a packet can arrive before anything
        // on a screen has happened, and a model asked for quads with no seam behind it draws plain
        // earth rather than a path - which is exactly how the rendering spike looked until this line.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.use(new ClientSide()));

        // What draws a Golem of Ways and what screen its orders open on are NOT here, and the
        // attempt to put them here is worth a line. Both need the golem's registry entry, and this
        // constructor runs before any registry event has fired - so golemType() answered null and
        // the game crashed on the title screen with an NPE out of a lambda. Compiling said nothing
        // about it, which is the whole argument for starting the thing. They are in
        // ForgeClientSetup now, on the event that fires once the registries are full.

        // Block ids moved - a world from another mod list opened, a server's numbering taken on, or either
        // handed back. Forge posts it on each mod's own bus, so it is heard here rather than on the game's.
        // See ServerEvents.idsMoved.
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get()
            .getModEventBus()
            .addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLModIdMappingEvent moved) -> com.trmtgtnh.server.ServerEvents
                    .idsMoved());

        // Where the mod list's Config button goes. Forge's own GuiConfig is gone at this version and
        // this is what replaced the factory that used to point at it - one line rather than
        // TrmtGuiFactory's forty-four, because the screen itself is shared and says the rest.
        DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> net.minecraftforge.fml.ModLoadingContext.get()
                .registerExtensionPoint(
                    net.minecraftforge.fml.ExtensionPoint.CONFIGGUIFACTORY,
                    () -> (client, parent) -> com.trmtgtnh.client.gui.ConfigScreen.build(parent)));

        Trmt.LOG.info("{} on Forge, Minecraft 1.16.5", Trmt.NAME);
    }

    /**
     * The two written integrations, once a server is up.
     *
     * <p>
     * Not in the constructor: what they describe includes which tamper grades this pack can make,
     * which is decided by tags, and tags are not loaded until a server starts. See
     * {@code ServerEvents.serverStarted}.
     */
    // The forge bus, not the mod one. These two are named FML... and live in fml.event.server, which
    // reads as mod-bus and is not: the game refuses to start and says so, which is how this was
    // found rather than by reading.
    @Mod.EventBusSubscriber(modid = Trmt.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Lifecycle {

        private Lifecycle() {}

        // Before the world loads, which is the point: what erodes has to be known before the first
        // chunk is asked about, and on this edition nothing on a server ever worked it out.
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void aboutToStart(net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent event) {
            com.trmtgtnh.server.ServerEvents.serverStarting(event.getServer());
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void started(net.minecraftforge.fml.event.server.FMLServerStartedEvent event) {
            com.trmtgtnh.server.ServerEvents.serverStarted(event.getServer());
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void stopped(net.minecraftforge.fml.event.server.FMLServerStoppedEvent event) {
            com.trmtgtnh.server.ServerEvents.serverStopped();
        }
    }
}
