package com.trmtgtnh.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.entity.ContainerGolem;
import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.network.PacketGolemHome;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The golem's orders, its tools, and its upgrade, on one screen.
 *
 * <p>
 * Drawn rather than skinned - the panel is rectangles in the mod's own colors, which saves an
 * asset and lets the screen be exactly as tall as whatever the golem is carrying. A plain golem
 * holds sixteen tools and a wide one sixty-four, and the screen grows to fit.
 *
 * <p>
 * The families are paged four at a time because there are ten of them and only so much room. Each
 * row is one order: what wear that ground is held at, or nothing at all, which is where they all
 * start.
 *
 * <p>
 * Two coordinate frames meet here and must not be confused. {@link #initGui} places buttons in
 * screen coordinates, so every button x and y is offset by {@code guiLeft}/{@code guiTop};
 * {@link #drawGuiContainerForegroundLayer} is called inside a translate by those same amounts, so
 * every string there is in panel coordinates and must not add them again. The vertical
 * measurements the two frames share live in {@link ContainerGolem}, because the container places
 * the slots from the same numbers and the two cannot be allowed to drift apart.
 */
@SideOnly(Side.CLIENT)
public class GuiGolem extends GuiContainer {

    private static final int ID_RADIUS_DOWN = 1;
    private static final int ID_RADIUS_UP = 2;
    private static final int ID_PAGE_PREV = 3;
    private static final int ID_PAGE_NEXT = 4;
    private static final int ID_FAMILY_BASE = 20;
    private static final int ID_HELD_BASE = 60;
    private static final int ID_HOME_HERE = 70;

    /** Panel coordinates: the left margin, and the right edge everything stops at. */
    private static final int MARGIN = ContainerGolem.MARGIN;
    private static final int RIGHT = 168;

    /** Buttons are twelve tall on a fourteen pitch, which is what leaves room for the inventory. */
    private static final int BUTTON_H = 12;
    private static final int ROW_PITCH = 14;
    private static final int W_STEP = 12;
    private static final int W_WORD = 20;

    /** The four button columns every order row shares, and the edge its value is set against. */
    private static final int COL_MINUS = 98;
    private static final int COL_PLUS = 112;
    private static final int COL_OFF = 126;
    private static final int COL_LAST = 148;
    private static final int VALUE_RIGHT = 96;

    /** How wide a family name and a value may be before they are trimmed to fit their column. */
    private static final int NAME_WIDTH = 36;
    private static final int VALUE_WIDTH = 49;

    /** The status line, between the title bar and the first row of storage. */
    private static final int STATUS_DY = 19;

    /** Rows within the orders block, measured from its top. */
    private static final int HEADER_DY = 0;
    private static final int FAMILY_DY = 15;
    private static final int HELD_NAME_DY = 58;
    private static final int HELD_ROW_DY = 68;
    private static final int HOME_DY = 82;

    /** The radius controls and the page arrows, on the header row. */
    private static final int COL_RADIUS_UP = 84;
    private static final int COL_PAGE_PREV = 104;
    private static final int COL_PAGE_NEXT = 156;
    private static final int RADIUS_TEXT_X = 24;
    private static final int PAGE_TEXT_MID = 136;

    private final EntityGolemOfWays golem;

    private final SurfaceFamily[] families;

    /**
     * How many rows of storage the screen is currently drawn for.
     *
     * <p>
     * Not final, because the storage upgrade changes it: the panel grows from two rows to eight
     * while the window is open, and the orders and the player's inventory move down with it.
     */
    private int storageRows;

    private int page;

    /**
     * The field open over the home readout, or null while it is only a readout.
     *
     * <p>
     * One field rather than three, because three numbers typed one box at a time is three times
     * the tabbing for a thing somebody is going to paste in from the debug screen anyway.
     */
    private GuiTextField typing;

    private ContainerGolem orders() {
        return (ContainerGolem) inventorySlots;
    }

    public GuiGolem(InventoryPlayer playerInventory, EntityGolemOfWays golem) {
        super(new ContainerGolem(playerInventory, golem));
        this.golem = golem;
        this.storageRows = orders().shownRows();
        this.families = stagedFamilies();
        this.xSize = 176;
        this.ySize = ContainerGolem.panelHeight(storageRows);
    }

    /**
     * Re-measures the panel for however wide the golem has just become and lays it out again.
     *
     * <p>
     * The container has already moved its slots; this moves everything drawn around them. Both are
     * driven from the golem's own watched upgrade, so the screen follows the slot the moment the
     * upgrade lands in it rather than the next time the window is opened.
     */
    private void resize() {
        // The field remembers where it was put and nothing moves it, so a panel that has just
        // grown or shrunk under it leaves it floating over the wrong row. Dropped rather than
        // moved: whatever was half typed into it was about a screen that no longer exists.
        closeHomeField();
        this.storageRows = orders().shownRows();
        this.ySize = ContainerGolem.panelHeight(storageRows);
        initGui();
    }

    private static SurfaceFamily[] stagedFamilies() {
        int count = 0;
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (family.staged) count++;
        }
        SurfaceFamily[] out = new SurfaceFamily[count];
        int at = 0;
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (family.staged) out[at++] = family;
        }
        return out;
    }

    private int pages() {
        return Math.max(1, (families.length + ContainerGolem.ROWS_PER_PAGE - 1) / ContainerGolem.ROWS_PER_PAGE);
    }

    /** The top of the orders block, in panel coordinates. */
    private int ordersTop() {
        return ContainerGolem.ordersTop(storageRows);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();

        int left = guiLeft;
        int ordersTop = guiTop + ordersTop();
        String off = I18n.translateToLocal("trmtgtnh.golem.gui.off");

        // Header: the radius on the left, the page arrows on the right.
        buttonList.add(new Flat(ID_RADIUS_DOWN, left + MARGIN, ordersTop + HEADER_DY, W_STEP, BUTTON_H, "-"));
        buttonList.add(new Flat(ID_RADIUS_UP, left + COL_RADIUS_UP, ordersTop + HEADER_DY, W_STEP, BUTTON_H, "+"));
        buttonList.add(new Flat(ID_PAGE_PREV, left + COL_PAGE_PREV, ordersTop + HEADER_DY, W_STEP, BUTTON_H, "<"));
        buttonList.add(new Flat(ID_PAGE_NEXT, left + COL_PAGE_NEXT, ordersTop + HEADER_DY, W_STEP, BUTTON_H, ">"));

        for (int row = 0; row < ContainerGolem.ROWS_PER_PAGE; row++) {
            int index = page * ContainerGolem.ROWS_PER_PAGE + row;
            if (index >= families.length) break;
            int y = ordersTop + FAMILY_DY + row * ROW_PITCH;
            buttonList.add(new Flat(ID_FAMILY_BASE + row * 4, left + COL_MINUS, y, W_STEP, BUTTON_H, "-"));
            buttonList.add(new Flat(ID_FAMILY_BASE + row * 4 + 1, left + COL_PLUS, y, W_STEP, BUTTON_H, "+"));
            buttonList.add(new Flat(ID_FAMILY_BASE + row * 4 + 2, left + COL_OFF, y, W_WORD, BUTTON_H, off));
            buttonList.add(new Flat(ID_FAMILY_BASE + row * 4 + 3, left + COL_LAST, y, W_WORD, BUTTON_H, "0%"));
        }

        // The held-block row, whose name has the line above to itself so a long one has room.
        int heldY = ordersTop + HELD_ROW_DY;
        buttonList.add(new Flat(ID_HELD_BASE, left + COL_MINUS, heldY, W_STEP, BUTTON_H, "-"));
        buttonList.add(new Flat(ID_HELD_BASE + 1, left + COL_PLUS, heldY, W_STEP, BUTTON_H, "+"));
        buttonList.add(new Flat(ID_HELD_BASE + 2, left + COL_OFF, heldY, W_WORD, BUTTON_H, off));
        buttonList.add(
            new Flat(
                ID_HELD_BASE + 3,
                left + COL_LAST,
                heldY,
                W_WORD,
                BUTTON_H,
                I18n.translateToLocal("trmtgtnh.golem.gui.any")));

        buttonList.add(
            new Flat(
                ID_HOME_HERE,
                left + COL_OFF,
                ordersTop + HOME_DY,
                RIGHT - COL_OFF,
                BUTTON_H,
                I18n.translateToLocal("trmtgtnh.golem.gui.here")));

        greyHeldRow();
    }

    /**
     * Greys the held-block buttons out when nothing orderable is in hand.
     *
     * <p>
     * Kept enabled-or-not rather than added-and-removed, so the row does not appear and vanish
     * under the cursor as the player scrolls the hotbar.
     */
    @Override
    public void updateScreen() {
        super.updateScreen();
        // The client's own copy of the golem learns about the upgrade through its watched value,
        // so this is where the change is noticed - there is no packet of the screen's own and none
        // is wanted.
        if (orders().relayout()) resize();
        if (typing != null) typing.updateCursorCounter();
        greyHeldRow();
    }

    private void greyHeldRow() {
        boolean holding = orders().shownHeldTarget() != ContainerGolem.HELD_NONE;
        for (Object each : buttonList) {
            GuiButton button = (GuiButton) each;
            if (button.id >= ID_HELD_BASE && button.id < ID_HELD_BASE + 4) button.enabled = holding;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_PAGE_PREV) {
            page = (page - 1 + pages()) % pages();
            initGui();
            return;
        }
        if (button.id == ID_PAGE_NEXT) {
            page = (page + 1) % pages();
            initGui();
            return;
        }
        if (button.id == ID_RADIUS_DOWN) {
            send(ContainerGolem.BUTTON_RADIUS_DOWN);
            return;
        }
        if (button.id == ID_RADIUS_UP) {
            send(ContainerGolem.BUTTON_RADIUS_UP);
            return;
        }
        if (button.id == ID_HOME_HERE) {
            closeHomeField();
            send(ContainerGolem.BUTTON_HOME_HERE);
            return;
        }
        if (button.id >= ID_HELD_BASE && button.id < ID_HELD_BASE + 4) {
            send(ContainerGolem.HELD_BASE + (button.id - ID_HELD_BASE));
            return;
        }
        if (button.id >= ID_FAMILY_BASE) {
            int offset = button.id - ID_FAMILY_BASE;
            int row = offset / 4;
            int action = offset % 4;
            int index = page * ContainerGolem.ROWS_PER_PAGE + row;
            if (index >= families.length) return;
            send(ContainerGolem.FAMILY_BASE + families[index].ordinal() * ContainerGolem.FAMILY_STRIDE + action);
        }
    }

    /**
     * Turns the readout into a field, pre-filled with what it was showing.
     *
     * <p>
     * Made on demand and thrown away on the first Enter or Escape, because a field that is always
     * there is a field that swallows every key the screen has - and this screen closes on E like
     * every other container.
     */
    private void openHomeField() {
        // Clear of the word "home" on its left and of the button on its right, both measured
        // rather than guessed: the label runs to thirty-two and the button starts at COL_OFF.
        typing = new GuiTextField(0, fontRenderer, guiLeft + 34, guiTop + ordersTop() + HOME_DY, 88, 12);
        typing.setMaxStringLength(40);
        // Held keys repeat only while a screen asks for it, and a coordinate is the one thing on
        // this screen anybody ever holds backspace through.
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        typing.setText(homeText());
        typing.setFocused(true);
        typing.setCursorPositionEnd();
    }

    /** Puts the field away and gives back the key repeat it borrowed. */
    private void closeHomeField() {
        typing = null;
        org.lwjgl.input.Keyboard.enableRepeatEvents(false);
    }

    /**
     * Reads a typed home and sends it, or says nothing and closes.
     *
     * <p>
     * Three numbers separated by whatever somebody felt like typing - spaces, commas, or both -
     * because a coordinate copied out of the debug screen has commas in it and one read off a sign
     * does not. The word "here" is taken as well, for the same reason the command has one: it is
     * the shortest way to say the thing people mean most often.
     */
    private void commitHomeField() {
        if (typing == null) return;
        String typed = typing.getText()
            .trim();
        closeHomeField();
        if (typed.isEmpty()) return;

        if (typed.equalsIgnoreCase(I18n.translateToLocal("trmtgtnh.golem.gui.here"))
            || "here".equalsIgnoreCase(typed)) {
            send(ContainerGolem.BUTTON_HOME_HERE);
            return;
        }

        String[] parts = typed.replace(',', ' ')
            .trim()
            .split("\\s+");
        if (parts.length != 3) return;
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            // The same bounds the server keeps, asked here as well so a place it would refuse is
            // one the screen never claims to have sent.
            if (y < 0 || y > 255) return;
            if (Math.abs(x - Math.floor(golem.posX)) > PacketGolemHome.MAX_MOVE) return;
            if (Math.abs(z - Math.floor(golem.posZ)) > PacketGolemHome.MAX_MOVE) return;
            TrmtNetwork.setGolemHome(golem.getEntityId(), x, y, z);
        } catch (NumberFormatException notAPlace) {
            // Silently, and the row goes back to what it was showing. A screen that scolded
            // somebody for mistyping would be a screen that had to find somewhere to scold them.
        }
    }

    /** Buttons ride the container's own channel; see {@link ContainerGolem#enchantItem}. */
    private void send(int id) {
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, id);
    }

    /**
     * The readout is its own click target, which is what makes typing a home discoverable.
     *
     * <p>
     * A field beside the button would say what it was for and cost a row nobody needs most of the
     * time. A readout that turns into a field costs nothing, and the thing it turns into is
     * pre-filled with the thing it was showing - so the gesture reads as editing what is there
     * rather than as answering a question.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        super.drawScreen(mouseX, mouseY, partial);
        // After everything else and in screen coordinates, which is where the box was placed.
        if (typing != null) {
            // The container's draw leaves the item lighting on for the stack under the cursor, and
            // a flat box drawn under that comes out tinted and depth-tested against nothing.
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            typing.drawTextBox();
            return;
        }
        // A readout that can be typed into looks exactly like one that cannot, so the only thing
        // standing between the feature and nobody finding it is this line.
        if (!overHome(mouseX, mouseY)) return;
        java.util.List hint = new java.util.ArrayList();
        hint.add(I18n.translateToLocal("trmtgtnh.golem.gui.homeHint"));
        drawHoveringText(hint, mouseX, mouseY, fontRenderer);
    }

    /** Whether the pointer is inside the open field, which decides whose click it is. */
    private boolean withinField(int mouseX, int mouseY) {
        if (typing == null) return false;
        int y = guiTop + ordersTop() + HOME_DY;
        return mouseX >= guiLeft + 34 && mouseX < guiLeft + 34 + 88 && mouseY >= y && mouseY < y + 12;
    }

    /** Whether the pointer is over the home readout, which is both a click target and a tooltip. */
    private boolean overHome(int mouseX, int mouseY) {
        int y = guiTop + ordersTop() + HOME_DY;
        return mouseX >= guiLeft + MARGIN && mouseX <= guiLeft + COL_OFF - 2 && mouseY >= y && mouseY <= y + BUTTON_H;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        if (typing != null) {
            // Inside the box the click is the box's: it moves the cursor, and a right click
            // empties it, which is what every other field in the game does. Anywhere else the
            // click was meant for the screen, so the field is put away and the click goes on to
            // whatever it was aimed at - a slot, a button, or nothing.
            if (withinField(mouseX, mouseY)) {
                typing.mouseClicked(mouseX, mouseY, button);
                if (button == 1) typing.setText("");
                return;
            }
            closeHomeField();
        }
        if (overHome(mouseX, mouseY)) {
            openHomeField();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        closeHomeField();
    }

    @Override
    protected void keyTyped(char typedChar, int key) throws java.io.IOException {
        if (typing == null) {
            super.keyTyped(typedChar, key);
            return;
        }
        // Escape closes the field rather than the screen, which is what every other field in the
        // game does and what somebody halfway through a coordinate expects.
        if (key == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            closeHomeField();
            return;
        }
        if (key == org.lwjgl.input.Keyboard.KEY_RETURN || key == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
            commitHomeField();
            return;
        }
        typing.textboxKeyTyped(typedChar, key);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        int left = guiLeft;
        int top = guiTop;
        int ordersTop = ordersTop();
        int inventoryTop = ContainerGolem.playerTop(storageRows);

        drawRect(left - 1, top - 1, left + xSize + 1, top + ySize + 1, 0xFF3A2E1E);
        drawRect(left, top, left + xSize, top + ySize, 0xF2D9CDB2);
        drawGradientRect(left, top, left + xSize, top + 16, 0xFF6B5433, 0xFF54401F);

        // Slot wells, so the storage reads as slots even without a skin.
        for (int row = 0; row < storageRows; row++) {
            for (int column = 0; column < ContainerGolem.STORAGE_COLUMNS; column++) {
                well(left + MARGIN + column * 18, top + ContainerGolem.STORAGE_TOP + row * 18);
            }
        }
        if (golem.upgradesAllowed()) {
            // A rule between the storage and the upgrade, so the gap reads as a division rather
            // than as a missing slot.
            int railTop = top + ContainerGolem.STORAGE_TOP;
            int railBottom = railTop + storageRows * 18 - 2;
            drawRect(left + 148, railTop, left + 149, railBottom, 0xFF8B7B5E);
            upgradeWell(left + ContainerGolem.UPGRADE_X, top + ContainerGolem.upgradeSlotY(storageRows));
        }

        // The orders sit in a panel of their own, so the block reads as one thing.
        int panelTop = top + ordersTop - 3;
        int panelBottom = top + ordersTop + ContainerGolem.ORDERS_HEIGHT + 3;
        drawRect(left + 5, panelTop, left + xSize - 5, panelBottom, 0xFF8B7B5E);
        drawRect(left + 6, panelTop + 1, left + xSize - 6, panelBottom - 1, 0xFFCFC2A6);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                well(left + MARGIN + column * 18, top + inventoryTop + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            well(left + MARGIN + column * 18, top + inventoryTop + 58);
        }
    }

    /**
     * The upgrade's own well: the same size, in warmer metal, with a second frame around it.
     *
     * <p>
     * One slot among sixteen identical ones is invisible however it is placed, so this one is not
     * identical. The extra ring costs two rectangles and is the difference between a slot somebody
     * finds and a slot somebody asks about.
     */
    private void upgradeWell(int x, int y) {
        drawRect(x - 3, y - 3, x + 19, y + 19, 0xFF6B5433);
        drawRect(x - 2, y - 2, x + 18, y + 18, 0xFFA98A55);
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF6B5433);
        drawRect(x, y, x + 16, y + 16, 0xFFC7B48D);
    }

    private void well(int x, int y) {
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF8B7B5E);
        drawRect(x, y, x + 16, y + 16, 0xFFBFB197);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int ordersTop = ordersTop();

        String name = golem.getName();
        fontRenderer.drawString(name, MARGIN, 4, 0xFFE9C9);

        // Whose golem this is, in the title bar, where it costs the orders no room. The watched
        // copy rather than the field: the field is only ever written on the server, and this screen
        // is looking at the client's own copy of the entity, whose field has never been anything
        // but empty - so the line reserved room for here was never once drawn. The WAILA tooltip
        // beside it always read the watched copy and always worked, which is what made this look
        // like the screen had lost the line rather than never having had it.
        String builder = golem.watchedSummoner();
        if (builder != null && !builder.isEmpty()) {
            String built = I18n.translateToLocal("trmtgtnh.golem.gui.built") + " " + builder;
            int width = fontRenderer.getStringWidth(built);
            if (RIGHT - width > MARGIN + fontRenderer.getStringWidth(name) + 6) {
                fontRenderer.drawString(built, RIGHT - width, 4, 0xC9B48D);
            }
        }

        // What it is holding, or why it is holding nothing - which is the one fact that explains
        // every other thing a golem is or is not doing. With no tamper it works at nothing, mends
        // nothing and runs from everything, and until this line existed the only way to find that
        // out was to notice it doing nothing and go and read WAILA.
        //
        // Two independent channels are consulted, not one. The watched armed bit is the golem's
        // own account of its slots; the equipment slot is what the renderer actually draws from.
        // In ordinary running they agree, and if they ever do not, that is worth seeing rather
        // than averaging into a single cheerful answer.
        String note;
        int color;
        ItemStack tool = golem.getHeldItemMainhand();
        if (!golem.isArmed()) {
            note = I18n.translateToLocal("trmtgtnh.golem.gui.noTamper");
            color = 0xB03A2A;
        } else if (tool == null) {
            note = I18n.translateToLocal("trmtgtnh.golem.gui.toolMissing");
            color = 0xB03A2A;
        } else if (!golem.hasMendingStock()) {
            note = I18n.translateToLocal("trmtgtnh.golem.gui.noStock");
            color = 0x8A6A2A;
        } else if (!golem.watchedOrders()) {
            note = I18n.translateToLocal("trmtgtnh.golem.gui.noOrders");
            color = 0x8A7A5E;
        } else {
            note = tool.getDisplayName();
            color = 0x3A5A2A;
        }
        fontRenderer.drawString(trim(note, RIGHT - MARGIN), MARGIN, STATUS_DY, color);

        int headerY = ordersTop + HEADER_DY + 2;
        fontRenderer.drawString(
            I18n.translateToLocal("trmtgtnh.golem.gui.radius") + " " + golem.watchedRadius(),
            RADIUS_TEXT_X,
            headerY,
            0x3A2A14);

        String pageLabel = (page + 1) + "/" + pages();
        fontRenderer
            .drawString(pageLabel, PAGE_TEXT_MID - fontRenderer.getStringWidth(pageLabel) / 2, headerY, 0x3A2A14);

        for (int row = 0; row < ContainerGolem.ROWS_PER_PAGE; row++) {
            int index = page * ContainerGolem.ROWS_PER_PAGE + row;
            if (index >= families.length) break;
            SurfaceFamily family = families[index];
            int y = ordersTop + FAMILY_DY + row * ROW_PITCH + 2;

            fontRenderer.drawString(trim(family.key(), NAME_WIDTH), MARGIN, y, 0x3A2A14);

            int target = orders().shownTargetFor(family);
            String shown = target < 0 ? I18n.translateToLocal("trmtgtnh.golem.gui.ignored") : target + "%";
            value(trim(shown, VALUE_WIDTH), y, target < 0 ? 0x8A7A5E : 0x2A5A2A);
        }

        drawHeldRow(ordersTop);
        drawHomeRow(ordersTop);
    }

    /**
     * The home row: where the golem walks back to, and the two ways of moving it.
     *
     * <p>
     * A golem's home is the one thing about it with no visible answer anywhere. It is set when the
     * shape is built and never again unless somebody knows there is a command for it, and every
     * boundary the golem has - what it works, how far it fights, where it wanders, what it walks
     * back to - is measured from it. A number nothing shows and everything depends on is the kind
     * of thing people file bugs about.
     *
     * <p>
     * Two ways of moving it because they answer different questions. The button is for "here, where
     * I am standing", which is what somebody looking at a road wants; typing is for a place you can
     * read off a map or a sign but cannot conveniently walk to.
     */
    private void drawHomeRow(int ordersTop) {
        int y = ordersTop + HOME_DY + 2;
        fontRenderer
            .drawString(trim(I18n.translateToLocal("trmtgtnh.golem.gui.home"), NAME_WIDTH), MARGIN, y, 0x3A2A14);

        // Nothing where the readout was while the field is open: the field itself is drawn in
        // screen coordinates from drawScreen, because this method runs inside a translate by the
        // panel's corner and a box placed at a screen position would land at twice it.
        if (typing != null) return;
        value(trim(homeText(), VALUE_WIDTH + 24), y, 0x3A2A14);
    }

    /** The home as it reads on the row, and as it is pre-filled into the field. */
    private String homeText() {
        int[] home = golem.watchedHome();
        return home[0] + " " + home[1] + " " + home[2];
    }

    /**
     * One row for the block in hand, whose order beats its family's.
     *
     * <p>
     * The block being configured is simply the one held, which needs no list to scroll and no
     * picker: point the golem at a stack and the row is about that stack. When nothing orderable is
     * in hand the row says so rather than disappearing, so its buttons keep their place. The name
     * has the line above to itself, because an item name is as long as whichever mod named it
     * decided it was.
     */
    private void drawHeldRow(int ordersTop) {
        int nameY = ordersTop + HELD_NAME_DY;
        int rowY = ordersTop + HELD_ROW_DY + 2;
        int shown = orders().shownHeldTarget();

        if (shown == ContainerGolem.HELD_NONE) {
            fontRenderer.drawString(
                trim(I18n.translateToLocal("trmtgtnh.golem.gui.holdBlock"), RIGHT - MARGIN),
                MARGIN,
                nameY,
                0x8A7A5E);
            return;
        }

        ItemStack held = mc.player.getHeldItemMainhand();
        String name = held == null ? "?" : held.getDisplayName();
        fontRenderer.drawString(trim(name, RIGHT - MARGIN), MARGIN, nameY, 0x3A2A14);

        String text;
        int color;
        if (shown == EntityGolemOfWays.NO_BLOCK_TARGET) {
            text = I18n.translateToLocal("trmtgtnh.golem.gui.asFamily");
            color = 0x8A7A5E;
        } else if (shown < 0) {
            text = I18n.translateToLocal("trmtgtnh.golem.gui.ignored");
            color = 0x8A7A5E;
        } else {
            text = shown + "%";
            color = 0x2A5A2A;
        }
        value(trim(text, VALUE_RIGHT - MARGIN), rowY, color);
    }

    /** Sets a value against the right edge of the value column, clear of the first button. */
    private void value(String text, int y, int color) {
        fontRenderer.drawString(text, VALUE_RIGHT - fontRenderer.getStringWidth(text), y, color);
    }

    /** Keeps a string inside its column, whatever a translation or another mod called the thing. */
    private String trim(String text, int width) {
        if (fontRenderer.getStringWidth(text) <= width) return text;
        return fontRenderer.trimStringToWidth(text, width - 6) + "...";
    }

    /**
     * A small flat button in the panel's own colors.
     *
     * <p>
     * Vanilla's widget texture is twenty pixels tall and is blitted at whatever height the button
     * was given, so a fourteen-pixel button loses the bottom of its own frame and a column of them
     * looks smeared. Four rows of proper twenty-pixel buttons will not fit above the inventory, so
     * these are drawn instead - which the rest of this screen already is.
     */
    private static final class Flat extends GuiButton {

        private static final int BORDER = 0xFF6B5433;
        private static final int FACE = 0xFFB5A487;
        private static final int FACE_OVER = 0xFFD8C9AA;
        private static final int FACE_OFF = 0xFFBCB4A2;
        private static final int LABEL = 0xFF2A1E0E;
        private static final int LABEL_OFF = 0xFF8A8272;

        Flat(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;

            boolean over = enabled && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

            int face = !enabled ? FACE_OFF : over ? FACE_OVER : FACE;
            drawRect(x, y, x + width, y + height, BORDER);
            drawRect(x + 1, y + 1, x + width - 1, y + height - 1, face);

            FontRenderer font = mc.fontRenderer;
            font.drawString(
                displayString,
                x + (width - font.getStringWidth(displayString)) / 2,
                y + (height - 8) / 2 + 1,
                enabled ? LABEL : LABEL_OFF);
        }
    }
}
