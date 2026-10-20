package com.trmtgtnh.neoforge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;

import com.trmtgtnh.Client;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.model.GhostQuads;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.Rotations;

/**
 * The ghost's model under NeoForge, which has to be told where it is.
 *
 * <p>
 * <strong>The expensive side of the fork.</strong> Fabric hands a model the {@code BlockPos} it is
 * drawing and its half needs nothing else. Forge and NeoForge up to 1.21.1 handed a model {@code ModelData},
 * and the <em>manager</em> that gathers it only knows about block entities - a tile entity per worn
 * square is not an option, there are thousands on a road. But the model itself was asked, per square,
 * by {@code getModelData}, and could answer with whatever it liked. So that is where the square was
 * put, and {@link GhostSeat} was the second way in rather than the only one.
 *
 * <p>
 * <strong>From 1.21.5 NeoForge tells the model itself.</strong> A block's model is a {@code BlockStateModel} that hands
 * the renderer parts, and NeoForge's {@code BlockStateModelExtension.collectParts} takes the level, the position and the
 * state beside the random source - the chunk mesher's {@code ModelBlockRenderer.tesselateBlock} calls that form (read
 * from 26.1.2.122's patched jar). So the square arrives as an argument, which is the door 1.21.1's model data was, and
 * the seat is still what answers when a renderer asks the plain two-argument form, which NeoForge's
 * {@code DynamicBlockStateModel} hands on with an empty level at the origin.
 *
 * <p>
 * <strong>Two doors, because one of them is not always open.</strong> The seat is filled by a mixin on
 * {@code ModelBlockRenderer.tesselateBlock} (1.21.1's {@code BlockRenderDispatcher.renderBatched}), which is vanilla's
 * path and not everybody's: Rubidium meshed chunks itself and never called the 1.16.5 method, so with it installed the
 * seat was never taken and every worn square drew nothing - a hole cut in the ground, seen on 2026-10-06 in a real
 * instance. It did ask the model for its data, as any Forge-aware renderer should, so the square arrived that way and
 * the seat is what answers when a renderer skips both.
 *
 * <p>
 * Told neither way, the plain block is drawn rather than nothing. A model asked for parts outside a
 * chunk mesh - an item, a screen, a mod walking the model registry - has no square to answer for,
 * and a guess there would be some other square's wear; but an empty list is a hole you can see the
 * sky through, and unworn ground is the better wrong answer.
 *
 * <p>
 * No pass is turned away here any more, where 1.21.1's {@code getQuads} handed back nothing in the passes that were not
 * the square's: from 1.21.5 every quad carries its own chunk layer and the mesher sorts them by it, so the square's
 * quads all carry the square's one pass (GhostQuads, GhostLayers) and a square still draws in one pass, as on 1.21.1.
 */
public class GhostModelNeoForge implements DynamicBlockStateModel {

    private final BlockStateModel fallback;

    public GhostModelNeoForge(BlockStateModel fallback) {
        this.fallback = fallback;
    }

