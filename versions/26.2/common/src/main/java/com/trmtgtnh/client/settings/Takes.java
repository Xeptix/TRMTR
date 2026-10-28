package com.trmtgtnh.client.settings;

import java.util.Locale;

/**
 * When a changed setting takes effect, so the screen can say so honestly (0.9.228; spec M13).
 *
 * <p>
 * A setting marked as needing a restart takes one, and the screen offers to quit the game after saving; one that needs
 * a world restart is greyed while a world is open, as Forge's own screen locks it; one whose comment says it takes
 * effect on the next resource reload is marked " ~" and the screen offers that reload; everything else takes effect at
 * once. The comment is read only when neither flag is set, because a setting that needs a restart needs one whatever
 * else it says.
 */
public enum Takes {

    /** After a restart of the game: marked " *". */
    RESTART,
    /** After the world is left and opened again. */
    WORLD,
    /** On the next resource reload: marked " ~". */
    RELOAD,
    /** At once. */
    NOW;

    /** The words in a comment that say a setting waits for the next resource reload. */
    static final String RELOAD_WORDS = "resource reload";

    public static Takes of(boolean mcRestart, boolean worldRestart, String comment) {
        if (mcRestart) return RESTART;
        if (worldRestart) return WORLD;
        if (comment != null && comment.toLowerCase(Locale.ROOT)
            .contains(RELOAD_WORDS)) return RELOAD;
        return NOW;
    }
}
