package com.trmtgtnh.client.model;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Draws a ghost block: a worn top at the height the square has sunk to, over earth.
 *
 * <p>
 * This is the class that stands in for the heart of the 1.7.10 edition, and it is the reason this
 * spike was run before anything else was ported. There, a block's appearance was chosen by
 * {@code getIcon(world, x, y, z, side)} - handed the position, asked for a texture per face - and
 * 1.12.2 has no such method. Appearance is a baked model now, and a baked model is handed a block
 * state, not a place. What bridges the two is the extended state: the block reads the position in
 * {@code getExtendedState} and writes the square's whole record into an unlisted property, and this
 * reads it back out here - which appearance, which layer of it, and how deep. Same information,
 * carried by a different road.
 *
 * <p>
 * Built as quads directly rather than from a JSON model, because the geometry itself varies: the top
 * face sits at a height that depends on the gradation, and a JSON model has one fixed shape per
 * variant. Eighty gradations would be eighty variants of every family, which is exactly the
 * enumeration unlisted properties exist to avoid.
 *
 * <p>
 * The top face is returned as a general quad rather than as the {@code UP} face, and that matters.
 * Face quads are culled against the neighbour on that side, and the neighbour above a worn square is
 * air or a player's feet - but a face that is not at the top of the cell must never be treated as
 * though it were. Handed over with no side, it is always drawn.
 */
@SideOnly(Side.CLIENT)
public class GhostBakedModel implements IBakedModel {

    /**
     * Seven ints a vertex: position, color, texture, lightmap. The block format, written by hand.
     *
     * <p>
     * <strong>This is the number OptiFine's shaders broke the old quads on, and writing it here is
     * the fix.</strong> The model used to hand back {@code UnpackedBakedQuad}s, which size their
     * packed array from {@code format.getNextOffset()} when they are constructed and fill it from the
     * same format later. That is safe only while the format's stride stays put - and
     * {@code VertexFormat} is mutable and shared, so when a shader pack loads and the chunk format
     * grows, an array measured before the growth is written past its end. Forge's
     * {@code LightUtil.pack} does exactly that and does not check: the loop even carries a
     * {@code // TODO handle overflow} where the bounds test would go.
     *
     * <p>
     * Seen three ways on 2026-10-05 under OptiFine HD U G5, all of them with the same cause. In the
     * item format the hollow drew as enormous stretched blades; in the block format it drew as a
     * regular chevron pattern; and switching shaders off mid-session crashed the game outright with
     * {@code ArrayIndexOutOfBoundsException: 28} inside {@code UnpackedBakedQuad.getVertexData},
     * tesselating {@code trmtgtnh:ghost_grass}. Twenty-eight is four vertices of seven ints - one int
     * past the end of exactly this array.
     *
     * <p>
     * So the quads are packed here instead, in the layout vanilla's own blocks use, and handed over
     * as a plain {@link BakedQuad}. The renderer copies that with {@code addVertexData}, which is the
     * path OptiFine instruments and expands for itself. Nothing reads a stride that something else
     * can change underneath it.
     */
    private static final int INTS_PER_VERTEX = 7;

    /** The tint index grass takes its biome color through; see the block color registration. */
    public static final int GRASS_TINT = 0;

    /**
     * The tint index every other face takes a path light's color through.
     *
     * <p>
     * The other edition gives a ghost one color for the whole block - biome, glow and all multiplied
     * together - because that is how 1.7.10 asks. 1.12.2 asks per face, and only of a face that carries
     * a tint index, so a face with none could never be lit. Every face carries one or the other: grass
     * takes its biome color with the glow multiplied in, and everything else takes the glow alone,
     * which is plain white on a square nobody has lit and so no change at all.
     */
    public static final int LIGHT_TINT = 1;

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        short record = ErosionState.NONE;
        if (state instanceof IExtendedBlockState) {
            Integer held = ((IExtendedBlockState) state).getValue(BlockGhost.RECORD);
            if (held != null) record = (short) held.intValue();
        }
        int origin = -1;
        int outline = BlockGhost.WHOLE_CUBE;
        int rotation = 0;
        int fringeTurn = 0;
        boolean snowed = false;
        int stair = -1;
        if (state instanceof IExtendedBlockState) {
            IExtendedBlockState extended = (IExtendedBlockState) state;
            Integer held = extended.getValue(BlockGhost.ORIGIN);
            if (held != null) origin = held.intValue();
            held = extended.getValue(BlockGhost.ROTATION);
            if (held != null) rotation = held.intValue();
            held = extended.getValue(BlockGhost.FRINGE_TURN);
            if (held != null) fringeTurn = held.intValue();
            held = extended.getValue(BlockGhost.SNOWED);
            if (held != null) snowed = held.intValue() != 0;
            held = extended.getValue(BlockGhost.OUTLINE);
            if (held != null) outline = held.intValue();
            held = extended.getValue(BlockGhost.STAIR);
            if (held != null) stair = held.intValue();
        }

