package com.trmtgtnh.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A config snapshot has to survive being written down, and for a long time it did not.
 *
 * <p>
 * A snapshot is a whole config file - this mod's runs to a few hundred kilobytes - and it was stored
 * as an NBT string. An NBT string is written with {@code writeUTF}, whose length field is two bytes,
 * so anything past 65535 of them throws and the <em>whole file</em> goes unwritten. Every save threw.
 * The only sign was one line in the log, and a snapshot taken and used in the same session works
 * perfectly either way - it is the restart afterwards that lost them, which is why nothing caught it
 * for so long.
 *
 * <p>
 * These are the only tests here that touch a Minecraft type, and they do it because the fault was in
 * the shape of the file rather than in any arithmetic. NBT needs no game to be running: a compound and
 * the stream tools are plain data classes with no registry behind them.
 */
class SnapshotSizeTest {

    /** About the size of this mod's own config, which is what a snapshot holds. */
    private static final int REAL_SNAPSHOT_BYTES = 326_752;

    private static String snapshotSized() {
        StringBuilder big = new StringBuilder(REAL_SNAPSHOT_BYTES);
        while (big.length() < REAL_SNAPSHOT_BYTES) {
            big.append("general.erosionSpeed=1.0\n");
        }
        return big.toString();
    }

    /** The fault itself, kept as a test so nobody puts it back by reaching for setString. */
    @Test
    void aStringThatBigCannotBeWrittenAtAll(@TempDir File folder) {
        NBTTagCompound root = new NBTTagCompound();
        root.setString("left", snapshotSized());
        File file = new File(folder, "as-a-string.dat");

        assertThrows(IOException.class, () -> CompressedStreamTools.write(root, file));
    }

    /** And the fix: the same snapshot, stored the way the store stores it now. */
    @Test
    void asBytesItSurvivesTheRoundTrip(@TempDir File folder) throws IOException {
        String snapshot = snapshotSized();
        NBTTagCompound root = new NBTTagCompound();
        SnapshotStore.write(root, "left", snapshot);
        File file = new File(folder, "as-bytes.dat");

        CompressedStreamTools.write(root, file);
        assertTrue(file.isFile(), "nothing was written");

        NBTTagCompound back = CompressedStreamTools.read(file);
        assertEquals(snapshot, SnapshotStore.read(back, "left"));
    }

    /**
     * A file written by a build from before the fix still loads.
     *
     * <p>
     * Nothing has written one since, but a config small enough to fit in a string would have been
     * written that way and should not cost anybody their slot.
     */
    @Test
    void aStringWrittenByAnOlderBuildIsStillRead() {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("left", "general.erosionSpeed=1.0");

        assertEquals("general.erosionSpeed=1.0", SnapshotStore.read(entry, "left"));
    }

    /** An empty slot is a key that is not there, and reads back as nothing. */
    @Test
    void anEmptySlotIsNotWrittenAndReadsBackAsNothing() {
        NBTTagCompound entry = new NBTTagCompound();
        SnapshotStore.write(entry, "left", null);
        SnapshotStore.write(entry, "right", "");

        assertTrue(entry.hasNoTags(), "an empty slot should leave nothing behind");
        assertNull(SnapshotStore.read(entry, "left"));
        assertNull(SnapshotStore.read(entry, "right"));
    }
}
