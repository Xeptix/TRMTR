package com.trmtgtnh.api;

/**
 * Handed to a plugin's register, and only valid during that call. What is registered lasts the session.
 */
@ImplementedByTrmtr
public interface Registrar {

    /** Hears wear before it lands and every change to a record after. */
    void wearListener(WearListener listener);

    /** Decides where ground may wear. */
    void groundRule(GroundRule rule);

    /** Weighs a mob's step by something only the mod knows. */
    void wearerRule(WearerRule rule);

    /**
     * Draws worn ground for a block. For blocks of the plugin's own namespace only: one for another namespace's block
     * is refused with one line.
     */
    void renderer(String blockSelector, WearRenderer renderer);

    /**
     * A data file from code: name is its path under trmtr/, such as "families/loam.json". It counts as the plugin's
     * own mod's jar in the load order.
     */
    void data(String name, String json);
}
