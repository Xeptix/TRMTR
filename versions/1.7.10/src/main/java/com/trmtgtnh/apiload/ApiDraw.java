package com.trmtgtnh.apiload;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.trmtgtnh.api.Wear;
import com.trmtgtnh.api.WearDraw;
import com.trmtgtnh.apihost.PluginHost;
import com.trmtgtnh.apihost.WearCuts;
import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.block.GhostRendering;
import com.trmtgtnh.client.ClientErosionCache;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The render hook in 1.7.10 (W3 T8; api/API.md, "Drawing a hooked block"): a worn ghost standing in for a block a
 * plugin registered a {@code WearRenderer} for is handed to it, and what it asks for is drawn through the renderer's
 * own face methods into the {@link Tessellator}, in place of the standard block renderer.
 *
 * <p>
 * <strong>Where.</strong> {@code MixinGhostShaderMaterial} calls {@link #drawn} from {@code renderBlockByRenderType},
 * after the block's bounds are stamped on the renderer, and cancels the standard renderer when it answers true. Every
 * mesher that draws a ghost passes through that method: vanilla's, and Angelica's Celeritas mesher, which (read from
 * Angelica 2.2.29's own classes) builds a {@code RenderBlocks} per section, calls {@code renderBlockByRenderType} into
 * {@code Tessellator.instance} and copies that tessellator's buffer into the section mesh after each block. The faces
 * here are written by the same {@code renderFace*} methods and into the same {@code Tessellator.instance} the standard
 * renderer uses, so whatever captures the one captures the other, Angelica's shader normals included.
 *
 * <p>
 * <strong>Nothing of the hook's reaches the tessellator until it returns true.</strong> Its boxes and quads are kept
 * and drawn after, so a hook that throws or declines leaves nothing behind and the block is drawn as usual. Only what a
 * hook writes straight to {@link #nativeContext()} goes in at once, which is the price of asking for it. Drawing what
 * it asked for is guarded the same way (Codex review R21): every picture, light and color is read before a vertex is
 * written, and a failure while writing takes the written vertices back out, so the block goes back to the standard
 * renderer whole. A face with no picture hands it back too, rather than leave a hole; a tessellator whose count cannot
 * be read is never handed to a hook, and one that cannot be set back keeps what was written rather than take a second
 * block over it (Codex review R22).
 *
 * <p>
 * <strong>Units.</strong> Block units, 0 to 1, laid over the bounds the ghost is drawn in: y 1 is the ground's drawn
 * top,
 * which is below the cell's once it has sunk, so a cut at the top stays at the surface as the ground goes down.
 */
@SideOnly(Side.CLIENT)
public final class ApiDraw implements WearDraw {

    /**
     * The non-AO light each face takes, as the standard block renderer shades them: down, up, north, south, west, east.
     */
    static final float[] SHADE = { 0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f };

    /** Each face's neighbour, as an offset: down, up, north, south, west, east. */
    private static final int[][] TOWARD = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    private final GhostBlock ghost;
    private final IBlockAccess world;
    private final int x, y, z;
    private final Block origin;
    private final int originMeta;
    private final Wear wear;
    private final WearCuts cuts = new WearCuts();
    private final List<float[]> quads = new ArrayList<float[]>();
    private final List<int[]> quadFaces = new ArrayList<int[]>();
    private boolean open = true;
    /** Set when writing failed part way and what was written could not be taken back out of the tessellator. */
    private boolean stuck;

    private ApiDraw(GhostBlock ghost, IBlockAccess world, int x, int y, int z, Block origin, int originMeta,
        Wear wear) {
        this.ghost = ghost;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.origin = origin;
        this.originMeta = originMeta;
        this.wear = wear;
    }

    /**
     * Hands the ghost at a position to the renderer registered for the block it stands in for, and draws what it asked
     * for. True when the hook drew the block, so the standard renderer must not; false hands it back to TRMTR.
     */
    public static boolean drawn(RenderBlocks renderer, GhostBlock ghost, int x, int y, int z) {
        PluginHost host = ApiLoad.host();
        if (host == null || !host.draws()) return false;
        int packed = ClientErosionCache.get()
            .originAt(x, y, z);
        if (packed < 0) return false;
        Block origin = GhostRendering.originBlock(packed);
        if (origin == null) return false;
        int originMeta = packed & 0xF;
        String name = SurfaceRegistry.registryName(origin);
        if (name == null) return false;
        short state = ClientErosionCache.get()
            .stateAt(x, y, z);
        Wear wear = state == ErosionState.NONE ? null : ApiBridge.clientWearOn(state, origin, originMeta);
        // Codex review R22: a failure drawing what the hook asked for can only be taken back where the tessellator's
        // count can be read, so where it cannot (a tessellator another mod has rebuilt) the hook is not asked at all
        // and TRMTR draws the block itself: nothing of the hook's is ever left under the standard renderer's faces.
        // Taken before the hook, so a failure takes back what it wrote to nativeContext() too.
        Tessellator tessellator = Tessellator.instance;
        int[] mark = Mark.take(tessellator);
        if (mark == null) {
            host.sayOnce(
                "tessellator",
                PluginHost.PREFIX
                    + "the tessellator's vertex count cannot be read (another mod has changed it), so a hooked block"
                    + " that failed part way could not be taken back; TRMTR draws hooked blocks itself");
            return false;
        }
        ApiDraw draw = new ApiDraw(ghost, renderer.blockAccess, x, y, z, origin, originMeta, wear);
        boolean drew;
        try {
            drew = host.draw(name, originMeta, null, null, draw);
        } finally {
            draw.open = false;
        }
        if (!drew) return false;
        if (draw.cuts.refused() > 0) {
            host.sayOnce(
                "boxes|" + name,
                PluginHost.PREFIX + "the renderer for "
                    + name
                    + " cut more than "
                    + WearCuts.MOST_BOXES
                    + " boxes in one draw; the rest were not cut - report this to that mod");
        }
        // Codex review R21: a failure drawing what the hook asked for hands the block back to the standard renderer.
        // Everything that asks another mod or the world is read first, before a vertex is written; what is written
        // after it is taken back out of the tessellator if it fails part way.
        Throwable failed;
        try {
            List<Planned> plan = draw.plan(renderer);
            failed = draw.emit(renderer, plan, tessellator, mark);
        } catch (Exception | LinkageError | AssertionError | StackOverflowError thrown) {
            failed = thrown;
        }
        if (failed == null) return true;
        if (draw.stuck) {
            // Codex review R22: what was written could not be taken back out. Drawing the standard block over it
            // would lay two blocks' faces in one place; the part already written is kept instead, said once.
            host.sayOnce(
                "stuck|" + name,
                PluginHost.PREFIX + "could not draw what the renderer for "
                    + name
                    + " asked for ("
                    + failed
                    + ") and could not take back what was written; the block is left as far as it was drawn");
            return true;
        }
        host.sayOnce(
            "emit|" + name,
            PluginHost.PREFIX + "could not draw what the renderer for "
                + name
                + " asked for ("
                + failed
                + "); TRMTR drew the block itself");
        return false;
    }

    /**
     * One face of what is left, read whole before anything is written: its bounds, picture, light and color, and the
     * tinted fringe a grass side takes over it.
     */
    static final class Planned {

        int face;
        double x0, y0, z0, x1, y1, z1;
        IIcon icon;
        int brightness;
        int rgb;
        /** The grass fringe drawn over this face, or null. */
        IIcon overlay;
        int overlayRgb;
        /** For a hook's own quad: its four corners in world units with their u and v, or null for a face. */
        double[] vertices;
    }

    /**
     * Reads everything the drawing needs from the world and from other mods' code: which faces stand, their pictures,
     * light and color, and each quad's corners and picture. Nothing is written to the tessellator here, so a failure
     * leaves nothing behind.
     */
    private List<Planned> plan(RenderBlocks renderer) {
        Block block = ghost.asBlock();
        double minX = renderer.renderMinX, minY = renderer.renderMinY, minZ = renderer.renderMinZ;
        double maxX = renderer.renderMaxX, maxY = renderer.renderMaxY, maxZ = renderer.renderMaxZ;
        int tintTop = tint(WearCuts.UP), tintSide = tint(WearCuts.NORTH);
        boolean fringes = RenderBlocks.fancyGrass && !renderer.hasOverrideBlockTexture();
        List<Planned> plan = new ArrayList<Planned>();
        for (WearCuts.Face face : cuts.faces()) {
            int[] toward = TOWARD[face.face];
            if (face.outer && !renderer.renderAllFaces
                && !block.shouldSideBeRendered(world, x + toward[0], y + toward[1], z + toward[2], face.face)) {
                continue;
            }
            IIcon icon = face.outer ? GhostRendering.iconFor(ghost, world, x, y, z, face.face)
                : face.stage > 0 ? GhostRendering.iconFor(ghost, world, x, y, z, face.face, face.stage)
                    : ownSprite(face.face);
            // Codex review R22: a face with no picture would be a hole, so the whole block goes back instead.
            if (icon == null) throw new IllegalStateException("no picture for face " + face.face);
            Planned each = new Planned();
            each.face = face.face;
            each.x0 = lerp(minX, maxX, face.x0);
            each.y0 = lerp(minY, maxY, face.y0);
            each.z0 = lerp(minZ, maxZ, face.z0);
            each.x1 = lerp(minX, maxX, face.x1);
            each.y1 = lerp(minY, maxY, face.y1);
            each.z1 = lerp(minZ, maxZ, face.z1);
            each.icon = icon;
            each.brightness = face.outer
                ? block.getMixedBrightnessForBlock(world, x + toward[0], y + toward[1], z + toward[2])
                : light();
            each.rgb = face.face == WearCuts.UP ? tintTop : tintSide;
            // A grass side takes its tinted fringe over it, as the standard renderer draws one over any side whose
            // picture is grass_side (Codex review R21): thinned as the ground's wear, or as the cut's stage, thins it.
            if (fringes && face.face >= WearCuts.NORTH && "grass_side".equals(icon.getIconName())) {
                IIcon vanilla = BlockGrass.getIconSideOverlay();
                each.overlay = face.outer ? GhostRendering.grassSideOverlay(ghost, x, y, z, vanilla)
                    : face.stage > 0 ? GhostRendering.grassSideOverlay(ghost, x, y, z, vanilla, face.stage) : vanilla;
                each.overlayRgb = GhostRendering.colorFor(ghost, world, x, y, z) & 0xFFFFFF;
            }
            plan.add(each);
        }
        for (int q = 0; q < quads.size(); q++) {
            float[] corners = quads.get(q);
            int face = quadFaces.get(q)[0];
            IIcon icon = ownSprite(face);
            if (icon == null) throw new IllegalStateException("no picture for a quad on face " + face);
            Planned each = new Planned();
            each.face = face;
            each.icon = icon;
            each.brightness = light();
            each.rgb = quadFaces.get(q)[1];
            each.vertices = new double[20];
            for (int v = 0; v < 4; v++) {
                each.vertices[v * 5] = x + lerp(minX, maxX, corners[v * 3]);
                each.vertices[v * 5 + 1] = y + lerp(minY, maxY, corners[v * 3 + 1]);
                each.vertices[v * 5 + 2] = z + lerp(minZ, maxZ, corners[v * 3 + 2]);
                each.vertices[v * 5 + 3] = icon.getInterpolatedU(corners[12 + v * 2] * 16.0D);
                each.vertices[v * 5 + 4] = icon.getInterpolatedV(corners[13 + v * 2] * 16.0D);
            }
            plan.add(each);
        }
        return plan;
    }

    /**
     * Writes the plan through the renderer's own face methods into the tessellator. Null when it was drawn; what
     * failed when it was not, with every vertex written since {@code mark} taken back out (see {@link Mark}); where
     * that cannot be done, {@link #stuck} is set so the standard renderer does not draw over it.
     */
    private Throwable emit(RenderBlocks renderer, List<Planned> plan, Tessellator tessellator, int[] mark) {
        Block block = ghost.asBlock();
        boolean ao = renderer.enableAO;
        double minX = renderer.renderMinX, minY = renderer.renderMinY, minZ = renderer.renderMinZ;
        double maxX = renderer.renderMaxX, maxY = renderer.renderMaxY, maxZ = renderer.renderMaxZ;
        renderer.enableAO = false;
        // A plugin's renderer lays out its own faces, so its tops are drawn as it laid them, never turned with the
        // position (0.9.229): the newer editions build a hooked square's quads apart from the ghost's own, unturned.
        GhostRendering.HOOK_DRAWING.set(Boolean.TRUE);
        try {
            for (Planned each : plan) {
                tessellator.setBrightness(each.brightness);
                color(tessellator, each.rgb, each.face);
                if (each.vertices != null) {
                    for (int v = 0; v < 4; v++) {
                        tessellator.addVertexWithUV(
                            each.vertices[v * 5],
                            each.vertices[v * 5 + 1],
                            each.vertices[v * 5 + 2],
                            each.vertices[v * 5 + 3],
                            each.vertices[v * 5 + 4]);
                    }
                    continue;
                }
                renderer.setRenderBounds(each.x0, each.y0, each.z0, each.x1, each.y1, each.z1);
                face(renderer, block, each.face, each.icon);
                if (each.overlay != null) {
                    color(tessellator, each.overlayRgb, each.face);
                    face(renderer, block, each.face, each.overlay);
                }
            }
            return null;
        } catch (Exception | LinkageError | AssertionError | StackOverflowError thrown) {
            if (!Mark.restore(tessellator, mark)) stuck = true;
            return thrown;
        } finally {
            GhostRendering.HOOK_DRAWING.remove();
            renderer.enableAO = ao;
            renderer.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private void face(RenderBlocks renderer, Block block, int face, IIcon icon) {
        switch (face) {
            case WearCuts.DOWN:
                renderer.renderFaceYNeg(block, x, y, z, icon);
                break;
            case WearCuts.UP:
                renderer.renderFaceYPos(block, x, y, z, icon);
                break;
            case WearCuts.NORTH:
                renderer.renderFaceZNeg(block, x, y, z, icon);
                break;
            case WearCuts.SOUTH:
                renderer.renderFaceZPos(block, x, y, z, icon);
                break;
            case WearCuts.WEST:
                renderer.renderFaceXNeg(block, x, y, z, icon);
                break;
            default:
                renderer.renderFaceXPos(block, x, y, z, icon);
                break;
        }
    }

    /**
     * Where the tessellator's buffer stood before a hooked block was written, so a failure part way can take back what
     * was written (Codex review R21). The tessellator keeps its count in private fields, read by their development and
     * game names. Where they cannot be read (a tessellator some other mod has rebuilt), the hook is not asked and the
     * standard renderer draws the block (Codex review R22); where they cannot be written back, what was written is kept
     * and the standard renderer does not draw over it.
     */
    static final class Mark {

        private static final String[][] NAMES = { { "rawBufferIndex", "field_147569_p" },
            { "vertexCount", "field_78406_i" }, { "addedVertices", "field_78411_s" } };
        private static volatile Field[] fields;
        private static volatile boolean unreadable;

        private Mark() {}

        static int[] take(Tessellator tessellator) {
            Field[] found = fields();
            if (found == null) return null;
            try {
                int[] mark = new int[found.length];
                for (int i = 0; i < found.length; i++) mark[i] = found[i].getInt(tessellator);
                return mark;
            } catch (IllegalAccessException | RuntimeException notReadable) {
                return null;
            }
        }

        /** True when the tessellator stands where the mark was taken again; false when it could not be set back. */
        static boolean restore(Tessellator tessellator, int[] mark) {
            Field[] found = fields();
            if (found == null || mark == null || mark.length != found.length) return false;
            try {
                for (int i = 0; i < found.length; i++) found[i].setInt(tessellator, mark[i]);
                return true;
            } catch (IllegalAccessException | RuntimeException notWritable) {
                return false;
            }
        }

        private static Field[] fields() {
            Field[] found = fields;
            if (found != null || unreadable) return found;
            found = new Field[NAMES.length];
            for (int i = 0; i < NAMES.length; i++) {
                for (String name : NAMES[i]) {
                    try {
                        found[i] = Tessellator.class.getDeclaredField(name);
                        found[i].setAccessible(true);
                        break;
                    } catch (NoSuchFieldException | RuntimeException notThatName) {
                        found[i] = null;
                    }
                }
                if (found[i] == null || found[i].getType() != int.class) {
                    unreadable = true;
                    return null;
                }
            }
            fields = found;
            return found;
        }
    }

    private static double lerp(double from, double to, float at) {
        return from + (to - from) * at;
    }

    private static void color(Tessellator tessellator, int rgb, int face) {
        float shade = SHADE[face < 0 || face > 5 ? 1 : face];
        tessellator.setColorOpaque_F(
            (rgb >> 16 & 0xFF) / 255f * shade,
            (rgb >> 8 & 0xFF) / 255f * shade,
            (rgb & 0xFF) / 255f * shade);
    }

    private IIcon ownSprite(int face) {
        try {
            IIcon icon = origin.getIcon(face, originMeta);
            if (icon != null) return icon;
        } catch (RuntimeException awkwardBlock) {
            // The ghost's own picture below, rather than a failed chunk build.
        }
        return GhostRendering.iconFor(ghost, world, x, y, z, face);
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
        return ApiBridge.blockText(origin, originMeta);
    }

    @Override
    public Wear wear() {
        return wear;
    }

    @Override
    public Object sprite(int face) {
        return ownSprite(face);
    }

    /** The ghost's own tint: the covered block's, eased by wear; a grass block's sides take none, as vanilla's. */
    @Override
    public int tint(int face) {
        if (face != WearCuts.UP && ghost.appearance() == SurfaceFamily.GRASS) return 0xFFFFFF;
        return GhostRendering.colorFor(ghost, world, x, y, z) & 0xFFFFFF;
    }

    /** The light over the block, packed as the game packs it: what its top and every cut are lit by. */
    @Override
    public int light() {
        return ghost.asBlock()
            .getMixedBrightnessForBlock(world, x, y + 1, z);
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

    /** {@code Tessellator.instance}, drawing quads at world positions; what is written here goes in at once. */
    @Override
    public Object nativeContext() {
        return Tessellator.instance;
    }
}
