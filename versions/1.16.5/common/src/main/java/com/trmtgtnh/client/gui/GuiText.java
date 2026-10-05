package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/**
 * A long string cut into lines that fit, which the font used to answer for itself.
 *
 * <p>
 * <strong>Written rather than carried, because the method it replaces is gone and its replacement
 * does not answer in strings.</strong> Both older editions ask
 * {@code fontRenderer.listFormattedStringToWidth(text, width)} and get a list of strings back. At
 * this version a font splits text through a {@code StringSplitter} into a list of
 * {@code FormattedText} - which is the better answer for coloured text and the wrong shape for every
 * caller in this mod, all of which hand it a plain translated sentence and draw the lines centred.
 *
 * <p>
 * So this is the one place that conversion happens. Four screens wrap text and all four wanted the
 * same thing; four copies of two lines would be four chances for one of them to index a list that
 * might be shorter than it expects, which is the other half of what this fixes - a caller asking for
 * line two of a sentence that only needed one line used to be an exception, and is now an empty
 * string.
 */
public final class GuiText {

    private GuiText() {}

    /** Every line of this text at this width, or an empty list for nothing to say. */
    public static List<String> wrap(Font font, String text, int width) {
        if (font == null || text == null || text.isEmpty() || width <= 0) {
            return Collections.<String>emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (FormattedText line : font.getSplitter()
            .splitLines(text, width, Style.EMPTY)) {
            out.add(line.getString());
        }
        return out;
    }

    /**
     * One line of wrapped text, or an empty string where there is no such line.
     *
     * <p>
     * The shape every caller actually wants: a screen draws line nought and line one at two fixed
     * heights, and whether there is a line one depends on the language. An empty string draws as
     * nothing, which is what a screen with a short sentence should show.
     */
    public static String line(List<String> lines, int at) {
        return lines == null || at < 0 || at >= lines.size() ? "" : lines.get(at);
    }

    /**
     * Lines of plain text as the things a tooltip is drawn from.
     *
     * <p>
     * Both older editions hand {@code drawHoveringText} a list of strings. At this version a tooltip
     * is drawn from components, and the one method that takes anything else wants a list of
     * {@code FormattedCharSequence} - so the strings are wrapped and the font is left to do what it
     * has always done with them.
     *
     * <p>
     * <strong>The section signs survive, which is the point.</strong> Every tooltip in this mod
     * carries its own colour as a {@code §7} at the head of each line, which is how both older
     * editions colour them. A component built from literal text keeps those and the font still
     * reads them, so nothing about the colouring has to be rewritten.
     */
    public static List<net.minecraft.network.chat.Component> components(List<String> lines) {
        List<net.minecraft.network.chat.Component> out =
            new ArrayList<net.minecraft.network.chat.Component>();
        if (lines == null) return out;
        for (String one : lines) {
            out.add(new net.minecraft.network.chat.TextComponent(one == null ? "" : one));
        }
        return out;
    }
}
