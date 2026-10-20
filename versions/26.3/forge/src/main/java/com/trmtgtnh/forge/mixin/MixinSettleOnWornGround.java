package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

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
 * {@code CommonMixinsNeedNoRefmapTest}. The Fabric module has the same class.
 *
 * <p>
 * <strong>By 26.1 a state's offset is asked with the position alone</strong> ({@code BlockStateBase.getOffset(BlockPos)},
 * world/level/block/state/BlockBehaviour.java), so the state has no view to read the rut from. The renderer that adds
 * the offset to the model has one: Forge's {@code ModelBlockRenderer.tesselateBlock}, handed the chunk's view and the
 * square, asks the state for its offset there. So the offset it is answered is lowered there, by the same rule and
 * with the same view 1.21.1's state was handed; the snow and carpet that settle have no offset in their shapes, so the
 * picture was all this ever moved.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinSettleOnWornGround {

    // Optional, as the 1.7.10 edition's cosmetic hooks are: its loss costs a picture, where a required one that a
    // pack's renderer had moved would stop the game from starting (0.9.222, spec SU21).
    @Redirect(
        method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFF"
            + "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J"
            + "Lnet/minecraftforge/client/model/data/ModelData;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getOffset(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"),
        require = 0)
    private Vec3 trmt$settleOnWornGround(BlockState state, BlockPos at, BlockQuadOutput output, float x, float y,
        float z, BlockAndTintGetter level, BlockPos pos, BlockState drawn, BlockStateModel model, long seed,
        ModelData data) {
        Vec3 vanilla = state.getOffset(at);
        Vec3 settled = com.trmtgtnh.client.render.Settling.offset(state, level, at, vanilla);
        return settled != null ? settled : vanilla;
    }
}
