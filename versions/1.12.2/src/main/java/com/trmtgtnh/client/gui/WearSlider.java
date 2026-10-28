package com.trmtgtnh.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/**
 * A slider for a counted setting on the mod's own settings screen, drawn as a strip of lawn that the handle wears to
 * dirt as it moves: path behind it, grass ahead (0.9.228, as 26.3's).
 *
 * <p>
 * Which settings slide is {@link ConfigLayout#slides} - {@code wearGradations} and {@code wearRotations}, as on Forge's
 * screen here (spec CF59). It moves in whole steps between the setting's own bounds and says the number it stands at.
 * Only the picture is the mod's own: the handle is the game's, drawn as its own Options sliders draw theirs, and the
 * drag is theirs too - followed in {@link #mouseDragged}, which the game calls each frame the button is drawn.
 */
final class WearSlider extends GuiButton {

    /** Told the whole number each time the handle comes to rest on a new one. */
    interface Moved {

        void to(int value);
    }

    private final int min;
    private final int max;
    private final Moved moved;

    /** Where the handle stands along the bar, nought to one. */
    double value;

    private boolean dragging;

    WearSlider(int id, int width, int height, int min, int max, int at, Moved moved) {
        super(id, 0, 0, width, height, "");
        this.min = min;
        this.max = max;
        this.moved = moved;
        this.value = fraction(at, min, max);
        displayString = String.valueOf(current());
    }

    /** Where a number stands along the bar, nought to one. */
    static double fraction(int at, int min, int max) {
        if (max <= min) return 0D;
        return (ConfigWords.clamp(at, min, max) - min) / (double) (max - min);
    }

    /** The whole number the handle stands at. */
    int current() {
        return min + (int) Math.round(value * (max - min));
    }

    /** The handle put where the pointer is, snapped to its step, and the number said. */
    private void follow(int mouseX) {
        double placed = (mouseX - (x + 4)) / (double) (width - 8);
        value = placed < 0D ? 0D : placed > 1D ? 1D : placed;
        int now = current();
        // Snapped, so the handle stands where the number says rather than between two of them.
        value = fraction(now, min, max);
        if (!String.valueOf(now)
            .equals(displayString)) {
            displayString = String.valueOf(now);
            moved.to(now);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!super.mousePressed(mc, mouseX, mouseY)) return false;
        follow(mouseX);
        dragging = true;
        return true;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        dragging = false;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (dragging) follow(mouseX);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        mouseDragged(mc, mouseX, mouseY);
        WornPathArt.wornBar(x, y, width, height, value, com.trmtgtnh.client.settings.Slider.LEAST_STAGES);
        WornPathArt.outline(x, y, width, height, hovered ? 0xFFFFFFFF : 0xFF000000);
        mc.getTextureManager()
            .bindTexture(BUTTON_TEXTURES);
        GlStateManager.color(1F, 1F, 1F, 1F);
        int handle = x + (int) (value * (width - 8));
        drawTexturedModalRect(handle, y, 0, 66, 4, 20);
        drawTexturedModalRect(handle + 4, y, 196, 66, 4, 20);
        drawCenteredString(
            mc.fontRenderer,
            displayString,
            x + width / 2,
            y + (height - 8) / 2,
            hovered ? 0xFFFFA0 : 0xE0E0E0);
    }
}
