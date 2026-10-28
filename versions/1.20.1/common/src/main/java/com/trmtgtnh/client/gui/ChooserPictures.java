package com.trmtgtnh.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.command.CommandTrmt;
import com.trmtgtnh.config.Presets;
import com.trmtgtnh.erosion.CostCurve;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The pictures beside a chooser's values (0.9.228, spec M23.2 and S3; Xep: "Perhaps images where applicable to help you
 * understand"): a family's wear look drawn on its own road block three quarters through the run, a cost curve as a
 * column of gold for each of sixteen steps, the depth a preset sinks a rut to, and the steps a quality preset wears
 * grass through. The other choosers have none: a word says as much as a picture of a word would.
 *
 * <p>
 * Each picture is a {@link Picture}, which the settings canvas draws at 16 by 16, or at 32 by 32 in a dropdown's detail
 * panel by scaling it. Made without touching the game - a test can ask which chooser has which - and drawn only when
 * the screen draws. The arithmetic each draws is plain ({@link #curveHeights}, {@link #qualityLevels},
 * {@link #depthPixels}) and is the game's own: {@code CostCurve.at}, {@code Presets.quality} and the depth leans. The
 * 1.7.10 edition's pictures, drawn through this game's {@code GuiGraphics}.
 */
final class ChooserPictures {

    /** A picture the settings canvas draws: 16 by 16 GUI pixels from its top left. */
    interface Picture {

        void draw(GuiGraphics graphics, int x, int y);
    }

    private ChooserPictures() {}

    /** One preview maker for every look picture: it keeps its textures until the family changes. */
    private static final PatternPreview PREVIEWS = new PatternPreview();

    /** The setting a family's wear look is chosen with, and its cost curve. */
    static final String WEAR_PATTERN = "wearPattern";

    static final String COST_CURVE = "costCurve";

    /** The picture beside {@code value} of the chooser {@code category.key}, or null where it has none. */
    static Picture of(String category, String key, String value) {
        if (value == null) return null;
        SurfaceFamily family = SettingsModel.familyOf(category);
        if (family != null && WEAR_PATTERN.equals(key)) return new Look(family, PatternPreview.canonical(value));
        if (family != null && COST_CURVE.equals(key)) {
            CostCurve curve = CostCurve.byKey(value);
            return curve == null ? null : new Curve(curve);
        }
        if (Presets.CATEGORY.equals(category) && "depth".equals(key) && depthPixels(value) >= 0) {
            return new Depth(value);
        }
        if (Presets.CATEGORY.equals(category) && "quality".equals(key) && !Presets.CUSTOM.equals(value)) {
            return new Quality(value);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The arithmetic, plain
    // ------------------------------------------------------------------

    /**
     * A cost curve as sixteen columns (spec S3.2): what each of sixteen equal steps along a run costs, by the curve's
     * own arithmetic ({@link CostCurve#at}), as a height of at most {@code height} pixels, the dearest step the
     * tallest.
     */
    static int[] curveHeights(CostCurve curve, int columns, int height) {
        float[] cost = new float[columns];
        float most = 0f;
        for (int i = 0; i < columns; i++) {
            cost[i] = curve.at(i, columns);
            most = Math.max(most, cost[i]);
        }
        int[] out = new int[columns];
        for (int i = 0; i < columns; i++) {
            out[i] = most <= 0f ? 1 : Math.max(1, Math.round(height * cost[i] / most));
        }
        return out;
    }

    /**
     * How worn each of four strips of grass is drawn for a quality preset (spec M23.2: "a 4-step strip of grass worn at
     * that preset's gradations"): the strips stand a quarter, a half, three quarters and the whole of the way along a
     * run, each held to the nearest picture a run of that preset's gradations has. Few gradations put two strips on one
     * picture, so Potato's strip jumps where Default's steps evenly. Wear levels are the lawn's, nought to four.
     */
    static int[] qualityLevels(int gradations) {
        double pictures = Math.max(1D, gradations / 16D);
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            double along = (i + 1) / 4D;
            double held = Math.round(along * pictures) / pictures;
            out[i] = (int) Math.max(0, Math.min(WornPathArt.MOST_WORN, Math.round(held * WornPathArt.MOST_WORN)));
        }
        return out;
    }

    /**
     * How far below the grass a depth preset's rut is drawn, in the picture's pixels: four at Default, scaled by the
     * rung's lean as {@code Presets} scales every family's depth (none 0, shallow a half, deep one and a half, deepest
     * two). -1 for Custom and anything else, which have no picture.
     */
    static int depthPixels(String rung) {
        if ("none".equals(rung)) return 0;
        if ("shallow".equals(rung)) return 2;
        if ("default".equals(rung)) return 4;
        if ("deep".equals(rung)) return 6;
        if ("deepest".equals(rung)) return 8;
        return -1;
    }

    // ------------------------------------------------------------------
    // The pictures
    // ------------------------------------------------------------------

    /** A family's wear look, on its own road block, three quarters through the run (spec M23.2, S3.1). */
    static final class Look implements Picture {

        final SurfaceFamily family;

        final String pattern;

        Look(SurfaceFamily family, String pattern) {
            this.family = family;
            this.pattern = pattern;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            drawLook(graphics, family, pattern, PatternPreview.THREE_QUARTERS, x, y);
        }

        @Override
        public String toString() {
            return "look:" + family.key() + "/" + pattern;
        }
    }

    /**
     * One look on a family's road block, at one of the Wear Editor's points along the run, 16 by 16: the block's own
     * faces handed to the very operators the atlas runs ({@link PatternPreview}), tinted as the block is, as the Wear
     * Editor's look picker draws them.
     */
    static void drawLook(GuiGraphics graphics, SurfaceFamily family, String pattern, int step, int x, int y) {
        Block block = CommandTrmt.roadBlock(family);
        ItemStack shown = block == null ? null : new ItemStack(block);
        ResourceLocation at = PREVIEWS.of(family, shown, pattern, step);
        if (at == null) return;
        // The block's own multiplier, as the Wear Editor tints its looks: grass's top is grey on disk.
        int tint = shown == null ? 0xFFFFFF : ItemTints.of(shown);
        RenderSystem.enableBlend();
        graphics.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, 1f);
        graphics.blit(at, x, y, 16, 16, 0f, 0f, 16, 16, 16, 16);
        graphics.setColor(1F, 1F, 1F, 1F);
    }

    /**
     * Which point along the run each square of a family page's band is drawn at (spec S3.3): the band is a path
     * across the page's head, its ends barely worn and its middle worn through - a sixteenth of the way, a half, three
     * quarters, the whole, as {@code WornPathArt.pathStrip} wears its lawn deeper toward the middle. As
     * {@link PatternPreview}'s steps.
     */
    static int[] bandSteps(int squares) {
        int[] byDepth = { 0, 1, PatternPreview.THREE_QUARTERS, 2 };
        int[] out = new int[Math.max(0, squares)];
        for (int i = 0; i < out.length; i++) {
            int fromEnd = Math.min(i, out.length - 1 - i);
            out[i] = byDepth[Math.min(fromEnd, byDepth.length - 1)];
        }
        return out;
    }

    /**
     * A family page's band (spec S3.3): its road block worn across a path at four wears, 16 high, in the family's own
     * look, as many squares as fit {@code width}.
     */
    static void drawBand(GuiGraphics graphics, SurfaceFamily family, String pattern, int x, int y, int width) {
        int[] steps = bandSteps(width / 16);
        int left = x + (width - steps.length * 16) / 2;
        for (int i = 0; i < steps.length; i++) {
            drawLook(graphics, family, PatternPreview.canonical(pattern), steps[i], left + i * 16, y);
        }
    }

    /** A cost curve, a column of gold a step (spec S3.2). */
    static final class Curve implements Picture {

        final CostCurve curve;

        Curve(CostCurve curve) {
            this.curve = curve;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            graphics.fill(x, y, x + 16, y + 16, Canvas.TAB_OPEN);
            int[] heights = curveHeights(curve, 16, 14);
            for (int i = 0; i < heights.length; i++) {
                graphics.fill(x + i, y + 15 - heights[i], x + i + 1, y + 15, Canvas.GOLD);
            }
            graphics.setColor(1F, 1F, 1F, 1F);
        }

        @Override
        public String toString() {
            return "curve:" + curve.key();
        }
    }

    /** A depth preset: grass either side of a rut whose path sits as far down as the rung sinks it (spec M23.2). */
    static final class Depth implements Picture {

        final String rung;

        Depth(String rung) {
            this.rung = rung;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            int earth = 0xFF866043;
            int grass = WornPathArt.grassColor();
            int path = WornPathArt.pathTop(earth);
            int sink = depthPixels(rung);
            graphics.fill(x, y, x + 16, y + 16, Canvas.TAB_OPEN);
            // The ground either side: two pixels of turf over earth, from four down.
            graphics.fill(x, y + 4, x + 16, y + 16, earth);
            graphics.fill(x, y + 4, x + 4, y + 6, grass);
            graphics.fill(x + 12, y + 4, x + 16, y + 6, grass);
            // The rut between: the sky down to its floor, the path's top on the floor.
            graphics.fill(x + 4, y + 4, x + 12, y + 4 + sink, Canvas.TAB_OPEN);
            graphics.fill(x + 4, y + 4 + sink, x + 12, y + 6 + sink, path);
            graphics.setColor(1F, 1F, 1F, 1F);
        }

        @Override
        public String toString() {
            return "depth:" + rung;
        }
    }

    /** A quality preset: four strips of grass worn as far as its gradations let a run show (spec M23.2). */
    static final class Quality implements Picture {

        final String rung;

        Quality(String rung) {
            this.rung = rung;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            int[] levels = qualityLevels(Presets.quality(rung)[0]);
            for (int i = 0; i < levels.length; i++) {
                WornPathArt.square(graphics, x + 4 * i, y, 16, 4, levels[i], 0);
            }
            graphics.setColor(1F, 1F, 1F, 1F);
        }

        @Override
        public String toString() {
            return "quality:" + rung;
        }
    }
}
