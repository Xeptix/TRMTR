package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What the inspection reply says about one record, and what it keeps back, run without the game.
 *
 * <p>
 * The fault it guards against would say nothing: a leaf's trample tally, or ground short of its first gradation,
 * handing a tooltip a wear and a threshold that read exactly like progress toward a gradation the block does not
 * have. No exception, no log line, only a figure that is not true. The speeds and the reinforcement settings are
 * statics on the config and two of these tests move them, so all four are put back before and after every test,
 * for the same reason TrampleTallyTest gives: one test leaving a speed moved would fail whichever ran next.
 */
class RecordReadoutTest {

    @BeforeEach
    void startAtShippedSettings() {
        restoreDefaults();
    }

    @AfterEach
    void restoreDefaults() {
        TrmtConfig.erosionSpeed = 1.0d;
        TrmtConfig.globalSpeed = 1.0d;
        TrmtConfig.reinforceEnabled = true;
        TrmtConfig.reinforceWearFactor = 1.0d;
    }

    /** Everything a record with nothing drawn keeps back, asked in one place so no test can leave a figure out. */
    private static void assertWithheld(RecordReadout readout, String what) {
        assertEquals(0f, readout.wear, 0f, what + ": no wear goes out");
        assertEquals(0f, readout.threshold, 0f, what + ": nor a threshold");
        assertEquals(RecordReadout.WITHHELD, readout.untouchedSeconds, what + ": nor how long it was left alone");
        assertEquals(RecordReadout.WITHHELD, readout.recoverySeconds, what + ": nor a recovery");
        assertEquals(RecordReadout.WITHHELD, readout.chainIndex, what + ": nor a place on the run");
        assertFalse(RecordReadout.tellsProgress(readout.threshold), what + ": so it tells no progress");
        assertEquals(0f, RecordReadout.progress(readout.wear, readout.threshold), 0f, what + ": and shows none");
    }

    @Test
    @DisplayName("a reinforced leaf's tally shows its reinforcement and none of its wear")
    void reinforcedLeafTally() {
        ErosionEntry tally = TrampleTally.start(SurfaceFamily.LEAVES, 16f, 24f, 0.5f, 100);
        tally.recordStep(12f, 150);
        tally.setReinforce(1);
        RecordReadout readout = RecordReadout.of(tally, 50, -1, -1, 0);
        assertWithheld(readout, "a reinforced leaf");
        assertEquals(1, readout.reinforce, "its reinforcement is told");
        assertEquals(0, readout.ward, "and it has no ward");
        assertEquals(0, readout.chainLength, "a leaf has no run");
        assertEquals(12f, tally.getWear(), 0f, "and reading the tally leaves its count where it was");
    }

    @Test
    @DisplayName("a warded plant's tally is withheld even when the caller passes a place and a recovery")
    void wardedPlantTally() {
        ErosionEntry tally = TrampleTally.start(SurfaceFamily.VEGETATION, 16f, 24f, 0.5f, 0);
        tally.recordStep(8f, 10);
        tally.setWard(3);
        RecordReadout readout = RecordReadout.of(tally, 90, 1200, 5, 0);
        assertWithheld(readout, "a warded plant");
        assertEquals(3, readout.ward, "both halves of its ward are told");
        assertEquals(0, readout.reinforce, "and it has no reinforcement");
    }

    @Test
    @DisplayName("ground walked short of its first gradation, then reinforced, shows no progress")
    void walkedShortThenReinforced() {
        ErosionEntry ground = new ErosionEntry(SurfaceFamily.GRASS, 20f, 0);
        ground.recordStep(15f, 10);
        ground.setReinforce(2);
        RecordReadout readout = RecordReadout.of(ground, 30, -1, -1, 80);
        assertWithheld(readout, "grass walked short of a gradation");
        assertEquals(2, readout.reinforce, "its reinforcement is told");
        assertEquals(80, readout.chainLength, "and the run its block belongs to, which is a fact about the block");
    }

    @Test
    @DisplayName("ground reinforced before anybody walked it does not report the stand-in threshold")
    void reinforcedBeforeAnyStep() {
        ErosionEntry ground = new ErosionEntry(SurfaceFamily.GRASS, ErosionEntry.UNDRAWN_THRESHOLD, 0);
        ground.setReinforce(1);
        assertEquals(
            2f,
            ground.effectiveThreshold(),
            0f,
            "the stand-in, doubled by one reinforcement, is what went out");
        RecordReadout readout = RecordReadout.of(ground, 0, -1, -1, 80);
        assertWithheld(readout, "grass reinforced before a step");
        assertEquals(80, readout.chainLength, "the run is still told");
    }

