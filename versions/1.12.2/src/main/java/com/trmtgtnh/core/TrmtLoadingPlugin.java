package com.trmtgtnh.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Registers this mod's mixin config with Mixin directly, but only when MixinBooter did not boot Mixin itself - the
 * one case in which the jar's {@code MixinConfigs} manifest attribute is never read.
 *
 * <p>
 * <strong>Why this exists.</strong> JourneyMap 6 for 1.12.2 (6.0.0 to 6.0.10, from 2026-07) ships its own Mixin
 * 0.8.5 behind a tweaker of its own, which boots before MixinBooter. MixinBooter then warns in the debug log that
 * JourneyMap "bundles its own Mixins and this may cause issues", skips its own start - MixinExtras and the reading
 * of every other mod's manifest - and {@code mixins.trmtgtnh.json} is never even selected. Every mixin in this
 * mod went with it, silently: snow and carpet settling on worn ground, the repaint when the server rewrites a
 * chunk, the librarian's books, the OptiFine shader-material seat, and the shade a whole ghost gives the corners
 * beside it. Found on 2026-10-07 by the 0.9.219 pass, and proved by taking JourneyMap out for one run.
 *
 * <p>
 * MixinBooter's early loaders still run in that case, and all one does is {@code Mixins.addConfiguration}. This
 * makes that same call itself, at the same point - a loading plugin's {@code injectData} - so it does not need
 * MixinBooter's interface to load, and a game missing MixinBooter still reaches FML's missing-mod screen rather
 * than failing on a class it cannot find. It starts MixinExtras too, which this mod's wrapped calls need and which
 * MixinBooter left unstarted. When MixinBooter did boot Mixin it reads the manifest as it always has, and this
 * registers nothing: the same config registered twice would apply every mixin twice.
 *
 * <p>
 * <strong>A loading plugin was decided against when this edition was planned</strong> - "mixins yes, a coremod of
 * our own never". The user reversed that for this case on 2026-10-07, on condition that it works with JourneyMap
 * 6.0.10 in the instance. It transforms nothing and loads no game class. {@code Trmt.mixinsLoaded} says in the log
 * if the mixins are still missing once the game is up.
 */
@IFMLLoadingPlugin.Name("TRMT Reimagined")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public final class TrmtLoadingPlugin implements IFMLLoadingPlugin {

    /** The config, as the manifest names it. */
    public static final String CONFIG = "mixins.trmtgtnh.json";

    /** The name MixinBooter's own Mixin service gives itself. */
    static final String MIXINBOOTER = "MixinBooter";

    private static final Logger LOG = LogManager.getLogger("trmtgtnh");

    @Override
    public void injectData(Map<String, Object> data) {
        String service = serviceName();
        List<String> configs = configsFor(service);
        if (configs.isEmpty()) return;
        LOG.warn(
            "Mixin was booted by {} rather than by MixinBooter, which then reads no mod's MixinConfigs - JourneyMap 6"
                + " for 1.12.2 is known to do this. Registering {} directly instead.",
            service,
            CONFIG);
        try {
            com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        } catch (Throwable unstartable) {
            LOG.warn("MixinExtras could not be started under that Mixin: {}", unstartable.toString());
        }
        try {
            for (String config : configs) {
                org.spongepowered.asm.mixin.Mixins.addConfiguration(config);
            }
        } catch (Throwable unregistrable) {
            LOG.warn("{} could not be registered: {}", CONFIG, unregistrable.toString());
        }
    }

    /**
     * What to register, given whose service booted Mixin: nothing when MixinBooter's did, because it reads the
     * manifest and the same config twice would apply every mixin twice, and nothing when there is no Mixin to ask;
     * the config otherwise.
     */
    public static List<String> configsFor(String service) {
        return service == null || MIXINBOOTER.equals(service) ? Collections.<String>emptyList()
            : Collections.singletonList(CONFIG);
    }

    /** The active Mixin service's own name, or null when there is no Mixin to ask. */
    static String serviceName() {
        try {
            return org.spongepowered.asm.service.MixinService.getService()
                .getName();
        } catch (Throwable noMixin) {
            return null;
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
