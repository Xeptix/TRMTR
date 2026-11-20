package com.trmtgtnh.dataload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.trmtgtnh.data.DataKind;
import com.trmtgtnh.data.DataTable;

/**
 * What the looks files say of one block state, for the texture build (W3 spec 2.1, task T5): its look and strength,
 * the face it wears in place of its own top, the layer behind a cut-away face, and its authored stage pictures.
 *
 * <p>
 * Looks are each client's own (a resource pack may give them), so nothing here reaches the server's fingerprint; what
 * reaches the textures' own signature is {@link #signature}, so a {@code /trmt reload} that changes a look stitches
 * again. A block's look is per block and so beats its family's {@code wearPattern} and {@code wearStrength}, which are
 * per family; the player's own {@code innerLayerTextures} line still beats a file's {@code innerLayer}.
 *
 * <p>
 * A block is asked by its registry name and its form (the surface table's: the block itself, or a doubled slab), with
 * the properties of the state that form stands for and the block's tags, so a selector naming properties
 * ({@code ns:name[type=bottom]}) or a tag ({@code #ns:tag}) matches. Keyed by text, so this is the stitch's reader and
 * never the drawing's: a state's pictures are found while drawing by its place in {@code StateSets}. The game-free half
 * takes plain strings, so the suite holds it without a game. Answers are kept for the table they came from, since the planner asks every block on
 * each stitch. Texture names are 26.1.2's atlas paths ({@code example:block/loam_top}). Carried from the 1.12.2
 * tree's class of the same name in W3 T18.
 */
public final class BlockLooks {

    /** The form a block state is filed under: the block itself. */
    public static final int SINGLE = 0;

    /** A slab's doubled form. */
    public static final int DOUBLED = 1;

    /** One block state's look, any part of which may be absent. */
    public static final class Look {

        /** No file names this state. */
        public static final Look NONE = new Look(null, null, null, null, Collections.<String>emptyList(), null);

        /** The look's id ({@code FamilySettings.PATTERN_*}), or null. */
        public final String pattern;
        /** Its strength, 0 to 1, or null. */
        public final Float strength;
        /** The sprite to wear in place of the block's own top, or null. */
        public final String face;
        /** The sprite behind a cut-away face, or null. */
        public final String innerLayer;
        /** Authored pictures, two to sixteen, or empty. */
        public final List<String> stages;

        /**
         * Whether the block's worn top turns a quarter with its position, or null where the file does not say and the
         * face's own pixels and names decide ({@code FaceDirection}). A file's word wins over both (0.9.229).
         */
        public final Boolean turns;

        Look(String pattern, Float strength, String face, String innerLayer, List<String> stages, Boolean turns) {
            this.pattern = pattern;
            this.strength = strength;
            this.face = face;
            this.innerLayer = innerLayer;
            this.stages = Collections.unmodifiableList(stages);
            this.turns = turns;
        }

        public boolean isNone() {
            return pattern == null && strength == null && face == null && innerLayer == null && stages.isEmpty()
                && turns == null;
        }

        /**
         * The face the set planner files a block under when the block will not name its own top (Codex review R18):
         * the file's face, or, for a file that gives only authored pictures, its first picture. Null when the file
         * gives neither, and the block is left to the planner's rescue as before. Without it a block whose top cannot
         * be asked for never drew the pictures its looks file gave it.
         */
        public String planFace() {
            if (face != null) return face;
            return stages.isEmpty() ? null : stages.get(0);
        }

        /** The look's pattern where it gives one, else the family's. */
        public String patternOr(String family) {
            return pattern != null ? pattern : family;
        }

        /** The look's strength where it gives one, else the family's. */
        public float strengthOr(float family) {
            return strength != null ? strength.floatValue() : family;
        }

