package com.trmtgtnh.client.gui;

import java.util.Collection;

import com.trmtgtnh.util.MobEntries;
import com.trmtgtnh.util.MobWear;

/**
 * What a row of the mobs list page says, decided without the screen (0.9.228; Xep, 2026-10-20: "each row's multiplier
 * a field of its own, defaulting to 1.0"; docs/goals/0.9.228-decisions.md, "Which mobs wear the ground"): a row's name
 * and its number, the line they write, whether a name answers to no mob, and which picture the row is drawn with. Plain
 * Java over {@link MobEntries} and {@link MobWear}, so the page reads a line
 * exactly as the server does and a test can ask every rule.
 *
 * <p>
 * {@link #ONE}, {@link #nameOf}, {@link #numberOf}, {@link #numberReads}, {@link #line} and {@link #unknown} are the
 * 1.7.10 edition's, word for word: the number field shows the number exactly as the line writes it - "0" stays "0",
 * "2" stays "2" - and "1.0" where the line gives none. Until 0.9.228's fix this edition showed a line with no number as
 * "1", where the reference shows "1.0" (seen in {@code screens-settings-list}, 2026-10-21).
 */
final class MobRows {

    /** What the number field holds for a line that gives none. */
    static final String ONE = "1.0";

    /** The line that counts every mob no line names whose group is off: never red, and naming no mob to draw. */
    static final String EVERY_MOB = "*";

    private MobRows() {}

    /**
     * A line's name: what stands before its number, or the whole line where what follows a colon is no number - which
     * the server reads as a name, so the page does too.
     */
    static String nameOf(String line) {
        MobEntries.Entry entry = MobEntries.parse(line);
        return entry == null ? "" : entry.name;
    }

    /** A line's number as it is written - "1.5" of "Villager:1.5" - and {@link #ONE} where the line gives none. */
    static String numberOf(String line) {
        MobEntries.Entry entry = MobEntries.parse(line);
        if (entry == null || entry.badWeight) return ONE;
        String trimmed = line.trim();
        int colon = trimmed.lastIndexOf(':');
        return colon > 0 ? trimmed.substring(colon + 1)
            .trim() : ONE;
    }

    /** Whether a typed number is one a line can carry: a number, and not below nought. */
    static boolean numberReads(String text) {
        if (text == null) return false;
        try {
            float number = Float.parseFloat(text.trim());
            return !Float.isNaN(number) && !Float.isInfinite(number) && number >= 0f;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /**
     * The line a row writes: {@code name:number}, or the bare name where the number is 1.0 - which is how a bare name
     * reads. Blank for a row with no name, which Done leaves out. Asked only with a number that reads.
     */
    static String line(String name, String number) {
        String named = name == null ? "" : name.trim();
        if (named.isEmpty()) return "";
        String written = number == null ? ONE : number.trim();
        if (!numberReads(written) || Float.parseFloat(written) == 1f) return named;
        return named + ":" + written;
    }

    /** Whether a row's name answers to no mob of the game's - drawn red. Never for '*', nor for a row with no name. */
    static boolean unknown(String line, Collection<String> registered) {
        return !MobWear.unknown(new String[] { line }, registered)
            .isEmpty();
    }

    /**
     * Whether a row's name, as its name field holds it, is one no mob in this game answers to, and so drawn red: never
     * '*', never a row still empty. Asked of the name as a line naming it at {@link #ONE}, so a name field holding a
     * colon and a figure - "Zombie:2" typed into the name - is asked by its whole text, which is what the row shows.
     */
    static boolean red(String name, Collection<String> registered) {
        String named = name == null ? "" : name.trim();
        if (named.isEmpty() || EVERY_MOB.equals(named)) return false;
        return unknown(named + ":" + ONE, registered);
    }

    /** How long a mob's picture takes to turn once about its upright axis (spec C6). */
    static final long TURN_MILLIS = 8000L;

    /**
     * The angle every mob picture stands at, in degrees, at this time on the clock (spec C6): one turn in
     * {@link #TURN_MILLIS}, the same for every mob at one time, so a list of them turns together.
     */
    static float turn(long millis) {
        long into = millis % TURN_MILLIS;
        if (into < 0) into += TURN_MILLIS;
        return into * 360F / TURN_MILLIS;
    }

    /** Which picture a row draws. */
    enum Picture {
        /** The mob itself, drawn small. */
        MOB,
        /** Its spawn egg. */
        EGG,
        /** This mod's own generic egg, {@code textures/gui/mob_egg.png}. */
        GENERIC,
        /** Nothing: a name no mob answers to. */
        NONE
    }

    /**
     * The picture, in Xep's order: "We should always try to show the mob itself, and falllback to spawn egg where
     * possible, and perhaps we make a generic spawn egg icon or something as the final fallback" - and a name no mob
     * answers to gets none, so a known mob without an egg still looks different from an unknown name.
     */
    static Picture pictureFor(boolean known, boolean drawable, boolean egg) {
        if (!known) return Picture.NONE;
        if (drawable) return Picture.MOB;
        if (egg) return Picture.EGG;
        return Picture.GENERIC;
    }
}
