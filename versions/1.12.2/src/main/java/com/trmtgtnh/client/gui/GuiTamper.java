package com.trmtgtnh.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.item.EnchLight;
import com.trmtgtnh.item.EnchReinforce;
import com.trmtgtnh.item.EnchWard;
import com.trmtgtnh.item.ItemChunkTamper;
import com.trmtgtnh.item.ItemMagicTamper;
import com.trmtgtnh.network.PacketEditFamily;
import com.trmtgtnh.network.PacketEditMob;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The settings on a tamper, as a screen rather than a sequence of clicks in the air.
 *
 * <p>
 * No container and no server element, deliberately. Everything shown here already lives in the
 * stack's own data, which the client has a copy of, so opening it is a client-side act and
 * nothing needs synchronising to put it on screen. The one thing that must reach the server is
 * the answer, and that goes as two numbers - see {@code PacketTamperSettings} for why not as a
 * stack.
 *
 * <p>
 * The values are edited locally as you click, so the screen responds without waiting for a round
 * trip, and sent once when it closes. If the server disagrees - because the tool was swapped out
 * of the hand meanwhile - it simply does not apply them, and the next thing to read the stack
 * reads the truth.
 */
@SideOnly(Side.CLIENT)
public class GuiTamper extends GuiScreen {

    private static final int ID_REACH_DOWN = 10;
    private static final int ID_REACH_UP = 11;
    private static final int ID_STEPS_DOWN = 20;
    private static final int ID_STEPS_UP = 21;
    private static final int ID_DONE = 30;
    private static final int ID_FAMILY = 40;
    private static final int ID_REINFORCE = 60;
    private static final int ID_REINFORCE_LEVEL = 61;
    private static final int ID_ASSIGN = 41;
    private static final int ID_EXCLUDE = 42;
    private static final int ID_MOB_ADD = 50;
    private static final int ID_MOB_REMOVE = 51;

    private final ItemStack stack;

    private int reach;
    private int steps;
    private int mode;
    private int reinforceLevel;
    private boolean wayfarer;
    private final boolean reinforceCapable;
    private final boolean wardCapable;

    private final boolean lightCapable;

    /**
     * Whether the controls that change which blocks wear are drawn at all.
     *
     * <p>
     * Not drawn and disabled - not drawn. A greyed-out button is a promise that the thing exists
     * and you are the wrong person, which for a permission the server owns is both truer and
     * less useful than simply not offering it. The server checks again regardless; this only
     * decides what is on screen.
     */
    private final boolean mayEdit;

    /** The block this tool last worked, and which family to offer it to. */
    private final String target;

    /** The entity kind this tool last worked, offered to the mob list instead of a family. */
    private final String entity;

    /**
     * Whether the editor is talking about an entity rather than a block.
     *
     * <p>
     * One sub-panel, two things it can edit. Which one it shows follows whichever the tool
     * touched last - a Ctrl-click on a pig puts it in entity mode, a Ctrl-click on ground puts
     * it back in block mode - so the operator never has to choose a mode, only point the tool.
     */
    private final boolean entityMode;

    private int familyChoice;

