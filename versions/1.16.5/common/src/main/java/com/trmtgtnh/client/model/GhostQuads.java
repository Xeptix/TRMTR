package com.trmtgtnh.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * What a worn square looks like, as quads, given everything that decides it.
 *
 * <p>
 * This is the shared half of the 1.12.2 edition's {@code GhostBakedModel}, and the split is the one
 * {@code docs/ERA-A-RENDERING.md} designed. That class is a {@code IBakedModel} that reads six
 * numbers out of the blockstate and then builds the picture; here the reading is the only part that
 * differs between loaders, so it moves out to a model class each, and everything from the numbers
 * onward lives here and is written once.
 *
 * <p>
 * <strong>The vertices are packed by hand, and that is a real cost.</strong> The 1.12.2 edition
 * builds its quads through Forge's {@code UnpackedBakedQuad.Builder}, which validates what this does
 * not; Fabric has {@code QuadEmitter} and the two have nothing in common. A {@code BakedQuad} made
 * from a packed {@code int[]} is the one thing both loaders take, so that is what this makes - eight
 * ints a vertex, in vanilla's own block format.
 *
 * <p>
 * Corners are in the order vanilla's own face baker uses, so the winding - and therefore which way a
 * face points and whether it survives back-face culling - is the game's rather than a guess. A face
 * wound the wrong way does not look wrong; it is simply not there.
 */
public final class GhostQuads {

    /**
     * The tint slot a grass-coloured face asks for.
     *
     * <p>
     * Only grass takes the biome's colour. Worn through to dirt, a square has no grass left to tint,
     * and a dirt rut washed green by a jungle would be a very strange road.
     */
    public static final int GRASS_TINT = 0;

    /** The tint slot for everything that carries its own colour. */
    public static final int LIGHT_TINT = 1;

    /** Position, colour, texture, light, normal: vanilla's block format, eight ints a vertex. */
    private static final int INTS_PER_VERTEX = 8;

    private GhostQuads() {}

    /**
     * The quads for one face of one worn square.
     *
     * <p>
     * Every argument is something the loader's model had to find out for itself - the record, what
     * the square stands in for, the outline of that block, and the three turns and the snow that
     * decide which picture goes on which side. Both older editions carry all six in the blockstate;
     * neither loader here can, which is the whole of why this is a separate class.
     */
    public static List<BakedQuad> build(short record, int origin, int outline, int rotation, int fringeTurn,
        boolean snowed, Direction side) {
        SurfaceFamily appearance = ErosionState.familyOf(record);
        float floor = BlockGhost.floorOf(outline);
        // Sunk from the block's own top rather than from the top of its cell, and never below its
        // floor: a slab worn through would otherwise be drawn as a sheet of nothing hanging in its
        // own space.
        float height = Math.max(floor, BlockGhost.topOf(outline) - WearSteps.sunk(record, outline));

        TextureAtlasSprite top = topOf(record, appearance, origin, rotation);
        TextureAtlasSprite earth = earth();
        // Said before any of this square's vertices are written, and only ever heard while the seat
        // is a ghost's: the block underneath while its own surface is still showing, and the earth it
        // has worn into once it is not. The same answer decides the claim and the picture, which is
        // the whole of why the shine goes as the road breaks up rather than the moment it is walked
        // on. See ShaderMaterial, which holds all of this.
        com.trmtgtnh.client.render.ShaderMaterial.claim(top == null ? -1 : origin);
        if (top == null) top = earth;
        int tint = appearance == SurfaceFamily.GRASS ? GRASS_TINT : LIGHT_TINT;

        if (side == null) {
            return Collections.singletonList(quad(Direction.UP, floor, height, top, tint, false));
        }
        if (side == Direction.UP) {
            // The top is the one face asked for without a side, above. Asked for again with one, it
            // would be drawn twice.
            return Collections.emptyList();
        }
        if (side == Direction.DOWN) {
            // The underside of a square that has not moved: never seen unless the ground below is
            // gone, and the earth it was sitting on is the honest answer when it is.
            return Collections.singletonList(quad(side, floor, height, earth, LIGHT_TINT, false));
        }

        GhostSides.Face face = GhostSides.of(record, origin, side.get3DDataValue(), rotation, fringeTurn, snowed);
        TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
        if (face.overlay == null) {
            return Collections.singletonList(quad(side, floor, height, flank, LIGHT_TINT, face.slid));
        }
        // Two quads at one place, the fringe after the flank, which is how vanilla's own grass model
        // draws its overlay: one element for the side and a second, coincident, for the tinted one.
        List<BakedQuad> both = new ArrayList<BakedQuad>(2);
        both.add(quad(side, floor, height, flank, LIGHT_TINT, face.slid));
        both.add(quad(side, floor, height, face.overlay, GRASS_TINT, face.slid));
        return both;
    }

