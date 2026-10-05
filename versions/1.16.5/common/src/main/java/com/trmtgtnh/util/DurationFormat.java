package com.trmtgtnh.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A span of in-game days, written and read the way a person would say it: {@code 1y 90d 22h 3m 18s}.
 *
 * <p>
 * The number it works on is a count of in-game days - the unit the healing config already speaks
 * in, where a day is the world clock's own day. The breakdown below it is the ordinary calendar:
 * a year is 365 days, a day 24 hours, an hour 60 minutes, a minute 60 seconds. So a heal time of
 * four and a half days reads {@code 4d 12h}, and one of nineteen hundred days reads {@code 5y 75d}.
 * That is a far easier thing to aim at than a bare decimal, which is the whole reason the table
 * shows it this way and lets you type it back.
 *
 * <p>
 * Format and parse are inverses to the second: whatever {@link #format} prints, {@link #parse}
 * reads back to the same whole number of seconds. A test holds them to that.
 */
public final class DurationFormat {

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final long SECONDS_PER_DAY = 86400L;
    private static final long SECONDS_PER_YEAR = 365L * SECONDS_PER_DAY;

    /** A run of digits and an optional unit letter: {@code 90d}, {@code 18s}, or a bare {@code 90}. */
    private static final Pattern TOKEN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([yYdDhHmMsS]?)");

    private DurationFormat() {}

    /**
     * The span as a string, largest non-zero unit first, zero units dropped.
     *
     * <p>
     * A span of nothing is {@code 0s} rather than the empty string, so a cell is never blank and a
     * round trip through {@link #parse} still lands on zero.
     */
    public static String format(double days) {
        long total = Math.round(Math.max(0d, days) * SECONDS_PER_DAY);
        if (total <= 0L) return "0s";

        long years = total / SECONDS_PER_YEAR;
        total -= years * SECONDS_PER_YEAR;
        long remDays = total / SECONDS_PER_DAY;
        total -= remDays * SECONDS_PER_DAY;
        long hours = total / SECONDS_PER_HOUR;
        total -= hours * SECONDS_PER_HOUR;
        long minutes = total / SECONDS_PER_MINUTE;
        long seconds = total - minutes * SECONDS_PER_MINUTE;

        StringBuilder out = new StringBuilder();
        append(out, years, 'y');
        append(out, remDays, 'd');
        append(out, hours, 'h');
        append(out, minutes, 'm');
        append(out, seconds, 's');
        return out.toString()
            .trim();
    }

    /**
     * A short form for a cramped cell: the two largest non-zero units, no more.
     *
     * <p>
     * {@code 5y 75d}, {@code 4d 12h}, {@code 3m 18s}. Enough to read the size of a thing at a
     * glance where the full string would not fit; the full string is what a tooltip and the edit
     * field use.
     */
    public static String formatShort(double days) {
        String full = format(days);
        String[] parts = full.split(" ");
        if (parts.length <= 2) return full;
        return parts[0] + " " + parts[1];
    }

    /**
     * Reads a span back to a number of in-game days.
     *
     * <p>
     * Lenient on purpose: units in any order, spaces optional, upper or lower case, and a bare
     * number taken as days so somebody who just wants "ten days" can type {@code 10}. Anything it
     * cannot make sense of contributes nothing rather than throwing, because this reads a field a
     * person is still typing into.
     */
    public static double parse(String text) {
        if (text == null) return 0d;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0d;

        // A bare number is days.
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException notPlain) {
            // It has units in it; read them below.
        }

        double seconds = 0d;
        boolean any = false;
        Matcher matcher = TOKEN.matcher(trimmed.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            double value;
            try {
                value = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException bad) {
                continue;
            }
            String unit = matcher.group(2);
            if (unit == null || unit.isEmpty()) {
                // A number with no unit, mixed in with ones that have them, is taken as days.
                seconds += value * SECONDS_PER_DAY;
            } else {
                switch (unit.charAt(0)) {
                    case 'y':
                        seconds += value * SECONDS_PER_YEAR;
                        break;
                    case 'd':
                        seconds += value * SECONDS_PER_DAY;
                        break;
                    case 'h':
                        seconds += value * SECONDS_PER_HOUR;
                        break;
                    case 'm':
                        seconds += value * SECONDS_PER_MINUTE;
                        break;
                    case 's':
                        seconds += value;
                        break;
                    default:
                        continue;
                }
            }
            any = true;
        }
        return any ? seconds / SECONDS_PER_DAY : 0d;
    }

    private static void append(StringBuilder out, long value, char unit) {
        if (value <= 0L) return;
        if (out.length() > 0) out.append(' ');
        out.append(value)
            .append(unit);
    }
}
