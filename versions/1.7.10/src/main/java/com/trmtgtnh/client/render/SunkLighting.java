package com.trmtgtnh.client.render;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import com.trmtgtnh.client.texture.SunkLight;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * This edition's half of {@link SunkLight}: the light, the passage of light and the shade of the twenty-seven blocks
 * around a sunk square, read from the world the way Forge's {@code BlockInfo.updateLightMatrix} reads them, and the
 * four corners of its top face worked out from them (0.9.223).
 *
 * <p>
 * Each cell is asked of its own block, as Forge asks each cell of its own state: its mixed brightness - which, for a
 * block that takes its neighbours' brightness, as a sunk ghost does, is the brightest beside it - its opacity at that
 * position, and its ambient occlusion value, which is 0.2 for a normal cube and 1 otherwise. A sunk ghost is no opaque
 * cube, so, as in Forge's lighter, light from it reaches each of its six neighbours.
 */
@SideOnly(Side.CLIENT)
public final class SunkLighting {

    private SunkLighting() {}

    /**
     * How many sunk tops vanilla's smooth lighting has handed here this session - the proof the mixin's injection bound
     * and runs, since an injection that is not required says nothing when it fails to bind. The harness logs it.
     */
    private static final java.util.concurrent.atomic.AtomicLong LIT = new java.util.concurrent.atomic.AtomicLong();

    public static void noteLit() {
        LIT.incrementAndGet();
    }

    public static long litCount() {
        return LIT.get();
    }

    /**
     * The four corners of the top face of the sunk square at x, y, z, its top at {@code height} within the block, each
     * {sky level, block level, shade}, in the order vanilla's smooth lighting names them for a top face: top left (the
     * corner at +x +z), bottom left (+x -z), bottom right (-x -z), top right (-x +z).
     */
    public static float[][] topCorners(IBlockAccess world, Block self, int x, int y, int z, float height) {
        int[][][] sky = new int[3][3][3];
        int[][][] block = new int[3][3][3];
        boolean[][][] passes = new boolean[3][3][3];
        float[][][] ao = new float[3][3][3];
        for (int dx = 0; dx <= 2; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = 0; dz <= 2; dz++) {
                    int px = x + dx - 1, py = y + dy - 1, pz = z + dz - 1;
                    Block cell = world.getBlock(px, py, pz);
                    int brightness = cell.getMixedBrightnessForBlock(world, px, py, pz);
                    sky[dx][dy][dz] = (brightness >> 20) & 0xF;
                    block[dx][dy][dz] = (brightness >> 4) & 0xF;
                    passes[dx][dy][dz] = cell.getLightOpacity(world, px, py, pz) < 15;
                    ao[dx][dy][dz] = cell.getAmbientOcclusionLightValue();
                }
            }
        }
        // This edition asks opaqueness of the block alone, not side by side; a sunk ghost is no opaque cube.
        boolean through = !self.isOpaqueCube();
        boolean[] open = { through, through, through, through, through, through };
        return new float[][] { SunkLight.topCorner(sky, block, passes, ao, open, 1, height, 1),
            SunkLight.topCorner(sky, block, passes, ao, open, 1, height, 0),
            SunkLight.topCorner(sky, block, passes, ao, open, 0, height, 0),
            SunkLight.topCorner(sky, block, passes, ao, open, 0, height, 1) };
    }
}
