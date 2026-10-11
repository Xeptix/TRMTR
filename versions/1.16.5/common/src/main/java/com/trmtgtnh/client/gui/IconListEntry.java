package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import me.shedaniel.clothconfig2.gui.entries.AbstractTextFieldListListEntry;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

/**
 * A list of names on the settings screen that draws, beside each line, the block it names - or, for
 * the mob list, that mob's spawn egg.
 *
 * <p>
 * The 1.7.10 screen does this with {@code BlockIconArrayEntry} and {@code MobIconArrayEntry}, rows in
 * Forge's array editor (spec CF60). This screen is Cloth's, decided on 2026-10-10 to stay Cloth's, and
 * Cloth's own string list makes its rows through a constructor argument nothing outside it can
 * reach. So this is Cloth's list with its own rows: the same text field Cloth draws, moved right by an
 * icon's width, and the icon in the gap. Everything else - adding, removing, resetting, the save -
 * is Cloth's, inherited unchanged.
 *
 * <p>
 * A name nothing answers to draws nothing, the same answer 1.7.10 gives for a mob with no egg; and a
 * block whose renderer throws is never asked again, which {@link WearIcons#drawIcon} remembers.
 */
public final class IconListEntry extends AbstractTextFieldListListEntry<String, IconListEntry.Cell, IconListEntry> {

    /** An icon's width and a two-pixel gap: the room the text field gives up. */
    static final int ICON_ROOM = 18;

    /** Lines resolve the same for every row and every screen, so each is worked out once. */
    private static final Map<String, ItemStack> BLOCKS = new HashMap<String, ItemStack>();

    private static final Map<String, ItemStack> MOBS = new HashMap<String, ItemStack>();

    private final ConfigLayout.Icons kind;

    public IconListEntry(Component name, List<String> value, Supplier<Optional<Component[]>> tooltip,
        Consumer<List<String>> save, Supplier<List<String>> defaults, Component resetKey, ConfigLayout.Icons kind) {
        // Collapsed, no restart, a delete button on each line and new lines at the top: what
        // StringListBuilder hands Cloth's own string list, which this screen used until 0.9.223.
        super(name, value, false, tooltip, save, defaults, resetKey, false, true, true, Cell::new);
        this.kind = kind;
        setAddTooltip(new TranslatableComponent("text.cloth-config.list.add"));
        setRemoveTooltip(new TranslatableComponent("text.cloth-config.list.remove"));
    }

    @Override
    public IconListEntry self() {
        return this;
    }

    /** One line: Cloth's text field, and the picture of what it names. */
    public static final class Cell
        extends AbstractTextFieldListListEntry.AbstractTextFieldListCell<String, Cell, IconListEntry> {

        private String lastText;
        private ItemStack icon = ItemStack.EMPTY;

        public Cell(String value, IconListEntry outer) {
            super(value, outer);
        }

        @Override
        protected String substituteDefault(String value) {
            return value == null ? "" : value;
        }

        @Override
        protected boolean isValidText(String text) {
            return true;
        }

        @Override
        public String getValue() {
            return widget.getValue();
        }

        @Override
        public Optional<Component> getError() {
            return Optional.empty();
        }

        @Override
        public void render(PoseStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
            int mouseY, boolean isSelected, float delta) {
            // Cloth's row first, a little narrower, so a renderer that leaves anything behind cannot
            // reach the text of its own row - the order 1.7.10 draws in for the same reason.
            super.render(
                matrices,
                index,
                y,
                x + ICON_ROOM,
                entryWidth - ICON_ROOM,
                entryHeight,
                mouseX,
                mouseY,
                isSelected,
                delta);

            String text = widget.getValue();
            if (lastText == null || !lastText.equals(text)) {
                lastText = text;
                icon = resolve(listListEntry.kind, text);
            }
            if (icon.isEmpty()) return;
            // The text field stands at y + 1 and is eighteen high, so sixteen at y + 2 sits in the
            // middle of it, the height of an inventory slot.
            WearIcons.drawIcon(lastText.trim(), icon, x, y + 2);
        }
    }

    /** One line as a stack to draw, or the empty stack where there is nothing worth drawing. */
    static ItemStack resolve(ConfigLayout.Icons kind, String raw) {
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
