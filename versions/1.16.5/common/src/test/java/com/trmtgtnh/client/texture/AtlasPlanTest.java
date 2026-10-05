package com.trmtgtnh.client.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sums that decide how much of the block atlas the wear textures take, pinned by figures worked out by
 * hand.
 *
 * <p>
 * The cost model and the cap used to be three copies that disagreed, and none of them could fail a test.
 */
class AtlasPlanTest {

    /** A measure whose every readable texture is drawn at one edge, which is how 0.9.209 first priced the pack. */
    private static AtlasPlan.Measure measure(boolean taken, int demand, int glMaximum, boolean anisotropic,
        int packEdge) {
        return new AtlasPlan.Measure(
            taken,
            demand,
            demand,
            (long) demand * AtlasPlan.spriteCells(packEdge, anisotropic),
            glMaximum,
            anisotropic);
    }

    /**
     * A wish whose every sprite is drawn at one edge, so the figures from before per-file pricing can be held
     * where they were right.
     */
    private static AtlasPlan.Wish uniformWish(int edge, int wanted, int floor, int rotations, int fallbacks, int sides,
        int[] sets, int ceiling, int spriteCeiling) {
        int each = AtlasPlan.cellsFor(edge);
        int[] cells = null;
        if (sets != null) {
            cells = new int[sets.length];
            for (int i = 0; i < sets.length; i++) {
                cells[i] = sets[i] * each;
            }
        }
        return new AtlasPlan.Wish(
            edge,
            wanted,
            floor,
            rotations,
            fallbacks,
            (long) fallbacks * each,
            each,
            sides,
            (long) sides * each,
            sets,
            cells,
            ceiling,
            spriteCeiling);
    }

    /** This many surface sets of one appearance each. */
    private static int[] plainSets(int count) {
        int[] sets = new int[count];
        Arrays.fill(sets, 1);
        return sets;
    }

    /** The test pack's six hundred sets, one in thirty of them a lawn with its earth: 620 appearances. */
    private static int[] defaultPackSets() {
        int[] sets = new int[600];
        for (int i = 0; i < sets.length; i++) {
            sets[i] = i % 30 == 0 ? 2 : 1;
        }
        return sets;
    }

    /** The test pack at sixteen pixels, eighty gradations and four rotations, with every ceiling at its default. */
    private static AtlasPlan.Wish defaultPackWish() {
        return uniformWish(16, 80, 16, 4, 17, 40, defaultPackSets(), 1024, 262144);
    }

    /** The thirty-two pixel pack 0.9.207 planned a quarter past the room its atlas had left. */
    private static AtlasPlan thirtyTwoPixelPack() {
        return AtlasPlan.plan(
            measure(true, 10000, 16384, false, 32),
            uniformWish(32, 80, 16, 4, 17, 40, plainSets(983), 1024, 262144));
    }

    /** A 256 square with ninety slots free, and a lawn third in line. */
    private static AtlasPlan lawnThirdInLine() {
        return AtlasPlan.plan(
            measure(true, 150, 256, false, 16),
            uniformWish(16, 16, 16, 1, 1, 0, new int[] { 1, 1, 2, 1 }, 1024, Integer.MAX_VALUE));
    }

    /** The pre-flight as it stood before this class, transcribed so the sixteen-pixel figures can be held to it. */
    private static int legacyGradations(int wanted, long appearances, int rotations, int cost, int demand, int glMax) {
        long side = Math.min(glMax <= 0 ? 8192 : glMax, 8192);
        long cells = side * side / 256L;
        long room = Math.max(0L, cells - cells / 16L - (long) demand * cost);
        long affordable = room / Math.max(1L, appearances * rotations * cost);
        if (affordable >= wanted) return wanted;
        return (int) Math.max(16L, Math.min(wanted, affordable));
    }

    /** A stitch of one sixteen-pixel strip for each half, each this many slots long, at four mipmap levels. */
    private static AtlasPlan.Stitched strips(long ownSlots, long otherSlots) {
        AtlasPlan.Stitched stitched = new AtlasPlan.Stitched(4);
        stitched.add(true, 16, (int) (16L * ownSlots));
        stitched.add(false, 16, (int) (16L * otherSlots));
        return stitched;
    }

    /** These values as bytes. */
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    @Test
    @DisplayName("a sprite is priced as the square the stitcher rounds it to")
    void aSpriteIsPricedAsItsRoundedSquare() {
        assertEquals(1, AtlasPlan.cellsFor(-5));
        assertEquals(1, AtlasPlan.cellsFor(0));
        assertEquals(1, AtlasPlan.cellsFor(8));
        assertEquals(1, AtlasPlan.cellsFor(16));
        assertEquals(4, AtlasPlan.cellsFor(17));
        assertEquals(4, AtlasPlan.cellsFor(24), "floor division priced it at 1");
        assertEquals(4, AtlasPlan.cellsFor(32));
        assertEquals(9, AtlasPlan.cellsFor(33));
        assertEquals(9, AtlasPlan.cellsFor(48));
        assertEquals(16, AtlasPlan.cellsFor(64));
    }

    @Test
    @DisplayName("the plan never builds past an 8192 square")
    void theSideNeverPasses8192() {
        assertEquals(8192, AtlasPlan.side(-1), "a probe that failed");
        assertEquals(8192, AtlasPlan.side(0));
        assertEquals(256, AtlasPlan.side(256));
        assertEquals(4096, AtlasPlan.side(4096));
        assertEquals(8192, AtlasPlan.side(8192));
        assertEquals(8192, AtlasPlan.side(16384));

        assertEquals(262144L, AtlasPlan.squareCells(16384));
        assertEquals(16384L, AtlasPlan.squareCells(2048));
        assertEquals(262144L, AtlasPlan.squareCells(-1), "a probe that failed");
    }

    @Test
    @DisplayName("room is the square less a sixteenth less the pack as its files price it")
    void roomIsTheSquareLessTheReserveLessThePack() {
        assertEquals(235760L, AtlasPlan.room(new AtlasPlan.Measure(true, 10000, 10000, 10000L, 16384, false), 16));
        assertEquals(
            235760L,
            AtlasPlan.room(new AtlasPlan.Measure(true, 10000, 10000, 10000L, 16384, false), 32),
            "dirt and stone at thirty-two no longer price the pack");
        assertEquals(205760L, AtlasPlan.room(new AtlasPlan.Measure(true, 10000, 10000, 40000L, 16384, false), 16));
        assertEquals(51440L, AtlasPlan.room(new AtlasPlan.Measure(true, 10000, 10000, 10000L, 4096, false), 16));
        assertEquals(
            0L,
            AtlasPlan.room(new AtlasPlan.Measure(true, 20000, 20000, 80000L, 4096, false), 32),
            "never below nothing");
        assertEquals(15360L, AtlasPlan.room(new AtlasPlan.Measure(true, 0, 0, 0L, 2048, false), 16));

        assertEquals(
            40000L,
            AtlasPlan.packCells(new AtlasPlan.Measure(true, 10000, 10000, 40000L, 16384, true), 16),
            "the growth is in the widths read, not added again");
    }

    @Test
    @DisplayName("an unmeasured stitch assumes half the square is taken")
    void anUnmeasuredStitchAssumesHalfTheSquare() {
        assertEquals(
            114688L,
            AtlasPlan.room(new AtlasPlan.Measure(false, 999999, 999999, 999999L, 16384, false), 16),
            "the demand is ignored");
        assertEquals(28672L, AtlasPlan.room(new AtlasPlan.Measure(false, 0, 0, 0L, 4096, false), 32));
        assertEquals(
            131072L,
            AtlasPlan.packCells(new AtlasPlan.Measure(false, 10000, 10000, 40000L, 16384, true), 16),
            "half the square, whatever was read");
    }

