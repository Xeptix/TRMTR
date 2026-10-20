package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.Presets;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.MobEntries;
import com.trmtgtnh.util.OldNames;

/**
 * What the settings screen shows and how, decided without the screen: which headings, in which
 * order, which two numbers slide, and which lists carry a picture beside each line.
 *
 * <p>
 * Plain Java on purpose. The screen itself is Cloth's and cannot be built in a test, so every rule
 * the 1.7.10 screen states ({@code client/gui/TrmtConfigGui.java}) is answered here, where a test can
 * ask it, and {@link ConfigScreen} only asks.
 */
public final class ConfigLayout {

    private ConfigLayout() {}

    /**
     * The top-level headings, in the order the 1.7.10 screen names them.
     *
     * <p>
     * Presets first - the shortest way to a whole opinion, so somebody who wants one does not walk
     * past nine families to find it - then client, the one heading a player on somebody else's server
     * can actually change, then the rest as that edition writes them out by hand (spec CF58,
     * {@code TrmtConfigGui.java:178}). The families follow these, one heading each.
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
     * The six headings the 1.7.10 screen leaves off, which only a text editor reaches there.
     *
     * <p>
     * Named rather than merely absent from {@link #HEADINGS}, so a test can say which six and a
     * seventh cannot slip off unremarked. Xep's decision of 2026-10-10 was that this screen matches.
     */
    static final List<String> LEFT_TO_THE_FILE = Collections.unmodifiableList(
        Arrays.asList(
            TrmtConfig.CATEGORY_GOLEM,
            TrmtConfig.CATEGORY_LOOT,
            TrmtConfig.CATEGORY_WARD,
            TrmtConfig.CATEGORY_LIGHT,
            TrmtConfig.CATEGORY_INTEGRATION,
            TrmtConfig.CATEGORY_SPAWN_ITEMS));

    /**
     * The headings to show, of those the file holds, in the screen's order: {@link #HEADINGS}, then
     * each family's own heading in the enum's order.
     *
     * <p>
     * A family is a heading of its own, {@code families.grass}, as each is an element of its own on
     * the 1.7.10 screen ({@code TrmtConfigGui.java:203}), not a folder inside one "families" heading.
     * Anything else the file holds - the six above, and the bare {@code families} parent - is left
     * to the file, because that screen names what it shows and shows nothing it did not name.
     */
    public static List<String> headings(Collection<String> inFile) {
        List<String> shown = new ArrayList<String>();
        for (String heading : HEADINGS) {
            if (inFile.contains(heading)) shown.add(heading);
        }
        for (SurfaceFamily family : SurfaceFamily.values()) {
            String heading = TrmtConfig.CATEGORY_FAMILIES + ConfigFile.CATEGORY_SPLITTER + family.key();
            if (inFile.contains(heading)) shown.add(heading);
        }
        return shown;
    }

    /**
     * Whether a whole number is drawn as a slider: {@code client.wearGradations} and
     * {@code client.wearRotations}, the two that multiply into the block atlas and so the two most
     * worth pulling back a little at a time on a machine that is struggling - a thing to drag, not to
     * type (spec CF59, {@code TrmtConfigGui.java:236}).
     */
    public static boolean slides(String category, String key) {
        return TrmtConfig.CATEGORY_CLIENT.equals(category)
            && ("wearGradations".equals(key) || "wearRotations".equals(key));
    }

    /** What a list draws beside each of its lines. */
    public enum Icons {
        NONE,
        BLOCK,
        MOB
    }

    /**
     * Which lists carry a picture: {@code surfaces.exclude} and each family's {@code blocks},
     * {@code resistantBlocks} and {@code repairBlocks} a block's icon, {@code multipliers.mobs} a
     * spawn egg (spec CF60, {@code TrmtConfigGui.java:260}). A column of registry names is hard to
     * read back and easy to mistype, and a picture beside each row settles both at once.
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
}
