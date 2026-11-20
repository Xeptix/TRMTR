package com.trmtgtnh.surface;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.trmtgtnh.Trmt;
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
 * packed id-and-form keys it already uses, and a {@link Names} that answers a block name with its id, a form with its
 * properties and a block with its tags. The rules themselves are {@link PlayerLayer}'s, which every edition shares;
 * this is the 1.16.5 wiring of them (carried from the 1.12.2 tree's class of the same name in W3 T17).
 *
 * <p>
 * <strong>Forms, then states.</strong> 1.16.5's table is keyed by block and form ({@link SurfaceRegistry#formed}): a
 * block's single form, and a slab's doubled one. So where 1.12.2 walks a block's sixteen metadata values this walks its
 * two forms, each matched as the state it stands for - the block's default state, and a slab's {@code type=double}
 * state ({@link Names#properties}). Every other state is then matched as itself, and one whose facts differ from its
 * form's is set apart ({@link #setApart}): it takes a {@link StateTable.Row} of its own, layered by these same rules,
 * so {@code [type=top]} is "the states with these properties". Block tags ({@code #ns:tag}) are read from this edition
 * on: every block in a tag a file names is a block the file reaches.
 *
 * <p>
 * <strong>Detection's own lines.</strong> Detection writes what it finds into the per-family lists
 * ({@code TrmtConfig.recordDetectedSurfaces}), and those lists are the player's layer. A line that names exactly what
 * detection finds for the same state is detection's own record read back, not the player overruling a file, so where
 * a file decides a state such a line is passed over and the file is heard; a line that disagrees with detection, or
 * names a state detection does not find, is the player's and wins. The same for the holders list. And a block a file
 * decides is not recorded by detection from then on, so no new line of that kind is written.
 */
public final class SurfaceData {

    private SurfaceData() {}

    /** A block's forms: single (0) and doubled (1), as {@link SurfaceRegistry#formed} keys them. */
    public static final int FORMS = 2;

    /** How a block name is found in this game. */
    public interface Names {

        /** The block's id, or -1 when it is not installed, is excluded by the player, or may not wear at all. */
        int idOf(String blockName);

        /** The block's registry name for an id, or null; what a tag's members are matched by. */
        default String nameOf(int blockId) {
            return null;
        }

        /**
         * The properties of the state this form stands for, as names and values in the game's own spelling
         * ({@code type=bottom}), so a selector naming properties matches it; null where none can be read or the block
         * has no such form.
         */
        default Map<String, String> properties(int blockId, int form) {
            return null;
        }

        /** The tags this block is in, as {@code ns:tag} without the mark; null where none can be read. */
        default Collection<String> tags(int blockId) {
            return null;
        }

        /** The ids of the blocks in a tag ({@code ns:tag}, without the mark); empty where it names none. */
        default Collection<Integer> idsInTag(String tag) {
            return Collections.emptyList();
        }

        /** Every state of a block; empty where they cannot be read. */
        default List<State> states(int blockId) {
            return Collections.emptyList();
        }
    }

    /** One block state as a file sees it. */
    public static final class State {

        /** Its id in the game's numbering of block states. */
        public final int id;

        /** The form of its block it falls in. */
        public final int form;

        /** Its properties in the game's own spelling. */
        public final Map<String, String> properties;

        public State(int id, int form, Map<String, String> properties) {
            this.id = id;
            this.form = form;
            this.properties = properties;
        }
    }

    /** Packs a block id and a form the way {@link SurfaceRegistry} keys its tables. */
    public static int key(int blockId, int form) {
        return (blockId << 4) | (form & 0xF);
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
     * Every block a file of this kind reaches, as name and id: the ids it names, and every block in each tag it names.
     * A block not installed, or that may not wear, is not reached.
     */
    static Map<String, Integer> reached(DataTable table, DataKind kind, Names names) {
        Map<String, Integer> out = new java.util.TreeMap<String, Integer>();
        for (String name : table.namedIds(kind)) {
            int id;
            try {
                id = names.idOf(name);
            } catch (RuntimeException | LinkageError awkward) {
                continue;
            }
            if (id >= 0) out.put(name, Integer.valueOf(id));
        }
        for (String tag : table.namedTags(kind)) {
            Collection<Integer> members;
            try {
                members = names.idsInTag(tag);
            } catch (RuntimeException | LinkageError awkward) {
                continue;
            }
            if (members == null) continue;
            for (Integer member : members) {
                if (member == null) continue;
                String name;
                try {
                    name = names.nameOf(member.intValue());
                    if (name == null || names.idOf(name) < 0) continue;
                } catch (RuntimeException | LinkageError awkward) {
                    continue;
                }
                out.put(name, member);
            }
        }
        return out;
    }

    /**
     * Reads the families facts of every block a file reaches, at each of its forms and the properties of the state that
     * form stands for, with the block's tags, merged as the table merges them. Never throws.
     */
    public static Read read(DataTable table, Names names) {
        if (table == null || names == null) return Read.NONE;
        Map<Integer, String> family = new HashMap<Integer, String>();
        Map<Integer, Float> resistance = new HashMap<Integer, Float>();
        Map<Integer, String> cover = new HashMap<Integer, String>();
        Map<Integer, String[]> repair = new HashMap<Integer, String[]>();
        Map<Integer, String[]> through = new HashMap<Integer, String[]>();
        Map<Integer, String> shipped = new HashMap<Integer, String>();
        for (Map.Entry<String, Integer> reached : reached(table, DataKind.FAMILIES, names).entrySet()) {
            String name = reached.getKey();
            int id = reached.getValue()
                .intValue();
            Collection<String> tags = tags(names, id);
            for (int form = 0; form < FORMS; form++) {
                Map<String, String> state = properties(names, id, form);
                // A form the block does not have (a doubled form of anything but a slab) is not read.
                if (form > 0 && state == null) continue;
                DataTable.Facts facts = table.family(name, Selector.ANY_META, state, tags);
                if (facts.isEmpty()) continue;
                readFacts(facts, Integer.valueOf(key(id, form)), family, resistance, cover, repair, through);
                Object named = facts.value("family");
                if (named instanceof String && shippedHere(facts.get("family")))
                    shipped.put(Integer.valueOf(key(id, form)), (String) named);
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
        return fact.from.source.type == DataSource.Type.MOD_JAR && Trmt.MODID.equals(fact.from.namespace);
    }

    /** One place's families facts into the maps of a {@link Read}. */
    private static void readFacts(DataTable.Facts facts, Integer at, Map<Integer, String> family,
        Map<Integer, Float> resistance, Map<Integer, String> cover, Map<Integer, String[]> repair,
        Map<Integer, String[]> through) {
        Object named = facts.value("family");
        if (named instanceof String) family.put(at, (String) named);
        Object resisted = facts.value("resistance");
        if (resisted instanceof Number) resistance.put(at, Float.valueOf(((Number) resisted).floatValue()));
        Object covered = facts.value("cover");
        if (covered instanceof String) cover.put(at, (String) covered);
        Object paid = facts.value("repairWith");
        if (paid instanceof List) {
            List<String> spelt = new ArrayList<String>();
            for (Object each : (List<?>) paid) {
                // By its id: what pays is an item, which has no properties, so ns:name[prop=value] is paid by ns:name.
                if (each instanceof Selector && !((Selector) each).tag) spelt.add(((Selector) each).id);
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

    /** The states a file sets apart from their forms, by state id: what the files say of each, and of which form. */
    public static final class SetApart {

        static final SetApart NONE = new SetApart(new HashMap<Integer, Integer>(), Read.NONE,
            new HashMap<Integer, DataShape>());

        /** Each state's key in the tables kept by block and form. */
        public final Map<Integer, Integer> forms;

        /** The families files' facts for each, as {@link #read} gives a form's. */
        public final Read read;

        /** The shape the files give each, where they give one. */
        public final Map<Integer, DataShape> shapes;

        SetApart(Map<Integer, Integer> forms, Read read, Map<Integer, DataShape> shapes) {
            this.forms = Collections.unmodifiableMap(forms);
            this.read = read;
            this.shapes = Collections.unmodifiableMap(shapes);
        }

        /** A per-form or per-block answer spread to the states that fall under it. */
        public <T> Map<Integer, T> spread(Map<Integer, T> byKey, boolean keyedByForm) {
            Map<Integer, T> out = new HashMap<Integer, T>();
            for (Map.Entry<Integer, Integer> each : forms.entrySet()) {
                int key = each.getValue()
                    .intValue();
                T found = byKey.get(Integer.valueOf(keyedByForm ? key : key >> 4));
                if (found != null) out.put(each.getKey(), found);
            }
            return out;
        }

        /** The same for a set of block ids. */
        public Set<Integer> spread(Set<Integer> byBlock) {
            Set<Integer> out = new HashSet<Integer>();
            for (Map.Entry<Integer, Integer> each : forms.entrySet()) {
                int block = each.getValue()
                    .intValue() >> 4;
                if (byBlock.contains(Integer.valueOf(block))) out.add(each.getKey());
            }
            return out;
        }
    }

    /**
     * The states whose facts differ from those of the form they fall in: a selector or a shapes entry's {@code state}
     * that takes some of a form's states and not others. Each is read as itself, so a state set apart for its shape
     * alone still carries its family's facts, and a top slab beside a {@code [type=bottom]} file, which the form's
     * default state would answer for, has the facts of what it is. Both sets of facts are compared as the table merged
     * them, so a state the entries say nothing different of is not set apart. Never throws.
     */
    public static SetApart setApart(DataTable table, Names names) {
        if (table == null || names == null) return SetApart.NONE;
        Map<Integer, Integer> forms = new HashMap<Integer, Integer>();
        Map<Integer, String> family = new HashMap<Integer, String>();
        Map<Integer, Float> resistance = new HashMap<Integer, Float>();
        Map<Integer, String> cover = new HashMap<Integer, String>();
        Map<Integer, String[]> repair = new HashMap<Integer, String[]>();
        Map<Integer, String[]> through = new HashMap<Integer, String[]>();
        Map<Integer, String> shipped = new HashMap<Integer, String>();
        Map<Integer, DataShape> shapes = new HashMap<Integer, DataShape>();
        Map<String, Integer> blocks = new java.util.TreeMap<String, Integer>(reached(table, DataKind.FAMILIES, names));
        blocks.putAll(reached(table, DataKind.SHAPES, names));
        for (Map.Entry<String, Integer> reached : blocks.entrySet()) {
            String name = reached.getKey();
            int id = reached.getValue()
                .intValue();
            List<State> states;
            try {
                states = names.states(id);
            } catch (RuntimeException | LinkageError awkward) {
                continue;
            }
            if (states == null || states.size() < 2) continue;
            Collection<String> tags = tags(names, id);
            for (State state : states) {
                Map<String, String> ofForm = properties(names, id, state.form);
                DataTable.Facts wears = table.family(name, Selector.ANY_META, state.properties, tags);
                DataTable.Facts shape = table.shape(name, Selector.ANY_META, state.properties, tags);
                String formWears = table.family(name, Selector.ANY_META, ofForm, tags)
                    .toString();
                String formShape = table.shape(name, Selector.ANY_META, ofForm, tags)
                    .toString();
                if (wears.toString()
                    .equals(formWears)
                    && shape.toString()
                        .equals(formShape))
                    continue;
                Integer at = Integer.valueOf(state.id);
                forms.put(at, Integer.valueOf(key(id, state.form)));
                readFacts(wears, at, family, resistance, cover, repair, through);
                Object named = wears.value("family");
                if (named instanceof String && shippedHere(wears.get("family"))) shipped.put(at, (String) named);
                DataShape made = shape.isEmpty() ? null
                    : DataShape.fromFacts(
                        shape.value("shape"),
                        shape.value("boxes"),
                        shape.value("maxSinkPixels"),
                        shape.value("sinks"));
                if (made != null) shapes.put(at, made);
            }
        }
        return new SetApart(forms, new Read(family, resistance, cover, repair, through, shipped), shapes);
    }

    /**
     * Each state set apart as a whole row, from what {@link #families}, {@link #cover}, {@link #holds},
     * {@link #resistance} and {@link #chains} made of it: the same rules a form is held to, so a player's list or a
     * family switched off beats a state's file as it beats a block's.
     *
     * @param built      the family each wears as, the families switched off already out
     * @param cover      which are cover
     * @param holds      which of those hold
     * @param resistance how much more traffic each takes
     * @param chains     each one's own chain
     */
    public static Map<Integer, StateTable.Row> rows(SetApart apart, Map<Integer, SurfaceFamily> built,
        Set<Integer> cover, Set<Integer> holds, Map<Integer, Float> resistance,
        Map<Integer, SurfaceFamily[]> chains) {
        Map<Integer, StateTable.Row> out = new HashMap<Integer, StateTable.Row>();
        for (Integer state : apart.forms.keySet()) {
            Float taken = resistance.get(state);
            String[] paid = apart.read.repairWith.get(state);
            out.put(
                state,
                new StateTable.Row(
                    built.get(state),
                    cover.contains(state),
                    holds.contains(state),
                    apart.shapes.get(state),
                    chains.get(state),
                    taken == null ? 1f : taken.floatValue(),
                    paid == null ? new String[0] : paid));
        }
        return out;
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
     * The shapes the files give, state by state, for the blocks this game has (W3 spec 2.4, task T6): each reached block
     * at its forms, each entry's own {@code state} heard against the state the form stands for, merged key by key as the
     * table merges them. A state whose merged facts make no shape is left to its class. Never throws.
     */
    public static Map<Integer, DataShape> shapes(DataTable table, Names names) {
        Map<Integer, DataShape> out = new HashMap<Integer, DataShape>();
        if (table == null || names == null) return out;
        for (Map.Entry<String, Integer> reached : reached(table, DataKind.SHAPES, names).entrySet()) {
            String name = reached.getKey();
            int id = reached.getValue()
                .intValue();
            Collection<String> tags = tags(names, id);
            for (int form = 0; form < FORMS; form++) {
                Map<String, String> state = properties(names, id, form);
                if (form > 0 && state == null) continue;
                DataTable.Facts facts = table.shape(name, Selector.ANY_META, state, tags);
                if (facts.isEmpty()) continue;
                DataShape shape = DataShape.fromFacts(
                    facts.value("shape"),
                    facts.value("boxes"),
                    facts.value("maxSinkPixels"),
                    facts.value("sinks"));
                if (shape != null) out.put(Integer.valueOf(key(id, form)), shape);
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

    /** A form's properties from {@link Names#properties}, or null where they cannot be read. */
    static Map<String, String> properties(Names names, int id, int form) {
        try {
            return names.properties(id, form);
        } catch (RuntimeException | LinkageError awkward) {
            return null;
        }
    }

    /** A block's tags from {@link Names#tags}, or null where they cannot be read. */
    static Collection<String> tags(Names names, int id) {
        try {
            Collection<String> found = names.tags(id);
            return found == null ? null : new LinkedHashSet<String>(found);
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
     * places vanilla blocks detection does not (sandstone stairs as stone, clay as dirt), and measured against detection
     * alone that counted each brought, so the quick demos left out every vanilla block that file places (carried from
     * 1.12.2, where it took the through yard's only stair platform with it).
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

    /**
     * A per-block map from one keyed by block and form: each block's single-form value, by its plain id. 1.16.5's cover,
     * holders and resistance are kept per block (a block's forms are never two kinds of cover or of hardness), so a
     * file's value for those is its single form's, and a state with another answer is set apart ({@link #setApart}).
     */
    public static <T> Map<Integer, T> byBlock(Map<Integer, T> byForm) {
        Map<Integer, T> out = new HashMap<Integer, T>();
        for (Map.Entry<Integer, T> each : byForm.entrySet()) {
            int packed = each.getKey()
                .intValue();
            if ((packed & 0xF) == 0) out.put(Integer.valueOf(packed >> 4), each.getValue());
        }
        return out;
    }

    /** Whether a file decides this block's family at any of its forms, so detection should not record it. */
    static boolean decidedAnywhere(Map<Integer, String> data, int blockId) {
        for (int form = 0; form < FORMS; form++) {
            if (data.containsKey(Integer.valueOf(key(blockId, form)))) return true;
        }
        return false;
    }

    /** A file's repair selectors for one state, or none. */
    public static String[] repairWith(Map<Integer, String[]> repair, int blockId, int form) {
        String[] found = repair.get(Integer.valueOf(key(blockId, form)));
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
