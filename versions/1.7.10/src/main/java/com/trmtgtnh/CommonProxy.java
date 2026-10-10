package com.trmtgtnh;

import net.minecraftforge.common.MinecraftForge;

import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.config.ConfigReload;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.item.ModItems;
import com.trmtgtnh.item.ModRecipes;
import com.trmtgtnh.item.TamperEvents;
import com.trmtgtnh.server.ServerEvents;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Everything that runs on both sides, and the seam the client half hangs off.
 *
 * <p>
 * Packet handlers dispatch through the proxy rather than calling client classes directly.
 * Message handler classes are loaded on both sides when the channel is registered, so a
 * direct reference to anything client-only would risk a dedicated server tripping over a
 * class it cannot see.
 */
public class CommonProxy {

    public void preInit() {
        ModBlocks.register();
        ModItems.register();
        registerEntities();
        // The config screen posts its "you changed something" event on FML's bus and nowhere
        // else. Registered on the Forge bus, as this was, the listener is simply never called
        // and pressing Done does nothing at all - not even write the file.
        FMLCommonHandler.instance()
            .bus()
            .register(new TrmtConfig.ChangeListener());
        MinecraftForge.EVENT_BUS.register(ErosionStore.get());
        MinecraftForge.EVENT_BUS.register(ServerEvents.get());
        FMLCommonHandler.instance()
            .bus()
            .register(ServerEvents.get());
        // On both sides, and it has to be: one of its two handlers exists to stop the client
        // making dig progress while a tamper is held, which is what keeps a held left-click from
        // repeating the gesture.
        MinecraftForge.EVENT_BUS.register(TamperEvents.get());
    }

    /** Registered in pre-init beside the blocks and items, because the id map is compared on connect. */
    public void registerEntities() {
        com.trmtgtnh.entity.ModEntities.register();
    }

    public void init() {
        // Registered here - the common proxy, which the client does not override - so client
        // and server settle on the same enchantment id, which is written into an enchanted tool.
        // Registered whatever the feature switch says, so the id is always claimed and a tool
        // that carries it does not turn into a broken enchantment when the feature is off;
        // whether it can be applied or does anything is gated live on the switch instead.
        com.trmtgtnh.item.EnchReinforce.register(com.trmtgtnh.config.TrmtConfig.reinforceEnchantId);
        com.trmtgtnh.item.EnchWard.register(com.trmtgtnh.config.TrmtConfig.wardEnchantId);
        com.trmtgtnh.item.EnchLight.register(com.trmtgtnh.config.TrmtConfig.lightEnchantId);
        // Same reasoning one line up, and the same consequence for getting it wrong: an
        // effect id is written into a save and sent to a client as a raw byte, so both
        // sides have to settle on the same number or each shows the other's as something
        // else. Claimed whatever the switches say, so a draught drunk before somebody
        // turned the feature off does not become an unresolvable effect in their save.
        com.trmtgtnh.item.ModPotions.register(
            com.trmtgtnh.config.TrmtConfig.lightnessPotionId,
            com.trmtgtnh.config.TrmtConfig.heavyFootPotionId);
        // After the items, because every icon on the page is one of them.
        com.trmtgtnh.item.ModAchievements.register();
        cpw.mods.fml.common.network.NetworkRegistry.INSTANCE
            .registerGuiHandler(Trmt.instance, new com.trmtgtnh.entity.TrmtGuiHandler());
    }

    public void postInit() {
        // Here rather than at init because a recipe's ingredients are a question about the whole
        // pack, and only by now is every mod's answer in.
        ModRecipes.register();
        // After the recipes, so every item that could be found already exists.
        com.trmtgtnh.item.ModLoot.register();
    }

    /** Called after the config GUI or a command changes something. */
    public void onConfigChanged(ConfigReload.Delta delta) {}

    /** Says that a server owns some of what was just typed. Only a client has anyone to tell. */
    public void tellServerOwnsGeometry() {}

    // -- packet sinks; the client proxy is where these actually do something --

    public void handleChunkErosion(int chunkX, int chunkZ, int[] keys, short[] states) {}

    public void handleDelta(int x, int y, int z, short state) {}

    /**
     * True when this client's own player is aiming at nothing a click could land on.
     *
     * <p>
     * False everywhere else, including for another player's swing arriving as an animation. Only
     * the client has a crosshair, so only the client can answer.
     */
    public boolean swungAtNothing(net.minecraft.entity.player.EntityPlayer swinger) {
        return false;
    }

    public void handleChunkLight(int chunkX, int chunkZ, int[] keys, byte[] values) {}

    public void handleLightDelta(int x, int y, int z, int packed) {}

    /** What the client believes is glowing here. Nothing, on a side that has no client. */
    public int clientLightLevel(int x, int y, int z) {
        return 0;
    }

    public int clientLightPacked(int x, int y, int z) {
        return 0;
    }

    /**
     * The wear recorded at a position, or {@link ErosionState#NONE} when there is none.
     *
     * <p>
     * Never -1 for "nothing", however tempting the symmetry with the origin lookup: -1 as a
     * short is every bit set, which decodes as a real family at the deepest rut this mod can
     * make. A miss has to be the same value as an empty record or a chunk edge becomes a hole.
     */
    public short erosionStateAt(int x, int y, int z) {
        return ErosionState.NONE;
    }

