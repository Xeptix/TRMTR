package com.trmtgtnh.client.texture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * How much of the block atlas the wear textures may take, worked out from numbers rather than from a stitch.
 *
 * <p>
 * The planner used to price the atlas twice and cap it once, and the three came apart. The pre-flight
 * counted one appearance per block where the planner makes one set per face, two appearances for a lawn,
 * and it counted neither the grass fringes nor the side walls; it then held the ramp at sixteen gradations
 * whatever the room said, and the only cap after it counted sprites where the room is counted in slots. On
 * a thirty-two pixel pack a sprite is four slots, so a plan that sat at a quarter of its ceiling ran a
 * quarter past the room the atlas had left, and nothing noticed, because each of the three was right about
 * the thing it counted.
 *
 * <p>
 * So the pricing lives here, once. The pre-flight and the cap are the same sums, the planner is handed a
 * gate by the plan rather than keeping a figure of its own, and nothing from the game is in it, which is
 * what lets a test pin every figure exactly. It is on the list CoreStaysPortableTest keeps free of
 * Minecraft. What a texture is, what a block is and what the log says stay in WearTextures, which hands
 * every figure in.
 *
 * <p>
 * Every figure handed in is a price in slots at the size the sprite will be stitched at, worked out from the
 * file it is drawn from. The first form of this class priced every sprite, the pack's own included, at the
 * edge of vanilla dirt and stone, and that is wrong both ways: a thirty-two pixel pack that redraws only
 * vanilla, laid over a modpack whose own blocks stay at sixteen, was priced four times over and gave up faces
 * it had room for, while a mod's own larger faces were priced below what they took. Which face a sprite is
 * drawn from, and the edge that gives, is FaceRules'; this class counts slots. What actually went to the
 * stitcher is tallied here as well, so that the log can hold the prices to it.
 */
public final class AtlasPlan {

    /** The edge of one atlas slot, in pixels. The room is counted in these. */
    public static final int CELL = 16;

    /** The largest square the atlas is planned to, whatever the card will address, unless the player asks for more. */
    public static final int LARGEST_SIDE = 8192;

    /**
     * The largest it is planned to where the player has asked for more (client.largerAtlas) and the card reports it.
     */
    public static final int LARGER_SIDE = 16384;

    /**
     * The side planned to at most: {@link #LARGEST_SIDE}, or {@link #LARGER_SIDE} once the player's setting says so.
     * Set
     * from the settings as they are read, and read by the stitch.
     */
    private static volatile int ceiling = LARGEST_SIDE;

    /** The share of the square held back for the stitcher: one part in this many. */
    public static final int RESERVE_SHARE = 16;

    /** The share of the square taken as spoken for when nothing measured the pack: one part in this many. */
    public static final int UNMEASURED_SHARE = 2;

    /**
     * Pixels added to the width, and again to the height, of every sprite the game loads from a file while
     * anisotropic filtering is on. No wear sprite grows: it is made from the face with that border taken off; see
     * AnisotropicBorder.
     */
    public static final int ANISOTROPIC_GROWTH = 16;

    /**
     * The bytes at the head of a PNG file that give its width: the eight-byte signature, the first chunk's
     * length and type, and the width and height that chunk opens with.
     */
    public static final int HEADER_BYTES = 24;

    /**
     * The widest texture a header is believed about. No card addresses a texture wider than this, so a header
     * claiming more is taken to be damaged rather than priced.
     */
    public static final int WIDEST_FILE = 32768;

