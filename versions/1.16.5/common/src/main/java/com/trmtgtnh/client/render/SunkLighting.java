package com.trmtgtnh.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
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
                    int brightness = LevelRenderer.getLightColor(level, cell, at);
                    sky[dx][dy][dz] = (brightness >> 20) & 0xF;
                    block[dx][dy][dz] = (brightness >> 4) & 0xF;
                    passes[dx][dy][dz] = cell.getLightBlock(level, at) < 15;
                    ao[dx][dy][dz] = cell.getShadeBrightness(level, at);
                }
            }
        }
        // Light reaches each side of a block that is not solid, as the 1.7.10 edition's asks it of an opaque cube; a
        // sunk ghost is neither.
        boolean through = !self.isSolidRender(level, pos);
        boolean[] open = { through, through, through, through, through, through };
        float[][] out = new float[corners.length][];
        for (int i = 0; i < corners.length; i++) {
            out[i] = SunkLight.topCorner(sky, block, passes, ao, open, corners[i][0], height, corners[i][1]);
        }
        return out;
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

    /**
     * Whether Canvas should take a cell as closed for its light (0.9.228; Xep, 2026-10-26: "The craters look darker
     * (more shadows) than the other versions"): a ghost that holds no light at all, sky or block. Canvas averages the
     * light of every cell it is not told is closed into a face's corners, and a ghost - which says it occludes nothing,
     * so that its neighbours' faces are not culled against a dip - stops light as the block it stands in for did, so a
     * whole one holds none. Averaged in, that nought drew every crater wall at under half the luma it has under every
     * other renderer, which take a neighbour with no light as missing. A sunk ghost lets light in and a glowing one
     * holds its own, and both stay open. See MixinCanvasGhostClosed.
     */
    public static boolean closedToLight(boolean ghost, int packedLight) {
        return ghost && packedLight == 0;
    }
}
