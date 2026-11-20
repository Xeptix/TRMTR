package com.trmtgtnh.api;

/**
 * The thread that builds chunks for drawing. False lets TRMTR draw as usual.
 */
public interface WearRenderer {

    boolean draw(WearDraw draw);
}
