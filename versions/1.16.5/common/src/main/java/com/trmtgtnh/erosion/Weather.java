package com.trmtgtnh.erosion;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Ground that only mends while the weather is doing the mending.
 *
 * <p>
 * A family may be told to recover only while precipitation is falling on it - snow for the two cold
 * families and rain for everything else. Elapsed time still banks up exactly as it always did, so a
 * dry spell delays recovery rather than cancelling it and nothing about catching a chunk up after a
 * month away is lost. What changes is that the bank is only ever <em>paid out</em> while it is
 * actually coming down, and paid at a rate rather than in a lump, so a player standing in the rain
 * watches a path fill in rather than finding it healed the instant a storm begins.
 *
 * <p>
 * The meter counts <b>wet seconds</b>, and it has to. Three things rule out the easier answers. The
 * sweep visits chunks on a round robin, so how often a given chunk is looked at depends on how much
 * of the world has been walked on - metering per visit would make one setting mean different things
 * on a new world and an old one. Wall time between visits is no better, since a chunk looked at
 * fifty seconds apart would be paid for fifty seconds of weather it may not have had. And whether it
 * is raining is a property of the world rather than of a place: what falls where, and whether it
 * falls at all, is decided per position from the sky above it and the biome around it, but the clock
 * is world-wide. So one counter per world, ticked while it rains, and one remembered reading per
 * chunk.
 *
 * <p>
 * None of it is written to disk. A restart begins the meter again, which costs at most one payout,
 * and a chunk met for the first time records the reading and is paid nothing - which is exactly what
 * makes returning after a month bank the debt instead of dumping it.
 */
public final class Weather {

    /** Ticks of rain each dimension has had since this server started. */
    /**
     * How long it has been raining in each dimension, keyed by the dimension's name.
     *
     * <p>
     * Both older editions key this by dimension id, because there a dimension is an id. A name is
     * the better key and always was: it is stable between installs, it reads in a log, and two mods
     * cannot be handed the same one.
     */
    private static final Map<ResourceLocation, Integer> WET_TICKS = new HashMap<ResourceLocation, Integer>();

    /** What that counter read when each chunk was last paid. */
    private static final Map<Long, Integer> LAST_PAID = new HashMap<Long, Integer>();

    private Weather() {}

    /** Counts a tick of weather. Called once a tick per world, before anything else asks. */
    public static void tick(Level world) {
        if (world == null || !world.isRaining()) return;
        ResourceLocation id = world.dimension()
            .location();
        Integer held = WET_TICKS.get(id);
        WET_TICKS.put(id, Integer.valueOf(held == null ? 1 : held.intValue() + 1));
    }

    /** Forgets everything. Called when a server stops, so a second world starts its own clock. */
    public static void reset() {
        WET_TICKS.clear();
        LAST_PAID.clear();
    }

    private static int wetTicks(Level world) {
        Integer held = WET_TICKS.get(
            world.dimension()
                .location());
        return held == null ? 0 : held.intValue();
    }

    /**
     * Whether this family waits for weather at all.
     *
     * <p>
     * Asked of the base family rather than of whatever the ground currently draws as, for the same
     * reason its price and its recovery are: a grass path showing the earth beneath it is still
     * grass wearing through, and the earth's rules are not the ones it is running under.
     */
    public static boolean waitsForWeather(SurfaceFamily base) {
        if (!TrmtConfig.wetHealingEnabled || base == null) return false;
        FamilySettings settings = TrmtConfig.family(base);
        return settings != null && settings.healsOnlyWhenWet;
    }

    /**
     * Whether this family waits for weather in this particular world.
     *
     * <p>
     * The question both callers actually have, and one that used to be answered in the wrong
     * place. A dimension with no weather can never pay a family that waits for it, so what that
     * should mean - mend the ordinary way, or never mend - has to lift the wait or keep it, rather
     * than be settled by pretending rain is or is not falling. It was settled inside
     * {@link #fallingOn}, and the wrong way round: switched on, which is how it ships and which the
     * setting describes as letting that ground mend the ordinary way, it reported nothing falling,
     * and a family waiting for weather in the Nether or the End never mended again.
     */
    public static boolean waitsForWeather(SurfaceFamily base, Level world) {
        if (!waitsForWeather(base)) return false;
        return !(world != null && !world.dimensionType()
            .hasSkyLight() && TrmtConfig.wetHealingSkipsWeatherlessDimensions);
    }

