package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What mending costs, in figures.
 *
 * <p>
 * The promises made about the price of mending are all arithmetic: a chunk tamper set to eight costs
 * eight times one set to one, each kind of ground pays its own way, credit bought with cobble never
 * pays for mossy cobble, and a free gesture neither refuses a square nor earns anything. Every one of
 * them could break without a single thing looking wrong in the world - a patch that mends is a patch
 * that mends, whoever paid for it. So each is pinned here with the exact figure it promises, rather
 * than with a comparison that a wrong figure could also satisfy.
 *
 * <p>
 * The walk each test performs is the one {@code MendPurse} performs on a square: ask what is owed,
 * let the gradation land, record the purchase if one was owed, then record the gradation against the
 * credit. Nothing here needs Minecraft, which is why it can be pinned at all.
 */
class MendLedgerTest {

    /** A square that would take anything at all, which is what a family with its switch on behaves like. */
    private static final MendLedger.Accepts ALL = new MendLedger.Accepts() {

        @Override
        public boolean accepts(Object kind) {
            return true;
        }
    };

    /** A square that would take only these kinds of item. */
    private static MendLedger.Accepts taking(Object... kinds) {
        final List<Object> taken = Arrays.asList(kinds);
        return new MendLedger.Accepts() {

            @Override
            public boolean accepts(Object kind) {
                return taken.contains(kind);
            }
        };
    }

    /**
     * Mends one square by so many gradations, the way {@code MendPurse} walks it, paying with the one
     * kind of item given whenever something is owed.
     *
     * @return how many purchases the square made
     */
    private static int walk(MendLedger ledger, SurfaceFamily family, MendLedger.Accepts square, Object kind,
        int steps) {
        int purchases = 0;
        for (int i = 0; i < steps; i++) {
            if (ledger.owedBefore(family, square) > 0) {
                ledger.bought(family, Collections.singletonList(kind));
                purchases++;
            }
            ledger.mended(family, square);
        }
        return purchases;
    }

    @Test
    @DisplayName("one kind of ground costs one gesture's price, however much of it is mended")
    void oneKindCostsWhatItDid() {
        MendLedger one = MendLedger.perGesture(1);
        for (int square = 0; square < 37; square++) walk(one, SurfaceFamily.GRASS, ALL, "dirt", 1);
        assertEquals(1, one.blocksSpent());
        assertEquals(37, one.paidGradations());
        assertEquals(37, one.gradations());

        MendLedger two = MendLedger.perGesture(2);
        for (int square = 0; square < 37; square++) walk(two, SurfaceFamily.GRASS, ALL, "dirt", 1);
        assertEquals(2, two.blocksSpent());
        assertEquals(37, two.paidGradations());
    }

    @Test
    @DisplayName("two kinds of ground in one gesture each pay the price")
    void twoKindsPayEach() {
        MendLedger ledger = MendLedger.perGesture(2);
        // Interleaved, as a scattered patch meets them, so the order visited cannot be what matters.
        for (int square = 0; square < 5; square++) {
            walk(ledger, SurfaceFamily.GRASS, ALL, "dirt", 1);
            if (square < 3) walk(ledger, SurfaceFamily.COBBLE, ALL, "cobble", 1);
        }
        assertEquals(4, ledger.blocksSpent());
        assertEquals(8, ledger.paidGradations());
    }

    @Test
    @DisplayName("ground mended eight deep costs eight times ground mended one deep")
    void eightDeepCostsEightTimesOneDeep() {
        MendLedger shallow = MendLedger.perGradations(4);
        for (int square = 0; square < 100; square++) walk(shallow, SurfaceFamily.DIRT, ALL, "dirt", 1);
        assertEquals(25, shallow.blocksSpent());
        assertEquals(100, shallow.paidGradations());

        MendLedger deep = MendLedger.perGradations(4);
        for (int square = 0; square < 100; square++) walk(deep, SurfaceFamily.DIRT, ALL, "dirt", 8);
        assertEquals(200, deep.blocksSpent());
        assertEquals(800, deep.paidGradations());
        assertEquals(8 * shallow.blocksSpent(), deep.blocksSpent());
    }

