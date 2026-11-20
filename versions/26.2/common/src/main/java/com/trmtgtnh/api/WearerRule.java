package com.trmtgtnh.api;

/**
 * Server thread, and never inside a chunk load. Returns the mob's multiplier of a player's step, given the one TRMTR
 * worked out. Asked after TRMTR has worked out its own and before the player's mob list is applied, so the player's
 * list still wins.
 */
public interface WearerRule {

    float multiplier(Object entity, String entityId, float current);
}
