package com.trmtgtnh.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.entity.ContainerGolem;
import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.network.PacketGolemHome;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The golem's orders, its tools, and its upgrade, on one screen.
 *
 * <p>
 * Drawn rather than skinned - the panel is rectangles in the mod's own colours, which saves an
 * asset and lets the screen be exactly as tall as whatever the golem is carrying. A plain golem
 * holds sixteen tools and a wide one sixty-four, and the screen grows to fit.
 *
 * <p>
 * The families are paged four at a time because there are ten of them and only so much room. Each
 * row is one order: what wear that ground is held at, or nothing at all, which is where they all
 * start.
 *
 * <p>
 * Two coordinate frames meet here and must not be confused. {@link #init} places buttons in
 * screen coordinates, so every button x and y is offset by {@code leftPos}/{@code topPos};
 * {@link #renderLabels} is called inside a translate by those same amounts, so
 * every string there is in panel coordinates and must not add them again. The vertical
 * measurements the two frames share live in {@link ContainerGolem}, because the container places
 * the slots from the same numbers and the two cannot be allowed to drift apart.
 */
public class GuiGolem extends AbstractContainerScreen<ContainerGolem> {

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
    private EditBox typing;

    private ContainerGolem orders() {
        // No cast: a screen names its own menu type here, so the base class already holds one.
        return menu;
    }

    public GuiGolem(ContainerGolem orders, Inventory playerInventory,
        net.minecraft.network.chat.Component title) {
        super(orders, playerInventory, title);
        this.golem = orders.golem();
        this.storageRows = orders().shownRows();
        this.families = stagedFamilies();
        this.imageWidth = 176;
        this.imageHeight = ContainerGolem.panelHeight(storageRows);
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
        this.imageHeight = ContainerGolem.panelHeight(storageRows);
        init();
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
    public void init() {
        super.init();
        // Both lists, and both are needed. The superclass clears them before it calls this, so on
        // the ordinary path these do nothing - but this screen calls init() again itself when the
        // page or the storage width changes, and a stale entry left in `children` still takes
        // clicks from a button that is no longer drawn.
        buttons.clear();
        children.clear();

        int left = leftPos;
        int ordersTop = topPos + ordersTop();
        String off = com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.off");

        // Header: the radius on the left, the page arrows on the right.
        addButton(new Flat(this, ID_RADIUS_DOWN, left + MARGIN, ordersTop + HEADER_DY, W_STEP, BUTTON_H, "-"));
        addButton(new Flat(this, ID_RADIUS_UP, left + COL_RADIUS_UP, ordersTop + HEADER_DY, W_STEP, BUTTON_H, "+"));
        addButton(new Flat(this, ID_PAGE_PREV, left + COL_PAGE_PREV, ordersTop + HEADER_DY, W_STEP, BUTTON_H, "<"));
        addButton(new Flat(this, ID_PAGE_NEXT, left + COL_PAGE_NEXT, ordersTop + HEADER_DY, W_STEP, BUTTON_H, ">"));

        for (int row = 0; row < ContainerGolem.ROWS_PER_PAGE; row++) {
            int index = page * ContainerGolem.ROWS_PER_PAGE + row;
            if (index >= families.length) break;
            int y = ordersTop + FAMILY_DY + row * ROW_PITCH;
            addButton(new Flat(this, ID_FAMILY_BASE + row * 4, left + COL_MINUS, y, W_STEP, BUTTON_H, "-"));
            addButton(new Flat(this, ID_FAMILY_BASE + row * 4 + 1, left + COL_PLUS, y, W_STEP, BUTTON_H, "+"));
            addButton(new Flat(this, ID_FAMILY_BASE + row * 4 + 2, left + COL_OFF, y, W_WORD, BUTTON_H, off));
            addButton(new Flat(this, ID_FAMILY_BASE + row * 4 + 3, left + COL_LAST, y, W_WORD, BUTTON_H, "0%"));
        }

        // The held-block row, whose name has the line above to itself so a long one has room.
        int heldY = ordersTop + HELD_ROW_DY;
        addButton(new Flat(this, ID_HELD_BASE, left + COL_MINUS, heldY, W_STEP, BUTTON_H, "-"));
        addButton(new Flat(this, ID_HELD_BASE + 1, left + COL_PLUS, heldY, W_STEP, BUTTON_H, "+"));
        addButton(new Flat(this, ID_HELD_BASE + 2, left + COL_OFF, heldY, W_WORD, BUTTON_H, off));
        addButton(
            new Flat(this, 
                ID_HELD_BASE + 3,
                left + COL_LAST,
                heldY,
                W_WORD,
                BUTTON_H,
                com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.any")));

        addButton(
            new Flat(this, 
                ID_HOME_HERE,
                left + COL_OFF,
                ordersTop + HOME_DY,
                RIGHT - COL_OFF,
                BUTTON_H,
                com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.here")));

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
    public void tick() {
        super.tick();
        // The client's own copy of the golem learns about the upgrade through its watched value,
        // so this is where the change is noticed - there is no packet of the screen's own and none
        // is wanted.
        if (orders().relayout()) resize();
        if (typing != null) typing.tick();
        greyHeldRow();
    }

    private void greyHeldRow() {
        boolean holding = orders().shownHeldTarget() != ContainerGolem.HELD_NONE;
        for (net.minecraft.client.gui.components.AbstractWidget each : buttons) {
            if (!(each instanceof Flat)) continue;
            int id = ((Flat) each).id;
            if (id >= ID_HELD_BASE && id < ID_HELD_BASE + 4) each.active = holding;
        }
    }

    void actionPerformed(int id) {
        if (id == ID_PAGE_PREV) {
            page = (page - 1 + pages()) % pages();
            init();
            return;
        }
        if (id == ID_PAGE_NEXT) {
            page = (page + 1) % pages();
            init();
            return;
        }
        if (id == ID_RADIUS_DOWN) {
            send(ContainerGolem.BUTTON_RADIUS_DOWN);
            return;
        }
        if (id == ID_RADIUS_UP) {
            send(ContainerGolem.BUTTON_RADIUS_UP);
            return;
        }
        if (id == ID_HOME_HERE) {
            closeHomeField();
            send(ContainerGolem.BUTTON_HOME_HERE);
            return;
        }
        if (id >= ID_HELD_BASE && id < ID_HELD_BASE + 4) {
            send(ContainerGolem.HELD_BASE + (id - ID_HELD_BASE));
            return;
        }
        if (id >= ID_FAMILY_BASE) {
            int offset = id - ID_FAMILY_BASE;
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
        typing = new EditBox(
            font,
            leftPos + 34,
            topPos + ordersTop() + HOME_DY,
            88,
            12,
            net.minecraft.network.chat.TextComponent.EMPTY);
        typing.setMaxLength(40);
        // Held keys repeat only while a screen asks for it, and a coordinate is the one thing on
        // this screen anybody ever holds backspace through. Asked of the keyboard handler now
        // rather than of a static on the input library.
        minecraft.keyboardHandler.setSendRepeatsToGui(true);
        typing.setValue(homeText());
        typing.setFocus(true);
        typing.moveCursorToEnd();
        // So the field takes keys and clicks at all. 1.12.2 asks a screen's text fields one at a
        // time from keyTyped; here a widget has to be a child of the screen to be offered anything,
        // and this one is made on demand rather than in init, so it says so itself.
        children.add(typing);
    }

    /** Puts the field away and gives back the key repeat it borrowed. */
    private void closeHomeField() {
        if (typing != null) children.remove(typing);
        typing = null;
        minecraft.keyboardHandler.setSendRepeatsToGui(false);
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
        String typed = typing.getValue()
            .trim();
        closeHomeField();
        if (typed.isEmpty()) return;

        if (typed.equalsIgnoreCase(com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.here"))
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
            if (Math.abs(x - Math.floor(golem.getX())) > PacketGolemHome.MAX_MOVE) return;
            if (Math.abs(z - Math.floor(golem.getZ())) > PacketGolemHome.MAX_MOVE) return;
            TrmtNetwork.setGolemHome(golem.getId(), x, y, z);
        } catch (NumberFormatException notAPlace) {
            // Silently, and the row goes back to what it was showing. A screen that scolded
            // somebody for mistyping would be a screen that had to find somewhere to scold them.
        }
    }

    /** Buttons ride the container's own channel; see {@link ContainerGolem#enchantItem}. */
    private void send(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
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
    public void render(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partial) {
        super.render(pose, mouseX, mouseY, partial);
        // After everything else and in screen coordinates, which is where the box was placed.
        if (typing != null) {
            // Three lines gone, and they were a workaround rather than a drawing. The other
            // edition's container draw leaves the fixed-function item lighting on for the stack
            // under the cursor, so a flat box drawn afterwards came out tinted and depth-tested
            // against nothing, and it had to put all of that back by hand. There is no global
            // lighting state to be left on here: a box is drawn through the buffer with its own
            // render type, so it is drawn and that is all.
            typing.render(pose, mouseX, mouseY, partial);
            return;
        }
        // A readout that can be typed into looks exactly like one that cannot, so the only thing
        // standing between the feature and nobody finding it is this line.
        if (!overHome(mouseX, mouseY)) return;
        renderComponentTooltip(
            pose,
            java.util.Collections.<net.minecraft.network.chat.Component>singletonList(
                new net.minecraft.network.chat.TextComponent(
                    com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.homeHint"))),
            mouseX,
            mouseY);
    }

    /** Whether the pointer is inside the open field, which decides whose click it is. */
    private boolean withinField(double mouseX, double mouseY) {
        if (typing == null) return false;
        int y = topPos + ordersTop() + HOME_DY;
        return mouseX >= leftPos + 34 && mouseX < leftPos + 34 + 88 && mouseY >= y && mouseY < y + 12;
    }

    /** Whether the pointer is over the home readout, which is both a click target and a tooltip. */
    private boolean overHome(double mouseX, double mouseY) {
        int y = topPos + ordersTop() + HOME_DY;
        return mouseX >= leftPos + MARGIN && mouseX <= leftPos + COL_OFF - 2 && mouseY >= y && mouseY <= y + BUTTON_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (typing != null) {
            // Inside the box the click is the box's: it moves the cursor, and a right click
            // empties it, which is what every other field in the game does. Anywhere else the
            // click was meant for the screen, so the field is put away and the click goes on to
            // whatever it was aimed at - a slot, a button, or nothing.
            if (withinField(mouseX, mouseY)) {
                typing.mouseClicked(mouseX, mouseY, button);
                if (button == 1) typing.setValue("");
                return true;
            }
            closeHomeField();
        }
        if (overHome(mouseX, mouseY)) {
            openHomeField();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        super.removed();
        closeHomeField();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (typing == null) return super.keyPressed(key, scan, modifiers);
        // Escape closes the field rather than the screen, which is what every other field in the
        // game does and what somebody halfway through a coordinate expects.
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            closeHomeField();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            commitHomeField();
            return true;
        }
        // Before the screen, so the field gets its own backspace and arrows rather than the screen
        // closing on whatever key happens to be bound to the inventory.
        if (typing.keyPressed(key, scan, modifiers)) return true;
        return super.keyPressed(key, scan, modifiers);
    }

    /** Everything typed, as text, which is the half of the old hook that was about characters. */
    @Override
    public boolean charTyped(char typed, int modifiers) {
        if (typing != null && typing.charTyped(typed, modifiers)) return true;
        return super.charTyped(typed, modifiers);
    }

    @Override
    protected void renderBg(com.mojang.blaze3d.vertex.PoseStack pose, float partial, int mouseX,
        int mouseY) {
        int left = leftPos;
        int top = topPos;
        int ordersTop = ordersTop();
        int inventoryTop = ContainerGolem.playerTop(storageRows);

        fill(pose, left - 1, top - 1, left + imageWidth + 1, top + imageHeight + 1, 0xFF3A2E1E);
        fill(pose, left, top, left + imageWidth, top + imageHeight, 0xF2D9CDB2);
        fillGradient(pose, left, top, left + imageWidth, top + 16, 0xFF6B5433, 0xFF54401F);

        // Slot wells, so the storage reads as slots even without a skin.
        for (int row = 0; row < storageRows; row++) {
            for (int column = 0; column < ContainerGolem.STORAGE_COLUMNS; column++) {
                well(pose, left + MARGIN + column * 18, top + ContainerGolem.STORAGE_TOP + row * 18);
            }
        }
        if (golem.upgradesAllowed()) {
            // A rule between the storage and the upgrade, so the gap reads as a division rather
            // than as a missing slot.
            int railTop = top + ContainerGolem.STORAGE_TOP;
            int railBottom = railTop + storageRows * 18 - 2;
            fill(pose, left + 148, railTop, left + 149, railBottom, 0xFF8B7B5E);
            upgradeWell(pose, left + ContainerGolem.UPGRADE_X, top + ContainerGolem.upgradeSlotY(storageRows));
        }

        // The orders sit in a panel of their own, so the block reads as one thing.
        int panelTop = top + ordersTop - 3;
        int panelBottom = top + ordersTop + ContainerGolem.ORDERS_HEIGHT + 3;
        fill(pose, left + 5, panelTop, left + imageWidth - 5, panelBottom, 0xFF8B7B5E);
        fill(pose, left + 6, panelTop + 1, left + imageWidth - 6, panelBottom - 1, 0xFFCFC2A6);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                well(pose, left + MARGIN + column * 18, top + inventoryTop + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            well(pose, left + MARGIN + column * 18, top + inventoryTop + 58);
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
    private void upgradeWell(com.mojang.blaze3d.vertex.PoseStack pose, int x, int y) {
        fill(pose, x - 3, y - 3, x + 19, y + 19, 0xFF6B5433);
        fill(pose, x - 2, y - 2, x + 18, y + 18, 0xFFA98A55);
        fill(pose, x - 1, y - 1, x + 17, y + 17, 0xFF6B5433);
        fill(pose, x, y, x + 16, y + 16, 0xFFC7B48D);
    }

    private void well(com.mojang.blaze3d.vertex.PoseStack pose, int x, int y) {
        fill(pose, x - 1, y - 1, x + 17, y + 17, 0xFF8B7B5E);
        fill(pose, x, y, x + 16, y + 16, 0xFFBFB197);
    }

    @Override
    protected void renderLabels(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY) {
        int ordersTop = ordersTop();

        String name = golem.getName()
            .getString();
        font.draw(pose, name, MARGIN, 4, 0xFFE9C9);

        // Whose golem this is, in the title bar, where it costs the orders no room. The watched
        // copy rather than the field: the field is only ever written on the server, and this screen
        // is looking at the client's own copy of the entity, whose field has never been anything
        // but empty - so the line reserved room for here was never once drawn. The WAILA tooltip
        // beside it always read the watched copy and always worked, which is what made this look
        // like the screen had lost the line rather than never having had it.
        String builder = golem.watchedSummoner();
        if (builder != null && !builder.isEmpty()) {
            String built = com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.built") + " " + builder;
            int width = font.width(built);
            if (RIGHT - width > MARGIN + font.width(name) + 6) {
                font.draw(pose, built, RIGHT - width, 4, 0xC9B48D);
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
        int colour;
        ItemStack tool = golem.getMainHandItem();
        if (!golem.isArmed()) {
            note = com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.noTamper");
            colour = 0xB03A2A;
        } else if (tool == null) {
            note = com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.toolMissing");
            colour = 0xB03A2A;
        } else if (!golem.hasMendingStock()) {
            note = com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.noStock");
            colour = 0x8A6A2A;
        } else if (!golem.watchedOrders()) {
            note = com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.noOrders");
            colour = 0x8A7A5E;
        } else {
            note = tool.getHoverName()
                .getString();
            colour = 0x3A5A2A;
        }
        font.draw(pose, trim(note, RIGHT - MARGIN), MARGIN, STATUS_DY, colour);

        int headerY = ordersTop + HEADER_DY + 2;
        font.draw(pose, 
            com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.radius") + " " + golem.watchedRadius(),
            RADIUS_TEXT_X,
            headerY,
            0x3A2A14);

        String pageLabel = (page + 1) + "/" + pages();
        font
            .draw(pose, pageLabel, PAGE_TEXT_MID - font.width(pageLabel) / 2, headerY, 0x3A2A14);

        for (int row = 0; row < ContainerGolem.ROWS_PER_PAGE; row++) {
            int index = page * ContainerGolem.ROWS_PER_PAGE + row;
            if (index >= families.length) break;
            SurfaceFamily family = families[index];
            int y = ordersTop + FAMILY_DY + row * ROW_PITCH + 2;

            font.draw(pose, trim(family.key(), NAME_WIDTH), MARGIN, y, 0x3A2A14);

            int target = orders().shownTargetFor(family);
            String shown = target < 0 ? com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.ignored") : target + "%";
            value(pose, trim(shown, VALUE_WIDTH), y, target < 0 ? 0x8A7A5E : 0x2A5A2A);
        }

        drawHeldRow(pose, ordersTop);
        drawHomeRow(pose, ordersTop);
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
    private void drawHomeRow(com.mojang.blaze3d.vertex.PoseStack pose, int ordersTop) {
        int y = ordersTop + HOME_DY + 2;
        font
            .draw(pose, trim(com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.home"), NAME_WIDTH), MARGIN, y, 0x3A2A14);

        // Nothing where the readout was while the field is open: the field itself is drawn in
        // screen coordinates from drawScreen, because this method runs inside a translate by the
        // panel's corner and a box placed at a screen position would land at twice it.
        if (typing != null) return;
        value(pose, trim(homeText(), VALUE_WIDTH + 24), y, 0x3A2A14);
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
    private void drawHeldRow(com.mojang.blaze3d.vertex.PoseStack pose, int ordersTop) {
        int nameY = ordersTop + HELD_NAME_DY;
        int rowY = ordersTop + HELD_ROW_DY + 2;
        int shown = orders().shownHeldTarget();

        if (shown == ContainerGolem.HELD_NONE) {
            font.draw(pose, 
                trim(com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.holdBlock"), RIGHT - MARGIN),
                MARGIN,
                nameY,
                0x8A7A5E);
            return;
        }

        ItemStack held = minecraft.player.getMainHandItem();
        String name = held == null ? "?"
            : held.getHoverName()
                .getString();
        font.draw(pose, trim(name, RIGHT - MARGIN), MARGIN, nameY, 0x3A2A14);

        String text;
        int colour;
        if (shown == EntityGolemOfWays.NO_BLOCK_TARGET) {
            text = com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.asFamily");
            colour = 0x8A7A5E;
        } else if (shown < 0) {
            text = com.trmtgtnh.util.Translate.get("trmtgtnh.golem.gui.ignored");
            colour = 0x8A7A5E;
        } else {
            text = shown + "%";
            colour = 0x2A5A2A;
        }
        value(pose, trim(text, VALUE_RIGHT - MARGIN), rowY, colour);
    }

    /** Sets a value against the right edge of the value column, clear of the first button. */
    private void value(com.mojang.blaze3d.vertex.PoseStack pose, String text, int y, int colour) {
        font.draw(pose, text, VALUE_RIGHT - font.width(text), y, colour);
    }

    /** Keeps a string inside its column, whatever a translation or another mod called the thing. */
    private String trim(String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - 6) + "...";
    }

    /**
     * A small flat button in the panel's own colours.
     *
     * <p>
     * Vanilla's widget texture is twenty pixels tall and is blitted at whatever height the button
     * was given, so a fourteen-pixel button loses the bottom of its own frame and a column of them
     * looks smeared. Four rows of proper twenty-pixel buttons will not fit above the inventory, so
     * these are drawn instead - which the rest of this screen already is.
     */
    private static final class Flat extends Button {

        /**
         * Which button this is.
         *
         * <p>
         * Vanilla's own id is gone - a button carries a callback instead - and the screen's dispatch
         * is a switch over twenty-six of them that is worth keeping exactly as it was. So the id is
         * kept here and handed back, which turns the callback into the dispatch rather than
         * replacing it.
         */
        private final int id;

        private static final int BORDER = 0xFF6B5433;
        private static final int FACE = 0xFFB5A487;
        private static final int FACE_OVER = 0xFFD8C9AA;
        private static final int FACE_OFF = 0xFFBCB4A2;
        private static final int LABEL = 0xFF2A1E0E;
        private static final int LABEL_OFF = 0xFF8A8272;

        Flat(GuiGolem screen, int id, int x, int y, int width, int height, String label) {
            // The screen arrives as a parameter rather than as an enclosing instance, because
            // `this` - outer or otherwise - may not be named in a super() argument, and an inner
            // class does not get round that.
            super(
                x,
                y,
                width,
                height,
                new net.minecraft.network.chat.TextComponent(label),
                ignored -> screen.actionPerformed(id));
            this.id = id;
        }

        @Override
        public void renderButton(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY,
            float partialTicks) {
            if (!visible) return;

            boolean over = active && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

            int face = !active ? FACE_OFF : over ? FACE_OVER : FACE;
            fill(pose, x, y, x + width, y + height, BORDER);
            fill(pose, x + 1, y + 1, x + width - 1, y + height - 1, face);

            Font font = Minecraft.getInstance().font;
            String label = getMessage().getString();
            font.draw(pose, 
                label,
                x + (width - font.width(label)) / 2,
                y + (height - 8) / 2 + 1,
                active ? LABEL : LABEL_OFF);
        }
    }
}
