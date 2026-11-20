package com.trmtgtnh.api;

/**
 * Only valid during draw. No world access: everything needed is here.
 *
 * <p>
 * Positions are in block units, 0 to 1 on each axis from the block's lower corner (its smallest x, y and z); y 1 is
 * the ground's drawn top, which is below the cell's once the ground has sunk. A face is numbered as the game numbers
 * directions: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east. Nothing given to {@link #quad} or {@link #box} is drawn
 * until {@code draw} returns true; returning false, or throwing, draws none of it.
 */
@ImplementedByTrmtr
public interface WearDraw {

    int x();

    int y();

    int z();

    /** The block, in the edition's own spelling. */
    String block();

    /** Its record. */
    Wear wear();

    /** The block's own sprite on that face, as the edition's own object. */
    Object sprite(int face);

    /** The block's own tint on that face. */
    int tint(int face);

    /** The light there. */
    int light();

    /**
     * Adds one quad over what is left of the block: four corners, x y z each, in the order they are drawn, counter-
     * clockwise seen from the side it faces; uv four pairs, 0 to 1 across the block's own sprite on {@code face}; tint
     * 0xRRGGBB, multiplied with the face's light.
     */
    void quad(float[] xyz, float[] uv, int face, int tint);

    /**
     * Cuts a box out of the block. Clamped to the block; corners either way round. TRMTR draws what is left: the
     * block's
     * faces where they still stand, and the floor and walls the cut opens, which take the picture TRMTR draws at
     * {@code stage} (0 or less: the block's own sprite). Up to 16 boxes a draw.
     */
    void box(float x0, float y0, float z0, float x1, float y1, float z1, int stage);

    /** The edition's own drawing object, for a mod that needs more than quad and box. */
    Object nativeContext();
}
