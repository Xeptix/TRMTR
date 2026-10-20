package com.trmtgtnh.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.client.texture.SunkLight;

/**
 * This edition's half of {@link SunkLight}: the light, the passage of light and the shade of the twenty-seven blocks
 * around a sunk square, read from the world as vanilla's smooth lighting reads them - the light by
 * {@code LevelRenderer.getLightColor}, the shade by {@code getShadeBrightness}, which a ghost answers per square, a
 * whole one with a normal cube's 0.2 - and the corners of its top face worked out from them (0.9.223). The 1.7.10
 * edition's {@code SunkLighting}, carried.
 *
 * <p>
 * For vanilla's smooth lighting, which takes a top that is not flush with its block from inside the block, and which
 * draws on Forge unless its experimental light pipeline is on. Rubidium, Embeddium, Sodium and Indigo already light a
 * sunk top by its height, and draw it unhelped.
 */
public final class SunkLighting {

    private SunkLighting() {}

    /**
     * How many sunk tops vanilla's smooth lighting has handed here this session - the proof the hook binds and runs.
     * The harness logs it.
     */
    private static final java.util.concurrent.atomic.AtomicLong LIT = new java.util.concurrent.atomic.AtomicLong();

    public static void noteLit() {
        LIT.incrementAndGet();
    }

    public static long litCount() {
        return LIT.get();
    }

    /**
     * The corners of the top face of the sunk square at {@code pos}, given as {x, z} within the block, each answered
     * as {sky level, block level, shade}, the face at {@code height} within its block.
     */
    public static float[][] topCorners(BlockAndTintGetter level, BlockState self, BlockPos pos, float height,
        float[][] corners) {
        int[][][] sky = new int[3][3][3];
        int[][][] block = new int[3][3][3];
        boolean[][][] passes = new boolean[3][3][3];
        float[][][] ao = new float[3][3][3];
        for (int dx = 0; dx <= 2; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = 0; dz <= 2; dz++) {
                    BlockPos at = pos.offset(dx - 1, dy - 1, dz - 1);
                    BlockState cell = level.getBlockState(at);
                    // getLightColor is getLightCoords by 26.1, taking how the brightness is read; the default reader is
                    // the one 1.21.1's three-argument form used, and the packing is the same.
                    int brightness = LevelRenderer.getLightCoords(LevelRenderer.BrightnessGetter.DEFAULT, level, cell, at);
                    sky[dx][dy][dz] = (brightness >> 20) & 0xF;
                    block[dx][dy][dz] = (brightness >> 4) & 0xF;
                    passes[dx][dy][dz] = lightBlock(level, cell, at) < 15;
                    ao[dx][dy][dz] = cell.getShadeBrightness(level, at);
                }
            }
        }
        // Light reaches each side of a block that is not solid, as the 1.7.10 edition's asks it of an opaque cube; a
        // sunk ghost is neither.
        // A state answers this alone from 1.21.2, with no square.
        boolean through = !self.isSolidRender();
        boolean[] open = { through, through, through, through, through, through };
        float[][] out = new float[corners.length][];
        for (int i = 0; i < corners.length; i++) {
            out[i] = SunkLight.topCorner(sky, block, passes, ao, open, corners[i][0], height, corners[i][1]);
        }
        return out;
    }

    /**
     * How much light a cell stops, at its own square: 1.21.1's {@code getLightBlock(level, pos)}. From 1.21.2 a state
     * answers that alone ({@code getLightDampening}), so a ghost's per-square figure is asked of its own rule, which is
     * what 1.21.1's call reached for a ghost.
     */
    private static int lightBlock(BlockAndTintGetter level, BlockState cell, BlockPos at) {
        if (cell.getBlock() instanceof com.trmtgtnh.block.BlockGhost) {
            return ((com.trmtgtnh.block.BlockGhost) cell.getBlock()).getLightBlock(cell, level, at);
        }
        return cell.getLightDampening();
    }

    /**
     * The brighter of two packed lightmap values, block light and sky light each on its own (0.9.224): a corner of a sunk
     * top under Canvas is never darker than the cell it sits in, which on this version holds the light let into it - the
     * light the Sodium family reads there. See MixinCanvasSunkLight.
     */
    public static int brighterOf(int packed, int cell) {
        int block = Math.max(packed & 0xFFFF, cell & 0xFFFF);
        int sky = Math.max(packed >>> 16, cell >>> 16);
        return block | (sky << 16);
    }
}
