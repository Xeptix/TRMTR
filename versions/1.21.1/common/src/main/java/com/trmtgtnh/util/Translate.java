package com.trmtgtnh.util;

import java.util.IllegalFormatException;
import java.util.Locale;

import net.minecraft.locale.Language;

import com.trmtgtnh.Trmt;

/**
 * A translated line, on either side, which this version makes harder than it was.
 *
 * <p>
 * Both older editions translate on the server as freely as on the client: 1.12.2's
 * {@code net.minecraft.util.text.translation.I18n} is deprecated but present on both sides, and
 * 1.7.10's is the same. At this version the class of that name is under {@code client} and a
 * dedicated server does not have it - so a line this mod builds on the server and sends as finished
 * text, which is what every mending message is, would crash the server rather than fail to
 * translate.
 *
 * <p>
 * <strong>The honest fix would be to send the key and let the client translate it</strong>, as a
 * translatable component. That is what this version wants, and it is a change to the shape of every
 * message in the mod rather than to the way one of them is translated - the lines are assembled on
 * the server out of several translations, joined with translated joining words, and a component
 * tree carrying all of that is a different design. It is worth doing and it is not worth doing
 * halfway, so what is here is the narrow fix: the language table the server already has.
 *
 * <p>
 * That table is the server's own, which means a message is in the server's language rather than in
 * the reader's. Both older editions have exactly that limitation for exactly the same reason, so
 * this is not a step backwards from either of them - and the javadoc on the 1.12.2 version of
 * {@code familyList} already says the joining words are translated on the server.
 */
public final class Translate {

    private static volatile boolean missingSaid;

    private Translate() {}

    /** One key, with the arguments it takes. */
    public static String get(String key, Object... args) {
        if (key == null) return "";
        String pattern = Language.getInstance()
            .getOrDefault(key);
        if (args == null || args.length == 0) return pattern;
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (IllegalFormatException wrongArguments) {
            // A key whose translation does not take what it was given. Said once with the key, which
            // is the only thing that makes it findable, and the pattern is handed back untouched
            // rather than the line being lost.
            if (!missingSaid) {
                missingSaid = true;
                Trmt.LOG.warn(
                    "The translation for {} does not take the values this mod passes it, so it is "
                        + "being shown unfilled. This is said once.",
                    key,
                    wrongArguments);
            }
            return pattern;
        }
    }
}
