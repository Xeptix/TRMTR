package com.trmtgtnh.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import com.trmtgtnh.config.ConfigFile.Setting;
import com.trmtgtnh.erosion.CostCurve;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.original.FaithfulChains;
import com.trmtgtnh.original.FaithfulHeal;
import com.trmtgtnh.original.FaithfulWear;
import com.trmtgtnh.original.OriginalSettings;
import com.trmtgtnh.original.Vegetation;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Faithful Mode: grass, dirt, sand, leaves and plants worn and healed by the original mod's numbers (0.9.229 W5;
 * docs/goals/0.9.229-w5-spec.md, 2.1).
 *
 * <p>
 * One switch, {@code presets.faithful}, first on the presets tab, and one heading, {@code faithful}, holding the
 * original's settings under this mod's names with 0.5.1's numbers as their defaults. The heading is built from
 * {@link OriginalSettings#KEYS}, the one table of those keys, their defaults and their ranges, so "Use your TRMT
 * settings" fills exactly these settings, one key to one key, and the two can never name different things.
 *
 * <p>
 * Applied inside {@code TrmtConfig.read}, after {@code Presets.applyAll} and before {@code ServerRules.reassert}: after
 * the presets, because the four tuning choosers write the same family figures and Faithful Mode has to have the last
 * word over them for the five families it owns; before the server's rules, so a server a player is visiting still has
 * the last word over both. It writes the <b>live</b> settings only, never a property of the file, so the player's own
 * figures for those five stay in the file untouched and come back on the first read with the switch off. Each read
 * starts again from the file, so nothing of Faithful Mode is left behind on the objects either.
 *
 * <p>
 * What is applied here is what the family settings can carry: the stage counts the pictures are cut to, the thresholds
 * and their flat curve, the grass-to-dirt wear-through, sand's drawn depth, and the trampling switches and drop chances
 * for leaves and plants. The chains themselves come from {@link FaithfulChains} in {@code ErosionChain.rebuild}, and
 * each step's threshold is drawn from the family it shows ({@code ErosionChain.pace}). The rest of the heading - the
 * wear and heal switches of grass, dirt and sand, the three multipliers, the heal days per stage and the plant list -
 * is read and held here for the wear, heal and trampling code to ask (spec 2.3 to 2.6).
 *
 * <p>
 * The other seven families keep this mod's own numbers (spec question 2).
 */
public final class FaithfulProfile {

    /** The heading the original's settings live under. */
    public static final String CATEGORY = "faithful";

    /** The switch's key, under {@link Presets#CATEGORY}. */
    public static final String SWITCH = "faithful";

    /**
     * Whether worn plain dirt heals into grass, as the original's always does (spec 2.4, question 3). Not a key of the
     * original's file, so not in {@link OriginalSettings#KEYS}: a choice about how faithful to be, on by default.
     */
    public static final String HEALS_TO_GRASS = CATEGORY + ".dirtHealsToGrass";

    /**
     * Whether the coarse end heals a stage about a minute after it forms, as the original's does by a side effect of
     * writing it no record (spec 2.4, question 4). Not a key of the original's file; off by default.
     */
    public static final String COARSE_FLICKER = CATEGORY + ".coarseFlicker";

    /** The edition this tree is, as {@link Vegetation#EDITIONS} names it. */
    public static final String EDITION = "26.3";

    /** The families Faithful Mode owns. */
    private static final SurfaceFamily[] OWNED = { SurfaceFamily.GRASS, SurfaceFamily.DIRT, SurfaceFamily.SAND,
        SurfaceFamily.LEAVES, SurfaceFamily.VEGETATION };

    private static volatile boolean on;

    /** Each {@code faithful} setting's value, as {@link OriginalSettings.Reading#values} shapes them. */
    private static volatile Map<String, Object> values = defaults();

    private FaithfulProfile() {}

    /** Whether Faithful Mode is on. */
    public static boolean on() {
        return on;
    }

    /** Whether Faithful Mode is on and this family is one of the five it owns. */
    public static boolean owns(SurfaceFamily family) {
        if (!on || family == null) return false;
        for (SurfaceFamily owned : OWNED) {
            if (owned == family) return true;
        }
        return false;
    }

    /**
     * A number of the heading, by its full name ({@code faithful.grass.thresholdMin}); NaN for one it does not have.
     */
    public static double number(String name) {
        Object value = values.get(name);
        return value instanceof Double ? ((Double) value).doubleValue() : Double.NaN;
    }

    /** A switch of the heading, by its full name ({@code faithful.grass.wears}); false for one it does not have. */
    public static boolean switchedOn(String name) {
        return Boolean.TRUE.equals(values.get(name));
    }

    /** One of the five-value settings, the heal days per stage, first stage first; null for one it does not have. */
    public static double[] perStage(String name) {
        Object value = values.get(name);
        return value instanceof double[] ? ((double[]) value).clone() : null;
    }

    /**
     * Which of the original's three kinds of walker this is (spec 2.3): a player, a mob on a lead, or any other mob,
     * which the original never lets wear anything. A led mob counts as led only while the lead switch
     * ({@code erodeFromLeashedMobs}) is on, as for this mod's own wear (Codex review R17): with it off, a mob its group
     * lets in still wears this mod's families, but on a lead it is a loose mob to Faithful's five.
     */
    public static FaithfulWear.Mover moverOf(LivingEntity walker) {
        if (walker instanceof Player) return FaithfulWear.Mover.PLAYER;
        if (walker instanceof Mob && TrmtConfig.erodeFromLeashedMobs && ((Mob) walker).isLeashed())
            return FaithfulWear.Mover.LEASHED;
        return FaithfulWear.Mover.LOOSE;
    }

    /**
     * What one step wears Faithful Mode's five families by on the square stood on: the original's
     * {@code player * mounted} or {@code player * leash} from the {@code faithful} heading, times {@code factor} (this
     * mod's heavy-walker draughts, an addition the original lacks). With the switch off, {@code ours} unchanged, so a
     * caller asking both never wears any differently while Faithful Mode is off.
     */
    public static float underfoot(FaithfulWear.Mover mover, boolean riding, float factor, float ours) {
        if (!on) return ours;
        return FaithfulWear
            .underfoot(mover, riding, number("faithful.player"), number("faithful.mounted"), number("faithful.leashed"))
            * factor;
    }

    /** The square ahead's share of a Faithful step worth {@code underfoot}; {@code ours} with the switch off. */
    public static float ahead(FaithfulWear.Mover mover, float underfoot, float ours) {
        return on ? FaithfulWear.ahead(mover, underfoot) : ours;
    }

    /** Each side square's share of a Faithful step worth {@code underfoot}; {@code ours} with the switch off. */
    public static float aside(FaithfulWear.Mover mover, float underfoot, float ours) {
        return on ? FaithfulWear.aside(mover, underfoot) : ours;
    }

    /**
     * What a step wears this family by: {@code faithful} for one of the five while Faithful Mode is on, nothing for
     * one whose {@code faithful.<surface>.wears} is off (the original's "no new wear"), and {@code ours} for every
     * other family, or for all of them with the switch off.
     */
    public static float amountFor(SurfaceFamily family, float ours, float faithful) {
        if (!owns(family)) return ours;
        return switchedOn(CATEGORY + "." + family.key() + ".wears") ? faithful : 0f;
    }

    /**
     * Whether wear past a threshold goes on toward the next step: always, but for a walker's step over one of Faithful
     * Mode's families while it is on. The original's counter starts again from nought at every stage change
     * ({@code trmt$tryTransform}), so its passes round up stage by stage - a leashed mob takes 18 passes from grass to
     * bare earth at the least, not 16 - and Faithful Mode does the same (director, 2026-10-30). A blast, a landing or
     * a tamper still carries its surplus, as in this mod everywhere, since the original has none of them.
     */
    public static boolean carriesSurplus(SurfaceFamily base, boolean walked) {
        return !(walked && owns(base));
    }

    /**
     * How fast wear short of a whole stage fades on this family, as a share of its threshold a day: never, for one of
     * Faithful Mode's five, because the original's count never fades; {@code healing.wearDecayPerDay} otherwise.
     */
    public static double wearDecayPerDay(SurfaceFamily family) {
        return owns(family) ? 0d : TrmtConfig.wearDecayPerDay;
    }

    /** Whether Faithful Mode is on and this base heals along the original's chain: grass, dirt or sand. */
    private static boolean healsFaithfully(SurfaceFamily base) {
        return owns(base) && FaithfulChains.has(base);
    }

    /**
     * Whether worn ground of this base heals at all: not one of Faithful Mode's three whose
     * {@code faithful.<surface>.heals} is off (the original's {@code deErosion} toggles: worn ground stays as it is).
     * Every other base, and all of them with the switch off, heal as they always have.
     */
    public static boolean heals(SurfaceFamily base) {
        return !healsFaithfully(base) || switchedOn(CATEGORY + "." + base.key() + ".heals");
    }

    /**
     * Whether covered ground of this base heals at once (spec 2.4): one of Faithful Mode's three, whose original turned
     * an eroded block back into its vanilla block the moment anything that occludes stood on it, whatever its heal
     * toggle said.
     */
    public static boolean healsWhenCovered(SurfaceFamily base) {
        return healsFaithfully(base);
    }

    /**
     * Whether rain falling on worn ground of this base shortens its days ({@code wetRecoverySpeed}): every base but
     * Faithful Mode's three while it is on, because the original knows no weather and its days are its days, rain or
     * shine (spec 2.4: the stage's figure, divided by the global healing speeds alone).
     */
    public static boolean rainMends(SurfaceFamily base) {
        return !healsFaithfully(base);
    }

    /** Whether worn ground of this base heals in half the days when nothing worn is around it (spec 2.4). */
    public static boolean healsFasterAlone(SurfaceFamily base) {
        return healsFaithfully(base);
    }

    /**
     * The days a stage at this index of the base's chain waits untouched before it steps back one (spec 2.4): for
     * Faithful Mode's three, the {@code faithful} heading's figure for that stage, the coarse end's about a minute
     * while {@link #COARSE_FLICKER} is on, halved when {@code isolated}, then divided by this mod's healing and overall
     * speeds as every family's is; {@code ours} for every other base, for an index the original has no figure for, and
     * for all of them with the switch off.
     */
    public static double healDays(SurfaceFamily base, int index, boolean isolated, double ours) {
        if (!healsFaithfully(base)) return ours;
        double days;
        if (switchedOn(COARSE_FLICKER) && FaithfulHeal.coarseEnd(base, index)) {
            // The original's coarse end has no record, so it never waits its days at all, isolated or not: it goes
            // back at its first random tick.
            days = FaithfulHeal.RANDOM_TICK_DAYS;
        } else {
            days = FaithfulHeal.days(
                base,
                index,
                held("faithful.grassHealDays"),
                number("faithful.dirtHealDays"),
                number("faithful.coarseHealDays"),
                held("faithful.sandHealDays"));
            if (Double.isNaN(days)) return ours;
            if (isolated) days = FaithfulHeal.isolatedDays(days);
        }
        return days / (TrmtConfig.positive(TrmtConfig.healingRate) * TrmtConfig.positive(TrmtConfig.globalSpeed));
    }

    /**
     * Whether the step back from this index turns worn plain dirt into grass, at grass's bare earth (spec 2.4): plain
     * dirt's first worn stage, while Faithful Mode is on and {@link #HEALS_TO_GRASS} is. Off, it heals back to plain
     * dirt as this mod's own dirt does.
     */
    public static boolean healsIntoGrass(SurfaceFamily base, int index) {
        return healsFaithfully(base) && switchedOn(HEALS_TO_GRASS) && FaithfulHeal.healsIntoGrass(base, index);
    }

    /**
     * Whether covered ground at this index of the base's chain goes back to dirt rather than to its own block: grass
     * worn to earth, which the original's covered {@code eroded_dirt} left as dirt, while Faithful Mode is on and
     * {@link #HEALS_TO_GRASS} is (the director's reading of W5 T3's departure 2). Off, a square keeps the ground it
     * started as, as plain dirt healing does.
     */
    public static boolean coveredToDirt(SurfaceFamily base, int index) {
        return base == SurfaceFamily.GRASS && healsFaithfully(base)
            && switchedOn(HEALS_TO_GRASS)
            && index >= FaithfulHeal.bareEarth();
    }

    /**
     * Whether this step back grows short grass on the square, for a roll between 0 and 1 (spec 2.4): a step back
     * between worn turf stages of grass, at the original's 5%, while Faithful Mode is on.
     */
    public static boolean sprouts(SurfaceFamily base, int index, double roll) {
        return healsFaithfully(base) && FaithfulHeal.sprouts(base, index, roll);
    }

    /**
     * Whether a tall grass trampled down becomes short grass and stops there rather than breaking (spec 2.6): a plant
     * trampled while Faithful Mode is on, as the original's did.
     */
    public static boolean shortensTallGrass(SurfaceFamily target) {
        return target == SurfaceFamily.VEGETATION && owns(target);
    }

    /**
     * Whether bone meal on worn ground of this base mends as the original's did (spec 2.6): the aimed square alone,
     * one stage, free of any block. Grass, dirt and sand while Faithful Mode is on; with it off, this mod's patch.
     */
    public static boolean mendsOneStage(SurfaceFamily base) {
        return healsFaithfully(base);
    }

    /** Whether bone meal is refused on worn ground of this base: sand, while Faithful Mode is on (the original's). */
    public static boolean refusesBoneMeal(SurfaceFamily base) {
        return base == SurfaceFamily.SAND && healsFaithfully(base);
    }

    /**
     * Whether a brush gives worn ground of this base back a stage as the original's did (spec 2.6, W5 T17): sand,
     * while Faithful Mode is on. Asked by {@code server.FaithfulBrush}, in the editions that have a brush (1.20.1 on).
     */
    public static boolean brushes(SurfaceFamily base) {
        return base == SurfaceFamily.SAND && healsFaithfully(base);
    }

    /** A five-value setting as held, without a copy, for the heal clock to read many times a sweep. */
    private static double[] held(String name) {
        Object value = values.get(name);
        return value instanceof double[] ? (double[]) value : null;
    }

    /** The plants Faithful Mode tramples, in this edition's names. */
    public static String[] plants() {
        Object value = values.get("faithful.vegetation.blocks");
        return value instanceof String[] ? ((String[]) value).clone() : new String[0];
    }

    /**
     * Declares the switch. Called first in {@code Presets.applyAll}, so it is the first row of the presets tab and the
     * first line of the heading in the file (the heading keeps its declared order).
     */
    static boolean declareSwitch(ConfigFile config) {
        return config.getBoolean(
            SWITCH,
            Presets.CATEGORY,
            false,
            "Faithful Mode: grass, dirt, sand, leaves and plants wear and heal by the original The Roads More Travelled's rules and numbers, which are under the faithful heading and start at its version 0.5.1's. Grass wears through five stages to bare earth and on to coarse dirt without sinking, dirt the same from its first worn stage, and sand through five stages that sink a little each. Your own settings for those five stay in this file as they are and are back the moment this is off; the other surfaces wear by this mod's settings either way.");
    }

    /**
     * Declares the {@code faithful} heading, reads it, and, with the switch on, writes Faithful Mode into the live
     * settings. Called from inside {@code TrmtConfig.read}, between {@code Presets.applyAll} and
     * {@code ServerRules.reassert}.
     */
    public static void apply(ConfigFile config) {
        // The server's switch while its rules are held, the file's otherwise (spec 2.7; Codex review R15, finding 2):
        // the chains a client places a server's records on have to be the server's, and so does the pace it shows.
        on = ServerRules.faithful(declareSwitch(config));
        FaithfulChains.switchTo(on);
        values = Collections.unmodifiableMap(read(config));
        // This mod's own chains, from the player's numbers while they are still the live ones, so a record worn along
        // them is placed by how far along it was once Faithful Mode's chains replace them (Codex review R15).
        if (on) ErosionChain.keepOwn();
        if (on) write();
    }

    /** Every setting of the heading as 0.5.1 has it, so the profile answers before any file is read. */
    private static Map<String, Object> defaults() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (OriginalSettings.Key key : OriginalSettings.KEYS) {
            switch (key.kind) {
                case SWITCH:
                    out.put(key.faithful, Boolean.valueOf(key.fallback != 0d));
                    break;
                case PLANTS:
                    out.put(key.faithful, plantsOfThisEdition());
                    break;
                default:
                    if (key.slot < 0) {
                        out.put(key.faithful, Double.valueOf(key.fallback));
                    } else {
                        double[] five = (double[]) out.get(key.faithful);
                        if (five == null) {
                            five = new double[perStageLength(key.faithful)];
                            out.put(key.faithful, five);
                        }
                        five[key.slot] = key.fallback;
                    }
                    break;
            }
        }
        out.put(HEALS_TO_GRASS, Boolean.TRUE);
        out.put(COARSE_FLICKER, Boolean.FALSE);
        return out;
    }

    /** The original's eighteen plants as this edition names them, leaving out the two it does not have. */
    private static String[] plantsOfThisEdition() {
        List<String> out = new ArrayList<String>();
        for (String name : Vegetation.row(EDITION)) {
            if (name != null) out.add(name);
        }
        return out.toArray(new String[out.size()]);
    }

    private static int perStageLength(String name) {
        int length = 0;
        for (OriginalSettings.Key key : OriginalSettings.KEYS) {
            if (key.faithful.equals(name) && key.slot >= length) length = key.slot + 1;
        }
        return length;
    }

    /** The heading a full name lives under: {@code faithful.grass} for {@code faithful.grass.thresholdMin}. */
    static String categoryOf(String name) {
        return name.substring(0, name.lastIndexOf('.'));
    }

    /** A full name's own key: {@code thresholdMin} for {@code faithful.grass.thresholdMin}. */
    static String keyOf(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static Map<String, Object> read(ConfigFile config) {
        config.setCategoryComment(
            CATEGORY,
            "The original The Roads More Travelled's settings, each named after its own key in that mod's config/trmt.json, with its version 0.5.1's numbers as their defaults. presets.faithful runs grass, dirt, sand, leaves and plants by these; while it is off they change nothing.");
        Map<String, Object> defaults = defaults();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (OriginalSettings.Key key : OriginalSettings.KEYS) {
            if (out.containsKey(key.faithful)) continue;
            String category = categoryOf(key.faithful);
            String name = keyOf(key.faithful);
            if (!CATEGORY.equals(category)) {
                config.setCategoryComment(category, headingComment(keyOf(category)));
            }
            switch (key.kind) {
                case SWITCH:
                    out.put(
                        key.faithful,
                        Boolean.valueOf(config.getBoolean(name, category, key.fallback != 0d, comment(key))));
                    break;
                case PLANTS: {
                    String[] shipped = (String[]) defaults.get(key.faithful);
                    String[] listed = config.get(category, name, shipped, comment(key))
                        .getStringList();
                    out.put(key.faithful, listed == null ? shipped : listed.clone());
                    break;
                }
                default:
                    if (key.slot < 0) {
                        float read = TrmtConfig.declareFloat(
                            config,
                            category,
                            name,
                            (float) key.fallback,
                            (float) key.min,
                            (float) key.max,
                            comment(key));
                        out.put(key.faithful, Double.valueOf(read));
                    } else {
                        double[] shipped = (double[]) defaults.get(key.faithful);
                        Setting list = config
                            .get(category, name, shipped, comment(key), key.min, key.max, true, shipped.length);
                        out.put(key.faithful, perStage(list.getDoubleList(), shipped, key.min, key.max));
                    }
                    break;
            }
        }
        // This mod's two choices about how faithful the healing is (spec questions 3 and 4), after the original's own.
        out.put(
            HEALS_TO_GRASS,
            Boolean.valueOf(
                config.getBoolean(
                    keyOf(HEALS_TO_GRASS),
                    CATEGORY,
                    true,
                    "Whether worn dirt heals into grass under Faithful Mode, as in the original, where every worn earth stage heals on into turf even where it began as plain dirt. Off, worn plain dirt heals back to plain dirt.")));
        out.put(
            COARSE_FLICKER,
            Boolean.valueOf(
                config.getBoolean(
                    keyOf(COARSE_FLICKER),
                    CATEGORY,
                    false,
                    "Whether the coarse end of a path heals a stage about a minute after it forms under Faithful Mode, as the original's did because it kept no record of it, so a busy path's end flickers between worn earth and coarse dirt. Off, the coarse end waits its coarseHealDays like any other stage.")));
        return out;
    }

    /**
     * Five figures as the file holds them, each held to the range; a list of the wrong length, or a figure that is not
     * a number, takes the default for each missing or unreadable place rather than shifting the rest along.
     */
    static double[] perStage(double[] held, double[] shipped, double min, double max) {
        double[] out = shipped.clone();
        if (held == null) return out;
        for (int i = 0; i < out.length && i < held.length; i++) {
            double value = held[i];
            if (Double.isNaN(value)) continue;
            out[i] = value < min ? min : (value > max ? max : value);
        }
        return out;
    }

    /** Writes Faithful Mode into the live settings of the five families it owns, and nowhere else. */
    private static void write() {
        for (SurfaceFamily base : new SurfaceFamily[] { SurfaceFamily.GRASS, SurfaceFamily.DIRT, SurfaceFamily.SAND }) {
            FamilySettings live = TrmtConfig.family(base);
            if (live == null) continue;
            String key = "faithful." + base.key();
            live.stages = FaithfulChains.stages(base);
            live.thresholdMin = (float) number(key + ".thresholdMin");
            live.thresholdMax = (float) number(key + ".thresholdMax");
            // The original draws every stage's threshold from the same range; this mod's curves lean a run's cost
            // toward one end of it.
            live.costCurve = CostCurve.FLAT;
            // Nothing in the original holds wear short of the end of its run.
            live.maxWear = 1.0f;
            // Grass wears through to dirt and nothing else does, whatever the wear-through lists say: ErosionChain
            // asks this list whether a record showing dirt still belongs to grass when the chain moves under it.
            live.wearsThroughTo = base == SurfaceFamily.GRASS ? new String[] { SurfaceFamily.DIRT.key() }
                : new String[0];
            // The heal clock asks healDays per stage (spec 2.4); this one figure a family is what the wear table, the
            // screen and a visiting client's rules are shown: the first stage's, which is every turf stage's and every
            // worn earth stage's at 0.5.1's numbers.
            live.healDaysPerStage = base == SurfaceFamily.DIRT ? number("faithful.dirtHealDays")
                : perStageFirst(base == SurfaceFamily.GRASS ? "faithful.grassHealDays" : "faithful.sandHealDays");
            // Only sand sinks, and only as far as the original drew it. Grass and dirt keep the player's depth: a
            // Faithful chain never sinks them, and the other seven families' ruts into dirt are drawn by it.
            if (base == SurfaceFamily.SAND) live.maxSinkPixels = FaithfulChains.deepest(base);
            // Sand draws the original's own five pictures, which this mod carries and nothing else draws: its own
            // sand look turns grey as it wears where the original's grows darker and warmer (R2, W5 T4). Grass's own
            // look already follows the original's turf, and dirt is drawn as it always was.
            if (base == SurfaceFamily.SAND) live.wearPattern = FamilySettings.ORIGINAL_SAND_LOOK;
        }
        for (SurfaceFamily target : new SurfaceFamily[] { SurfaceFamily.LEAVES, SurfaceFamily.VEGETATION }) {
            FamilySettings live = TrmtConfig.family(target);
            if (live == null) continue;
            String key = "faithful." + target.key();
            live.thresholdMin = (float) number(key + ".thresholdMin");
            live.thresholdMax = (float) number(key + ".thresholdMax");
        }
        TrmtConfig.trampleLeaves = switchedOn("faithful.leaves.wears");
        TrmtConfig.trampleVegetation = switchedOn("faithful.vegetation.wears");
        TrmtConfig.leavesDropChance = (float) number("faithful.leaves.dropChance");
        TrmtConfig.vegetationDropChance = (float) number("faithful.vegetation.dropChance");
    }

    private static double perStageFirst(String name) {
        double[] five = perStage(name);
        return five == null || five.length == 0 ? Double.NaN : five[0];
    }

    private static String headingComment(String surface) {
        if ("leaves".equals(surface)) {
            return "How Faithful Mode tramples leaves underfoot, from the original's erosion.leavesEnabled and erosionThresholds.leaves.";
        }
        if ("vegetation".equals(surface)) {
            return "How Faithful Mode tramples plants walked through, from the original's erosion.vegetationEnabled, erosionThresholds.vegetation and erodableVegetation.";
        }
        return "How Faithful Mode wears and heals " + surface
            + ", from the original's erosion."
            + surface
            + "Enabled, deErosion."
            + surface
            + "Enabled and erosionThresholds."
            + surface
            + ".";
    }

    /** Each setting's comment, naming the original's key it stands for. */
    private static String comment(OriginalSettings.Key key) {
        String name = keyOf(key.faithful);
        String surface = keyOf(categoryOf(key.faithful));
        String from = " The original's " + key.original + ".";
        boolean trampled = "leaves".equals(surface) || "vegetation".equals(surface);
        String thing = "leaves".equals(surface) ? "a leaf" : "a plant";
        if ("wears".equals(name)) {
            if (trampled) {
                return "Whether Faithful Mode tramples " + ("leaves".equals(surface) ? "leaves underfoot" : "plants")
                    + ", in place of trampling."
                    + surface
                    + " while it is on. This breaks real blocks."
                    + from;
            }
            return "Whether " + surface
                + " takes new wear under Faithful Mode. Off, no new wear starts and wear already there still heals."
                + from;
        }
        if ("heals".equals(name)) {
            return "Whether worn " + surface + " heals under Faithful Mode. Off, worn ground stays as it is." + from;
        }
        if ("player".equals(name)) {
            return "The wear one step on foot adds under Faithful Mode, to the square stepped on; a fifth of it to the square ahead and half to each side."
                + from;
        }
        if ("mounted".equals(name)) {
            return "How many times over a step counts under Faithful Mode when riding." + from;
        }
        if ("leashed".equals(name)) {
            return "How many times over a step counts under Faithful Mode for a creature on a lead, which wears only the square it stands on."
                + from;
        }
        if ("thresholdMin".equals(name) || "thresholdMax".equals(name)) {
            String end = "thresholdMin".equals(name) ? "The least" : "The most";
            if (trampled) {
                return end + " wear "
                    + thing
                    + " takes before it breaks under Faithful Mode, drawn afresh for each one between the two."
                    + from;
            }
            return end + " wear a stage of "
                + surface
                + " takes before it moves on under Faithful Mode, drawn afresh for each stage between the two."
                + from;
        }
        if ("dropChance".equals(name)) {
            return "Chance " + thing + " trampled under Faithful Mode drops what it would drop broken." + from;
        }
        if ("blocks".equals(name)) {
            return "The plants Faithful Mode tramples, in this edition's names: the original's eighteen out of the box, but for cornflower and lily of the valley, which this edition does not have."
                + from;
        }
        if ("grassHealDays".equals(name)) {
            return "In-game days each of the five worn grass stages waits untouched before it heals a stage under Faithful Mode, the first stage first. The original's deErosionTimeoutDays.grass.stage1 to stage5.";
        }
        if ("sandHealDays".equals(name)) {
            return "In-game days each of the five worn sand stages waits untouched before it heals a stage under Faithful Mode, the first stage first. The original's deErosionTimeoutDays.sand.stage1 to stage5.";
        }
        if ("dirtHealDays".equals(name)) {
            return "In-game days a worn earth stage waits untouched before it heals a stage under Faithful Mode."
                + from;
        }
        if ("coarseHealDays".equals(name)) {
            return "In-game days the coarse end of a worn path waits untouched before it heals a stage under Faithful Mode."
                + from;
        }
        return "A setting of Faithful Mode." + from;
    }
}
