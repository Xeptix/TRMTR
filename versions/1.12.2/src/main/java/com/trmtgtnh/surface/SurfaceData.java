package com.trmtgtnh.surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.trmtgtnh.Tags;
import com.trmtgtnh.data.DataKind;
import com.trmtgtnh.data.DataSource;
import com.trmtgtnh.data.DataTable;
import com.trmtgtnh.data.PlayerLayer;
import com.trmtgtnh.data.Selector;

/**
 * The data files' families, cover, resistance and repair, laid between detection and the player's own lists (W3 spec
 * 2.2, task T5): "data beats detection; the player beats data", and data never written into the player's lists.
 *
 * <p>
 * Game-free, so the suite can hold every rule here: {@link SurfaceRegistry#resolve} hands over the block states as the
 * packed id-and-metadata keys it already uses, and a {@link Names} that answers a block name with its id and a state
 * with its properties. The rules themselves are {@link PlayerLayer}'s, which every edition shares; this is the 1.12.2
 * wiring of them (carried from the 1.7.10 tree's class of the same name in W3 T16), where a selector may name a
 * state's properties as well as its metadata.
 *
 * <p>
 * <strong>Detection's own lines.</strong> Detection writes what it finds into the per-family lists
 * ({@code TrmtConfig.recordDetectedSurfaces}), and those lists are the player's layer. A line that names exactly what
 * detection finds for the same state is detection's own record read back, not the player overruling a file, so where
 * a file decides a state such a line is passed over and the file is heard; a line that disagrees with detection, or
 * names a state detection does not find, is the player's and wins. The same for the holders list. And a state a file
 * decides is not recorded by detection from then on, so no new line of that kind is written.
 */
public final class SurfaceData {

    private SurfaceData() {}

    /** How a block name is found in this game. */
    public interface Names {

        /** The block's id, or -1 when it is not installed, is excluded by the player, or may not wear at all. */
        int idOf(String blockName);

        /**
         * Whether one value of a block found here keeps a tile entity, and so may not wear: asked of each value, as
         * detection asks it (Xep, 2026-11-09, "Fix it, per-value rule"). None, where a caller has no blocks to ask.
         */
        default boolean keepsStateAt(String blockName, int meta) {
            return false;
        }

        /**
         * The properties of the state this block takes at this metadata value, as names and values in the game's own
         * spelling ({@code half=bottom}), so a selector naming properties matches it; null where none can be read.
         */
        default Map<String, String> properties(int blockId, int meta) {
            return null;
        }
    }

    /** Whether a file reaches this value of a block {@link Names#idOf} found: not where it keeps a tile entity. */
    private static boolean reaches(Names names, String name, int meta) {
        try {
            return !names.keepsStateAt(name, meta);
        } catch (RuntimeException | LinkageError awkward) {
            return false;
        }
    }

    /** Packs a block id and a metadata value the way {@link SurfaceRegistry} keys its tables. */
    public static int key(int blockId, int meta) {
        return (blockId << 4) | (meta & 0xF);
    }

    /** What the families files say, state by state, for the blocks this game has. */
    public static final class Read {

        /** A state's family key, or {@code none}. */
        public final Map<Integer, String> family;
        /** A state's resistance, where a file gives one. */
        public final Map<Integer, Float> resistance;
        /** A state's {@code breaks} or {@code holds}. */
        public final Map<Integer, String> cover;
        /** What else pays for mending a state, as selectors in the config lists' spelling. */
        public final Map<Integer, String[]> repairWith;

        /**
         * A state's own {@code wearsThroughTo} as written, family keys in order; empty for {@code "none"}, a block that
         * sinks as itself (W3 T9).
         */
        public final Map<Integer, String[]> through;

        /**
         * The family keys of {@link #family} that this mod's own jar decides - its shipped files, which are the mod's
         * own families as detection is, so nothing they place counts as brought ({@link SurfaceData#brought(Map, Map,
         * Map, Read, Vanilla)}).
         */
        public final Map<Integer, String> shipped;

        Read(Map<Integer, String> family, Map<Integer, Float> resistance, Map<Integer, String> cover,
            Map<Integer, String[]> repairWith, Map<Integer, String[]> through, Map<Integer, String> shipped) {
            this.family = Collections.unmodifiableMap(family);
            this.resistance = Collections.unmodifiableMap(resistance);
            this.cover = Collections.unmodifiableMap(cover);
            this.repairWith = Collections.unmodifiableMap(repairWith);
            this.through = Collections.unmodifiableMap(through);
            this.shipped = Collections.unmodifiableMap(shipped);
        }

