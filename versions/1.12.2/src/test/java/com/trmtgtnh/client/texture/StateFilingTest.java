package com.trmtgtnh.client.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The filing the wear atlas is kept in, which finds a block's wear by the block rather than by its id.
 *
 * <p>
 * What it replaces went wrong quietly: a world whose ids differed from the pack's drew one block's worn ground with
 * another block's pictures, and nothing failed. So why the old filing failed is shown here beside the fix, with two
 * keys whose numbers are swapped, though the lookups themselves are proved by the log lines and the swapped-world
 * check in game, which no test without the game can reach. Every other promise the atlas leans on is pinned by the
 * case that would break it: a key that is equal but not the same, metadata out of range, a later filing reaching
 * one already published.
 *
 * <p>
 * The keys are plain objects standing in for blocks and the numberings are tables standing in for the registry, so
 * nothing here needs the game.
 */
class StateFilingTest {

    /** A key equal to every other and hashing alike, as a key with an equality of its own might. */
    private static final class AllAlike {

        @Override
        public boolean equals(Object other) {
            return true;
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }

    /** A registry stand-in whose two directions are set together, or apart to fake a move caught half done. */
    private static final class Ids implements StateFiling.Numbering<Object> {

        private final Map<Object, Integer> numbers = new IdentityHashMap<Object, Integer>();

        private final Map<Integer, Object> keys = new HashMap<Integer, Object>();

        /** Gives this key this number, both ways. */
        Ids both(Object key, int id) {
            numbered(key, id);
            return naming(id, key);
        }

        /** Gives this key this number, without saying what the number leads to. */
        Ids numbered(Object key, int id) {
            numbers.put(key, Integer.valueOf(id));
            return this;
        }

        /** Makes this number lead to this key, without numbering the key. */
        Ids naming(int id, Object key) {
            keys.put(Integer.valueOf(id), key);
            return this;
        }

        @Override
        public int idOf(Object key) {
            Integer id = numbers.get(key);
            return id == null ? -1 : id.intValue();
        }

        @Override
        public Object keyAt(int id) {
            return id < 0 ? null : keys.get(Integer.valueOf(id));
        }
    }

    /** Every filing a walk hands over, as value@meta, with the keys kept beside them to be checked by identity. */
    private static final class Walk implements StateFiling.Visitor<Object, String> {

        final List<String> seen = new ArrayList<String>();

        final List<Object> keys = new ArrayList<Object>();

        @Override
        public void visit(Object key, int meta, String value) {
            seen.add(value + "@" + meta);
            keys.add(key);
        }
    }

    private static Walk walk(StateFiling<Object, String> filing) {
        Walk walk = new Walk();
        filing.forEach(walk);
        return walk;
    }

    private static Walk walk(StateFiling.Builder<Object, String> builder) {
        Walk walk = new Walk();
        builder.forEach(walk);
        return walk;
    }

    private static void assertNothingFound(StateFiling.Audit<Object> audit, int keys, String why) {
        assertEquals(keys, audit.keys, why);
        assertEquals(0, audit.moved, why);
        assertEquals(0, audit.absent, why);
        assertEquals(0, audit.absentStates, why);
        assertEquals(0, audit.misread, why);
        assertEquals(0, audit.misreadStates, why);
        assertTrue(audit.movedExamples.isEmpty(), why);
        assertTrue(audit.misreadExamples.isEmpty(), why);
    }

    private static void assertMoved(StateFiling.Moved<Object> moved, Object key, int from, int to) {
        assertSame(key, moved.key);
        assertEquals(from, moved.from);
        assertEquals(to, moved.to);
    }

