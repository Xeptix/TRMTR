package com.trmtgtnh.block;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import com.trmtgtnh.client.ClientLightCache;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;

/**
 * Whether any worn square anywhere is carrying a path light.
 *
 * <p>
 * <p>
 * What is here is what the rest of the carried code reaches for, and it is the cheap half by design.
 * A pack with no path lights in it must not pay for the question, so every lit square anywhere sets
 * this once and the colouring path reads a boolean instead of a lookup. It is one-way on purpose:
 * nothing clears it, because a square that was lit may be unlit by the time anybody asks, and a flag
 * that flickered would cost more to maintain than the lookup it is there to avoid.
 *
 * <p>
 * The rest of the class is here now that the store is. One thing is simpler than in the 1.12.2
 * edition: there the client's answer is fetched through the sided proxy, because the client cache is
 * a client-only class a common one may not name. Here both caches live in the shared module and are
 * asked directly - the side is decided by whether the level says it is a client's, which is a better
 * question than which proxy was installed.
 *
 * <p>
 * What is still per loader, and is not here, is registering the colour: a block tells the game how to
 * tint itself through a loader-specific registry. {@code GhostTint} is what both registries are
 * handed, and {@link #tinted} is what it calls - so a lit square gets its glow multiplied into
 * whatever colour it would otherwise have been.
 */
public final class GhostLight {

    private static volatile boolean anyLit;

    private GhostLight() {}

    /**
     * Says that something, somewhere, is lit.
     *
     * <p>
     * Called as records arrive - from a packet, from a chunk read off disk, from a light being placed
     * - and the point is that it is called from all three. A world loading with lights already in it
     * has to take the fast path off just as surely as one where somebody has only now lit a square.
     */
    public static void noteLit() {
        anyLit = true;
    }

    /** Whether the cheap answer is still available. */
    public static boolean anyLit() {
        return anyLit;
    }

    /** How brightly this position glows, 0 to 15. */
    public static int levelAt(BlockGetter access, int x, int y, int z) {
        if (!anyLit || !TrmtConfig.lightEnabled) return 0;
        if (access instanceof Level && !((Level) access).isClientSide()) {
            ErosionEntry entry = ErosionStore.get()
                .getEntry((Level) access, x, y, z);
            return entry == null ? 0 : entry.getLightLevel();
        }
        return ClientLightCache.get()
            .levelAt(x, y, z);
    }

    /** The whole packed byte - level and colour - which only the renderer wants. */
    public static int packedAt(BlockGetter access, int x, int y, int z) {
        if (!anyLit || !TrmtConfig.lightEnabled) return 0;
        if (access instanceof Level && !((Level) access).isClientSide()) {
            ErosionEntry entry = ErosionStore.get()
                .getEntry((Level) access, x, y, z);
            return entry == null ? 0 : entry.getLight();
        }
        return ClientLightCache.get()
            .at(x, y, z);
    }

    /**
     * The sixteen colours a glow can be, as {@code 0xRRGGBB}.
     *
     * <p>
     * Vanilla's dye order, so "the fourth one" means the same thing here as everywhere else in the
     * game, and lifted towards white rather than used at full saturation - these multiply a texture
     * that is already earth-coloured, and a fully saturated multiplier turns lit ground into a flat
     * silhouette of itself.
     */
    private static final int[] COLOURS = { 0xFFFFFF, 0xFFB89A, 0xFF9AE0, 0xB9D3FF, 0xFFF0A0, 0xC8FFA8, 0xFFC4DA,
        0xC0C0C0, 0xE0E0E0, 0xA8F0FF, 0xE2B0FF, 0xA8BCFF, 0xE0C0A0, 0xC8FFB0, 0xFFA8A8, 0xFFFFC8 };

    /** The tint for a packed light byte, or white when it is not lit. */
    public static int colourOf(int packed) {
        if ((packed & 0xF) == 0) return 0xFFFFFF;
        return COLOURS[(packed >> 4) & 0xF];
    }

    public static int colourCount() {
        return COLOURS.length;
    }

    /**
     * A colour with a glow multiplied in, channel by channel.
     *
     * <p>
     * Which is what a colour multiplier already is, so a lit block gets its biome colour and its glow
     * at once rather than one replacing the other - grass in a swamp still reads as swamp grass when
     * somebody lights it green.
     */
    public static int tinted(int base, int packed) {
        int glow = colourOf(packed);
        if (glow == 0xFFFFFF) return base;
        if (base == 0xFFFFFF) return glow;
        int red = ((base >> 16) & 0xFF) * ((glow >> 16) & 0xFF) / 255;
        int green = ((base >> 8) & 0xFF) * ((glow >> 8) & 0xFF) / 255;
        int blue = (base & 0xFF) * (glow & 0xFF) / 255;
        return (red << 16) | (green << 8) | blue;
    }
}
