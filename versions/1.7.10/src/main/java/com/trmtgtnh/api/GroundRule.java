package com.trmtgtnh.api;

/**
 * Server thread, and never inside a chunk load. False keeps the ground there from wearing. Asked before wear lands; any
 * rule saying false keeps it off.
 */
public interface GroundRule {

    boolean mayWear(Object level, int x, int y, int z, String block, Object entity);
}
