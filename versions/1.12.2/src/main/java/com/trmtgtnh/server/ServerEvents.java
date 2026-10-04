package com.trmtgtnh.server;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.erosion.Reinforcement;
import com.trmtgtnh.item.ModPotions;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.util.ExplosionSize;
import com.trmtgtnh.util.MainThread;

/**
 * Every handler the other edition keeps here is here.
 *
 * <p>
 * What drives everything else: the footfalls that wear ground, the tick the sweep runs on, and the
 * events that tell the store a block it was tracking has gone. The 1.7.10 edition's version of this
 * file is the mod's wiring loom - it also carries achievements, quest books, spawn grants, the golem's
 * head-placing, bone-meal mending and the chunk-watch that feeds clients - and it is the one file that
 * genuinely cannot be carried across whole, because it names something from nearly every milestone
 * that has not arrived yet. Carrying it would have meant stubbing half the mod to satisfy a hub.
 *
 * <p>
 * So this is written rather than carried, and it grows a handler at a time as the subsystems land.
 * Each handler here keeps the other edition's reasoning, because the reasoning is the part worth
 * having; what differs is that 1.12.2 hid every event's fields behind getters, which is most of the
 * hundred differences this file would otherwise have.
 *
 * <p>
 * Still to come, each with the milestone that brings it: crafting achievements and first-join grants
 * (books and items); the quest-book admin notice (compat); a golem's head being placed (the golem);
 * and bone-meal mending and its experience (tools, with the recipes).
 */
public final class ServerEvents {

    private static final ServerEvents INSTANCE = new ServerEvents();

    private ServerEvents() {}

    public static ServerEvents get() {
        return INSTANCE;
    }

    private int sampleCounter;

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        if (!dueThisTick()) return;

