package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.util.Translate;

/**
 * The mod's own settings screen, for a 26.x game without Cloth Config (Xep's decision, 0.9.227; see
 * {@link ConfigScreens}).
 *
 * <p>
 * <strong>It shows what Cloth's screen shows, in the same order, and saves the same way</strong>; only how it looks
 * is the mod's own. The headings are {@link ConfigLayout#headings}, one page each, stepped through with the arrows
 * beside the heading as Cloth's tabs are; each page lists its category's settings and then its folders under their
 * own headings, as Cloth's sub-categories; the two buttons - offer the settings to the server, open the wear table -
 * head the first page where there is a world to use them in; every setting is the kind of control its own type asks
 * for, the two counted settings slide (spec CF59), a list of blocks or mobs draws what each line names (spec CF60, in
 * {@link WornPathListScreen}), a setting that needs a restart says so (spec CF61), and the hover is the setting's
 * whole comment with its range. Nothing is written until Done, and Done is the reload the command runs (spec CF49),
 * through {@link ConfigWords#saved}. Cloth's screen saves through a method of its own, and both end in the same
 * deferred reload, {@code ConfigReload.fromGuiDeferred}.
 *
 * <p>
 * Escape or Cancel leaves without saving, and asks first when there is something to lose, as Cloth does. A number
 * that does not read, or reads out of its range, is drawn red and holds Done back until it is put right - Cloth's
 * answer as well.
 *
 * <p>
 * The look is Xep's brief: a lawn with a path worn across it behind everything, the heading laid on a strip of path,
 * the sliders worn from grass to dirt, and the tamper beside the title ({@link WornPathArt}).
 */
public final class WornPathScreen extends Screen {

    /** Where the list of settings starts, below the title and the heading. */
    static final int LIST_TOP = 50;

    /** The room kept below the list for Done and Cancel. */
    static final int FOOTER = 34;

    /** How wide a setting's control is drawn. */
    static final int CONTROL_WIDTH = 150;

    /** How wide each setting's reset button is drawn. */
    static final int RESET_WIDTH = 46;

    /** The widest a row of the list is drawn, however wide the window. */
    static final int ROW_WIDTH = 440;

    /** How high a row is drawn. */
    static final int ROW_HEIGHT = 24;

    /** The red a number that does not read is drawn in. */
    static final int REFUSED = 0xFFFF5555;

    /** How an edit box draws ordinary text. */
    static final int TYPED = 0xFFE0E0E0;

    private final Screen parent;
    private final ConfigFile config;
    private final List<String> headings;

    /** What has been changed and not yet saved: each setting's new value as the file would write it. */
    final Map<ConfigFile.Setting, String> edits = new IdentityHashMap<ConfigFile.Setting, String>();

    /** A list's new lines, kept apart because a list is not one value. */
    final Map<ConfigFile.Setting, String[]> listEdits = new IdentityHashMap<ConfigFile.Setting, String[]>();

    /** The settings whose typed text does not read as their kind, which hold Done back. */
    final Map<ConfigFile.Setting, Boolean> refused = new IdentityHashMap<ConfigFile.Setting, Boolean>();

    private int page;
    private SettingsList list;
    private Button done;

    private WornPathScreen(Screen parent, ConfigFile config) {
        super(Component.literal(Trmt.NAME));
        this.parent = parent;
        this.config = config;
        this.headings = shownHeadings(config);
    }

    /** The whole screen, or null where there is no config to show. */
    static Screen build(Screen parent) {
        ConfigFile config = TrmtConfig.raw();
        if (config == null) return null;
        return new WornPathScreen(parent, config);
    }

    /** The headings this screen pages through: 1.7.10's, in its order, and only those the file holds. */
    private static List<String> shownHeadings(ConfigFile config) {
        List<String> shown = new ArrayList<String>();
        for (String name : ConfigLayout.headings(config.getCategoryNames())) {
            if (config.getCategory(name) != null) shown.add(name);
        }
        return shown;
    }

