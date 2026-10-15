package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;

import com.trmtgtnh.server.ServerEvents;

/**
 * Bars a category of mob from spawning on a warded block.
 *
 * <p>
 * Forge asks {@code LivingSpawnEvent.CheckSpawn} and takes DENY for an answer. Fabric has no such
 * event, and the game's own version of the question is this method: every spawn asks it, and a false
 * refuses the spawn without anything else having to be undone.
 *
 * <p>
 * <strong>The spawn type is the argument that makes this safe.</strong> Only a natural spawn is
 * refused; a spawner or a spawn egg is the player's own doing and is left alone. Forge gets that
 * distinction from a flag on its event and this gets it from the same value the game passes in, so
 * both loaders refuse exactly the same spawns.
 */
@Mixin(Mob.class)
public abstract class MixinMob {

    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private void trmt$wardRefusesSpawn(LevelAccessor level, MobSpawnType reason,
        CallbackInfoReturnable<Boolean> callback) {
        Mob self = (Mob) (Object) this;
        boolean fromSpawner = reason == MobSpawnType.SPAWNER || reason == MobSpawnType.SPAWN_EGG
            || reason == MobSpawnType.COMMAND || reason == MobSpawnType.BUCKET
            || reason == MobSpawnType.DISPENSER;
        if (!(level instanceof net.minecraft.world.level.Level)) return;
        boolean barred = ServerEvents.spawnBarred(
            (net.minecraft.world.level.Level) level,
            self,
            self.getX(),
            self.getY(),
            self.getZ(),
            fromSpawner);
        if (barred) callback.setReturnValue(Boolean.FALSE);
    }
}
