package com.trmtgtnh.erosion;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What a family's wear settings work out to in the round: how many phases it wears through, how
 * much traffic reaches each quarter of that, and how long it takes to grow back.
 *
 * <p>
 * Every figure is a plain function of two things: the <em>structure</em> of the wear chain
 * (which family owns each phase, and how deep each sits), which comes from the live
 * {@link ErosionChain}; and the <em>pricing</em> - the thresholds, heal times, speed multipliers
 * and the ceiling a run stops at - which comes from a {@link Pricing}. The ceiling sits with the
 * pricing rather than with the structure because it is the one part of a run's shape a server
 * does not hand its clients, so two machines can stop an identical chain at different points. The client fills the Wear
 * Table with its own live pricing, and can price the
 * same chain a second time against a snapshot of the server's, so the two schedules can be shown
 * side by side. That is the whole reason pricing is a separate argument rather than read inline.
 *
 * <p>
 * The two units are honestly different and the table says so. Wear is counted in <em>crossings</em>
 * - passes of a plain player over the block - because there is no such thing as a second of wear:
 * ground wears when something walks on it, not while time passes. Healing is the opposite, a pure
 * function of elapsed time, so it is counted in in-game days.
 */
public final class WearMath {

    /** The quarters every column is quoted at. */
    public static final float[] FRACTIONS = { 0.25f, 0.5f, 0.75f, 1.0f };

    private static final double[] NONE = { 0, 0, 0, 0 };

    private WearMath() {}

    // ------------------------------------------------------------------
    // Pricing: the numbers that scale a chain into a schedule
    // ------------------------------------------------------------------

    /**
     * The thresholds, heal times and speeds a chain is priced by.
     *
     * <p>
     * One built from the live config prices the client's own table; one built from the server's
     * snapshot prices the same chain the server's way, for the diff. Per-family figures are held
     * by ordinal, which is stable within a version and is how the packet lines them up.
     */
    public static final class Pricing {

        final double multiplierPlayer;
        final double erosionSpeed;
        final double globalSpeed;
        final double healingRate;

        /** The whole world's wear ceiling, as a fraction. One where a source named none. */
        private final double maxWearFraction;

        /** Each family's own ceiling by ordinal, or null where a source named none. */
        private final double[] familyCap;

        private final CostCurve[] curves;

        private final double[] avgThreshold;
        private final double[] healDaysPerStage;

        // The two shorter constructors that stood here are gone rather than kept for tidiness.
        // They defaulted the ceiling to one, which was harmless while the ceiling was read out
        // of the live config anyway and stopped being harmless the moment a row started taking
        // it from the pricing: the wear editor went on calling the short form and priced every
        // family as though nothing capped it, disagreeing with the table it exists to edit. A
        // default that is right until one line changes elsewhere is worse than an argument
        // somebody has to supply, so now everybody supplies it.

        public Pricing(double multiplierPlayer, double erosionSpeed, double globalSpeed, double healingRate,
            double maxWearFraction, double[] familyCap, double[] avgThreshold, double[] healDaysPerStage,
            CostCurve[] curves) {
            this.curves = curves;
            this.multiplierPlayer = multiplierPlayer;
            this.erosionSpeed = erosionSpeed;
            this.globalSpeed = globalSpeed;
            this.healingRate = healingRate;
            this.maxWearFraction = maxWearFraction;
            this.familyCap = familyCap;
            this.avgThreshold = avgThreshold;
            this.healDaysPerStage = healDaysPerStage;
        }

        /**
         * How far along its run this family may wear under this pricing: the lower of the world's
         * ceiling and the family's own, which is the rule the chain itself applies.
         *
         * <p>
         * Carried by the pricing rather than read from the live config, because a ceiling decides
         * where a run ends and every figure on a row is a figure about that run. A row drawn for a
         * server has to stop where that server stops it, or its quarters are quarters of the wrong
         * run and its total quotes crossings the ground there is never charged.
         */
        double cap(SurfaceFamily family) {
            return Math.min(worldCap(), familyCap(family));
        }

        /**
         * The whole world's wear ceiling this pricing was built with, as a fraction.
         *
         * <p>
         * Offered on its own, apart from {@link #cap}, because the wear editor has to say which of
         * the two ceilings is holding a family back, and on a remote server neither of them is
         * this machine's: the rules packet never folds either into a client's settings, so the
         * server's figures exist only in the pricing it sent.
         */
        public double worldCap() {
            return maxWearFraction;
        }

        /**
         * This family's own wear ceiling under this pricing, as a fraction, before the world's is
         * applied. One where the pricing names none for it, which is also what a family with no
         * ceiling of its own is written as, so the two read alike.
         */
        public double familyCap(SurfaceFamily family) {
            if (familyCap == null || family == null) return 1d;
            int i = family.ordinal();
            return i >= 0 && i < familyCap.length ? familyCap[i] : 1d;
        }

        double avgThreshold(SurfaceFamily family) {
            if (family == null) return 1d;
            int i = family.ordinal();
            return i >= 0 && i < avgThreshold.length ? avgThreshold[i] : 1d;
        }

