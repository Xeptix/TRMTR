package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * One generated wear texture: a particular surface, worn to a particular stage, rotated a
 * particular way.
 *
 * <p>
 * Nothing is shipped pre-composited. The pixels are built at stitch time from the block's own
 * textures and the decomposed wear pattern, which is what lets a Twilight Forest dirt or a
 * Biomes O' Plenty grass wear in its own colours, keeps the mod honest about not
 * redistributing Mojang's art, and means a resource pack retextures worn ground for free.
 */
public class WearSprite {

    /** The colour of the placeholder a sprite is stitched with until the pass installs its picture. */
    private static final int PLACEHOLDER_COLOUR = 0xFF7A6A55;

    /**
     * How many wear sprites have been sized for the stitcher since the pass last asked, and how long it took. Read
     * and written only under the class lock. Summed across threads, so a parallel loader makes the time a total, not
     * a wait.
     */
    private static int loads;

    private static long loadNanos;

    /**
     * Whether anything should draw this sprite. False until the sprite pass installs its picture, and false again
     * when its source cannot be read or composing or installing it fails: until then it carries a placeholder so the
     * atlas has something to stitch, and a lookup that drew it would put a flat colour on the ground.
     */
    private volatile boolean usable;

    /**
     * Whether this was built without the block's own pixels: a face read from its file rather than out of the atlas,
     * or a family stand-in. Said at the end of the sprite pass, by block, so a block that wears as a guess can be
     * found.
     */
    private boolean improvised;

    /**
     * The worn shell without its layer, handed from the worker to the render thread.
     *
     * <p>
     * Written in {@code compose}, which may be on a worker, and read in {@code install}, which is
     * not. The two are ordered by the future the pool is collected through, which is the same
     * ordering every other field composed out there already relies on.
     */
    private int[] pendingShell;

    /** The same, once it is the render thread's. Read by nothing but the animation below. */
    private int[] shell;

    private int[][] layerFrames;

    private AnimationMetadataSection layerAnimation;

    private int layerSize;

    /** Whether this sprite's holes are left see-through, carried so the animation matches. */
    private boolean seeThrough;

    /** Which frame is on the card, so a tick that changes nothing uploads nothing. */
    private int uploaded = -1;

    /** The block being worn, or null for the vanilla-derived fallback set. */
    private final Block origin;
    private final int originMeta;

    /**
     * What this sprite is called, which is where the atlas will look for it and what the pack answers to.
     *
     * <p>
     * Both older editions hand this to {@code TextureAtlasSprite}'s constructor and read it back with
     * {@code getIconName}, because there the sprite is the thing with a name. Here the name belongs to the file this
     * draws.
     */
    private final ResourceLocation name;

    /** Which family this surface belongs to, deciding where the worn-through earth comes from. */
    private final SurfaceFamily originFamily;

    /** The appearance being drawn, which is not always the origin's own family. */
    private final SurfaceFamily appearance;

    private final int stage;
    private final int stageCount;
    private final int rotation;

    /**
     * The edge this sprite is stitched and composed at, which is the edge its plan priced it at. Decided once, while
     * the stitch is planned, from the widths of the files its faces are drawn from, and carried here so that the price,
     * the size the stitcher reserves and the picture are one number rather than three worked out apart.
     */
    private final int edge;

    /**
     * True when this sprite is a mended side rather than a gradation of wear.
     *
     * <p>
     * Biomes O' Plenty's grasses are the case. Their own renderer draws vanilla grass first and
     * then their side texture over it, and that side texture has its top rows cut away so the
     * vanilla grass fringe shows through. A ghost draws in one pass, so borrowing the texture
     * left a band you could see straight through, and falling back to vanilla's side put vanilla
     * soil where the block's own loam belongs. Baking the pass underneath into the sprite is
     * what gives both: the block's own earth, under the fringe it was drawn to show.
     */
    private final boolean mendSide;

    /**
     * True when this sprite is a de-greened grass side wall rather than a gradation of wear.
     *
     * <p>
     * The block's own side with its green top edge and any cut-away holes filled down to bare
     * earth, so a worn grass block's flanks read as soil under a receding fringe rather than
     * keeping a static green strip the biome tint never touches. See
     * {@link WearCompositor#degreenTopEdge}.
     */
    private final boolean wallDegreen;

    public WearSprite(ResourceLocation name, Block origin, int originMeta, SurfaceFamily originFamily,
        SurfaceFamily appearance, int stage, int stageCount, int rotation, int edge) {
        this(name, origin, originMeta, originFamily, appearance, stage, stageCount, rotation, edge, false);
    }

    public WearSprite(ResourceLocation name, Block origin, int originMeta, SurfaceFamily originFamily,
        SurfaceFamily appearance, int stage, int stageCount, int rotation, int edge, boolean mendSide) {
        this(name, origin, originMeta, originFamily, appearance, stage, stageCount, rotation, edge, mendSide, false);
    }

    public WearSprite(ResourceLocation name, Block origin, int originMeta, SurfaceFamily originFamily,
        SurfaceFamily appearance, int stage, int stageCount, int rotation, int edge, boolean mendSide,
        boolean wallDegreen) {
        this.name = name;
        this.mendSide = mendSide;
        this.wallDegreen = wallDegreen;
        this.origin = origin;
        this.originMeta = originMeta;
        this.originFamily = originFamily;
        this.appearance = appearance;
        this.stage = stage;
        this.stageCount = stageCount;
        this.rotation = rotation;
        this.edge = edge;
    }

    /**
     * This sprite's picture, drawn now, for the pack to encode.
     *
     * <p>
     * Both older editions do this in two halves: a load that sizes the sprite for the stitcher and hands it a
     * placeholder, and a later pass that composes the real picture and installs it. The halves exist because a
     * sprite's size is fixed the moment its load returns, while the faces it is made from may not have loaded yet.
     * Neither problem survives here - the pack is asked for a finished file, and the faces come from their own files
     * rather than from the atlas - so it is one question again, asked once.
     *
     * <p>
     * The edge is still the one the plan priced, because that is what the rest of the pass is budgeted against.
     */
    public ResourceLocation name() {
        return name;
    }

    /** The edge the plan priced this at, which is what the picture is drawn to. */
    public int edge() {
        return edge;
    }

    /**
     * Which gradation this is, how many there are, and what is being drawn - for the brightness
     * tally in {@code WearTextures.Tally}, and for nothing else.
     *
     * <p>
     * Package-private and for that one purpose. The tally has to tell a first gradation from a last
     * one, because a first that is already dark means the pixels this was built from and only a last
     * being dark means the curve applied to them; and it has to keep grass apart from the rest,
     * because the grass cover-off path is the one the recorded brightness baseline could never
     * measure.
     */
    int stage() {
        return stage;
    }

    int stageCount() {
        return stageCount;
    }

    SurfaceFamily appearance() {
        return appearance;
    }

    public GeneratedPack.Sheet sheet(ResourceManager manager) {
        long began = System.nanoTime();
        int size = edge > 0 ? edge : FaceRules.PLACEHOLDER_EDGE;
        usable = false;
        if ((long) size * size > Integer.MAX_VALUE) {
            // More pixels than one array can hold, which only a file header claiming such a width could price. Both
            // older editions return out of a load the game calls outside its own catch, so a throw there would take
            // the atlas with it; here the pack has already claimed this name and has to answer, so it answers with
            // the placeholder at an edge that certainly fits. Never sized, so the lookup skips it either way.
            Trmt.LOG.warn(
                "Left wear texture {} out of the stitch: an edge of {} pixels is more than one picture can hold",
                name,
                Integer.valueOf(size));
            return blank(FaceRules.PLACEHOLDER_EDGE);
        }
        // Composed from the faces' own files. Both older editions compose from faces the atlas has already loaded,
        // declaring them as dependencies so the atlas loads them first; there is no atlas to read here, because a
        // pack is read while one is being built. See docs/WEAR-TEXTURES.md.
        int[] pixels = edge > 0 ? WearTextures.composeOne(this) : null;
        GeneratedPack.Sheet made = pixels == null ? null : install(pixels, WearTextures.sourceOf(this));
        countLoad(System.nanoTime() - began);
        // What cannot be made keeps the placeholder and stays unusable, as it did in both older editions, and every
        // lookup falls past it.
        return made == null ? blank(size) : made;
    }

    /**
     * True when the picture installed was made without the block's own pixels read out of the atlas: from a file, or a
     * family stand-in.
     */
    public boolean isImprovised() {
        return improvised;
    }

