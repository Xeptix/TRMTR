package com.trmtgtnh.config;

import java.util.List;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.PhysicalDecay;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.util.MainThread;

/**
 * Applying a config change without restarting.
 *
 * <p>
 * The settings themselves are only half of it. Nearly everything the mod does at speed reads
 * from something derived: which blocks erode is a lookup table built by walking the whole block
 * registry, the order surfaces wear through is a set of prebuilt chains, whether a block can be
 * sunk into is a flag stamped onto the block itself. Re-reading the file and stopping there
 * leaves all of that describing the config you used to have.
 *
 * <p>
 * So there is one ordered sequence, here, and both ways of changing the config run it. The order
 * is not arbitrary - the surface table has to exist before blocks can be marked sinkable, and the
 * rules cannot be broadcast until the collision check has had its say on whether real ruts are
 * actually available. A {@link Delta} taken across the change then tells each side what genuinely
 * moved, so a change to healing rates does not repaint the world and a change to block lists does.
 */
public final class ConfigReload {

    private ConfigReload() {}

    // ------------------------------------------------------------------
    // What changed
    // ------------------------------------------------------------------

    /** What actually moved across a reload. Everything downstream is decided from this. */
    public static final class Delta {

        /** Which blocks erode at all. Painted ghosts on blocks that left have to come off. */
        public final boolean surfaces;

        /** Rut depth or collision. The solid-or-hollow ghost variant is chosen at paint time. */
        public final boolean sink;

        /** Whether this client shows overlays, and how far. */
        public final boolean overlay;

        /** Whether the player's own show-erosion preference moved, as opposed to the server's. */
        public final boolean showErosionMoved;

        /** Generated wear pixels. Only a re-stitch of the block atlas can show these. */
        public final boolean textures;

        public final boolean enabledOn;
        public final boolean enabledOff;

        Delta(boolean surfaces, boolean sink, boolean overlay, boolean showErosionMoved, boolean textures,
            boolean enabledOn, boolean enabledOff) {
            this.surfaces = surfaces;
            this.sink = sink;
            this.overlay = overlay;
            this.showErosionMoved = showErosionMoved;
            this.textures = textures;
            this.enabledOn = enabledOn;
            this.enabledOff = enabledOff;
        }

        /** True when the client has to take its overlays off and put them back on. */
        public boolean needsRepaint() {
            return surfaces || sink || overlay || enabledOn || enabledOff;
        }

        public boolean any() {
            return needsRepaint() || textures || showErosionMoved;
        }
    }

    /**
     * The handful of numbers a {@link Delta} is worked out from.
     *
     * <p>
     * Deliberately taken from what the mod ended up with rather than from what the file said.
     * Turning auto-detection off, for instance, changes one boolean but may not change a single
     * entry in the surface table, because the lists it wrote on previous runs are still there;
     * comparing the resulting table gets that right where comparing the setting does not.
     */
    static final class Snapshot {

        private final int surfaces;
        private final int sink;
        private final int textures;
        private final boolean showErosion;
        private final boolean overlayForced;
        private final int overlayDistance;
        private final boolean enabled;
        /** Which stand-in the painter chooses, which only a repaint can change. */
        private final boolean seeThrough;

        private Snapshot(int surfaces, int sink, int textures, boolean showErosion, boolean overlayForced,
            int overlayDistance, boolean enabled, boolean seeThrough) {
            this.surfaces = surfaces;
            this.sink = sink;
            this.textures = textures;
            this.showErosion = showErosion;
            this.overlayForced = overlayForced;
            this.overlayDistance = overlayDistance;
            this.enabled = enabled;
            this.seeThrough = seeThrough;
        }

