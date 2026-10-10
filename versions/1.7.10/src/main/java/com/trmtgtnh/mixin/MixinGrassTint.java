package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.client.renderer.RenderBlocks;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trmtgtnh.block.BlockGhostGrass;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Lets a worn grass block keep grass's side faces.
 *
 * <p>
 * A grass block is the only surface whose sides are drawn from a different texture than its top
 * <em>and</em> which carries a biome tint, so it is the only one where getting this wrong shows.
 * The renderer draws grass by leaving its sides alone and painting a separately tinted overlay
 * over them. Every other block gets the tint multiplied straight onto the side texture, which on
 * a grass block's dirt-colored sides comes out dark green.
 *
 * <p>
 * Which of those two a block gets is decided by comparing it against vanilla grass - not by type,
 * not by texture name, but by object identity. Nothing a block can do about itself will satisfy
 * that, which is why two earlier attempts at this failed: both taught the ghost to describe
 * itself as grass, and the test never asked. It has to be changed where it is made.
 *
 * <p>
 * So this substitutes the value the comparison reads. When the block being drawn is one of ours
 * standing in for grass, the comparison is handed that same block and therefore matches, and the
 * renderer takes grass's path - untinted sides, tinted overlay, exactly as vanilla grass. For
 * anything else the original value goes through untouched.
 *
 * <p>
 * The comparison lives in the renderer's non-ambient-occlusion path, which is also where this
 * pack's chunk mesher sends every block: it cancels the ambient-occlusion variants and reroutes
 * them here, so this one site covers both. This modifies a value rather than replacing the
 * instruction, so it composes with the pack's own handler on the same spot instead of colliding
 * with it.
 */
@Mixin(RenderBlocks.class)
public class MixinGrassTint {

    // Not required to apply. If a future renderer update moves this comparison, the cost is that
    // worn grass goes back to having green sides - a blemish on a cosmetic mod. Making it
    // mandatory would instead refuse to start a pack of two hundred and thirty-five mods.
    @ModifyExpressionValue(
        require = 0,
        // All three, for the reason MixinGrassSideOverlay already gives next door: with smooth
        // lighting on - which is the default - a block goes through the two ambient-occlusion methods
        // and never reaches the third. Hooking only the third was enough while the renderer was
        // vanilla's or this pack's, because both reach the grass treatment another way: they ask the
        // block's position-free getIcon for the top face, get vanilla's own grass_top back, and key
        // the untinted sides off that name.
        //
        // **OptiFine never makes that call.** Measured on 2026-10-06 with a probe: plain 1.7.10 asks
        // the position-free icon twice in a run and OptiFine not once in fifty-three icon lookups. So
        // under OptiFine the only route left to the grass treatment is this identity comparison, and
        // it has to be substituted wherever the comparison is made rather than in one method of three.
        method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/init/Blocks;grass:Lnet/minecraft/block/BlockGrass;",
            opcode = Opcodes.GETSTATIC))
    private BlockGrass trmt$wornGrassIsStillGrass(BlockGrass original, Block block, int x, int y, int z, float red,
        float green, float blue) {
        // Deliberately the concrete grass class, not the GhostBlock interface: this handler
        // is typed to return a BlockGrass, and only the grass variants are one.
        if (!(block instanceof BlockGhostGrass)) return original;

        BlockGhostGrass ghost = (BlockGhostGrass) block;
        // Only while it still looks like grass. Once wear has carried the chain through to bare
        // earth the ghost reports no tint at all, and earth's sides are meant to take the same
        // treatment as any other block's.
        // Untinted grass is no longer a BlockGhostGrass at all, so this cannot fire for one -
        // kept because the class it guards is public and the day someone builds an untinted
        // grass variant again, this is the line that stops it claiming a treatment it cannot use.
        if (ghost.appearance() != SurfaceFamily.GRASS || ghost.isUntinted()) return original;

        // And only for the variant standing in for vanilla grass. What is being claimed here is
        // grass's whole special case - untinted sides, with a separately tinted fringe drawn
        // over them - and the renderer only draws that fringe for a side texture literally
        // named grass_side. A modded turf with its own texture got the first half and not the
        // second: no tint on its sides, and no fringe to make up for it, so it went grey.
        if (!ghost.mimicsVanillaGrassTop()) return original;

        return ghost;
    }
}
