package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.trmtgtnh.server.ServerEvents;

/**
 * Bone meal mends a worn patch (spec WD40 to WD43); see {@link ServerEvents#boneMealed}.
 *
 * <p>
 * This loader has no bone meal event, so the hook sits where Forge fires its own: at the head of {@code growCrop}, which
 * both a player's use and a dispenser go through. When it mends something the handful is spent and the call answers
 * that it did, so nothing grows - Forge's allowed result, exactly. The player is not in that call, so the use hands it
 * over on its way in, and that same use is the click that proves the handful came from a hand. A dispenser names no
 * player: it pays from nothing, so with the cost on it mends nothing, and it never earns experience.
 */
@Mixin(BoneMealItem.class)
public class MixinBoneMealMends {

    /** Who is using bone meal on this thread just now, between the use and the growing it asks for. */
    private static final ThreadLocal<Player> USING = new ThreadLocal<Player>();

    @Inject(method = "useOn", at = @At("HEAD"))
    private void trmt$usedBy(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        ServerEvents
            .rightClickedHolding(context.getPlayer(), context.getLevel(), context.getClickedPos(), context.getItemInHand());
        USING.set(context.getPlayer());
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void trmt$usedUp(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        USING.remove();
    }

    @Inject(method = "growCrop", at = @At("HEAD"), cancellable = true)
    private static void trmt$mends(ItemStack stack, Level level, BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        Player player = USING.get();
        // Taken once: a use that threw before its end must not lend its player to the next dispenser.
        USING.remove();
        if (ServerEvents.boneMealed(level, pos, player)) {
            stack.shrink(1);
            callback.setReturnValue(true);
        } else if (ServerEvents.boneMealAnsweredOnTheClient(level, pos)) {
            // The client's answer over a worn square it holds a record for, so the game stops at this hand instead of
            // going on to the off hand with a second use (0.9.222). Nothing spent: the server's answer decides that.
            callback.setReturnValue(true);
        }
    }
}
