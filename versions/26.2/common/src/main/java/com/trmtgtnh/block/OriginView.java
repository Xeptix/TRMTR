package com.trmtgtnh.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * The world with one square handed back to the block a ghost stands in for - the 1.7.10 edition's class of the same
 * name, carried in 0.9.222 for the questions a ghost now passes on (spec GF12, GF15).
 *
 * <p>
 * A covered block asked about itself through the real world would find the ghost at its own square, and a block that
 * asked the ghost a question there would come straight back into it. Through this it finds itself. Everything else
 * passes through untouched, because the neighbours really are what they say they are - the water beside a reed's sand
 * above all.
 *
 * <p>
 * Only the three questions a view must answer are answered here; every other one a {@code BlockGetter} can be asked is
 * worked out from these by the game's own defaults, so it sees the same square.
 */
public final class OriginView implements BlockGetter {

    private final BlockGetter delegate;

    private final BlockPos at;

    private final BlockState origin;

    public OriginView(BlockGetter delegate, BlockPos at, BlockState origin) {
        this.delegate = delegate;
        this.at = at.immutable();
        this.origin = origin;
    }

    /** The world's own height, which 1.17 asks every block getter: the view sees the same world. */
    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    /** The world's lowest block - getMinBuildHeight until 1.21.2, getMinY after. */
    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    private boolean here(BlockPos pos) {
        return at.equals(pos);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return delegate == null ? null : delegate.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (here(pos)) return origin;
        return delegate == null ? Blocks.AIR.defaultBlockState() : delegate.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (here(pos)) return origin.getFluidState();
        return delegate == null ? Blocks.AIR.defaultBlockState()
            .getFluidState() : delegate.getFluidState(pos);
    }

    /**
     * The world's highest block. Until 1.21.2 the question was getMaxBuildHeight, the first height above the world,
     * answered as the top plus one; from 1.21.2 it is getMaxY, the top itself, so the same square is named.
     */
    @Override
    public int getMaxY() {
        // Asked through Heights, as every world height in this edition is (0.9.223).
        return com.trmtgtnh.util.Heights.top(delegate);
    }
}
