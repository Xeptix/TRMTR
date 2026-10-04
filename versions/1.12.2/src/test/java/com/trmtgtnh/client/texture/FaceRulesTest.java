package com.trmtgtnh.client.texture;

import static com.trmtgtnh.surface.SurfaceFamily.COBBLE;
import static com.trmtgtnh.surface.SurfaceFamily.DIRT;
import static com.trmtgtnh.surface.SurfaceFamily.END;
import static com.trmtgtnh.surface.SurfaceFamily.GRASS;
import static com.trmtgtnh.surface.SurfaceFamily.GRAVEL;
import static com.trmtgtnh.surface.SurfaceFamily.ICE;
import static com.trmtgtnh.surface.SurfaceFamily.LEAVES;
import static com.trmtgtnh.surface.SurfaceFamily.NETHER;
import static com.trmtgtnh.surface.SurfaceFamily.SAND;
import static com.trmtgtnh.surface.SurfaceFamily.SNOW;
import static com.trmtgtnh.surface.SurfaceFamily.STONE;
import static com.trmtgtnh.surface.SurfaceFamily.VEGETATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Which face a wear sprite is drawn from, what stands in for it and the edge it is drawn at, pinned by answers worked
 * out by hand.
 *
 * <p>
 * These rules were written twice, in the class that builds a picture and in the class that prices it, with nothing to
 * hold the two together but a comment asking for it, and the size a sprite was stitched at was a third reading of them.
 * One home is worth having only while a test says what lives there.
 */
class FaceRulesTest {

    @Test
    @DisplayName("the cover look covers only the surface it belongs to")
    void theCoverLookCoversOnlyItsOwnSurface() {
        assertTrue(FaceRules.drawsCover(GRASS, GRASS, true));
        assertFalse(FaceRules.drawsCover(GRASS, DIRT, true), "worn through to its earth, the cover is already gone");
        assertFalse(FaceRules.drawsCover(GRASS, GRASS, false), "a look that wears the face");
        assertTrue(FaceRules.drawsCover(STONE, STONE, true), "asked of the look, not of the family");
    }

    @Test
    @DisplayName("only grass worn through shows its earth, and grey earth is stood in for")
    void onlyGrassWornThroughShowsItsEarth() {
        assertTrue(FaceRules.revealsEarth(GRASS, DIRT));
        assertFalse(FaceRules.revealsEarth(GRASS, GRASS));
        assertFalse(FaceRules.revealsEarth(STONE, COBBLE));
        assertFalse(FaceRules.revealsEarth(null, DIRT));

        assertTrue(FaceRules.standsInForEarth(false, true, true));
        assertFalse(FaceRules.standsInForEarth(false, true, false), "earth with a colour of its own");
        assertFalse(FaceRules.standsInForEarth(true, true, true), "a cover shows no earth");
        assertFalse(FaceRules.standsInForEarth(false, false, true), "a face that is not grass worn through");
        assertEquals("dirt", FaceRules.standInName(GRASS, DIRT));
        assertEquals("snow", FaceRules.standInName(SNOW, SNOW));

        assertTrue(
            FaceRules.isColourless(new int[] { 0xFF808080, 0xFF7F8083, 0x00FF0000 }),
            "every channel within four, and a clear pixel has no colour to disagree about");
        assertFalse(FaceRules.isColourless(new int[] { 0xFF808080, 0xFF6E8E43 }), "one green pixel");
        assertFalse(FaceRules.isColourless(new int[] { 0x00000000 }), "nothing to see is not grey");
        assertFalse(FaceRules.isColourless(new int[0]));
        assertFalse(FaceRules.isColourless(null));
    }

    @Test
    @DisplayName("a sprite is made of its appearance, and grass of itself")
    void aSpriteIsMadeOfItsAppearance() {
        assertEquals(GRASS, FaceRules.madeOf(GRASS, DIRT), "a lawn's earth is read as a pair with its turf");
        assertEquals(COBBLE, FaceRules.madeOf(STONE, COBBLE), "stone worn through to cobble is made of cobble");
        assertEquals(STONE, FaceRules.madeOf(STONE, null));
        assertNull(FaceRules.madeOf(null, null));
    }

