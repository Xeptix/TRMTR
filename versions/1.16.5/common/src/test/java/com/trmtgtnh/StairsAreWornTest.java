package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * A stair wears like everything else.
 *
 * <p>
 * It was the one shape every edition of this mod declined. The painter checked for a stair and
 * refused to paint one, so a road that crossed a staircase stopped at it, and the comment explaining
 * why was true of the ghost as it then was: a ghost is a box over a cell, and a stair is not a box.
 *
 * <p>
 * This version has the shorter road to it. A stair's shape is a {@code VoxelShape} and the block it
 * covers will hand one over for the asking, so the same answer serves the collision box, the outline
 * a player aims at and - through {@code toAabbs} - the boxes the model draws. The 1.12.2 edition has
 * to pack the shape into a property and port vanilla's own box table to get the same three things to
 * agree, because its model is handed a state and no world.
 *
 * <p>
 * A worn stair does not sink, and that is a decision rather than an omission. A stair fuses what it
 * looks like and what it collides as into one answer, so a dip in the picture would be a dip the
 * server has not got, and the server would spend every tick pushing whoever stood in it back out of
 * ground it believes is solid.
 */
class StairsAreWornTest {

    private static final String GHOST = "com/trmtgtnh/block/BlockGhost.java";
    private static final String PAINTER = "com/trmtgtnh/client/OverlayPainter.java";
    private static final String QUADS = "com/trmtgtnh/client/model/GhostQuads.java";

    @Test
    void the_painter_no_longer_refuses_a_stair() throws IOException {
        assertFalse(
            body(PAINTER).contains("SurfaceShape.STAIR"),
            "the painter still turns a stair away; a road that crosses a staircase stops at it");
    }

    @Test
    void a_stair_keeps_the_shape_of_the_stair_it_covers() throws IOException {
        String ghost = body(GHOST);

        assertTrue(
            ghost.contains("public static VoxelShape stairShapeAt("),
            "there has to be one place that answers what shape a worn stair is");
        assertTrue(
            ghost.contains("under.getShape(world, pos)"),
            "and it has to be the covered block's own shape rather than a shape of this mod's "
                + "invention, or a worn stair is a different stair");
    }

    @Test
    void one_answer_serves_the_picture_and_the_footing() throws IOException {
        String ghost = body(GHOST);

        for (String asked : new String[] { "public VoxelShape getCollisionShape(", "public VoxelShape getShape(" }) {
            int at = ghost.indexOf(asked);
            assertTrue(at > 0, "BlockGhost has to answer " + asked);
            String answer = ghost.substring(at, Math.min(ghost.length(), at + 400));
            assertTrue(
                answer.contains("stairShapeAt(") && answer.contains("if (stair != null) return stair;"),
                asked + " has to hand back the stair's own shape before it works out any sinking - a "
                    + "stair that sank would be drawn with a dip the server has not got");
        }

        assertTrue(ghost.contains("stair.toAabbs()"), "and the boxes the model draws have to come off that same shape");
    }

    @Test
    void a_stair_is_drawn_whole_rather_than_sorted_into_six_faces() throws IOException {
        String quads = body(QUADS);
        int at = quads.indexOf("private static List<BakedQuad> stairs(");
        assertTrue(at > 0, "there has to be a path that draws a stair");
        String stairs = quads.substring(at, Math.min(quads.length(), at + 2000));

        assertTrue(
            stairs.contains("if (side != null) return Collections.emptyList();"),
            "a stair's quads go in the general bucket: a sub-box's top faces up in the middle of its "
                + "own cell, where culling against the neighbour above would hide the tread");
    }

    /** The file with its comments and javadoc stripped, so a sentence cannot satisfy a check. */
    private static String body(String relative) throws IOException {
        StringBuilder out = new StringBuilder();
        boolean block = false;
        for (String line : SourceTree.lines(relative)) {
            String trimmed = line.trim();
            if (block) {
                if (trimmed.contains("*/")) block = false;
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) block = true;
                continue;
            }
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue;
            out.append(line)
                .append('\n');
        }
        return out.toString();
    }
}
