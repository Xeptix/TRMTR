package com.trmtgtnh.util;

import net.minecraft.world.level.Explosion;

/**
 * How big an explosion is, which this version does not say in public.
 *
 * <p>
 * The 1.7.10 edition reads {@code explosion.explosionSize}, a public field. Here the same number is {@code private
 * final float radius} with no getter (read in both loaders' jars) - the event hands over the explosion, its position
 * and its list of doomed blocks, but not the one figure that says how strong it was, and that figure is the whole of
 * whether a reinforced block survives it and how far the scouring reaches.
 *
 * <p>
 * <strong>Read through an accessor of this mod's own, by the field's name</strong> (0.9.228; Xep: "Own accessor").
 * Until 0.9.228 this found the explosion's only {@code float} field by reflection, by its type - which a mod adding a
 * second float field to {@code Explosion} would have broken with nothing but a line in the log, and a blast that
 * cannot be measured spares no reinforced block. Each loader's {@code MixinExplosionRadius} gives every {@code
 * Explosion} the {@link Radius} interface, reading {@code radius} by its name, remapped with the rest of the jar. In
 * the loader modules rather than common's, because it names a game member, which a common mixin may not
 * (CommonMixinsNeedNoRefmapTest).
 */
public final class ExplosionSize {

    /** What each loader's MixinExplosionRadius gives every explosion: its strength, by the field's own name. */
    public interface Radius {

        float trmt$radius();
    }

    private ExplosionSize() {}

    /** The explosion's strength, or -1 when there is no explosion to ask. */
    public static float of(Explosion explosion) {
        if (explosion == null) return -1f;
        return ((Radius) (Object) explosion).trmt$radius();
    }
}
