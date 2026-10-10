package com.trmtgtnh.block;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

/**
 * The world with one square handed back to the block a ghost stands in for - the 1.7.10 edition's class of the same
 * name, carried in 0.9.222 for the questions a ghost now passes on (spec GF12, GF15).
 *
 * <p>
 * A covered block asked about itself through the real world would find the ghost at its own square: Forge's harvest
 * check reads the state there before it asks anything else, and a block deciding what a plant may stand on may look
 * too. Through this it finds itself. Everything else passes through untouched, because the neighbours really are
 * what they say they are - the water beside a reed's sand above all.
 *
 * <p>
 * Not client-only, as that edition's is not: the questions are ones either side may ask, though only a client ever
 * has a ghost to ask them of. The three members the game marks for the client are marked the same way here.
 */
final class OriginView implements IBlockAccess {

    private final IBlockAccess delegate;

    private final BlockPos at;

    private final IBlockState origin;

    OriginView(IBlockAccess delegate, BlockPos at, IBlockState origin) {
        this.delegate = delegate;
        this.at = at.toImmutable();
        this.origin = origin;
    }

    private boolean here(BlockPos pos) {
        return at.equals(pos);
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(BlockPos pos) {
        return delegate == null ? null : delegate.getTileEntity(pos);
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return delegate == null ? 0 : delegate.getCombinedLight(pos, lightValue);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        if (here(pos)) return origin;
        return delegate == null ? net.minecraft.init.Blocks.AIR.getDefaultState() : delegate.getBlockState(pos);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        if (here(pos)) return false;
        return delegate == null || delegate.isAirBlock(pos);
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public Biome getBiome(BlockPos pos) {
        return delegate == null ? net.minecraft.init.Biomes.PLAINS : delegate.getBiome(pos);
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return delegate == null ? 0 : delegate.getStrongPower(pos, direction);
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public WorldType getWorldType() {
        return delegate == null ? WorldType.DEFAULT : delegate.getWorldType();
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        if (here(pos)) return origin.isSideSolid(this, pos, side);
        return delegate == null ? _default : delegate.isSideSolid(pos, side, _default);
    }
}
