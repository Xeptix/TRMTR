package com.trmtgtnh.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

/**
 * A quad of a hooked block told to take the light of the cell above it (0.9.229; ApiDraw, "Light").
 *
 * <p>
 * The seventh int of a vertex is its packed light. Forge's light pipeline, on by default, takes the brighter of it and
 * what it worked out, each channel on its own ({@code VertexLighterFlat.processQuad}), so the square under a cut, where
 * it reads nought, draws as bright as the air above it. The game's own renderer overwrites it with the face's light
 * ({@code putBrightness4}), which is why the same quads are also filed under the top face. {@code tools/hook_light.py}
 * reads each of those.
 *
 * <p>
 * A glow over worn ground is one too (0.9.228, real glow; {@code GhostBakedModel.glow}), its light the glow's own level.
 * On the game's own renderer that light is kept by {@code MixinBakedLightKept} ({@link BakedLight}), as Forge's
 * pipeline keeps it.
 */
public final class HookQuad extends BakedQuad {

    /** The packed light's place in a vertex of seven ints: position, color, texture, then this. */
    private static final int LIGHT_AT = 6;

    private final int[] packed;

    HookQuad(int[] packed, int tint, EnumFacing side, TextureAtlasSprite sprite) {
        super(packed, tint, side, sprite, true, DefaultVertexFormats.BLOCK);
        this.packed = packed;
    }

    /** Bakes a packed light into every vertex. */
    void lit(int light) {
        for (int at = LIGHT_AT; at < packed.length; at += GhostBakedModel.INTS_PER_VERTEX) packed[at] = light;
    }
}
