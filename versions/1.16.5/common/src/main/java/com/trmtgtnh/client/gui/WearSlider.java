package com.trmtgtnh.client.gui;

import java.util.function.IntConsumer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.TextComponent;

/**
 * A slider for a counted setting on the mod's own settings screen, drawn as a strip of lawn that the handle wears to
 * dirt as it moves: path behind it, grass ahead.
 *
 * <p>
 * Which settings slide is {@link ConfigLayout#slides}, as on Cloth's screen (spec CF59) - the two counted settings,
 * {@code wearGradations} and {@code wearRotations}. It moves in whole steps between the setting's own bounds and
 * says the number it stands at, as Cloth's integer slider does. Only the picture is the mod's own: the handle is the
 * game's, cut from its widgets sheet exactly where 1.16.5's own slider cuts it ({@code AbstractSliderButton.renderBg}).
 */
final class WearSlider extends AbstractSliderButton {

    private final int min;
    private final int max;
    private final IntConsumer moved;

    WearSlider(int width, int height, int min, int max, int at, IntConsumer moved) {
        super(0, 0, width, height, TextComponent.EMPTY, fraction(at, min, max));
        this.min = min;
        this.max = max;
        this.moved = moved;
        updateMessage();
    }

    /** Where a number stands along the bar, nought to one. */
    static double fraction(int at, int min, int max) {
        if (max <= min) return 0D;
        double placed = (ConfigWords.clamp(at, min, max) - min) / (double) (max - min);
        return placed;
    }

    /** The whole number the handle stands at. */
    int current() {
        return min + (int) Math.round(value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(new TextComponent(String.valueOf(current())));
    }

    @Override
    protected void applyValue() {
        // Snapped to its step, so the handle stands where the number says rather than between two of them.
        value = fraction(current(), min, max);
        moved.accept(current());
    }

    @Override
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
        WornPathArt.wornBar(pose, x, y, getWidth(), getHeight(), value, com.trmtgtnh.client.settings.Slider.LEAST_STAGES);
        WornPathArt.outline(pose, x, y, getWidth(), getHeight(), isHovered() ? 0xFFFFFFFF : 0xFF000000);
        // The game's own handle, its two halves as the game's slider draws them: four pixels from the left of the
        // widgets sheet's button face and four from its right, twenty high, the lit face while hovered.
        Minecraft minecraft = Minecraft.getInstance();
        WornPathArt.bind(WIDGETS_LOCATION, 0xFFFFFFFF);
        int face = (isHovered() ? 2 : 1) * 20;
        int handle = x + (int) (value * (getWidth() - 8));
        blit(pose, handle, y, 0, 46 + face, 4, 20);
        blit(pose, handle + 4, y, 196, 46 + face, 4, 20);
        drawCenteredString(pose, minecraft.font, getMessage(), x + getWidth() / 2, y + (getHeight() - 8) / 2,
            active ? 0xFFFFFF : 0xA0A0A0);
    }
}
