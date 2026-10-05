package com.trmtgtnh.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.item.ModPotions;

/**
 * Putting this mod's blocks into Forge's registry.
 *
 * <p>
 * Half of the first thing in this port the two loaders genuinely cannot share. Forge fires an event
 * when it is ready to be told about blocks and refuses them at any other moment; Fabric has no such
 * event and expects a direct call during initialisation. So the common module says what to register
 * and this says when and where.
 *
 * <p>
 * The registry event rather than a {@code DeferredRegister}, which is the more usual Forge idiom and
 * is the wrong shape here: a deferred register wants to own the names and the suppliers, and the
 * names belong to the common module, which is where both loaders read them from. Handing each block
 * over as it arrives keeps one list of what exists rather than two that have to agree.
 */
@Mod.EventBusSubscriber(modid = Trmt.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeRegistration {

    private ForgeRegistration() {}

    /**
     * Forge fires one event per registry and refuses a registration made during the wrong one, so
     * each registry this mod puts something into has its own method here. Blocks are the simplest of
     * them: nothing this mod registers as a block has an item form - a ghost is painted rather than
     * placed - so this event is the only one that asks {@code ModBlocks} for anything. The item event
     * came back through here too while the rendering spike had a placeable ghost; see
     * {@code ModBlocks.register} for what became of it.
     */
    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> event) {
        ModBlocks.register(
            (name, block) -> event.getRegistry()
                .register(block.setRegistryName(name)));
        Trmt.LOG.info("Registered blocks with Forge");
    }

    /**
     * The two draughts. Their own event, for the same reason blocks and items have their own: Forge
     * refuses a registration made during the wrong one.
     */
    @SubscribeEvent
    public static void effects(RegistryEvent.Register<MobEffect> event) {
        ModPotions.register(
            (name, effect) -> event.getRegistry()
                .register(effect.setRegistryName(name)));
        Trmt.LOG.info("Registered effects with Forge");
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        com.trmtgtnh.item.ModItems.register(new com.trmtgtnh.item.ModItems.Registrar() {

            @Override
            public void item(ResourceLocation name, Item item) {
                event.getRegistry()
                    .register(item.setRegistryName(name));
            }

            // The three answers only this loader can hear. See ForgeTampers.
            @Override
            public com.trmtgtnh.item.ItemGradedTamper makeGradedTamper() {
                return new ForgeTampers.Graded();
            }

            @Override
            public com.trmtgtnh.item.ItemChunkTamper makeChunkTamper() {
                return new ForgeTampers.Chunk();
            }

            @Override
            public com.trmtgtnh.item.ItemMagicTamper makeMagicTamper() {
                return new ForgeTampers.Magic();
            }
        });
        Trmt.LOG.info("Registered items with Forge");
    }

    /** The three tamper enchantments, in their own registry's turn. */
    @SubscribeEvent
    public static void enchantments(
        RegistryEvent.Register<net.minecraft.world.item.enchantment.Enchantment> event) {
        com.trmtgtnh.item.ModEnchantments.register(
            (name, enchantment) -> event.getRegistry()
                .register(enchantment.setRegistryName(name)));
        Trmt.LOG.info("Registered enchantments with Forge");
    }

    /**
     * The golem.
     *
     * <p>
     * Its attributes are not here, and cannot be: an attribute supplier is built against the
     * registry of attributes, so it cannot be built while that registry is still being filled.
     * Forge asks for them in an event of their own once every type in the game exists. Fabric draws
     * no such distinction and takes both at once, which is why {@code ModEntities.Registrar} has two
     * methods and Forge answers them in two places.
     */
    @SubscribeEvent
    public static void entities(RegistryEvent.Register<net.minecraft.world.entity.EntityType<?>> event) {
        com.trmtgtnh.entity.ModEntities.register(new com.trmtgtnh.entity.ModEntities.Registrar() {

            @Override
            public void entity(ResourceLocation name, net.minecraft.world.entity.EntityType<?> type) {
                event.getRegistry()
                    .register(type.setRegistryName(name));
            }

            /** Deliberately nothing. See {@link ForgeRegistration#attributes}. */
            @Override
            public void attributes(net.minecraft.world.entity.EntityType<?> type,
                net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder built) {}
        });
        Trmt.LOG.info("Registered entities with Forge");
    }

    /** The golem's four numbers, in the event Forge keeps for them. */
    @SubscribeEvent
    public static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(
            com.trmtgtnh.entity.ModEntities.golemType(),
            com.trmtgtnh.entity.EntityGolemOfWays.createAttributes()
                .build());
        Trmt.LOG.info("Registered the golem's attributes with Forge");
    }

    /**
     * The window the golem's orders are given through, and the one thing about it Forge does its own
     * way.
     *
     * <p>
     * {@code IForgeContainerType} rather than the vanilla {@code MenuType}, and that is the whole of
     * it: vanilla's factory is handed a window id and the player's inventory and nothing else, and
     * this window has to know <em>which golem</em>. Forge's version also passes the extra bytes the
     * server wrote when it opened the thing. Fabric's answer is {@code ExtendedScreenHandlerType},
     * which does the same job under another name - so the id travels as an id on both, and neither
     * way of saying so can live in the shared module. See {@code GolemMenu}.
     */
    @SubscribeEvent
    public static void menus(RegistryEvent.Register<net.minecraft.world.inventory.MenuType<?>> event) {
        net.minecraft.world.inventory.MenuType<com.trmtgtnh.entity.ContainerGolem> type =
            net.minecraftforge.common.extensions.IForgeContainerType
                .create(com.trmtgtnh.entity.ContainerGolem::new);
        type.setRegistryName(new ResourceLocation(Trmt.MODID, "golem"));
        event.getRegistry()
            .register(type);
        com.trmtgtnh.entity.GolemMenu.use(type);

        com.trmtgtnh.entity.GolemMenu.use((player, golem) -> {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer)) return;
            net.minecraftforge.fml.network.NetworkHooks.openGui(
                (net.minecraft.server.level.ServerPlayer) player,
                new net.minecraft.world.MenuProvider() {

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
                },
                buffer -> buffer.writeVarInt(golem.getId()));
        });
        Trmt.LOG.info("Registered the golem's window with Forge");
    }
}
