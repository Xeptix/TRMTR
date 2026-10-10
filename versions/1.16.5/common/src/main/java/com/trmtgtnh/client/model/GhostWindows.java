package com.trmtgtnh.client.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Client;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.texture.InnerLayers;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Squares that are drawn through: worn ground over a block with something see-through behind its own cut-away face,
 * Chisel's waterstone the case it was written for - the 1.7.10 edition's window twins, rule for rule.
 *
 * <p>
 * That edition paints a separate twin wherever {@code TrmtConfig.seeThroughInnerLayers && InnerLayers.isWindow(origin,
 * originMeta)}, and the twin carries four clauses: it draws in the pass that blends; it stops claiming to be an opaque
 * cube, so its neighbours draw the faces behind it; it hides the face it shares with an identical twin, as glass does;
 * and while it still stops all light, the faces seen through it are lit from its brightest neighbour. This edition
 * carried the pictures - composed with the holes left open - and none of the four, so until 0.9.220 a worn waterstone
 * drew its water opaque, as blue stone (found 2026-10-08). Here there is one ghost, so each clause is asked per square:
 * the pass in GhostLayers.of, the neighbours' faces already (the ghost never occludes), the shared face in the
 * renderers' own face tests, and the light at the one method every renderer on this version reads a cell's light
 * through.
 *
 * <p>
 * Client only, and read from meshers: the filing InnerLayers keeps is published whole through one volatile field and
 * never written after, so a mesher may read it.
 */
public final class GhostWindows {

    private GhostWindows() {}

    /**
     * Whether the block a square stands in for is a window - the 1.7.10 edition's OverlayPainter.wantedGhost,
     * {@code TrmtConfig.seeThroughInnerLayers && InnerLayers.isWindow(origin, originMeta)}. Meta is nought on this
     * version (GhostSides.metaOf).
     */
    public static boolean windowOf(int origin) {
        if (!TrmtConfig.seeThroughInnerLayers || origin < 0) return false;
        BlockState under = stateOf(origin);
        return under != null && InnerLayers.isWindow(under.getBlock(), 0);
    }

