package com.trmtgtnh.client.gui;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.GrassColor;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.SettingsGeometry;

/**
 * The pictures the mod's own settings screen is drawn with: a lawn with a path worn across it, a heading laid on a
 * strip of path, a bar that wears from grass to dirt, the screen's dark dirt, the wordmark and the screen's icons
 * (0.9.228).
 *
 * <p>
 * Xep's brief for 26.x's screen without Cloth Config (0.9.227), carried here in 0.9.228: "a little more themed towards
 * my mod" - worn dirt and grass behind it, headers on a path strip, sliders that wear grass to dirt, the mod's icon in
 * the title. Every lawn picture is one this jar already carries for the worn ground itself, the same files 26.3
 * carries: {@code grass_top_<n>_r<k>} is grass at wear {@code n} of four (its pixels thin as {@code n} rises, so the
 * dirt beneath shows through) in four turns, and {@code dirt_all_0_r<k>} the earth under it. The grass is grey in the
 * file, as the game's own is, and is drawn tinted with the game's default grass color. The 1.7.10 edition's jar
 * carries none, and composes the same squares from the game's own grass; the look is the same.
 *
 * <p>
 * <strong>The screen's dark dirt is composed from the pack's own dirt</strong> (spec M12, 1.3), as in every edition:
 * the game's {@code textures/block/dirt.png} with each channel times {@code DIRT_BG} or {@code DIRT_PANEL}, made into
 * a texture of the screen's own the first time it is drawn and again after the resources reload, so it follows
 * whatever resource pack is loaded.
 *
 * <p>
 * Drawn with 1.20.1's {@code GuiGraphics}: each picture blitted by its location, the grass over the earth with blending
 * on (its thinned pixels are the transparent ones) and multiplied by the tint through the shader color, which is put
 * back to white after every tinted picture so nothing drawn next comes out green.
 */
public final class WornPathArt {

    /** The most worn picture there is: {@code grass_top_4}. */
    static final int MOST_WORN = 4;

    /** The size a square of lawn is drawn at behind the screen: twice the picture's own sixteen. */
    static final int LAWN_SQUARE = 32;

    /** Every picture is sixteen by sixteen. */
    private static final int PICTURE = 16;

    /** What the screen's title is drawn beside: the tamper, the first tool the mod gives a player. */
    static final ResourceLocation ICON = new ResourceLocation(Trmt.MODID, "textures/item/tamper.png");

    /** The game's own dirt, which the screen's dark dirt is composed from. */
    static final ResourceLocation DIRT = new ResourceLocation("textures/block/dirt.png");

    /** The wordmark, regular and compact (spec 1.1), and the settings screen's icons (1.3), shipped in the jar. */
    static final ResourceLocation WORDMARK = new ResourceLocation(Trmt.MODID, "textures/gui/wordmark.png");

    static final ResourceLocation WORDMARK_SMALL = new ResourceLocation(Trmt.MODID, "textures/gui/wordmark_small.png");

    static final ResourceLocation ICONS = new ResourceLocation(Trmt.MODID, "textures/gui/settings_icons.png");

    /** The size the dark dirt is tiled at: the pack's sixteen pixels to sixteen GUI pixels. */
    static final int DIRT_TILE = 16;

    /** The settings screen's two dark dirts, composed from the pack; null until first drawn and after a reload. */
    private static ResourceLocation dirtBg;

    private static ResourceLocation dirtPanel;

    private static boolean listening;

    private WornPathArt() {}

    // ------------------------------------------------------------------
    // Composing - plain, so a test can ask it
    // ------------------------------------------------------------------

    /** Grass at a wear of nought (untouched) to four (nearly gone), in one of its four turns. */
    static ResourceLocation grass(int wear, int turn) {
        return new ResourceLocation(Trmt.MODID, "textures/block/grass_top_" + wear + "_r" + (turn & 3) + ".png");
    }

    /** The earth beneath, in one of its four turns. */
    static ResourceLocation dirt(int turn) {
        return new ResourceLocation(Trmt.MODID, "textures/block/dirt_all_0_r" + (turn & 3) + ".png");
    }

    /**
     * The game's default grass color, opaque.
     */
    static int grassColor() {
        return 0xFF000000 | GrassColor.getDefaultColor();
    }

