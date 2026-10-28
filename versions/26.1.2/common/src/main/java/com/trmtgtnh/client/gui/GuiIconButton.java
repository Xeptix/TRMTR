package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.SettingsKeys;

/**
 * A 20 by 20 button of the game's own, with one of the settings screen's icons on it (0.9.228): the way back from
 * Cloth Config's settings screen to the mod's own (spec M14.3), with the same {@code toggle} icon the own screen's
 * header button carries the other way, so the two read as one switch. Its hover says what it is and what it does, as
 * the 1.7.10 edition's way back from Forge's screen does.
 */
final class GuiIconButton extends Button {

    private final String icon;

    GuiIconButton(int x, int y, String icon, Runnable pressed) {
        super(x, y, 20, 20, Component.literal(""), button -> pressed.run(), DEFAULT_NARRATION);
        this.icon = icon;
    }

    /**
     * The game's own face, then the icon where the words would be, as {@link OptionsButton}'s button draws itself: from
     * 26.1 a button's contents are all it draws, its sprite included, so the face is asked for first. Then its hover,
     * for the end of the frame: 26.x draws a hover set for the next frame last, over every widget.
     */
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractDefaultSprite(graphics);
        WornPathArt.settingsIcon(graphics, Canvas.iconCell(icon), getX() + 2, getY() + 2);
        if (isHovered()) hover(graphics, mouseX, mouseY);
    }

    /** Its hover: its name in gold, then what it does (spec M14.3), wrapped as the own screen wraps a hover. */
    private static void hover(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return;
        List<FormattedCharSequence> lines = new ArrayList<FormattedCharSequence>();
        lines.add(Component.literal(ChatFormatting.GOLD + I18n.get(SettingsKeys.TOGGLE_BACK))
            .getVisualOrderText());
        for (String line : GuiText.wrap(mc.font, I18n.get(SettingsKeys.TOGGLE_BACK_TIP),
            Math.min(260, mc.screen.width - 16))) {
            lines.add(Component.literal(line)
                .getVisualOrderText());
        }
        graphics.setTooltipForNextFrame(mc.font, lines, mouseX, mouseY);
    }
}