        /**
         * What two blocks must share, beyond their faces and family, to share one set of worn pictures: every part of
         * the look painted into them. Empty for no look, so the sets of a pack without looks files are keyed as before.
         */
        public String setKey() {
            if (isNone()) return "";
            StringBuilder out = new StringBuilder(" look");
            out.append(' ')
                .append(pattern)
                .append(' ')
                .append(strength)
                .append(' ')
                .append(face)
                .append(' ')
                .append(innerLayer);
            for (String stage : stages) out.append(' ')
                .append(stage);
            // Under client.wearTurns' turn only, a set that turns is drawn with one pattern and one that keeps still
            // with all of them, so two blocks a file tells apart here cannot share one set.
            if (turns != null) out.append(turns.booleanValue() ? " turns" : " still");
            return out.toString();
        }
    }

    private BlockLooks() {}

    /** The answers kept for one table, replaced as one. */
    private static final class Kept {

        final DataTable table;
        final Map<String, Look> answers = new ConcurrentHashMap<String, Look>();

        /** The blocks a looks selector names by their properties. */
        final java.util.Set<String> picked = new java.util.HashSet<String>();

        Kept(DataTable table) {
            this.table = table;
            if (table == null) return;
            for (String target : table.targets(DataKind.LOOKS)
                .keySet()) {
                int bracket = target.indexOf('[');
                if (bracket > 0) picked.add(target.substring(0, bracket));
            }
        }
    }

    /**
     * Whether a looks selector of the last data read names some of this block's states by their properties, so its
     * states may wear different pictures ({@code StateSets}). Any thread.
     */
    public static boolean picksStates(String blockName) {
        DataTable table = DataLoad.table();
        Kept here = kept;
        if (here.table != table) {
            here = new Kept(table);
            kept = here;
        }
        return here.picked.contains(blockName);
    }

    private static volatile Kept kept = new Kept(null);

    /** What the last data read says of a block state, by its block, form, properties and tags. Any thread. */
    public static Look of(BlockState state) {
        if (state == null) return Look.NONE;
        try {
            Block block = state.getBlock();
            Identifier name = BuiltInRegistries.BLOCK.getKey(block);
            if (name == null) return Look.NONE;
            return of(DataLoad.table(), name.toString(), formOf(state), propertiesOf(state), tagsOf(block));
        } catch (RuntimeException | LinkageError unreadable) {
            return Look.NONE;
        }
    }

    /**
     * What the last data read says of a block in its single form, the state the wear textures are built for: 26.1.2's
     * wear sprites are filed by block, so the block's default state answers for it.
     */
    public static Look of(Block block) {
        if (block == null) return Look.NONE;
        try {
            return of(block.defaultBlockState());
        } catch (RuntimeException | LinkageError unreadable) {
            return Look.NONE;
        }
    }

    /**
     * What a table says of a block state by its registry name, form, properties and tags; {@link Look#NONE} where
     * nothing does. Kept by name, form, properties and tags, since the tags a client holds change with the server it
     * joins. Never throws.
     */
    public static Look of(DataTable table, String blockName, int form, Map<String, String> state,
        Collection<String> tags) {
        if (table == null || blockName == null) return Look.NONE;
        Kept here = kept;
        if (here.table != table) {
            here = new Kept(table);
            kept = here;
        }
        String key = blockName + "|" + form + "|" + state + "|" + tags;
        Look known = here.answers.get(key);
        if (known != null) return known;
        Look found = read(table, blockName, form, state, tags);
        here.answers.put(key, found);
        return found;
    }

