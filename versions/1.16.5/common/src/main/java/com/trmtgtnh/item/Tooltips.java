package com.trmtgtnh.item;

import java.util.AbstractList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;

/**
 * Tooltip lines that stop at the edge of a sensible column instead of at the edge of the monitor.
 *
 * <p>
 * Nothing in this game wraps an item tooltip. A line goes out as far as it is long, and on a wide
 * screen a sentence of any substance draws a bar of text from one side to the other - which is
 * unreadable in a way that gets worse the better the monitor. Every mod that writes more than a few
 * words has to answer this for itself.
 *
 * <p>
 * Answered once, at the list rather than at the line. Every tooltip in this mod is built by handing
 * lines to the list the game passed in, and there are forty-odd of those calls across eight items;
 * wrapping each of them would be forty edits now and one more for every line added afterwards, with
 * the one that got forgotten being the one nobody notices until it is on somebody else's
 * screenshot. Substituting the list instead is one line per item and covers everything they will
 * ever add.
 *
 * <p>
 * The width is measured rather than assumed, because a tooltip is drawn in interface pixels and
 * there are four times as many of those at one interface scale as at another. Half the screen is the
 * ceiling anybody would want; the fixed cap under it is what stops a very wide window turning the
 * wrap back into the thing it was fixing.
 *
 * <h2>What this version changed, and what it did not</h2>
 *
 * <p>
 * <strong>A tooltip is a list of components here, not of strings</strong> - and this hands back a
 * list of strings anyway, on purpose. Both older editions write their tooltips as text with
 * formatting codes in it, forty-odd calls of {@code add(TextFormatting.GRAY + something)}, and
 * nothing in any of them wants a styled tree. Converting those call sites would have been the forty
 * edits this class exists to avoid, and would have made three editions' tooltip code stop looking
 * alike for no gain. So the view converts: a string added here becomes a plain text component in the
 * list the game passed in, and a line read back comes out as the string it was written as.
 *
 * <p>
 * The splitting is the game's own line splitter rather than the character measuring the older
 * editions do, because this version has one and it is better at it.
 *
 * <p>
 * Client-only, as the method that builds a tooltip is - a dedicated server never builds one of these
 * and has no font to measure with. There is no annotation for that in a module both loaders share;
 * what keeps it off a server is that nothing on a server calls it.
 */
public final class Tooltips {

    /** The widest a line is allowed to be, in interface pixels. About four dozen characters. */
    private static final int WIDEST = 260;

    /** And the narrowest, so a tiny window gives short lines rather than one letter per line. */
    private static final int NARROWEST = 120;

    private Tooltips() {}

    /**
     * The game's tooltip, as a list of strings that wraps whatever is added to it.
     *
     * <p>
     * A caller writes one assignment at the top of the method that builds its tooltip and then adds
     * strings exactly as the other two editions do.
     */
    public static List<String> lines(List<Component> tooltip) {
        return tooltip == null ? null : new Wrapping(tooltip);
    }

    private static int room() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return WIDEST;
        // The window's own scaled width, which is what both older editions get from a scaled
        // resolution object this version does not have.
        int half = mc.getWindow()
            .getGuiScaledWidth() / 2;
        return Math.max(NARROWEST, Math.min(WIDEST, half));
    }

    /**
     * A list of strings over the game's list of components, splitting a line as it goes in.
     *
     * <p>
     * Only {@code add} does anything unusual. Everything else is the list underneath, because a
     * tooltip is read back as well as written to - the first entry is the item's own name, and code
     * that recolors it does so by index - and a view that quietly refused those would break the very
     * thing it was meant to leave alone.
     */
    private static final class Wrapping extends AbstractList<String> {

        private final List<Component> delegate;

        Wrapping(List<Component> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean add(String entry) {
            if (entry == null) return delegate.add(new TextComponent(""));

            Minecraft mc = Minecraft.getInstance();
            Font font = mc == null ? null : mc.font;
            // No font yet is not a case worth being clever about: it cannot happen while a tooltip is
            // being drawn, and the honest fallback is the line exactly as it was written.
            if (font == null) return delegate.add(new TextComponent(entry));

            List<FormattedText> split = font.getSplitter()
                .splitLines(entry, room(), Style.EMPTY);
            if (split == null || split.size() <= 1) return delegate.add(new TextComponent(entry));

            for (FormattedText piece : split) {
                delegate.add(new TextComponent(flatten(piece)));
            }
            return true;
        }

        /**
         * One split piece as the text it holds.
         *
         * <p>
         * A piece comes back as {@code FormattedText} rather than as a string - the splitter answers
         * in what it can split - and the only way back is to walk it. Every run of it is text this
         * mod wrote, formatting codes and all, so the walk is a concatenation and nothing is lost.
         */
        private static String flatten(FormattedText piece) {
            final StringBuilder out = new StringBuilder();
            piece.visit(text -> {
                out.append(text);
                return Optional.empty();
            });
            return out.toString();
        }

        @Override
        public void add(int index, String entry) {
            delegate.add(index, new TextComponent(entry == null ? "" : entry));
        }

        @Override
        public String get(int index) {
            Component held = delegate.get(index);
            return held == null ? "" : held.getString();
        }

        @Override
        public String set(int index, String entry) {
            Component was = delegate.set(index, new TextComponent(entry == null ? "" : entry));
            return was == null ? "" : was.getString();
        }

        @Override
        public String remove(int index) {
            Component was = delegate.remove(index);
            return was == null ? "" : was.getString();
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }
}
