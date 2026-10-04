package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The storage layer, which is the one part of this mod that can damage a save if it is wrong.
 *
 * <p>
 * Everything here runs without Minecraft on the classpath: keys, packed records and the chunk
 * blob are deliberately free of game types so they can be tested directly. The corruption
 * cases matter most — a chunk whose erosion cannot be parsed must still load, because erosion
 * is cosmetic and a world is not.
 */
class ErosionStorageTest {

    @Test
    @DisplayName("every position in a 1.12.2-height chunk column round-trips through a key")
    void keysRoundTrip() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    int key = ErosionKey.pack(x, y, z);
                    assertEquals(x, ErosionKey.localX(key), "local x");
                    assertEquals(z, ErosionKey.localZ(key), "local z");
                    assertEquals(y, ErosionKey.y(key), "y");
                }
            }
        }
    }

    @Test
    @DisplayName("keys are unique across a whole modern column, not just an old one")
    void keysAreUnique() {
        Set<Integer> seen = new HashSet<Integer>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y++) {
                    assertTrue(seen.add(Integer.valueOf(ErosionKey.pack(x, y, z))), "duplicate key");
                }
            }
        }
        assertEquals(16 * 16 * 384, seen.size());
    }

    @Test
    @DisplayName("world coordinates reduce to chunk-local ones, including negatives")
    void worldCoordinatesWrap() {
        for (int worldX = -64; worldX < 64; worldX++) {
            for (int worldZ = -64; worldZ < 64; worldZ++) {
                int key = ErosionKey.packWorld(worldX, 70, worldZ);
                assertEquals(worldX & 0xF, ErosionKey.localX(key));
                assertEquals(worldZ & 0xF, ErosionKey.localZ(key));
                assertEquals(worldX, ErosionKey.worldX(key, worldX >> 4));
                assertEquals(worldZ, ErosionKey.worldZ(key, worldZ >> 4));
            }
        }
    }

    @Test
    @DisplayName("records written before the stage field widened still decode identically")
    void oldFlagsStillDecode() {
        // The stage field used to be four bits with the eighth reserved and always zero. This
        // rebuilds a byte exactly as that version wrote it and insists the wider mask reads the
        // same numbers back, because getting this wrong turns every existing world's paths into
        // a different surface at a different depth rather than failing where anyone would see it.
        //
        // Only the first eight families are checked, and that is the point rather than a gap:
        // the old byte held the family in three bits, so a record written by that version can
        // only ever name one of the eight that existed then. A ninth or tenth family is
        // unreachable through this path by construction, and asserting otherwise would be
        // asserting that a format could store something it had no room for.
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (family.ordinal() >= 8) continue;
            for (int stage = -1; stage <= 14; stage++) {
                short legacy = ErosionState.fromLegacy((byte) ((family.ordinal() & 0x7) | (((stage + 1) & 0xF) << 3)));
                assertEquals(family, ErosionEntry.familyOf(legacy), "family for " + family + " stage " + stage);
                assertEquals(
                    stage,
                    ErosionState.legacyStageOf((byte) legacy),
                    "stage for " + family + " stage " + stage);
            }
        }
    }

    @Test
    @DisplayName("the widened stage field reaches the sixteen stages a family may have")
    void widenedStageRange() {
        for (SurfaceFamily family : SurfaceFamily.values()) {
            for (int stage = -1; stage < SurfaceFamily.MAX_STAGES; stage++) {
                ErosionEntry entry = new ErosionEntry(family, 10f, 0);
                entry.setAppearance(family, stage, 10f);
                short state = entry.packState();
                assertEquals(stage, ErosionEntry.stageOf(state), "stage " + stage + " of " + family);
                assertEquals(family, ErosionEntry.familyOf(state), "family " + family + " at stage " + stage);
            }
        }
    }

    @Test
    @DisplayName("family and stage survive the packed flags byte")
    void flagsRoundTrip() {
        for (SurfaceFamily family : SurfaceFamily.values()) {
            for (int stage = -1; stage < SurfaceFamily.MAX_STAGES; stage++) {
                ErosionEntry entry = new ErosionEntry(family, 10f, 0);
                entry.setAppearance(family, stage, 10f);
                short state = entry.packState();
                assertEquals(family, ErosionEntry.familyOf(state), "family for " + family + " stage " + stage);
                assertEquals(stage, ErosionEntry.stageOf(state), "stage for " + family + " stage " + stage);
            }
        }
    }

    @Test
    @DisplayName("a chunk of entries survives a write and read")
    void chunkBlobRoundTrips() {
        ChunkErosionData data = new ChunkErosionData();
        Random random = new Random(1234L);
        int count = 376;

        for (int i = 0; i < count; i++) {
            int key = ErosionKey.pack(random.nextInt(16), random.nextInt(256), random.nextInt(16));
            SurfaceFamily family = SurfaceFamily.values()[random.nextInt(SurfaceFamily.values().length)];
            ErosionEntry entry = new ErosionEntry(family, 5f + random.nextFloat() * 20f, random.nextInt(100000));
            entry.setAppearance(family, random.nextInt(4), 12f);
            entry.recordStep(random.nextFloat() * 5f, 500);
            data.put(key, entry);
        }

        byte[] blob = data.write();
        assertNotNull(blob, "a non-empty chunk must serialise");

        ChunkErosionData restored = ChunkErosionData.read(blob);
        assertEquals(data.size(), restored.size(), "entry count");

        for (int key : data.keys()) {
            ErosionEntry original = data.get(key);
            ErosionEntry copy = restored.get(key);
            assertNotNull(copy, "entry missing after round-trip");
            assertEquals(original.getFamily(), copy.getFamily());
            assertEquals(original.getStage(), copy.getStage());
            assertEquals(original.getWear(), copy.getWear(), 0.0001f);
            assertEquals(original.getThreshold(), copy.getThreshold(), 0.0001f);
            assertEquals(original.getLastTouchedSeconds(), copy.getLastTouchedSeconds());
        }
    }

    @Test
    @DisplayName("an empty chunk serialises to nothing, so abandoned chunks shed the tag")
    void emptyChunkWritesNothing() {
        assertNull(new ChunkErosionData().write());
    }

    @Test
    @DisplayName("malformed blobs yield an empty map instead of throwing")
    void corruptionIsSurvivable() {
        assertEquals(
            0,
            ChunkErosionData.read(null)
                .size(),
            "null");
        assertEquals(
            0,
            ChunkErosionData.read(new byte[0])
                .size(),
            "empty");
        assertEquals(
            0,
            ChunkErosionData.read(new byte[] { 99 })
                .size(),
            "unknown version");

        ChunkErosionData data = new ChunkErosionData();
        ErosionEntry entry = new ErosionEntry(SurfaceFamily.GRASS, 8f, 42);
        entry.setAppearance(SurfaceFamily.GRASS, 2, 8f);
        data.put(ErosionKey.pack(3, 64, 9), entry);
        byte[] blob = data.write();

        // Every truncation of a valid blob must read cleanly, however abrupt.
        for (int length = 0; length < blob.length; length++) {
            byte[] truncated = new byte[length];
            System.arraycopy(blob, 0, truncated, 0, length);
            ChunkErosionData.read(truncated); // must not throw
        }

        // And so must arbitrary noise that happens to carry the right version byte.
        Random random = new Random(99L);
        for (int round = 0; round < 300; round++) {
            byte[] noise = new byte[random.nextInt(200)];
            random.nextBytes(noise);
            if (noise.length > 0) noise[0] = 2;
            ChunkErosionData.read(noise); // must not throw
        }
    }

    @Test
    @DisplayName("entries with no progress are pruned so a healed chunk loses its tag")
    void pruningDropsSpentEntries() {
        ChunkErosionData data = new ChunkErosionData();
        data.put(ErosionKey.pack(1, 64, 1), new ErosionEntry(SurfaceFamily.GRASS, 8f, 0));

        ErosionEntry worn = new ErosionEntry(SurfaceFamily.SAND, 8f, 0);
        worn.setAppearance(SurfaceFamily.SAND, 1, 8f);
        data.put(ErosionKey.pack(2, 64, 2), worn);

        data.prune();
        assertEquals(1, data.size(), "only the visibly worn entry should survive");
        assertNotNull(data.write(), "a surviving entry still serialises");
    }

    @Test
    @DisplayName("chunk keys stay distinct across dimensions and negative coordinates")
    void chunkKeysAreDistinct() {
        Set<Long> seen = new HashSet<Long>();
        int[] dimensions = { -1, 0, 1, 7, -30 };
        for (int dimension : dimensions) {
            for (int x = -40; x <= 40; x += 7) {
                for (int z = -40; z <= 40; z += 7) {
                    long key = ErosionStore.chunkKey(dimension, x, z);
                    assertTrue(seen.add(Long.valueOf(key)), "collision at " + dimension + " " + x + "," + z);
                    assertEquals(dimension, ErosionStore.dimensionOf(key), "dimension");
                    assertEquals(x, ErosionStore.chunkXOf(key), "chunk x");
                    assertEquals(z, ErosionStore.chunkZOf(key), "chunk z");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // The format change that gave a position its own depth
    // ------------------------------------------------------------------

    /**
     * A key in the layout every format before 6 wrote: x in bits 12-15, z in 8-11, y in 0-7.
     *
     * <p>
     * Written out here rather than called, because the code that knew how to make one is gone and the
     * point of the tests below is that a save containing one still loads. If this and
     * {@code ErosionKey.upgradeFromShort} ever disagree, the tests that use both will say so.
     */
    private static short oldKey(int localX, int y, int localZ) {
        return (short) (((localX & 0xF) << 12) | ((localZ & 0xF) << 8) | (y & 0xFF));
    }

    /** Builds a blob exactly as the version that stored one byte per record wrote it. */
    private static byte[] legacyBlob(SurfaceFamily family, int stage, float wear, float threshold, int touched,
        short key) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(1 + 15);
        buf.put((byte) 2); // that version's format number
        buf.putShort(key);
        buf.put((byte) ((family.ordinal() & 0x7) | (((stage + 1) & 0xF) << 3)));
        buf.putFloat(wear);
        buf.putFloat(threshold);
        buf.putInt(touched);
        return buf.array();
    }

    @Test
    @DisplayName("a world written before positions had a depth still has its paths afterwards")
    void legacyWorldSurvives() {
        byte[] old = legacyBlob(SurfaceFamily.SAND, 5, 7.5f, 20f, 123456, oldKey(3, 64, 9));

        ChunkErosionData data = ChunkErosionData.read(old);
        // Looked up by what the current packer would make of that position, which is the whole claim:
        // the upgrade has to land the record where everything else in the mod will go looking for it.
        ErosionEntry entry = data.get(ErosionKey.pack(3, 64, 9));

        assertNotNull(entry, "the record must survive the format change, not be discarded");
        assertEquals(SurfaceFamily.SAND, entry.getFamily(), "family");
        assertEquals(7.5f, entry.getWear(), 0.001f, "progress toward the next step is unchanged");
        assertEquals(20f, entry.getThreshold(), 0.001f, "the step size is unchanged");
        assertEquals(123456, entry.getLastTouchedSeconds(), "the healing clock is unchanged");
    }

    @Test
    @DisplayName("an old stage that had sunk comes back at the depth it was drawn at")
    void legacyDepthIsRecovered() {
        // Sand ran eight stages and began sinking 35% of the way along, reaching six sixteenths
        // at the last one. Its final stage must not come back sitting flat on the ground.
        short old = oldKey(1, 70, 1);
        int key = ErosionKey.pack(1, 70, 1);
        ChunkErosionData deep = ChunkErosionData.read(legacyBlob(SurfaceFamily.SAND, 7, 0f, 20f, 1, old));
        ChunkErosionData shallow = ChunkErosionData.read(legacyBlob(SurfaceFamily.SAND, 0, 0f, 20f, 1, old));

        assertTrue(
            deep.get(key)
                .getSink() > 0,
            "a fully worn old record had visibly sunk");
        assertEquals(
            0,
            shallow.get(key)
                .getSink(),
            "a barely worn one had not");
        assertTrue(
            deep.get(key)
                .getSink()
                > shallow.get(key)
                    .getSink(),
            "depth must still increase with wear");
    }

    @Test
    @DisplayName("the top two bits carry a reinforcement level, 0-3, through the packed word")
    void reinforcementRoundTrips() {
        // Bit 12 was reserved until a ninth family needed its high bit, bit 13 until pinning
        // claimed it, and these last two until reinforcement did. A record that sets them is no
        // longer a future version's - it is a reinforced block, and reads back as one.
        for (int level = 0; level <= 3; level++) {
            short state = ErosionState.pack(SurfaceFamily.DIRT, 2, 3, false, level);
            assertEquals(level, ErosionState.reinforceOf(state), "reinforcement survives the pack");
            ErosionEntry entry = ErosionEntry.fromPacked(state, 1f, 2f, 3);
            assertNotNull(entry, "a reinforced record must not be dropped");
            assertEquals(level, entry.getReinforce(), "the level survives a round trip through the entry");
            assertEquals(state, entry.packState(), "and repacks to the same word");
        }
    }

    @Test
    @DisplayName("a reinforced but unworn position keeps its record rather than being pruned")
    void reinforcedUnwornSurvivesPrune() {
        short state = ErosionState.pack(SurfaceFamily.DIRT, -1, 0, false, 2);
        ErosionEntry entry = ErosionEntry.fromPacked(state, 0f, 1f, 0);
        assertNotNull(entry);
        assertFalse(entry.isVisible(), "it shows nothing");
        assertFalse(entry.isPrunable(), "but its reinforcement keeps it from being pruned");
    }

    // ------------------------------------------------------------------
    // The format change that gave a position a spawn ward
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a spawn ward survives the chunk blob alongside everything else")
    void wardRoundTrips() {
        ChunkErosionData data = new ChunkErosionData();
        // One position per ward value, each also carrying real wear so it is a full record.
        for (int ward = 0; ward <= 3; ward++) {
            int key = ErosionKey.pack(ward, 64 + ward, ward);
            ErosionEntry entry = new ErosionEntry(SurfaceFamily.STONE, 9f, 100 + ward);
            entry.setAppearance(SurfaceFamily.STONE, 2, 12f);
            entry.setWard(ward);
            data.put(key, entry);
        }

        ChunkErosionData restored = ChunkErosionData.read(data.write());
        for (int ward = 0; ward <= 3; ward++) {
            ErosionEntry copy = restored.get(ErosionKey.pack(ward, 64 + ward, ward));
            assertNotNull(copy, "warded record missing after round-trip");
            assertEquals(ward, copy.getWard(), "the ward flags survive");
            assertEquals((ward & 1) != 0, copy.wardsHostile(), "hostile flag");
            assertEquals((ward & 2) != 0, copy.wardsPassive(), "passive flag");
        }
    }

    @Test
    @DisplayName("a warded but unworn position keeps its record rather than being pruned")
    void wardedUnwornSurvivesPrune() {
        ErosionEntry entry = new ErosionEntry(SurfaceFamily.STONE, 1f, 0);
        entry.setWard(0x1); // hostile barred, nothing else
        assertFalse(entry.isVisible(), "it shows nothing");
        assertFalse(entry.isPrunable(), "but its ward keeps it from being pruned");
        entry.setWard(0);
        assertTrue(entry.isPrunable(), "cleared of its ward, it prunes as usual");
    }

    /** Builds a blob exactly as version 3 wrote it, before the ward byte was appended. */
    private static byte[] v3Blob(SurfaceFamily family, int stage, float wear, float threshold, int touched, short key) {
        ErosionEntry entry = new ErosionEntry(family, threshold, touched);
        entry.setAppearance(family, stage, threshold);
        entry.setWear(wear);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(1 + 16);
        buf.put((byte) 3); // version 3, no ward byte
        buf.putShort(key);
        buf.putShort(entry.packState());
        buf.putFloat(wear);
        buf.putFloat(threshold);
        buf.putInt(touched);
        return buf.array();
    }

    @Test
    @DisplayName("a world written before the ward existed still has its paths, unwarded")
    void wardlessWorldSurvives() {
        ChunkErosionData data = ChunkErosionData
            .read(v3Blob(SurfaceFamily.COBBLE, 4, 6.5f, 18f, 4242, oldKey(5, 72, 11)));
        ErosionEntry entry = data.get(ErosionKey.pack(5, 72, 11));

        assertNotNull(entry, "the record must survive the format change, not be discarded");
        assertEquals(SurfaceFamily.COBBLE, entry.getFamily(), "family");
        assertEquals(4, entry.getStage(), "stage");
        assertEquals(6.5f, entry.getWear(), 0.001f, "progress is unchanged");
        assertEquals(18f, entry.getThreshold(), 0.001f, "the step size is unchanged");
        assertEquals(4242, entry.getLastTouchedSeconds(), "the healing clock is unchanged");
        assertEquals(0, entry.getWard(), "and it reads back unwarded");
    }

    @Test
    @DisplayName("a pin survives the packed state word alongside everything else")
    void pinRoundTrips() {
        for (SurfaceFamily family : SurfaceFamily.values()) {
            for (int layer = 0; layer < SurfaceFamily.MAX_STAGES; layer++) {
                for (int sink = 0; sink <= SinkProfile.MAX_SINK_PIXELS; sink++) {
                    short pinned = ErosionState.pack(family, layer, sink, true);
                    assertTrue(ErosionState.frozenOf(pinned), "pinned");
                    assertEquals(family, ErosionState.familyOf(pinned), "family beside the pin");
                    assertEquals(layer, ErosionState.layerOf(pinned), "layer beside the pin");
                    assertEquals(sink, ErosionState.sinkOf(pinned), "depth beside the pin");
                    assertFalse(ErosionState.hasUnknownBits(pinned), "the pin is a bit this version understands");
                }
            }
        }
    }

    @Test
    @DisplayName("a record written before pinning existed reads back unpinned and unchanged")
    void recordsWrittenWithoutAPinAreUntouched() {
        // The whole argument for spending a reserved bit rather than a format number: nothing
        // ever wrote here, so every record already on disk decodes to exactly what it did.
        short before = ErosionState.pack(SurfaceFamily.DIRT, 2, 3);
        assertFalse(ErosionState.frozenOf(before), "an old record is not pinned");
        ErosionEntry entry = ErosionEntry.fromPacked(before, 1f, 2f, 3);
        assertNotNull(entry, "an old record still loads");
        assertFalse(entry.isFrozen(), "and loads unpinned");
        assertEquals(SurfaceFamily.DIRT, entry.getFamily(), "family");
        assertEquals(2, entry.getStage(), "stage");
        assertEquals(3, entry.getSink(), "depth");
    }

    @Test
    @DisplayName("nothing with no appearance can be pinned")
    void aPinNeedsSomethingToShow() {
        // An invisible entry is prunable, so a pin on one would be dropped at the next save
        // without anything noticing. Refusing it up front keeps the flag meaning what it says.
        assertFalse(
            ErosionState.frozenOf(ErosionState.pack(SurfaceFamily.SAND, -1, 0, true)),
            "an invisible record cannot carry a pin");
        assertEquals(
            ErosionState.NONE,
            ErosionState.pack(SurfaceFamily.GRASS, -1, 0, true),
            "and the removal sentinel stays exactly zero");
    }

    @Test
    @DisplayName("family, layer and depth all survive the packed state word")
    void stateRoundTrips() {
        for (SurfaceFamily family : SurfaceFamily.values()) {
            for (int layer = -1; layer < SurfaceFamily.MAX_STAGES; layer++) {
                for (int sink = 0; sink <= SinkProfile.MAX_SINK_PIXELS; sink++) {
                    short state = ErosionState.pack(family, layer, sink);
                    assertEquals(family, ErosionState.familyOf(state), "family");
                    assertEquals(layer, ErosionState.layerOf(state), "layer");
                    assertEquals(sink, ErosionState.sinkOf(state), "sink");
                }
            }
        }
    }

    @Test
    @DisplayName("nothing worn still packs to zero, which is what removal means on the wire")
    void nothingIsStillZero() {
        assertEquals(
            ErosionState.NONE,
            ErosionState.pack(SurfaceFamily.GRASS, -1, 0),
            "the removal sentinel must not have moved");
        for (SurfaceFamily family : SurfaceFamily.values()) {
            for (int layer = 0; layer < SurfaceFamily.MAX_STAGES; layer++) {
                assertTrue(
                    ErosionState.pack(family, layer, 0) != ErosionState.NONE,
                    "a visible record must never look like a removal");
            }
        }
    }

    @Test
    @DisplayName("a depth deeper than a player can climb out of is refused on read")
    void depthIsClampedOnRead() {
        short state = ErosionState.pack(SurfaceFamily.DIRT, 3, 15);
        assertEquals(
            SinkProfile.MAX_SINK_PIXELS,
            ErosionState.sinkOf(state),
            "a corrupt depth must be held at what the engine can stand on");
    }

    // ------------------------------------------------------------------
    // The tallies trampling keeps, which needed no format change
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a plant's trample tally survives pruning and the chunk blob with its figures intact")
    void trampleTallyRoundTrips() {
        // A tally is an invisible record whose only claim to being kept is its wear. Nothing was added
        // to the format for it, and this is the test that says nothing had to be: a prune keeps any
        // record with wear on it, and an invisible record of the eighth family packs to a word that is
        // not the removal sentinel and reads back as itself. The clock matters as much as the wear,
        // because the fade is measured from it after every load.
        int key = ErosionKey.pack(7, 65, 12);
        ErosionEntry tally = TrampleTally.start(SurfaceFamily.VEGETATION, 20f, 20f, 0f, 1234);
        tally.recordStep(5f, 1234);
        ChunkErosionData data = new ChunkErosionData();
        data.put(key, tally);

        data.prune();
        assertEquals(1, data.size(), "wear alone must keep an invisible record through a prune");

        byte[] blob = data.write();
        assertNotNull(blob, "a chunk holding only a tally still serialises");
        ErosionEntry copy = ChunkErosionData.read(blob)
            .get(key);
        assertNotNull(copy, "the tally must come back from the blob");
        assertEquals(SurfaceFamily.VEGETATION, copy.getFamily(), "family");
        assertEquals(-1, copy.getStage(), "still invisible");
        assertEquals(5f, copy.getWear(), 0f, "wear");
        assertEquals(20f, copy.getThreshold(), 0f, "threshold, at face value");
        assertEquals(1234, copy.getLastTouchedSeconds(), "the clock its fade is measured from");
    }

    @Test
    @DisplayName("the widened key carries 1.18's world, floor and ceiling included")
    void theWidenedKeyCarriesAModernWorld() {
        for (int x = 0; x < 16; x += 5) {
            for (int z = 0; z < 16; z += 5) {
                for (int y = -64; y <= 319; y++) {
                    int key = ErosionKey.pack(x, y, z);
                    assertEquals(x, ErosionKey.localX(key), "local x at y " + y);
                    assertEquals(z, ErosionKey.localZ(key), "local z at y " + y);
                    assertEquals(y, ErosionKey.y(key), "y");
                }
            }
        }
    }

    @Test
    @DisplayName("a key written by a build before the widening is read into the right place")
    void anOldKeyUpgradesToTheRightPosition() {
        // The old layout, built here rather than borrowed, so this keeps testing the upgrade after
        // the only code that knew how to write it is gone: x in bits 12-15, z in 8-11, y in 0-7.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y += 17) {
                    short old = (short) (((x & 0xF) << 12) | ((z & 0xF) << 8) | (y & 0xFF));
                    int now = ErosionKey.upgradeFromShort(old);
                    assertEquals(x, ErosionKey.localX(now), "local x");
                    assertEquals(z, ErosionKey.localZ(now), "local z");
                    assertEquals(y, ErosionKey.y(now), "y");
                    assertEquals(ErosionKey.pack(x, y, z), now, "not the key pack would have made");
                }
            }
        }
    }

    @Test
    @DisplayName("a version 5 chunk still loads, and comes back in the current format")
    void aVersionFiveChunkStillLoads() {
        // A version 5 blob, written by hand: version byte, then key(2) state(2) wear(4) threshold(4)
        // touched(4) ward(1) light(1) per entry. This is the format every save written before the
        // widening is in, so it is built from its bytes rather than from the writer, which can no
        // longer produce it.
        int count = 64;
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(1 + count * 18);
        buf.put((byte) 5);
        Random random = new Random(606L);
        short[] oldKeys = new short[count];
        short[] states = new short[count];
        for (int i = 0; i < count; i++) {
            int x = i % 16;
            int z = (i / 16) % 16;
            int y = 60 + i;
            oldKeys[i] = (short) (((x & 0xF) << 12) | ((z & 0xF) << 8) | (y & 0xFF));
            states[i] = ErosionState.pack(SurfaceFamily.GRASS, 1 + random.nextInt(8), 0);
            buf.putShort(oldKeys[i]);
            buf.putShort(states[i]);
            buf.putFloat(3.5f + i);
            buf.putFloat(40f);
            buf.putInt(1000 + i);
            buf.put((byte) 0);
            buf.put((byte) 0);
        }

        ChunkErosionData read = ChunkErosionData.read(buf.array());
        assertEquals(count, read.size(), "every version 5 entry should survive");

        for (int i = 0; i < count; i++) {
            int expected = ErosionKey.upgradeFromShort(oldKeys[i]);
            ErosionEntry entry = read.get(expected);
            assertNotNull(entry, "entry " + i + " did not land on its upgraded key");
            assertEquals(3.5f + i, entry.getWear(), 0.0001f, "wear of entry " + i);
            assertEquals(1000 + i, entry.getLastTouchedSeconds(), "touched of entry " + i);
        }

        // And it writes back in the current format, so no key is upgraded twice.
        byte[] again = read.write();
        assertNotNull(again);
        assertEquals(6, again[0], "a loaded old chunk must be written back as version 6");
        assertEquals(
            count,
            ChunkErosionData.read(again)
                .size(),
            "and read again from version 6");
    }

    @Test
    @DisplayName("reading an older format is counted, so something can say so")
    void anUpgradeIsCounted() {
        // The store logs this once per session and reads the count from here, because the core is not
        // allowed to name the mod's logger. What is checked is the wiring: four format changes shipped
        // with no way to tell from outside whether a migration had run, and this is what fixed that.
        int before = ChunkErosionData.upgradedCount();
        ChunkErosionData.read(v3Blob(SurfaceFamily.STONE, 2, 1f, 10f, 7, oldKey(2, 66, 4)));

        assertTrue(ChunkErosionData.upgradedCount() > before, "reading a version 3 blob must count");
        assertTrue(ChunkErosionData.oldestFormatSeen() <= 3, "and must remember how old it was");
        assertEquals(6, ChunkErosionData.currentFormat(), "the format this build writes");
    }
}
