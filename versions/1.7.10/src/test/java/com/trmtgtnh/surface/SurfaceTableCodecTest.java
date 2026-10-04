package com.trmtgtnh.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A server's surface table on its way to a client.
 *
 * <p>
 * What a client draws, and the ground it collides with, follow from this table. Two failures would
 * be quiet and both would be believed: a table that arrives different from the one sent, and a
 * fingerprint that does not move when a table does, so a client goes on drawing a table the server
 * has left behind. Both are arithmetic, so both are pinned here.
 */
class SurfaceTableCodecTest {

    private static int key(int id, int meta) {
        return (id << 4) | meta;
    }

    private static Map<Integer, Integer> sample() {
        Map<Integer, Integer> families = new HashMap<Integer, Integer>();
        for (int meta = 0; meta < 16; meta++) families.put(key(2, meta), 0);
        families.put(key(3, 0), 1);
        families.put(key(3, 2), 2);
        families.put(key(44, 3), 5);
        families.put(key(4095, 15), 11);
        return families;
    }

    private static Set<Integer> set(int... keys) {
        Set<Integer> out = new HashSet<Integer>();
        for (int k : keys) out.add(k);
        return out;
    }

    @Test
    @DisplayName("a table comes back exactly as it was sent")
    void roundTrip() {
        Map<Integer, Integer> families = sample();
        Set<Integer> cover = set(key(31, 1), key(31, 2), key(38, 0));
        Set<Integer> holds = set(key(38, 0));
        SurfaceTableCodec.Table back = SurfaceTableCodec.decode(SurfaceTableCodec.encode(families, cover, holds));
        assertEquals(families, back.families);
        assertEquals(cover, back.cover);
        assertEquals(holds, back.holds);
    }

    @Test
    @DisplayName("an empty table is a table")
    void emptyRoundTrip() {
        Map<Integer, Integer> none = new HashMap<Integer, Integer>();
        SurfaceTableCodec.Table back = SurfaceTableCodec
            .decode(SurfaceTableCodec.encode(none, new HashSet<Integer>(), new HashSet<Integer>()));
        assertTrue(back.families.isEmpty());
        assertTrue(back.cover.isEmpty());
        assertTrue(back.holds.isEmpty());
    }

    @Test
    @DisplayName("the fingerprint does not care what order a table was built in")
    void fingerprintIgnoresOrder() {
        Map<Integer, Integer> forwards = new LinkedHashMap<Integer, Integer>();
        Map<Integer, Integer> backwards = new LinkedHashMap<Integer, Integer>();
        forwards.put(key(2, 0), 0);
        forwards.put(key(3, 0), 1);
        backwards.put(key(3, 0), 1);
        backwards.put(key(2, 0), 0);
        Set<Integer> none = new HashSet<Integer>();
        assertEquals(
            SurfaceTableCodec.fingerprint(forwards, none, none, true),
            SurfaceTableCodec.fingerprint(backwards, none, none, true));
    }

    @Test
    @DisplayName("two blocks swapping families moves the fingerprint, which a sum would not")
    void fingerprintSeesASwap() {
        Map<Integer, Integer> before = new HashMap<Integer, Integer>();
        Map<Integer, Integer> after = new HashMap<Integer, Integer>();
        before.put(key(2, 0), 0);
        before.put(key(3, 0), 1);
        after.put(key(2, 0), 1);
        after.put(key(3, 0), 0);
        Set<Integer> none = new HashSet<Integer>();
        assertNotEquals(
            SurfaceTableCodec.fingerprint(before, none, none, true),
            SurfaceTableCodec.fingerprint(after, none, none, true));
    }