    @Test
    @DisplayName("a state is found under the object it was filed with")
    void a_state_is_found_under_the_object_it_was_filed_with() {
        Object marble = new Object();
        Object limestone = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        assertTrue(builder.file(marble, 0, "marble"));
        assertTrue(builder.file(limestone, 0, "limestone"));
        assertEquals("marble", builder.get(marble, 0));
        assertTrue(builder.has(limestone, 0));

        StateFiling<Object, String> filing = builder.build();
        assertEquals("marble", filing.get(marble, 0));
        assertEquals("limestone", filing.get(limestone, 0));
        assertTrue(filing.has(marble, 0));
        assertNull(filing.get(new Object(), 0), "an object never filed");
        assertFalse(filing.has(new Object(), 0));
    }

    @Test
    @DisplayName("an equal but distinct key is a different key")
    void an_equal_but_distinct_key_is_a_different_key() {
        AllAlike first = new AllAlike();
        AllAlike second = new AllAlike();
        Map<Object, String> byEquality = new HashMap<Object, String>();
        byEquality.put(first, "first");
        assertEquals("first", byEquality.get(second), "a map that asks equals takes one for the other");

        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        assertTrue(builder.file(first, 0, "first"));
        assertNull(builder.get(second, 0));
        assertTrue(builder.file(second, 0, "second"), "not refused as a state already filed");

        StateFiling<Object, String> filing = builder.build();
        assertEquals("first", filing.get(first, 0));
        assertEquals("second", filing.get(second, 0));
        assertEquals(2, filing.keys());
        assertEquals(2, filing.states());
    }

    @Test
    @DisplayName("the first filing of a state keeps its value")
    void the_first_filing_of_a_state_keeps_its_value() {
        Object key = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        assertTrue(builder.file(key, 3, "first"));
        assertFalse(builder.file(key, 3, "second"));
        assertFalse(builder.file(key, 19, "third"), "nineteen is metadata three again");
        assertEquals("first", builder.get(key, 3));
        assertEquals(1, builder.states());
        assertEquals(Arrays.asList("first@3"), walk(builder).seen, "a refused filing leaves nothing in the order");

        StateFiling<Object, String> filing = builder.build();
        assertEquals("first", filing.get(key, 3));
        assertEquals(1, filing.states());
    }

    @Test
    @DisplayName("metadata is read as its low four bits, filing and finding alike")
    void metadata_is_read_as_its_low_four_bits() {
        Object key = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        assertTrue(builder.file(key, 17, "one"));
        assertTrue(builder.file(key, -1, "fifteen"));
        assertEquals("one", builder.get(key, 1));
        assertEquals("one", builder.get(key, 17));
        assertEquals("fifteen", builder.get(key, 15));
        assertEquals("fifteen", builder.get(key, -1));
        assertFalse(builder.file(key, 1, "again"), "seventeen filed metadata one");
        assertFalse(builder.file(key, 15, "again"), "minus one filed metadata fifteen");
        assertNull(builder.get(key, 16), "sixteen is metadata nought, which was never filed");

        StateFiling<Object, String> filing = builder.build();
        assertEquals("one", filing.get(key, 1));
        assertEquals("one", filing.get(key, 17));
        assertEquals("fifteen", filing.get(key, 15));
        assertEquals("fifteen", filing.get(key, -1));
        assertEquals(2, filing.states());
        assertEquals(Arrays.asList("one@1", "fifteen@15"), walk(filing).seen, "a walk hands over the metadata as read");
    }

    @Test
    @DisplayName("unfiled metadata of a filed key misses")
    void unfiled_metadata_of_a_filed_key_misses() {
        // Shaped like snakestone: one block filed at one and thirteen and at nothing between or beyond.
        Object snakestone = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        builder.file(snakestone, 1, "head");
        builder.file(snakestone, 13, "body");
        StateFiling<Object, String> filing = builder.build();
        for (int meta = 0; meta < 16; meta++) {
            boolean filed = meta == 1 || meta == 13;
            assertEquals(filed, filing.has(snakestone, meta), "metadata " + meta);
            assertEquals(filed, builder.has(snakestone, meta), "metadata " + meta + " while building");
            if (!filed) assertNull(filing.get(snakestone, meta), "metadata " + meta);
        }
        assertEquals(1, filing.keys());
        assertEquals(2, filing.states());
    }

