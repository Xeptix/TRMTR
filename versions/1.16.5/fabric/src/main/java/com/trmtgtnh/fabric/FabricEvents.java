package com.trmtgtnh.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.item.TamperEvents;
import com.trmtgtnh.server.ServerEvents;

/**
 * Where Fabric's events reach this mod, for the four moments Fabric has an event for.
 *
 * <p>
 * Nothing is decided here. Each registration is an unpacking and a call into {@link ServerEvents},
 * which is shared with the other loader and knows nothing about events at all.
 *
 * <p>
 * <strong>The other seven moments are in {@code mixin/} and that is not a shortcut.</strong> Forge
 * fires an event for every one of the eleven this mod listens for; Fabric's API covers four. For the
 * rest there is no event to subscribe to - not a worse one, none - so the only way in is to put the
 * call where the game does the thing. Each of those mixins says what it is in for and why no event
 * would do.
 *
 * <p>
 * A player's own tick is the one that could have gone either way. There is no per-player tick event,
 * but there is a server tick, and walking every player once a tick from there would have worked. It
 * is a mixin instead so that both loaders call {@code playerTicked} from the same place in the tick -
 * inside the player's own tick, after it has moved - rather than once at the end from a list, where a
 * player who moved early in the tick and a player who moved late are both read at the same moment.
 */
public final class FabricEvents {

    private FabricEvents() {}

    public static void open() {
        // /trmt, which is registered each time the command tree is built - once per server start
        // rather than once per launch. See the Forge module's copy of this.
        net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback.EVENT
            .register((dispatcher, dedicated) -> com.trmtgtnh.command.CommandTrmt.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(server -> ServerEvents.serverTicked());

        // As early as Fabric offers for a level, which is before its chunks are asked for.
        ServerWorldEvents.LOAD.register((server, level) -> ServerEvents.levelLoaded(level));

        // A chunk arriving and a chunk going, which is when wear read off disk is promoted onto the
        // main thread and when what is in memory is set aside to be written. The reading and the
        // writing themselves are MixinChunkSerializer's: vanilla's method has the level to hand and
        // neither loader's chunk-data event does. See ErosionStore for what each of the four does.
        ServerChunkEvents.CHUNK_LOAD.register(
            (level, chunk) -> com.trmtgtnh.erosion.ErosionStore.get()
                .chunkLoaded(level, chunk.getPos().x, chunk.getPos().z));
        ServerChunkEvents.CHUNK_UNLOAD.register(
            (level, chunk) -> com.trmtgtnh.erosion.ErosionStore.get()
                .chunkUnloaded(level, chunk.getPos().x, chunk.getPos().z));

        // The player is read off the handler's own field rather than from a getter: this version's
        // packet listener holds it as `player` with nothing to ask.
        ServerPlayConnectionEvents.JOIN
            .register((handler, sender, server) -> ServerEvents.playerJoined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerEvents.playerLeft(handler.player));

        // AFTER rather than BEFORE: the state is handed over as it was, and a break that something
        // else cancels should take no wear with it.
        PlayerBlockBreakEvents.AFTER
            .register((level, player, pos, state, entity) -> ServerEvents.blockBroken(level, pos, state));

        // A left-click with a tamper is a gesture, not a dig.
        //
        // SUCCESS is what refuses the dig here, and refusing it is what makes the server send the
        // block straight back and never begin destroying it. Both sides are called and only the
        // server acts; the client's copy is what swings the arm. See TamperEvents, which Forge
        // reaches through an event of its own.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, face) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!TamperEvents.holdsTamper(held)) return InteractionResult.PASS;
            TamperEvents.get()
                .leftClicked(level, player, pos.getX(), pos.getY(), pos.getZ(), held);
            return InteractionResult.SUCCESS;
        });

        // And the second refusal: a tamper breaks nothing, however long it is held on a block. The
        // callback above stops a click starting a dig; this stops one that started another way.
        PlayerBlockBreakEvents.BEFORE
            .register((level, player, pos, state, entity) -> !TamperEvents.holdsTamper(player.getMainHandItem()));
    }

    /** Called by the chunk-watch mixin, which has the tracker rather than the player to hand. */
    public static void chunkWatched(ServerPlayer player, int chunkX, int chunkZ) {
        ServerEvents.chunkWatched(player, chunkX, chunkZ);
    }
}
