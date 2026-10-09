package com.trmtgtnh.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.Trmt;

/**
 * The color a block multiplies its own picture by, which the wear editor's previews are drawn in.
 *
 * <p>
 * <strong>A seam for one object, and the reason is that the object is private.</strong> Both older
 * editions ask {@code Minecraft.getItemColors()} and get the game's own set - the one every mod has
 * registered its handlers into. There is no such method at this version: the field is private and
 * nothing public hands it out. Forge patches an accessor back on, which is Forge's own addition and
 * invisible to a module both loaders share; Fabric opens the field with a line of access widener,
 * which is invisible for the same reason. So each loader reaches it its own way and tells this, and
 * the shared code asks here.
 *
 * <p>
 * The same arrangement {@code TamperModels} is under for {@code ItemProperties.register}, for the
 * same reason, and the widener file carries both lines with the same note about being narrow on
 * purpose.
 *
 * <h2>What happens if nobody says</h2>
 *
 * <p>
 * A set built from vanilla's own handlers, once, and a warning once. That is a real answer rather
 * than nothing: every tinted block a ground family is likely to contain - grass, leaves, the vines -
 * is vanilla's and is colored correctly by it. What it misses is a modded block with a color
 * handler of its own, which would draw its preview untinted, and that is worth a line in the log
 * rather than a blank picture.
 */
public final class ItemTints {

    private static ItemColors told;

    private static ItemColors guessed;

    private static boolean complained;

    private ItemTints() {}

    /** Called once by each loader's client start, with the set the game is actually using. */
    public static void use(ItemColors colors) {
        told = colors;
    }

    /** Whether a loader has handed the real set over. */
    public static boolean wired() {
        return told != null;
    }

    /**
     * The color this stack multiplies its own picture by, or plain white where it has none.
     *
     * <p>
     * Tint index nought, which is the one an ordinary block's own texture carries; a block with more
     * than one tinted layer is drawn by its model rather than by a single multiplier, and a preview
     * is one picture.
     */
    public static int of(ItemStack shown) {
        if (shown == null || shown.isEmpty()) return 0xFFFFFF;
        ItemColors colors = colors();
        if (colors == null) return 0xFFFFFF;
        try {
            return colors.getColor(shown, 0) & 0xFFFFFF;
        } catch (RuntimeException hostileHandler) {
            // Somebody else's color handler, asked about a stack outside a world. White is a
            // picture; a thrown exception is a screen that will not open.
            return 0xFFFFFF;
        }
    }

    private static ItemColors colors() {
        if (told != null) return told;
        if (guessed == null) {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return null;
            if (!complained) {
                complained = true;
                Trmt.LOG.warn(
                    "No loader handed over the game's own item colors, so the wear editor's previews are "
                        + "tinted from vanilla's handlers alone. Every tinted ground block vanilla has is "
                        + "still right; a modded one with a color handler of its own will draw untinted. "
                        + "Said once.");
            }
            guessed = ItemColors.createDefault(client.getBlockColors());
        }
        return guessed;
    }
}
