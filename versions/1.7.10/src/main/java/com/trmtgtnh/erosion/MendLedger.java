package com.trmtgtnh.erosion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What one mending gesture owes, and what it has already paid for.
 *
 * <p>
 * Every promise made about the price of mending is a promise about arithmetic. A chunk tamper set to
 * eight costs eight times one set to one, over any area big enough for rounding not to show; it costs about a quarter
 * of what the hand tamper would
 * charge for the same gradations; each kind of ground pays its own way; ground whose material is not
 * carried is left rather than mended at somebody else's expense. None of those is about an
 * inventory, an item or a world, and every one of them is easy to break quietly - a price rounded
 * once per gesture instead of once per kind of ground, a credit that leaks from grass to cobble, a
 * count that overflows on a large cube. So the arithmetic is kept here, with nothing from the game
 * in it, where a test can run it and pin the figures. Taking the blocks out of a player's inventory
 * is a separate job, done by {@code MendPurse}, and taking them out of a golem's storage is done by
 * {@code GolemWork}; each asks this class what is owed and tells it what was taken.
 *
 * <p>
 * <b>Credit lives in pools.</b> A purchase opens a pool holding the family it was made for, the kinds
 * of item that were taken for it, and how much mending it has left to pay for: without limit for a
 * price charged once a gesture, or so many gradations for a price charged by the gradation. A square
 * may draw on a pool only when it is of the same family and would itself have accepted every kind
 * of item in that pool. That condition is what stops cobble bought for a stone road from paying for
 * the mossy cobble or the granite beside it, which a tamper refuses to be paid with; keying credit
 * by family alone mended those squares with material the player was not carrying. Squares that
 * would accept each other's stock still share one pool, so a patch of a single kind of ground costs
 * exactly what one gesture always did.
 *
 * <p>
 * Rounding happens per pool, and a ledger lasts one gesture. A golem's stroke counts as a gesture,
 * and is priced through {@link #rate(int, int)}. The last purchase made by the gradation may pay for
 * a few gradations nothing needed, and that part-purchase is not carried into the next gesture,
 * because nothing is remembered between gestures to carry it in. So a golem that finds one gradation
 * of some ground on a stroke pays a whole purchase for it, however many gradations a purchase would
 * have paid for.
 *
 * <p>
 * Kinds are opaque to this class. It never looks inside one; it only hands them back to the square
 * that is asking, so they need nothing but a sensible {@code equals}.
 */
public final class MendLedger {

    /** Why a gesture did less than it was asked to, as far as the price is concerned. */
    public enum Shortfall {
        /** Nothing was short. Squares refused for any other reason do not reach the ledger. */
        NONE,
        /** At least one square was left exactly as it was because its material was not carried. */
        LEFT_SQUARES,
        /** Every square short of material had already had something put back before it ran out. */
        RAN_OUT,
        /** Nothing at all was mended, and at least one square was short of material. */
        NOTHING_MENDED
    }

    /**
     * The square asking: would stock of this kind have paid for it?
     *
     * <p>
     * Kinds are opaque and compared only through {@code equals}. A square answers for itself, which
     * is how a pool bought by one square is refused to another that would never have taken what was
     * bought.
     */
    public interface Accepts {

        boolean accepts(Object kind);
    }

    /** The credit of a pool bought at a price charged once a gesture, which is never used up. */
    private static final int UNLIMITED = Integer.MAX_VALUE;

    /** What one purchase costs in blocks, or nought for a ledger that charges nothing. */
    private final int price;

    /** How much mending one purchase pays for: {@link #UNLIMITED}, or a number of gradations. */
    private final int creditPerPool;

    /**
     * Pools that still have credit, per family, earliest first.
     *
     * <p>
     * A pool whose credit is spent is dropped rather than kept. It can never pay again, and on a
     * large cube priced by the gradation there are thousands of them, so walking past spent pools on
     * every gradation would make a gesture's cost grow with the square of its size. What is left is
     * one live pool for each set of stock that no earlier square would share, which is a handful.
     */
    private final EnumMap<SurfaceFamily, List<Pool>> live = new EnumMap<SurfaceFamily, List<Pool>>(SurfaceFamily.class);

    private final EnumSet<SurfaceFamily> shortFamilies = EnumSet.noneOf(SurfaceFamily.class);

    private int gradations;
    private int paidGradations;
    private int blocksSpent;
    private int squaresLeft;
    private int squaresPartly;

    private MendLedger(int price, int creditPerPool) {
        this.price = price;
        this.creditPerPool = creditPerPool;
    }

    /** A ledger that charges nothing, never refuses a square, and records no paid gradation. */
    public static MendLedger free() {
        return new MendLedger(0, 0);
    }

    /**
     * One price per pool of ground met, however much of it is mended.
     *
     * <p>
     * This is the shape of bone meal's price and the hand tamper's. A figure of nought or below gives
     * {@link #free()}, so a config setting of nought means mending by that gesture costs nothing - and
     * earns nothing, since only paid gradations are worth experience.
     *
     * @param blocks the price of one pool, in blocks
     */
    public static MendLedger perGesture(int blocks) {
        if (blocks <= 0) return free();
        return new MendLedger(blocks, UNLIMITED);
    }

    /**
     * One block per so many gradations, rounded up per pool.
     *
     * <p>
     * This is the chunk tamper's price. A figure below one is held at one rather than read as free,
     * because nought gradations per block has no sensible meaning as an exchange rate and the switch
     * that makes the chunk tamper free is a separate setting. It is exactly
     * {@link #rate(int, int) rate(1, gradationsPerBlock)}, and a test holds the two to the same figures.
     *
     * @param gradationsPerBlock how many gradations one block pays to put back
     */
    public static MendLedger perGradations(int gradationsPerBlock) {
        return rate(1, gradationsPerBlock);
    }

    /**
     * So many blocks per so many gradations, rounded up per pool.
     *
     * <p>
     * This is the golem's price: the chunk tamper's rate, with as many blocks to a purchase as the golem
     * is charged. {@code rate(1, g)} is {@link #perGradations(int) perGradations(g)}, and
     * {@code rate(m, 1)} charges m blocks for every gradation, which is the flat price golems paid up to
     * 0.9.205 and pay again when {@code GolemWork.PRICED_FROM_THE_CHUNK_TAMPER} is set to false.
     *
     * <p>
     * Both figures below one are held at one rather than read as free. Nought gradations to a purchase
     * has no sensible meaning as an exchange rate, for the reason perGradations gives; and a golem paying
     * nought blocks would mend for nothing, which is refused on purpose, because a keeper that cost
     * nothing to run would never need looking after. So no figure passed here, however it was reached,
     * silently makes mending free; {@link #free()} is the only way to that.
     *
     * @param blocks     what one purchase costs, in blocks
     * @param gradations how many gradations of one pool a purchase pays to put back
     */
    public static MendLedger rate(int blocks, int gradations) {
        return new MendLedger(blocks < 1 ? 1 : blocks, gradations < 1 ? 1 : gradations);
    }

    /** True when this ledger charges nothing at all. */
    public boolean isFree() {
        return price <= 0;
    }

    /**
     * What one purchase costs, in blocks: the gesture price, or the price of one purchase by the
     * gradation, which is one block for the chunk tamper and may be more for a golem; nought when free.
     */
    public int price() {
        return price;
    }

    /**
     * What this square must buy before its next gradation can go back.
     *
     * <p>
     * Nought when the ledger is free, or when a pool of this family still has credit and the square
     * would accept every kind of item in it; otherwise {@link #price()}. Asking changes nothing, so a
     * caller may ask, find the stock is not carried, and walk away without having spent any credit.
     *
     * @param family the family of the square's ground
     * @param square the square asking; null is read as a square that accepts nothing
     */
    public int owedBefore(SurfaceFamily family, Accepts square) {
        if (isFree()) return 0;
        return poolFor(family, square) == null ? price : 0;
    }

    /**
     * Records a purchase, opening a pool bought with these kinds.
     *
     * <p>
     * Called after the gradation it pays for has landed and the blocks have been taken, and before
     * {@link #mended} records that gradation - which then draws on the pool opened here. It opens a
     * new pool rather than topping up an old one, because an old one may have been bought with stock
     * this square's neighbours would not take.
     *
     * @param family the family of the square that bought
     * @param kinds  the distinct kinds of item taken; duplicates are folded together
     * @throws IllegalStateException when no kinds are given, or a kind is null, since a purchase that
     *                               names nothing it took cannot be told apart from one that took
     *                               nothing; or when this ledger is free, since a free ledger taking
     *                               material means a caller has charged a player for nothing
     */
    public void bought(SurfaceFamily family, Collection<?> kinds) {
        if (isFree()) {
            throw new IllegalStateException("A free mending ledger was told a purchase was made for " + family);
        }
        if (kinds == null || kinds.isEmpty()) {
            throw new IllegalStateException("A purchase for " + family + " named no kind of item taken");
        }
        LinkedHashSet<Object> distinct = new LinkedHashSet<Object>();
        for (Object kind : kinds) {
            if (kind == null) {
                throw new IllegalStateException("A purchase for " + family + " named a null kind of item");
            }
            distinct.add(kind);
        }
        List<Pool> pools = live.get(family);
        if (pools == null) {
            pools = new ArrayList<Pool>(2);
            live.put(family, pools);
        }
        pools.add(new Pool(distinct.toArray(), creditPerPool));
        blocksSpent += price;
    }

    /**
     * Records one gradation put back.
     *
     * <p>
     * On a paid ledger it draws on the earliest pool of this family that still has credit and that
     * the square accepts. When there is none the caller has mended a gradation nothing paid for,
     * which is exactly the leak this class exists to close, so it fails loudly rather than letting the
     * gradation through. A free ledger ignores pools, and the square may be null.
     *
     * @throws IllegalStateException on a paid ledger with no pool this square may draw on
     */
    public void mended(SurfaceFamily family, Accepts square) {
        if (isFree()) {
            gradations++;
            return;
        }
        Pool pool = poolFor(family, square);
        if (pool == null) {
            throw new IllegalStateException(
                "A gradation of " + family + " was recorded as mended with nothing bought that would pay for it");
        }
        if (pool.credit != UNLIMITED) {
            pool.credit--;
            if (pool.credit <= 0) live.get(family)
                .remove(pool);
        }
        gradations++;
        paidGradations++;
    }

    /**
     * Records a gradation that landed although the take that should have paid for it failed.
     *
     * <p>
     * That cannot happen on the server thread, where nothing else touches the inventory between the
     * check and the take. If it does, the gradation is counted, so the report is truthful about what
     * changed, and never paid, so it earns nothing.
     */
    public void mendedUnpaid(SurfaceFamily family) {
        gradations++;
    }

    /**
     * Records a square that wanted a gradation it could not pay for.
     *
     * @param family    the family of the square's ground
     * @param untouched true when nothing at all went back on the square, false when some gradations
     *                  did before the material ran out
     */
    public void shortOf(SurfaceFamily family, boolean untouched) {
        if (untouched) squaresLeft++;
        else squaresPartly++;
        shortFamilies.add(family);
    }

    /** Every gradation put back, paid for or not. */
    public int gradations() {
        return gradations;
    }

    /** Gradations put back and paid for. Always nought on a free ledger, which is what keeps it from earning. */
    public int paidGradations() {
        return paidGradations;
    }

    /** Blocks spent: the price of one purchase for every pool opened. */
    public int blocksSpent() {
        return blocksSpent;
    }

    /** Squares left exactly as they were for want of material. */
    public int squaresLeft() {
        return squaresLeft;
    }

    /** Squares that had some wear put back before their material ran out. */
    public int squaresPartly() {
        return squaresPartly;
    }

    /** The families that were short of material, in declaration order. A read-only view that follows the ledger. */
    public Set<SurfaceFamily> shortFamilies() {
        return Collections.unmodifiableSet(shortFamilies);
    }

    /**
     * What, if anything, the gesture was short of.
     *
     * <p>
     * {@link Shortfall#NOTHING_MENDED} when nothing went back and something was short;
     * {@link Shortfall#LEFT_SQUARES} when any square was left untouched; {@link Shortfall#RAN_OUT}
     * when every short square had already been partly mended; otherwise {@link Shortfall#NONE}. A
     * gesture that mended nothing for any reason other than material answers NONE, so a caller that
     * speaks only on a shortfall stays quiet about ground that was pinned, unworn or never payable.
     */
    public Shortfall shortfall() {
        if (squaresLeft == 0 && squaresPartly == 0) return Shortfall.NONE;
        if (gradations == 0) return Shortfall.NOTHING_MENDED;
        if (squaresLeft > 0) return Shortfall.LEFT_SQUARES;
        return Shortfall.RAN_OUT;
    }

    /** The earliest live pool of this family whose every kind the square accepts, or null. */
    private Pool poolFor(SurfaceFamily family, Accepts square) {
        if (square == null) return null;
        List<Pool> pools = live.get(family);
        if (pools == null) return null;
        for (int i = 0; i < pools.size(); i++) {
            Pool pool = pools.get(i);
            if (pool.servedBy(square)) return pool;
        }
        return null;
    }

    /** Credit bought once, and the kinds of item it was bought with. */
    private static final class Pool {

        private final Object[] kinds;
        private int credit;

        Pool(Object[] kinds, int credit) {
            this.kinds = kinds;
            this.credit = credit;
        }

        /** True when the square would have taken every kind of item this pool was bought with. */
        boolean servedBy(Accepts square) {
            for (Object kind : kinds) {
                if (!square.accepts(kind)) return false;
            }
            return true;
        }
    }
}
