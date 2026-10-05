package com.trmtgtnh.forge;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.IDynamicBakedModel;
import net.minecraftforge.client.model.data.IModelData;

import com.trmtgtnh.Client;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.model.GhostQuads;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.Rotations;

/**
 * The ghost's model under Forge, which has to be told where it is.
 *
 * <p>
 * <strong>The expensive side of the fork.</strong> Fabric hands a model the {@code BlockPos} it is
 * drawing and its half needs nothing else. Forge at this version hands a model {@code IModelData},
 * and nothing supplies model data for a block that is not a block entity - so the position comes
 * from {@link GhostSeat}, which {@code MixinModelBlockRenderer} fills on the way past. That mixin is
 * the whole cost, and the rendering spike went looking for a cheaper door before accepting it.
 *
 * <p>
 * Nothing seated means nothing drawn rather than something wrong. A model asked for quads outside a
 * chunk mesh - an item, a screen, a mod walking the model registry - has no square to answer for,
 * and a guess there would be some other square's wear.
 */
public class GhostModelForge implements IDynamicBakedModel {

    private final BakedModel fallback;

    public GhostModelForge(BakedModel fallback) {
        this.fallback = fallback;
    }

    @Override
    @Nonnull
    public List<BakedQuad> getQuads(BlockState state, Direction side, @Nonnull Random random,
        @Nonnull IModelData data) {
        BlockAndTintGetter level = GhostSeat.level();
        BlockPos pos = GhostSeat.pos();
        countSeat(level != null && pos != null);
        if (level == null || pos == null) return Collections.emptyList();

        short record = Client.ghostRecordAt(level, pos.getX(), pos.getY(), pos.getZ());
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        int outline = BlockGhost.outlineAt(level, pos, origin);
        // Salted by depth, as the top face is, so a rut does not replay its first run's turns at
        // every pixel it sinks.
        int rotation = Rotations.forPosition(pos.getX(), pos.getZ(), ErosionState.sinkOf(record));
        int fringeTurn = Rotations.forPosition(pos.getX(), pos.getZ());
        boolean snowed = BlockGhost.snowedAt(level, pos);

        return GhostQuads.build(record, origin, outline, rotation, fringeTurn, snowed, side);
    }

    /** How many times this model has been asked for quads, and how many of those had a seat. */
    private static final java.util.concurrent.atomic.AtomicLong ASKED =
        new java.util.concurrent.atomic.AtomicLong();

    private static final java.util.concurrent.atomic.AtomicLong WITH_SEAT =
        new java.util.concurrent.atomic.AtomicLong();

    private static boolean warned;

    /**
     * Says so, once, if this model is being asked for quads and never seated.
     *
     * <p>
     * A guard rather than a diagnostic, and it exists because the thing it guards against happened
     * and nothing noticed for months. The seat was taken by a mixin on
     * {@code ModelBlockRenderer.tesselateBlock}, which Forge's render path does not use - it goes
     * through {@code BlockRenderDispatcher.renderModel} into {@code ForgeBlockModelRenderer} - so
     * this model was asked five hundred times a run, answered an empty list every time, and every
     * worn square in the world drew nothing at all.
     *
     * <p>
     * None of the usual alarms could fire. The mixin applied: its config requires injections to
     * match, and a handler given a deliberately wrong signature was refused outright, so the method
     * was found and injected - it was simply never called. And the hole it left looked so much like
     * the feature working that the spike photographed it, measured the rut it could stand in, and
     * called it a success. What somebody eventually noticed was not that the ground was missing but
     * that it was <em>too dark</em>: the floor in those pictures is the top of the block underneath
     * and the walls are the sides of the neighbours, both lit as the inside of a pit.
     *
     * <p>
     * Which is why this counts rather than trusts. Two hundred asks without a single seat is not a
     * slow start, it is a renderer this mod is not on the path of - and the whole cost of knowing
     * is one increment per square per rebuild.
     */
    private static void countSeat(boolean seated) {
        long total = ASKED.incrementAndGet();
        if (seated) WITH_SEAT.incrementAndGet();
        if (warned || total < 200L || WITH_SEAT.get() > 0L) return;
        warned = true;
        com.trmtgtnh.Trmt.LOG.error(
            "The ghost model has been asked for quads {} times and not once was it told which square it is "
                + "drawing, so every worn square is drawing nothing at all. The seat is taken by a mixin on "
                + "BlockRenderDispatcher.renderModel; if another mod has replaced the block renderer, or a later "
                + "version moves that method, the mixin will still apply and still never be called. In game that "
                + "looks like a hole cut in the ground rather than a path - see MixinBlockRenderDispatcher.",
            Long.valueOf(total));
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    @Nonnull
    public TextureAtlasSprite getParticleIcon() {
        return fallback.getParticleIcon();
    }

    @Override
    @Nonnull
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    @Nonnull
    public ItemTransforms getTransforms() {
        // The fallback's, which is the plain cube this replaced. Nothing holds a ghost, so nothing
        // should ever ask - but a model that cannot answer this does not load at all.
        return fallback.getTransforms();
    }
}