    public GuiTamper(ItemStack stack) {
        this.stack = stack;
        this.reach = ItemChunkTamper.reachOf(stack);
        this.steps = ItemChunkTamper.stepsOf(stack);
        this.mode = ItemChunkTamper.modeOf(stack);
        this.reinforceLevel = ItemChunkTamper.reinforceLevelOf(stack);
        this.wayfarer = stack.getItem() instanceof ItemMagicTamper;
        this.reinforceCapable = TrmtConfig.reinforceEnabled && EnchReinforce.has(stack);
        this.wardCapable = TrmtConfig.wardEnabled && EnchWard.has(stack);
        this.lightCapable = TrmtConfig.lightEnabled && EnchLight.has(stack);
        // A tool set to a mode it can no longer use falls back to plain, so the screen never
        // opens showing a mode the cycle would not offer.
        if ((this.mode == ItemChunkTamper.MODE_REINFORCE && !this.reinforceCapable)
            || (this.mode == ItemChunkTamper.MODE_WARD && !this.wardCapable)
            || (this.mode == ItemChunkTamper.MODE_LIGHT && !this.lightCapable)) {
            this.mode = ItemChunkTamper.MODE_NONE;
        }
        this.target = ItemChunkTamper.targetOf(stack);
        this.entity = ItemChunkTamper.entityOf(stack);
        // Only the magic tamper carries the editor, and only for somebody the server says may
        // use it. Given that, the editor shows for whichever kind the tool last worked and has
        // something to name.
        boolean operatorTool = stack.getItem() instanceof ItemMagicTamper && Trmt.proxy.mayEditFamilies();
        boolean hasEntity = entity != null && !entity.isEmpty();
        boolean hasBlock = target != null && !target.isEmpty();
        this.entityMode = operatorTool && ItemChunkTamper.lastWorkedEntity(stack) && hasEntity;
        this.mayEdit = operatorTool && (entityMode || hasBlock);
        this.familyChoice = firstStagedFamily();
    }

