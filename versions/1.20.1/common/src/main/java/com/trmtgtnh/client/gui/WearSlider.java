package com.trmtgtnh.client.gui;

import java.util.function.IntConsumer;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A slider for a counted setting on the mod's own settings screen, drawn as a strip of lawn that the handle wears to
 * dirt as it moves: path behind it, grass ahead.
 *
 * <p>
 * Carried from 26.3 in 0.9.228. Which settings slide is {@link ConfigLayout#slides}, as on Cloth's screen (spec
 * CF59) - the two counted settings, {@code wearGradations} and {@code wearRotations}. It moves in whole steps between
 * the setting's own bounds and says the number it stands at, as Cloth's integer slider does. Only the picture is the
 * mod's own: the handle is the game's - at 1.20.1 a nine-sliced piece of {@code textures/gui/slider.png}, as the game's
 * own slider draws it, where the newer games have a sprite of its own.
 */
final class WearSlider extends AbstractSliderButton {

    private static final ResourceLocation SLIDER = new ResourceLocation("textures/gui/slider.png");

    private final int min;
    private final int max;
    private final IntConsumer moved;

    WearSlider(int width, int height, int min, int max, int at, IntConsumer moved) {
        super(0, 0, width, height, Component.empty(), fraction(at, min, max));
        this.min = min;
        this.max = max;
        this.moved = moved;
        updateMessage();
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

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(String.valueOf(current())));
    }

    @Override
    protected void applyValue() {
        // Snapped to its step, so the handle stands where the number says rather than between two of them.
        value = fraction(current(), min, max);
        moved.accept(current());
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        WornPathArt.wornBar(graphics, getX(), getY(), getWidth(), getHeight(), value, com.trmtgtnh.client.settings.Slider.LEAST_STAGES);
        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF000000);
        // As the game's own slider draws its handle: blended, the lit row of the slider picture when hovered or focused.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blitNineSliced(SLIDER, getX() + (int) (value * (getWidth() - 8)), getY(), 8, getHeight(), 20, 4, 200, 20,
            0, isHoveredOrFocused() ? 60 : 40);
        renderScrollingString(graphics, Minecraft.getInstance().font, 2, 0xFFFFFF | Mth.ceil(alpha * 255.0F) << 24);
    }
}
