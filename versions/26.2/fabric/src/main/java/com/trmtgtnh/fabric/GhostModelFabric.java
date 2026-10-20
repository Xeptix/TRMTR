package com.trmtgtnh.fabric;

import java.util.List;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Client;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.model.GhostQuads;
import com.trmtgtnh.client.render.OptiFineMaterial;
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
 * From 1.21.5 a block's baked model is a {@code BlockStateModel}, and Fabric API's renderer API (13.x at 26.1) asks
 * one for a square's quads through {@link #emitQuads}, which every FRAPI renderer calls - Indigo, and Sodium, which
 * implements the API itself. 1.21.1's {@code emitBlockQuads}, {@code isVanillaAdapter} and the baked model's other
 * questions are gone with {@code BakedModel}; what they were asked is asked here. The quads themselves are built by
 * {@link GhostQuads}, shared with the other loaders, and handed to the emitter whole - {@code fromBakedQuad} is
 * exactly the door for that.
 */
public class GhostModelFabric extends WrapperBlockStateModel {

    public GhostModelFabric(BlockStateModel fallback) {
        super(fallback);
    }

    @Override
    public void emitQuads(QuadEmitter context, BlockAndTintGetter level, BlockPos pos, BlockState state,
        RandomSource random, Predicate<Direction> cullTest) {
        // The emitter is called context because it is what 1.21.1's RenderContext was to this method: the one thing a
        // square's quads go into.
        short record = Client.ghostRecordAt(level, pos.getX(), pos.getY(), pos.getZ());
        int origin = Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        int outline = BlockGhost.outlineAt(level, pos, origin);
        // Salted by depth, as the top face is, so a rut does not replay its first run's turns at
        // every pixel it sinks.
        int rotation = Rotations.forPosition(pos.getX(), pos.getZ(), com.trmtgtnh.erosion.ErosionState.sinkOf(record));
        int fringeTurn = Rotations.forPosition(pos.getX(), pos.getZ());
        boolean snowed = BlockGhost.snowedAt(level, pos);

        // Asked once without a side and once per side, which is how both older editions are asked and
        // what decides whether a face can be culled away. See emit.
        java.util.List<net.minecraft.world.phys.AABB> stairs = BlockGhost.stairBoxesAt(level, pos, origin);
        // The pass this square belongs in is carried on every quad it emits - from 26.1 inside the quad itself
        // (GhostQuads gives each one GhostLayers.of(origin)), where 1.21.1 said it as a material here.
        //
        // Under Canvas, the material the block this square claims to be was given, in that same pass. FrexMaterial
        // answers whether there is anything to give it to; at 26.1.2 there is not (see FrexMaterial), so this asks
        // and goes on with the square's own quads, as 1.21.1 did wherever FREX was absent.
        if (FrexMaterial.active()) {
            FrexMaterial.of(GhostQuads.claimOf(record, origin, rotation), com.trmtgtnh.client.GhostLayers.of(origin));
        }
        // Under OptiFabric, OptiFine never pushes a shader entry for a model drawn this way - Indigo hands it here
        // from OptiFine's chunk rebuild before OptiFine's own push - so the block this square claims to be goes on
        // the stack of the very buffer its quads land in, and comes off after. Nowhere else is there a buffer to
        // push onto, or anything to push it for. See OptiFabricEntry, which at 26.1.2 finds no such buffer.
        com.mojang.blaze3d.vertex.VertexConsumer optifine = OptiFineMaterial.pushes()
            ? OptiFabricEntry.bufferOf(context, com.trmtgtnh.client.GhostLayers.of(origin))
            : null;
        if (optifine != null) OptiFineMaterial.pushClaim(GhostQuads.claimOf(record, origin, rotation), optifine);
        try {
            emit(
                context,
                GhostQuads.build(record, origin, outline, rotation, fringeTurn, snowed, null, stairs),
                null,
                cullTest);
            for (Direction side : Direction.values()) {
                emit(
                    context,
                    GhostQuads.build(record, origin, outline, rotation, fringeTurn, snowed, side, stairs),
                    side,
                    cullTest);
            }
        } finally {
            if (optifine != null) OptiFineMaterial.popClaim(optifine);
        }
    }

