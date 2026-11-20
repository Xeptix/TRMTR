package com.trmtgtnh.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.block.GhostRendering;
import com.trmtgtnh.client.texture.WearSprite;
import com.trmtgtnh.client.texture.WearTextures;
import com.trmtgtnh.config.TrmtConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Real glow on worn ground in 1.7.10 (Xep, 2026-11-15): each face the renderer draws for a worn ghost is drawn a second
 * time with its glow picture, where it has one - end stone's late purple cracks and the nether's ash flecks - lit at
 * the brighter of the glow's light and the world's.
 *
 * <p>
 * <strong>Where.</strong> {@code MixinGhostShaderMaterial} calls {@link #afterFace} as each of the renderer's six face
 * methods returns, so whatever drew the worn face - the standard renderer with smooth lighting or without, the render
 * hook's {@code ApiDraw}, a data file's boxes, Angelica's Celeritas mesher, which builds a {@code RenderBlocks} per
 * section and draws through these same methods into the same tessellator - draws the glow straight after it, at the
 * same bounds and with the same picture coordinates, through the same face method. The glow picture is clear
 * everywhere but its lit pixels, and the block pass is drawn alpha-tested, so it is a cutout layer over the worn face.
 *
 * <p>
 * <strong>The light.</strong> The modern renderers take the brighter of a quad's baked light and the world's; the
 * tessellator takes one light and nothing else, so it is taken here: each corner's light with smooth lighting, the
 * face's one light without it, raised block and sky apart to the glow's level and never lowered. By day the sky is
 * brighter than the glow, the glow's pixels are the worn face's own at the worn face's light, and nothing changes; in
 * the dark they keep the glow's light. The colors are left as the face had them, its shade and its smooth lighting's
 * darkening
 * included, as a modern renderer shades an emissive quad. What was changed is put back, so the next face, and the grass
 * fringe a vanilla side draws over its base, are drawn as they would have been.
 */
@SideOnly(Side.CLIENT)
public final class GhostGlow {

    /** Glow faces drawn this session, which the harness logs. */
    private static final AtomicLong DRAWN = new AtomicLong();

    /**
     * The harness's switch alone: while it is set no glow is drawn, so the glow part can photograph one frame with the
     * glow and without it and weigh the two. Nothing else sets it.
     */
    public static volatile boolean held;

    private GhostGlow() {}

    /**
     * A light as the game packs it, raised block and sky apart to {@code level} out of fifteen where it is darker, and
     * otherwise untouched. Each half is taken whole, so a smoothly lit corner's light, which averages four and carries
     * a fraction below its level, keeps that fraction wherever the world's is the brighter.
     */
    public static int brighter(int packed, int level) {
        int sky = Math.max((packed >>> 16) & 0xFF, level << 4);
        int block = Math.max(packed & 0xFF, level << 4);
        return (sky << 16) | block;
    }

    /** How many glow faces have been drawn this session. */
    public static long drawnCount() {
        return DRAWN.get();
    }

    /**
     * Draws a face's glow over it, where it has one; called as each face method returns, with what it was given.
     * Nothing
     * for any block but a ghost, for a face drawn with another picture over it (the breaking cracks), or for a picture
     * with no glow, which is every picture but a worn sprite's whose look glows from the gradation its glow begins.
     *
     * @param face the face method's side: down, up, north, south, west, east
     */
    public static void afterFace(RenderBlocks renderer, Block block, int face, double x, double y, double z,
        IIcon icon) {
        if (held || !TrmtConfig.glowingWear) return;
        if (!(block instanceof GhostBlock) || renderer.hasOverrideBlockTexture()) return;
        IIcon glow = WearTextures.glowIcon(icon);
        if (glow == null) return;
        int level = ((WearSprite) glow).glowLevelOf();
        if (renderer.enableAO) {
            SMOOTH_DRAWN.incrementAndGet();
            int topLeft = renderer.brightnessTopLeft;
            int bottomLeft = renderer.brightnessBottomLeft;
            int bottomRight = renderer.brightnessBottomRight;
            int topRight = renderer.brightnessTopRight;
            renderer.brightnessTopLeft = brighter(topLeft, level);
            renderer.brightnessBottomLeft = brighter(bottomLeft, level);
            renderer.brightnessBottomRight = brighter(bottomRight, level);
            renderer.brightnessTopRight = brighter(topRight, level);
            try {
                draw(renderer, block, face, x, y, z, icon, glow);
            } finally {
                renderer.brightnessTopLeft = topLeft;
                renderer.brightnessBottomLeft = bottomLeft;
                renderer.brightnessBottomRight = bottomRight;
                renderer.brightnessTopRight = topRight;
            }
            return;
        }
        IBlockAccess access = renderer.blockAccess;
        if (access == null) return;
        Tessellator tessellator = Tessellator.instance;
        int world = flatLight(renderer, access, block, face, floor(x), floor(y), floor(z));
        // Angelica's Celeritas lights a smoothly lit block itself, from the world, over whatever light the tessellator
        // was given: the glow is handed to it as the face is, so it takes the face's own light and shade exactly, and
        // MixinCeleritasGlow raises the light Celeritas works out for it (raiseCeleritasLight).
        if (Celeritas.relights(renderer)) {
            CELERITAS_DRAWN.incrementAndGet();
            draw(renderer, block, face, x, y, z, icon, glow);
            return;
        }
        tessellator.setBrightness(brighter(world, level));
        try {
            draw(renderer, block, face, x, y, z, icon, glow);
        } finally {
            tessellator.setBrightness(world);
        }
    }

    /**
     * Raises the light Angelica's Celeritas has just worked out for one quad of a section, where the quad is a glow:
     * called by {@code MixinCeleritasGlow} as soon as Celeritas's own lighter has filled the quad's light data, with
     * the
     * section's build context and the picture the quad is drawn with. Each of the quad's four lightmap values is raised
     * block and sky apart to the glow's level and never lowered ({@link #brighter}); its shade is left as Celeritas
     * made
     * it, the face's own. Anything that is not a glow, or a context whose light data cannot be read, is left alone.
     */
    public static void raiseCeleritasLight(Object context, TextureAtlasSprite sprite) {
        if (held || !TrmtConfig.glowingWear || !(sprite instanceof WearSprite)) return;
        WearSprite glow = (WearSprite) sprite;
        if (!glow.isGlow()) {
            if (CELERITAS_SAMPLES.size() < SAMPLES) LAST_FACE.set(sprite.getIconName() + " " + Celeritas.said(context));
            return;
        }
        int level = glow.glowLevelOf();
        int[] light = Celeritas.lightOf(context);
        if (light == null) return;
        String before = CELERITAS_SAMPLES.size() < SAMPLES ? Celeritas.said(context) : null;
        for (int i = 0; i < light.length; i++) light[i] = brighter(light[i], level);
        CELERITAS_RAISED.incrementAndGet();
        if (before != null) {
            CELERITAS_SAMPLES.add(
                "the face before it, " + LAST_FACE.get()
                    + "; the glow "
                    + sprite.getIconName()
                    + " "
                    + before
                    + ", raised to "
                    + Celeritas.said(context));
        }
    }

    /** How many glow quads, each with the worn face quad Celeritas lit before it, are kept for the harness. */
    private static final int SAMPLES = 4;

    /**
     * The first glow quads Celeritas lit this session, each said with its light and shade before and after it was
     * raised and beside the worn face quad lit before it on the same thread, which is the face it is drawn over: the
     * harness logs them, so a glow that comes out brighter than its face by day says whether its light or its shade
     * differs from the face's.
     */
    private static final java.util.List<String> CELERITAS_SAMPLES = java.util.Collections
        .synchronizedList(new java.util.ArrayList<String>());

    private static final ThreadLocal<String> LAST_FACE = new ThreadLocal<String>();

    /** The glow quads Celeritas lit first this session, said as {@link #CELERITAS_SAMPLES} keeps them. */
    public static java.util.List<String> celeritasSamples() {
        synchronized (CELERITAS_SAMPLES) {
            return new java.util.ArrayList<String>(CELERITAS_SAMPLES);
        }
    }

    /** Glow quads whose Celeritas light was raised this session, which the harness logs. */
    private static final AtomicLong CELERITAS_RAISED = new AtomicLong();

    public static long celeritasRaisedCount() {
        return CELERITAS_RAISED.get();
    }

    /** Glow faces drawn with the standard renderer's own smooth lighting this session, which the harness logs. */
    private static final AtomicLong SMOOTH_DRAWN = new AtomicLong();

    public static long smoothDrawnCount() {
        return SMOOTH_DRAWN.get();
    }

    /** Glow faces handed to Celeritas to light this session, which the harness holds the raised count to. */
    private static final AtomicLong CELERITAS_DRAWN = new AtomicLong();

    public static long celeritasDrawnCount() {
        return CELERITAS_DRAWN.get();
    }

    /**
     * Angelica's own lighting, reached by name and never compiled against (ShaderMaterialStaysOptionalTest), as
     * {@code AngelicaGutter} reaches its gutter. Read from Angelica 2.2.29's and 2.2.43's classes: with its smooth
     * lighting on, Angelica draws a smoothly lit block through the renderer's flat path with its renderer's
     * {@code applyingCeleritasAO} set, and every vertex written until the block ends is one Celeritas lights itself,
     * from the world, with its own smooth lighter and shade, whatever light it was written with. A glow is written
     * there as the face is, so it takes exactly the face's light and shade, and its light is raised once Celeritas has
     * worked it out: its build context's {@code quadLightData.lm}, the four lightmap values of the quad in hand (the
     * same in Embeddium's {@code QuadLightData} in both versions). Without Angelica, or where a name is not found,
     * nothing here is asked again.
     */
    static final class Celeritas {

        private static volatile MethodHandle applying;

        private static volatile boolean absent;

        private static volatile java.lang.reflect.Field lightData;

        private static volatile java.lang.reflect.Field lightmaps;

        private static volatile boolean unreadable;

        private Celeritas() {}

        /** Whether this renderer's block is being lit by Celeritas itself. */
        static boolean relights(RenderBlocks renderer) {
            if (absent || !find()) return false;
            try {
                return (boolean) applying.invokeExact(renderer);
            } catch (Throwable notRead) {
                absent = true;
                return false;
            }
        }

        /** The quad in hand's four lightmap values in a section's build context, or null where they cannot be read. */
        static int[] lightOf(Object context) {
            if (unreadable || context == null) return null;
            try {
                java.lang.reflect.Field data = lightData;
                java.lang.reflect.Field maps = lightmaps;
                if (data == null || maps == null) {
                    data = context.getClass()
                        .getDeclaredField("quadLightData");
                    data.setAccessible(true);
                    maps = data.getType()
                        .getField("lm");
                    maps.setAccessible(true);
                    lightData = data;
                    lightmaps = maps;
                }
                Object owner = data.get(context);
                return owner == null ? null : (int[]) maps.get(owner);
            } catch (Throwable notThere) {
                unreadable = true;
                com.trmtgtnh.Trmt.LOG.warn(
                    "Could not read the light Angelica's Celeritas works out for a quad, so worn ground's glow is lit as the ground around it under Celeritas",
                    notThere);
                return null;
            }
        }

        /**
         * The quad in hand as Celeritas has it, for the harness: each corner's light as sky and block out of 255, its
         * shade, and the color it was written with, or what could not be read.
         */
        static String said(Object context) {
            StringBuilder out = new StringBuilder();
            try {
                int[] lm = lightOf(context);
                Object owner = lightData.get(context);
                float[] br = (float[]) owner.getClass()
                    .getField("br")
                    .get(owner);
                out.append("light");
                for (int each : lm) out.append(' ')
                    .append((each >>> 16) & 0xFF)
                    .append('/')
                    .append(each & 0xFF);
                out.append(", shade");
                for (float each : br) out.append(' ')
                    .append(String.format(java.util.Locale.ROOT, "%.3f", Float.valueOf(each)));
            } catch (Throwable notRead) {
                out.append("light and shade unread (")
                    .append(notRead)
                    .append(')');
            }
            try {
                java.lang.reflect.Field field = context.getClass()
                    .getDeclaredField("vertices");
                field.setAccessible(true);
                Object[] vertices = (Object[]) field.get(context);
                out.append(", color");
                for (Object vertex : vertices) {
                    java.lang.reflect.Field color = vertex.getClass()
                        .getField("color");
                    out.append(' ')
                        .append(Integer.toHexString(color.getInt(vertex)));
                }
            } catch (Throwable notRead) {
                out.append(", color unread");
            }
            return out.toString();
        }

        private static boolean find() {
            if (applying != null) return true;
            try {
                java.lang.reflect.Field field = RenderBlocks.class.getDeclaredField("applyingCeleritasAO");
                field.setAccessible(true);
                applying = MethodHandles.lookup()
                    .unreflectGetter(field)
                    .asType(MethodType.methodType(boolean.class, RenderBlocks.class));
                return true;
            } catch (Throwable notAngelica) {
                absent = true;
                return false;
            }
        }
    }

    /**
     * The one light the standard renderer gives a face without smooth lighting: the neighbour's on that side, or the
     * block's own where the face is drawn inside the block's cell.
     */
    private static int flatLight(RenderBlocks renderer, IBlockAccess access, Block block, int face, int x, int y,
        int z) {
        switch (face) {
            case 0:
                return block.getMixedBrightnessForBlock(access, x, renderer.renderMinY > 0.0D ? y : y - 1, z);
            case 1:
                return block.getMixedBrightnessForBlock(access, x, renderer.renderMaxY < 1.0D ? y : y + 1, z);
            case 2:
                return block.getMixedBrightnessForBlock(access, x, y, renderer.renderMinZ > 0.0D ? z : z - 1);
            case 3:
                return block.getMixedBrightnessForBlock(access, x, y, renderer.renderMaxZ < 1.0D ? z : z + 1);
            case 4:
                return block.getMixedBrightnessForBlock(access, renderer.renderMinX > 0.0D ? x : x - 1, y, z);
            default:
                return block.getMixedBrightnessForBlock(access, renderer.renderMaxX < 1.0D ? x : x + 1, y, z);
        }
    }

    /**
     * The face again, through the same face method, with the glow's picture. A worn top whose set turns is drawn a
     * quarter turn round (MixinGhostTopTurn, from the face's own picture), and the glow is turned with it, so its lit
     * pixels lie over the face's; the turn is put back after.
     */
    private static void draw(RenderBlocks renderer, Block block, int face, double x, double y, double z, IIcon icon,
        IIcon glow) {
        int turn = face == 1 ? GhostRendering.uvRotateTop(icon, floor(x), floor(y), floor(z)) : 0;
        int held = renderer.uvRotateTop;
        if (turn != 0) renderer.uvRotateTop = turn;
        try {
            drawFace(renderer, block, face, x, y, z, glow);
        } finally {
            renderer.uvRotateTop = held;
        }
        DRAWN.incrementAndGet();
    }

    private static void drawFace(RenderBlocks renderer, Block block, int face, double x, double y, double z,
        IIcon glow) {
        switch (face) {
            case 0:
                renderer.renderFaceYNeg(block, x, y, z, glow);
                break;
            case 1:
                renderer.renderFaceYPos(block, x, y, z, glow);
                break;
            case 2:
                renderer.renderFaceZNeg(block, x, y, z, glow);
                break;
            case 3:
                renderer.renderFaceZPos(block, x, y, z, glow);
                break;
            case 4:
                renderer.renderFaceXNeg(block, x, y, z, glow);
                break;
            default:
                renderer.renderFaceXPos(block, x, y, z, glow);
                break;
        }
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
