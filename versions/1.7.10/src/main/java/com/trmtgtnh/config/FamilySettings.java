package com.trmtgtnh.config;

import java.util.EnumMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

import com.trmtgtnh.erosion.CostCurve;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Per-family tuning: how fast a surface wears, how many gradations it shows, how long it
 * takes to recover, and which authored wear pattern drives its textures.
 *
 * <p>
 * Splitting this out of {@link TrmtConfig} is what makes the mod extensible to surfaces
 * upstream never had. Adding gravel, stone and cobblestone cost one enum constant and one
 * defaults row each, rather than a new branch in every switch statement.
 *
 * <p>
 * The shipped ordering, fastest to slowest: sand, gravel, grass, dirt, cobblestone, stone.
 * That follows upstream's relative pacing - a grass layer scuffs away long before packed
 * earth does - scaled to something appropriate for a pack played over months. Every number
 * is a config key, so reordering them is one edit.
 */
public final class FamilySettings {

    /** Wear patterns the texture generator knows how to apply. */
    public static final String PATTERN_GRASS = "grass";
    public static final String PATTERN_DIRT = "dirt";
    public static final String PATTERN_SAND = "sand";
    public static final String PATTERN_POLISH = "polish";
    public static final String PATTERN_CRACK = "crack";
    public static final String PATTERN_SMOOTH = "smooth";

    /**
     * The seven later looks, each an arrangement of the same three operators as the four above.
     *
     * <p>
     * Every one of these ids is frozen the moment it ships, and more firmly than it looks. A stored
     * name that stops being recognised does not fall back to something near it: it falls all the
     * way to the rub, silently, on a world that had been cracking for a month. So the id is not the
     * place to tidy up wording afterwards. The name shown in the picker is, and that one can be
     * changed as freely as this one cannot.
     *
     * <p>
     * The ids name the operator and the shown names name the picture, and the two have never quite
     * agreed - dirt has always been shown as "rubbed away". That is why smooth_dirt is shown as
     * "smoothed rubbed" rather than renamed to match: it carries the existing split forward instead
     * of setting a second convention up beside it.
     *
     * <p>
     * An id freezes the order as well as the spelling. crack_dirt cracks and then rubs, which is the
     * reverse of the two compounds before it, and if that order were ever changed the id would have
     * to stay and be wrong - because renaming it drops every config file that names it, and what
     * those files fall to is a plain rub, which is one of the two things the look is made of, so it
     * would look almost right. Which is why the order was measured rather than assumed, and why the
     * test pins it.
     */
    public static final String PATTERN_CRACK_LITE = "crack_lite";

    public static final String PATTERN_SMOOTH_HEAVY = "smooth_heavy";

    public static final String PATTERN_DIRT_LITE = "dirt_lite";

    public static final String PATTERN_SMOOTH_CRACK = "smooth_crack";

    public static final String PATTERN_SMOOTH_DIRT = "smooth_dirt";

    public static final String PATTERN_CRACK_DIRT = "crack_dirt";

    public static final String PATTERN_CRACK_DIRT_LITE = "crack_dirt_lite";

    /**
     * The four looks made for 0.9.229's four new families (Xep, 2026-11-12: "make 4 new wear patterns tailored to
     * these families"), drawn in {@code TailoredWear}. Each is named for the material it was made for, as grass is,
     * because what it draws is that material wearing rather than one of the three operators: flesh bruises and goes
     * wet, taint blights and veins, sculk's glints go out and it goes dead, cloud packs down into a denser white. Each
     * still works from the block's own pixels, so like every look here it is safe on any block. Frozen as the others
     * are the moment they ship. Each is its family's own look (Xep, 2026-11-12, shown the comparison: "Let's go with
     * the new ones").
     */
    public static final String PATTERN_FLESH = "flesh";

    public static final String PATTERN_TAINT = "taint";

    public static final String PATTERN_SCULK = "sculk";

    public static final String PATTERN_CLOUD = "cloud";

    /**
     * Seven looks made for the older families, drawn in {@code TailoredGround} (Xep, 2026-11-12: new patterns for the
     * families that were already there, added beside the old ones, which stay). Each id names its material and what
     * a foot does to it, because sand, gravel and the rest are already a family's name and, for sand, an older look's
     * id: sand_trodden packs a firm line, gravel_parted opens dark gaps between the stones, cobble_polished polishes
     * the tops and grinds the mortar, snow_packed packs a grey-blue glossy track, ice_scuffed clouds to frost,
     * nether_charred chars and end_bleached bleaches pale. Frozen as every id is the moment it ships. Each is its
     * family's default (Xep, 2026-11-12, from the comparison sheets: "Let's do new, with the idea I had before of a
     * global + per family toggle for bringing back the old wear styles"), and the look each family had before is
     * its classic look, brought back by classic wear ({@link #classicLook}).
     */
    public static final String PATTERN_SAND_TRODDEN = "sand_trodden";

    public static final String PATTERN_GRAVEL_PARTED = "gravel_parted";

    public static final String PATTERN_COBBLE_POLISHED = "cobble_polished";

    public static final String PATTERN_SNOW_PACKED = "snow_packed";

    public static final String PATTERN_ICE_SCUFFED = "ice_scuffed";

    public static final String PATTERN_NETHER_CHARRED = "nether_charred";

    public static final String PATTERN_END_BLEACHED = "end_bleached";

    /**
     * Every look the file's wearPattern key offers, in the order it offers them: every look that draws something of
     * its own, and never the older sand and polish names, which are read but fall to the rub. Held here rather than
     * written inline at the key so the test that holds the picker, the settings screen and the data format to one list
     * can ask it.
     */
    private static final String[] OFFERED_LOOKS = { PATTERN_GRASS, PATTERN_CRACK, PATTERN_CRACK_LITE, PATTERN_SMOOTH,
        PATTERN_SMOOTH_HEAVY, PATTERN_DIRT, PATTERN_DIRT_LITE, PATTERN_SMOOTH_CRACK, PATTERN_SMOOTH_DIRT,
        PATTERN_CRACK_DIRT, PATTERN_CRACK_DIRT_LITE, PATTERN_FLESH, PATTERN_TAINT, PATTERN_SCULK, PATTERN_CLOUD,
        PATTERN_SAND_TRODDEN, PATTERN_GRAVEL_PARTED, PATTERN_COBBLE_POLISHED, PATTERN_SNOW_PACKED, PATTERN_ICE_SCUFFED,
        PATTERN_NETHER_CHARRED, PATTERN_END_BLEACHED };

    public static String[] offeredLooks() {
        return OFFERED_LOOKS.clone();
    }

    /**
     * Whether a worn face of this family turns with its position where the face itself cannot be read (0.9.229,
     * docs/goals/0.9.229-rotation-spec.md). Only the default: a face that can be read is judged by its own pixels and
     * names ({@code FaceDirection}), and a looks file's {@code turns} wins over both, so nether bricks keep still while
     * netherrack beside them turns.
     *
     * <p>
     * Conservative, because a face no reader can open is most often a mod's block drawn by a loader of its own, and
     * nothing is known of it but its family: only the loose ground that is never laid in courses turns unread - grass,
     * dirt, sand, gravel and snow. Stone, cobble, the Nether's and the End's ground, ice and the modded families all
     * hold made blocks (bricks, tiles, pillars) beside their natural ground, so an unread face of theirs keeps still,
     * as every worn face did before 0.9.229.
     */
    public static boolean turnsByDefault(SurfaceFamily family) {
        if (family == null) return false;
        switch (family) {
            case GRASS:
            case DIRT:
            case SAND:
            case GRAVEL:
            case SNOW:
                return true;
            default:
                return false;
        }
    }

