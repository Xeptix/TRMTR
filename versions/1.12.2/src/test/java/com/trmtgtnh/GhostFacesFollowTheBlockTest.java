package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * A ghost's faces are the covered block's, not a set of guesses about it.
 *
 * <p>
 * Three rules, all of them the 1.7.10 edition's, and all three were lost in the port in a way that
 * only showed up when somebody looked at a wall:
 *
 * <ul>
 * <li><em>The underside is a face like any other.</em> It was dirt by decree for every block, so a
 * worn stone slab had an earth bottom and a worn stair had earth under each of its steps. The
 * original rule is that a block whose sides match its top wears on every face it has; a lawn reaches
 * dirt by being asked for its own bottom, which really is dirt.</li>
 * <li><em>The fringe belongs to lawns, on their flanks.</em> It was handed to anything whose
 * appearance was grass - what a square is <em>wearing as</em> rather than what it is made of - so a
 * modded block detected into the grass family wore vanilla's green fringe, and so did tops and
 * bottoms.</li>
 * <li><em>A sunken square keeps the look it had before any of this.</em> The older edition states
 * this gate twice, for the thinned fringe and for the earth wall. Without it the flanks of a sinking
 * lawn went on receding as it sank - the wall darkening every side on 1.12.2, the fringe thinning and
 * brightening on this one.</li>
 * </ul>
 */
class GhostFacesFollowTheBlockTest {

    private static final String MODEL = "com/trmtgtnh/client/model/GhostBakedModel.java";
    private static final String SIDES = "com/trmtgtnh/client/model/GhostSides.java";

    @Test
    void the_underside_is_asked_for_like_any_other_face() throws IOException {
        String quads = body(MODEL);

        assertFalse(
            quads.contains("if (side == EnumFacing.DOWN)"),
            "the underside had its own branch, and that branch answered earth for every block - a "
                + "worn stone slab is stone underneath");
        assertTrue(
            quads.contains("EnumFacing.DOWN.getIndex()"),
            "a stair's steps have undersides inside the cell, and they go through the same rule");
    }

    @Test
    void the_fringe_is_for_lawn_flanks_only() throws IOException {
        String sides = body(SIDES);

        assertTrue(
            sides.contains("boolean lawn = mimicsVanillaGrassTop(block, meta)"),
            "whether to draw grass's fringe is a question about the block, not about what the square "
                + "is wearing as");
        assertTrue(
            sides.contains("boolean flank = side >= 2"),
            "and only the four flanks carry it, as vanilla's own grass model does");
        assertTrue(
            sides.contains("lawn && flank ?"),
            "both have to gate the fringe, or it reaches a top, a bottom, or somebody else's block");
    }

    @Test
    void a_sunken_square_keeps_its_old_look() throws IOException {
        String sides = body(SIDES);

        assertTrue(
            sides.contains("boolean sunken = ErosionState.sinkOf(record) > 0"),
            "the side rules need to know whether the square has sunk");
        assertTrue(
            sides.contains("!sunken && sideStep >= WALL_START"),
            "the earth wall is for ground that has not sunk; the 1.7.10 edition says so outright");
        assertTrue(
            sides.contains("fringeFor(record, fringeTurn, !sunken)"),
            "and a sunken square's fringe is the plain vanilla one rather than a thinned one");
    }

    @Test
    void the_top_is_tinted_green_only_for_a_real_lawn() throws IOException {
        String model = body(MODEL);

        assertFalse(
            model.contains("appearance == SurfaceFamily.GRASS ? GRASS_TINT"),
            "the top used to take the biome's grass colour whenever the square was wearing as grass, "
                + "whatever block it covered - so a red block detected into the grass family had "
                + "green multiplied into it and came out dark red");
        assertTrue(
            model.contains("GhostSides.tintsAsGrass(origin, appearance)"),
            "the tint has to ask the same question the fringe and the earth wall ask: is this "
                + "vanilla's turf, whose picture is stored grey and waiting for a colour");
    }

    @Test
    void a_side_picture_slides_only_when_the_square_has_sunk() throws IOException {
        String sides = body("com/trmtgtnh/client/model/GhostSides.java");

        assertFalse(
            sides.contains("originFamily, appearance), true, null)"),
            "sliding was handed out as a constant, which is an off-by-one texture row on every block "
                + "that is not a whole cube - vanilla's grass path keeps a transparent row at the top "
                + "of its side and its own model skips it with uv [0, 1, 16, 16]");
        assertTrue(
            sides.contains("originFamily, appearance), slides, null)"),
            "only a square that has dropped below its block's own top may slide its side picture");
        assertTrue(
            sides.contains("boolean slides = sunken && side >= 2"),
            "and only a flank may slide at all - the 1.7.10 edition says side >= 2 in the same breath "
                + "as its sunken test, and the underside comes through this method now too");
    }

    @Test
    void each_square_draws_in_the_pass_its_block_draws_in() throws IOException {
        String ghost = body("com/trmtgtnh/block/BlockGhost.java");
        assertTrue(
            ghost.contains("public static net.minecraft.util.BlockRenderLayer layerOf(int origin)"),
            "the pass a square draws in is the covered block's own");
        assertTrue(
            ghost.contains("public boolean canRenderInLayer("),
            "and the ghost has to offer itself to every pass it may need, because which one a square "
                + "wants is not known until the square is");

        String model = body(MODEL);
        assertTrue(
            model.contains("MinecraftForgeClient.getRenderLayer()") && model.contains("BlockGhost.layerOf(origin)"),
            "the model hands back nothing in the passes that are not this square's - without which "
                + "worn ice draws over what is behind it rather than through it");
    }

