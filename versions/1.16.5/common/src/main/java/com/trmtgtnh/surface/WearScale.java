package com.trmtgtnh.surface;

/**
 * How finely wear is counted for the purpose of choosing a picture, as opposed to how many
 * gradations a record can hold.
 *
 * <p>
 * These are two different numbers that shared one constant for a long time, and the sharing went
 * unnoticed because both happened to be sixteen. {@link SurfaceFamily#MAX_STAGES} is a fact about
 * what is written down: the layer a ghost block keeps in its own four bits of metadata, and the
 * layer an erosion record keeps in its five. The number below is a fact about what is drawn. It
 * lives on the client, is never persisted, never sent and never read back, and can therefore be
 * whatever the block atlas will hold.
 *
 * <p>
 * Nothing here is part of any format. Changing it changes how finely wear is drawn at the next
 * stitch and nothing else at all - no save migrates, no packet moves, and ground goes on wearing at
 * exactly the rate its family's settings say.
 */
public final class WearScale {

    /**
     * How many positions a wear chain is counted in when deciding which picture to draw.
     *
     * <p>
     * Eighty, because that is the run the shipped defaults actually produce: sixteen gradations on
     * the face as it stands, then a pixel of depth and eight more gradations, eight times over. The
     * comment over {@code FamilySettings.defaultsFor} describes that shape and names the same
     * figure. So at the defaults every one of a chain's eighty steps can be given a picture of its
     * own, and each further crossing that wears the ground changes what is on it.
     *
     * <p>
     * A literal rather than the longest chain a family could be configured to have. That bound is
     * sixteen gradations plus fifteen pixels of depth at sixteen gradations each, which is two
     * hundred and fifty-six; at four rotations across the six hundred-odd surface appearances a
     * large pack registers, that is over six hundred thousand sprites against the two hundred and
     * sixty-two thousand a maximum-sized atlas holds. A computed ceiling would be one nothing could
     * ever draw, and it would move the config screen's own maximum under a player every time
     * somebody edited a family.
     *
     * <p>
     * Nor is it computed from the live chain, which differs per family - grass has no depth so its
     * chain is sixteen, ice sinks half as far so its is forty-eight - while the drawn table has one
     * stride for all of them. A family whose chain is longer than this simply has consecutive steps
     * that share a picture, which is what every family has today.
     */
    public static final int COUNTED_STEPS = 80;

    private WearScale() {}
}
