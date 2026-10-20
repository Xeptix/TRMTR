package com.trmtgtnh.client.render;

import net.minecraft.client.renderer.entity.layers.RenderLayer;

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
 * tool the way a 1.16.5 player does: from the south 1.7.10's golem showed the tamper's flat face and this one its
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
public class LayerGolemTool
    extends RenderLayer<GolemRenderState, ModelGolemOfWays> {

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
        super(renderer);
        this.renderer = renderer;
    }

    /**
     * Hangs the tool off the fist and hands it to the game to draw, from 1.21.9.
     *
     * <p>
     * The arm is posed from the golem's state just before the layers are asked, as it was posed from the golem in
     * 1.21.1, so the same eight steps land the tool in the same place; the item was resolved to its model when the
     * golem was read (see {@link RenderGolemOfWays#extractRenderState}).
     */
    @Override
    public void submit(com.mojang.blaze3d.vertex.PoseStack pose,
        net.minecraft.client.renderer.SubmitNodeCollector buffers, int light, GolemRenderState golem,
        float netHeadYaw, float headPitch) {
        if (golem.tool.isEmpty()) return;

        pose.pushPose();
        try {
            renderer.golemModel()
                .postRenderToolHand(pose);
            // The middle of the fist, in the arm's own units: eleven across and twenty-five and a
            // half down from the shoulder is where that box is drawn.
            pose.translate(-0.6875D, 1.59375D, 0.0D);
            // 1.7.10's flip of the vertical, as half a turn about the arm: see the class comment.
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180F));
            for (float[] step : HELD) {
                switch ((char) step[0]) {
                    case 'T':
                        pose.translate(step[1], step[2], step[3]);
                        break;
                    case 'S':
                        pose.scale(step[1], step[2], step[3]);
                        break;
                    case 'X':
                        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(step[1]));
                        break;
                    case 'Y':
                        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(step[1]));
                        break;
                    default:
                        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(step[1]));
                        break;
                }
            }
            // Resolved with the golem's world and a seed of nought, as 1.21.1's drawing call was given; the golem's
            // tool picks among no variants.
            golem.tool.submit(pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                golem.outlineColor);
        } finally {
            // No color to put back: there is no global color to have changed. The try/finally
            // stays for the matrix, which there still is.
            pose.popPose();
        }
    }
}
