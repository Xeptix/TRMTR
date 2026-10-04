package com.trmtgtnh.client.gui;

import java.util.Map;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.TrmtConfig;

/**
 * Forge's config object, built on demand from the mod's own settings file.
 *
 * <p>
 * The mod reads and writes its settings itself now, in Forge's file format but with none of Forge's
 * code, so that the later editions have a settings layer to recompile rather than one to rewrite.
 * The one thing that still wants Forge's class is the in-game screen, and it wants it badly: Forge's
 * {@code GuiConfig} brings the category navigation, an editor per type, the array editor with its
 * add and remove and reorder, the comment tooltips, the range sliders, per-setting and whole-screen
 * reset, and the restart warning. That is thousands of lines this mod gets for nothing, and nothing
 * about it is worth reimplementing to satisfy a layering rule.
 *
 * <p>
 * So the screen is handed a copy. Opening it walks the real settings and builds a Forge
 * {@link Configuration} holding the same values, the same types, the same comments, the same bounds
 * and the same defaults; the screen edits that; pressing Done copies the differences back. The copy
 * is then dropped. Nothing else in the mod ever sees it, and no path except this one can.
 *
 * <p>
 * The copy is a copy on purpose rather than a live view. A view would mean giving Forge's property
 * objects a way to reach ours, which is the coupling this whole exercise was to remove - and the
 * screen is the one place where a round trip costs nothing, because it happens when a human opens a
 * menu and again when they close it.
 *
 * <p>
 * This class is the only Forge config code left in the mod and the only piece a later edition
 * deletes rather than ports. From 1.16.5 on there is no {@code GuiConfig} to adapt to: that edition
 * draws its own screen over the same {@link ConfigFile}, which is the point of having one.
 */
public final class ConfigAdapter {

    private ConfigAdapter() {}

    /**
     * The copy currently open on screen, or null when no screen is open.
     *
     * <p>
     * Held because the read-back has to find the same object the screen was given, and the screen
     * does not hand it back. One field is enough: there is one config screen and one player.
     */
    private static Configuration open;

    /** Whether a copy is open, so the read-back can be asked unconditionally. */
    static boolean isOpen() {
        return open != null;
    }

    /**
     * A Forge config holding everything the real settings hold.
     *
     * <p>
     * Built by asking Forge for each setting at its <em>default</em> and then setting the value,
     * rather than by asking for it at its current value. The difference is the whole of the reset
     * button: a property whose default is whatever it already holds is a property the screen cannot
     * offer to put back, and the mod has settings nobody would ever find their way back to by hand.
     */
    static Configuration project() {
        ConfigFile mine = TrmtConfig.raw();
        Configuration theirs = new Configuration();
        if (mine == null) return theirs;

        for (String name : mine.getCategoryNames()) {
            ConfigFile.Category category = mine.getCategory(name);
            theirs.setCategoryComment(name, category.getComment());
            for (Map.Entry<String, ConfigFile.Setting> each : category.getValues()
                .entrySet()) {
                ConfigFile.Setting setting = each.getValue();
                if (setting == null) continue;
                carry(theirs, name, setting);
            }
        }
        open = theirs;
        return theirs;
    }

