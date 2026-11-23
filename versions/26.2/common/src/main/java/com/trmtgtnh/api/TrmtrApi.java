package com.trmtgtnh.api;

/**
 * Where a mod reaches TRMTR.
 *
 * <p>
 * This package is trmtr-api, the same in every edition from 1.7.10 to 26.3. It names no class of the game: where the
 * game's own object is passed, the type is {@code Object}, and the API's documents name the class each edition passes.
 * A mod depends on it at compile time only and never shades it: TRMTR carries these classes in its own jar and is
 * their only provider.
 */
public final class TrmtrApi {

    /** This API's version, which is not the mod's. */
    public static final String VERSION = "1.0.1";

    /** TRMTR itself, once TRMTR has set it. */
    private static volatile Trmtr trmtr;

    private TrmtrApi() {}

    /**
     * TRMTR itself. Set before any plugin is called.
     *
     * @throws IllegalStateException when asked before TRMTR has started, which a plugin never is
     */
    public static Trmtr get() {
        Trmtr held = trmtr;
        if (held == null) {
            throw new IllegalStateException("trmtr-api " + VERSION + " was asked for TRMTR before TRMTR had started");
        }
        return held;
    }

    /**
     * Sets what {@link #get()} gives, once. Not part of the API: TRMTR reaches it from its own jar, before it loads any
     * plugin, and a second, different value is refused so nothing can take TRMTR's place after it has started.
     */
    static synchronized void install(Trmtr itself) {
        if (itself == null) throw new IllegalArgumentException("TRMTR cannot be installed as null");
        if (trmtr != null && trmtr != itself) throw new IllegalStateException("TRMTR is already installed");
        trmtr = itself;
    }
}
