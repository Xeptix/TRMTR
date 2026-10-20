package com.trmtgtnh.neoforge.mixin;

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

/**
 * Draws snow and carpet down with the ground worn away under them, through the offset every renderer at
 * this version adds to a block's model - see {@code client.render.Settling}, which says why that is the
 * seat and what it leaves alone.
 *
 * <p>
 * 1.21.1 hooked the state's own {@code getOffset(BlockGetter, BlockPos)} at its return, so whatever the block worked out
 * was kept and only lowered. The body only calls out, for the reason {@link com.trmtgtnh.mixin.MixinPackRepository}
 * gives.
 *
 * <p>
 * <strong>From 1.21.2 a state's offset is asked with the position alone</strong> ({@code getOffset(BlockPos)}), so
 * the state has no level to read the rut under it from - and the rule must read the view the mesher was handed, never
 * the game's level (Settling). So the offset is lowered where it is asked with a level in hand: in
 * {@code ModelBlockRenderer.tesselateBlock}, where the chunk mesher draws every block (SectionCompiler.compile) and
 * reads the offset it adds to the model, handed that block's level and position (26.2.0.88's patched jar). The
 * offset the block worked out is kept and only lowered, as before. A renderer that meshes chunks without that method
 * asks the plain offset and draws snow and carpet where they stand, as a renderer that skipped 1.21.1's hook did.
 *
 * <p>
 * <strong>In each loader's module, not in common, from 0.9.219.</strong> It names a game method, so the
 * name goes into a refmap, and common's processor writes that refmap in Fabric's intermediary names: a
 * production Forge jar has it remapped on the way out, and a Forge development run reads it as it is and
 * refuses the mixin - which, on a class as early as {@code Block}, is a game that never starts. Every
 * mixin left in common injects with {@code remap = false} for that reason; see
 * {@code CommonMixinsNeedNoRefmapTest}. The Fabric module has the same class.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinSettleOnWornGround {

    // Optional, as the 1.7.10 edition's cosmetic hooks are: its loss costs a picture, where a required one that a
    // pack's renderer had moved would stop the game from starting (0.9.222, spec SU21). The handler takes the drawing
    // method's own arguments after the call's, which is where the level comes from.
    @Redirect(
        method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFF"
            + "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getOffset(Lnet/minecraft/core/BlockPos;)"
                + "Lnet/minecraft/world/phys/Vec3;"),
        require = 0)
    private Vec3 trmt$settleOnWornGround(BlockState state, BlockPos pos, BlockQuadOutput output, float x, float y,
        float z, BlockAndTintGetter level, BlockPos drawn, BlockState drawnState, BlockStateModel model, long seed) {
        Vec3 vanilla = state.getOffset(pos);
        Vec3 settled = com.trmtgtnh.client.render.Settling.offset(state, level, pos, vanilla);
        return settled != null ? settled : vanilla;
    }
}
