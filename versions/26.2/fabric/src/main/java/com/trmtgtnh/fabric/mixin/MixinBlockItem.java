package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

import com.trmtgtnh.server.ServerEvents;

/**
 * A block has been placed, so a record set aside when it was broken can come back to it.
 *
 * <p>
 * Forge fires {@code BlockEvent.EntityPlaceEvent}. Fabric has an event for breaking and none for
 * placing, which is the asymmetry this file exists to close.
 *
 * <p>
 * <strong>At the return of placing rather than at the head</strong>, and only when it succeeded: a
 * placement that was refused - no room, wrong side, a protection mod saying no - must not hand a
 * record back to ground that nothing was placed on. The state is read out of the world rather than
 * off the item, because what finally stands there is the block's own choice and may be a different
 * state from the one the item would have made.
 *
 * <p>
 * This catches a block placed from an item, which is what the other editions' event catches too. A
 * block written straight into the world by another mod reaches neither, there or here.
 */
@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @Inject(method = "place", at = @At("RETURN"))
    private void trmt$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> callback) {
        InteractionResult result = callback.getReturnValue();
        if (result == null || !result.consumesAction()) return;
        if (context == null || context.getLevel() == null) return;
        // The placer as well, for the golem the builder may stand up: whoever set the head is who it
        // records as having built it.
        ServerEvents.blockPlaced(
            context.getLevel(),
            context.getClickedPos(),
            context.getLevel()
                .getBlockState(context.getClickedPos()),
            context.getPlayer());
    }
}
