package com.trmtgtnh.client.gui;

import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.MapColor;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.journeymap.JourneyMapColors;
import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.ColorMath;
import com.trmtgtnh.client.settings.ColorPicker;
import com.trmtgtnh.client.settings.SettingsGeometry.Rect;
import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The path color's picker, over the settings screen (0.9.228, spec M10, M17; Xep, from his GT New Horizons notes:
 * "neither is the manual color codes"). The color row is a swatch and its hex, with nothing to type into; the swatch
 * opens this, and this is the editor: the shared {@link ColorPicker}'s square, hue bar and eight ready colors, a strip
 * of worn ground drawn in the color as a map draws it, and the hex as a second way in, refused with its reason while it
 * does not read - which holds Apply. Apply hands the row the color as one edit; Cancel and Escape leave it as it was.
 *
 * <p>
 * What it decides is plain - a test makes one and types into it - and {@link Picker} is the game's half: the parent
 * drawn behind it, the hex field, the pointer and the keys.
 */
public final class ColorField {

    /** The setting the strip is drawn at, as the game draws the color (spec M10.3). */
    static final String HIGHLIGHT = TrmtConfig.CATEGORY_CLIENT + ".desirePathHighlight";

    /** The strip's cells, worn from nothing at its left to fully at its right. */
    static final int PREVIEW_CELLS = 16;

    private final ColorPicker picker;

    private final Consumer<String> apply;

    private final float strength;

    ColorField(String hex, float strength, Consumer<String> apply) {
        ColorMath.Parsed parsed = ColorMath.parse(hex);
        this.picker = new ColorPicker(parsed.reads() ? parsed.rgb : DEFAULT);
        this.strength = strength;
        this.apply = apply;
    }

    /** The shipped color, which a value that does not read is shown as, as the game draws it. */
    static final int DEFAULT = 0xAA44CC;

    ColorPicker picker() {
        return picker;
    }

    /** The hex field's text as typed: a reading moves the ring and the marker, a refusal holds Apply (spec M10.2). */
    void typed(String text) {
        picker.typed(text);
    }

    /**
     * Apply: the color written into the row, upper case with its {@code #}, as one edit (spec M10.5). Nothing while the
     * typed hex is refused.
     *
     * @return whether it was applied
     */
    boolean apply() {
        if (picker.applyHeld()) return false;
        apply.accept(ColorMath.hex(picker.rgb()));
        return true;
    }

    /**
     * The strip's colors (spec M10.3, M10.6): a run of grass worn to earth, each cell as a map draws a square that worn
     * - its material, its darkening, then pulled toward the path color at the highlight set - through the very calls
     * the game makes ({@code JourneyMapColors.darkenBy}, the map at this version that is handed a color per square). At
     * a highlight of nought the color is drawn nowhere in the game, so the strip is drawn at full, where the color
     * shows, rather than showing nothing.
     */
    int[] preview() {
        int[] out = new int[PREVIEW_CELLS];
        float shown = strength > 0f ? strength : 1f;
        for (int i = 0; i < PREVIEW_CELLS; i++) {
            float worn = i / (float) (PREVIEW_CELLS - 1);
            // The material, then how used it is, as the map lays them; then the pull, at the highlight this screen holds
            // rather than the one the game was last given.
            int ground = JourneyMapColors
                .darkenBy(ColorMath.mix(MapColor.GRASS.col, MapColor.DIRT.col, worn), worn);
            out[i] = 0xFF000000 | ColorMath.highlight(ground, picker.rgb(), shown, worn);
        }
        return out;
    }

    /**
     * Opens the picker over {@code parent} on {@code hex}; {@code apply} is told the color chosen. The strip is drawn
     * at the highlight the settings screen holds, edited or not.
     */
    public static void open(Screen parent, String hex, Consumer<String> apply) {
        Minecraft.getInstance()
            .setScreen(new Picker(parent, new ColorField(hex, highlightOf(parent), apply)));
    }

    /** The picker as the rig photographs it (spec Part 4, {@code settings-color}): nothing it chooses goes anywhere. */
    public static Screen forPhotograph(Screen parent, String hex) {
        return new Picker(parent, new ColorField(hex, highlightOf(parent), chosen -> {}));
    }

    private static float highlightOf(Screen parent) {
        if (parent instanceof WornPathScreen) {
            SettingsModel model = ((WornPathScreen) parent).model;
            SettingsModel.Setting setting = model.setting(HIGHLIGHT);
            if (setting != null) return (float) ConfigWords.number(model.value(setting), 0D);
        }
        return TrmtConfig.desirePathHighlight;
    }

