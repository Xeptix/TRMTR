package com.trmtgtnh.config;

import java.util.Locale;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Ready-made answers to the questions this config asks most often.
 *
 * <p>
 * Five choosers, each owning one axis and nothing else, so that wanting slow wear and quick recovery
 * is a thing somebody can ask for rather than a compromise they have to accept. A single bundled
 * preset cannot express that, which is why there is not one.
 *
 * <p>
 * Every chooser also offers <b>custom</b>, and custom is not a rung - it is what the config says
 * when the numbers underneath do not match any rung. Two rules make that work, and getting either
 * wrong is what makes preset systems infuriating:
 *
 * <ul>
 * <li>Editing a value while a rung is chosen moves the chooser to custom rather than being silently
 * overwritten on the next read. What somebody typed is what they meant.</li>
 * <li>Going from custom to a rung and back to custom gives back <em>the same</em> custom values.
 * They are remembered on the way out, not discarded.</li>
 * </ul>
 *
 * <p>
 * Those two need one thing the config cannot otherwise tell: whether the chooser moved or the values
 * did. Both look identical afterwards - a chooser saying one thing and numbers saying another - so
 * each axis also records which rung it last applied. Chooser different from that record means the
 * chooser moved; chooser the same but values adrift means somebody edited. The record and the
 * remembered values live under {@code presets.remembered}, which is bookkeeping rather than
 * settings, and says so.
 *
 * <p>
 * A rung is always measured against the <em>shipped</em> defaults rather than against whatever is
 * in the file, so applying one twice is applying it once and a rung means the same thing on every
 * world. Server-owned geometry still wins: presets are applied before {@code ServerRules.reassert},
 * so a visitor's chosen depth cannot argue with the depth its host is running.
 */
public final class Presets {

    public static final String CATEGORY = "presets";

    private static final String REMEMBERED = CATEGORY + Configuration.CATEGORY_SPLITTER + "remembered";

    public static final String CUSTOM = "custom";

    private Presets() {}

    /** The axes, in the order they appear on screen. */
    private enum Axis {

        QUALITY("quality", new String[] { "potato", "low", "default", "high", "ultra" }),
        WEAR("wear", new String[] { "very_slow", "slow", "default", "brisk", "quick" }),
        HEALING("healing", new String[] { "very_slow", "slow", "default", "brisk", "quick" }),
        PHASES("phases", new String[] { "default", "fewer", "coarse", "very_coarse" }),
        DEPTH("depth", new String[] { "none", "shallow", "default", "deep", "deepest" });

        final String key;

        final String[] rungs;

        Axis(String key, String[] rungs) {
            this.key = key;
            this.rungs = rungs;
        }

        String[] choices() {
            String[] all = new String[rungs.length + 1];
            System.arraycopy(rungs, 0, all, 0, rungs.length);
            all[rungs.length] = CUSTOM;
            return all;
        }

        boolean knows(String name) {
            for (String rung : rungs) {
                if (rung.equals(name)) return true;
            }
            return false;
        }
    }

    /**
     * Settles every chooser and writes the result into the live settings.
     *
     * <p>
     * Called from inside {@code TrmtConfig.read}, after the family map has been rebuilt from the
     * file and before anything is compiled out of it - the same window {@code ServerRules} uses,
     * and immediately before it, so that a server's geometry has the last word.
     */
    public static void applyAll(Configuration config) {
        config.setCategoryComment(
            CATEGORY,
            "Ready-made answers to the questions below, one chooser an axis so that wanting slow wear and quick recovery is a thing you can ask for rather than a compromise. Each also offers 'custom', which is what a chooser says when the numbers underneath it do not match any of its rungs. Editing one of those numbers moves the chooser to custom rather than having your edit overwritten; going custom, then to a rung, then back to custom gives you the same numbers you had. The bookkeeping that makes the second one possible lives under presets.remembered and is not meant to be read.");
        config.setCategoryComment(
            REMEMBERED,
            "Not settings. This is where a chooser keeps the numbers you had before you picked a rung, so that going back to 'custom' gives them back rather than leaving you to type them again, and where it records which rung it last applied so it can tell a chooser being moved from a value being edited. Written automatically. Editing anything here by hand does nothing useful and may lose the values it is holding for you.");

        for (Axis axis : Axis.values()) {
            settle(config, axis);
        }
    }

