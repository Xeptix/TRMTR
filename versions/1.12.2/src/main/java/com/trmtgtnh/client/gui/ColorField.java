package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.material.MapColor;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.trmtgtnh.client.journeymap.JourneyMapColors;
import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.ColorMath;
import com.trmtgtnh.client.settings.ColorPicker;
import com.trmtgtnh.client.settings.HoverText;
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
 * What it decides is plain - a test makes one and types into it - and {@link Screen} is the game's half: the parent
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

    /**
     * The preview's words as the small font (0.75) writes them in a place {@code width} pixels wide: wrapped, at most
     * {@link ColorPicker#PREVIEW_WORDS_LINES} lines, the last trimmed where more would follow.
     */
    static List<String> previewLines(Canvas canvas, String words, int width) {
        int room = (int) (width / 0.75F);
        List<String> wrapped = HoverText.wrap(canvas, Collections.singletonList(words), room);
        if (wrapped.size() <= ColorPicker.PREVIEW_WORDS_LINES) return wrapped;
        List<String> out = new ArrayList<String>(wrapped.subList(0, ColorPicker.PREVIEW_WORDS_LINES));
        int last = out.size() - 1;
        out.set(last, canvas.trim(out.get(last) + " " + wrapped.get(last + 1), room));
        return out;
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
     * The strip's colors (spec M10.3, M10.6): a run of grass worn to earth, each cell as a map draws a square that
     * worn - its material, its darkening, then pulled toward the path color at the highlight set - through the very
     * calls the game makes ({@code JourneyMapColors.mapColorFor}, where this edition draws the color). At a
     * highlight of nought the color is drawn nowhere in the game, so the strip is drawn at full, where the color
     * shows, rather than showing nothing.
     */
    int[] preview() {
        int[] out = new int[PREVIEW_CELLS];
        float shown = strength > 0f ? strength : 1f;
        for (int i = 0; i < PREVIEW_CELLS; i++) {
            float worn = i / (float) (PREVIEW_CELLS - 1);
            // The material, then how used it is, as JourneyMapColors.mapColorFor lays them; then the pull, at the
            // highlight this screen holds rather than the one the game was last given.
            int ground = JourneyMapColors
                .mapColorFor(worn, ColorMath.mix(MapColor.GRASS.colorValue, MapColor.DIRT.colorValue, worn));
            out[i] = 0xFF000000 | ColorMath.highlight(ground, picker.rgb(), shown, worn);
        }
        return out;
    }

    /**
     * Opens the picker over {@code parent} on {@code hex}; {@code apply} is told the color chosen. The strip is
     * drawn at the highlight the settings screen holds, edited or not.
     */
    public static void open(GuiScreen parent, String hex, Consumer<String> apply) {
        net.minecraft.client.Minecraft.getMinecraft()
            .displayGuiScreen(new Screen(parent, new ColorField(hex, highlightOf(parent), apply)));
    }

    /** The picker as the rig photographs it (spec Part 4, {@code settings-color}): nothing it chooses goes anywhere. */
    public static GuiScreen forPhotograph(GuiScreen parent, String hex) {
        return new Screen(parent, new ColorField(hex, highlightOf(parent), chosen -> {}));
    }

    private static float highlightOf(GuiScreen parent) {
        if (parent instanceof WornPathScreen) {
            SettingsModel model = ((WornPathScreen) parent).model;
            SettingsModel.Setting setting = model.setting(HIGHLIGHT);
            if (setting != null) return (float) ConfigWords.number(model.value(setting), 0D);
        }
        return TrmtConfig.desirePathHighlight;
    }

    /** The picker's screen: the settings behind it, the box over them. */
    static final class Screen extends GuiScreen {

        private final GuiScreen parent;

        private final ColorField field;

        private GuiTextField hex;

        private SettingsCanvas canvas;

        Screen(GuiScreen parent, ColorField field) {
            this.parent = parent;
            this.field = field;
        }

        @Override
        public void initGui() {
            buttonList.clear();
            Keyboard.enableRepeatEvents(true);
            canvas = new SettingsCanvas(mc, width, height);
            if (parent != null) parent.setWorldAndResolution(mc, width, height);
            ColorPicker picker = field.picker();
            picker.layout(new Rect(0, 0, width, height));
            Rect at = picker.hexField();
            hex = new GuiTextField(0, fontRenderer, at.x0 + 3, at.y0 + 2, at.width() - 6, 10);
            hex.setEnableBackgroundDrawing(false);
            hex.setMaxStringLength(7);
            hex.setText(picker.hexText());
            hex.setTextColor(Canvas.GREY & 0xFFFFFF);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partial) {
            if (parent != null) parent.drawScreen(-1, -1, partial);
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
            drawPreviewWords(canvas, picker.previewWords());
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
            hex.drawTextBox();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }

        /**
         * What the strip shows, wrapped in the small font to the place the picker keeps for it under the strip
         * (0.9.228): written as one trimmed line it ran past the box and into the hex field and its label.
         */
        static void drawPreviewWords(Canvas canvas, Rect at) {
            List<String> lines = previewLines(canvas, canvas.words(SettingsKeys.COLOR_PREVIEW), at.width());
            for (int i = 0; i < lines.size(); i++) {
                canvas.text(lines.get(i), at.x0, at.y0 + i * ColorPicker.SMALL_LINE, Canvas.GREY, 0.75F);
            }
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
            ColorPicker picker = field.picker();
            hex.mouseClicked(mouseX, mouseY, button);
            if (picker.hexField()
                .contains(mouseX, mouseY)) return;
            int did = picker.press(mouseX, mouseY);
            if (did == ColorPicker.APPLY) {
                if (field.apply()) back();
            } else if (did == ColorPicker.CANCEL) {
                back();
            } else if (did == ColorPicker.TOOK) {
                hex.setText(picker.hexText());
            }
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int button, long held) {
            ColorPicker picker = field.picker();
            if (!picker.dragging()) return;
            picker.drag(mouseX, mouseY);
            hex.setText(picker.hexText());
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) field.picker()
                .release();
        }

        @Override
        protected void keyTyped(char typed, int key) {
            if (key == Keyboard.KEY_ESCAPE) {
                back();
                return;
            }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                if (field.apply()) back();
                return;
            }
            if (!hex.isFocused()) hex.setFocused(true);
            String before = hex.getText();
            hex.textboxKeyTyped(typed, key);
            if (!hex.getText()
                .equals(before)) field.typed(hex.getText());
        }

        @Override
        public void updateScreen() {
            hex.updateCursorCounter();
        }

        @Override
        public void onGuiClosed() {
            Keyboard.enableRepeatEvents(false);
        }

        private void back() {
            mc.displayGuiScreen(parent);
        }
    }
}
