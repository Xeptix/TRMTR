package com.trmtgtnh.apiload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.api.Wear;
import com.trmtgtnh.api.WearDraw;
import com.trmtgtnh.apihost.PluginHost;
import com.trmtgtnh.apihost.WearCuts;
import com.trmtgtnh.data.Selector;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * The render hook in 26.1.2 (W3 T8; api/API.md, "Drawing a hooked block"; carried from the 1.12.2 tree's class of the
 * same name in T17): a worn ghost standing in for a block a plugin registered a {@code WearRenderer} for is handed to
 * it, and what it asks for is built as quads in place of TRMTR's own.
 *
 * <p>
 * <strong>Where.</strong> {@code GhostQuads.build}, the one point every chunk build reaches a ghost through: Forge's
 * model ({@code GhostModelForge.getQuads}, which vanilla's renderer, Rubidium and OptiFine ask) and Fabric's
 * ({@code GhostModelFabric.emitBlockQuads}, which Indigo, the Sodium family and Canvas ask) both hand every face of a
 * ghost to it - after the pass test and the shader pack's claim, and before the ghost's own shapes (a stair's boxes, a
 * data file's boxes, the one-box ground), so a hooked block is the plugin's whatever its shape.
 *
 * <p>
 * <strong>Nothing of the hook's is drawn until it returns true.</strong> Its boxes and quads are kept and built after,
 * into lists of this draw's own, so a hook that throws or declines leaves nothing behind and the ghost is drawn as
 * TRMTR draws it. What a hook adds to {@link #nativeContext()} (the list of quads being built) is in a list of this
 * draw's own as well, and goes with the rest. Every picture is read before a quad is built (Codex review R21); a face
 * or a quad with no picture hands the whole block back rather than leave a hole (Codex review R22). The 1.7.10
 * edition's tessellator mark and its "stuck" draw have no counterpart here, as in 1.12.2: nothing is written anywhere
 * until the lists are handed to the renderer whole.
 *
 * <p>
 * <strong>Units.</strong> Block units, 0 to 1, laid over the space the ghost is drawn in: y 0 its floor and y 1 the
 * ground's drawn top, which is below the cell's once it has sunk, so a cut at the top stays at the surface as the
 * ground goes down.
 *
 * <p>
 * <strong>Light (0.9.229; found by the rig, 2026-11-06).</strong> 1.7.10 draws a hooked block with smooth lighting off,
 * its outer faces lit by the cell each looks into and everything else - a cut's walls and floors, a hook's own quads -
 * by the cell above, what {@link #light()} reports. A model's quad cannot be handed its light: the game takes it from
 * the cell the quad points into or from its own, both solid under a cut, so walls drew near black. Two things carry the
 * same drawing here. Every quad lit from above is filed under the top face while the game draws that face
 * ({@link Painter#openAbove}), and a quad in a face's list takes that face's cell on every flat path read
 * (ModelBlockRenderer's, Indigo's, the Sodium family's). And each edition turns smooth lighting off for the square, and
 * bakes the light into the quad where a quad can carry it ({@link Painter#overhead}). {@code tools/hook_light.py}
 * reads each of those from each loader's own jars; api/API.md says which pipelines fall short.
 *
 * <p>
 * Names no client class, so a dedicated server that loads it (it never draws) names nothing it lacks: pictures and
 * quads are the {@link Painter}'s, which the client's model package implements, and travel here as objects.
 */
public final class ApiDraw implements WearDraw {

    /**
     * Where the model reads its pictures and builds its quads: the model package's half of a hooked block. Pictures are
     * the game's {@code TextureAtlasSprite} and quads its {@code BakedQuad}, as plain objects here.
     */
    public interface Painter {

        /** The worn ghost's own picture on a face (null for none). */
        Object outer(int face);

        /** The grass fringe the ghost draws over a face, or null. */
        Object outerOverlay(int face);

        /** The picture a cut's wall or floor takes at a box's stage, as the ghost draws that stage (null for none). */
        Object atStage(int face, int stage);

        /** The grass fringe over a cut's wall at a box's stage, or null. */
        Object overlayAtStage(int face, int stage);

        /** The covered block's own picture on a face, its model's (null for none). */
        Object own(int face);

        /** The tinted overlay the covered block's own model lays over that face (a grass side's fringe), or null. */
        Object ownOverlay(int face);

        /** The tint slot a face of the ghost takes its color through. */
        int tintIndex(int face);

        /** The tint slot a fringe takes. */
        int overlayTintIndex();

        /** Where the space the ghost is drawn in begins and where its drawn top stands, in the cell's frame. */
        float floor();

        float height();

        /** One face of a box in the cell's frame, the corners as the model's own faces are wound. */
        Object face(int face, float x0, float y0, float z0, float x1, float y1, float z1, Object sprite, int tintIndex);

        /** A quad a hook placed itself: four corners in the cell's frame, uv 0 to 1 across the sprite, its color. */
        Object free(float[] corners, float[] uv, int face, Object sprite, int rgb);

        /** Whether an object a hook put in {@link #nativeContext()} is a quad this model can draw. */
        boolean isQuad(Object each);

        /**
         * Whether the game draws this block's face toward the cell above: a quad filed under that face is drawn only
         * then. Shut, the cell above is solid and lights nothing, and the quads stay with no side.
         */
        boolean openAbove();

        /** The same quad, told to take the light of the cell above as far as this edition lets a quad say so. */
        Object overhead(Object quad);
    }

    /** What a hooked block came to: one list for each face the renderer culls by, and one it never culls. */
    public static final class Drawn {

        /** By face number, down to east: the faces that lie on the cell's own side and are culled with it. */
        public final List<List<Object>> byFace = new ArrayList<List<Object>>(6);

        /** Everything else, handed over with no side. */
        public final List<Object> general = new ArrayList<Object>();

        Drawn() {
            for (int i = 0; i < 6; i++) byFace.add(new ArrayList<Object>());
        }
    }

    private final int x, y, z;
    private final BlockState origin;
    private final Wear wear;
    private final int light;
    private final int tint;
    private final Painter painter;
    private final WearCuts cuts = new WearCuts();
    private final List<float[]> quads = new ArrayList<float[]>();
    private final List<int[]> quadFaces = new ArrayList<int[]>();
    private final List<Object> nativeQuads = new ArrayList<Object>();
    private boolean open = true;

    private ApiDraw(int x, int y, int z, BlockState origin, Wear wear, int light, int tint, Painter painter) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.origin = origin;
        this.wear = wear;
        this.light = light;
        this.tint = tint;
        this.painter = painter;
    }

    /** Whether any plugin's renderer is registered and on, so a ghost should be offered to one at all. */
    public static boolean draws() {
        PluginHost host = ApiLoad.host();
        return host != null && host.draws();
    }

    /**
     * Hands the ghost standing over {@code origin} to the renderer registered for it, and builds what it asked for.
     * The quads when the hook drew the block, so the model draws nothing of its own; null hands it back to TRMTR.
     *
     * @param record the square's record as the client holds it
     * @param light  the light over the square, packed
     * @param tint   the square's one color, tint slot nought's answer
     */
    public static Drawn drawn(int x, int y, int z, BlockState origin, short record, int light, int tint,
        Painter painter) {
        return drawn(ApiLoad.host(), x, y, z, origin, record, light, tint, painter);
    }

    /** The same, asking a host of the caller's: the session's, or a test's own. */
    static Drawn drawn(PluginHost host, int x, int y, int z, BlockState origin, short record, int light, int tint,
        Painter painter) {
        if (host == null || !host.draws() || origin == null) return null;
        String name = SurfaceRegistry.registryName(origin.getBlock());
        if (name == null) return null;
        Wear wear = record == com.trmtgtnh.erosion.ErosionState.NONE ? null
            : ApiBridge.clientWearOn(record, origin);
        ApiDraw draw = new ApiDraw(x, y, z, origin, wear, light, tint, painter);
        // The block's tags, so a renderer registered for #ns:tag is found (1.16.5 on); none where they cannot be read.
        java.util.Collection<String> tags;
        try {
            tags = SurfaceRegistry.tagsOf(origin.getBlock());
        } catch (RuntimeException | LinkageError unbound) {
            tags = null;
        }
        boolean drew;
        try {
            drew = host.draw(name, Selector.ANY_META, SurfaceRegistry.propertiesOf(origin), tags, draw);
        } finally {
            draw.open = false;
        }
        if (!drew) return null;
        if (draw.cuts.refused() > 0) {
            host.sayOnce(
                "boxes|" + name,
                PluginHost.PREFIX + "the renderer for "
                    + name
                    + " cut more than "
                    + WearCuts.MOST_BOXES
                    + " boxes in one draw; the rest were not cut - report this to that mod");
        }
        // Codex review R21: a failure building what the hook asked for hands the block back to the model whole. Every
        // picture is read before a quad is built, and nothing built reaches the renderer unless all of it was.
        try {
            return draw.build();
        } catch (Exception | LinkageError | AssertionError | StackOverflowError failed) {
            host.sayOnce(
                "emit|" + name,
                PluginHost.PREFIX + "could not draw what the renderer for "
                    + name
                    + " asked for ("
                    + failed
                    + "); TRMTR drew the block itself");
            return null;
        }
    }

    /** The faces of what is left and the hook's own quads, pictures read first, then built; throws on a gap. */
    private Drawn build() {
        float floor = painter.floor();
        float height = painter.height();
        List<WearCuts.Face> faces = cuts.faces();
        // Every picture first (Codex review R21, R22): a face with none hands the whole block back, before a quad.
        Object[] sprites = new Object[faces.size()];
        Object[] overlays = new Object[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            WearCuts.Face face = faces.get(i);
            sprites[i] = face.outer ? painter.outer(face.face)
                : face.stage > 0 ? painter.atStage(face.face, face.stage) : ownSprite(face.face);
            if (sprites[i] == null) throw new IllegalStateException("no picture for face " + face.face);
            // A grass side keeps its tinted fringe over it (Codex review R21), as the ghost's own model and the
            // covered block's own draw one: thinned as the ground's wear, or as the cut's stage, thins it.
            if (face.face >= WearCuts.NORTH) {
                overlays[i] = face.outer ? painter.outerOverlay(face.face)
                    : face.stage > 0 ? painter.overlayAtStage(face.face, face.stage) : painter.ownOverlay(face.face);
            }
        }
        Object[] quadSprites = new Object[quads.size()];
        for (int q = 0; q < quads.size(); q++) {
            quadSprites[q] = ownSprite(quadFaces.get(q)[0]);
            if (quadSprites[q] == null) throw new IllegalStateException("no picture for a quad");
        }

        Drawn out = new Drawn();
        boolean open = painter.openAbove();
        for (int i = 0; i < faces.size(); i++) {
            WearCuts.Face face = faces.get(i);
            float y0 = floor + (height - floor) * face.y0;
            float y1 = floor + (height - floor) * face.y1;
            boolean above = litFromAbove(face);
            List<Object> into = above && open ? out.byFace.get(WearCuts.UP)
                : face.outer && onTheCellsSide(face, y0, y1) ? out.byFace.get(face.face) : out.general;
            Object quad = painter
                .face(face.face, face.x0, y0, face.z0, face.x1, y1, face.z1, sprites[i], painter.tintIndex(face.face));
            into.add(above ? painter.overhead(quad) : quad);
            if (overlays[i] != null) {
                Object fringe = painter
                    .face(face.face, face.x0, y0, face.z0, face.x1, y1, face.z1, overlays[i], painter.overlayTintIndex());
                into.add(above ? painter.overhead(fringe) : fringe);
            }
        }
        for (int q = 0; q < quads.size(); q++) {
            float[] corners = quads.get(q);
            float[] cell = new float[12];
            for (int v = 0; v < 4; v++) {
                cell[v * 3] = corners[v * 3];
                cell[v * 3 + 1] = floor + (height - floor) * corners[v * 3 + 1];
                cell[v * 3 + 2] = corners[v * 3 + 2];
            }
            float[] uv = new float[8];
            System.arraycopy(corners, 12, uv, 0, 8);
            Object quad = painter.free(cell, uv, quadFaces.get(q)[0], quadSprites[q], quadFaces.get(q)[1]);
            (open ? out.byFace.get(WearCuts.UP) : out.general).add(painter.overhead(quad));
        }
        for (Object each : nativeQuads) {
            if (each != null && painter.isQuad(each)) out.general.add(each);
        }
        return out;
    }

    /**
     * Whether a face takes the light of the cell above, as 1.7.10's {@code ApiDraw.plan} lights it: every face a cut
     * opened, and the block's top. An outer side or underside is lit by the cell it looks into, as a whole block's is.
     */
    private static boolean litFromAbove(WearCuts.Face face) {
        return !face.outer || face.face == WearCuts.UP;
    }

    /**
     * Whether an outer face lies on the cell's own side, and so is culled by the neighbour there as a whole block's
     * face is; a top below the cell's own top, a floor above its bottom, and every face a cut opened are not.
     */
    private static boolean onTheCellsSide(WearCuts.Face face, float y0, float y1) {
        switch (face.face) {
            case WearCuts.DOWN:
                return y0 <= 0f;
            case WearCuts.UP:
                return y1 >= 1f;
            case WearCuts.NORTH:
                return face.z0 <= 0f;
            case WearCuts.SOUTH:
                return face.z1 >= 1f;
            case WearCuts.WEST:
                return face.x0 <= 0f;
            default:
                return face.x1 >= 1f;
        }
    }

    /** The covered block's own picture on a face, or the ghost's own where it gives none. */
    private Object ownSprite(int face) {
        Object own = null;
        try {
            own = painter.own(face);
        } catch (RuntimeException awkwardBlock) {
            own = null;
        }
        return own != null ? own : painter.outer(face);
    }

    // ------------------------------------------------------------------
    // WearDraw
    // ------------------------------------------------------------------

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    @Override
    public int z() {
        return z;
    }

    @Override
    public String block() {
        return ApiBridge.blockText(origin);
    }

    @Override
    public Wear wear() {
        return wear;
    }

    /** The covered block's own sprite on a face, a {@code TextureAtlasSprite} (API.md's table). */
    @Override
    public Object sprite(int face) {
        return ownSprite(face);
    }

    /** The ghost's own color: the covered block's, eased by wear; a grass block's sides take none, as vanilla's. */
    @Override
    public int tint(int face) {
        if (face != WearCuts.UP && wear != null && "grass".equals(wear.appearance())) return 0xFFFFFF;
        return tint & 0xFFFFFF;
    }

    /** The light over the block, packed as the game packs it: what its top and every cut are lit by. */
    @Override
    public int light() {
        return light;
    }

    @Override
    public void quad(float[] xyz, float[] uv, int face, int tint) {
        if (!open || xyz == null || uv == null || xyz.length < 12 || uv.length < 8) return;
        float[] corners = new float[20];
        for (int i = 0; i < 12; i++) corners[i] = xyz[i];
        for (int i = 0; i < 8; i++) corners[12 + i] = uv[i];
        for (float each : corners) if (Float.isNaN(each) || Float.isInfinite(each)) return;
        quads.add(corners);
        quadFaces.add(new int[] { face < 0 || face > 5 ? WearCuts.UP : face, tint & 0xFFFFFF });
    }

    @Override
    public void box(float x0, float y0, float z0, float x1, float y1, float z1, int stage) {
        if (!open) return;
        cuts.cut(x0, y0, z0, x1, y1, z1, stage);
    }

    /**
     * The list of quads this draw builds, a {@code List<BakedQuad>} (API.md's table): what a hook adds here is drawn
     * with the rest of its block when it answers true, and thrown away with it when it does not.
     */
    @Override
    public Object nativeContext() {
        return nativeQuads;
    }
}
