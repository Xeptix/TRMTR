package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.client.render.Settling;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The crosshair finds snow and carpet settled onto worn ground where they are drawn, not where they would stand on
 * ground that had not worn (0.9.223).
 *
 * <p>
 * Both places the world's ray trace asks a block for its hit - the first block, where the ray starts, and every block
 * it walks through after - are put through {@link Settling#rayTrace}, which raises the ray by the rut's drop for a
 * block that settles and asks the block itself. Its outline comes down by the same test in
 * {@link MixinSettledOutline}, and only there: Xep, 2026-10-10, "lower it as long as the actual hitbox lowers with it".
 * Not required: a mod that rewrites the ray trace leaves the hit where it was, and the outline, which comes down only
 * once a hit has, with it. A world with no ghosts in it - the integrated server's - finds no drop.
 */
@SideOnly(Side.CLIENT)
@Mixin(World.class)
public abstract class MixinSettledHit {

    @Redirect(
        method = "func_147447_a(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;ZZZ)Lnet/minecraft/util/MovingObjectPosition;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;collisionRayTrace(Lnet/minecraft/world/World;IIILnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;"),
        require = 0)
    private MovingObjectPosition trmt$hitWhereItIsDrawn(Block block, World world, int x, int y, int z, Vec3 start,
        Vec3 end) {
        return Settling.rayTrace(block, world, x, y, z, start, end);
    }
}
