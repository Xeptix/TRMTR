package com.trmtgtnh.block;

import javax.annotation.Nullable;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What a ghost answers as the family it is worn as, rather than as the block it covers.
 *
 * <p>
 * The 1.7.10 edition registers a stand-in per appearance family ({@code ModBlocks.forAppearance}), and each is told
 * its family when it is made: its step sound and its slipperiness are set once, from {@code GhostLogic.stepSoundFor}
 * and {@code GhostLogic.slipperinessFor}. This edition has one ghost for every family, so the same answers are asked
 * per square instead, of the family the client's record there says the square is worn as - which is what the painter
 * of that edition chooses the stand-in by. Forge 1.12.2 hands the position to both questions, so nothing is lost by
 * asking this way (0.9.222, spec GF10 and GF17).
 */
public final class GhostFamily {

    private GhostFamily() {}

    /**
     * The family a square is worn as, or null where nothing is recorded.
     *
     * <p>
     * The client's record at that square, as the world above it changes it ({@code ghostRecordAt}: a plant holds it at
     * the end of its own run) - the same record the model draws from, so a square sounds like what it is drawn as. Null
     * rather than the family nought decodes to, because a record of nothing decodes to grass.
     */
    @Nullable
    public static SurfaceFamily familyAt(IBlockAccess world, BlockPos pos) {
        if (pos == null) return null;
        short record = Trmt.proxy.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ());
        return BlockGhost.shows(record) ? ErosionState.familyOf(record) : null;
    }

    /**
     * The family's sound, stepping, digging, breaking and landing alike - the 1.7.10 edition's
     * {@code GhostLogic.stepSoundFor}, table for table: grass, sand, gravel, stone for the stony four, snow, glass for
     * ice, and gravel for anything else. This version names gravel's sound {@code GROUND} and grass's {@code PLANT}.
     * Gravel too where nothing is recorded, which is that table's last line.
     */
    public static SoundType soundFor(@Nullable SurfaceFamily family) {
        if (family == null) return SoundType.GROUND;
        switch (family) {
            case GRASS:
                return SoundType.PLANT;
            case SAND:
                return SoundType.SAND;
            case GRAVEL:
                return SoundType.GROUND;
            case STONE:
            case COBBLE:
            case NETHER:
            case END:
                return SoundType.STONE;
            case SNOW:
                return SoundType.SNOW;
            case ICE:
                return SoundType.GLASS;
            default:
                return SoundType.GROUND;
        }
    }

    /**
     * How slippery the family is underfoot - the 1.7.10 edition's {@code GhostLogic.slipperinessFor}: ice's 0.98, and
     * Block's own 0.6 for everything else.
     *
     * <p>
     * The figure a square slides at past its covered block's family, or with nothing recorded under it; while it still
     * wears as that block, {@link #slipperinessAt} asks the block itself (0.9.223, spec GF17).
     */
    public static float slipperinessFor(@Nullable SurfaceFamily family) {
        return family == SurfaceFamily.ICE ? 0.98F : 0.6F;
    }

    /**
     * How slippery a square is underfoot: the covered block's own figure while the square still wears as that block's
     * family - worn blue ice at blue ice's, a pack's scenery made of ice's material at its own - and the family's, the
     * 1.7.10 edition's figure, past it or where nothing is recorded (0.9.223; Xep, 2026-10-10: worn blue ice slides at
     * blue ice's own figure, "asked of the covered block"). The line is the tint's: a square worn into another
     * family is that family's ground, and slides as it.
     */
    public static float slipperinessAt(net.minecraft.block.Block ghost, IBlockAccess world, BlockPos pos) {
        SurfaceFamily family = familyAt(world, pos);
        IBlockState covered = GhostInherit.coveredAt(ghost, pos);
        if (covered == null) return slipperinessFor(family);
        try {
            net.minecraft.block.Block block = covered.getBlock();
            return slipperinessOf(
                family,
                com.trmtgtnh.surface.SurfaceRegistry.familyOf(block, block.getMetaFromState(covered)),
                block.slipperiness);
        } catch (RuntimeException awkwardBlock) {
            return slipperinessFor(family);
        }
    }

    /**
     * The slide rule itself: the covered block's own figure while the square wears as that block's family, the family's
     * past it or with nothing known under it. See {@link #slipperinessAt}.
     *
     * @param wears         the family the square's record says it wears as
     * @param own           the covered block's family, or null where nothing is known
     * @param coveredFigure the covered block's own slipperiness
     */
    static float slipperinessOf(SurfaceFamily wears, SurfaceFamily own, float coveredFigure) {
        return own != null && own == wears ? coveredFigure : slipperinessFor(wears);
    }

    /**
     * The block a family stands for where nothing is recorded under a square - the 1.7.10 edition's
     * {@code GhostLogic.vanillaCounterpart}, which that edition keeps for "map color and fallbacks".
     *
     * <p>
     * A fallback that edition reaches by another road: its stand-in for a family is given that family's hardness,
     * material and harvest tool ({@code hardnessFor}, {@code materialFor}, {@code harvestToolFor}), and each of those is
     * this block's own - so a square whose covered block is not known breaks as its family's block, there and here
     * (spec GF11, GF12).
     */
    public static IBlockState standIn(SurfaceFamily family) {
        switch (family) {
            case GRASS:
                return Blocks.GRASS.getDefaultState();
            case SAND:
                return Blocks.SAND.getDefaultState();
            case GRAVEL:
                return Blocks.GRAVEL.getDefaultState();
            case STONE:
                return Blocks.STONE.getDefaultState();
            case COBBLE:
                return Blocks.COBBLESTONE.getDefaultState();
            case NETHER:
                return Blocks.NETHERRACK.getDefaultState();
            case END:
                return Blocks.END_STONE.getDefaultState();
            case SNOW:
                return Blocks.SNOW.getDefaultState();
            case ICE:
                return Blocks.ICE.getDefaultState();
            default:
                return Blocks.DIRT.getDefaultState();
        }
    }
}
