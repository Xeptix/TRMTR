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
 * <strong>Named by its descriptor, in the names the game runs under.</strong> OptiFine's class has two {@code
 * pushEntity}s, so the method is named with its descriptor. Until 1.21.1 it was named twice: in Forge's names, where
 * OptiFine ships its own classes in those names, and in Fabric's intermediary ones, where OptiFabric remapped OptiFine
 * into them. From 26.1 the game ships unobfuscated and every loader runs Mojang's names, so the one name is both.
 *
 * <p>
 * <strong>This seat is Forge's; OptiFabric has its own.</strong> Under OptiFabric the seat does not bind -
 * OptiFabric defines OptiFine's classes after Mixin has read every config - and binding would not help: there a
 * ghost's FRAPI model is drawn by Indigo, handed the chunk's buffer from OptiFine's rebuild past OptiFine's own
 * push, so no push for a ghost arrives. There the Fabric ghost model pushes the covered block's entry itself
 * (GhostModelFabric, 0.9.220). An OptiFabric that one day loads OptiFine sooner finds the seat under the same name.
 */
@Pseudo
@Mixin(targets = OculusGate.OPTIFINE_SEAT, remap = false)
public abstract class MixinOptiFineShaderSeat {

    @Inject(
        // Forge 47's runtime names are Mojang's for classes, as OptiFine HD U I6 ships them (javap, 0.9.225); 1.16.5's
        // net/minecraft/block/BlockState and IVertexBuilder, carried here first, bound nothing. From 26.1 the game is
        // not obfuscated and every loader runs Mojang's names, Fabric included - intermediary is gone - so the one
        // descriptor names the method wherever OptiFine runs; 1.21.1's intermediary twin could match nothing here.
        method = "pushEntity(Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V",
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