    /** The eight bytes every PNG file opens with. */
    private static final int[] PNG_SIGNATURE = { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

    /** What stopped the plan taking more surface sets. */
    public enum Limit {
        /** Nothing did: every set the walk recorded was kept. */
        NONE,
        /** surfaces.maxTexturedSurfaces, which bounds what is priced as well as what is kept. */
        CEILING,
        /**
         * The room left in the atlas, which only stops a set once the ramp is held at the floor, or at a lower
         * count that was asked for.
         */
        ROOM,
        /** client.maxWearSprites, counting every sprite this mod registers. */
        SPRITES,
        /**
         * The sprites the renderer can index across the whole block atlas ({@link Measure#spriteRoom}), counting every
         * sprite this mod registers once the rest of the pack has its own (1.0.0, canvas-sprites).
         */
        RENDERER
    }

    /**
     * What was measured of the atlas this stitch, as figures.
     *
     * <p>
     * When the measure was not taken, only the card's size and the filtering setting are read, and the pack
     * is taken to have had half the square.
     */
    public static final class Measure {

        /** Whether the injection that measures the atlas ran this stitch. */
        public final boolean taken;

        /** How many textures the atlas had gathered when it was measured. */
        public final int packSprites;

        /** How many of those had a file whose header gave a width. */
        public final int packRead;

        /**
         * The slots those came to, each at the width its header gave, grown by sixteen pixels a side where
         * anisotropic filtering widens it.
         */
        public final long packReadCells;

        /** The largest texture the card says it will address, or minus one where the probe failed. */
        public final int glMaximum;

        /**
         * Whether anisotropic filtering will widen every sprite the game loads from a file. It grows the pack's own
         * textures in the price, and missingno in what the stitch line counts; nothing this mod registers grows
         * with it.
         */
        public final boolean anisotropic;

        /** Actual largest atlas edge this stitch may use; the usual planner retains its configured 8192 cap. */
        public final int stitchSide;

        /** Pixels Angelica reserves on each side of a stitched block sprite. */
        public final int holderGutter;

        /**
         * The share of the square, in thousandths, the stitcher that will pack this atlas was measured to fill, or
         * nought where it was not measured (0.9.229, glow-pixels). Above nought, and with a wish priced in pixels
         * ({@link Wish#withPixels}), the plan prices this mod's holders by their exact pixels and holds them and the
         * pack to this share of the square; at nought, every set is priced in whole slots and a sixteenth is held
         * back, as before. Only the edition can say which stitcher will run, so only the edition passes it.
         */
        public final int fillPerMille;

        /**
         * The most sprites the whole block atlas may hold, missingno and the rest of the pack's included, or nought
         * where nothing but the room bounds the count (1.0.0, canvas-sprites). Only an edition can say which renderer
         * will read the atlas, so only the edition passes it: Canvas for 1.16.5 keeps one row of a sprite information
         * texture for every sprite, as many rows as the count rounded up to a power of two, and a texture taller than
         * the card's largest is never uploaded, so every block its shaders draw is then read from the wrong place in
         * the atlas. Above nought, every candidate {@link #planForBorderBudget} weighs is held to it as it is to the
         * room, so the rotations give way first, then the gradations, and a face's own pixels last.
         */
        public final int spriteRoom;

        public Measure(boolean taken, int packSprites, int packRead, long packReadCells, int glMaximum,
            boolean anisotropic) {
            this(taken, packSprites, packRead, packReadCells, glMaximum, anisotropic, side(glMaximum), 0);
        }

        public Measure(boolean taken, int packSprites, int packRead, long packReadCells, int glMaximum,
            boolean anisotropic, int stitchSide, int holderGutter) {
            this(taken, packSprites, packRead, packReadCells, glMaximum, anisotropic, stitchSide, holderGutter, 0);
        }

        public Measure(boolean taken, int packSprites, int packRead, long packReadCells, int glMaximum,
            boolean anisotropic, int stitchSide, int holderGutter, int fillPerMille) {
            this(
                taken,
                packSprites,
                packRead,
                packReadCells,
                glMaximum,
                anisotropic,
                stitchSide,
                holderGutter,
                fillPerMille,
                0);
        }

        public Measure(boolean taken, int packSprites, int packRead, long packReadCells, int glMaximum,
            boolean anisotropic, int stitchSide, int holderGutter, int fillPerMille, int spriteRoom) {
            this.taken = taken;
            this.packSprites = packSprites;
            this.packRead = packRead;
            this.packReadCells = packReadCells;
            this.glMaximum = glMaximum;
            this.anisotropic = anisotropic;
            this.stitchSide = Math.max(1, Math.min(glMaximum > 0 ? glMaximum : LARGEST_SIDE, stitchSide));
            this.holderGutter = Math.max(0, holderGutter);
            this.fillPerMille = Math.max(0, Math.min(1000, fillPerMille));
            this.spriteRoom = Math.max(0, spriteRoom);
        }

        /** The same measure with this bound on the atlas's sprites, or none at nought. */
        public Measure withSpriteRoom(int sprites) {
            return new Measure(
                taken,
                packSprites,
                packRead,
                packReadCells,
                glMaximum,
                anisotropic,
                stitchSide,
                holderGutter,
                fillPerMille,
                sprites);
        }

        /**
         * The sprites this mod may register within {@link #spriteRoom}: what is left once missingno and the textures
         * the atlas had gathered when it was measured have theirs, never below none, or {@code Long.MAX_VALUE} where
         * nothing bounds the count. Where nothing measured the atlas the pack is taken to hold half of it, as the room
         * takes it to hold half the square.
         */
        public long ownSpriteRoom() {
            if (spriteRoom <= 0) return Long.MAX_VALUE;
            long pack = taken ? Math.max(0, packSprites) : spriteRoom / UNMEASURED_SHARE;
            return Math.max(0L, spriteRoom - pack - 1L);
        }

        public long squareCells() {
            return (long) stitchSide * stitchSide / ((long) CELL * CELL);
        }

        /** How many of the pack's textures could not be read, never below none. */
        public int packUnread() {
            return Math.max(0, Math.max(0, packSprites) - Math.max(0, packRead));
        }
    }

    /**
     * What the planner would register if nothing stopped it, priced in slots, and the ceilings the player has
     * set on it.
     *
     * <p>
     * The surface sets are held as an appearance count and a slot count apiece, in the order the planner's walk
     * makes them, both copied on the way in, so that nothing the caller does to its arrays afterwards reaches a
     * plan.
     */
    public static final class Wish {

        /**
         * The edge dirt and stone are drawn at. It prices a set with no recorded slots, and the pack's own
         * textures when not one of their files could be read; every other figure here was priced from its own
         * file before it arrived.
         */
        public final int wearEdge;

        /** The gradations per run asked for. */
        public final int gradations;

        /** The fewest gradations a run is drawn with where the room pays for fewer. */
        public final int floor;

        /** How many rotations every gradation is drawn at. */
        public final int rotations;

        /** How many appearances the family fallbacks come to. */
        public final int fallbackAppearances;

        /**
         * The slots one gradation of every fallback appearance takes at one rotation, each at its vanilla
         * file's edge.
         */
        public final long fallbackCells;

        /** The slots one grass-side fringe takes, at the edge of the overlay it is read from. */
        public final int fringeCells;

        /**
         * Sprites planned once each rather than per gradation: the mended sides, the grass walls and the wall an
         * unknown grass falls back on.
         */
        public final int sideSprites;

        /** The slots all of those take, each at the edge of its own block's side. */
        public final long sideCells;

        /** surfaces.maxTexturedSurfaces: how many of the surface sets may be priced or kept. */
        public final int surfaceCeiling;

        /** client.maxWearSprites: how many sprites may be planned in all. */
        public final int spriteCeiling;

        private final int[] surfaceSets;

        private final int[] surfaceCells;

        /**
         * Of the fallback appearances, how many are drawn with one pattern whatever the rotations (client.wearTurns'
         * turn only, for a family whose face turns: WearTurns.seeds), and the slots one gradation of those takes.
         * Nought where nothing is.
         */
        public final int fallbackOneAppearances;

        public final long fallbackOneCells;

        /** Per recorded surface set, whether it is drawn with one pattern whatever the rotations. */
        private final boolean[] surfaceOne;

        /** Per recorded surface set, how many of its appearances are glow overlays. */
        private final int[] surfaceGlows;

        /** Per recorded surface set, its glow sprites at one rotation, indexed by the gradations drawn. */
        private final int[][] surfaceGlowLit;

        /** Of the fallback appearances, how many are glow overlays, and their sprites by the gradations drawn. */
        public final int fallbackGlows;

        private final int[] fallbackGlowLit;

        /** Per recorded surface set, the slots one gradation of its glow overlays takes at one rotation. */
        private final int[] surfaceGlowCells;

        /** The slots one gradation of the fallback tier's glow overlays takes at one rotation. */
        public final long fallbackGlowCells;

        /**
         * The same prices in exact holder pixels (0.9.229, glow-pixels; {@link #withPixels}), or null where the wish is
         * priced in whole slots alone: per recorded set, one gradation at one rotation and its glow overlays' share of
         * that; the fallback tier's, of which the part drawn with one pattern and the glows' share; the fringe's; and
         * the side sprites', once each.
         */
        private final int[] surfacePixels;

        private final int[] surfaceGlowPixels;

        public final long fallbackPixels;

        public final long fallbackOnePixels;

        public final long fallbackGlowPixels;

        public final long fringePixels;

        public final long sidePixels;

        /**
         * A recorded set's slots at one rotation at this many gradations, its glows' only at the stages where they can
         * light (GlowRoomTest): the slots of the stages that cannot are taken off, rounded down, so the room is never
         * priced below what is stitched.
         */
        public long surfaceSetSlots(int index, int gradations) {
            long cells = surfaceSetCells(index);
            long g = Math.max(0, gradations);
            int glows = surfaceSetGlows(index);
            long glowCells = index < surfaceGlowCells.length ? Math.max(0L, Math.min(cells, surfaceGlowCells[index]))
                : 0L;
            if (glows <= 0 || glowCells <= 0L) return cells * g;
            long unlit = (long) glows * g - surfaceSetGlowSprites(index, gradations);
            return cells * g - unlit * glowCells / glows;
        }

        /** The fallback tier's glow slots at one rotation at this many gradations, at the stages that cannot light. */
        public long fallbackUnlitGlowSlots(int gradations) {
            if (fallbackGlows <= 0 || fallbackGlowCells <= 0L) return 0L;
            long unlit = (long) fallbackGlows * Math.max(0, gradations) - fallbackGlowSprites(gradations);
            return unlit * fallbackGlowCells / fallbackGlows;
        }

        /** Whether this wish carries its prices in exact holder pixels as well as in slots ({@link #withPixels}). */
        public boolean pricedInPixels() {
            return surfacePixels != null;
        }

        /**
         * One gradation of a recorded set at one rotation, in exact holder pixels; where no figure was recorded, its
         * slots' pixels, which is never less.
         */
        public long surfaceSetPixels(int index) {
            return surfacePixels != null && index < surfacePixels.length ? Math.max(0, surfacePixels[index])
                : surfaceSetCells(index) * CELL * CELL;
        }

        /**
         * {@link #surfaceSetSlots} in exact pixels: a recorded set's pixels at one rotation at this many gradations,
         * its glows' only at the stages where they can light. The pixels of the stages that cannot are taken off,
         * rounded down, so the room is never priced below what is stitched.
         */
        public long surfaceSetPixels(int index, int gradations) {
            long pixels = surfaceSetPixels(index);
            long g = Math.max(0, gradations);
            int glows = surfaceSetGlows(index);
            long glowPixels = surfaceGlowPixels != null && index < surfaceGlowPixels.length
                ? Math.max(0L, Math.min(pixels, surfaceGlowPixels[index]))
                : 0L;
            if (glows <= 0 || glowPixels <= 0L) return pixels * g;
            long unlit = (long) glows * g - surfaceSetGlowSprites(index, gradations);
            return pixels * g - unlit * glowPixels / glows;
        }

        /** {@link #fallbackUnlitGlowSlots} in exact pixels. */
        public long fallbackUnlitGlowPixels(int gradations) {
            if (fallbackGlows <= 0 || fallbackGlowPixels <= 0L) return 0L;
            long unlit = (long) fallbackGlows * Math.max(0, gradations) - fallbackGlowSprites(gradations);
            return unlit * fallbackGlowPixels / fallbackGlows;
        }

        /** A recorded set at this many gradations at one rotation, in the plan's unit: pixels, or else slots. */
        long surfaceSetUnits(int index, int gradations, boolean pixels) {
            return pixels ? surfaceSetPixels(index, gradations) : surfaceSetSlots(index, gradations);
        }

        /** The fallback tier's unlit glows at one rotation, in the plan's unit. */
        long fallbackUnlitGlowUnits(int gradations, boolean pixels) {
            return pixels ? fallbackUnlitGlowPixels(gradations) : fallbackUnlitGlowSlots(gradations);
        }

        /**
         * The same wish with its prices in exact holder pixels as well (0.9.229, glow-pixels), for a plan whose measure
         * names a measured stitcher: per recorded set, in the order of surfaceSets, one gradation at one rotation and
         * its glow overlays' share; the fallback tier's, the part of it drawn with one pattern and its glows'; the
         * fringe's; and the side sprites' together. The slot prices stay, and a measure that names no stitcher plans
         * in them as before.
         */
        public Wish withPixels(int[] surfacePixels, int[] surfaceGlowPixels, long fallbackPixels,
            long fallbackOnePixels, long fallbackGlowPixels, long fringePixels, long sidePixels) {
            return new Wish(
                wearEdge,
                gradations,
                floor,
                rotations,
                fallbackAppearances,
                fallbackCells,
                fringeCells,
                sideSprites,
                sideCells,
                surfaceSets,
                surfaceCells,
                surfaceCeiling,
                spriteCeiling,
                fallbackOneAppearances,
                fallbackOneCells,
                surfaceOne,
                surfaceGlows,
                surfaceGlowLit,
                surfaceGlowCells,
                this.fallbackGlows,
                fallbackGlowLit,
                fallbackGlowCells,
                surfacePixels == null ? new int[0] : surfacePixels,
                surfaceGlowPixels,
                fallbackPixels,
                fallbackOnePixels,
                fallbackGlowPixels,
                fringePixels,
                sidePixels);
        }

        /** A recorded set's glow sprites at one rotation at this many gradations: only the stages that can light. */
        public long surfaceSetGlowSprites(int index, int gradations) {
            int glows = index < surfaceGlows.length ? surfaceGlows[index] : 0;
            return litSprites(glows, index < surfaceGlowLit.length ? surfaceGlowLit[index] : null, gradations);
        }

        /** A recorded set's glow overlays, among its appearances. */
        public int surfaceSetGlows(int index) {
            return index < surfaceGlows.length ? Math.max(0, Math.min(surfaceSets[index], surfaceGlows[index])) : 0;
        }

        /** The fallback tier's glow sprites at one rotation at this many gradations. */
        public long fallbackGlowSprites(int gradations) {
            return litSprites(fallbackGlows, fallbackGlowLit, gradations);
        }

        public Wish(int wearEdge, int gradations, int floor, int rotations, int fallbackAppearances, long fallbackCells,
            int fringeCells, int sideSprites, long sideCells, int[] surfaceSets, int[] surfaceCells, int surfaceCeiling,
            int spriteCeiling) {
            this(
                wearEdge,
                gradations,
                floor,
                rotations,
                fallbackAppearances,
                fallbackCells,
                fringeCells,
                sideSprites,
                sideCells,
                surfaceSets,
                surfaceCells,
                surfaceCeiling,
                spriteCeiling,
                0,
                0L,
                null);
        }

        /**
         * The same, with what client.wearTurns' turn only draws with one pattern rather than one per rotation (0.9.229,
         * docs/goals/0.9.229-rotation-spec.md: "a quarter of the atlas space"): that part of the fallback tier, and
         * a flag per surface set, in the order of surfaceSets. A set so flagged is priced at one rotation whatever the
         * rotations, so dropping rotations for room never touches it.
         */
        public Wish(int wearEdge, int gradations, int floor, int rotations, int fallbackAppearances, long fallbackCells,
            int fringeCells, int sideSprites, long sideCells, int[] surfaceSets, int[] surfaceCells, int surfaceCeiling,
            int spriteCeiling, int fallbackOneAppearances, long fallbackOneCells, boolean[] surfaceOne) {
            this(
                wearEdge,
                gradations,
                floor,
                rotations,
                fallbackAppearances,
                fallbackCells,
                fringeCells,
                sideSprites,
                sideCells,
                surfaceSets,
                surfaceCells,
                surfaceCeiling,
                spriteCeiling,
                fallbackOneAppearances,
                fallbackOneCells,
                surfaceOne,
                null,
                null,
                null,
                0,
                null,
                0L);
        }

        /**
         * The same, with the glow overlays among the appearances counted only at the stages where they can light
         * (2026-11-17, GlowStagesTest): per recorded set, how many of its appearances are glows and, indexed by the
         * gradations drawn, how many glow sprites those come to at one rotation; and the same for the fallback tier.
         * Their slots are still priced at every stage, so the room is only ever more than enough.
         */
        public Wish(int wearEdge, int gradations, int floor, int rotations, int fallbackAppearances, long fallbackCells,
            int fringeCells, int sideSprites, long sideCells, int[] surfaceSets, int[] surfaceCells, int surfaceCeiling,
            int spriteCeiling, int fallbackOneAppearances, long fallbackOneCells, boolean[] surfaceOne,
            int[] surfaceGlows, int[][] surfaceGlowLit, int[] surfaceGlowCells, int fallbackGlows,
            int[] fallbackGlowLit, long fallbackGlowCells) {
            this(
                wearEdge,
                gradations,
                floor,
                rotations,
                fallbackAppearances,
                fallbackCells,
                fringeCells,
                sideSprites,
                sideCells,
                surfaceSets,
                surfaceCells,
                surfaceCeiling,
                spriteCeiling,
                fallbackOneAppearances,
                fallbackOneCells,
                surfaceOne,
                surfaceGlows,
                surfaceGlowLit,
                surfaceGlowCells,
                fallbackGlows,
                fallbackGlowLit,
                fallbackGlowCells,
                null,
                null,
                0L,
                0L,
                0L,
                0L,
                0L);
        }

        private Wish(int wearEdge, int gradations, int floor, int rotations, int fallbackAppearances,
            long fallbackCells, int fringeCells, int sideSprites, long sideCells, int[] surfaceSets, int[] surfaceCells,
            int surfaceCeiling, int spriteCeiling, int fallbackOneAppearances, long fallbackOneCells,
            boolean[] surfaceOne, int[] surfaceGlows, int[][] surfaceGlowLit, int[] surfaceGlowCells, int fallbackGlows,
            int[] fallbackGlowLit, long fallbackGlowCells, int[] surfacePixels, int[] surfaceGlowPixels,
            long fallbackPixels, long fallbackOnePixels, long fallbackGlowPixels, long fringePixels, long sidePixels) {
            this.surfacePixels = surfacePixels == null ? null : surfacePixels.clone();
            this.surfaceGlowPixels = surfaceGlowPixels == null ? new int[0] : surfaceGlowPixels.clone();
            this.fallbackPixels = Math.max(0L, fallbackPixels);
            this.fallbackOnePixels = Math.max(0L, Math.min(this.fallbackPixels, fallbackOnePixels));
            this.fallbackGlowPixels = Math.max(0L, Math.min(this.fallbackPixels, fallbackGlowPixels));
            this.fringePixels = Math.max(0L, fringePixels);
            this.sidePixels = Math.max(0L, sidePixels);
            this.surfaceGlows = surfaceGlows == null ? new int[0] : surfaceGlows.clone();
            this.surfaceGlowLit = surfaceGlowLit == null ? new int[0][] : surfaceGlowLit.clone();
            this.fallbackGlows = Math.max(0, Math.min(Math.max(0, fallbackAppearances), fallbackGlows));
            this.fallbackGlowLit = fallbackGlowLit;
            this.surfaceGlowCells = surfaceGlowCells == null ? new int[0] : surfaceGlowCells.clone();
            this.fallbackGlowCells = Math.max(0L, Math.min(Math.max(0L, fallbackCells), fallbackGlowCells));
            this.fallbackOneAppearances = Math
                .max(0, Math.min(Math.max(0, fallbackAppearances), fallbackOneAppearances));
            this.fallbackOneCells = Math.max(0L, Math.min(Math.max(0L, fallbackCells), fallbackOneCells));
            this.surfaceOne = surfaceOne == null ? new boolean[0] : surfaceOne.clone();
            this.wearEdge = wearEdge;
            this.gradations = gradations;
            this.floor = floor;
            this.rotations = rotations;
            this.fallbackAppearances = fallbackAppearances;
            this.fallbackCells = fallbackCells;
            this.fringeCells = fringeCells;
            this.sideSprites = sideSprites;
            this.sideCells = sideCells;
            this.surfaceSets = surfaceSets == null ? new int[0] : surfaceSets.clone();
            this.surfaceCells = surfaceCells == null ? new int[0] : surfaceCells.clone();
            this.surfaceCeiling = surfaceCeiling;
            this.spriteCeiling = spriteCeiling;
        }

        /** How many surface sets the walk recorded. */
        public int surfaceSetCount() {
            return surfaceSets.length;
        }

        /** The appearances one recorded surface set holds. */
        public int surfaceSet(int index) {
            return surfaceSets[index];
        }

        /**
         * One gradation of a set at one rotation, in slots; where no figure was recorded, its appearances at the
         * wear edge.
         */
        public long surfaceSetCells(int index) {
            return index < surfaceCells.length ? Math.max(0, surfaceCells[index])
                : (long) Math.max(0, surfaceSets[index]) * cellsFor(wearEdge);
        }

        /** Whether a recorded surface set is drawn with one pattern whatever the rotations. */
        public boolean surfaceSetOne(int index) {
            return index >= 0 && index < surfaceOne.length && surfaceOne[index];
        }

        /** The patterns a recorded surface set is drawn with at this many rotations: one if flagged, else all. */
        public int surfaceSetTurns(int index, int turns) {
            return surfaceSetOne(index) ? 1 : Math.max(1, turns);
        }

        /** The same wish with the four settings the log gives advice about replaced, every price kept. */
        public Wish withSettings(int gradations, int rotations, int surfaceCeiling, int spriteCeiling) {
            return new Wish(
                wearEdge,
                gradations,
                floor,
                rotations,
                fallbackAppearances,
                fallbackCells,
                fringeCells,
                sideSprites,
                sideCells,
                surfaceSets,
                surfaceCells,
                surfaceCeiling,
                spriteCeiling,
                fallbackOneAppearances,
                fallbackOneCells,
                surfaceOne,
                surfaceGlows,
                surfaceGlowLit,
                surfaceGlowCells,
                fallbackGlows,
                fallbackGlowLit,
                fallbackGlowCells,
                surfacePixels,
                surfaceGlowPixels,
                fallbackPixels,
                fallbackOnePixels,
                fallbackGlowPixels,
                fringePixels,
                sidePixels);
        }
    }

    /**
     * Glow sprites at one rotation at this many gradations: the table's figure where it has one, which counts only the
     * stages that can light, and every stage of every glow past it; never more than every stage, nor fewer than none.
     */
    public static long litSprites(int glows, int[] lit, int gradations) {
        long every = (long) Math.max(0, glows) * Math.max(0, gradations);
        if (gradations <= 0 || lit == null || gradations >= lit.length) return every;
        return Math.max(0L, Math.min(every, lit[gradations]));
    }

    /** Which of the surfaces the planner's walk is handed are natural ground, the first to be offered places. */
    public interface Ground<T> {

        /** Whether this surface is ground the game generates, rather than a decorative variant of one. */
        boolean natural(T surface);
    }

    /** The namespace vanilla's blocks are registered under, in every edition. */
    public static final String VANILLA = "minecraft";

    /**
     * Whether a registry name is one of vanilla's own blocks: its namespace is {@link #VANILLA}, matched without
     * regard to case. A name with no namespace, or none at all, is not.
     */
    public static boolean vanilla(String registryName) {
        if (registryName == null) return false;
        int colon = registryName.indexOf(':');
        return colon == VANILLA.length() && registryName.regionMatches(true, 0, VANILLA, 0, colon);
    }

    /**
     * The surfaces in the order the planner's walk offers them places: natural ground first, then everything else, each
     * part in the order it came, as a list of the caller's own.
     *
     * <p>
     * The walk keeps sets from the first, so whatever a limit cuts is cut from the end of this order. Before 0.9.229
     * that was registry-name order alone, and on the 1.20.1 copies with Chisel, Chipped and Rechiseled 1513 faces were
     * wanted, the ceiling of 1024 kept the first of them, and vanilla's nether wart block, warped wart, grass and dried
     * kelp sorted after chipped's and chisel's carvings: a worn nether wart block drew brown dirt while a thousand
     * decorative variants kept their own pixels. Ground that generates is what a path is worn across, so it is offered
     * a place before any variant of it (Xep, 2026-11-08: "Both"). Stable within each part, so a pack's order is the
     * same at every stitch and a set keeps its place while nothing is added.
     */
    public static <T> List<T> naturalFirst(List<T> surfaces, Ground<? super T> ground) {
        List<T> out = new ArrayList<T>(surfaces == null ? 0 : surfaces.size());
        if (surfaces == null) return out;
        List<T> rest = new ArrayList<T>();
        for (T surface : surfaces) {
            if (ground.natural(surface)) out.add(surface);
            else rest.add(surface);
        }
        out.addAll(rest);
        return out;
    }

    /** What the planner's walk asks before it makes each surface set. */
    public interface SetGate {

        /**
         * Whether a set of this many appearances, taking this many slots a gradation at one rotation, may be
         * made.
         */
        boolean take(int appearances, int cells);

        /**
         * The same, for a set that may be drawn with one pattern whatever the rotations ({@code one}: client.wearTurns'
         * turn only, for a face that turns). The cells are still one gradation at one rotation.
         */
        boolean take(int appearances, int cells, boolean one);

        /**
         * The same, with how many of the appearances are glow overlays, their sprites at one rotation by the
         * gradations drawn, counted only at the stages where they can light (GlowStagesTest), and the slots one
         * gradation of them takes at one rotation, priced only at those stages too (GlowRoomTest).
         */
        boolean take(int appearances, int cells, boolean one, int glows, int[] glowLit, int glowCells);

        /**
         * The same, with the set's exact holder pixels at one gradation and one rotation and its glow overlays' share
         * of them (0.9.229, glow-pixels), which a plan whose measure names a measured stitcher prices it by.
         */
        boolean take(int appearances, int cells, boolean one, int glows, int[] glowLit, int glowCells, int pixels,
            int glowPixels);

        /** Whether this gate has refused a set, after which it grants nothing. */
        boolean shut();
    }

    /**
     * Records the sets a walk would make, an appearance count and a slot count apiece, and refuses nothing.
     *
     * <p>
     * Handed to the planner's walk before any gradation count is chosen, so the plan prices the sets the real
     * walk will make, each at the size of the face it is built from, rather than one per block.
     */
    public static final class Census implements SetGate {

        private int[] sets = new int[64];

        private int[] cells = new int[64];

        private boolean[] ones = new boolean[64];

        private int[] glows = new int[64];

        private int[][] glowLit = new int[64][];

        private int[] glowCells = new int[64];

        private int[] pixels = new int[64];

        private int[] glowPixels = new int[64];

        private int count;

        @Override
        public boolean take(int appearances, int slots) {
            return take(appearances, slots, false);
        }

        @Override
        public boolean take(int appearances, int slots, boolean one) {
            return take(appearances, slots, one, 0, null, 0);
        }

        /** A set given in slots alone is taken as its slots' pixels, which is never less than it holds. */
        @Override
        public boolean take(int appearances, int slots, boolean one, int glows, int[] glowLit, int glowCells) {
            long cell = (long) CELL * CELL;
            return take(
                appearances,
                slots,
                one,
                glows,
                glowLit,
                glowCells,
                (int) Math.min(Integer.MAX_VALUE, Math.max(0, slots) * cell),
                (int) Math.min(Integer.MAX_VALUE, Math.max(0, glowCells) * cell));
        }

        @Override
        public boolean take(int appearances, int slots, boolean one, int glows, int[] glowLit, int glowCells,
            int pixels, int glowPixels) {
            if (count == sets.length) {
                sets = Arrays.copyOf(sets, count * 2);
                cells = Arrays.copyOf(cells, count * 2);
                ones = Arrays.copyOf(ones, count * 2);
                this.glows = Arrays.copyOf(this.glows, count * 2);
                this.glowLit = Arrays.copyOf(this.glowLit, count * 2);
                this.glowCells = Arrays.copyOf(this.glowCells, count * 2);
                this.pixels = Arrays.copyOf(this.pixels, count * 2);
                this.glowPixels = Arrays.copyOf(this.glowPixels, count * 2);
            }
            sets[count] = Math.max(0, appearances);
            cells[count] = Math.max(0, slots);
            ones[count] = one;
            this.glows[count] = Math.max(0, glows);
            this.glowLit[count] = glowLit;
            this.glowCells[count] = Math.max(0, glowCells);
            this.pixels[count] = Math.max(0, pixels);
            this.glowPixels[count] = Math.max(0, Math.min(Math.max(0, pixels), glowPixels));
            count++;
            return true;
        }

        /** Each recorded set's exact holder pixels at one gradation and one rotation, in the same order. */
        public int[] pixels() {
            return Arrays.copyOf(pixels, count);
        }

        /** Of those, each recorded set's glow overlays' pixels, in the same order. */
        public int[] glowPixels() {
            return Arrays.copyOf(glowPixels, count);
        }

        /** How many of each recorded set's appearances are glow overlays, in the same order. */
        public int[] glows() {
            return Arrays.copyOf(glows, count);
        }

        /** Each recorded set's glow sprites at one rotation by the gradations drawn, in the same order. */
        public int[][] glowLit() {
            return Arrays.copyOf(glowLit, count);
        }

        /** The slots one gradation of each recorded set's glow overlays takes at one rotation, in the same order. */
        public int[] glowCells() {
            return Arrays.copyOf(glowCells, count);
        }

        /**
         * Whether each set recorded so far is drawn with one pattern, in the same order, as an array of the caller's
         * own.
         */
        public boolean[] ones() {
            return Arrays.copyOf(ones, count);
        }

        @Override
        public boolean shut() {
            return false;
        }

        /** The appearance counts recorded so far, in the order they were taken, as an array of the caller's own. */
        public int[] sets() {
            return Arrays.copyOf(sets, count);
        }

        /** The slot counts recorded so far, in the same order, as an array of the caller's own. */
        public int[] cells() {
            return Arrays.copyOf(cells, count);
        }
    }

    /**
     * What went to the stitcher, tallied in pixels as its holders round each sprite and split into this mod's
     * sprites and everything else.
     *
     * <p>
     * Rounded to slots once over each half rather than once per sprite, because below four mipmap levels an edge
     * need not be a multiple of sixteen, and rounding every sprite up would report more than was stitched.
     */
    public static final class Stitched {

        private final int levels;

        private int ownSprites;

        private int otherSprites;

        private long ownPixels;

        private long otherPixels;

        public Stitched(int mipmapLevels) {
            this.levels = Math.max(0, Math.min(15, mipmapLevels));
        }

        /**
         * One sprite as it goes to the stitcher. A sprite with no size on either side was never loaded, and adds
         * nothing.
         */
        public void add(boolean own, int width, int height) {
            if (width <= 0 || height <= 0) return;
            long pixels = (long) mipmapDimension(width, levels) * mipmapDimension(height, levels);
            if (own) {
                ownSprites++;
                ownPixels += pixels;
            } else {
                otherSprites++;
                otherPixels += pixels;
            }
        }

        /** The mipmap levels every edge was rounded at, held between none and fifteen. */
        public int levels() {
            return levels;
        }

        /** How many of this mod's sprites were counted. */
        public int ownSprites() {
            return ownSprites;
        }

        /** How many other sprites were counted. */
        public int otherSprites() {
            return otherSprites;
        }

        /** The slots this mod's sprites came to, rounded up once over the whole. */
        public long ownCells() {
            return (ownPixels + CELL * CELL - 1) / (CELL * CELL);
        }

        /** The slots everything else came to, rounded up once over the whole. */
        public long otherCells() {
            return (otherPixels + CELL * CELL - 1) / (CELL * CELL);
        }
    }

    /**
     * Slots one sprite of this edge takes: the edge rounded up to whole slots, squared, never below one.
     *
     * <p>
     * Rounded up rather than down, because the stitcher rounds every edge up to a multiple of its mipmap
     * stride, which is sixteen at the four levels the game ships with: a twenty-four pixel sprite sits in a
     * thirty-two pixel square, and pricing it at one slot let a pack drawn that way plan four times the room
     * it had.
     */
    public static int cellsFor(int edge) {
        int across = (Math.max(1, edge) + CELL - 1) / CELL;
        return across * across;
    }

    /** The cells a holder takes after a per-side atlas gutter has been reserved. */
    public static int cellsForHolder(int edge, int gutter) {
        return cellsFor(edge + 2 * Math.max(0, gutter));
    }

    /**
     * Slots one sprite of this edge takes, grown by sixteen pixels a side's length where anisotropic filtering
     * widens it.
     */
    public static int spriteCells(int edge, boolean grown) {
        return cellsFor(edge + (grown ? ANISOTROPIC_GROWTH : 0));
    }

    /**
     * An edge as the stitcher's holder rounds it: up to a multiple of two to the power of the mipmap levels. The
     * sum Stitcher.getMipmapDimension does, with the levels held between none and fifteen so that a figure from
     * a hand-edited options file cannot shift a bit off the end.
     */
    public static int mipmapDimension(int edge, int levels) {
        if (edge <= 0) return 0;
        int stride = Math.max(0, Math.min(15, levels));
        return ((edge >> stride) + ((edge & ((1 << stride) - 1)) == 0 ? 0 : 1)) << stride;
    }

    /**
     * The width a PNG's header gives, or nought for anything that is not a PNG header, is cut short, or claims a
     * width no card could address.
     */
    public static int pngWidth(byte[] head) {
        if (head == null || head.length < HEADER_BYTES) return 0;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if ((head[i] & 0xFF) != PNG_SIGNATURE[i]) return 0;
        }
        if (head[12] != 'I' || head[13] != 'H' || head[14] != 'D' || head[15] != 'R') return 0;
        long width = ((head[16] & 0xFFL) << 24) | ((head[17] & 0xFF) << 16)
            | ((head[18] & 0xFF) << 8)
            | (head[19] & 0xFF);
        return width >= 1 && width <= WIDEST_FILE ? (int) width : 0;
    }

