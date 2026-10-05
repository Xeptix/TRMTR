package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The mobs list, as the server reads it and as the magic tamper's two buttons rewrite it.
 *
 * <p>
 * Both halves fail quietly. A rewrite the reader understands differently leaves a mob wearing the
 * ground after somebody pressed stop, or stopped after they pressed wear, and nothing says so; the
 * only sign is a path that keeps appearing. So every rewrite here is checked by reading it back the
 * way the server does, rather than by the text it produces alone.
 */
class MobEntriesTest {

    /** What the server would count this mob for under these lines. */
    private static float weightOf(String[] entries, String mob) {
        return MobEntries.lookup(MobEntries.table(entries), mob);
    }

    private static void assertEntry(String raw, String name, float weight, boolean badWeight) {
        MobEntries.Entry entry = MobEntries.parse(raw);
        assertNotNull(entry, raw);
        assertEquals(name, entry.name, raw);
        assertEquals(weight, entry.weight, 0f, raw);
        assertEquals(badWeight, entry.badWeight, raw);
    }

    @Test
    @DisplayName("a line is read as the reader has always read it")
    void parsingAsTheReaderDoes() {
        assertEntry("Villager:1.5", "Villager", 1.5f, false);
        assertEntry(" Villager : 2 ", "Villager", 2f, false);
        assertEntry("Villager", "Villager", 1f, false);
        assertEntry("*", "*", 1f, false);
        assertEntry(":5", ":5", 1f, false);
        assertEntry("minecraft:Villager", "minecraft:Villager", 1f, true);
        assertEntry("Villager:abc", "Villager:abc", 1f, true);

        MobEntries.Entry notANumber = MobEntries.parse("Villager:NaN");
        assertEquals("Villager", notANumber.name);
        assertTrue(Float.isNaN(notANumber.weight), "a weight the table then leaves out");
        assertFalse(notANumber.badWeight);

        assertNull(MobEntries.parse(null));
        assertNull(MobEntries.parse(""));
        assertNull(MobEntries.parse("   "));
    }

    @Test
    @DisplayName("stopping a bare line leaves Name:0, which counts for nothing")
    void stoppingABareLine() {
        String[] before = { "Villager" };
        assertEquals(1f, weightOf(before, "Villager"), 0f);
        String[] after = MobEntries.neverWears(before, "Villager");
        assertArrayEquals(new String[] { "Villager:0" }, after);
        assertEquals(0f, weightOf(after, "Villager"), 0f);
    }

    @Test
    @DisplayName("stopping a weighted line counts for nothing too")
    void stoppingAWeightedLine() {
        String[] after = MobEntries.neverWears(new String[] { "Villager:1.5" }, "Villager");
        assertArrayEquals(new String[] { "Villager:0" }, after);
        assertEquals(0f, weightOf(after, "Villager"), 0f);
    }

    @Test
    @DisplayName("stopping a mob only the wildcard counts appends Name:0 and leaves every other mob the wildcard's")
    void stoppingUnderTheWildcard() {
        String[] after = MobEntries.neverWears(new String[] { "*" }, "Villager");
        assertArrayEquals(new String[] { "*", "Villager:0" }, after);
        assertEquals(0f, weightOf(after, "Villager"), 0f);
        assertEquals(1f, weightOf(after, "Zombie"), 0f);

        String[] halved = MobEntries.neverWears(new String[] { "*:0.5" }, "Villager");
        assertArrayEquals(new String[] { "*:0.5", "Villager:0" }, halved);
        assertEquals(0f, weightOf(halved, "Villager"), 0f);
        assertEquals(0.5f, weightOf(halved, "Zombie"), 0f);
    }

    @Test
    @DisplayName("every line naming the mob, in any case, collapses to one at the first one's place")
    void duplicatesCollapse() {
        String[] before = { "villager:2", "Zombie", "", "VILLAGER", "Cow:0.5", "Villager:1.5" };
        String[] after = MobEntries.neverWears(before, "Villager");
        assertArrayEquals(new String[] { "Villager:0", "Zombie", "", "Cow:0.5" }, after);
        assertEquals(0f, weightOf(after, "Villager"), 0f);
        assertEquals(1f, weightOf(after, "Zombie"), 0f);
        assertEquals(0.5f, weightOf(after, "Cow"), 0f);
    }

    @Test
    @DisplayName("stopping twice is stopping once")
    void stoppingIsIdempotent() {
        String[][] lists = { { "Villager" }, { "*" }, {}, { "villager:2", "Zombie", "", "VILLAGER", "Cow:0.5" }, null };
        for (String[] list : lists) {
            String[] once = MobEntries.neverWears(list, "Villager");
            assertArrayEquals(once, MobEntries.neverWears(once, "Villager"));
        }
        assertArrayEquals(new String[] { "Villager:0" }, MobEntries.neverWears(null, "Villager"));
    }

    @Test
    @DisplayName("a line whose weight did not read is left alone, and Name:0 still wins")
    void aBadWeightLineIsLeft() {
        String[] after = MobEntries.neverWears(new String[] { "Villager:abc", "*" }, "Villager");
        assertArrayEquals(new String[] { "Villager:abc", "*", "Villager:0" }, after);
        assertEquals(0f, weightOf(after, "Villager"), 0f);
        assertEquals(
            Float.valueOf(1f),
            MobEntries.table(after)
                .get("villager:abc"),
            "filed under its whole text");

        String[] later = MobEntries.neverWears(new String[] { "Villager:2", "Villager:abc" }, "Villager");
        assertArrayEquals(new String[] { "Villager:0", "Villager:abc" }, later);
        assertEquals(0f, weightOf(later, "Villager"), 0f);
    }

