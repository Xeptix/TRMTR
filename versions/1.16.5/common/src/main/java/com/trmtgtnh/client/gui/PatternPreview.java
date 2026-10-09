package com.trmtgtnh.client.gui;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.texture.FaceRules;
import com.trmtgtnh.client.texture.WearPatterns;
import com.trmtgtnh.client.texture.WearSprite;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Small pictures of what each wear look does, drawn on the blocks it will be done to.
 *
 * <p>
 * The eleven looks are not describable in a word - crack, smooth, rub and cover-coming-off, four of
 * them again with a lighter or heavier hand, and three that do two of those at once, say roughly what
 * they do and nothing about what they look like on stone as against on snow - so the
 * picker shows them instead of naming them. Three per look, at the start of a run, halfway along it
 * and at the end, because what separates them is as much how they get there as where they end up.
 *
 * <p>
 * On the family's own blocks, cycling with the icon beside the row, because a picture is not a
 * property of a look alone: rubbing a patch off cobblestone and rubbing a patch off sandstone are
 * two different pictures, and the one worth seeing is the one your ground is made of. It also
 * answers the question the pictures otherwise raise - why the cover-coming-off look does almost
 * nothing on stone - by showing it doing almost nothing on stone.
 *
 * <p>
 * Built by handing the block's own faces to the very same operators the atlas runs, so a preview
 * cannot drift from the thing it previews. That is the whole reason those were split out of the
 * sprite: a picture that lies about what will happen is worse than no picture.
 *
 * <p>
 * Kept until the family or the block changes and then thrown away, because these are dynamic
 * textures and each one holds a GL name until it is told not to. Thirty-three at a time is nothing;
 * thirty-three per block per family per screen opening, never released, is a leak.
 */
final class PatternPreview {

    /**
     * The three points along a run worth showing: the first real gradation, halfway, and finished.
     *
     * <p>
     * A sixteenth rather than the hundredth it was. That hundredth was drawing whatever the lift
     * off zero made of it, which was the same value for every look and about five times what the
     * number itself says; with the lift gone it would draw a picture of a state no run visits, and
     * a different one per look - a hundredth of the way along is 0.01 on a look that spreads evenly
     * and six times that on one that does not. Since the point of this row is to let two looks be
     * compared, the column has to be the same place in both runs rather than the same number handed
     * to both.
     */
    private static final float[] AT = { 1f / 15f, 0.5f, 1.0f };

    /** How many gradations the cover-coming-off look is shown across, which is its own run. */
    private static final int COVER_STEPS = 16;

    private final Map<String, ResourceLocation> made = new HashMap<String, ResourceLocation>();

    /**
     * The textures behind those names, kept so their pixels can be rewritten in place.
     *
     * <p>
     * The row cycles through a family's blocks once a second, and every one of those is thirty-three
     * new pictures, so the thirty-three are made once and painted over afterwards.
     *
     * <p>
     * The other edition gives a stronger reason than this version has: there the texture manager's
     * own delete takes a texture out of its map and never tells the driver, so minting a fresh one
     * each time leaks a GL name each time. {@code release} does free the name here. Painting over is
     * still the right thing - thirty-three uploads a second is cheaper than thirty-three
     * allocations a second, and the names stay put - but it is an optimisation now rather than a
     * leak being avoided.
     */
    private final Map<String, DynamicTexture> canvas = new HashMap<String, DynamicTexture>();

    /**
     * Which of the thirty-three have been painted for the block currently being shown.
     *
     * <p>
     * A set rather than a flag, and that is the whole of the bug it replaces. "The block has
     * changed" was asked once per call and answered by updating the record of which block was
     * being shown - so the first to ask got a repaint and all the rest were told nothing had changed
     * and handed back the picture of the block before. All but one thirty-third of the picker sat
     * still while a single square cycled.
     */
    private final java.util.Set<String> painted = new java.util.HashSet<String>();

    private SurfaceFamily shownFor;

    private String shownOn;

    /**
     * The looks the picker offers, in the order it offers them.
     *
     * <p>
     * Every one that does something, and no more. The setting still reads two further names - the
     * old sand and polish - so that a file written before this was tidied goes on working, and both
     * of them run the rub. Offering them would be offering the same picture three times over;
     * {@link #canonical} is what makes a file that says one of them show the row it will actually
     * use.
     *
     * <p>
     * Ordered so that each lighter or heavier variant sits directly under the look it varies, and
     * the three compounds come last. A picker exists to be compared down, and a variation is only
     * judgeable against the thing it varies, while a compound is something gone looking for rather
     * than landed on. The four original looks keep the order they have always had relative to one
     * another, so a habit still finds them.
     */
    private static final String[] LOOKS = { FamilySettings.PATTERN_GRASS, FamilySettings.PATTERN_CRACK,
        FamilySettings.PATTERN_CRACK_LITE, FamilySettings.PATTERN_SMOOTH, FamilySettings.PATTERN_SMOOTH_HEAVY,
        FamilySettings.PATTERN_DIRT, FamilySettings.PATTERN_DIRT_LITE, FamilySettings.PATTERN_SMOOTH_CRACK,
        FamilySettings.PATTERN_SMOOTH_DIRT, FamilySettings.PATTERN_CRACK_DIRT, FamilySettings.PATTERN_CRACK_DIRT_LITE };

