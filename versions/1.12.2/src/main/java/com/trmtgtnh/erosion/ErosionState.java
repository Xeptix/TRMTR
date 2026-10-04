package com.trmtgtnh.erosion;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * How worn one position is, packed into a short.
 *
 * <p>
 * Wear has two independent parts. A surface picks up <em>visual layers</em> — scuffing that
 * shows but does not move anything — and when it runs out of those, it drops a pixel and starts
 * a fresh run of layers on the newly exposed material. So a position is a layer <em>and</em> a
 * depth, and neither implies the other: layer 3 happens once at every depth the ground reaches.
 *
 * <p>
 * That is why this is a short rather than the byte it used to be. The old record could only say
 * which stage a block was at, so depth had to be inferred from the stage's position in the wear
 * chain — which stops working the moment the same layer occurs at nine different depths, because
 * the lookup finds the first one and a block eight pixels down would jump back to one pixel down
 * on its next step.
 *
 * <pre>
 * bit  15 14 13 12 | 11 10  9  8 |  7  6  5  4  3 |  2  1  0
 *      [ r  r  F  H] [ sink 0-8  ] [ layer + 1     ] [ family ]
 *                F = frozen: pinned against wear and healing alike
 *                H = the family ordinal's fourth bit, worth eight
 * </pre>
 *
 * <p>
 * Two properties are deliberate. Zero still means "nothing here", because a visible record
 * always has a non-zero biased layer — the removal sentinel the delta packet relies on is
 * unchanged. And the low byte is bit-identical to the byte the previous version wrote for any
 * position that had not yet sunk, which is what lets the old format be read without guessing.
 */
public final class ErosionState {

    /** Nothing worn here. Also the removal sentinel on the wire. */
    public static final short NONE = 0;

    private static final int FAMILY_MASK = 0x7;

    /**
     * The family ordinal's high bit, which is what lets there be more than eight families.
     *
     * <p>
     * Deliberately not adjacent to the low three. Widening the field in place would move the
     * layer and depth along with it and break every record ever written; parked up here in
     * reserve, a record from before there were nine families has a zero there and reads back as
     * exactly the family it always was. Nothing migrates and no format number moves.
     */
    private static final int FAMILY_HIGH_MASK = 0x1000;

    private static final int FAMILY_HIGH_VALUE = 8;
    private static final int LAYER_SHIFT = 3;
    private static final int LAYER_MASK = 0x1F;
    private static final int SINK_SHIFT = 8;
    private static final int SINK_MASK = 0xF;
    /**
     * Pinned: neither wear nor healing may move this position until it is released.
     *
     * <p>
     * Bit 13 rather than 12 because a test pins bit 12 as the one no version understands, and
     * claiming it would mean editing the guarantee in the same change that spends it. Nothing
     * has ever written any of these four, so every record already on disk reads back through
     * the narrowed mask as exactly the same family, layer and depth, and no format number moves.
     */
    private static final int FROZEN_MASK = 0x2000;

    /**
     * The reinforcement level, 0-3, in the top two bits. Formerly reserved; a record from before
     * this existed has zero there and reads back as unreinforced, so no format number changed.
     */
    private static final int REINFORCE_MASK = 0xC000;
    private static final int REINFORCE_SHIFT = 14;

    private ErosionState() {}

    public static short pack(SurfaceFamily family, int layer, int sink) {
        return pack(family, layer, sink, false);
    }

    public static short pack(SurfaceFamily family, int layer, int sink, boolean frozen) {
        return pack(family, layer, sink, frozen, 0);
    }

