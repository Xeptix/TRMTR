package com.trmtgtnh.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.client.texture.ModelFaces;
import com.trmtgtnh.client.texture.VanillaNames;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.surface.WearScale;

/**
 * What a ghost draws on the four sides of its square, and what is drawn over that.
 *
 * <p>
 * The other edition decides this in {@code GhostRendering.iconFor}, which is handed a position and a side and
 * answers with one texture; the renderer then draws grass's tinted fringe over it in a pass of its own, which a
 * mixin redirects to the thinned fringe. A 1.12.2 model is handed neither a position nor a second pass: it returns
 * quads. So the same decisions are made here, and what was a second render pass becomes a second quad - exactly as
 * vanilla's own grass model does it, two coincident elements, the overlay after the side.
 *
 * <p>
 * The decisions, in the order the other edition takes them:
 *
 * <ul>
 * <li><b>A surface that is one texture all round</b> - sand, stone, gravel, dirt - wears its sides from the same
 * sprite as its top, at the side's own lesser wear, so a rut cut into it shows wear on the wall of the cut as well
 * as on its floor.</li>
 * <li><b>Grass</b> is the exception, and is never worn down its sides: its sides are their own texture with a green
 * cap along the top, and wearing those from the top texture puts the path's surface down its flanks. It gets the
 * block's own side, or - once the fringe has receded far enough - a de-greened earth wall, with the receding fringe
 * drawn over either.</li>
 * <li><b>Worn through to earth</b> - a grass square whose appearance is no longer grass - shows the block's
 * underside rather than its side, because what a rut through turf exposes is the soil it sat on.</li>
 * <li><b>Anything else</b> shows the block's own face, slid down by however far the square has sunk, so that a cap
 * along the texture's top edge rides the new surface instead of staying up at the old one.</li>
 * </ul>
 */
public final class GhostSides {

    /**
     * How much side wear the de-greened wall waits for: three steps of the counted eighty.
     *
     * <p>
     * The other edition writes it as a fraction rather than a number for a reason worth keeping - the gate was once
     * "more than nothing", which meant one drawn gradation of sixteen, and widening the counted space fivefold
     * without this would have brought the wall out at a fifth of the wear it needs.
     */
    private static final int WALL_START = Math
        .max(1, (WearScale.COUNTED_STEPS + SurfaceFamily.MAX_STAGES) / (2 * SurfaceFamily.MAX_STAGES));

    /** What a side is drawn with: a picture, whether to slide it down, and what to draw over it. */
    public static final class Face {

        public final TextureAtlasSprite sprite;

        /** Whether the picture slides down with the sunken surface, so a cap on its top edge rides the new top. */
        public final boolean slid;

        /** The receding grass fringe, drawn over the side in a second quad and tinted, or null. */
        public final TextureAtlasSprite overlay;

        Face(TextureAtlasSprite sprite, boolean slid, TextureAtlasSprite overlay) {
            this.sprite = sprite;
            this.slid = slid;
            this.overlay = overlay;
        }
    }

    private GhostSides() {}

