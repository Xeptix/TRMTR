package com.trmtgtnh.client;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Serene Seasons, read by the mods part's Seasons set (0.9.229 W6 R5; docs/goals/0.9.229-w6-spec.md 3.4). The part sets
 * the season through the mod's own command before each stage's photographs, summer, autumn and winter, and then, in
 * winter with the rain forced, asks Weather's own question at an unworn square and says it: winter rain over a plains
 * is the mod's snow, and a square that reads it as rain is the fault T5 mended.
 *
 * <p>
 * The season is read through the mod's public API by reflection, naming the class and its methods, so nothing here
 * compiles against the mod, whose licence reserves every right ({@code the_season_is_asked_by_name}). The names are the
 * jars' own: {@code SeasonHelper.getSeasonState(world)}, and the state's {@code getSeason} and {@code getSubSeason},
 * the same in every pinned jar from 1.7.10's 1.2.18 to 26.3's 26.1.2.0.7 (javap). The command is the one thing that
 * differs, so each harness hands its own in.
 *
 * <p>
 * Plain Java with nothing of the game's, so it is tested on its own.
 */
final class ModsSeason {

    /** The set whose copies hold the mod: the only set these lines are said in. */
    static final String SET = "seasons";

    /** The mod's id, as its mcmod.info, mods.toml and fabric.mod.json give it. */
    static final String MOD = "sereneseasons";

    /** The mod's public way in, and its one question of a world. */
    static final String HELPER = "sereneseasons.api.season.SeasonHelper";

    static final String STATE = "getSeasonState";

    /** The state's two answers: the season, and the sub-season the command sets. */
    static final String SEASON = "getSeason";

    static final String SUB_SEASON = "getSubSeason";

    /** The season the weather line is asked in, and the sub-season the command sets it with. */
    static final String WINTER = "WINTER";

    static final String RAIN_SUB_SEASON = "MID_WINTER";

    /** Each stage's sub-season: the first photographs in summer, the second in autumn, the deepest in winter. */
    private static final String[] STAGE_SUB_SEASONS = { "MID_SUMMER", "MID_AUTUMN", RAIN_SUB_SEASON };

    /** What the rig reads: the square asked, Weather's answer for snow there, and the season it was asked in. */
    static final String LINE = "Spike walk: mods - weather square=%d,%d,%d cold=%b season=%s";

    /** The beats, a second each, the part waits for a season or the rain to arrive before it says it did not. */
    static final int WAIT_BEATS = 15;

    /** The beats a season that has arrived is given to draw before its photographs. */
    static final int SETTLE_BEATS = 3;

    private ModsSeason() {}

    /** Whether a fixture's set is the one these lines belong to. */
    static boolean asked(String set) {
        return SET.equals(set);
    }

    /** The sub-season a stage is photographed in, or null for a stage the part does not have. */
    static String forStage(int stage) {
        return stage < 1 || stage > STAGE_SUB_SEASONS.length ? null : STAGE_SUB_SEASONS[stage - 1];
    }

    /** The season a sub-season is part of: MID_SUMMER is SUMMER, as the mod's own enum pairs them. */
    static String seasonOf(String subSeason) {
        if (subSeason == null) return null;
        int cut = subSeason.lastIndexOf('_');
        return cut < 0 ? subSeason : subSeason.substring(cut + 1);
    }

    /** Whether a world reading this season has reached the sub-season's. */
    static boolean reached(String season, String subSeason) {
        return season != null && season.equals(seasonOf(subSeason));
    }

    /**
     * The command that sets a sub-season, in the harness's own format: the sub-season goes in lower case, which every
     * edition's command takes but 1.16.5's, whose format upper-cases it ({@code %S}).
     */
    static String command(String format, String subSeason) {
        return String.format(Locale.ROOT, format, subSeason.toLowerCase(Locale.ROOT));
    }

    /** The world's season, SUMMER to WINTER, or null where the mod is absent. */
    static String seasonIn(Object world) {
        return read(world, HELPER, SEASON);
    }

    /** The world's sub-season, EARLY_SPRING to LATE_WINTER, or null where the mod is absent. */
    static String subSeasonIn(Object world) {
        return read(world, HELPER, SUB_SEASON);
    }

    /**
     * One of the state's answers, by name, through a helper of this name; the name is handed in so a stand-in can be
     * read. The world's own class loader is the one the mod's classes are in.
     */
    static String read(Object world, String helper, String getter) {
        if (world == null) return null;
        try {
            Class<?> type = Class.forName(
                helper,
                false,
                world.getClass()
                    .getClassLoader());
            for (Method each : type.getMethods()) {
                Class<?>[] takes = each.getParameterTypes();
                if (!STATE.equals(each.getName()) || takes.length != 1 || !takes[0].isInstance(world)) continue;
                Object state = each.invoke(null, world);
                if (state == null) return null;
                Object said = each.getReturnType()
                    .getMethod(getter)
                    .invoke(state);
                return said instanceof Enum ? ((Enum<?>) said).name() : null;
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
            return null;
        }
    }

    /** What the part says where the season cannot be read at all: its load says itself first. */
    static String unread(boolean loaded) {
        return loaded ? MOD + " is loaded and " + HELPER + "." + STATE + " could not be read, so its API has moved"
            : MOD + " is not loaded in this game, so no season can be set";
    }

    /** Whether the weather line can be read: winter has arrived and the forced rain is falling. */
    static boolean settled(String season, boolean raining) {
        return WINTER.equals(season) && raining;
    }

    /** The line, in this shape on every edition. */
    static String line(int x, int y, int z, boolean cold, String season) {
        return String.format(
            Locale.ROOT,
            LINE,
            Integer.valueOf(x),
            Integer.valueOf(y),
            Integer.valueOf(z),
            Boolean.valueOf(cold),
            season);
    }

    /** Null when winter rain over the square read as snow; otherwise what was read instead. */
    static String fault(String season, boolean raining, boolean cold) {
        if (season == null) return "the season could not be read through " + HELPER;
        if (!WINTER.equals(season)) return "the season reads " + season + " after it was set to " + RAIN_SUB_SEASON;
        if (!raining) return "the rain was forced and the world does not say it is raining";
        if (!cold) return "winter rain over the square read as rain, so Weather did not ask the sky where seasons are";
        return null;
    }
}
