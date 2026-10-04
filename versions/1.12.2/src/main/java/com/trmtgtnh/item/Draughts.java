package com.trmtgtnh.item;

import net.minecraft.potion.Potion;

import com.trmtgtnh.config.TrmtConfig;

/**
 * The two draughts, and which effect each one pours.
 *
 * <p>
 * An enum rather than two fields, for the same reason {@code GuideBook} and {@code GolemUpgrade} are
 * enums: registration, recipes, loot and the creative tab all want to walk the set, and a set that
 * can be walked never grows a third member somebody forgot to add to one of those four lists.
 */
public enum Draughts {

    /**
     * Milkucha's own. While it lasts, that walker wears no ground whatever.
     *
     * <p>
     * Three minutes, which is the original's 3600 ticks exactly, and the figure on the bottle in the
     * screenshots this was built from.
     */
    LIGHTNESS("draught_lightness"),

    /** Its opposite, which the original mod has no equivalent of. Several times the mark. */
    HEAVYFOOT("draught_heavyfoot");

    private final String itemName;

    Draughts(String itemName) {
        this.itemName = itemName;
    }

    /** The registry name, which is also the texture name and the stem of the lang key. */
    public String itemName() {
        return itemName;
    }

    /** The effect this one pours. */
    public Potion effect() {
        return this == LIGHTNESS ? ModPotions.LIGHTNESS : ModPotions.HEAVY;
    }

    /** How long a draught of this lasts, in seconds, as the config has it. */
    public int seconds() {
        int wanted = this == LIGHTNESS ? TrmtConfig.lightnessSeconds : TrmtConfig.heavyFootSeconds;
        return wanted < 0 ? 0 : wanted;
    }

    /** Whether this one's effect is switched on. The item exists either way; see ModPotions. */
    public boolean enabled() {
        if (!TrmtConfig.potionsEnabled) return false;
        return this == LIGHTNESS ? TrmtConfig.potionLightness : TrmtConfig.potionHeavyFoot;
    }

    /** The key under which this draught's own explanation lives. */
    public String tooltipKey() {
        return "trmtgtnh.draught." + name().toLowerCase(java.util.Locale.ROOT) + ".tip";
    }
}
