package com.trmtgtnh.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * A quad of a hooked block (0.9.229; ApiDraw, "Light"), which draws as 1.7.10 draws one: with smooth lighting off, and,
 * where it is told to be, lit by the cell above it.
 *
 * <p>
 * <strong>Why a class.</strong> Forge from 1.20 and NeoForge read {@code hasAmbientOcclusion()} of a quad, and take its
 * shade from the direction alone where it says no; Embeddium reads the same. Vanilla's own {@code BakedQuad} has no such
 * method, and neither has what the common module compiles against, so this is not an override to the compiler. On Fabric
 * the class is what {@code GhostModelFabric.emit} recognises and sends out under a material with no smooth lighting.
 *
 * <p>
 * <strong>The light.</strong> The seventh int of a vertex is its packed light. Forge and NeoForge take the brighter of it
 * and the light the renderer worked out ({@code applyBakedLighting}), the Sodium family the same
 * ({@code mergeBakedLight}, or Fabric's lightmap once {@code fromVanilla} has copied it; Rubidium through
 * {@link #keptLight}), each channel on its own, so the
 * square under a cut, where the renderer reads nought, draws as bright as the air above it. Every one of them is read in
 * {@code tools/hook_light.py}.
 */
public final class HookQuad extends BakedQuad {

    /** The packed light's place in a vertex of eight ints: position, color, texture, then this. */
    private static final int LIGHT_AT = 6;

    private final int[] packed;

    /** Whether the quad is smoothly lit: never a hooked block's, always a glow overlay's. */
    private final boolean smooth;

    HookQuad(int[] packed, int tint, Direction side, TextureAtlasSprite sprite) {
        this(packed, tint, side, sprite, false);
    }

    /**
     * A quad that is smoothly lit or not. A glow overlay is (0.9.228, real glow): it is shaded exactly as the picture
     * it lies on, smooth lighting and the face's shade both, and only its light is raised, so by day it is the
     * picture again and draws nothing new.
     */
    HookQuad(int[] packed, int tint, Direction side, TextureAtlasSprite sprite, boolean smooth) {
        super(packed, tint, side, sprite, true);
        this.packed = packed;
        this.smooth = smooth;
    }

    /** Read by Forge, NeoForge and Embeddium. No {@code @Override}: the plain game this module compiles against has none. */
    public boolean hasAmbientOcclusion() {
        return smooth;
    }

    /**
     * The light Rubidium is handed for one vertex of a quad (0.9.228, real glow): Rubidium 0.2.13 writes the light it
     * worked out and never reads the quad's own, so a glow overlay drew as dark as the ground under it. This is the
     * brighter of the two, block and sky each on its own and to the sixteenth, as Forge and Embeddium take it; a quad of
     * anyone else's is left as Rubidium lit it. See {@code MixinRubidiumBakedLight} in the Forge module.
     */
    public static int keptLight(Object quad, int vertex, int light) {
        if (!(quad instanceof HookQuad)) return light;
        int[] own = ((HookQuad) quad).packed;
        int at = vertex * GhostQuads.INTS_PER_VERTEX + LIGHT_AT;
        if (at < 0 || at >= own.length) return light;
        return com.trmtgtnh.client.render.SunkLighting.brighterOf(light, own[at]);
    }

    /** Bakes a packed light into every vertex. */
    void lit(int light) {
        for (int at = LIGHT_AT; at < packed.length; at += GhostQuads.INTS_PER_VERTEX) packed[at] = light;
    }
}
