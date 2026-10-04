package com.trmtgtnh.item;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;

import com.trmtgtnh.Trmt;

/**
 * The enchantment that unlocks reinforce mode, and nothing else.
 *
 * <p>
 * It goes only on the two big tampers, which it enforces itself: {@link Ench#canApply} refuses every
 * other tool, so an enchanting table or an anvil puts it on a chunk tamper and on no other tool. A plain
 * book is the exception the game makes: a table lets a book take any enchantment that answers yes to
 * {@link Ench#isAllowedOnBooks}, which this one answers from the feature switch, and {@link UnlockBooks}
 * holds a librarian's new trades to the same answer. One level - a tool either can reinforce or it cannot.
 *
 * <p>
 * The id is the awkward part. Vanilla keeps enchantments in a fixed array of 256, and a pack this
 * size has claimed many of them, so a hard-coded number would land on somebody else's. The id is
 * taken from config, and {@code -1} means "find a free slot at startup". The catch a packmaker has
 * to know is that the id is written into the enchanted tool's own data: if the free slot drifts
 * because the mod set changed, a tool enchanted at the old id quietly loses it. Pinning the number
 * is the fix, which is why the config says so.
 */
public final class EnchReinforce {

    /** The registered instance, or null when registration could find no free slot. */
    public static Enchantment INSTANCE;

    private EnchReinforce() {}

    /**
     * Registers the enchantment. Called from the common proxy so client and server agree on the id.
     *
     * @param configuredId the id to use, or -1 to take the first free slot
     */
    public static void register(int configuredId) {
        if (INSTANCE != null) return;
        int id = configuredId >= 0 ? configuredId : firstFreeId();
        if (id < 0) {
            Trmt.LOG.warn("No free enchantment id for reinforcement; the feature will be unavailable");
            return;
        }
        if (Enchantment.enchantmentsList[id] != null) {
            Trmt.LOG.warn(
                "Enchantment id {} is already taken by {}; reinforcement will be unavailable. Set a free id in config.",
                Integer.valueOf(id),
                Enchantment.enchantmentsList[id].getName());
            return;
        }
        INSTANCE = new Ench(id);
        Trmt.LOG.info("Registered the reinforcement enchantment at id {}", Integer.valueOf(id));
    }

    /** The reinforcement level on a stack - 0 or 1, since the enchant has one level. */
    public static boolean has(ItemStack stack) {
        if (INSTANCE == null || stack == null) return false;
        return EnchantmentHelper.getEnchantmentLevel(INSTANCE.effectId, stack) > 0;
    }

    private static int firstFreeId() {
        // From the top down, so the mod's own enchantment tends to sit above the crowded low ids
        // the base game and older mods use.
        for (int id = Enchantment.enchantmentsList.length - 1; id >= 0; id--) {
            if (Enchantment.enchantmentsList[id] == null) return id;
        }
        return -1;
    }

    /**
     * A digger-type enchantment restricted to the tampers.
     *
     * <p>
     * The type is {@code digger} so it sits with the tools rather than the weapons or armour, but
     * the type never gets a chance to matter: {@code canApply} rejects every item but a chunk
     * tamper, and the Wayfarer is one by inheritance.
     */
    private static final class Ench extends Enchantment {

        private Ench(int id) {
            super(id, 2, EnumEnchantmentType.digger);
            setName("trmtReinforce");
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
            // Gated on the feature switch so turning reinforcement off takes the enchantment out of the
            // tables and anvils for every tool, while the id stays registered so a tool that already
            // carries it does not read as a broken enchantment. A plain book at a table is let in by
            // isAllowedOnBooks instead, below; a creative player at an anvil is asked nothing at all.
            return com.trmtgtnh.config.TrmtConfig.reinforceEnabled && stack != null
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
            return com.trmtgtnh.config.TrmtConfig.reinforceEnabled;
        }
    }
}
