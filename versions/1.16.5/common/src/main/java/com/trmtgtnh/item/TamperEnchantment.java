package com.trmtgtnh.item;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;


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
 * tools rather than the weapons or armour, but the type never gets a chance to matter: {@link #canEnchant}
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
    private final String key;

    TamperEnchantment(String name, String path) {
        super(Rarity.RARE, EnchantmentCategory.DIGGER, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
        this.key = "enchantment." + name;
    }

    /**
     * The translation key, which is the other editions' and not this version's.
     *
     * <p>
     * Left to itself this version would work the key out from the registry name - enchantment.
     * trmtgtnh.light - and that is a key neither older edition has. One language file serves all
     * three, so the key is answered here and the registry name is given at registration, where it
     * belongs.
     */
    @Override
    public String getDescriptionId() {
        return key;
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
     * does not read as a broken one. A plain book at a table is kept out by {@link #isDiscoverable}
     * instead; a creative player at an anvil is asked nothing at all.
     */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return enabled() && stack != null && stack.getItem() instanceof ItemChunkTamper;
    }

    /**
     * Whether an enchanting table may offer this on a tool: the same answer as an anvil's, as the other editions'
     * {@code canApplyAtEnchantingTable} gives it.
     *
     * <p>
     * <strong>Forge's question, declared here without {@code @Override}</strong>, because the shared module is built
     * against the game without Forge's additions and Forge's own method names are never remapped: on Forge this is
     * {@code Enchantment.canApplyAtEnchantingTable}, which a table asks instead of the category. Left to Forge's
     * default it asked the category, DIGGER, which takes only a digging tool - and a chunk tamper is no digging tool,
     * so until 0.9.222 no table offered any of the three on one (spec RL2). Fabric's table asks the category alone;
     * {@code MixinTableOffersUnlocks} adds the three there.
     */
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    /**
     * Whether an enchanting table may offer this at all. Only while its feature is on.
     *
     * <p>
     * <strong>This is the question both older editions wanted and neither had.</strong> There the
     * gap is a plain book: {@link #canEnchant} is asked about a tool, and a table asks nothing about
     * a book - so a switched-off unlock could be rolled onto one. 1.7.10 and 1.12.2 close that with
     * Forge's {@code isAllowedOnBooks}, which is Forge's own addition with no counterpart on the
     * other loader, and which is why this class was first carried without it and said so.
     *
     * <p>
     * 1.16 added the vanilla question instead, for Soul Speed: an enchantment no table may offer and
     * no librarian may trade, obtainable only from a piglin. That is exactly the shape a switched-off
     * unlock wants, it is asked of the enchantment rather than of the item, and it is asked the same
     * way on both loaders. So this edition answers a better question than either older one and needs
     * nothing a loader has to supply.
     */
    @Override
    public boolean isDiscoverable() {
        return enabled();
    }

    /**
     * Whether a librarian may unlock a book trade for this. Only while its feature is on.
     *
     * <p>
     * <strong>And this is what {@code MixinLibrarianBooks} was for.</strong> A librarian draws its
     * book enchantment evenly from the registry and asks it nothing, in both older editions - so a
     * switched-off unlock came up as often as Sharpness, for a book nothing could use. 1.7.10 picks
     * an {@code Enchantment} local out of a long method by type to redraw it and 1.12.2 does the
     * same to a smaller method; both are mixins into the villager.
     *
     * <p>
     * 1.16 filters the draw by this question before making it, so there is nothing to redraw and no
     * mixin to write. {@code UnlockBooks.forLibrarian} is carried and now has no caller here, which
     * is worth leaving rather than deleting: it is one of the things the drift check compares
     * against the other editions' copy.
     */
    @Override
    public boolean isTradeable() {
        return enabled();
    }

}