        /**
         * The curve this family prices by, or the flat one where a caller supplied none.
         *
         * <p>
         * Null-tolerant deliberately. A pricing table can arrive from a server built before curves
         * existed, and a table without them should describe a mod without them rather than refuse
         * to draw anything at all.
         */
        CostCurve curve(SurfaceFamily family) {
            if (curves == null || family == null) return CostCurve.FLAT;
            int i = family.ordinal();
            CostCurve held = i >= 0 && i < curves.length ? curves[i] : null;
            return held == null ? CostCurve.FLAT : held;
        }

        double healDays(SurfaceFamily family) {
            if (family == null) return 0d;
            int i = family.ordinal();
            return i >= 0 && i < healDaysPerStage.length ? healDaysPerStage[i] : 0d;
        }

        /** The pricing the config currently in effect gives. */
        public static Pricing live() {
            SurfaceFamily[] families = SurfaceFamily.values();
            double[] avg = new double[families.length];
            double[] heal = new double[families.length];
            CostCurve[] curves = new CostCurve[families.length];
            for (int i = 0; i < families.length; i++) {
                FamilySettings fs = TrmtConfig.family(families[i]);
                avg[i] = fs == null ? 1d : (fs.thresholdMin + fs.thresholdMax) / 2d;
                heal[i] = fs == null ? 0d : fs.healDaysPerStage;
                curves[i] = fs == null ? CostCurve.FLAT : fs.costCurve;
            }
            double[] caps = new double[families.length];
            for (int i = 0; i < families.length; i++) {
                FamilySettings fs = TrmtConfig.family(families[i]);
                caps[i] = fs == null ? 1d : fs.maxWear;
            }
            return new Pricing(
                TrmtConfig.multiplierPlayer,
                TrmtConfig.erosionSpeed,
                TrmtConfig.globalSpeed,
                TrmtConfig.healingRate,
                TrmtConfig.maxWearFraction,
                caps,
                avg,
                heal,
                curves);
        }
    }

    // ------------------------------------------------------------------
    // A row
    // ------------------------------------------------------------------

    /** One family's row: everything the table shows for it. */
    public static final class Row {

        /** Phases of wear this family actually reaches, after the wear cap. */
        public final int phases;

        /** The uncapped chain length, so the table can show "48/80" when a cap is in force. */
        public final int fullPhases;

        /** Crossings of a plain player to advance one phase of the base surface - the knob. */
        public final double crossingsPerPhase;

        /** In-game days to heal one phase of the base surface back - the heal knob. */
        public final double healDaysPerPhase;

        /** Crossings of a plain player to reach each quarter of full wear. */
        public final double[] crossings;

        /** In-game days to grow each quarter of the way back from fully worn. */
        public final double[] healDays;

        /** How deep the rut sinks at full wear, in pixels (0-8). */
        public final int sinkPixels;

        /** The wear cap in effect, as a fraction - the smaller of the global and family caps. */
        public final float cap;

        public final boolean wears;

        Row(int phases, int fullPhases, double crossingsPerPhase, double healDaysPerPhase, double[] crossings,
            double[] healDays, int sinkPixels, float cap, boolean wears) {
            this.phases = phases;
            this.fullPhases = fullPhases;
            this.crossingsPerPhase = crossingsPerPhase;
            this.healDaysPerPhase = healDaysPerPhase;
            this.crossings = crossings;
            this.healDays = healDays;
            this.sinkPixels = sinkPixels;
            this.cap = cap;
            this.wears = wears;
        }
    }

    /** The row for one family, priced by the live config. */
    public static Row rowFor(SurfaceFamily base) {
        return rowFrom(base, Pricing.live());
    }

    /**
     * The row for one family, on the live chain structure, priced however the caller says.
     *
     * <p>
     * Which family owns each phase, and how deep each of them sits, comes from the live
     * {@link ErosionChain}: a connected client is sent the server's stage counts, depths and sink
     * profile and applies them to its own settings, so both machines build the same chain.
     *
     * <p>
     * The wear ceiling is the exception, and it is the reason {@link Pricing} carries one. It is
     * not folded into a client's settings by the rules packet - {@code ServerRules.apply} leaves
     * both {@code families.&lt;name&gt;.maxWear} and {@code general.maxWearFraction} alone - so two
     * machines can stop their ground at different points along an identical chain. A row therefore
     * takes its ceiling, and with it its phase count and its sink depth, from the pricing it is
     * drawn for. Anyone building a {@link Pricing} by hand must supply one, or the row quietly
     * describes a world with no ceiling in it.
     */
    public static Row rowFrom(SurfaceFamily base, Pricing pricing) {
        return rowFrom(base, pricing, 0, -1, -1f);
    }

