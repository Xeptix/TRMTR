package com.trmtgtnh.client.render;

import net.minecraft.client.renderer.entity.layers.RenderLayer;

import com.trmtgtnh.entity.EntityGolemOfWays;

/**
 * Draws a golem's eyes lit, whatever the light where it is standing.
 *
 * <p>
 * The spider's trick, which is what both versions offer: the whole model again, in a texture that is
 * black everywhere except the few places the golem glows, with the lightmap forced to its brightest
 * corner and the blend set to add. Black adds nothing, so everything but those places is invisible
 * and costs one more pass of twenty-one boxes.
 *
 * <p>
 * Every golem gets it and not only the fitted ones, because the thing worth seeing at night is that
 * the figure standing in your road is a golem rather than a monster. An unfitted one carries a dull
 * ember and a fitted one carries its own color, which is the same fact the skin says in daylight.
 *
 * <p>
 * <strong>Where this is simpler than the other edition.</strong> There the extra pass is a method on
 * the renderer, {@code shouldRenderPass}, and it needs a second copy of the model to draw with,
 * because the base class poses the pass model separately from the main one. Here a layer is handed
 * the model the main pass has already posed, so there is one model and the pose is the same by
 * construction rather than by both halves being given the same numbers.
 *
 * <p>
 * The fog is simpler too, and that is the larger saving. Fixed-function fog is applied to a fragment
 * <em>before</em> the blend stage, so a texel that is black in the texture does not reach the blender
 * black - it reaches it as the fog color, scaled by distance, which under an additive blend turns
 * the golem into a brighter blob instead of a dark shape with two lit points. The other edition
 * discovered that and had to read the fog color back with {@code glGetFloat} and hand a black one
 * to {@code glFog} itself, which cost a version to get right: the read wants sixteen floats and the
 * write wants exactly four, and sixteen into four is an overflow thrown inside a catch-all, logged,
 * and otherwise invisible except that the golem silently stopped holding its tool. 1.12.2 has
 * {@code setupFogColor}, which vanilla's own spider uses for exactly this, so none of that apparatus
 * is needed here.
 */
public class LayerGolemGlow
    extends RenderLayer<EntityGolemOfWays, ModelGolemOfWays> {

    /**
     * Vanilla's own number for the brightest corner of the lightmap, which changed with the
     * pipeline.
     *
     * <p>
     * 61680 in the other edition - two bytes, block light and sky light side by side, fed to a
     * global the driver held. Here a packed light is an int with sky light in its upper half, so
     * full bright is 240 shifted up by sixteen, and it is a vertex attribute passed to the draw
     * rather than a global set before it. Vanilla's own eyes layer writes this same number.
     */
    private static final int FULL_BRIGHT = 15728640;

    public LayerGolemGlow(RenderGolemOfWays renderer) {
        super(renderer);
    }

    @Override
    public void render(com.mojang.blaze3d.vertex.PoseStack pose,
        net.minecraft.client.renderer.MultiBufferSource buffers, int light, EntityGolemOfWays golem,
        float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
        float headPitch) {
        // Everything the other edition does by hand - additive blending, alpha off, no depth write,
        // the lightmap pinned at full bright, and all of it put back afterwards - is this one
        // choice of render type. It is vanilla's own, the one the enderman's and the spider's eyes
        // use, and picking it rather than setting it is the whole difference.
        //
        // Which also retires a bug the other edition had to write a paragraph about. Its restore
        // put the lightmap back by hand, and had to, because the next layer hangs a tamper in the
        // golem's fist and a tamper drawn with the lightmap still pinned is a tool lit like daylight
        // in a dark room. Nothing here is pinned, so there is nothing to put back and no way to
        // forget to - and the eleven lines that did it are gone rather than translated.
        getParentModel().renderToBuffer(
            pose,
            buffers.getBuffer(
                net.minecraft.client.renderer.RenderType
                    .eyes(GolemSkins.glowFor(golem, golem.fittedUpgrade()))),
            FULL_BRIGHT,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
            1F,
            1F,
            1F,
            1F);
    }
}