    /**
     * The width of an image file, read from as little of it as will tell, or nought where nothing believable can be
     * read. Closing the stream is the caller's.
     *
     * <p>
     * A PNG's width comes from its first twenty-four bytes, and nothing past them is read, so an ordinary pack costs
     * no more than a header apiece. Anything else is asked of the image reader vanilla would load it with. Vanilla
     * loads a block texture through ImageIO, which picks its reader from the opening bytes and not from the name, so
     * JPEG, GIF or BMP data saved as a .png is stitched at its full size; given nought here it would be priced at the
     * pack's average, or at dirt and stone's edge, a thousand slots short for one 512-pixel file. The bytes already
     * read go back in front of the stream, so that reader sees the file from its first byte.
     *
     * <p>
     * Here rather than beside the resource manager that opens the file, because nothing in it needs the game, which
     * lets a test hand it a file of each format and hold it to the width.
     */
    public static int fileWidth(InputStream stream) throws IOException {
        if (stream == null) return 0;
        byte[] head = new byte[HEADER_BYTES];
        int got = 0;
        while (got < head.length) {
            int more = stream.read(head, got, head.length - got);
            if (more < 0) break;
            got += more;
        }
        int width = got < head.length ? 0 : pngWidth(head);
        if (width > 0 || got <= 0) return width;
        return imageWidth(new SequenceInputStream(new ByteArrayInputStream(head, 0, got), stream));
    }

