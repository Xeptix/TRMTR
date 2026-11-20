package com.trmtgtnh.client.model;

import java.nio.ByteBuffer;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A quad's own light, kept on the game's own block renderer as Forge's light pipeline keeps it (0.9.228, real glow).
 *
 * <p>
 * Forge's pipeline, on by default, takes the brighter of a quad's baked light and the light it works out, each channel
 * on its own ({@code VertexLighterFlat.processQuad}; {@code tools/hook_light.py} reads it). The game's own renderer,
 * which draws whenever that pipeline is off - and OptiFine switches it off and draws through its own copy of that
 * renderer - copies the quad's vertices into the buffer and then writes the face's light over the baked one
 * ({@code putBrightness4}), so a glow there drew as dark as the ground under it. {@code MixinBakedLightKept} hands
 * this each light that call is about to write, and the call writes the brighter of it and what the quad carried.
 *
 * <p>
 * A quad that carries no light of its own, which is every quad of the game's own models, is written exactly as it
 * was: nought is never the brighter.
 */
@SideOnly(Side.CLIENT)
public final class BakedLight {

    /** The lightmap's element among a vertex's texture coordinates: the second, after the sprite's. */
    private static final int LIGHTMAP = 1;

    private BakedLight() {}

    /**
     * The light to write to one of the four vertices just copied into the buffer: the brighter of {@code light} and
     * the light that vertex carried in, each channel on its own.
     *
     * @param vertex which of the four, nought to three, in the order the call takes them
     */
    public static int brighter(BufferBuilder buffer, int vertex, int light) {
        VertexFormat format = buffer.getVertexFormat();
        if (format == null || !format.hasUvOffset(LIGHTMAP)) return light;
        int index = buffer.getVertexCount() - 4 + vertex;
        if (index < 0) return light;
        int at = index * format.getSize() + format.getUvOffsetById(LIGHTMAP);
        ByteBuffer bytes = buffer.getByteBuffer();
        if (bytes == null || at < 0 || at + 4 > bytes.capacity()) return light;
        return brighter(bytes.getInt(at), light);
    }

    /**
     * The brighter of two packed lights, each channel on its own: sky light in the high half, block light in the low,
     * as the game packs a lightmap coordinate.
     */
    public static int brighter(int baked, int light) {
        if (baked == 0) return light;
        int block = Math.max(baked & 0xFFFF, light & 0xFFFF);
        int sky = Math.max(baked >>> 16, light >>> 16);
        return sky << 16 | block;
    }
}
