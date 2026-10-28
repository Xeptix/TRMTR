package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.config.ConfigFile;
import com.trmtgtnh.config.TrmtConfig;

/**
 * Which settings screen opens: this mod's own ({@link WornPathScreen}), or Cloth Config's ({@link ConfigScreen}) where
 * it is installed and {@code client.settingsScreen} asks for it (0.9.228).
 *
 * <p>
 * <strong>Cloth is optional on 26.x</strong> - Xep's decision for 0.9.227: "if it's there it uses that, if not uses
 * our in house one", and from 0.9.228 in every edition (Xep, 2026-10-20: "If the user has it, it uses those (i.e
 * Cloth), otherwise it falls back to our own in-house screen"). At 26.1.2 Cloth has no Forge build at all, so requiring
 * it would leave Forge with no settings screen, and NeoForge and Fabric players would be asked for a library to open
 * one page. Every way to the settings comes through here: each loader's mod list (NeoForge's and Forge's config
 * factories, {@code FabricModMenu}), {@code /trmt config}, the key, and the Options-screen button.
 *
 * <p>
 * Cloth is asked for by its own class rather than by its mod id, because the id is spelled differently on each loader
 * ({@code cloth-config} on Forge, {@code cloth-config2} on Fabric) and the class is the thing {@link ConfigScreen}
 * actually needs. {@link ConfigScreen} is touched only once Cloth is known to be there: it names Cloth's types, and
 * loading it without them throws.
 *
 * <p>
 * <strong>There is no other screen without Cloth</strong> (spec 3.4, M14.5): NeoForge's own {@code ConfigurationScreen}
 * is in every 26.x NeoForge jar and does not serve - it shows NeoForge's own config specs, which this mod's file is not
 * (Xep, 2026-10-21: NeoForge's own screen is not the other screen) - Forge 26.x has no generic config screen and Fabric
 * has none. Then the toggle is not drawn on the own screen, and {@code client.settingsScreen}'s hover
 * says why ({@code toggle.none}).
 *
 * <p>
 * <strong>From 0.9.228 the own screen opens first</strong> (Xep, 2026-10-20: "Our own" by default), in every edition:
 * Cloth's opens only when {@code client.settingsScreen} is {@code other} and Cloth is there. The setting is shown on
 * both screens, so either can switch to the other.
 *
 * <p>
 * {@code -Dtrmt.config.ownScreen=true} opens the mod's own screen whatever the setting says, and
 * {@code -Dtrmt.config.otherScreen=true} Cloth's where it is installed - how the rig photographs both in one game.
 */
public final class ConfigScreens {

    /** A class every Cloth Config build since 4.x has carried, and the first one {@link ConfigScreen} uses. */
    static final String CLOTH_CLASS = "me.shedaniel.clothconfig2.api.ConfigBuilder";

    /** The switch that opens the mod's own screen whatever the setting says. */
    static final String OWN_SCREEN = "trmt.config.ownScreen";

    /** The switch that opens the other screen, Cloth's here, whatever the setting says. */
    static final String OTHER_SCREEN = "trmt.config.otherScreen";

    private static volatile Boolean cloth;

    /** Cloth's screen {@link ConfigScreen} built last, and what it was built over, for the way back. */
    private static java.lang.ref.WeakReference<Screen> builtCloth = new java.lang.ref.WeakReference<Screen>(null);

    private static java.lang.ref.WeakReference<Screen> builtOver = new java.lang.ref.WeakReference<Screen>(null);

    private ConfigScreens() {}

    /**
     * The settings screen.
     *
     * @param parent what Save and Cancel go back to
     */
    public static Screen open(Screen parent) {
        if (opensOther()) {
            Screen other = ConfigScreen.build(parent);
            if (other != null) return other;
        }
        return new WornPathScreen(parent);
    }

    /**
     * Whether there is another settings screen to switch to (spec M14.1, M14.5): Cloth's, where it is installed. With
     * none, the own screen draws no toggle and the setting's hover says why.
     */
    public static boolean otherExists() {
        return clothInstalled();
    }

    /** The other screen's name, as a word key: Cloth's here. */
    public static String otherNameKey() {
        return SettingsKeys.TOGGLE_CLOTH;
    }

    /**
     * The screen to switch to - the own one, or the other - over the same parent, the choice written into
     * {@code client.settingsScreen} first (spec M14.2, M14.3; Xep: "We should be able to easily toggle between the old
     * settings screen and our own"). Written at once and alone, not on the next Save: the next way in - the Options
     * button, the key, {@code /trmt config}, the mod list - reads it at its press, and a switch that waited for a
     * Save would be forgotten by everybody who switched and left. Without Cloth the other is the own screen again.
     */
    public static Screen switchTo(boolean own, Screen parent) {
        remember(TrmtConfig.raw(), own);
        if (!own && clothInstalled()) {
            Screen other = ConfigScreen.build(parent);
            if (other != null) return other;
        }
        return new WornPathScreen(parent);
    }

