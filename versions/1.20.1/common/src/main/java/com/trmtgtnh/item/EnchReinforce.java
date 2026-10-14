package com.trmtgtnh.item;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.config.TrmtConfig;

/**
 * The enchantment that unlocks reinforce mode, and nothing else.
 *
 * <p>
 * It goes only on the two big tampers, which it enforces itself: {@link TamperEnchantment#canApply}
 * refuses every other tool, so an enchanting table or an anvil puts it on a chunk tamper and on no other
 * tool. A plain book is the exception the game makes: a table lets a book take any enchantment that
 * answers yes to {@link TamperEnchantment#isAllowedOnBooks}, which this one answers from the feature
 * switch. One level - a tool either can reinforce or it cannot.
 *
 * <p>
 * The other edition's account of this class is mostly about its id: a fixed array of 256, a pack that
 * has claimed most of it, a config setting that pins the number, and a tool that quietly loses its
 * enchantment when the free slot drifts. None of that survives into 1.12.2, where an enchantment is
 * registered by name and the numbers are Forge's to keep in step. {@code reinforce.enchantId} is still
 * read, because the settings are one file shared with the other edition, and changes nothing here.
 */
public final class EnchReinforce {

    /**
     * The registered instance.
     *
     * <p>
     * Never null once registration has run, where the other edition's could be - it had no free slot to
     * give it. Every caller still asks, because they were written against that edition and it costs
     * nothing to keep them true for both.
     */
    public static Enchantment INSTANCE;

    private EnchReinforce() {}

    /** Makes the enchantment. Called from the registry event, on both sides alike. */
    static Enchantment create() {
        INSTANCE = new TamperEnchantment("trmtReinforce", "reinforce") {

            @Override
            boolean enabled() {
                return TrmtConfig.reinforceEnabled;
            }
        };
        return INSTANCE;
    }

    /** Whether a stack carries the reinforcement enchantment. */
    public static boolean has(ItemStack stack) {
        if (INSTANCE == null || stack == null || stack.isEmpty()) return false;
        return EnchantmentHelper.getItemEnchantmentLevel(INSTANCE, stack) > 0;
    }
}
