package com.trmtgtnh.client.texture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Iterator;

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

    /** The largest square the atlas is planned to, whatever the card will address. */
    public static final int LARGEST_SIDE = 8192;

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
        SPRITES
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

        public Measure(boolean taken, int packSprites, int packRead, long packReadCells, int glMaximum,
            boolean anisotropic) {
            this.taken = taken;
            this.packSprites = packSprites;
            this.packRead = packRead;
            this.packReadCells = packReadCells;
            this.glMaximum = glMaximum;
            this.anisotropic = anisotropic;
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

        public Wish(int wearEdge, int gradations, int floor, int rotations, int fallbackAppearances, long fallbackCells,
            int fringeCells, int sideSprites, long sideCells, int[] surfaceSets, int[] surfaceCells, int surfaceCeiling,
            int spriteCeiling) {
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
                spriteCeiling);
        }
    }

    /** What the planner's walk asks before it makes each surface set. */
    public interface SetGate {

        /**
         * Whether a set of this many appearances, taking this many slots a gradation at one rotation, may be
         * made.
         */
        boolean take(int appearances, int cells);

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

        private int count;

        @Override
        public boolean take(int appearances, int slots) {
            if (count == sets.length) {
                sets = Arrays.copyOf(sets, count * 2);
                cells = Arrays.copyOf(cells, count * 2);
            }
            sets[count] = Math.max(0, appearances);
            cells[count] = Math.max(0, slots);
            count++;
            return true;
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
     * texture.
     *
     * <p>
     * A card whose probe failed reports minus one, and is planned as 8192.
     */
    public static int side(int glMaximum) {
        return glMaximum <= 0 ? LARGEST_SIDE : Math.min(glMaximum, LARGEST_SIDE);
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
        if (!measure.taken) return squareCells(measure.glMaximum) / UNMEASURED_SHARE;
        long sprites = Math.max(0, measure.packSprites);
        long read = Math.max(0, measure.packRead);
        if (read == 0L) return sprites * spriteCells(wearEdge, measure.anisotropic);
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
        long cells = squareCells(measure.glMaximum);
        return Math.max(0L, cells - cells / RESERVE_SHARE - packCells(measure, wearEdge));
    }

    /**
     * How many gradations the room pays for once the slots every gradation shares are out of it, and none
     * when those alone fill it. Unbounded when a gradation costs nothing.
     */
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
     * the plan stops where the room runs out, and the faces last in registry-name order wear their family's
     * generic art. Nor does it ever lift a count above what was asked. Forge clamps client.wearGradations to
     * sixteen or more when it reads the config file, so a count below sixteen arrives here only from a
     * hand-edited presets.remembered quality string, which Presets writes into the setting without that range.
     */
    public static int gradations(int wanted, int floor, long affordable) {
        wanted = Math.max(1, wanted);
        floor = Math.max(1, floor);
        if (affordable >= wanted) return wanted;
        return (int) Math.max(Math.min(floor, wanted), affordable); // affordable < wanted here
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

    /** The gradations per run this plan draws. */
    public final int gradations;

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
        this.measure = measure;
        this.wish = wish;
        int turns = Math.max(1, wish.rotations);
        long fallbacks = Math.max(0, wish.fallbackAppearances);
        long tierCells = Math.max(0L, wish.fallbackCells) + Math.max(0, wish.fringeCells);
        long sides = Math.max(0, wish.sideSprites);
        this.side = side(measure.glMaximum);
        this.squareCells = squareCells(measure.glMaximum);
        this.reserveCells = this.squareCells / RESERVE_SHARE;
        this.packCells = packCells(measure, wish.wearEdge);
        this.room = room(measure, wish.wearEdge);
        this.sideCells = Math.max(0L, wish.sideCells);
        int recorded = wish.surfaceSetCount();
        int considered = Math.min(recorded, Math.max(0, wish.surfaceCeiling));
        long candidates = 0L;
        long candidateCells = 0L;
        for (int i = 0; i < considered; i++) {
            candidates += Math.max(0, wish.surfaceSet(i));
            candidateCells += wish.surfaceSetCells(i);
        }
        this.surfaceSetsConsidered = considered;
        this.pricedAppearances = fallbacks + candidates + 1L; // + the grass-side fringe
        this.pricedCells = tierCells + candidateCells;
        this.cellsPerGradation = this.pricedCells * turns;
        this.affordable = affordable(this.room, this.sideCells, this.cellsPerGradation);
        this.gradations = gradations(wish.gradations, wish.floor, this.affordable);
        this.cellsWanted = this.cellsPerGradation * Math.max(1, wish.gradations) + this.sideCells;
        this.mandatorySprites = (fallbacks + 1L) * this.gradations * turns + sides;
        this.mandatoryCells = tierCells * this.gradations * turns + this.sideCells;

        long sprites = this.mandatorySprites;
        long cells = this.mandatoryCells;
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
            long setSprites = appearances * this.gradations * turns;
            long setSlots = setCells * this.gradations * turns;
            if (cells + setSlots > this.room) {
                stopped = Limit.ROOM;
                break;
            }
            if (sprites + setSprites > Math.max(0, wish.spriteCeiling)) {
                stopped = Limit.SPRITES;
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
        this.plannedCells = cells;
        this.stoppedBy = stopped;
    }

    /**
     * Everything the stitch will register, priced in slots at the size each will be stitched at and fitted to the
     * room.
     *
     * <p>
     * The family fallbacks, the grass fringe and the side walls are mandatory: they are planned in full at
     * whatever gradation count is chosen, because every lookup falls through to them and truncating them leaves
     * ground with nothing to draw. The surface sets come after, in the order the walk makes them. The gradation
     * count is chosen first, from every set within surfaces.maxTexturedSurfaces, so that when there is not room it
     * is the ramp that coarsens first; only once it is held at the floor, or at a lower count that was asked for, do
     * the faces last in registry-name order give way. The sprite ceiling cuts sets and never gradations. The limits
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
        return mandatoryCells > room;
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
