package com.trmtgtnh.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.ConfigFile.Category;
import com.trmtgtnh.config.ConfigFile.Setting;
import com.trmtgtnh.util.ClientSettingRules;

/**
 * This client's half of a server's rules for its players' settings (0.9.229; Xep, 2026-11-18): what the server holds
 * for the visit, and the read that applies it without the player's file ever holding a server's value. The 1.7.10
 * edition's class over this edition's own {@link ConfigFile}.
 *
 * <p>
 * Every rule is put into the live settings for the length of each {@link TrmtConfig#read}, and the whole of the
 * settings is put back as it was before anything after the read can save it: every value, taken as the read
 * opened. So a read while connected - the settings screen's Done, the join's, any other - builds
 * everything from the server's values, and the file goes on holding the player's own, byte for byte; leaving reads it
 * back with nothing held. Putting back the whole of the settings rather than the held ones alone is the guarantee:
 * the presets write their bookkeeping and their rungs' numbers during a read, and a held number would otherwise reach
 * the file that way. The cost is that nothing a read writes for itself is kept while a server holds anything, which
 * every read without one writes again.
 *
 * <p>
 * <strong>And each setting's edited mark is put back with its value</strong>, which 1.7.10 has no need of. There a
 * read marks Forge's headings changed, so every save after one writes the file whole, with or without a server, and
 * the values alone decide its bytes. This edition's file is marked so by a load alone, every heading it makes being
 * new; after the save that follows, it is written again only where something was edited, and a plain read edits
 * nothing. Putting a held value in and back would otherwise leave the setting marked, and the next save would write
 * the settings over a file the player had edited by hand since the game last read it, in the layout this edition
 * writes - with no server, kept.
 *
 * <p>
 * Nothing is held in a world of one's own: a single-player or LAN host's own settings are its own, and its list is
 * what its guests are sent ({@link ClientSettingsBook}). The guard is asked when rules are taken and when a read opens.
 */
public final class ClientSettingsHeld {

    private static final ClientSettingRules.Visit VISIT = new ClientSettingRules.Visit();

    /** The player's own value of each held setting, as the file has it, taken as the last read closed. */
    private static final Map<String, String> OWN = new HashMap<String, String>();

    /** What has been said not to apply here this visit, so each is said once. */
    private static final Set<String> SAID = new TreeSet<String>();

    private ClientSettingsHeld() {}

    // ------------------------------------------------------------------
    // The rules
    // ------------------------------------------------------------------

    /**
     * Takes a server's rules for the visit, each checked against this client's own settings; whether anything held
     * moved, which is when a read is wanted. In a world of one's own nothing is taken, and anything held is let go.
     */
    public static synchronized boolean hold(List<ClientSettingRules.Rule> sent) {
        if (Trmt.runningServer() != null) {
            boolean had = VISIT.held();
            release();
            return had;
        }
        boolean moved = VISIT.hold(sent, specs(TrmtConfig.raw()));
        for (Map.Entry<String, String> each : VISIT.notApplying()
            .entrySet()) {
            if (SAID.add(each.getKey())) {
                Trmt.LOG.info("This server sets {}, which does not apply here: {}", each.getKey(), each.getValue());
            }
        }
        return moved;
    }

    /** Lets every rule go; the next read gives the player's own settings back. */
    public static synchronized void release() {
        VISIT.release();
        OWN.clear();
        SAID.clear();
    }

    /** Whether a server holds any of this client's settings now. */
    public static synchronized boolean held() {
        return VISIT.held() && Trmt.runningServer() == null;
    }

    /** How many of the player's settings this server sets. */
    public static synchronized int count() {
        return held() ? VISIT.count() : 0;
    }

    /** The server's value for a forced setting, shown locked in the menu, or null where the setting is the player's. */
    public static synchronized String forced(String category, String key) {
        return held() ? VISIT.forced(ClientSettingRules.id(category, key)) : null;
    }

    /** The server's own value for a suggested setting, whatever the player chose since, or null. */
    public static synchronized String suggestion(String category, String key) {
        if (!held()) return null;
        String id = ClientSettingRules.id(category, key);
        return VISIT.suggested(id) ? VISIT.serverValue(id) : null;
    }

