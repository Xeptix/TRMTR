package com.trmtgtnh.server;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.erosion.Reinforcement;
import com.trmtgtnh.item.ModPotions;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.util.MainThread;

/**
 * What the game's own events make this mod do, with no event in sight.
 *
 * <p>
 * This is what drives everything else: the footfalls that wear ground, the tick the sweep runs on,
 * and the news that a block the store was tracking has gone. Both older editions keep it as a class
 * of annotated handlers, and the 1.12.2 one's own javadoc says it was written rather than carried
 * because it names something from nearly every milestone. The same is true here, and there is a
 * second reason besides.
 *
 * <p>
 * <strong>There is no event here at all.</strong> Not one argument names an event class, and that is
 * the whole shape of this file: the two loaders agree on almost nothing about how an event reaches a
 * mod. Forge has one of these as an annotated method; Fabric has an {@code Event} object for some of
 * them, nothing at all for others, and a mixin is the only way in. What they do agree on is what the
 * mod wants to know - that something walked, that a block was broken, that a blast went off - so that
 * is what this class takes, as plain game types, and each loader's module is left with the job of
 * noticing.
 *
 * <p>
 * The reasoning in each method is the other editions', because the reasoning is the part worth
 * having. What changed is said where it changed.
 *
 * <h2>What is not here yet</h2>
 *
 * <p>
 * Three handlers from the 1.12.2 edition are missing, each waiting on a milestone rather than on a
 * decision: crafting an advancement needs the achievements, a first join needs the spawn grants and
 * the items they hand out, and the quest-book notice needs the compat layer. Named in
 * {@code PortProgressTest}.
 */
public final class ServerEvents {

    private ServerEvents() {}

    /**
     * Movement is sampled rather than read every tick, and this is the count it is sampled against.
     *
     * <p>
     * Wear is credited per block entered, not per tick spent standing on one, so sampling costs
     * nothing in accuracy until a sprinting player crosses two block boundaries inside the window.
     */
    private static int sampleCounter;

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    /** A player has finished a tick on the server. */
    public static void playerTicked(Player player) {
        if (!(player instanceof ServerPlayer)) return;
        if (!dueThisTick()) return;

        float multiplier = ErosionEngine.multiplierFor((ServerPlayer) player);
        if (multiplier > 0f) ErosionEngine.get()
            .onMoverTick((ServerPlayer) player, multiplier);
    }

    /**
     * Everything else that walks.
     *
     * <p>
     * The lead switch and the mob list are two ways in, and {@code multiplierFor} asks each of them
     * properly. Returning on the lead switch alone shut the list off with it, so villagers stopped
     * wearing paths the moment led animals were told not to. Skipped only when neither could answer,
     * which keeps this costing nothing on a pack that uses neither.
     */
    public static void livingTicked(LivingEntity entity) {
        if (!TrmtConfig.erodeFromLeashedMobs && TrmtConfig.mobMultipliers.isEmpty()) return;
        if (entity == null || entity.level == null || entity.level.isClientSide()) return;
        if (entity instanceof Player) return; // handled above
        if (!dueThisTick()) return;

        float multiplier = ErosionEngine.multiplierFor(entity);
        if (multiplier > 0f) ErosionEngine.get()
            .onMoverTick(entity, multiplier);
    }

