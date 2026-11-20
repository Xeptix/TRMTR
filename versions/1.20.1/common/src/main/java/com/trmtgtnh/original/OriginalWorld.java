package com.trmtgtnh.original;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * A chunk the original mod saved, put back into vanilla blocks with the wear it showed kept as this mod's records.
 *
 * <p>
 * The original keeps a square's wear in the block itself: {@code trmt:eroded_grass_block[stage=2]} is the third worn
 * picture of turf, and nothing else in the world says so (docs/goals/0.9.229-w5-original.md, 8.3). Without the original
 * the game cannot resolve that name and the square loads as air, and a registry remap would keep the block but drop the
 * {@code stage}. So the conversion reads the section palettes before the game does (spec 2.9): each original entry is
 * replaced by its vanilla block in place, and the positions using it are listed with the step of wear they showed, for
 * the store to turn into records once the chunk is in its level.
 *
 * <p>
 * Over a small model of a chunk's sections - a palette of names and properties and the packed indices - rather than the
 * game's tag, so the rewrite is the same file in all eight editions and its rules are tried without a game. Each
 * converting edition's adapter reads its {@code CompoundTag} into these sections, writes back any entry this changed,
 * and leaves the tag alone when nothing did. The packed indices are only read, never rewritten: an entry keeps its
 * place in the palette and only its name and properties change, so a palette may hold the same vanilla block twice
 * afterwards, which the spike (S1, point 3) must show the game decodes.
 */
public final class OriginalWorld {

    /** The original's namespace: every block of its is {@code trmt:<name>}. */
    public static final String NAMESPACE = "trmt:";

    public static final String GRASS = "trmt:eroded_grass_block";

    public static final String DIRT = "trmt:eroded_dirt";

    public static final String COARSE_DIRT = "trmt:eroded_coarse_dirt";

    public static final String SAND = "trmt:eroded_sand";

    /** 0.2 only: it had no stages, so it carries no record. */
    public static final String ROOTED_DIRT = "trmt:eroded_rooted_dirt";

    /** Squares in a section: 16 by 16 by 16, x fastest, then z, then y. */
    private static final int SQUARES = 4096;

    /** One palette entry: a block's name and its properties, as the section's palette holds them. */
    public static final class Entry {

        public String name;

        public final Map<String, String> properties;

        public Entry(String name, Map<String, String> properties) {
            this.name = name;
            this.properties = properties == null ? new LinkedHashMap<String, String>() : properties;
        }
    }

    /** One section: its height in sections, its palette and its packed indices (null or empty for one entry). */
    public static final class Section {

        public final int y;

        public final List<Entry> palette;

        public final long[] data;

        /** Which palette entries this conversion replaced, by index; the adapter writes back only these. */
        final Set<Integer> replaced = new LinkedHashSet<Integer>();

        public Section(int y, List<Entry> palette, long[] data) {
            this.y = y;
            this.palette = palette;
            this.data = data;
        }

        /** Whether the conversion replaced the entry at this index of the palette. */
        public boolean replaced(int index) {
            return replaced.contains(Integer.valueOf(index));
        }
    }

    /** One square that showed wear: where it is in the chunk, which chain it is on, and the step it had reached. */
    public static final class Square {

        /** 0 to 15 within the chunk. */
        public final int x;

        /** The world height. */
        public final int y;

        /** 0 to 15 within the chunk. */
        public final int z;

        /** The chain the record is on: grass, dirt (only with the dirt-becomes-grass switch off) or sand. */
        public final SurfaceFamily base;

        /** How far along the original's own chain the square was (spec 2.2), for {@link OriginalGradation#index}. */
        public final int step;

        Square(int x, int y, int z, SurfaceFamily base, int step) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.base = base;
            this.step = step;
        }

