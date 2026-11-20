package com.trmtgtnh.server;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.trmtgtnh.config.FaithfulProfile;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * The brush on Faithful Mode's worn sand (0.9.229 W5 T17; spec 2.6), as the original's {@code BrushItemMixin} does it
 * (0.9.229-w5-original.md, section 7): the brush strokes at the use ticks where {@code (duration - remaining + 1) % 10
 * == 5}, as vanilla's own brush does (read from 1.20.1's {@code BrushItem.onUseTick}); each stroke at worn sand within
 * 4.5 blocks adds one to the brushing player's count, and on the second the square steps back one stage along the
 * healing chain, the brush takes 1 damage, the sand's dust is shown and the count starts again. Sand alone, and only
 * while Faithful Mode owns it ({@link FaithfulProfile#brushes}); every other square and every other time the brush is
 * vanilla's.
 *
 * <p>
 * Called from the head of {@code BrushItem.onUseTick} by each loader's mixin ({@code MixinFaithfulBrush} in the Forge
 * and Fabric modules): at 1.20.1 a common mixin may name no vanilla member ({@code CommonMixinsNeedNoRefmapTest}), so
 * the hook is the loaders' and everything it does is here, once. 1.7.10, 1.12.2 and 1.16.5 have no brush.
 */
public final class FaithfulBrush {

    /** The original's reach for a stroke at worn sand, 4.5 blocks from the eye (0.5.1's literal). */
    public static final double REACH = 4.5D;

    /** Strokes at worn sand per stage given back: the second takes it. */
    public static final int STROKES = 2;

    /** Each brushing player's strokes at worn sand since the last stage given back; server thread only. */
    private static final Map<Object, Integer> COUNTS = new WeakHashMap<Object, Integer>();

    private FaithfulBrush() {}

    /**
     * Whether this use tick is one of the brush's strokes: the tick vanilla's brush strokes at, ten ticks apart from
     * the fifth, for a use that began with {@code duration} ticks to run and has {@code remaining} left.
     */
    public static boolean strokes(int duration, int remaining) {
        return remaining >= 0 && (duration - remaining + 1) % 10 == 5;
    }

    /**
     * One more stroke at worn sand counted for this player, answering whether it is the one that gives a stage back
     * (the second), and starting the count again when it is. Package-visible so the count is tried without a world.
     */
    static boolean counts(Object player) {
        Integer held = COUNTS.get(player);
        int now = (held == null ? 0 : held.intValue()) + 1;
        if (now >= STROKES) {
            COUNTS.remove(player);
            return true;
        }
        COUNTS.put(player, Integer.valueOf(now));
        return false;
    }

    /** Forgets every player's count: a world unloaded, or a test starting clean. */
    public static void forgetAll() {
        COUNTS.clear();
    }

    /**
     * One use tick of a brush, from the head of {@code BrushItem.onUseTick}: on a stroke, at worn sand Faithful Mode
     * owns within reach, counted, and on the second the stage given back, the brush worn and the dust shown. Answers
     * whether a stage was given back. Server only; vanilla's tick goes on either way, as the original's did.
     */
    public static boolean used(Level level, LivingEntity user, ItemStack brush, int duration, int remaining) {
        if (level == null || level.isClientSide() || !TrmtConfig.enabled) return false;
        if (!(user instanceof Player) || brush == null || !strokes(duration, remaining)) return false;
        if (!FaithfulProfile.brushes(SurfaceFamily.SAND)) return false;
        Vec3 eye = user.getEyePosition();
        Vec3 reach = eye.add(user.getViewVector(1.0F).scale(REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, user));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        BlockState ground = level.getBlockState(pos);
        if (!FaithfulProfile.brushes(SurfaceRegistry.familyOf(ground))) return false;
        ErosionEngine engine = ErosionEngine.get();
        if (!engine.restorable(level, pos.getX(), pos.getY(), pos.getZ())) return false;
        if (!counts(user)) return false;
        // One step back along the healing chain, as the heal and Faithful bone meal take it.
        if (!engine.restoreOneStage(level, pos.getX(), pos.getY(), pos.getZ(), true, true)) return false;
        final InteractionHand hand = user.getUsedItemHand();
        brush.hurtAndBreak(1, user, broken -> broken.broadcastBreakEvent(hand));
        if (level instanceof ServerLevel) {
            Vec3 at = hit.getLocation();
            ((ServerLevel) level).sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, ground),
                at.x,
                at.y,
                at.z,
                8,
                0.2D,
                0.05D,
                0.2D,
                0.0D);
        }
        return true;
    }
}
