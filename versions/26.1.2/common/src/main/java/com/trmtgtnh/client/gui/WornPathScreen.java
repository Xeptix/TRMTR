package com.trmtgtnh.client.gui;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.ColorMath;
import com.trmtgtnh.client.settings.FocusRing;
import com.trmtgtnh.client.settings.HoverText;
import com.trmtgtnh.client.settings.IconTabs;
import com.trmtgtnh.client.settings.ListEdits;
import com.trmtgtnh.client.settings.NumberWords;
import com.trmtgtnh.client.settings.SettingsGeometry;
import com.trmtgtnh.client.settings.SettingsGeometry.Frame;
import com.trmtgtnh.client.settings.SettingsGeometry.Rect;
import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.client.settings.Slider;
import com.trmtgtnh.client.settings.TabStrip;
import com.trmtgtnh.command.CommandTrmt;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The mod's own settings screen (0.9.228; Xep, 2026-10-21: "I just don't want this to be a downgrade from forge's mod
 * options / Cloth. It should feel like an upgrade"), drawn to docs/goals/0.9.228-settings-spec.md, Part 1.
 *
 * <p>
 * What it shows and does is decided in {@link SettingsModel}, which a test can ask; this draws it with this edition's
 * screens and hears the keys and the pointer. Top to bottom, each band where {@code SettingsGeometry} puts it: the
 * header - a lawn band, the wordmark, the help button with the keyboard map, and the header's icon buttons (the toggle
 * to the other screen, the Wear Table, Push to server; M14, M15) - then the tabs (M1), Surfaces' family icons, the
 * search field and "Changed only" (M2, C5), the banner while a value is refused (M6), the list on the pack's own dark
 * dirt (M12), and the footer: Undo all, Reset tab..., Cancel and Save (M8, M9).
 *
 * <p>
 * Each row is its setting's plain title and summary (M21), a gold mark and bar when changed this visit, its control by
 * its kind (1.8: On and Off, a dropdown, a slider and its field, a field, a color swatch, a list in place or on its
 * page), an undo arrow while changed and a reset; a number shows its range while focused and its reason while refused.
 * The hover is short and fits the screen; Shift shows the whole description, paged (M19). Tab walks every control
 * (M11). Nothing is written until Save; Escape or Cancel asks first when there is something to lose.
 *
 * <p>
 * It edits the mod's own settings ({@link ConfigFile}), as the 1.12.2 edition's does. The 1.7.10 edition's screen,
 * line for line, in this game's names: the game hears a key and the character it types apart ({@code keyPressed} and
 * {@code charTyped}), where 1.7.10 hears both in one, so each is handed to the one {@link #keyTyped} - a key with no
 * character, a character with no key. The screen is drawn in order, nearest last ({@link SettingsCanvas}).
 */
public class WornPathScreen extends Screen {

    /** The list page's and the picker's measures. */
    static final int LIST_TOP = 50;

    static final int FOOTER = 34;

    static final int CONTROL_WIDTH = 150;

    static final int RESET_WIDTH = 46;

    static final int ROW_WIDTH = 440;

    static final int ROW_HEIGHT = 24;

    /** The red a refused value is drawn in, and the color typed text is drawn in, on the list page. */
    static final int REFUSED = 0xFF5555;

    static final int TYPED = 0xE0E0E0;

    /** No key: what a character typed carries in {@link #keyTyped}, since the game hears the two apart. */
    static final int NO_KEY = -1;

    /** How long the pointer rests on a control before its hover shows (spec 1.6). */
    private static final long CONTROL_HOVER_MILLIS = 500L;

    /** The rows the wheel moves a notch. */
    private static final int WHEEL_ROWS = 3;

    // Kept for the game session, not in the file (spec M1.6, M1.7, S1.4).
    private static String lastTab;

    private static final Map<String, String> LAST_PAGE = new HashMap<String, String>();

    private static final Map<String, Integer> SCROLLS = new HashMap<String, Integer>();

    private static final Set<String> OPEN_LISTS = new HashSet<String>();

    private static boolean advancedOpen;

    private final Screen parent;

    final SettingsModel model;

    private SettingsCanvas canvas;

    private Frame frame;

    private TabStrip strip;

    private final IconTabs icons = new IconTabs();

    private String tab;

    private String page;

    private EditBox search;

    private String searchText = "";

    private boolean changedOnly;

    /** The rows shown, laid out from the panel's top; a search's results or "Changed only" stand in for the page. */
    private final List<View> views = new ArrayList<View>();

    private int scroll;

    private int contentHeight;

    /** The focused control, by id: {@code c:<setting>}, {@code u:}, {@code r:}, {@code f:}, a header or footer name. */
    private String focus = "search";

    private final Map<String, EditBox> fields = new HashMap<String, EditBox>();

    /** A refused value's text, kept while the screen lays out again, so the red stays where it was typed. */
    private final Map<String, String> typed = new HashMap<String, String>();

    private final Map<String, Slider> sliders = new HashMap<String, Slider>();

    private final Map<String, ChooserButton> choosers = new HashMap<String, ChooserButton>();

    private final Map<String, ListRows> inline = new HashMap<String, ListRows>();

    private RememberedRows remembered;

    private Slider dragging;

    private String draggingId;

    private boolean draggingThumb;

    /** The first line of a Shift-held hover, which the wheel pages (spec M19.3). */
    private int hoverLine;

    private Object hoverWhat;

    private long hoverSince;

    // A photograph's state, handed in through the builder hooks (spec Part 4); null and false in a game.
    private String hoverShown;

    private boolean shiftShown;

    private boolean toggleHoverShown;

    private String sliderHeld;

    private boolean askToLeave;

    private String dropdownShown;

    /**
     * The setting a photograph is about, scrolled into the middle of the panel as a hover's is (spec Part 4): the last
     * one an edit, focus, slider, fold, dropdown or file text was handed in for. A photograph of a setting below the
     * panel's first rows otherwise shows the top of its tab and not the state it was taken for.
     */
    private String shownSetting;

    private String dropdownHighlight;

    /**
     * The whole screen, with a screen to go back to; with no config loaded it shows nothing to edit. Public, because
     * each loader's mod list, the harness and {@link ConfigScreens} build it.
     */
    public WornPathScreen(Screen parent) {
        super(Component.literal(Trmt.NAME));
        this.parent = parent;
        ConfigFile config = TrmtConfig.raw();
        this.model = new SettingsModel(config, new SettingsModel.Words() {

            @Override
            public String words(String key, Object... args) {
                return I18n.get(key, args);
            }

            @Override
            public boolean has(String key) {
                return net.minecraft.locale.Language.getInstance()
                    .has(key);
            }
        });
        this.model.otherScreen(ConfigScreens.otherExists());
        List<String> tabs = model.tabs();
        this.tab = lastTab != null && tabs.contains(lastTab) ? lastTab : tabs.isEmpty() ? null : tabs.get(0);
        this.page = pageOf(tab);
    }

    private String pageOf(String tabId) {
        if (tabId == null) return null;
        List<String> pages = model.pages(tabId);
        String kept = LAST_PAGE.get(tabId);
        return kept != null && pages.contains(kept) ? kept : pages.isEmpty() ? tabId : pages.get(0);
    }

    // ------------------------------------------------------------------
    // The photograph's hooks (spec Part 4): each returns the screen, and nothing reaches the file
    // ------------------------------------------------------------------

    /**
     * The screen as the rig photographs it: opened on a tab, or a Surfaces page ({@code families.grass}), and where
     * {@code hoverOf} names one of its settings, with that setting's hover shown as if the pointer rested on its name.
     * Null where there is no such tab or page.
     */
    public static WornPathScreen forPhotograph(Screen parent, String heading, String hoverOf) {
        WornPathScreen screen = new WornPathScreen(parent);
        if (!screen.open(heading)) return null;
        screen.hoverShown = hoverOf;
        return screen;
    }

    /** Opens a tab, or the tab a page is on and that page; false where there is neither. */
    private boolean open(String heading) {
        for (String each : model.tabs()) {
            if (each.equals(heading) || model.pages(each)
                .contains(heading)) {
                tab = each;
                page = model.pages(each)
                    .contains(heading) ? heading : pageOf(each);
                return true;
            }
        }
        return false;
    }

    /** A list setting's own page, as the rig photographs it: the list {@code name} of {@code heading}, or null. */
    public static WornPathListScreen listForPhotograph(Screen parent, String heading, String name) {
        return listForPhotograph(parent, heading, name, null);
    }

    /**
     * A list setting's own page holding the lines given, as an edit not yet saved, so the photograph shows rows a
     * fresh file has none of. Nothing reaches the file.
     */
    public static WornPathListScreen listForPhotograph(Screen parent, String heading, String name,
        String[] shownLines) {
        WornPathScreen screen = new WornPathScreen(parent);
        ConfigFile config = TrmtConfig.raw();
        if (config == null || !config.hasCategory(heading)) return null;
        ConfigFile.Setting setting = config.getCategory(heading)
            .get(name);
        if (setting == null || !setting.isList()) return null;
        if (shownLines != null) screen.editLines(setting, shownLines);
        SettingsModel.Setting shown = screen.model.setting(heading + "." + name);
        return shown == null ? null : new WornPathListScreen(screen, shown);
    }

    /** A value edited, as if typed (spec Part 4, {@code withEdit}): a value that does not read is refused, red. */
    public WornPathScreen withEdit(String category, String key, String value) {
        SettingsModel.Setting setting = model.setting(category + "." + key);
        if (setting != null) {
            if (model.type(setting, value) != null) typed.put(setting.id, value);
            else typed.remove(setting.id);
        }
        return showing(category, key);
    }

    /** The setting a photograph is about, kept to be scrolled into view as the screen lays out. */
    private WornPathScreen showing(String category, String key) {
        shownSetting = category + "." + key;
        return this;
    }

    public WornPathScreen withSearch(String query) {
        searchText = query == null ? "" : query;
        return this;
    }

    public WornPathScreen withFocus(String category, String key) {
        focus = "c:" + category + "." + key;
        return showing(category, key);
    }

    /** A slider at a value, its handle held as if dragged. */
    public WornPathScreen withSliderHeld(String category, String key, String value) {
        withEdit(category, key, value);
        sliderHeld = category + "." + key;
        return showing(category, key);
    }

    /** The question asked on leaving with an edit unsaved. */
    public WornPathScreen askingToLeave() {
        askToLeave = true;
        return this;
    }

    public WornPathScreen withHoverOnToggle() {
        toggleHoverShown = true;
        return this;
    }

    public WornPathScreen withInlineOpen(String category, String key) {
        OPEN_LISTS.add(category + "." + key);
        open(category);
        return showing(category, key);
    }

    /** A server's values standing over this client's (spec S4): handed in, nothing reaches the rules. */
    public WornPathScreen withLocked(Map<String, String> values) {
        model.locked(values);
        return this;
    }

    public WornPathScreen withChangedOnly() {
        changedOnly = true;
        return this;
    }

    /** A chooser's dropdown open, the highlight on {@code value}. */
    public WornPathScreen withDropdownOpen(String category, String key, String value) {
        open(category);
        dropdownShown = category + "." + key;
        dropdownHighlight = value;
        return showing(category, key);
    }

    public WornPathScreen withAdvancedOpen() {
        advancedOpen = true;
        return this;
    }

    public WornPathScreen withShiftHeld() {
        shiftShown = true;
        return this;
    }

    /**
     * A value as if read from the file (spec Part 4, {@code withFileText}): shown as the screen shows what it reads.
     */
    public WornPathScreen withFileText(String category, String key, String text) {
        SettingsModel.Setting setting = model.setting(category + "." + key);
        if (setting != null) {
            model.edits()
                .set(setting.id, setting.kind != 0 ? NumberWords.shown(text, setting.kind) : text);
        }
        return showing(category, key);
    }

    /** A list's lines, as an edit (the list page hands its lines back through here). */
    void editLines(ConfigFile.Setting setting, String[] lines) {
        for (SettingsModel.Setting each : model.settings()) {
            if (each.property == setting) {
                model.edits()
                    .set(each.id, SettingsModel.join(lines));
                return;
            }
        }
    }

    /** A list's lines as this screen holds them. */
    String[] linesOf(ConfigFile.Setting setting) {
        for (SettingsModel.Setting each : model.settings()) {
            if (each.property == setting) {
                List<String> lines = SettingsModel.lines(model.value(each));
                return lines.toArray(new String[lines.size()]);
            }
        }
        return setting.getStringList();
    }

    // ------------------------------------------------------------------
    // Laying out
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        // Laid out from nothing every time: a resize, a return from a page and a reset all come back through here.
        clearWidgets();
        canvas = new SettingsCanvas(minecraft, width, height);
        model.world(minecraft != null && minecraft.level != null);
        List<String> labels = new ArrayList<String>();
        for (String each : model.tabs()) labels.add(model.tabLabel(each));
        strip = new TabStrip(labels);
        fields.clear();
        sliders.clear();
        choosers.clear();
        inline.clear();
        // Made again at the panel's new width (spec M16.2): its rows are laid from the left and width it is given.
        remembered = null;
        relayout();
        int at = model.tabs()
            .indexOf(tab);
        if (at >= 0) strip.show(at);
        Integer kept = SCROLLS.get(scrollKey());
        scroll = kept == null ? 0 : kept;
        if (hoverShown != null || shownSetting != null) {
            // A photograph's hover: its setting scrolled into the middle of the panel, where its hover has room; and
            // likewise the setting any other photograph is about.
            View shown = viewOf(hoverShown != null ? hoverShown : shownSetting);
            if (shown != null) scroll = shown.top - frame.rows.height() / 2;
        }
        clampScroll();
        if (askToLeave) {
            askToLeave = false;
            leave();
        }
    }

    /** The search field, the frame and the rows, laid out again from what the model holds now. */
    private void relayout() {
        boolean surfaces = TrmtConfig.CATEGORY_SURFACES.equals(tab) && !searching();
        frame = SettingsGeometry.of(width, height, surfaces, model.banner() != null);
        strip.layout(canvas, frame.tabs);
        if (frame.subTabs != null) icons.layout(
            model.pages(tab)
                .size(),
            frame.subTabs);
        String held = search == null ? searchText : search.getValue();
        boolean searchFocused = search != null && search.isFocused();
        search = new EditBox(
            font,
            frame.search.x0 + 18,
            frame.search.y0 + (frame.search.height() - 8) / 2,
            frame.search.width() - 22 - countWidth(),
            10,
            Component.literal(""));
        search.setBordered(false);
        search.setMaxLength(100);
        search.setValue(held);
        search.setTextColor(Canvas.WHITE);
        search.setFocused(searchFocused || "search".equals(focus));
        searchText = held;
        layoutRows();
    }

    private int countWidth() {
        return searching() ? 70 : 0;
    }

    private boolean searching() {
        String text = search == null ? searchText : search.getValue();
        return !text.trim()
            .isEmpty() || changedOnly;
    }

    /** The key a scroll position is kept under: the page, or the search. */
    private String scrollKey() {
        return searching() ? "?" : page;
    }

    /** The rows of the page, or of the search, each placed under the one before. */
    private void layoutRows() {
        views.clear();
        List<SettingsModel.Row> rows = model.results(searchText, changedOnly);
        if (rows == null) rows = model.rows(page, advancedOpen);
        int left = frame.rows.x0;
        int r = SettingsGeometry.rowWidth(frame);
        int y = bandPattern() != null ? BAND : 0;
        for (SettingsModel.Row row : rows) {
            View view = new View(row);
            view.top = y;
            view.height = heightOf(view, left, r);
            views.add(view);
            y += view.height;
        }
        contentHeight = y;
        clampScroll();
    }

    private int heightOf(View view, int left, int r) {
        SettingsModel.Row row = view.row;
        switch (row.type) {
            case FOLDER:
            case ADVANCED:
                return SettingsGeometry.FOLDER_ROW;
            case NOTE:
                return Math.max(
                    1,
                    HoverText.wrap(canvas, Collections.singletonList(row.text), r - 12)
                        .size())
                    * 10 + 8;
            case REMEMBERED:
                if (remembered == null) remembered = new RememberedRows(model.edits(), left + 12, r - 12);
                return remembered.height() + 14;
            case EMPTY:
                return Math.max(30, frame.rows.height());
            default:
                break;
        }
        SettingsModel.Setting setting = row.setting;
        int height = frame.compact ? SettingsGeometry.SETTING_ROW_COMPACT : SettingsGeometry.SETTING_ROW;
        if (row.kicker != null) height += SettingsGeometry.KICKER;
        if (underLine(setting) != null) height += SettingsGeometry.UNDER_LINE;
        if (setting.editor == ConfigLayout.Editor.LIST && OPEN_LISTS.contains(setting.id) && foldable(setting)) {
            ListRows rows = inline.get(setting.id);
            if (rows == null) {
                rows = new ListRows(
                    ConfigLayout.listKind(setting.category, setting.key),
                    SettingsModel.lines(model.value(setting)),
                    model.edits(),
                    setting.id,
                    left + 12,
                    r - 12);
                // A line added, removed or pasted in place moves every row under the list (spec S1).
                rows.onResize(this::layoutRows);
                inline.put(setting.id, rows);
            }
            height += rows.height() + 6;
        }
        return height;
    }

    /** Whether a list is short enough to open in place (spec S1): at most eight lines when the tab is laid out. */
    private boolean foldable(SettingsModel.Setting setting) {
        return ListEdits.foldable(
            SettingsModel.lines(model.value(setting))
                .size());
    }

    /** The line under a control: a refused value's reason in red, or a focused number's range (spec M6.1, M6.2). */
    private String underLine(SettingsModel.Setting setting) {
        NumberWords.Refusal refusal = model.refusal(setting.id);
        if (refusal != null) return model.refusalWords(refusal);
        if (setting.isNumber() && ("c:" + setting.id).equals(focus)) return model.range(setting);
        return null;
    }

    private void clampScroll() {
        int most = Math.max(0, contentHeight - frame.rows.height());
        scroll = scroll < 0 ? 0 : Math.min(scroll, most);
    }

    private View viewOf(String id) {
        for (View view : views) {
            if (view.row.setting != null && (view.row.setting.id.equals(id) || view.row.setting.key.equals(id))) {
                return view;
            }
        }
        return null;
    }

    /** One row as laid out: its row, where it starts in the list, and how tall it is. */
    private static final class View {

        final SettingsModel.Row row;

        int top;

        int height;

        View(SettingsModel.Row row) {
            this.row = row;
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * From 1.20.2 a screen draws its background itself, once a frame, and 1.21 blurs it (from 26.1 its
     * {@code extractBackground}). Answered here with nothing, so the dirt this screen draws first is not drawn over.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        canvas.frame(graphics);
        // A frame's room for first tries of list pictures, and its slice of a picker's gathering (spec Part 6).
        SettingsPictures.frame();
        canvas.background(0, 0, width, height);
        drawHeader(mouseX, mouseY);
        strip.draw(
            canvas,
            model.tabs()
                .indexOf(tab),
            tabDots(),
            mouseX,
            mouseY);
        if (frame.subTabs != null) {
            List<String> pages = model.pages(tab);
            Object[] pictures = new Object[pages.size()];
            int[] dots = new int[pages.size()];
            for (int i = 0; i < pages.size(); i++) {
                SurfaceFamily family = SettingsModel.familyOf(pages.get(i));
                pictures[i] = family == null ? SettingsCanvas.TAMPER : CommandTrmt.roadBlock(family);
                dots[i] = model.dot(model.idsOfPage(pages.get(i)));
            }
            icons.draw(canvas, pictures, pages.indexOf(page), dots, mouseX, mouseY);
            focusFrame("subtabs", frame.subTabs);
        }
        drawSearchRow(mouseX, mouseY, partial);
        String banner = model.banner();
        if (frame.banner != null && banner != null) {
            Rect at = frame.banner;
            canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.BANNER);
            Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, Canvas.RED);
            canvas.text(canvas.trim(banner, at.width() - 8), at.x0 + 4, at.y0 + 2, Canvas.WHITE, true);
        }
        drawPanel(mouseX, mouseY, partial);
        drawFooter(mouseX, mouseY);
        ChooserButton open = drawnChooser();
        if (open != null) open.drawDropdown(canvas, mouseX, mouseY, frame.rows, new Rect(0, 0, width, height));
        else drawHover(mouseX, mouseY);
        canvas.drawHover();
    }

    private int[] tabDots() {
        List<String> tabs = model.tabs();
        int[] dots = new int[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) dots[i] = model.dot(model.idsOf(tabs.get(i)));
        return dots;
    }

    /** The header: the lawn band, its gold rule, the wordmark, the help button and the header's icon buttons. */
    private void drawHeader(int mouseX, int mouseY) {
        canvas.pushClip(0, 0, width, frame.header.y1);
        WornPathArt.lawnBand(canvas.graphics(), width, frame.header.y1);
        canvas.popClip();
        canvas.fill(frame.headerRule.x0, frame.headerRule.y0, frame.headerRule.x1, frame.headerRule.y1, Canvas.GOLD);
        WornPathArt.wordmark(canvas.graphics(), frame);
        iconButton(frame.help, "help", true, mouseX, mouseY, "help");
        List<String> header = model.header();
        for (int i = 0; i < header.size(); i++) {
            String action = header.get(i);
            iconButton(frame.headerSlots[i], action, true, mouseX, mouseY, "header:" + action);
        }
    }

    /** A square button with an icon: dark fill, a brown frame, gold under the pointer or with the focus. */
    private void iconButton(Rect at, String icon, boolean enabled, int mouseX, int mouseY, String id) {
        boolean under = enabled && at.contains(mouseX, mouseY);
        canvas.fill(at.x0, at.y0, at.x1, at.y1, under ? Canvas.TAB_IDLE : Canvas.TAB_OPEN);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, under ? Canvas.GOLD : Canvas.RULE);
        canvas.icon(icon, at.x0 + (at.width() - 16) / 2, at.y0 + (at.height() - 16) / 2);
        if (!enabled) canvas.fill(at.x0 + 1, at.y0 + 1, at.x1 - 1, at.y1 - 1, 0xA0271A11);
        focusFrame(id, at);
    }

    /** A button with words. */
    private void wordButton(Rect at, String label, boolean enabled, int mouseX, int mouseY, String id) {
        boolean under = enabled && at.contains(mouseX, mouseY);
        canvas.fill(at.x0, at.y0, at.x1, at.y1, under ? Canvas.TAB_IDLE : Canvas.TAB_OPEN);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, under ? Canvas.GOLD : Canvas.RULE);
        String shown = canvas.trim(label, at.width() - 6);
        canvas.text(
            shown,
            at.x0 + (at.width() - canvas.width(shown)) / 2,
            at.y0 + (at.height() - 8) / 2,
            !enabled ? Canvas.GREY : under ? Canvas.GOLD : Canvas.WHITE,
            true);
        focusFrame(id, at);
    }

    /** The focused control's gold frame, one pixel outside it (spec 1.4). */
    private void focusFrame(String id, Rect at) {
        if (id.equals(focus)) Canvas.frame(canvas, at.x0 - 1, at.y0 - 1, at.x1 + 1, at.y1 + 1, Canvas.GOLD);
    }

    private void drawSearchRow(int mouseX, int mouseY, float partial) {
        Rect at = frame.search;
        canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.TAB_OPEN);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, Canvas.RULE);
        canvas.icon("search", at.x0 + 1, at.y0 + (at.height() - 16) / 2);
        if (search.getValue()
            .isEmpty() && !search.isFocused()) {
            canvas.text(
                canvas.trim(I18n.get(SettingsKeys.SEARCH_HINT), at.width() - 22),
                at.x0 + 18,
                search.getY(),
                Canvas.GREY,
                true);
        }
        search.extractRenderState(canvas.graphics(), mouseX, mouseY, partial);
        canvas.drewText();
        if (searching()) {
            String count = model.resultCount(model.results(searchText, changedOnly));
            canvas.text(count, at.x1 - 4 - (int) (canvas.width(count) * 0.75F), at.y0 + 5, Canvas.BODY, 0.75F);
        }
        focusFrame("search", at);
        Rect toggle = frame.changedOnly;
        wordButton(toggle, I18n.get(SettingsKeys.FILTER_CHANGED), true, mouseX, mouseY, "changedOnly");
        if (changedOnly) canvas.fill(toggle.x0, toggle.y1 - 2, toggle.x1, toggle.y1, Canvas.GOLD);
    }

    private void drawPanel(int mouseX, int mouseY, float partial) {
        Rect panel = frame.panel;
        Rect rows = frame.rows;
        Canvas.frame(canvas, panel.x0, panel.y0, panel.x1, panel.y1, Canvas.GOLD);
        canvas.panel(rows.x0, rows.y0, rows.x1, rows.y1);
        canvas.pushClip(rows.x0, rows.y0, rows.x1, rows.y1);
        int r = SettingsGeometry.rowWidth(frame);
        String pattern = bandPattern();
        if (pattern != null && scroll < BAND) {
            // A family page's head (spec S3.3): its road block worn across a path, in the family's own look.
            GuiGraphicsExtractor graphics = canvas.graphics();
            ChooserPictures.drawBand(graphics, SettingsModel.familyOf(page), pattern, rows.x0 + 4, rows.y0 + 2 - scroll, r - 8);
        }
        for (View view : views) {
            int y = rows.y0 + view.top - scroll;
            if (y + view.height <= rows.y0 || y >= rows.y1) continue;
            drawRow(view, rows.x0, y, r, mouseX, mouseY, partial);
        }
        canvas.popClip();
        if (contentHeight > rows.height()) {
            Rect track = frame.scrollbar;
            canvas.fill(track.x0, track.y0, track.x1, track.y1, Canvas.TAB_OPEN);
            Rect thumb = thumb();
            boolean lit = draggingThumb || thumb.contains(mouseX, mouseY);
            canvas.fill(thumb.x0, thumb.y0, thumb.x1, thumb.y1, lit ? Canvas.GOLD : Canvas.RULE);
        }
    }

    /** A family page's band's height: 16 and its margins (spec S3.3). */
    private static final int BAND = 20;

    /** The look a family page's band is drawn in, or null where the page has no band: not a family's, or a search. */
    private String bandPattern() {
        if (searching() || SettingsModel.familyOf(page) == null) return null;
        SettingsModel.Setting look = model.setting(page + "." + ChooserPictures.WEAR_PATTERN);
        return look == null ? null : model.value(look);
    }

    /** The scrollbar's thumb (spec 1.1): its height the share of the rows in view. */
    private Rect thumb() {
        Rect track = frame.scrollbar;
        int visible = track.height();
        int total = Math.max(visible, contentHeight);
        int size = Math.max(16, visible * visible / total);
        int most = Math.max(1, contentHeight - visible);
        int y = track.y0 + (visible - size) * scroll / most;
        return new Rect(track.x0, y, track.x1, y + size);
    }

    private void drawRow(View view, int left, int top, int r, int mouseX, int mouseY, float partial) {
        SettingsModel.Row row = view.row;
        switch (row.type) {
            case FOLDER:
                canvas.text(row.text, left + 6, top + 5, Canvas.GOLD, true);
                canvas.fill(left, top + 17, left + r, top + 18, Canvas.RULE);
                return;
            case NOTE: {
                int y = top + 4;
                for (String line : HoverText.wrap(canvas, Collections.singletonList(row.text), r - 12)) {
                    canvas.text(line, left + 6, y, Canvas.BODY, true);
                    y += 10;
                }
                canvas.fill(left, top + view.height - 1, left + r, top + view.height, Canvas.RULE);
                return;
            }
            case ADVANCED: {
                canvas.icon(advancedOpen ? "fold_open" : "fold_closed", left + 2, top + 1);
                canvas.text(row.text, left + 20, top + 5, Canvas.GOLD, true);
                canvas.fill(left, top + 17, left + r, top + 18, Canvas.RULE);
                focusFrame("adv", new Rect(left, top, left + r, top + 18));
                return;
            }
            case REMEMBERED: {
                canvas.text(
                    canvas.trim(I18n.get(SettingsKeys.ADVANCED_NOTE), r - 18),
                    left + 12,
                    top + 2,
                    Canvas.BODY,
                    0.75F);
                remembered.layout(top + 12);
                remembered.draw(canvas, mouseX, mouseY);
                return;
            }
            case EMPTY: {
                String said = canvas.trim(row.text, r - 12);
                canvas.text(
                    said,
                    left + (r - canvas.width(said)) / 2,
                    frame.rows.y0 + frame.rows.height() / 2 - 4,
                    Canvas.BODY,
                    true);
                return;
            }
            default:
                break;
        }
        int y = top;
        if (row.kicker != null) {
            canvas.text(row.kicker, left + 6, y + 2, Canvas.GOLD, 0.75F);
            y += SettingsGeometry.KICKER;
        }
        drawSetting(row, left, y, r, mouseX, mouseY, partial);
    }

    /** One setting's row (spec 1.2, 1.4). */
    private void drawSetting(SettingsModel.Row row, int left, int top, int r, int mouseX, int mouseY, float partial) {
        SettingsModel.Setting setting = row.setting;
        SettingsGeometry.SettingRow at = SettingsGeometry.settingRow(left, top, r, frame.compact);
        boolean changed = model.edits()
            .changed(setting.id);
        String locked = model.locked(setting);
        boolean greyed = model.greyed(setting);
        boolean enabled = model.enabled(setting);
        if (changed) canvas.fill(at.changedBar.x0, at.changedBar.y0, at.changedBar.x1, at.changedBar.y1, Canvas.GOLD);

        int titleLeft = at.title.x0;
        boolean folds = setting.editor == ConfigLayout.Editor.LIST && foldable(setting);
        if (folds) {
            canvas.icon(OPEN_LISTS.contains(setting.id) ? "fold_open" : "fold_closed", left + 2, top + 7);
            focusFrame("f:" + setting.id, Rect.sized(left + 2, top + 7, 16, 16));
            titleLeft += 14;
        }
        String mark = setting.takes == com.trmtgtnh.client.settings.Takes.RESTART ? " *"
            : setting.takes == com.trmtgtnh.client.settings.Takes.RELOAD ? " ~" : "";
        String title = model.title(setting);
        int room = at.title.x1 - titleLeft - canvas.width(mark);
        String shownTitle = canvas.trim(title, room);
        int titleColor = locked != null ? Canvas.GREY : greyed ? Canvas.GREYED : changed ? Canvas.GOLD : Canvas.WHITE;
        canvas.text(shownTitle, titleLeft, at.title.y0, titleColor, true);
        for (int[] span : row.spans) {
            // The parts a search matched, gold over the title (spec M2.4).
            if (span[0] >= shownTitle.length()) continue;
            int end = Math.min(span[1], shownTitle.length());
            canvas.text(
                shownTitle.substring(span[0], end),
                titleLeft + canvas.width(shownTitle.substring(0, span[0])),
                at.title.y0,
                Canvas.GOLD,
                true);
        }
        if (!mark.isEmpty()) {
            canvas.text(mark, titleLeft + canvas.width(shownTitle), at.title.y0, Canvas.GOLD, true);
        }
        if (at.summary != null) {
            String summary = model.summary(setting);
            canvas.text(
                canvas.trim(summary, (int) ((at.summary.x1 - titleLeft) / 0.75F)),
                titleLeft,
                at.summary.y0,
                Canvas.BODY,
                0.75F);
        }

        drawControl(setting, at, enabled, mouseX, mouseY, partial);

        String under = underLine(setting);
        if (under != null) {
            boolean refused = model.refusal(setting.id) != null;
            canvas.text(
                canvas.trim(under, (int) (SettingsGeometry.CONTROL_WIDTH / 0.75F)),
                at.underLine.x0,
                at.underLine.y0 + 1,
                refused ? Canvas.RED : Canvas.BODY,
                0.75F);
        }
        if (changed) {
            canvas.icon("undo", at.undo.x0 - 1, at.undo.y0 - 1);
            focusFrame("u:" + setting.id, at.undo);
        }
        if (locked != null) {
            canvas.icon("lock", at.reset.x0 - 1, at.reset.y0 - 1);
        } else if (model.showsReset(setting)) {
            canvas.icon("reset", at.reset.x0 - 1, at.reset.y0 - 1);
            if (model.edits()
                .atDefault(setting.id) || !enabled) {
                // Greyed at its default (spec M8.1): the icon dimmed by the panel's own dark.
                canvas.fill(at.reset.x0, at.reset.y0, at.reset.x1, at.reset.y1, 0xB0100A06);
            }
            focusFrame("r:" + setting.id, at.reset);
        }

        ListRows rows = inline.get(setting.id);
        if (rows != null && OPEN_LISTS.contains(setting.id)) {
            int listTop = top + at.height + (under != null ? SettingsGeometry.UNDER_LINE : 0) + 2;
            Canvas.frame(canvas, left + 12, listTop, left + r, listTop + rows.height() + 2, Canvas.RULE);
            rows.layout(listTop + 1);
            rows.visible(frame.rows.y0, frame.rows.y1);
            rows.draw(canvas, mouseX, mouseY);
        }
    }

    /** A setting's control, by its kind (spec 1.8). */
    private void drawControl(SettingsModel.Setting setting, SettingsGeometry.SettingRow at, boolean enabled, int mouseX,
        int mouseY, float partial) {
        String id = "c:" + setting.id;
        String locked = model.locked(setting);
        if (locked != null) {
            // Set by this server (spec S4, 1.4): the control disabled, showing the server's value, not this client's.
            Rect c = at.control;
            canvas.fill(c.x0, c.y0, c.x1, c.y1, Canvas.TAB_OPEN);
            Canvas.frame(canvas, c.x0, c.y0, c.x1, c.y1, Canvas.RULE);
            canvas
                .text(canvas.trim(lockedWords(setting, locked), c.width() - 8), c.x0 + 4, c.y0 + 6, Canvas.GREY, true);
            return;
        }
        switch (setting.editor) {
            case SWITCH: {
                boolean on = model.on(setting);
                Rect c = at.control;
                boolean under = enabled && c.contains(mouseX, mouseY);
                canvas.fill(c.x0, c.y0, c.x1, c.y1, under ? Canvas.TAB_IDLE : Canvas.TAB_OPEN);
                Canvas.frame(canvas, c.x0, c.y0, c.x1, c.y1, under ? Canvas.GOLD : Canvas.RULE);
                String said = I18n.get(on ? SettingsKeys.OPTIONS_ON : SettingsKeys.OPTIONS_OFF);
                canvas.text(
                    said,
                    c.x0 + (c.width() - canvas.width(said)) / 2,
                    c.y0 + 6,
                    !enabled ? Canvas.GREY : on ? Canvas.WHITE : Canvas.BODY,
                    true);
                focusFrame(id, c);
                return;
            }
            case CHOOSER: {
                ChooserButton chooser = chooser(setting);
                chooser.layout(at.control);
                chooser.draw(canvas, mouseX, mouseY, id.equals(focus), enabled);
                focusFrame(id, at.control);
                return;
            }
            case SLIDER: {
                Slider slider = slider(setting);
                slider.layout(at.slider);
                slider.draw(canvas, id.equals(focus), mouseX, mouseY);
                drawField(setting, at.sliderField, enabled, mouseX, mouseY, partial);
                focusFrame(id, at.slider);
                return;
            }
            case COLOR: {
                // A swatch and its hex; no field to type in (spec M17): the swatch opens the picker.
                Rect s = at.swatch;
                ColorMath.Parsed parsed = ColorMath.parse(model.value(setting));
                int rgb = parsed.reads() ? parsed.rgb : 0xAA44CC;
                canvas.fill(s.x0, s.y0, s.x1, s.y1, 0xFF000000 | rgb);
                Canvas.frame(canvas, s.x0, s.y0, s.x1, s.y1, s.contains(mouseX, mouseY) ? Canvas.GOLD : Canvas.RULE);
                canvas.text(ColorMath.hex(rgb), at.hexReadout.x0 + 1, at.hexReadout.y0 + 7, Canvas.GREY, 0.75F);
                focusFrame(id, s);
                return;
            }
            case LIST: {
                int count = SettingsModel.lines(model.value(setting))
                    .size();
                wordButton(at.control, I18n.get(SettingsKeys.EDIT_LIST, count), enabled, mouseX, mouseY, id);
                return;
            }
            default:
                drawField(setting, at.control, enabled, mouseX, mouseY, partial);
        }
    }

    /** A server's value as the control would say it: On or Off, a chooser value's title, else as it is. */
    private String lockedWords(SettingsModel.Setting setting, String value) {
        if (setting.editor == ConfigLayout.Editor.SWITCH) {
            return I18n.get(Boolean.parseBoolean(value) ? SettingsKeys.OPTIONS_ON : SettingsKeys.OPTIONS_OFF);
        }
        if (setting.editor == ConfigLayout.Editor.CHOOSER) {
            String key = SettingsKeys.option(setting.id, value);
            String title = I18n.get(key);
            if (!key.equals(title)) return title;
        }
        return value;
    }

    /** A field to type in: its box, its text red while refused, gold framed while focused. */
    private void drawField(SettingsModel.Setting setting, Rect box, boolean enabled, int mouseX, int mouseY,
        float partial) {
        EditBox field = field(setting, box);
        canvas.fill(box.x0, box.y0, box.x1, box.y1, Canvas.TAB_OPEN);
        Canvas.frame(canvas, box.x0, box.y0, box.x1, box.y1, Canvas.RULE);
        field.setX(box.x0 + 4);
        field.setY(box.y0 + 6);
        boolean refused = model.refusal(setting.id) != null;
        // The colors whole, alpha and all: from 1.21.6 a text color's alpha is drawn, and the 1.21.1 form, the alpha
        // masked off, drew every field's words fully clear (0.9.228, seen in every 26.x settings-tabs photograph).
        field.setTextColor(!enabled ? Canvas.GREY : refused ? Canvas.RED : Canvas.BODY);
        field.setTextColorUneditable(Canvas.GREY);
        field.setEditable(enabled);
        field.extractRenderState(canvas.graphics(), mouseX, mouseY, partial);
        canvas.drewText();
        focusFrame("c:" + setting.id, box);
    }

    private EditBox field(SettingsModel.Setting setting, Rect box) {
        EditBox field = fields.get(setting.id);
        if (field == null) {
            field = new EditBox(font, box.x0 + 4, box.y0 + 6, box.width() - 8, 10, Component.literal(""));
            field.setBordered(false);
            field.setMaxLength(32767);
            String kept = typed.get(setting.id);
            if (kept != null) field.setValue(kept);
            else field.setValue(model.value(setting));
            field.moveCursorToStart(false);
            field.setFocused(("c:" + setting.id).equals(focus));
            fields.put(setting.id, field);
        }
        return field;
    }

    private Slider slider(SettingsModel.Setting setting) {
        Slider slider = sliders.get(setting.id);
        if (slider == null) {
            ConfigFile.Setting property = setting.property;
            double min = ConfigWords.number(ConfigLayout.minOf(property), 0D);
            double max = ConfigWords.number(ConfigLayout.maxOf(property), 0D);
            slider = new Slider(ConfigWords.number(model.value(setting), min), min, max, setting.kind);
            if (setting.id.equals(sliderHeld)) slider.hold(true);
            sliders.put(setting.id, slider);
        }
        return slider;
    }

    private ChooserButton chooser(final SettingsModel.Setting setting) {
        ChooserButton chooser = choosers.get(setting.id);
        if (chooser == null) {
            chooser = new ChooserButton(
                setting.category,
                setting.key,
                model.choices(setting),
                model.value(setting),
                value -> {
                    model.choose(setting, value);
                    edited();
                });
            if (com.trmtgtnh.config.Presets.CATEGORY.equals(setting.category)) {
                chooser.details(rung -> model.presetChanges(setting, rung));
            }
            if (setting.id.equals(dropdownShown)) {
                chooser.open(dropdownHighlight);
                dropdownShown = null;
            }
            choosers.put(setting.id, chooser);
        }
        return chooser;
    }

    /** The chooser whose dropdown is drawn this frame: open, or opening, as it is built as it is first drawn. */
    private ChooserButton drawnChooser() {
        for (ChooserButton chooser : choosers.values()) {
            if (chooser.wantsOpen()) return chooser;
        }
        return null;
    }

    private ChooserButton openChooser() {
        for (ChooserButton chooser : choosers.values()) {
            if (chooser.dropdownOpen()) return chooser;
        }
        return null;
    }

    private void drawFooter(int mouseX, int mouseY) {
        boolean anyChanged = model.changed();
        boolean held = model.saveHeld();
        if (frame.compact) {
            iconButton(frame.undoAll, "undo_all", anyChanged, mouseX, mouseY, "undoAll");
            iconButton(frame.resetTab, "reset_tab", !searching(), mouseX, mouseY, "resetTab");
        } else {
            wordButton(frame.undoAll, I18n.get(SettingsKeys.UNDO_ALL), anyChanged, mouseX, mouseY, "undoAll");
            wordButton(frame.resetTab, I18n.get(SettingsKeys.RESET_TAB), !searching(), mouseX, mouseY, "resetTab");
        }
        wordButton(frame.cancel, I18n.get(SettingsKeys.GUI_CANCEL), true, mouseX, mouseY, "cancel");
        wordButton(frame.done, model.saveLabel(), !held, mouseX, mouseY, "done");
    }

    // ------------------------------------------------------------------
    // Hovers (M19, S8)
    // ------------------------------------------------------------------

    /** The one hover this frame: what the pointer rests on, or a photograph's. */
    private void drawHover(int mouseX, int mouseY) {
        List<SettingsModel.Line> lines = null;
        Object what = null;
        int x = mouseX;
        int y = mouseY;
        if (toggleHoverShown && !model.header()
            .isEmpty()) {
            lines = model.headerHover(SettingsModel.HEADER_TOGGLE, I18n.get(ConfigScreens.otherNameKey()));
            x = frame.headerSlots[0].x0;
            y = frame.headerSlots[0].y1;
            what = "toggle";
        } else if (hoverShown != null) {
            View view = viewOf(hoverShown);
            if (view != null) {
                lines = model.hover(view.row.setting, shiftShown || shiftDown());
                x = frame.rows.x0 + 8;
                y = frame.rows.y0 + view.top - scroll + 10;
                what = view.row.setting;
            }
        } else {
            what = under(mouseX, mouseY);
            lines = hoverOf(what);
        }
        if (!java.util.Objects.equals(what, hoverWhat)) {
            hoverWhat = what;
            hoverSince = now();
            hoverLine = 0;
        }
        if (lines == null || lines.isEmpty()) return;
        if (what instanceof Control && now() - hoverSince < CONTROL_HOVER_MILLIS) return;
        boolean whole = what instanceof SettingsModel.Setting && (shiftShown || shiftDown())
            || what instanceof Control && shiftDown();
        drawLines(SettingsModel.wrap(canvas, lines, width), whole, x, y);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static boolean shiftDown() {
        return net.minecraft.client.Minecraft.getInstance()
            .hasShiftDown();
    }

    /** A hover's lines in its box, inside the screen; a Shift-held whole description paged (spec M19.3). */
    private void drawLines(List<SettingsModel.Line> wrapped, boolean whole, int x, int y) {
        List<String> lines = SettingsModel.texts(wrapped);
        HoverText.Page shown = HoverText.page(lines, hoverLine, height);
        List<SettingsModel.Line> drawn = new ArrayList<SettingsModel.Line>();
        int from = Math.max(0, shown.from - 1);
        for (int i = from; i < from + shown.lines.size(); i++) drawn.add(wrapped.get(i));
        if (shown.paged) {
            drawn.add(
                new SettingsModel.Line(
                    I18n.get(SettingsKeys.HOVER_PAGE, shown.from, shown.to, shown.of),
                    Canvas.GREY,
                    false));
        }
        int widest = 0;
        for (SettingsModel.Line line : drawn) {
            widest = Math.max(widest, canvas.width(line.text) + (line.shiftIcon ? SettingsModel.SHIFT_ICON_ROOM : 0));
        }
        int w = Math.min(widest, HoverText.width(width)) + 2 * HoverText.PAD;
        int h = drawn.size() * HoverText.LINE - 2 + 2 * HoverText.PAD;
        Rect box = HoverText.place(w, h, x, y, width, height);
        canvas.fill(box.x0, box.y0, box.x1, box.y1, Canvas.HOVER_BG);
        Canvas.frame(canvas, box.x0, box.y0, box.x1, box.y1, Canvas.GOLD);
        int ty = box.y0 + HoverText.PAD;
        for (SettingsModel.Line line : drawn) {
            int tx = box.x0 + HoverText.PAD;
            if (line.shiftIcon) {
                GuiGraphicsExtractor graphics = canvas.graphics();
                graphics.pose().pushMatrix();
                graphics.pose().translate(tx, ty - 1);
                graphics.pose().scale(0.625F, 0.625F);
                canvas.icon("shift", 0, 0);
                graphics.pose().popMatrix();
                tx += SettingsModel.SHIFT_ICON_ROOM;
            }
            canvas.text(line.text, tx, ty, line.color, true);
            ty += HoverText.LINE;
        }
    }

    /** A control the pointer rests on, whose hover waits half a second. */
    private static final class Control {

        final SettingsModel.Setting setting;

        Control(SettingsModel.Setting setting) {
            this.setting = setting;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Control && ((Control) other).setting == setting;
        }

        @Override
        public int hashCode() {
            return setting.hashCode();
        }
    }

    /** What is under the pointer, for its hover: a setting's name, a control, a tab, a header or footer button. */
    private Object under(int mouseX, int mouseY) {
        if (frame.help.contains(mouseX, mouseY)) return "help";
        List<String> header = model.header();
        for (int i = 0; i < header.size(); i++) {
            if (frame.headerSlots[i].contains(mouseX, mouseY)) return "header:" + header.get(i);
        }
        int tabAt = strip.hit(mouseX, mouseY);
        if (tabAt >= 0) return "tab:" + model.tabs()
            .get(tabAt);
        if (frame.subTabs != null) {
            int at = icons.hit(mouseX, mouseY);
            if (at >= 0) return "page:" + model.pages(tab)
                .get(at);
        }
        if (frame.changedOnly.contains(mouseX, mouseY)) return "changedOnly";
        if (frame.undoAll.contains(mouseX, mouseY)) return "undoAll";
        if (frame.resetTab.contains(mouseX, mouseY)) return "resetTab";
        if (!frame.rows.contains(mouseX, mouseY)) return null;
        View view = viewAt(mouseY);
        if (view == null || view.row.setting == null) return null;
        SettingsGeometry.SettingRow at = rowGeometry(view);
        if (mouseX < at.control.x0 - 6) return view.row.setting;
        if (at.control.contains(mouseX, mouseY) || at.slider.contains(mouseX, mouseY)
            || at.sliderField.contains(mouseX, mouseY)) {
            return new Control(view.row.setting);
        }
        if (at.undo.contains(mouseX, mouseY)) return "undo:" + view.row.setting.id;
        if (at.reset.contains(mouseX, mouseY)) return "reset:" + view.row.setting.id;
        return null;
    }

    private List<SettingsModel.Line> hoverOf(Object what) {
        if (what == null) return null;
        // With Shift held, the whole description (0.9.228; Xep, from GT New Horizons: "Hold Shift for the whole
        // description" showed on every hover and Shift changed nothing - this asked for the short hover always).
        if (what instanceof SettingsModel.Setting) return model.hover((SettingsModel.Setting) what, shiftDown());
        if (what instanceof Control) return model.hover(((Control) what).setting, shiftDown());
        String said = (String) what;
        if ("help".equals(said)) return model.keysHover();
        if (said.startsWith("header:")) {
            return model.headerHover(said.substring(7), I18n.get(ConfigScreens.otherNameKey()));
        }
        if (said.startsWith("tab:")) {
            String each = said.substring(4);
            String category = TrmtConfig.CATEGORY_SURFACES.equals(each) ? TrmtConfig.CATEGORY_SURFACES : each;
            return model.tabHover(model.tabLabel(each), category, model.idsOf(each));
        }
        if (said.startsWith("page:")) {
            String each = said.substring(5);
            return model.tabHover(model.pageLabel(each), each, model.idsOfPage(each));
        }
        if ("changedOnly".equals(said)) return tip(SettingsKeys.FILTER_CHANGED, SettingsKeys.FILTER_CHANGED_TIP);
        if ("undoAll".equals(said)) return tip(SettingsKeys.UNDO_ALL, SettingsKeys.UNDO_ALL_TIP);
        if ("resetTab".equals(said)) return tip(SettingsKeys.RESET_TAB, SettingsKeys.RESET_TAB_TIP);
        if (said.startsWith("undo:")) {
            SettingsModel.Setting setting = model.setting(said.substring(5));
            return Collections.singletonList(
                new SettingsModel.Line(
                    I18n.get(
                        SettingsKeys.UNDO_TIP,
                        model.edits()
                            .opening(setting.id)),
                    Canvas.BODY));
        }
        if (said.startsWith("reset:")) {
            SettingsModel.Setting setting = model.setting(said.substring(6));
            return Collections.singletonList(
                new SettingsModel.Line(
                    I18n.get(
                        SettingsKeys.RESET_TIP,
                        model.edits()
                            .defaultOf(setting.id)),
                    Canvas.BODY));
        }
        return null;
    }

    private static List<SettingsModel.Line> tip(String title, String tip) {
        return Arrays.asList(
            new SettingsModel.Line(I18n.get(title), Canvas.GOLD),
            new SettingsModel.Line(I18n.get(tip), Canvas.BODY));
    }

    private View viewAt(int mouseY) {
        int y = mouseY - frame.rows.y0 + scroll;
        for (View view : views) {
            if (y >= view.top && y < view.top + view.height) return view;
        }
        return null;
    }

    /** A setting row's parts where it is drawn now. */
    private SettingsGeometry.SettingRow rowGeometry(View view) {
        int top = frame.rows.y0 + view.top - scroll + (view.row.kicker != null ? SettingsGeometry.KICKER : 0);
        return SettingsGeometry.settingRow(frame.rows.x0, top, SettingsGeometry.rowWidth(frame), frame.compact);
    }

    // ------------------------------------------------------------------
    // What a press does
    // ------------------------------------------------------------------

    /** Every edit comes back here: the controls agree with the model again and the rows are laid out anew. */
    private void edited() {
        for (Map.Entry<String, EditBox> one : fields.entrySet()) {
            if (one.getValue()
                .isFocused() || typed.containsKey(one.getKey())) continue;
            one.getValue()
                .setValue(model.value(model.setting(one.getKey())));
        }
        for (Map.Entry<String, Slider> one : sliders.entrySet()) {
            if (one.getValue() == dragging) continue;
            one.getValue()
                .set(ConfigWords.number(model.value(model.setting(one.getKey())), 0D));
        }
        for (Map.Entry<String, ChooserButton> one : choosers.entrySet()) {
            one.getValue()
                .show(model.value(model.setting(one.getKey())));
        }
        boolean bannerWas = frame.banner != null;
        if (bannerWas != (model.banner() != null)) relayout();
        else layoutRows();
    }

    private void click() {
        minecraft.getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** The header's buttons: the toggle, the Wear Table, Push to server (spec M14, M15). */
    private void headerPressed(String action) {
        if (SettingsModel.HEADER_PUSH.equals(action)) {
            // Shown to everybody, as before: a client cannot tell whether it is an operator; the server decides.
            TrmtNetwork.pushConfig(ConfigWords.asLines());
        } else if (SettingsModel.HEADER_WEARTABLE.equals(action)) {
            // Over this screen, so Back comes back to the edits not yet saved.
            minecraft.setScreen(new GuiWearTable(WornPathScreen.this));
        } else if (SettingsModel.HEADER_TOGGLE.equals(action)) {
            if (!model.changed()) {
                minecraft.setScreen(ConfigScreens.switchTo(false, parent));
                return;
            }
            // Asked first with something unsaved (spec M14.2): save and switch, switch and lose it, or stay.
            minecraft.setScreen(toggleQuestion());
        }
    }

    /** "Save before switching?" with its three answers (spec M14.2): Save and leave, Leave without saving, Stay. */
    private Screen toggleQuestion() {
        final WornPathScreen self = this;
        List<SettingsQuestion.Answer> answers = new ArrayList<SettingsQuestion.Answer>();
        answers.add(new SettingsQuestion.Answer(SettingsKeys.DISCARD_SAVE, () -> {
            if (model.saveHeld()) {
                minecraft.setScreen(self);
                return;
            }
            SettingsModel.Saved saved = model.save();
            if (saved.written > 0) ConfigWords.saved();
            Screen other = ConfigScreens.switchTo(false, parent);
            minecraft.setScreen(
                saved.restart || saved.reload ? new WornPathRestartScreen(other, saved.restart, saved.reload) : other);
        }));
        answers.add(
            new SettingsQuestion.Answer(
                SettingsKeys.DISCARD_LEAVE,
                () -> minecraft.setScreen(ConfigScreens.switchTo(false, parent))));
        answers.add(new SettingsQuestion.Answer(SettingsKeys.DISCARD_STAY, () -> minecraft.setScreen(self)));
        return new SettingsQuestion(
            Arrays.asList(SettingsKeys.TOGGLE_ASK, SettingsKeys.DISCARD_NOTE),
            Collections.singletonList(SettingsKeys.TOGGLE_ASK),
            answers);
    }

    private void openTab(String each) {
        if (each == null || each.equals(tab)) return;
        SCROLLS.put(scrollKey(), scroll);
        tab = each;
        lastTab = each;
        page = pageOf(each);
        Integer kept = SCROLLS.get(page);
        scroll = kept == null ? 0 : kept;
        strip.show(
            model.tabs()
                .indexOf(each));
        relayout();
    }

    private void openPage(String each) {
        if (each == null || each.equals(page)) return;
        SCROLLS.put(scrollKey(), scroll);
        page = each;
        LAST_PAGE.put(tab, each);
        Integer kept = SCROLLS.get(page);
        scroll = kept == null ? 0 : kept;
        relayout();
    }

    /** Ctrl+Tab: the next tab, through Surfaces' sub-tabs on the way (spec 1.6). */
    private void nextTab(int direction) {
        List<String> pages = model.pages(tab);
        int at = pages.indexOf(page);
        if (pages.size() > 1 && at + direction >= 0 && at + direction < pages.size()) {
            openPage(pages.get(at + direction));
            return;
        }
        List<String> tabs = model.tabs();
        if (tabs.isEmpty()) return;
        int index = ((tabs.indexOf(tab) + direction) % tabs.size() + tabs.size()) % tabs.size();
        openTab(tabs.get(index));
        List<String> into = model.pages(tab);
        if (direction < 0 && into.size() > 1) openPage(into.get(into.size() - 1));
        else if (into.size() > 1) openPage(into.get(0));
    }

    private void setFocus(String id) {
        focus = id;
        if (id != null) {
            for (ListRows rows : inline.values()) rows.blur();
            if (remembered != null) remembered.blur();
        }
        search.setFocused("search".equals(id));
        for (Map.Entry<String, EditBox> one : fields.entrySet()) {
            one.getValue()
                .setFocused(("c:" + one.getKey()).equals(id));
        }
        // A focused number shows its range under it, so the rows move.
        layoutRows();
        View view = id != null && id.startsWith("c:") ? viewOf(id.substring(2)) : null;
        if (view != null) {
            // Scrolled into view.
            if (view.top < scroll) scroll = view.top;
            else if (view.top + view.height > scroll + frame.rows.height()) {
                scroll = view.top + view.height - frame.rows.height();
            }
            clampScroll();
        }
    }

    /** The order Tab walks (spec 1.6, M11): the header, search, Changed only, every row's controls, the footer. */
    private FocusRing ring() {
        List<String> ids = new ArrayList<String>();
        ids.add("help");
        for (String action : model.header()) ids.add("header:" + action);
        ids.add("search");
        ids.add("changedOnly");
        if (frame.subTabs != null) ids.add("subtabs");
        for (View view : views) {
            SettingsModel.Row row = view.row;
            if (row.type == SettingsModel.RowType.ADVANCED) ids.add("adv");
            if (row.setting == null) continue;
            String id = row.setting.id;
            if (row.setting.editor == ConfigLayout.Editor.LIST && foldable(row.setting)) ids.add("f:" + id);
            ids.add("c:" + id);
            ids.add("u:" + id);
            ids.add("r:" + id);
        }
        ids.add("undoAll");
        ids.add("resetTab");
        ids.add("cancel");
        ids.add("done");
        return new FocusRing(ids).skip(id -> !focusable(id));
    }

    /** Whether Tab may land on a control: disabled, locked and absent controls are skipped. */
    private boolean focusable(String id) {
        if (id.startsWith("c:") || id.startsWith("u:") || id.startsWith("r:")) {
            SettingsModel.Setting setting = model.setting(id.substring(2));
            if (setting == null || !model.enabled(setting)) return false;
            if (id.startsWith("u:")) return model.edits()
                .changed(setting.id);
            if (id.startsWith("r:")) return model.showsReset(setting) && !model.edits()
                .atDefault(setting.id);
            return true;
        }
        if ("undoAll".equals(id)) return model.changed();
        if ("resetTab".equals(id)) return !searching();
        if ("done".equals(id)) return !model.saveHeld();
        return true;
    }

    /** Enter or Space on a focused control that is pressed rather than typed in. */
    private void press(String id) {
        if (id == null) return;
        if (id.startsWith("header:")) {
            String action = id.substring(7);
            headerPressed(action);
        } else if ("changedOnly".equals(id)) {
            toggleChangedOnly();
        } else if ("undoAll".equals(id)) {
            model.revertAll();
            typed.clear();
            edited();
        } else if ("resetTab".equals(id)) {
            askResetTab();
        } else if ("cancel".equals(id)) {
            leave();
        } else if ("done".equals(id)) {
            save();
        } else if ("adv".equals(id)) {
            advancedOpen = !advancedOpen;
            layoutRows();
        } else if (id.startsWith("f:")) {
            String setting = id.substring(2);
            if (!OPEN_LISTS.remove(setting)) OPEN_LISTS.add(setting);
            layoutRows();
        } else if (id.startsWith("u:")) {
            SettingsModel.Setting setting = model.setting(id.substring(2));
            model.revert(setting);
            typed.remove(setting.id);
            edited();
        } else if (id.startsWith("r:")) {
            SettingsModel.Setting setting = model.setting(id.substring(2));
            model.reset(setting);
            typed.remove(setting.id);
            edited();
        } else if (id.startsWith("c:")) {
            pressControl(model.setting(id.substring(2)), 0);
        }
    }

    /** A setting's control pressed: a switch switches, a list opens, a color opens its picker. */
    private void pressControl(final SettingsModel.Setting setting, int button) {
        if (setting == null || !model.enabled(setting)) return;
        switch (setting.editor) {
            case SWITCH:
                model.toggle(setting);
                edited();
                return;
            case LIST:
                minecraft.setScreen(new WornPathListScreen(this, setting));
                return;
            case COLOR:
                ColorField.open(this, model.value(setting), hex -> {
                    model.edits()
                        .set(setting.id, hex);
                    edited();
                });
                return;
            case CHOOSER:
                chooser(setting).open(model.value(setting));
                return;
            default:
        }
    }

    private void toggleChangedOnly() {
        SCROLLS.put(scrollKey(), scroll);
        changedOnly = !changedOnly;
        scroll = 0;
        relayout();
    }

    private void askResetTab() {
        if (searching()) return;
        final WornPathScreen self = this;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                model.resetPage(page);
                typed.clear();
            }
            minecraft.setScreen(self);
        },
            Component.literal(I18n.get(SettingsKeys.RESET_TAB_ASK, model.pageLabel(page))),
            Component.literal(I18n.get(SettingsKeys.RESET_TAB_NOTE)),
            Component.literal(I18n.get(SettingsKeys.RESET_TAB_YES)),
            Component.literal(I18n.get(SettingsKeys.GUI_CANCEL))));
    }

    /**
     * Save: every edit written into the settings, then the save and reload Cloth's screen reaches (spec CF49), then
     * back - by the restart screen where a change waits for a restart or a resource reload (M13.3).
     */
    private void save() {
        if (model.saveHeld()) return;
        SettingsModel.Saved saved = model.save();
        if (saved.written > 0) ConfigWords.saved();
        if (saved.restart || saved.reload) {
            minecraft.setScreen(new WornPathRestartScreen(parent, saved.restart, saved.reload));
        } else {
            minecraft.setScreen(parent);
        }
    }

    /** Escape or Cancel: back, asking first when there is something to lose. */
    private void leave() {
        if (!model.changed()) {
            minecraft.setScreen(parent);
            return;
        }
        final WornPathScreen self = this;
        minecraft.setScreen(
            new ConfirmScreen(
                yes -> minecraft.setScreen(yes ? parent : self),
                Component.literal(I18n.get(SettingsKeys.DISCARD)),
                Component.literal(I18n.get(SettingsKeys.DISCARD_NOTE))));
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        int modifiers = event.modifiers();
        keyTyped((char) 0, key);
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        char typedChar = (char) event.codepoint();
        keyTyped(typedChar, NO_KEY);
        return true;
    }

    /** A key into a field: the character it types where it carries one, else the key itself, as the field hears it. */
    private static boolean boxKey(EditBox box, char typedChar, int key) {
        // 26.x hands a field one event, built here from the key or the character as the game builds it.
        if (key == NO_KEY) return box.charTyped(new net.minecraft.client.input.CharacterEvent(typedChar));
        return box.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, 0));
    }

    /**
     * The keys of spec 1.6, every one handled here: a key with no character ({@code typedChar} nought) as the game
     * presses it, or a character typed with no key ({@link #NO_KEY}).
     */
    void keyTyped(char typedChar, int key) {
        boolean ctrl = minecraft.hasControlDown();
        boolean shift = minecraft.hasShiftDown();
        ChooserButton open = openChooser();
        if (open != null) {
            // Up, Down, Home, End, Enter and Escape move in and close the open dropdown.
            open.keyTyped(typedChar, key);
            return;
        }
        // A list open in place with one of its boxes focused takes the keys first: typing, its suggestions, Tab
        // through its boxes, Alt+Up and Alt+Down, and Escape out of it (spec S1, M5, S7).
        for (ListRows rows : inline.values()) {
            if (rows.focused() && rows.keyTyped(typedChar, key)) return;
        }
        // The Advanced fold's own fields (spec M16.2): digits, Up and Down, Tab through them, Escape out.
        if (remembered != null && remembered.focused() && remembered.keyTyped(typedChar, key)) {
            edited();
            return;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            if ("search".equals(focus) && !search.getValue()
                .isEmpty()) {
                search.setValue("");
                searchChanged();
                setFocus(null);
                return;
            }
            leave();
            return;
        }
        if (key == InputConstants.KEY_TAB) {
            if (ctrl) {
                nextTab(shift ? -1 : 1);
                return;
            }
            EditBox field = focusedField();
            if (field != null && suggestionTaken(field)) return;
            setFocus(ring().next(focus, shift));
            return;
        }
        if (ctrl && key == InputConstants.KEY_F) {
            setFocus("search");
            search.moveCursorToEnd(false);
            search.setHighlightPos(0);
            return;
        }
        if (ctrl && key == InputConstants.KEY_Z) {
            String id = focus != null && focus.startsWith("c:") ? focus.substring(2) : null;
            if (id == null || !model.edits()
                .undo(id)) model.undoLast();
            typed.clear();
            edited();
            return;
        }
        if (ctrl && key == InputConstants.KEY_S) {
            if (!model.saveHeld()) save();
            return;
        }
        if ("search".equals(focus)) {
            String before = search.getValue();
            boxKey(search, typedChar, key);
            if (!search.getValue()
                .equals(before)) searchChanged();
            return;
        }
        EditBox field = focusedField();
        if (field != null) {
            SettingsModel.Setting setting = model.setting(focus.substring(2));
            if (setting.isNumber() && (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN)) {
                stepField(setting, field, key == InputConstants.KEY_UP ? 1 : -1);
                return;
            }
            String before = field.getValue();
            boxKey(field, typedChar, key);
            if (!field.getValue()
                .equals(before)) typedInto(setting, field.getValue());
            return;
        }
        SettingsModel.Setting focused = focus != null && focus.startsWith("c:") ? model.setting(focus.substring(2))
            : null;
        if (focused != null && focused.editor == ConfigLayout.Editor.CHOOSER) {
            if (chooser(focused).keyTyped(typedChar, key)) return;
        }
        if (focused != null && focused.editor == ConfigLayout.Editor.SLIDER
            && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)) {
            Slider slider = slider(focused);
            if (slider.key(key == InputConstants.KEY_LEFT ? -1 : 1, shift)) {
                model.type(focused, slider.text());
                edited();
            }
            return;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE) {
            press(focus);
            return;
        }
        int page = frame.rows.height() - SettingsGeometry.SETTING_ROW;
        if (key == InputConstants.KEY_PAGEUP) scroll -= page;
        else if (key == InputConstants.KEY_PAGEDOWN) scroll += page;
        else if (key == InputConstants.KEY_HOME) scroll = 0;
        else if (key == InputConstants.KEY_END) scroll = contentHeight;
        clampScroll();
    }

    /** Tab in a name field showing a suggestion takes it (spec M5.1): the suggestions are the list rows' own. */
    private boolean suggestionTaken(EditBox field) {
        return false;
    }

    private EditBox focusedField() {
        if (focus == null || !focus.startsWith("c:")) return null;
        EditBox field = fields.get(focus.substring(2));
        return field != null && field.isFocused() ? field : null;
    }

    /** Text typed into a setting's field: kept, or refused red with its reason (spec M6.2). */
    private void typedInto(SettingsModel.Setting setting, String text) {
        if (model.type(setting, text) != null) typed.put(setting.id, text);
        else typed.remove(setting.id);
        Slider slider = sliders.get(setting.id);
        if (slider != null && model.refusal(setting.id) == null) slider.set(ConfigWords.number(text, 0D));
        edited();
    }

    /** Up, Down or the wheel on a focused number (spec M6.4). */
    private void stepField(SettingsModel.Setting setting, EditBox field, int direction) {
        String stepped = model.step(setting, field.getValue(), direction, minecraft.hasShiftDown(), minecraft.hasControlDown());
        field.setValue(stepped);
        typed.remove(setting.id);
        Slider slider = sliders.get(setting.id);
        if (slider != null) slider.set(ConfigWords.number(stepped, 0D));
        edited();
    }

    private void searchChanged() {
        String before = searchText;
        searchText = search.getValue();
        boolean was = !before.trim()
            .isEmpty();
        boolean now = !searchText.trim()
            .isEmpty();
        if (!was && now) {
            SCROLLS.put(page, scroll);
            scroll = 0;
        } else if (was && !now) {
            Integer kept = SCROLLS.get(page);
            scroll = kept == null ? 0 : kept;
        } else {
            scroll = 0;
        }
        if (was != now) relayout();
        else layoutRows();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double x = event.x();
        double y = event.y();
        int button = event.button();
        mouseClicked((int) x, (int) y, button);
        return true;
    }

    private void mouseClicked(int mouseX, int mouseY, int button) {
        ChooserButton open = openChooser();
        if (open != null) {
            open.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (frame.help.contains(mouseX, mouseY)) return;
        List<String> header = model.header();
        for (int i = 0; i < header.size(); i++) {
            if (frame.headerSlots[i].contains(mouseX, mouseY)) {
                String action = header.get(i);
                click();
                headerPressed(action);
                return;
            }
        }
        int tabAt = strip.hit(mouseX, mouseY);
        if (tabAt == TabStrip.ARROW_LEFT || tabAt == TabStrip.ARROW_RIGHT) {
            strip.scrollBy(tabAt == TabStrip.ARROW_LEFT ? -1 : 1);
            return;
        }
        if (tabAt >= 0) {
            click();
            openTab(
                model.tabs()
                    .get(tabAt));
            return;
        }
        if (frame.subTabs != null) {
            int at = icons.hit(mouseX, mouseY);
            if (at >= 0) {
                click();
                openPage(
                    model.pages(tab)
                        .get(at));
                return;
            }
        }
        if (frame.search.contains(mouseX, mouseY)) {
            setFocus("search");
            search.mouseClicked(WidgetEvents.click(mouseX, mouseY, button), false);
            if (button == 1) {
                search.setValue("");
                searchChanged();
            }
            return;
        }
        if (frame.changedOnly.contains(mouseX, mouseY)) {
            click();
            toggleChangedOnly();
            return;
        }
        for (String id : new String[] { "undoAll", "resetTab", "cancel", "done" }) {
            Rect at = "undoAll".equals(id) ? frame.undoAll
                : "resetTab".equals(id) ? frame.resetTab : "cancel".equals(id) ? frame.cancel : frame.done;
            if (at.contains(mouseX, mouseY)) {
                if (focusable(id)) {
                    click();
                    press(id);
                }
                return;
            }
        }
        if (contentHeight > frame.rows.height() && frame.scrollbar.contains(mouseX, mouseY)) {
            Rect thumb = thumb();
            if (thumb.contains(mouseX, mouseY)) draggingThumb = true;
            else scroll += (mouseY < thumb.y0 ? -1 : 1) * (frame.rows.height() - SettingsGeometry.SETTING_ROW);
            clampScroll();
            return;
        }
        if (frame.rows.contains(mouseX, mouseY)) rowClicked(mouseX, mouseY, button);
        else setFocus(null);
    }

    private void rowClicked(int mouseX, int mouseY, int button) {
        View view = viewAt(mouseY);
        if (view == null) return;
        if (view.row.type == SettingsModel.RowType.ADVANCED) {
            click();
            press("adv");
            return;
        }
        if (view.row.type == SettingsModel.RowType.REMEMBERED) {
            // A field of the fold took the keyboard: the screen's own let it go.
            if (remembered.mouseClicked(mouseX, mouseY, button)) {
                if (remembered.focused()) setFocus(null);
                edited();
            }
            return;
        }
        SettingsModel.Setting setting = view.row.setting;
        if (setting == null) return;
        ListRows rows = inline.get(setting.id);
        if (rows != null && OPEN_LISTS.contains(setting.id) && rows.mouseClicked(mouseX, mouseY, button)) {
            // A box of the list took the keyboard: the screen's own fields let it go.
            if (rows.focused()) setFocus(null);
            return;
        }
        SettingsGeometry.SettingRow at = rowGeometry(view);
        if (setting.editor == ConfigLayout.Editor.LIST && foldable(setting)
            && Rect.sized(at.changedBar.x0, at.title.y0 - 2, 18, 18)
                .contains(mouseX, mouseY)) {
            click();
            press("f:" + setting.id);
            return;
        }
        if (at.undo.contains(mouseX, mouseY) && model.edits()
            .changed(setting.id)) {
            click();
            setFocus("u:" + setting.id);
            press("u:" + setting.id);
            return;
        }
        if (at.reset.contains(mouseX, mouseY) && model.locked(setting) == null && model.showsReset(setting)) {
            if (!model.edits()
                .atDefault(setting.id) && model.enabled(setting)) {
                click();
                press("r:" + setting.id);
            }
            return;
        }
        if (!model.enabled(setting)) return;
        String id = "c:" + setting.id;
        switch (setting.editor) {
            case NUMBER:
            case TEXT:
                if (at.control.contains(mouseX, mouseY)) {
                    setFocus(id);
                    EditBox clicked = fields.get(setting.id);
                    if (clicked != null) clicked.mouseClicked(WidgetEvents.click(mouseX, mouseY, button), false);
                    return;
                }
                break;
            case SLIDER:
                if (at.sliderField.contains(mouseX, mouseY)) {
                    setFocus(id);
                    EditBox clicked = fields.get(setting.id);
                    if (clicked != null) clicked.mouseClicked(WidgetEvents.click(mouseX, mouseY, button), false);
                    return;
                }
                if (slider(setting).press(mouseX, mouseY)) {
                    setFocus(id);
                    dragging = slider(setting);
                    draggingId = setting.id;
                    model.type(setting, dragging.text());
                    edited();
                    return;
                }
                break;
            case CHOOSER:
                if (at.control.contains(mouseX, mouseY)) {
                    setFocus(id);
                    click();
                    chooser(setting).mouseClicked(mouseX, mouseY, button);
                    return;
                }
                break;
            case COLOR:
                if (at.swatch.contains(mouseX, mouseY)) {
                    setFocus(id);
                    click();
                    pressControl(setting, button);
                    return;
                }
                break;
            default:
                if (at.control.contains(mouseX, mouseY)) {
                    setFocus(id);
                    click();
                    pressControl(setting, button);
                    return;
                }
        }
        setFocus(null);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double x = event.x();
        double y = event.y();
        int button = event.button();
        int mouseX = (int) x;
        int mouseY = (int) y;
        for (ListRows rows : inline.values()) rows.mouseDragged(mouseX, mouseY);
        if (remembered != null) remembered.mouseDragged(mouseX, mouseY);
        if (dragging != null && dragging.drag(mouseX, mouseY)) {
            SettingsModel.Setting setting = model.setting(draggingId);
            model.type(setting, dragging.text());
            EditBox field = fields.get(draggingId);
            if (field != null) field.setValue(dragging.text());
            typed.remove(draggingId);
            edited();
        }
        if (draggingThumb) {
            Rect track = frame.scrollbar;
            int size = thumb().height();
            int travel = Math.max(1, track.height() - size);
            scroll = (mouseY - track.y0 - size / 2) * Math.max(0, contentHeight - track.height()) / travel;
            clampScroll();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double x = event.x();
        double y = event.y();
        int button = event.button();
        if (button == 0) {
            int mouseX = (int) x;
            int mouseY = (int) y;
            for (ListRows rows : inline.values()) rows.mouseReleased(mouseX, mouseY);
            if (remembered != null) remembered.mouseReleased(mouseX, mouseY);
            if (dragging != null) dragging.release();
            dragging = null;
            draggingId = null;
            draggingThumb = false;
        }
        return true;
    }

    /** The wheel (spec 1.6): an open dropdown, a Shift-held hover, the tab strip, a focused number, or the list. */
    @Override
    public boolean mouseScrolled(double x, double y, double across, double delta) {
        int notches = (int) Math.signum(delta);
        if (notches == 0 || frame == null) return false;
        int mouseX = (int) x;
        int mouseY = (int) y;
        ChooserButton open = openChooser();
        if (open != null) {
            open.wheel(notches);
            return true;
        }
        if (shiftDown() && hoverWhat != null) {
            hoverLine = Math.max(0, hoverLine - notches);
            return true;
        }
        if (frame.tabs.contains(mouseX, mouseY)) {
            strip.scrollBy(-notches);
            return true;
        }
        EditBox field = focusedField();
        if (field != null) {
            SettingsModel.Setting setting = model.setting(focus.substring(2));
            View view = viewOf(setting.id);
            if (setting.isNumber() && view != null && rowGeometry(view).control.contains(mouseX, mouseY)) {
                stepField(setting, field, notches);
                return true;
            }
        }
        SettingsModel.Setting focused = focus != null && focus.startsWith("c:") ? model.setting(focus.substring(2))
            : null;
        if (focused != null && focused.editor == ConfigLayout.Editor.CHOOSER) {
            View view = viewOf(focused.id);
            if (view != null && rowGeometry(view).control.contains(mouseX, mouseY)) {
                chooser(focused).wheel(notches);
                return true;
            }
        }
        if (frame.rows.contains(mouseX, mouseY)) {
            scroll -= notches * WHEEL_ROWS * SettingsGeometry.SETTING_ROW;
            clampScroll();
        }
        return true;
    }

    // From 1.20.2 a box's cursor blinks by the clock, and EditBox has no tick of its own to be asked: the tick is the
    // text input's alone.

    /** The typed characters kept coming while this screen is open (WidgetEvents.hearTyping). */
    @Override
    public void tick() {
        WidgetEvents.hearTyping(this);
    }

    @Override
    public void removed() {
        if (frame != null) SCROLLS.put(scrollKey(), scroll);
        if (tab != null) lastTab = tab;
    }

    /** The settings a tab holds, for a test or a hook that wants them. */
    Collection<String> idsOf(String each) {
        return model.idsOf(each);
    }

    /** Values edited and not saved, by id, as the model holds them. */
    Map<String, String> pending() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (String id : model.edits()
            .changedIds())
            out.put(
                id,
                model.edits()
                    .value(id));
        return out;
    }
}
