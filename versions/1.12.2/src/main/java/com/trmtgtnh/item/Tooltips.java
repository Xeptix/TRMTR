package com.trmtgtnh.item;

import java.util.AbstractList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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
 * strings to the list the game passed in, and there are forty-odd of those calls across eight
 * items; wrapping each of them would be forty edits now and one more for every line added
 * afterwards, with the one that got forgotten being the one nobody notices until it is on somebody
 * else's screenshot. Substituting the list instead is one line per item and covers everything they
 * will ever add.
 *
 * <p>
 * The width is measured rather than assumed, because a tooltip is drawn in interface pixels and
 * there are four times as many of those at one interface scale as at another. Half the screen is
 * the ceiling anybody would want; the fixed cap under it is what stops a very wide window turning
 * the wrap back into the thing it was fixing.
 *
 * <p>
 * Client-only, as {@code Item.addInformation} itself is - a dedicated server never builds one of
 * these and has no font to measure with.
 */
@SideOnly(Side.CLIENT)
public final class Tooltips {

    /** The widest a line is allowed to be, in interface pixels. About four dozen characters. */
    private static final int WIDEST = 260;

    /** And the narrowest, so a tiny window gives short lines rather than one letter per line. */
    private static final int NARROWEST = 120;

    private Tooltips() {}

    /**
     * The same list, wrapping whatever is added to it.
     *
     * <p>
     * Handed back as the raw type the game passes in, so a caller writes one assignment at the top
     * of {@code addInformation} and changes nothing else.
     */
    @SuppressWarnings("rawtypes")
    public static List wrapping(List tooltip) {
        if (tooltip == null || tooltip instanceof Wrapping) return tooltip;
        return new Wrapping(tooltip);
    }

    private static int room() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return WIDEST;
        ScaledResolution res = new ScaledResolution(mc);
        int half = res.getScaledWidth() / 2;
        return Math.max(NARROWEST, Math.min(WIDEST, half));
    }

    /**
     * A list that splits a line as it goes in.
     *
     * <p>
     * Only {@code add} does anything unusual. Everything else is the list underneath, because a
     * tooltip is read back as well as written to - the first entry is the item's own name, and code
     * that recolours it does so by index - and a view that quietly refused those would break the
     * very thing it was meant to leave alone.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final class Wrapping extends AbstractList {

        private final List delegate;

        Wrapping(List delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean add(Object entry) {
            if (!(entry instanceof String)) return delegate.add(entry);

            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            // No font yet is not a case worth being clever about: it cannot happen while a tooltip
            // is being drawn, and the honest fallback is the line exactly as it was written.
            if (font == null) return delegate.add(entry);

            List split = font.listFormattedStringToWidth((String) entry, room());
            if (split == null || split.isEmpty()) return delegate.add(entry);
            delegate.addAll(split);
            return true;
        }

        @Override
        public void add(int index, Object entry) {
            delegate.add(index, entry);
        }

        @Override
        public Object get(int index) {
            return delegate.get(index);
        }

        @Override
        public Object set(int index, Object entry) {
            return delegate.set(index, entry);
        }

        @Override
        public Object remove(int index) {
            return delegate.remove(index);
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }
}