    /**
     * The width the image reader vanilla would load a file with finds in its header, or nought where no reader
     * recognises the file, which is where vanilla finds nothing to stitch either.
     *
     * <p>
     * The reader is chosen as ImageIO.read chooses it, the first that says it can decode the opening bytes, and
     * asked only for the width, which the readers the runtime ships answer without decoding a pixel. A PNG whose
     * header was refused above comes here too, and is still refused: its reader either fails on the damaged header
     * or gives a width past the widest file believed. The bytes are cached in memory rather than through
     * ImageIO.createImageInputStream, which may open a temporary file for every texture handed to it. An image
     * reader can be code another mod put on the classpath, so one whose classes cannot load is taken as a file that
     * cannot be read rather than let out into the stitch.
     */
    private static int imageWidth(InputStream whole) {
        ImageInputStream input = null;
        ImageReader reader = null;
        try {
            input = new MemoryCacheImageInputStream(whole);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return 0;
            reader = readers.next();
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            return width >= 1 && width <= WIDEST_FILE ? width : 0;
        } catch (IOException unreadable) {
            return 0;
        } catch (RuntimeException broken) {
            return 0;
        } catch (LinkageError unloadable) {
            return 0;
        } finally {
            if (reader != null) {
                try {
                    reader.dispose();
                } catch (RuntimeException ignored) {
                    // The width is decided either way; a reader that cannot tidy up is the collector's.
                } catch (LinkageError ignored) {
                    // Nor is one whose tidying reaches a class that will not load let out into the stitch.
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // Closes only the cache; the resource's own stream is closed by the caller.
                }
            }
        }
    }

