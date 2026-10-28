package com.trmtgtnh.client.gui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.network.PacketPushConfig;

/**
 * What both settings screens say about a setting, and what both do with the settings: the names, the hovers, the
 * lines offered to a server and the save (0.9.228).
 *
 * <p>
 * This edition gained a second screen that release, the mod's own ({@link WornPathScreen}), beside Forge's
 * ({@link TrmtConfigGui}). What the two share lives here, so they cannot drift apart in what they call a setting, in
 * the range they say it has, or in how they save it. Every edition carries a class of this name with the same answers;
 * this one reads the mod's own {@link ConfigFile}, as the newer editions' does, since that is what this edition's
 * settings are, and Forge's screen is handed a copy of it ({@link ConfigAdapter}).
 */
public final class ConfigWords {

    /** How wide a hover's lines are wrapped on the own screen. */
    static final int NOTE_WIDTH = 260;

    private ConfigWords() {}

    /**
     * A heading's own name: {@code families.grass} is headed "Grass", as Forge heads it by the category's last part.
     */
    static String leaf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    /**
     * What a setting is called on screen: its own translated name where it declares one, and otherwise its key, spaced
     * out. Forge's screen asks the same question of the same key - a key is translated exactly when the answer differs
     * from it - so showErosion reads "Show worn paths" on both.
     */
    static String named(ConfigFile.Setting setting) {
        String key = setting.getLanguageKey();
        if (key != null && !key.isEmpty()) {
            String said = I18n.format(key);
            if (!key.equals(said)) return said;
        }
        return title(setting.getName());
    }

    /** A category or setting name as a heading: {@code wearStrength} reads as "Wear strength". */
    static String title(String name) {
        if (name == null || name.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (int at = 0; at < name.length(); at++) {
            char one = name.charAt(at);
            if (at > 0 && Character.isUpperCase(one) && !Character.isUpperCase(name.charAt(at - 1))) {
                out.append(' ')
                    .append(Character.toLowerCase(one));
            } else if (one == '.' || one == '_') {
                out.append(' ');
            } else {
                out.append(at == 0 ? Character.toUpperCase(one) : one);
            }
        }
        return out.toString();
    }

    /**
     * A setting's comment, wrapped, as the lines of its hover on the own screen, then its range or default once.
     *
     * <p>
     * Every line of it, because these comments are the documentation: several run to a paragraph, and a hover that
     * showed the first sentence would hide the part explaining what the setting is for.
     */
    static List<String> noteLines(ConfigFile.Setting setting) {
        return noteLines(setting, new Wrap() {

            @Override
            public List<String> lines(String text) {
                return wrap(text, NOTE_WIDTH);
            }
        });
    }

    /** How a hover's comment is cut into lines: by the game's font on a screen, left whole in a test. */
    interface Wrap {

        List<String> lines(String text);
    }

    /** The hover's lines, the comment cut as {@code wrap} cuts it. */
    static List<String> noteLines(ConfigFile.Setting setting, Wrap wrap) {
        String comment = withoutRange(setting.getComment());
        List<String> out = new ArrayList<String>();
        if (!comment.isEmpty()) out.addAll(wrap.lines(comment));
        String range = rangeLine(setting);
        if (range.isEmpty()) return out;
        if (!out.isEmpty()) out.add("");
        out.add(TextFormatting.DARK_GRAY + range);
        return out;
    }

    static List<String> wrap(String text, int width) {
        Minecraft client = Minecraft.getMinecraft();
        List<String> lines = new ArrayList<String>();
        if (client == null || client.fontRenderer == null) {
            lines.add(text);
            return lines;
        }
        lines.addAll(client.fontRenderer.listFormattedStringToWidth(text, width));
        return lines;
    }

    /**
     * The last line of a setting's hover, the same words in every edition (0.9.228): {@code Range: 0 to 100, default 2}
     * for a bounded number, {@code Default: own} for anything else - and nothing for a list, whose default can be
     * dozens of lines. Each number written as {@link #number(double)} writes it.
     *
     * <p>
     * Until then Forge's screen added its own line after the comment, {@code [range: 0.0 ~ 10.0, default:
     * 0.20000000298023224]} for a float, and on GT New Horizons' copy of Forge the three numbers came out in the wrong
     * places; the own screen says it in these words, and Forge's now says it in the same ({@link TrmtConfigEntries}).
     */
    static String rangeLine(ConfigFile.Setting setting) {
        return line(
            setting.isList(),
            setting.isBounded(),
            number(setting.getMin()),
            number(setting.getMax()),
            setting.getDefault());
    }

    /**
     * The same line from what Forge's screen holds of a setting - its kind by name ({@code INTEGER}, {@code DOUBLE},
     * {@code STRING}, as both {@code Property.Type} and Forge's screen name them), its bounds and its default - so that
     * screen's hover says it in the same words ({@link TrmtConfigEntries}). Forge gives an unbounded number the ends of
     * an int or a double as its bounds, so a number is bounded where either bound is not one of those.
     */
    static String rangeLine(boolean list, String kind, String min, String max, String def) {
        return line(list, forgeBounded(kind, min, max), shown(min), shown(max), def);
    }

    /** The line itself, said one way for both screens. */
    private static String line(boolean list, boolean bounded, String min, String max, String def) {
        if (list) return "";
        String shownDefault = shown(def);
        if (bounded) return "Range: " + min + " to " + max + ", default " + shownDefault;
        return "Default: " + shownDefault;
    }

    /** Whether Forge's screen holds a number whose bounds somebody set, as Forge's own screen decides it. */
    static boolean forgeBounded(String kind, String min, String max) {
        if (!"INTEGER".equals(kind) && !"DOUBLE".equals(kind)) return false;
        double low;
        double high;
        try {
            low = Double.parseDouble(min);
            high = Double.parseDouble(max);
        } catch (RuntimeException notANumber) {
            return false;
        }
        boolean lowSet = low != Integer.MIN_VALUE && low != -Double.MAX_VALUE;
        boolean highSet = high != Integer.MAX_VALUE && high != Double.MAX_VALUE;
        return lowSet || highSet;
    }

    /** A number as the hover writes it: 100.0 is "100", 0.5 is "0.5", 0.2 is "0.2" - never a widened float's tail. */
    static String number(double value) {
        return new BigDecimal(String.valueOf(value)).stripTrailingZeros()
            .toPlainString();
    }

    /**
     * A default or bound as the hover writes it: a number as {@link #number(double)} writes it, anything else as is.
     */
    static String shown(String value) {
        if (value == null) return "";
        try {
            return number(Double.parseDouble(value.trim()));
        } catch (NumberFormatException notANumber) {
            return value;
        }
    }

    /**
     * A comment without what a Forge-style getter appends to it - {@code [range: 2 ~ 32, default: 12]} from
     * {@code getInt} and {@code getFloat}, {@code [default: true]} from {@code getBoolean} and {@code getString} - so
     * the hover says the range and the default once, in {@link #rangeLine}'s words. This edition's file keeps that
     * text apart from the comment, so nothing here carries it today; a comment written with it by hand is read the same.
     */
    static String withoutRange(String comment) {
        if (comment == null) return "";
        String text = comment.trim();
        if (!text.endsWith("]")) return text;
        int at = Math.max(text.lastIndexOf("[range: "), text.lastIndexOf("[default: "));
        return at < 0 ? text
            : text.substring(0, at)
                .trim();
    }

    /** Whether typed text is a value its setting can hold: a number of its kind within its range, or any words. */
    static boolean reads(ConfigFile.Setting setting, String text) {
        String trimmed = text.trim();
        double number;
        switch (setting.getType()) {
            case INTEGER:
                try {
                    number = Integer.parseInt(trimmed);
                } catch (NumberFormatException notWhole) {
                    return false;
                }
                break;
            case DOUBLE:
                try {
                    number = Double.parseDouble(trimmed);
                } catch (NumberFormatException notANumber) {
                    return false;
                }
                if (Double.isNaN(number) || Double.isInfinite(number)) return false;
                break;
            default:
                return true;
        }
        return !setting.isBounded() || number >= setting.getMin() && number <= setting.getMax();
    }

    /** Typed text as it is kept: a number without the spaces round it, words as typed. */
    static String normal(ConfigFile.Setting setting, String text) {
        switch (setting.getType()) {
            case INTEGER:
            case DOUBLE:
                return text.trim();
            default:
                return text;
        }
    }

    static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    static double number(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException notANumber) {
            return fallback;
        }
    }

