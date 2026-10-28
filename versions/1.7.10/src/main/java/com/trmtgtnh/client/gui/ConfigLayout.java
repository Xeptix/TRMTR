package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.trmtgtnh.client.settings.ListLine;
import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.client.settings.SliderRule;
import com.trmtgtnh.config.Presets;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What this mod's own settings screen shows and how, decided without the screen: which tabs, in which order, which
 * settings slide, what kind of editor each setting gets, and what each list's lines name (0.9.228; spec M1, M7, M16,
 * S2, S7 and survey 2.2).
 *
 * <p>
 * The tabs begin with the headings Forge's screen here names ({@link #HEADINGS}, spec CF58), in the same order, so the
 * two screens agree where they overlap; then the six categories the file alone held until 0.9.228 (Xep: they "become
 * tabs"), and the twelve families are not tabs of their own but sub-tabs under Surfaces. The newer editions carry a
 * class of this name with the same answers.
 */
public final class ConfigLayout {

    private ConfigLayout() {}

    /**
     * The headings Forge's screen here names, in its order: presets first - the shortest way to a whole opinion - then
     * client, the one heading a player on somebody else's server can change, then the rest as that screen writes them.
     * The own screen's first thirteen tabs are these ({@code BothSettingsScreensAgreeTest}).
     */
    static final List<String> HEADINGS = Collections.unmodifiableList(
        Arrays.asList(
            Presets.CATEGORY,
            TrmtConfig.CATEGORY_CLIENT,
            Configuration.CATEGORY_GENERAL,
            TrmtConfig.CATEGORY_HEALING,
            TrmtConfig.CATEGORY_MULTIPLIERS,
            TrmtConfig.CATEGORY_TRAMPLING,
            TrmtConfig.CATEGORY_EXPLOSIONS,
            TrmtConfig.CATEGORY_IMPACTS,
            TrmtConfig.CATEGORY_SURFACES,
            TrmtConfig.CATEGORY_PERFORMANCE,
            TrmtConfig.CATEGORY_REINFORCE,
            TrmtConfig.CATEGORY_POTIONS,
            TrmtConfig.CATEGORY_WEATHER));

    /**
     * The six categories only the file held until 0.9.228, which became tabs then (spec S2), after Forge's headings.
     */
    static final List<String> FILE_ONLY = Collections.unmodifiableList(
        Arrays.asList(
            TrmtConfig.CATEGORY_GOLEM,
            TrmtConfig.CATEGORY_LOOT,
            TrmtConfig.CATEGORY_WARD,
            TrmtConfig.CATEGORY_LIGHT,
            TrmtConfig.CATEGORY_INTEGRATION,
            TrmtConfig.CATEGORY_SPAWN_ITEMS));

    /** Every tab the own screen can show, in its order (spec M1.1): Forge's headings, then the six. */
    /**
     * The language key a heading's button reads on Forge's screen (0.9.228): the own screen's word for that tab, or for
     * a family
     * its sub-tab's ({@code SettingsModel.tabLabel} and {@code pageLabel}); null for a category neither screen shows.
     */
    static String headingKey(String category) {
        String families = TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER;
        if (category.startsWith(families)) {
            String family = category.substring(families.length());
            return SurfaceFamily.byKey(family) == null ? null : SettingsKeys.OWN + "family." + family;
        }
        return TABS.contains(category) ? SettingsKeys.OWN + "tab." + category : null;
    }

    static final List<String> TABS;

    static {
        List<String> tabs = new ArrayList<String>(HEADINGS);
        tabs.addAll(FILE_ONLY);
        TABS = Collections.unmodifiableList(tabs);
    }

    /** The heading a family's settings are filed under: {@code families.grass}. */
    static String familyCategory(SurfaceFamily family) {
        return TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER + family.key();
    }

    /**
     * The tabs to show, of those the file holds, in the screen's order (spec M1.1). Surfaces is shown where the file
     * holds it or any family, since the families are its sub-tabs.
     */
    public static List<String> tabs(Collection<String> inFile) {
        List<String> shown = new ArrayList<String>();
        for (String tab : TABS) {
            if (inFile.contains(tab) || TrmtConfig.CATEGORY_SURFACES.equals(tab) && !subTabs(inFile).isEmpty()) {
                shown.add(tab);
            }
        }
        return shown;
    }

    /**
     * Surfaces' sub-tabs, as the categories they open (spec M1.2): {@code surfaces} itself ("All surfaces"), then each
     * family's heading in the enum's order. A family the file does not hold has none.
     */
    public static List<String> subTabs(Collection<String> inFile) {
        List<String> shown = new ArrayList<String>();
        if (inFile.contains(TrmtConfig.CATEGORY_SURFACES)) shown.add(TrmtConfig.CATEGORY_SURFACES);
        for (SurfaceFamily family : SurfaceFamily.values()) {
            String heading = familyCategory(family);
            if (inFile.contains(heading)) shown.add(heading);
        }
        return shown;
    }

    /**
     * The headings Forge's screen shows, of those the file holds, in its order: {@link #HEADINGS}, then each family's
     * heading in the enum's order (spec CF58). Kept for Forge's screen and its test; the own screen asks
     * {@link #tabs}.
     */
    public static List<String> headings(Collection<String> inFile) {
        List<String> shown = new ArrayList<String>();
        for (String heading : HEADINGS) {
            if (inFile.contains(heading)) shown.add(heading);
        }
        for (SurfaceFamily family : SurfaceFamily.values()) {
            String heading = familyCategory(family);
            if (inFile.contains(heading)) shown.add(heading);
        }
        return shown;
    }

    /**
     * Whether a number is drawn as a slider, by the shared rule (spec M7.1): a whole number of span at most 100, a
     * decimal from 0 to at most 1, both bounded - and {@code client.wearGradations} and {@code client.wearRotations}
     * always.
     */
    public static boolean slides(char kind, String min, String max, String category, String key) {
        return SliderRule.slides(kind, min, max, category, key);
    }

    /** The two counted settings Forge's screen slides (spec CF59); every other slider is the own screen's alone. */
    public static boolean slides(String category, String key) {
        return TrmtConfig.CATEGORY_CLIENT.equals(category)
            && ("wearGradations".equals(key) || "wearRotations".equals(key));
    }

    /** The kind of editor a setting is given (spec 1.8 and M16.1). */
    public enum Editor {
        /** On and Off. */
        SWITCH,
        /** A dropdown of its values (M23). */
        CHOOSER,
        /** A slider and its typed field (M7). */
        SLIDER,
        /** A typed number, stepped by Up, Down and the wheel (M6). */
        NUMBER,
        /** A swatch opening the color picker (M10, M17). */
        COLOR,
        /** A list: in place when short, on its own page when long (S1). */
        LIST,
        /** Free words. Never a value holding structured text (M16.1). */
        TEXT,
        /** The presets' memory, shown only under the Advanced fold as rows of its own (M16.2). */
        REMEMBERED
    }

    /** Where the presets keep what Custom brings back (spec M16.2). */
    static final String REMEMBERED = Presets.CATEGORY + Configuration.CATEGORY_SPLITTER + "remembered";

    /** The one color setting (spec M10). */
    static final String COLOR_CATEGORY = TrmtConfig.CATEGORY_CLIENT;

    static final String COLOR_KEY = "desirePathColor";

    /**
     * A number's kind as the shared code names it: {@code 'I'} whole, {@code 'F'} float, {@code 'D'} double, else 0.
     */
    static char numberKind(String category, String key, Property setting) {
        if (setting == null || setting.isList()) return 0;
        switch (setting.getType()) {
            case INTEGER:
                return 'I';
            case DOUBLE:
                return TrmtConfig.isFloat(category, key) ? 'F' : 'D';
            case STRING:
                // Forge's getFloat files a float as words with bounds; the four such are floats.
                return ConfigWords.bounded(setting) ? 'F' : 0;
            default:
                return 0;
        }
    }

    /** The editor a setting is given (spec 1.8): every setting has exactly one, decided here. */
    public static Editor editorFor(String category, String key, Property setting) {
        if (category != null && (category.equals(REMEMBERED) || category.startsWith(REMEMBERED + "."))) {
            return Editor.REMEMBERED;
        }
        if (setting == null) return Editor.TEXT;
        if (setting.isList()) return Editor.LIST;
        if (setting.getType() == Property.Type.BOOLEAN) return Editor.SWITCH;
        String[] choices = setting.getValidValues();
        if (choices != null && choices.length > 0) return Editor.CHOOSER;
        if (COLOR_CATEGORY.equals(category) && COLOR_KEY.equals(key)) return Editor.COLOR;
        char kind = numberKind(category, key, setting);
        if (kind != 0) {
            return slides(kind, setting.getMinValue(), setting.getMaxValue(), category, key) ? Editor.SLIDER
                : Editor.NUMBER;
        }
        return Editor.TEXT;
    }

    /**
     * Whether a value is structured text no player should type whole (spec M16.1): {@code ;}, {@code =}, two commas.
     */
    static boolean structured(String value) {
        if (value == null) return false;
        int commas = value.length() - value.replace(",", "")
            .length();
        return value.indexOf(';') >= 0 || value.indexOf('=') >= 0 || commas > 1;
    }

    /** What a list draws beside each of its lines, as the list page draws it today (spec CF60). */
    public enum Icons {
        NONE,
        BLOCK,
        MOB
    }

    /**
     * Which lists carry a picture on today's list page: {@code surfaces.exclude} and each family's {@code blocks},
     * {@code resistantBlocks} and {@code repairBlocks} a block's icon, {@code multipliers.mobs} a spawn egg (spec
     * CF60). The picture rows of 0.9.228 ask {@link #listKind} instead.
     */
    public static Icons iconsFor(String category, String key) {
        if (category == null || key == null) return Icons.NONE;
        if (TrmtConfig.CATEGORY_SURFACES.equals(category) && "exclude".equals(key)) return Icons.BLOCK;
        if (TrmtConfig.CATEGORY_MULTIPLIERS.equals(category) && "mobs".equals(key)) return Icons.MOB;
        if (category.startsWith(TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER)
            && ("blocks".equals(key) || "resistantBlocks".equals(key) || "repairBlocks".equals(key))) {
            return Icons.BLOCK;
        }
        return Icons.NONE;
    }

    /**
     * What a list's lines name (survey 2.2), as the shared {@link ListLine} reads them: every block, mob, item, fluid,
     * enchantment, grade, family, dimension and texture list, and {@link ListLine.Kind#TEXT} for the rest.
     */
    public static ListLine.Kind listKind(String category, String key) {
        if (category == null || key == null) return ListLine.Kind.TEXT;
        String family = TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER;
        if (category.startsWith(family)) {
            if ("repairBlocks".equals(key)) return ListLine.Kind.BLOCK_OR_ORE;
            if ("blocks".equals(key) || "resistantBlocks".equals(key)) return ListLine.Kind.BLOCK;
            if ("wearsThroughTo".equals(key)) return ListLine.Kind.FAMILY;
            return ListLine.Kind.TEXT;
        }
        if (TrmtConfig.CATEGORY_SURFACES.equals(category)) {
            if ("exclude".equals(key)) return ListLine.Kind.BLOCK_NO_META;
            if (key.startsWith("groundCover")) return ListLine.Kind.BLOCK;
        }
        if (Configuration.CATEGORY_GENERAL.equals(category)) {
            if ("demoContainer".equals(key)) return ListLine.Kind.BLOCK;
            if ("tamperGrades".equals(key)) return ListLine.Kind.GRADE;
            if ("dimensionList".equals(key)) return ListLine.Kind.DIMENSION;
        }
        if (TrmtConfig.CATEGORY_GOLEM.equals(category)
            && ("arms".equals(key) || "bodyTop".equals(key) || "bodyBottom".equals(key))) {
            return ListLine.Kind.BLOCK;
        }
        if (TrmtConfig.CATEGORY_CLIENT.equals(category) && "innerLayerTextures".equals(key)) {
            return ListLine.Kind.SPRITE_PAIR;
        }
        if (TrmtConfig.CATEGORY_MULTIPLIERS.equals(category) && "mobs".equals(key)) return ListLine.Kind.MOB;
        if (TrmtConfig.CATEGORY_REINFORCE.equals(category) && "materials".equals(key)) {
            return ListLine.Kind.ITEM_OR_FLUID;
        }
        if (TrmtConfig.CATEGORY_LIGHT.equals(category) && "materials".equals(key)) return ListLine.Kind.ITEM;
        if (TrmtConfig.CATEGORY_WARD.equals(category)
            && ("hostileMaterials".equals(key) || "passiveMaterials".equals(key))) {
            return ListLine.Kind.ITEM;
        }
        if (TrmtConfig.CATEGORY_HEALING.equals(category)
            && ("mendingEnchantments".equals(key) || "xpBoostEnchantments".equals(key))) {
            return ListLine.Kind.ENCHANT;
        }
        return ListLine.Kind.TEXT;
    }

    /** The lists "tried in the order written", whose lines can be moved (spec S7). */
    public static boolean ordered(String category, String key) {
        if (category == null || key == null) return false;
        if (category.startsWith(TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER)) {
            return "wearsThroughTo".equals(key);
        }
        if (Configuration.CATEGORY_GENERAL.equals(category)) {
            return "demoContainer".equals(key) || "tamperGrades".equals(key);
        }
        if (TrmtConfig.CATEGORY_REINFORCE.equals(category)) return "materials".equals(key);
        if (TrmtConfig.CATEGORY_GOLEM.equals(category)) {
            return "arms".equals(key) || "bodyTop".equals(key) || "bodyBottom".equals(key);
        }
        return false;
    }
}
