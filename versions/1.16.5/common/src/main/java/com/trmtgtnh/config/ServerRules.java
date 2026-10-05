package com.trmtgtnh.config;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.SinkProfile;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What a server says the ground is like, kept for as long as the visit lasts.
 *
 * <p>
 * A server sends three numbers a family: how many gradations it wears through, how many pixels it
 * can sink, and how far along its run the sinking starts. They are applied to this client's live
 * settings and never written to its file, which is the whole point - leaving puts the player's own
 * preferences straight back. What was missing was anywhere to keep them. They lived only in the
 * {@link FamilySettings} objects they had been stamped onto, and {@code TrmtConfig.read} does not
 * edit those objects, it replaces the map wholesale from the file. So any config read while
 * connected quietly handed the whole lot back: a player who opened the settings screen on a server,
 * changed a colour and pressed Done was returned to their own geometry, and nothing said so.
 *
 * <p>
 * Which mattered, because the two halves then disagree about where the floor is. Depth itself is on
 * the wire, but what a client does with it is not: a rut deeper than this client's own
 * {@code maxSinkPixels} is clamped back to it, and a client whose own file says the ruts are only
 * pictures declines to fall into any of them. The server goes on hollowing the ground out
 * regardless. The player walks along the top of a rut the server keeps dropping them into, and
 * rubber-bands along every well-trodden path - which is precisely the desync
 * {@code PhysicalDecay.verifyCollisionHook} exists to prevent, arriving by a route it never watched.
 *
 * <p>
 * The bytes off the wire are what is kept, rather than references to the settings they were applied
 * to, and that is the one structural decision here that matters: a read replaces those objects, so a
 * reference held across one is a reference to rubbish. Nothing in this class recompiles anything
 * either - no chain is rebuilt, no block re-stamped - because every caller does that on the very
 * next line, and doing it twice is how a reload comes to apply itself and then undo itself.
 *
 * <p>
 * Nothing is held on a machine that runs its own world. In single player both sides read these same
 * statics: the reload that applies a host's edit also broadcasts it, and the broadcast is built by
 * reading the settings a re-assertion would just have reverted, so a host who changed a stage count
 * would watch it change back. The guard is asked both when a record is taken and when it is used -
 * once so a record cannot be taken by mistake, and once so a record taken before a world was opened
 * cannot fire inside one.
 */
public final class ServerRules {

    private static volatile boolean held;

    private static volatile boolean forceOverlay;

    /** Null when the server said nothing about it, which an older server does. */
    private static volatile String decayMode;

    /** Everything the server said about the shape of its ground, or null when nothing is held. */
    private static volatile Geometry geometry;

    /** How many settings the last re-assertion actually had to put back. The tell. */
    private static volatile int overrode;

    /** Whether the player has already been told, this visit, that some of it is not theirs. */
    private static volatile boolean toldPlayer;

    /**
     * Everything a server says about the shape of its ground, in one piece.
     *
     * <p>
     * A carrier rather than seven arguments, and it exists because the argument list was the thing
     * that kept this from being finished. It began at three, took a fourth when depth had to travel,
     * and the successor lists would have made it seven - at which point adding the next one is a
     * change to four files and a signature nobody can read, which is a good way to arrive at a
     * decision to leave the next one out. A field on a carrier costs one line in each of the two
     * places that fill and read it.
     *
     * <p>
     * Every array is by family ordinal and may be null, which means this server said nothing: an
     * older server and a server with nothing to say are the same silence, and the answer to both is
     * that this client's own file stands.
     */
    public static final class Geometry {

        /** Gradations on the face, pixels of sink, where along the run sinking starts. */
        public final byte[] stages;

        public final byte[] maxSink;

        public final byte[] sinkStart;

        /** Gradations a pixel of depth is worth. */
        public final byte[] depths;

        /**
         * What each family wears through into, as family ordinals, written rather than switched on.
         *
         * <p>
         * The list as it is <em>written</em>, deliberately, with the two switches carried separately
         * beside it. Sending the gated list would have been fewer bytes and would have thrown away
         * the one question the client needs the ungated list for: whether a record it has been sent
         * could ever have come from this ground. The painter asks exactly that before it draws
         * anything, and a record made while a run was switched on is still that surface's own record
         * afterwards.
         */
        public final byte[][] successors;

