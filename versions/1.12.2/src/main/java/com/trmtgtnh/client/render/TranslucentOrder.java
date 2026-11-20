package com.trmtgtnh.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.trmtgtnh.Trmt;

/**
 * Keeps the blending pass of the chunks round the camera drawn in the order the camera needs (0.9.229, G17).
 *
 * <p>
 * This version draws its blending pass with depth writes off ({@code EntityRenderer.renderWorldPass}:
 * {@code depthMask(false)} before {@code renderBlockLayer(TRANSLUCENT)}), so where two of its faces cover the same
 * pixel the one drawn last is the one seen, and a chunk's faces are drawn in the order {@code
 * BufferBuilder.sortVertexData} last put them in: farthest from the camera first, measured from wherever the camera
 * was when the chunk was built or sorted. Vanilla sorts a chunk again only when the camera has moved more than a block
 * since it last did ({@code RenderGlobal.renderBlockLayer}), only for the first fifteen chunks of its list with that
 * pass started, and on a worker, through a task that a build already in hand turns away or is cancelled for
 * ({@code RenderChunk.makeCompileTaskTransparency}). A camera that then holds still keeps whatever order was handed to
 * the card last, and nothing looks at it again.
 *
 * <p>
 * A worn window is drawn in the blending pass (the 1.7.10 edition's twin answers render pass 1), and a sunk one is
 * hollow: it shares no pane, so it draws all four of its sides and its neighbours draw theirs against it. Those faces
 * stand inside the row, behind the tops beside them, and are only ever hidden by being drawn first. In the second
 * demonstrate yard's window row, photographed from the west, the west sides of sunk squares 4 to 7 came out over the
 * tops in front of them, dark, in some runs and not others (dev 1.12.2, the Rig and the Chisel target, 2026-11-02 to
 * 2026-11-04): the row's chunk was drawn in an order sorted from east of it, while the light under every one of those
 * faces counted full ({@code SpikeWalk.benchLight}).
 *
 * <p>
 * The 1.7.10 edition cannot draw it so: its {@code RenderGlobal.sortAndRender} sorts the twenty-seven nearest chunks
 * on the client thread, there and then, whenever the camera has moved more than a block, and builds a chunk on that
 * thread sorted from the camera as it stands. So here the order each chunk's blending pass was handed to the card in
 * is remembered, from where it was sorted ({@code MixinOrderSortedFrom}, {@code MixinOrderUploaded}), and every client
 * tick each of the twenty-seven chunks round the camera whose order was worked out more than a block from where the
 * camera is, with no build or sort of it in hand, is sorted again through the game's own call. The newer editions
 * draw their blending pass with depth writes on, so a face behind another is never drawn over it however it is sorted.
 *
 * <p>
 * The rule is kept apart from the game so it can be tested with plain stand-ins; {@link #keepRound} is the one place
 * that reads the renderer, by reflection, and stands aside for the session if it cannot.
 */
public final class TranslucentOrder {

    /**
     * How far, squared, the camera may be from where a chunk's order was worked out before it is sorted again: a
     * block, vanilla's own measure in {@code RenderGlobal.renderBlockLayer} and the 1.7.10 edition's.
     */
    public static final double STALE_SQ = 1.0D;

    /**
     * How many chunks either way of the camera's own are kept: one, the twenty-seven nearest, which is how many the
     * 1.7.10 edition's {@code RenderGlobal.sortAndRender} sorts again.
     */
    public static final int REACH = 1;

    /** One chunk of the renderer, as this rule sees it. */
    public interface Section {

        /** Its corner, in blocks. */
        int originX();

        int originY();

        int originZ();

        /**
         * Where the camera stood when the blending pass the card now draws for it was sorted, or null where it draws
         * none or that is not known.
         */
        float[] drawnFrom();

        /** Whether a build or a sort of it is handed out and not yet done. */
        boolean inHand();

        /** Asks the game to sort its blending pass again, from wherever the camera is when that is done. */
        void sortAgain();
    }

    /** Where a buffer's blending pass was last sorted from, as {@code MixinOrderSortedFrom} gives it. */
    public interface SortedFrom {

        /** Where it was sorted from since it was last begun, or null. */
        float[] trmt$sortedFrom();
    }

    private static final TranslucentOrder INSTANCE = new TranslucentOrder();

    /**
     * Each chunk's order as last handed to the card, by its renderer object: the client thread writes it as it uploads
     * and reads it each tick, so a plain map, let go of with the renderer.
     */
    private final Map<Object, float[]> drawn = new WeakHashMap<Object, float[]>();