    /** One setting, as the property the screen will draw. */
    private static void carry(Configuration theirs, String category, ConfigFile.Setting setting) {
        String key = setting.getName();
        String comment = setting.getComment();
        Property property;

        if (setting.isList() && setting.getType() == ConfigFile.Type.INTEGER) {
            // A list of whole numbers, which is the dimension list and nothing else. Given to Forge
            // as one so that its array editor refuses text, as it does on 1.7.10 - handing it a
            // string list instead would compile, draw and let somebody type a dimension name in.
            property = theirs.get(
                category,
                key,
                setting.getDefaultList() == null ? new int[0] : whole(setting.getDefaultList()),
                comment);
            property.set(setting.getStringList());
        } else if (setting.isList()) {
            String[] def = setting.getDefaultList();
            property = theirs.get(category, key, def == null ? new String[0] : def, comment);
            property.set(setting.getStringList());
        } else if (setting.getType() == ConfigFile.Type.BOOLEAN) {
            property = theirs.get(category, key, "true".equalsIgnoreCase(setting.getDefault()), comment);
            property.set(setting.getBoolean());
        } else if (setting.getType() == ConfigFile.Type.INTEGER) {
            property = setting.isBounded() ? theirs.get(
                category,
                key,
                whole(setting.getDefault()),
                comment,
                (int) setting.getMin(),
                (int) setting.getMax()) : theirs.get(category, key, whole(setting.getDefault()), comment);
            property.set(setting.getInt());
        } else if (setting.getType() == ConfigFile.Type.DOUBLE) {
            property = setting.isBounded()
                ? theirs
                    .get(category, key, fractional(setting.getDefault()), comment, setting.getMin(), setting.getMax())
                : theirs.get(category, key, fractional(setting.getDefault()), comment);
            property.set(setting.getDouble());
        } else if (setting.getValidValues() != null) {
            property = theirs.get(category, key, setting.getDefault(), comment, setting.getValidValues());
            property.set(setting.getString());
        } else {
            property = theirs.get(category, key, setting.getDefault(), comment);
            property.set(setting.getString());
        }

        if (property == null) return;
        if (setting.getLanguageKey() != null) property.setLanguageKey(setting.getLanguageKey());
        if (setting.requiresMcRestart()) property.setRequiresMcRestart(true);
    }

    /**
     * Copies back what the screen changed, and says whether anything did.
     *
     * <p>
     * Called from the config-changed event, through the proxy, by {@code TrmtConfig.ChangeListener}.
     * Forge posts that event after writing the screen's edits into its properties and the mod
     * reloads from it, so the handler is the one place standing between the two - which is why the
     * call is there and not in the screen. Doing it from the screen would mean asking Forge to save
     * its entries a second time and relying on that being re-entrant, which it happens to be and
     * should not have to be.
     *
     * <p>
     * Compared rather than assumed: Forge marks a property changed when the screen touches it at
     * all, and a player who opens a setting, looks at it and closes it again should not cause a
     * save, a reload and a packet to every client.
     */
    public static boolean readBack() {
        Configuration theirs = open;
        open = null;
        ConfigFile mine = TrmtConfig.raw();
        if (theirs == null || mine == null) return false;

        int moved = 0;
        for (String name : theirs.getCategoryNames()) {
            ConfigCategory category = theirs.getCategory(name);
            if (!mine.hasCategory(name)) continue;
            ConfigFile.Category ours = mine.getCategory(name);
            for (Property property : category.getValues()
                .values()) {
                if (property == null || property.getName() == null) continue;
                ConfigFile.Setting setting = ours.get(property.getName());
                if (setting == null) continue;
                if (setting.isList()) {
                    if (!same(setting.getStringList(), property.getStringList())) {
                        setting.set(property.getStringList());
                        moved++;
                    }
                } else if (!setting.getString()
                    .equals(property.getString())) {
                        setting.set(property.getString());
                        moved++;
                    }
            }
        }
        if (moved > 0) Trmt.LOG.debug("Config screen changed {} setting(s)", Integer.valueOf(moved));
        return moved > 0;
    }

    /** Drops the copy without reading it, for a screen closed by any route but Done. */
    public static void discard() {
        open = null;
    }

    private static boolean same(String[] ours, String[] theirs) {
        if (ours == null || theirs == null) return ours == theirs;
        if (ours.length != theirs.length) return false;
        for (int i = 0; i < ours.length; i++) {
            if (ours[i] == null ? theirs[i] != null : !ours[i].equals(theirs[i])) return false;
        }
        return true;
    }

    private static int whole(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    /** A list of numbers as numbers, skipping anything that is not one, as the reader does. */
    private static int[] whole(String[] text) {
        int[] held = new int[text.length];
        int kept = 0;
        for (String each : text) {
            int one;
            try {
                one = Integer.parseInt(each.trim());
            } catch (NumberFormatException notANumber) {
                continue;
            }
            // Parsed first, stored second. The obvious form - held[kept++] = parseInt(...) -
            // increments the index before the parse runs, because Java evaluates an array subscript
            // before the right-hand side, so a bad entry would leave a nought behind and count it.
            held[kept++] = one;
        }
        if (kept == held.length) return held;
        int[] exact = new int[kept];
        System.arraycopy(held, 0, exact, 0, kept);
        return exact;
    }

    private static double fractional(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException notANumber) {
            return 0.0d;
        }
    }
}
