package com.trmtgtnh.item;

/**
 * A tamper tool whose left-click means the same thing when it lands on nothing.
 *
 * <p>
 * Left-click reaches a tool through {@link TamperEvents}, which listens for
 * {@code LEFT_CLICK_BLOCK} and so hears nothing at all when the crosshair is on sky. For the two
 * tools whose {@link TamperTool#leftClick} ignores the position entirely - the comparison tool and
 * the snapshot tool - that is a gesture lost for no reason, so they say so here and an air swing is
 * routed to them instead.
 *
 * <p>
 * A marker rather than a method on {@code TamperTool}, and opt-in rather than opt-out, because the
 * tampers proper read the square they were pointed at: an air swing has nothing to give them, and a
 * tool added later should have to think about that rather than inherit an answer.
 */
public interface AirSwingTool extends TamperTool {
}