    @Override
    protected void init() {
        double scrolled = list == null ? 0D : list.scrollAmount();
        list = new SettingsList(minecraft, width, height - LIST_TOP - FOOTER, LIST_TOP);
        fillPage();
        list.setScrollAmount(scrolled);
        addRenderableWidget(list);

        int stripLeft = width / 2 - stripWidth() / 2;
        addRenderableWidget(
            Button.builder(Component.literal("<"), pressed -> turn(-1))
                .bounds(stripLeft - 24, 26, 20, 20)
                .build());
        addRenderableWidget(
            Button.builder(Component.literal(">"), pressed -> turn(1))
                .bounds(stripLeft + stripWidth() + 4, 26, 20, 20)
                .build());

        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL, pressed -> onClose())
                .bounds(width / 2 - 154, height - 27, 150, 20)
                .build());
        done = addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, pressed -> save())
                .bounds(width / 2 + 4, height - 27, 150, 20)
                .build());
        refreshDone();
    }

    private int stripWidth() {
        return Math.min(220, Math.max(100, width - 120));
    }

    /** One heading on or back, wrapping round at either end as Cloth's tabs do not need to. */
    private void turn(int by) {
        if (headings.isEmpty()) return;
        page = Math.floorMod(page + by, headings.size());
        rebuildWidgets();
        list.setScrollAmount(0D);
    }

    /** The page being shown: its heading's settings, then each of its folders under a heading of its own. */
    private void fillPage() {
        if (headings.isEmpty()) return;
        String top = headings.get(page);
        ConfigFile.Category category = config.getCategory(top);
        if (category == null) return;

        if (page == 0 && minecraft != null && minecraft.level != null) {
            // The two things this screen does besides editing, where there is a world to do them in.
            list.add(new ActionRow(
                Component.literal(Translate.get("trmtgtnh.config.push")),
                () -> TrmtNetwork.pushConfig(ConfigWords.asLines())));
            list.add(new ActionRow(
                Component.literal(Translate.get("trmtgtnh.weartable.button")),
                // Over this screen, so Back comes back to the edits not yet saved, as the Cloth screen's button does.
                () -> minecraft.gui.setScreen(new GuiWearTable(this))));
        }

        for (ConfigFile.Setting setting : category.getValues()
            .values()) {
            if (setting != null) list.add(new SettingRow(top, setting));
        }

        // The folders, in the order the file holds them.
        Map<String, List<ConfigFile.Setting>> folders = new LinkedHashMap<String, List<ConfigFile.Setting>>();
        for (String name : config.getCategoryNames()) {
            if (!name.startsWith(top + ConfigFile.CATEGORY_SPLITTER)) continue;
            ConfigFile.Category inside = config.getCategory(name);
            if (inside == null) continue;
            List<ConfigFile.Setting> held = new ArrayList<ConfigFile.Setting>();
            for (ConfigFile.Setting setting : inside.getValues()
                .values()) {
                if (setting != null) held.add(setting);
            }
            if (!held.isEmpty()) folders.put(name.substring(top.length() + 1), held);
        }
        for (Map.Entry<String, List<ConfigFile.Setting>> folder : folders.entrySet()) {
            list.add(new FolderRow(Component.literal(ConfigWords.title(folder.getKey()))));
            String inside = top + ConfigFile.CATEGORY_SPLITTER + folder.getKey();
            for (ConfigFile.Setting setting : folder.getValue()) {
                list.add(new SettingRow(inside, setting));
            }
        }
    }

    // ------------------------------------------------------------------
    // What is being edited
    // ------------------------------------------------------------------

    /** A setting's value as this screen holds it: the edit where there is one, the file's otherwise. */
    String valueOf(ConfigFile.Setting setting) {
        String edited = edits.get(setting);
        return edited != null ? edited : setting.getString();
    }

    /** A list's lines as this screen holds them. */
    String[] linesOf(ConfigFile.Setting setting) {
        String[] edited = listEdits.get(setting);
        return edited != null ? edited.clone() : setting.getStringList();
    }

    void edit(ConfigFile.Setting setting, String value) {
        if (value.equals(setting.getString())) edits.remove(setting);
        else edits.put(setting, value);
    }

    void editLines(ConfigFile.Setting setting, String[] lines) {
        if (Arrays.equals(lines, setting.getStringList())) listEdits.remove(setting);
        else listEdits.put(setting, lines.clone());
    }

    void refuse(ConfigFile.Setting setting, boolean bad) {
        if (bad) refused.put(setting, Boolean.TRUE);
        else refused.remove(setting);
        refreshDone();
    }

    private void refreshDone() {
        if (done != null) done.active = refused.isEmpty();
    }

    /** Whether there is anything Cancel would throw away. */
    boolean changed() {
        return !edits.isEmpty() || !listEdits.isEmpty();
    }

    /**
     * Done: every edit written into the settings, then the save and reload Cloth's screen runs, then back.
     *
     * <p>
     * A setting that needs a restart and was changed is said once, after the save, as 1.7.10's screen says it.
     */
    private void save() {
        if (!refused.isEmpty()) return;
        boolean restart = false;
        for (Map.Entry<ConfigFile.Setting, String> one : edits.entrySet()) {
            write(one.getKey(), one.getValue());
            restart |= one.getKey()
                .requiresMcRestart();
        }
        for (Map.Entry<ConfigFile.Setting, String[]> one : listEdits.entrySet()) {
            one.getKey()
                .set(one.getValue());
            restart |= one.getKey()
                .requiresMcRestart();
        }
        boolean any = changed();
        edits.clear();
        listEdits.clear();
        if (any) ConfigWords.saved();
        if (restart) {
            minecraft.gui.setScreen(
                new AlertScreen(
                    () -> minecraft.gui.setScreen(parent),
                    Component.literal(Translate.get("trmtgtnh.config.own.restart")),
                    Component.literal(Translate.get("trmtgtnh.config.own.restart.note"))));
        } else {
            minecraft.gui.setScreen(parent);
        }
    }

    /** One value written as its own kind, the way the Cloth screen's entries write it. */
    private static void write(ConfigFile.Setting setting, String value) {
        switch (setting.getType()) {
            case BOOLEAN:
                setting.set(Boolean.parseBoolean(value));
                return;
            case INTEGER:
                setting.set((int) ConfigWords.number(value, 0D));
                return;
            case DOUBLE:
                setting.set(String.valueOf(ConfigWords.number(value, 0D)));
                return;
            default:
                setting.set(value);
        }
    }

    @Override
    public void onClose() {
        if (!changed()) {
            minecraft.gui.setScreen(parent);
            return;
        }
        minecraft.gui.setScreen(new ConfirmScreen(leave -> minecraft.gui.setScreen(leave ? parent : this),
            Component.literal(Translate.get("trmtgtnh.config.own.discard")),
            Component.literal(Translate.get("trmtgtnh.config.own.discard.note"))));
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        WornPathArt.lawn(graphics, width, height);
        // Dimmed, so the words read over the lawn, and a little more under the title and the buttons.
        graphics.fill(0, 0, width, height, 0x66000000);
        graphics.fill(0, 0, width, LIST_TOP - 2, 0x40000000);
        graphics.fill(0, height - FOOTER + 2, width, height, 0x40000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The title, with the tamper beside it.
        int named = font.width(title);
        int left = width / 2 - (16 + 4 + named) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, WornPathArt.ICON, left, 5, 0F, 0F, 16, 16, 16, 16, 16, 16);
        graphics.text(font, title, left + 20, 9, 0xFFFFFFFF, true);

        // The heading, laid on a strip of path, with where it is among them.
        int stripLeft = width / 2 - stripWidth() / 2;
        WornPathArt.pathStrip(graphics, stripLeft, 26, stripWidth(), 20);
        if (!headings.isEmpty()) {
            String heading = ConfigWords.title(ConfigWords.leaf(headings.get(page)));
            String where = Translate.get("trmtgtnh.config.own.page", page + 1, headings.size());
            graphics.centeredText(font, Component.literal(heading), width / 2, 28, 0xFFFFFFFF);
            graphics.centeredText(font, Component.literal(where), width / 2, 37, 0xFFC8B89A);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    /** The settings of one page, scrolled. */
    final class SettingsList extends ContainerObjectSelectionList<Row> {

        SettingsList(Minecraft minecraft, int width, int height, int top) {
            super(minecraft, width, height, top, ROW_HEIGHT);
        }

        void add(Row row) {
            addEntry(row, row.height());
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, width - 40);
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

    /** One row of the list. */
    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {

        int height() {
            return ROW_HEIGHT;
        }
    }

    /** A folder's heading, laid on a strip of path as the page's own heading is. */
    final class FolderRow extends Row {

        private final Component name;

        FolderRow(Component name) {
            this.name = name;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int stripWidth = Math.min(getContentWidth(), font.width(name) + 40);
            int left = getContentXMiddle() - stripWidth / 2;
            WornPathArt.pathStrip(graphics, left, getContentY() + 2, stripWidth, 16);
            graphics.centeredText(font, name, getContentXMiddle(), getContentY() + 6, 0xFFFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }

    /** One of the two buttons that head the first page. */
    final class ActionRow extends Row {

        private final Button button;

        ActionRow(Component name, Runnable action) {
            this.button = Button.builder(name, pressed -> action.run())
                .size(CONTROL_WIDTH + 4 + RESET_WIDTH, 20)
                .build();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            button.setPosition(getContentRight() - button.getWidth(), getContentY());
            button.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.singletonList(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.singletonList(button);
        }
    }

    /** One setting: its name and hover on the left, its control and a reset on the right. */
    final class SettingRow extends Row {

        private final String category;
        private final ConfigFile.Setting setting;
        private final Component name;
        private final List<Component> note;
        private final AbstractWidget control;
        private final Button reset;

        SettingRow(String category, ConfigFile.Setting setting) {
            this.category = category;
            this.setting = setting;
            this.name = setting.requiresMcRestart() ? ConfigWords.named(setting)
                .copy()
                .append(Component.literal(" *").withStyle(ChatFormatting.GOLD)) : ConfigWords.named(setting);
            this.note = hover(setting);
            this.control = controlFor(category, setting);
            this.reset = Button.builder(Component.translatable("controls.reset"), pressed -> resetToDefault())
                .size(RESET_WIDTH, 20)
                .build();
        }

        private void resetToDefault() {
            if (setting.isList()) editLines(setting, setting.getDefaultList());
            else edit(setting, setting.getDefault());
            refuse(setting, false);
            rebuildWidgets();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int right = getContentRight();
            reset.setPosition(right - RESET_WIDTH, getContentY());
            control.setPosition(right - RESET_WIDTH - 4 - CONTROL_WIDTH, getContentY());
            reset.active = !atDefault();

            int room = control.getX() - getContentX() - 6;
            String shown = name.getString();
            boolean cut = font.width(shown) > room;
            Component label = cut ? Component.literal(font.plainSubstrByWidth(shown, room - font.width("...")) + "...")
                : name;
            int edited = changedHere() ? 0xFFFFE08A : 0xFFFFFFFF;
            graphics.text(font, label, getContentX(), getContentY() + 6, edited, true);

            control.extractRenderState(graphics, mouseX, mouseY, a);
            reset.extractRenderState(graphics, mouseX, mouseY, a);

            boolean onTheName = hovered && mouseX >= getContentX() && mouseX < getContentX() + room;
            if (onTheName && !note.isEmpty()) {
                graphics.setTooltipForNextFrame(font, note, Optional.empty(), mouseX, mouseY);
            }
        }

        private boolean changedHere() {
            return setting.isList() ? listEdits.containsKey(setting) : edits.containsKey(setting);
        }

        private boolean atDefault() {
            if (setting.isList()) return Arrays.equals(linesOf(setting), setting.getDefaultList());
            return valueOf(setting).equals(setting.getDefault());
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Arrays.asList(control, reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Arrays.asList(control, reset);
        }
    }

    /** The hover: the setting's name, its whole comment with its range, and the restart note where it needs one. */
    private List<Component> hover(ConfigFile.Setting setting) {
        List<Component> out = new ArrayList<Component>();
        out.add(ConfigWords.named(setting)
            .copy()
            .withStyle(ChatFormatting.YELLOW));
        for (String line : ConfigWords.noteLines(setting)) {
            out.add(Component.literal(line));
        }
        if (setting.requiresMcRestart()) {
            out.add(Component.empty());
            out.add(Component.literal(Translate.get("trmtgtnh.config.own.restartMark"))
                .withStyle(ChatFormatting.GOLD));
        }
        return out.size() == 1 ? Collections.<Component>emptyList() : out;
    }

    /** The control a setting's own type asks for, as the Cloth screen chooses its entry. */
    private AbstractWidget controlFor(String category, ConfigFile.Setting setting) {
        Component name = ConfigWords.named(setting);

        if (setting.isList()) {
            // A list opens an editor of its own lines, which draws what each names where the setting's lines name
            // blocks or mobs (spec CF60).
            final ConfigLayout.Icons icons = ConfigLayout.iconsFor(category, setting.getName());
            return Button
                .builder(
                    Component.literal(Translate.get("trmtgtnh.config.own.editList", linesOf(setting).length)),
                    pressed -> minecraft.gui.setScreen(new WornPathListScreen(this, name, setting, icons)))
                .size(CONTROL_WIDTH, 20)
                .build();
        }

        switch (setting.getType()) {
            case BOOLEAN:
                return CycleButton.onOffBuilder(Boolean.parseBoolean(valueOf(setting)))
                    .displayOnlyValue()
                    .create(0, 0, CONTROL_WIDTH, 20, name, (button, value) -> edit(setting, String.valueOf(value)));
            case INTEGER:
                if (ConfigLayout.slides(category, setting.getName()) && setting.isBounded()) {
                    // The two counted settings slide (spec CF59), on a bar the handle wears to dirt.
                    int min = (int) setting.getMin();
                    int max = (int) setting.getMax();
                    int at = ConfigWords.clamp((int) ConfigWords.number(valueOf(setting), min), min, max);
                    return new WearSlider(CONTROL_WIDTH, 20, min, max, at, value -> edit(setting, String.valueOf(value)));
                }
                return typed(setting, name);
            case DOUBLE:
                return typed(setting, name);
            default: {
                // A setting that names its own valid values gets a chooser, not a text box - and a value already in
                // the file that is not on the list stays on it, as on the Cloth screen, rather than being dropped the
                // moment the screen opens.
                String[] choices = setting.getValidValues();
                if (choices != null && choices.length > 0) {
                    String current = valueOf(setting);
                    List<String> offered = new ArrayList<String>(Arrays.asList(choices));
                    if (!offered.contains(current)) offered.add(current);
                    return CycleButton.<String>builder(Component::literal, current)
                        .withValues(offered)
                        .displayOnlyValue()
                        .create(0, 0, CONTROL_WIDTH, 20, name, (button, value) -> edit(setting, value));
                }
                return typed(setting, name);
            }
        }
    }

    /** A box to type a value in: a number is drawn red, and holds Done back, while it does not read. */
    private EditBox typed(ConfigFile.Setting setting, Component name) {
        EditBox box = new EditBox(font, 0, 0, CONTROL_WIDTH, 20, name);
        box.setMaxLength(32767);
        box.setValue(valueOf(setting));
        // A box laid out again holds the last value that read, so whatever refusal its text had is over.
        boolean reading = ConfigWords.reads(setting, box.getValue());
        box.setTextColor(reading ? TYPED : REFUSED);
        refuse(setting, !reading);
        box.setResponder(text -> {
            boolean good = ConfigWords.reads(setting, text);
            box.setTextColor(good ? TYPED : REFUSED);
            refuse(setting, !good);
            if (good) edit(setting, ConfigWords.normal(setting, text));
        });
        return box;
    }
}
