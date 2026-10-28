package com.trmtgtnh.client.gui;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Comparator;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.ColorizerGrass;

import org.lwjgl.opengl.GL11;

import com.trmtgtnh.Trmt;

/**
 * The pictures the mod's own settings screen is drawn with: a lawn with a path worn across it, a heading laid on a
 * strip of path, a bar that wears from grass to dirt, and the Options-screen button's icon (0.9.228).
 *
 * <p>
 * Xep's brief for 26.x's own screen, carried here: "a little more themed towards my mod" - worn dirt and grass behind
 * it, headers on a path strip, sliders that wear grass to dirt, the mod's icon in the title. The newer editions draw it
 * from pictures their jars carry, {@code grass_top_<n>_r<k>} and {@code dirt_all_0_r<k>}. <strong>This jar carries
 * none</strong>, as the 1.7.10 edition's does not, so the same pictures are composed here as the screen first draws,
 * from the game's own grass top - tinted with the default grass color, since the file is grey as the game's is - and
 * its own dirt, so the lawn follows whatever resource pack is loaded, and is composed again when the pack changes.
 *
 * <p>
 * Grass at wear {@code n} of four has {@code n} quarters of its pixels taken away, showing the dirt beneath: which
 * quarter each pixel falls in is fixed by a hash of where it is, ranked so each quarter is exactly a quarter, so a
 * picture is the same every time it is made and a more worn one has lost every pixel a less worn one had. Each comes in
 * four turns, so a lawn of them does not repeat. The arithmetic is the 1.7.10 edition's, line for line, so the two
 * lawns are the same lawn.
 *
 * <p>
 * <strong>Drawn with the alpha test on</strong>, as every screen of this edition is: every composed pixel is opaque, so
 * the test keeps all of it, and the color is set back to white after every picture.
 */
public final class WornPathArt {

    /** The most worn picture there is: every pixel of grass taken away. */
    static final int MOST_WORN = 4;

    /** How many turns each picture comes in. */
    static final int TURNS = 4;

    /** The size a square of lawn is drawn at behind the screen: twice the picture's own sixteen. */
    static final int LAWN_SQUARE = 32;

    /** The rows of the button's icon the path is worn across: the middle six of sixteen, as 26.3's icon. */
    static final int PATH_FROM = 5;

    static final int PATH_ROWS = 6;

    /** What the screen's title is drawn beside: the tamper, the first tool the mod gives a player. */
    static final ResourceLocation ICON = new ResourceLocation(Trmt.MODID, "textures/items/tamper.png");

    /** The game's own pictures the lawn is composed from. */
    static final ResourceLocation GRASS = new ResourceLocation("textures/blocks/grass_top.png");

    static final ResourceLocation DIRT = new ResourceLocation("textures/blocks/dirt.png");

    /** The composed squares, by wear then turn, and the button's icon; null until first drawn and after a reload. */
    private static ResourceLocation[][] squares;

    private static ResourceLocation icon;

    private static boolean listening;

    private WornPathArt() {}

    // ------------------------------------------------------------------
    // Composing - plain, so a test can ask it
    // ------------------------------------------------------------------

    /** The game's default grass color, opaque: the color an item of grass is drawn in, outside any biome. */
    static int grassColor() {
        return 0xFF000000 | ColorizerGrass.getGrassColor(0.5D, 1.0D);
    }

    /**
     * Which quarter of the grass each pixel of a {@code size} square is taken away in, nought to three: a fixed hash of
     * where it is, ranked, so each quarter holds exactly a quarter of the pixels and the answer never changes.
     */
    static int[] quarters(int size) {
        final int count = size * size;
        Integer[] order = new Integer[count];
        final int[] hashed = new int[count];
        for (int at = 0; at < count; at++) {
            order[at] = Integer.valueOf(at);
            hashed[at] = mix(at % size, at / size);
        }
        Arrays.sort(order, new Comparator<Integer>() {

            @Override
            public int compare(Integer a, Integer b) {
                int byHash = Integer.compare(hashed[a.intValue()], hashed[b.intValue()]);
                return byHash != 0 ? byHash : Integer.compare(a.intValue(), b.intValue());
            }
        });
        int[] quarter = new int[count];
        for (int rank = 0; rank < count; rank++) {
            quarter[order[rank].intValue()] = (int) ((long) rank * 4 / count);
        }
        return quarter;
    }