    /**
     * Everything one sprite needs that a worker thread may not go and fetch for itself.
     *
     * <p>
     * The resource manager reads from zip files and says nothing about being safe to share, a
     * block's own {@code getIcon} is a mod's code and may assume anything at all, and another
     * sprite's pixel array is a plain field the atlas swaps wholesale. So all of it is read on the
     * render thread, once per surface rather than once per sprite, and what crosses to a worker is
     * this: arrays nothing will write to again, and numbers.
     *
     * <p>
     * Immutable, and its arrays are shared by every gradation and rotation of one surface - six
     * hundred-odd of these serve nearly two hundred thousand sprites, which is what stops the
     * reading cost rising with the gradation count. That sharing is only sound because no operator
     * in {@link WearCompositor} writes to what it is handed. Checked over every array store in that
     * file, and worth checking again before adding one.
     *
     * <p>
     * The wear curve, the look and its strength are snapshotted here rather than read while
     * composing, and that is not tidiness. The config file is re-read on the SERVER thread when
     * somebody closes the config screen or a command reloads it, which can happen while a stitch is
     * running on the client thread; a batch whose sprites were composed against two different
     * curves would be a seam in the ground that nothing would ever explain.
     *
     * <p>
     * Every face and layer in one is already at the edge its sprites are stitched at, scaled as it was read, so a
     * worker never decides a size. The wear order is the one array that is not: it stays at the art's sixteen pixels
     * a side and is mapped to the edge as it is applied, which decides nothing, since sixteen is a constant. A mended
     * side's {@code NOTHING_UNDER} is not a picture at all, only a mark that there is nothing to lay under it.
     */
    public static final class Source {

        /**
         * The edge its sprites are stitched at. Every face and layer here is already at this edge, scaled as it was
         * read; {@link #grassOrder} stays at the art's sixteen and is mapped to the edge as it is applied, and
         * {@link #mendUnder} may be the empty {@code NOTHING_UNDER}.
         */
        final int edge;

        final int[] top;

        final int[] bottom;

        /**
         * Vanilla grass side, to be laid under a mended side. Null unless this is one, and the empty
         * {@code NOTHING_UNDER} when the pack has no grass side to lay.
         */
        final int[] mendUnder;

        /**
         * One wear order per rotation, primed on the render thread, or null when unused. Each is sixteen pixels a
         * side, the art's size, whatever the edge, and is sampled to the edge as it is applied.
         */
        final float[][] grassOrder;

        final String pattern;

        final float strength;

        final float curve;

        final boolean improvised;

        final boolean revealsEarth;

        /**
         * What the block draws behind its own face, to be baked under the worn one. Null unless a
         * config entry names one. Distinct from {@link #mendUnder} because that replaces the wear
         * pass outright, where this goes beneath its result.
         */
        final int[] innerLayer;

        /**
         * Every frame of the layer behind the shell, cut at the source's edge, or null when it does not move.
         *
         * <p>
         * Held once per surface rather than once per sprite, which is the whole of why this is affordable: one
         * appearance of one surface is a picture for every gradation and rotation, three hundred and twenty at the
         * settings shipped, and the lava under all of them is the same lava.
         */
        final int[][] layerFrames;

        /** The layer's own timing, read from the pack that supplied it. */
        final AnimationMetadataSection layerAnimation;

        /**
         * Whether the holes in this shell are left see-through rather than filled in.
         *
         * <p>
         * Decided in {@code harvest} from the pixels and the setting together, so that a worker is
         * handed an answer rather than a question - and so that turning the setting off gives back
         * exactly the picture that shipped before any of this existed.
         */
        final boolean seeThrough;

        /**
         * Whether the face this surface's appearance is drawn from was read at another size from that edge and scaled
         * to it, which the end of the sprite pass names.
         */
        final boolean scaled;

        Source(int edge, int[] top, int[] bottom, int[] mendUnder, float[][] grassOrder, String pattern, float strength,
            float curve, boolean improvised, boolean revealsEarth, int[] innerLayer, int[][] layerFrames,
            AnimationMetadataSection layerAnimation, boolean seeThrough, boolean scaled) {
            this.edge = edge;
            this.top = top;
            this.bottom = bottom;
            this.mendUnder = mendUnder;
            this.grassOrder = grassOrder;
            this.pattern = pattern;
            this.strength = strength;
            this.curve = curve;
            this.improvised = improvised;
            this.revealsEarth = revealsEarth;
            this.innerLayer = innerLayer;
            this.layerFrames = layerFrames;
            this.layerAnimation = layerAnimation;
            this.seeThrough = seeThrough;
            this.scaled = scaled;
        }

        boolean isUnreadable() {
            return top == null && bottom == null;
        }

        static Source unreadable() {
            return new Source(0, null, null, null, null, "", 1f, 1f, false, false, null, null, null, false, false);
        }
    }

    /**
     * Reads everything this sprite's surface is made of, at the edge its sprites are stitched at. Render thread
     * only, and that is not advice. Called only from the sprite pass, inside the ask it holds open round each
     * surface, and never at load.
     *
     * <p>
     * A face of another size is scaled to that edge here, once for the surface, rather than the finished picture
     * afterwards: the plan priced this edge, the stitcher has reserved it, and a picture composed at any other
     * size would either take room given to other faces or waste what it was given. Where the face this
     * appearance is drawn from was not that size, the source says so, and the end of the pass names the block.
     */
    Source harvest(FaceSource faces, ResourceManager manager) {
        improvised = false;
        int size = edge;
        // Only a sprite of a set the plan never priced carries no edge, and nothing can be drawn at no size.
        if (size <= 0) return Source.unreadable();

        if (wallDegreen || mendSide) {
            int[] side = read(faces, 2);
            if (side == null) return Source.unreadable();
            boolean scaled = edgeOf(side) != size;
            side = rescale(side, size);
            if (wallDegreen) {
                return new Source(
                    size,
                    side,
                    side,
                    null,
                    null,
                    "",
                    1f,
                    1f,
                    improvised,
                    false,
                    null,
                    null,
                    null,
                    false,
                    scaled);
            }
            // Read at the side's edge, so the pass underneath is laid at the size the side is drawn at. A pack with no
            // grass side has nothing to lay under it, which is what NOTHING_UNDER says; until 0.9.212 a missing file
            // threw here, and the pass filed the whole mended side as unreadable.
            BufferedImage grassSide = WearPatterns.readIcon(manager, "grass_side");
            int[] under = grassSide == null ? NOTHING_UNDER : WearPatterns.toPixels(grassSide, size);
            return new Source(
                size,
                side,
                side,
                under,
                null,
                "",
                1f,
                1f,
                improvised,
                false,
                null,
                null,
                null,
                false,
                scaled);
        }

        int[] top = read(faces, 1);
        int[] bottom = read(faces, 0);
        if (bottom == null) bottom = top;
        if (top == null) top = bottom;
        if (top == null) return Source.unreadable();

        // Asked of the look rather than of the family, which is what the setting has always said it
        // meant and never did. The default for the grass family is still this look, so nothing moves
        // unless somebody asks it to - and only while the sprite is still drawing its own surface,
        // because once it has worn through into a successor the cover is already gone.
        FamilySettings look = TrmtConfig.family(appearance);
        boolean cover = FaceRules.drawsCover(originFamily, appearance, FaceSource.coverLook(look));

        // Grass that has worn through shows the earth it was sitting on; everything else wears the
        // face you were already looking at.
        boolean revealsEarth = FaceRules.revealsEarth(originFamily, appearance);

        // The face this appearance is drawn from, at the size it was read, so a surface whose face was not the size
        // its plan priced can be named at the end of the pass.
        int drawnFrom = cover ? Math.max(edgeOf(top), edgeOf(bottom)) : revealsEarth ? edgeOf(bottom) : edgeOf(top);
        if (FaceRules.standsInForEarth(cover, revealsEarth, FaceRules.isColourless(bottom))) {
            // The block had no earth to show. A turf block whose every face is one greyscale texture
            // is not a green thing on a brown thing; it is a grey mask that only becomes a colour
            // once the biome tint runs through it, and revealing it once the tint has been dropped
            // turns the ground grey the moment it starts to sink. Drawn at this sprite's edge rather
            // than at the art's sixteen, so the size a sprite is stitched at depends on the widths its
            // plan read and never on whether its underside happens to be grey.
            BufferedImage standIn = WearPatterns.readIcon(manager, FaceRules.standInName(originFamily, appearance));
            if (standIn != null) {
                bottom = WearPatterns.toPixels(standIn, size);
                improvised = true;
                drawnFrom = size;
            }
        }
        boolean scaled = drawnFrom != size;
        top = rescale(top, size);
        bottom = rescale(bottom, size);

        // What the block draws behind its own face, where a config entry names one. Read here
        // rather than in compose for the same reason as everything else in this method: this is the
        // thread allowed to touch a resource manager, and the workers are handed arrays.
        int[] innerLayer = null;
        int[][] layerFrames = null;
        AnimationMetadataSection layerAnimation = null;
        boolean seeThrough = false;
        String innerName = InnerLayers.textureFor(origin, originMeta);
        if (innerName != null) {
            int[] face = revealsEarth ? bottom : top;
            BufferedImage layer = WearPatterns.readIcon(manager, innerName);
            if (layer == null) {
                InnerLayers.couldNotRead(innerName);
            } else {
                // Scaled to this sprite's edge rather than to its own size, and clamped to one frame on
                // the way, because WearPatterns already treats a tall image as a strip of frames and
                // takes the first - which is what makes naming an animated texture here work at all.
                innerLayer = WearPatterns.toPixels(layer, size);

                // Whether anything is gained by drawing this surface through at all. Worth seeing
                // through only where the shell has been cut clean away AND the layer itself is not
                // solid there - lava is solid everywhere it is drawn, so a lavastone never asks for
                // any of this and is composed exactly as it always was. Asked of the pixels rather
                // than of the config, which is what makes the answer honest for a block nobody
                // anticipated and for a pack that supplies its own water.
                boolean window = false;
                for (int i = 0; innerLayer != null && i < innerLayer.length && i < face.length; i++) {
                    if (((face[i] >>> 24) & 0xFF) == 0 && ((innerLayer[i] >>> 24) & 0xFF) < 255) {
                        window = true;
                        break;
                    }
                }
                // Noted whatever the setting says, because what a block has behind it is a fact, and
                // the painter has to know it the moment the setting is turned on. Against the block
                // itself rather than its id, which the next world or server may number differently.
                if (window && origin != null) {
                    InnerLayers.noteWindow(origin, originMeta);
                }
                seeThrough = window && TrmtConfig.seeThroughInnerLayers;

                // And every other frame besides, where the layer moves and there is room. Read here
                // because this is the thread allowed to open a resource, and once per surface rather
                // than once per sprite because every gradation of a surface has the same lava under
                // it. The budget is what stops a config naming fifty blocks costing a gigabyte.
                layerAnimation = InnerLayers.animationOf(manager, innerName);
                if (layerAnimation != null) {
                    int count = WearPatterns.frameCount(layer, layerAnimation.getFrameHeight(-1));
                    if (count > 1 && InnerLayers.mayMove(innerName, count, size)) {
                        layerFrames = new int[count][];
                        for (int frame = 0; frame < count; frame++) {
                            layerFrames[frame] = WearPatterns
                                .framePixels(layer, frame, layerAnimation.getFrameHeight(-1), size);
                        }
                    } else {
                        layerAnimation = null;
                    }
                }
            }
        }

        return new Source(
            size,
            top,
            bottom,
            null,
            cover ? WearPatterns.orders(rotationsInUse()) : null,
            look == null ? "" : look.wearPattern,
            look == null ? 1f : look.wearStrength,
            TrmtConfig.wearCurve <= 0f ? 1f : TrmtConfig.wearCurve,
            improvised,
            revealsEarth,
            innerLayer,
            layerFrames,
            layerAnimation,
            seeThrough,
            scaled);
    }

