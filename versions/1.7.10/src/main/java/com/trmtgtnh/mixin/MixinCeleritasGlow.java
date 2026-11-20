package com.trmtgtnh.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.trmtgtnh.client.render.GhostGlow;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Real glow under Angelica's Celeritas lighting (Xep, 2026-11-16, approved: "write the @Pseudo mixin that raises
 * Celeritas's computed light for glow quads"). Celeritas lights every vertex of a smoothly lit block itself, with its
 * own smooth lighter and shade, so a glow written there takes exactly the light and shade of the face beneath it - and
 * none of the glow's own. This raises that light, once Celeritas has worked it out for the quad in hand and before it
 * is written into the section's mesh, to the glow's level, block and sky apart and never lowered
 * ({@link GhostGlow#raiseCeleritasLight}), leaving the shade as Celeritas made it. So by day the glow is the face's own
 * pixels at the face's own light and shade, and in the dark it keeps its light.
 *
 * <p>
 * Read from Angelica 2.2.29's and 2.2.43's {@code AngelicaChunkBuildContext}: {@code copyRawBuffer} finds each quad's
 * picture by its texture coordinates into a local named {@code sprite}, then calls {@code calculateQuadLight}, which
 * fills {@code quadLightData}, then writes the vertices from it. The signature of {@code copyRawBuffer} differs between
 * the two, so it is named alone. Named by string and never compiled against, as {@code MixinLayeredBlockShell} names
 * Chisel's renderer: {@code @Pseudo}, remapping off, and allowed to find nothing, so an Angelica that has moved any of
 * it costs the glow its light under Celeritas and nothing else, which {@link GhostGlow#celeritasRaisedCount} shows.
 * Registered by {@code LateMixins} only where Angelica is loaded, on the client.
 */
@SideOnly(Side.CLIENT)
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext", remap = false)
public class MixinCeleritasGlow {

    @Inject(
        method = "copyRawBuffer",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext;calculateQuadLight(IIIIIIIZZZ)V",
            shift = At.Shift.AFTER),
        remap = false,
        require = 0,
        expect = 0)
    private void trmt$raiseGlow(CallbackInfo ci, @Local(name = "sprite") TextureAtlasSprite sprite) {
        GhostGlow.raiseCeleritasLight(this, sprite);
    }
}
