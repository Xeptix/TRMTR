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
    static final int INTS_PER_VERTEX = 7;

    /**
     * The tint index a square's one color goes through - the 1.7.10 edition's {@code GhostRendering.colorFor}: the
     * covered block's own color while the square wears as its own family, white past it or sunk as grass, the path
     * light multiplied in; see {@link BlockGhost#coveredTint}. Named for grass because until 0.9.223 only a lawn's top
     * and fringe took it, and with the biome's grass color; since then every face that edition multiplies its color
     * onto carries it - the top, the fringe, and the flanks of anything but a lawn (spec GS11, GS12, GT14, GT17).
     */
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

    /**
     * The tint index a worn stair's sides and undersides take the covered block's own color through, the path light
     * multiplied in - see {@link BlockGhost#coveredTint}. The 1.7.10 edition multiplies that color into every face of
     * its stair stand-in, which is never untinted; until 0.9.222 these took the path light alone (spec GF7).
     */
    public static final int COVERED_TINT = 2;

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
            held = extended.getValue(BlockGhost.GAME_TURN);
            GAME_TURN.get()[0] = held == null ? 0 : held.intValue();
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
        // The glow overlay alone, in a cutout pass the square itself does not draw in (0.9.228, real glow): see glow().
        if (drawing != null && drawing == glowLayer(origin)) {
            return glow(record, origin, outline, rotation, fringeTurn, snowed, side, stair);
        }
        if (drawing != null && drawing != BlockGhost.layerOf(origin)) return Collections.emptyList();

        // trmtr-api's render hook (W3 T8): a ghost over a block a plugin registered a renderer for is drawn as the
        // plugin asks, in place of everything below - its shape, its boxes, its one-box ground - and the model's own
        // drawing where the plugin declines, fails or has none. After the layer test, and the shader pack is told
        // what the square is made of before any of its quads, as on every other path. See ApiDraw.
        List<BakedQuad> hooked = hooked(state, record, origin, rotation, fringeTurn, snowed, outline, stair, side);
        if (hooked != null) return hooked;

        // A stair is a shape rather than a height, so nothing below this line applies to one: it is
        // drawn as the boxes its own shape is made of and handed back whole. See stairs().
        if (stair >= 0) return stairs(record, origin, rotation, fringeTurn, snowed, side, stair);

        // A data file's boxes (W3 spec 2.4, T6): drawn box by box, the walked boxes' tops lowered by the drawn depth,
        // with the ghost's own worn pictures on every face, after the render hook so a plugin that draws its block
        // still does. The 1.7.10 edition's GhostBoxes. See boxes().
        double[][] boxes = BlockGhost.boxesAt(record, origin, false);
        if (boxes != null) return boxes(record, origin, rotation, fringeTurn, snowed, side, boxes);

        SurfaceFamily appearance = ErosionState.familyOf(record);
        // The other edition's GhostLogic.bottomAt and heightAt, drawn: a slab from its own floor and never below
        // it - worn through, it would otherwise be a sheet of nothing hanging in its own space - and anything else
        // from the top of its cell, so a block already short loses its first pixel into its own shortfall. Until
        // 0.9.220 this sank everything from the block's own top; Xep chose the other edition's rule on 2026-10-08.
        float floor = (float) BlockGhost.bottomAt(origin);
        float height = (float) BlockGhost.heightAt(record, origin, outline, false);

        TextureAtlasSprite top = topOf(record, appearance, origin, rotation);
        int turn = top == null ? 0 : topTurn(record, appearance, origin, rotation);
        TextureAtlasSprite earth = earth();
        // Said before any of this square's vertices are written, and only ever heard while the seat is a ghost's:
        // the block underneath while the square still wears as that block's own family, and the block of the
        // family its wear has reached once it does not - the 1.7.10 edition's rule; see ShaderMaterial. Whether a
        // wear picture exists changes what is drawn, never the claim: until 0.9.223 a missing picture claimed
        // earth, so a shader pack lit a sand square with no picture as dirt (spec GF23).
        com.trmtgtnh.client.render.ShaderMaterial.claim(origin, appearance);
        // No wear picture: the family's own stock top, as the 1.7.10 edition's fallbackIcon draws it - grass's top
        // tinted as grass is - rather than earth for every family, which drew a sand or stone square with no
        // picture as dirt (0.9.222, spec GT11). Earth only where the family has no stock top of its own.
        if (top == null) top = stockTop(appearance, earth);
        // The top takes the square's one color, whatever the family: the covered block's own, which is the biome's
        // green for a lawn, a modded turf's own tint for a turf, and white for a block that colors nothing - so a red
        // block detected into the grass family is never green - and white once worn past its family or sunk as
        // grass, where a dirt rut washed green would be a very strange road (0.9.223, spec GT14, GT17).
        int tint = GRASS_TINT;
        // A lawn's flanks go down grass's path in the 1.7.10 edition - MixinGrassTint: untinted sides, the tinted
        // fringe drawn over them - and every other square's take its one color, as any block's do (spec GS11).
        int flankTint = GhostSides.tintsAsGrass(origin, appearance) ? LIGHT_TINT : GRASS_TINT;

        // The top is handed over with no side, so nothing culls it, because a sunk top is not a face of the
        // cell at all and the neighbour above says nothing about it. A whole window's top is a face of the
        // cell, and is handed over as one, so that a window above it of the same pane hides it as glass does -
        // the other edition's shouldSideBeRendered asks this of every side, the top included (2026-10-08).
        boolean paned = floor <= 0F && height >= 1F && GhostWindows.windowOf(origin);
        if (side == null) {
            return paned ? Collections.<BakedQuad>emptyList()
                : Collections.singletonList(quad(EnumFacing.UP, floor, height, top, tint, 0F, turn));
        }
        if (side == EnumFacing.UP) {
            return paned ? Collections.singletonList(quad(EnumFacing.UP, floor, height, top, tint, 0F, turn))
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
                .singletonList(quad(side, floor, height, flank, flankTint, shiftFor(face.slid, outline, height)));
        }
        // Two quads at one place, the fringe after the flank, which is how vanilla's own grass model draws
        // its overlay: one element for the side and a second, coincident, for the tinted overlay.
        List<BakedQuad> both = new java.util.ArrayList<BakedQuad>(2);
        float shift = shiftFor(face.slid, outline, height);
        both.add(quad(side, floor, height, flank, flankTint, shift));
        both.add(quad(side, floor, height, face.overlay, GRASS_TINT, shift));
        return both;
    }

    // ------------------------------------------------------------------
    // Real glow (0.9.228)
    // ------------------------------------------------------------------

    /**
     * The pass a glow overlay is drawn in: cutout, so the pixels that do not glow are discarded, and the atlas is drawn
     * there without its smaller copies, so a glow's few lit pixels are never averaged away into the clear ones around
     * them. A square that itself draws in the cutout pass has its overlay in the mipped cutout pass instead, so an
     * overlay is always alone in its pass and the model's own drawing in its own pass is left exactly as it was. The
     * ghost is offered to both already (BlockGhost.canRenderInLayer).
     */
    static net.minecraft.util.BlockRenderLayer glowLayer(int origin) {
        return BlockGhost.layerOf(origin) == net.minecraft.util.BlockRenderLayer.CUTOUT
            ? net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED
            : net.minecraft.util.BlockRenderLayer.CUTOUT;
    }

    /**
     * Whether the overlays are drawn. Always, but for the walk harness's glow part, which withholds them for one
     * photograph of each pair and builds the chunks again, so the pair differs in the glow alone; nothing in this mod
     * clears it.
     */
    public static volatile boolean glowShown = true;

    /**
     * The glow overlay of this square's wear picture, the pixels of it that shine in the dark, or null where the
     * picture has none (0.9.228, real glow). The end's purple cracks and the nether's ash are the looks that have them.
     * Asked as {@link #topOf} asks for the picture, so it goes over exactly the picture drawn.
     *
     * @param level receives the light level, out of fifteen, the overlay is baked with
     */
    private static TextureAtlasSprite glowTopOf(short record, SurfaceFamily appearance, int origin, int rotation,
        int[] level) {
        if (appearance == null || !WearTextures.glows(appearance) || !BlockGhost.shows(record)) return null;
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
        TextureAtlasSprite glow = WearTextures.glowIcon(block, meta, family, appearance, step, rotation);
        if (glow == null) return null;
        level[0] = WearTextures.glowLevelAt(block, meta, family, appearance, step, rotation);
        return level[0] > 0 ? glow : null;
    }

    /**
     * Whether any face of this square has a glow overlay to draw: its top, or a side or the underside drawn from the same
     * wear set.
     */
    public static boolean hasGlow(short record, int origin, int rotation, int fringeTurn, boolean snowed) {
        SurfaceFamily appearance = ErosionState.familyOf(record);
        if (appearance == null || !WearTextures.glows(appearance)) return false;
        if (glowTopOf(record, appearance, origin, rotation, new int[1]) != null) return true;
        for (int face = 0; face < 6; face++) {
            if (face == EnumFacing.UP.getIndex()) continue;
            if (GhostSides.of(record, origin, face, rotation, fringeTurn, snowed).glow != null) return true;
        }
        return false;
    }

    /** One glow overlay quad: the picture's own place, white, baked with the light the glow is given. */
    static BakedQuad glowQuad(EnumFacing side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, int level, float shiftRows) {
        return glowQuad(side, x0, y0, z0, x1, y1, z1, sprite, level, shiftRows, 0);
    }

    /** The same, turned as its picture is: a top {@code turn} quarters, held inside its edges as every quad here is. */
    static BakedQuad glowQuad(EnumFacing side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, int level, float shiftRows, int turn) {
        HookQuad quad = new HookQuad(vertices(side, x0, y0, z0, x1, y1, z1, sprite, shiftRows, turn), -1, side, sprite);
        quad.lit(com.trmtgtnh.client.texture.TailoredGround.glowLight(level));
        return quad;
    }

    /**
     * The glow overlays for one face of one worn square, drawn over what the model draws for the same arguments
     * (0.9.228, real glow; Xep, 2026-11-15: "I'd prefer a true glow"). Wherever the model lays a wear picture whose look
     * has glowing pixels - the end's cracks, the nether's ash - this lays the picture's overlay at the same place and
     * the same shift: the top, every side, the underside, every box of a stair and of a data file's shape. Each is the
     * picture's own pixels again, lit as brightly as the glow says, so by day it adds nothing and in the dark it
     * shines. Nothing is laid where the picture is not a wear one (a mended side, a grass wall, the block's own face).
     */
    static List<BakedQuad> glow(short record, int origin, int outline, int rotation, int fringeTurn, boolean snowed,
        @Nullable EnumFacing side, int stair) {
        if (!glowShown) return Collections.emptyList();
        SurfaceFamily appearance = ErosionState.familyOf(record);
        if (appearance == null || !WearTextures.glows(appearance)) return Collections.emptyList();
        int[] level = new int[1];
        java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes = stair >= 0 ? BlockGhost.stairBoxes(stair) : null;
        double[][] shaped = boxes != null && !boxes.isEmpty() ? null : BlockGhost.boxesAt(record, origin, false);
        if (boxes != null && !boxes.isEmpty() || shaped != null) {
            // Every face of every box, with no side, as stairs and data boxes are drawn.
            if (side != null) return Collections.emptyList();
            int count = boxes != null && !boxes.isEmpty() ? boxes.size() : shaped.length;
            TextureAtlasSprite top = glowTopOf(record, appearance, origin, rotation, level);
            int topLevel = level[0];
            // Turned as the picture under it is (0.9.229, the worn turn).
            int turn = top == null ? 0 : topTurn(record, appearance, origin, rotation);
            List<BakedQuad> out = new java.util.ArrayList<BakedQuad>();
            for (int at = 0; at < count; at++) {
                float x0, y0, z0, x1, y1, z1;
                if (shaped == null) {
                    net.minecraft.util.math.AxisAlignedBB box = boxes.get(at);
                    x0 = (float) box.minX;
                    y0 = (float) box.minY;
                    z0 = (float) box.minZ;
                    x1 = (float) box.maxX;
                    y1 = (float) box.maxY;
                    z1 = (float) box.maxZ;
                } else {
                    double[] box = shaped[at];
                    x0 = (float) box[0];
                    y0 = (float) box[1];
                    z0 = (float) box[2];
                    x1 = (float) box[3];
                    y1 = (float) box[4];
                    z1 = (float) box[5];
                }
                if (top != null) out.add(glowQuad(EnumFacing.UP, x0, y0, z0, x1, y1, z1, top, topLevel, 0F, turn));
                GhostSides.Face below = GhostSides.of(record, origin, EnumFacing.DOWN.getIndex(), rotation, fringeTurn, snowed);
                if (below.glow != null) {
                    out.add(glowQuad(EnumFacing.DOWN, x0, y0, z0, x1, y1, z1, below.glow, below.glowLevel, 0F));
                }
                for (EnumFacing compass : EnumFacing.HORIZONTALS) {
                    GhostSides.Face face = GhostSides.of(record, origin, compass.getIndex(), rotation, fringeTurn, snowed);
                    if (face.glow != null) {
                        out.add(glowQuad(compass, x0, y0, z0, x1, y1, z1, face.glow, face.glowLevel, 0F));
                    }
                }
            }
            return out;
        }
        float floor = (float) BlockGhost.bottomAt(origin);
        float height = (float) BlockGhost.heightAt(record, origin, outline, false);
        boolean paned = floor <= 0F && height >= 1F && GhostWindows.windowOf(origin);
        if (side == null || side == EnumFacing.UP) {
            // The top's own side, as the model files it: none, or up for a whole window.
            if (paned ? side != EnumFacing.UP : side != null) return Collections.emptyList();
            TextureAtlasSprite glow = glowTopOf(record, appearance, origin, rotation, level);
            if (glow == null) return Collections.emptyList();
            int turn = topTurn(record, appearance, origin, rotation);
            return Collections.singletonList(glowQuad(EnumFacing.UP, 0F, floor, 0F, 1F, height, 1F, glow, level[0], 0F, turn));
        }
        // A side or the underside, from the same wear set as the picture drawn there.
        GhostSides.Face face = GhostSides.of(record, origin, side.getIndex(), rotation, fringeTurn, snowed);
        if (face.glow == null) return Collections.emptyList();
        return Collections.singletonList(
            glowQuad(side, 0F, floor, 0F, 1F, height, 1F, face.glow, face.glowLevel, shiftFor(face.slid, outline, height)));
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
        int turn = top == null ? 0 : topTurn(record, appearance, origin, rotation);
        TextureAtlasSprite earth = earth();
        com.trmtgtnh.client.render.ShaderMaterial.claim(origin, appearance);
        // No wear picture: the family's own stock top, as the 1.7.10 edition's fallbackIcon draws it - grass's top
        // tinted as grass is - rather than earth for every family, which drew a sand or stone square with no
        // picture as dirt (0.9.222, spec GT11). Earth only where the family has no stock top of its own.
        if (top == null) top = stockTop(appearance, earth);
        // The covered block's own tint on every face, the top included: the 1.7.10 edition's stair stand-in is never
        // grass's and never untinted, and multiplies that one color into every face it has, the stock top it falls back
        // to included. Until 0.9.222 a stair drawn with vanilla's grass top took the biome's grass color here as a lawn
        // does, and every other face the path light alone (spec GF7).
        int tint = COVERED_TINT;

        java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes = BlockGhost.stairBoxes(stair);
        List<BakedQuad> out = new java.util.ArrayList<BakedQuad>(boxes.size() * 6);
        for (net.minecraft.util.math.AxisAlignedBB box : boxes) {
            float x0 = (float) box.minX;
            float y0 = (float) box.minY;
            float z0 = (float) box.minZ;
            float x1 = (float) box.maxX;
            float y1 = (float) box.maxY;
            float z1 = (float) box.maxZ;

            out.add(quad(EnumFacing.UP, x0, y0, z0, x1, y1, z1, top, tint, 0F, turn));

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
                    COVERED_TINT,
                    0F));

            // The covered block's own tint on every side, as on every face of the other edition's stair stand-in
            // (0.9.222, spec GF7) - never the path light alone.
            for (EnumFacing compass : EnumFacing.HORIZONTALS) {
                GhostSides.Face face = GhostSides.of(record, origin, compass.getIndex(), rotation, fringeTurn, snowed);
                TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
                out.add(quad(compass, x0, y0, z0, x1, y1, z1, flank, COVERED_TINT, 0F));
                if (face.overlay != null) {
                    out.add(quad(compass, x0, y0, z0, x1, y1, z1, face.overlay, GRASS_TINT, 0F));
                }
            }
        }
        return out;
    }

    /**
     * Worn ground over a block a data file gives boxes (W3 spec 2.4, T6), every face of every box, uncullable as a
     * stair's are and for the same reason: a face of a box is not a face of the cell. Each box takes the ghost's
     * own pictures - its wear picture on top, whatever {@code GhostSides} says for each side and the underside -
     * and its own tints, so a boxes block wears as the ground around it does. Faces where two boxes touch are
     * drawn inside the other box, where nothing can see them, as the 1.7.10 edition's {@code GhostBoxes} leaves
     * them.
     */
    private static List<BakedQuad> boxes(short record, int origin, int rotation, int fringeTurn, boolean snowed,
        @Nullable EnumFacing side, double[][] boxes) {
        if (side != null) return Collections.emptyList();
        SurfaceFamily appearance = ErosionState.familyOf(record);
        TextureAtlasSprite top = topOf(record, appearance, origin, rotation);
        int turn = top == null ? 0 : topTurn(record, appearance, origin, rotation);
        TextureAtlasSprite earth = earth();
        com.trmtgtnh.client.render.ShaderMaterial.claim(origin, appearance);
        if (top == null) top = stockTop(appearance, earth);
        int flankTint = GhostSides.tintsAsGrass(origin, appearance) ? LIGHT_TINT : GRASS_TINT;
        List<BakedQuad> out = new java.util.ArrayList<BakedQuad>(boxes.length * 6);
        for (double[] box : boxes) {
            float x0 = (float) box[0], y0 = (float) box[1], z0 = (float) box[2];
            float x1 = (float) box[3], y1 = (float) box[4], z1 = (float) box[5];
            out.add(quad(EnumFacing.UP, x0, y0, z0, x1, y1, z1, top, GRASS_TINT, 0F, turn));
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
                    flankTint,
                    0F));
            for (EnumFacing compass : EnumFacing.HORIZONTALS) {
                GhostSides.Face face = GhostSides.of(record, origin, compass.getIndex(), rotation, fringeTurn, snowed);
                TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
                out.add(quad(compass, x0, y0, z0, x1, y1, z1, flank, flankTint, 0F));
                if (face.overlay != null) out.add(quad(compass, x0, y0, z0, x1, y1, z1, face.overlay, GRASS_TINT, 0F));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // trmtr-api's render hook (W3 T8)
    // ------------------------------------------------------------------

    /**
     * One block's hooked drawing, kept while the renderer asks the model for it face by face: the renderer asks for
     * each of the six faces and then for the faces with no side, and the hook is asked once for all seven. Per thread,
     * as chunk builds are, and forgotten after the last ask, so the next build of the same square asks the hook again.
     */
    private static final ThreadLocal<Object[]> HOOKED = new ThreadLocal<Object[]>();

    /** The hook's answer kept for a square it declined, so the model draws it without asking seven times. */
    private static final Object DECLINED = new Object();

    /**
     * The quads a plugin's renderer gives this face of this square (W3 T8), or null where no renderer drew it and the
     * model draws it itself.
     */
    @Nullable
    private static List<BakedQuad> hooked(@Nullable IBlockState state, short record, int origin, int rotation,
        int fringeTurn, boolean snowed, int outline, int stair, @Nullable EnumFacing side) {
        if (!(state instanceof IExtendedBlockState) || origin < 0) return null;
        IExtendedBlockState extended = (IExtendedBlockState) state;
        Long at = extended.getValue(BlockGhost.POS);
        // No position: no renderer was registered when the square's facts were read, so none is asked.
        if (at == null) return null;
        Object[] kept = HOOKED.get();
        Object result;
        if (kept != null && ((Long) kept[0]).longValue() == at.longValue()
            && ((Integer) kept[1]).intValue() == record
            && ((Integer) kept[2]).intValue() == origin) {
            result = kept[3];
        } else {
            result = drawHooked(extended, at.longValue(), record, origin, rotation, fringeTurn, snowed, outline, stair);
            HOOKED.set(new Object[] { at, Integer.valueOf(record), Integer.valueOf(origin), result });
        }
        // The faces with no side are the renderer's last ask of a block, so the drawing is forgotten after them.
        if (side == null) HOOKED.remove();
        if (!(result instanceof com.trmtgtnh.apiload.ApiDraw.Drawn)) return null;
        com.trmtgtnh.apiload.ApiDraw.Drawn drawn = (com.trmtgtnh.apiload.ApiDraw.Drawn) result;
        // Told before any of the square's quads, as on every other path; the plugin's own pictures are its own.
        SurfaceFamily appearance = ErosionState.familyOf(record);
        com.trmtgtnh.client.render.ShaderMaterial.claim(origin, appearance);
        return side == null ? drawn.general : drawn.byFace.get(side.getIndex());
    }

    /** Asks the renderer registered for the covered block, once for the square; {@link #DECLINED} where none drew. */
    private static Object drawHooked(IExtendedBlockState extended, long at, short record, int origin, int rotation,
        int fringeTurn, boolean snowed, int outline, int stair) {
        IBlockState covered;
        try {
            covered = net.minecraft.block.Block.getStateById(origin);
        } catch (RuntimeException awkwardBlock) {
            return DECLINED;
        }
        net.minecraft.block.Block block = covered.getBlock();
        int meta;
        try {
            meta = block.getMetaFromState(covered);
        } catch (RuntimeException awkwardBlock) {
            meta = 0;
        }
        Integer light = extended.getValue(BlockGhost.HOOK_LIGHT);
        Integer tint = extended.getValue(BlockGhost.HOOK_TINT);
        Integer open = extended.getValue(BlockGhost.HOOK_OPEN);
        net.minecraft.util.math.BlockPos pos = net.minecraft.util.math.BlockPos.fromLong(at);
        // The space the ghost is drawn in, which a hook's units are laid over: the whole cell for a stair or a file's
        // boxes, which are not one height, and otherwise the floor and drawn top the model's own ground has.
        float floor = 0F;
        float height = 1F;
        if (stair < 0 && BlockGhost.boxesAt(record, origin, false) == null) {
            floor = (float) BlockGhost.bottomAt(origin);
            height = (float) BlockGhost.heightAt(record, origin, outline, false);
        }
        Painter painter = new Painter(
            record,
            origin,
            rotation,
            fringeTurn,
            snowed,
            covered,
            floor,
            height,
            light == null ? 0 : light.intValue(),
            open != null && open.intValue() != 0);
        com.trmtgtnh.apiload.ApiDraw.Drawn drawn = com.trmtgtnh.apiload.ApiDraw.drawn(
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            block,
            meta,
            record,
            light == null ? 0 : light.intValue(),
            tint == null ? 0xFFFFFF : tint.intValue(),
            painter);
        return drawn == null ? DECLINED : drawn;
    }

    /**
     * The model's half of a hooked block (W3 T8): the ghost's own pictures and tints, the covered block's own from its
     * model, the pictures a cut opens at a box's stage, and the quads built as the model builds its own.
     */
    static final class Painter implements com.trmtgtnh.apiload.ApiDraw.Painter {

        /** The highest layer a record can carry: five bits, biased by one, as 1.7.10's GhostRendering.atStage holds. */
        private static final int MOST_LAYER = 30;

        private final short record;
        private final int origin;
        private final int rotation;
        private final int fringeTurn;
        private final boolean snowed;
        private final IBlockState covered;
        private final float floor;
        private final float height;
        private final SurfaceFamily appearance;
        private final int light;
        private final boolean open;

        Painter(short record, int origin, int rotation, int fringeTurn, boolean snowed, IBlockState covered,
            float floor, float height, int light, boolean open) {
            this.record = record;
            this.origin = origin;
            this.rotation = rotation;
            this.fringeTurn = fringeTurn;
            this.snowed = snowed;
            this.covered = covered;
            this.floor = floor;
            this.height = height;
            this.appearance = ErosionState.familyOf(record);
            this.light = light;
            this.open = open;
        }

        /**
         * The record as a cut at {@code stage} shows it (Codex review R21): its own depth, with the stage as its
         * layer, held to the layers a record can carry - the 1.7.10 edition's {@code GhostRendering.atStage}.
         */
        static short atStage(SurfaceFamily appearance, short record, int stage) {
            int layer = stage < 0 ? 0 : stage > MOST_LAYER ? MOST_LAYER : stage;
            return ErosionState.pack(appearance, layer, ErosionState.sinkOf(record));
        }

        private TextureAtlasSprite picture(short shown, int face) {
            if (face == EnumFacing.UP.getIndex()) {
                TextureAtlasSprite top = topOf(shown, appearance, origin, rotation);
                return top != null ? top : stockTop(appearance, earth());
            }
            GhostSides.Face side = GhostSides.of(shown, origin, face, rotation, fringeTurn, snowed);
            return side.sprite != null ? side.sprite : earth();
        }

        private TextureAtlasSprite fringe(short shown, int face) {
            if (face < 2) return null;
            return GhostSides.of(shown, origin, face, rotation, fringeTurn, snowed).overlay;
        }

        @Override
        public TextureAtlasSprite outer(int face) {
            return picture(record, face);
        }

        @Override
        public TextureAtlasSprite outerOverlay(int face) {
            return fringe(record, face);
        }

        /** A cut's wall or floor at a box's stage: the ghost's picture for that stage, at the ground's own depth. */
        @Override
        public TextureAtlasSprite atStage(int face, int stage) {
            return picture(atStage(appearance, record, stage), face);
        }

        @Override
        public TextureAtlasSprite overlayAtStage(int face, int stage) {
            return fringe(atStage(appearance, record, stage), face);
        }

        /** The covered block's own picture on a face: the first quad its own model gives there. */
        @Override
        public TextureAtlasSprite own(int face) {
            List<BakedQuad> quads = ownQuads(face);
            return quads.isEmpty() ? null : quads.get(0)
                .getSprite();
        }

        /**
         * The fringe the covered block's own model lays over a grass side: a tinted second quad over a face whose
         * picture is vanilla's grass side, as vanilla's grass model draws its overlay (Codex review R21).
         */
        @Override
        public TextureAtlasSprite ownOverlay(int face) {
            if (face < 2) return null;
            List<BakedQuad> quads = ownQuads(face);
            if (quads.size() < 2 || !"minecraft:blocks/grass_side".equals(
                quads.get(0)
                    .getSprite()
                    .getIconName()))
                return null;
            for (int i = 1; i < quads.size(); i++) {
                if (quads.get(i)
                    .hasTintIndex())
                    return quads.get(i)
                        .getSprite();
            }
            return null;
        }

        private List<BakedQuad> ownQuads(int face) {
            if (covered == null) return Collections.emptyList();
            IBakedModel model = Minecraft.getMinecraft()
                .getBlockRendererDispatcher()
                .getModelForState(covered);
            if (model == null) return Collections.emptyList();
            List<BakedQuad> quads = model.getQuads(covered, EnumFacing.byIndex(face), 0L);
            return quads == null ? Collections.<BakedQuad>emptyList() : quads;
        }

        /** The top takes the square's one color; every other face as the model's own flanks take theirs. */
        @Override
        public int tintIndex(int face) {
            if (face == EnumFacing.UP.getIndex()) return GRASS_TINT;
            return GhostSides.tintsAsGrass(origin, appearance) ? LIGHT_TINT : GRASS_TINT;
        }

        @Override
        public int overlayTintIndex() {
            return GRASS_TINT;
        }

        @Override
        public float floor() {
            return floor;
        }

        @Override
        public float height() {
            return height;
        }

        @Override
        public BakedQuad face(int face, float x0, float y0, float z0, float x1, float y1, float z1,
            TextureAtlasSprite sprite, int tintIndex) {
            EnumFacing side = EnumFacing.byIndex(face);
            return new HookQuad(vertices(side, x0, y0, z0, x1, y1, z1, sprite, 0F), tintIndex, side, sprite);
        }

        @Override
        public BakedQuad free(float[] corners, float[] uv, int face, TextureAtlasSprite sprite, int rgb) {
            return freeQuad(corners, uv, EnumFacing.byIndex(face), sprite, rgb);
        }

        @Override
        public boolean openAbove() {
            return open;
        }

        /** The light baked into the vertices: see {@link HookQuad}. */
        @Override
        public BakedQuad overhead(BakedQuad quad) {
            ((HookQuad) quad).lit(light);
            return quad;
        }
    }

    /**
     * A quad a plugin placed itself (W3 T8): its four corners in the cell's frame, in its order, uv 0 to 1 across the
     * sprite, and its color written into the vertices rather than through a tint slot, since a plugin's color is its
     * own. Packed as every quad here is, seven ints a vertex in the block format.
     */
    static BakedQuad freeQuad(float[] corners, float[] uv, EnumFacing side, TextureAtlasSprite sprite, int rgb) {
        int[] data = new int[4 * INTS_PER_VERTEX];
        // The block format's color is ABGR: red in the lowest byte, alpha whole.
        int abgr = 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
        int at = 0;
        for (int v = 0; v < 4; v++) {
            data[at] = Float.floatToRawIntBits(corners[v * 3]);
            data[at + 1] = Float.floatToRawIntBits(corners[v * 3 + 1]);
            data[at + 2] = Float.floatToRawIntBits(corners[v * 3 + 2]);
            data[at + 3] = abgr;
            data[at + 4] = Float.floatToRawIntBits(sprite.getInterpolatedU(uv[v * 2] * 16.0D));
            data[at + 5] = Float.floatToRawIntBits(sprite.getInterpolatedV(uv[v * 2 + 1] * 16.0D));
            data[at + 6] = 0;
            at += INTS_PER_VERTEX;
        }
        return new HookQuad(data, -1, side, sprite);
    }

    /**
     * The wear picture for this square's top, or null where there is none.
     *
     * <p>
     * The other edition's {@code GhostRendering.iconFor}, top face: the record placed in the counted space,
     * then this surface's own set by the block it covers, then its family's. What the square is standing over
     * is turned back from the state id the painter remembered; nothing remembered means the family's set.
     */
    /**
     * Notes this square's wear picture where its layer moves, so it moves while it is on the ground near the player
     * (0.9.221, SeenPictures). From the block's facts for the model, the last place with a position.
     */
    public static void noteDrawn(short record, int origin, int rotation, int x, int y, int z) {
        TextureAtlasSprite top = topOf(record, ErosionState.familyOf(record), origin, rotation);
        if (top instanceof com.trmtgtnh.client.texture.WearSprite
            && ((com.trmtgtnh.client.texture.WearSprite) top).moves()) {
            com.trmtgtnh.client.texture.InnerLayers.noteSeen(top, x, y, z);
        }
    }

    /**
     * The turn the game itself gives the unworn block at the square being drawn, on this thread (0.9.229; the rotation
     * spec's "When the turn starts"): read from the square's BlockGhost.GAME_TURN as its quads are asked, and read by
     * {@link #topTurn}, so the square's top and its glow overlay keep the game's turn before the first sink.
     */
    private static final ThreadLocal<int[]> GAME_TURN = new ThreadLocal<int[]>() {

        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /**
     * The quarter turns the game draws the unworn block's top at this position by: its model asked for its top face
     * as BlockModelRenderer asks it, with MathHelper.getPositionRandom of the position, so a weighted model picks the
     * variant the game draws there, and read for how its picture is laid. Nought where the block's model has no
     * turns, no top or nothing readable. vanilla_turns.py lists the blocks the game turns, from this edition's jars.
     */
    public static int gameTurn(net.minecraft.util.math.BlockPos pos, int origin) {
        if (origin < 0 || pos == null) return 0;
        try {
            IBlockState state = net.minecraft.block.Block.getStateById(origin);
            IBakedModel model = Minecraft.getMinecraft()
                .getBlockRendererDispatcher()
                .getModelForState(state);
            if (model == null) return 0;
            List<BakedQuad> quads = model
                .getQuads(state, EnumFacing.UP, net.minecraft.util.math.MathHelper.getPositionRandom(pos));
            if (quads != null && !quads.isEmpty()) return turnOf(quads.get(0));
        } catch (RuntimeException awkward) {
            return 0;
        }
        return 0;
    }

    /** The turn a top quad is laid at, from the picture corner its north-west vertex samples (WearTurns.turnOfCorner). */
    static int turnOf(BakedQuad quad) {
        TextureAtlasSprite sprite = quad.getSprite();
        int[] data = quad.getVertexData();
        int stride = data.length / 4;
        int corner = 0;
        float least = Float.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            float at = Float.intBitsToFloat(data[v * stride]) + Float.intBitsToFloat(data[v * stride + 2]);
            if (at < least) {
                least = at;
                corner = v;
            }
        }
        float width = sprite.getMaxU() - sprite.getMinU();
        float height = sprite.getMaxV() - sprite.getMinV();
        if (width == 0F || height == 0F) return 0;
        return com.trmtgtnh.client.texture.WearTurns.turnOfCorner(
            (Float.intBitsToFloat(data[corner * stride + 4]) - sprite.getMinU()) / width,
            (Float.intBitsToFloat(data[corner * stride + 5]) - sprite.getMinV()) / height);
    }

    /**
     * How many quarter turns clockwise, seen from above with north up, this square's worn top is drawn turned by
     * (0.9.229, docs/goals/0.9.229-rotation-spec.md): asked of WearTextures with exactly what {@link #topOf} asks it,
     * so the turn is the drawn set's, and none where the square draws no wear picture.
     */
    private static int topTurn(short record, SurfaceFamily appearance, int origin, int rotation) {
        if (appearance == null || !BlockGhost.shows(record)) return 0;
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
        return WearTextures.quarterTurns(block, meta, family, appearance, step, rotation, ErosionState.sinkOf(record),
            GAME_TURN.get()[0]);
    }

    /**
     * A top's texture coordinates, in sixteenths, at a point of the cell turned {@code turn} quarters clockwise seen
     * from above: the picture's top row laid down the east edge for one, upside down for two, down the west edge for
     * three. The 1.7.10 edition's renderFaceYPos lays it so for uvRotateTop 1, 3 and 2, which is what it draws these
     * with, so a position turns the same way in both.
     */
    public static float[] turnedTop(float x, float z, int turn) {
        switch (turn & 3) {
            case 1:
                return new float[] { z * 16F, (1F - x) * 16F };
            case 2:
                return new float[] { (1F - x) * 16F, (1F - z) * 16F };
            case 3:
                return new float[] { (1F - z) * 16F, x * 16F };
            default:
                return new float[] { x * 16F, z * 16F };
        }
    }

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

    /** A family's own stock top, from the block atlas, or earth for a family with none. See {@link #stockTopName}. */
    private static TextureAtlasSprite stockTop(SurfaceFamily family, TextureAtlasSprite earth) {
        String name = stockTopName(family);
        return name == null ? earth
            : Minecraft.getMinecraft()
                .getTextureMapBlocks()
                .getAtlasSprite("minecraft:" + name);
    }

    /** The 1.7.10 edition's GhostLogic.fallbackTextureName, at this version's names; null where it says dirt. */
    static String stockTopName(SurfaceFamily family) {
        if (family == null) return null;
        switch (family) {
            case GRASS:
                return "blocks/grass_top";
            case SAND:
                return "blocks/sand";
            case GRAVEL:
                return "blocks/gravel";
            case STONE:
                return "blocks/stone";
            case COBBLE:
                return "blocks/cobblestone";
            case NETHER:
                return "blocks/netherrack";
            case END:
                return "blocks/end_stone";
            case SNOW:
                return "blocks/snow";
            case ICE:
                return "blocks/ice";
            case FLESH:
                return "blocks/soul_sand";
            case TAINT:
                return "blocks/mycelium_top";
            case SCULK:
                return "blocks/obsidian";
            case CLOUD:
                return "blocks/wool_colored_white";
            default:
                return null;
        }
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

    /** The same, its top turned {@code turn} quarters (see {@link #turnedTop}). */
    private static BakedQuad quad(EnumFacing side, float floor, float height, TextureAtlasSprite sprite, int tint,
        float shiftRows, int turn) {
        return quad(side, 0F, floor, 0F, 1F, height, 1F, sprite, tint, shiftRows, turn);
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
        return quad(side, x0, y0, z0, x1, y1, z1, sprite, tint, shiftRows, 0);
    }

    /** The same, an upward face's picture turned {@code turn} quarters (see {@link #turnedTop}). */
    private static BakedQuad quad(EnumFacing side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, int tint, float shiftRows, int turn) {
        return new BakedQuad(
            vertices(side, x0, y0, z0, x1, y1, z1, sprite, shiftRows, turn),
            tint,
            side,
            sprite,
            true,
            DefaultVertexFormats.BLOCK);
    }

    /** {@link #quad}'s four vertices, seven ints each, for the quad classes that take them: this and {@link HookQuad}. */
    static int[] vertices(EnumFacing side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, float shiftRows) {
        return vertices(side, x0, y0, z0, x1, y1, z1, sprite, shiftRows, 0);
    }

    /** The same, an upward face's picture turned {@code turn} quarters (see {@link #turnedTop}). */
    static int[] vertices(EnumFacing side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, float shiftRows, int turn) {
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
                // A worn top turned with its position, in the cell's frame as u and v are (0.9.229).
                if (side == EnumFacing.UP && (turn & 3) != 0) {
                    float[] turned = turnedTop(c[0], c[2], turn);
                    u = turned[0];
                    v = turned[1];
                }
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
            // Held a hair inside the picture's edge (0.9.229, EdgeInset): exactly on it, a block edge falling on a row
            // of pixel centres drew that row from the picture beside this one in the atlas.
            u = com.trmtgtnh.client.texture.EdgeInset.clamp(u, sprite.getIconWidth());
            v = com.trmtgtnh.client.texture.EdgeInset.clamp(v, sprite.getIconHeight());
            data[at + 4] = Float.floatToRawIntBits(sprite.getInterpolatedU(u));
            data[at + 5] = Float.floatToRawIntBits(sprite.getInterpolatedV(v));
            // The lightmap, which the renderer fills in; a model that guessed here would be arguing
            // with the lighting it is about to be given. A hooked block's quad alone says otherwise: HookQuad.lit.
            data[at + 6] = 0;
            at += INTS_PER_VERTEX;
        }
        return data;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return true;
    }

    /**
     * No smooth lighting on a hooked square, as 1.7.10's (ApiDraw, "Light"). The renderer asks this of the state
     * {@code BlockGhost.getExtendedState} filled, before it asks for the square's quads, and BlockModelRenderer's
     * {@code renderModel} and OptiFine's alike choose their path by the answer; the drawing asked for here is the one
     * those quads are. Only in the pass the square draws in, as the model's own drawing is. This is the first ask of a
     * draw on every path, and Forge's pipeline asks for the faces with no side first, so a drawing kept from the last
     * draw of this square would serve those faces with the last draw's light: it is forgotten here.
     */
    @Override
    public boolean isAmbientOcclusion(@Nullable IBlockState state) {
        if (!(state instanceof IExtendedBlockState)) return true;
        IExtendedBlockState extended = (IExtendedBlockState) state;
        int origin = number(extended, BlockGhost.ORIGIN, -1);
        net.minecraft.util.BlockRenderLayer drawing = net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();
        if (drawing != null && drawing != BlockGhost.layerOf(origin)) return true;
        HOOKED.remove();
        GAME_TURN.get()[0] = number(extended, BlockGhost.GAME_TURN, 0);
        return hooked(
            state,
            (short) number(extended, BlockGhost.RECORD, ErosionState.NONE),
            origin,
            number(extended, BlockGhost.ROTATION, 0),
            number(extended, BlockGhost.FRINGE_TURN, 0),
            number(extended, BlockGhost.SNOWED, 0) != 0,
            number(extended, BlockGhost.OUTLINE, BlockGhost.WHOLE_CUBE),
            number(extended, BlockGhost.STAIR, -1),
            EnumFacing.UP) == null;
    }

    private static int number(IExtendedBlockState state, net.minecraftforge.common.property.IUnlistedProperty<Integer> what,
        int otherwise) {
        Integer held = state.getValue(what);
        return held == null ? otherwise : held.intValue();
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
        // Earth, unless a ghost square's dust is being spawned, which takes that square's family's picture -
        // the 1.7.10 edition's, side by side (0.9.223, spec GF19; see GhostDust).
        return GhostDust.sprite(earth());
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
