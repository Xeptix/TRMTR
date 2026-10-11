package com.trmtgtnh.forge.mixin;

import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.block.GhostInherit;

import mcp.mobius.waila.api.IDataAccessor;

/**
 * Jade's harvest line reads the block a worn square covers, so it names the tool and the level that block needs
 * (0.9.223; Xep, 2026-10-10, the tooltip's harvest line "from the covered block").
 *
 * <p>
 * Jade asks the block state alone what tool it wants, at what level, and whether it needs one at all - and a ghost is
 * one block for every family, made of earth, so worn obsidian said no tool was needed while its break speed, the
 * covered block's, said otherwise. Its harvest provider reads the state once, from the tooltip's accessor, and asks
 * everything of that; this hands it the covered block there instead, through {@link GhostInherit#harvestStateFor}.
 * Jade is the only tooltip on these editions that reads a harvest line on the client: The One Probe works its line out
 * on the server, from the real block, and Hwyla and WTHIT draw none.
 *
 * <p>
 * Not required, and named without a descriptor: Jade is not always there, and a Jade that changed this method keeps
 * its own answer rather than stopping the game. {@link GhostInherit#harvestStateFor} says once in the log when it has
 * answered.
 */
@Pseudo
@Mixin(targets = "snownee.jade.addon.vanilla.HarvestToolProvider", remap = false)
public abstract class MixinJadeHarvestLine {

    @Redirect(
        method = "getText",
        at = @At(value = "INVOKE", target = "Lmcp/mobius/waila/api/IDataAccessor;getBlockState", remap = false),
        require = 0,
        remap = false)
    private BlockState trmt$theCoveredBlock(IDataAccessor accessor) {
        return GhostInherit.harvestStateFor(accessor.getBlockState(), accessor.getPosition());
    }
}
