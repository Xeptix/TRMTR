package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * A stair wears like everything else.
 *
 * <p>
 * It was the one shape this edition declined. The painter checked for a stair and refused to paint
 * one, with a comment explaining that a stair is three boxes cut four ways and that the ghost is a
 * box over a cell - true of the ghost as it then was, and the reason the refusal stood for as long as
 * it did. A road that crossed a staircase simply stopped at it.
 *
 * <p>
 * What makes it possible is settling the shape where there is a world to ask. A stair's facing and
 * half are in its metadata; which of the five shapes it is - straight, or one of four corners - is
 * not stored at all and is worked out from its neighbours every time. The model is handed a state and
 * no world, so the answer is packed in {@code BlockGhost.stairCodeAt} while the extended state is
 * being built and read back out by the model and the collision alike.
 *
 * <p>
 * <strong>The boxes are a port of vanilla's own private method and the numbers are held here on
 * purpose.</strong> Nothing in this build can call {@code BlockStairs.getCollisionBoxList}, so if the
 * port drifts there is no compiler and no vanilla to catch it - a worn stair would quietly be walked
 * on as a shape other than the one it is drawn as. These are the eighteen boxes vanilla uses.
 */
class StairsAreWornTest {

    private static final String GHOST = "com/trmtgtnh/block/BlockGhost.java";
    private static final String PAINTER = "com/trmtgtnh/client/OverlayPainter.java";
    private static final String MODEL = "com/trmtgtnh/client/model/GhostBakedModel.java";

    @Test
    void the_painter_no_longer_refuses_a_stair() throws IOException {
        String painter = body(PAINTER);
        assertFalse(
            painter.contains("SurfaceShape.STAIR"),
            "the painter still turns a stair away; a road that crosses a staircase stops at it");
    }

    @Test
    void the_shape_is_settled_where_there_is_a_world_to_ask() throws IOException {
        String ghost = body(GHOST);

        assertTrue(
            ghost.contains("public static int stairCodeAt("),
            "something has to work out which shape a stair is while a world is still to hand");
        assertTrue(
            ghost.contains("getActualState("),
            "and it has to ask for the actual state: a stair's corner shape is not in its metadata, so "
                + "a stored state would report every corner in a staircase as straight");
        assertTrue(
            ghost.contains(".withProperty(STAIR,"),
            "the answer has to reach the model, which is handed a state and nothing else");
    }

    @Test
    void one_answer_serves_the_picture_and_the_footing() throws IOException {
        String ghost = body(GHOST);

        assertTrue(
            ghost.contains("public void addCollisionBoxToList("),
            "a stair is walked on as several boxes, and a single bounding box cannot say that - "
                + "without this a player walks up an invisible ramp");
        assertTrue(
            ghost.contains("stairBoxes(stair)") || ghost.contains("stairBoxes(code)"),
            "and the boxes it is walked on have to be the same list the model draws");

        String model = body(MODEL);
        assertTrue(
            model.contains("BlockGhost.stairBoxes("),
            "the model has to draw that same list, or the picture and the footing are two guesses");
    }

    @Test
    void a_stair_is_drawn_whole_rather_than_sorted_into_six_faces() throws IOException {
        String model = body(MODEL);
        int at = model.indexOf("private static List<BakedQuad> stairs(");
        assertTrue(at > 0, "there has to be a path that draws a stair");
        String stairs = model.substring(at, Math.min(model.length(), at + 2000));

        assertTrue(
            stairs.contains("if (side != null) return Collections.emptyList();"),
            "a stair's quads go in the general bucket: a sub-box's top faces up in the middle of its "
                + "own cell, where culling against the neighbour above would hide the tread");
    }

    @Test
    void the_eighteen_boxes_are_vanillas() throws IOException {
        String ghost = body(GHOST);
        String[][] expected = { { "AABB_SLAB_TOP", "0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D" },
            { "AABB_SLAB_BOTTOM", "0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D" },
            { "AABB_QTR_TOP_WEST", "0.0D, 0.5D, 0.0D, 0.5D, 1.0D, 1.0D" },
            { "AABB_QTR_TOP_EAST", "0.5D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D" },
            { "AABB_QTR_TOP_NORTH", "0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 0.5D" },
            { "AABB_QTR_TOP_SOUTH", "0.0D, 0.5D, 0.5D, 1.0D, 1.0D, 1.0D" },
            { "AABB_QTR_BOT_WEST", "0.0D, 0.0D, 0.0D, 0.5D, 0.5D, 1.0D" },
            { "AABB_QTR_BOT_EAST", "0.5D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D" },
            { "AABB_QTR_BOT_NORTH", "0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 0.5D" },
            { "AABB_QTR_BOT_SOUTH", "0.0D, 0.0D, 0.5D, 1.0D, 0.5D, 1.0D" },
            { "AABB_OCT_TOP_NW", "0.0D, 0.5D, 0.0D, 0.5D, 1.0D, 0.5D" },
            { "AABB_OCT_TOP_NE", "0.5D, 0.5D, 0.0D, 1.0D, 1.0D, 0.5D" },
            { "AABB_OCT_TOP_SW", "0.0D, 0.5D, 0.5D, 0.5D, 1.0D, 1.0D" },
            { "AABB_OCT_TOP_SE", "0.5D, 0.5D, 0.5D, 1.0D, 1.0D, 1.0D" },
            { "AABB_OCT_BOT_NW", "0.0D, 0.0D, 0.0D, 0.5D, 0.5D, 0.5D" },
            { "AABB_OCT_BOT_NE", "0.5D, 0.0D, 0.0D, 1.0D, 0.5D, 0.5D" },
            { "AABB_OCT_BOT_SW", "0.0D, 0.0D, 0.5D, 0.5D, 0.5D, 1.0D" },
            { "AABB_OCT_BOT_SE", "0.5D, 0.0D, 0.5D, 1.0D, 0.5D, 1.0D" }, };

        for (String[] box : expected) {
            assertTrue(
                ghost.contains(box[0] + " = new AxisAlignedBB(" + box[1] + ")"),
                box[0] + " has to be " + box[1] + ", which is what vanilla's stair uses");
        }
    }

    @Test
    void the_half_that_reads_as_a_mistake_is_vanillas_too() throws IOException {
        String ghost = body(GHOST);
        // A stair whose half is TOP has its slab across the top of the cell and its step in the
        // bottom half, so the top half asks for the boxes named BOT. It looks inverted, it is not,
        // and somebody tidying it would break every upside-down stair in the world.
        assertTrue(
            ghost.contains("top ? AABB_QTR_BOT_NORTH : AABB_QTR_TOP_NORTH"),
            "the top half takes the quarter named BOT - vanilla's naming, and inverting it to read "
                + "better would put the step of every upside-down stair in the wrong place");
        assertTrue(ghost.contains("top ? AABB_OCT_BOT_NW : AABB_OCT_TOP_NW"), "and the same for the corner piece");
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
