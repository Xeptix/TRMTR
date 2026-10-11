package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.client.render.Settling;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The outline round snow or carpet settled onto worn ground is drawn where the block is drawn, by the same test that
 * lowers the crosshair's hit in {@link MixinSettledHit} (0.9.223).
 *
 * <p>
 * Not required: a mod that rewrites how the selection box is drawn leaves the outline where it was - up, over a hit
 * that has come down, which is how it stood before 0.9.223 - rather than refusing to start. What may never happen, an
 * outline lowered over a hit that was not, cannot: the outline comes down only once a hit has (Settling.outline).
 */
@SideOnly(Side.CLIENT)
@Mixin(RenderGlobal.class)
public abstract class MixinSettledOutline {

    @Redirect(
        method = "drawSelectionBox(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MovingObjectPosition;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;getSelectedBoundingBoxFromPool(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;"),
        require = 0)
    private AxisAlignedBB trmt$outlineWhereItIsDrawn(Block block, World world, int x, int y, int z) {
        return Settling.outline(block, world, x, y, z);
    }
}
