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
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
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

    private static final VertexFormat FORMAT = DefaultVertexFormats.ITEM;

    /** The tint index grass takes its biome colour through; see the block colour registration. */
    public static final int GRASS_TINT = 0;

    /**
     * The tint index every other face takes a path light's colour through.
     *
     * <p>
     * The other edition gives a ghost one colour for the whole block - biome, glow and all multiplied
     * together - because that is how 1.7.10 asks. 1.12.2 asks per face, and only of a face that carries
     * a tint index, so a face with none could never be lit. Every face carries one or the other: grass
     * takes its biome colour with the glow multiplied in, and everything else takes the glow alone,
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
        }
        SurfaceFamily appearance = ErosionState.familyOf(record);
        float floor = BlockGhost.floorOf(outline);
        // Sunk from the block's own top rather than from the top of its cell, and never below its floor: a
        // slab worn through would otherwise be drawn as a sheet of nothing hanging in its own space.
        float height = Math
            .max(floor, BlockGhost.topOf(outline) - com.trmtgtnh.client.model.WearSteps.sunk(record, outline));

        TextureAtlasSprite top = topOf(record, appearance, origin, rotation);
        TextureAtlasSprite earth = earth();
        // Said before any of this square's vertices are written, and only ever heard while the seat is
        // a ghost's: the block underneath while its own surface is still showing, and the earth it has
        // worn into once it is not. The same answer decides the claim and the picture, which is the
        // whole of why the shine goes as the road breaks up rather than the moment it is walked on.
        com.trmtgtnh.client.render.ShaderMaterial.claim(top == null ? -1 : origin);
        if (top == null) top = earth;
        // Only grass takes the biome's colour. Worn through to dirt, a square has no grass left to
        // tint, and a dirt rut washed green by a jungle would be a very strange road.
        int tint = appearance == SurfaceFamily.GRASS ? GRASS_TINT : LIGHT_TINT;

        if (side == null) {
            return Collections.singletonList(quad(EnumFacing.UP, floor, height, top, tint, false));
        }
        if (side == EnumFacing.UP) {
            return Collections.emptyList();
        }
        if (side == EnumFacing.DOWN) {
            // The underside of a square that has not moved: never seen unless the ground below is gone, and
            // the earth it was sitting on is the honest answer when it is.
            return Collections.singletonList(quad(side, floor, height, earth, LIGHT_TINT, false));
        }

        GhostSides.Face face = GhostSides.of(record, origin, side.getIndex(), rotation, fringeTurn, snowed);
        TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
        if (face.overlay == null) {
            return Collections.singletonList(quad(side, floor, height, flank, LIGHT_TINT, face.slid));
        }
        // Two quads at one place, the fringe after the flank, which is how vanilla's own grass model draws
        // its overlay: one element for the side and a second, coincident, for the tinted overlay.
        List<BakedQuad> both = new java.util.ArrayList<BakedQuad>(2);
        both.add(quad(side, floor, height, flank, LIGHT_TINT, face.slid));
        both.add(quad(side, floor, height, face.overlay, GRASS_TINT, face.slid));
        return both;
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

        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(FORMAT);
        builder.setTexture(sprite);
        builder.setQuadOrientation(side);
        builder.setQuadTint(tint);
        builder.setApplyDiffuseLighting(true);

        float nx = side.getXOffset(), ny = side.getYOffset(), nz = side.getZOffset();
        for (float[] c : corners) {
            float u, v;
            if (side.getAxis() == EnumFacing.Axis.Y) {
                u = c[0] * 16F;
                v = c[2] * 16F;
            } else {
                u = (side.getAxis() == EnumFacing.Axis.X ? c[2] : c[0]) * 16F;
                // The earth under a sunken square shows its top rows, not a squashed whole: a side
                // half a block tall takes half a texture, taken from the top.
                //
                // Unless the picture slides with the surface, which is what the other edition's SideShift
                // does for a face with a cap along its top edge: the window starts at the texture's own top
                // row wherever the square has sunk to, so the cap rides the new surface rather than staying
                // up at the height the ground used to be.
                v = (slid ? height - c[1] : 1F - c[1]) * 16F;
            }
            put(builder, c[0], c[1], c[2], sprite.getInterpolatedU(u), sprite.getInterpolatedV(v), nx, ny, nz);
        }
        return builder.build();
    }

    private static void put(UnpackedBakedQuad.Builder builder, float x, float y, float z, float u, float v, float nx,
        float ny, float nz) {
        for (int e = 0; e < FORMAT.getElementCount(); e++) {
            VertexFormatElement element = FORMAT.getElement(e);
            switch (element.getUsage()) {
                case POSITION:
                    builder.put(e, x, y, z, 1F);
                    break;
                case COLOR:
                    builder.put(e, 1F, 1F, 1F, 1F);
                    break;
                case UV:
                    if (element.getIndex() == 0) builder.put(e, u, v, 0F, 1F);
                    else builder.put(e);
                    break;
                case NORMAL:
                    builder.put(e, nx, ny, nz, 0F);
                    break;
                default:
                    builder.put(e);
                    break;
            }
        }
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