    /** A well-mixed number for a pixel, the same for the same place every time. */
    static int mix(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }

    /** A color multiplied by a tint, as the game tints grass; the alpha is the color's own. */
    static int tint(int argb, int tint) {
        int r = ((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (tint & 0xFF) / 255;
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /**
     * One square of ground, {@code size} by {@code size}: the dirt, and the tinted grass over it with {@code wear}
     * quarters of its pixels taken away, the whole turned a quarter turn clockwise {@code turn} times.
     *
     * @param grass the grass top's pixels, {@code size} square, as the file holds them
     * @param dirt  the dirt's pixels, {@code size} square
     */
    static int[] square(int[] grass, int[] dirt, int size, int wear, int turn, int tintColor) {
        int[] quarter = quarters(size);
        int[] out = new int[size * size];
        int worn = clampWear(wear);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int from = unturned(x, y, size, turn & 3);
                int pixel = quarter[from] < worn ? dirt[from] : tint(grass[from], tintColor);
                out[y * size + x] = 0xFF000000 | pixel;
            }
        }
        return out;
    }

    /** The tan a path top is drawn toward: the newer games' own path top, read by eye, as one color. */
    static final int PATH_TONE = 0x947A50;

    /**
     * A path top made from a pixel of the pack's own dirt, for the Options button's icon (0.9.228; Xep: "I like the
     * path top better"): this game has no path block to take one from, so the dirt is drawn most of the way toward
     * a path's tan - lighter, warmer and flatter, as trodden earth is - and still follows the loaded resource pack.
     */
    static int pathTop(int argb) {
        int out = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int dirt = argb >> shift & 0xFF;
            int tone = PATH_TONE >> shift & 0xFF;
            out |= (dirt * 45 + tone * 55) / 100 << shift;
        }
        return out;
    }

    /**
     * The Options-screen button's icon (Xep: "A custom icon, showing a worn grass path from in-game own textures"): a
     * square of lawn with the most worn picture under a band through the middle, untouched grass either side.
     */
    static int[] icon(int[] grass, int[] dirt, int size, int tintColor) {
        int[] lawn = square(grass, dirt, size, 0, 0, tintColor);
        int[] path = new int[size * size];
        for (int i = 0; i < path.length; i++) {
            path[i] = pathTop(dirt[i]);
        }
        int from = size * PATH_FROM / 16;
        int to = from + size * PATH_ROWS / 16;
        int[] out = lawn.clone();
        for (int y = from; y < to; y++) {
            System.arraycopy(path, y * size, out, y * size, size);
        }
        return out;
    }

    /**
     * The settings screen's dark dirt (spec M12, 1.3): the pack's own dirt with each channel times {@code factor},
     * opaque - {@code DIRT_BG} at 0.22 behind the panel, {@code DIRT_PANEL} at 0.16 inside it. Composed from the pack,
     * as the lawn is, so it follows the resource pack.
     */
    static int[] darkened(int[] dirt, float factor) {
        int[] out = new int[dirt.length];
        for (int i = 0; i < dirt.length; i++) {
            out[i] = com.trmtgtnh.client.settings.Canvas.darken(dirt[i] & 0xFFFFFF, factor);
        }
        return out;
    }

    /** Where the pixel drawn at {@code x, y} of a square turned {@code turn} times comes from, as an index. */
    static int unturned(int x, int y, int size, int turn) {
        int last = size - 1;
        switch (turn & 3) {
            case 1:
                return (last - x) * size + y;
            case 2:
                return (last - y) * size + (last - x);
            case 3:
                return x * size + (last - y);
            default:
                return y * size + x;
        }
    }

    /** How worn the lawn is at a point: four on the path's middle line, nought two squares and more from it. */
    static int wearOnThePath(int x, int y, int width, int height) {
        double across = width <= 0 ? 0D : x / (double) width;
        double line = height * 0.62D + Math.sin(across * Math.PI * 1.6D + 0.6D) * height * 0.14D;
        double squares = Math.abs(y - line) / LAWN_SQUARE;
        if (squares < 0.5D) return 4;
        if (squares < 1.0D) return 3;
        if (squares < 1.5D) return 2;
        if (squares < 2.0D) return 1;
        return 0;
    }

