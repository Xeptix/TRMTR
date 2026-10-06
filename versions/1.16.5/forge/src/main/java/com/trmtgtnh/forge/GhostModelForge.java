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
 * and the <em>manager</em> that gathers it only knows about block entities - a tile entity per worn
 * square is not an option, there are thousands on a road. But the model itself is asked, per square,
 * by {@link #getModelData}, and may answer with whatever it likes. So that is where the square is
 * put, and {@link GhostSeat} is the second way in rather than the only one.
 *
 * <p>
 * <strong>Two doors, because one of them is not always open.</strong> The seat is filled by a mixin
 * on {@code BlockRenderDispatcher.renderModel}, which is vanilla's path and not everybody's:
 * Rubidium meshes chunks itself and never calls that method, so with it installed the seat was never
 * taken and every worn square drew nothing - a hole cut in the ground, seen on 2026-10-06 in a real
 * instance. It does ask the model for its data, as any Forge-aware renderer should, so the square
 * now arrives that way and the seat is what answers when a renderer skips both.
 *
 * <p>
 * Told neither way, the plain block is drawn rather than nothing. A model asked for quads outside a
 * chunk mesh - an item, a screen, a mod walking the model registry - has no square to answer for,
 * and a guess there would be some other square's wear; but an empty list is a hole you can see the
 * sky through, and unworn ground is the better wrong answer.
 */
public class GhostModelForge implements IDynamicBakedModel {

    private final BakedModel fallback;

    public GhostModelForge(BakedModel fallback) {
        this.fallback = fallback;
    }

    /**
     * Which square this is, handed to the model by the renderer that is about to draw it.
     *
     * <p>
     * Forge calls this once per block per chunk rebuild, with the position and the world slice being
     * meshed, and takes what comes back to {@code getQuads}. Nothing else carries it: the data a
     * block entity would have put here is kept and passed on, so a renderer that wanted it still
     * finds it.
     */
    @Override
    @Nonnull
    public IModelData getModelData(@Nonnull BlockAndTintGetter level, @Nonnull BlockPos pos,
        @Nonnull BlockState state, @Nonnull IModelData tileData) {
        return new Square(level, pos.immutable(), tileData);
    }

    @Override
    @Nonnull
    public List<BakedQuad> getQuads(BlockState state, Direction side, @Nonnull Random random,
        @Nonnull IModelData data) {
        BlockAndTintGetter level = data instanceof Square ? ((Square) data).level : null;
        BlockPos pos = level == null ? null : ((Square) data).pos;
        if (level == null || pos == null) {
            level = GhostSeat.level();
            pos = GhostSeat.pos();
        }
        countSeat(level != null && pos != null);
        if (level == null || pos == null) return fallback.getQuads(state, side, random);

        short record = Client.ghostRecordAt(level, pos.getX(), pos.getY(), pos.getZ());
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());

        // Nothing at all in the passes this square is not drawn in. The ghost claims every pass a
        // covered block might use - see ForgeClientSetup - and this is where all but one of them are
        // turned away, so worn ice is drawn with the translucent blocks and worn stone with the
        // solid ones, exactly as the blocks they stand in for are.
        //
        // Null outside a chunk rebuild, which is something else asking; those are drawn.
        net.minecraft.client.renderer.RenderType building = net.minecraftforge.client.MinecraftForgeClient
            .getRenderLayer();
        if (building != null && building != com.trmtgtnh.client.GhostLayers.of(origin)) {
            return Collections.emptyList();
        }

        int outline = BlockGhost.outlineAt(level, pos, origin);
        // Salted by depth, as the top face is, so a rut does not replay its first run's turns at
        // every pixel it sinks.
        int rotation = Rotations.forPosition(pos.getX(), pos.getZ(), ErosionState.sinkOf(record));
        int fringeTurn = Rotations.forPosition(pos.getX(), pos.getZ());
        boolean snowed = BlockGhost.snowedAt(level, pos);

        return GhostQuads.build(
            record,
            origin,
            outline,
            rotation,
            fringeTurn,
            snowed,
            side,
            BlockGhost.stairBoxesAt(level, pos, origin));
    }

    /**
     * One square, carried from {@link #getModelData} to {@link #getQuads}.
     *
     * <p>
     * It answers nothing of its own: the properties belong to whatever a block entity would have put
     * here, which is passed through untouched. This model recognises its own data by its type and
     * reads the two fields directly, so there is no property to look up and nothing to collide with.
     */
    private static final class Square implements IModelData {

        final BlockAndTintGetter level;

        final BlockPos pos;

        private final IModelData carried;

        Square(BlockAndTintGetter level, BlockPos pos, IModelData carried) {
            this.level = level;
            this.pos = pos;
            this.carried = carried;
        }

        @Override
        public boolean hasProperty(net.minecraftforge.client.model.data.ModelProperty<?> property) {
            return carried != null && carried.hasProperty(property);
        }

        @Override
        public <T> T getData(net.minecraftforge.client.model.data.ModelProperty<T> property) {
            return carried == null ? null : carried.getData(property);
        }

        @Override
        public <T> T setData(net.minecraftforge.client.model.data.ModelProperty<T> property, T data) {
            return carried == null ? null : carried.setData(property, data);
        }
    }

    /** How many times this model has been asked for quads, and how many of those had a seat. */
    private static final java.util.concurrent.atomic.AtomicLong ASKED = new java.util.concurrent.atomic.AtomicLong();

    private static final java.util.concurrent.atomic.AtomicLong WITH_SEAT = new java.util.concurrent.atomic.AtomicLong();

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
                + "drawing, by either of the two ways it can be: the model data this model answers in "
                + "getModelData, which any Forge-aware renderer asks for, or the seat a mixin on "
                + "BlockRenderDispatcher.renderModel fills on vanilla's path. A renderer that does neither "
                + "leaves every worn square drawn as plain ground - no wear anywhere, though not the hole in "
                + "the world this used to be. See GhostModelForge and MixinBlockRenderDispatcher.",
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
