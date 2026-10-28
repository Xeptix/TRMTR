package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.trmtgtnh.client.settings.ListLine;
import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.client.settings.SliderRule;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.Presets;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.MobEntries;
import com.trmtgtnh.util.OldNames;

/**
 * What this mod's own settings screen shows and how, decided without the screen: which tabs, in which order, which
 * settings slide, what kind of editor each setting gets, and what each list's lines name (0.9.228; spec M1, M7, M16,
 * S2, S7 and survey 2.2).
 *
 * <p>
 * The tabs begin with the headings Forge's screen here names ({@link #HEADINGS}, spec CF58), in the same order, so the
 * two screens agree where they overlap; then the six categories the file alone held until 0.9.228 (Xep: they "become
 * tabs"), and the twelve families are not tabs of their own but sub-tabs under Surfaces. The 1.7.10 edition and the
 * others carry a class of this name with the same answers; this one reads the mod's own {@link ConfigFile}, and also
 * says what a block or mob line of Cloth Config's screen stands for, which is this edition's other screen.
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
            ConfigFile.CATEGORY_GENERAL,
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
     * The language key a heading reads on Cloth's screen (0.9.228; Xep, 2026-10-25, "Use the tab names"): the own
     * screen's word for that tab, or for a family its sub-tab's ({@code SettingsModel.tabLabel} and {@code pageLabel});
     * null for a category neither screen shows, which keeps its own name.
     */
    static String headingKey(String category) {
        String families = TrmtConfig.CATEGORY_FAMILIES + ConfigFile.CATEGORY_SPLITTER;
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
        return TrmtConfig.CATEGORY_FAMILIES + ConfigFile.CATEGORY_SPLITTER + family.key();
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
     * The headings Cloth Config's screen shows, of those the file holds, in its order: every tab of the own screen
     * ({@link #TABS}), the six the file alone held included, then each family's heading in the enum's order (0.9.228;
     * Xep, answering the specification's open question 3 of Forge's screen in the older editions: "If there are
     * settings missing then yeah we should add all categories there too"), so the other screen holds every setting the
     * own one does, as Forge's does in the 1.7.10 edition.
     */
    public static List<String> otherScreenHeadings(Collection<String> inFile) {
        List<String> shown = new ArrayList<String>();
        for (String heading : TABS) {
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

    /**
     * The multipliers page's order (0.9.228): the movers' switches, the curated list's, the player, mount and lead
     * numbers, the three groups', the mobs list, then the ten cap settings, each switch before its number. A file from
     * before 0.9.228 holds the new settings after its old ones, since they were put in after it was written; both
     * screens
     * show this order whatever the file's.
     */
    static final List<String> MULTIPLIERS_ORDER = Collections.unmodifiableList(
        Arrays.asList(
            "fromPlayers",
            "fromLeashedMobs",
            "fromHostileMobs",
            "fromPassiveMobs",
            "fromVillagers",
            "fromCustom",
            "player",
            "mounted",
            "leashed",
            "hostile",
            "passive",
            "villager",
            "mobs",
            "capPlayers",
            "playersPerChunk",
            "capHostileMobs",
            "hostilePerChunk",
            "capPassiveMobs",
            "passivePerChunk",
            "capVillagers",
            "villagersPerChunk",
            "capCustom",
            "customPerChunk"));

    /**
     * A heading's settings in the order its page shows them: the multipliers in {@link #MULTIPLIERS_ORDER}, anything
     * else they hold after those in the file's order; every other heading in the file's order.
     */
    public static List<String> ordered(String category, Collection<String> inFile) {
        List<String> out = new ArrayList<String>();
        if (TrmtConfig.CATEGORY_MULTIPLIERS.equals(category)) {
            for (String key : MULTIPLIERS_ORDER) {
                if (inFile.contains(key)) out.add(key);
            }
        }
        for (String key : inFile) {
            if (!out.contains(key)) out.add(key);
        }
        return out;
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
    static final String REMEMBERED = Presets.CATEGORY + ConfigFile.CATEGORY_SPLITTER + "remembered";

    /** The one color setting (spec M10). */
    static final String COLOR_CATEGORY = TrmtConfig.CATEGORY_CLIENT;

    static final String COLOR_KEY = "desirePathColor";

    /**
     * A number's kind as the shared code names it: {@code 'I'} whole, {@code 'F'} float, {@code 'D'} double, else 0.
     */
    static char numberKind(String category, String key, ConfigFile.Setting setting) {
        if (setting == null || setting.isList()) return 0;
        switch (setting.getType()) {
            case INTEGER:
                return 'I';
            case DOUBLE:
                // This edition's file holds every float as a double ({@code ConfigFile.getFloat} writes 'D'), so the
                // float's own decimal comes from the list the reads fill (spec M20.3), not from how the file holds it.
                return TrmtConfig.isFloat(category, key) ? 'F' : 'D';
            default:
                return 0;
        }
    }

    /**
     * A setting's least value as the shared code reads a bound, or null where it has none: {@code ConfigFile} keeps
     * the bounds as numbers and says whether there are any, where Forge's {@code Property} keeps them as words.
     */
    static String minOf(ConfigFile.Setting setting) {
        return setting == null || !setting.isBounded() ? null : ConfigWords.number(setting.getMin());
    }

    /** A setting's greatest value, as {@link #minOf} reads the least. */
    static String maxOf(ConfigFile.Setting setting) {
        return setting == null || !setting.isBounded() ? null : ConfigWords.number(setting.getMax());
    }

    /** The editor a setting is given (spec 1.8): every setting has exactly one, decided here. */
    public static Editor editorFor(String category, String key, ConfigFile.Setting setting) {
        if (category != null && (category.equals(REMEMBERED) || category.startsWith(REMEMBERED + "."))) {
            return Editor.REMEMBERED;
        }
        if (setting == null) return Editor.TEXT;
        if (setting.isList()) return Editor.LIST;
        if (setting.getType() == ConfigFile.Type.BOOLEAN) return Editor.SWITCH;
        String[] choices = setting.getValidValues();
        if (choices != null && choices.length > 0) return Editor.CHOOSER;
        if (COLOR_CATEGORY.equals(category) && COLOR_KEY.equals(key)) return Editor.COLOR;
        char kind = numberKind(category, key, setting);
        if (kind != 0) {
            return slides(kind, minOf(setting), maxOf(setting), category, key) ? Editor.SLIDER : Editor.NUMBER;
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
        if (category.startsWith(TrmtConfig.CATEGORY_FAMILIES + ConfigFile.CATEGORY_SPLITTER)
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
        String family = TrmtConfig.CATEGORY_FAMILIES + ConfigFile.CATEGORY_SPLITTER;
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
        if (ConfigFile.CATEGORY_GENERAL.equals(category)) {
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
        if (category.startsWith(TrmtConfig.CATEGORY_FAMILIES + ConfigFile.CATEGORY_SPLITTER)) {
            return "wearsThroughTo".equals(key);
        }
        if (ConfigFile.CATEGORY_GENERAL.equals(category)) {
            return "demoContainer".equals(key) || "tamperGrades".equals(key);
        }
        if (TrmtConfig.CATEGORY_REINFORCE.equals(category)) return "materials".equals(key);
        if (TrmtConfig.CATEGORY_GOLEM.equals(category)) {
            return "arms".equals(key) || "bodyTop".equals(key) || "bodyBottom".equals(key);
        }
        return false;
    }

    // ------------------------------------------------------------------
    // What Cloth Config's screen asks, this edition's other screen
    // ------------------------------------------------------------------

    /**
     * The registry name one block-list line stands for, or null where there is nothing to look up.
     *
     * <p>
     * The split is {@code SurfaceRegistry.applyList}'s, which is the authority on what these lines
     * mean: {@code modid:block}, optionally {@code :meta} or {@code :*}, and a name 1.13 retired read
     * as the block it became, its metadata choosing which. Repeated rather than shared, as 1.7.10's
     * {@code BlockIconArrayEntry.build} repeats it, because reaching into classification to change a
     * working method for a picture is a poor trade.
     */
    public static String blockName(String raw) {
        if (raw == null) return null;
        String entry = raw.trim();
        if (entry.isEmpty()) return null;
        int meta = -1;
        String name = entry;
        int lastColon = entry.lastIndexOf(':');
        if (lastColon > 0 && entry.indexOf(':') != lastColon) {
            String tail = entry.substring(lastColon + 1);
            name = entry.substring(0, lastColon);
            if (!"*".equals(tail)) {
                try {
                    meta = Integer.parseInt(tail);
                } catch (NumberFormatException wildcardOrTypo) {
                    meta = -1;
                }
            }
        }
        if (name.isEmpty()) return null;
        return OldNames.one(name, meta)
            .toLowerCase(Locale.ROOT);
    }

    /**
     * The registry names one mob-list line may stand for, first choice first, or none.
     *
     * <p>
     * The line is {@code MobEntries}' to read - a name, then an optional {@code :weight} - and a name
     * with a namespace is the registry name itself, while one without is 1.7.10's spelling and read as
     * what it became. {@code *} names every mob and has no picture.
     */
    public static List<String> mobNames(String raw) {
        MobEntries.Entry entry = MobEntries.parse(raw);
        if (entry == null || entry.name == null) return Collections.emptyList();
        String name = entry.name.trim()
            .toLowerCase(Locale.ROOT);
        if (name.isEmpty() || "*".equals(name)) return Collections.emptyList();
        if (name.indexOf(':') >= 0) return Collections.singletonList(name);
        return OldNames.mobs(name);
    }

    /**
     * A category's settings in the order a screen shows them: the multipliers page in {@link #MULTIPLIERS_ORDER}, every
     * other heading in the order the file holds it. An upgraded file holds 0.9.228's settings after its own, where they
     * were put in, so the page cannot take the file's order.
     */
    public static <T> List<T> inOrder(String category, java.util.Map<String, T> byName) {
        List<T> shown = new ArrayList<T>();
        if (!TrmtConfig.CATEGORY_MULTIPLIERS.equals(category)) {
            shown.addAll(byName.values());
            return shown;
        }
        for (String name : MULTIPLIERS_ORDER) {
            T held = byName.get(name);
            if (held != null) shown.add(held);
        }
        for (java.util.Map.Entry<String, T> each : byName.entrySet()) {
            if (!MULTIPLIERS_ORDER.contains(each.getKey())) shown.add(each.getValue());
        }
        return shown;
    }

    /** What a mob row draws beside its name. */
    public enum Picture {
        NONE,
        MOB,
        EGG,
        GENERIC
    }

    /**
     * A mob row's picture, in Xep's order (2026-10-20: "We should always try to show the mob itself, and falllback to
     * spawn egg where possible, and perhaps we make a generic spawn egg icon or something as the final fallback"): the
     * mob itself, drawn small, where it can be drawn; else its spawn egg; else this mod's own generic egg. A name no mob
     * answers to gets none, so a known mob without an egg still looks different from a name that is wrong.
     */
    public static Picture picture(boolean known, boolean mobDrawn, boolean hasEgg) {
        if (!known) return Picture.NONE;
        if (mobDrawn) return Picture.MOB;
        if (hasEgg) return Picture.EGG;
        return Picture.GENERIC;
    }
}
