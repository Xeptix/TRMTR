package com.trmtgtnh.api;

/**
 * What a bit of wear is about to land on, and what caused it.
 */
@ImplementedByTrmtr
public interface WearContext {

    /** The edition's world. */
    Object level();

    int x();

    int y();

    int z();

    /** The block there, in the edition's own spelling. */
    String block();

    /** Null when nothing walked there: a blast, a command or a call through Actions. */
    Object entity();

    /** What caused the wear. */
    Cause cause();

    /** What caused a bit of wear. */
    enum Cause {
        /** A player or mob walking. */
        STEP,
        /** A ridden mob or vehicle. */
        MOUNT,
        /** An explosion. */
        BLAST,
        /** Something landing hard from a height. */
        IMPACT,
        /** The Golem of Ways. */
        GOLEM,
        /** A TRMTR command. */
        COMMAND,
        /** A call through {@link Actions}. */
        API
    }
}