    /**
     * What decides this square's geometry, so a renderer that keeps geometry by key never hands one square's wear to
     * another.
     *
     * <p>
     * New with NeoForge's block state models (1.21.5): a model that answers null cannot be cached, and one that answers
     * the same key for two squares is drawn the same at both. The key is exactly what {@link GhostQuads#build} is given -
     * the record, what the square stands in for, its outline, the two turns, the snow and the stair's boxes - so two
     * squares share geometry only where 1.21.1 would have built them the same quads. Unseated, the plain block's own key.
     */
    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        Inputs inputs = inputs(level, pos);
        if (inputs == null) return fallback.createGeometryKey(level, pos, state, random);
        return inputs;
    }

    /**
     * The square's parts, or the plain block's where no square is known.
     *
     * <p>
     * 1.21.1's {@code getQuads} was asked side by side; the same quads for every side and for none are one part here
     * ({@link GhostQuads#part}), so every rule behind them is 1.21.1's.
     */
    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
        List<BlockStateModelPart> parts) {
        Inputs inputs = inputs(level, pos);
        if (inputs == null) {
            fallback.collectParts(level, pos, state, random, parts);
            return;
        }
        parts.add(
            GhostQuads.part(
                inputs.record,
                inputs.origin,
                inputs.outline,
                inputs.rotation,
                inputs.fringeTurn,
                inputs.snowed,
                inputs.boxes,
                fallback.particleMaterial()
                    .sprite()));
    }

    /**
     * Everything the quads are built from, read at the square - or null when there is no square, which is the empty
     * level of the two-argument form with no seat taken.
     */
    private static Inputs inputs(BlockAndTintGetter level, BlockPos pos) {
        // The plain two-argument form arrives as the empty level at the origin (DynamicBlockStateModel); that is no
        // square, and the seat answers for it if one is taken.
        if (level == null || level == BlockAndTintGetter.EMPTY || pos == null) {
            level = GhostSeat.level();
            pos = GhostSeat.pos();
        }
        countSeat(level != null && pos != null);
        if (level == null || pos == null) return null;

        short record = Client.ghostRecordAt(level, pos.getX(), pos.getY(), pos.getZ());
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        int outline = BlockGhost.outlineAt(level, pos, origin);
        // Salted by depth, as the top face is, so a rut does not replay its first run's turns at
        // every pixel it sinks.
        int rotation = Rotations.forPosition(pos.getX(), pos.getZ(), ErosionState.sinkOf(record));
        int fringeTurn = Rotations.forPosition(pos.getX(), pos.getZ());
        boolean snowed = BlockGhost.snowedAt(level, pos);
        List<AABB> boxes = BlockGhost.stairBoxesAt(level, pos, origin);
        return new Inputs(record, origin, outline, rotation, fringeTurn, snowed,
            boxes == null ? null : new ArrayList<AABB>(boxes));
    }

    /** One square's inputs, which are also its geometry key: equal inputs build equal quads. */
    private record Inputs(short record, int origin, int outline, int rotation, int fringeTurn, boolean snowed,
        List<AABB> boxes) {}

    /** How many times this model has been asked for parts, and how many of those had a square. */
    private static final java.util.concurrent.atomic.AtomicLong ASKED = new java.util.concurrent.atomic.AtomicLong();

    private static final java.util.concurrent.atomic.AtomicLong WITH_SEAT = new java.util.concurrent.atomic.AtomicLong();

    private static boolean warned;

    /**
     * Says so, once, if this model is being asked for parts and never told its square.
     *
     * <p>
     * A guard rather than a diagnostic, and it exists because the thing it guards against happened
     * and nothing noticed for months. The seat was taken by a mixin on
     * {@code ModelBlockRenderer.tesselateBlock}, which Forge's 1.16.5 render path did not use - it went
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
     * Which is why this counts rather than trusts. Two hundred asks without a single square is not a
     * slow start, it is a renderer this mod is not on the path of - and the whole cost of knowing
     * is one increment per square per rebuild.
     */
    private static void countSeat(boolean seated) {
        long total = ASKED.incrementAndGet();
        if (seated) WITH_SEAT.incrementAndGet();
        if (warned || total < 200L || WITH_SEAT.get() > 0L) return;
        warned = true;
        com.trmtgtnh.Trmt.error(
            "The ghost model has been asked for quads {} times and not once was it told which square it is "
                + "drawing, by either of the two ways it can be: the level and position NeoForge's collectParts "
                + "hands a model, which any NeoForge-aware renderer passes, or the seat a mixin on "
                + "ModelBlockRenderer.tesselateBlock fills on vanilla's path. A renderer that does neither "
                + "leaves every worn square drawn as plain ground - no wear anywhere, though not the hole in "
                + "the world this used to be. See GhostModelNeoForge and MixinBlockRenderDispatcher.",
            Long.valueOf(total));
    }

    /**
     * The particle picture: the fallback's, unless a ghost square's dust is being spawned, which takes that square's
     * family's picture - the 1.7.10 edition's, side by side (0.9.223, spec GF19; see GhostDust). From 1.21.5 a model
     * answers a material, a sprite and whether it is forced see-through, where 1.21.1 answered the sprite alone.
     */
    @Override
    @Deprecated
    public Material.Baked particleMaterial() {
        Material.Baked plain = fallback.particleMaterial();
        return new Material.Baked(com.trmtgtnh.client.model.GhostDust.sprite(plain.sprite()), plain.forceTranslucent());
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return particleMaterial();
    }

    /**
     * Whether any quad here is see-through or animated - new in 1.21.5, where 1.21.1 had no such question. Without a
     * square, the plain block's answer; at one, the square's own quads'.
     */
    @Override
    @Deprecated
    public int materialFlags() {
        return fallback.materialFlags();
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        List<BlockStateModelPart> parts = new ArrayList<BlockStateModelPart>(1);
        collectParts(level, pos, state, RandomSource.create(42L), parts);
        int flags = 0;
        for (BlockStateModelPart part : parts) flags |= part.materialFlags();
        return flags;
    }
}
