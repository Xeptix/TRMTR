package com.trmtgtnh.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Whether a mod this one requires is older than this mod wants (0.9.221; Xep, 2026-10-09: "track in the
 * latest.properties the minimum and recommended versions of each mandatory dependency, and display a similar message
 * to the user like we do for updates to warn them if the dependency is on a version lower than the minimum"). The
 * loaders refuse to start below a hard floor; this is the softer one above it, below which things work but may not
 * work well - and it can move after a jar has shipped, because it is read from the same file the update notice
 * reads.
 *
 * <p>
 * <strong>The file supplies version numbers only.</strong> Which mods a jar requires, and their names, are the jar's
 * own (each edition's {@code UpdateNotice.REQUIRED}); a line for a mod the jar does not list is never read, and a
 * value that is not a version number in {@link #SHAPE} is ignored. So, as for the update notice, nothing in the file
 * can put words into anybody's chat.
 *
 * <p>
 * Lines are {@code requires.<edition>.<mod id>.minimum} and {@code .recommended}:
 * {@code requires.1.16.5-fabric.fabric.minimum=0.42.0}. Portable: no Minecraft, no logging framework.
 */
public final class DependencyCheck {

    /** What a dependency's lines start with, before the edition. */
    static final String REQUIRES = "requires.";

    /** A version number as the file may give it: a digit first, then up to 31 of digits, letters and . + _ -. */
    static final Pattern SHAPE = Pattern.compile("[0-9][0-9A-Za-z.+_-]{0,31}");

    private DependencyCheck() {}

    /** One required mod found older than the file's minimum for it. */
    public static final class Below {

        /** The mod's id, as its loader knows it. */
        public final String id;

        /** Its name, as this jar gives it. */
        public final String name;

        /** The version installed. */
        public final String installed;

        /** The least this mod wants. */
        public final String minimum;

        /** What this mod recommends - the minimum itself when the file gives no other. */
        public final String recommended;

        Below(String id, String name, String installed, String minimum, String recommended) {
            this.id = id;
            this.name = name;
            this.installed = installed;
            this.minimum = minimum;
            this.recommended = recommended;
        }

        /** Whether the recommended version is a different one from the minimum, so both are worth naming. */
        public boolean recommendsMore() {
            return compare(recommended, minimum) > 0;
        }
    }

    /**
     * Every required mod older than the file's minimum for this edition.
     *
     * @param body    the version file, or null when none was read
     * @param edition this jar's line in the file: {@code 1.16.5-fabric}
     * @param known   each required mod as {@code {id, name, installed version}}; a mod not installed, or whose version
     *                is unknown, is never reported
     */
    public static List<Below> below(String body, String edition, String[][] known) {
        List<Below> out = new ArrayList<Below>();
        if (body == null || known == null) return out;
        Properties read = new Properties();
        try {
            read.load(new StringReader(body));
        } catch (IOException e) {
            return out;
        } catch (IllegalArgumentException e) {
            return out;
        }
        for (String[] mod : known) {
            if (mod == null || mod.length < 3 || mod[2] == null) continue;
            String base = REQUIRES + edition + "." + mod[0];
            String minimum = shaped(read.getProperty(base + ".minimum"));
            if (minimum == null) continue;
            String recommended = shaped(read.getProperty(base + ".recommended"));
            if (recommended == null || compare(recommended, minimum) < 0) recommended = minimum;
            if (compare(mod[2], minimum) < 0) out.add(new Below(mod[0], mod[1], mod[2], minimum, recommended));
        }
        return out;
    }

    /** A version from the file, trimmed, if it has the one shape allowed; otherwise null. */
    static String shaped(String said) {
        if (said == null) return null;
        String text = said.trim();
        return SHAPE.matcher(text)
            .matches() ? text : null;
    }

    /**
     * Two versions in order, by their numbers: each run of digits compared as a number, left to right, a missing one
     * counting as nought, so {@code 0.42.0} is newer than {@code 0.9.3}. Anything after a {@code +} is build
     * metadata and ignored ({@code 0.42.0+1.16} is {@code 0.42.0}); letters only separate numbers.
     */
    public static int compare(String a, String b) {
        long[] x = numbers(a);
        long[] y = numbers(b);
        for (int at = 0; at < Math.max(x.length, y.length); at++) {
            long p = at < x.length ? x[at] : 0;
            long q = at < y.length ? y[at] : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static long[] numbers(String version) {
        String text = version == null ? "" : version;
        int plus = text.indexOf('+');
        if (plus >= 0) text = text.substring(0, plus);
        List<Long> found = new ArrayList<Long>();
        long current = -1;
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            if (c >= '0' && c <= '9') {
                long next = (current < 0 ? 0 : current) * 10 + (c - '0');
                current = next > 999999999L ? 999999999L : next;
            } else if (current >= 0) {
                found.add(Long.valueOf(current));
                current = -1;
            }
        }
        if (current >= 0) found.add(Long.valueOf(current));
        long[] out = new long[found.size()];
        for (int at = 0; at < out.length; at++) out[at] = found.get(at)
            .longValue();
        return out;
    }
}