    /**
     * The settings screen changed a setting the server suggests: the change is the player's for the visit, checked
     * against this setting's shape, and never reaches the file. Whether it was taken here - false for a setting the
     * server does not suggest, which the screen writes into the file as ever.
     */
    public static synchronized boolean choose(String category, String key, String value) {
        if (!held() || !VISIT.suggested(ClientSettingRules.id(category, key))) return false;
        String id = ClientSettingRules.id(category, key);
        Setting property = find(TrmtConfig.raw(), id);
        ClientSettingRules.Verdict verdict = property == null ? null
            : ClientSettingRules.check(id, value, spec(category, key, property));
        if (verdict != null && verdict.taken()) VISIT.chose(id, verdict.value);
        return true;
    }

    /** Whether the server suggests this setting. */
    public static synchronized boolean suggested(String category, String key) {
        return held() && VISIT.suggested(ClientSettingRules.id(category, key));
    }

    /** The value a setting is held at this visit, or null where it is the player's. */
    public static synchronized String effective(String category, String key) {
        return held() ? VISIT.effective(ClientSettingRules.id(category, key)) : null;
    }

    /** What the server sent that does not apply here, with why: shown in the menu as not applying. */
    public static synchronized Map<String, String> notApplying() {
        return held() ? new LinkedHashMap<String, String>(VISIT.notApplying()) : new LinkedHashMap<String, String>();
    }