        static final Read NONE = new Read(
            new HashMap<Integer, String>(),
            new HashMap<Integer, Float>(),
            new HashMap<Integer, String>(),
            new HashMap<Integer, String[]>(),
            new HashMap<Integer, String[]>(),
            new HashMap<Integer, String>());
    }

    /**
     * Reads the families facts of every block a file names, at each of its sixteen metadata values and the properties
     * of the state it takes there, merged as the table merges them. 1.12.2 has no block tags, so the named ids are
     * every block a file can reach. Never throws.
     */
    public static Read read(DataTable table, Names names) {
        if (table == null || names == null) return Read.NONE;
        Map<Integer, String> family = new HashMap<Integer, String>();
        Map<Integer, Float> resistance = new HashMap<Integer, Float>();
        Map<Integer, String> cover = new HashMap<Integer, String>();
        Map<Integer, String[]> repair = new HashMap<Integer, String[]>();
        Map<Integer, String[]> through = new HashMap<Integer, String[]>();
        Map<Integer, String> shipped = new HashMap<Integer, String>();
        for (String name : table.namedIds(DataKind.FAMILIES)) {
            int id;
            try {
                id = names.idOf(name);
            } catch (RuntimeException | LinkageError awkward) {
                continue;
            }
            if (id < 0) continue;
            for (int meta = 0; meta < 16; meta++) {
                DataTable.Facts facts = table.family(name, meta, properties(names, id, meta), null);
                if (facts.isEmpty() || !reaches(names, name, meta)) continue;
                Integer at = Integer.valueOf(key(id, meta));
                Object named = facts.value("family");
                if (named instanceof String) family.put(at, (String) named);
                if (named instanceof String && shippedHere(facts.get("family"))) shipped.put(at, (String) named);
                Object resisted = facts.value("resistance");
                if (resisted instanceof Number) resistance.put(at, Float.valueOf(((Number) resisted).floatValue()));
                Object covered = facts.value("cover");
                if (covered instanceof String) cover.put(at, (String) covered);
                Object paid = facts.value("repairWith");
                if (paid instanceof List) {
                    List<String> spelt = new ArrayList<String>();
                    for (Object each : (List<?>) paid) {
                        if (each instanceof Selector && !((Selector) each).tag)
                            spelt.add(((Selector) each).canonical());
                    }
                    if (!spelt.isEmpty()) repair.put(at, spelt.toArray(new String[spelt.size()]));
                }
                Object wears = facts.value("wearsThroughTo");
                if (wears instanceof List) {
                    List<String> keys = new ArrayList<String>();
                    for (Object each : (List<?>) wears) {
                        // "none" stands alone (the reader refuses it beside a family): the block sinks as itself.
                        if (each instanceof String && !"none".equals(each)) keys.add((String) each);
                    }
                    through.put(at, keys.toArray(new String[keys.size()]));
                }
            }
        }
        return new Read(family, resistance, cover, repair, through, shipped);
    }

    /**
     * Whether a fact was set by this mod's own files: a mod's file under this mod's namespace. By the namespace and not
     * by the mod id of the jar it came in, because a Fabric development run serves the common module's resources as a
     * generated mod of their own ({@code generated_...}), and there the quick demos still left out every vanilla block
     * the shipped files place (rig, 2026-11-19).
     */
    static boolean shippedHere(DataTable.Fact fact) {
        if (fact == null || fact.from == null || fact.from.source == null) return false;
        return fact.from.source.type == DataSource.Type.MOD_JAR && Tags.MOD_ID.equals(fact.from.namespace);
    }

    /**
     * The blocks' own chains (W3 T9): each state a file gives a {@code wearsThroughTo}, as the families it names in
     * order, where the state wears as the family that list was written for - the file's own {@code family} for it, or
     * detection's where the files give none. A state the player's lists moved to another family, or that does not wear,
     * follows its family's chain: the player beats data, and a list written for one family's ground says nothing about
     * another's. A name this build has no family for is passed over; an empty list sinks as itself.
     *
     * @param built    the family table as published (detection, the files, the player's lists)
     * @param detected what detection found
     * @param data     what the files say ({@link Read#family} and {@link Read#through})
     */
    public static Map<Integer, SurfaceFamily[]> chains(Map<Integer, SurfaceFamily> built,
        Map<Integer, SurfaceFamily> detected, Read data) {
        Map<Integer, SurfaceFamily[]> out = new HashMap<Integer, SurfaceFamily[]>();
        for (Map.Entry<Integer, String[]> each : data.through.entrySet()) {
            Integer state = each.getKey();
            SurfaceFamily family = built.get(state);
            if (family == null || !family.staged) continue;
            String fromData = data.family.get(state);
            SurfaceFamily meant = fromData != null ? SurfaceFamily.byKey(fromData) : detected.get(state);
            if (meant != family) continue;
            List<SurfaceFamily> named = new ArrayList<SurfaceFamily>();
            for (String key : each.getValue()) {
                SurfaceFamily one = SurfaceFamily.byKey(key);
                if (one != null && one.staged && !named.contains(one)) named.add(one);
            }
            out.put(state, named.toArray(new SurfaceFamily[named.size()]));
        }
        return out;
    }

