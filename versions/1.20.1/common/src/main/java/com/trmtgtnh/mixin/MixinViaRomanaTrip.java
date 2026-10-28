package com.trmtgtnh.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.compat.viaromana.TripWear;

/**
 * Notes each Via Romana fast trip as it is asked for, so the road it goes along can be worn once it lands (0.9.228;
 * Xep's decision of 2026-10-20, "Via Romana", part two, off unless integration.viaRomanaTripWear is on).
 *
 * <p>
 * The target is the trip request itself, Via Romana's record
 * {@code net.rasanovum.viaromana.network.packets.TeleportRequestC2S}, and its {@code handle(Level, Player)}, which
 * passes the request to {@code teleport.ServerTeleportHandler.handleTeleportRequest} on the server - read from all four
 * of its 2.2.3 jars. Injected at its head so the request is in hand as {@code this}; the request's own accessors are
 * asked by name in {@code compat.viaromana.ViaRomana}, since naming the record's type here would put Via Romana on the
 * compile path. Not required and refused where Via Romana is not installed, as {@link MixinViaRomanaPathBlock} is.
 */
@Pseudo
@Mixin(targets = "net.rasanovum.viaromana.network.packets.TeleportRequestC2S", remap = false)
public abstract class MixinViaRomanaTrip {

    @Inject(method = "handle", at = @At("HEAD"), require = 0, remap = false)
    private void trmt$noteTheTrip(Level level, Player player, CallbackInfo info) {
        TripWear.requested(this, level, player);
    }
}