        // Nothing at all in the passes this square is not drawn in. The ghost offers itself to every
        // pass a covered block might use - see BlockGhost.canRenderInLayer - and this is where all
        // but one of them are turned away, so a worn ice block is drawn with the translucent blocks
        // and worn stone with the solid ones, exactly as the blocks they stand in for are.
        //
        // Null outside a chunk rebuild, which is an item or a particle asking; those are drawn.
        net.minecraft.util.BlockRenderLayer drawing = net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();
        if (drawing != null && drawing != BlockGhost.layerOf(origin)) return Collections.emptyList();

        // A stair is a shape rather than a height, so nothing below this line applies to one: it is
        // drawn as the boxes its own shape is made of and handed back whole. See stairs().
        if (stair >= 0) return stairs(record, origin, rotation, fringeTurn, snowed, side, stair);

        SurfaceFamily appearance = ErosionState.familyOf(record);
        // The other edition's GhostLogic.bottomAt and heightAt, drawn: a slab from its own floor and never below
        // it - worn through, it would otherwise be a sheet of nothing hanging in its own space - and anything else
        // from the top of its cell, so a block already short loses its first pixel into its own shortfall. Until
        // 0.9.220 this sank everything from the block's own top; Xep chose the other edition's rule on 2026-10-08.
        float floor = (float) BlockGhost.bottomAt(origin);
        float height = (float) BlockGhost.heightAt(record, origin, outline, false);

        TextureAtlasSprite top = topOf(record, appearance, origin, rotation);
        TextureAtlasSprite earth = earth();
        // Said before any of this square's vertices are written, and only ever heard while the seat is
        // a ghost's: the block underneath while the square still wears as that block's own family, the
        // block of the family its wear has reached once it does not, and earth where the picture is the
        // bare earth - which is why the claim is made before that substitution, while a null top still
        // says so. The 1.7.10 edition's rule; see ShaderMaterial.
        com.trmtgtnh.client.render.ShaderMaterial.claim(origin, top == null ? null : appearance);
        if (top == null) top = earth;
        // Only grass takes the biome's color. Worn through to dirt, a square has no grass left to
        // tint, and a dirt rut washed green by a jungle would be a very strange road.
        // Grey-and-tinted, or its own colors? See GhostSides.tintsAsGrass - the square has to be
        // wearing as grass and the block under it has to be a lawn, because the picture is made from
        // that block's own pixels.
        int tint = GhostSides.tintsAsGrass(origin, appearance) ? GRASS_TINT : LIGHT_TINT;

