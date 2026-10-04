package com.trmtgtnh.client.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What moving the layer behind a worn surface makes the game keep, and whether the budget has room for it, pinned by
 * figures worked out by hand.
 *
 * <p>
 * The budget used to charge a surface for its frames alone while its setting promised the shells as well, and nothing
 * could fail a test over it. The figures are the settings shipped - sixteen pixels, eighty gradations and four
 * rotations, so three hundred and twenty pictures a surface, at four mip levels - and the liquids as the game ships
 * them, lava twenty images and water thirty-two. Chisel's fifteen faces are eight lavastones and seven waterstones.
 */
class MovingLayerLedgerTest {

    /** This many pictures, every one loaded at this edge. */
    private static MovingLayerLedger.Pictures set(int edge, int pictures) {
        MovingLayerLedger.Pictures set = new MovingLayerLedger.Pictures();
        set.add(edge, pictures);
        return set;
    }

    /** A lavastone at the settings shipped, every picture at the edge its twenty frames were cut to. */
    private static MovingLayerLedger.Price lava(int edge) {
        return MovingLayerLedger.price(set(edge, 320), 20, edge, 4);
    }

    /** A waterstone at the settings shipped, every picture at the edge its thirty-two frames were cut to. */
    private static MovingLayerLedger.Price water(int edge) {
        return MovingLayerLedger.price(set(edge, 320), 32, edge, 4);
    }

    /** A sixteen-pixel still picture with its four mip levels, sized as vanilla's generator sizes them. */
    private static int[][] mipChain16() {
        return new int[][] { new int[256], new int[64], new int[16], new int[4], new int[1] };
    }

    /** This many sixteen-pixel frames, as a set of their own. */
    private static int[][] frames(int count) {
        int[][] frames = new int[count][];
        for (int i = 0; i < count; i++) {
            frames[i] = new int[256];
        }
        return frames;
    }

    // ------------------------------------------------------------------
    // Prices
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a budget is whole megabytes, and nought or less is nothing")
    void budgetIsWholeMebibytesAndNoughtOrLessIsNothing() {
        assertEquals(67108864L, MovingLayerLedger.budgetBytes(64), "the default");
        assertEquals(536870912L, MovingLayerLedger.budgetBytes(512));
        assertEquals(1048576L, MovingLayerLedger.budgetBytes(1));
        assertEquals(0L, MovingLayerLedger.budgetBytes(0));
        assertEquals(0L, MovingLayerLedger.budgetBytes(-1));
        assertEquals(2251799812636672L, MovingLayerLedger.budgetBytes(Integer.MAX_VALUE), "multiplied as a long");
    }

    @Test
    @DisplayName("a picture and its frames are four bytes a pixel")
    void aPictureAndItsFramesAreFourBytesAPixel() {
        assertEquals(1024L, MovingLayerLedger.pictureBytes(16));
        assertEquals(4096L, MovingLayerLedger.pictureBytes(32));
        assertEquals(0L, MovingLayerLedger.pictureBytes(0));
        assertEquals(0L, MovingLayerLedger.pictureBytes(-16));

        assertEquals(20480L, MovingLayerLedger.framesBytes(20, 16), "lava");
        assertEquals(32768L, MovingLayerLedger.framesBytes(32, 16), "water");
        assertEquals(81920L, MovingLayerLedger.framesBytes(20, 32));
        assertEquals(0L, MovingLayerLedger.framesBytes(0, 16));
        assertEquals(0L, MovingLayerLedger.framesBytes(-20, 16));
        assertEquals(0L, MovingLayerLedger.framesBytes(20, 0));
        assertEquals(
            8589953124L,
            MovingLayerLedger.framesBytes(1, 46341),
            "an edge whose square is past what an int holds");
    }

