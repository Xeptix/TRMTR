package com.trmtgtnh.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * A collection, said in a log line, without the line being able to grow without limit.
 *
 * <p>
 * Log4j formats a collection parameter by walking it, and nothing bounds how long the result is. On
 * 2026-10-06 that stopped the 1.16.5 edition starting at all: the texture stitch passed its set of
 * stand-in surfaces straight to {@code LOG.info}, and on a stitch composing forty-four thousand
 * pictures from a hundred and fifty-six surfaces the formatter threw {@code OutOfMemoryError} with a
 * StringBuilder past two gigabytes - inside {@code TextureAtlas.reload}, which took the whole resource
 * reload down with it and left the renderer tessellating blocks against a null model. The game did not
 * reach its title screen.
 *
 * <p>
 * The other two editions carry the identical lines and survive them only because their packs are an
 * order of magnitude smaller. That is not a property anybody chose and it does not hold when somebody
 * installs a large texture pack, so all three were changed together rather than only the one that
 * fell over.
 *
 * <p>
 * What is printed is the count first - which is the part anybody actually reads - and then as many
 * names as {@link #NAMED} allows, each of them clipped, with the remainder counted rather than
 * listed. Every element is copied as it is walked, so a collection something else is still adding to
 * cannot keep this line going round.
 *
 * <p>
 * Portable: no Minecraft, no logging framework, nothing but the collection it is handed.
 */
public final class LogSample {

    /** How many entries are named before the rest are counted. */
    public static final int NAMED = 40;

    /**
     * How long one entry may be.
     *
     * <p>
     * Because the count is not the only way a line gets long: forty entries are harmless, and forty
     * entries whose {@code toString} is a serialised blockstate apiece are not. A name long enough to
     * be clipped here was never going to be read anyway.
     */
    public static final int LONGEST = 120;

    /**
     * How many entries are walked at all, counted ones included.
     *
     * <p>
     * The naming cap alone is not enough, and the crash this class exists for is the proof. The set
     * that took the game down held <strong>ten short strings</strong> - the line that replaced it says
     * so - which cannot make a two-gigabyte string by being read. What it can do is be written while
     * it is read: the stitch adds to it as pictures are composed, and a walk over a collection
     * somebody else is mutating is under no obligation to end. Counting to the end of such a walk
     * swaps a crash for a hang, which is worse, because a hang has no stack trace.
     *
     * <p>
     * So the walk itself stops. Past this the count is reported as "at least", which is honest and is
     * all a log line ever needed.
     */
    public static final int WALKED = 10000;

    private LogSample() {}

    /** A bounded description of a collection, safe to hand to a log line. */
    public static String of(Collection<?> items) {
        if (items == null) return "none";
        StringBuilder out = new StringBuilder();
        int counted = 0;
        int named = 0;
        // Walked rather than measured first: size() and iteration can disagree on a collection being
        // added to, and the count that matters is the one this line actually printed from.
        for (Iterator<?> each = items.iterator(); each.hasNext() && counted < WALKED;) {
            Object item = each.next();
            counted++;
            if (named >= NAMED) continue;
            if (named > 0) out.append(", ");
            out.append(clip(item));
            named++;
        }
        return finish(out, counted, named);
    }

    /** The same for a map, whose entries are printed as {@code key=value}. */
    public static String of(Map<?, ?> items) {
        if (items == null) return "none";
        StringBuilder out = new StringBuilder();
        int counted = 0;
        int named = 0;
        for (Iterator<? extends Map.Entry<?, ?>> each = items.entrySet()
            .iterator(); each.hasNext() && counted < WALKED;) {
            Map.Entry<?, ?> entry = each.next();
            counted++;
            if (named >= NAMED) continue;
            if (named > 0) out.append(", ");
            out.append(clip(entry.getKey()))
                .append('=')
                .append(clip(entry.getValue()));
            named++;
        }
        return finish(out, counted, named);
    }

    private static String finish(StringBuilder out, int counted, int named) {
        if (counted == 0) return "none";
        StringBuilder said = new StringBuilder();
        // "at least", once the walk was cut short, because then the count is this class's limit
        // rather than the collection's size and saying otherwise would be a plain untruth.
        if (counted >= WALKED) said.append("at least ");
        said.append(counted)
            .append(counted == 1 ? " entry: " : " entries: ")
            .append(out);
        if (counted > named) {
            said.append(" and ")
                .append(counted - named)
                .append(" more");
        }
        return said.toString();
    }

    private static String clip(Object item) {
        String text = String.valueOf(item);
        return text.length() <= LONGEST ? text : text.substring(0, LONGEST) + "...";
    }
}
