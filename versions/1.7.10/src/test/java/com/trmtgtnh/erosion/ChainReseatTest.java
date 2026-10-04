package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * That a worn path survives its own rules being changed.
 *
 * <p>
 * A chain is rebuilt from config whenever the wear settings move, so a record written under the
 * old shape can name a step that no longer exists anywhere. Until this search existed such a
 * record was read as describing a different surface altogether and thrown away, which turned any
 * edit to the wear settings into a quiet deletion of every worn path in the world.
 *
 * <p>
 * The chains here are built by hand rather than looked up, because the lookup wants a loaded Forge
 * configuration and the rule does not.
 */
class ChainReseatTest {

    /** A chain of the shape the engine builds: a surface run, then a run per pixel of depth. */
    private static short[] chain(SurfaceFamily surface, int stages, SurfaceFamily sunken, int perDepth, int deepest) {
        short[] out = new short[stages + perDepth * deepest];
        int at = 0;
        for (int layer = 0; layer < stages; layer++) {
            out[at++] = ErosionState.pack(surface, layer, 0);
        }
        for (int depth = 1; depth <= deepest; depth++) {
            for (int layer = 0; layer < perDepth; layer++) {
                out[at++] = ErosionState.pack(sunken, layer, depth);
            }
        }
        return out;
    }

    @Test
    @DisplayName("a step that still exists is found exactly, so nothing moves for nothing")
    void anExistingStepIsItsOwnNearest() {
        short[] full = chain(SurfaceFamily.GRASS, 16, SurfaceFamily.DIRT, 8, 8);
        assertEquals(5, ErosionChain.nearestIn(full, SurfaceFamily.GRASS, 5, 0), "the sixth surface step");
        assertEquals(16, ErosionChain.nearestIn(full, SurfaceFamily.DIRT, 0, 1), "the first sunken step");
    }

    @Test
    @DisplayName("halving the gradations lands a record on the step nearest where it was")
    void aShortenedRunKeepsItsGround() {
        short[] halved = chain(SurfaceFamily.GRASS, 8, SurfaceFamily.DIRT, 8, 8);

        // Written when grass had sixteen surface gradations; gradation 15 no longer exists.
        int found = ErosionChain.nearestIn(halved, SurfaceFamily.GRASS, 15, 0);
        assertTrue(found >= 0, "a record from the old shape must not be lost");
        assertEquals(7, found, "and lands on the deepest surface gradation the new shape has");
    }

    @Test
    @DisplayName("depth outweighs shade, because a pixel of depth is a whole run of shades")
    void depthIsWorthMoreThanGradation() {
        short[] shallow = chain(SurfaceFamily.GRASS, 16, SurfaceFamily.DIRT, 8, 3);

        // Recorded six pixels down; the deepest the new shape reaches is three. The answer must
        // be a step at depth three, not a step at depth one that happens to share a gradation.
        int found = ErosionChain.nearestIn(shallow, SurfaceFamily.DIRT, 4, 6);
        assertTrue(found >= 0, "still on the chain");
        assertEquals(3, ErosionState.sinkOf(shallow[found]), "the deepest depth left");
        assertEquals(4, ErosionState.layerOf(shallow[found]), "and the same gradation within it");
    }

    @Test
    @DisplayName("a genuine change of surface still finds nothing, so it is still dropped")
    void anotherSurfaceIsNotRescued() {
        // Grass grew back over a patch that had worn down into sand. Sand is nowhere on grass's
        // chain, so there is no honest place to put this record and it must not be given one.
        short[] grassChain = chain(SurfaceFamily.GRASS, 16, SurfaceFamily.DIRT, 8, 8);
        assertEquals(-1, ErosionChain.nearestIn(grassChain, SurfaceFamily.SAND, 3, 2));
    }

    @Test
    @DisplayName("nothing is claimed from an empty or absent chain")
    void nothingFromNothing() {
        assertEquals(-1, ErosionChain.nearestIn(null, SurfaceFamily.GRASS, 0, 0), "a family that does not erode");
        assertEquals(-1, ErosionChain.nearestIn(new short[0], SurfaceFamily.GRASS, 0, 0), "or one switched off");
        short[] full = chain(SurfaceFamily.GRASS, 16, SurfaceFamily.DIRT, 8, 8);
        assertEquals(-1, ErosionChain.nearestIn(full, SurfaceFamily.GRASS, -1, 0), "an invisible record has no step");
    }
}
