package com.trmtgtnh.fabric.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

import com.trmtgtnh.server.ServerEvents;
import com.trmtgtnh.util.ExplosionSize;

/**
 * A blast that has actually happened, with the list of what it is about to take.
 *
 * <p>
 * Forge fires {@code ExplosionEvent.Detonate} here and even patches a getter onto the explosion for
 * its position; Fabric has neither, so this is both the hook and the getter.
 *
 * <p>
 * <strong>At the head of finalizing, which is the one moment the list exists and nothing has been
 * destroyed.</strong> The explosion has already worked out every block it will take and has not yet
 * taken one, so a reinforced block removed from the list here is spared without anything else having
 * to be kept in step - and the same list is what a watching client is told to redraw. A tick later
 * the list is empty and the blocks are air.
 *
 * <p>
 * The three shadowed fields are the position, which this version keeps private with no accessor. The
 * strength is not shadowed: it is read by {@link ExplosionSize}, which finds the one float field by
 * its type rather than by name and so works on both loaders without a second mixin.
 */
@Mixin(Explosion.class)
public abstract class MixinExplosion {

    @Shadow
    private Level level;

    @Shadow
    private double x;

    @Shadow
    private double y;

    @Shadow
    private double z;

    @Shadow
    private List<BlockPos> toBlow;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void trmt$detonated(boolean particles, CallbackInfo callback) {
        ServerEvents.exploded(level, x, y, z, ExplosionSize.of((Explosion) (Object) this), toBlow);
    }
}
