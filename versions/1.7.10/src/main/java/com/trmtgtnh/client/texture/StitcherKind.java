package com.trmtgtnh.client.texture;

/**
 * Which stitcher will pack the block atlas, asked as the plan is made, and the share of the square it was measured to
 * fill (0.9.229, glow-pixels). The plan prices this mod's holders in exact pixels against that share wherever it is
 * less than the planned square less a sixteenth, and in whole slots where the square already holds less than the
 * stitcher packs.
 *
 * <p>
 * lwjgl3ify 3.0.42 swaps vanilla's Stitcher for its StbStitcher through its MixinStitcher when
 * {@code stbiTextureStitching} is on and FastCraft is absent; with FastCraft in, it applies the same mixin only under
 * {@code stbiIgnoreFastcraft} or for FastCraft 1.25 with OptiFine (read from its Mixins and TargetedMod classes on
 * 2026-11-18). OptiFine 1.7.10 replaces vanilla's Stitcher with its own patch of it, which fills the whole square
 * (OPTIFINE). FastCraft 1.25 takes the stitch from the Stitcher altogether and packs the atlas with a packer of its own
 * (FASTCRAFT). Where two of these could each be the one that packs, which is decided by their own code and was never
 * run here, the plan is held to the least that any measured stitcher packs (UNMEASURED). With lwjgl3ify's stitching
 * off, or no lwjgl3ify, vanilla's Stitcher packs the atlas. Nothing here loads a class of another mod's: FastCraft and
 * OptiFine are asked for by their class files, and lwjgl3ify's setting is read from its own Config, which its coremod
 * has loaded long before any stitch.
 *
 * <p>
 * Edition code, 1.7.10's alone: no port runs lwjgl3ify, Angelica, OptiFine 1.7.10, FastCraft or these stitchers.
 */
enum StitcherKind {

    /**
     * lwjgl3ify's StbStitcher: every replay packed at the side it starts at, the fullest at 97.4% (SmallGutterTest).
     */
    STB("lwjgl3ify's STB stitcher", SmallGutter.STB_FILL_PER_MILLE,
        "lwjgl3ify's STB stitcher packs nine tenths of the square it starts at"),

    /** Vanilla's Stitcher, which reaches only half the square (SmallGutter.VANILLA_FILL_PER_MILLE). */
    VANILLA("vanilla's stitcher", SmallGutter.VANILLA_FILL_PER_MILLE,
        "vanilla's stitcher reaches only half its largest square"),

    /**
     * OptiFine 1.7.10 HD_U_E7's Stitcher (0.9.229, stitch-optifine): vanilla's, but its expandAndAllocateSlot checks
     * each side against its own largest where vanilla checks the other's, so it grows the height after the width
     * reaches the largest and fills the whole square (read from bpv with OptiFine's patch applied,
     * tools/optifine_class.py, and run on that class: 30,000 holders placed where the replay places them). Replayed
     * over GT New Horizons 2.9.0's holders as captured, without gutters, at 48 pixels, with the anisotropic border, and
     * over 256-pixel squares, at 16384 and 8192, it packed 97.57% to 100.00% of its largest square (VanillaStitchReplay
     * --edition optifine). Nine tenths, as STB's, is 7.57 points below the least. Its largest square is what
     * OptiFine's own probe finds, from 65536 down, which is never less than the card's largest that the plan is told.
     */
    OPTIFINE("OptiFine's stitcher", 900,
        "OptiFine's stitcher was measured to fill at least 97.5% of its largest square"),

    /**
     * FastCraft 1.25's packer (0.9.229, stitch-optifine), which takes the stitch from vanilla's Stitcher: it keeps its
     * free room as squares a power of two across, puts each sprite whose rounded side is a power of two into the
     * smallest that holds it, packs a run of sprites of any other size into square blocks of their own first, and
     * grows the atlas to the card's largest texture on both sides (VanillaStitchReplay's FastCraft, read from
     * fastcraft.ah). Replayed as OptiFine's was, and at two mipmap levels as well as four, it packed 51.30% of the
     * square where every holder of this mod's is 20 pixels (each taking a 32-pixel square), 64.94% to 96.58% where
     * many holders' sides are not powers of two, and all of it where every side is one. 45% is 6.3 points below the
     * least.
     */
    FASTCRAFT("FastCraft's packer", 450,
        "FastCraft's packer was measured to fill as little as 51% of its largest square"),

