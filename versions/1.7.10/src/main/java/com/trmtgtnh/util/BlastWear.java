package com.trmtgtnh.util;

/**
 * How hard a blast wears the ground, and how far (0.9.228; Xep, 2026-10-22, from the blast photographs: "more like 2-5
 * blocks away with the damage sloping off the further it gets", and the crater's own floor worn "starting from the
 * epicenter and tapering off at the edges with some randomness to it" - docs/goals/0.9.228-decisions.md, "Blast wear,
 * from the blast photographs").
 *
 * <p>
 * Three numbers, each the same in every edition: how far from the middle the scouring reaches, how hard it is at the
 * middle, and what share of that a position at some distance takes, roughed by a roll of the world's own random. The
 * engine walks the sphere and hands each position to its ordinary step, after the blast has taken its blocks, so the
 * crater's bared floor and walls are worn by the same rule as the ground around it; everything that decides whether a
 * position can wear at all - pins, shelter, reinforcement, the per-chunk cap, unloaded chunks, the dimension list -
 * stays in the step. Plain Java, on the list CoreStaysPortableTest keeps free of the game, so all eight editions wear a
 * crater by one arithmetic.
 *
 * <p>
 * Why these numbers. A creeper's blast (strength three) on the flat test ground takes 47 to 50 of the 81 squares around
 * it in every edition: a disc about 3.9 blocks across from its middle, and a stick of dynamite (four) about 5.2. Until
 * 0.9.228 the reach was the blast's radius times {@code explosionReach}, six blocks for a creeper at the default of
 * two, so the fall-off had all but run out at the crater's lip and the photographs showed worn ground one, sometimes
 * two, blocks past it. The reach now runs that far past the blast's own radius instead - nine blocks for a creeper,
 * twelve for dynamite, at the same setting - and the share still falls in a straight line from the middle, so an
 * existing config gets the new shape with no setting moved. The share falls along a gentle curve rather than a straight
 * line, and the default strength is 168 (140 until Xep read the photographs: "I also wouldn't mind the blasts being
 * about 20% stronger overall as far as wear goes"). A creeper's crater then leaves its lip about two thirds as hard as
 * its middle, ground two blocks past it a whole gradation of grass even at the dearest draw and the lowest roll, and
 * less than a step of untouched grass five blocks past it: worn ground ends four to five blocks out, ragged.
 */
public final class BlastWear {

    /**
     * How far a roll moves one position's wear either way, as a share of it: a half, so the same distance takes
     * between a half and one and a half of its share. A quarter until Xep saw it photographed (2026-10-25, "More ragged
     * edge"): the worn edge is ragged rather than a circle, a block or two either way where the share is small, and a
     * square at the lip can now outwear one on the crater's floor - the floor is still worn hardest on the whole.
     */
    public static final float RAGGED = 0.5f;

    private BlastWear() {}

    /**
     * How far from the middle a blast of this radius scours, in blocks: its own radius and that again times
     * {@code reachSetting} past it, held under {@code maxRadius} and under the radius whose sphere holds
     * {@code maxPositions} positions. Nought for a blast of no radius.
     *
     * <p>
     * The work bound is a sphere's, because the engine walks the whole sphere inside the tick that set the charge off,
     * and a very large figure would stall the server however sensible the distance looked. Pulling the reach in is the
     * right way to lose it: what goes is the outermost ring, where the wear had already faded.
     */
    public static float reach(float radius, float reachSetting, float maxRadius, int maxPositions) {
        if (!(radius > 0f)) return 0f;
        float past = reachSetting > 0f ? reachSetting : 0f;
        float reach = radius + radius * past;
        if (reach > maxRadius) reach = maxRadius;
        double affordable = Math.cbrt(maxPositions * 3.0D / (4.0D * Math.PI));
        if (reach > affordable) reach = (float) affordable;
        return reach > 0f ? reach : 0f;
    }

    /**
     * How hard the ground is scoured at the very middle, in a footstep's units: the configured strength, quoted
     * against the reference blast and in proportion to this one, so a creeper scuffs less than a stick of dynamite and
     * a mining charge more. Nought for a blast of no radius or a reference of none.
     */
    public static float centre(float radius, float strength, float reference) {
        if (!(radius > 0f) || !(reference > 0f) || !(strength > 0f)) return 0f;
        return strength * (radius / reference);
    }

    /**
     * How the share bends on its way out: what is left of the distance to the reach, raised to this. One was a straight
     * line, which Xep read in the photographs as a hard blast one block round the crater and then a hard taper
     * (2026-10-25: "If we can make it a little more gradual, that would be great"); below one the curve holds up
     * further out, so the ground three and four blocks past a creeper's crater takes about twice what the line gave
     * it, and still comes to nothing at the reach.
     */
    public static final double FALL = 0.7D;

    /**
     * The share of the middle's wear a position this far from the middle takes: all of it at the middle, falling to
     * nothing at the reach along {@link #FALL}'s curve, and nothing beyond. Never square-law: the real thing falls off
     * faster, but a scuff that is invisible two blocks out is one nobody sees, and what this is for is the crater and
     * the ring of scoured ground around it.
     */
    public static float share(double distance, float reach) {
        if (!(reach > 0f) || distance >= reach) return 0f;
        if (distance <= 0D) return 1f;
        return (float) Math.pow(1D - distance / reach, FALL);
    }

    /**
     * What one position's roll makes of its share: between {@code 1 - RAGGED} and {@code 1 + RAGGED}, one at a roll of
     * a half. The roll is the world's own random, nought up to one; anything outside that is held to it.
     */
    public static float ragged(float roll) {
        float held = roll < 0f ? 0f : (roll > 1f ? 1f : roll);
        if (held != held) held = 0.5f;
        return 1f + RAGGED * (2f * held - 1f);
    }

    /** The wear one position takes from a blast: the middle's, its share at this distance, and its roll. */
    public static float amount(float centre, double distance, float reach, float roll) {
        float share = share(distance, reach);
        if (share <= 0f || !(centre > 0f)) return 0f;
        return centre * share * ragged(roll);
    }
}