    /**
     * The shapes the files give, state by state, for the blocks this game has (W3 spec 2.4, task T6): each named block
     * at its sixteen metadata values, each entry's own {@code meta} heard, merged key by key as the table merges them.
     * A state whose merged facts make no shape is left to its class. Never throws.
     */
    public static Map<Integer, DataShape> shapes(DataTable table, Names names) {
        Map<Integer, DataShape> out = new HashMap<Integer, DataShape>();
        if (table == null || names == null) return out;
        for (String name : table.namedIds(DataKind.SHAPES)) {
            int id;
            try {
                id = names.idOf(name);
            } catch (RuntimeException | LinkageError awkward) {
                continue;
            }
            if (id < 0) continue;
            for (int meta = 0; meta < 16; meta++) {
                DataTable.Facts facts = table.shape(name, meta, properties(names, id, meta), null);
                if (facts.isEmpty() || !reaches(names, name, meta)) continue;
                DataShape shape = DataShape.fromFacts(
                    facts.value("shape"),
                    facts.value("boxes"),
                    facts.value("maxSinkPixels"),
                    facts.value("sinks"));
                if (shape != null) out.put(Integer.valueOf(key(id, meta)), shape);
            }
        }
        return out;
    }

    /**
     * The family table: detection, then the files, then the player's lists ({@link PlayerLayer#family}). A family
     * switched off is not asked here; {@code SurfaceRegistry.dropDisabledFamilies} takes those out afterwards, as it
     * always has.
     *
     * @param detected what detection found
     * @param listed   what the player's per-family lists name, applied in family order as they always were
     * @param data     what the files say ({@link Read#family})
     */
    public static Map<Integer, SurfaceFamily> families(Map<Integer, SurfaceFamily> detected,
        Map<Integer, SurfaceFamily> listed, Map<Integer, String> data) {
        Set<Integer> states = new HashSet<Integer>(detected.keySet());
        states.addAll(listed.keySet());
        states.addAll(data.keySet());
        Map<Integer, SurfaceFamily> out = new HashMap<Integer, SurfaceFamily>(states.size() * 2);
        for (Integer state : states) {
            SurfaceFamily found = detected.get(state);
            SurfaceFamily player = listed.get(state);
            String fromData = data.get(state);
            // Detection's own line, read back from the list it wrote: not the player overruling a file.
            if (player != null && fromData != null && player == found) player = null;
            String answer = PlayerLayer.family(
                found == null ? null : found.key(),
                fromData,
                player == null ? null : player.key(),
                false,
                null);
            SurfaceFamily family = answer == null ? null : SurfaceFamily.byKey(answer);
            if (family != null) out.put(state, family);
        }
        return out;
    }

    /** A state's properties from {@link Names#properties}, or null where they cannot be read. */
    static Map<String, String> properties(Names names, int id, int meta) {
        try {
            return names.properties(id, meta);
        } catch (RuntimeException | LinkageError awkward) {
            return null;
        }
    }

    /** Whether a block id is the game's own, {@code minecraft:}. */
    public interface Vanilla {

        boolean isVanilla(int blockId);
    }

    /**
     * The states a data file or a plugin brought into the family table: each state a file gives a family that wears
     * there, where the block is not the game's own, or where the table without the files would not wear it as that
     * family. A file that only says again what detection and the player's lists already decide for a vanilla block
     * brings nothing. The quick demos lay none of these (2026-11-04: dev 1.7.10 carries the example mod and its data,
     * and its loam, step and runestone took rows of their own, so dev and pack photographs stopped matching); the
     * {@code api} part is where they are played.
     *
     * @param built   the family table as published (detection, the files, the player's lists)
     * @param own     the same table without the files ({@link #families} given no data)
     * @param data    what the files say ({@link Read#family})
     * @param vanilla which block ids are the game's own
     */
    public static Set<Integer> brought(Map<Integer, SurfaceFamily> built, Map<Integer, SurfaceFamily> own,
        Map<Integer, String> data, Vanilla vanilla) {
        Set<Integer> out = new HashSet<Integer>();
        for (Integer state : data.keySet()) {
            SurfaceFamily family = built.get(state);
            if (family == null) continue;
            if (own.get(state) != family || !vanilla.isVanilla(state.intValue() >> 4)) out.add(state);
        }
        return out;
    }

