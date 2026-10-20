package com.trmtgtnh.compat;

/**
 * Jade's and WTHIT's name and mod lines over a worn square, said for the block it covers (spec GF14; 0.9.227 on 1.16.5,
 * Xep: "Fix it, played"), as 1.7.10's Waila names it from the covered block's stack and 1.20.1 and 1.21.1 do from
 * 0.9.226.
 *
 * <p>
 * Both mods write those two lines themselves, each formatted from their own settings - a color and a style in the
 * string - and keep them under a tag in their taggable list ({@code object_name}, {@code mod_name}). So the loader
 * modules find each line by its tag and put the covered block's words where the stand-in's were, keeping every other
 * character of the line, which keeps whatever the player set it to look like. Nothing here names either mod.
 */
public final class CoveredNames {

    /** The tag the name line is kept under, by its path, in Jade 2 and WTHIT 2 alike. */
    public static final String NAME_TAG = "object_name";

    /** The tag the mod line is kept under, by its path. */
    public static final String MOD_TAG = "mod_name";

    private CoveredNames() {}

    /**
     * A line with the stand-in's words put back as the covered block's, or the line itself when it does not hold them
     * or nothing is known to put in their place.
     */
    public static String renamed(String line, String standIn, String covered) {
        if (line == null || standIn == null || standIn.isEmpty() || covered == null || covered.isEmpty()) return line;
        int at = line.indexOf(standIn);
        if (at < 0) return line;
        return line.substring(0, at) + covered + line.substring(at + standIn.length());
    }
}
