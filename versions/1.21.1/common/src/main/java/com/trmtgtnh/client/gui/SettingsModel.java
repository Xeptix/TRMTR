package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.Edits;
import com.trmtgtnh.client.settings.Greying;
import com.trmtgtnh.client.settings.HoverText;
import com.trmtgtnh.client.settings.NumberWords;
import com.trmtgtnh.client.settings.PresetDiff;
import com.trmtgtnh.client.settings.SettingsIndex;
import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.client.settings.TabStrip;
import com.trmtgtnh.client.settings.Takes;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.Presets;
import com.trmtgtnh.config.ServerRules;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Everything the mod's own settings screen knows and decides, without drawing anything (0.9.228; spec Part 1).
 *
 * <p>
 * The screen itself ({@link WornPathScreen}) is a {@code Screen}, which needs a running game to be built, so what it
 * shows and what it does with a press lives here, over the mod's own
 * {@link ConfigFile} and the shared {@code com.trmtgtnh.client.settings} core, where a test can ask it: the tabs and
 * their pages (M1, S2), the rows of a page (folders, the Presets heading and its Advanced fold, M16.1, M22.3), each
 * setting's plain title and summary (M21) and its editor (1.8), the field's text (M20), the hover (M19), what is
 * changed, refused and greyed (M6, M8, M9, S4, S6), search and "Changed only" (M2, C5), the presets and what they would
 * change (M22, S5), the header's actions (M15), and the save.
 *
 * <p>
 * Every value is held by the shared {@link Edits} under {@code category.key}, as the screen shows it: a number as its
 * own shortest decimal, a list as its lines joined by a line end. Nothing reaches the file until {@link #save}.
 */
public final class SettingsModel {

    /** The words of the language file, as the screen asks them. */
    public interface Words {

        String words(String key, Object... args);

        /** Whether the language file holds the key at all. */
        boolean has(String key);
    }

    /** One setting, as the screen shows it. */
    public static final class Setting {

        public final String category;

        public final String key;

        /** {@code category.key}: its id in {@link Edits}. */
        public final String id;

        public final ConfigFile.Setting property;

        public final ConfigLayout.Editor editor;

        /** {@code 'I'}, {@code 'F'}, {@code 'D'} for a number; 0 for anything else. */
        public final char kind;

        /** The tab it is on and the page of that tab (the category for a family's setting, or the tab's own). */
        public final String tab;

        public final String page;

        /** Its place in screen order. */
        public final int order;

        public final Takes takes;

        Setting(String category, String key, ConfigFile.Setting property, String tab, String page, int order) {
            this.category = category;
            this.key = key;
            this.id = category + "." + key;
            this.property = property;
            this.editor = ConfigLayout.editorFor(category, key, property);
            this.kind = ConfigLayout.numberKind(category, key, property);
            this.tab = tab;
            this.page = page;
            this.order = order;
            // This edition's file has no world-restart mark (nor does any of 1.7.10's settings ask for one).
            this.takes = Takes.of(property.requiresMcRestart(), false, property.getComment());
        }

        public boolean isNumber() {
            return editor == ConfigLayout.Editor.NUMBER || editor == ConfigLayout.Editor.SLIDER;
        }
    }

    /** What a row of the list is. Never an action: the actions are the header's (spec M15). */
    public enum RowType {
        /** One setting. */
        SETTING,
        /** A sub-category's heading. */
        FOLDER,
        /** A sentence heading a page: the Presets tab's (spec M22.3). */
        NOTE,
        /** The Presets tab's Advanced fold: what Custom brings back (spec M16.2). */
        ADVANCED,
        /** The fold's rows, opened. */
        REMEMBERED,
        /** A line in the panel's middle saying there is nothing: no search result, nothing changed. */
        EMPTY
    }

    /** One row of the list. */
    public static final class Row {

        public final RowType type;

        public final Setting setting;

        /** A folder's or a note's words. */
        public final String text;

        /** A search result's tab, drawn above it; null on a tab's own page. */
        public final String kicker;

        /** The parts of the title a search matched, drawn gold. */
        public final List<int[]> spans;

        Row(RowType type, Setting setting, String text, String kicker, List<int[]> spans) {
            this.type = type;
            this.setting = setting;
            this.text = text;
            this.kicker = kicker;
            this.spans = spans == null ? Collections.<int[]>emptyList() : spans;
        }

        @Override
        public String toString() {
            return type + (setting != null ? " " + setting.id : "") + (text != null ? " " + text : "");
        }
    }

    /** One line of a hover, with its color. */
    public static final class Line {

        public final String text;

        public final int color;

        public final boolean shiftIcon;

        Line(String text, int color) {
            this(text, color, false);
        }

        Line(String text, int color, boolean shiftIcon) {
            this.text = text;
            this.color = color;
            this.shiftIcon = shiftIcon;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** The header's actions, by id, right to left as they take the slots (spec M15). */
    public static final String HEADER_TOGGLE = "toggle";

    public static final String HEADER_WEARTABLE = "weartable";

    public static final String HEADER_PUSH = "push";

    private final ConfigFile config;

    private final Words words;

    private final List<String> tabs = new ArrayList<String>();

    private final Map<String, List<String>> pages = new LinkedHashMap<String, List<String>>();

    private final List<Setting> settings = new ArrayList<Setting>();

    private final Map<String, Setting> byId = new LinkedHashMap<String, Setting>();

    private final Edits edits;

    /** Typed values that do not read, by id, and why; each holds Save (spec M6). */
    private final Map<String, NumberWords.Refusal> refused = new LinkedHashMap<String, NumberWords.Refusal>();

    private final SettingsIndex index;

    /** Whether a world is open: the header's two world actions, and a world-restart setting greyed (M13.4). */
    private boolean world;

    /** Whether there is another settings screen to switch to (M14.5). */
    private boolean otherScreen = true;

    /** A server's values standing over this client's, by id, handed in for a photograph; null to ask the rules. */
    private Map<String, String> lockedHandedIn;

    public SettingsModel(ConfigFile config, Words words) {
        this.config = config;
        this.words = words;
        Map<String, String> opening = new LinkedHashMap<String, String>();
        Map<String, String> defaults = new LinkedHashMap<String, String>();
        List<SettingsIndex.Entry> entries = new ArrayList<SettingsIndex.Entry>();
        if (config != null) {
            Collection<String> inFile = config.getCategoryNames();
            tabs.addAll(ConfigLayout.tabs(inFile));
            for (String tab : tabs) {
                List<String> tabPages = TrmtConfig.CATEGORY_SURFACES.equals(tab) ? ConfigLayout.subTabs(inFile)
                    : Collections.singletonList(tab);
                pages.put(tab, tabPages);
                for (String page : tabPages) {
                    if (!config.hasCategory(page)) continue;
                    add(config.getCategory(page), tab, page, opening, defaults, entries);
                    for (ConfigFile.Category inside : children(config, page)) {
                        add(inside, tab, page, opening, defaults, entries);
                    }
                }
            }
        }
        this.edits = new Edits(opening, defaults);
        this.index = new SettingsIndex(entries);
    }

    /**
     * A category's folders, in the file's order: the categories one level under it ({@code presets.remembered} under
     * {@code presets}), as Forge's {@code ConfigCategory.getChildren} hands them over in the 1.7.10 edition.
     */
    static List<ConfigFile.Category> children(ConfigFile config, String name) {
        List<ConfigFile.Category> out = new ArrayList<ConfigFile.Category>();
        String prefix = name + ConfigFile.CATEGORY_SPLITTER;
        for (String each : new ArrayList<String>(config.getCategoryNames())) {
            if (!each.startsWith(prefix) || each.indexOf(ConfigFile.CATEGORY_SPLITTER, prefix.length()) >= 0) continue;
            out.add(config.getCategory(each));
        }
        return out;
    }

    private void add(ConfigFile.Category category, String tab, String page, Map<String, String> opening,
        Map<String, String> defaults, List<SettingsIndex.Entry> entries) {
        String name = category.getName();
        // The declared order, as 1.7.10's file keeps it, and the multipliers in their own (ConfigLayout.ordered).
        for (String key : ConfigLayout.ordered(name, category.keySet())) {
            ConfigFile.Setting property = category.get(key);
            if (property == null) continue;
            Setting setting = new Setting(name, property.getName(), property, tab, page, settings.size());
            settings.add(setting);
            byId.put(setting.id, setting);
            opening.put(setting.id, fileValue(setting, property.isList() ? null : property.getString(), false));
            defaults.put(setting.id, fileValue(setting, property.isList() ? null : property.getDefault(), true));
            if (setting.editor != ConfigLayout.Editor.REMEMBERED) {
                entries.add(
                    new SettingsIndex.Entry(
                        kicker(page),
                        name,
                        setting.key,
                        title(setting),
                        // The key typed whole as well as by its parts: the shared search splits a field's words at
                        // camelCase but a query only at spaces, so "maxWearSprites" from the file found nothing.
                        plainSummary(setting) + " " + setting.key.toLowerCase(java.util.Locale.ROOT),
                        ConfigWords.withoutRange(property.getComment()),
                        setting.order));
            }
        }
    }

    /**
     * A value as the screen holds it: a list's lines joined by line ends, a number as its own shortest decimal (spec
     * M20: a float the file holds as {@code 0.10000000149011612} is 0.1), anything else as written.
     */
    private static String fileValue(Setting setting, String single, boolean asDefault) {
        ConfigFile.Setting property = setting.property;
        if (property.isList()) {
            String[] lines = asDefault ? property.getDefaultList() : property.getStringList();
            return lines == null ? "" : join(lines);
        }
        if (setting.kind != 0) return NumberWords.shown(single, setting.kind);
        return single == null ? "" : single;
    }

    static String join(String[] lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append('\n');
            out.append(lines[i] == null ? "" : lines[i]);
        }
        return out.toString();
    }

    /** A list's lines, as held now. */
    public static List<String> lines(String value) {
        List<String> out = new ArrayList<String>();
        if (value == null || value.isEmpty()) return out;
        for (String line : value.split("\n", -1)) out.add(line);
        return out;
    }

    // ------------------------------------------------------------------
    // Tabs and pages
    // ------------------------------------------------------------------

    public List<String> tabs() {
        return Collections.unmodifiableList(tabs);
    }

    /** A tab's pages: Surfaces' sub-tabs, or the tab alone. */
    public List<String> pages(String tab) {
        List<String> held = pages.get(tab);
        return held == null ? Collections.<String>emptyList() : held;
    }

    public String tabLabel(String tab) {
        return words.words(SettingsKeys.OWN + "tab." + tab);
    }

    /** A sub-tab's label: "All surfaces", or the family's name. */
    public String pageLabel(String page) {
        if (TrmtConfig.CATEGORY_SURFACES.equals(page)) return words.words(SettingsKeys.FAMILY_ALL);
        if (page.startsWith(TrmtConfig.CATEGORY_FAMILIES + ConfigFile.CATEGORY_SPLITTER)) {
            return words.words(SettingsKeys.OWN + "family." + ConfigWords.leaf(page));
        }
        return tabLabel(page);
    }

    /** A setting's page as a search result's kicker names it: "General", or "Surfaces - Grass" (spec M2.1). */
    public String kicker(String page) {
        for (Map.Entry<String, List<String>> tab : pages.entrySet()) {
            if (!tab.getValue()
                .contains(page)) continue;
            if (tab.getValue()
                .size() > 1
                || !tab.getKey()
                    .equals(page)) {
                return tabLabel(tab.getKey()) + " - " + pageLabel(page);
            }
            return tabLabel(page);
        }
        return pageLabel(page);
    }

    /** The family a sub-tab is, or null for "All surfaces" and every other page. */
    public static SurfaceFamily familyOf(String page) {
        String prefix = TrmtConfig.CATEGORY_FAMILIES + ConfigFile.CATEGORY_SPLITTER;
        if (page == null || !page.startsWith(prefix)) return null;
        return SurfaceFamily.byKey(page.substring(prefix.length()));
    }

    /**
     * The rows of a page (spec 1.2): its settings, then each folder under its heading. Presets opens with its
     * comment's first sentence (M22.3) and ends with the Advanced fold in place of {@code presets.remembered}'s rows
     * (M16.1), opened with {@code advancedOpen}.
     */
    public List<Row> rows(String page, boolean advancedOpen) {
        List<Row> out = new ArrayList<Row>();
        if (config == null || !config.hasCategory(page)) return out;
        ConfigFile.Category category = config.getCategory(page);
        if (Presets.CATEGORY.equals(page)) {
            String first = firstSentence(category.getComment());
            if (!first.isEmpty()) out.add(new Row(RowType.NOTE, null, first, null, null));
        }
        addSettings(out, category.getName());
        for (ConfigFile.Category inside : children(config, page)) {
            String name = inside.getName();
            if (name.equals(ConfigLayout.REMEMBERED)) {
                out.add(new Row(RowType.ADVANCED, null, words.words(SettingsKeys.ADVANCED), null, null));
                if (advancedOpen) out.add(new Row(RowType.REMEMBERED, null, null, null, null));
                continue;
            }
            int before = out.size();
            out.add(new Row(RowType.FOLDER, null, ConfigWords.title(ConfigWords.leaf(name)), null, null));
            addSettings(out, name);
            if (out.size() == before + 1) out.remove(before);
        }
        return out;
    }

    private void addSettings(List<Row> out, String category) {
        for (Setting setting : settings) {
            if (setting.category.equals(category) && setting.editor != ConfigLayout.Editor.REMEMBERED) {
                out.add(new Row(RowType.SETTING, setting, null, null, null));
            }
        }
    }

    /** A text's first sentence: up to and with its first full stop followed by a space, or the whole. */
    static String firstSentence(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        int at = trimmed.indexOf(". ");
        return at < 0 ? trimmed : trimmed.substring(0, at + 1);
    }

    /** Every setting a tab holds, across its pages. */
    public List<String> idsOf(String tab) {
        List<String> out = new ArrayList<String>();
        for (Setting setting : settings) {
            if (setting.tab.equals(tab)) out.add(setting.id);
        }
        return out;
    }

    /** Every setting a page holds, its folders and its remembered values included. */
    public List<String> idsOfPage(String page) {
        List<String> out = new ArrayList<String>();
        for (Setting setting : settings) {
            if (setting.page.equals(page)) out.add(setting.id);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Search and "Changed only" (M2, C5)
    // ------------------------------------------------------------------

    /**
     * The rows that stand for a tab while something is searched or "Changed only" is on: every setting on every tab
     * whose words match the query (best first, M2.3) and, with the filter, that is not at its default (C5); each
     * headed by its tab as a kicker. Null when neither is asked, so the tab's own page shows.
     */
    public List<Row> results(String query, boolean changedOnly) {
        boolean searching = query != null && !query.trim()
            .isEmpty();
        if (!searching && !changedOnly) return null;
        List<Row> out = new ArrayList<Row>();
        if (searching) {
            for (SettingsIndex.Hit hit : index.find(query)) {
                Setting setting = byId.get(hit.entry.id());
                if (setting == null || changedOnly && edits.atDefault(setting.id)) continue;
                out.add(new Row(RowType.SETTING, setting, null, hit.entry.tab, hit.nameSpans));
            }
            if (out.isEmpty()) out.add(
                new Row(
                    RowType.EMPTY,
                    null,
                    changedOnly ? words.words(SettingsKeys.FILTER_CHANGED_NONE)
                        : words.words(SettingsKeys.SEARCH_NONE, query.trim()),
                    null,
                    null));
            return out;
        }
        for (Setting setting : settings) {
            if (setting.editor == ConfigLayout.Editor.REMEMBERED || edits.atDefault(setting.id)) continue;
            out.add(new Row(RowType.SETTING, setting, null, kicker(setting.page), null));
        }
        if (out.isEmpty())
            out.add(new Row(RowType.EMPTY, null, words.words(SettingsKeys.FILTER_CHANGED_NONE), null, null));
        return out;
    }

    /** The count the search row shows (M2.7): "12 settings found". */
    public String resultCount(List<Row> results) {
        int found = 0;
        if (results != null) {
            for (Row row : results) {
                if (row.type == RowType.SETTING) found++;
            }
        }
        return found == 1 ? words.words(SettingsKeys.SEARCH_COUNT_ONE)
            : words.words(SettingsKeys.SEARCH_COUNT_MANY, found);
    }

    // ------------------------------------------------------------------
    // What a row says
    // ------------------------------------------------------------------

    public Setting setting(String id) {
        return byId.get(id);
    }

    public List<Setting> settings() {
        return Collections.unmodifiableList(settings);
    }

    /** A setting's plain title (spec M21), from the language file; its key spaced out only where it has none. */
    public String title(Setting setting) {
        String key = SettingsKeys.title(setting.category, setting.key);
        return words.has(key) ? words.words(key) : ConfigWords.title(setting.key);
    }

    /** A setting's own one-line summary (spec M21), or nothing where the language file has none. */
    private String plainSummary(Setting setting) {
        String key = SettingsKeys.summary(setting.category, setting.key);
        return words.has(key) ? words.words(key) : "";
    }

    /**
     * The line under a setting's title (spec 1.2): its summary - and on a preset row its chosen value's own line,
     * so "Default" never stands alone (M22.1).
     */
    public String summary(Setting setting) {
        if (setting.editor == ConfigLayout.Editor.CHOOSER) {
            String line = SettingsKeys.optionLine(setting.id, edits.value(setting.id));
            if (Presets.CATEGORY.equals(setting.category) && words.has(line)) return words.words(line);
        }
        return plainSummary(setting);
    }

    /**
     * Whether a row draws its Reset: every row does, greyed at its default (M8.1) - but a preset row only off its
     * default, so five rows at "Default" no longer each carry a Reset (M22.4).
     */
    public boolean showsReset(Setting setting) {
        if (Presets.CATEGORY.equals(setting.category)) return !edits.atDefault(setting.id);
        return true;
    }

    /** What a setting holds now, as its control shows it. */
    public String value(Setting setting) {
        return edits.value(setting.id);
    }

    /** A chooser value's own title, the file's word only where the language file has none. */
    public String optionTitle(Setting setting, String value) {
        String key = SettingsKeys.option(setting.id, value);
        return words.has(key) ? words.words(key) : value;
    }

    /** Whether a setting is on (a switch). */
    public boolean on(Setting setting) {
        return Boolean.parseBoolean(value(setting));
    }

    /** The range line under a focused number (spec M6.1): "0 to 100, default 2", or "Default 1.5". */
    public String range(Setting setting) {
        ConfigFile.Setting property = setting.property;
        String def = NumberWords.shownBound(property.getDefault());
        if (NumberWords.bounded(
            false,
            property.getType()
                .name(),
            ConfigLayout.minOf(property),
            ConfigLayout.maxOf(property),
            property.getDefault())) {
            return words.words(
                SettingsKeys.RANGE,
                NumberWords.shownBound(ConfigLayout.minOf(property)),
                NumberWords.shownBound(ConfigLayout.maxOf(property)),
                def);
        }
        return words.words(SettingsKeys.DEFAULT, def);
    }

    /** The values a chooser offers, in the file's order. */
    public List<String> choices(Setting setting) {
        String[] valid = setting.property.getValidValues();
        List<String> out = new ArrayList<String>();
        if (valid != null) Collections.addAll(out, valid);
        return out;
    }

    // ------------------------------------------------------------------
    // States (spec 1.4)
    // ------------------------------------------------------------------

    public Edits edits() {
        return edits;
    }

    public void world(boolean world) {
        this.world = world;
    }

    public boolean world() {
        return world;
    }

    public void otherScreen(boolean exists) {
        this.otherScreen = exists;
    }

    /** A server's values handed in, for a photograph (spec S4's hook); nothing reaches the rules. */
    public void locked(Map<String, String> values) {
        this.lockedHandedIn = values == null ? null : new HashMap<String, String>(values);
    }

    /**
     * The value a server holds a setting at, or null (spec S4). A client setting is never locked, but the one a
     * server's rules carry ({@link ServerRules#serverCarries}).
     */
    public String locked(Setting setting) {
        if (!ServerRules.serverCarries(setting.category, setting.key)) return null;
        if (lockedHandedIn != null) return lockedHandedIn.get(setting.id);
        return ServerRules.lockedValue(setting.category, setting.key);
    }

    /** The switch a setting waits on, where it is off (spec S6), or null. */
    public Greying.Rule greyedBy(Setting setting) {
        Greying.Rule rule = Greying.governing(setting.category, setting.key);
        if (rule == null) return null;
        String on = edits.value(rule.switchSetting());
        if (on == null) return null;
        return Greying.greyed(rule, Boolean.parseBoolean(on)) ? rule : null;
    }

    /** Whether a setting is greyed: by its switch (S6), or as a world-restart setting while a world is open (M13.4). */
    public boolean greyed(Setting setting) {
        return greyedBy(setting) != null || world && setting.takes == Takes.WORLD;
    }

    /** Whether its control takes input: not greyed, not locked. */
    public boolean enabled(Setting setting) {
        return !greyed(setting) && locked(setting) == null;
    }

    public NumberWords.Refusal refusal(String id) {
        return refused.get(id);
    }

    public int refusedCount() {
        return refused.size();
    }

    /** The refusal's words: "Below 0", "Not a number". */
    public String refusalWords(NumberWords.Refusal refusal) {
        return refusal.arg == null ? words.words(refusal.key) : words.words(refusal.key, refusal.arg);
    }

    /** A tab's dot (spec 1.4): red while any of its settings is refused, else gold while any is changed. */
    public int dot(Collection<String> ids) {
        boolean changed = false;
        for (String id : ids) {
            if (refused.containsKey(id)) return TabStrip.DOT_REFUSED;
            if (edits.changed(id)) changed = true;
        }
        return changed ? TabStrip.DOT_CHANGED : TabStrip.DOT_NONE;
    }

    /** What Done reads (spec M6.3, M9.2): "Fix 2 first", "Save 3 changes", or the game's own "Done". */
    public String saveLabel() {
        int refusedNow = refused.size();
        if (refusedNow == 1) return words.words(SettingsKeys.FIX_ONE);
        if (refusedNow > 1) return words.words(SettingsKeys.FIX_MANY, refusedNow);
        int changed = edits.changedCount();
        if (changed == 1) return words.words(SettingsKeys.SAVE_ONE);
        if (changed > 1) return words.words(SettingsKeys.SAVE_MANY, changed);
        return words.words(SettingsKeys.GUI_DONE);
    }

    /** Whether Save is held: while any value is refused, and only then (M6.3; red names never hold it, M3.3). */
    public boolean saveHeld() {
        return !refused.isEmpty();
    }

    /** The banner's words while a value is refused (M6.3), or null. */
    public String banner() {
        int refusedNow = refused.size();
        if (refusedNow == 0) return null;
        return refusedNow == 1 ? words.words(SettingsKeys.BANNER_ONE)
            : words.words(SettingsKeys.BANNER_MANY, refusedNow);
    }

    /** Whether there is anything Cancel would throw away. */
    public boolean changed() {
        return edits.changedCount() > 0;
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    /**
     * Typed text: kept when it reads as the setting's kind and range, else refused with the reason and the last value
     * that read kept (spec M6.2, M7.5). Answers the refusal, or null.
     */
    public NumberWords.Refusal type(Setting setting, String text) {
        if (setting.isNumber()) {
            NumberWords.Refusal why = NumberWords
                .check(text, setting.kind, ConfigLayout.minOf(setting.property), ConfigLayout.maxOf(setting.property));
            if (why != null) {
                refused.put(setting.id, why);
                return why;
            }
            refused.remove(setting.id);
            edits.set(setting.id, text.trim());
            return null;
        }
        edits.set(setting.id, text);
        return null;
    }

    /**
     * Up, Down or the wheel on a focused number (spec M6.4): stepped by the setting's step, a refused value from its
     * default. Answers the new text.
     */
    public String step(Setting setting, String text, int direction, boolean shift, boolean ctrl) {
        String stepped = NumberWords.step(
            text,
            setting.kind,
            ConfigLayout.minOf(setting.property),
            ConfigLayout.maxOf(setting.property),
            NumberWords.shownBound(setting.property.getDefault()),
            direction,
            shift,
            ctrl);
        type(setting, stepped);
        return stepped;
    }

    /** A switch pressed. */
    public void toggle(Setting setting) {
        edits.set(setting.id, String.valueOf(!on(setting)));
    }

    /**
     * A chooser's value chosen. A preset is chosen with every setting it moves, as one step, so each of those rows is
     * marked changed and one Ctrl+Z takes the whole choice back (spec S5.2).
     */
    public void choose(Setting setting, String value) {
        if (!Presets.CATEGORY.equals(setting.category)) {
            edits.set(setting.id, value);
            return;
        }
        Map<String, String> group = new LinkedHashMap<String, String>();
        group.put(setting.id, value);
        group.putAll(presetValues(setting.key, value));
        edits.group(group);
    }

    /** A row's Reset: its default (M8.1). */
    public void reset(Setting setting) {
        refused.remove(setting.id);
        edits.set(setting.id, edits.defaultOf(setting.id));
    }

    /** A row's undo arrow: what the screen opened with (M8.2). */
    public void revert(Setting setting) {
        refused.remove(setting.id);
        edits.revert(setting.id);
    }

    /** Undo all (M8.4). */
    public void revertAll() {
        refused.clear();
        edits.revertAll();
    }

    /** Reset tab... once asked (M8.5): every setting of the page to its default, as one step. */
    public void resetPage(String page) {
        List<String> ids = new ArrayList<String>();
        for (String id : idsOfPage(page)) {
            Setting setting = byId.get(id);
            if (setting != null && setting.editor != ConfigLayout.Editor.REMEMBERED) ids.add(id);
        }
        for (String id : ids) refused.remove(id);
        edits.resetAll(ids);
    }

    /** Ctrl+Z with nothing focused (M8.3): the last step back. */
    public void undoLast() {
        for (String id : edits.undoLast()) refused.remove(id);
    }

    // ------------------------------------------------------------------
    // Presets (M22, S5)
    // ------------------------------------------------------------------

    /**
     * What choosing a preset's rung would set, by id, as the screen holds values: the five picture settings for
     * quality, and each wearing family's figures for the others, from the preset machinery's own arithmetic
     * ({@link Presets#quality}, {@link Presets#previewed}). Nothing for custom, which keeps the numbers there are.
     */
    public Map<String, String> presetValues(String axis, String rung) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (rung == null || Presets.CUSTOM.equals(rung)) return out;
        if ("quality".equals(axis)) {
            int[] q = Presets.quality(rung);
            out.put(TrmtConfig.CATEGORY_CLIENT + ".wearGradations", String.valueOf(q[0]));
            out.put(TrmtConfig.CATEGORY_CLIENT + ".wearRotations", String.valueOf(q[1]));
            out.put(TrmtConfig.CATEGORY_CLIENT + ".maxWearSprites", String.valueOf(q[2]));
            out.put(TrmtConfig.CATEGORY_SURFACES + ".maxTexturedSurfaces", String.valueOf(q[3]));
            out.put(TrmtConfig.CATEGORY_CLIENT + ".perSurfaceTextures", String.valueOf(q[4] != 0));
            return keepKnown(out);
        }
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged || TrmtConfig.family(family) == null) continue;
            String category = ConfigLayout.familyCategory(family);
            Presets.Figures figures = Presets.previewed(family, axis, rung);
            if ("wear".equals(axis)) {
                out.put(category + ".thresholdMin", decimal(figures.thresholdMin));
                out.put(category + ".thresholdMax", decimal(figures.thresholdMax));
            } else if ("healing".equals(axis)) {
                out.put(category + ".healDaysPerStage", NumberWords.number(figures.healDaysPerStage));
            } else if ("phases".equals(axis)) {
                out.put(category + ".stages", String.valueOf(figures.stages));
                out.put(category + ".layersPerDepth", String.valueOf(figures.layersPerDepth));
            } else if ("depth".equals(axis)) {
                out.put(category + ".maxSinkPixels", String.valueOf(figures.maxSinkPixels));
            }
        }
        return keepKnown(out);
    }

    private static String decimal(float value) {
        return NumberWords.number(Double.parseDouble(Float.toString(value)));
    }

    private Map<String, String> keepKnown(Map<String, String> values) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> one : values.entrySet()) {
            if (byId.containsKey(one.getKey())) out.put(one.getKey(), one.getValue());
        }
        return out;
    }

    /** At most this many changes are listed in a preset's detail (spec S5.1). */
    static final int PRESET_LINES = 12;

    /**
     * What a preset's detail panel lists for a rung (spec S5.1): "Choosing Low changes 5 settings:", then one line a
     * change - its plain title, the value now and the rung's - at most twelve, or "Choosing Default changes nothing".
     */
    public List<String> presetChanges(Setting preset, String rung) {
        List<String> out = new ArrayList<String>();
        Map<String, String> wanted = presetValues(preset.key, rung);
        if (wanted.isEmpty()) return out;
        List<PresetDiff.Change> changes = PresetDiff.diff(edits.values(), wanted, new ArrayList<String>(byId.keySet()));
        String named = optionTitle(preset, rung);
        if (changes.isEmpty()) {
            out.add(words.words(SettingsKeys.PRESET_NONE, named));
            return out;
        }
        out.add(
            changes.size() == 1 ? words.words(SettingsKeys.PRESET_CHANGES_ONE, named)
                : words.words(SettingsKeys.PRESET_CHANGES_MANY, named, changes.size()));
        for (int i = 0; i < changes.size() && i < PRESET_LINES; i++) {
            PresetDiff.Change change = changes.get(i);
            Setting setting = byId.get(change.id);
            out.add(
                words.words(
                    SettingsKeys.PRESET_LINE,
                    setting == null ? change.id : named(setting),
                    said(change.from),
                    said(change.to)));
        }
        if (changes.size() > PRESET_LINES) out.add("...");
        return out;
    }

    /** A setting named with its family where it is a family's: "Grass - Wear starts at". */
    private String named(Setting setting) {
        SurfaceFamily family = familyOf(setting.category);
        return family == null ? title(setting) : pageLabel(setting.category) + " - " + title(setting);
    }

    private String said(String value) {
        if ("true".equals(value)) return words.words(SettingsKeys.OPTIONS_ON);
        if ("false".equals(value)) return words.words(SettingsKeys.OPTIONS_OFF);
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------
    // Hovers (M19, S8)
    // ------------------------------------------------------------------

    /**
     * A setting's hover (spec M19.2): its title, its summary, its range or default, the restart or reload line, any
     * state line, and - where it has a comment - "Hold Shift for the whole description". With Shift held, the whole
     * description instead (M19.3), the screen paging it.
     */
    /**
     * Whether a setting's whole description says more than its summary, so Shift has more to show: its comment holds
     * words its summary has not (letters and digits alone compared, case aside).
     */
    static boolean saysMore(String comment, String summary) {
        String whole = plainWords(comment);
        return !whole.isEmpty() && !plainWords(summary).contains(whole);
    }

    private static String plainWords(String text) {
        return text == null ? ""
            : text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    public List<Line> hover(Setting setting, boolean whole) {
        List<Line> out = new ArrayList<Line>();
        out.add(new Line(title(setting), Canvas.GOLD));
        String comment = ConfigWords.withoutRange(setting.property.getComment());
        String range = setting.property.isList() ? ""
            : NumberWords.rangeLine(
                false,
                setting.property.getType()
                    .name(),
                ConfigLayout.minOf(setting.property),
                ConfigLayout.maxOf(setting.property),
                setting.property.getDefault());
        if (whole) {
            if (!comment.isEmpty()) out.add(new Line(comment, Canvas.BODY));
            if (!range.isEmpty()) out.add(new Line(range, Canvas.GREY));
            return out;
        }
        String summary = summary(setting);
        if (!summary.isEmpty()) out.add(new Line(summary, Canvas.BODY));
        out.addAll(qualitySets(setting));
        if (!range.isEmpty()) out.add(new Line(range, Canvas.GREY));
        if (setting.takes == Takes.RESTART) out.add(new Line(words.words(SettingsKeys.RESTART_MARK), Canvas.GOLD));
        if (setting.takes == Takes.RELOAD) out.add(new Line(words.words(SettingsKeys.RELOAD_MARK), Canvas.GOLD));
        if (setting.takes == Takes.WORLD && world) {
            out.add(new Line(words.words(SettingsKeys.RESTART_MARK), Canvas.GOLD));
        }
        String locked = locked(setting);
        if (locked != null) {
            out.add(new Line(words.words(SettingsKeys.LOCKED, locked), Canvas.GREY));
            out.add(new Line(words.words(SettingsKeys.LOCKED_NOTE), Canvas.GREY));
        }
        Greying.Rule rule = greyedBy(setting);
        if (rule != null) {
            Setting by = byId.get(rule.switchSetting());
            out.add(new Line(words.words(SettingsKeys.GREYED, by == null ? rule.switchKey : title(by)), Canvas.GREY));
        }
        Greying.Rule partial = Greying.governing(setting.category, setting.key);
        if (partial != null && Greying.explained(partial, Boolean.parseBoolean(edits.value(partial.switchSetting())))) {
            out.add(new Line(words.words(SettingsKeys.GREYED_PARTIAL), Canvas.GREY));
        }
        NumberWords.Refusal refusal = refused.get(setting.id);
        if (refusal != null) out.add(new Line(refusalWords(refusal), Canvas.RED));
        if (setting.editor == ConfigLayout.Editor.CHOOSER) {
            out.add(new Line(words.words(SettingsKeys.CYCLE_TIP), Canvas.GREY));
        }
        if (!otherScreen && TrmtConfig.CATEGORY_CLIENT.equals(setting.category) && "settingsScreen".equals(setting.key)) {
            // No other screen in this game (spec M14.5): the toggle is not drawn, and the setting says why.
            out.add(new Line(words.words(SettingsKeys.TOGGLE_NONE), Canvas.GREY));
        }
        // The hint only where Shift shows more than this (0.9.228): a comment that says no more than the summary.
        if (saysMore(comment, summary)) out.add(new Line(words.words(SettingsKeys.HOVER_MORE), Canvas.GREY, true));
        return out;
    }

    /**
     * What the quality preset sets at the rung it holds (spec S3.4): each of its five picture settings by its plain
     * title, at the rung's value, from the preset machinery's own numbers. Nothing for Custom, which sets nothing, nor
     * for any other setting.
     */
    List<Line> qualitySets(Setting setting) {
        List<Line> out = new ArrayList<Line>();
        if (!Presets.CATEGORY.equals(setting.category) || !"quality".equals(setting.key)) return out;
        for (Map.Entry<String, String> one : presetValues("quality", edits.value(setting.id)).entrySet()) {
            Setting set = byId.get(one.getKey());
            out.add(new Line((set == null ? one.getKey() : named(set)) + ": " + said(one.getValue()), Canvas.BODY));
        }
        return out;
    }

    /** Room kept at a line's left for the Shift key's icon. */
    static final int SHIFT_ICON_ROOM = 18;

    /**
     * A hover's lines wrapped to the width a hover may take on a screen this wide (spec M19.1), each keeping its color.
     */
    public static List<Line> wrap(Canvas canvas, List<Line> lines, int screenW) {
        // The box adds its padding either side of the text, so the text takes that much less: on a screen narrower
        // than a hover's widest, the whole box then keeps the 8-pixel margin (M19.1), not only its words.
        int width = HoverText.width(screenW);
        List<Line> out = new ArrayList<Line>();
        for (Line line : lines) {
            int room = width - (line.shiftIcon ? SHIFT_ICON_ROOM : 0);
            boolean first = true;
            for (String cut : HoverText.wrap(canvas, Collections.singletonList(line.text), room)) {
                out.add(new Line(cut, line.color, first && line.shiftIcon));
                first = false;
            }
        }
        return out;
    }

    /** The lines' words alone. */
    public static List<String> texts(List<Line> lines) {
        List<String> out = new ArrayList<String>();
        for (Line line : lines) out.add(line.shiftIcon ? "    " + line.text : line.text);
        return out;
    }

    /**
     * A tab's hover (spec M1.8, S8): its name, its dot's words, and its category's own comment; a sub-tab's carries
     * its family's.
     */
    public List<Line> tabHover(String label, String category, Collection<String> ids) {
        List<Line> out = new ArrayList<Line>();
        out.add(new Line(label, Canvas.GOLD));
        int refusedHere = 0;
        for (String id : ids) {
            if (refused.containsKey(id)) refusedHere++;
        }
        int changedHere = edits.changedIn(ids);
        if (refusedHere > 0) {
            out.add(
                new Line(
                    refusedHere == 1 ? words.words(SettingsKeys.TAB_REFUSED_ONE)
                        : words.words(SettingsKeys.TAB_REFUSED_MANY, refusedHere),
                    Canvas.RED));
        }
        if (changedHere > 0) {
            out.add(
                new Line(
                    changedHere == 1 ? words.words(SettingsKeys.TAB_CHANGED_ONE)
                        : words.words(SettingsKeys.TAB_CHANGED_MANY, changedHere),
                    Canvas.GOLD));
        }
        String comment = config == null || !config.hasCategory(category) ? null
            : config.getCategory(category)
                .getComment();
        if (comment != null && !comment.trim()
            .isEmpty()) out.add(new Line(comment.trim(), Canvas.BODY));
        return out;
    }

    /** The help button's hover (spec 1.6): the keyboard map. */
    public List<Line> keysHover() {
        List<Line> out = new ArrayList<Line>();
        out.add(new Line(words.words(SettingsKeys.HELP_TITLE), Canvas.GOLD));
        String[] keys = { SettingsKeys.HELP_1, SettingsKeys.HELP_2, SettingsKeys.HELP_3, SettingsKeys.HELP_4,
            SettingsKeys.HELP_5, SettingsKeys.HELP_6, SettingsKeys.HELP_7, SettingsKeys.HELP_8, SettingsKeys.HELP_9 };
        for (String key : keys) out.add(new Line(words.words(key), Canvas.BODY));
        return out;
    }

    // ------------------------------------------------------------------
    // The header (M15)
    // ------------------------------------------------------------------

    /**
     * The header's actions, right to left as they take its slots: the toggle where another screen exists (M14.5),
     * then the Wear Table and Push to server where a world is open to use them in (today's conditions). Never a row.
     */
    public List<String> header() {
        List<String> out = new ArrayList<String>();
        if (otherScreen) out.add(HEADER_TOGGLE);
        if (world) {
            out.add(HEADER_WEARTABLE);
            out.add(HEADER_PUSH);
        }
        return out;
    }

    /** A header action's hover: its words, then what it does. */
    public List<Line> headerHover(String action, String otherName) {
        List<Line> out = new ArrayList<Line>();
        if (HEADER_TOGGLE.equals(action)) {
            out.add(new Line(otherName, Canvas.GOLD));
            out.add(new Line(words.words(SettingsKeys.TOGGLE_TIP, otherName), Canvas.BODY));
        } else if (HEADER_WEARTABLE.equals(action)) {
            out.add(new Line(words.words(SettingsKeys.HEADER_WEARTABLE), Canvas.GOLD));
            out.add(new Line(words.words(SettingsKeys.HEADER_WEARTABLE_TIP), Canvas.BODY));
        } else if (HEADER_PUSH.equals(action)) {
            out.add(new Line(words.words(SettingsKeys.HEADER_PUSH), Canvas.GOLD));
            out.add(new Line(words.words(SettingsKeys.HEADER_PUSH_TIP), Canvas.BODY));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    /** What a save changed that waits for something: a restart, a resource reload. */
    public static final class Saved {

        public final int written;

        public final boolean restart;

        public final boolean reload;

        Saved(int written, boolean restart, boolean reload) {
            this.written = written;
            this.restart = restart;
            this.reload = reload;
        }
    }

    /**
     * Every changed value written into its setting, as its own kind, the way Forge's screen writes it. Nothing while a
     * value is refused. A preset chosen this visit is written as the chooser alone where its figures still hold the
     * rung's numbers: the preset machinery applies them on the reload, and keeps the custom numbers it is moving from
     * only if the file still holds them then.
     */
    public Saved save() {
        if (!refused.isEmpty()) return new Saved(0, false, false);
        List<String> skip = new ArrayList<String>();
        for (Setting setting : settings) {
            if (!Presets.CATEGORY.equals(setting.category) || setting.editor != ConfigLayout.Editor.CHOOSER) continue;
            if (!edits.changed(setting.id)) continue;
            for (Map.Entry<String, String> figure : presetValues(setting.key, edits.value(setting.id)).entrySet()) {
                if (Edits.same(edits.value(figure.getKey()), figure.getValue())) skip.add(figure.getKey());
            }
        }
        int written = 0;
        boolean restart = false;
        boolean reload = false;
        for (String id : edits.changedIds()) {
            Setting setting = byId.get(id);
            if (setting == null || skip.contains(id)) continue;
            write(setting.property, edits.value(id));
            written++;
            restart |= setting.takes == Takes.RESTART;
            reload |= setting.takes == Takes.RELOAD;
        }
        return new Saved(written, restart, reload);
    }

    /** One value written as its own kind: a list split at its line ends, a number as a number. */
    static void write(ConfigFile.Setting setting, String value) {
        if (setting.isList()) {
            List<String> lines = lines(value);
            setting.set(lines.toArray(new String[lines.size()]));
            return;
        }
        switch (setting.getType()) {
            case BOOLEAN:
                setting.set(Boolean.parseBoolean(value));
                return;
            case INTEGER:
                setting.set((int) ConfigWords.number(value, 0D));
                return;
            case DOUBLE:
                setting.set(ConfigWords.number(value, 0D));
                return;
            default:
                setting.set(value);
        }
    }
}