    /**
     * What the files brought, the mod's own shipped files counted as the mod's own (rig, 2026-11-19): measured against
     * the table with only those files, and only over the states another source decides. The shipped minecraft.json
     * moves stone_stairs off detection's stone to cobble, and measured against detection alone that counted it brought,
     * so the quick demos laid no stairs and the through yard had none to photograph; every vanilla block that file
     * places was left out of them with it.
     *
     * @param built  the family table as published (detection, the files, the player's lists)
     * @param found  what detection found
     * @param listed what the player's per-family lists name
     * @param data   what the files say, with what this mod's own jar decides ({@link Read#shipped})
     */
    public static Set<Integer> brought(Map<Integer, SurfaceFamily> built, Map<Integer, SurfaceFamily> found,
        Map<Integer, SurfaceFamily> listed, Read data, Vanilla vanilla) {
        Map<Integer, String> others = new HashMap<Integer, String>(data.family);
        others.keySet()
            .removeAll(data.shipped.keySet());
        return brought(built, families(found, listed, data.shipped), others, vanilla);
    }

    /** The ground cover: detection's and the player's list's, and every state a file gives a {@code cover}. */
    public static Set<Integer> cover(Set<Integer> detectedAndListed, Map<Integer, String> data) {
        Set<Integer> out = new HashSet<Integer>(detectedAndListed);
        out.addAll(data.keySet());
        return out;
    }

    /**
     * Which cover holds its square: the player's holders list, then a file's {@code holds} or {@code breaks}, then
     * detection. Only ever a state of the cover set.
     */
    public static Set<Integer> holds(Set<Integer> cover, Set<Integer> detected, Set<Integer> listed,
        Map<Integer, String> data) {
        Set<Integer> out = new HashSet<Integer>();
        for (Integer state : cover) {
            boolean found = detected.contains(state);
            boolean player = listed.contains(state);
            String fromData = data.get(state);
            // As for the families: a holder line that only repeats detection is detection's own record.
            if (player && fromData != null && found) player = false;
            boolean held;
            if (player) held = true;
            else if (fromData != null) held = "holds".equals(fromData);
            else held = found;
            if (held) out.add(state);
        }
        return out;
    }

    /** Resistance: a file's, with the player's resistant lists over it. Ordinary ground (1) is not kept. */
    public static Map<Integer, Float> resistance(Map<Integer, Float> data, Map<Integer, Float> listed) {
        Map<Integer, Float> out = new HashMap<Integer, Float>();
        for (Map.Entry<Integer, Float> each : data.entrySet()) {
            if (each.getValue()
                .floatValue() > 1f) out.put(each.getKey(), each.getValue());
        }
        out.putAll(listed);
        return out;
    }

    /** Whether a file decides this block's family at any of its values, so detection should not record it. */
    static boolean decidedAnywhere(Map<Integer, String> data, int blockId) {
        for (int meta = 0; meta < 16; meta++) {
            if (data.containsKey(Integer.valueOf(key(blockId, meta)))) return true;
        }
        return false;
    }

    /** A file's repair selectors for one state, or none. */
    public static String[] repairWith(Map<Integer, String[]> repair, int blockId, int meta) {
        String[] found = repair.get(Integer.valueOf(key(blockId, meta)));
        return found == null ? new String[0] : found;
    }

    /** Both lists of what pays, the family's and a file's, as one, the family's first. */
    public static String[] joined(String[] family, String[] data) {
        if (data == null || data.length == 0) return family;
        if (family == null || family.length == 0) return data;
        String[] out = new String[family.length + data.length];
        System.arraycopy(family, 0, out, 0, family.length);
        System.arraycopy(data, 0, out, family.length, data.length);
        return out;
    }

    /**
     * The table's fingerprint with the server's data folded in, so a client whose mods or packs give it other data
     * uses the server's table for the visit (W3 spec 2.2). No data leaves the table's own fingerprint as it was, so
     * a side with no data still agrees with one from before trmtr-api.
     */
    public static long withData(long table, long data) {
        if (data == 0L) return table;
        long hash = table;
        hash ^= data;
        hash *= 0x100000001b3L;
        hash ^= data >>> 29;
        hash *= 0x100000001b3L;
        return hash == 0L ? 1L : hash;
    }
}