    /**
     * What to draw on one side of a worn square.
     *
     * @param record     the square's record, as the ghost holds it
     * @param origin     the block state id the painter remembered, or -1
     * @param side       the face, in the game's own numbering
     * @param turn       the wear pattern's rotation at this square
     * @param fringeTurn the fringe's own rotation, which the other edition takes without the depth in it
     * @param snowed     whether snow lies on this square, which changes what a grass block shows on its flanks
     */
    public static Face of(short record, int origin, int side, int turn, int fringeTurn, boolean snowed) {
        SurfaceFamily appearance = ErosionState.familyOf(record);
        BlockState under = stateOf(origin);
        Block block = under == null ? null : under.getBlock();
        int meta = block == null ? 0 : metaOf(block, under);
        SurfaceFamily originFamily = block == null ? null : SurfaceRegistry.familyOf(block);
        if (originFamily == null) originFamily = appearance;

        /*
         * Whether this square has dropped below the top of the block it stands in for, which is the
         * only thing that may slide a side picture.
         * It used to be handed out as a constant true, and that is an off-by-one row on every block
         * that is not a whole cube. Sliding anchors the texture's own top row to the top of the quad;
         * not sliding anchors its bottom row to the bottom of the cell, which is what vanilla does.
         * Vanilla's grass path is the case that showed it: the block is fifteen sixteenths tall, the
         * top row of grass_path_side is fully transparent, and vanilla's model skips that row with
         * `uv [0, 1, 16, 16]`. Sliding an unsunk path drew that transparent row along the top of
         * every side - a see-through band in the cut-out pass, and a black one in the solid pass.
         */
        boolean sunken = ErosionState.sinkOf(record) > 0 && !stair(under);
        // A worn stair is never sunk, whatever depth its record carries: it keeps the stair's own shape, and the
        // 1.7.10 edition's stair stand-in answers isSunken false and isUntinted false - so its flanks never slide, and
        // a deep-worn turf stair never shows the earth a sunk lawn does (0.9.222, spec GF7).
        // ...and only a flank may slide. The 1.7.10 edition says `side >= 2` in the same breath as
        // its sunken test, and it matters here in a way it never did there: the underside now comes
        // through this method too, and a bottom face anchored to the top of its own quad samples the
        // texture upside down.
        boolean slides = sunken && side >= 2;

        // One texture all round: the side wears from the same set as the top, at the side's own lesser wear.
        if (appearance != SurfaceFamily.GRASS && !hasOwnSides(block, meta)) {
            TextureAtlasSprite worn = WearTextures
                .icon(block, meta, originFamily, appearance, WearSteps.sideLayer(appearance, record), turn);
            if (worn != null) return new Face(worn, false, null);
        }

        // Worn through to what was underneath: a rut through turf shows the soil it sat on, not the turf's flank. And
        // grass sunk as grass counts as worn through (0.9.222, spec SC22): the 1.7.10 edition draws it with its
        // untinted sunken ghost, whose sides are earth - `|| ghost.isUntinted()` in its revealsEarth - so a sunken
        // lawn shows soil on its flanks, under snow or not, rather than turf below the turf beside it.
        boolean revealsEarth = originFamily == SurfaceFamily.GRASS
            && (appearance != SurfaceFamily.GRASS || sunken);
        if (revealsEarth) {
            // Never slid (0.9.222, spec GS4): earth is not the block's own side picture, and the 1.7.10 edition slides
            // only a face drawn with that (`face == side`) - so revealed earth stays nailed to the bottom of the cell.
            return new Face(faceSprite(block, meta, 0, originFamily, appearance), false, null);
        }

        if (appearance == SurfaceFamily.GRASS) {
            // Only where the block really is a lawn, and only on a flank.
            //
            // The fringe used to be handed to anything whose <em>appearance</em> was grass, which is
            // what a square is wearing as rather than what it is made of - so a modded block detected
            // into the grass family got vanilla's green fringe laid over its own sides, and the top
            // and bottom faces got one too. The 1.7.10 edition keys this off the block drawing
            // vanilla's own grass top, which is the same question {@link #mimicsVanillaGrassTop}
            // already answers for the earth wall just below.
            //
            // Never a stair, whatever its top is drawn with: the 1.7.10 edition's stair stand-in answers
            // mimicsVanillaGrassTop false, so a turf stair gets no fringe and no earth wall and takes the covered
            // block's own tint on its sides like any block (0.9.222, spec GF7).
            boolean lawn = mimicsVanillaGrassTop(block, meta) && !stair(under);
            boolean flank = side >= 2;
            // The thinned fringe and the earth wall are only for a square that has not sunk - the gate the
            // 1.7.10 edition states twice, and which neither port carried until 0.9.219: without it a sinking
            // lawn's flanks went on receding as it sank. Since 0.9.222 a sunken lawn shows earth (revealsEarth,
            // above) and never reaches here, as 1.7.10's untinted sunken ghost never reaches its fringe; the gate
            // stays for the day that changes.
            TextureAtlasSprite fringe = lawn && flank ? fringeFor(record, fringeTurn, !sunken) : null;
            if (snowed && flank) {
                // Snow sits on it, so the flank is the snowed one the block itself would draw; the fringe has
                // nothing to do under snow. A flank only (0.9.222, spec SC19): the underside comes through here too,
                // and 1.7.10 asks `side >= 2` in the same breath as the snow, so a snowed lawn's underside is its own
                // earth rather than the snowed side picture.
                return new Face(snowedSide(block, meta, originFamily, appearance), false, null);
            }
            int sideStep = WearSteps.sideLayer(appearance, record);
            if (TrmtConfig.grassSideWear && flank && !sunken && sideStep >= WALL_START && lawn) {
                TextureAtlasSprite wall = WearTextures.grassEarthWall(block, meta);
                if (wall != null) return new Face(wall, false, fringe);
            }
            // A covered block that cuts its own side away, mended with vanilla's grass underneath it.
            //
            // Such a block draws itself in more than one layer and fills the holes with what shows
            // through; a ghost draws in one, so borrowing that texture left a band you could see
            // straight through. The mend is composed at stitch time for exactly the blocks whose side
            // texture has holes in it - see WearTextures.wantsMendedSide - and there is none for
            // anything else, so this costs a null check per face and nothing else. When the atlas
            // could not fit one, the block's own side is drawn as before and the composer's report
            // says how many it could not install.
            if (flank) {
                TextureAtlasSprite mended = WearTextures.mendedSide(block, meta);
                if (mended != null) return new Face(mended, false, fringe);
            }
            return new Face(faceSprite(block, meta, side, originFamily, appearance), false, fringe);
        }

        // Slid only for a block with sides of its own (0.9.222, spec GS18): one of a single picture all round reaches
        // here only when its worn side picture could not be had, and the 1.7.10 edition draws its own face unslid.
        return new Face(faceSprite(block, meta, side, originFamily, appearance), slides && hasOwnSides(block, meta),
            null);
    }

