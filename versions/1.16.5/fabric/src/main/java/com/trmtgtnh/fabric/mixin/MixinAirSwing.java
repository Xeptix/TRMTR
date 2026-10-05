package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

import com.trmtgtnh.item.AirSwing;

/**
 * Notices a left-click that landed on nothing.
 *
 * <p>
 * <strong>This is what {@code Item.onEntitySwing} was, and it is vanilla rather than a loader's
 * addition.</strong> Both older editions hear an air swing through that method, which Forge adds to
 * the item - so the two tools that care override it. There is no such method on the other loader
 * here, which is why {@code PacketAirSwing} was the one message this port deferred; what asks now is
 * the client's own attack handling, which decides between an entity, a block and nothing, and the
 * third case is this gesture exactly.
 *
 * <p>
 * At the return, so the decision has been made and the arm has swung. The callback carries no
 * value, because this method answers none - which the refmap is what settled, not a guess: it
 * writes the descriptor out, and the descriptor ends in V. Nothing is cancelled and
 * nothing is changed - this only listens, and {@link AirSwing} is where the two cheap checks live
 * that keep a packet off the wire for every swing of every sword.
 *
 * <p>
 * Twice, once per loader, for the refmap reason every paired mixin in this port is under: a mixin
 * that names a method has that name written into a refmap in one loader's names and the other
 * refuses it. The body is one line.
 */
@Mixin(Minecraft.class)
public abstract class MixinAirSwing {

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void trmt$noticeAirSwing(CallbackInfo callback) {
        AirSwing.swung(
            Minecraft.getInstance().player);
    }
}
