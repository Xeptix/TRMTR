package com.trmtgtnh.server;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.erosion.MendLedger;
import com.trmtgtnh.erosion.Reinforcement;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.util.MainThread;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Wires the erosion engine to the Forge event bus.
 *
 * <p>
 * Upstream does all of this with mixins into {@code ServerPlayerEntity},
 * {@code MobEntity}, {@code BoneMealItem} and friends. None of that is needed here: every
 * hook upstream mixes in for has a Forge event in 1.7.10, and upstream's remaining mixins
 * exist only to intercept interactions with the eroded <em>blocks</em> it places, which this
 * backport never places. That leaves the mod with no bytecode injection at all, which is the
 * single biggest thing it can do for compatibility in a 235-mod pack.
 */
public final class ServerEvents {

    private static final ServerEvents INSTANCE = new ServerEvents();

    private int sampleCounter;

    private ServerEvents() {}

    public static ServerEvents get() {
        return INSTANCE;
    }

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

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        // The lead switch and the mob list are two ways in, and multiplierFor asks each of them
        // properly. Returning here on the lead switch alone shut the list off with it, so villagers
        // stopped wearing paths the moment led animals were told not to. Skipped only when neither
        // could answer, which keeps this event costing nothing on a pack that uses neither.
        if (!TrmtConfig.erodeFromLeashedMobs && TrmtConfig.mobMultipliers.isEmpty()) return;
        EntityLivingBase entity = event.entityLiving;
        if (entity == null || entity.worldObj == null || entity.worldObj.isRemote) return;
        if (entity instanceof net.minecraft.entity.player.EntityPlayer) return; // handled above
        if (!dueThisTick()) return;

