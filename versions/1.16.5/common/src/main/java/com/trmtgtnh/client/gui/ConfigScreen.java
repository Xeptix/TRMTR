package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.network.PacketPushConfig;
import com.trmtgtnh.network.TrmtNetwork;

/**
 * The in-game settings screen, built from this mod's own config file.
 *
 * <p>
 * <strong>This is the one place this port takes a dependency it refuses everywhere else, and the
 * reason is that there is nothing left to refuse it in favour of.</strong> Both older editions build
 * this screen out of Forge's {@code GuiConfig}, {@code IModGuiFactory} and {@code IConfigElement};
 * all three are gone at this version and vanilla has never had a settings screen for a mod. Cloth
 * Config is what every mod on both loaders uses instead, it is one artifact with the same API on
 * each, and the alternative is writing a scrolling property editor from scratch - which is a
 * fortnight of work to arrive somewhere worse. So {@code TrmtConfigGui}, {@code TrmtGuiFactory},
 * {@code ConfigAdapter}, {@code BlockIconArrayEntry} and {@code MobIconArrayEntry} are replaced by
 * this one class, and it is required rather than optional: a settings screen that is sometimes
 * absent is worse than one that asks for a library.
 *
 * <h2>Built from the file, not from a list</h2>
 *
 * <p>
 * Every setting on this screen comes from walking {@link ConfigFile}, which means a setting added to
 * the config appears here with no second place to edit. The other edition's {@code ConfigAdapter}
 * does the same walk into Forge's own element type and is a hundred lines longer for it; what is
 * gone with it is the projection it made - Cloth writes through a save consumer per entry, so there
 * is no second copy to keep in step or to discard on escape.
 *
 * <h2>What is kept exactly</h2>
 *
 * <p>
 * The client category comes first, because it is the only part a player on somebody else's server
 * can actually change. Every other category is still listed, so a single-player world can be tuned
 * without leaving the game. Both of those are the other edition's reasoning and neither is a
 * property of the library.
 *
 * <p>
 * The two buttons are kept as well, at the top of the first category rather than in a row of their
 * own - Cloth owns its own footer, and an entry that is a button is a smaller intrusion than taking
 * that over. They are shown only where there is a world to use them in, which is the same gate the
 * other edition puts on them.
 *
 * <h2>What is not kept</h2>
 *
 * <p>
 * The block and mob pickers drew an icon beside each entry, which is what
 * {@code BlockIconArrayEntry} and {@code MobIconArrayEntry} were for. Cloth's string list has no
 * such hook, so those lists are names here. The names are what the file holds and what a pack
 * author types; the icons were a convenience, and {@code WearIcons.drawIcon} - which is what drew
 * them - is still here and still drawing them on the wear table and its editor.
 */
public final class ConfigScreen {

    private ConfigScreen() {}

    /**
     * The whole screen, or null where there is no config to show.
     *
     * @param parent what Done goes back to, which each loader knows and this does not
     */
    public static Screen build(Screen parent) {
        ConfigFile config = TrmtConfig.raw();
        if (config == null) return null;

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(new TextComponent(Trmt.NAME))
            .setSavingRunnable(ConfigScreen::saved);
        ConfigEntryBuilder entries = builder.entryBuilder();

        boolean first = true;
        for (String name : ordered(config)) {
            ConfigFile.Category category = config.getCategory(name);
            if (category == null) continue;
            ConfigCategory shown = builder.getOrCreateCategory(new TextComponent(title(name)));
            if (first) {
                first = false;
                buttons(shown, parent);
            }
            fill(shown, entries, config, name, category);
        }
        return builder.build();
    }

