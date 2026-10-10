package com.trmtgtnh.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.ClientSide;

/**
 * The client tick, on Forge. The Fabric one's opposite number and deliberately the same shape.
 *
 * <p>
 * Two things happen here and they are the whole of it: while there is a world, the client layer gets
 * its tick - the overlay drains the queue of arrived records, work other threads left for this one is
 * run, and a pending rebuild of the wear pictures counts down - and when the world goes away, it is
 * told once so everything it was holding goes back where it came from. Leaving is the half that must
 * happen whatever else does.
 *
 * <p>
 * <strong>This class used to be five hundred lines, and all but these two were a harness.</strong> It
 * opened a world named {@code spike} from the title screen, walked the player across it for two
 * minutes, put the world on peaceful, closed the pause screen under them, moved the camera and took
 * photographs - ungated, in the shipped jar, with only the absence of a save by that name standing
 * between a player and all of it. That is now {@link ForgeSpikeRun}, which runs only when
 * {@code -Dtrmt.spike} is set, and which this hands the tick to after the real work and never before
 * it.
 *
 * <p>
 * {@code value = Dist.CLIENT} rather than a side annotation on the class, because this subscribes to
 * the Forge event bus and a server that loaded it would subscribe to a client tick that never comes -
 * which is not an error and is exactly the kind of quiet nothing this project keeps finding.
 */
@Mod.EventBusSubscriber(modid = Trmt.MODID, value = Dist.CLIENT)
public final class TrmtForgeClient {

    /** Whether there is a world to leave, so leaving happens once rather than every idle tick. */
    private static boolean left;

    private TrmtForgeClient() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            if (left) {
                left = false;
                ClientSide.leaveWorld();
            }
            // What a tick with no world still owes - see ClientSide.idleTick.
            ClientSide.idleTick();
        } else {
            left = true;
            ClientSide.tick();
        }
    }

    /** A chunk arriving in the client's world, which Forge announces once its blocks are in (spec PT18). */
    @SubscribeEvent
    public static void chunkLoaded(net.minecraftforge.event.world.ChunkEvent.Load event) {
        if (event.getWorld() == null || !event.getWorld()
            .isClientSide()) return;
        ClientSide.chunkLoaded(
            event.getChunk()
                .getPos().x,
            event.getChunk()
                .getPos().z);
    }
}