        static Snapshot take() {
            int surfaces = SurfaceRegistry.tableSignature();
            int sink = 17;
            int textures = TrmtConfig.maxTexturedSurfaces * 31 + (TrmtConfig.perSurfaceTextures ? 1 : 0);

            // Ordering is the point of the texture signature, not just membership: per-surface
            // texture sets are numbered in registry-name order, so inserting one block shifts
            // every later set onto different source pixels.
            List<SurfaceRegistry.SurfaceState> states = SurfaceRegistry.texturableStates();
            textures = textures * 31 + states.size();
            for (int i = 0; i < states.size(); i++) {
                SurfaceRegistry.SurfaceState state = states.get(i);
                textures = textures * 31 + state.hashCode();
                // And what each metadata of it wears, because that is what the planner reads now.
                // The name alone moved only when a block entered or left the list, so splitting a
                // slab's eight materials apart - or merging them back - rewrote every sprite in
                // the atlas without moving the signature that asks for a re-stitch.
                for (int meta = 0; meta < 16; meta++) {
                    SurfaceFamily family = SurfaceRegistry.familyOf(state.block, meta);
                    textures = textures * 31 + (family == null ? 0 : family.ordinal() + 1);
                }
            }

            // Everything that decides which appearances a chain visits, because that is exactly
            // what decides which sprites have to exist. Adding a successor to a family adds a
            // whole appearance to its fallback set, and a signature that missed it would leave
            // the new steps with no textures until something else happened to force a stitch.
            textures = textures * 31 + (TrmtConfig.grassWearsThroughToDirt ? 1 : 0);
            textures = textures * 31 + (TrmtConfig.wearThroughToOtherSurfaces ? 1 : 0);
            textures = textures * 31 + TrmtConfig.maxWearSprites;
            textures = textures * 31 + (TrmtConfig.largerAtlas ? 1 : 0);
            // The shape of a run decides what every gradation of every sprite looks like, so a
            // change to it is a whole new atlas even though not one sprite has been added or taken
            // away. Each look's own shape rides along with this and needs nothing of its own: it is
            // a compiled constant chosen by wearPattern, and wearPattern is hashed above.
            textures = textures * 31 + Float.floatToIntBits(TrmtConfig.wearCurve);
            // How many pictures there are and how many rotations of them, neither of which was
            // here. Both size the icon table, so lowering either halves the atlas and doubles every
            // step of every run - and until now nothing asked for the regeneration that would show
            // it, so the setting looked like it did nothing until something unrelated forced a
            // stitch.
            textures = textures * 31 + TrmtConfig.wearGradations;
            textures = textures * 31 + TrmtConfig.wearRotations;
            // What goes behind a worn block whose own face is cut away, and whether it moves. Both
            // are read while the atlas is being built and baked into the picture, so like the two
            // above they showed nothing at all until something unrelated forced a stitch - which is
            // the same fault, one paragraph later, on settings added after that paragraph was
            // written. The list is hashed by content rather than by length, because renaming the
            // texture behind a block is the edit somebody experimenting would make.
            textures = textures * 31 + java.util.Arrays.hashCode(TrmtConfig.innerLayerTextures);
            textures = textures * 31 + (TrmtConfig.animateInnerLayers ? 1 : 0);
            textures = textures * 31 + TrmtConfig.innerLayerAnimationBudgetMb;
            textures = textures * 31 + (TrmtConfig.seeThroughInnerLayers ? 1 : 0);
            // The chain's shape, not only the atlas's. These two decide which appearances
            // a run visits, so a change to either has to rebuild chains as well as restitch -
            // missing them is why toggling the switch looked like it did nothing at all.
            sink = sink * 31 + (TrmtConfig.wearThroughToOtherSurfaces ? 1 : 0);
            sink = sink * 31 + (TrmtConfig.grassWearsThroughToDirt ? 1 : 0);
            sink = sink * 31 + TrmtConfig.physicalDecay.hashCode();

            for (SurfaceFamily family : SurfaceFamily.values()) {
                FamilySettings settings = TrmtConfig.family(family);
                if (settings == null) continue;
                sink = sink * 31 + settings.maxSinkPixels;
                sink = sink * 31 + Float.floatToIntBits(settings.sinkStartFraction);
                sink = sink * 31 + settings.stages;
                // Which appearances this family's run visits, rather than how many steps it takes
                // to visit them. The stage count itself stood here and the planner has never read
                // it: planChain walks the chain, collects the distinct families found on it, and
                // lays out a full set of gradations for each, so two runs of quite different
                // lengths that pass through the same materials want precisely the same sprites.
                // Nudging a family from twelve stages to thirteen therefore bought a rebuild of the
                // entire atlas - the better part of half a minute - for a picture that came back
                // identical to the pixel.
                //
                // The set does move, though, and being too blunt here was hiding a second fault by
                // being absent one line further down. A family whose chain empties loses every
                // sprite it had, and dirt at one stage is the reachable case: its surface run
                // starts at layer one rather than layer zero, so a single stage leaves nothing on
                // it, and with no depth to sink into there is no second run to put it back. Both of
                // those floors are the config screen's own, so it takes no hand-edited file to
                // arrive there. Meanwhile maxSinkPixels sat in the sink signature and in no other,
                // although setting it to zero deletes the depth run entire and with it every
                // successor the chain would have reached - an atlas quietly left describing a
                // config nobody was running any more.
                //
                // Asking the chain settles both at once, and goes on settling them if the shape of
                // a run is ever built some other way, because it reads the answer instead of
                // predicting it. Safe to read here: the chains are rebuilt inside TrmtConfig.read,
                // which lands between the snapshot taken before a reload and the one taken after.
                int visited = 0;
                int steps = ErosionChain.length(family);
                for (int step = 0; step < steps; step++) {
                    SurfaceFamily seen = ErosionChain.familyAt(family, step);
                    if (seen != null) visited |= 1 << seen.ordinal();
                }
                textures = textures * 31 + visited;
                textures = textures * 31 + (settings.enabled ? 1 : 0);
                textures = textures * 31 + Float.floatToIntBits(settings.wearStrength);
                textures = textures * 31 + (settings.wearPattern == null ? 0 : settings.wearPattern.hashCode());
                textures = textures * 31 + java.util.Arrays.hashCode(settings.wearsThroughTo);
                sink = sink * 31 + java.util.Arrays.hashCode(settings.wearsThroughTo);
                sink = sink * 31 + settings.layersPerDepth;
                sink = sink * 31 + (settings.slabs ? 1 : 0);
                sink = sink * 31 + (settings.stairs ? 1 : 0);
            }

            return new Snapshot(
                surfaces,
                sink,
                textures,
                TrmtConfig.showErosion,
                TrmtConfig.overlayForced,
                TrmtConfig.overlayDistanceChunks,
                TrmtConfig.enabled,
                TrmtConfig.seeThroughInnerLayers);
        }

