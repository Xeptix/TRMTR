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
 * <strong>The tool is held as 1.7.10 holds it, turn for turn</strong> (GO62, 0.9.224; Xep: "Let's fix this in
 * 0.9.224"). Until then this layer handed the item to its own {@code thirdperson_righthand} transform, which holds a
 * tool the way a 1.12.2 player does: from the south 1.7.10's golem showed the tamper's flat face and this one its
 * edge. 1.7.10 turns the fist three times - a scale of 0.9 that also flips the vertical, a hundred degrees down about
 * the arm and forty-five about the vertical - and its item renderer then turns any flat item five times more before
 * drawing it as a slab a block across. {@link #HELD} is those eight steps, and the model is drawn with no transform of
 * its own.
 *
 * <p>
 * Not copied as written, though, for one reason: the flip. A negative scale turns every face inside out, which shows
 * the far face of the slab once culling is on and lights it as facing away. So the whole chain is mirrored left to
 * right instead - which a sprite drawn on both faces of a slab cannot show - and the flip becomes half a turn about
 * the arm's own axis: the turns about the vertical and about the depth change sign, so do the steps across, and the
 * last step lays this edition's centered item model where 1.7.10's slab lies, its sprite reading from the far corner
 * as 1.7.10's does. {@code GolemToolIsHeldAsTheReferenceHoldsItTest} multiplies both chains out and checks every
 * corner of the sprite lands in the same place.
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
     * The steps from the fist to the drawn item, after the half turn that stands in for 1.7.10's flip. Each is a kind -
     * {@code 'T'} a step, {@code 'S'} a scale, {@code 'X'}, {@code 'Y'} or {@code 'Z'} a turn in degrees about that
     * axis - and its numbers. In 1.7.10's words: the golem's own 0.9, -100 about X and 45 about Y; its item renderer's
     * step down 0.3, 1.5, 50 about Y, 335 about Z and the step to the slab's corner; then where this edition's model
     * sits in that slab.
     */
    static final float[][] HELD = {
        { 'S', 0.9F, 0.9F, 0.9F },
        { 'X', -100F },
        { 'Y', -45F },
        { 'T', 0F, -0.3F, 0F },
        { 'S', 1.5F, 1.5F, 1.5F },
        { 'Y', -50F },
        { 'Z', -335F },
        { 'T', 0.9375F, -0.0625F, 0F },
        { 'T', -0.5F, 0.5F, -0.03125F } };

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
            // 1.7.10's flip of the vertical, as half a turn about the arm: see the class comment.
            GlStateManager.rotate(180F, 0F, 0F, 1F);
            for (float[] step : HELD) {
                switch ((char) step[0]) {
                    case 'T':
                        GlStateManager.translate(step[1], step[2], step[3]);
                        break;
                    case 'S':
                        GlStateManager.scale(step[1], step[2], step[3]);
                        break;
                    case 'X':
                        GlStateManager.rotate(step[1], 1F, 0F, 0F);
                        break;
                    case 'Y':
                        GlStateManager.rotate(step[1], 0F, 1F, 0F);
                        break;
                    default:
                        GlStateManager.rotate(step[1], 0F, 0F, 1F);
                        break;
                }
            }
            Minecraft.getMinecraft()
                .getItemRenderer()
                .renderItemSide(golem, shown, ItemCameraTransforms.TransformType.NONE, false);
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