    /** One face of this sprite's block as the pass reads it, noting when it was not the block's own loaded pixels. */
    private int[] read(FaceSource faces, int side) {
        FaceSource.Face face = faces.read(origin, side, originFamily, appearance);
        if (!face.ownPixels) improvised = true;
        return face.pixels;
    }

    /** Stands for "there is nothing to lay under this", so that null can go on meaning "not a mend". */
    private static final int[] NOTHING_UNDER = new int[0];

    /**
     * The finished pixels, from nothing but arrays and numbers. Safe on any thread.
     *
     * <p>
     * Deliberately takes no {@link ResourceManager} and no {@link net.minecraft.world.level.block.Block}: what
     * a worker may not touch is then a matter for the compiler rather than for review.
     *
     * <p>
     * Draws at the source's edge and nowhere else, so the picture is the size the stitcher reserved.
     */
    int[] compose(Source source) {
        if (source == null || source.isUnreadable()) return null;
        int size = source.edge;
        if (size <= 0) return null;
        if (wallDegreen) return WearCompositor.degreenTopEdge(source.top, size);
        if (source.mendUnder != null) {
            // Cloned rather than handed back. One Source feeds every gradation and rotation of a
            // surface, and vanilla's mipmap generator keeps level zero by reference, so a sprite
            // whose pixels ARE its Source's array would share one upload with all of them.
            if (source.mendUnder.length == 0) return source.top.clone();
            return WearCompositor.over(source.mendUnder, source.top, size);
        }
        if (source.grassOrder != null) {
            float[] order = source.grassOrder[rotation % Math.max(1, source.grassOrder.length)];
            return grassPass(order, source.top, source.bottom, stage, stageCount);
        }

        int[] pixels = source.revealsEarth ? source.bottom : source.top;
        if (pixels == null) return null;
        float progress = stageCount <= 1 ? 1f : stage / (float) (stageCount - 1);
        int[] worn = wearPass(pixels, size, source.pattern, progress, source.strength, rotation, source.curve);
        // Only this branch, and only because it is the only one whose source is a single face of the
        // block being worn. The three above compose two faces into one or lay a mended side over
        // vanilla's, and what shows through those is earth or turf rather than the world.
        worn = WearCompositor.withSourceAlpha(worn, pixels);
        // And what shows through this one is the world, which is right for a leaf or a pane of ice
        // and wrong for a shell with something behind it. Where the block has named what is behind
        // it, that goes in here - after the wear pass, because it is the shell that wears and not
        // the lava under it, and under the shell's own transparency, so the gaps show what they
        // showed before the ground was ever walked on.
        if (source.innerLayer == null) return worn;
        // Kept, so the frame can be laid over the same shell again every time the layer moves,
        // rather than a finished picture stored per frame per gradation per rotation - which for
        // fifteen faces at eighty gradations is a hundred and sixty megabytes held for the session
        // against eleven for this. Uniquely owned: the wear pass allocated it, the alpha was written
        // into it in place, and the overlay below returns a new array rather than this one.
        if (source.layerFrames != null) pendingShell = worn;
        return source.seeThrough ? WearCompositor.overThroughHoles(source.innerLayer, worn, size)
            : WearCompositor.over(source.innerLayer, worn, size);
    }

    /** As {@link #rescale}, but never the argument itself, so a shared Source is never handed on. */
    private static int[] copyTo(int[] pixels, int size) {
        int[] scaled = rescale(pixels, size);
        return scaled == pixels ? pixels.clone() : scaled;
    }

    /**
     * Puts composed pixels in place. Render thread only.
     *
     * <p>
     * Separated from composing because it writes this sprite's own fields and regenerates its
     * mipmaps through a vanilla routine that is not safe to run twice at once: the blend for a frame
     * with a transparent pixel in it reads and writes one shared static array of four, so two
     * threads mipmapping a grass overlay or a fringe at the same moment would interleave into it and
     * produce wrong colours in the lower levels - silently, on somebody else's machine.
     *
     * <p>
     * The picture arrives at this sprite's own edge, because compose draws every picture at the edge its plan
     * priced; the scaling below is the identity for all of them and is kept only so a picture that did not
     * would still fit its slot, which the pass counts and warns about.
     */
    GeneratedPack.Sheet install(int[] pixels, Source source) {
        // Taken before anything can return, so that a sprite which fails below does not go on
        // holding a shell it will never draw.
        int[] worn = pendingShell;
        pendingShell = null;

        if (pixels == null) return null;
        int wanted = edge;
        if (wanted <= 0) return null;

        improvised = source != null && source.improvised;
        int[] still = rescale(pixels, wanted);
        // No mipmaps made here, and none to make: the atlas mipmaps the PNG this becomes. Both older editions have
        // to hand over a chain with a slot per level, and the 1.7.10 edition's fixed five was wrong often enough to
        // throw.
        usable = true;
        adoptLayer(source, worn, wanted, still);
        return strip(still, wanted);
    }

