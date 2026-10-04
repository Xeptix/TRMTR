package com.trmtgtnh.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a shaped surface begins and ends.
 *
 * <p>
 * Deriving this from the shape and the metadata rather than from the block's own bounds is the
 * whole point: those fields are shared mutable state that only mean anything immediately after
 * {@code setBlockBoundsBasedOnState}, and the two sides that have to agree about where the ground
 * is do not call it at the same moments. What is worth testing is that the derivation is right,
 * and that a full block still gets exactly the cell it always had.
 */
class SurfaceShapeTest {

    @Test
    @DisplayName("a full block occupies its whole cell, whatever its metadata says")
    void aFullBlockIsUnchanged() {
        for (int meta = 0; meta < 16; meta++) {
            assertEquals(0.0D, SurfaceShape.bottomOf(SurfaceShape.FULL, meta), 0.0D, "meta " + meta);
            assertEquals(1.0D, SurfaceShape.topOf(SurfaceShape.FULL, meta), 0.0D, "meta " + meta);
        }
    }

    @Test
    @DisplayName("a slab resting on the floor is the bottom half, and one hung from the ceiling the top")
    void aSlabTakesItsHalf() {
        for (int meta = 0; meta < 8; meta++) {
            assertEquals(0.0D, SurfaceShape.bottomOf(SurfaceShape.SLAB, meta), 0.0D, "lower slab, meta " + meta);
            assertEquals(0.5D, SurfaceShape.topOf(SurfaceShape.SLAB, meta), 0.0D, "lower slab, meta " + meta);
        }
        for (int meta = 8; meta < 16; meta++) {
            assertEquals(0.5D, SurfaceShape.bottomOf(SurfaceShape.SLAB, meta), 0.0D, "upper slab, meta " + meta);
            assertEquals(1.0D, SurfaceShape.topOf(SurfaceShape.SLAB, meta), 0.0D, "upper slab, meta " + meta);
        }
    }

    @Test
    @DisplayName("half a block deep is exactly what a slab has to give up")
    void aSlabIsNeverWornThrough() {
        for (int meta = 0; meta < 16; meta++) {
            double thickness = SurfaceShape.topOf(SurfaceShape.SLAB, meta)
                - SurfaceShape.bottomOf(SurfaceShape.SLAB, meta);
            assertEquals(0.5D, thickness, 0.0D, "meta " + meta);
        }
    }

    @Test
    @DisplayName("only a full block fills its cell, and only a full block sinks its whole depth")
    void whichShapesArePartial() {
        assertFalse(SurfaceShape.FULL.isPartial());
        assertFalse(SurfaceShape.FULL.isHalfDepth());
        assertTrue(SurfaceShape.SLAB.isPartial());
        assertTrue(SurfaceShape.SLAB.isHalfDepth());
        assertTrue(SurfaceShape.STAIR.isPartial());
        assertTrue(SurfaceShape.STAIR.isHalfDepth());
    }

    @Test
    @DisplayName("nothing is a shape without a block to be one")
    void nullIsOrdinaryGround() {
        assertEquals(SurfaceShape.FULL, SurfaceShape.of(null));
    }
}