        /** Whether each family wears at all. A family switched off has no chain and draws nothing. */
        public final boolean[] enabled;

        /**
         * The two switches that gate the lists above, or -1 where this server sent none.
         *
         * <p>
         * Bit 0 is the wholesale one and bit 1 is turf's own. They travel because they decide
         * whether the lists are followed, and a client gating a server's lists by its own switches
         * would build a different chain out of identical lists - which is the whole fault this
         * carries them to fix.
         */
        public final int switches;

        public Geometry(byte[] stages, byte[] maxSink, byte[] sinkStart, byte[] depths, byte[][] successors,
            boolean[] enabled, int switches) {
            this.stages = stages;
            this.maxSink = maxSink;
            this.sinkStart = sinkStart;
            this.depths = depths;
            this.successors = successors;
            this.enabled = enabled;
            this.switches = switches;
        }

        /** Whether there is enough here to be worth applying at all. */
        boolean usable() {
            return stages != null && maxSink != null && sinkStart != null;
        }
    }

    private ServerRules() {}

    /**
     * Takes a server's rules and applies them.
     *
     * <p>
     * Says nothing about whether the wear pictures need redrawing, and used to. It reported a
     * moved stage count, which the caller took for the answer to that question and which never
     * was one: the atlas is laid out from the materials a run passes through rather than from
     * the number of gradations it takes to pass through them. {@link #chainAppearances()} is
     * the question worth asking, and it has to be asked of the chains rather than of anything
     * this method can see.
     */
    public static void hold(boolean force, String mode, Geometry wanted) {
        if (wanted == null || !wanted.usable()) return;

        forceOverlay = force;
        decayMode = mode == null || mode.isEmpty() ? null : mode;
        geometry = wanted;

        boolean first = !held;
        apply(true);

        if (Trmt.runningServer() != null) {
            // A world of one's own. Applied, because the numbers are that world's own anyway and
            // applying them changes nothing, but not kept, because keeping them would make the
            // host's next edit impossible to save.
            forget();
            return;
        }

        held = true;
        if (first) {
            Trmt.LOG.info(
                "Holding this server's wear geometry for the visit; it differs from your own config on {} setting(s). Your file is untouched and comes back when you leave.",
                Integer.valueOf(overrode));
        }
    }

    /**
     * Stamps the held rules back over settings a config read has just replaced.
     *
     * <p>
     * Called from inside {@code TrmtConfig.read}, between the family map being rebuilt from the file
     * and anything being compiled out of it. Both halves of that are load-bearing. Earlier and it
     * writes onto objects about to be thrown away; later and the wear chains and the collision flag
     * have already been built once from the wrong numbers.
     */
    public static void reassert() {
        overrode = 0;
        if (!held || Trmt.runningServer() != null) return;

        apply(true);
        if (overrode > 0) {
            // Said at debug rather than info because an ordinary visit can reach it several times,
            // and said at all because an injection or a hook that silently never runs is the shape
            // of failure this mod has shipped behind more than once. A player reporting that their
            // footing is wrong on a server can be asked for this line, and its absence is an answer.
            Trmt.LOG.debug(
                "Server rules re-asserted over {} setting(s) the config read had put back",
                Integer.valueOf(overrode));
        }
    }

    /**
     * Gives this client its own settings back. Called on the way out, before the read that does it.
     *
     * <p>
     * Ordered that way deliberately: the read at disconnect is the designed hand-back, and it is the
     * one read in the mod that must not be re-asserted into.
     */
    public static void release() {
        forget();
        // The one thing dropped here and not in forget. Forcing the overlay on is applied like the
        // rest and survives a config read on its own, no read method touching it - so it is not part
        // of what has to be put back, only part of what has to be let go of, and letting go of it
        // where a record is merely declined would take it off a host who is entitled to it.
        TrmtConfig.overlayForced = false;
    }

    /** Drops the record without touching anything it was applied to. */
    private static void forget() {
        held = false;
        geometry = null;
        decayMode = null;
        forceOverlay = false;
        overrode = 0;
        toldPlayer = false;
    }