    static String[] looks() {
        return LOOKS.clone();
    }

    /** How many rows the picker has, which its geometry asks every frame. */
    static int lookCount() {
        return LOOKS.length;
    }

    /**
     * The look a stored name actually runs, which is not always the name.
     *
     * <p>
     * Two names in the setting are older than the operators are and fall to the rub: a file written
     * before this was tidied still says sand or polish, and the picker has to show the row that
     * will actually be used rather than none of them. Folded here rather than at the setting,
     * because the setting is what somebody typed and rewriting it under them would be worse.
     *
     * <p>
     * Answered by walking the same list the picker draws rather than by a list of its own, and that
     * is not tidiness. The two disagreeing is the exact failure this method exists to prevent, and
     * it would not show up as a wrong label: the picker decides whether a click is a change or a
     * change back by comparing the clicked row against this, so a look that is offered but not
     * folded would highlight the wrong row AND make clicking its own row do nothing whatever.
     */
    static String canonical(String pattern) {
        for (String look : LOOKS) {
            if (look.equals(pattern)) return look;
        }
        return FamilySettings.PATTERN_DIRT;
    }

    /**
     * What a look is called on the screen. The stored names are older than the looks are.
     *
     * <p>
     * The last line is load-bearing rather than tidy. This is asked about the stored value and not
     * about {@link #canonical} of it - the card names the look a family is set to, whatever that
     * says - so it is still handed sand, polish and anything anybody has mistyped, and something
     * has to come back for them.
     */
    static String nameOf(String pattern) {
        if (FamilySettings.PATTERN_GRASS.equals(pattern)) return "cover off";
        if (FamilySettings.PATTERN_CRACK.equals(pattern)) return "cracked";
        if (FamilySettings.PATTERN_CRACK_LITE.equals(pattern)) return "cracked lite";
        if (FamilySettings.PATTERN_SMOOTH.equals(pattern)) return "smoothed";
        if (FamilySettings.PATTERN_SMOOTH_HEAVY.equals(pattern)) return "smoothed heavy";
        if (FamilySettings.PATTERN_DIRT_LITE.equals(pattern)) return "rubbed away lite";
        if (FamilySettings.PATTERN_SMOOTH_CRACK.equals(pattern)) return "smoothed cracked";
        if (FamilySettings.PATTERN_SMOOTH_DIRT.equals(pattern)) return "smoothed rubbed";
        if (FamilySettings.PATTERN_CRACK_DIRT.equals(pattern)) return "cracked rubbed";
        if (FamilySettings.PATTERN_CRACK_DIRT_LITE.equals(pattern)) return "cracked rubbed lite";
        return "rubbed away";
    }

    /**
     * The texture for one look at one point along the run, or null if it cannot be made.
     *
     * <p>
     * Everything is built the first time it is asked for rather than up front, so a family nobody
     * opens the picker on costs nothing at all, and a block the cycle has not reached yet costs
     * nothing either.
     */
    ResourceLocation of(SurfaceFamily family, ItemStack shown, String pattern, int step) {
        if (family == null || step < 0 || step >= AT.length) return null;

        String on = shown == null ? "" : String.valueOf(net.minecraft.core.Registry.BLOCK.getId(Block.byItem(shown.getItem())));

        // A different family is a different set of names, so those are released and made again.
        if (family != shownFor) {
            forget();
            shownFor = family;
        }
        // A different block is the same thirty-three names painted again, so nothing is released -
        // but every one of the thirty-three is now out of date, not merely whichever is asked for
        // first.
        if (!on.equals(shownOn)) {
            shownOn = on;
            painted.clear();
        }

        String key = pattern + "/" + step;
        if (painted.contains(key)) return made.get(key);

        ResourceLocation built = build(family, shown, pattern, step);
        made.put(key, built);
        painted.add(key);
        return built;
    }

