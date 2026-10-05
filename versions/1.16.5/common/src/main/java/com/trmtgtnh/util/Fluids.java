package com.trmtgtnh.util;

import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.Trmt;

/**
 * What fluid a container holds, which the two loaders answer in entirely different ways.
 *
 * <p>
 * Reinforcement can be paid for with a bucket or a cell of something - a pack's concrete, most
 * often - and the settings name the fluid rather than the container, so that one entry covers every
 * mod's way of carrying it. Answering that needs a way to ask an arbitrary item what is inside it,
 * and this version has no such thing that both loaders share.
 *
 * <p>
 * <strong>Forge has a capability for exactly this and Fabric, at this version, has nothing.</strong>
 * Forge's fluid handler is asked of any item and answers for modded containers as readily as for a
 * bucket. Fabric's fluid transfer API arrives several versions later; here the only containers that
 * can be recognised are buckets, and the only way to read one is the field it keeps its fluid in. So
 * the Fabric side answers for buckets - including a mod's own, through an accessor - and for nothing
 * else, and says so once rather than quietly refusing a payment the settings allow.
 *
 * <p>
 * Unwired, nothing holds a fluid. That is the right answer on a dedicated server with no loader half
 * registered and during the moments before one is, and it costs only that a fluid entry does not
 * pay - never that a payment is taken twice or taken wrongly.
 */
public final class Fluids {

    /** What a loader module supplies: a way to ask a container what is in it. */
    public interface Side {

        /**
         * The registered name of the fluid this is a container of, or null for anything that is not
         * one.
         *
         * <p>
         * The name rather than the fluid, because that is what the settings hold and what this mod
         * compares; a loader that has a richer answer still has to say it in the one form both can.
         */
        String fluidNameOf(ItemStack stack);

        /** How much of it this holds, in the units a bucket is one thousand of. */
        int fluidAmountOf(ItemStack stack);

        /**
         * One bucket's worth taken out of a single container, and what the container has become.
         *
         * <p>
         * Null when the container holds no fluid at all, which is what tells a caller to ask the
         * second rule - whether the item declares a crafting remainder - rather than assume the
         * thing was used up. The empty stack when it held one and was consumed by the draining.
         *
         * @param one a stack of exactly one, which this may modify
         */
        ItemStack drained(ItemStack one);
    }

    /** What one bucket holds, which both loaders agree on and neither names the same way. */
    public static final int BUCKET = 1000;

    private static volatile Side side;

    private static volatile boolean complained;

    private Fluids() {}

    /** Tells this how to ask. Called by each loader's module as the mod starts. */
    public static void use(Side loaders) {
        side = loaders;
    }

    /** Forgets it again. For tests, which must not leak one into the next. */
    public static void forget() {
        side = null;
    }

    public static boolean wired() {
        return side != null;
    }

    /** The fluid this is a full or partial container of, by name, or null. */
    public static String nameOf(ItemStack stack) {
        Side at = reaching();
        return at == null || stack == null || stack.isEmpty() ? null : at.fluidNameOf(stack);
    }

    /** How much of it is in there. */
    public static int amountOf(ItemStack stack) {
        Side at = reaching();
        return at == null || stack == null || stack.isEmpty() ? 0 : at.fluidAmountOf(stack);
    }

    /** One bucket's worth out of this container, and what it has become. */
    public static ItemStack drained(ItemStack one) {
        Side at = reaching();
        return at == null || one == null || one.isEmpty() ? null : at.drained(one);
    }

    /**
     * Whether a settings entry names this fluid.
     *
     * <p>
     * Both older editions compare a bare name - "water", a pack's "concrete" - because that is what
     * a fluid was called there. A fluid has a full registry name here, so a settings file written
     * for either of them says half of one. Both spellings are accepted, which is what lets the same
     * file name the same fluid on all three editions.
     */
    public static boolean named(String entry, String registryName) {
        if (entry == null || registryName == null) return false;
        if (entry.equalsIgnoreCase(registryName)) return true;
        int colon = registryName.indexOf(':');
        return colon >= 0 && entry.equalsIgnoreCase(registryName.substring(colon + 1));
    }

    private static Side reaching() {
        Side at = side;
        if (at == null && !complained) {
            complained = true;
            Trmt.LOG.warn(
                "Something asked what fluid a container holds before this loader said how to ask, so "
                    + "the answer is none and a fluid entry in the reinforcement settings will not pay. "
                    + "This is said once.");
        }
        return at;
    }
}
