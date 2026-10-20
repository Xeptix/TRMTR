package com.trmtgtnh.item;

import java.util.function.Consumer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which creative tab each of this mod's items is in, and what it puts there.
 *
 * <p>
 * From 1.19.3 an item no longer names its tab or fills it: each loader is asked, as a tab is built, what to add to it
 * (Forge's BuildCreativeModeTabContentsEvent, Fabric's ItemGroupEvents), and both ask this. 1.16.5 had a tools tab and
 * a misc tab; 1.20's tabs are finer, so each item goes where its own kind goes in 1.20: the tampers and the tools with
 * the tools and utilities, the golem's egg with the spawn eggs, a draught with the drinks, an upgrade with the
 * ingredients - a placement in a menu, not a change to the item. Each item's stacks are the ones 1.16.5's
 * fillItemCategory gave: one tamper of each grade the pack can supply, the magic tamper at its full reach.
 */
public final class CreativeTabs {

    private CreativeTabs() {}

    /**
     * The vanilla tabs this mod's items go in, named here because 1.20.1 keeps the tabs' own keys private: each loader
     * maps these to the keys it can reach (Forge opens CreativeModeTabs; Fabric names them in its item group API).
     */
    public enum Tab {
        TOOLS_AND_UTILITIES,
        SPAWN_EGGS,
        FOOD_AND_DRINKS,
        INGREDIENTS
    }

    /** Every stack of this mod's that belongs in this tab, in ModItems' order. */
    public static void into(Tab tab, Consumer<ItemStack> out) {
        for (Item item : ModItems.all()) {
            if (tabOf(item) != tab) continue;
            if (item instanceof Stacks) ((Stacks) item).creativeStacks(out);
            else out.accept(new ItemStack(item));
        }
    }

    /** The tab an item of this mod's goes in. */
    public static Tab tabOf(Item item) {
        if (item instanceof ItemGolemEgg) return Tab.SPAWN_EGGS;
        if (item instanceof ItemDraught) return Tab.FOOD_AND_DRINKS;
        if (item instanceof ItemGolemUpgrade) return Tab.INGREDIENTS;
        return Tab.TOOLS_AND_UTILITIES;
    }

    /** An item that puts more than its plain stack in its tab. */
    public interface Stacks {

        void creativeStacks(Consumer<ItemStack> out);
    }
}
