package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * The ghost must not let this version cache its answers.
 *
 * <p>
 * Everything interesting a ghost says is about one square: how far it has sunk, how much light it
 * takes out, what shape it is. This version builds a {@code BlockBehaviour$BlockStateBase$Cache} for
 * each block state the first time it is used, and fills it by calling the block once with
 * {@code EmptyBlockGetter.INSTANCE} and {@code BlockPos.ZERO} - a view with no world in it, where a
 * ghost cannot find its own record and answers as though nothing had worn. Every later question is
 * then answered from that frozen value, for every square.
 *
 * <p>
 * {@code Properties.dynamicShape()} is what stops the cache being built at all. Without it the
 * per-position overrides in {@code BlockGhost} are dead code that compiles, ships and never runs.
 *
 * <p>
 * <strong>What it cost before it was found, on 2026-10-06.</strong> A sunken square reported that it
 * blocks all light when it blocks none. Fabric's Indigo renderer - which, unlike vanilla's, reads
 * opacity to decide ambient occlusion - then drew the wall beside every sunken square about two and a
 * half times too dark: measured 19 against the 1.7.10 edition's 50, with Forge at 53 and unaffected,
 * so it looked for four diagnostic runs like a Fabric rendering bug rather than a block property
 * frozen at construction. With {@code dynamicShape()} the same wall measures 48.5.
 *
 * <p>
 * This reads the source rather than running it because what it guards against compiles perfectly and
 * is visible only in a running game - and only on one of the two loaders.
 */
class GhostAnswersPerSquareTest {

    private static final String BLOCK = "common/src/main/java/com/trmtgtnh/block/BlockGhost.java";

    @Test
    void the_ghost_refuses_the_state_cache() throws IOException {
        String source = read();
        assertTrue(
            source.contains(".dynamicShape()"),
            BLOCK + " does not ask for a dynamic shape, so this version caches its answers from one "
                + "call made with an empty world before any square has worn - and every per-position "
                + "override below it becomes dead code");
    }

    /**
     * And the overrides it protects are still there to protect.
     *
     * <p>
     * A guard on the cache alone would pass just as happily if somebody removed the methods whose
     * being live is the whole point of removing it.
     */
    @Test
    void the_answers_it_protects_are_still_asked_per_square() throws IOException {
        String source = read();
        for (String[] method : new String[][] { { "getLightBlock", "how much light a square takes out" },
            { "propagatesSkylightDown", "whether the sky reaches past it" },
            { "getShadeBrightness", "how much light a neighbour's touching face keeps" } }) {
            assertTrue(
                source.contains("public " + methodReturn(method[0]) + " " + method[0] + "("),
                BLOCK + " no longer answers " + method[0] + " - " + method[1]
                    + " - which is one of the answers dynamicShape() exists to keep live");
        }
        assertTrue(
            source.contains("sunkAt(world, pos)"),
            "and at least one of them has to actually look the square up, or the cache was disabled "
                + "for nothing");
    }

    private static String methodReturn(String name) {
        if ("getLightBlock".equals(name)) return "int";
        if ("getShadeBrightness".equals(name)) return "float";
        return "boolean";
    }

    private static String read() throws IOException {
        java.io.File at = new java.io.File(SourceTree.repoRoot(), BLOCK);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        return new String(
            java.nio.file.Files.readAllBytes(at.toPath()),
            java.nio.charset.Charset.forName("UTF-8"));
    }
}
