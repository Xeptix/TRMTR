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
 * ({@code mergeBakedLight}, or Fabric's lightmap once {@code fromVanilla} has copied it), each channel on its own, so the
 * square under a cut, where the renderer reads nought, draws as bright as the air above it. Every one of them is read in
 * {@code tools/hook_light.py}.
 */
public final class HookQuad extends BakedQuad {

    /** The packed light's place in a vertex of eight ints: position, color, texture, then this. */
    private static final int LIGHT_AT = 6;

    private final int[] packed;

    /**
     * Whether the quad keeps smooth lighting: false for a hooked block's, true for a glow overlay's (0.9.228, real glow),
     * which must be shaded exactly as the picture under it is - the same ambient occlusion, the same face shade - and
     * differ from it only in the light baked in, which every renderer merges with its own by taking the brighter.
     */
    private final boolean smooth;

    HookQuad(int[] packed, int tint, Direction side, TextureAtlasSprite sprite) {
        this(packed, tint, side, sprite, false);
    }

    HookQuad(int[] packed, int tint, Direction side, TextureAtlasSprite sprite, boolean smooth) {
        super(packed, tint, side, sprite, true);
        this.packed = packed;
        this.smooth = smooth;
    }

    /** Read by Forge, NeoForge and Embeddium. No {@code @Override}: the plain game this module compiles against has none. */
    public boolean hasAmbientOcclusion() {
        return smooth;
    }

    /** Bakes a packed light into every vertex. */
    void lit(int light) {
        for (int at = LIGHT_AT; at < packed.length; at += GhostQuads.INTS_PER_VERTEX) packed[at] = light;
    }
}
