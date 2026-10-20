package com.trmtgtnh.fabric.mixin;

import java.util.List;

import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;

import com.trmtgtnh.server.ServerEvents;

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
 * At 1.21.1 three shadowed fields were the position, which that version kept private with no accessor, and the
 * strength was not shadowed: it was read by {@code ExplosionSize}, which finds the one float field by
 * its type rather than by name and so works on both loaders without a second mixin.
 *
 * <p>
 * From 1.21.2 Explosion is an interface and the blast that happens is ServerExplosion, whose {@code explode} works
 * out the list, hurts what it hurts, then takes the blocks and lights the fires, in one method;
 * {@code finalizeExplosion} is gone. So this is now that same moment inside {@code explode}: the list made and not
 * one block taken - just before it asks whether it takes blocks at all, as 1.21.1's finalizing was, after the
 * entities. The position is the blast's centre, now public, and the list is the method's own. The strength is the
 * explosion's own {@code radius()}, public from 1.21.2 on the interface and asked here directly. 1.21.1's
 * ExplosionSize found it as the class's one float field, which from 1.21.2 is ServerExplosion's; ExplosionSize asks
 * {@code radius()} too now.
 */
@Mixin(ServerExplosion.class)
public abstract class MixinExplosion {

    @Inject(method = "explode",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerExplosion;interactsWithBlocks()Z"))
    private void trmt$detonated(CallbackInfoReturnable<Integer> callback, @Local List<BlockPos> toBlow) {
        ServerExplosion self = (ServerExplosion) (Object) this;
        net.minecraft.world.phys.Vec3 centre = self.center();
        ServerEvents.exploded(self.level(), centre.x, centre.y, centre.z, self.radius(), toBlow);
    }
}
