package com.trmtgtnh;

import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.trmtgtnh.command.CommandTrmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.erosion.PhysicalDecay;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceRegistry;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * TRMT Reimagined: The Roads More Travelled rebuilt for Forge 1.7.10 and GT: New Horizons.
 * Original mod by milkucha, CC BY-NC 4.0 — see LICENSE.md and ATTRIBUTION.md.
 *
 * <p>
 * Terrain accumulates wear where people actually walk, and shows it. The wear lives
 * entirely in per-chunk NBT on the server; the block array is never touched, so this whole
 * mod can be deleted from a pack without altering a single block of terrain.
 */
@Mod(
    modid = Trmt.MODID,
    name = Trmt.NAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    guiFactory = "com.trmtgtnh.client.gui.TrmtGuiFactory")
public final class Trmt {

    // The id is what a save writes down, so it is fixed for the life of the mod; the name is
    // only ever read by a person, and is free to change.
    public static final String MODID = "trmtgtnh";
    public static final String NAME = "TRMT Reimagined";

    public static final Logger LOG = LogManager.getLogger(NAME);

    @Mod.Instance(MODID)
    public static Trmt instance;

    @SidedProxy(clientSide = "com.trmtgtnh.client.ClientProxy", serverSide = "com.trmtgtnh.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        TrmtConfig.load(event.getSuggestedConfigurationFile());
        TrmtNetwork.init();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Guarded by the mod being present: the compat class is the only thing that mentions
        // Waila, and it is never loaded unless Waila answers this.
        if (cpw.mods.fml.common.Loader.isModLoaded("Waila")) {
            com.trmtgtnh.compat.WailaCompat.request();
        }
        // Resolved once here as well as after post-init, because the block atlas is stitched in
        // between and the stitcher can only generate wear textures for surfaces it already knows
        // about. Without this the per-surface textures are empty until the player forces a
        // resource reload, and worn ground borrows vanilla dirt in the meantime.
        SurfaceRegistry.resolve();
        proxy.init();
        // Here rather than in post-init, and the placement is load-bearing. Amazing Trophies reads
        // its folder in its own post-init, and nothing orders two mods within a phase, so a write
        // from post-init won a launch or lost one depending on which of us FML happened to call
        // first - and on this pack it lost, which is why no trophy ever arrived. Every mod gets
        // init before any mod gets post-init, so writing here wins whatever the sort order. It
        // needs the items from pre-init and the achievements from proxy.init() above, and nothing
        // that is still to come.
        com.trmtgtnh.compat.TrophyCompat.writeDefinitions();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Again, now that every other mod has finished registering. Anything that appeared
        // since init is picked up; a second pass that finds nothing new writes nothing.
        SurfaceRegistry.resolve();
        // Has to follow surface resolution: it stamps every block with whether its family can
        // ever sink, which is what keeps the collision path off a registry lookup.
        PhysicalDecay.markSinkableBlocks();
        Trmt.proxy.markSettlingBlocks();
        com.trmtgtnh.block.GhostInherit.survey();
        com.trmtgtnh.compat.QuestbookCompat.writeQuests();
        proxy.postInit();
    }

    /**
     * What becomes of the four per-material tampers an old save still names.
     *
     * <p>
     * They were separate items until they were folded into the one graded tamper, and a save made
     * before that still names them in its id table. Left unanswered, Forge stops the world loading
     * to ask whether to carry on without them - a prompt in single player, and a console question a
     * dedicated server sits waiting on - and carrying on deletes every one of them from every chest.
     * Only a world saved on a build from before the first public release names them at all, so
     * this is for those worlds and costs nothing anywhere else.
     *
     * <p>
     * They are let go without the question rather than handed to the graded tamper. Forge gives an
     * item exactly one id, and handing a retired name to the graded tamper registers that tamper again
     * under the retired name's id. A save old enough to name a retired tamper names the graded tamper
     * as well, as the one this was found on did, and often more than one retired tamper besides, so
     * the second registration is refused, and Forge then declares the whole save corrupted and will
     * not open it at all: from 0.9.200 to 0.9.207 that is what these worlds did. Letting them go is
     * what carrying on through Forge's question always did. Each retired id is blocked, so nothing
     * registered later can take it and turn an old stack into something else, and whatever of them
     * was left in the world is gone. The log names each one let go.
     */
    @Mod.EventHandler
    public void missingMappings(cpw.mods.fml.common.event.FMLMissingMappingsEvent event) {
        for (cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping mapping : event.get()) {
            if (mapping.type != cpw.mods.fml.common.registry.GameRegistry.Type.ITEM) continue;
            if (!RETIRED_TAMPERS.contains(mapping.name)) continue;
            mapping.ignore();
            LOG.info(
                "Let go of {}, a per-material tamper from before the graded tamper that this save still names: its id is blocked from reuse, and any of it left in the world is gone",
                mapping.name);
        }
    }

    /**
     * Rebuilds the surface table, and what is stamped from it, when the block ids in force change.
     *
     * <p>
     * The surface table, its ground-cover and holder sets, and the stamps taken from them are keyed by the
     * number each block carries at the moment they are built, and they were built once, under the numbers
     * blocks were registered with. A world keeps the numbers it was first saved with, though, and a server
     * hands its own to every client that joins it. Forge moves every block onto those numbers when the world
     * loads or the join completes, and back again when either ends, and nothing here followed. So on any
     * world whose numbers differ from a fresh registration - which is any world that has outlived a change
     * to its mod list - a modded block was looked up under a number that had since gone to another block:
     * the wrong ground wore, the right ground did not, and ground cover could be broken off blocks that
     * were never ground cover. Vanilla ground was spared only because vanilla numbers never move.
     *
     * <p>
     * Fired for the revert as well as the load, which is what puts right a rebuild made under numbers about
     * to go. A kick can reach the client between the check that ends a visit and the end of that tick, and
     * the visit's table is then handed back, and the client's own rebuilt, while the server's numbers are
     * still in force; the revert a tick later rebuilds it again under the client's own.
     *
     * <p>
     * The wear atlas is filed by block object rather than by id, so a move leaves it as it is; only the
     * origin remembered under each painted position is a number, and it is turned back into a block under
     * the ids in force when it is drawn.
     */
    @Mod.EventHandler
    public void idsMoved(cpw.mods.fml.common.event.FMLModIdMappingEvent event) {
        proxy.idsMoved();
    }

