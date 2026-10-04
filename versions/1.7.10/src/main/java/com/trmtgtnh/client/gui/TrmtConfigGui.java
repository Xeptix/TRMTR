package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.IConfigElement;

/**
 * The in-game settings screen.
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

    /** High enough not to collide with the ids Forge's own screen hands out. */
    private static final int ID_PUSH = 9021;
    private static final int ID_WEAR = 9022;

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
        if (net.minecraft.client.Minecraft.getMinecraft().theWorld == null) return;

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
        entryList = new cpw.mods.fml.client.config.GuiConfigEntries(this, mc);
        height = realHeight;

        buttonList.add(
            new net.minecraft.client.gui.GuiButton(
                ID_PUSH,
                width / 2 - 154,
                height - 29 - PUSH_ROW,
                150,
                20,
                fitButton(net.minecraft.util.StatCollector.translateToLocal("trmtgtnh.config.push"), 150)));
        // The Wear Table reads the same live config the push button offers, so it belongs on
        // the same row and needs the same live world to read.
        buttonList.add(
            new net.minecraft.client.gui.GuiButton(
                ID_WEAR,
                width / 2 + 4,
                height - 29 - PUSH_ROW,
                150,
                20,
                net.minecraft.util.StatCollector.translateToLocal("trmtgtnh.weartable.button")));
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

    /** How much taller the button row makes the furniture at the bottom of the screen. */
    private static final int PUSH_ROW = 24;

    @Override
    protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (button.id == ID_WEAR) {
            net.minecraft.client.Minecraft.getMinecraft()
                .displayGuiScreen(new GuiWearTable(this));
            return;
        }
        if (button.id != ID_PUSH) {
            super.actionPerformed(button);
            return;
        }
        com.trmtgtnh.network.TrmtNetwork.pushConfig(settingsAsLines());
    }

    /**
     * Every simple setting, as category, key and value.
     *
     * <p>
     * Lists go as well, joined on a control character, and the reason is written where they are
     * joined. What never goes is decided by {@link com.trmtgtnh.network.PacketPushConfig#travels}:
     * this client's own heading, the quality chooser and the one quality figure filed under surfaces, the
     * presets' bookkeeping, and the enchantment and potion ids.
     */
    private static java.util.List<String> settingsAsLines() {
        java.util.List<String> lines = new ArrayList<String>();
        Configuration config = TrmtConfig.raw();
        if (config == null) return lines;
        for (String name : config.getCategoryNames()) {
            ConfigCategory category = config.getCategory(name);
            for (Property property : category.getValues()
                .values()) {
                if (property == null || property.getName() == null) continue;
                if (!com.trmtgtnh.network.PacketPushConfig.travels(name, property.getName())) continue;
                // Lists go too, joined on a control character no config value can
                // contain. They used to be skipped here and refused at the far end,
                // which made the one setting somebody most wants to hand a server -
                // which blocks count as what - the one setting that silently would not go.
                String value = property.isList() ? join(property.getStringList()) : property.getString();
                String line = name + "\t" + property.getName() + "\t" + value;
                // Dropped whole rather than cut short. The block lists detection writes run
                // to thousands of characters, and half of one applied to a server's config
                // would be worse than none of it - and pointless besides, since that server's
                // own detection writes its own.
                if (com.trmtgtnh.network.PacketPushConfig.fits(line)) lines.add(line);
            }
        }
        return lines;
    }

    /** A list as one line, on a separator no config value can contain. */
    private static String join(String[] values) {
        if (values == null || values.length == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append('\u001f');
            out.append(values[i] == null ? "" : values[i]);
        }
        return out.toString();
    }

    private static List<IConfigElement> categories() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        Configuration config = TrmtConfig.raw();
        if (config == null) return elements;
        showIconsOnBlockLists(config);
        slideTheCountedSettings(config);

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
        // walked - which is why six categories that exist in the file have never appeared on this
        // screen at all. A category left out of here is a category only a text editor can reach.
        if (config.hasCategory(TrmtConfig.CATEGORY_POTIONS)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_POTIONS)));
        }
        if (config.hasCategory(TrmtConfig.CATEGORY_WEATHER)) {
            elements.add(new ConfigElement(config.getCategory(TrmtConfig.CATEGORY_WEATHER)));
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
     * {@link Property} object, taking the setting with it — but this runs each time the screen
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
