package com.trmtgtnh.erosion;

/**
 * The server's connection to one player, asked whether the player's client has answered the last place the server
 * put it (0.9.229; the 26.3 Fabric Sodium reload, 2026-11-01). MixinPlayerArrives makes every connection one, from the
 * game's own field: the position the server waits for the client to confirm (and, where the game has it, whether the
 * client has said its world has loaded). {@link ErosionEngine} asks it before an arriving player's first square is
 * taken; see its steppedOnto.
 */
public interface ClientAnswered {

    /** Whether the client has confirmed where it was last put, so the server reads its own movement again. */
    boolean trmtAnswered();
}
