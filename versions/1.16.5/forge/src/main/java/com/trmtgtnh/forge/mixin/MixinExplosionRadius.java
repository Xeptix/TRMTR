package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.level.Explosion;

import com.trmtgtnh.util.ExplosionSize;

/**
 * An explosion's strength, read by the field's own name (0.9.228; Xep: "Own accessor"; see {@link ExplosionSize}).
 *
 * <p>
 * This version's {@code Explosion} keeps it as {@code private final float radius} with no method of its own (read in
 * this loader's jar), so the accessor is this mod's: every explosion is given {@link ExplosionSize.Radius}, answered
 * from the shadowed field, which the refmap names as the running game does. The same mixin stands in the other
 * loader's module; it is not in common's, because a common mixin may name no game member.
 */
@Mixin(Explosion.class)
public abstract class MixinExplosionRadius implements ExplosionSize.Radius {

    @Shadow
    @Final
    private float radius;

    @Override
    public float trmt$radius() {
        return radius;
    }
}
