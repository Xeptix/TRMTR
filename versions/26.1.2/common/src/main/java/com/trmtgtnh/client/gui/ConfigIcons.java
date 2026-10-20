package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;
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
 * Taken out of {@link IconListEntry} in 0.9.227, because 26.x has two settings screens and only one of them is
 * Cloth's. {@code IconListEntry} is a Cloth class by inheritance, and calling a static method on it loads it - which
 * loads Cloth, which is not there for the screen that exists for when it is not. So the lookup lives here, naming no
 * Cloth type, and both screens ask it. The answers are 1.21.1's.
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
        Identifier at = name == null ? null : Identifier.tryParse(name);
        if (at == null) return ItemStack.EMPTY;
        // getOptional, because the registry answers a name it does not know with air.
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(at)
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
            Identifier at = Identifier.tryParse(name);
            if (at == null) continue;
            EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(at)
                .orElse(null);
            if (type == null) continue;
            // The first mob the line names decides, as in 1.7.10: a mob with no egg shows its name
            // alone rather than somebody else's egg.
            // 1.21.5 made a spawn egg an item carrying its mob as a component, so the egg is found by
            // that component now rather than from a table of eggs; the same egg 1.21.1 found.
            Optional<net.minecraft.core.Holder<Item>> egg = SpawnEggItem.byId(type);
            return egg.isPresent() ? new ItemStack(egg.get()) : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }
}
