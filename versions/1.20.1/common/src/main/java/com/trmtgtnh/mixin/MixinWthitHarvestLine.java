package com.trmtgtnh.mixin;

import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.block.GhostInherit;

import mcp.mobius.waila.api.IBlockAccessor;

/**
 * WTHIT's harvest line over a worn square, read for the block it covers (0.9.227, spec GF9; Xep: "Hook it, played"), as
 * {@link MixinJadeHarvestLine} hands Jade's: the tool and tier the covered block wants, never the stand-in's own,
 * which wants none. WTHIT's harvest provider reads the block state once, in {@code appendBody}, and decides the whole
 * line from it, so that one read is handed {@link GhostInherit#harvestStateFor}, which answers anything that is not a
 * ghost with its own state. Off at WTHIT's defaults - its harvest line draws nothing until a player turns it on - which
 * is why no panel showed the fault. Named by its class, {@code require = 0}, and refused by {@link OculusGate} where
 * WTHIT is absent, as Jade's is.
 */
@Pseudo
@Mixin(targets = "mcp.mobius.waila.plugin.harvest.provider.HarvestProvider", remap = false)
public abstract class MixinWthitHarvestLine {

    @Redirect(
        method = "appendBody",
        at = @At(value = "INVOKE", target = "Lmcp/mobius/waila/api/IBlockAccessor;getBlockState", remap = false),
        require = 0,
        remap = false)
    private BlockState trmt$theCoveredBlock(IBlockAccessor accessor) {
        return GhostInherit.harvestStateFor(accessor.getBlockState(), accessor.getPosition());
    }
}
