package com.trmtgtnh.original;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The original mod's chunks, converted as they are read: this edition's adapter between a saved chunk's
 * {@code CompoundTag} and the portable {@link OriginalWorld} (docs/goals/0.9.229-w5-spec.md, 2.9; spike S1, 2.10).
 *
 * <p>
 * <strong>Where it is called.</strong> From 1.21.2 a chunk is read in two steps, so here it is one line in a new
 * {@code HEAD} hook on {@code SerializableChunkData.parse}, before vanilla decodes the palette: it is seen there with
 * the original's {@code stage} property still in it, which no registry remap or alias keeps (0.9.229-found.md, W5
 * finding 5). {@code parse} has no level and the rewrite needs none, as it reads only the tag; the squares ride on the
 * parsed record beside the wear's bytes and are handed to the store in the existing {@code read} hook, where the level
 * is known. That runs off the server thread, so this does no more than rewrite the tag and list the worn squares; they
 * become records on the server thread when the chunk is promoted ({@link #place}, called by the store).
 *
 * <p>
 * <strong>The spike's lines.</strong> On the spike branch every converted palette entry is logged with its properties
 * as read, so S1 can see the {@code stage} arrive intact (point 2), and each converted chunk with its cost, and the fast
 * path's cost every {@link #FAST_REPORT} chunks (point 8).
 */
public final class OriginalChunk {

    /** Spec 2.9's {@code conversion.enabled}, on by default, set from the file by {@code TrmtConfig.readConversion}. */
    public static volatile boolean enabled = true;

    /**
     * Spec 2.9's {@code conversion.dirtBecomesGrass}, on by default (question 3), set from the file by
     * {@code TrmtConfig.readConversion}.
     */
    public static volatile boolean dirtBecomesGrass = true;

    /** The fast path is reported once every this many chunks. */
    static final int FAST_REPORT = 4096;

    private static final AtomicBoolean saidInstalled = new AtomicBoolean();

    private static final Set<String> saidUnknown = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /** The chunk tag's mark that its stored light is correct, as the game's chunk reader names it. */
    static final String LIGHT_ON = "isLightOn";

    /** A section's stored block light and sky light, as the game's chunk reader names them. */
    static final String BLOCK_LIGHT = "BlockLight";

    static final String SKY_LIGHT = "SkyLight";

    private static final AtomicLong fastChunks = new AtomicLong();

    private static final AtomicLong fastNanos = new AtomicLong();

    private static final AtomicLong convertedChunks = new AtomicLong();

    private static final AtomicLong convertedSquares = new AtomicLong();

    /**
     * Chunks and squares converted and not yet taken into the save's record by the server thread
     * ({@code OriginalConversionData.record}): counted here, off it, where saved data must not be touched.
     */
    private static final AtomicLong unrecordedChunks = new AtomicLong();

    private static final AtomicLong unrecordedSquares = new AtomicLong();

    /** Whether the last {@link #convert} on this thread changed its tag, for the read hook's return to mark the chunk. */
    private static final ThreadLocal<Boolean> changedHere = new ThreadLocal<Boolean>();

    /** The squares of the last {@link #convert} on this thread that changed its tag, carried from parse to read. */
    private static final ThreadLocal<List<OriginalWorld.Square>> convertedHere = new ThreadLocal<List<OriginalWorld.Square>>();

    /** How a record's first threshold is drawn; the engine's own draw in the game. */
    public interface Thresholds {

        float at(SurfaceFamily base, int index);
    }

    private OriginalChunk() {}

    /**
     * The read hook's one call: rewrites the original's palette entries in this chunk tag in place and returns the worn
     * squares, or an empty list. A chunk naming nothing of the original's is left exactly as it was read.
     */
    public static List<OriginalWorld.Square> convert(int chunkX, int chunkZ, CompoundTag tag) {
        changedHere.set(Boolean.FALSE);
        convertedHere.remove();
        if (tag == null || !enabled) return Collections.emptyList();
        long start = System.nanoTime();
        if (!mentionsOriginal(tag)) {
            fast(System.nanoTime() - start);
            return Collections.emptyList();
        }
        if (ModsPresent.has("trmt")) {
            if (saidInstalled.compareAndSet(false, true)) Trmt.LOG.info(
                "This world holds blocks of The Roads More Travelled, which is installed, so they are left to it.");
            return Collections.emptyList();
        }

        OriginalWorld.Result result = rewrite(chunkX, chunkZ, tag, dirtBecomesGrass);
        long micros = (System.nanoTime() - start) / 1000L;
        for (String name : result.unknown) {
            if (saidUnknown.add(name)) Trmt.LOG.warn("Left {} as it was: The Roads More Travelled had no such block to convert.", name);
        }
        if (!result.changed) return Collections.emptyList();

        // The light the chunk was saved with is the original's: its sunk sand was no whole block and let the sky in,
        // and the sand that replaces it is opaque, so a converted chunk kept that light inside whole blocks (R1, point
        // 7). The chunk is handed to the game as one whose light was never worked out: marked unlit, and with no light
        // stored in any section. The mark alone is not enough (R1 again, 2026-11-02, 46 squares still lit inside): the
        // game's chunk reader queues every section's stored BlockLight and SkyLight whatever the mark says, and the
        // light step of an unlit chunk only propagates from the light's sources, which raises light and never lowers
        // what is stored inside a block that has turned opaque. With nothing stored the light step works the chunk out
        // from its sources alone, as it does for a chunk generated or upgraded with no light.
        tag.putBoolean(LIGHT_ON, false);
        forgetLight(tag);
        changedHere.set(Boolean.TRUE);
        convertedHere.set(result.squares);
        // Counted for the save's record, which the server thread keeps and which says the first conversion in the log.
        unrecordedChunks.incrementAndGet();
        unrecordedSquares.addAndGet(result.squares.size());
        long chunks = convertedChunks.incrementAndGet();
        long squaresSoFar = convertedSquares.addAndGet(result.squares.size());
        // The [S1] lines are debug from 1.0.1 (D21): one for every chunk and palette entry converted is the
        // harness's to read (its original part raises this logger first), not a line in a player's log.
        Trmt.LOG.debug("[S1] Converted chunk {},{}: {} worn squares, {} us; {} chunks and {} squares so far.", chunkX, chunkZ,
            result.squares.size(), micros, chunks, squaresSoFar);
        return result.squares;
    }

    /** Whether the last {@link #convert} on this thread changed its chunk, cleared as it is asked. */
    public static boolean takeChanged() {
        Boolean changed = changedHere.get();
        changedHere.set(Boolean.FALSE);
        return changed != null && changed.booleanValue();
    }

    /**
     * The squares of the last {@link #convert} on this thread that changed its chunk, or null when it changed nothing;
     * cleared as it is asked. The parse hook's return puts them on the parsed record, which is how a converted chunk is
     * told from one that was not when the record is read into a chunk.
     */
    public static List<OriginalWorld.Square> takeConverted() {
        List<OriginalWorld.Square> squares = convertedHere.get();
        convertedHere.remove();
        changedHere.set(Boolean.FALSE);
        return squares;
    }

    /**
     * How many chunks this game has converted so far, for the S1 harness part (point 5): a world opened again after
     * its converted chunks were saved must convert none.
     */
    public static long chunksConverted() {
        return convertedChunks.get();
    }

    /**
     * The fast path's count and mean cost so far, said by the S1 harness part at its end (point 8): FAST_REPORT is
     * more chunks than one run of it loads, so the periodic line alone would say nothing.
     */
    public static String fastPathSoFar() {
        long chunks = fastChunks.get();
        long micros = chunks == 0 ? 0 : fastNanos.get() / 1000L / chunks;
        return chunks + " chunks, " + micros + " us each on average";
    }

    /**
     * The chunks and squares converted since this was last asked, as {chunks, squares}, and none left counted: the
     * server thread's one taker, which puts them into the save's record.
     */
    public static long[] takeUnrecorded() {
        return new long[] { unrecordedChunks.getAndSet(0L), unrecordedSquares.getAndSet(0L) };
    }

    /**
     * Takes every section's stored light out of a chunk tag, under the names the game's chunk reader asks for, so the
     * reader queues none of it (see {@link #convert}).
     */
    static void forgetLight(CompoundTag tag) {
        ListTag sections = tag.getListOrEmpty("sections");
        for (int s = 0; s < sections.size(); s++) {
            CompoundTag section = sections.getCompoundOrEmpty(s);
            section.remove(BLOCK_LIGHT);
            section.remove(SKY_LIGHT);
        }
    }

    private static void fast(long nanos) {
        long chunks = fastChunks.incrementAndGet();
        long total = fastNanos.addAndGet(nanos);
        if (chunks % FAST_REPORT == 0) {
            Trmt.LOG.debug("[S1] Conversion fast path: {} chunks, {} us each on average.", chunks, total / 1000L / chunks);
        }
    }

    /** The fast path's question, asked of the tag's palettes directly without building the model. */
    static boolean mentionsOriginal(CompoundTag tag) {
        ListTag sections = tag.getListOrEmpty("sections");
        for (int s = 0; s < sections.size(); s++) {
            CompoundTag states = sections.getCompoundOrEmpty(s)
                .getCompoundOrEmpty("block_states");
            ListTag palette = states.getListOrEmpty("palette");
            for (int p = 0; p < palette.size(); p++) {
                if (palette.getCompoundOrEmpty(p)
                    .getStringOr("Name", "")
                    .startsWith(OriginalWorld.NAMESPACE)) return true;
            }
        }
        return false;
    }

    /**
     * Reads the sections into the portable model, converts them, and writes back each entry the conversion replaced.
     * Only replaced entries are written; the packed indices are never touched.
     */
    static OriginalWorld.Result rewrite(int chunkX, int chunkZ, CompoundTag tag, boolean dirtBecomesGrass) {
        ListTag sectionTags = tag.getListOrEmpty("sections");
        List<OriginalWorld.Section> sections = new ArrayList<OriginalWorld.Section>();
        List<ListTag> palettes = new ArrayList<ListTag>();
        for (int s = 0; s < sectionTags.size(); s++) {
            CompoundTag section = sectionTags.getCompoundOrEmpty(s);
            CompoundTag states = section.getCompoundOrEmpty("block_states");
            ListTag paletteTag = states.getListOrEmpty("palette");
            List<OriginalWorld.Entry> palette = new ArrayList<OriginalWorld.Entry>();
            for (int p = 0; p < paletteTag.size(); p++) {
                CompoundTag entry = paletteTag.getCompoundOrEmpty(p);
                Map<String, String> properties = new LinkedHashMap<String, String>();
                CompoundTag held = entry.getCompoundOrEmpty("Properties");
                for (String key : held.keySet()) {
                    held.getString(key).ifPresent(value -> properties.put(key, value));
                }
                palette.add(new OriginalWorld.Entry(entry.getStringOr("Name", ""), properties));
            }
            long[] data = states.getLongArray("data").orElse(null);
            sections.add(new OriginalWorld.Section(section.getByteOr("Y", (byte) 0), palette, data));
            palettes.add(paletteTag);
        }

        // Read before the rewrite replaces them: what each original entry said, for the spike's log (point 2).
        List<String> before = new ArrayList<String>();
        for (OriginalWorld.Section section : sections) {
            for (OriginalWorld.Entry entry : section.palette) before.add(entry.name + entry.properties);
        }

        OriginalWorld.Result result = OriginalWorld.convert(sections, dirtBecomesGrass);
        if (!result.changed) return result;

        int said = 0;
        for (int s = 0; s < sections.size(); s++) {
            OriginalWorld.Section section = sections.get(s);
            ListTag paletteTag = palettes.get(s);
            for (int p = 0; p < section.palette.size(); p++) {
                String was = before.get(said++);
                if (!section.replaced(p)) continue;
                OriginalWorld.Entry entry = section.palette.get(p);
                CompoundTag written = paletteTag.getCompoundOrEmpty(p);
                written.putString("Name", entry.name);
                if (entry.properties.isEmpty()) {
                    written.remove("Properties");
                } else {
                    CompoundTag properties = new CompoundTag();
                    for (Map.Entry<String, String> property : entry.properties.entrySet()) {
                        properties.putString(property.getKey(), property.getValue());
                    }
                    written.put("Properties", properties);
                }
                // A palette entry's few properties, made one bounded string here rather than handed to the log whole.
                String became = entry.name + entry.properties;
                Trmt.LOG.debug("[S1] Chunk {},{} section {} palette {}: {} became {}", chunkX, chunkZ, section.y, p, was,
                    became);
            }
        }
        return result;
    }

    /**
     * Turns converted squares into records in a chunk's wear, on the server thread, against the chains in force now
     * (spec 2.9, "The record"). A record already at a square keeps whichever of the two is further worn. Returns how
     * many records were written.
     */
    public static int place(ChunkErosionData data, List<OriginalWorld.Square> squares, boolean faithful, int nowSeconds,
        Thresholds thresholds) {
        if (data == null || squares == null) return 0;
        int placed = 0;
        for (OriginalWorld.Square square : squares) {
            SurfaceFamily base = square.base;
            int index = OriginalGradation.index(base, square.step, faithful, ErosionChain.forBase(base));
            if (index < 0) continue;
            SurfaceFamily appearance = ErosionChain.familyAt(base, index);
            if (appearance == null) continue;
            int stage = ErosionChain.stageAt(base, index);
            int sink = ErosionChain.sinkAt(base, index);

            int key = ErosionKey.pack(square.x, square.y, square.z);
            ErosionEntry entry = data.get(key);
            if (entry != null && entry.isVisible()
                && ErosionChain.indexOf(base, entry.getFamily(), entry.getStage(), entry.getSink()) >= index) continue;

            float threshold = thresholds == null ? ErosionEntry.UNDRAWN_THRESHOLD : thresholds.at(base, index);
            if (entry == null) {
                entry = new ErosionEntry(base, threshold, nowSeconds);
                data.put(key, entry);
            }
            entry.setAppearance(appearance, stage, threshold);
            entry.setSink(sink);
            entry.setLastTouchedSeconds(nowSeconds);
            data.markDirty();
            placed++;
        }
        return placed;
    }
}