    /** The picker's screen: the settings behind it, the box over them. */
    static final class Picker extends Screen {

        private final Screen parent;

        private final ColorField field;

        private EditBox hex;

        private SettingsCanvas canvas;

        Picker(Screen parent, ColorField field) {
            super(Component.literal(Trmt.NAME));
            this.parent = parent;
            this.field = field;
        }

        @Override
        protected void init() {
            clearWidgets();
            canvas = new SettingsCanvas(minecraft, width, height);
            if (parent != null) parent.init(minecraft, width, height);
            ColorPicker picker = field.picker();
            picker.layout(new Rect(0, 0, width, height));
            Rect at = picker.hexField();
            hex = new EditBox(font, at.x0 + 3, at.y0 + 2, at.width() - 6, 10, Component.literal(""));
            hex.setBordered(false);
            hex.setMaxLength(7);
            hex.setValue(picker.hexText());
            hex.setTextColor(Canvas.GREY & 0xFFFFFF);
        }

        /**
         * From 1.20.2 {@code Screen.render} draws the background itself, once a frame, and 1.21 blurs it. Answered here
         * with nothing, so the dirt this screen draws first is not drawn over.
         */
        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {}

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            if (parent != null) parent.render(graphics, -1, -1, partial);
            canvas.frame(graphics);
            WornPathArt.plainState();
            // The settings dimmed behind the box. Opaque enough for the alpha test (spec 1.3).
            canvas.fill(0, 0, width, height, 0xA0000000);
            ColorPicker picker = field.picker();
            picker.draw(canvas, mouseX, mouseY);
            Rect strip = picker.preview();
            int[] cells = field.preview();
            int cell = strip.width() / cells.length;
            for (int i = 0; i < cells.length; i++) {
                int x0 = strip.x0 + i * cell;
                int x1 = i == cells.length - 1 ? strip.x1 : x0 + cell;
                canvas.fill(x0, strip.y0, x1, strip.y1, cells[i]);
            }
            Canvas.frame(canvas, strip.x0 - 1, strip.y0 - 1, strip.x1 + 1, strip.y1 + 1, Canvas.RULE);
            canvas.text(
                canvas.trim(canvas.words(SettingsKeys.COLOR_PREVIEW), (int) ((strip.width() + 10) / 0.75F)),
                strip.x0,
                strip.y1 + 2,
                Canvas.GREY,
                0.75F);
            Rect at = picker.hexField();
            canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.TAB_OPEN);
            Canvas.frame(
                canvas,
                at.x0,
                at.y0,
                at.x1,
                at.y1,
                hex.isFocused() ? Canvas.GOLD : picker.refusal() != null ? Canvas.RED : Canvas.RULE);
            hex.setTextColor((picker.refusal() != null ? Canvas.RED : Canvas.GREY) & 0xFFFFFF);
            hex.render(graphics, mouseX, mouseY, partial);
            canvas.drewText();
            graphics.setColor(1F, 1F, 1F, 1F);
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            int mouseX = (int) x;
            int mouseY = (int) y;
            ColorPicker picker = field.picker();
            hex.mouseClicked(x, y, button);
            if (picker.hexField()
                .contains(mouseX, mouseY)) return true;
            int did = picker.press(mouseX, mouseY);
            if (did == ColorPicker.APPLY) {
                if (field.apply()) back();
            } else if (did == ColorPicker.CANCEL) {
                back();
            } else if (did == ColorPicker.TOOK) {
                hex.setValue(picker.hexText());
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            ColorPicker picker = field.picker();
            if (!picker.dragging()) return false;
            picker.drag((int) x, (int) y);
            hex.setValue(picker.hexText());
            return true;
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            if (button == 0) field.picker()
                .release();
            return true;
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                back();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (field.apply()) back();
                return true;
            }
            if (!hex.isFocused()) hex.setFocused(true);
            String before = hex.getValue();
            hex.keyPressed(key, scan, modifiers);
            if (!hex.getValue()
                .equals(before)) field.typed(hex.getValue());
            return true;
        }

        @Override
        public boolean charTyped(char typed, int modifiers) {
            if (!hex.isFocused()) hex.setFocused(true);
            String before = hex.getValue();
            hex.charTyped(typed, modifiers);
            if (!hex.getValue()
                .equals(before)) field.typed(hex.getValue());
            return true;
        }

        // No tick: from 1.20.2 a box's cursor blinks by the clock, and EditBox has no tick of its own to be asked.

        @Override
        public void removed() {
        }

        @Override
        public boolean isPauseScreen() {
            return parent == null || parent.isPauseScreen();
        }

        private void back() {
            minecraft.setScreen(parent);
        }
    }
}
