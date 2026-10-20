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
                : Collections.singletonList(quad(Direction.UP, floor, height, top, tint, 0F, layer));
        }
        if (side == Direction.UP) {
            return paned ? Collections.singletonList(quad(Direction.UP, floor, height, top, tint, 0F, layer))
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

            out.add(quad(Direction.UP, x0, y0, z0, x1, y1, z1, top, tint, 0F, layer));

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

    /** One face of the box from the floor of the cell to {@code height}, across the whole footprint. */
    private static BakedQuad quad(Direction side, float floor, float height, TextureAtlasSprite sprite, int tint,
        float shiftRows, ChunkSectionLayer layer) {
        return quad(side, 0F, floor, 0F, 1F, height, 1F, sprite, tint, shiftRows, layer);
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
            uvs[at] = UVPair.pack(sprite.getU(u / 16F), sprite.getV(v / 16F));
            at++;
        }
        return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uvs[0], uvs[1], uvs[2], uvs[3], side,
            material(sprite, tint, layer));
    }

    /**
     * A quad's material as 26.1 keeps it: its sprite, the chunk pass it draws in (the covered block's - see
     * GhostLayers), the pass an item would draw it in (the game's own choice for a block sprite of that pass, as
     * {@code BakedQuad.MaterialInfo.of} makes it), its tint slot, shaded - the {@code true} 1.21.1 handed every ghost
     * quad - and no light of its own, as 1.21.1's quads had none: a ghost's glow is its state's light level and its
     * tint.
     */
    private static BakedQuad.MaterialInfo material(TextureAtlasSprite sprite, int tint, ChunkSectionLayer layer) {
        ChunkSectionLayer pass = layer == null ? ChunkSectionLayer.CUTOUT : layer;
        return new BakedQuad.MaterialInfo(sprite, pass,
            pass.translucent() ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet(), tint, true, 0);
    }

    /**
     * Every quad of one worn square, each side and the unculled ones, as one part of a block's geometry - what a model
     * hands the renderer from 26.1, where 1.21.1's models were asked side by side. Built from {@link #build} for each
     * side and for no side, so the quads and every rule behind them are 1.21.1's; each loader's model hands it on.
     * Ambient occlusion is on, as 1.21.1's ghost models said, and the particle is whatever the model would give.
     */
    public static BlockStateModelPart part(short record, int origin, int outline, int rotation, int fringeTurn,
        boolean snowed, List<AABB> boxes, TextureAtlasSprite particle) {
        QuadCollection.Builder quads = new QuadCollection.Builder();
        for (BakedQuad quad : build(record, origin, outline, rotation, fringeTurn, snowed, null, boxes)) {
            quads.addUnculledFace(quad);
        }
        for (Direction side : Direction.values()) {
            for (BakedQuad quad : build(record, origin, outline, rotation, fringeTurn, snowed, side, boxes)) {
                quads.addCulledFace(side, quad);
            }
        }
        return new SimpleModelWrapper(quads.build(), true, new Material.Baked(particle, false));
    }
}