    /** How many sorts this has asked for over the session: the harness says it, so a run can tell it ran. */
    private long asked;

    TranslucentOrder() {}

    public static TranslucentOrder get() {
        return INSTANCE;
    }

    /** Remembers the order a chunk's blending pass was handed to the card in. Client thread. */
    public void uploaded(Object section, float[] sortedFrom) {
        if (section == null) return;
        if (sortedFrom == null) drawn.remove(section);
        else drawn.put(section, sortedFrom.clone());
    }

    /** Where a chunk's blending pass now drawn was sorted from, or null. */
    public float[] drawnFrom(Object section) {
        return section == null ? null : drawn.get(section);
    }

    /** Whether a chunk's corner lies within {@link #REACH} chunks of the chunk the camera is in. */
    public static boolean near(int originX, int originY, int originZ, double x, double y, double z) {
        return Math.abs((originX >> 4) - (floor(x) >> 4)) <= REACH && Math.abs((originY >> 4) - (floor(y) >> 4)) <= REACH
            && Math.abs((originZ >> 4) - (floor(z) >> 4)) <= REACH;
    }

    /** Whether an order worked out from {@code from} is wrong for a camera at x, y, z. */
    public static boolean stale(float[] from, double x, double y, double z) {
        if (from == null || from.length < 3) return false;
        double dx = from[0] - x;
        double dy = from[1] - y;
        double dz = from[2] - z;
        return dx * dx + dy * dy + dz * dz > STALE_SQ;
    }

    /**
     * Asks every chunk near the camera whose blending pass is drawn in an order worked out more than a block away, and
     * that has nothing in hand, to be sorted again, and says how many it asked. A chunk with a build or a sort in hand
     * is left to it: asking would cancel one being done and start it again, and its own sort is taken from the camera
     * as it is when it runs; if that is still wrong, the next tick asks.
     */
    public int keep(Iterable<? extends Section> sections, double x, double y, double z) {
        int now = 0;
        for (Section each : sections) {
            if (!near(each.originX(), each.originY(), each.originZ(), x, y, z)) continue;
            if (!stale(each.drawnFrom(), x, y, z) || each.inHand()) continue;
            each.sortAgain();
            now++;
        }
        asked += now;
        return now;
    }

    /** How many chunks near the camera are drawn in an order worked out more than a block away, in hand or not. */
    public static int staleNear(Iterable<? extends Section> sections, double x, double y, double z) {
        int stale = 0;
        for (Section each : sections) {
            if (near(each.originX(), each.originY(), each.originZ(), x, y, z) && stale(each.drawnFrom(), x, y, z)) {
                stale++;
            }
        }
        return stale;
    }

    /** How many sorts this has asked for over the session. */
    public long asked() {
        return asked;
    }

