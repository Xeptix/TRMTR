package com.trmtgtnh;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * TRMT Reimagined for Minecraft 1.12.2.
 *
 * <p>
 * The second edition of a mod that has already been written once, for 1.7.10. At present it carries
 * the part of that mod which needed no porting, its settings, and a rendering spike: one ghost block,
 * drawn with generated wear sprites at the depth its square has sunk to, painted over the grass around
 * the player from fixed data.
 *
 * <p>
 * The mod id is the one the 1.7.10 edition uses, and that is deliberate. It is the namespace of every
 * registry name, lang key and resource path, so keeping it lets the resources and every translation
 * come across untouched, and lets the data files this mod writes for other mods land where they
 * already land. The two editions cannot meet - they are different Minecraft versions - so there is
 * nothing for the shared name to collide with.
 */
@Mod(
    modid = Tags.MOD_ID,
    name = Tags.MOD_NAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    // The mixin loader, named so that a pack without it is told rather than left to wonder. This is
    // something the other edition cannot do: UniMixins is a tweaker, with no mod id to require, so
    // there the only place a player is told is the download page's relations block. MixinBooter
    // registers itself as an ordinary mod, so Forge can refuse the launch and say why.
    //
    // required-after rather than required-before: what the config needs is to be read, which happens
    // at the loader's own stage long before anything here runs, and the ordering only says that this
    // mod loads after it.
    dependencies = "required-after:mixinbooter",
    guiFactory = "com.trmtgtnh.client.gui.TrmtGuiFactory")
public class Trmt {

    /**
     * This mod, as Forge holds it.
     *
     * <p>
     * Only one thing in either edition needs it: the gui handler is registered against a mod object
     * and a screen is opened by naming the same one, which is how Forge routes a window request back
     * to whoever asked for it.
     */
    @Mod.Instance(Tags.MOD_ID)
    public static Trmt instance;

    /**
     * The mod id, by the name the 1.7.10 edition gives it.
     *
     * <p>
     * The generated {@code Tags.MOD_ID} says the same, and is what the annotation above reads. This
     * exists because the settings file is carried across from the other edition line for line, and it
     * asks for {@code Trmt.MODID}; answering to both names keeps that file unedited.
     */
    public static final String MODID = Tags.MOD_ID;

    public static final String NAME = Tags.MOD_NAME;

    public static final Logger LOG = LogManager.getLogger(Tags.MOD_ID);

    @SidedProxy(clientSide = "com.trmtgtnh.client.ClientProxy", serverSide = "com.trmtgtnh.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("{} {} for Minecraft 1.12.2", NAME, Tags.VERSION);
        TrmtConfig.load(event.getSuggestedConfigurationFile());
        com.trmtgtnh.network.TrmtNetwork.init();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Guarded by the mod being present: the compat class is the only thing that mentions the
        // block tooltip, and it is never loaded unless that mod answers this. Hwyla registers itself
        // under the lower-case name where the other edition's Waila answered to a capital one.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("waila")) {
            com.trmtgtnh.compat.WailaCompat.request();
        }
        // Before the atlas is built, because the stitcher can only generate wear pictures for
        // surfaces it already knows about - the same ordering the 1.7.10 edition keeps, and for the
        // same reason.
        SurfaceRegistry.resolve();
        proxy.init();
    }

    /**
     * Detection again, now that every other mod has finished registering.
     *
     * <p>
     * Anything that appeared since init is picked up, and a second pass that finds nothing new
     * writes nothing. The 1.7.10 edition follows this with stamping every block that can sink and
     * surveying what the ghosts stand in for; neither of those exists here yet.
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        SurfaceRegistry.resolve();
        // Which blocks can ever sink, stamped once the surfaces they belong to are known - so the
        // collision path, which runs for every moving thing every tick, can dismiss the rest with one
        // lookup.
        com.trmtgtnh.erosion.PhysicalDecay.markSinkableBlocks();
        // What a tamper can be made of, and what one costs - both questions about the whole pack, and
        // so neither askable before now: a grade is only available when the ore dictionary has its
        // material, and a recipe's ingredients are the same question asked again. The grades are
        // resolved at the top of the recipe registration, which is where the other edition asks it.
        com.trmtgtnh.item.ModRecipes.register();
        // Two files offered to two other mods, written where each of them already looks. After the
        // recipes, because both describe things this mod has only just finished deciding it has.
        com.trmtgtnh.compat.TrophyCompat.writeDefinitions();
        com.trmtgtnh.compat.QuestbookCompat.writeQuests();
    }

    /**
     * Looks at the world's own questbook, so a player can be told whether this mod has a chapter in
     * it.
     *
     * <p>
     * At server-started rather than at world-load, because the answer is about the save and the save
     * is not there to be read any earlier.
     */
    @Mod.EventHandler
    public void serverStarted(net.minecraftforge.fml.common.event.FMLServerStartedEvent event) {
        com.trmtgtnh.compat.QuestbookCompat.examineWorld(
            net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        serverThread = Thread.currentThread();
        // Recorded first, because the command's own first act is to ask whether it is on this thread.
        event.registerServerCommand(new com.trmtgtnh.command.CommandTrmt());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        serverThread = null;
        // The stopped server's own pending work, and only that. It used to be thrown away from the
        // client's disconnect, which in single player ran while this server was still shutting
        // down and could discard a reload it had queued; the client's queue is its own business.
        com.trmtgtnh.util.MainThread.clearServer();
        com.trmtgtnh.compat.QuestbookCompat.forgetWorld();
        com.trmtgtnh.erosion.ErosionStore.get()
            .clearMemory();
        com.trmtgtnh.erosion.ErosionEngine.get()
            .clearOrphans();
        com.trmtgtnh.erosion.ErosionEngine.get()
            .reset();
        com.trmtgtnh.erosion.Weather.reset();
        com.trmtgtnh.erosion.SnowCover.reset();
    }

    /** The thread the running server ticks on, recorded as it starts, or null with none running. */
    private static volatile Thread serverThread;

    /**
     * Whether the caller is on the running server's own thread.
     *
     * <p>
     * Carried from the 1.7.10 edition, where the reasoning is set out in full: a command from a chat
     * bridge's own thread, one over RCon, and Forge's disconnection events do not arrive on the server
     * thread, and most of what this mod does on a server assumes they do. It matters more here than it
     * did there, because in 1.12.2 packet handlers run on the network thread as well. False with no
     * server running, which sends a caller to queue its work rather than do it.
     */
    public static boolean onServerThread() {
        Thread recorded = serverThread;
        return recorded != null && recorded == Thread.currentThread();
    }

    /** Whether the thread a server was recorded on as it started is still alive. */
    public static boolean serverThreadAlive() {
        Thread recorded = serverThread;
        return recorded != null && recorded.isAlive();
    }

    /**
     * The server, if the game has one.
     *
     * <p>
     * 1.7.10 kept it in a static on {@code MinecraftServer}; 1.12.2 asks FML.
     */
    public static MinecraftServer server() {
        return FMLCommonHandler.instance()
            .getMinecraftServerInstance();
    }

    /**
     * The server only while it is actually running, which is not the same question.
     *
     * <p>
     * A client that has opened a single-player world keeps a server instance around after that world
     * is closed, so anything deciding "is there a world here that I own" has to ask whether it is still
     * running, or it ends up handing work to a dead thread.
     */
    public static MinecraftServer runningServer() {
        MinecraftServer candidate = server();
        return candidate != null && candidate.isServerRunning() ? candidate : null;
    }
}
