package com.trmtgtnh.client.gui;

/**
 * Which picture a dimension takes (0.9.228): the vanilla three their ground, every other the portal. Plain, so a test
 * can hold the rule the edition's pictures follow; the same in every edition, by the ids 1.7.10 and 1.12.2 write and
 * the vanilla names the newer editions write.
 */
final class DimensionPictures {

    static final int OVERWORLD = 0;

    static final int NETHER = 1;

    static final int END = 2;

    /** Any dimension but the vanilla three, or one no id or name tells. */
    static final int OTHER = 3;

    private DimensionPictures() {}

    static int of(Integer id) {
        if (id == null) return OTHER;
        if (id.intValue() == 0) return OVERWORLD;
        if (id.intValue() == -1) return NETHER;
        if (id.intValue() == 1) return END;
        return OTHER;
    }
}