    /** The registry names the per-material tampers were saved under. */
    private static final java.util.Set<String> RETIRED_TAMPERS = new java.util.HashSet<String>(
        java.util.Arrays.asList(
            MODID + ":tamper_iron",
            MODID + ":tamper_gold",
            MODID + ":tamper_diamond",
            MODID + ":tamper_netherite"));

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Fired on the thread the server will tick on, in single player and on a dedicated server
        // alike. See onServerThread.
        serverThread = Thread.currentThread();
        event.registerServerCommand(new CommandTrmt());
    }

    /**
     * About to start rather than starting, because a new world places its bonus chest while it is being
     * built and generates its spawn area before the starting event, and anything put in a chest pool has
     * to be there before the first chest is. Every mod has finished loading by now, which is what the
     * Wayfarer's check needs. See ModLoot.settleWayfarer.
     */
    @Mod.EventHandler
    public void serverAboutToStart(cpw.mods.fml.common.event.FMLServerAboutToStartEvent event) {
        com.trmtgtnh.item.ModLoot.settleWayfarer();
    }

    /**
     * Started rather than starting, so the questbook has finished loading its own databases before
     * this looks at one of them. Which of two mods handles the starting event first is not
     * decidable from here, and reading a file halfway through somebody else's load is not a thing
     * to leave to chance.
     */
    @Mod.EventHandler
    public void serverStarted(cpw.mods.fml.common.event.FMLServerStartedEvent event) {
        com.trmtgtnh.compat.QuestbookCompat.examineWorld(
            cpw.mods.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        serverThread = null;
        // The stopped server's own pending work, and only that. It used to be thrown away from the
        // client's disconnect, which in single player ran while this server was still shutting
        // down and could discard a reload it had queued; the client's queue is its own business.
        com.trmtgtnh.util.MainThread.clearServer();
        com.trmtgtnh.compat.QuestbookCompat.forgetWorld();
        ErosionStore.get()
            .clearMemory();
        ErosionEngine.get()
            .clearOrphans();
        ErosionEngine.get()
            .reset();
        com.trmtgtnh.erosion.Weather.reset();
        com.trmtgtnh.erosion.SnowCover.reset();
    }

    /**
     * Accepts whatever the other side's copy of this mod is, or that it has none.
     *
     * <p>
     * This settles only the comparison of mod lists, and it is not what decides whether a client
     * without this mod can join a server that has it. That is decided afterwards, by Forge's
     * block and item handshake, which refuses any client missing a server's registrations - and
     * this mod registers both. So a server running it needs it on every client. What answering
     * yes here does allow is a client carrying it onto a server that does not, where nothing is
     * sent to it and nothing is drawn.
     */
    @NetworkCheckHandler
    public boolean checkRemoteVersions(java.util.Map<String, String> remoteVersions, Side side) {
        return true;
    }

    /** The thread the running server ticks on, recorded as it starts, or null with none running. */
    private static volatile Thread serverThread;

    /**
     * Whether the caller is on the running server's own thread.
     *
     * <p>
     * 1.7.10 has no way to ask, and most of what this mod does on a server assumes the answer. A
     * command typed in chat or at the console runs on the server thread, and so does every one of this
     * mod's packet handlers, since the connection's queue is drained there, and so does the logged-out
     * event, which the connection's tick announces. A command from a chat bridge's own thread does not,
     * nor one over RCon where a mod has mended vanilla's listener, and neither do Forge's own
     * disconnection events, fired on the network thread as a connection closes. False with no server
     * running, which sends a caller to queue its work rather than do it.
     */
    public static boolean onServerThread() {
        Thread recorded = serverThread;
        return recorded != null && recorded == Thread.currentThread();
    }

    /**
     * Whether the thread a server was recorded on as it started is still alive, running or on its way
     * out.
     *
     * <p>
     * Asked by a client with no world loaded, to learn whether the block ids can still move under it
     * from another thread. A single-player client is let go the moment its server marks itself
     * stopped, and the ids are put back on that server's thread only afterwards, with the stopped
     * event that clears the record following straight after. Alive rather than merely recorded, so a
     * server that dies without that event cannot hold anything for ever. A server that gives up before
     * its starting event is never recorded, so the ids a failed start puts back are not covered.
     */
    public static boolean serverThreadAlive() {
        Thread recorded = serverThread;
        return recorded != null && recorded.isAlive();
    }

    /** Convenience for the many places that need "is there a server and is it ours". */
    public static MinecraftServer server() {
        return MinecraftServer.getServer();
    }

    /**
     * The server only while it is actually running, which is not the same question.
     *
     * <p>
     * Minecraft keeps its server in a static that is assigned once and never cleared, so on a
     * client that has opened a single-player world even once, {@link #server()} keeps answering
     * for the rest of the session — long after that world is closed and while connected to
     * somebody else's server. Anything deciding "is there a world here that I own" has to ask
     * whether the thing is still running, or it ends up handing work to a dead thread.
     */
    public static MinecraftServer runningServer() {
        MinecraftServer candidate = MinecraftServer.getServer();
        return candidate != null && candidate.isServerRunning() ? candidate : null;
    }
}
