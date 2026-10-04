package com.trmtgtnh;

/**
 * Whether another mod is installed.
 *
 * <p>
 * One line of Forge, in one place, so that the three thousand lines of settings that want the answer
 * do not each have to name a loader to get it. {@link com.trmtgtnh.config.TrmtConfig} asks this four
 * times - whether GregTech, Amazing Trophies and Chisel are here - and those four calls were the
 * last thing keeping the whole settings layer tied to Forge.
 *
 * <p>
 * This is one of the handful of things every loader can do and none of them agree how, so it is a
 * seam by design rather than by accident: a later edition replaces this class and nothing else
 * changes. The compat classes that talk to those mods keep their own checks, because a class that
 * already names another mod's API gains nothing by not naming the loader too.
 */
public final class ModsPresent {

    private ModsPresent() {}

    /**
     * Whether the mod with this id is loaded.
     *
     * <p>
     * Safe before the mod list exists, which matters because the settings are read early: Forge
     * answers false rather than throwing, and a pack is not going to gain a mod halfway through a
     * run, so the answer does not need to be cached to be stable.
     */
    public static boolean has(String modId) {
        return net.minecraftforge.fml.common.Loader.isModLoaded(modId);
    }
}
