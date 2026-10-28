package com.trmtgtnh.client.gui;

import java.util.Collection;

import com.trmtgtnh.config.MobNames;
import com.trmtgtnh.util.MobEntries;

/**
 * What a row of the mobs list page says, decided without the screen (0.9.228; Xep, 2026-10-20: "each row's multiplier
 * a field of its own, defaulting to 1.0"): a row's name and its number, the line they write, whether a name answers to
 * no mob, and which picture the row is drawn with. Plain Java over {@link MobEntries} and the rules, so the page reads
 * a line exactly as the server does and a test can ask every rule - the 1.7.10 edition's {@code MobRows}, with this
 * game's names: a registry name standing alone, {@code minecraft:pig}, is a name and its number 1.0, and a 1.7.10
 * spelling answers wherever the mob it is read as is registered ({@link MobNames#answering}).
 */
final class MobRows {

    /** What the number field holds for a line that gives none. */
    static final String ONE = "1.0";

    private static final String EVERY_MOB = "*";

    private MobRows() {}

    /**
     * A line's name: what stands before its number, or the whole line where what follows its last colon is no number -
     * a registry name standing alone, or a line the server reads as a name - so the page reads it as the server does.
     */
    static String nameOf(String line) {
        MobEntries.Entry entry = MobEntries.parse(line);
        return entry == null ? "" : entry.name;
    }

    /** A line's number as it is written - "1.5" of "minecraft:villager:1.5" - and {@link #ONE} where it gives none. */
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
        return !MobNames.unknown(new String[] { line }, registered)
            .isEmpty();
    }

    /** Whether a mob of the game's answers to this line's name, compared without regard to case as the server does. */
    private static boolean answers(String line, String name, Collection<String> registered) {
        if (registered == null) return false;
        for (String each : MobNames.answering(new String[] { line }, registered)) {
            if (each != null && each.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** The picture a row is drawn with. */
    enum Picture {
        /** The mob itself, drawn small. */
        MOB,
        /** Its spawn egg. */
        EGG,
        /** This mod's own generic egg, for a mob with neither. */
        GENERIC,
        /** Nothing: no name, '*', or a name no mob answers to. */
        NONE
    }

    /**
     * Which picture, in Xep's order ("always try to show the mob itself, and fallback to spawn egg where possible, and
     * perhaps we make a generic spawn egg icon or something as the final fallback"): the mob itself where it can be
     * drawn, else its spawn egg where it has one, else the generic egg - so a mob without an egg still looks different
     * from a name no mob answers to, which gets none.
     */
    static Picture picture(String line, Collection<String> registered, boolean mobDrawn, boolean hasEgg) {
        String name = nameOf(line).trim();
        if (name.isEmpty() || EVERY_MOB.equals(name) || !answers(line, name, registered)) return Picture.NONE;
        if (mobDrawn) return Picture.MOB;
        if (hasEgg) return Picture.EGG;
        return Picture.GENERIC;
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
}
