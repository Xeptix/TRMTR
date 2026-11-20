package com.trmtgtnh.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * What the harness's mods part lays (0.9.229 W6, docs/goals/0.9.229-w6-spec.md 2.5): the blocks of one set of other
 * mods, read from the file make_w6_instances.py writes into a rig copy, or the plain rows where no file is there.
 *
 * <pre>
 * {"set": "slabs", "blocks": [{"id": "terrain_slabs:grass_slab", "plant": "minecraft:poppy", "stage": 7}]}
 * </pre>
 *
 * A row is a block selector, the plant set on it, and the deepest index along its chain, where that is not the end. A
 * selector is {@code ns:name}, or {@code ns:name:3} for a metadata value on 1.7.10 and 1.12.2. A set with no blocks of
 * its own lays the plain rows and photographs them under its own name, so its pictures are held to the same target's
 * {@code mods-plain-<n>}.
 *
 * <p>
 * Plain Java with nothing of the game's, so it is tested on its own and reads the same in every edition.
 */
public final class ModsFixture {

    /** Where the file is, under the game's folder. */
    public static final String FILE = "config/trmtr-mods-fixture.json";

    /** The set of a run with no file, or with no name in its file. */
    public static final String PLAIN = "plain";

    /**
     * The set whose one photograph is a worn line seen from far away, drawn by Distant Horizons (0.9.229 W6 T16, R11):
     * its copy holds no blocks, and the part lays its own scene instead of the strips.
     */
    public static final String FAR = "far";

    /** The strips one photograph frames. */
    public static final int PER_FRAME = 4;

    /** The stages photographed: the first, the first past the wall's start, and the deepest. */
    public static final int STAGES = 3;

    /**
     * The chain index of the second photograph. The wall first appears at chain step three, at every setting of the
     * counted space (GhostSides.WALL_START), so this is the first index past it.
     */
    public static final int PAST_WALL = 4;

    /** A row's blocks: its selector, its block's id and metadata, and what stands on it. */
    public static final class Row {

        public final String selector;

        public final String id;

        public final int meta;

        public final String plant;

        public final String plantId;

        public final int plantMeta;

        /** The deepest index along the block's chain, or less than nought for the chain's end. */
        public final int deep;

        Row(String selector, String plant, int deep) {
            this.selector = selector;
            this.id = idOf(selector);
            this.meta = metaOf(selector);
            this.plant = plant;
            this.plantId = plant == null ? null : idOf(plant);
            this.plantMeta = plant == null ? 0 : metaOf(plant);
            this.deep = deep;
        }
    }

    /** The set's name, lower case: what its photographs are named for. */
    public final String set;

    /** The rows to lay, west to east, then frame by frame to the north. */
    public final List<Row> rows;

    private ModsFixture(String set, List<Row> rows) {
        this.set = set;
        this.rows = Collections.unmodifiableList(rows);
    }

    /** A selector's block id: {@code ns:name}, whatever follows it. */
    static String idOf(String selector) {
        String[] parts = selector.split(":");
        return parts.length < 2 ? selector : parts[0] + ":" + parts[1];
    }