    private static boolean dueThisTick() {
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
    public static void fell(LivingEntity entity, float distance) {
        if (!TrmtConfig.enabled || !TrmtConfig.fallWear) return;
        if (entity == null || entity.level == null || entity.level.isClientSide()) return;

        float multiplier = ErosionEngine.multiplierFor(entity);
        if (multiplier <= 0f) return;

        if (ModPotions.treadsLightly(entity, entity.getVehicle())) return;
        multiplier *= ModPotions.wearFactor(entity, entity.getVehicle());

        ErosionEngine.get()
            .impact(entity.level, entity.getX(), entity.getY() - 1.0D, entity.getZ(), distance, multiplier);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    /** The server has finished a tick. */
    public static void serverTicked() {
        rebuildRecipesOnce();
        sampleCounter++;
        MainThread.drainServer();
        if ((sampleCounter % 20) == 0) advanceHealClock();
        ErosionEngine.get()
            .onServerTick();
        ErosionEngine.get()
            .sweepOrphans();
    }

    /** Whether this session's recipes have been rebuilt since the server started. */
    private static boolean recipesRebuilt;

    /**
     * Builds this mod's recipes again, once, on the first tick of a server.
     *
     * <p>
     * <strong>Because every other moment is too early, and that cost this edition every one of its
     * tamper recipes.</strong> A recipe here is built from what the pack contains, and that is read
     * from tags: {@code ingotIron} means {@code forge:ingots/iron}. The mixin that adds them injects
     * at the tail of {@code RecipeManager.apply}, where the tags of this reload are not bound yet -
     * measured, not guessed: the same question asked there reads nought items under
     * {@code ingotIron} and, with the world open, one. So every grade reported that the pack had no
     * metal for it, nought tamper recipes were built, and the mod said so in a line written for a
     * pack that genuinely has no metals.
     *
     * <p>
     * {@code serverStarted} is too early as well, and says so loudly rather than quietly: an
     * ingredient built from a tag there throws {@code IllegalStateException: Unrecognized tag},
     * because the collection the serializer uses is still being swapped in. The first tick is after
     * all of it, on both loaders, and is before any player can have joined - so the recipe book a
     * client is sent is the right one and nobody sees the wrong one.
     */
    private static void rebuildRecipesOnce() {
        if (recipesRebuilt) return;
        net.minecraft.server.MinecraftServer server = Trmt.runningServer();
        if (server == null) return;
        // Waited for rather than timed. Every moment that looked right was wrong - the tail of the
        // reload reads nought items under ingotIron, and both serverStarted and the first tick throw
        // "Unrecognized tag" outright from the ingredient, because the collection the serializer
        // uses is still being swapped in. So this asks on each tick whether the tags have turned up
        // and does the work on the tick they have, which needs no knowledge of when that is.
        if (!com.trmtgtnh.util.OreNames.tagsArrived()) return;
        recipesRebuilt = true;
        if (server.getRecipeManager() instanceof com.trmtgtnh.item.ExtraRecipes.Rebuildable) {
            ((com.trmtgtnh.item.ExtraRecipes.Rebuildable) server.getRecipeManager()).trmt$rebuildTrmtRecipes();
        }
    }

    /**
     * Holds the healing clock still while the server is empty.
     *
     * <p>
     * Once a second is enough: it measures whole seconds of in-game time, and what it feeds is read
     * against stamps that are themselves in seconds. Everything hangs off the overworld, because a
     * saved record is per-save rather than per-dimension.
     *
     * <p>
     * Golems are untouched by this and that is the point of it. They work from their own ticks in
     * whatever chunks are kept loaded, so a golem tending a road overnight goes on tending it - what
     * stops is the clock that would otherwise have undone the work before anybody saw it.
     */
    private static void advanceHealClock() {
        MinecraftServer server = Trmt.runningServer();
        if (server == null) return;
        // The overworld, which this version names rather than finding at index nought.
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        HealClockData clock = HealClockData.get(overworld);
        if (clock == null) return;
        // Switching the setting off stops the clock falling further behind; it does not hand back
        // the time already held out. Zeroing the total would age every road on the server by
        // however long it had ever sat empty, the instant somebody flipped a switch, and nothing
        // about that would look like the setting they had just changed.
        boolean counts = server.getPlayerCount() > 0 || !TrmtConfig.pauseHealingWhenEmpty;
        clock.advance(overworld.getGameTime() / 20L, counts);
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
     * future, healing nothing at all for as long again.
     *
     * <p>
     * Each loader is asked to call this as early as it can be called for that level, and before
     * anything else of this mod's can ask for a chunk in it.
     */
    public static void levelLoaded(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        HealClockData clock = HealClockData.get(level);
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
    public static void chunkWatched(ServerPlayer player, int chunkX, int chunkZ) {
        if (!TrmtConfig.enabled) return;
        if (player == null || player.level == null) return;
        if (!TrmtNetwork.isSubscribed(player)) return;

        ChunkErosionData data = ErosionStore.get()
            .getChunk(
                ErosionStore.get()
                    .indexOf(player.level),
                chunkX,
                chunkZ);
        if (data == null || data.isEmpty()) return;
        TrmtNetwork.sendChunkTo(player, chunkX, chunkZ, data);
    }

    /**
     * A server is starting, and must work out what erodes before anything asks it.
     *
     * <p>
     * <strong>Without this, nothing on this edition erodes at all.</strong> Detection was reached
     * from two places only: the texture stitcher, which is client-only, and {@code /trmt reload}. So
     * a client worked out its own table while building wear sprites and a server never worked out
     * one at all - and in single player the client then adopts the server's table on joining, which
     * replaced a good table with an empty one. The symptom is a world where no square ever wears and
     * {@code /trmt demonstrate} answers "Nothing is detected as erodable". On a dedicated server
     * nobody would have seen anything wear, ever.
     *
     * <p>
     * It went unseen because every test and every probe run drives a client, and a client resolves
     * on its way to stitching an atlas. The 1.12.2 edition resolves from its own mod lifecycle, in
     * {@code init} and again in {@code postInit}, which is both sides by construction; this edition
     * has no such lifecycle to borrow, so the moment is here - before the world loads, after every
     * registry is frozen, on whichever side is starting a server.
     *
     * <p>
     * The chain is the one {@code /trmt reload} runs and in the same order, minus the parts that
     * belong to a reload: what erodes, then which of it can sink. Tamper grades are decided from
     * tags and are rebuilt on the first tick, which is late enough to be their own problem.
     */
    public static void serverStarting(net.minecraft.server.MinecraftServer server) {
        // Both loaders say this on the server's own thread, as it starts. See Trmt.serverThreadAlive.
        Trmt.serverThreadIs(Thread.currentThread());
        com.trmtgtnh.surface.SurfaceRegistry.resolve();
        com.trmtgtnh.erosion.PhysicalDecay.markSinkableBlocks();
        UpdateNotice.serverStarting();
    }

    /**
     * A server has started, which is when the two written integrations do their writing.
     *
     * <p>
     * Both offer another mod a file in a folder that mod already reads - see {@code TrophyCompat}
     * and {@code QuestbookCompat}, neither of which names a class of the mod it is for. Here rather
     * than at the end of loading, which is where the 1.12.2 edition puts them: what they describe
     * includes which tamper grades this pack can make, and that is decided by tags, which are not
     * loaded until a server starts.
     *
     * <p>
     * Examining the world is the other half and has always been at this moment, because the answer
     * is about the save and the save is not there to be read any earlier.
     */
    public static void serverStarted(net.minecraft.server.MinecraftServer server) {
        // The recipes want rebuilding once the tags are bound, and this is not yet that moment - a
        // tag ingredient built here throws "Unrecognized tag" outright, which is how far off it is.
        // So it is asked for on the first tick instead; see rebuildRecipesOnce.
        recipesRebuilt = false;

        com.trmtgtnh.compat.TrophyCompat.writeDefinitions();
        com.trmtgtnh.compat.QuestbookCompat.writeQuests();
        com.trmtgtnh.compat.QuestbookCompat.examineWorld(server);
    }

    /**
     * A server has stopped, and everything that was about that world goes with it.
     *
     * <p>
     * All of it is in-memory state keyed on a world that no longer exists: the wear of the chunks
     * that were loaded, the engine's record of who stood where, each chunk's wet-healing and snow
     * meters, and the work the server thread had queued for itself. A single-player client stops a
     * server every time it leaves a world and starts another for the next one, so anything kept here
     * is handed to the next world rather than being merely stale - which is how one save's roads
     * turn up in another.
     *
     * <p>
     * The pending work is the stopped server's own and only that. The client's queue is its own
     * business and is emptied in {@code ClientSide.leaveWorld}: in single player this runs while
     * that client is still there, and clearing its queue from here could discard a reload it had
     * asked for.
     */
    public static void serverStopped() {
        Trmt.serverThreadIs(null);
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

    /**
     * Something was crafted, which is how the tool ladder is awarded.
     *
     * <p>
     * The one trigger in {@code ModAchievements} that no loader called here: the method was carried,
     * the advancements were carried, and crafting a tamper awarded nothing. Forge fires an event for
     * it; Fabric has none, so its half is a mixin into the slot the result is taken from - which is
     * the same place vanilla itself hangs its own crafting triggers.
     */
    public static void crafted(Player player, ItemStack stack) {
        if (player == null || player.level == null || player.level.isClientSide()) return;
        com.trmtgtnh.item.ModAchievements.onCrafted(player, stack);
    }

    /**
     * A player has joined, and if it is their first time they are handed something to start with.
     *
     * <p>
     * Whether it is their first time is {@code SpawnGrants}' business rather than this one's, and it
     * is remembered on the player rather than on the world - so somebody who makes a second world is
     * not handed a second set.
     */
    public static void playerJoined(Player player) {
        SpawnGrants.onLogin(player);
        tellAboutQuests(player);
        UpdateNotice.onLogin(player);
    }

    /**
     * Tells somebody who can act on it that this world's questbook has no chapter for this mod.
     *
     * <p>
     * Carried from the other edition, where it is the one handler this edition was first ported
     * without - it wanted the compat layer, which has now landed.
     */
    private static void tellAboutQuests(Player player) {
        if (!(player instanceof ServerPlayer)) return;
        // The questbook's own rule for who may run the command, rather than a stricter one of this
        // mod's invention: a pack that has opened its admin commands to everybody has decided that
        // everybody can act on this, and telling nobody in that case helps no one.
        if (!player.hasPermissions(2) && !com.trmtgtnh.compat.QuestbookCompat.bqAdminUnrestricted()) {
            return;
        }
        if (!com.trmtgtnh.compat.QuestbookCompat.shouldTell(
            player.getGameProfile()
                .getName())) {
            return;
        }
        Notices.say(
            (ServerPlayer) player,
            Notices.line(
                "This world's questbook has no chapter for this mod yet. When you have a moment, run "
                    + "/bq_admin default load - quest progress is kept, though a quest edited inside the "
                    + "book and never exported is replaced.",
                net.minecraft.ChatFormatting.GRAY,
                true));
    }

    /** A player has left, and whatever was remembered about what they wanted goes with them. */
    public static void playerLeft(Player player) {
        if (player instanceof ServerPlayer) TrmtNetwork.forget((ServerPlayer) player);
        UpdateNotice.onLogout(player);
    }

    // ------------------------------------------------------------------
    // Blocks coming and going
    // ------------------------------------------------------------------

    /**
     * A broken block takes its wear with it. Without this the overlay would linger on whatever is
     * placed there next until the next healing pass noticed the mismatch.
     *
     * <p>
     * The state is handed in rather than read back out of the world: by the time this is called the
     * world may already hold air, and on one of the two loaders it certainly does.
     */
    public static void blockBroken(Level level, BlockPos pos, BlockState state) {
        if (level == null || level.isClientSide() || pos == null || state == null) return;
        // Set aside rather than dropped, in case the same block comes straight back.
        ErosionEngine.get()
            .breakBlock(level, pos.getX(), pos.getY(), pos.getZ(), state.getBlock(), 0);
    }

    /**
     * Block ids moved under the surface table, so it is rebuilt under the ids now in force.
     *
     * <p>
     * The table is keyed by a block's number, and a number is only good for the registry it was read
     * under: a world from another mod list, or a server with its own history, hands out different ones.
     * Built at start-up and never again, it went on looking up the wrong blocks until a reload - the wrong
     * modded ground wore and real modded paths did not. The 1.7.10 edition's rebuild; this edition heard no
     * such move on either loader until 0.9.219. Forge says so with {@code FMLModIdMappingEvent}, Fabric's
     * registry sync with its remap callback, and both land here.
     *
     * <p>
     * A client takes it on its own terms - on its own thread, with the atlas audit after it; a server with no
     * client in it rebuilds where it stands. {@link SurfaceRegistry#resolve} re-stamps the sinkable and
     * settling blocks as it goes.
     */
    public static void idsMoved() {
        if (com.trmtgtnh.Client.idsMoved()) return;
        Trmt.LOG.info("Block ids changed; rebuilding the surface table under them");
        com.trmtgtnh.surface.SurfaceRegistry.resolve();
    }

    /**
     * A block was placed, by {@code player} or by nobody in particular.
     *
     * <p>
     * The same block back in the same spot inside the window gets its record back, and a player head
     * set on the right shape stands a Golem of Ways up - the 1.7.10 edition's two handlers of the one
     * event, here one call because each loader hands over the one placement. The second was missing
     * until 0.9.219: the builder was ported whole and nothing called it, so a golem could be had from a
     * loot egg or the demonstrate yard and never from the blocks and a head, which is how the guide
     * says to make one. Any skull here; whether it is a player's on the ground is the builder's
     * question, as it is there.
     */
    public static void blockPlaced(Level level, BlockPos pos, BlockState state, Player player) {
        if (level == null || level.isClientSide() || pos == null || state == null) return;
        ErosionEngine.get()
            .placeBlock(level, pos.getX(), pos.getY(), pos.getZ(), state.getBlock(), 0);
        if (state.getBlock() instanceof net.minecraft.world.level.block.AbstractSkullBlock) {
            com.trmtgtnh.entity.GolemBuilder.onHeadPlaced(level, pos.getX(), pos.getY(), pos.getZ(), player);
        }
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * Whether a warded block bars this mob from spawning here.
     *
     * <p>
     * A mob spawns standing on the block beneath the spawn point, so the ward that matters is the
     * one on that ground block - a floor warded against hostiles keeps them off itself, which is
     * what building a peaceful patch should mean. Only a natural spawn is refused; a spawner or a
     * spawn egg is the player's own doing and is left alone.
     *
     * <p>
     * Answered rather than acted on, because the two loaders cancel a spawn in entirely different
     * ways and neither of them is this class's business.
     *
     * @return true when this spawn should be refused
     */
    public static boolean spawnBarred(Level level, LivingEntity living, double x, double y, double z,
        boolean fromSpawner) {
        if (!TrmtConfig.wardEnabled || fromSpawner) return false;
        if (level == null || level.isClientSide() || living == null) return false;

        boolean hostile = living instanceof Enemy;
        boolean passive = living instanceof Animal;
        if (!hostile && !passive) return false;

        int gx = Mth.floor(x);
        int gy = Mth.floor(y) - 1;
        int gz = Mth.floor(z);
        ErosionEntry entry = ErosionStore.get()
            .getEntry(level, gx, gy, gz);
        if (entry == null) return false;

        return (hostile && entry.wardsHostile()) || (passive && entry.wardsPassive());
    }

    // ------------------------------------------------------------------
    // Explosions
    // ------------------------------------------------------------------

    /** Whether a spared block has been reported yet, so the log says it once a session. */
    private static boolean sparedReported;

    /**
     * A blast that has actually happened, with the list of what it is about to take.
     *
     * <p>
     * After the blast is settled rather than before, because a blast that can still be cancelled
     * should not scour anything. The list is the explosion's own, filled in before this is called and
     * destroyed from afterwards, and the same list is what the server sends a watching client to draw
     * - so taking a position out of it here is the whole of sparing it, on both sides, with nothing
     * else to keep in step.
     *
     * <p>
     * The centre comes in as three numbers rather than off the explosion, and that is the one place
     * this file pays for having two loaders. One of them patches a getter onto the explosion and the
     * other does not; both hooks know where the blast was, so both can say.
     *
     * @param affected the blast's own list, which this removes spared blocks from in place
     */
    public static void exploded(Level level, double centreX, double centreY, double centreZ, float size,
        List<BlockPos> affected) {
        if (level == null || level.isClientSide()) return;
        // Before the wear switch rather than behind it, and before the list is read below, so a
        // spared block is neither destroyed nor counted as ground about to be.
        spareReinforced(level, affected, size);
        if (!TrmtConfig.enabled || !TrmtConfig.explosionWear || size <= 0f) return;
        // Gathered here because this is the one moment the list exists: it holds every affected block
        // still standing and about to be taken. Scouring ground that is air a moment later spends a
        // chunk's entry budget on positions nobody will ever see.
        Set<Long> doomed = new HashSet<Long>();
        if (affected != null) {
            for (BlockPos at : affected) {
                doomed.add(Long.valueOf(ErosionEngine.packPosition(at.getX(), at.getY(), at.getZ())));
            }
        }

        ErosionEngine.get()
            .blast(level, centreX, centreY, centreZ, size, doomed);
    }

    /**
     * Takes every reinforced block that withstands this blast out of the list it is about to destroy.
     *
     * <p>
     * The whole of what makes reinforcement blast-proof, and for as long as reinforcement existed in
     * the 1.7.10 edition it was missing there. The rule was written, both ceilings were configurable,
     * the tooltip said blast-proof and so did the guide, and nothing ever asked: a block reinforced
     * three times went up with the rest of the crater.
     *
     * <p>
     * Not behind the master switch or the blast-wear one. Both keep every record, reinforcement
     * included, and a block that stopped being blast-proof because somebody paused the paths would be
     * an irreversible edit nobody asked for. Only the reinforcement feature's own switch turns this
     * off, and that is asked inside {@link Reinforcement#survives}.
     */
    private static void spareReinforced(Level level, List<BlockPos> affected, float size) {
        if (!TrmtConfig.reinforceEnabled || size <= 0f) return;
        if (affected == null || affected.isEmpty()) return;
        int spared = 0;
        for (Iterator<BlockPos> it = affected.iterator(); it.hasNext();) {
            BlockPos at = it.next();
            if (!Reinforcement.survives(level, at.getX(), at.getY(), at.getZ(), size)) continue;
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
}
