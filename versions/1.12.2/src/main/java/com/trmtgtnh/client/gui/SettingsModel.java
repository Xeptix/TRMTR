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
import com.trmtgtnh.config.ClientSettingsHeld;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.FaithfulProfile;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.OriginalFile;
import com.trmtgtnh.config.Presets;
import com.trmtgtnh.config.ServerRules;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.CostCurve;
import com.trmtgtnh.original.FaithfulChains;
import com.trmtgtnh.original.OriginalSettings;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Everything the mod's own settings screen knows and decides, without drawing anything (0.9.228; spec Part 1).
 *
 * <p>
 * The screen itself ({@link WornPathScreen}) is a {@code GuiScreen}, which this edition cannot build in a unit test -
 * its parent draws items as it loads - so what it shows and what it does with a press lives here, over the mod's own
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
        /**
         * Faithful Mode's fold, under its row (0.9.229 W5, spec 2.7): opened, what it runs in place of the player's own
         * numbers, then the {@code faithful} heading's settings, editable there - it has no tab of its own.
         */
        FAITHFUL,
        /** One line of Faithful Mode's detail, opened under its fold. */
        DETAIL,
        /**
         * "Use your TRMT settings" (0.9.229 W5 T9, spec 2.8): shown only where the original mod's {@code trmt.json} was
         * found, and chosen as a preset's rung is, from a dropdown whose detail panel lists what choosing it changes
         * and what could not be matched ({@link #originalChanges}).
         */
        ORIGINAL,
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

    /** The wear backups' restore screen (0.9.229), in a world of one's own: its icon is the undo arrow. */
    public static final String HEADER_BACKUPS = "backups";

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
            // Faithful Mode's numbers are the presets tab's, under its fold, rather than a tab of their own (spec 2.7).
            if (pages.containsKey(Presets.CATEGORY) && config.hasCategory(FaithfulProfile.CATEGORY)) {
                ConfigFile.Category faithful = config.getCategory(FaithfulProfile.CATEGORY);
                add(faithful, Presets.CATEGORY, Presets.CATEGORY, opening, defaults, entries);
                for (ConfigFile.Category inside : children(config, FaithfulProfile.CATEGORY)) {
                    add(inside, Presets.CATEGORY, Presets.CATEGORY, opening, defaults, entries);
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
            // A setting a server holds opens at the value it is held at this visit, not the file's (0.9.229).
            String held = property.isList() ? null : ClientSettingsHeld.effective(name, property.getName());
            opening.put(
                setting.id,
                fileValue(setting, held != null ? held : property.isList() ? null : property.getString(), false));
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
        return rows(page, advancedOpen, false);
    }

    /**
     * The rows of a page, and on the presets tab Faithful Mode's fold under its row (spec 2.7), opened with
     * {@code faithfulOpen}: its detail a line a row, then the {@code faithful} heading's settings, each surface's
     * under its own folder; then, where the original mod's file was found, "Use your TRMT settings" (spec 2.8), a row
     * chosen as a preset's rung is.
     */
    public List<Row> rows(String page, boolean advancedOpen, boolean faithfulOpen) {
        List<Row> out = new ArrayList<Row>();
        if (config == null || !config.hasCategory(page)) return out;
        ConfigFile.Category category = config.getCategory(page);
        if (Presets.CATEGORY.equals(page)) {
            String first = firstSentence(category.getComment());
            if (!first.isEmpty()) out.add(new Row(RowType.NOTE, null, first, null, null));
        }
        addSettings(out, category.getName());
        if (Presets.CATEGORY.equals(page)) {
            int end = addFaithful(out, faithfulOpen);
            if (end >= 0 && originalFound()) {
                out.add(end, new Row(RowType.ORIGINAL, null, words.words(ORIGINAL_ROW), null, null));
            }
        }
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
        // The page's Advanced folder, last (0.9.229; Xep, 2026-11-18): settings most players never need, kept where
        // the file has them, so a setting moved here keeps its key.
        int before = out.size();
        out.add(new Row(RowType.FOLDER, null, words.words(SettingsKeys.FOLDER_ADVANCED), null, null));
        for (Setting setting : settings) {
            if (setting.category.equals(category.getName()) && SettingsKeys.inAdvanced(setting.id)) {
                out.add(new Row(RowType.SETTING, setting, null, null, null));
            }
        }
        if (out.size() == before + 1) out.remove(before);
        return out;
    }

    private void addSettings(List<Row> out, String category) {
        for (Setting setting : settings) {
            if (setting.category.equals(category) && setting.editor != ConfigLayout.Editor.REMEMBERED
                && !SettingsKeys.inAdvanced(setting.id)) {
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

    /** Whether the world open is one's own, with its wear backups running: the restore screen is offered there. */
    private boolean ownWorld;

    public void ownWorld(boolean own) {
        this.ownWorld = own;
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
     * The value a server holds a setting at, or null (spec S4). A client setting is locked where the server forces
     * it (0.9.229; Xep, 2026-11-18: "lock + one chat line"), and the one a server's own rules carry
     * ({@link ServerRules#serverCarries}).
     */
    public String locked(Setting setting) {
        String forced = ClientSettingsHeld.forced(setting.category, setting.key);
        if (forced != null) return fileValue(setting, forced, false);
        if (!ServerRules.serverCarries(setting.category, setting.key)) return null;
        if (lockedHandedIn != null) return lockedHandedIn.get(setting.id);
        return ServerRules.lockedValue(setting.category, setting.key);
    }

    /** The value a server suggests for a setting, where it suggests one: the player may change it, for the visit. */
    public String suggested(Setting setting) {
        if (!ClientSettingsHeld.suggested(setting.category, setting.key)) return null;
        String value = ClientSettingsHeld.suggestion(setting.category, setting.key);
        return value == null ? null : fileValue(setting, value, false);
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
        if (FaithfulProfile.SWITCH.equals(axis)) return Boolean.parseBoolean(rung) ? keepKnown(faithfulValues()) : out;
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
        if (FaithfulProfile.SWITCH.equals(preset.key)) {
            return Boolean.parseBoolean(rung) ? faithfulChanges() : new ArrayList<String>();
        }
        List<String> out = tuningChanges(preset, rung);
        // While Faithful Mode is on, the four tuning choosers still write their figures for every family, but the five
        // it owns run by its numbers until it is off again, and the detail says so (spec 2.1).
        if (TUNING.contains(preset.key) && faithfulOn()) out.add(Math.min(1, out.size()), words.words(FAITHFUL_CHOOSER));
        return out;
    }

    /** The four choosers that tune wear, whose figures Faithful Mode overrides for the five it owns (spec 2.1). */
    static final List<String> TUNING = Collections
        .unmodifiableList(java.util.Arrays.asList("wear", "healing", "phases", "depth"));

    private List<String> tuningChanges(Setting preset, String rung) {
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
        if (changes.size() > PRESET_LINES) out.add(words.words(SettingsKeys.PRESET_MORE, changes.size() - PRESET_LINES));
        return out;
    }

    /** A setting named with its family where it is a family's: "Grass - Wear starts at". */
    String named(Setting setting) {
        SurfaceFamily family = familyOf(setting.category);
        return family == null ? title(setting) : pageLabel(setting.category) + " - " + title(setting);
    }

    private String said(String value) {
        if ("true".equals(value)) return words.words(SettingsKeys.OPTIONS_ON);
        if ("false".equals(value)) return words.words(SettingsKeys.OPTIONS_OFF);
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------
    // Faithful Mode (0.9.229 W5, spec 2.1, 2.3 and 2.7)
    // ------------------------------------------------------------------

    /** The switch, by its id. */
    static final String FAITHFUL = Presets.CATEGORY + "." + FaithfulProfile.SWITCH;

    /** The fold's own words, and its detail's (this edition's own keys; their English is in settings_ui.tsv). */
    static final String FAITHFUL_FOLD = SettingsKeys.OWN + "faithful.fold";

    static final String FAITHFUL_OFF = SettingsKeys.OWN + "faithful.off";

    static final String FAITHFUL_CHANGES_ONE = SettingsKeys.OWN + "faithful.changes.one";

    static final String FAITHFUL_CHANGES_MANY = SettingsKeys.OWN + "faithful.changes.many";

    static final String FAITHFUL_NONE = SettingsKeys.OWN + "faithful.none";

    static final String FAITHFUL_NOTHING = SettingsKeys.OWN + "faithful.nothing";

    static final String FAITHFUL_SPEEDS = SettingsKeys.OWN + "faithful.speeds";

    static final String FAITHFUL_KEPT = SettingsKeys.OWN + "faithful.kept";

    static final String FAITHFUL_EVERY = SettingsKeys.OWN + "faithful.every";

    static final String FAITHFUL_CHOOSER = SettingsKeys.OWN + "faithful.chooser";

    /** Every key above, for the test that holds the language file to them. */
    static final List<String> FAITHFUL_WORDS = Collections.unmodifiableList(
        java.util.Arrays.asList(
            FAITHFUL_FOLD,
            FAITHFUL_OFF,
            FAITHFUL_CHANGES_ONE,
            FAITHFUL_CHANGES_MANY,
            FAITHFUL_NONE,
            FAITHFUL_NOTHING,
            FAITHFUL_SPEEDS,
            FAITHFUL_KEPT,
            FAITHFUL_EVERY,
            FAITHFUL_CHOOSER));

    /** The three dials that still apply over Faithful Mode, which is the original's pace with each at 1 (spec 2.3). */
    static final List<String> SPEEDS = Collections.unmodifiableList(
        java.util.Arrays.asList(
            ConfigFile.CATEGORY_GENERAL + ".globalSpeed",
            ConfigFile.CATEGORY_GENERAL + ".erosionSpeed",
            TrmtConfig.CATEGORY_HEALING + ".healingRate"));

    /** Whether Faithful Mode is on, as the screen holds the switch now. */
    public boolean faithfulOn() {
        return Boolean.parseBoolean(edits.value(FAITHFUL));
    }

    /**
     * The fold under Faithful Mode's row; opened, its detail a line a row and the heading's settings. Answers where the
     * fold ends, or -1 where there is no fold.
     */
    private int addFaithful(List<Row> out, boolean open) {
        int at = -1;
        for (int i = 0; i < out.size(); i++) {
            Setting setting = out.get(i).setting;
            if (setting != null && FAITHFUL.equals(setting.id)) at = i;
        }
        if (at < 0 || !config.hasCategory(FaithfulProfile.CATEGORY)) return -1;
        List<Row> fold = new ArrayList<Row>();
        fold.add(new Row(RowType.FAITHFUL, null, words.words(FAITHFUL_FOLD), null, null));
        if (open) {
            for (String line : faithfulDetail()) fold.add(new Row(RowType.DETAIL, null, line, null, null));
            ConfigFile.Category heading = config.getCategory(FaithfulProfile.CATEGORY);
            addFolder(fold, words.words(FAITHFUL_EVERY), heading.getName());
            for (ConfigFile.Category inside : children(config, FaithfulProfile.CATEGORY)) {
                String name = inside.getName();
                addFolder(fold, surfaceWords(name.substring(name.lastIndexOf('.') + 1)), name);
            }
        }
        out.addAll(at + 1, fold);
        return at + 1 + fold.size();
    }

    private void addFolder(List<Row> out, String title, String category) {
        int before = out.size();
        out.add(new Row(RowType.FOLDER, null, title, null, null));
        addSettings(out, category);
        if (out.size() == before + 1) out.remove(before);
    }

    /** A surface's name as its sub-tab reads, "Plants" for vegetation; the key spaced out where there is none. */
    private String surfaceWords(String surface) {
        String key = SettingsKeys.OWN + "family." + surface;
        return words.has(key) ? words.words(key) : ConfigWords.title(surface);
    }

    /**
     * Faithful Mode's detail, as its fold opens with it: that it waits while off, what it runs in place of the
     * player's own numbers ({@link #presetChanges}), the speed dials still applied where any is not 1, and that the
     * player's numbers are kept.
     */
    public List<String> faithfulDetail() {
        List<String> out = new ArrayList<String>();
        if (!faithfulOn()) out.add(words.words(FAITHFUL_OFF));
        out.addAll(faithfulChanges());
        String speeds = speedsApplied();
        if (speeds != null) out.add(speeds);
        out.add(words.words(FAITHFUL_KEPT));
        return out;
    }

    /** Every setting Faithful Mode runs by its numbers in place of the value the screen holds, a line each. */
    private List<String> faithfulChanges() {
        List<String> out = new ArrayList<String>();
        // Leaves and plants keep their break figures in no setting of the file, only in their shipped settings, so
        // what Faithful Mode runs in place of those is said against the shipped figures.
        Map<String, String> current = new LinkedHashMap<String, String>(edits.values());
        Map<String, String> wanted = faithfulValues();
        for (String id : wanted.keySet()) {
            if (!byId.containsKey(id)) current.put(id, shippedFigure(id));
        }
        List<PresetDiff.Change> changes = PresetDiff.diff(current, wanted, new ArrayList<String>(byId.keySet()));
        if (changes.isEmpty()) {
            out.add(words.words(FAITHFUL_NONE));
            return out;
        }
        out.add(
            changes.size() == 1 ? words.words(FAITHFUL_CHANGES_ONE)
                : words.words(FAITHFUL_CHANGES_MANY, changes.size()));
        for (PresetDiff.Change change : changes) {
            out.add(
                words.words(
                    SettingsKeys.PRESET_LINE,
                    faithfulNamed(change.id),
                    listed(change.from),
                    listed(change.to)));
        }
        return out;
    }

    /**
     * A setting Faithful Mode runs, named as the list names it: a setting of the file by its own name, and a family's
     * figure the file has no setting for by its family and the {@code faithful} setting that fills it.
     */
    private String faithfulNamed(String id) {
        Setting setting = byId.get(id);
        if (setting != null) return named(setting);
        String family = TrmtConfig.CATEGORY_FAMILIES + ".";
        String rest = id.startsWith(family) ? id.substring(family.length()) : id;
        Setting filler = byId.get(FaithfulProfile.CATEGORY + "." + rest);
        int dot = rest.indexOf('.');
        return dot < 0 || filler == null ? id : surfaceWords(rest.substring(0, dot)) + " - " + title(filler);
    }

    /** A family's shipped figure for an id the file holds no setting for: {@code families.leaves.thresholdMin}. */
    private static String shippedFigure(String id) {
        String family = TrmtConfig.CATEGORY_FAMILIES + ".";
        if (!id.startsWith(family)) return null;
        String rest = id.substring(family.length());
        int dot = rest.indexOf('.');
        SurfaceFamily surface = dot < 0 ? null : SurfaceFamily.byKey(rest.substring(0, dot));
        if (surface == null) return null;
        FamilySettings shipped = FamilySettings.shipped(surface);
        String key = rest.substring(dot + 1);
        if ("thresholdMin".equals(key)) return decimal(shipped.thresholdMin);
        if ("thresholdMax".equals(key)) return decimal(shipped.thresholdMax);
        return null;
    }

    /** A value on one line: a list's lines joined by commas, an empty one as "nothing". */
    private String listed(String value) {
        if (value == null || value.isEmpty()) return words.words(FAITHFUL_NOTHING);
        // A figure of a list of numbers is read as the file writes it, 5.0; said as the screen says a number, 5.
        String trimmed = value.trim();
        if (trimmed.matches("-?[0-9]+\\.0+")) return trimmed.substring(0, trimmed.indexOf('.'));
        return said(value.replace("\n", ", "));
    }

    /** "Overall speed 2, Healing speed 0.5" for the dials not at 1, or null where all three are. */
    private String speedsApplied() {
        List<String> off = new ArrayList<String>();
        for (String id : SPEEDS) {
            Setting setting = byId.get(id);
            String value = edits.value(id);
            if (setting == null || value == null || PresetDiff.same(value, "1")) continue;
            off.add(title(setting) + " " + value);
        }
        if (off.isEmpty()) return null;
        StringBuilder joined = new StringBuilder();
        for (String one : off) {
            if (joined.length() > 0) joined.append(", ");
            joined.append(one);
        }
        return words.words(FAITHFUL_SPEEDS, joined.toString());
    }

    /**
     * What Faithful Mode runs in place of the player's own figures while it is on, by id, as the screen holds values:
     * what {@code FaithfulProfile.write} writes into the live settings, from the {@code faithful} values held now, so
     * an edit there shows at once ({@code FaithfulDetailTest} holds the two to each other).
     */
    Map<String, String> faithfulValues() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (SurfaceFamily base : new SurfaceFamily[] { SurfaceFamily.GRASS, SurfaceFamily.DIRT, SurfaceFamily.SAND }) {
            String category = ConfigLayout.familyCategory(base);
            String from = FaithfulProfile.CATEGORY + "." + base.key();
            out.put(category + ".stages", String.valueOf(FaithfulChains.stages(base)));
            put(out, category + ".thresholdMin", edits.value(from + ".thresholdMin"));
            put(out, category + ".thresholdMax", edits.value(from + ".thresholdMax"));
            out.put(category + ".costCurve", CostCurve.FLAT.key());
            out.put(category + ".maxWear", "1");
            out.put(category + ".wearsThroughTo", base == SurfaceFamily.GRASS ? SurfaceFamily.DIRT.key() : "");
            String heal = base == SurfaceFamily.DIRT ? edits.value("faithful.dirtHealDays")
                : firstLine(
                    edits.value(base == SurfaceFamily.GRASS ? "faithful.grassHealDays" : "faithful.sandHealDays"));
            put(out, category + ".healDaysPerStage", heal);
            if (base == SurfaceFamily.SAND) {
                out.put(category + ".maxSinkPixels", String.valueOf(FaithfulChains.deepest(base)));
            }
        }
        for (SurfaceFamily target : new SurfaceFamily[] { SurfaceFamily.LEAVES, SurfaceFamily.VEGETATION }) {
            String category = ConfigLayout.familyCategory(target);
            String from = FaithfulProfile.CATEGORY + "." + target.key();
            put(out, category + ".thresholdMin", edits.value(from + ".thresholdMin"));
            put(out, category + ".thresholdMax", edits.value(from + ".thresholdMax"));
        }
        String trampling = TrmtConfig.CATEGORY_TRAMPLING + ".";
        put(out, trampling + "leaves", edits.value("faithful.leaves.wears"));
        put(out, trampling + "vegetation", edits.value("faithful.vegetation.wears"));
        put(out, trampling + "leavesDropChance", edits.value("faithful.leaves.dropChance"));
        put(out, trampling + "vegetationDropChance", edits.value("faithful.vegetation.dropChance"));
        return out;
    }

    private static void put(Map<String, String> out, String id, String value) {
        if (value != null) out.put(id, value);
    }

    private static String firstLine(String lines) {
        if (lines == null) return null;
        int end = lines.indexOf('\n');
        return end < 0 ? lines : lines.substring(0, end);
    }

    // ------------------------------------------------------------------
    // "Use your TRMT settings" (0.9.229 W5 T9, spec 2.8)
    // ------------------------------------------------------------------

    /** The original mod's settings file, as {@link OriginalFile} looks for it in this mod's own config folder. */
    public static final String ORIGINAL_FILE = OriginalFile.NAME;

    /** The row's words (this edition's own keys; their English is in settings_ui.tsv). */
    static final String ORIGINAL_ROW = SettingsKeys.OWN + "original.row";

    /** The rung that leaves the settings as they are, and the one that uses the file, as the dropdown lists them. */
    public static final String ORIGINAL_OFF = "off";

    public static final String ORIGINAL_ON = "on";

    /** Where a rung's title is filed; its line and its detail follow it, as a chooser value's do. */
    static String originalWords(String rung) {
        return SettingsKeys.OWN + "original." + rung;
    }

    static final String ORIGINAL_UNMATCHED = SettingsKeys.OWN + "original.unmatched";

    static final String ORIGINAL_UNREAD = SettingsKeys.OWN + "original.unread";

    static final String ORIGINAL_PLANT = SettingsKeys.OWN + "original.plant";

    static final String ORIGINAL_RANGE = SettingsKeys.OWN + "original.range";

    static final String ORIGINAL_ABSENT = SettingsKeys.OWN + "original.absent";

    static final String ORIGINAL_ABSENT_LIST = SettingsKeys.OWN + "original.absent.list";

    static final String ORIGINAL_BROKEN = SettingsKeys.OWN + "original.broken";

    /** Every key of the row, for the test that holds the language file to them. */
    static final List<String> ORIGINAL_WORDS = Collections.unmodifiableList(
        java.util.Arrays.asList(
            ORIGINAL_ROW,
            originalWords(ORIGINAL_OFF),
            originalWords(ORIGINAL_OFF) + ".line",
            originalWords(ORIGINAL_OFF) + ".detail",
            originalWords(ORIGINAL_ON),
            originalWords(ORIGINAL_ON) + ".line",
            originalWords(ORIGINAL_ON) + ".detail",
            ORIGINAL_UNMATCHED,
            ORIGINAL_UNREAD,
            ORIGINAL_PLANT,
            ORIGINAL_RANGE,
            ORIGINAL_ABSENT,
            ORIGINAL_ABSENT_LIST,
            ORIGINAL_BROKEN));

    /** What the original's file gave when the screen opened, or null where there was no file. */
    private OriginalSettings.Reading original;

    /** The original's file as last looked for ({@link OriginalFile#look}); null where there is none. */
    public void original(String text) {
        original = text == null ? null : OriginalSettings.read(text, FaithfulProfile.EDITION);
    }

    /** Whether the row is shown: the original's file was found, readable or not. */
    public boolean originalFound() {
        return original != null && config != null && config.hasCategory(FaithfulProfile.CATEGORY);
    }

    /** The row's two rungs, in the order its dropdown lists them. */
    public List<String> originalChoices() {
        return java.util.Arrays.asList(ORIGINAL_OFF, ORIGINAL_ON);
    }

    /**
     * The rung the row shows, as a preset's chooser shows the rung whose numbers are there: "In use" while Faithful
     * Mode is on and the screen holds every value of the file, "Not in use" otherwise.
     */
    public String originalChosen() {
        Map<String, String> wanted = originalValues();
        if (wanted.isEmpty()) return ORIGINAL_OFF;
        for (Map.Entry<String, String> one : wanted.entrySet()) {
            String held = edits.value(one.getKey());
            if (held == null || !sameLines(held, one.getValue())) return ORIGINAL_OFF;
        }
        return ORIGINAL_ON;
    }

    /**
     * What a rung sets, by id: "In use" the file's values ({@link #originalValues}); "Not in use", where the file's
     * values are in use, Faithful Mode off, the file's numbers left in its settings; nothing otherwise.
     */
    Map<String, String> originalRung(String rung) {
        if (ORIGINAL_ON.equals(rung)) return originalValues();
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (ORIGINAL_ON.equals(originalChosen())) out.put(FAITHFUL, "false");
        return keepKnown(out);
    }

    /**
     * A rung's detail panel (spec 2.8), as a preset's lists what its rung would change: for "In use",
     * {@link #originalDetail}; for "Not in use", Faithful Mode switched off where the file's values are in use, or that
     * it changes nothing.
     */
    public List<String> originalChanges(String rung) {
        if (ORIGINAL_ON.equals(rung)) return originalDetail();
        List<String> out = new ArrayList<String>();
        String named = words.words(originalWords(ORIGINAL_OFF));
        List<PresetDiff.Change> changes = PresetDiff
            .diff(edits.values(), originalRung(rung), new ArrayList<String>(byId.keySet()));
        if (changes.isEmpty()) {
            out.add(words.words(SettingsKeys.PRESET_NONE, named));
            return out;
        }
        out.add(
            changes.size() == 1 ? words.words(SettingsKeys.PRESET_CHANGES_ONE, named)
                : words.words(SettingsKeys.PRESET_CHANGES_MANY, named, changes.size()));
        for (PresetDiff.Change change : changes) {
            Setting setting = byId.get(change.id);
            out.add(
                words.words(
                    SettingsKeys.PRESET_LINE,
                    setting == null ? change.id : named(setting),
                    said(change.from),
                    said(change.to)));
        }
        return out;
    }

    /**
     * The line under the row's title: where the file cannot be read, the refusal (spec 2.8: "the row says so and
     * changes nothing"); null otherwise, so the chosen rung's own line is shown, as a preset row's is.
     */
    public String originalSummary() {
        if (!originalFound() || original.readable()) return null;
        return originalDetail().get(0);
    }

    /**
     * A rung chosen (spec 2.8): "In use" turns Faithful Mode on and sets the file's values through the same edit path a
     * preset's rung takes, as one step, so Ctrl+Z, Cancel and Save treat it as any choice; "Not in use" takes Faithful
     * Mode off again by that path. Nothing where the file cannot be read.
     */
    public void chooseOriginal(String rung) {
        Map<String, String> wanted = originalRung(rung);
        if (!wanted.isEmpty()) edits.group(wanted);
    }

    /**
     * What choosing the row sets, by id, as the screen holds values: Faithful Mode on, and each {@code faithful}
     * setting the file's value, one key to one key ({@code OriginalSettings.KEYS}). A value the screen already holds is
     * given in the screen's own text, so it is no change. Empty where there is no file, or it cannot be read.
     */
    Map<String, String> originalValues() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (!originalFound() || !original.readable()) return out;
        out.put(FAITHFUL, "true");
        for (Map.Entry<String, Object> one : original.values()
            .entrySet()) {
            String id = one.getKey();
            String held = edits.value(id);
            String wanted = screenText(one.getValue());
            out.put(id, held != null && sameLines(held, wanted) ? held : wanted);
        }
        return keepKnown(out);
    }

    /** A value of {@code OriginalSettings.Reading.values} as the screen holds it: a list a line a value. */
    private static String screenText(Object value) {
        if (value instanceof double[]) {
            double[] figures = (double[]) value;
            String[] lines = new String[figures.length];
            for (int i = 0; i < figures.length; i++) lines[i] = NumberWords.number(figures[i]);
            return join(lines);
        }
        if (value instanceof String[]) return join((String[]) value);
        if (value instanceof Double) return NumberWords.number((Double) value);
        return String.valueOf(value);
    }

    /** Whether two values hold the same lines, a number by its value: "5.0" and "5" are the same figure. */
    private static boolean sameLines(String one, String other) {
        List<String> a = lines(one);
        List<String> b = lines(other);
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!PresetDiff.same(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    /**
     * The row's detail (spec 2.8): "Choosing Use your TRMT settings changes N settings:" with a line a change, then
     * "Could not be matched:" with a line for each value that has no counterpart here, then each key the file did not
     * hold with the default used for it. A file that cannot be read is one line saying so, and nothing else.
     */
    public List<String> originalDetail() {
        List<String> out = new ArrayList<String>();
        if (!originalFound()) return out;
        if (!original.readable()) {
            OriginalSettings.Problem broken = original.refusal();
            out.add(words.words(ORIGINAL_BROKEN, broken.line, broken.column, broken.why));
            return out;
        }
        String named = words.words(ORIGINAL_ROW);
        List<PresetDiff.Change> changes = PresetDiff
            .diff(edits.values(), originalValues(), new ArrayList<String>(byId.keySet()));
        List<PresetDiff.Change> real = new ArrayList<PresetDiff.Change>();
        for (PresetDiff.Change change : changes) {
            if (!sameLines(change.from == null ? "" : change.from, change.to == null ? "" : change.to))
                real.add(change);
        }
        if (real.isEmpty()) {
            out.add(words.words(SettingsKeys.PRESET_NONE, named));
        } else {
            out.add(
                real.size() == 1 ? words.words(SettingsKeys.PRESET_CHANGES_ONE, named)
                    : words.words(SettingsKeys.PRESET_CHANGES_MANY, named, real.size()));
            for (PresetDiff.Change change : real) {
                Setting setting = byId.get(change.id);
                out.add(
                    words.words(
                        SettingsKeys.PRESET_LINE,
                        setting == null ? change.id : named(setting),
                        originalSaid(change.from),
                        originalSaid(change.to)));
            }
        }
        if (!original.unmatched()
            .isEmpty()) {
            out.add(words.words(ORIGINAL_UNMATCHED));
            for (OriginalSettings.Problem problem : original.unmatched()) out.add(unmatchedLine(problem));
        }
        for (OriginalSettings.Problem problem : original.absent()) out.add(unmatchedLine(problem));
        return out;
    }

    /** One line about a value of the file, in this edition's words. */
    private String unmatchedLine(OriginalSettings.Problem problem) {
        switch (problem.what) {
            case NOTHING_READS_IT:
                return words.words(ORIGINAL_UNREAD, problem.key);
            case NO_SUCH_PLANT:
                return words.words(ORIGINAL_PLANT, problem.key);
            case OUT_OF_RANGE:
                return words.words(
                    ORIGINAL_RANGE,
                    problem.key,
                    NumberWords.number(problem.given),
                    NumberWords.number(problem.applied));
            case NOT_IN_FILE:
                for (OriginalSettings.Key key : OriginalSettings.KEYS) {
                    if (!key.original.equals(problem.key)) continue;
                    if (key.kind == OriginalSettings.Kind.PLANTS) return words.words(ORIGINAL_ABSENT_LIST, problem.key);
                    if (key.kind == OriginalSettings.Kind.SWITCH) {
                        return words.words(ORIGINAL_ABSENT, problem.key, said(String.valueOf(problem.applied != 0d)));
                    }
                }
                return words.words(ORIGINAL_ABSENT, problem.key, NumberWords.number(problem.applied));
            default:
                return words.words(ORIGINAL_BROKEN, problem.line, problem.column, problem.why);
        }
    }

    /** A value on one line: a switch as On or Off, each figure of a list by its value, an empty one as "nothing". */
    private String originalSaid(String value) {
        if (value == null || value.trim()
            .isEmpty()) return words.words(FAITHFUL_NOTHING);
        List<String> out = new ArrayList<String>();
        for (String line : lines(value)) {
            String trimmed = line.trim();
            out.add(
                trimmed.matches("-?[0-9]+(\\.[0-9]+)?") ? NumberWords.number(Double.parseDouble(trimmed))
                    : said(trimmed));
        }
        StringBuilder joined = new StringBuilder();
        for (String one : out) {
            if (joined.length() > 0) joined.append(", ");
            joined.append(one);
        }
        return joined.toString();
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
        String suggested = suggested(setting);
        if (suggested != null) {
            out.add(new Line(words.words(SettingsKeys.SUGGESTED, suggested), Canvas.GREY));
            out.add(new Line(words.words(SettingsKeys.SUGGESTED_NOTE), Canvas.GREY));
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
            // "Restore a backup" sits in the world's settings (wear backups, 0.9.229), in single player.
            if (ownWorld) out.add(HEADER_BACKUPS);
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
        } else if (HEADER_BACKUPS.equals(action)) {
            out.add(new Line(words.words(SettingsKeys.HEADER_BACKUPS), Canvas.GOLD));
            out.add(new Line(words.words(SettingsKeys.HEADER_BACKUPS_TIP), Canvas.BODY));
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
            // A setting a server suggests is the player's to change for the visit alone: their choice goes to the
            // visit, never into the file (0.9.229). A forced one is locked and cannot be edited at all.
            if (ClientSettingsHeld.choose(setting.category, setting.key, edits.value(id))) {
                written++;
                restart |= setting.takes == Takes.RESTART;
                reload |= setting.takes == Takes.RELOAD;
                continue;
            }
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