    private static int firstStagedFamily() {
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (family.staged) return family.ordinal();
        }
        return 0;
    }

    private SurfaceFamily chosenFamily() {
        SurfaceFamily family = SurfaceFamily.byOrdinal(familyChoice);
        return family == null ? SurfaceFamily.DIRT : family;
    }

    private void nextFamily() {
        for (int step = 1; step <= SurfaceFamily.values().length; step++) {
            SurfaceFamily candidate = SurfaceFamily.byOrdinal((familyChoice + step) % SurfaceFamily.values().length);
            if (candidate != null && candidate.staged) {
                familyChoice = candidate.ordinal();
                return;
            }
        }
    }

    /**
     * The two heights this screen can have.
     *
     * <p>
     * The operator editor is a panel of its own below a gap, not a lower region of the settings
     * panel, so the screen is taller when it is shown. Both are centred from the same anchor, so
     * the routine controls do not jump when the editor appears or is withheld.
     */
    private int panelHeight() {
        return mayEdit ? 262 : 180;
    }

    private int topAnchor() {
        // Clamped rather than left to go negative, so the title bar does not slide off the top of
        // a short window at a large GUI scale. GuiScreen does no clipping of its own.
        return Math.max(4, height / 2 - panelHeight() / 2);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void initGui() {
        buttonList.clear();
        int midX = width / 2;
        int top = topAnchor();

        // Pushed to the panel edges so the value reads on its own line between them rather than
        // colliding with either, which is what the old centre layout did at wide values.
        buttonList.add(new GuiButton(ID_REACH_DOWN, midX - 108, top + 52, 30, 20, "-"));
        buttonList.add(new GuiButton(ID_REACH_UP, midX + 78, top + 52, 30, 20, "+"));
        buttonList.add(new GuiButton(ID_STEPS_DOWN, midX - 108, top + 80, 30, 20, "-"));
        buttonList.add(new GuiButton(ID_STEPS_UP, midX + 78, top + 80, 30, 20, "+"));
        int doneY = mayEdit ? top + 240 : top + 156;
        boolean showLevel = reinforceCapable && mode == ItemChunkTamper.MODE_REINFORCE && wayfarer;
        if (showLevel) {
            // The Wayfarer's extra, only in reinforce mode: a level to jump straight to. Chosen
            // here, applied by one right-click for one material, up or down.
            buttonList.add(new GuiButton(ID_REINFORCE_LEVEL, midX - 104, doneY, 60, 20, levelLabel()));
            buttonList.add(new GuiButton(ID_REINFORCE, midX - 40, doneY, 76, 20, modeLabel()));
            buttonList.add(new GuiButton(ID_DONE, midX + 40, doneY, 64, 20, I18n.translateToLocal("gui.done")));
        } else if (modeCapable()) {
            buttonList.add(new GuiButton(ID_REINFORCE, midX - 104, doneY, 100, 20, modeLabel()));
            buttonList.add(new GuiButton(ID_DONE, midX + 4, doneY, 100, 20, I18n.translateToLocal("gui.done")));
        } else {
            buttonList.add(new GuiButton(ID_DONE, midX - 50, doneY, 100, 20, I18n.translateToLocal("gui.done")));
        }

        if (!mayEdit) return;
        if (entityMode) {
            buttonList.add(
                new GuiButton(
                    ID_MOB_ADD,
                    midX - 100,
                    top + 186,
                    200,
                    20,
                    I18n.translateToLocal("trmtgtnh.editor.entityAdd")));
            buttonList.add(
                new GuiButton(
                    ID_MOB_REMOVE,
                    midX - 100,
                    top + 208,
                    200,
                    20,
                    I18n.translateToLocal("trmtgtnh.editor.entityRemove")));
            return;
        }
        buttonList.add(new GuiButton(ID_FAMILY, midX - 104, top + 186, 96, 20, chosenFamily().key()));
        buttonList.add(
            new GuiButton(ID_ASSIGN, midX + 8, top + 186, 96, 20, I18n.translateToLocal("trmtgtnh.editor.assign")));
        buttonList.add(
            new GuiButton(
                ID_EXCLUDE,
                midX - 100,
                top + 208,
                200,
                20,
                I18n.translateToLocal("trmtgtnh.editor.exclude")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_REACH_DOWN:
                reach = Math.max(0, reach - 1);
                break;
            case ID_REACH_UP:
                reach = Math.min(TrmtConfig.chunkTamperMaxReach, reach + 1);
                break;
            case ID_STEPS_DOWN:
                steps = Math.max(1, steps - 1);
                break;
            case ID_STEPS_UP:
                steps = Math.min(TrmtConfig.chunkTamperMaxSteps, steps + 1);
                break;
            case ID_FAMILY:
                nextFamily();
                button.displayString = chosenFamily().key();
                break;
            case ID_ASSIGN:
                TrmtNetwork.editFamily(PacketEditFamily.ACTION_ASSIGN, target, chosenFamily().ordinal());
                mc.displayGuiScreen(null);
                break;
            case ID_EXCLUDE:
                TrmtNetwork.editFamily(PacketEditFamily.ACTION_EXCLUDE, target, 0);
                mc.displayGuiScreen(null);
                break;
            case ID_MOB_ADD:
                TrmtNetwork.editMob(PacketEditMob.ACTION_ADD, entity);
                mc.displayGuiScreen(null);
                break;
            case ID_MOB_REMOVE:
                TrmtNetwork.editMob(PacketEditMob.ACTION_REMOVE, entity);
                mc.displayGuiScreen(null);
                break;
            case ID_REINFORCE:
                cycleMode();
                // Rebuild so the mode label and the Wayfarer's level button follow the new mode.
                initGui();
                break;
            case ID_REINFORCE_LEVEL:
                reinforceLevel = (reinforceLevel + 1) % (Math.min(3, TrmtConfig.reinforceMaxLevel) + 1);
                button.displayString = levelLabel();
                break;
            case ID_DONE:
                mc.displayGuiScreen(null);
                break;
            default:
                break;
        }
    }

    /**
     * Sent once, on the way out.
     *
     * <p>
     * Rather than on every click, which would be a packet per press of a button somebody is
     * about to press four more times.
     */
    private String levelLabel() {
        return I18n.translateToLocal("trmtgtnh.reinforce.level") + " " + reinforceLevel;
    }

    /** Whether this tool has any unlock mode to offer, so the mode button is drawn at all. */
    private boolean modeCapable() {
        return reinforceCapable || wardCapable || lightCapable;
    }

    /**
     * Advances to the next mode this tool can actually use.
     *
     * <p>
     * None, reinforce, ward, light, round to none - skipping any whose enchantment the tool does
     * not carry, so the button only ever lands on a mode the tool can use.
     */
    private void cycleMode() {
        int modes = ItemChunkTamper.MODE_LIGHT + 1;
        for (int step = 1; step <= modes; step++) {
            int next = (mode + step) % modes;
            if (next == ItemChunkTamper.MODE_NONE || capable(next)) {
                mode = next;
                return;
            }
        }
        mode = ItemChunkTamper.MODE_NONE;
    }

    /** Whether the tool carries what a given mode needs. */
    private boolean capable(int which) {
        if (which == ItemChunkTamper.MODE_REINFORCE) return reinforceCapable;
        if (which == ItemChunkTamper.MODE_WARD) return wardCapable;
        if (which == ItemChunkTamper.MODE_LIGHT) return lightCapable;
        return false;
    }

    private String modeLabel() {
        String key = mode == ItemChunkTamper.MODE_REINFORCE ? "trmtgtnh.mode.reinforce"
            : mode == ItemChunkTamper.MODE_WARD ? "trmtgtnh.mode.ward"
                : mode == ItemChunkTamper.MODE_LIGHT ? "trmtgtnh.mode.light" : "trmtgtnh.mode.off";
        return I18n.translateToLocal("trmtgtnh.mode.label") + " " + I18n.translateToLocal(key);
    }

    @Override
    public void onGuiClosed() {
        // Written locally too, so the tooltip is right the instant the screen shuts rather than
        // a round trip later. The server's copy is what counts; this is only what is shown.
        ItemChunkTamper.setReach(stack, reach);
        ItemChunkTamper.setSteps(stack, steps);
        ItemChunkTamper.setMode(stack, mode);
        ItemChunkTamper.setReinforceLevel(stack, reinforceLevel);
        TrmtNetwork.sendTamperSettings(reach, steps, mode, reinforceLevel);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        int midX = width / 2;
        int top = topAnchor();
        int edge = reach * 2 + 1;
        boolean free = stack.getItem() instanceof ItemChunkTamper && ((ItemChunkTamper) stack.getItem()).isFree(stack);
        // A chunk tamper is paid by bone meal's rule and switched by bone meal's setting, so with that
        // off even a plain one mends for nothing - and mending that costs nothing earns nothing.
        boolean costsMaterial = !free && TrmtConfig.bonemealCostsABlock;
        boolean xpOn = TrmtConfig.xpFromHealing && TrmtConfig.xpPerGradation > 0f;
        int per = Math.max(1, TrmtConfig.chunkTamperGradationsPerBlock);

        // The panel: a bordered dark surface with a gradient title bar, so the screen reads as
        // something built rather than as bare buttons on the dirt. Border first, fill inset one
        // pixel inside it.
        drawRect(midX - 121, top - 1, midX + 121, top + 145, 0xFF6A6A7A);
        drawRect(midX - 120, top, midX + 120, top + 144, 0xE6141420);
        drawGradientRect(midX - 120, top, midX + 120, top + 18, 0xFF32324A, 0xFF20202C);
        drawRect(midX - 120, top + 18, midX + 120, top + 19, 0xFF6A6A7A);
        drawRect(midX - 112, top + 44, midX + 112, top + 45, 0x30FFFFFF);

        // A faint backing behind each -/+ pair, so a setting reads as one unit rather than two
        // buttons with a number floating between them.
        drawRect(midX - 114, top + 49, midX + 114, top + 75, 0x40FFFFFF);
        drawRect(midX - 113, top + 50, midX + 113, top + 74, 0x14FFFFFF);
        drawRect(midX - 114, top + 77, midX + 114, top + 103, 0x40FFFFFF);
        drawRect(midX - 113, top + 78, midX + 113, top + 102, 0x14FFFFFF);
        drawRect(midX - 112, top + 107, midX + 112, top + 108, 0x30FFFFFF);

        // The operator editor is its own panel, in purple, below a gap - a different and rarer
        // action that must not be mistaken for a third setting.
        if (mayEdit) {
            drawRect(midX - 118, top + 154, midX + 118, top + 230, 0xFF7A5AA6);
            drawRect(midX - 117, top + 155, midX + 117, top + 229, 0xE61B1330);
            drawGradientRect(midX - 117, top + 155, midX + 117, top + 171, 0xFF3A2A55, 0xFF261A3A);
            drawRect(midX - 117, top + 171, midX + 117, top + 172, 0xFF7A5AA6);
        }

        // The title already carries the grade: "Diamond Chunk Tamper", "Wayfarer's Tamper".
        drawCenteredString(fontRenderer, stack.getDisplayName(), midX, top + 5, 0xFFFFFF);

        // Wrapped rather than drawn as one line - the descriptions are wider than the panel, and
        // a single centred line is exactly what smeared off the edge before.
        String descKey = free ? "trmtgtnh.magictamper.blurb" : "trmtgtnh.chunktamper.desc";
        List desc = fontRenderer.listFormattedStringToWidth(I18n.translateToLocal(descKey), 216);
        if (!desc.isEmpty()) drawCenteredString(fontRenderer, (String) desc.get(0), midX, top + 22, 0xB8B8C4);
        if (desc.size() > 1) drawCenteredString(fontRenderer, (String) desc.get(1), midX, top + 32, 0xB8B8C4);

        drawCenteredString(
            fontRenderer,
            I18n.translateToLocal("trmtgtnh.chunktamper.area") + " " + edge + "x" + edge + "x" + edge,
            midX,
            top + 58,
            0xFFFFFF);
        drawCenteredString(
            fontRenderer,
            I18n.translateToLocal("trmtgtnh.chunktamper.perUse") + " " + steps,
            midX,
            top + 86,
            0xFFFFFF);

        // Material cost, in the green the free case earns and the grey a price is quoted in. The free
        // case carries no experience line: only mending that was paid for earns any, which is what
        // the server holds to, so a line there would promise something that never arrives.
        if (!costsMaterial) {
            drawCenteredString(
                fontRenderer,
                I18n.translateToLocal("trmtgtnh.tamper.cost.free"),
                midX,
                top + 112,
                0x9FE0A0);
        } else {
            String costStr = I18n.translateToLocalFormatted("trmtgtnh.chunktamper.cost.rate", Integer.valueOf(per));
            List cost = fontRenderer.listFormattedStringToWidth(costStr, 216);
            if (!cost.isEmpty()) drawCenteredString(fontRenderer, (String) cost.get(0), midX, top + 112, 0xB8B8C4);
            if (cost.size() > 1) drawCenteredString(fontRenderer, (String) cost.get(1), midX, top + 122, 0xB8B8C4);
            if (xpOn) {
                drawCenteredString(
                    fontRenderer,
                    I18n.translateToLocal("trmtgtnh.tamper.xp"),
                    midX,
                    top + 132,
                    0x8CE08C);
            }
        }

        if (mayEdit) {
            drawCenteredString(
                fontRenderer,
                I18n.translateToLocal("trmtgtnh.gui.editor.section"),
                midX,
                top + 158,
                0xC9A0FF);
            String label;
            String shown;
            if (entityMode) {
                label = I18n.translateToLocal("trmtgtnh.editor.entityLast");
                shown = entity;
            } else {
                label = I18n.translateToLocal("trmtgtnh.editor.title");
                shown = target.length() > 30 ? target.substring(0, 29) + "..." : target;
            }
            drawCenteredString(fontRenderer, label + " " + shown, midX, top + 174, 0xE0D0FF);
        }

        super.drawScreen(mouseX, mouseY, partial);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