    @Test
    @DisplayName("gradations fall only as far as the floor, and never rise past the wish")
    void gradationsFallOnlyToTheFloor() {
        assertEquals(98L, AtlasPlan.affordable(235760L, 0L, 2400L));
        assertEquals(80, AtlasPlan.gradations(80, 16, 98L));

        assertEquals(58L, AtlasPlan.affordable(235760L, 0L, 4000L));
        assertEquals(58, AtlasPlan.gradations(80, 16, 58L));

        assertEquals(12L, AtlasPlan.affordable(205760L, 0L, 16000L));
        assertEquals(16, AtlasPlan.gradations(80, 16, 12L), "held at the floor");
        assertEquals(8, AtlasPlan.gradations(8, 16, 12L));

        assertEquals(4L, AtlasPlan.affordable(205760L, 0L, 51440L));
        assertEquals(8, AtlasPlan.gradations(8, 16, 4L), "0.9.207 raised this to 16");

        assertEquals(0L, AtlasPlan.affordable(100L, 200L, 5L), "the fixed slots alone fill the room");
        assertEquals(16, AtlasPlan.gradations(80, 16, 0L));

        assertEquals(48L, AtlasPlan.affordable(240L, 0L, 5L));
        assertEquals(48, AtlasPlan.gradations(48, 16, 48L));

        assertEquals(Long.MAX_VALUE, AtlasPlan.affordable(5L, 0L, 0L));
    }

    @Test
    @DisplayName("at sixteen pixels the arithmetic is what it was")
    void sixteenPixelsPriceAsTheOldPreFlightDid() {
        int[] appearances = { 1, 17, 300, 617, 1000, 2000, 3600 };
        int[] rotations = { 1, 2, 4 };
        int[] wanted = { 16, 24, 48, 80 };
        int[] demands = { 0, 10000, 40000 };
        int[] maximums = { 4096, 8192, 16384 };
        int cases = 0;
        for (int count : appearances) {
            for (int turns : rotations) {
                for (int asked : wanted) {
                    for (int demand : demands) {
                        for (int maximum : maximums) {
                            long room = AtlasPlan.room(measure(true, demand, maximum, false, 16), 16);
                            long affordable = AtlasPlan.affordable(room, 0L, (long) count * turns);
                            assertEquals(
                                legacyGradations(asked, count, turns, 1, demand, maximum),
                                AtlasPlan.gradations(asked, 16, affordable),
                                count + " appearances at "
                                    + turns
                                    + " rotations wanting "
                                    + asked
                                    + ", demand "
                                    + demand
                                    + " on a card of "
                                    + maximum);
                            cases++;
                        }
                    }
                }
            }
        }
        assertEquals(
            appearances.length * rotations.length * wanted.length * demands.length * maximums.length,
            cases,
            "every case in the grid was compared");
    }

    @Test
    @DisplayName("the default pack at sixteen pixels draws eighty and keeps every surface")
    void theDefaultPackAtSixteenPixelsIsUnmoved() {
        AtlasPlan plan = AtlasPlan.plan(measure(true, 10000, 16384, false, 16), defaultPackWish());
        assertEquals(262144L, plan.squareCells);
        assertEquals(16384L, plan.reserveCells);
        assertEquals(10000L, plan.packCells);
        assertEquals(235760L, plan.room);
        assertEquals(600, plan.surfaceSetsConsidered);
        assertEquals(638L, plan.pricedAppearances, "17 fallbacks, 620 set appearances and the fringe");
        assertEquals(638L, plan.pricedCells, "one slot apiece at sixteen pixels");
        assertEquals(2552L, plan.cellsPerGradation);
        assertEquals(40L, plan.sideCells);
        assertEquals(92L, plan.affordable);
        assertEquals(80, plan.gradations);
        assertFalse(plan.floorHeld());
        assertEquals(5800L, plan.mandatorySprites, "18 appearances at 80 gradations and 4 rotations, and 40 sides");
        assertEquals(5800L, plan.mandatoryCells);
        assertEquals(600, plan.surfaceSetsKept);
        assertEquals(620L, plan.surfaceAppearancesKept);
        assertEquals(620L, plan.surfaceCellsKept);
        assertEquals(AtlasPlan.Limit.NONE, plan.stoppedBy);
        assertEquals(204200L, plan.plannedSprites);
        assertEquals(204200L, plan.plannedCells);
        assertEquals(plan.cellsWanted, plan.plannedCells, "everything priced was planned");

        // 0.9.207 priced 617 appearances, one per block and one per fallback, and planned 17 x 320 + 620 x 320
        // = 203,840 sprites at the same eighty gradations.
        assertEquals(80, legacyGradations(80, 617, 4, 1, 10000, 16384));
    }

    @Test
    @DisplayName("a pack drawn at thirty-two pixels throughout no longer plans past the room it measured")
    void aThirtyTwoPixelPackStaysInItsRoom() {
        AtlasPlan plan = thirtyTwoPixelPack();
        assertEquals(40000L, plan.packCells);
        assertEquals(205760L, plan.room);
        assertEquals(1001L, plan.pricedAppearances);
        assertEquals(4004L, plan.pricedCells);
        assertEquals(16016L, plan.cellsPerGradation);
        assertEquals(12L, plan.affordable);
        assertEquals(16, plan.gradations);
        assertTrue(plan.floorHeld());
        assertEquals(1192L, plan.mandatorySprites);
        assertEquals(4768L, plan.mandatoryCells);
        assertFalse(plan.mandatoryOverruns());

        assertEquals(785, plan.surfaceSetsKept);
        assertEquals(3140L, plan.surfaceCellsKept);
        assertEquals(198, plan.surfacesDropped());
        assertEquals(AtlasPlan.Limit.ROOM, plan.stoppedBy);
        assertEquals(51432L, plan.plannedSprites);
        assertEquals(205728L, plan.plannedCells);
        assertTrue(plan.plannedCells <= plan.room);
        assertTrue(plan.plannedCells + 256L > plan.room, "one more set of 64 sprites would not have fitted");

        // The defect: 0.9.207 priced 1,000 appearances, held the ramp at sixteen, and then registered every
        // set, every fringe and every wall at four slots apiece.
        assertEquals(16, legacyGradations(80, 1000, 4, 4, 10000, 16384));
        assertTrue(64000L * 4 + 64L * 4 + 40L * 4 > 205760L);
        assertEquals(256416L, 64000L * 4 + 64L * 4 + 40L * 4);
    }

    @Test
    @DisplayName("a thirty-two pixel pack whose sets fit keeps them all at the ramp 0.9.207 chose")
    void aThirtyTwoPixelPackThatFitsKeepsEverything() {
        AtlasPlan plan = AtlasPlan.plan(
            measure(true, 10000, 16384, false, 32),
            uniformWish(32, 80, 16, 4, 17, 23, plainSets(583), 1024, 262144));
        assertEquals(2404L, plan.pricedCells);
        assertEquals(21L, plan.affordable);
        assertEquals(21, plan.gradations);
        assertFalse(plan.floorHeld());
        assertEquals(1535L, plan.mandatorySprites);
        assertEquals(6140L, plan.mandatoryCells);
        assertEquals(583, plan.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.NONE, plan.stoppedBy);
        assertEquals(50507L, plan.plannedSprites);
        assertEquals(202028L, plan.plannedCells);

        assertEquals(21, legacyGradations(80, 600, 4, 4, 10000, 16384));
    }

    @Test
    @DisplayName("a grass set is priced at both its appearances and the first refusal is final")
    void aLawnCostsBothItsAppearances() {
        AtlasPlan plan = lawnThirdInLine();
        assertEquals(90L, plan.room);
        assertEquals(12L, plan.affordable);
        assertEquals(16, plan.gradations);
        assertEquals(2, plan.surfaceSetsKept);
        assertEquals(2L, plan.surfaceAppearancesKept);
        assertEquals(2L, plan.surfaceCellsKept);
        assertEquals(AtlasPlan.Limit.ROOM, plan.stoppedBy);
        assertEquals(64L, plan.plannedCells, "the fourth set would have fitted at 80, and is not taken");
    }

