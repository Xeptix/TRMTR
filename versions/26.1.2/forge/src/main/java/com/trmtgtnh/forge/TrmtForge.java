package com.trmtgtnh.forge;

import java.io.File;

import net.minecraftforge.common.IPlantable;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;
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
            modId -> ModList.isLoaded(modId));
        // What this build calls itself, which the loader knows and the shared module does not. The
        // 1.12.2 edition reads a class its build plugin generates; there is none here and the two
        // compat layers only want it to stamp a folder. See Trmt.useVersion.
        Trmt.useVersion(
            ModList.getModContainerById(Trmt.MODID)
                .map(
                    container -> container.getModInfo()
                        .getVersion()
                        .toString())
                .orElse("unknown"));
        // This jar's line in the update check's version file, whether this is a development game - which
        // only the loader can say - and the mods this jar requires. See UpdateNotice.
        // Nothing required at 26.1.2: Cloth Config, which 1.21.1's Forge jar required on a client, has no Forge build for
        // this game and is optional on every 26.x jar (decided), so this jar names no mod for the check to ask about.
        com.trmtgtnh.server.UpdateNotice.edition(
            "26.1.2-forge",
            !net.minecraftforge.fml.loading.FMLEnvironment.production,
            new String[0][]);
        // How the dependency check reads a required mod's version (0.9.221).
        com.trmtgtnh.server.UpdateNotice.modVersions(
            id -> net.minecraftforge.fml.ModList.getModContainerById(id)
                .map(
                    mod -> mod.getModInfo()
                        .getVersion()
                        .toString())
                .orElse(null));
        Trmt.useHost(() -> {
            net.minecraft.server.MinecraftServer candidate = ServerLifecycleHooks.getCurrentServer();
            return candidate != null && candidate.isRunning() ? candidate : null;
        });

        // A tag that resolves whenever it is asked rather than when it is made, which is what a
        // recipe's ingredient needs: Forge's own optional tag. See OreNames.LazyTags for what asking
        // the collection of the moment cost this edition.
        com.trmtgtnh.util.OreNames.use(name -> net.minecraft.tags.ItemTags.create(name));
        // c: on all three loaders at 1.21.1: Forge 52 ships its forge: tags and the c: convention's both, and c: is
        // what NeoForge and Fabric read, so a pack's material names mean the same on every jar (see OreNames).
        com.trmtgtnh.util.OreNames.namespace("c");
        // No word from Forge for a machine's player: Forge has had no FakePlayer since 1.21.1 (Forge 52, and Forge 64
        // here, read from its jar), so a machine's player is its mod's own ServerPlayer and the connection is what the
        // shared module asks, as on Fabric (Machines; spec WD59).
        // A block keeps a tile entity, and detection must never stand a ghost in for it (spec SD6). On 1.16.5 a Forge
        // mod could say so through IForgeBlockState.hasTileEntity without being an EntityBlock, and this told detection
        // to ask that too. Forge 1.17 took that method away: a block keeps one only as an EntityBlock, which the shared
        // module already asks, so this loader has nothing more to tell it here.

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
        // DistExecutor is gone from Forge 64: the client's half is a method of TrmtForgeClient, a client-only class
        // named here only behind the side check, so a server never loads it.
        boolean client = net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient();
        if (client) TrmtForgeClient.offerWearPack();

        // Where an arriving packet puts its news, and where the ghost's model asks what to draw.
        // Both are the same object, so it is wired once and here: a packet can arrive before anything
        // on a screen has happened, and a model asked for quads with no seam behind it draws plain
        // earth rather than a path - which is exactly how the rendering spike looked until this line.
        if (client) TrmtForgeClient.wireClient();

        // What draws a Golem of Ways and what screen its orders open on are NOT here, and the
        // attempt to put them here is worth a line. Both need the golem's registry entry, and this
        // constructor runs before any registry event has fired - so golemType() answered null and
        // the game crashed on the title screen with an NPE out of a lambda. Compiling said nothing
        // about it, which is the whole argument for starting the thing. They are in
        // ForgeClientSetup now, on the event that fires once the registries are full.

        // Block ids moved - a world from another mod list opened, a server's numbering taken on, or either
        // handed back. Forge posts it on each mod's own bus, so it is heard here rather than on the game's.
        // See ServerEvents.idsMoved.
        // Forge 47 posts it on the game's bus, as IdMappingEvent; from Forge 64 (EventBus 7) each event has a bus of
        // its own, and this is that one.
        net.minecraftforge.registries.IdMappingEvent.BUS.addListener(
            (net.minecraftforge.registries.IdMappingEvent moved) -> com.trmtgtnh.server.ServerEvents.idsMoved());

        // No block tooltip mod but WTHIT is built for Forge at 1.21.1 - The One Probe and Jade are NeoForge's and
        // Fabric's - and WTHIT finds its plugin through waila_plugins.json, so nothing is asked for here.

        // Where the mod list's Config button goes, and the grade property the tampers' client item files name. See
        // TrmtForgeClient.configButton and gradeProperty.
        if (client) {
            TrmtForgeClient.configButton();
            TrmtForgeClient.gradeProperty();
        }

        // The game version read from the game, so the line cannot go stale as the edition is carried (see TrmtFabric).
        // name(), from 1.21.6, where WorldVersion became an interface of plain accessors; the same name as before.
        Trmt.LOG.info("{} on Forge, Minecraft {}", Trmt.NAME, net.minecraft.SharedConstants.getCurrentVersion()
            .name());
    }

    /**
     * The two written integrations, once a server is up.
     *
     * <p>
     * Not in the constructor: what they describe includes which tamper grades this pack can make,
     * which is decided by tags, and tags are not loaded until a server starts. See
     * {@code ServerEvents.serverStarted}.
     */
    // The forge bus, not the mod one. On 1.16.5 two of these were named FML... and lived in fml.event.server,
    // which reads as mod-bus and is not: the game refused to start and said so. From 1.17 all four are
    // net.minecraftforge.event.server's, which says where they are posted. From Forge 64 (EventBus 7) no bus is named:
    // each event says which group it is posted on, and a subscriber left to choose is registered where its events are.
    @Mod.EventBusSubscriber(modid = Trmt.MODID)
    public static final class Lifecycle {

        private Lifecycle() {}

        // Before the world loads, which is the point: what erodes has to be known before the first
        // chunk is asked about, and on this edition nothing on a server ever worked it out.
        @net.minecraftforge.eventbus.api.listener.SubscribeEvent
        public static void aboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
            com.trmtgtnh.server.ServerEvents.serverStarting(event.getServer());
        }

        @net.minecraftforge.eventbus.api.listener.SubscribeEvent
        public static void started(net.minecraftforge.event.server.ServerStartedEvent event) {
            com.trmtgtnh.server.ServerEvents.serverStarted(event.getServer());
        }

        @net.minecraftforge.eventbus.api.listener.SubscribeEvent
        public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
            com.trmtgtnh.server.ServerEvents.serverStopped();
        }
    }
}