    @Test
    @DisplayName("a price by the gradation is rounded up for each kind of ground on its own")
    void roundingIsPerKind() {
        MendLedger uneven = MendLedger.perGradations(4);
        walk(uneven, SurfaceFamily.GRASS, ALL, "dirt", 5);
        walk(uneven, SurfaceFamily.DIRT, ALL, "dirt", 3);
        assertEquals(3, uneven.blocksSpent());
        assertEquals(8, uneven.paidGradations());

        MendLedger even = MendLedger.perGradations(4);
        walk(even, SurfaceFamily.GRASS, ALL, "dirt", 4);
        walk(even, SurfaceFamily.DIRT, ALL, "dirt", 4);
        assertEquals(2, even.blocksSpent());
        assertEquals(8, even.paidGradations());
    }

    @Test
    @DisplayName("credit bought for one family never pays for another")
    void creditNeverCrossesFamilies() {
        MendLedger ledger = MendLedger.perGesture(3);
        walk(ledger, SurfaceFamily.GRASS, ALL, "dirt", 1);
        assertEquals(0, ledger.owedBefore(SurfaceFamily.GRASS, ALL));
        assertEquals(3, ledger.owedBefore(SurfaceFamily.DIRT, ALL), "a square that takes anything still pays");
        assertEquals(3, ledger.blocksSpent());
    }

    @Test
    @DisplayName("a free ledger never refuses a square and never earns, even across a whole cube")
    void freeNeverRefusesAndNeverEarns() {
        MendLedger free = MendLedger.free();
        assertTrue(free.isFree());
        assertEquals(0, free.price());
        // A fifteen-cubed chunk tamper cube, every square sixteen gradations deep.
        for (int square = 0; square < 3375; square++) {
            for (int step = 0; step < 16; step++) {
                assertEquals(0, free.owedBefore(SurfaceFamily.STONE, ALL));
                free.mended(SurfaceFamily.STONE, ALL);
            }
        }
        // The purse hands a free ledger no quote at all, so a null square must be taken quietly.
        free.mended(SurfaceFamily.STONE, null);
        assertEquals(54001, free.gradations());
        assertEquals(0, free.paidGradations());
        assertEquals(0, free.blocksSpent());
        assertEquals(MendLedger.Shortfall.NONE, free.shortfall());
    }

    @Test
    @DisplayName("a gesture price of nought or below is free")
    void noughtOrBelowIsFree() {
        for (int blocks : new int[] { 0, -3 }) {
            MendLedger ledger = MendLedger.perGesture(blocks);
            assertTrue(ledger.isFree(), "perGesture(" + blocks + ")");
            assertEquals(0, ledger.price());
            assertEquals(0, ledger.owedBefore(SurfaceFamily.GRASS, ALL));
            ledger.mended(SurfaceFamily.GRASS, null);
            assertEquals(1, ledger.gradations());
            assertEquals(0, ledger.paidGradations());
            assertEquals(0, ledger.blocksSpent());
        }
    }

    @Test
    @DisplayName("a price by the gradation below one is held at one, never read as free")
    void perGradationsIsHeldAtOne() {
        for (int rate : new int[] { 0, -7 }) {
            MendLedger ledger = MendLedger.perGradations(rate);
            assertFalse(ledger.isFree(), "perGradations(" + rate + ")");
            assertEquals(1, ledger.price());
            assertEquals(3, walk(ledger, SurfaceFamily.SAND, ALL, "sand", 3));
            assertEquals(3, ledger.blocksSpent());
            assertEquals(3, ledger.paidGradations());
        }
    }

