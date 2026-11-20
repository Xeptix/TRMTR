package com.trmtgtnh.client;

import java.util.Locale;

/**
 * Countered's Smooth Steps, read by the mods part's walk (0.9.229 W6 R6; docs/goals/0.9.229-w6-spec.md 3.9 in the 1.7.10
 * tree). The mod is client only: its {@code EntityMixin} notes the player's height at the head and the return of
 * {@code Entity.move}, and a rise the move made beyond what it was asked becomes a negative offset, which its
 * {@code CameraMixin} eases out over the next ticks. It reads the entity's height and never a block's bounds, so a step
 * out of a rut is smoothed as any step is; the walk proves it by reading the offset just after the player steps over a
 * rut's lip.
 *
 * <p>
 * Read by reflection through the interface its mixin puts on every entity, so nothing here compiles against the mod
 * and a game without it reads nothing. The names are the jars' own: {@code IStepTracker} and its two getters are the
 * same in all ten jars the W6 Slabs copies hold (javap, 1.1.0, 1.2.0 and 1.2.1).
 */
final class ModsStep {

    /** The mod's id, as its fabric.mod.json and mods.toml give it. */
    static final String MOD = "smooth_steps";

    /** The interface the mod's EntityMixin puts on every entity. */
    static final String TRACKER = "net.countered.smoothsteps.IStepTracker";

    /** This tick's offset; its other getter is the last tick's, which the camera eases from. */
    static final String OFFSET = "smoothSteps$getOffset";

    /** What the rig reads: the rise the walk met and the offset the mod made of it. */
    static final String LINE = "Spike walk: mods - step rise=%.3f offset=%.3f";

    /** Less than this is no rise and no offset: the mod itself drops an offset under a thousandth. */
    static final double STILL = 1.0E-4D;

    /** The most a walking player steps up without a jump, every edition from 1.20.1 to 26.3. */
    static final double STEP_UP = 0.6D;

    private ModsStep() {}

    /** The offset Smooth Steps holds on this entity, or null where the mod is absent or the entity is no tracker. */
    static Double offsetOf(Object entity) {
        return offsetOf(entity, TRACKER);
    }

    /** The offset a tracker of this name holds on the entity; the name is handed in so a stand-in can be read. */
    static Double offsetOf(Object entity, String tracker) {
        if (entity == null) return null;
        try {
            Class<?> type = Class.forName(tracker, false, entity.getClass().getClassLoader());
            if (!type.isInstance(entity)) return null;
            Object said = type.getMethod(OFFSET).invoke(entity);
            return said instanceof Number ? Double.valueOf(((Number) said).doubleValue()) : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
            return null;
        }
    }

    /** The line, in this shape on every edition. */
    static String line(double rise, double offset) {
        return String.format(Locale.ROOT, LINE, Double.valueOf(rise), Double.valueOf(offset));
    }

    /** Whether a lip of this height is one a walk steps up: a rise at all, and no more than the player's step. */
    static boolean walkable(double lip) {
        return lip > STILL && lip <= STEP_UP;
    }

    /** Null when the walk proved the mod smooths a step out of a rut; otherwise what it found instead. */
    static String fault(double rise, Double offset) {
        if (!(rise > STILL)) return "the walk over the rut's lip met no rise";
        if (offset == null) return "the player is no " + TRACKER + ", so the offset of the rise could not be read";
        if (!(Math.abs(offset.doubleValue()) > STILL)) {
            return String.format(Locale.ROOT, "a rise of %.3f left the offset at nought", Double.valueOf(rise));
        }
        return null;
    }
}