    /**
     * The original mod's own five sand pictures, carried as ratios against vanilla sand and drawn on the block's own
     * face (0.9.229 W5 T4). Set on sand's live settings by Faithful Mode alone ({@code FaithfulProfile}) and never
     * offered in the file's list, so this mod's own sand keeps the look its file gives it; the older {@code sand} name
     * a file may still hold goes on meaning the plain rub, as it has since the pattern was dropped.
     */
    public static final String ORIGINAL_SAND_LOOK = "original_sand";

    public final SurfaceFamily family;

    /** Whether this family erodes at all. */
    public boolean enabled = true;

    /**
     * Whether slabs cut from this surface wear as well as whole blocks of it.
     *
     * <p>
     * A shape rather than a family, so it has nothing of its own to tune: a slab walks the same
     * chain at the same prices as the block it was cut from, and takes exactly as many crossings
     * to wear through and exactly as long to recover. Only the rut is drawn half as deep, because
     * half a block of stone cannot lose eight pixels and still be there.
     */
    public boolean slabs = true;

    /** Whether stairs cut from this surface wear as well as whole blocks of it. */
    public boolean stairs = true;

    /** Whether auto-detection may classify blocks into this family. */
    public boolean autoDetect = true;

    /** Extra blocks to force into this family: {@code modid:block}, {@code :meta} or {@code :*}. */
    public String[] extra = new String[0];

    /** How many distinct wear appearances this family shows. */
    public int stages;

    /**
     * How many visual layers a run gets once the ground has started to drop.
     *
     * <p>
     * Shorter than the first run on purpose. The first run is the wear people actually watch -
     * a track appearing in turf - and deserves the detail; once a rut is forming, the depth is
     * doing the talking and the layers are only texture between one pixel and the next.
     */
    public int layersPerDepth = 8;

    /** Random threshold draw per stage, before the global erosion speed multiplier. */
    public float thresholdMin;
    public float thresholdMax;

    /**
     * In-game days one stage takes to step back, before the global healing multiplier.
     *
     * <p>
     * Its default is this family's own figure rather than anything worked out from its wear cost;
     * see the table in {@code defaultsFor} for all of them together and for why the rule that used
     * to derive them was retired. Written into the config like everything else, so a pack that
     * wants a surface which wears fast and heals slowly can still say so.
     */
    public double healDaysPerStage;

    /**
     * How this family's price changes as it wears further down its run.
     *
     * <p>
     * Held as the curve itself rather than as its name, because it is read on every threshold draw
     * and a string comparison there would be paid tens of times a second for nothing.
     */
    public CostCurve costCurve = CostCurve.FLAT;

    /**
     * Whether this family recovers only while precipitation is falling on it.
     *
     * <p>
     * Off for every family out of the box. What it does when on, and the four settings that decide
     * what "falling on it" means, live under the weather heading.
     */
    public boolean healsOnlyWhenWet;

    /**
     * How much faster this family recovers while precipitation is actually falling on it.
     *
     * <p>
     * A discount rather than a gate, and that difference is the whole reason it exists. The
     * per-family {@code healsOnlyWhenWet} switch says recovery happens <em>only</em> in the wet, so
     * every place the rain cannot reach is a place that never mends - which is why it ships off for
     * all sixteen families and why, in practice, nobody has weather-linked recovery at all. This one
     * cannot mean never: a covered walkway simply does not earn it and mends at its ordinary rate.
     * So it can default on for the surfaces where rain plausibly does the work.
     *
     * <p>
     * Held at one or above when it is read, which is the thing that keeps the promise above true
     * rather than merely intended.
     */
    public float wetRecoverySpeed = 1.0f;

    /**
     * Which look this family's textures are drawn in: the look the file names, or this family's classic look where a
     * classic wear switch covers it and the file names nothing but the shipped default ({@link #effectiveLook}).
     */
    public String wearPattern;

    /**
     * Whether this family is drawn in its classic look, the one it shipped with before the look made for it (0.9.229):
     * the per-family half of classic wear, beside {@code TrmtConfig.classicWear} for every family. Off as it ships, and
     * read only for a family that has a classic look ({@link #classicLook}).
     */
    public boolean classicWear;

    /** How far the pattern is applied, 0 leaving the block untouched and 1 applying it fully. */
    public float wearStrength;

    /** Deepest this family ever sinks, in sixteenths of a block. 0 means it never sinks. */
    public int maxSinkPixels;

    /** Where along the family's stages sinking starts, as a fraction of the way through. */
    public float sinkStartFraction;

    /**
     * Blocks in this family that resist wear, and by how much.
     *
     * <p>
     * A grass path is the case this exists for. It is already a worn surface - somebody made it
     * that way on purpose - so it should take far more traffic than turf before it degrades
     * further, while recovering at the same rate as everything else. Multiplying its thresholds
     * does exactly that, and because the threshold is drawn once and stored on the entry, it
     * costs nothing afterwards.
     */
    public String[] resistantBlocks = new String[0];

    public float resistance = 1.0f;

    /**
     * What else a surface in this family may be mended with, besides a block of itself.
     *
     * <p>
     * Grass is the case this exists for. A worn lawn is mended with a block of earth, not with
     * turf: turf is what the world grows and earth is what a player has by the stack, and asking
     * for the scarcer of the two to repair the commoner one has the economics backwards. Grass
     * paths fall out of the same idea from the other direction - they are already dirt, and the
     * dirt family's entry covers every made path in the pack without naming one of them.
     */
    public String[] repairBlocks = new String[0];

    /**
     * What this surface exposes once its own face is used up, in the order the ground reaches it.
     *
     * <p>
     * Grass has always done this and the reason was never special to grass: a bald lawn is earth,
     * because there was never any grass below the first pixel of it. A stone road is the same
     * argument one material along. What a rut in it exposes is not smooth stone with a dip in it -
     * stone breaks before it erodes - it is broken stone, then the grit that broke off it, then
     * the ground the road was laid on. Every one of those is a family the mod already has, so the
     * run costs no new tuning; only the order had to be said out loud.
     *
     * <p>
     * Empty means the ground hollows out as itself, which is what sand and snow do, and is also
     * what a family gets when every name in its list has been switched off.
     */
    public String[] wearsThroughTo = new String[0];

    /** How far along its run this family may wear, as a fraction of the whole. */
    public float maxWear = 1.0f;

    private FamilySettings(SurfaceFamily family, int stages, float min, float max, double healDays, CostCurve curve,
        String pattern, float strength, int maxSink, float sinkStart) {
        this.resistance = 8.0f;
        this.family = family;
        this.stages = stages;
        this.thresholdMin = min;
        this.thresholdMax = max;
        this.healDaysPerStage = healDays;
        this.costCurve = curve;
        this.wearPattern = pattern;
        this.wearStrength = strength;
        this.maxSinkPixels = maxSink;
        this.sinkStartFraction = sinkStart;
    }

    // ------------------------------------------------------------------
    // Defaults
    // ------------------------------------------------------------------

    /**
     * A fresh copy of what this family ships with, untouched by any file.
     *
     * <p>
     * Handed out so that a preset can lean on the shipped figure rather than on whatever is
     * currently in effect. Leaning on the live value would compound: applying the same rung twice
     * would move the numbers twice, and a rung would mean something different on every world.
     */
    public static FamilySettings shipped(SurfaceFamily family) {
        FamilySettings out = defaultsFor(family);
        out.wetRecoverySpeed = defaultWetRecoverySpeed(family);
        return out;
    }

