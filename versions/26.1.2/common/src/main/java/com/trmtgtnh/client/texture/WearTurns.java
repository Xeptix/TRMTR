package com.trmtgtnh.client.texture;

/**
 * How a worn block's rotation is shown: client.wearTurns (0.9.229; Xep, 2026-11-16,
 * docs/goals/0.9.229-rotation-spec.md:
 * "Turn and re-seed", "Build it").
 *
 * <ul>
 * <li>{@link #BOTH}, the default: four differently seeded wear patterns, and each drawn turned by its position's own
 * quarter turn, so four patterns sit at four angles;</li>
 * <li>{@link #TURN}: one pattern per gradation, turned four ways where it is drawn, a quarter of the atlas room for the
 * faces that turn;</li>
 * <li>{@link #RESEED}: the seeded patterns, never turned, which is how every worn block drew before 0.9.229.</li>
 * </ul>
 *
 * Only a face that turns ({@link FaceDirection}) is ever turned or held to one pattern; every other face keeps its
 * seeded patterns in all three. The turn is the whole face picture, the block's own texture and its wear together,
 * because the wear follows the stones and cannot turn on its own.
 *
 * <p>
 * Free of the game and the same file in every edition (CoreStaysPortableTest).
 */
public final class WearTurns {

    private WearTurns() {}

    public static final String BOTH = "both";

    public static final String TURN = "turn";

    public static final String RESEED = "reseed";

    /** The chooser's values, in the order it offers them. */
    public static final String[] MODES = { BOTH, TURN, RESEED };

    /** The setting's value as one of {@link #MODES}; anything else is the default, {@link #BOTH}. */
    public static String mode(String setting) {
        if (setting != null) {
            String trimmed = setting.trim();
            for (String mode : MODES) {
                if (mode.equalsIgnoreCase(trimmed)) return mode;
            }
        }
        return BOTH;
    }

    /** How many seeded patterns a set is drawn with: one for a face that turns under {@link #TURN}, else all. */
    public static int seeds(String mode, boolean turns, int rotations) {
        int all = Math.max(1, rotations);
        return TURN.equals(mode(mode)) && turns ? 1 : all;
    }

    /**
     * Which seeded pattern a position draws: its rotation folded onto the patterns drawn, or the one pattern a face
     * that turns has under {@link #TURN}.
     */
    public static int seed(String mode, boolean turns, int rotation, int rotations) {
        int drawn = seeds(mode, turns, rotations);
        return drawn <= 1 ? 0 : (rotation & 3) % drawn;
    }

    /** Quarter turns clockwise, seen from above with north up, that a position's face is drawn turned by: 0 to 3. */
    public static int quarterTurns(String mode, boolean turns, int rotation) {
        if (!turns || RESEED.equals(mode(mode))) return 0;
        return rotation & 3;
    }

    // ------------------------------------------------------------------------------------------------------------
    // When the turn starts: client.wearTurnStart (0.9.229; Xep, 2026-11-16, docs/goals/0.9.229-rotation-spec.md,
    // "When the turn starts")
    // ------------------------------------------------------------------------------------------------------------

    /**
     * The default: before the first pixel of sinking a worn block keeps exactly the turn the game gives the unworn
     * block at that position - none in 1.7.10, which turns no block's top, and the game's own random turn in the newer
     * editions where the block's model has one (dirt, sand, grass tops and the like) - and from the first pixel of
     * sinking each depth takes the position-and-depth turn.
     */
    public static final String START_GAME = "game";

    /** Texture-only stages stay unturned, as the unworn block is drawn in 1.7.10; the turn starts at the first sink. */
    public static final String START_SINK = "sink";

    /** The turn appears at the first stage of wear, sunk or not: how every worn block was turned before this. */
    public static final String START_FIRST = "first";

    /** The chooser's values, in the order it offers them, the default first. */
    public static final String[] STARTS = { START_GAME, START_SINK, START_FIRST };

    /** The setting's value as one of {@link #STARTS}; anything else is the default, {@link #START_GAME}. */
    public static String start(String setting) {
        if (setting != null) {
            String trimmed = setting.trim();
            for (String start : STARTS) {
                if (start.equalsIgnoreCase(trimmed)) return start;
            }
        }
        return START_GAME;
    }

    /**
     * The quarter turns a face is drawn turned by, told when the turn starts: as {@link #quarterTurns(String, boolean,
     * int)} once the block has sunk ({@code depth} pixels, above nought); before that, the position's turn under
     * {@link #START_FIRST}, none under {@link #START_SINK}, and under {@link #START_GAME} the turn the game itself
     * gives the unworn block there, {@code gameTurn} (nought where the game turns nothing).
     */
    public static int quarterTurns(String mode, boolean turns, int rotation, String start, int depth, int gameTurn) {
        if (!turns || RESEED.equals(mode(mode))) return 0;
        if (depth > 0) return rotation & 3;
        String when = start(start);
        if (START_FIRST.equals(when)) return rotation & 3;
        if (START_SINK.equals(when)) return 0;
        return gameTurn & 3;
    }

    /**
     * The quarter turns clockwise, seen from above with north up, a top face is drawn turned by, read from where its
     * north-west corner's vertex samples its picture: {@code u} and {@code v} that sample as fractions of the picture,
     * 0 at its left and top. Unturned, that corner samples the picture's top left; a quarter turn the bottom left, a
     * half the bottom right and three quarters the top right - the same turns the ghost's own top is laid out by. How
     * the newer editions read the game's own turn of a block from the face its model gives at a position. The nearest
     * corner answers, so a sample a little inside its corner, as a model's own UVs often are, reads the same.
     */
    public static int turnOfCorner(float u, float v) {
        boolean right = u >= 0.5F;
        boolean bottom = v >= 0.5F;
        if (!right && !bottom) return 0;
        if (!right) return 1;
        if (bottom) return 2;
        return 3;
    }
}
