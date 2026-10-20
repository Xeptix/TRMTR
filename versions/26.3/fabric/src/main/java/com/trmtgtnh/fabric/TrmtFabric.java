package com.trmtgtnh.fabric;

import java.io.File;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.VegetationBlock;

import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.item.ModPotions;
import com.trmtgtnh.surface.Plants;

/**
 * Where the mod starts under Fabric.
 *
 * <p>
 * Thin on purpose. The two older editions put the lifecycle in the mod class itself, which worked
 * because each had one loader; here the same code has to start twice, so what is shared went into the
 * common module and what is left in this class is the two things only a loader can answer and the
 * order they have to be answered in.
 *
 * <p>
 * The order matters and is the whole reason this class reads as it does. The settings layer asks
 * whether other mods are installed while it is being read, so the answer has to be in place before
 * the file is. Reading the settings first would not fail - it would quietly decide that no companion
 * mod is present and configure the mod for a world that is not there.
 */
public final class TrmtFabric implements ModInitializer {

    /**
     * The server while it is running, or null.
     *
     * <p>
     * Fabric has no static accessor for it, so it is caught as it starts and let go as it stops. That
     * is also the more honest answer than one: a client that has opened a single-player world keeps a
     * server instance after the world closes, and the question the settings layer asks is whether
     * there is a world here to tell about a change.
     */
    private static volatile MinecraftServer running;

    @Override
    public void onInitialize() {
        ModsPresent.use(
            modId -> FabricLoader.getInstance()
                .isModLoaded(modId));
        Trmt.useHost(() -> running);

        // Block ids moved: Fabric's registry sync renumbers blocks to a server's ids on joining it, to a
        // world's own as it opens, and back again. See ServerEvents.idsMoved.
        net.fabricmc.fabric.api.event.registry.RegistryIdRemapCallback.event(net.minecraft.core.registries.BuiltInRegistries.BLOCK)
            .register(moved -> com.trmtgtnh.server.ServerEvents.idsMoved());

        // The same seam as the Forge side's, filled with this loader's word for it. See
        // OreNames.LazyTags for why a recipe cannot hold a tag fetched from the collection of the
        // moment.
        // From 1.18 a tag is a key, which resolves against the collection of the moment whenever it is asked - the
        // named handle 1.16.5's TagRegistry.item made, now vanilla's own on both loaders.
        com.trmtgtnh.util.OreNames.use(
            name -> net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, name));
        // c: on all three loaders at 1.21.1, Fabric API's convention tags among them; 1.20.1's Fabric jar shipped
        // forge: tags of its own, which are gone here (see OreNames).
        com.trmtgtnh.util.OreNames.namespace("c");
        // Fabric API's own word for a machine's player, which the shared module cannot name: a mod's stand-in that
        // is given a connection anyway passes the connection test as a person (Machines; spec WD59). Fabric API has
        // carried FakePlayer from 0.80 (1.19.4); until 0.9.226 this jar handed nothing over, believing it had none.
        com.trmtgtnh.util.Machines.use(player -> player instanceof net.fabricmc.fabric.api.entity.FakePlayer);

        // Vanilla's bush class, which is what every plant that needs ground underneath it extends.
        // Forge answers this with its own IPlantable instead, so the two agree about vanilla and
        // differ about a modded plant that extends neither - see Plants for why that is the safe
        // direction to differ in.
        // From 1.21.5 that class is VegetationBlock: BushBlock is now one plant of its own (the bush), which extends it.
        // Asking for BushBlock would recognise that one plant and no other; VegetationBlock is 1.21.1's BushBlock.
        Plants.use(state -> state.getBlock() instanceof VegetationBlock);

        // What this build calls itself; see Trmt.useVersion and the Forge module's copy of this.
        Trmt.useVersion(
            net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(Trmt.MODID)
                .map(
                    container -> container.getMetadata()
                        .getVersion()
                        .getFriendlyString())
                .orElse("unknown"));
        // This jar's line in the update check's version file, whether this is a development game, and the
        // mods this jar requires; see UpdateNotice and the Forge module's copy of this.
        com.trmtgtnh.server.UpdateNotice.edition(
            "26.3-fabric",
            FabricLoader.getInstance()
                .isDevelopmentEnvironment(),
            com.trmtgtnh.server.UpdateNotice.REQUIRED_FABRIC);
        // How the dependency check reads a required mod's version (0.9.221).
        com.trmtgtnh.server.UpdateNotice.modVersions(
            id -> FabricLoader.getInstance()
                .getModContainer(id)
                .map(
                    mod -> mod.getMetadata()
                        .getVersion()
                        .getFriendlyString())
                .orElse(null));