    /**
     * How much faster rain mends each family, out of the box.
     *
     * <p>
     * Twice for the four surfaces rain genuinely works on: turf, earth, sand and gravel all fill in
     * and settle when they are wet, and a downpour on a dirt track is the closest thing this game
     * has to the real mechanism. One for everything else. Rain does not mend dressed stone, and
     * snow and ice are already answered by the weather switch beside this one - being rained on is
     * not what heals either of those, being snowed on is, and that is a gate rather than a
     * discount because it genuinely is the only way they recover.
     *
     * <p>
     * A helper rather than a thirteenth argument threaded through sixteen constructor calls.
     */
    private static float defaultWetRecoverySpeed(SurfaceFamily family) {
        switch (family) {
            case GRASS:
            case DIRT:
            case SAND:
            case GRAVEL:
                return 2.0f;
            default:
                return 1.0f;
        }
    }

    private static FamilySettings defaultsFor(SurfaceFamily family) {
        switch (family) {
            // Every surface runs the same shape: sixteen visual layers on the face as it
            // stands, then a pixel of depth and eight more layers, over and over down to half a
            // block. Eighty steps from untouched to a rut you can stand in.
            //
            // The per-step costs below are the old totals divided by the new step count, so
            // ground takes about the traffic it always did to wear through - there are simply
            // far more gradations along the way. Getting that wrong would have made everything
            // three to ten times slower without anyone asking for it.
            //
            // Healing used to be one constant times a family's own average threshold, so a surface
            // costing twice the traffic to wear took twice as long to come back and the whole table
            // held together from a single number. It was a good rule and it has been retired,
            // because it cannot say what these surfaces actually do. Snow and sand sit within half a
            // point of each other in what they cost to mark, and they recover nothing like alike:
            // fresh snow covers a track in under a week and a rut in sand wants the better part of a
            // month of wind. A rule taking one column from another cannot express that, so each
            // family now owns its recovery outright, in the column beside its thresholds.
            //
            // What is lost with the rule is worth naming, since it was written down as its reason:
            // ten hand-set numbers can drift out of step with the thresholds beside them. They are
            // set out here together, one line a family, precisely so that drifting apart is visible
            // rather than hidden - and the wear table draws both columns for every family from these
            // same figures, so a pair that has come adrift shows up in game rather than only here.
            //
            // The figures themselves, in whole in-game days from fully worn back to untouched:
            // snow 6, sand 30, gravel 45, dirt 100, grass 125, cobble 170, ice 31, stone 320, and
            // nether and end 400 apiece; flesh 37.5 and taint 125 since 0.9.229, and sculk 100 and cloud 6
            // beside them.
            //
            // They were set against one test rather than by taste, and it is the test the retired
            // rule existed to pass: how much traffic it takes to hold ground where it is, against
            // how fast it mends. That figure is a family's passes per gradation divided by its heal
            // days per gradation, and under the old rule it came to the same thirty-six crossings
            // an in-game day for every surface in the game - one every thirty-three seconds of real
            // time, for ever, which is a great deal to ask before any ground marks at all. Every
            // family here comes out easier than that, from gravel's four crossings a day to stone's
            // sixteen. Ground marks under ordinary use now, which is the whole point of it.
            //
            // The looks are the one thing in this method that was not reasoned to. Every family
            // began on whichever of the four original operators was nearest, because four was all
            // there were. The ten older families' looks were then picked by putting the wear table's
            // own previews of every look side by side on each family's own blocks and choosing, and
            // the four newest families' by setting the look made for each beside the one it arrived
            // with on a comparison sheet. That is the right way to settle it - a look is a picture,
            // and no measure of one predicts whether snow ought to crack or scuff - so what is
            // recorded below is what each choice does rather than an argument for why it was
            // inevitable.
            //
            // What was measured is that none of them collapses. Every look here draws eighty
            // distinct pictures of eighty on its own family's face at all four rotations, and
            // sixteen of sixteen at the config minimum. The smallest step between neighbouring
            // gradations is between a quarter and eight tenths of a color level for the ten, and
            // smaller for taint and sculk, whose faces sit near black.
            case GRASS:
                // Grass loses its cover rather than its substance, so it never sinks on its own;
                // the hollowing out happens once the chain carries on into the earth underneath.
                return new FamilySettings(family, 16, 8f, 16f, 1.5625d, CostCurve.SOD, PATTERN_GRASS, 1.0f, 0, 0f);
            case DIRT:
                // A hundred days to lose a rut, near enough. Earth does not grow anything back
                // by itself, it simply settles and gets colonised from the edges, which is slower
                // than turf regrowing over it and faster than anything made of stone.
                return new FamilySettings(family, 16, 8f, 12f, 100d / 79d, CostCurve.SOD, PATTERN_DIRT, 1.0f, 8, 0f);
            case SAND:
                // Buffed flat and then trodden, rather than only trodden. Sand has almost no relief
                // to lose, so a plain rub had little to take and read as a stain; flattening the
                // whole face first gives the track something to be darker than. Four and a half
                // levels darker at the end of its run than the rub was, and the first picture is
                // the one that moves most. That was the classic look, smooth_dirt, and classic wear
                // brings it back; sand now ships the look made for it, a firm trodden line with the
                // grains pushed aside (Xep, 2026-11-12, from the v2 comparison sheets: "Let's do new, with the idea I
                // had before of a
                // global + per family toggle for bringing back the old wear styles").
                return new FamilySettings(
                    family,
                    16,
                    1.2f,
                    2.4f,
                    0.375d,
                    CostCurve.SETTLE,
                    PATTERN_SAND_TRODDEN,
                    1.0f,
                    8,
                    0f);
            case GRAVEL:
                // Stones rather than grains, and it shows at both ends. A little more traffic to
                // mark than sand - four and a fifth passes a gradation against sand's three and
                // three fifths - because a foot displaces gravel instead of sinking into it. And
                // half as long again to recover, because nothing levels gravel back out: wind
                // fills a footprint in sand and does nothing whatever to a rut in loose rock. Its
                // classic look was the plain rub; it ships the look made for it, the stones parting
                // over dark gaps as the ground grimes (Xep, 2026-11-12, from the v2 comparison sheets: "Let's do new,
                // with the idea I had before of a
                // global + per family toggle for bringing back the old wear styles").
                return new FamilySettings(
                    family,
                    16,
                    1.4f,
                    2.8f,
                    0.5625d,
                    CostCurve.SETTLE,
                    PATTERN_GRAVEL_PARTED,
                    1.0f,
                    8,
                    0f);
            case COBBLE:
                // The crack it always had, with a path worn through it. Cobble is the one surface
                // already made of pieces, so the fissures had nothing to add that the stone did not
                // already say; a track across them is what tells you which way people walked. Two
                // and a half levels darker at the end than the plain crack, and rather more than
                // that at the halfway mark. That cracked rub is its classic look; it ships the look
                // made for it, the stones polished and dulled over darker mortar, cracking and
                // scuffing as the run goes on (Xep, 2026-11-12, from the v2 comparison sheets: "Let's do new, with the
                // idea I had before of a
                // global + per family toggle for bringing back the old wear styles").
                return new FamilySettings(
                    family,
                    16,
                    12.5f,
                    20f,
                    2.125d,
                    CostCurve.FLAT,
                    PATTERN_COBBLE_POLISHED,
                    1.0f,
                    8,
                    0f);
            case STONE:
                return new FamilySettings(family, 16, 25f, 40f, 4.0d, CostCurve.FLAT, PATTERN_CRACK, 1.0f, 8, 0f);
            case SNOW:
                // The softest thing here. Snow packs down under the first few passes and holds the
                // shape, which is why a trodden path across a snowfield reads before any other
                // surface does - two and two fifths passes a gradation, a hundred and ninety-two to
                // wear the whole way through. It also comes back faster than anything else, and for
                // a reason nothing else has: snow is the one surface that gets replaced from above.
                // Six in-game days to lose a whole path, against stone's three hundred and twenty.
                //
                // The lightest of the compounds, which is the only one that suits it. Snow shows
                // wear harder than anything else here - the plain rub took it seventy levels down,
                // more than any other family loses - so the look that reads on stone flattens snow
                // to grey. This one stops a little over halfway along its parent's run and lands
                // five levels lighter than the rub did, while a crack through it gives packed snow
                // the broken surface it actually has. That is its classic look; it ships the look
                // made for it, a packed, cooler, walked-in track (Xep, 2026-11-12, from the v2 comparison sheets:
                // "Let's do new, with the idea I had before of a
                // global + per family toggle for bringing back the old wear styles").
                return new FamilySettings(
                    family,
                    16,
                    0.8f,
                    1.6f,
                    0.075d,
                    CostCurve.PACK,
                    PATTERN_SNOW_PACKED,
                    1.0f,
                    8,
                    0f);
            case ICE:
                // Fragile, and it took two goes to write that down honestly. Ice used to be the
                // most stubborn ground in the mod - dearer to mark than stone and slower to mend
                // than anything but the Nether - which is not what ice is at all. It is now the
                // softest thing here after snow and sand: twenty-six passes a gradation against
                // stone's sixty-five, and a whole path gone in thirty-one days.
                //
                // Quick to mend for a reason nothing else here has. Ice is the only ground in the
                // game that re-forms itself; water freezes back over a scuff and the mark is
                // simply not there any more, no filling in and nothing growing over it. Its rut
                // stays shallow whatever happens - half the depth the earthy families reach -
                // because the surface is being scuffed dull rather than carried away.
                //
                // Cracked and then trodden, which is what scratching dull looks like on something
                // clear. It is by far the biggest change of the six - eighty-seven levels at the
                // end of the run against the rub's forty-nine - and it can be, because ice starts
                // brighter than anything else in the game and had the furthest to fall. That is its
                // classic look; it ships the look made for it, which scuffs toward white frost and
                // cracks only late, since scratched ice goes milky rather than dark (Xep, 2026-11-12, from the v2
                // comparison sheets: "Let's do new, with the idea I had before of a
                // global + per family toggle for bringing back the old wear styles").
                return new FamilySettings(
                    family,
                    16,
                    10f,
                    16f,
                    0.65d,
                    CostCurve.GLAZE,
                    PATTERN_ICE_SCUFFED,
                    1.0f,
                    4,
                    0f);
            case FLESH:
                // Biomes O' Plenty's flesh (0.9.229, W4 T17), the floor of five of its Nether biomes. Read from its
                // jar: sponge material, 0.4 to dig, gravel's sound, a collision box an eighth short of the top and a
                // drag on whatever walks in it - soul sand's twin in all but what it is made of, and soul sand wears
                // as sand. So sand's prices, as soft as anything here but snow. Snow's curve rather than sand's,
                // because flesh is pressed rather than displaced: cheap at first, dearer once there is nothing left
                // to squeeze. Its mend is sand's and a quarter again, the Nether's own rule, as nothing falls on it
                // there: thirty-seven and a half days for a whole path. Its look is the one made for it, bruised and
                // wet where trodden (Xep, 2026-11-12, shown it beside the smoothed rub flesh arrived with: "Let's go
                // with the new ones"): a buffed rub read as worn sand, and flesh is pressed, not worn away.
                return new FamilySettings(family, 16, 1.2f, 2.4f, 0.46875d, CostCurve.PACK, PATTERN_FLESH, 1.0f, 8, 0f);
            case TAINT:
                // Thaumcraft's crusted taint (0.9.229, W4 T17), the crust of the Tainted Land. Read from its jar:
                // 1.75 to dig, between stone and cobblestone, and it drops nothing. A crust breaks before it wears,
                // so cobblestone's prices at one price the whole way. Its mend is turf's, a hundred and twenty-five
                // days for a whole path, because taint is the one ground here besides grass that grows: it ticks and
                // creeps across its biome, and a road through it closes over the way a lawn does. Its look is the one
                // made for it, blighted (Xep's choice of 2026-11-12, as flesh's above), where it arrived with
                // cobblestone's cracked rub: a crack cuts dark lines into stone, and taint's fibres stand out lighter
                // than its crust while the blight grows back over the path.
                return new FamilySettings(family, 16, 12.5f, 20f, 1.5625d, CostCurve.FLAT, PATTERN_TAINT, 1.0f, 8, 0f);
            case SCULK:
                // The deep dark's living skin (0.9.229, W4; the design, 0.9.229-new-families.md 3). Vanilla's sculk is
                // 0.2 to dig and Et Futurum's 0.6, softer than flesh either way, and both are soft living floor that a
                // foot presses rather than carries away: so flesh's prices and snow's curve, cheap at first and dearer
                // once there is nothing left to press. Its face is within sixty-five levels of black and its specks
                // are its whole character, so its look is the one made for it (Xep's choice of 2026-11-12, as
                // flesh's above), where it arrived with the heavy buff, which took every speck down together: here
                // its glints go out one at a time, then the skin goes smooth, grey and dead, and as no worn picture
                // moves the first worn layer also stops the pulse. Trodden sculk goes still. Dirt's mend, a hundred
                // days for a whole path: nothing falls in the deep dark and sculk never grows back by itself, only
                // spreading where something dies by a catalyst, which is colonisation from the edges, what dirt's
                // figure is.
                return new FamilySettings(family, 16, 1.2f, 2.4f, 1.25d, CostCurve.PACK, PATTERN_SCULK, 1.0f, 8, 0f);
            case CLOUD:
                // Twilight Forest's fluffy cloud (0.9.229, W4; the design, 0.9.229-new-families.md 4). Not ground
                // that is carried away: a foot presses a dent and the fluff closes round it. Its face has ten levels
                // of relief in all, so a look that flattens changes nothing and only one that marks a track shows,
                // and snow, the other white family, showed a full rub takes white to grey. It arrived with the
                // lightest rub and ships the look made for it (Xep's choice of 2026-11-12, as flesh's above): packed
                // flatter, a denser track pressed in, and a lighter rim of the fluff pushed aside, which is what
                // makes a white track legible at all. Snow's prices, curve and pace throughout, the softest
                // thing a foot meets, and filled again as quickly as a footprint in fresh snow, six days for a whole
                // path; over its forty-eight steps that is an eighth of a day a step. Half a block's rut would be
                // carried away; a pressed trough stays shallow, four pixels, as ice's does.
                return new FamilySettings(family, 16, 0.8f, 1.6f, 0.125d, CostCurve.PACK, PATTERN_CLOUD, 1.0f, 4, 0f);
            case NETHER:
            case END:
                // Stone's thresholds exactly. Netherrack is soft to dig and end stone is not, but
                // neither is ground that a road wears into quickly, and giving them their own wear
                // figures would only be inventing a difference nobody asked to see.
                //
                // Their recovery is stone's and a quarter again, which is a difference somebody did
                // ask to see, and it is the one thing about these two dimensions that a player can
                // reason about without being told. Every surface in the overworld is mended by
                // weather - rain fills, frost heaves, plants creep back in - and neither of these
                // places has any. Nothing falls on a road in the Nether. So a mark made here holds
                // four hundred days where the same mark in stone holds three hundred and twenty.
                //
                // Not stone's look, though, and that is the difference worth having. Both are
                // already violently patterned - netherrack is all noise and end stone all speckle -
                // so a crack laid over either lands in a face that has no quiet in it to break.
                // Buffing first takes that noise down and lets the fissures be the thing you see;
                // both come out about four levels lighter than the plain crack as well, which
                // matters more for end stone than for anything else here, since it wears further
                // than any surface in the game.
                //
                // That smoothed crack is both families' classic look. Each now ships the look made
                // for it: the Nether's ground chars, and end stone bleaches pale, unevenly, with grit
                // and faint cracks (Xep, 2026-11-12, from the v2 comparison sheets: "Let's do new, with the idea I had
                // before of a
                // global + per family toggle for bringing back the old wear styles").
                return new FamilySettings(
                    family,
                    16,
                    25f,
                    40f,
                    5.0d,
                    CostCurve.FLAT,
                    family == SurfaceFamily.NETHER ? PATTERN_NETHER_CHARRED : PATTERN_END_BLEACHED,
                    1.0f,
                    8,
                    0f);
            case LEAVES:
                // No stages, so nothing here is ever priced. The figures are what these two
                // would take if somebody switched them on rather than a placeholder, because a
                // nought in this column would divide by itself the moment they did.
                return new FamilySettings(family, 0, 16f, 24f, 1.5d, CostCurve.FLAT, PATTERN_DIRT, 0f, 0, 1.0f);
            default:
                return new FamilySettings(family, 0, 16f, 24f, 1.5d, CostCurve.FLAT, PATTERN_DIRT, 0f, 0, 1.0f);
        }
    }

