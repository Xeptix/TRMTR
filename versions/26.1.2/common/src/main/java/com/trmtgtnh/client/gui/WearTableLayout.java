package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Where the Wear Table's pieces stand on a screen of a given size, worked out apart from the game so a test and the
 * harness can hold it (1.0.0, from the screens part's photographs of 2026-11-19: the bar's sentence cut short with an
 * ellipsis, and the column key and the green/red line squeezed between Back and Edit down to the screen's bottom edge,
 * below the panel, where the chat and the hotbar draw).
 *
 * <p>
 * The legend has a band of its own at the foot of the panel, under a rule, and every sentence in it is wrapped to the
 * panel's width rather than cut: the table's body gives up the height the band needs and scrolls for the rest. The
 * buttons have their row below the panel to themselves. Nothing here touches the game, so every edition carries this
 * file as it is.
 */
final class WearTableLayout {

    static final int PANEL_MAX = 420;
    static final int PANEL_TOP = 18;
    /** The header band: title, the two column keys and the quarter headings, down to the rule under them. */
    static final int HEADER_H = 45;
    /** One legend line: the font's nine pixels and one between lines. */
    static final int LINE_H = 10;
    static final int ROW_H = 26;
    static final int BUTTON_H = 20;

    /**
     * The smallest screen the layout is held at: the game's floor at automatic GUI scale, which picks the largest
     * scale that leaves at least 320 by 240. Below it the table's own columns are already wider than the panel.
     */
    static final int MIN_WIDTH = 320;
    static final int MIN_HEIGHT = 240;

    final int width;
    final int height;
    final int panelWidth;
    final int panelLeft;
    final int panelTop;
    final int panelBottom;
    final int bodyTop;
    final int bodyBottom;
    /** The one-pixel rule between the body and the legend band. */
    final int ruleY;
    final int legendLeft;
    final int legendRoom;
    final int legendTop;
    final int buttonTop;
    /** The legend, wrapped, in the order it is drawn. */
    final List<String> lines;

    private WearTableLayout(int width, int height, List<String> lines) {
        this.width = width;
        this.height = height;
        this.panelWidth = panelWidth(width);
        this.panelLeft = width / 2 - panelWidth / 2;
        this.panelTop = PANEL_TOP;
        this.buttonTop = buttonTop(height);
        // Five pixels above the buttons, border included: the strip the bar's sentence used to be squeezed into.
        this.panelBottom = buttonTop - 6;
        this.bodyTop = panelTop + HEADER_H;
        this.legendLeft = panelLeft + 6;
        this.legendRoom = legendRoom(width);
        this.lines = Collections.unmodifiableList(lines);
        this.legendTop = panelBottom - 2 - lines.size() * LINE_H;
        this.ruleY = legendTop - 4;
        this.bodyBottom = Math.max(bodyTop, ruleY - 2);
    }

    static int panelWidth(int width) {
        return Math.min(width - 8, PANEL_MAX);
    }

    static int panelLeft(int width) {
        return width / 2 - panelWidth(width) / 2;
    }

    static int buttonTop(int height) {
        return height - 28;
    }

    static int legendRoom(int width) {
        return panelWidth(width) - 12;
    }

    /** The layout of a screen {@code width} by {@code height} showing these sentences, each wrapped to the panel. */
    static WearTableLayout of(int width, int height, List<String> sentences, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<String>();
        int room = legendRoom(width);
        for (String sentence : sentences) {
            lines.addAll(wrap(sentence, room, measure));
        }
        return new WearTableLayout(width, height, lines);
    }

    /** The y of legend line {@code i}. */
    int lineY(int i) {
        return legendTop + i * LINE_H;
    }

    /**
     * A sentence broken at its spaces into lines no wider than {@code room}. A word wider than the room on its own is
     * broken where it reaches the edge, so nothing is ever cut or drawn past it.
     */
    static List<String> wrap(String text, int room, ToIntFunction<String> measure) {
        List<String> out = new ArrayList<String>();
        String line = "";
        for (String word : text.trim()
            .split(" +")) {
            if (word.isEmpty()) continue;
            String joined = line.isEmpty() ? word : line + " " + word;
            if (measure.applyAsInt(joined) <= room) {
                line = joined;
                continue;
            }
            if (!line.isEmpty()) out.add(line);
            line = word;
            while (measure.applyAsInt(line) > room && line.length() > 1) {
                int cut = line.length() - 1;
                while (cut > 1 && measure.applyAsInt(line.substring(0, cut)) > room) cut--;
                out.add(line.substring(0, cut));
                line = line.substring(cut);
            }
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    /**
     * What does not fit, in words, or null when every sentence is shown whole and inside the panel: each line within
     * the room, the lines together the sentences word for word, the band below the rule and the body's last pixel, the
     * body at least one row high, the panel above the buttons and the buttons above the screen's edge.
     */
    String misfit(List<String> sentences, ToIntFunction<String> measure) {
        StringBuilder words = new StringBuilder();
        for (String sentence : sentences) {
            words.append(' ')
                .append(sentence.trim());
        }
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int w = measure.applyAsInt(line);
            if (w > legendRoom) return "legend line " + (i + 1) + " is " + w + " wide in " + legendRoom + ": " + line;
            shown.append(' ')
                .append(line);
        }
        if (!squeezed(shown.toString()).equals(squeezed(words.toString()))) {
            return "the legend's lines are not its sentences whole";
        }
        if (legendLeft < panelLeft || legendLeft + legendRoom > panelLeft + panelWidth) {
            return "the legend runs outside the panel's sides";
        }
        if (ruleY <= bodyBottom || legendTop <= ruleY) return "the legend band overlaps the table's body";
        if (bodyBottom - bodyTop < ROW_H) {
            return "the table's body is " + (bodyBottom - bodyTop) + " high, less than one row";
        }
        if (lineY(lines.size()) > panelBottom - 2) return "the legend runs below the panel";
        if (panelBottom + 1 >= buttonTop) return "the panel reaches the buttons";
        if (buttonTop + BUTTON_H > height) return "the buttons run off the screen";
        return null;
    }

    private static String squeezed(String text) {
        return text.trim()
            .replaceAll(" +", " ");
    }
}