    private static void settle(Configuration config, Axis axis) {
        Property chooser = config.get(CATEGORY, axis.key, "default", describe(axis), axis.choices());
        String wanted = normalise(chooser.getString());
        if (!axis.knows(wanted) && !CUSTOM.equals(wanted)) {
            Trmt.LOG.warn(
                "presets.{} is set to {}, which is not one of its choices; treating it as custom and leaving your own numbers alone",
                axis.key,
                wanted);
            wanted = CUSTOM;
        }

        Property record = config.get(
            REMEMBERED,
            axis.key + "LastApplied",
            CUSTOM,
            "Bookkeeping rather than a setting, and written automatically. It records which rung the " + axis.key
                + " chooser last applied, which is the one thing a config file cannot work out for itself. A chooser that has been moved, and a number that has been edited under a chooser that has not, leave the file looking exactly the same afterwards - a chooser saying one thing and numbers saying another - and the two deserve opposite answers, because a chooser that moved should apply its rung while an edit should be kept and carry the chooser to custom. This record is what tells them apart. Changing it by hand tells the next read that the wrong one of those happened.");
        String lastApplied = normalise(record.getString());
        Property kept = config.get(
            REMEMBERED,
            axis.key + "Custom",
            "",
            "Bookkeeping rather than a setting, and written automatically. It holds the " + axis.key
                + " numbers that were in the file before a rung was picked, so that going back to 'custom' hands them straight back rather than leaving every family to be typed in again. Emptying it breaks nothing: with nothing remembered, 'custom' keeps whatever numbers the file has at the time, which is what asking for custom means anyway, and the log says so when it happens - but the older set this was holding is then gone for good.");

        if (!wanted.equals(lastApplied)) {
            // The chooser moved. What is in the file belongs to where it moved FROM, so it is worth
            // keeping only if that was custom - a rung's numbers can always be worked out again.
            if (CUSTOM.equals(lastApplied)) kept.set(encode(axis));
            if (CUSTOM.equals(wanted)) {
                if (!restore(axis, kept.getString())) {
                    // Nothing was ever kept - this world has only ever been on a rung. Leaving the
                    // numbers exactly where they are is the honest answer: they become the custom
                    // ones, which is what somebody switching to custom is asking to be allowed.
                    Trmt.LOG.info(
                        "presets.{} moved to custom with nothing remembered, so the numbers it already had are now yours to edit",
                        axis.key);
                }
            } else {
                apply(axis, wanted);
            }
            push(config, axis);
            record.set(wanted);
            chooser.set(wanted);
            return;
        }

        if (CUSTOM.equals(wanted)) {
            // Steady state on custom: keep the file's numbers as the ones to give back later.
            kept.set(encode(axis));
            return;
        }

        // The chooser did not move. If the numbers no longer match the rung it names, somebody
        // edited them, and an edit outranks a rung - so the chooser follows the numbers rather than
        // the numbers being quietly put back.
        if (matches(axis, wanted)) return;
        chooser.set(CUSTOM);
        record.set(CUSTOM);
        kept.set(encode(axis));
        Trmt.LOG.info(
            "presets.{} moved itself to custom because its settings no longer match the {} rung; your edits are kept",
            axis.key,
            wanted);
    }

