package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.HoverText;

/**
 * A question the settings screens ask with more than two answers (0.9.228): what to do about a restart or a resource
 * reload after Save (spec M13.3), and whether to save before switching to the other screen (M14.2) - which the game's
 * own yes-or-no screen cannot ask, having two buttons. Drawn on the settings' dark dirt: its lines in a box, the first
 * gold, and its answers as the game's own buttons in a row under them. Escape is the last answer, which is always the
 * one that changes nothing.
 */
class SettingsQuestion extends GuiScreen {

    /** One answer: its words, and what pressing it does. */
    static final class Answer {

        final String key;

        final Runnable then;

        Answer(String key, Runnable then) {
            this.key = key;
            this.then = then;
        }
    }

    private static final int BUTTON_WIDTH = 120;

    private static final int GAP = 4;

    private final List<String> keys;

    private final List<String> headlines;

    private final List<Answer> answers;

    private SettingsCanvas canvas;

    /**
     * @param lineKeys  the words, a paragraph a key
     * @param headlines those of them drawn gold: each question's own line, its note under it in the body color
     * @param answers   the buttons, left to right; Escape presses the last
     */
    SettingsQuestion(List<String> lineKeys, List<String> headlines, List<Answer> answers) {
        this.keys = new ArrayList<String>(lineKeys);
        this.headlines = new ArrayList<String>(headlines);
        this.answers = new ArrayList<Answer>(answers);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        canvas = new SettingsCanvas(mc, width, height);
        int count = answers.size();
        int each = Math.min(BUTTON_WIDTH, (width - 16 - GAP * (count - 1)) / Math.max(1, count));
        int row = each * count + GAP * (count - 1);
        int x = (width - row) / 2;
        for (int i = 0; i < count; i++) {
            buttonList
                .add(new GuiButton(i, x + i * (each + GAP), height / 2 + 30, each, 20, words(answers.get(i).key)));
        }
    }

    private static String words(String key) {
        return I18n.format(key);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        WornPathArt.plainState();
        canvas.background(0, 0, width, height);
        int boxWidth = Math.min(width - 16, 320);
        List<String> lines = new ArrayList<String>();
        List<Boolean> gold = new ArrayList<Boolean>();
        for (String key : keys) {
            List<String> wrapped = HoverText
                .wrap(canvas, java.util.Collections.singletonList(words(key)), boxWidth - 16);
            for (String line : wrapped) {
                lines.add(line);
                gold.add(headlines.contains(key));
            }
        }
        int boxHeight = lines.size() * 11 + 16;
        int x0 = (width - boxWidth) / 2;
        int y1 = height / 2 + 22;
        int y0 = y1 - boxHeight;
        canvas.panel(x0, y0, x0 + boxWidth, y1);
        Canvas.frame(canvas, x0, y0, x0 + boxWidth, y1, Canvas.GOLD);
        int y = y0 + 8;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            canvas.text(
                line,
                (width - canvas.width(line)) / 2,
                y,
                gold.get(i)
                    .booleanValue() ? Canvas.GOLD : Canvas.BODY,
                true);
            y += 11;
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
        super.drawScreen(mouseX, mouseY, partial);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0 && button.id < answers.size()) answers.get(button.id).then.run();
    }

    @Override
    protected void keyTyped(char typed, int key) throws java.io.IOException {
        if (key == Keyboard.KEY_ESCAPE && !answers.isEmpty()) {
            answers.get(answers.size() - 1).then.run();
        }
    }
}
