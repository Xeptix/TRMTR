package com.trmtgtnh.item;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.config.TrmtConfig;

/**
 * The enchantment that unlocks spawn-ward mode, and nothing else.
 *
 * <p>
 * A sibling of {@link EnchReinforce}: same shape, same reasons, and here the same class underneath -
 * see {@link TamperEnchantment} for why there is one of those rather than three copies of it.
 *
 * <p>
 * Ward mode lets a tamper bar hostile or passive mobs from spawning on a block. The enchantment only
 * unlocks the mode; the gestures and their cost live in {@link WardGestures}, and the refusal itself in
 * the server's spawn check.
 */
public final class EnchWard {

    /** The registered instance. See {@link EnchReinforce#INSTANCE} for why callers still ask. */
    public static Enchantment INSTANCE;

    private EnchWard() {}

    /** Makes the enchantment. Called from the registry event, on both sides alike. */
    static Enchantment create() {
        INSTANCE = new TamperEnchantment("trmtWard", "ward") {

            @Override
            boolean enabled() {
                return TrmtConfig.wardEnabled;
            }
        };
        return INSTANCE;
    }

    /** Whether a stack carries the ward enchantment. */
    public static boolean has(ItemStack stack) {
        if (INSTANCE == null || stack == null || stack.isEmpty()) return false;
        return EnchantmentHelper.getItemEnchantmentLevel(INSTANCE, stack) > 0;
    }
}
