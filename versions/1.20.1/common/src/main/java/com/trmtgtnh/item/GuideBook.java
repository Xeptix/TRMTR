package com.trmtgtnh.item;

import java.util.Locale;

/**
 * The four books the mod ships, and how many pages each has.
 *
 * <p>
 * A book is a key and a page count, and nothing else. Every word lives in the language file under
 * {@code trmtgtnh.guide.<key>.pageN.title} and {@code .body}, which means the prose can be
 * corrected, translated or rewritten without touching a line of Java - and means the reader never
 * has to know what any particular book is about.
 *
 * <p>
 * Four separate items rather than one item with a metadata, because each is meant to look like
 * itself on the shelf and in the hand, and a per-book animated texture is far simpler that way.
 */
public enum GuideBook {

    /** The overview: what the mod does and how to start using it. */
    MK1("mk1", 8),

    /** The technical one: the numbers, the storage, the knobs. */
    MK2("mk2", 10),

    /** Every command, its arguments, and what it does. */
    COMMANDS("commands", 8),

    /** The Golem of Ways: building it, feeding it, and its upgrades. */
    GOLEM("golem", 10);

    public final String key;

    public final int pages;

    GuideBook(String key, int pages) {
        this.key = key;
        this.pages = pages;
    }

    /** The item's registry and texture name, e.g. {@code guide_mk1}. */
    public String itemName() {
        return "guide_" + key;
    }

    /** The lang key for this book's own title. */
    public String titleKey() {
        return "trmtgtnh.guide." + key + ".title";
    }

    /** The lang key for one page's heading, numbered from 1. */
    public String pageTitleKey(int page) {
        return "trmtgtnh.guide." + key + ".page" + page + ".title";
    }

    /**
     * The lang key for one page's body, numbered from 1.
     *
     * <p>
     * One page has two texts. The golem book's price page describes whichever price the golem is
     * built with, and the flat price has its own key beside the usual one. The switch is a
     * compile-time constant, copied into this class when it is built, so asking it here loads
     * nothing of the golem's on a client.
     */
    public String pageBodyKey(int page) {
        String body = "trmtgtnh.guide." + key + ".page" + page + ".body";
        // The golem's price page has a second text for the flat price; see GolemWork.PRICED_FROM_THE_CHUNK_TAMPER.
        return !com.trmtgtnh.entity.GolemWork.PRICED_FROM_THE_CHUNK_TAMPER && "golem".equals(key) && page == 5
            ? body + ".flat"
            : body;
    }

    public static GuideBook byKey(String key) {
        if (key != null) {
            String wanted = key.toLowerCase(Locale.ROOT);
            for (GuideBook book : values()) {
                if (book.key.equals(wanted)) return book;
            }
        }
        return MK1;
    }

    public static GuideBook byOrdinal(int ordinal) {
        GuideBook[] all = values();
        return ordinal < 0 || ordinal >= all.length ? MK1 : all[ordinal];
    }
}
