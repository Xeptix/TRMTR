package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A librarian's book, drawn again when what came up is switched off.
 *
 * <p>
 * Both ways this goes wrong are quiet. A draw that leans towards some entries shows only as a librarian
 * that sells one book a shade too often, and a draw that asks the random source when nothing is switched
 * off changes every villager's trades on a server that never turned anything off. So the draw is checked
 * against a random source that reports what it was asked, rather than by counting outcomes.
 */
class OfferedDrawTest {

    private static final Predicate<String> ON = new Predicate<String>() {

        @Override
        public boolean test(String s) {
            return !s.startsWith("off");
        }
    };

    /** A random source that must not be asked at all. */
    private static final class Untouched extends Random {

        @Override
        protected int next(int bits) {
            throw new AssertionError("the random source was asked");
        }
    }

    /** A random source that gives one answer and remembers every bound it was handed. */
    private static final class Scripted extends Random {

        final int answer;
        final List<Integer> bounds = new ArrayList<Integer>();

        Scripted(int answer) {
            this.answer = answer;
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(Integer.valueOf(bound));
            return answer;
        }
    }

    @Test
    @DisplayName("an offered pick comes back untouched, without asking the random source")
    void offeredPickStands() {
        String[] list = { "a", "off-reinforce", "b" };
        assertSame(list[0], OfferedDraw.redraw(list[0], list, ON, new Untouched()));
    }

    @Test
    @DisplayName("a switched-off pick is drawn again evenly from the entries still offered")
    void switchedOffPickIsDrawnAgainEvenly() {
        String[] list = { "a", "off-reinforce", "b", "c", "off-light" };
        String[] reachable = { "a", "b", "c" };
        for (int i = 0; i < reachable.length; i++) {
            Scripted random = new Scripted(i);
            assertEquals(reachable[i], OfferedDraw.redraw("off-reinforce", list, ON, random));
            assertEquals(1, random.bounds.size(), "one draw");
            assertEquals(Integer.valueOf(3), random.bounds.get(0), "drawn from the three still offered");
        }
    }

    @Test
    @DisplayName("an entry the list holds twice is twice as likely, as in the game's own draw")
    void duplicatesWeighAsInTheList() {
        String[] list = { "a", "b", "b", "off-ward" };
        assertEquals("a", OfferedDraw.redraw("off-ward", list, ON, new Scripted(0)));
        assertEquals("b", OfferedDraw.redraw("off-ward", list, ON, new Scripted(1)));
        assertEquals("b", OfferedDraw.redraw("off-ward", list, ON, new Scripted(2)));
    }

    @Test
    @DisplayName("with nothing offered the pick comes back as it was, rather than an empty draw")
    void nothingOfferedKeepsThePick() {
        String[] list = { "off-reinforce", "off-ward" };
        assertSame(list[1], OfferedDraw.redraw(list[1], list, ON, new Untouched()));
    }

    @Test
    @DisplayName("holes in the list are passed over and a missing pick stays missing")
    void holesArePassedOver() {
        String[] list = { null, "off-light", "a", null };
        assertEquals("a", OfferedDraw.redraw("off-light", list, ON, new Scripted(0)));
        assertNull(OfferedDraw.redraw(null, list, ON, new Untouched()));
    }

    @Test
    @DisplayName("each entry is asked once, so the pool counted and the entry handed out rest on the same answers")
    void eachEntryAskedOnce() {
        final AtomicInteger asked = new AtomicInteger();
        Predicate<String> counting = new Predicate<String>() {

            @Override
            public boolean test(String s) {
                asked.incrementAndGet();
                return ON.test(s);
            }
        };
        String[] list = { "a", "off-reinforce", "b" };
        OfferedDraw.redraw("off-reinforce", list, counting, new Scripted(0));
        assertEquals(1 + list.length, asked.get(), "the pick once, then every entry once");
    }
}
