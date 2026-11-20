package com.trmtgtnh.api;

/**
 * A mod's plugin, named in its plugin.json.
 *
 * <p>
 * Loaded once, after TRMTR has read the data files and before it first works out which block is in which family, by
 * TRMTR's own class loader. Without TRMTR installed it is never loaded, so a mod can support TRMTR without requiring
 * it.
 */
public interface TrmtrPlugin {

    /** Registers the plugin's hooks and data. The registrar is only valid during this call. */
    void register(Registrar registrar);
}
