package com.trmtgtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.client.render.ShaderMaterial;

/**
 * Hands over the seat a ghost claims its shader material from.
 *
 * <p>
 * Oculus works out a block's shader id once per block and leaves the answer on this holder for its
 * vertex writer to read as it writes. That makes the holder the one object worth having, and this is
 * the only moment it can be had: it is kept in a field of Oculus's own chunk buffers, and nothing
 * hands it out.
 *
 * <p>
 * Taken at the tail rather than the head, so the id Oculus resolved is already in place and a square
 * this mod has nothing to say about is left exactly as Oculus left it. Only our own blocks are seated,
 * which is what keeps this off the meshing hot path in any real sense: a chunk of ordinary ground pays
 * one {@code instanceof} per block and never touches a thread-local.
 *
 * <p>
 * Nothing is put back. It does not need to be - the next block through has its own id resolved by the
 * method this rides on, before anything of ours can speak again - and {@link ShaderMaterial#seat} drops
 * the previous claim as it takes the seat, so a ghost can never inherit the answer given for the ghost
 * before it.
 *
 * <p>
 * {@code @Pseudo} because the target is not on the compile classpath and is not going to be: naming it
 * for real would mean a build dependency on Oculus, which is the one thing this must not cost. It tells
 * the annotation processor to stop looking and leaves the match to be made at runtime, which is where
 * the answer actually is. {@code remap = false} throughout for the same reason - another mod's class is
 * in no refmap, which is also why this is the one mixin in the whole port that both loaders can
 * share a single copy of - and the method is matched by name because the holder declares exactly one. {@code require = 0} for
 * the same reason every optional seam in this mod carries it: a version of Oculus that has moved this
 * should cost the feature and nothing else. {@link OculusGate} refuses the whole config on a client
 * that has no Oculus at all, so the usual case is that this is never even considered.
 */
@Pseudo
@Mixin(targets = OculusGate.HOLDER, remap = false)
public abstract class MixinOculusBlockContext {

    // Iris 1.8, 1.21's (0.9.226), hands the holder Iris's own id for each block and no block state, so there is no
    // asking here whether the block is one of ours: every block takes the seat, and only a ghost's model ever claims
    // from it - its claim lands on the block being meshed, and the next block's setBlockData puts its own id back.
    @Inject(method = "setBlockData", at = @At("TAIL"), require = 0, remap = false)
    private void trmt$seatGhostMaterial(int blockId, byte renderType, byte blockEmission, int localPosX, int localPosY,
        int localPosZ, CallbackInfo ci) {
        ShaderMaterial.seat(this);
    }
}
