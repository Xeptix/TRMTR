package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Where the Wayfarer is filed, and when the library has to take it as well.
 *
 * <p>
 * The failure fenced off here is a silent one: Forge accepts a category name nothing fills without a
 * word, and a Wayfarer filed only there is in the world's loot and in no chest. Nothing short of finding
 * every stronghold empty shows it, so each rule deciding between warning, falling back and leaving alone
 * is pinned. The names are Forge's own, from ChestGenHooks, written out because this runs without Forge;
 * the dedicated-server probe checked them against the real table.
 */
class WayfarerCategoriesTest {

    private static final String LIBRARY = "strongholdLibrary";

    /** Forge's ten, in the order ChestGenHooks declares them. */
    private static final String[] FORGE = { "mineshaftCorridor", "pyramidDesertyChest", "pyramidJungleChest",
        "pyramidJungleDispenser", "strongholdCorridor", "strongholdLibrary", "strongholdCrossing", "villageBlacksmith",
        "bonusChest", "dungeonChest" };

    @Test
    @DisplayName("nothing named, or only blanks, means the library")
    void emptyMeansTheLibrary() {
        List<String> library = Collections.singletonList(LIBRARY);
        assertEquals(library, WayfarerCategories.named(null, LIBRARY));
        assertEquals(library, WayfarerCategories.named(new String[0], LIBRARY));
        assertEquals(library, WayfarerCategories.named(new String[] { null, "", "   " }, LIBRARY));
    }

    @Test
    @DisplayName("names are trimmed and kept in the order written, a repeat included")
    void namesAreTidied() {
        assertEquals(
            Arrays.asList("pyramidDesertyChest", "dungeonChest", "pyramidDesertyChest"),
            WayfarerCategories
                .named(new String[] { " pyramidDesertyChest ", "", "dungeonChest", "pyramidDesertyChest" }, LIBRARY),
            "a name written twice is filed twice, the one way to weight one place above another");
        assertEquals(
            Arrays.asList("strongholdLibrary", "strongholdlibrary"),
            WayfarerCategories.named(new String[] { "strongholdLibrary", "strongholdlibrary" }, LIBRARY),
            "case is part of the name, as it is to Forge");
    }

    @Test
    @DisplayName("the list handed back cannot be changed")
    void namedIsFixed() {
        final List<String> named = WayfarerCategories.named(new String[] { "dungeonChest" }, LIBRARY);
        assertThrows(UnsupportedOperationException.class, new Executable() {

            @Override
            public void execute() {
                named.add(LIBRARY);
            }
        });
    }

    @Test
    @DisplayName("only a pool with no count and nothing but the Wayfarer in it reads as unused")
    void whatUnusedMeans() {
        assertTrue(WayfarerCategories.unused(0, 0, 0), "what a lookup of an unknown name makes");
        assertFalse(WayfarerCategories.unused(1, 5, 0), "sized by somebody, even with nothing in it yet");
        assertFalse(WayfarerCategories.unused(0, 1, 0), "any count at all was set on purpose");
        assertFalse(WayfarerCategories.unused(0, 0, 1), "filled by somebody who draws with a count of their own");
    }

    @Test
    @DisplayName("the library takes the Wayfarer only when every name is unused")
    void fallingBack() {
        List<String> one = Collections.singletonList("pyramidDesertChest");
        assertTrue(WayfarerCategories.fallsBack(one, one, LIBRARY));
        List<String> two = Arrays.asList("pyramidDesertyChest", "strongholdLibary");
        assertFalse(
            WayfarerCategories.fallsBack(two, Collections.singletonList("strongholdLibary"), LIBRARY),
            "one real pool is somewhere");
        assertTrue(WayfarerCategories.fallsBack(two, two, LIBRARY));
        assertFalse(WayfarerCategories.fallsBack(two, Collections.<String>emptyList(), LIBRARY));
        assertTrue(
            WayfarerCategories.fallsBack(Arrays.asList("x", "x"), Collections.singletonList("x"), LIBRARY),
            "a repeat is the same place, unused as often as it is named");
    }

    @Test
    @DisplayName("never a further library entry, and nothing for a Wayfarer that was not filed")
    void notFallingBack() {
        List<String> withLibrary = Arrays.asList("strongholdLibrary", "twilightForestHollowHill");
        assertFalse(WayfarerCategories.fallsBack(withLibrary, withLibrary, LIBRARY), "already filed there");
        assertFalse(WayfarerCategories.fallsBack(null, Collections.singletonList("x"), LIBRARY));
        assertFalse(
            WayfarerCategories.fallsBack(Collections.<String>emptyList(), Collections.<String>emptyList(), LIBRARY),
            "an empty list is not every name unused");
        assertFalse(WayfarerCategories.fallsBack(Collections.singletonList("x"), null, LIBRARY));
    }

    @Test
    @DisplayName("a slip of case or a letter or two names the Forge category it was probably meant to be")
    void nearMisses() {
        assertEquals("pyramidDesertyChest", WayfarerCategories.nearMiss("pyramidDesertChest", FORGE));
        assertEquals("strongholdLibrary", WayfarerCategories.nearMiss("strongholdlibrary", FORGE));
        assertEquals("strongholdLibrary", WayfarerCategories.nearMiss("StrongholdLibary", FORGE));
        assertEquals("dungeonChest", WayfarerCategories.nearMiss("dungeonChests", FORGE));
        assertEquals("mineshaftCorridor", WayfarerCategories.nearMiss("mineshaftCoridor", FORGE));
    }

    @Test
    @DisplayName("no suggestion for a name nothing is close to, or for one that is already exact")
    void noNearMiss() {
        assertNull(WayfarerCategories.nearMiss("twilightForestHollowHill", FORGE));
        assertNull(WayfarerCategories.nearMiss("strongholdLibrary", FORGE));
        assertNull(WayfarerCategories.nearMiss("stronghold", FORGE), "seven letters short of the nearest");
        assertNull(WayfarerCategories.nearMiss(null, FORGE));
        assertNull(WayfarerCategories.nearMiss("dungeonChest", null));
    }

    @Test
    @DisplayName("the nearest wins, the first given on a tie, and an exact name anywhere outranks a close one")
    void nearestThenFirst() {
        assertEquals("abcdef", WayfarerCategories.nearMiss("abcdeg", new String[] { "abcdef1", "abcdef", "abcdef2" }));
        assertEquals("abcdef1", WayfarerCategories.nearMiss("abcdefx", new String[] { "abcdef1", "abcdef2" }));
        assertNull(WayfarerCategories.nearMiss("dungeonChest", new String[] { "dungeonChests", "dungeonChest" }));
    }
}