    @Test
    @DisplayName("asking what is owed changes nothing")
    void askingThePriceChangesNothing() {
        MendLedger ledger = MendLedger.perGradations(4);
        for (int i = 0; i < 10; i++) assertEquals(1, ledger.owedBefore(SurfaceFamily.GRAVEL, ALL));
        assertEquals(0, ledger.blocksSpent());
        assertEquals(0, ledger.gradations());

        assertEquals(1, walk(ledger, SurfaceFamily.GRAVEL, ALL, "gravel", 1));
        for (int i = 0; i < 50; i++) assertEquals(0, ledger.owedBefore(SurfaceFamily.GRAVEL, ALL));
        // Three gradations of credit are left however often the price was asked.
        assertEquals(0, walk(ledger, SurfaceFamily.GRAVEL, ALL, "gravel", 3));
        assertEquals(1, ledger.owedBefore(SurfaceFamily.GRAVEL, ALL));
        assertEquals(1, ledger.blocksSpent());
        assertEquals(4, ledger.gradations());
        assertEquals(4, ledger.paidGradations());
    }

    @Test
    @DisplayName("mending with nothing to pay for it, or buying with nothing, is refused loudly")
    void mendingUnpaidThrows() {
        final MendLedger ledger = MendLedger.perGesture(1);
        assertThrows(IllegalStateException.class, new Executable() {

            @Override
            public void execute() {
                ledger.mended(SurfaceFamily.GRASS, ALL);
            }
        }, "nothing bought at all");

        ledger.bought(SurfaceFamily.GRASS, Collections.singletonList("dirt"));
        ledger.mended(SurfaceFamily.GRASS, taking("dirt"));
        assertThrows(IllegalStateException.class, new Executable() {

            @Override
            public void execute() {
                ledger.mended(SurfaceFamily.GRASS, taking("sand"));
            }
        }, "credit this square would not have taken");
        assertThrows(IllegalStateException.class, new Executable() {

            @Override
            public void execute() {
                ledger.mended(SurfaceFamily.GRASS, null);
            }
        }, "a square that accepts nothing");
        assertThrows(IllegalStateException.class, new Executable() {

            @Override
            public void execute() {
                ledger.bought(SurfaceFamily.GRASS, new ArrayList<Object>());
            }
        }, "a purchase naming no kind");
        assertThrows(IllegalStateException.class, new Executable() {

            @Override
            public void execute() {
                ledger.bought(SurfaceFamily.GRASS, null);
            }
        }, "a purchase naming nothing at all");

        final MendLedger free = MendLedger.free();
        assertThrows(IllegalStateException.class, new Executable() {

            @Override
            public void execute() {
                free.bought(SurfaceFamily.GRASS, Collections.singletonList("dirt"));
            }
        }, "material taken on a free ledger");

        // A refusal records nothing, so the figures are those of the one gradation that was paid for.
        assertEquals(1, ledger.gradations());
        assertEquals(1, ledger.paidGradations());
        assertEquals(1, ledger.blocksSpent());
        assertEquals(0, free.blocksSpent());
    }

    @Test
    @DisplayName("the last block by the gradation is charged whole, however little of it is used")
    void lastBlockIsChargedWhole() {
        MendLedger four = MendLedger.perGradations(4);
        walk(four, SurfaceFamily.SNOW, ALL, "snow", 1);
        assertEquals(1, four.blocksSpent());
        assertEquals(1, four.paidGradations());

        MendLedger sixteen = MendLedger.perGradations(16);
        walk(sixteen, SurfaceFamily.SNOW, ALL, "snow", 1);
        assertEquals(1, sixteen.blocksSpent());
    }

