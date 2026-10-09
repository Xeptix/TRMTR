package com.trmtgtnh.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.entity.GolemUpgrade;

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
@SideOnly(Side.CLIENT)
public class LayerGolemGlow implements LayerRenderer<EntityGolemOfWays> {

    /** Vanilla's own number for the brightest corner of the lightmap. */
    private static final int FULL_BRIGHT = 61680;

    private final RenderGolemOfWays renderer;

    public LayerGolemGlow(RenderGolemOfWays renderer) {
        this.renderer = renderer;
    }

    @Override
    public void doRenderLayer(EntityGolemOfWays golem, float limbSwing, float limbSwingAmount, float partialTicks,
        float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        GolemUpgrade fitted = golem.fittedUpgrade();
        renderer.bindTexture(GolemSkins.glowFor(golem, fitted));

        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(!golem.isInvisible());
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT % 65536, FULL_BRIGHT / 65536);
        GlStateManager.color(1F, 1F, 1F, 1F);

        Minecraft.getMinecraft().entityRenderer.setupFogColor(true);
        renderer.getMainModel()
            .render(golem, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        Minecraft.getMinecraft().entityRenderer.setupFogColor(false);

        // Everything this pass changed, put back. The layer after it hangs a tamper in the golem's
        // fist, and a tamper drawn with the lightmap still pinned at full bright is a tool lit like
        // daylight in a dark room - which is a bug that only appears once something else on the same
        // entity glows.
        int here = golem.getBrightnessForRender();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, here % 65536, here / 65536);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