    @Test
    @DisplayName("a null key or value is refused, and a null key always misses")
    void a_null_key_or_value_is_refused_and_a_null_key_always_misses() {
        Object key = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        assertFalse(builder.file(null, 0, "nobody's"));
        assertFalse(builder.file(key, 0, null));
        assertEquals(0, builder.states());
        assertEquals(0, builder.keys(), "a refused value does not leave its key behind");
        assertNull(builder.get(null, 0));
        assertFalse(builder.has(null, 0));
        assertFalse(builder.has(key, 0));

        assertTrue(builder.file(key, 0, "kept"), "a refused value leaves the state free");
        StateFiling<Object, String> filing = builder.build();
        assertNull(filing.get(null, 0));
        assertFalse(filing.has(null, 0));
        assertEquals("kept", filing.get(key, 0));
        assertEquals(1, filing.keys());
    }

    @Test
    @DisplayName("the empty filing misses everything")
    void the_empty_filing_misses_everything() {
        StateFiling<Object, String> empty = StateFiling.empty();
        assertEquals(0, empty.states());
        assertEquals(0, empty.keys());
        assertTrue(empty.isEmpty());
        assertNull(empty.get(new Object(), 0));
        assertFalse(empty.has(new Object(), 0));
        assertNull(empty.get(null, 0));
        assertTrue(walk(empty).seen.isEmpty());
        assertEquals(0, empty.numbersNow(new Ids()).length);
        assertNothingFound(empty.audit(null, new Ids(), 5), 0, "nothing filed, nothing to find");

        StateFiling<Object, String> built = StateFiling.<Object, String>builder()
            .build();
        assertTrue(built.isEmpty(), "a builder given nothing builds an empty filing");
        assertEquals(0, built.keys());

        StateFiling.Builder<Object, String> one = StateFiling.builder();
        one.file(new Object(), 0, "one");
        assertFalse(
            one.build()
                .isEmpty());
    }

    @Test
    @DisplayName("a built filing does not see later filings, and two builds are independent")
    void a_built_filing_does_not_see_later_filings() {
        Object a = new Object();
        Object b = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        builder.file(a, 0, "a0");
        StateFiling<Object, String> first = builder.build();

        // Another metadata of a key already filed writes into that key's row, which is what a shared row would leak.
        builder.file(a, 1, "a1");
        builder.file(b, 0, "b0");
        StateFiling<Object, String> second = builder.build();
        builder.file(a, 2, "a2");

        assertTrue(first.has(a, 0));
        assertFalse(first.has(a, 1), "filed into the same key's row after the build");
        assertFalse(first.has(b, 0));
        assertEquals(1, first.states());
        assertEquals(1, first.keys());
        assertEquals(Arrays.asList("a0@0"), walk(first).seen);

        assertTrue(second.has(a, 1));
        assertTrue(second.has(b, 0));
        assertFalse(second.has(a, 2), "filed after the second build");
        assertEquals(3, second.states());
        assertEquals(2, second.keys());
        assertEquals(Arrays.asList("a0@0", "a1@1", "b0@0"), walk(second).seen);

        assertEquals(4, builder.states());
        assertEquals("a2", builder.get(a, 2));
    }

