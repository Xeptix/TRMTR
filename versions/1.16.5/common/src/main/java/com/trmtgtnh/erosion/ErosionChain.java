package com.trmtgtnh.erosion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The ordered list of appearances a surface passes through as it wears, and the reverse
 * order it recovers along.
 *
 * <p>
 * Upstream models this as a sequence of real block replacements: grass_block becomes
 * eroded_grass_block s0..s4, then eroded_dirt, then eroded_coarse_dirt. Here nothing is
 * replaced — the block in the world stays exactly the grass it always was — so the chain
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

    private ErosionChain() {}

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
     * first run is longer because that is the wear people actually look at — a track appearing
     * in turf — and the later ones are shorter because by then the interesting part is the rut.
     */
    public static void rebuild() {
        Map<SurfaceFamily, short[]> built = new EnumMap<SurfaceFamily, short[]>(SurfaceFamily.class);

        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged) continue;
            FamilySettings settings = TrmtConfig.family(family);
            if (settings == null || !settings.enabled) continue;

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
            List<SurfaceFamily> successors = TrmtConfig.wearsThroughTo(family, true);

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

            finish(built, family, steps);
        }

        chains = Collections.unmodifiableMap(built);
    }

    private static void finish(Map<SurfaceFamily, short[]> built, SurfaceFamily family, List<Short> steps) {
        if (steps.isEmpty()) return;
        short[] chain = new short[steps.size()];
        for (int i = 0; i < chain.length; i++) {
            chain[i] = steps.get(i)
                .shortValue();
        }
        built.put(family, chain);
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
        int full = length(base);
        if (full <= 0) return 0;
        FamilySettings settings = TrmtConfig.family(base);
        float ceiling = RunShape.ceiling(TrmtConfig.maxWearFraction, settings == null ? 1f : settings.maxWear);
        return RunShape.capped(full, ceiling);
    }

    public static SurfaceFamily familyAt(SurfaceFamily base, int index) {
        short[] chain = chains.get(base);
        if (chain == null || index < 0 || index >= chain.length) return null;
        return ErosionState.familyOf(chain[index]);
    }

    public static int stageAt(SurfaceFamily base, int index) {
        short[] chain = chains.get(base);
        if (chain == null || index < 0 || index >= chain.length) return -1;
        return ErosionState.layerOf(chain[index]);
    }

    /** How far the ground has physically dropped at this point on the chain. */
    public static int sinkAt(SurfaceFamily base, int index) {
        short[] chain = chains.get(base);
        if (chain == null || index < 0 || index >= chain.length) return 0;
        return ErosionState.sinkOf(chain[index]);
    }

    /**
     * Where an appearance sits in a base's chain, or -1 when it does not belong to it.
     *
     * <p>
     * A -1 means the entry was written for a different surface than the one now in the
     * world — grass that has since been replaced with sand, or a stage that no longer exists
     * because the config lowered the family's stage count — and the caller should reset it
     * rather than trying to continue from a position that is no longer on the map.
     */
    public static int indexOf(SurfaceFamily base, SurfaceFamily appearance, int stage, int sink) {
        short[] chain = chains.get(base);
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
        short[] chain = chains.get(base);
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
     */
    public static boolean canEverShow(SurfaceFamily base, SurfaceFamily appearance) {
        if (base == null || appearance == null) return false;
        if (base == appearance) return true;
        return TrmtConfig.wearsThroughTo(base, false)
            .contains(appearance);
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
     */
    public static int reseatIndex(SurfaceFamily base, SurfaceFamily appearance, int stage, int sink) {
        short[] chain = chains.get(base);
        if (chain == null || stage < 0 || !canEverShow(base, appearance)) return -1;

        int wantSink = ErosionState.clampSink(sink);
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
        return TrmtConfig.wearPaceFollowsTheGround ? base : familyAt(base, index);
    }

    /** The same question asked of an appearance already in hand rather than of an index. */
    public static SurfaceFamily pace(SurfaceFamily base, SurfaceFamily appearance) {
        return TrmtConfig.wearPaceFollowsTheGround ? base : appearance;
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

}
