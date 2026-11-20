package com.trmtgtnh.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;

import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.block.GhostRendering;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws worn ground over a block a data file gives boxes (W3 spec 2.4, task T6): each box on its own, through the
 * standard block renderer, with the ghost's own worn pictures on its top and its sides, the walked boxes' tops lowered
 * by the drawn depth.
 *
 * <p>
 * Through {@code RenderBlocks.renderStandardBlock} with the block's bounds and the render bounds set to each box in
 * turn, so every face is culled, lit, tinted and shaded exactly as the one-box ghost's are, and so whatever meshes the
 * standard renderer meshes this too: Angelica and Celeritas call {@code renderBlockByRenderType} into the
 * {@code Tessellator} and copy what it wrote (W3 T8's reading of Angelica 2.2.29), and this is called from inside it.
 * The bounds are set back round all the boxes afterwards, which is what the rest of the frame reads. Faces where two
 * boxes touch are drawn inside the other box, where nothing can see them.
 */
@SideOnly(Side.CLIENT)
public final class GhostBoxes {

    private GhostBoxes() {}

    /** Draws the ghost at a position box by box, and answers true; false where its block has no boxes. */
    public static boolean drawn(RenderBlocks renderer, GhostBlock ghost, int x, int y, int z) {
        double[][] boxes = GhostRendering.drawnBoxes(ghost, renderer.blockAccess, x, y, z);
        if (boxes == null) return false;
        Block block = ghost.asBlock();
        try {
            for (double[] box : boxes) {
                block.setBlockBounds(
                    (float) box[0],
                    (float) box[1],
                    (float) box[2],
                    (float) box[3],
                    (float) box[4],
                    (float) box[5]);
                renderer.setRenderBoundsFromBlock(block);
                renderer.renderStandardBlock(block, x, y, z);
            }
        } finally {
            block.setBlockBoundsBasedOnState(renderer.blockAccess, x, y, z);
            renderer.setRenderBoundsFromBlock(block);
        }
        return true;
    }
}