    /**
     * Hands built quads to the emitter, which is what {@code fromBakedQuad} is for.
     *
     * <p>
     * <strong>The cull face is the argument, not the quad's own direction, and that distinction is
     * load-bearing.</strong> Forge's renderer takes the side a model was asked for as the side to cull
     * against, and a model asked without one gets its quads kept whatever the neighbours are doing.
     * Fabric's emitter has no such call and the face has to be said outright - so a top face that has
     * sunk below the ceiling of its own cell must be emitted with no cull face at all. Reading it off
     * the quad's direction would say UP, and every sunken square in the world would be culled away
     * by the air above it: a road that vanishes as it wears, which is the opposite of the point.
     *
     * <p>
     * From Fabric API's renderer API 5 (1.21.5) a model does its own culling, with the test it is handed: a side the
     * test says is hidden is not emitted, exactly as Fabric encodes a vanilla model part
     * ({@code VanillaBlockModelPartEncoder}, read from Fabric API 0.155.3's jar), and the quads asked for with no
     * side are never tested. The cull face is still said on each quad, as there. {@code fromBakedQuad} sets the
     * direction as the nominal face, which is what the renderer lights the quad by, and the tint slot, the pass and
     * the shading the quad carries.
     *
     * <h2>One emitter, held</h2>
     *
     * <p>
     * <strong>An emitter is one reusable quad.</strong> Every property is set on it and then it is emitted, once per
     * quad. 1.21.1 asked a render context for it, and asking once per property wiped each property as the next was
     * set, so {@code emit()} published an empty quad: every worn square in the world drew nothing on this loader for
     * months, and it looked so much like the feature working - a sunken, correctly-shaped, earth-dark hollow - that
     * the rendering spike photographed it and called it a success. From 26.1 the emitter is handed in and there is
     * nothing to ask twice, but the order is kept: everything set, then one emit. See {@code GhostModelForge}, which
     * had a different fault with the same symptom at the same time.
     *
     * <p>
     * 1.21.1 also set each quad one attribute at a time under OptiFabric, because {@code fromVanilla} copied a packed
     * vertex array at a stride OptiFine could change. From 1.21.5 a quad is a record of positions and packed texture
     * coordinates, with no array and no stride to read, so there is nothing for that path to step around.
     *
     * <p>
     * Ambient occlusion as the renderer decides and vanilla's shading: what 1.21.1's ghost model said
     * ({@code useAmbientOcclusion} true), as Fabric encodes a vanilla part that asks for it.
     */
    private static void emit(QuadEmitter context, List<BakedQuad> quads, Direction cull, Predicate<Direction> cullTest) {
        if (cull != null && cullTest.test(cull)) return;
        for (BakedQuad quad : quads) {
            context.cullFace(cull);
            context.fromBakedQuad(quad);
            context.ambientOcclusion(TriState.DEFAULT);
            context.shadeMode(ShadeMode.VANILLA);
            context.emit();
        }
    }

    /**
     * No key: every square's geometry is its own.
     *
     * <p>
     * From Fabric API's renderer API 5 a model may name its geometry, so a renderer can keep what it built and reuse
     * it for another square with the same key. A ghost's picture depends on the record held for its own square, which
     * no key made from the state can say, so none is given - null, which is "do not reuse" - and every square is
     * asked, as 1.21.1 asked every square.
     */
    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return null;
    }

    // ------------------------------------------------------------------
    // The vanilla half, which nothing should reach but everything must answer
    // ------------------------------------------------------------------

    /*
     * The plain block, for a renderer that does not speak the mesh API, is the wrapped model's own parts
     * (WrapperBlockStateModel.collectParts, which this does not override) - 1.21.1's getQuads answered with the
     * fallback's quads for the same reason.
     *
     * A FRAPI renderer - Indigo, or Sodium - calls emitQuads and never that. One that does not implement FRAPI has no
     * way to be told which square it is drawing, and there is no position in that signature to read.
     *
     * It used to answer with nothing, and nothing is the worst available answer - the ghost has
     * replaced the ground in the client's own copy of the world, so no quads means a hole you can
     * see the sky through rather than a path. That is exactly what Forge's half did on 2026-10-06
     * with Rubidium installed, photographed in a real instance, and this is the same fault waiting on
     * this side. Unworn ground says "this renderer draws no wear"; a hole says "this mod is broken".
     */

    @Override
    public Material.Baked particleMaterial() {
        // The fallback's, unless a ghost square's dust is being spawned, which takes that square's family's
        // picture - the 1.7.10 edition's, side by side (0.9.223, spec GF19; see GhostDust). From 1.21.5 a model's
        // particle is a baked material (a sprite and whether it is forced translucent) rather than a sprite; the
        // sprite is what is chosen, the rest kept.
        return dust(super.particleMaterial());
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return dust(super.particleMaterial(level, pos, state));
    }

    private static Material.Baked dust(Material.Baked unheld) {
        if (unheld == null) return null;
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = com.trmtgtnh.client.model.GhostDust
            .sprite(unheld.sprite());
        return sprite == unheld.sprite() ? unheld : new Material.Baked(sprite, unheld.forceTranslucent());
    }

    /*
     * 1.21.1's getTransforms, getOverrides, isGui3d, usesBlockLight, isCustomRenderer and emitItemQuads have no
     * counterpart: from 1.21.4 an item's picture is its own item model, not a block model asked about its item form,
     * and a ghost has no item form (see BlockGhost's constructor).
     */
}
