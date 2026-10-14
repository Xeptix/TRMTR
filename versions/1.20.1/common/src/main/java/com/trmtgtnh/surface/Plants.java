package com.trmtgtnh.surface;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether a block grows out of whatever is underneath it.
 *
 * <p>
 * Asked by the ground-cover detection, which needs to tell a tuft standing on the ground from a vine
 * hanging off a wall: a path worn under the first should bring it down, and a path worn under the
 * second has nothing to do with it. The two are on the same material and are otherwise hard to tell
 * apart.
 *
 * <p>
 * <strong>Forge has a precise answer and Fabric does not, and that asymmetry is the whole reason this
 * is a seam.</strong> Forge's {@code IPlantable} is its own word for exactly this relationship, which
 * is why the older editions ask it directly. Fabric has no equivalent, so its answer is vanilla's
 * {@code BushBlock} - the class every vanilla plant that needs ground extends, and which Forge's own
 * plants implement {@code IPlantable} on top of. So the two loaders agree about vanilla and differ
 * about a modded plant that extends neither.
 *
 * <p>
 * They differ in the safe direction. A plant neither test recognises is simply missed, and a missed
 * one is a decoration that survives a path being worn under it - where an over-reach is somebody's
 * block destroyed. The hand-written list under {@code surfaces} is there for anything detection turns
 * out to be too shy about, and it is the same list on both loaders.
 *
 * <p>
 * Unwired, nothing is a plant. That switches ground cover off rather than breaking it, and it says so
 * once: see {@link com.trmtgtnh.ModsPresent} for why the seams in this mod complain rather than
 * failing silently.
 */
public final class Plants {

    /** What a loader module supplies: its own word for "this grows out of the ground". */
    public interface Test {

        boolean growsOnGround(BlockState state);
    }

    private static volatile Test test;

    private static volatile boolean complained;

    private Plants() {}

    /** Tells this how to decide. Called once by each loader module as the mod starts. */
    public static void use(Test loaders) {
        test = loaders;
    }

    /** Forgets it again. For tests, which must not leak an answer into the next one. */
    public static void forget() {
        test = null;
        complained = false;
    }

    /** Whether a loader has told this how to answer yet. */
    public static boolean wired() {
        return test != null;
    }

    public static boolean growsOnGround(BlockState state) {
        Test asking = test;
        if (asking == null) {
            if (!complained) {
                complained = true;
                com.trmtgtnh.Trmt.LOG.warn(
                    "Nothing has told this edition how to recognise a plant, so ground cover will "
                        + "find none. The loader module should call Plants.use() as it starts; this "
                        + "is said once.");
            }
            return false;
        }
        return state != null && asking.growsOnGround(state);
    }
}
