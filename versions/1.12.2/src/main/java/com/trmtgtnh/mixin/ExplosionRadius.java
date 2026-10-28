package com.trmtgtnh.mixin;

import net.minecraft.world.Explosion;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * An explosion's strength, by the field's own name (0.9.228; Xep: "Own accessor"). This version keeps it in
 * {@code private final float size} with no getter (read in its bytecode), so the accessor is this mod's own - by the
 * field's name, not as "the only float field", which is what util.ExplosionSize found it by until then. The name is
 * the newer editions' (ExplosionRadius, onto {@code radius}), so the same class reads the same in every port.
 */
@Mixin(Explosion.class)
public interface ExplosionRadius {

    @Accessor("size")
    float trmt$size();
}
