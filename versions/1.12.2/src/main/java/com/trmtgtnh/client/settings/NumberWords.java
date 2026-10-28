package com.trmtgtnh.client.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * How the settings screen writes, checks and steps a number (0.9.228; spec M6, M20 and 1.8).
 *
 * <p>
 * Every number is written as its own shortest decimal: 100.0 is "100", 0.5 is "0.5", and a float setting read back
 * from a file that holds {@code 0.10000000149011612} - the float 0.1 widened to a double, which is what Forge wrote for
 * years - shows as "0.1", because that is the number the game is using ({@link #shown}). Typed text is checked before
 * it is kept, and refused with the reason in words ({@link #check}); Up, Down and the wheel step it by an amount that
 * suits its range ({@link #step}), in decimal arithmetic, so 0.1 and 0.2 make 0.3 and never 0.30000000000000004.
 */
public final class NumberWords {

    /** A number as a person writes one: digits, one point at most, an exponent. No "1f", no "NaN", no hex. */
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private static final Pattern WHOLE = Pattern.compile("[+-]?\\d+");

    private NumberWords() {}

    /** Why typed text was refused: one of the {@code refuse.*} keys, and the number it names where it names one. */
    public static final class Refusal {

        public final String key;

        public final String arg;

        public Refusal(String key, String arg) {
            this.key = key;
            this.arg = arg;
        }

        @Override
        public String toString() {
            return arg == null ? key : key + "(" + arg + ")";
        }
    }

    /** A number as the screen writes it: 100.0 is "100", 0.5 is "0.5", 0.2 is "0.2" - never a widened float's tail. */
    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return String.valueOf(value);
        return plain(new BigDecimal(String.valueOf(value)));
    }

    private static String plain(BigDecimal value) {
        if (value.signum() == 0) return "0";
        return value.stripTrailingZeros()
            .toPlainString();
    }

    /**
     * A value as its field shows it (spec M20): a float setting's text as the float's own shortest decimal, a double's
     * as the double's, a whole number as itself. Text that does not read as its kind is shown as it is.
     *
     * @param kind {@code 'I'} whole, {@code 'F'} a float setting, {@code 'D'} a double
     */
    public static String shown(String raw, char kind) {
        if (raw == null) return "";
        String text = raw.trim();
        try {
            if (kind == 'F') {
                if (!DECIMAL.matcher(text)
                    .matches()) return raw;
                return number(Double.parseDouble(Float.toString(Float.parseFloat(text))));
            }
            if (kind == 'D') {
                if (!DECIMAL.matcher(text)
                    .matches()) return raw;
                return number(Double.parseDouble(text));
            }
            if (kind == 'I' && WHOLE.matcher(text)
                .matches()) return Long.toString(Long.parseLong(text));
        } catch (NumberFormatException unreadable) {
            return raw;
        }
        return raw;
    }

    /**
     * The last line of a setting's hover, as both screens have said it since step 4: {@code Range: 0 to 100, default 2}
     * for a bounded number, {@code Default: own} for anything else, and nothing for a list, whose default can be dozens
     * of lines.
     *
     * @param kind its kind by name, as Forge's {@code Property.Type} names it: {@code INTEGER}, {@code DOUBLE},
     *             {@code STRING} (a float Forge declares as words with bounds)
     */
    public static String rangeLine(boolean list, String kind, String min, String max, String def) {
        if (list) return "";
        String shownDefault = shownBound(def);
        if (bounded(list, kind, min, max, def)) {
            return "Range: " + shownBound(min) + " to " + shownBound(max) + ", default " + shownDefault;
        }
        return "Default: " + shownDefault;
    }

    /** A default or bound as the hover writes it: a number as {@link #number(double)} writes it, words as they are. */
    public static String shownBound(String value) {
        if (value == null) return "";
        try {
            return number(Double.parseDouble(value.trim()));
        } catch (NumberFormatException notANumber) {
            return value;
        }
    }

    /** Whether a setting is a number with a range somebody set, rather than the ends of an int or a double. */
    public static boolean bounded(boolean list, String kind, String min, String max, String def) {
        if (list) return false;
        boolean words = "STRING".equals(kind);
        if (!"INTEGER".equals(kind) && !"DOUBLE".equals(kind) && !words) return false;
        double low;
        double high;
        try {
            low = Double.parseDouble(min);
            high = Double.parseDouble(max);
            if (words) Double.parseDouble(def);
        } catch (RuntimeException notANumber) {
            return false;
        }
        return set(low, high);
    }

    /** Whether either bound is one somebody set. */
    private static boolean set(double min, double max) {
        boolean low = min != Integer.MIN_VALUE && min != -Double.MAX_VALUE && min != Long.MIN_VALUE;
        boolean high = max != Integer.MAX_VALUE && max != Double.MAX_VALUE && max != Long.MAX_VALUE;
        return low || high;
    }

    /** A bound as a number, or null when it is absent or one of the ends nobody set. */
    static BigDecimal bound(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!DECIMAL.matcher(trimmed)
            .matches()) return null;
        double value = Double.parseDouble(trimmed);
        if (value == Integer.MIN_VALUE || value == Integer.MAX_VALUE
            || value == -Double.MAX_VALUE
            || value == Double.MAX_VALUE
            || value == Long.MIN_VALUE
            || value == Long.MAX_VALUE) return null;
        return new BigDecimal(trimmed);
    }

    /**
     * Whether typed text is a value its setting can hold (spec M6.2): null when it is, else why not - empty, not a
     * number, not a whole number, below its least or above its most, in that order.
     *
     * @param kind {@code 'I'} for a whole number; {@code 'D'} or {@code 'F'} for a decimal
     */
    public static Refusal check(String text, char kind, String min, String max) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return new Refusal(SettingsKeys.REFUSE_EMPTY, null);
        if (!DECIMAL.matcher(trimmed)
            .matches()) return new Refusal(SettingsKeys.REFUSE_NOT_NUMBER, null);
        if (kind == 'I' && !WHOLE.matcher(trimmed)
            .matches()) return new Refusal(SettingsKeys.REFUSE_NOT_WHOLE, null);
        BigDecimal value = new BigDecimal(trimmed);
        BigDecimal low = bound(min);
        BigDecimal high = bound(max);
        if (low != null && value.compareTo(low) < 0) return new Refusal(SettingsKeys.REFUSE_BELOW, plain(low));
        if (high != null && value.compareTo(high) > 0) return new Refusal(SettingsKeys.REFUSE_ABOVE, plain(high));
        return null;
    }

    /**
     * How far one press of Up or Down moves a number (spec 1.8): a whole number with a long range by a hundredth of its
     * span's order of magnitude, never under 1 - 0 to 1048576 by 10000, 0 to 512 by 1 - and an unbounded one by 1; a
     * decimal by a hundredth when its span is at most 1, a tenth up to 100, and 1 beyond, or unbounded.
     */
    public static BigDecimal stepSize(char kind, String min, String max) {
        BigDecimal low = bound(min);
        BigDecimal high = bound(max);
        if (kind == 'I') {
            if (low == null || high == null) return BigDecimal.ONE;
            BigDecimal step = magnitude(high.subtract(low)).movePointLeft(2);
            return step.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : step;
        }
        if (low == null || high == null) return BigDecimal.ONE;
        BigDecimal span = high.subtract(low);
        if (span.compareTo(BigDecimal.ONE) <= 0) return new BigDecimal("0.01");
        if (span.compareTo(new BigDecimal(100)) <= 0) return new BigDecimal("0.1");
        return BigDecimal.ONE;
    }

    /** The power of ten at or below a span of at least 1: 1048576 gives 1000000, 512 gives 100, under 1 gives 1. */
    static BigDecimal magnitude(BigDecimal span) {
        if (span.compareTo(BigDecimal.ONE) < 0) return BigDecimal.ONE;
        int digits = span.setScale(0, RoundingMode.FLOOR)
            .toPlainString()
            .length();
        return BigDecimal.ONE.movePointRight(digits - 1);
    }

    /**
     * Typed text stepped once (spec 1.6 and M6.4): by {@link #stepSize}, ten times that with Shift, a tenth of it with
     * Ctrl (a whole number never by less than 1), clamped to the range. A value that is refused steps from the default.
     *
     * @param direction 1 up, -1 down
     */
    public static String step(String text, char kind, String min, String max, String def, int direction, boolean shift,
        boolean ctrl) {
        BigDecimal from;
        if (check(text, kind, min, max) == null) {
            from = new BigDecimal(text.trim());
        } else if (def != null && DECIMAL.matcher(def.trim())
            .matches()) {
                from = new BigDecimal(def.trim());
            } else {
                BigDecimal low = bound(min);
                from = low == null ? BigDecimal.ZERO : low;
            }
        BigDecimal by = stepSize(kind, min, max);
        if (shift) by = by.multiply(BigDecimal.TEN);
        if (ctrl) {
            by = by.movePointLeft(1);
            if (kind == 'I' && by.compareTo(BigDecimal.ONE) < 0) by = BigDecimal.ONE;
        }
        BigDecimal to = from.add(by.multiply(new BigDecimal(direction < 0 ? -1 : 1)));
        BigDecimal low = bound(min);
        BigDecimal high = bound(max);
        if (low != null && to.compareTo(low) < 0) to = low;
        if (high != null && to.compareTo(high) > 0) to = high;
        if (kind == 'I') return to.setScale(0, RoundingMode.HALF_UP)
            .toPlainString();
        return plain(to);
    }
}
