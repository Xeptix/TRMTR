package com.trmtgtnh.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The settings file, read and written by this mod rather than by Forge.
 *
 * <p>
 * Forge's {@code Configuration} does this on 1.7.10 and 1.12.2 and is gone from 1.13 onward, replaced
 * by a TOML format that is not the same file. **That matters more than the inconvenience of porting
 * it**: the two editions that exist today read one {@code trmtgtnh.cfg}, which is why a packmaker's
 * three thousand lines of settings move between them untouched and why the settings class itself is
 * carried across by a script rather than translated. Taking Forge's replacement would end that for
 * good, and leave the mod's largest file to be maintained once per version.
 *
 * <p>
 * So the format stays and the mod owns the reader. It is Forge's format, deliberately, down to the
 * type prefixes and the banner comments - a file written here is read by Forge's own parser and the
 * other way round, which is the whole point and is what the tests check in both directions.
 *
 * <h2>The format</h2>
 *
 * <pre>
 *   # a comment, which belongs to the setting below it
 *   category {
 *       B:aBoolean=true
 *       I:anInteger=7
 *       D:aDouble=0.5
 *       S:aString=text
 *       S:aList &lt;
 *           first
 *           second
 *        &gt;
 *       nested {
 *           B:deeper=false
 *       }
 *   }
 * </pre>
 *
 * <p>
 * Four types, categories nesting to any depth, and a list written in angle brackets one item to a
 * line. A comment attaches to whatever follows it. Everything else in the file - the row of hashes
 * above each category, the wrapped help text - is a comment and is reproduced on write because a
 * config nobody can read is a config nobody edits.
 *
 * <h2>What this class is not</h2>
 *
 * <p>
 * Not a general-purpose config library and not a drop-in for Forge's class: it answers the calls this
 * mod makes and no others. It names nothing from Minecraft or from Forge, which is the property that
 * lets it be the one settings reader every future edition shares, whatever loader it is running on.
 */
public final class ConfigFile {

    /** What Forge calls the category every mod puts its ungrouped settings in. */
    public static final String CATEGORY_GENERAL = "general";

    /** What Forge joins a nested category's name with, and so what this file does. */
    public static final String CATEGORY_SPLITTER = ".";

    /**
     * What kind of value a setting holds.
     *
     * <p>
     * Named rather than a character because the mod switches on it - a setting pushed to a server
     * arrives as text and has to be parsed as whatever it is. The letters are what the file carries
     * and are Forge's: B, I, D, S.
     */
    public enum Type {

        BOOLEAN('B'),
        INTEGER('I'),
        DOUBLE('D'),
        STRING('S');

        private final char prefix;

        Type(char prefix) {
            this.prefix = prefix;
        }

        /** The letter this type is written with. */
        public char prefix() {
            return prefix;
        }

        /** The type a letter means, or a string for a letter nobody recognises. */
        public static Type ofPrefix(char prefix) {
            for (Type type : values()) {
                if (type.prefix == prefix) return type;
            }
            return STRING;
        }
    }

    /** How wide the banner above a category is drawn, matching what Forge writes. */
    private static final int BANNER_WIDTH = 104;

    private final File file;

    /** Categories in the order they were first asked for, which is the order they are written. */
    private final Map<String, Category> categories = new LinkedHashMap<String, Category>();

    private boolean changed;

    public ConfigFile(File file) {
        this.file = file;
    }

    public File getConfigFile() {
        return file;
    }