    @Test
    @DisplayName("filings come back in the order they were made")
    void filings_come_back_in_the_order_they_were_made() {
        Object a = new Object();
        Object b = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        builder.file(b, 13, "b13");
        builder.file(a, 0, "a0");
        builder.file(b, 1, "b1");
        assertFalse(builder.file(b, 13, "b13 again"));
        builder.file(a, 5, "a5");

        List<String> expected = Arrays.asList("b13@13", "a0@0", "b1@1", "a5@5");
        List<Object> expectedKeys = Arrays.asList(b, a, b, a);
        Walk building = walk(builder);
        assertEquals(expected, building.seen, "thirteen was filed before one, and comes back before it");
        Walk built = walk(builder.build());
        assertEquals(expected, built.seen);
        for (int i = 0; i < expectedKeys.size(); i++) {
            assertSame(expectedKeys.get(i), building.keys.get(i), "the key handed over at " + i);
            assertSame(expectedKeys.get(i), built.keys.get(i), "the key handed over at " + i + " once built");
        }

        // A walk that files into the builder it is walking is handed only what was there when it began, even when
        // its first filing outgrows the order's first array.
        final StateFiling.Builder<Object, String> growing = StateFiling.builder();
        for (int i = 0; i < 64; i++) {
            growing.file(new Object(), 0, "k" + i);
        }
        final int[] handed = { 0 };
        growing.forEach(new StateFiling.Visitor<Object, String>() {

            @Override
            public void visit(Object key, int meta, String value) {
                assertEquals("k" + handed[0], value);
                handed[0]++;
                growing.file(key, meta + 1, value + " at one");
            }
        });
        assertEquals(64, handed[0]);
        assertEquals(128, growing.states());
    }

    @Test
    @DisplayName("states and keys are each counted once")
    void states_and_keys_are_each_counted_once() {
        Object a = new Object();
        Object b = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        builder.file(a, 0, "a0");
        builder.file(a, 0, "a0 again");
        builder.file(a, 16, "a16");
        builder.file(a, 1, "a1");
        builder.file(b, 0, "b0");
        builder.file(b, 2, null);
        builder.file(null, 3, "nobody's");
        assertEquals(3, builder.states());
        assertEquals(2, builder.keys());

        StateFiling<Object, String> filing = builder.build();
        assertEquals(3, filing.states());
        assertEquals(2, filing.keys());
        assertFalse(filing.isEmpty());
        assertEquals(2, filing.numbersNow(new Ids()).length, "one number for each key, not for each state");
    }

    @Test
    @DisplayName("shows why: a number packed from ids finds another block after a swap, where a filing by object finds its own")
    void a_packed_number_finds_another_key_after_a_swap_and_a_filing_does_not() {
        Object marble = new Object();
        Object limestone = new Object();
        int meta = 3;

        // The atlas is stitched under the pack's own numbering, and filed both ways.
        Ids atStitch = new Ids().both(marble, 2001)
            .both(limestone, 2002);
        Map<Integer, String> packed = new HashMap<Integer, String>();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        packed.put(Integer.valueOf((atStitch.idOf(marble) << 4) | meta), "marble");
        packed.put(Integer.valueOf((atStitch.idOf(limestone) << 4) | meta), "limestone");
        builder.file(marble, meta, "marble");
        builder.file(limestone, meta, "limestone");
        StateFiling<Object, String> filing = builder.build();

        // Then a world saved under a numbering with the two swapped is opened, and ground is painted over each,
        // remembering what lay under it as a number packed from the ids in force there.
        Ids inWorld = new Ids().both(marble, 2002)
            .both(limestone, 2001);
        int overMarble = (inWorld.idOf(marble) << 4) | meta;
        int overLimestone = (inWorld.idOf(limestone) << 4) | meta;

        assertEquals(
            "limestone",
            packed.get(Integer.valueOf(overMarble)),
            "the bug: worn marble drew limestone's pictures");
        assertEquals("marble", packed.get(Integer.valueOf(overLimestone)), "and worn limestone drew marble's");

        assertEquals(
            "marble",
            filing.get(inWorld.keyAt(overMarble >> 4), overMarble & 0xF),
            "the fix: the number is turned back into its block under the ids in force, and the block finds its own");
        assertEquals("limestone", filing.get(inWorld.keyAt(overLimestone >> 4), overLimestone & 0xF));
    }

