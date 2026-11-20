package com.trmtgtnh.erosion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.original.FaithfulChains;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The ordered list of appearances a surface passes through as it wears, and the reverse
 * order it recovers along.
 *
 * <p>
 * Upstream models this as a sequence of real block replacements: grass_block becomes
 * eroded_grass_block s0..s4, then eroded_dirt, then eroded_coarse_dirt. Here nothing is
 * replaced - the block in the world stays exactly the grass it always was - so the chain
 * lives entirely in data and only decides which ghost block the client paints on top.
 *
 * <p>
 * Chains are rebuilt whenever config changes, because stage counts are configurable and
 * grass may or may not be allowed to wear through into dirt. A step is one packed erosion
 * state, so a chain is a {@code short[]} and "how worn is this?" is an index into it.
 */
public final class ErosionChain {

    /** Rebuilt on config load; replaced wholesale so concurrent readers stay consistent. */
    private static volatile Map<SurfaceFamily, short[]> chains = emptyChains();

    /**
     * This mod's own chains as last built from the player's settings, kept while Faithful Mode's replace them, so a
     * record worn along one can be placed on the other by how far along it was ({@link #reseatIndex}).
     */
    private static volatile Map<SurfaceFamily, short[]> own = emptyChains();

    /** A chunk's records were last placed on this mod's own chains: every save from before 0.9.229 W5 says so. */
    public static final byte PLACED_OWN = 0;

    /** A chunk's records were last placed on Faithful Mode's chains. */
    public static final byte PLACED_FAITHFUL = 1;

    /** Which chains {@link #rebuild} last built, so a record is placed on the chains that are in force. */
    private static volatile byte placing = PLACED_OWN;

    /**
     * The chains of blocks a data file gives a list of their own (trmtr-api, a families entry's {@code wearsThroughTo};
     * W3 T9), by base and list as written. Each is built from the settings in force the first time a block on it is
     * asked about, and all are dropped at every {@link #rebuild}, so a block's own chain is always built from the same
     * settings as the family chains beside it. Records hold states, never indexes, so a block whose chain changes keeps
     * its record and is placed again on the new chain ({@link Chain#reseatIndex}).
     */
    private static volatile Map<String, Chain> variants = new ConcurrentHashMap<String, Chain>();

    /**
     * The family chains as {@link Chain}s, rebuilt with them, so the hot paths that ask for a family's chain allocate
     * nothing.
     */
    private static volatile Map<SurfaceFamily, Chain> wholes = Collections
        .unmodifiableMap(new EnumMap<SurfaceFamily, Chain>(SurfaceFamily.class));

    /** The lists data files give blocks, by the family those blocks wear as, as the surface table last published. */
    private static volatile Map<SurfaceFamily, List<SurfaceFamily[]>> lists = Collections
        .unmodifiableMap(new EnumMap<SurfaceFamily, List<SurfaceFamily[]>>(SurfaceFamily.class));

    /**
     * Every family a block's own list has named this session, by the family the block wears as: what a block of that
     * family may have been worn into. Only ever grows, so a record made on a block's own chain is still known for that
     * ground's own after the list is changed or taken away by a later data read ({@link #canEverShow}). Replaced
     * whole under {@link #NAMING}, read without a lock.
     */
    private static volatile Map<SurfaceFamily, Set<SurfaceFamily>> named = Collections
        .unmodifiableMap(new EnumMap<SurfaceFamily, Set<SurfaceFamily>>(SurfaceFamily.class));

    private static final Object NAMING = new Object();

    private ErosionChain() {}

    /**
     * Which chains records are placed on now: Faithful Mode's while the chains in force are its, this mod's own
     * otherwise (Codex review R17). A chunk whose records were placed on the other is placed again, whole, by
     * {@link #placeAcross}, before anything reads it.
     */
    public static byte placing() {
        return placing;
    }

    private static Map<SurfaceFamily, short[]> emptyChains() {
        return Collections.unmodifiableMap(new EnumMap<SurfaceFamily, short[]>(SurfaceFamily.class));
    }

    /**
     * Recomputes every chain from the current family settings.
     *
     * <p>
     * A chain is the whole run a surface takes from untouched to as worn as it gets, and every
     * step on it is a family, a visual layer and a depth. The shape is the same for everything:
     * a long run of layers on the surface as it stands, and then, each time that run is used up,
     * the ground drops a pixel and a shorter run starts on the freshly exposed material. The
     * first run is longer because that is the wear people actually look at - a track appearing
     * in turf - and the later ones are shorter because by then the interesting part is the rut.
     */
    public static void rebuild() {
        Map<SurfaceFamily, short[]> built = new EnumMap<SurfaceFamily, short[]>(SurfaceFamily.class);

        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged) continue;
            FamilySettings settings = TrmtConfig.family(family);
            if (settings == null || !settings.enabled) continue;