        float multiplier = ErosionEngine.multiplierFor(event.player);
        if (multiplier > 0f) ErosionEngine.get()
            .onMoverTick(event.player, multiplier);
    }

    /**
     * Everything else that walks.
     *
     * <p>
     * The lead switch and the mob list are two ways in, and {@code multiplierFor} asks each of them
     * properly. Returning on the lead switch alone shut the list off with it, so villagers stopped
     * wearing paths the moment led animals were told not to. Skipped only when neither could answer,
     * which keeps this event costing nothing on a pack that uses neither.
     */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!TrmtConfig.erodeFromLeashedMobs && TrmtConfig.mobMultipliers.isEmpty()) return;
        EntityLivingBase entity = event.getEntityLiving();
        if (entity == null || entity.world == null || entity.world.isRemote) return;
        if (entity instanceof EntityPlayer) return; // handled above
        if (!dueThisTick()) return;

        float multiplier = ErosionEngine.multiplierFor(entity);
        if (multiplier > 0f) ErosionEngine.get()
            .onMoverTick(entity, multiplier);
    }

    /**
     * Movement is sampled rather than read every tick.
     *
     * <p>
     * Wear is credited per block entered, not per tick spent standing on one, so sampling costs
     * nothing in accuracy until a sprinting player crosses two block boundaries inside the window.
     */
    private boolean dueThisTick() {
        return TrmtConfig.movementSampleTicks <= 1 || (sampleCounter % TrmtConfig.movementSampleTicks) == 0;
    }

    /**
     * What lands hard marks where it landed.
     *
     * <p>
     * Weighted by the same figure walking uses, so whatever is allowed to wear a path is allowed to
     * leave a landing mark and nothing else is - a player scuffs the ground, a falling zombie does
     * not, and both answers come from one setting rather than two.
     *
     * <p>
     * The landing half of the two draughts. Walking is guarded inside the engine, where the record of
     * the last block stood on lives; a fall reaches the ground by a different road and has to be
     * guarded on it, or somebody light on their feet would leave no track and still crater on arrival.
     * Scaled here rather than inside impact, which already quotes everything against this figure - so
     * four times the mark needs no second formula.
     */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (!TrmtConfig.enabled || !TrmtConfig.fallWear) return;
        EntityLivingBase entity = event.getEntityLiving();
        if (entity == null || entity.world == null || entity.world.isRemote) return;

        float multiplier = ErosionEngine.multiplierFor(entity);
        if (multiplier <= 0f) return;

        if (ModPotions.treadsLightly(entity, entity.getRidingEntity())) return;
        multiplier *= ModPotions.wearFactor(entity, entity.getRidingEntity());

        ErosionEngine.get()
            .impact(entity.world, entity.posX, entity.posY - 1.0D, entity.posZ, event.getDistance(), multiplier);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        sampleCounter++;
        MainThread.drainServer();
        if ((sampleCounter % 20) == 0) advanceHealClock();
        ErosionEngine.get()
            .onServerTick();
        ErosionEngine.get()
            .sweepOrphans();
    }

    /**
     * Holds the healing clock still while the server is empty.
     *
     * <p>
     * Once a second is enough: it measures whole seconds of in-game time, and what it feeds is read
     * against stamps that are themselves in seconds. Everything hangs off the overworld, because map
     * storage is per-save rather than per-dimension.
     *
     * <p>
     * Golems are untouched by this and that is the point of it. They work from their own ticks in
     * whatever chunks are kept loaded, so a golem tending a road overnight goes on tending it - what
     * stops is the clock that would otherwise have undone the work before anybody saw it.
     */
    private void advanceHealClock() {
        MinecraftServer server = Trmt.runningServer();
        if (server == null || server.worlds == null || server.worlds.length == 0) return;
        World overworld = server.worlds[0];
        if (overworld == null) return;
        HealClockData clock = HealClockData.get(overworld);
        if (clock == null) return;
        // Switching the setting off stops the clock falling further behind; it does not hand back
        // the time already held out. Zeroing the total would age every road on the server by
        // however long it had ever sat empty, the instant somebody flipped a switch, and nothing
        // about that would look like the setting they had just changed.
        boolean counts = server.getCurrentPlayerCount() > 0 || !TrmtConfig.pauseHealingWhenEmpty;
        clock.advance(overworld.getTotalWorldTime() / 20L, counts);
        ErosionEngine.setHealPause((int) clock.idleSeconds());
    }

    /**
     * Sets the healing clock's pause from the save before any of that world's chunks load.
     *
     * <p>
     * The pause is otherwise first set a second into the server's life, and the chunks loaded at
     * startup - the spawn area, and whatever a chunk loader keeps - load before the first tick. They
     * were caught up against a pause of nought, so every idle second the save had ever held back was
     * paid to them as healing at once, which is precisely what pausing an empty server is for
     * stopping; and a second later the pause arrived and left those same records stamped in the
     * future, healing nothing at all for as long again. At the highest priority, so the answer is in
     * place before anything else listening for the same load can ask for a chunk.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world == null || world.isRemote || world.provider == null || world.provider.getDimension() != 0) return;
        HealClockData clock = HealClockData.get(world);
        if (clock != null) ErosionEngine.setHealPause((int) clock.idleSeconds());
    }

    // ------------------------------------------------------------------
    // Client sync
    // ------------------------------------------------------------------

    /**
     * Sends a client a chunk's wear at the moment it starts watching that chunk.
     *
     * <p>
     * The same moment vanilla starts sending it block changes, which is what makes the two agree: a
     * client that has the chunk has the wear on it, and every later change to it reaches the same
     * players by the same test. Nothing is sent to a player who has not said hello, or who has said
     * they do not want it.
     */
    @SubscribeEvent
    public void onChunkWatch(ChunkWatchEvent.Watch event) {
        if (!TrmtConfig.enabled) return;
        EntityPlayerMP player = event.getPlayer();
        if (player == null || player.world == null) return;
        if (!TrmtNetwork.isSubscribed(player)) return;

        int dimension = player.world.provider.getDimension();
        int chunkX = event.getChunk().x;
        int chunkZ = event.getChunk().z;

        ChunkErosionData data = ErosionStore.get()
            .getChunk(dimension, chunkX, chunkZ);
        if (data == null || data.isEmpty()) return;
        TrmtNetwork.sendChunkTo(player, chunkX, chunkZ, data);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) TrmtNetwork.forget((EntityPlayerMP) event.player);
    }

    // ------------------------------------------------------------------
    // Blocks coming and going
    // ------------------------------------------------------------------

    /**
     * A broken block takes its wear with it. Without this the overlay would linger on whatever is
     * placed there next until the next healing pass noticed the mismatch.
     *
     * <p>
     * 1.7.10 handed the block and its metadata over as two fields of the event. 1.12.2 hands over
     * one state, which is both - so the pair is unpacked here rather than read back out of the
     * world, which would be a second lookup and, by the time some other handler has had the event,
     * possibly a different answer.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;
        IBlockState state = event.getState();
        BlockPos pos = event.getPos();
        // Set aside rather than dropped, in case the same block comes straight back.
        ErosionEngine.get()
            .breakBlock(
                world,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                state.getBlock(),
                state.getBlock()
                    .getMetaFromState(state));
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;
        IBlockState state = event.getPlacedBlock();
        BlockPos pos = event.getPos();
        // The same block back in the same spot inside the window gets its record back.
        ErosionEngine.get()
            .placeBlock(
                world,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                state.getBlock(),
                state.getBlock()
                    .getMetaFromState(state));
    }

    /**
     * Bars a category of mob from spawning on a warded block.
     *
     * <p>
     * A mob spawns standing on the block beneath the spawn point, so the ward that matters is the
     * one on that ground block - a floor warded against hostiles keeps them off itself, which is
     * what building a peaceful patch should mean. Only a natural spawn is refused; a spawner or a
     * spawn egg is the player's own doing and is left alone. The result is set to DENY only when the
     * ward actually covers this kind of mob, so every other spawn is untouched.
     *
     * <p>
     * The spawner test is new, and says outright what the other edition got for nothing: 1.7.10 does
     * not ask this event about a spawner at all, and 1.12.2 does, with a flag to tell them apart. Left
     * unasked, a warded floor under a dungeon's spawner would have starved it.
     */
    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!TrmtConfig.wardEnabled || event.isSpawner()) return;
        World world = event.getWorld();
        if (world == null || world.isRemote) return;
        EntityLivingBase living = event.getEntityLiving();
        if (living == null) return;

        boolean hostile = living instanceof IMob;
        boolean passive = living instanceof EntityAnimal;
        if (!hostile && !passive) return;

        int gx = MathHelper.floor(event.getX());
        int gy = MathHelper.floor(event.getY()) - 1;
        int gz = MathHelper.floor(event.getZ());
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, gx, gy, gz);
        if (entry == null) return;

        if ((hostile && entry.wardsHostile()) || (passive && entry.wardsPassive())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * Detonate rather than Start, because Start can still be cancelled and ground should only
     * be scoured by an explosion that actually happened.
     */
    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;
        Explosion explosion = event.getExplosion();
        float size = ExplosionSize.of(explosion);
        // Before the wear switch rather than behind it, and before the list is read below, so a
        // spared block is neither destroyed nor counted as ground about to be.
        spareReinforced(event, size);
        if (!TrmtConfig.enabled || !TrmtConfig.explosionWear || size <= 0f) return;
        // Gathered here because this event is the one moment the list exists: Forge fires it with
        // every affected block still standing and about to be taken. Scouring ground that is air a
        // moment later spends a chunk's entry budget on positions nobody will ever see.
        java.util.Set<Long> doomed = new java.util.HashSet<Long>();
        for (BlockPos at : event.getAffectedBlocks()) {
            doomed.add(Long.valueOf(ErosionEngine.packPosition(at.getX(), at.getY(), at.getZ())));
        }

        Vec3d centre = explosion.getPosition();
        ErosionEngine.get()
            .blast(world, centre.x, centre.y, centre.z, size, doomed);
    }

    /** Whether a spared block has been reported yet, so the log says it once a session. */
    private static boolean sparedReported;

    /**
     * Takes every reinforced block that withstands this blast out of the list it is about to destroy.
     *
     * <p>
     * The whole of what makes reinforcement blast-proof, and for as long as reinforcement existed in
     * the other edition it was missing there. The rule was written, both ceilings were configurable,
     * the tooltip said blast-proof and so did the guide, and nothing ever asked: a block reinforced
     * three times went up with the rest of the crater. The list this event hands over is the
     * explosion's own, filled in before the event fires and destroyed from afterwards, and the same list
     * is what the server sends a watching client to draw - so taking a position out of it here is the
     * whole of sparing it, on both sides, with nothing else to keep in step.
     *
     * <p>
     * Not behind the master switch or the blast-wear one. Both keep every record, reinforcement
     * included, and a block that stopped being blast-proof because somebody paused the paths would be
     * an irreversible edit nobody asked for. Only the reinforcement feature's own switch turns this
     * off, and that is asked inside {@link Reinforcement#survives}.
     */
    private static void spareReinforced(ExplosionEvent.Detonate event, float size) {
        if (!TrmtConfig.reinforceEnabled || size <= 0f) return;
        java.util.List<BlockPos> affected = event.getAffectedBlocks();
        if (affected == null || affected.isEmpty()) return;
        World world = event.getWorld();
        int spared = 0;
        for (java.util.Iterator<BlockPos> it = affected.iterator(); it.hasNext();) {
            BlockPos at = it.next();
            if (!Reinforcement.survives(world, at.getX(), at.getY(), at.getZ(), size)) continue;
            it.remove();
            spared++;
        }
        if (spared > 0 && !sparedReported) {
            sparedReported = true;
            Trmt.LOG.info(
                "A blast of size {} left {} reinforced block(s) standing; reinforcement is holding",
                Float.valueOf(size),
                Integer.valueOf(spared));
        }
    }

    /** The tool ladder: crafting one of the three tampers is worth an advancement. */
    @SubscribeEvent
    public void onItemCrafted(net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemCraftedEvent event) {
        com.trmtgtnh.item.ModAchievements.onCrafted(event.player, event.crafting);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        SpawnGrants.onLogin(event.player);
        tellAboutQuests(event.player);
    }

    /**
     * Mentions the quest chapter this mod has written, once, to somebody who can load it.
     *
     * <p>
     * Writing the file is useless on its own: the questbook only reads that folder when a world is
     * made or when the command is run, and running it is not this mod's decision to make. So the only
     * thing left is to say the line is there, and only to a player who has the permission to do
     * something about it - which on a server is the admin and in single player is everybody.
     */
    private void tellAboutQuests(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null) return;
        // The questbook's own rule for who may run the command, rather than a stricter one of this
        // mod's invention: a pack that has opened its admin commands to everybody has decided that
        // everybody can act on this, and telling nobody in that case helps no one.
        if (!player.canUseCommand(2, "bq_admin") && !com.trmtgtnh.compat.QuestbookCompat.bqAdminUnrestricted()) {
            return;
        }
        if (!com.trmtgtnh.compat.QuestbookCompat.shouldTell(player.getName())) return;
        player.sendMessage(
            new net.minecraft.util.text.TextComponentString(
                net.minecraft.util.text.TextFormatting.GRAY + "["
                    + com.trmtgtnh.Trmt.NAME
                    + "] "
                    + net.minecraft.util.text.TextFormatting.WHITE
                    + "This world's questbook has no chapter for this mod yet. When you have a moment, run "
                    + net.minecraft.util.text.TextFormatting.AQUA
                    + "/bq_admin default load"
                    + net.minecraft.util.text.TextFormatting.WHITE
                    + " - quest progress is kept, though a quest edited inside the book and never exported is replaced."));
    }
}