    /**
     * The side of the square the atlas is planned to, in pixels.
     *
     * <p>
     * Capped at an 8192 square whatever the driver says it could address, and that cap is the
     * load-bearing half rather than a rounding. A card reporting sixteen thousand will let the
     * stitcher build a texture of most of a gigabyte, and there is no way to find out that it could
     * not hold it: the allocation goes through {@code glTexImage2D} with no error check, so a
     * refusal is returned into nothing and every sample from the atlas comes back black - the whole
     * atlas, every block in the game, with not a word in the log. This mod does not ask for that
     * texture - unless the player has said, in client.largerAtlas, that their card can hold one: then
     * a card reporting sixteen thousand is planned to that, and no further whatever it reports. Xep's
     * choice on 2026-10-08, off by default, for a pack whose faces will not fit an 8192 square at the
     * gradations asked for - Chisel for 1.12.2 drew every ramp at 62 of 80.
     *
     * <p>
     * A card whose probe failed reports minus one, and is planned as 8192 whatever the setting says.
     */
    public static int side(int glMaximum) {
        return glMaximum <= 0 ? LARGEST_SIDE : Math.min(glMaximum, ceiling);
    }

    /**
     * The most sprites a renderer can index that keeps one row of a texture for every sprite, as many rows as the count
     * rounded up to a power of two (1.0.0, canvas-sprites): the count rounded up is within the card exactly when the
     * count is within the largest power of two the card addresses. A card whose probe failed is taken as 8192, the
     * square the atlas is planned to. Canvas for 1.16.5 keeps such a texture; see {@link Measure#spriteRoom}.
     */
    public static int powerOfTwoRows(int glMaximum) {
        return Integer.highestOneBit(glMaximum > 0 ? glMaximum : LARGEST_SIDE);
    }

    /** Lets the atlas be planned past an 8192 square, to {@link #LARGER_SIDE}, or holds it there again. */
    public static void allowLarger(boolean larger) {
        ceiling = larger ? LARGER_SIDE : LARGEST_SIDE;
    }

    /** Slots in the square the atlas is planned to. */
    public static long squareCells(int glMaximum) {
        long edge = side(glMaximum);
        return edge * edge / ((long) CELL * CELL);
    }

    /**
     * The slots the rest of the pack is priced at.
     *
     * <p>
     * Measured, it is the textures whose files could be read, at the size each says it is, and every one that
     * could not at the average of those, rounded up once over the whole. Those that could not are a sprite with a
     * loader of its own and no file at its name, stitched at whatever size its loader chooses, and a file that is
     * missing or that no image reader recognises, which is not stitched at all, so the average is about right for
     * the first and errs high for the second. Pricing them at dirt and stone's edge instead would say nothing about
     * a modded sprite, and at nought would under-price every custom loader. A sprite with a loader of its own that
     * does have a file at its name is priced from that file like any other, whatever size its loader then stitches,
     * and so is image data of another format saved under a .png name, which vanilla decodes and stitches in full.
     * Where not one file could be read the resource manager itself is failing, and dirt and stone's edge is
     * the only figure left. When nothing measured the pack, half the square is taken as spoken for. That is a
     * guess and a cautious one: the most hopeful figure, an empty atlas, is what let a stitch whose measure went
     * missing plan straight past a room it had never seen.
     */
    public static long packCells(Measure measure, int wearEdge) {
        if (!measure.taken) return measure.squareCells() / UNMEASURED_SHARE;
        long sprites = Math.max(0, measure.packSprites);
        long read = Math.max(0, measure.packRead);
        if (read == 0L) return sprites * spriteCells(wearEdge + 2 * measure.holderGutter, measure.anisotropic);
        long readCells = Math.max(0L, measure.packReadCells);
        long unread = measure.packUnread();
        // The average times the unread, rounded up, worked as whole slots a texture and a remainder, so that
        // no product along the way is larger than the figure it comes to, as readCells times unread could be.
        long whole = readCells / read;
        long part = readCells % read;
        return readCells + whole * unread + (part * unread + read - 1L) / read;
    }

    /**
     * How much of the block atlas is left once the rest of the pack has had its share.
     *
     * <p>
     * A sixteenth is held back for the stitcher, which packs onto shelves rather than optimally and will not reach
     * the last few per cent of an atlas with sprites of mixed sizes.
     */
    public static long room(Measure measure, int wearEdge) {
        long cells = measure.squareCells();
        return Math.max(0L, cells - cells / RESERVE_SHARE - packCells(measure, wearEdge));
    }

    /**
     * How many gradations the room pays for once the slots every gradation shares are out of it, and none
     * when those alone fill it. Unbounded when a gradation costs nothing.
     */
    /**
     * Past the figure every stage of every glow pays for, the gradations whose glow overlays' slots at the stages that
     * can light still fit the room, up to those asked (GlowRoomTest). The slots grow with the gradations, so the first
     * that does not fit ends it.
     */
    static long affordableLit(Wish wish, int considered, int turns, long tierSlots, long room, long sideCells,
        long linear) {
        return affordableLit(wish, considered, turns, tierSlots, room, sideCells, linear, false);
    }

    /** The same in the plan's unit: exact pixels where {@code pixels}, else slots. */
    static long affordableLit(Wish wish, int considered, int turns, long tierUnits, long room, long sideUnits,
        long linear, boolean pixels) {
        if (linear >= wish.gradations) return linear;
        long best = linear;
        for (int g = (int) Math.max(1L, linear + 1L); g <= wish.gradations; g++) {
            long units = tierUnits * g + sideUnits - wish.fallbackUnlitGlowUnits(g, pixels) * turns;
            for (int i = 0; i < considered; i++) {
                units += wish.surfaceSetUnits(i, g, pixels) * wish.surfaceSetTurns(i, turns);
            }
            if (units > room) break;
            best = g;
        }
        return best;
    }

