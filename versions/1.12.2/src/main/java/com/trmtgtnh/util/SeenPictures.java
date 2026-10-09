package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Which moving pictures are on the ground near the player: the worn pictures whose layer moves (Chisel's lavastone
 * and waterstone) that a chunk section within the render distance has been drawn with (0.9.221).
 *
 * <p>
 * Xep, 2026-10-08: worn Chisel should move "not more animated than normal Chisel, not less". An unworn Chisel block
 * moves because the one liquid picture behind it moves - one upload, whatever stands where. A worn picture carries
 * its own copy of the liquid under its own shell, so every one of them that moves is an upload of its own, and on
 * GT New Horizons with Chisel there are 15,872 of them. Until 0.9.221 the atlas asked all of them every tick, in view
 * or not, and a ceiling of 256 a tick let the same 256 move while the rest stood still - whichever stood where the
 * player was looking. So a picture now moves only while a section near the player has been drawn with it: every one
 * the player can see moves in step with the liquid beside it, and the cost is the worn liquid nearby rather than the
 * atlas.
 *
 * <p>
 * Fed from the one place each edition chooses a worn ghost face's picture, with the block's position. A section is
 * forgotten only once it lies beyond the render distance ({@link #prune}), never because something else asked for a
 * picture there: a picture kept that need not be costs an upload, and one dropped that is in view stands still, which
 * is the fault this exists to end. So a square whose wear moved on keeps its old picture moving until the player
 * leaves - a few uploads at most.
 *
 * <p>
 * Portable: no Minecraft. A picture is any object, compared by identity. Every method is synchronised, because
 * sections are drawn on worker threads in the newer editions and the pictures are moved on the render thread.
 */
public final class SeenPictures {

    /** Each section's pictures, by {@link #key}. */
    private final Map<Long, List<Object>> bySection = new HashMap<Long, List<Object>>();

    /** How many sections use each picture. */
    private final Map<Object, int[]> uses = new IdentityHashMap<Object, int[]>();

    /** A chunk section's key, from its section coordinates. */
    public static long key(int sectionX, int sectionY, int sectionZ) {
        return ((long) (sectionX & 0x3FFFFF) << 42) | ((long) (sectionY & 0xFFFFF) << 22) | (sectionZ & 0x3FFFFF);
    }

    /** The section coordinates across, {x, z}, of a key. */
    static int[] across(long key) {
        return new int[] { (int) (key >> 42), ((int) (key & 0x3FFFFF)) << 10 >> 10 };
    }

    /** Notes that the section holding this block has been drawn with this moving picture. */
    public synchronized void note(Object picture, int x, int y, int z) {
        if (picture == null) return;
        Long key = Long.valueOf(key(x >> 4, y >> 4, z >> 4));
        List<Object> held = bySection.get(key);
        if (held == null) {
            held = new ArrayList<Object>();
            bySection.put(key, held);
        }
        for (Object each : held) if (each == picture) return;
        held.add(picture);
        int[] count = uses.get(picture);
        if (count == null) uses.put(picture, new int[] { 1 });
        else count[0]++;
    }

    /** Whether a section near the player has been drawn with this picture. */
    public synchronized boolean wanted(Object picture) {
        return uses.containsKey(picture);
    }

    /** Forgets every section farther than {@code radius} blocks, across, from the player: beyond what is drawn. */
    public synchronized void prune(double playerX, double playerZ, int radius) {
        double reach = radius + 16.0D;
        for (Iterator<Map.Entry<Long, List<Object>>> each = bySection.entrySet()
            .iterator(); each.hasNext();) {
            Map.Entry<Long, List<Object>> section = each.next();
            int[] at = across(
                section.getKey()
                    .longValue());
            double dx = at[0] * 16 + 8 - playerX;
            double dz = at[1] * 16 + 8 - playerZ;
            if (dx * dx + dz * dz > reach * reach) {
                release(section.getValue());
                each.remove();
            }
        }
    }

    /** Forgets everything: a new stitch makes new pictures. */
    public synchronized void clear() {
        bySection.clear();
        uses.clear();
    }

    /** How many distinct pictures are wanted now. */
    public synchronized int pictures() {
        return uses.size();
    }

    /** How many sections have been drawn with any. */
    public synchronized int sections() {
        return bySection.size();
    }

    private void release(List<Object> pictures) {
        for (Object picture : pictures) {
            int[] count = uses.get(picture);
            if (count == null) continue;
            if (--count[0] <= 0) uses.remove(picture);
        }
    }
}