    @Test
    @DisplayName("the still picture keeps every mip level vanilla makes of it")
    void theStillPictureKeepsEveryMipLevelVanillaMakes() {
        assertEquals(1364L, MovingLayerLedger.keptBytes(16, 4), "256, 64, 16, 4 and 1 pixels");
        assertEquals(1024L, MovingLayerLedger.keptBytes(16, 0), "mipmapping off keeps the picture alone");
        assertEquals(1344L, MovingLayerLedger.keptBytes(16, 2));
        assertEquals(1024L, MovingLayerLedger.keptBytes(16, -1));
        assertEquals(5456L, MovingLayerLedger.keptBytes(32, 4));
        assertEquals(21824L, MovingLayerLedger.keptBytes(64, 4));

        assertEquals(
            3068L,
            MovingLayerLedger.keptBytes(24, 4),
            "576, 144, 36, 9 and 2 pixels: a quarter the length, rounded down");
        assertEquals(2124L, MovingLayerLedger.keptBytes(20, 3), "400, 100, 25 and 6 pixels");
        assertEquals(20L, MovingLayerLedger.keptBytes(2, 4), "4 and 1 pixels, then nothing");
        assertEquals(20L, MovingLayerLedger.keptBytes(2, 30), "levels past the last pixel add nothing");

        assertEquals(0L, MovingLayerLedger.keptBytes(0, 4));
        assertEquals(0L, MovingLayerLedger.keptBytes(-16, 4));
    }

    @Test
    @DisplayName("a picture with no size yet is not a picture")
    void aPictureWithNoSizeYetIsNotAPicture() {
        MovingLayerLedger.Pictures none = new MovingLayerLedger.Pictures();
        none.add(0);
        none.add(-16);
        none.add(16, 0);
        none.add(16, -3);
        assertEquals(0, none.count());

        MovingLayerLedger.Price price = MovingLayerLedger.price(none, 20, 16, 4);
        assertEquals(0, price.pictures);
        assertEquals(0L, price.total(), "not even the frames, which no picture would share");
        assertEquals(
            0L,
            MovingLayerLedger.price(null, 20, 16, 4)
                .total());

        MovingLayerLedger ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(64));
        assertEquals(MovingLayerLedger.Answer.NOTHING_TO_MOVE, ledger.ask("lava_still", price));
        assertEquals(MovingLayerLedger.Answer.NOTHING_TO_MOVE, ledger.ask("lava_still", null));
        assertEquals(0, ledger.surfacesGranted());
        assertEquals(0, ledger.surfacesRefused());
        assertEquals(0L, ledger.granted());

