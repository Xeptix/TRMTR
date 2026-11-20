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
            case SCULK:
                // Where nothing is recorded, its stand-in's sound (0.9.229, W4; the design's table 3.5): the
                // 1.7.10 edition gives gravel's, as Et Futurum's own sculk sound cannot be named without it.
                return SoundType.SCULK;
            case CLOUD:
                // Fluffy cloud's own, read from Twilight Forest's jar, and white wool's (0.9.229, W4).
                return SoundType.WOOL;
            default:
                return SoundType.GRAVEL;
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
    public static float slipperinessFor(SurfaceFamily family) {
        return family == SurfaceFamily.ICE ? 0.98F : 0.6F;
    }

    /**
     * How slippery a square is underfoot: the covered block's own figure while the square still wears as that block's
     * family - worn blue ice at blue ice's, a pack's scenery made of ice's material at its own - and the family's, the
     * 1.7.10 edition's figure, past it or where nothing is recorded (0.9.223; Xep, 2026-10-10: worn blue ice slides at
     * blue ice's own figure, "asked of the covered block"). The line is the tint's: a square worn into another
     * family is that family's ground, and slides as it.
     */
    public static float slipperinessAt(Block ghost, BlockGetter world, BlockPos pos) {
        SurfaceFamily family = familyAt(world, pos);
        BlockState covered = GhostInherit.coveredAt(ghost, pos);
        if (covered == null) return slipperinessFor(family);
        try {
            return slipperinessOf(
                family,
                com.trmtgtnh.surface.SurfaceRegistry.familyOf(covered),
                covered.getBlock()
                    .getFriction());
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
     * For the Fabric module, whose mixins answer with this in the five places vanilla reads {@code Block.getFriction()}
     * - a living thing's movement, an item's, an experience orb's, a flying mob's and a boat's - which are the five Forge
     * patches to ask {@code getSlipperiness} at a square. Since 0.9.229 they take the value the game read and change it
     * only on a worn square ({@link #frictionOver}), so another mod's redirect of the same read still applies.
     */
    public static float frictionOf(Block block, BlockGetter world, BlockPos pos) {
        if (block instanceof BlockGhost && pos != null) return slipperinessAt(block, world, pos);
        return block.getFriction();
    }

    /**
     * The ghost standing at a square, or null where the square holds anything else or is not known.
     *
     * <p>
     * For the Fabric module's friction mixins, which since 0.9.229 no longer replace the game's
     * {@code Block.getFriction()} call but take the value it gave (see {@link #frictionOver}), and so ask the square
     * again for the block the game read there. The same square in the same tick, so the same block.
     */
    public static Block ghostAt(BlockGetter world, BlockPos pos) {
        if (world == null || pos == null) return null;
        Block block = world.getBlockState(pos)
            .getBlock();
        return block instanceof BlockGhost ? block : null;
    }

    /**
     * The friction the game read at a square, kept unless the square is worn: there, the ghost's answer,
     * {@link #frictionOf}'s (0.9.229).
     *
     * <p>
     * <strong>Why not a redirect of the read.</strong> Until 0.9.228 the Fabric mixins {@code @Redirect}ed
     * {@code Block.getFriction()}. Two redirects of one call cannot both apply, and Biome Makeover 1.3.8 redirects the
     * same call in {@code LivingEntity.travel}, so all of Fabric 3 (1.16.5) died before its first screen. The mixins now
     * take the value after the call, whatever made it - vanilla, or another mod's redirect - and change it only on a
     * worn square, where the ghost's family's friction replaces whatever was read there. Everywhere else the value
     * passes through untouched.
     */
    public static float frictionOver(float asked, BlockGetter world, BlockPos pos) {
        Block ghost = ghostAt(world, pos);
        return ghost == null ? asked : frictionOf(ghost, world, pos);
    }

    /**
     * What a worn square looks like to anything that asks with the square in hand: the block it covers while it still
     * wears as that block's family, so worn grass is grass and a pack's own lawn is that lawn, and its family's stock
     * block past that, so a track worn to earth is earth (W6 S1). The line is the slide's and the tint's: a square worn
     * into another family is that family's ground.
     *
     * <p>
     * The camouflage hook of all three loaders, which Presence Footsteps asks on every step (PFSolver.getBlockStateAt)
     * and connected-texture mods ask of a neighbour. A square with no record, or no block recorded under it, stays the
     * ghost, as it always was. Read from the square's own record, so it is as safe on a mesher thread as
     * {@link BlockGhost#propagatesSkylightDown}; nothing in the game or the three loaders asks this for itself
     * (tools/appearance_sites.py reads their jars), so the only askers are other mods.
     */
    public static BlockState appearanceAt(BlockState state, BlockGetter world, BlockPos pos) {
        BlockPos square = BlockGhost.inWorld(world, pos);
        SurfaceFamily family = familyAt(world, square);
        BlockState covered = GhostInherit.coveredAt(state.getBlock(), square);
        if (family == null || covered == null) return state;
        return appearanceOf(family, com.trmtgtnh.surface.SurfaceRegistry.familyOf(covered), covered);
    }

    /**
     * The rule itself: the covered block while the square wears as that block's family, the family's stock block past
     * it. See {@link #appearanceAt}.
     *
     * @param wears   the family the square's record says it wears as
     * @param own     the covered block's family, or null where it has none
     * @param covered the block under the square
     */
    static BlockState appearanceOf(SurfaceFamily wears, SurfaceFamily own, BlockState covered) {
        return own == wears ? covered : standIn(wears);
    }

    /**
     * The state a mod that reads the block at a square itself, and never asks the camouflage hook, should be handed: a
     * worn square's appearance ({@link #appearanceAt}), anything that is not a ghost as it is. Asked by the Forge
     * module's {@code MixinPresenceFootstepsGround}, where the Forge port of Presence Footsteps {@code 1.9.1-beta.1}
     * reads the state under a step without the hook (W6 T9; docs/goals/0.9.229-w6-spec.md 3.3 in the 1.7.10 tree), so
     * a worn square is heard as the ground it stands for and not as the ghost's gravel.
     */
    public static BlockState groundFor(BlockState state, BlockGetter world, BlockPos pos) {
        if (state == null || pos == null || !(state.getBlock() instanceof BlockGhost)) return state;
        return appearanceAt(state, world, pos);
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
            case FLESH:
                // No vanilla flesh: soul sand is the Nether floor it is all but a twin of, and its short box and
                // drag (0.9.229, W4 T17; the 1.7.10 edition's vanillaCounterpart).
                return Blocks.SOUL_SAND.defaultBlockState();
            case TAINT:
                // No vanilla taint: mycelium, a living crust that spreads over the earth.
                return Blocks.MYCELIUM.defaultBlockState();
            case SCULK:
                // Vanilla's own from 1.19, the deep dark's skin (0.9.229, W4; the 1.7.10 edition's
                // vanillaCounterpart is obsidian, which only that edition and the two before 1.19 need).
                return Blocks.SCULK.defaultBlockState();
            case CLOUD:
                // No vanilla cloud: white wool is fluffy cloud's twin in all a ghost reports, as soft to dig, the
                // same quiet sound, and as white (0.9.229, W4; the 1.7.10 edition's vanillaCounterpart).
                return Blocks.WHITE_WOOL.defaultBlockState();
            default:
                return Blocks.DIRT.defaultBlockState();
        }
    }

    /**
     * A family's own hardness where its stand-in's is not the real block's, or NaN: flesh 0.4 and crusted taint 1.75,
     * read from Biomes O' Plenty's and Thaumcraft's jars, which no vanilla block shares (0.9.229, W4 T17; the 1.7.10
     * edition's GhostLogic.hardnessFor gives its ghosts those figures). Every other family's stand-in is its own
     * block, whose hardness is already the family's. Asked only where nothing is recorded under a square.
     */
    public static float ownHardness(SurfaceFamily family) {
        if (family == SurfaceFamily.FLESH) return 0.4F;
        if (family == SurfaceFamily.TAINT) return 1.75F;
        return Float.NaN;
    }
}
