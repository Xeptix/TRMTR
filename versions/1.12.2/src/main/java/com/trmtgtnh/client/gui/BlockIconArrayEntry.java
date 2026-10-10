package com.trmtgtnh.client.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.GuiEditArray;
import net.minecraftforge.fml.client.config.GuiEditArrayEntries;
import net.minecraftforge.fml.client.config.IConfigElement;

import org.lwjgl.opengl.GL11;

/**
 * A row in Forge's array editor that shows the named block's icon beside its name.
 *
 * <p>
 * The block lists in this config are just strings, and a string is a poor way to check you have
 * the right block when a pack ships four hundred of them and half the names are guesses. Forge
 * already leaves a quarter of the row empty to the left of the text field, so the icon costs no
 * layout: it simply fills space that was blank.
 *
 * <p>
 * Forge builds these <em>reflectively</em>, looking for exactly
 * {@code (GuiEditArray, GuiEditArrayEntries, IConfigElement, Object)} - so the fourth parameter
 * has to be declared {@code Object} even though a String is always what arrives, and the class
 * has to be public and top-level. Get it wrong and there is no compile error: the constructor
 * lookup fails at runtime, the row is dropped, and saving the screen then writes the list back
 * without it. That failure mode is why this class does nothing else.
 */
public class BlockIconArrayEntry extends GuiEditArrayEntries.StringEntry {

    /** Sits flush against the text field, which Forge starts at {@code listWidth / 4 + 1}. */
    private static final int ICON_INSET = 18;

    /** Entries resolve to the same block for every row and every screen, so resolve once. */
    private static final Map<String, ItemStack> RESOLVED = new HashMap<String, ItemStack>();

    /**
     * Blocks whose renderer threw. Held apart from {@link #RESOLVED} on purpose: that map is
     * emptied when it grows too large, and forgetting that a block crashes would mean crashing
     * again on the next frame, forever.
     */
    private static final Set<String> BROKEN = new HashSet<String>();

    /**
     * Ours rather than {@link RenderItem#getInstance()}, whose {@code zLevel} is shared mutable
     * state that other GUIs are entitled to leave set to anything.
     */
    private static RenderItem items() {
        return Minecraft.getMinecraft()
            .getRenderItem();
    }

    private String lastText;
    private ItemStack icon;

    public BlockIconArrayEntry(GuiEditArray owningScreen, GuiEditArrayEntries owningEntryList,
        IConfigElement configElement, Object value) {
        super(owningScreen, owningEntryList, configElement, value);
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY,
        boolean isSelected, float partialTicks) {
        // Forge's row first. Drawing the icon last means that even if a block's renderer leaves
        // the GL state in a mess, it cannot reach the text and buttons of its own row.
        super.drawEntry(slotIndex, x, y, listWidth, slotHeight, mouseX, mouseY, isSelected, partialTicks);

        String text = this.textFieldValue.getText();
        if (lastText == null || !lastText.equals(text)) {
            lastText = text;
            icon = resolve(text);
        }
        String key = lastText.trim();
        if (icon == null || BROKEN.contains(key)) return;

        // slotHeight is 16 here - GuiSlot passes its row pitch less four - so a 16x16 icon is
        // exactly the height of the band, the same as an inventory slot.
        draw(key, icon, listWidth / 4 - ICON_INSET, y);
    }

    // ------------------------------------------------------------------
    // Name to block
    // ------------------------------------------------------------------

    /**
     * Turns one config entry into a stack to draw, or null if there is nothing worth drawing.
     *
     * <p>
     * The split mirrors the one in {@code SurfaceRegistry.applyList}, which is the authority on
     * what these strings mean; it is repeated rather than shared because reaching into the
     * classification path to change a working method, for a picture, is a poor trade. If the
     * accepted format ever changes, both move.
     */
    private static ItemStack resolve(String raw) {
        if (raw == null) return null;
        String entry = raw.trim();
        if (entry.isEmpty()) return null;

        if (RESOLVED.containsKey(entry)) return RESOLVED.get(entry);
        // Typing a name inserts one dead entry per keystroke, so this fills up with rubbish
        // long before it fills up with blocks. Nothing here is expensive to work out again.
        if (RESOLVED.size() > 256) RESOLVED.clear();

        ItemStack stack = build(entry);
        RESOLVED.put(entry, stack);
        return stack;
    }

