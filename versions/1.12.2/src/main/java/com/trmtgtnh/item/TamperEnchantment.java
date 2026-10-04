package com.trmtgtnh.item;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.trmtgtnh.Trmt;

/**
 * An enchantment that unlocks one of the big tampers' modes, and nothing else.
 *
 * <p>
 * The other edition writes this class out three times, once inside each of {@code EnchReinforce},
 * {@code EnchWard} and {@code EnchLight}, differing only in the name and the feature switch. It was
 * three copies there because each copy also carried its own hunt for a free numeric id, and that hunt
 * is the part 1.12.2 took away: an enchantment is a registry entry with a name now, Forge keeps the
 * numbers in step across saves, and a tool enchanted under one mod set still carries its enchantment
 * under another. What is left is small enough to say once.
 *
 * <p>
 * A digger-type enchantment restricted to the tampers. The type is {@code DIGGER} so it sits with the
 * tools rather than the weapons or armour, but the type never gets a chance to matter: {@link #canApply}
 * rejects every item but a chunk tamper, and the Wayfarer is one by inheritance. Rare, which is the
 * other edition's weight of two under the name this version gives it. One level - a tool either can do
 * the thing or it cannot.
 */
abstract class TamperEnchantment extends Enchantment {

    /**
     * @param name the translation name, which is the other edition's, so that one language file
     *             serves both
     * @param path the registry name, which is new: there was nothing to call it by before but a number
     */
    TamperEnchantment(String name, String path) {
        super(Rarity.RARE, EnumEnchantmentType.DIGGER, new EntityEquipmentSlot[] { EntityEquipmentSlot.MAINHAND });
        setName(name);
        setRegistryName(new ResourceLocation(Trmt.MODID, path));
    }

    /** The feature switch this enchantment answers to. Read live, like everything below that asks it. */
    abstract boolean enabled();

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    /**
     * Gated on the feature switch, so turning a feature off takes its enchantment out of the tables and
     * anvils for every tool, while the enchantment stays registered so a tool that already carries it
     * does not read as a broken one. A plain book at a table is let in by {@link #isAllowedOnBooks}
     * instead; a creative player at an anvil is asked nothing at all.
     */
    @Override
    public boolean canApply(ItemStack stack) {
        return enabled() && stack != null && stack.getItem() instanceof ItemChunkTamper;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canApply(stack);
    }

    /**
     * A plain book on an enchanting table is let in by this as well as by
     * {@link #canApplyAtEnchantingTable}, which no book passes, and the game's own answer is yes - so
     * until the other edition read the switch here, a table went on rolling the book with the feature
     * off. Read live, like {@link #canApply}.
     */
    @Override
    public boolean isAllowedOnBooks() {
        return enabled();
    }
}
