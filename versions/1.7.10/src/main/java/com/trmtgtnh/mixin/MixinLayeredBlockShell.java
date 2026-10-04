package com.trmtgtnh.mixin;

import net.minecraft.client.renderer.RenderBlocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.client.render.LayerLift;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Lifts the carved shell of Chisel's lavastone and waterstone off the lava or water drawn inside
 * it, so the two stop arguing about which of them is in front.
 *
 * <p>
 * Those blocks are drawn twice. The liquid goes down in the solid pass through the renderer's
 * override texture, at whatever bounds it was already carrying; the carved stone goes down in the
 * pass that sorts back to front, which restates the bounds as a whole cube and draws over it. Two
 * full cubes in the same place. On a plain client that settles itself - identical corners give
 * identical depths, the depth test is less-or-equal, and the second one drawn wins every pixel.
 *
 * <p>
 * What unsettles it here is the crack fix that comes with the modern chunk builder, which grows
 * every full-cube face a thousandth of a block sideways to hide the seams between chunks - and then
 * does it in only the first of the two passes, because its own guard returns immediately unless the
 * world render pass is nought and the block's class is on a whitelist Chisel is not on. The liquid's
 * quad moves and the stone's does not, and two almost-coplanar quads round their depths differently
 * pixel by pixel.
 *
 * <p>
 * The shell is lifted rather than the liquid sunk, and the direction is not a matter of taste.
 * Sinking the liquid puts the renderer's lower bound above nought, which flips the standard
 * renderer from taking the neighbouring cell's brightness to taking this block's own - and inside an
 * opaque waterstone, which emits no light, that is nought, so the water renders black. It flips the
 * six matching tests on the ambient-occlusion path with it, and it sets the partial-bounds flag.
 * Lifting leaves every one of those tests answering exactly as nought does, so the lighting, the
 * shading path and the texture window are what they are today and only the geometry moves.
 *
 * <p>
 * Lifting reaches into the neighbouring cell, and that is safe for one specific reason: a face with
 * an opaque neighbour is not drawn in either pass, because the culling test reads the <em>block's</em>
 * bounds - which nothing here touches - and falls through to "is the neighbour an opaque cube". So
 * the lift only ever protrudes where there is nothing at that plane to protrude into. Chisel reached
 * the same conclusion for the item form years ago: its inventory renderer already lifts the shell by
 * a thousandth. Only the world renderer forgot to.
 *
 * <p>
 * The target is named by string because Chisel is not on this mod's compile classpath and never will
 * be, and the method it redirects is spelled obfuscated because that is what a shipped client calls
 * it and no name map can be generated for a class the compiler cannot see. {@code @Pseudo} is what
 * makes a client without Chisel skip this silently rather than complain about it. Being allowed to
 * find nothing is not optional either - without it a future Chisel that drops the redundant call
 * would crash the client rather than quietly doing nothing - and the price of that permission is
 * that a failure to apply is silent, which is why {@link LayerLift} says one line to the log the
 * first time it runs. A development workspace runs unobfuscated names and will not match this; it
 * also has no Chisel, so there is nothing there to fix.
 */
@SideOnly(Side.CLIENT)
@Pseudo
@Mixin(targets = "team.chisel.client.render.RendererMultiLayer", remap = false)
public class MixinLayeredBlockShell {

    @Redirect(
        method = "renderWorldBlock(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;ILnet/minecraft/client/renderer/RenderBlocks;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;func_147782_a(DDDDDD)V"),
        remap = false,
        require = 0,
        expect = 0)
    private void trmt$liftShell(RenderBlocks renderer, double minX, double minY, double minZ, double maxX, double maxY,
        double maxZ) {
        LayerLift.lift(renderer, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
