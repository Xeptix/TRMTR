package com.trmtgtnh.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
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
 * <strong>An item model may carry overrides, each a picture and a condition for using it, and a
 * condition is a named number read off the stack.</strong> So the grade becomes one number, declared
 * once here, and {@code tamper.json} and {@code chunk_tamper.json} each list forty-four overrides
 * against it. The choosing is then vanilla's, in a mechanism resource packs already understand - a
 * pack can redraw one metal, or add a condition of its own, without this class knowing.
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
 * {@code TamperModelOverridesTest} compares the two directly - every override in both model files,
 * against this list - so a grade added to the drawn set and not to the files, or added to the files
 * in the wrong place, fails the build rather than drawing the wrong metal.
 *
 * <p>
 * A grade nobody has drawn, and a stack with no grade at all, answer -1: no override matches and the
 * model's own plain picture is drawn. That is what the other editions do with an unknown metal, and
 * the reason {@code iconFor} falls back the way it does.
 *
 * <h2>Where it is registered</h2>
 *
 * <p>
 * By each loader, because the one vanilla method that takes a property is private until 1.17: Forge
 * patches it public and Fabric is handed one line of access widener. Both reach the same method and
 * both hand it {@link #grader}, so the rule is here and only the door differs.
 */
public final class TamperModels {

    /** The name the model files know this number by. */
    public static final ResourceLocation GRADE = new ResourceLocation(Trmt.MODID, "grade");

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
     * nothing outside a client ever holds one of these: {@code ItemPropertyFunction} is a client
     * interface and this class is a client class, which is why it is in {@code client.model} rather
     * than beside the item.
     */
    public static ItemPropertyFunction grader() {
        return (stack, level, holder) -> gradeOf(stack);
    }
}
