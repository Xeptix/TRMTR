package com.trmtgtnh.client.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import com.trmtgtnh.mixin.MixinSpriteEdges;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * This edition's half of the shared edge inset (0.9.229, EdgeInset): a worn square's picture is drawn from
 * {@link EdgeInset#TEXELS} of a texel inside its edges, as every other edition's ghost faces are.
 *
 * <p>
 * The other editions build the ghost's faces themselves and clamp each coordinate. Here the faces are the standard
 * renderer's - RenderBlocks, OptiFine's version of it, or Angelica's and Celeritas's own meshers, all of which read the
 * sprite's edges - so the edges are moved on this mod's own sprites alone, once each stitch, and every renderer draws
 * them inset without any of them being changed. The game's own initSprite already pulls every sprite in by a hundredth
 * of a pixel ({@link #GAME_INSET}); this adds what makes it the shared inset. The middle of the picture stays exactly
 * where it was, and a point between moves by less than a thirty-second of a texel.
 *
 * <p>
 * Faces drawn with the game's own pictures - a flank of plain dirt, a family's stock top where no wear picture exists -
 * keep the game's hundredth, as every block drawn with those pictures does.
 */
@SideOnly(Side.CLIENT)
public final class SpriteEdges {

    private SpriteEdges() {}

    /** What TextureAtlasSprite.initSprite in 1.7.10 pulls each edge in by, in atlas pixels (read from its bytecode). */
    public static final double GAME_INSET = 0.009999999776482582D;

    /**
     * Pulls a sprite the game has just placed further in, to the shared inset. A sprite too small to hold it is left.
     */
    public static void inset(TextureAtlasSprite sprite, int atlasWidth, int atlasHeight) {
        float[] to = edges(
            sprite.getMinU(),
            sprite.getMaxU(),
            sprite.getMinV(),
            sprite.getMaxV(),
            atlasWidth,
            atlasHeight);
        if (to == null) return;
        MixinSpriteEdges edges = (MixinSpriteEdges) (Object) sprite;
        edges.trmt$setMinU(to[0]);
        edges.trmt$setMaxU(to[1]);
        edges.trmt$setMinV(to[2]);
        edges.trmt$setMaxV(to[3]);
    }

    /**
     * The edges {@link #inset} gives a sprite the game placed at these, as {minU, maxU, minV, maxV}, or null where the
     * atlas is unknown or the sprite too small to hold the inset.
     */
    public static float[] edges(float minU, float maxU, float minV, float maxV, int atlasWidth, int atlasHeight) {
        if (atlasWidth <= 0 || atlasHeight <= 0) return null;
        float du = (float) ((EdgeInset.TEXELS - GAME_INSET) / atlasWidth);
        float dv = (float) ((EdgeInset.TEXELS - GAME_INSET) / atlasHeight);
        if (maxU - minU <= 2F * du || maxV - minV <= 2F * dv) return null;
        return new float[] { minU + du, maxU - du, minV + dv, maxV - dv };
    }
}
