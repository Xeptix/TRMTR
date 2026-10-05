package com.trmtgtnh.surface;

import java.util.Locale;

/**
 * The kind of surface a block behaves as, independent of which mod added it.
 *
 * <p>
 * GTNH ships a great deal of terrain. Rather than name every block, each erodable block is
 * sorted into one of these families and inherits that family's wear chain, thresholds,
 * healing rate, wear pattern and toggles. {@link SurfaceRegistry} does the sorting;
 * {@code FamilySettings} holds the tuning.
 *
 * <p>
 * The ordinal is persisted inside every erosion record and sent on the wire, so <b>the
 * order of these constants is part of the save format</b>. Append new families at the end,
 * never insert - inserting one renumbers every family after it and silently rewrites the
 * meaning of every record already saved. Four bits are allocated, so there is room for
 * sixteen in total; see {@code ErosionState} for where the fourth bit lives and why it is not
 * beside the other three.
 */
public enum SurfaceFamily {

    /** Grass-like surfaces. Wears toward the bare earth underneath. */
    GRASS(true),
    /** Bare earth: dirt, coarse dirt, mud, loam. */
    DIRT(true),
    /** Loose granular surfaces: sand, red sand, modded equivalents. */
    SAND(true),
    /** Gravel and its variants: granular like sand, but keeps its own colouring. */
    GRAVEL(true),
    /** Smooth rock: stone, granite, marble, GregTech's stone types. Wears very slowly. */
    STONE(true),
    /** Cobblestone and its variants. Slightly quicker to scuff than smooth stone. */
    COBBLE(true),
    /**
     * Leaves. Trampling target only: a leaf walked on keeps a tally and breaks when it runs out, so
     * there are no wear stages.
     */
    LEAVES(false),
    /** Flowers, tall grass and similar. Trampling target only, counted where the feet are. */
    VEGETATION(false),
    /** Netherrack, nether brick and the rest of that country. Wears about as slowly as stone. */
    NETHER(true),
    /** End stone and its kin. Wears about as slowly as stone. */
    END(true),
    /** Snow blocks. Compacts under foot faster than anything else that holds a shape. */
    SNOW(true),
    /** Ice, packed and otherwise. Scuffs and scratches, but takes a long time to. */
    ICE(true);

    private static final SurfaceFamily[] BY_ORDINAL = values();

    /**
     * Hard ceiling on stages.
     *
     * <p>
     * Sixteen, and it cannot go higher without a real format change - but not for the reason this
     * used to give. The wear chain has been a {@code short[]} of
     * {@link com.trmtgtnh.erosion.ErosionState} words since depth became a number of its own, and
     * that word gives the layer five bits biased by one, so the packed record holds nought to
     * thirty and always did. The byte with four bits each is the format before that one, and only
     * {@code ErosionState.legacyStageOf} still reads it.
     *
     * <p>
     * What actually caps this is the block in the world. A painted position keeps its layer in the
     * ghost block's metadata - {@code OverlayPainter} writes it with
     * {@code setBlock(x, y, z, ghost, stage, 0)} and {@code GhostRendering.iconFor} reads it
     * straight back with {@code getBlockMetadata} - and metadata is four bits. A seventeenth stage
     * would wrap onto the first and two different gradations would become one block state with
     * nothing to notice it had happened.
     *
     * <p>
     * This is the cap on what a RECORD may hold. How many PICTURES the ground is drawn with is a
     * different question with a different answer, and reading this one as though it were both is
     * what capped the drawn gradations at sixteen for a reason that had nothing to do with drawing.
     * That number is {@link WearScale#COUNTED_STEPS}, which is client-side and never written down.
     */
    public static final int MAX_STAGES = 16;

    /** True for families that accumulate wear stages and paint an overlay. */
    public final boolean staged;

    SurfaceFamily(boolean staged) {
        this.staged = staged;
    }

    /** Config key and command name for this family. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SurfaceFamily byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= BY_ORDINAL.length) return null;
        return BY_ORDINAL[ordinal];
    }

    public static SurfaceFamily byKey(String key) {
        for (SurfaceFamily family : BY_ORDINAL) {
            if (family.key()
                .equalsIgnoreCase(key)) return family;
        }
        return null;
    }

    /** The families that carry wear stages, in declaration order. */
    public static SurfaceFamily[] staged() {
        int count = 0;
        for (SurfaceFamily family : BY_ORDINAL) {
            if (family.staged) count++;
        }
        SurfaceFamily[] out = new SurfaceFamily[count];
        int i = 0;
        for (SurfaceFamily family : BY_ORDINAL) {
            if (family.staged) out[i++] = family;
        }
        return out;
    }
}
