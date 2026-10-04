package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * That reinforcing a block holds it against feet, not only against explosions.
 *
 * <p>
 * The multiplier is applied where the threshold is read rather than where it is stored, so these
 * are all questions about one position changing under its own reinforcement without ever being
 * given a new threshold - which is the whole reason for putting it there.
 */
class ReinforcementWearTest {

    private static final float STEP = 20f;

    @AfterEach
    void restoreDefaults() {
        TrmtConfig.reinforceEnabled = true;
        TrmtConfig.reinforceWearFactor = 1.0d;
    }

    private static ErosionEntry fresh() {
        return new ErosionEntry(SurfaceFamily.GRASS, STEP, 0);
    }

    @Test
    @DisplayName("each level adds a whole extra lifetime, so three levels is four times the traffic")
    void everyLevelAddsALifetime() {
        ErosionEntry entry = fresh();
        assertEquals(STEP, entry.effectiveThreshold(), 0.001f, "unreinforced ground is unchanged");

        for (int level = 1; level <= 3; level++) {
            entry.setReinforce(level);
            assertEquals(
                STEP * (level + 1),
                entry.effectiveThreshold(),
                0.001f,
                "level " + level + " must cost " + (level + 1) + " times the crossings");
        }
    }

    @Test
    @DisplayName("the stored draw is left alone, so the save still holds the family's own number")
    void storedThresholdIsUntouched() {
        ErosionEntry entry = fresh();
        entry.setReinforce(3);
        assertEquals(STEP, entry.getThreshold(), 0.001f, "what is written to disk is the honest draw");
    }

    @Test
    @DisplayName("reinforcing part-worn ground sets the finish line back rather than leaving it")
    void reinforcingMovesTheFinishLine() {
        ErosionEntry entry = fresh();
        entry.recordStep(STEP - 0.5f, 0);
        assertFalse(entry.thresholdReached(), "half a step short of moving");

        entry.setReinforce(1);
        assertFalse(entry.thresholdReached(), "and now a whole lifetime short of it");

        entry.recordStep(STEP, 0);
        assertFalse(entry.thresholdReached(), "the traffic that would have moved it twice over is not enough");

        entry.recordStep(1f, 0);
        assertTrue(entry.thresholdReached(), "but twice the lifetime is");
    }

    @Test
    @DisplayName("taking the reinforcement off hands back the wear that was banked under it")
    void unreinforcingReleasesBankedWear() {
        ErosionEntry entry = fresh();
        entry.setReinforce(3);
        entry.recordStep(STEP * 2, 0);
        assertFalse(entry.thresholdReached(), "half way along a fourfold threshold");

        entry.setReinforce(0);
        assertTrue(entry.thresholdReached(), "and well past an ordinary one the moment it is unreinforced");
    }

    @Test
    @DisplayName("a factor of zero, or the feature switched off, leaves wear exactly as it was")
    void theFeatureCanBeStoodDown() {
        ErosionEntry entry = fresh();
        entry.setReinforce(3);

        TrmtConfig.reinforceWearFactor = 0d;
        assertEquals(STEP, entry.effectiveThreshold(), 0.001f, "no extra lifetimes means no extra traffic");

        TrmtConfig.reinforceWearFactor = 1d;
        TrmtConfig.reinforceEnabled = false;
        assertEquals(STEP, entry.effectiveThreshold(), 0.001f, "and neither does reinforcement not existing");
    }

    @Test
    @DisplayName("the configured speed and the reinforcement compound rather than one winning")
    void speedAndReinforcementCompound() {
        float plain = fresh().effectiveThreshold();
        ErosionEntry entry = fresh();
        entry.setReinforce(1);
        assertEquals(plain * 2f, entry.effectiveThreshold(), 0.001f, "whatever the speed setting is worth, doubled");
    }
}
