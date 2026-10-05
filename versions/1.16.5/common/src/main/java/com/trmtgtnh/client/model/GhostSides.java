package com.trmtgtnh.client.model;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import com.trmtgtnh.client.texture.ModelFaces;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.client.texture.VanillaNames;
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

        // One texture all round: the side wears from the same set as the top, at the side's own lesser wear.
        if (appearance != SurfaceFamily.GRASS && !hasOwnSides(block, meta)) {
            TextureAtlasSprite worn = WearTextures
                .icon(block, meta, originFamily, appearance, WearSteps.sideLayer(appearance, record), turn);
            if (worn != null) return new Face(worn, false, null);
        }

        // Worn through to what was underneath: a rut through turf shows the soil it sat on, not the turf's flank.
        boolean revealsEarth = originFamily == SurfaceFamily.GRASS && appearance != SurfaceFamily.GRASS;
        if (revealsEarth) {
            return new Face(faceSprite(block, meta, 0, originFamily, appearance), true, null);
        }

        if (appearance == SurfaceFamily.GRASS) {
            TextureAtlasSprite fringe = fringeFor(record, fringeTurn);
            if (snowed) {
                // Snow sits on it, so the flank is the snowed one the block itself would draw; the fringe has
                // nothing to do under snow.
                return new Face(snowedSide(block, meta, originFamily, appearance), false, null);
            }
            int sideStep = WearSteps.sideLayer(appearance, record);
            if (TrmtConfig.grassSideWear && sideStep >= WALL_START && mimicsVanillaGrassTop(block, meta)) {
                TextureAtlasSprite wall = WearTextures.grassEarthWall(block, meta);
                if (wall != null) return new Face(wall, false, fringe);
            }
            return new Face(faceSprite(block, meta, side, originFamily, appearance), false, fringe);
        }

        return new Face(faceSprite(block, meta, side, originFamily, appearance), true, null);
    }

    /**
     * The thinned fringe for this square's side wear, or the block's own full overlay where there is none.
     *
     * <p>
     * Grey either way: the quad it is drawn on carries the biome tint, and tinting it here would tint it twice.
     */
    private static TextureAtlasSprite fringeFor(short record, int fringeTurn) {
        if (!TrmtConfig.grassSideWear) return vanilla("blocks/grass_side_overlay");
        TextureAtlasSprite thinned = WearTextures
            .grassSideOverlay(WearSteps.sideLayer(SurfaceFamily.GRASS, record), fringeTurn);
        return thinned != null ? thinned : vanilla("blocks/grass_side_overlay");
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
        return "minecraft:blocks/grass_top".equals(top);
    }

    /** The flank a snowed block draws: vanilla's snowed grass side for a lawn, else the block's own. */
    private static TextureAtlasSprite snowedSide(Block block, int meta, SurfaceFamily originFamily,
        SurfaceFamily appearance) {
        if (mimicsVanillaGrassTop(block, meta)) return vanilla("blocks/grass_side_snowed");
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