    /** A selector's metadata value, or nought. */
    static int metaOf(String selector) {
        String[] parts = selector.split(":");
        if (parts.length < 3) return 0;
        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException wrong) {
            throw new IllegalArgumentException("the selector " + selector + " has no metadata value after its name");
        }
    }

    /** The plain rows: {selector, plant or null} each, this game's own spelling, given by the harness. */
    private static List<Row> plainRows(String[][] plain) {
        List<Row> rows = new ArrayList<Row>();
        for (String[] each : plain) rows.add(new Row(each[0], each[1], -1));
        return rows;
    }

    /** The fixture of a run with no file. */
    public static ModsFixture plain(String[][] plain) {
        return new ModsFixture(PLAIN, plainRows(plain));
    }

    /**
     * The fixture a file's text says. A file with no blocks is a set whose subject is the plain ground, and takes the
     * plain rows under its own name. Refuses (IllegalArgumentException) a file that is not this shape, so a fixture
     * that cannot be read is a FAIL of the part and never an empty run.
     */
    public static ModsFixture read(String text, String[][] plain) {
        JsonObject file;
        try {
            file = new Gson().fromJson(text, JsonObject.class);
        } catch (JsonParseException wrong) {
            throw new IllegalArgumentException("not JSON: " + wrong.getMessage());
        }
        if (file == null) throw new IllegalArgumentException("the file is empty");
        JsonElement named = file.get("set");
        String set = named == null ? PLAIN
            : named.getAsString()
                .toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<Row>();
        JsonElement blocks = file.get("blocks");
        if (blocks != null) {
            if (!blocks.isJsonArray()) throw new IllegalArgumentException("blocks is not a list");
            for (JsonElement each : blocks.getAsJsonArray()) rows.add(row(each));
        }
        return new ModsFixture(set, rows.isEmpty() ? plainRows(plain) : rows);
    }

    private static Row row(JsonElement each) {
        if (!each.isJsonObject() || !each.getAsJsonObject()
            .has("id")) throw new IllegalArgumentException("a row has no id: " + each);
        JsonObject row = each.getAsJsonObject();
        return new Row(
            row.get("id")
                .getAsString(),
            row.has("plant") ? row.get("plant")
                .getAsString() : null,
            row.has("stage") ? row.get("stage")
                .getAsInt() : -1);
    }

    /** The fixture of a game folder: its file, or the plain rows where it has none. */
    public static ModsFixture of(File game, String[][] plain) throws IOException {
        File file = new File(game, FILE);
        if (!file.isFile()) return plain(plain);
        return read(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), plain);
    }

    /** Whether this is the far set, whose scene is a worn line photographed from far away. */
    public boolean far() {
        return FAR.equals(set);
    }

    /**
     * The pitch, in degrees down, of a camera this far above a point on the ground and this far from it across the
     * ground: the far set's camera looks at its line's middle.
     */
    public static float pitchToward(double up, double across) {
        return (float) Math.toDegrees(Math.atan2(up, across));
    }

    /** How many photographs each stage takes. */
    public int frames() {
        return (rows.size() + PER_FRAME - 1) / PER_FRAME;
    }

    /** The column of a row in its frame. */
    public static int column(int row) {
        return row % PER_FRAME;
    }

    /** The frame a row is in. */
    public static int frame(int row) {
        return row / PER_FRAME;
    }

    /**
     * The index along a chain of this length a stage is forced to: the first stage, the first past the wall's start,
     * or the deepest; never past the chain's end.
     */
    public static int index(int stage, int length, int deep) {
        int last = Math.max(0, length - 1);
        if (stage <= 1) return Math.min(1, last);
        if (stage == 2) return Math.min(PAST_WALL, last);
        return deep < 0 ? last : Math.min(deep, last);
    }

    /**
     * A photograph's name after "trmt-mods-": {@code slabs-2}, and {@code slabs-2-p2} for a set's next frame of that
     * stage.
     */
    public String shot(int stage, int frame) {
        return set + "-" + stage + (frame == 0 ? "" : "-p" + (frame + 1));
    }

    /** What a "mods - block" line says of a row with no family: a block detection leaves alone. */
    public static final String NO_FAMILY = "none";

    /** A row's state in that line: its forced square drawn as this mod's ghost in the client. */
    public static final String GHOST = "ghost";

    /** A row's state in that line: its forced square drawn as the block it was laid as. */
    public static final String UNWORN = "unworn";

    /**
     * How long the part waits, a second at a time, for every row with a family to read its ghost in the client before
     * it says the rows: the view wait's twenty seconds (0.9.229, shift 19).
     */
    public static final int GHOST_WAIT_SECONDS = 20;

    /**
     * Whether a row's line holds (0.9.229, shift 19). A row with a family was forced along its chain, so its square must
     * read its ghost, and one read unworn is a FAIL of the part, never a line alone: under three games at once 1.16.5
     * Forge W6 Place said grass and dirt unworn and passed. A row with no family is a block detection leaves alone, as
     * the Slabs set's four terrain stone slabs are, unworn by design, and holds.
     */
    public static boolean holds(String family, boolean ghost) {
        return family == null || NO_FAMILY.equals(family) || ghost;
    }

    /** Whether the part waits another second for its rows' ghosts: some still unworn, and the bound not reached. */
    public static boolean waitsForGhosts(int unworn, int waited) {
        return unworn > 0 && waited < GHOST_WAIT_SECONDS;
    }

    /**
     * The FAIL the part says of the rows with a family still unworn at the wait's bound, or null where there are none.
     * Worded as the wear not reaching the client in time, which the rig's sweep reads as it reads a slow world
     * (sweep.WEAR_UNSENT): load beside a reading that the machine held the game back, the mod's fault on a quiet one.
     */
    public static String unwornFault(List<String> unworn) {
        if (unworn == null || unworn.isEmpty()) return null;
        return unworn.size() + " row(s) with a family still unworn in this client after " + GHOST_WAIT_SECONDS
            + " seconds waiting for their ghosts: " + String.join(", ", unworn);
    }

    // ------------------------------------------------------------------
    // The far set's verdict (0.9.229 G17: the Far cell played on ten copies)
    // ------------------------------------------------------------------

    /**
     * How long the far set waits, a second at a time, for Distant Horizons to say what it holds at the band's middle,
     * near and far. That mod's answer waits on its own read of its data without a bound; asked on the game's own thread,
     * 1.21.1 NeoForge's never came and the game sat 645 seconds after the band was laid. So it is asked on a thread of its
     * own (DistantColors.ask) and the part fails at this bound.
     */
    public static final int FAR_READING_SECONDS = 30;

    /** Whether the far set waits another second for that mod's answer: none yet, and the bound not reached. */
    public static boolean farReadingWaits(String answer, int waited) {
        return answer == null && waited < FAR_READING_SECONDS;
    }

    /** The FAIL the far set says where that mod gave no answer by the bound, "near" the band or "far" from it. */
    public static String farUnanswered(String when) {
        return "Distant Horizons did not say what it holds at the band's middle within " + FAR_READING_SECONDS
            + " seconds of being asked " + when + ", so its own data could not be read";
    }

    /** This mod's ghosts as Distant Horizons names a block in its data: the block's id, before "_STATE_". */
    private static final String[] GHOST_IDS = { "trmtgtnh:ghost", "trmtgtnh:ghost_stair" };

    /** Whether that mod's answer, as DistantColors.heldAt words it, names one of this mod's ghosts. */
    public static boolean namesGhost(String held) {
        if (held == null) return false;
        int end = held.indexOf("_STATE_");
        if (end < 0) end = held.indexOf(' ');
        String id = end < 0 ? held : held.substring(0, end);
        for (String ghost : GHOST_IDS) {
            if (ghost.equals(id)) return true;
        }
        return false;
    }

    /**
     * The FAIL the far set says of what that mod holds at the band's middle from far away, or null where it holds the
     * ghost. 26.1.2 NeoForge passed with grass_block held there and plain grass in the photograph, because the verdict
     * asked only whether any square had ever been given its color.
     */
    public static String farHeldFault(String held) {
        if (namesGhost(held)) return null;
        return "Distant Horizons holds " + held + " at the band's middle from far away, not this mod's ghost, so far terrain"
            + " draws no wear there";
    }

    /** A color that could not be read from the photograph. */
    public static final int NO_PICTURE = -1;

    /**
     * How far apart, in one channel at least, the band's middle and the grass beside it must read for the band to be in
     * the photograph. R11 round 2's read 55 apart in red (138,100,72 against 83,108,52); the same grass read twice reads a
     * few apart.
     */
    public static final int FAR_BAND_STEP = 30;

    /**
     * The FAIL the far set says of its photograph, or null where the band is drawn: its middle a second color beside the
     * grass, as a map draws a path.
     */
    public static String farBandFault(int band, int grass) {
        if (band == NO_PICTURE || grass == NO_PICTURE) return "the far photograph could not be read for the band";
        int apart = Math.max(
            Math.abs(((band >> 16) & 255) - ((grass >> 16) & 255)),
            Math.max(Math.abs(((band >> 8) & 255) - ((grass >> 8) & 255)), Math.abs((band & 255) - (grass & 255))));
        if (apart > FAR_BAND_STEP) return null;
        return "the band is not in the far photograph: its middle reads " + said(band) + " and the grass beside it "
            + said(grass) + ", " + apart + " apart, where the band is a second color more than " + FAR_BAND_STEP + " apart";
    }

    /**
     * How differently Distant Horizons may light the band's top from the map color it was handed: the band's color, read
     * as that color times one shade, must have the shade within these bounds, and every channel within FAR_WORN_SLACK of
     * that. The line beside the band read 0.79 of worn earth's map color on 1.20.1 Fabric and 0.96 on 1.12.2; the band
     * drawn in that mod's own color for the ghost read 2.3 times it in the newer editions (earth's texture) and 3.2 times
     * it on 1.12.2 (grey).
     */
    public static final double FAR_SHADE_LEAST = 0.6D;

    public static final double FAR_SHADE_MOST = 1.3D;

    public static final int FAR_WORN_SLACK = 16;

    /**
     * The FAIL the far set says of its photograph, or null where the band is drawn in the color its worn family is given:
     * the map color this client's record gave the band's middle while it held it (DistantColors.wornColorAt), as that mod
     * shades a top. Apart from the grass is not enough (G17, the Far cell played on 1.12.2, shift 28): the band was never
     * given this mod's color in any edition, since that mod asks a run's color at its foot, and it read (138,100,70) in the
     * newer editions, that mod's own color for the ghost, and passed.
     */
    public static String farWornFault(int band, int worn) {
        if (band == NO_PICTURE) return "the far photograph could not be read for the band";
        if (worn < 0) return "no worn color was read at the band's middle while this client held its record";
        int[] b = { (band >> 16) & 255, (band >> 8) & 255, band & 255 };
        int[] w = { (worn >> 16) & 255, (worn >> 8) & 255, worn & 255 };
        double dot = 0D;
        double self = 0D;
        for (int i = 0; i < 3; i++) {
            dot += b[i] * (double) w[i];
            self += w[i] * (double) w[i];
        }
        double shade = self == 0D ? 0D : dot / self;
        int off = 0;
        for (int i = 0; i < 3; i++) off = Math.max(off, (int) Math.round(Math.abs(b[i] - shade * w[i])));
        if (shade >= FAR_SHADE_LEAST && shade <= FAR_SHADE_MOST && off <= FAR_WORN_SLACK) return null;
        return "the band is not drawn in its worn color: its middle reads " + said(band) + ", "
            + String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(shade)) + " times the map color " + said(worn)
            + " its record gives it, " + off + " off in a channel, where it is " + FAR_SHADE_LEAST + " to " + FAR_SHADE_MOST
            + " times it and no more than " + FAR_WORN_SLACK + " off";
    }

    /** A packed color as (r,g,b). */
    public static String said(int color) {
        if (color == NO_PICTURE) return "nothing";
        return "(" + ((color >> 16) & 255) + "," + ((color >> 8) & 255) + "," + (color & 255) + ")";
    }

    /** The mean of some packed colors, or {@link #NO_PICTURE} for none. */
    public static int meanColor(int[] pixels) {
        if (pixels == null || pixels.length == 0) return NO_PICTURE;
        long r = 0;
        long g = 0;
        long b = 0;
        for (int each : pixels) {
            r += (each >> 16) & 255;
            g += (each >> 8) & 255;
            b += each & 255;
        }
        int n = pixels.length;
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /**
     * The columns, {from, to}, of a picture this size that lie on a span of the ground from this far east of the camera's
     * line of sight to this far, at this distance along it, at the field of view asked for and at a tenth wider, as the
     * game draws it for a player who is flying: read there, the span is read whichever the game drew.
     */
    public static int[] farSpan(double eastFrom, double eastTo, double along, int width, int height, double fovDegrees) {
        double from = Math.max(column(eastFrom, along, width, height, fovDegrees), column(eastFrom, along, width, height, fovDegrees * 1.1D));
        double to = Math.min(column(eastTo, along, width, height, fovDegrees), column(eastTo, along, width, height, fovDegrees * 1.1D));
        return new int[] { (int) Math.ceil(from), (int) Math.floor(to) - 1 };
    }

    /**
     * The columns, {from, to}, a span of the ground is drawn on at the field of view asked for or at a tenth wider,
     * either: where the line is looked for at its darkest column, since a line three blocks wide is drawn on few columns
     * and those the two fields of view share may miss its dark one (0.9.229, shift 28).
     */
    public static int[] farSpanEither(double eastFrom, double eastTo, double along, int width, int height, double fovDegrees) {
        double from = Math.min(column(eastFrom, along, width, height, fovDegrees), column(eastFrom, along, width, height, fovDegrees * 1.1D));
        double to = Math.max(column(eastTo, along, width, height, fovDegrees), column(eastTo, along, width, height, fovDegrees * 1.1D));
        return new int[] { (int) Math.ceil(from), (int) Math.floor(to) - 1 };
    }

    /** The darkest of some packed colors, by the sum of their channels, or {@link #NO_PICTURE} for none. */
    public static int darkest(int[] colors) {
        int found = NO_PICTURE;
        int least = Integer.MAX_VALUE;
        if (colors == null) return found;
        for (int each : colors) {
            if (each == NO_PICTURE) continue;
            int sum = ((each >> 16) & 255) + ((each >> 8) & 255) + (each & 255);
            if (sum < least) {
                least = sum;
                found = each;
            }
        }
        return found;
    }

    /**
     * The brightest channel the line's darkest column may read and still be the map's darkened earth. 1.20.1 Fabric's reads
     * (45,32,28) and 26.2 NeoForge's (50,35,31); Distant Horizons' own color for the ghost reads (138,98,69) and the grass
     * (83,109,51) (0.9.229, shift 28).
     */
    public static final int FAR_LINE_BRIGHTEST = 80;

    /**
     * The FAIL the far set says of its photograph's line, or null where it is drawn dark: its darkest column no channel
     * brighter than {@link #FAR_LINE_BRIGHTEST}, and darker than the grass by more than {@link #FAR_BAND_STEP} in a
     * channel. 26.1.2 NeoForge passed with its line in that mod's own color for the ghost, (139,98,70), and 26.3 NeoForge
     * with it plain grass, every square of it forgotten as healed.
     */
    public static String farLineFault(int line, int grass) {
        if (line == NO_PICTURE || grass == NO_PICTURE) return "the far photograph could not be read for the line";
        int brightest = Math.max((line >> 16) & 255, Math.max((line >> 8) & 255, line & 255));
        int darker = Math.max(
            ((grass >> 16) & 255) - ((line >> 16) & 255),
            Math.max(((grass >> 8) & 255) - ((line >> 8) & 255), (grass & 255) - (line & 255)));
        if (brightest <= FAR_LINE_BRIGHTEST && darker > FAR_BAND_STEP) return null;
        return "the line is not drawn dark in the far photograph: its darkest column reads " + said(line) + " and the grass "
            + said(grass) + ", where the map's darkened earth reads no channel above " + FAR_LINE_BRIGHTEST + " and more than "
            + FAR_BAND_STEP + " darker than the grass, as R11 round 2's (45,32,28)";
    }

    /**
     * The most squares that may be forgotten between the far set's near line and its far one. Nothing heals there - the
     * band and the line are pinned - so any square forgotten as the camera moves away is far terrain losing the wear: 26.1.2
     * NeoForge forgot 1048 and 26.3 NeoForge 1568 and drew grass where the line was (0.9.229, shift 28).
     */
    public static final int FAR_FORGOTTEN_MOST = 8;

    /** The FAIL the far set says of the squares forgotten as the camera moved away, or null where few enough were. */
    public static String farForgottenFault(int forgotten) {
        if (forgotten <= FAR_FORGOTTEN_MOST) return null;
        return forgotten + " remembered square(s) forgotten as the camera moved away, where nothing heals and "
            + FAR_FORGOTTEN_MOST + " at most may go, so far terrain loses the wear it was given";
    }

    // ------------------------------------------------------------------
    // The far set switched off and on again (0.9.229 G17 t6; Xep, 2026-11-09: "Follow the switch")
    // ------------------------------------------------------------------

    /** The far set's photographs: its first, then with the overlay switched off, then switched on again. */
    public static final int FAR_FIRST = 0;

    public static final int FAR_OFF = 1;

    public static final int FAR_ON = 2;

    /** What each of those photographs adds to the far set's shot name. */
    private static final String[] FAR_SWITCH_SHOTS = { "", "-off", "-on" };

    /** A far photograph's name after "trmt-mods-": {@code far-1}, {@code far-1-off} and {@code far-1-on}. */
    public String farShot(int switched) {
        return shot(1, 0) + FAR_SWITCH_SHOTS[switched];
    }

    /**
     * The FAIL the far set says of the plugin's switch counts, or null where they hold. Each count is {overlay switched,
     * far terrain drawn again, clears kept as the switch's answer, forgotten as healed} (DistantColors.switchCounts), taken
     * before the overlay was switched off and now, after this many switches: each switch followed at that mod's next frame
     * with its far terrain drawn again, the server's clear answering the hello saying off kept once, and nothing forgotten
     * as healed, since switching heals nothing (0.9.229 t6: until then the clear answering that hello forgot every
     * remembered square, so switched on again far terrain never got its colors back).
     */
    public static String farSwitchFault(int[] before, int[] now, int switches) {
        if (before == null || now == null || before.length < 4 || now.length < 4) {
            return "the plugin's switch counts were not read before and after the switch";
        }
        java.util.List<String> wrong = new java.util.ArrayList<String>();
        int switched = now[0] - before[0];
        int drawn = now[1] - before[1];
        int kept = now[2] - before[2];
        int healed = now[3] - before[3];
        if (switched != switches) wrong.add("the overlay switched " + switched + " time(s) where it was switched " + switches);
        if (drawn != switches) {
            wrong.add("far terrain drawn again " + drawn + " time(s) where the overlay was switched " + switches);
        }
        if (kept != 1) wrong.add(kept + " clear(s) kept as the switch's answer where the one hello saying off is answered by one");
        if (healed != 0) wrong.add(healed + " square(s) forgotten as healed where switching the overlay heals nothing");
        return wrong.isEmpty() ? null : String.join("; ", wrong);
    }

    /**
     * The FAIL the far set says of its photograph with the overlay switched off, or null where neither the band nor the
     * line reads this mod's wear: the first verdict inverted, the band no longer in its worn family's map color
     * ({@link #farWornFault} says it is not) and the line no longer drawn dark ({@link #farLineFault} says it is not).
     * Switched off, Distant Horizons draws its own color there, for the ghost it built while worn: its average of earth's
     * texture, (138,100,70), or 1.12.2's untinted grey (146,146,146). A photograph not read is a FAIL, never a pass.
     */
    public static String farOffFault(int band, int line, int grass, int worn) {
        if (band == NO_PICTURE || line == NO_PICTURE || grass == NO_PICTURE) {
            return "the far photograph with the overlay switched off could not be read for the band and the line";
        }
        if (worn < 0) return "no worn color was read at the band's middle so switched off there is nothing to differ from";
        java.util.List<String> wrong = new java.util.ArrayList<String>();
        if (farWornFault(band, worn) == null) {
            wrong.add("the band still reads its worn color with the overlay switched off: its middle reads " + said(band)
                + " against the map color " + said(worn) + " its record gave it");
        }
        if (farLineFault(line, grass) == null) {
            wrong.add("the line is still drawn dark with the overlay switched off: its darkest column reads " + said(line)
                + " and the grass " + said(grass));
        }
        return wrong.isEmpty() ? null : String.join("; ", wrong);
    }

    /** The column a point this far east of the line of sight, this far along it, is drawn on, at this vertical field of view. */
    private static double column(double east, double along, int width, int height, double fovDegrees) {
        double focal = height / 2D / Math.tan(Math.toRadians(fovDegrees) / 2D);
        return width / 2D + focal * east / along;
    }
}
