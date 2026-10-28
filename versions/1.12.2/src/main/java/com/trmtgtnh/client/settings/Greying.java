package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which settings do nothing while a switch says so, in one table (S6).
 *
 * <p>
 * Every rule here is one the settings' own comments state, or one the code makes true and a comment implies; each says
 * where. The screen greys such a setting while its switch stands that way - its name {@code GREYED}, its control
 * disabled but still showing the value, its hover saying "Turned off by" the switch - and never stops anybody saving
 * it: a greyed value is kept, and comes back into force with the switch.
 *
 * <p>
 * A partial rule never greys. {@code multipliers.mobs} with {@code fromCustom} off still holds its zeros ("a mob named
 * at 0 still never wears, so the magic tamper's stop button always holds"), so greying it would say it does nothing
 * when it does something; its hover says {@code greyed.partial} instead (Xep, 2026-10-21: not greyed, and its
 * hover explains).
 */
public final class Greying {

    /** One setting and the switch it waits on. */
    public static final class Rule {

        /** The setting's category, as the file names it ({@code multipliers}, {@code families.grass}). */
        public final String category;

        /** The setting's key. */
        public final String key;

        /** The switch's category. */
        public final String switchCategory;

        /** The switch's key: a boolean setting. */
        public final String switchKey;

        /** Whether the switch only takes part of the setting's effect away, so it is explained and never greyed. */
        public final boolean partial;

        /**
         * Whether the setting does nothing while its switch is on rather than off - true for one rule,
         * {@code client.wearCarriesWithDepth}, which "only does anything while" {@code wearNeverStepsBack} "is off".
         */
        public final boolean whileOn;

        /** A setting that does nothing while its switch is off. */
        public Rule(String category, String key, String switchCategory, String switchKey, boolean partial) {
            this(category, key, switchCategory, switchKey, partial, false);
        }

        /** A setting that does nothing while its switch stands at {@code whileOn}. */
        public Rule(String category, String key, String switchCategory, String switchKey, boolean partial,
            boolean whileOn) {
            this.category = category;
            this.key = key;
            this.switchCategory = switchCategory;
            this.switchKey = switchKey;
            this.partial = partial;
            this.whileOn = whileOn;
        }

        /** The setting as {@code category.key}. */
        public String setting() {
            return category + "." + key;
        }

        /** The switch as {@code category.key}. */
        public String switchSetting() {
            return switchCategory + "." + switchKey;
        }

        @Override
        public String toString() {
            return setting() + (partial ? " partly" : "") + " by " + switchSetting() + (whileOn ? " on" : " off");
        }
    }

    /** The families whose settings wear in stages, as the file names their headings ({@code families.<key>}). */
    private static final String[] STAGED = { "grass", "dirt", "sand", "gravel", "stone", "cobble", "nether", "end",
        "snow", "ice" };

    /** The table, in the screen's order of tabs. */
    public static final List<Rule> RULES;

    static {
        List<Rule> rules = new ArrayList<Rule>();

        // client: "Turn it off to get the old behaviour back; wearCarriesWithDepth only does anything while it is off"
        // (wearNeverStepsBack's comment).
        rules.add(new Rule("client", "wearCarriesWithDepth", "client", "wearNeverStepsBack", false, true));
        // client: the layer behind a worn block "is gone from any block that wears its family's generic art instead:
        // every block while client.perSurfaceTextures is off" (innerLayerTextures), and the moving layer is nothing
        // "for a block with no worn pictures of its own, which is every block while client.perSurfaceTextures is off"
        // (animateInnerLayers). Seeing through it, its memory and its uploads are of that same layer.
        for (String key : new String[] { "innerLayerTextures", "seeThroughInnerLayers", "animateInnerLayers",
            "innerLayerAnimationBudgetMb", "innerLayerUploadsPerTick" }) {
            rules.add(new Rule("client", key, "client", "perSurfaceTextures", false));
        }

        // general: flattenWearUnderBlocks "Has no effect unless hideWearUnderBlocks is on".
        rules.add(new Rule("general", "flattenWearUnderBlocks", "general", "hideWearUnderBlocks", false));
        // general: the chance of a drop is the chance of what wearDropsEnabled switches.
        rules.add(new Rule("general", "wearDropChance", "general", "wearDropsEnabled", false));

        // healing: natural recovery's own settings, which healing.enabled switches ("Whether untouched ground recovers
        // over time"; ErosionEngine returns before healing while it is off). Not the paid mending - bone meal, the
        // tampers, their experience and the enchantments - which work whether or not ground mends by itself.
        for (String key : new String[] { "pauseWhenEmpty", "healingRate", "wearDecayPerDay" }) {
            rules.add(new Rule("healing", key, "healing", "enabled", false));
        }

        // multipliers: each group's number by its switch, the leashed number by fromLeashedMobs, and each cap's number
        // by its cap. The mobs list only partly: its zeros hold with fromCustom off.
        rules.add(new Rule("multipliers", "leashed", "multipliers", "fromLeashedMobs", false));
        rules.add(new Rule("multipliers", "hostile", "multipliers", "fromHostileMobs", false));
        rules.add(new Rule("multipliers", "passive", "multipliers", "fromPassiveMobs", false));
        rules.add(new Rule("multipliers", "villager", "multipliers", "fromVillagers", false));
        rules.add(new Rule("multipliers", "mobs", "multipliers", "fromCustom", true));
        rules.add(new Rule("multipliers", "playersPerChunk", "multipliers", "capPlayers", false));
        rules.add(new Rule("multipliers", "hostilePerChunk", "multipliers", "capHostileMobs", false));
        rules.add(new Rule("multipliers", "passivePerChunk", "multipliers", "capPassiveMobs", false));
        rules.add(new Rule("multipliers", "villagersPerChunk", "multipliers", "capVillagers", false));
        rules.add(new Rule("multipliers", "customPerChunk", "multipliers", "capCustom", false));

        // families: a family's own recovery is natural recovery, so healing.enabled switches it too; and what a family
        // wears through to is read only while its switch is on (TrmtConfig.wearsThroughTo: grass by
        // grassWearsThroughToDirt, every other family by wearThroughToOtherSurfaces).
        for (String family : STAGED) {
            String category = "families." + family;
            for (String key : new String[] { "healDaysPerStage", "healsOnlyWhenWet", "wetRecoverySpeed" }) {
                rules.add(new Rule(category, key, "healing", "enabled", false));
            }
            rules.add(
                new Rule(
                    category,
                    "wearsThroughTo",
                    "general",
                    "grass".equals(family) ? "grassWearsThroughToDirt" : "wearThroughToOtherSurfaces",
                    false));
        }

        // golem: reinforces "rides on golem.pickUpDrops as well ... with that off this does nothing whatever it says";
        // its wearDropChance's "master switch is still wearDrops in general".
        rules.add(new Rule("golem", "reinforces", "golem", "pickUpDrops", false));
        rules.add(new Rule("golem", "wearDropChance", "general", "wearDropsEnabled", false));

        RULES = Collections.unmodifiableList(rules);
    }

    private Greying() {}

    /** The rule a setting waits on, or null where it waits on none. */
    public static Rule governing(String category, String key) {
        for (Rule rule : RULES) {
            if (rule.category.equals(category) && rule.key.equals(key)) return rule;
        }
        return null;
    }

    /**
     * Whether a setting under this rule is greyed while its switch is {@code switchOn}. No rule greys nothing, and a
     * partial rule never greys.
     */
    public static boolean greyed(Rule rule, boolean switchOn) {
        if (rule == null || rule.partial) return false;
        return switchOn == rule.whileOn;
    }

    /** Whether the hover adds {@code greyed.partial}: a partial rule whose switch stands the way it waits on. */
    public static boolean explained(Rule rule, boolean switchOn) {
        return rule != null && rule.partial && switchOn == rule.whileOn;
    }
}
