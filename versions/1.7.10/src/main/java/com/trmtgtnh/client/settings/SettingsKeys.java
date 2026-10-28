package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every word key the settings screen asks for (0.9.228; spec 1.5, 1.5.1 and 1.5.2).
 *
 * <p>
 * The screen asks only for keys named here, so a key it asks for is a key that exists; each edition's test checks
 * every one is in its language file with the English the shared test holds, so all eight editions say the same words.
 * A setting's own title and summary, and each chooser value's title, line and detail, are named by rule rather than
 * one by one: {@link #title}, {@link #summary}, {@link #option}, {@link #optionLine}, {@link #optionDetail}.
 */
public final class SettingsKeys {

    private SettingsKeys() {}

    /** Every key of spec 1.5 lives under this, but the few kept from before and the game's own. */
    public static final String OWN = "trmtgtnh.config.own.";

    // Kept as they were (steps 4 and 5).
    public static final String SETTINGS = OWN + "settings";
    public static final String EDIT_LIST = OWN + "editList";
    public static final String ADD = OWN + "add";
    public static final String ADD_MOB = OWN + "addMob";
    public static final String PICK_MOB = OWN + "pickMob";
    public static final String SEARCH_MOB = OWN + "searchMob";
    public static final String UNKNOWN_MOB = OWN + "unknownMob";
    public static final String MOB_NUMBER = OWN + "mobNumber";
    public static final String RESTART_MARK = OWN + "restartMark";
    public static final String RESTART = OWN + "restart";
    public static final String RESTART_NOTE = OWN + "restart.note";
    public static final String DISCARD = OWN + "discard";
    public static final String DISCARD_NOTE = OWN + "discard.note";
    public static final String PUSH = "trmtgtnh.config.push";
    public static final String WEARTABLE_BUTTON = "trmtgtnh.weartable.button";

    /** The game's own words, which every edition's language already carries. */
    public static final String OPTIONS_ON = "options.on";
    public static final String OPTIONS_OFF = "options.off";
    public static final String GUI_DONE = "gui.done";
    public static final String GUI_CANCEL = "gui.cancel";

    // New in 0.9.228.
    public static final String TAB_PRESETS = OWN + "tab.presets";
    public static final String TAB_CLIENT = OWN + "tab.client";
    public static final String TAB_GENERAL = OWN + "tab.general";
    public static final String TAB_HEALING = OWN + "tab.healing";
    public static final String TAB_MULTIPLIERS = OWN + "tab.multipliers";
    public static final String TAB_TRAMPLING = OWN + "tab.trampling";
    public static final String TAB_EXPLOSIONS = OWN + "tab.explosions";
    public static final String TAB_IMPACTS = OWN + "tab.impacts";
    public static final String TAB_SURFACES = OWN + "tab.surfaces";
    public static final String TAB_PERFORMANCE = OWN + "tab.performance";
    public static final String TAB_REINFORCE = OWN + "tab.reinforce";
    public static final String TAB_POTIONS = OWN + "tab.potions";
    public static final String TAB_WEATHER = OWN + "tab.weather";
    public static final String TAB_GOLEM = OWN + "tab.golem";
    public static final String TAB_LOOT = OWN + "tab.loot";
    public static final String TAB_SPAWNWARD = OWN + "tab.spawnward";
    public static final String TAB_PATHLIGHT = OWN + "tab.pathlight";
    public static final String TAB_INTEGRATION = OWN + "tab.integration";
    public static final String TAB_SPAWNITEMS = OWN + "tab.spawnitems";
    public static final String TAB_MORE = OWN + "tab.more";
    public static final String TAB_CHANGED_ONE = OWN + "tab.changed.one";
    public static final String TAB_CHANGED_MANY = OWN + "tab.changed.many";
    public static final String TAB_REFUSED_ONE = OWN + "tab.refused.one";
    public static final String TAB_REFUSED_MANY = OWN + "tab.refused.many";
    public static final String FAMILY_ALL = OWN + "family.all";
    public static final String FAMILY_GRASS = OWN + "family.grass";
    public static final String FAMILY_DIRT = OWN + "family.dirt";
    public static final String FAMILY_SAND = OWN + "family.sand";
    public static final String FAMILY_GRAVEL = OWN + "family.gravel";
    public static final String FAMILY_STONE = OWN + "family.stone";
    public static final String FAMILY_COBBLE = OWN + "family.cobble";
    public static final String FAMILY_LEAVES = OWN + "family.leaves";
    public static final String FAMILY_VEGETATION = OWN + "family.vegetation";
    public static final String FAMILY_NETHER = OWN + "family.nether";
    public static final String FAMILY_END = OWN + "family.end";
    public static final String FAMILY_SNOW = OWN + "family.snow";
    public static final String FAMILY_ICE = OWN + "family.ice";
    public static final String SEARCH_HINT = OWN + "search.hint";
    public static final String SEARCH_NONE = OWN + "search.none";
    public static final String SEARCH_COUNT_ONE = OWN + "search.count.one";
    public static final String SEARCH_COUNT_MANY = OWN + "search.count.many";
    public static final String FILTER_CHANGED = OWN + "filter.changed";
    public static final String FILTER_CHANGED_TIP = OWN + "filter.changed.tip";
    public static final String FILTER_CHANGED_NONE = OWN + "filter.changed.none";
    public static final String SAVE_ONE = OWN + "save.one";
    public static final String SAVE_MANY = OWN + "save.many";
    public static final String FIX_ONE = OWN + "fix.one";
    public static final String FIX_MANY = OWN + "fix.many";
    public static final String UNDO_ALL = OWN + "undoAll";
    public static final String UNDO_ALL_TIP = OWN + "undoAll.tip";
    public static final String RESET_TAB = OWN + "resetTab";
    public static final String RESET_TAB_TIP = OWN + "resetTab.tip";
    public static final String RESET_TAB_ASK = OWN + "resetTab.ask";
    public static final String RESET_TAB_NOTE = OWN + "resetTab.note";
    public static final String RESET_TAB_YES = OWN + "resetTab.yes";
    public static final String RESET_TIP = OWN + "reset.tip";
    public static final String UNDO_TIP = OWN + "undo.tip";
    public static final String RELOAD_MARK = OWN + "reloadMark";
    public static final String BANNER_ONE = OWN + "banner.one";
    public static final String BANNER_MANY = OWN + "banner.many";
    public static final String RANGE = OWN + "range";
    public static final String DEFAULT = OWN + "default";
    public static final String REFUSE_NOT_NUMBER = OWN + "refuse.notNumber";
    public static final String REFUSE_NOT_WHOLE = OWN + "refuse.notWhole";
    public static final String REFUSE_BELOW = OWN + "refuse.below";
    public static final String REFUSE_ABOVE = OWN + "refuse.above";
    public static final String REFUSE_EMPTY = OWN + "refuse.empty";
    public static final String CYCLE_TIP = OWN + "cycle.tip";
    public static final String ADD_BLOCK = OWN + "addBlock";
    public static final String ADD_ITEM = OWN + "addItem";
    public static final String ADD_FLUID = OWN + "addFluid";
    public static final String ADD_ENCHANTMENT = OWN + "addEnchantment";
    public static final String PICK_BLOCK = OWN + "pickBlock";
    public static final String PICK_ITEM = OWN + "pickItem";
    public static final String PICK_FLUID = OWN + "pickFluid";
    public static final String PICK_ENCHANTMENT = OWN + "pickEnchantment";
    public static final String PICK_NONE = OWN + "pick.none";
    public static final String PICK_VARIANT = OWN + "pick.variant";
    public static final String PICK_EVERY = OWN + "pick.every";
    public static final String PICK_SHIFT = OWN + "pick.shift";
    public static final String PICK_GATHERING = OWN + "pick.gathering";
    public static final String FOLD_OPEN = OWN + "fold.open";
    public static final String FOLD_CLOSE = OWN + "fold.close";
    public static final String REMOVE_TIP = OWN + "remove.tip";
    public static final String DRAG_TIP = OWN + "drag.tip";
    public static final String COPY = OWN + "copy";
    public static final String COPY_TIP = OWN + "copy.tip";
    public static final String PASTE = OWN + "paste";
    public static final String PASTE_TIP = OWN + "paste.tip";
    public static final String PASTE_NONE = OWN + "paste.none";
    public static final String PASTE_ADDED_ONE = OWN + "paste.added.one";
    public static final String PASTE_ADDED_MANY = OWN + "paste.added.many";
    public static final String STATUS_NOT_HERE = OWN + "status.notHere";
    public static final String STATUS_UNKNOWN_BLOCK = OWN + "status.unknownBlock";
    public static final String STATUS_UNKNOWN_ITEM = OWN + "status.unknownItem";
    public static final String STATUS_UNKNOWN_FLUID = OWN + "status.unknownFluid";
    public static final String STATUS_UNKNOWN_ORE = OWN + "status.unknownOre";
    public static final String STATUS_UNKNOWN_TAG = OWN + "status.unknownTag";
    public static final String STATUS_UNKNOWN_ENCHANTMENT = OWN + "status.unknownEnchantment";
    public static final String STATUS_UNKNOWN_SPRITE = OWN + "status.unknownSprite";
    public static final String STATUS_UNKNOWN_FAMILY = OWN + "status.unknownFamily";
    public static final String STATUS_UNKNOWN_DIMENSION = OWN + "status.unknownDimension";
    public static final String STATUS_META_REFUSED = OWN + "status.metaRefused";
    public static final String STATUS_NO_ITEM = OWN + "status.noItem";
    public static final String STATUS_EVERY = OWN + "status.every";
    public static final String STATUS_MEMBERS_ONE = OWN + "status.members.one";
    public static final String STATUS_MEMBERS_MANY = OWN + "status.members.many";
    public static final String STATUS_FLUID = OWN + "status.fluid";
    public static final String STATUS_KEPT_OUT = OWN + "status.keptOut";
    public static final String STATUS_ANY_MOB = OWN + "status.anyMob";
    public static final String STATUS_MATCHES_ONE = OWN + "status.matches.one";
    public static final String STATUS_MATCHES_MANY = OWN + "status.matches.many";
    public static final String STATUS_BAD_LINE = OWN + "status.badLine";
    public static final String GRADE_NAME = OWN + "grade.name";
    public static final String GRADE_ORE = OWN + "grade.ore";
    public static final String GRADE_USES = OWN + "grade.uses";
    public static final String GRADE_REACH = OWN + "grade.reach";
    public static final String GRADE_BAD = OWN + "grade.bad";
    public static final String CHIPS_ADD = OWN + "chips.add";
    public static final String CHIPS_TIP = OWN + "chips.tip";
    public static final String COLOR_PICK = OWN + "color.pick";
    public static final String COLOR_HEX = OWN + "color.hex";
    public static final String COLOR_REFUSE_DIGITS = OWN + "color.refuse.digits";
    public static final String COLOR_REFUSE_CHAR = OWN + "color.refuse.char";
    public static final String COLOR_PREVIEW = OWN + "color.preview";
    public static final String COLOR_APPLY = OWN + "color.apply";
    public static final String GREYED = OWN + "greyed";
    public static final String GREYED_PARTIAL = OWN + "greyed.partial";
    public static final String LOCKED = OWN + "locked";
    public static final String LOCKED_NOTE = OWN + "locked.note";
    public static final String PRESET_CHANGES_ONE = OWN + "preset.changes.one";
    public static final String PRESET_CHANGES_MANY = OWN + "preset.changes.many";
    public static final String PRESET_LINE = OWN + "preset.line";
    public static final String PRESET_NONE = OWN + "preset.none";
    public static final String RESTART_QUIT = OWN + "restart.quit";
    public static final String RESTART_LATER = OWN + "restart.later";
    public static final String RELOAD = OWN + "reload";
    public static final String RELOAD_NOW = OWN + "reload.now";
    public static final String DISCARD_SAVE = OWN + "discard.save";
    public static final String DISCARD_LEAVE = OWN + "discard.leave";
    public static final String DISCARD_STAY = OWN + "discard.stay";
    public static final String TOGGLE_FORGE = OWN + "toggle.forge";
    public static final String TOGGLE_CLOTH = OWN + "toggle.cloth";
    public static final String TOGGLE_TIP = OWN + "toggle.tip";
    public static final String TOGGLE_BACK = OWN + "toggle.back";
    public static final String TOGGLE_BACK_TIP = OWN + "toggle.back.tip";
    public static final String TOGGLE_NONE = OWN + "toggle.none";
    public static final String TOGGLE_ASK = OWN + "toggle.ask";
    public static final String HELP_TITLE = OWN + "help.title";
    public static final String HELP_1 = OWN + "help.1";
    public static final String HELP_2 = OWN + "help.2";
    public static final String HELP_3 = OWN + "help.3";
    public static final String HELP_4 = OWN + "help.4";
    public static final String HELP_5 = OWN + "help.5";
    public static final String HELP_6 = OWN + "help.6";
    public static final String HELP_7 = OWN + "help.7";
    public static final String HELP_8 = OWN + "help.8";
    public static final String HELP_9 = OWN + "help.9";
    public static final String HEADER_PUSH = OWN + "header.push";
    public static final String HEADER_PUSH_TIP = OWN + "header.push.tip";
    public static final String HEADER_WEARTABLE = OWN + "header.weartable";
    public static final String HEADER_WEARTABLE_TIP = OWN + "header.weartable.tip";
    public static final String ADVANCED = OWN + "advanced";
    public static final String ADVANCED_NOTE = OWN + "advanced.note";
    public static final String ADVANCED_LAST_CHOSEN = OWN + "advanced.lastChosen";
    public static final String ADVANCED_DEPTH = OWN + "advanced.depth";
    public static final String ADVANCED_HEALING = OWN + "advanced.healing";
    public static final String ADVANCED_PHASES = OWN + "advanced.phases";
    public static final String ADVANCED_QUALITY = OWN + "advanced.quality";
    public static final String ADVANCED_STAGES = OWN + "advanced.stages";
    public static final String ADVANCED_LAYERS = OWN + "advanced.layers";
    public static final String HINT_BLOCK = OWN + "hint.block";
    public static final String HINT_ITEM = OWN + "hint.item";
    public static final String HINT_MOB = OWN + "hint.mob";
    public static final String HINT_ENCHANTMENT = OWN + "hint.enchantment";
    public static final String HINT_DIMENSION = OWN + "hint.dimension";
    public static final String HINT_DIMENSION_NAMED = OWN + "hint.dimension.named";
    public static final String HINT_TEXTURE = OWN + "hint.texture";
    public static final String HINT_FAMILY = OWN + "hint.family";
    public static final String HINT_GRADE = OWN + "hint.grade";
    public static final String HINT_TEXT = OWN + "hint.text";
    public static final String ADD_DIMENSION = OWN + "addDimension";
    public static final String PICK_DIMENSION = OWN + "pickDimension";
    public static final String PICK_TEXTURE = OWN + "pickTexture";
    public static final String TEXTURE_BLOCK = OWN + "texture.block";
    public static final String TEXTURE_SHOWS = OWN + "texture.shows";
    public static final String DIMENSION_BY_NAME = OWN + "dimension.byName";
    public static final String HOVER_MORE = OWN + "hover.more";
    public static final String HOVER_PAGE = OWN + "hover.page";
    public static final String OPTION_CHOOSE = OWN + "option.choose";

    /** The keys this mod's own language file must carry: spec 1.5, kept and new, without the game's own four. */
    public static final String[] ALL = { SETTINGS, EDIT_LIST, ADD, ADD_MOB, PICK_MOB, SEARCH_MOB, UNKNOWN_MOB,
        MOB_NUMBER, RESTART_MARK, RESTART, RESTART_NOTE, DISCARD, DISCARD_NOTE, PUSH, WEARTABLE_BUTTON, TAB_PRESETS,
        TAB_CLIENT, TAB_GENERAL, TAB_HEALING, TAB_MULTIPLIERS, TAB_TRAMPLING, TAB_EXPLOSIONS, TAB_IMPACTS, TAB_SURFACES,
        TAB_PERFORMANCE, TAB_REINFORCE, TAB_POTIONS, TAB_WEATHER, TAB_GOLEM, TAB_LOOT, TAB_SPAWNWARD, TAB_PATHLIGHT,
        TAB_INTEGRATION, TAB_SPAWNITEMS, TAB_MORE, TAB_CHANGED_ONE, TAB_CHANGED_MANY, TAB_REFUSED_ONE, TAB_REFUSED_MANY,
        FAMILY_ALL, FAMILY_GRASS, FAMILY_DIRT, FAMILY_SAND, FAMILY_GRAVEL, FAMILY_STONE, FAMILY_COBBLE, FAMILY_LEAVES,
        FAMILY_VEGETATION, FAMILY_NETHER, FAMILY_END, FAMILY_SNOW, FAMILY_ICE, SEARCH_HINT, SEARCH_NONE,
        SEARCH_COUNT_ONE, SEARCH_COUNT_MANY, FILTER_CHANGED, FILTER_CHANGED_TIP, FILTER_CHANGED_NONE, SAVE_ONE,
        SAVE_MANY, FIX_ONE, FIX_MANY, UNDO_ALL, UNDO_ALL_TIP, RESET_TAB, RESET_TAB_TIP, RESET_TAB_ASK, RESET_TAB_NOTE,
        RESET_TAB_YES, RESET_TIP, UNDO_TIP, RELOAD_MARK, BANNER_ONE, BANNER_MANY, RANGE, DEFAULT, REFUSE_NOT_NUMBER,
        REFUSE_NOT_WHOLE, REFUSE_BELOW, REFUSE_ABOVE, REFUSE_EMPTY, CYCLE_TIP, ADD_BLOCK, ADD_ITEM, ADD_FLUID,
        ADD_ENCHANTMENT, PICK_BLOCK, PICK_ITEM, PICK_FLUID, PICK_ENCHANTMENT, PICK_NONE, PICK_VARIANT, PICK_EVERY,
        PICK_SHIFT, PICK_GATHERING, FOLD_OPEN, FOLD_CLOSE, REMOVE_TIP, DRAG_TIP, COPY, COPY_TIP, PASTE, PASTE_TIP,
        PASTE_NONE, PASTE_ADDED_ONE, PASTE_ADDED_MANY, STATUS_NOT_HERE, STATUS_UNKNOWN_BLOCK, STATUS_UNKNOWN_ITEM,
        STATUS_UNKNOWN_FLUID, STATUS_UNKNOWN_ORE, STATUS_UNKNOWN_TAG, STATUS_UNKNOWN_ENCHANTMENT, STATUS_UNKNOWN_SPRITE,
        STATUS_UNKNOWN_FAMILY, STATUS_UNKNOWN_DIMENSION, STATUS_META_REFUSED, STATUS_NO_ITEM, STATUS_EVERY,
        STATUS_MEMBERS_ONE, STATUS_MEMBERS_MANY, STATUS_FLUID, STATUS_KEPT_OUT, STATUS_ANY_MOB, STATUS_MATCHES_ONE,
        STATUS_MATCHES_MANY, STATUS_BAD_LINE, GRADE_NAME, GRADE_ORE, GRADE_USES, GRADE_REACH, GRADE_BAD, CHIPS_ADD,
        CHIPS_TIP, COLOR_PICK, COLOR_HEX, COLOR_REFUSE_DIGITS, COLOR_REFUSE_CHAR, COLOR_PREVIEW, COLOR_APPLY, GREYED,
        GREYED_PARTIAL, LOCKED, LOCKED_NOTE, PRESET_CHANGES_ONE, PRESET_CHANGES_MANY, PRESET_LINE, PRESET_NONE,
        RESTART_QUIT, RESTART_LATER, RELOAD, RELOAD_NOW, DISCARD_SAVE, DISCARD_LEAVE, DISCARD_STAY, TOGGLE_FORGE,
        TOGGLE_CLOTH, TOGGLE_TIP, TOGGLE_BACK, TOGGLE_BACK_TIP, TOGGLE_NONE, TOGGLE_ASK, HELP_TITLE, HELP_1, HELP_2,
        HELP_3, HELP_4, HELP_5, HELP_6, HELP_7, HELP_8, HELP_9, HEADER_PUSH, HEADER_PUSH_TIP, HEADER_WEARTABLE,
        HEADER_WEARTABLE_TIP, ADVANCED, ADVANCED_NOTE, ADVANCED_LAST_CHOSEN, ADVANCED_DEPTH, ADVANCED_HEALING,
        ADVANCED_PHASES, ADVANCED_QUALITY, ADVANCED_STAGES, ADVANCED_LAYERS, HINT_BLOCK, HINT_ITEM, HINT_MOB,
        HINT_ENCHANTMENT, HINT_DIMENSION, HINT_DIMENSION_NAMED, HINT_TEXTURE, HINT_FAMILY, HINT_GRADE, HINT_TEXT,
        ADD_DIMENSION, PICK_DIMENSION, PICK_TEXTURE, TEXTURE_BLOCK, TEXTURE_SHOWS, DIMENSION_BY_NAME, HOVER_MORE,
        HOVER_PAGE, OPTION_CHOOSE };

    /** The game's own keys the screen uses; not this mod's to write. */
    public static final String[] GAME = { OPTIONS_ON, OPTIONS_OFF, GUI_DONE, GUI_CANCEL };

    /** Gone in 0.9.228, with its last use: "%s of %s". */
    public static final String REMOVED_PAGE = OWN + "page";

    /**
     * Every chooser's values in the order its dropdown lists them (spec 1.5.1), by the name its words are filed under:
     * {@code category.key}, except the two every wearing family has, which share their words across families.
     */
    public static final Map<String, List<String>> CHOOSERS;

    /** The values whose line cannot say enough alone, so they carry a detail: {@code setting.value}. */
    public static final List<String> DETAILED;

    static {
        Map<String, List<String>> choosers = new LinkedHashMap<String, List<String>>();
        List<String> rungs = Arrays.asList("very_slow", "slow", "default", "brisk", "quick", "custom");
        choosers.put("presets.quality", Arrays.asList("potato", "low", "default", "high", "ultra", "custom"));
        choosers.put("presets.wear", rungs);
        choosers.put("presets.healing", rungs);
        choosers.put("presets.phases", Arrays.asList("default", "fewer", "coarse", "very_coarse", "custom"));
        choosers.put("presets.depth", Arrays.asList("none", "shallow", "default", "deep", "deepest", "custom"));
        choosers.put("general.physicalDecay", Arrays.asList("real", "visual", "off"));
        choosers.put("general.updateNotice", Arrays.asList("operators", "everyone", "off"));
        choosers.put("general.updateNoticeReleases", Arrays.asList("relevant", "all"));
        choosers.put("client.settingsScreen", Arrays.asList("own", "other"));
        choosers.put("costCurve", Arrays.asList("flat", "sod", "settle", "pack", "glaze"));
        choosers.put(
            "wearPattern",
            Arrays.asList(
                "grass",
                "crack",
                "crack_lite",
                "smooth",
                "smooth_heavy",
                "dirt",
                "dirt_lite",
                "smooth_crack",
                "smooth_dirt",
                "crack_dirt",
                "crack_dirt_lite"));
        CHOOSERS = Collections.unmodifiableMap(choosers);
        DETAILED = Collections.unmodifiableList(
            Arrays.asList(
                "presets.quality.potato",
                "presets.quality.low",
                "presets.quality.default",
                "presets.quality.high",
                "presets.quality.ultra",
                "presets.quality.custom",
                "presets.wear.custom",
                "presets.phases.fewer",
                "presets.phases.coarse",
                "presets.phases.very_coarse",
                "presets.depth.deepest",
                "general.physicalDecay.real",
                "general.physicalDecay.visual",
                "general.updateNotice.operators",
                "general.updateNoticeReleases.relevant",
                "client.settingsScreen.other",
                "costCurve.flat",
                "costCurve.sod",
                "costCurve.settle",
                "costCurve.pack",
                "costCurve.glaze",
                "wearPattern.grass",
                "wearPattern.crack_lite",
                "wearPattern.dirt",
                "wearPattern.dirt_lite",
                "wearPattern.crack_dirt"));
    }

    /** Whether a category is one of the surface families', whose settings share their words. */
    public static boolean family(String category) {
        return category != null && category.startsWith("families.");
    }

    /** The name a chooser's words are filed under: {@code families.grass.costCurve} is {@code costCurve}. */
    public static String chooser(String setting) {
        if (family(setting) && setting.indexOf('.', "families.".length()) >= 0) {
            return setting.substring(setting.lastIndexOf('.') + 1);
        }
        return setting;
    }

    /** A chooser value's title: {@code trmtgtnh.option.presets.quality.potato}. */
    public static String option(String setting, String value) {
        return "trmtgtnh.option." + chooser(setting) + "." + value;
    }

    public static String optionLine(String setting, String value) {
        return option(setting, value) + ".line";
    }

    public static String optionDetail(String setting, String value) {
        return option(setting, value) + ".detail";
    }

    /** A setting's plain title: {@code trmtgtnh.setting.general.globalSpeed}, a family's under {@code family}. */
    public static String title(String category, String key) {
        return "trmtgtnh.setting." + (family(category) ? "family" : category) + "." + key;
    }

    public static String summary(String category, String key) {
        return title(category, key) + ".summary";
    }

    /** Every key of spec 1.5 and 1.5.1 this mod's language file must carry, in order. */
    public static List<String> all() {
        List<String> out = new ArrayList<String>(Arrays.asList(ALL));
        for (Map.Entry<String, List<String>> chooser : CHOOSERS.entrySet()) {
            for (String value : chooser.getValue()) {
                out.add(option(chooser.getKey(), value));
                out.add(optionLine(chooser.getKey(), value));
                if (DETAILED.contains(chooser.getKey() + "." + value)) {
                    out.add(optionDetail(chooser.getKey(), value));
                }
            }
        }
        return out;
    }
}