    /**
     * The wear picture for this square's top, or null where there is none.
     *
     * <p>
     * The record placed in the counted space, then this surface's own set by the block it covers,
     * then its family's. What the square is standing over is turned back from the state id the
     * painter remembered; nothing remembered means the family's set.
     */
    private static TextureAtlasSprite topOf(short record, SurfaceFamily appearance, int origin, int rotation) {
        if (appearance == null || !BlockGhost.shows(record)) return null;
        Block block = null;
        SurfaceFamily family = null;
        if (origin >= 0) {
            try {
                BlockState under = Block.stateById(origin);
                block = under.getBlock();
                family = SurfaceRegistry.familyOf(block);
            } catch (RuntimeException awkwardBlock) {
                block = null;
            }
        }
        if (family == null) family = appearance;
        int step = WearSteps.topLayer(appearance, record);
        // The metadata value both older editions pass is nought here, and still passed, because the
        // surface table is keyed on the pair. See GhostSides.metaOf.
        return WearTextures.icon(block, 0, family, appearance, step, rotation);
    }

    /** Vanilla's dirt, which is what a square shows where it has nothing of its own left. */
    private static TextureAtlasSprite earth() {
        Minecraft game = Minecraft.getInstance();
        if (game == null || game.getModelManager() == null) return null;
        TextureAtlas atlas = game.getModelManager()
            .getAtlas(TextureAtlas.LOCATION_BLOCKS);
        return atlas == null ? null : atlas.getSprite(new ResourceLocation("minecraft", "block/dirt"));
    }

    /** One face of the box from the floor of the cell to {@code height}. */
    private static BakedQuad quad(Direction side, float floor, float height, TextureAtlasSprite sprite, int tint,
        boolean slid) {
        float x0 = 0F, x1 = 1F, z0 = 0F, z1 = 1F, y0 = floor, y1 = height;
        float[][] corners;
        switch (side) {
            case UP:
                corners = new float[][] { { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 } };
                break;
            case DOWN:
                corners = new float[][] { { x0, y0, z1 }, { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 } };
                break;
            case NORTH:
                corners = new float[][] { { x1, y1, z0 }, { x1, y0, z0 }, { x0, y0, z0 }, { x0, y1, z0 } };
                break;
            case SOUTH:
                corners = new float[][] { { x0, y1, z1 }, { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 } };
                break;
            case WEST:
                corners = new float[][] { { x0, y1, z0 }, { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 } };
                break;
            case EAST:
            default:
                corners = new float[][] { { x1, y1, z1 }, { x1, y0, z1 }, { x1, y0, z0 }, { x1, y1, z0 } };
                break;
        }

        int[] packed = new int[INTS_PER_VERTEX * 4];
        int at = 0;
        for (float[] c : corners) {
            float u;
            float v;
            if (side.getAxis() == Direction.Axis.Y) {
                u = c[0] * 16F;
                v = c[2] * 16F;
            } else {
                u = (side.getAxis() == Direction.Axis.X ? c[2] : c[0]) * 16F;
                // The earth under a sunken square shows its top rows, not a squashed whole: a side
                // half a block tall takes half a texture, taken from the top.
                //
                // Unless the picture slides with the surface, which is what a face with a cap along
                // its top edge wants: the window starts at the texture's own top row wherever the
                // square has sunk to, so the cap rides the new surface rather than staying up at the
                // height the ground used to be.
                v = (slid ? height - c[1] : 1F - c[1]) * 16F;
            }
            at = put(packed, at, c[0], c[1], c[2], sprite.getU(u), sprite.getV(v), side);
        }
        return new BakedQuad(packed, tint, side, sprite, true);
    }

    /**
     * One vertex, in the eight ints vanilla's block format wants.
     *
     * <p>
     * Position as three floats, then the colour, then the texture as two floats, then the packed
     * light, then the normal as four signed bytes. White and unlit, because a chunk's own lighting
     * is applied over this - writing anything else here would be a second tint nobody asked for.
     */
    private static int put(int[] out, int at, float x, float y, float z, float u, float v, Direction side) {
        out[at++] = Float.floatToRawIntBits(x);
        out[at++] = Float.floatToRawIntBits(y);
        out[at++] = Float.floatToRawIntBits(z);
        out[at++] = 0xFFFFFFFF;
        out[at++] = Float.floatToRawIntBits(u);
        out[at++] = Float.floatToRawIntBits(v);
        out[at++] = 0;
        out[at++] = packedNormal(side);
        return at;
    }

    /** A face's normal, as the four bytes the format keeps it in. */
    private static int packedNormal(Direction side) {
        int x = (byte) (side.getStepX() * 127) & 0xFF;
        int y = (byte) (side.getStepY() * 127) & 0xFF;
        int z = (byte) (side.getStepZ() * 127) & 0xFF;
        return x | (y << 8) | (z << 16);
    }
}
