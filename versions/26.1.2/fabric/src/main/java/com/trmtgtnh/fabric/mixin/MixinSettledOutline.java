package com.trmtgtnh.fabric.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.client.render.Settling;

/**
 * The outline round snow or carpet settled onto worn ground is drawn where the block is drawn, by the same test that
 * lowers the crosshair's hit in {@link MixinSettledHit} - the 1.7.10 edition's MixinSettledOutline (0.9.223).
 *
 * <p>
 * Not required: a renderer that rewrites how the hit outline is drawn - OptiFine replaces this class whole - leaves the
 * outline where it was, up over a hit that has come down, which is how it stood before 0.9.223, rather than refusing to
 * start. What may never happen, an outline lowered over a hit that was not, cannot: the outline comes down only once a
 * hit has (Settling.outline).
 *
 * <p>
 * By 26.1 the outline is worked out into a render state before it is drawn: the shape is asked in
 * {@code extractBlockOutline} (read from 26.1.2), where 1.21.1 asked it in {@code renderHitOutline}, which now draws
 * the shape it is handed. The same call is redirected there.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinSettledOutline {

    @Redirect(
        method = "extractBlockOutline",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"),
        require = 0)
    private VoxelShape trmt$outlineWhereItIsDrawn(BlockState state, BlockGetter level, BlockPos pos,
        CollisionContext context) {
        return Settling.outline(state, level, pos, state.getShape(level, pos, context));
    }
}
