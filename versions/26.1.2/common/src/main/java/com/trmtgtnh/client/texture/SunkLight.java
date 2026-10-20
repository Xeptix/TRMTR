package com.trmtgtnh.client.texture;

/**
 * How a sunk top is lit: the light and the shade at one corner of a top face drawn below its block's top, worked out
 * the way Forge's smooth lighter works them out for any face (0.9.223).
 *
 * <p>
 * Xep, 2026-10-11, shown worn grass sunk in a narrow row under three renderers: "On the bottom 3 images, I prefer how
 * the middle one looks the most, honestly" - the middle one being 1.12.2 with Forge's light pipeline on. That pipeline
 * lights a vertex from where it actually is, blending the light and shade of the blocks around it by its position, so
 * a shallow rut stands in nearly the light of the ground beside it and a deep one darkens by degrees. Vanilla's own
 * smooth lighting - 1.7.10's, and 1.12.2's and 1.16.5's whenever Forge's pipeline is not the one drawing - takes a
 * face that is not flush with its block's top from inside that block, every corner alike, so the same row drew the
 * whole run as dark as its deepest square: (55,39,27) against (83,59,40), measured on the turf part's grass.
 *
 * <p>
 * So each edition's vanilla path hands a sunk ghost's top face to this instead, and draws what it answers: the
 * arithmetic of {@code net.minecraftforge.client.model.pipeline.BlockInfo.updateLightMatrix} and of
 * {@code VertexLighterSmoothAo.calcLightmap} and {@code getAo}, as Forge 14.23.5.2847 ships them, carried line for
 * line so the vanilla path draws what the pipeline would have. Shared with the other editions byte for byte, and
 * free of the game, so it is tested with plain numbers.
 *
 * <p>
 * Every array is indexed {@code [x][y][z]}, each 0 to 2, the block itself at {@code [1][1][1]}: sky and block light
 * levels (0 to 15) as the world packs them for each cell, whether light passes through each cell (its opacity below
 * 15), and each cell's ambient occlusion value (0.2 for a normal cube, 1 otherwise).
 */
public final class SunkLight {

    private SunkLight() {}

    /** Light in one level's lightmap units as Forge's lighter keeps it: a level times 0x20 over 0xFFFF. */
    private static final float UNIT = 0x20 / (float) 0xFFFF;

    /** The six sides as offsets, in the order the game numbers them: down, up, north, south, west, east. */
    private static final int[][] SIDES = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    /**
     * One corner of a top face, as {sky level, block level, shade}: the two light levels from 0 to 15 - fractional, as
     * the blend gives them - and the factor its color is multiplied by.
     *
     * @param cornerX   the corner's x within its block, 0 to 1 - its vertex's own position, as Forge's lighter reads it
     * @param height    the face's height within its block, 0 to 1 - below 1 for a sunk top
     * @param cornerZ   the corner's z within its block, 0 to 1
     * @param openSides for each side in the game's order - down, up, north, south, west, east - whether the block
     *                  lets light through it for this purpose: Forge's {@code !doesSideBlockRendering}, which is every
     *                  side for a block that is not an opaque cube, as a sunk ghost is not. Each such side's neighbour
     *                  is
     *                  lit at least one level below the block
     */
    public static float[] topCorner(int[][][] sky, int[][][] block, boolean[][][] passes, float[][][] ao,
        boolean[] openSides, float cornerX, float height, float cornerZ) {
        int[][][] s = copy(sky);
        int[][][] b = copy(block);
        for (int i = 0; i < SIDES.length; i++) {
            if (!openSides[i]) continue;
            int x = SIDES[i][0] + 1, y = SIDES[i][1] + 1, z = SIDES[i][2] + 1;
            s[x][y][z] = Math.max(s[1][1][1] - 1, s[x][y][z]);
            b[x][y][z] = Math.max(b[1][1][1] - 1, b[x][y][z]);
        }
        float[][][][] skyLight = new float[3][2][2][2];
        float[][][][] blockLight = new float[3][2][2][2];
        matrix(s, passes, skyLight);
        matrix(b, passes, blockLight);
        // Where Forge's lighter places the vertex: its position less the block's middle, plus half its normal - up,
        // for a top face - so a top at height h sits at h on y.
        float x = cornerX - 0.5f;
        float y = height - 0.5f + 0.5f;
        float z = cornerZ - 0.5f;
        float[] light = lightmap(skyLight, blockLight, x, y, z);
        return new float[] { light[0], light[1], shade(ao, x, y, z) };
    }

