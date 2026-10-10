package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;

import com.trmtgtnh.server.ServerEvents;

/**
 * A ward refuses a natural spawn whatever the mob makes of its own spawn rules (0.9.222, spec RL33).
 *
 * <p>
 * {@link MixinMob} asks at the head of {@code Mob.checkSpawnRules}, and a mob that answers that question itself without
 * asking up - vanilla's ocelot is one - never reaches it, so a warded floor kept everything off but those. Here the
 * question is asked where natural spawning tests a position for any mob, before the mob's own rules, which is where
 * Forge's spawn event is fired from too. Only natural spawns pass this way; a spawner or an egg does not.
 */
@Mixin(NaturalSpawner.class)
public abstract class MixinNaturalSpawnWard {

    @Inject(method = "isValidPositionForMob", at = @At("HEAD"), cancellable = true)
    private static void trmt$wardRefusesNaturalSpawn(ServerLevel level, Mob mob, double distance,
        CallbackInfoReturnable<Boolean> callback) {
        if (level == null || mob == null) return;
        if (ServerEvents.spawnBarred(level, mob, mob.getX(), mob.getY(), mob.getZ(), false)) {
            callback.setReturnValue(Boolean.FALSE);
        }
    }
}