    /**
     * Every setting a server may list, at the value this client is using now - the held value where a server holds it,
     * the player's own elsewhere. What {@code /trmt clientrules copy} sends.
     */
    public static synchronized Map<String, String> current() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        ConfigFile config = TrmtConfig.raw();
        if (config == null) return out;
        for (String name : new ArrayList<String>(config.getCategoryNames())) {
            Category category = config.getCategory(name);
            for (Map.Entry<String, Setting> each : category.getValues()
                .entrySet()) {
                if (!ClientSettingRules.settable(name, each.getKey())) continue;
                String id = ClientSettingRules.id(name, each.getKey());
                String held = held() ? VISIT.effective(id) : null;
                out.put(id, held != null ? held : written(each.getValue()));
            }
        }
        return out;
    }

    /** Every setting this edition's file holds that a server may list, by place, in name order. */
    public static List<String> settable() {
        Set<String> out = new TreeSet<String>();
        ConfigFile config = TrmtConfig.raw();
        if (config == null) return new ArrayList<String>();
        for (String name : new ArrayList<String>(config.getCategoryNames())) {
            for (String key : config.getCategory(name)
                .keySet()) {
                if (ClientSettingRules.settable(name, key)) out.add(ClientSettingRules.id(name, key));
            }
        }
        return new ArrayList<String>(out);
    }

    // ------------------------------------------------------------------
    // The settings' shapes
    // ------------------------------------------------------------------

    /** This edition's settings' shapes, read off the settings themselves. */
    public static ClientSettingRules.Specs specs(final ConfigFile config) {
        return new ClientSettingRules.Specs() {

            @Override
            public ClientSettingRules.Spec spec(String id) {
                Setting property = find(config, id);
                return property == null ? null
                    : ClientSettingsHeld
                        .spec(ClientSettingRules.categoryOf(id), ClientSettingRules.keyOf(id), property);
            }
        };
    }

    /**
     * One setting's shape. A float is held as a double here, as every fraction is; the floats' list says which are
     * floats, as the 1.7.10 edition asks it, though the kind is a double either way. The bounds are the numbers the
     * setting was declared with, written as the file's range line writes a double, where it has any.
     */
    static ClientSettingRules.Spec spec(String category, String key, Setting property) {
        ClientSettingRules.Kind kind;
        if (TrmtConfig.isFloat(category, key)) {
            kind = ClientSettingRules.Kind.DOUBLE;
        } else {
            switch (property.getType()) {
                case BOOLEAN:
                    kind = ClientSettingRules.Kind.BOOLEAN;
                    break;
                case INTEGER:
                    kind = ClientSettingRules.Kind.INTEGER;
                    break;
                case DOUBLE:
                    kind = ClientSettingRules.Kind.DOUBLE;
                    break;
                default:
                    kind = ClientSettingRules.Kind.STRING;
            }
        }
        boolean numeric = kind == ClientSettingRules.Kind.INTEGER || kind == ClientSettingRules.Kind.DOUBLE;
        boolean bounded = numeric && property.isBounded();
        String[] valid = property.getValidValues();
        // The gutter's file goes on reading 0, a value its chooser no longer offers, so a server may set it too.
        if ("angelicaWearGutter".equals(key) && valid != null) {
            valid = java.util.Arrays.copyOf(valid, valid.length + 1);
            valid[valid.length - 1] = "0";
        }
        return new ClientSettingRules.Spec(
            kind,
            property.isList(),
            bounded ? String.valueOf(property.getMin()) : null,
            bounded ? String.valueOf(property.getMax()) : null,
            valid);
    }

    /** A setting by its place, or null where this file has none. */
    static Setting find(ConfigFile config, String id) {
        String category = ClientSettingRules.categoryOf(id);
        String key = ClientSettingRules.keyOf(id);
        if (config == null || category == null || key == null || !config.hasCategory(category)) return null;
        return config.getCategory(category)
            .get(key);
    }

    /** A setting's value as a rule writes it: a list's items joined by a comma and a space. */
    static String written(Setting property) {
        if (!property.isList()) return property.getString();
        StringBuilder out = new StringBuilder();
        for (String item : property.getStringList()) {
            if (out.length() > 0) out.append(", ");
            out.append(item);
        }
        return out.toString();
    }

    private static void put(Setting property, String value) {
        if (property.isList()) {
            List<String> items = new ArrayList<String>();
            for (String part : value.split(",")) {
                String item = part.trim();
                if (!item.isEmpty()) items.add(item);
            }
            property.set(items.toArray(new String[items.size()]));
        } else {
            property.set(value);
        }
    }

    // ------------------------------------------------------------------
    // The settings screen
    // ------------------------------------------------------------------

    /**
     * Takes the settings screen's edits to held settings before its read (ConfigReload.fromGui). The screen writes
     * into the settings the file is saved from: a suggested setting the player changed is taken as their choice for
     * the visit, a forced one is not theirs to change, and either way the value the file is saved with is put back to
     * their own.
     */
    public static synchronized void takeEdits(ConfigFile config) {
        if (!held() || config == null) return;
        for (String id : VISIT.applied()
            .keySet()) {
            Setting property = find(config, id);
            String own = OWN.get(id);
            if (property == null || own == null) continue;
            String now = written(property);
            if (now.equals(own)) continue;
            ClientSettingRules.Verdict verdict = ClientSettingRules
                .check(id, now, spec(ClientSettingRules.categoryOf(id), ClientSettingRules.keyOf(id), property));
            if (verdict.taken()) VISIT.chose(id, verdict.value);
            put(property, own);
        }
    }

    // ------------------------------------------------------------------
    // The read
    // ------------------------------------------------------------------

    /** The settings as a read found them, to be put back as it ends. */
    public static final class Window {

        private final List<Setting> properties = new ArrayList<Setting>();

        private final List<String[]> lists = new ArrayList<String[]>();

        private final List<String> values = new ArrayList<String>();

        /** Each setting's edited mark as the read found it (see the class's third paragraph). */
        private final List<Boolean> marks = new ArrayList<Boolean>();

        private final Map<String, Setting> held = new LinkedHashMap<String, Setting>();
    }

    /**
     * Opens a read: takes every setting as it stands, then puts the held ones in at the visit's values. Null, and
     * nothing touched, while nothing is held.
     */
    static synchronized Window open(ConfigFile config) {
        if (!held() || config == null) return null;
        Window window = new Window();
        for (String name : new ArrayList<String>(config.getCategoryNames())) {
            for (Setting property : config.getCategory(name)
                .getValues()
                .values()) {
                window.properties.add(property);
                window.lists.add(property.isList() ? property.getStringList() : null);
                window.values.add(property.isList() ? null : property.getString());
                window.marks.add(Boolean.valueOf(property.edited()));
            }
        }
        for (Map.Entry<String, String> each : ClientSettingRules.forRead(VISIT.applied())
            .entrySet()) {
            Setting property = find(config, each.getKey());
            if (property == null) continue;
            window.held.put(each.getKey(), property);
            put(property, each.getValue());
        }
        return window;
    }

    /**
     * Closes a read: every setting back as the read found it, so what is saved next is the player's own, and saved
     * only where something but the visit edited it. Each held setting's own value is taken here, which is what tells
     * the settings screen's edit from the player's file.
     */
    static synchronized void close(Window window) {
        if (window == null) return;
        for (int i = 0; i < window.properties.size(); i++) {
            Setting property = window.properties.get(i);
            String[] list = window.lists.get(i);
            if (list != null) {
                if (!Arrays.equals(list, property.getStringList())) property.set(list);
            } else {
                String value = window.values.get(i);
                if (value != null && !value.equals(property.getString())) property.set(value);
            }
            property.edited(window.marks.get(i)
                .booleanValue());
        }
        for (Map.Entry<String, Setting> each : window.held.entrySet()) {
            OWN.put(each.getKey(), written(each.getValue()));
        }
    }
}
