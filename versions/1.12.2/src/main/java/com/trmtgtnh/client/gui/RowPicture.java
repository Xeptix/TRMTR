package com.trmtgtnh.client.gui;

/**
 * The picture a list row draws, following the row's text (0.9.228; Xep: "a row added in the screen drawn with its
 * picture at once, the block lists too"): asked every frame with the text the row shows now, it works the picture out
 * again only when that text has changed since the last frame, so a row just added, or a name just typed, shows its
 * picture on the next frame and a row left alone costs one comparison. Every row that draws a picture beside a line -
 * the own screen's list page and both of Forge's array rows - draws through one of these.
 */
final class RowPicture<T> {

    /** How a row's text becomes its picture; null for none. */
    interface Resolve<T> {

        T of(String text);
    }

    private final Resolve<T> resolve;

    private String last;

    private T held;

    RowPicture(Resolve<T> resolve) {
        this.resolve = resolve;
    }

    /** The picture for the text the row shows this frame. */
    T of(String text) {
        String now = text == null ? "" : text;
        if (last == null || !last.equals(now)) {
            last = now;
            held = resolve.of(now);
        }
        return held;
    }
}