    private static Look read(DataTable table, String blockName, int form, Map<String, String> state,
        Collection<String> tags) {
        try {
            if (table.namedIds(DataKind.LOOKS)
                .isEmpty()
                && table.namedTags(DataKind.LOOKS)
                    .isEmpty())
                return Look.NONE;
            DataTable.Facts facts = table.look(blockName, form, state, tags);
            if (facts.isEmpty()) return Look.NONE;
            Object pattern = facts.value("look");
            Object strength = facts.value("strength");
            Object face = facts.value("face");
            Object inner = facts.value("innerLayer");
            Object stages = facts.value("stages");
            Object turns = facts.value("turns");
            List<String> pictures = new ArrayList<String>();
            if (stages instanceof List) {
                for (Object each : (List<?>) stages) if (each instanceof String) pictures.add((String) each);
            }
            Look look = new Look(
                pattern instanceof String ? (String) pattern : null,
                strength instanceof Number ? Float.valueOf(((Number) strength).floatValue()) : null,
                face instanceof String ? (String) face : null,
                inner instanceof String ? (String) inner : null,
                pictures.size() >= 2 ? pictures : Collections.<String>emptyList(),
                turns instanceof Boolean ? (Boolean) turns : null);
            return look.isNone() ? Look.NONE : look;
        } catch (RuntimeException unexpected) {
            return Look.NONE;
        }
    }

    /** The surface table's form of a state: a doubled slab, or the block itself. */
    static int formOf(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE)
            && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? DOUBLED : SINGLE;
    }

    /** A state's properties as names and values in the game's own spelling ({@code type=bottom}). */
    static Map<String, String> propertiesOf(BlockState state) {
        Map<String, String> out = new TreeMap<String, String>();
        // A stream of pairs from 26.1, where 1.21.1 held a map; each pair says its own value's name.
        state.getValues()
            .forEach(
                each -> out.put(
                    each.property()
                        .getName(),
                    each.valueName()));
        return out;
    }

    /** The block tags this side holds the block in, {@code ns:tag} without the mark; empty where none are bound. */
    static Collection<String> tagsOf(Block block) {
        return com.trmtgtnh.surface.SurfaceRegistry.tagsOf(block);
    }

    /**
     * Whether any looks file gives a block a layer behind it. Asked as a texture pass begins, because a file's layer
     * moves on the same terms as a line in {@code innerLayerTextures}, and the pass counts pictures only where something
     * may ask.
     */
    public static boolean namesALayer() {
        return namesALayer(DataLoad.table());
    }

    /** The same of one table. Never throws. */
    public static boolean namesALayer(DataTable table) {
        if (table == null) return false;
        try {
            for (DataTable.Facts facts : table.targets(DataKind.LOOKS)
                .values()) {
                if (facts.value("innerLayer") != null) return true;
            }
            return false;
        } catch (RuntimeException unexpected) {
            return false;
        }
    }

    /**
     * Which authored picture a gradation shows: the gradations spread across the pictures in order, the first
     * gradations on the first picture and the last on the last (FORMAT.md, "looks/": "TRMTR spreads its wear stages
     * across them in order").
     *
     * <p>
     * Up to as many pictures as gradations, each picture takes an even share of the gradations. With more pictures than
     * gradations, the first gradation shows the first picture and the last the last, spread over {@code pictures - 1}
     * and {@code stageCount - 1} to the nearest (Codex review R18): the share form gave three gradations over four
     * pictures 0, 1 and 2, and the deepest picture never drew.
     */
    public static int stagePicture(int stage, int stageCount, int pictures) {
        if (pictures <= 1 || stageCount <= 1) return 0;
        int at = Math.max(0, Math.min(stage, stageCount - 1));
        if (pictures > stageCount) {
            long spans = stageCount - 1;
            return (int) (((long) at * (pictures - 1) * 2 + spans) / (2 * spans));
        }
        return Math.min(pictures - 1, (int) ((long) at * pictures / stageCount));
    }

    /**
     * A signature of every look a table gives, for the texture signature {@code ConfigReload} compares across a
     * reload: a look changed in the config folder must stitch again, as a changed {@code wearPattern} does.
     */
    public static int signature(DataTable table) {
        if (table == null) return 0;
        try {
            return table.targets(DataKind.LOOKS)
                .toString()
                .hashCode();
        } catch (RuntimeException unexpected) {
            return 0;
        }
    }
}
