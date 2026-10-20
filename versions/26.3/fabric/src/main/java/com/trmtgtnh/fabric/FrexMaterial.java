package com.trmtgtnh.fabric;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
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
 * when the map is the default). So at 1.21.1 the claim was said here, as the material each of the square's quads
 * carries: the claimed block's map, asked for the claimed block's own sprite - the one a map that
 * names sprites names - copied by FREX's own finder and given back this square's pass, so worn ice stays
 * in the translucent pass whatever the map says.
 *
 * <p>
 * Nothing of FREX or Canvas is named at compile time. Everything is reached by reflection, asked for
 * only when the renderer installed is a FREX one, and the first failure gives up for the session. With
 * Indigo or Sodium there is nothing here to do and nothing is asked.
 *
 * <p>
 * <strong>At 26.1.2 there is no material to say it with.</strong> Fabric API's renderer API lost its materials in
 * 1.21.5 (renderer API 5; 13.0.8 at 26.1.2 has no {@code RenderMaterial}, {@code MaterialFinder} or
 * {@code RendererAccess}: a quad carries its layer, glow, shading and occlusion directly), and 1.21.1 handed Canvas its
 * FREX material through exactly those. No Canvas is published for 26.1.2 (Modrinth, read 2026-10-18), so there is no
 * FREX renderer to learn a new door from. {@link #active} still looks for FREX by name, as before, and says once if
 * it finds it; it answers false, so the model emits the square's own quads, as 1.21.1 did wherever FREX was absent.
 */
final class FrexMaterial {

    private static final String MAP = "grondag.frex.api.material.MaterialMap";

    private static boolean looked;

    private FrexMaterial() {}

    /**
     * Whether there is a FREX renderer to say a material to - one boolean after the first ask, since the
     * renderer is chosen as the game starts and the first ghost is drawn long after. Always false at 26.1.2; see the
     * class note.
     */
    static boolean active() {
        if (!TrmtConfig.inheritShaderMaterial) return false;
        if (!looked) look();
        return false;
    }

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        try {
            Class.forName(MAP);
            Trmt.LOG.info(
                "FREX is installed, but Fabric's renderer API at this version carries no material to hand it, so worn "
                    + "ground keeps its own material under it");
        } catch (Throwable notThere) {
            // No FREX, which is the ordinary case: Indigo and Sodium have none of this.
        }
    }

    /**
     * The material a square's quads would carry under Canvas: the claimed block's own, in this square's pass. Null at
     * 26.1.2, where no such material exists; see the class note.
     */
    static Object of(BlockState claim, ChunkSectionLayer pass) {
        return null;
    }
}