        // Before the world loads, which is the point: what erodes has to be known before the first
        // chunk is asked about, and on this edition nothing on a server ever worked it out.
        ServerLifecycleEvents.SERVER_STARTING
            .register(server -> com.trmtgtnh.server.ServerEvents.serverStarting(server));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> running = server);
        // Once the worlds are loaded, as Forge's FMLServerStartedEvent and 1.7.10's are: examining the world reads
        // the save, which is not there before. Until 0.9.222 this ran at SERVER_STARTING, before the worlds loaded.
        // After the server is on record, because both writers ask this mod where it is.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> com.trmtgtnh.server.ServerEvents.serverStarted(server));
        // Once the server has stopped, after its last save - Forge's FMLServerStoppedEvent and 1.7.10's moment. Until
        // 0.9.222 this was SERVER_STOPPING, which Fabric fires at the head of the shutdown, before the server saves its
        // worlds: the wear store was emptied first, every loaded chunk was then saved without its wear, and each world
        // lost what had been worn since its last autosave every time it closed (spec PN28).
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            running = null;
            com.trmtgtnh.server.ServerEvents.serverStopped();
        });

        // Fabric has no registry event to wait for: the registries are open during initialisation and
        // closed after it, so this is both the right moment and the only one. Before the settings are
        // read, because nothing in registration may depend on a setting - which blocks exist goes into
        // a save's own record of what exists, and a set that varies with the settings is a save that
        // will not open after somebody changes one.
        ModBlocks.register((name, block) -> Registry.register(net.minecraft.core.registries.BuiltInRegistries.BLOCK, name, block));

        ModPotions.register((name, effect) -> Registry.register(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT, name, effect));

        com.trmtgtnh.item.ModItems.register((name, item) -> Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, name, item));
        // No enchantments to register: from 1.21 they are data, read from data/trmtgtnh/enchantment as a world loads
        // (see TamperEnchantment).

        // The golem, and its four numbers in the same breath. Forge needs two moments for this and
        // Fabric one, which is why ModEntities asks for both in a single call and lets the loader
        // decide when each happens; see ForgeRegistration.attributes for the other half of it.
        com.trmtgtnh.entity.ModEntities.register(new com.trmtgtnh.entity.ModEntities.Registrar() {

            @Override
            public void entity(net.minecraft.resources.Identifier name,
                net.minecraft.world.entity.EntityType<?> type) {
                Registry.register(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE, name, type);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void attributes(net.minecraft.world.entity.EntityType<?> type,
                net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder built) {
                // The cast is the registry's doing rather than ours: Fabric's helper is declared
                // for a type of living entity and ModEntities hands over a type of anything,
                // because the seam has to be able to carry an entity that is not a mob one day.
                net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                    (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>) type,
                    built);
            }
        });
        registerGolemMenu();

        FabricChannel.open();

        // How to ask a container what is inside it, which here means a bucket and nothing else. See
        // Fluids for why that is the loader's limit rather than this mod's.
        FabricFluids.use();

        // The four moments Fabric has an event for. The other seven are mixins, and FabricEvents
        // says which and why.
        FabricEvents.open();

        TrmtConfig.load(
            new File(
                FabricLoader.getInstance()
                    .getConfigDir()
                    .toFile(),
                Trmt.MODID + ".cfg"));

        // The game version read from the game, so the line cannot go stale as the edition is carried: 1.20.1's Fabric
        // line said 1.16.5, the edition it was carried from, until 0.9.226 (StartLineNamesItsGameTest).
        // name(), from 1.21.6, where WorldVersion became an interface of plain accessors; the same name as before.
        Trmt.LOG.info("{} on Fabric, Minecraft {}", Trmt.NAME, net.minecraft.SharedConstants.getCurrentVersion()
            .name());
    }

    /**
     * The window the golem's orders are given through.
     *
     * <p>
     * An <em>extended</em> screen handler type, which is the one thing about this Fabric does its
     * own way: the ordinary one hands its factory a window id and the player's inventory and
     * nothing else, and this window has to know <em>which golem</em>. The extended one carries the
     * bytes the server wrote when it opened the thing. Forge's answer is
     * {@code IForgeContainerType}, which does the same job under another name.
     *
     * <p>
     * Opening it writes the golem's id into that buffer, and the client's end of
     * {@code ContainerGolem} reads it back and finds the entity. The identity check stays on the
     * server: the menu is only opened for a golem somebody is standing next to, and a client that
     * is sent an id it has no golem for refuses the window rather than showing an empty one.
     */
    private static void registerGolemMenu() {
        // From 1.20.5 what the server sends with the window is typed data with a codec of its own rather than bytes
        // written into a buffer: here the golem's id, a varint, which is what 1.20.1 wrote. The client's end is
        // handed it in the buffer ContainerGolem has always read, so the menu is shared with Forge unchanged.
        // Fabric API 0.155 (26.1) renamed the screen handler API to Mojang's words: ExtendedScreenHandlerType is
        // ExtendedMenuType and ExtendedScreenHandlerFactory ExtendedMenuProvider, in api.menu.v1; the same two parts.
        net.minecraft.world.inventory.MenuType<com.trmtgtnh.entity.ContainerGolem> type = Registry.register(
            net.minecraft.core.registries.BuiltInRegistries.MENU,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(Trmt.MODID, "golem"),
            new net.fabricmc.fabric.api.menu.v1.ExtendedMenuType<com.trmtgtnh.entity.ContainerGolem, Integer>(
                (windowId, inventory, golemId) -> {
                    net.minecraft.network.FriendlyByteBuf extra = new net.minecraft.network.FriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer());
                    extra.writeVarInt(golemId.intValue());
                    return new com.trmtgtnh.entity.ContainerGolem(windowId, inventory, extra);
                },
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT));
        com.trmtgtnh.entity.GolemMenu.use(type);

        com.trmtgtnh.entity.GolemMenu.use((player, golem) -> {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer)) return;
            player.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<Integer>() {

                @Override
                public net.minecraft.network.chat.Component getDisplayName() {
                    return golem.getName();
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int windowId,
                    net.minecraft.world.entity.player.Inventory inventory,
                    net.minecraft.world.entity.player.Player opening) {
                    return new com.trmtgtnh.entity.ContainerGolem(windowId, inventory, golem);
                }

                @Override
                public Integer getScreenOpeningData(net.minecraft.server.level.ServerPlayer to) {
                    return Integer.valueOf(golem.getId());
                }
            });
        });
    }
}
