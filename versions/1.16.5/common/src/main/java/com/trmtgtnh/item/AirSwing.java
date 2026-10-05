package com.trmtgtnh.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.Client;
import com.trmtgtnh.network.TrmtNetwork;

/**
 * A left-click that landed on nothing, which is a gesture the game posts no event for.
 *
 * <p>
 * <strong>Asked from a different place than in either older edition, and it is a better place.</strong>
 * 1.7.10 and 1.12.2 both notice the swing from {@code Item.onEntitySwing}, which is Forge's own
 * addition to the item - so the two tools that care about an air swing override it and say so. There
 * is no such method on the other loader here, which is why {@code PacketAirSwing} was the one
 * message this port deferred.
 *
 * <p>
 * What replaced it is the client's own attack handling, which is vanilla's: when the attack key is
 * pressed the client decides between an entity, a block and nothing, and the third case is exactly
 * this gesture. That is the same code on both loaders, so both ask the same question in the same
 * place - which is closer to the same behaviour than two different hooks would have been.
 *
 * <p>
 * The two checks the item used to make for free are made here instead: that the swinger is this
 * client's own player, and that what they are holding is one of the tools that opted in. Neither is
 * trusted on the far side - the server re-reads the held item - but both keep a packet off the wire
 * for every swing of every sword.
 */
public final class AirSwing {

    private AirSwing() {}

    /**
     * The attack key was pressed and hit nothing. Tells the server, if there is anything to tell it.
     *
     * <p>
     * Called from each loader's own mixin on that moment, which exists twice for the refmap reason
     * every other paired mixin in this port does - the bodies are this one line.
     */
    public static void swung(Player swinger) {
        if (swinger == null) return;
        ItemStack held = swinger.getMainHandItem();
        if (held == null || held.isEmpty() || !(held.getItem() instanceof AirSwingTool)) return;
        if (!Client.swungAtNothing(swinger)) return;
        TrmtNetwork.sendAirSwingIfMissed(swinger);
    }
}
