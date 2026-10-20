package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.List;

/**
 * What /trmt version says (0.9.227; Xep: "/trmt version", open to every player): this jar's build and edition, the
 * newest build for it as the server's last update check found it, and each required mod's version against what the
 * version file asks of it - so a player, or Xep in a support thread, can check without waiting for a join.
 *
 * <p>
 * Built from what the server already holds - {@link UpdateCheck#last()} and {@link UpdateCheck#lastBody()} - so asking
 * makes no request of its own. Every word is in this class: the file supplies only names in the strict shapes
 * {@link UpdateCheck} and {@link DependencyCheck} trust, as for the notice itself. Portable, and the same in every
 * edition (CoreStaysPortableTest).
 */
public final class VersionReport {

    private VersionReport() {}

    /**
     * The lines, in order.
     *
     * @param name      the mod's name
     * @param version   this jar's own version
     * @param edition   this jar's line in the file: {@code 1.16.5-fabric}
     * @param answer    the server's last update check, or null if none has finished
     * @param body      the version file as that check read it, or null
     * @param installed each required mod as {@code {id, name, installed version}}, a version null when not installed
     */
    public static List<String> lines(String name, String version, String edition, UpdateCheck.Answer answer,
        String body, String[][] installed) {
        List<String> out = new ArrayList<String>();
        out.add(name + " " + version + " (" + edition + ")");
        out.add(newest(version, answer));
        for (String[] mod : installed == null ? new String[0][] : installed) {
            if (mod == null || mod.length < 3) continue;
            out.add(required(mod[1], mod[2], DependencyCheck.floor(body, edition, mod[0]), body != null));
        }
        return out;
    }

    /** The line about the newest build for this jar. */
    static String newest(String version, UpdateCheck.Answer answer) {
        if (answer == null) {
            return "The newest build has not been asked for yet: the server reads the version file as it starts, unless"
                + " general.updateNotice is off.";
        }
        if (answer.newer != null && answer.bad) {
            return version + " has known issues, and " + answer.newer + " is the build to use.";
        }
        if (answer.newer != null) {
            return answer.newer + " is out for this jar"
                + (answer.critical != null ? ", and " + answer.critical + " was critical for it" : "")
                + ".";
        }
        return "Update check: " + answer.said + ".";
    }

    /** The line about one required mod. */
    static String required(String mod, String installed, String[] floor, boolean read) {
        if (installed == null) return mod + " is not installed, and this mod needs it.";
        if (floor == null) {
            return mod + " "
                + installed
                + (read ? " - the version file asks nothing of it." : " - the version file has not been read yet.");
        }
        String minimum = floor[0];
        String recommended = floor[1];
        boolean more = DependencyCheck.compare(recommended, minimum) > 0;
        if (DependencyCheck.compare(installed, minimum) < 0) {
            return mod + " "
                + installed
                + " is older than the "
                + minimum
                + " this mod needs"
                + (more ? "; " + recommended + " is recommended" : "")
                + ".";
        }
        return mod + " "
            + installed
            + " - needs "
            + minimum
            + " or newer"
            + (more && DependencyCheck.compare(installed, recommended) < 0 ? "; " + recommended + " is recommended"
                : "")
            + ".";
    }
}
