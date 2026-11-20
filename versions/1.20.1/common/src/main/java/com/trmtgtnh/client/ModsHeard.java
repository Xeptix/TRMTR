package com.trmtgtnh.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * What Presence Footsteps hears at a worn square, asked of its own solver (0.9.229 W6 T9;
 * docs/goals/0.9.229-w6-spec.md 3.3 in the 1.7.10 tree). The harness cannot hear, so the mods part of a Sound copy asks
 * the mod the question one footstep asks of one square and says the sound key it answers: a worn grass square should be
 * heard as grass and a lawn worn through to earth as earth, where without this mod's answer it was heard as the ghost's
 * own gravel.
 *
 * <p>
 * Reached by reflection through {@code PresenceFootsteps.getInstance()}, its sound engine and the engine's solver, so
 * no tree compiles against the mod, and never anything of it copied: its licence (PolyForm Shield) lets a mod use it
 * and test with it. The names are the jars' own, read with javap from every build the W6 Sound copies hold, and the
 * per-square question has three shapes there:
 * <ul>
 * <li>{@code r29} (1.16.5, Fabric and the Forge port): {@code findAssociation(Level, BlockPos, AABB)}, the answer's key
 * {@code getAcousticName()};</li>
 * <li>the Forge port's {@code 1.9.1-beta.1} (1.20.1): {@code findAssociation(Entity, BlockPos, AABB)}, the same
 * key;</li>
 * <li>{@code 1.10.1} to {@code 1.14.0} and the NeoForge port: {@code findAssociation(AssociationPool, LivingEntity,
 * MutableBlockPos, AABB)}, the key {@code dry().raw()}.</li>
 * </ul>
 * Each is the private method the step's own search calls for every square it tries, which reads the square's state,
 * checks it against the walker's box and falls to the state's sound where the block map has no row - the path a worn
 * square takes. The engine is a getter, or the Forge port's 1.20.1 and the NeoForge port's public field; the solver is
 * the engine's from 1.10.1 and its isolator's before.
 *
 * <p>
 * Plain Java with nothing of the game's: the harness hands in the game's own objects, and each shape is matched by the
 * types the method takes, so a stand-in of each is tested on its own and the file reads the same in every edition.
 */
public final class ModsHeard {

    /** The set whose copies hold Presence Footsteps: the mods part asks only there. */
    public static final String SET = "sound";

    /** Presence Footsteps' own class, in every build read. */
    public static final String MOD = "eu.ha3.presencefootsteps.PresenceFootsteps";

    /** The per-square question's name, in every build read. */
    public static final String ASK = "findAssociation";

    /**
     * What the ghost is heard as when the mod is handed the ghost: no block map has it, so the mod falls to its sound,
     * which is gravel's (predicted by the spec, 3.3; the baseline run is D54's failing test).
     */
    public static final String GHOST = "gravel";

    /** What the mod answers where it heard nothing at the square: its own words for no sound. */
    static final String[] NOTHING = { "NOT_EMITTER", "UNASSIGNED" };

    /** What the rig reads: the row, its stage and the key the mod heard. */
    public static final String LINE = "Spike walk: mods - heard %s stage=%d = %s";

    private ModsHeard() {}

    /** Whether a set's mods part asks what Presence Footsteps hears. */
    public static boolean asks(String set) {
        return SET.equals(set);
    }

    /** The line, in this shape on every edition. */
    public static String line(String selector, int stage, String key) {
        return String.format(Locale.ROOT, LINE, selector, Integer.valueOf(stage), key);
    }

    /**
     * The sound key Presence Footsteps gives a square, asked of its solver as one footstep asks it.
     *
     * @param mod     the mod's class ({@link #MOD}, or a stand-in)
     * @param level   the client's level
     * @param player  the client's player, the walker
     * @param pos     the square
     * @param mutable the same square, mutable, for the builds that walk it up and down
     * @param box     the walker's box at the square, which the mod holds the square's shape to
     */
    public static String heard(Class<?> mod, Object level, Object player, Object pos, Object mutable, Object box)
        throws ReflectiveOperationException {
        Method getInstance = mod.getMethod("getInstance");
        Object instance = getInstance.invoke(null);
        if (instance == null) throw new IllegalStateException(mod.getName() + ".getInstance() is null: not started");
        Object engine = engineOf(getInstance.getReturnType(), instance);
        if (engine == null) throw new IllegalStateException("Presence Footsteps has no sound engine yet");
        Object solver = solverOf(engine);
        if (solver == null) throw new IllegalStateException("Presence Footsteps' engine has no solver yet");
        return keyOf(ask(solver, engine, level, player, pos, mutable, box));
    }

