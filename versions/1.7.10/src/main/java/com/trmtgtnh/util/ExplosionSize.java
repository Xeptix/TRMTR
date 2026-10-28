package com.trmtgtnh.util;

import net.minecraft.world.Explosion;

/**
 * An explosion's strength as its detonate event finds it (0.9.228; Xep: "Halve it on 1.7.10 to match").
 *
 * <p>
 * Vanilla 1.7.10's {@code Explosion.doExplosionA} doubles its own {@code explosionSize} while it gathers the blocks it
 * will destroy, and puts it back only when it has done so - after Forge's {@code onExplosionDetonate} fires (read in
 * the
 * game's bytecode). So inside the event the field reads twice the strength the explosion was made with: a strength-four
 * creeper read 8.0 here, where 1.12.2 and every later edition read 4.0, and blast wear and what reinforcement survives
 * were never 1:1. Halved here, the one place the event's strength is read, it is the explosion's real strength, as
 * every other edition reads it. Only for the detonate event: outside it the field is the strength as made.
 */
public final class ExplosionSize {

    private ExplosionSize() {}

    /** The strength the explosion was made with, read inside its detonate event. */
    public static float inDetonate(Explosion explosion) {
        return explosion.explosionSize / 2F;
    }
}
