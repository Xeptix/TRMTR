package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.config.Configuration;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.Presets;
import com.trmtgtnh.config.ServerRules;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.CostCurve;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.RunShape;
import com.trmtgtnh.erosion.SinkProfile;
import com.trmtgtnh.erosion.WearMath;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.DurationFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Wear Table with the numbers turned into knobs.
 *
 * <p>
 * One family fills the screen at a time, chosen from a rail down the left, and every figure it
 * shows can be typed at rather than only read. Crossings are shown as a strip - the per-phase step
 * and the four quarters - and heal times as a column of plain-language spans you can type back
 * ({@code 1y 90d 22h}). Behind each cell is one of two scale factors on that family's config: a
 * crossings edit moves both its thresholds together, a heal edit moves its heal time. Nothing
 * reaches the config or the server until it is published, and publishing shows exactly what will
 * change first.
 *
 * <p>
 * There is no scrolling body, on purpose. A text field cannot be clipped to a scrolled viewport in
 * this version without drifting out of it, so the layout removes the viewport entirely: one family,
 * one screen, the field docked in a fixed bar at the bottom.
 */
@SideOnly(Side.CLIENT)
public class GuiWearEditor extends GuiScreen {

    private static final int ID_BACK = 1;
    private static final int ID_PUBLISH = 2;
    private static final int ID_APPLY = 3;
    private static final int ID_REVERT = 4;

    private static final int KIND_CROSS = 0;
    private static final int KIND_HEAL = 1;
    private static final int KIND_PHASES = 2;
    private static final int KIND_SINK = 3;
    private static final int KIND_CAP = 4;

    private final GuiScreen parent;

    private SurfaceFamily[] families;
    private int selected;

    /** One scale on each family's thresholds and one on its heal time; 1.0 means untouched. */
    private double[] kCross;
    private double[] mHeal;

    /**
     * The shape of each family's run as this screen has it: a length of 0, and a depth or ceiling of
     * -1, mean untouched. A depth of 0 is a real figure - ground that only discolors - which is why
     * the depth's untouched mark sits below it.
     *
     * <p>
     * Held as the numbers themselves rather than as scales, unlike the two above, because a run's
     * length is a count of steps and a depth is a count of pixels: there is nothing continuous
     * about either and a multiplier would only be a worse way of writing an integer. What gets
     * published is worked out from these at the moment of publishing, so the compensation that
     * keeps the totals still cannot drift away from the change it is paying for.
     */
    private int[] sPhases;
    private int[] sSink;
    private float[] sCap;

    /** Which wear look each family has been set to, or null for the one it already had. */
    private String[] sPattern;

    /** Which preset rung each tuning axis has been set to, or null for the one it already has. */
    private String[] sPreset;

    /** Where each preset chip was drawn, so it can be clicked. */
    private final int[][] presetBox = new int[4][4];

    /** Which cost curve each family has been set to, or null for the one it already had. */
    private CostCurve[] sCurve;

    /** Where the header's curve figure was drawn, and whether its picker is open. */
    private final int[] hdrCurve = new int[4];

    private boolean curveOpen;

    /**
     * The five shapes, drawn once when the picker opens rather than once a frame.
     *
     * <p>
     * Asking a curve for its multiplier is a walk of the whole run whenever the mean for that
     * length is not already worked out, and this would ask five hundred times a frame.
     */
    private int[][] curvePlot;

    /** Where the header's fourth figure was drawn, and whether its picker is open. */
    private final int[] hdrLook = new int[4];

    private boolean lookOpen;

    /** The pictures of what each look does, made on demand and released with the screen. */
    private final PatternPreview previews = new PatternPreview();

    /** Where the three header figures were last drawn, so they can be clicked. */
    private final int[] hdrPhases = new int[4];
    private final int[] hdrSink = new int[4];
    private final int[] hdrCap = new int[4];

    private GuiTextField field;
    private int editKind = -1; // one of the KIND_ constants, or -1 for nothing
    private int editSlot; // 0 = per phase, 1..4 = quarter
    private boolean fieldError;

    /**
     * What the field held when it was pointed at a figure.
     *
     * <p>
     * Needed now that typing takes an axis off its preset. Without it, opening a cell to read it and
     * pressing Enter would drop a rung somebody chose on purpose, for a figure they never changed.
     */
    private String boundText;

    /**
     * A line that stands in the footer for a few seconds after something staged was dropped, and
     * the hover that explains it.
     */
    private String notice;

    private List<String> noticeTip = new ArrayList<String>();
    private int noticeTicks;

    private boolean previewOpen;
    private final List<Rect> hovers = new ArrayList<Rect>();
    private final List<List<String>> hoverText = new ArrayList<List<String>>();

    private int cursor;

    /**
     * Whether the block cycle is being held still.
     *
     * <p>
     * Static, and that is the only interesting thing about it. This screen is reached from the wear
     * table and hands back to it, so anybody comparing one family against another crosses that
     * boundary constantly - and a hold that let go every time the screen was rebuilt would be worse
     * than no hold at all, because it would look like the button had stopped working. It lasts as
     * long as the game does and no longer, which is the right lifetime for something somebody is
     * holding still in order to look at it.
     */
    private static boolean paused;

    /**
     * Whether every family is being shown as its plain vanilla block instead of its own.
     *
     * <p>
     * Static for the same reason the hold is, and separate from it because they are different
     * questions: the hold says the pictures have stopped changing, this says which picture they
     * stopped on. Letting the cycle run again clears it, because a cycle that ran while every row
     * insisted on vanilla would be a control contradicting itself.
     */
    private static boolean stock;

    private int cycleTicks;

