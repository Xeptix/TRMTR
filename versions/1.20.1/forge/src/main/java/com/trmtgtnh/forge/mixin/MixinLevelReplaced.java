package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import com.trmtgtnh.client.ClearsInPacketOrder;
import com.trmtgtnh.client.ClientSide;

/**
 * The client's world is being replaced - a change of dimension - so every record, light and queued chunk goes (spec
 * PN34), queued behind this mod's packets that arrived before the respawn, not run on the spot (0.9.222).
 *
 * <p>
 * See {@link ClientSide#forgetLevel} for what goes and {@link ClearsInPacketOrder} for why it waits in this mod's
 * queue: the old world's wear still waiting there was applied after an on-the-spot clear, into the new world. Called
 * only from the login and respawn handlers, on the client thread, where this mod's packets join its queue on this
 * version, so the clear lands between the old world's and the new one's. Asked only when there is a world to replace,
 * so the first world joined forgets nothing; leaving a server is {@link ClientSide#leaveWorld}, which this does not
 * replace. The Fabric module has the same hook, for the reason {@link MixinChunkForgotten} gives.
 */
@Mixin(Minecraft.class)
public class MixinLevelReplaced {

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void trmt$levelReplaced(ClientLevel next, CallbackInfo callback) {
        if (((Minecraft) (Object) this).level != null) ClearsInPacketOrder.worldReplaced(ClientSide::forgetLevel);
    }
}
