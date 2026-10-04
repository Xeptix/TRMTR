package com.trmtgtnh.block;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The world with one position handed back to the block a ghost is standing in for.
 *
 * <p>
 * A covered block asked about itself would otherwise find a ghost there, and read a wear gradation
 * as its own metadata - which is how a modded turf came back blue at gradation one, and how
 * anything that looks at where it is standing gets a wrong answer. Everything around the position
 * passes through untouched, because the neighbours really are what they say they are.
 *
 * <p>
 * Deliberately not client-only, even though every use of it so far is drawing. The questions a
 * covered block is asked through this - what does your face look like, do you glow, what do you
 * scatter into the air - are questions either side may ask, and a class the server cannot load is
 * a class the server's half of the answer cannot use.
 */
final class OriginView implements IBlockAccess {

    private final IBlockAccess delegate;

    private final int x;

    private final int y;

    private final int z;

    private final Block origin;

    private final int originMeta;

    OriginView(IBlockAccess delegate, int x, int y, int z, Block origin, int originMeta) {
        this.delegate = delegate;
        this.x = x;
        this.y = y;
        this.z = z;
        this.origin = origin;
        this.originMeta = originMeta;
    }

    private boolean here(int atX, int atY, int atZ) {
        return atX == x && atY == y && atZ == z;
    }

    @Override
    public Block getBlock(int atX, int atY, int atZ) {
        return here(atX, atY, atZ) ? origin : delegate.getBlock(atX, atY, atZ);
    }

    @Override
    public int getBlockMetadata(int atX, int atY, int atZ) {
        return here(atX, atY, atZ) ? originMeta : delegate.getBlockMetadata(atX, atY, atZ);
    }

    @Override
    public TileEntity getTileEntity(int atX, int atY, int atZ) {
        return delegate.getTileEntity(atX, atY, atZ);
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int atX, int atY, int atZ, int min) {
        return delegate.getLightBrightnessForSkyBlocks(atX, atY, atZ, min);
    }

    @Override
    public int isBlockProvidingPowerTo(int atX, int atY, int atZ, int side) {
        return delegate.isBlockProvidingPowerTo(atX, atY, atZ, side);
    }

    @Override
    public boolean isAirBlock(int atX, int atY, int atZ) {
        return here(atX, atY, atZ) ? false : delegate.isAirBlock(atX, atY, atZ);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int atX, int atZ) {
        return delegate.getBiomeGenForCoords(atX, atZ);
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return delegate.extendedLevelsInChunkCache();
    }

    @Override
    public boolean isSideSolid(int atX, int atY, int atZ, ForgeDirection side, boolean noHit) {
        return delegate.isSideSolid(atX, atY, atZ, side, noHit);
    }
}
