package com.trmtgtnh.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Where a player is sent when something goes wrong, and where they hear about updates: the mod's GitHub issues
 * and its Discord, written once and held identical in every edition by the drift check.
 *
 * <p>
 * Asked for on 2026-10-08, to begin 0.9.221: crash reports and the mod's own error lines carry both links, and
 * the update notice carries the Discord's, with a sentence asking the player to join to hear the moment an
 * update drops. A crash report is read by whoever has it in front of them - often a pack's player, who did
 * not choose this mod by name - so the report says where to take it, and that it is this mod's only when the
 * crash names it.
 *
 * <p>
 * <strong>Both addresses are permanent.</strong> Every jar from 0.9.221 on shows them for as long as it is
 * played. The invite was made never to expire (it lands on the server's #pick-your-games), and is the one the
 * store pages carry.
 *
 * <p>
 * Portable: no Minecraft and no logging framework. Each edition logs and draws these words itself.
 */
public final class Support {

    /** The public repository's issues, where a fault is reported. */
    public static final String ISSUES = "https://github.com/Xeptix/TRMTR/issues";

    /** The Discord invite: help, and every update announced the moment it drops. */
    public static final String DISCORD = "https://discord.gg/RE85HRwYZZ";

    /**
     * Where in the Discord a fault goes (Xep, 2026-10-09: "That + github issues are the two proper paths. Can also be
     * posted on an individual release post of TRMTR in the mc releases"). The invite lands on #pick-your-games, so the
     * words name the rooms.
     */
    public static final String DISCORD_ROOMS = "#mc-bug-reports or #mc-help";

    /** The invite as a reader sees it, without the scheme. */
    public static final String DISCORD_SHOWN = "discord.gg/RE85HRwYZZ";

    /**
     * The invite the game's own chat links to - the update notice and its help line - made for them alone, so the
     * server can count who arrives from inside the game (Xep, 2026-10-09: "specifically for the in-game link (for
     * tracking purposes)"). Permanent too, landing on #welcome. Crash reports and log lines keep {@link #DISCORD}.
     */
    public static final String DISCORD_IN_GAME = "https://discord.gg/vR6vwEKR6W";

    /** The in-game invite as the chat shows it. */
    public static final String DISCORD_IN_GAME_SHOWN = "discord.gg/vR6vwEKR6W";

    /** The name a crash report's line is filed under. */
    public static final String CRASH_LABEL = "TRMT: Reimagined";

    /**
     * The update notice's Discord line, when a language file has none: the invite goes on the line under it, so no
     * chat ever breaks it in two.
     */
    public static final String JOIN_FALLBACK = "Join the Discord to hear the moment an update drops:";

    private static final AtomicBoolean ASKED = new AtomicBoolean();

    private Support() {}

    /**
     * The line logged after this mod's first error in a session - once, so a fault that repeats every tick does
     * not double the log.
     */
    public static String afterError() {
        return "TRMT: Reimagined logged an error just above. If it keeps happening, please report it at " + ISSUES
            + " with this log attached, or in the Discord ("
            + DISCORD
            + ") in "
            + DISCORD_ROOMS
            + ".";
    }

    /** Whether this is the first time this session that {@link #afterError()} is due. */
    public static boolean firstError() {
        return ASKED.compareAndSet(false, true);
    }

    /** The line every crash report carries under {@link #CRASH_LABEL}, with the running build's name. */
    public static String crashDetail(String version) {
        return (version == null || version.isEmpty() ? "" : version + ". ")
            + "If this crash names TRMT or com.trmtgtnh, please report it at "
            + ISSUES
            + " with this report attached, or in the Discord ("
            + DISCORD
            + ") in "
            + DISCORD_ROOMS
            + ", or as a reply on TRMT: Reimagined's release post in #mc-mod-releases.";
    }

    /**
     * A translated sentence with its places filled in order, or - when it does not hold exactly as many
     * {@code %s} as there are words to put in, as a language file a version behind may not - the fallback filled
     * instead. Never {@link String#format}: a stray {@code %d} in a translation must not throw on a server.
     */
    public static String fill(String pattern, String fallback, String... values) {
        String use = places(pattern) == values.length ? pattern : fallback;
        StringBuilder out = new StringBuilder();
        int from = 0;
        for (String value : values) {
            int at = use.indexOf("%s", from);
            out.append(use, from, at)
                .append(value);
            from = at + 2;
        }
        return out.append(use.substring(from))
            .toString();
    }

    /**
     * A translated sentence cut at its places, so each can be drawn in its own style - {@code places + 1} pieces, or
     * the fallback's when the translation does not hold exactly that many {@code %s}.
     */
    public static String[] cut(String pattern, String fallback, int places) {
        String use = places(pattern) == places ? pattern : fallback;
        String[] out = new String[places + 1];
        int from = 0;
        for (int at = 0; at < places; at++) {
            int found = use.indexOf("%s", from);
            out[at] = use.substring(from, found);
            from = found + 2;
        }
        out[places] = use.substring(from);
        return out;
    }

    /** How many {@code %s} a sentence holds; none for null. */
    static int places(String pattern) {
        int n = 0;
        for (int at = pattern == null ? -1 : pattern.indexOf("%s"); at >= 0; at = pattern.indexOf("%s", at + 2)) n++;
        return n;
    }

    /**
     * The update notice's second line cut at its one place, so the invite can be put back as a link - two
     * pieces, or null when a translation does not have exactly one {@code %s}, and the caller uses
     * {@link #JOIN_FALLBACK}.
     */
    public static String[] around(String pattern) {
        if (pattern == null) return null;
        int at = pattern.indexOf("%s");
        if (at < 0 || pattern.indexOf("%s", at + 2) >= 0) return null;
        return new String[] { pattern.substring(0, at), pattern.substring(at + 2) };
    }
}
