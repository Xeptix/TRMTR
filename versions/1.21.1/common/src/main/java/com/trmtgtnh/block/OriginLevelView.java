package com.trmtgtnh.block;

import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@link OriginView} for a plant that was handed a whole level: the level with one square handed back to the block a
 * ghost stands in for, and every other question - light, sky, biome, the chunk - passed to the level itself (0.9.223,
 * spec GF15).
 *
 * <p>
 * Fabric's soil hooks put a plant's own question to the covered block through a view, and until 0.9.223 that view was a
 * bare {@code BlockGetter}, so a plant whose soil test reads the light, or a mod's plant that takes the world it is
 * handed for the level it was asked from, met a view that could not answer. Forge asks the 1.7.10 edition's question
 * through the world it was handed, and so does this, wherever that world is a level: Xep, 2026-10-10, "Carry all five".
 *
 * <p>
 * Only the three questions about a square are answered here, as the narrower view answers them; everything a level
 * works out from those - whether a square is empty, its brightness, its sturdiness - comes back through them by the
 * game's own defaults, so it sees the same square.
 */
public final class OriginLevelView implements LevelReader {

    private final LevelReader delegate;

    private final OriginView square;

    public OriginLevelView(LevelReader delegate, BlockPos at, BlockState origin) {
        this.delegate = delegate;
        this.square = new OriginView(delegate, at, origin);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return square.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return square.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return square.getFluidState(pos);
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean require) {
        return delegate.getChunk(x, z, status, require);
    }

    @Override
    @Deprecated
    public boolean hasChunk(int x, int z) {
        return delegate.hasChunk(x, z);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return delegate.getHeight(type, x, z);
    }

    @Override
    public int getSkyDarken() {
        return delegate.getSkyDarken();
    }

    @Override
    public BiomeManager getBiomeManager() {
        return delegate.getBiomeManager();
    }

    @Override
    public net.minecraft.core.Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return delegate.getUncachedNoiseBiome(x, y, z);
    }

    /** The world's registries, which 1.19 asks every level reader; the world's own. */
    @Override
    public net.minecraft.core.RegistryAccess registryAccess() {
        return delegate.registryAccess();
    }

    /** Which features are on, which 1.19.3 asks every level reader; the world's own answer. */
    @Override
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
        return delegate.enabledFeatures();
    }

    @Override
    public boolean isClientSide() {
        return delegate.isClientSide();
    }

    @Override
    @Deprecated
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return delegate.dimensionType();
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return delegate.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return delegate.getBlockTint(pos, resolver);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return delegate.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(int x, int z) {
        return delegate.getChunkForCollisions(x, z);
    }

    @Override
    public java.util.List<VoxelShape> getEntityCollisions(Entity entity, AABB area) {
        return delegate.getEntityCollisions(entity, area);
    }
}
