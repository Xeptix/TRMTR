package com.trmtgtnh.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic Surroundings' footprints, read by the mods part's walk (0.9.229 W6 R10; docs/goals/0.9.229-w6-spec.md 3.12 in
 * the 1.7.10 tree). The mod keeps every print it has laid in one store, {@code Footprints}, held by
 * {@code Footprints$Holder.INSTANCE}, as an {@code ArrayDeque} called {@code prints} of {@code Footprints$Print}, each
 * with the {@code ground} it was pressed into. A print is laid only where the block under a foot is in the mod's client
 * tag {@code dsurround:effects/footprints}, and this mod's file in its own namespace puts its ghost there.
 *
 * <p>
 * Read by reflection, by name, so nothing here compiles against the mod and a game without it reads nothing. The names
 * are the jars' own: 0.4.9 for 1.21.1, Fabric and NeoForge alike (javap; the mod's own classes keep their names on
 * both). Its classes are package-private, so each member is opened to read it; the NeoForge jar has no module
 * descriptor, so it is an automatic module, every package of which is open.
 */
final class ModsPrints {

    /** The mod's id, as its fabric.mod.json and neoforge.mods.toml give it. */
    static final String MOD = "dsurround";

    /** The class whose static field holds the store, by its binary name. */
    static final String HOLDER = "org.orecruncher.dsurround.effects.footprints.Footprints$Holder";

    /** The holder's field, the store's queue and a print's ground. */
    static final String INSTANCE = "INSTANCE";

    static final String PRINTS = "prints";

    static final String GROUND = "ground";

    /** What the rig reads: one line per print on the scene. */
    static final String LINE = "Spike walk: mods - print at=%d,%d,%d family=%s worn=%s";

    private ModsPrints() {}

    /** The ground under each print the mod holds now, oldest first, or null where the mod is absent or unreadable. */
    static List<Object> grounds() {
        return grounds(HOLDER, ModsPrints.class.getClassLoader());
    }

    /** The same of a holder of this name; the name is handed in so a stand-in can be read. */
    static List<Object> grounds(String holder, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(holder, true, loader);
            Object store = opened(type, INSTANCE).get(null);
            if (store == null) return null;
            Object queue = opened(store.getClass(), PRINTS).get(store);
            if (!(queue instanceof Collection)) return null;
            List<Object> out = new ArrayList<Object>();
            for (Object print : new ArrayList<Object>((Collection<?>) queue)) {
                if (print != null) out.add(opened(print.getClass(), GROUND).get(print));
            }
            return out;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
            return null;
        }
    }

    private static Field opened(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** The line, in this shape on every edition. */
    static String line(int x, int y, int z, String family, boolean worn) {
        return String.format(
            Locale.ROOT,
            LINE,
            Integer.valueOf(x),
            Integer.valueOf(y),
            Integer.valueOf(z),
            family,
            Boolean.valueOf(worn));
    }

    /** Null when the walk left prints on worn ground; otherwise what it found instead. */
    static String fault(int onScene, int onWorn) {
        if (onScene <= 0) {
            return "the walk along every worn line laid no print on the scene at all, so nothing was asked of any square";
        }
        if (onWorn <= 0) {
            return onScene + " print(s) on unworn ground and none on a worn square, so the ghost does not take footprints";
        }
        return null;
    }
}