    /**
     * How many settings the last hold or re-assertion actually had to move.
     *
     * <p>
     * Asked by the caller for one reason: what a worn block draws is worked out from the live
     * settings while a chunk's mesh is being built, not stored per position, so geometry that
     * moved under an already-built chunk goes on showing the old picture until the meshes are
     * built again. Nothing else in the join path rebuilds them.
     */
    public static int overrode() {
        return overrode;
    }

    /** Whether a server currently owns part of this client's geometry. */
    public static boolean held() {
        return held;
    }

    /**
     * Whether the player should be told that part of what they just typed is not theirs today.
     *
     * <p>
     * Latched, so a visit says it once. Without the latch a player whose own file asks for pictures
     * on a server that has real ruts would be told again on every press of Done, which is noise
     * rather than news.
     */
    public static boolean shouldTellPlayer() {
        if (!held || overrode <= 0 || toldPlayer) return false;
        toldPlayer = true;
        return true;
    }

    /**
     * The top bit of a fingerprint entry: whether the family wears at all.
     *
     * <p>
     * Kept in the same word as the appearances rather than in an array of its own, because the two
     * are always taken at the same moment and about the same family, and one word compared two ways
     * needs no second snapshot to fall out of step with the first. Bit thirty-one is free by a wide
     * margin - the low bits are one per family and there are twelve.
     */
    private static final int WEARS_AT_ALL = 1 << 31;

    /**
     * A fingerprint of every family's run: which appearances it visits, and whether it wears at all.
     *
     * <p>
     * Taken before a change and compared after, twice, because two quite different things follow
     * from it and lumping them together was wrong in both directions.
     *
     * <p>
     * {@link #appearancesMovedFrom} asks the atlas question. A stitch lays out a full run of
     * pictures for each appearance a chain visits, and nothing whatever for the number of gradations
     * it takes to visit them: two runs of different lengths through the same materials want
     * precisely the same sprites. So a server one stage away from this client's own file used to buy
     * a rebuild of the entire atlas - the better part of half a minute - to arrive back at a picture
     * identical to the pixel, at the moment in a visit a player is least willing to spend it.
     * {@code ConfigReload.Snapshot.take} was given this correction and this pair of callers was not.
     *
     * <p>
     * {@link #enabledMovedFrom} asks the surface-table question, and it is a separate bit because
     * the appearance set cannot answer it. The table is pruned of every block of a family whose
     * switch is off, and the pruning does not care whether the family is staged - but a run is only
     * built for a staged family, so leaves and vegetation have an empty chain in every config and
     * their switch moves no appearance at all. Inferring the table's question from the atlas's
     * therefore missed exactly the two families whose table entry decides whether a route destroys
     * the plants standing in it. The same blind spot covers a staged family whose chain is empty for
     * other reasons, which the config screen's own floors can reach.
     *
     * <p>
     * Only building the chains says what they come out as, so every caller must have rebuilt them
     * before comparing. Both do: the join rebuilds on the line after the rules are held, and the
     * disconnect reads the file, which rebuilds them itself.
     */
    public static int[] chainAppearances() {
        SurfaceFamily[] families = SurfaceFamily.values();
        int[] visited = new int[families.length];
        for (int i = 0; i < families.length; i++) {
            FamilySettings settings = TrmtConfig.family(families[i]);
            if (settings != null && settings.enabled) visited[i] |= WEARS_AT_ALL;
            int steps = ErosionChain.length(families[i]);
            for (int step = 0; step < steps; step++) {
                SurfaceFamily seen = ErosionChain.familyAt(families[i], step);
                if (seen != null) visited[i] |= 1 << seen.ordinal();
            }
        }
        return visited;
    }

    /**
     * Whether any family's visited appearances have moved: the question the block atlas answers to.
     *
     * <p>
     * The switch bit is masked out of both sides deliberately. A family switched off draws no
     * pictures, but it had none planned for it either, so turning one on or off moves not a pixel of
     * the atlas - and half a minute of rebuilding is far too much to spend on finding that out.
     */
    public static boolean appearancesMovedFrom(int[] before) {
        if (before == null) return false;
        int[] now = chainAppearances();
        if (now.length != before.length) return true;
        for (int i = 0; i < now.length; i++) {
            if ((now[i] & ~WEARS_AT_ALL) != (before[i] & ~WEARS_AT_ALL)) return true;
        }
        return false;
    }