    public GuiWearEditor(GuiScreen parent) {
        this.parent = parent;
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private int pw() {
        return Math.min(width - 8, 420);
    }

    private int left() {
        return width / 2 - pw() / 2;
    }

    private int top() {
        return 18;
    }

    private int panelBottom() {
        return height - 54;
    }

    private int editTop() {
        return height - 50;
    }

    private int foot() {
        return height - 22;
    }

    private int cl() {
        return left() + 34;
    }

    private int cr() {
        return left() + pw() - 8;
    }

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        List<SurfaceFamily> staged = new ArrayList<SurfaceFamily>();
        for (SurfaceFamily f : SurfaceFamily.values()) {
            if (f.staged) staged.add(f);
        }
        families = staged.toArray(new SurfaceFamily[staged.size()]);

        int n = SurfaceFamily.values().length;
        if (kCross == null) {
            kCross = new double[n];
            mHeal = new double[n];
            sPhases = new int[n];
            sSink = new int[n];
            sCap = new float[n];
            sPattern = new String[n];
            sCurve = new CostCurve[n];
            sPreset = new String[Presets.TUNING_AXES.length];
            for (int i = 0; i < n; i++) {
                kCross[i] = 1d;
                mHeal[i] = 1d;
                sSink[i] = -1;
                sCap[i] = -1f;
            }
        }

        buttonList.clear();
        buttonList.add(new GuiButton(ID_BACK, left(), foot(), 70, 20, StatCollector.translateToLocal("gui.back")));
        buttonList.add(
            new GuiButton(
                ID_PUBLISH,
                left() + pw() - 140,
                foot(),
                140,
                20,
                StatCollector.translateToLocal("trmtgtnh.weareditor.publish")));
        buttonList.add(
            new GuiButton(
                ID_APPLY,
                width / 2 + 150,
                editTop() + 3,
                44,
                18,
                StatCollector.translateToLocal("trmtgtnh.weareditor.apply")));
        buttonList.add(
            new GuiButton(
                ID_REVERT,
                width / 2 - 96,
                editTop() + 3,
                52,
                18,
                StatCollector.translateToLocal("trmtgtnh.weareditor.revert")));

        field = new GuiTextField(fontRendererObj, width / 2 - 40, editTop() + 5, 120, 16);
        field.setMaxStringLength(48);
        field.setEnableBackgroundDrawing(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        // Each preview holds a GL texture until it is told not to, and nobody else is going to.
        previews.forget();
    }

    @Override
    public void updateScreen() {
        if (field != null) field.updateCursorCounter();
        // Before the hold, because a notice is about something just done rather than about the
        // pictures, and one that stayed up for as long as the cycle was held still would stop
        // meaning "just now".
        if (noticeTicks > 0) noticeTicks--;
        // The count is held as well as the cursor, so letting go carries on from where it stopped
        // rather than jumping a block the moment the button is pressed again.
        if (paused) return;
        if (++cycleTicks >= WearIcons.CYCLE_TICKS) {
            cycleTicks = 0;
            cursor++;
        }
    }

    private SurfaceFamily selectedFamily() {
        return families[selected];
    }

    // ------------------------------------------------------------------
    // Working pricing and rows
    // ------------------------------------------------------------------

    private WearMath.Pricing workingPricing() {
        SurfaceFamily[] all = SurfaceFamily.values();
        double[] avg = new double[all.length];
        double[] heal = new double[all.length];
        com.trmtgtnh.erosion.CostCurve[] shapes = new com.trmtgtnh.erosion.CostCurve[all.length];
        double[] caps = new double[all.length];
        for (int i = 0; i < all.length; i++) {
            // What would land, and nothing else. A staged rung's figure stands in for the file's on
            // its axis, and a typed scale is never multiplied on top of one: typing drops the rung
            // and choosing the rung drops what was typed, so the two are never staged together on
            // one axis and there is no product of them for the card to show.
            avg[i] = publishedAvg(all[i]);
            heal[i] = publishedHeal(all[i]);
            // Carried, because a curve decides what a run costs at every point except its end. Left
            // out, this screen priced every family flat while the table beside it did not, so the
            // two disagreed about six of the ten families - and this screen's entire purpose is
            // showing what a change would do to the figures on that table.
            shapes[i] = shapeCurve(all[i]);
            caps[i] = fileCeiling(all[i]);
        }
        // The ceilings the ground stops at with nothing staged, taken from wherever they are in
        // force: the server's on somebody else's server, because the rules never fold either
        // ceiling into a client, and this client's own otherwise. Every row this screen draws hands
        // its staged ceiling in as an override on top of these, so the pricing and the rows come
        // from one source and agree. Carried at all because a row takes its phase count from its
        // pricing's ceiling: without it this screen priced every family over its whole run while
        // the table beside it priced the capped one, and opening a crossings cell and pressing
        // Enter without typing would have staged a change.
        return new WearMath.Pricing(
            TrmtConfig.multiplierPlayer,
            TrmtConfig.erosionSpeed,
            TrmtConfig.globalSpeed,
            TrmtConfig.healingRate,
            worldCeiling(),
            caps,
            avg,
            heal,
            shapes);
    }

    private double denom() {
        return Math.max(1e-4d, TrmtConfig.multiplierPlayer) * positive(TrmtConfig.erosionSpeed)
            * positive(TrmtConfig.globalSpeed);
    }

    private double healSpeed() {
        return positive(TrmtConfig.healingRate) * positive(TrmtConfig.globalSpeed);
    }

    private static double positive(double rate) {
        return rate <= 0d ? 1d : rate;
    }

    // ------------------------------------------------------------------
    // One staged run, read by everything
    // ------------------------------------------------------------------

    /**
     * Whether this screen is looking at somebody else's server rather than a world of its own.
     *
     * <p>
     * The pricing snapshot alone cannot say, because a world of one's own is sent one too. The
     * difference matters for the ceilings: an integrated server reads the very file this screen
     * does, and a remote one reads a file this machine has never seen.
     */
    private boolean remote() {
        return Trmt.proxy.serverPricing() != null && mc != null && !mc.isIntegratedServerRunning();
    }

    /**
     * The world's wear ceiling where the ground actually stops.
     *
     * <p>
     * The server's on a remote connection. Neither ceiling is folded into a client by the rules, so
     * this client's own figure there describes a world nobody is standing in, and a preview drawn
     * against it could run past the point where that server stops the ground.
     */
    private float worldCeiling() {
        WearMath.Pricing server = Trmt.proxy.serverPricing();
        if (server != null && remote()) return (float) server.worldCap();
        return TrmtConfig.maxWearFraction;
    }

    /** This family's own ceiling as it stands with nothing staged, from the same place. */
    private float fileCeiling(SurfaceFamily family) {
        WearMath.Pricing server = Trmt.proxy.serverPricing();
        if (server != null && remote()) return (float) server.familyCap(family);
        FamilySettings own = TrmtConfig.family(family);
        return own == null ? 1f : own.maxWear;
    }

    /**
     * How deep this family's own run goes, staged or not, and never a depth it borrows.
     *
     * <p>
     * A staged nought counts. It is ground that only discolors, and a real figure somebody typed;
     * reading it as untouched was how a depth of nought used to publish the old depth instead.
     */
    private int ownDepth(SurfaceFamily family) {
        int staged = sSink[family.ordinal()];
        return staged >= 0 ? staged : previewSink(family);
    }

    /**
     * How many layers each pixel of this family's own depth is worth, as publishing would leave it.
     *
     * <p>
     * A typed length is shared out between the face and the depth, and for a family with a depth of
     * its own that share can move its layers per pixel. A family that borrows this one's depth -
     * grass from dirt - is built on the server from the figure published here, so it has to be
     * previewed from it too, or dirt's card and grass's would describe two different worlds.
     *
     * <p>
     * Worked out here rather than through {@link #stagedRun}, which would go on to ask this family's
     * own successor. A family with a depth of its own does not borrow, so its successor has no say
     * in the split, and asking anyway would go round forever on surfaces that wear through into one
     * another in a circle.
     */
    private int stagedLayers(SurfaceFamily family) {
        int typed = sPhases[family.ordinal()];
        int depth = RunShape.clampDepth(ownDepth(family));
        if (typed > 0 && depth > 0) {
            int[] split = RunShape
                .split(typed, depth, firstOffset(family), false, RunShape.layers(previewLayers(family)));
            if (split != null) return split[1];
        }
        return previewLayers(family);
    }

    /**
     * The one run this screen describes for a family: the shape everything staged would give it.
     *
     * <p>
     * One, read by the header, the columns, the solvers and the publish alike. They used to ask
     * separate questions - the header of the staged shape, the columns and the solvers of the live
     * chain - so a typed length moved the header's figure and nothing underneath it, and a figure
     * solved against one run was published against another.
     *
     * <p>
     * A family with no depth of its own borrows the first surface it wears through into, which is
     * what grass does and always has: turf is a face rather than a substance, so the run below it is
     * measured in the earth's pixels. The successor's figures are its staged ones, because the
     * server builds the borrow from what is published rather than from what the file said before.
     */
    private RunShape.Run stagedRun(SurfaceFamily family) {
        int i = family.ordinal();
        List<SurfaceFamily> successors = TrmtConfig.wearsThroughTo(family, true);
        boolean wearsThrough = !successors.isEmpty();
        SurfaceFamily next = wearsThrough ? successors.get(0) : null;
        return RunShape.Run.of(
            previewStages(family),
            firstOffset(family),
            previewLayers(family),
            ownDepth(family),
            wearsThrough,
            next == null ? 1 : stagedLayers(next),
            next == null ? 0 : ownDepth(next),
            sPhases[i],
            worldCeiling(),
            sCap[i] >= 0f ? sCap[i] : fileCeiling(family));
    }

    /** The shape lines this family would publish, worked out from its staged run at this moment. */
    private RunShape.Published published(SurfaceFamily family) {
        return RunShape.publish(stagedRun(family), firstOffset(family), sSink[family.ordinal()]);
    }

    /**
     * What this family's thresholds and heal time are scaled by so a typed length still ends where
     * the run did: one when no length is typed, or when the typed one cannot be shared out.
     */
    private double keepEnd(SurfaceFamily family) {
        return published(family).keepEnd;
    }

    /**
     * The average threshold that would land for this family.
     *
     * <p>
     * Under a staged wear rung, the rung's own figure and nothing more. The rung is applied on the
     * server after every typed line, so its figures are what the ground ends up with, and nothing
     * typed is staged beside it. Otherwise the file's figure, by the typed scale, and by whatever
     * keeps a typed length's end where it was.
     */
    private double publishedAvg(SurfaceFamily family) {
        String rung = stagedPreset("wear");
        if (rung != null) {
            Presets.Figures figures = Presets.previewed(family, "wear", rung);
            return (figures.thresholdMin + figures.thresholdMax) / 2d;
        }
        FamilySettings fs = TrmtConfig.family(family);
        double file = fs == null ? 1d : (fs.thresholdMin + fs.thresholdMax) / 2d;
        return file * kCross[family.ordinal()] * keepEnd(family);
    }

    /** The heal time that would land for this family, by the same rule on the healing axis. */
    private double publishedHeal(SurfaceFamily family) {
        String rung = stagedPreset("healing");
        if (rung != null) return Presets.previewed(family, "healing", rung).healDaysPerStage;
        FamilySettings fs = TrmtConfig.family(family);
        double file = fs == null ? 0d : fs.healDaysPerStage;
        return file * mHeal[family.ordinal()] * keepEnd(family);
    }

    /** The row as it would read once everything staged for this family had been published. */
    private WearMath.Row workingRow(SurfaceFamily family) {
        return workingRow(family, workingPricing());
    }

    /**
     * The same row against a pricing already in hand, for a caller drawing several from one.
     *
     * <p>
     * An empty run is an inert row rather than a row asked for a length of nought, because a row
     * reads nought as "use the live chain". A depth rung taking a one-stage family down to nothing
     * would otherwise preview the very run it is about to take away.
     */
    private WearMath.Row workingRow(SurfaceFamily family, WearMath.Pricing working) {
        RunShape.Run run = stagedRun(family);
        if (run.full <= 0) return WearMath.inert();
        return WearMath.rowFrom(family, working, run.full, run.depth, run.ceiling);
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        hovers.clear();
        hoverText.clear();

        int l = left();
        int r = l + pw();
        int t = top();
        int pb = panelBottom();

        drawRect(l - 1, t - 1, r + 1, pb + 1, 0xFF6A6A7A);
        drawRect(l, t, r, pb, 0xE6141420);
        drawGradientRect(l, t, r, t + 18, 0xFF32324A, 0xFF20202C);
        drawRect(l, t + 18, r, t + 19, 0xFF6A6A7A);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("trmtgtnh.weartable.title"),
            width / 2,
            t + 5,
            0xFFFFFF);
        WearMath.Pricing serverPricing = Trmt.proxy.serverPricing();
        String where = serverPricing == null ? StatCollector.translateToLocal("trmtgtnh.weartable.local")
            : StatCollector.translateToLocal("trmtgtnh.weartable.server");
        drawString(fontRendererObj, where, r - 6 - fontRendererObj.getStringWidth(where), t + 5, 0xB8B8C4);

        drawPause(mouseX, mouseY);
        drawStock(mouseX, mouseY);
        drawRail(l, t, pb);
        drawCard(serverPricing);

        // Edit bar.
        int eb = editTop();
        drawRect(l, eb, r, eb + 26, 0xFF6A6A7A);
        drawRect(l + 1, eb + 1, r - 1, eb + 25, 0xE6141420);
        // Held clear of the Revert button rather than allowed to run under it. The bar is laid
        // out from the middle of the screen and the label from the left edge of the panel, so
        // how much room there is between them depends on the window - and on a narrow one the
        // longest label, a family with a long name at the per-phase slot, does not fit.
        drawString(fontRendererObj, editLabel(width / 2 - 96 - 4 - (l + 6)), l + 6, eb + 6, 0xE0D0FF);
        drawString(fontRendererObj, unitLabel(), width / 2 + 86, eb + 6, 0x8C8CA0);
        if (field != null) field.drawTextBox();
        // What Revert leaves alone is as easy to get wrong as what it drops, so the button says.
        addHover(
            width / 2 - 96,
            eb + 3,
            width / 2 - 96 + 52,
            eb + 21,
            wrapped("§7", StatCollector.translateToLocal("trmtgtnh.weareditor.revert.tip")));

        // Footer. For a few seconds after something staged was dropped it says what was dropped, and
        // otherwise it counts what is staged. In the count's place rather than beside it, because the
        // room between the two buttons holds one short line; and centred in that room rather than on
        // the screen, which the two buttons are not.
        if (noticeTicks > 0 && notice != null) {
            int from = left() + 70;
            int to = left() + pw() - 140;
            int room = to - from - 8;
            String shown = notice;
            if (room > 8 && fontRendererObj.getStringWidth(shown) > room) {
                shown = fontRendererObj.trimStringToWidth(shown, room - 8) + "...";
            }
            int mid = (from + to) / 2;
            int half = fontRendererObj.getStringWidth(shown) / 2;
            drawCenteredString(fontRendererObj, shown, mid, foot() + 6, 0xE0B060);
            addHover(mid - half - 2, foot() + 4, mid + half + 2, foot() + 16, noticeTip);
        } else {
            int staged = countStaged();
            String note = staged == 0 ? StatCollector.translateToLocal("trmtgtnh.weareditor.none")
                : staged + " " + StatCollector.translateToLocal("trmtgtnh.weareditor.staged");
            drawCenteredString(fontRendererObj, note, width / 2, foot() + 6, staged == 0 ? 0x8C8CA0 : 0xE0D0FF);
        }

        super.drawScreen(mouseX, mouseY, partial);

        if (previewOpen) drawPreview(mouseX, mouseY);
        else if (!lookOpen && !curveOpen) drawHover(mouseX, mouseY);

        // Last, and over everything, because it is a thing you are in the middle of doing rather
        // than a thing on the card.
        if (lookOpen) drawLookPicker(cl(), mouseX, mouseY);
        else if (curveOpen) drawCurvePicker(cl(), mouseX, mouseY);
    }

    /**
     * Where the hold button is, as one method rather than three copies of a corner.
     *
     * <p>
     * In the title bar's left, which is the one part of this screen with nothing in it: the title
     * is centred and the local-or-server note is right-aligned, so the corner has been empty since
     * the screen was written. Small on purpose - it is a thing you press once and then forget, not
     * a thing to be looked at while reading the table.
     */
    private int[] pauseBox() {
        int x = left() + 5;
        int y = top() + 4;
        return new int[] { x, y, x + 11, y + 11 };
    }

    /** Beside the hold, because the two are one control between them. */
    private int[] stockBox() {
        int[] hold = pauseBox();
        return new int[] { hold[2] + 3, hold[1], hold[2] + 14, hold[3] };
    }