    /**
     * The pixels this mod's holders may take where the measure names a measured stitcher (0.9.229, glow-pixels): its
     * share of the square, less the pack's own holders at their slots' pixels. Nothing is held back besides, because
     * the share is the margin, set below the fullest pack that stitcher was replayed at.
     */
    public static long pixelRoom(Measure measure, int wearEdge) {
        long cell = (long) CELL * CELL;
        long share = measure.squareCells() * cell * measure.fillPerMille / 1000L;
        return Math.max(0L, share - packCells(measure, wearEdge) * cell);
    }

    public static long affordable(long room, long fixedCells, long cellsPerGradation) {
        if (cellsPerGradation <= 0L) return Long.MAX_VALUE;
        return room > fixedCells ? (room - fixedCells) / cellsPerGradation : 0L;
    }

    /**
     * The most pictures a run can be drawn with and still fit, which is not always what was asked.
     *
     * <p>
     * Decided before a single sprite is planned, and that is what matters rather than the number.
     * The budget used to be spent on whichever blocks the planner happened to reach first: it
     * planned until it ran out and left everything after that wearing its family's generic art, down
     * a list sorted by registry name. That is the wrong thing to give up when the reason the budget
     * ran out is that somebody left the drawn count high, because a block wearing as the wrong
     * material is far more visible than a coarser ramp on the right one.
     *
     * <p>
     * Sixteen is the floor because that is one picture per gradation a record can hold, and below it
     * consecutive gradations share a picture again, which is the fault the whole of this was written to end.
     * The floor holds even where the room will not, and what gives then is which faces keep their own pixels:
     * the plan stops where the room runs out, and the faces last in the walk's order, which offers natural ground
     * its places first ({@link #naturalFirst}), wear their family's generic art. Nor does it ever lift a count above
     * what was asked. Forge clamps client.wearGradations to
     * sixteen or more when it reads the config file, so a count below sixteen arrives here only from a
     * hand-edited presets.remembered quality string, which Presets writes into the setting without that range.
     */
    public static int gradations(int wanted, int floor, long affordable) {
        wanted = Math.max(1, wanted);
        floor = Math.max(1, floor);
        if (affordable >= wanted) return wanted;
        return (int) Math.max(Math.min(floor, wanted), affordable); // affordable < wanted here
    }

    /** One part in this many of the gradations asked may be trimmed to keep a rotation. */
    public static final int TRIM_SHARE = 10;

    /** The most pictures a ramp of this many gradations may lose to a trim. */
    public static int maxTrim(int asked) {
        return Math.max(0, asked) / TRIM_SHARE;
    }

    /**
     * For each of {@code steps} counted stages, the picture it draws, given a ramp asked at {@code asked} gradations
     * and registered with {@code pictures}.
     *
     * <p>
     * Where nothing was trimmed this is the proportional map the lookup always used: stage times the gradations over
     * the steps. Where up to a tenth of the asked gradations were trimmed to keep a rotation (Xep, 2026-11-15: "up to
     * 8 of 80 stages would look the same as another stage"), the stages are all still there, and a trimmed gradation
     * draws the picture of the one before it, which costs no sprite. The twins are every other gradation across the
     * middle of the ramp, spread rather than clustered: the first gradations, where wear first shows, and the last,
     * the fully worn ones, stay distinct. A trim past the tenth is not a trim, and the map is the proportional one of
     * the pictures there are.
     */
    public static int[] pictureMap(int steps, int asked, int pictures) {
        int stages = Math.max(0, steps);
        int[] map = new int[stages];
        int wanted = Math.max(1, asked);
        int kept = Math.max(1, Math.min(pictures, wanted));
        int dropped = wanted - kept;
        if (dropped > maxTrim(wanted)) {
            wanted = kept;
            dropped = 0;
        }
        // The first twin, and every other gradation after it: 33, 35, ... 47 of 80 when eight are trimmed.
        int first = (wanted - 2 * dropped) / 2 + 1;
        for (int stage = 0; stage < stages; stage++) {
            int gradation = Math.min((int) ((long) stage * wanted / stages), wanted - 1);
            int before = dropped == 0 ? 0 : Math.max(0, Math.min(dropped, (gradation - first + 1) / 2));
            boolean twin = dropped > 0 && gradation >= first
                && gradation <= first + 2 * (dropped - 1)
                && (gradation - first) % 2 == 0;
            map[stage] = twin ? gradation - 1 - before : gradation - before;
        }
        return map;
    }

    /** The picture each counted stage draws in this plan. */
    public int[] pictureMap(int steps) {
        return pictureMap(steps, asked, gradations);
    }

    /** The measure this plan was made from. */
    public final Measure measure;

    /** The wish this plan was made from. */
    public final Wish wish;

    /** The side of the square planned to, in pixels. */
    public final int side;

    /** Slots in that square. */
    public final long squareCells;

    /** The slots held back for the stitcher. */
    public final long reserveCells;

    /** The slots the rest of the pack is priced at. */
    public final long packCells;

    /** The slots left once the reserve and the pack have had theirs. */
    public final long room;

    /** How many recorded surface sets fell within surfaces.maxTexturedSurfaces and were priced. */
    public final int surfaceSetsConsidered;

    /**
     * Appearances priced for every gradation: the family fallbacks, every set within the surface ceiling and
     * the grass-side fringe.
     */
    public final long pricedAppearances;

    /** The slots those take for one gradation at one rotation. */
    public final long pricedCells;

    /** The slots those take for one gradation at every rotation. */
    public final long cellsPerGradation;

    /** The slots the side sprites take, once. */
    public final long sideCells;

    /** The slots everything priced would take at the gradations asked for, the side sprites included. */
    public final long cellsWanted;

    /** How many gradations of everything priced the room pays for. */
    public final long affordable;

    /** The gradations per run this plan draws: the distinct pictures a ramp is registered with. */
    public final int gradations;

    /**
     * The gradations the ramp was asked to have. Above {@link #gradations} only where a small trim was taken to keep
     * a rotation, and then the ramp still steps through every stage it was asked for; see {@link #pictureMap}.
     */
    public final int asked;

    /**
     * Sprites planned whatever else gives way: the fallbacks and the fringe at every gradation and rotation,
     * and the side sprites.
     */
    public final long mandatorySprites;

    /** The slots the mandatory sprites take. */
    public final long mandatoryCells;

    /** How many of the recorded surface sets were kept, counted from the first. */
    public final int surfaceSetsKept;

    /** Appearances across the surface sets kept. */
    public final long surfaceAppearancesKept;

    /** Slots across the surface sets kept, for one gradation at one rotation. */
    public final long surfaceCellsKept;

    /** Sprites planned in all. */
    public final long plannedSprites;

    /** Slots planned in all, within the room unless the mandatory sprites alone are not. */
    public final long plannedCells;

    /** The limit the first set to give way met, or NONE when none did. */
    public final Limit stoppedBy;

    private AtlasPlan(Measure measure, Wish wish) {
        this(measure, wish, wish.gradations);
    }