        @Override
        public String toString() {
            return x + "," + y + "," + z + " " + base + " " + step;
        }
    }

    /** What a conversion did. */
    public static final class Result {

        /** Nothing to convert: the chunk is to be left exactly as it was read. */
        static final Result NONE = new Result(false, new ArrayList<Square>(), new LinkedHashSet<String>());

        /** Whether any palette entry was replaced; when not, the chunk's tag is left untouched. */
        public final boolean changed;

        /** The squares that showed wear, for the store. */
        public final List<Square> squares;

        /** Names in the original's namespace this does not know, left as they were, for the log to name once. */
        public final Set<String> unknown;

        Result(boolean changed, List<Square> squares, Set<String> unknown) {
            this.changed = changed;
            this.squares = Collections.unmodifiableList(squares);
            this.unknown = Collections.unmodifiableSet(unknown);
        }
    }

    private OriginalWorld() {}

    /** Whether any section's palette names a block of the original's: the fast path's one question. */
    public static boolean mentionsOriginal(List<Section> sections) {
        for (Section section : sections) {
            for (Entry entry : section.palette) {
                if (entry.name != null && entry.name.startsWith(NAMESPACE)) return true;
            }
        }
        return false;
    }

    /**
     * Replaces every original entry in these sections by its vanilla block and lists the worn squares.
     *
     * <p>
     * Worn grass becomes a grass block. Worn dirt and the coarse end become a grass block too while
     * {@code dirtBecomesGrass} is on (the shipped choice, spec question 3: the original heals every worn dirt into
     * grass, and a block state cannot say whether a square began as grass or as dirt), carrying a grass record past the
     * turf; with it off they become dirt on the dirt chain, and the bare first step, which looks exactly like dirt,
     * carries no record. Worn sand becomes sand, losing any water it held, since sand cannot hold it. A chunk with no
     * name of the original's comes back as {@link Result#NONE} with nothing touched, and so does a chunk already
     * converted, which is what makes reading one twice before it is saved harmless.
     */
    public static Result convert(List<Section> sections, boolean dirtBecomesGrass) {
        if (!mentionsOriginal(sections)) return Result.NONE;

        List<Square> squares = new ArrayList<Square>();
        Set<String> unknown = new LinkedHashSet<String>();
        boolean changed = false;
        for (Section section : sections) {
            Map<Integer, Square> marks = new LinkedHashMap<Integer, Square>();
            for (int i = 0; i < section.palette.size(); i++) {
                Entry entry = section.palette.get(i);
                if (entry.name == null || !entry.name.startsWith(NAMESPACE)) continue;
                Square mark = markOf(entry, dirtBecomesGrass);
                if (!replace(entry, dirtBecomesGrass)) {
                    unknown.add(entry.name);
                    continue;
                }
                section.replaced.add(Integer.valueOf(i));
                changed = true;
                if (mark != null) marks.put(Integer.valueOf(i), mark);
            }
            if (!marks.isEmpty()) listSquares(section, marks, squares);
        }
        return new Result(changed, squares, unknown);
    }

    /** The chain and step an original entry showed, or null when it carries no record. Read before it is replaced. */
    private static Square markOf(Entry entry, boolean dirtBecomesGrass) {
        String name = entry.name;
        if (GRASS.equals(name)) return new Square(0, 0, 0, SurfaceFamily.GRASS, stage(entry, 4));
        if (SAND.equals(name)) return new Square(0, 0, 0, SurfaceFamily.SAND, stage(entry, 4));
        if (DIRT.equals(name)) {
            int stage = stage(entry, 3);
            if (dirtBecomesGrass) return new Square(0, 0, 0, SurfaceFamily.GRASS, 5 + stage);
            return stage == 0 ? null : new Square(0, 0, 0, SurfaceFamily.DIRT, stage - 1);
        }
        if (COARSE_DIRT.equals(name)) {
            if (dirtBecomesGrass) return new Square(0, 0, 0, SurfaceFamily.GRASS, 9);
            return new Square(0, 0, 0, SurfaceFamily.DIRT, 3);
        }
        return null;
    }

    /** Puts the vanilla block in an original entry's place; false for a name of the original's this does not know. */
    private static boolean replace(Entry entry, boolean dirtBecomesGrass) {
        String name = entry.name;
        boolean dirt = DIRT.equals(name) || COARSE_DIRT.equals(name);
        if (GRASS.equals(name) || (dirt && dirtBecomesGrass)) {
            entry.name = "minecraft:grass_block";
            entry.properties.clear();
            entry.properties.put("snowy", "false");
            return true;
        }
        if (dirt) {
            entry.name = "minecraft:dirt";
            entry.properties.clear();
            return true;
        }
        if (SAND.equals(name)) {
            entry.name = "minecraft:sand";
            entry.properties.clear();
            return true;
        }
        if (ROOTED_DIRT.equals(name)) {
            entry.name = "minecraft:rooted_dirt";
            entry.properties.clear();
            return true;
        }
        return false;
    }

    /** The entry's {@code stage}, held to 0 and the block's last; 0 when it has none (0.2's blocks had none). */
    private static int stage(Entry entry, int last) {
        String value = entry.properties.get("stage");
        if (value == null) return 0;
        int stage;
        try {
            stage = Integer.parseInt(value.trim());
        } catch (NumberFormatException notOne) {
            return 0;
        }
        return stage < 0 ? 0 : (stage > last ? last : stage);
    }

    /** Lists every square of a section using one of the marked entries. */
    private static void listSquares(Section section, Map<Integer, Square> marks, List<Square> out) {
        int bits = bitsFor(section.palette.size());
        if (bits == 0) {
            Square mark = marks.get(Integer.valueOf(0));
            if (mark == null) return;
            for (int position = 0; position < SQUARES; position++) out.add(at(section, position, mark));
            return;
        }
        int perLong = 64 / bits;
        int needed = (SQUARES + perLong - 1) / perLong;
        if (section.data == null || section.data.length != needed) return;
        long mask = (1L << bits) - 1L;
        for (int position = 0; position < SQUARES; position++) {
            long word = section.data[position / perLong];
            int index = (int) ((word >>> ((position % perLong) * bits)) & mask);
            Square mark = marks.get(Integer.valueOf(index));
            if (mark != null) out.add(at(section, position, mark));
        }
    }

    private static Square at(Section section, int position, Square mark) {
        int x = position & 15;
        int z = (position >> 4) & 15;
        int y = section.y * 16 + (position >> 8);
        return new Square(x, y, z, mark.base, mark.step);
    }

    /**
     * Bits per index in a saved section: none for a palette of one, and otherwise enough for the palette but never
     * fewer than four, the smallest a block section uses. Indices do not span two longs.
     */
    static int bitsFor(int paletteSize) {
        if (paletteSize <= 1) return 0;
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        return bits < 4 ? 4 : bits;
    }
}