    /**
     * Whether the square here is one of that edition's window twins: a window, and not clear ice - ice over a block
     * that does not fill its cell has no window twin there, its ordinary clear stand-in being drawn through already.
     */
    static boolean windowSquareAt(BlockGetter world, BlockPos pos, int origin) {
        if (!windowOf(origin)) return false;
        SurfaceFamily wears = ErosionState.familyOf(Client.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ()));
        if (wears != SurfaceFamily.ICE) return true;
        BlockState covered = stateOf(origin);
        return covered != null && covered.canOcclude();
    }

    /**
     * The pane a square belongs to, or nought - the 1.7.10 edition's BlockGhost.shouldSideBeRendered, {@code window
     * && !sunken && world.getBlock(x, y, z) == this}.
     *
     * <p>
     * "The same block" there is the same window twin, chosen by the family the square wears as, so two squares over
     * different blocks that wear as one family share a pane. Only that class carries the rule: a hollow (sunk, or over
     * a short block), a stair, and grass drawing vanilla's own top - BlockGhostGrass there - never share one.
     */
    public static int paneAt(BlockGetter world, BlockPos pos) {
        if (world == null || pos == null || !(world.getBlockState(pos)
            .getBlock() instanceof BlockGhost)) return 0;
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        if (!windowSquareAt(world, pos, origin) || !BlockGhost.wholeAt(world, pos)) return 0;
        SurfaceFamily wears = ErosionState.familyOf(Client.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ()));
        if (wears == null || GhostSides.tintsAsGrass(origin, wears)) return 0;
        return 1 + wears.ordinal();
    }

    /** Whether the face of this square towards {@code face} is shared with an identical window, and so not drawn. */
    public static boolean sharesPane(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        // The cheap exit first: this runs for every face of every block in a chunk.
        if (state == null || !(state.getBlock() instanceof BlockGhost) || !InnerLayers.anyWindows()) return false;
        int pane = paneAt(level, pos);
        return pane != 0 && pane == paneAt(level, pos.relative(face));
    }

    /** Above and the four sides, never below - the neighbours the 1.7.10 edition's light reads for such a block. */
    private static final Direction[] AROUND = { Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST,
        Direction.EAST };

    /**
     * The packed light at a window's cell, or -1 to leave the asking renderer to its own answer.
     *
     * <p>
     * A window stops all light, as the block it covers does, and still the faces seen through it are lit: the 1.7.10
     * edition's twin says {@code useNeighborBrightness}, and that edition's ChunkCache then answers a light read at its
     * cell with the brightest saved light of the cells above and on its four sides, each kind of light taken on its
     * own, the asking block's own light as a floor. That flag is gone from this version, so the read is answered here,
     * at LevelRenderer.getLightColor - the one method vanilla, Forge's renderer, Indigo, Sodium and its forks, Canvas
     * and OptiFine all read a cell's light through (read with javap from each, 2026-10-08) - Xep's choice of exactly
     * that rule over letting light into the cell. The position decides, never the state handed in: the flat path asks
     * with the face owner's state and the neighbour's position.
     */
    public static int lightAt(BlockAndTintGetter level, BlockState state, BlockPos pos) {
        boolean windows = InnerLayers.anyWindows();
        if ((!windows && !BlockGhost.anyStairPainted()) || level == null || pos == null) return -1;
        if (!(level.getBlockState(pos)
            .getBlock() instanceof BlockGhost)) return -1;
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        // A worn stair too, of any family, clear or not: it stops all light, as the 1.7.10 edition's stair stand-in does,
        // and that stand-in says useNeighborBrightness for every family, so the riser, the treads and the block beside it
        // are lit from the brightest light around its cell rather than from the cell, which holds none (0.9.222, spec
        // GF8). Until then a stair let light into its cell instead, through its solid back and floor as well.
        if (BlockGhost.coveredStair(origin) == null && !(windows && windowSquareAt(level, pos, origin))) return -1;
        if (state != null && state.emissiveRendering(level, pos)) return 15728880;
        int sky = 0;
        int block = 0;
        for (Direction each : AROUND) {
            BlockPos next = pos.relative(each);
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, next));
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, next));
        }
        if (state != null) block = Math.max(block, state.getLightEmission());
        return sky << 20 | block << 4;
    }

    /**
     * The packed light an entity is drawn with when the point it is lit from stands in a cell {@link #lightAt} answers:
     * the brighter of what its renderer read there and the light lent to that cell, each kind on its own (0.9.222, spec
     * GF8).
     *
     * <p>
     * A worn stair stops all light and its cell holds none, and an item lying on its step, or anything small enough to
     * be lit from inside the cell, is lit from that cell - by the world's own light read, which an entity's renderer
     * makes and every other renderer here does not ({@code EntityRenderer.getPackedLightCoords}). The 1.7.10 edition's
     * world answers that read with the brightest neighbour too, its stair stand-in saying useNeighborBrightness, so the
     * read is lent the same light here; what the renderer read still counts, so a burning or glowing creature keeps its
     * own.
     */
    public static int entityLight(BlockAndTintGetter level, BlockPos pos, int packed) {
        int lent = lightAt(level, null, pos);
        return lent < 0 ? packed : brighterOf(packed, lent);
    }

    /** Two packed lights, the brighter of each kind. */
    static int brighterOf(int packed, int lent) {
        int block = Math.max((packed >> 4) & 0xF, (lent >> 4) & 0xF);
        int sky = Math.max((packed >> 20) & 0xF, (lent >> 20) & 0xF);
        return sky << 20 | block << 4;
    }

    private static BlockState stateOf(int origin) {
        if (origin < 0) return null;
        try {
            return Block.stateById(origin);
        } catch (RuntimeException awkwardBlock) {
            return null;
        }
    }
}