    private static ItemStack build(String entry) {
        try {
            int meta = 0;
            String name = entry;
            int lastColon = entry.lastIndexOf(':');
            // "modid:block:meta" - the suffix is optional and may be "*", meaning every value.
            if (lastColon > 0 && entry.indexOf(':') != lastColon) {
                String tail = entry.substring(lastColon + 1);
                name = entry.substring(0, lastColon);
                if (!"*".equals(tail)) {
                    try {
                        meta = Integer.parseInt(tail) & 0xF;
                    } catch (NumberFormatException wildcardOrTypo) {
                        meta = 0;
                    }
                }
            }

            Block block = Block.getBlockFromName(name);
            // Null is a name nothing registered. Air is refused because it has nothing to show, which
            // only matters to a list that names it outright.
            if (block == null || block == Blocks.AIR) return null;

            Item item = Item.getItemFromBlock(block);
            // No item form: no icon, which is answer enough. Compared against air rather
            // than against null, because that is what 1.12.2 answers for a block nobody
            // can hold - and a null test here would never fire at all.
            if (item == null || item == net.minecraft.init.Items.AIR) return null;

            return new ItemStack(item, 1, meta);
        } catch (Throwable hostileBlock) {
            // A name can reach any mod in the pack. Something in here throwing is a reason to
            // draw nothing, never a reason to take the screen down.
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * Draws one 16x16 icon, leaving the GL state exactly as it was found.
     *
     * <p>
     * This is more ceremony than a picture deserves, and all of it is because a block icon is
     * not really a picture: for most terrain blocks it is a live isometric render, which hands
     * control to whatever renderer the block's mod registered. In a pack this size that is a lot
     * of third-party code running inside a config screen. The attribute stack absorbs whatever
     * it changes, the matrix loop unwinds whatever it forgot to pop, and the catch makes sure a
     * block that dies here only ever dies once.
     */
    /** Shared with the mob rows, which need exactly this harness around exactly this call. */
    static void drawIcon(String key, ItemStack stack, int x, int y) {
        draw(key, stack, x, y);
    }

    private static void draw(String key, ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null) return;

        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TRANSFORM_BIT);
        int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        try {
            // Vanilla draws inventory items with depth testing off - the isometric cube is
            // modelled behind the GUI plane, so with it on the icon is simply not there.
            // The other edition turns depth testing off here, because a 1.7.10 inventory
            // icon is an isometric cube modelled behind the GUI plane and with it on the
            // icon is simply not there. A 1.12.2 item is a model drawn against that plane
            // and wants it on, which is what vanilla's own slots do.
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();

            // Vanilla's shared renderer rather than one of our own: making a RenderItem
            // here would mean handing it the model manager and the item colors, and
            // every screen in the game already draws through this one. Its zLevel is
            // shared mutable state, which is why it is put back below.
            RenderItem items = items();
            float wasAt = items.zLevel;
            items.zLevel = 0.0F;
            try {
                items.renderItemAndEffectIntoGUI(stack, x, y);
            } finally {
                items.zLevel = wasAt;
            }
        } catch (Throwable hostileRenderer) {
            BROKEN.add(key);
            // A renderer that died part way through a quad leaves the tessellator believing it
            // is still drawing, and the next thing to open one throws. Flushing costs a frame
            // of smeared geometry; not flushing costs the whole screen.
            try {
                Tessellator.getInstance()
                    .draw();
            } catch (Throwable wasNotDrawing) {
                // Already idle, which is the ordinary case and not worth reporting.
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            while (GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) > depth) {
                GL11.glPopMatrix();
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glPopAttrib();
            // Through the state manager as well as through GL, because popping the
            // attribute stack puts the driver back without telling the manager, which
            // would then skip the next call it believed was already in force.
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableRescaleNormal();
        }
    }
}
