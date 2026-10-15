package com.trmtgtnh.erosion;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The arithmetic of a family's run, worked out from numbers rather than from a built chain.
 *
 * <p>
 * A run is a stretch of gradations on the face as it stands, then a shorter stretch for each pixel
 * of depth, stopped somewhere along its length by a ceiling. The engine builds that shape into a
 * chain, the Wear Table reads it back, and the wear editor has to work it out for a shape that does
 * not exist yet, so that somebody can see what a change does before it is published. For a while
 * each of them carried its own copy of the sums, and the copies drifted: the editor wrote a borrowed
 * depth where nought had been typed, republished a reshuffled split for a change of depth alone, and
 * capped a staged ceiling without the world's. Every one of those was a copy disagreeing with the
 * engine, and none of them could fail a test, because the copy lived in a screen.
 *
 * <p>
 * So the sums live here, once, and everything that needs them calls in. It has nothing from the game
 * in it, which is what lets a test pin each figure exactly, and it is on the list
 * CoreStaysPortableTest keeps free of Minecraft.
 */
public final class RunShape {

    private RunShape() {}

    /** The ceiling a run actually stops at: the lower of the world's and the family's own. */
    public static float ceiling(float world, float family) {
        return Math.min(world, family);
    }

    /**
     * How many phases of a run of {@code full} a ceiling lets wear reach.
     *
     * <p>
     * Never below one while there is a run at all: a family capped at nothing would have no step to
     * take, and a position that can never show anything is indistinguishable from a family switched
     * off, which is what the family's own enabled setting is for.
     */
    public static int capped(int full, float ceiling) {
        if (full <= 0) return 0;
        if (ceiling >= 1f) return full;
        if (ceiling <= 0f) return 1;
        int capped = Math.round(full * ceiling);
        return capped < 1 ? 1 : Math.min(capped, full);
    }

    /**
     * Whether a family takes its depth, and the layers each pixel of it is worth, from the first
     * surface it wears through into.
     *
     * <p>
     * Grass is the reason: turf is a face rather than a substance, so its run has always been
     * measured in the earth's pixels. Any family with no depth of its own that wears through into
     * something does the same, and one with nothing to wear through into simply stops at the end of
     * its surface stretch.
     */
    public static boolean borrowsDepth(int ownDepth, boolean wearsThrough) {
        return ownDepth <= 0 && wearsThrough;
    }

    /**
     * A depth held inside what the engine can stand on. The engine's own clamp rather than a copy of
     * it, so the two cannot come apart.
     */
    public static int clampDepth(int depth) {
        return ErosionState.clampSink(depth);
    }

    /** Layers per pixel of depth, never below one, because a pixel worth nothing would not be a step. */
    public static int layers(int layersPerDepth) {
        return Math.max(1, layersPerDepth);
    }

    /**
     * How many phases a run of this shape has.
     *
     * <p>
     * The depth and layers are the figures the run actually uses, already clamped and borrowed.
     * Dirt's first gradation is skipped, because a ghost showing no wear at all cannot be told from
     * the block it covers, and that is what the offset counts.
     */
    public static int length(int stages, int firstOffset, int layers, int depth) {
        return Math.max(0, stages - firstOffset) + layers * depth;
    }

    /** How many phases a quarter column reaches, rounded the way every row rounds it. */
    public static int quarter(float fraction, int phases) {
        int n = Math.round(fraction * phases);
        return n < 0 ? 0 : n > phases ? phases : n;
    }

    /**
     * The scale that keeps a run's end where it was when its length changes.
     *
     * <p>
     * A run half as long has to cost twice as much a step, or the end of it moves. One where either
     * length is nothing, because there is no end to keep.
     */
    public static double keepEnd(int before, int after) {
        if (before <= 0 || after <= 0) return 1d;
        return before / (double) after;
    }

