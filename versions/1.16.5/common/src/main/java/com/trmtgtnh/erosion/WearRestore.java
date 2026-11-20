package com.trmtgtnh.erosion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Putting wear back (0.9.229; docs/goals/0.9.229-wear-backup-spec.md, "Restore" and "Undo"). A restore fills only the
 * chunks whose wear is missing or damaged, and in them only the records that are not there, so it never writes over
 * newer wear; and it keeps every record's own clock, so the catch-up a chunk is given as it loads applies the healing
 * that has elapsed since the backup. An undo takes back exactly what its restore put in. Portable, the same file in
 * every edition: the edition finds the chunks, this decides what goes in and what comes out.
 */
public final class WearRestore {

    /** A restore's source that is no one backup: the mirror where it holds the chunk, then the newest backup. */
    public static final String LATEST = "latest";

    private WearRestore() {}

    /**
     * Puts into {@code live} every record of {@code from} that {@code live} lacks, and nothing else: a record
     * {@code live} has is newer wear and stays as it is. Each record keeps the clock it was saved with - restamped,
     * the ground would have healed nothing for the time since the backup, and would heal it all again from now.
     *
     * @return the keys put in, for the undo
     */
    public static int[] fill(ChunkErosionData live, ChunkErosionData from) {
        if (live == null || from == null) return new int[0];
        List<Integer> put = new ArrayList<Integer>();
        for (int key : from.keys()) {
            if (live.get(key) != null) continue;
            ErosionEntry entry = from.get(key);
            if (entry == null) continue;
            live.put(key, entry);
            put.add(Integer.valueOf(key));
        }
        int[] out = new int[put.size()];
        for (int i = 0; i < out.length; i++) out[i] = put.get(i)
            .intValue();
        return out;
    }

    /** Takes out of {@code live} the records a restore put in. @return how many were there to take out */
    public static int unfill(ChunkErosionData live, int[] keys) {
        if (live == null || keys == null) return 0;
        int taken = 0;
        for (int key : keys) {
            if (live.get(key) == null) continue;
            live.remove(key);
            taken++;
        }
        return taken;
    }

    /** What an Undo restore asked for does: it runs now, after the restore still being read, or there is none. */
    public enum UndoAsked {
        NOW,
        AFTER_RESTORE,
        NOTHING
    }

    /**
     * What an Undo restore asked for does (0.9.229): a restore still being read (a backup's file, on the background
     * thread) is undone as soon as it has started, never ignored, since the player was shown the button and pressed it;
     * otherwise the restore in hand is undone, or there is none to undo.
     *
     * @param restoreStarting a restore has been asked for and has not started yet
     * @param restoreToUndo   the restore in hand has put something in, or has chunks still to do, and is not an undo
     */
    public static UndoAsked undoAsked(boolean restoreStarting, boolean restoreToUndo) {
        if (restoreStarting) return UndoAsked.AFTER_RESTORE;
        return restoreToUndo ? UndoAsked.NOW : UndoAsked.NOTHING;
    }

    /**
     * One restore, kept in the world ({@code <world>/trmtgtnh/restore.dat}) until it is done and for its undo: what it
     * restores from, the backup taken just before it, the chunks still to do as they next load, and the records put
     * into each chunk so far. Chunks that are not loaded are done when they load rather than loaded for it, so a
     * restore never makes the server load the world.
     */
    public static final class Run {

        /** A backup's name, or {@link #LATEST}. */
        public String source;

        /** The backup taken just before this restore, which is what its undo puts back. */
        public String undoBackup;

        /** When it was started, in milliseconds since 1970. */
        public long made;

        /** Set once the restore is being undone: the chunks waiting are then to have their records taken out. */
        public boolean undoing;

        /** Chunks still to be done when they next load. */
        public final Set<WearMirror.Spot> waiting = new LinkedHashSet<WearMirror.Spot>();

        /** The records put into each chunk, for the undo. */
        public final Map<WearMirror.Spot, int[]> filled = new LinkedHashMap<WearMirror.Spot, int[]>();

        public Run(String source, String undoBackup, long made) {
            this.source = source;
            this.undoBackup = undoBackup;
            this.made = made;
        }

        /** Notes what went into a chunk, adding to what went in before. */
        public void filledInto(WearMirror.Spot spot, int[] keys) {
            waiting.remove(spot);
            if (keys == null || keys.length == 0) return;
            int[] before = filled.get(spot);
            if (before == null) {
                filled.put(spot, keys.clone());
                return;
            }
            int[] both = new int[before.length + keys.length];
            System.arraycopy(before, 0, both, 0, before.length);
            System.arraycopy(keys, 0, both, before.length, keys.length);
            filled.put(spot, both);
        }

        /** Turns this restore into its undo: every chunk anything went into waits to have it taken out. */
        public void undo() {
            undoing = true;
            waiting.clear();
            waiting.addAll(filled.keySet());
        }

        /** Notes that a chunk's records have been taken out again. */
        public void undoneIn(WearMirror.Spot spot) {
            waiting.remove(spot);
            filled.remove(spot);
        }

        /** Whether there is nothing left for this run to do or to undo. */
        public boolean spent() {
            return undoing ? waiting.isEmpty() : waiting.isEmpty() && filled.isEmpty();
        }

        /** How many records it has put in so far, across every chunk. */
        public int recordsFilled() {
            int total = 0;
            for (int[] keys : filled.values()) total += keys.length;
            return total;
        }

        public byte[] write() {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeByte(1);
                out.writeUTF(source == null ? "" : source);
                out.writeUTF(undoBackup == null ? "" : undoBackup);
                out.writeLong(made);
                out.writeBoolean(undoing);
                out.writeInt(waiting.size());
                for (WearMirror.Spot spot : waiting) out.writeUTF(spot.toString());
                out.writeInt(filled.size());
                for (Map.Entry<WearMirror.Spot, int[]> each : filled.entrySet()) {
                    out.writeUTF(
                        each.getKey()
                            .toString());
                    out.writeInt(each.getValue().length);
                    for (int key : each.getValue()) out.writeInt(key);
                }
                out.flush();
                return bytes.toByteArray();
            } catch (IOException never) {
                throw new IllegalStateException(never);
            }
        }

        /** A run from {@link #write}'s bytes, or null for anything else. */
        public static Run read(byte[] bytes) {
            if (bytes == null) return null;
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
                if (in.readByte() != 1) return null;
                Run run = new Run(in.readUTF(), in.readUTF(), in.readLong());
                run.undoing = in.readBoolean();
                int waiting = in.readInt();
                for (int i = 0; i < waiting; i++) {
                    WearMirror.Spot spot = WearMirror.Spot.parse(in.readUTF());
                    if (spot != null) run.waiting.add(spot);
                }
                int filled = in.readInt();
                for (int i = 0; i < filled; i++) {
                    WearMirror.Spot spot = WearMirror.Spot.parse(in.readUTF());
                    int count = in.readInt();
                    if (count < 0 || count > 1 << 20) return null;
                    int[] keys = new int[count];
                    for (int k = 0; k < count; k++) keys[k] = in.readInt();
                    if (spot != null) run.filled.put(spot, keys);
                }
                return run;
            } catch (IOException damaged) {
                return null;
            }
        }
    }
}