    /** The tan a path top is drawn toward: the newer games' own path top, read by eye, as one color. */
    static final int PATH_TONE = 0x947A50;

    /**
     * A path top made from a pixel of dirt, as the 1.7.10 edition makes its Options button's path from the pack's dirt:
     * drawn most of the way toward a path's tan - lighter, warmer and flatter, as trodden earth is. The depth preset's
     * picture draws its rut's floor in it ({@code ChooserPictures.Depth}), the same color in every edition.
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
     * The settings screen's dark dirt (spec M12, 1.3): the pack's own dirt with each channel times {@code factor},
     * opaque - {@code DIRT_BG} at 0.22 behind the panel, {@code DIRT_PANEL} at 0.16 inside it. Composed from the pack,
     * as the 1.7.10 edition's is, so it follows the resource pack.
     */
    static int[] darkened(int[] dirt, float factor) {
        int[] out = new int[dirt.length];
        for (int i = 0; i < dirt.length; i++) {
            out[i] = Canvas.darken(dirt[i] & 0xFFFFFF, factor);
        }
        return out;
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
    // The pack's dirt, read and made into textures of the screen's own
    // ------------------------------------------------------------------

    /**
     * How long composing took in the frame the settingsperf part is reading (0.9.228): reset at each frame's start by
     * {@code SettingsPictures.frame}, and nought in every frame but the one that composed.
     */
    static long composeNanos;

    /** The two dark dirts, composed from what the resource pack holds now, once until the pack changes. */
    private static void compose() {
        if (dirtBg != null) return;
        long started = System.nanoTime();
        try {
            composeNow();
        } finally {
            composeNanos += System.nanoTime() - started;
        }
    }

    private static void composeNow() {
        Minecraft mc = Minecraft.getInstance();
        listen(mc);
        BufferedImage dirtImage = read(mc.getResourceManager(), DIRT);
        // The picture's own resolution, so a 32-pixel pack's dirt is drawn at 32 pixels; sixteen when it cannot be
        // read.
        int size = dirtImage != null ? Math.min(dirtImage.getWidth(), dirtImage.getHeight()) : 16;
        size = Math.max(1, Math.min(size, 256));
        int[] dirt = pixels(dirtImage, size, 0xFF866043);
        TextureManager textures = mc.getTextureManager();
        dirtBg = upload(textures, "trmtgtnh_settings_dirt_bg", darkened(dirt, Canvas.DIRT_BG_FACTOR), size);
        dirtPanel = upload(textures, "trmtgtnh_settings_dirt_panel", darkened(dirt, Canvas.DIRT_PANEL_FACTOR), size);
    }

    /** The pixels as a texture of the screen's own, red and blue put the way the game's images hold them. */
    private static ResourceLocation upload(TextureManager textures, String name, int[] argb, int size) {
        NativeImage image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int one = argb[y * size + x];
                image.setPixelRGBA(x, y, (one & 0xFF00FF00) | ((one >> 16) & 0xFF) | ((one & 0xFF) << 16));
            }
        }
        return textures.register(name, new DynamicTexture(image));
    }

    /** Composed again, from the new pack, the next time anything is drawn after the resources reload. */
    private static void listen(Minecraft mc) {
        if (listening || !(mc.getResourceManager() instanceof ReloadableResourceManager)) return;
        listening = true;
        ((ReloadableResourceManager) mc.getResourceManager())
            .registerReloadListener((ResourceManagerReloadListener) resources -> forget());
    }

    private static void forget() {
        TextureManager textures = Minecraft.getInstance()
            .getTextureManager();
        if (dirtBg != null && textures != null) textures.release(dirtBg);
        if (dirtPanel != null && textures != null) textures.release(dirtPanel);
        dirtBg = null;
        dirtPanel = null;
    }

    private static BufferedImage read(ResourceManager resources, ResourceLocation location) {
        try (InputStream stream = resources.open(location)) {
            return ImageIO.read(stream);
        } catch (Exception unreadable) {
            Trmt.LOG.debug("The settings screen could not read {}; drawing it plain", location, unreadable);
            return null;
        }
    }

    /**
     * A picture's first frame as {@code size} by {@code size} pixels, by nearest pixel: an animated file is a column
     * of frames, so only its top square is the picture. A picture that could not be read is one color.
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

    /** The next picture white or multiplied by an opaque color, with blending on for its transparent pixels. */
    static void tint(GuiGraphics graphics, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, 1F);
    }

    /** Puts the color back to white once a tinted picture has been drawn. */
    static void untint(GuiGraphics graphics) {
        graphics.setColor(1F, 1F, 1F, 1F);
    }

    /** One whole picture, drawn at any size. */
    static void picture(GuiGraphics graphics, ResourceLocation picture, int x, int y, int size) {
        tint(graphics, 0xFFFFFFFF);
        graphics.blit(picture, x, y, size, size, 0F, 0F, PICTURE, PICTURE, PICTURE, PICTURE);
    }

    /**
     * One square of ground: earth, and grass over it worn as far as {@code wear} says. Drawn at any size and cut to
     * {@code width} from the left, so a row of squares can end part way through one.
     */
    static void square(GuiGraphics graphics, int x, int y, int size, int width, int wear, int turn) {
        squareCut(graphics, x, y, size, width, size, wear, turn);
    }

    /** A square of ground cut to {@code width} across and {@code shownHeight} down from its top. */
    private static void squareCut(GuiGraphics graphics, int x, int y, int size, int width, int shownHeight, int wear,
        int turn) {
        if (width <= 0 || size <= 0 || shownHeight <= 0) return;
        int shown = Math.min(width, size);
        int down = Math.min(shownHeight, size);
        int sourceW = Math.max(1, Math.round(PICTURE * (shown / (float) size)));
        int sourceH = Math.max(1, Math.round(PICTURE * (down / (float) size)));
        tint(graphics, 0xFFFFFFFF);
        graphics.blit(dirt(turn), x, y, shown, down, 0F, 0F, sourceW, sourceH, PICTURE, PICTURE);
        tint(graphics, grassColor());
        graphics.blit(grass(clampWear(wear), turn), x, y, shown, down, 0F, 0F, sourceW, sourceH, PICTURE, PICTURE);
        untint(graphics);
    }

    /**
     * The whole screen's lawn, with one path worn across it from left to right.
     *
     * <p>
     * The path is a gentle curve, worn deepest along its middle and less the further a square lies from it, the way
     * a real path wears: four in the middle, then three, two and one, and untouched grass beyond. Each square's turn
     * is fixed by where it is, so the lawn does not shimmer from frame to frame.
     */
    static void lawn(GuiGraphics graphics, int width, int height) {
        for (int top = 0, row = 0; top < height; top += LAWN_SQUARE, row++) {
            for (int left = 0, column = 0; left < width; left += LAWN_SQUARE, column++) {
                int middleX = left + LAWN_SQUARE / 2;
                int middleY = top + LAWN_SQUARE / 2;
                square(graphics, left, top, LAWN_SQUARE, LAWN_SQUARE, wearOnThePath(middleX, middleY, width, height),
                    turnAt(column, row));
            }
        }
    }

    /**
     * The header's lawn band (spec 1.1): squares of lawn across the full width, a path worn along it, cut to the band.
     */
    static void lawnBand(GuiGraphics graphics, int width, int height) {
        for (int top = 0, row = 0; top < height; top += LAWN_SQUARE, row++) {
            for (int left = 0, column = 0; left < width; left += LAWN_SQUARE, column++) {
                int shown = Math.min(LAWN_SQUARE, height - top);
                int wear = wearOnThePath(left + LAWN_SQUARE / 2, top + LAWN_SQUARE / 2, width, height * 2);
                squareCut(graphics, left, top, LAWN_SQUARE, LAWN_SQUARE, shown, wear, turnAt(column, row));
            }
        }
    }

    /**
     * A heading on a strip of path: worn deepest under the words and less towards each end, the strip's own height
     * square by square.
     */
    static void pathStrip(GuiGraphics graphics, int x, int y, int width, int height) {
        int squares = Math.max(1, (width + height - 1) / height);
        for (int at = 0; at < squares; at++) {
            int left = x + at * height;
            int fromEnd = Math.min(at, squares - 1 - at);
            int wear = fromEnd == 0 ? 2 : fromEnd == 1 ? 3 : MOST_WORN;
            square(graphics, left, y, height, x + width - left, wear, turnAt(at, y));
        }
        outline(graphics, x - 1, y - 1, width + 2, height + 2, 0x80000000);
    }

    /**
     * A slider's track, worn grass (0.9.228; Xep, 2026-10-26: "It should show the grass texture for that version,
     * degrading to fully worn (top layer only) grass as you slide"): a row of cells, one a stage of the track
     * ({@code Slider.stagesFor}), each this game's own grass top drawn as the ground draws a step of grass's wear
     * chain, from untouched grass to the last step before the ground sinks. Cells behind the handle fully worn, cells
     * ahead untouched, and the cell under it worn as far as the handle has come into it ({@code Slider.cellWear}). A
     * cell wider than the bar is high is laid square by square. 1.7.10's.
     */
    static void wornBar(GuiGraphics graphics, int x, int y, int width, int height, double fraction, int stages) {
        int[] edges = cellEdges(width, stages);
        double[] wear = com.trmtgtnh.client.settings.Slider.cellWear(edges.length - 1, fraction);
        int last = com.trmtgtnh.erosion.ErosionChain.lastFlatIndex(com.trmtgtnh.surface.SurfaceFamily.GRASS);
        for (int at = 0; at + 1 < edges.length; at++) {
            int left = edges[at];
            int right = edges[at + 1];
            for (int from = left; from < right; from += height) {
                grassAt(graphics, x + from, y, height, Math.min(height, right - from), wear[at], last, at);
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
     * tinting it. Cut by a scissor, since a sprite is drawn whole. Before the pictures are stitched, the screen's own
     * composed squares stand in. 1.7.10's.
     */
    private static void grassAt(GuiGraphics graphics, int x, int y, int size, int width, double wear, int last,
        int turn) {
        if (width <= 0 || size <= 0) return;
        com.trmtgtnh.surface.SurfaceFamily grass = com.trmtgtnh.surface.SurfaceFamily.GRASS;
        int step = pictureFor(wear, last);
        com.trmtgtnh.surface.SurfaceFamily appearance = step < 0 ? grass
            : com.trmtgtnh.erosion.ErosionChain.familyAt(grass, step);
        int picture = step < 0 ? -1 : com.trmtgtnh.client.model.WearSteps.chainPicture(grass, step);
        net.minecraft.client.renderer.texture.TextureAtlasSprite icon = step < 0 ? grassTop()
            : appearance == null || picture < 0 ? null
                : com.trmtgtnh.client.texture.WearTextures
                    .icon(net.minecraft.world.level.block.Blocks.GRASS_BLOCK, 0, grass, appearance, picture, turn);
        if (icon == null) {
            square(graphics, x, y, size, width, (int) Math.round(Math.max(0D, wear) * MOST_WORN), turn);
            return;
        }
        int shown = Math.min(width, size);
        tint(graphics, appearance == grass ? grassColor() : 0xFFFFFFFF);
        graphics.enableScissor(x, y, x + shown, y + size);
        graphics.blit(x, y, 0, size, size, icon);
        graphics.disableScissor();
        untint(graphics);
    }

    /** The game's own grass top on the block atlas, or null where the atlas has none of that name. */
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite grassTop() {
        net.minecraft.client.renderer.texture.TextureAtlasSprite top = Minecraft.getInstance()
            .getModelManager()
            .getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
            .getSprite(new ResourceLocation("minecraft", "block/grass_block_top"));
        return top == null || net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()
            .equals(
                top.contents()
                    .name()) ? null : top;
    }

    /** The track's cells as {@code stages + 1} edges from 0 to {@code width}, as even as whole pixels allow. */
    static int[] cellEdges(int width, int stages) {
        int cells = Math.max(1, Math.min(stages, Math.max(1, width)));
        int[] edges = new int[cells + 1];
        for (int at = 0; at <= cells; at++) edges[at] = at * width / cells;
        return edges;
    }

    /** A box of the screen's dark dirt: {@code DIRT_BG}, or {@code DIRT_PANEL} inside the list panel. */
    static void dirt(GuiGraphics graphics, int x0, int y0, int x1, int y1, boolean panel) {
        if (x1 <= x0 || y1 <= y0) return;
        compose();
        tint(graphics, 0xFFFFFFFF);
        // The composed texture repeats, so one quad tiles the whole box, sixteen GUI pixels a tile.
        graphics.blit(panel ? dirtPanel : dirtBg, x0, y0, x1 - x0, y1 - y0, x0, y0, x1 - x0, y1 - y0, DIRT_TILE,
            DIRT_TILE);
        RenderSystem.disableBlend();
    }

    /** The wordmark, at one texture pixel to one GUI pixel. */
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
    static int lineLeft(SettingsGeometry.Frame frame) {
        if (!frame.compact) return -1;
        int x = (frame.width - LINE_WIDTH) / 2;
        boolean fits = x >= frame.help.x1 + LINE_CLEAR && x + LINE_WIDTH <= frame.headerSlots[2].x0 - LINE_CLEAR;
        return fits ? x : -1;
    }

    /** The header's wordmark: the regular one, or in a compact header the one-line name where it fits, else TRMT. */
    static void wordmark(GuiGraphics graphics, SettingsGeometry.Frame frame) {
        int line = lineLeft(frame);
        if (line < 0) {
            wordmark(graphics, frame.wordmark.x0, frame.wordmark.y0, frame.compact);
            return;
        }
        int y = frame.wordmark.y0;
        tint(graphics, 0xFFFFFFFF);
        graphics.blit(
            WORDMARK_SMALL,
            line,
            y,
            LINE_TRMT_U,
            0F,
            LINE_TRMT_WIDTH,
            SettingsGeometry.WORDMARK_SMALL_HEIGHT,
            SettingsGeometry.WORDMARK_SMALL_WIDTH,
            SettingsGeometry.WORDMARK_SMALL_HEIGHT);
        graphics.blit(
            WORDMARK,
            line + LINE_TRMT_WIDTH + LINE_GAP,
            y + LINE_WORD_DROP,
            LINE_WORD_U,
            LINE_WORD_V,
            LINE_WORD_WIDTH,
            LINE_WORD_HEIGHT,
            SettingsGeometry.WORDMARK_WIDTH,
            SettingsGeometry.WORDMARK_HEIGHT);
    }

    static void wordmark(GuiGraphics graphics, int x, int y, boolean compact) {
        int w = compact ? SettingsGeometry.WORDMARK_SMALL_WIDTH : SettingsGeometry.WORDMARK_WIDTH;
        int h = compact ? SettingsGeometry.WORDMARK_SMALL_HEIGHT : SettingsGeometry.WORDMARK_HEIGHT;
        tint(graphics, 0xFFFFFFFF);
        graphics.blit(compact ? WORDMARK_SMALL : WORDMARK, x, y, 0F, 0F, w, h, w, h);
    }

    /** One of the settings screen's icons, by its cell in the atlas (spec 1.3). */
    static void settingsIcon(GuiGraphics graphics, int cell, int x, int y) {
        if (cell < 0) return;
        int cells = Canvas.ICON_ATLAS_WIDTH / Canvas.ICON_CELL;
        int size = Canvas.ICON_CELL;
        tint(graphics, 0xFFFFFFFF);
        graphics.blit(
            ICONS,
            x,
            y,
            (cell % cells) * size,
            (cell / cells) * size,
            size,
            size,
            Canvas.ICON_ATLAS_WIDTH,
            Canvas.ICON_ATLAS_HEIGHT);
    }

    /** The title's tamper, sixteen pixels square. */
    static void tamper(GuiGraphics graphics, int x, int y) {
        tint(graphics, 0xFFFFFFFF);
        graphics.blit(ICON, x, y, 0F, 0F, 16, 16, 16, 16);
    }

    /**
     * The state a screen's background is drawn in, whatever the frame before left: plain white, and the depth test
     * off, as the game's own backgrounds draw - an item or a mob drawn in a list leaves it on. 1.20.1 has no fixed
     * lighting or fog to turn off.
     */
    static void plainState() {
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /**
     * A one-pixel frame inside {@code x, y, width, height}: {@code GuiGraphics.renderOutline}.
     */
    static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.renderOutline(x, y, width, height, color);
    }
}
