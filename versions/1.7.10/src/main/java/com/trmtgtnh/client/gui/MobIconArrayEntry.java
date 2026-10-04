package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.client.config.GuiEditArray;
import cpw.mods.fml.client.config.GuiEditArrayEntries;
import cpw.mods.fml.client.config.IConfigElement;

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
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight,
        net.minecraft.client.renderer.Tessellator tessellator, int mouseX, int mouseY, boolean isSelected) {
        super.drawEntry(slotIndex, x, y, listWidth, slotHeight, tessellator, mouseX, mouseY, isSelected);

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
            Class<? extends Entity> type = EntityList.stringToClassMapping.get(name);
            if (type == null) {
                // Names are matched without regard to case where they are used, so the picture
                // should not be the one place that insists on it.
                for (Map.Entry<String, Class<? extends Entity>> known : EntityList.stringToClassMapping.entrySet()) {
                    if (known.getKey()
                        .equalsIgnoreCase(name)) {
                        type = known.getValue();
                        break;
                    }
                }
            }
            if (type == null) return null;

            // The id is only reachable the long way round: the class-to-id map is private, so
            // this walks the public id-to-class one. It runs once per distinct name.
            for (Map.Entry<Integer, Class<? extends Entity>> mapping : EntityList.IDtoClassMapping.entrySet()) {
                if (mapping.getValue() != type) continue;
                int id = mapping.getKey()
                    .intValue();
                if (!EntityList.entityEggs.containsKey(Integer.valueOf(id))) return null;
                return new ItemStack(Items.spawn_egg, 1, id);
            }
            return null;
        } catch (Throwable awkwardEntity) {
            return null;
        }
    }
}
