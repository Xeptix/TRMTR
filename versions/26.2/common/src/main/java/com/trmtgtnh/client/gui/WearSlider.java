package com.trmtgtnh.client.gui;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A slider for a counted setting on the mod's own settings screen, drawn as a strip of lawn that the handle wears to
 * dirt as it moves: path behind it, grass ahead.
 *
 * <p>
 * Which settings slide is {@link ConfigLayout#slides}, as on Cloth's screen (spec CF59) - the two counted settings,
 * {@code wearGradations} and {@code wearRotations}. It moves in whole steps between the setting's own bounds and
 * says the number it stands at, as Cloth's integer slider does. Only the picture is the mod's own: the handle is the
 * game's.
 */
final class WearSlider extends AbstractSliderButton {

    private static final Identifier HANDLE = Identifier.withDefaultNamespace("widget/slider_handle");

    private static final Identifier HANDLE_LIT = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");

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
        double placed = (ConfigWords.clamp(at, min, max) - min) / (double) (max - min);
        return placed;
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
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        WornPathArt.wornBar(graphics, getX(), getY(), getWidth(), getHeight(), value, com.trmtgtnh.client.settings.Slider.LEAST_STAGES);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF000000);
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            isHoveredOrFocused() ? HANDLE_LIT : HANDLE,
            getX() + (int) (value * (getWidth() - 8)),
            getY(),
            8,
            getHeight());
        extractScrollingStringOverContents(
            graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE),
            getMessage(),
            2);
        handleCursor(graphics);
    }
}