    /**
     * The still picture, or the still picture and every frame of its moving layer stacked under it.
     *
     * <p>
     * <strong>This is where the animation stopped being uploaded and started being a texture.</strong> Both older
     * editions keep one scratch picture and, each tick, compose the shell over the layer's current frame and upload
     * it into the atlas at this sprite's origin - and then put the minimum filter back by hand, because uploading a
     * single mip level switches mipmapping off across the whole block atlas. Here every frame is composed once,
     * stacked into one tall strip, and declared animated in the {@code .mcmeta} beside it. The atlas does the rest,
     * mipmaps included.
     *
     * <p>
     * What it costs is room: a sprite with thirty-two frames takes thirty-two times the space, where the older
     * editions held one frame and swapped it. {@code InnerLayers.mayMove} is the gate for that and already takes the
     * frame count and the edge - what it guards has changed from memory to atlas room, so its numbers want revisiting
     * with a real pack to measure against.
     */
    private GeneratedPack.Sheet strip(int[] still, int size) {
        if (layerFrames == null || layerAnimation == null || shell == null) {
            return new GeneratedPack.Sheet(still, size, size, null);
        }

        int count = layerFrames.length;
        int area = size * size;
        int[] stacked = new int[area * count];
        for (int frame = 0; frame < count; frame++) {
            int[] under = layerFrames[frame];
            if (under == null) {
                System.arraycopy(still, 0, stacked, frame * area, area);
                continue;
            }
            int[] composed = new int[area];
            composed = seeThrough ? WearCompositor.overThroughHolesInto(composed, under, shell, size)
                : WearCompositor.overInto(composed, under, shell, size);
            System.arraycopy(composed, 0, stacked, frame * area, area);
        }
        return new GeneratedPack.Sheet(stacked, size, size * count, animationMeta(count));
    }

    /**
     * The {@code .mcmeta} that tells the atlas this is animated, and how fast.
     *
     * <p>
     * Only the default frame time is written, not a frame list. The strip is built in the layer's own frame order,
     * so the frames are already where a plain animation expects them - a list would be the identity, written out.
     */
    private String animationMeta(int count) {
        int time = Math.max(1, layerAnimation.getDefaultFrameTime());
        return "{\"animation\":{\"frametime\":" + time + "}}";
    }

    /**
     * Takes the moving layer over from the source, on the thread that may speak to the atlas.
     *
     * <p>
     * Done here rather than while composing because setting the metadata is what puts this sprite on
     * the list the atlas ticks, and that list is filled while the atlas is being built - after this
     * runs and before anything is drawn. A sprite that declared itself later would never be ticked;
     * one that declared itself here and then failed to compose would be ticked with nothing to show,
     * which is why the shell is cleared on every path out.
     */
    private void adoptLayer(Source source, int[] worn, int wanted, int[] still) {
        if (source == null || source.layerFrames == null || worn == null) return;
        if (!TrmtConfig.animateInnerLayers || source.layerAnimation == null) return;

        int square = wanted * wanted;
        shell = worn.length == square ? worn : copyTo(worn, wanted);

        // The frames were cut at the edge this sprite was stitched at, and so was the shell, so both are shared
        // untouched. A picture at another size from its frames would need a scaled copy of its own, which the sprite
        // pass no longer makes, since every picture of one filing has one edge. The branch is kept so such a picture
        // would still draw, and it is counted as a copy, which the ledger prices and the end of the stitch would show.
        int[][] frames = source.layerFrames;
        boolean fits = true;
        for (int i = 0; i < frames.length && fits; i++) {
            fits = frames[i] != null && frames[i].length == square;
        }
        if (!fits) {
            int[][] scaled = new int[frames.length][];
            for (int i = 0; i < frames.length; i++) {
                if (frames[i] == null) return;
                scaled[i] = copyTo(frames[i], wanted);
            }
            frames = scaled;
        }

        layerFrames = frames;
        layerSize = wanted;
        layerAnimation = source.layerAnimation;
        seeThrough = source.seeThrough;
        // Neither edition's way of telling the atlas a sprite moves is needed here: the .mcmeta says so, and the
        // atlas reads it as it would for anybody's texture.
        // Measured from what this picture keeps rather than priced again, so the end of the stitch can hold what the
        // pictures hold against what their surfaces were granted. A chain of one, because that is now literally what
        // is kept - the mip levels belong to the atlas.
        InnerLayers.noteAnimated(shell, new int[][] { still }, frames, frames != source.layerFrames);
    }

    /**
     * Marks a sprite that cannot be drawn, so the lookup falls past it: one whose source could not be read, or
     * whose composing or installing failed.
     *
     * <p>
     * It keeps only the placeholder load gave it, which nothing draws, and what is drawn in its place depends on what
     * it was. A block's own wear falls to its family's fallback at the same gradation and rotation: coarser, the right
     * material, and honest about what could not be read. A block's grass wall falls to the fallback wall, and a
     * mended side to vanilla's own grass side. A family fallback and the fallback wall have nothing below them, so
     * where one of those cannot be drawn, no wear is drawn there at all.
     */
    void markUnusable() {
        usable = false;
    }

    /** The edge this sprite's plan priced it at, and so the edge it is stitched and composed at. */
    int plannedEdge() {
        return edge;
    }

    /** The block being worn, or null for the fallback set: what the caller files this sprite's sources under. */
    Block origin() {
        return origin;
    }

    /**
     * The metadata of the block being worn, which the count of pictures behind a moving layer files a surface under
     * beside {@link #origin()}.
     */
    int originMeta() {
        return originMeta;
    }

    /**
     * Which surface's sources this sprite is made from, among the sprites of its {@link #origin()}, so one
     * read serves all of its pictures.
     *
     * <p>
     * Every field {@link #harvest} consults and no more. Stage and rotation are deliberately absent:
     * putting them in would collapse the cache to one read per sprite and give back the only thing
     * that keeps the serial half of the pass flat as the gradation count rises.
     *
     * <p>
     * The block itself is not in the string. The caller files sources under the block object and uses
     * this only within it, because a number read while the ids are being moved on another thread could
     * give two blocks one key and build one of them from the other's pixels.
     *
     * <p>
     * The edge is in it because harvest cuts the layer's frames at that edge. Every sprite of one surface is
     * planned at the same edge, so it splits nothing, and it keeps a picture from ever being handed frames cut
     * for another size.
     */
    String sourceKey() {
        return (origin == null ? "-" : Integer.toString(originMeta)) + "/"
            + (originFamily == null ? "-" : originFamily.key())
            + "/"
            + (appearance == null ? "-" : appearance.key())
            + (mendSide ? "/m" : "")
            + (wallDegreen ? "/w" : "")
            + "@"
            + edge;
    }

    /** How many rotations this stitch is building, which the cover look needs one order for each of. */
    private static int rotationsInUse() {
        return Math.max(1, Math.min(4, TrmtConfig.wearRotations));
    }

    /**
     * One face, worn by the cover coming off it.
     *
     * <p>
     * The fourth operator, and the only one that needs two textures rather than one: it does not
     * change the face it is given, it takes it away in patches and lets the one underneath show
     * through. Which is what a turf block does, and why the look is named after grass - but it is
     * not grass that decides it. Anything with a different face underneath does the same thing:
     * podzol, mycelium, a mossy stone with clean stone beneath.
     *
     * <p>
     * Split out beside {@link #wearPass} for the same reason that one was: the picker in the wear
     * editor shows somebody what a look does, and a picture that can drift from the thing it
     * pictures is worse than no picture.
     *
     * <p>
     * Neither face is corrected on the way in. That has been tried twice and failed twice, and it
     * was always going to: a correction has to assume some particular green, the real one is a
     * property of the biome, and any gap between them shows as a colour cast - guess low and worn
     * earth is olive, guess high and it is pink. The tint is dealt with where the real one is
     * actually known, which is at render time; see GhostRendering.
     */
    public static int[] grassPass(ResourceManager manager, int[] cover, int[] under, int stage, int stageCount,
        int rotation) {
        return grassPass(WearPatterns.grassWearOrder(manager, rotation), cover, under, stage, stageCount);
    }

