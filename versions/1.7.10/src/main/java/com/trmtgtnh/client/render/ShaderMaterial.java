package com.trmtgtnh.client.render;

import java.lang.reflect.Method;

import net.minecraft.block.Block;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.block.GhostRendering;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * What a shader pack thinks worn ground is made of.
 *
 * <p>
 * A shader pack decides how a block behaves under light - how it shines, whether it glows, how
 * rough it reads - by looking the block up by name in its own table. A ghost is not in anybody's
 * table, so under a shader pack a worn stone road did not merely lose its polish: it lost every
 * material property the unworn block had and fell back on whatever the pack does with a stranger.
 *
 * <p>
 * Angelica ships a public way to say otherwise. {@code Iris.setShaderMaterialOverride(Block, int)}
 * tells the shader pipeline to treat the vertices being emitted as some other block, and
 * {@code resetShaderMaterialOverride} takes it back. Nothing inside Angelica calls either; they
 * exist for other mods. Both take vanilla types only, and the first returns immediately when
 * shaders are off, so the whole of this is a boolean read on a client that is not using them.
 *
 * <p>
 * Reached by reflection and gated on the mod being loaded at all, so no Angelica class is named at
 * compile time and a client without it never looks. That is also what makes the effect
 * self-detecting rather than configured: with no Iris, nothing to call; with Iris but no shader
 * pack, the call returns on its first line.
 *
 * <p>
 * What a ghost claims to be is the honest answer twice over. While it is still drawing its own
 * family it claims the block it is covering, so a worn granite road is granite as far as the pack
 * is concerned. Once it has worn through into another material it claims that material instead, so
 * the shine goes as the road breaks up - not by fading a number nobody authored, but by becoming
 * the cobble, then the grit, then the earth that the pack already has real values for.
 */
@SideOnly(Side.CLIENT)
public final class ShaderMaterial {

    private static Method set;

    private static Method reset;

    private static boolean looked;

    private ShaderMaterial() {}

    /** Whether there is anything to say and anybody to say it to. */
    public static boolean available() {
        if (!TrmtConfig.inheritShaderMaterial) return false;
        if (!looked) look();
        return set != null;
    }

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        if (!Loader.isModLoaded("angelica")) return;
        try {
            Class<?> iris = Class.forName("net.coderbot.iris.Iris");
            set = iris.getMethod("setShaderMaterialOverride", Block.class, int.class);
            reset = iris.getMethod("resetShaderMaterialOverride");
            Trmt.LOG.info("Worn ground will keep the shader material of the block it covers");
        } catch (Throwable notThere) {
            // A version of Angelica without the hook, or one that moved it. Nothing is lost that
            // was not already lost, so this is worth one line rather than a stack trace.
            set = null;
            reset = null;
            Trmt.LOG.debug("Angelica is present but has no shader material override: {}", notThere.toString());
        }
    }

    /**
     * Says what this ghost should be taken for, or takes the claim back.
     *
     * @param ghost the stand-in being drawn, or null for anything that is not one
     */
    public static void claim(GhostBlock ghost, int x, int y, int z) {
        if (!available()) return;
        try {
            if (ghost == null) {
                reset.invoke(null);
                return;
            }
            int packed = Trmt.proxy.originPackedAt(x, y, z);
            Block origin = packed < 0 ? null : Block.getBlockById(packed >> 4);
            SurfaceFamily appearance = ghost.appearance();

            // Still itself: claim the block underneath, whatever it is. Worn through: claim the
            // material it has worn into, which the pack has values for and the covered block's
            // pixels no longer describe.
            Block claim = origin;
            int meta = packed < 0 ? 0 : packed & 0xF;
            if (origin == null || !GhostRendering.showsOwnMaterial(origin, meta, appearance)) {
                claim = GhostRendering.counterpartOf(appearance);
                meta = 0;
            }
            if (claim == null) {
                reset.invoke(null);
                return;
            }
            set.invoke(null, claim, Integer.valueOf(meta));
        } catch (Throwable awkward) {
            // One failure is enough: something has changed under us and asking again every block
            // for the rest of the session would be the expensive way to keep finding that out.
            set = null;
            reset = null;
            Trmt.LOG.warn("Giving up on the shader material override: {}", awkward.toString());
        }
    }
}
