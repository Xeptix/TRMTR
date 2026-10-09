package com.trmtgtnh.item;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;

import com.trmtgtnh.Trmt;

/**
 * The enchantment that unlocks path-light mode, and nothing else.
 *
 * <p>
 * The third of the set, and built exactly like {@link EnchReinforce} and {@link EnchWard}: only on
 * the two big tampers, one level, and an id taken from config where {@code -1} means "find a free
 * slot". See {@link EnchReinforce} for the full account of why the id is the awkward part.
 *
 * <p>
 * The enchantment only unlocks the mode. Lighting a block, recoloring it and putting it out are
 * all in {@link LightGestures}.
 */
public final class EnchLight {

    /** The registered instance, or null when registration could find no free slot. */
    public static Enchantment INSTANCE;

    private EnchLight() {}

    /**
     * Registers the enchantment. Called from the common proxy so client and server agree on the id.
     *
     * @param configuredId the id to use, or -1 to take the first free slot
     */
    public static void register(int configuredId) {
        if (INSTANCE != null) return;
        int id = configuredId >= 0 ? configuredId : firstFreeId();
        if (id < 0) {
            Trmt.LOG.warn("No free enchantment id for path light; the feature will be unavailable");
            return;
        }
        if (Enchantment.enchantmentsList[id] != null) {
            Trmt.LOG.warn(
                "Enchantment id {} is already taken by {}; path light will be unavailable. Set a free id in config.",
                Integer.valueOf(id),
                Enchantment.enchantmentsList[id].getName());
            return;
        }
        INSTANCE = new Ench(id);
        Trmt.LOG.info("Registered the path-light enchantment at id {}", Integer.valueOf(id));
    }

    /** Whether a stack carries the path-light enchantment. */
    public static boolean has(ItemStack stack) {
        if (INSTANCE == null || stack == null) return false;
        return EnchantmentHelper.getEnchantmentLevel(INSTANCE.effectId, stack) > 0;
    }

    private static int firstFreeId() {
        // From the top down, like its two siblings, so this one takes the third slot from the top.
        for (int id = Enchantment.enchantmentsList.length - 1; id >= 0; id--) {
            if (Enchantment.enchantmentsList[id] == null) return id;
        }
        return -1;
    }

    /** A digger-type enchantment restricted to the tampers. Same rationale as EnchReinforce. */
    private static final class Ench extends Enchantment {

        private Ench(int id) {
            super(id, 2, EnumEnchantmentType.digger);
            setName("trmtLight");
            Enchantment.addToBookList(this);
        }

        @Override
        public int getMinLevel() {
            return 1;
        }

        @Override
        public int getMaxLevel() {
            return 1;
        }

        @Override
        public boolean canApply(ItemStack stack) {
            // Gated on the feature switch so turning light off takes the enchantment out of the
            // tables and anvils for every tool, while the id stays registered so a tool that already
            // carries it does not read as a broken enchantment. A plain book at a table is let in by
            // isAllowedOnBooks instead, below; a creative player at an anvil is asked nothing at all.
            return com.trmtgtnh.config.TrmtConfig.lightEnabled && stack != null
                && stack.getItem() instanceof ItemChunkTamper;
        }

        @Override
        public boolean canApplyAtEnchantingTable(ItemStack stack) {
            return canApply(stack);
        }

        @Override
        public boolean isAllowedOnBooks() {
            // A plain book on an enchanting table is let in by this as well as by canApplyAtEnchantingTable,
            // which no book passes, and the game's own answer is yes - so until this read the switch, a table
            // went on rolling the book with the feature off. Read live, like canApply. UnlockBooks asks it
            // for a librarian's draw as well, so a table and a librarian cannot come to different answers.
            return com.trmtgtnh.config.TrmtConfig.lightEnabled;
        }
    }
}