        Delta since(Snapshot before) {
            return new Delta(
                surfaces != before.surfaces,
                sink != before.sink,
                // The see-through setting rides in the overlay's own bucket rather than only in the
                // textures': whether a block HAS anything behind it is a fact about its pixels and
                // does not move when the setting does, but WHICH stand-in is painted over it does,
                // and nothing but a repaint changes that.
                showErosion != before.showErosion || overlayForced != before.overlayForced
                    || overlayDistance != before.overlayDistance
                    || seeThrough != before.seeThrough,
                showErosion != before.showErosion,
                textures != before.textures,
                enabled && !before.enabled,
                !enabled && before.enabled);
        }
    }

    // ------------------------------------------------------------------
    // The two ways in
    // ------------------------------------------------------------------

    /**
     * Picks up an edit made to the file on disk. This is what {@code /trmt reload} runs.
     *
     * @return null when the file could not be read, in which case nothing changed
     */
    public static Delta fromDisk() {
        Snapshot before = Snapshot.take();
        if (!TrmtConfig.reloadFromDisk()) return null;
        return finish(before);
    }

    /**
     * Applies edits the config screen has already made.
     *
     * <p>
     * Does not re-read the file from disk: the screen writes straight into the settings Forge is
     * holding, and reading over the top of that would throw away what the player just typed. It does
     * re-read those held settings, which is not the same thing, and is the distinction this
     * paragraph used to blur. That re-read is how a screenful of edits reaches the mod at all - and
     * it was also how a visit to somebody else's server used to lose that server's geometry.
     */
    public static Delta fromGui() {
        if (TrmtConfig.isPoisoned()) {
            Trmt.LOG
                .warn("Ignoring config screen changes: the file on disk failed to parse, fix it and use /trmt reload");
            return null;
        }
        Snapshot before = Snapshot.take();
        TrmtConfig.read();
        TrmtConfig.save();
        // Asked after the read, which is where a server's numbers were put back, and before the work
        // below, so the answer is about this press of Done rather than about the next one.
        boolean overruled = ServerRules.shouldTellPlayer();
        Delta delta = finish(before);
        if (overruled) Trmt.proxy.tellServerOwnsGeometry();
        return delta;
    }

    // ------------------------------------------------------------------
    // The sequence
    // ------------------------------------------------------------------

    /**
     * Rebuilds everything derived from the settings, in the only order that works.
     *
     * <p>
     * Detection runs every time rather than only when the lists look different, because working
     * out whether it would have changed anything costs the same walk as doing it. This is a
     * deliberate, occasional action, not something on a tick.
     */
    private static Delta finish(Snapshot before) {
        SurfaceRegistry.resolve();
        PhysicalDecay.markSinkableBlocks();
        Trmt.proxy.markSettlingBlocks();
        // The other edition surveys here what each of its sixty-six ghost blocks must inherit from the
        // block it covers - its material, its light, its sounds - because a ghost there is a block chosen
        // once and written into the world. This edition has one ghost that reads what it is standing in
        // for at every rebuild, so there is nothing to survey ahead of time.

        // Only now: marking can find the collision hook missing and quietly downgrade real ruts
        // to visual ones, and that decision is part of what the rules tell clients.
        TrmtNetwork.broadcastRules();

        Delta delta = Snapshot.take()
            .since(before);
        if (delta.enabledOff) TrmtNetwork.broadcastClearAll();
        // After the clear, which reaches only players still subscribed, and after the rules, so a client
        // told its ruts are now real already knows it when the ground arrives. Re-enabling the mod or
        // switching ruts to real used to leave every player it newly applied to seeing nothing until
        // they rejoined. A no-op on a client connected to somebody else's server, where this runs too.
        TrmtNetwork.reconcileSubscriptions();

        Trmt.proxy.onConfigChanged(delta);
        return delta;
    }

    /**
     * Runs a reload on the server thread when one is actually running, or here when none is.
     *
     * <p>
     * The config screen fires on the client thread, and in single player that is not the thread
     * that owns the world. Note the deferral this implies: an open menu pauses the integrated
     * server, so the work lands the moment the player closes it rather than while they are still
     * looking at the screen.
     */
    public static void fromGuiDeferred() {
        if (Trmt.runningServer() == null) {
            fromGui();
            return;
        }
        // Written now, on the thread that asked, and applied later where the world is. The screen
        // has already put its edits into the settings Forge holds, and the reload that saves them
        // waits for the integrated server's next tick - which never comes when the player goes from
        // the pause menu straight to Save and Quit, so an edit lived for that session and was gone
        // from the file at the next launch. Saving still refuses a file that failed to read.
        TrmtConfig.save();
        MainThread.onServer(new Runnable() {

            @Override
            public void run() {
                fromGui();
            }
        });
    }
}