    /**
     * The categories, the client's first.
     *
     * <p>
     * A nested category - {@code families.grass} - is not a category of its own here: it is a folder
     * inside the one it is named under, which is what Cloth calls a sub-category and what the file's
     * own dotted name has always meant.
     */
    private static List<String> ordered(ConfigFile config) {
        List<String> tops = new ArrayList<String>();
        for (String name : config.getCategoryNames()) {
            String top = name.indexOf('.') < 0 ? name : name.substring(0, name.indexOf('.'));
            if (!tops.contains(top)) tops.add(top);
        }
        // The one piece of ordering this screen insists on, and the other edition's reason for it:
        // on somebody else's server the client category is the only part a player can change.
        if (tops.remove("client")) tops.add(0, "client");
        return tops;
    }

    /** Every setting under one top-level category, with its folders as sub-categories. */
    private static void fill(ConfigCategory shown, ConfigEntryBuilder entries, ConfigFile config, String top,
        ConfigFile.Category category) {
        for (ConfigFile.Setting setting : category.getValues()
            .values()) {
            if (setting != null) shown.addEntry(entry(entries, setting));
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
            SubCategoryBuilder made = entries.startSubCategory(new TextComponent(title(folder.getKey())));
            for (ConfigFile.Setting setting : folder.getValue()) {
                made.add(entry(entries, setting));
            }
            shown.addEntry(made.build());
        }
    }

    /** One setting, as whichever kind of entry its own type asks for. */
    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> entry(ConfigEntryBuilder entries,
        final ConfigFile.Setting setting) {
        Component name = named(setting);
        Component[] note = note(setting);

        if (setting.isList()) {
            return entries.startStrList(name, Arrays.asList(setting.getStringList()))
                .setDefaultValue(Arrays.asList(setting.getDefaultList()))
                .setTooltip(note)
                .setSaveConsumer(values -> setting.set(values.toArray(new String[values.size()])))
                .setExpanded(false)
                .build();
        }

        switch (setting.getType()) {
            case BOOLEAN:
                return entries.startBooleanToggle(name, setting.getBoolean())
                    .setDefaultValue(Boolean.parseBoolean(setting.getDefault()))
                    .setTooltip(note)
                    .setSaveConsumer(value -> setting.set(value.booleanValue()))
                    .build();
            case INTEGER: {
                me.shedaniel.clothconfig2.impl.builders.IntFieldBuilder made = entries
                    .startIntField(name, (int) number(setting.getString(), 0D))
                    .setDefaultValue((int) number(setting.getDefault(), 0D))
                    .setTooltip(note)
                    .setSaveConsumer(value -> setting.set(value.intValue()));
                if (setting.isBounded()) {
                    made.setMin((int) setting.getMin())
                        .setMax((int) setting.getMax());
                }
                return made.build();
            }
            case DOUBLE: {
                me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder made = entries
                    .startDoubleField(name, number(setting.getString(), 0D))
                    .setDefaultValue(number(setting.getDefault(), 0D))
                    .setTooltip(note)
                    .setSaveConsumer(value -> setting.set(String.valueOf(value)));
                if (setting.isBounded()) {
                    made.setMin(setting.getMin())
                        .setMax(setting.getMax());
                }
                return made.build();
            }
            default: {
                // A setting that names its own valid values gets a chooser, not a text box.
                //
                // Found by the unwired sweep on 2026-10-06: ConfigFile.Setting has carried
                // getValidValues() in this edition since it was ported and nothing ever asked it, so
                // every such setting - the wear patterns, the looks - was a free text field here and a
                // constrained chooser in the other two. A player could type a pattern that does not
                // exist and the screen would take it. That is a difference in what the mod lets you
                // do, not in how it looks.
                String[] choices = setting.getValidValues();
                if (choices != null && choices.length > 0) {
                    String current = setting.getString();
                    boolean known = false;
                    for (String one : choices) {
                        if (one.equals(current)) known = true;
                    }
                    // A value already in the file that is not on the list would otherwise be dropped
                    // silently the moment the screen opened; it stays, and choosing anything else
                    // leaves it behind.
                    return needsRestart(
                        entries.startSelector(name, known ? choices : with(choices, current), current)
                            .setDefaultValue(setting.getDefault())
                            .setTooltip(note)
                            .setSaveConsumer(value -> setting.set(value)),
                        setting)
                                .build();
                }
                return needsRestart(
                    entries.startStrField(name, setting.getString())
                        .setDefaultValue(setting.getDefault())
                        .setTooltip(note)
                        .setSaveConsumer(value -> setting.set(value)),
                    setting)
                            .build();
            }
        }
    }

