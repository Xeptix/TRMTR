package com.trmtgtnh.client;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostLight;
import com.trmtgtnh.client.model.GhostQuads;

/**
 * What colour a ghost is at a place.
 *
 * <p>
 * A ghost's pictures are drawn from the pixels of the block it stands in for, and several of those
 * are stored grey and coloured at render time - grass most of all, which is grey in the file and
 * green only because the game multiplies a biome's colour into it. A face asking for a tint that
 * nothing answers is handed white, and white times grey is grey. That is the whole of why worn grass
 * drew as flat grey slabs with a mottled band along the top: the band was the fringe, in its own
 * untinted pixels, and the rest was the dirt below it.
 *
 * <p>
 * The quads ask for one of two slots and this answers both. {@link GhostQuads#GRASS_TINT} is the
 * biome's grass colour, for faces that still have grass on them; {@link GhostQuads#LIGHT_TINT} is
 * white, for faces that carry their own colour already. Both then have the path light multiplied
 * into them, which is what makes a lit square lit without replacing what it is made of - grass in a
 * swamp still reads as swamp grass when somebody lights it green.
 *
 * <p>
 * Shared rather than written twice, because it is a rule and not wiring. The loaders differ only in
 * which registry they hand it to: Forge has an event for it and Fabric a registry, and both are one
 * line. The 1.12.2 edition keeps this same rule inline in its client proxy, which is where it was
 * read from.
 */
public final class GhostTint implements BlockColor {

    private static final GhostTint INSTANCE = new GhostTint();

    private GhostTint() {}

    /** The one handler, which both loaders register against the one ghost block. */
    public static BlockColor handler() {
        return INSTANCE;
    }

    @Override
    public int getColor(BlockState state, BlockAndTintGetter world, BlockPos pos, int tintIndex) {
        if (tintIndex != GhostQuads.GRASS_TINT && tintIndex != GhostQuads.LIGHT_TINT) return -1;

        // The path light, multiplied into both: a glow outranks every rule about when ground is
        // tinted, because somebody chose this colour for this square.
        int glow = world == null || pos == null ? 0 : GhostLight.packedAt(world, pos.getX(), pos.getY(), pos.getZ());
        if (tintIndex == GhostQuads.LIGHT_TINT) {
            return GhostLight.tinted(0xFFFFFF, glow);
        }

        // Nought five and one are vanilla's own fallback pair - temperature and humidity for a
        // position there is no world to ask about, which is what an item in a menu is.
        int grass = world == null || pos == null ? GrassColor.get(0.5D, 1.0D)
            : BiomeColors.getAverageGrassColor(world, pos);
        return GhostLight.tinted(grass, glow);
    }
}
