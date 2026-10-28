package com.trmtgtnh.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

import com.trmtgtnh.client.settings.Canvas;

/**
 * A 20 by 20 button of the game's own, with one of the settings screen's icons on it (0.9.228): the way back from
 * Forge's settings screen to the mod's own (spec M14.3), with the same {@code toggle} icon the own screen's header
 * button carries the other way, so the two read as one switch.
 */
final class GuiIconButton extends GuiButton {

    private final String icon;

    GuiIconButton(int id, int x, int y, String icon) {
        super(id, x, y, 20, 20, "");
        this.icon = icon;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partial) {
        super.drawButton(mc, mouseX, mouseY, partial);
        if (!visible) return;
        WornPathArt.settingsIcon(Canvas.iconCell(icon), x + 2, y + 2);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Whether the pointer is over it, for its hover. */
    boolean under(int mouseX, int mouseY) {
        return visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