    @Test
    @DisplayName("the surface ceiling bounds what is priced as well as what is kept")
    void theSurfaceCeilingBoundsThePrice() {
        int[] five = { 1, 1, 1, 1, 1 };

        AtlasPlan three = AtlasPlan
            .plan(measure(true, 0, 256, false, 16), uniformWish(16, 48, 16, 1, 1, 0, five, 3, Integer.MAX_VALUE));
        assertEquals(240L, three.room);
        assertEquals(3, three.surfaceSetsConsidered);
        assertEquals(5L, three.pricedCells, "the fallback, the fringe and three sets");
        assertEquals(48L, three.affordable, "pricing all five would have given 34");
        assertEquals(48, three.gradations);
        assertEquals(3, three.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.CEILING, three.stoppedBy);
        assertEquals(240L, three.plannedCells);

        AtlasPlan none = AtlasPlan
            .plan(measure(true, 0, 256, false, 16), uniformWish(16, 48, 16, 1, 1, 0, five, 0, Integer.MAX_VALUE));
        assertEquals(120L, none.affordable);
        assertEquals(48, none.gradations);
        assertEquals(0, none.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.CEILING, none.stoppedBy);
        assertEquals(96L, none.plannedCells);

        AtlasPlan empty = AtlasPlan
            .plan(measure(true, 0, 256, false, 16), uniformWish(16, 48, 16, 1, 1, 0, new int[0], 0, Integer.MAX_VALUE));
        assertEquals(AtlasPlan.Limit.NONE, empty.stoppedBy, "nothing recorded, nothing cut");
        AtlasPlan missing = AtlasPlan
            .plan(measure(true, 0, 256, false, 16), uniformWish(16, 48, 16, 1, 1, 0, null, 0, Integer.MAX_VALUE));
        assertEquals(AtlasPlan.Limit.NONE, missing.stoppedBy, "no list is an empty one");
    }

    @Test
    @DisplayName("maxWearSprites cuts surfaces, not the ramp")
    void theSpriteCeilingCutsSurfacesNotTheRamp() {
        AtlasPlan plan = AtlasPlan.plan(
            measure(true, 10000, 16384, false, 16),
            uniformWish(16, 16, 16, 1, 17, 40, plainSets(600), 1024, 8192));
        assertEquals(381L, plan.affordable);
        assertEquals(16, plan.gradations);
        assertEquals(328L, plan.mandatorySprites);
        // 0.9.207 kept 495, because it did not count the 16 fringes and 40 walls against this ceiling.
        assertEquals(491, plan.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.SPRITES, plan.stoppedBy);
        assertEquals(8184L, plan.plannedSprites);
    }

    @Test
    @DisplayName("a stitch that was not measured plans cautiously")
    void anUnmeasuredStitchPlansCautiously() {
        AtlasPlan plan = AtlasPlan.plan(new AtlasPlan.Measure(false, 999999, 0, 0L, 0, false), defaultPackWish());
        assertEquals(131072L, plan.packCells);
        assertEquals(114688L, plan.room);
        assertEquals(44L, plan.affordable);
        assertEquals(44, plan.gradations);
        assertEquals(600, plan.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.NONE, plan.stoppedBy);
        assertEquals(112328L, plan.plannedCells);
    }

    @Test
    @DisplayName("a pack the card cannot hold still gets its fallbacks, and says so")
    void aPackTheCardCannotHoldKeepsItsFallbacks() {
        AtlasPlan plan = AtlasPlan.plan(
            measure(true, 20000, 4096, false, 32),
            uniformWish(32, 80, 16, 4, 17, 40, plainSets(600), 1024, 262144));
        assertEquals(80000L, plan.packCells);
        assertEquals(0L, plan.room);
        assertEquals(0L, plan.affordable);
        assertEquals(16, plan.gradations);
        assertEquals(4768L, plan.mandatoryCells);
        assertTrue(plan.mandatoryOverruns());
        assertEquals(0, plan.surfaceSetsKept);
        assertEquals(600, plan.surfacesDropped());
        assertEquals(AtlasPlan.Limit.ROOM, plan.stoppedBy);
        assertEquals(4768L, plan.plannedCells, "the fallbacks, fringe and walls, planned regardless");

        AtlasPlan bare = AtlasPlan
            .plan(measure(true, 20000, 4096, false, 32), uniformWish(32, 80, 16, 4, 17, 40, new int[0], 1024, 262144));
        assertEquals(AtlasPlan.Limit.NONE, bare.stoppedBy);
        assertTrue(bare.mandatoryOverruns());
    }

    @Test
    @DisplayName("fringes and side walls are priced at their own files and faces")
    void fringesAndWallsArePricedAtTheirOwnFilesAndFaces() {
        AtlasPlan oneWall = AtlasPlan.plan(
            measure(true, 0, 256, false, 16),
            uniformWish(16, 80, 16, 1, 1, 1, new int[] { 1 }, 1024, Integer.MAX_VALUE));
        assertEquals(79, oneWall.gradations);
        assertEquals(238L, oneWall.plannedCells);

        AtlasPlan noWall = AtlasPlan.plan(
            measure(true, 0, 256, false, 16),
            uniformWish(16, 80, 16, 1, 1, 0, new int[] { 1 }, 1024, Integer.MAX_VALUE));
        assertEquals(80, noWall.gradations);
        assertEquals(240L, noWall.plannedCells);

        AtlasPlan large = AtlasPlan.plan(
            measure(true, 0, 256, false, 16),
            uniformWish(32, 80, 16, 1, 1, 1, new int[] { 1 }, 1024, Integer.MAX_VALUE));
        assertEquals(19, large.gradations);
        assertEquals(232L, large.plannedCells);
        assertEquals(58L, large.plannedSprites);

        // Everything at sixteen pixels but one wall drawn at thirty-two: that wall is four slots, once.
        AtlasPlan largeWall = AtlasPlan.plan(
            new AtlasPlan.Measure(true, 0, 0, 0L, 256, false),
            new AtlasPlan.Wish(
                16,
                80,
                16,
                1,
                1,
                1L,
                1,
                1,
                4L,
                new int[] { 1 },
                new int[] { 1 },
                1024,
                Integer.MAX_VALUE));
        assertEquals(240L, largeWall.room, "one wall drawn at thirty-two pixels");
        assertEquals(3L, largeWall.pricedCells);
        assertEquals(78L, largeWall.affordable);
        assertEquals(78, largeWall.gradations);
        assertEquals(160L, largeWall.mandatoryCells);
        assertEquals(238L, largeWall.plannedCells);
        assertEquals(235L, largeWall.plannedSprites);
        assertEquals(1, largeWall.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.NONE, largeWall.stoppedBy);
    }

