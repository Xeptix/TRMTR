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
 * <strong>A pack with no MixinBooter at all</strong> (G17 R5, 2026-11-11; Xep: "Make it cope (Recommended)").
 * RLCraft 2.9.3 boots Mixin through its mods' own tweakers, from the copies they carry, and the first of them on the
 * class path is the game's - Mixin 0.8.3 from {@code framevoidpatch-1.0.0}, or 0.8 from MixinCompat. MixinBooter
 * 11.13 cannot start there: it puts itself at the end of the class path and its constructor calls
 * {@code ASM.isAtLeastVersion(II)Z}, which Mixin gained in 0.8.4, so the game died before its first screen. So this
 * jar no longer requires MixinBooter of Forge; it registers its config under whatever Mixin 0.8 or newer another mod
 * booted, as for JourneyMap, and asks for MixinBooter only where there is no Mixin at all - by handing FML
 * {@link MixinBooterWanted}, whose one requirement is MixinBooter, so the player sees Forge's own missing-mod screen
 * as before. MixinExtras comes only with MixinBooter, and {@code com.trmtgtnh.mixin.OculusGate} applies a redirect
 * form of each hook it would wrap where it is absent.
 *
 * <p>
 * <strong>A loading plugin was decided against when this edition was planned</strong> - "mixins yes, a coremod of
 * our own never". The user reversed that for this case on 2026-10-07, on condition that it works with JourneyMap
 * 6.0.10 in the instance. It transforms nothing and loads no game class. {@code Trmt.mixinsLoaded} says in the log
 * if the mixins are still missing once the game is up.
 */
