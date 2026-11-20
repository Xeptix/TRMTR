package com.trmtgtnh.apihost;

import com.trmtgtnh.api.WearContext;

/**
 * What a bit of wear is about to land on, as {@code WearListener.beforeWear} is told it. Made fresh for each call, so
 * a plugin keeping one keeps a true account of that one bit of wear. Portable: the same file in all eight trees.
 */
public final class WearHere implements WearContext {

    private final Object level;
    private final int x;
    private final int y;
    private final int z;
    private final String block;
    private final Object entity;
    private final Cause cause;

    public WearHere(Object level, int x, int y, int z, String block, Object entity, Cause cause) {
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
        this.block = block;
        this.entity = entity;
        this.cause = cause == null ? Cause.STEP : cause;
    }

    @Override
    public Object level() {
        return level;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    @Override
    public int z() {
        return z;
    }

    @Override
    public String block() {
        return block;
    }

    @Override
    public Object entity() {
        return entity;
    }

    @Override
    public Cause cause() {
        return cause;
    }
}