    /**
     * The same, given the order outright rather than a manager to fetch it with.
     *
     * <p>
     * Which is what lets this run on a worker: the order is read once per rotation on the render
     * thread and handed over, so nothing here touches the resource manager or the cache behind it.
     * Both faces are cloned on the way through rather than handed back, because one set of source
     * pixels now feeds every gradation and rotation of a surface.
     */
    static int[] grassPass(float[] order, int[] cover, int[] under, int stage, int stageCount) {
        if (cover == null || under == null || order == null) return cover == null ? null : cover.clone();
        int size = Math.max(edgeOf(under), edgeOf(cover));
        cover = copyTo(cover, size);
        under = copyTo(under, size);
        float cut = WearCompositor.coverageThreshold(order, WearPatterns.grassCoverage(stage, stageCount));
        return WearCompositor.applyCoverage(under, cover, size, order, WearPatterns.ART_SIZE, cut);
    }

    /**
     * How far along its parent's run a lighter look has got by the end of its own.
     *
     * <p>
     * This is the whole of what lighter means here, deliberately: each lite look is its parent's
     * branch with {@code eased} replaced by {@code eased * LIGHTER} and nothing else changed at all.
     * Because {@link #ease} is a power curve, a lite run finishing at 0.55 of the eased value
     * reproduces exactly the state its parent passes through about a quarter of the way along - so
     * nothing can come out of a lite look that could not come out of the look it is lighter than. On
     * operators that have to be safe on a block nobody drew art for, that is a better guarantee than
     * any amount of separate tuning could give.
     *
     * <p>
     * The number is fixed by the crack rather than chosen, though not quite in the way this used to
     * say. The crack's fine web stops GROWING at or below a wear of 0.45; it is not switched off,
     * but held at its floor half-width of 0.004 of a face against 0.018 at the end of a full run. So
     * any factor of 0.45 or less keeps the finer half of the network at its narrowest for the whole
     * of a run, and a cracked look whose web never opens is not quite a lighter crack. 0.55 is the
     * smallest round factor clear of that, and it clears it narrowly: at the end of a lighter
     * crack's own run the web is 0.00655, a sixth of the way from its floor to its ceiling. The rub
     * has no threshold of its own to respect, so it simply agrees.
     */
    private static final float LIGHTER = 0.55f;

    /**
     * How far the crack in the smoothed-and-cracked look is run against a plain crack's full run.
     *
     * <p>
     * Short of the end on purpose, for a reason in the crack's own schedule rather than in taste.
     * Its drop curve peaks around a wear of 0.70 and eases back after it, because a real fissure
     * silts up as the face around it goes - so a fissure at 0.80 is darker than the same fissure at
     * 1.00. That is exactly what this look needs: the buffing has already taken the surround's
     * relief away and the fissures have to carry the picture on their own.
     */
    private static final float SMOOTHED_CRACK_RUN = 0.80f;

    /**
     * How much of the face the track in the smoothed-and-rubbed look covers, against a plain rub's.
     *
     * <p>
     * The rub leaves everything it does not touch exactly as it found it, so here the untouched part
     * is the only evidence that any buffing happened at all. At the plain rub's own coverage barely a
     * quarter of the face would be left showing it and the whole thing would read as a slightly odd
     * rub; at this fraction it is nearer two fifths, wide enough to be its own thing rather than a
     * rim around the track. The track's depth is left alone, because the track is what the name
     * promises and it should be as deep here as on the look it borrows it from.
     */
    private static final float SMOOTHED_RUB_TRACK = 0.85f;

    /**
     * How much of the face the track in the cracked-and-rubbed look covers, against a plain rub's.
     *
     * <p>
     * Not the same fraction as {@link #SMOOTHED_RUB_TRACK} and deliberately not the same constant,
     * because the two answer different operators. There the untouched part is the only evidence that
     * any buffing happened. Here it is the only part of the face still carrying a crack at full
     * relief, because the rub flattens the fissures it does claim to about a quarter of theirs.
     *
     * <p>
     * Both halves of this look run at once, which is what makes it the only one of the eleven that had
     * to be pulled back on brightness rather than tuned for legibility. On the eight vanilla faces
     * at four rotations a fully worn face of this look sat 93.12 levels below the unworn ground it
     * stands beside, where the darkest of the other nine - the plain crack - sits at 69.74 and their
     * median at 43.64. A player picking down a list of ten pictures saw one of them go black. This
     * fraction and {@link #CRACKED_RUB_RUN} together answered that first, and neither does it alone:
     * 79.47 levels, which closed thirteen and a half of the twenty-three levels between this look
     * and the next darkest. It was still the darkest of the eleven by ten levels after that, so a
     * third fraction followed on {@link #CRACKED_RUB_SETTLE} and took it to 72.02, against the next
     * darkest at 69.74.
     *
     * <p>
     * Narrowing the track is the half of that with no legibility cost at all - on its own it moves
     * the smallest adjacent-gradation step by 0.004 of a level, which is inside the scatter of the
     * sweep that measured it - and it is bounded by the picture rather than by the measure. Below
     * about this fraction the track stops being a path: it breaks into islands, and 0.65 is the last
     * value at which the worn part is still one connected run across the face. At the last
     * gradation the track is 120 pixels of 256 where it was 157, and its largest connected piece
     * 64 per cent of itself where it was 93, which is the honest price of the change and is a
     * trodden path breaking up rather than a path that is no longer there.
     */
    private static final float CRACKED_RUB_TRACK = 0.65f;

    /**
     * How far the crack in the cracked-and-rubbed look is run against a plain crack's full run.
     *
     * <p>
     * The other half of the brightness answer above, and it is the half that costs something. This
     * look is the only one that runs two darkening operators over one face, so it is the only one
     * where both have to give a little; the track above gives what it can give for nothing, and the
     * remaining eight levels come from here.
     *
     * <p>
     * What it costs is the middle picture's crack network. Fissure against surround over the whole
     * face at the halfway mark falls from 9.60 levels to 6.28, where a plain crack at the same point
     * of its own run reads 10.64 - so at half wear this look now reads as a path with cracking
     * around it rather than as a cracked face with a path through it. Everything else measured moved
     * the right way at the time: fissures inside the track rose slightly, 4.77 levels to 4.89, the
     * network over a fully worn face from 10.14 to 12.13, and the track's separation from the ground
     * beside it from 30.70 to 34.24. {@link #CRACKED_RUB_SETTLE} has since moved the last two again,
     * to 14.08 and 18.33, and the first to 9.51 - so the figures here are what this fraction did on
     * its own and not what the shipped look measures.
     *
     * <p>
     * This is the paragraph that used to say the crack half was not scaled at all and that scaling
     * it measured worse, quoting fissures inside the track falling from 4.27 levels to 3.80. That
     * measurement does not reproduce - on today's operators they rise - and the eight levels of
     * brightness it dismissed as unasked for turned out to be exactly what was being asked for. It
     * is written down rather than quietly replaced because a number in a comment that nobody can
     * reproduce is worse than no number.
     *
     * <p>
     * The one thing genuinely given up is a literal identity: the part of the face the track never
     * claims used to be a plain crack pixel for pixel, and is now a crack at four fifths of its run
     * pixel for pixel. That is still a state the plain crack genuinely passes through, which is the
     * same ground {@link #LIGHTER} and {@link #SMOOTHED_CRACK_RUN} already stand on, so what a
     * player sees is a picture the parent operator can actually make.
     */
    private static final float CRACKED_RUB_RUN = 0.80f;

    /**
     * How fast the track in the cracked-and-rubbed look goes on deepening once it has bedded in.
     *
     * <p>
     * The third brightness answer, and the one that spends the half the two fractions above
     * deliberately left alone. Scaling the rub's depth outright was measured and declined, for one
     * reason that reproduces exactly: it halves the middle preview's track separation, 13.42 levels
     * to 7.08, where narrowing the track and shortening the crack leave that figure untouched. But
     * that is not a property of a shallower track. It is a property of taking depth away from the
     * first half of a run, where the track has only just formed and has nothing to spare. Take it
     * only from the second half and the cost is not paid at all: at half wear this look is the
     * picture it was before, pixel for pixel.
     *
     * <p>
     * So the depth follows the run until the track has bedded in and then advances at this fraction
     * of the rate, which is a truer story than a straight line was as well as a lighter one - a
     * trodden path can only go so deep, and after that it spreads. A fully worn face falls from
     * 79.47 levels below the unworn ground beside it to 72.02, where the next darkest look is 69.74
     * and the gap from that one to the third is 3.08. This look is inside the dark cluster now
     * rather than ten levels clear of everything in it.
     *
     * <p>
     * The knee is {@link #LIGHTER} rather than a number of its own, and that is load-bearing rather
     * than tidy. A lighter look is its parent's run stopped at that fraction, so this is the last
     * place the shape can bend without moving the lighter look with it - which is measured rather
     * than argued: 1312 pictures of 1312 bit-identical to the run before this change, at four curve
     * tilts and four strengths. Written this way the guarantee keeps itself; move LIGHTER and the
     * lighter look still cannot move, because the arm that bends is written on the same number the
     * lighter look stops at.
     *
     * <p>
     * A piecewise shape is the only one in this file, and it earns that by removing exactly the part
     * of a straight line that measured badly and nothing else. What it costs is the fully worn
     * track's separation from the ground beside it, 34.24 levels to 18.33, and the fissures inside
     * the track, which rise from 4.89 to 9.51 against a whole-face 14.08 - so a fully worn path
     * reads shallower, and less walked flat, than it did. What it does not cost is the track's
     * shape: the pixel count, the connectivity and the piece count the fraction above is written
     * around are untouched at every strength, which is what picking the depth rather than the
     * coverage buys. The corner itself is 0.04 of a colour level, where the run already has one of
     * 0.34 at its sixty-ninth gradation that nobody has ever reported.
     */
    private static final float CRACKED_RUB_SETTLE = 0.15f;