        MovingLayerLedger nought = new MovingLayerLedger(MovingLayerLedger.budgetBytes(0));
        assertEquals(MovingLayerLedger.Answer.NOTHING_TO_MOVE, nought.ask("lava_still", price));
        assertEquals(0, nought.declinedAtNought(), "a surface with no sized picture is not counted against nought");
    }

    @Test
    @DisplayName("a sixteen-pixel lava surface")
    void aSixteenPixelLavaSurface() {
        MovingLayerLedger.Price lava = lava(16);
        assertEquals(320, lava.pictures);
        assertEquals(327680L, lava.shells, "320 shells of 256 pixels");
        assertEquals(436480L, lava.kept, "320 still pictures of 341 pixels with their mip levels");
        assertEquals(20480L, lava.frames, "twenty frames, once");
        assertEquals(0L, lava.copies);
        assertEquals(784640L, lava.total());
    }

    @Test
    @DisplayName("a sixteen-pixel water surface")
    void aSixteenPixelWaterSurface() {
        MovingLayerLedger.Price water = water(16);
        assertEquals(320, water.pictures);
        assertEquals(327680L, water.shells);
        assertEquals(436480L, water.kept);
        assertEquals(32768L, water.frames, "thirty-two frames, once");
        assertEquals(0L, water.copies);
        assertEquals(796928L, water.total());
    }

    @Test
    @DisplayName("at thirty-two pixels a surface costs four times as much")
    void atThirtyTwoPixelsASurfaceCostsFourTimesAsMuch() {
        MovingLayerLedger.Price lava = lava(32);
        assertEquals(1310720L, lava.shells);
        assertEquals(1745920L, lava.kept);
        assertEquals(81920L, lava.frames);
        assertEquals(0L, lava.copies);
        assertEquals(3138560L, lava.total());
        assertEquals(4L * lava(16).total(), lava.total());

        MovingLayerLedger.Price water = water(32);
        assertEquals(131072L, water.frames);
        assertEquals(3187712L, water.total());
        assertEquals(4L * water(16).total(), water.total());
    }

    @Test
    @DisplayName("a picture loaded at another size from the frames keeps a copy of them of its own")
    void aPictureLoadedAtAnotherSizeKeepsItsOwnFrames() {
        MovingLayerLedger.Pictures halves = set(16, 160);
        halves.add(32, 160);
        assertEquals(320, halves.count());
        MovingLayerLedger.Price price = MovingLayerLedger.price(halves, 20, 32, 4);
        assertEquals(320, price.pictures);
        assertEquals(819200L, price.shells, "160 shells of 256 pixels and 160 of 1,024");
        assertEquals(1091200L, price.kept);
        assertEquals(81920L, price.frames, "the thirty-two pixel half shares the frames as cut");
        assertEquals(3276800L, price.copies, "each sixteen-pixel picture keeps twenty frames of its own");
        assertEquals(5269120L, price.total());

        MovingLayerLedger.Pictures oneByOne = new MovingLayerLedger.Pictures();
        for (int i = 0; i < 160; i++) {
            oneByOne.add(32);
            oneByOne.add(16);
        }
        MovingLayerLedger.Price same = MovingLayerLedger.price(oneByOne, 20, 32, 4);
        assertEquals(320, oneByOne.count());
        assertEquals(price.copies, same.copies);
        assertEquals(price.total(), same.total(), "filed one at a time, the other size first");

        MovingLayerLedger.Pictures three = set(16, 1);
        three.add(32);
        three.add(64);
        MovingLayerLedger.Price threeSizes = MovingLayerLedger.price(three, 20, 32, 0);
        assertEquals(3, threeSizes.pictures);
        assertEquals(21504L, threeSizes.shells, "a third size, past the room the count began with");
        assertEquals(21504L, threeSizes.kept, "no mip levels");
        assertEquals(81920L, threeSizes.frames);
        assertEquals(348160L, threeSizes.copies, "twenty frames at sixteen pixels and twenty at sixty-four");
        assertEquals(473088L, threeSizes.total());
    }

    @Test
    @DisplayName("frames no picture shares are not priced")
    void framesNoPictureSharesAreNotPriced() {
        MovingLayerLedger.Price price = MovingLayerLedger.price(set(16, 320), 20, 32, 4);
        assertEquals(327680L, price.shells);
        assertEquals(436480L, price.kept);
        assertEquals(0L, price.frames, "the set the surface was harvested with is let go of once the pass ends");
        assertEquals(6553600L, price.copies);
        assertEquals(7317760L, price.total());
    }

    @Test
    @DisplayName("the pictures follow gradations and rotations, and the frames are priced once however many")
    void picturesFollowGradationsAndRotations() {
        MovingLayerLedger.Price sixteenAtFour = MovingLayerLedger.price(set(16, 64), 20, 16, 4);
        assertEquals(65536L, sixteenAtFour.shells);
        assertEquals(87296L, sixteenAtFour.kept);
        assertEquals(20480L, sixteenAtFour.frames);
        assertEquals(173312L, sixteenAtFour.total(), "sixteen gradations at four rotations");

        MovingLayerLedger.Price eightyAtOne = MovingLayerLedger.price(set(16, 80), 20, 16, 4);
        assertEquals(20480L, eightyAtOne.frames);
        assertEquals(211520L, eightyAtOne.total(), "eighty gradations at one rotation");

        assertEquals(
            320L * (MovingLayerLedger.pictureBytes(16) + MovingLayerLedger.keptBytes(16, 4)),
            lava(16).total() - lava(16).frames,
            "a shell and a still picture for every one of the 320");
    }

    // ------------------------------------------------------------------
    // The ledger
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Chisel's fifteen fit the default budget, and the old charge saw one byte in thirty of them")
    void chiselsFifteenFitTheDefaultBudgetAndTheOldChargeSawOneByteInThirty() {
        MovingLayerLedger ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(64));
        long frames = 0L;
        for (int i = 0; i < 8; i++) {
            MovingLayerLedger.Price lava = lava(16);
            frames += lava.frames;
            assertEquals(MovingLayerLedger.Answer.GRANTED, ledger.ask("lava_still", lava), "lavastone " + i);
        }
        for (int i = 0; i < 7; i++) {
            MovingLayerLedger.Price water = water(16);
            frames += water.frames;
            assertEquals(MovingLayerLedger.Answer.GRANTED, ledger.ask("water_still", water), "waterstone " + i);
        }
        assertEquals(15, ledger.surfacesGranted());
        assertEquals(4800, ledger.picturesGranted());
        assertEquals(11855616L, ledger.granted());
        assertEquals(0, ledger.surfacesRefused());
        assertEquals(0L, ledger.cheapestRefused(), "none was refused");
        assertEquals("", ledger.describeRefusals());

        assertEquals(393216L, frames, "all 0.9.210 charged: the 384 KiB its stitch line gave");
        assertTrue(frames * 30L < ledger.granted(), "more than thirty times what was charged");
        assertTrue(ledger.granted() < frames * 31L, "and less than thirty-one times");
    }

    @Test
    @DisplayName("a surface moves whole or not at all")
    void aSurfaceMovesWholeOrNotAtAll() {
        MovingLayerLedger ledger = new MovingLayerLedger(1048576L);
        assertEquals(MovingLayerLedger.Answer.GRANTED, ledger.ask("lava_still", lava(16)));
        assertEquals(
            MovingLayerLedger.Answer.REFUSED,
            ledger.ask("water_still", water(16)),
            "263,936 bytes left, and the surface needs 796,928");
        assertEquals(784640L, ledger.granted(), "nothing of the refused surface was granted");
        assertEquals(1, ledger.surfacesGranted());
        assertEquals(320, ledger.picturesGranted());
        assertEquals(1, ledger.surfacesRefused());
        assertEquals(796928L, ledger.refusedBytes());
        assertEquals(2L, ledger.everythingAskedMebibytes(), "1,581,568 bytes, rounded up");
    }

    @Test
    @DisplayName("a refusal does not stop a cheaper surface after it")
    void aRefusalDoesNotStopACheaperSurfaceAfterIt() {
        MovingLayerLedger ledger = new MovingLayerLedger(1048576L);
        assertEquals(MovingLayerLedger.Answer.REFUSED, ledger.ask("lava_still", lava(32)));
        assertEquals(MovingLayerLedger.Answer.GRANTED, ledger.ask("lava_still", lava(16)));
        assertEquals(784640L, ledger.granted());
        assertEquals(1, ledger.surfacesGranted());
        assertEquals(1, ledger.surfacesRefused());
        assertEquals(3138560L, ledger.cheapestRefused());
    }

    @Test
    @DisplayName("a surface exactly filling the budget is granted, and nothing past it is")
    void aSurfaceExactlyFillingTheBudgetIsGranted() {
        MovingLayerLedger ledger = new MovingLayerLedger(784640L);
        assertEquals(MovingLayerLedger.Answer.GRANTED, ledger.ask("lava_still", lava(16)));
        MovingLayerLedger.Price tiny = MovingLayerLedger.price(set(1, 1), 2, 1, 0);
        assertEquals(16L, tiny.total(), "one pixel of shell, one of still picture and two of frames");
        assertEquals(MovingLayerLedger.Answer.REFUSED, ledger.ask("tiny", tiny));
        assertEquals(784640L, ledger.granted());
        assertEquals(16L, ledger.cheapestRefused());
    }

    @Test
    @DisplayName("a budget of nought declines every surface, and counts them")
    void aBudgetOfNoughtDeclinesEverySurfaceAndCountsThem() {
        MovingLayerLedger ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(0));
        assertEquals(0L, ledger.budget());
        assertEquals(MovingLayerLedger.Answer.NOUGHT, ledger.ask("lava_still", lava(16)));
        assertEquals(MovingLayerLedger.Answer.NOUGHT, ledger.ask("water_still", water(16)));
        assertEquals(2, ledger.declinedAtNought());
        assertEquals(0L, ledger.granted());
        assertEquals(0, ledger.surfacesGranted());
        assertEquals(0, ledger.surfacesRefused(), "not refused for want of room, which the log words differently");
        assertEquals(0L, ledger.refusedBytes());
        assertEquals("", ledger.describeRefusals());

        assertEquals(0L, new MovingLayerLedger(-5L).budget(), "a budget below nought is nought");
    }

    @Test
    @DisplayName("a budget below the price of one surface moves nothing, and the budget it recommends does")
    void aBudgetBelowOneSurfaceMovesNothing() {
        MovingLayerLedger two = new MovingLayerLedger(MovingLayerLedger.budgetBytes(2));
        assertEquals(MovingLayerLedger.Answer.REFUSED, two.ask("lava_still", lava(32)));
        assertEquals(0, two.surfacesGranted());
        assertEquals(3138560L, two.cheapestRefused());
        assertEquals(3L, two.everythingAskedMebibytes());

        MovingLayerLedger three = new MovingLayerLedger(
            MovingLayerLedger.budgetBytes((int) two.everythingAskedMebibytes()));
        assertEquals(MovingLayerLedger.Answer.GRANTED, three.ask("lava_still", lava(32)));
    }

    @Test
    @DisplayName("refusals are told by texture, in the order met")
    void refusalsAreToldByTextureInTheOrderMet() {
        MovingLayerLedger ledger = new MovingLayerLedger(1048576L);
        assertEquals(MovingLayerLedger.Answer.REFUSED, ledger.ask("lava_still", lava(32)));
        assertEquals(MovingLayerLedger.Answer.REFUSED, ledger.ask("lava_still", lava(32)));
        assertEquals(MovingLayerLedger.Answer.REFUSED, ledger.ask("water_still", water(32)));
        assertEquals(
            "lava_still on 2 surfaces (6130 KiB), water_still on 1 surface (3113 KiB)",
            ledger.describeRefusals());
        assertEquals(3, ledger.surfacesRefused());
        assertEquals(9464832L, ledger.refusedBytes());
        assertEquals(10L, ledger.everythingAskedMebibytes());

        MovingLayerLedger waterFirst = new MovingLayerLedger(1048576L);
        waterFirst.ask("water_still", water(32));
        waterFirst.ask("lava_still", lava(32));
        assertEquals(
            "water_still on 1 surface (3113 KiB), lava_still on 1 surface (3065 KiB)",
            waterFirst.describeRefusals());
    }

    @Test
    @DisplayName("at sixty-four pixels the default budget moves five lavastones")
    void atSixtyFourPixelsTheDefaultBudgetMovesFiveLavastones() {
        assertEquals(12554240L, lava(64).total());
        assertEquals(12750848L, water(64).total());

        MovingLayerLedger ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(64));
        for (int i = 0; i < 8; i++) {
            assertEquals(
                i < 5 ? MovingLayerLedger.Answer.GRANTED : MovingLayerLedger.Answer.REFUSED,
                ledger.ask("lava_still", lava(64)),
                "lavastone " + i);
        }
        for (int i = 0; i < 7; i++) {
            assertEquals(MovingLayerLedger.Answer.REFUSED, ledger.ask("water_still", water(64)), "waterstone " + i);
        }
        assertEquals(5, ledger.surfacesGranted());
        assertEquals(10, ledger.surfacesRefused());
        assertEquals(62771200L, ledger.granted());
        assertEquals(12554240L, ledger.cheapestRefused());
        assertEquals(
            "lava_still on 3 surfaces (36780 KiB), water_still on 7 surfaces (87164 KiB)",
            ledger.describeRefusals());
        assertEquals(181L, ledger.everythingAskedMebibytes(), "189,689,856 bytes for all fifteen");
    }

    // ------------------------------------------------------------------
    // Holdings
    // ------------------------------------------------------------------

    @Test
    @DisplayName("what a picture takes is what it was priced at")
    void whatAPictureTakesIsWhatItWasPriced() {
        MovingLayerLedger.Price lava = lava(16);
        MovingLayerLedger ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(64));
        assertEquals(MovingLayerLedger.Answer.GRANTED, ledger.ask("lava_still", lava));
        int[][] shared = frames(20);
        for (int i = 0; i < 320; i++) {
            ledger.took(new int[256], mipChain16(), shared, false);
        }
        assertEquals(327680L, ledger.shellsHeld());
        assertEquals(436480L, ledger.keptHeld());
        assertEquals(20480L, ledger.framesHeld(), "one set, shared by all 320");
        assertEquals(0L, ledger.copiesHeld());
        assertEquals(784640L, ledger.held());
        assertEquals(lava.shells, ledger.shellsHeld());
        assertEquals(lava.kept, ledger.keptHeld());
        assertEquals(lava.frames, ledger.framesHeld());
        assertEquals(ledger.granted(), ledger.held());
        assertFalse(ledger.overdrawn());
        assertEquals(0L, ledger.givenBack());
        assertEquals(320, ledger.picturesHolding());
        assertEquals(0, ledger.picturesNotTaken());

        long before = ledger.held();
        ledger.took(new int[256], mipChain16(), frames(20), true);
        assertEquals(22868L, ledger.held() - before, "a shell, a still picture and twenty frames of its own");
        assertEquals(
            MovingLayerLedger.pictureBytes(16) + MovingLayerLedger.keptBytes(16, 4)
                + MovingLayerLedger.framesBytes(20, 16),
            ledger.held() - before);
        assertEquals(20480L, ledger.copiesHeld());
        assertEquals(20480L, ledger.framesHeld(), "a copy of its own is not the shared set");
        assertTrue(ledger.overdrawn(), "one picture more than was priced");
    }

    @Test
    @DisplayName("pictures that never took their layer are given back")
    void picturesThatNeverTookTheirLayerAreGivenBack() {
        MovingLayerLedger ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(64));
        assertEquals(MovingLayerLedger.Answer.GRANTED, ledger.ask("lava_still", lava(16)));
        int[][] shared = frames(20);
        for (int i = 0; i < 319; i++) {
            ledger.took(new int[256], mipChain16(), shared, false);
        }
        assertEquals(782252L, ledger.held());
        assertFalse(ledger.overdrawn());
        assertEquals(2388L, ledger.givenBack(), "one shell and one still picture");
        assertEquals(1, ledger.picturesNotTaken());
    }

    @Test
    @DisplayName("holding more than was granted is overdrawn")
    void holdingMoreThanWasGrantedIsOverdrawn() {
        MovingLayerLedger ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(64));
        assertEquals(MovingLayerLedger.Answer.GRANTED, ledger.ask("lava_still", lava(16)));
        int[][] shared = frames(20);
        for (int i = 0; i < 321; i++) {
            ledger.took(new int[256], mipChain16(), shared, false);
        }
        assertEquals(787028L, ledger.held());
        assertTrue(ledger.overdrawn());
        assertEquals(0L, ledger.givenBack());
        assertEquals(0, ledger.picturesNotTaken(), "never below nought");
    }

    @Test
    @DisplayName("shared frames are held once, and let go of when the book closes")
    void sharedFramesAreHeldOnceAndLetGoOfWhenTheBookCloses() {
        MovingLayerLedger ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(64));
        int[][] first = frames(20);
        int[][] second = frames(20);
        ledger.took(new int[256], mipChain16(), first, false);
        ledger.took(new int[256], mipChain16(), first, false);
        assertEquals(20480L, ledger.framesHeld(), "two pictures sharing one set count it once");
        ledger.took(new int[256], mipChain16(), second, false);
        assertEquals(40960L, ledger.framesHeld(), "an equal set that is another array is another set");
        assertEquals(0L, ledger.copiesHeld());

        long held = ledger.held();
        ledger.closeBook();
        assertEquals(held, ledger.held(), "closing the book forgets the arrays and keeps the figures");
        assertEquals(40960L, ledger.framesHeld());
        ledger.took(new int[256], mipChain16(), first, false);
        assertEquals(61440L, ledger.framesHeld(), "an array handed in after the book closed is counted again");

        long before = ledger.held();
        ledger.took(null, null, null, false);
        ledger.took(null, null, null, true);
        assertEquals(before, ledger.held(), "a picture that hands in nothing adds nothing but itself");
        assertEquals(6, ledger.picturesHolding());
    }

    @Test
    @DisplayName("bytesOf counts only what is there")
    void bytesOfCountsOnlyWhatIsThere() {
        assertEquals(0L, MovingLayerLedger.bytesOf((int[]) null));
        assertEquals(1024L, MovingLayerLedger.bytesOf(new int[256]));
        assertEquals(1088L, MovingLayerLedger.bytesOf(new int[][] { new int[256], null, new int[16] }));
        assertEquals(0L, MovingLayerLedger.bytesOf((int[][]) null));
        assertEquals(0L, MovingLayerLedger.bytesOf(new int[0][]));
        assertEquals(
            MovingLayerLedger.keptBytes(16, 4),
            MovingLayerLedger.bytesOf(mipChain16()),
            "the still picture as vanilla keeps it is what keptBytes prices");
    }

    @Test
    @DisplayName("figures for the log round kibibytes down and a recommended budget up")
    void figuresForTheLog() {
        assertEquals(0L, MovingLayerLedger.kibibytes(1023L));
        assertEquals(1L, MovingLayerLedger.kibibytes(1024L));
        assertEquals(766L, MovingLayerLedger.kibibytes(784640L));
        assertEquals(11577L, MovingLayerLedger.kibibytes(11855616L), "the figure the stitch line gives for Chisel");

        assertEquals(0L, MovingLayerLedger.mebibytesUp(0L));
        assertEquals(0L, MovingLayerLedger.mebibytesUp(-1L));
        assertEquals(1L, MovingLayerLedger.mebibytesUp(1L));
        assertEquals(1L, MovingLayerLedger.mebibytesUp(1048576L));
        assertEquals(2L, MovingLayerLedger.mebibytesUp(1048577L));
        assertEquals(3L, MovingLayerLedger.mebibytesUp(3138560L));
    }

    @Test
    @DisplayName("large figures do not overflow")
    void largeFiguresDoNotOverflow() {
        MovingLayerLedger.Price huge = MovingLayerLedger.price(set(8192, 262144), 512, 4096, 0);
        assertEquals(262144, huge.pictures);
        assertEquals(1L << 46, huge.shells);
        assertEquals(1L << 46, huge.kept);
        assertEquals(0L, huge.frames);
        assertEquals(1L << 55, huge.copies, "262,144 pictures, each keeping 512 frames of 67,108,864 pixels");
        assertEquals((1L << 55) + (1L << 47), huge.total());

        MovingLayerLedger ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(512));
        assertEquals(MovingLayerLedger.Answer.REFUSED, ledger.ask("huge", huge));
        assertEquals(0L, ledger.granted());
        assertEquals((1L << 55) + (1L << 47), ledger.refusedBytes());
        assertEquals((1L << 35) + (1L << 27), ledger.everythingAskedMebibytes());
    }

    // ------------------------------------------------------------------
    // Uploads
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nothing is taken or counted before the first tick")
    void nothingIsTakenOrCountedBeforeTheFirstTick() {
        MovingLayerLedger.UploadTally tally = new MovingLayerLedger.UploadTally();
        assertFalse(tally.take(), "no allowance yet");
        assertFalse(tally.newTick(2), "and a picture refused before the first tick was not refused by the ceiling");
        assertTrue(tally.take());
        assertTrue(tally.take());
        assertFalse(tally.newTick(2), "two asked of two");
    }

    @Test
    @DisplayName("the ceiling turns away what is over it, and says so")
    void theCeilingTurnsAwayWhatIsOverItAndSaysSo() {
        MovingLayerLedger.UploadTally tally = new MovingLayerLedger.UploadTally();
        assertFalse(tally.newTick(2));
        assertTrue(tally.take());
        assertTrue(tally.take());
        assertFalse(tally.take());
        assertTrue(tally.newTick(2));
        assertEquals(3, tally.lastAsked());
        assertEquals(2, tally.lastAllowed());
        assertEquals(1, tally.lastTurnedAway());
    }

    @Test
    @DisplayName("a tick that turns nothing away says nothing")
    void aTickThatTurnsNothingAwaySaysNothing() {
        MovingLayerLedger.UploadTally tally = new MovingLayerLedger.UploadTally();
        tally.newTick(4);
        for (int i = 0; i < 4; i++) {
            assertTrue(tally.take(), "picture " + i);
        }
        assertFalse(tally.newTick(4));
        assertEquals(0, tally.lastAsked(), "no figures kept for a tick not said");
    }

    @Test
    @DisplayName("the line is said once a stitch")
    void theLineIsSaidOncePerStitch() {
        MovingLayerLedger.UploadTally tally = new MovingLayerLedger.UploadTally();
        tally.newTick(1);
        assertTrue(tally.take());
        assertFalse(tally.take());
        assertTrue(tally.newTick(1));
        assertEquals(2, tally.lastAsked());

        assertTrue(tally.take());
        assertFalse(tally.take());
        assertFalse(tally.take());
        assertFalse(tally.newTick(1), "said already this stitch");
        assertEquals(2, tally.lastAsked(), "the figures kept are the tick that was said");

        tally.forget();
        assertTrue(tally.take());
        assertFalse(tally.take());
        assertFalse(tally.take());
        assertTrue(tally.newTick(1), "a new stitch says it again");
        assertEquals(3, tally.lastAsked());
        assertEquals(1, tally.lastAllowed());
        assertEquals(2, tally.lastTurnedAway());
    }

    @Test
    @DisplayName("a reload part way through a tick drops the old atlas's asks rather than saying them as the new stitch's")
    void aReloadDropsTheOldAtlasTick() {
        MovingLayerLedger.UploadTally tally = new MovingLayerLedger.UploadTally();
        tally.newTick(1);
        tally.take();
        tally.take();
        tally.take();
        tally.forget();
        assertFalse(tally.newTick(1), "the old atlas's tick is not said as the new stitch's");

        assertTrue(tally.take());
        assertFalse(tally.take());
        assertFalse(tally.take());
        assertTrue(tally.newTick(1), "the new stitch's own first overrun is said");
        assertEquals(3, tally.lastAsked());
        assertEquals(1, tally.lastAllowed());
        assertEquals(2, tally.lastTurnedAway());
    }

    @Test
    @DisplayName("an allowance below nought is nought")
    void aNegativeAllowanceIsNought() {
        MovingLayerLedger.UploadTally tally = new MovingLayerLedger.UploadTally();
        assertFalse(tally.newTick(-5));
        assertFalse(tally.take(), "nothing may be redrawn");
        assertTrue(tally.newTick(-5), "and the picture turned away is said");
        assertEquals(0, tally.lastAllowed());
        assertEquals(1, tally.lastAsked());
        assertEquals(1, tally.lastTurnedAway());
    }

    // ------------------------------------------------------------------
    // The text's figures
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the figures the settings and the javadocs give hold")
    void theFiguresTheCommentsGiveHold() {
        long megabyte = MovingLayerLedger.MEBIBYTE;
        long default64 = MovingLayerLedger.budgetBytes(64);

        long lavastone = lava(16).total();
        assertTrue(lavastone <= megabyte * 3L / 4L, "one lavastone comes to three quarters of a megabyte");

        long chisel = 8L * lava(16).total() + 7L * water(16).total();
        assertEquals(11855616L, chisel);
        assertTrue(chisel > 11L * megabyte, "Chisel's fifteen come to a little over eleven megabytes");
        assertTrue(chisel < 11L * megabyte + megabyte / 2L, "and not eleven and a half");
        assertTrue(5L * chisel < default64, "the default has room for more than five times that");

        long chiselAtThirtyTwo = 8L * lava(32).total() + 7L * water(32).total();
        assertEquals(47422464L, chiselAtThirtyTwo);
        assertTrue(
            chiselAtThirtyTwo > 44L * megabyte && chiselAtThirtyTwo < 46L * megabyte,
            "about forty-five at thirty-two pixels");

        long lavastoneAtThirtyTwo = lava(32).total();
        assertTrue(
            lavastoneAtThirtyTwo > MovingLayerLedger.budgetBytes(2),
            "a budget of two moves nothing on such a pack");
        assertTrue(
            lavastoneAtThirtyTwo <= MovingLayerLedger.budgetBytes(3),
            "a thirty-two pixel lavastone needs just under three megabytes");

        // WearSprite's compose and updateAnimation: a finished picture with its mip levels for every frame of every
        // picture, which the shells replaced.
        long retainedPictures = 8L * 320L * 20L * MovingLayerLedger.keptBytes(16, 4)
            + 7L * 320L * 32L * MovingLayerLedger.keptBytes(16, 4);
        assertEquals(167608320L, retainedPictures);
        assertTrue(
            retainedPictures > 159L * megabyte && retainedPictures < 161L * megabyte,
            "a hundred and sixty megabytes of retained pictures");

        // This class's javadoc: what the frames-only charge let through the default before its first refusal.
        long lavastonesLetIn = default64 / lava(16).frames;
        assertEquals(3276L, lavastonesLetIn);
        assertTrue(lavastonesLetIn * lavastone > 2L * 1024L * megabyte, "more than two gigabytes before a refusal");
        assertEquals(85L, default64 / lavastone, "priced whole, the default refuses past eighty-five");
    }
}
