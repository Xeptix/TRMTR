package com.trmtgtnh.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.entity.EntityGolemOfWays;

/**
 * Puts the tool in a golem's free fist.
 *
 * <p>
 * A layer runs after the model has been drawn and while the arm angles for this frame are still set,
 * so posting off the arm reproduces the pose exactly and a tool hung there swings with it for
 * nothing. That is what lets the tool be carried at rest as well as swung: the arm hangs at the
 * golem's side when it has nothing to do, and the tamper hangs with it, because both are drawn from
 * the same angles. An unarmed golem holds nothing and this costs it one empty-stack check.
 *
 * <p>
 * <strong>Most of the other edition's version of this is gone, and should be.</strong> There the
 * transform for a long-handled tool is applied here by hand - a scale, a hundred degrees about one
 * axis and forty-five about another, copied out of vanilla - with a second branch for a flat sprite
 * that nothing in the mod takes, and after it a two-branch draw that loops over an item's render
 * passes and tints each one. In 1.12.2 an item is a model and carries its own
 * {@code thirdperson_righthand} display transform, which is where {@code item/handheld} puts exactly
 * that angle, and tint is an {@code IItemColor} the renderer applies itself. So this is vanilla's own
 * way into a hand, two numbers of the golem's own, and one call.
 *
 * <p>
 * The matrix is popped from a finally, because the whole of an entity's drawing sits inside one
 * catch-all up in the base class: a throw between the push and the pop would be swallowed into a log
 * line, and what the player would see instead is every model drawn after this one in the frame slowly
 * sliding off its own feet.
 */
@SideOnly(Side.CLIENT)
public class LayerGolemTool implements LayerRenderer<EntityGolemOfWays> {

    /**
     * How much bigger than a player's the tool is drawn.
     *
     * <p>
     * A tool sized for a hand at chest height reads as a twig in a fist this size, and the golem is
     * half again the height of the man who handed it over. Only a little over one, because an item
     * model already carries a third-person scale of its own and this multiplies it.
     */
    private static final float TOOL_SIZE = 1.15F;

    private final RenderGolemOfWays renderer;

    public LayerGolemTool(RenderGolemOfWays renderer) {
        this.renderer = renderer;
    }

    @Override
    public void doRenderLayer(EntityGolemOfWays golem, float limbSwing, float limbSwingAmount, float partialTicks,
        float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        ItemStack shown = golem.getHeldItemMainhand();
        if (shown == null || shown.isEmpty()) return;

        GlStateManager.pushMatrix();
        try {
            renderer.golemModel()
                .postRenderToolHand(0.0625F);
            // The middle of the fist, in the arm's own units: eleven across and twenty-five and a
            // half down from the shoulder is where that box is drawn.
            GlStateManager.translate(-0.6875F, 1.59375F, 0F);
            // The two turns from vanilla's LayerHeldItem, and only those two. They are what an item
            // model's thirdperson_righthand transform is written against, so they are not the golem's
            // to change - but the small translation that follows them in vanilla is, and is dropped:
            // it steps from a player's arm origin into a player's hand, and the line above has already
            // stepped into a fist a good deal further down a much longer arm. Keeping both put the
            // tamper two-thirds of a block out past the fist, with its handle through the chest.
            GlStateManager.rotate(-90F, 1F, 0F, 0F);
            GlStateManager.rotate(180F, 0F, 1F, 0F);
            GlStateManager.scale(TOOL_SIZE, TOOL_SIZE, TOOL_SIZE);
            Minecraft.getMinecraft()
                .getItemRenderer()
                .renderItemSide(golem, shown, ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, false);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1F, 1F, 1F, 1F);
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