    /**
     * Marks a setting that only takes effect after a restart, where it says so.
     *
     * <p>
     * The other two editions pass {@code requiresMcRestart} to Forge's own screen, which puts a note
     * beside the entry. Cloth has the same idea under a different name, and this edition was carrying
     * the flag without ever telling anybody - so a setting that needed a restart looked exactly like
     * one that did not, and the only way to find out was that nothing happened.
     */
    private static <T, E extends me.shedaniel.clothconfig2.api.AbstractConfigListEntry<T>,
        B extends me.shedaniel.clothconfig2.impl.builders.FieldBuilder<T, E, B>> B needsRestart(B made,
            ConfigFile.Setting setting) {
        made.requireRestart(setting.requiresMcRestart());
        return made;
    }

    /** The valid values with one more on the end, for a file that already holds something else. */
    private static String[] with(String[] choices, String extra) {
        String[] all = new String[choices.length + 1];
        System.arraycopy(choices, 0, all, 0, choices.length);
        all[choices.length] = extra;
        return all;
    }

    /**
     * A setting's comment, wrapped, as the lines of its hover.
     *
     * <p>
     * Every line of it, because these comments are the documentation: several run to a paragraph and
     * one runs to half a page, and a hover that showed the first sentence of those would be a hover
     * that hid the part explaining what the setting is for.
     */
    private static Component[] note(ConfigFile.Setting setting) {
        String comment = setting.getComment();
        if (comment == null || comment.isEmpty()) return new Component[0];
        Minecraft client = Minecraft.getInstance();
        List<String> lines = client == null ? Arrays.asList(comment) : GuiText.wrap(client.font, comment, 260);
        if (setting.isBounded()) lines = withRange(lines, setting);
        Component[] out = new Component[lines.size()];
        for (int at = 0; at < out.length; at++) {
            out[at] = new TextComponent(lines.get(at));
        }
        return out;
    }

    /** The allowed range, said in the hover, because Cloth refuses silently without saying why. */
    private static List<String> withRange(List<String> lines, ConfigFile.Setting setting) {
        List<String> out = new ArrayList<String>(lines);
        out.add("");
        out.add(net.minecraft.ChatFormatting.DARK_GRAY + setting.getRange());
        return out;
    }

    /** The two things this screen does besides editing, where there is a world to do them in. */
    private static void buttons(ConfigCategory shown, Screen parent) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        shown.addEntry(
            new ActionEntry(
                new TextComponent(com.trmtgtnh.util.Translate.get("trmtgtnh.config.push")),
                () -> TrmtNetwork.pushConfig(asLines())));
        shown.addEntry(
            new ActionEntry(
                new TextComponent(com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.button")),
                () -> Minecraft.getInstance()
                    .setScreen(new GuiWearTable(parent))));
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
    private static List<String> asLines() {
        List<String> lines = new ArrayList<String>();
        // The real settings rather than anything this screen is holding: what is offered to somebody
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
    private static String join(String[] values) {
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
    private static void saved() {
        TrmtConfig.save();
        com.trmtgtnh.config.ConfigReload.fromDisk();
    }

    /** A category or setting name as a heading: {@code wearStrength} reads as "Wear strength". */
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
    private static Component named(ConfigFile.Setting setting) {
        String key = setting.getLanguageKey();
        if (key != null && !key.isEmpty()) {
            String said = com.trmtgtnh.util.Translate.get(key);
            if (!key.equals(said)) return new TextComponent(said);
        }
        return new TextComponent(title(setting.getName()));
    }

    private static String title(String name) {
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

    private static double number(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException notANumber) {
            return fallback;
        }
    }

    /** Lower-cased, for the one comparison this file makes on a name. */
    static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