    /**
     * How far along its coverage a look starts, as a fraction of that coverage's range.
     *
     * <p>
     * On the coverage and never on the depth, which is the whole of why this is not the lift that
     * {@link #ease} records having removed. The depth still starts at nothing, so the first
     * gradation is still very nearly untouched ground; what starts part way along is how much of the
     * face the operator has claimed. Measured, that is what the early run was short of. A crack lays
     * a fissure network of a fixed width and then multiplies its whole contrast by the coverage, and
     * at the first of eighty gradations that coverage was 0.011 - so what a player was offered for
     * the first ninth of a run was a whole-face dimming of under two per cent and not one pixel of
     * two hundred and fifty-six moved by an amount anybody can see.
     *
     * <p>
     * Applied to the eased value rather than to the product, so that the end of a run is a fixed
     * point: this returns exactly one at an eased value of one, every coverage argument ends where
     * it always did, and eight of the nine families' fully worn ground is bit-identical to what it
     * was. Flooring the product instead is the obvious way to write it and was measured: no coverage
     * product reaches one at all - a crack's tops out at 0.9 and a rub's at 0.72 - so the whole range
     * compresses and every family's fully worn ground darkens, sand by 3.22 levels and snow by 5.90.
     *
     * <p>
     * Not given to the plain rub or to the lighter rub, and that is measured rather than tidy.
     * {@link WearCompositor#applyOverlay} claims pixels by an integer budget of
     * {@code round(coverage * 256)}, so a floor on its coverage does not deepen the early picture at
     * all - it only shifts which whole pixels are claimed - and near the end of a run the budget
     * stops incrementing at one pair of neighbouring gradations. It costs that look nearly two fifths
     * of its smallest step, 0.1367 to 0.0846, and buys the gap between wear level one and wear level
     * six a tenth. The buffed rub keeps the floor because there it costs nothing at all: 0.1055
     * either way.
     */
    private static final float COVERAGE_FLOOR = 0.12f;

    /** The eased value a coverage argument is driven by, floored. One at one, so a run's end never moves. */
    private static float spread(float eased) {
        if (eased <= 0f) return 0f;
        if (eased >= 1f) return eased;
        return COVERAGE_FLOOR + (1f - COVERAGE_FLOOR) * eased;
    }

    /**
     * One face, worn.
     *
     * <p>
     * Split out so that anything wanting to show somebody what a look does - the picker in the
     * wear editor, in particular - shows them the same arithmetic the atlas will actually run,
     * rather than an impression of it. A preview that can drift from the thing it previews is
     * worse than no preview.
     *
     * <p>
     * The name chooses between ten arrangements of three operators, every one of them working off
     * the block's own pixels so it is safe on a block nobody drew art for. Three are the operators
     * themselves: crack dulls the surface and splits it open, smooth flattens it, and the default
     * rubs a patch of it away. Three are those run with a lighter hand, one of them a lighter
     * compound, which is the same call with
     * the eased progress scaled and nothing else - so a lite look can only ever show a state its
     * parent genuinely passes through. One is the flattening pushed further, done at the operator's
     * own parameters rather than by multiplying strength, because strength is the family's setting
     * and a look that quietly rescaled it would make that setting lie. The last three run two
     * operators in sequence and live in {@link WearCompositor}, beside the ones they compose,
     * because the order they run in is pixel arithmetic and not a preference.
     *
     * <p>
     * The eleventh takes the cover off and shows what is underneath. That one needs two faces rather
     * than one and lives in {@link #grassPass} next door. Anything unrecognised - including the old
     * sand and polish names still sitting in people's configs - falls to the default, so no file
     * breaks.
     *
     * @param progress how far along its run this face is, from 0 to 1
     */
    public static int[] wearPass(int[] pixels, int size, String pattern, float progress, float strength, int rotation) {
        return wearPass(pixels, size, pattern, progress, strength, rotation, TrmtConfig.wearCurve);
    }

    /**
     * The same, told the player's curve rather than reading it.
     *
     * <p>
     * Because the config is re-read on the server thread and a stitch runs on the client's: two
     * sprites of one run composed against two different curves would be a seam nothing explains.
     */
    public static int[] wearPass(int[] pixels, int size, String pattern, float progress, float strength, int rotation,
        float curve) {
        // How evenly the change is spread depends on which operator is about to run, so the look
        // is asked rather than assumed: the crack and both buffings divide their own change nearly
        // evenly and want a straight line, while the rub has barely started by the halfway mark and
        // needs the curve to pull its late saturation forward.
        float eased = ease(progress, pattern, curve);
        // A lighter look is its parent's run stopped early, so it is the eased value that moves and
        // never the arguments around it. Each pair below is visibly the same call twice.
        float lite = eased * LIGHTER;
        // Coverage only, and only for the looks whose coverage is not an integer pixel budget; see
        // COVERAGE_FLOOR. Every depth argument below still starts at nothing.
        float spread = spread(eased);
        float liteSpread = spread(lite);

        if (FamilySettings.PATTERN_CRACK.equals(pattern)) {
            return WearCompositor.applyCrack(pixels, size, spread * 0.9f * strength, eased * strength, rotation);
        }
        if (FamilySettings.PATTERN_CRACK_LITE.equals(pattern)) {
            return WearCompositor.applyCrack(pixels, size, liteSpread * 0.9f * strength, lite * strength, rotation);
        }
        if (FamilySettings.PATTERN_SMOOTH.equals(pattern)) {
            return WearCompositor.applyPolish(
                pixels,
                size,
                WearCompositor.POLISH_CONTRAST,
                WearCompositor.POLISH_LUMA,
                eased * strength);
        }
        if (FamilySettings.PATTERN_SMOOTH_HEAVY.equals(pattern)) {
            return WearCompositor
                .applyPolish(pixels, size, WearCompositor.HEAVY_CONTRAST, WearCompositor.HEAVY_LUMA, eased * strength);
        }
        if (FamilySettings.PATTERN_SMOOTH_CRACK.equals(pattern)) {
            // The buffing runs the whole way and the crack stops short of it, which is the one
            // asymmetry in here and is explained on SMOOTHED_CRACK_RUN.
            float cut = eased * SMOOTHED_CRACK_RUN;
            return WearCompositor.applySmoothedCrack(
                pixels,
                size,
                spread * SMOOTHED_CRACK_RUN * 0.9f * strength,
                cut * strength,
                eased * strength,
                rotation);
        }
        if (FamilySettings.PATTERN_SMOOTH_DIRT.equals(pattern)) {
            // Depth unscaled, and passed without strength, exactly as the plain rub below passes
            // it. That the rub's depth ignores strength is a quirk of the operator older than this
            // change; a compound that honoured it while its parent did not would be worse than the
            // quirk, because nobody could predict either from a config file.
            return WearCompositor.applySmoothedRub(
                pixels,
                size,
                spread * 0.72f * SMOOTHED_RUB_TRACK * strength,
                eased,
                eased * strength,
                rotation);
        }
        if (FamilySettings.PATTERN_CRACK_DIRT.equals(pattern)) {
            // The only look where every part is held back, because it is the only one where two
            // darkening operators run over one face; each fraction is explained on its own constant.
            // Depth is passed without strength, exactly as both other rubs pass it, for the reason
            // written on the smoothed one - but no longer passed unscaled, and the shape it is
            // scaled by is the whole of CRACKED_RUB_SETTLE.
            float run = eased * CRACKED_RUB_RUN;
            float settled = eased <= LIGHTER ? eased : LIGHTER + (eased - LIGHTER) * CRACKED_RUB_SETTLE;
            return WearCompositor.applyCrackedRub(
                pixels,
                size,
                spread * 0.72f * CRACKED_RUB_TRACK * strength,
                settled,
                spread * CRACKED_RUB_RUN * 0.9f * strength,
                run * strength,
                rotation);
        }
        if (FamilySettings.PATTERN_CRACK_DIRT_LITE.equals(pattern)) {
            // The block above with lite in place of eased throughout, the run and the settling
            // inside it included, which is the same lines every other pair in here is. Worth saying
            // what that buys for a compound rather than for one operator: every picture this look
            // draws is the one its parent draws at 0.55 of the progress, bit for bit, across eight
            // faces, four rotations and forty-one points along the run. It has no state of its own
            // to go wrong.
            //
            // The settling's second arm never fires here, because lite cannot exceed LIGHTER. That
            // is not an accident of the numbers, it is why the knee is written at LIGHTER: this look
            // is exactly the part of its parent's run that happens before the bend, so the parent
            // can be lightened past it without this one moving at all.
            float run = lite * CRACKED_RUB_RUN;
            float settled = lite <= LIGHTER ? lite : LIGHTER + (lite - LIGHTER) * CRACKED_RUB_SETTLE;
            return WearCompositor.applyCrackedRub(
                pixels,
                size,
                liteSpread * 0.72f * CRACKED_RUB_TRACK * strength,
                settled,
                liteSpread * CRACKED_RUB_RUN * 0.9f * strength,
                run * strength,
                rotation);
        }
        // Ranked once and shared, because both looks below want the same ranking of the same pixels
        // and it is the dearest thing either of them does.
        float[] order = WearCompositor.sourceWearOrder(pixels, size, rotation);
        if (FamilySettings.PATTERN_DIRT_LITE.equals(pattern)) {
            return WearCompositor.applyOverlay(pixels, size, order, size, lite * 0.72f * strength, lite);
        }
        return WearCompositor.applyOverlay(pixels, size, order, size, eased * 0.72f * strength, eased);
    }