    public static short pack(SurfaceFamily family, int layer, int sink, boolean frozen, int reinforce) {
        if (family == null && reinforce <= 0) return NONE;
        int ordinal = family == null ? 0 : family.ordinal();
        int packed = ordinal & FAMILY_MASK;
        if (ordinal >= FAMILY_HIGH_VALUE) packed |= FAMILY_HIGH_MASK;
        packed |= ((layer + 1) & LAYER_MASK) << LAYER_SHIFT;
        packed |= (clampSink(sink) & SINK_MASK) << SINK_SHIFT;
        // Only on a record that is actually showing something. A pin on an invisible entry
        // would never reach a client, would be pruned at the next save, and would make the
        // zero that means "nothing here" ambiguous.
        if (frozen && layer >= 0) packed |= FROZEN_MASK;
        // Reinforcement rides even on an invisible record, because a reinforced block need not
        // be a worn one - that is the whole point of it.
        packed |= (Math.max(0, Math.min(3, reinforce)) & 0x3) << REINFORCE_SHIFT;
        return (short) packed;
    }

    /** The reinforcement level packed in this record, 0-3. */
    public static int reinforceOf(short state) {
        return ((state & REINFORCE_MASK) >>> REINFORCE_SHIFT) & 0x3;
    }

    /** True when this position is pinned against both wear and healing. */
    public static boolean frozenOf(short state) {
        return (state & FROZEN_MASK) != 0;
    }

    public static SurfaceFamily familyOf(short state) {
        int ordinal = state & FAMILY_MASK;
        if ((state & FAMILY_HIGH_MASK) != 0) ordinal += FAMILY_HIGH_VALUE;
        return SurfaceFamily.byOrdinal(ordinal);
    }

    /** The visual layer, or -1 when this position carries no visible wear. */
    public static int layerOf(short state) {
        return ((state >> LAYER_SHIFT) & LAYER_MASK) - 1;
    }

    public static int sinkOf(short state) {
        return clampSink((state >> SINK_SHIFT) & SINK_MASK);
    }

    /**
     * True when the record uses a bit this version does not understand.
     *
     * <p>
     * Nothing is unknown now that the two top bits carry reinforcement, so this is always false.
     * Kept as a seam: the next thing to claim a bit turns it back on for the bits above its own.
     */
    public static boolean hasUnknownBits(short state) {
        return false;
    }

    /**
     * Holds a depth inside what the engine can actually stand on.
     *
     * <p>
     * Applied on every read as well as on write, because a corrupt or newer record must never be
     * able to hand the collision code a box below half a block — that is the depth a player can
     * still step out of, and past it the ground becomes a trap.
     */
    public static int clampSink(int sink) {
        if (sink < 0) return 0;
        return sink > SinkProfile.MAX_SINK_PIXELS ? SinkProfile.MAX_SINK_PIXELS : sink;
    }

    /**
     * How far along its whole run a position is, from 0 to 1.
     *
     * <p>
     * Worked out arithmetically rather than by looking the position up in its chain, because
     * this is asked once per rendered face on the threads that build chunk meshes and a scan
     * there would be felt. The shape of every chain is known — one long run of layers, then a
     * shorter run for each pixel of depth — so the position is just counting.
     */
    public static float progressOf(short state, int firstRunLayers, int layersPerDepth, int deepest) {
        int layer = layerOf(state);
        if (layer < 0) return 0f;
        int sink = sinkOf(state);
        int first = Math.max(1, firstRunLayers);
        int per = Math.max(1, layersPerDepth);

        int index = sink == 0 ? layer : first + (sink - 1) * per + layer;
        int total = first + Math.max(0, deepest) * per;
        if (total <= 1) return 0f;
        float fraction = index / (float) (total - 1);
        if (fraction < 0f) return 0f;
        return fraction > 1f ? 1f : fraction;
    }

    /**
     * Reads a record written by the version that stored one byte.
     *
     * <p>
     * That byte held a family and a stage counted along the old chain, where sinking was a
     * property of how far along a family's stages you were rather than a number of its own. The
     * honest conversion needs the chain that produced it, so this only recovers the family and
     * the stage; {@link ErosionChain} turns those into a layer and a depth under the new model.
     */
    public static short fromLegacy(byte flags) {
        return (short) (flags & 0xFF);
    }

    /** The stage the previous format's byte encoded, or -1 for none. */
    public static int legacyStageOf(byte flags) {
        return ((flags >> LAYER_SHIFT) & 0xF) - 1;
    }
}
