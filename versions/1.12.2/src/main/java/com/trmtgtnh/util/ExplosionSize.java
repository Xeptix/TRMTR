package com.trmtgtnh.util;

import net.minecraft.world.Explosion;

import com.trmtgtnh.mixin.ExplosionRadius;

/**
 * How big an explosion is, which 1.12.2 no longer says.
 *
 * <p>
 * The other edition reads {@code explosion.explosionSize}, a public field. Here the same number is a
 * private final with no getter - the event hands over the explosion, its position and its list of
 * doomed blocks, but not the one figure that says how strong it was, and that figure is the whole of
 * whether a reinforced block survives it and how far the scouring reaches.
 *
 * <p>
 * Read through this mod's own accessor onto the field by its name, {@code size} (0.9.228; Xep: "Own accessor"; see
 * {@link ExplosionRadius}). Until then it was found by reflection as the class's only {@code float} field, which held
 * only as long as nobody added a second one - a mod mixing a float into Explosion would have silently turned every
 * blast's strength unreadable, and a blast that cannot be measured spares nothing.
 */
public final class ExplosionSize {

    private ExplosionSize() {}

    /** The explosion's strength, or -1 for no explosion. */
    public static float of(Explosion explosion) {
        if (explosion == null) return -1f;
        return ((ExplosionRadius) explosion).trmt$size();
    }
}