    /**
     * Opens a tamper's settings screen, where there is a screen to open.
     *
     * <p>
     * Client-side only by nature rather than by guard: everything on it lives in the stack's own
     * data, which the client already holds, so there is nothing to synchronise before showing it.
     */
    public void openTamperScreen(net.minecraft.item.ItemStack stack) {}

    /**
     * Remembers whether this client may edit which blocks wear.
     *
     * <p>
     * Only ever used to decide whether to draw the controls. Every edit is checked again on the
     * server, because what a client believes about its own privileges is not evidence.
     */
    public void setMayEditFamilies(boolean allowed) {}

    /**
     * Whether the settings modifier is down on this client. False everywhere but a client.
     *
     * <p>
     * A dedicated server has no keyboard to ask, and asks {@code TamperModifiers} instead - which
     * is told by the client, ahead of the click. See that class for why.
     */
    public boolean modifierHeld() {
        return false;
    }

    /** The server's Wear-Table pricing, or null. Held only on the client; the base does nothing. */
    public void setServerPricing(com.trmtgtnh.erosion.WearMath.Pricing pricing) {}

    public com.trmtgtnh.erosion.WearMath.Pricing serverPricing() {
        return null;
    }

    public boolean mayEditFamilies() {
        return false;
    }

    public void handleClearAll() {}

    /** Applies a server's session rules. Only a client has anything to do with them. */
    public void applyServerRules(boolean forceOverlay, String decayMode,
        com.trmtgtnh.config.ServerRules.Geometry geometry) {}

    /** A server named its surface table's fingerprint. Only a client has anything to do with it. */
    public void considerServerTable(boolean sent, long fingerprint) {}

    /**
     * Rebuilds the surface table, and what is stamped from it, under the block ids now in force. See
     * {@code Trmt.idsMoved} for why. Run where it stands: on a dedicated server both ways in - a world's
     * ids loading and their revert when it stops - arrive on the server thread, before any player can
     * be on the world or after the last has gone.
     */
    public void idsMoved() {
        Trmt.LOG.info("Block ids changed; rebuilding the surface table under them");
        com.trmtgtnh.surface.SurfaceRegistry.resolve();
        com.trmtgtnh.erosion.PhysicalDecay.markSinkableBlocks();
        markSettlingBlocks();
        com.trmtgtnh.block.GhostInherit.survey();
    }

    /** A server's surface table arrived. Only a client has anything to do with it. */
    public void installServerTable(long fingerprint, boolean holdsSwitch, byte[] bytes) {}

    /** Receives the wear numbers for a position the player is looking at. */
    public void acceptInspection(int x, int y, int z, float wear, float threshold, int untouchedSeconds,
        int recoverySeconds, int chainIndex, int chainLength, int reinforce, int ward) {}

    /** The golem's screen, or null off the client. See TrmtGuiHandler for why it comes from here. */
    public Object golemScreen(net.minecraft.entity.player.EntityPlayer player,
        com.trmtgtnh.entity.EntityGolemOfWays golem) {
        return null;
    }

    /** Opens a guide book. Only a client has anywhere to read it. */
    public void openGuideScreen(int bookOrdinal) {}

    /** Opens or refreshes the config-snapshot screen. Only a client has one. */
    public void openSnapshotScreen(int flags) {}

    /** Asks the server what the snapshot slots hold, so the screen can open. Client only. */
    public void requestSnapshotScreen() {}

    /** Shows a wear-pattern preview. Only a client has a picture to change. */
    public void applyDevPreview(int mode) {}

    /**
     * Sets one family's wear look. Only a client has one, and only its own.
     *
     * <p>
     * A look changes no state, no collision and no record - only which sprites a client bakes into
     * its own atlas - so it is that client's to choose and travels on no packet. A server that has
     * a value for this key in its own file keeps it, and it goes on driving nothing, which is what
     * it did before anybody noticed.
     */
    public void setWearLook(com.trmtgtnh.surface.SurfaceFamily family, String pattern) {}

    /** True on a physical client whose player has the overlay switched on. */
    public boolean overlayActive() {
        return false;
    }

    /**
     * The block a ghost is standing in for at a position, or null off the client. Ghost
     * blocks ask this so they can mirror what they cover rather than guess at it.
     */
    public net.minecraft.block.Block originBlockAt(int x, int y, int z) {
        return null;
    }

    /** Restamps which blocks are drawn down with worn ground. Nothing on a server, which never draws anything. */
    public void markSettlingBlocks() {}

    /**
     * How far the ground at this position has dropped under whatever is resting on it, in block
     * units, as the client believes it.
     *
     * <p>
     * Asked of the proxy rather than worked out twice, and that is the whole point of it being
     * here. The server has the erosion record and reads it directly; a client has a ghost and reads
     * the ghost - and the client's answer has to be the same number the renderer moved the picture
     * by, not merely one computed from the same inputs. A hair of difference between the two would
     * be the player standing a hair off the snow they can see, every tick, on every snowfield.
     */
    public double settledDropUnder(int x, int y, int z) {
        return 0.0D;
    }

    /** Packed {@code blockId << 4 | meta} of the covered block, or -1 when nothing is known. */
    public int originPackedAt(int x, int y, int z) {
        return -1;
    }
}
