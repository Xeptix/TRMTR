package com.trmtgtnh.item;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;

import com.trmtgtnh.config.TrmtConfig;

/**
 * The enchantment that unlocks wayfinding mode, and nothing else.
 *
 * <p>
 * The third sibling of {@link EnchReinforce}, on the same class underneath - see
 * {@link TamperEnchantment} for why there is one of those rather than three copies of it.
 *
 * <p>
 * Wayfinding mode lets a tamper light a square of worn ground in one of sixteen colors. The enchantment
 * only unlocks the mode; the gestures and their cost live in {@link LightGestures}, and the glow itself in
 * {@code GhostLight} and the ghost it is asked of.
 */
public final class EnchLight {

    /** The registered instance. See {@link EnchReinforce#INSTANCE} for why callers still ask. */
    public static Enchantment INSTANCE;

    private EnchLight() {}

    /** Makes the enchantment. Called from the registry event, on both sides alike. */
    static Enchantment create() {
        INSTANCE = new TamperEnchantment("trmtLight", "light") {

            @Override
            boolean enabled() {
                return TrmtConfig.lightEnabled;
            }
        };
        return INSTANCE;
    }

    /** Whether a stack carries the wayfinding enchantment. */
    public static boolean has(ItemStack stack) {
        if (INSTANCE == null || stack == null || stack.isEmpty()) return false;
        return EnchantmentHelper.getEnchantmentLevel(INSTANCE, stack) > 0;
    }
}