    @Test
    @DisplayName("a shortfall is named by what was left, what ran out, and whether anything mended")
    void shortfalls() {
        MendLedger none = MendLedger.perGesture(1);
        assertEquals(MendLedger.Shortfall.NONE, none.shortfall());
        walk(none, SurfaceFamily.GRASS, ALL, "dirt", 4);
        assertEquals(MendLedger.Shortfall.NONE, none.shortfall());
        assertTrue(
            none.shortFamilies()
                .isEmpty());

        MendLedger left = MendLedger.perGesture(1);
        walk(left, SurfaceFamily.GRASS, ALL, "dirt", 2);
        left.shortOf(SurfaceFamily.COBBLE, true);
        left.shortOf(SurfaceFamily.COBBLE, true);
        left.shortOf(SurfaceFamily.GRASS, false);
        assertEquals(MendLedger.Shortfall.LEFT_SQUARES, left.shortfall());
        assertEquals(2, left.squaresLeft());
        assertEquals(1, left.squaresPartly());
        assertEquals(EnumSet.of(SurfaceFamily.GRASS, SurfaceFamily.COBBLE), left.shortFamilies());
        Iterator<SurfaceFamily> order = left.shortFamilies()
            .iterator();
        assertEquals(SurfaceFamily.GRASS, order.next(), "families come back in declaration order");
        assertEquals(SurfaceFamily.COBBLE, order.next());

        MendLedger ranOut = MendLedger.perGesture(1);
        walk(ranOut, SurfaceFamily.GRASS, ALL, "dirt", 3);
        ranOut.shortOf(SurfaceFamily.GRASS, false);
        assertEquals(MendLedger.Shortfall.RAN_OUT, ranOut.shortfall());
        assertEquals(0, ranOut.squaresLeft());
        assertEquals(1, ranOut.squaresPartly());

        final MendLedger nothing = MendLedger.perGesture(1);
        nothing.shortOf(SurfaceFamily.SAND, true);
        assertEquals(MendLedger.Shortfall.NOTHING_MENDED, nothing.shortfall());
        assertEquals(1, nothing.squaresLeft());
        assertEquals(EnumSet.of(SurfaceFamily.SAND), nothing.shortFamilies());

        assertThrows(UnsupportedOperationException.class, new Executable() {

            @Override
            public void execute() {
                nothing.shortFamilies()
                    .add(SurfaceFamily.ICE);
            }
        }, "the families short are the ledger's to record");
    }

    @Test
    @DisplayName("a gradation whose take failed is counted but never paid")
    void mendedUnpaidIsCountedNotPaid() {
        MendLedger ledger = MendLedger.perGesture(2);
        walk(ledger, SurfaceFamily.GRASS, ALL, "dirt", 3);
        ledger.mendedUnpaid(SurfaceFamily.GRASS);
        assertEquals(4, ledger.gradations());
        assertEquals(3, ledger.paidGradations());
        assertEquals(2, ledger.blocksSpent());
    }

    @Test
    @DisplayName("cobble bought for one square never pays for mossy cobble beside it")
    void aPoolRefusedByTheSquareIsNotDrawnOn() {
        final MendLedger ledger = MendLedger.perGesture(2);
        final MendLedger.Accepts cobble = taking("cobble");
        final MendLedger.Accepts mossy = taking("mossy");

        assertEquals(2, ledger.owedBefore(SurfaceFamily.COBBLE, cobble));
        assertEquals(1, walk(ledger, SurfaceFamily.COBBLE, cobble, "cobble", 1));
        assertEquals(2, ledger.owedBefore(SurfaceFamily.COBBLE, mossy));
        assertThrows(IllegalStateException.class, new Executable() {

            @Override
            public void execute() {
                ledger.mended(SurfaceFamily.COBBLE, mossy);
            }
        });
        assertEquals(2, ledger.blocksSpent());

        assertEquals(1, walk(ledger, SurfaceFamily.COBBLE, mossy, "mossy", 1));
        assertEquals(0, walk(ledger, SurfaceFamily.COBBLE, cobble, "cobble", 1), "plain cobble still has its pool");
        assertEquals(4, ledger.blocksSpent());
        assertEquals(3, ledger.paidGradations());
    }