    /**
     * The scale on a family's own figure that makes a cell read {@code target}.
     *
     * <p>
     * A cell is what other families contribute, which this scale cannot move, plus the weight of the
     * phases this family owns times its figure, times the scale, times the compensation for a changed
     * length, over the speed. Solved for the scale.
     *
     * @param target      what the cell should read
     * @param fixed       the part of the cell other families contribute
     * @param ownedWeight the curve weight of the phases this family owns, or one for a single phase
     * @param figure      this family's own figure, from the file
     * @param keepEnd     the compensation the published figure will carry
     * @param speed       what a figure is divided by to become the cell's unit
     * @return the scale, or null when none above nought reaches the target, when this family owns
     *         nothing the scale could move, or when the answer is not a finite number
     */
    public static Double scaleFor(double target, double fixed, double ownedWeight, double figure, double keepEnd,
        double speed) {
        if (!(ownedWeight > 0d) || !(figure > 0d) || !(keepEnd > 0d)) return null;
        double k = (target - fixed) * speed / (ownedWeight * figure * keepEnd);
        if (!(k > 0d) || Double.isInfinite(k)) return null;
        return Double.valueOf(k);
    }

    /**
     * The two numbers a run's length is made of, solved for a length somebody typed.
     *
     * <p>
     * Only the total is on the screen, so a typed total has to be shared back out between the surface
     * stretch and the stretches for each pixel of depth, and there is usually more than one way.
     * <ul>
     * <li>With no depth, the surface stretch is the whole run and only {@code stages} moves.</li>
     * <li>With a borrowed depth, only {@code stages} moves too. The layers per pixel are another
     * family's setting, and moving them would silently reshape that family's own run.</li>
     * <li>With a depth of its own, layers per pixel are tried from one upward, and the first whose
     * surface stretch fits between one and sixteen is kept. That is the split with the fewest layers
     * per pixel and so the longest surface stretch; it does not keep the two stretches in proportion.
     * Every split that fits builds the target exactly, so no nearer miss is ever looked for.</li>
     * </ul>
     *
     * @param target      the whole run's length
     * @param depth       the depth the run uses, already clamped and borrowed
     * @param firstOffset the gradations skipped at the start, one for dirt and nought otherwise
     * @param borrowed    whether that depth belongs to another family
     * @param layers      the layers per pixel the run uses now
     * @return {stages, layersPerDepth}, or null when the target cannot be reached at all
     */
    public static int[] split(int target, int depth, int firstOffset, boolean borrowed, int layers) {
        if (depth <= 0) {
            int stages = target + firstOffset;
            if (stages < 1 || stages > SurfaceFamily.MAX_STAGES) return null;
            return new int[] { stages, layers };
        }

        if (borrowed) {
            int stages = target + firstOffset - layers * depth;
            if (stages < 1 || stages > SurfaceFamily.MAX_STAGES) return null;
            return new int[] { stages, layers };
        }

        for (int perDepth = 1; perDepth <= SurfaceFamily.MAX_STAGES; perDepth++) {
            int stages = target + firstOffset - perDepth * depth;
            // Fewer surface gradations with every extra layer, so once there is no room there never
            // will be again.
            if (stages < 1) break;
            if (stages > SurfaceFamily.MAX_STAGES) continue;
            return new int[] { stages, perDepth };
        }
        return null;
    }

    /**
     * One family's run as it would stand once everything staged for it had landed.
     *
     * <p>
     * Built once and read by everything that draws or publishes the family, so the header, the
     * columns, the solvers and the lines sent to the server cannot describe different runs.
     */
    public static final class Run {

        /** The length the figures build, with no typed length. */
        public final int built;

        /** The typed length's split, or null when none was typed or it cannot be shared out. */
        public final int[] split;

        /**
         * The whole run's length: the typed one where it can be shared out, otherwise the built one,
         * because a length that cannot be published must not be shown as though it would be.
         */
        public final int full;

        /** How far along the run wear may go, after the ceiling. */
        public final int reached;

        /** The depth the run sinks to: its own, or the first successor's where it borrows. */
        public final int depth;

        /** The layers each pixel of that depth is worth, from whichever family owns the depth. */
        public final int layers;

        /**
         * Whether the depth and layers are another family's. True wherever a family has no depth of
         * its own and wears through into something, even when that something has no depth either.
         */
        public final boolean borrowed;

        /** The family's own ceiling, staged or from the file. */
        public final float familyCeiling;

        /** The ceiling the run stops at: the lower of the world's and the family's own. */
        public final float ceiling;

        /** Whether the world's ceiling is the lower one, so a higher family figure changes nothing. */
        public final boolean heldByWorld;