    @Test
    @DisplayName("a protected record stripped by the sweep is withheld")
    void strippedBySweep() {
        ErosionEntry kept = new ErosionEntry(SurfaceFamily.DIRT, 1f, 0);
        kept.setAppearance(SurfaceFamily.DIRT, -1, 0f);
        kept.setWard(1);
        RecordReadout readout = RecordReadout.of(kept, 0, -1, -1, 0);
        assertWithheld(readout, "a ward kept on a block that stopped being a surface");
        assertEquals(1, readout.ward, "its ward is told");
    }

    @Test
    @DisplayName("worn ground is shown in full")
    void wornGroundInFull() {
        TrmtConfig.erosionSpeed = 4.0d;
        ErosionEntry worn = new ErosionEntry(SurfaceFamily.GRASS, 20f, 0);
        worn.setAppearance(SurfaceFamily.GRASS, 3, 20f);
        worn.recordStep(2.5f, 10);
        worn.setReinforce(1);
        worn.setWard(1);
        RecordReadout readout = RecordReadout.of(worn, 70, 1200, 12, 80);
        assertEquals(2.5f, readout.wear, 0f, "the wear");
        assertEquals(
            10f,
            readout.threshold,
            0.0001f,
            "twenty at four times the speed is five, doubled by one reinforcement");
        assertEquals(70, readout.untouchedSeconds, "how long it was left alone");
        assertEquals(1200, readout.recoverySeconds, "the recovery");
        assertEquals(12, readout.chainIndex, "the place on the run");
        assertEquals(80, readout.chainLength, "the run");
        assertEquals(1, readout.reinforce, "the reinforcement");
        assertEquals(1, readout.ward, "the ward");
        assertTrue(RecordReadout.tellsProgress(readout.threshold), "a drawn threshold tells progress");
        assertEquals(0.25f, RecordReadout.progress(readout.wear, readout.threshold), 0.0001f, "a quarter of the way");
    }

    @Test
    @DisplayName("a threshold made tiny by the speeds still tells progress")
    void tinyThresholdTellsProgress() {
        TrmtConfig.erosionSpeed = 16.0d;
        TrmtConfig.globalSpeed = 16.0d;
        ErosionEntry stone = new ErosionEntry(SurfaceFamily.STONE, 0.01f, 0);
        stone.setAppearance(SurfaceFamily.STONE, 0, 0.01f);
        RecordReadout readout = RecordReadout.of(stone, 0, -1, 0, 40);
        assertTrue(
            readout.threshold > 0f && readout.threshold < 0.0001f,
            "a hundredth at both speeds sixteen is about four hundred-thousandths, not " + readout.threshold);
        assertTrue(RecordReadout.tellsProgress(readout.threshold), "which is still a threshold");
        assertEquals(0f, RecordReadout.progress(readout.wear, readout.threshold), 0f, "and with no wear, no progress");
    }

    @Test
    @DisplayName("progress stays between nought and one")
    void progressIsBounded() {
        assertEquals(0.5f, RecordReadout.progress(10f, 20f), 0f, "half way");
        assertEquals(1f, RecordReadout.progress(30f, 20f), 0f, "past the threshold is all the way and no further");
        assertEquals(0f, RecordReadout.progress(5f, 0f), 0f, "a threshold of nought tells nothing");
        assertEquals(0f, RecordReadout.progress(5f, Float.NaN), 0f, "nor does one that is not a number");
        assertEquals(0f, RecordReadout.progress(Float.NaN, 20f), 0f, "wear that is not a number is no progress");
        assertEquals(0f, RecordReadout.progress(-1f, 20f), 0f, "and nor is wear below nought");
    }

    @Test
    @DisplayName("a place on the run past 127 survives the trip, and only minus one is withheld")
    void placesPast127SurviveTheWire() {
        for (int place = 0; place <= 254; place++) {
            // Written as one byte and read back signed, which is what PacketInspectResult does with it.
            byte onTheWire = (byte) place;
            assertEquals(place, RecordReadout.indexFromWire(onTheWire), "place " + place);
        }
        assertEquals(RecordReadout.WITHHELD, RecordReadout.indexFromWire(-1), "minus one is withheld");
        assertEquals(128, RecordReadout.indexFromWire((byte) 128), "the first place the signed read turned negative");
        assertEquals(
            RecordReadout.WITHHELD,
            RecordReadout.indexFromWire((byte) 255),
            "place 255 cannot be told from withheld, and only the last step of a run of exactly 256 has it");
    }
}
