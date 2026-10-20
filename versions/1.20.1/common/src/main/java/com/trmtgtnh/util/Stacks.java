package com.trmtgtnh.util;

import com.mojang.serialization.Dynamic;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

/**
 * A stack read from this mod's own saved compounds - the golem's storage, its upgrade and masonry slots - in the shape
 * the game writes now, whichever edition wrote it (0.9.227, carried from 1.16.5's).
 *
 * <p>
 * <strong>Found carrying a worn world from 1.12.2 to 1.16.5</strong> (tools/wear_chain.py): 1.12.2 keeps a stack's
 * wear as {@code Damage} at the stack's root, and 1.13 moved it into the stack's own tag. The game's upgrade moves it
 * for every container it knows the shape of, and the golem's storage is a list only this mod reads, so the upgrade
 * never looked inside it: the golem's iron pickaxe came back unworn. A 1.12.2 world opened here takes the same road,
 * and so does a 1.12.2 golem that came through 1.16.5 without being loaded there. So a compound in the old shape is
 * handed to the game's own fixer here, from 1.12.2's data version to this one's - the same upgrade the game gives its
 * own chests, applied to a stack it could not find. A stack 1.13 to 1.20.1 wrote is already this version's shape and
 * is read as it is.
 */
public final class Stacks {

    /** 1.12.2's data version: the last before the flattening, and the version every 1.12.2 save writes. */
    static final int BEFORE_THE_FLATTENING = 1343;

    private Stacks() {}

    /** The stack in a compound, in either shape. */
    public static ItemStack of(CompoundTag tag) {
        return ItemStack.of(current(tag));
    }

    /**
     * Whether a compound holds a stack in 1.12.2's shape: its wear at the root, where 1.13 and after keep it in the
     * stack's tag and never write one there.
     */
    static boolean beforeTheFlattening(CompoundTag tag) {
        return tag != null && tag.contains("Damage", Tag.TAG_ANY_NUMERIC);
    }

    static CompoundTag current(CompoundTag tag) {
        if (!beforeTheFlattening(tag)) return tag;
        Dynamic<Tag> fixed = DataFixers.getDataFixer()
            .update(
                References.ITEM_STACK,
                new Dynamic<Tag>(NbtOps.INSTANCE, tag),
                BEFORE_THE_FLATTENING,
                // 1.16.5 read this as getWorldVersion(); from 1.18 it is the data version's own number.
                SharedConstants.getCurrentVersion()
                    .getDataVersion()
                    .getVersion());
        return fixed.getValue() instanceof CompoundTag compound ? compound : new CompoundTag();
    }
}