    @Test
    @DisplayName("squares that would take a pool's stock share it")
    void squaresAcceptingThePoolShareIt() {
        MendLedger ledger = MendLedger.perGesture(2);
        MendLedger.Accepts vanillaGrass = taking("dirt");
        MendLedger.Accepts moddedTurf = taking("dirt", "turf");

        assertEquals(1, walk(ledger, SurfaceFamily.GRASS, vanillaGrass, "dirt", 1));
        assertEquals(0, ledger.owedBefore(SurfaceFamily.GRASS, moddedTurf));
        ledger.mended(SurfaceFamily.GRASS, moddedTurf);
        assertEquals(2, ledger.blocksSpent());
        assertEquals(2, ledger.paidGradations());
    }

    @Test
    @DisplayName("a pool bought with two kinds serves only a square that would take both")
    void aPoolOfTwoKindsNeedsBoth() {
        MendLedger ledger = MendLedger.perGesture(1);
        MendLedger.Accepts both = taking("a", "b");
        MendLedger.Accepts onlyA = taking("a");

        assertEquals(1, ledger.owedBefore(SurfaceFamily.STONE, both));
        ledger.bought(SurfaceFamily.STONE, Arrays.asList("a", "b"));
        ledger.mended(SurfaceFamily.STONE, both);
        assertEquals(0, ledger.owedBefore(SurfaceFamily.STONE, both));
        assertEquals(1, ledger.owedBefore(SurfaceFamily.STONE, onlyA));
        assertEquals(1, ledger.blocksSpent());
    }

    @Test
    @DisplayName("pools priced by the gradation round separately, and a square draws on the earliest it accepts")
    void perGradationPoolsRoundSeparately() {
        MendLedger ledger = MendLedger.perGradations(4);
        MendLedger.Accepts sand = taking("sand");
        MendLedger.Accepts red = taking("red");
        MendLedger.Accepts either = taking("sand", "red");

        assertEquals(1, walk(ledger, SurfaceFamily.SAND, sand, "sand", 3));
        assertEquals(1, ledger.owedBefore(SurfaceFamily.SAND, red), "red sand does not draw on plain sand's block");
        assertEquals(1, walk(ledger, SurfaceFamily.SAND, red, "red", 1));
        assertEquals(2, ledger.blocksSpent());

        // One gradation is left in the sand pool and three in the red. A square that takes either
        // draws on the earlier, which empties it.
        assertEquals(0, ledger.owedBefore(SurfaceFamily.SAND, sand));
        ledger.mended(SurfaceFamily.SAND, either);
        assertEquals(1, ledger.owedBefore(SurfaceFamily.SAND, sand));
        assertEquals(0, walk(ledger, SurfaceFamily.SAND, red, "red", 3));
        assertEquals(1, ledger.owedBefore(SurfaceFamily.SAND, red));

        assertEquals(2, ledger.blocksSpent());
        assertEquals(8, ledger.paidGradations());
    }

    @Test
    @DisplayName("a golem at two blocks a purchase pays twice what a chunk tamper does over the same ground")
    void golemRateIsTwiceTheChunkTamper() {
        MendLedger golem = MendLedger.rate(2, 4);
        MendLedger player = MendLedger.perGradations(4);
        // A fifteen-square layer, one gradation deep: 225 gradations is 57 purchases, the last of them
        // paying for one gradation and three that nothing needed.
        for (int square = 0; square < 225; square++) {
            walk(golem, SurfaceFamily.GRASS, ALL, "dirt", 1);
            walk(player, SurfaceFamily.GRASS, ALL, "dirt", 1);
        }
        assertEquals(2, golem.price());
        assertEquals(114, golem.blocksSpent());
        assertEquals(225, golem.paidGradations());
        assertEquals(57, player.blocksSpent());
        assertEquals(225, player.paidGradations());
    }