        // The top is handed over with no side, so nothing culls it, because a sunk top is not a face of the
        // cell at all and the neighbour above says nothing about it. A whole window's top is a face of the
        // cell, and is handed over as one, so that a window above it of the same pane hides it as glass does -
        // the other edition's shouldSideBeRendered asks this of every side, the top included (2026-10-08).
        boolean paned = floor <= 0F && height >= 1F && GhostWindows.windowOf(origin);
        if (side == null) {
            return paned ? Collections.<BakedQuad>emptyList()
                : Collections.singletonList(quad(EnumFacing.UP, floor, height, top, tint, 0F));
        }
        if (side == EnumFacing.UP) {
            return paned ? Collections.singletonList(quad(EnumFacing.UP, floor, height, top, tint, 0F))
                : Collections.<BakedQuad>emptyList();
        }
        // The underside goes through the same rule as the flanks rather than being dirt by decree.
        // Dirt was the answer for every block, and it is only the right one for a lawn - whose
        // underside really is dirt, and which reaches that answer below by being asked for its own
        // bottom face. A worn stone slab's underside is stone, and a stair's inner steps are the
        // stair's own material; both were drawn as earth, which is what the 1.7.10 edition's rule
        // never did: there, a block whose sides match its top wears on every face it has.
        GhostSides.Face face = GhostSides.of(record, origin, side.getIndex(), rotation, fringeTurn, snowed);
        TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
        if (face.overlay == null) {
            return Collections
                .singletonList(quad(side, floor, height, flank, LIGHT_TINT, shiftFor(face.slid, outline, height)));
        }
        // Two quads at one place, the fringe after the flank, which is how vanilla's own grass model draws
        // its overlay: one element for the side and a second, coincident, for the tinted overlay.
        List<BakedQuad> both = new java.util.ArrayList<BakedQuad>(2);
        float shift = shiftFor(face.slid, outline, height);
        both.add(quad(side, floor, height, flank, LIGHT_TINT, shift));
        both.add(quad(side, floor, height, face.overlay, GRASS_TINT, shift));
        return both;
    }

    /**
     * Every face of every box a stair is made of, all of it uncullable.
     *
     * <p>
     * Handed back under the general bucket - the side vanilla asks for with no direction - rather than
     * sorted into the six faces. A face of a sub-box is not a face of the cell: the top of a stair's
     * lower step faces up in the middle of its own square, where vanilla's culling asks about the
     * neighbour above and would answer that the step's tread is hidden by thin air. Culling the boxes
     * against each other is the only thing given up, which is a few quads on a block somebody has worn
     * a path across.
     *
     * <p>
     * The sprites are the square's own: its wear picture on every upward face, the earth it stands in
     * on every downward one, and whatever {@code GhostSides} says for each compass face, so a worn
     * stair is the same material as the worn ground running up to it.
     */
    private static List<BakedQuad> stairs(short record, int origin, int rotation, int fringeTurn, boolean snowed,
        @Nullable EnumFacing side, int stair) {
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

        java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes = BlockGhost.stairBoxes(stair);
        List<BakedQuad> out = new java.util.ArrayList<BakedQuad>(boxes.size() * 6);
        for (net.minecraft.util.math.AxisAlignedBB box : boxes) {
            float x0 = (float) box.minX;
            float y0 = (float) box.minY;
            float z0 = (float) box.minZ;
            float x1 = (float) box.maxX;
            float y1 = (float) box.maxY;
            float z1 = (float) box.maxZ;

            out.add(quad(EnumFacing.UP, x0, y0, z0, x1, y1, z1, top, tint, 0F));

            // The stair's own underside, by the same rule as its flanks. A stair is made of boxes
            // stacked within one cell, so most of these faces are the undersides of its steps -
            // stone on a stone stair, and dirt only where the stair really is a lawn.
            GhostSides.Face below = GhostSides
                .of(record, origin, EnumFacing.DOWN.getIndex(), rotation, fringeTurn, snowed);
            out.add(
                quad(
                    EnumFacing.DOWN,
                    x0,
                    y0,
                    z0,
                    x1,
                    y1,
                    z1,
                    below.sprite == null ? earth : below.sprite,
                    LIGHT_TINT,
                    0F));

            for (EnumFacing compass : EnumFacing.HORIZONTALS) {
                GhostSides.Face face = GhostSides.of(record, origin, compass.getIndex(), rotation, fringeTurn, snowed);
                TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
                out.add(quad(compass, x0, y0, z0, x1, y1, z1, flank, LIGHT_TINT, 0F));
                if (face.overlay != null) {
                    out.add(quad(compass, x0, y0, z0, x1, y1, z1, face.overlay, GRASS_TINT, 0F));
                }
            }
        }
        return out;
    }

    /**
     * The wear picture for this square's top, or null where there is none.
     *
     * <p>
     * The other edition's {@code GhostRendering.iconFor}, top face: the record placed in the counted space,
     * then this surface's own set by the block it covers, then its family's. What the square is standing over
     * is turned back from the state id the painter remembered; nothing remembered means the family's set.
     */
    private static TextureAtlasSprite topOf(short record, SurfaceFamily appearance, int origin, int rotation) {
        if (appearance == null || !BlockGhost.shows(record)) return null;
        net.minecraft.block.Block block = null;
        int meta = 0;
        SurfaceFamily family = null;
        if (origin >= 0) {
            try {
                IBlockState under = net.minecraft.block.Block.getStateById(origin);
                block = under.getBlock();
                meta = block.getMetaFromState(under);
                family = com.trmtgtnh.surface.SurfaceRegistry.familyOf(block, meta);
            } catch (RuntimeException awkwardBlock) {
                block = null;
            }
        }
        if (family == null) family = appearance;
        int step = WearSteps.topLayer(appearance, record);
        return WearTextures.icon(block, meta, family, appearance, step, rotation);
    }

    private static TextureAtlasSprite earth() {
        return Minecraft.getMinecraft()
            .getTextureMapBlocks()
            .getAtlasSprite("minecraft:blocks/dirt");
    }

    /**
     * One face of the box from the floor of the cell to {@code height}.
     *
     * <p>
     * Corners in the order vanilla's own face baker uses, so the winding - and therefore which way
     * the face points and whether it survives back-face culling - is the game's, not a guess. A face
     * wound the wrong way does not look wrong; it is simply not there.
     */
    private static BakedQuad quad(EnumFacing side, float floor, float height, TextureAtlasSprite sprite, int tint,
        float shiftRows) {
        return quad(side, 0F, floor, 0F, 1F, height, 1F, sprite, tint, shiftRows);
    }

    /**
     * How far down to slide a side texture, in sprite rows, for a square that has sunk.
     *
     * <p>
     * The distance the ground has actually dropped from the top of the block it stands in for, never
     * the whole crop. Clamped as the other edition clamps it: nothing below nought, nothing past
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
     * A ghost is a box over a cell, so for every shape but one the footprint is the whole cell and the
     * ends are 0 and 1. A stair is the exception: it is drawn as the boxes its own shape is made of,
     * each with its own ends, which is what lets a worn stair keep the shape of the stair it covers
     * rather than standing in for it as a cube.
     *
     * <p>
     * The texture coordinates stay in the cell's frame rather than the box's - u from x, v from z, both
     * times sixteen - so a box covering half a cell takes half a texture, the half it covers. That is
     * what makes the boxes of a stair read as one worn surface rather than as two small ones, each
     * stretched to a full picture.
     */
    private static BakedQuad quad(EnumFacing side, float x0, float y0, float z0, float x1, float y1, float z1,
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

        int[] data = new int[4 * INTS_PER_VERTEX];
        int at = 0;
        for (float[] c : corners) {
            float u, v;
            if (side.getAxis() == EnumFacing.Axis.Y) {
                u = c[0] * 16F;
                v = c[2] * 16F;
            } else {
                u = (side.getAxis() == EnumFacing.Axis.X ? c[2] : c[0]) * 16F;
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
                // sampled. This is the other edition's SideShift arithmetic.
                v = (1F - c[1]) * 16F - shiftRows;
            }
            data[at] = Float.floatToRawIntBits(c[0]);
            data[at + 1] = Float.floatToRawIntBits(c[1]);
            data[at + 2] = Float.floatToRawIntBits(c[2]);
            // White, so that a tint multiplies cleanly and an untinted face is left alone.
            data[at + 3] = -1;
            data[at + 4] = Float.floatToRawIntBits(sprite.getInterpolatedU(u));
            data[at + 5] = Float.floatToRawIntBits(sprite.getInterpolatedV(v));
            // The lightmap, which the renderer fills in; a model that guessed here would be arguing
            // with the lighting it is about to be given.
            data[at + 6] = 0;
            at += INTS_PER_VERTEX;
        }
        return new BakedQuad(data, tint, side, sprite, true, DefaultVertexFormats.BLOCK);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return earth();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