    @Test
    @DisplayName("the gate grants exactly what the plan kept")
    void theGateGrantsExactlyWhatWasKept() {
        AtlasPlan.SetGate gate = thirtyTwoPixelPack().gate();
        for (int i = 0; i < 785; i++) {
            assertTrue(gate.take(1, 4), "set " + i);
        }
        assertFalse(gate.take(1, 4), "the first set past the room");
        assertTrue(gate.shut());
        assertFalse(gate.take(1, 4), "and nothing after it");

        AtlasPlan lawn = lawnThirdInLine();
        AtlasPlan.SetGate walk = lawn.gate();
        assertTrue(walk.take(1, 1));
        assertTrue(walk.take(1, 1));
        assertFalse(walk.take(2, 2));
        assertTrue(walk.shut());

        AtlasPlan.SetGate otherWalk = lawn.gate();
        assertTrue(otherWalk.take(2, 2), "a fresh gate, walked differently");
        assertFalse(otherWalk.take(1, 1), "the appearances priced are used up");

        AtlasPlan.SetGate larger = lawn.gate();
        assertFalse(larger.take(1, 3), "a set priced at more slots than were kept is refused");

        AtlasPlan.SetGate slotsOnly = lawn.gate();
        assertTrue(slotsOnly.take(1, 1));
        assertFalse(slotsOnly.take(1, 2), "refused on slots alone");

        AtlasPlan.Census census = new AtlasPlan.Census();
        assertTrue(census.take(1, 1));
        assertTrue(census.take(2, 8));
        assertTrue(census.take(-3, -5));
        assertFalse(census.shut());
        int[] recorded = census.sets();
        int[] recordedCells = census.cells();
        assertArrayEquals(new int[] { 1, 2, 0 }, recorded, "a count below nought is recorded as nought");
        assertArrayEquals(new int[] { 1, 8, 0 }, recordedCells, "and so are slots below nought");
        census.take(1, 1);
        assertArrayEquals(new int[] { 1, 2, 0 }, recorded, "a later take does not reach an array already returned");
        assertArrayEquals(new int[] { 1, 8, 0 }, recordedCells, "on either count");
    }

    @Test
    @DisplayName("no plan ever breaks its own promises")
    void noPlanBreaksItsOwnPromises() {
        Random random = new Random(208);
        int[] maximums = { -1, 0, 2048, 4096, 8192, 16384 };
        int[] edges = { 16, 24, 32, 48, 64 };
        int[] ceilings = { 0, 64, 256, 1024, 4096 };
        int[] spriteCeilings = { 768, 8192, 32768, 262144 };

        for (int trial = 0; trial < 20000; trial++) {
            boolean taken = random.nextDouble() < 0.9d;
            int demand = random.nextInt(60001);
            int read = taken ? demand - random.nextInt(demand / 10 + 1) : 0;
            long readCells = read + 3L * random.nextInt(read + 1);
            int maximum = maximums[random.nextInt(maximums.length)];
            boolean anisotropic = random.nextDouble() < 0.3d;
            int edge = edges[random.nextInt(edges.length)];
            int wanted = 2 + random.nextInt(79);
            int rotations = 1 + random.nextInt(4);
            int fallbacks = 1 + random.nextInt(29);
            int sides = random.nextInt(200);
            int count = random.nextInt(3000);
            AtlasPlan.Census census = new AtlasPlan.Census();
            for (int i = 0; i < count; i++) {
                int appearances = random.nextDouble() < 0.1d ? 2 : 1;
                census.take(appearances, appearances * AtlasPlan.cellsFor(edges[random.nextInt(edges.length)]));
            }
            int[] sets = census.sets();
            int[] cells = census.cells();
            int ceiling = ceilings[random.nextInt(ceilings.length)];
            int spriteCeiling = spriteCeilings[random.nextInt(spriteCeilings.length)];
            int raise = 1 + random.nextInt(4999);
            int fileEdge = edges[random.nextInt(edges.length)];
            long fallbackCells = (long) fallbacks * AtlasPlan.cellsFor(fileEdge);
            int fringeCells = AtlasPlan.cellsFor(fileEdge);
            long sideCells = (long) sides * AtlasPlan.cellsFor(edges[random.nextInt(edges.length)]);
            int[] dearerCells = new int[cells.length];
            for (int i = 0; i < cells.length; i++) {
                dearerCells[i] = cells[i] + 1;
            }

            AtlasPlan.Measure measure = new AtlasPlan.Measure(taken, demand, read, readCells, maximum, anisotropic);
            AtlasPlan plan = AtlasPlan.plan(
                measure,
                new AtlasPlan.Wish(
                    edge,
                    wanted,
                    16,
                    rotations,
                    fallbacks,
                    fallbackCells,
                    fringeCells,
                    sides,
                    sideCells,
                    sets,
                    cells,
                    ceiling,
                    spriteCeiling));
            String where = "trial " + trial
                + ": measured "
                + taken
                + ", demand "
                + demand
                + ", read "
                + read
                + " at "
                + readCells
                + " slots, card "
                + maximum
                + ", anisotropic "
                + anisotropic
                + ", edge "
                + edge
                + ", file edge "
                + fileEdge
                + ", wanted "
                + wanted
                + ", rotations "
                + rotations
                + ", fallbacks "
                + fallbacks
                + ", sides "
                + sides
                + " at "
                + sideCells
                + " slots, sets "
                + count
                + ", ceiling "
                + ceiling
                + ", sprite ceiling "
                + spriteCeiling;
            int kept = plan.surfaceSetsKept;
            int floor = Math.min(16, wanted);
            long g = plan.gradations;
            long turns = rotations;

            assertTrue(plan.gradations >= floor && plan.gradations <= wanted, where);
            if (!plan.mandatoryOverruns()) {
                assertTrue(plan.plannedCells <= plan.room, where);
            }
            assertTrue(kept <= Math.min(count, ceiling), where);
            if (kept > 0) {
                assertTrue(plan.plannedSprites <= spriteCeiling, where);
            }
            if (plan.stoppedBy == AtlasPlan.Limit.ROOM) {
                assertEquals(floor, plan.gradations, where);
            }
            if (plan.floorHeld() && !plan.mandatoryOverruns()) {
                assertTrue(plan.stoppedBy == AtlasPlan.Limit.ROOM || plan.stoppedBy == AtlasPlan.Limit.SPRITES, where);
            }
            if (plan.stoppedBy == AtlasPlan.Limit.NONE) {
                assertEquals(count, kept, where);
            }
            assertEquals(plan.mandatoryCells + plan.surfaceCellsKept * g * turns, plan.plannedCells, where);
            assertEquals(plan.mandatorySprites + plan.surfaceAppearancesKept * g * turns, plan.plannedSprites, where);
            if (kept < count) {
                if (plan.stoppedBy == AtlasPlan.Limit.CEILING) {
                    assertTrue(kept >= ceiling, where);
                } else if (plan.stoppedBy == AtlasPlan.Limit.ROOM) {
                    assertTrue(plan.plannedCells + cells[kept] * g * turns > plan.room, where);
                } else {
                    assertEquals(AtlasPlan.Limit.SPRITES, plan.stoppedBy, where);
                    assertTrue(plan.plannedSprites + sets[kept] * g * turns > spriteCeiling, where);
                }
            }

            AtlasPlan.SetGate gate = plan.gate();
            int granted = 0;
            for (int i = 0; i < sets.length; i++) {
                if (gate.take(sets[i], cells[i])) granted++;
            }
            assertEquals(kept, granted, where);

            AtlasPlan.SetGate dearerWalk = plan.gate();
            int dearerGranted = 0;
            long dearerSlots = 0L;
            for (int i = 0; i < sets.length; i++) {
                if (dearerWalk.take(sets[i], dearerCells[i])) {
                    dearerGranted++;
                    dearerSlots += dearerCells[i];
                }
            }
            assertTrue(dearerGranted <= kept, where + ": a walk that answers dearer took more sets");
            assertTrue(dearerSlots <= plan.surfaceCellsKept, where + ": a walk that answers dearer took more slots");

            AtlasPlan unbounded = AtlasPlan.plan(
                measure,
                new AtlasPlan.Wish(
                    edge,
                    wanted,
                    16,
                    rotations,
                    fallbacks,
                    fallbackCells,
                    fringeCells,
                    sides,
                    sideCells,
                    sets,
                    cells,
                    ceiling,
                    Integer.MAX_VALUE));
            // The read count is held where it was, so both the slots read and the textures left unread rise.
            AtlasPlan crowded = AtlasPlan.plan(
                new AtlasPlan.Measure(taken, demand + raise, read, readCells + raise, maximum, anisotropic),
                new AtlasPlan.Wish(
                    edge,
                    wanted,
                    16,
                    rotations,
                    fallbacks,
                    fallbackCells,
                    fringeCells,
                    sides,
                    sideCells,
                    sets,
                    cells,
                    ceiling,
                    Integer.MAX_VALUE));
            AtlasPlan dearer = AtlasPlan.plan(
                measure,
                new AtlasPlan.Wish(
                    edge,
                    wanted,
                    16,
                    rotations,
                    fallbacks,
                    fallbackCells,
                    fringeCells,
                    sides,
                    sideCells,
                    sets,
                    dearerCells,
                    ceiling,
                    spriteCeiling));
            assertEquals(plan.gradations, unbounded.gradations, where + ": the sprite ceiling moved the ramp");
            assertTrue(crowded.gradations <= plan.gradations, where + ", raised by " + raise);
            assertTrue(crowded.surfaceSetsKept <= unbounded.surfaceSetsKept, where + ", raised by " + raise);
            assertTrue(dearer.gradations <= plan.gradations, where + ": dearer sets bought a finer ramp");
        }
    }

