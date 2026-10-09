package com.trmtgtnh.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.PhysicalDecay;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.MobEntries;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Everything tunable, in one Forge {@link Configuration} file.
 *
 * <p>
 * Server and client settings share a file rather than being split across two. The server is
 * authoritative for every value except the {@code client} category, which only affects what
 * the player in front of this particular game sees.
 *
 * <p>
 * <b>On the defaults.</b> Upstream TRMT is tuned for a vanilla-paced world, where a path
 * appearing within one session is the point. GTNH is played for hundreds of hours across
 * months, and at upstream's rate everything within sight of a base would be bare dirt on the
 * first day. The shipped thresholds are roughly eight times upstream's, healing is slower
 * still, and stone barely marks at all. {@link #globalSpeed} moves the whole thing at once;
 * {@link #erosionSpeed} and {@link #healingRate} tilt one side against the other; and
 * {@link FamilySettings} holds the per-surface tuning.
 */
public final class TrmtConfig {

    public static final String CATEGORY_FAMILIES = "families";
    public static final String CATEGORY_SURFACES = "surfaces";
    public static final String CATEGORY_MULTIPLIERS = "multipliers";
    public static final String CATEGORY_HEALING = "healing";
    public static final String CATEGORY_TRAMPLING = "trampling";
    public static final String CATEGORY_EXPLOSIONS = "explosions";
    public static final String CATEGORY_IMPACTS = "impacts";
    public static final String CATEGORY_PERFORMANCE = "performance";
    public static final String CATEGORY_REINFORCE = "reinforce";
    public static final String CATEGORY_WARD = "spawnward";

    public static final String CATEGORY_LIGHT = "pathlight";
    public static final String CATEGORY_INTEGRATION = "integration";
    public static final String CATEGORY_SPAWN_ITEMS = "spawnitems";
    public static final String CATEGORY_LOOT = "loot";
    public static final String CATEGORY_POTIONS = "potions";
    public static final String CATEGORY_WEATHER = "weather";
    public static final String CATEGORY_GOLEM = "golem";
    public static final String CATEGORY_CLIENT = "client";

    private static Configuration config;

    private static volatile Map<SurfaceFamily, FamilySettings> families = Collections.emptyMap();

    /**
     * Set when the file on disk could not be trusted, which stops anything being written back.
     *
     * <p>
     * A half-parsed config is the one state where saving is actively destructive: the settings
     * in memory are part the user's and part defaults, and writing that out replaces a file the
     * user can still fix by hand with one they cannot.
     */
    private static volatile boolean poisoned;

    // -- general -----------------------------------------------------------

    /** Master switch. When false the server tracks nothing and clients are told to clear. */
    public static boolean enabled = true;

    /** When true, erosion happens only in the listed dimensions; otherwise everywhere but them. */
    public static boolean dimensionListIsWhitelist = false;

    /** Dimension ids the list above applies to. Empty plus blacklist mode means "everywhere". */
    public static int[] dimensionList = new int[0];

    /** Sneaking adds no wear, so you can cross a lawn without scarring it. */
    public static boolean sneakSuppresses = true;

    /** Whether wearing a surface has a small chance to shed something - a seed, a snowball, flint. */
    public static boolean wearDropsEnabled = true;

    /** That chance, per stage a block wears, for the families that drop anything. */
    public static float wearDropChance = 0.01f;

    /**
     * Whether a mover that has just worn a block must leave it alone for a while before wearing
     * it again. Keeps a mob pacing a pen, or anything jumping on the spot, from grinding one
     * square to bare earth without going anywhere.
     */
    public static boolean retriggerCooldown = true;

    /** Whether the same cooldown is imposed on players. Off, so a player can work a spot freely. */
    public static boolean retriggerCooldownPlayers = false;

    /** Seconds a worn block is left alone before the same mover may wear it again. */
    public static int retriggerSeconds = 10;

    /** How far, horizontally, a mover must get from a block before it may wear it again sooner. */
    public static float retriggerBlocks = 3.0f;

    /** Lowest and highest world Y that will ever be tracked. */
    public static int minY = 0;
    public static int maxY = 255;

    /**
     * Whether a fully worn grass block keeps going as bare, then scuffed, earth. Off reads
     * gentler: paths thin out but never go bald.
     */
    public static boolean grassWearsThroughToDirt = true;

    /**
     * Whether a surface that has worn through its own face goes on as a different material.
     *
     * <p>
     * The single switch over the whole idea, so a pack that wants none of it edits one line rather
     * than ten lists. What each family becomes is families.&lt;name&gt;.wearsThroughTo.
     */
    public static boolean wearThroughToOtherSurfaces = false;

    /** Whether a step of wear costs what the ground is made of rather than what it looks like. */
    public static boolean wearPaceFollowsTheGround = true;

    public static final String DECAY_REAL = "real";
    public static final String DECAY_VISUAL = "visual";
    public static final String DECAY_OFF = "off";

    /**
     * Whether a worn path physically hollows out, and whether you can walk down into it.
     *
     * <p>
     * <b>real</b> gives ruts with collision to match, which is the only mode where the ground
     * you see and the ground you stand on are the same thing. It costs something to get there:
     * the server has to answer for the shape of a block it never changed, so every client must
     * show the same ruts and cannot switch the overlay off while connected.
     *
     * <p>
     * <b>visual</b> renders the ruts but leaves collision alone, so you stand a little above
     * the deepest of them. Nothing is forced on anyone and the per-player toggle stays free.
     *
     * <p>
     * <b>off</b> is flat wear only.
     */
    public static String physicalDecay = DECAY_REAL;

    public static final String UPDATE_OPERATORS = "operators";
    public static final String UPDATE_EVERYONE = "everyone";
    public static final String UPDATE_OFF = "off";

    /**
     * Who is told, as they join, that a newer release is out: <b>operators</b> - whoever can update this
     * copy - <b>everyone</b>, or <b>off</b>, which also means the mod never asks. See
     * {@link com.trmtgtnh.server.UpdateNotice}.
     */
    public static String updateNotice = UPDATE_OPERATORS;

    public static final String RELEASES_RELEVANT = "relevant";
    public static final String RELEASES_ALL = "all";

    /**
     * Which newer releases {@link #updateNotice} tells of (0.9.221; Xep, 2026-10-09): <b>relevant</b>, the default,
     * only one that changes this jar - every edition moves its number with every release - and <b>all</b>, every
     * one. A release that changes nothing here is one line in the server's log either way.
     */
    public static String updateNoticeReleases = RELEASES_RELEVANT;

    /**
     * Scales every wear threshold at once. Above 1.0 erodes faster, below 1.0 slower. The
     * shipped defaults are about eight times slower than upstream TRMT, so 8.0 here restores
     * upstream's pace.
     */
    public static double erosionSpeed = 1.0d;

    /**
     * One dial over the whole mod: how fast everything happens, wearing and recovering alike.
     *
     * <p>
     * 2.0 makes paths form twice as fast <em>and</em> grow back twice as fast; 0.5 slows both by
     * half. Because it moves both sides together it changes the pace of the mod without
     * changing its balance — which is the thing you actually want when a pack feels too eager
     * or too sleepy overall. {@link #erosionSpeed} and {@link #healingRate} still tilt one side
     * against the other on top of it.
     */
    public static double globalSpeed = 1.0d;

    // -- surface detection -------------------------------------------------

    public static boolean surfaceAutoDetect = true;

    /**
     * Whether detection writes what it found into the per-family block lists.
     *
     * <p>
     * Without this, detection is a black box: it decides what erodes and there is nothing in
     * the config to look at or argue with. Writing its findings back turns the lists into a
     * record you can read, prune and freeze — switch {@code autoDetect} off afterwards and the
     * list is the whole story.
     */
    public static boolean writeDetectedSurfaces = true;

    /** Blocks that must never erode, whatever detection concludes. */
    public static String[] surfaceExclude = { "minecraft:mycelium", "minecraft:soul_sand", "minecraft:farmland",
        "etfuturum:farmland", "minecraft:tilled_field", "Natura:GrassSlabDouble", "GalacticraftAmunRa:tile.baseGrass" };

    /**
     * Whether snow and carpet are drawn down with ground that has worn away underneath them.
     *
     * <p>
     * Client-side and cosmetic: nothing about where a player stands changes, because a snow layer
     * has no collision to move and the server is never told anything different.
     */
    public static boolean settleOnWornGround = true;

    /** Whether worn ground keeps the shader material of the block it covers. */
    public static boolean inheritShaderMaterial = true;

    /** Whether a ghost lets the block it covers go on scattering its own ambient particles. */
    public static boolean inheritAmbientParticles = true;

    /** Whether a ghost lets the block it covers go on acting on whatever stands inside it. */
    public static boolean inheritEntityCollision = true;

    /** Whether startup detection looks for ground cover as well as for surfaces. */
    public static boolean groundCoverAutoDetect = true;

    /** Ground cover named by hand, and the list detection writes what it found into. */
    public static String[] groundCoverBlocks = new String[0];

    /** Ground cover that holds its square rather than coming off it, filled in by detection. */
    public static String[] groundCoverHoldsBlocks = new String[0];

    /** Cover that must never be treated as holding, whatever detection concludes. */
    public static String[] groundCoverHoldsExclude = new String[0];

    /** Blocks that must never be treated as ground cover, whatever detection concludes. */
    public static String[] groundCoverExclude = new String[0];

    /**
     * A ceiling of the player's own on wear sprites, counting every one this mod registers; at the default it is above
     * any room an 8192 atlas can have, so it never binds there.
     */
    public static int maxWearSprites = 262144;

    /**
     * Whether the wear may be planned into an atlas past an 8192 square, up to 16384 where the card reports it: the
     * player's word that their card can hold one, off by default because a card that cannot draws every block black
     * without a word. Handed to AtlasPlan as it is read.
     */
    public static boolean largerAtlas;

    /** Upper bound on how many distinct block faces get their own generated wear textures. */
    public static int maxTexturedSurfaces = 1024;

    /**
     * Whether worn ground is forbidden from ever looking cleaner than it did a step ago.
     *
     * <p>
     * Off, the drawn gradation is a blend of how far through the current run a block is with
     * how far through the whole chain it is - and the first of those restarts every time the
     * ground sinks another pixel while the second only rises. The reset outweighs the rise, so
     * at each depth boundary the texture jumps back toward clean at the very moment the block
     * drops lower.
     */
    public static boolean wearNeverStepsBack = true;

    /**
     * Whether a worn grass block's side fringe recedes toward dirt as its top does.
     *
     * <p>
     * On, the green fringe drawn over a grass block's sides is thinned per gradation and the base
     * wall behind it is de-greened to earth, so a path reads as soil on its flanks and not a
     * column of turf. Off restores the static full fringe. Behind a switch because it leans on a
     * second renderer mixin: if a resource pack's connected textures rename a modded grass side
     * away from {@code grass_side} the whole fringe can drop out - a pre-existing limitation this
     * does not cause - and turning it off is the way back.
     */
    public static boolean grassSideWear = true;

    /**
     * How many wear patterns a surface has to spread across neighbouring blocks.
     *
     * <p>
     * Which pixels wear away first, not a turn of the texture. The position already picks one
     * of four, but only two were ever built, so the other two folded onto them and a long path
     * came out visibly striped in a two-block rhythm.
     */
    public static int wearRotations = 4;

    /**
     * How many separate pictures a surface's run of wear is drawn with.
     *
     * <p>
     * Sixteen is one per gradation the engine actually tracks, which is what stops two
     * consecutive steps looking identical. Eight was the old figure and halved them onto each
     * other; anything lower is coarser still, and buys atlas space back if a machine needs it.
     */
    public static int wearGradations = 16;

    /**
     * How far every look's run is pushed away from the shape measured for it.
     *
     * <p>
     * A scale rather than the exponent it used to be, and the difference is the whole of what was
     * wrong: the operators do not saturate in the same direction, so one number applied to all of
     * them suits at most half. One leaves each look on its own, which is what the mod is tuned for.
     */
    public static float wearCurve = 1.0f;

    // -- multipliers -------------------------------------------------------

    public static boolean erodeFromPlayers = true;
    public static boolean erodeFromLeashedMobs = true;

    // ------------------------------------------------------------------
    // Draughts
    // ------------------------------------------------------------------

    /** Whether the draughts do anything at all. The items exist either way; see ModPotions. */
    public static boolean potionsEnabled = true;

    /** Whether a Draught of Lightness exempts its drinker from wearing ground. */
    public static boolean potionLightness = true;

    /** Whether a Draught of the Heavy Foot multiplies its drinker's wear. */
    public static boolean potionHeavyFoot = true;

    /** The effect id Lightness claims, or -1 to take the first free one. */
    public static int lightnessPotionId = -1;

    /** The effect id Heavy-Footedness claims, or -1 to take the first free one. */
    public static int heavyFootPotionId = -1;

    /** How long one Draught of Lightness lasts, in seconds. */
    public static int lightnessSeconds = 180;

    /** How long one Draught of the Heavy Foot lasts, in seconds. */
    public static int heavyFootSeconds = 180;

    /** How much more ground a heavy-footed walker wears than an ordinary one. */
    public static float heavyFootFactor = 4.0f;

    /** How heavily a Draught of Lightness is weighted in the chest pools it joins. */
    public static int lootWeightDraughtLight;

    /** And its opposite, which is rarer because it is the stranger thing to find. */
    public static int lootWeightDraughtHeavy;

    // ------------------------------------------------------------------
    // Weather
    // ------------------------------------------------------------------

    /** Whether the per-family "only mends in the wet" switches do anything. */
    /** Whether snow lying on ground takes the traffic until it has been trodden through. */
    public static boolean snowCovers = true;

    /** How much walking one layer of snow absorbs before it goes. */
    public static float snowCoverWear = 6.0f;

    /** In-game days for an untrodden track through snow to fill back in. 0 never fades. */
    public static float snowCoverFadeDays = 1.0f;

    public static boolean wetHealingEnabled = true;

    /** Seconds of falling weather bought per gradation of recovery. */
    public static float wetHealSecondsPerStage = 3.0f;

    /** Whether ground under a roof is excluded, having nothing falling on it. */
    public static boolean wetHealingNeedsOpenSky = true;

    /** Whether a biome that never rains is excluded rather than counted as wet. */
    public static boolean wetHealingNeedsRainyBiome = true;

    /** Whether a dimension with no weather ignores the rule instead of never recovering. */
    public static boolean wetHealingSkipsWeatherlessDimensions = true;

    public static float multiplierPlayer = 0.5f;
    public static float multiplierMounted = 2.0f;
    public static float multiplierLeashed = 1.5f;

    /**
     * How worn a block's sides look next to its top, as a fraction.
     *
     * <p>
     * A rut is cut from above. Its floor takes the traffic and its walls only get what brushes
     * past them, so the sides stay visibly behind - half as far along, by default.
     */
    /** How far out from the block used a handful of bone meal can reach, in blocks. */
    public static int bonemealRadius = 2;

    /** The most gradations one square can be mended by in a single handful. */
    public static int bonemealMaxSteps = 80;

    /**
     * How far along its run any surface may wear, as a fraction of the whole.
     *
     * <p>
     * A ceiling on the finished look rather than on the rate. Ground still wears at whatever
     * speed it was going to; it simply stops when it gets here, so a world can have paths that
     * scuff without ever hollowing out.
     */
    public static float maxWearFraction = 1.0f;

    /**
     * Whether editing which blocks wear is an operator's privilege.
     *
     * <p>
     * On by default because it writes the server's own config and reloads it, which is not a
     * thing one player should be able to do to everybody else's world. Turn it off on a world
     * where everyone present is trusted.
     */
    public static boolean familyEditorOperatorsOnly = true;

    /**
     * Whether any block of a family mends any worn block of that same family.
     *
     * <p>
     * On by default, which is what makes a modded lawn mendable with a different modded turf
     * without anyone listing every pair by hand: the two are the same family, so one pays for
     * the other. The per-family repairBlocks list still adds the cross-family cases on top - grass
     * mended with earth - and is the way to name something outside the family. Read by bone meal,
     * the chunk tamper and the golem; the hand tamper pays in what the ground drops and never
     * looks at it.
     */
    public static boolean repairAnyInFamily = true;

    /**
     * Whether putting ground back is worth experience.
     *
     * <p>
     * Only ground that cost something to put back is. Wearing ground in is free, so a free way
     * back out would make the round trip a farm with nothing to slow it but the tool.
     */
    public static boolean xpFromHealing = true;

    /** Experience per gradation of wear mended and paid for, before any enchantment on the tool. */
    public static float xpPerGradation = 0.25f;

    /** How much each level of an experience-boosting enchantment adds, as a fraction. */
    public static float xpBoostPerLevel = 0.25f;

    /** Name fragments that mark an enchantment as repairing a tool from experience. */
    public static String[] mendingEnchantments = { "mending", "repair" };

    /** Name fragments that mark an enchantment as increasing experience gained. */
    public static String[] xpBoostEnchantments = { "xpboost", "experienceboost", "xp" };

    /** Whether a block standing on worn ground flattens it rather than hiding the wear. */
    public static boolean flattenWearUnderBlocks = true;

    /** Whether an explosion scours the ground around it as well as blowing a hole in it. */
    public static boolean explosionWear = true;

    /** How hard the ground is scoured at the very middle of a blast. */
    public static float explosionStrength = 140.0f;

    /** How far from a blast the scouring can reach, whatever the charge claims. */
    public static float explosionMaxRadius = 64.0f;

    /** How far the scouring reaches compared with the blast's own radius. */
    public static float explosionReach = 2.0f;

    /** The blast size the strength figure is quoted against. Vanilla TNT. */
    public static final float REFERENCE_BLAST = 4.0f;

    /** Ceiling on how many positions one blast may touch, whatever its radius works out to. */
    public static int explosionMaxPositions = 262144;

    /** Whether something landing hard scuffs the ground it lands on. */
    public static boolean fallWear = true;

    /** How hard the ground is marked by a landing from the reference height. */
    public static float fallStrength = 6.0f;

    /** Below this a landing marks nothing. */
    public static float fallMinDistance = 3.0f;

    /** The drop the strength figure is quoted against. */
    public static float fallReferenceDistance = 10.0f;

    /** How far a landing's scuff can spread, however far the thing fell. */
    public static float fallMaxRadius = 3.0f;

    /**
     * Whether mending worn ground costs a block as well as the bone meal.
     *
     * <p>
     * Free repair is fine while a path is decoration and wrong the moment it is terrain somebody
     * has to maintain: a handful of bone meal undoes a season of traffic and the ground stops
     * meaning anything. A block of each kind of ground mended puts the repair on the same footing
     * as laying the ground in the first place - and grass takes earth, because grass is what the
     * world grows and earth is what a player digs by the stack.
     */
    public static boolean bonemealCostsABlock = true;

    /**
     * Scales every tamper's durability against its tier's own figure.
     *
     * <p>
     * Read when the items are registered, not when one is used, so changing it is felt by the
     * next tamper made rather than silently repairing or breaking every one already in a world.
     */
    public static float tamperDurabilityScale = 1.0f;

    /** How far out of the clicked block a new chunk tamper reaches. */
    public static int chunkTamperDefaultReach = 2;

    /** The furthest a chunk tamper may be set to reach. */
    public static int chunkTamperMaxReach = 7;

    /** The most gradations one chunk tamper gesture may move a position. */
    public static int chunkTamperMaxSteps = 8;

    /**
     * How many gradations of wear one block of material pays a chunk tamper to put back.
     *
     * <p>
     * Counted in gradations rather than positions since 0.9.204, so a rut filled eight deep costs
     * what eight shallow ones would. It was chunkTamperPositionsPerBlock before that, and a figure
     * set under the old name is carried across when the file is read rather than quietly reset.
     */
    public static int chunkTamperGradationsPerBlock = 4;

    /**
     * The pack personality switch - GregTech-hardened recipes, the compressed-block golem build, a
     * Wayfarer built around netherite where the pack can make a netherite chunk tamper, tiered costs and
     * the questbook chapter. Off, those fall back to pure vanilla-Forge behaviour.
     *
     * <p>
     * Not the master switch for every companion-mod integration, which is what this called itself from
     * the day it was written, before there were trophies or chest finds for it to govern. Trophies answer
     * to {@link #trophies} and the achievements they are earned through, and chest finds to
     * {@link #lootFinds} and their own weights, and neither asks this: a trophy and a find change nothing a
     * pack costs, so neither has a plainer version to fall back to, and a pack that turned this off for
     * plain recipes has not thereby asked to lose them. The map and tooltip readouts, which change no
     * gameplay, are not reached by it either.
     *
     * <p>
     * Defaults to whether GregTech is present, decided when the file is first written and kept after
     * that, so adding GregTech to a pack that has already run leaves this off until somebody turns it
     * on. Can be forced either way.
     */
    public static boolean gtnhEnhanced = true;

    /** Whether the reinforcement feature - the enchantment, the mode, the blast-proofing - exists. */
    public static boolean reinforceEnabled = true;

    /** The enchantment id, or -1 to take the first free slot at startup. */
    public static int reinforceEnchantId = -1;

    /** The most times a block may be reinforced. Hard-capped at 3 by the two bits it is stored in. */
    public static int reinforceMaxLevel = 3;

    /** How much of an extra lifetime each reinforcement level adds before a block wears a step. */
    public static double reinforceWearFactor = 1.0d;

    /**
     * What one reinforcement costs, tried in order: a fluid, then an item, first the player has.
     *
     * <p>
     * Four entries for what is really two answers, because concrete is not one thing. A pack that
     * registers it as a fluid is named by the first, and that is the tidiest form since the fluid
     * registry then hands the container back. GT New Horizons does not: its concrete comes in two
     * buckets that are plain items, an iron one and a single-use clay one, and those are named
     * outright. Obsidian is last and is what plain Minecraft pays with. Nothing here has to exist -
     * an entry naming an item no mod in the pack provides is skipped.
     */
    public static String[] reinforceMaterials = { "fluid:wet.concrete", "dreamcraft:dreamcraft_Concrete_bucket",
        "dreamcraft:clayBucketConcrete", "minecraft:obsidian" };

    /**
     * The blast size each reinforcement level below the top can withstand, added to the block's own
     * resistance. The top level is always fully blast-proof whatever is written here.
     */
    public static float[] reinforceCeiling = { 8.0f, 30.0f };

    /**
     * The starting items, each off by default.
     *
     * <p>
     * Off because a mod that puts things in an inventory uninvited is one somebody will be
     * annoyed by. Turning one on reaches everybody who has never had that item in this save, not
     * only players who spawn afterwards - see {@code SpawnGrants}.
     */
    public static boolean spawnWithTamper = false;

    public static boolean spawnWithChunkTamper = false;

    public static boolean spawnWithWayfarer = false;

    public static boolean spawnWithGuideMk1 = false;

    public static boolean spawnWithGuideMk2 = false;

    public static boolean spawnWithGuideCommands = false;

    public static boolean spawnWithGuideGolem = false;

    /**
     * Whether the mod's things turn up in world-generated chests at all.
     *
     * <p>
     * The one switch over every find. Each find also answers to a weight below, where 0 takes out
     * whatever that weight covers - and three cover a group: the three unlock books share one, the
     * Mk II, Commands and Golem guides another, and every ordinary golem upgrade a third, so no weight
     * takes out just one of those. Each find answers as well to the switch of the feature it belongs
     * to - the golem and its upgrades, the draughts, each unlock enchantment, which is the only way to
     * lose a single unlock book - but never to {@link #gtnhEnhanced}: every find is one of this mod's own items, or
     * vanilla's enchanted book carrying one of its enchantments, and none needs anything a GregTech
     * pack ships.
     *
     * <p>
     * Read once, when {@code ModLoot} files the finds at the end of start-up; /trmt reload changes this
     * field and nothing in any chest pool.
     */
    public static boolean lootFinds = true;

    /**
     * How likely each thing is to be found, as a weight in the chest pool it is added to.
     *
     * <p>
     * Weights, not chances: a pool totals in the hundreds, so a one or a two here is a fraction of
     * a percent and every other item in that chest keeps very nearly the odds it had. Zero leaves
     * that thing out of the world entirely.
     */
    public static int lootWeightTamper = 3;

    public static int lootWeightChunkTamper = 1;

    public static int lootWeightWayfarer = 1;

    public static int lootWeightGuide = 5;

    public static int lootWeightGuideRare = 2;

    public static int lootWeightEnchantBook = 1;

    public static int lootWeightUpgrade = 2;

    public static int lootWeightOmni = 1;

    public static int lootWeightGolemEgg = 1;

    /** Whether the Golem of Ways exists at all - buildable, spawnable, and able to work. */
    public static boolean golemEnabled = true;

    /** Whether upgrades exist. Off, the golem has no upgrade slot at all. */
    public static boolean golemUpgrades = true;

    /** Whether the golem picks up what its work drops and keeps it. */
    public static boolean golemPickup = true;

    /**
     * Whether a golem standing about shows you which sort of standing about it is.
     *
     * <p>
     * Switchable because it is a visible change to a mob people already know, and because the way
     * back is exact: with it off the figure these are drawn from is zero everywhere and the golem
     * behaves precisely as it did before they existed, rehearsal and all.
     */
    public static boolean golemIdlePoses = true;

    /** How far it works from its anchor without the range upgrade. */
    public static int golemBaseRadius = 16;

    /** The furthest it can ever be told to work. */
    public static int golemMaxRadius = 64;

    /** Blocks one purchase of a golem's mending costs; GolemWork.strokeLedger says how much mending a purchase buys. */
    public static int golemBlockCost = 2;

    /** Whether an armed golem defends its ground, and whether an unarmed one runs from it. */
    public static boolean golemCombat = true;

    /** What one of its blows takes off, in half-hearts. */
    public static float golemAttackDamage = 4.0f;

    /** How far a fully worn square darkens on a map, as a fraction of its own color. */
    /** How far a fully worn square is pulled toward the desire-path color on a map. 0 is off. */
    public static float desirePathHighlight = 0f;

    /** That color as the file writes it, kept so the setting round-trips unchanged. */
    public static String desirePathColor = "#AA44CC";

    /** The parsed form, which is what the map path actually reads. */
    public static int desirePathRgb = 0xAA44CC;

    /** Whether worn ground reports its wear through the block tint, for maps that read the world. */
    public static boolean mapWearThroughTint = true;

    /**
     * Whether a map draws a worn square in a color that says how worn it is. Read by the color handler this mod hands
     * to JourneyMap, and by the wear shade a world-reading map is given through the block tint, so switching it off
     * takes the wear off both. A modded turf's tint correction for such a map is not wear and is not affected.
     */
    public static boolean mapTracksWear = true;

    public static float mapWearDarkening = 0.62f;

    /** Ticks between the points of health a stout golem puts back, or 0 for none. */
    public static int golemStoutHealTicks = 24000;

    /** How long a golem's list of work stands before it looks again, in seconds. */
    public static int golemTargetMemory = 900;

    /** The least time between two full sweeps of a golem's ground, in seconds. */
    public static int golemScanCooldown = 10;

    /** How many rings of equally distant work a golem will choose between. */
    public static int golemTargetLayers = 3;

    /**
     * Whether a golem will eat reinforcing material thrown to it and lay it in its round.
     *
     * <p>
     * Rides on {@link #golemPickup} as well: material is noticed by the sweep that picks up what
     * the work shakes loose, so a golem told not to pick things up never sees it.
     */
    public static boolean golemReinforces = true;

    /** How far from itself a golem can reach to work a square. */
    public static int golemWorkReach = 4;

    /** How far past its work radius it will step, to reach a fight or to get away from one. */
    public static int golemGuardRange = 8;

    /** Whether the settled upgrade may reach into the containers around a golem's anchor. */
    public static boolean golemAreaStorage = true;

    /** How far past its own round a settled golem reaches for a container. */
    public static int golemStoreReach = 8;

    /** How far either side of an order a green golem drives the ground, in gradations. */
    public static int golemGreenBand = 8;

    /** The chance a golem sheds something each time it takes a square down a physical level. */
    public static float golemWearDropChance = 0.5f;

    /** The least ground a golem will ever be allowed to move over, whatever its round is set to. */
    public static int golemRoamFloor = 16;

    /** Whether the nine parts bind into an unstable All Ways before they bind into a settled one. */
    public static boolean golemUnstableOmni = true;

    /** How long an unstable golem keeps one set of upgrades, in ticks. */
    public static int golemUnstablePeriod = 1200;

    /** The upper body: packed stone. Tried in order; anything modded is skipped when not enhanced. */
    public static String[] golemBodyTop = { "ExtraUtilities:cobblestone_compressed:3", "minecraft:stonebrick" };

    /** The lower body: packed earth. */
    public static String[] golemBodyBottom = { "ExtraUtilities:cobblestone_compressed:11", "minecraft:dirt" };

    /** The arms: lighter earth. */
    public static String[] golemArms = { "ExtraUtilities:cobblestone_compressed:10", "minecraft:dirt" };

    /**
     * What the demonstration command puts its sample items in, tried in order.
     *
     * <p>
     * The mod adds more things than a vanilla chest holds once every tamper grade is counted, so
     * the first roomy container the pack can supply wins and a plain chest is doubled up. Anything
     * outside vanilla is skipped when the GTNH enhancements are off.
     */
    public static String[] demoContainer = { "Avaritia:Infinity_Chest", "IronChest:BlockIronChest:9",
        "minecraft:chest" };

    /**
     * The chest categories the Wayfarer may be found in.
     *
     * <p>
     * Its own setting because it is the one find worth placing deliberately. This version of the
     * game has no End structure carrying a loot category, so the default is the stronghold library -
     * the rarest vanilla pool, and the room the End portal stands in.
     *
     * <p>
     * A name is only a string a generator asks for, and Forge makes an empty pool for one nobody uses
     * rather than refusing it, so a wrong name would take the Wayfarer and never give it back.
     * ModLoot.settleWayfarer looks as a server is about to start, warns about each name nothing else
     * fills, and files the Wayfarer in the library as well when none of them is real. A name written
     * twice is filed twice. Taken up once, as the game starts.
     */
    public static String[] lootWayfarerCategories = { "strongholdLibrary" };

    /** Whether the mod's own achievement page exists. */
    public static boolean achievements = true;

    /**
     * Whether trophy definitions are written for Amazing Trophies.
     *
     * <p>
     * Defaults to whether that mod is installed, so a pack that has it gets trophies without
     * being asked and a pack that does not never writes a file. The mod itself is never loaded
     * either way - all this does is put JSON in the folder it already reads. The default is decided
     * when the file is first written and kept after that, so a pack that ran once before Amazing
     * Trophies was added has this off until somebody turns it on.
     *
     * <p>
     * {@link #gtnhEnhanced} is deliberately not asked, and its comment says so. Trophies need this and
     * the {@link #achievements} they are earned through, and each one needs the feature its
     * achievement is for. Off, like achievements off, stops the writing and takes nothing back: a
     * folder an earlier launch wrote is left where it stands, and Amazing Trophies goes on reading it
     * until it is deleted.
     */
    public static boolean trophies = true;

    /**
     * Whether a quest line is written into the pack's questbook for BetterQuesting.
     *
     * <p>
     * Purely additive - two new folders and one appended line in the tab order, with no
     * existing quest file so much as opened. Nothing is loaded into a running world either;
     * picking the line up is a command the player runs when they want it.
     */
    public static boolean questbook = true;

    /**
     * Whether a player who can do something about it is told that this world has no chapter yet.
     *
     * <p>
     * Separate from the writing, because the two failures are different: a pack that has decided
     * against the chapter wants the folder left alone, and a server that has decided against being
     * reminded still wants the folder written for the next world it makes.
     */
    public static boolean questbookNotice = true;

    /**
     * How long, in real seconds, a broken block's record is held in case the same block comes back.
     *
     * <p>
     * Real seconds rather than in-game ones, because this is about the person holding the pickaxe
     * noticing their mistake, not about the world's clock. 0 turns it off and a break forgets at
     * once, which is how it behaved before.
     */
    public static int graceSeconds = 900;

    /** Whether the spawn-ward feature - its enchantment, its tamper mode, its spawn-barring - exists. */
    public static boolean wardEnabled = true;

    /** The ward enchantment id, or -1 to take the first free slot at startup. */
    public static int wardEnchantId = -1;

    /** How many of the material barring one category from one block costs; the Wayfarer pays half. */
    public static int wardCostCount = 2;

    /** What barring hostiles from a block costs, tried in order, first the player has enough of. */
    public static String[] wardHostileMaterials = { "minecraft:ender_eye" };

    /** What barring passives from a block costs, tried in order, first the player has enough of. */
    public static String[] wardPassiveMaterials = { "minecraft:golden_carrot" };

    /** Whether path lighting - its enchantment, its tamper mode, the glow itself - exists. */
    public static boolean lightEnabled = true;

    /** The path-light enchantment id, or -1 to take the first free slot at startup. */
    public static int lightEnchantId = -1;

    /** How brightly a lit block glows, 1 to 15. A torch is 14. */
    public static int lightLevel = 12;

    /** How many of the material lighting one block costs; the Wayfarer pays half. */
    public static int lightCostCount = 1;

    /** What lighting a block costs, tried in order, first the player has enough of. */
    public static String[] lightMaterials = { "minecraft:glowstone_dust" };

    /**
     * The grades a tamper can be made at, as {@code name:oreName:uses}.
     *
     * <p>
     * A list rather than a sweep of the ore dictionary, deliberately. GregTech alone registers
     * some nine hundred materials, and a creative tab holding nine hundred tampers is not a
     * feature - so the pack's own likely materials are named here and anything absent is simply
     * skipped. Nothing in this list reaches the save's id map: the grade rides in the stack.
     *
     * <p>
     * The Wayfarer's tamper is built around a chunk tamper of one of these grades - netherite where
     * the enhancements are on, then diamond, and failing both the one with the most uses written
     * against it - so taking the diamond line out moves it onto another metal rather than leaving it
     * uncraftable. Worked out once as the game starts, like every grade here; see WayfarerCore.
     */
    public static String[] tamperGrades = { "iron:ingotIron:512:1", "gold:ingotGold:256:3", "diamond:gemDiamond:2048:2",
        "netherite:ingotNetherite:4096:3", "bronze:ingotBronze:768:1", "steel:ingotSteel:1536:2",
        "aluminium:ingotAluminium:2048:2", "stainlesssteel:ingotStainlessSteel:4096:3", "titanium:ingotTitanium:8192:3",
        "tungstensteel:ingotTungstenSteel:16384:4", "neutronium:ingotNeutronium:32767:4", "copper:ingotCopper:384",
        "tin:ingotTin:300", "lead:ingotLead:384", "nickel:ingotNickel:640", "zinc:ingotZinc:384",
        "silver:ingotSilver:512", "electrum:ingotElectrum:480", "invar:ingotInvar:900",
        "cupronickel:ingotCupronickel:640", "brass:ingotBrass:512", "wroughtiron:ingotWroughtIron:512",
        "tungsten:ingotTungsten:2600", "cobalt:ingotCobalt:1100", "chrome:ingotChrome:900",
        "nichrome:ingotNichrome:1200", "kanthal:ingotKanthal:1400", "tungstencarbide:ingotTungstenCarbide:9000",
        "hssg:ingotHSSG:5120", "hsse:ingotHSSE:7000", "hsss:ingotHSSS:8000", "damascussteel:ingotDamascusSteel:2600",
        "osmium:ingotOsmium:3400", "iridium:ingotIridium:12000", "platinum:ingotPlatinum:2000",
        "palladium:ingotPalladium:2200", "manganese:ingotManganese:512", "tantalum:ingotTantalum:1800",
        "molybdenum:ingotMolybdenum:1600", "vanadiumsteel:ingotVanadiumSteel:3200", "darksteel:ingotDarkSteel:2400",
        "redsteel:ingotRedSteel:3000", "bluesteel:ingotBlueSteel:3600" };

    /**
     * What one right-click patch mend costs, in blocks of what the ground drops, once for each kind
     * of ground in the patch.
     */
    public static int tamperPatchCost = 2;

    /** What one single-square mend costs, in blocks of what the ground drops, taken once the gradation is back. */
    public static int tamperMendCost = 1;

    /** The least time between two left-click gestures on the same square, in ticks. */
    public static int tamperCooldownTicks = 10;

    /** Whether a tamper can deliberately wear ground in, rather than only mend it. */
    public static boolean tamperCanWear = true;

    /** Whether a tamper can pin a square against wear and healing. */
    public static boolean tamperCanPin = true;

    public static float sideWearFraction = 0.5f;

    /**
     * How much of a block's look comes from its total wear rather than its current gradation.
     *
     * <p>
     * Gradations restart every time the ground drops a pixel, so on their own a deep rut reads as
     * freshly cut. Carrying some of the overall figure into what is drawn means each run still
     * wears through visibly while every run starts further along than the last.
     */
    public static float wearCarriesWithDepth = 0.9f;

    /**
     * Which mobs wear the ground, and how hard, by entity name.
     *
     * <p>
     * Villagers only, out of the box. They are the one crowd that walks the same short route
     * between the same few doors day after day, which is exactly how a real path gets made;
     * almost everything else in a pack this size wanders, and a wandering herd carving roads
     * across a continent is not a feature. Anything here is in addition to players and to
     * anything on a lead.
     */
    public static Map<String, Float> mobMultipliers = Collections.emptyMap();

    /** Wear bled onto the block ahead of the walker. */
    public static float bleedFront = 0.2f;

    /** Wear bled onto each flanking block. This is what gives a path its width. */
    public static float bleedSide = 0.5f;

    // -- healing -----------------------------------------------------------

    public static boolean healingEnabled = true;

    /**
     * Whether the clock healing is measured against stops while the server is empty.
     *
     * <p>
     * On by default, and only ever does anything on a server that can be empty - a single-player
     * world is never running without its one player in it.
     */
    public static boolean pauseHealingWhenEmpty = true;

    /**
     * How fast ground recovers. Higher heals faster, in the same direction as every other rate
     * here — the old {@code healingSpeed} key meant the opposite and was renamed rather than
     * silently inverted under anyone's existing config.
     */
    public static double healingRate = 1.0d;

    /**
     * Fraction of the current stage's threshold that bleeds off per untouched in-game day.
     * Without this a route crossed twice a year would still eventually become a path.
     */
    public static double wearDecayPerDay = 0.05d;

    // -- trampling ---------------------------------------------------------

    /**
     * The two trampling switches: each keeps a fading tally against a plant in the way or a leaf
     * underfoot and breaks a real block when it runs out, so both ship off. They are not the only
     * rules that edit the world - {@link #groundWearsAway}, {@link #groundCoverBreaks} and
     * {@link #snowCovers} do too, and ship on.
     */
    public static boolean trampleVegetation = false;
    public static boolean trampleLeaves = false;
    public static float vegetationDropChance = 0.2f;
    public static float leavesDropChance = 0.1f;

    /**
     * Whether ground worn the whole way through is removed rather than simply stopping.
     *
     * <p>
     * The one rule here that takes a block nobody put there and nobody broke, which is why it is
     * hedged: a square must be worn a whole threshold past the end of its run, and a pin, a
     * reinforcement, a ward or a glow each veto it outright.
     */
    public static boolean groundWearsAway = true;

    /**
     * Whether the plants standing in a route go down when the ground under them gives way.
     *
     * <p>
     * On, unlike the vegetation and leaves switches, and the difference is what it waits for. Those
     * count crossings of the plant or the leaf itself, so a flower can go on ground that still looks
     * untouched; this one only fires where the ground has visibly given way - a level dropped, while
     * {@link #groundCoverOnSink} is on - and so never anywhere a route is not plainly forming.
     */
    public static boolean groundCoverBreaks = true;

    /**
     * Whether a planted thing holds the ground it is standing on rather than being knocked off it.
     *
     * <p>
     * The third answer to what a forming path does to what is standing in it, and the one that
     * does not have to choose between destroying somebody's orchard and pretending the ground
     * under it is untouched. The wear is still counted; it simply is not expressed until there
     * is nothing left holding it back.
     */
    public static boolean groundCoverHolds = true;

    /**
     * Whether the ground has to physically drop a level, or whether any gradation will do.
     *
     * <p>
     * A family's whole run is eighty gradations and eight sunk pixels, so this is the difference
     * between a plant that survives a tenth of what one that does not. And it is the honest moment:
     * a gradation is a change of shade, where a level is the ground going out from under the thing
     * standing on it.
     */
    public static boolean groundCoverOnSink = true;

    /**
     * Whether plants standing on hoed ground go down with everything else.
     *
     * <p>
     * Off, because tilled soil is the one honest signal that somebody planted this. Asking the
     * plant what it is cannot separate a field from a meadow - Natura's cotton is a crop by
     * Forge's own reckoning and also grows wild on plain grass - but asking what it was planted
     * in can.
     */
    public static boolean groundCoverOnTilled = false;

    /** Chance a plant taken down with the ground still drops its item. */
    public static float groundCoverDropChance = 0.5f;

    // -- performance -------------------------------------------------------

    public static int sweepIntervalTicks = 200;
    public static int sweepChunksPerPass = 64;
    public static int maxEntriesPerChunk = 3072;
    public static int movementSampleTicks = 2;

    // -- client ------------------------------------------------------------

    /** Client only. When false you see plain terrain; nobody else is affected. */
    public static boolean showErosion = true;

    /**
     * Set for the session when a server has ruts you can walk down into.
     *
     * <p>
     * Held apart from {@link #showErosion} rather than overwriting it, so a player's own
     * preference survives a visit to a server that does not allow it and comes back the moment
     * they leave. Never written to disk.
     */
    public static boolean overlayForced;

    /** Overlays further away than this many chunks are not painted. */
    public static int overlayDistanceChunks = 12;

    /**
     * Build wear textures per surface, so a Biomes O' Plenty grass or a Twilight Forest dirt
     * wears in its own colors instead of vanilla's.
     */
    public static boolean perSurfaceTextures = true;

    /** Overlay positions applied to the client world per tick, to keep frame spikes out. */
    public static int overlayApplyBudget = 512;

    /** Whether the carved shell of a layered block is lifted clear of the layer drawn inside it. */
    public static boolean liftLayeredBlockShell;

    /** How far, in blocks, that shell is lifted. */
    public static double layeredBlockShellLift = 0.002d;

    /**
     * What is drawn behind a block whose own texture is cut away to show a second layer.
     *
     * <p>
     * Entries are {@code modid:block[:meta]=texture}. Read once per resource reload and never at
     * render time: what it decides is baked into the wear sprite while the atlas is being built.
     */
    public static String[] innerLayerTextures = { "chisel:lavastone=lava_still", "chisel:waterstone=water_still" };

    /** Whether that layer moves, where the texture named for it is one that moves. */
    public static boolean animateInnerLayers = true;

    /** Whether the holes in a worn shell are left see-through rather than filled in. */
    public static boolean seeThroughInnerLayers;

    /** The most memory, in megabytes, moving layers may hold for the session. */
    public static int innerLayerAnimationBudgetMb = 64;

    /** How many moving layers may be redrawn in one tick. */
    public static int innerLayerUploadsPerTick = 256;

    /**
     * Whether a block standing on worn ground hides the wear underneath it.
     *
     * <p>
     * Only the drawing stops. The wear itself is the server's and is never touched, so lifting
     * the block off shows exactly the path that was there - this is a thing you cannot pave over
     * to repair, only a thing you can stop looking at.
     */
    public static boolean hideWearUnderBlocks = true;

    private TrmtConfig() {}

    public static Configuration raw() {
        return config;
    }

    /**
     * The surfaces a family wears through into, as families rather than as names.
     *
     * <p>
     * Resolved here because the answer depends on two switches besides the list itself, and
     * because a name that no longer matches a family has to be dropped somewhere quiet: a pack
     * that renames one in its file should get the rest of its list back rather than an exception
     * at chunk load. An unresolvable name is said once in the log, because a run that silently
     * stops one material short is the sort of thing nobody thinks to look for.
     *
     * <p>
     * The two switches divide the families between them rather than stacking on the same ones,
     * and that is the whole of what turf needs. Wearing through into another material is a choice
     * only where there is something else to do instead: stone can break into cobble or it can go
     * on being stone, and the wholesale switch is off out of the box so it goes on being stone.
     * Turf has no such choice. It is a face rather than a substance - no depth of its own, no
     * pixels to sink through - so the earth beneath it is not an alternative to its rut, it is the
     * only rut it can have. Held to the wholesale switch as well, grass answered a setting about
     * other materials by losing its entire run: sixteen gradations of thinning green on a default
     * config and then nothing at all, no matter how much anybody walked on it. Its own older
     * setting says whether a bald patch appears, and says it alone.
     *
     * @param honourSwitches false to get the list as it is written, whatever the switches say.
     *                       That is what recognises ground recorded under a setting somebody has
     *                       since turned off, which would otherwise be thrown away as though the
     *                       block underneath it had been replaced.
     */
    public static java.util.List<SurfaceFamily> wearsThroughTo(SurfaceFamily family, boolean honourSwitches) {
        FamilySettings settings = family(family);
        if (settings == null || settings.wearsThroughTo == null) return Collections.emptyList();
        if (honourSwitches) {
            if (family == SurfaceFamily.GRASS) {
                if (!grassWearsThroughToDirt) return Collections.emptyList();
            } else if (!wearThroughToOtherSurfaces) {
                return Collections.emptyList();
            }
        }

        List<SurfaceFamily> out = new ArrayList<SurfaceFamily>(settings.wearsThroughTo.length);
        for (String raw : settings.wearsThroughTo) {
            if (raw == null) continue;
            String name = raw.trim();
            if (name.isEmpty()) continue;
            SurfaceFamily named = SurfaceFamily.byKey(name);
            if (named == null) {
                warnUnknownSuccessor(family, name);
                continue;
            }
            // Its own name in its own list describes a run that never leaves home, which is what
            // an empty list already says; honouring it would only put a second run of the same
            // pictures through the atlas. A family with no wear stages has no pictures at all.
            if (named == family || !named.staged) continue;
            if (honourSwitches) {
                FamilySettings target = family(named);
                if (target == null || !target.enabled) continue;
            }
            if (!out.contains(named)) out.add(named);
        }
        return out;
    }

    /** Names nobody can resolve, said once each rather than once per chain rebuild. */
    private static final Set<String> WARNED_SUCCESSORS = new TreeSet<String>();

    private static void warnUnknownSuccessor(SurfaceFamily family, String name) {
        String key = family.key() + "/" + name;
        synchronized (WARNED_SUCCESSORS) {
            if (!WARNED_SUCCESSORS.add(key)) return;
        }
        Trmt.LOG.warn(
            "families.{}.wearsThroughTo names '{}', which is not a surface family - skipping it. "
                + "The run simply stops one material short; nothing is lost.",
            family.key(),
            name);
    }

    public static FamilySettings family(SurfaceFamily family) {
        return families.get(family);
    }

    public static Map<SurfaceFamily, FamilySettings> allFamilies() {
        return families;
    }

    /** The file the settings were loaded from, so other files of this mod's can sit beside it. */
    private static File loadedFrom;

    /** The folder the settings live in - the config folder - where the update notice keeps who has quieted it. */
    public static File folder() {
        File file = loadedFrom;
        return file == null ? new File("config")
            : file.getAbsoluteFile()
                .getParentFile();
    }

    public static void load(File file) {
        loadedFrom = file;
        config = new Configuration(file);
        config.load();
        read();
        save();
    }

    /**
     * Re-reads the file from disk, then everything derived from it.
     *
     * <p>
     * {@link #read()} on its own only re-examines the copy already parsed into memory, which
     * is what the config GUI wants — it has just written to that copy — but not what
     * {@code /trmt reload} wants, where the point is to pick up an edit made to the file. Note
     * that stage counts and texture settings still need a resource reload to show, because the
     * wear textures are generated once when the block atlas is stitched.
     */
    public static boolean reloadFromDisk() {
        if (config == null) return false;

        // What the settings are known to contain right now. Forge's parser merges what it finds
        // over what is already in memory, so a file that lost its tail is survivable — the old
        // values are still there and saving puts them back. The dangerous case is the file being
        // momentarily absent, which is how some editors save: the parser then clears every
        // category, creates an empty file and parses that quite happily, after which a re-read
        // would rebuild the lot at defaults and a save would write those over the real settings.
        // A key that existed a moment ago and does not now is what that looks like from here.
        Set<String> before = keysOnRecord();

        try {
            config.load();
        } catch (RuntimeException malformed) {
            poisoned = true;
            Trmt.error("Config file could not be parsed, keeping the settings already loaded", malformed);
            return false;
        }

        Set<String> after = keysOnRecord();
        if (!before.isEmpty() && !after.containsAll(before)) {
            poisoned = true;
            Trmt.error(
                "Config file is missing {} settings it had a moment ago, so it looks truncated. Keeping the settings already loaded and writing nothing back.",
                Integer.valueOf(before.size() - countPresent(before, after)));
            return false;
        }

        poisoned = false;
        read();
        save();
        return true;
    }

    /** Whether the file on disk last failed to read, in which case nothing may be written. */
    public static boolean isPoisoned() {
        return poisoned;
    }

    /** Every setting the parsed config currently holds, as "category.key". */
    private static Set<String> keysOnRecord() {
        Set<String> keys = new java.util.HashSet<String>();
        for (String name : config.getCategoryNames()) {
            ConfigCategory category = config.getCategory(name);
            for (String key : category.keySet()) {
                keys.add(name + '.' + key);
            }
        }
        return keys;
    }

    private static int countPresent(Set<String> wanted, Set<String> found) {
        int present = 0;
        for (String key : wanted) {
            if (found.contains(key)) present++;
        }
        return present;
    }

    /**
     * Re-reads every value, puts back anything a server owns for the visit, rebuilds the wear
     * chains, and writes anything new back out.
     */
    public static void read() {
        // A failed parse leaves the settings part read and part whatever survived, which is not
        // a state worth adopting. Everything keeps the values it already had until the file is
        // fixed and re-read; the alternative is a world quietly running on half a config.
        if (poisoned) {
            Trmt.LOG.warn("Not applying settings: the config file failed to read, fix it and use /trmt reload");
            return;
        }
        readGeneral();
        readSurfaces();
        readMultipliers();
        readPotions();
        readWeather();
        readHealing();
        readTrampling();
        readPerformance();
        readClient();
        families = Collections.unmodifiableMap(FamilySettings.readAll(config));
        // Between the map being replaced and anything being built out of it, and it has to be
        // both. A server's geometry is stamped onto these objects and never written to the file,
        // so reading the file back is exactly what loses it - and the two lines below compile the
        // wear chains and the collision flag from whatever the map holds when they run. Anywhere
        // earlier writes onto objects about to be discarded; anywhere later builds the chains
        // twice, once from the wrong numbers. Inert on a server and in a world of one's own.
        // Before the server's own numbers are put back, so that a host owning the geometry has
        // the last word over any rung a visitor has chosen for themselves.
        Presets.applyAll(config);
        // Last, after every reader that gives a setting its comment. Forge does not read comments
        // back out of a file, so a setting loaded from disk has none until the code declares it again -
        // and the audit ran at the end of the client heading, before the family headings and the
        // presets had been declared. On any start with a config file already there, which is every
        // restart of a real server, it warned that some two hundred and fifty settings had no
        // explanation, every one of which did.
        auditComments();
        ServerRules.reassert();
        ErosionChain.rebuild();
        PhysicalDecay.refresh();
    }

    /**
     * One starting-item switch, all worded the same way.
     *
     * <p>
     * The wording matters more than usual here, because the behaviour is not what the obvious
     * reading of "spawn with" would be: it reaches players who are already in the world.
     */
    private static boolean spawnItem(String name, String what) {
        return config.getBoolean(
            name,
            CATEGORY_SPAWN_ITEMS,
            false,
            "Whether every player is given " + what
                + ". Not only on a fresh spawn: anyone who has never been given one in this save gets it the next time they log in, so turning this on reaches people already playing. Nobody is ever given a second.");
    }

    /** One loot weight, all worded the same way. Zero leaves that thing out of the world. */
    private static int lootWeight(String name, int def, String what) {
        return config.getInt(
            name,
            CATEGORY_LOOT,
            def,
            0,
            100,
            "How heavily " + what
                + " is weighted in the chest pools it is added to. A pool totals in the hundreds, so these numbers are deliberately tiny. 0 means it is never generated. Read once, as the game starts: a change takes a restart, and /trmt reload alone changes no chest pool.");
    }

    private static void readGeneral() {
        enabled = config.getBoolean(
            "enabled",
            Configuration.CATEGORY_GENERAL,
            true,
            "Master switch. When false the server stops accumulating wear and tells clients to clear their overlays. Stored data is kept, so turning this back on restores every path exactly as it was.");
        Property enhanced = config.get(
            CATEGORY_INTEGRATION,
            "gtnhEnhanced",
            cpw.mods.fml.common.Loader.isModLoaded("gregtech"),
            "The pack personality switch. On, the mod tunes itself to the companion mods a GregTech pack ships: harder recipes, the compressed-block golem build, a Wayfarer built around a netherite chunk tamper where the pack can make one, tiered material costs, and the quest chapter written for BetterQuesting. Off, all of that falls back to plain vanilla-Forge behaviour - plain recipes, a golem built from vanilla blocks only, a Wayfarer that no longer prefers netherite, and no quest chapter written, though a chapter an earlier launch wrote is left where it is. It is not a switch for every companion mod, whatever it once said. Trophies answer to integration.trophies and the chest finds to the loot settings, and neither asks this: neither changes what anything costs, so neither has a plainer version to fall back to, and turning this off removes no trophy and takes nothing out of any chest - loot.lootFinds is the switch that takes every find out. The map coloring and tooltip readouts, which change no gameplay, are not reached by it either. The default is decided the first time this file is written - on when GregTech is installed, off otherwise - and then kept, so a pack that ran once before GregTech was added keeps this off until it is turned on here.");
        gtnhEnhanced = enhanced.getBoolean();
        dimensionListIsWhitelist = config.getBoolean(
            "dimensionListIsWhitelist",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, erosion only happens in the dimensions listed below. When false, it happens everywhere except them.");
        dimensionList = config.get(
            Configuration.CATEGORY_GENERAL,
            "dimensionList",
            new int[0],
            "Dimension ids the whitelist/blacklist above applies to. The Overworld is 0, the Nether -1 and the End 1; anything else is whatever id the mod that adds it was given. The list ships empty, and empty means opposite things in the two modes: in blacklist mode nothing is excluded, so ground wears everywhere, and in whitelist mode nothing is included, so ground wears nowhere at all. Turning that switch on and leaving this list alone therefore does not narrow erosion down to somewhere sensible - it stops it in every dimension there is, because the dimension test is asked before anything is tracked, worn, broken or tampered with. Name at least one id here before setting that switch to whitelist.")
            .getIntList();
        sneakSuppresses = config.getBoolean(
            "sneakSuppresses",
            Configuration.CATEGORY_GENERAL,
            true,
            "Sneaking adds no wear, so you can cross a lawn without leaving a mark.");
        wearDropsEnabled = config.getBoolean(
            "wearDropsEnabled",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether wearing a surface in has a small chance to shed something from it: grass drops a random seed - vanilla wheat, or any seed the pack's farming mods have registered - snow drops a snowball, and gravel drops flint. Rolled once each time an exposed surface block wears a stage, and only above the very bottom of the world, so an area tamper drops from the ground it exposes rather than from every block in the cube.");
        wearDropChance = readFloat(
            Configuration.CATEGORY_GENERAL,
            "wearDropChance",
            0.01f,
            0f,
            1f,
            "The chance, from 0 to 1, that a stage of wear on a dropping surface sheds its item. The default is one in a hundred, low enough to be a surprise rather than a way to farm.");
        retriggerCooldown = config.getBoolean(
            "retriggerCooldown",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether a mover that has just worn a block has to leave it alone for a while before it can wear it again. Wear is already counted per block entered rather than per tick, so standing still never wears anything; this is the next step - it stops a mob pacing a pen, or anything jumping on one square, from grinding that square to bare earth without actually going anywhere. The block is freed again once the mover is retriggerBlocks away or retriggerSeconds have passed, whichever comes first.");
        retriggerCooldownPlayers = config.getBoolean(
            "retriggerCooldownPlayers",
            Configuration.CATEGORY_GENERAL,
            false,
            "Whether that same cooldown is imposed on players too. Off by default: a player laying out a path deliberately should be able to work one spot as hard as they like. On, a player is throttled exactly as mobs are.");
        retriggerSeconds = config.getInt(
            "retriggerSeconds",
            Configuration.CATEGORY_GENERAL,
            10,
            0,
            600,
            "How many seconds a block a mover just wore is left alone before that same mover may wear it again. Reached at the same time as the distance below - whichever comes first frees the block. 0 turns the time limit off, leaving only the distance one.");
        retriggerBlocks = readFloat(
            Configuration.CATEGORY_GENERAL,
            "retriggerBlocks",
            3.0f,
            0f,
            64f,
            "How far, measured flat along the ground, a mover has to get from a block it just wore before it may wear it again ahead of the time limit. The point is that walking on somewhere else and coming back is fine, but treading the same square on the spot is not. 0 turns the distance route off, leaving only the time one.");
        minY = config
            .getInt("minY", Configuration.CATEGORY_GENERAL, 0, 0, 255, "Lowest world Y that will ever be tracked.");
        maxY = config
            .getInt("maxY", Configuration.CATEGORY_GENERAL, 255, 0, 255, "Highest world Y that will ever be tracked.");
        grassWearsThroughToDirt = config.getBoolean(
            "grassWearsThroughToDirt",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true a fully worn grass block keeps going as bare, then scuffed, earth. When false it stops at the last grass stage, which reads gentler: paths thin out but never go bald. This is the only switch turf answers to. wearThroughToOtherSurfaces below is about materials with a substance of their own to hollow out instead of showing what is under them, which turf has not: it is a face, with no depth of its own, so the earth beneath it is the only rut it can have.");
        wearThroughToOtherSurfaces = config.getBoolean(
            "wearThroughToOtherSurfaces",
            Configuration.CATEGORY_GENERAL,
            false,
            "Off out of the box. When true a surface that has worn through its own face goes on as a different material rather than hollowing out as more of itself: a stone road breaks into cobble, then into the grit that came off it, then into the earth it was laid on. When false every surface sinks as itself, which is what it did before this existed. It costs nothing in steps - a run is the same length either way and each step costs the same - so this changes what a rut looks like on the way down and nothing else. What each family becomes is families.<name>.wearsThroughTo. Grass is not among them: turf is a face rather than a substance and has no rut of its own to sink into, so it is grassWearsThroughToDirt above, and only that, which decides whether a path through turf ever goes bald.");
        wearPaceFollowsTheGround = config.getBoolean(
            "wearPaceFollowsTheGround",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether one step of wear costs what the ground is made of, or what the top of it currently looks like. A road that has worn through into another surface is still made of what it was made of, and charging its deep half at the price of the grit lying in it would take a stone road from about five thousand two hundred crossings to about two thousand two hundred, and cut the three hundred and twenty in-game days an abandoned one takes to recover to about a hundred and fifty. On, the block underneath decides both wearing and healing, so a family's thresholds and heal time mean what they say however far down its chain a position has been walked. Off restores the older behaviour, where each step was priced by the surface it happens to be drawing as, and grass's earth steps cost dirt's rate again rather than grass's.");
        physicalDecay = config.getString(
            "physicalDecay",
            Configuration.CATEGORY_GENERAL,
            DECAY_REAL,
            "Whether worn paths physically hollow out. real: ruts you can walk down into. The server owns the collision, so every client is shown the ruts and cannot switch the overlay off while connected; without that, a player with it off would trip over dips they cannot see. visual: ruts you can see but not feel. Nothing is forced on anyone and the per-player toggle stays free, at the cost of standing slightly above the deepest ruts. off: flat wear only. Per-family depth is families.<name>.maxSinkPixels.",
            new String[] { DECAY_REAL, DECAY_VISUAL, DECAY_OFF });
        updateNotice = config.getString(
            "updateNotice",
            Configuration.CATEGORY_GENERAL,
            UPDATE_OPERATORS,
            "Who is told, as they join, that a newer TRMT: Reimagined is out. operators: whoever can update this copy - you in single player, the host of a LAN game, a server's operators. everyone: every player, privately. off: nobody, and the mod never asks. Once a launch, a server reads one small file from GitHub to learn the newest version; nothing about you or your world is sent.",
            new String[] { UPDATE_OPERATORS, UPDATE_EVERYONE, UPDATE_OFF });
        updateNoticeReleases = config.getString(
            "updateNoticeReleases",
            Configuration.CATEGORY_GENERAL,
            RELEASES_RELEVANT,
            "Which newer releases updateNotice tells of. relevant: only one that changes this edition - every edition takes the new number with every release, so most releases change only some of them. all: every newer release. A release that changes nothing here is still written in the server's log. A release with a critical fix for this edition says so either way.",
            new String[] { RELEASES_RELEVANT, RELEASES_ALL });

        globalSpeed = config.get(
            Configuration.CATEGORY_GENERAL,
            "globalSpeed",
            1.0d,
            "One dial over the whole mod. 2.0 means paths form twice as fast and recover twice as fast; 0.5 halves both. Moving both sides together changes how quickly the mod moves without changing its balance. The two rates below tilt one side against the other on top of this.",
            0.01d,
            1000.0d)
            .getDouble();

        erosionSpeed = config.get(
            Configuration.CATEGORY_GENERAL,
            "erosionSpeed",
            1.0d,
            "Scales every wear threshold at once. 2.0 erodes twice as fast, 0.5 half as fast. The shipped defaults are about eight times slower than upstream TRMT, so 8.0 restores upstream's pace.",
            0.01d,
            1000.0d)
            .getDouble();
    }

    private static void readSurfaces() {
        surfaceAutoDetect = config.getBoolean(
            "autoDetect",
            CATEGORY_SURFACES,
            true,
            "Scan every installed block at startup and sort grass-, dirt-, sand-, gravel- and stone-like blocks into families automatically. This is what makes the mod cover the pack's modded terrain without naming each block by hand. Run /trmt surfaces to see what it found; per-family detection toggles and manual block lists live under the families category.");
        writeDetectedSurfaces = config.getBoolean(
            "writeDetectedToConfig",
            CATEGORY_SURFACES,
            true,
            "Write everything detection finds into the per-family blocks lists, so you can see what it decided. To drop one block while leaving detection on, add it to the exclude list below; removing it from a blocks list only sticks once autoDetect is off, because detection would otherwise put it straight back.");
        surfaceExclude = config.get(
            CATEGORY_SURFACES,
            "exclude",
            new String[] { "minecraft:mycelium", "minecraft:soul_sand", "minecraft:farmland", "etfuturum:farmland",
                "minecraft:tilled_field", "Natura:GrassSlabDouble", "GalacticraftAmunRa:tile.baseGrass" },
            "Blocks that must never erode, whatever detection concludes. Registry names only, no metadata. Tilled soil is here because a field someone hoed should stay a field; paths are deliberately not, and resist wear instead — see families.dirt.resistantBlocks. Natura's double grass slab is a shape somebody built out of turf rather than ground that grew, and Amun-Ra's red grass turns itself into methane dirt anywhere but its own world, so wearing either is showing you something that is about to stop existing.")
            .getStringList();
        maxWearSprites = config.getInt(
            "maxWearSprites",
            CATEGORY_CLIENT,
            262144,
            768,
            262144,
            "A ceiling on the wear sprites this mod plans into the block atlas, counting every one it registers: worn faces, the family fallbacks, the grass-side fringes, the grass side walls and the mended sides. It is not what decides whether they fit. At every stitch the mod measures how much of the atlas the rest of the pack has already taken and how large a texture this card will address, and plans no further than the room left: an 8192 square at most, or 16384 with client.largerAtlas, less a sixteenth held back for the stitcher, less the pack's own textures, each at the size its file says it is. That room is counted in sixteen-pixel slots, and a wear sprite is drawn at the resolution of the face it is worn from, so it takes one slot for a sixteen-pixel face, four for a thirty-two and sixteen for a sixty-four. While anisotropic filtering is on, every texture the game loads into the atlas for itself is sixteen pixels wider and taller, and the pack's own are priced that way; a wear sprite is not, because it is made from its face with that border taken off, so a sixteen-pixel face's wear takes one slot either way. Even with nothing else in an 8192 atlas that is 245,760 sprites at one slot apiece, under this default of 262,144, so at the default this setting never takes effect there at all: it exists to be turned down. With client.largerAtlas on the room can be four times that, and this default then holds the wear to an 8192 square's worth of sixteen-pixel sprites - which only a pack worn mostly at sixteen pixels reaches, since a larger face takes several slots for one sprite. Turned down far enough, it binds before the room does, which keeps the wear textures' share of video memory small on a machine short of it - 65,536 sixteen-pixel sprites are a 4096 square's worth, about eighty-five megabytes once mipmaps are counted, against about three hundred and forty for an 8192 square's worth. When it stops the plan, the ramp keeps its gradations and the faces last in registry-name order wear their family's generic art instead, and the log says so. The family fallbacks, fringes and walls are always planned in full, whatever this says and even where the room will not hold them, because without them worn ground has nothing to draw, and they count against this ceiling before any face does. Out of the box they come to three thousand eight hundred and forty sprites at eighty gradations and four rotations, and one hundred and ninety-two at sixteen and one, plus one for every grass wall and mended side and one for the wall an unknown grass falls back on: twelve appearances at every gradation and rotation, a fallback for each of the ten families that wear, one more for the earth under grass, and the fringe. Turning general.wearThroughToOtherSurfaces on, or joining a server whose rules turn it on, adds a fallback for every surface a family wears through into, which at the shipped families.<name>.wearsThroughTo makes eighteen appearances and those figures five thousand seven hundred and sixty and two hundred and eighty-eight. Set below what they come to, this gives no block its own wear at all and saves nothing further, and the log says so, and gives the figure that lets the first face in wherever the room would take it. Every face that wears with its own pixels costs gradations times rotations sprites, twice that for grass, which carries its earth as well. Where this ceiling stops the plan, lowering client.wearGradations fits more faces under it, but only below the count the log says was drawn, and never below sixteen; lowering client.wearRotations fits more too, though where the log also says the room drew fewer gradations than were asked, a rotation given up is spent on finer gradations first, up to the count asked for, and only what is left over brings faces back. Either way the log works out how many faces each change would keep. Where the room stops the plan, the ramp is at sixteen already, and lowering client.wearRotations is the only one of the two that can bring faces back. The log says how much room was measured and how much of it was used, every stitch.");
        largerAtlas = config.getBoolean(
            "largerAtlas",
            CATEGORY_CLIENT,
            false,
            "Let this mod plan its wear into a block atlas larger than an 8192 square - up to 16384, where your graphics card says it can address one. Off, the wear is planned into what an 8192 square has left once the pack's own textures are in, and a pack whose faces will not all fit at client.wearGradations draws every surface's ramp more coarsely, never below sixteen gradations, before any face falls back to its family's generic art; Chisel for 1.12.2's carvings, for one, drew every ramp at 62 of 80. On, they fit, and the price is video memory: a 16384 atlas is four times an 8192 one, about one and a third gigabytes with its smaller copies against about three hundred and forty megabytes. Turn it on only if your card has that to spare. The game builds the atlas without checking that the card accepted it, so a card that cannot hold one draws every block in the game black, with nothing in the log to say why - if that happens, turn this off again. A pack that needs more than 8192 for its own textures gets it from the game either way; this only lets the wear ask for the room. client.maxWearSprites still applies. Takes effect on the next resource reload.");
        com.trmtgtnh.client.texture.AtlasPlan.allowLarger(largerAtlas);
        maxTexturedSurfaces = config.getInt(
            "maxTexturedSurfaces",
            CATEGORY_SURFACES,
            1024,
            0,
            4096,
            "Upper bound on how many distinct block faces get their own generated wear textures. Anything past it falls back to the family's vanilla-derived set, which is why a modded block would wear into a vanilla one. Metadata values that draw the same face share a set, so this counts faces rather than block states. It is not the only limit. The block atlas is measured at every stitch, and when the faces this lets through would not fit in the room it actually has, the ramp is drawn more coarsely first, never below sixteen gradations, and past that the faces last in registry-name order fall back too - so once the log says the atlas stopped the plan, raising this changes nothing. Zero gives no block its own wear at all.");
        inheritShaderMaterial = config.getBoolean(
            "inheritShaderMaterial",
            CATEGORY_CLIENT,
            true,
            "Keep the shader material of the block underneath, so worn ground does not lose every property a shader pack gives it. A pack decides how a block behaves under light by looking it up by name in its own table, and the blocks this mod paints are in nobody's table - so with shaders on, a worn stone road was not merely duller, it was a stranger. This tells the shader pipeline to treat it as the block it is covering while it is still that material, and as whatever it has worn through into once it is not, so the shine goes as the road breaks up rather than the moment anybody walks on it. Needs Angelica, and does nothing at all without it or without a shader pack loaded: it is one boolean read on a client that is not using them, and no Angelica class is named unless the mod is actually there.");
        inheritAmbientParticles = config.getBoolean(
            "inheritAmbientParticles",
            CATEGORY_CLIENT,
            true,
            "Let a worn block go on scattering whatever the block underneath it scatters - mycelium's spores, a lit block's sparkle, and whatever a modded terrain block does of its own accord. The client picks a thousand positions around you every tick and asks each block it lands on for its ambient particles, and it does that whether or not the block asked to be ticked, so a ghost is asked exactly as often as the ground it replaced and without this answers with silence. The one cost is that it runs the covered block's own code from the client tick, which is third-party code in a pack this size; a block that throws is noted once and skipped, and turning this off stops asking altogether.");
        inheritEntityCollision = config.getBoolean(
            "inheritEntityCollision",
            CATEGORY_CLIENT,
            true,
            "Let worn ground go on doing to whoever walks into it whatever the block underneath does. Chisel's cloud is the one this was written for: it takes an entity's fall almost entirely away, and a worn one had stopped doing it, because what your client sees at that position is this mod's stand-in rather than the cloud. A fall is worked out by your own machine and the server takes the answer, so losing this lost the effect outright rather than halving it. Only the player at the controls and whatever they are riding: everything else is moved by the server and arrives here as positions to copy, so nudging one would buy jitter, and a block that hurts or ignites what stands in it should not be given a second chance to do it. Off, worn ground of that kind is simply ground. Blocks that keep state of their own are never covered by this mod at all, so nothing here can reach for something that is no longer there.");
        settleOnWornGround = config.getBoolean(
            "settleOnWornGround",
            CATEGORY_CLIENT,
            true,
            "Draw snow and carpet down with ground that has worn away underneath them. A snow layer lying on a rut half a block deep otherwise hangs in the air over it, because a block's shape is its own business and nothing tells it what has happened below. Purely how it looks: a snow layer has no collision box to move, nothing is sent to the server, and where you stand is unchanged. Deliberately just those two - a rail or a repeater would settle as easily and would then be drawn a step below the box the server still holds it at, and a patterned block would smear, because the renderer stretches a whole texture over a side face once a shape leaves its own block.");
        groundCoverAutoDetect = config.getBoolean(
            "groundCoverAutoDetect",
            CATEGORY_SURFACES,
            true,
            "Also scan for ground cover at startup: the thin plant-material decorations that grow out of whatever is under them - tall grass, flowers, saplings, dead bushes and the modded equivalents. These are not surfaces and never wear; the list only decides what trampling.groundCover is allowed to knock down. What it finds is written into groundCoverBlocks below, the same way the family lists are filled in.");
        groundCoverBlocks = config.get(
            CATEGORY_SURFACES,
            "groundCoverBlocks",
            new String[0],
            "Blocks that count as ground cover, filled in by detection and yours to add to. Registry names, optionally with a metadata suffix - 'BiomesOPlenty:foliage:15' names the dead leaf pile alone, where 'BiomesOPlenty:foliage' names all sixteen plants that block holds. Detection always claims all sixteen, so a suffix is only worth writing once autoDetect is off. Use groundCoverExclude to take a block back out; removing it from here only sticks with detection off, because detection would put it straight back.")
            .getStringList();
        groundCoverHoldsBlocks = config.get(
            CATEGORY_SURFACES,
            "groundCoverHoldsBlocks",
            new String[0],
            "Ground cover that holds its square rather than coming off it, filled in by detection and yours to add to. Saplings and flowers are found automatically; a modded herb or a magical bloom that ought to be spared is one line here. Same format as the lists above - registry names, optionally with a metadata suffix. Anything named here that is not ground cover in the first place is ignored, because holding is one of two answers to what happens when the ground gives way and a block that was never going to be asked cannot answer either.")
            .getStringList();
        groundCoverHoldsExclude = config.get(
            CATEGORY_SURFACES,
            "groundCoverHoldsExclude",
            new String[0],
            "Cover that must never be treated as holding, whatever detection concludes. The way to make a sapling break with everything else rather than hold its ground.")
            .getStringList();
        groundCoverExclude = config.get(
            CATEGORY_SURFACES,
            "groundCoverExclude",
            new String[0],
            "Blocks that must never be treated as ground cover, whatever detection concludes. Registry names only, no metadata. Empty by default: stalks are deliberately included, so a cane or a cactus standing on ground that drops away comes down in full rather than hanging there. Put a name here for anything a pack would rather keep - the entry wins over detection, which is what makes it useful, because removing a name from groundCoverBlocks only sticks while detection is off.")
            .getStringList();
        wearGradations = config.getInt(
            "wearGradations",
            CATEGORY_CLIENT,
            com.trmtgtnh.surface.WearScale.COUNTED_STEPS,
            com.trmtgtnh.surface.SurfaceFamily.MAX_STAGES,
            com.trmtgtnh.surface.WearScale.COUNTED_STEPS,
            "How many separate pictures each surface's run of wear is drawn with. A family's whole run is eighty steps at the shipped figures - sixteen gradations on the face as it stands, then a pixel of depth and eight more, eight times over - so eighty here, which is the default, gives every one of those its own picture and no two consecutive steps look alike. Sixteen is the floor, and there consecutive steps share a picture five ways. The wear textures in the block atlas grow in step with this, so eighty costs five times what sixteen does; lower it if a machine struggles to stitch. When the block atlas has not the room for this many on every face, fewer are drawn, never fewer than sixteen, and the log says how many and why; past that it is faces that give way rather than gradations. While the log says fewer were drawn than asked, setting this anywhere from that count up changes nothing.");
        wearCurve = (float) config.get(
            CATEGORY_CLIENT,
            "wearCurve",
            1.0d,
            "How far every wear pattern's run is pushed away from the shape measured for it. One leaves each pattern on its own number and is what should normally be here. Below one the early steps of every run do more and the later ones less, so a path appears quickly and then deepens slowly; above one the reverse, and ground stays nearly untouched for a while before going all at once. This used to be a single exponent applied to every pattern alike, and that was the mistake it is now a scale to avoid: a crack spreads its own change almost evenly and goes on paying for a straight line to the end of its run, while a rub has barely started by the halfway mark and needs the curve to pull its late work forward - so the one number that suited the cracked families left the rubbed ones changing by a fifth of a color level a step, which is nothing. This is not the same question as how worn the ground gets, which is the family's own wearStrength and maxWear; it is only how the journey there is divided up. Two things to know before moving it far. Because it multiplies, the reachable range differs by pattern: 0.2 to 3.0 here is an effective 0.20 to 3.00 on the cracked and buffed patterns but only 0.12 to 1.80 on the rub, so setting three and finding the rub stopped at 1.8 is the setting working rather than failing. And the rub's own shape is sharp - taking this to about 1.3 costs it more than half of its smallest step, long before anything looks wrong on stone.",
            0.2d,
            3.0d)
            .getDouble();
        wearRotations = config.getInt(
            "wearRotations",
            CATEGORY_CLIENT,
            4,
            1,
            4,
            "How many wear patterns a surface has to spread across neighbouring blocks - which pixels wear away first, not a turn of the texture. A position already picks one of four, but only two used to be built and the other two folded onto them, which is why a long path read as striped in a two-block rhythm. Four is the most the position can distinguish. Every face's wear textures are gradations times rotations, so while everything asked for is drawn this multiplies what the wear takes in the block atlas exactly as client.wearGradations does, and lowering either lightens the stitch. Once the room left in the atlas or client.maxWearSprites limits the plan, a rotation given up is spent rather than saved: where the log says fewer gradations were drawn than asked, on finer gradations, up to the count asked for, and where it says faces gave way, on bringing faces back. Until everything asked for is drawn the atlas is about as full as before, so a lower setting here trades wear patterns for gradations or faces and leaves the stitch no lighter. To make it smaller where the log says fewer gradations were drawn and no face gave way, lower client.wearGradations below the count the log gives; where faces gave way, lower client.maxWearSprites below the sprites the log says were registered in all, which gives still more of them their family's generic art.");
        wearNeverStepsBack = config.getBoolean(
            "wearNeverStepsBack",
            CATEGORY_CLIENT,
            true,
            "Whether worn ground is forbidden from ever looking cleaner than it did a step ago. Off, the drawn gradation mixes how far through its current run a block is with how far through the whole chain it is, and the first of those restarts every time the ground sinks a pixel - so the texture jumps back toward clean at the exact moment the block drops lower. On, each run is given its own slice of the pictures and fills it from start to end, which keeps both readings and cannot go backwards. Turn it off to get the old behaviour back; wearCarriesWithDepth only does anything while it is off.");
        grassSideWear = config.getBoolean(
            "grassSideWear",
            CATEGORY_CLIENT,
            true,
            "Whether a worn grass block's side fringe recedes toward dirt as its top wears, instead of keeping a full green edge down a bald path. On, the tinted fringe is thinned per gradation and the earth wall behind it is de-greened, so the sides of a path read as soil; off leaves the static grass side. This drives a second renderer mixin, so if a resource pack renames a modded grass side away from grass_side and its fringe drops out, turn this off to recover it.");
    }

    /** What the demonstrate command asks for now, and what every build before 0.9.118 asked for. */
    private static final String[] DEMO_CONTAINER = { "Avaritia:Infinity_Chest", "IronChest:BlockIronChest:9",
        "minecraft:chest" };

    private static final String[] DEMO_CONTAINER_WAS = { "Avaritia:Infinity_Chest", "IronChest:BlockIronChest",
        "minecraft:chest" };

    /** Whether the warning about a file naming both chunk tamper settings has been given this run. */
    private static boolean retiredChunkTamperSaid;

    /** Whether the warning about a file naming both names of the desire-path setting has been given this run. */
    private static boolean retiredDesirePathSaid;

    /**
     * Whether the file just read names a setting outright, rather than the setting being merged in from
     * what was already held.
     *
     * <p>
     * Forge merges a reloaded file into the settings in memory, so after a reload every name this mod has
     * ever read is present whether or not the file still says it, and nothing on a setting records where
     * it came from this time. Asked of the file's own text, for the one question that needs the
     * difference. A file that cannot be read names nothing.
     */
    private static boolean fileNames(String key) {
        return fileNames(config, key);
    }

    /** {@link #fileNames(String)}, asked of the file behind the settings given rather than the mod's own. */
    private static boolean fileNames(Configuration from, String key) {
        java.io.File file = from.getConfigFile();
        if (file == null || !file.isFile()) return false;
        try {
            String text = new String(
                java.nio.file.Files.readAllBytes(file.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
            return java.util.regex.Pattern
                .compile("(?m)^\\s*[A-Za-z]:\\s*" + java.util.regex.Pattern.quote(key) + "\\s*=")
                .matcher(text)
                .find();
        } catch (java.io.IOException unreadable) {
            return false;
        }
    }

    /**
     * Moves an untouched demoContainer on to the spelling that names a chest worth using.
     *
     * <p>
     * Forge keeps whatever is in the file and only ever writes a default into one that has none, so
     * a changed default reaches nobody who has run the mod before - which for this setting is
     * everybody. The old spelling still resolves, silently, to the smallest chest Iron Chest has,
     * and the comment above it in their file now describes a chest that spelling cannot ask for.
     *
     * <p>
     * Rewritten only when it matches the old default exactly, entry for entry. Anything anybody has
     * actually edited is left alone, which is the line the rest of the config already draws: the
     * mod may replace what the mod wrote, and never what somebody typed.
     */
    private static String[] repointDemoContainer(Property property) {
        String[] current = property.getStringList();
        if (current == null || current.length != DEMO_CONTAINER_WAS.length) return current;
        for (int i = 0; i < current.length; i++) {
            if (current[i] == null || !current[i].trim()
                .equals(DEMO_CONTAINER_WAS[i])) {
                return current;
            }
        }
        property.set(DEMO_CONTAINER);
        Trmt.LOG.info("Pointed demoContainer at the roomier Iron Chest, which is what its default now asks for");
        return DEMO_CONTAINER;
    }

    private static void readMultipliers() {
        erodeFromPlayers = config
            .getBoolean("fromPlayers", CATEGORY_MULTIPLIERS, true, "Whether players walking causes wear.");
        erodeFromLeashedMobs = config.getBoolean(
            "fromLeashedMobs",
            CATEGORY_MULTIPLIERS,
            true,
            "Whether mobs you are leading on a lead cause wear. A loose mob wears only if the mobs list names it, which out of the box is villagers alone, so a wandering herd does not carve roads across the world.");
        multiplierPlayer = readFloat(
            CATEGORY_MULTIPLIERS,
            "player",
            0.5f,
            0f,
            100f,
            "Wear added when a player steps onto a new block, and the unit every other figure in this category is a multiple of rather than an alternative to. A mount, a lead or a named mob multiplies this number instead of replacing it, so halving it to soften the tracks players leave halves villagers and led animals by exactly as much; to quieten one kind of mover on its own, move that mover's own line. It is also what one crossing costs on the wear table, which is why the figures there shift when this does. Nought is a real setting and it means never: the step is abandoned before the ground is ever looked at, so nothing that walks wears anything at all, whatever the mob list and the lead switch say. That is not the same as turning players off, which is fromPlayers above, and it leaves explosions alone, because a blast is scoured by its own strength rather than by this.");
        multiplierMounted = readFloat(
            CATEGORY_MULTIPLIERS,
            "mounted",
            2.0f,
            0f,
            100f,
            "Extra multiplier while riding. Hooves wear a path faster than boots.");
        multiplierLeashed = readFloat(
            CATEGORY_MULTIPLIERS,
            "leashed",
            1.5f,
            0f,
            100f,
            "Extra multiplier for an animal being led on a lead. Above 1 because a herd walked the same way every day is how a drove road gets worn in, and a lead is the clearest sign in the game that an animal is going somewhere rather than wandering.");
        mobMultipliers = parseMobs(
            config.get(
                CATEGORY_MULTIPLIERS,
                "mobs",
                new String[] { "Villager" },
                "Which mobs wear the ground, on top of players and anything on a lead. One entity name per line, as the game names it - Villager, Zombie, Pig. Add ':' and a number to make one wear harder or softer than the rest, e.g. 'Villager:1.5'. A line of '*' means every mob, which in a pack this size is a lot of mobs. 'Zombie:0' keeps zombies off the ground while they walk loose, even under a '*' line, because a named line outranks the wildcard; it is what the magic tamper's stop button writes. A mob on a lead still wears while fromLeashedMobs is on, because that counts anything being led. Baby villagers are villagers and are covered by the same line.")
                .getStringList());
        bonemealRadius = config.getInt(
            "bonemealRadius",
            Configuration.CATEGORY_GENERAL,
            2,
            0,
            8,
            "How far a handful of bone meal can reach when mending worn ground, in blocks. The patch it actually mends is a random size up to this, so repairing a track is a few scattered handfuls rather than one square at a time. 0 mends only the block used.");
        bonemealMaxSteps = config.getInt(
            "bonemealMaxSteps",
            Configuration.CATEGORY_GENERAL,
            80,
            1,
            1000,
            "The most gradations one square can be mended by in a single handful. Each square in the patch rolls its own amount, from one gradation up to this, so the mend comes out blotchy rather than uniform. The default is a whole chain, meaning a lucky square can be fully restored.");
        repairAnyInFamily = config.getBoolean(
            "repairAnyInFamily",
            CATEGORY_HEALING,
            true,
            "Whether any block of a family can mend any worn block of that same family. On by default, so a worn modded dirt can be filled with any other block the mod counts as dirt without listing every one in repairBlocks. The per-family repairBlocks lists still work on top of this and are how to allow something from outside the family - grass mended with earth. Off, only the block's own kind and its repairBlocks list will pay. Read by bone meal, the chunk tamper and the golem, which pay with a block of the ground itself; the hand tamper pays in what the ground drops and ignores this.");
        xpFromHealing = config.getBoolean(
            "xpFromHealing",
            CATEGORY_HEALING,
            true,
            "Whether mending worn ground is worth experience. Priced per gradation put back rather than per block or per gesture, so filling a deep rut is worth more than brushing off one scuff. Only a gradation that cost something earns anything, because wearing ground in is free and a free way back out would make the round trip a farm. Bone meal spent from a player's hand earns outside creative whatever bonemealCostsABlock says, since the handful itself is spent; a dispenser, and another mod's fertiliser while that setting is off, earn nothing. A tamper earns only for the gradations it took material for. So creative mode, the Wayfarer's Tamper, a tamperMendCost or tamperPatchCost of 0 and a chunk tamper with bonemealCostsABlock off all mend for nothing and earn nothing at any xpPerGradation - and a tool with a repairing enchantment is not repaired by them either, because that repair is paid out of the same experience.");
        xpPerGradation = readFloat(
            CATEGORY_HEALING,
            "xpPerGradation",
            0.25f,
            0f,
            64f,
            "Experience per gradation of wear mended and paid for, before any enchantment on the tool. A gradation that cost nothing earns nothing at any figure - see xpFromHealing. Fractions are honoured by chance rather than by rounding - at the default a quarter of the gradations pay a point each - because a whole orb is the only unit the game has and rounding a quarter down would pay nothing at all.");
        xpBoostPerLevel = readFloat(
            CATEGORY_HEALING,
            "xpBoostPerLevel",
            0.25f,
            0f,
            4f,
            "How much each level of an experience-boosting enchantment adds, as a fraction of the base. 0.25 means a level III boost pays 175 per cent.");
        mendingEnchantments = config.get(
            CATEGORY_HEALING,
            "mendingEnchantments",
            new String[] { "mending", "repair" },
            "Name fragments that mark an enchantment as one that repairs a tool from experience. There is no Mending in 1.7.10 - it arrives two versions later - so this is matched against whatever the pack supplies under a name that reads like one, by name rather than by id because ids differ between installs. When a tamper carries one, experience earned mending ground repairs the tamper first at vanilla's rate of two durability per point, and only the remainder reaches the player.")
            .getStringList();
        xpBoostEnchantments = config.get(
            CATEGORY_HEALING,
            "xpBoostEnchantments",
            new String[] { "xpboost", "experienceboost", "xp" },
            "Name fragments that mark an enchantment as one that increases experience gained. Matched the same way as mendingEnchantments, with punctuation and spaces ignored on both sides.")
            .getStringList();
        explosionWear = config.getBoolean(
            "explosionWear",
            CATEGORY_EXPLOSIONS,
            true,
            "Whether an explosion scours the ground around it as well as blowing a hole in it. What a blast does not destroy outright it still scours, and a crater with clean grass up to its lip reads as a hole somebody cut rather than as something that went off.");
        explosionStrength = config.getFloat(
            "explosionStrength",
            CATEGORY_EXPLOSIONS,
            140.0f,
            0f,
            1000f,
            "How hard the ground is scoured at the very middle of a blast, in the same units a footstep uses, falling away to nothing at the edge. The middle is not where you see it: a charge destroys the ground directly beneath it, so what this number really sets is how worn the crater lip is. At the default a stick of dynamite leaves its lip about a third worn and fades out four or five blocks past that.");
        explosionMaxRadius = config.getFloat(
            "explosionMaxRadius",
            CATEGORY_EXPLOSIONS,
            64.0f,
            1f,
            256f,
            "How far from a blast the scouring may reach, whatever radius the charge itself claims. A ceiling rather than a setting: the radius arrives from whatever set the explosion off, and a mod handing over a very large one would otherwise have the server walk a great many positions inside a single tick. See explosionMaxPositions, which bounds the work rather than the distance and is the one that will actually bite first on a very large charge.");
        explosionReach = config.getFloat(
            "explosionReach",
            CATEGORY_EXPLOSIONS,
            2.0f,
            0.1f,
            8f,
            "How far the scouring reaches compared with the blast's own radius. Above 1 because the ground a charge scours is wider than the hole it digs. It also decides how much of the wear the survivors get: the fall-off is measured from the middle, and the middle is inside the crater, so a short reach means the only ground left standing is the ground the fall-off had already faded out on.");
        explosionMaxPositions = config.getInt(
            "explosionMaxPositions",
            CATEGORY_EXPLOSIONS,
            262144,
            1024,
            4194304,
            "The most positions one blast may look at. A blast is handled inside the tick that set it off, and the sphere grows as the cube of its radius, so a very large charge would otherwise stall the server outright. When this bites, the reach is pulled in until the work fits - which costs the outermost ring, where the wear had faded to nothing anyway.");
        fallWear = config.getBoolean(
            "fallWear",
            CATEGORY_IMPACTS,
            true,
            "Whether something landing hard scuffs the ground it lands on. The same idea as an explosion and much tamer: what comes down from a height puts its momentum through a patch of ground about its own size, marking that and very little around it.");
        fallStrength = readFloat(
            CATEGORY_IMPACTS,
            "fallStrength",
            6.0f,
            0f,
            1000f,
            "How hard the ground is marked by a landing from fallReferenceDistance, in the same units a footstep uses. Everything else is in proportion to the speed it arrived at, which goes as the square root of the drop - so twice the height is not twice the blow.");
        fallMinDistance = readFloat(
            CATEGORY_IMPACTS,
            "fallMinDistance",
            3.0f,
            0f,
            256f,
            "Below this a landing marks nothing. Anything shorter is a step somebody took rather than a fall, and it is already counted as one; it is also where vanilla stops doing fall damage.");
        fallReferenceDistance = readFloat(
            CATEGORY_IMPACTS,
            "fallReferenceDistance",
            10.0f,
            1f,
            256f,
            "The drop that fallStrength is quoted against. Raise it to make every landing gentler without touching the strength figure.");
        fallMaxRadius = readFloat(
            CATEGORY_IMPACTS,
            "fallMaxRadius",
            3.0f,
            1f,
            16f,
            "How far a landing's scuff can spread, however far the thing fell. A landing is a local thing; this stops a very long drop reading as a small explosion.");
        bonemealCostsABlock = config.getBoolean(
            "bonemealCostsABlock",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether mending worn ground with bone meal also costs a block of the ground being mended, taken from anywhere in the player's inventory. Every kind of ground the handful mends pays for itself, once: a patch across a grass verge and a cobble road costs a block for the grass and another for the cobble, and a square whose ground you are carrying nothing for is left exactly as it was rather than mended at the expense of whatever you happened to click, and chat says so whenever the handful mended something or was aimed at worn ground. A block bought for one square covers another only when it would have paid for that one too. A block of exactly what is under a square always pays; families.<name>.repairBlocks names what else will, which is what lets grass and paths be mended with ordinary dirt, and healing.repairAnyInFamily lets any block of the same family pay. Nothing is taken for a handful that mended nothing. Creative pays nothing. Off restores the old behaviour, where a handful of bone meal repaired a season of traffic for nothing but the bone meal; bone meal spent from a player's hand still earns experience then, and a dispenser or another mod's fertiliser earns none. The chunk tamper is paid by this same rule and switched by this same setting, so off makes its mending free of material too - and because a chunk tamper that spends no material earns no experience, off also means a chunk tamper never earns any. A golem pays by this same rule but is not switched by this setting: off, it still spends its blocks, because a keeper that cost nothing to run would never need looking after. See golem.blockCost.");
        maxWearFraction = readFloat(
            Configuration.CATEGORY_GENERAL,
            "maxWearFraction",
            1.0f,
            0f,
            1f,
            "How far along its run any surface may wear, as a fraction of the whole - so 0.25 stops everything at a quarter of its eighty steps, about where a path is a path and not yet a hollow. A ceiling on the finished look rather than on the rate: ground still wears at whatever speed it was going to and simply stops when it arrives here. Each family has its own ceiling too, in families.<name>.maxWear, and whichever is lower wins.");
        familyEditorOperatorsOnly = config.getBoolean(
            "familyEditorOperatorsOnly",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether changing which blocks wear, and which creatures wear it, from the magic tamper's screen, is an operator's privilege. On by default because it writes this config and reloads it, which is not something one player should be able to do to everybody else's world. A player without the privilege is not shown the controls at all rather than shown them and refused.");
        tamperDurabilityScale = readFloat(
            Configuration.CATEGORY_GENERAL,
            "tamperDurabilityScale",
            1.0f,
            0f,
            10f,
            "Scales how long a tamper lasts against its grade's own figure - 512 uses for iron, 256 for gold, 2048 for diamond and 4096 for netherite, read from the third field of each tamperGrades entry rather than from anything here. Only a gesture that actually changed something costs a use, so brushing one along ordinary ground is free. It never reaches nought: anything under a hundredth is held at a hundredth and every tamper keeps at least one use, so a very small figure makes tampers nearly disposable rather than unbreakable - the Wayfarer's is the one that never wears. Read live, so a change applies at once to every tamper, including one already in somebody's hand; lower it far enough and a well-used tamper can be left with fewer uses than it has already spent, and it breaks on its next one.");
        chunkTamperDefaultReach = config.getInt(
            "chunkTamperDefaultReach",
            Configuration.CATEGORY_GENERAL,
            2,
            0,
            7,
            "How far out of the clicked block a newly made chunk tamper reaches, so 2 is a five-by-five-by-five cube. Right-clicking the air walks it up a step and round, and the tooltip says where it is.");
        chunkTamperMaxReach = config.getInt(
            "chunkTamperMaxReach",
            Configuration.CATEGORY_GENERAL,
            7,
            0,
            7,
            "The furthest a chunk tamper may be set to reach. Seven is a fifteen-by-fifteen-by-fifteen cube, which is a chunk's width; the ceiling is there because the cost of a gesture goes as the cube of this number.");
        chunkTamperMaxSteps = config.getInt(
            "chunkTamperMaxSteps",
            Configuration.CATEGORY_GENERAL,
            8,
            1,
            16,
            "The most gradations one chunk tamper gesture may move a position. Sneak and right-click the air to walk it up. Every gradation put back is paid for, so a gesture costs as much as it puts back: over any real area of ground worn eight deep, a use set to eight costs very nearly eight times a use set to one - not quite, on a handful of squares, because the last block of each kind of ground is rounded up either way. See chunkTamperGradationsPerBlock.");
        // Retired, and carried across. The chunk tamper stopped charging by the position in 0.9.204 and charges by
        // the gradation, which is what its tooltip first promised. The figure is the same exchange rate counted in
        // the unit the price is now paid in, so a pack's tuning is kept rather than thrown away along with a name
        // that would otherwise go on saying positions.
        ConfigCategory general = config.getCategory(Configuration.CATEGORY_GENERAL);
        if (general.containsKey("chunkTamperPositionsPerBlock")) {
            String carried = general.get("chunkTamperPositionsPerBlock")
                .getString();
            if (fileNames("chunkTamperGradationsPerBlock")) {
                // The file names both, so it has been edited or merged by hand since the rename, and the new
                // name is the one somebody chose. The old one is dropped, and said once, rather than winning in
                // silence - on every read, where the file is one nothing is allowed to rewrite.
                if (!retiredChunkTamperSaid) {
                    retiredChunkTamperSaid = true;
                    com.trmtgtnh.Trmt.LOG.warn(
                        "Ignoring general.chunkTamperPositionsPerBlock={}: the file also sets chunkTamperGradationsPerBlock, which replaced it in 0.9.204",
                        carried);
                }
            } else if (general.containsKey("chunkTamperGradationsPerBlock")) {
                // The file names only the old one, yet the new one is held: a reload merges the file into the
                // settings already held, so by then the new name is always present. Copied over, or a pack's
                // file or a config snapshot still carrying the old name had its figure thrown away by
                // /trmt reload - the very path a pack's figure is likeliest to arrive by.
                general.get("chunkTamperGradationsPerBlock")
                    .set(carried);
            } else {
                general.put(
                    "chunkTamperGradationsPerBlock",
                    new Property("chunkTamperGradationsPerBlock", carried, Property.Type.INTEGER));
            }
            general.remove("chunkTamperPositionsPerBlock");
        }
        chunkTamperGradationsPerBlock = config.getInt(
            "chunkTamperGradationsPerBlock",
            Configuration.CATEGORY_GENERAL,
            4,
            1,
            256,
            "How many gradations of wear one block of material pays a chunk tamper to put back. Priced by the gradation rather than by the square, so filling a deep rut costs what it is deep: at the default a fifteen-by-fifteen layer of one kind of worn ground costs 57 blocks mended one gradation deep and 450 mended eight deep, about a quarter of what the hand tamper's single-square mend would charge for the same gradations at its own default. Each square in the cube is paid for from blocks that would mend it, by bone meal's rule (see bonemealCostsABlock, which also switches this cost off), and each kind of ground is rounded up on its own, so a cube holding one worn square of grass and one of cobble spends a block of each - and so does one holding grass and bare earth, although both are mended with earth. A block is taken only once the gradation it pays for has gone back, and ground whose material runs out is left as it is from there on, with a line in chat to say how many squares were. Replaces chunkTamperPositionsPerBlock, which counted positions however deep each was mended; a figure set under that name is carried across unchanged."
                // The golem's half of this follows GolemWork.PRICED_FROM_THE_CHUNK_TAMPER, so the file never
                // describes a price the golem is not charging.
                + (com.trmtgtnh.entity.GolemWork.PRICED_FROM_THE_CHUNK_TAMPER
                    ? " A golem carrying a chunk tamper or the Wayfarer's is priced from this figure too: golem.blockCost blocks buy this many gradations of one kind of ground, rounded up for each kind on each stroke. A golem with a plain tamper puts back a single gradation a stroke, so it pays the whole of golem.blockCost for each one whatever this says. See golem.blockCost."
                    : " A golem does not read this figure: it pays golem.blockCost for every gradation whatever tamper it holds. See golem.blockCost."));
        reinforceEnabled = config.getBoolean(
            "enabled",
            CATEGORY_REINFORCE,
            true,
            "Whether the reinforcement feature exists at all: the enchantment that unlocks it, the tool mode, and the blast-proofing it grants. Off, the enchantment still registers so a save that has it on a tool does not lose an id, but the mode and its gestures do nothing, and nothing new of it is handed out: an enchanting table will not roll it onto a book or offer it for a tamper, a librarian will not offer it in a new trade, and the book's recipe, its chest finds, its two achievements, its trophy and its quest are left out. The mode, the table and librarians follow a change straight away, from /trmt reload or the config screen. The recipe, the chest finds, the achievements, the trophy and the quest are settled as the game starts and follow a change either way only after a restart, so until then a switch turned off still leaves the book craftable and findable, and one turned on leaves it neither. Nothing already made is taken back: a librarian who already sells the book keeps that trade, which is saved with the villager, a chest that already holds one keeps it, a world that has already taken the quest chapter keeps its quests, and a book of it will not go onto a tool while this is off, except by a player in creative mode. Another mod that picks enchantments from the game's book list for itself, rather than through an enchanting table or the game's librarian, is not held to this. The Waila readout reads this machine's own copy, so a client with it off shows no reinforcement even on a server that has it on.");
        reinforceEnchantId = config.getInt(
            "enchantId",
            CATEGORY_REINFORCE,
            -1,
            -1,
            255,
            "The id the reinforcement enchantment registers at. -1 takes the first free slot at startup, which is fine for a single pack but drifts if the set of mods changes - and the id is written into the enchanted tool's own data, so a drift silently strips the enchantment off existing tools. Pin it to a fixed free number on a pack you intend to keep.");
        reinforceMaxLevel = config.getInt(
            "maxLevel",
            CATEGORY_REINFORCE,
            3,
            1,
            3,
            "The most times one block may be reinforced. The top level is fully blast-proof; the levels below raise its blast resistance. Capped at 3 whatever is written, because the level is stored in two bits of the position's own record.");
        reinforceWearFactor = readFloat(
            CATEGORY_REINFORCE,
            "wearFactor",
            1.0f,
            0.0f,
            16.0f,
            "How much longer each reinforcement level makes a block take to wear, counted as whole extra lifetimes added per level. At the default of 1 a block reinforced once needs twice the traffic to move a step, twice needs three times and three times needs four - and because it applies to every one of the eighty steps, a fully reinforced road takes four times the walking to sink from new to bare. Healing is untouched: the recovery rate is a share of the threshold, so a reinforced block still mends a step in the same idle time. Only the rate is governed here. A reinforced leaf or plant is never trampled at all, as trampling.leaves and trampling.vegetation say, and reinforced ground never wears away under trampling.groundWearsAway, whatever this is set to. Set to 0 to take reinforcement off the rate ground wears at, as it was before this existed: it still guards against explosions, against trampling and against ground wearing away.");
        reinforceMaterials = config.get(
            CATEGORY_REINFORCE,
            "materials",
            new String[] { "fluid:wet.concrete", "dreamcraft:dreamcraft_Concrete_bucket",
                "dreamcraft:clayBucketConcrete", "minecraft:obsidian" },
            "What one reinforcement costs, tried in the order written and paid with the first the player is carrying. A 'fluid:name' entry is a bucket or cell of that fluid, named as the fluid registry names it rather than as the tooltip spells it. A plain 'modid:name[:meta]' entry is one of that item, and is the form to use for a container that no mod registered as a fluid container - which is what GT New Horizons' two concrete buckets are, and why they are named outright here. Either way the container's own rule decides what comes back: a bucket that declares an empty gets its empty handed back, from the fluid registry or from the item's own container item, and a single-use clay one declares none and is spent. An entry naming something this pack does not have is skipped, so the whole list can name four things and find one - which is what the default does, preferring concrete in whichever form the pack has it and falling back to obsidian on plain Minecraft. If what you are carrying is not being taken, stand by the golem and run /trmt golem: it prints what the item actually is, what each entry here resolves to, and the exact line to add.")
            .getStringList();
        reinforceCeiling = new float[] { readFloat(
            CATEGORY_REINFORCE,
            "ceilingLevel1",
            8.0f,
            0f,
            100000f,
            "The blast size a block reinforced once can withstand, on top of its own resistance. Vanilla TNT is size 4; a creeper 3. Above this size the block is destroyed as usual."),
            readFloat(
                CATEGORY_REINFORCE,
                "ceilingLevel2",
                30.0f,
                0f,
                100000f,
                "The blast size a block reinforced twice can withstand, on top of its own resistance. Reinforcing a third time makes it fully blast-proof whatever this says.") };
        spawnWithTamper = spawnItem("tamper", "a plain tamper at the pack's first grade");
        spawnWithChunkTamper = spawnItem("chunkTamper", "a chunk tamper at the pack's first grade");
        spawnWithWayfarer = spawnItem("wayfarerTamper", "the Wayfarer's tamper, which is the whole ladder at once");
        spawnWithGuideMk1 = spawnItem("guideMk1", "the Wayfarer's Guide, Mk I");
        spawnWithGuideMk2 = spawnItem("guideMk2", "the Wayfarer's Guide, Mk II");
        spawnWithGuideCommands = spawnItem("guideCommands", "the Commands guide");
        spawnWithGuideGolem = spawnItem("guideGolem", "the Golem of Ways guide");
        lootFinds = config.getBoolean(
            "lootFinds",
            CATEGORY_LOOT,
            true,
            "Whether the mod's things turn up in world-generated chests - its tools, guide books, draughts, the golem and its upgrades, and enchanted books carrying its unlock enchantments. Additive: nothing that was already findable stops being findable, and the weights below are small enough that every other item keeps very nearly the odds it had. Off, the mod adds nothing to any chest. This is the one switch over all of them, and integration.gtnhEnhanced has no say: nothing a chest is given needs a GregTech pack, so a pack set to behave as plain Forge finds them too. Each find also has a weight below, where 0 leaves out whatever that weight covers, and three weights cover a group rather than one find: loot.enchantBook is all three unlock books, loot.guideBookRare the Mk II, Commands and Golem guides, and loot.golemUpgrade every ordinary golem upgrade, so none of those can be left out alone by weight - a single unlock book goes only by turning its enchantment off, which takes the enchantment out entirely. Every find also follows the switch of the feature it belongs to. Read once, as the game starts: a change takes a restart, /trmt reload alone neither adds nor removes anything, and a chest already generated keeps what it was filled with.");
        lootWeightTamper = lootWeight("tamper", 3, "a plain tamper, in dungeons, mineshafts and a smith's chest");
        lootWeightChunkTamper = lootWeight("chunkTamper", 1, "a chunk tamper, in dungeons and strongholds");
        lootWeightWayfarer = lootWeight(
            "wayfarer",
            1,
            "the Wayfarer's tamper, in a stronghold library unless loot.wayfarerCategories names somewhere else - the rarest tool here");
        lootWeightGuide = lootWeight("guideBook", 5, "the Wayfarer's Guide Mk I, which is meant to be found early");
        lootWeightGuideRare = lootWeight(
            "guideBookRare",
            2,
            "each of the Mk II, Commands and Golem guides, all three at this one weight,");
        lootWeightEnchantBook = lootWeight(
            "enchantBook",
            1,
            "each of the enchanted books carrying the mode unlocks, all three at this one weight,");
        lootWeightUpgrade = lootWeight(
            "golemUpgrade",
            2,
            "each of the golem's ordinary upgrades, all at this one weight,");
        lootWeightDraughtLight = lootWeight(
            "draughtLightness",
            2,
            "a Draught of Lightness, in dungeons, mineshafts and a smith's chest");
        lootWeightDraughtHeavy = lootWeight(
            "draughtHeavyFoot",
            1,
            "a Draught of the Heavy Foot, which is the stranger of the two to come across");
        lootWeightOmni = lootWeight("golemOmniUpgrade", 1, "the All Ways upgrade, which is every other one at once");
        lootWeightGolemEgg = lootWeight("golemEgg", 1, "a dormant Golem of Ways - the rarest thing the mod generates");
        golemEnabled = config.getBoolean(
            "enabled",
            CATEGORY_GOLEM,
            true,
            "Whether the Golem of Ways exists at all - whether it can be built, spawned, or do any work. Off, the shape does nothing and any golem already standing simply idles.");
        golemUpgrades = config.getBoolean(
            "upgrades",
            CATEGORY_GOLEM,
            true,
            "Whether golem upgrades exist. Off, none are craftable and the golem's screen has no upgrade slot at all - not a slot that refuses things, no slot.");
        golemIdlePoses = config.getBoolean(
            "idlePoses",
            CATEGORY_GOLEM,
            true,
            "Whether a golem standing about shows you why. Three postures, each on a different part of it: head bowed and the body's sway all but stopped when it has been told nothing at all; its own tamper plate cast out from the hip when it has orders and not the means to carry them out; and a slow shift of weight from one foot to the other when the round is walked and the road needs nothing. Each takes about three seconds to arrive, so a golem between two jobs never strikes an attitude about it. Off, it goes back to rehearsing a third of its work stroke whenever it is holding a tamper with nothing to do, which is what it did before these were added.");
        golemPickup = config.getBoolean(
            "pickUpDrops",
            CATEGORY_GOLEM,
            true,
            "Whether the golem keeps what its own work shakes loose - the seeds, the flint, the snowballs - putting them in its storage while there is room and leaving them on the ground when there is not. It holds them for you to take out; you cannot put them back, because its slots only accept the tampers and the ground it mends with. A hopper or a dropper may put in only that ground, and never into its last empty slot, which is kept for what the golem picks up and for a tamper to replace one that breaks; and no hopper or hopper minecart can take anything out of it, these included. What it will not do is sweep up a battlefield: a mob drop is neither its earnings nor its materials, which keeps a golem that has been fighting from filling with rotten flesh and having nowhere left to put its gravel. Reinforcing rides on this too: a golem only notices reinforcing material thrown down while it is picking things up, so off here quietly switches golem.reinforces off with it.");
        golemBaseRadius = config.getInt(
            "baseRadius",
            CATEGORY_GOLEM,
            16,
            1,
            64,
            "How far from its anchor a golem works without the range upgrade.");
        golemMaxRadius = config.getInt(
            "maxRadius",
            CATEGORY_GOLEM,
            64,
            1,
            64,
            "The furthest a golem can ever be told to work, with every upgrade fitted.");
        // Two texts, chosen by GolemWork.PRICED_FROM_THE_CHUNK_TAMPER, because the price has two forms and one
        // line chooses between them: turning it back cannot leave the file describing the price it no longer is.
        golemBlockCost = config.getInt(
            "blockCost",
            CATEGORY_GOLEM,
            2,
            1,
            16,
            (com.trmtgtnh.entity.GolemWork.PRICED_FROM_THE_CHUNK_TAMPER
                ? "How many blocks a golem spends on one purchase of mending. A golem is priced the way the chunk tamper is: one purchase pays for general.chunkTamperGradationsPerBlock gradations of one kind of ground, rounded up for each kind on each stroke. So at the default of 2 a golem working a chunk tamper or the Wayfarer's spends two blocks wherever a chunk tamper in a player's hand would spend one - 114 blocks for a fifteen-by-fifteen layer of one kind of ground mended one gradation deep, where the chunk tamper spends 57 - and a golem working a plain tamper, which puts back a single gradation a stroke, spends two blocks for every gradation: twice the hand tamper's single-square mend at general.tamperMendCost's default of one. The golem never reads tamperMendCost, so changing that moves only the player's side of the comparison. A stroke that finds fewer gradations of some ground than one purchase pays for still buys a whole purchase, so ground a golem keeps up a gradation or two at a time costs it close to this figure for every gradation, whatever tamper it holds. The frugality upgrade halves this figure, rounding down and never below one: at the default that brings a golem level with a player doing the same work with a plain tamper or a chunk tamper at those tools' own defaults, at 3 it comes down to one as well, and at 1 it saves nothing."
                : "How many blocks a golem spends for every gradation of wear it puts back, whatever tamper it is holding. At the default of 2 that is twice the hand tamper's single-square mend at general.tamperMendCost's default of one, and up to eight times what a chunk tamper costs a player at general.chunkTamperGradationsPerBlock's default of four: the chunk tamper rounds a whole area of one kind of ground together, and a golem pays for every gradation on its own. The golem reads neither tamperMendCost nor chunkTamperGradationsPerBlock, so changing either moves only the player's side of the comparison. The frugality upgrade halves this figure, rounding down and never below one, and at 1 it saves nothing.")
                + " A golem is never free: the Wayfarer's Tamper costs a player nothing, and so does a chunk tamper with general.bonemealCostsABlock off, but neither reaches a golem, because a keeper that cost nothing to run would never need looking after. What pays is bone meal's rule - a block of the ground itself, whatever families.<name>.repairBlocks names, or with healing.repairAnyInFamily on any block of the same family - taken from the golem's own storage first and then, with the settled upgrade, from the containers around its anchor.");
        golemCombat = config.getBoolean(
            "combat",
            CATEGORY_GOLEM,
            true,
            "Whether a golem defends the ground it keeps. With a tamper in its storage it hits back at whatever hits it and steps in front of a player or a villager a monster has gone for - monsters only, never a player, a villager, a pet or another golem, and never a creeper or a ghast, one of which it would blow up its own road over and the other of which it cannot reach. It never goes looking: those two things are the whole of it. Without a tamper it runs from monsters instead, creepers included, which is the same emptiness that stops it working at all. Off, none of this happens and a golem does nothing but its round.");
        golemAttackDamage = config.getFloat(
            "attackDamage",
            CATEGORY_GOLEM,
            4.0f,
            0f,
            20f,
            "What one of its blows takes off, in half-hearts, once a second. Deliberately well under the iron golem's seven to twenty-one: this one has sixty health and cannot be knocked back, so it is meant to win by outlasting a monster rather than by killing it quickly, and a road-mender that cleared a spawner would have stopped being a road-mender. Flat whatever tamper it is holding, because the grade list is yours to edit and a damage curve drawn over it would move under anybody who only meant to add a metal. Every landed blow costs the tamper a use, the same as a stroke of work does, so a golem left to fight spends the tools you gave it to work with. 0 leaves it shoving monsters about without hurting them. The fierce upgrade doubles whatever this says, so lowering it lowers that too. The stout upgrade is not on this dial at all - it doubles the golem's health, which is a flat sixty and has no setting of its own.");
        mapTracksWear = config.getBoolean(
            "mapTracksWear",
            CATEGORY_SURFACES,
            true,
            "Whether a map draws worn ground differently from ground nobody has crossed. On, a worn square is drawn in a color that has travelled toward whatever that ground is turning into - a turf path leaves green and arrives at earth in step with how far along its run it has walked - and darkened by how heavily it has been used. Which is a map that answers 'where do people go' as well as 'what is this made of', and it is why a road shows up on a minimap at all. Off, every square is drawn as the material it started as, and the darkening a world-reading map is given through client.mapWearThroughTint goes with it. One correction still reaches such a map with this off, because it is not wear: a modded turf has its tint put right, so a path through it does not read as a green stripe. What a map with no per-position color handler can do is coarser and cannot be helped: the vanilla map item works from a fixed palette of sixty-four colors with no darker sibling to pick, so it is given the right material and nothing about how worn it is. JourneyMap is asked per position and gets all of it; a map that reads the world, Xaero's Minimap among them, gets the darkening.");
        mapWearDarkening = (float) config.get(
            CATEGORY_SURFACES,
            "mapWearDarkening",
            0.62d,
            "How far a fully worn square is darkened on a map, as a fraction of the color it would otherwise be. The darkening is spread evenly over every gradation the ground has - eighty for most families - so this also sets how much one step of wear is worth: at the default, about eight tenths of one per cent each. Raise it to tell the levels apart more easily, at the cost of a worn road reading as a darker material rather than as the same material worn. There is a floor on what can be shown either way: eight-bit color has only so many values between a block's own shade and a fraction of it, and on already-dark ground several gradations will land on the same one however wide this is set. Nought means no darkening at all, and now genuinely does: it used to be read as a request for the default, so a pack that turned this off silently got it back.",
            0.0d,
            0.9d)
            .getDouble();
        golemTargetMemory = config.getInt(
            "targetMemory",
            CATEGORY_GOLEM,
            900,
            10,
            7200,
            "How long a golem's list of squares that want doing stands before it looks at its whole radius again, in seconds. The list is also thrown away the moment the last square on it is finished, whichever comes first, so this only matters to a golem that still has work it cannot reach or cannot pay for. Making the list is the expensive part - sixteen thousand columns for a golem keeping sixty-four blocks - so this is the dial that decides how often that is paid for.");
        golemScanCooldown = config.getInt(
            "scanCooldown",
            CATEGORY_GOLEM,
            10,
            1,
            600,
            "The least time between two full sweeps of a golem's ground, in seconds. Without it, a golem standing in a finished field would sweep its whole radius every stroke looking for work that is not there, which is the one case where the search costs far more than the work. Raise it on a server with many golems; it only ever delays a golem noticing new work, and only for this long.");
        golemTargetLayers = config.getInt(
            "targetLayers",
            CATEGORY_GOLEM,
            3,
            1,
            16,
            "How many rings of equally distant work a golem will choose between when it picks somewhere to go. The list is sorted by rounded distance from its home, and each time it chooses it takes a number of rings between one and this, then picks at random from everything in them. At 1 it always takes the nearest work and sweeps the ground in a visible spiral; at 3 it keeps close to home while looking like a thing deciding where to go next. Large numbers let it wander to whatever it likes.");
        golemStoutHealTicks = Math.round(
            readFloat(
                CATEGORY_GOLEM,
                "stoutHealDays",
                1.0f,
                0f,
                100f,
                "In-game days a golem carrying the stout upgrade takes to put one point of health back, or 0 for none. Only the stout ones, and the all-ways golem that carries every upgrade at once; nothing else mends at all. Deliberately slow - the upgrade doubles a golem's sixty health to a hundred and twenty, so at the default one knocked down to half its bar is sixty in-game days getting back to full, and twenty hours of that spent somewhere loaded - because the point of the upgrade is health to spend standing somewhere, not health that comes back between strokes. Counted off the golem's own age, so one in an unloaded chunk mends nothing while it is not there.")
                * 24000f);
        golemReinforces = config.getBoolean(
            "reinforces",
            CATEGORY_GOLEM,
            true,
            "Whether a golem picks up reinforcing material thrown down beside it and lays it into its own round. One piece at a time, chewed through with the empty container handed straight back, and one square of its round taken up a level for each - so a whole stack can still be handed over at once and left with it, and it paces itself through the pile. It will not take a bite unless it has orders, a tamper, and a square wanting reinforcement, because the container goes back before the material is laid and it cannot hand back what it has already swallowed. What counts as material is whatever reinforce.materials names - the same list a tamper is paid from, which on a pack with GregTech means a bucket of concrete and on plain Minecraft means obsidian - so this needs nothing of its own and follows that list wherever it is pointed. A golem that can find nowhere left to reinforce takes no bite at all and waits a little before looking again; a mouthful it has already chewed is held until a square wants it, and falls with the rest of what it carries only if it dies. It rides on golem.pickUpDrops as well: a golem notices thrown material only while it is picking things up, so with that off this does nothing whatever it says. Off, thrown material is ignored and reinforcing stays a thing done by hand.");
        golemWorkReach = config.getInt(
            "workReach",
            CATEGORY_GOLEM,
            4,
            1,
            16,
            "How far from itself a golem can reach to work a square, in blocks. It works everything within this of where it is standing and then walks to the next thing that needs doing, which is why a golem paces its road rather than standing still and rewriting ground sixty blocks away. Its work radius still says which ground is its business; this says how much of that is within arm's length at any moment. Large numbers turn it back into a thing that tends a whole field from one spot.");
        golemAreaStorage = config.getBoolean(
            "areaStorage",
            CATEGORY_GOLEM,
            true,
            "Whether a golem carrying the Settled Ways upgrade may use the containers around its anchor: taking a tamper or a block to mend with when it has run out, and putting its surplus back when it is full. It only ever puts something into a container that is already holding that same thing, so it can never decide for itself where anything ought to live, and it only reaches into what a hopper could reach into - so barrels, drawers and modded chests all work and none of them is named anywhere in this mod. Off, the upgrade is still craftable and still fits, and does nothing.");
        golemStoreReach = config.getInt(
            "storeReach",
            CATEGORY_GOLEM,
            8,
            0,
            64,
            "How far past the ground it keeps a settled golem will reach for a container, in blocks. Its round plus this, the same shape as the guard range - because a chest sitting just off the edge of the ground a golem tends is a chest you would expect it to use. Zero confines it to exactly its own round. Larger numbers cost a wider scan every few seconds; the scan skips any chunk that is not loaded, so this can never pull terrain into memory.");
        golemGreenBand = config.getInt(
            "greenBand",
            CATEGORY_GOLEM,
            8,
            1,
            64,
            "How far either side of its order a golem carrying the Green Ways upgrade drives the ground, in gradations of that family's own run. The order stops being a place to stop and becomes the middle of a band: the golem wears its whole round down to one edge, turns when it finds nothing left to do, mends it all back up to the other, and round again. Eight is about one physical level for most families, so a round breathes by roughly a level in each direction. Larger bands take longer to turn and shed more each time; a band of one is a golem fidgeting.");
        golemWearDropChance = readFloat(
            CATEGORY_GOLEM,
            "wearDropChance",
            0.5f,
            0f,
            1f,
            "The chance that a golem working ground down sheds something, rolled once each time a square physically drops a level rather than once per gradation. Its own number rather than the one traffic uses, because the two are different units: a footstep moves a shade and a golem takes a whole level off in one gesture. What it sheds is whatever that ground sheds - seeds from turf, flint from gravel, snowballs from snow - and it goes straight into the golem's own store, falling to the floor only when there is no room left. Zero stops it entirely; the master switch is still wearDrops in general.");
        golemRoamFloor = config.getInt(
            "roamFloor",
            CATEGORY_GOLEM,
            16,
            1,
            128,
            "The least ground a golem will move over, in blocks from its home, whatever its round is narrowed to. One number bounds four things - how far it will step to meet something, how far it will chase, how far it wanders when idle, and how far out it will walk itself home from - so the tightest of the four decides all of them, and a golem told to keep a single square would otherwise be penned into nine blocks and stand perfectly still on one. Narrowing the round is meant to say what it keeps, not to nail its feet down.");
        golemUnstableOmni = config.getBoolean(
            "unstableOmni",
            CATEGORY_GOLEM,
            true,
            "Whether the nine upgrades bind into an Unstable All Ways rather than straight into the settled one. The unstable part carries the whole set and keeps only three to nine of them at a time, choosing again every minute, with the storage always among them so nothing the golem is carrying can be stranded by the roll - and it wears its name the way it wears the set, which is badly. A nether star settles it into the ordinary All Ways upgrade afterwards. Off, the nine parts craft straight to the settled version and the unstable one is never made, though anything already holding one keeps working.");
        golemUnstablePeriod = config.getInt(
            "unstablePeriod",
            CATEGORY_GOLEM,
            1200,
            20,
            24000,
            "How long an unstable golem holds on to one set of upgrades, in ticks. Twelve hundred is a minute, which is long enough to notice what it currently is and short enough that it is never that for long. Raising it makes an unstable golem nearly as good as a settled one; lowering it makes it useless for anything but watching.");
        golemGuardRange = config.getInt(
            "guardRange",
            CATEGORY_GOLEM,
            8,
            4,
            64,
            "How far past its work radius a golem will stand. It bounds the search for a fight, the chase, and its idle wandering, so a golem no longer drifts off the ground it was set on and a monster that leads it over that line is let go. Running away is the one thing not bounded by it - a retreat that had to stay inside the thing you are retreating around would not be a retreat - but an unarmed golem walks back to its anchor once the monster has gone. Small numbers keep it tight to its road; large ones let it range, at the price of finding it somewhere you did not leave it.");
        golemBodyTop = config.get(
            CATEGORY_GOLEM,
            "bodyTop",
            new String[] { "ExtraUtilities:cobblestone_compressed:3", "minecraft:stonebrick" },
            "The block of packed stone that forms the golem's upper body, tried in the order written. Entries outside vanilla are skipped while the GTNH enhancements are off, so a pack asking to behave as plain Forge builds the plain shape even if the compressed blocks happen to be installed.")
            .getStringList();
        golemBodyBottom = config
            .get(
                CATEGORY_GOLEM,
                "bodyBottom",
                new String[] { "ExtraUtilities:cobblestone_compressed:11", "minecraft:dirt" },
                "The block of packed earth that forms the golem's lower body, tried in the order written.")
            .getStringList();
        golemArms = config
            .get(
                CATEGORY_GOLEM,
                "arms",
                new String[] { "ExtraUtilities:cobblestone_compressed:10", "minecraft:dirt" },
                "The blocks that form the golem's two arms, either side of its upper body, tried in the order written.")
            .getStringList();
        lootWayfarerCategories = config.get(
            CATEGORY_LOOT,
            "wayfarerCategories",
            new String[] { "strongholdLibrary" },
            "Which chest categories the Wayfarer's Tamper may be generated in. A category here is just the name a world generator asks for, so a pack can name its own End or dungeon structures' categories and the Wayfarer will turn up in them. This version of the game ships no End structure with a category of its own, which is why the default is the stronghold library - the rarest vanilla pool, and the room the End portal stands in. A name is matched exactly, case included, and a wrong one is refused nowhere: Forge makes an empty pool for a name nobody uses, and a Wayfarer filed only there would never be found. So as a server starts - in single player, as the first world is opened - the log warns about each name whose pool holds nothing but the Wayfarer and has no chest size, which is how a pool no other mod has touched looks, and when that is every name here the Wayfarer is filed in the stronghold library as well. A mod that fills its own pool only later than that is warned about too, and if every name is one of those the Wayfarer turns up in the library besides. What this cannot see is a real pool the world never draws from: bonusChest is filled only in a world made with a bonus chest, and a server with structures switched off makes no stronghold, mineshaft, village or temple chests - the library included - and a mod with its own structure switched off makes none of its own, so a Wayfarer named only in those is then found nowhere, without a word; dungeon chests are made either way, so a name such as dungeonChest still gives it out. A name written twice is filed twice and doubles the Wayfarer's odds there, which is the one way to weight one place above another. Empty means the library. Taken up once, as the game starts, so a change waits for a restart. A loot.wayfarer weight of 0 is the setting that leaves it out on purpose.")
            .getStringList();
        demoContainer = repointDemoContainer(
            config.get(
                Configuration.CATEGORY_GENERAL,
                "demoContainer",
                DEMO_CONTAINER,
                "What the demonstrate command puts one of every item into, tried in the order written and settling on the first this pack can supply. Entries may name a metadata as 'mod:block:meta', which for Iron Chest is what decides which chest it is and therefore how many slots it has - the default asks for the Dark Steel Chest, which holds the whole list at once. Where a container is still too small a second is placed beside it, which for a vanilla chest is a double chest. Anything outside vanilla is skipped when the GTNH enhancements are off."));
        achievements = config.getBoolean(
            "achievements",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether the mod adds its own achievement page - sixteen of them with everything below switched on: the three tampers, the three modes an enchantment unlocks and the enchanted book that carries each one's lesson, the four guide books and the shelf for having read every one of them, standing a golem up, and the All Ways upgrade. Fewer where a pack cannot reach one of them. A mode and its book are registered only where that enchantment is both switched on and actually holding an id - the ids are claimed whatever the switches say, so that a tool already carrying one does not break when somebody turns the feature off, which means a claimed id alone proves nothing about whether anybody can use it. The golem and its upgrade go the same way. An achievement nobody in the pack can earn would sit on the page for ever with no way to clear it. Off, nothing is registered and no achievement is ever awarded.");
        questbookNotice = config.getBoolean(
            "questbookNotice",
            CATEGORY_INTEGRATION,
            true,
            "Whether an operator entering a world whose questbook has no chapter for this mod is told so, once, with the command that adds it. Only when it is actually true: the world's own quest database is read as it loads, and nothing is said on a world that already has the chapter, on a world new enough to have taken it as it was made, or in a pack that has told BetterQuesting to re-import its defaults on every load and will therefore take it unaided. Off, the chapter is still written for any world that has not yet been made; nobody is simply told about the ones that have.");
        questbook = config.getBoolean(
            "questbook",
            CATEGORY_INTEGRATION,
            true,
            "Whether a quest line for this mod is written into config/betterquesting/DefaultQuests for BetterQuesting to pick up - seventeen quests covering wearing ground down, mending it, all three tampers, all three enchantments, the four books, the golem and its upgrades, with loot bag and Pam's food rewards pitched to sit alongside the pack's own. Fewer where a pack cannot reach one: a quest whose item does not exist is left out, and so is an enchantment's quest with reinforce.enabled, spawnward.enabled or pathlight.enabled off or with no free id for it to sit at, the golem's quests other than its book with golem.enabled off, and the All Ways quest with golem.upgrades off. Nothing is written unfinishable, whatever depended on a quest left out re-parents onto what is left, and the Wayfarer's Tamper offered as the last quest's prize carries only the enchantments the chapter still teaches. Turning one of those switches either way rewrites the folder at the next start, but only the folder: a world that has already taken the chapter keeps the quests it was given, and whether importing it again takes away one since left out is BetterQuesting's to decide. The write is additive: two new folders, one appended line in the tab order, and no existing quest file touched. Nothing is loaded into a running world - run /bq_admin default load when you want it. Requires the GTNH enhancements above and BetterQuesting to be installed.");
        trophies = config.getBoolean(
            "trophies",
            CATEGORY_INTEGRATION,
            cpw.mods.fml.common.Loader.isModLoaded("amazingtrophies"),
            "Whether trophy definitions are written into config/amazingtrophies/trophies/trmtgtnh for Amazing Trophies to pick up - seven of them, one each for the Wayfarer, each of the three unlock enchantments bound into a book, standing a golem up, the All Ways upgrade, and reading every guide book. Fewer where a pack has no part for one: a trophy whose achievement does not exist, because its feature is switched off, its item never registered or its enchantment found no free id, is left out rather than written unearnable. Inside that mod's own folder because that is the only place it looks, in a subfolder of this mod's own so nothing written here can collide with the pack's; it reads the whole tree and ignores anything that is not a .json. While trophies are being written the folder carries a fingerprint of what was written and is rewritten whenever that changes, so the list cannot go stale. The GTNH enhancements above are not asked, so a pack with them off still gets every trophy. Requires the achievements above, since every trophy is earned by earning one, and Amazing Trophies to be installed; on without either, nothing is written. Turning this off, or the achievements, stops the writing and takes nothing back: a folder an earlier launch wrote stays where it is and Amazing Trophies goes on reading it, trophies whose achievements no longer exist included, so delete that folder as well to take them away. The default is decided the first time this file is written - on when Amazing Trophies is installed - and then kept, so a pack that ran once before Amazing Trophies was added has this set false and writes nothing until it is turned on here.");
        graceSeconds = config.getInt(
            "blockBreakGraceSeconds",
            Configuration.CATEGORY_GENERAL,
            900,
            0,
            86400,
            "How long, in REAL seconds, a broken block's record - its wear, its reinforcement, its spawn ward - is held in case the same block is put back. Put the exact same block back at the exact same spot inside this window and everything it carried returns; put a different block there and the record simply waits, so swapping a block out and back still works. After the window it is dropped for good. 0 turns this off and a break forgets immediately.");
        wardEnabled = config.getBoolean(
            "enabled",
            CATEGORY_WARD,
            true,
            "Whether the spawn-ward feature exists at all: the enchantment that unlocks it, the tool mode, and the mob-spawn barring it grants. Off, the enchantment still registers so a save that has it on a tool does not lose an id, but the mode and its gestures do nothing, and nothing new of it is handed out: an enchanting table will not roll it onto a book or offer it for a tamper, a librarian will not offer it in a new trade, and the book's recipe, its chest finds, its two achievements, its trophy and its quest are left out. The mode, the table and librarians follow a change straight away, from /trmt reload or the config screen. The recipe, the chest finds, the achievements, the trophy and the quest are settled as the game starts and follow a change either way only after a restart, so until then a switch turned off still leaves the book craftable and findable, and one turned on leaves it neither. Nothing already made is taken back: a librarian who already sells the book keeps that trade, which is saved with the villager, a chest that already holds one keeps it, a world that has already taken the quest chapter keeps its quests, and a book of it will not go onto a tool while this is off, except by a player in creative mode. Another mod that picks enchantments from the game's book list for itself, rather than through an enchanting table or the game's librarian, is not held to this. The Waila readout reads this machine's own copy, so a client with it off shows no ward even on a server that has it on.");
        wardEnchantId = config.getInt(
            "enchantId",
            CATEGORY_WARD,
            -1,
            -1,
            255,
            "The id the spawn-ward enchantment registers at. -1 takes the first free slot from the top at startup - fine for one pack, but it drifts if the mod set changes, and the id is written into the enchanted tool, so a drift strips it off existing tools. Pin it to a fixed free number on a pack you keep.");
        wardCostCount = config.getInt(
            "cost",
            CATEGORY_WARD,
            2,
            0,
            64,
            "How many of the material barring one category of mob from one block costs. The Wayfarer's tamper pays half of this. Letting a category back is free but for a scratch of durability. 0 makes barring free.");
        wardHostileMaterials = config.get(
            CATEGORY_WARD,
            "hostileMaterials",
            new String[] { "minecraft:ender_eye" },
            "What barring hostile mobs from a block costs, tried in the order written and paid with the first the player has enough of. A 'modid:name[:meta]' entry is that item. The default is an eye of ender per unit of 'cost'.")
            .getStringList();
        wardPassiveMaterials = config.get(
            CATEGORY_WARD,
            "passiveMaterials",
            new String[] { "minecraft:golden_carrot" },
            "What barring passive mobs from a block costs, tried in the order written and paid with the first the player has enough of. A 'modid:name[:meta]' entry is that item. The default is a golden carrot per unit of 'cost'.")
            .getStringList();
        lightEnabled = config.getBoolean(
            "enabled",
            CATEGORY_LIGHT,
            true,
            "Whether path lighting exists at all: the enchantment that unlocks it, the tool mode, and the glow itself. Off, the enchantment still registers so a save that has it on a tool does not lose an id, but the mode and its gestures do nothing, no block glows, and nothing new of it is handed out: an enchanting table will not roll it onto a book or offer it for a tamper, a librarian will not offer it in a new trade, and the book's recipe, its chest finds, its two achievements, its trophy and its quest are left out. The mode, the table and librarians follow a change straight away, from /trmt reload or the config screen. The recipe, the chest finds, the achievements, the trophy and the quest are settled as the game starts and follow a change either way only after a restart, so until then a switch turned off still leaves the book craftable and findable, and one turned on leaves it neither. Nothing already made is taken back: a librarian who already sells the book keeps that trade, which is saved with the villager, a chest that already holds one keeps it, a world that has already taken the quest chapter keeps its quests, and a book of it will not go onto a tool while this is off, except by a player in creative mode. Another mod that picks enchantments from the game's book list for itself, rather than through an enchanting table or the game's librarian, is not held to this. Only ground that is already worn can be lit, because the glow is emitted by the ghost this mod paints over worn ground - so this lights paths, not walls.");
        lightEnchantId = config.getInt(
            "enchantId",
            CATEGORY_LIGHT,
            -1,
            -1,
            255,
            "The id the path-light enchantment registers at. -1 takes the first free slot from the top at startup - fine for one pack, but it drifts if the mod set changes, and the id is written into the enchanted tool, so a drift strips it off existing tools. Pin it to a fixed free number on a pack you keep.");
        lightLevel = config.getInt(
            "level",
            CATEGORY_LIGHT,
            12,
            1,
            15,
            "How brightly a lit block glows. For scale: a torch is 14, glowstone 15, and 8 is the threshold below which hostile mobs will still spawn - so a road lit under that is a lit road that things still crawl onto.");
        lightCostCount = config.getInt(
            "cost",
            CATEGORY_LIGHT,
            1,
            0,
            64,
            "How many of the material lighting one block costs. The Wayfarer's tamper pays half. Recoloring an already-lit block and putting one out are free but for a scratch of durability. 0 makes lighting free.");
        lightMaterials = config.get(
            CATEGORY_LIGHT,
            "materials",
            new String[] { "minecraft:glowstone_dust" },
            "What lighting a block costs, tried in the order written and paid with the first the player has enough of. A 'modid:name[:meta]' entry is that item. The default is a glowstone dust per unit of 'cost'.")
            .getStringList();
        tamperGrades = config.get(
            Configuration.CATEGORY_GENERAL,
            "tamperGrades",
            new String[] { "iron:ingotIron:512:1", "gold:ingotGold:256:3", "diamond:gemDiamond:2048:2",
                "netherite:ingotNetherite:4096:3", "bronze:ingotBronze:768:1", "steel:ingotSteel:1536:2",
                "aluminium:ingotAluminium:2048:2", "stainlesssteel:ingotStainlessSteel:4096:3",
                "titanium:ingotTitanium:8192:3", "tungstensteel:ingotTungstenSteel:16384:4",
                "neutronium:ingotNeutronium:32767:4", "copper:ingotCopper:384", "tin:ingotTin:300",
                "lead:ingotLead:384", "nickel:ingotNickel:640", "zinc:ingotZinc:384", "silver:ingotSilver:512",
                "electrum:ingotElectrum:480", "invar:ingotInvar:900", "cupronickel:ingotCupronickel:640",
                "brass:ingotBrass:512", "wroughtiron:ingotWroughtIron:512", "tungsten:ingotTungsten:2600",
                "cobalt:ingotCobalt:1100", "chrome:ingotChrome:900", "nichrome:ingotNichrome:1200",
                "kanthal:ingotKanthal:1400", "tungstencarbide:ingotTungstenCarbide:9000", "hssg:ingotHSSG:5120",
                "hsse:ingotHSSE:7000", "hsss:ingotHSSS:8000", "damascussteel:ingotDamascusSteel:2600",
                "osmium:ingotOsmium:3400", "iridium:ingotIridium:12000", "platinum:ingotPlatinum:2000",
                "palladium:ingotPalladium:2200", "manganese:ingotManganese:512", "tantalum:ingotTantalum:1800",
                "molybdenum:ingotMolybdenum:1600", "vanadiumsteel:ingotVanadiumSteel:3200",
                "darksteel:ingotDarkSteel:2400", "redsteel:ingotRedSteel:3000", "bluesteel:ingotBlueSteel:3600" },
            "The grades a tamper and a chunk tamper can be made at, as name:oreName:uses:reach - reach is optional and is worked out from durability when it is left out, so a three-field entry is still valid. Any whose ore name the pack does not supply is skipped, so this same list works on plain vanilla and on GregTech. Durability is capped at 32767 whatever is written here, because a stack's damage is a short on disk and a larger number wraps negative the first time the world saves. The grade rides in the item's own data, so adding or removing one never touches a save's id map. The Wayfarer's tamper is built around a chunk tamper of one of these grades: netherite where integration.gtnhEnhanced is on and a netherite chunk tamper can be made, diamond where one can, and otherwise whichever grade that can be made has the most uses written against it, the first of equals in this list winning. So taking the diamond line out, or pointing it at an ore this pack does not supply, moves the Wayfarer onto another metal rather than leaving it uncraftable - and on the shipped list that is neutronium wherever the pack registers its ingot. It goes without a recipe, with a warning in the log, only where no grade here can be made into a chunk tamper or nothing is registered as blockDiamond. The grades are worked out once, as the game or server starts: /trmt reload and the settings screen read a changed list into memory, but no tamper, recipe or grade follows it until the next start.")
            .getStringList();
        tamperPatchCost = config.getInt(
            "tamperPatchCost",
            Configuration.CATEGORY_GENERAL,
            2,
            0,
            64,
            "What one right-click mend costs, in blocks of whatever the worn ground drops - earth under grass, cobble under stone. That gesture mends a scattered patch the size of the tamper's tier, which is why it costs more than the single-square mend. The price is charged once for each kind of ground the patch actually mends, taken as the first gradation of it goes back: a patch across a grass verge and a cobble road costs this in earth and this again in cobble, a stone floor beside a cobble road pays it twice although both are mended with cobble, cobble bought for one square never pays for mossy cobble or granite beside it, a square whose material you are not carrying is left as it was with a line in chat to say so, and a patch that mends nothing costs nothing. 0 makes this mend free, and free mending earns no experience, so at 0 this gesture never does.");
        tamperMendCost = config.getInt(
            "tamperMendCost",
            Configuration.CATEGORY_GENERAL,
            1,
            0,
            64,
            "What one sneak + left-click mend costs, in blocks of whatever the worn ground drops, taken once the gradation has actually gone back. That gesture mends exactly one gradation of exactly one square. 0 makes it free, and free mending earns no experience, so at 0 this gesture never does: otherwise wearing a square in and mending it out again would be an experience farm with nothing to slow it but the tool.");
        tamperCooldownTicks = config.getInt(
            "tamperCooldownTicks",
            Configuration.CATEGORY_GENERAL,
            10,
            0,
            100,
            "The least time between two left-click gestures on the same square, in ticks. A new square always goes through immediately, so dragging a held click along a route paints it at full speed. This exists for creative, where the client re-sends the click four times a second for as long as the button is down and sends nothing at all when it is let go.");
        tamperCanWear = config.getBoolean(
            "tamperCanWear",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether left-clicking with a tamper wears ground in deliberately. Turning this off leaves the tool able to mend and to pin, so paths can only ever be made by walking them.");
        tamperCanPin = config.getBoolean(
            "tamperCanPin",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether sneak + right-clicking with a tamper pins a square against both wear and healing. A pin holds indefinitely and against everyone, so a server that would rather nobody could freeze its terrain should turn this off.");
        // Retired. The earth showing through worn grass stopped being corrected in the texture when
        // the tint began to be decided where it is known, and nothing has read this since - so a
        // setting that promised a change and made none is taken out of the file, rather than left
        // there describing a correction that no longer exists.
        if (config.getCategory(CATEGORY_CLIENT)
            .containsKey("earthTintCompensation")) {
            config.getCategory(CATEGORY_CLIENT)
                .remove("earthTintCompensation");
        }
        wearCarriesWithDepth = readFloat(
            CATEGORY_CLIENT,
            "wearCarriesWithDepth",
            0.9f,
            0f,
            1f,
            "How much of what a block looks like comes from its total wear rather than the gradation it is currently on. Gradations restart each time the ground sinks a pixel, so at 0 a deep rut looks freshly cut; at 1 only the total counts and the gradations stop reading. Half of each keeps both.");
        sideWearFraction = readFloat(
            CATEGORY_CLIENT,
            "sideWearFraction",
            0.5f,
            0f,
            1f,
            "How worn the sides of a block look next to its top. 0 leaves them clean, 1 wears them as hard as the floor of the rut. The sides follow how far the ground has come overall rather than the gradation it is currently showing, so they only ever get worse.");
        bleedFront = readFloat(
            CATEGORY_MULTIPLIERS,
            "bleedFront",
            0.2f,
            0f,
            10f,
            "Fraction of a step's wear bled onto the block ahead. Softens the leading edge of a path.");
        bleedSide = readFloat(
            CATEGORY_MULTIPLIERS,
            "bleedSide",
            0.5f,
            0f,
            10f,
            "Fraction bled onto each flanking block. This is what gives a path its width.");
    }

    /**
     * The draughts. Read as its own pass rather than folded into the multipliers beside it, because
     * an effect id is a different kind of thing from a rate: it is claimed once at startup, written
     * into saves, and sent over the wire, and burying it among the traffic figures would hide that.
     */
    private static void readPotions() {
        config.setCategoryComment(
            CATEGORY_POTIONS,
            "The two draughts, and the effects they pour. A Draught of Lightness is the original mod's own Potion of Lightness, brought across with its ingredients intact: while it lasts, that walker wears no ground whatever - not the block underfoot and not the ones around it a footstep normally bleeds onto - which is the same total suppression that sneaking gives. A Draught of the Heavy Foot is its opposite and is new here, made the way vanilla makes any opposite, by corrupting the first with a fermented spider eye. The two items are registered whatever the switches here say, and that is deliberate rather than untidy: the item id map is compared between a client and a server when they connect, so a switch that changed which items exist would stop two machines that disagree about it from connecting at all. What these switches turn off is the effect, the recipe and the chance of finding one - never the item. A draught already in a chest with the feature off is an ornament, and drinking it says so.");
        potionsEnabled = config.getBoolean(
            "enabled",
            CATEGORY_POTIONS,
            true,
            "Whether the draughts do anything. Off, both effects stop working, neither recipe is registered and neither turns up in a chest - but both items still exist, because which items exist is compared when a client connects to a server and may not depend on a setting.");
        potionLightness = config.getBoolean(
            "lightness",
            CATEGORY_POTIONS,
            true,
            "Whether a Draught of Lightness exempts its drinker. The exemption is total rather than partial - no wear on the block underfoot, none on the ones a footstep spreads onto, and none from a landing - and it covers whatever the drinker is riding as well, so a splash over a horse genuinely exempts the horse. It does not mend ground that is already worn; recovering is a separate mechanic and this does not hurry it.");
        potionHeavyFoot = config.getBoolean(
            "heavyFoot",
            CATEGORY_POTIONS,
            true,
            "Whether a Draught of the Heavy Foot multiplies its drinker's wear. This one is not in the original mod. Because the factor is applied to the figure that reaches the block underfoot, the blocks a footstep spreads onto and any crop being trodden on, a heavy walker widens a track as well as deepening it - which is more than the words 'more wear' promise, and is worth knowing before turning it up.");
        heavyFootFactor = readFloat(
            CATEGORY_POTIONS,
            "heavyFootFactor",
            4.0f,
            0.0f,
            64.0f,
            "How many ordinary crossings one heavy-footed crossing counts as. At the default of four, a stone road wants sixteen passes a gradation instead of sixty-five, and turf six instead of twenty-four. Nothing is compounded when a heavy rider sits on a heavy mount: two doses is still one heavy thing walking. Nought does not mean no wear: anything at or under nought is read as one, so the draught then does nothing at all.");
        lightnessSeconds = config.getInt(
            "lightnessSeconds",
            CATEGORY_POTIONS,
            180,
            0,
            86400,
            "How long one Draught of Lightness lasts, in seconds. Three minutes is the original mod's own figure, to the tick. Nought makes the draught do nothing: the bottle is still drunk and the effect is gone the next tick.");
        heavyFootSeconds = config.getInt(
            "heavyFootSeconds",
            CATEGORY_POTIONS,
            180,
            0,
            86400,
            "How long one Draught of the Heavy Foot lasts, in seconds. Matched to Lightness, since it is meant to be the same bottle read backwards. Nought makes the draught do nothing, the same way.");
        lightnessPotionId = config.getInt(
            "lightnessPotionId",
            CATEGORY_POTIONS,
            -1,
            -1,
            255,
            "The effect id Lightness registers at. -1 takes the first free slot counting up from 24, which is right for a pack that does not change - but an effect id is written into your save and sent to clients as a single byte, so a server and a client that resolved to different numbers disagree about what was drunk. Where the other side has a different effect at that number it shows the wrong one; where it has nothing there, the effect is simply dropped on arrival and a line in the client's log says so. It used to be worse than either - an effect with nothing behind it threw inside the client's own world tick - and that is guarded now, but agreeing on the number is still the only way to see the right thing. On a pack you intend to keep, pin this to a number you know is spare and pin the same number on every machine. Plain Minecraft leaves 24 to 31 free. Numbers of 128 and above only survive the journey on a pack that widens the byte they travel in, so the automatic search takes everything below 128 first and says so if it cannot.");
        heavyFootPotionId = config.getInt(
            "heavyFootPotionId",
            CATEGORY_POTIONS,
            -1,
            -1,
            255,
            "The effect id Heavy-Footedness registers at, under the same rules and the same warning as the setting above. Lightness is claimed first, so where a pack has exactly one slot left it goes to the faithful effect and this one is skipped. The log says which at startup, and so does /trmt status, which is the quickest way to compare two machines.");
    }

    /**
     * Ground that waits for the weather.
     *
     * <p>
     * Its own pass and its own category rather than folded into healing, because what is being
     * decided here is not how fast ground recovers but whether the sky is allowed to have an
     * opinion about it - and because three of these five exist only to stop the rule quietly
     * meaning never.
     */
    private static void readWeather() {
        config.setCategoryComment(
            CATEGORY_WEATHER,
            "Two different things the sky does to ground, kept under one heading because both of them are weather, and only one of them is waiting to be asked for. The first is snow lying on ground taking the traffic instead of the ground beneath it, which is on out of the box and is what the three snowCover settings decide: walking a snowy road treads the snow away layer by layer, and only once the last of it has gone does what is underneath begin to mark. That half is at work in every world without anything being switched on. The second is ground that mends only while the weather is doing the mending, and that half is off out of the box - a surface waits for weather when its own families.<name>.healsOnlyWhenWet is switched on, and every one of the twelve families ships that off, so until somebody turns one on wetHealingEnabled and wetHealSecondsPerStage decide nothing whatever. The rule they then decide is that elapsed time still banks up exactly as it always did, so a dry spell delays recovery rather than cancelling it and a chunk nobody has visited for a month still holds every day of it - but the bank is only paid out while precipitation is actually falling on that spot, and paid at a rate rather than in a lump, so a storm fills a path in while you stand in it instead of undoing it the instant it starts. The three settings after those - whether ground has to see the sky, whether its biome has to be one that gets weather, and what to do in a dimension that has none - are each there because they name a place where waiting for weather would quietly mean never. Two of them are earning their keep already with every family switch still off, because the same questions decide where rain counts toward the families.<name>.wetRecoverySpeed discount, which ships at two for turf, earth, sand and gravel: narrow either one and a sheltered or a dry path stops earning its faster mending as well as being shut out of the gate. The dimension setting only ever matters to the gate, since rain never falls in a world with no sky for it to fall from.");
        snowCovers = config.getBoolean(
            "snowCovers",
            CATEGORY_WEATHER,
            true,
            "Whether snow lying on the ground takes the traffic instead of the ground under it. On, walking a snowy road treads the snow away layer by layer, and only once the last of it has gone does the ground start to wear - which is what a path through snow looks like, and is not what happened before. What happened before was two different wrong things at once, neither of them chosen: one layer of snow, which is the only depth this game ever lays by itself, has a collision box of no height whatever, so feet landed on the ground beneath and the ground wore at full speed with snow visibly lying on it; two layers or more and the step arrived addressed to the snow, which is not a wearing surface, and was thrown away entirely - shelter that was total, permanent, free, and not even a debt banked against the thaw. Off, both of those come back. Nothing here touches recovery: ground under snow goes on mending at its usual rate, so a path quietly fills in beneath a snowfall, which is the one thing about all this that was already right.");
        snowCoverWear = readFloat(
            CATEGORY_WEATHER,
            "snowCoverWear",
            6.0f,
            0.5f,
            1000.0f,
            "How much walking one layer of snow absorbs before it is trodden away. At the default of six, and with an ordinary crossing worth half of one, that is twelve crossings a layer - so a single fresh fall gives way inside a minute of use while a drift several layers deep takes real effort to break a road through. The snow is taken one layer at a time and the ground beneath is not touched until the last of it has gone. Nothing is dropped when a layer goes: what carried it off was somebody's boots.");
        snowCoverFadeDays = readFloat(
            CATEGORY_WEATHER,
            "snowCoverFadeDays",
            1.0f,
            0.0f,
            365.0f,
            "How many untouched in-game days it takes for a half-trodden track through snow to fill back in, as if it had been snowed over. At the default of one day a route crossed a few times and then left alone is gone by morning, which is the same argument the ground's own partial decay makes: only sustained use should leave a mark. Set to 0 and a half-trodden layer waits for ever, so a single crossing a year would eventually break through. None of this is written to the save - the game lays and melts snow on its own schedule, so a half-trodden layer is a fact with a lifetime measured in minutes, and a layer met again after a reload is met fresh.");
        wetHealingEnabled = config.getBoolean(
            "wetHealingEnabled",
            CATEGORY_WEATHER,
            true,
            "Whether the per-family 'only mends in the wet' switches do anything at all. Off, every one of them is inert: no family waits for weather, whatever its own switch says, which is the quickest way to take that gate back out without editing a switch a family. It does not take the whole of the weather out of recovery, though, and the part it leaves behind runs the other way. The wetRecoverySpeed discount is only ever given to a family that is not already being paid by the rain through the meter, because cheapening a gated family as well would be paying for one storm twice - so switching this off makes every family eligible for that discount rather than fewer, including any that were waiting for weather a moment ago. And since every family ships with its own switch off, the usual effect of turning this off is nothing whatever: turf, earth, sand and gravel are already earning the discount and go on mending in half the untouched days while rain is actually falling on them, with this on or off. To have recovery owe nothing at all to the sky, set every families.<name>.wetRecoverySpeed to one as well.");
        wetHealSecondsPerStage = readFloat(
            CATEGORY_WEATHER,
            "wetHealSecondsPerStage",
            3.0f,
            0.0f,
            600.0f,
            "Seconds of falling weather bought per gradation of recovery. At the default of three, a surface worn all the way through wants four minutes of rain to come all the way back - which is roughly a third of one storm, so a badly worn path takes several. Set to 0 to pay the whole banked debt the moment it starts raining, which is the behaviour without a meter at all. This is real time rather than the recovery already owed: a gradation still costs its family's own healDaysPerStage of untouched days, and this decides how quickly what is already owed is handed over.");
        wetHealingNeedsOpenSky = config.getBoolean(
            "wetHealingNeedsOpenSky",
            CATEGORY_WEATHER,
            true,
            "Whether ground has to be able to see the sky. On, a path under a roof, in a cave or beneath a thick canopy never mends while its family is waiting for weather, because nothing is falling on it - which is right, since nothing washes an indoor floor, and is also a large thing to discover rather than choose. Off, shelter makes no difference and covered ground recovers whenever it rains outside.");
        wetHealingNeedsRainyBiome = config.getBoolean(
            "wetHealingNeedsRainyBiome",
            CATEGORY_WEATHER,
            true,
            "Whether a biome that never has weather is excluded. On, a desert gets no rain and sand in one never recovers - which is awkward, because being filled back in is sand's whole character and what does the filling is wind, which this game does not have. Off, a dry biome counts as wet, so desert sand mends whenever it is raining somewhere in that world. Snow and ice are unaffected either way: those two always ask whether it is cold enough here to be snowing.");
        wetHealingSkipsWeatherlessDimensions = config.getBoolean(
            "wetHealingSkipsWeatherlessDimensions",
            CATEGORY_WEATHER,
            true,
            "What to do in a dimension that has no weather whatever - the Nether, the End, and anything built like them. On, the rule simply does not apply there and ground recovers the ordinary way, which is the safe answer. Off, it does apply, and since it will never rain in either place that means ground there never recovers again for as long as the world exists. That is a legitimate thing to want and it is not something anybody should arrive at by accident.");
    }

    private static void readHealing() {
        healingEnabled = config.getBoolean(
            "enabled",
            CATEGORY_HEALING,
            true,
            "Whether untouched ground recovers over time. Healing is applied lazily from the world clock, so a chunk nobody has visited for a month heals the moment it loads, exactly as if it had been loaded the whole time. Server downtime does not count: the clock is in-game time, not real time. With it off, trample tallies against plants and leaves never fade either.");
        pauseHealingWhenEmpty = config.getBoolean(
            "pauseWhenEmpty",
            CATEGORY_HEALING,
            true,
            "Whether natural recovery stops while nobody is connected. On, the clock healing is measured against is held still for as long as the server sits empty, so a road does not grow back overnight through time no player was there for. Golems are unaffected and that is the point: one left tending a road in a chunkloaded corner goes on working through the night and keeps what it did, which is what makes an unattended golem farm worth building. Wearing is unaffected too, since wear is counted per crossing rather than per second. The same holds for the tallies trampling.vegetation and trampling.leaves keep against plants and leaves: crossings still count while the clock is held and nothing fades, so a creature a chunk loader keeps moving can finish off a plant or a leaf overnight. The pause is a running total saved with the world, so it survives a restart, and it starts at zero on an existing save - the clock only begins to lag once the server has actually sat empty. Off, the clock runs whenever the server does, which is how this behaved before - though switching it off only stops the clock falling further behind, it does not give back time already held out, because that would age every road on the server the instant the switch was flipped. Nothing happens either way in single player.");
        healingRate = config.get(
            CATEGORY_HEALING,
            "healingRate",
            1.0d,
            "How fast ground recovers. 2.0 recovers twice as fast, 0.5 half as fast — the same direction as every other rate in this config, and it multiplies every family at once. Per-family durations live under families.<name>.healDaysPerStage, and each of those is that family's own figure rather than anything worked out from its wear cost. They used to be derived - one constant times the average threshold - which made the table tidy and made it lie: snow and sand cost almost exactly the same traffic to mark and recover nothing like alike, because fresh snow covers a track in under a week and a rut in sand wants a month of wind. Out of the box a fully worn path takes six in-game days to disappear on snow, thirty on sand, forty-five on gravel, a hundred on dirt, a hundred and twenty-five on turf, a hundred and seventy on cobble, thirty-one on ice, three hundred and twenty on stone, and four hundred in the Nether or the End, where there is no weather to fill anything in. The wear table in game draws all of that from these same figures, so it is the place to look after changing one.",
            0.01d,
            1000.0d)
            .getDouble();
        wearDecayPerDay = config.get(
            CATEGORY_HEALING,
            "wearDecayPerDay",
            0.05d,
            "Fraction of the current stage's threshold that bleeds off per untouched in-game day. Without this a route crossed twice a year would still eventually become a path; with it, only sustained use leaves a mark. The same fraction fades the tallies trampling.vegetation and trampling.leaves keep against a plant or a leaf, and nothing else does: at 0.05 a full tally takes twenty in-game days to clear, whatever the speed settings. 0 disables partial decay, so only whole stages ever recover - and since a plant or a leaf has no stages, a tally against one then never fades at all.",
            0.0d,
            1.0d)
            .getDouble();
    }

    private static void readTrampling() {
        trampleVegetation = config.getBoolean(
            "vegetation",
            CATEGORY_TRAMPLING,
            false,
            "Break a plant once enough crossings have gone through it. Out of the box that is tall grass, ferns, flowers, double plants and dead bushes, and detection adds whatever else is made of plant or vine and is not a full block - crops, saplings, sugar cane, mushrooms and vines among them, because what a block is made of cannot tell a field from a meadow; surfaces.exclude is how to spare one. Lily pads are detected too and are never trampled, since they are stood on rather than walked through. Each plant keeps a tally of its own, counted where the feet are rather than on the ground beneath, so a plant can go on a square that still looks untouched. At the shipped figures a plant's tally runs to sixteen to twenty-four, in the wear multipliers.player adds per step: thirty-two to forty-eight crossings on foot, half that mounted, fewer for anything the multipliers heading weights heavier, and general.erosionSpeed and general.globalSpeed divide that exactly as they divide a gradation of ground. The tally fades at healing.wearDecayPerDay, so a plant crossed now and then recovers. At the shipped speeds only a plant crossed more than about twice an in-game day is brought down; the speeds divide that rate along with the tally, so at an erosionSpeed of 8 a plant crossed more often than about once every three to five days goes in the end, and healing.healingRate does not reach the fade at all. With healing.enabled off or healing.wearDecayPerDay at 0 nothing fades, and every plant crossed that many times over any span of time is eventually broken. Nothing fades either while healing.pauseWhenEmpty holds the clock on an empty server, and creatures still count then wherever a chunk loader keeps them moving, so villagers can finish off overnight a plant that would have recovered with somebody online. Villagers and anything on a lead count as they do for ground, and one milling about a doorway or tied to a post treads the same few plants again and again; general.retriggerCooldown holds a creature back here exactly as it does on the ground, which slows that down without stopping it. A plant carrying a reinforcement or a spawn ward is never trampled, and neither is a plant standing in tilled soil unless trampling.groundCoverOnTilled is on, which is what spares a farm. trampling.groundCoverHolds does not apply here, so a sapling or a flower goes the way tall grass does. Nothing is counted while sneaking with general.sneakSuppresses on, under the Draught of Lightness, or outside general.minY to general.maxY, and no new tally is started in a chunk already holding performance.maxEntriesPerChunk records, though one already there goes on counting. No claim or protection mod is asked, because the event those mods listen for has to name a player to blame and this break is made in nobody's name - so on a server with land claims, anybody walking through somebody else's claim wears its plants down as they would their own. This destroys real blocks and is NOT undone by removing the mod, which is why it ships off.");
        trampleLeaves = config.getBoolean(
            "leaves",
            CATEGORY_TRAMPLING,
            false,
            "Wear away the leaf block a route crosses on top of - a way over a canopy, or a bridge of leaves - until it breaks and whatever is standing on it falls, from however high the leaf was. The leaf counted is the one underfoot, since nothing stands inside a leaf, and only the one under the middle of whoever is walking. A floor or a roof of leaves that anyone moves about on goes far sooner than a route across one, because every step from one leaf to the next counts. Anything laid on the leaf - a slab, a carpet - takes the traffic and the leaf keeps no tally; a plant counted as vegetation hanging into the space above it does not, so what the vegetation heading counts decides this too. Snow lying on it is trodden through first when weather.snowCovers is on, as on ground; with that off, snow two layers deep or more shelters the leaf for as long as it lies. The tally, its figures and its fading are those of trampling.vegetation: thirty-two to forty-eight crossings on foot at the shipped speeds, recovery for a leaf crossed less than about twice an in-game day, and no fading with healing.enabled off, with healing.wearDecayPerDay at 0, or while healing.pauseWhenEmpty holds the clock on an empty server. A leaf carrying a reinforcement or a spawn ward is never trampled, which is the way to keep a leaf bridge standing. Creatures count as they do for ground, and a mount under its rider; a multipliers.mobs line of '*' takes in spiders on a canopy and ocelots in a jungle, and general.retriggerCooldown slows one loitering on the same few leaves without stopping it. A broken leaf lets the leaves around it decay exactly as breaking it by hand would. Leaves anybody placed never decay, but a tree holds its leaves only within four leaves of its trunk, and giant jungle trees and big oaks grow canopy beyond that which stands only because nothing has disturbed it - so a route over one can let a good deal more of the canopy around it go than the route itself, and leaves a structure was generated with and no trunk holds can go the whole way. Vines hanging from a broken leaf or clinging to its side come down with it and leave nothing behind. No claim or protection mod is asked, for the reason given under trampling.vegetation. This destroys real blocks and is NOT undone by removing the mod, which is why it ships off.");
        vegetationDropChance = readFloat(
            CATEGORY_TRAMPLING,
            "vegetationDropChance",
            0.2f,
            0f,
            1f,
            "Chance a trampled plant gets to drop what it would drop broken by hand: a flower itself, tall grass its occasional seeds, a crop what its stage gives. 0 means a trampled plant leaves nothing behind - except where it is the foot of a stalk of sugar cane, whose pieces above come down with it and each drop in full, because that is the cane's own rule for standing on nothing - or where it is the top half of a two-block plant, whose lower half then drops in full by vanilla's own rule.");
        leavesDropChance = readFloat(
            CATEGORY_TRAMPLING,
            "leavesDropChance",
            0.1f,
            0f,
            1f,
            "Chance a trampled leaf gets to drop what a decaying leaf would. That is never the leaf block itself: for vanilla leaves it is a sapling one time in twenty, one in forty for jungle, and from oak and dark oak an apple one time in two hundred, while a modded leaf decides for itself - so at the shipped 0.1 a sapling turns up roughly once in two hundred broken leaves. 0 means a trampled leaf never leaves anything behind.");
        groundWearsAway = config.getBoolean(
            "groundWearsAway",
            CATEGORY_TRAMPLING,
            true,
            "Remove a block that has been worn the whole way through, rather than letting it sit at the end of its run taking no further notice of anything. It drops nothing: what wore away was carried off a grain at a time by whatever walked over it, and a rule that paid you for it would be a mining technique. A square must be worn a full threshold PAST the end of its chain before it goes, which is both the grace an older world needs - so nothing already worn out vanishes the moment this arrives - and what stops a single charge taking untouched ground to a hole inside one tick. A pin, a reinforcement, a spawn ward and a wayfinding glow each refuse it outright, on the reasoning that a tamper charge somebody spent is a refusal rather than a delay. So does a plant holding the square under trampling.groundCoverHolds, for as long as it stands, and so does any wear ceiling below the whole run - general.maxWearFraction or a family's own maxWear - because ground told to stop short of the end never reaches it. This destroys real blocks and is NOT undone by removing the mod.");
        groundCoverBreaks = config.getBoolean(
            "groundCover",
            CATEGORY_TRAMPLING,
            true,
            "Break the plants standing on a square when the ground under them gives way, as though whatever wore it had gone through them to do it. Not the same thing as trampling.vegetation: that one counts crossings of the plant itself and breaks it on its own tally, where this one asks the plant nothing and takes it down with the ground. It is the cheaper of the two, because nothing at all is recorded for the plant's position, and the better behaved, because a plant only ever goes where a path has visibly formed - which is why this one ships on and the vegetation and leaves switches do not. Cactus and sugar cane count, and taking the bottom of a stalk takes the whole stalk, because neither will stand on nothing. Which blocks count is decided by detection and listed under surfaces.groundCoverBlocks. It still destroys real blocks, and that is NOT undone by removing the mod.");
        groundCoverHolds = config.getBoolean(
            "groundCoverHolds",
            CATEGORY_TRAMPLING,
            true,
            "Let a planted thing hold the ground it is standing on instead of being knocked off it. A sapling or a flower on a square that keeps being walked over holds that square just short of dropping a level: the wear goes on being counted, and the ground goes on darkening through its surface gradations, but it does not sink and it does not go on into the material underneath while the plant is there. Break the plant and the square shows everything it had been holding back, at once. This is the third answer to what a route does to what is standing in it, and the only one that neither destroys somebody's orchard nor pretends the ground under it is untouched. Which plants hold rather than break is decided by detection and listed under surfaces.groundCoverHoldsBlocks - saplings and flowers out of the box, because a sapling is a tree somebody planted where a tuft of grass is weather, and nothing in the block registry draws that line for us.");
        groundCoverOnSink = config.getBoolean(
            "groundCoverOnSink",
            CATEGORY_TRAMPLING,
            true,
            "Wait for the ground to physically drop a level before knocking the plants off it, rather than acting on every gradation. A family's whole run is eighty gradations and eight sunk pixels, so this is roughly a tenth as often - and it is the honest moment, because a gradation is a change of shade where a level is the ground going out from under the thing standing on it. Turn it off and a plant goes at the first gradation its square wears, which on a busy route means almost at once. Note that a family configured never to sink, or a world with physical decay switched off in its chain, then never brings a plant down at all.");
        groundCoverOnTilled = config.getBoolean(
            "groundCoverOnTilled",
            CATEGORY_TRAMPLING,
            false,
            "Whether plants standing on hoed ground go down with everything else. Off, anything planted in tilled soil is left alone, which is the only honest way to spare a field: asking the plant what it is cannot separate a farm from a meadow, because Natura's cotton and barley are crops by Forge's own reckoning and also grow wild on plain grass, but asking what they were planted in can. It decides trampling.vegetation as well: off, a plant standing in tilled soil keeps no tally and is never trampled, however often it is crossed. For trampling.groundCover this only ever comes up where tilled soil has been made erodable, since it is on the surface exclude list by default and ground that never wears never drops a level; for trampling.vegetation it matters in every field.");
        groundCoverDropChance = readFloat(
            CATEGORY_TRAMPLING,
            "groundCoverDropChance",
            0.5f,
            0f,
            1f,
            "Chance a plant taken down by the ground wearing still drops its item. Higher than leavesDropChance and vegetationDropChance on purpose. With groundCoverOnSink on, as it ships, this fires only where a square has actually dropped a level, and on turf and earth, where nearly every plant grows, that takes far more crossings than a trampled plant's own tally running out, so it can afford to be generous - and a path that quietly eats a rare flower and leaves nothing behind reads as a bug rather than as weather.");
    }

    private static void readPerformance() {
        sweepIntervalTicks = config.getInt(
            "sweepIntervalTicks",
            CATEGORY_PERFORMANCE,
            200,
            20,
            24000,
            "Ticks between healing sweeps over loaded chunks. Unloaded chunks need no sweeping; they catch up the moment they load.");
        sweepChunksPerPass = config.getInt(
            "sweepChunksPerPass",
            CATEGORY_PERFORMANCE,
            64,
            1,
            4096,
            "Chunks examined per sweep. The sweep round-robins, so this bounds its cost however much world is loaded.");
        maxEntriesPerChunk = config.getInt(
            "maxEntriesPerChunk",
            CATEGORY_PERFORMANCE,
            3072,
            16,
            65536,
            "Hard cap on tracked positions in one chunk. Stops a save growing without bound under sustained traffic. A chunk at the cap starts no new record from traffic: ground not already tracked there stops wearing, and trampling starts no new tally. Work done on purpose is not held to it - a tamper or a golem wearing ground down, and a reinforcement or a ward put down, are still recorded past it. Tallies against plants and leaves count towards it while they last - a single crossing's for about half an in-game day at the shipped figures, a nearly spent one's for up to twenty - and for ever while healing.enabled is off or healing.wearDecayPerDay is 0, since nothing then fades them away.");
        movementSampleTicks = config.getInt(
            "movementSampleTicks",
            CATEGORY_PERFORMANCE,
            2,
            1,
            20,
            "Ticks between movement checks per entity. Wear is counted per block entered rather than per tick, so raising this only loses accuracy when sprinting diagonally.");
    }

    /**
     * A {@code #RRGGBB} color, or the fallback with one line in the log.
     *
     * <p>
     * Said aloud rather than swallowed. A mistyped color that quietly drew black would look
     * exactly like the feature working, because worn ground is meant to be dark anyway - so the
     * one mistake somebody is likely to make is the one that would be hardest to notice.
     */
    private static int parseRgb(String text, int fallback) {
        String hex = text == null ? "" : text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        else if (hex.regionMatches(true, 0, "0x", 0, 2)) hex = hex.substring(2);
        try {
            if (hex.length() == 6) return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException notAColor) {
            // Falls through to the complaint below.
        }
        Trmt.LOG.warn("client.desirePathColor is not a #RRGGBB color ('{}'); using the default", text);
        return fallback;
    }

    /**
     * The name {@code client.desirePathColor} was saved under until 0.9.220. Nothing reads it but
     * {@link #carryDesirePathColor}, and nothing writes it at all.
     */
    static final String DESIRE_PATH_COLOR_WAS = "desirePathColour";

    /**
     * Moves a player's desire-path color from the name it was saved under until 0.9.220 to the name it has
     * now.
     *
     * <p>
     * The mod's spelling became "color" in 0.9.220, and this is the one place where renaming took more
     * than an edit: Forge reads a name it does not find in the file as a setting nobody has set, so a bare
     * rename would have put every color a player had chosen back to the default violet without a word.
     *
     * <p>
     * The same three cases as the chunk tamper's rename in {@link #readGeneral}, for the same reasons. A
     * file that names only the old setting has its value carried to the new name - created on a first
     * load, written over the default on a reload, which merges the file into what is already held. A file
     * that names both has been edited since the rename, and the new name is the one somebody chose; the
     * old one is dropped and said once. Either way the old name leaves the settings, which marks the file
     * changed, so the save that follows every read writes it out under the new name.
     *
     * <p>
     * Called before {@link #readClient} reads the setting, and it has to be: read first, and the field
     * would already hold the default this exists to keep out of it.
     */
    static void carryDesirePathColor(Configuration from) {
        ConfigCategory client = from.getCategory(CATEGORY_CLIENT);
        if (!client.containsKey(DESIRE_PATH_COLOR_WAS)) return;
        String carried = client.get(DESIRE_PATH_COLOR_WAS)
            .getString();
        if (fileNames(from, "desirePathColor")) {
            if (!retiredDesirePathSaid) {
                retiredDesirePathSaid = true;
                Trmt.LOG.warn(
                    "Ignoring client.{}={}: the file also sets desirePathColor, which replaced it in 0.9.220",
                    DESIRE_PATH_COLOR_WAS,
                    carried);
            }
        } else if (client.containsKey("desirePathColor")) {
            client.get("desirePathColor")
                .set(carried);
        } else {
            client.put("desirePathColor", new Property("desirePathColor", carried, Property.Type.STRING));
        }
        client.remove(DESIRE_PATH_COLOR_WAS);
    }

    private static void readClient() {
        Property show = config.get(
            CATEGORY_CLIENT,
            "showErosion",
            true,
            "Client only. When off you see plain terrain - with one exception worth knowing, because it is the shipped default rather than an edge: where the ruts are real enough to walk down into (general.physicalDecay=real) the drawing is held on whatever this says, in a world of your own as much as on somebody else's server, because a hollow you can fall into and cannot see is worse than one you did not want to look at; the server keeps tracking and other players are unaffected.");
        show.setLanguageKey("trmtgtnh.config.showErosion");
        showErosion = show.getBoolean();

        Property distance = config.get(
            CATEGORY_CLIENT,
            "overlayDistanceChunks",
            12,
            "Overlays further away than this many chunks are not painted. Independent of render distance, so a large view distance need not mean a large overlay cost. Read by this client alone: what a server sends follows the chunks it has actually sent a player, whatever this is set to on either machine, so lowering it costs nothing but what is drawn.",
            2,
            32);
        distance.setLanguageKey("trmtgtnh.config.overlayDistance");
        overlayDistanceChunks = distance.getInt();

        Property perSurface = config.get(
            CATEGORY_CLIENT,
            "perSurfaceTextures",
            true,
            "Build wear textures per surface, so a Biomes O' Plenty grass or a Twilight Forest dirt wears in its own colors instead of vanilla's. Costs extra sprites in the block atlas at the resolution of the faces they are worn from, bounded by surfaces.maxTexturedSurfaces and client.maxWearSprites. Where the atlas has not the room for them all at client.wearGradations, every surface's ramp is drawn more coarsely first, the family fallbacks that everything else wears included, never below sixteen gradations, and only past that do the faces last in registry-name order fall back; the log says which. On a large pack, or one drawn at thirty-two pixels, that means this setting can cost every worn block in the world some of its gradations, and switching it off gives that room back to the ramp. Off, no worn block is drawn from its own pixels, so nothing named in client.innerLayerTextures is drawn into worn ground either: Chisel's lavastone and waterstone wear their family's generic art with no lava or water in it. The potato quality rung turns this off.");
        perSurface.setLanguageKey("trmtgtnh.config.perSurfaceTextures");
        perSurface.setRequiresMcRestart(true);
        perSurfaceTextures = perSurface.getBoolean();

        hideWearUnderBlocks = config.getBoolean(
            "hideWearUnderBlocks",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether a block placed directly on worn ground hides the wear under it. Only the drawing stops - the wear is kept exactly as it was, so breaking the block on top brings the same path straight back. It is not a way to repair ground by paving over it.");
        flattenWearUnderBlocks = config.getBoolean(
            "flattenWearUnderBlocks",
            Configuration.CATEGORY_GENERAL,
            true,
            "What hideWearUnderBlocks actually does when something is built on worn ground. On, the ground keeps the texture of the wear it has and simply fills its own cube again, so a path that runs under a block still reads as a path and only its dip goes away. Off, the wear is not drawn at all and the plain block shows through. Either way the wear itself is untouched and comes straight back when the block on top is broken. Has no effect unless hideWearUnderBlocks is on.");
        overlayApplyBudget = config.getInt(
            "overlayApplyBudget",
            CATEGORY_CLIENT,
            512,
            32,
            16384,
            "Overlay positions written into the client world per tick. Spreading a large batch over several ticks keeps a frame spike out of the picture when you fly into a well-travelled area.");

        liftLayeredBlockShell = config.getBoolean(
            "liftLayeredBlockShell",
            CATEGORY_CLIENT,
            cpw.mods.fml.common.Loader.isModLoaded("chisel"),
            "Stops the lava and water inside Chisel's lavastone and waterstone from flickering against the carved stone drawn over it. Those blocks are two full cubes occupying exactly the same space - the liquid first, the stone over it - and two surfaces at the same depth give a graphics card no way to say which is in front. On a plain client that settles itself, because both are worked out from the same corners and so come to the same depth; what unsettles it is the crack fix that comes with the modern chunk builder, which grows every full-cube face a thousandth of a block sideways to hide the seams between chunks and does it in only the first of the two passes. The liquid's face moves, the stone's does not, and the two argue pixel by pixel. This lifts the stone a hair proud of the liquid so the argument cannot arise. Purely visual and yours alone: it changes nothing about where the block is, what it collides with, how it is lit, which of its faces are drawn, or what anybody else sees. Defaults on when Chisel is installed, does nothing whatever when it is not, and is quietly skipped if a future Chisel draws these blocks some other way - the log says once, at the first one drawn, whether it is doing anything.");
        layeredBlockShellLift = config.get(
            CATEGORY_CLIENT,
            "layeredBlockShellLift",
            0.002d,
            "How far, in blocks, the setting above lifts the carved stone off the liquid beneath it. Depth precision falls away with the square of the distance to your eye, so no fixed figure lasts for ever: a five-hundredth of a block holds to roughly forty blocks away, a two-hundred-and-fiftieth to sixty, a hundredth to ninety. Raise it if the flicker comes back across a long view; lower it if you can see the stone standing proud of the blocks beside it close up, which at the default is a thirty-second of a texture pixel and should not be visible at all. The lift only ever reaches into a neighbouring cell you can see through, because a face against a solid neighbour is never drawn in the first place, so there is nothing there for it to poke through. Nought disables the lift while leaving the setting above on, which is how to check whether the lift is what you are looking at; a twentieth exaggerates it until the stone visibly balloons, which is how to check whether it is being applied at all.",
            0.0d,
            0.05d)
            .getDouble();
        innerLayerTextures = config.get(
            CATEGORY_CLIENT,
            "innerLayerTextures",
            new String[] { "chisel:lavastone=lava_still", "chisel:waterstone=water_still" },
            "What to draw behind a worn block whose own texture is cut away to show something underneath. A few blocks are a shell with a second layer behind them - Chisel's lavastone and waterstone are stone with lava or water showing through the gaps - and they draw that layer in a pass of their own. Worn ground is drawn by this mod in one pass and cannot do that, so before this setting existed the gaps in the shell were simply gaps: between a twentieth and three quarters of every face of a worn one was a hole with nothing behind it, and a worn waterstone came out plain grey with no water in it at all. Each entry says which texture belongs behind which block, written as modid:block=texture or modid:block:meta=texture, and the layer is painted into the worn picture once while the textures are being built rather than drawn again every frame - so it costs nothing at all while you are playing, and it sinks with the ground because it is part of the picture the ground is drawn with. Naming a block from a mod you do not have does nothing; the two entries shipped here are inert without Chisel, and a texture that cannot be found is said once in the log rather than quietly ignored. Emptying the list turns the whole thing off and gives the holes back. A layer is only ever laid into a worn picture made from the block's own pixels, so it is gone from any block that wears its family's generic art instead: every block while client.perSurfaceTextures is off, which the potato quality rung also does, and any block that surfaces.maxTexturedSurfaces, client.maxWearSprites or the room in the block atlas turns away, which the log says at the stitch where it happens. Where the texture named is one that moves, the layer moves with it and keeps step with the unworn blocks around it; see animateInnerLayers, which is what decides that and what it costs. Takes effect on the next resource reload.")
            .getStringList();
        seeThroughInnerLayers = config.getBoolean(
            "seeThroughInnerLayers",
            CATEGORY_CLIENT,
            cpw.mods.fml.common.Loader.isModLoaded("chisel"),
            "Let the layer behind a worn block be seen through, where it has any transparency of its own. Chisel's waterstone is stone with water behind it, and vanilla's water is drawn with about a third of its light coming from whatever is on the other side - but the block it belongs to is solid, and so is the worn ground this mod paints over it, so that third has nowhere to come from and the water reads as blue stone. On, the gaps a carving leaves are left genuinely open: the carved stone stays solid, and only the holes let anything through. It is a deliberate departure from what the block looks like unworn rather than a correction to it, which is why it asks first. How much comes through depends entirely on which chiselling it is, and on three of the seven it is almost nothing. The gaps in a carved face run from a thirty-second of it to three eighths, and vanilla's water is never less than two thirds solid in any of its thirty-two frames - so across a whole face the light arriving from behind is about one part in a hundred on the cobble, black and creeper carvings, one in thirty on the tiled, one in twenty on the chaotic, and one in nine on the plain panel, which is the only one where it is plainly a different block. Turning this on and looking at a worn cobble waterstone shows nothing whatever, and that is the setting working rather than failing; the panel and the chaotic are the ones to look at. Nothing at all is drawn through where the layer is solid, so lava is untouched by this and always will be. Costs nothing when off, and nothing at all without a mod that has such a block. Takes effect on the next resource reload.");
        animateInnerLayers = config.getBoolean(
            "animateInnerLayers",
            CATEGORY_CLIENT,
            true,
            "Whether the layer behind a worn block moves, when the texture named for it is one that moves. Chisel's lavastone and waterstone are stone with lava or water behind them, and that lava is animated - twenty pictures cycling every two ticks - so worn ground showing a single still frame of it sat dead beside the unworn blocks around it. On, the frame under the worn shell is laid again each time the liquid moves, and stays in step with the liquid in the block next door because it is counted by the same clock read out of the same file, while it is on the ground near you - every such picture, as the unworn blocks beside it move with their liquid, and none elsewhere on the atlas, which is what keeps the cost to the worn liquid in sight. What it costs is memory held for the session: the shell of every worn picture, kept rather than thrown away once it has been drawn, the still picture the atlas keeps beside it for anything that moves, and one copy of the liquid's frames per surface - a little over eleven megabytes for Chisel's fifteen faces at sixteen pixels and the settings shipped here, and about forty-five at thirty-two. The budget below counts all of it, which is what keeps a config naming fifty blocks from quietly costing far more. Nothing at all when innerLayerTextures is empty or names nothing that moves, or for a block with no worn pictures of its own, which is every block while client.perSurfaceTextures is off, as the potato quality rung also leaves it. No quality rung moves this, client.innerLayerAnimationBudgetMb or client.innerLayerUploadsPerTick. A lower rung lays the layer into fewer pictures and lets fewer blocks keep their own, and neither changes how many pictures one tick may redraw. Takes effect on the next resource reload.");
        innerLayerAnimationBudgetMb = config.getInt(
            "innerLayerAnimationBudgetMb",
            CATEGORY_CLIENT,
            64,
            0,
            512,
            "The most memory, in megabytes, that moving layers may hold for the session. A surface whose layer moves is priced whole before any of it is granted, and the price is everything moving it makes the game keep: the shell of every worn picture of the surface, the still picture and its smaller copies that the atlas keeps for anything that moves and lets go of for everything else, and one copy of the liquid's frames for the surface, cut at the size its worn pictures are drawn at. So it rises with how many blocks you have named in innerLayerTextures, with client.wearGradations and client.wearRotations, the two settings that decide how many pictures a surface is drawn with, and with the square of the texture's resolution. At sixteen pixels, eighty gradations, four rotations and the game's default of four mipmap levels, one lavastone comes to three quarters of a megabyte and Chisel's eight lavastones and seven waterstones to a little over eleven, so the default has room for more than five times that; at thirty-two pixels each costs four times as much. Anisotropic filtering changes none of these figures. Surfaces are granted in the order they are built, each whole or not at all, and one the budget will not stretch to keeps its layer's first frame in every picture while a cheaper surface after it can still move. Below the price of a single surface nothing moves at all - a thirty-two pixel lavastone needs just under three megabytes, so two here moves nothing on such a pack - and the log says so rather than leaving it to be noticed. What is held, by kind, and the surfaces kept still, counted by the texture behind them, with what moving them would have needed, are written to the log at the end of every resource reload. Nought declines the whole thing, which is the same picture as turning the setting above off.");
        innerLayerUploadsPerTick = config.getInt(
            "innerLayerUploadsPerTick",
            CATEGORY_CLIENT,
            256,
            16,
            8192,
            "How many moving layers may be redrawn in one tick, as a guard. From 0.9.221 a worn picture whose layer moves is redrawn only while a chunk section near you has been drawn with it - every one of those, in step with the liquid in the unworn block beside it, as Chisel's own blocks move - and nothing elsewhere on the atlas, so this is reached only by a great deal of worn moving ground in sight at once. A picture over the ceiling is not redrawn late: it keeps the frame it shows until its layer next moves and asks again, and the first tick after a resource reload that turns any away is written to the log with how many asked. Until 0.9.221 every moving picture on the atlas asked every tick, in view or not - several thousand for Chisel's faces - and this ceiling let the same few hundred move while the rest stood still wherever they were.");

        mapWearThroughTint = config.getBoolean(
            "mapWearThroughTint",
            CATEGORY_CLIENT,
            true,
            "Whether worn ground reports how worn it is through its own tint, which is the one question about a particular square that a minimap reading the world can ask a block. JourneyMap does not need this and is not affected by it either way - it is handed a color handler of its own and asks that. Every other map on 1.7.10 works its colors out from the block, and for them this is the difference between a road that darkens as it wears and a road that looks exactly like the ground beside it. It costs nothing where nothing asks. What it rests on is worth knowing before switching it off for no reason, and worth knowing before leaving it on if something looks wrong: the tint is given only to a caller that hands over the world itself, because the renderer hands over a view of one chunk instead, and worn ground is already darkened in the picture it is drawn with - so a renderer told the same thing twice would draw it twice as dark. That test holds for vanilla's own mesher and for this pack's, both checked by name, and it is an inference about who is asking rather than a promise anybody made. If a future renderer or shader starts handing the whole world over, every worn block in the world goes too dark and this is the setting that puts it right. One thing already falls the wrong side of it and is left alone: the cracks drawn on a block you are breaking take the shade too, for as long as the swing lasts. Two further things are worth knowing before judging whether this is working. Xaero's asks in its Accurate block-color mode, which is the one it ships with, and in its Vanilla mode only when 'Biomes in Vanilla Color Mode' is also on - in plain Vanilla mode it takes the block's map color and hands it back without asking anything about the position, so this changes nothing there and a path shows only where the ground has worn through into a different material. And a map that keeps the tiles it has drawn only redraws one when something tells it the chunk changed; this mod tells Xaero's directly, because nothing else would, but any other map that caches the same way will show the wear as of the last time it drew that square.");
        desirePathHighlight = readFloat(
            CATEGORY_CLIENT,
            "desirePathHighlight",
            0f,
            0f,
            1f,
            "How far a worn square's color on the map is pulled toward the desire-path color below, at the point it is fully worn. Nought is off and off is what ships, so this means nothing at all until somebody deliberately raises it. Left alone, a map goes on drawing worn ground as the ground it is - travelling toward what it is turning into and darkening as it goes, which is what surfaces.mapTracksWear does. Turn this up and the map stops answering 'what material is this square' for worn ground and starts answering 'where does everybody actually walk'. That is a different map and a deliberate trade: a road drawn in violet is no longer a road drawn in stone. One puts a fully worn square entirely in the highlight color; a half leaves both readings at once, the material still recognisable with the traffic laid over it, and is the setting to try first. The pull is scaled by how worn each square is, so ground nobody has ever crossed is left exactly as it was - but be ready for how much ground is not that, because a square counts from its first crossing and around a base that is most of it. The scale is deliberately not a straight line but the square root of the wear, because a route is interesting the moment somebody starts using it and a straight line leaves a new one invisible until it is half worn out. Two things it cannot do: it needs JourneyMap and does nothing whatever without it, because the highlight is applied by the color handler this mod hands to JourneyMap and no other map is given one; and it does not repaint a map already drawn, so ground near you recolors as it is mapped again while ground you explored last week keeps its old colors until you go back. The log says once, the first time a square is actually highlighted, that all of this is working.");
        carryDesirePathColor(config);
        desirePathColor = config.getString(
            "desirePathColor",
            CATEGORY_CLIENT,
            "#AA44CC",
            "The color worn ground is pulled toward when desirePathHighlight is above nought, as #RRGGBB. The default is a violet chosen for being a color no ground is: a map is greens, browns, greys and blues in every dimension this mod wears ground in, including the red of the Nether and the pale yellow of the End, so a violet path cannot be misread as a material the way an ochre or a red one could - and it stays separable for the common forms of color blindness, where an orange road over green terrain does not. Change it if it collides with something else your map draws. Anything that is not six hex digits falls back to the default and says so once in the log rather than quietly drawing black, because black is what a mistyped color would draw and worn ground is meant to be dark anyway, so the mistake would look exactly like the feature working. This is also the quickest way to find out whether any of it is running: set this to #00FF00, put desirePathHighlight to 1, and walk a path you know is worn.");
        desirePathRgb = parseRgb(desirePathColor, 0xAA44CC);
        describeCategories();
    }

    /**
     * The hover text for each heading, which is the one thing the settings screen has no other
     * way of telling you.
     *
     * <p>
     * Every individual setting carries its own comment already, and Forge shows that as a
     * tooltip. A category is different: all a reader gets is a one-word button, so without this
     * the only way to find out what "multipliers" means is to open it and read nine settings.
     */
    private static void describeCategories() {
        // Six headings that were never written, which the audit below has been complaining about
        // on every single load since it was added - a warning nobody could act on without reading
        // the source, sitting in the log beside the ones that matter.
        config.setCategoryComment(
            CATEGORY_GOLEM,
            "The Golem of Ways: whether it exists, how far it reaches, how hard it works, and what it costs to keep. It is built in the world like an iron golem rather than crafted, and it is the only thing in this mod that will hold a stretch of ground at a wear level you choose without you walking it yourself. Everything here belongs to the world it is standing in.");
        config.setCategoryComment(
            CATEGORY_LOOT,
            "Whether this mod's things turn up in chests the world generates, and how heavily each is weighted. Additive: nothing that was already findable stops being findable, and the weights are deliberately tiny against pools that total in the hundreds, so every other item keeps very nearly the odds it had. Every setting here is read once, as the game starts, so a change takes a restart.");
        config.setCategoryComment(
            CATEGORY_WARD,
            "The ward: an enchantment that lets a tamper mark ground where nothing will spawn. Server-owned, because where monsters may appear is not a thing a client gets an opinion about.");
        config.setCategoryComment(
            CATEGORY_LIGHT,
            "The path light: an enchantment that lets a tamper make ground glow without placing a light source in it. The glow is drawn rather than lit, so nothing about it changes what spawns or what the world thinks the brightness is.");
        config.setCategoryComment(
            CATEGORY_INTEGRATION,
            "What this mod writes for other mods that are here, and never links against. Quest chapters and trophy definitions are generated as those mods' own data files, which is why they need nothing on the compile path and cost nothing at all when the mod they are for is absent.");
        config.setCategoryComment(
            CATEGORY_SPAWN_ITEMS,
            "Items handed to a player the first time they arrive in a world. Every one is off, and stays off: a mod that puts things in somebody's inventory uninvited is one they will remember for the wrong reason.");
        config.setCategoryComment(
            Configuration.CATEGORY_GENERAL,
            "The rules of wear itself: whether it happens, how fast, how far a blast or a fall reaches, and what the tampers cost to use. These belong to the server - a client's copy of them is only used when there is no server, and is overwritten by whatever it connects to.");
        config.setCategoryComment(
            CATEGORY_CLIENT,
            "Settings that are yours alone. Nothing here reaches the server and nothing here is overwritten by one, so they survive visiting somebody else's world. This is where to turn the overlay off, change how much detail the wear textures are drawn with, and decide whether snow and carpet settle into the ruts beneath them. Two settings that are just as much yours are not in here: hideWearUnderBlocks and flattenWearUnderBlocks, which decide how worn ground is drawn once a block is placed on it, sit under general instead. Nothing ever sends either of them to a client, so read what the general heading says about that category with those two set aside - the copy in your own file is what decides what you see, on somebody else's server exactly as much as in a world of your own.");
        config.setCategoryComment(
            CATEGORY_HEALING,
            "How worn ground recovers, and what it is worth to hurry it along. Ground left alone mends itself over in-game days; bone meal and the tampers pay to skip the wait, and this is where that price and the experience it earns are set.");
        config.setCategoryComment(
            CATEGORY_MULTIPLIERS,
            "What different things weigh, as far as the ground is concerned. A crossing by anything here counts for as many ordinary footsteps as the number says, so a horse can be made to cut a track four times faster than a player and a chicken can be made to leave no mark at all. A mob named at 0 in the mobs list leaves no mark while it walks loose, though on a lead it is counted by fromLeashedMobs; player at 0 means nothing that walks wears anything at all.");
        config.setCategoryComment(
            CATEGORY_TRAMPLING,
            "What a route does to what stands in it and to what it is walked across. Two switches, both off, keep a tally of crossings against a plant in the way or a leaf underfoot and break it when the tally runs out. The rest follow the ground: a plant comes down when the square under it drops a level, a planted thing can hold its square instead, and ground worn the whole way through is removed. Nothing here changes vanilla's own rule for trampling farmland.");
        config.setCategoryComment(
            CATEGORY_EXPLOSIONS,
            "How a blast scours the ground it does not destroy. A crater with clean grass up to its lip reads as a hole somebody cut rather than as something that went off, and this is the ring of scuffed ground that fixes that. Strength falls away with distance, and the middle of it is usually inside the crater, so the reach setting matters as much as the strength one.");
        config.setCategoryComment(
            CATEGORY_IMPACTS,
            "How something landing hard marks the ground it lands on. The same shape as a blast and a much smaller one: a fall puts its whole momentum through a patch the size of whatever fell, so it marks that patch and very little around it.");
        config.setCategoryComment(
            CATEGORY_SURFACES,
            "Which blocks wear at all, and how the mod decides. Detection runs at startup, writes what it found back into the family lists below, and never removes an entry - so the exclude list here is the way to take a block back out, and it is consulted by both the detection path and the hand-written lists.");
        config.setCategoryComment(
            CATEGORY_PERFORMANCE,
            "The ceilings that keep all of this cheap: how many positions are tracked, how often they are looked at, how much is sent to a client and how much of it is applied per tick. Raising these buys detail and spends frames and bandwidth; lowering them does the reverse. None of it changes how ground behaves, only how much of it is watched at once.");
        config.setCategoryComment(
            CATEGORY_REINFORCE,
            "The reinforcement feature: an enchantment, only for the chunk tamper and the Wayfarer's, that unlocks a mode in which the tool reinforces a block against explosions and against traffic instead of mending ground. Right-click spends a material and reinforces the block; two left-clicks take a reinforcement off. Three reinforcements make a block blast-proof, and by default make it take four times the crossings to wear.");
        config.setCategoryComment(
            CATEGORY_FAMILIES,
            "One heading per kind of surface. Each decides whether that kind wears, how many gradations it wears through, how deep it sinks, how long it takes to grow back, and which blocks count as it. This is where to make stone all but permanent and sand wear in a dozen crossings.");
    }

    /**
     * Complains in the log about anything with no hover text.
     *
     * <p>
     * Here because a setting with no comment is invisible in the settings screen - Forge shows
     * the comment and nothing else - and because the failure is silent: nobody notices a missing
     * tooltip until they go looking for one. Cheap enough to run every load, and it means a
     * setting added later cannot quietly ship without an explanation.
     */
    private static void auditComments() {
        List<String> bare = new ArrayList<String>();
        for (String name : config.getCategoryNames()) {
            ConfigCategory category = config.getCategory(name);
            if (category == null) continue;
            if (isBlank(category.getComment())) bare.add(name + " (the heading itself)");
            for (Property property : category.getValues()
                .values()) {
                if (property == null) continue;
                if (isBlank(property.comment)) bare.add(name + "." + property.getName());
            }
        }
        if (bare.isEmpty()) return;
        Trmt.LOG.warn(
            "{} settings have no hover explanation: {}",
            Integer.valueOf(bare.size()),
            com.trmtgtnh.util.LogSample.of(bare));
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim()
            .isEmpty();
    }

    private static float readFloat(String category, String name, float def, float min, float max, String comment) {
        return (float) config.get(category, name, def, comment, min, max)
            .getDouble();
    }

    public static void save() {
        if (poisoned) return; // never write a file assembled from a bad read
        if (config != null && config.hasChanged()) config.save();
    }

    /** Whether this client should be painting overlays at all. */
    public static boolean overlayVisible() {
        return showErosion || overlayForced;
    }

    /**
     * Merges detected blocks into the per-family lists and saves.
     *
     * <p>
     * Merged rather than replaced, so anything added by hand survives. The result is sorted and
     * deduplicated, which also means the file stops churning once the pack stops changing.
     */
    public static void recordDetectedSurfaces(Map<SurfaceFamily, ? extends Collection<String>> detected) {
        if (config == null || detected.isEmpty()) return;

        int added = 0;
        for (Map.Entry<SurfaceFamily, ? extends Collection<String>> entry : detected.entrySet()) {
            FamilySettings settings = family(entry.getKey());
            if (settings == null) continue;

            Set<String> merged = new TreeSet<String>();
            if (settings.extra != null) {
                for (String existing : settings.extra) {
                    if (existing != null && !existing.trim()
                        .isEmpty()) merged.add(existing.trim());
                }
            }
            int before = merged.size();
            merged.addAll(entry.getValue());
            if (merged.size() == before) continue;
            added += merged.size() - before;

            String[] list = merged.toArray(new String[merged.size()]);
            settings.extra = list;

            String category = CATEGORY_FAMILIES + Configuration.CATEGORY_SPLITTER
                + entry.getKey()
                    .key();
            Property property = config.getCategory(category)
                .get("blocks");
            if (property != null) property.set(list);
        }

        if (added > 0) {
            save();
            Trmt.LOG
                .info("Recorded {} newly detected blocks into the family lists in the config", Integer.valueOf(added));
        }
    }

    /**
     * Merges detected ground cover into the surfaces list and saves.
     *
     * <p>
     * The same contract as {@link #recordDetectedSurfaces} and for the same reason: merged rather
     * than replaced so a name added by hand survives, sorted and deduplicated so the file stops
     * churning once the pack stops changing, and never removing an entry - which is why
     * groundCoverExclude exists rather than simply deleting a line.
     */
    public static void recordDetectedGroundCover(Collection<String> detected) {
        if (config == null || detected == null || detected.isEmpty()) return;

        Set<String> merged = new TreeSet<String>();
        if (groundCoverBlocks != null) {
            for (String existing : groundCoverBlocks) {
                if (existing != null && !existing.trim()
                    .isEmpty()) merged.add(existing.trim());
            }
        }
        int before = merged.size();
        merged.addAll(detected);
        if (merged.size() == before) return;

        String[] list = merged.toArray(new String[merged.size()]);
        groundCoverBlocks = list;
        Property property = config.getCategory(CATEGORY_SURFACES)
            .get("groundCoverBlocks");
        if (property != null) property.set(list);
        save();
        Trmt.LOG.info(
            "Recorded {} newly detected ground-cover blocks into the config",
            Integer.valueOf(merged.size() - before));
    }

    /** The same contract as {@link #recordDetectedGroundCover}, for the holding half of it. */
    public static void recordDetectedHolders(Collection<String> detected) {
        if (config == null || detected == null || detected.isEmpty()) return;

        Set<String> merged = new TreeSet<String>();
        if (groundCoverHoldsBlocks != null) {
            for (String existing : groundCoverHoldsBlocks) {
                if (existing != null && !existing.trim()
                    .isEmpty()) merged.add(existing.trim());
            }
        }
        int before = merged.size();
        merged.addAll(detected);
        if (merged.size() == before) return;

        String[] list = merged.toArray(new String[merged.size()]);
        groundCoverHoldsBlocks = list;
        Property property = config.getCategory(CATEGORY_SURFACES)
            .get("groundCoverHoldsBlocks");
        if (property != null) property.set(list);
        save();
    }

    /** Writes a client preference back to disk, e.g. after a command toggles it. */
    public static void setShowErosion(boolean value) {
        showErosion = value;
        if (config != null) {
            config.get(CATEGORY_CLIENT, "showErosion", true)
                .set(value);
            save();
        }
    }

    /** Writes the master switch back to disk, so /trmt disable survives a restart. */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (config != null) {
            config.get(Configuration.CATEGORY_GENERAL, "enabled", true)
                .set(value);
            save();
        }
    }

    /** True when worn ground should be drawn hollowed out at all. */
    public static boolean physicalDecayShows() {
        return !DECAY_OFF.equals(physicalDecay);
    }

    /** True when the hollows are something you can stand in rather than only look at. */
    public static boolean physicalDecayCollides() {
        return DECAY_REAL.equals(physicalDecay);
    }

    public static float scale(float threshold) {
        double speed = positive(erosionSpeed) * positive(globalSpeed);
        return (float) (threshold / speed);
    }

    /**
     * Turns the configured lines into a name-to-multiplier lookup.
     *
     * <p>
     * Names are matched case-insensitively, because "villager" is what somebody will type and
     * being right about the capital V is not worth a silent no-op.
     *
     * <p>
     * How a line reads is {@link MobEntries}'s alone, and the magic tamper's buttons rewrite the list
     * through the same class, so a line the tamper writes is read back here exactly as it was meant.
     */
    private static Map<String, Float> parseMobs(String[] entries) {
        if (entries == null || entries.length == 0) return Collections.emptyMap();
        // Warned about here rather than in MobEntries, which is plain Java with no logger. A line
        // whose weight will not read is kept whole as a name at the ordinary weight, so this warning
        // is the only sign that something like 'Villager:1,5' is not doing what it looks like.
        for (String raw : entries) {
            MobEntries.Entry entry = MobEntries.parse(raw);
            if (entry != null && entry.badWeight) {
                Trmt.LOG.warn("Bad multiplier in mob entry '{}', treating the whole line as a name", raw);
            }
        }
        // Nought is kept as a real answer rather than an absent one. Dropping it left the named mob
        // to fall through to a '*' line and count for whatever the wildcard counts for, which is the
        // reverse of what naming a mob and giving it nought asks for, and it is what the tamper's
        // stop button writes. Keeping it is safe because every reader of the multiplier already
        // treats nought as "do not track this one". A negative is still left out, having nothing
        // the ground could act on.
        return MobEntries.table(entries);
    }

    /**
     * How hard this mob wears the ground while it walks loose, or 0 if it is not one of the
     * configured ones. A line naming the mob outranks '*', nought included, so a mob named at nought
     * stays out under a wildcard. A mob on a lead never gets this far: the lead weighs it first.
     */
    public static float mobMultiplier(String entityName) {
        if (entityName == null) return 0f;
        return MobEntries.lookup(mobMultipliers, entityName);
    }

    /** Guards a rate against zero or nonsense, which would otherwise divide the world away. */
    static double positive(double rate) {
        return rate <= 0 ? 1.0d : rate;
    }

    public static boolean dimensionAllowed(int dimensionId) {
        boolean listed = false;
        if (dimensionList != null) {
            for (int id : dimensionList) {
                if (id == dimensionId) {
                    listed = true;
                    break;
                }
            }
        }
        return dimensionListIsWhitelist == listed;
    }

    /** Picks up edits made through Forge's in-game config screen. */
    public static final class ChangeListener {

        @SubscribeEvent
        public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (!Trmt.MODID.equals(event.modID)) return;
            ConfigReload.fromGuiDeferred();
        }
    }
}
