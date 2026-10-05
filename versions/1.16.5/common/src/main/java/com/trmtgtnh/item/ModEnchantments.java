package com.trmtgtnh.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import com.trmtgtnh.Trmt;

/**
 * Where the three tamper enchantments are made, and the seam that lets two loaders register them.
 *
 * <p>
 * The 1.12.2 edition keeps this as a Forge registry event with three lines in it. There is no such
 * event in a module two loaders share, so this is the arrangement {@code ModBlocks}, {@code
 * ModPotions} and {@code ModItems} are already under: the enchantments are made here and handed to
 * whatever the loader supplies for putting them away.
 *
 * <p>
 * Each is made once however often this is called, and it is called more than once - Forge fires a
 * separate event per registry and each comes back through here. A second instance would leave the
 * registry holding one and every {@code has()} test asking about another, which reads in game as an
 * enchantment that applies and then does nothing.
 */
public final class ModEnchantments {

    /** What a loader module supplies: somewhere to put an enchantment under a name. */
    public interface Registrar {

        void enchantment(ResourceLocation name, Enchantment enchantment);
    }

    private ModEnchantments() {}

    /**
     * Makes the three and hands them over.
     *
     * <p>
     * The registry name is given here rather than set on the enchantment, which is the whole of what
     * changed at this version: an enchantment used to carry its own name and is now told it by
     * whoever registers it. Its translation key is still its own - see {@link TamperEnchantment} for
     * why that one did not move.
     */
    public static void register(Registrar into) {
        into.enchantment(new ResourceLocation(Trmt.MODID, "reinforce"), reinforce());
        into.enchantment(new ResourceLocation(Trmt.MODID, "ward"), ward());
        into.enchantment(new ResourceLocation(Trmt.MODID, "light"), light());
    }

    private static Enchantment reinforce() {
        return EnchReinforce.INSTANCE == null ? EnchReinforce.create() : EnchReinforce.INSTANCE;
    }

    private static Enchantment ward() {
        return EnchWard.INSTANCE == null ? EnchWard.create() : EnchWard.INSTANCE;
    }

    private static Enchantment light() {
        return EnchLight.INSTANCE == null ? EnchLight.create() : EnchLight.INSTANCE;
    }
}