    /**
     * The two faces a look might need, read off the block the row is currently showing.
     *
     * <p>
     * The top is what wears and the bottom is what shows through when it does. A block with one
     * texture on every face therefore has nothing to reveal, which is the honest answer for it
     * rather than a special case: the cover-coming-off look does nothing there, and the picture
     * says so.
     *
     * @return the icon name, or the family's stand-in when the block will not say
     */
    private String faceOf(ItemStack shown, SurfaceFamily family, int side) {
        if (shown != null) {
            // Air rather than null, because that is what 1.12.2 answers for an item that is
            // not a block - it never answers null, so a null test here would never fire.
            Block block = Block.byItem(shown.getItem());
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                // The other edition asks the block for the icon on a face and reads its name.
                // There are no icons here; a block's faces come off its baked model, which is
                // a question the texture pipeline already had to answer for the ghost's own
                // sides. Same answer, same shape, read from the model instead of the block.
                String name = com.trmtgtnh.client.texture.ModelFaces.faceName(block, side);
                if (name != null) return name;
            }
        }
        return FaceRules.vanillaName(family, side);
    }

    private ResourceLocation build(SurfaceFamily family, ItemStack shown, String pattern, int step) {
        try {
            int[] top = read(faceOf(shown, family, 1));
            // A block whose face is not a file of its own - one drawn from a sheet, or by a mod's
            // own renderer - cannot be read here. The family's stand-in is what the game itself
            // falls back on for exactly that block, so it is what the picture should show.
            if (top == null) top = read(FaceRules.vanillaName(family, 1));
            if (top == null) return null;
            int size = (int) Math.round(Math.sqrt(top.length));
            if (size <= 0) return null;

            FamilySettings settings = TrmtConfig.family(family);
            float strength = settings == null ? 1f : settings.wearStrength;

            int[] worn;
            if (FamilySettings.PATTERN_GRASS.equals(pattern)) {
                int[] bottom = read(faceOf(shown, family, 0));
                if (bottom == null) bottom = read(FaceRules.vanillaName(family, 0));
                worn = WearSprite.grassPass(
                    Minecraft.getInstance()
                        .getResourceManager(),
                    top,
                    bottom == null ? top : bottom,
                    Math.round(AT[step] * (COVER_STEPS - 1)),
                    COVER_STEPS,
                    0);
            } else {
                worn = WearSprite.wearPass(top, size, pattern, AT[step], strength, 0);
            }
            if (worn == null) return null;

            int edge = (int) Math.round(Math.sqrt(worn.length));
            String key = pattern + "/" + step;

            DynamicTexture kept = canvas.get(key);
            NativeImage canvasImage = kept == null ? null : kept.getPixels();
            if (canvasImage != null && canvasImage.getWidth() == edge && canvasImage.getHeight() == edge) {
                paint(canvasImage, worn, edge);
                kept.upload();
                return made.get(key);
            }

            // A canvas of the wrong size cannot be painted over, which happens when two blocks in
            // one family are drawn at different resolutions. The old one goes rather than being
            // left behind: asking for a location under a name that already has one hands back a
            // second location and keeps the first, so not saying goodbye here is a texture leaked
            // every time the cycle passes such a pair.
            ResourceLocation stale = made.get(key);
            if (stale != null) {
                Minecraft.getInstance()
                    .getTextureManager()
                    .release(stale);
            }

            NativeImage image = new NativeImage(edge, edge, false);
            paint(image, worn, edge);
            DynamicTexture texture = new DynamicTexture(image);
            canvas.put(key, texture);
            return Minecraft.getInstance()
                .getTextureManager()
                .register(Trmt.MODID + "_look_" + family.key() + "_" + pattern + "_" + step, texture);
        } catch (RuntimeException awkwardTexture) {
            Trmt.LOG.debug("Could not draw a wear preview for {}/{}", family.key(), pattern, awkwardTexture);
            return null;
        }
    }

    /**
     * The compositor's pixels into one of the game's images, with red and blue put the right way
     * round.
     *
     * <p>
     * <strong>This is the one line of this class that could be silently wrong.</strong> The
     * compositor produces ARGB - what {@code BufferedImage.TYPE_INT_ARGB} wanted, and what this mod
     * has produced since 1.7.10. A {@code NativeImage} keeps its bytes in the order R, G, B, A, so
     * read back as an int on a little-endian machine it is ABGR: alpha and green sit where they sat
     * and red and blue have swapped. Handed an ARGB pixel it would draw a picture of exactly the
     * right shape in the wrong colors, which reads as a tinting bug rather than as a channel order.
     *
     * <p>
     * A pixel at a time rather than through the image's own bulk copy, because there is not one that
     * takes an {@code int[]} - and at sixteen or thirty-two to a side, three times eleven times once
     * a second, there is nothing here worth being cleverer about.
     */
    private static void paint(NativeImage into, int[] argb, int edge) {
        for (int y = 0; y < edge; y++) {
            for (int x = 0; x < edge; x++) {
                int one = argb[y * edge + x];
                into.setPixelRGBA(x, y, (one & 0xFF00FF00) | ((one >> 16) & 0xFF) | ((one & 0xFF) << 16));
            }
        }
    }

    private int[] read(String iconName) {
        BufferedImage art = WearPatterns.readIcon(
            Minecraft.getInstance()
                .getResourceManager(),
            iconName);
        if (art == null) return null;
        int size = WearPatterns.squareSize(art);
        return size <= 0 ? null : WearPatterns.toPixels(art, size);
    }

    /** Releases every picture made so far. Called when the screen closes or the block changes. */
    void forget() {
        for (ResourceLocation at : made.values()) {
            if (at == null) continue;
            Minecraft.getInstance()
                .getTextureManager()
                .release(at);
        }
        made.clear();
        canvas.clear();
        painted.clear();
        shownFor = null;
        shownOn = null;
    }
}
