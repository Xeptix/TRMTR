package com.trmtgtnh.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.client.render.Settling;

/**
 * The outline round snow or carpet settled onto worn ground is drawn where the block is drawn, by the same test that
 * lowers the crosshair's hit in {@link MixinSettledHit} - the 1.7.10 edition's MixinSettledOutline (0.9.223).
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
        method = "drawSelectionBox(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/math/RayTraceResult;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/state/IBlockState;getSelectedBoundingBox(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/AxisAlignedBB;"),
        require = 0)
    private AxisAlignedBB trmt$outlineWhereItIsDrawn(IBlockState state, World world, BlockPos pos) {
        return Settling.outline(state, world, pos);
    }
}
