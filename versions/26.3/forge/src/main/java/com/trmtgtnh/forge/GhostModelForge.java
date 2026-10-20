package com.trmtgtnh.forge;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

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
 * drawing and its half needs nothing else. Forge at this version hands a model {@code ModelData},
 * and the <em>manager</em> that gathers it only knows about block entities - a tile entity per worn
 * square is not an option, there are thousands on a road. But the model itself is asked, per square,
 * by {@link #getModelData}, and may answer with whatever it likes. So that is where the square is
 * put, and {@link GhostSeat} is the second way in rather than the only one.
 *
 * <p>
 * <strong>Two doors, because one of them is not always open.</strong> The seat is filled by a mixin
 * on {@code BlockRenderDispatcher.renderBatched} (1.16.5's renderModel; by 26.1 Forge's
 * {@code ModelBlockRenderer.tesselateBlock}), which is vanilla's path and not everybody's:
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
 *
 * <p>
 * <strong>From 1.21.4 a block's model is a {@code BlockStateModel} that hands the renderer parts</strong>, and Forge
 * 64 asks it for those parts with the model data (IForgeBlockStateModel.collectParts), having asked it for that data
 * square by square first (getModelData, from SectionCompiler). So the square arrives the same way it did, and the
 * whole square - every side and the unculled faces, already carrying each quad's pass and tint slot - is handed back as
 * one part ({@code GhostQuads.part}). No pass is turned away any more: 26.1's chunk compiler sorts quads by their own
 * layer, so the 1.21.1 check against the pass being built has nothing left to check.
 */
public class GhostModelForge implements BlockStateModel {

    private final BlockStateModel fallback;

    public GhostModelForge(BlockStateModel fallback) {
        this.fallback = fallback;
    }

    /**
     * Which square this is, handed to the model by the renderer that is about to draw it.
     *
     * <p>
     * Forge calls this once per block per chunk rebuild, with the position and the world slice being
     * meshed, and takes what comes back to {@code collectParts}. Nothing else carries it: the data a
     * block entity would have put here is kept and passed on, with the square added under a property of this
     * model's own, so a renderer that wanted it still finds it. (On 1.16.5 the data was an interface and the square
     * was a class of it; from Forge 41 it is one final class of properties.)
     */
    @Override
    @Nonnull
    public ModelData getModelData(@Nonnull BlockAndTintGetter level, @Nonnull BlockPos pos, @Nonnull BlockState state,
        @Nonnull ModelData tileData) {
        return tileData.derive()
            .with(SQUARE, new Square(level, pos.immutable()))
            .build();
    }

    /** Asked with no data: nothing says which square, so the plain block is drawn, as with no seat below. */
    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
        collectParts(random, output, ModelData.EMPTY);
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> output, ModelData data) {
        Square square = data == null ? null : data.get(SQUARE);
        BlockAndTintGetter level = square == null ? null : square.level;
        BlockPos pos = square == null ? null : square.pos;
        if (level == null || pos == null) {
            level = GhostSeat.level();
            pos = GhostSeat.pos();
        }
        countSeat(level != null && pos != null);
        if (level == null || pos == null) {
            fallback.collectParts(random, output, data == null ? ModelData.EMPTY : data);
            return;
        }

        short record = Client.ghostRecordAt(level, pos.getX(), pos.getY(), pos.getZ());
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());

        int outline = BlockGhost.outlineAt(level, pos, origin);
        // Salted by depth, as the top face is, so a rut does not replay its first run's turns at
        // every pixel it sinks.
        int rotation = Rotations.forPosition(pos.getX(), pos.getZ(), ErosionState.sinkOf(record));
        int fringeTurn = Rotations.forPosition(pos.getX(), pos.getZ());
        boolean snowed = BlockGhost.snowedAt(level, pos);

        output.add(
            GhostQuads.part(
                record,
                origin,
                outline,
                rotation,
                fringeTurn,
                snowed,
                BlockGhost.stairBoxesAt(level, pos, origin),
                particleMaterial().sprite()));
    }

    /**
     * One square, carried from {@link #getModelData} to {@link #collectParts} under {@link #SQUARE}, a property no
     * other model holds, beside whatever a block entity would have put there, which is passed through untouched.
     */
    private static final class Square {

        final BlockAndTintGetter level;

        final BlockPos pos;

        Square(BlockAndTintGetter level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }
    }

    /** Where this model keeps its square in the data it is handed back. */
    private static final ModelProperty<Square> SQUARE = new ModelProperty<Square>();

    /** How many times this model has been asked for its parts, and how many of those had a seat. */
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
        com.trmtgtnh.Trmt.error(
            "The ghost model has been asked for its parts {} times and not once was it told which square it is "
                + "drawing, by either of the two ways it can be: the model data this model answers in "
                + "getModelData, which any Forge-aware renderer asks for, or the seat a mixin on "
                + "ModelBlockRenderer.tesselateBlock fills on vanilla's path. A renderer that does neither "
                + "leaves every worn square drawn as plain ground - no wear anywhere, though not the hole in "
                + "the world this used to be. See GhostModelForge and MixinBlockRenderDispatcher.",
            Long.valueOf(total));
    }

    @Override
    @Nonnull
    public Material.Baked particleMaterial() {
        // The fallback's, unless a ghost square's dust is being spawned, which takes that square's family's
        // picture - the 1.7.10 edition's, side by side (0.9.223, spec GF19; see GhostDust).
        Material.Baked own = fallback.particleMaterial();
        TextureAtlasSprite sprite = com.trmtgtnh.client.model.GhostDust.sprite(own.sprite());
        return sprite == own.sprite() ? own : new Material.Baked(sprite, own.forceTranslucent());
    }

    @Override
    @Nonnull
    public Material.Baked particleMaterial(@Nonnull ModelData data) {
        return particleMaterial();
    }

    /**
     * The fallback's: whether a model draws see-through or moving pictures is asked of a block with no square in hand
     * (an outline, a block being moved), and the plain block is what is drawn without one.
     */
    @Override
    public int materialFlags() {
        return fallback.materialFlags();
    }
}