    /** Forgets every chunk's order, as when the renderer is made again. */
    public void clear() {
        drawn.clear();
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    // ------------------------------------------------------------------
    // The game's side, read by reflection: RenderGlobal's chunks in view, each one's chunk, and its build in hand
    // ------------------------------------------------------------------

    private static java.lang.reflect.Field inView;

    private static java.lang.reflect.Field chunkInView;

    private static java.lang.reflect.Field buildInHand;

    private static java.lang.reflect.Field dispatcher;

    private static boolean unreadable;

    /** Whether the upload hook has been seen to run, said once: the proof the order is being read at all. */
    private static boolean uploadSeen;

    /**
     * Whether the order handed to the card is being followed here: false where a renderer hands chunks to the card by
     * a road of its own that the upload hook does not stand on, and then nothing here acts.
     */
    public static boolean followed() {
        return uploadSeen;
    }

    /** Notes, once, that the card's copy of a blending pass is being followed. */
    public static void uploadSeen() {
        if (uploadSeen) return;
        uploadSeen = true;
        Trmt.LOG.info("Blending order: each chunk's order is followed as it is handed to the card");
    }

    /** The chunks the renderer draws this frame within reach of the camera, as sections, or none if unreadable. */
    public static List<Section> sectionsNear(net.minecraft.client.Minecraft mc, double x, double y, double z) {
        List<Section> out = new ArrayList<Section>();
        if (unreadable || mc.renderGlobal == null) return out;
        try {
            if (inView == null) {
                chunkInView = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                    Class.forName("net.minecraft.client.renderer.RenderGlobal$ContainerLocalRenderInformation"),
                    "renderChunk",
                    "field_178036_a");
                buildInHand = net.minecraftforge.fml.relauncher.ReflectionHelper
                    .findField(net.minecraft.client.renderer.chunk.RenderChunk.class, "compileTask", "field_178599_i");
                dispatcher = net.minecraftforge.fml.relauncher.ReflectionHelper
                    .findField(net.minecraft.client.renderer.RenderGlobal.class, "renderDispatcher", "field_174995_M");
                // Last, so a lookup that failed above is never half kept.
                inView = net.minecraftforge.fml.relauncher.ReflectionHelper
                    .findField(net.minecraft.client.renderer.RenderGlobal.class, "renderInfos", "field_72755_R");
            }
            final net.minecraft.client.renderer.chunk.ChunkRenderDispatcher sorter = (net.minecraft.client.renderer.chunk.ChunkRenderDispatcher) dispatcher
                .get(mc.renderGlobal);
            if (sorter == null) return out;
            for (Object each : new ArrayList<Object>((List<?>) inView.get(mc.renderGlobal))) {
                final net.minecraft.client.renderer.chunk.RenderChunk chunk = (net.minecraft.client.renderer.chunk.RenderChunk) chunkInView
                    .get(each);
                if (chunk == null) continue;
                final net.minecraft.util.math.BlockPos at = chunk.getPosition();
                if (!near(at.getX(), at.getY(), at.getZ(), x, y, z)) continue;
                final net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator build = (net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator) buildInHand
                    .get(chunk);
                out.add(new Section() {

                    @Override
                    public int originX() {
                        return at.getX();
                    }

                    @Override
                    public int originY() {
                        return at.getY();
                    }

                    @Override
                    public int originZ() {
                        return at.getZ();
                    }

                    @Override
                    public float[] drawnFrom() {
                        // Nothing to keep in order where the chunk as built has no blending faces: its card copy is
                        // empty, or it has moved and is waiting to be built where it now stands.
                        if (chunk.getCompiledChunk()
                            .isLayerEmpty(net.minecraft.util.BlockRenderLayer.TRANSLUCENT)) return null;
                        return get().drawnFrom(chunk);
                    }

                    @Override
                    public boolean inHand() {
                        return chunk.needsUpdate() || build != null
                            && build.getStatus() != net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator.Status.DONE;
                    }

                    @Override
                    public void sortAgain() {
                        sorter.updateTransparencyLater(chunk);
                    }
                });
            }
        } catch (Exception | LinkageError cannot) {
            unreadable = true;
            Trmt.LOG.warn(
                "Blending order: the renderer's chunks cannot be read ({}), so the order is left as the game keeps it",
                String.valueOf(cannot));
            out.clear();
        }
        return out;
    }

    /**
     * Each client tick: the chunks round the camera sorted again where their drawn order is wrong for it. The camera
     * is the eye of the view entity, which is where the game's own worker sorts from ({@code
     * ChunkRenderWorker.processTask}).
     */
    public static void keepRound(net.minecraft.client.Minecraft mc) {
        net.minecraft.entity.Entity eye = mc.getRenderViewEntity();
        if (eye == null || !uploadSeen) return;
        double x = eye.posX;
        double y = eye.posY + eye.getEyeHeight();
        double z = eye.posZ;
        get().keep(sectionsNear(mc, x, y, z), x, y, z);
    }

    /** How many chunks round the camera are drawn in an order worked out more than a block from it, for the harness. */
    public static int staleRound(net.minecraft.client.Minecraft mc) {
        net.minecraft.entity.Entity eye = mc.getRenderViewEntity();
        if (eye == null) return 0;
        double x = eye.posX;
        double y = eye.posY + eye.getEyeHeight();
        double z = eye.posZ;
        return staleNear(sectionsNear(mc, x, y, z), x, y, z);
    }

    /** Each chunk round the camera drawn in an order worked out more than a block from it: its corner and where. */
    public static String staleSaid(net.minecraft.client.Minecraft mc) {
        net.minecraft.entity.Entity eye = mc.getRenderViewEntity();
        if (eye == null) return "";
        double x = eye.posX;
        double y = eye.posY + eye.getEyeHeight();
        double z = eye.posZ;
        StringBuilder said = new StringBuilder();
        for (Section each : sectionsNear(mc, x, y, z)) {
            float[] from = each.drawnFrom();
            if (!stale(from, x, y, z)) continue;
            said.append("; the chunk at ")
                .append(each.originX())
                .append(' ')
                .append(each.originY())
                .append(' ')
                .append(each.originZ())
                .append(" sorted from ")
                .append(String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", from[0], from[1], from[2]))
                .append(each.inHand() ? ", work in hand" : "");
        }
        return said.toString();
    }
}