    @Test
    @DisplayName("a stroke that finds a single gradation of some ground still pays a whole purchase")
    void aStrokeOfOneGradationPaysAWholePurchase() {
        // A ledger lasts one stroke, so nothing left over from this purchase reaches the next stroke. That
        // is why a golem mending ground a gradation or two at a time pays close to its full price for each.
        MendLedger golem = MendLedger.rate(2, 4);
        assertEquals(1, walk(golem, SurfaceFamily.GRASS, ALL, "dirt", 1));
        assertEquals(2, golem.blocksSpent());
        assertEquals(1, golem.paidGradations());

        MendLedger frugal = MendLedger.rate(1, 4);
        assertEquals(1, walk(frugal, SurfaceFamily.GRASS, ALL, "dirt", 1));
        assertEquals(1, frugal.blocksSpent());
        assertEquals(1, frugal.paidGradations());
    }

    @Test
    @DisplayName("a rate of one gradation to a purchase is the flat price a golem paid up to 0.9.205")
    void rateByOneGradationIsThe0_9_205Price() {
        MendLedger layer = MendLedger.rate(2, 1);
        for (int square = 0; square < 225; square++) walk(layer, SurfaceFamily.GRASS, ALL, "dirt", 1);
        assertEquals(450, layer.blocksSpent());
        assertEquals(225, layer.paidGradations());

        MendLedger deep = MendLedger.rate(2, 1);
        for (int square = 0; square < 25; square++) walk(deep, SurfaceFamily.GRASS, ALL, "dirt", 8);
        assertEquals(400, deep.blocksSpent());
        assertEquals(200, deep.paidGradations());
    }

    @Test
    @DisplayName("a frugal golem, at one block a purchase, pays exactly what a player's chunk tamper does")
    void frugalGolemMatchesThePlayer() {
        MendLedger frugal = MendLedger.rate(1, 4);
        MendLedger player = MendLedger.perGradations(4);
        // A fifteen-square layer eight gradations deep: 1800 gradations, which four divides exactly.
        for (int square = 0; square < 225; square++) {
            walk(frugal, SurfaceFamily.STONE, ALL, "stone", 8);
            walk(player, SurfaceFamily.STONE, ALL, "stone", 8);
        }
        assertEquals(450, frugal.blocksSpent());
        assertEquals(450, player.blocksSpent());
        assertEquals(1800, frugal.paidGradations());
        assertEquals(1800, player.paidGradations());
    }

    @Test
    @DisplayName("a rate with either figure below one is held at one, never read as free")
    void rateIsHeldAtOne() {
        for (int[] figures : new int[][] { { 0, 0 }, { -3, -1 } }) {
            String name = "rate(" + figures[0] + ", " + figures[1] + ")";
            MendLedger ledger = MendLedger.rate(figures[0], figures[1]);
            assertFalse(ledger.isFree(), name);
            assertEquals(1, ledger.price(), name);
            assertEquals(3, walk(ledger, SurfaceFamily.SAND, ALL, "sand", 3), name);
            assertEquals(3, ledger.blocksSpent(), name);
            assertEquals(3, ledger.paidGradations(), name);
        }

        // Each figure is held on its own: a price of nought still buys the rate asked for, and a rate of
        // nought still charges the price asked for, once for every gradation.
        MendLedger noBlocks = MendLedger.rate(0, 4);
        assertEquals(1, noBlocks.price());
        assertEquals(1, walk(noBlocks, SurfaceFamily.SAND, ALL, "sand", 3));
        assertEquals(1, noBlocks.blocksSpent());

        MendLedger noGradations = MendLedger.rate(3, 0);
        assertEquals(3, noGradations.price());
        assertEquals(3, walk(noGradations, SurfaceFamily.SAND, ALL, "sand", 3));
        assertEquals(9, noGradations.blocksSpent());
    }

