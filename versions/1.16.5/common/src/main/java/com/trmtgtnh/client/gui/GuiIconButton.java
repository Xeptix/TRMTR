package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

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
        super(x, y, 20, 20, new TextComponent(""), button -> pressed.run(), GuiIconButton::hover);
        this.icon = icon;
    }

    /**
     * The game's button face, as its own buttons draw it, with the icon where the words would be, then its hover: as
     * {@link OptionsButton}'s button draws itself, since 1.16.5's {@code Button} draws its face, words and hover in one
     * method and the icon would otherwise be drawn over the hover.
     */
    @Override
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
        WornPathArt.bind(WIDGETS_LOCATION, 0xFFFFFFFF);
        int face = getYImage(isHovered());
        int half = getWidth() / 2;
        blit(pose, x, y, 0, 46 + face * 20, half, getHeight());
        blit(pose, x + half, y, 200 - (getWidth() - half), 46 + face * 20, getWidth() - half, getHeight());
        WornPathArt.settingsIcon(pose, Canvas.iconCell(icon), x + 2, y + 2);
        RenderSystem.color4f(1F, 1F, 1F, 1F);
        if (isHovered()) renderToolTip(pose, mouseX, mouseY);
    }

    /** Its hover: its name in gold, then what it does (spec M14.3). */
    private static void hover(Button button, PoseStack pose, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return;
        List<Component> lines = new ArrayList<Component>();
        lines.add(new TextComponent(ChatFormatting.GOLD + I18n.get(SettingsKeys.TOGGLE_BACK)));
        for (String line : GuiText.wrap(mc.font, I18n.get(SettingsKeys.TOGGLE_BACK_TIP), Math.min(260, mc.screen.width - 16))) {
            lines.add(new TextComponent(line));
        }
        mc.screen.renderComponentTooltip(pose, lines, mouseX, mouseY);
    }
}
