package com.trmtgtnh.client.gui;

import net.minecraft.client.gui.screens.Screen;

/**
 * Which settings screen a loader's Config button opens: Cloth Config's where Cloth is installed, and this mod's own
 * worn-path screen where it is not.
 *
 * <p>
 * <strong>Cloth is optional on 26.x</strong> - Xep's decision for 0.9.227: "if it's there it uses that, if not uses
 * our in house one". Every older modern edition requires it, because Forge's own config screen was gone and Cloth was
 * the one library on every loader. At 26.1.2 Cloth has no Forge build at all, so requiring it would leave Forge with
 * no settings screen, and NeoForge and Fabric players would be asked for a library to open one page.
 *
 * <p>
 * Cloth is asked for by its own class rather than by its mod id, because the id is spelled differently on each loader
 * and the class is the thing {@link ConfigScreen} actually needs. {@link ConfigScreen} is touched only once Cloth is
 * known to be there: it names Cloth's types, and loading it without them throws.
 *
 * <p>
 * {@code -Dtrmt.config.ownScreen=true} opens the mod's own screen even with Cloth installed, which is how the rig
 * photographs both screens in one game.
 */
public final class ConfigScreens {

    /** A class every Cloth Config build since 4.x has carried, and the first one {@link ConfigScreen} uses. */
    static final String CLOTH_CLASS = "me.shedaniel.clothconfig2.api.ConfigBuilder";

    /** The switch that opens the mod's own screen whatever is installed. */
    static final String OWN_SCREEN = "trmt.config.ownScreen";

    private static volatile Boolean cloth;

    private ConfigScreens() {}

    /**
     * The settings screen, or null where there is no config to show.
     *
     * @param parent what Done and Cancel go back to
     */
    public static Screen open(Screen parent) {
        if (clothInstalled() && !Boolean.getBoolean(OWN_SCREEN)) return ConfigScreen.build(parent);
        return WornPathScreen.build(parent);
    }

    /** Whether Cloth Config can be loaded from here, asked once a session. */
    public static boolean clothInstalled() {
        Boolean known = cloth;
        if (known == null) {
            known = Boolean.valueOf(present(CLOTH_CLASS, ConfigScreens.class.getClassLoader()));
            cloth = known;
        }
        return known.booleanValue();
    }

    /** Whether a class can be found without running anything in it. */
    static boolean present(String name, ClassLoader from) {
        try {
            Class.forName(name, false, from);
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }
}