    private AtlasPlan(Measure measure, Wish wish, int asked) {
        this.measure = measure;
        this.wish = wish;
        // Exact pixels where the measure names a measured stitcher and the wish carries them (0.9.229, glow-pixels):
        // every sum below is then in pixels, rounded to slots once over each, and the room is that stitcher's share of
        // the square. Otherwise every sum is in slots, each set's rounded up on its own, as before.
        this.pixels = measure.fillPerMille > 0 && wish.pricedInPixels();
        long unit = this.pixels ? (long) CELL * CELL : 1L;
        int turns = Math.max(1, wish.rotations);
        long fallbacks = Math.max(0, wish.fallbackAppearances);
        long tierCells = Math.max(0L, wish.fallbackCells) + Math.max(0, wish.fringeCells);
        long tierUnits = this.pixels ? wish.fallbackPixels + wish.fringePixels : tierCells;
        long sides = Math.max(0, wish.sideSprites);
        this.side = measure.stitchSide;
        this.squareCells = measure.squareCells();
        // Priced in pixels, what is held back is the rest of the square past the stitcher's share, so the square less
        // the reserve is that share and planForBorderBudget's test holds the plan, rounded up once, and the pack to it.
        this.reserveCells = this.pixels ? this.squareCells - this.squareCells * measure.fillPerMille / 1000L
            : this.squareCells / RESERVE_SHARE;
        this.packCells = packCells(measure, wish.wearEdge);
        this.roomUnits = this.pixels ? pixelRoom(measure, wish.wearEdge) : room(measure, wish.wearEdge);
        this.room = this.roomUnits / unit;
        this.sideCells = Math.max(0L, wish.sideCells);
        long sideUnits = this.pixels ? wish.sidePixels : this.sideCells;
        int recorded = wish.surfaceSetCount();
        int considered = Math.min(recorded, Math.max(0, wish.surfaceCeiling));
        long candidates = 0L;
        long candidateCells = 0L;
        // A gradation of every priced sprite at every pattern it is drawn with: all the rotations, save what
        // client.wearTurns' turn only draws once (Wish.surfaceSetOne, Wish.fallbackOneCells).
        long candidateUnits = 0L;
        for (int i = 0; i < considered; i++) {
            candidates += Math.max(0, wish.surfaceSet(i));
            candidateCells += wish.surfaceSetCells(i);
            candidateUnits += (this.pixels ? wish.surfaceSetPixels(i) : wish.surfaceSetCells(i))
                * wish.surfaceSetTurns(i, turns);
        }
        long oneFallbacks = wish.fallbackOneAppearances;
        long oneTierUnits = this.pixels ? wish.fallbackOnePixels : wish.fallbackOneCells;
        long tierPerGradation = (tierUnits - oneTierUnits) * turns + oneTierUnits;
        long unitsPerGradation = tierPerGradation + candidateUnits;
        this.surfaceSetsConsidered = considered;
        this.pricedAppearances = fallbacks + candidates + 1L; // + the grass-side fringe
        this.pricedCells = tierCells + candidateCells;
        this.cellsPerGradation = slots(unitsPerGradation, unit);
        this.affordable = affordableLit(
            wish,
            considered,
            turns,
            tierPerGradation,
            this.roomUnits,
            sideUnits,
            affordable(this.roomUnits, sideUnits, unitsPerGradation),
            this.pixels);
        this.gradations = gradations(wish.gradations, wish.floor, this.affordable);
        this.asked = Math.max(this.gradations, asked);
        this.cellsWanted = slots(unitsPerGradation * Math.max(1, wish.gradations) + sideUnits, unit);
        // A fallback's glow overlays are drawn at every rotation and counted only where they can light
        // (GlowStagesTest).
        long unlitFallbackGlows = (long) wish.fallbackGlows * this.gradations
            - wish.fallbackGlowSprites(this.gradations);
        this.mandatorySprites = ((fallbacks - oneFallbacks + 1L) * turns + oneFallbacks) * this.gradations + sides
            - unlitFallbackGlows * turns;
        this.mandatoryUnits = tierPerGradation * this.gradations + sideUnits
            - wish.fallbackUnlitGlowUnits(this.gradations, this.pixels) * turns;
        this.mandatoryCells = slots(this.mandatoryUnits, unit);

        long sprites = this.mandatorySprites;
        long cells = this.mandatoryUnits;
        int kept = 0;
        long keptAppearances = 0L;
        long keptCells = 0L;
        Limit stopped = Limit.NONE;
        for (int i = 0; i < recorded; i++) {
            if (i >= considered) {
                stopped = Limit.CEILING;
                break;
            }
            long appearances = Math.max(0, wish.surfaceSet(i));
            long setCells = wish.surfaceSetCells(i);
            int setTurns = wish.surfaceSetTurns(i, turns);
            // Its glow overlays only at the stages where they can light (GlowStagesTest).
            long glows = wish.surfaceSetGlows(i);
            long setSprites = ((appearances - glows) * this.gradations + wish.surfaceSetGlowSprites(i, this.gradations))
                * setTurns;
            // And its glow overlays' room only there too (GlowRoomTest), in the plan's unit.
            long setSlots = wish.surfaceSetUnits(i, this.gradations, this.pixels) * setTurns;
            if (cells + setSlots > this.roomUnits) {
                stopped = Limit.ROOM;
                break;
            }
            if (sprites + setSprites > Math.max(0, wish.spriteCeiling)) {
                stopped = Limit.SPRITES;
                break;
            }
            // What the renderer can index (Measure.spriteRoom), asked after the player's own ceiling so that a
            // ceiling the player set is the one named where both would stop the same set.
            if (sprites + setSprites > measure.ownSpriteRoom()) {
                stopped = Limit.RENDERER;
                break;
            }
            sprites += setSprites;
            cells += setSlots;
            kept++;
            keptAppearances += appearances;
            keptCells += setCells;
        }
        this.surfaceSetsKept = kept;
        this.surfaceAppearancesKept = keptAppearances;
        this.surfaceCellsKept = keptCells;
        this.plannedSprites = sprites;
        this.plannedCells = slots(cells, unit);
        this.stoppedBy = stopped;
    }

    /** Whole slots for a sum in the plan's unit, rounded up once over the sum. */
    private static long slots(long units, long unit) {
        return unit <= 1L ? units : (Math.max(0L, units) + unit - 1L) / unit;
    }

    /**
     * Whether this plan priced this mod's holders in exact pixels against a measured stitcher's share of the square
     * (0.9.229, glow-pixels), rather than in whole slots against the square less a sixteenth.
     */
    public final boolean pixels;

    /** The room and the mandatory tier in the plan's unit: pixels where {@link #pixels}, else slots. */
    private final long roomUnits;

    private final long mandatoryUnits;

    /**
     * Everything the stitch will register, priced in slots at the size each will be stitched at and fitted to the
     * room.
     *
     * <p>
     * The family fallbacks, the grass fringe and the side walls are mandatory: they are planned in full at
     * whatever gradation count is chosen, because every lookup falls through to them and truncating them leaves
     * ground with nothing to draw. The surface sets come after, in the order the walk makes them, natural ground
     * first ({@link #naturalFirst}). The gradation
     * count is chosen first, from every set within surfaces.maxTexturedSurfaces, so that when there is not room it
     * is the ramp that coarsens first; only once it is held at the floor, or at a lower count that was asked for, do
     * the faces last in that order give way. The sprite ceiling cuts sets and never gradations. The limits
     * are asked in the order ceiling, room, sprites, and the one reported is the first a set met.
     *
     * <p>
     * The room stops a set only once the ramp is held. Where the plan draws no more gradations than the room pays
     * for, those gradations of every priced sprite and the side walls come to no more than the room, and that
     * total is exactly the mandatory tier plus every set within the surface ceiling, so every one of them fits.
     */
    public static AtlasPlan plan(Measure measure, Wish wish) {
        return new AtlasPlan(measure, wish);
    }

    /**
     * Choose what gives way when the wear art does not fit the atlas, before any wear sprite is registered, in the
     * order Xep set (2026-11-14 and 2026-11-15: "We should first drop the extra rotations rather than drop blocks /
     * wear levels for blocks", and "Fewer stages, then colors"): before a rotation is dropped, up to a tenth of the
     * gradations asked may be trimmed (a trimmed gradation draws its neighbour's picture and the stages stay as they
     * were, see {@link #pictureMap}); then rotations, four down to one, each with that trim; then gradations at one
     * rotation, really fewer, down to one; never a surface's own pixels while any of that can still give; and only as
     * the last resort one shared look per family, at one gradation and one rotation.
     *
     * <p>
     * A candidate is accepted when it keeps every admitted face at the gradations asked for and holds a sixteenth of
     * the square for packing slack. A pack that fits at the rotations and gradations asked for comes out as
     * {@link #plan} makes it. Angelica's gutter, which makes every holder larger, is in the measure and the wish, and
     * is the one difference between the paths: when even the shared tier does not fit a gutter-bordered atlas this
     * returns null so that the wear is not registered at all, where a plain atlas gets the shared tier anyway, as it
     * always has, because its mandatory sprites overrunning is warned about and not fatal.
     *
     * <p>
     * Where the measure bounds the atlas's sprites ({@link Measure#spriteRoom}, Canvas for 1.16.5), a candidate is
     * accepted only within that count as well, so the same order gives way to it; and when even the shared tier is past
     * it this returns null on any atlas, because a renderer that cannot index the atlas draws every block wrongly
     * (1.0.0, canvas-sprites).
     *
     * <p>
     * Before 0.9.229 an atlas with no gutter, which is every edition but 1.7.10 with Angelica, was planned by
     * {@link #plan} alone: that coarsens the ramp for room down to its floor and never lowers a rotation, so a very
     * large pack lost wear stages before it lost a rotation, the reverse of the order above.
     */
    public static AtlasPlan planForBorderBudget(Measure measure, Wish wish) {
        AtlasPlan candidate = null;
        int asked = Math.max(1, wish.gradations);
        int trimmable = maxTrim(asked);
        for (int turns = Math.max(1, wish.rotations); turns >= 1; turns--) {
            for (int trim = 0; trim <= trimmable; trim++) {
                int pictures = asked - trim;
                candidate = new AtlasPlan(
                    measure,
                    wish.withSettings(pictures, turns, wish.surfaceCeiling, wish.spriteCeiling),
                    asked);
                if (withinHolderBudget(candidate, pictures)) return candidate;
            }
        }
        for (int gradations = Math.max(1, wish.gradations) - 1; gradations >= 1; gradations--) {
            candidate = plan(measure, wish.withSettings(gradations, 1, wish.surfaceCeiling, wish.spriteCeiling));
            if (withinHolderBudget(candidate, gradations)) return candidate;
        }
        candidate = plan(measure, wish.withSettings(1, 1, 0, wish.spriteCeiling));
        if (withinHolderBudget(candidate, 1)) return candidate;
        // Past what the renderer can index nothing is registered either (1.0.0, canvas-sprites): there, unlike a plain
        // atlas whose mandatory tier ran over its room, every block in the game would be drawn wrongly, not only worn
        // ground.
        return measure.holderGutter == 0 && !candidate.pastSpriteRoom() ? candidate : null;
    }

    private static boolean withinHolderBudget(AtlasPlan candidate, int requestedGradations) {
        return candidate.surfaceSetsKept == candidate.surfaceSetsConsidered
            && candidate.gradations == requestedGradations
            && candidate.plannedCells + candidate.packCells <= candidate.squareCells - candidate.reserveCells
            && !candidate.pastSpriteRoom();
    }

    /**
     * True where this plan's sprites, the mandatory tier's included, come to more than the renderer can index once the
     * rest of the pack has its own ({@link Measure#ownSpriteRoom}); never where nothing bounds the count.
     */
    public boolean pastSpriteRoom() {
        return plannedSprites > measure.ownSpriteRoom();
    }

    /**
     * True where the room pays for fewer gradations than this plan draws, which only the floor brings about, or a
     * count asked for below it.
     */
    public boolean floorHeld() {
        return affordable < gradations;
    }

