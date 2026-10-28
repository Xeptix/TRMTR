package com.trmtgtnh.client.gui;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import me.shedaniel.clothconfig2.gui.entries.AbstractTextFieldListListEntry;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;

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

        /** The mob list's picture, from the line as it stands (0.9.228). */
        private final RowPicture<MobPictures.Shown> mob = new RowPicture<MobPictures.Shown>(MobPictures::shown);

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
            if (listListEntry.kind == ConfigLayout.Icons.MOB) {
                // The mob list's picture is the own screen's (0.9.228): the mob itself, else its egg, else the mod's
                // generic egg, worked out from the line as it stands so a row added or typed shows it at once.
                MobPictures.draw(matrices, mob.of(text), text, x, y + 2);
                return;
            }
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

    /** One line as a stack to draw, or the empty stack: {@link ConfigIcons}, which names no Cloth type (0.9.228). */
    static ItemStack resolve(ConfigLayout.Icons kind, String raw) {
        return ConfigIcons.resolve(kind, raw);
    }
}