    /**
     * Whether any family's switch has moved: the question the surface table answers to.
     *
     * <p>
     * Its own question because its own answer is expensive in the other direction. Rebuilding the
     * table means walking the block registry, which is worth doing when a family has changed hands
     * and worth nothing at all when only a successor list has.
     */
    public static boolean enabledMovedFrom(int[] before) {
        if (before == null) return false;
        int[] now = chainAppearances();
        if (now.length != before.length) return true;
        for (int i = 0; i < now.length; i++) {
            if ((now[i] & WEARS_AT_ALL) != (before[i] & WEARS_AT_ALL)) return true;
        }
        return false;
    }

    /**
     * Writes the held numbers onto the live settings.
     *
     * @param counting whether to record how many of them had to be changed, which is what tells a
     *                 log reader that this ran and a player that some of the screen is not theirs
     */
    private static void apply(boolean counting) {
        Geometry wanted = geometry;
        if (wanted == null || !wanted.usable()) return;

        int changed = 0;

        String mode = decayMode;
        if (mode != null) {
            if (!mode.equals(TrmtConfig.physicalDecay)) changed++;
            TrmtConfig.physicalDecay = mode;
        }
        if (TrmtConfig.overlayForced != forceOverlay) changed++;
        TrmtConfig.overlayForced = forceOverlay;

        // The two switches that decide whether a successor list is followed at all. Minus one is a
        // server that sent none, and there its own lists are followed under this client's switches
        // exactly as they were before any of this travelled.
        if (wanted.switches >= 0) {
            boolean wholesale = (wanted.switches & 1) != 0;
            boolean turf = (wanted.switches & 2) != 0;
            if (TrmtConfig.wearThroughToOtherSurfaces != wholesale) changed++;
            TrmtConfig.wearThroughToOtherSurfaces = wholesale;
            if (TrmtConfig.grassWearsThroughToDirt != turf) changed++;
            TrmtConfig.grassWearsThroughToDirt = turf;
        }

        SurfaceFamily[] families = SurfaceFamily.values();
        // Bounded on all three rather than on the stage array alone. They arrive at one length
        // today, so the old loop was safe by a fact about the packet rather than by anything the
        // loop said; a packet written later has no reason to know that was being relied on.
        int count = Math.min(
            families.length,
            Math.min(wanted.stages.length, Math.min(wanted.maxSink.length, wanted.sinkStart.length)));

        for (int i = 0; i < count; i++) {
            FamilySettings settings = TrmtConfig.family(families[i]);
            if (settings == null) continue;

            // Nought means this server said nothing about the family - an older server and a family
            // that does not wear look the same from here - so its own count stands.
            int stage = clampStages(wanted.stages[i] & 0xFF);
            if (stage > 0 && stage != settings.stages) {
                settings.stages = stage;
                changed++;
            }

            // Nought is the same absence here, and for one more reason than the others: a
            // family a server said nothing about and a server that says nothing at all both
            // arrive as nought, and this client's own figure is the only honest answer to
            // either. Clamped to the same floor the config reader uses, since a nought that
            // survived would divide a run by nothing.
            if (wanted.depths != null && i < wanted.depths.length) {
                int per = clampStages(wanted.depths[i] & 0xFF);
                if (per > 0 && per != settings.layersPerDepth) {
                    settings.layersPerDepth = per;
                    changed++;
                }
            }

            int sink = clampSink(wanted.maxSink[i] & 0xFF);
            if (sink != settings.maxSinkPixels) changed++;
            settings.maxSinkPixels = sink;

            float start = fractionOf(wanted.sinkStart[i] & 0xFF);
            if (start != settings.sinkStartFraction) changed++;
            settings.sinkStartFraction = start;

            // Whether the family wears at all. Held for the bluntest of reasons: a family switched
            // off gets no chain built for it, so a client that has turned off what a server has not
            // cannot place a single one of that family's records - not the wrong picture, no
            // picture. Guarded on the array being present rather than on its contents, since false
            // is a real answer here and there is no spare value to mean "unsaid".
            if (wanted.enabled != null && i < wanted.enabled.length) {
                if (settings.enabled != wanted.enabled[i]) changed++;
                settings.enabled = wanted.enabled[i];
            }

            // What it wears through into. Written as keys rather than kept as ordinals because
            // everything downstream reads this field as configured names, including the config
            // screen a player may open mid-visit - and a screen showing ordinals would be a second
            // way of saying the same thing that only one half of the mod could read.
            if (wanted.successors != null && i < wanted.successors.length && wanted.successors[i] != null) {
                String[] named = namesOf(wanted.successors[i]);
                // Compared against this client's own list AS RESOLVED rather than as written, which
                // is comparing like with like: what arrives has already been through the resolver on
                // the server, so a file that spells a family differently, names one twice or names
                // one nothing here recognises would otherwise read as a difference for ever. That
                // matters beyond tidiness because this count is what asks for the world to be drawn
                // again, and a difference that can never be reconciled asks for it every time.
                if (!java.util.Arrays.equals(named, resolvedNames(families[i]))) {
                    settings.wearsThroughTo = named;
                    changed++;
                }
            }
        }

        if (counting) overrode = changed;
    }

