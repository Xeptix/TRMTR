package com.trmtgtnh.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.TamperEvents;
import com.trmtgtnh.server.ServerEvents;

/**
 * Where NeoForge's events reach this mod. The Forge module's ForgeEvents carried across, moment for moment.
 *
 * <p>
 * Nothing is decided here. Every handler is an unpacking and a call: which of the event's getters holds the level,
 * which holds the position, which holds the state - and then {@link ServerEvents}, which is shared with the other
 * loaders and knows nothing about events at all.
 *
 * <p>
 * It is a thin file on purpose. The Fabric module has the same list of moments and reaches most of them by an entirely
 * different road, and the only way for the three to stay in step is for the thing they all call to hold all of the
 * thinking. A handler here that did some of it would be a handler the others silently do without.
 *
 * <p>
 * Where NeoForge renamed a moment or changed how an answer is given, the handler says so; the moments themselves are
 * Forge's.
 */
@EventBusSubscriber(modid = Trmt.MODID)
public final class NeoForgeEvents {

    private NeoForgeEvents() {}

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    /**
     * Puts /trmt in front of the game, each time the command tree is built.
     *
     * <p>
     * Which is once per server start rather than once per launch: the tree is rebuilt for every world, so this fires
     * again for the next one. Registering into the dispatcher it is handed rather than keeping one of our own is what
     * makes that harmless.
     */
    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        com.trmtgtnh.command.CommandTrmt.register(event.getDispatcher());
    }

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    // The end of a player's tick: NeoForge posts it as an event of its own (Post) where Forge posted one with a phase.
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        ServerEvents.playerTicked(event.getEntity());
    }

    /**
     * Each living thing's tick. NeoForge has no living tick of its own: Forge's LivingTickEvent became the tick of
     * every entity, posted before it ticks (Pre) as Forge's was, so the living ones are picked out here.
     */
    @SubscribeEvent
    public static void onLivingUpdate(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity) {
            ServerEvents.livingTicked((net.minecraft.world.entity.LivingEntity) event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        // A fall's distance is a double from 1.21.5; the rule takes 1.21.1's float.
        ServerEvents.fell(event.getEntity(), (float) event.getDistance());
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    // The end of the server's tick, posted as its own event (Post), as for a player.
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerEvents.serverTicked();
    }

    /**
     * At the highest priority, so the healing clock's pause is in place before anything else listening for the same
     * load can ask for a chunk in that level.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel) ServerEvents.levelLoaded((ServerLevel) event.getLevel());
    }

    // ------------------------------------------------------------------
    // Client sync
    // ------------------------------------------------------------------

    /**
     * A chunk's wear goes to a player as the chunk itself does (spec PN41). NeoForge fires {@code Watch} as the chunk map
     * queues a chunk for a player, before the chunk is sent, and {@code Sent} after the chunk's packet - read from
     * NeoForge 21.1's own jar (0.9.226). A record sent at the queue would reach a client with no chunk to draw it on,
     * and wear that changed while the chunk waited would go nowhere, because a waiting chunk is not tracked by the
     * player yet. Forge 52 fires its {@code Watch} from the send, and Fabric hooks the send itself.
     */
    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Sent event) {
        ServerEvents.chunkWatched(event.getPlayer(), event.getPos().x(), event.getPos().z());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ServerEvents.playerJoined(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerEvents.playerLeft(event.getEntity());
    }

    // ------------------------------------------------------------------
    // Blocks coming and going
    // ------------------------------------------------------------------

    // Last of every handler, and only if none cancelled the break (0.9.222): the event comes before the block goes,
    // and a protection mod refusing the break after this had run left the block standing with its wear wiped.
    // Fabric's PlayerBlockBreakEvents.AFTER comes after a break that happened, which this now matches. NeoForge, as
    // Forge, hands a cancelled event to no handler that did not ask for one.
    //
    // BlockEvent.BreakEvent is BreakBlockEvent by 26.1, and is posted on the client as well as the server
    // (CommonHooks.fireBlockBreak, called from MultiPlayerGameMode and ServerPlayerGameMode in 26.1.2.122's patched
    // jar), where 1.21.1's was the server's alone - so a client's is let go, and the server's is handled as before.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (!(event.getLevel() instanceof Level)) return;
        if (event.getLevel()
            .isClientSide()) return;
        ServerEvents.blockBroken((Level) event.getLevel(), event.getPos(), event.getState());
    }

    // Last of every handler, and only if none cancelled the placement (0.9.222), as for a break: a protection mod
    // refusing the placement after this had run left the record taken back, or a golem built, where nothing was placed.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level)) return;
        // The placer as well, for the golem the builder may stand up: whoever set the head is who it records as having
        // built it.
        ServerEvents.blockPlaced(
            (Level) event.getLevel(),
            event.getPos(),
            event.getPlacedBlock(),
            event.getEntity() instanceof net.minecraft.world.entity.player.Player
                ? (net.minecraft.world.entity.player.Player) event.getEntity()
                : null);
    }

    // ------------------------------------------------------------------
    // Bone meal
    // ------------------------------------------------------------------

    /**
     * Bone meal mends a worn patch (spec WD40 to WD43); see {@link ServerEvents#boneMealed}. Answered only when it
     * mended something, which is the handful spent and growing nothing; otherwise the game grows whatever it always
     * grew. A dispenser comes here too, naming no player.
     *
     * <p>
     * Answered on the client too, over a worn square it holds a record for, so the game stops at the main hand instead
     * of going on to the off hand with a second use (0.9.222; {@link ServerEvents#boneMealAnsweredOnTheClient}).
     *
     * <p>
     * <strong>NeoForge answers differently from Forge, and the handful is spent here.</strong> Forge's event took an
     * ALLOW, and Forge then shrank the stack itself, on the server alone. NeoForge's is answered by
     * {@code setSuccessful(true)}, which cancels the game's own handling and spends nothing - read from its
     * {@code BoneMealItem.applyBonemeal}, which returns at once for a cancelled event. So the server's mend takes one
     * from the stack here, as Fabric's hook does, and the client's answer takes nothing: the server decides that.
     */
    @SubscribeEvent
    public static void onBonemeal(net.neoforged.neoforge.event.entity.player.BonemealEvent event) {
        if (ServerEvents.boneMealed(event.getLevel(), event.getPos(), event.getPlayer())) {
            event.getStack()
                .shrink(1);
            event.setSuccessful(true);
        } else if (ServerEvents.boneMealAnsweredOnTheClient(event.getLevel(), event.getPos())) {
            event.setSuccessful(true);
        }
    }

    /** The right click that proves a handful came from a hand, heard before the item is used. */
    @SubscribeEvent
    public static void onRightClickBlock(
        net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        ServerEvents.rightClickedHolding(event.getEntity(), event.getLevel(), event.getPos(), event.getItemStack());
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * Refused by setting the result to FAIL, which is NeoForge's own way of saying so - Forge's DENY under the result
     * type NeoForge gave this event - and leaves every spawn this mod has no opinion about exactly as it was.
     *
     * <p>
     * The position check vanilla makes of a mob it is about to place, naturally or from a spawner. Its level is the
     * server's accessor, which a world generating chunks hands over too, and the level it belongs to is asked of it; a
     * spawner is one that names its spawner.
     */
    @SubscribeEvent
    public static void onCheckSpawn(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck event) {
        boolean barred = ServerEvents.spawnBarred(
            event.getLevel()
                .getLevel(),
            event.getEntity(),
            event.getX(),
            event.getY(),
            event.getZ(),
            event.getSpawner() != null);
        if (barred) event.setResult(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    // ------------------------------------------------------------------
    // Tampers
    // ------------------------------------------------------------------

    /**
     * Left-click, and sneak plus left-click.
     *
     * <p>
     * Cancelled whether or not anything happened, and on both sides: cancelling is what makes the server send the
     * block straight back and never begin destroying it, and a gesture that found nothing to do still must not be
     * allowed to turn into a dig.
     *
     * <p>
     * At the default priority so that every claim-protection mod in the pack vetoes this for free - a cancelled
     * cancellable event is not delivered onward, and the protection mods all cancel at a higher priority than this.
     */
    @SubscribeEvent
    public static void onLeftClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (!TamperEvents.holdsTamper(event.getItemStack())) return;
        event.setCanceled(true);
        TamperEvents.get()
            .leftClicked(
                event.getLevel(),
                event.getEntity(),
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
    public static void onBreakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (event.getEntity() == null) return;
        if (TamperEvents.holdsTamper(
            event.getEntity()
                .getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------
    // Explosions
    // ------------------------------------------------------------------

    /**
     * Detonate rather than Start, because Start can still be cancelled and ground should only be scoured by an
     * explosion that actually happened.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        net.minecraft.world.level.Explosion explosion = event.getExplosion();
        net.minecraft.world.phys.Vec3 centre = explosion.center();
        ServerEvents.exploded(
            event.getLevel(),
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
        ServerEvents.crafted(event.getEntity(), event.getCrafting());
    }

    // ------------------------------------------------------------------
    // Chunks coming and going
    // ------------------------------------------------------------------

    /**
     * A chunk has arrived, so wear read off disk is promoted onto the main thread.
     *
     * <p>
     * The reading and the writing are not here. NeoForge, as Forge, has {@code ChunkDataEvent} for both, and its load
     * half cannot be used: a chunk being read off disk is a {@code ProtoChunk}, which knows no level, and which level a
     * chunk belongs to is half of the key its wear is stored under, so every loader goes through
     * {@code MixinChunkSerializer} instead, where vanilla hands the level over as an argument.
     *
     * <p>
     * These two moments, though, are what the events are for and are the same on every loader: a chunk that is now in
     * the world, and one that has left it.
     */
    @SubscribeEvent
    public static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        net.minecraft.world.level.chunk.ChunkAccess chunk = event.getChunk();
        if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk)) return;
        Level level = ((net.minecraft.world.level.chunk.LevelChunk) chunk).getLevel();
        com.trmtgtnh.erosion.ErosionStore.get()
            .chunkLoaded(level, (net.minecraft.world.level.chunk.LevelChunk) chunk);
    }

    /**
     * A chunk has gone. Its record is set aside rather than dropped, because the save comes after this and the record
     * has to still be there to be written - see {@code ErosionStore.unloading}.
     */
    @SubscribeEvent
    public static void onChunkUnload(net.neoforged.neoforge.event.level.ChunkEvent.Unload event) {
        net.minecraft.world.level.chunk.ChunkAccess chunk = event.getChunk();
        if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk)) return;
        Level level = ((net.minecraft.world.level.chunk.LevelChunk) chunk).getLevel();
        com.trmtgtnh.erosion.ErosionStore.get()
            .chunkUnloaded(level, chunk.getPos().x(), chunk.getPos().z());
    }
}
