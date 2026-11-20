package com.trmtgtnh.item;

import java.util.WeakHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.server.ReinforceCost;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Reinforcing a block, and taking a reinforcement back off.
 *
 * <p>
 * Right-click adds one, paying a material - or, with the Wayfarer, sets the level its screen names and
 * pays only for a rise; two left-clicks take one away. The level lives in the
 * position's own erosion record - which is why a reinforced block need not be a worn one, and why
 * reinforcing an unworn block simply creates an invisible record that carries nothing but the
 * level. See {@link com.trmtgtnh.erosion.Reinforcement} for what the level then does to an
 * explosion.
 *
 * <p>
 * Server-side, but for {@link #clientClicked} and {@link #clientTicked}. The gestures are dispatched from {@link ItemChunkTamper},
 * which has already decided this is a reinforce click; here is only the rule.
 */
public final class ReinforceGestures {

    /** Where each player's un-reinforce is half-done: x, y, z, and the tick it was armed. */
    private static final WeakHashMap<Player, int[]> ARMED = new WeakHashMap<Player, int[]>();

    /** Two left-clicks at the same block within this many ticks take a reinforcement off. */
    private static final int ARM_WINDOW_TICKS = 40;

    /** Each player's press of the attack key as the server has it; see {@link Press}. */
    private static final WeakHashMap<Player, Press> PRESSES = new WeakHashMap<Player, Press>();

    /**
     * For a client that does not say where its presses begin (a build before this rule): a left-click on the same
     * block within this many server ticks of the player's last continues the same press.
     */
    static final int PRESS_GAP_TICKS = 2;

    /**
     * A left-click on the same block on the client's next tick after its press was last known held, or the same one,
     * is the attack key still held. The press is known held by its last click and by every client tick that ends with
     * the key down and the crosshair on a block ({@link #clientTicked}), not by its clicks alone: the game's own dig
     * delay sends no click for several ticks of a held key (RL19, 2026-11-09).
     */
    static final int HELD_CLIENT_TICKS = 1;

    /**
     * How far apart, in server ticks, a press's announcement and its left-click may be handled and still be one
     * press. The client writes the two in the same tick, one behind the other, so nothing but the server's own queue
     * stands between them: the loaders hand a message to the game's thread behind clicks that arrived after it, and
     * this mod hands it once more to the end of the tick. A second is far more than either takes.
     */
    static final int PAIR_TICKS = 20;

    /**
     * The client's press of the attack key in reinforce mode: x, y, z and the client's tick it was last known held, or
     * null when none is held. Client side only.
     */
    private static int[] clientLast;

    private ReinforceGestures() {}

    /**
     * Right-click: raises a block's reinforcement, for one material.
     *
     * <p>
     * The chunk tamper adds one level, up to the cap. The Wayfarer sets the level its screen names, up
     * or down, and pays only when that is a rise. Either earns the reinforcing achievement for a rise and
     * for nothing else: the reply that the block is already there, a refused payment, a lowering and a
     * clear earn nothing, as two left-clicks earn nothing.
     */
    public static boolean reinforce(Level world, int x, int y, int z, Player player, ItemStack stack) {
        if (world == null || world.isClientSide() || !TrmtConfig.reinforceEnabled) return false;
        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        if (block == null || com.trmtgtnh.util.Worlds.isAir(world, x, y, z)) return false;

        int level = level(world, x, y, z);
        int cap = Math.min(3, TrmtConfig.reinforceMaxLevel);
        boolean free = player != null && player.getAbilities().instabuild;

        // The Wayfarer sets a block straight to a chosen level, up or down, for one material -
        // where the chunk tamper only ever adds one and pays for each. That is the whole of its
        // reinforce bonus. Only an increase costs anything; dropping a level is free.
        boolean wayfarer = stack != null && stack.getItem() instanceof ItemMagicTamper;
        if (wayfarer) {
            int target = Math.max(0, Math.min(cap, ItemChunkTamper.reinforceLevelOf(stack)));
            if (target == level) {
                say(player, ChatFormatting.AQUA, com.trmtgtnh.util.Translate.get("trmtgtnh.reinforce.already"));
                return true;
            }
            // Asked once, because three things turn on it: only a rise is paid for, only a rise
            // earns the achievement, and a rise sounds higher than a drop.
            boolean raising = target > level;
            if (raising && !free && !ReinforceCost.pay(player)) {
                say(player, ChatFormatting.RED, com.trmtgtnh.util.Translate.get("trmtgtnh.reinforce.needs"));
                return false;
            }
            setLevel(world, x, y, z, target);
            // The chunk tamper's achievement, for the same thing: a block taken up a level. This branch
            // once returned without it, and since warding and lighting award on paths the Wayfarer
            // shares, reinforcing was the one mode the finest tamper could never earn.
            if (raising) ModAchievements.onReinforced(player);
            world.playSound(
                null,
                x + 0.5D,
                y + 0.5D,
                z + 0.5D,
                net.minecraft.sounds.SoundEvents.STONE_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS,
                0.8f,
                raising ? 1.4f : 0.8f);
            world.levelEvent(
                2001,
                new net.minecraft.core.BlockPos(x, y, z),
                Block.getId(com.trmtgtnh.util.Worlds.stateAt(world, x, y, z)));
            say(
                player,
                ChatFormatting.AQUA,
                target <= 0 ? com.trmtgtnh.util.Translate.get("trmtgtnh.reinforce.none")
                    : com.trmtgtnh.util.Translate.get(
                        "trmtgtnh.reinforce.now",
                        Integer.valueOf(target),
                        Integer.valueOf(cap)));
            return true;
        }

        if (level >= cap) {
            say(player, ChatFormatting.AQUA, com.trmtgtnh.util.Translate.get("trmtgtnh.reinforce.full"));
            return true;
        }

        if (!free && !ReinforceCost.pay(player)) {
            say(player, ChatFormatting.RED, com.trmtgtnh.util.Translate.get("trmtgtnh.reinforce.needs"));
            return false;
        }

        setLevel(world, x, y, z, level + 1);
        ModAchievements.onReinforced(player);
        world.playSound(
            null,
            x + 0.5D,
            y + 0.5D,
            z + 0.5D,
            net.minecraft.sounds.SoundEvents.STONE_BREAK,
            net.minecraft.sounds.SoundSource.BLOCKS,
            0.8f,
            1.4f);
        // A puff of block-breaking particles, which reads as "something set here" without a block
        // actually breaking.
        world.levelEvent(
            2001,
            new net.minecraft.core.BlockPos(x, y, z),
            Block.getId(com.trmtgtnh.util.Worlds.stateAt(world, x, y, z)));
        say(
            player,
            ChatFormatting.AQUA,
            com.trmtgtnh.util.Translate.get("trmtgtnh.reinforce.now", Integer.valueOf(level + 1), Integer.valueOf(cap)));
        return true;
    }

    /**
     * Left-click: takes a reinforcement off, but only on the second click at the same block.
     *
     * <p>
     * Armed on the first click and applied on the second, the same way a dangerous single-press is
     * made to take two - so a stray left-click while walking a wall does not quietly undo it.
     */
    public static boolean unreinforce(Level world, int x, int y, int z, Player player) {
        if (world == null || world.isClientSide() || !TrmtConfig.reinforceEnabled || player == null) return false;
        // A held button is one press (RL19, 0.9.229). This edition's client refuses the dig before it marks the
        // block as being dug, so a held attack key sends the server a fresh start every tick, two on the tick it
        // is pressed, where 1.7.10's client sends one. Which of those begins a press is the client's to say, in
        // its own ticks, since a client that stalls while the server ticks on sends one press's clicks several
        // server ticks apart (clientClicked); a click is taken only as the start of a press so announced.
        if (!press(player).clickTakes(world, x, y, z, player.tickCount)) return true;
        return pressed(world, x, y, z, player);
    }

    /**
     * Server side: the client says a new press of the attack key began on x, y, z ({@code PacketTamperPress}). The
     * press is taken now if its left-click has already been heard, otherwise when that click is.
     */
    public static void pressBegan(Player player, int x, int y, int z) {
        if (player == null || !TrmtConfig.reinforceEnabled) return;
        Level world = player.level();
        if (world == null || world.isClientSide()) return;
        if (press(player).pressTakes(world, x, y, z, player.tickCount)) pressed(world, x, y, z, player);
    }

    /**
     * Client side: a left-click this client is sending with a chunk tamper in reinforce mode, on its own tick
     * {@code clientTick}. The server is told where a press begins, before the click itself goes.
     */
    public static void clientClicked(int x, int y, int z, int clientTick) {
        boolean begins = beginsPress(clientLast, x, y, z, clientTick);
        clientLast = new int[] { x, y, z, clientTick };
        if (begins) com.trmtgtnh.network.TrmtNetwork.sendTamperPress(x, y, z);
    }

    /**
     * Whether the client's left-click at x, y, z on its tick {@code now} begins a press, after the press {@code last}
     * (x, y, z and the client tick it was last known held, or null for none). 1.7.10's rule in the client's own ticks:
     * its client lets the block go on any tick the key is up or the crosshair is on no block, and starts again on a
     * click at another block, so a press is one block held from client tick to client tick, however far apart the
     * server hears its clicks.
     */
    static boolean beginsPress(int[] last, int x, int y, int z, int now) {
        return !(last != null && last[0] == x
            && last[1] == y
            && last[2] == z
            && now - last[3] >= 0
            && now - last[3] <= HELD_CLIENT_TICKS);
    }

    /**
     * Client side, at the end of every client tick {@code clientTick}: whether the attack key is held as the game reads
     * it for digging (no screen, the mouse taken, the key down) and whether the crosshair is on a block. Holds the
     * client's press through the tick, or lets it go.
     */
    public static void clientTicked(boolean attackHeld, boolean onABlock, int clientTick) {
        clientLast = heldThrough(clientLast, attackHeld, onABlock, clientTick);
    }

    /**
     * The client's press {@code last} after its tick {@code now}: let go on a tick with the key up or the crosshair on
     * no block, where 1.7.10's client lets the block go ({@code resetBlockRemoving}), and otherwise held to this tick,
     * so the next click on its block continues it however long the clicks paused (RL19, 2026-11-09). The game's own
     * dig delay, {@code MultiPlayerGameMode.destroyDelay}, returns before the left-click on each tick it counts. It is
     * set to five by a creative start and by a block broken in survival, and counted down only while the key is held,
     * so it carries into the next hold, whose first click goes and then four ticks send none. Read by the gap in its
     * clicks, the next click began a second press and took a level. A crosshair passed over another block keeps the
     * press, as in 1.7.10, which counts its delay before it asks what is hit; a click on that block begins its own.
     */
    static int[] heldThrough(int[] last, boolean attackHeld, boolean onABlock, int now) {
        if (last == null || !attackHeld || !onABlock) return null;
        return new int[] { last[0], last[1], last[2], now };
    }

    private static Press press(Player player) {
        Press press = PRESSES.get(player);
        if (press == null) {
            press = new Press();
            PRESSES.put(player, press);
        }
        return press;
    }

    /**
     * One player's press as the server hears it: the left-click last heard, how many of the clicks heard on its block
     * no press has taken, and a press the client announced that no click has met yet.
     *
     * <p>
     * The client sends the announcement just before the click it belongs to, but the two may be handled in either
     * order, so each waits for the other: whichever is handled second takes the press, once. A client that has not
     * announced a press yet (a build before this rule, or the first press of a visit, whose announcement is handled
     * after its click) is heard by the older rule, the same block within {@link #PRESS_GAP_TICKS} server ticks; the
     * first announcement then finds its click already taken and does not take it again.
     *
     * <p>
     * <strong>Each click heard takes one press at most</strong> (review R30, 0.9.229). An announcement handled after
     * its click took the press from the last click heard and left that click as it was, so a client that announced
     * twice on one block after one click, within {@link #PAIR_TICKS}, was given two presses: the block armed and a level
     * taken off. So the clicks no press has taken are counted, an announcement takes one of them, and one that finds
     * none waits for a click of its own, as it waits for one not heard yet. A count rather than a mark on the last
     * click, because this edition's server hears a click every tick the key is held and two as it goes down: a
     * double-click run together by a stalled client arrives as four clicks and then both announcements, which a mark
     * on the last click would have given one press.
     */
    static final class Press {

        private boolean announcing;

        private Object heardIn;
        private int heardX, heardY, heardZ, heardAt;

        /** The clicks heard on the last click's block, each within PAIR_TICKS of the one before, that took no press. */
        private int untaken;

        private Object newIn;
        private int newX, newY, newZ, newAt;

        /** A left-click on x, y, z in {@code world} was heard on server tick {@code now}: whether it takes a press. */
        boolean clickTakes(Object world, int x, int y, int z, int now) {
            boolean same = at(heardIn, heardX, heardY, heardZ, world, x, y, z);
            boolean continuing = same && within(heardAt, now, PRESS_GAP_TICKS);
            if (!same || !within(heardAt, now, PAIR_TICKS)) untaken = 0;
            heardIn = world;
            heardX = x;
            heardY = y;
            heardZ = z;
            heardAt = now;
            boolean takes;
            if (!announcing) {
                takes = !continuing;
            } else {
                takes = at(newIn, newX, newY, newZ, world, x, y, z) && within(newAt, now, PAIR_TICKS);
                if (takes) newIn = null;
            }
            if (!takes) untaken++;
            return takes;
        }

        /** The client announced a new press on x, y, z in {@code world}, handled on server tick {@code now}. */
        boolean pressTakes(Object world, int x, int y, int z, int now) {
            boolean first = !announcing;
            announcing = true;
            boolean heard = at(heardIn, heardX, heardY, heardZ, world, x, y, z) && within(heardAt, now, PAIR_TICKS);
            // The first announcement of a visit comes after its click, which the older rule has already taken.
            if (heard && first) return false;
            // A click no press has taken yet meets this announcement; one already taken is not taken again (R30).
            if (heard && untaken > 0) {
                untaken--;
                return true;
            }
            newIn = world;
            newX = x;
            newY = y;
            newZ = z;
            newAt = now;
            return false;
        }

        private static boolean at(Object in, int ax, int ay, int az, Object world, int x, int y, int z) {
            return in != null && in == world && ax == x && ay == y && az == z;
        }

        private static boolean within(int then, int now, int ticks) {
            return now - then >= 0 && now - then <= ticks;
        }
    }

    /** One press of the attack key, taken: arms the block, or on the second press takes a level off. */
    private static boolean pressed(Level world, int x, int y, int z, Player player) {
        int level = level(world, x, y, z);
        if (level <= 0) {
            ARMED.remove(player);
            return false;
        }

        int now = player.tickCount;
        int[] armed = ARMED.get(player);
        boolean sameBlock = armed != null && armed[0] == x
            && armed[1] == y
            && armed[2] == z
            && now - armed[3] <= ARM_WINDOW_TICKS;
        if (!sameBlock) {
            ARMED.put(player, new int[] { x, y, z, now });
            say(player, ChatFormatting.YELLOW, com.trmtgtnh.util.Translate.get("trmtgtnh.reinforce.arm"));
            return true;
        }

        ARMED.remove(player);
        setLevel(world, x, y, z, level - 1);
        world.playSound(
            null,
            x + 0.5D,
            y + 0.5D,
            z + 0.5D,
            net.minecraft.sounds.SoundEvents.STONE_BREAK,
            net.minecraft.sounds.SoundSource.BLOCKS,
            0.6f,
            0.8f);
        say(
            player,
            ChatFormatting.AQUA,
            level - 1 <= 0 ? com.trmtgtnh.util.Translate.get("trmtgtnh.reinforce.none")
                : com.trmtgtnh.util.Translate.get(
                    "trmtgtnh.reinforce.now",
                    Integer.valueOf(level - 1),
                    Integer.valueOf(Math.min(3, TrmtConfig.reinforceMaxLevel))));
        return true;
    }

    // ------------------------------------------------------------------

    /** The reinforcement level at a position, for anything that is not a player's own gesture. */
    public static int levelAt(Level world, int x, int y, int z) {
        return level(world, x, y, z);
    }

    /**
     * Raises a position's reinforcement, held to the configured ceiling.
     *
     * <p>
     * Exposed for the golem, which pays for its material the same way and so is allowed to set the
     * same figure. It does not take payment itself: whoever calls it has already settled that.
     */
    public static void raiseBy(Level world, int x, int y, int z, int steps) {
        int cap = Math.min(3, TrmtConfig.reinforceMaxLevel);
        int now = level(world, x, y, z);
        int wanted = Math.min(cap, now + Math.max(0, steps));
        if (wanted != now) setLevel(world, x, y, z, wanted);
    }

    private static int level(Level world, int x, int y, int z) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        return entry == null ? 0 : entry.getReinforce();
    }

    private static void setLevel(Level world, int x, int y, int z, int level) {
        // The store's own index for this level. Both older editions ask the world for a
        // dimension number; there is none here, and the store has always been the thing that knows
        // which of its shelves a level is on.
        int dimension = ErosionStore.get()
            .indexOf(world);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int key = ErosionKey.packWorld(x, y, z);
        com.trmtgtnh.erosion.ChunkErosionData data = ErosionStore.get()
            .getOrCreateChunk(dimension, chunkX, chunkZ);
        ErosionEntry entry = data.get(key);
        if (entry == null) {
            // Family from the block if it is a surface, dirt otherwise - it only matters if this
            // block ever wears; the record itself starts invisible, so no ghost is drawn. Its
            // threshold is a stand-in until the square is first walked on, when the engine draws
            // the family's own.
            SurfaceFamily family = SurfaceRegistry.familyOf(com.trmtgtnh.util.Worlds.stateAt(world, x, y, z));
            if (family == null || !family.staged) family = SurfaceFamily.DIRT;
            entry = new ErosionEntry(family, ErosionEntry.UNDRAWN_THRESHOLD, ErosionEngine.nowSeconds(world));
            data.put(key, entry);
        }
        entry.setReinforce(level);
        if (level <= 0 && entry.isPrunable()) {
            data.remove(key);
        }
        data.markDirty();
        ErosionStore.get()
            .markModified(world, chunkX, chunkZ);
    }

    private static void say(Player player, ChatFormatting color, String message) {
        // The second argument is who said it, which this version wants: nobody did, and
        // NIL_UUID is how the game spells that.
        if (player != null) {
            player.sendSystemMessage(Component.literal(color + message));
        }
    }
}