    /** The mod's sound engine: its getter, or the public field of the builds that have none. */
    static Object engineOf(Class<?> type, Object instance) throws ReflectiveOperationException {
        try {
            return type.getMethod("getEngine")
                .invoke(instance);
        } catch (NoSuchMethodException older) {
            return type.getField("engine")
                .get(instance);
        }
    }

    /** The engine's solver from 1.10.1, its isolator's before; each asked through the type that declares it. */
    static Object solverOf(Object engine) throws ReflectiveOperationException {
        try {
            return engine.getClass()
                .getMethod("getSolver")
                .invoke(engine);
        } catch (NoSuchMethodException older) {
            Method getIsolator = engine.getClass()
                .getMethod("getIsolator");
            Object isolator = getIsolator.invoke(engine);
            return isolator == null ? null
                : getIsolator.getReturnType()
                    .getMethod("getSolver")
                    .invoke(isolator);
        }
    }

    /** The solver's answer for the square, from whichever of the three shapes this build has. */
    static Object ask(Object solver, Object engine, Object level, Object player, Object pos, Object mutable, Object box)
        throws ReflectiveOperationException {
        for (Class<?> type = solver.getClass(); type != null; type = type.getSuperclass()) {
            for (Method each : type.getDeclaredMethods()) {
                if (!ASK.equals(each.getName())) continue;
                Object[] with = arguments(each.getParameterTypes(), engine, level, player, pos, mutable, box);
                if (with == null) continue;
                each.setAccessible(true);
                return each.invoke(solver, with);
            }
        }
        throw new NoSuchMethodException(
            solver.getClass()
                .getName() + " has no " + ASK + " of a square and a box in any shape read from the jars");
    }

    /** What one of the solver's methods of that name takes for the square, or null where it is not the square's. */
    static Object[] arguments(Class<?>[] takes, Object engine, Object level, Object player, Object pos, Object mutable,
        Object box) throws ReflectiveOperationException {
        int count = takes.length;
        if (count < 3 || !takes[count - 1].isInstance(box)) return null;
        if (count == 3 && takes[1].isInstance(pos)) {
            if (takes[0].isInstance(level)) return new Object[] { level, pos, box };
            if (takes[0].isInstance(player)) return new Object[] { player, pos, box };
            return null;
        }
        if (count == 4 && takes[1].isInstance(player) && takes[2].isInstance(mutable)) {
            return new Object[] { poolOf(takes[0], player, engine), player, mutable, box };
        }
        return null;
    }

    /** From 1.10.1 a square is asked for one walker's pool: made as the mod makes it, of the walker and engine. */
    static Object poolOf(Class<?> pool, Object player, Object engine) throws ReflectiveOperationException {
        for (Constructor<?> each : pool.getConstructors()) {
            Class<?>[] takes = each.getParameterTypes();
            if (takes.length == 2 && takes[0].isInstance(player) && takes[1].isInstance(engine)) {
                return each.newInstance(player, engine);
            }
        }
        throw new NoSuchMethodException(pool.getName() + " is not made of a walker and an engine");
    }

    /** The answer's key: getAcousticName() up to 1.9.1, the dry key's raw text after. */
    static String keyOf(Object found) throws ReflectiveOperationException {
        if (found == null) return null;
        Class<?> type = found.getClass();
        try {
            Object name = type.getMethod("getAcousticName")
                .invoke(found);
            return name == null ? null : name.toString();
        } catch (NoSuchMethodException newer) {
            Method dry = type.getMethod("dry");
            Object key = dry.invoke(found);
            if (key == null) return null;
            Object raw = dry.getReturnType()
                .getMethod("raw")
                .invoke(key);
            return raw == null ? null : raw.toString();
        }
    }

    /** The ground's own key: the builds before 1.10.1 add a plant's after a comma. */
    static String ground(String key) {
        if (key == null) return null;
        int comma = key.indexOf(',');
        return (comma < 0 ? key : key.substring(0, comma)).trim();
    }

    /**
     * Null when the mod heard a worn square as its ground; otherwise what it heard instead.
     *
     * @param ghost         whether the square is drawn as this mod's ghost in the client, which is what was asked
     * @param wornAsGravel  whether the square wears as gravel, where gravel is its ground's own sound
     * @param key           what the mod heard
     */
    public static String fault(boolean ghost, boolean wornAsGravel, String key) {
        String ground = ground(key);
        if (ground == null || ground.isEmpty()) return "Presence Footsteps answered no key at the square";
        for (String nothing : NOTHING) {
            if (nothing.equals(ground)) {
                return "Presence Footsteps heard nothing at the square (" + key + "): not asked as a step asks";
            }
        }
        if (!ghost) return "the square is not worn in this client, so no worn square was heard";
        if (GHOST.equals(ground) && !wornAsGravel) {
            return "heard as " + GHOST + ", the ghost's own sound: the mod was handed the ghost, not its ground";
        }
        return null;
    }
}
