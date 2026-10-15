package com.trmtgtnh.util;

import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * What a stack remembers, kept where 1.21.1 keeps it: the stack's {@code minecraft:custom_data} component.
 *
 * <p>
 * 1.20.5 took the free-form tag off the stack and put item data into components. Every older edition keeps a tool's
 * grade, reach and modes in that tag, under the key {@code trmt}; this edition keeps the same compound, under the same
 * key, in the custom-data component rather than in a component type of its own. That is deliberate, and it is the
 * reason this class exists rather than a registered type: when a 1.20.1 world is opened on 1.21.1, vanilla's own data
 * fixer moves every tag key it does not know into {@code minecraft:custom_data}, unchanged - so a tamper made on 1.20.1
 * arrives here with its grade and its modes already where this edition reads them, and nothing of ours has to run for
 * that. A component type of our own would need a fixer of our own to carry them, and every tool a player owns would be
 * reset to iron the day they upgraded. (Spec: the upgraded world keeps everything; 0.9.226's world-upgrade spike.)
 *
 * <p>
 * A component is immutable once on the stack - its hash is cached, and a stack compares by it - so the tag is never
 * changed in place: {@link #read} hands back a copy, and {@link #edit} replaces the component with the changed one.
 */
public final class StackData {

    private StackData() {}

    /** A copy of the stack's custom data, or null when it has none. Reading never adds a component to the stack. */
    public static CompoundTag read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : data.copyTag();
    }

    /** Whether the stack carries any custom data at all - the older editions' {@code hasTag()}. */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    /** Changes the stack's custom data, making it if it has none: the older editions' {@code getOrCreateTag()}. */
    public static void edit(ItemStack stack, Consumer<CompoundTag> change) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }

    /** Whether two stacks remember the same things - the older editions' comparison of their tags. */
    public static boolean same(ItemStack left, ItemStack right) {
        return java.util.Objects.equals(read(left), read(right));
    }
}
