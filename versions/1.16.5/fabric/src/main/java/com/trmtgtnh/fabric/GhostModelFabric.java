package com.trmtgtnh.fabric;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
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
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<Random> random,
        RenderContext context) {
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
        // The pass this square belongs in, carried on every quad it emits. See materialFor.
        net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial material = materialFor(origin);
        // Under Canvas, the material the block this square claims to be is given, in that same pass -
        // the shader-material claim the other renderers take from a seat. See FrexMaterial.
        if (FrexMaterial.active()) {
            net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial claimed = FrexMaterial
                .of(GhostQuads.claimOf(record, origin, rotation), com.trmtgtnh.client.GhostLayers.of(origin));
            if (claimed != null) material = claimed;
        }
        // Under OptiFabric, OptiFine never pushes a shader entry for a model drawn this way - Indigo hands it here
        // from OptiFine's chunk rebuild before OptiFine's own push - so the block this square claims to be goes on
        // the stack of the very buffer its quads land in, and comes off after. Nowhere else is there a buffer to
        // push onto, or anything to push it for. See OptiFabricEntry.
        com.mojang.blaze3d.vertex.VertexConsumer optifine = OptiFineMaterial.pushes()
            ? OptiFabricEntry.bufferOf(context, com.trmtgtnh.client.GhostLayers.of(origin))
            : null;
        if (optifine != null) OptiFineMaterial.pushClaim(GhostQuads.claimOf(record, origin, rotation), optifine);
        try {
            emit(
                context,
                GhostQuads.build(record, origin, outline, rotation, fringeTurn, snowed, null, stairs),
                null,
                material);
            for (Direction side : Direction.values()) {
                emit(
                    context,
                    GhostQuads.build(record, origin, outline, rotation, fringeTurn, snowed, side, stairs),
                    side,
                    material);
            }
        } finally {
            if (optifine != null) OptiFineMaterial.popClaim(optifine);
        }
    }

    /**
     * The material a square's quads carry, which is how this loader chooses a pass.
     *
     * <p>
     * Fabric binds a block to one pass and offers no way to ask which pass is being built, so the
     * Forge side's trick - claim every pass, hand back nothing in the three that are wrong - has
     * nothing here to hook on to. Its rendering API answers a better question instead: a
     * <em>quad</em> may carry its own blend mode, and the block's registered pass is only the
     * fallback for quads that do not. So worn ice emits translucent quads and worn stone solid ones,
     * out of one model, with no second block and no second registration.
     *
     * <p>
     * Null when no renderer is installed, which cannot happen while Fabric API is present - Indigo is
     * part of it - but is checked because a null slipped into {@code material()} would be a crash on
     * every worn square rather than a wrong-looking one.
     */
    private static net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial materialFor(int origin) {
        net.fabricmc.fabric.api.renderer.v1.Renderer renderer = net.fabricmc.fabric.api.renderer.v1.RendererAccess.INSTANCE
            .getRenderer();
        if (renderer == null) return null;
        return renderer.materialFinder()
            .clear()
            .blendMode(0, com.trmtgtnh.client.GhostLayers.of(origin))
            .find();
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
    /**
     * Whether quads go to the emitter one attribute at a time rather than through {@code fromVanilla} - under
     * OptiFabric only.
     *
     * <p>
     * {@code fromVanilla} copies a whole quad at the stride the renderer fixed once, when it first started, from
     * {@code DefaultVertexFormat.BLOCK} - which OptiFine grows while a shader pack is on. Under OptiFabric, Indigo
     * can fix the grown stride and then be handed these eight-int vertices once the pack is turned off, and it
     * copies past their end: on 2026-10-07 the mid-session toggle crashed the game there, with
     * ArrayIndexOutOfBoundsException in {@code MutableQuadViewImpl.fromVanilla}, on a chunk rebuilt after the pack
     * went. One attribute at a time reads no stride at all.
     *
     * <p>
     * <strong>And only there</strong>, because Canvas does not read a quad set that way as it reads one copied in:
     * the first version of this fix set every quad by attribute on every renderer, and Canvas drew the first yard
     * smeared into streaks with a dark block across it. Nothing but OptiFine moves that stride, and on Fabric
     * OptiFine comes only through OptiFabric, which Canvas cannot run beside.
     */
    private static final boolean BY_ATTRIBUTE = net.fabricmc.loader.api.FabricLoader.getInstance()
        .isModLoaded("optifabric");

    private static void emit(RenderContext context, List<BakedQuad> quads, Direction cull,
        net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial material) {
        for (BakedQuad quad : quads) {
            // Once, and kept. See above - this is not a tidy-up.
            QuadEmitter emitter = context.getEmitter();
            if (!BY_ATTRIBUTE) {
                emitter.fromVanilla(quad.getVertices(), 0, false);
            } else {
                // See BY_ATTRIBUTE. The normals are left unset, as fromVanilla left them, so the renderer
                // takes the face's own.
                emitVertices(emitter, quad.getVertices());
            }
            emitter.cullFace(cull);
            emitter.nominalFace(quad.getDirection());
            emitter.colorIndex(quad.getTintIndex());
            // Before emit, like everything else here, and after fromVanilla, which does not touch it.
            if (material != null) emitter.material(material);
            emitter.emit();
        }
    }

    /**
     * One quad's four vertices, each attribute set on its own - see {@link #BY_ATTRIBUTE}.
     *
     * <p>
     * <strong>Read at the quad's own stride, not at eight.</strong> OptiFine patches {@code BakedQuad.getVertices}
     * to hand the data back grown to its shader format while a pack is on - the same attributes first in each
     * vertex, more after them - so the length of the array is the only thing that says how far apart the
     * vertices are. Read at eight, under OptiFabric with a pack on, every vertex after the first was taken from
     * the middle of another, and the first yard drew as tiled grass sides with black bands across it. That is
     * also the crash this path exists for, seen from the renderer's side: Indigo copied at a stride fixed while
     * the arrays were grown, and was handed eight-int ones once the pack went off.
     */
    private static void emitVertices(QuadEmitter emitter, int[] packed) {
        int stride = packed.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int at = vertex * stride;
            emitter.pos(
                vertex,
                Float.intBitsToFloat(packed[at]),
                Float.intBitsToFloat(packed[at + 1]),
                Float.intBitsToFloat(packed[at + 2]));
            emitter.spriteColor(vertex, 0, packed[at + 3]);
            emitter.sprite(vertex, 0, Float.intBitsToFloat(packed[at + 4]), Float.intBitsToFloat(packed[at + 5]));
            emitter.lightmap(vertex, packed[at + 6]);
        }
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> random, RenderContext context) {
        // A ghost has no item form, and nothing should ever ask. See BlockGhost's constructor.
    }

    // ------------------------------------------------------------------
    // The vanilla half, which nothing should reach but everything must answer
    // ------------------------------------------------------------------

    /**
     * The plain block, for a renderer that does not speak the mesh API.
     *
     * <p>
     * {@code isVanillaAdapter} says false, so a renderer that implements FRAPI - Indigo, or Sodium
     * with Indium beside it - calls {@link #emitBlockQuads} and never this. One that does not
     * implement FRAPI has no way to be told which square it is drawing, and there is no position in
     * this signature to read: Sodium on its own is the case that matters, since a player may well
     * install it without Indium.
     *
     * <p>
     * It used to answer with nothing, and nothing is the worst available answer - the ghost has
     * replaced the ground in the client's own copy of the world, so no quads means a hole you can
     * see the sky through rather than a path. That is exactly what Forge's half did on 2026-10-06
     * with Rubidium installed, photographed in a real instance, and this is the same fault waiting on
     * this side. Unworn ground says "this renderer draws no wear"; a hole says "this mod is broken".
     */
    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) {
        return fallback.getQuads(state, side, random);
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
