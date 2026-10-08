package com.trmtgtnh.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.TamperEvents;
import com.trmtgtnh.server.ServerEvents;

/**
 * Where Forge's events reach this mod.
 *
 * <p>
 * Nothing is decided here. Every handler is an unpacking and a call: which of the event's getters
 * holds the level, which holds the position, which holds the state - and then {@link ServerEvents},
 * which is shared with the other loader and knows nothing about events at all.
 *
 * <p>
 * It is a thin file on purpose. The Fabric module has the same list of moments and reaches most of
 * them by an entirely different road, and the only way for the two to stay in step is for the thing
 * they both call to hold all of the thinking. A handler here that did some of it would be a handler
 * Fabric silently does without.
 */
@Mod.EventBusSubscriber(modid = Trmt.MODID)
public final class ForgeEvents {

    private ForgeEvents() {}

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    /**
     * Puts /trmt in front of the game, each time the command tree is built.
     *
     * <p>
     * Which is once per server start rather than once per launch: the tree is rebuilt for every
     * world, so this fires again for the next one. Registering into the dispatcher it is handed
     * rather than keeping one of our own is what makes that harmless.
     */
    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.trmtgtnh.command.CommandTrmt.register(event.getDispatcher());
    }

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerEvents.playerTicked(event.player);
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        ServerEvents.livingTicked(event.getEntityLiving());
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        ServerEvents.fell(event.getEntityLiving(), event.getDistance());
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerEvents.serverTicked();
    }

    /**
     * At the highest priority, so the healing clock's pause is in place before anything else
     * listening for the same load can ask for a chunk in that level.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWorldLoad(WorldEvent.Load event) {
        if (event.getWorld() instanceof ServerLevel) ServerEvents.levelLoaded((ServerLevel) event.getWorld());
    }

    // ------------------------------------------------------------------
    // Client sync
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerEvents.chunkWatched(event.getPlayer(), event.getPos().x, event.getPos().z);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ServerEvents.playerJoined(event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerEvents.playerLeft(event.getPlayer());
    }

    // ------------------------------------------------------------------
    // Blocks coming and going
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getWorld() instanceof Level)) return;
        ServerEvents.blockBroken((Level) event.getWorld(), event.getPos(), event.getState());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getWorld() instanceof Level)) return;
        // The placer as well, for the golem the builder may stand up: whoever set the head is who it
        // records as having built it.
        ServerEvents.blockPlaced(
            (Level) event.getWorld(),
            event.getPos(),
            event.getPlacedBlock(),
            event.getEntity() instanceof net.minecraft.world.entity.player.Player
                ? (net.minecraft.world.entity.player.Player) event.getEntity()
                : null);
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * Refused by setting the result to DENY, which is Forge's own way of saying so and leaves every
     * spawn this mod has no opinion about exactly as it was.
     */
    @SubscribeEvent
    public static void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!(event.getWorld() instanceof Level)) return;
        boolean barred = ServerEvents.spawnBarred(
            (Level) event.getWorld(),
            event.getEntityLiving(),
            event.getX(),
            event.getY(),
            event.getZ(),
            event.isSpawner());
        if (barred) event.setResult(Event.Result.DENY);
    }

    // ------------------------------------------------------------------
    // Tampers
    // ------------------------------------------------------------------

    /**
     * Left-click, and sneak plus left-click.
     *
     * <p>
     * Cancelled whether or not anything happened, and on both sides: cancelling is what makes the
     * server send the block straight back and never begin destroying it, and a gesture that found
     * nothing to do still must not be allowed to turn into a dig.
     *
     * <p>
     * At the default priority so that every claim-protection mod in the pack vetoes this for free -
     * a cancelled cancellable event is not delivered onward, and the protection mods all cancel at a
     * higher priority than this.
     */
    @SubscribeEvent
    public static void onLeftClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (!TamperEvents.holdsTamper(event.getItemStack())) return;
        event.setCanceled(true);
        TamperEvents.get()
            .leftClicked(
                event.getWorld(),
                event.getPlayer(),
                event.getPos()
                    .getX(),
                event.getPos()
                    .getY(),
                event.getPos()
                    .getZ(),
                event.getItemStack());
    }

    /** The second refusal: a tamper breaks nothing, at any speed. */
    @SubscribeEvent
    public static void onBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (event.getPlayer() == null) return;
        if (TamperEvents.holdsTamper(
            event.getPlayer()
                .getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------
    // Explosions
    // ------------------------------------------------------------------

    /**
     * Detonate rather than Start, because Start can still be cancelled and ground should only be
     * scoured by an explosion that actually happened.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        net.minecraft.world.level.Explosion explosion = event.getExplosion();
        net.minecraft.world.phys.Vec3 centre = explosion.getPosition();
        ServerEvents.exploded(
            event.getWorld(),
            centre.x,
            centre.y,
            centre.z,
            com.trmtgtnh.util.ExplosionSize.of(explosion),
            event.getAffectedBlocks());
    }

    // ------------------------------------------------------------------
    // Crafting
    // ------------------------------------------------------------------

    /** Crafting a tamper awards its step of the ladder. Fabric reaches this through a mixin. */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ServerEvents.crafted(event.getPlayer(), event.getCrafting());
    }

    // ------------------------------------------------------------------
    // Chunks coming and going
    // ------------------------------------------------------------------

    /**
     * A chunk has arrived, so wear read off disk is promoted onto the main thread.
     *
     * <p>
     * The reading and the writing are not here. Forge has {@code ChunkDataEvent} for both and its
     * load half cannot be used: it carries no world, because a chunk being read off disk is a
     * {@code ProtoChunk} and the only thing that answers {@code getWorldForge} is a
     * {@code LevelChunk}. Which level a chunk belongs to is half of the key its wear is stored
     * under, so both loaders go through {@code MixinChunkSerializer} instead, where vanilla hands
     * the level over as an argument.
     *
     * <p>
     * These two moments, though, are what the events are for and are the same on both loaders: a
     * chunk that is now in the world, and one that has left it.
     */
    @SubscribeEvent
    public static void onChunkLoad(net.minecraftforge.event.world.ChunkEvent.Load event) {
        net.minecraft.world.level.chunk.ChunkAccess chunk = event.getChunk();
        if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk)) return;
        Level level = ((net.minecraft.world.level.chunk.LevelChunk) chunk).getLevel();
        com.trmtgtnh.erosion.ErosionStore.get()
            .chunkLoaded(level, chunk.getPos().x, chunk.getPos().z);
    }

    /**
     * A chunk has gone. Its record is set aside rather than dropped, because the save comes after
     * this and the record has to still be there to be written - see {@code ErosionStore.unloading}.
     */
    @SubscribeEvent
    public static void onChunkUnload(net.minecraftforge.event.world.ChunkEvent.Unload event) {
        net.minecraft.world.level.chunk.ChunkAccess chunk = event.getChunk();
        if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk)) return;
        Level level = ((net.minecraft.world.level.chunk.LevelChunk) chunk).getLevel();
        com.trmtgtnh.erosion.ErosionStore.get()
            .chunkUnloaded(level, chunk.getPos().x, chunk.getPos().z);
    }
}
