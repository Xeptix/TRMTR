package com.trmtgtnh.client.texture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.dataload.BlockLooks;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * The block states that wear pictures of their own, because a data file gave them another family or another look than
 * the rest of their block ({@code ns:name[half=top]}; FORMAT.md, "Block selectors").
 *
 * <p>
 * Wear sets are filed by block and a place in that block's row of sixteen ({@link StateFiling}), the places metadata
 * fills on the older editions and nothing filled from 1.13 on. So a state set apart takes one of those places for each
 * different family and look among a block's states: place nought is the block's own, as it always was, and a drawing
 * square asks its covered state for its place here. Sixteen places is the same number of different pictures a block has
 * on 1.7.10 and 1.12.2; a block that asks for more says so once and its extra states draw the block's own.
 *
 * <p>
 * Surveyed once as a stitch begins, on the render thread, from each state's answer as {@link BlockLooks#of(BlockState)}
 * reads it, and published whole through one volatile field, so a mesher reads it with one probe of an identity map and
 * nothing read off the state. A state is never asked for its properties while drawing. A pack whose files pick no block
 * apart has nothing here and pays one volatile read.
 */
public final class StateSets {

    /** The places in a block's row. */
    static final int PLACES = 16;

    /** One different family and look among a block's states, past its own. */
    static final class Variant {

        final Block block;

        /** Its place in the block's row, one or more. */
        final int meta;

        /** A state that wears it, which answers for its look. */
        final BlockState sample;

        final SurfaceFamily family;

        Variant(Block block, int meta, BlockState sample, SurfaceFamily family) {
            this.block = block;
            this.meta = meta;
            this.sample = sample;
            this.family = family;
        }
    }

    /** One stitch's survey, replaced whole. */
    private static final class Survey {

        static final Survey NONE = new Survey(Collections.<BlockState, Integer>emptyMap(),
            Collections.<Variant>emptyList());

        final Map<BlockState, Integer> places;

        final List<Variant> variants;

        Survey(Map<BlockState, Integer> places, List<Variant> variants) {
            this.places = places;
            this.variants = variants;
        }
    }

    private static volatile Survey survey = Survey.NONE;

    private StateSets() {}

    /** The place in its block's row this state's pictures are filed at; nought for all but a state set apart. */
    public static int metaOf(BlockState state) {
        Map<BlockState, Integer> places = survey.places;
        if (places.isEmpty() || state == null) return 0;
        Integer place = places.get(state);
        return place == null ? 0 : place.intValue();
    }

    /** The state that answers for a place of a block's row, or null for the block's own place. */
    static BlockState sample(Block block, int meta) {
        if (meta == 0) return null;
        for (Variant each : survey.variants) {
            if (each.block == block && each.meta == meta) return each.sample;
        }
        return null;
    }

    /** Every place past a block's own, in the order the stitch will plan them. */
    static List<Variant> variants() {
        return survey.variants;
    }

    /** Looks at every state of each block the files pick apart, as a stitch begins. Render thread. */
    static void survey(List<SurfaceRegistry.SurfaceState> surfaces) {
        Map<BlockState, Integer> places = new IdentityHashMap<BlockState, Integer>();
        List<Variant> variants = new ArrayList<Variant>();
        for (SurfaceRegistry.SurfaceState surface : surfaces) {
            Block block = surface.block;
            if (!SurfaceRegistry.hasStatesApart(block) && !BlockLooks.picksStates(surface.registryName)) continue;
            Map<String, Integer> kept = new LinkedHashMap<String, Integer>();
            kept.put(keyOf(block.defaultBlockState()), Integer.valueOf(0));
            boolean spoken = false;
            for (BlockState state : block.getStateDefinition()
                .getPossibleStates()) {
                SurfaceFamily family = SurfaceRegistry.familyOf(state);
                if (family == null || !family.staged) continue;
                String key = keyOf(state);
                Integer place = kept.get(key);
                if (place == null) {
                    if (kept.size() >= PLACES) {
                        if (!spoken) {
                            spoken = true;
                            Trmt.LOG.warn(
                                "{} asks for more than {} different wear pictures among its block states; the rest draw the block's own",
                                surface.registryName,
                                Integer.valueOf(PLACES));
                        }
                        continue;
                    }
                    place = Integer.valueOf(kept.size());
                    kept.put(key, place);
                    variants.add(new Variant(block, place.intValue(), state, family));
                }
                if (place.intValue() != 0) places.put(state, place);
            }
        }
        survey = places.isEmpty() ? Survey.NONE : new Survey(places, Collections.unmodifiableList(variants));
    }

    /** What decides a state's pictures: the family it wears as and every part of its look. */
    private static String keyOf(BlockState state) {
        SurfaceFamily family = SurfaceRegistry.familyOf(state);
        return (family == null ? "-" : family.key()) + BlockLooks.of(state)
            .setKey();
    }
}
