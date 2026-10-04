package com.trmtgtnh.item;

import net.minecraft.enchantment.Enchantment;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.trmtgtnh.Tags;

/**
 * Where the tampers' enchantments are made and registered.
 *
 * <p>
 * The other edition registers these from its common proxy with a numeric id each, taken from config or
 * found free at startup, so that client and server agree. Here they are registry entries like the items
 * and blocks, registered from the same kind of event and on the same terms: both sides register the
 * same set, always, whatever the settings say. A feature that is switched off still has its enchantment,
 * so a tool that carries it keeps it; what the switch changes is whether a table or an anvil will offer
 * it, which the enchantment asks for itself.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModEnchantments {

    private ModEnchantments() {}

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Enchantment> event) {
        event.getRegistry()
            .register(EnchReinforce.create());
        event.getRegistry()
            .register(EnchWard.create());
        event.getRegistry()
            .register(EnchLight.create());
    }
}
