package com.trmtgtnh.client.gui;

import net.minecraft.client.gui.GuiScreen;

import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.TrmtConfig;

/**
 * Which settings screen opens: this mod's own ({@link WornPathScreen}), or Forge's built-in one ({@link TrmtConfigGui})
 * where {@code client.settingsScreen} asks for it (0.9.228).
 *
 * <p>
 * Xep, 2026-10-20: "could we make it toggleable for 1.7.10 and 1.12.2 if users don't like it?" - and "Our own" by
 * default. Every way to the settings comes through here: the mod list's Config button ({@link TrmtGuiFactory}, which
 * Forge asks for a screen at every press, so a change to the setting takes at the next press), {@code /trmt config},
 * the key, and the Options-screen button. Forge's screen is part of every 1.12.2 game, so the other screen always
 * exists here; the newer editions ask whether Cloth Config is installed instead.
 *
 * <p>
 * {@code -Dtrmt.config.ownScreen=true} opens the own screen whatever the setting says, and
 * {@code -Dtrmt.config.otherScreen=true} Forge's - how the rig photographs both in one game.
 */
public final class ConfigScreens {

    /** The way back's size: a 20 by 20 icon button, the game's own button height (spec M14.3). */
    static final int WAY_BACK = 20;

    /**
     * Where the way back goes on Forge's screen, as {x, y} (0.9.228): at {@code width - 26, 8}, unless something of
     * that screen's own stands there, then one button further down its right edge while there is room, then one
     * further left along its top. Down before left, unlike Cloth's on 1.16.5: GT New Horizons gives Forge's screen a
     * search field from its title to the right edge (Xep: it covered the way back), and puts the title just left of
     * it, where no widget list can see a drawn string - the right edge below the field is clear in that screen and in
     * Forge's own. Never over a widget, by {@link OptionsButton}'s own test of a spot; null where nothing is free.
     */
    static int[] wayBackSpot(java.util.List<int[]> widgets, int screenWidth, int screenHeight) {
        int x = screenWidth - 26;
        for (int y = 8; y + WAY_BACK <= screenHeight; y += WAY_BACK + OptionsButton.GAP) {
            if (!OptionsButton.taken(widgets, x, y, WAY_BACK)) return new int[] { x, y };
        }
        for (x = screenWidth - 26; x >= 0; x -= WAY_BACK + OptionsButton.GAP) {
            if (!OptionsButton.taken(widgets, x, 8, WAY_BACK)) return new int[] { x, 8 };
        }
        return null;
    }

    /** The switch that opens the mod's own screen whatever the setting says. */
    static final String OWN_SCREEN = "trmt.config.ownScreen";

    /** The switch that opens the other screen, Forge's here, whatever the setting says. */
    static final String OTHER_SCREEN = "trmt.config.otherScreen";

    private ConfigScreens() {}

    /**
     * The settings screen.
     *
     * @param parent what Done and Cancel go back to
     */
    public static GuiScreen open(GuiScreen parent) {
        if (opensOther(
            TrmtConfig.settingsScreen,
            true,
            Boolean.getBoolean(OWN_SCREEN),
            Boolean.getBoolean(OTHER_SCREEN))) return new TrmtConfigGui(parent);
        return new WornPathScreen(parent);
    }

    /**
     * Whether there is another settings screen to switch to (spec M14.1, M14.5): Forge's is part of every 1.12.2 game,
     * so the toggle is always drawn here.
     */
    public static boolean otherExists() {
        return true;
    }

    /** The other screen's name, as a word key: Forge's here. */
    public static String otherNameKey() {
        return SettingsKeys.TOGGLE_FORGE;
    }

    /**
     * The screen to switch to - the own one, or the other - over the same parent, the choice written into
     * {@code client.settingsScreen} first (spec M14.2, M14.3; Xep: "We should be able to easily toggle between the old
     * settings screen and our own"). Written at once and alone, not on the next Save: the next way in - the Options
     * button, the key, {@code /trmt config}, the mod list - reads it at its press, and a switch that waited for a
     * Save would be forgotten by everybody who switched and left.
     */
    public static GuiScreen switchTo(boolean own, GuiScreen parent) {
        remember(TrmtConfig.raw(), own);
        return own ? new WornPathScreen(parent) : new TrmtConfigGui(parent);
    }

    /**
     * The choice of screen, into the live setting and the file: {@code settingsScreen} set and the file saved. Neither
     * screen puts an edit into the file's settings before its own Save, so the other screen's unsaved edits, Forge's or
     * ours, are not written by this - they stay where that screen's Save or Cancel puts them. Plain, so a test can
     * write it into a file of its own.
     */
    static void remember(ConfigFile config, boolean own) {
        String value = own ? TrmtConfig.SCREEN_OWN : TrmtConfig.SCREEN_OTHER;
        TrmtConfig.settingsScreen = value;
        if (config == null || !config.hasCategory(TrmtConfig.CATEGORY_CLIENT)) return;
        ConfigFile.Setting setting = config.getCategory(TrmtConfig.CATEGORY_CLIENT)
            .get("settingsScreen");
        if (setting == null || value.equals(setting.getString())) return;
        setting.set(value);
        // The live file through its own save, which refuses to write a file assembled from a bad read.
        if (config == TrmtConfig.raw()) TrmtConfig.save();
        else config.save();
    }

    /**
     * Whether the other screen opens: only where it exists - always, on 1.12.2 - and then when its switch asks for it,
     * or the setting says {@code other}, and the own screen's switch does not ask for the own. Plain, so a test can ask
     * it every way, and the same question every edition asks.
     */
    static boolean opensOther(String setting, boolean exists, boolean ownSwitch, boolean otherSwitch) {
        if (!exists || ownSwitch) return false;
        return otherSwitch || TrmtConfig.SCREEN_OTHER.equalsIgnoreCase(setting == null ? "" : setting.trim());
    }
}