@IFMLLoadingPlugin.Name("TRMT: Reimagined")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public final class TrmtLoadingPlugin implements IFMLLoadingPlugin {

    /** The config, as the manifest names it. */
    public static final String CONFIG = "mixins.trmtgtnh.json";

    /** The name MixinBooter's own Mixin service gives itself. */
    static final String MIXINBOOTER = "MixinBooter";

    /**
     * The oldest Mixin this mod's mixins apply under, and the config's {@code minVersion}: the 0.8 line, whose
     * config-plugin interface takes ASM's own tree classes. Mixin 0.7 took its relocated copies, so the config's plugin
     * cannot load under it.
     */
    public static final String OLDEST_MIXIN = "0.8";

    /** MixinExtras' start, which comes in MixinBooter's jar and in no other this mod expects. */
    public static final String MIXINEXTRAS = "com.llamalad7.mixinextras.MixinExtrasBootstrap";

    /** The container that asks Forge for MixinBooter, named rather than loaded: see {@link #getModContainerClass}. */
    static final String WANTED = "com.trmtgtnh.core.MixinBooterWanted";

    /** What this game's Mixin asks of this jar. */
    public enum Route {
        /** MixinBooter booted Mixin and reads the manifest itself: nothing to do. */
        MIXINBOOTER,
        /** Another mod booted Mixin 0.8 or newer - JourneyMap 6, or RLCraft's tweakers: the config is registered here. */
        REGISTER,
        /** Another mod booted a Mixin older than 0.8, which none of this mod's mixins apply under: the game is stopped. */
        TOO_OLD,
        /** No Mixin at all: Forge is asked for MixinBooter, through {@link MixinBooterWanted}. */
        NO_MIXIN
    }

    private static final Logger LOG = LogManager.getLogger("TRMT: Reimagined");

    /** The container that stops a game whose Mixin is too old, named rather than loaded. */
    static final String REFUSED = "com.trmtgtnh.core.MixinTooOld";

    /** What {@link #injectData} found, or null outside a game, where it never runs. */
    private static volatile Route found;

    /** The version of the Mixin that booted and the jar it came from, as {@link #injectData} read them. */
    private static volatile String foundVersion, foundSource;

    /**
     * Whether this plugin started MixinExtras, where it is the one to start it (the route that registers the config):
     * null where it did not try (G17, Codex's review of R5, 2026-11-11). See {@link #mixinExtrasRuns(boolean)}.
     */
    private static volatile Boolean extrasStarted;

    @Override
    public void injectData(Map<String, Object> data) {
        String service = serviceName();
        String version = mixinVersion();
        Route route = routeFor(service, version);
        found = route;
        foundVersion = version;
        foundSource = mixinSource();
        if (route == Route.NO_MIXIN) {
            LOG.warn("No Mixin is in this game, and this mod's mixins need one: Forge is asked for MixinBooter.");
            return;
        }
        if (route == Route.TOO_OLD) {
            for (String line : tooOldLines()) LOG.warn(line);
            return;
        }
        List<String> configs = configsFor(route);
        if (configs.isEmpty()) return;
        LOG.warn(
            "Mixin {} was booted by {} rather than by MixinBooter, which then reads no mod's MixinConfigs - JourneyMap 6"
                + " for 1.12.2 is known to do this, and so do packs such as RLCraft that boot their own. Registering {}"
                + " directly instead.",
            version,
            service,
            CONFIG);
        extrasStarted = Boolean.FALSE;
        if (!present(MIXINEXTRAS)) {
            LOG.info(
                "MixinExtras is not in this game - it comes with MixinBooter - so the hooks it would wrap apply in their"
                    + " redirect forms.");
        } else {
            try {
                com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
                extrasStarted = Boolean.TRUE;
            } catch (Throwable unstartable) {
                LOG.warn(
                    "MixinExtras could not be started under that Mixin, so the hooks it would wrap apply in their redirect"
                        + " forms: {}",
                    unstartable.toString());
            }
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
     * The way through, given whose service booted Mixin and which version it is: MixinBooter's reads the manifest;
     * any other of 0.8 or newer has the config registered here, an older one nothing; and with no Mixin to ask,
     * Forge is asked for MixinBooter. A version that cannot be read is taken as new enough, as it was before versions
     * were asked.
     */
    public static Route routeFor(String service, String version) {
        if (service == null) return Route.NO_MIXIN;
        if (MIXINBOOTER.equals(service)) return Route.MIXINBOOTER;
        if (version != null && compare(version, OLDEST_MIXIN) < 0) return Route.TOO_OLD;
        return Route.REGISTER;
    }

    /**
     * What to register on a route: the config where another mod booted a Mixin it applies under, and nothing
     * otherwise - under MixinBooter's, because it reads the manifest and the same config twice would apply every
     * mixin twice.
     */
    public static List<String> configsFor(Route route) {
        return route == Route.REGISTER ? Collections.singletonList(CONFIG) : Collections.<String>emptyList();
    }

    /**
     * The container FML is handed on a route: the one that asks for MixinBooter where there is no Mixin, the one that
     * stops the game where its Mixin is older than this mod's mixins apply under, or none.
     */
    public static String containerFor(Route route) {
        if (route == Route.NO_MIXIN) return WANTED;
        if (route == Route.TOO_OLD) return REFUSED;
        return null;
    }

    /**
     * Whether MixinExtras runs in this game, for the mixin config's plugin to choose the wrapped hooks or their redirect
     * forms by (G17, Codex's review of R5, 2026-11-11): where this plugin was the one to start it, whether the start
     * succeeded, so a MixinExtras jar that is there and will not start under the game's Mixin gets the redirect forms
     * rather than wrapped hooks that then fail to apply; elsewhere - MixinBooter starts its own, and outside a game
     * nothing started anything - whether its jar is there.
     */
    public static boolean mixinExtrasRuns(boolean present) {
        return mixinExtrasRuns(found, extrasStarted, present);
    }

    static boolean mixinExtrasRuns(Route route, Boolean started, boolean present) {
        if (route == Route.REGISTER) return Boolean.TRUE.equals(started);
        return present;
    }

    /** What a game whose Mixin is too old is told, from what {@link #injectData} read. */
    public static List<String> tooOldLines() {
        return tooOldLines(foundVersion, foundSource);
    }

    /**
     * What a game whose Mixin is too old is told (G17, Codex's review of R5, 2026-11-11): which Mixin, from which jar
     * where that can be read, and what this mod needs - rather than a game that starts with every mixin of this mod
     * missing.
     */
    static List<String> tooOldLines(String version, String source) {
        String mixin = "Mixin " + (version == null ? "of an older line" : version);
        return java.util.Arrays.asList(
            "TRMT: Reimagined cannot start under this game's Mixin.",
            mixin + (source == null ? " was started by another mod." : " was started from " + source + "."),
            "This mod's mixins need Mixin " + OLDEST_MIXIN + " or newer, as MixinBooter brings.",
            "Update or remove the mod that brings " + mixin + ", then start the game again.");
    }

    /**
     * Whether this game runs this mod's mixins under another mod's Mixin, with no need of MixinBooter: so a report of
     * what this mod requires does not send a player in such a pack for the mod that cannot start there.
     */
    public static boolean registeredWithoutMixinBooter() {
        return found == Route.REGISTER;
    }

    /** Why the config was not registered, for the warning that says so once the game is up. */
    public static String whyNotRegistered() {
        Route route = found;
        if (route == Route.TOO_OLD) return "The Mixin another mod booted is older than " + OLDEST_MIXIN + ".";
        if (route == Route.NO_MIXIN) return "There is no Mixin in this game; MixinBooter brings one.";
        return "Another mod booted Mixin before MixinBooter - see the compatibility notes.";
    }

    /** Two Mixin versions by their numbers, a missing part counting as nought: 0.8 is older than 0.8.3. */
    static int compare(String a, String b) {
        String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = number(x, i), q = number(y, i);
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static int number(String[] parts, int at) {
        if (at >= parts.length || parts[at].isEmpty()) return 0;
        try {
            return Integer.parseInt(parts[at]);
        } catch (NumberFormatException huge) {
            return Integer.MAX_VALUE;
        }
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

    /**
     * The version of the Mixin that booted, read from its own class: a constant, so it is read by reflection, never
     * named, or the compiler would put the version this jar was built against in its place.
     */
    static String mixinVersion() {
        try {
            Object version = Class.forName("org.spongepowered.asm.launch.MixinBootstrap", false, loader())
                .getField("VERSION")
                .get(null);
            return version == null ? null : version.toString();
        } catch (Throwable unread) {
            return null;
        }
    }

    /** The name of the jar the booted Mixin's own class came from, or null where that cannot be read. */
    static String mixinSource() {
        try {
            java.net.URL at = Class.forName("org.spongepowered.asm.launch.MixinBootstrap", false, loader())
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();
            String path = at.getPath();
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String name = path.substring(path.lastIndexOf('/') + 1);
            return name.isEmpty() ? null : java.net.URLDecoder.decode(name, "UTF-8");
        } catch (Throwable unread) {
            return null;
        }
    }

    /** Whether a class is there to load, asked of its bytes so nothing is loaded by asking. */
    static boolean present(String className) {
        try {
            return loader().getResource(className.replace('.', '/') + ".class") != null;
        } catch (Throwable noLoader) {
            return false;
        }
    }

    private static ClassLoader loader() {
        return TrmtLoadingPlugin.class.getClassLoader();
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    /**
     * Asked by FML once {@link #injectData} has run: {@link MixinBooterWanted} where there is no Mixin, so Forge's own
     * check names MixinBooter on its missing-mod screen; nothing otherwise.
     */
    @Override
    public String getModContainerClass() {
        Route route = found;
        return route == null ? null : containerFor(route);
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