    private static String normalise(String raw) {
        return raw == null ? CUSTOM
            : raw.trim()
                .toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Asking a rung what it would do, without doing it
    // ------------------------------------------------------------------

    /**
     * The axes worth previewing on the wear screens. Quality is not among them.
     *
     * <p>
     * Quality moves five texture settings and reaches neither the wear arithmetic nor the chain, so
     * on a screen made of crossings and recovery times it would be a chooser that visibly did
     * nothing. It belongs on the settings screen, where the things it moves are.
     */
    public static final String[] TUNING_AXES = { "wear", "healing", "phases", "depth" };

    private static Axis axisOf(String key) {
        for (Axis axis : Axis.values()) {
            if (axis.key.equals(key)) return axis;
        }
        return null;
    }

    /** The rungs of one axis, without custom, which is a state rather than a choice. */
    public static String[] rungsOf(String axisKey) {
        Axis axis = axisOf(axisKey);
        return axis == null ? new String[0] : axis.rungs.clone();
    }

    /**
     * Which rung the live settings currently match, or custom when none of them does.
     *
     * <p>
     * Worked out from the settings rather than read from the chooser, so it stays true after
     * something has been published: publishing numbers alone moves the chooser to custom on the
     * next read, and anything caching the old answer would go on naming a rung the config has
     * stopped claiming.
     */
    public static String current(String axisKey) {
        Axis axis = axisOf(axisKey);
        if (axis == null) return CUSTOM;
        for (String rung : axis.rungs) {
            if (matches(axis, rung)) return rung;
        }
        return CUSTOM;
    }

    /**
     * What one family's figures would be under this rung, without applying anything.
     *
     * <p>
     * Applying and undoing was the other way to answer this and is not available: {@link #apply}
     * writes the live settings and {@code push} writes the file, and both would be fighting
     * {@code ServerRules.reassert} inside the same read.
     *
     * <p>
     * Begins as a copy of what the family has <em>now</em> and overwrites only what the named axis
     * owns. Beginning from the shipped figures instead would quietly reset the five it does not.
     */
    public static Figures previewed(SurfaceFamily family, String axisKey, String rung) {
        FamilySettings live = TrmtConfig.family(family);
        FamilySettings base = FamilySettings.shipped(family);
        Figures out = new Figures();
        if (live == null) return out;
        out.thresholdMin = live.thresholdMin;
        out.thresholdMax = live.thresholdMax;
        out.healDaysPerStage = live.healDaysPerStage;
        out.stages = live.stages;
        out.layersPerDepth = live.layersPerDepth;
        out.maxSinkPixels = live.maxSinkPixels;

        Axis axis = axisOf(axisKey);
        if (axis == null || base == null || rung == null || CUSTOM.equals(rung)) return out;

        switch (axis) {
            case WEAR:
                out.thresholdMin = base.thresholdMin * lean(rung);
                out.thresholdMax = base.thresholdMax * lean(rung);
                break;
            case HEALING:
                out.healDaysPerStage = base.healDaysPerStage * lean(rung);
                break;
            case PHASES: {
                int[] shape = phaseRung(rung);
                out.stages = shape == null ? base.stages : Math.min(base.stages, shape[0]);
                out.layersPerDepth = shape == null ? base.layersPerDepth : Math.min(base.layersPerDepth, shape[1]);
                break;
            }
            case DEPTH:
                out.maxSinkPixels = base.maxSinkPixels == 0 ? 0
                    : clamp(Math.round(base.maxSinkPixels * depthLean(rung)), 0, 15);
                break;
            default:
                break;
        }
        return out;
    }

    /** The six figures a rung can move, for a screen that wants to draw them before they are real. */
    public static final class Figures {

        public float thresholdMin;

        public float thresholdMax;

        public double healDaysPerStage;

        public int stages;

        public int layersPerDepth;

        public int maxSinkPixels;
    }

    // ------------------------------------------------------------------
    // What each rung means
    // ------------------------------------------------------------------

    /** How hard a rung leans, as a multiplier on the shipped figure. */
    static float lean(String rung) {
        if ("very_slow".equals(rung)) return 2.0f;
        if ("slow".equals(rung)) return 1.4f;
        if ("brisk".equals(rung)) return 0.7f;
        if ("quick".equals(rung)) return 0.5f;
        return 1.0f;
    }

    static float depthLean(String rung) {
        if ("none".equals(rung)) return 0f;
        if ("shallow".equals(rung)) return 0.5f;
        if ("deep".equals(rung)) return 1.5f;
        if ("deepest".equals(rung)) return 2.0f;
        return 1.0f;
    }

    static int[] phaseRung(String rung) {
        if ("fewer".equals(rung)) return new int[] { 12, 6 };
        if ("coarse".equals(rung)) return new int[] { 8, 4 };
        if ("very_coarse".equals(rung)) return new int[] { 4, 2 };
        return null; // default: whatever each family ships with
    }

    private static void apply(Axis axis, String rung) {
        if (axis == Axis.QUALITY) {
            applyQuality(rung);
            return;
        }
        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings live = TrmtConfig.family(family);
            FamilySettings base = FamilySettings.shipped(family);
            if (live == null || base == null || !family.staged) continue;
            switch (axis) {
                case WEAR:
                    live.thresholdMin = base.thresholdMin * lean(rung);
                    live.thresholdMax = base.thresholdMax * lean(rung);
                    break;
                case HEALING:
                    live.healDaysPerStage = base.healDaysPerStage * lean(rung);
                    break;
                case PHASES: {
                    int[] shape = phaseRung(rung);
                    live.stages = shape == null ? base.stages : Math.min(base.stages, shape[0]);
                    live.layersPerDepth = shape == null ? base.layersPerDepth : Math.min(base.layersPerDepth, shape[1]);
                    break;
                }
                case DEPTH:
                    // A family with no depth of its own keeps none whatever the rung says: grass is
                    // a face rather than a substance and borrows its depth from the earth beneath.
                    live.maxSinkPixels = base.maxSinkPixels == 0 ? 0
                        : clamp(Math.round(base.maxSinkPixels * depthLean(rung)), 0, 15);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Writes the live settings back into the file's own properties.
     *
     * <p>
     * Not optional, and the reason is the next startup rather than tidiness. A rung applied only to
     * the live settings leaves the file still holding whatever was there before, so the following
     * read hands those old numbers back, finds they do not match the rung the chooser names, and
     * concludes somebody edited them - quietly moving itself to custom and undoing the choice. The
     * file has to agree with the chooser, which also means opening it after picking a rung shows
     * that rung's numbers rather than a name and a set of stale figures.
     */
    private static void push(Configuration config, Axis axis) {
        if (axis == Axis.QUALITY) {
            write(config, TrmtConfig.CATEGORY_CLIENT, "wearGradations", TrmtConfig.wearGradations);
            write(config, TrmtConfig.CATEGORY_CLIENT, "wearRotations", TrmtConfig.wearRotations);
            write(config, TrmtConfig.CATEGORY_CLIENT, "maxWearSprites", TrmtConfig.maxWearSprites);
            write(config, TrmtConfig.CATEGORY_CLIENT, "perSurfaceTextures", TrmtConfig.perSurfaceTextures);
            write(config, TrmtConfig.CATEGORY_SURFACES, "maxTexturedSurfaces", TrmtConfig.maxTexturedSurfaces);
            return;
        }
        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings live = TrmtConfig.family(family);
            // Staged families only, here and in every other walk over the families in this class.
            // The two that never wear through have no thresholds, heal days or shape read from the
            // file at all, so writing a rung's numbers into them made properties with no
            // explanation, and comparing a rung against them found a difference that was never an
            // edit - which moved every rung but the default back to custom on the next read.
            if (live == null || !family.staged) continue;
            String category = TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER + family.key();
            switch (axis) {
                case WEAR:
                    write(config, category, "thresholdMin", live.thresholdMin);
                    write(config, category, "thresholdMax", live.thresholdMax);
                    break;
                case HEALING:
                    write(config, category, "healDaysPerStage", live.healDaysPerStage);
                    break;
                case PHASES:
                    write(config, category, "stages", live.stages);
                    write(config, category, "layersPerDepth", live.layersPerDepth);
                    break;
                case DEPTH:
                    write(config, category, "maxSinkPixels", live.maxSinkPixels);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Sets a value on the property the reader already declared, leaving everything else on it alone.
     *
     * <p>
     * Every one of Forge's short {@code get} overloads sets the comment along with the value, and
     * with no comment given it sets it to nothing - so writing a rung's numbers back through them
     * wiped the hover text off every property they touched, for the rest of the session and into
     * the file. The audit at the end of every read caught it in a real log: seventy-two family
     * settings with no explanation, on the first read after a rung had been picked. Every key
     * written here was declared by the family reader moments earlier in the same read, so the
     * property is there to be found and is written to directly. The fallback is for the one case
     * where that stops being true, and it says so rather than leaving a bare property behind
     * quietly.
     */
    private static void write(Configuration config, String category, String key, double value) {
        Property property = declared(config, category, key);
        if (property != null) property.set(value);
        else config.get(category, key, value)
            .set(value);
    }

    private static void write(Configuration config, String category, String key, int value) {
        Property property = declared(config, category, key);
        if (property != null) property.set(value);
        else config.get(category, key, value)
            .set(value);
    }

    private static void write(Configuration config, String category, String key, boolean value) {
        Property property = declared(config, category, key);
        if (property != null) property.set(value);
        else config.get(category, key, value)
            .set(value);
    }

    private static Property declared(Configuration config, String category, String key) {
        Property property = config.getCategory(category)
            .get(key);
        if (property == null) {
            Trmt.LOG.warn(
                "presets: {}.{} had not been declared when a rung wrote to it, so its hover text will be missing until the next start",
                category,
                key);
        }
        return property;
    }

    private static boolean matches(Axis axis, String rung) {
        if (axis == Axis.QUALITY) return qualityMatches(rung);
        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings live = TrmtConfig.family(family);
            FamilySettings base = FamilySettings.shipped(family);
            if (live == null || base == null || !family.staged) continue;
            switch (axis) {
                case WEAR:
                    if (!near(live.thresholdMin, base.thresholdMin * lean(rung))) return false;
                    if (!near(live.thresholdMax, base.thresholdMax * lean(rung))) return false;
                    break;
                case HEALING:
                    if (!near((float) live.healDaysPerStage, (float) (base.healDaysPerStage * lean(rung)))) {
                        return false;
                    }
                    break;
                case PHASES: {
                    int[] shape = phaseRung(rung);
                    int stages = shape == null ? base.stages : Math.min(base.stages, shape[0]);
                    int layers = shape == null ? base.layersPerDepth : Math.min(base.layersPerDepth, shape[1]);
                    if (live.stages != stages || live.layersPerDepth != layers) return false;
                    break;
                }
                case DEPTH: {
                    int wanted = base.maxSinkPixels == 0 ? 0
                        : clamp(Math.round(base.maxSinkPixels * depthLean(rung)), 0, 15);
                    if (live.maxSinkPixels != wanted) return false;
                    break;
                }
                default:
                    break;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Quality, which is the client's alone
    // ------------------------------------------------------------------

    private static void applyQuality(String rung) {
        int[] q = quality(rung);
        TrmtConfig.wearGradations = q[0];
        TrmtConfig.wearRotations = q[1];
        TrmtConfig.maxWearSprites = q[2];
        TrmtConfig.maxTexturedSurfaces = q[3];
        TrmtConfig.perSurfaceTextures = q[4] != 0;
    }

    private static boolean qualityMatches(String rung) {
        int[] q = quality(rung);
        return TrmtConfig.wearGradations == q[0] && TrmtConfig.wearRotations == q[1]
            && TrmtConfig.maxWearSprites == q[2]
            && TrmtConfig.maxTexturedSurfaces == q[3]
            && TrmtConfig.perSurfaceTextures == (q[4] != 0);
    }

    /** gradations, rotations, sprite ceiling, textured surfaces, per-surface textures. */
    static int[] quality(String rung) {
        if ("potato".equals(rung)) return new int[] { 16, 1, 8192, 64, 0 };
        if ("low".equals(rung)) return new int[] { 24, 2, 32768, 256, 1 };
        if ("high".equals(rung)) return new int[] { 80, 4, 262144, 2048, 1 };
        if ("ultra".equals(rung)) return new int[] { 80, 4, 262144, 4096, 1 };
        return new int[] { 80, 4, 262144, 1024, 1 };
    }

    // ------------------------------------------------------------------
    // Remembering what somebody typed
    // ------------------------------------------------------------------

    private static String encode(Axis axis) {
        if (axis == Axis.QUALITY) {
            int[] q = new int[] { TrmtConfig.wearGradations, TrmtConfig.wearRotations, TrmtConfig.maxWearSprites,
                TrmtConfig.maxTexturedSurfaces, TrmtConfig.perSurfaceTextures ? 1 : 0 };
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < q.length; i++) {
                if (i > 0) out.append(',');
                out.append(q[i]);
            }
            return out.toString();
        }
        StringBuilder out = new StringBuilder();
        for (SurfaceFamily family : SurfaceFamily.values()) {
            FamilySettings live = TrmtConfig.family(family);
            if (live == null || !family.staged) continue;
            if (out.length() > 0) out.append(';');
            out.append(family.key())
                .append('=');
            switch (axis) {
                case WEAR:
                    out.append(live.thresholdMin)
                        .append(',')
                        .append(live.thresholdMax);
                    break;
                case HEALING:
                    out.append(live.healDaysPerStage);
                    break;
                case PHASES:
                    out.append(live.stages)
                        .append(',')
                        .append(live.layersPerDepth);
                    break;
                case DEPTH:
                    out.append(live.maxSinkPixels);
                    break;
                default:
                    break;
            }
        }
        return out.toString();
    }

    private static boolean restore(Axis axis, String held) {
        if (held == null || held.trim()
            .isEmpty()) {
            return false;
        }
        try {
            if (axis == Axis.QUALITY) {
                String[] parts = held.split(",");
                if (parts.length < 5) return false;
                TrmtConfig.wearGradations = Integer.parseInt(parts[0].trim());
                TrmtConfig.wearRotations = Integer.parseInt(parts[1].trim());
                TrmtConfig.maxWearSprites = Integer.parseInt(parts[2].trim());
                TrmtConfig.maxTexturedSurfaces = Integer.parseInt(parts[3].trim());
                TrmtConfig.perSurfaceTextures = !"0".equals(parts[4].trim());
                return true;
            }
            for (String chunk : held.split(";")) {
                int split = chunk.indexOf('=');
                if (split <= 0) continue;
                SurfaceFamily family = SurfaceFamily.byKey(chunk.substring(0, split));
                FamilySettings live = family == null || !family.staged ? null : TrmtConfig.family(family);
                if (live == null) continue;
                String[] parts = chunk.substring(split + 1)
                    .split(",");
                switch (axis) {
                    case WEAR:
                        if (parts.length < 2) continue;
                        live.thresholdMin = Float.parseFloat(parts[0].trim());
                        live.thresholdMax = Float.parseFloat(parts[1].trim());
                        break;
                    case HEALING:
                        live.healDaysPerStage = Double.parseDouble(parts[0].trim());
                        break;
                    case PHASES:
                        if (parts.length < 2) continue;
                        live.stages = Integer.parseInt(parts[0].trim());
                        live.layersPerDepth = Integer.parseInt(parts[1].trim());
                        break;
                    case DEPTH:
                        live.maxSinkPixels = Integer.parseInt(parts[0].trim());
                        break;
                    default:
                        break;
                }
            }
            return true;
        } catch (RuntimeException unreadable) {
            // Written by this mod and read by this mod, so getting here means a hand-edit or a file
            // from a version that wrote it differently. Neither is worth refusing to start over.
            Trmt.LOG.warn(
                "Could not read back the custom {} settings that were being kept; the numbers now in the file stand instead",
                axis.key,
                unreadable);
            return false;
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) <= Math.max(1e-4f, Math.abs(b) * 1e-3f);
    }

    private static int clamp(int value, int low, int high) {
        return value < low ? low : (value > high ? high : value);
    }

    private static String describe(Axis axis) {
        switch (axis) {
            case QUALITY:
                return "How much work this client does drawing worn ground. Yours alone: it reaches no server and no server overwrites it. The shipped setting is already the top of three of its four figures, so 'high' and 'ultra' only widen how many separate blocks get textures of their own - the room above default is genuinely small, and saying so is more use than pretending otherwise. 'low' and 'potato' are where the difference is: potato draws sixteen pictures a run at one rotation and turns per-surface textures off entirely, which is the difference between a stitch that takes half a minute and one that does not, and which leaves every worn block on its family's generic art with no layer behind it, so Chisel's lavastone and waterstone wear without their lava and water. No rung moves the settings for that layer; they are client.animateInnerLayers, client.innerLayerAnimationBudgetMb and client.innerLayerUploadsPerTick.";
            case WEAR:
                return "How much traffic it takes to mark ground, across every family at once. Each rung scales the shipped figures rather than replacing them, so the character of each surface is kept - snow still marks first and stone last - and only the pace changes. 'very_slow' is twice the traffic, 'quick' is half.";
            case HEALING:
                return "How long a mark lasts once nobody is using it. Scaled the same way and independent of the wear chooser above, which is the point of having two: slow to appear and quick to fade is a perfectly reasonable thing to want, and one bundled preset could not say it. 'very_slow' is twice as long to recover, 'quick' is half.";
            case PHASES:
                return "How many separate gradations a run passes through before it is as worn as it gets. Downwards only, because sixteen surface stages is the ceiling the record itself can hold. Fewer phases means each one is a larger visible step. The thresholds are not raised to match, so a run with fewer phases takes less traffic to wear all the way through; a phase count typed in the wear editor does raise them, to keep the end where it was.";
            case DEPTH:
                return "How far ground physically sinks under foot. Scaled from each family's own figure, so ice still sinks half as far as earth does and turf still sinks not at all on its own account. 'none' turns the hollowing off entirely and leaves wear as something you can only see. On a server that has real ruts this is the server's to decide, and its answer overrides whatever is chosen here.";
            default:
                return "";
        }
    }
}
