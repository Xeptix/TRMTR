package com.trmtgtnh.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.MendLedger;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Pays for mending square by square, out of a live inventory, as a gesture walks its ground.
 *
 * <p>
 * The arithmetic of the price is {@link MendLedger}'s, and has nothing of the game in it. This is
 * the other half: the half that looks at a block, asks what would pay for it, looks in a bag and
 * takes something out. Keeping the two apart is what lets the arithmetic be tested without a world,
 * and what lets two tools with different ideas of the right material - the hand tamper wants what
 * the ground drops, bone meal and the chunk tamper want the ground itself - share one walk and one
 * set of promises about what a gesture costs.
 *
 * <p>
 * Nothing is counted up front. A square's price is worked out when the walk reaches it, and taken
 * only once the gradation it pays for has actually gone back, so a pinned square, an unworn one, or
 * a patch that turns out to mend nothing costs nothing at all. One stack may pay for two kinds of
 * ground - plain dirt fills a rut in a lawn and a rut in bare earth - and it is simply spent by
 * whichever square reaches it first.
 *
 * <p>
 * One purse lasts one gesture. Its quotes are cached for that long and no longer, because a config
 * reload between two clicks may change what a block will take.
 */
public final class MendPurse {

    /**
     * One square's price, worked out once.
     *
     * <p>
     * Worked out once because the check and the take have to name the same items. A block whose drop
     * is rolled at random could otherwise be found affordable in one item and charged in another, with
     * the gradation already back by the time the take found nothing to take.
     */
    public interface Quote extends MendLedger.Accepts {

        /** Whether this many are carried. Stops counting once the figure is reached. */
        boolean has(int count);

        /**
         * Takes this many, the square's own kind first.
         *
         * <p>
         * All or nothing: when fewer than {@code count} are carried it takes nothing and answers null.
         * A take that stopped halfway would have spent stock on a gradation it then refused.
         *
         * @return the distinct kinds taken, as made by {@link MendPurse#kindOf}; null when nothing was
         */
        List<Object> take(int count);

        /**
         * The one item this square wants, by its name as the server reads it, or null when the rule
         * would take any of several things. Only ever used to say what was wanted.
         */
        String wanted();
    }

    /** What would pay for a square. One payer lasts one gesture, like the purse that asks it. */
    public interface Payer {

        /**
         * The price of mending this block, or null when nothing could ever pay for it - a block with
         * no item form, say. A null quote leaves the square exactly as it is, uncharged and uncounted.
         */
        Quote quote(World world, int x, int y, int z, Block block, int meta);
    }

    /** Whether the one warning about a failed take has been written this run. */
    private static boolean warnedUnpaid;

    private final EntityPlayer owner;
    private final MendLedger ledger;
    private final Payer payer;

    /**
     * Every item a short square wanted, by name. A null among them stands for a square whose rule
     * would take any of several things, which is enough on its own to rule out naming one item.
     */
    private final Set<String> wantedSeen = new HashSet<String>();

    /** Set once a take has failed, after which this purse mends nothing more. */
    private boolean halted;

    /**
     * @param player who the gesture is for; may be null, and is used only to name them in the log
     * @param ledger what the gesture costs
     * @param payer  what pays for each square; never asked anything while the ledger is free
     */
    public MendPurse(EntityPlayer player, MendLedger ledger, Payer payer) {
        this.owner = player;
        this.ledger = ledger;
        this.payer = payer;
    }

    /**
     * Bone meal's rule, and the chunk tamper's: a block of the ground itself, or whatever its family
     * will take in its place, from anywhere in the player's inventory.
     *
     * <p>
     * Quotes are kept per block and metadata for as long as the payer lives, which is one gesture. A
     * fifteen-cube of one kind of ground asks the same question some thousands of times, and the
     * answer does not change between them.
     */
    public static Payer byOwnBlock(final EntityPlayer player) {
        return new Payer() {

            private final Map<Long, Quote> quotes = new HashMap<Long, Quote>();

            @Override
            public Quote quote(World world, int x, int y, int z, Block block, int meta) {
                Long key = Long.valueOf(((long) Block.getIdFromBlock(block) << 32) | (meta & 0xFFFFFFFFL));
                if (quotes.containsKey(key)) return quotes.get(key);
                HealingCost cost = HealingCost.forPlayer(player, block, meta);
                Quote quote = cost == null ? null : new OwnBlockQuote(cost);
                quotes.put(key, quote);
                return quote;
            }
        };
    }