    @Test
    @DisplayName("a price by the gradation is a rate of one block, figure for figure")
    void perGradationsIsRateOfOneBlock() {
        // A hundred squares three deep is 300 gradations: at 16 to a block that is 18 whole blocks and
        // twelve gradations over, so 19; at 256 it is one whole block and 44 over, so 2.
        int[] rates = { 0, 1, 4, 16, 256 };
        int[] spent = { 300, 300, 75, 19, 2 };
        for (int i = 0; i < rates.length; i++) {
            String name = "gradations per block " + rates[i];
            MendLedger before = MendLedger.perGradations(rates[i]);
            MendLedger rated = MendLedger.rate(1, rates[i]);
            for (int square = 0; square < 100; square++) {
                walk(before, SurfaceFamily.DIRT, ALL, "dirt", 3);
                walk(rated, SurfaceFamily.DIRT, ALL, "dirt", 3);
            }
            assertEquals(spent[i], before.blocksSpent(), name);
            assertEquals(spent[i], rated.blocksSpent(), name);
            assertEquals(1, before.price(), name);
            assertEquals(1, rated.price(), name);
            assertEquals(300, before.paidGradations(), name);
            assertEquals(300, rated.paidGradations(), name);
        }
    }

    @Test
    @DisplayName("a golem's purchases round for each kind of ground on its own, however the kinds are interleaved")
    void golemPoolsRoundPerKind() {
        MendLedger ledger = MendLedger.rate(2, 4);
        MendLedger.Accepts grass = taking("dirt");
        MendLedger.Accepts cobble = taking("cobble");

        // The walk, in this order, one gradation a square: grass, cobble, grass, cobble, grass, cobble,
        // grass, grass. Each purchase opens a pool of four gradations for two blocks, and a pool is
        // dropped the moment its last gradation is drawn.
        // Grass buys its first pool and draws on it: three left.
        assertEquals(1, walk(ledger, SurfaceFamily.GRASS, grass, "dirt", 1));
        // Cobble cannot draw on grass's pool, so it buys its own: three left.
        assertEquals(1, walk(ledger, SurfaceFamily.COBBLE, cobble, "cobble", 1));
        // Grass two left, cobble two left, grass one left, cobble one left.
        assertEquals(0, walk(ledger, SurfaceFamily.GRASS, grass, "dirt", 1));
        assertEquals(0, walk(ledger, SurfaceFamily.COBBLE, cobble, "cobble", 1));
        assertEquals(0, walk(ledger, SurfaceFamily.GRASS, grass, "dirt", 1));
        assertEquals(0, walk(ledger, SurfaceFamily.COBBLE, cobble, "cobble", 1));
        // Grass's fourth gradation empties its pool, which is dropped.
        assertEquals(0, walk(ledger, SurfaceFamily.GRASS, grass, "dirt", 1));
        // So the fifth buys a second grass pool: three left.
        assertEquals(1, walk(ledger, SurfaceFamily.GRASS, grass, "dirt", 1));

        assertEquals(6, ledger.blocksSpent());
        assertEquals(8, ledger.paidGradations());
        assertEquals(0, ledger.owedBefore(SurfaceFamily.COBBLE, cobble), "one gradation of cobble is still paid for");
        assertEquals(0, ledger.owedBefore(SurfaceFamily.GRASS, grass), "three gradations of grass are still paid for");

        // On a fresh ledger, exactly four gradations of grass spend its one pool and buy nothing for cobble.
        MendLedger fresh = MendLedger.rate(2, 4);
        int purchases = 0;
        for (int square = 0; square < 4; square++) purchases += walk(fresh, SurfaceFamily.GRASS, grass, "dirt", 1);
        assertEquals(1, purchases);
        assertEquals(2, fresh.blocksSpent());
        assertEquals(2, fresh.owedBefore(SurfaceFamily.GRASS, grass), "the spent pool was dropped");
        assertEquals(2, fresh.owedBefore(SurfaceFamily.COBBLE, cobble), "grass bought nothing for cobble");
    }

    @Test
    @DisplayName("a price of many blocks to a purchase is expressible, and rounds the same way")
    void aLargeMultipleIsExpressible() {
        MendLedger ledger = MendLedger.rate(16, 4);
        for (int square = 0; square < 225; square++) walk(ledger, SurfaceFamily.GRASS, ALL, "dirt", 1);
        assertEquals(16, ledger.price());
        assertEquals(912, ledger.blocksSpent());
        assertEquals(225, ledger.paidGradations());
    }
}
