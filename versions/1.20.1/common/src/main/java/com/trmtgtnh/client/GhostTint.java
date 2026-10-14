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
     * That edition gives a block one color for all its faces; this version asks a color per tint slot, and slot nought
     * is the one a block that tints at all answers on - grass, leaves, vines. White where the block answers nothing, as
     * a block with no color of its own answers white there.
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
        if (untintedHollow(own, com.trmtgtnh.erosion.ErosionState.sinkOf(record),
            com.trmtgtnh.surface.SurfaceShape.of(covered) == com.trmtgtnh.surface.SurfaceShape.STAIR)) {
            return 0xFFFFFF;
        }
        try {
            return opaqueTint(colors.getColor(covered, new OriginTintView(world, pos, covered), pos, 0));
        } catch (RuntimeException awkwardBlock) {
            return 0xFFFFFF;
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

        /** The world's own height, which 1.17 asks every block getter. */
        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }

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
