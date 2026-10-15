package com.trmtgtnh.neoforge;

import java.io.File;

import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.Plants;

/**
 * Where the mod starts under NeoForge. The Forge module's TrmtForge carried across, answering the same questions in
 * the same order and for the same reasons; where NeoForge differs, the difference is said where it is met.
 *
 * <p>
 * NeoForge hands the constructor its mod's own event bus and container rather than leaving them to be fetched from a
 * static context, which is the one change to its shape: what used to go through {@code FMLJavaModLoadingContext} and
 * {@code ModLoadingContext} goes through those two.
 *
 * <p>
 * Like Forge, it keeps the running server somewhere a static call can reach, so there is no lifecycle to subscribe to
 * in order to know. It is still asked whether it is running rather than merely whether it exists, because a client
 * that has closed a single-player world still has one.
 */
@Mod(Trmt.MODID)
public final class TrmtNeoForge {

    public TrmtNeoForge(IEventBus modBus, ModContainer container) {
        ModsPresent.use(
            modId -> ModList.get()
                .isLoaded(modId));
        // What this build calls itself, which the loader knows and the shared module does not. See Trmt.useVersion.
        Trmt.useVersion(
            container.getModInfo()
                .getVersion()
                .toString());
        // This jar's line in the update check's version file, whether this is a development game - which only the
        // loader can say - and the mods this jar requires. See UpdateNotice. A line of its own: no edition before this
        // one had a NeoForge jar, so there is no older line it could share. Cloth's NeoForge jar calls itself
        // cloth_config, as its Forge jar does, so Forge's list of required mods is this jar's too.
        com.trmtgtnh.server.UpdateNotice
            .edition("1.21.1-neoforge", !FMLEnvironment.production, com.trmtgtnh.server.UpdateNotice.REQUIRED_FORGE);
        // How the dependency check reads a required mod's version (0.9.221).
        com.trmtgtnh.server.UpdateNotice.modVersions(
            id -> ModList.get()
                .getModContainerById(id)
                .map(
                    mod -> mod.getModInfo()
                        .getVersion()
                        .toString())
                .orElse(null));
        Trmt.useHost(() -> {
            net.minecraft.server.MinecraftServer candidate = ServerLifecycleHooks.getCurrentServer();
            return candidate != null && candidate.isRunning() ? candidate : null;
        });

        // A tag that resolves whenever it is asked rather than when it is made, which is what a recipe's ingredient
        // needs: NeoForge keeps Forge's optional tag, ItemTags.create. See OreNames.LazyTags for what asking the
        // collection of the moment cost this edition.
        com.trmtgtnh.util.OreNames.use(name -> net.minecraft.tags.ItemTags.create(name));
        // NeoForge's own convention namespace for shared material tags, as the Forge jar hands over Forge's: c, not
        // forge. NeoForge 21.1 ships its material tags under c alone (data/c/tags/item/ingots/iron.json in its jar, and
        // no forge folder), and mods tag their metals there, so forge here read nought items under every name - and
        // the recipes waited for an iron tag that never filled, and were never built (0.9.226, the first NeoForge load).
        com.trmtgtnh.util.OreNames.namespace("c");
        // NeoForge's own word for a machine's player, which the shared module cannot name; the connection alone misses
        // one given a connection anyway (Machines; spec WD59). A block keeps a tile entity only as an EntityBlock, which
        // the shared module already asks (spec SD6).
        com.trmtgtnh.util.Machines.use(player -> player instanceof net.neoforged.neoforge.common.util.FakePlayer);

        // "This grows out of whatever is underneath it", which is exactly the question Plants asks. Forge answers with
        // its own IPlantable, which NeoForge removed at 1.20.5 - its plants now ask the ground through
        // canSustainPlant instead, and nothing names a plant as one. So the answer is the vanilla blocks Forge 52 marks
        // IPlantable, read from its patched game jar: every bush, the cactus, the reed and bamboo. Fabric answers with
        // the bush alone; this keeps the Forge jar's answer for every vanilla plant, and like Fabric misses a modded one
        // built on none of these (see Plants for why that is the safe direction).
        Plants.use(state -> {
            net.minecraft.world.level.block.Block block = state.getBlock();
            return block instanceof BushBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock
                || block instanceof BambooStalkBlock;
        });

        TrmtConfig.load(
            new File(
                FMLPaths.CONFIGDIR.get()
                    .toFile(),
                Trmt.MODID + ".cfg"));

        // The channel's payload types are registered when NeoForge asks for them, on this mod's bus; the way out is
        // wired now, as Forge's channel is opened here. See NeoForgeChannel.
        NeoForgeChannel.open(modBus);

        // How to ask a container what is inside it, which is a capability here as on Forge. See Fluids.
        NeoForgeFluids.use();

        // On a client, and as early as there is one: the wear pack, the client layer and the Config button. NeoForge
        // has no DistExecutor, so the client's half is a class of its own that only a client loads, called by name
        // once the loader says this is one. See TrmtNeoForgeClient.construct.
        if (FMLEnvironment.dist.isClient()) TrmtNeoForgeClient.construct(container);

        // What draws a Golem of Ways and what screen its orders open on are NOT here: both need the golem's registry
        // entry, and this constructor runs before any registry event has fired. See NeoForgeClientSetup.

        // Block ids moved - a world from another mod list opened, a server's numbering taken on, or either handed
        // back. NeoForge posts it on the game's bus, as Forge 47 did. See ServerEvents.idsMoved. Marked for removal in
        // NeoForge 21.1 and still posted there, which is what this version needs.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.registries.IdMappingEvent moved) -> com.trmtgtnh.server.ServerEvents.idsMoved());

        // The One Probe takes its API through a message sent while mods enqueue theirs, and only when it is installed,
        // as 1.20.1's Forge jar asked: OneProbeTooltip is the only class that names it, and nothing loads that class
        // otherwise. Jade and WTHIT find their plugins in the common module by themselves, so they need nothing here.
        modBus.addListener((net.neoforged.fml.event.lifecycle.InterModEnqueueEvent enqueue) -> {
            if (ModList.get()
                .isLoaded("theoneprobe")) OneProbeTooltip.request();
        });

        // The game version read from the game, so the line cannot go stale as the edition is carried (see TrmtFabric).
        Trmt.LOG.info("{} on NeoForge, Minecraft {}", Trmt.NAME, net.minecraft.SharedConstants.getCurrentVersion()
            .getName());
    }

    /**
     * The two written integrations, once a server is up.
     *
     * <p>
     * Not in the constructor: what they describe includes which tamper grades this pack can make, which is decided by
     * tags, and tags are not loaded until a server starts. See {@code ServerEvents.serverStarted}.
     */
    // The game's bus, which Forge's subscriber was told (FORGE) and NeoForge's works out from each event's type: none of
    // these is a mod-bus event.
    @EventBusSubscriber(modid = Trmt.MODID)
    public static final class Lifecycle {

        private Lifecycle() {}

        // Before the world loads, which is the point: what erodes has to be known before the first chunk is asked
        // about.
        @SubscribeEvent
        public static void aboutToStart(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
            com.trmtgtnh.server.ServerEvents.serverStarting(event.getServer());
        }

        @SubscribeEvent
        public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
            com.trmtgtnh.server.ServerEvents.serverStarted(event.getServer());
        }

        @SubscribeEvent
        public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
            com.trmtgtnh.server.ServerEvents.serverStopped();
        }
    }
}
