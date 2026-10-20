package com.trmtgtnh.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The color a block multiplies its own picture by, which the wear editor's previews are drawn in.
 *
 * <p>
 * <strong>Asked of the item's own model from 26.1, because the set of color handlers it was asked of is
 * gone.</strong> Every older edition asks the game's set of item color handlers - the one every mod registered
 * its handlers into - for tint index nought. 1.21.1 had to be handed that set by each loader, since the field
 * holding it was private; that is why this class once had a {@code use} for each loader's client start to call.
 * From 1.21.4 an item's tints are part of its model definition rather than a handler registered in code, and
 * the set itself no longer exists. So the stack's model is resolved for the GUI, as the game does before it draws
 * one, and the first tint its first layer carries is read back: the same number 1.21.1's handler answered for
 * tint index nought - grass's default green, a leaf's foliage color, a modded block's own - and plain white
 * for a stack whose model carries no tint, which is what 1.21.1's set answered for a stack with no handler. No
 * loader has anything to hand over now, so nothing is guessed and nothing is warned about.
 */
public final class ItemTints {

    /**
     * Where the model is resolved into, kept to learn which layer came first.
     *
     * <p>
     * A render state's layers are its own; the first one handed out by {@code newLayer} is layer nought, the
     * one an ordinary block's own picture is drawn from. Screens are drawn on one thread, so one is enough.
     */
    private static final Probe PROBE = new Probe();

    private ItemTints() {}

    /**
     * Whether the real colors are in reach, which they always are from 26.1: they come with the item's model,
     * not from a loader. Kept so a caller asking the old question still has its answer.
     */
    public static boolean wired() {
        return true;
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
        Minecraft client = Minecraft.getInstance();
        if (client == null) return 0xFFFFFF;
        ItemModelResolver models = client.getItemModelResolver();
        if (models == null) return 0xFFFFFF;
        try {
            // No level and no owner, as 1.21.1's handlers were asked: a preview is of the block, not of where
            // anybody is standing.
            PROBE.first = null;
            models.updateForTopItem(PROBE, shown, ItemDisplayContext.GUI, null, null, 0);
            ItemStackRenderState.LayerRenderState layer = PROBE.first;
            if (layer == null) return 0xFFFFFF;
            it.unimi.dsi.fastutil.ints.IntList tints = layer.tintLayers();
            if (tints.isEmpty()) return 0xFFFFFF;
            return tints.getInt(0) & 0xFFFFFF;
        } catch (RuntimeException hostileHandler) {
            // Somebody else's tint source, asked about a stack outside a world. White is a
            // picture; a thrown exception is a screen that will not open.
            return 0xFFFFFF;
        } finally {
            PROBE.clear();
            PROBE.first = null;
        }
    }

    /** A render state that remembers the first layer it handed out. */
    private static final class Probe extends ItemStackRenderState {

        ItemStackRenderState.LayerRenderState first;

        @Override
        public ItemStackRenderState.LayerRenderState newLayer() {
            ItemStackRenderState.LayerRenderState layer = super.newLayer();
            if (first == null) first = layer;
            return layer;
        }
    }
}