            // Faithful Mode wears grass, dirt and sand along the original mod's own chains, step for step (0.9.229 W5,
            // spec 2.2). A table rather than the builder below, because the original wears grass through to level
            // earth, and the builder only ever reaches the material underneath by sinking into it.
            if (FaithfulChains.wears(family)) {
                built.put(family, FaithfulChains.chain(family));
                continue;
            }

            short[] chain = general(family, settings);
            if (chain != null) built.put(family, chain);
        }

        Map<SurfaceFamily, Chain> whole = new EnumMap<SurfaceFamily, Chain>(SurfaceFamily.class);
        for (SurfaceFamily family : SurfaceFamily.values()) {
            whole.put(family, new Chain(family, built.get(family), null));
        }
        chains = Collections.unmodifiableMap(built);
        wholes = Collections.unmodifiableMap(whole);
        // A block's own chain is built from the settings the family chains were just built from, so the ones built
        // from the last settings go with them.
        variants = new ConcurrentHashMap<String, Chain>();
        placing = FaithfulChains.on() ? PLACED_FAITHFUL : PLACED_OWN;
    }

    /**
     * The chain a family's blocks follow, unless a block has one of its own
     * ({@link #of(SurfaceFamily, SurfaceFamily[])}).
     * Never null for a family; its steps are none where the family does not erode.
     */
    public static Chain of(SurfaceFamily base) {
        if (base == null) return Chain.NOWHERE;
        Chain whole = wholes.get(base);
        return whole != null ? whole : new Chain(base, chains.get(base), null);
    }

    /**
     * The chain a block follows: its family's, or, when a data file gives the block a {@code wearsThroughTo} of its
     * own,
     * that list's (trmtr-api, W3 T9). Built as the family's is - the family's own run on its face, its depth and layers
     * -
     * with the block's list for what the ground shows as it sinks, held to the same switches and the same rules as a
     * family's list ({@link #honoured}); an empty list ({@code "none"} in the file) sinks as itself. Faithful Mode's
     * three wear the original's chains whatever a block's list says, as the original had no lists. A family that does
     * not erode gives a chain with no steps, whatever its blocks' lists say.
     *
     * @param through the block's own list as written, in order, or null for its family's chain
     */
    public static Chain of(SurfaceFamily base, SurfaceFamily[] through) {
        if (through == null || base == null || FaithfulChains.wears(base)) return of(base);
        if (chains.get(base) == null) return of(base);
        Map<String, Chain> built = variants;
        String key = variantKey(base, through);
        Chain chain = built.get(key);
        if (chain != null) return chain;
        FamilySettings settings = TrmtConfig.family(base);
        if (settings == null || !settings.enabled) return of(base);
        chain = new Chain(base, general(base, settings, honoured(base, through)), through.clone());
        Chain raced = built.putIfAbsent(key, chain);
        return raced != null ? raced : chain;
    }

    /** A block's own chain's name: its family, then its list in order. */
    static String variantKey(SurfaceFamily base, SurfaceFamily[] through) {
        StringBuilder key = new StringBuilder(base.key());
        key.append('>');
        for (SurfaceFamily each : through) {
            key.append(each == null ? "?" : each.key())
                .append(',');
        }
        return key.toString();
    }

    /**
     * What a block's own list wears through to as the settings stand: the same rules {@code TrmtConfig.wearsThroughTo}
     * holds a family's list to. The player's switches first (the player beats data): grass's own switch for a grass
     * block, none for a sculk block, whose list answers to itself as sculk's does (0.9.229, W4), and the wholesale
     * switch for every other; then the block's own family, a family without stages and one switched off are passed
     * over, and a family named twice counts once.
     */
    static List<SurfaceFamily> honoured(SurfaceFamily base, SurfaceFamily[] through) {
        // Sculk's own list answers to itself, as its family's list does (TrmtConfig.wearsThroughTo; 0.9.229, W4).
        if (base == SurfaceFamily.GRASS ? !TrmtConfig.grassWearsThroughToDirt
            : base != SurfaceFamily.SCULK && !TrmtConfig.wearThroughToOtherSurfaces) {
            return Collections.emptyList();
        }
        List<SurfaceFamily> out = new ArrayList<SurfaceFamily>(through.length);
        for (SurfaceFamily named : through) {
            if (named == null || named == base || !named.staged) continue;
            FamilySettings target = TrmtConfig.family(named);
            if (target == null || !target.enabled) continue;
            if (!out.contains(named)) out.add(named);
        }
        return out;
    }

    /**
     * The lists data files give blocks of their own, by the family those blocks wear as, published by the edition's
     * surface table each time it is built or a server's is held (W3 T9). Each family a list names is remembered for
     * the session as one its blocks may have been worn into ({@link #canEverShow}).
     */
    public static void ownLists(Map<SurfaceFamily, List<SurfaceFamily[]>> byFamily) {
        Map<SurfaceFamily, List<SurfaceFamily[]>> kept = new EnumMap<SurfaceFamily, List<SurfaceFamily[]>>(
            SurfaceFamily.class);
        if (byFamily != null) {
            for (Map.Entry<SurfaceFamily, List<SurfaceFamily[]>> each : byFamily.entrySet()) {
                if (each.getKey() == null || each.getValue() == null) continue;
                List<SurfaceFamily[]> copies = new ArrayList<SurfaceFamily[]>();
                for (SurfaceFamily[] list : each.getValue()) {
                    if (list != null) copies.add(list.clone());
                }
                kept.put(each.getKey(), Collections.unmodifiableList(copies));
            }
        }
        synchronized (NAMING) {
            Map<SurfaceFamily, Set<SurfaceFamily>> grown = new EnumMap<SurfaceFamily, Set<SurfaceFamily>>(
                SurfaceFamily.class);
            for (Map.Entry<SurfaceFamily, Set<SurfaceFamily>> each : named.entrySet()) {
                grown.put(each.getKey(), EnumSet.copyOf(each.getValue()));
            }
            for (Map.Entry<SurfaceFamily, List<SurfaceFamily[]>> each : kept.entrySet()) {
                for (SurfaceFamily[] list : each.getValue()) {
                    for (SurfaceFamily family : list) {
                        if (family == null || family == each.getKey() || !family.staged) continue;
                        Set<SurfaceFamily> into = grown.get(each.getKey());
                        if (into == null) {
                            into = EnumSet.noneOf(SurfaceFamily.class);
                            grown.put(each.getKey(), into);
                        }
                        into.add(family);
                    }
                }
            }
            for (Map.Entry<SurfaceFamily, Set<SurfaceFamily>> each : grown.entrySet()) {
                each.setValue(Collections.unmodifiableSet(each.getValue()));
            }
            named = Collections.unmodifiableMap(grown);
        }
        lists = Collections.unmodifiableMap(kept);
    }

    /** Forgets every list, as a new session starts with none. For the suite, which runs many sessions in one. */
    static void forgetOwnLists() {
        synchronized (NAMING) {
            named = Collections.unmodifiableMap(new EnumMap<SurfaceFamily, Set<SurfaceFamily>>(SurfaceFamily.class));
        }
        lists = Collections.unmodifiableMap(new EnumMap<SurfaceFamily, List<SurfaceFamily[]>>(SurfaceFamily.class));
    }

    /**
     * Every appearance a family's ground can show as the chains now stand: its family chain's and each of its blocks'
     * own chains'. What the client plans a fallback picture for, so a block worn through to a surface its family never
     * reaches still has something to draw.
     */
    public static Set<SurfaceFamily> appearancesOf(SurfaceFamily base) {
        Set<SurfaceFamily> out = EnumSet.noneOf(SurfaceFamily.class);
        if (base == null) return out;
        of(base).collect(out);
        List<SurfaceFamily[]> own = lists.get(base);
        if (own != null) {
            for (SurfaceFamily[] list : own) of(base, list).collect(out);
        }
        return out;
    }

    /**
     * Keeps this mod's own chains as the player's settings build them, for a record worn along one of them to be
     * placed by {@link #reseatIndex} once Faithful Mode has replaced it. Called by the edition's Faithful Mode before
     * it writes its own numbers into the live settings, which is the last moment the player's are there to build from
     * (0.9.229 W5; Codex review R15). Kept once per settings read with Faithful Mode on, so they are the player's
     * numbers as that read found them.
     */
    public static void keepOwn() {
        Map<SurfaceFamily, short[]> kept = new EnumMap<SurfaceFamily, short[]>(SurfaceFamily.class);
        kept.putAll(own);
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged) continue;
            FamilySettings settings = TrmtConfig.family(family);
            if (settings == null || !settings.enabled) continue;
            short[] chain = general(family, settings);
            if (chain != null) kept.put(family, chain);
        }
        own = Collections.unmodifiableMap(kept);
    }

    /** This mod's own chain for a family, from its settings as they stand, or null for one with no steps. */
    private static short[] general(SurfaceFamily family, FamilySettings settings) {
        return general(family, settings, TrmtConfig.wearsThroughTo(family, true));
    }

    /**
     * This mod's own chain for a family's ground wearing through to these surfaces in turn: the family's own list, or a
     * block's ({@link #of(SurfaceFamily, SurfaceFamily[])}). Null for one with no steps.
     */
    private static short[] general(SurfaceFamily family, FamilySettings settings, List<SurfaceFamily> wearsThrough) {
        List<Short> steps = new ArrayList<Short>();

        // The surface as it stands. On real dirt the first layer is skipped, because a ghost
        // showing no wear at all is indistinguishable from the block it is covering; where
        // something has just worn off to expose it, that same layer is a real step.
        int first = family == SurfaceFamily.DIRT ? 1 : 0;
        for (int layer = first; layer < settings.stages; layer++) {
            steps.add(Short.valueOf(ErosionState.pack(family, layer, 0)));
        }

        // What the ground becomes once its own face is gone, in the order it gets there. An
        // empty list stands the family in for itself, which makes "nothing to wear through
        // into" and "wears through into something" one code path rather than two: grass with
        // its switch off then produces a chain that simply stops after its surface run,
        // exactly as it did when that case was written out by hand.
        List<SurfaceFamily> successors = wearsThrough;

        // How deep the run goes and how many layers a pixel of it is worth belong to the
        // family being worn, not to whatever it is exposing. Anything else quietly retires
        // this family's own two settings and lets a successor's numbers decide how long its
        // chain is - which would move every per-step cost and every heal time underneath it.
        // Grass is the one exception and the reason the borrow existed at all: it has no
        // depth of its own, because turf is a face rather than a substance, so its run has
        // always been measured in the earth's pixels. Which is also why turf is exempt from
        // the wholesale wear-through switch rather than gated by it: with no successor to
        // borrow from there is nothing to measure the run in, and the chain stops dead at the
        // end of the surface layers. See TrmtConfig.wearsThroughTo.
        //
        // The borrow rule, the depth clamp and the layer floor are asked of RunShape rather
        // than written out here, because the wear editor previews a run that has not been
        // built yet by the same three rules. Written twice, they drift, and the editor then
        // shows a run the engine never builds.
        FamilySettings shape = settings;
        if (RunShape.borrowsDepth(shape.maxSinkPixels, !successors.isEmpty())) {
            FamilySettings borrowed = TrmtConfig.family(successors.get(0));
            if (borrowed != null) shape = borrowed;
        }
        if (successors.isEmpty()) successors = Collections.singletonList(family);

        int deepest = RunShape.clampDepth(shape.maxSinkPixels);
        int perDepth = RunShape.layers(shape.layersPerDepth);
        for (int depth = 1; depth <= deepest; depth++) {
            SurfaceFamily sunkenAs = successors.get(sliceOf(depth, deepest, successors.size()));
            for (int layer = 0; layer < perDepth; layer++) {
                steps.add(Short.valueOf(ErosionState.pack(sunkenAs, layer, depth)));
            }
        }

        return finish(steps);
    }

    private static short[] finish(List<Short> steps) {
        if (steps.isEmpty()) return null;
        short[] chain = new short[steps.size()];
        for (int i = 0; i < chain.length; i++) {
            chain[i] = steps.get(i)
                .shortValue();
        }
        return chain;
    }

    /** The chain a base surface follows, or null when that family does not erode. */
    public static short[] forBase(SurfaceFamily base) {
        return chains.get(base);
    }

    public static int length(SurfaceFamily base) {
        short[] chain = chains.get(base);
        return chain == null ? 0 : chain.length;
    }

    /**
     * How far along a chain wear is allowed to go, which is not always all of it.
     *
     * <p>
     * The ceiling applies here rather than at each of the places wear happens, because "how worn
     * may this get" is a fact about the chain and not about who is doing the wearing. Traffic,
     * a blast, a hard landing and the tool all ask the same question and get the same answer.
     *
     * <p>
     * Never below one: a family capped at nothing would have no step to take at all, and a
     * position that can never show anything is indistinguishable from a family switched off,
     * which is what the family's own enabled setting is for.
     *
     * <p>
     * The arithmetic lives in {@link RunShape}, which the wear table and the editor call with
     * lengths that do not exist yet. One function for both is what keeps a previewed ceiling
     * where the engine puts it.
     */
    public static int cappedLength(SurfaceFamily base) {
        return cappedIn(base, length(base));
    }

    /** The ceiling's arithmetic, for a chain of this base and this length: the family's own and the world's. */
    private static int cappedIn(SurfaceFamily base, int full) {
        if (full <= 0) return 0;
        FamilySettings settings = TrmtConfig.family(base);
        float ceiling = RunShape.ceiling(TrmtConfig.maxWearFraction, settings == null ? 1f : settings.maxWear);
        return RunShape.capped(full, ceiling);
    }

    public static SurfaceFamily familyAt(SurfaceFamily base, int index) {
        return familyIn(chains.get(base), index);
    }

    private static SurfaceFamily familyIn(short[] chain, int index) {
        if (chain == null || index < 0 || index >= chain.length) return null;
        return ErosionState.familyOf(chain[index]);
    }

    public static int stageAt(SurfaceFamily base, int index) {
        return stageIn(chains.get(base), index);
    }

    private static int stageIn(short[] chain, int index) {
        if (chain == null || index < 0 || index >= chain.length) return -1;
        return ErosionState.layerOf(chain[index]);
    }

    /** How far the ground has physically dropped at this point on the chain. */
    public static int sinkAt(SurfaceFamily base, int index) {
        return sinkIn(chains.get(base), index);
    }

    private static int sinkIn(short[] chain, int index) {
        if (chain == null || index < 0 || index >= chain.length) return 0;
        return ErosionState.sinkOf(chain[index]);
    }

    /**
     * Where an appearance sits in a base's chain, or -1 when it does not belong to it.
     *
     * <p>
     * A -1 means the entry was written for a different surface than the one now in the
     * world - grass that has since been replaced with sand, or a stage that no longer exists
     * because the config lowered the family's stage count - and the caller should reset it
     * rather than trying to continue from a position that is no longer on the map.
     */
    public static int indexOf(SurfaceFamily base, SurfaceFamily appearance, int stage, int sink) {
        return indexIn(chains.get(base), appearance, stage, sink);
    }

    private static int indexIn(short[] chain, SurfaceFamily appearance, int stage, int sink) {
        if (chain == null || appearance == null || stage < 0) return -1;
        short want = ErosionState.pack(appearance, stage, sink);
        for (int i = 0; i < chain.length; i++) {
            if (chain[i] == want) return i;
        }
        return -1;
    }

    /**
     * Which of a family's successors owns one pixel of depth.
     *
     * <p>
     * Shared out as evenly as they divide, with the remainder going to the earliest rather than
     * the last: eight pixels between three of them is three, three, two. What a road shows most of
     * is the first thing it broke into, and the last is the floor of a rut few people ever wear
     * all the way down to.
     *
     * <p>
     * The run's length does not depend on this at all. Every depth carries the same number of
     * layers whoever owns it, so a family that gains successors keeps exactly the eighty steps it
     * had - which is what stops a setting meant only to change what the ground looks like from
     * moving every per-step cost and every heal time underneath it.
     */
    static int sliceOf(int depth, int deepest, int count) {
        if (count <= 1 || deepest <= 0) return 0;

        // Whole shares first, then the remainder one apiece from the front. Scaling the depth by
        // the count and dividing instead is shorter and gets the common cases right, but it hands
        // the remainder out by position rather than by rank: six pixels between four successors
        // comes out two, one, two, one, which is a rut that shows more of the third material than
        // of the second. What a road shows most of should be the first thing it broke into.
        //
        // A list longer than the run is the same rule taken to its end - the earliest own one
        // pixel each and the tail owns nothing, so a two-pixel stone rut is cobble and then grit
        // rather than cobble and then the earth underneath.
        int each = deepest / count;
        int spare = deepest % count;
        int through = 0;
        for (int i = 0; i < count; i++) {
            through += each + (i < spare ? 1 : 0);
            if (depth <= through) return i;
        }
        return count - 1;
    }

    /**
     * The last step of this chain that has not sunk at all, or -1 when it has none.
     *
     * <p>
     * The end of the run a surface takes on the face it still has, before the ground starts
     * dropping and the material underneath begins to show. It is where wear is held when something
     * planted is standing on the square: as worn as ground can look while still being that ground.
     */
    public static int lastFlatIndex(SurfaceFamily base) {
        return lastFlatIn(chains.get(base));
    }

    private static int lastFlatIn(short[] chain) {
        if (chain == null) return -1;
        int last = -1;
        for (int i = 0; i < chain.length; i++) {
            if (ErosionState.sinkOf(chain[i]) != 0) break;
            last = i;
        }
        return last;
    }

    /**
     * Whether a base surface has ever been able to show this appearance.
     *
     * <p>
     * Asked of the list as it is written rather than as it is currently switched on, because the
     * whole point of the question is ground recorded under a setting somebody has since changed.
     * A stone road showing gravel is still a stone road when the run that produced it is turned
     * off; what it must not be mistaken for is a record left behind by a block that has been dug
     * up and replaced with something else.
     *
     * <p>
     * A block of the family may have a list of its own from a data file (W3 T9), so every family such a list has named
     * this session counts too, whether or not the block's list still names it: ground worn on a block's own chain is
     * still that ground when a data read changes or takes away the list, as a family's is when its list is switched
     * off. A list taken away before this session began is not known here; the engine's reseat still keeps such a rut by
     * its depth ({@link Chain#reseatIndex}).
     */
    public static boolean canEverShow(SurfaceFamily base, SurfaceFamily appearance) {
        if (base == null || appearance == null) return false;
        if (base == appearance) return true;
        if (TrmtConfig.wearsThroughTo(base, false)
            .contains(appearance)) return true;
        Set<SurfaceFamily> own = named.get(base);
        return own != null && own.contains(appearance);
    }

    /**
     * The step this base's chain would use for an appearance that is no longer on it.
     *
     * <p>
     * Same depth, nearest layer. Depth is the part of a record you can read from across a field
     * and the part that took the longest to earn, so a change to the chain's shape should move the
     * picture and leave the rut where it is. Stone that gains a cobble run keeps its ruts and
     * starts showing cobble in them; stone that loses one keeps its ruts and goes back to showing
     * stone.
     *
     * <p>
     * Returns -1 for an appearance this base has never worn into, and for a depth its chain no
     * longer reaches. Both are the honest "this is not my record" that {@link #indexOf} gives, and
     * the caller should still act on it.
     *
     * <p>
     * Except across Faithful Mode's switch, which swaps one whole chain for another of a different shape rather than
     * reshaping one: there a record is placed by how far along its run it was. The original wears grass through to
     * level earth, this mod only by sinking, so by depth and layer alone grass worn to bare earth under Faithful Mode
     * went back to turf when it was switched off (0.9.229 W5; Codex review R15). A record whose state is on the chain
     * the switch replaced - the original's while it is off, this mod's own while it is on - goes to the same fraction
     * of the new one.
     */
    public static int reseatIndex(SurfaceFamily base, SurfaceFamily appearance, int stage, int sink) {
        return reseatIn(base, chains.get(base), null, false, appearance, stage, sink);
    }

    /**
     * @param byDepth whether a rut showing a surface no list known now names is still placed by its depth, at the
     *                deepest this chain reaches if it reaches no deeper (W3 T9; Codex review of T9): a block's list
     *                taken away between two sessions leaves its ruts showing a surface nothing names any more, and a
     *                rut is only ever made by wearing that very square, so its depth is kept rather than the record
     *                read as another surface's. Ground with no depth is still asked whether it can be this ground's.
     */
    private static int reseatIn(SurfaceFamily base, short[] chain, SurfaceFamily[] written, boolean byDepth,
        SurfaceFamily appearance, int stage, int sink) {
        if (chain == null || stage < 0 || appearance == null) return -1;
        int across = acrossTheSwitch(base, chain, ErosionState.pack(appearance, stage, sink));
        if (across >= 0) return across;
        int wantSink = ErosionState.clampSink(sink);
        if (!canEverShow(base, appearance) && !names(written, appearance)) {
            if (!byDepth || wantSink <= 0 || !appearance.staged) return -1;
            int deepest = 0;
            for (short step : chain) deepest = Math.max(deepest, ErosionState.sinkOf(step));
            if (deepest <= 0) return -1;
            wantSink = Math.min(wantSink, deepest);
        }

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < chain.length; i++) {
            if (ErosionState.sinkOf(chain[i]) != wantSink) continue;
            int distance = Math.abs(ErosionState.layerOf(chain[i]) - stage);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * Where a state on the chain Faithful Mode's switch replaced goes on the chain now in force: the same fraction of
     * the way along, rounded to the nearest step; -1 when it is on neither, or the base has no chain of the original's.
     */
    static int acrossTheSwitch(SurfaceFamily base, short[] chain, short state) {
        if (!FaithfulChains.has(base)) return -1;
        short[] replaced = FaithfulChains.wears(base) ? own.get(base) : FaithfulChains.chain(base);
        if (replaced == null || replaced.length == 0) return -1;
        for (int i = 0; i < replaced.length; i++) {
            if (replaced[i] != state) continue;
            if (replaced.length == 1 || chain.length == 1) return 0;
            return (int) Math.round(i * (chain.length - 1) / (double) (replaced.length - 1));
        }
        return -1;
    }

    /**
     * Where a record placed on one set of chains goes on the chains now in force (Codex review R17): its index on the
     * chain it was placed on, as the same fraction of this base's chain now, rounded to the nearest step. Read from the
     * chunk's own word of which chains that was ({@code ChunkErosionData.placedOn}), so a state both chains hold -
     * grass layers 0 to 4, dirt layers 1 to 4 at no depth - is read on the chain it was earned on and keeps its
     * fraction rather than its layer (Faithful grass layer 4 is 4/9 of its run, this mod's 4/79). -1 when nothing needs
     * placing: the chains are the same ones, the base has no chain of the original's, or the state is not on the chain
     * it was placed on (left to {@link #reseatIndex} and {@link #nearestIndex}, as before).
     */
    public static int placeAcross(SurfaceFamily base, byte placedOn, SurfaceFamily appearance, int stage, int sink) {
        return placeIn(base, chains.get(base), placedOn, appearance, stage, sink);
    }

    private static int placeIn(SurfaceFamily base, short[] chain, byte placedOn, SurfaceFamily appearance, int stage,
        int sink) {
        if (placedOn == placing || base == null || appearance == null || stage < 0) return -1;
        if (!FaithfulChains.has(base)) return -1;
        short[] from = placedOn == PLACED_FAITHFUL ? FaithfulChains.chain(base) : own.get(base);
        if (chain == null || from == null || from.length == 0) return -1;
        short state = ErosionState.pack(appearance, stage, sink);
        for (int i = 0; i < from.length; i++) {
            if (from[i] != state) continue;
            if (from.length == 1 || chain.length == 1) return 0;
            return (int) Math.round(i * (chain.length - 1) / (double) (from.length - 1));
        }
        return -1;
    }

    /**
     * Whose numbers price one step of this chain.
     *
     * <p>
     * A road that has worn through into another surface is still made of what it was made of, so
     * by default the base family pays for every step of its own run however far down that run a
     * position has been walked. The alternative - charging each step at the price of whatever it
     * is currently drawing as - is what the engine used to do, and it is what turns a stone road
     * into a two-thousand-crossing road the moment the grit it shed starts showing.
     */
    public static SurfaceFamily paceAt(SurfaceFamily base, int index) {
        return followsTheGround(base) ? base : familyAt(base, index);
    }

    /** The same question asked of an appearance already in hand rather than of an index. */
    public static SurfaceFamily pace(SurfaceFamily base, SurfaceFamily appearance) {
        return followsTheGround(base) ? base : appearance;
    }

    /**
     * Whether the ground a step is made of prices it rather than what it shows: as {@code wearPaceFollowsTheGround}
     * says, except on a chain of Faithful Mode's. The original draws every eroded block's threshold from its own
     * vanilla block's range, so worn grass that has become bare earth wears at dirt's price (0.9.229 W5, spec 2.2:
     * six draws from grass's range to bare earth, four from dirt's to the coarse end).
     */
    private static boolean followsTheGround(SurfaceFamily base) {
        return TrmtConfig.wearPaceFollowsTheGround && !FaithfulChains.wears(base);
    }

    /**
     * The step on this chain nearest to one that is no longer on it.
     *
     * <p>
     * A chain is not a constant. Change a family's gradation count, its depth, how many layers a
     * pixel of depth is worth, or whether grass is allowed to wear through into dirt, and every
     * chain is rebuilt from the new numbers - at which point a record written under the old shape
     * may name a step that no longer exists anywhere. Until this existed, such a record was read
     * as describing a different surface altogether and thrown away, which turned any edit to the
     * wear settings into a quiet deletion of every worn path in the world.
     *
     * <p>
     * Only steps drawn as the same appearance are considered, and that is what makes the answer
     * trustworthy rather than merely close: a record saying "dirt, four pixels down" is placed
     * among the dirt steps of the new chain and nowhere else, so a genuine change of surface -
     * grass grown back over a worn patch of dirt - still finds nothing and is still dropped.
     *
     * <p>
     * Depth is weighted far above gradation because they are not comparable quantities. A pixel
     * of depth is a whole run of gradations, so a step at the right depth and the wrong shade is
     * much nearer the truth than a step at the right shade and the wrong depth.
     *
     * @return the index of the nearest step, or -1 when this appearance is not on the chain at all
     */
    public static int nearestIndex(SurfaceFamily base, SurfaceFamily appearance, int stage, int sink) {
        return nearestIn(chains.get(base), appearance, stage, sink);
    }

    /**
     * The same search over a chain handed in rather than looked up.
     *
     * <p>
     * Split out for no reason but that the lookup needs a loaded config and this does not, which
     * is the difference between a rule that is tested and a rule that is only reasoned about.
     */
    static int nearestIn(short[] chain, SurfaceFamily appearance, int stage, int sink) {
        if (chain == null || appearance == null || stage < 0) return -1;

        int best = -1;
        int bestCost = Integer.MAX_VALUE;
        for (int i = 0; i < chain.length; i++) {
            if (ErosionState.familyOf(chain[i]) != appearance) continue;
            int cost = Math.abs(ErosionState.sinkOf(chain[i]) - sink) * DEPTH_WEIGHT
                + Math.abs(ErosionState.layerOf(chain[i]) - stage);
            if (cost < bestCost) {
                bestCost = cost;
                best = i;
            }
        }
        return best;
    }

    /** How much worse a pixel of depth is to be wrong about than a gradation. */
    private static final int DEPTH_WEIGHT = 64;

    /** Whether a block's own list, as written, names this family. */
    private static boolean names(SurfaceFamily[] written, SurfaceFamily family) {
        if (written == null || family == null) return false;
        for (SurfaceFamily each : written) {
            if (each == family) return true;
        }
        return false;
    }

    /**
     * One chain as a block follows it: its family's, or the block's own (trmtr-api, W3 T9;
     * {@link ErosionChain#of(SurfaceFamily, SurfaceFamily[])}). Asked everything the family-keyed readers above are
     * asked, with the same answers on a family's chain, so a caller that knows the block asks the block's chain and
     * nothing else changes. The family stays the family: its settings price, cap and heal every step, as on the
     * family's chain. Immutable; one is not kept past the question it was fetched for, because a rebuild replaces it.
     */
    public static final class Chain {

        /** No family: a chain with no steps. */
        static final Chain NOWHERE = new Chain(null, null, null);

        /** The family the block wears as. */
        public final SurfaceFamily base;

        private final short[] steps;

        /** The block's own list as written, or null on its family's chain. */
        private final SurfaceFamily[] written;

        Chain(SurfaceFamily base, short[] steps, SurfaceFamily[] written) {
            this.base = base;
            this.steps = steps;
            this.written = written;
        }

        /** Whether this is a block's own chain rather than its family's. */
        public boolean own() {
            return written != null;
        }

        /** The steps, as packed states, or null where the family does not erode. Not to be written to. */
        public short[] steps() {
            return steps;
        }

        public int length() {
            return steps == null ? 0 : steps.length;
        }

        /** As {@link ErosionChain#cappedLength}, on this chain. */
        public int cappedLength() {
            return cappedIn(base, length());
        }

        public SurfaceFamily familyAt(int index) {
            return familyIn(steps, index);
        }

        public int stageAt(int index) {
            return stageIn(steps, index);
        }

        public int sinkAt(int index) {
            return sinkIn(steps, index);
        }

        public int indexOf(SurfaceFamily appearance, int stage, int sink) {
            return indexIn(steps, appearance, stage, sink);
        }

        public int lastFlatIndex() {
            return lastFlatIn(steps);
        }

        /** As {@link ErosionChain#canEverShow}, with the block's own list as written as well. */
        public boolean canEverShow(SurfaceFamily appearance) {
            return ErosionChain.canEverShow(base, appearance) || base != null && names(written, appearance);
        }

        /**
         * As {@link ErosionChain#reseatIndex}, on this chain: a record made on the family's chain, or on another list
         * of the block's, is placed at its own depth and the nearest layer, showing what this chain shows there. A rut
         * showing a surface no list known this session names - a list taken away between two sessions - is placed by
         * its depth too, never dropped as another surface's.
         */
        public int reseatIndex(SurfaceFamily appearance, int stage, int sink) {
            if (base == null) return -1;
            return reseatIn(base, steps, written, true, appearance, stage, sink);
        }

        public int nearestIndex(SurfaceFamily appearance, int stage, int sink) {
            return nearestIn(steps, appearance, stage, sink);
        }

        /** As {@link ErosionChain#placeAcross}, onto this chain. */
        public int placeAcross(byte placedOn, SurfaceFamily appearance, int stage, int sink) {
            return placeIn(base, steps, placedOn, appearance, stage, sink);
        }

        public SurfaceFamily paceAt(int index) {
            return followsTheGround(base) ? base : familyAt(index);
        }

        public SurfaceFamily pace(SurfaceFamily appearance) {
            return followsTheGround(base) ? base : appearance;
        }

        /** Every appearance on this chain, into the set given. */
        void collect(Set<SurfaceFamily> into) {
            if (steps == null) return;
            for (short step : steps) {
                SurfaceFamily shown = ErosionState.familyOf(step);
                if (shown != null) into.add(shown);
            }
        }
    }
}
