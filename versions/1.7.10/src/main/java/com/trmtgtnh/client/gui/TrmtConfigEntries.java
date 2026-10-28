package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.GuiUtils;
import cpw.mods.fml.client.config.IConfigElement;

/**
 * Forge's own rows for its settings screen here ({@link TrmtConfigGui}), each with this mod's hover in place of Forge's
 * (0.9.228): the name, the comment without the range Forge's getters appended to it, and the range or default once, in
 * {@link ConfigWords#rangeLine}'s words - the line the own screen ends its hover with, and every edition's.
 *
 * <p>
 * Forge built its hover by putting its own range line after the comment, which already ended in the
 * {@code [range: ... ~ ..., default: ...]} {@code getInt} and {@code getFloat} append, so a number's range was said
 * twice - and on GT New Horizons' copy of Forge the second time with the three numbers in the wrong places. Each row
 * here is Forge's row with only its hover written over as it is made - extended where Forge lets it be, and the on-off
 * and chooser rows, whose constructors Forge keeps to itself, written out as Forge writes them. Forge finds them by
 * the class a setting names ({@link #hoverEverySetting}), and builds each with the constructor it looks for, so every
 * class is public and takes exactly {@code (GuiConfig, GuiConfigEntries, IConfigElement)}.
 */
public final class TrmtConfigEntries {

    private TrmtConfigEntries() {}

    /**
     * Names this file's row for every setting, by the setting's kind, where Forge would otherwise choose its own. Runs
     * each time the screen opens, because a reload replaces every setting and the class goes with it. A slider stays a
     * slider; a list keeps the icon rows it was given for its own editor.
     */
    static void hoverEverySetting(Iterable<Property> settings) {
        for (Property setting : settings) {
            if (setting == null) continue;
            Class<?> named = setting.getConfigEntryClass();
            if (named == GuiConfigEntries.NumberSliderEntry.class) {
                setting.setConfigEntryClass(Slider.class);
            } else if (named != null) {
                continue;
            } else if (setting.isList()) {
                setting.setConfigEntryClass(Array.class);
            } else if (setting.getType() == Property.Type.BOOLEAN) {
                setting.setConfigEntryClass(OnOff.class);
            } else if (setting.getType() == Property.Type.INTEGER) {
                setting.setConfigEntryClass(Whole.class);
            } else if (setting.getType() == Property.Type.DOUBLE) {
                setting.setConfigEntryClass(Decimal.class);
            } else if (setting.getType() == Property.Type.STRING) {
                String[] choices = setting.getValidValues();
                setting.setConfigEntryClass(choices != null && choices.length > 0 ? Choice.class : Words.class);
            }
        }
    }

    /**
     * The hover for one row: Forge's own first lines - the name, then the setting's translated hover where the
     * language file has one, its comment otherwise - then the range line, then Forge's restart note.
     */
    @SuppressWarnings("unchecked")
    static List<String> hover(String name, IConfigElement<?> element, boolean allRequireRestart) {
        Minecraft mc = Minecraft.getMinecraft();
        String key = element.getLanguageKey() + ".tooltip";
        String translated = I18n.format(key)
            .replace("\\n", "\n");
        String comment = ConfigWords.withoutRange(element.getComment());
        String said = !translated.equals(key) ? EnumChatFormatting.YELLOW + translated
            : !comment.isEmpty() ? EnumChatFormatting.YELLOW + comment : EnumChatFormatting.RED + "No tooltip defined.";
        List<String> out = new ArrayList<String>(
            mc.fontRenderer.listFormattedStringToWidth(EnumChatFormatting.GREEN + name + "\n" + said, 300));
        String range = ConfigWords.rangeLine(
            element.isList(),
            element.getType()
                .name(),
            String.valueOf(element.getMinValue()),
            String.valueOf(element.getMaxValue()),
            String.valueOf(element.getDefault()));
        if (!range.isEmpty())
            out.addAll(mc.fontRenderer.listFormattedStringToWidth(EnumChatFormatting.AQUA + range, 300));
        if (element.requiresMcRestart() || allRequireRestart) {
            out.add(EnumChatFormatting.RED + "[" + I18n.format("fml.configgui.gameRestartTitle") + "]");
        }
        return out;
    }

    /**
     * Forge's on-off row, as Forge's {@code BooleanEntry} is - which cannot be extended, its constructor being private
     * to Forge's own list - with this file's hover.
     */
    public static class OnOff extends GuiConfigEntries.ButtonEntry {

        protected final boolean beforeValue;
        protected boolean currentValue;

        @SuppressWarnings("rawtypes")
        public OnOff(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
            super(owningScreen, owningEntryList, configElement);
            this.beforeValue = Boolean.valueOf(
                configElement.get()
                    .toString());
            this.currentValue = beforeValue;
            this.btnValue.enabled = enabled();
            updateValueButtonText();
            toolTip = hover(name, configElement, owningScreen.allRequireMcRestart);
        }