    /**
     * The choice of screen, into the live setting and the file: {@code settingsScreen} set and the file saved. Neither
     * screen puts an edit into the file's settings before its own Save, so the other screen's unsaved edits, Cloth's or
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

    private static boolean opensOther() {
        return opensOther(
            TrmtConfig.settingsScreen,
            clothInstalled(),
            Boolean.getBoolean(OWN_SCREEN),
            Boolean.getBoolean(OTHER_SCREEN));
    }

    /**
     * Whether the other screen - Cloth's - opens: only where it is installed, and then when its switch asks for it, or
     * the setting says {@code other} and the own screen's switch does not ask for that one. Plain, so a test can ask it
     * every way, and the same question every edition asks.
     */
    static boolean opensOther(String setting, boolean exists, boolean ownSwitch, boolean otherSwitch) {
        if (!exists || ownSwitch) return false;
        return otherSwitch || TrmtConfig.SCREEN_OTHER.equalsIgnoreCase(setting == null ? "" : setting.trim());
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

    // ------------------------------------------------------------------
    // What each loader adds once a screen has laid itself out
    // ------------------------------------------------------------------

    /**
     * What this mod adds to another class's screen once it has laid itself out, each loader calling this from its own
     * after-init event - NeoForge's and Forge's {@code ScreenEvent.Init.Post}, Fabric's {@code ScreenEvents.AFTER_INIT}
     * - with the screen's widgets and a way to add one: the Options screen's small button ({@link OptionsButton}), and
     * on Cloth's screen this mod built, the way back to the own screen ({@link #wayBackTo}).
     *
     * <p>
     * Through the loaders rather than through Cloth's own {@code setAfterInitConsumer} and an invoker on
     * {@code Screen.addButton}, which the specification names (3.2): adding to another class's screen needs that
     * protected method, and a common mixin here may name no game member ({@code CommonMixinsNeedNoRefmapTest}) - while
     * both loaders already hand every screen's widgets over at this moment, with a way to add one.
     */
    public static void afterInit(Screen screen, List<?> children, Consumer<AbstractWidget> add) {
        OptionsButton.addTo(screen, children, add);
        wayBackTo(screen, children, add);
    }

    /** The way back's size: a 20 by 20 icon button, the game's own button height (spec M14.3, 3.2). */
    static final int WAY_BACK = 20;

    /**
     * The way back on Cloth's screen (spec M14.3, 3.2): a 20 by 20 icon button at its top right, in its title row - which
     * Cloth leaves empty but for the title it draws in the middle, its tabs beginning 41 pixels down - placed where no
     * widget of Cloth's stands ({@link #wayBackSpot}). Only on the screen {@link ConfigScreen} built; a category's
     * folders are on the same screen there, so the way back is on every page of it.
     */
    static void wayBackTo(Screen screen, List<?> children, Consumer<AbstractWidget> add) {
        if (screen == null || screen != builtCloth.get()) return;
        List<int[]> widgets = new ArrayList<int[]>();
        for (Object each : children) {
            if (each instanceof AbstractWidget && ((AbstractWidget) each).visible) {
                AbstractWidget w = (AbstractWidget) each;
                widgets.add(new int[] { w.getX(), w.getY(), w.getWidth(), w.getHeight() });
            }
        }
        int[] at = wayBackSpot(widgets, screen.width, screen.height);
        if (at != null) add.accept(new GuiIconButton(at[0], at[1], "toggle", () -> switchBack(screen)));
    }

    /**
     * Where the way back goes, as {x, y}: at {@code width - 26, 8} as on Forge's screen in the 1.7.10 edition, unless a
     * widget stands there, then one button further left, while there is room; and below where the screen's left edge
     * comes first - never over another button, by {@link OptionsButton}'s own test of a spot. Null where nothing is
     * free.
     */
    static int[] wayBackSpot(List<int[]> widgets, int screenWidth, int screenHeight) {
        int x = screenWidth - 26;
        int y = 8;
        while (x >= 0 && OptionsButton.taken(widgets, x, y, WAY_BACK)) x -= WAY_BACK + OptionsButton.GAP;
        if (x >= 0) return new int[] { x, y };
        x = screenWidth - 26;
        y = 8 + WAY_BACK + OptionsButton.GAP;
        while (y + WAY_BACK <= screenHeight && OptionsButton.taken(widgets, x, y, WAY_BACK)) {
            y += WAY_BACK + OptionsButton.GAP;
        }
        return y + WAY_BACK <= screenHeight ? new int[] { x, y } : null;
    }

    /**
     * The way back pressed: the choice written and the own screen opened over the screen Cloth's was opened over.
     * Cloth's screen saves its entries on Save only, so edits made there are left unsaved, as Escape leaves them.
     */
    private static void switchBack(Screen cloth) {
        net.minecraft.client.Minecraft.getInstance()
            .setScreen(switchTo(true, cloth == builtCloth.get() ? builtOver.get() : null));
    }

    /**
     * Told by {@link ConfigScreen} of each Cloth screen it builds and what it was built over: the screen the way back is
     * added to, and where the own screen goes back to. Kept here, not there, because this class is loaded whether or
     * not Cloth is, and that one only where it is; held weakly, as a screen the player has left is gone.
     */
    static void builtCloth(Screen cloth, Screen over) {
        builtCloth = new java.lang.ref.WeakReference<Screen>(cloth);
        builtOver = new java.lang.ref.WeakReference<Screen>(over);
    }
}
