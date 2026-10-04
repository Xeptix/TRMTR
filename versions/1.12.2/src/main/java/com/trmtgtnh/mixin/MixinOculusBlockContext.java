package com.trmtgtnh.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.block.BlockGhost;
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
 * in no refmap - and the method is matched by name because the holder declares exactly one. {@code require = 0} for
 * the same reason every optional seam in this mod carries it: a version of Oculus that has moved this
 * should cost the feature and nothing else. {@link OculusGate} refuses the whole config on a client
 * that has no Oculus at all, so the usual case is that this is never even considered.
 */
@SideOnly(Side.CLIENT)
@Pseudo
@Mixin(targets = OculusGate.HOLDER, remap = false)
public abstract class MixinOculusBlockContext {

    @Inject(method = "set", at = @At("TAIL"), require = 0, remap = false)
    private void trmt$seatGhostMaterial(IBlockState state, short renderType, CallbackInfo ci) {
        if (state != null && state.getBlock() instanceof BlockGhost) {
            ShaderMaterial.seat(this);
        } else {
            // Given up rather than left standing: the claim is made later, from the model, and a seat
            // still held when the mesher has moved on to ordinary ground is a claim waiting to land on
            // the wrong block.
            ShaderMaterial.unseat();
        }
    }
}
