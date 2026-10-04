package com.trmtgtnh.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What each rung of each chooser actually means.
 *
 * <p>
 * Applying a preset needs a live Forge {@code Configuration} and is not testable here. What is
 * testable is the half that decides what a rung is worth, and one thing about it that would fail
 * silently rather than loudly: Forge holds a value it reads to the range declared beside it, so a
 * rung that asks for a figure outside that range does not fail, it is quietly clamped to something
 * nobody chose. That is exactly the shape of fault this project keeps finding late, so the ranges
 * are written down here beside the rungs that have to fit inside them.
 */
class PresetsTest {

    @Test
    @DisplayName("every quality rung sits inside the range its own setting declares")
    void qualityRungsAreInRange() {
        for (String rung : new String[] { "potato", "low", "default", "high", "ultra" }) {
            int[] q = Presets.quality(rung);
            assertEquals(5, q.length, rung + " must set every figure it owns");

            // client.wearGradations: SurfaceFamily.MAX_STAGES .. WearScale.COUNTED_STEPS
            assertTrue(q[0] >= 16 && q[0] <= 80, rung + " gradations out of range: " + q[0]);
            // client.wearRotations: 1..4
            assertTrue(q[1] >= 1 && q[1] <= 4, rung + " rotations out of range: " + q[1]);
            // client.maxWearSprites: 768..262144
            assertTrue(q[2] >= 768 && q[2] <= 262144, rung + " sprite ceiling out of range: " + q[2]);
            // surfaces.maxTexturedSurfaces: 0..4096
            assertTrue(q[3] >= 0 && q[3] <= 4096, rung + " textured surfaces out of range: " + q[3]);
            assertTrue(q[4] == 0 || q[4] == 1, rung + " per-surface flag must be a flag");
        }
    }

    @Test
    @DisplayName("the quality ladder only ever climbs, so a higher rung is never cheaper")
    void qualityLadderIsMonotonic() {
        String[] ladder = { "potato", "low", "default", "high", "ultra" };
        for (int i = 1; i < ladder.length; i++) {
            int[] below = Presets.quality(ladder[i - 1]);
            int[] here = Presets.quality(ladder[i]);
            for (int f = 0; f < 5; f++) {
                assertTrue(here[f] >= below[f], ladder[i] + " asks for less than " + ladder[i - 1] + " in figure " + f);
            }
        }
    }

    @Test
    @DisplayName("default quality is what the mod ships with, so choosing it changes nothing")
    void defaultQualityIsTheShippedOne() {
        int[] q = Presets.quality("default");
        assertEquals(80, q[0], "gradations");
        assertEquals(4, q[1], "rotations");
        assertEquals(262144, q[2], "sprite ceiling");
        assertEquals(1024, q[3], "textured surfaces");
        assertEquals(1, q[4], "per-surface textures");
    }

    @Test
    @DisplayName("slow means more and quick means less, and default means exactly one")
    void theLeanLadderRunsTheRightWay() {
        assertEquals(1.0f, Presets.lean("default"), 0f, "default must be a no-op");
        assertTrue(Presets.lean("very_slow") > Presets.lean("slow"));
        assertTrue(Presets.lean("slow") > Presets.lean("default"));
        assertTrue(Presets.lean("default") > Presets.lean("brisk"));
        assertTrue(Presets.lean("brisk") > Presets.lean("quick"));
        assertEquals(2.0f, Presets.lean("very_slow"), 0f, "twice the traffic, twice the wait");
        assertEquals(0.5f, Presets.lean("quick"), 0f);
        assertEquals(1.0f, Presets.lean("nonsense"), 0f, "anything unrecognised must not move a number");
    }

