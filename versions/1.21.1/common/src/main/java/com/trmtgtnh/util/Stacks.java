package com.trmtgtnh.util;

import com.mojang.serialization.Dynamic;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

/**
 * A stack saved into this mod's own data, and read back - including one saved by 1.20.1.
 *
 * <p>
 * 1.20.5 changed how a stack is written: {@code count} and {@code components} where it had {@code Count} and
 * {@code tag}, and it is written with the world's registries now, because a component can name a registry entry. The
 * game carries every stack it knows the place of across that change itself, when a world is opened on the newer
 * version - a player's inventory, a chest, an item frame. <strong>It does not know the places in this mod's own
 * data</strong>: what a Golem of Ways carries, the upgrade fitted to it, the masonry it swallowed. Read with this
 * version's reader alone, every one of those loads as nothing, and a golem upgraded from 1.20.1 would come back empty
 * - material a player handed over, which the mod may not lose (0.9.226's world-upgrade spike).
 *
 * <p>
 * So a stack in the old shape - it has {@code Count} - is put through the game's own fixer for stacks first, from
 * 1.20.1's data version to this one, exactly as the game would have put it through had it known where it was. What
 * this mod remembers in a stack's tag lands in its custom data on the way, which is where {@link StackData} reads it.
 *
 * <p>
 * <strong>And a stack 1.12.2 wrote is older still</strong> (0.9.227): its wear is {@code Damage} at the stack's root,
 * which 1.13 moved into the stack's tag, and its block variants are a number the flattening turned into ids. Fixed from
 * 1.20.1's version, such a stack is taken for one already flattened, and the pickaxe a 1.12.2 golem carried comes back
 * unworn - found carrying a worn world from 1.12.2 to 1.16.5, whose {@code Stacks} answers it the same way. So a stack
 * with {@code Damage} at its root, which nothing from 1.13 on writes there, is fixed from 1.12.2's version instead.
 */
public final class Stacks {

    /** 1.20.1's data version: the last before stacks took components. Every edition before this one writes it. */
    static final int BEFORE_COMPONENTS = 3465;

    /** 1.12.2's data version: the last before the flattening, and the version every 1.12.2 save writes. */
    static final int BEFORE_THE_FLATTENING = 1343;

    private Stacks() {}

    /** A stack written into a compound of its own, or an empty compound for an empty stack. */
    public static CompoundTag save(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        Tag written = stack.save(registries);
        return written instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    /**
     * A stack written into a compound that already holds other things - a slot number beside it. The stack's fields
     * join the compound's, as {@code ItemStack.save(CompoundTag)} did before.
     */
    public static void saveInto(ItemStack stack, HolderLookup.Provider registries, CompoundTag into) {
        if (stack == null || stack.isEmpty() || into == null) return;
        // From 1.20.5 save(registries, tag) hands back a new compound - the given one's fields with the stack's - and
        // leaves the given one as it was. Until the first 1.21.1 game (2026-10-14) its answer was dropped here, so every
        // stack in a golem's storage was written as its slot number alone, and lost on the next load.
        Tag written = stack.save(registries, into);
        if (written instanceof CompoundTag compound && compound != into) into.merge(compound);
    }

    /** The stack in a compound, in either shape; empty where there is none, or where its item is gone. */
    public static ItemStack load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!holdsAStack(tag)) return ItemStack.EMPTY;
        return ItemStack.parseOptional(registries, current(tag));
    }

    /**
     * Whether a compound holds a stack at all. Not one with no item named - an emptied slot, which from 1.20.5 writes
     * nothing beside its slot number - nor one naming air, which is what 1.20.1 wrote for the same slot. Both are empty,
     * as they were before; asking the game's parser about either loads an empty stack as well, but logs an error for
     * every slot.
     */
    static boolean holdsAStack(CompoundTag tag) {
        if (tag == null || !tag.contains("id")) return false;
        return !"minecraft:air".equals(tag.getString("id"));
    }

    /** Whether this compound holds a stack written before 1.20.5. */
    static boolean old(CompoundTag tag) {
        return tag.contains("Count");
    }

    /**
     * Whether this compound holds a stack in 1.12.2's shape: its wear at the root, where 1.13 and after keep it in the
     * stack's tag and never write one there.
     */
    static boolean beforeTheFlattening(CompoundTag tag) {
        return tag.contains("Damage", Tag.TAG_ANY_NUMERIC);
    }

    private static CompoundTag current(CompoundTag tag) {
        if (!old(tag)) return tag;
        Dynamic<Tag> fixed = DataFixers.getDataFixer()
            .update(
                References.ITEM_STACK,
                new Dynamic<Tag>(NbtOps.INSTANCE, tag),
                beforeTheFlattening(tag) ? BEFORE_THE_FLATTENING : BEFORE_COMPONENTS,
                SharedConstants.getCurrentVersion()
                    .getDataVersion()
                    .getVersion());
        return fixed.getValue() instanceof CompoundTag compound ? compound : new CompoundTag();
    }
}
