package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.trmtgtnh.server.ServerEvents;

/**
 * Two of the moments Fabric has no event for: something finished a tick, and something landed.
 *
 * <p>
 * Forge fires {@code LivingUpdateEvent} and {@code LivingFallEvent} for these. Fabric's API has
 * neither, and there is nothing else to listen to - a living entity's tick and its landing are both
 * private business between the entity and the world. So the call goes where the game does the thing.
 *
 * <p>
 * <strong>A tick hook on every living entity is the most expensive thing this mod asks of either
 * loader, and it is asked on both.</strong> Forge's event costs the same - it is fired from the same
 * method - so this is not a Fabric tax; it is what wearing ground from movement costs. What keeps it
 * cheap is that {@code livingTicked} answers and returns on a configuration read for any pack that
 * does not wear ground from mobs at all.
 *
 * <p>
 * Players are handled by their own hook and this one steps aside for them, which is what the other
 * editions do: a player's tick is a superset of this one, and counting a footfall twice would wear a
 * path twice as fast for the only thing that walks on most of them.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$ticked(CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player) return;
        ServerEvents.livingTicked(self);
    }

    /**
     * Landing, taken at the head of the fall-damage calculation.
     *
     * <p>
     * That method is called for every landing and not only a damaging one - it is where the distance
     * fallen is finally known - so it is the same moment Forge's event fires at. At the head, so the
     * ground is marked whether or not anything else goes on to cancel the damage: the mark is made by
     * the landing and not by the hurt.
     */
    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void trmt$landed(float distance, float multiplier, CallbackInfoReturnable<Boolean> callback) {
        ServerEvents.fell((LivingEntity) (Object) this, distance);
    }
}
