package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.level.Explosion;

import com.trmtgtnh.util.ExplosionRadius;

/**
 * Gives every explosion this mod's own accessor for its strength (0.9.228; Xep: "Own accessor"; see
 * {@code ExplosionSize}): {@code private final float radius}, which 1.20.1 offers no getter for. By the field's name,
 * read from this version's jar, where until 0.9.228 the strength was found by reflection as the class's only float.
 * The same in the Fabric module; a common mixin may name no vanilla member at 1.20.1.
 */
@Mixin(Explosion.class)
public abstract class MixinExplosionRadius implements ExplosionRadius {

    @Shadow
    @Final
    private float radius;

    @Override
    public float trmt$radius() {
        return radius;
    }
}
