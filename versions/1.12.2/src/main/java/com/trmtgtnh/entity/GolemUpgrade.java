package com.trmtgtnh.entity;

import java.util.Locale;

import net.minecraft.item.ItemStack;

import com.trmtgtnh.item.ItemGolemUpgrade;

/**
 * What can be fitted to a Golem of Ways, and what each one changes.
 *
 * <p>
 * One slot and one upgrade at a time, so this is a plain choice rather than a set of flags - and
 * the last two are the choice of having all the others, one of them reliably.
 * Each also renames the golem, and the epithets were chosen as a set: Far, Swift, Sparing, Frugal,
 * Deep, Green, Stout, Fierce and Settled, ending at All - which read aloud is the pun the whole
 * naming exists for. Settled is the one that is two puns: settled ways are fixed habits, and a
 * golem that has settled somewhere is one with a home and stores in it.
 */
public enum GolemUpgrade {

    /** Nothing fitted. */
    NONE("none"),

    /** Works further from its anchor. */
    RANGE("range"),

    /** Works faster. */
    SPEED("speed"),

    /** Spends half the durability on its tools. */
    SPARING("sparing"),

    /** Spends half the blocks on its mending. */
    FRUGAL("frugal"),

    /** Four times the storage. */
    DEEP("deep"),

    /** Holds a band of wear rather than one level, so its round cycles up and down. */
    GREEN("green"),

    /** Twice the health, so it outlasts what it cannot outfight. */
    STOUT("stout"),

    /** Twice the force behind a blow. */
    FIERCE("fierce"),

    /** Works out of the stores around its anchor rather than only out of itself. */
    SETTLED("settled"),

    /** Every one of the above at once. The hardest thing in the mod to make. */
    OMNI("omni"),

    /**
     * Every one of the above at once, and unable to hold on to them.
     *
     * <p>
     * The nine parts bind into this before they bind into anything stable, because nine of them is
     * a full grid and there is nowhere left to put the star that would settle them. What comes out
     * carries the whole set and keeps only some of it at a time - a handful chosen afresh every
     * minute, the storage always among them, so the one thing that would strand what the golem is
     * carrying is the one thing that never flickers.
     *
     * <p>
     * A working part rather than a failed one. It is cheaper than the bound version by exactly a
     * nether star, and what that star buys is knowing which golem you have.
     */
    UNSTABLE("unstable");

    public final String key;

    GolemUpgrade(String key) {
        this.key = key;
    }

    /** The item's registry and texture name, e.g. {@code golem_upgrade_range}. */
    public String itemName() {
        return "golem_upgrade_" + key;
    }

    /** The lang key for the golem's name while this is fitted. */
    public String nameKey() {
        return "entity.trmtgtnh.golem." + key + ".name";
    }

    /** The lang key for the upgrade item's own name. */
    public String itemNameKey() {
        return "item.trmtgtnh." + itemName() + ".name";
    }

    public boolean isOmni() {
        return this == OMNI;
    }

    /**
     * Whether this is one of the two that carry the whole set.
     *
     * <p>
     * The bound one and the loose one. They cost the same to find and the same to be handed, so
     * anywhere that treats "all of them" as a tier rather than as a behaviour wants both - which is
     * the loot table and the questbook, and nothing else. What each of them actually <em>does</em>
     * is asked of the golem rather than of this, because only one of them knows the answer.
     */
    public boolean carriesTheSet() {
        return this == OMNI || this == UNSTABLE;
    }

    /**
     * The upgrades an unstable one chooses from, which is every one that is a single thing.
     *
     * <p>
     * Neither of the two that carry the set, for the obvious reason and a less obvious one: an
     * unstable golem that rolled the bound version would be a stable golem for a minute, which is
     * the one thing this part is not for.
     */
    public static GolemUpgrade[] unstablePool() {
        GolemUpgrade[] all = values();
        int count = 0;
        for (GolemUpgrade upgrade : all) {
            if (upgrade != NONE && !upgrade.carriesTheSet()) count++;
        }
        GolemUpgrade[] out = new GolemUpgrade[count];
        int at = 0;
        for (GolemUpgrade upgrade : all) {
            if (upgrade != NONE && !upgrade.carriesTheSet()) out[at++] = upgrade;
        }
        return out;
    }

    public boolean extendsRange() {
        return this == RANGE || isOmni();
    }

    public boolean isFaster() {
        return this == SPEED || isOmni();
    }

    /** Half the durability off its tools. */
    public boolean sparesTools() {
        return this == SPARING || isOmni();
    }

    /**
     * Half the blocks for its mending, rounding down and never below one; GolemWork.strokeLedger says
     * what that is half of.
     */
    public boolean sparesBlocks() {
        return this == FRUGAL || isOmni();
    }

    public boolean widensStorage() {
        return this == DEEP || isOmni();
    }

    /**
     * Twice the health.
     *
     * <p>
     * An attribute modifier rather than a different base value, so it goes on and comes off with
     * the part: a golem fitted with one is still, underneath, a sixty-health golem, and pulling the
     * part out takes the extra health with it rather than leaving one whose maximum has been
     * quietly rewritten. The sixty itself has no setting today; if it ever gets one this needs no
     * change, which is the other half of why it is a modifier.
     */
    public boolean standsLonger() {
        return this == STOUT || isOmni();
    }

    /**
     * Twice the force behind a blow.
     *
     * <p>
     * Applied where the blow is struck rather than as an attack-damage attribute, for the same
     * reason the golem has never had one: a stat is a thing another mod can find and turn up, and
     * a road-mender that can be quietly made into a weapon is not a road-mender.
     */
    public boolean hitsHarder() {
        return this == FIERCE || isOmni();
    }

    /**
     * Reaches into the containers around its anchor: takes what it needs, puts back what it cannot
     * carry.
     *
     * <p>
     * The only upgrade that touches anything outside the golem, which is why it is the only one
     * with a switch of its own beside the master one. A pack that would rather nothing reached into
     * a player's chests unasked can turn it off and leave the other eight alone.
     */
    public boolean usesStores() {
        return this == SETTLED || isOmni();
    }

    public static GolemUpgrade byOrdinal(int ordinal) {
        GolemUpgrade[] all = values();
        return ordinal < 0 || ordinal >= all.length ? NONE : all[ordinal];
    }

    public static GolemUpgrade byKey(String key) {
        if (key != null) {
            String wanted = key.toLowerCase(Locale.ROOT);
            for (GolemUpgrade upgrade : values()) {
                if (upgrade.key.equals(wanted)) return upgrade;
            }
        }
        return NONE;
    }

    /** Which upgrade a stack is, or {@link #NONE} for anything that is not one. */
    public static GolemUpgrade of(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return NONE;
        if (stack.getItem() instanceof ItemGolemUpgrade) return ((ItemGolemUpgrade) stack.getItem()).upgrade();
        return NONE;
    }

    /** Every upgrade that is actually an item, which is all of them but {@link #NONE}. */
    public static GolemUpgrade[] real() {
        GolemUpgrade[] all = values();
        GolemUpgrade[] out = new GolemUpgrade[all.length - 1];
        System.arraycopy(all, 1, out, 0, out.length);
        return out;
    }
}
