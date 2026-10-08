package com.trmtgtnh.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.render.OptiFineMaterial;

/**
 * Hands over the seat a ghost claims its shader material from, under OptiFine.
 *
 * <p>
 * OptiFine works out a block's shader id as the block's model starts and pushes it onto a stack carried
 * by the chunk's buffer, and this is the only moment that buffer and that entry are both to hand. Taken
 * at the tail, so the entry is already in place, and only for our own blocks: any other block's push
 * gives the seat up, which is what keeps a claim from landing on ground that is not a ghost's.
 *
 * <p>
 * {@code @Pseudo}, {@code remap = false} and {@code require = 0} for the reasons {@code
 * MixinOculusBlockContext} gives: the target is not on the compile classpath and is in no refmap, and a
 * version of OptiFine that has moved it should cost the feature and nothing else. {@link OculusGate}
 * refuses the mixin outright on a client with no OptiFine. The method is named with its descriptor
 * because OptiFine's class has two {@code pushEntity}s, and the descriptor is written in the names the
 * game runs under, which is what OptiFine's own class carries once Forge has deobfuscated it.
 */
@SideOnly(Side.CLIENT)
@Pseudo
@Mixin(targets = OculusGate.OPTIFINE_SEAT, remap = false)
public abstract class MixinOptiFineShaderSeat {

    @Inject(
        method = "pushEntity(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)V",
        at = @At("TAIL"),
        require = 0,
        remap = false)
    private static void trmt$seatGhostMaterial(IBlockState state, BlockPos pos, IBlockAccess world,
        BufferBuilder buffer, CallbackInfo ci) {
        if (state != null && state.getBlock() instanceof BlockGhost) {
            OptiFineMaterial.seat(buffer, pos, world);
        } else {
            OptiFineMaterial.unseat();
        }
    }
}
