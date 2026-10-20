package com.trmtgtnh.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ItemChunkTamper;
import com.trmtgtnh.item.TamperArt;
import com.trmtgtnh.item.TamperGrade;

/**
 * Which picture draws a tamper, chosen from the grade in the stack. This mod's third answer to one
 * question.
 *
 * <p>
 * 1.7.10 registers an icon per drawn grade while the atlas is stitched and answers {@code getIcon}
 * with one of them. 1.12.2 cannot: an item is drawn from a baked model there and which model is
 * chosen before any drawing happens, so that edition declares the same names as model variants and
 * hands the loader an {@code ItemMeshDefinition} that maps a stack to one. Neither exists here -
 * {@code ItemMeshDefinition} went at 1.13 - and what replaced it is better than both, because it is
 * data.
 *
 * <p>
 * <strong>An item's picture is chosen by its item file, and a range dispatch there picks a model by a
 * named number read off the stack.</strong> So the grade becomes one number, declared once here, and
 * the item files {@code items/tamper.json} and {@code items/chunk_tamper.json} each list one entry per
 * drawn grade against it - up to 1.21.1 they were overrides in the item's model, which 1.21.4 replaced
 * with the item files. The choosing is then vanilla's, in a mechanism resource packs already
 * understand - a pack can redraw one metal, or add a condition of its own, without this class knowing.
 *
 * <h2>The number, and why it is sorted</h2>
 *
 * <p>
 * The index of the grade's key in {@link TamperArt#drawn()}, <strong>sorted</strong>. That set is a
 * {@code HashSet} and its iteration order is not a promise anybody made, so sorting is what makes
 * the number the same in the model files as in this method. The sort is alphabetical rather than the
 * order they are declared in for the same reason: one of those is a property of the text and the
 * other is a property of the data structure.
 *
 * <p>
 * {@code TamperModelOverridesTest} compares the two directly - every entry in both item files,
 * against this list - so a grade added to the drawn set and not to the files, or added to the files
 * in the wrong place, fails the build rather than drawing the wrong metal.
 *
 * <p>
 * A grade nobody has drawn, and a stack with no grade at all, answer -1: it is below every entry's
 * threshold, so the file's fallback, the item's own plain picture, is drawn. That is what the other editions do with an unknown metal, and
 * the reason {@code iconFor} falls back the way it does.
 *
 * <h2>Where it is registered</h2>
 *
 * <p>
 * By each loader, because vanilla's map of numeric item properties is closed to mods: NeoForge posts
 * an event to register one, and Forge's client setup and Fabric's client entry put it into the map by
 * name. All three hand it {@link Grade}'s codec under {@link #GRADE}, so the rule is here and only the
 * door differs.
 */
public final class TamperModels {

    /** The name the model files know this number by. */
    public static final Identifier GRADE = Identifier.fromNamespaceAndPath(Trmt.MODID, "grade");

    /** The drawn grades, sorted, which is the order the model files are written in. */
    private static final List<String> ORDER = sorted();

    private TamperModels() {}

    private static List<String> sorted() {
        List<String> keys = new ArrayList<String>(TamperArt.drawn());
        Collections.sort(keys);
        return Collections.unmodifiableList(keys);
    }

    /** The drawn grades in the order the overrides are written in, for the generator and the test. */
    public static List<String> order() {
        return ORDER;
    }

    /** The number one stack answers, which is the index of its grade among the drawn ones. */
    public static float gradeOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1.0F;
        TamperGrade grade = ItemChunkTamper.gradeOf(stack);
        if (grade == null || grade.key == null) return -1.0F;
        int at = ORDER.indexOf(grade.key.toLowerCase(java.util.Locale.ROOT));
        return at;
    }

    /**
     * The rule, as the game wants it.
     *
     * <p>
     * A method rather than a field, so each loader's registration reads as what it is - and so that
     * nothing outside a client ever holds one of these: an item model property is a client
     * interface and this class is a client class, which is why it is in {@code client.model} rather
     * than beside the item.
     *
     * <p>
     * After 1.21.1 an item's picture is chosen by its client item file ({@code assets/<mod>/items/<item>.json}) rather
     * than by overrides in its model, and a number read off the stack is a {@code RangeSelectItemModelProperty} named in
     * that file's {@code range_dispatch}, registered under {@link #GRADE} with its codec, {@link Grade#MAP_CODEC}.
     * {@code ItemPropertyFunction} and {@code ClampedItemPropertyFunction} are gone by 26.1. The number is 1.21.1's,
     * the index of the grade among the drawn ones and -1 for none, unclamped; a range dispatch takes the entry with the
     * highest threshold at or below it and the fallback below the lowest, which is what the forty-four overrides chose.
     */
    public static net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty grader() {
        return Grade.INSTANCE;
    }

    /**
     * The same rule. On 1.21.1 Fabric's registration took a clamped function, whose clamp this undid; by 26.1 there is
     * one kind of numeric property and nothing clamps it, so this is {@link #grader}.
     */
    public static net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty unclampedGrader() {
        return Grade.INSTANCE;
    }

    /** The grade, as a property an item file can name: {@link #gradeOf} for every stack, with nothing to configure. */
    public static final class Grade
        implements net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty {

        public static final Grade INSTANCE = new Grade();

        /** Nothing to read from the file: the property is the rule alone. */
        public static final com.mojang.serialization.MapCodec<Grade> MAP_CODEC = com.mojang.serialization.MapCodec
            .unit(INSTANCE);

        private Grade() {}

        @Override
        public float get(ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
            net.minecraft.world.entity.ItemOwner owner, int seed) {
            return gradeOf(stack);
        }

        @Override
        public com.mojang.serialization.MapCodec<Grade> type() {
            return MAP_CODEC;
        }
    }
}

