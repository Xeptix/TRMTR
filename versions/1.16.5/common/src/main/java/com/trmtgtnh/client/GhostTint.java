package com.trmtgtnh.client;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostLight;
import com.trmtgtnh.client.model.GhostQuads;

/**
 * What color a ghost is at a place.
 *
 * <p>
 * A ghost's pictures are drawn from the pixels of the block it stands in for, and several of those
 * are stored grey and colored at render time - grass most of all, which is grey in the file and
 * green only because the game multiplies a biome's color into it. A face asking for a tint that
 * nothing answers is handed white, and white times grey is grey. That is the whole of why worn grass
 * drew as flat grey slabs with a mottled band along the top: the band was the fringe, in its own
 * untinted pixels, and the rest was the dirt below it.
 *
 * <p>
 * The quads ask for one of three slots and this answers all of them. {@link GhostQuads#LIGHT_TINT} is white,
 * for a lawn's flanks, which go grass's way under a tinted fringe; {@link GhostQuads#GRASS_TINT} and
 * {@link GhostQuads#COVERED_TINT} are the square's one color - the 1.7.10 edition's colorFor, the covered block's
 * own color handler while it wears as its own family (0.9.223, spec GS11, GS12, GT14, GT17). Each then has the
 * path light multiplied into it, which is what makes a lit square lit without replacing what it is made of -
 * grass in a swamp still reads as swamp grass when somebody lights it green.
 *
 * <p>
 * Shared rather than written twice, because it is a rule and not wiring. The loaders differ only in
 * which registry they hand it to: Forge has an event for it and Fabric a registry, and both are one
 * line. The 1.12.2 edition keeps this same rule inline in its client proxy, which is where it was
 * read from.
 */
public final class GhostTint implements BlockColor {

    private static final GhostTint INSTANCE = new GhostTint();

    private GhostTint() {}

    /** The one handler, which both loaders register against the one ghost block. */
    public static BlockColor handler() {
        return INSTANCE;
    }

    @Override
    public int getColor(BlockState state, BlockAndTintGetter world, BlockPos pos, int tintIndex) {
        if (tintIndex != GhostQuads.GRASS_TINT && tintIndex != GhostQuads.LIGHT_TINT
            && tintIndex != GhostQuads.COVERED_TINT) return -1;

        // The path light, multiplied into both: a glow outranks every rule about when ground is
        // tinted, because somebody chose this color for this square.
        int glow = world == null || pos == null ? 0 : GhostLight.packedAt(world, pos.getX(), pos.getY(), pos.getZ());
        if (tintIndex == GhostQuads.LIGHT_TINT) {
            return GhostLight.tinted(0xFFFFFF, glow);
        }
        // Everything else is the square's one color with the glow in it - the 1.7.10 edition's colorFor: the covered
        // block's own color while it still wears as its own family, white past it or sunk as grass. Slot nought carries
        // it on a square's top, fringe and non-lawn flanks (0.9.223, spec GS11, GS12, GT14, GT17), slot two on a worn
        // stair's faces (0.9.222, spec GF7); breaking and hitting dust asks slot nought with the real world in hand and
        // gets the same answer (spec GF19).
        return GhostLight.tinted(coveredTint(world, pos, net.minecraft.client.Minecraft.getInstance()
            .getBlockColors()), glow);
    }