    /**
     * How a look's run is spread across its gradations, before any tilt the player asks for.
     *
     * <p>
     * One number per operator rather than one for the mod, because the three of them do not
     * saturate in the same direction and no single exponent can serve them. The measure that fixes
     * these is the smallest difference between any two neighbouring gradations, on the least
     * forgiving of eight vanilla faces at all four rotations, at sixteen gradations - a run is only
     * as legible as its least legible step, and an average hides exactly the failure worth hunting.
     * All four rotations are built and a path lays them side by side, so the worst pair over the
     * whole set is a pair somebody meets.
     *
     * <p>
     * The eight faces are stone, cobblestone, dirt, sand, gravel, netherrack, end stone and snow,
     * written down because the measure cannot recover them: it is a worst case and netherrack is the
     * worst of the eight for every look, so several different eights print the same sweep and a
     * later run on another set will disagree with these without anything being wrong. Every figure
     * here was taken at sixteen gradations, which is no longer the default - a run is now divided
     * into seventy-nine steps rather than fifteen and every step is about a fifth the size - so any
     * figure added from now on says which count it came from. At eighty the crack's smallest step is
     * 0.3906 and the rub's 0.1367 and both still give every gradation its own picture, while the
     * plain smoothing gives 45 of 80, the heavy smoothing 70 and the lighter rub 77: the span of
     * those operators being too small to divide that finely, rather than a fault in the exponent.
     *
     * <p>
     * What that found is not what the comment this replaces assumed, and the assumption was the
     * fault rather than the number. The crack is very nearly a straight line in its own parameter:
     * a fifth of the way along it has done between eighteen and twenty-one per cent of everything
     * it will ever do, not the half that used to be written here, and both smoothings are the same.
     * A straight line through a straight operator is what divides its change most evenly, so those
     * want an exponent of one and a curve below one only takes it away again - the crack's smallest
     * step ran 1.03 at the 0.45 this used to be, against 2.29 here.
     *
     * <p>
     * The rub is the opposite and that is the whole of why one number cannot do.
     * {@link WearCompositor#applyOverlay} works by rank, recolouring the most exposed pixels and
     * leaving every other one bit-identical, and early in a run both the count it has claimed and
     * the depth it has taken them to are small at once. Measured, it has done four to eight per
     * cent of its run at a parameter of a fifth and a quarter to two fifths at a half. A straight
     * line through that spends almost the entire run in its last few gradations: at an exponent of
     * one the rub's smallest step is 0.23 of a colour level, which is nothing, against 1.73 here.
     * Five shipped families draw it - dirt, sand, gravel, snow and ice - so it decides what most
     * worn ground looks like.
     *
     * <p>
     * The cracked-and-rubbed compound is given this as well, and is the first look to share a number
     * on its own measurement rather than by parentage - the two lighter looks share theirs because
     * they are the same operator run shorter. It is also the only look on the straight line with a
     * rub in it, so the resemblance needs explaining rather than assuming: it has none of the rub's
     * dead first half, because the crack runs first and is very nearly linear in its own parameter,
     * so there is change in the early gradations for a straight line to spread. At sixteen
     * gradations its sweep reads 2.178, 2.263, 2.518, 2.534 and 2.202 across exponents from 0.90 to
     * 1.10, and at eighty 0.319, 0.371, 0.384, 0.344 and 0.234 across the same - the two counts put
     * the peak on opposite sides of one, so it is a peak at one to the accuracy this has.
     *
     * <p>
     * One thing that number costs, written here rather than left to be discovered. At an exponent of
     * one the first of the picker's three previews is a plain crack, because the track has claimed
     * about four per cent of the face by then and the crack is doing all the work. An exponent of
     * 0.80 stands clear there and costs the run a fifth of its smallest step, which is the measure
     * this file tunes on, so the run keeps the straight line and the picker keeps the duplicate.
     */
    private static final float CURVE_EVEN = 1.00f;

    /**
     * The rub's own, and the reason the one above cannot be the only one.
     *
     * <p>
     * Six tenths is its own measured best rather than a compromise: the sweep reads 1.33, 1.48,
     * 1.66, 1.73, 1.44, 1.06 across exponents from 0.45 to 0.70, a clean peak with the fall on
     * either side of it steep enough to be unmistakable.
     */
    private static final float CURVE_RUB = 0.60f;

    /**
     * The buffed-and-rubbed compound's, which lands between its two halves rather than at either.
     *
     * <p>
     * Which is the best evidence that these numbers belong to the operators and not to taste: a
     * look that buffs a whole face and then rubs part of it measures at eight tenths, squarely
     * between the buffing's straight line and the rub's six tenths, without anybody putting it
     * there. The buffing moves the tones the rub reads its floor and shadow off, and the floor is
     * what stalls a plain rub late, so the stall arrives later here.
     */
    private static final float CURVE_SMOOTHED_RUB = 0.80f;

    /**
     * The buffed-and-cracked compound's, and the only number here above one.
     *
     * <p>
     * Above its parent's rather than below it, because the buffing underneath has already taken the
     * surround's relief away and the fissures arrive against a flatter ground, so the crack's own
     * saturation lands later than it does on an unbuffed face. Earned rather than tidy: the sweep
     * reads 1.80, 1.89, 1.99, 2.09, 1.94 across exponents from 0.95 to 1.15, a smooth hump rather
     * than a spike, and a straight line here costs a fifth of a colour level - which is real where
     * the tenths given up elsewhere are not.
     */
    private static final float CURVE_SMOOTHED_CRACK = 1.10f;