    @Test
    @DisplayName("the vanilla table names a texture for every family and side")
    void theVanillaTableNamesATextureForEveryFamilyAndSide() {
        assertEquals("grass_top", FaceRules.vanillaName(GRASS, 1));
        assertEquals("dirt", FaceRules.vanillaName(GRASS, 0));
        assertEquals("dirt", FaceRules.vanillaName(GRASS, 2));

        SurfaceFamily[] families = { DIRT, SAND, GRAVEL, STONE, COBBLE, LEAVES, VEGETATION, NETHER, END, SNOW, ICE };
        String[] names = { "dirt", "sand", "gravel", "stone", "cobblestone", "dirt", "dirt", "netherrack", "end_stone",
            "snow", "ice" };
        for (int i = 0; i < families.length; i++) {
            for (int side = 0; side < 6; side++) {
                assertEquals(names[i], FaceRules.vanillaName(families[i], side), families[i] + " on side " + side);
            }
        }
        for (int side = 0; side < 6; side++) {
            assertEquals("dirt", FaceRules.vanillaName(null, side), "no family on side " + side);
        }
        for (SurfaceFamily family : SurfaceFamily.values()) {
            assertNotNull(FaceRules.vanillaName(family, 1), family + " has a name");
        }
    }

    @Test
    @DisplayName("a face is priced and drawn at its file, and never grown")
    void aFaceIsDrawnAtItsFile() {
        assertEquals(16, FaceRules.faceEdge(16, 0, 32), "a face read is drawn at its file");
        assertEquals(32, FaceRules.faceEdge(0, 0, 32), "a face unread at dirt and stone's edge");
        assertEquals(16, FaceRules.faceEdge(0, 0, 16), "an unread face is not grown under filtering");
        assertEquals(64, FaceRules.faceEdge(64, 16, 16));
        assertEquals(16, AtlasPlan.cellsFor(64));
        assertEquals(
            64,
            FaceRules.faceEdge(0, 64, 16),
            "an unread face is drawn no smaller than the face put in its place");
        assertEquals(32, FaceRules.faceEdge(0, 16, 32));
    }

    @Test
    @DisplayName("a sprite read only from vanilla files is drawn at its file, its pair's, or the stand-in's")
    void aSpriteReadOnlyFromVanillaFiles() {
        assertEquals(16, FaceRules.fileEdge(0, 0), "the flat stand-in");
        assertEquals(FaceRules.PLACEHOLDER_EDGE, FaceRules.fileEdge(0, 0));
        assertEquals(64, FaceRules.fileEdge(0, 64));
        assertEquals(32, FaceRules.fileEdge(32, 64));
    }

    @Test
    @DisplayName("an appearance is drawn at the face it comes from")
    void anAppearanceIsDrawnAtTheFaceItComesFrom() {
        assertEquals(16, FaceRules.appearanceEdge(false, false, 16, 64), "drawn from the top");
        assertEquals(1, AtlasPlan.cellsFor(FaceRules.appearanceEdge(false, false, 16, 64)));
        assertEquals(64, FaceRules.appearanceEdge(false, true, 16, 64), "grass worn through to its earth");
        assertEquals(16, AtlasPlan.cellsFor(FaceRules.appearanceEdge(false, true, 16, 64)));
        assertEquals(64, FaceRules.appearanceEdge(true, false, 16, 64), "a cover over the larger face");
        assertEquals(16, AtlasPlan.cellsFor(FaceRules.appearanceEdge(true, false, 16, 64)));
        assertEquals(32, FaceRules.appearanceEdge(true, false, 32, 16));
        assertEquals(4, AtlasPlan.cellsFor(FaceRules.appearanceEdge(true, false, 32, 16)));
    }
}
