package com.trmtgtnh.util;

import java.lang.reflect.Field;

import net.minecraft.world.Explosion;

import com.trmtgtnh.Trmt;

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
 * Found by its type rather than its name, as this edition finds the model manager: {@code Explosion}
 * has exactly one {@code float} field, so the lookup needs no mapping table, survives obfuscation, and
 * asks nothing of the build. An access transformer would have worked too, and would have made the jar
 * one that edits a vanilla class's access to load; for one number read once per explosion, reflection
 * costs less than that.
 *
 * <p>
 * Refuses loudly rather than quietly, because the quiet failure is the bug the other edition shipped
 * with for as long as reinforcement existed: a blast that cannot be measured spares nothing, and a
 * block reinforced three times goes up with the crater. If the lookup ever finds no field, or more than
 * one, that is said once in the log in so many words.
 */
public final class ExplosionSize {

    private static final Field SIZE = find();

    private static boolean warned;

    private ExplosionSize() {}

    private static Field find() {
        Field found = null;
        for (Field field : Explosion.class.getDeclaredFields()) {
            if (field.getType() != float.class) continue;
            if (found != null) {
                Trmt.LOG.warn(
                    "Explosion has more than one float field ({} and {}); blast strength cannot be read, so reinforced blocks will not be spared",
                    found.getName(),
                    field.getName());
                return null;
            }
            found = field;
        }
        if (found == null) {
            Trmt.LOG.warn(
                "Explosion has no float field; blast strength cannot be read, so reinforced blocks will not be spared");
            return null;
        }
        found.setAccessible(true);
        return found;
    }

    /** The explosion's strength, or -1 when it cannot be read. */
    public static float of(Explosion explosion) {
        if (explosion == null || SIZE == null) return -1f;
        try {
            return SIZE.getFloat(explosion);
        } catch (IllegalAccessException refused) {
            if (!warned) {
                warned = true;
                Trmt.LOG.warn("Could not read an explosion's strength; reinforced blocks will not be spared", refused);
            }
            return -1f;
        }
    }
}
