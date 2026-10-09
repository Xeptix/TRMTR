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
import net.minecraft.world.phys.AABB;

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
     * The tint slot a grass-colored face asks for.
     *
     * <p>
     * Only grass takes the biome's color. Worn through to dirt, a square has no grass left to tint,
     * and a dirt rut washed green by a jungle would be a very strange road.
     */
    public static final int GRASS_TINT = 0;

    /** The tint slot for everything that carries its own color. */
    public static final int LIGHT_TINT = 1;

    /** Position, color, texture, light, normal: vanilla's block format, eight ints a vertex. */
    public static final int INTS_PER_VERTEX = 8;

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
        return build(record, origin, outline, rotation, fringeTurn, snowed, side, null);
    }

    /**
     * The same, for a square whose shape is not one box.
     *
     * <p>
     * {@code boxes} is the shape of the block being stood in for, broken into the boxes it is made
     * of, and is given only for a stair - every other shape a ghost covers is a single box and says
     * so through {@code outline}. A worn stair keeps the stair's own form and does not sink: a stair
     * fuses its drawn shape and its collision shape into one answer, so a dip in the picture would be
     * a dip the server has not got, and the server would spend every tick pushing whoever stood in it
     * back out of ground it believes is solid. What a worn stair shows is the wear, which was always
     * the larger half of the effect - the 1.7.10 edition says the same in {@code BlockGhostStairs},
     * where it reached the same conclusion by extending vanilla's stair block.
     */
    public static List<BakedQuad> build(short record, int origin, int outline, int rotation, int fringeTurn,
        boolean snowed, Direction side, List<AABB> boxes) {
        if (boxes != null && !boxes.isEmpty()) return stairs(record, origin, rotation, fringeTurn, snowed, side, boxes);
        SurfaceFamily appearance = ErosionState.familyOf(record);
        // The other edition's GhostLogic.bottomAt and heightAt, drawn: a slab from its own floor and never below
        // it - worn through, it would otherwise be a sheet of nothing hanging in its own space - and anything else
        // from the top of its cell, so a block already short loses its first pixel into its own shortfall. Until
        // 0.9.220 this sank everything from the block's own top; Xep chose the other edition's rule on 2026-10-08.
        float floor = (float) BlockGhost.bottomAt(origin);
        float height = (float) BlockGhost.heightAt(record, origin, outline, false);

        TextureAtlasSprite top = topOf(record, appearance, origin, rotation);
        TextureAtlasSprite earth = earth();
        // Said before any of this square's vertices are written, and only ever heard while the seat
        // is a ghost's: the block underneath while the square still wears as that block's own family,
        // the block of the family its wear has reached once it does not, and earth where the picture
        // is the bare earth - which is why the claim is made before that substitution, while a null
        // top still says so. The 1.7.10 edition's rule; see ShaderMaterial, which holds all of this.
        com.trmtgtnh.client.render.ShaderMaterial.claim(origin, top == null ? null : appearance);
        if (top == null) top = earth;
        // Grey-and-tinted, or its own colors? See GhostSides.tintsAsGrass - the square has to be
        // wearing as grass and the block under it has to be a lawn, because the picture is made from
        // that block's own pixels.
        int tint = GhostSides.tintsAsGrass(origin, appearance) ? GRASS_TINT : LIGHT_TINT;

        if (side == null) {
            return Collections.singletonList(quad(Direction.UP, floor, height, top, tint, 0F));
        }
        if (side == Direction.UP) {
            // The top is the one face asked for without a side, above. Asked for again with one, it
            // would be drawn twice.
            return Collections.emptyList();
        }
        // The underside goes through the same rule as the flanks rather than being dirt by decree.
        // Dirt was the answer for every block, and it is only the right one for a lawn - whose
        // underside really is dirt, and which reaches that answer below by being asked for its own
        // bottom face. A worn stone slab's underside is stone, and a stair's inner steps are the
        // stair's own material; both were drawn as earth, which is what the 1.7.10 edition's rule
        // never did: there, a block whose sides match its top wears on every face it has.
        GhostSides.Face face = GhostSides.of(record, origin, side.get3DDataValue(), rotation, fringeTurn, snowed);
        TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
        if (face.overlay == null) {
            return Collections
                .singletonList(quad(side, floor, height, flank, LIGHT_TINT, shiftFor(face.slid, outline, height)));
        }
        // Two quads at one place, the fringe after the flank, which is how vanilla's own grass model
        // draws its overlay: one element for the side and a second, coincident, for the tinted one.
        List<BakedQuad> both = new ArrayList<BakedQuad>(2);
        float shift = shiftFor(face.slid, outline, height);
        both.add(quad(side, floor, height, flank, LIGHT_TINT, shift));
        both.add(quad(side, floor, height, face.overlay, GRASS_TINT, shift));
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
    /**
     * The block this square tells a shader pack it is made of - the claim {@link #build} makes, for a
     * renderer that takes it as a material on each quad rather than from a seat. Canvas is the one: see
     * the Fabric module's {@code FrexMaterial}. Asked the same way {@code build} asks it, from the same
     * picture, so the two can never disagree about a square.
     */
    public static BlockState claimOf(short record, int origin, int rotation) {
        SurfaceFamily appearance = ErosionState.familyOf(record);
        TextureAtlasSprite top = topOf(record, appearance, origin, rotation);
        return com.trmtgtnh.client.render.ShaderMaterial.claimFor(origin, top == null ? null : appearance);
    }

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

    /**
     * Every face of every box a stair is made of, all of it uncullable.
     *
     * <p>
     * Handed back under the general bucket - the side vanilla asks for with no direction - rather
     * than sorted into the six faces. A face of a sub-box is not a face of the cell: the top of a
     * stair's lower step faces up in the middle of its own square, where vanilla's culling asks about
     * the neighbour above and would answer that the step's tread is hidden by thin air. Culling the
     * boxes against each other is the only thing given up, which is a few quads on a block somebody
     * has worn a path across.
     *
     * <p>
     * The sprites are the square's own: its wear picture on every upward face, the earth it stands in
     * on every downward one, and whatever {@code GhostSides} says for each compass face, so a worn
     * stair is the same material as the worn ground running up to it.
     */
    private static List<BakedQuad> stairs(short record, int origin, int rotation, int fringeTurn, boolean snowed,
        Direction side, List<AABB> boxes) {
        if (side != null) return Collections.emptyList();

        SurfaceFamily appearance = ErosionState.familyOf(record);
        TextureAtlasSprite top = topOf(record, appearance, origin, rotation);
        TextureAtlasSprite earth = earth();
        com.trmtgtnh.client.render.ShaderMaterial.claim(origin, top == null ? null : appearance);
        if (top == null) top = earth;
        // Grey-and-tinted, or its own colors? See GhostSides.tintsAsGrass - the square has to be
        // wearing as grass and the block under it has to be a lawn, because the picture is made from
        // that block's own pixels.
        int tint = GhostSides.tintsAsGrass(origin, appearance) ? GRASS_TINT : LIGHT_TINT;

        List<BakedQuad> out = new ArrayList<BakedQuad>(boxes.size() * 6);
        for (AABB box : boxes) {
            float x0 = (float) box.minX;
            float y0 = (float) box.minY;
            float z0 = (float) box.minZ;
            float x1 = (float) box.maxX;
            float y1 = (float) box.maxY;
            float z1 = (float) box.maxZ;

            out.add(quad(Direction.UP, x0, y0, z0, x1, y1, z1, top, tint, 0F));

            // The stair's own underside, by the same rule as its flanks. A stair is made of boxes
            // stacked within one cell, so most of these faces are the undersides of its steps -
            // stone on a stone stair, and dirt only where the stair really is a lawn.
            GhostSides.Face below = GhostSides
                .of(record, origin, Direction.DOWN.get3DDataValue(), rotation, fringeTurn, snowed);
            out.add(
                quad(
                    Direction.DOWN,
                    x0,
                    y0,
                    z0,
                    x1,
                    y1,
                    z1,
                    below.sprite == null ? earth : below.sprite,
                    LIGHT_TINT,
                    0F));

            for (Direction compass : Direction.Plane.HORIZONTAL) {
                GhostSides.Face face = GhostSides
                    .of(record, origin, compass.get3DDataValue(), rotation, fringeTurn, snowed);
                TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
                out.add(quad(compass, x0, y0, z0, x1, y1, z1, flank, LIGHT_TINT, 0F));
                if (face.overlay != null) {
                    out.add(quad(compass, x0, y0, z0, x1, y1, z1, face.overlay, GRASS_TINT, 0F));
                }
            }
        }
        return out;
    }

    /** One face of the box from the floor of the cell to {@code height}, across the whole footprint. */
    private static BakedQuad quad(Direction side, float floor, float height, TextureAtlasSprite sprite, int tint,
        float shiftRows) {
        return quad(side, 0F, floor, 0F, 1F, height, 1F, sprite, tint, shiftRows);
    }

    /**
     * How far down to slide a side texture, in sprite rows, for a square that has sunk.
     *
     * <p>
     * The distance the ground has actually dropped from the top of the block it stands in for, never
     * the whole crop. Clamped as the 1.7.10 edition clamps it: nothing below nought, nothing past
     * fifteen, so a shift can never walk off the end of a sprite.
     */
    private static float shiftFor(boolean slid, int outline, float height) {
        if (!slid) return 0F;
        long rows = Math.round(16.0D * (BlockGhost.topOf(outline) - height));
        return rows <= 0L ? 0F : Math.min(rows, 15L);
    }

    /**
     * One face of any box, not only of a square's whole footprint.
     *
     * <p>
     * A ghost is a box over a cell, so for every shape but one the footprint is the whole cell and
     * the ends are 0 and 1. A stair is the exception: it is drawn as the boxes its own shape is made
     * of, each with its own ends, which is what lets a worn stair keep the shape of the stair it
     * covers rather than standing in for it as a cube.
     *
     * <p>
     * The texture coordinates stay in the cell's frame rather than the box's - u from x, v from z,
     * both times sixteen - so a box covering half a cell takes half a texture, the half it covers.
     * That is what makes the boxes of a stair read as one worn surface rather than as two small ones,
     * each stretched to a full picture.
     */
    private static BakedQuad quad(Direction side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, int tint, float shiftRows) {
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
                // The renderer nails a side face's texture to the bottom of the cell: the window is
                // [16 - 16*height, 16] in sprite rows, so shortening a block from the top throws away
                // the rows at the top of the sprite. For a surface whose sides are one uniform
                // texture that is right - a rut cut into sand shows sand all the way down the wall.
                //
                // For a made surface it is wrong: a path's side is soil with a pale cap along its top
                // edge, and cropping from the top eats the cap. So the window slides down by however
                // far the ground has actually dropped, which puts its top edge at 16 - 16*originTop -
                // a figure that does not depend on the sink depth at all. A path stands fifteen
                // sixteenths high, so its window starts at row one and stays there however deep the
                // rut gets, and row nought - which is transparent on grass_path_side - is never
                // sampled. See SideShift in the 1.7.10 edition, whose arithmetic this is.
                v = (1F - c[1]) * 16F - shiftRows;
            }
            at = put(packed, at, c[0], c[1], c[2], sprite.getU(u), sprite.getV(v), side);
        }
        return new BakedQuad(packed, tint, side, sprite, true);
    }

    /**
     * One vertex, in the eight ints vanilla's block format wants.
     *
     * <p>
     * Position as three floats, then the color, then the texture as two floats, then the packed
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
