package com.trmtgtnh.mixin;

import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * refuses the mixin outright on a client with no OptiFine.
 *
 * <p>
 * <strong>Named twice, once for each way OptiFine runs.</strong> OptiFine's class has two {@code
 * pushEntity}s, so the method is named with its descriptor, and a descriptor is written in the names the
 * game runs under: Forge's, where OptiFine ships its own classes in those names, and Fabric's
 * intermediary ones, where OptiFabric remaps OptiFine into them. Only one of the two exists in any game,
 * which {@code require = 0} lets be. The handler itself is written once, in this tree's names, and the
 * build turns those into whichever loader's it is going to.
 *
 * <p>
 * <strong>This seat is Forge's; OptiFabric has its own.</strong> Under OptiFabric the seat does not bind -
 * OptiFabric defines OptiFine's classes after Mixin has read every config - and binding would not help: there a
 * ghost's FRAPI model is drawn by Indigo, handed the chunk's buffer from OptiFine's rebuild past OptiFine's own
 * push, so no push for a ghost arrives. There the Fabric ghost model pushes the covered block's entry itself
 * (GhostModelFabric, 0.9.220). The intermediary name stays, costing nothing, against an OptiFabric that one day
 * loads OptiFine sooner.
 */
@Pseudo
@Mixin(targets = OculusGate.OPTIFINE_SEAT, remap = false)
public abstract class MixinOptiFineShaderSeat {

    @Inject(
        method = { "pushEntity(Lnet/minecraft/block/BlockState;Lcom/mojang/blaze3d/vertex/IVertexBuilder;)V",
            "pushEntity(Lnet/minecraft/class_2680;Lnet/minecraft/class_4588;)V" },
        at = @At("TAIL"),
        require = 0,
        remap = false)
    private static void trmt$seatGhostMaterial(BlockState state, VertexConsumer buffer, CallbackInfo ci) {
        if (state != null && state.getBlock() instanceof BlockGhost) {
            OptiFineMaterial.seat(buffer);
        } else {
            OptiFineMaterial.unseat();
        }
    }
}
