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

    /**
     * A vanilla chest's random book, drawn again when it landed on one of the three unlocks.
     *
     * <p>
     * At this version a chest's book takes an enchantment drawn evenly from every one registered, asking it nothing -
     * not its switch, not whether it is treasure - so the dungeon, mineshaft, desert temple and mansion chests handed
     * out the unlocks, switched off or not; the 1.7.10 edition's chests roll their books at level thirty, which none of
     * the three can reach, and its own find is the only chest that gives one (0.9.222, spec BP29). Drawn again the way
     * the game drew it - evenly, any level the enchantment has - from everything but the three, so every other book's
     * odds stand as they were. Called by {@code MixinChestBooksDrawAgain} with what the draw made.
     */
    public static net.minecraft.item.ItemStack withoutUnlocks(net.minecraft.item.ItemStack book, java.util.Random rand) {
        if (book == null || book.getItem() != net.minecraft.init.Items.ENCHANTED_BOOK || rand == null) return book;
        boolean unlock = false;
        for (Enchantment each : net.minecraft.enchantment.EnchantmentHelper.getEnchantments(book)
            .keySet()) {
            if (each instanceof TamperEnchantment) unlock = true;
        }
        if (!unlock) return book;
        java.util.List<Enchantment> others = new java.util.ArrayList<Enchantment>();
        for (Enchantment each : Enchantment.REGISTRY) {
            if (!(each instanceof TamperEnchantment)) others.add(each);
        }
        if (others.isEmpty()) return new net.minecraft.item.ItemStack(net.minecraft.init.Items.BOOK);
        Enchantment drawn = others.get(rand.nextInt(others.size()));
        net.minecraft.item.ItemStack again = new net.minecraft.item.ItemStack(net.minecraft.init.Items.ENCHANTED_BOOK);
        net.minecraft.item.ItemEnchantedBook.addEnchantment(
            again,
            new net.minecraft.enchantment.EnchantmentData(
                drawn,
                net.minecraft.util.math.MathHelper.getInt(rand, drawn.getMinLevel(), drawn.getMaxLevel())));
        return again;
    }
}
