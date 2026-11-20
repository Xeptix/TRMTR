package com.trmtgtnh.compat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.trmtgtnh.ModsPresent;

/**
 * The D60 guard's warn-once line for the original, The Roads More Travelled (docs/goals/0.9.229-w5-spec.md, 2.11,
 * question 13).
 *
 * <p>
 * With the original (mod id {@code trmt}) installed, this mod already stands aside: {@link
 * com.trmtgtnh.original.OriginalBlocks} keeps every {@code trmt:} block out of every family, and
 * {@link com.trmtgtnh.original.OriginalChunk} converts nothing. Both mods still wear the same plain grass, dirt and
 * sand until the original replaces a square with its own block, so the guard says so, plainly, and switches nothing
 * else off: one log line as the first server starts, and one chat line to the first operator, or the host of a world of
 * one's own, to join in each server run. Not stored, so removing the mod and adding it again warns again.
 *
 * <p>
 * Pure, with the logger and the chat handed in, so it is the same file in every edition the original has a jar for
 * (1.20.1, 1.21.1 and 26.1.2), and on every loader of each: on one the original has no jar for, it is never installed
 * and nothing is said. In compat rather than beside the original's files, because the original package is one set of
 * files in all eight editions and this is in three; the same shape as {@code WornPath}.
 */
public final class OriginalInstalled {

    /** The original's mod id, the same on every loader it ships for. */
    public static final String MOD_ID = "trmt";

    /**
     * What is said, in the log and in chat: the overlap, plainly. In-game text, for Xep to read with the rest before it
     * ships.
     */
    public static final String LINE = "The Roads More Travelled is installed too. This mod leaves its worn blocks to "
        + "it; both still wear plain grass, dirt and sand.";

    /** Whether the log has been told, once a game: at the first server's start. */
    private static final AtomicBoolean logged = new AtomicBoolean();

    /** Whether an operator or the host has been told in this server run. */
    private static final AtomicBoolean told = new AtomicBoolean();

    private OriginalInstalled() {}

    /** Whether the original is installed beside this mod. */
    public static boolean installed() {
        return ModsPresent.has(MOD_ID);
    }

    /**
     * A server is starting: a new run, so its first operator is told again; and with the original installed the log is
     * told, the first time a game starts one. Answers whether the log was told now.
     */
    public static boolean serverStarting(Consumer<String> log) {
        told.set(false);
        if (!installed() || !logged.compareAndSet(false, true)) return false;
        log.accept(LINE);
        return true;
    }

    /**
     * Someone has joined: an operator or the host of a world of one's own is told the line, if the original is
     * installed and nobody has been told in this run. {@code say} sends it and answers whether it went out; a line that
     * did not go out is said to the next one to join. Answers whether this joiner was told.
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
