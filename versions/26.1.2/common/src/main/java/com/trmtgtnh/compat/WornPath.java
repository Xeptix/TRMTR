package com.trmtgtnh.compat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.trmtgtnh.ModsPresent;

/**
 * The D60 guard for Worn Path, the other wear mod D58 names (docs/goals/0.9.229-w5-spec.md, 2.11, questions 12 and 13).
 *
 * <p>
 * Worn Path (mod id {@code worn_path}) owns no blocks: read from its own jars (S0, T16), it turns grass and dirt into
 * vanilla dirt path, then packed mud and mud bricks, where players walk. So there is nothing for
 * {@link com.trmtgtnh.original.OriginalBlocks} to stand aside on, and both mods wear the same grass. The guard is to say so, plainly, and to switch nothing off: D60
 * says everything else stays as normal, and the owner decides. One log line as the first server starts, and one chat
 * line to the first operator, or the host of a world of one's own, to join in each server run. Not stored, so removing
 * the mod and adding it again warns again.
 *
 * <p>
 * Pure, with the logger and the chat handed in, so it is the same file in every edition Worn Path exists for (1.21.1,
 * 26.1.2 and 26.2), and on every loader of each: on one Worn Path has no jar for, it is never installed and nothing is
 * said. In compat rather than beside the original's files, because the original package is one set of files in all
 * eight editions and this is in three.
 */
public final class WornPath {

    /** Worn Path's mod id, the same in every one of its jars. */
    public static final String MOD_ID = "worn_path";

    /**
     * What is said, in the log and in chat: the overlap, and the setting that switches off this mod's grass family.
     * In-game text, for Xep to read with the rest before it ships.
     */
    public static final String LINE = "Worn Path is installed too. Both mods wear grass where players walk: Worn Path "
        + "turns it into dirt path, packed mud and mud bricks, and this mod wears it down in stages. Nothing has been "
        + "switched off; to leave grass to Worn Path, set families.grass.enabled to false in this mod's settings.";

    /** Whether the log has been told, once a game: at the first server's start. */
    private static final AtomicBoolean logged = new AtomicBoolean();

    /** Whether an operator or the host has been told in this server run. */
    private static final AtomicBoolean told = new AtomicBoolean();

    private WornPath() {}

    /** Whether Worn Path is installed beside this mod. */
    public static boolean installed() {
        return ModsPresent.has(MOD_ID);
    }

    /**
     * A server is starting: a new run, so its first operator is told again; and with Worn Path installed the log is
     * told, the first time a game starts one. Answers whether the log was told now.
     */
    public static boolean serverStarting(Consumer<String> log) {
        told.set(false);
        if (!installed() || !logged.compareAndSet(false, true)) return false;
        log.accept(LINE);
        return true;
    }

    /**
     * Someone has joined: an operator or the host of a world of one's own is told the line, if Worn Path is installed
     * and nobody has been told in this run. {@code say} sends it and answers whether it went out; a line that did not go
     * out is said to the next one to join. Answers whether this joiner was told.
     */
    public static boolean tellAtJoin(boolean operatorOrHost, Predicate<String> say) {
        if (!operatorOrHost || told.get() || !installed()) return false;
        if (!say.test(LINE)) return false;
        told.set(true);
        return true;
    }

    /** Forgets every telling, as a new game would. For tests. */
    static void forget() {
        logged.set(false);
        told.set(false);
    }
}
