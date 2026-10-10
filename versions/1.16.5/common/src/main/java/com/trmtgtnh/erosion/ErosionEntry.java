package com.trmtgtnh.erosion;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Erosion progress for a single block position.
 *
 * <p>
 * This is the sole source of truth for how worn a position is. Unlike upstream TRMT, the
 * world itself never records erosion - no eroded block is ever written to the chunk's block
 * array on the server. That is what lets the mod be removed without leaving unknown blocks
 * in the terrain, and what lets one client switch the visuals off without the server or
 * anyone else's view changing.
 *
 * <p>
 * Mutable and allocated in large numbers, so it stays deliberately small: two floats, an
 * int and a packed byte, fifteen bytes on disk. Rotation is deliberately <em>not</em>
 * stored - it is a pure function of the block position, so both sides derive the same value
 * for free and neither the save nor the wire pays for it.
 */
public final class ErosionEntry {

    /** Which appearance this entry currently wears. See {@link ErosionChain}. */
    private SurfaceFamily family;

    /** Visual stage within {@link #family}, or -1 for "tracked but not yet visibly worn". */
    private byte stage;

    /** Accumulated wear toward the current stage's threshold. */
    private float wear;

    /**
     * Randomly drawn threshold for the current gradation, giving erosion an organic edge.
     *
     * <p>
     * Stored at its face value, with no speed multiplier folded in. That matters: the multiplier
     * used to be applied when the number was drawn, so a position kept whatever speed was set the
     * first time anything walked on it and changing the setting did nothing to ground that
     * already existed. Applying it at the moment of comparison means a change takes effect
     * everywhere at once, which is what anyone changing it expects.
     */
    private float threshold;

    /**
     * World time of the last step, in seconds of {@code totalWorldTime}.
     *
     * <p>
     * Absolute rather than relative, which is the whole trick behind healing in unloaded
     * chunks: the difference between this and the clock at load time says exactly how much
     * recovery a chunk missed while nobody was looking, so an untouched chunk catches up in
     * one pass instead of needing to have been ticking all along. It counts in-game time, so
     * a server left switched off for a week heals nothing.
     */
    private int lastTouchedSeconds;

    /** How far this position has physically dropped, in sixteenths of a block. */
    private byte sink;

    /**
     * Pinned: neither traffic nor time may move this position.
     *
     * <p>
     * Only ever true on a visible entry. That invariant is what keeps the flag reachable: an
     * invisible entry is prunable, so a pin on one would be dropped at the next save without
     * anything noticing.
     */
    private boolean frozen;

    /** How many times this position has been reinforced, 0-3. Independent of wear. */
    private byte reinforce;

    /**
     * Which categories of mob are barred from spawning on this block: bit 0 hostile, bit 1
     * passive. Independent of wear and of reinforcement, and set by the ward tamper mode.
     *
     * <p>
     * It rides in the same record as the family precisely so a ward placed on ground that has
     * never worn keeps the right family - the position becomes an invisible entry carrying
     * nothing but the ward, the same way a reinforced-but-unworn block does. Stored in a
     * companion byte rather than the packed state word, which is already full.
     */
    private byte ward;

    /**
     * The glow set on this position: level in the low nibble, color index in the high one.
     *
     * <p>
     * Zero means unlit, which is why the level and not the color occupies the low bits - a color
     * on its own is not a light, and packing it this way makes "is anything set here" a test against
     * zero rather than a mask. Like the ward it rides in the same record as the family, so lighting
     * ground that has never worn keeps that ground's identity.
     */
    private byte light;

    public ErosionEntry(SurfaceFamily family, float threshold, int nowSeconds) {
        this.family = family;
        this.stage = -1;
        this.sink = 0;
        this.wear = 0f;
        this.threshold = threshold;
        this.lastTouchedSeconds = nowSeconds;
    }

    /** Full-state constructor used by deserialization. */
    ErosionEntry(SurfaceFamily family, byte stage, byte sink, float wear, float threshold, int lastTouchedSeconds,
        boolean frozen) {
        this.family = family;
        this.frozen = frozen && stage >= 0;
        this.stage = stage;
        this.sink = ErosionState.clampSink(sink) == sink ? sink : (byte) ErosionState.clampSink(sink);
        this.wear = wear;
        this.threshold = threshold;
        this.lastTouchedSeconds = lastTouchedSeconds;
    }