    /**
     * Which of the four a look is drawn on.
     *
     * <p>
     * Branching in the same order as {@link #wearPass} and falling the same way at the end, which
     * is the one thing in here that must not drift. That method falls through to the rub for any
     * name it does not recognise, the older {@code sand} and {@code polish} names still sitting in
     * people's config files included, so a look that fell to a straight line here would be drawn on
     * one look's schedule through another look's operator - and nobody reading either file could
     * predict what came out. Kept adjacent to the branch list it mirrors for that reason.
     *
     * <p>
     * Six looks are given the straight line although four of them measure a shade better just
     * above it, and the reason is measurable rather than a preference for round numbers. The plain
     * and heavy smoothings have no peak to take: their sweeps bounce on a floor, non-monotone by a
     * factor of two between neighbouring steps, which is what an operator out of range looks like.
     * The crack does have a real peak, at 1.05 worth a tenth of a level, and it is not taken
     * because the far side of it is a cliff - 1.10 gives back that tenth and more, 1.20 gives back
     * half the step - so a default sitting a hundredth from a fall would punish anybody who moved
     * {@code client.wearCurve} up at all.
     *
     * <p>
     * All three lighter looks keep their parents' numbers, which is what lets {@link #LIGHTER}'s
     * promise stand word for word, and for the third of them that is paid for rather than free. The
     * lighter rub measures three hundredths better on its own at 0.55, well inside the spread
     * between one rotation and the next. The lighter cracked-and-rubbed does not: its own sweep
     * peaks at 0.90 rather than at its parent's 1.00, and at eighty gradations that peak was worth
     * 0.2187 of a colour level against 0.0039 here. {@link #COVERAGE_FLOOR} has since taken the
     * straight line to 0.2461, so it now measures better than the peak it gave up.
     *
     * <p>
     * Given away deliberately, because the exponent decides where along a run a gradation falls. A
     * lite look drawn on a different one is no longer its parent's run stopped early but a
     * differently paced run, and the picker's three columns would stop comparing the same place in
     * two runs - which is the one thing the column positions exist to do. What the straight line
     * buys back is exact rather than approximate: on it, every picture the lighter compound draws is
     * bit-identical to its parent's at 0.55 of the progress. What it costs is one step of
     * seventy-nine, the step off untouched ground, which is where the lighter crack's own smallest
     * step lived as well; all eighty gradations still come out distinct. Both of those steps have
     * since been lifted by {@link #COVERAGE_FLOOR} - the lighter crack's from 0.1016 to 0.2383 -
     * because the step off untouched ground is exactly what a coverage floor is for.
     */
    private static float curveFor(String pattern) {
        if (FamilySettings.PATTERN_CRACK.equals(pattern)) return CURVE_EVEN;
        if (FamilySettings.PATTERN_CRACK_LITE.equals(pattern)) return CURVE_EVEN;
        if (FamilySettings.PATTERN_SMOOTH.equals(pattern)) return CURVE_EVEN;
        if (FamilySettings.PATTERN_SMOOTH_HEAVY.equals(pattern)) return CURVE_EVEN;
        if (FamilySettings.PATTERN_SMOOTH_CRACK.equals(pattern)) return CURVE_SMOOTHED_CRACK;
        if (FamilySettings.PATTERN_SMOOTH_DIRT.equals(pattern)) return CURVE_SMOOTHED_RUB;
        if (FamilySettings.PATTERN_CRACK_DIRT.equals(pattern)) return CURVE_EVEN;
        if (FamilySettings.PATTERN_CRACK_DIRT_LITE.equals(pattern)) return CURVE_EVEN;
        if (FamilySettings.PATTERN_DIRT_LITE.equals(pattern)) return CURVE_RUB;
        return CURVE_RUB;
    }

    /**
     * How hard the operator presses at a given point along a look's run.
     *
     * <p>
     * A plain power on the look's own exponent, scaled by the player's. What used to be here as
     * well was a lift off zero, so the first gradation started a little way along rather than at
     * nothing, and it has gone because the fault it answered no longer exists. It was written on
     * the reasoning that an evenly spread first step moves about six per cent of a run's change and
     * wants help. Measured at the exponents above, the step off untouched ground is nowhere the
     * smallest step in a run and is the largest in most of them - the crack 2.76 against a worst of
     * 2.29, the rub 1.88 against 1.73 - because a lift now takes change from the fifteen steps that
     * are short of it and hands it to the one that already has the most. Seven of the eight looks
     * measured worse with it.
     *
     * <p>
     * The eighth is the lighter rub, which gains seven hundredths and is the one honest cost of
     * removing it: it is the only look whose first step IS its smallest step, which is exactly why
     * a lift helps it and nothing else. No shipped family selects it, and seven hundredths of a
     * colour level out of 255 does not buy back a discontinuity every other look is paying for.
     *
     * <p>
     * Told the pattern rather than the family on purpose. Two families that both name the same look
     * have to get the same run out of it, or the name in the config file does not mean anything;
     * asking by look makes any other outcome impossible to express by accident.
     *
     * <p>
     * None of this moves where a run ENDS, and that is arithmetic rather than luck: this returns
     * exactly one at a progress of one for every exponent and every setting, so the fully worn
     * picture is a fixed point of every change here. Measured bit-identical across exponents from
     * 0.45 to 1.60 on all eight looks, eight faces and four rotations.
     */
    private static float ease(float progress, String pattern, float curve) {
        if (progress <= 0f) return 0f;
        float tilt = curve <= 0f ? 1f : curve;
        return (float) Math.pow(progress, curveFor(pattern) * tilt);
    }

    // ------------------------------------------------------------------
    // Sizes
    // ------------------------------------------------------------------

    /** Edge length of a square block of pixels. */
    private static int edgeOf(int[] pixels) {
        return pixels == null ? 0 : (int) Math.round(Math.sqrt(pixels.length));
    }

    /** Nearest-neighbour resize, for a face read at another size from the edge its sprite is stitched at. */
    private static int[] rescale(int[] pixels, int size) {
        int from = edgeOf(pixels);
        if (from == size) return pixels;
        int[] out = new int[size * size];
        for (int y = 0; y < size; y++) {
            int sy = y * from / size;
            for (int x = 0; x < size; x++) {
                out[y * size + x] = pixels[sy * from + (x * from / size)];
            }
        }
        return out;
    }

    /**
     * Whether anything should actually draw this sprite.
     *
     * <p>
     * False until the sprite pass has installed its picture, and for good when the block's own texture could
     * not be read - which happens more often than it sounds, because a block's icon name is not obliged to
     * correspond to a file, and in a pack this size plenty do not. The sprite still exists, because it was
     * registered with the atlas before anyone knew, but a lookup skips it and the renderer falls to whatever lies
     * below it, which {@link #markUnusable} sets out.
     *
     * <p>
     * Getting this wrong is what made every worn block a flat slab: the placeholder pixels a sprite is stitched
     * with were being drawn as though they were wear.
     */
    public boolean isUsable() {
        return usable;
    }

    /**
     * The surface this sprite stands for, named the way the config names it.
     *
     * <p>
     * For reporting only. Sixteen Chisel variants sit behind one registry name and each is meant
     * to wear in its own pixels, so the metadata is half the answer to which of them managed it -
     * a count of how many sprites improvised says nothing at all about who.
     */
    public String originName() {
        if (origin == null) return originFamily.key() + " fallback";
        Object registered = net.minecraft.core.Registry.BLOCK.getKey(origin);
        return (registered == null ? String.valueOf(origin) : registered.toString()) + ":" + originMeta;
    }

    /**
     * Reads and resets how many wear sprites have been sized for the stitcher since this was last read. For the log.
     */
    static synchronized int takeLoads() {
        int count = loads;
        loads = 0;
        return count;
    }

    /** Reads and resets how long those loads took together, in whole milliseconds. For the log. */
    static synchronized long takeLoadMillis() {
        long millis = loadNanos / 1000000L;
        loadNanos = 0L;
        return millis;
    }

    /**
     * Counts one load for the log. Under the class lock, as the placeholders are and for the same reason: a mod that
     * loads sprites in parallel would otherwise lose counts, and the line would under-report a stitch that did run.
     */
    private static synchronized void countLoad(long nanos) {
        loads++;
        loadNanos += nanos;
    }

    /**
     * A flat square of the placeholder colour, for a sprite that could not be made.
     *
     * <p>
     * Both older editions keep one chain per edge, shared between sprites, because the atlas asked every sprite for
     * every mip level and two hundred thousand of them would otherwise be two hundred thousand chains. There are no
     * levels to fill here - the atlas mipmaps the PNG itself - so there is nothing worth sharing and no cache to let
     * go of at the start of a stitch.
     *
     * <p>
     * Still the flat colour rather than nothing, for the reason it always was: {@code usable} is false, so a lookup
     * falls past this, and the colour is what somebody would see if one ever did not.
     */
    private static GeneratedPack.Sheet blank(int size) {
        int[] flat = new int[size * size];
        Arrays.fill(flat, PLACEHOLDER_COLOUR);
        return new GeneratedPack.Sheet(flat, size, size, null);
    }


}
