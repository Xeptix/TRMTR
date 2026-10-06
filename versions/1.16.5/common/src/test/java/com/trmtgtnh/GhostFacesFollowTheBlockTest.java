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

    private static final String QUADS = "com/trmtgtnh/client/model/GhostQuads.java";
    private static final String SIDES = "com/trmtgtnh/client/model/GhostSides.java";

    @Test
    void the_underside_is_asked_for_like_any_other_face() throws IOException {
        String quads = body(QUADS);

        assertFalse(
            quads.contains("if (side == Direction.DOWN)"),
            "the underside had its own branch, and that branch answered earth for every block - a "
                + "worn stone slab is stone underneath");
        assertTrue(
            quads.contains("Direction.DOWN.get3DDataValue()"),
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
        String quads = body(QUADS);

        assertFalse(
            quads.contains("appearance == SurfaceFamily.GRASS ? GRASS_TINT"),
            "the top used to take the biome's grass colour whenever the square was wearing as grass, "
                + "whatever block it covered - so a red block detected into the grass family had "
                + "green multiplied into it and came out dark red");
        assertTrue(
            quads.contains("GhostSides.tintsAsGrass(origin, appearance)"),
            "the tint has to ask the same question the fringe and the earth wall ask: is this "
                + "vanilla's turf, whose picture is stored grey and waiting for a colour");
    }

    @Test
    void a_side_picture_slides_only_when_the_square_has_sunk() throws IOException {
        String sides = body(SIDES);

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
        String layers = body("com/trmtgtnh/client/GhostLayers.java");
        assertTrue(
            layers.contains("ItemBlockRenderTypes.getChunkRenderType(under)"),
            "the pass a square draws in is the covered block's own, asked of the game rather than " + "guessed");

        String forge = read("forge/src/main/java/com/trmtgtnh/forge/ForgeClientSetup.java");
        assertTrue(
            forge.contains("GhostLayers::claims"),
            "Forge has to claim every pass, because which one a square wants is not known until the " + "square is");
        String forgeModel = read("forge/src/main/java/com/trmtgtnh/forge/GhostModelForge.java");
        assertTrue(
            forgeModel.contains("MinecraftForgeClient") && forgeModel.contains("GhostLayers.of(origin)"),
            "and the model has to hand back nothing in the passes that are not this square's");

        String fabricModel = read("fabric/src/main/java/com/trmtgtnh/fabric/GhostModelFabric.java");
        assertTrue(
            fabricModel.contains("blendMode(0, com.trmtgtnh.client.GhostLayers.of(origin))"),
            "Fabric binds a block to one pass, so the pass is carried on the quad instead - which is "
                + "the better fit, and is why worn ice can be translucent without a second block");
    }

    @Test
    void a_block_narrower_than_its_square_keeps_its_own_footing() throws IOException {
        String inherit = body("com/trmtgtnh/block/GhostInherit.java");
        assertTrue(
            inherit.contains("public static VoxelShape ownFootingAt("),
            "the third thing a ghost inherits is the footing the covered block insists on; this class "
                + "carried only two of the three, behind a javadoc that said 'two of those ways'");
        assertTrue(
            inherit.contains("bounds.maxX < 1.0D - TOLERANCE"),
            "and the test is the footprint, never the height - a shorter block is exactly what wear "
                + "produces, so a slab and a path go on wearing as they always have");

        String ghost = body("com/trmtgtnh/block/BlockGhost.java");
        assertTrue(
            ghost.contains("GhostInherit.ownFootingAt(world, pos, origin)"),
            "and the collision path has to ask it - without that a full cell is handed back over a "
                + "pad you should fall through");
    }

    @Test
    void vanilla_grass_is_recognised_as_a_lawn_on_this_version() throws IOException {
        String sides = body(SIDES);

        // The test for "is this vanilla's turf" gates the tint, the fringe and the earth wall, so a
        // version-wrong texture name in it turns grass grey and takes the other two with it. This
        // edition carried the 1.12.2 spelling - "minecraft:blocks/grass_top" - of a texture that 1.13
        // renamed to block/grass_block_top, so it matched nothing here.
        assertFalse(
            sides.contains("\"blocks/"),
            "a blocks/ texture path is the pre-1.13 spelling and matches nothing on this version");
        assertTrue(
            sides.contains("sprite(top).equals(sprite(\"grass_top\"))"),
            "the comparison has to go through the same normalisation the sprite lookup does, so a "
                + "bare name, a foldered one and a namespaced one all compare equal");
    }

    @Test
    void a_sunken_side_slides_by_the_drop_and_not_by_the_crop() throws IOException {
        String quads = body(QUADS);

        assertFalse(
            quads.contains("slid ? height - c[1]"),
            "re-anchoring the texture to the top of the quad samples row nought at any sink depth - "
                + "which on grass_path_side is a transparent row, so a sunken path grew a see-through "
                + "band along the top of every side");
        assertTrue(
            quads.contains("v = (1F - c[1]) * 16F - shiftRows;"),
            "the window is nailed to the bottom of the cell and then slid down");
        assertTrue(
            quads.contains("Math.round(16.0D * (BlockGhost.topOf(outline) - height))"),
            "and it slides by the distance the ground has actually dropped, which puts the window's "
                + "top edge at 16 - 16*originTop - a figure that does not depend on the sink depth, "
                + "so a path's window starts at row one and stays there");
    }

    @Test
    void an_unsunken_square_stops_light_as_the_block_it_covers_did() throws IOException {
        String ghost = body("com/trmtgtnh/block/BlockGhost.java");

        assertTrue(
            ghost.contains("public int getLightBlock(") && ghost.contains("sunkAt(world, pos) ? 0 : 15"),
            "a worn-but-unsunken square is still a whole block of earth and stops light as the block "
                + "it stands in for did; the default reads canOcclude, which is said once for the "
                + "block and is no, so every ghost leaked light whatever had happened to it");
        assertTrue(
            ghost.contains("public boolean propagatesSkylightDown("),
            "and skylight passes through one that has sunk, as it does a slab - this was documented, "
                + "measured, and then not written");

        // The javadoc for it sat in the file with no method under it, and being a block comment it
        // swallowed the next method's javadoc too.
        assertFalse(
            ghost.contains("* /**"),
            "a javadoc opened inside another comment is a method's documentation that is not "
                + "attached to it, and a method that was meant to be there and is not");
    }

    @Test
    void a_map_that_caches_its_tiles_is_told_a_chunk_changed() throws IOException {
        String xaero = body("com/trmtgtnh/client/xaero/XaeroMinimap.java");
        assertTrue(
            xaero.contains("getDeclaredField(\"xaero_chunkClean\")"),
            "Xaero keeps every tile it writes and redraws one only when a block packet dirties the "
                + "chunk - and this mod paints client-side and sends none, so a road never appears "
                + "on its map. The flag is named the same on both loaders and every version here");

        String painter = body("com/trmtgtnh/client/OverlayPainter.java");
        assertTrue(
            painter.contains("XaeroMinimap.chunkChangedAt(world, chunkX, chunkZ)"),
            "painting a chunk's worth of ghosts has to tell it, and so does lifting them - a lifted "
                + "path leaves a dark tile behind on a map that keeps what it drew");
        assertTrue(painter.contains("XaeroMinimap.chunkChanged(world, x, z)"), "and so does painting a single square");
    }

    @Test
    void journeymap_is_handed_a_colour_per_position() throws IOException {
        String jm = body("com/trmtgtnh/client/journeymap/JourneyMapColors.java");

        // JourneyMap decides a block's colour once per BlockMD and caches it, so one ghost covering
        // a hundred modded dirts drew all hundred the same brown. It is also the one map that can
        // show how worn a road is: it takes an ordinary RGB value, where the vanilla answer is a
        // choice of sixty-four fixed palette entries.
        assertTrue(
            jm.contains("journeymap.client.model.block.BlockMD")
                && jm.contains("journeymap.client.mod.IBlockColorProxy"),
            "the names are this version's and were read out of the jar - JourneyMap 6 moved the model "
                + "classes down a package each and replaced the one-method handler with a two-method "
                + "proxy, so the 1.7.10 edition's names would compile, run and find nothing");
        assertTrue(
            jm.contains("setBlockColorProxy"),
            "and the proxy has to actually be installed on the ghost's own BlockMD");
        assertTrue(
            jm.contains("TrmtConfig.desirePathHighlight") && jm.contains("TrmtConfig.mapWearDarkening"),
            "the four map settings mean what they say against this map, which is why they are no "
                + "longer named in sayWhatMapsCannotDo");
    }

    @Test
    void a_covered_block_that_cuts_its_own_side_away_is_mended() throws IOException {
        String textures = body("com/trmtgtnh/client/texture/WearTextures.java");

        assertFalse(
            textures.contains("wantsMendedSide(Block block, SurfaceFamily family)"),
            "the mend was stubbed off behind a signature that took no resources and could only ever "
                + "answer no, with a comment deferring the question until the ghost's sides were "
                + "done; they are done");
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
    void occlusion_is_not_answered_per_square_here_and_that_is_deliberate() throws IOException {
        String ghost = body("com/trmtgtnh/block/BlockGhost.java");

        // The one rule of the 1.7.10 edition's that cannot be carried to this one, and this guard
        // holds the shape of the failure so nobody tries it twice.
        //
        // There, isOpaqueCube is "!sunken && !clear && !window", because that edition keeps a
        // separate sunken variant of every ghost. One ghost standing in for all of them has one
        // state, and 1.16.5 caches a state's occlusion shapes once - in BlockStateBase$Cache, by
        // calling getOcclusionShape with EmptyBlockGetter.INSTANCE and BlockPos.ZERO. So an override
        // that reads the position is called exactly once, with no position, and whatever it answers
        // becomes the answer for every square in the world.
        //
        // It was tried on 2026-10-06. The full cube came back for every ghost, every ghost then
        // occluded all of its neighbours, and every side of every worn square was culled away. The
        // light half of the same question is genuinely position-aware and is kept - see
        // getLightBlock, which the light engine asks per position and does not cache.
        assertTrue(
            ghost.contains(".noOcclusion()"),
            "a ghost has to declare that it occludes nothing: the per-state cache turns any "
                + "position-aware answer into one answer for every square, and that answer hid "
                + "every side of every worn block");
        assertFalse(
            ghost.contains("public VoxelShape getOcclusionShape("),
            "and overriding it is worse than useless here - it is called once, without a position, "
                + "and culls every face in the world");
    }

    private static String read(String relative) throws IOException {
        java.io.File at = new java.io.File(SourceTree.repoRoot(), relative);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        return new String(java.nio.file.Files.readAllBytes(at.toPath()), java.nio.charset.StandardCharsets.UTF_8);
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