    /**
     * More than one stitcher could pack the atlas, or which one cannot be read: held to the least any measured
     * stitcher packs, vanilla's and FastCraft's 45% of the card's largest square (0.9.229, stitch-optifine). Until then
     * this priced whole slots, 93.75% of the planned square, which neither of those packs.
     */
    UNMEASURED("a stitcher not measured", SmallGutter.VANILLA_FILL_PER_MILLE,
        "a stitcher not measured is held to the least any measured stitcher packs, 45% of its largest square");

    final String said;

    final int fillPerMille;

    /** Why the plan is held to this share, for the plan's line; it starts with {@link #said}. */
    final String why;

    StitcherKind(String said, int fillPerMille, String why) {
        this.said = said;
        this.fillPerMille = fillPerMille;
        this.why = why;
    }

    /**
     * The share of a square of {@code side} this stitcher was measured to fill, in thousandths, for a plan made to that
     * square on a card whose largest texture is {@code glMaximum} (0.9.229, stitch-cap); nought where the plan's square
     * less a sixteenth, in whole slots, already holds.
     *
     * <p>
     * STB's nine tenths are of the square it starts at, and only a plan priced in pixels, under Angelica's gutter, is
     * held to them; without that gutter STB grows past the planned square as far as the card goes. Every other share is
     * of the card's largest square, which each of the others grows to, and is scaled to the planned one: vanilla's
     * Stitcher reaches only half of it (VanillaStitcherHalfTest), so a plan to 8192 on a card that addresses 16384 has
     * twice its square and keeps whole slots, and a plan to the card's own largest side, with client.largerAtlas or on
     * a card of 8192, is held to 45%. A card whose probe failed is planned as 8192 and asked for no more.
     */
    int fillPerMille(int glMaximum, int side, boolean gutter) {
        if (this == STB) return gutter ? fillPerMille : 0;
        if (side <= 0) return 0;
        long largest = glMaximum > 0 ? glMaximum : side;
        long share = fillPerMille * largest * largest / ((long) side * side);
        return share * AtlasPlan.RESERVE_SHARE >= 1000L * (AtlasPlan.RESERVE_SHARE - 1) ? 0 : (int) share;
    }

    /**
     * The stitcher these facts make. FastCraft alone, with lwjgl3ify's stitching off or no lwjgl3ify: its packer.
     * OptiFine alone, likewise: its stitcher. Either beside the other, or beside lwjgl3ify's stitching on or unread,
     * where lwjgl3ify's mixin and FastCraft's transformer decide between them: unmeasured, held to the least. Neither:
     * lwjgl3ify's StbStitcher present with its stitching on, STB; its setting unread, unmeasured; anything else,
     * vanilla's. {@code stbStitching} is null where lwjgl3ify's setting could not be read.
     */
    static StitcherKind of(boolean fastcraft, boolean optifine, boolean stbStitcher, Boolean stbStitching) {
        boolean stbMay = stbStitcher && !Boolean.FALSE.equals(stbStitching);
        if (fastcraft || optifine) {
            if ((fastcraft && optifine) || stbMay) return UNMEASURED;
            return fastcraft ? FASTCRAFT : OPTIFINE;
        }
        if (!stbStitcher) return VANILLA;
        if (stbStitching == null) return UNMEASURED;
        return stbStitching.booleanValue() ? STB : VANILLA;
    }

    /** The stitcher this game will pack the block atlas with. */
    static StitcherKind detect() {
        ClassLoader loader = StitcherKind.class.getClassLoader();
        boolean stb = present(loader, "me/eigenraven/lwjgl3ify/textures/StbStitcher.class");
        return of(
            present(loader, "fastcraft/Tweaker.class"),
            present(loader, "optifine/OptiFineForgeTweaker.class"),
            stb,
            stb ? stbStitching(loader) : null);
    }

    private static boolean present(ClassLoader loader, String file) {
        try {
            return loader != null && loader.getResource(file) != null;
        } catch (RuntimeException unaskable) {
            return false;
        }
    }

    /** lwjgl3ify's own Config.MIXIN_STBI_TEXTURE_STITCHING, or null where it cannot be read. */
    private static Boolean stbStitching(ClassLoader loader) {
        try {
            Class<?> config = Class.forName("me.eigenraven.lwjgl3ify.core.Config", false, loader);
            return Boolean.valueOf(
                config.getField("MIXIN_STBI_TEXTURE_STITCHING")
                    .getBoolean(null));
        } catch (Throwable unreadable) {
            return null;
        }
    }
}