    /** A fixed turn for a square, from where it is. */
    static int turnAt(int column, int row) {
        int mixed = column * 73856093 ^ row * 19349663;
        return (mixed >>> 7) & 3;
    }

    private static int clampWear(int wear) {
        return wear < 0 ? 0 : wear > MOST_WORN ? MOST_WORN : wear;
    }

    // ------------------------------------------------------------------
    // The game's own pictures, read and made into textures of the screen's own
    // ------------------------------------------------------------------

    /**
     * How long composing took in the frame the settingsperf part is reading (0.9.228): reset at each frame's start by
     * {@code SettingsPictures.frame}, and nought in every frame but the one that composed.
     */
    static long composeNanos;

    /** The squares and the icon, composed from what the resource pack holds now, once until the pack changes. */
    private static void compose() {
        if (squares != null) return;
        long started = System.nanoTime();
        try {
            composeNow();
        } finally {
            composeNanos += System.nanoTime() - started;
        }
    }

    private static void composeNow() {
        Minecraft mc = Minecraft.getMinecraft();
        listen(mc);
        BufferedImage grassImage = read(mc.getResourceManager(), GRASS);
        BufferedImage dirtImage = read(mc.getResourceManager(), DIRT);
        // The picture's own resolution, so a 32-pixel pack's lawn is drawn at 32 pixels; sixteen when it cannot be
        // read.
        int size = grassImage != null ? grassImage.getWidth() : dirtImage != null ? dirtImage.getWidth() : 16;
        size = Math.max(1, Math.min(size, 256));
        int[] grass = pixels(grassImage, size, 0xFF9E9E9E);
        int[] dirt = pixels(dirtImage, size, 0xFF866043);
        int tintColor = grassColor();
        TextureManager textures = mc.getTextureManager();
        ResourceLocation[][] made = new ResourceLocation[MOST_WORN + 1][TURNS];
        for (int wear = 0; wear <= MOST_WORN; wear++) {
            for (int turn = 0; turn < TURNS; turn++) {
                made[wear][turn] = upload(
                    textures,
                    "trmtgtnh_lawn_" + wear + "_r" + turn,
                    square(grass, dirt, size, wear, turn, tintColor),
                    size);
            }
        }
        icon = upload(textures, "trmtgtnh_settings_icon", icon(grass, dirt, size, tintColor), size);
        dirtBg = upload(
            textures,
            "trmtgtnh_settings_dirt_bg",
            darkened(dirt, com.trmtgtnh.client.settings.Canvas.DIRT_BG_FACTOR),
            size);
        dirtPanel = upload(
            textures,
            "trmtgtnh_settings_dirt_panel",
            darkened(dirt, com.trmtgtnh.client.settings.Canvas.DIRT_PANEL_FACTOR),
            size);
        squares = made;
    }

    /** The settings screen's two dark dirts, composed with the lawn; null until first drawn and after a reload. */
    private static ResourceLocation dirtBg;

    private static ResourceLocation dirtPanel;

    /** The wordmark, regular and compact (spec 1.1), and the settings screen's icons (1.3), shipped in the jar. */
    static final ResourceLocation WORDMARK = new ResourceLocation(Trmt.MODID, "textures/gui/wordmark.png");

    static final ResourceLocation WORDMARK_SMALL = new ResourceLocation(Trmt.MODID, "textures/gui/wordmark_small.png");

    static final ResourceLocation ICONS = new ResourceLocation(Trmt.MODID, "textures/gui/settings_icons.png");

    /** The size the dark dirt is tiled at: the pack's sixteen pixels to sixteen GUI pixels. */
    static final int DIRT_TILE = 16;