    @Test
    @DisplayName("a texture the game loads is priced at the frame it loads, grown while filtering is on")
    void eachSpriteIsPricedAtItsOwnEdge() {
        assertEquals(1, AtlasPlan.spriteCells(16, false));
        assertEquals(4, AtlasPlan.spriteCells(16, true), "a sixteen-pixel texture the game loads under filtering");
        assertEquals(9, AtlasPlan.spriteCells(32, true));
        assertEquals(16, AtlasPlan.spriteCells(64, false));
    }

    @Test
    @DisplayName("a pack is priced from its own files, and an unread texture at the average of the rest")
    void aPackIsPricedFromItsOwnFiles() {
        AtlasPlan.Measure someUnread = new AtlasPlan.Measure(true, 10000, 9000, 9451L, 16384, false);
        assertEquals(10502L, AtlasPlan.packCells(someUnread, 32), "9,451 read, and 1,000 at 1.05 slots rounded up");
        assertEquals(235258L, AtlasPlan.room(someUnread, 32));
        assertEquals(1000, someUnread.packUnread());

        assertEquals(
            10000L,
            AtlasPlan.packCells(new AtlasPlan.Measure(true, 10000, 9000, 9000L, 16384, false), 16),
            "no rounding up at sixteen");

        AtlasPlan.Measure noneRead = new AtlasPlan.Measure(true, 10000, 0, 0L, 16384, false);
        assertEquals(40000L, AtlasPlan.packCells(noneRead, 32), "not one file read: dirt and stone's edge");
        assertEquals(205760L, AtlasPlan.room(noneRead, 32));
        assertEquals(
            40000L,
            AtlasPlan.packCells(new AtlasPlan.Measure(true, 10000, 0, 0L, 16384, true), 16),
            "and grown where filtering widens the pack");

        assertEquals(0L, AtlasPlan.packCells(new AtlasPlan.Measure(true, 0, 0, 0L, 16384, false), 64));
        assertEquals(
            12000L,
            AtlasPlan.packCells(new AtlasPlan.Measure(true, 10000, 12000, 12000L, 16384, false), 16),
            "more read than gathered leaves none unread");
    }

    @Test
    @DisplayName("a thirty-two pixel vanilla-only pack over 25,000 sixteen-pixel modded textures keeps every face it has room for")
    void aVanillaOnlyPackOverAModpackKeepsItsFaces() {
        // 24,600 modded textures at sixteen pixels and 400 vanilla ones redrawn at thirty-two; sixty of the faces
        // are vanilla ones, the rest modded. The fallbacks, the fringe and the fallback wall read vanilla files,
        // at thirty-two; the other 39 walls are modded sides at sixteen.
        int[] cells = new int[1024];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i < 60 ? 4 : 1;
        }
        AtlasPlan plan = AtlasPlan.plan(
            new AtlasPlan.Measure(true, 25000, 25000, 26200L, 16384, false),
            new AtlasPlan.Wish(32, 80, 16, 4, 17, 68L, 4, 40, 43L, plainSets(1024), cells, 1024, 262144));
        assertEquals(26200L, plan.packCells);
        assertEquals(219560L, plan.room);
        assertEquals(1042L, plan.pricedAppearances);
        assertEquals(1276L, plan.pricedCells);
        assertEquals(5104L, plan.cellsPerGradation);
        assertEquals(43L, plan.affordable);
        assertEquals(43, plan.gradations);
        assertFalse(plan.floorHeld());
        assertEquals(408363L, plan.cellsWanted);
        assertEquals(3136L, plan.mandatorySprites);
        assertEquals(12427L, plan.mandatoryCells);
        assertEquals(1024, plan.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.NONE, plan.stoppedBy);
        assertEquals(1204L, plan.surfaceCellsKept);
        assertEquals(179264L, plan.plannedSprites);
        assertEquals(219515L, plan.plannedCells);

