package com.trmtgtnh.forge.mixin;

import net.minecraft.client.renderer.block.model.BakedQuad;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.client.model.HookQuad;

/**
 * A glow overlay keeps its light under Rubidium (0.9.228, real glow).
 *
 * <p>
 * Every other renderer here takes the brighter of a quad's baked light and the light it worked out: Forge in
 * {@code applyBakedLighting}, Embeddium in {@code mergeBakedLight}, Indium and vanilla's own. Rubidium 0.2.13 does not:
 * its {@code BlockRenderer.renderQuad}, read in its jar with javap, writes {@code QuadLightData.lm[j]} for vertex
 * {@code j} and never reads the quad's own, so end cracks, nether ash and sculk drew as dark as the ground under them.
 * This holds the quad being drawn and hands Rubidium's one read of {@code lm} through {@link HookQuad#keptLight}, which
 * changes nothing but this mod's own lit quads. Embeddium has the same method and read and already merges after it;
 * the brighter of the brighter is the same light.
 *
 * <p>
 * {@code @Pseudo}, because Rubidium is not on this build's classpath, and {@code require = 0}, because a Rubidium that
 * has changed its renderer should draw without the glow rather than stop the game. Rubidium's names are never
 * remapped; {@code BakedQuad} is the game's and is renamed with the rest of this module. One {@code BlockRenderer} is
 * made per chunk-building thread, so the held quad is never another thread's. Nothing of Rubidium is copied.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer", remap = false)
public abstract class MixinRubidiumBakedLight {

    @Unique
    private Object trmt$quad;

    @ModifyVariable(method = "renderQuad", at = @At("HEAD"), argsOnly = true, require = 0, remap = false)
    private BakedQuad trmt$heldQuad(BakedQuad quad) {
        trmt$quad = quad;
        return quad;
    }

    @Redirect(
        method = "renderQuad",
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/model/light/data/QuadLightData;lm:[I",
            args = "array=get",
            remap = false),
        require = 0,
        remap = false)
    private int trmt$keptLight(int[] light, int vertex) {
        return HookQuad.keptLight(trmt$quad, vertex, light[vertex]);
    }
}
