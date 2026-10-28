package com.trmtgtnh.util;

import net.minecraft.world.level.Explosion;

/**
 * How big an explosion is - the other edition's {@code explosion.explosionSize}, a public field there.
 *
 * <p>
 * Asked through an accessor of this mod's own (0.9.228; Xep: "Own accessor"): at 1.20.1 {@code Explosion} keeps its
 * strength in {@code private final float radius} with no getter, so each loader module mixes {@link ExplosionRadius}
 * into the class ({@code MixinExplosionRadius}), reading the field by its name. Until 0.9.228 every port from 1.12.2
 * found the class's only {@code float} field by reflection, which answered for whatever float the class held and
 * warned when it found none or two; the field is named now, and the build checks the name against this version's
 * jar. The figure is the whole of whether a reinforced block survives a blast and how far the scouring reaches, and a
 * blast that cannot be measured spares nothing.
 *
 * <p>
 * In the loader modules, not in common, because at 1.20.1 a common mixin may name no vanilla member
 * ({@code CommonMixinsNeedNoRefmapTest}); the 1.21.1 edition asks the game's own {@code radius()}.
 */
public final class ExplosionSize {

    private ExplosionSize() {}

    /** The explosion's strength, or -1 for none. */
    public static float of(Explosion explosion) {
        if (explosion == null) return -1f;
        return ((ExplosionRadius) explosion).trmt$radius();
    }
}