    @Test
    @DisplayName("wearing after stopping gives the bare name at a weight of one, even under a lighter wildcard")
    void wearsUndoesStop() {
        String[] stopped = MobEntries.neverWears(new String[] { "Villager:1.5", "Zombie" }, "Villager");
        String[] again = MobEntries.wears(stopped, "Villager");
        assertArrayEquals(new String[] { "Villager", "Zombie" }, again);
        assertEquals(1f, weightOf(again, "Villager"), 0f);

        String[] underWildcard = MobEntries
            .wears(MobEntries.neverWears(new String[] { "*:0.5" }, "Villager"), "Villager");
        assertArrayEquals(new String[] { "*:0.5", "Villager" }, underWildcard);
        assertEquals(1f, weightOf(underWildcard, "Villager"), 0f);
        assertEquals(0.5f, weightOf(underWildcard, "Zombie"), 0f);
    }

    @Test
    @DisplayName("wearing leaves a weight above nought exactly as it was written, a wildcard's included")
    void wearsKeepsAPositiveWeight() {
        String[] weighted = { "Villager:1.5" };
        assertArrayEquals(weighted, MobEntries.wears(weighted, "Villager"));

        String[] lastWins = { "villager:0", "Zombie", "Villager:3" };
        assertArrayEquals(lastWins, MobEntries.wears(lastWins, "Villager"));

        String[] negativeIgnored = { "Villager:3", "Villager:-1" };
        assertArrayEquals(negativeIgnored, MobEntries.wears(negativeIgnored, "Villager"));
        assertEquals(3f, weightOf(negativeIgnored, "Villager"), 0f, "the reader skips the negative line");

        String[] wildcard = { "*:2" };
        String[] underWildcard = MobEntries.wears(wildcard, "Zombie");
        assertArrayEquals(wildcard, underWildcard, "a bare name appended would outrank the wildcard at one");
        assertEquals(2f, weightOf(underWildcard, "Zombie"), 0f);

        String[] ignoredUnderWildcard = { "*:2", "Zombie:-1" };
        assertArrayEquals(ignoredUnderWildcard, MobEntries.wears(ignoredUnderWildcard, "Zombie"));
        assertEquals(2f, weightOf(ignoredUnderWildcard, "Zombie"), 0f, "the reader skips the negative line");
    }

    @Test
    @DisplayName("wearing replaces a negative line, a line that is not a number, and a last duplicate at nought")
    void wearsReplacesWhatCountsForNothing() {
        String[] negative = MobEntries.wears(new String[] { "Villager:-1" }, "Villager");
        assertArrayEquals(new String[] { "Villager" }, negative);
        assertEquals(1f, weightOf(negative, "Villager"), 0f);

        String[] notANumber = MobEntries.wears(new String[] { "Zombie", "Villager:NaN" }, "Villager");
        assertArrayEquals(new String[] { "Zombie", "Villager" }, notANumber);
        assertEquals(1f, weightOf(notANumber, "Villager"), 0f);

        String[] noughtLast = { "Villager:2", "Zombie", "villager:0" };
        assertEquals(0f, weightOf(noughtLast, "Villager"), 0f, "the last line wins, so it counts for nothing");
        String[] replaced = MobEntries.wears(noughtLast, "Villager");
        assertArrayEquals(new String[] { "Villager", "Zombie" }, replaced);
        assertEquals(1f, weightOf(replaced, "Villager"), 0f);

        assertArrayEquals(
            new String[] { "Zombie", "Villager" },
            MobEntries.wears(new String[] { "Zombie" }, "Villager"));
    }

    @Test
    @DisplayName("the table leaves out negative and unreadable weights, lets the last line win and cannot be changed")
    void theTable() {
        final Map<String, Float> table = MobEntries.table(
            new String[] { "Villager:-1", "Cow:NaN", "Pig:2", "pig:0.5", "Zombie:3", "zombie:-2", "VillagerGolem" });
        assertEquals(3, table.size());
        assertFalse(table.containsKey("villager"));
        assertFalse(table.containsKey("cow"));
        assertEquals(Float.valueOf(0.5f), table.get("pig"), "the later line");
        assertEquals(Float.valueOf(3f), table.get("zombie"), "a negative line hides nothing");
        assertEquals(Float.valueOf(1f), table.get("villagergolem"), "keys are lower case");
        assertThrows(UnsupportedOperationException.class, new Executable() {

            @Override
            public void execute() {
                table.put("villager", Float.valueOf(1f));
            }
        });

        final Map<String, Float> empty = MobEntries.table(null);
        assertTrue(empty.isEmpty());
        assertTrue(
            MobEntries.table(new String[0])
                .isEmpty());
        assertThrows(UnsupportedOperationException.class, new Executable() {

            @Override
            public void execute() {
                empty.put("villager", Float.valueOf(1f));
            }
        });

        assertEquals(0f, MobEntries.lookup(table, null), 0f);
        assertEquals(0f, MobEntries.lookup(table, "Villager"), 0f, "no line and no wildcard");
        assertEquals(0f, MobEntries.lookup(null, "Villager"), 0f);
    }
}
