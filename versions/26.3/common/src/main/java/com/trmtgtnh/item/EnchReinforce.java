package com.trmtgtnh.item;

import net.minecraft.world.item.ItemStack;

/**
 * The enchantment that unlocks reinforce mode, and nothing else.
 *
 * <p>
 * It goes only on the two big tampers: its {@code supported_items} names those two and nothing else, so an enchanting
 * table or an anvil puts it on a chunk tamper and on no other tool, and a plain book takes it at a table while its
 * feature is on. One level - a tool either can reinforce or it cannot.
 *
 * <p>
 * The older editions keep the registered instance here. This version has none to keep: the enchantment is data,
 * {@code data/trmtgtnh/enchantment/reinforce.json}, made by the game when a world loads - see
 * {@link TamperEnchantment}, which holds its name and its switch. {@code reinforce.enchantId} is still read, because
 * the settings are one file shared with the other editions, and changes nothing here.
 */
public final class EnchReinforce {

    /** The name, translation key and switch. */
    public static final TamperEnchantment UNLOCK = TamperEnchantment.REINFORCE;

    private EnchReinforce() {}

    /** Whether a stack carries the reinforcement enchantment. */
    public static boolean has(ItemStack stack) {
        return UNLOCK.on(stack);
    }
}
