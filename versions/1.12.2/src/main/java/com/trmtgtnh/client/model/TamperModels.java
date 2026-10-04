package com.trmtgtnh.client.model;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.ItemMeshDefinition;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ItemChunkTamper;
import com.trmtgtnh.item.TamperArt;

/**
 * Which model draws a tamper, chosen from the grade in the stack.
 *
 * <p>
 * The other edition does this inside the item: 1.7.10 registers an icon per drawn grade while the
 * atlas is stitched and answers {@code getIcon} with one of them, and both of those are methods on
 * {@link Item}. There is no such question here - a 1.12.2 item is drawn from a baked model, and which
 * model it is gets chosen before any drawing happens - so the same set of names is declared as model
 * variants and this maps a stack to one of them.
 *
 * <p>
 * A class of its own on the client side rather than a few methods on the item, and that is a real
 * difference rather than tidiness. The item is shared code that a dedicated server loads; everything
 * here names the client's model loader. Written as the mesh definition itself rather than handing one
 * out anonymously, because an anonymous class is its own class file with none of this one's
 * annotation on it, and the side scan would report it - rightly, on the only evidence it has.
 *
 * <p>
 * Parameterised by the tool's base name, because the chunk tamper is drawn from the same grades and
 * the same rule, under its own set of pictures.
 */
@SideOnly(Side.CLIENT)
public final class TamperModels implements ItemMeshDefinition {

    private final String base;

    private TamperModels(String base) {
        this.base = base;
    }

    /**
     * Declares every model this item can be drawn as, and the rule that picks one.
     *
     * <p>
     * The drawn set rather than the configured one, and for the reason it is the drawn set there:
     * these names are resolved when models are loaded, and a name with no file behind it becomes the
     * missing-model black cube and a line in the log for every stack of it. What a pack author can
     * type is unbounded; what somebody has drawn is a list.
     */
    public static void register(Item item, String base) {
        List<ResourceLocation> variants = new ArrayList<ResourceLocation>();
        variants.add(new ResourceLocation(Trmt.MODID, base));
        for (String key : TamperArt.drawn()) {
            variants.add(new ResourceLocation(Trmt.MODID, TamperArt.iconFor(base, key)));
        }
        ModelBakery.registerItemVariants(item, variants.toArray(new ResourceLocation[variants.size()]));
        ModelLoader.setCustomMeshDefinition(item, new TamperModels(base));
    }

    /**
     * The plain registration, for a tool drawn with one picture whatever it is made of.
     *
     * <p>
     * The Wayfarer's, and the reason its {@code gradedIcons} answers no: the grade was always a
     * statement about material, and that tool is past caring about material. Declaring the graded
     * set for it would name forty-three pictures nobody has drawn.
     */
    public static void registerOne(Item item, String name) {
        ModelLoader
            .setCustomModelResourceLocation(item, 0, new ModelResourceLocation(Trmt.MODID + ":" + name, "inventory"));
    }

    @Override
    public ModelResourceLocation getModelLocation(ItemStack stack) {
        return new ModelResourceLocation(
            Trmt.MODID + ":" + TamperArt.iconFor(base, ItemChunkTamper.gradeOf(stack).key),
            "inventory");
    }
}
