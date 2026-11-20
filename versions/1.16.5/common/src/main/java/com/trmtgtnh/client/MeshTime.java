package com.trmtgtnh.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * GF1's measure, the meshtime part's clock (0.9.229; docs/goals/0.9.228-step6-spec.md section 13; Xep, 2026-11-08, on
 * an unsunk worn square hiding its neighbours' faces: "This update"). The hide is kept only if it measures cheaper,
 * and "before any code, the harness gains a measure": the time to rebuild every section of the second yard, N times,
 * and the faces the renderer emitted where it can say, logged per renderer with the hide off and on, on the same scene.
 * This is that measure and nothing of the hide: {@link #HIDE} is the hide's switch, read here for the line alone.
 *
 * <p>
 * <strong>How a rebuild is timed.</strong> Every section is marked dirty at one frame, through the level renderer's own
 * {@code setSectionDirty}, which Sodium, Embeddium and Canvas take over for their own sections; then each frame asks
 * whether the renderer is done, and the round's time runs from the mark to the first of {@link #DONE_FRAMES} frames
 * in a row that said so. "Done" is the renderer's own answer that it has nothing left to build
 * ({@code hasRenderedAllChunks}, which those renderers answer from their own queues; OptiFine's own count of chunks to
 * update), and under vanilla and OptiFine also every section's own compiled result replaced since the mark - the
 * queue alone is empty for a moment while a worker still holds the last task. The frames are this part's own: a
 * screen that draws nothing ({@link Clock}) is open while it runs, so the clock is the same in every loader.
 *
 * <p>
 * <strong>Never a hang, never a pass it did not earn.</strong> A round that has not finished in {@link #CAP_NANOS}, or
 * a run in which no frame is drawn for {@link #NO_FRAME_NANOS}, ends the part with a FAIL naming it; OptiFine that
 * cannot be asked its count says it cannot be measured, rather than passing on an answer it never gave.
 */
public final class MeshTime {

    /**
     * The switch the hide is read from: on unless {@code -Dtrmt.gf1.hide=false} (Xep, 2026-11-09: "Keep, vanilla
     * only"). The hide itself ({@code client.render.GhostHide.ON}) reads the same property, and this part's line says
     * whether the hide answers in this game - the switch on and vanilla's mesher drawing - so the two runs are told
     * apart by it, and a seat the hide never reaches says off.
     */
    public static final String HIDE = "trmt.gf1.hide";

    /**
     * How many rebuilds are timed: twenty, so the median and the 90th percentile are each a rank among twenty and one
     * slow round (a garbage collection, the server saving) moves them by one place rather than by its own length.
     */
    static final int ROUNDS = 20;

    /**
     * How many rebuilds go first untimed: three. The first finds the sections this camera's renderer rebuilds at all;
     * over the other two the code paths are compiled and the renderer's buffers grown, as they are for every later one.
     */
    static final int WARM_UP = 3;

    /**
     * The longest one rebuild may take before the part says FAIL: twenty seconds. A yard's sections rebuild in well
     * under one; a rebuild still going after twenty is one that will not finish, never a slow one.
     */
    static final long CAP_NANOS = 20_000_000_000L;

    /** How long the part may go without a frame before it says FAIL: ten seconds. */
    static final long NO_FRAME_NANOS = 10_000_000_000L;

    /**
     * How many frames pass after the mark before the renderer is asked: two. The renderer takes up what was marked in
     * the frame after the mark, so its queue is still empty for that one.
     */
    static final int FRAMES_FIRST = 2;

    /** How many frames in a row must say done before a round is: three (see the class's note on the queue). */
    static final int DONE_FRAMES = 3;

    /**
     * How long after its mark the first round waits before it is judged: two seconds. That round finds which sections
     * the renderer rebuilds from this camera, so it must see every rebuild the mark set off land; judged on the queue
     * alone it was judged before the renderer had taken any up (dev 1.21.1 Fabric, 2026-11-09: every section left out,
     * and 67 of 89 kept on the next run where NeoForge kept 89). A round takes 0.07 to 0.23 seconds here.
     */
    static final long SETTLE_NANOS = 2_000_000_000L;

    /**
     * How much of the yard the first round must keep, in tenths: nine. The first round chooses the sections every timed
     * round waits on; under load on dev 1.21.1 Fabric (2026-11-09) it kept 4 of 89 and timed those 4, a PASS that
     * measured a twentieth of the yard. Fewer than nine tenths kept is a FAIL, and the sweep plays the seat again.
     */
    static final int KEPT_TENTHS = 9;

    enum Stage {
        IDLE,
        TIMING,
        DONE,
        FAILED
    }

    private static volatile Stage stage = Stage.IDLE;

    /** The sections measured, packed as SectionPos longs. */
    private static long[] keys = new long[0];

    /** Vanilla's own section objects for those keys, and the compiled result each held at the mark; null otherwise. */
    private static Object[] held;

    private static Object[] heldCompiled;

    /** Whether done is also asked of each section's own compiled result (vanilla and OptiFine). */
    private static boolean perSection;

    private static boolean optiFine;

    private static int round;

    private static long markedAt;

    private static int framesSince;

    private static int doneFrames;

    private static long firstDoneAt;

    private static volatile long lastFrameAt;

    private static final long[] TIMED = new long[ROUNDS];

    private static int sectionsLeftOut;

    private static volatile String failure;

    private MeshTime() {}

    /** "on" or "off", as the line says it: whether the hide answers here, its switch on under vanilla's mesher (see HIDE). */
    public static String hide() {
        boolean on = com.trmtgtnh.client.render.GhostHide.switchedOn(System.getProperty(HIDE))
            && com.trmtgtnh.client.render.GhostHide.forSeat(seat());
        return on ? "on" : "off";
    }

    /** The measure's line, in the words the sweep and the hide's decision read. */
    static String line(String seat, String hide, int sections, int rounds, double medianMs, double p90Ms, String faces) {
        return String.format(
            Locale.ROOT,
            "Spike walk: meshtime - renderer=%s hide=%s sections=%d rounds=%d median=%.1f p90=%.1f faces=%s",
            seat,
            hide,
            Integer.valueOf(sections),
            Integer.valueOf(rounds),
            Double.valueOf(medianMs),
            Double.valueOf(p90Ms),
            faces);
    }

    /** The median of the first {@code count} times, in milliseconds: the mean of the middle two for an even count. */
    static double medianMs(long[] nanos, int count) {
        long[] sorted = Arrays.copyOf(nanos, count);
        Arrays.sort(sorted);
        if (count == 0) return 0D;
        if (count % 2 == 1) return sorted[count / 2] / 1e6D;
        return (sorted[count / 2 - 1] + sorted[count / 2]) / 2e6D;
    }

    /** The 90th percentile by the nearest rank, in milliseconds: the smallest time at least nine tenths are no slower than. */
    static double p90Ms(long[] nanos, int count) {
        long[] sorted = Arrays.copyOf(nanos, count);
        Arrays.sort(sorted);
        if (count == 0) return 0D;
        int rank = (int) Math.ceil(0.9D * count);
        return sorted[Math.max(0, rank - 1)] / 1e6D;
    }

    /**
     * Which renderer this game draws its terrain with, as the line names it: vanilla, or the mods that take it over.
     * GF1's hide answers only where this says vanilla ({@code client.render.GhostHide.forSeat}).
     */
    public static String seat() {
        StringBuilder said = new StringBuilder();
        if (optiFineHere()) said.append("optifine");
        else if (com.trmtgtnh.ModsPresent.has("canvas")) said.append("canvas");
        else if (com.trmtgtnh.ModsPresent.has("embeddium")) said.append("embeddium");
        else if (com.trmtgtnh.ModsPresent.has("rubidium")) said.append("rubidium");
        else if (com.trmtgtnh.ModsPresent.has("sodium")) said.append("sodium");
        else said.append("vanilla");
        if (com.trmtgtnh.ModsPresent.has("oculus")) said.append("+oculus");
        else if (com.trmtgtnh.ModsPresent.has("iris")) said.append("+iris");
        return said.toString();
    }

    /** Whether the renderer is vanilla's own sections, asked of each: vanilla and OptiFine, which keeps them. */
    private static boolean vanillaSections(String seat) {
        return seat.startsWith("vanilla") || seat.startsWith("optifine");
    }

    /** Why this renderer cannot be measured, or null when it can. */
    static String unmeasurable(Minecraft client) {
        if (optiFineHere() && optiFineCount(client) == null)
            return "OptiFine is here and its own count of chunks to update cannot be asked, so a rebuild's end cannot be seen";
        return null;
    }

    /** Begins the rounds over these sections; the frames do the rest. */
    static void start(Minecraft client, long[] sections) {
        keys = sections.clone();
        String seat = seat();
        optiFine = optiFineHere();
        perSection = false;
        held = null;
        heldCompiled = null;
        if (vanillaSections(seat)) {
            Object[] found = vanillaSectionsFor(client, keys);
            if (found != null) {
                held = found;
                heldCompiled = new Object[keys.length];
                perSection = true;
            }
        }
        round = 0;
        markedAt = 0L;
        sectionsLeftOut = 0;
        failure = null;
        lastFrameAt = System.nanoTime();
        stage = Stage.TIMING;
    }

    static Stage stage() {
        return stage;
    }

    static String failure() {
        return failure;
    }

    static int sections() {
        return keys.length;
    }

    static int leftOut() {
        return sectionsLeftOut;
    }

    static boolean askedOfEachSection() {
        return perSection;
    }

    static long[] timed() {
        return TIMED.clone();
    }

    /** Ends the rounds where they stand, as the part's own watch does when no frame comes. */
    static void stop(String why) {
        if (stage != Stage.TIMING) return;
        failure = why;
        stage = Stage.FAILED;
    }

    /** The part's own watch, from the client tick: no frame for NO_FRAME_NANOS is a FAIL, never a wait. */
    static void watch() {
        if (stage == Stage.TIMING && System.nanoTime() - lastFrameAt > NO_FRAME_NANOS)
            stop("no frame was drawn for " + NO_FRAME_NANOS / 1_000_000_000L + " seconds while a rebuild was timed");
    }

    /** One frame, from the Clock: marks a round, or asks whether the one marked is done. */
    static void frame() {
        if (stage != Stage.TIMING) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.levelRenderer == null) {
            stop("the world went away while a rebuild was timed");
            return;
        }
        long now = System.nanoTime();
        lastFrameAt = now;
        if (markedAt == 0L) {
            mark(client);
            markedAt = now;
            framesSince = 0;
            doneFrames = 0;
            return;
        }
        framesSince++;
        if (framesSince >= FRAMES_FIRST && built(client)) {
            if (doneFrames++ == 0) firstDoneAt = now;
            if (roundJudged(round, perSection, doneFrames, now - markedAt)) finishRound(firstDoneAt - markedAt);
            return;
        }
        doneFrames = 0;
        if (now - markedAt > CAP_NANOS) {
            stop("round " + (round + 1) + " of " + (WARM_UP + ROUNDS) + " had not finished building its "
                + keys.length + " sections after " + CAP_NANOS / 1_000_000_000L + " seconds");
        }
    }

    /**
     * Whether a round that has said done for doneFrames frames is over: DONE_FRAMES of them, and for the first round of
     * a renderer asked section by section, SETTLE_NANOS since the mark too (see SETTLE_NANOS).
     */
    static boolean roundJudged(int round, boolean perSection, int doneFrames, long sinceMark) {
        if (doneFrames < DONE_FRAMES) return false;
        return round > 0 || !perSection || sinceMark >= SETTLE_NANOS;
    }

    /** Whether a first round that kept kept of total sections holds enough of the yard: KEPT_TENTHS tenths of it. */
    static boolean keptEnough(int kept, int total) {
        return total > 0 && kept * 10L >= total * (long) KEPT_TENTHS;
    }

    private static void finishRound(long took) {
        if (round == 0 && perSection) {
            // The first round finds which sections this camera's renderer rebuilds: a section it never takes up (out
            // of its view, or empty to it) is left out, so every later round waits only on what is rebuilt.
            int kept = 0;
            for (int i = 0; i < keys.length; i++) {
                if (compiledOf(held[i]) != heldCompiled[i]) {
                    keys[kept] = keys[i];
                    held[kept] = held[i];
                    kept++;
                }
            }
            sectionsLeftOut = keys.length - kept;
            keys = Arrays.copyOf(keys, kept);
            held = Arrays.copyOf(held, kept);
            heldCompiled = new Object[kept];
            if (!keptEnough(kept, kept + sectionsLeftOut)) {
                stop(kept == 0
                    ? "the renderer rebuilt none of the " + sectionsLeftOut + " sections from this camera"
                    : "the renderer rebuilt only " + kept + " of the " + (kept + sectionsLeftOut)
                        + " sections from this camera, under the nine tenths a measure is compared on");
                return;
            }
        }
        if (round >= WARM_UP) TIMED[round - WARM_UP] = took;
        round++;
        markedAt = 0L;
        if (round >= WARM_UP + ROUNDS) stage = Stage.DONE;
    }

    private static void mark(Minecraft client) {
        for (int i = 0; i < keys.length; i++) {
            if (perSection) heldCompiled[i] = compiledOf(held[i]);
            client.levelRenderer.setSectionDirty(SectionPos.x(keys[i]), SectionPos.y(keys[i]), SectionPos.z(keys[i]));
        }
    }

    /** Whether the renderer has built everything marked: its own answer, and under vanilla each section's own. */
    private static boolean built(Minecraft client) {
        boolean queue;
        if (optiFine) {
            Integer left = optiFineLeft(client);
            queue = left != null && left.intValue() == 0;
        } else {
            queue = client.levelRenderer.hasRenderedAllChunks();
        }
        if (!queue) return false;
        if (!perSection || round == 0) return true;
        for (int i = 0; i < keys.length; i++) {
            if (compiledOf(held[i]) == heldCompiled[i]) return false;
        }
        return true;
    }

    // -- vanilla's own sections --

    private static Object compiledOf(Object section) {
        return ((ChunkRenderDispatcher.RenderChunk) section).getCompiledChunk();
    }

    /** The level renderer's view area, found by its type: a field's name differs between the loaders' names. */
    private static ViewArea viewArea(Minecraft client) {
        for (java.lang.reflect.Field field : net.minecraft.client.renderer.LevelRenderer.class.getDeclaredFields()) {
            if (field.getType() != ViewArea.class) continue;
            try {
                field.setAccessible(true);
                return (ViewArea) field.get(client.levelRenderer);
            } catch (ReflectiveOperationException | RuntimeException unreadable) {
                return null;
            }
        }
        return null;
    }

    /** Vanilla's section objects for these keys, in their order, or null where any is not there to ask. */
    private static Object[] vanillaSectionsFor(Minecraft client, long[] wanted) {
        ViewArea area = viewArea(client);
        if (area == null || area.chunks == null) return null;
        Map<Long, Object> byKey = new HashMap<Long, Object>();
        for (ChunkRenderDispatcher.RenderChunk each : area.chunks) {
            if (each == null) continue;
            BlockPos origin = each.getOrigin();
            byKey.put(Long.valueOf(SectionPos.asLong(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4)), each);
        }
        Object[] found = new Object[wanted.length];
        for (int i = 0; i < wanted.length; i++) {
            found[i] = byKey.get(Long.valueOf(wanted[i]));
            if (found[i] == null) return null;
        }
        return found;
    }

    private static java.lang.reflect.Field indexCount;

    private static boolean indexCountLooked;

    /**
     * The faces the renderer emitted for the measured sections, as the line says them: vanilla's own compiled buffers,
     * each layer's vertex count over four (four corners a face), or "not said" where the renderer is not vanilla's or
     * the count cannot be read under this game's names.
     */
    static String faces() {
        if (!perSection || held == null) return "not said";
        if (!indexCountLooked) {
            indexCountLooked = true;
            try {
                indexCount = com.mojang.blaze3d.vertex.VertexBuffer.class.getDeclaredField("vertexCount");
                indexCount.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException absent) {
                indexCount = null;
            }
        }
        if (indexCount == null) return "not said";
        long quads = 0L;
        try {
            for (Object each : held) {
                ChunkRenderDispatcher.RenderChunk section = (ChunkRenderDispatcher.RenderChunk) each;
                ChunkRenderDispatcher.CompiledChunk compiled = section.getCompiledChunk();
                for (RenderType layer : RenderType.chunkBufferLayers()) {
                    if (compiled.isEmpty(layer)) continue;
                    quads += indexCount.getInt(section.getBuffer(layer)) / 4;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return "not said";
        }
        return Long.toString(quads);
    }

    // -- the second yard's sections --

    /** How far the yard's sections are followed from its landing, in sections each way: eight, past any yard's edge. */
    static final int REACH = 8;

    /**
     * Every section of the second yard that holds a block: followed from the section under the landing through each
     * neighbouring section that holds one too, within REACH and a row either side of the landing's. The yards stand
     * forty blocks or more apart in open sky, so the following stops at this yard's edge. Null while a chunk on the way
     * is not yet in this client's world: asked of the chunk source, whose {@code hasChunk} is {@code getChunk(x, z,
     * FULL, false) != null}, which the client's chunk cache answers with null for a chunk it has not got (javap). Never
     * of the level, whose {@code hasChunk} answers true for every chunk (0.9.229 W6 T17; ClientHoldsTheChunkTest).
     */
    static long[] yardSections(ClientLevel level, double x, double feet, double z) {
        int fromX = net.minecraft.util.Mth.floor(x) >> 4;
        int fromY = (net.minecraft.util.Mth.floor(feet) - 1) >> 4;
        int fromZ = net.minecraft.util.Mth.floor(z) >> 4;
        int low = Math.max(com.trmtgtnh.util.Heights.bottom(level) >> 4, fromY - 1);
        int high = Math.min(com.trmtgtnh.util.Heights.top(level) >> 4, fromY + 1);
        java.util.LinkedHashSet<Long> kept = new java.util.LinkedHashSet<Long>();
        java.util.HashSet<Long> seen = new java.util.HashSet<Long>();
        java.util.ArrayDeque<long[]> next = new java.util.ArrayDeque<long[]>();
        next.add(new long[] { fromX, fromY, fromZ });
        seen.add(Long.valueOf(SectionPos.asLong(fromX, fromY, fromZ)));
        while (!next.isEmpty()) {
            long[] at = next.poll();
            int sx = (int) at[0];
            int sy = (int) at[1];
            int sz = (int) at[2];
            if (!level.getChunkSource()
                .hasChunk(sx, sz)) return null;
            if (!holdsABlock(level, sx, sy, sz)) continue;
            kept.add(Long.valueOf(SectionPos.asLong(sx, sy, sz)));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = sx + dx;
                        int ny = sy + dy;
                        int nz = sz + dz;
                        if (Math.abs(nx - fromX) > REACH || Math.abs(nz - fromZ) > REACH || ny < low || ny > high) continue;
                        if (seen.add(Long.valueOf(SectionPos.asLong(nx, ny, nz)))) next.add(new long[] { nx, ny, nz });
                    }
                }
            }
        }
        long[] out = new long[kept.size()];
        int i = 0;
        for (Long each : kept) out[i++] = each.longValue();
        return out;
    }

    private static boolean holdsABlock(ClientLevel level, int sx, int sy, int sz) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    at.set((sx << 4) + x, (sy << 4) + y, (sz << 4) + z);
                    if (!level.getBlockState(at)
                        .isAir()) return true;
                }
            }
        }
        return false;
    }

    // -- OptiFine, asked by reflection --

    private static Boolean optiFineThere;

    private static boolean optiFineHere() {
        if (optiFineThere == null) {
            boolean there;
            try {
                Class.forName("net.optifine.Config", false, MeshTime.class.getClassLoader());
                there = true;
            } catch (Throwable absent) {
                there = false;
            }
            optiFineThere = Boolean.valueOf(there);
        }
        return optiFineThere.booleanValue();
    }

    private static java.lang.reflect.Method optiFineCounted;

    private static boolean optiFineLooked;

    private static java.lang.reflect.Method optiFineCount(Minecraft client) {
        if (!optiFineLooked) {
            optiFineLooked = true;
            try {
                optiFineCounted = client.levelRenderer.getClass()
                    .getMethod("getCountChunksToUpdate");
            } catch (NoSuchMethodException | RuntimeException absent) {
                optiFineCounted = null;
            }
        }
        return optiFineCounted;
    }

    private static Integer optiFineLeft(Minecraft client) {
        java.lang.reflect.Method count = optiFineCount(client);
        if (count == null) return null;
        try {
            return Integer.valueOf(((Number) count.invoke(client.levelRenderer)).intValue());
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return null;
        }
    }

    // -- the frames --

    /**
     * The screen open while the rounds run: it draws nothing, so the world is drawn as it is without it, and each frame
     * it is drawn in is one frame of the clock. Not a pause screen, so the server keeps its pace.
     */
    static final class Clock extends net.minecraft.client.gui.screens.Screen {

        Clock() {
            super(new net.minecraft.network.chat.TextComponent(""));
        }

        @Override
        public void render(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partial) {
            frame();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }
}