        @Override
        public void updateValueButtonText() {
            this.btnValue.displayString = I18n.format(String.valueOf(currentValue));
            btnValue.packedFGColour = currentValue ? GuiUtils.getColorCode('2', true)
                : GuiUtils.getColorCode('4', true);
        }

        @Override
        public void valueButtonPressed(int slotIndex) {
            if (enabled()) currentValue = !currentValue;
        }

        @Override
        public boolean isDefault() {
            return currentValue == Boolean.valueOf(
                configElement.getDefault()
                    .toString());
        }

        @Override
        public void setToDefault() {
            if (enabled()) {
                currentValue = Boolean.valueOf(
                    configElement.getDefault()
                        .toString());
                updateValueButtonText();
            }
        }

        @Override
        public boolean isChanged() {
            return currentValue != beforeValue;
        }

        @Override
        public void undoChanges() {
            if (enabled()) {
                currentValue = beforeValue;
                updateValueButtonText();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement() {
            if (enabled() && isChanged()) {
                configElement.set(currentValue);
                return configElement.requiresMcRestart();
            }
            return false;
        }

        @Override
        public Boolean getCurrentValue() {
            return currentValue;
        }

        @Override
        public Boolean[] getCurrentValues() {
            return new Boolean[] { getCurrentValue() };
        }
    }

    public static class Whole extends GuiConfigEntries.IntegerEntry {

        @SuppressWarnings("rawtypes")
        public Whole(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
            super(owningScreen, owningEntryList, configElement);
            toolTip = hover(name, configElement, owningScreen.allRequireMcRestart);
        }
    }

    public static class Decimal extends GuiConfigEntries.DoubleEntry {

        @SuppressWarnings("rawtypes")
        public Decimal(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
            super(owningScreen, owningEntryList, configElement);
            toolTip = hover(name, configElement, owningScreen.allRequireMcRestart);
        }
    }

    public static class Words extends GuiConfigEntries.StringEntry {

        @SuppressWarnings("rawtypes")
        public Words(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
            super(owningScreen, owningEntryList, configElement);
            toolTip = hover(name, configElement, owningScreen.allRequireMcRestart);
        }
    }

    /**
     * Forge's chooser row, as Forge's {@code CycleValueEntry} is - private to Forge's own list as the on-off row is -
     * with this file's hover. A value not on the list starts the chooser at its first, as Forge's does.
     */
    public static class Choice extends GuiConfigEntries.ButtonEntry {

        protected final int beforeIndex;
        protected final int defaultIndex;
        protected int currentIndex;

        @SuppressWarnings("rawtypes")
        public Choice(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
            super(owningScreen, owningEntryList, configElement);
            beforeIndex = indexOf(
                configElement.get()
                    .toString());
            defaultIndex = indexOf(
                configElement.getDefault()
                    .toString());
            currentIndex = beforeIndex;
            this.btnValue.enabled = enabled();
            updateValueButtonText();
            toolTip = hover(name, configElement, owningScreen.allRequireMcRestart);
        }

        private int indexOf(String value) {
            String[] valid = configElement.getValidValues();
            for (int i = 0; i < valid.length; i++) {
                if (valid[i].equalsIgnoreCase(value)) return i;
            }
            return 0;
        }

        @Override
        public void updateValueButtonText() {
            this.btnValue.displayString = I18n.format(configElement.getValidValues()[currentIndex]);
        }

        @Override
        public void valueButtonPressed(int slotIndex) {
            if (enabled()) {
                if (++this.currentIndex >= configElement.getValidValues().length) this.currentIndex = 0;
                updateValueButtonText();
            }
        }

        @Override
        public boolean isDefault() {
            return currentIndex == defaultIndex;
        }

        @Override
        public void setToDefault() {
            if (enabled()) {
                currentIndex = defaultIndex;
                updateValueButtonText();
            }
        }

        @Override
        public boolean isChanged() {
            return currentIndex != beforeIndex;
        }

        @Override
        public void undoChanges() {
            if (enabled()) {
                currentIndex = beforeIndex;
                updateValueButtonText();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement() {
            if (enabled() && isChanged()) {
                configElement.set(configElement.getValidValues()[currentIndex]);
                return configElement.requiresMcRestart();
            }
            return false;
        }

        @Override
        public String getCurrentValue() {
            return configElement.getValidValues()[currentIndex];
        }

        @Override
        public String[] getCurrentValues() {
            return new String[] { getCurrentValue() };
        }
    }

    public static class Array extends GuiConfigEntries.ArrayEntry {

        @SuppressWarnings("rawtypes")
        public Array(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
            super(owningScreen, owningEntryList, configElement);
            toolTip = hover(name, configElement, owningScreen.allRequireMcRestart);
        }
    }

    public static class Slider extends GuiConfigEntries.NumberSliderEntry {

        @SuppressWarnings("rawtypes")
        public Slider(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
            super(owningScreen, owningEntryList, configElement);
            toolTip = hover(name, configElement, owningScreen.allRequireMcRestart);
        }
    }
}
