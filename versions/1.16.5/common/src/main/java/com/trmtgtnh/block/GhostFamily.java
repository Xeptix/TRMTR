package com.trmtgtnh.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Client;
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
 * of that edition chooses the stand-in by (0.9.222, spec GF10 and GF17).
 *
 * <p>
 * <strong>The two loaders reach it differently, and that is the whole of the difference.</strong> Forge asks a block
 * both questions at a square ({@code getSoundType} and {@code getSlipperiness}, Forge's own, which {@link BlockGhost}
 * answers). Vanilla, which is what Fabric runs, asks a state with no square in it - {@code BlockState.getSoundType()},
 * {@code Block.getFriction()} - so the Fabric module's mixins sit where the game plays a block's sound or reads its
 * friction, where the square is known, and hand both here: {@link #soundOf} and {@link #frictionOf}.
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
    public static SurfaceFamily familyAt(BlockGetter world, BlockPos pos) {
        if (pos == null) return null;
        short record = Client.ghostRecordAt(world, pos.getX(), pos.getY(), pos.getZ());
        return BlockGhost.shows(record) ? ErosionState.familyOf(record) : null;
    }

    /**
     * The family's sound, stepping, digging, breaking and landing alike - the 1.7.10 edition's
     * {@code GhostLogic.stepSoundFor}, table for table: grass, sand, gravel, stone for the stony four, snow, glass for
     * ice, and gravel for anything else, which is also the answer where nothing is recorded.
     */
    public static SoundType soundFor(SurfaceFamily family) {
        if (family == null) return SoundType.GRAVEL;
        switch (family) {
            case GRASS:
                return SoundType.GRASS;
            case SAND:
                return SoundType.SAND;
            case GRAVEL:
                return SoundType.GRAVEL;
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
                return SoundType.GRAVEL;
        }
    }

    /**
     * How slippery the family is underfoot - the 1.7.10 edition's {@code GhostLogic.slipperinessFor}: ice's 0.98, and
     * Block's own 0.6 for everything else.
     *
     * <p>
     * Per family, as that edition has it, although this version knows the square and could answer the covered block's
     * own figure. The rule is the family's (spec GF17), and that edition says what it costs: a pack's scenery made of
     * ice's material slides a player at 0.98 where the server's block says otherwise.
     */
    public static float slipperinessFor(SurfaceFamily family) {
        return family == SurfaceFamily.ICE ? 0.98F : 0.6F;
    }

    /**
     * The sound a state makes at a square: a ghost's family's, anything else its own.
     *
     * <p>
     * For the Fabric module, whose mixins replace the game's {@code BlockState.getSoundType()} with this where the
     * square is known: stepping on it ({@code Entity.playStepSound}, and a horse's own), landing on it
     * ({@code LivingEntity.playBlockFallSound}), hitting it ({@code MultiPlayerGameMode.continueDestroyBlock}) and
     * breaking it ({@code LevelRenderer.levelEvent}, event 2001) - the same four places Forge patches to ask a block at
     * a square, so both loaders answer at the same moments.
     */
    public static SoundType soundOf(BlockState state, BlockGetter world, BlockPos pos) {
        if (state.getBlock() instanceof BlockGhost && pos != null) return soundFor(familyAt(world, pos));
        return state.getSoundType();
    }

    /**
     * The friction of a block at a square: a ghost's family's, anything else its own.
     *
     * <p>
     * For the Fabric module, whose mixins replace {@code Block.getFriction()} with this in the five places vanilla reads
     * it - a living thing's movement, an item's, an experience orb's, a flying mob's and a boat's - which are the five
     * Forge patches to ask {@code getSlipperiness} at a square.
     */
    public static float frictionOf(Block block, BlockGetter world, BlockPos pos) {
        if (block instanceof BlockGhost && pos != null) return slipperinessFor(familyAt(world, pos));
        return block.getFriction();
    }

    /**
     * The block a family stands for where nothing is recorded under a square - the 1.7.10 edition's
     * {@code GhostLogic.vanillaCounterpart}, which that edition keeps for "map color and fallbacks".
     *
     * <p>
     * A fallback that edition reaches by another road: its stand-in for a family is given that family's hardness,
     * material and harvest tool ({@code hardnessFor}, {@code materialFor}, {@code harvestToolFor}), and each of those is
     * this block's own - so a square whose covered block is not known breaks as its family's block, there and here
     * (spec GF11, GF12). Snow is the snow block, as that edition's {@code Blocks.snow} is.
     */
    public static BlockState standIn(SurfaceFamily family) {
        switch (family) {
            case GRASS:
                return Blocks.GRASS_BLOCK.defaultBlockState();
            case SAND:
                return Blocks.SAND.defaultBlockState();
            case GRAVEL:
                return Blocks.GRAVEL.defaultBlockState();
            case STONE:
                return Blocks.STONE.defaultBlockState();
            case COBBLE:
                return Blocks.COBBLESTONE.defaultBlockState();
            case NETHER:
                return Blocks.NETHERRACK.defaultBlockState();
            case END:
                return Blocks.END_STONE.defaultBlockState();
            case SNOW:
                return Blocks.SNOW_BLOCK.defaultBlockState();
            case ICE:
                return Blocks.ICE.defaultBlockState();
            default:
                return Blocks.DIRT.defaultBlockState();
        }
    }
}
