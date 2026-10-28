package com.trmtgtnh.util;

/**
 * An explosion's strength, as each loader module's {@code MixinExplosionRadius} gives every {@code Explosion} at 1.20.1
 * (0.9.228; Xep: "Own accessor"): the class keeps it in a private field with no getter. See {@link ExplosionSize}.
 */
public interface ExplosionRadius {

    /** The explosion's {@code radius} field: the strength it was made with. */
    float trmt$radius();
}
