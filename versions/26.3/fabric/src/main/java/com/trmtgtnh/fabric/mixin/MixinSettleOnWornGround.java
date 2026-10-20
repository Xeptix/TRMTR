package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws snow and carpet down with the ground worn away under them, through the offset every renderer at
 * this version adds to a block's model - see {@code client.render.Settling}, which says why that is the
 * seat and what it leaves alone.
 *
 * <p>
 * On the state rather than the block, because that is where this version keeps the offset; at the
 * return, so whatever the block worked out is kept and only lowered. The body only calls out, for the
 * reason {@link com.trmtgtnh.mixin.MixinPackRepository} gives.
 *
 * <p>
 * <strong>In each loader's module, not in common, from 0.9.219.</strong> It names a game method, so the
 * name goes into a refmap, and common's processor writes that refmap in Fabric's intermediary names: a
 * production Forge jar has it remapped on the way out, and a Forge development run reads it as it is and
 * refuses the mixin - which, on a class as early as {@code Block}, is a game that never starts. Every
 * mixin left in common injects with {@code remap = false} for that reason; see
 * {@code CommonMixinsNeedNoRefmapTest}. The Forge module has the same class.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinSettleOnWornGround {

    // Optional, as the 1.7.10 edition's cosmetic hooks are: its loss costs a picture, where a required one that a
    // pack's renderer had moved would stop the game from starting (0.9.222, spec SU21).
    //
    // From 1.21.2 the offset is asked with the position alone (getOffset(BlockPos)): the level it was asked in is gone
    // from the question. Only the client's world holds ghosts, so the client's world is the one asked - every renderer
    // draws from it - and a question asked on the integrated server's own thread is answered as the game answers it,
    // as 1.21.1's was there, where no square below is ever a ghost.
    //
    // And before there is a game at all: vanilla's Blocks fills every state's cache as the class loads - the bamboo
    // sapling's shape asks its offset - in the client's bootstrap, before Minecraft is built. Asked for the game then,
    // the first 26.1.2 Fabric client died before its window opened (0.9.227); with no game there is no world to read.
    @Inject(method = "getOffset", at = @At("RETURN"), cancellable = true, require = 0)
    private void trmt$settleOnWornGround(BlockPos pos, CallbackInfoReturnable<Vec3> callback) {
        net.minecraft.client.Minecraft game = net.minecraft.client.Minecraft.getInstance();
        if (game == null) return;
        net.minecraft.server.MinecraftServer server = game.getSingleplayerServer();
        if (server != null && server.isSameThread()) return;
        BlockGetter level = game.level;
        if (level == null) return;
        Vec3 settled = com.trmtgtnh.client.render.Settling
            .offset((BlockState) (Object) this, level, pos, callback.getReturnValue());
        if (settled != null) callback.setReturnValue(settled);
    }
}
