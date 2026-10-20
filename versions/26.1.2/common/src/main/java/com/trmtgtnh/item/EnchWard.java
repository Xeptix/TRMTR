package com.trmtgtnh.item;

import net.minecraft.world.item.ItemStack;

/**
 * The enchantment that unlocks spawn-ward mode, and nothing else.
 *
 * <p>
 * A sibling of {@link EnchReinforce}: same shape, same reasons, and the same class underneath - see
 * {@link TamperEnchantment}, and {@code data/trmtgtnh/enchantment/ward.json}.
 *
 * <p>
 * Ward mode lets a tamper bar hostile or passive mobs from spawning on a block. The enchantment only
 * unlocks the mode; the gestures and their cost live in {@link WardGestures}, and the refusal itself in
 * the server's spawn check.
 */
public final class EnchWard {

    /** The name, translation key and switch. */
    public static final TamperEnchantment UNLOCK = TamperEnchantment.WARD;

    private EnchWard() {}

    /** Whether a stack carries the ward enchantment. */
    public static boolean has(ItemStack stack) {
        return UNLOCK.on(stack);
    }
}