        private Run(int built, int[] split, int full, int reached, int depth, int layers, boolean borrowed,
            float familyCeiling, float ceiling, boolean heldByWorld) {
            this.built = built;
            this.split = split;
            this.full = full;
            this.reached = reached;
            this.depth = depth;
            this.layers = layers;
            this.borrowed = borrowed;
            this.familyCeiling = familyCeiling;
            this.ceiling = ceiling;
            this.heldByWorld = heldByWorld;
        }

        /**
         * The run these figures make.
         *
         * @param stages          the family's surface gradations
         * @param firstOffset     the gradations skipped at the start, one for dirt and nought otherwise
         * @param ownLayers       the family's own layers per pixel of depth
         * @param ownDepth        the family's own depth; nought or less means none
         * @param wearsThrough    whether it wears through into anything
         * @param successorLayers the first successor's layers per pixel, read only when borrowing
         * @param successorDepth  the first successor's own depth, read only when borrowing
         * @param typedLength     a whole run's length somebody typed, or nought for none
         * @param worldCeiling    the world's ceiling
         * @param familyCeiling   the family's own ceiling
         */
        public static Run of(int stages, int firstOffset, int ownLayers, int ownDepth, boolean wearsThrough,
            int successorLayers, int successorDepth, int typedLength, float worldCeiling, float familyCeiling) {
            boolean borrowed = borrowsDepth(ownDepth, wearsThrough);
            int depth = clampDepth(borrowed ? successorDepth : ownDepth);
            int layers = layers(borrowed ? successorLayers : ownLayers);
            int built = length(stages, firstOffset, layers, depth);
            int[] split = typedLength > 0 ? split(typedLength, depth, firstOffset, borrowed, layers) : null;
            int full = split != null ? typedLength : built;
            float ceiling = ceiling(worldCeiling, familyCeiling);
            return new Run(
                built,
                split,
                full,
                capped(full, ceiling),
                depth,
                layers,
                borrowed,
                familyCeiling,
                ceiling,
                familyCeiling > worldCeiling);
        }
    }

    /** The shape lines one family publishes, each -1 where no line goes out. */
    public static final class Published {

        /** The family's own staged depth, never a borrowed one; -1 when none is staged. */
        public final int maxSinkPixels;

        /** The typed length's surface gradations; -1 when no length is typed or it cannot be shared out. */
        public final int stages;

        /** The typed length's layers per pixel; -1 as for stages, and always where the depth is borrowed. */
        public final int layersPerDepth;

        /** The scale on the family's thresholds and heal time that keeps its run's end where it was. */
        public final double keepEnd;

        private Published(int maxSinkPixels, int stages, int layersPerDepth, double keepEnd) {
            this.maxSinkPixels = maxSinkPixels;
            this.stages = stages;
            this.layersPerDepth = layersPerDepth;
            this.keepEnd = keepEnd;
        }
    }

    /**
     * The shape lines one family publishes.
     *
     * <p>
     * A depth goes out as the family's own figure, so nought is written as nought rather than as the
     * depth it would borrow; writing the borrowed one made a typed nought publish the depth it was
     * meant to remove. {@code stages} and {@code layersPerDepth} go out only for a typed length that
     * can be shared out, so a change of depth alone never republishes a reshuffled split, and
     * {@code layersPerDepth} never for a borrowed depth, which is another family's setting.
     *
     * <p>
     * The compensation is worked out from the lengths rather than staged as its own figure, so
     * publishing twice cannot apply it twice. A depth typed on its own is not compensated, because
     * the run it changes is the one the figures build.
     *
     * @param run         the family's staged run
     * @param firstOffset the gradations skipped at the start, one for dirt and nought otherwise
     * @param stagedDepth the family's own staged depth, or -1 when none is staged
     */
    public static Published publish(Run run, int firstOffset, int stagedDepth) {
        int sink = stagedDepth < 0 ? -1 : clampDepth(stagedDepth);
        if (run.split == null) return new Published(sink, -1, -1, 1d);
        int after = length(run.split[0], firstOffset, run.split[1], run.depth);
        return new Published(sink, run.split[0], run.borrowed ? -1 : run.split[1], keepEnd(run.built, after));
    }
}
