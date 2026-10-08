package com.trmtgtnh.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.pipeline.BlockInfo;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.block.BlockGhost;

/**
 * Forge's light pipeline, which draws every smooth-lit block unless something has switched it off: a whole ghost
 * shades the corners beside it, as the block it replaced did - see {@link BlockGhost#wholeAt}.
 *
 * <p>
 * Forge fills a three-by-three-by-three table of how much each cell around the block shades, from each cell's
 * state alone, and a ghost's state answers that it shades nothing. This puts the right figure back for each
 * cell that is a whole ghost, at the end, once the table is full. Only a cell that shades nothing can be a
 * ghost, so only those are looked at again.
 */
@Mixin(value = BlockInfo.class, remap = false)
public abstract class MixinGhostShadeForge {

    @Shadow
    private IBlockAccess world;

    @Shadow
    private BlockPos blockPos;

    @Shadow
    @Final
    private float[][][] ao;

    @Inject(method = "updateLightMatrix", at = @At("TAIL"))
    private void trmt$wholeGhostsShade(CallbackInfo callback) {
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    if (ao[x][y][z] != 1.0F) continue;
                    BlockPos pos = blockPos.add(x - 1, y - 1, z - 1);
                    if (world.getBlockState(pos)
                        .getBlock() instanceof BlockGhost && BlockGhost.wholeAt(world, pos)) {
                        ao[x][y][z] = BlockGhost.WHOLE_SHADE;
                        BlockGhost.shadeSeen("Forge's light pipeline");
                    }
                }
            }
        }
    }
}