    /**
     * The same row, priced as though this family had a different shape.
     *
     * <p>
     * What the editor needs to show somebody the consequence of a change before they publish it.
     * Only the three numbers a shape is made of are overridable - how many phases the run has, how
     * deep it ends up and how far along it is allowed to go - because those are the three the
     * header offers, and every other figure on the screen follows from them.
     *
     * <p>
     * It costs nothing to offer, because with the pace following the ground - which is the default
     * and the only setting under which a length can be changed without changing what a step costs
     * - every phase of a run is priced by the base family and the chain is not consulted at all.
     *
     * @param phasesOverride the whole run's length, or 0 to read the real chain
     * @param sinkOverride   how deep the last phase sits, or -1 to read the real chain
     * @param capOverride    how far along the run may go, or -1 to read the configured cap
     */
    public static Row rowFrom(SurfaceFamily base, Pricing pricing, int phasesOverride, int sinkOverride,
        float capOverride) {
        FamilySettings settings = TrmtConfig.family(base);
        // Asked of the pricing rather than of the live config, so that a row drawn for a server
        // stops where that server stops it. The ceiling was the one member of the pricing that
        // arrived on the wire and was then read out of this machine's own file instead, which
        // let the server column quote a whole run on a server that caps its ground a quarter of
        // the way along. Pricing.live() fills it from the same two settings that stood here, so
        // the client's own column is unmoved.
        float cap = capOverride >= 0f ? capOverride : (float) pricing.cap(base);
        int full = phasesOverride > 0 ? phasesOverride : ErosionChain.length(base);
        // The same function ErosionChain.cappedLength goes through, worked out from numbers rather
        // than from a built chain, so a length that does not exist yet is capped exactly as the
        // engine would cap it once it did.
        int phases = RunShape.capped(full, cap);
        boolean wears = base.staged && settings != null && settings.enabled && phases > 0;
        if (!wears) {
            return inert(full);
        }

        double perCrossing = Math.max(1e-4d, pricing.multiplierPlayer);
        double wearSpeed = positive(pricing.erosionSpeed) * positive(pricing.globalSpeed);
        double healSpeed = positive(pricing.healingRate) * positive(pricing.globalSpeed);

        double[] cumCrossings = new double[phases + 1];
        double[] cumHealDays = new double[phases + 1];
        for (int phase = 0; phase < phases; phase++) {
            // A phase belongs to whichever family it draws as - grass's own layers, then the
            // earth beneath - so it is priced by that family's thresholds and heal time.
            SurfaceFamily at = ErosionChain.paceAt(base, phase);
            SurfaceFamily priced = at == null ? base : at;
            // Shaped by the same curve the engine draws through, against the same denominator: the
            // whole chain, not the part a wear ceiling allows. A table measuring against the capped
            // length would quote prices no block is ever actually charged.
            double average = pricing.avgThreshold(priced) * pricing.curve(priced)
                .at(phase, full);
            double crossings = average / (perCrossing * wearSpeed);
            double days = pricing.healDays(at == null ? base : at) / healSpeed;
            cumCrossings[phase + 1] = cumCrossings[phase] + crossings;
            cumHealDays[phase + 1] = cumHealDays[phase] + days;
        }

        double[] crossings = new double[FRACTIONS.length];
        double[] healDays = new double[FRACTIONS.length];
        for (int k = 0; k < FRACTIONS.length; k++) {
            int n = RunShape.quarter(FRACTIONS[k], phases);
            // Wearing counts up from pristine; the first n phases.
            crossings[k] = cumCrossings[n];
            // Healing counts down from fully worn; the deepest n phases, which for grass are the
            // dirt phases and heal at dirt's rate.
            healDays[k] = cumHealDays[phases] - cumHealDays[phases - n];
        }

        // The per-phase knobs are the BASE family's own step: what one gradation of this block
        // costs and heals, before the chain borrows any earth beneath it. That is the value a
        // threshold or a heal-time setting most directly moves.
        //
        // Left unshaped by the curve, and right that way rather than in spite of it: a curve
        // averages to one across a whole run, so this is what a gradation costs on average whatever
        // curve the family carries. The cumulative columns above are where the shape shows.
        double crossingsPerPhase = pricing.avgThreshold(base) / (perCrossing * wearSpeed);
        double healDaysPerPhase = pricing.healDays(base) / healSpeed;

        int sink = sinkOverride >= 0 ? sinkOverride : ErosionChain.sinkAt(base, phases - 1);
        return new Row(phases, full, crossingsPerPhase, healDaysPerPhase, crossings, healDays, sink, cap, true);
    }

    /**
     * The row of a family that does not wear at all: no phases, no crossings, no heal time, no
     * depth and no ceiling.
     *
     * <p>
     * Offered so a caller previewing a run that comes to nothing can say so directly. Asking
     * {@link #rowFrom(SurfaceFamily, Pricing, int, int, float)} for a run of nought does not give
     * this, because a phase count of nought there means "read the live chain", and the preview
     * would quietly show the run that is about to be taken away.
     */
    public static Row inert() {
        return inert(0);
    }

    /** The same row, still recording how long the run would have been had it worn. */
    private static Row inert(int full) {
        return new Row(0, full, 0d, 0d, NONE.clone(), NONE.clone(), 0, 0f, false);
    }

    /** Guards a rate against zero, which would otherwise divide the schedule away. */
    private static double positive(double rate) {
        return rate <= 0d ? 1d : rate;
    }
}