        // 0.9.209 as first written priced all of it at dirt and stone's thirty-two pixels.
        AtlasPlan before = AtlasPlan.plan(
            measure(true, 25000, 16384, false, 32),
            uniformWish(32, 80, 16, 4, 17, 40, plainSets(1024), 1024, 262144));
        assertEquals(145760L, before.room);
        assertEquals(16, before.gradations);
        assertEquals(550, before.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.ROOM, before.stoppedBy);
    }

    @Test
    @DisplayName("the same pack at sixty-four pixels")
    void theSamePackAtSixtyFourPixels() {
        int[] cells = new int[1024];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i < 60 ? 16 : 1;
        }
        AtlasPlan plan = AtlasPlan.plan(
            new AtlasPlan.Measure(true, 25000, 25000, 31000L, 16384, false),
            new AtlasPlan.Wish(64, 80, 16, 4, 17, 272L, 16, 40, 55L, plainSets(1024), cells, 1024, 262144));
        assertEquals(214760L, plan.room);
        assertEquals(2212L, plan.pricedCells);
        assertEquals(24L, plan.affordable);
        assertEquals(24, plan.gradations);
        assertEquals(707895L, plan.cellsWanted);
        assertEquals(1768L, plan.mandatorySprites);
        assertEquals(27703L, plan.mandatoryCells);
        assertEquals(1024, plan.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.NONE, plan.stoppedBy);
        assertEquals(100072L, plan.plannedSprites);
        assertEquals(212407L, plan.plannedCells);
        assertFalse(plan.mandatoryOverruns());

        AtlasPlan before = AtlasPlan.plan(
            measure(true, 25000, 16384, false, 64),
            uniformWish(64, 80, 16, 4, 17, 40, plainSets(1024), 1024, 262144));
        assertEquals(0L, before.room, "every texture priced at sixty-four pixels fills the square");
        assertEquals(19072L, before.mandatoryCells);
        assertTrue(before.mandatoryOverruns(), "the false 'may not fit at all'");
        assertEquals(0, before.surfaceSetsKept);
    }

    @Test
    @DisplayName("a mod's own thirty-two pixel faces over sixteen-pixel dirt no longer overrun")
    void aModsLargerFacesNoLongerOverrun() {
        int[] cells = new int[600];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i % 2 == 0 ? 4 : 1;
        }
        AtlasPlan plan = AtlasPlan.plan(
            new AtlasPlan.Measure(true, 10000, 10000, 16000L, 16384, false),
            new AtlasPlan.Wish(16, 80, 16, 4, 17, 17L, 1, 40, 40L, plainSets(600), cells, 1024, 262144));
        assertEquals(229760L, plan.room);
        assertEquals(1518L, plan.pricedCells);
        assertEquals(6072L, plan.cellsPerGradation);
        assertEquals(37L, plan.affordable);
        assertEquals(37, plan.gradations);
        assertEquals(485800L, plan.cellsWanted);
        assertEquals(2704L, plan.mandatorySprites);
        assertEquals(2704L, plan.mandatoryCells);
        assertEquals(600, plan.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.NONE, plan.stoppedBy);
        assertEquals(91504L, plan.plannedSprites);
        assertEquals(224704L, plan.plannedCells);

        // Priced at dirt's sixteen pixels, the plan drew eighty gradations and put 197,800 slots in the plan
        // where the sprites it registered came to the 485,800 below, far past the room.
        AtlasPlan before = AtlasPlan.plan(
            measure(true, 10000, 16384, false, 16),
            uniformWish(16, 80, 16, 4, 17, 40, plainSets(600), 1024, 262144));
        assertEquals(80, before.gradations);
        assertEquals(197800L, before.plannedCells);
        assertEquals(485800L, 80L * 4 * 1500 + 5800);
    }

    @Test
    @DisplayName("anisotropic filtering grows the pack's own textures and nothing this mod registers")
    void anisotropicFilteringGrowsOnlyThePack() {
        int[] sets = defaultPackSets();
        int[] cells = new int[sets.length];
        for (int i = 0; i < sets.length; i++) {
            cells[i] = sets[i];
        }
        // The pack's ten thousand textures grow to four slots each. Every face this mod wears, the 40 side sprites, the
        // fallbacks and the fringe stay at their sixteen pixels, because a face copied out of the atlas has its border
        // taken off and a sprite read from a file never had one.
        AtlasPlan plan = AtlasPlan.plan(
            new AtlasPlan.Measure(true, 10000, 10000, 40000L, 16384, true),
            new AtlasPlan.Wish(16, 80, 16, 4, 17, 17L, 1, 40, 40L, sets, cells, 1024, 262144));
        assertEquals(205760L, plan.room);
        assertEquals(638L, plan.pricedAppearances);
        assertEquals(638L, plan.pricedCells);
        assertEquals(2552L, plan.cellsPerGradation);
        assertEquals(80L, plan.affordable);
        assertEquals(80, plan.gradations);
        assertFalse(plan.floorHeld());
        assertEquals(204200L, plan.cellsWanted);
        assertEquals(5800L, plan.mandatorySprites);
        assertEquals(5800L, plan.mandatoryCells);
        assertEquals(600, plan.surfaceSetsKept);
        assertEquals(620L, plan.surfaceCellsKept);
        assertEquals(AtlasPlan.Limit.NONE, plan.stoppedBy);
        assertEquals(204200L, plan.plannedSprites);
        assertEquals(204200L, plan.plannedCells);

        AtlasPlan before = AtlasPlan.plan(
            measure(true, 10000, 16384, true, 16),
            uniformWish(16, 80, 16, 4, 17, 40, defaultPackSets(), 1024, 262144));
        assertEquals(80, before.gradations);
        assertEquals(204200L, before.plannedCells);
        assertEquals(AtlasPlan.Limit.NONE, before.stoppedBy);
        assertEquals(
            before.plannedCells,
            plan.plannedCells,
            "filtering on in both: the plan is the one that prices every wear sprite at its face's own sixteen pixels");
        assertEquals(
            before.gradations,
            plan.gradations,
            "filtering on in both: the plan is the one that prices every wear sprite at its face's own sixteen pixels");
    }

    @Test
    @DisplayName("a face drawn larger than the rest takes the room of the faces after it")
    void aLargerFaceTakesTheRoomOfTheFacesAfterIt() {
        AtlasPlan.Measure measure = new AtlasPlan.Measure(true, 120, 120, 120L, 256, false);
        AtlasPlan plan = AtlasPlan.plan(
            measure,
            new AtlasPlan.Wish(
                32,
                16,
                16,
                1,
                1,
                1L,
                1,
                0,
                0L,
                new int[] { 1, 1, 1 },
                new int[] { 1, 4, 1 },
                1024,
                Integer.MAX_VALUE));
        assertEquals(120L, plan.room);
        assertEquals(8L, plan.pricedCells);
        assertEquals(15L, plan.affordable);
        assertEquals(16, plan.gradations);
        assertTrue(plan.floorHeld());
        assertEquals(32L, plan.mandatoryCells);
        assertEquals(2, plan.surfaceSetsKept, "the second face is kept, and the third has no room left");
        assertEquals(AtlasPlan.Limit.ROOM, plan.stoppedBy);
        assertEquals(112L, plan.plannedCells);
        assertEquals(64L, plan.plannedSprites);

        AtlasPlan even = AtlasPlan.plan(
            measure,
            new AtlasPlan.Wish(
                32,
                16,
                16,
                1,
                1,
                1L,
                1,
                0,
                0L,
                new int[] { 1, 1, 1 },
                new int[] { 1, 1, 1 },
                1024,
                Integer.MAX_VALUE));
        assertEquals(5L, even.pricedCells);
        assertEquals(24L, even.affordable);
        assertEquals(3, even.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.NONE, even.stoppedBy);
        assertEquals(80L, even.plannedCells);
    }

    @Test
    @DisplayName("what went to the stitcher is counted as the stitcher rounds it")
    void whatWentToTheStitcherIsRoundedAsTheStitcherRoundsIt() {
        assertEquals(16, AtlasPlan.mipmapDimension(16, 4));
        assertEquals(32, AtlasPlan.mipmapDimension(17, 4));
        assertEquals(32, AtlasPlan.mipmapDimension(24, 4));
        assertEquals(48, AtlasPlan.mipmapDimension(33, 4));
        assertEquals(16, AtlasPlan.mipmapDimension(8, 4));
        assertEquals(0, AtlasPlan.mipmapDimension(0, 4));
        assertEquals(24, AtlasPlan.mipmapDimension(24, 0));
        assertEquals(28, AtlasPlan.mipmapDimension(26, 2));
        assertEquals(32, AtlasPlan.mipmapDimension(16, 5), "a hand-edited mipmapLevels of five");

        AtlasPlan.Stitched stitched = new AtlasPlan.Stitched(4);
        stitched.add(true, 32, 32);
        stitched.add(false, 24, 24);
        stitched.add(false, 8, 8);
        stitched.add(true, 0, 0);
        stitched.add(false, 16, 0);
        assertEquals(1, stitched.ownSprites());
        assertEquals(4L, stitched.ownCells());
        assertEquals(2, stitched.otherSprites(), "a sprite never loaded is not counted");
        assertEquals(5L, stitched.otherCells());

        AtlasPlan.Stitched unrounded = new AtlasPlan.Stitched(0);
        unrounded.add(false, 24, 24);
        unrounded.add(false, 24, 24);
        assertEquals(5L, unrounded.otherCells(), "rounded once and not three and three");

        assertEquals(0, new AtlasPlan.Stitched(-3).levels());
        assertEquals(15, new AtlasPlan.Stitched(20).levels());
    }

    @Test
    @DisplayName("a sprite stitched at the edge it was priced at takes exactly its price")
    void aSpriteStitchedAtItsPricedEdgeTakesItsPrice() {
        for (int edge = 1; edge <= 128; edge++) {
            AtlasPlan.Stitched stitched = new AtlasPlan.Stitched(4);
            stitched.add(true, edge, edge);
            assertEquals(
                (long) AtlasPlan.cellsFor(edge),
                stitched.ownCells(),
                "edge " + edge + " at four mipmap levels");
        }
        for (int edge : new int[] { 24, 40 }) {
            AtlasPlan.Stitched unrounded = new AtlasPlan.Stitched(0);
            unrounded.add(true, edge, edge);
            assertTrue(unrounded.ownCells() <= AtlasPlan.cellsFor(edge), "edge " + edge + " with mipmapping off");
        }
    }

    @Test
    @DisplayName("a stitch warns once the whole of it spends the reserve, whichever half ran past its price")
    void aStitchWarnsOnceTheWholeSpendsTheReserve() {
        AtlasPlan plan = AtlasPlan.plan(measure(true, 10000, 16384, false, 16), defaultPackWish());
        assertEquals(204200L, plan.plannedCells);
        assertEquals(10000L, plan.packCells);
        assertEquals(16384L, plan.reserveCells);
        assertEquals(262144L, plan.squareCells);
        assertEquals(31560L, plan.room - plan.plannedCells, "room the plan left unused");
        // The reserve is first touched past 262,144 - 16,384 = 245,760 slots in all.

        AtlasPlan.Stitched asPriced = strips(204200L, 10000L);
        assertEquals(0L, plan.ownPast(asPriced));
        assertEquals(0L, plan.restPast(asPriced));
        assertFalse(plan.overdrawn(asPriced));
        assertEquals(0L, plan.reserveSpent(asPriced));

        AtlasPlan.Stitched absorbed = strips(214000L, 16000L);
        assertEquals(9800L, plan.ownPast(absorbed));
        assertEquals(6000L, plan.restPast(absorbed));
        assertFalse(plan.overdrawn(absorbed), "230,000 in all, 15,760 short of the reserve");
        assertEquals(0L, plan.reserveSpent(absorbed));

        AtlasPlan.Stitched past = strips(214000L, 17000L);
        assertEquals(9800L, plan.ownPast(past));
        assertEquals(7000L, plan.restPast(past));
        assertFalse(
            plan.overdrawn(past),
            "16,800 past the two prices, more than the reserve, but the 31,560 the plan left unused absorb it");
        assertEquals(0L, plan.reserveSpent(past));

        AtlasPlan.Stitched restOnly = strips(190000L, 30000L);
        assertEquals(0L, plan.ownPast(restOnly), "a half that came in under its price is past it by none");
        assertEquals(20000L, plan.restPast(restOnly));
        assertFalse(
            plan.overdrawn(restOnly),
            "14,200 under one price and 20,000 past the other come to 220,000 in all, 25,760 short of the reserve");
        assertEquals(0L, plan.reserveSpent(restOnly));

        AtlasPlan.Stitched atTheLine = strips(220000L, 25760L);
        assertFalse(plan.overdrawn(atTheLine), "245,760 in all, the reserve untouched");
        assertEquals(0L, plan.reserveSpent(atTheLine));
        AtlasPlan.Stitched overTheLine = strips(220000L, 25761L);
        assertTrue(plan.overdrawn(overTheLine), "one slot into the reserve");
        assertEquals(1L, plan.reserveSpent(overTheLine));

        AtlasPlan.Stitched neitherAlone = strips(220000L, 26000L);
        assertEquals(15800L, plan.ownPast(neitherAlone));
        assertEquals(16000L, plan.restPast(neitherAlone));
        assertTrue(
            plan.ownPast(neitherAlone) < plan.reserveCells && plan.restPast(neitherAlone) < plan.reserveCells,
            "neither half past its price by the reserve");
        assertTrue(plan.overdrawn(neitherAlone), "and 246,000 in all");
        assertEquals(240L, plan.reserveSpent(neitherAlone));

        AtlasPlan.Stitched full = strips(220000L, 42144L);
        assertTrue(plan.overdrawn(full));
        assertEquals(
            16384L,
            plan.reserveSpent(full),
            "262,144 in all: every slot held back spent, the square exactly full");
        AtlasPlan.Stitched pastSquare = strips(220000L, 42145L);
        assertTrue(plan.overdrawn(pastSquare));
        assertEquals(
            16385L,
            plan.reserveSpent(pastSquare),
            "262,145 in all: more than the reserve holds only once past the square");

        AtlasPlan crammed = AtlasPlan.plan(
            measure(true, 20000, 4096, false, 32),
            uniformWish(32, 80, 16, 4, 17, 40, plainSets(600), 1024, 262144));
        assertEquals(65536L, crammed.squareCells);
        assertEquals(4096L, crammed.reserveCells);
        assertEquals(4768L, crammed.plannedCells);
        assertEquals(80000L, crammed.packCells);
        AtlasPlan.Stitched overSquare = strips(4768L, 80000L);
        assertEquals(0L, crammed.ownPast(overSquare));
        assertEquals(0L, crammed.restPast(overSquare));
        assertTrue(crammed.overdrawn(overSquare), "exactly as priced, and the prices alone past the square");
        assertEquals(
            23328L,
            crammed.reserveSpent(overSquare),
            "84,768 in all against 61,440: the 4,096 held back and 19,232 past the square");
    }

    @Test
    @DisplayName("a PNG header gives its width and nothing else does")
    void aPngHeaderGivesItsWidth() {
        byte[] header = bytes(
            0x89,
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
            0x00,
            0x00,
            0x0D,
            0x49,
            0x48,
            0x44,
            0x52,
            0x00,
            0x00,
            0x00,
            0x20,
            0x00,
            0x00,
            0x00,
            0x20);
        assertEquals(24, AtlasPlan.HEADER_BYTES);
        assertEquals(32, AtlasPlan.pngWidth(header));

        byte[] tall = header.clone();
        tall[19] = 0x10;
        tall[22] = 0x04;
        tall[23] = 0x00;
        assertEquals(16, AtlasPlan.pngWidth(tall), "the width, not the height");

        byte[] notPng = header.clone();
        notPng[0] = (byte) 0xFF;
        assertEquals(0, AtlasPlan.pngWidth(notPng));

        assertEquals(0, AtlasPlan.pngWidth(Arrays.copyOf(header, 23)), "cut short");

        byte[] otherChunk = header.clone();
        otherChunk[15] = 'X';
        assertEquals(0, AtlasPlan.pngWidth(otherChunk), "IHDX");

        byte[] zero = header.clone();
        zero[19] = 0;
        assertEquals(0, AtlasPlan.pngWidth(zero));

        byte[] negative = header.clone();
        negative[16] = (byte) 0x80;
        negative[19] = 0;
        assertEquals(0, AtlasPlan.pngWidth(negative), "a width past what an int holds");

        byte[] tooWide = header.clone();
        tooWide[17] = 0x01;
        tooWide[19] = 0;
        assertEquals(0, AtlasPlan.pngWidth(tooWide), "65,536 pixels, which no card addresses");

        byte[] widest = header.clone();
        widest[18] = (byte) 0x80;
        widest[19] = 0;
        assertEquals(32768, AtlasPlan.pngWidth(widest));

        assertEquals(0, AtlasPlan.pngWidth(null));
    }

    @Test
    @DisplayName("a file's width is read whatever image format is saved under its name, and a PNG's from its header alone")
    void aFileWidthIsReadWhateverTheFormat() throws IOException {
        assertEquals(40, AtlasPlan.fileWidth(new ByteArrayInputStream(encoded("png", 40, 20))), "a PNG");
        assertEquals(40, AtlasPlan.fileWidth(new ByteArrayInputStream(encoded("jpg", 40, 20))), "JPEG data");
        assertEquals(40, AtlasPlan.fileWidth(new ByteArrayInputStream(encoded("bmp", 40, 20))), "BMP data");
        assertEquals(40, AtlasPlan.fileWidth(new ByteArrayInputStream(encoded("gif", 40, 20))), "GIF data");

        // Past its twenty-four bytes this stream refuses to be read, so a width back means none of the rest was.
        byte[] png = encoded("png", 48, 16);
        InputStream headerOnly = new SequenceInputStream(
            new ByteArrayInputStream(Arrays.copyOf(png, AtlasPlan.HEADER_BYTES)),
            new InputStream() {

                @Override
                public int read() throws IOException {
                    throw new IOException("read past the header");
                }
            });
        assertEquals(48, AtlasPlan.fileWidth(headerOnly), "a PNG costs its header and nothing more");

        byte[] noise = new byte[64];
        Arrays.fill(noise, (byte) 'x');
        assertEquals(0, AtlasPlan.fileWidth(new ByteArrayInputStream(noise)), "bytes no image reader recognises");
        assertEquals(0, AtlasPlan.fileWidth(new ByteArrayInputStream(Arrays.copyOf(png, 10))), "a PNG cut off early");
        assertEquals(0, AtlasPlan.fileWidth(new ByteArrayInputStream(new byte[0])), "an empty file");
        assertEquals(0, AtlasPlan.fileWidth(null));
    }

    /** A black image of this size in this format, as the bytes a file holding it would carry. */
    private static byte[] encoded(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out), "this runtime writes " + format);
        return out.toByteArray();
    }

    @Test
    @DisplayName("a set with no recorded slots is priced at the wear edge, and the arrays are the wish's own")
    void aSetWithNoSlotsIsPricedAtTheWearEdge() {
        AtlasPlan.Wish unrecorded = new AtlasPlan.Wish(
            32,
            16,
            16,
            1,
            1,
            4L,
            4,
            0,
            0L,
            new int[] { 1, 2 },
            new int[] { 1 },
            1024,
            Integer.MAX_VALUE);
        assertEquals(1L, unrecorded.surfaceSetCells(0));
        assertEquals(8L, unrecorded.surfaceSetCells(1), "two appearances at thirty-two pixels");

        int[] sets = { 1, 1, 1 };
        int[] cells = { 1, 4, 1 };
        AtlasPlan.Measure measure = new AtlasPlan.Measure(true, 120, 120, 120L, 256, false);
        AtlasPlan.Wish wish = new AtlasPlan.Wish(32, 16, 16, 1, 1, 1L, 1, 0, 0L, sets, cells, 1024, Integer.MAX_VALUE);
        AtlasPlan first = AtlasPlan.plan(measure, wish);
        assertEquals(112L, first.plannedCells);
        Arrays.fill(sets, 99);
        Arrays.fill(cells, 99);
        AtlasPlan again = AtlasPlan.plan(measure, wish);
        assertEquals(first.plannedCells, again.plannedCells, "the caller's arrays changed after the wish was made");
        assertEquals(1, wish.surfaceSet(0));
        assertEquals(4L, wish.surfaceSetCells(1));
    }

    @Test
    @DisplayName("advice is worked out, not argued")
    void adviceIsWorkedOutNotArgued() {
        AtlasPlan capped = AtlasPlan.plan(
            measure(true, 10000, 16384, false, 32),
            uniformWish(32, 80, 16, 4, 17, 40, plainSets(983), 1024, 40000));
        assertEquals(AtlasPlan.Limit.SPRITES, capped.stoppedBy);
        assertEquals(606, capped.surfaceSetsKept);
        assertEquals(39976L, capped.plannedSprites);
        assertEquals(16, capped.gradations);
        assertTrue(capped.floorHeld());
        AtlasPlan cappedFewer = capped.fewerRotations();
        assertEquals(2, cappedFewer.wish.rotations, "not one, which spends the rotations saved on the ramp");
        assertEquals(781, cappedFewer.surfaceSetsKept);
        assertEquals(765, capped.planAt(80, 3).surfaceSetsKept);
        assertEquals(765, capped.planAt(80, 1).surfaceSetsKept);

        AtlasPlan ramped = AtlasPlan.plan(
            measure(true, 10000, 16384, false, 32),
            uniformWish(32, 80, 16, 4, 17, 23, plainSets(583), 1024, 32768));
        assertEquals(21, ramped.gradations);
        assertEquals(371, ramped.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.SPRITES, ramped.stoppedBy);
        AtlasPlan rampedFewer = ramped.fewerRotations();
        assertEquals(1, rampedFewer.wish.rotations);
        assertEquals(391, rampedFewer.surfaceSetsKept);
        assertEquals(80, rampedFewer.gradations);
        assertEquals(493, ramped.planAt(16, 4).surfaceSetsKept, "the floor fits more faces under the ceiling");
        assertEquals(
            371,
            ramped.planAt(40, 4).surfaceSetsKept,
            "a count between the drawn and the asked changes nothing");
        assertEquals(80, ramped.withSettings(80, 4, 0, 32768).gradations, "no face priced, the ramp asked for");

        AtlasPlan starved = AtlasPlan.plan(
            measure(true, 10000, 16384, false, 16),
            uniformWish(16, 80, 16, 4, 17, 40, defaultPackSets(), 1024, 2048));
        assertEquals(AtlasPlan.Limit.SPRITES, starved.stoppedBy);
        assertEquals(0, starved.surfaceSetsKept);
        assertEquals(5800L, starved.mandatorySprites);
        long firstIn = starved.mandatorySprites
            + (long) starved.wish.surfaceSet(0) * starved.gradations * starved.wish.rotations;
        assertEquals(6440L, firstIn, "the ceiling that lets the first face, a lawn, in");
        assertEquals(1, starved.withSettings(80, 4, 1024, (int) firstIn).surfaceSetsKept);
        assertEquals(0, starved.withSettings(80, 4, 1024, (int) firstIn - 1).surfaceSetsKept);
        AtlasPlan starvedFewer = starved.fewerRotations();
        assertEquals(1, starvedFewer.wish.rotations);
        assertEquals(6, starvedFewer.surfaceSetsKept);
        assertEquals(12, starved.planAt(16, 4).surfaceSetsKept);

        assertNull(lawnThirdInLine().fewerRotations(), "one rotation has no fewer");

        AtlasPlan plan = thirtyTwoPixelPack();
        AtlasPlan same = plan.planAt(80, 4);
        assertEquals(plan.gradations, same.gradations);
        assertEquals(plan.surfaceSetsKept, same.surfaceSetsKept);
        assertEquals(plan.plannedCells, same.plannedCells);
        assertEquals(plan.plannedSprites, same.plannedSprites);
        assertEquals(plan.stoppedBy, same.stoppedBy);
        assertEquals(plan.room, same.room);

        AtlasPlan three = AtlasPlan.plan(
            measure(true, 0, 256, false, 16),
            uniformWish(16, 48, 16, 1, 1, 0, new int[] { 1, 1, 1, 1, 1 }, 3, Integer.MAX_VALUE));
        AtlasPlan raised = three.withSettings(48, 1, 5, Integer.MAX_VALUE);
        assertEquals(5, raised.surfaceSetsKept);
        assertEquals(34, raised.gradations, "the two faces let in are paid for in gradations");
        assertEquals(238L, raised.plannedCells);
        assertEquals(AtlasPlan.Limit.NONE, raised.stoppedBy);

        AtlasPlan inert = AtlasPlan.plan(
            new AtlasPlan.Measure(true, 161, 161, 161L, 256, false),
            new AtlasPlan.Wish(
                16,
                16,
                16,
                1,
                1,
                1L,
                1,
                0,
                0L,
                new int[] { 1, 1, 1 },
                new int[] { 1, 1, 1 },
                2,
                Integer.MAX_VALUE));
        assertEquals(79L, inert.room);
        assertEquals(16, inert.gradations);
        assertEquals(2, inert.surfaceSetsKept);
        assertEquals(AtlasPlan.Limit.CEILING, inert.stoppedBy);
        AtlasPlan inertRaised = inert.withSettings(16, 1, 3, Integer.MAX_VALUE);
        assertEquals(2, inertRaised.surfaceSetsKept, "a raised ceiling the room cannot pay for keeps nothing more");
        assertEquals(AtlasPlan.Limit.ROOM, inertRaised.stoppedBy);
        assertTrue(inertRaised.floorHeld());
    }
}
