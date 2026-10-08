package com.trmtgtnh.fabric;

import java.lang.reflect.Method;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The claim {@code ShaderMaterial} works out, said to Canvas.
 *
 * <p>
 * Canvas does not look a block's shader material up in a pack's table, the way OptiFine and Oculus do.
 * It reads a FREX material map - {@code assets/<mod>/materialmaps/block/<block>.json}, shipped by a
 * pipeline or a resource pack - and gives each quad of a block the material that map names for it. A
 * ghost has no map, so its quads were drawn with none of the material the block they cover is given:
 * the same stranger as under any other shader.
 *
 * <p>
 * <strong>Canvas leaves a quad's own material alone when the block's map is the default one</strong>,
 * which a ghost's always is (read with javap from Canvas 1.0.1511: {@code mapMaterials} returns at once
 * when the map is the default). So the claim is said here, as the material each of the square's quads
 * carries: the claimed block's map, asked for the claimed block's own sprite - the one a map that
 * names sprites names - copied by FREX's own finder and given back this square's pass, so worn ice stays
 * in the translucent pass whatever the map says.
 *
 * <p>
 * Nothing of FREX or Canvas is named at compile time. Everything is reached by reflection, asked for
 * only when the renderer installed is a FREX one, and the first failure gives up for the session. With
 * Indigo or Indium there is nothing here to do and nothing is asked.
 */
final class FrexMaterial {

    private static final String MAP = "grondag.frex.api.material.MaterialMap";

    private static final String FINDER = "grondag.frex.api.material.MaterialFinder";

    private static final String MATERIAL = "grondag.frex.api.material.RenderMaterial";

    private static Method mapOf;

    private static Method mapped;

    private static Method copyFrom;

    private static Class<?> finderClass;

    private static Class<?> materialClass;

    private static boolean looked;

    private FrexMaterial() {}

    /**
     * Whether there is a FREX renderer to say a material to - one boolean after the first ask, since the
     * renderer is chosen as the game starts and the first ghost is drawn long after.
     */
    static boolean active() {
        if (!TrmtConfig.inheritShaderMaterial) return false;
        if (!looked) look();
        return mapOf != null;
    }

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> map = Class.forName(MAP);
            finderClass = Class.forName(FINDER);
            materialClass = Class.forName(MATERIAL);
            mapOf = map.getMethod("get", BlockState.class);
            mapped = map.getMethod("getMapped", TextureAtlasSprite.class);
            copyFrom = finderClass.getMethod("copyFrom", materialClass);
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null || !finderClass.isInstance(renderer.materialFinder())) {
                // FREX present with somebody else's renderer, which cannot take a FREX material.
                mapOf = null;
                mapped = null;
                copyFrom = null;
                return;
            }
            Trmt.LOG.info("Worn ground will keep the FREX material of the block it covers");
        } catch (Throwable notThere) {
            // No FREX, which is the ordinary case: Indigo, Indium and Sodium have none of this.
            mapOf = null;
            mapped = null;
            copyFrom = null;
            finderClass = null;
            materialClass = null;
        }
    }

    /**
     * The material a square's quads carry under Canvas: the claimed block's own, in this square's pass.
     * Null where its map names nothing, which leaves the square's ordinary material in place.
     */
    static RenderMaterial of(BlockState claim, RenderType pass) {
        if (claim == null || mapOf == null) return null;
        try {
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null) return null;
            MaterialFinder finder = renderer.materialFinder();
            if (!finderClass.isInstance(finder)) return null;
            Object map = mapOf.invoke(null, claim);
            if (map == null) return null;
            TextureAtlasSprite sprite = Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModelShaper()
                .getParticleIcon(claim);
            Object material = mapped.invoke(map, sprite);
            if (material == null || !materialClass.isInstance(material)) return null;
            MaterialFinder copied = (MaterialFinder) copyFrom.invoke(finder, material);
            return copied.blendMode(0, pass)
                .find();
        } catch (Throwable awkward) {
            // One failure is enough: asking again for every square for the rest of the session would be
            // the expensive way to keep finding out that something has changed under us.
            mapOf = null;
            mapped = null;
            copyFrom = null;
            finderClass = null;
            materialClass = null;
            Trmt.LOG.warn("Giving up on the FREX material override: {}", awkward.toString());
            return null;
        }
    }
}
