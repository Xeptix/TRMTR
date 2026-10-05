package com.trmtgtnh.fabric;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Client;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.model.GhostQuads;
import com.trmtgtnh.erosion.Rotations;

/**
 * The ghost's model under Fabric, which is handed the position and so needs nothing else.
 *
 * <p>
 * <strong>This is the cheap side of the fork, and it is worth saying how cheap.</strong> Fabric's
 * renderer hands a model the {@code BlockPos} it is drawing, so everything the picture depends on
 * can simply be looked up here, at the moment it is wanted. Both older editions have to smuggle six
 * numbers through the blockstate to get them this far - Forge's unlisted properties on 1.12.2, a
 * render hook on 1.7.10 - and the Forge half of this edition has to go further still, because it
 * gets no position at all.
 *
 * <p>
 * {@code isVanillaAdapter} says false, which is what makes the renderer call
 * {@link #emitBlockQuads} rather than {@link #getQuads}. The quads themselves are built by
 * {@link GhostQuads}, shared with the other loader, and handed to the emitter in their packed form -
 * {@code fromVanilla} is exactly the door for that.
 */
public class GhostModelFabric implements BakedModel, FabricBakedModel {

    private final BakedModel fallback;

    public GhostModelFabric(BakedModel fallback) {
        this.fallback = fallback;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos,
        Supplier<Random> random, RenderContext context) {
        short record = Client.ghostRecordAt(level, pos.getX(), pos.getY(), pos.getZ());
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        int outline = BlockGhost.outlineAt(level, pos, origin);
        // Salted by depth, as the top face is, so a rut does not replay its first run's turns at
        // every pixel it sinks.
        int rotation = Rotations.forPosition(pos.getX(), pos.getZ(),
            com.trmtgtnh.erosion.ErosionState.sinkOf(record));
        int fringeTurn = Rotations.forPosition(pos.getX(), pos.getZ());
        boolean snowed = BlockGhost.snowedAt(level, pos);

        // Asked once without a side and once per side, which is how both older editions are asked and
        // what decides whether a face can be culled away. See emit.
        emit(context, GhostQuads.build(record, origin, outline, rotation, fringeTurn, snowed, null), null);
        for (Direction side : Direction.values()) {
            emit(context, GhostQuads.build(record, origin, outline, rotation, fringeTurn, snowed, side), side);
        }
    }

    /**
     * Hands packed quads to the emitter, which is what {@code fromVanilla} is for.
     *
     * <p>
     * <strong>The cull face is the argument, not the quad's own direction, and that distinction is
     * load-bearing.</strong> Forge's renderer takes the side a model was asked for as the side to cull
     * against, and a model asked without one gets its quads kept whatever the neighbours are doing.
     * Fabric's emitter has no such call and the face has to be said outright - so a top face that has
     * sunk below the ceiling of its own cell must be emitted with no cull face at all. Reading it off
     * {@code getDirection()} would say UP, and every sunken square in the world would be culled away
     * by the air above it: a road that vanishes as it wears, which is the opposite of the point.
     *
     * <p>
     * The direction is still said, as the nominal face, because that is what the renderer lights the
     * quad by.
     *
     * <h2>One emitter, held</h2>
     *
     * <p>
     * <strong>{@code getEmitter()} clears the quad it hands back.</strong> That is the documented
     * contract - it returns the context's one reusable quad, wiped and ready - and it means asking
     * for it once per property, which reads so naturally, wipes each property as the next is set.
     * This method used to do exactly that: five calls to {@code getEmitter()}, one per line, so the
     * vertices were cleared by the call that set the cull face, the cull face by the call that set
     * the nominal face, and {@code emit()} published an empty quad.
     *
     * <p>
     * Every worn square in the world drew nothing, on this loader, for months. It looked so much
     * like the feature working - a sunken, correctly-shaped, earth-dark hollow - that the rendering
     * spike photographed it and called it a success; what is actually in those pictures is the top
     * of the block underneath and the sides of the neighbours, lit as the inside of a pit. Somebody
     * watching a run said "that is a hole" and was right. See {@code GhostModelForge}, which had a
     * different fault with the same symptom at the same time.
     */
    private static void emit(RenderContext context, List<BakedQuad> quads, Direction cull) {
        for (BakedQuad quad : quads) {
            // Once, and kept. See above - this is not a tidy-up.
            QuadEmitter emitter = context.getEmitter();
            emitter.fromVanilla(quad.getVertices(), 0, false);
            emitter.cullFace(cull);
            emitter.nominalFace(quad.getDirection());
            emitter.colorIndex(quad.getTintIndex());
            emitter.emit();
        }
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> random, RenderContext context) {
        // A ghost has no item form, and nothing should ever ask. See BlockGhost's constructor.
    }

    // ------------------------------------------------------------------
    // The vanilla half, which nothing should reach but everything must answer
    // ------------------------------------------------------------------

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) {
        return Collections.emptyList();
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
    public TextureAtlasSprite getParticleIcon() {
        return fallback.getParticleIcon();
    }

    @Override
    public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        // The fallback's, which is the plain cube this replaced. Nothing holds a ghost, so nothing
        // should ever ask - but a model that cannot answer this does not load at all.
        return fallback.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
