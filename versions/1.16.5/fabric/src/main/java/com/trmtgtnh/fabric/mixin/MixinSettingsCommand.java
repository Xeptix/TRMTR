package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;

import com.trmtgtnh.client.gui.SettingsAccess;

/**
 * {@code /trmt config} on the client: the line is caught as the player sends it, answered here, and never sent
 * (0.9.228; Xep, 2026-10-20: "/trmt config").
 *
 * <p>
 * <strong>Not Fabric API's client commands, though 0.42 has them.</strong> A client command named {@code trmt} answers
 * every line that starts with it: with {@code /trmt config} registered, {@code /trmt reload} reads on the client as an
 * incorrect argument, which Fabric's client commands refuse on the spot rather than pass on, so the server's own
 * {@code /trmt} would hear nothing else. So the line is caught where every chat line leaves the client -
 * {@code LocalPlayer.chat}, which the chat screen's send calls - and {@link SettingsAccess#typed} takes this one line and
 * nothing else; every other line goes on to the server as before. The Forge jar catches the same line through
 * {@code ClientChatEvent}, its own hook at the same place.
 *
 * <p>
 * At the head, cancelled only for that line: nothing reaches the connection, and the chat screen still keeps the line in
 * its history, which it does before it sends.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinSettingsCommand {

    @Inject(method = "chat", at = @At("HEAD"), cancellable = true)
    private void trmt$settingsCommand(String message, CallbackInfo callback) {
        if (SettingsAccess.typed(message)) callback.cancel();
    }
}