    /**
     * The one color a worn square takes: the covered block's own color, asked of it through a view that holds it at
     * its own square - the 1.7.10 edition's {@code GhostRendering.colorFor}, {@code origin.colorMultiplier(new
     * OriginView(...))} - or white where the square no longer wears as the covered block's own family, as that
     * edition's has worn past its tint, and white for grass sunk as grass, which that edition draws with its untinted
     * hollow stand-in ({@code ghost.isUntinted()}). It is the color of every face that edition multiplies its color
     * onto: a worn stair's (0.9.222, spec GF7), and since 0.9.223 every square's top, its fringe, and the flanks of
     * anything but a lawn (spec GS11, GS12, GT14, GT17).
     *
     * <p>
     * That edition gives a block one color for all its faces; this version asks a color per tint slot, so the block's
     * one color is the one it gives its own top: asked at the slot its own model tints its top with ({@link #topSlot}).
     * Slot nought for vanilla's grass, leaves and vines, and for a block whose top takes no tint, which answers white
     * there. Not slot nought for every block: TerraFirmaCraft colors its grass at slot one and answers nothing at
     * nought, so asking nought alone drew its worn grass top in the grey of the picture as stored (0.9.229 W6 R9).
     */
    public static int coveredTint(BlockAndTintGetter world, BlockPos pos, BlockColors colors) {
        if (world == null || pos == null || colors == null) return 0xFFFFFF;
        int origin = com.trmtgtnh.Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ());
        BlockState covered;
        try {
            covered = origin < 0 ? null : net.minecraft.world.level.block.Block.stateById(origin);
        } catch (RuntimeException awkwardBlock) {
            covered = null;
        }
        if (covered == null) return 0xFFFFFF;
        short record = com.trmtgtnh.Client.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ());
        com.trmtgtnh.surface.SurfaceFamily own;
        try {
            own = com.trmtgtnh.surface.SurfaceRegistry.familyOf(covered);
        } catch (RuntimeException awkwardBlock) {
            own = null;
        }
        if (!takesCoveredTint(own, com.trmtgtnh.erosion.ErosionState.familyOf(record))) return 0xFFFFFF;
        // Sunk only where the block's shape sinks (W3 T6 follow-up): a "sinks": false lawn is drawn as unsunk turf.
        if (untintedHollow(own,
            com.trmtgtnh.surface.SurfaceShape.sinks(covered) ? com.trmtgtnh.erosion.ErosionState.sinkOf(record) : 0,
            com.trmtgtnh.surface.SurfaceShape.of(covered) == com.trmtgtnh.surface.SurfaceShape.STAIR)) {
            return 0xFFFFFF;
        }
        final BlockState asked = covered;
        try {
            return ownColor(
                slot -> colors.getColor(asked, new OriginTintView(world, pos, asked), pos, slot),
                topTints(asked));
        } catch (RuntimeException awkwardBlock) {
            return 0xFFFFFF;
        }
    }

    /**
     * A covered block's one color: its color handler's answer at the slot its own top is tinted at, as a tint.
     *
     * @param answerAt the block's color handler at its square, by tint slot
     * @param topTints the tint slot of each quad the block's own model draws on its top, in order, -1 for none
     */
    static int ownColor(java.util.function.IntUnaryOperator answerAt, int[] topTints) {
        return opaqueTint(answerAt.applyAsInt(topSlot(topTints)));
    }

    /**
     * The slot a block tints its own top at: its first tinted top quad's, and nought where none is tinted, the slot
     * every block was asked at before (0.9.229 W6 R9). TerraFirmaCraft's grass draws its top from a part whose one face
     * is {@code tintindex} 1, and its color handler answers only there: read from TerraFirmaCraft-Forge-1.20.1-3.2.28
     * ({@code ClientEventHandler}, the grass handler {@code snowy || tintIndex != 1 ? -1 : grassColor}) and the same in
     * TerraFirmaCraft-NeoForge-1.21.1-4.2.13.
     */
    static int topSlot(int[] topTints) {
        if (topTints != null) {
            for (int tint : topTints) {
                if (tint >= 0) return tint;
            }
        }
        return 0;
    }

    /** What each top quad of a model is tinted at, kept per state with the model it was read from. */
    private static final java.util.Map<BlockState, Top> TOPS = new java.util.concurrent.ConcurrentHashMap<BlockState, Top>();

    private static final class Top {

        final net.minecraft.client.resources.model.BakedModel model;

        final int[] tints;

        Top(net.minecraft.client.resources.model.BakedModel model, int[] tints) {
            this.model = model;
            this.tints = tints;
        }
    }

    /**
     * The tint slot of each quad a block's own model draws on its top, from its baked model as the game draws it - the
     * quads on the up side, else the unculled quads that face up, as {@code WearTextures.drawnFace} asks them, with
     * its seed. Kept per state and read again once the models are baked anew. Empty where the model will not say;
     * never throws.
     */
    static int[] topTints(BlockState covered) {
        try {
            net.minecraft.client.resources.model.BakedModel model = net.minecraft.client.Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModel(covered);
            if (model == null) return new int[0];
            Top known = TOPS.get(covered);
            if (known != null && known.model == model) return known.tints;
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads = model
                .getQuads(covered, net.minecraft.core.Direction.UP, new java.util.Random(42L));
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> up = new java.util.ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
            if (quads != null) up.addAll(quads);
            if (up.isEmpty()) {
                for (net.minecraft.client.renderer.block.model.BakedQuad quad : model
                    .getQuads(covered, null, new java.util.Random(42L))) {
                    if (quad.getDirection() == net.minecraft.core.Direction.UP) up.add(quad);
                }
            }
            int[] tints = new int[up.size()];
            for (int i = 0; i < tints.length; i++) tints[i] = up.get(i)
                .getTintIndex();
            TOPS.put(covered, new Top(model, tints));
            return tints;
        } catch (RuntimeException | LinkageError awkwardModel) {
            return new int[0];
        }
    }

    /**
     * Whether a square takes its covered block's own tint: only while it still wears as that block's own family - the
     * 1.7.10 edition's {@code originFamily != ghost.appearance()} exit, past which the ground is mostly bare earth and
     * tinting it would turn a path olive (0.9.222, spec GF7).
     */
    static boolean takesCoveredTint(com.trmtgtnh.surface.SurfaceFamily own, com.trmtgtnh.surface.SurfaceFamily wears) {
        return own != null && own == wears;
    }

    /**
     * Whether a square is drawn as the 1.7.10 edition's untinted hollow grass: grass sunk as grass. That edition paints
     * its sunken grass stand-in, built untinted, for every grass square with a sink, and its colorFor returns the light
     * alone for it; a stair is never sunk there, whatever its record says (spec GT17, SC22, GF7).
     */
    static boolean untintedHollow(com.trmtgtnh.surface.SurfaceFamily own, int sink, boolean stair) {
        return own == com.trmtgtnh.surface.SurfaceFamily.GRASS && sink > 0 && !stair;
    }

    /** A color handler's answer as a tint: white for none (-1), the alpha left off. */
    static int opaqueTint(int answered) {
        return answered == -1 ? 0xFFFFFF : answered & 0xFFFFFF;
    }

    /**
     * The world with one square handed back to the block a ghost stands in for, as a color handler is handed one - the
     * block module's OriginView, which is a plain view, with the three things a color handler may also ask passed
     * through: the biome tint above all, which grass and leaves read at the square.
     */
    private static final class OriginTintView implements BlockAndTintGetter {

        private final BlockAndTintGetter delegate;

        private final BlockPos at;

        private final BlockState origin;

        OriginTintView(BlockAndTintGetter delegate, BlockPos at, BlockState origin) {
            this.delegate = delegate;
            this.at = at.immutable();
            this.origin = origin;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return at.equals(pos) ? origin : delegate.getBlockState(pos);
        }

        @Override
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
            return at.equals(pos) ? origin.getFluidState() : delegate.getFluidState(pos);
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public float getShade(net.minecraft.core.Direction face, boolean shade) {
            return delegate.getShade(face, shade);
        }

        @Override
        public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver color) {
            return delegate.getBlockTint(pos, color);
        }

        @Override
        public int getBrightness(net.minecraft.world.level.LightLayer layer, BlockPos pos) {
            return delegate.getBrightness(layer, pos);
        }

        @Override
        public int getMaxBuildHeight() {
            // Asked through Heights, as every world height in this edition is (0.9.223).
            return com.trmtgtnh.util.Heights.top(delegate) + 1;
        }
    }
}
