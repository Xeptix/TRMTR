package com.trmtgtnh.client.model;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.texture.InnerLayers;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Which worn squares are windows, and which windows share a pane.
 *
 * <p>
 * A window is the 1.7.10 edition's word for worn ground whose own picture is cut away somewhere the layer behind it -
 * the water in Chisel's waterstone - lets light through. There the painter decides it once per square and paints a
 * window twin of the stand-in block, and the twin carries four clauses: it draws in the pass that blends, it stops
 * claiming to be an opaque cube so its neighbours draw the faces it now shows them, it hides the face it shares with
 * an identical twin the way glass does, and it still stops all light while lighting what it shows from its brightest
 * neighbour. This edition paints one ghost for everything, so the same decision is made per square as it is meshed,
 * and the four clauses are answered by {@link BlockGhost#layerOf}, {@link BlockGhost#doesSideBlockRendering},
 * {@link BlockGhost#shouldSideBeRendered} and the ghost's own light answers, which already were the twin's. Until
 * 2026-10-08 none of this was carried: the picture was composed see-through and then drawn in the covered block's own
 * pass - SOLID, for Chisel's - which writes the water in the holes opaque.
 *
 * <p>
 * Asked on the chunk mesher's threads. What it reads is safe there: the setting is one field, the filing of windows is
 * published whole through one volatile field ({@link InnerLayers#isWindow}), and the record and origin of a square come
 * through the proxy from the client's cache, which is built for lock-free reads from exactly those threads.
 */
@SideOnly(Side.CLIENT)
public final class GhostWindows {

    private GhostWindows() {}

    /**
     * Whether the block a square stands over is drawn through its holes - the 1.7.10 edition's
     * {@code OverlayPainter.wantedGhost}, word for word: {@code TrmtConfig.seeThroughInnerLayers &&
     * InnerLayers.isWindow(origin, originMeta)}. The setting is asked here rather than when the pictures were made,
     * as there, so turning it off draws the very next mesh solid.
     *
     * @param origin the covered block's state id, as the painter remembered it, or -1 where nothing is remembered
     */
    public static boolean windowOf(int origin) {
        if (!TrmtConfig.seeThroughInnerLayers || origin < 0) return false;
        try {
            IBlockState under = Block.getStateById(origin);
            Block block = under.getBlock();
            // The meta the sprite pass noted the window under: the one the model looks the picture up by.
            return InnerLayers.isWindow(block, block.getMetaFromState(under));
        } catch (RuntimeException awkwardBlock) {
            // An id nothing answers to, or a block that cannot say its meta: drawn as it always was.
            return false;
        }
    }

    /**
     * The pane a square belongs to, or nought where it belongs to none.
     *
     * <p>
     * The 1.7.10 edition's {@code BlockGhost.shouldSideBeRendered}: {@code window && !sunken && world.getBlock(x, y, z)
     * == this}. "The same block" there is the same window twin, and the twin is chosen by the family the square wears
     * as, so two squares over different Chisel carvings that wear as one family share a pane. Not every window carries
     * the rule there, and none of these does here: a square wearing as grass (that edition's grass twins are another
     * class, with no such rule), a hollow one - sunk, or over a block that stands short, which
     * {@link BlockGhost#wholeAt}
     * says the way that edition's painter does - and a stair, which {@code wholeAt} counts as not whole. Nor clear ice,
     * which has no window twin there and keeps its own clear stand-in.
     */
    public static int paneAt(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null) return 0;
        if (!(world.getBlockState(pos)
            .getBlock() instanceof BlockGhost)) return 0;
        if (!windowOf(Trmt.proxy.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ()))) return 0;
        if (!BlockGhost.wholeAt(world, pos) || BlockGhost.clearCovers(world, pos) != null) return 0;
        SurfaceFamily wears = ErosionState
            .familyOf(Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ()));
        return wears == null || wears == SurfaceFamily.GRASS ? 0 : 1 + wears.ordinal();
    }
}