    @Test
    void vanilla_grass_is_recognised_as_a_lawn() throws IOException {
        String sides = body("com/trmtgtnh/client/model/GhostSides.java");

        // The test for "is this vanilla's turf" gates the tint, the fringe and the earth wall. It
        // compared raw strings against "minecraft:blocks/grass_top" while vanilla's own model
        // declares "blocks/grass_top" with no namespace, so it matched nothing and grass was never
        // a lawn.
        assertTrue(
            sides.contains("canonical(\"grass_top\").equals(canonical(top))"),
            "the comparison has to normalise both sides - a bare name, a foldered one and a "
                + "namespaced one all name the same texture");
    }

    @Test
    void a_sunken_side_slides_by_the_drop_and_not_by_the_crop() throws IOException {
        String model = body(MODEL);

        assertFalse(
            model.contains("slid ? height - c[1]"),
            "re-anchoring the texture to the top of the quad samples row nought at any sink depth - "
                + "which on grass_path_side is a transparent row, so a sunken path grew a band along "
                + "the top of every side: see-through in the cut-out pass, black in the solid one");
        assertTrue(
            model.contains("v = (1F - c[1]) * 16F - shiftRows;"),
            "the window is nailed to the bottom of the cell and then slid down");
        assertTrue(
            model.contains("Math.round(16.0D * (BlockGhost.topOf(outline) - height))"),
            "and it slides by the distance the ground has actually dropped, which puts the window's "
                + "top edge at 16 - 16*originTop - a figure that does not depend on the sink depth, "
                + "so a path's window starts at row one and stays there");
    }

    @Test
    void an_unsunken_square_stops_light_as_the_block_it_covers_did() throws IOException {
        String ghost = body("com/trmtgtnh/block/BlockGhost.java");

        assertTrue(
            ghost.contains("public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos)"),
            "opacity has to be answered per position: isOpaqueCube is asked of the state alone, and "
                + "one ghost stands in for both sunken and unsunken squares");
        assertTrue(
            ghost.contains("sunkAt(world, pos) ? 0 : 255"),
            "all of the light until the ground sinks and none once it has - letting light through an "
                + "unsunken square lit the cell below a road, and caves under one");
    }

    @Test
    void a_map_that_caches_its_tiles_is_told_a_chunk_changed() throws IOException {
        String xaero = body("com/trmtgtnh/client/xaero/XaeroMinimap.java");
        assertTrue(
            xaero.contains("getDeclaredField(\"xaero_chunkClean\")"),
            "Xaero keeps every tile it writes and redraws one only when a block packet dirties the "
                + "chunk - and this mod paints client-side and sends none, so a road never appears "
                + "on its map. The flag is named the same on every version this mod targets");

        String painter = body("com/trmtgtnh/client/OverlayPainter.java");
        assertTrue(
            painter.contains("XaeroMinimap.chunkChangedAt(world, chunkX, chunkZ)"),
            "painting a chunk's worth of ghosts has to tell it, and so does lifting them");
        assertTrue(painter.contains("XaeroMinimap.chunkChanged(world, x, z)"), "and so does painting a single square");
    }

    @Test
    void journeymap_is_handed_a_colour_per_position() throws IOException {
        String jm = body("com/trmtgtnh/client/journeymap/JourneyMapColors.java");

        assertTrue(
            jm.contains("journeymap.client.model.block.BlockMD")
                && jm.contains("journeymap.client.mod.IBlockColorProxy"),
            "the names are this version's and were read out of the jar - JourneyMap 6 moved the model "
                + "classes down a package each and replaced the one-method handler with a two-method "
                + "proxy, so the 1.7.10 edition's names would compile, run and find nothing");
        assertTrue(
            jm.contains("setBlockColorProxy"),
            "and the proxy has to actually be installed on the ghost's own BlockMD");
    }

    @Test
    void a_covered_block_that_cuts_its_own_side_away_is_mended() throws IOException {
        String textures = body("com/trmtgtnh/client/texture/WearTextures.java");
        assertTrue(
            textures.contains("hasHoles(WearPatterns.readIcon(manager, side))"),
            "the signal is the hole itself, read from the texture - not the render layer, which is a "
                + "proxy that catches vanilla grass, the one block this must never claim");

        String sides = body("com/trmtgtnh/client/model/GhostSides.java");
        assertTrue(
            sides.contains("WearTextures.mendedSide(block, meta)"),
            "and the drawing has to use it, or the sprite is composed, stitched and never asked for");
    }

    @Test
    void a_square_hides_what_it_looks_like_it_hides() throws IOException {
        String ghost = body("com/trmtgtnh/block/BlockGhost.java");
        assertTrue(
            ghost.contains("public boolean doesSideBlockRendering("),
            "isOpaqueCube is asked of the state alone and has to stay false, so whether a neighbour "
                + "may leave off its face is answered per position - without it every worn square "
                + "made its neighbours draw faces nobody can see");
        assertTrue(
            ghost.contains("floorOf(outline) <= 0F && topOf(outline) >= 1F"),
            "and only a whole cube hides anything: a slab or a path stands short, and a neighbour "
                + "that left off its face against one would show a hole");
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
