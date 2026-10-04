package com.trmtgtnh.compat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the quest chapter teaches, under every combination of the three switches.
 *
 * <p>
 * Every failure here is silent in game. A quest for a book nothing makes still draws and still holds back the
 * Wayfarer after it; a prize naming a switched-off mode is still handed out; a stamp that misses a switch still calls
 * a stale chapter current. So each is checked against all eight combinations rather than the one a pack happens to
 * ship.
 */
class QuestLessonsTest {

    private static final int R = 250;

    private static final int W = 249;

    private static final int L = 248;

    /** 4 reinforcing, 2 warding, 1 path light - the order of their quests' numbers. */
    private static QuestLessons combination(int mask) {
        return new QuestLessons(
            QuestLessons.taught((mask & 4) != 0, R),
            QuestLessons.taught((mask & 2) != 0, W),
            QuestLessons.taught((mask & 1) != 0, L));
    }

    @Test
    @DisplayName("a lesson is taught only with its switch on and an id to sit at")
    void bothTheSwitchAndTheId() {
        assertEquals(R, QuestLessons.taught(true, R));
        assertEquals(QuestLessons.NOT_TAUGHT, QuestLessons.taught(false, R));
        assertEquals(QuestLessons.NOT_TAUGHT, QuestLessons.taught(true, -1));
        assertEquals(QuestLessons.NOT_TAUGHT, QuestLessons.taught(false, -1));
        assertEquals(0, QuestLessons.taught(true, 0), "nought is a real id");
        assertEquals(
            "/-1/-1/-1",
            new QuestLessons(-7, -2, Integer.MIN_VALUE).stamp(),
            "every untaught lesson reads alike");
    }

    @Test
    @DisplayName("the stamp tells every combination apart, compared the way isCurrent compares it")
    void stampsNeverMatchAcrossCombinations() {
        assertEquals("/250/249/248", combination(7).stamp(), "all taught keeps the shape the stamp always had");
        for (int a = 0; a < 8; a++) {
            String file = "generatedFor=0.9.213" + combination(a).stamp() + "/golems=true/upgrades=true\n";
            for (int b = 0; b < 8; b++) {
                String asked = "0.9.213" + combination(b).stamp() + "/golems=true/upgrades=true";
                assertEquals(a == b, file.contains(asked), a + " against " + b);
            }
        }
    }

    @Test
    @DisplayName("the id of a lesson not taught never reaches the stamp")
    void switchedOffIdsAreForgotten() {
        QuestLessons before = new QuestLessons(R, QuestLessons.taught(false, W), L);
        QuestLessons moved = new QuestLessons(R, QuestLessons.taught(false, 17), L);
        assertEquals(before.stamp(), moved.stamp());
    }

    @Test
    @DisplayName("the prize carries every lesson taught and nothing else")
    void prizeFollowsTheSwitches() {
        assertArrayEquals(new int[] { R, W, L }, combination(7).prize());
        assertArrayEquals(new int[] { R, L }, combination(5).prize());
        assertArrayEquals(new int[] { W }, combination(2).prize());
        assertArrayEquals(new int[0], combination(0).prize());
        for (int mask = 0; mask < 8; mask++) {
            assertEquals(Integer.bitCount(mask), combination(mask).count(), "mask " + mask);
        }
    }

    @Test
    @DisplayName("no sentence promises more lessons than the pack teaches")
    void wordsFollowTheCount() {
        for (int mask = 0; mask < 8; mask++) {
            QuestLessons lessons = combination(mask);
            String wayfarer = lessons.wayfarerOpens();
            String chunk = lessons.chunkTamperCloses();
            int n = lessons.count();
            assertTrue(wayfarer.startsWith("The last tamper. "), wayfarer);
            assertTrue(chunk.startsWith("\n\n") && chunk.endsWith("opens the rest of the chapter."), chunk);
            assertEquals(n == 3, wayfarer.contains("three"), wayfarer);
            assertEquals(n == 3, chunk.contains("three"), chunk);
            assertEquals(n == 2, wayfarer.contains("both"), wayfarer);
            assertEquals(n == 2, chunk.contains("two"), chunk);
            assertEquals(n > 0, wayfarer.contains("lesson"), wayfarer);
            assertEquals(n > 0, chunk.contains("enchantment"), chunk);
        }
    }

    @Test
    @DisplayName("a lone lesson is named, and it is the one taught")
    void theOnlyLessonIsNamed() {
        assertTrue(
            combination(4).wayfarerOpens()
                .contains("the reinforcing lesson"));
        assertTrue(
            combination(2).wayfarerOpens()
                .contains("the warding lesson"));
        assertTrue(
            combination(1).wayfarerOpens()
                .contains("the wayfinding lesson"));
    }
}
