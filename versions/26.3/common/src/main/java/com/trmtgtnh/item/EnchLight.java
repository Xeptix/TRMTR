package com.trmtgtnh.item;

import net.minecraft.world.item.ItemStack;

/**
 * The enchantment that unlocks wayfinding mode, and nothing else.
 *
 * <p>
 * The third sibling of {@link EnchReinforce}, on the same class underneath - see {@link TamperEnchantment}, and
 * {@code data/trmtgtnh/enchantment/light.json}.
 *
 * <p>
 * Wayfinding mode lets a tamper light a square of worn ground in one of sixteen colors. The enchantment
 * only unlocks the mode; the gestures and their cost live in {@link LightGestures}, and the glow itself in
 * {@code GhostLight} and the ghost it is asked of.
 */
public final class EnchLight {

    /** The name, translation key and switch. */
    public static final TamperEnchantment UNLOCK = TamperEnchantment.LIGHT;

    private EnchLight() {}

    /** Whether a stack carries the wayfinding enchantment. */
    public static boolean has(ItemStack stack) {
        return UNLOCK.on(stack);
    }
}