    /**
     * The thinned fringe for this square's side wear, or the block's own full overlay where there is none.
     *
     * <p>
     * Grey either way: the quad it is drawn on carries the biome tint, and tinting it here would tint it twice.
     */
    private static TextureAtlasSprite fringeFor(short record, int fringeTurn, boolean thin) {
        if (!thin || !TrmtConfig.grassSideWear) return atlasSprite(sprite("grass_side_overlay"));
        TextureAtlasSprite thinned = WearTextures
            .grassSideOverlay(WearSteps.sideLayer(SurfaceFamily.GRASS, record), fringeTurn);
        return thinned != null ? thinned : atlasSprite(sprite("grass_side_overlay"));
    }

    /**
     * Whether this square's wear picture is a grey one waiting for the biome's grass color.
     *
     * <p>
     * Two things have to be true and only one of them used to be asked. The square has to be wearing
     * as grass - and the block it covers has to <em>be</em> a lawn, because the picture is composited
     * from that block's own pixels. Vanilla's grass top is stored grey and is green only because the
     * game multiplies a biome color into it; anything else is stored in its own colors and
     * multiplying grass green into those is just darkening them.
     *
     * <p>
     * Asking only the first question tinted the top of every block a pack had detected into the grass
     * family: a red block came out dark red, and blocks that are not green at all came out green.
     * This is the same test the fringe and the earth wall are gated by, which is the point - all
     * three are asking "is this vanilla's turf".
     */
    public static boolean tintsAsGrass(int origin, SurfaceFamily appearance) {
        if (appearance != SurfaceFamily.GRASS) return false;
        BlockState under = stateOf(origin);
        Block block = under == null ? null : under.getBlock();
        if (block == null) return false;
        return mimicsVanillaGrassTop(block, metaOf(block, under));
    }

    /**
     * Whether the covered block is a stair, whose stand-in in the 1.7.10 edition is never sunk and never grass's
     * (0.9.222, spec GF7). See BlockGhost.coveredStair.
     */
    public static boolean stair(BlockState under) {
        return under != null && com.trmtgtnh.surface.SurfaceShape.of(under) == com.trmtgtnh.surface.SurfaceShape.STAIR;
    }

    /** Whether a block's sides are their own texture rather than the one on its top. */
    private static boolean hasOwnSides(Block block, int meta) {
        if (block == null) return false;
        String top = ModelFaces.faceName(block, 1);
        String flank = ModelFaces.faceName(block, 2);
        return top != null && flank != null && !top.equals(flank);
    }