    /**
     * This client's own successor list as the resolver sees it, for comparing against the wire.
     *
     * <p>
     * The list as written can say things the resolver drops - a name no build has a family for, the
     * same family twice, the family itself - and what arrives has already had all three dropped on
     * the server. Comparing the two forms would report a difference that no amount of applying could
     * ever settle.
     */
    private static String[] resolvedNames(SurfaceFamily family) {
        java.util.List<SurfaceFamily> own = TrmtConfig.wearsThroughTo(family, false);
        String[] out = new String[own.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = own.get(i)
                .key();
        }
        return out;
    }

    /**
     * Family ordinals from the wire turned back into the keys the settings hold.
     *
     * <p>
     * An ordinal this build has no family for is dropped rather than guessed at. It can only come
     * from a server built against a different set of families, where the honest reading of an
     * unknown material is that this client cannot draw it, and a run one material short is what a
     * config naming an unknown successor already produces.
     */
    private static String[] namesOf(byte[] ordinals) {
        SurfaceFamily[] families = SurfaceFamily.values();
        java.util.List<String> out = new java.util.ArrayList<String>(ordinals.length);
        for (int i = 0; i < ordinals.length; i++) {
            int ordinal = ordinals[i] & 0xFF;
            if (ordinal < families.length) out.add(families[ordinal].key());
        }
        return out.toArray(new String[out.size()]);
    }

    /**
     * A sinking start as a fraction, from the hundredths the wire carries.
     *
     * <p>
     * Clamped, which the packet path never was. A value read from the file is held to nought and one
     * where it is read; a byte off the wire went straight into a float every reader assumes is
     * inside that range, and two and a half was as reachable as a half. It survived only because the
     * next config read wiped it - which is the very fault being fixed here, so without the clamp
     * this change would make an old latent bug last the whole visit instead of a moment.
     */
    static float fractionOf(int hundredths) {
        if (hundredths <= 0) return 0f;
        return hundredths >= 100 ? 1f : hundredths / 100f;
    }

    /**
     * A stage count from the wire, held to the same ceiling the file on disk is held to.
     *
     * <p>
     * {@link SurfaceFamily#MAX_STAGES}, because that is what a painted position can carry: a ghost
     * block keeps its layer in the four bits of its own metadata, so a seventeenth gradation wraps
     * onto the first and two different amounts of wear become one block state with nothing to notice
     * it. Which is stricter than the erosion record, whose layer field is five bits and holds nought
     * to thirty - and stricter is the point. {@code FamilySettings.read} holds the file to this same
     * number, so a server cannot push this client past a figure the player could have typed
     * themselves. See MAX_STAGES, which says all of this at length and was written to retire the
     * record-shaped reason this comment first gave for it.
     *
     * <p>
     * The wire byte widens to nought through 255 at the call site, so nothing here bites on a server
     * sending what the format says. What it stops is a value from one that is not.
     */
    static int clampStages(int wanted) {
        return wanted > SurfaceFamily.MAX_STAGES ? SurfaceFamily.MAX_STAGES : wanted;
    }

    /** A depth from the wire, held to what the sink field can carry. */
    static int clampSink(int wanted) {
        return wanted > SinkProfile.MAX_SINK_PIXELS ? SinkProfile.MAX_SINK_PIXELS : wanted;
    }
}
