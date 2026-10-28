package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the server a newer client joined runs an older build, and what the client tells its player (0.9.228; Xep: "A
 * newer client on an older server - Add it, every edition").
 *
 * <p>
 * A server tells an older client to update, from the protocol in the client's hello (TrmtNetwork.tellToUpdateIfOlder);
 * until 0.9.228 nothing told a newer client anything, because no packet from the server carried its build. From
 * 0.9.228 the rules packet's last tail does ({@code PacketServerRules.SERVER_FORMAT}): the server's protocol and its
 * build's name. A 0.9.227 server's rules end before it, so a client finding no such tail is on 0.9.227 or older.
 *
 * <p>
 * Here rather than in the client's package for one reason: the build's leading number is the shared update check's
 * own reading ({@code UpdateCheck.leading} and {@code compare}, used as they are), which only this package sees. Plain
 * Java, so a test can ask every rule.
 */
public final class OlderServer {

    /** The longest build name the rules carry: the update check's own limit on one. */
    public static final int LONGEST_NAME = UpdateCheck.LONGEST_NAME;

    /** The line every one of these starts with, as the server's older-client line starts. */
    public static final String PREFIX = "[TRMT: Reimagined] ";

    public static final String HEADLINE_FALLBACK = "This server runs an older build.";

    /**
     * The last build whose rules carry no server tail: a server that sent none is this or older. Said on its own line,
     * as a named server's build is, rather than in one line with this client's: measured with tools/chat_width.py,
     * "Yours is 0.9.228; the server's is 0.9.227 or older." fits the chat's 320 pixels at 268, and with a snapshot's
     * name in it (0.9.228-snapshot.12) it is 334 - wrapped wherever the chat likes, for the testers who meet it most.
     */
    public static final String LAST_WITHOUT_TAIL = "0.9.227";

    public static final String YOURS_FALLBACK = "Yours is %s,";

    public static final String THEIRS_FALLBACK = "the server's %s.";

    /** The server's line when it sent no build of its own. */
    public static final String SILENT_FALLBACK = "the server's %s or older.";

    public static final String DIFFER_FALLBACK = "Some of what you see here may differ from it.";

    /** Under the headline, as the snapshot notice's lines are. */
    public static final String UNDER = "  - ";

    private OlderServer() {}

    /**
     * Whether a server is older than this client: one that sent no build of its own is (0.9.227 or before); one that
     * did is when its protocol is below this client's, or its build's leading number is. The same number in another
     * stage - a snapshot of the same version - is not older, so testers are not told; a name the update check cannot
     * read is judged by its protocol alone.
     */
    public static boolean older(boolean tail, int protocol, String build, int ourProtocol, String ourBuild) {
        if (!tail) return true;
        if (protocol < ourProtocol) return true;
        String theirs = UpdateCheck.leading(build);
        String ours = UpdateCheck.leading(ourBuild);
        if (theirs == null || ours == null) return false;
        try {
            return UpdateCheck.compare(theirs, ours) < 0;
        } catch (IllegalArgumentException unreadable) {
            return false;
        }
    }

    /**
     * The lines, as plain text: the headline, then this client's build and the server's on a line each - "0.9.227 or
     * older" where the server sent none, the 1.7.10 edition's {@code silent} line - so the longest names this project
     * makes still fit the chat, then what it means. Each sentence is the language file's where it has one that takes what it is given, and the English here
     * where it has not.
     */
    public static List<String> lines(boolean tail, String ours, String theirs) {
        List<String> out = new ArrayList<String>();
        out.add(PREFIX + said("trmtgtnh.olderServer.headline", HEADLINE_FALLBACK));
        out.add(UNDER + said("trmtgtnh.olderServer.yours", YOURS_FALLBACK, ours));
        if (tail && theirs != null) {
            out.add(UNDER + said("trmtgtnh.olderServer.theirs", THEIRS_FALLBACK, theirs));
        } else {
            out.add(UNDER + said("trmtgtnh.olderServer.silent", SILENT_FALLBACK, LAST_WITHOUT_TAIL));
        }
        out.add(UNDER + said("trmtgtnh.olderServer.differ", DIFFER_FALLBACK));
        return out;
    }

    /** One sentence: the language file's, filled in, or the fallback where the file has none that fits. */
    private static String said(String key, String fallback, String... fill) {
        String text;
        try {
            text = Translate.get(key);
        } catch (RuntimeException noLanguage) {
            text = null;
        }
        String pattern = text == null || text.equals(key) || places(text) != fill.length ? fallback : text;
        return fill.length == 0 ? pattern : pattern.replace("%s", fill[0] == null ? "" : fill[0]);
    }

    private static int places(String text) {
        int count = 0;
        for (int at = text.indexOf("%s"); at >= 0; at = text.indexOf("%s", at + 2)) {
            count++;
        }
        return count;
    }
}
