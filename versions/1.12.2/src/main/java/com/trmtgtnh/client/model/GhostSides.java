package com.trmtgtnh.client.model;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.client.texture.ModelFaces;
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
@SideOnly(Side.CLIENT)
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
        IBlockState under = stateOf(origin);
        Block block = under == null ? null : under.getBlock();
        int meta = block == null ? 0 : metaOf(block, under);
        SurfaceFamily originFamily = block == null ? null : SurfaceRegistry.familyOf(block, meta);
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
        boolean sunken = ErosionState.sinkOf(record) > 0;
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

        // Worn through to what was underneath: a rut through turf shows the soil it sat on, not the turf's flank.
        boolean revealsEarth = originFamily == SurfaceFamily.GRASS && appearance != SurfaceFamily.GRASS;
        if (revealsEarth) {
            return new Face(faceSprite(block, meta, 0, originFamily, appearance), slides, null);
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
            boolean lawn = mimicsVanillaGrassTop(block, meta);
            boolean flank = side >= 2;
            // A square that has sunk keeps the look it had before this feature existed, which is
            // the gate the 1.7.10 edition states twice - once for the thinned fringe and once for
            // the earth wall below - and which neither port carried. Without it a sinking lawn's
            // flanks went on receding as it sank: here the wall darkened the top of every side, and
            // on 1.16.5 the fringe kept thinning and brightening against it. Both are this one
            // missing condition.
            TextureAtlasSprite fringe = lawn && flank ? fringeFor(record, fringeTurn, !sunken) : null;
            if (snowed) {
                // Snow sits on it, so the flank is the snowed one the block itself would draw; the fringe has
                // nothing to do under snow.
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

        return new Face(faceSprite(block, meta, side, originFamily, appearance), slides, null);
    }

    /**
     * The thinned fringe for this square's side wear, or the block's own full overlay where there is none.
     *
     * <p>
     * Grey either way: the quad it is drawn on carries the biome tint, and tinting it here would tint it twice.
     */
    private static TextureAtlasSprite fringeFor(short record, int fringeTurn, boolean thin) {
        if (!thin || !TrmtConfig.grassSideWear) return vanilla("blocks/grass_side_overlay");
        TextureAtlasSprite thinned = WearTextures
            .grassSideOverlay(WearSteps.sideLayer(SurfaceFamily.GRASS, record), fringeTurn);
        return thinned != null ? thinned : vanilla("blocks/grass_side_overlay");
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
        IBlockState under = stateOf(origin);
        Block block = under == null ? null : under.getBlock();
        if (block == null) return false;
        return mimicsVanillaGrassTop(block, metaOf(block, under));
    }

    /** Whether a block's sides are their own texture rather than the one on its top. */
    private static boolean hasOwnSides(Block block, int meta) {
        if (block == null) return false;
        String top = ModelFaces.faceName(block, meta, 1);
        String flank = ModelFaces.faceName(block, meta, 2);
        return top != null && flank != null && !top.equals(flank);
    }

    /**
     * Whether this block's top is vanilla grass's own, which is what the other edition keys the earth wall and the
     * tinted fringe off: a block drawing that texture is a lawn whatever mod added it.
     */
    private static boolean mimicsVanillaGrassTop(Block block, int meta) {
        String top = block == null ? null : ModelFaces.faceName(block, meta, 1);
        if (top == null) return false;
        // Compared canonically rather than as strings, because one texture has several spellings.
        // Vanilla's own model declares "blocks/grass_top" with no namespace, and this used to compare
        // against the literal "minecraft:blocks/grass_top" - so it matched nothing, vanilla grass was
        // never recognised as a lawn, and neither the earth wall nor the fringe ever fired for it.
        return canonical("grass_top").equals(canonical(top));
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
        String named = ModelFaces.faceName(block, meta, side);
        if (named == null) {
            named = com.trmtgtnh.client.texture.FaceRules
                .vanillaName(com.trmtgtnh.client.texture.FaceRules.madeOf(originFamily, appearance), side);
        }
        if (named == null) return null;
        return Minecraft.getMinecraft()
            .getTextureMapBlocks()
            .getAtlasSprite(sprite(named));
    }

    /** A bare name in the other edition's form given the folder 1.12.2 files a block texture under. */
    private static String sprite(String name) {
        return name.indexOf('/') >= 0 ? name : "minecraft:blocks/" + name;
    }

    /**
     * One texture name written one way, so two spellings of the same texture compare equal.
     *
     * <p>
     * A model may name a texture bare, with a folder, or with a namespace as well, and all three mean
     * the same file. Comparing the raw strings makes two of those three a miss.
     */
    private static String canonical(String name) {
        if (name == null) return null;
        int colon = name.indexOf(':');
        String domain = colon >= 0 ? name.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? name.substring(colon + 1) : name;
        if (path.indexOf('/') < 0) path = "blocks/" + path;
        return domain + ":" + path;
    }

    private static TextureAtlasSprite vanilla(String path) {
        return Minecraft.getMinecraft()
            .getTextureMapBlocks()
            .getAtlasSprite("minecraft:" + path);
    }

    private static IBlockState stateOf(int origin) {
        if (origin < 0) return null;
        try {
            return Block.getStateById(origin);
        } catch (RuntimeException awkwardBlock) {
            return null;
        }
    }

    private static int metaOf(Block block, IBlockState state) {
        try {
            return block.getMetaFromState(state);
        } catch (RuntimeException awkwardBlock) {
            return 0;
        }
    }
}