    /**
     * What an item taken in payment was, for the ledger to remember a pool by.
     *
     * <p>
     * The item and its damage, and nothing else. A tag a mod has stamped on a block does not make it
     * a different block, and the rules that decide what pays already ignore tags.
     *
     * @return an opaque value with a sensible {@code equals}, or null for no stack
     */
    public static Object kindOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        return new Kind(stack.getItem(), stack.getItemDamage());
    }

    /**
     * A fresh stack of one of a kind, so a square can be asked whether it would have taken it.
     *
     * @return null for anything {@link #kindOf} did not make
     */
    public static ItemStack stackOf(Object kind) {
        if (!(kind instanceof Kind)) return null;
        Kind known = (Kind) kind;
        return new ItemStack(known.item, 1, known.damage);
    }

    /** What the gesture has cost and done so far. */
    public MendLedger ledger() {
        return ledger;
    }

    /**
     * Puts back up to {@code steps} gradations on one square, paying for each as it lands.
     *
     * <p>
     * A square is priced only if it is worn ground the engine would mend. It then draws on credit
     * already bought with stock it would have taken itself, or buys its own; if it cannot, it is left
     * where it stands and the ledger is told so. A gradation goes back before its price is taken and
     * the price is taken whole or not at all, so nothing is ever spent on work that did not happen.
     *
     * @return how many gradations went back on this square
     */
    public int mendSquare(World world, int x, int y, int z, int steps, boolean notifyClients) {
        if (halted || steps <= 0) return 0;
        // Asking the world for ground it does not have loaded would generate the chunk, and a patch
        // at a chunk edge reaches across one.
        if (!world.blockExists(x, y, z)) return 0;
        ErosionEngine engine = ErosionEngine.get();
        if (!engine.restorable(world, x, y, z)) return 0;

        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        SurfaceFamily family = SurfaceRegistry.familyOf(block, meta);
        if (family == null || !family.staged) return 0;

        Quote quote = null;
        if (!ledger.isFree()) {
            quote = payer.quote(world, x, y, z, block, meta);
            // Nothing could ever pay for this square, so it is neither priced nor counted as short:
            // telling a player to carry something that does not exist is no help to anybody.
            if (quote == null) return 0;
        }

        int done = 0;
        for (int step = 0; step < steps; step++) {
            int owed = ledger.isFree() ? 0 : ledger.owedBefore(family, quote);
            if (owed > 0 && !quote.has(owed)) {
                ledger.shortOf(family, done == 0);
                wantedSeen.add(quote.wanted());
                break;
            }
            if (!engine.restoreOneStage(world, x, y, z, notifyClients)) break;
            if (owed > 0) {
                List<Object> kinds = quote.take(owed);
                if (kinds == null) {
                    // Cannot happen on the server thread, where nothing touches the inventory between
                    // the check a line above and this take. If it does, the gradation has already
                    // landed: it is counted so the report is true, earns nothing, and the walk stops
                    // rather than mending any more on credit it does not have.
                    if (!warnedUnpaid) {
                        warnedUnpaid = true;
                        Trmt.LOG.warn(
                            "A mend for {} found its payment gone between check and take at {},{},{}; gesture stopped",
                            owner == null ? "nobody" : owner.getCommandSenderName(),
                            Integer.valueOf(x),
                            Integer.valueOf(y),
                            Integer.valueOf(z));
                    }
                    halted = true;
                    ledger.mendedUnpaid(family);
                    done++;
                    break;
                }
                ledger.bought(family, kinds);
            }
            ledger.mended(family, quote);
            done++;
        }
        return done;
    }

    /** This purse as the work an engine patch hands each of its squares to, telling clients as it goes. */
    public ErosionEngine.PatchWork patchWork() {
        return new ErosionEngine.PatchWork() {

            @Override
            public int apply(World world, int x, int y, int z, int steps) {
                return mendSquare(world, x, y, z, steps, true);
            }
        };
    }

    /**
     * Says which ground was left for want of material, after a gesture that did mend something.
     *
     * <p>
     * Silent unless something was actually short. A square refused for being pinned, unworn or beyond
     * any payment never reaches the ledger as short, so it never reaches chat either.
     */
    public void reportShort(EntityPlayer player, boolean prefixed) {
        MendLedger.Shortfall shortfall = ledger.shortfall();
        String text;
        if (shortfall == MendLedger.Shortfall.LEFT_SQUARES) {
            int left = ledger.squaresLeft();
            text = left == 1 ? StatCollector.translateToLocalFormatted("trmtgtnh.mend.left.one", familyList())
                : StatCollector
                    .translateToLocalFormatted("trmtgtnh.mend.left.many", Integer.valueOf(left), familyList());
        } else if (shortfall == MendLedger.Shortfall.RAN_OUT) {
            text = StatCollector.translateToLocalFormatted("trmtgtnh.mend.ranOut", familyList());
        } else {
            return;
        }
        Notices.say(player, Notices.line(text, EnumChatFormatting.GRAY, prefixed));
    }

    /**
     * Says why a gesture mended nothing, when the reason was material.
     *
     * <p>
     * Silent for every other reason. When every short square wanted one and the same item, and that
     * item has a name, the line names it and how many: "needs two dirt" is something a player can act
     * on. When the squares wanted different things, or a rule that would take several, it names the
     * ground instead, because naming one item of several would send somebody off for the wrong one.
     *
     * @param price what one purchase costs, for the line that names an item; nought when no such line
     *              is wanted
     */
    public void reportNothing(EntityPlayer player, int price, boolean prefixed) {
        if (ledger.shortfall() != MendLedger.Shortfall.NOTHING_MENDED) return;
        String text;
        if (price > 0 && wantedSeen.size() == 1 && !wantedSeen.contains(null)) {
            Iterator<String> only = wantedSeen.iterator();
            text = StatCollector
                .translateToLocalFormatted("trmtgtnh.tamper.needs", Integer.valueOf(price), only.next());
        } else {
            text = StatCollector.translateToLocalFormatted("trmtgtnh.mend.nothing", familyList());
        }
        Notices.say(player, Notices.line(text, EnumChatFormatting.GRAY, prefixed));
    }

    /**
     * The short families by name, in plain English order: "grass", "grass or cobble", "grass, sand or
     * cobble". Every piece is translated here on the server, joining words included.
     */
    private String familyList() {
        String joined = null;
        String pending = null;
        for (SurfaceFamily family : ledger.shortFamilies()) {
            String name = StatCollector.translateToLocal("trmtgtnh.family." + family.key());
            if (joined == null) {
                joined = name;
            } else if (pending == null) {
                pending = name;
            } else {
                joined = StatCollector.translateToLocalFormatted("trmtgtnh.mend.list", joined, pending);
                pending = name;
            }
        }
        if (joined == null) return "";
        if (pending == null) return joined;
        return StatCollector.translateToLocalFormatted("trmtgtnh.mend.or", joined, pending);
    }

    /** {@link HealingCost}'s rules, asked of one block and remembered for the gesture. */
    private static final class OwnBlockQuote implements Quote {

        private final HealingCost cost;

        /** Whether each kind met so far would have paid here, so a pool is judged once, not per gradation. */
        private final Map<Object, Boolean> answers = new HashMap<Object, Boolean>(4);

        OwnBlockQuote(HealingCost cost) {
            this.cost = cost;
        }

        @Override
        public boolean accepts(Object kind) {
            Boolean known = answers.get(kind);
            if (known == null) {
                ItemStack stack = stackOf(kind);
                known = Boolean.valueOf(stack != null && cost.paidBy(stack));
                answers.put(kind, known);
            }
            return known.booleanValue();
        }

        @Override
        public boolean has(int count) {
            return cost.has(count);
        }

        @Override
        public List<Object> take(int count) {
            return cost.take(count);
        }

        @Override
        public String wanted() {
            // The rule takes the block itself, anything its family lists and possibly any block of
            // its family, so there is no one item to name.
            return null;
        }
    }

    /** An item and its damage, compared by value. */
    private static final class Kind {

        private final Item item;
        private final int damage;

        Kind(Item item, int damage) {
            this.item = item;
            this.damage = damage;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Kind)) return false;
            Kind that = (Kind) other;
            return item == that.item && damage == that.damage;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(item) * 31 + damage;
        }

        @Override
        public String toString() {
            return Item.itemRegistry.getNameForObject(item) + "@" + damage;
        }
    }
}
