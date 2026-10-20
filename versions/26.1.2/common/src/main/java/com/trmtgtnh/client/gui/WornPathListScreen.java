package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.util.Translate;

/**
 * One list setting's lines, on the mod's own settings screen: a box per line, a button to take each away, and one to
 * add another, with the block or mob a line names drawn beside it where the setting's lines name them (spec CF60).
 *
 * <p>
 * What Cloth's list entry does on its screen, as a page of its own because a list on the mod's own screen would
 * otherwise have to grow its row: new lines go in at the top, as Cloth puts them, and Done hands the lines back to
 * {@link WornPathScreen}, where they wait for its own Done like every other edit. A line left blank is dropped,
 * since no list this mod reads gives a blank line a meaning. Cancel or Escape goes back without them.
 */
final class WornPathListScreen extends Screen {

    /** An icon's width and a two-pixel gap, as on the Cloth screen's list ({@code IconListEntry.ICON_ROOM}). */
    static final int ICON_ROOM = 18;

    private final WornPathScreen parent;
    private final ConfigFile.Setting setting;
    private final ConfigLayout.Icons icons;

    /** The lines as they stand here, kept across a resize. */
    private final List<String> lines;

    private Lines list;

    WornPathListScreen(WornPathScreen parent, Component name, ConfigFile.Setting setting, ConfigLayout.Icons icons) {
        super(name);
        this.parent = parent;
        this.setting = setting;
        this.icons = icons;
        this.lines = new ArrayList<String>(Arrays.asList(parent.linesOf(setting)));
    }

    @Override
    protected void init() {
        double scrolled = list == null ? 0D : list.scrollAmount();
        list = new Lines(minecraft, width, height - WornPathScreen.LIST_TOP - WornPathScreen.FOOTER,
            WornPathScreen.LIST_TOP);
        for (int at = 0; at < lines.size(); at++) {
            list.add(new Line(at));
        }
        list.setScrollAmount(scrolled);
        addRenderableWidget(list);

        addRenderableWidget(
            Button.builder(Component.literal(Translate.get("trmtgtnh.config.own.add")), pressed -> {
                lines.add(0, "");
                rebuildWidgets();
                list.setScrollAmount(0D);
            })
                .bounds(width / 2 - 75, 26, 150, 20)
                .build());
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL, pressed -> onClose())
                .bounds(width / 2 - 154, height - 27, 150, 20)
                .build());
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, pressed -> keep())
                .bounds(width / 2 + 4, height - 27, 150, 20)
                .build());
    }

    /** Done: the lines that say something, handed back to the settings screen to wait for its own Done. */
    private void keep() {
        List<String> kept = new ArrayList<String>();
        for (String line : lines) {
            if (!line.trim()
                .isEmpty()) kept.add(line);
        }
        parent.editLines(setting, kept.toArray(new String[kept.size()]));
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        WornPathArt.lawn(graphics, width, height);
        graphics.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int stripWidth = Math.min(width - 40, Math.max(120, font.width(title) + 40));
        WornPathArt.pathStrip(graphics, width / 2 - stripWidth / 2, 4, stripWidth, 18);
        graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /** The lines, scrolled. */
    final class Lines extends ContainerObjectSelectionList<Line> {

        Lines(Minecraft minecraft, int width, int height, int top) {
            super(minecraft, width, height, top, WornPathScreen.ROW_HEIGHT);
        }

        void add(Line line) {
            addEntry(line);
        }

        @Override
        public int getRowWidth() {
            return Math.min(WornPathScreen.ROW_WIDTH, width - 40);
        }

        @Override
        protected void extractListBackground(GuiGraphicsExtractor graphics) {
            graphics.fill(getRowLeft() - 6, getY(), getRowRight() + 6, getBottom(), 0x99101010);
        }

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor graphics) {
            graphics.fill(getRowLeft() - 6, getY() - 1, getRowRight() + 6, getY(), 0x80000000);
            graphics.fill(getRowLeft() - 6, getBottom(), getRowRight() + 6, getBottom() + 1, 0x80000000);
        }
    }

    /** One line: what it names, the box it is typed in, and the button that takes it away. */
    final class Line extends ContainerObjectSelectionList.Entry<Line> {

        private final int index;
        private final EditBox box;
        private final Button remove;
        private String lastText;
        private ItemStack icon = ItemStack.EMPTY;

        Line(int index) {
            this.index = index;
            this.box = new EditBox(font, 0, 0, 200, 20, Component.literal(String.valueOf(index + 1)));
            box.setMaxLength(32767);
            box.setValue(lines.get(index));
            box.setResponder(text -> lines.set(this.index, text));
            this.remove = Button.builder(Component.literal("-"), pressed -> {
                lines.remove(this.index);
                rebuildWidgets();
            })
                .size(20, 20)
                .build();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int left = getContentX() + (icons == ConfigLayout.Icons.NONE ? 0 : ICON_ROOM);
            remove.setPosition(getContentRight() - 20, getContentY());
            box.setPosition(left, getContentY());
            box.setWidth(getContentRight() - 24 - left);
            box.extractRenderState(graphics, mouseX, mouseY, a);
            remove.extractRenderState(graphics, mouseX, mouseY, a);

            if (icons == ConfigLayout.Icons.NONE) return;
            String text = box.getValue();
            if (lastText == null || !lastText.equals(text)) {
                lastText = text;
                icon = ConfigIcons.resolve(icons, text);
            }
            if (!icon.isEmpty()) WearIcons.drawIcon(graphics, lastText.trim(), icon, getContentX(), getContentY() + 2);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Arrays.asList(box, remove);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Arrays.asList(box, remove);
        }
    }
}
