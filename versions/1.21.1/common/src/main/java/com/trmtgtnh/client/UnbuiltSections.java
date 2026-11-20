package com.trmtgtnh.client;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;

/**
 * The sections a redraw asked for while Sodium had not yet built them, asked for again once it has (0.9.229).
 *
 * <p>
 * <strong>Why.</strong> Sodium 0.5 and later drop a rebuild asked of a section they have not built yet:
 * {@code RenderSectionManager.scheduleRebuild} marks a section only when it is there and {@code isBuilt()} (read
 * from Sodium 0.5.13, 0.6.13 and 0.9.2). A section's first build takes its copy of the world when
 * the build is handed out and is not marked built until its result comes back, so a square painted between the two
 * is left out of it and the mark that said so is thrown away. Vanilla keeps a dirty flag whatever state the section
 * is in, and Embeddium and the 1.16.5 family mark an unbuilt section too. The turf rows met it on 26.3 Fabric
 * Sodium (W1 T11, after the redraw had been widened; carried here from 26.3): the chunk east of the dried kelp
 * lawn was having its first build as the lawn was laid and painted, kept the grass beside the deepest square with
 * its face toward a whole block, and the sky showed through the rut's end.
 *
 * <p>
 * So each section of a redraw's box that Sodium says is not ready is remembered, and marked again on the first
 * client tick that Sodium says it is: a build already under way is then followed by one that sees the paint. A
 * section that is never built - out of render distance, or its chunk gone - is let go after {@link #PATIENCE}
 * ticks. Without Sodium nothing is remembered and nothing is asked.
 */
public final class UnbuiltSections {

    /** How long a section is waited for, in client ticks: half a minute. */
    static final int PATIENCE = 600;

    /** Sodium's world renderer, by its package before and after 0.6; the first that loads is the one asked. */
    static final String[] RENDERERS = { "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer",
        "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer" };

    /** What is asked of the renderer: whether a section is built, and to mark one again. */
    interface Renderer {

        boolean ready(int x, int y, int z);

        void mark(int x, int y, int z);
    }

    private static final UnbuiltSections INSTANCE = new UnbuiltSections();

    /** Each remembered section, by its packed position, with the ticks it has waited. */
    private final Map<Long, int[]> owed = new LinkedHashMap<Long, int[]>();

    UnbuiltSections() {}

    public static UnbuiltSections get() {
        return INSTANCE;
    }

    /** Remembers each section of a block box the renderer has not built. The box is the one the redraw marked. */
    void note(Renderer renderer, int x0, int y0, int z0, int x1, int y1, int z1) {
        if (renderer == null) return;
        for (int x = x0 >> 4; x <= x1 >> 4; x++) {
            for (int y = y0 >> 4; y <= y1 >> 4; y++) {
                for (int z = z0 >> 4; z <= z1 >> 4; z++) {
                    if (renderer.ready(x, y, z)) continue;
                    Long key = Long.valueOf(SectionPos.asLong(x, y, z));
                    int[] waited = owed.get(key);
                    if (waited == null) owed.put(key, new int[] { 0 });
                    else waited[0] = 0;
                }
            }
        }
    }

    /** Marks again each remembered section the renderer has built since, and lets go of those waited on too long. */
    void settle(Renderer renderer) {
        if (owed.isEmpty()) return;
        if (renderer == null) {
            owed.clear();
            return;
        }
        Iterator<Map.Entry<Long, int[]>> each = owed.entrySet()
            .iterator();
        while (each.hasNext()) {
            Map.Entry<Long, int[]> entry = each.next();
            long key = entry.getKey()
                .longValue();
            int x = SectionPos.x(key);
            int y = SectionPos.y(key);
            int z = SectionPos.z(key);
            if (renderer.ready(x, y, z)) {
                renderer.mark(x, y, z);
                each.remove();
            } else if (++entry.getValue()[0] >= PATIENCE) {
                each.remove();
            }
        }
    }

    /** Forgets everything, for leaving a world. */
    public void clear() {
        owed.clear();
    }

    int size() {
        return owed.size();
    }

    /** The redraw's half: called with the box it has just marked. */
    static void noteRedraw(int x0, int y0, int z0, int x1, int y1, int z1) {
        INSTANCE.note(sodium(), x0, y0, z0, x1, y1, z1);
    }

    /** The client tick's half. */
    static void settleTick() {
        INSTANCE.settle(sodium());
    }

    // -- Sodium, asked by reflection: the shared module cannot name it, and most clients have none --

    private static boolean looked;

    private static Method instance;

    private static Method ready;

    /** Sodium's renderer for this world, or null where there is no Sodium or it has no world yet. */
    private static Renderer sodium() {
        if (!looked) {
            looked = true;
            for (String name : RENDERERS) {
                try {
                    Class<?> type = Class.forName(name);
                    instance = type.getMethod("instanceNullable");
                    ready = type.getMethod("isSectionReady", int.class, int.class, int.class);
                    break;
                } catch (ReflectiveOperationException | LinkageError absent) {
                    instance = null;
                    ready = null;
                }
            }
        }
        if (instance == null) return null;
        final Object renderer;
        try {
            renderer = instance.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException notYet) {
            return null;
        }
        if (renderer == null) return null;
        return new Renderer() {

            @Override
            public boolean ready(int x, int y, int z) {
                try {
                    return ((Boolean) ready.invoke(renderer, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z)))
                        .booleanValue();
                } catch (ReflectiveOperationException | RuntimeException unanswerable) {
                    // Asked as though built, so nothing is remembered that can never be settled.
                    return true;
                }
            }

            @Override
            public void mark(int x, int y, int z) {
                Minecraft client = Minecraft.getInstance();
                if (client.levelRenderer != null) client.levelRenderer.setSectionDirty(x, y, z);
            }
        };
    }
}