    @Test
    @DisplayName("ground cover, what holds, and the hold switch are all part of the answer")
    void fingerprintSeesTheSets() {
        Map<Integer, Integer> families = sample();
        Set<Integer> cover = set(key(31, 1));
        Set<Integer> none = new HashSet<Integer>();
        long plain = SurfaceTableCodec.fingerprint(families, cover, none, true);
        assertNotEquals(plain, SurfaceTableCodec.fingerprint(families, none, none, true), "cover");
        assertNotEquals(plain, SurfaceTableCodec.fingerprint(families, cover, cover, true), "holds");
        assertNotEquals(plain, SurfaceTableCodec.fingerprint(families, cover, none, false), "switch");
        assertNotEquals(
            SurfaceTableCodec.fingerprint(families, cover, none, true),
            SurfaceTableCodec.fingerprint(families, none, cover, true),
            "a key moved from one set to the other is not the same table");
    }

    @Test
    @DisplayName("a large pack's table fits comfortably inside a payload")
    void largeTableFits() {
        Map<Integer, Integer> families = new HashMap<Integer, Integer>();
        Set<Integer> cover = new HashSet<Integer>();
        Set<Integer> holds = new HashSet<Integer>();
        // More than the 8,456 states of the largest pack this mod has met, spread across ids.
        for (int id = 100; id < 700; id++) {
            for (int meta = 0; meta < 16; meta++) families.put(key(id, meta), id % 12);
        }
        for (int id = 1000; id < 1300; id++) {
            for (int meta = 0; meta < 16; meta++) cover.add(key(id, meta));
            if (id % 3 == 0) holds.add(key(id, 0));
        }
        byte[] bytes = SurfaceTableCodec.encode(families, cover, holds);
        assertTrue(bytes.length < 16000, "compressed to " + bytes.length + " bytes");
        assertEquals(families, SurfaceTableCodec.decode(bytes).families);
    }

    @Test
    @DisplayName("bytes that are not a table are refused rather than misread")
    void malformedIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SurfaceTableCodec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> SurfaceTableCodec.decode(new byte[] { 1, 2, 3, 4 }));
        byte[] good = SurfaceTableCodec.encode(sample(), new HashSet<Integer>(), new HashSet<Integer>());
        byte[] cut = java.util.Arrays.copyOf(good, good.length / 2);
        assertThrows(IllegalArgumentException.class, () -> SurfaceTableCodec.decode(cut));
        assertThrows(
            IllegalArgumentException.class,
            () -> SurfaceTableCodec.decode(new byte[SurfaceTableCodec.MAX_COMPRESSED + 1]));
    }

    @Test
    @DisplayName("an ordinal that cannot fit its four bits is refused on the way out")
    void badOrdinalIsRefused() {
        Map<Integer, Integer> families = new HashMap<Integer, Integer>();
        families.put(key(2, 0), 15);
        assertThrows(
            IllegalArgumentException.class,
            () -> SurfaceTableCodec.encode(families, new HashSet<Integer>(), new HashSet<Integer>()));
    }

    @Test
    @DisplayName("bytes left over after a table are refused")
    void trailingBytesAreRefused() {
        byte[] good = SurfaceTableCodec.encode(sample(), new HashSet<Integer>(), new HashSet<Integer>());
        byte[] longer = java.util.Arrays.copyOf(good, good.length + 3);
        assertThrows(IllegalArgumentException.class, () -> SurfaceTableCodec.decode(longer));
    }

    @Test
    @DisplayName("a small payload that would inflate past any real table is refused rather than unpacked")
    void inflationBombIsRefused() {
        byte[] bomb = deflate(new byte[1 << 20]);
        assertTrue(bomb.length < SurfaceTableCodec.MAX_COMPRESSED, "a megabyte of nothing compresses small");
        assertThrows(IllegalArgumentException.class, () -> SurfaceTableCodec.decode(bomb));
    }

    @Test
    @DisplayName("a table written in a layout this build does not know is refused rather than misread")
    void unknownLayoutIsRefused() {
        byte[] foreign = deflate(new byte[] { (byte) (SurfaceTableCodec.FORMAT + 1), 0, 0, 0, 0 });
        assertThrows(IllegalArgumentException.class, () -> SurfaceTableCodec.decode(foreign));
    }

    private static byte[] deflate(byte[] raw) {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
