package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.EntityList;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fml.client.config.GuiEditArray;
import net.minecraftforge.fml.client.config.GuiEditArrayEntries;
import net.minecraftforge.fml.client.config.IConfigElement;

/**
 * A row in Forge's array editor that shows the named mob's spawn egg beside its name.
 *
 * <p>
 * The same idea as {@link BlockIconArrayEntry} and for the same reason: a list of entity names is
 * a list of strings you cannot check by looking at. A spawn egg is the cheapest honest picture of
 * a mob there is — it is an ordinary item, so it goes through the drawing this already does for
 * blocks, with none of the cost or the risk of rendering a live entity inside a config screen
 * that may have no world behind it.
 *
 * <p>
 * Mobs a mod registered without claiming a global id have no egg, and show their name alone.
 * That is the same answer this gives for a name nothing recognises, which is a little
 * unsatisfying and still better than a missing-texture square.
 */
public class MobIconArrayEntry extends GuiEditArrayEntries.StringEntry {

    private static final Map<String, ItemStack> RESOLVED = new HashMap<String, ItemStack>();

    private String lastText;
    private ItemStack icon;

    public MobIconArrayEntry(GuiEditArray owningScreen, GuiEditArrayEntries owningEntryList,
        IConfigElement configElement, Object value) {
        super(owningScreen, owningEntryList, configElement, value);
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY,
        boolean isSelected, float partialTicks) {
        super.drawEntry(slotIndex, x, y, listWidth, slotHeight, mouseX, mouseY, isSelected, partialTicks);

        String text = this.textFieldValue.getText();
        if (lastText == null || !lastText.equals(text)) {
            lastText = text;
            icon = resolve(text);
        }
        if (icon == null) return;
        BlockIconArrayEntry.drawIcon(lastText.trim(), icon, listWidth / 4 - 18, y);
    }

    /** Turns one config line into a spawn egg, or null when there is nothing to show. */
    private static ItemStack resolve(String raw) {
        if (raw == null) return null;
        String entry = raw.trim();
        if (entry.isEmpty()) return null;

        // The line may carry a multiplier after a colon; the name is what identifies the mob.
        int colon = entry.lastIndexOf(':');
        if (colon > 0) entry = entry.substring(0, colon)
            .trim();
        if (entry.isEmpty() || "*".equals(entry)) return null;

        if (RESOLVED.containsKey(entry)) return RESOLVED.get(entry);
        if (RESOLVED.size() > 256) RESOLVED.clear();

        ItemStack stack = build(entry);
        RESOLVED.put(entry, stack);
        return stack;
    }

    private static ItemStack build(String name) {
        try {
            // One walk of the entity registry rather than three map lookups, and it answers
            // both questions at once: the old-style name this mod matches on, and the key
            // an egg is stamped with. Names are matched without regard to case where they
            // are used, so the picture does not insist on it either.
            for (net.minecraftforge.fml.common.registry.EntityEntry known
                : net.minecraftforge.fml.common.registry.ForgeRegistries.ENTITIES) {
                if (known.getName() == null || !known.getName()
                    .equalsIgnoreCase(name)) {
                    continue;
                }
                ResourceLocation key = known.getRegistryName();
                // Mobs registered without an egg show their name alone, which is the same
                // answer this gives for a name nothing recognises.
                if (key == null || !EntityList.ENTITY_EGGS.containsKey(key)) return null;
                // The mob is in the egg's data now rather than in its damage value, which
                // is the whole of what changed about spawn eggs between the two versions.
                ItemStack egg = new ItemStack(Items.SPAWN_EGG);
                ItemMonsterPlacer.applyEntityIdToItemStack(egg, key);
                return egg;
            }
            return null;
        } catch (Throwable awkwardEntity) {
            return null;
        }
    }
}
