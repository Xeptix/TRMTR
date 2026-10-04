package com.trmtgtnh.block;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * The metadata a worn stair would have if it were not being worn.
 *
 * <p>
 * A stair keeps two things in its metadata - which way it faces and which half of the block it sits
 * in - and a ghost keeps its wear gradation there instead. Everything in {@code BlockStairs} that
 * decides a shape reads that metadata, so left alone it reads a gradation as a compass bearing.
 *
 * <p>
 * The damage is not confined to the ghost. A stair asks its <em>neighbours</em> for their metadata
 * too, because two stairs facing the same way and sitting in the same half join into a corner, and
 * the test for that is a bare "is it a stair" followed by a comparison of the two values. A ghost
 * passes the first half, so an ordinary unworn stair standing beside a worn one compares its own
 * bearing against a wear gradation and rebuilds itself as a corner whenever the arithmetic happens
 * to agree - which is once every four gradations, and is why the artefact came and went in bands as
 * the ground wore in rather than simply being there or not.
 *
 * <p>
 * So the answer cannot live on the ghost's own class: the block reading the wrong number is the
 * block next door, which is a plain vanilla stair and knows nothing about any of this. It has to be
 * put back at the point of reading, which is what {@link com.trmtgtnh.mixin.MixinStairMetadata}
 * does with this.
 *
 * <p>
 * Asked through the proxy rather than through the client cache directly, so this class names
 * nothing client-only and a dedicated server can load it without complaint. Off the client the
 * proxy answers -1, which reads as "nothing recorded" and falls through to the real value.
 */
public final class GhostStairs {

    private GhostStairs() {}

    /**
     * The metadata at a position as the stair rules should see it.
     *
     * <p>
     * The real value everywhere but at a worn stair, and there the stair's own. Cheap enough to sit
     * in the middle of a chunk build: one block lookup and a type check, and the check fails on the
     * first comparison for every block in the world that is not one of ours.
     */
    public static int metadataAt(IBlockAccess world, int x, int y, int z) {
        if (world == null) return 0;
        int real = world.getBlockMetadata(x, y, z);
        Block block = world.getBlock(x, y, z);
        if (!(block instanceof GhostBlock)) return real;
        if (((GhostBlock) block).shape() != SurfaceShape.STAIR) return real;

        int packed = Trmt.proxy.originPackedAt(x, y, z);
        // Nothing recorded means nothing to put back, and a stair has to face somewhere. Zero is
        // the bottom half facing east, which is where an unplaced stair points anyway.
        return packed < 0 ? 0 : packed & 0xF;
    }
}
