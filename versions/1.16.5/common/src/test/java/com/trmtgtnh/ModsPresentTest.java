package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The seam that lets the settings layer ask about other mods without naming a loader.
 *
 * <p>
 * Worth testing for the reason the seam exists at all. This replaced a direct call to Forge in three
 * thousand lines of settings, and it has a failure mode the direct call did not: if nothing fills it
 * in, every question is answered "not installed" and every integration switches itself off without a
 * word. That is a bug this project has shipped twice, in other forms, and both times the thing that
 * was wrong looked exactly like the thing that was right.
 *
 * <p>
 * So the unwired answer is pinned here rather than left to be discovered, and so is the fact that it
 * is a seam a test can fill - which is the whole argument for a settable field over
 * {@code @ExpectPlatform}, and would be worth nothing if nobody ever filled it.
 */
class ModsPresentTest {

    @AfterEach
    void leaveNothingBehind() {
        // A static seam is shared state, and a test that leaves an answer in it decides the next
        // test's result. This is the kind of thing that passes alone and fails in a suite.
        ModsPresent.forget();
    }

    @Test
    void answers_absent_when_no_loader_has_filled_it_in() {
        ModsPresent.forget();
        assertFalse(ModsPresent.wired(), "nothing should be wired before a loader starts");
        assertFalse(ModsPresent.has("journeymap"), "the unwired answer must be absent, not an exception");
    }

    @Test
    void asks_whatever_the_loader_handed_over() {
        List<String> asked = new ArrayList<String>();
        ModsPresent.use(modId -> {
            asked.add(modId);
            return "chisel".equals(modId);
        });

        assertTrue(ModsPresent.wired());
        assertTrue(ModsPresent.has("chisel"), "the loader said this one is installed");
        assertFalse(ModsPresent.has("gregtech"), "and that this one is not");
        assertEquals(java.util.Arrays.asList("chisel", "gregtech"), asked, "each question should reach the loader once");
    }

    @Test
    void the_last_answer_handed_over_is_the_one_used() {
        // Deliberate: a test installing its own answer and a loader installing the real one are both
        // legitimate, and which wins is the caller's business rather than something enforced here.
        ModsPresent.use(modId -> true);
        assertTrue(ModsPresent.has("anything"));
        ModsPresent.use(modId -> false);
        assertFalse(ModsPresent.has("anything"));
    }

    @Test
    void forgetting_puts_it_back_to_unwired() {
        ModsPresent.use(modId -> true);
        assertTrue(ModsPresent.wired());
        ModsPresent.forget();
        assertFalse(ModsPresent.wired());
        assertFalse(ModsPresent.has("anything"));
    }
}
