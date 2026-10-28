package com.trmtgtnh.util;

import net.minecraft.world.level.Explosion;

/**
 * How big an explosion is - the other edition's {@code explosion.explosionSize}, a public field there.
 *
 * <p>
 * Asked of the version's own method (0.9.228; Xep: "Own accessor"): from 1.21 {@code Explosion} says its strength
 * through {@code radius()}. Until 0.9.228 every port from 1.12.2 found the class's only {@code float} field by
 * reflection, because 1.12.2, 1.16.5 and 1.20.1 keep it private with no getter; here there is one, so the reflection
 * and the warnings it needed for a field it might not find are gone. The figure is the whole of whether a reinforced
 * block survives a blast and how far the scouring reaches, and a blast that cannot be measured spares nothing.
 */
public final class ExplosionSize {

    private ExplosionSize() {}

    /** The explosion's strength, or -1 for none. */
    public static float of(Explosion explosion) {
        if (explosion == null) return -1f;
        return explosion.radius();
    }
}
