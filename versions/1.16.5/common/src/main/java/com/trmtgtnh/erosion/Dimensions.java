package com.trmtgtnh.erosion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * Whether the mod is allowed to work in a level, asked of a setting written in numbers.
 *
 * <p>
 * {@code general.dimensionList} is a list of dimension ids, because in both older editions a
 * dimension <em>is</em> an id. 1.16.5 names them instead, and the setting file is meant to carry
 * between editions unchanged - so the numbers are still there and still have to mean something.
 *
 * <p>
 * <strong>The three vanilla ids are honoured exactly.</strong> -1, 0 and 1 have always meant the
 * nether, the overworld and the end, and they are what almost every list contains - somebody
 * restricting this mod to the overworld writes {@code 0}. Those are translated and the setting works
 * as written.
 *
 * <p>
 * <strong>Any other number cannot be honoured and is named in the log, once.</strong> A modded
 * dimension's id was assigned by the loader at runtime and nothing here can recover which dimension a
 * given number meant - the id is not written in the save, not derivable from the name, and not stable
 * between installs. Guessing would be worse than declining: a wrong guess in a whitelist switches the
 * mod off somewhere it should work, and in a blacklist switches it on somewhere it should not.
 *
 * <p>
 * This is the same bargain the map settings make - kept in the file so a pack can move between
 * versions, named in the log where they cannot do what they say. The honest fix is a list of names,
 * and that is a settings change rather than a port decision.
 */
public final class Dimensions {

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");

    private static final ResourceLocation END = new ResourceLocation("minecraft", "the_end");

    private static volatile boolean complained;

    private Dimensions() {}

    /** Whether this level is one the mod may work in. */
    public static boolean allowed(Level level) {
        if (level == null) return false;
        ResourceLocation name = level.dimension()
            .location();

        sayWhatCannotBeHonoured();

        int asId = idFor(name);
        if (asId == NO_ID) {
            // A dimension none of the numbers can name. Treated as unlisted, which is what the
            // setting's own default - an empty blacklist - already means for everywhere.
            return !TrmtConfig.dimensionListIsWhitelist;
        }
        return TrmtConfig.dimensionAllowed(asId);
    }

    private static final int NO_ID = Integer.MIN_VALUE;

    private static int idFor(ResourceLocation name) {
        if (OVERWORLD.equals(name)) return 0;
        if (NETHER.equals(name)) return -1;
        if (END.equals(name)) return 1;
        return NO_ID;
    }

    /** Names, once, every number in the list that cannot mean a dimension here. */
    private static void sayWhatCannotBeHonoured() {
        if (complained) return;
        complained = true;
        int[] listed = TrmtConfig.dimensionList;
        if (listed == null) return;

        StringBuilder odd = new StringBuilder();
        for (int id : listed) {
            if (id == 0 || id == -1 || id == 1) continue;
            if (odd.length() > 0) odd.append(", ");
            odd.append(id);
        }
        if (odd.length() == 0) return;

        Trmt.LOG.info(
            "general.dimensionList names dimensions this version cannot identify by number: {}. The "
                + "vanilla three are honoured as written; a modded dimension's id was assigned at "
                + "runtime and is not recoverable from a save, so those entries are ignored rather "
                + "than guessed at. The rest of the list still works.",
            odd);
    }
}
