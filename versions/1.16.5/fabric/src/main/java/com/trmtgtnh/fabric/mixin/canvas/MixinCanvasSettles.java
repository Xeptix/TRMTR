package com.trmtgtnh.fabric.mixin.canvas;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.client.render.Settling;

/**
 * Draws snow and carpet down with the ground worn away under them under Canvas, as every other renderer here does.
 *
 * <p>
 * The others add a block state's offset to its model whatever the block is, which is the seat
 * {@link Settling} answers through. Canvas asks first whether the block's offset type is none, and asks the state
 * for an offset only when it is not - and snow and carpet have none, so it never asked, and a snowed-over rut was
 * drawn with its snow standing at the height of the road beside it. Found by the bench's snow and carpet
 * photographs on 2026-10-08, where Canvas alone drew them flat. So Canvas's one question is answered by
 * {@link Settling#offsetTypeFor}: a type with an offset for a block that settles, the block's own for anything
 * else. Vanilla's offset itself still reads the real type, so a settling block not on worn ground is drawn where
 * it stands.
 *
 * <p>
 * {@code @Pseudo}, because Canvas is not on this build's classpath, and {@code require = 0}, because a Canvas that
 * has rewritten its region builder should cost the settling, not the game: the settling's own once-only line,
 * "Settling a block onto worn ground", is how a Canvas log says this ran. Gated by {@link CanvasGate}.
 */
@Pseudo
@Mixin(targets = "grondag.canvas.terrain.region.BuiltRenderRegion", remap = false)
public abstract class MixinCanvasSettles {

    @Redirect(
        method = "buildTerrain",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getOffsetType()Lnet/minecraft/world/level/block/state/BlockBehaviour$OffsetType;",
            remap = true))
    private BlockBehaviour.OffsetType trmt$askSettlingBlocksWhereToDraw(Block block) {
        return Settling.offsetTypeFor(block);
    }
}