    /** A box of the screen's dark dirt: {@code DIRT_BG}, or {@code DIRT_PANEL} inside the list panel. */
    static void dirt(int x0, int y0, int x1, int y1, boolean panel) {
        if (x1 <= x0 || y1 <= y0) return;
        compose();
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(panel ? dirtPanel : dirtBg);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        double u0 = x0 / (double) DIRT_TILE;
        double v0 = y0 / (double) DIRT_TILE;
        double u1 = x1 / (double) DIRT_TILE;
        double v1 = y1 / (double) DIRT_TILE;
        // The dynamic texture repeats, so one quad tiles the whole box.
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x0, y1, 0D)
            .tex(u0, v1)
            .endVertex();
        buffer.pos(x1, y1, 0D)
            .tex(u1, v1)
            .endVertex();
        buffer.pos(x1, y0, 0D)
            .tex(u1, v0)
            .endVertex();
        buffer.pos(x0, y0, 0D)
            .tex(u0, v0)
            .endVertex();
        tessellator.draw();
    }

    /**
     * The header's lawn band (spec 1.1): squares of lawn across the full width, a path worn along it, cut to the band.
     */
    static void lawnBand(int width, int height) {
        for (int top = 0, row = 0; top < height; top += LAWN_SQUARE, row++) {
            for (int left = 0, column = 0; left < width; left += LAWN_SQUARE, column++) {
                int shown = Math.min(LAWN_SQUARE, height - top);
                int wear = wearOnThePath(left + LAWN_SQUARE / 2, top + LAWN_SQUARE / 2, width, height * 2);
                squareCut(left, top, LAWN_SQUARE, LAWN_SQUARE, shown, wear, turnAt(column, row));
            }
        }
    }

    /** A square of ground cut to {@code width} across and {@code shownHeight} down from its top. */
    private static void squareCut(int x, int y, int size, int width, int shownHeight, int wear, int turn) {
        if (width <= 0 || size <= 0 || shownHeight <= 0) return;
        compose();
        int shown = Math.min(width, size);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(squares[clampWear(wear)][turn & 3]);
        quad(x, y, shown, shownHeight, shown / (double) size, shownHeight / (double) size);
    }

    /**
     * The compact header's wordmark on one line, TRMT then REIMAGINED (0.9.228; Xep, 2026-10-25, asked whether the
     * compact header should read the full name: "'TRMT: Reimagined'" - the full name where it fits, TRMT alone only on
     * a narrow window). Both halves are cut from the two shipped wordmarks, so it is the V2 artwork's own lettering:
     * the compact TRMT, and the REIMAGINED line from under the regular one, set on TRMT's baseline. No colon between
     * them: Xep, shown one, "I kind of like it better without the cologn in the settings menu".
     */
    static final int LINE_TRMT_U = 8;

    static final int LINE_TRMT_WIDTH = 32;

    static final int LINE_GAP = 3;

    static final int LINE_WORD_U = 10;

    static final int LINE_WORD_V = 20;

    static final int LINE_WORD_WIDTH = 58;

    static final int LINE_WORD_HEIGHT = 9;

    /** Where REIMAGINED stands below the top of the compact TRMT, so the two share a baseline. */
    static final int LINE_WORD_DROP = 6;

    static final int LINE_WIDTH = LINE_TRMT_WIDTH + LINE_GAP + LINE_WORD_WIDTH;

    /** The space kept between the line and the help button or the header's icon buttons either side of it. */
    static final int LINE_CLEAR = 4;

    /**
     * Where the one-line wordmark starts in a compact header, centred, or -1 where it does not fit between the help
     * button and the leftmost of the header's three icon slots - a narrow window, where TRMT alone is drawn.
     */
    static int lineLeft(com.trmtgtnh.client.settings.SettingsGeometry.Frame frame) {
        if (!frame.compact) return -1;
        int x = (frame.width - LINE_WIDTH) / 2;
        boolean fits = x >= frame.help.x1 + LINE_CLEAR && x + LINE_WIDTH <= frame.headerSlots[2].x0 - LINE_CLEAR;
        return fits ? x : -1;
    }

    /** The header's wordmark: the regular one, or in a compact header the one-line name where it fits, else TRMT. */
    static void wordmark(com.trmtgtnh.client.settings.SettingsGeometry.Frame frame) {
        int line = lineLeft(frame);
        if (line < 0) {
            wordmark(frame.wordmark.x0, frame.wordmark.y0, frame.compact);
            return;
        }
        TextureManager textures = Minecraft.getMinecraft()
            .getTextureManager();
        int y = frame.wordmark.y0;
        textures.bindTexture(WORDMARK_SMALL);
        blended(
            line,
            y,
            LINE_TRMT_U,
            0,
            LINE_TRMT_WIDTH,
            com.trmtgtnh.client.settings.SettingsGeometry.WORDMARK_SMALL_HEIGHT,
            com.trmtgtnh.client.settings.SettingsGeometry.WORDMARK_SMALL_WIDTH,
            com.trmtgtnh.client.settings.SettingsGeometry.WORDMARK_SMALL_HEIGHT);
        textures.bindTexture(WORDMARK);
        blended(
            line + LINE_TRMT_WIDTH + LINE_GAP,
            y + LINE_WORD_DROP,
            LINE_WORD_U,
            LINE_WORD_V,
            LINE_WORD_WIDTH,
            LINE_WORD_HEIGHT,
            com.trmtgtnh.client.settings.SettingsGeometry.WORDMARK_WIDTH,
            com.trmtgtnh.client.settings.SettingsGeometry.WORDMARK_HEIGHT);
    }

    /** The wordmark, at one texture pixel to one GUI pixel. */
    static void wordmark(int x, int y, boolean compact) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(compact ? WORDMARK_SMALL : WORDMARK);
        int w = compact ? com.trmtgtnh.client.settings.SettingsGeometry.WORDMARK_SMALL_WIDTH
            : com.trmtgtnh.client.settings.SettingsGeometry.WORDMARK_WIDTH;
        int h = compact ? com.trmtgtnh.client.settings.SettingsGeometry.WORDMARK_SMALL_HEIGHT
            : com.trmtgtnh.client.settings.SettingsGeometry.WORDMARK_HEIGHT;
        blended(x, y, 0, 0, w, h, w, h);
    }

    /** One of the settings screen's icons, by its cell in the atlas (spec 1.3). */
    static void settingsIcon(int cell, int x, int y) {
        if (cell < 0) return;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(ICONS);
        int cells = com.trmtgtnh.client.settings.Canvas.ICON_ATLAS_WIDTH
            / com.trmtgtnh.client.settings.Canvas.ICON_CELL;
        int size = com.trmtgtnh.client.settings.Canvas.ICON_CELL;
        blended(
            x,
            y,
            (cell % cells) * size,
            (cell / cells) * size,
            size,
            size,
            com.trmtgtnh.client.settings.Canvas.ICON_ATLAS_WIDTH,
            com.trmtgtnh.client.settings.Canvas.ICON_ATLAS_HEIGHT);
    }

    /** Part of the bound texture, drawn with its alpha. */
    private static void blended(int x, int y, int u, int v, int w, int h, int textureW, int textureH) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, w, h, textureW, textureH);
        GlStateManager.disableBlend();
    }

    private static ResourceLocation upload(TextureManager textures, String name, int[] pixels, int size) {
        DynamicTexture texture = new DynamicTexture(size, size);
        System.arraycopy(pixels, 0, texture.getTextureData(), 0, pixels.length);
        texture.updateDynamicTexture();
        return textures.getDynamicTextureLocation(name, texture);
    }

    /** Composed again, from the new pack, the next time anything is drawn after the resources reload. */
    private static void listen(Minecraft mc) {
        if (listening || !(mc.getResourceManager() instanceof IReloadableResourceManager)) return;
        listening = true;
        ((IReloadableResourceManager) mc.getResourceManager())
            .registerReloadListener(new IResourceManagerReloadListener() {

                @Override
                public void onResourceManagerReload(IResourceManager resources) {
                    forget();
                }
            });
    }

    private static void forget() {
        TextureManager textures = Minecraft.getMinecraft()
            .getTextureManager();
        if (squares != null && textures != null) {
            for (ResourceLocation[] wear : squares) {
                for (ResourceLocation one : wear) {
                    textures.deleteTexture(one);
                }
            }
        }
        if (icon != null && textures != null) textures.deleteTexture(icon);
        if (dirtBg != null && textures != null) textures.deleteTexture(dirtBg);
        if (dirtPanel != null && textures != null) textures.deleteTexture(dirtPanel);
        squares = null;
        icon = null;
        dirtBg = null;
        dirtPanel = null;
    }

    private static BufferedImage read(IResourceManager resources, ResourceLocation location) {
        IResource resource = null;
        try {
            resource = resources.getResource(location);
            return ImageIO.read(resource.getInputStream());
        } catch (Exception unreadable) {
            Trmt.LOG.debug("The settings screen could not read {}; drawing it plain", location, unreadable);
            return null;
        } finally {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception ignored) {
                    // Read or not, there is nothing more to do with it.
                }
            }
        }
    }

    /**
     * A picture's first frame as {@code size} by {@code size} pixels, by nearest pixel: an animated file is a column
     * of frames, so only its top square is the picture, and a dirt drawn at another resolution than the grass is
     * scaled to the grass's. A picture that could not be read is one color.
     */
    private static int[] pixels(BufferedImage image, int size, int fallback) {
        int[] out = new int[size * size];
        if (image == null) {
            Arrays.fill(out, fallback);
            return out;
        }
        int side = Math.max(1, Math.min(image.getWidth(), image.getHeight()));
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = image.getRGB(x * side / size, y * side / size);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * One square of ground at any size, cut to {@code width} from the left, so a row of squares can end part way
     * through one.
     */
    static void square(int x, int y, int size, int width, int wear, int turn) {
        if (width <= 0 || size <= 0) return;
        compose();
        int shown = Math.min(width, size);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(squares[clampWear(wear)][turn & 3]);
        quad(x, y, shown, size, shown / (double) size, 1D);
    }

    /**
     * The whole screen's lawn, with one path worn across it from left to right: a gentle curve, worn deepest along
     * its middle and less the further a square lies from it, the way a real path wears.
     */
    static void lawn(int width, int height) {
        for (int top = 0, row = 0; top < height; top += LAWN_SQUARE, row++) {
            for (int left = 0, column = 0; left < width; left += LAWN_SQUARE, column++) {
                int middleX = left + LAWN_SQUARE / 2;
                int middleY = top + LAWN_SQUARE / 2;
                square(
                    left,
                    top,
                    LAWN_SQUARE,
                    LAWN_SQUARE,
                    wearOnThePath(middleX, middleY, width, height),
                    turnAt(column, row));
            }
        }
    }

    /** A heading on a strip of path: worn deepest under the words and less towards each end. */
    static void pathStrip(int x, int y, int width, int height) {
        int squares = Math.max(1, (width + height - 1) / height);
        for (int at = 0; at < squares; at++) {
            int left = x + at * height;
            int fromEnd = Math.min(at, squares - 1 - at);
            int wear = fromEnd == 0 ? 2 : fromEnd == 1 ? 3 : MOST_WORN;
            square(left, y, height, x + width - left, wear, turnAt(at, y));
        }
        outline(x - 1, y - 1, width + 2, height + 2, 0x80000000);
    }

    /**
     * A slider's track, worn grass (0.9.228; Xep, 2026-10-26: "It should show the grass texture for that version,
     * degrading to fully worn (top layer only) grass as you slide"): a row of cells, one a stage of the track
     * ({@code Slider.stagesFor}), each this game's own grass top drawn at one of this mod's own worn-grass pictures -
     * the very ones the ground is drawn with - from untouched grass to the last picture before the ground sinks. Cells
     * behind the handle fully worn, cells ahead untouched, and the cell under it worn as far as the handle has come into
     * it ({@code Slider.cellWear}). A cell wider than the bar is high is laid square by square. 1.7.10's.
     */
    static void wornBar(int x, int y, int width, int height, double fraction, int stages) {
        int[] edges = cellEdges(width, stages);
        double[] wear = com.trmtgtnh.client.settings.Slider.cellWear(edges.length - 1, fraction);
        int last = com.trmtgtnh.erosion.ErosionChain.lastFlatIndex(com.trmtgtnh.surface.SurfaceFamily.GRASS);
        for (int at = 0; at + 1 < edges.length; at++) {
            int left = edges[at];
            int right = edges[at + 1];
            for (int from = left; from < right; from += height) {
                grassAt(x + from, y, height, Math.min(height, right - from), wear[at], last, at);
            }
        }
    }

    /**
     * The step of grass's wear chain a cell worn {@code wear} of the way is drawn as: -1, the game's own grass top, for
     * an untouched cell, else step {@code round(wear * last)}, where {@code last} is the chain's last step before the
     * ground sinks.
     */
    static int pictureFor(double wear, int last) {
        if (wear <= 0D || last < 0) return -1;
        int placed = (int) Math.round(Math.min(1D, wear) * last);
        return placed < 0 ? 0 : placed;
    }

    /**
     * One square of grass worn {@code wear} of the way, cut to {@code width}, drawn as the ground draws that step of
     * grass's wear chain: from the block atlas, the grass top or this mod's own worn picture for the step's appearance
     * and gradation ({@code WearSteps.chainPicture}), in the game's default grass color - as an item of grass is -
     * while the step still shows grass, and untinted once it has worn to another appearance, as the ground stops
     * tinting it. Before the pictures are stitched, the screen's own composed squares stand in. 1.7.10's.
     */
    private static void grassAt(int x, int y, int size, int width, double wear, int last, int turn) {
        if (width <= 0 || size <= 0) return;
        com.trmtgtnh.surface.SurfaceFamily grass = com.trmtgtnh.surface.SurfaceFamily.GRASS;
        int step = pictureFor(wear, last);
        com.trmtgtnh.surface.SurfaceFamily appearance = step < 0 ? grass
            : com.trmtgtnh.erosion.ErosionChain.familyAt(grass, step);
        int picture = step < 0 ? -1 : com.trmtgtnh.client.model.WearSteps.chainPicture(grass, step);
        net.minecraft.client.renderer.texture.TextureAtlasSprite icon = step < 0 ? Minecraft.getMinecraft()
            .getTextureMapBlocks()
            .getAtlasSprite("minecraft:blocks/grass_top")
            : appearance == null || picture < 0 ? null
                : com.trmtgtnh.client.texture.WearTextures
                    .icon(net.minecraft.init.Blocks.GRASS, 0, grass, appearance, picture, turn);
        if (icon == null) {
            square(x, y, size, width, (int) Math.round(Math.max(0D, wear) * MOST_WORN), turn);
            return;
        }
        int shown = Math.min(width, size);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        int color = appearance == grass ? grassColor() : 0xFFFFFFFF;
        GlStateManager.color(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, 1F);
        GlStateManager.enableTexture2D();
        double u0 = icon.getMinU();
        double u1 = u0 + (icon.getMaxU() - u0) * shown / size;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + size, 0D)
            .tex(u0, icon.getMaxV())
            .endVertex();
        buffer.pos(x + shown, y + size, 0D)
            .tex(u1, icon.getMaxV())
            .endVertex();
        buffer.pos(x + shown, y, 0D)
            .tex(u1, icon.getMinV())
            .endVertex();
        buffer.pos(x, y, 0D)
            .tex(u0, icon.getMinV())
            .endVertex();
        tessellator.draw();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /** The track's cells as {@code stages + 1} edges from 0 to {@code width}, as even as whole pixels allow. */
    static int[] cellEdges(int width, int stages) {
        int cells = Math.max(1, Math.min(stages, Math.max(1, width)));
        int[] edges = new int[cells + 1];
        for (int at = 0; at <= cells; at++) edges[at] = at * width / cells;
        return edges;
    }

    /** The Options-screen button's icon, sixteen pixels square. */
    static void icon(int x, int y) {
        compose();
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(icon);
        quad(x, y, 16, 16, 1D, 1D);
    }

    /** The title's tamper, sixteen pixels square. */
    static void tamper(int x, int y) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(ICON);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0F, 0F, 16, 16, 16F, 16F);
        GlStateManager.disableBlend();
    }

    /**
     * The state a screen's background is drawn in, whatever the frame before left: no lighting, no fog, plain white.
     * The game's own backgrounds set it so before they draw ({@code GuiScreen.drawBackground}), and the hover a frame
     * ends with turns the item lighting on ({@code GuiUtils.drawHoveringText}).
     */
    static void plainState() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableRescaleNormal();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /** A one-pixel frame round a box. */
    static void outline(int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + 1, color);
        Gui.drawRect(x, y + height - 1, x + width, y + height, color);
        Gui.drawRect(x, y + 1, x + 1, y + height - 1, color);
        Gui.drawRect(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** The bound texture over a box, from its top left to {@code u, v} of it. Opaque, so the alpha test keeps it. */
    private static void quad(int x, int y, int width, int height, double u, double v) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0D)
            .tex(0D, v)
            .endVertex();
        buffer.pos(x + width, y + height, 0D)
            .tex(u, v)
            .endVertex();
        buffer.pos(x + width, y, 0D)
            .tex(u, 0D)
            .endVertex();
        buffer.pos(x, y, 0D)
            .tex(0D, 0D)
            .endVertex();
        tessellator.draw();
    }
}
