package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.network.PacketPushConfig;

/**
 * What both settings screens say about a setting, and what both do with the settings: the names, the hovers, the
 * lines offered to a server and the save.
 *
 * <p>
 * Taken out of {@link ConfigScreen} in 0.9.227, when 26.x gained a second screen for a game without Cloth Config
 * ({@link WornPathScreen}). {@code ConfigScreen} names Cloth's types and cannot be loaded without them, so what the
 * two share lives here, naming none, and the two screens cannot drift apart in what they call a setting or how they
 * save it. Every answer is the one {@code ConfigScreen} gave before.
 */
public final class ConfigWords {

    /** How wide a hover's lines are wrapped. */
    static final int NOTE_WIDTH = 260;

    private ConfigWords() {}

    /**
     * A heading's own name: {@code families.grass} is headed "Grass", as Forge heads it on the 1.7.10
     * screen by the category's last part.
     */
    static String leaf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    /**
     * What a setting is called on screen: its own translated name where it declares one, and
     * otherwise its key, spaced out.
     *
     * <p>
     * <strong>Three settings declare one</strong> - {@code showErosion},
     * {@code overlayDistanceChunks} and {@code perSurfaceTextures} - and they are the three a player
     * is most likely to go looking for, which is why they were given human names in the first place.
     * Both older editions hand the key to Forge's own config screen, which looks it up and shows the
     * translation; this screen is not Forge's, so nothing was looking it up and the three read "Show
     * erosion", "Overlay distance chunks" and "Per surface textures" here while reading "Show worn
     * paths", "Overlay distance" and "Per-surface wear textures" in the other two. The translations
     * were already shipped in this edition's {@code en_us.json}; only the question was missing.
     *
     * <p>
     * A key that has no translation is shown spaced out rather than raw, which is the test Forge
     * makes too: {@code Language.getOrDefault} hands back the key itself when it knows nothing about
     * it, so a key is translated exactly when the answer differs from the question.
     */
    static Component named(ConfigFile.Setting setting) {
        String key = setting.getLanguageKey();
        if (key != null && !key.isEmpty()) {
            String said = com.trmtgtnh.util.Translate.get(key);
            if (!key.equals(said)) return Component.literal(said);
        }
        return Component.literal(title(setting.getName()));
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
     * A setting's comment, wrapped, as the lines of its hover.
     *
     * <p>
     * Every line of it, because these comments are the documentation: several run to a paragraph and
     * one runs to half a page, and a hover that showed the first sentence of those would be a hover
     * that hid the part explaining what the setting is for. The allowed range follows, because a field
     * refuses a number out of range without saying why.
     */
    static List<String> noteLines(ConfigFile.Setting setting) {
        String comment = setting.getComment();
        if (comment == null || comment.isEmpty()) return new ArrayList<String>();
        Minecraft client = Minecraft.getInstance();
        List<String> lines = client == null ? Arrays.asList(comment) : GuiText.wrap(client.font, comment, NOTE_WIDTH);
        if (!setting.isBounded()) return lines;
        List<String> out = new ArrayList<String>(lines);
        out.add("");
        out.add(net.minecraft.ChatFormatting.DARK_GRAY + setting.getRange());
        return out;
    }

    /**
     * Every simple setting, as category, key and value - the lines offered to a server.
     *
     * <p>
     * Carried from the other edition's screen, including what never goes and why: that is
     * {@link PacketPushConfig#travels}, and a list goes joined on a control character no config value
     * can contain, because the one setting somebody most wants to hand a server is which blocks count
     * as what.
     */
    static List<String> asLines() {
        List<String> lines = new ArrayList<String>();
        // The real settings rather than anything a screen is holding: what is offered to somebody
        // else's machine should come from the mod's own file.
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
                // Dropped whole rather than cut short, as there: half of a block list applied to a
                // server's config would be worse than none of it.
                if (PacketPushConfig.fits(line)) lines.add(line);
            }
        }
        return lines;
    }

    /** A list as one line, on a separator no config value can contain. */
    static String join(String[] values) {
        if (values == null || values.length == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int at = 0; at < values.length; at++) {
            if (at > 0) out.append('\u001f');
            out.append(values[at]);
        }
        return out.toString();
    }

    /**
     * Done was pressed, so what the entries wrote is written to disk and read back.
     *
     * <p>
     * Through the same reload the command uses, so an edit here lands exactly as an edit to the file
     * and a {@code /trmt reload} would - including the surface re-detection and the texture rebuild
     * it may ask for. The other edition goes through Forge's config-changed event to the same place.
     */
    static void saved() {
        // Saved at once and reloaded where the world is (spec CF49): this fires on the client
        // thread, which in single player does not own the world, so the reload waits for the
        // integrated server's thread - and the save goes first, because that thread's next tick never
        // comes when a player goes from the pause menu straight to Save and Quit. It reloads what the
        // entries just wrote rather than the file, and on somebody else's server it is the path that
        // tells the player, once a visit, that the server's numbers stood over their edit (spec CF38).
        com.trmtgtnh.config.ConfigReload.fromGuiDeferred();
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
}
