package com.trmtgtnh.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.texture.SunkLight;

/**
 * This edition's half of {@link SunkLight}: the light, the passage of light and the shade of the twenty-seven blocks
 * around a sunk square, read from the world exactly as Forge's {@code BlockInfo.updateLightMatrix} reads them - with
 * the shade a whole ghost casts corrected as {@link com.trmtgtnh.mixin.MixinGhostShadeForge} corrects it there - and
 * the corners of its top face worked out from them (0.9.223). The 1.7.10 edition's {@code SunkLighting}, carried.
 *
 * <p>
 * Forge's pipeline already lights a sunk top this way, and draws it unhelped; this is for vanilla's smooth lighting,
 * which takes a top that is not flush with its block from inside the block - Forge's pipeline turned off, and OptiFine,
 * which keeps vanilla's lighting.
 */
@SideOnly(Side.CLIENT)
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
    public static float[][] topCorners(IBlockAccess world, IBlockState self, BlockPos pos, float height,
        float[][] corners) {
        int[][][] sky = new int[3][3][3];
        int[][][] block = new int[3][3][3];
        boolean[][][] passes = new boolean[3][3][3];
        float[][][] ao = new float[3][3][3];
        for (int dx = 0; dx <= 2; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = 0; dz <= 2; dz++) {
                    BlockPos at = pos.add(dx - 1, dy - 1, dz - 1);
                    IBlockState cell = world.getBlockState(at);
                    int brightness = cell.getPackedLightmapCoords(world, at);
                    sky[dx][dy][dz] = (brightness >> 20) & 0xF;
                    block[dx][dy][dz] = (brightness >> 4) & 0xF;
                    passes[dx][dy][dz] = cell.getLightOpacity(world, at) < 15;
                    float shade = cell.getAmbientOcclusionLightValue();
                    if (shade == 1.0F && cell.getBlock() instanceof BlockGhost && BlockGhost.wholeAt(world, at)) {
                        shade = BlockGhost.WHOLE_SHADE;
                    }
                    ao[dx][dy][dz] = shade;
                }
            }
        }
        // Forge's lighter asks each side of the block whether it blocks rendering, and lets light through the ones
        // that do not; a sunk ghost blocks none.
        boolean[] open = new boolean[6];
        EnumFacing[] sides = EnumFacing.values();
        for (int i = 0; i < 6; i++) open[i] = !self.doesSideBlockRendering(world, pos, sides[i]);
        float[][] out = new float[corners.length][];
        for (int i = 0; i < corners.length; i++) {
            out[i] = SunkLight.topCorner(sky, block, passes, ao, open, corners[i][0], height, corners[i][1]);
        }
        return out;
    }
}