    // ------------------------------------------------------------------
    // Classic wear
    // ------------------------------------------------------------------

    /**
     * The look a family shipped with before the look made for it, or null for a family whose look never changed.
     *
     * <p>
     * Xep, 2026-11-12, choosing the new looks for all eleven families that have one: "Let's do new, with the idea I had
     * before of a global + per family toggle for bringing back the old wear styles", the old ones "just left there in
     * case users want to use them". For the seven older families this is what 0.9.228 shipped, read from its
     * {@code defaultsFor}; flesh, taint, sculk and cloud were not in 0.9.228, and theirs is the look each arrived with
     * in 0.9.229's snapshots. Like every look id, these never change: a classic look is a promise about the past.
     */
    public static String classicLook(SurfaceFamily family) {
        switch (family) {
            case SAND:
            case FLESH:
                return PATTERN_SMOOTH_DIRT;
            case GRAVEL:
                return PATTERN_DIRT;
            case COBBLE:
            case ICE:
            case TAINT:
                return PATTERN_CRACK_DIRT;
            case SNOW:
                return PATTERN_CRACK_DIRT_LITE;
            case NETHER:
            case END:
                return PATTERN_SMOOTH_CRACK;
            case SCULK:
                return PATTERN_SMOOTH_HEAVY;
            case CLOUD:
                return PATTERN_DIRT_LITE;
            default:
                return null;
        }
    }

