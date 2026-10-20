package com.trmtgtnh.neoforge;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.trmtgtnh.Client;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.ClientSide;
import com.trmtgtnh.client.texture.WearPackSource;
import com.trmtgtnh.client.texture.WearTextures;

/**
 * The client tick, on NeoForge. The Forge module's TrmtForgeClient carried across, and the Fabric one's opposite
 * number, deliberately the same shape.
 *
 * <p>
 * Two things happen here and they are the whole of it: while there is a world, the client layer gets its tick - the
 * overlay drains the queue of arrived records, work other threads left for this one is run, and a pending rebuild of
 * the wear pictures counts down - and when the world goes away, it is told once so everything it was holding goes back
 * where it came from. Leaving is the half that must happen whatever else does.
 *
 * <p>
 * The harness is a class of its own, which runs only when {@code -Dtrmt.spike} is set, and which this hands the
 * tick to after the real work and never before it.
 *
 * <p>
 * {@code value = Dist.CLIENT}, because this subscribes to the game's bus and a server that loaded it would subscribe to
 * a client tick that never comes - which is not an error and is exactly the kind of quiet nothing this project keeps
 * finding.
 */
@EventBusSubscriber(modid = Trmt.MODID, value = Dist.CLIENT)
public final class TrmtNeoForgeClient {

    /** Whether there is a world to leave, so leaving happens once rather than every idle tick. */
    private static boolean left;

    private TrmtNeoForgeClient() {}

    /**
     * The client's half of the mod's constructor, called from {@link TrmtNeoForge} once the loader says this is a
     * client.
     *
     * <p>
     * A method here rather than lines there because NeoForge has no {@code DistExecutor}: Forge's constructor wrapped
     * each client line in one so a server never loaded the classes they name, and a class only a client calls into
     * does the same job. Each line is the Forge constructor's, in its order.
     */
    static void construct(ModContainer container) {
        // As early as there is a client. The pack repository takes its sources when it is built and the resource
        // manager is assembled from whatever was offered then, so a pack wired any later is a pack the manager never
        // heard of - and every wear sprite the atlas asks for comes back as a missing texture.
        WearPackSource.use(WearTextures.SHEETS);

        // Where an arriving packet puts its news, and where the ghost's model asks what to draw. Both are the same
        // object, so it is wired once and here: a packet can arrive before anything on a screen has happened, and a
        // model asked for quads with no seam behind it draws plain earth rather than a path.
        Client.use(new ClientSide());

        // Where the mod list's Config button goes: NeoForge's IConfigScreenFactory, registered on the mod's own
        // container, where Forge registered its ConfigScreenFactory through ModLoadingContext. The screen itself is
        // shared and says the rest.
        container.registerExtensionPoint(
            net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
            (net.neoforged.neoforge.client.gui.IConfigScreenFactory) (mod, parent) -> com.trmtgtnh.client.gui.ConfigScreen
                .build(parent));
    }

    // The end of the tick, which NeoForge posts as an event of its own (Post) where Forge posted one event with a
    // phase to check.
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
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

    /** A chunk arriving in the client's world, which NeoForge announces once its blocks are in (spec PT18). */
    @SubscribeEvent
    public static void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (event.getLevel() == null || !event.getLevel()
            .isClientSide()) return;
        ClientSide.chunkLoaded(
            event.getChunk()
                .getPos().x,
            event.getChunk()
                .getPos().z);
    }
}
