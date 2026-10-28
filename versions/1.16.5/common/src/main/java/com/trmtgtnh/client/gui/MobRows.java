package com.trmtgtnh.client.gui;

import java.util.Collection;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.MobEntries;

/**
 * What a row of the mobs list page says, decided without the screen (0.9.228; Xep, 2026-10-20: "each row's multiplier
 * a field of its own, defaulting to 1.0"): a row's name and its number, the line they write, and whether a name
 * answers to no mob. Plain Java over {@link MobEntries} and {@link TrmtConfig#unknownMobs}, so the page reads a line
 * exactly as the server does and a test can ask every rule - the 1.7.10 edition's, with this edition's reading of a
 * name: a registry name, or a 1.7.10 spelling read as the mob it became.
 *
 * <p>
 * {@link #ONE}, {@link #nameOf}, {@link #numberOf}, {@link #numberReads} and {@link #line} are the 1.7.10 edition's,
 * word for word: the number field shows the number exactly as the line writes it - "0" stays "0", "2" stays "2" - and
 * "1.0" where the line gives none. Until 0.9.228's fix this edition wrote every number as a float writes it ("0.0",
 * "2.0"), seen in {@code screens-settings-list} on 2026-10-21. Which picture a row draws is still
 * {@link ConfigLayout#picture}, which {@link MobPictures} asks with what it has already resolved.
 */
final class MobRows {

    /** What the number field holds for a line that gives none. */
    static final String ONE = "1.0";

    private MobRows() {}

    /**
     * A line's name: what stands before its number, or the whole line where what follows a colon is no number - which
     * the server reads as a name, so the page does too. A registry name's own colon is part of its name.
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

    /**
     * Whether a row's name answers to no mob of the game's - drawn red. Never for '*', nor for a row with no name. In
     * any spelling: {@link TrmtConfig#unknownMobs} is {@code MobWear.unknown} with every 1.7.10 spelling of a known mob
     * counted as known.
     */
    static boolean unknown(String line, Collection<String> registered) {
        return !TrmtConfig.unknownMobs(new String[] { line }, registered)
            .isEmpty();
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