    @Test
    @DisplayName("the depth ladder runs the right way and default leaves each family alone")
    void theDepthLadderRunsTheRightWay() {
        assertEquals(0f, Presets.depthLean("none"), 0f);
        assertEquals(1.0f, Presets.depthLean("default"), 0f);
        assertTrue(Presets.depthLean("shallow") < 1.0f);
        assertTrue(Presets.depthLean("deep") > 1.0f);
        assertTrue(Presets.depthLean("deepest") > Presets.depthLean("deep"));
    }

    @Test
    @DisplayName("no depth rung can push a family past what the sink field holds")
    void depthRungsStayInsideTheRecord() {
        for (String rung : new String[] { "none", "shallow", "default", "deep", "deepest" }) {
            for (SurfaceFamily family : SurfaceFamily.values()) {
                FamilySettings shipped = FamilySettings.shipped(family);
                if (shipped == null) continue;
                int wanted = shipped.maxSinkPixels == 0 ? 0
                    : Math.round(shipped.maxSinkPixels * Presets.depthLean(rung));
                assertTrue(Math.min(15, wanted) <= 15, rung + " on " + family.key() + " would ask for " + wanted);
            }
        }
    }

    @Test
    @DisplayName("the phases ladder only ever goes down, because sixteen is the record's ceiling")
    void phaseRungsOnlyDescend() {
        assertNull(Presets.phaseRung("default"), "default must mean each family's own figure");
        int[] fewer = Presets.phaseRung("fewer");
        int[] coarse = Presets.phaseRung("coarse");
        int[] veryCoarse = Presets.phaseRung("very_coarse");
        assertNotNull(fewer);
        assertNotNull(coarse);
        assertNotNull(veryCoarse);

        assertTrue(fewer[0] > coarse[0] && coarse[0] > veryCoarse[0], "stages must descend");
        assertTrue(fewer[1] > coarse[1] && coarse[1] > veryCoarse[1], "layers must descend");
        assertTrue(fewer[0] <= SurfaceFamily.MAX_STAGES, "no rung may exceed what a record can hold");
        assertTrue(veryCoarse[0] >= 1 && veryCoarse[1] >= 1, "a run of no gradations is not a run");
    }

    @Test
    @DisplayName("high and ultra move only the textured-surface ceiling, low draws under a sixth of the pictures under a lower ceiling, and only potato turns per-surface textures off")
    void theQualityRungsDoWhatTheReadmeSays() {
        // README's Presets section and the quality chooser's own comment say each of these in words, and
        // the inner-layer paragraph leans on all of them: high and ultra change nothing about a layer on a
        // pack with room, a lower rung lays a moving layer into fewer pictures and lets fewer blocks keep
        // their own, and potato alone takes every block's own pixels away and the layer with them. A rung
        // changed so that this fails leaves those passages wrong until they are changed with it.
        int[] shipped = Presets.quality("default");
        for (String rung : new String[] { "high", "ultra" }) {
            int[] q = Presets.quality(rung);
            for (int f = 0; f < 5; f++) {
                if (f == 3) continue;
                assertEquals(
                    shipped[f],
                    q[f],
                    rung + " must match default in figure "
                        + f
                        + ", because README says it moves only the textured-surface ceiling");
            }
            assertTrue(q[3] > shipped[3], rung + " must let more blocks have textures of their own than default does");
        }
        int[] low = Presets.quality("low");
        assertTrue(
            low[0] * low[1] * 6 < shipped[0] * shipped[1],
            "README says low draws a surface with under a sixth of the default's pictures: " + low[0] * low[1]
                + " against "
                + shipped[0] * shipped[1]);
        assertTrue(low[3] < shipped[3], "README says low lowers the ceiling on how many blocks keep their own pixels");
        for (String rung : new String[] { "low", "default", "high", "ultra" }) {
            assertEquals(
                1,
                Presets.quality(rung)[4],
                rung + " must keep per-surface textures on, because README says only potato turns them off");
        }
        assertEquals(
            0,
            Presets.quality("potato")[4],
            "potato must turn per-surface textures off, because README says it takes every inner layer away");
    }
}