    /**
     * The look a family is drawn in, from the look its file names and the two classic wear switches.
     *
     * <p>
     * A switch brings back the classic look only where the file names the family's shipped default: that is a family
     * nobody has chosen a look for. Any other look in the file was chosen by somebody, and it wins over both switches,
     * the same line the rest of the config draws between what the mod wrote and what a player typed. The cost of
     * drawing it there is that the shipped default itself, chosen by hand, cannot be told from no choice: with a switch
     * on, a family set back to its new look by hand still draws the classic one, and the way to see the new look is to
     * turn its switch off.
     */
    public static String effectiveLook(SurfaceFamily family, String chosen, boolean classicEverywhere,
        boolean classicHere) {
        String classic = classicLook(family);
        if (classic == null || !(classicEverywhere || classicHere)) return chosen;
        return defaultsFor(family).wearPattern.equals(chosen) ? classic : chosen;
    }

    /**
     * Whether a settings file holds a family's look at its classic look, written there by a build whose default it
     * was: untouched, rather than chosen.
     *
     * <p>
     * Forge keeps whatever is in the file and writes a default only into a file that has none, so every settings file
     * 0.9.228 (or a 0.9.229 snapshot) wrote holds the old look by name, and the new default would reach nobody who had
     * run the mod before. The comment Forge writes above the key ends with the default of the build that saved it, so
     * the old look under the old look's own default is a file nobody has changed since; the same look under this
     * build's default is somebody choosing the classic look again, and stays. The rule {@code
     * TrmtConfig.writtenAtOldCeiling} keeps for the textured-surfaces ceiling, here for one family's block.
     */
    static boolean untouchedOldLook(String text, String key, String old) {
        if (text == null || old == null) return false;
        java.util.regex.Matcher open = java.util.regex.Pattern
            .compile("(?m)^\\s*" + java.util.regex.Pattern.quote(key) + "\\s*\\{\\s*$")
            .matcher(text);
        int families = text.indexOf("families {");
        while (open.find()) {
            if (families >= 0 && open.start() < families) continue;
            int close = text.indexOf('}', open.end());
            String block = text.substring(open.end(), close < 0 ? text.length() : close);
            String quoted = java.util.regex.Pattern.quote(old);
            return java.util.regex.Pattern
                .compile("(?m)default: " + quoted + "\\]\\s*\\R\\s*S:wearPattern\\s*=\\s*" + quoted + "\\s*$")
                .matcher(block)
                .find();
        }
        return false;
    }

    private static String fileText(Configuration config) {
        java.io.File file = config.getConfigFile();
        if (file == null || !file.isFile()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException unreadable) {
            return null;
        }
    }

    /**
     * What each family wears through into out of the box.
     *
     * <p>
     * Only the ones with somewhere honest to go. Sand under sand is sand and snow under snow is
     * snow, so those hollow out as themselves; netherrack and end stone are left alone for the
     * same reason the rest of their tuning is stone's, which is that inventing a difference
     * nobody asked to see is worse than having none.
     */
    private static String[] defaultWearsThroughTo(SurfaceFamily family) {
        switch (family) {
            case GRASS:
                return new String[] { SurfaceFamily.DIRT.key() };
            case STONE:
                return new String[] { SurfaceFamily.COBBLE.key(), SurfaceFamily.GRAVEL.key(),
                    SurfaceFamily.DIRT.key() };
            case COBBLE:
                return new String[] { SurfaceFamily.GRAVEL.key(), SurfaceFamily.DIRT.key() };
            case GRAVEL:
                return new String[] { SurfaceFamily.DIRT.key() };
            case SCULK:
                // The rock the deep dark's sculk is spread over, drawn as deepslate (FaceRules.fallbackName), and on
                // by default: sculk answers to this list alone, as grass answers to its own switch (TrmtConfig
                // .wearsThroughTo; Xep, 2026-11-11, "Yes, by default"). Emptied, sculk sinks as itself.
                return new String[] { SurfaceFamily.STONE.key() };
            default:
                return new String[0];
        }
    }

    /** Blocks that resist wear out of the box: the ones that are already a made path. */
    private static String[] defaultResistant(SurfaceFamily family) {
        if (family == SurfaceFamily.DIRT) {
            return new String[] { "etfuturum:grass_path", "minecraft:grass_path" };
        }
        return new String[0];
    }

    /**
     * What each family takes as payment besides a block of itself.
     *
     * <p>
     * Only the two made of earth need it. Sand, gravel, stone and cobblestone are cheap and
     * come by the stack in their own right, so a block of the thing itself is no hardship.
     */
    private static String[] defaultRepair(SurfaceFamily family) {
        if (family == SurfaceFamily.GRASS || family == SurfaceFamily.DIRT) {
            return new String[] { "minecraft:dirt" };
        }
        return new String[0];
    }