    /**
     * How many gradations a chunk may be paid this pass, and marks the meter as read.
     *
     * <p>
     * Called once for a whole chunk rather than once an entry, because rain falls on all of it at
     * once: every position in the chunk is offered the same allowance, and a position the rain
     * cannot actually reach is refused separately by {@link #fallingOn}.
     */
    public static int allowance(Level world, long chunkKey) {
        int now = wetTicks(world);
        Long key = Long.valueOf(chunkKey);
        Integer paid = LAST_PAID.get(key);
        if (paid == null) {
            // First sight. The reading is taken and nothing is paid, which is what makes a chunk
            // that has been away for a month keep its debt rather than have it settled on arrival.
            LAST_PAID.put(key, Integer.valueOf(now));
            return 0;
        }
        float price = TrmtConfig.wetHealSecondsPerStage;
        // Moved on by what was paid for rather than to now, so the part of a gradation not yet
        // bought is still on the meter at the next visit. See readingAfter.
        LAST_PAID.put(key, Integer.valueOf(readingAfter(paid.intValue(), now, price)));
        return stagesFor(now - paid.intValue(), price);
    }

    /**
     * Where a chunk's meter reading moves to once it has been paid.
     *
     * <p>
     * Pure, and pinned by a test, because this is where the meter used to lose weather. A reading
     * taken to the present on every visit throws away whatever did not come to a whole gradation,
     * and the sweep visits a chunk far more often than a storm lasts: at the defaults a tenth of all
     * rain went nowhere, and with a gradation priced above the sweep interval every second of it
     * did, so the ground never mended at all. Only whole gradations move the reading on.
     */
    static int readingAfter(int paid, int now, float secondsPerStage) {
        if (secondsPerStage <= 0f || now <= paid) return now;
        int ticks = ticksPerStage(secondsPerStage);
        return paid + ((now - paid) / ticks) * ticks;
    }

    private static int ticksPerStage(float secondsPerStage) {
        return Math.max(1, Math.round(secondsPerStage * 20f));
    }

    /**
     * How many gradations a stretch of weather pays for. The whole of the meter, and pure.
     *
     * <p>
     * Separated out so it can be pinned by a test: everything else here needs a live world, and
     * this is the part that decides whether four minutes of rain mends a path or forty.
     *
     * @param wetTicks        ticks of weather since this chunk was last paid
     * @param secondsPerStage what one gradation costs in weather, or nought for no meter at all
     */
    static int stagesFor(int wetTicks, float secondsPerStage) {
        if (secondsPerStage <= 0f) return Integer.MAX_VALUE;
        if (wetTicks <= 0) return 0;
        return wetTicks / ticksPerStage(secondsPerStage);
    }

    /** Lets go of a chunk's reading when it unloads, so the map does not grow for ever. */
    public static void forget(long chunkKey) {
        LAST_PAID.remove(Long.valueOf(chunkKey));
    }

    /**
     * Whether precipitation is reaching this position, in the form this family needs.
     *
     * <p>
     * Four questions, three of which have a setting because each is a place where the answer is
     * permanently no and a setting that quietly means never is the failure this mod keeps having to
     * dig itself out of.
     */
    public static boolean fallingOn(Level world, int x, int y, int z, SurfaceFamily base) {
        if (world == null) return false;

        // A world with no weather at all - the Nether, the End, and any dimension built like them.
        // Nothing falls there. Whether that means its ground mends the ordinary way or never mends
        // is a different question with a setting of its own, and it is asked by
        // waitsForWeather(base, world) rather than answered here by claiming rain is falling.
        if (!world.dimensionType()
            .hasSkyLight()) return false;

        if (!world.isRaining()) return false;

        // Under a roof, in a cave, beneath a canopy. Nothing washes an indoor floor, so this is
        // wanted by default - but it does mean a covered walkway keeps its wear for ever, which is
        // a large thing to have happen without having chosen it.
        if (TrmtConfig.wetHealingNeedsOpenSky && !world.canSeeSky(new net.minecraft.core.BlockPos(x, y + 1, z)))
            return false;

        Biome biome = world.getBiome(new net.minecraft.core.BlockPos(x, 0, z));
        if (biome == null) return false;

        boolean cold = biome.getPrecipitation() == Biome.Precipitation.SNOW;
        if (base == SurfaceFamily.SNOW || base == SurfaceFamily.ICE) {
            // Snow and ice want snow, and in Minecraft that is the same event: it is raining
            // everywhere at once and the biome decides what arrives. A snow block sitting somewhere
            // warm is not being snowed on, whatever the sky is doing.
            return cold;
        }

        // A desert has no rain, so sand in one would never recover - which is awkward, since being
        // filled back in is sand's whole character, and what fills it is wind, which this game does
        // not have. Hence the setting: off, a dry biome counts as wet rather than as never.
        if (!TrmtConfig.wetHealingNeedsRainyBiome) return true;
        return biome.getPrecipitation() == Biome.Precipitation.RAIN || cold;
    }
}
