package com.trmtgtnh.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.joml.Vector3f;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.client.texture.WearTurns;
import com.trmtgtnh.config.TrmtConfig;
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
 *
 * <p>
 * <strong>From 26.1 a quad is no longer packed ints.</strong> {@code BakedQuad} moved to
 * {@code client.resources.model.geometry} and became a record of four positions, four packed texture coordinates
 * ({@code UVPair.pack}), its direction and a material: the sprite, the chunk pass it draws in, the pass an item would
 * draw it in, its tint slot, whether it is shaded and what light it gives off. The color and light the old format
 * carried, white and nought, are the renderer's own now, and the normal is worked out from the direction - the same
 * numbers. The corners, the texture coordinates and every rule deciding them are 1.21.1's, unchanged; the pass is
 * {@link com.trmtgtnh.client.GhostLayers#of}, written into each quad, where 1.21.1 kept a square's quads to its one
 * pass by turning the others away. And a block's geometry is a list of parts from 26.1, so {@link #part} gathers a
 * square's quads, every side, into one part for the loaders' models.
 */
public final class GhostQuads {

    /**
     * The tint slot a square's one color goes through - the 1.7.10 edition's {@code GhostRendering.colorFor}: the covered
     * block's own color while the square wears as its own family, white past it or sunk as grass, the path light
     * multiplied in; see GhostTint.coveredTint. Named for grass because until 0.9.223 only a lawn's top and fringe took
     * it, and with the biome's grass color; since then every face that edition multiplies its color onto carries it -
     * the top, the fringe, and the flanks of anything but a lawn (spec GS11, GS12, GT14, GT17).
     */
    public static final int GRASS_TINT = 0;

    /** The tint slot for a lawn's flanks, which go grass's way: the path light alone, under a tinted fringe. */
    public static final int LIGHT_TINT = 1;

    /**
     * The tint slot a worn stair's faces take the covered block's own color through, the path light multiplied in - see
     * GhostTint. The 1.7.10 edition multiplies that color into every face of its stair stand-in, which is never untinted
     * and never grass's; until 0.9.222 these took the path light alone (spec GF7).
     */
    public static final int COVERED_TINT = 2;

    /**
     * Position, color, texture, light, normal: vanilla's block format, eight ints a vertex. A quad was built from these
     * until 26.1, which builds one from positions and packed texture coordinates instead (see the class comment); kept
     * for whatever still reads a vertex in that format.
     */
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
        // A data file's boxes (W3 spec 2.4, T6): drawn box by box, the walked boxes' tops lowered by the drawn depth,
        // with the ghost's own worn pictures on every face. After a stair's boxes (a data stair is drawn as one) and,
        // through the level-aware build below, after the render hook, so a plugin that draws its block still does.
        // The 1.7.10 edition's GhostBoxes, 1.12.2's GhostBakedModel.boxes.
        double[][] shaped = BlockGhost.boxesAt(record, origin, false);
        if (shaped != null) return dataBoxes(record, origin, rotation, fringeTurn, snowed, side, shaped);
        SurfaceFamily appearance = ErosionState.familyOf(record);
        // The pass the covered block draws in, carried on each quad from 26.1 - see GhostLayers.
        ChunkSectionLayer layer = com.trmtgtnh.client.GhostLayers.of(origin);
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

        // The top is handed over with no side, so nothing culls it, because a sunk top is not a face of the cell at
        // all and the neighbour above says nothing about it. A whole window's top is a face of the cell, and is
        // handed over as one, so that a window above it of the same pane hides it as glass does - the 1.7.10
        // edition's shouldSideBeRendered asks this of every side, the top included, and 1.12.2 hands it over the
        // same way. Until 0.9.222 this edition handed every top over with no side, so a stack of worn windows drew
        // the face each pair shares (spec GT31).
        boolean paned = floor <= 0F && height >= 1F && GhostWindows.windowOf(origin);
        if (side == null) {
            return paned ? Collections.<BakedQuad>emptyList()
                : Collections.singletonList(quad(Direction.UP, floor, height, top, tint, 0F, layer, turn));
        }
        if (side == Direction.UP) {
            return paned ? Collections.singletonList(quad(Direction.UP, floor, height, top, tint, 0F, layer, turn))
                : Collections.<BakedQuad>emptyList();
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
                .singletonList(quad(side, floor, height, flank, flankTint, shiftFor(face.slid, outline, height), layer));
        }
        // Two quads at one place, the fringe after the flank, which is how vanilla's own grass model
        // draws its overlay: one element for the side and a second, coincident, for the tinted one.
        List<BakedQuad> both = new ArrayList<BakedQuad>(2);
        float shift = shiftFor(face.slid, outline, height);
        both.add(quad(side, floor, height, flank, flankTint, shift, layer));
        both.add(quad(side, floor, height, face.overlay, GRASS_TINT, shift, layer));
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
        // From the block and the family its wear has reached, whether or not a wear picture exists (0.9.227, spec
        // GF23): build stopped claiming earth for a square with no picture in 0.9.223, and this, the Fabric module's
        // claim for Canvas and OptiFabric, went on doing it - a sand square with no picture lit as dirt there.
        return com.trmtgtnh.client.render.ShaderMaterial.claimFor(origin, ErosionState.familyOf(record));
    }

    /**
     * The turn the game itself gives the unworn block at the square being drawn, on this thread (0.9.229; the rotation
     * spec's "When the turn starts"): set by {@link #rotationAt} where the square's rotation is worked out, and read by
     * {@link #topTurn}, so the square's top and its glow overlay keep the game's turn before the first sink.
     */
    private static final ThreadLocal<int[]> GAME_TURN = new ThreadLocal<int[]>() {

        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /**
     * A square's rotation, salted by how far it has sunk, as every loader works it out; and, where client.wearTurnStart
     * matches the game and the square has not sunk, the game's own turn of the unworn block there kept for its top.
     */
    public static int rotationAt(net.minecraft.client.renderer.block.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos, short record, int origin) {
        int sink = ErosionState.sinkOf(record);
        GAME_TURN.get()[0] = sink <= 0 && WearTurns.START_GAME.equals(WearTurns.start(TrmtConfig.wearTurnStart))
            ? gameTurn(pos, origin)
            : 0;
        return com.trmtgtnh.erosion.Rotations.forPosition(pos.getX(), pos.getZ(), sink);
    }

    /** The game's turn {@link #rotationAt} kept for the square being drawn on this thread. */
    public static int gameTurnHere() {
        return GAME_TURN.get()[0];
    }

    /** Kept again from a cache's key, before a square is built from it (GhostModelNeoForge). */
    public static void gameTurnHere(int turn) {
        GAME_TURN.get()[0] = turn;
    }

    /**
     * The quarter turns the game draws the unworn block's top at this position by: its model's top face asked as the
     * game's own renderer asks it, and read for how its picture is laid. Nought where the block's model has no turns,
     * no top or nothing readable. vanilla_turns.py lists the blocks the game turns, from each edition's own jars.
     */
    static int gameTurn(net.minecraft.core.BlockPos pos, int origin) {
        if (origin < 0 || pos == null) return 0;
        try {
            BlockState state = Block.stateById(origin);
            net.minecraft.client.renderer.block.dispatch.BlockStateModel model = Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .get(state);
            if (model == null) return 0;
            // As ModelBlockRenderer.tesselateBlock asks it: its random seeded with the state's seed at the position,
            // then the model's parts collected, the variant among them picked by that random.
            net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
            random.setSeed(state.getSeed(pos));
            List<BlockStateModelPart> parts = new ArrayList<BlockStateModelPart>();
            model.collectParts(random, parts);
            for (BlockStateModelPart part : parts) {
                for (BakedQuad quad : part.getQuads(Direction.UP)) return turnOf(quad);
            }
        } catch (RuntimeException awkward) {
            return 0;
        }
        return 0;
    }

    /** The turn a top quad is laid at, from the picture corner its north-west vertex samples (WearTurns.turnOfCorner). */
    static int turnOf(BakedQuad quad) {
        TextureAtlasSprite sprite = quad.materialInfo()
            .sprite();
        int corner = 0;
        float least = Float.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            float at = quad.position(v)
                .x()
                + quad.position(v)
                    .z();
            if (at < least) {
                least = at;
                corner = v;
            }
        }
        float width = sprite.getU1() - sprite.getU0();
        float height = sprite.getV1() - sprite.getV0();
        if (width == 0F || height == 0F) return 0;
        long uv = quad.packedUV(corner);
        return WearTurns.turnOfCorner((UVPair.unpackU(uv) - sprite.getU0()) / width,
            (UVPair.unpackV(uv) - sprite.getV0()) / height);
    }

    /**
     * How many quarter turns clockwise, seen from above with north up, this square's worn top is drawn turned by
     * (0.9.229, docs/goals/0.9.229-rotation-spec.md): asked of WearTextures with exactly what {@link #topOf} asks it,
     * so the turn is the drawn set's, and none where the square draws no wear picture.
     */
    private static int topTurn(short record, SurfaceFamily appearance, int origin, int rotation) {
        if (appearance == null || !BlockGhost.shows(record)) return 0;
        Block block = null;
        SurfaceFamily family = null;
        int meta = 0;
        if (origin >= 0) {
            try {
                BlockState under = Block.stateById(origin);
                block = under.getBlock();
                family = SurfaceRegistry.familyOf(under);
                meta = com.trmtgtnh.client.texture.StateSets.metaOf(under);
            } catch (RuntimeException awkwardBlock) {
                block = null;
            }
        }
        if (family == null) family = appearance;
        int step = WearSteps.topLayer(appearance, record);
        return WearTextures.quarterTurns(block, meta, family, appearance, step, rotation, ErosionState.sinkOf(record),
            gameTurnHere());
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
        Block block = null;
        SurfaceFamily family = null;
        int meta = 0;
        if (origin >= 0) {
            try {
                BlockState under = Block.stateById(origin);
                block = under.getBlock();
                // The state's own family and pictures, where a data file sets it apart from its block.
                family = SurfaceRegistry.familyOf(under);
                meta = com.trmtgtnh.client.texture.StateSets.metaOf(under);
            } catch (RuntimeException awkwardBlock) {
                block = null;
            }
        }
        if (family == null) family = appearance;
        int step = WearSteps.topLayer(appearance, record);
        // Nought for every state but one a data file sets apart. See GhostSides.metaOf.
        return WearTextures.icon(block, meta, family, appearance, step, rotation);
    }

    /**
     * The glow overlay of this square's wear picture, the pixels of it that shine in the dark, or null where the
     * picture has none (0.9.228, real glow). The end's purple cracks, the nether's ash and sculk's survivors are the
     * looks that have them. Asked as {@link #topOf} asks for the picture, so it goes over exactly the picture drawn.
     *
     * @param level receives the light level, out of fifteen, the overlay is baked with
     */
    private static TextureAtlasSprite glowTopOf(short record, SurfaceFamily appearance, int origin, int rotation,
        int[] level) {
        if (appearance == null || !WearTextures.glows(appearance) || !BlockGhost.shows(record)) return null;
        Block block = null;
        SurfaceFamily family = null;
        int meta = 0;
        if (origin >= 0) {
            try {
                BlockState under = Block.stateById(origin);
                block = under.getBlock();
                family = SurfaceRegistry.familyOf(under);
                meta = com.trmtgtnh.client.texture.StateSets.metaOf(under);
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
     * Whether any face of this square has a glow overlay to draw: its top, or a side or the underside drawn from the
     * same wear set. 1.21.1's Forge and NeoForge models asked it to know which passes a square draws in; from 26.1 a
     * quad carries its own pass, and this answers only whether there is anything to add.
     */
    public static boolean hasGlow(short record, int origin, int rotation, int fringeTurn, boolean snowed) {
        SurfaceFamily appearance = ErosionState.familyOf(record);
        if (appearance == null || !WearTextures.glows(appearance)) return false;
        if (glowTopOf(record, appearance, origin, rotation, new int[1]) != null) return true;
        for (int face = 0; face < 6; face++) {
            if (face == Direction.UP.get3DDataValue()) continue;
            if (GhostSides.of(record, origin, face, rotation, fringeTurn, snowed).glow != null) return true;
        }
        return false;
    }

    /**
     * One glow overlay quad: the picture's own place, untinted, in the cutout pass whatever pass the square draws in,
     * so the pixels that do not glow are discarded, and giving off the light the glow is given. From 26.1 that light
     * is the quad's own {@code lightEmission}, which every renderer reads - vanilla's and Forge's and NeoForge's
     * {@code VertexConsumer.putBlockBakedQuad} through {@code QuadInstance.getLightCoordsWithEmission}, Indigo's
     * {@code fromBakedQuad} into the quad's lightmap - and which takes the brighter of it and the world's light for
     * block and sky alike, as 1.21.1's baked light ({@code HookQuad.lit(TailoredGround.glowLight(level))}) did.
     */
    private static BakedQuad glowQuad(Direction side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, int level, float shiftRows, int turn) {
        return quad(side, x0, y0, z0, x1, y1, z1, sprite, -1, shiftRows, ChunkSectionLayer.CUTOUT, turn, level);
    }

    /**
     * The glow overlays for one face of one worn square, drawn over what {@link #build} draws for the same arguments
     * (0.9.228, real glow; Xep, 2026-11-15: "I'd prefer a true glow"). Wherever {@code build} lays a wear picture whose
     * look has glowing pixels - the end's cracks, the nether's ash, sculk's survivors - this lays the picture's overlay
     * at the same place and the same shift: the top, every side, the underside, every box of a stair and of a data
     * file's shape. Each is the picture's own pixels again, lit as brightly as the glow says, so by day it adds
     * nothing and in the dark it shines. Nothing is laid where the picture is not a wear one (a mended side, a grass
     * wall, the block's own face).
     */
    public static List<BakedQuad> glow(short record, int origin, int outline, int rotation, int fringeTurn,
        boolean snowed, Direction side, List<AABB> boxes) {
        SurfaceFamily appearance = ErosionState.familyOf(record);
        if (appearance == null || !WearTextures.glows(appearance)) return Collections.emptyList();
        // The top's overlay turns as the picture under it does (0.9.229, rotation); a side never turns.
        int turn = topTurn(record, appearance, origin, rotation);
        int[] level = new int[1];
        double[][] shaped = boxes != null && !boxes.isEmpty() ? null : BlockGhost.boxesAt(record, origin, false);
        if (boxes != null && !boxes.isEmpty() || shaped != null) {
            // Every face of every box, with no side, as stairs and data boxes are drawn.
            if (side != null) return Collections.emptyList();
            int count = boxes != null && !boxes.isEmpty() ? boxes.size() : shaped.length;
            TextureAtlasSprite top = glowTopOf(record, appearance, origin, rotation, level);
            int topLevel = level[0];
            List<BakedQuad> out = new ArrayList<BakedQuad>();
            for (int at = 0; at < count; at++) {
                float x0, y0, z0, x1, y1, z1;
                if (shaped == null) {
                    AABB box = boxes.get(at);
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
                if (top != null) out.add(glowQuad(Direction.UP, x0, y0, z0, x1, y1, z1, top, topLevel, 0F, turn));
                GhostSides.Face below = GhostSides
                    .of(record, origin, Direction.DOWN.get3DDataValue(), rotation, fringeTurn, snowed);
                if (below.glow != null) {
                    out.add(glowQuad(Direction.DOWN, x0, y0, z0, x1, y1, z1, below.glow, below.glowLevel, 0F, 0));
                }
                for (Direction compass : Direction.Plane.HORIZONTAL) {
                    GhostSides.Face face = GhostSides
                        .of(record, origin, compass.get3DDataValue(), rotation, fringeTurn, snowed);
                    if (face.glow != null) {
                        out.add(glowQuad(compass, x0, y0, z0, x1, y1, z1, face.glow, face.glowLevel, 0F, 0));
                    }
                }
            }
            return out;
        }
        float floor = (float) BlockGhost.bottomAt(origin);
        float height = (float) BlockGhost.heightAt(record, origin, outline, false);
        boolean paned = floor <= 0F && height >= 1F && GhostWindows.windowOf(origin);
        if (side == null || side == Direction.UP) {
            // The top's own side, as build files it: none, or up for a whole window.
            if (paned ? side != Direction.UP : side != null) return Collections.emptyList();
            TextureAtlasSprite glow = glowTopOf(record, appearance, origin, rotation, level);
            if (glow == null) return Collections.emptyList();
            return Collections.singletonList(glowQuad(Direction.UP, 0F, floor, 0F, 1F, height, 1F, glow, level[0], 0F, turn));
        }
        // A side or the underside, from the same wear set as the picture drawn there.
        GhostSides.Face face = GhostSides.of(record, origin, side.get3DDataValue(), rotation, fringeTurn, snowed);
        if (face.glow == null) return Collections.emptyList();
        return Collections.singletonList(
            glowQuad(side, 0F, floor, 0F, 1F, height, 1F, face.glow, face.glowLevel, shiftFor(face.slid, outline, height), 0));
    }

    /** Vanilla's dirt, which is what a square shows where it has nothing of its own left. */
    /** A family's own stock top, from the block atlas, or earth for a family with none. See {@link #stockTopName}. */
    private static TextureAtlasSprite stockTop(SurfaceFamily family, TextureAtlasSprite earth) {
        String name = stockTopName(family);
        if (name == null) return earth;
        Minecraft game = Minecraft.getInstance();
        if (game == null || game.getModelManager() == null) return earth;
        TextureAtlas atlas = blockAtlas();
        return atlas == null ? earth : atlas.getSprite(Identifier.fromNamespaceAndPath("minecraft", name));
    }

    /** The 1.7.10 edition's GhostLogic.fallbackTextureName, at this version's names; null where it says dirt. */
    static String stockTopName(SurfaceFamily family) {
        if (family == null) return null;
        switch (family) {
            case GRASS:
                return "block/grass_block_top";
            case SAND:
                return "block/sand";
            case GRAVEL:
                return "block/gravel";
            case STONE:
                return "block/stone";
            case COBBLE:
                return "block/cobblestone";
            case NETHER:
                return "block/netherrack";
            case END:
                return "block/end_stone";
            case SNOW:
                return "block/snow";
            case ICE:
                return "block/ice";
            case FLESH:
                return "block/soul_sand";
            case TAINT:
                return "block/mycelium_top";
            case SCULK:
                return "block/sculk";
            case CLOUD:
                return "block/white_wool";
            default:
                return null;
        }
    }

    private static TextureAtlasSprite earth() {
        TextureAtlas atlas = blockAtlas();
        return atlas == null ? null : atlas.getSprite(Identifier.fromNamespaceAndPath("minecraft", "block/dirt"));
    }

    /**
     * The block atlas, or null before the game has one. 1.21.1 asked the model manager for it
     * ({@code ModelManager.getAtlas(TextureAtlas.LOCATION_BLOCKS)}); by 26.1 the game's {@code AtlasManager} holds every
     * atlas, by the id of its definition ({@code AtlasIds.BLOCKS}). The same atlas, the same sprites.
     */
    public static TextureAtlas blockAtlas() {
        Minecraft game = Minecraft.getInstance();
        if (game == null || game.getAtlasManager() == null) return null;
        try {
            return game.getAtlasManager()
                .getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
        } catch (IllegalArgumentException notYet) {
            return null;
        }
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
        // The pass the covered stair draws in, carried on each quad from 26.1 - see GhostLayers.
        ChunkSectionLayer layer = com.trmtgtnh.client.GhostLayers.of(origin);

        List<BakedQuad> out = new ArrayList<BakedQuad>(boxes.size() * 6);
        for (AABB box : boxes) {
            float x0 = (float) box.minX;
            float y0 = (float) box.minY;
            float z0 = (float) box.minZ;
            float x1 = (float) box.maxX;
            float y1 = (float) box.maxY;
            float z1 = (float) box.maxZ;

            out.add(quad(Direction.UP, x0, y0, z0, x1, y1, z1, top, tint, 0F, layer, turn));

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
                    COVERED_TINT,
                    0F,
                    layer));

            // The covered block's own tint on every side, as on every face of the other edition's stair stand-in
            // (0.9.222, spec GF7) - never the path light alone.
            for (Direction compass : Direction.Plane.HORIZONTAL) {
                GhostSides.Face face = GhostSides
                    .of(record, origin, compass.get3DDataValue(), rotation, fringeTurn, snowed);
                TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
                out.add(quad(compass, x0, y0, z0, x1, y1, z1, flank, COVERED_TINT, 0F, layer));
                if (face.overlay != null) {
                    out.add(quad(compass, x0, y0, z0, x1, y1, z1, face.overlay, GRASS_TINT, 0F, layer));
                }
            }
        }
        return out;
    }

    /**
     * Worn ground over a block a data file gives boxes (W3 spec 2.4, T6), every face of every box, uncullable as a
     * stair's are and for the same reason: a face of a box is not a face of the cell. Each box takes the ghost's own
     * pictures - its wear picture on top, whatever {@code GhostSides} says for each side and the underside - and its
     * own tints, so a boxes block wears as the ground around it does. Faces where two boxes touch are drawn inside the
     * other box, where nothing can see them, as the 1.7.10 edition's {@code GhostBoxes} leaves them.
     */
    private static List<BakedQuad> dataBoxes(short record, int origin, int rotation, int fringeTurn, boolean snowed,
        Direction side, double[][] boxes) {
        if (side != null) return Collections.emptyList();
        SurfaceFamily appearance = ErosionState.familyOf(record);
        TextureAtlasSprite top = topOf(record, appearance, origin, rotation);
        int turn = top == null ? 0 : topTurn(record, appearance, origin, rotation);
        TextureAtlasSprite earth = earth();
        com.trmtgtnh.client.render.ShaderMaterial.claim(origin, appearance);
        if (top == null) top = stockTop(appearance, earth);
        int flankTint = GhostSides.tintsAsGrass(origin, appearance) ? LIGHT_TINT : GRASS_TINT;
        // The pass the covered block draws in, carried on each quad from 26.1 - see GhostLayers.
        ChunkSectionLayer layer = com.trmtgtnh.client.GhostLayers.of(origin);
        List<BakedQuad> out = new ArrayList<BakedQuad>(boxes.length * 6);
        for (double[] box : boxes) {
            float x0 = (float) box[0], y0 = (float) box[1], z0 = (float) box[2];
            float x1 = (float) box[3], y1 = (float) box[4], z1 = (float) box[5];
            out.add(quad(Direction.UP, x0, y0, z0, x1, y1, z1, top, GRASS_TINT, 0F, layer, turn));
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
                    flankTint,
                    0F,
                    layer));
            for (Direction compass : Direction.Plane.HORIZONTAL) {
                GhostSides.Face face = GhostSides
                    .of(record, origin, compass.get3DDataValue(), rotation, fringeTurn, snowed);
                TextureAtlasSprite flank = face.sprite == null ? earth : face.sprite;
                out.add(quad(compass, x0, y0, z0, x1, y1, z1, flank, flankTint, 0F, layer));
                if (face.overlay != null) {
                    out.add(quad(compass, x0, y0, z0, x1, y1, z1, face.overlay, GRASS_TINT, 0F, layer));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // trmtr-api's render hook (W3 T8)
    // ------------------------------------------------------------------

    /**
     * The quads for one face of one worn square, as each loader's model asks for them (W3 T8): the render hook first,
     * where a plugin registered a renderer for the block the square covers, and otherwise {@link #build} as it always
     * drew. The one point every renderer that draws worn ground reaches - Forge's model, which vanilla's renderer,
     * Rubidium and OptiFine ask, and Fabric's, which Indigo, the Sodium family and Canvas ask - so the hook is
     * reached by all of them. Asked after the pass test, as the model's own drawing is.
     */
    public static List<BakedQuad> build(net.minecraft.client.renderer.block.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos, short record, int origin, int outline, int rotation, int fringeTurn,
        boolean snowed, Direction side, List<AABB> stairs) {
        List<BakedQuad> hooked = hooked(level, pos, record, origin, outline, rotation, fringeTurn, snowed, stairs, side);
        if (hooked != null) return hooked;
        return build(record, origin, outline, rotation, fringeTurn, snowed, side, stairs);
    }

    /**
     * One block's hooked drawing, kept while the renderer asks the model for it face by face: the renderer asks for
     * each of the six faces and then for the faces with no side (vanilla's {@code ModelBlockRenderer}, and each
     * loader's model in that order), and the hook is asked once for all seven. Per thread, as chunk builds are, and
     * forgotten after the last ask, so the next build of the same square asks the hook again.
     */
    private static final ThreadLocal<Object[]> HOOKED = new ThreadLocal<Object[]>();

    /** The hook's answer kept for a square it declined, so the model draws it without asking seven times. */
    private static final Object DECLINED = new Object();

    /**
     * Whether a plugin's renderer draws this square (W3 T8), so the square is to be drawn with smooth lighting off, as
     * 1.7.10 draws a hooked block (ApiDraw, "Light"). Asked before the renderer asks for the square's quads; the drawing
     * it causes is the one those asks are answered with.
     */
    public static boolean drawnByHook(net.minecraft.client.renderer.block.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos) {
        if (level == null || pos == null || !com.trmtgtnh.apiload.ApiDraw.draws()) return false;
        short record = com.trmtgtnh.Client.ghostRecordAt(level, pos.getX(), pos.getY(), pos.getZ());
        int origin = com.trmtgtnh.Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        int rotation = rotationAt(level, pos, record, origin);
        int fringeTurn = com.trmtgtnh.erosion.Rotations.forPosition(pos.getX(), pos.getZ());
        return hooked(
            level,
            pos,
            record,
            origin,
            BlockGhost.outlineAt(level, pos, origin),
            rotation,
            fringeTurn,
            BlockGhost.snowedAt(level, pos),
            BlockGhost.stairBoxesAt(level, pos, origin),
            Direction.UP) != null;
    }

    /** Whether the game draws this square's face toward the cell above, so a quad filed under that face is drawn. */
    private static boolean openAbove(net.minecraft.client.renderer.block.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos) {
        try {
            return net.minecraft.world.level.block.Block.shouldRenderFace(level.getBlockState(pos),
                level.getBlockState(pos.above()), Direction.UP);
        } catch (RuntimeException awkward) {
            return false;
        }
    }

    /**
     * The quads a plugin's renderer gives this face of this square (W3 T8), or null where no renderer drew it and the
     * model draws it itself.
     */
    static List<BakedQuad> hooked(net.minecraft.client.renderer.block.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos,
        short record, int origin, int outline, int rotation, int fringeTurn, boolean snowed, List<AABB> stairs,
        Direction side) {
        if (level == null || pos == null || origin < 0 || !com.trmtgtnh.apiload.ApiDraw.draws()) return null;
        long at = pos.asLong();
        Object[] kept = HOOKED.get();
        Object result;
        if (kept != null && ((Long) kept[0]).longValue() == at
            && ((Integer) kept[1]).intValue() == record
            && ((Integer) kept[2]).intValue() == origin) {
            result = kept[3];
        } else {
            result = drawHooked(level, pos, record, origin, outline, rotation, fringeTurn, snowed, stairs);
            HOOKED.set(new Object[] { Long.valueOf(at), Integer.valueOf(record), Integer.valueOf(origin), result });
        }
        // The faces with no side are the renderer's last ask of a block, so the drawing is forgotten after them.
        if (side == null) HOOKED.remove();
        if (!(result instanceof com.trmtgtnh.apiload.ApiDraw.Drawn)) return null;
        com.trmtgtnh.apiload.ApiDraw.Drawn drawn = (com.trmtgtnh.apiload.ApiDraw.Drawn) result;
        // Told before any of the square's quads, as on every other path; the plugin's own pictures are its own.
        SurfaceFamily appearance = ErosionState.familyOf(record);
        com.trmtgtnh.client.render.ShaderMaterial.claim(origin, appearance);
        List<Object> chosen = side == null ? drawn.general : drawn.byFace.get(side.get3DDataValue());
        List<BakedQuad> out = new ArrayList<BakedQuad>(chosen.size());
        for (Object each : chosen) out.add((BakedQuad) each);
        return out;
    }

    /** Asks the renderer registered for the covered block, once for the square; {@link #DECLINED} where none drew. */
    private static Object drawHooked(net.minecraft.client.renderer.block.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos, short record, int origin, int outline, int rotation, int fringeTurn,
        boolean snowed, List<AABB> stairs) {
        BlockState covered;
        try {
            covered = Block.stateById(origin);
        } catch (RuntimeException awkwardBlock) {
            return DECLINED;
        }
        if (covered == null) return DECLINED;
        // What the hook reads and the model, handed a view and a position, can read for itself: the light over the
        // square (WearDraw.light, and what its cuts are lit by - see ApiDraw, "Light") and its one color.
        int light;
        try {
            light = net.minecraft.util.LightCoordsUtil.getLightCoords(level, pos.above());
        } catch (RuntimeException awkward) {
            light = 0;
        }
        int tint;
        try {
            // Slot nought of the ghost's own tint sources, 1.21.1's getColor(state, level, pos, 0): a block's color is a
            // list of sources from 26.1, and a block with none there answers white.
            net.minecraft.client.color.block.BlockTintSource source = Minecraft.getInstance()
                .getBlockColors()
                .getTintSource(level.getBlockState(pos), GRASS_TINT);
            int answered = source == null ? -1 : source.colorInWorld(level.getBlockState(pos), level, pos);
            tint = answered == -1 ? 0xFFFFFF : answered & 0xFFFFFF;
        } catch (RuntimeException awkward) {
            tint = 0xFFFFFF;
        }
        // The space the ghost is drawn in, which a hook's units are laid over: the whole cell for a stair or a file's
        // boxes, which are not one height, and otherwise the floor and drawn top the model's own ground has.
        float floor = 0F;
        float height = 1F;
        if ((stairs == null || stairs.isEmpty()) && BlockGhost.boxesAt(record, origin, false) == null) {
            floor = (float) BlockGhost.bottomAt(origin);
            height = (float) BlockGhost.heightAt(record, origin, outline, false);
        }
        Painter painter = new Painter(record, origin, rotation, fringeTurn, snowed, covered, floor, height,
            openAbove(level, pos));
        // A plugin's own colors, which from 26.1 go through tint slots of this square's: see HookTints.
        HookTints.begin(pos.asLong());
        com.trmtgtnh.apiload.ApiDraw.Drawn drawn = com.trmtgtnh.apiload.ApiDraw
            .drawn(pos.getX(), pos.getY(), pos.getZ(), covered, record, light, tint, painter);
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
        private final BlockState covered;
        private final float floor;
        private final float height;
        private final SurfaceFamily appearance;
        private final boolean open;

        Painter(short record, int origin, int rotation, int fringeTurn, boolean snowed, BlockState covered, float floor,
            float height, boolean open) {
            this.record = record;
            this.origin = origin;
            this.rotation = rotation;
            this.fringeTurn = fringeTurn;
            this.snowed = snowed;
            this.covered = covered;
            this.floor = floor;
            this.height = height;
            this.appearance = ErosionState.familyOf(record);
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
            if (face == Direction.UP.get3DDataValue()) {
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
        public Object outer(int face) {
            return picture(record, face);
        }

        @Override
        public Object outerOverlay(int face) {
            return fringe(record, face);
        }

        /** A cut's wall or floor at a box's stage: the ghost's picture for that stage, at the ground's own depth. */
        @Override
        public Object atStage(int face, int stage) {
            return picture(atStage(appearance, record, stage), face);
        }

        @Override
        public Object overlayAtStage(int face, int stage) {
            return fringe(atStage(appearance, record, stage), face);
        }

        /** The covered block's own picture on a face: the first quad its own model gives there. */
        @Override
        public Object own(int face) {
            List<BakedQuad> quads = ownQuads(face);
            return quads.isEmpty() ? null : spriteOf(quads.get(0));
        }

        /**
         * The fringe the covered block's own model lays over a grass side: a tinted second quad over a face whose
         * picture is vanilla's grass side, as vanilla's grass model draws its overlay (Codex review R21).
         */
        @Override
        public Object ownOverlay(int face) {
            if (face < 2) return null;
            List<BakedQuad> quads = ownQuads(face);
            if (quads.size() < 2) return null;
            TextureAtlasSprite first = spriteOf(quads.get(0));
            if (first == null || !"minecraft:block/grass_block_side".equals(
                first.contents()
                    .name()
                    .toString()))
                return null;
            for (int i = 1; i < quads.size(); i++) {
                if (quads.get(i)
                    .materialInfo()
                    .isTinted()) return spriteOf(quads.get(i));
            }
            return null;
        }

        private List<BakedQuad> ownQuads(int face) {
            if (covered == null) return Collections.emptyList();
            // From 26.1 a state's model hands out parts, each with its quads per side, and the set is the model
            // manager's, where 1.21.1's block renderer answered the model and the model its quads.
            net.minecraft.client.renderer.block.dispatch.BlockStateModel model = Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .get(covered);
            if (model == null) return Collections.emptyList();
            List<BlockStateModelPart> parts = new ArrayList<BlockStateModelPart>();
            model.collectParts(net.minecraft.util.RandomSource.create(42L), parts);
            List<BakedQuad> quads = new ArrayList<BakedQuad>();
            for (BlockStateModelPart part : parts) quads.addAll(part.getQuads(Direction.from3DDataValue(face)));
            return quads;
        }

        /** The top takes the square's one color; every other face as the model's own flanks take theirs. */
        @Override
        public int tintIndex(int face) {
            if (face == Direction.UP.get3DDataValue()) return GRASS_TINT;
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
        public Object face(int face, float x0, float y0, float z0, float x1, float y1, float z1, Object sprite,
            int tintIndex) {
            return quad(
                Direction.from3DDataValue(face),
                x0,
                y0,
                z0,
                x1,
                y1,
                z1,
                (TextureAtlasSprite) sprite,
                tintIndex,
                0F,
                com.trmtgtnh.client.GhostLayers.of(origin));
        }

        @Override
        public Object free(float[] corners, float[] uv, int face, Object sprite, int rgb) {
            return freeQuad(
                corners,
                uv,
                Direction.from3DDataValue(face),
                (TextureAtlasSprite) sprite,
                HookTints.slotFor(rgb),
                com.trmtgtnh.client.GhostLayers.of(origin));
        }

        @Override
        public boolean isQuad(Object each) {
            return each instanceof BakedQuad;
        }

        @Override
        public boolean openAbove() {
            return open;
        }

        /**
         * Nothing to do from 26.1: a vertex is its position and texture coordinates, and {@code MaterialInfo}'s one light
         * value is the block channel alone, so a quad cannot carry sky light. Filed under the top face and drawn flat
         * ({@link #part}), it takes the cell above's.
         */
        @Override
        public Object overhead(Object quad) {
            return quad;
        }
    }

    /** The sprite a baked quad draws; null where it cannot say. */
    static TextureAtlasSprite spriteOf(BakedQuad quad) {
        try {
            return quad.materialInfo()
                .sprite();
        } catch (RuntimeException awkward) {
            return null;
        }
    }

    /**
     * A quad a plugin placed itself (W3 T8): its four corners in the cell's frame, in its order, uv 0 to 1 across the
     * sprite - which is what the sprite itself takes from 1.21, as {@link #quad} says. Its color, which 1.21.1 wrote
     * into the vertices, is a tint slot from 26.1, where a vertex is its position and packed texture coordinates alone:
     * {@code tint} is the slot {@link HookTints} gave it, and {@link com.trmtgtnh.client.GhostTint} answers that slot.
     */
    static BakedQuad freeQuad(float[] corners, float[] uv, Direction side, TextureAtlasSprite sprite, int tint,
        ChunkSectionLayer layer) {
        Vector3f[] positions = new Vector3f[4];
        long[] uvs = new long[4];
        for (int v = 0; v < 4; v++) {
            positions[v] = new Vector3f(corners[v * 3], corners[v * 3 + 1], corners[v * 3 + 2]);
            uvs[v] = UVPair.pack(sprite.getU(uv[v * 2]), sprite.getV(uv[v * 2 + 1]));
        }
        return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uvs[0], uvs[1], uvs[2], uvs[3], side,
            material(sprite, tint, layer));
    }

    /** One face of the box from the floor of the cell to {@code height}, across the whole footprint. */
    private static BakedQuad quad(Direction side, float floor, float height, TextureAtlasSprite sprite, int tint,
        float shiftRows, ChunkSectionLayer layer) {
        return quad(side, 0F, floor, 0F, 1F, height, 1F, sprite, tint, shiftRows, layer);
    }

    /** The same, its top turned {@code turn} quarters (see {@link #turnedTop}). */
    private static BakedQuad quad(Direction side, float floor, float height, TextureAtlasSprite sprite, int tint,
        float shiftRows, ChunkSectionLayer layer, int turn) {
        return quad(side, 0F, floor, 0F, 1F, height, 1F, sprite, tint, shiftRows, layer, turn);
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
    static BakedQuad quad(Direction side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, int tint, float shiftRows, ChunkSectionLayer layer) {
        return quad(side, x0, y0, z0, x1, y1, z1, sprite, tint, shiftRows, layer, 0);
    }

    /** The same, an upward face's picture turned {@code turn} quarters (see {@link #turnedTop}). */
    static BakedQuad quad(Direction side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, int tint, float shiftRows, ChunkSectionLayer layer, int turn) {
        return quad(side, x0, y0, z0, x1, y1, z1, sprite, tint, shiftRows, layer, turn, 0);
    }

    /**
     * The same, giving off {@code emission} of fifteen as its own light: a glow overlay's (0.9.228, real glow), turned
     * as the picture under it is.
     */
    static BakedQuad quad(Direction side, float x0, float y0, float z0, float x1, float y1, float z1,
        TextureAtlasSprite sprite, int tint, float shiftRows, ChunkSectionLayer layer, int turn, int emission) {
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

        Vector3f[] positions = new Vector3f[4];
        long[] uvs = new long[4];
        int at = 0;
        for (float[] c : corners) {
            float u;
            float v;
            if (side.getAxis() == Direction.Axis.Y) {
                u = c[0] * 16F;
                v = c[2] * 16F;
                // A worn top turned with its position, in the cell's frame as u and v are (0.9.229).
                if (side == Direction.UP && (turn & 3) != 0) {
                    float[] turned = turnedTop(c[0], c[2], turn);
                    u = turned[0];
                    v = turned[1];
                }
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
            // From 1.21 the sprite takes a fraction of itself, 0 to 1, where every earlier edition handed it sixteenths
            // (read from TextureAtlasSprite.getU: u0 + (u1 - u0) * u). Handed sixteenths, every face spread across
            // sixteen sprites of the atlas - the first 1.21.1 game drew all worn ground as strips of other blocks.
            //
            // From 26.1 a vertex is its position and its packed texture coordinates (UVPair.pack, as the game's own face
            // baker writes them); the white color, the light of nought and the normal the old eight ints carried are
            // the renderer's own now.
            positions[at] = new Vector3f(c[0], c[1], c[2]);
            // Held a hair inside the picture's edge (0.9.229, EdgeInset): exactly on it, a block edge falling on a row
            // of pixel centres drew that row from the picture beside this one in the atlas.
            u = com.trmtgtnh.client.texture.EdgeInset.clamp(u, sprite.contents().width());
            v = com.trmtgtnh.client.texture.EdgeInset.clamp(v, sprite.contents().height());
            uvs[at] = UVPair.pack(sprite.getU(u / 16F), sprite.getV(v / 16F));
            at++;
        }
        return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uvs[0], uvs[1], uvs[2], uvs[3], side,
            material(sprite, tint, layer, emission));
    }

    /**
     * A quad's material as 26.1 keeps it: its sprite, the chunk pass it draws in (the covered block's - see
     * GhostLayers), the pass an item would draw it in (the game's own choice for a block sprite of that pass, as
     * {@code BakedQuad.MaterialInfo.of} makes it), its tint slot, shaded - the {@code true} 1.21.1 handed every ghost
     * quad - and no light of its own, as 1.21.1's quads had none: a ghost's glow is its state's light level and its
     * tint.
     *
     * <p>
     * From 26.3 the material also names the item passes' two glint render types, chosen as {@code MaterialInfo.of}
     * chooses them for a block sprite of that pass, and says its shade as a direction to shade by: none, which
     * shades a face by its own direction - what {@code shade = true} did (BlockModelLighter, 26.2 and 26.3).
     */
    private static BakedQuad.MaterialInfo material(TextureAtlasSprite sprite, int tint, ChunkSectionLayer layer) {
        return material(sprite, tint, layer, 0);
    }

    /** The same, giving off {@code emission} of fifteen: nought for every quad but a glow overlay (0.9.228). */
    private static BakedQuad.MaterialInfo material(TextureAtlasSprite sprite, int tint, ChunkSectionLayer layer,
        int emission) {
        ChunkSectionLayer pass = layer == null ? ChunkSectionLayer.CUTOUT : layer;
        boolean translucent = pass.translucent();
        return new BakedQuad.MaterialInfo(sprite, pass,
            translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet(),
            translucent ? Sheets.translucentBlockItemGlintSheet() : Sheets.cutoutBlockItemGlintSheet(),
            translucent ? Sheets.translucentBlockItemGlintSpecialSheet() : Sheets.cutoutBlockItemGlintSpecialSheet(), tint,
            null, emission);
    }

    /**
     * Every quad of one worn square, each side and the unculled ones, as one part of a block's geometry - what a model
     * hands the renderer from 26.1, where 1.21.1's models were asked side by side. Built from {@link #build} for each
     * side and for no side, so the quads and every rule behind them are 1.21.1's; each loader's model hands it on.
     * Ambient occlusion is on, as 1.21.1's ghost models said, and the particle is whatever the model would give. The
     * six faces first and the faces with no side last, the order the level-aware build keeps a plugin's one drawing
     * across (W3 T8).
     *
     * <p>
     * Each square's glow overlays (0.9.228, real glow) are in the part beside the pictures they go over, each filed
     * under the same face so it is culled with it, and shaded as those pictures are - the part's one smooth lighting
     * and every renderer's face shade - with only their light raised, to the brighter of the renderer's and the
     * light each gives off (VertexConsumer.putBlockBakedQuad, QuadInstance.getLightCoordsWithEmission).
     */
    public static BlockStateModelPart part(net.minecraft.client.renderer.block.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos, short record, int origin, int outline, int rotation, int fringeTurn,
        boolean snowed, List<AABB> boxes, TextureAtlasSprite particle) {
        // A hooked square draws flat, as 1.7.10's (ApiDraw, "Light"): every flat path lights a quad by the face it is filed
        // under, which is what a cut's walls are filed under the top for. The first part's word decides for a block.
        boolean smooth = !drawnByHook(level, pos);
        QuadCollection.Builder quads = new QuadCollection.Builder();
        for (Direction side : Direction.values()) {
            for (BakedQuad quad : build(level, pos, record, origin, outline, rotation, fringeTurn, snowed, side, boxes)) {
                quads.addCulledFace(side, quad);
            }
            for (BakedQuad quad : glow(record, origin, outline, rotation, fringeTurn, snowed, side, boxes)) {
                quads.addCulledFace(side, quad);
            }
        }
        for (BakedQuad quad : build(level, pos, record, origin, outline, rotation, fringeTurn, snowed, null, boxes)) {
            quads.addUnculledFace(quad);
        }
        for (BakedQuad quad : glow(record, origin, outline, rotation, fringeTurn, snowed, null, boxes)) {
            quads.addUnculledFace(quad);
        }
        return new SimpleModelWrapper(quads.build(), smooth, new Material.Baked(particle, false));
    }
}
