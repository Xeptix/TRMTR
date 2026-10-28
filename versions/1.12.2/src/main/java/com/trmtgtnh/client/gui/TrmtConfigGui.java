package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.client.config.IConfigElement;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Forge's settings screen, for this mod: since 0.9.228 the other of two, opened where {@code client.settingsScreen}
 * says {@code other} ({@link ConfigScreens}); the mod's own, {@link WornPathScreen}, opens by default. Its one
 * addition is the way back (spec M14.3): an icon button at its top right, "TRMT's screen", which writes the choice and
 * opens the own screen over the same parent. On this top screen only - a category's own page is Forge's plain screen,
 * and its way back is Done, then the button.
 *
 * <p>
 * The client category comes first because it is the only part a player on someone else's
 * server can actually change: the rest is read from the server's copy of the config and shown
 * here for reference. Every category is listed rather than only the client one, so a
 * single-player world can be tuned without leaving the game.
 */
public class TrmtConfigGui extends GuiConfig {

    public TrmtConfigGui(GuiScreen parent) {
        super(parent, categories(), Trmt.MODID, false, false, Trmt.NAME);

    }

    /**
     * Drops the projection the screen was editing.
     *
     * <p>
     * On Done the read-back has already taken it, through the config-changed event, and this finds
     * nothing. On Escape there is no event and nothing has been read back - which is what Escape
     * means - so this is where the copy goes. Only ever called for this screen: the nested category
     * screens Forge opens are plain {@code GuiConfig} and do not inherit it.
     */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        ConfigAdapter.discard();
    }

    /** High enough not to collide with the ids Forge's own screen hands out. */
    private static final int ID_PUSH = 9021;
    private static final int ID_WEAR = 9022;
    private static final int ID_BACK = 9023;

    /** The way back's button: 20 by 20 at the top right, clear of Forge's title (spec 3.1). */
    private GuiIconButton back;

    /**
     * Offers these settings to the server.
     *
     * <p>
     * Shown whenever there is a world to offer them to, and not only to operators - because the
     * client cannot tell whether it is one. Its own {@code canCommandSenderUseCommand} answers
     * from a permission level a client does not have, so any button that appeared only for
     * operators would be a button that appeared for everybody who edited their client. The
     * server decides, says so in chat, and the refusal is the answer.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void initGui() {
        super.initGui();
        // The way back to the mod's own screen (spec M14.3), whether or not a world is open.
        // Where nothing of this screen's own stands (0.9.228): its buttons, and any text field a pack's mod gave it.
        int[] spot = ConfigScreens.wayBackSpot(widgetsOn(this), width, height);
        if (spot != null) {
            back = new GuiIconButton(ID_BACK, spot[0], spot[1], "toggle");
            buttonList.add(back);
        }
        if (net.minecraft.client.Minecraft.getMinecraft().world == null) return;

        // Forge sizes its scrolling list to end at height - 32, which is exactly where a button
        // on the row above it wants to be - so the button was drawn over the last category
        // rather than under it, and the bottom of the list was unreachable.
        //
        // Rebuilt against a screen two rows shorter rather than reaching into the list's own
        // protected bounds. The list takes its extent from this screen's height when it is
        // constructed, so borrowing the height for one line is the whole trick, and it uses
        // nothing that is not public API.
        int realHeight = height;
        height = realHeight - PUSH_ROW;
        entryList = new net.minecraftforge.fml.client.config.GuiConfigEntries(this, mc);
        height = realHeight;

        buttonList.add(
            new net.minecraft.client.gui.GuiButton(
                ID_PUSH,
                width / 2 - 154,
                height - 29 - PUSH_ROW,
                150,
                20,
                fitButton(net.minecraft.util.text.translation.I18n.translateToLocal("trmtgtnh.config.push"), 150)));
        // The Wear Table reads the same live config the push button offers, so it belongs on
        // the same row and needs the same live world to read.
        buttonList.add(
            new net.minecraft.client.gui.GuiButton(
                ID_WEAR,
                width / 2 + 4,
                height - 29 - PUSH_ROW,
                150,
                20,
                net.minecraft.util.text.translation.I18n.translateToLocal("trmtgtnh.weartable.button")));
    }

    /**
     * A button label cut to the button, because vanilla's never is.
     *
     * <p>
     * {@code GuiButton} centres its text and draws it whatever the width, so a label wider than
     * its button spills out over the screen on both sides. Nothing in the mod's own English hits
     * this now that the push label is shorter, but a translation will, and a label that stops
     * short is better than one lying across the Wear Table button beside it.
     */
    private String fitButton(String text, int width) {
        net.minecraft.client.gui.FontRenderer font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        int room = width - 8;
        if (font.getStringWidth(text) <= room) return text;
        return font.trimStringToWidth(text, Math.max(0, room - 8)) + "...";
    }

    /**
     * Everything a screen holds where a button could clash with it, as {x, y, width, height}: its visible buttons, and
     * every text field it keeps in a field of its own, by type, up through the classes it extends - GT New Horizons'
     * search field included, which no list of the screen's buttons holds.
     */
    static List<int[]> widgetsOn(TrmtConfigGui screen) {
        List<int[]> out = new ArrayList<int[]>();
        for (Object each : screen.buttonList) {
            net.minecraft.client.gui.GuiButton button = (net.minecraft.client.gui.GuiButton) each;
            if (button.visible) out.add(new int[] { button.x, button.y, button.width, button.height });
        }
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (!net.minecraft.client.gui.GuiTextField.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    net.minecraft.client.gui.GuiTextField text = (net.minecraft.client.gui.GuiTextField) field
                        .get(screen);
                    if (text != null && text.getVisible()) {
                        out.add(new int[] { text.x, text.y, text.width, text.height });
                    }
                } catch (ReflectiveOperationException | RuntimeException unread) {
                    // A field that cannot be read is passed over; the rest still place the button.
                }
            }
        }
        return out;
    }

    /** How much taller the button row makes the furniture at the bottom of the screen. */
    private static final int PUSH_ROW = 24;

    @Override
    protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (button.id == ID_BACK) {
            // Forge's screen saves its entries on Done only, so edits made here are left unsaved, as Escape leaves.
            net.minecraft.client.Minecraft.getMinecraft()
                .displayGuiScreen(ConfigScreens.switchTo(true, parentScreen));
            return;
        }
        if (button.id == ID_WEAR) {
            net.minecraft.client.Minecraft.getMinecraft()
                .displayGuiScreen(new GuiWearTable(this));
            return;
        }
        if (button.id != ID_PUSH) {
            super.actionPerformed(button);
            return;
        }
        com.trmtgtnh.network.TrmtNetwork.pushConfig(ConfigWords.asLines());
    }

    /** The way back's hover: its name, then what it does (spec M14.3). */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        super.drawScreen(mouseX, mouseY, partial);
        if (back != null && back.under(mouseX, mouseY)) {
            List<String> lines = new ArrayList<String>();
            lines.add(
                net.minecraft.util.text.TextFormatting.GOLD + net.minecraft.client.resources.I18n
                    .format(com.trmtgtnh.client.settings.SettingsKeys.TOGGLE_BACK));
            lines.addAll(
                mc.fontRenderer.listFormattedStringToWidth(
                    net.minecraft.client.resources.I18n
                        .format(com.trmtgtnh.client.settings.SettingsKeys.TOGGLE_BACK_TIP),
                    Math.min(260, width - 16)));
            drawHoveringText(lines, mouseX, mouseY);
        }
    }

    private static List<IConfigElement> categories() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        // Forge's own config object, built from the mod's settings for the life of this screen and
        // read back when Done is pressed. Forge's editors can only edit Forge's properties, and this
        // is the only place in the mod that still holds one. See ConfigAdapter.
        Configuration config = ConfigAdapter.project();
        if (config == null) return elements;
        showIconsOnBlockLists(config);
        slideTheCountedSettings(config);
        // The multipliers in the own screen's order (0.9.228, ConfigLayout.MULTIPLIERS_ORDER), not the alphabet's.
        ConfigCategory multipliers = config.getCategory(TrmtConfig.CATEGORY_MULTIPLIERS);
        multipliers.setPropertyOrder(ConfigLayout.ordered(TrmtConfig.CATEGORY_MULTIPLIERS, multipliers.keySet()));
        // After the sliders, which keep sliding: every row's hover says its range once (0.9.228, TrmtConfigEntries).
        for (String name : config.getCategoryNames()) {
            TrmtConfigEntries.hoverEverySetting(
                config.getCategory(name)
                    .getOrderedValues());
            // Each heading's button named as the own screen's tab is (0.9.228; Xep, 2026-10-25, "Use the tab names"):
            // Forge labels a category by its language key and falls back to the file's own lower-case name.
            String key = ConfigLayout.headingKey(name);
            if (key != null) config.getCategory(name)
                .setLanguageKey(key);
        }

        // Presets above even the client settings: it is the shortest way to a whole opinion, and
        // somebody who wants one should not have to walk past nine families to find it.
        if (config.hasCategory(com.trmtgtnh.config.Presets.CATEGORY)) {
            elements.add(new ConfigElement(config.getCategory(com.trmtgtnh.config.Presets.CATEGORY)));
        }
        // Client first: it is the setting a player is most likely here to change.
        elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_CLIENT)));
        elements.add(new ConfigElement(config.getCategory(Configuration.CATEGORY_GENERAL)));
        elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_HEALING)));
        elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_MULTIPLIERS)));
        elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_TRAMPLING)));
        elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_EXPLOSIONS)));
        elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_IMPACTS)));
        elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_SURFACES)));
        elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_PERFORMANCE)));
        if (config.hasCategory(TrmtConfig.CATEGORY_REINFORCE)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_REINFORCE)));
        }
        // Named by hand, like everything above it, because this list is written out rather than
        // walked - which is why six categories that were in the file did not appear on this screen
        // until 0.9.228. A category left out of here is a category only a text editor can reach.
        if (config.hasCategory(TrmtConfig.CATEGORY_POTIONS)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_POTIONS)));
        }
        if (config.hasCategory(TrmtConfig.CATEGORY_WEATHER)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_WEATHER)));
        }
        // The six the file alone held until 0.9.228, after the rest as on the own screen's tabs (Xep, answering the
        // spec's open question 3: "If there are settings missing then yeah we should add all categories there too").
        if (config.hasCategory(TrmtConfig.CATEGORY_GOLEM)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_GOLEM)));
        }
        if (config.hasCategory(TrmtConfig.CATEGORY_LOOT)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_LOOT)));
        }
        if (config.hasCategory(TrmtConfig.CATEGORY_WARD)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_WARD)));
        }
        if (config.hasCategory(TrmtConfig.CATEGORY_LIGHT)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_LIGHT)));
        }
        if (config.hasCategory(TrmtConfig.CATEGORY_INTEGRATION)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_INTEGRATION)));
        }
        if (config.hasCategory(TrmtConfig.CATEGORY_SPAWN_ITEMS)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_SPAWN_ITEMS)));
        }

        for (SurfaceFamily family : SurfaceFamily.values()) {
            String category = TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER + family.key();
            if (config.hasCategory(category)) {
                elements.add(new ConfigElement(config.getCategory(category)));
            }
        }
        return elements;
    }

    /**
     * Draws the two counting settings as sliders rather than as typed numbers.
     *
     * <p>
     * Here for the same two reasons the icon lists are: the class literal is a client GUI class
     * that does not exist on a dedicated server, and a config reload replaces every {@link Property}
     * object and takes the setting with it, while this runs every time the screen opens.
     *
     * <p>
     * Both of these are numbers somebody wants to feel rather than to know. How many pictures a run
     * of wear is drawn with, and how many rotations of each, are the two that multiply into the
     * block atlas, so they are also the two most worth being able to pull back a little at a time
     * on a machine that is struggling - which is a thing to drag, not a thing to type.
     */
    private static void slideTheCountedSettings(Configuration config) {
        slide(
            config.getCategory(TrmtConfig.CATEGORY_CLIENT)
                .get("wearGradations"));
        slide(
            config.getCategory(TrmtConfig.CATEGORY_CLIENT)
                .get("wearRotations"));
    }

    private static void slide(Property property) {
        if (property != null) property.setConfigEntryClass(GuiConfigEntries.NumberSliderEntry.class);
    }

    /**
     * Asks Forge's array editor to draw the block's icon beside each entry in the block lists.
     *
     * <p>
     * The mob multipliers are a list of the same shape, so they are given the same treatment with a
     * mob's head instead. The point in both cases is that a column of registry names is hard to read
     * back and easy to mistype, and a picture beside each row settles both at once.
     *
     * <p>
     * Here rather than in {@link TrmtConfig} for two reasons. The class literal loads a client
     * GUI class, which does not exist on a dedicated server. And a config reload replaces every
     * {@link Property} object, taking the setting with it - but this runs each time the screen
     * is opened, which is exactly when it needs to be true again.
     */
    private static void showIconsOnBlockLists(Configuration config) {
        iconify(
            config.getCategory(TrmtConfig.CATEGORY_SURFACES)
                .get("exclude"));

        Property mobs = config.getCategory(TrmtConfig.CATEGORY_MULTIPLIERS)
            .get("mobs");
        if (mobs != null) mobs.setArrayEntryClass(MobIconArrayEntry.class);

        for (SurfaceFamily family : SurfaceFamily.values()) {
            String name = TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER + family.key();
            if (!config.hasCategory(name)) continue;
            ConfigCategory category = config.getCategory(name);
            iconify(category.get("blocks"));
            iconify(category.get("resistantBlocks"));
            iconify(category.get("repairBlocks"));
        }
    }

    private static void iconify(Property property) {
        if (property != null) property.setArrayEntryClass(BlockIconArrayEntry.class);
    }
}
