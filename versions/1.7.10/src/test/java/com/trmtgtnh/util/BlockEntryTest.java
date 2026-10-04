package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * That a config entry's name and its metadata come apart in the one way that is not obvious.
 *
 * <p>
 * A registry name already contains a colon, so the metadata is the tail past the <em>last</em> one
 * and only when that tail is a number. Split on the first colon instead and
 * {@code IronChest:BlockIronChest} becomes a block called BlockIronChest with no metadata at all -
 * which is how the demonstrate command spent a long time placing the smallest chest that mod has
 * and quietly dropping everything that would not fit in it.
 */
class BlockEntryTest {

    @Test
    @DisplayName("a plain registry name keeps its colon and takes the fallback")
    void plainName() {
        assertEquals("minecraft:chest", BlockEntry.nameOf("minecraft:chest"));
        assertEquals(0, BlockEntry.metaOf("minecraft:chest", 0));
        assertEquals(32767, BlockEntry.metaOf("minecraft:chest", 32767));

        assertEquals("IronChest:BlockIronChest", BlockEntry.nameOf("IronChest:BlockIronChest"));
        assertEquals(0, BlockEntry.metaOf("IronChest:BlockIronChest", 0));
    }

    @Test
    @DisplayName("a trailing number is the metadata, and the rest is still the name")
    void withMeta() {
        assertEquals("IronChest:BlockIronChest", BlockEntry.nameOf("IronChest:BlockIronChest:9"));
        assertEquals(9, BlockEntry.metaOf("IronChest:BlockIronChest:9", 0));

        assertEquals("minecraft:stone_slab", BlockEntry.nameOf("minecraft:stone_slab:3"));
        assertEquals(3, BlockEntry.metaOf("minecraft:stone_slab:3", 32767));
    }

    @Test
    @DisplayName("a tail that is not a number stays part of the name")
    void notANumber() {
        assertEquals("mod:thing:special", BlockEntry.nameOf("mod:thing:special"));
        assertEquals(7, BlockEntry.metaOf("mod:thing:special", 7));
    }

    @Test
    @DisplayName("surrounding space and nothing at all are both survivable")
    void edges() {
        assertEquals("IronChest:BlockIronChest", BlockEntry.nameOf("  IronChest:BlockIronChest:9  "));
        assertEquals(9, BlockEntry.metaOf("  IronChest:BlockIronChest:9  ", 0));

        assertEquals("", BlockEntry.nameOf(null));
        assertEquals(5, BlockEntry.metaOf(null, 5));
        assertEquals("", BlockEntry.nameOf(""));
        assertEquals(5, BlockEntry.metaOf("", 5));
    }

    @Test
    @DisplayName("a name with no colon at all is a name, not a metadata")
    void noColon() {
        assertEquals("chest", BlockEntry.nameOf("chest"));
        assertEquals(0, BlockEntry.metaOf("chest", 0));
        // A leading colon leaves nothing to be the name, so the whole of it stays one.
        assertEquals(":9", BlockEntry.nameOf(":9"));
        assertEquals(0, BlockEntry.metaOf(":9", 0));
    }
}
