package com.trmtgtnh.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.client.render.Settling;

/**
 * The crosshair finds snow and carpet settled onto worn ground where they are drawn - the 1.7.10 edition's
 * MixinSettledHit (0.9.223).
 *
 * <p>
 * Both places the world's ray trace asks a block for its hit - the block the ray starts in, and every block it walks
 * through after - are put through {@link Settling#rayTrace}, which raises the ray by the rut's drop for a block that
 * settles and asks the block itself. Its outline comes down by the same test in {@link MixinSettledOutline}, and only
 * there: Xep, 2026-10-10, "lower it as long as the actual hitbox lowers with it". Not required: a mod that rewrites
 * the ray trace leaves the hit where it was, and the outline, which comes down only once a hit has, with it. A world
 * with no ghosts in it - the integrated server's - finds no drop.
 */
@SideOnly(Side.CLIENT)
@Mixin(World.class)
public abstract class MixinSettledHit {

    @Redirect(
        method = "rayTraceBlocks(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZZ)Lnet/minecraft/util/math/RayTraceResult;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/state/IBlockState;collisionRayTrace(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/RayTraceResult;"),
        require = 0)
    private RayTraceResult trmt$hitWhereItIsDrawn(IBlockState state, World world, BlockPos pos, Vec3d start,
        Vec3d end) {
        return Settling.rayTrace(state, world, pos, start, end);
    }
}
