package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

/**
 * The map key, which is the one piece of this layer that had to be invented rather than carried.
 *
 * <p>
 * Both older editions pack a dimension id into the top sixteen bits, because a dimension was an int.
 * Here it is a {@code ResourceKey<Level>} and there is no number, so the store keeps its own list of
 * the levels it has seen and packs that list's index instead.
 *
 * <p>
 * That is new arithmetic in a class whose old arithmetic was load-bearing, so it is checked rather
 * than assumed - and the thing most worth checking is negative coordinates. The chunk fields are
 * twenty-four bits and are sign-extended back out by hand; get that wrong and wear in the negative
 * quadrant lands in the wrong chunk, which is the kind of fault that looks like "wear sometimes does
 * not save" and takes a week.
 */
class ErosionStoreKeyTest {

    private static final int[] COORDINATES = { 0, 1, -1, 17, -17, 1000, -1000, 8388607, -8388608 };

    @Test
    void every_chunk_coordinate_survives_the_round_trip() {
        for (int x : COORDINATES) {
            for (int z : COORDINATES) {
                long key = ErosionStore.chunkKey(0, x, z);
                assertEquals(x, ErosionStore.chunkXOf(key), "x lost at " + x + "," + z);
                assertEquals(z, ErosionStore.chunkZOf(key), "z lost at " + x + "," + z);
            }
        }
    }

    @Test
    void the_level_index_survives_beside_them() {
        for (int index : new int[] { 0, 1, 7, 255, 4096, 65535 }) {
            long key = ErosionStore.chunkKey(index, -1000, 1000);
            assertEquals(index, ErosionStore.levelIndexOf(key), "level index lost at " + index);
            assertEquals(-1000, ErosionStore.chunkXOf(key), "x disturbed by level index " + index);
            assertEquals(1000, ErosionStore.chunkZOf(key), "z disturbed by level index " + index);
        }
    }

    @Test
    void two_levels_never_share_a_key_for_the_same_chunk() {
        // The whole reason the index exists. Two levels' wear mixing together would be silent and
        // would look like wear appearing where nobody walked.
        assertNotEquals(ErosionStore.chunkKey(0, 5, 5), ErosionStore.chunkKey(1, 5, 5));
    }

    @Test
    void a_level_keeps_the_index_it_was_given() {
        ErosionStore store = ErosionStore.get();
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        ResourceLocation nether = new ResourceLocation("minecraft", "the_nether");

        int first = store.indexOf(overworld);
        int second = store.indexOf(nether);
        assertNotEquals(first, second, "two levels were given the same index");
        assertEquals(first, store.indexOf(overworld), "asking twice gave a different index");
        assertEquals(second, store.indexOf(nether), "asking twice gave a different index");

        assertEquals(overworld, store.levelNameOf(first));
        assertEquals(nether, store.levelNameOf(second));
    }

    @Test
    void an_index_nobody_has_used_names_no_level() {
        // Rather than returning something plausible. A key that outlived the session that made it
        // would otherwise resolve to whichever level happened to be at that index this time.
        assertNull(ErosionStore.get()
            .levelNameOf(60000));
        assertNull(ErosionStore.get()
            .levelNameOf(-1));
    }

    @Test
    void the_engine_seam_is_optional() {
        // The store is told about chunks whether or not anything is listening, and the engine has not
        // been ported. Nothing here may throw because of that.
        ErosionStore.forgetEngine();
        ErosionStore.get()
            .chunkLoaded(null, 0, 0);
        assertTrue(true, "reaching this line is the assertion");
    }
}