    /**
     * Whether anything would be different if this were written out.
     *
     * <p>
     * Asks the settings as well as the file, because the two change for different reasons: the file
     * changes when a default has to be added or a value repaired at load, and a setting changes when
     * something edits it afterwards. Only the first used to be counted, which made every edit made
     * through {@link Setting#set} - the wear editor, the mob multipliers, a restored snapshot -
     * apply for the session and never reach the disk.
     */
    public boolean hasChanged() {
        if (changed) return true;
        for (Category category : categories.values()) {
            for (Setting setting : category.settings.values()) {
                if (setting.changed) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Reading what is there
    // ------------------------------------------------------------------

    /**
     * Parses the file, or leaves everything empty when there is nothing to parse.
     *
     * <p>
     * Deliberately forgiving, for the same reason the chunk reader is: a line nobody can make sense
     * of is skipped rather than thrown, because a settings file somebody has hand-edited into an odd
     * state should cost them that setting and not their game. What cannot be read keeps the default
     * the calling code passes in, and the file is rewritten in good order at the next save.
     */
    public void load() {
        categories.clear();
        changed = false;
        if (file == null || !file.isFile()) {
            changed = true;
            return;
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8));
            List<String> path = new ArrayList<String>();
            StringBuilder comment = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("#")) {
                    collectComment(comment, trimmed);
                    continue;
                }
                if (trimmed.equals("}")) {
                    if (!path.isEmpty()) path.remove(path.size() - 1);
                    comment.setLength(0);
                    continue;
                }
                if (trimmed.endsWith("{")) {
                    path.add(
                        unquote(
                            trimmed.substring(0, trimmed.length() - 1)
                                .trim()));
                    category(join(path)).comment = take(comment);
                    continue;
                }
                if (trimmed.length() > 2 && trimmed.charAt(1) == ':') {
                    readSetting(in, category(join(path)), trimmed, take(comment));
                    continue;
                }
                // Anything else is a line this parser does not know. Skipped on purpose.
                comment.setLength(0);
            }
        } catch (IOException unreadable) {
            // Everything keeps its default and the file is rewritten at the next save.
            categories.clear();
            changed = true;
        } finally {
            close(in);
        }
    }

    /** A banner row of hashes carries no meaning; anything else is help text. */
    private static void collectComment(StringBuilder into, String line) {
        String body = line.substring(1)
            .trim();
        if (body.isEmpty() || body.replace("#", "")
            .replace("-", "")
            .isEmpty()) {
            return;
        }
        if (into.length() > 0) into.append(' ');
        into.append(body);
    }

    private static String take(StringBuilder comment) {
        String out = comment.toString();
        comment.setLength(0);
        return out;
    }

    /** One setting, which is either a value on this line or a list across the next several. */
    private void readSetting(BufferedReader in, Category into, String line, String comment) throws IOException {
        char type = line.charAt(0);
        String rest = line.substring(2);
        int opens = rest.indexOf('<');
        int equals = rest.indexOf('=');

        if (opens >= 0 && (equals < 0 || opens < equals)) {
            String name = unquote(
                rest.substring(0, opens)
                    .trim());
            List<String> items = new ArrayList<String>();
            String item;
            while ((item = in.readLine()) != null) {
                String body = item.trim();
                if (body.equals(">")) break;
                if (body.isEmpty()) continue;
                items.add(body);
            }
            Setting setting = new Setting(name, type, comment);
            setting.values = items;
            into.settings.put(name, setting);
            return;
        }
        if (equals < 0) return;
        String name = unquote(
            rest.substring(0, equals)
                .trim());
        Setting setting = new Setting(name, type, comment);
        setting.value = rest.substring(equals + 1);
        into.settings.put(name, setting);
    }

    private static String unquote(String name) {
        if (name.length() >= 2 && name.charAt(0) == '"' && name.endsWith("\"")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    // ------------------------------------------------------------------
    // Asking for a setting
    // ------------------------------------------------------------------

    public boolean getBoolean(String name, String category, boolean def, String comment) {
        Setting setting = ask(category, name, 'B', def ? "true" : "false", comment);
        return "true".equalsIgnoreCase(setting.value.trim());
    }

    public int getInt(String name, String category, int def, int min, int max, String comment) {
        Setting setting = ask(category, name, 'I', Integer.toString(def), comment);
        int held;
        try {
            held = Integer.parseInt(setting.value.trim());
        } catch (NumberFormatException notANumber) {
            held = def;
            setting.value = Integer.toString(def);
            changed = true;
        }
        setting.range = "[range: " + min + " ~ " + max + ", default: " + def + "]";
        setting.bound(min, max);
        return held;
    }

    public float getFloat(String name, String category, float def, float min, float max, String comment) {
        Setting setting = ask(category, name, 'D', Float.toString(def), comment);
        float held;
        try {
            held = Float.parseFloat(setting.value.trim());
        } catch (NumberFormatException notANumber) {
            held = def;
            setting.value = Float.toString(def);
            changed = true;
        }
        setting.range = "[range: " + min + " ~ " + max + ", default: " + def + "]";
        setting.bound(min, max);
        return held;
    }

    public String getString(String name, String category, String def, String comment) {
        return ask(category, name, 'S', def, comment).value;
    }

    /** A setting by category and name, made at the default if the file had none. */
    public Setting get(String category, String name, boolean def, String comment) {
        return ask(category, name, 'B', def ? "true" : "false", comment);
    }

    public Setting get(String category, String name, String def, String comment) {
        return ask(category, name, 'S', def, comment);
    }

    /** A list setting, made at the default if the file had none. */
    public Setting get(String category, String name, String[] def, String comment) {
        Category into = category(category);
        Setting setting = into.settings.get(name);
        if (setting == null) {
            setting = new Setting(name, 'S', comment);
            setting.values = new ArrayList<String>(java.util.Arrays.asList(def));
            into.settings.put(name, setting);
            changed = true;
        } else if (setting.values == null) {
            // A scalar where a list is wanted: one item, which is what Forge does with it too.
            setting.values = new ArrayList<String>();
            if (!setting.value.isEmpty()) setting.values.add(setting.value.trim());
        }
        setting.comment = comment;
        setting.defList = new ArrayList<String>(java.util.Arrays.asList(def));
        return setting;
    }

    /**
     * A string from a fixed set of choices.
     *
     * <p>
     * The choices are not enforced here. Forge does not enforce them either - it offers them on the
     * screen as a cycling button and accepts whatever a text editor put there - and every caller in
     * this mod checks the value itself and says so in the log, which is the better error anyway.
     */
    public String getString(String name, String category, String def, String comment, String[] validValues) {
        Setting setting = ask(category, name, 'S', def, comment);
        setting.validValues = validValues;
        return setting.value;
    }

    /** A string setting from a fixed set of choices. */
    public Setting get(String category, String name, String def, String comment, String[] validValues) {
        Setting setting = ask(category, name, 'S', def, comment);
        setting.validValues = validValues;
        return setting;
    }

    /**
     * A setting with no help text, which takes the help text away.
     *
     * <p>
     * Forge's own does this - it passes a null comment straight to the setting - so a call that only
     * wants to write a value leaves that value without its paragraph until the next start, when the
     * reading code asks for it properly again. Kept rather than fixed: one setting is written this
     * way by {@code /trmt disable} and one by {@code /trmt hide}, and the mod has always behaved so.
     */
    public Setting get(String category, String name, boolean def) {
        return ask(category, name, 'B', def ? "true" : "false", "");
    }

    public Setting get(String category, String name, int def) {
        return ask(category, name, 'I', Integer.toString(def), "");
    }

    public Setting get(String category, String name, double def) {
        return ask(category, name, 'D', Double.toString(def), "");
    }

    /** A whole number with bounds for the screen to draw. */
    public Setting get(String category, String name, int def, String comment, int min, int max) {
        Setting setting = ask(category, name, 'I', Integer.toString(def), comment);
        if (!parses(setting.value, false)) {
            setting.value = Integer.toString(def);
            changed = true;
        }
        setting.range = "[range: " + min + " ~ " + max + ", default: " + def + "]";
        setting.bound(min, max);
        return setting;
    }

    /** A fractional number with bounds for the screen to draw. Takes a float as well, by widening. */
    public Setting get(String category, String name, double def, String comment, double min, double max) {
        Setting setting = ask(category, name, 'D', Double.toString(def), comment);
        if (!parses(setting.value, true)) {
            setting.value = Double.toString(def);
            changed = true;
        }
        setting.range = "[range: " + min + " ~ " + max + ", default: " + def + "]";
        setting.bound(min, max);
        return setting;
    }

    /** A list of whole numbers, which this mod uses for exactly one setting: the dimension list. */
    public Setting get(String category, String name, int[] def, String comment) {
        String[] asText = new String[def.length];
        for (int i = 0; i < def.length; i++) {
            asText[i] = Integer.toString(def[i]);
        }
        Setting setting = get(category, name, asText, comment);
        setting.type = 'I';
        return setting;
    }

    /**
     * Whether a held value is a number at all, which is the only thing Forge checks.
     *
     * <p>
     * Not whether it is inside its bounds. Forge's {@code isIntValue} is a bare {@code parseInt} and
     * its {@code isDoubleValue} a bare {@code parseDouble}; the minimum and maximum it was handed go
     * to the config screen and nowhere else. So a figure a text editor put above the maximum reaches
     * the mod exactly as written, on 1.7.10 and therefore here.
     */
    private static boolean parses(String held, boolean fractional) {
        try {
            if (fractional) Double.parseDouble(held.trim());
            else Integer.parseInt(held.trim());
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private Setting ask(String category, String name, char type, String def, String comment) {
        Category into = category(category);
        Setting setting = into.settings.get(name);
        if (setting == null) {
            setting = new Setting(name, type, comment);
            setting.value = def;
            into.settings.put(name, setting);
            changed = true;
        }
        setting.comment = comment == null ? "" : comment;
        setting.type = type;
        setting.def = def == null ? "" : def;
        return setting;
    }

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    public Category getCategory(String name) {
        return category(name);
    }

    /**
     * Whether a category is already there, without making one.
     *
     * <p>
     * {@link #getCategory} makes what it cannot find, which is what the settings code wants and the
     * opposite of what a packet wants: three of them are handed a category name by whoever is at the
     * other end and must not create whatever they were sent.
     */
    public boolean hasCategory(String name) {
        return categories
            .containsKey(name == null || name.isEmpty() ? CATEGORY_GENERAL : name.toLowerCase(java.util.Locale.ROOT));
    }

    public Set<String> getCategoryNames() {
        return categories.keySet();
    }

    public void setCategoryComment(String name, String comment) {
        category(name).comment = comment;
    }

    /**
     * A category by its full dotted name, made if it is new - and so are its ancestors.
     *
     * <p>
     * The ancestors are the part that is not obvious and the part a probe caught. A category is
     * written inside its parent, so one whose parent was never asked for has nothing to be written
     * inside and is silently dropped: asking only for {@code families.grass} produced a file with no
     * {@code families} in it and therefore no {@code grass} either. Forge's own class makes the
     * parents implicitly and callers rely on it without knowing they do - this mod asks for thirteen
     * nested categories and names the parent of exactly none of them.
     */
    private Category category(String name) {
        String key = name == null || name.isEmpty() ? CATEGORY_GENERAL : name.toLowerCase(java.util.Locale.ROOT);
        int dot = key.indexOf('.');
        while (dot > 0) {
            String ancestor = key.substring(0, dot);
            if (!categories.containsKey(ancestor)) {
                categories.put(ancestor, new Category(ancestor));
                changed = true;
            }
            dot = key.indexOf('.', dot + 1);
        }
        Category held = categories.get(key);
        if (held == null) {
            held = new Category(key);
            categories.put(key, held);
            changed = true;
        }
        return held;
    }

    private static String join(List<String> path) {
        if (path.isEmpty()) return CATEGORY_GENERAL;
        StringBuilder out = new StringBuilder();
        for (String part : path) {
            if (out.length() > 0) out.append('.');
            out.append(part);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Writing it back
    // ------------------------------------------------------------------

    /**
     * Writes the file, in the format Forge's own parser reads.
     *
     * <p>
     * Nested categories are written inside their parents, which is how Forge writes them and how a
     * reader - either reader - expects to find them. Everything is written whether it changed or not,
     * because a settings file is also documentation and a half-written one is worse than none.
     */
    public void save() {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();

        BufferedWriter out = null;
        try {
            out = new BufferedWriter(
                new OutputStreamWriter(new java.io.FileOutputStream(file), StandardCharsets.UTF_8));
            out.write("# Configuration file");
            out.newLine();
            out.newLine();
            for (Category category : roots()) {
                writeCategory(out, category, 0);
            }
            changed = false;
            for (Category category : categories.values()) {
                for (Setting setting : category.settings.values()) {
                    setting.changed = false;
                }
            }
        } catch (IOException unwritable) {
            // Nothing to do about it here; the values in memory are still right for this session.
        } finally {
            close(out);
        }
    }

    /** The categories with no parent among the categories, in insertion order. */
    private List<Category> roots() {
        List<Category> out = new ArrayList<Category>();
        for (Category category : categories.values()) {
            if (!category.name.contains(".")) out.add(category);
        }
        return out;
    }

    private List<Category> childrenOf(Category parent) {
        List<Category> out = new ArrayList<Category>();
        String prefix = parent.name + ".";
        for (Category category : categories.values()) {
            if (category.name.startsWith(prefix) && category.name.indexOf('.', prefix.length()) < 0) {
                out.add(category);
            }
        }
        return out;
    }

    private void writeCategory(BufferedWriter out, Category category, int depth) throws IOException {
        String pad = indent(depth);
        String leaf = category.name.substring(category.name.lastIndexOf('.') + 1);

        if (category.comment != null && !category.comment.isEmpty()) {
            out.write(pad + banner());
            out.newLine();
            writeComment(out, pad, category.comment);
            out.write(pad + banner());
            out.newLine();
            out.newLine();
        }
        out.write(pad + quoteIfNeeded(leaf) + " {");
        out.newLine();

        for (Setting setting : category.settings.values()) {
            writeSetting(out, setting, depth + 1);
        }
        for (Category child : childrenOf(category)) {
            writeCategory(out, child, depth + 1);
        }
        out.write(pad + "}");
        out.newLine();
        out.newLine();
    }

    private void writeSetting(BufferedWriter out, Setting setting, int depth) throws IOException {
        String pad = indent(depth);
        String help = setting.comment == null ? "" : setting.comment;
        if (setting.range != null) help = help.isEmpty() ? setting.range : help + " " + setting.range;
        if (!help.isEmpty()) writeComment(out, pad, help);

        if (setting.values != null) {
            out.write(pad + setting.type + ":" + quoteIfNeeded(setting.name) + " <");
            out.newLine();
            for (String item : setting.values) {
                out.write(indent(depth + 1) + item);
                out.newLine();
            }
            out.write(pad + " >");
            out.newLine();
        } else {
            out.write(pad + setting.type + ":" + quoteIfNeeded(setting.name) + "=" + setting.value);
            out.newLine();
        }
        out.newLine();
    }

    /** Help text, wrapped the way Forge wraps it so an existing file is not churned. */
    private static void writeComment(BufferedWriter out, String pad, String comment) throws IOException {
        out.write(pad + "# " + comment);
        out.newLine();
    }

    private static String banner() {
        StringBuilder out = new StringBuilder(BANNER_WIDTH);
        for (int i = 0; i < BANNER_WIDTH; i++) out.append('#');
        return out.toString();
    }

    private static String indent(int depth) {
        StringBuilder out = new StringBuilder(depth * 4);
        for (int i = 0; i < depth * 4; i++) out.append(' ');
        return out.toString();
    }

    /** Forge quotes a name holding anything but word characters, and its parser expects the same. */
    private static String quoteIfNeeded(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '.') return "\"" + name + "\"";
        }
        return name;
    }

    private static void close(java.io.Closeable it) {
        if (it == null) return;
        try {
            it.close();
        } catch (IOException ignored) {
            // Nothing useful to do with a failure to close.
        }
    }

    // ------------------------------------------------------------------
    // What a category and a setting are
    // ------------------------------------------------------------------

    /** One category, by its full dotted name, holding its settings in the order they were written. */
    public static final class Category {

        private final String name;

        private final Map<String, Setting> settings = new LinkedHashMap<String, Setting>();

        private String comment = "";

        Category(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getComment() {
            return comment;
        }

        public Set<String> keySet() {
            return settings.keySet();
        }

        /** The settings by name, in the order the file holds them. Forge hands out a map here too. */
        public Map<String, Setting> getValues() {
            return settings;
        }

        public Setting get(String name) {
            return settings.get(name);
        }

        /** Whether this category holds a setting by that name. */
        public boolean containsKey(String name) {
            return settings.containsKey(name);
        }

        /** Takes a setting out, for a setting the mod has retired. */
        public void remove(String name) {
            settings.remove(name);
        }

        /** Puts a setting in, for the places that carry a retired setting's value onto its heir. */
        public void put(String name, Setting setting) {
            settings.put(name, setting);
        }
    }

    /** One setting: a name, a type, help text, and either a value or a list of them. */
    public static final class Setting {

        private final String name;

        private char type;

        private String comment;

        private String value = "";

        private List<String> values;

        /** The range line Forge appends to an int's or a double's help text. */
        private String range;

        /**
         * The bounds, as numbers rather than as the sentence above.
         *
         * <p>
         * Because the config screen draws two of these as sliders, and a slider needs the two ends.
         * Absent for a setting nobody gave bounds to, which is most of them.
         */
        private double min;

        private double max;

        private boolean bounded;

        /** Whether this setting has numeric bounds at all. */
        public boolean isBounded() {
            return bounded;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        /** Set when something edits this setting, cleared when the file is written. */
        private boolean changed;

        /**
         * What the code asked for, which is not what the file holds.
         *
         * <p>
         * Kept because the config screen offers to put a setting back to it, and shows it in the
         * tooltip. A screen built from a file that has forgotten its own defaults has a reset button
         * that quietly resets to whatever is already there.
         */
        private String def = "";

        private List<String> defList;

        /** The code's default for this setting, as text. */
        public String getDefault() {
            return def;
        }

        /** The code's default for a list setting, or null for a scalar. */
        public String[] getDefaultList() {
            if (defList == null) return null;
            return defList.toArray(new String[defList.size()]);
        }

        Setting(String name, char type, String comment) {
            this.name = name;
            this.type = type;
            this.comment = comment == null ? "" : comment;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return Type.ofPrefix(type);
        }

        public String getComment() {
            return comment;
        }

        public boolean isList() {
            return values != null;
        }

        public String getString() {
            return value;
        }

        public boolean getBoolean() {
            return "true".equalsIgnoreCase(value.trim());
        }

        public String[] getStringList() {
            if (values == null) return new String[0];
            return values.toArray(new String[values.size()]);
        }

        public void set(String newValue) {
            String fresh = newValue == null ? "" : newValue;
            if (values != null || !fresh.equals(value)) changed = true;
            value = fresh;
            values = null;
        }

        public void set(boolean newValue) {
            set(newValue ? "true" : "false");
        }

        public void set(int newValue) {
            set(Integer.toString(newValue));
        }

        public void set(String[] newValues) {
            List<String> fresh = new ArrayList<String>(java.util.Arrays.asList(newValues));
            if (values == null || !values.equals(fresh)) changed = true;
            values = fresh;
        }

        /**
         * Made by hand, for the migration that carries a retired setting's value onto its
         * replacement. Everything else comes out of the file or out of a default.
         */
        public Setting(String name, String value, Type type) {
            this(name, type.prefix(), "");
            this.value = value == null ? "" : value;
            this.changed = true;
        }

        /** The choices the config screen should offer, or null for a free-text setting. */
        private String[] validValues;

        /** The translation key the config screen should label this with, or null for its name. */
        private String languageKey;

        /** Whether changing this needs the game restarted before it means anything. */
        private boolean requiresMcRestart;

        public String[] getValidValues() {
            return validValues;
        }

        public String getLanguageKey() {
            return languageKey;
        }

        public void setLanguageKey(String key) {
            languageKey = key;
        }

        public boolean requiresMcRestart() {
            return requiresMcRestart;
        }

        public void setRequiresMcRestart(boolean needed) {
            requiresMcRestart = needed;
        }

        /** The range line, for the screen's tooltip. Empty for a setting with no bounds. */
        public String getRange() {
            return range == null ? "" : range;
        }

        /**
         * This list as whole numbers, skipping anything that is not one.
         *
         * <p>
         * Skipped rather than thrown or defaulted. A dimension list with a typo in it should cost
         * that one dimension, not the whole list - and a list that came back empty because of one
         * bad entry would, in whitelist mode, stop erosion in every dimension there is.
         */
        public int[] getIntList() {
            if (values == null) return new int[0];
            int[] held = new int[values.size()];
            int kept = 0;
            for (String each : values) {
                int one;
                try {
                    one = Integer.parseInt(each.trim());
                } catch (NumberFormatException notANumber) {
                    continue; // Skipped, per the paragraph above.
                }
                // Parsed first, stored second, and not the other way round. Java evaluates an array
                // subscript before the right-hand side, so the shorter held[kept++] = parseInt(...)
                // increments the index and then throws - leaving a nought in the array and counting
                // it. For this setting a nought is the Overworld, so one typo in the dimension list
                // would have quietly listed the dimension almost everybody is standing in.
                held[kept++] = one;
            }
            if (kept == held.length) return held;
            int[] exact = new int[kept];
            System.arraycopy(held, 0, exact, 0, kept);
            return exact;
        }

        /** This setting as a whole number, or nought if it is not one. */
        public int getInt() {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException notANumber) {
                return 0;
            }
        }

        /** This setting as a fractional number, or nought if it is not one. */
        public double getDouble() {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException notANumber) {
                return 0.0d;
            }
        }

        public void set(double newValue) {
            set(Double.toString(newValue));
        }

        void bound(double low, double high) {
            min = low;
            max = high;
            bounded = true;
        }
    }
}
