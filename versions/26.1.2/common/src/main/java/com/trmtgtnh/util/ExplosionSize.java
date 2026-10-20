package com.trmtgtnh.util;

import net.minecraft.world.level.Explosion;

/**
 * How big an explosion is.
 *
 * <p>
 * The 1.7.10 edition reads {@code explosion.explosionSize}, a public field. From 1.12.2 to 1.21.1 the same number was
 * a private final with no getter, and this class found it by reflection as the one {@code float} field
 * {@code Explosion} declared - the event hands over the explosion, its position and its list of doomed blocks, but not
 * the one figure that says how strong it was, and that figure is the whole of whether a reinforced block survives it
 * and how far the scouring reaches.
 *
 * <p>
 * <strong>1.21.2 made {@code Explosion} an interface, with {@code radius()} on it</strong>, and the field went to
 * {@code ServerExplosion}. The reflection carried from 1.21.1 found no field at all on 26.1.2 and answered -1 for
 * every blast, which is the quiet failure 1.7.10 shipped with for as long as reinforcement existed: a blast that cannot
 * be measured spares nothing, and a block reinforced three times goes up with the crater. So the number is simply
 * asked for now (0.9.227), the same strength 1.21.1 read.
 */
public final class ExplosionSize {

    private ExplosionSize() {}

    /** The explosion's strength, or -1 when there is no explosion to ask. */
    public static float of(Explosion explosion) {
        if (explosion == null) return -1f;
        return explosion.radius();
    }
}