    @Test
    @DisplayName("numbers now follow the order keys were first filed")
    void numbers_now_follow_the_order_keys_were_first_filed() {
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        builder.file(b, 0, "b0");
        builder.file(a, 3, "a3");
        builder.file(b, 1, "b1");
        builder.file(c, 0, "c0");
        StateFiling<Object, String> filing = builder.build();

        Ids ids = new Ids().both(a, 10)
            .both(b, 20);
        int[] numbers = filing.numbersNow(ids);
        assertArrayEquals(
            new int[] { 20, 10, -1 },
            numbers,
            "b first, a next, and c, which has no number, last; b's second state takes no second place");
        numbers[0] = 99;
        assertArrayEquals(new int[] { 20, 10, -1 }, filing.numbersNow(ids), "the array is the caller's own");
    }

    @Test
    @DisplayName("an audit counts moved keys, with examples up to the limit")
    void an_audit_counts_moved_keys_with_examples_up_to_the_limit() {
        Object[] keys = new Object[6];
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        Ids then = new Ids();
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new Object();
            builder.file(keys[i], 0, "k" + i);
            then.both(keys[i], 100 + i);
        }
        builder.file(keys[0], 1, "k0 at one");
        StateFiling<Object, String> filing = builder.build();
        int[] numbersThen = filing.numbersNow(then);

        // The first four swap in pairs and the last two keep their numbers.
        Ids now = new Ids().both(keys[0], 101)
            .both(keys[1], 100)
            .both(keys[2], 103)
            .both(keys[3], 102)
            .both(keys[4], 104)
            .both(keys[5], 105);
        StateFiling.Audit<Object> audit = filing.audit(numbersThen, now, 3);
        assertEquals(6, audit.keys);
        assertEquals(4, audit.moved, "keys rather than states: the first key's two states count once");
        assertEquals(0, audit.absent);
        assertEquals(0, audit.absentStates);
        assertEquals(0, audit.misread);
        assertEquals(0, audit.misreadStates);
        assertTrue(audit.misreadExamples.isEmpty());
        assertEquals(3, audit.movedExamples.size(), "no more examples than the limit");
        assertMoved(audit.movedExamples.get(0), keys[0], 100, 101);
        assertMoved(audit.movedExamples.get(1), keys[1], 101, 100);
        assertMoved(audit.movedExamples.get(2), keys[2], 102, 103);

        StateFiling.Audit<Object> quiet = filing.audit(numbersThen, now, 0);
        assertEquals(4, quiet.moved, "every count is kept whatever the limit");
        assertTrue(quiet.movedExamples.isEmpty());
        assertTrue(filing.audit(numbersThen, now, -1).movedExamples.isEmpty(), "a limit below nought keeps none");
    }

    @Test
    @DisplayName("an audit reports a key without a number as absent, with its states")
    void an_audit_reports_a_key_without_a_number_as_absent_with_its_states() {
        Object clientOnly = new Object();
        Object shared = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        builder.file(clientOnly, 0, "c0");
        builder.file(clientOnly, 1, "c1");
        builder.file(clientOnly, 2, "c2");
        builder.file(shared, 0, "s0");
        StateFiling<Object, String> filing = builder.build();
        int[] numbersThen = filing.numbersNow(
            new Ids().both(clientOnly, 1)
                .both(shared, 2));

        // A server without the first key's mod leaves it no number on this side at all, and renumbers the other.
        Ids now = new Ids().both(shared, 7);
        StateFiling.Audit<Object> audit = filing.audit(numbersThen, now, 5);
        assertEquals(2, audit.keys);
        assertEquals(1, audit.absent);
        assertEquals(3, audit.absentStates);
        assertEquals(1, audit.moved, "the absent key had a number too, and is not counted as moved");
        assertMoved(audit.movedExamples.get(0), shared, 2, 7);
        assertEquals(1, audit.movedExamples.size());
        assertEquals(0, audit.misread, "having no number is not misreading one");
        assertEquals(0, audit.misreadStates);
        assertTrue(audit.misreadExamples.isEmpty());
    }