    /**
     * Whether this block's top is vanilla grass's own, which is what the other edition keys the earth wall and the
     * tinted fringe off: a block drawing that texture is a lawn whatever mod added it.
     */
    private static boolean mimicsVanillaGrassTop(Block block, int meta) {
        String top = block == null ? null : ModelFaces.faceName(block, 1);
        if (top == null) return false;
        // Compared as locations rather than as strings, because one texture has several spellings. A
        // model may declare "block/grass_block_top", "minecraft:block/grass_block_top" or the bare
        // name, and this used to compare against the literal "minecraft:blocks/grass_top" - which is
        // the 1.12.2 edition's spelling of a texture 1.13 renamed. It therefore matched nothing on
        // this version, so vanilla grass was never recognised as a lawn: no earth wall, no fringe,
        // and once the tint was gated on this too, grass wore grey.
        return sprite(top).equals(sprite("grass_top"));
    }

    /**
     * The flank a snowed block draws: the side its own snowy variant draws, as the 1.7.10 edition asks the block itself
     * (0.9.222, spec SC20) - so a modded turf with a snow picture of its own shows it; else vanilla's snowed grass side
     * for a lawn, else the block's own side.
     */
    private static TextureAtlasSprite snowedSide(Block block, int meta, SurfaceFamily originFamily,
        SurfaceFamily appearance) {
        String own = com.trmtgtnh.client.texture.ModelFaces.snowedSideName(block);
        TextureAtlasSprite drawn = own == null ? null : atlasSprite(sprite(own));
        if (drawn != null && !net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()
            .equals(drawn.getName())) return drawn;
        if (mimicsVanillaGrassTop(block, meta)) return atlasSprite(sprite("grass_side_snowed"));
        return faceSprite(block, meta, 2, originFamily, appearance);
    }

    /**
     * The block's own picture for one face, or its family's stock texture where its model would not say.
     *
     * <p>
     * From the survey the stitch made rather than from the block's baked model. The survey is a plain table of names
     * filled in on the render thread; asking a baked model here would be asking another mod's code a question on a
     * mesher thread, which is the trade the other edition refuses everywhere but one place.
     */
    private static TextureAtlasSprite faceSprite(Block block, int meta, int side, SurfaceFamily originFamily,
        SurfaceFamily appearance) {
        String named = ModelFaces.faceName(block, side);
        if (named == null) {
            named = com.trmtgtnh.client.texture.FaceRules
                .vanillaName(com.trmtgtnh.client.texture.FaceRules.madeOf(originFamily, appearance), side);
        }
        if (named == null) return null;
        return atlasSprite(sprite(named));
    }

    /**
     * A bare name as the location this version files a block texture under.
     *
     * <p>
     * Both older editions build a string and hand it to the atlas. Here a sprite is asked for by
     * {@code ResourceLocation}, and the folder moved from {@code blocks} to {@code block} at 1.13 -
     * the same move {@code VanillaNames} makes for the faces a wear texture is composed from, and
     * for the same reason.
     */
    private static ResourceLocation sprite(String name) {
        ResourceLocation named = new ResourceLocation(VanillaNames.current(name));
        if (named.getPath()
            .indexOf('/') >= 0) return named;
        return new ResourceLocation(named.getNamespace(), "block/" + named.getPath());
    }

    private static TextureAtlasSprite vanilla(String path) {
        return atlasSprite(new ResourceLocation("minecraft", path));
    }

    /**
     * One sprite out of the block atlas.
     *
     * <p>
     * Through the model manager, because this version has several atlases and
     * {@code getTextureMapBlocks} named the one. Null-guarded all the way down: this is called while
     * drawing, and a client that has not finished a resource reload has a model manager holding no
     * atlas at all.
     */
    private static TextureAtlasSprite atlasSprite(ResourceLocation name) {
        if (name == null) return null;
        Minecraft game = Minecraft.getInstance();
        if (game == null || game.getModelManager() == null) return null;
        TextureAtlas atlas = game.getModelManager()
            .getAtlas(TextureAtlas.LOCATION_BLOCKS);
        return atlas == null ? null : atlas.getSprite(name);
    }

    private static BlockState stateOf(int origin) {
        if (origin < 0) return null;
        try {
            return Block.stateById(origin);
        } catch (RuntimeException awkwardBlock) {
            return null;
        }
    }

    /**
     * Nought, always.
     *
     * <p>
     * Both older editions ask the block which of its metadata values this state is, because there a
     * block is several surfaces. 1.13 split those into separate blocks, so there is one. Kept as a
     * method, and still passed down, because the lookup below it is keyed on the pair and changing
     * that is the surface table's business rather than this file's.
     */
    private static int metaOf(Block block, BlockState state) {
        return 0;
    }
}
