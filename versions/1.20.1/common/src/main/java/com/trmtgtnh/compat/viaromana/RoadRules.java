package com.trmtgtnh.compat.viaromana;

import com.trmtgtnh.erosion.ErosionState;

/**
 * The arithmetic of the Via Romana support, apart from the game and from Via Romana (0.9.228; Xep's decision of
 * 2026-10-20, docs/goals/0.9.228-decisions.md in the 1.7.10 tree, "Via Romana").
 *
 * <p>
 * Three answers, one per part of the decision, each a pure function of the numbers handed in so the suite asks them
 * directly: whether a worn square is worn far enough to count as road, how much wear a fast trip lays on each square of
 * the road it went along, and what a square on a charted road pays for each gradation it heals. Everything that reads a
 * world, a setting or Via Romana's own classes lives beside this and hands the numbers in.
 */
public final class RoadRules {

    private RoadRules() {}

    /**
     * The gradation, counted from 1 along a surface's wear run, from which a worn square counts as road.
     *
     * <p>
     * {@code setting} above nought is taken as it stands. Nought, the default, is the gradation at which the square is
     * drawn as bare path: the first step of its run that has sunk, where the face's own run is used up - for grass the
     * first gradation of the earth beneath it, because the turf's coverage curve ends nearly bare and hands over to
     * earth there (WearPatterns.GRASS_COVERAGE). A run that never sinks has no such step, and so does one a wear ceiling
     * stops short of it; there the square is as bare as it may get at the last gradation it can reach, and that is the
     * one used. Above the reachable run is never reached, which is what a number past it means.
     *
     * @param chain   the base surface's whole chain (ErosionChain.forBase), or null where it does not wear
     * @param capped  how far along that chain wear may go (ErosionChain.cappedLength)
     * @param setting integration.viaRomanaRoadGradation
     * @return the gradation, or {@link Integer#MAX_VALUE} where nothing on this surface can count
     */
    public static int roadGradation(short[] chain, int capped, int setting) {
        if (chain == null || chain.length == 0 || capped <= 0) return Integer.MAX_VALUE;
        if (setting > 0) return setting;
        int reachable = Math.min(capped, chain.length);
        for (int i = 0; i < reachable; i++) {
            if (ErosionState.sinkOf(chain[i]) > 0) return i + 1;
        }
        return reachable;
    }

    /**
     * Whether a square whose record is this counts as a Via Romana road block.
     *
     * <p>
     * The record is placed on the chain by its family, layer and depth alone - the pin, the reinforcement and the other
     * bits a record carries say nothing about how worn it is - and a record the chain has no step for (a surface since
     * replaced, a chain rebuilt under it) counts as nothing, as it is drawn as nothing either.
     */
    public static boolean counts(short[] chain, int capped, int setting, short record) {
        if (chain == null || record == ErosionState.NONE || ErosionState.layerOf(record) < 0) return false;
        short want = ErosionState
            .pack(ErosionState.familyOf(record), ErosionState.layerOf(record), ErosionState.sinkOf(record));
        for (int i = 0; i < chain.length; i++) {
            if (chain[i] == want) return i + 1 >= roadGradation(chain, capped, setting);
        }
        return false;
    }

    /**
     * How much wear a fast trip lays on each square of the road it went along: so many ordinary player steps, or
     * nothing with the switch off.
     *
     * @param on         integration.viaRomanaTripWear
     * @param steps      integration.viaRomanaTripSteps, a multiple of one player's step
     * @param playerStep what one ordinary step by this player weighs (multipliers.player, nought for a player who
     *                   wears nothing)
     */
    public static float tripAmount(boolean on, double steps, float playerStep) {
        if (!on || steps <= 0d || playerStep <= 0f) return 0f;
        return (float) (steps * playerStep);
    }

    /**
     * The price in days of one gradation healed on a charted road: the ordinary price over the road's speed, so half
     * speed costs twice the days. Off a road the speed is one and the price is the ordinary one; at nought nothing is
     * ever affordable.
     *
     * @param pace integration.viaRomanaRoadHealing where the square is on a charted road, one where it is not
     */
    public static double chartedPrice(double cost, double pace) {
        if (pace >= 1d) return cost;
        if (pace <= 0d) return Double.POSITIVE_INFINITY;
        return cost / pace;
    }

    /** The partial wear a charted square sheds a day: the ordinary rate times the road's speed, never more. */
    public static double chartedBleed(double perDay, double pace) {
        if (pace >= 1d) return perDay;
        if (pace <= 0d) return 0d;
        return perDay * pace;
    }

    /** Whether a square at this speed never heals at all - held against time as a pinned square is. */
    public static boolean neverHeals(double pace) {
        return pace <= 0d;
    }
}