        float multiplier = ErosionEngine.multiplierFor(entity);
        if (multiplier > 0f) ErosionEngine.get()
            .onMoverTick(entity, multiplier);
    }

    /**
     * Movement is sampled rather than read every tick. Wear is credited per block entered,
     * not per tick spent standing on one, so sampling costs nothing in accuracy until a
     * sprinting player crosses two block boundaries inside the sample window.
     */
    private boolean dueThisTick() {
        return TrmtConfig.movementSampleTicks <= 1 || (sampleCounter % TrmtConfig.movementSampleTicks) == 0;
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
        // The update notice's round every eight hours (0.9.221), asked once a second.
        if ((sampleCounter % 20) == 0) com.trmtgtnh.server.UpdateNotice.serverTick();
    }

    /**
     * Holds the healing clock still while the server is empty.
     *
     * <p>
     * Once a second is enough: it is measuring whole seconds of in-game time, and the thing it
     * feeds is read against stamps that are themselves in seconds. Everything is hung off the
     * overworld, because map storage is per-save rather than per-dimension and the total world
     * time every dimension answers with is the overworld's anyway.
     *
     * <p>
     * Golems are untouched by this and that is the point of it. They work from their own ticks in
     * whatever chunks are kept loaded, so a golem tending a road overnight goes on tending it -
     * what stops is the clock that would otherwise have undone the work before anybody saw it.
     */
    private void advanceHealClock() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null || server.worldServers.length == 0) return;
        World overworld = server.worldServers[0];
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

    // ------------------------------------------------------------------
    // Client sync
    // ------------------------------------------------------------------

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
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onWorldLoad(net.minecraftforge.event.world.WorldEvent.Load event) {
        World world = event.world;
        if (world == null || world.isRemote || world.provider == null || world.provider.dimensionId != 0) return;
        HealClockData clock = HealClockData.get(world);
        if (clock != null) ErosionEngine.setHealPause((int) clock.idleSeconds());
    }

    @SubscribeEvent
    public void onChunkWatch(ChunkWatchEvent.Watch event) {
        if (!TrmtConfig.enabled) return;
        final EntityPlayerMP player = event.player;
        if (player == null || player.worldObj == null) return;
        if (!TrmtNetwork.isSubscribed(player)) return;

        final int dimension = player.worldObj.provider.dimensionId;
        final int chunkX = event.chunk.chunkXPos;
        final int chunkZ = event.chunk.chunkZPos;

        ChunkErosionData data = ErosionStore.get()
            .getChunk(dimension, chunkX, chunkZ);
        if (data == null || data.isEmpty()) return;
        TrmtNetwork.sendChunkTo(player, chunkX, chunkZ, data);
    }

    /** The tool ladder: crafting one of the three tampers is worth an achievement. */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        com.trmtgtnh.item.ModAchievements.onCrafted(event.player, event.crafting);
    }

    /** Anything a player is owed and has never had in this save is handed over here. */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        SpawnGrants.onLogin(event.player);
        tellAboutQuests(event.player);
        if (event.player instanceof EntityPlayerMP) UpdateNotice.onLogin((EntityPlayerMP) event.player);
    }

    /**
     * Mentions the quest line, once, to somebody who could actually load it.
     *
     * <p>
     * Writing the file is useless on its own: BetterQuesting only reads that folder when a world is
     * made or when the command is run, and running it is not the mod's decision to make. So the
     * only thing left is to say the line is there, and only to a player who has the permission to
     * do something about it - which on a server is an operator, and in single player is the owner when
     * the world allows cheats. Vanilla makes the single-player owner an operator only then
     * ({@code ServerConfigurationManager.func_152596_g}), which is the same rule the command itself is
     * held to: a world without cheats, where nobody can run it, is never told to.
     */
    private void tellAboutQuests(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null) return;
        // The questbook's own rule for who may run the command, rather than a stricter one of this
        // mod's invention: a pack that has opened its admin commands to everybody has decided that
        // everybody can act on this, and telling nobody in that case helps no one.
        if (!player.canCommandSenderUseCommand(2, "bq_admin")
            && !com.trmtgtnh.compat.QuestbookCompat.bqAdminUnrestricted()) {
            return;
        }
        if (!com.trmtgtnh.compat.QuestbookCompat.shouldTell(player.getCommandSenderName())) return;
        player.addChatMessage(
            new net.minecraft.util.ChatComponentText(
                net.minecraft.util.EnumChatFormatting.GRAY + "["
                    + com.trmtgtnh.Trmt.NAME
                    + "] "
                    + net.minecraft.util.EnumChatFormatting.WHITE
                    + "This world's questbook has no chapter for this mod yet. When you have a moment, run "
                    + net.minecraft.util.EnumChatFormatting.AQUA
                    + "/bq_admin default load"
                    + net.minecraft.util.EnumChatFormatting.WHITE
                    + " - quest progress is kept, though a quest edited inside the book and never exported is replaced."));
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) TrmtNetwork.forget((EntityPlayerMP) event.player);
        if (event.player instanceof EntityPlayerMP) UpdateNotice.onLogout((EntityPlayerMP) event.player);
    }

    // ------------------------------------------------------------------
    // World edits by other means
    // ------------------------------------------------------------------

    /**
     * A broken block takes its wear with it. Without this the overlay would linger on
     * whatever is placed there next until the next healing pass noticed the mismatch.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        // Set aside rather than dropped, in case the same block comes straight back.
        ErosionEngine.get()
            .breakBlock(event.world, event.x, event.y, event.z, event.block, event.blockMetadata);
    }

    /**
     * Bars a category of mob from spawning on a warded block.
     *
     * <p>
     * A mob spawns standing on the block beneath the spawn point, so the ward that matters is the
     * one on that ground block - a floor warded against hostiles keeps them off itself, which is
     * what building a peaceful patch should mean. Only a natural spawn passes through here; a
     * spawner or a spawn egg is the player's own doing and is left alone. The result is set to
     * DENY only when the ward actually covers this kind of mob, so every other spawn is untouched.
     */
    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!TrmtConfig.wardEnabled) return;
        World world = event.world;
        if (world == null || world.isRemote) return;
        EntityLivingBase living = event.entityLiving;
        if (living == null) return;

        boolean hostile = living instanceof IMob;
        boolean passive = living instanceof EntityAnimal;
        if (!hostile && !passive) return;

        int gx = MathHelper.floor_double(event.x);
        int gy = MathHelper.floor_double(event.y) - 1;
        int gz = MathHelper.floor_double(event.z);
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, gx, gy, gz);
        if (entry == null) return;

        if ((hostile && entry.wardsHostile()) || (passive && entry.wardsPassive())) {
            event.setResult(cpw.mods.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    /** A player head set on the right shape stands a Golem of Ways up. */
    @SubscribeEvent
    public void onHeadPlaced(BlockEvent.PlaceEvent event) {
        if (event.block == net.minecraft.init.Blocks.skull) {
            com.trmtgtnh.entity.GolemBuilder.onHeadPlaced(event.world, event.x, event.y, event.z, event.player);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        // The same block back in the same spot inside the window gets its record back.
        ErosionEngine.get()
            .placeBlock(event.world, event.x, event.y, event.z, event.block, event.blockMetadata);
    }

    /**
     * Bone meal repairs a worn patch of ground.
     *
     * <p>
     * The patch itself is {@code ErosionEngine.mendPatch}, which the tamper's right-click
     * gesture also uses. Bone meal and a tamper differ over how far the patch reaches and over
     * what a handful costs, and over nothing else worth writing twice.
     *
     * <p>
     * The bone meal is only consumed if something was actually repaired, so it still behaves
     * normally on crops and saplings standing on worn ground.
     *
     * <p>
     * When the ground costs material as well, the payment is found before anything is mended and
     * taken only once something has been: a repair cannot be handed back if it turns out there is
     * nothing to pay with. A player who cannot pay is left exactly where they were, with the bone
     * meal still in hand and free to grow whatever it always grew.
     */
    /**
     * What lands hard marks where it landed.
     *
     * <p>
     * Weighted by the same figure walking uses, so whatever is allowed to wear a path is
     * allowed to leave a landing mark and nothing else is - a player scuffs the ground, a
     * falling zombie does not, and both answers come from one setting rather than two.
     */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (!TrmtConfig.enabled || !TrmtConfig.fallWear) return;
        EntityLivingBase entity = event.entityLiving;
        if (entity == null || entity.worldObj == null || entity.worldObj.isRemote) return;

        float multiplier = ErosionEngine.multiplierFor(entity);
        if (multiplier <= 0f) return;

        // The landing half of the two draughts. Walking is guarded inside the engine, where the
        // record of the last block stood on lives; a fall reaches the ground by a different road
        // and has to be guarded on it, or somebody light on their feet would leave no track and
        // still crater on arrival. Scaled here rather than inside impact, which already quotes
        // everything against this figure - so four times the mark needs no second formula.
        if (com.trmtgtnh.item.ModPotions.treadsLightly(entity, entity.ridingEntity)) return;
        multiplier *= com.trmtgtnh.item.ModPotions.wearFactor(entity, entity.ridingEntity);

        ErosionEngine.get()
            .impact(entity.worldObj, entity.posX, entity.posY - 1.0D, entity.posZ, event.distance, multiplier);
    }

    /**
     * Detonate rather than Start, because Start can still be cancelled and ground should only
     * be scoured by an explosion that actually happened.
     */
    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.world;
        if (world == null || world.isRemote) return;
        // Before the wear switch rather than behind it, and before the list is read below, so a
        // spared block is neither destroyed nor counted as ground about to be.
        spareReinforced(world, event);
        if (!TrmtConfig.enabled || !TrmtConfig.explosionWear) return;
        // Gathered here because this event is the one moment the list exists: Forge fires it
        // from doExplosionA, with every affected block still standing and about to be taken by
        // doExplosionB. Scouring ground that is air a moment later spends a chunk's entry
        // budget on positions nobody will ever see.
        java.util.Set<Long> doomed = new java.util.HashSet<Long>();
        java.util.List<?> affected = event.getAffectedBlocks();
        if (affected != null) {
            for (Object entry : affected) {
                if (!(entry instanceof net.minecraft.world.ChunkPosition)) continue;
                net.minecraft.world.ChunkPosition at = (net.minecraft.world.ChunkPosition) entry;
                doomed.add(Long.valueOf(ErosionEngine.packPosition(at.chunkPosX, at.chunkPosY, at.chunkPosZ)));
            }
        }

        ErosionEngine.get()
            .blast(
                world,
                event.explosion.explosionX,
                event.explosion.explosionY,
                event.explosion.explosionZ,
                event.explosion.explosionSize,
                doomed);
    }

    /** Whether a spared block has been reported yet, so the log says it once a session. */
    private static boolean sparedReported;

    /**
     * Takes every reinforced block that withstands this blast out of the list it is about to destroy.
     *
     * <p>
     * The whole of what makes reinforcement blast-proof, and for as long as reinforcement has
     * existed it was missing. The rule was written, both ceilings were configurable, the tooltip
     * said blast-proof and so did the guide, and nothing ever asked: a block reinforced three times
     * went up with the rest of the crater. The list this event hands over is the explosion's own,
     * filled in before the event fires and destroyed from afterwards, and the same list is what the
     * server sends a watching client to draw - so taking a position out of it here is the whole of
     * sparing it, on both sides, with nothing else to keep in step.
     *
     * <p>
     * Not behind the master switch or the blast-wear one. Both keep every record, reinforcement
     * included, and a block that stopped being blast-proof because somebody paused the paths would
     * be an irreversible edit nobody asked for. Only the reinforcement feature's own switch turns
     * this off, and that is asked inside {@link Reinforcement#survives}.
     */
    private static void spareReinforced(World world, ExplosionEvent.Detonate event) {
        if (!TrmtConfig.reinforceEnabled || event.explosion == null) return;
        java.util.List<?> affected = event.getAffectedBlocks();
        if (affected == null || affected.isEmpty()) return;
        float size = event.explosion.explosionSize;
        int spared = 0;
        for (java.util.Iterator<?> it = affected.iterator(); it.hasNext();) {
            Object entry = it.next();
            if (!(entry instanceof net.minecraft.world.ChunkPosition)) continue;
            net.minecraft.world.ChunkPosition at = (net.minecraft.world.ChunkPosition) entry;
            if (!Reinforcement.survives(world, at.chunkPosX, at.chunkPosY, at.chunkPosZ, size)) continue;
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

    /**
     * Bone meal on worn ground mends a scattered patch of it.
     *
     * <p>
     * Payment happens as the patch is walked. Each kind of ground pays from stock that square would
     * itself take, a block of it once for the handful, and the block is taken only once the square's
     * gradation has gone back - so a handful across a grass verge and a cobble road costs a block of
     * each, ground nothing is carried for is left as it was, and a handful that mends nothing costs
     * nothing and goes on to grow whatever it always grew. A handful aimed at ground that does not
     * wear mends nothing, whatever the cost setting, or the cheapest way to mend a stone road would be
     * a handful thrown at the dirt beside it.
     *
     * <p>
     * Experience for a handful that cost no block is paid only when that handful was vanilla bone
     * meal really spent from a player's hand: the player right-clicked this very block holding bone
     * meal, on this tick, which vanilla announces before it uses the item. Holding bone meal was not
     * proof enough, because other mods post this event for a player holding it while spending their
     * own - a growth charm, a sigil - and at least three in the pack spend nothing when it is allowed.
     * Paying them while the block cost is off would make mending both free and worth experience,
     * which is the round trip that must never be a farm.
     * Creative earns nothing at all, since the game hands the bone meal back.
     *
     * <p>
     * A fake player needs no special case to pay. It pays from its own inventory like anyone, and a
     * machine growing crops through {@code ItemDye.func_150919_a} usually carries nothing, so with
     * the cost on it mends nothing and the bone meal grows whatever it always grew. It never earns
     * experience, and it is never told anything.
     */
    @SubscribeEvent
    public void onBonemeal(BonemealEvent event) {
        World world = event.world;
        if (world == null || world.isRemote || !TrmtConfig.enabled) return;
        EntityPlayer player = event.entityPlayer;

        SurfaceFamily aimed = SurfaceRegistry.familyOf(event.block, world.getBlockMetadata(event.x, event.y, event.z));
        if (aimed == null || !aimed.staged) return;

        // Creative pays nothing, and has to: the game puts back only the stack in the hand after a
        // right click, so a block taken from any other slot would be gone for good.
        boolean creative = player != null && player.capabilities.isCreativeMode;
        boolean fromHand = player != null && !(player instanceof FakePlayer)
            && isBoneMeal(player.getHeldItem())
            && clickedWithBoneMeal(player, world, event.x, event.y, event.z);
        MendLedger ledger = creative || !TrmtConfig.bonemealCostsABlock ? MendLedger.free() : MendLedger.perGesture(1);
        MendPurse purse = new MendPurse(player, ledger, MendPurse.byOwnBlock(player));
        // Asked before the patch is mended, because afterwards the answer is about what is left.
        boolean aimedWorn = ErosionEngine.get()
            .restorable(world, event.x, event.y, event.z);

        int mended = ErosionEngine.get()
            .mendPatch(world, event.x, event.y, event.z, TrmtConfig.bonemealRadius, purse.patchWork());
        if (mended <= 0) {
            // Quieter than the tools. A handful on unworn ground is somebody growing grass, and only
            // a worn square under the crosshair is a mend the player was actually trying to make.
            if (aimedWorn) purse.reportNothing(player, 0, true);
            return;
        }

        // Bone meal has nothing to repair, so every point of this reaches the player.
        if (!creative) {
            HealingXp
                .award(player, null, ledger.isFree() ? (fromHand ? ledger.gradations() : 0) : ledger.paidGradations());
        }
        purse.reportShort(player, true);

        event.setResult(cpw.mods.fml.common.eventhandler.Event.Result.ALLOW);
        // The green sparkle everything else gets for the same gesture. Without it the ground
        // simply changes and nobody is sure the bone meal did anything.
        // One block up. Played at the ground itself the particles spawn inside it and most of
        // them are never seen.
        world.playAuxSFX(2005, event.x, event.y + 1, event.z, 0);
    }

    /**
     * Where each player last right-clicked a block holding bone meal, and on which tick.
     *
     * <p>
     * What the bone meal event cannot say for itself: whether the handful is the one in the player's
     * hand. It carries no stack, and a mod that fires it for a player spends whatever it likes. Vanilla
     * announces the right click first, on the same tick and at the same block, and only then uses the
     * item, so a click remembered here and matched there is a handful really thrown. Weakly held, so a
     * player who leaves is not kept.
     */
    private final java.util.Map<EntityPlayer, long[]> boneMealClicks = new java.util.WeakHashMap<EntityPlayer, long[]>();

    @SubscribeEvent
    public void onRightClickHoldingBoneMeal(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        EntityPlayer player = event.entityPlayer;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (player instanceof FakePlayer || !isBoneMeal(player.getHeldItem())) return;
        boneMealClicks.put(player, new long[] { event.x, event.y, event.z, player.worldObj.getTotalWorldTime() });
    }

    /** Whether this player's last bone meal click was at this block on this tick. Forgotten once asked. */
    private boolean clickedWithBoneMeal(EntityPlayer player, World world, int x, int y, int z) {
        long[] click = boneMealClicks.remove(player);
        return click != null && click[0] == x
            && click[1] == y
            && click[2] == z
            && click[3] == world.getTotalWorldTime();
    }

    /**
     * Whether a stack is vanilla bone meal, which is dye at damage fifteen.
     *
     * <p>
     * Vanilla takes exactly the stack in hand when bone meal is allowed, so a handful of this in a
     * real player's hand is a handful spent. Anything else posting the event may have spent nothing.
     */
    private static boolean isBoneMeal(ItemStack stack) {
        return stack != null && stack.getItem() == Items.dye && stack.getItemDamage() == 15;
    }
}