    /**
     * True where the fallbacks, the fringe and the side sprites alone want more slots than the room has, so
     * that no surface set is kept and the stitch may not fit at all.
     */
    public boolean mandatoryOverruns() {
        return mandatoryUnits > roomUnits;
    }

    /** How many of the recorded surface sets gave way, to whichever limit. */
    public int surfacesDropped() {
        return wish.surfaceSetCount() - surfaceSetsKept;
    }

    /**
     * Slots this mod's own sprites took past what this plan priced them at, or none. A description of which half ran
     * over, for the log to quote; it decides nothing, because whether the stitch fits is judged on the whole.
     */
    public long ownPast(Stitched stitched) {
        return Math.max(0L, stitched.ownCells() - plannedCells);
    }

    /**
     * Slots everything else took past what the pack was priced at, or none. Like ownPast, a description and not a
     * test.
     */
    public long restPast(Stitched stitched) {
        return Math.max(0L, stitched.otherCells() - packCells);
    }

    /**
     * Whether what went to the stitcher, taken together, has begun to spend the sixteenth held back: more slots in
     * all than the square less the reserve, whichever half ran past its price.
     *
     * <p>
     * The whole is judged rather than each half against its price, because atlas slots are interchangeable and the
     * stitcher never tells one mod's sprites from another's. Slots this mod's sprites came in under their price, and
     * room the plan left unused, take an overrun on the other half as readily as the reserve would. The first form
     * of this check added each half's excess, never below none, and set the sum against the reserve, so icons other
     * mods register after the measure could run the pack twenty thousand slots past its price and bring a warning
     * that the atlas might not fit while more than twenty-five thousand slots stood free before the reserve was
     * touched.
     *
     * <p>
     * Past this line the stitcher is working in the slack its shelf packing was left, and may still fit the square;
     * past the square itself it cannot, and reserveSpent then comes to more than the reserve holds.
     */
    public boolean overdrawn(Stitched stitched) {
        return stitched.ownCells() + stitched.otherCells() > squareCells - reserveCells;
    }

    /**
     * How many of the slots held back for the stitcher what went to it took, both halves together, or none while the
     * whole stays within the square less the reserve.
     *
     * <p>
     * It comes to more than reserveCells exactly when the whole has passed the square, and by as many slots as it
     * passed it, so the figure is not capped at the reserve: a log that caps it would hide how far past the square a
     * stitch went.
     */
    public long reserveSpent(Stitched stitched) {
        return Math.max(0L, stitched.ownCells() + stitched.otherCells() - (squareCells - reserveCells));
    }

    /**
     * The plan the same measure and prices would make at these four settings.
     *
     * <p>
     * Asked by the log rather than by the planner, so that the advice a warning gives is a figure worked out and
     * not a rule argued: whether a changed setting brings faces back depends on where the room, the ceilings and
     * the rounding of every sum happen to fall, and the only way to be sure is to do the sums.
     */
    public AtlasPlan withSettings(int gradations, int rotations, int surfaceCeiling, int spriteCeiling) {
        return plan(measure, wish.withSettings(gradations, rotations, surfaceCeiling, spriteCeiling));
    }

    /** The plan at these gradations and rotations, both ceilings as they are. */
    public AtlasPlan planAt(int gradations, int rotations) {
        return withSettings(gradations, rotations, wish.surfaceCeiling, wish.spriteCeiling);
    }

    /**
     * Of the rotation counts below this plan's, the plan that keeps the most surface sets, taking the higher count
     * where two keep as many, or null at one rotation.
     *
     * <p>
     * Not simply the plan at one rotation. Where the room has set the ramp, a rotation given up is spent on finer
     * gradations first, and at one rotation the ramp can grow past what the sprite ceiling holds, so fewer
     * rotations need not keep more.
     */
    public AtlasPlan fewerRotations() {
        AtlasPlan best = null;
        for (int turns = Math.max(1, wish.rotations) - 1; turns >= 1; turns--) {
            AtlasPlan candidate = planAt(wish.gradations, turns);
            if (best == null || candidate.surfaceSetsKept > best.surfaceSetsKept) best = candidate;
        }
        return best;
    }

    /**
     * A gate that grants exactly the sets this plan kept, in order, and refuses everything after its first
     * refusal.
     *
     * <p>
     * It counts appearances and slots as well as sets, so a walk that answers differently the second time comes
     * out with fewer sets than were priced, never more slots.
     */
    public SetGate gate() {
        return new Gate(surfaceSetsKept, surfaceAppearancesKept, surfaceCellsKept);
    }

    /**
     * What the stitcher's own search is spared, without moving a single sprite.
     *
     * <p>
     * The stitcher - vanilla's, the same search from 1.7.10 to 1.16.5 - places each sprite by walking its tree of
     * slots from the top, first fit, and never remembers that a part of the tree is already too full. It sorts the
     * sprites largest first, so by the time the small ones arrive every search walks every part already filled,
     * and the walk grows with the square of the number of sprites. Sprites of one size kept that tolerable; a pack
     * of mixed sizes does not. Chisel for 1.12.2, whose faces are sixteen to sixty-four pixels and whose wear is
     * drawn at each face's own size since 0.9.220, spent 165 seconds of a 211-second texture load inside that walk
     * (sampled on the client thread, 2026-10-08), where 1.7.10's Chisel, all sixteen pixels, stitched more
     * sprites in seconds.
     *
     * <p>
     * A slot that has refused a sprite has no free room at least that wide and that tall for the rest of the
     * stitch, because nothing in the search ever gives room back: a slot splits only for a sprite it takes, so a
     * search that fails changes nothing, and a free slot is only ever split into smaller ones. So each slot keeps
     * the last sprite it refused, and a search that reaches it with one at least as large both ways turns back at
     * once, where vanilla's would have walked the whole of it to find the same nothing.
     *
     * <p>
     * With one exception, which is vanilla's fault and which this does not copy. Vanilla's search lets a slot already
     * split for smaller sprites take a sprite of exactly its own size, laid over them, and they are then lost from
     * the atlas: a slot holding a sprite is never looked inside again. Sorted tallest first, no sprite can arrive at
     * that size, but 1.7.10 and 1.12.2 also try a sprite that is not square on its side, and on its side it can.
     * The shortcut, which has seen that slot refuse, searches on and puts it somewhere free. So every sprite lands
     * where vanilla's search would put it, until vanilla would lay one over others; 1.16.5 turns nothing and never
     * meets it, and a pack's sprites are nearly all square. AtlasPlanTest runs vanilla's search beside the shortcut
     * and holds both halves of that, placement by placement.
     */
    public static final class Search {

        /** What a slot holds before it has refused anything: no sprite is that large. */
        public static final int NOTHING_REFUSED = Integer.MAX_VALUE;

        /**
         * Searches turned back since the last report. Counted without a lock, because it is counted at every slot
         * a search is spared and the point of all this is speed; on 1.16.5, whose atlases stitch side by side, a
         * count may be lost to a race, and the figure is for the log, not for anything that decides.
         */
        private static long spared;

        private static int largestSprites;

        private static long largestNanos;

        private Search() {}

        /** Whether a slot that refused a sprite of the first size must refuse one of the second. */
        public static boolean stillRefuses(int refusedWidth, int refusedHeight, int width, int height) {
            return width >= refusedWidth && height >= refusedHeight;
        }

        /** Counts one search turned back. */
        public static void spare() {
            spared++;
        }

        /**
         * Notes a finished stitch, keeping the largest since the last report: the block atlas, which is the one
         * the report is about, is by far the largest in every edition, and on 1.16.5 the others stitch beside it.
         */
        public static synchronized void stitched(int sprites, long nanos) {
            if (sprites < largestSprites) return;
            largestSprites = sprites;
            largestNanos = nanos;
        }

        /** The largest stitch since the last call - its sprites and milliseconds - and the searches spared. */
        public static synchronized long[] take() {
            long[] report = { largestSprites, largestNanos / 1000000L, spared };
            largestSprites = 0;
            largestNanos = 0L;
            spared = 0L;
            return report;
        }
    }

    private static final class Gate implements SetGate {

        private final int sets;

        private final long appearances;

        private final long cells;

        private int granted;

        private long taken;

        private long takenCells;

        private boolean shut;

        Gate(int sets, long appearances, long cells) {
            this.sets = sets;
            this.appearances = appearances;
            this.cells = cells;
        }

        @Override
        public boolean take(int asked, int askedCells, boolean one) {
            // Granted on appearances and slots alone, as the plan kept them: a set the walk flags differently the
            // second time is the walk's own fault, and the slot count it is held to is the same either way.
            return take(asked, askedCells);
        }

        @Override
        public boolean take(int asked, int askedCells, boolean one, int glows, int[] glowLit, int glowCells) {
            // Granted as the other two are: the glows are among the appearances the plan kept.
            return take(asked, askedCells);
        }

        @Override
        public boolean take(int asked, int askedCells, boolean one, int glows, int[] glowLit, int glowCells, int pixels,
            int glowPixels) {
            // Granted on the slots the census recorded beside the pixels, so a walk that answers differently the
            // second time still comes out with fewer sets than were priced, never more.
            return take(asked, askedCells);
        }

        @Override
        public boolean take(int asked, int askedCells) {
            if (shut) return false;
            long more = Math.max(0, asked);
            long moreCells = Math.max(0, askedCells);
            if (granted >= sets || taken + more > appearances || takenCells + moreCells > cells) {
                shut = true;
                return false;
            }
            granted++;
            taken += more;
            takenCells += moreCells;
            return true;
        }

        @Override
        public boolean shut() {
            return shut;
        }
    }
}