    private boolean over(int[] box, int mouseX, int mouseY) {
        return mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3];
    }

    private boolean overPause(int mouseX, int mouseY) {
        return over(pauseBox(), mouseX, mouseY);
    }

    /**
     * Draws it, showing what a press would do rather than what is happening.
     *
     * <p>
     * Two bars while the cycle runs and a wedge while it is held, which is the way round every
     * other transport control in the world does it: the glyph is the instruction, not the status.
     * The status is legible anyway from the thing the button governs - a row of blocks that is
     * either changing or is not.
     */
    private void drawPause(int mouseX, int mouseY) {
        int[] box = pauseBox();
        boolean over = overPause(mouseX, mouseY);
        drawRect(box[0], box[1], box[2], box[3], over ? 0xFF6A6A7A : 0xFF3A3A46);
        drawRect(box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1, over ? 0xFF505062 : 0xFF26262E);

        int color = over ? 0xFFFFFFFF : 0xFFB8B8C4;
        int x = box[0];
        int y = box[1];
        if (paused) {
            // A wedge, stepped rather than drawn, which is what eleven pixels allows.
            drawRect(x + 4, y + 3, x + 5, y + 8, color);
            drawRect(x + 5, y + 4, x + 6, y + 7, color);
            drawRect(x + 6, y + 5, x + 7, y + 6, color);
        } else {
            drawRect(x + 3, y + 3, x + 5, y + 8, color);
            drawRect(x + 6, y + 3, x + 8, y + 8, color);
        }

        java.util.List<String> tip = new ArrayList<String>();
        tip.add(
            StatCollector.translateToLocal(paused ? "trmtgtnh.weartable.cycle.run" : "trmtgtnh.weartable.cycle.hold"));
        tip.add("\u00a78" + StatCollector.translateToLocal("trmtgtnh.weartable.cycle.why"));
        addHover(box[0], box[1], box[2], box[3], tip);
    }

    /**
     * Draws the reset, which is one press doing two things and says so in its tooltip.
     *
     * <p>
     * A bar and a wedge back against it - the mark every transport control uses for "to the
     * beginning" - and lit green while it is in force, because unlike the hold this one has a state
     * the thing it governs cannot show you. A stopped cycle is visibly stopped; a family sitting on
     * vanilla stone looks exactly like a family whose cycle merely happens to be on stone.
     */
    private void drawStock(int mouseX, int mouseY) {
        int[] box = stockBox();
        boolean on = over(box, mouseX, mouseY);
        drawRect(box[0], box[1], box[2], box[3], on ? 0xFF6A6A7A : 0xFF3A3A46);
        drawRect(box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1, on ? 0xFF505062 : 0xFF26262E);

        int color = stock ? 0xFF6AD46A : on ? 0xFFFFFFFF : 0xFFB8B8C4;
        int x = box[0];
        int y = box[1];
        drawRect(x + 3, y + 3, x + 4, y + 8, color);
        drawRect(x + 5, y + 5, x + 6, y + 6, color);
        drawRect(x + 6, y + 4, x + 7, y + 7, color);
        drawRect(x + 7, y + 3, x + 8, y + 8, color);

        java.util.List<String> tip = new ArrayList<String>();
        tip.add(StatCollector.translateToLocal("trmtgtnh.weartable.cycle.stock"));
        tip.add("\u00a78" + StatCollector.translateToLocal("trmtgtnh.weartable.cycle.stock.why"));
        addHover(box[0], box[1], box[2], box[3], tip);
    }

    /**
     * The block a family is being shown as this frame.
     *
     * <p>
     * One method for the rail, the card's header and the look previews alike, because the three of
     * them showing different blocks at the same moment would be worse than any of them showing the
     * wrong one - a picture of a look on a block other than the one named beside it is a picture of
     * nothing in particular.
     */
    private ItemStack iconFor(SurfaceFamily family) {
        if (stock) return WearIcons.stock(family);
        List<ItemStack> group = WearIcons.forFamily(family);
        return group.isEmpty() ? null : group.get(cursor % group.size());
    }

    private void drawRail(int l, int t, int pb) {
        for (int i = 0; i < families.length; i++) {
            int cellTop = t + 22 + i * 20;
            if (cellTop + 18 > pb) break;
            if (i == selected) {
                drawRect(l + 3, cellTop, l + 27, cellTop + 18, 0x22FFFFFF);
                drawRect(l + 3, cellTop, l + 5, cellTop + 18, 0xFF6AD46A);
            }
            ItemStack icon = iconFor(families[i]);
            if (icon != null) {
                BlockIconArrayEntry.drawIcon(families[i].key(), icon, l + 6, cellTop + 1);
            }
        }
    }

    private void drawCard(WearMath.Pricing serverPricing) {
        SurfaceFamily fam = selectedFamily();
        int t = top();
        int cl = cl();
        int cr = cr();

        // One row for the whole card, drawn from the staged run. The header used to be the only part
        // that read the staged shape, so a typed length moved the phase count and left every column
        // underneath it quoting the run as it was.
        RunShape.Run run = stagedRun(fam);
        WearMath.Row row = workingRow(fam, workingPricing());
        WearMath.Row base = serverPricing == null ? null : WearMath.rowFrom(fam, serverPricing);

        // Header.
        ItemStack heading = iconFor(fam);
        if (heading != null) BlockIconArrayEntry.drawIcon("hdr" + fam.key(), heading, cl, t + 22);
        drawString(fontRendererObj, fam.key(), cl + 22, t + 22, row.wears ? 0xFFFFFF : 0xFF808080);
        if (row.wears) {
            // Drawn as three figures rather than one line, because each of them is a thing you can
            // click. Each records where it landed so the click test has something to test against;
            // laying them out here rather than at fixed offsets keeps them together whatever a
            // family's numbers happen to be as wide as.
            //
            // Each lights against where the ground stops today with nothing staged, rather than
            // against a second row drawn from the same pricing: a staged rung moves both of those
            // rows alike, and comparing the two would never light for it.
            int ordinal = fam.ordinal();
            FamilySettings own = TrmtConfig.family(fam);
            int liveLen = ErosionChain.length(fam);
            float liveCeil = RunShape.ceiling(worldCeiling(), fileCeiling(fam));
            int liveDepth = liveLen > 0 ? ErosionChain.sinkAt(fam, liveLen - 1) : 0;
            int at = cl + 22;
            at = headerFigure(
                hdrPhases,
                at,
                t + 33,
                cr,
                row.phases + (row.phases < row.fullPhases ? "/" + row.fullPhases : "") + " ph",
                row.fullPhases != liveLen || row.phases != RunShape.capped(liveLen, liveCeil),
                editKind == KIND_PHASES);
            // Starred when the depth is another surface's, the one figure on this line that this
            // family's own settings do not decide.
            at = headerFigure(
                hdrSink,
                at,
                t + 33,
                cr,
                row.sinkPixels + "px" + (run.borrowed && run.depth > 0 ? "*" : ""),
                row.sinkPixels != liveDepth
                    || (sSink[ordinal] >= 0 && (own == null || sSink[ordinal] != own.maxSinkPixels)),
                editKind == KIND_SINK);
            addBoxHover(hdrSink, depthTip(fam, run));
            // Starred when the world's ceiling is the lower and wins, so a figure typed here that
            // moves nothing on the card is seen to move nothing for a reason.
            at = headerFigure(
                hdrCap,
                at,
                t + 33,
                cr,
                Math.round(row.cap * 100f) + "%" + (run.heldByWorld ? "*" : ""),
                row.cap != liveCeil
                    || (sCap[ordinal] >= 0f && Math.round(sCap[ordinal] * 100f) != Math.round(fileCeiling(fam) * 100f)),
                editKind == KIND_CAP);
            addBoxHover(hdrCap, capTip(run));
            // Fourth rather than last, so that the look's name - which is by far the widest and the
            // only one that varies - is the one a narrow window trims.
            at = headerFigure(
                hdrCurve,
                at,
                t + 33,
                cr,
                shapeCurve(fam).key(),
                sCurve[fam.ordinal()] != null,
                curveOpen);
            headerFigure(
                hdrLook,
                at,
                t + 33,
                cr,
                PatternPreview.nameOf(shapePattern(fam)),
                sPattern[fam.ordinal()] != null,
                lookOpen);
            drawPresetChips(cl, cr, t);
        } else {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("trmtgtnh.weartable.inert"),
                cl + 22,
                t + 33,
                0xFF808080);
        }
        drawRect(cl, t + 46, cr, t + 47, 0x30FFFFFF);
        if (!row.wears) return;

        // Crossings strip.
        drawString(fontRendererObj, StatCollector.translateToLocal("trmtgtnh.weareditor.cross"), cl, t + 52, 0xC9C9D6);
        String[] caps = { "/phase", "1/4", "1/2", "3/4", "Full" };
        for (int c = 0; c < 5; c++) {
            int cellLeft = cl + c * 75;
            int rightX = cellLeft + 69;
            drawCenteredString(fontRendererObj, caps[c], cellLeft + 37, t + 64, 0x8C8CA0);
            if (c == 0) outline(cl - 1, t + 61, cl + 74, t + 87, 0xFF6A6A7A);
            else drawRect(cellLeft, t + 61, cellLeft + 74, t + 87, 0x14FFFFFF);

            double value = c == 0 ? row.crossingsPerPhase : row.crossings[c - 1];
            double bval = base == null ? Double.NaN : (c == 0 ? base.crossingsPerPhase : base.crossings[c - 1]);
            drawValueCell(
                GuiWearTable.compact(value),
                Double.isNaN(bval) ? Double.NaN : value - bval,
                rightX,
                t + 75,
                editKind == KIND_CROSS && editSlot == c);
            addHover(cellLeft, t + 61, cellLeft + 74, t + 87, crossTip(fam, c, row));
        }
        drawRect(cl, t + 92, cr, t + 93, 0x30FFFFFF);

        // Heal column.
        drawString(fontRendererObj, StatCollector.translateToLocal("trmtgtnh.weareditor.heal"), cl, t + 98, 0xC9C9D6);
        String[] labels = { "/phase", "to 1/4", "to 1/2", "to 3/4", "full" };
        for (int i = 0; i < 5; i++) {
            int y = t + 108 + i * 12;
            boolean readOnly = i > 0 && healBorrowedWholly(fam, i);
            int color = readOnly ? 0xFF808080 : 0xFFFFFF;
            drawString(fontRendererObj, labels[i], cl, y, 0x8C8CA0);
            double days = i == 0 ? row.healDaysPerPhase : row.healDays[i - 1];
            drawString(fontRendererObj, DurationFormat.format(days), cl + 56, y, color);
            if (editKind == KIND_HEAL && editSlot == i) {
                drawRect(cl + 52, y - 2, cr, y + 9, 0x206AD46A);
            }
            double bdays = base == null ? Double.NaN : (i == 0 ? base.healDaysPerPhase : base.healDays[i - 1]);
            if (!Double.isNaN(bdays) && Math.abs(days - bdays) > 1e-4d) {
                String tag = (days > bdays ? "+" : "-") + DurationFormat.formatShort(Math.abs(days - bdays));
                int c2 = days > bdays ? 0xFF6AD46A : 0xFFE0605A;
                drawString(fontRendererObj, tag, cr - fontRendererObj.getStringWidth(tag), y, c2);
            }
            if (readOnly)
                drawString(fontRendererObj, "*", cr - fontRendererObj.getStringWidth("*") - 40, y, 0xFF808080);
            addHover(cl, y - 2, cr, y + 9, healTip(fam, i, row, readOnly));
        }
    }

    /** A right-aligned value with its green/red delta just left of it, and a knob outline when bound. */
    private void drawValueCell(String value, double delta, int rightX, int y, boolean bound) {
        int vw = fontRendererObj.getStringWidth(value);
        if (!Double.isNaN(delta) && Math.abs(delta) >= 0.5d) {
            String tag = (delta > 0 ? "+" : "-") + GuiWearTable.compact(Math.abs(delta));
            int color = delta > 0 ? 0xFF6AD46A : 0xFFE0605A;
            int tw = fontRendererObj.getStringWidth(tag);
            drawString(fontRendererObj, tag, rightX - vw - 3 - tw, y, color);
        }
        drawString(fontRendererObj, value, rightX - vw, y, bound ? 0xFF6AD46A : 0xFFFFFF);
    }

    private void outline(int x1, int y1, int x2, int y2, int color) {
        drawRect(x1, y1, x2, y1 + 1, color);
        drawRect(x1, y2 - 1, x2, y2, color);
        drawRect(x1, y1, x1 + 1, y2, color);
        drawRect(x2 - 1, y1, x2, y2, color);
    }

    /**
     * What the edit bar says it is editing, in no more than the room it has.
     *
     * <p>
     * Shortened in the order the words can best be spared rather than simply cut. The middle word
     * goes first, because the far end of the same bar already says whether the number is crossings
     * or a time; the slot goes next, because a family name alone still tells you which row you
     * touched; and only then is what is left trimmed to fit, which by that point cannot happen
     * outside a window narrower than the panel will draw at.
     */
    private String editLabel(int room) {
        // Trimmed like everything else here. It used to return before the trimming, so the one
        // label that is shown before anything has been clicked - the longest of them, and the one
        // most people see first - was the one label that ran under the Revert button.
        if (editKind < 0) {
            String pick = StatCollector.translateToLocal("trmtgtnh.weareditor.pick");
            return room <= 0 || fontRendererObj.getStringWidth(pick) <= room ? pick
                : fontRendererObj.trimStringToWidth(pick, room);
        }

        String family = selectedFamily().key();
        if (editKind == KIND_PHASES) return family + "  phases";
        if (editKind == KIND_SINK) return family + "  depth";
        if (editKind == KIND_CAP) return family + "  how far it may wear";

        String slot = editSlot == 0 ? "per phase" : new String[] { "", "1/4", "1/2", "3/4", "full" }[editSlot];
        String full = family + "  " + (editKind == KIND_HEAL ? "heal" : "crossings") + "  " + slot;
        if (room <= 0 || fontRendererObj.getStringWidth(full) <= room) return full;

        String shorter = family + "  " + slot;
        if (fontRendererObj.getStringWidth(shorter) <= room) return shorter;

        return fontRendererObj.trimStringToWidth(family, room);
    }

    // ------------------------------------------------------------------
    // Hover tooltips
    // ------------------------------------------------------------------

    private void addHover(int x1, int y1, int x2, int y2, List<String> text) {
        if (text == null || text.isEmpty()) return;
        hovers.add(new Rect(x1, y1, x2, y2));
        hoverText.add(text);
    }

    /**
     * A hover over a box some figure recorded as it was drawn, and none over a box it left empty.
     *
     * <p>
     * A figure with no room zeroes its box, and a hover registered over the zeroed one would answer
     * the mouse in the screen's top-left corner.
     */
    private void addBoxHover(int[] box, List<String> text) {
        if (box[2] <= box[0] || box[3] <= box[1]) return;
        addHover(box[0], box[1], box[2], box[3], text);
    }

    /** A sentence broken to a tooltip's width, every line in one color. */
    private List<String> wrapped(String color, String text) {
        List<String> lines = new ArrayList<String>();
        for (String line : fontRendererObj.listFormattedStringToWidth(text, 220)) lines.add(color + line);
        return lines;
    }

    /**
     * Why the depth reads as it does, where that is not simply this family's own figure.
     *
     * <p>
     * Nought and a borrowed depth are the two that look like a mistake: a family that sinks no
     * distance at all, and a depth this family's own settings never name.
     */
    private List<String> depthTip(SurfaceFamily fam, RunShape.Run run) {
        if (run.depth == 0) {
            return wrapped("§7", StatCollector.translateToLocal("trmtgtnh.weareditor.depth.never"));
        }
        if (!run.borrowed) return null;
        List<SurfaceFamily> successors = TrmtConfig.wearsThroughTo(fam, true);
        String from = successors.isEmpty() ? "?"
            : successors.get(0)
                .key();
        return wrapped(
            "§7",
            StatCollector
                .translateToLocalFormatted("trmtgtnh.weareditor.depth.borrowed", from, Integer.valueOf(run.depth)));
    }

    /**
     * Why the ceiling reads lower than this family's own figure, when the world's is holding it.
     *
     * <p>
     * Named by whose setting it is. On somebody else's server the ceiling holding it is theirs, and
     * a hover naming this client's setting would send somebody off to change a file that decides
     * nothing there.
     */
    private List<String> capTip(RunShape.Run run) {
        if (!run.heldByWorld) return null;
        String key = remote() ? "trmtgtnh.weareditor.cap.world.server" : "trmtgtnh.weareditor.cap.world";
        return wrapped(
            "§7",
            StatCollector.translateToLocalFormatted(
                key,
                Integer.valueOf(Math.round(run.ceiling * 100f)),
                Integer.valueOf(Math.round(run.familyCeiling * 100f))));
    }

    private void drawHover(int mouseX, int mouseY) {
        for (int i = 0; i < hovers.size(); i++) {
            if (hovers.get(i)
                .has(mouseX, mouseY)) {
                drawHoveringText(hoverText.get(i), mouseX, mouseY, fontRendererObj);
                return;
            }
        }
    }

    private List<String> crossTip(SurfaceFamily fam, int slot, WearMath.Row row) {
        FamilySettings fs = TrmtConfig.family(fam);
        if (fs == null) return null;
        List<String> lines = new ArrayList<String>();
        String cat = "families." + fam.key();
        // The figures that would land, which are the ones the cells are drawn from: a staged rung's
        // own, or the file's by the typed scale and by whatever keeps a typed length's end in place.
        String rung = stagedPreset("wear");
        double keep = keepEnd(fam);
        double scale = kCross[fam.ordinal()] * keep;
        double min;
        double max;
        if (rung != null) {
            Presets.Figures figures = Presets.previewed(fam, "wear", rung);
            min = figures.thresholdMin;
            max = figures.thresholdMax;
        } else {
            min = fs.thresholdMin * scale;
            max = fs.thresholdMax * scale;
        }
        lines.add("§f" + cat + ".thresholdMin§7 = " + trim(min));
        lines.add("§f" + cat + ".thresholdMax§7 = " + trim(max));
        if (slot == 0) {
            lines.add("§8" + GuiWearTable.compact(row.crossingsPerPhase) + " crossings per phase");
        } else {
            lines.add(
                "§8reaches " + Math.round(WearMath.FRACTIONS[slot - 1] * row.phases) + " of " + row.phases + " phases");
        }
        if (rung != null || scale != 1d)
            lines.add("§8(staged from " + trim(fs.thresholdMin) + "/" + trim(fs.thresholdMax) + ")");
        // Only with no rung staged on this axis. A rung's figures land as they are, uncompensated,
        // so a line saying they had been scaled would be untrue.
        if (rung == null && keep != 1d) {
            lines.addAll(
                wrapped("§8", StatCollector.translateToLocalFormatted("trmtgtnh.weareditor.keepend", trim(keep))));
        }
        return lines;
    }

    private List<String> healTip(SurfaceFamily fam, int slot, WearMath.Row row, boolean readOnly) {
        FamilySettings fs = TrmtConfig.family(fam);
        if (fs == null) return null;
        List<String> lines = new ArrayList<String>();
        if (readOnly) {
            lines.add("§7governed by §fdirt§7 (deepest phases)");
            lines.add("§8edit it on the dirt row");
            return lines;
        }
        // The figure that would land, by the same rule as the crossings tooltip.
        String rung = stagedPreset("healing");
        double keep = keepEnd(fam);
        double scale = mHeal[fam.ordinal()] * keep;
        lines.add("§ffamilies." + fam.key() + ".healDaysPerStage§7 = " + trim(publishedHeal(fam)));
        double days = slot == 0 ? row.healDaysPerPhase : row.healDays[slot - 1];
        lines.add("§8" + DurationFormat.format(days));
        if (rung != null || scale != 1d) lines.add("§8(staged from " + trim(fs.healDaysPerStage) + ")");
        if (rung == null && keep != 1d) {
            lines.addAll(
                wrapped("§8", StatCollector.translateToLocalFormatted("trmtgtnh.weareditor.keepend", trim(keep))));
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (previewOpen) {
            int[][] pbs = previewButtons();
            for (int i = 0; i < pbs.length; i++) {
                int[] b = pbs[i];
                if (mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + 18) {
                    if (i == 0) previewOpen = false;
                    else if (i == 1) {
                        discardAll();
                        previewOpen = false;
                    } else {
                        publish();
                        previewOpen = false;
                    }
                    return;
                }
            }
            return; // a click anywhere else in the modal is swallowed, not passed to the editor
        }
        if (field != null) field.mouseClicked(mouseX, mouseY, mouseButton);

        // Before the picker's branch would have a chance to close it: the button sits in the title
        // bar, which no row of the picker can reach, so a press here is never also a choice of look.
        if (!lookOpen && !previewOpen && overPause(mouseX, mouseY)) {
            paused = !paused;
            // Letting the cycle run again releases the reset with it. A cycle running while every
            // row insisted on vanilla would be the two buttons contradicting one another, and the
            // one that was pressed last is the one to believe.
            if (!paused) stock = false;
            return;
        }
        if (!lookOpen && !previewOpen && over(stockBox(), mouseX, mouseY)) {
            stock = true;
            paused = true;
            return;
        }

        // The look picker takes every click while it is open, because a click anywhere else is a
        // choice not to choose. It does not swallow it, though - the click goes on to the screen
        // afterwards - which is why lookPickerTop keeps every row clear of the buttons.
        // The same arrangement as the look picker below, and for the same reasons.
        if (curveOpen) {
            int hit = curveRowAt(mouseX, mouseY);
            if (hit >= 0) {
                CostCurve pick = CostCurve.values()[hit];
                SurfaceFamily fam = selectedFamily();
                FamilySettings own = TrmtConfig.family(fam);
                // Choosing what a family already has clears the staging rather than staging a
                // change of nothing, so the violet only ever means something is genuinely pending.
                boolean same = own != null && own.costCurve == pick;
                sCurve[fam.ordinal()] = same ? null : pick;
            }
            curveOpen = false;
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (lookOpen) {
            int hit = lookRowAt(mouseX, mouseY);
            if (hit >= 0) {
                String look = PatternPreview.looks()[hit];
                SurfaceFamily fam = selectedFamily();
                FamilySettings own = TrmtConfig.family(fam);
                boolean same = own != null && look.equals(PatternPreview.canonical(own.wearPattern));
                sPattern[fam.ordinal()] = same ? null : look;
            }
            lookOpen = false;
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        // Rail selection.
        int l = left();
        for (int i = 0; i < families.length; i++) {
            int cellTop = top() + 22 + i * 20;
            if (mouseX >= l + 3 && mouseX <= l + 27 && mouseY >= cellTop && mouseY <= cellTop + 18) {
                selectFamily(i);
                super.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
        }

        // The four figures are drawn only for a family that wears at all, and their boxes hold
        // wherever they were last drawn - so without this a click on the blank line of a family
        // that does not wear lands on the figure of whichever family did.
        if (!workingRow(selectedFamily()).wears) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        // The chips, before the header figures: they sit below the card's rows and cannot overlap
        // them, but a stale box from a narrower window could, and a rejected axis must reject here
        // rather than fall through.
        for (int i = 0; i < Presets.TUNING_AXES.length; i++) {
            if (!within(presetBox[i], mouseX, mouseY)) continue;
            String axis = Presets.TUNING_AXES[i];
            if (serverOwns(axis)) {
                Trmt.proxy.tellServerOwnsGeometry();
                super.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
            cyclePreset(i, mouseButton == 1 ? -1 : 1);
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (within(hdrCurve, mouseX, mouseY)) {
            curveOpen = true;
            lookOpen = false;
            // Worked out afresh each time it opens, because the run length it is drawn at belongs
            // to the family selected now and to any staged phase count on it.
            curvePlot = null;
            closeEdit();
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (within(hdrLook, mouseX, mouseY)) {
            lookOpen = true;
            curveOpen = false;
            closeEdit();
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        // The header's three figures, which are the shape of the run rather than its prices.
        if (within(hdrPhases, mouseX, mouseY)) {
            bindShape(KIND_PHASES);
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (within(hdrSink, mouseX, mouseY)) {
            bindShape(KIND_SINK);
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (within(hdrCap, mouseX, mouseY)) {
            bindShape(KIND_CAP);
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        // Cell selection.
        int t = top();
        int cl = cl();
        WearMath.Row row = workingRow(selectedFamily());
        if (row.wears) {
            for (int c = 0; c < 5; c++) {
                int cellLeft = cl + c * 75;
                if (mouseX >= cellLeft && mouseX <= cellLeft + 74 && mouseY >= t + 61 && mouseY <= t + 87) {
                    bind(KIND_CROSS, c, row);
                    super.mouseClicked(mouseX, mouseY, mouseButton);
                    return;
                }
            }
            for (int i = 0; i < 5; i++) {
                int y = t + 108 + i * 12;
                if (mouseX >= cl && mouseX <= cr() && mouseY >= y - 2 && mouseY <= y + 9) {
                    if (i > 0 && healBorrowedWholly(selectedFamily(), i)) {
                        jumpToDirt();
                    } else {
                        bind(KIND_HEAL, i, row);
                    }
                    super.mouseClicked(mouseX, mouseY, mouseButton);
                    return;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void selectFamily(int index) {
        selected = index;
        closeEdit();
        field.setText("");
    }

    /** Points the edit bar at one of the three figures in the header. */
    private void bindShape(int kind) {
        SurfaceFamily fam = selectedFamily();
        editKind = kind;
        editSlot = 0;
        fieldError = false;
        // Each filled with the figure typing would replace, which is not always the one on the
        // header. The depth is the family's own, so grass reads nought rather than the earth's depth
        // it borrows; the ceiling is its own too, which a lower world ceiling may be holding it under.
        String text;
        if (kind == KIND_PHASES) text = Integer.toString(workingRow(fam).fullPhases);
        else if (kind == KIND_SINK) text = Integer.toString(ownDepth(fam));
        else text = Integer.toString(Math.min(100, Math.round(stagedRun(fam).familyCeiling * 100f)));
        boundText = text;
        field.setText(text);
        field.setTextColor(0xE0E0E0);
        field.setFocused(true);
        field.setCursorPositionEnd();
    }

    private void bind(int kind, int slot, WearMath.Row row) {
        editKind = kind;
        editSlot = slot;
        fieldError = false;
        double value = cellOf(row, kind, slot);
        String text = kind == KIND_HEAL ? DurationFormat.format(value) : trim(value);
        boundText = text;
        field.setText(text);
        field.setTextColor(0xE0E0E0);
        field.setFocused(true);
        field.setCursorPositionEnd();
    }

    private void jumpToDirt() {
        for (int i = 0; i < families.length; i++) {
            if (families[i] == SurfaceFamily.DIRT) {
                selectFamily(i);
                bind(KIND_HEAL, 0, workingRow(SurfaceFamily.DIRT));
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (previewOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) previewOpen = false;
            return;
        }
        if (curveOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) curveOpen = false;
            return;
        }
        if (lookOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) lookOpen = false;
            return;
        }
        if (field != null && field.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                field.setFocused(false);
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN) {
                commit();
                return;
            }
            if (field.textboxKeyTyped(typedChar, keyCode)) {
                fieldError = false;
                field.setTextColor(0xE0E0E0);
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_BACK:
                mc.displayGuiScreen(parent);
                break;
            case ID_PUBLISH:
                if (countStaged() > 0) previewOpen = true;
                break;
            case ID_APPLY:
                commit();
                break;
            case ID_REVERT:
                // Everything staged for the family on screen, and nothing else. The presets stay,
                // because each one moves every family and this button is about one of them.
                revertFamily(selectedFamily().ordinal());
                // Its depth may be the one another family's typed length was shared out against.
                showNotice(null, settleLengths());
                lookOpen = false;
                curveOpen = false;
                curvePlot = null;
                closeEdit();
                field.setText("");
                break;
            default:
                break;
        }
    }

    private void markError() {
        fieldError = true;
        field.setTextColor(0xFFE0605A);
    }

    /**
     * Stages what the field holds, or marks it wrong and stages nothing.
     *
     * <p>
     * Typing a figure takes its axis off any preset staged there, the way editing the file moves a
     * chooser to custom. A rung and a typed figure on one axis both set the same numbers and the
     * rung is applied last, so only one of them could ever land; choosing a rung already drops what
     * was typed on its axis, and this is the other half of that rule. The rung goes before the
     * figure is solved, so the solve is made against the file's own figures on this axis, which are
     * the ones the typed figure will be published against. Everything else staged stays in the
     * solve, because it lands too.
     */
    private void commit() {
        if (editKind < 0) return;
        SurfaceFamily fam = selectedFamily();
        FamilySettings fs = TrmtConfig.family(fam);
        if (fs == null) return;
        String text = field.getText();

        // Opening a figure to read it and pressing Enter is not an edit, and must not cost a rung.
        if (sameAsPrefill(text)) {
            closeEdit();
            return;
        }

        // Guarded, because the ceiling has no preset and so no axis to clear.
        int axis = axisOf(editKind);
        String was = null;
        if (axis >= 0) {
            was = sPreset[axis];
            sPreset[axis] = null;
        }
        if (!commitTyped(fam, fs, text)) {
            if (axis >= 0) sPreset[axis] = was;
            markError();
            return;
        }
        showNotice(was != null ? Presets.TUNING_AXES[axis] : null, settleLengths());
        closeEdit();
    }

    /**
     * Stages one typed figure against the shape as it now stands, and says whether it could.
     *
     * <p>
     * Leaves nothing behind when it fails, so the caller need only put the rung back. A figure equal
     * to what the family has with nothing staged clears the staging rather than staging a change of
     * nothing, which is the rule both pickers keep: the violet then only ever means something is
     * genuinely pending, and a publish never carries a line whose one effect is to move a chooser
     * to custom.
     */
    private boolean commitTyped(SurfaceFamily fam, FamilySettings fs, String text) {
        int i = fam.ordinal();
        if (editKind == KIND_PHASES || editKind == KIND_SINK || editKind == KIND_CAP) {
            int typed;
            try {
                typed = (int) Math.round(Double.parseDouble(text.trim()));
            } catch (NumberFormatException bad) {
                return false;
            }

            if (editKind == KIND_SINK) {
                if (typed < 0 || typed > SinkProfile.MAX_SINK_PIXELS) return false;
                int wasSink = sSink[i];
                int wasPhases = sPhases[i];
                // A depth is half of what makes a run's length, so a length typed earlier no longer
                // describes anything once the depth moves. Forgetting it is the honest answer: the
                // figure on the screen goes back to what the new depth produces, and can be typed
                // over again if that is not what was wanted. Only when the depth really moves,
                // though, because a depth typed as it already stands changes nothing.
                if (typed != ownDepth(fam)) sPhases[i] = 0;
                sSink[i] = typed == fs.maxSinkPixels ? -1 : typed;
                // A family that wears with no gradations to wear through is not a run the chain can
                // build.
                if (stagedRun(fam).full <= 0) {
                    sSink[i] = wasSink;
                    sPhases[i] = wasPhases;
                    return false;
                }
                return true;
            }

            if (editKind == KIND_CAP) {
                if (typed < 1 || typed > 100) return false;
                sCap[i] = typed == Math.round(fileCeiling(fam) * 100f) ? -1f : typed / 100f;
                return true;
            }

            int wasPhases = sPhases[i];
            sPhases[i] = 0;
            if (typed == stagedRun(fam).built) return true;
            if (typed < 1) {
                sPhases[i] = wasPhases;
                return false;
            }
            sPhases[i] = typed;
            if (stagedRun(fam).split == null) {
                sPhases[i] = wasPhases;
                return false;
            }
            return true;
        }

        if (editKind == KIND_HEAL) {
            double target = DurationFormat.parse(text);
            if (!(target > 0d) || Double.isInfinite(target)) return false;
            Double m = solveHeal(fam, editSlot, target);
            if (m == null) return false;
            // Read back with the scale at one, which is the cell as the file has it. A figure typed
            // back to that is the file's own, not a change a hair's breadth away from it. Compared
            // the way the cell is written as well as to the hundredth of a day, because a span typed
            // as the cell showed it is rounded to the second and may differ by more than that.
            mHeal[i] = 1d;
            double asFiled = cellOf(workingRow(fam), KIND_HEAL, editSlot);
            if (DurationFormat.format(target)
                .equals(DurationFormat.format(asFiled)) || trim(target).equals(trim(asFiled))) {
                return true;
            }
            mHeal[i] = clamp(m.doubleValue(), 1e-6d, 100000d / Math.max(1e-6d, fs.healDaysPerStage));
            return true;
        }

        double target;
        try {
            target = Double.parseDouble(text.trim());
        } catch (NumberFormatException bad) {
            return false;
        }
        if (!(target > 0d) || Double.isInfinite(target)) return false;
        Double k = solveCross(fam, editSlot, target);
        if (k == null) return false;
        kCross[i] = 1d;
        if (trim(target).equals(trim(cellOf(workingRow(fam), KIND_CROSS, editSlot)))) return true;
        double maxOrig = Math.max(1e-6d, Math.max(fs.thresholdMin, fs.thresholdMax));
        kCross[i] = clamp(k.doubleValue(), 1e-6d, 1e6d / maxOrig);
        return true;
    }

    /**
     * Whether the field still holds the figure it was filled with, read as a number rather than as
     * text, so that {@code 12.50} typed over {@code 12.5} is still not an edit.
     */
    private boolean sameAsPrefill(String text) {
        if (boundText == null || text == null) return false;
        if (editKind == KIND_HEAL) {
            double typed = DurationFormat.parse(text);
            return typed > 0d && typed == DurationFormat.parse(boundText);
        }
        try {
            double typed = Double.parseDouble(text.trim());
            double filled = Double.parseDouble(boundText.trim());
            if (editKind == KIND_CROSS) return typed == filled;
            // The three shape figures are whole numbers, and are compared the way commit reads them.
            return Math.round(typed) == Math.round(filled);
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** Which preset axis an edit belongs to, by its index in the axes, or -1 for the ceiling's none. */
    private static int axisOf(int kind) {
        String name;
        if (kind == KIND_CROSS) name = "wear";
        else if (kind == KIND_HEAL) name = "healing";
        else if (kind == KIND_PHASES) name = "phases";
        else if (kind == KIND_SINK) name = "depth";
        else return -1;
        for (int i = 0; i < Presets.TUNING_AXES.length; i++) {
            if (Presets.TUNING_AXES[i].equals(name)) return i;
        }
        return -1;
    }

    /**
     * Drops every typed length the shape now staged can no longer share out, and names the families.
     *
     * <p>
     * A length is shared out between the face and the depth when it is used rather than when it is
     * typed, so a depth moved afterwards - by a rung, a Revert, or a successor's own figure - can
     * leave one that no split reaches. Kept, it would show on the card and never be published, and
     * the server would build the run the figures give instead. Repeated until nothing moves, because
     * dropping one family's length can change the layers another family borrows.
     *
     * @return the families whose length was dropped, ready for the notice, or null when none was
     */
    private String settleLengths() {
        List<String> dropped = new ArrayList<String>();
        boolean moved = true;
        while (moved) {
            moved = false;
            for (SurfaceFamily f : families) {
                int i = f.ordinal();
                if (sPhases[i] <= 0 || stagedRun(f).split != null) continue;
                sPhases[i] = 0;
                dropped.add(f.key());
                moved = true;
            }
        }
        return dropped.isEmpty() ? null : join(dropped, ", ");
    }

    /**
     * Puts up the footer notice for whatever was just dropped, if anything was.
     *
     * <p>
     * Said rather than done quietly, because both undo something a person chose: a rung they picked,
     * or a length they typed. A figure that silently went back is the kind of thing somebody only
     * notices after publishing.
     */
    private void showNotice(String rungAxis, String lengths) {
        if (rungAxis == null && lengths == null) return;
        List<String> why = new ArrayList<String>();
        String text = null;
        if (rungAxis != null) {
            text = StatCollector.translateToLocalFormatted("trmtgtnh.weareditor.rungdropped", rungAxis);
            why.addAll(wrapped("§7", StatCollector.translateToLocal("trmtgtnh.weareditor.rungdropped.why")));
        }
        if (lengths != null) {
            String part = StatCollector.translateToLocalFormatted("trmtgtnh.weareditor.lengthdropped", lengths);
            text = text == null ? part : text + " - " + part;
            why.addAll(wrapped("§7", StatCollector.translateToLocal("trmtgtnh.weareditor.lengthdropped.why")));
        }
        // The notice itself first, whole, because the footer may have had to trim it.
        List<String> tip = new ArrayList<String>();
        tip.addAll(wrapped("§f", text));
        tip.addAll(why);
        notice = text;
        noticeTip = tip;
        noticeTicks = 100;
    }

    /** Lets go of whatever the edit bar was pointed at, and leaves its text where it is. */
    private void closeEdit() {
        editKind = -1;
        fieldError = false;
        boundText = null;
        if (field != null) {
            field.setFocused(false);
            field.setTextColor(0xE0E0E0);
        }
    }

    /**
     * The curve this family prices by.
     *
     * <p>
     * One accessor rather than a field read at each site, because the staged copy will need to
     * answer differently from the live one and every caller must ask the same question.
     */
    private CostCurve shapeCurve(SurfaceFamily family) {
        if (family == null) return CostCurve.FLAT;
        if (sCurve != null) {
            CostCurve staged = sCurve[family.ordinal()];
            if (staged != null) return staged;
        }
        FamilySettings fs = TrmtConfig.family(family);
        return fs == null || fs.costCurve == null ? CostCurve.FLAT : fs.costCurve;
    }

    /**
     * The scale on this family's thresholds that makes the chosen crossings cell read {@code target}.
     *
     * <p>
     * Solved against the staged run, the one the cells are drawn from. It used to measure the live
     * chain, so under a typed length a solved figure landed somewhere other than where it was typed.
     * The scale sits on the file's own figure with the typed length's compensation on top of it,
     * which is exactly how the two are published.
     */
    private Double solveCross(SurfaceFamily base, int slot, double target) {
        FamilySettings fs = TrmtConfig.family(base);
        double figure = (fs.thresholdMin + fs.thresholdMax) / 2d;
        if (figure <= 0d) return null;
        double keep = keepEnd(base);
        double denom = denom();
        if (slot == 0) return RunShape.scaleFor(target, 0d, 1d, figure, keep, denom);
        RunShape.Run run = stagedRun(base);
        int n = RunShape.quarter(WearMath.FRACTIONS[slot - 1], run.reached);
        double ownedWeight = 0d;
        double fixedCrossings = 0d;
        com.trmtgtnh.erosion.CostCurve mine = shapeCurve(base);
        for (int phase = 0; phase < n; phase++) {
            SurfaceFamily at = ErosionChain.paceAt(base, phase);
            // Weighted rather than counted. Every gradation used to cost this family's average, so
            // counting them was the same arithmetic; with a curve the fourth gradation of snow and
            // the seventieth differ fourfold, and a plain count solves for a number that makes the
            // cell read something else entirely. The curve is measured against the WHOLE run, not
            // the part a wear ceiling allows - the same figure the engine and the table use, or this
            // would solve against a shape neither of them draws.
            if (at == base || at == null) {
                ownedWeight += mine.at(phase, run.full);
            } else {
                fixedCrossings += publishedAvg(at) * shapeCurve(at).at(phase, run.full) / denom;
            }
        }
        return RunShape.scaleFor(target, fixedCrossings, ownedWeight, figure, keep, denom);
    }

    /** The scale on this family's heal time that makes the chosen heal cell read {@code target} days. */
    private Double solveHeal(SurfaceFamily base, int slot, double target) {
        FamilySettings fs = TrmtConfig.family(base);
        double figure = fs.healDaysPerStage;
        if (figure <= 0d) return null;
        double keep = keepEnd(base);
        double healSpeed = healSpeed();
        if (slot == 0) return RunShape.scaleFor(target, 0d, 1d, figure, keep, healSpeed);
        RunShape.Run run = stagedRun(base);
        int n = RunShape.quarter(WearMath.FRACTIONS[slot - 1], run.reached);
        int owned = 0;
        double fixedDays = 0d;
        for (int phase = run.reached - n; phase < run.reached; phase++) {
            SurfaceFamily at = ErosionChain.paceAt(base, phase);
            if (at == base || at == null) owned++;
            else fixedDays += publishedHeal(at) / healSpeed;
        }
        return RunShape.scaleFor(target, fixedDays, owned, figure, keep, healSpeed);
    }

    /** Whether every one of the deepest phases at this quarter belongs to another family. */
    private boolean healBorrowedWholly(SurfaceFamily base, int slot) {
        int reached = stagedRun(base).reached;
        int n = RunShape.quarter(WearMath.FRACTIONS[slot - 1], reached);
        for (int phase = reached - n; phase < reached; phase++) {
            SurfaceFamily at = ErosionChain.paceAt(base, phase);
            if (at == base || at == null) return false;
        }
        return n > 0;
    }

    // ------------------------------------------------------------------
    // Publish
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // The shape of a family's run: how many phases, how deep, how far along it may go
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Presets, staged
    // ------------------------------------------------------------------

    /** The rung staged for this axis, or null when none is. */
    private String stagedPreset(String axis) {
        if (sPreset == null) return null;
        for (int i = 0; i < Presets.TUNING_AXES.length; i++) {
            if (Presets.TUNING_AXES[i].equals(axis)) return sPreset[i];
        }
        return null;
    }

    /**
     * This family's own sink depth, as a staged preset would leave it.
     *
     * <p>
     * Its own rather than the one it borrows: a family with no depth of its own keeps none under
     * every rung, and the borrowing below is what then finds it one. Substituting the previewed
     * figure straight into the borrow would give turf a depth it must not have.
     */
    private int previewSink(SurfaceFamily family) {
        FamilySettings own = TrmtConfig.family(family);
        String rung = stagedPreset("depth");
        if (rung == null) return own == null ? 0 : own.maxSinkPixels;
        return Presets.previewed(family, "depth", rung).maxSinkPixels;
    }

    private int previewLayers(SurfaceFamily family) {
        FamilySettings own = TrmtConfig.family(family);
        String rung = stagedPreset("phases");
        if (rung == null) return own == null ? 1 : own.layersPerDepth;
        return Presets.previewed(family, "phases", rung).layersPerDepth;
    }

    private int previewStages(SurfaceFamily family) {
        FamilySettings own = TrmtConfig.family(family);
        String rung = stagedPreset("phases");
        if (rung == null) return own == null ? 0 : own.stages;
        return Presets.previewed(family, "phases", rung).stages;
    }

    /** Dirt skips its own first gradation, because a ghost showing no wear is not a ghost. */
    private static int firstOffset(SurfaceFamily family) {
        return family == SurfaceFamily.DIRT ? 1 : 0;
    }

    /**
     * Draws one of the header's three figures and remembers where it went.
     *
     * <p>
     * Colored like every other staged value on this screen - the ordinary grey until somebody has
     * typed over it, and then the color that says the number on the screen is not yet the number
     * on the server.
     *
     * @return where the next figure should start
     */
    /**
     * One clickable figure on the card's header line.
     *
     * @param right the last column this figure may use. Trimming rather than running off the card
     *              matters now there are four of these: the longest look name is over a hundred
     *              pixels and a narrow window had already run out of line before the fourth was
     *              added. A figure with no room records an empty box rather than keeping the one it
     *              had last time, which is the same trap the not-a-wearing-family guard exists for.
     */
    private int headerFigure(int[] box, int x, int y, int right, String text, boolean staged, boolean editing) {
        int room = right - x;
        if (room < 16) {
            box[0] = 0;
            box[1] = 0;
            box[2] = 0;
            box[3] = 0;
            return x;
        }
        if (fontRendererObj.getStringWidth(text) > room) {
            text = fontRendererObj.trimStringToWidth(text, room - 8) + "...";
        }
        int width = fontRendererObj.getStringWidth(text);
        box[0] = x - 2;
        box[1] = y - 2;
        box[2] = x + width + 2;
        box[3] = y + 10;
        // The same wash the heal cells wear when the edit bar is pointed at them. Without it the
        // bar names a figure the eye cannot find, and three numbers on one line all look alike.
        if (editing) drawRect(box[0], box[1], box[2], box[3], 0x206AD46A);
        drawString(fontRendererObj, text, x, y, staged ? 0xE0D0FF : 0xB8B8C4);
        return x + width + 14;
    }

    private static boolean within(int[] box, int mouseX, int mouseY) {
        return mouseX >= box[0] && mouseX <= box[2] && mouseY >= box[1] && mouseY <= box[3];
    }

    /** What the number in the edit bar is counted in. */
    private String unitLabel() {
        switch (editKind) {
            case KIND_HEAL:
                return "time";
            case KIND_PHASES:
                return "phases";
            case KIND_SINK:
                return "pixels";
            case KIND_CAP:
                return "per cent";
            default:
                return "crossings";
        }
    }

    /** How tall one row of the look picker is, which is the height of a preview plus air. */
    private static final int LOOK_ROW = 22;

    /**
     * The shortest row that can still hold its own label.
     *
     * <p>
     * Not a taste. The label is drawn {@code (row - art) / 2 + 1} into the row and the font is nine
     * pixels tall, so at a pitch of eleven the glyphs reach into the next row's highlight band;
     * twelve is the first pitch at which they do not. It is a floor rather than a pitch anybody
     * chooses - at every screen and interface scale the game itself picks, eleven looks come out at
     * twelve or more.
     *
     * <p>
     * A floor rather than a scrollbar, and the click handler is what rules the scrollbar out: it
     * closes the picker whatever was clicked, so a bar that could be dragged would need that
     * unpicked first, and a wheel-only list that vanishes when a stray pixel is touched would be
     * worse than the thing it fixed.
     */
    private static final int LOOK_ROW_FLOOR = 12;

    /** The picker's width, and the largest a preview is ever drawn, which is the art's own size. */
    private static final int LOOK_WIDTH = 190;

    private static final int LOOK_ART = 16;

    /**
     * Which of the two row pitches this screen has room for.
     *
     * <p>
     * Measured against the card's own bottom rather than against the screen, and that is the whole
     * of the subtlety. The picker is drawn last and over everything and looks modal, but it is not:
     * its click handler passes the click on to the screen afterwards, and any button under the
     * cursor is pressed by that. A row reaching the edit bar would let one click both choose a look
     * and press Revert; a row over the footer would choose a look and leave the screen. Held above
     * {@link #panelBottom} it can reach neither.
     */
    private int lookRow() {
        int pitch = (panelBottom() - (top() + 20) - 8) / Math.max(1, PatternPreview.lookCount());
        if (pitch > LOOK_ROW) pitch = LOOK_ROW;
        return pitch < LOOK_ROW_FLOOR ? LOOK_ROW_FLOOR : pitch;
    }

    /**
     * How many rows the picker actually shows, which is as many as clear the card's bottom.
     *
     * <p>
     * The one guard here, and the only thing that turns a silent fault into a visible one. Both the
     * drawing and the hit test used to walk the whole list, so a row that overflowed the band was
     * drawn past the card AND still answered a click - and since the click goes on to the screen
     * afterwards, a row reaching the edit bar would choose a look and press Revert with nothing
     * thrown and nothing in the log. Counting to this instead keeps the picker's height inside the
     * band, so a look that will not fit is neither drawn nor clickable: a visibly short picker,
     * which is the failure worth having.
     *
     * <p>
     * Nothing is hidden today, and the room left over is now one row rather than two. Eleven looks
     * fit whole above a scaled height of 232 and the automatic scale never goes below 240, where they
     * land exactly on the floor pitch; the twelfth needs 244 and is where this arrives - as a missing
     * row rather than as a click that does two things. The four pixels of air above and below the
     * rows are where a twelfth would have to be found, and finding it there is the whole of the fix.
     */
    private int lookRowsShown() {
        int band = panelBottom() - (top() + 20);
        return Math.max(0, Math.min(PatternPreview.lookCount(), (band - 8) / lookRow()));
    }

    /** A preview is the row less the air around it, at whichever pitch is in use. */
    private int lookArt() {
        int art = lookRow() - 2;
        return art > LOOK_ART ? LOOK_ART : art;
    }

    private int lookPickerHeight() {
        return 8 + lookRowsShown() * lookRow();
    }

    /**
     * Where the picker's top edge goes: over the card as it always has when it fits there, and slid
     * up toward the title rule when it does not.
     *
     * <p>
     * One method because the drawing and the hit test used to work this out separately, which was
     * harmless while the answer was a constant and is a live bug the moment it stops being one:
     * draw in one place and test in another and every click lands a row or two out, with nothing
     * thrown and nothing in the log to find it by.
     */
    private int lookPickerTop() {
        return Math.max(top() + 20, Math.min(top() + 46, panelBottom() - lookPickerHeight()));
    }

    /**
     * Draws the picker, when it is open, over the card it belongs to.
     *
     * <p>
     * Over rather than beside, because the thing being chosen changes the whole card underneath and
     * the comparison worth making is between the three looks rather than between a look and the
     * numbers. It closes on any click, including one that chooses nothing.
     */
    /**
     * Which row is under a point, or -1 for none.
     *
     * <p>
     * The one place a row is measured against the cursor, asked by the drawing and by the click
     * alike. Two copies of it would be two chances to disagree, and the disagreement would not look
     * like a bug: the picker would light one row and choose another, silently, with the mouse
     * sitting still between the two. The same argument put the picker's top edge in one method when
     * the pitch stopped being a constant, and it applies twice over now that a row lights up.
     */
    private int lookRowAt(int mouseX, int mouseY) {
        if (mouseX < cl() || mouseX > cl() + LOOK_WIDTH) return -1;
        int row = lookRow();
        int top = lookPickerTop();
        // To the rows that are actually drawn, and off the same method the drawing counts to, so a
        // row nobody can see is a row nobody can click.
        for (int i = 0; i < lookRowsShown(); i++) {
            int y = top + 4 + i * row;
            if (mouseY >= y - 1 && mouseY <= y + row - 3) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // The curve picker
    // ------------------------------------------------------------------

    private static final int CURVE_WIDTH = 200;

    private static final int CURVE_ROW = 22;

    /** How wide the little plot is, and so how many samples of the run it holds. */
    private static final int PLOT_WIDTH = 100;

    private static final int PLOT_HEIGHT = 16;

    /**
     * Steps one chip along its ladder, forwards on a left click and back on a right.
     *
     * <p>
     * Custom is a place a chooser can be but never a place it can be asked for: it is what the
     * config says when the numbers match no rung, so it is arrived at by editing them. Stepping
     * from custom therefore enters the ladder at one end rather than moving along from nowhere.
     *
     * <p>
     * Landing back on the rung the settings already match clears the staging rather than staging a
     * change of nothing, which is the rule both pickers use, so that the violet only ever means
     * something is genuinely pending.
     */
    private void cyclePreset(int index, int step) {
        String axis = Presets.TUNING_AXES[index];
        String[] rungs = Presets.rungsOf(axis);
        if (rungs.length == 0) return;

        String shown = sPreset[index] != null ? sPreset[index] : Presets.current(axis);
        int at = -1;
        for (int i = 0; i < rungs.length; i++) {
            if (rungs[i].equals(shown)) at = i;
        }
        int next = at < 0 ? (step > 0 ? 0 : rungs.length - 1)
            : ((at + step) % rungs.length + rungs.length) % rungs.length;

        String picked = rungs[next];
        sPreset[index] = picked.equals(Presets.current(axis)) ? null : picked;

        // A rung and a typed figure on the same axis are two answers to one question, and the rung
        // is applied last on the server, so the figure would be published and then overwritten.
        // The two are therefore never staged together on one axis: choosing a rung drops what was
        // typed on its axis here, and typing a figure drops the rung in commit. Dropping at the
        // moment of choosing rather than at publish makes the card show what will actually happen
        // instead of a figure that is about to be discarded.
        if (sPreset[index] != null) clearAxisEdits(axis);
        // Whether it was staged or unstaged, a rung can move a depth or a layer count that some typed
        // length was shared out against.
        showNotice(null, settleLengths());
        // An open edit is judged against the figure it was filled with, and a rung can move that
        // figure. Left open, the old figure typed back in would read as no edit at all, and the
        // rung would stay staged when typing a figure is meant to drop it. So the edit is let go,
        // as Revert and choosing another family let it go.
        closeEdit();
        field.setText("");
    }

    /** Drops whatever was typed on the axis a rung has just taken over. */
    private void clearAxisEdits(String axis) {
        for (SurfaceFamily f : families) {
            int i = f.ordinal();
            if ("wear".equals(axis)) kCross[i] = 1d;
            else if ("healing".equals(axis)) mHeal[i] = 1d;
            else if ("phases".equals(axis)) sPhases[i] = 0;
            else if ("depth".equals(axis)) sSink[i] = -1;
        }
    }

    private int curveRowsShown() {
        int band = panelBottom() - (top() + 20);
        int fits = (band - 8) / CURVE_ROW;
        if (fits < 0) fits = 0;
        return Math.min(CostCurve.values().length, fits);
    }

    private int curvePickerHeight() {
        return 8 + curveRowsShown() * CURVE_ROW;
    }

    private int curvePickerTop() {
        return Math.max(top() + 20, Math.min(top() + 46, panelBottom() - curvePickerHeight()));
    }

    /**
     * Which row the cursor is over, or minus one.
     *
     * <p>
     * The one place a row is measured, asked by the drawing and by the click alike, so that a row
     * nobody can see is a row nobody can click - and so that a row cannot land on the buttons
     * underneath, since a click here goes on to the screen's own handler afterwards.
     */
    private int curveRowAt(int mouseX, int mouseY) {
        if (mouseX < cl() || mouseX > cl() + CURVE_WIDTH) return -1;
        int top = curvePickerTop();
        for (int i = 0; i < curveRowsShown(); i++) {
            int y = top + 4 + i * CURVE_ROW;
            if (mouseY >= y - 1 && mouseY <= y + CURVE_ROW - 3) return i;
        }
        return -1;
    }

    /**
     * Works out the five pictures, at the run length the selected family actually has.
     *
     * <p>
     * Once when the picker opens rather than once a frame: asking a curve for a multiplier walks the
     * whole run whenever the average for that length is not already worked out, and this asks five
     * hundred times.
     *
     * <p>
     * A fixed axis of nought to two on every row, never fitted to what each curve happens to reach.
     * Every normalised curve lies between about a sixth and a little under two, so a per-row axis
     * would draw the flat one and the steepest one as the same picture - the one thing a gallery of
     * five must not do.
     */
    private void buildCurvePlot() {
        CostCurve[] all = CostCurve.values();
        int length = Math.max(2, stagedRun(selectedFamily()).full);
        curvePlot = new int[all.length][PLOT_WIDTH];
        for (int c = 0; c < all.length; c++) {
            for (int px = 0; px < PLOT_WIDTH; px++) {
                int index = Math.round(px * (length - 1) / (float) (PLOT_WIDTH - 1));
                float value = all[c].at(index, length);
                int h = Math.round(value / 2f * PLOT_HEIGHT);
                if (h < 1) h = 1;
                if (h > PLOT_HEIGHT) h = PLOT_HEIGHT;
                curvePlot[c][px] = h;
            }
        }
    }

    /** How a rise reads on a chip: "even" for the flat one, otherwise how many times dearer. */
    private static String riseLabel(CostCurve curve) {
        float rise = curve.rise();
        if (rise <= 1f) return "even";
        return rise == Math.round(rise) ? "x" + (int) rise : "x" + rise;
    }

    /**
     * Whether this axis is the server's to decide rather than this client's.
     *
     * <p>
     * Phases and depth are stamped back over a client by {@code ServerRules} immediately after the
     * presets are applied, inside the same read - so on a server that owns the geometry, choosing
     * either of those would preview something that could never happen. Greyed and refused rather
     * than allowed and quietly undone.
     */
    private boolean serverOwns(String axis) {
        if (!"phases".equals(axis) && !"depth".equals(axis)) return false;
        return ServerRules.held();
    }

    /**
     * Four chips at the foot of the card, one an axis, naming a whole opinion at once.
     *
     * <p>
     * At the foot rather than on the header line, because these are not properties of the family
     * that happens to be selected - they move every family together, and putting them beside the
     * per-family figures would say otherwise.
     *
     * <p>
     * Two lines of two rather than four across: the longest chip is over a hundred pixels and four
     * of those do not fit the card at a narrow window. The second column is the measured midpoint
     * rather than a fixed offset, or a narrow card leaves eighty pixels for a hundred-pixel chip.
     */
    private void drawPresetChips(int cl, int cr, int t) {
        if (panelBottom() < t + 213) return;

        drawRect(cl, t + 170, cr, t + 171, 0x30FFFFFF);
        drawString(
            fontRendererObj,
            StatCollector.translateToLocal("trmtgtnh.weareditor.presets"),
            cl,
            t + 176,
            0xC9C9D6);

        int half = (cr - cl) / 2;
        // One hover for all four. What a chip does to figures typed on its axis is the one thing
        // about it nobody can see from the chip.
        List<String> tip = wrapped("§7", StatCollector.translateToLocal("trmtgtnh.weareditor.presets.tip"));
        for (int i = 0; i < Presets.TUNING_AXES.length; i++) {
            String axis = Presets.TUNING_AXES[i];
            String shown = sPreset[i] != null ? sPreset[i] : Presets.current(axis);
            int x = cl + (i % 2 == 0 ? 0 : half);
            int y = t + 188 + (i / 2) * 12;
            int right = x + half - 6;
            if (serverOwns(axis)) {
                // Its own box is still recorded, so the click test has something to reject against
                // rather than falling through to whatever was drawn there last.
                headerFigure(presetBox[i], x, y, right, axis + ": " + shown, false, false);
                drawString(fontRendererObj, axis + ": " + shown, x, y, 0xFF808080);
            } else {
                headerFigure(presetBox[i], x, y, right, axis + ": " + shown, sPreset[i] != null, false);
            }
            addBoxHover(presetBox[i], tip);
        }
    }

    private void drawCurvePicker(int cl, int mouseX, int mouseY) {
        CostCurve[] all = CostCurve.values();
        if (curvePlot == null) buildCurvePlot();

        int top = curvePickerTop();
        int height = curvePickerHeight();
        int over = curveRowAt(mouseX, mouseY);
        drawRect(cl, top, cl + CURVE_WIDTH, top + height, 0xF0141420);
        drawRect(cl, top, cl + CURVE_WIDTH, top + 1, 0xFF6A6A7A);
        drawRect(cl, top + height - 1, cl + CURVE_WIDTH, top + height, 0xFF6A6A7A);

        CostCurve current = shapeCurve(selectedFamily());
        int rows = curveRowsShown();
        for (int i = 0; i < rows; i++) {
            int y = top + 4 + i * CURVE_ROW;
            boolean chosen = all[i] == current;
            boolean under = i == over;
            // The cursor first and the choice over the top, so a row that is both comes out
            // brightest rather than one wash hiding the other. White says where the mouse is and
            // the violet says what this family is set to, which are different questions.
            if (under) drawRect(cl + 2, y - 1, cl + CURVE_WIDTH - 2, y + CURVE_ROW - 3, 0x22FFFFFF);
            if (chosen) drawRect(cl + 2, y - 1, cl + CURVE_WIDTH - 2, y + CURVE_ROW - 3, 0x30E0D0FF);
            int label = chosen ? 0xE0D0FF : 0xB8B8C4;
            if (under) label = chosen ? 0xF4ECFF : 0xFFFFFF;

            drawString(fontRendererObj, all[i].key(), cl + 8, y + 6, label);
            drawString(fontRendererObj, riseLabel(all[i]), cl + 44, y + 6, 0x8C8CA0);

            int x0 = cl + 76;
            int y0 = y + 2;
            // A ground behind the columns, or the steepest curve's opening is invisible.
            drawRect(x0, y0, x0 + PLOT_WIDTH, y0 + PLOT_HEIGHT, 0x14FFFFFF);
            // One on a nought-to-two axis is exactly halfway up, so the flat curve's columns lie
            // along this line for their whole length - the plainest statement of what flat means.
            drawRect(x0, y0 + PLOT_HEIGHT / 2, x0 + PLOT_WIDTH, y0 + PLOT_HEIGHT / 2 + 1, 0x30FFFFFF);
            // Filled columns rather than a line: at sixteen pixels a one-pixel line with a shallow
            // slope reads as a dashed smear, and the area under it is the thing being compared.
            int fill = chosen ? 0xFFE0D0FF : (under ? 0xFFFFFFFF : 0xFF8C8CA0);
            for (int px = 0; px < PLOT_WIDTH; px++) {
                int h = curvePlot[i][px];
                drawRect(x0 + px, y0 + PLOT_HEIGHT - h, x0 + px + 1, y0 + PLOT_HEIGHT, fill);
            }
        }
    }

    private void drawLookPicker(int cl, int mouseX, int mouseY) {
        String[] looks = PatternPreview.looks();
        int row = lookRow();
        int art = lookArt();
        int top = lookPickerTop();
        int height = lookPickerHeight();
        int over = lookRowAt(mouseX, mouseY);
        drawRect(cl, top, cl + LOOK_WIDTH, top + height, 0xF0141420);
        drawRect(cl, top, cl + LOOK_WIDTH, top + 1, 0xFF6A6A7A);
        drawRect(cl, top + height - 1, cl + LOOK_WIDTH, top + height, 0xFF6A6A7A);

        // Folded once rather than once a row: the answer cannot change between rows, and it is a
        // walk of the whole list every time it is asked.
        String current = PatternPreview.canonical(shapePattern(selectedFamily()));
        ItemStack shown = cycled();
        int tint = tintOf(shown);
        int rows = lookRowsShown();
        for (int i = 0; i < rows; i++) {
            int y = top + 4 + i * row;
            boolean chosen = looks[i].equals(current);
            boolean under = i == over;
            // Under the cursor first and chosen over the top of it, so a row that is both comes out
            // brightest of all rather than one of the two washes hiding the other. Plain white for
            // the cursor and the violet for the choice, which is the difference worth keeping: one
            // says where the mouse is and the other says what this family is actually set to, and a
            // picker that told you those in the same color would be answering the wrong question.
            if (under) drawRect(cl + 2, y - 1, cl + LOOK_WIDTH - 2, y + row - 3, 0x22FFFFFF);
            if (chosen) drawRect(cl + 2, y - 1, cl + LOOK_WIDTH - 2, y + row - 3, 0x30E0D0FF);
            int label = chosen ? 0xE0D0FF : 0xB8B8C4;
            if (under) label = chosen ? 0xF4ECFF : 0xFFFFFF;
            drawString(fontRendererObj, PatternPreview.nameOf(looks[i]), cl + 8, y + (row - art) / 2 + 1, label);
            for (int step = 0; step < 3; step++) {
                ResourceLocation at = previews.of(selectedFamily(), shown, looks[i], step);
                if (at == null) continue;
                // The block's own multiplier, because a picture of worn sandstone drawn in flat
                // white is a picture of a different block. Not the biome tint, which is a property
                // of where you are standing rather than of the look.
                GL11.glColor4f(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, 1f);
                mc.getTextureManager()
                    .bindTexture(at);
                // The whole of a sixteen-pixel image rather than a corner of a two-hundred-and-
                // fifty-six-pixel sheet, which is what the ordinary blit assumes. The source stays
                // the whole face and only the destination shrinks, so the tight pitch costs
                // resolution on the screen and nothing in the picture behind it.
                Gui.func_152125_a(cl + 118 + step * (art + 4), y, 0f, 0f, 16, 16, art, art, 16f, 16f);
            }
        }
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** The color a block multiplies its own texture by, or plain white when it does not. */
    private static int tintOf(ItemStack shown) {
        if (shown == null) return 0xFFFFFF;
        net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(shown.getItem());
        return block == null ? 0xFFFFFF : block.getRenderColor(shown.getItemDamage());
    }

    /**
     * The block this family's row is showing at the moment, which the pictures are drawn on.
     *
     * <p>
     * The same cursor the icon beside the row uses, so the two never disagree - a picture of a look
     * on a block other than the one named beside it would be worse than no picture at all.
     */
    private ItemStack cycled() {
        return iconFor(selectedFamily());
    }

    /** Which look this family is set to, staged or live. */
    private String shapePattern(SurfaceFamily family) {
        String staged = sPattern[family.ordinal()];
        if (staged != null) return staged;
        FamilySettings own = TrmtConfig.family(family);
        return own == null || own.wearPattern == null ? FamilySettings.PATTERN_DIRT : own.wearPattern;
    }

    private int countStaged() {
        int n = 0;
        for (int i = 0; i < Presets.TUNING_AXES.length; i++) {
            if (sPreset[i] != null) n++;
        }
        for (SurfaceFamily f : families) {
            if (kCross[f.ordinal()] != 1d || mHeal[f.ordinal()] != 1d || shapeStaged(f)) n++;
        }
        return n;
    }

    /** Whether anything about this family's shape has been typed over. */
    private boolean shapeStaged(SurfaceFamily family) {
        int i = family.ordinal();
        return sPhases[i] > 0 || sSink[i] >= 0 || sCap[i] >= 0f || sPattern[i] != null || sCurve[i] != null;
    }

    /**
     * Drops everything staged for one family, by ordinal: crossings, heal time, phases, depth,
     * ceiling, curve and look. Not the presets, which belong to every family at once.
     */
    private void revertFamily(int i) {
        kCross[i] = 1d;
        mHeal[i] = 1d;
        sPhases[i] = 0;
        sSink[i] = -1;
        sCap[i] = -1f;
        sPattern[i] = null;
        sCurve[i] = null;
    }

    private void discardAll() {
        for (int i = 0; i < kCross.length; i++) revertFamily(i);
        for (int i = 0; i < Presets.TUNING_AXES.length; i++) sPreset[i] = null;
        lookOpen = false;
        curveOpen = false;
        curvePlot = null;
        closeEdit();
        field.setText("");
    }

    private List<String> stagedLines() {
        List<String> lines = new ArrayList<String>();
        // The chosen rungs first. A rung is applied inside the read that follows, after every value
        // this list sets, so anything it owns would be overwritten a moment later. That is why a rung
        // and a typed figure are never staged together on one axis - choosing either drops the
        // other - and why nothing below goes out on an axis a rung is taking.
        for (int i = 0; i < Presets.TUNING_AXES.length; i++) {
            if (sPreset[i] != null) {
                lines.add(Presets.CATEGORY + "\t" + Presets.TUNING_AXES[i] + "\t" + sPreset[i]);
            }
        }
        boolean wearRung = stagedPreset("wear") != null;
        boolean healRung = stagedPreset("healing") != null;
        for (SurfaceFamily f : families) {
            FamilySettings fs = TrmtConfig.family(f);
            if (fs == null) continue;
            int i = f.ordinal();
            String cat = TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER + f.key();

            // No look here; see stagedLooks. It never was reaching anybody through this list
            // anyway - the handler writes the server's own file and the rules that come back have
            // no field for a look - so on a dedicated server publishing one moved nobody's pixels,
            // the publisher's included.

            // The curve goes out with the numbers rather than being applied locally like the look,
            // and the difference is which machine owns it: a look is a picture this client draws
            // for itself, and a curve decides what the engine charges for a gradation, which the
            // server does. Absolute rather than a multiplier, so publishing it twice publishes it
            // once.
            CostCurve curve = sCurve[f.ordinal()];
            if (curve != null) lines.add(cat + "\tcostCurve\t" + curve.key());

            // The shape, and the compensation for it worked out here rather than staged separately.
            // A run that is half as long has to cost twice as much a step or the end of it moves,
            // and holding that as its own piece of state is how the two halves come apart: publish
            // twice and the compensation is applied twice. Derived from the staged run at the
            // moment of publishing, it cannot. RunShape decides which lines go out: a depth of
            // nought as nought rather than as the depth it would borrow, and a length only when it
            // can be shared out, so a depth typed on its own never republishes a split nobody
            // asked for.
            RunShape.Published shape = published(f);
            if (shape.maxSinkPixels >= 0) lines.add(cat + "\tmaxSinkPixels\t" + shape.maxSinkPixels);
            if (shape.stages >= 0) lines.add(cat + "\tstages\t" + shape.stages);
            if (shape.layersPerDepth >= 0) lines.add(cat + "\tlayersPerDepth\t" + shape.layersPerDepth);
            // In hundredths, which is what was typed. The float it is held in would otherwise go out
            // as 0.800000011920929.
            if (sCap[i] >= 0f) lines.add(cat + "\tmaxWear\t" + num(Math.round(sCap[i] * 100f) / 100d));

            // Nothing on an axis a rung is taking. The rung overwrites these in the same read, so
            // they could change nothing on the ground - but the server remembers the figures it held
            // on custom as the rung moves its chooser away, and these would be what it remembered,
            // in place of the custom set somebody had there.
            double cross = kCross[i] * shape.keepEnd;
            double heal = mHeal[i] * shape.keepEnd;
            if (cross != 1d && !wearRung) {
                lines.add(cat + "\tthresholdMin\t" + num(fs.thresholdMin * cross));
                lines.add(cat + "\tthresholdMax\t" + num(fs.thresholdMax * cross));
            }
            if (heal != 1d && !healRung) {
                lines.add(cat + "\thealDaysPerStage\t" + num(fs.healDaysPerStage * heal));
            }
        }
        return lines;
    }

    /**
     * The looks staged, which go nowhere near the server.
     *
     * <p>
     * The same shape as {@link #stagedLines} so that the preview can draw both the same way, but
     * this half is applied to this client and written to its own config. Kept as its own list rather
     * than flagged inside the other one, because the two are told apart on screen and a caller that
     * forgot which was which would send somebody's cosmetic preference to a machine that draws
     * nothing.
     */
    private List<String> stagedLooks() {
        List<String> lines = new ArrayList<String>();
        for (SurfaceFamily f : families) {
            if (sPattern[f.ordinal()] == null) continue;
            lines.add(
                TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER
                    + f.key()
                    + "\twearPattern\t"
                    + sPattern[f.ordinal()]);
        }
        return lines;
    }

    private void publish() {
        // The looks first, and locally. Applied before the numbers go out so that the rebuild the
        // last one asks for is queued before anything else can be waiting on the server; it is held
        // while this screen is open in any case, and lands once, a moment after it closes.
        for (SurfaceFamily f : families) {
            String look = sPattern[f.ordinal()];
            if (look == null) continue;
            sPattern[f.ordinal()] = null;
            Trmt.proxy.setWearLook(f, look);
        }

        List<String> lines = stagedLines();
        // Taken before anything below is cleared, because both are read from what is staged: the
        // compensation for a typed length comes from the staged run, and the rungs are part of it.
        boolean held = ServerRules.held();
        boolean wearRungSent = stagedPreset("wear") != null;
        boolean healRungSent = stagedPreset("healing") != null;
        double[] keep = new double[kCross.length];
        for (int i = 0; i < keep.length; i++) keep[i] = 1d;
        if (held) {
            for (SurfaceFamily f : families) keep[f.ordinal()] = keepEnd(f);
        }

        if (!lines.isEmpty()) TrmtNetwork.pushConfig(lines);
        // Cleared here rather than left for the broadcast to settle, because a curve is absolute:
        // the rules that come back carry it now, so a staged copy left in place would go on
        // claiming a change that has already landed.
        for (SurfaceFamily f : families) sCurve[f.ordinal()] = null;
        for (int i = 0; i < Presets.TUNING_AXES.length; i++) sPreset[i] = null;
        // In a world of your own the settings this card multiplies are the very ones the push has
        // just rewritten, because the integrated server and this screen share them. A scale left
        // staged there is applied again on top of its own result: the card shows twice the change,
        // and a second press of Publish, made reasonably by somebody who thinks the first did
        // nothing, sends it twice over into the file. So there everything numeric is let go.
        //
        // On somebody else's server the card prices by this client's own file, which a push does
        // not touch, so the scales stay staged; the server says in chat how many it applied. Where
        // that server holds the geometry, though, the rules it sends back stamp the new length onto
        // this client, and the compensation for a typed length is worked out from the difference
        // between the old run and the new - which by then is none. Left derived, it would fall back
        // to one, and the next Publish, of anything at all, would send the thresholds without it and
        // undo the first. So it is folded into the scales here and the typed length let go, and the
        // card shows exactly what the next Publish would send whether this push lands or is refused.
        // Where a rung went out, the rung's figures are what landed and that axis's scale goes back
        // to one. Depths and ceilings are absolute and stay as they are.
        //
        // The looks are not among any of this and are cleared above, because there is no broadcast
        // coming for those.
        if (mc != null && mc.isIntegratedServerRunning()) {
            for (int i = 0; i < kCross.length; i++) {
                kCross[i] = 1d;
                mHeal[i] = 1d;
                sPhases[i] = 0;
                sSink[i] = -1;
                sCap[i] = -1f;
            }
        } else if (held) {
            for (int i = 0; i < kCross.length; i++) {
                kCross[i] = wearRungSent ? 1d : kCross[i] * keep[i];
                mHeal[i] = healRungSent ? 1d : mHeal[i] * keep[i];
                sPhases[i] = 0;
            }
        }
        // Whatever the edit bar was filled with was read from figures this has just sent or let
        // go, so it is let go too, for the same reason a preset click lets it go.
        closeEdit();
        field.setText("");
    }

    private void drawPreview(int mouseX, int mouseY) {
        drawRect(0, 0, width, height, 0xC0000000);
        int cx = width / 2;
        int pl = cx - 150;
        int pr = cx + 150;
        int pt = Math.max(20, height / 2 - 90);
        int pbty = pt + 180;

        drawRect(pl - 1, pt - 1, pr + 1, pbty + 1, 0xFF7A5AA6);
        drawRect(pl, pt, pr, pbty, 0xE61B1330);
        drawGradientRect(pl, pt, pr, pt + 18, 0xFF3A2A55, 0xFF261A3A);
        drawRect(pl, pt + 18, pr, pt + 19, 0xFF7A5AA6);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("trmtgtnh.weareditor.pubtitle"),
            cx,
            pt + 5,
            0xFFFFFF);

        List<String> lines = stagedLines();
        List<String> looks = stagedLooks();
        drawCenteredString(
            fontRendererObj,
            (lines.size() + looks.size()) + " " + StatCollector.translateToLocal("trmtgtnh.weareditor.pubcount"),
            cx,
            pt + 22,
            0xC9A0FF);
        // Two headed blocks rather than one list. Half of these leave the machine and half do not,
        // and somebody watching one number go up has no way to tell which line did which.
        int y = pt + 36;
        y = drawSection(lines, "trmtgtnh.weareditor.pubsent", pl, y, pbty);
        y = drawSection(looks, "trmtgtnh.weareditor.pubkept", pl, y, pbty);

        // Three buttons, drawn and hit-tested by hand so they exist only while the modal is up.
        int by = pbty - 22;
        drawFakeButton(pl + 8, by, 70, StatCollector.translateToLocal("gui.cancel"), mouseX, mouseY);
        drawFakeButton(cx - 35, by, 70, StatCollector.translateToLocal("trmtgtnh.weareditor.discard"), mouseX, mouseY);
        drawFakeButton(pr - 78, by, 70, StatCollector.translateToLocal("trmtgtnh.weareditor.send"), mouseX, mouseY);
    }

    /** One headed block of staged lines, returning where the next one starts. */
    private int drawSection(List<String> lines, String headingKey, int pl, int y, int pbty) {
        if (lines.isEmpty()) return y;
        drawString(fontRendererObj, StatCollector.translateToLocal(headingKey), pl + 8, y, 0x8C8CA0);
        y += 11;
        for (String line : lines) {
            if (y > pbty - 30) {
                drawString(fontRendererObj, "...", pl + 8, y, 0x8C8CA0);
                return y + 11;
            }
            String[] parts = line.split("\t");
            String label = parts[0].replace("families" + Configuration.CATEGORY_SPLITTER, "") + " " + parts[1];
            drawString(fontRendererObj, label, pl + 8, y, 0xB8B8C4);
            drawString(fontRendererObj, "-> " + parts[2], pl + 8 + 150, y, 0xFFFFFF);
            y += 11;
        }
        return y;
    }

    /** A button that lives only while the preview is drawn; hit-tested in {@link #mouseClicked}. */
    private void drawFakeButton(int x, int y, int w, String label, int mouseX, int mouseY) {
        boolean over = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 18;
        drawRect(x, y, x + w, y + 18, over ? 0xFF6A6A7A : 0xFF3A3A46);
        drawRect(x + 1, y + 1, x + w - 1, y + 17, over ? 0xFF505062 : 0xFF26262E);
        drawCenteredString(fontRendererObj, label, x + w / 2, y + 5, 0xFFFFFF);
    }

    /** The three preview button rects, in the same order they are drawn. */
    private int[][] previewButtons() {
        int cx = width / 2;
        int pl = cx - 150;
        int pr = cx + 150;
        int pt = Math.max(20, height / 2 - 90);
        int by = pt + 180 - 22;
        return new int[][] { { pl + 8, by, 70 }, { cx - 35, by, 70 }, { pr - 78, by, 70 } };
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    /** The value one crossings or heal cell shows, by slot, from a row already in hand. */
    private static double cellOf(WearMath.Row row, int kind, int slot) {
        if (kind == KIND_HEAL) return slot == 0 ? row.healDaysPerPhase : row.healDays[slot - 1];
        return slot == 0 ? row.crossingsPerPhase : row.crossings[slot - 1];
    }

    private static String join(List<String> parts, String between) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (out.length() > 0) out.append(between);
            out.append(part);
        }
        return out.toString();
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.endsWith(".00") ? s.substring(0, s.length() - 3) : s;
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%s", v);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static final class Rect {

        final int x1;
        final int y1;
        final int x2;
        final int y2;

        Rect(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        boolean has(int x, int y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }
}