    /**
     * The world's packing of a light level pair, as each edition's brightness int carries them: sky high, block low.
     */
    public static int packed(float skyLevel, float blockLevel) {
        return ((int) (skyLevel * 16f) & 0xFF) << 16 | ((int) (blockLevel * 16f) & 0xFF);
    }

    private static int[][][] copy(int[][][] levels) {
        int[][][] out = new int[3][3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) out[x][y][z] = levels[x][y][z];
            }
        }
        return out;
    }

    /** BlockInfo.combine: four levels averaged, a dark one lifted from its neighbours where light reaches it. */
    private static float combine(int c, int s1, int s2, int s3, boolean t0, boolean t1, boolean t2, boolean t3) {
        if (c == 0 && !t0) c = Math.max(0, Math.max(s1, s2) - 1);
        if (s1 == 0 && !t1) s1 = Math.max(0, c - 1);
        if (s2 == 0 && !t2) s2 = Math.max(0, c - 1);
        if (s3 == 0 && !t3) s3 = Math.max(0, Math.max(s1, s2) - 1);
        return (float) (c + s1 + s2 + s3) * UNIT / 4f;
    }

    /** BlockInfo.updateLightMatrix's second half, for one kind of light: each corner's level on each axis. */
    private static void matrix(int[][][] s, boolean[][][] t, float[][][][] out) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    int x1 = x * 2;
                    int y1 = y * 2;
                    int z1 = z * 2;

                    int sxyz = s[x1][y1][z1];
                    boolean txyz = t[x1][y1][z1];

                    int sxz = s[x1][1][z1], sxy = s[x1][y1][1], syz = s[1][y1][z1];
                    boolean txz = t[x1][1][z1], txy = t[x1][y1][1], tyz = t[1][y1][z1];

                    int sx = s[x1][1][1], sy = s[1][y1][1], sz = s[1][1][z1];
                    boolean tx = t[x1][1][1], ty = t[1][y1][1], tz = t[1][1][z1];

                    out[0][x][y][z] = combine(
                        sx,
                        sxz,
                        sxy,
                        txz || txy ? sxyz : sx,
                        tx,
                        txz,
                        txy,
                        txz || txy ? txyz : tx);
                    out[1][x][y][z] = combine(
                        sy,
                        sxy,
                        syz,
                        txy || tyz ? sxyz : sy,
                        ty,
                        txy,
                        tyz,
                        txy || tyz ? txyz : ty);
                    out[2][x][y][z] = combine(
                        sz,
                        syz,
                        sxz,
                        tyz || txz ? sxyz : sz,
                        tz,
                        tyz,
                        txz,
                        tyz || txz ? txyz : tz);
                }
            }
        }
    }

    /** VertexLighterSmoothAo.calcLightmap, answering {sky level, block level}. */
    private static float[] lightmap(float[][][][] skyLight, float[][][][] blockLight, float x, float y, float z) {
        x *= 2;
        y *= 2;
        z *= 2;
        float l2 = x * x + y * y + z * z;
        if (l2 > 6 - 2e-2f) {
            float s = (float) Math.sqrt((6 - 2e-2f) / l2);
            x *= s;
            y *= s;
            z *= s;
        }
        float ax = x > 0 ? x : -x;
        float ay = y > 0 ? y : -y;
        float az = z > 0 ? z : -z;
        float e1 = 1 + 1e-4f;
        if (ax > 2 - 1e-4f && ay <= e1 && az <= e1) {
            x = x < 0 ? -2 + 1e-4f : 2 - 1e-4f;
        } else if (ay > 2 - 1e-4f && az <= e1 && ax <= e1) {
            y = y < 0 ? -2 + 1e-4f : 2 - 1e-4f;
        } else if (az > 2 - 1e-4f && ax <= e1 && ay <= e1) {
            z = z < 0 ? -2 + 1e-4f : 2 - 1e-4f;
        }
        ax = x > 0 ? x : -x;
        ay = y > 0 ? y : -y;
        az = z > 0 ? z : -z;
        if (ax <= e1 && ay + az > 3f - 1e-4f) {
            float s = (3f - 1e-4f) / (ay + az);
            y *= s;
            z *= s;
        } else if (ay <= e1 && az + ax > 3f - 1e-4f) {
            float s = (3f - 1e-4f) / (az + ax);
            z *= s;
            x *= s;
        } else if (az <= e1 && ax + ay > 3f - 1e-4f) {
            float s = (3f - 1e-4f) / (ax + ay);
            x *= s;
            y *= s;
        } else if (ax + ay + az > 4 - 1e-4f) {
            float s = (4 - 1e-4f) / (ax + ay + az);
            x *= s;
            y *= s;
            z *= s;
        }

        float bl = 0f;
        float sl = 0f;
        float s = 0f;
        for (int ix = 0; ix <= 1; ix++) {
            for (int iy = 0; iy <= 1; iy++) {
                for (int iz = 0; iz <= 1; iz++) {
                    float vx = x * (1 - ix * 2);
                    float vy = y * (1 - iy * 2);
                    float vz = z * (1 - iz * 2);

                    float s3 = vx + vy + vz + 4;
                    float sx = vy + vz + 3;
                    float sy = vz + vx + 3;
                    float sz = vx + vy + 3;

                    float bx = (2 * vx + vy + vz + 6) / (s3 * sy * sz * (vx + 2));
                    s += bx;
                    bl += bx * blockLight[0][ix][iy][iz];
                    sl += bx * skyLight[0][ix][iy][iz];

                    float by = (2 * vy + vz + vx + 6) / (s3 * sz * sx * (vy + 2));
                    s += by;
                    bl += by * blockLight[1][ix][iy][iz];
                    sl += by * skyLight[1][ix][iy][iz];

                    float bz = (2 * vz + vx + vy + 6) / (s3 * sx * sy * (vz + 2));
                    s += bz;
                    bl += bz * blockLight[2][ix][iy][iz];
                    sl += bz * skyLight[2][ix][iy][iz];
                }
            }
        }
        bl /= s;
        sl /= s;
        float most = 15f * UNIT;
        return new float[] { clamp(sl, most) / UNIT, clamp(bl, most) / UNIT };
    }

    /** VertexLighterSmoothAo.getAo: the shade, blended from the eight cells around the vertex. */
    private static float shade(float[][][] ao, float x, float y, float z) {
        int sx = x < 0 ? 1 : 2;
        int sy = y < 0 ? 1 : 2;
        int sz = z < 0 ? 1 : 2;

        if (x < 0) x++;
        if (y < 0) y++;
        if (z < 0) z++;

        float a = 0;
        a += ao[sx - 1][sy - 1][sz - 1] * (1 - x) * (1 - y) * (1 - z);
        a += ao[sx - 1][sy - 1][sz - 0] * (1 - x) * (1 - y) * (0 + z);
        a += ao[sx - 1][sy - 0][sz - 1] * (1 - x) * (0 + y) * (1 - z);
        a += ao[sx - 1][sy - 0][sz - 0] * (1 - x) * (0 + y) * (0 + z);
        a += ao[sx - 0][sy - 1][sz - 1] * (0 + x) * (1 - y) * (1 - z);
        a += ao[sx - 0][sy - 1][sz - 0] * (0 + x) * (1 - y) * (0 + z);
        a += ao[sx - 0][sy - 0][sz - 1] * (0 + x) * (0 + y) * (1 - z);
        a += ao[sx - 0][sy - 0][sz - 0] * (0 + x) * (0 + y) * (0 + z);
        return clamp(a, 1f);
    }

    private static float clamp(float value, float most) {
        return value < 0f ? 0f : value > most ? most : value;
    }
}
