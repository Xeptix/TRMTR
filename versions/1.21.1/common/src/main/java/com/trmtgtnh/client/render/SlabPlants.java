package com.trmtgtnh.client.render;

import java.lang.reflect.Method;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.trmtgtnh.Client;
import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;

/**
 * A plant standing on a worn slab of Countered's Terrain Slabs stays down with its slab (W6, T8).
 *
 * <p>
 * Terrain Slabs lets a plant stand on a bottom slab and draws it half a block lower, so it sits on the slab rather
 * than in the air above it. It decides that by asking what is below the plant, and on a client a worn square is a
 * ghost, which is not a slab, so the plant jumped up half a block the moment the ground under it wore.
 * This edition's build of it (3.1.2, Fabric and NeoForge) does that by lowering the state's offset, at the
 * return of {@code getOffset}, which is where {@code MixinSettleOnWornGround} calls {@link Settling#offset}.
 * So the same half block is added here, for a plant its own rule accepts, over a ghost whose covered block is a bottom
 * slab: the same test on the block the ghost covers, and whether the block counts as a plant on top is asked of its own
 * public method, so that rule and its settings stay Terrain Slabs'. Nothing here touches its code and no mixin goes
 * into it. MIT, and named by reflection only so that it need not be present.
 *
 * <p>
 * A game without Terrain Slabs pays one read of a field, and that is what most of the game's blocks cost: this is
 * asked of everything drawn. Read from the view the asker was handed, as {@link Settling} says it must be.
 */
public final class SlabPlants {

    /** The mod's id, read from its {@code fabric.mod.json and neoforge.mods.toml}. */
    private static final String MOD = "terrain_slabs";

    /** Where it answers whether a block counts as a plant on top: public, static, one argument. OnTopHelper from its 26.x builds. */
    private static final String OWNER = "net.countered.terrainslabs.util.MixinHelper";

    private static final String RULE = "terrain_slabs$isStateValidOnTop";

    /** How far down the mod draws a plant on a bottom slab: half a block, a slab's height. */
    private static final double HALF_BLOCK = 0.5D;

    /** The answer where the mod is not here, or would not say. */
    private static final Predicate<BlockState> NOT_HERE = state -> false;

    /** Its rule once it has been looked for, and null until then. */
    private static volatile Predicate<BlockState> rule;

    private SlabPlants() {}

    /** Hands in a rule, for a test, which has no Terrain Slabs to ask. */
    static void use(Predicate<BlockState> asked) {
        rule = asked;
    }

    /** Forgets the rule, so the next question looks for it again. For a test. */
    static void forget() {
        rule = null;
    }

    private static Predicate<BlockState> rule() {
        Predicate<BlockState> known = rule;
        if (known != null) return known;
        // Asked again until a loader has wired the seam: "not installed" from an unwired seam is not an answer to keep.
        if (!ModsPresent.wired()) return NOT_HERE;
        known = find();
        rule = known;
        return known;
    }

    private static Predicate<BlockState> find() {
        if (!ModsPresent.has(MOD)) return NOT_HERE;
        try {
            Method asked = Class.forName(OWNER, false, SlabPlants.class.getClassLoader())
                .getMethod(RULE, BlockState.class);
            return state -> ask(asked, state);
        } catch (ReflectiveOperationException | LinkageError moved) {
            Trmt.LOG.info("Terrain Slabs is here but its plant rule is not where it was ({}), so a plant on a worn slab "
                + "is drawn where the game says", moved.toString());
            return NOT_HERE;
        }
    }

    private static boolean ask(Method asked, BlockState state) {
        try {
            return Boolean.TRUE.equals(asked.invoke(null, state));
        } catch (ReflectiveOperationException | RuntimeException awkward) {
            return false;
        }
    }

    /** Whether Terrain Slabs would draw this block down on a slab: false wherever it is not installed. */
    public static boolean onTop(BlockState state) {
        return state != null && rule().test(state);
    }

    /**
     * How far down a block is drawn for the worn slab it stands on: half a block, or nought.
     *
     * <p>
     * The upper half of a tall plant asks of the ground two below it, as the mod does. Only over a ghost: over a real
     * slab, on a server's world or an unworn square, the mod has answered already.
     */
    public static double dropUnder(BlockState state, BlockGetter level, BlockPos pos) {
        Predicate<BlockState> asked = rule();
        if (asked == NOT_HERE || state == null || level == null || pos == null) return 0.0D;
        BlockPos ground = upperHalf(state) ? pos.below(2) : pos.below();
        if (!(level.getBlockState(ground)
            .getBlock() instanceof BlockGhost)) return 0.0D;
        int origin = Client.ghostOriginAt(ground.getX(), ground.getY(), ground.getZ());
        return origin >= 0 && bottomSlab(Block.stateById(origin)) && asked.test(state) ? HALF_BLOCK : 0.0D;
    }

    private static boolean upperHalf(BlockState state) {
        return state.getBlock() instanceof DoublePlantBlock
            && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    private static boolean bottomSlab(BlockState covered) {
        return covered != null && covered.getBlock() instanceof SlabBlock
            && covered.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }
}