    /**
     * The blocks each family is seeded with before detection runs: made paths for dirt, which
     * detection leaves alone, and vegetation's vanilla plants.
     */
    public static String[] seeded(SurfaceFamily family) {
        return defaultExtra(family);
    }

    private static String[] defaultExtra(SurfaceFamily family) {
        if (family == SurfaceFamily.DIRT) {
            // Paths are made ground, not natural ground, so detection leaves them alone; they
            // are named here instead, and resist wear rather than being immune to it. Et Futurum
            // registers the 1.7.10 one; the vanilla name is listed for packs that add it another
            // way, and a name nothing provides is skipped without complaint.
            return new String[] { "etfuturum:grass_path", "minecraft:grass_path" };
        }
        if (family == SurfaceFamily.VEGETATION) {
            return new String[] { "minecraft:tallgrass", "minecraft:yellow_flower", "minecraft:red_flower",
                "minecraft:double_plant", "minecraft:deadbush" };
        }
        return new String[0];
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Reads every family's section from the config, creating it with defaults if absent. */
    public static Map<SurfaceFamily, FamilySettings> readAll(Configuration config) {
        Map<SurfaceFamily, FamilySettings> out = new EnumMap<SurfaceFamily, FamilySettings>(SurfaceFamily.class);
        // Read once, before anything below declares a key: the comments in it are the last build's to save it.
        String text = fileText(config);
        for (SurfaceFamily family : SurfaceFamily.values()) {
            out.put(family, read(config, family, text));
        }
        return out;
    }

    private static FamilySettings read(Configuration config, SurfaceFamily family, String text) {
        FamilySettings settings = defaultsFor(family);
        settings.wetRecoverySpeed = defaultWetRecoverySpeed(family);
        String category = TrmtConfig.CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER + family.key();

        // Leaves and vegetation read these four keys and none of the tuning after them, so the words
        // written beside those four have to be about a family with no stages: what every other family
        // is told describes chains, prices and ruts that a trampling target never has.
        String key = family.key();
        String categoryComment;
        String enabledComment;
        String slabsComment;
        String stairsComment;
        if (family.staged) {
            categoryComment = "Everything about how " + key
                + " wears: whether it does, how many gradations it goes through, how deep it sinks under foot, how long it takes to recover, and which blocks count as it. The blocks list is filled in by startup detection and is also yours to edit - see the surfaces heading for how removing an entry actually works.";
            enabledComment = "Whether " + key + " surfaces erode at all.";
            slabsComment = "Whether slabs cut from " + key
                + " wear too. There is nothing separate to tune: a slab walks the same chain at the same prices as a whole block of the same stuff, so it takes exactly as many crossings to wear through and exactly as long to recover, and the only difference is that the rut is drawn half as deep - half a block cannot lose eight pixels and still be there. Detection finds them the same way it finds everything else, by what the block is made of and what it is called with the shape word taken off, so a modded slab of this material is included without anybody naming it.";
            stairsComment = "Whether stairs cut from " + key
                + " wear too, on the same terms as slabs above: the same chain, the same prices, and no depth at all. A stair states its own shape and never has a height clamped onto it, so it shows every gradation of its wear in its face and none of it underfoot - which is the one way a stair differs from the slab above, and is why it is worth its own switch.";
        } else {
            categoryComment = "Which blocks count as " + key
                + ", and whether any do. Nothing counted as "
                + key
                + " is ever drawn worn, and it has no gradations; this heading exists for trampling."
                + key
                + (family == SurfaceFamily.VEGETATION
                    ? ", which reads it, and trampling.leaves asks it too, since only a plant counted here leaves a leaf beneath it open to the traffic"
                    : ", the only thing that reads it")
                + ". The tally is fixed at sixteen to twenty-four, in the wear multipliers.player adds per step - thirty-two to forty-eight crossings on foot at the shipped speeds - and is not set anywhere in this file; how fast it fades is healing.wearDecayPerDay. The blocks list is filled in by startup detection and is also yours to edit - see the surfaces heading for how removing an entry actually works.";
            enabledComment = "Whether any block counts as " + key
                + " at all. Off, trampling."
                + key
                + " has nothing to act on and never breaks anything, and every tally it was keeping is dropped at the next sweep, as a block's is when it is taken out of the list below - where switching trampling."
                + key
                + " itself off keeps its tallies and lets them fade."
                + (family == SurfaceFamily.VEGETATION
                    ? " Off also means no plant above a leaf leaves the leaf open to trampling.leaves."
                    : "");
            slabsComment = "Whether slabs of " + key
                + " count as "
                + key
                + ". There is nothing to tune: a slab of it is trampled exactly as a whole block is.";
            stairsComment = "Whether stairs of " + key + " count as " + key + ", on the same terms as slabs above.";
        }
        config.setCategoryComment(category, categoryComment);
        settings.enabled = config.getBoolean("enabled", category, true, enabledComment);
        settings.slabs = config.getBoolean("slabs", category, true, slabsComment);
        settings.stairs = config.getBoolean("stairs", category, true, stairsComment);
        settings.autoDetect = config.getBoolean(
            "autoDetect",
            category,
            true,
            "Whether startup detection may classify installed blocks into this family. Turn off and use the list below to control it by hand.");
        settings.extra = config.get(
            category,
            "blocks",
            defaultExtra(family),
            "Blocks treated as " + family.key()
                + ". Detection fills this in with what it found, so it doubles as a record of what is actually being affected; anything you add by hand is kept. Format: modid:block, modid:block:meta, or modid:block:* for every metadata. Removing an entry only sticks once surfaces.autoDetect is off, because with it on detection puts the block straight back - use surfaces.exclude for that instead.")
            .getStringList();

        if (!family.staged) return settings;

        settings.stages = config.getInt(
            "stages",
            category,
            settings.stages,
            1,
            SurfaceFamily.MAX_STAGES,
            "How many wear gradations this surface shows before it starts to sink. Once it does, each further pixel of depth gets 'layersPerDepth' gradations of its own, so the whole run from untouched to the deepest rut is usually stages + layersPerDepth * maxSinkPixels steps - eighty at the shipped figures, and forty-eight for ice, which sinks only half as far as the rest. Two of the headings here are not that arithmetic, and both are worth knowing before pricing anything against it. Grass has no depth of its own, because turf is a face rather than a substance, so it has no pixels to measure a run in and borrows them from the first surface it wears through into: with general.grassWearsThroughToDirt on, which is how it ships, grass runs earth's eighty steps rather than the sixteen this sum would give, and with that switch off there is nothing to borrow from and the chain really does stop dead at sixteen. Dirt is one step shorter than the sum rather than longer, because a ghost showing no wear at all is indistinguishable from the earth it is covering, so dirt's first layer is skipped and its run is seventy-nine. Both figures matter beyond curiosity, since a run's length is what healDaysPerStage below is multiplied by for how long a fully worn path takes to fade. The wear table in game reads the real chain for every family, so it is the place to check a run after changing anything here.");
        settings.thresholdMin = readFloat(
            config,
            category,
            "thresholdMin",
            settings.thresholdMin,
            "Wear needed to advance one stage, lower bound of the per-position random draw. The draw is what keeps path edges ragged instead of geometric.");
        settings.thresholdMax = readFloat(
            config,
            category,
            "thresholdMax",
            settings.thresholdMax,
            "Upper bound of the per-position random draw.");
        settings.healDaysPerStage = config.get(
            category,
            "healDaysPerStage",
            settings.healDaysPerStage,
            "In-game days this family's stage must go untouched before it steps back. What a fully worn path costs altogether is this times the length of the family's whole run, and that run is longer than the stages figure above suggests: each pixel of depth adds layersPerDepth gradations of its own, and a family with no depth of its own takes both of those figures from the first surface it wears through into, because the ground beneath is then the only rut it can have. Which is why turf, whose maxSinkPixels is nought, still runs eighty gradations - sixteen of thinning green and then sixty-four down through the earth - and takes a hundred and twenty-five days to go rather than the twenty-five its own stages alone would come to. Turn general.grassWearsThroughToDirt off and there is nothing left for it to borrow: the run stops at sixteen, and twenty-five days is then the honest figure. Earth is one gradation shorter than the arithmetic gives, because a ghost showing no wear at all is indistinguishable from the dirt it is lying on and so its first layer is skipped; its run is seventy-nine rather than eighty, which is why its default is written as a hundred days divided by seventy-nine and comes out at a hundred exactly. All of this holds while general.wearPaceFollowsTheGround is on, which is how it ships, and that switch is what keeps every gradation of a run mended at the rate of the ground the path is made of rather than at the rate of whatever it happens to be drawn as by then. The wear table in game works the total out for every family from these same numbers and is the place to read it after changing one. Healing is applied from the world clock when a chunk loads, so a chunk nobody has visited still recovers. This is the family's own figure and is not worked out from its wear cost: what it takes to mark a surface and what it takes for that mark to fade are genuinely different questions, and snow against sand is the pair that proves it.",
            0.01d,
            100000d)
            .getDouble();
        String curveName = config.getString(
            "costCurve",
            category,
            settings.costCurve.key(),
            "How the price of a gradation changes as this surface wears further down its run. Every step used to cost the same, which is not what any of these surfaces do and is why early wear read as inert: the gradation you actually watch appear, the first, cost exactly as much as the last. flat holds one price the whole way, which is right for dressed stone - it does not compact underfoot, and the hundredth scuff costs what the first did. sod starts cheap and ends about four fifths dearer, for turf that tears at a touch over earth that is packed and rooted and does not. settle climbs to two and a half times, for sand and gravel, which displace under the first passes and then settle into a shape they have already found. pack climbs fourfold and stops climbing three quarters of the way along, for snow: one footfall in fresh powder is a whole gradation, what it leaves is packed snow and a different substance, and once thoroughly packed there is nothing left to compress. glaze climbs twelvefold in a straight line and never levels off at all, for ice, which does not compact and settle but merely polishes what is left, so every gradation is dearer than the one before it right to the end. Whichever is chosen, the total does not move. The multipliers over a whole run average to exactly one, so a curve only redistributes what the run costs - it can never quietly make a family easier or harder while appearing to be a change of shape, and every figure in the wear table survives changing it. What changes is where the cost sits: how quickly a path first shows, against how hard it is to drive one all the way down.",
            CostCurve.keys());
        CostCurve chosen = CostCurve.byKey(curveName);
        if (chosen == null) {
            com.trmtgtnh.Trmt.LOG.warn(
                "families.{}.costCurve is set to {}, which is not a curve this mod knows; keeping {}",
                new Object[] { family.key(), curveName, settings.costCurve.key() });
        } else {
            settings.costCurve = chosen;
        }
        settings.healsOnlyWhenWet = config.getBoolean(
            "healsOnlyWhenWet",
            category,
            false,
            "Whether this surface recovers only while it is being rained on - or snowed on, for snow and ice, which is the same weather asked of a colder place. Off, which is how every family ships, it recovers on the clock alone. On, the days it is owed still bank up exactly as they did, so nothing is lost by a dry spell and a chunk nobody has visited for a month still holds every day of it; what changes is that the debt is only paid while something is actually falling, and paid a gradation at a time rather than all at once. Read the weather heading before switching this on: three settings there decide whether sheltered ground, dry biomes and weatherless dimensions are excluded, and each of those is a place where this can quietly mean never.");
        settings.wetRecoverySpeed = readFloat(
            config,
            category,
            "wetRecoverySpeed",
            settings.wetRecoverySpeed,
            "How much faster this surface recovers while rain is actually falling on it. One is no difference at all and is what stone, cobble, netherrack and end stone ship with; two, which is what turf, earth, sand and gravel ship with, means a rut mends in half the untouched days while it is raining on it. Unlike the healsOnlyWhenWet switch above, this can never mean never - it is a discount rather than a gate, so ground the rain cannot reach simply does not earn it and mends at its ordinary rate, and anything under one is read as one. Whether rain is reaching a particular square is decided by the same three settings under the weather heading that the switch above uses: an open sky, a biome that has weather, and a dimension that has any. A family that IS waiting for weather does not also get the discount, because that would be paying for the same storm twice.");
        if (settings.wetRecoverySpeed < 1.0f) settings.wetRecoverySpeed = 1.0f;
        String shippedLook = settings.wearPattern;
        settings.wearPattern = config.getString(
            "wearPattern",
            category,
            shippedLook,
            "Which wear look shapes this family's textures, all worked from the block's own pixels so any of them is safe on a block nobody drew art for. grass takes the cover off in patches and lets the face underneath show through, which is what turf does and what anything with a different underside does - podzol, mycelium, mossy stone over clean. crack dulls a surface down and splits it open, for stone that fissures and grimes over rather than polishing smooth, and crack_lite is the same run stopped a little over halfway along, so a face grimes and hairlines without ever breaking up. smooth flattens a surface toward its own average, for something that buffs shiny underfoot, and smooth_heavy takes rather more of the relief with it, leaving about a third of the light and dark the block began with. dirt rubs a growing patch of the surface away, which is what a trodden path is, and dirt_lite rubs a narrower and shallower one, leaving three fifths of the face exactly as it was. The last four do two of those in one look, and every id is written in the order its own halves run, so a file says what will happen without anybody reading the source. smooth_crack and smooth_dirt buff the whole face flat before cracking it or before wearing a track across part of it, because everything the second half of those aims at is read out of the surface it is handed and buffing afterwards would only close up the fissures and the track that had just been made. crack_dirt goes the other way round, and for a measured reason rather than a preference: a crack run last blurs a track's edge away to a quarter of what it was, while a track worn last leaves two fifths of the face carrying the fracture network exactly as a plain crack drew it, pixel for pixel. It is shown in the wear table as cracked rubbed, and crack_dirt_lite is that same run stopped early - the identical call with the eased progress at 0.55 of itself and nothing else altered, so every picture it draws is one crack_dirt genuinely passes through, pixel for pixel. It is shown in the wear table as cracked rubbed lite. The four after those were made for one material each and are named for it. flesh darkens a growing trodden patch toward the block's own shadow color, closes its grain up and puts a few wet highlights on its raised points, for something soft that bruises rather than wears. taint darkens the ground in spreading blotches while the block's own lighter strands, and a fine network drawn over them, swell toward its brightest color, for a blight that grows back over a path. sculk puts most of the block's glints out one at a time, keeps about a fifth of them lit to the end, and flattens, greys and dulls the rest of the face, for a living skin trodden still. cloud packs the whole face flatter and presses a denser, slightly greyer track into it with a lighter rim of pushed-up fluff, for a soft white solid. Seven more were made for the older families, each named for its material and what a foot does to it: sand_trodden packs a firmer, darker line with loose grains heaped lighter along its edges; gravel_parted opens the gaps between stones and darkens them while the whole trodden ground grimes over, so the flint stands out; cobble_polished polishes the stone tops smooth and dulls them, grinds the mortar darker, and from a quarter of the way cracks and scuffs them, more finely and darker still toward the end; snow_packed packs a smoother, cooler, grey-blue track, walked in darker in patches, with a few glassy glints; ice_scuffed clouds the surface toward white frost in a spreading haze crossed by fine scratches, and past three quarters of the way dark cracks open through it; nether_charred chars a track darker, flatter and sootier, with flecks of ash; end_bleached bleaches a track paler, greyer and polished smooth, unevenly, with grit ground into it and, from two fifths of the way, a faint network of cracks. Each takes every color it uses from the block itself, lightened, darkened or greyed, so each is as safe on any block as the rest. dirt is what anything unrecognised falls back to - including the older sand and polish names, which are still read so that no file breaks. Read by your own client and by nobody else, so this is your choice on somebody else's server as much as in a world of your own, and a server's copy of this key changes nothing for anybody. Change this to experiment: a family's whole look follows from it. Each family with a look made for it ships that look; the look it shipped with before is its classic look, which classicWear below and client.classicWear bring back while this names the shipped default, and any other look written here wins over both. A file an earlier build wrote with the old default here untouched is moved on to the new default the first time this build reads it.",
            offeredLooks());
        String classic = classicLook(family);
        if (classic != null && classic.equals(settings.wearPattern) && untouchedOldLook(text, family.key(), classic)) {
            config.getCategory(category)
                .get("wearPattern")
                .set(shippedLook);
            com.trmtgtnh.Trmt.LOG.info(
                "families.{}.wearPattern was the old default {}; now {}, the look made for it (classicWear brings the old one back)",
                new Object[] { family.key(), classic, shippedLook });
            settings.wearPattern = shippedLook;
        }
        if (classic != null) {
            settings.classicWear = config.getBoolean(
                "classicWear",
                category,
                false,
                "Whether " + family.key()
                    + " is drawn in its classic look, "
                    + classic
                    + ", the one it shipped with before the look made for it, "
                    + shippedLook
                    + ". Only while wearPattern above names "
                    + shippedLook
                    + ": a look written there by hand wins over this. client.classicWear does the same for every family that has a classic look. Read by your own client and by nobody else. Takes effect on the next resource reload.");
        }
        settings.wearPattern = effectiveLook(
            family,
            settings.wearPattern,
            TrmtConfig.classicWear,
            settings.classicWear);
        settings.wearStrength = readFloat(
            config,
            category,
            "wearStrength",
            settings.wearStrength,
            "How strongly the pattern is applied, 0 to 1. Lower values leave more of the block's original character showing through.");
        settings.layersPerDepth = config.getInt(
            "layersPerDepth",
            category,
            settings.layersPerDepth,
            1,
            SurfaceFamily.MAX_STAGES,
            "How many visual gradations this family shows between one pixel of sinking and the next. The first run, before anything has sunk, uses the longer 'stages' count instead: that is the wear people actually watch, and it earns the detail.");
        settings.maxSinkPixels = config.getInt(
            "maxSinkPixels",
            category,
            settings.maxSinkPixels,
            0,
            com.trmtgtnh.erosion.SinkProfile.MAX_SINK_PIXELS,
            "How far this family's most worn stage physically sinks, in sixteenths of a block. 0 means it only ever discolors, unless it wears through into another surface: then it sinks as deep as the first of those, with that surface's layersPerDepth, which is how grass gets its rut. Whether that sinking is something you can walk down into or only something you can see is set by physicalDecay in the general category.");
        // Retired. Where a family starts to sink is decided by its stages: a run lays that many
        // gradations on the face first and every step after them sinks. So this fraction had nothing
        // left to move - it was read, sent to visiting clients and counted as a change, and nothing
        // that builds a run, a rut or a collision box ever asked it. Taken out of the file rather
        // than left promising that 1.0 stops a family sinking; maxSinkPixels at nought is what does.
        if (config.getCategory(category)
            .containsKey("sinkStartFraction")) {
            config.getCategory(category)
                .remove("sinkStartFraction");
        }

        settings.resistantBlocks = config.get(
            category,
            "resistantBlocks",
            defaultResistant(family),
            "Blocks in this family that wear far more slowly, listed the same way as the blocks setting above. Grass paths are here by default: someone already made that path on purpose, so it should stand up to traffic rather than being immune to it or degrading like turf.")
            .getStringList();
        settings.resistance = readFloat(
            config,
            category,
            "resistance",
            settings.resistance,
            "How much more wear the blocks listed above take before they advance a stage. Healing is unaffected, so a resistant surface recovers at the same rate as any other.");
        if (settings.resistance < 1f) settings.resistance = 1f;

        settings.maxWear = readFloat(
            config,
            category,
            "maxWear",
            1.0f,
            "How far along its run this surface may wear, as a fraction of the whole. 1 is the entire run from untouched to a rut half a block deep; 0.25 stops it about where a path reads as a path. The global general.maxWearFraction applies as well and whichever is lower wins, so a world can be capped once without editing every family.");

        settings.repairBlocks = config.get(
            category,
            "repairBlocks",
            defaultRepair(family),
            "What a worn " + family.key()
                + " surface may also be mended with, besides a block of itself, listed the same way as the blocks setting above. Grass and dirt both take plain dirt out of the box: grass is what the world grows and earth is what a player digs, so the repair should cost the commoner of the two. Prefix an entry with 'ore:' to name an ore dictionary group instead of a block. Empty means only a block of this surface will do - or, while healing.repairAnyInFamily is on, any block of the same family. Read by bone meal and the chunk tamper while bonemealCostsABlock is on, and by the golem always; the hand tamper pays in what the ground drops and does not read this list.")
            .getStringList();

        settings.wearsThroughTo = config.get(
            category,
            "wearsThroughTo",
            defaultWearsThroughTo(family),
            "Which surfaces this one exposes as it wears through, named the way the headings here name them - grass, dirt, sand, gravel, stone, cobble, snow, ice, nether, end, flesh, taint, sculk, cloud - and listed in the order the ground reaches them. Stone shows cobble, then the grit that broke off it, then the earth the road was laid on; grass shows the earth it was growing out of. The pixels of depth this family sinks are shared out between however many are listed, earliest first, so the list changes what a rut looks like on the way down and changes neither how many steps it takes to get there nor what any of them costs. A name that is this family's own, a name nothing matches, and a family that is switched off are all skipped. Empty means the ground hollows out as itself, which is what sand and snow do. Turned off wholesale by general.wearThroughToOtherSurfaces - except for grass, which has no depth of its own to hollow out and so answers to general.grassWearsThroughToDirt instead, and sculk, which answers to this list alone: it ships showing the deep dark's rock in its rut, and emptying the list makes it sink as itself.")
            .getStringList();

        if (settings.wearStrength < 0f) settings.wearStrength = 0f;
        if (settings.wearStrength > 1f) settings.wearStrength = 1f;
        return settings;
    }

    /** Declared with the default as the float's own decimal (0.9.228), as {@link TrmtConfig#declareFloat} says why. */
    private static float readFloat(Configuration config, String category, String name, float def, String comment) {
        return TrmtConfig.declareFloat(config, category, name, def, 0f, 1000000f, comment);
    }

    /** Threshold draw range after the global erosion speed multiplier. */
    /**
     * The threshold range as configured, with no speed multiplier applied.
     *
     * <p>
     * Named for what it no longer does, deliberately kept so nothing else has to change: the
     * multiplier is applied where a threshold is compared against rather than where it is drawn,
     * so that changing the speed affects ground that has already been walked on.
     */
    public float scaledMin() {
        return thresholdMin;
    }

    public float scaledMax() {
        return thresholdMax;
    }

    /** In-game days per stage after the global healing multiplier. */
    public double scaledHealDays() {
        return healDaysPerStage
            / (TrmtConfig.positive(TrmtConfig.healingRate) * TrmtConfig.positive(TrmtConfig.globalSpeed));
    }
}