    /**
     * Every simple setting, as category, key and value - the lines offered to a server.
     *
     * <p>
     * Lists go as well, joined on a control character no config value can contain, because the one setting somebody
     * most wants to hand a server is which blocks count as what. What never goes is decided by
     * {@link PacketPushConfig#travels}: this client's own heading, the quality chooser and the one quality figure filed
     * under surfaces, the presets' bookkeeping, and the enchantment and potion ids.
     */
    static List<String> asLines() {
        List<String> lines = new ArrayList<String>();
        // The real settings rather than anything a screen is holding: what is offered to somebody else's machine
        // should come from the mod's own file - and by the time this button can be pressed, a screen may hold edits
        // nobody has pressed Done on yet.
        ConfigFile config = TrmtConfig.raw();
        if (config == null) return lines;
        for (String name : config.getCategoryNames()) {
            ConfigFile.Category category = config.getCategory(name);
            for (ConfigFile.Setting property : category.getValues()
                .values()) {
                if (property == null || property.getName() == null) continue;
                if (!PacketPushConfig.travels(name, property.getName())) continue;
                String value = property.isList() ? join(property.getStringList()) : property.getString();
                String line = name + "\t" + property.getName() + "\t" + value;
                // Dropped whole rather than cut short. The block lists detection writes run to thousands of
                // characters, and half of one applied to a server's config would be worse than none of it.
                if (PacketPushConfig.fits(line)) lines.add(line);
            }
        }
        return lines;
    }

    /** A list as one line, on a separator no config value can contain. */
    static String join(String[] values) {
        if (values == null || values.length == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append('\u001f');
            out.append(values[i] == null ? "" : values[i]);
        }
        return out.toString();
    }

    /**
     * Done was pressed on the own screen, so what it wrote is saved and reloaded through the same deferred reload
     * Forge's screen reaches through its config-changed event (spec CF49, {@code CommonProxy.ConfigScreenListener}):
     * saved at once, then reloaded on the server's thread wherever a server runs, and on somebody else's server the path
     * that tells the player, once a visit, that the server's numbers stood over their edit (spec CF38).
     */
    static void saved() {
        com.trmtgtnh.config.ConfigReload.fromGuiDeferred();
    }
}
