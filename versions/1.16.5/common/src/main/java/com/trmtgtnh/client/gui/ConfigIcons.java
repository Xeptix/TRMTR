package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

/**
 * What a line of a block or mob list draws beside it on the settings screen (spec CF60): the block it names, or that
 * mob's spawn egg.
 *
 * <p>
 * Taken out of {@link IconListEntry} in 0.9.228, as 26.x took it out in 0.9.227, because this edition now has two
 * settings screens and only one of them is Cloth's. {@code IconListEntry} is a Cloth class by inheritance, and calling
 * a static method on it loads it - which loads Cloth, which need not be there for the screen that exists for when it
 * is not. So the lookup lives here, naming no Cloth type, and both screens ask it. The answers are the ones
 * {@code IconListEntry} gave.
 */
public final class ConfigIcons {

    /** Lines resolve the same for every row and every screen, so each is worked out once. */
    private static final Map<String, ItemStack> BLOCKS = new HashMap<String, ItemStack>();

    private static final Map<String, ItemStack> MOBS = new HashMap<String, ItemStack>();

    private ConfigIcons() {}

    /** One line as a stack to draw, or the empty stack where there is nothing worth drawing. */
    public static ItemStack resolve(ConfigLayout.Icons kind, String raw) {
        if (raw == null || kind == null || kind == ConfigLayout.Icons.NONE) return ItemStack.EMPTY;
        String entry = raw.trim();
        if (entry.isEmpty()) return ItemStack.EMPTY;
        Map<String, ItemStack> cache = kind == ConfigLayout.Icons.MOB ? MOBS : BLOCKS;
        ItemStack known = cache.get(entry);
        if (known != null) return known;
        // Typing a name makes one dead line per keystroke, so this fills with rubbish long before it
        // fills with blocks; nothing here is expensive to work out again.
        if (cache.size() > 256) cache.clear();
        ItemStack made;
        try {
            made = kind == ConfigLayout.Icons.MOB ? egg(entry) : block(entry);
        } catch (Throwable hostile) {
            // A name can reach any mod in the pack. Something in here throwing is a reason to draw
            // nothing, never a reason to take the screen down.
            made = ItemStack.EMPTY;
        }
        cache.put(entry, made);
        return made;
    }

    private static ItemStack block(String entry) {
        String name = ConfigLayout.blockName(entry);
        ResourceLocation at = name == null ? null : ResourceLocation.tryParse(name);
        if (at == null) return ItemStack.EMPTY;
        // getOptional, because the registry answers a name it does not know with air.
        Block block = Registry.BLOCK.getOptional(at)
            .orElse(null);
        if (block == null || block.defaultBlockState()
            .isAir()) return ItemStack.EMPTY;
        Item item = block.asItem();
        // No item form: no icon, which is answer enough.
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    private static ItemStack egg(String entry) {
        for (String name : ConfigLayout.mobNames(entry)) {
            ResourceLocation at = ResourceLocation.tryParse(name);
            if (at == null) continue;
            EntityType<?> type = Registry.ENTITY_TYPE.getOptional(at)
                .orElse(null);
            if (type == null) continue;
            // The first mob the line names decides, as in 1.7.10: a mob with no egg shows its name
            // alone rather than somebody else's egg.
            SpawnEggItem egg = SpawnEggItem.byId(type);
            return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
        }
        return ItemStack.EMPTY;
    }
}
