package com.trmtgtnh.client.gui;

import java.util.function.Function;

/**
 * What one list row draws beside its text, worked out again whenever the text is not what it was the frame before
 * (0.9.228; Xep, 2026-10-20: "a row added in the screen drawn with its picture at once, the block lists too").
 *
 * <p>
 * On 1.7.10 Forge's array editor made a newly added row a plain text row, so a mob typed into it showed no egg until
 * the list was opened again. A row here asks this every frame with its box's text as it stands, so a row added, or a
 * name typed, has its picture on the next frame, and a row whose text has not moved costs one comparison. Kept free of
 * the game's types so a test can hold it.
 */
final class RowPicture<T> {

    private final Function<String, T> resolve;

    private String lastText;

    private T picture;

    RowPicture(Function<String, T> resolve) {
        this.resolve = resolve;
    }

    /** The picture for the row's text as it stands now. */
    T of(String text) {
        String now = text == null ? "" : text;
        if (lastText == null || !lastText.equals(now)) {
            lastText = now;
            picture = resolve.apply(now);
        }
        return picture;
    }
}
