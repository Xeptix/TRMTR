package com.trmtgtnh.block;

import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;

/**
 * What a ghost glows at, asked from whichever side is asking.
 *
 * <p>
 * {@code getLightValue} is one of the few block methods both sides genuinely call: the server needs
 * it to propagate block light through the world, and the client needs it to bake brightness into a
 * chunk mesh. So this cannot be a client-only lookup, and it cannot be a server-only one either - it
 * has to answer correctly from a world the server owns and from the read-only view a chunk-meshing
 * thread is handed.
 *
 * <p>
 * The one thing to know about the side test below is what it means by "a world the server owns".
 * A chunk-meshing thread is handed a {@code ChunkCache}, not a {@code World}, so it falls through to
 * the client's answer - which is right, because meshes are only ever built on a client, and the
 * client's cache is written as immutable snapshots precisely so those threads can read it unlocked.
 *
 * <p>
 * {@code ChunkErosionData}, a portable class held byte for byte identical across both editions, calls
 * {@link #noteLit} by its fully qualified name - which is how a class that reads the world came to sit
 * inside a core meant to name nothing from the game, unseen by a test that checks only import lines.
 */
public final class GhostLight {

    /**
     * Whether anything, anywhere, has been lit since this process started.
     *
     * <p>
     * {@code getLightValue} is called by vanilla's light propagation for every ghost it walks past,
     * which on a well-worn server is a great many blocks a great many times. In a world where
     * nobody has lit anything - which is most of them, and all of them until somebody does - this
     * turns that whole path into one volatile read instead of two hash lookups.
     *
     * <p>
     * Deliberately one-way and never reset. It is a fast path, not a fact: setting it wrongly true
     * costs a lookup that returns zero, while clearing it wrongly would make a real light vanish.
     */
    private static volatile boolean anythingLit;

    /** Called wherever a light is written or read back, so the fast path knows to stand down. */
    public static void noteLit() {
        anythingLit = true;
    }

    private GhostLight() {}

    /** How brightly this position glows, 0 to 15. */
    public static int levelAt(IBlockAccess access, int x, int y, int z) {
        if (!anythingLit || !TrmtConfig.lightEnabled) return 0;
        if (access instanceof World && !((World) access).isRemote) {
            return serverLevel((World) access, x, y, z);
        }
        return Trmt.proxy.clientLightLevel(x, y, z);
    }

    /** The whole packed byte - level and colour - which only the renderer wants. */
    public static int packedAt(IBlockAccess access, int x, int y, int z) {
        if (!anythingLit || !TrmtConfig.lightEnabled) return 0;
        if (access instanceof World && !((World) access).isRemote) {
            World world = (World) access;
            ErosionEntry entry = ErosionStore.get()
                .getEntry(world, x, y, z);
            return entry == null ? 0 : entry.getLight();
        }
        return Trmt.proxy.clientLightPacked(x, y, z);
    }

    private static int serverLevel(World world, int x, int y, int z) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        return entry == null ? 0 : entry.getLightLevel();
    }

    /**
     * The sixteen colours a glow can be, as {@code 0xRRGGBB}.
     *
     * <p>
     * Vanilla's dye order, so "the fourth one" means the same thing here as everywhere else in the
     * game, and lifted towards white rather than used at full saturation - these multiply a
     * texture that is already earth-coloured, and a fully saturated multiplier turns lit ground
     * into a flat silhouette of itself.
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
     * somebody lights it green. The other edition's {@code GhostRendering.blend}, moved here because in
     * this edition the glow is the only thing it is ever used for.
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
