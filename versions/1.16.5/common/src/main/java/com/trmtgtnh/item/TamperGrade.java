package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.util.OreNames;
import com.trmtgtnh.config.TrmtConfig;

/**
 * A grade of chunk tamper: a name, a material and a number of uses.
 *
 * <p>
 * Carried in a stack's NBT rather than being an item of its own, so the set can follow whatever
 * a pack happens to contain without any of it reaching the save's id map. See
 * {@link ItemChunkTamper} for why that distinction is the whole design.
 *
 * <p>
 * The set comes from config, seeded with the four vanilla materials and a handful of the ones a
 * GregTech pack is likely to have. Deliberately a list rather than a sweep of the ore
 * dictionary: GregTech alone registers some nine hundred materials, and a creative tab with
 * nine hundred tampers in it is not a feature. A pack that wants a different set says so.
 */
public final class TamperGrade {

    private static final int DURABILITY_CEILING = 32767;

    private static volatile Map<String, TamperGrade> grades = new LinkedHashMap<String, TamperGrade>();
    private static volatile List<TamperGrade> obtainable = new ArrayList<TamperGrade>();

    /** Short name, used in NBT, in the lang key and in the recipe. */
    public final String key;

    /** The ore dictionary name this grade is made of and repaired with. */
    public final String ore;

    private final int declaredUses;

    private final int declaredReach;

    private TamperGrade(String key, String ore, int declaredUses, int declaredReach) {
        this.key = key;
        this.ore = ore;
        this.declaredUses = declaredUses;
        this.declaredReach = declaredReach;
    }

    /**
     * How far a plain tamper of this grade reaches when it mends a patch, in blocks.
     *
     * <p>
     * Optional in the config and computed from durability when it is left out, which is what
     * keeps every config already written to disk valid: those entries have three fields, and a
     * fourth that has to be there would have turned every one of them into a warning line.
     *
     * <p>
     * Durability is the right thing to compute it from because the two say the same thing about
     * a material. Gold is the exception the vanilla tool set makes on purpose - wide and soft -
     * so the shipped list states gold's reach outright rather than letting it come out as one.
     */
    public int reach() {
        if (declaredReach > 0) return Math.min(declaredReach, 4);
        if (declaredUses <= 512) return 1;
        return declaredUses <= 2048 ? 2 : 3;
    }

    /**
     * Uses before it breaks.
     *
     * <p>
     * Clamped hard, and this is not defensive tidiness. A stack's damage is a {@code short} both
     * on disk and on the wire - {@code ItemStack.writeToNBT} narrows it with {@code i2s} and the
     * packet writer sends {@code writeShort} - so a tool given more uses than a short can hold
     * would wrap negative the first time the world saved. GregTech hands out materials with
     * durabilities in the tens of thousands, so this is a real ceiling and not a theoretical one.
     */
    public int uses() {
        int scaled = Math.round(declaredUses * Math.max(0.01f, TrmtConfig.tamperDurabilityScale));
        if (scaled < 1) return 1;
        return scaled > DURABILITY_CEILING ? DURABILITY_CEILING : scaled;
    }

    /**
     * The uses this grade's entry states, before tamperDurabilityScale and the short ceiling.
     *
     * <p>
     * For comparing grades with one another rather than for wearing a tool down. The scale is the
     * same for every grade, but rounding a small one and capping a large one both turn different
     * figures into equal ones, and which grade the Wayfarer is built from should not move with a
     * setting that is read live.
     */
    int declaredUses() {
        return declaredUses;
    }

    /** Whether this stack is the material an anvil would mend this grade with. */
    public boolean matches(ItemStack material) {
        if (material == null || material.isEmpty()) return false;
        // One question rather than a walk through every name this stack answers to. The ore
        // dictionary let you ask a stack what it was; a tag is asked whether it holds the item, which
        // is the same answer from the other end and is what OreNames turns the pack's name into.
        return OreNames.matches(material, ore);
    }

    public String displayName() {
        return key.substring(0, 1)
            .toUpperCase(Locale.ROOT) + key.substring(1);
    }

    /** True when the pack actually supplies this grade's material. */
    public boolean present() {
        return OreNames.present(ore);
    }

    // ------------------------------------------------------------------
    // The set
    // ------------------------------------------------------------------

    /**
     * Reads the configured grades. Called once everything else has registered its items, so the
     * ore dictionary can be asked what the pack really has.
     */
    public static void resolve() {
        Map<String, TamperGrade> built = new LinkedHashMap<String, TamperGrade>();
        List<TamperGrade> found = new ArrayList<TamperGrade>();

        for (String raw : TrmtConfig.tamperGrades) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":");
            if (parts.length != 3 && parts.length != 4) {
                Trmt.LOG.warn("Tamper grade '{}' is not name:oreName:uses[:reach], skipping", entry);
                continue;
            }
            int uses;
            try {
                uses = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException notANumber) {
                Trmt.LOG.warn("Tamper grade '{}' has no readable use count, skipping", entry);
                continue;
            }
            // Unreadable rather than missing is still worth having the grade for: reach has a
            // sensible answer computed from durability, and losing the whole material over a
            // typo in an optional field would be the more surprising behaviour.
            int reach = 0;
            if (parts.length == 4) {
                try {
                    reach = Integer.parseInt(parts[3].trim());
                } catch (NumberFormatException notANumber) {
                    Trmt.LOG.warn("Tamper grade '{}' has no readable reach, working it out instead", entry);
                }
            }
            TamperGrade grade = new TamperGrade(
                parts[0].trim()
                    .toLowerCase(Locale.ROOT),
                parts[1].trim(),
                Math.max(1, uses),
                Math.max(0, reach));
            built.put(grade.key, grade);
            if (grade.present()) found.add(grade);
        }

        grades = built;
        obtainable = found;
        Trmt.LOG.info(
            "{} tamper grades configured, {} of them available in this pack",
            Integer.valueOf(built.size()),
            Integer.valueOf(found.size()));
    }

    /** Every grade this pack can actually supply, in configured order. */
    public static List<TamperGrade> available() {
        List<TamperGrade> found = obtainable;
        if (found.isEmpty()) {
            List<TamperGrade> only = new ArrayList<TamperGrade>();
            only.add(fallback());
            return only;
        }
        return Collections.unmodifiableList(found);
    }

    public static TamperGrade byKey(String key) {
        if (key != null) {
            TamperGrade grade = grades.get(key.toLowerCase(Locale.ROOT));
            if (grade != null) return grade;
        }
        return fallback();
    }

    /**
     * What an unmarked stack is.
     *
     * <p>
     * The first configured grade, or plain iron when the list is empty or unreadable. A tamper
     * that lost its grade should still be a tamper rather than a crash.
     */
    public static TamperGrade fallback() {
        for (TamperGrade grade : grades.values()) {
            return grade;
        }
        return new TamperGrade("iron", "ingotIron", 512, 1);
    }

    /** The sprite this grade's plain tamper is drawn with. */
    public String plainIcon() {
        return TamperArt.iconFor("tamper", key);
    }

    /** The sprite this grade's chunk tamper is drawn with. */
    public String chunkIcon() {
        return TamperArt.iconFor("chunk_tamper", key);
    }
}
