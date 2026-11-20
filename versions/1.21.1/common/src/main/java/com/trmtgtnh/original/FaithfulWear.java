package com.trmtgtnh.original;

/**
 * How hard one step wears grass, dirt, sand and leaves under Faithful Mode, by the original mod's own rules
 * (docs/goals/0.9.229-w5-spec.md, 2.3; docs/goals/0.9.229-w5-original.md, section 5).
 *
 * <p>
 * The original wears only for two kinds of walker. A player adds {@code player} to the square stood on, times
 * {@code mounted} when riding, a fifth of that to the square ahead and half of it to each square beside, and nothing
 * behind. A mob on a lead adds {@code player * leash} to the square it stands on and nothing anywhere else. Any other
 * mob never wears anything. There is no cooldown and no fading in the original: a step counts each time a walker
 * arrives on a new square, the rule this mod already shares, and a count once made is never forgotten. The edition's
 * engine asks this for Faithful Mode's five families and keeps its own figures for the other seven.
 *
 * <p>
 * Pure, so it is the same file in all eight editions and its arithmetic is tried without a game.
 */
public final class FaithfulWear {

    /** The original's share of a step on the square ahead of a player (its {@code ldc 0.2f}). */
    public static final float AHEAD = 0.2f;

    /** The original's share of a step on each square beside a player (its {@code ldc 0.5f}, twice). */
    public static final float ASIDE = 0.5f;

    /** The three kinds of walker the original tells apart. */
    public enum Mover {
        /** A player, on foot or riding. */
        PLAYER,
        /** A mob on a lead. */
        LEASHED,
        /** Any other mob, which the original never lets wear anything. */
        LOOSE
    }

    private FaithfulWear() {}

    /**
     * What one step adds to the square this walker stands on.
     *
     * @param player  the original's {@code erosionMultipliers.player}
     * @param mounted the original's {@code erosionMultipliers.mounted}, applied to a riding player only
     * @param leashed the original's {@code erosionMultipliers.leash}, applied to a mob on a lead only
     */
    public static float underfoot(Mover mover, boolean riding, double player, double mounted, double leashed) {
        if (mover == Mover.PLAYER) return (float) (riding ? player * mounted : player);
        if (mover == Mover.LEASHED) return (float) (player * leashed);
        return 0f;
    }

    /** What the square ahead takes from a step worth {@code underfoot}: a fifth of it from a player, else nothing. */
    public static float ahead(Mover mover, float underfoot) {
        return spreads(mover) ? underfoot * AHEAD : 0f;
    }

    /** What each square beside takes from a step worth {@code underfoot}: half of it from a player, else nothing. */
    public static float aside(Mover mover, float underfoot) {
        return spreads(mover) ? underfoot * ASIDE : 0f;
    }

    /** Whether this walker's steps reach past its own square: only a player's do. */
    public static boolean spreads(Mover mover) {
        return mover == Mover.PLAYER;
    }
}