    /**
     * The threshold a record is made with when whatever makes it cannot draw one.
     *
     * <p>
     * Reinforcing, warding and the demonstration platforms each make a record for ground nobody has
     * walked on, and the draw belongs to the engine, which none of them is. This figure used to stay
     * as the threshold, so a protected square's first gradation came at one unit of wear where its
     * unprotected neighbour's came at a draw from the family's range - for grass about five to ten on
     * the shipped settings - and a floor reinforced twice wore into its first gradation sooner than the
     * floor beside it, rather than three times later. The engine now draws in its place the first
     * time the square is walked on; see {@link #awaitsDraw()}.
     */
    public static final float UNDRAWN_THRESHOLD = 1f;

    /**
     * Whether this record, still showing nothing, holds a threshold that was never drawn: the stand-in
     * {@link #UNDRAWN_THRESHOLD}, or the nought the heal sweep writes when it keeps a record only for its
     * reinforcement or ward on a block that has stopped being a surface.
     *
     * <p>
     * Asked only of an invisible record, because every gradation arrives with a draw of its own. The nought
     * was a fault of its own: a block that came back as the same surface kept it, and the first footstep
     * on it advanced a whole gradation. A pack whose range can draw exactly one is drawn for again on each
     * step, which lands in the same range, so nothing it does is wrong - only repeated.
     */
    public boolean awaitsDraw() {
        return stage < 0 && (threshold == UNDRAWN_THRESHOLD || !(threshold > 0f));
    }

    public SurfaceFamily getFamily() {
        return family;
    }

    public int getStage() {
        return stage;
    }

    public int getSink() {
        return sink;
    }

    public void setSink(int depth) {
        this.sink = (byte) ErosionState.clampSink(depth);
    }

    public float getWear() {
        return wear;
    }

    public float getThreshold() {
        return threshold;
    }

    public int getLastTouchedSeconds() {
        return lastTouchedSeconds;
    }

    /** True when this position is pinned against both wear and healing. */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Pins or releases this position.
     *
     * <p>
     * Refuses to pin an entry with nothing to show, because such an entry is prunable and the
     * flag would not survive the next save. Releasing is always allowed.
     */
    public void setFrozen(boolean frozen) {
        this.frozen = frozen && stage >= 0;
    }

    /** True once this position has an appearance the client should paint. */
    public boolean isVisible() {
        return stage >= 0;
    }

    /** True when the entry carries no progress and no visible stage, so it can be pruned. */
    public boolean isPrunable() {
        return stage < 0 && wear <= 0f && reinforce == 0 && ward == 0 && light == 0;
    }

    /** How many times this position has been reinforced, 0-3. */
    public int getReinforce() {
        return reinforce;
    }

    public void setReinforce(int level) {
        this.reinforce = (byte) (level < 0 ? 0 : level > 3 ? 3 : level);
    }

    /** The mob categories barred from spawning here: bit 0 hostile, bit 1 passive. */
    public int getWard() {
        return ward & 0x3;
    }

    public void setWard(int flags) {
        this.ward = (byte) (flags & 0x3);
    }

    /** Whether hostile mobs are barred from spawning on this block. */
    public boolean wardsHostile() {
        return (ward & 0x1) != 0;
    }

    /** Whether passive mobs are barred from spawning on this block. */
    public boolean wardsPassive() {
        return (ward & 0x2) != 0;
    }

    // ------------------------------------------------------------------
    // Light
    // ------------------------------------------------------------------

    /** The whole packed light byte: level in the low nibble, color in the high one. */
    public int getLight() {
        return light & 0xFF;
    }

    public void setLight(int packed) {
        this.light = (byte) (packed & 0xFF);
    }

    /** How brightly this position glows, 0 (unlit) to 15. */
    public int getLightLevel() {
        return light & 0xF;
    }

    /** Which of the sixteen dye colors it glows, meaningful only when it is lit at all. */
    public int getLightColor() {
        return (light >> 4) & 0xF;
    }

    /** Sets both halves at once, because setting one without the other is never what is meant. */
    public void setLight(int level, int color) {
        int clamped = level < 0 ? 0 : level > 15 ? 15 : level;
        this.light = (byte) (clamped | ((color & 0xF) << 4));
    }

