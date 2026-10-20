package com.trmtgtnh.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SpongeBlock;
import net.minecraft.world.level.block.StructureVoidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.WetSpongeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * What 1.16.5 would have said a block was made of, asked of what 1.20 keeps (0.9.225, the modern-ports plan's spike 4).
 *
 * <p>
 * 1.20 removed {@code Material}, which detection leads with in every older edition: grass by its material, snow and ice
 * by theirs, rock and sand named and then confirmed by theirs. What replaced it is a set of properties Mojang filled in
 * for each vanilla block from the material it had - its map color, its sound, its note-block instrument (which every
 * edition before derived from the material: rock is the bass drum, sand the snare), whether a piston breaks it,
 * whether it can be replaced, whether anything collides with it. These rules read those back into the kinds detection
 * asks about.
 *
 * <p>
 * <strong>Held by the parity table</strong>, {@code docs/detection-parity.md}, written by
 * {@code tools/detection_parity.py} from both versions' own {@code Blocks} class: every one of the 762 vanilla blocks
 * that exists in both versions gets the same kind here as its 1.16.5 material - nought differ - with plant and
 * replaceable plant one answer, as they are to detection. The tool carries these rules in Python too, and
 * {@code MaterialKindMatchesTheTableTest} holds the two to the same order and the same conditions. A modded block is
 * read by the same properties, which a mod porting to 1.20 sets as Mojang did.
 *
 * <p>
 * The order is the rules' own: what a class says first, then a plant by its sound, then rock and sand by their
 * instruments, then turf and earth by their sounds.
 */
public enum MaterialKind {

    GRASS,
    DIRT,
    SAND,
    STONE,
    SNOW,
    TOP_SNOW,
    ICE,
    LEAVES,
    PLANT,
    REPLACEABLE_PLANT,
    CACTUS,
    CLOTH_DECORATION,
    OTHER;

    /** Whether this is a plant of either kind - which is all detection ever asks of the two. */
    public boolean plant() {
        return this == PLANT || this == REPLACEABLE_PLANT;
    }

    /** The kind a block's default state has. */
    public static MaterialKind of(Block block) {
        return of(block.defaultBlockState());
    }

    public static MaterialKind of(BlockState state) {
        Block block = state.getBlock();
        SoundType sound = state.getSoundType();
        NoteBlockInstrument instrument = state.instrument();
        if (block instanceof CactusBlock) return CACTUS;
        if (block instanceof LeavesBlock || state.is(BlockTags.LEAVES)) return LEAVES;
        if (block instanceof CarpetBlock) return CLOTH_DECORATION;
        if (block instanceof SnowLayerBlock) return TOP_SNOW;
        if (sound == SoundType.SNOW) return SNOW;
        if (block.defaultMapColor() == MapColor.ICE && sound == SoundType.GLASS) return ICE;
        if (state.liquid() || block instanceof LiquidBlock || block instanceof BubbleColumnBlock) return OTHER;
        if (block instanceof FlowerPotBlock || block instanceof StructureVoidBlock || block instanceof ScaffoldingBlock
            || block instanceof BambooSaplingBlock) return OTHER;
        // The water plants - kelp, seagrass, coral - were never ground or vegetation, and are built as plants are.
        if (sound == SoundType.WET_GRASS) return OTHER;
        if (block instanceof CocoaBlock || block instanceof ChorusPlantBlock
            || block instanceof ChorusFlowerBlock
            // The lily pad's class, named LilyPadBlock from 26.1 where it was WaterlilyBlock; the same block.
            || block instanceof LilyPadBlock
            || block instanceof AttachedStemBlock) return PLANT;
        if (noCollision(state) && state.getPistonPushReaction() == PushReaction.DESTROY
            && plantSound(sound)
            && !(block instanceof BasePressurePlateBlock)) {
            return state.canBeReplaced() ? REPLACEABLE_PLANT : PLANT;
        }
        // Built of rock and called clay in every edition before, so never rock here.
        if (block instanceof InfestedBlock) return OTHER;
        if (instrument == NoteBlockInstrument.BASEDRUM || sound == SoundType.BONE_BLOCK) return STONE;
        if ((instrument == NoteBlockInstrument.SNARE && (sound == SoundType.SAND || sound == SoundType.GRAVEL))
            || sound == SoundType.SOUL_SAND) return SAND;
        // They share the grass sound and were never turf.
        if (block instanceof SpongeBlock || block instanceof WetSpongeBlock || block instanceof TntBlock) return OTHER;
        if ((sound == SoundType.GRASS || sound == SoundType.WART_BLOCK || sound == SoundType.SHROOMLIGHT)
            && !noCollision(state)) return GRASS;
        if ((sound == SoundType.GRAVEL || sound == SoundType.SOUL_SOIL || sound == SoundType.ROOTED_DIRT)
            && instrument != NoteBlockInstrument.FLUTE) return DIRT;
        return OTHER;
    }

    /** A plant makes a plant's sound - what keeps torches, wire, webs and fire, built as plants are, out of it. */
    private static boolean plantSound(SoundType sound) {
        return sound == SoundType.GRASS || sound == SoundType.CROP
            || sound == SoundType.SWEET_BERRY_BUSH
            || sound == SoundType.NETHER_SPROUTS
            || sound == SoundType.ROOTS
            || sound == SoundType.FUNGUS
            || sound == SoundType.NETHER_WART
            || sound == SoundType.VINE
            || sound == SoundType.HARD_CROP
            || sound == SoundType.STEM
            || sound == SoundType.WEEPING_VINES
            || sound == SoundType.TWISTING_VINES
            || sound == SoundType.LILY_PAD;
    }

    private static boolean noCollision(BlockState state) {
        try {
            return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                .isEmpty();
        } catch (RuntimeException needsAWorld) {
            // A block whose shape asks the world for more than an empty one holds is solid enough to stand on.
            return false;
        }
    }
}