    @Test
    @DisplayName("an audit reports a number naming another object as misread")
    void an_audit_reports_a_number_naming_another_object_as_misread() {
        Object torn = new Object();
        Object renumbered = new Object();
        Object steady = new Object();
        Object lost = new Object();
        Object stranger = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        builder.file(torn, 0, "t0");
        builder.file(torn, 4, "t4");
        builder.file(renumbered, 0, "r0");
        builder.file(steady, 0, "s0");
        builder.file(lost, 0, "l0");
        StateFiling<Object, String> filing = builder.build();
        int[] numbersThen = filing.numbersNow(
            new Ids().both(torn, 1)
                .both(renumbered, 2)
                .both(steady, 3)
                .both(lost, 4));

        // As if caught half way through a move: the torn key keeps its number but the number leads to a stranger,
        // and the lost key has a new number that leads to nothing.
        Ids now = new Ids().numbered(torn, 1)
            .naming(1, stranger)
            .both(renumbered, 5)
            .both(steady, 3)
            .numbered(lost, 9);
        StateFiling.Audit<Object> audit = filing.audit(numbersThen, now, 5);
        assertEquals(4, audit.keys);
        assertEquals(2, audit.misread);
        assertEquals(3, audit.misreadStates, "both of the torn key's states and the lost key's one");
        assertEquals(2, audit.misreadExamples.size());
        assertSame(torn, audit.misreadExamples.get(0));
        assertSame(lost, audit.misreadExamples.get(1));
        assertEquals(1, audit.moved, "the lost key's number changed as well, and it is counted as misread alone");
        assertMoved(audit.movedExamples.get(0), renumbered, 2, 5);
        assertEquals(0, audit.absent);
        assertEquals(0, audit.absentStates);

        StateFiling.Audit<Object> limited = filing.audit(numbersThen, now, 1);
        assertEquals(2, limited.misread);
        assertEquals(1, limited.misreadExamples.size());
        assertSame(torn, limited.misreadExamples.get(0));
    }

    @Test
    @DisplayName("an audit of an unchanged numbering reports nothing")
    void an_audit_of_an_unchanged_numbering_reports_nothing() {
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        builder.file(a, 0, "a0");
        builder.file(a, 1, "a1");
        builder.file(b, 0, "b0");
        builder.file(c, 7, "c7");
        StateFiling<Object, String> filing = builder.build();

        Ids ids = new Ids().both(a, 1)
            .both(b, 2)
            .both(c, 3);
        int[] numbersThen = filing.numbersNow(ids);
        assertNothingFound(filing.audit(numbersThen, ids, 5), 3, "the numbering the filing was published under");
        Ids same = new Ids().both(c, 3)
            .both(a, 1)
            .both(b, 2);
        assertNothingFound(filing.audit(numbersThen, same, 5), 3, "a numbering made afresh with the same numbers");
    }

    @Test
    @DisplayName("an audit given no numbers, or too few, counts no moves past them")
    void an_audit_given_numbers_from_another_filing_counts_no_moves() {
        Object a = new Object();
        Object b = new Object();
        StateFiling.Builder<Object, String> builder = StateFiling.builder();
        builder.file(a, 0, "a0");
        builder.file(b, 0, "b0");
        StateFiling<Object, String> filing = builder.build();
        Ids now = new Ids().both(a, 11)
            .both(b, 12);

        assertNothingFound(filing.audit(null, now, 5), 2, "no numbers, so nothing to have moved from");
        assertNothingFound(filing.audit(new int[0], now, 5), 2, "the numbers of an empty filing");
        assertNothingFound(
            filing.audit(new int[] { 11 }, now, 5),
            2,
            "numbers covering the first key alone, which kept its own; the second is past their end");
        assertNothingFound(filing.audit(new int[] { -1, -1 }, now, 5), 2, "a negative number is none");

        // Absence does not depend on the numbers given, so it is still found without them.
        StateFiling.Audit<Object> audit = filing.audit(null, new Ids().both(a, 11), 5);
        assertEquals(1, audit.absent);
        assertEquals(1, audit.absentStates);
        assertEquals(0, audit.moved);
        assertEquals(0, audit.misread);
    }
}