    public boolean isLit() {
        return (light & 0xF) != 0;
    }

    /** Adds wear and records the step time. */
    public void recordStep(float amount, int nowSeconds) {
        this.wear += amount;
        this.lastTouchedSeconds = nowSeconds;
    }

    /** Moves the inactivity clock without adding wear. Used to bank partial healing. */
    public void setLastTouchedSeconds(int nowSeconds) {
        this.lastTouchedSeconds = nowSeconds;
    }

    public void setWear(float wear) {
        this.wear = wear < 0f ? 0f : wear;
    }

    /** True when accumulated wear has reached the current threshold. */
    public boolean thresholdReached() {
        return wear >= effectiveThreshold();
    }

    /**
     * The threshold as it stands under the speed currently configured and whatever reinforcement
     * this position carries.
     *
     * <p>
     * Both are applied here rather than folded into the stored number, because both can change
     * under ground that is already part worn - a pack owner turning the speed down, a player
     * reinforcing a path they have already walked - and neither should have to wait for the next
     * stage to draw a fresh threshold before it tells. What is saved stays the honest random draw
     * for the family, which is what a readout should show and what a different config should be
     * able to reinterpret.
     */
    public float effectiveThreshold() {
        return TrmtConfig.scale(threshold) * Reinforcement.wearFactor(reinforce);
    }

    /**
     * Moves to a new appearance and resets progress against a fresh threshold.
     * {@link ErosionChain} decides what the new appearance is; this only stores it.
     */
    public void setAppearance(SurfaceFamily newFamily, int newStage, float newThreshold) {
        this.family = newFamily;
        this.stage = (byte) newStage;
        this.wear = 0f;
        this.threshold = newThreshold;
    }

    /**
     * Re-points this entry at a different surface, discarding progress. Happens when the
     * block underneath changes - dirt that vanilla spread grass over, say - so the stored
     * appearance no longer belongs to any chain that block can follow.
     */
    public void retarget(SurfaceFamily newFamily, float newThreshold, int nowSeconds) {
        this.family = newFamily;
        this.stage = -1;
        this.wear = 0f;
        this.threshold = newThreshold;
        this.lastTouchedSeconds = nowSeconds;
    }

    // ------------------------------------------------------------------
    // Packing
    // ------------------------------------------------------------------
    //
    // family : bits 0-2 (8 families)
    // stage : bits 3-7, biased by one so -1 stores as 0 (stages -1 to 30)
    //
    // The same byte goes to disk and onto the wire, so a client update is four bytes per
    // position including the key.
    //
    // The stage field used to be four bits with the eighth reserved. Claiming that bit needs no
    // format version and no migration: nothing ever wrote it, so every stage a previous version
    // stored has a zero there and reads back through the wider mask as exactly the same number.
    // A record written here and read by an older build is the only asymmetry, and that one is
    // already handled - it rejects a stage it does not recognise and drops the entry.

    public short packState() {
        return ErosionState.pack(family, stage, sink, frozen, reinforce);
    }

    public static SurfaceFamily familyOf(short state) {
        return ErosionState.familyOf(state);
    }

    public static int stageOf(short state) {
        return ErosionState.layerOf(state);
    }

    static ErosionEntry fromPacked(short state, float wear, float threshold, int lastTouchedSeconds) {
        // A bit this version does not know the meaning of means the record came from a later one.
        // Dropping it keeps the reserved bits claimable later without another format number, the
        // same way the fifth layer bit was claimed.
        SurfaceFamily family = familyOf(state);
        if (family == null) return null;
        int stage = stageOf(state);
        if (stage >= SurfaceFamily.MAX_STAGES) return null; // corrupt, or written by a newer version
        // NaN and infinities would poison every later comparison; treat them as corruption.
        if (Float.isNaN(wear) || Float.isNaN(threshold) || Float.isInfinite(wear) || Float.isInfinite(threshold)) {
            return null;
        }
        ErosionEntry entry = new ErosionEntry(
            family,
            (byte) stage,
            (byte) ErosionState.sinkOf(state),
            wear,
            threshold,
            lastTouchedSeconds,
            ErosionState.frozenOf(state));
        entry.setReinforce(ErosionState.reinforceOf(state));
        return entry;
    }
}
